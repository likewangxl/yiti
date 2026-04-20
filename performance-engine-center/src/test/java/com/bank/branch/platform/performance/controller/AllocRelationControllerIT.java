package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.support.AllocTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AllocRelationController IT: 覆盖 3 只读端点 (list / history / summary) 的正反向场景 + 注解约束.
 *
 * <p>Plan Task 5.4 (L1572-1582) 钦定必含场景:
 * <ul>
 *   <li>summary_regularUser_onlyOwnEmp (普通用户查 summary → 仅能看到自己的汇总)</li>
 *   <li>history_whenAsOfDateMissing_returns400 (asOfDate 缺失 → 400)</li>
 * </ul>
 *
 * <p>Plan L1580 钦定: 3 个 GET 方法只有 {@code @BizAuth(READ)}, 无 {@code @AuditLog}.
 *
 * <p>summary 端点 empId 设计决策: 采用 "方案 C" —— Controller 不接受 empId 参数,
 * 永远以 {@link CurrentUserApi#getCurrentEmpId()} 为查询主体. 简化并规避越权查他人 summary
 * 的风险; 覆盖 DoD L1577 "普通用户 summary 只返回自己" 的语义.
 *
 * <p>测试数据前缀: TEST_AR_* (由 {@link AllocTestDataBuilder} 统一管理); 事务自动回滚.
 */
class AllocRelationControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.AllocRelationController";

    @MockBean
    private CurrentUserApi currentUserApi;

    @MockBean
    private BizScopeApi bizScopeApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 每个测试前默认装配 "管理员 + ALL 范围" 上下文; 个别用例可 override. */
    @BeforeEach
    void resetCurrentUser() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.getCurrentCandidateGroupKeys()).thenReturn(Set.of());
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
        Mockito.when(bizScopeApi.resolveScope(Mockito.anyString(), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.ALL);
    }

    /** 直接走 JDBC 插入 (Mapper 只读). */
    private void insertRaw(CustAllocRelation r) {
        jdbcTemplate.update(
                "INSERT INTO cust_alloc_relation (id, cust_id, alloc_dim, biz_kind, account_no, "
                        + "emp_id, ratio, effective_date, end_date, source_batch_id, source_process_date, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.getId(), r.getCustId(), r.getAllocDim(), r.getBizKind(), r.getAccountNo(),
                r.getEmpId(), r.getRatio(), r.getEffectiveDate(), r.getEndDate(),
                r.getSourceBatchId(), r.getSourceProcessDate(), r.getCreatedBy()
        );
    }

    // =================== list ===================

    @Test
    void list_whenExists_returns200() throws Exception {
        CustAllocRelation r = AllocTestDataBuilder.relation("LIST_OK", "EMP_LIST", "DEPOSIT");
        insertRaw(r);

        mockMvc.perform(get("/api/perf/alloc-relations")
                        .param("custId", r.getCustId())
                        .param("bizKind", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].custId").value(r.getCustId()))
                .andExpect(jsonPath("$.data[0].empId").value("EMP_LIST"))
                .andExpect(jsonPath("$.data[0].bizKind").value("DEPOSIT"));
    }

    @Test
    void list_whenCustIdMissing_returns400() throws Exception {
        // @RequestParam(value="custId") 默认 required=true, 缺失 → MissingServletRequestParameterException → 400
        mockMvc.perform(get("/api/perf/alloc-relations")
                        .param("bizKind", "DEPOSIT"))
                .andExpect(status().isBadRequest());
    }

    // =================== history ===================

    @Test
    void history_ok() throws Exception {
        // 在 asOfDate (今天) 生效的记录
        CustAllocRelation r = AllocTestDataBuilder.relation(
                "HIST_OK", "EMP_HIST", "LOAN", LocalDate.now().minusDays(5), null);
        insertRaw(r);

        mockMvc.perform(get("/api/perf/alloc-relations/history")
                        .param("custId", r.getCustId())
                        .param("asOfDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].custId").value(r.getCustId()))
                .andExpect(jsonPath("$.data[0].empId").value("EMP_HIST"));
    }

    /** Plan L1578 钦定: asOfDate 缺失 → 400. */
    @Test
    void history_whenAsOfDateMissing_returns400() throws Exception {
        mockMvc.perform(get("/api/perf/alloc-relations/history")
                        .param("custId", "TEST_AR_ANY"))
                .andExpect(status().isBadRequest());
    }

    // =================== summary ===================

    @Test
    void summary_ok() throws Exception {
        // 管理员身份查 summary: 以 "admin" 为员工工号过滤; 插入两条 emp_id=admin 的分配关系
        CustAllocRelation r1 = AllocTestDataBuilder.relation(
                "SUM_A", "admin", "DEPOSIT", LocalDate.now().minusDays(1), null);
        CustAllocRelation r2 = AllocTestDataBuilder.relation(
                "SUM_B", "admin", "DEPOSIT", LocalDate.now().minusDays(1), null);
        insertRaw(r1);
        insertRaw(r2);

        mockMvc.perform(get("/api/perf/alloc-relations/summary")
                        .param("bizKind", "DEPOSIT")
                        .param("asOfDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].empId").value("admin"))
                .andExpect(jsonPath("$.data[0].bizKind").value("DEPOSIT"))
                .andExpect(jsonPath("$.data[0].custCount").value(2));
    }

    /** Plan L1577 钦定: 普通用户登录 → summary 只返回自己的 empId 汇总. */
    @Test
    void summary_regularUser_onlyOwnEmp() throws Exception {
        // 切换为普通用户上下文 (empId = EMP_SELF)
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("EMP_SELF");
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(false);
        Mockito.when(bizScopeApi.resolveScope(Mockito.eq("EMP_SELF"), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.SELF);

        // 插入两个员工的分配关系, EMP_SELF 应只看到自己的汇总条目
        insertRaw(AllocTestDataBuilder.relation(
                "SUM_MINE", "EMP_SELF", "DEPOSIT", LocalDate.now().minusDays(1), null));
        insertRaw(AllocTestDataBuilder.relation(
                "SUM_OTHER", "EMP_OTHER", "DEPOSIT", LocalDate.now().minusDays(1), null));

        mockMvc.perform(get("/api/perf/alloc-relations/summary")
                        .param("bizKind", "DEPOSIT")
                        .param("asOfDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                // Controller 只用 currentUser empId, 因此 summary 列表只有自己一行
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].empId").value("EMP_SELF"));
    }

    // =================== 注解约束 ===================

    @Test
    void readMethods_shouldDeclareBizAuth() throws Exception {
        assertBizAuth("list", new Class<?>[]{String.class, String.class}, BizAction.READ);
        assertBizAuth("history", new Class<?>[]{String.class, LocalDate.class}, BizAction.READ);
        assertBizAuth("summary", new Class<?>[]{String.class, LocalDate.class}, BizAction.READ);
    }

    /** Plan L1580 钦定: 3 个读操作端点不得有 @AuditLog. */
    @Test
    void readMethods_shouldNotHaveAuditLog() throws Exception {
        assertNoAuditLog("list", new Class<?>[]{String.class, String.class});
        assertNoAuditLog("history", new Class<?>[]{String.class, LocalDate.class});
        assertNoAuditLog("summary", new Class<?>[]{String.class, LocalDate.class});
    }

    // =================== helpers ===================

    private void assertBizAuth(String methodName, Class<?>[] parameterTypes, BizAction action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).as("method %s 应有 @BizAuth", methodName).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(action);
    }

    private void assertNoAuditLog(String methodName, Class<?>[] parameterTypes) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog)
                .as("method %s 为只读端点, 不得带 @AuditLog (plan Task 5.4 L1580)", methodName)
                .isNull();
    }
}
