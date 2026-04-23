package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.RecalcReqDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.HistoryRecalcService;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PerfCalcController.recalc IT（V1.1 Task P7.2 Red）.
 *
 * <p>守护 POST /api/perf/recalc 端点契约（03 §F.2）：
 * <ul>
 *   <li>正常请求：返回父级 taskId + status=RUNNING</li>
 *   <li>@BizAuth(bizType=PERF_CONFIG, action=EXECUTE) 注解齐全</li>
 *   <li>@AuditLog(action=PERF_RECALC, resourceType=PERF_RUN_TASK, reasonRequired=true) 注解齐全</li>
 *   <li>reason 缺失 → 400 VALIDATION</li>
 *   <li>cycleDateTo &lt; cycleDateFrom → VALIDATION_FAILED（从 Service 透传）</li>
 * </ul>
 *
 * <p>使用 @MockBean {@link HistoryRecalcService} 隔离 DB/计算逻辑，专注 Controller 路由 + DTO 映射.
 */
class PerfCalcControllerRecalcIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.PerfCalcController";

    @MockBean
    private HistoryRecalcService historyRecalcService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Mockito.reset(historyRecalcService);
    }

    @Test
    @DisplayName("POST /api/perf/recalc：正常请求返回 taskId + status")
    void recalc_validRequest_returnsTaskId() throws Exception {
        Mockito.when(historyRecalcService.recalc(
                any(LocalDate.class), any(LocalDate.class),
                any(), anyString(), anyString(), anyString()))
                .thenReturn("PARENT_TASK_001");

        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDateFrom(LocalDate.of(2026, 3, 1));
        req.setCycleDateTo(LocalDate.of(2026, 3, 31));
        req.setMetricCodes(List.of("M_EMP_A", "M_EMP_B"));
        req.setVersion("v20260301");
        req.setReason("Q1 补录");

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value("PARENT_TASK_001"))
                .andExpect(jsonPath("$.data.status").exists());
    }

    @Test
    @DisplayName("POST /api/perf/recalc：reason 缺失返回 400")
    void recalc_missingReason_returns400() throws Exception {
        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDateFrom(LocalDate.of(2026, 3, 1));
        req.setCycleDateTo(LocalDate.of(2026, 3, 31));
        req.setVersion("v1");
        // 故意不设置 reason

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/perf/recalc：cycleDateTo < cycleDateFrom → VALIDATION_FAILED")
    void recalc_invalidDateRange_returnsValidationFailed() throws Exception {
        Mockito.when(historyRecalcService.recalc(
                any(LocalDate.class), any(LocalDate.class),
                any(), anyString(), anyString(), anyString()))
                .thenThrow(new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "startDate 不能晚于 endDate"));

        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDateFrom(LocalDate.of(2026, 3, 31));
        req.setCycleDateTo(LocalDate.of(2026, 3, 1));
        req.setVersion("v1");
        req.setReason("mistake");

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    @Test
    @DisplayName("POST /api/perf/recalc：cycleType 非法枚举值 → 400")
    void recalc_invalidCycleType_returns400() throws Exception {
        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("WEEKLY"); // 非法
        req.setCycleDateFrom(LocalDate.of(2026, 3, 1));
        req.setCycleDateTo(LocalDate.of(2026, 3, 31));
        req.setVersion("v1");
        req.setReason("test");

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PerfCalcController.recalc 方法标注 @BizAuth(PERF_CONFIG, EXECUTE) + @AuditLog(reasonRequired=true)")
    void recalc_methodAnnotations() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method method = findRecalcMethod(clazz);
        assertThat(method).as("必须存在 recalc 方法").isNotNull();

        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).as("必须标注 @BizAuth").isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(BizAction.EXECUTE);

        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).as("必须标注 @AuditLog").isNotNull();
        assertThat(auditLog.action()).isEqualTo("PERF_RECALC");
        assertThat(auditLog.resourceType()).isEqualTo("PERF_RUN_TASK");
        assertThat(auditLog.reasonRequired()).as("高危回算必须强制 reason").isTrue();
    }

    private static Method findRecalcMethod(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("recalc")) {
                return m;
            }
        }
        return null;
    }
}
