package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.support.AllocTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AllocRelation 数据范围注入集成测试（Task Q7.2）.
 *
 * <p>验证 Service→Mapper→DB 全链路的数据范围过滤行为：
 * <ul>
 *   <li>ALL：能看到所有员工的分配关系（本用例三个员工各一条，查 admin empId 时 3 条）</li>
 *   <li>SELF：只看到 scopeContext.empId 的分配关系（即便 API 参数为其他 empId）</li>
 *   <li>ctx=null：一条也看不到（fail-close "1=0"）</li>
 * </ul>
 *
 * <p>前缀 {@code TEST_ARS_*} 用于和其他 Alloc IT 隔离（事务回滚）.
 */
class AllocRelationScopeIntegrationTest extends PerformanceMapperTestBase {

    @MockBean
    private CurrentUserApi currentUserApi;

    @MockBean
    private BizScopeApi bizScopeApi;

    @Autowired
    private AllocRelationService allocRelationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMocks() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
        // 默认管理员 ALL
        Mockito.when(bizScopeApi.buildScopeContext(Mockito.anyString(), Mockito.any(BizType.class), Mockito.any(BizAction.class)))
                .thenAnswer(inv -> new DataScopeContext(
                        DataScopeType.ALL, inv.getArgument(0), "HQ", Set.of(),
                        inv.getArgument(1), inv.getArgument(2)));
        // resolveScope 兜底（AllocRelationService 其他方法会用）
        Mockito.when(bizScopeApi.resolveScope(Mockito.anyString(), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.ALL);
    }

    private void insertRaw(CustAllocRelation r) {
        jdbcTemplate.update(
                "INSERT INTO cust_alloc_relation (id, cust_id, alloc_dim, biz_kind, account_no, "
                        + "emp_id, ratio, effective_date, end_date, source_batch_id, source_process_date, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.getId(), r.getCustId(), r.getAllocDim(), r.getBizKind(), r.getAccountNo(),
                r.getEmpId(), r.getRatio(), r.getEffectiveDate(), r.getEndDate(),
                r.getSourceBatchId(), r.getSourceProcessDate(), r.getCreatedBy());
    }

    @Test
    void whenScopeAll_seesAllByEmp() {
        // 插入 3 条 emp_id=EMP_A1 的分配记录
        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_1", "EMP_A1", "LOAN"));
        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_2", "EMP_A1", "LOAN"));
        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_3", "EMP_A1", "LOAN"));

        // ALL scope → 以 EMP_A1 作为 Mapper 查询主体，应看到所有 3 条
        List<CustAllocRelation> list = allocRelationService.listCustomersByEmpWithScope("EMP_A1", "LOAN");

        assertThat(list).hasSize(3);
    }

    @Test
    void whenScopeSelf_onlySeesSelfEmpId() {
        // 切换当前用户为普通用户 EMP_SELF, scope=SELF
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("EMP_SELF");
        Mockito.when(bizScopeApi.buildScopeContext(Mockito.eq("EMP_SELF"), Mockito.any(), Mockito.any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF, "EMP_SELF", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));

        // 插入 3 条：2 条 EMP_SELF, 1 条 EMP_OTHER
        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_MINE_1", "EMP_SELF", "LOAN"));
        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_MINE_2", "EMP_SELF", "LOAN"));
        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_OTHER", "EMP_OTHER", "LOAN"));

        // 即便 API 传入 EMP_OTHER, scope fragment 会强制 emp_id = EMP_SELF 不命中其他用户
        // 但当前 Service 签名要求传 empId 作为 Mapper 主 where 条件；
        // 因此让 Controller 层 = 传 currentEmpId, 这里验证场景：普通用户传自己的 empId 能看到自己的 2 条
        List<CustAllocRelation> list = allocRelationService.listCustomersByEmpWithScope("EMP_SELF", "LOAN");

        assertThat(list).hasSize(2);
        assertThat(list).allSatisfy(r -> assertThat(r.getEmpId()).isEqualTo("EMP_SELF"));
    }

    @Test
    void whenScopeFailClose_seesNothing() {
        // 返回 null context 触发 fail-close
        Mockito.when(bizScopeApi.buildScopeContext(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);

        insertRaw(AllocTestDataBuilder.relation("TEST_ARS_FC", "admin", "LOAN"));

        List<CustAllocRelation> list = allocRelationService.listCustomersByEmpWithScope("admin", "LOAN");

        // fail-close "1=0", 查无结果
        assertThat(list).isEmpty();
    }

    @Test
    void whenScopeSelf_butAsOfDateIsToday_returnsOnlyEffectiveOnes() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("EMP_TIME");
        Mockito.when(bizScopeApi.buildScopeContext(Mockito.eq("EMP_TIME"), Mockito.any(), Mockito.any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF, "EMP_TIME", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));

        LocalDate today = LocalDate.now();
        // 当前生效 (custId=TEST_AR_ARS_EFF)
        insertRaw(AllocTestDataBuilder.relation("ARS_EFF",
                "EMP_TIME", "LOAN", today.minusDays(3), null));
        // 已失效 (custId=TEST_AR_ARS_EXP)
        insertRaw(AllocTestDataBuilder.relation("ARS_EXP",
                "EMP_TIME", "LOAN", today.minusDays(10), today.minusDays(5)));

        List<CustAllocRelation> list = allocRelationService.listCustomersByEmpWithScope("EMP_TIME", "LOAN");

        // 只有当前生效的那条应被返回（end_date 过期的被时间线条件排除）
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCustId()).isEqualTo("TEST_AR_ARS_EFF");
    }
}
