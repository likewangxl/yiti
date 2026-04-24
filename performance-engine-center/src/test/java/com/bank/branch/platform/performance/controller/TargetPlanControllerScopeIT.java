package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.service.TargetPlanService;
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
 * TargetPlanController V1.3 Task R1.3 list 端点改造 IT.
 *
 * <p>R1.3 验收点：Controller GET /api/perf/target-plans 应改用 pageWithScope
 * 路径（经 PerfScopeHelper 注入数据范围）, 而非 V1.0 / V1.2 时期的 page。
 *
 * <p>本 IT 以 {@link MockBean} 覆盖 TargetPlanService, 验证：
 * <ul>
 *   <li>Controller 调用 pageWithScope(...) 而非 page(...)</li>
 *   <li>kpiSchemeId / status / keyword / 分页参数透传正确</li>
 * </ul>
 *
 * <p>注：文件后缀 {@code IT} 属 failsafe 分组（V1.3 R0.3 已分层），
 * 运行需 {@code mvn verify}；{@code mvn test} 不会触发本 IT。
 */
class TargetPlanControllerScopeIT extends PerformanceControllerTestBase {

    @MockBean
    private TargetPlanService targetPlanService;

    @Test
    void list_delegatesToPageWithScope_notPage() throws Exception {
        // V1.3 R4.1：Controller 改调 pageWithScopeDto；验证 Controller 不再走 page
        when(targetPlanService.pageWithScopeDto(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 20, 0L, Collections.<TargetPlanDTO>emptyList()));

        mockMvc.perform(get("/api/perf/target-plans")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> kpiSchemeIdCap = ArgumentCaptor.forClass(String.class);
        verify(targetPlanService).pageWithScopeDto(
                kpiSchemeIdCap.capture(), any(), any(), anyInt(), anyInt());
        // 未传入 kpiSchemeId → null
        assertThat(kpiSchemeIdCap.getValue()).isNull();
        verify(targetPlanService, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void list_passesAllFilterParamsToScopeMethod() throws Exception {
        when(targetPlanService.pageWithScopeDto(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(2, 15, 0L, Collections.<TargetPlanDTO>emptyList()));

        mockMvc.perform(get("/api/perf/target-plans")
                        .param("kpiSchemeId", "KS_001")
                        .param("status", "ACTIVE")
                        .param("keyword", "TEST")
                        .param("pageNo", "2")
                        .param("pageSize", "15"))
                .andExpect(status().isOk());

        verify(targetPlanService).pageWithScopeDto(
                eq("KS_001"), eq("ACTIVE"), eq("TEST"), eq(2), eq(15));
    }
}
