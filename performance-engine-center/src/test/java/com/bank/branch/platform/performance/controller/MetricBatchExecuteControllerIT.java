package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/perf/metrics/batch-execute 鉴权 + 入参校验 IT（照抄 {@link MetricExecuteControllerIT} 套路）.
 *
 * <p>本任务（Task 6）用 mock 管理员鉴权覆盖：入参校验 400（metricCodes 空 / reason 缺失）+ 未登录 401
 * + 方法注解声明校验；资源 {@code P_PERF_MTR_BEXEC} 与角色绑定在 Task 7 才统一落库，本 IT 不依赖该资源。
 */
class MetricBatchExecuteControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.MetricDefController";

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
    void batchExecute_emptyMetricCodes_returns400() throws Exception {
        mockMvc.perform(post("/api/perf/metrics/batch-execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricCodes\":[],\"dataDate\":\"2026-07-01\",\"reason\":\"批量\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchExecute_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/perf/metrics/batch-execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricCodes\":[\"M_1\"],\"dataDate\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 未登录时 Controller 调 {@code currentUserApi.getCurrentEmpId()} 抛 {@link AuthException},
     * 由全局异常处理器映射 HTTP 401。body 需合法（通过 @Valid 校验），让流程走到方法体触发鉴权异常。
     */
    @Test
    void batchExecute_anonymous_returns401() throws Exception {
        Mockito.when(currentUserApi.getCurrentEmpId())
                .thenThrow(new AuthException("AUTH-40105", "未登录"));

        mockMvc.perform(post("/api/perf/metrics/batch-execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricCodes\":[\"M_1\"],\"dataDate\":\"2026-07-01\",\"reason\":\"r\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchExecute_shouldDeclareBizAuthAndAuditLog() throws Exception {
        Class<?> controller = Class.forName(CONTROLLER_FQCN);
        Method method = controller.getDeclaredMethod("batchExecute",
                com.bank.branch.platform.performance.controller.dto.BatchExecuteReqDTO.class);

        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(BizAction.EXECUTE);

        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.action()).isEqualTo("METRIC_BATCH_EXECUTE");
        assertThat(auditLog.resourceType()).isEqualTo("PERF_RUN_TASK");
        assertThat(auditLog.reasonRequired()).isTrue();
    }
}
