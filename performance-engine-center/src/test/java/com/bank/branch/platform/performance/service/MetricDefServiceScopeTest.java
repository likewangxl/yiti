package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
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
 * MetricDefService Q7.3 pageWithScope 示范测试.
 *
 * <p>Q7.3 以 Metric 列表查询示范：创建人维度的数据范围过滤
 * （SELF_CREATED → created_by = #{scopeParams.ownerEmpId}）.
 */
@ExtendWith(MockitoExtension.class)
class MetricDefServiceScopeTest {

    @Mock
    private PerfMetricDefMapper metricDefMapper;

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private CurrentUserApi currentUserApi;

    private PerfScopeHelper perfScopeHelper;

    private MetricDefService service;

    @BeforeEach
    void setUp() {
        this.perfScopeHelper = new PerfScopeHelper(bizScopeApi, null);
        // pageWithScope 只依赖 mapper / currentUserApi / perfScopeHelper,
        // 其他字段传 null 不影响本测试覆盖面
        this.service = new MetricDefService(
                metricDefMapper,
                null,   // metricRefService
                null,   // metricSlotService
                null,   // metricCycleDetectService
                null,   // kpiItemMapper（禁用前置校验用，本测试不涉及）
                null,   // objectMapper
                currentUserApi,
                null,   // userApi（详情解析用，本测试不涉及）
                perfScopeHelper,
                null);  // metricSchedulerService（本测试不涉及调度）
    }

    @Test
    @DisplayName("pageWithScope: ALL → 无 scope 片段")
    void whenScopeAll_passesEmptyFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ALL, "admin", "HQ", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(metricDefMapper.selectByConditionWithScope(
                any(), any(), any(), any(), anyInt(), anyInt(), eq(""), any()))
                .thenReturn(Collections.emptyList());
        when(metricDefMapper.countByConditionWithScope(
                any(), any(), any(), any(), eq(""), any()))
                .thenReturn(0L);

        PageResult<PerfMetricDef> result = service.pageWithScope(null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(metricDefMapper).selectByConditionWithScope(
                any(), any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEmpty();
    }

    @Test
    @DisplayName("pageWithScope: SELF_CREATED → 注入 created_by 过滤片段")
    void whenScopeSelfCreated_injectsCreatedByFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_CRT");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF_CREATED, "USER_CRT", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(metricDefMapper.selectByConditionWithScope(
                any(), any(), any(), any(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(metricDefMapper.countByConditionWithScope(
                any(), any(), any(), any(), anyString(), any()))
                .thenReturn(0L);

        service.pageWithScope("EMP", null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(metricDefMapper).selectByConditionWithScope(
                any(), any(), any(), any(), anyInt(), anyInt(),
                sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue()).isEqualTo("created_by = #{scopeParams.ownerEmpId}");
        assertThat(paramsCap.getValue()).containsEntry("ownerEmpId", "USER_CRT");
    }

    @Test
    @DisplayName("pageWithScope: ctx=null → fail-close \"1=0\"")
    void whenNoContext_failClose() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_NO");
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(null);
        when(metricDefMapper.selectByConditionWithScope(
                any(), any(), any(), any(), anyInt(), anyInt(), eq("1=0"), any()))
                .thenReturn(Collections.emptyList());
        when(metricDefMapper.countByConditionWithScope(
                any(), any(), any(), any(), eq("1=0"), any()))
                .thenReturn(0L);

        service.pageWithScope(null, null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(metricDefMapper).selectByConditionWithScope(
                any(), any(), any(), any(), anyInt(), anyInt(), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEqualTo("1=0");
    }
}
