package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiExportStrategy 单元测试（V1.2 Task Q6.2 Red）.
 *
 * <p>KpiResultMapper 与 MinioClient 均 mock 化，验证策略的:
 * <ul>
 *   <li>exportType 标识 = "KPI"</li>
 *   <li>解析 params_json（schemeCode / cycleType / cycleDate / asOfDate）</li>
 *   <li>调 countForExport 预检，超过上限 200000 → EXPORT_ROWS_EXCEEDS_LIMIT (PERF-42207)</li>
 *   <li>查询 KPI 结果，写 easyexcel Excel 字节流，putObject 上传 MinIO</li>
 *   <li>上传成功 → 回写 task.fileKey / task.fileSize，返回 rowCount</li>
 *   <li>参数缺失（schemeCode 空）→ VALIDATION_FAILED (PERF-42200)</li>
 *   <li>MinIO 抛异常 → EXPORT_FILE_GENERATE_FAILED (PERF-50002)</li>
 * </ul>
 */
class KpiExportStrategyTest {

    private KpiResultMapper kpiResultMapper;
    private FileApi fileApi;
    private KpiExportStrategy strategy;

    @BeforeEach
    void setUp() {
        kpiResultMapper = mock(KpiResultMapper.class);
        fileApi = mock(FileApi.class);
        strategy = new KpiExportStrategy(kpiResultMapper, fileApi);

        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F_OBS_KPI");
        when(fileApi.upload(any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dto);
    }

    @Test
    @DisplayName("exportType 返回 KPI")
    void exportType_returnsKpi() {
        assertThat(strategy.exportType()).isEqualTo("KPI");
    }

    @Test
    @DisplayName("execute：成功路径 → 查询 → 写 Excel → 上传 MinIO → 回写 fileKey + 返回 rowCount")
    void execute_happyPath_uploadsAndReturnsRowCount() throws Exception {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");

        when(kpiResultMapper.countForExport(eq("MONTHLY"), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(3L);
        when(kpiResultMapper.selectForExport(eq("MONTHLY"), any(LocalDate.class),
                any(LocalDate.class), any(), anyInt())).thenReturn(sampleKpiResults(3));

        int rowCount = strategy.execute(task);

        assertThat(rowCount).isEqualTo(3);
        assertThat(task.getFileKey()).isEqualTo("F_OBS_KPI");
        assertThat(task.getFileSize()).isNotNull().isPositive();

        // 验证 OBS 上传调用（文件名前缀 + category）
        ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
        verify(fileApi).upload(any(byte[].class), nameCap.capture(), anyString(), anyString(),
                eq(FileCategory.EXPORT_KPI));
        assertThat(nameCap.getValue()).startsWith("kpi_result_").endsWith(".xlsx");
    }

    @Test
    @DisplayName("execute：params 缺 cycleType → 抛 VALIDATION_FAILED (PERF-42200)")
    void execute_missingCycleType_throwsValidationFailed() {
        PerfExportTask task = buildTask("{\"cycleDate\":\"2026-03-31\",\"asOfDate\":\"2026-04-01\"}");

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("execute：行数超限 → 抛 EXPORT_ROWS_EXCEEDS_LIMIT (PERF-42207)")
    void execute_rowsExceedLimit_throwsExportRowsExceedsLimit() {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");
        when(kpiResultMapper.countForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(200001L);

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT));
    }

    @Test
    @DisplayName("execute：OBS 上传抛异常 → 抛 EXPORT_FILE_GENERATE_FAILED (PERF-50002)")
    void execute_obsThrows_wrapsToExportFileGenerateFailed() {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");
        when(kpiResultMapper.countForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(1L);
        when(kpiResultMapper.selectForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any(), anyInt())).thenReturn(sampleKpiResults(1));
        when(fileApi.upload(any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("OBS connection refused"));

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED));
    }

    @Test
    @DisplayName("execute：查询返回 0 行 → 仍上传空 Excel 并返回 0")
    void execute_emptyResult_uploadsEmptyExcel_returnsZero() throws Exception {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");
        when(kpiResultMapper.countForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(0L);
        when(kpiResultMapper.selectForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any(), anyInt())).thenReturn(new ArrayList<>());

        int rowCount = strategy.execute(task);

        assertThat(rowCount).isZero();
        assertThat(task.getFileKey()).isNotBlank();
    }

    // =================== helpers ===================

    private PerfExportTask buildTask(String paramsJson) {
        PerfExportTask t = new PerfExportTask();
        t.setId("TASK_EXPT_KPI_1");
        t.setExportType("KPI");
        t.setParamsJson(paramsJson);
        t.setOperatorId("admin");
        t.setStatus("RUNNING");
        return t;
    }

    private static List<KpiResult> sampleKpiResults(int count) {
        List<KpiResult> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            KpiResult r = new KpiResult();
            r.setId((long) i + 1);
            r.setEmpId("E_" + i);
            r.setCycleType("MONTHLY");
            r.setCycleDate(LocalDate.of(2026, 3, 31));
            r.setAsOfDate(LocalDate.of(2026, 4, 1));
            r.setDataVersion("v20260401");
            r.setKpiTotalScore(new BigDecimal("85.50"));
            r.setCalculatedTime(LocalDateTime.now());
            rows.add(r);
        }
        return rows;
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    @SuppressWarnings("unused")
    private static long anyLongValue() {
        return anyLong();
    }
}
