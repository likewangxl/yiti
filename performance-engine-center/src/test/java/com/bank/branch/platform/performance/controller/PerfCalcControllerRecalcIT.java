package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.RecalcReqDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.HistoryRecalcService;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
class PerfCalcControllerRecalcIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.PerfCalcController";

    @MockBean
    private HistoryRecalcService historyRecalcService;

    /**
     * V1.3 R4.2：mock PerfRunTaskService 以控制 recalc 响应 status 读取链路的上游数据。
     *
     * <p>Controller 通过 PerfCalcApi.getRunTask 读 status，其底层由
     * PerfRunTaskService.getById 提供 entity.
     */
    @MockBean
    private PerfRunTaskService perfRunTaskService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Mockito.reset(historyRecalcService, perfRunTaskService);
    }

    @Test
    @DisplayName("POST /api/perf/recalc：正常请求返回 taskId + status")
    void recalc_validRequest_returnsTaskId() throws Exception {
        Mockito.when(historyRecalcService.recalc(
                any(LocalDate.class), any(LocalDate.class),
                any(), anyString(), anyString(), anyString(), any()))
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
                any(), anyString(), anyString(), anyString(), any()))
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
    @DisplayName("V1.3 R4.2：POST /api/perf/recalc status 读取 perf_run_task 真实终态，非硬编码 RUNNING")
    void recalc_returnsRealStatus_notHardcodedRunning() throws Exception {
        // HistoryRecalcService 已同步执行完成，返回父 taskId
        Mockito.when(historyRecalcService.recalc(
                any(LocalDate.class), any(LocalDate.class),
                any(), anyString(), anyString(), anyString(), any()))
                .thenReturn("PARENT_TASK_REAL");
        // PerfRunTaskService.getById 返回终态为 SUCCESS 的 task（PerfCalcApi.getRunTask 底层）
        PerfRunTask finished = new PerfRunTask();
        finished.setId("PARENT_TASK_REAL");
        finished.setStatus("SUCCESS");
        Mockito.when(perfRunTaskService.getById("PARENT_TASK_REAL"))
                .thenReturn(java.util.Optional.of(finished));

        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDateFrom(LocalDate.of(2026, 3, 1));
        req.setCycleDateTo(LocalDate.of(2026, 3, 31));
        req.setMetricCodes(List.of("M_EMP_A"));
        req.setVersion("v20260301");
        req.setReason("Q1 补录");

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value("PARENT_TASK_REAL"))
                // V1.3 R4.2 断言：Controller 必须读 perf_run_task 真实 status，
                // 不能再硬编码 "RUNNING"
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("V1.3 R4.2：perf_run_task 查不到时退化到 RUNNING 占位")
    void recalc_whenTaskMissing_fallsBackToRunning() throws Exception {
        Mockito.when(historyRecalcService.recalc(
                any(LocalDate.class), any(LocalDate.class),
                any(), anyString(), anyString(), anyString(), any()))
                .thenReturn("PARENT_TASK_MISSING");
        // 极端竞态：Service 尚未 commit，Controller 读不到，退化到占位 RUNNING
        Mockito.when(perfRunTaskService.getById("PARENT_TASK_MISSING"))
                .thenReturn(java.util.Optional.empty());

        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDateFrom(LocalDate.of(2026, 3, 1));
        req.setCycleDateTo(LocalDate.of(2026, 3, 31));
        req.setVersion("v1");
        req.setReason("test");

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value("PARENT_TASK_MISSING"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    /**
     * V1.4 S4.1 Red：getRunTask 返回 Optional.empty 退化到占位 RUNNING 时，必须打 warn 日志
     * 以便运维从日志中定位"Service 未 commit"类极端竞态。Reviewer R4.2 建议项。
     *
     * <p>背景：V1.3 R4.2 已把 /api/perf/recalc 响应 status 改为读 perf_run_task 真实终态。
     * 但 Optional.empty 的 fallback 分支当前沉默返回 "RUNNING"，无日志；极端竞态（Service
     * 未及时 commit 到 Controller 读库之间）发生时，运维无任何可观测性。
     * V1.4 S4.1 要求 fallback 时输出 log.warn 含 parentTaskId + "查不到" 关键字.
     */
    @Test
    @DisplayName("V1.4 S4.1：fallback 到 RUNNING 占位时必须 log.warn 含 parentTaskId + '查不到'")
    void recalc_whenTaskMissing_logsWarning(CapturedOutput output) throws Exception {
        Mockito.when(historyRecalcService.recalc(
                any(LocalDate.class), any(LocalDate.class),
                any(), anyString(), anyString(), anyString(), any()))
                .thenReturn("PARENT_TASK_WARN");
        // 触发 Optional.empty fallback 分支
        Mockito.when(perfRunTaskService.getById("PARENT_TASK_WARN"))
                .thenReturn(java.util.Optional.empty());

        RecalcReqDTO req = new RecalcReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDateFrom(LocalDate.of(2026, 3, 1));
        req.setCycleDateTo(LocalDate.of(2026, 3, 31));
        req.setVersion("v1");
        req.setReason("warn log check");

        mockMvc.perform(post("/api/perf/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        // 断言 fallback 时输出 warn 日志，包含 [recalc] 前缀 + parentTaskId + "查不到"
        assertThat(output.getAll())
                .as("fallback 到 RUNNING 必须打 warn 日志供运维审计")
                .contains("[recalc]")
                .contains("PARENT_TASK_WARN")
                .contains("查不到");
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
