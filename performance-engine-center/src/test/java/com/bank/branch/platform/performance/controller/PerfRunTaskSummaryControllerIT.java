package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/perf/run-tasks/metric-summary 鉴权 + 分页契约 IT.
 *
 * <p>本任务（Task 3）用 mock 管理员鉴权覆盖两类场景：登录管理员 200 + 未登录 401；
 * 资源 {@code P_PERF_RT_SUM} 与角色绑定在 Task 7 才统一落库，本 IT 不依赖该资源。
 */
class PerfRunTaskSummaryControllerIT extends PerformanceControllerTestBase {

    @MockBean
    private CurrentUserApi currentUserApi;

    @MockBean
    private BizScopeApi bizScopeApi;

    /** 每个测试前默认装配 "管理员 + ALL 范围" 上下文（resolveScopeFilter 返回 null → 管理员全见）. */
    @BeforeEach
    void resetCurrentUser() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(bizScopeApi.resolveScope(Mockito.anyString(), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.ALL);
    }

    @Test
    void metricSummary_authed_returns200() throws Exception {
        mockMvc.perform(get("/api/perf/run-tasks/metric-summary")
                        .param("taskType", "METRIC_RUN")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /**
     * 未登录时 Controller 调 {@code currentUserApi.getCurrentEmpId()} 抛 {@link AuthException},
     * 由全局异常处理器映射 HTTP 401.
     */
    @Test
    void metricSummary_unauthenticated_returns401() throws Exception {
        Mockito.when(currentUserApi.getCurrentEmpId())
                .thenThrow(new AuthException("AUTH-40105", "未登录"));

        mockMvc.perform(get("/api/perf/run-tasks/metric-summary"))
                .andExpect(status().isUnauthorized());
    }
}
