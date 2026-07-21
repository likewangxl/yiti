package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetValueService V1.3 Task R1.1 pageWithScope 数据范围注入单元测试（V1.4 S2.3 精化）.
 *
 * <p>V1.3 R1.1：在已有的 listByPlan(强制 planId) 之外新增 pageWithScope 路径,
 * 经 PerfScopeHelper 注入 BizScopeApi 返回的 scope 片段到 Mapper.
 *
 * <p>V1.4 S2.3：随着 V1_4_0 引入 owner_emp_id / owner_org_code 独立字段,
 * ScopeColumns 从 V1.3 的"全部降级到 created_by"升级为按语义分列:
 * <ul>
 *   <li>ownerEmpCol   = "owner_emp_id" (SELF 精确匹配归属员工)</li>
 *   <li>assigneeCol   = "owner_emp_id" (SELF_ASSIGNED 同样基于归属员工列)</li>
 *   <li>createdByCol  = "created_by" (SELF_CREATED 语义保持"我创建的")</li>
 *   <li>ownerOrgCol   = "owner_org_code" (ORG 精确匹配归属机构)</li>
 *   <li>bizKeyCol     = null (perf_target_value 无 business_key, WORKFLOW_PARTICIPANT fail-close)</li>
 * </ul>
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>ALL → scopeFragment=空 / scopeParams=空</li>
 *   <li>SELF_CREATED → scopeFragment="created_by = #{scopeParams.ownerEmpId}"（不变）</li>
 *   <li>SELF → scopeFragment="owner_emp_id = #{scopeParams.ownerEmpId}"（V1.4 S2.3 新增）</li>
 *   <li>ORG → scopeFragment="owner_org_code = #{scopeParams.ownerOrgCode}"（V1.4 S2.3 新增）</li>
 *   <li>ctx=null → fail-close "1=0" + PageResult.total=0</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TargetValueServiceScopeTest {

    @Mock
    private PerfTargetValueMapper targetValueMapper;

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private CurrentUserApi currentUserApi;

    /** pageWithScopeDto 批量解析 EMP 对象姓名（subjectId=工号=PT_USER.username）用. */
    @Mock
    private UserApi userApi;

    private PerfScopeHelper perfScopeHelper;

    private TargetValueService service;

    @BeforeEach
    void setUp() {
        this.perfScopeHelper = new PerfScopeHelper(bizScopeApi, null);
        // 本测试覆盖 pageWithScope / pageWithScopeDto 读路径：后者要解析 EMP 姓名故需 userApi，
        // 其余（orgApi/planMapper/kpiItemMapper）只在写路径的主体/指标校验用到，传 null 即可
        this.service = new TargetValueService(targetValueMapper, currentUserApi, perfScopeHelper, userApi, null, null, null);
    }

    @Test
    @DisplayName("pageWithScope: ALL → 无 scope 片段, 全部目标值可见")
    void whenScopeAll_passesEmptyFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.buildScopeContext(eq("admin"), eq(BizType.PERF_CONFIG), eq(BizAction.LIST)))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ALL, "admin", "HQ", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(targetValueMapper.selectByConditionWithScope(
                anyString(), any(), any(), any(), anyInt(), anyInt(), eq(""), any()))
                .thenReturn(Collections.emptyList());
        when(targetValueMapper.countByConditionWithScope(
                anyString(), any(), any(), any(), eq(""), any()))
                .thenReturn(0L);

        PageResult<PerfTargetValue> result = service.pageWithScope(
                "PLAN_001", null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(targetValueMapper).selectByConditionWithScope(
                eq("PLAN_001"), any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEmpty();
    }

    @Test
    @DisplayName("pageWithScope: SELF_CREATED → 注入 created_by 过滤片段 + 对应参数（createdByCol 语义保持）")
    void whenScopeSelfCreated_injectsCreatedByFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_CRT");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF_CREATED, "USER_CRT", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(targetValueMapper.selectByConditionWithScope(
                anyString(), any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(targetValueMapper.countByConditionWithScope(
                anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope("PLAN_001", "EMP", null, "2026", 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(targetValueMapper).selectByConditionWithScope(
                eq("PLAN_001"), eq("EMP"), any(), eq("2026"), anyInt(), anyInt(),
                sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue()).isEqualTo("created_by = #{scopeParams.ownerEmpId}");
        assertThat(paramsCap.getValue()).containsEntry("ownerEmpId", "USER_CRT");
    }

    @Test
    @DisplayName("V1.4 S2.3: pageWithScope SELF → 注入 owner_emp_id 过滤片段（精确归属员工）")
    void whenScopeSelf_injectsOwnerEmpIdFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_OWN");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF, "USER_OWN", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(targetValueMapper.selectByConditionWithScope(
                anyString(), any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(targetValueMapper.countByConditionWithScope(
                anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope("PLAN_001", null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(targetValueMapper).selectByConditionWithScope(
                eq("PLAN_001"), any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
        assertThat(sqlCap.getValue())
                .as("V1.4 S2.3 SELF 必须切到 owner_emp_id（不再是 created_by）")
                .isEqualTo("owner_emp_id = #{scopeParams.ownerEmpId}");
    }

    @Test
    @DisplayName("V1.4 S2.3: pageWithScope ORG → 注入 owner_org_code 过滤片段（精确归属机构）")
    void whenScopeOrg_injectsOwnerOrgCodeFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_ORG");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ORG, "USER_ORG", "BRANCH_V14", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(targetValueMapper.selectByConditionWithScope(
                anyString(), any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(targetValueMapper.countByConditionWithScope(
                anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope("PLAN_001", null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(targetValueMapper).selectByConditionWithScope(
                eq("PLAN_001"), any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue())
                .as("V1.4 S2.3 ORG 必须切到 owner_org_code（不再是 created_by）")
                .isEqualTo("owner_org_code = #{scopeParams.ownerOrgCode}");
        assertThat(paramsCap.getValue()).containsEntry("ownerOrgCode", "BRANCH_V14");
    }

    @Test
    @DisplayName("pageWithScope: ctx=null → fail-close, Mapper 接收 \"1=0\"")
    void whenNoContext_failClose() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_NO");
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(null);
        when(targetValueMapper.selectByConditionWithScope(
                anyString(), any(), any(), any(), anyInt(), anyInt(), eq("1=0"), any()))
                .thenReturn(Collections.emptyList());
        when(targetValueMapper.countByConditionWithScope(
                anyString(), any(), any(), any(), eq("1=0"), any()))
                .thenReturn(0L);

        PageResult<PerfTargetValue> result = service.pageWithScope(
                "PLAN_001", null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(targetValueMapper).selectByConditionWithScope(
                eq("PLAN_001"), any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEqualTo("1=0");
    }

    // ---------------- pageWithScopeDto: EMP 对象姓名解析（2026-07-21） ----------------

    /** 构造一条 EMP 目标值。 */
    private PerfTargetValue empValue(String id, String subjectId) {
        PerfTargetValue v = new PerfTargetValue();
        v.setId(id);
        v.setPlanId("PLAN_001");
        v.setSubjectType("EMP");
        v.setSubjectId(subjectId);
        return v;
    }

    private UserDTO user(String username, String displayName) {
        UserDTO u = new UserDTO();
        u.setUsername(username);
        u.setDisplayName(displayName);
        return u;
    }

    /** 让 scope 走 ALL 空片段，并按给定记录打桩 mapper。 */
    private void stubAllScope(java.util.List<PerfTargetValue> records) {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.buildScopeContext(eq("admin"), eq(BizType.PERF_CONFIG), eq(BizAction.LIST)))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ALL, "admin", "HQ", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(targetValueMapper.countByConditionWithScope(
                anyString(), any(), any(), any(), eq(""), any()))
                .thenReturn((long) records.size());
        when(targetValueMapper.selectByConditionWithScope(
                anyString(), any(), any(), any(), anyInt(), anyInt(), eq(""), any()))
                .thenReturn(records);
    }

    /**
     * EMP 行必须带出 subjectName（工号→姓名）。
     * <p>
     * 前端 TargetValues.vue 原先自行调管理员接口 /api/admin/users 拉全量用户建工号→姓名映射，
     * 该接口资源 A_USER_LIST 仅授予少数角色，资财部经办人等进页面即 403「没有权限」。
     * 姓名解析下沉后端后前端不再需要该调用，故本用例是那条链路的回归锚点。
     * </p>
     */
    @Test
    @DisplayName("pageWithScopeDto: EMP 行按工号批量解析 subjectName")
    void pageWithScopeDto_resolvesEmpSubjectName() {
        stubAllScope(java.util.List.of(empValue("V1", "finance_zhou"), empValue("V2", "rm_li")));
        when(userApi.getUsersByUsernames(any()))
                .thenReturn(java.util.List.of(user("finance_zhou", "周八(资财)"), user("rm_li", "李四")));

        var page = service.pageWithScopeDto("PLAN_001", null, null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords().get(0).getSubjectName()).isEqualTo("周八(资财)");
        assertThat(page.getRecords().get(1).getSubjectName()).isEqualTo("李四");
        // 必须一次批量解析，不得逐行查询
        verify(userApi, org.mockito.Mockito.times(1)).getUsersByUsernames(any());
    }

    /** ORG 行不参与工号解析：机构名由前端 orgMap 承担，避免把机构编码当工号查。 */
    @Test
    @DisplayName("pageWithScopeDto: 无 EMP 行时不调用户接口")
    void pageWithScopeDto_skipsLookupWhenNoEmpRows() {
        // 变量不能叫 org：会遮蔽 org.mockito 包名，导致下方 never() 引用编译失败
        PerfTargetValue orgRow = new PerfTargetValue();
        orgRow.setId("V3");
        orgRow.setPlanId("PLAN_001");
        orgRow.setSubjectType("ORG");
        orgRow.setSubjectId("0101");
        stubAllScope(java.util.List.of(orgRow));

        var page = service.pageWithScopeDto("PLAN_001", null, null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getSubjectName()).isNull();
        verify(userApi, org.mockito.Mockito.never()).getUsersByUsernames(any());
    }

    /** 工号查不到姓名时 subjectName 留空，由前端兜底显示工号，不能塞工号冒充姓名。 */
    @Test
    @DisplayName("pageWithScopeDto: 工号无对应用户时 subjectName 为空")
    void pageWithScopeDto_unresolvedEmpLeavesNameNull() {
        stubAllScope(java.util.List.of(empValue("V4", "ghost_user")));
        when(userApi.getUsersByUsernames(any())).thenReturn(Collections.emptyList());

        var page = service.pageWithScopeDto("PLAN_001", null, null, null, 1, 20);

        assertThat(page.getRecords().get(0).getSubjectId()).isEqualTo("ghost_user");
        assertThat(page.getRecords().get(0).getSubjectName()).isNull();
    }
}
