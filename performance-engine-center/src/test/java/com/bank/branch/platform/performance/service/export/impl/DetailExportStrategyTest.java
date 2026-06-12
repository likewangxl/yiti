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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DetailExportStrategy 单元测试（V1.2 Task Q6.3 Red）.
 *
 * <p>核心逻辑：每条 KPI 主记录的 detail_json 包含 items 数组，每个 item 拆成一行 Excel。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>exportType 返回 DETAIL</li>
 *   <li>成功路径：查询 KPI → 拆分 items → 写 Excel → 上传 MinIO</li>
 *   <li>params 缺 cycleType → VALIDATION_FAILED</li>
 *   <li>空 detail_json 不抛错（只导出 0 条）</li>
 *   <li>行数超限 → EXPORT_ROWS_EXCEEDS_LIMIT</li>
 *   <li>MinIO 抛异常 → EXPORT_FILE_GENERATE_FAILED</li>
 * </ul>
 */
class DetailExportStrategyTest {

    private KpiResultMapper kpiResultMapper;
    private FileApi fileApi;
    private DetailExportStrategy strategy;

    @BeforeEach
    void setUp() {
        kpiResultMapper = mock(KpiResultMapper.class);
        fileApi = mock(FileApi.class);
        strategy = new DetailExportStrategy(kpiResultMapper, fileApi);

        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F_OBS_DETAIL");
        when(fileApi.upload(any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dto);
    }

    @Test
    @DisplayName("exportType 返回 DETAIL")
    void exportType_returnsDetail() {
        assertThat(strategy.exportType()).isEqualTo("DETAIL");
    }

    @Test
    @DisplayName("execute：成功路径 → 2 KPI × 3 items = 6 行")
    void execute_happyPath_expandsItemsToRows() {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");
        when(kpiResultMapper.countForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(2L);
        when(kpiResultMapper.selectForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any(), anyInt()))
                .thenReturn(List.of(sampleKpi("E_1"), sampleKpi("E_2")));

        int rowCount = strategy.execute(task);

        assertThat(rowCount).isEqualTo(6);  // 2 员工 × 3 items
        assertThat(task.getFileKey()).isEqualTo("F_OBS_DETAIL");
        verify(fileApi).upload(any(byte[].class), anyString(), anyString(), anyString(),
                eq(FileCategory.EXPORT_DETAIL));
    }

    @Test
    @DisplayName("execute：params 缺 cycleType → VALIDATION_FAILED")
    void execute_missingCycleType_throwsValidationFailed() {
        PerfExportTask task = buildTask("{\"cycleDate\":\"2026-03-31\"}");

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("execute：detail_json 为空 → 返回 0 行，不报错")
    void execute_emptyDetailJson_returnsZero() {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");
        KpiResult r = new KpiResult();
        r.setEmpId("E_1");
        r.setCycleType("MONTHLY");
        r.setCycleDate(LocalDate.of(2026, 3, 31));
        r.setAsOfDate(LocalDate.of(2026, 4, 1));
        r.setKpiTotalScore(new BigDecimal("85.00"));
        r.setDetailJson(null);
        r.setCalculatedTime(LocalDateTime.now());

        when(kpiResultMapper.countForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(1L);
        when(kpiResultMapper.selectForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any(), anyInt()))
                .thenReturn(List.of(r));

        int rowCount = strategy.execute(task);

        assertThat(rowCount).isZero();
    }

    @Test
    @DisplayName("execute：行数超限 → EXPORT_ROWS_EXCEEDS_LIMIT")
    void execute_rowsExceedLimit_throws() {
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
    @DisplayName("execute：OBS 抛异常 → EXPORT_FILE_GENERATE_FAILED")
    void execute_obsThrows_wraps() {
        PerfExportTask task = buildTask("{\"cycleType\":\"MONTHLY\",\"cycleDate\":\"2026-03-31\","
                + "\"asOfDate\":\"2026-04-01\"}");
        when(kpiResultMapper.countForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any())).thenReturn(1L);
        when(kpiResultMapper.selectForExport(anyString(), any(LocalDate.class),
                any(LocalDate.class), any(), anyInt())).thenReturn(List.of(sampleKpi("E_1")));
        when(fileApi.upload(any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("OBS down"));

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED));
    }

    // =================== helpers ===================

    private PerfExportTask buildTask(String paramsJson) {
        PerfExportTask t = new PerfExportTask();
        t.setId("TASK_EXPT_DET_1");
        t.setExportType("DETAIL");
        t.setParamsJson(paramsJson);
        t.setOperatorId("admin");
        t.setStatus("RUNNING");
        return t;
    }

    /** 每条 KPI 的 detail_json 含 3 items. */
    private static KpiResult sampleKpi(String empId) {
        KpiResult r = new KpiResult();
        r.setEmpId(empId);
        r.setCycleType("MONTHLY");
        r.setCycleDate(LocalDate.of(2026, 3, 31));
        r.setAsOfDate(LocalDate.of(2026, 4, 1));
        r.setKpiTotalScore(new BigDecimal("85.50"));
        r.setDetailJson("{\"items\":["
                + "{\"itemCode\":\"M_DEP\",\"metricValue\":100,\"targetValue\":120,\"weight\":0.3,\"score\":25},"
                + "{\"itemCode\":\"M_LOAN\",\"metricValue\":80,\"targetValue\":90,\"weight\":0.3,\"score\":22},"
                + "{\"itemCode\":\"M_NPL\",\"metricValue\":0.8,\"targetValue\":0.5,\"weight\":0.4,\"score\":28}"
                + "]}");
        r.setCalculatedTime(LocalDateTime.now());
        return r;
    }
}
