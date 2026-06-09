package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
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
 * TargetPlanService V1.3 Task R1.2 pageWithScope 数据范围注入单元测试（V1.4 S2.3 精化）.
 *
 * <p>V1.3 R1.2：在既有 page() 之外新增 pageWithScope 路径, 经 PerfScopeHelper 注入
 * BizScopeApi 返回的 scope 片段到 Mapper, 支持 SELF_CREATED / ORG 等细粒度过滤。
 *
 * <p>V1.4 S2.3：随着 V1_4_0 引入 owner_emp_id / owner_org_code 独立字段,
 * ScopeColumns 从 V1.3 的"全部降级到 created_by"升级为按语义分列:
 * <ul>
 *   <li>ownerEmpCol   = "owner_emp_id" (SELF 精确匹配归属员工)</li>
 *   <li>assigneeCol   = "owner_emp_id" (SELF_ASSIGNED)</li>
 *   <li>createdByCol  = "created_by" (SELF_CREATED 保持)</li>
 *   <li>ownerOrgCol   = "owner_org_code" (ORG 精确匹配归属机构)</li>
 *   <li>bizKeyCol     = null (perf_target_plan 无 business_key)</li>
 * </ul>
 *
 * <p>覆盖：ALL → 空片段；SELF_CREATED → created_by=；SELF → owner_emp_id=；
 * ORG → owner_org_code=；ctx=null → fail-close "1=0"。
 */
@ExtendWith(MockitoExtension.class)
class TargetPlanServiceScopeTest {

    @Mock
    private PerfTargetPlanMapper targetPlanMapper;

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private org.springframework.cache.CacheManager cacheManager;

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private com.bank.branch.platform.auth.api.UserApi userApi;

    private PerfScopeHelper perfScopeHelper;

    private TargetPlanService service;

    @BeforeEach
    void setUp() {
        this.perfScopeHelper = new PerfScopeHelper(bizScopeApi, null);
        this.service = new TargetPlanService(
                targetPlanMapper, kpiSchemeService, cacheManager,
                currentUserApi, perfScopeHelper, userApi);
    }

    @Test
    @DisplayName("pageWithScope: ALL → 无 scope 片段, 全部目标方案可见")
    void whenScopeAll_passesEmptyFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.buildScopeContext(eq("admin"), eq(BizType.PERF_CONFIG), eq(BizAction.LIST)))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ALL, "admin", "HQ", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(targetPlanMapper.selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), eq(""), any()))
                .thenReturn(Collections.emptyList());
        when(targetPlanMapper.countByConditionWithScope(
                any(), any(), any(), eq(""), any()))
                .thenReturn(0L);

        PageResult<PerfTargetPlan> result = service.pageWithScope(null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(targetPlanMapper).selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
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
        when(targetPlanMapper.selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(targetPlanMapper.countByConditionWithScope(
                any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope("KS_001", "ACTIVE", "prod", 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(targetPlanMapper).selectByConditionWithScope(
                eq("KS_001"), eq("ACTIVE"), eq("prod"), anyInt(), anyInt(),
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
        when(targetPlanMapper.selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(targetPlanMapper.countByConditionWithScope(
                any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope(null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(targetPlanMapper).selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
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
        when(targetPlanMapper.selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(targetPlanMapper.countByConditionWithScope(
                any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope(null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(targetPlanMapper).selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), paramsCap.capture());
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
        when(targetPlanMapper.selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), eq("1=0"), any()))
                .thenReturn(Collections.emptyList());
        when(targetPlanMapper.countByConditionWithScope(
                any(), any(), any(), eq("1=0"), any()))
                .thenReturn(0L);

        PageResult<PerfTargetPlan> result = service.pageWithScope(null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(targetPlanMapper).selectByConditionWithScope(
                any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEqualTo("1=0");
    }
}
