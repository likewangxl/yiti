package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TargetValueController V1.3 Task R1.3 list 端点改造 IT.
 *
 * <p>R1.3 验收点：Controller GET /api/perf/target-values 应改用 pageWithScope
 * 路径（经 PerfScopeHelper 注入数据范围）, 而非 V1.0 / V1.2 时期的 listByPlan。
 *
 * <p>本 IT 以 {@link MockBean} 覆盖 TargetValueService, 验证：
 * <ul>
 *   <li>Controller 调用 pageWithScope(...) 而非 listByPlan(...)</li>
 *   <li>planId / subjectType / subjectId / cycleKey / 分页参数透传正确</li>
 * </ul>
 *
 * <p>注：文件后缀 {@code IT} 属 failsafe 分组（V1.3 R0.3 已分层），
 * 运行需 {@code mvn verify}；{@code mvn test} 不会触发本 IT。
 */
class TargetValueControllerScopeIT extends PerformanceControllerTestBase {

    @MockBean
    private TargetValueService targetValueService;

    @Test
    void list_delegatesToPageWithScope_notListByPlan() throws Exception {
        // V1.3 R4.1：Controller 改调 pageWithScopeDto；验证 Controller 不再走 listByPlan
        when(targetValueService.pageWithScopeDto(
                any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 20, 0L, Collections.<TargetValueDTO>emptyList()));

        mockMvc.perform(get("/api/perf/target-values")
                        .param("planId", "PLAN_X")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());

        // 验证走 pageWithScopeDto 而非 listByPlan
        ArgumentCaptor<String> planIdCap = ArgumentCaptor.forClass(String.class);
        verify(targetValueService).pageWithScopeDto(
                planIdCap.capture(), any(), any(), any(), anyInt(), anyInt());
        assertThat(planIdCap.getValue()).isEqualTo("PLAN_X");
        verify(targetValueService, never()).listByPlan(
                any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void list_passesAllFilterParamsToScopeMethod() throws Exception {
        when(targetValueService.pageWithScopeDto(
                any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 10, 0L, Collections.<TargetValueDTO>emptyList()));

        mockMvc.perform(get("/api/perf/target-values")
                        .param("planId", "PLAN_Y")
                        .param("subjectType", "EMP")
                        .param("subjectId", "E001")
                        .param("cycleKey", "2026Q1")
                        .param("pageNo", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());

        verify(targetValueService).pageWithScopeDto(
                eq("PLAN_Y"), eq("EMP"), eq("E001"), eq("2026Q1"), eq(2), eq(10));
    }
}
