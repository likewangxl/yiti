package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.ExportAllocReqDTO;
import com.bank.branch.platform.performance.controller.dto.ExportKpiReqDTO;
import com.bank.branch.platform.performance.controller.dto.ExportMetricReqDTO;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.export.PerfExportService;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PerfExportController IT（V1.2 Task Q6.4）.
 *
 * <p>文件名以 {@code Test.java} 结尾（非 IT.java），surefire 默认会扫描执行。
 *
 * <p>覆盖 5 个端点的正反向 + 鉴权/审计注解：
 * <ol>
 *   <li>POST /api/perf/export/kpi     → 返回 ExportTaskRespDTO</li>
 *   <li>POST /api/perf/export/metric  → 返回 ExportTaskRespDTO</li>
 *   <li>POST /api/perf/export/alloc   → 返回 ExportTaskRespDTO（高危）</li>
 *   <li>POST /api/perf/export/detail  → 返回 ExportTaskRespDTO（高危）</li>
 *   <li>GET  /api/perf/export/task/{id} → 返回 ExportTaskRespDTO</li>
 * </ol>
 *
 * <p>@MockBean PerfExportService 隔离策略链（真实策略已在 *StrategyTest 单测覆盖）.
 */
class PerfExportControllerTest extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.PerfExportController";

    @MockBean
    private PerfExportService perfExportService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void resetMock() {
        Mockito.reset(perfExportService);
    }

    // =================== POST /api/perf/export/kpi ===================

    @Test
    void exportKpi_success_returnsTaskId() throws Exception {
        Mockito.when(perfExportService.createTask(eq("KPI"), any(), anyString()))
                .thenReturn("TASK_KPI_001");
        Mockito.when(perfExportService.getTask("TASK_KPI_001"))
                .thenReturn(sampleTask("TASK_KPI_001", "KPI", "SUCCESS"));

        ExportKpiReqDTO req = new ExportKpiReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDate(LocalDate.of(2026, 3, 31));
        req.setAsOfDate(LocalDate.of(2026, 4, 1));

        mockMvc.perform(post("/api/perf/export/kpi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("TASK_KPI_001"))
                .andExpect(jsonPath("$.data.exportType").value("KPI"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void exportKpi_rowsExceedLimit_returnsBusinessError() throws Exception {
        Mockito.when(perfExportService.createTask(eq("KPI"), any(), anyString()))
                .thenThrow(new PerfException(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT, 300000, 200000));

        ExportKpiReqDTO req = new ExportKpiReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDate(LocalDate.of(2026, 3, 31));
        req.setAsOfDate(LocalDate.of(2026, 4, 1));

        mockMvc.perform(post("/api/perf/export/kpi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42207"));
    }

    // =================== POST /api/perf/export/metric ===================

    @Test
    void exportMetric_success_returnsTaskId() throws Exception {
        Mockito.when(perfExportService.createTask(eq("METRIC"), any(), anyString()))
                .thenReturn("TASK_METRIC_001");
        Mockito.when(perfExportService.getTask("TASK_METRIC_001"))
                .thenReturn(sampleTask("TASK_METRIC_001", "METRIC", "SUCCESS"));

        ExportMetricReqDTO req = new ExportMetricReqDTO();
        req.setMetricCodes(List.of("M_TEST"));
        req.setBaseDim("EMP");
        req.setDataDate(LocalDate.of(2026, 4, 1));
        req.setVersion("v20260401");

        mockMvc.perform(post("/api/perf/export/metric")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("TASK_METRIC_001"))
                .andExpect(jsonPath("$.data.exportType").value("METRIC"));
    }

    // =================== POST /api/perf/export/alloc ===================

    @Test
    void exportAlloc_success_returnsTaskId() throws Exception {
        Mockito.when(perfExportService.createTask(eq("ALLOC"), any(), anyString()))
                .thenReturn("TASK_ALLOC_001");
        Mockito.when(perfExportService.getTask("TASK_ALLOC_001"))
                .thenReturn(sampleTask("TASK_ALLOC_001", "ALLOC", "SUCCESS"));

        ExportAllocReqDTO req = new ExportAllocReqDTO();
        req.setEffectiveDate(LocalDate.of(2026, 4, 1));

        mockMvc.perform(post("/api/perf/export/alloc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("TASK_ALLOC_001"));
    }

    // =================== POST /api/perf/export/detail ===================

    @Test
    void exportDetail_success_returnsTaskId() throws Exception {
        Mockito.when(perfExportService.createTask(eq("DETAIL"), any(), anyString()))
                .thenReturn("TASK_DETAIL_001");
        Mockito.when(perfExportService.getTask("TASK_DETAIL_001"))
                .thenReturn(sampleTask("TASK_DETAIL_001", "DETAIL", "SUCCESS"));

        ExportKpiReqDTO req = new ExportKpiReqDTO();
        req.setCycleType("MONTHLY");
        req.setCycleDate(LocalDate.of(2026, 3, 31));
        req.setAsOfDate(LocalDate.of(2026, 4, 1));

        mockMvc.perform(post("/api/perf/export/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("TASK_DETAIL_001"));
    }

    // =================== GET /api/perf/export/task/{id} ===================

    @Test
    void getTask_exists_returnsDTO() throws Exception {
        Mockito.when(perfExportService.getTask("T_OK"))
                .thenReturn(sampleTask("T_OK", "KPI", "SUCCESS"));

        mockMvc.perform(get("/api/perf/export/task/{id}", "T_OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("T_OK"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fileKey").value("perf/export/T_OK/kpi_result.xlsx"));
    }

    @Test
    void getTask_notFound_returnsError() throws Exception {
        Mockito.when(perfExportService.getTask("MISSING"))
                .thenThrow(new PerfException(PerfErrorCode.EXPORT_TASK_NOT_FOUND, "MISSING"));

        mockMvc.perform(get("/api/perf/export/task/{id}", "MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42208"));
    }

    // =================== 注解约束 ===================

    @Test
    void exportKpiMethod_shouldHaveBizAuthExportAndAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("exportKpi",
                ExportKpiReqDTO.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.EXPORT);

        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_EXPORT_KPI");
        assertThat(al.resourceType()).isEqualTo("PERF_EXPORT_TASK");
    }

    @Test
    void exportMetricMethod_shouldHaveBizAuthExport() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("exportMetric",
                ExportMetricReqDTO.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.EXPORT);
        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_EXPORT_METRIC");
    }

    @Test
    void exportAllocMethod_shouldRequireReason() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("exportAlloc",
                ExportAllocReqDTO.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.EXPORT);

        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_EXPORT_ALLOC");
        assertThat(al.reasonRequired())
                .as("分配关系导出为高危，必须 reasonRequired=true").isTrue();
    }

    @Test
    void exportDetailMethod_shouldRequireReason() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("exportDetail",
                ExportKpiReqDTO.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.EXPORT);

        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_EXPORT_DETAIL");
        assertThat(al.reasonRequired())
                .as("KPI 明细导出为高危，必须 reasonRequired=true").isTrue();
    }

    @Test
    void getTaskMethod_shouldHaveBizAuthRead_noAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("getTask", String.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.READ);
        // 读操作不审计
        assertThat(m.getAnnotation(AuditLog.class)).isNull();
    }

    // =================== helpers ===================

    private static PerfExportTask sampleTask(String id, String exportType, String status) {
        PerfExportTask t = new PerfExportTask();
        t.setId(id);
        t.setExportType(exportType);
        t.setStatus(status);
        t.setFileKey("perf/export/" + id + "/kpi_result.xlsx");
        t.setFileSize(2048L);
        t.setRowCount(100);
        t.setExpireAt(LocalDateTime.now().plusDays(7));
        t.setOperatorId("admin");
        t.setCreatedTime(LocalDateTime.now());
        t.setUpdatedTime(LocalDateTime.now());
        return t;
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> params() {
        return Map.of("cycleType", "MONTHLY");
    }
}
