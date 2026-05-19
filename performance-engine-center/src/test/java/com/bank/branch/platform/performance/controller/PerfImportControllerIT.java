package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.importer.PerfImportService;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PerfImportController IT（V1.1 Task P5.5）.
 *
 * <p>覆盖 5 个端点的正反向 + 鉴权/审计注解：
 * <ol>
 *   <li>POST   /api/perf/import/upload                             → 返回 batchId</li>
 *   <li>GET    /api/perf/import/batches/{batchId}                  → 返回 PerfImportBatchRespDTO</li>
 *   <li>GET    /api/perf/import/batches/{batchId}/errors           → 返回 List&lt;String&gt;</li>
 *   <li>POST   /api/perf/import/batches/{batchId}/retry            → 200 + success</li>
 *   <li>DELETE /api/perf/import/batches/{batchId}                  → 200 + success</li>
 * </ol>
 *
 * <p>@MockBean PerfImportService 隔离策略链（真实策略已在 TargetImportStrategyTest 等单测覆盖）。
 */
class PerfImportControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.PerfImportController";

    @MockBean
    private PerfImportService perfImportService;

    @BeforeEach
    void resetServiceMock() {
        Mockito.reset(perfImportService);
    }

    // =================== upload ===================

    @Test
    void upload_validFile_returnsRespDTO() throws Exception {
        // V1.11：startImport 返回 batchId，controller 通过 getBatchDto 装配为 DTO
        Mockito.when(perfImportService.startImport(eq("TARGET"), any(), anyString()))
                .thenReturn("BATCH_123");
        PerfImportBatchRespDTO batchDto = PerfImportBatchRespDTO.builder()
                .id("BATCH_123")
                .totalRows(8)
                .successRows(8)
                .errorRows(0)
                .updatedRows(0)
                .insertedRows(8)
                .build();
        Mockito.when(perfImportService.getBatchDto("BATCH_123")).thenReturn(batchDto);

        MockMultipartFile file = new MockMultipartFile("file", "targets.xlsx",
                "application/vnd.ms-excel", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/perf/import/upload")
                        .file(file)
                        .param("importType", "TARGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.batchId").value("BATCH_123"))
                .andExpect(jsonPath("$.data.totalRows").value(8))
                .andExpect(jsonPath("$.data.insertedRows").value(8))
                .andExpect(jsonPath("$.data.updatedRows").value(0))
                .andExpect(jsonPath("$.data.errorRows").value(0));
    }

    @Test
    void upload_metricDef_returnsRespDTOWithInsertAndUpdateCounts() throws Exception {
        // V1.11：METRIC_DEF 导入返回新增 + 更新计数
        Mockito.when(perfImportService.startImport(eq("METRIC_DEF"), any(), anyString()))
                .thenReturn("BATCH_M11");
        PerfImportBatchRespDTO batchDto = PerfImportBatchRespDTO.builder()
                .id("BATCH_M11")
                .totalRows(5)
                .successRows(5)
                .errorRows(0)
                .updatedRows(2)
                .insertedRows(3)
                .build();
        Mockito.when(perfImportService.getBatchDto("BATCH_M11")).thenReturn(batchDto);

        MockMultipartFile file = new MockMultipartFile("file", "metric-def.xlsx",
                "application/vnd.ms-excel", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/perf/import/upload")
                        .file(file)
                        .param("importType", "METRIC_DEF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.batchId").value("BATCH_M11"))
                .andExpect(jsonPath("$.data.totalRows").value(5))
                .andExpect(jsonPath("$.data.insertedRows").value(3))
                .andExpect(jsonPath("$.data.updatedRows").value(2));
    }

    @Test
    void upload_missingImportType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "targets.xlsx",
                "application/vnd.ms-excel", new byte[]{1, 2, 3});
        // 缺 importType param → Spring 校验返 400
        mockMvc.perform(multipart("/api/perf/import/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_unknownImportType_returnsBizKindInvalid() throws Exception {
        Mockito.when(perfImportService.startImport(eq("UNKNOWN"), any(), anyString()))
                .thenThrow(new PerfException(PerfErrorCode.BIZ_KIND_INVALID, "UNKNOWN"));

        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.ms-excel", new byte[]{1});

        mockMvc.perform(multipart("/api/perf/import/upload")
                        .file(file)
                        .param("importType", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40002"));
    }

    // =================== getBatch ===================

    @Test
    void getBatch_whenExists_returnsDTO() throws Exception {
        PerfImportBatchRespDTO dto = sampleDto("B_OK", "SUCCESS");
        Mockito.when(perfImportService.getBatchDto("B_OK")).thenReturn(dto);

        mockMvc.perform(get("/api/perf/import/batches/{batchId}", "B_OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("B_OK"))
                .andExpect(jsonPath("$.data.batchNo").value("IMP20260423001"))
                .andExpect(jsonPath("$.data.importType").value("TARGET"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalRows").value(10))
                .andExpect(jsonPath("$.data.successRows").value(10))
                .andExpect(jsonPath("$.data.errorRows").value(0));
    }

    @Test
    void getBatch_whenNotFound_returnsBusinessError() throws Exception {
        Mockito.when(perfImportService.getBatchDto("MISSING"))
                .thenThrow(new PerfException(PerfErrorCode.IMPORT_BATCH_NOT_FOUND, "MISSING"));

        mockMvc.perform(get("/api/perf/import/batches/{batchId}", "MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40017"));
    }

    // =================== getErrors ===================

    @Test
    void getErrors_returnsList() throws Exception {
        Mockito.when(perfImportService.getErrorDetails("B_ERR"))
                .thenReturn(List.of("第2行: empId 必填", "第5行: targetValue 必填"));

        mockMvc.perform(get("/api/perf/import/batches/{batchId}/errors", "B_ERR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value("第2行: empId 必填"))
                .andExpect(jsonPath("$.data[1]").value("第5行: targetValue 必填"));
    }

    @Test
    void getErrors_whenEmpty_returnsEmptyList() throws Exception {
        Mockito.when(perfImportService.getErrorDetails("B_EMPTY")).thenReturn(List.of());

        mockMvc.perform(get("/api/perf/import/batches/{batchId}/errors", "B_EMPTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // =================== retry ===================

    @Test
    void retry_success_returns200() throws Exception {
        Mockito.doNothing().when(perfImportService).retry("B_RT");

        mockMvc.perform(post("/api/perf/import/batches/{batchId}/retry", "B_RT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        Mockito.verify(perfImportService).retry("B_RT");
    }

    @Test
    void retry_whenNonFailedStatus_returnsValidationError() throws Exception {
        Mockito.doThrow(new PerfException(PerfErrorCode.VALIDATION_FAILED, "仅 FAILED 可重试"))
                .when(perfImportService).retry("B_NOT_FAILED");

        mockMvc.perform(post("/api/perf/import/batches/{batchId}/retry", "B_NOT_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    // =================== delete ===================

    @Test
    void delete_success_returns200() throws Exception {
        Mockito.doNothing().when(perfImportService).delete("B_DEL");

        mockMvc.perform(delete("/api/perf/import/batches/{batchId}", "B_DEL")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        Mockito.verify(perfImportService).delete("B_DEL");
    }

    @Test
    void delete_whenRunningStatus_returnsValidationError() throws Exception {
        Mockito.doThrow(new PerfException(PerfErrorCode.VALIDATION_FAILED, "仅终态可删"))
                .when(perfImportService).delete("B_RUNNING");

        mockMvc.perform(delete("/api/perf/import/batches/{batchId}", "B_RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    // =================== 注解约束 ===================

    @Test
    void uploadMethod_shouldDeclareBizAuthAndAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN)
                .getDeclaredMethod("upload", String.class,
                        org.springframework.web.multipart.MultipartFile.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.IMPORT);

        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_IMPORT_UPLOAD");
        assertThat(al.resourceType()).isEqualTo("PERF_IMPORT_BATCH");
    }

    @Test
    void getBatchMethod_shouldHaveBizAuth_noAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("getBatch", String.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.READ);
        // 读操作不做审计
        assertThat(m.getAnnotation(AuditLog.class)).isNull();
    }

    @Test
    void getErrorsMethod_shouldHaveBizAuth_noAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("getErrors", String.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.READ);
        assertThat(m.getAnnotation(AuditLog.class)).isNull();
    }

    @Test
    void retryMethod_shouldDeclareBizAuthAndAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("retry", String.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.EXECUTE);

        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_IMPORT_RETRY");
    }

    @Test
    void deleteMethod_shouldRequireReason() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN).getDeclaredMethod("delete", String.class);
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.action()).isEqualTo(BizAction.DELETE);

        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.action()).isEqualTo("PERF_IMPORT_DELETE");
        assertThat(al.resourceType()).isEqualTo("PERF_IMPORT_BATCH");
        assertThat(al.reasonRequired()).as("delete 为高危操作，必须 reasonRequired=true").isTrue();
    }

    // =================== helpers ===================

    /**
     * V1.3 R4.1：Controller 改为返回 DTO，测试构造 DTO 样本代替 entity.
     */
    private static PerfImportBatchRespDTO sampleDto(String id, String status) {
        return PerfImportBatchRespDTO.builder()
                .id(id)
                .batchNo("IMP20260423001")
                .importType("TARGET")
                .fileName("targets.xlsx")
                .status(status)
                .totalRows(10)
                .successRows(10)
                .errorRows(0)
                .remark(null)
                .createdBy("admin")
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
    }
}
