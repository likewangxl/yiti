package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
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
 * TargetValueService V1.3 Task R1.1 pageWithScope 数据范围注入单元测试.
 *
 * <p>V1.3 R1.1：在已有的 listByPlan(强制 planId) 之外新增 pageWithScope 路径,
 * 经 PerfScopeHelper 注入 BizScopeApi 返回的 scope 片段到 Mapper.
 *
 * <p>ScopeColumns 映射（perf_target_value 仅 created_by 可用的业务列）:
 * <ul>
 *   <li>ownerEmpCol   = "created_by" (SELF 降级到创建人)</li>
 *   <li>assigneeCol   = "created_by"</li>
 *   <li>createdByCol  = "created_by" (SELF_CREATED)</li>
 *   <li>ownerOrgCol   = "created_by" (perf_target_value 无 org_code 业务列, ORG 降级到 created_by)</li>
 * </ul>
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>ALL → scopeFragment=空 / scopeParams=空</li>
 *   <li>SELF_CREATED → scopeFragment="created_by = #{scopeParams.ownerEmpId}"</li>
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

    private PerfScopeHelper perfScopeHelper;

    private TargetValueService service;

    @BeforeEach
    void setUp() {
        this.perfScopeHelper = new PerfScopeHelper(bizScopeApi);
        this.service = new TargetValueService(targetValueMapper, currentUserApi, perfScopeHelper);
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
    @DisplayName("pageWithScope: SELF_CREATED → 注入 created_by 过滤片段 + 对应参数")
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
}
