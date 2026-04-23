package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MetricExportStrategy 单元测试（V1.2 Task Q6.3 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>exportType 返回 METRIC</li>
 *   <li>params 解析（metricCodes[] / baseDim / dataDate / version）</li>
 *   <li>成功路径：查询 slots → 写 Excel → 上传 MinIO → 回写 fileKey</li>
 *   <li>params 缺字段 → VALIDATION_FAILED</li>
 *   <li>metricCode 不存在 → METRIC_NOT_FOUND (PERF-40001)</li>
 *   <li>MinIO 抛异常 → EXPORT_FILE_GENERATE_FAILED</li>
 * </ul>
 */
class MetricExportStrategyTest {

    private PerfMetricDefMapper metricDefMapper;
    private EmpIndexResultMapper empIndexResultMapper;
    private OrgIndexResultMapper orgIndexResultMapper;
    private MinioClient minioClient;
    private MetricExportStrategy strategy;

    @BeforeEach
    void setUp() throws Exception {
        metricDefMapper = mock(PerfMetricDefMapper.class);
        empIndexResultMapper = mock(EmpIndexResultMapper.class);
        orgIndexResultMapper = mock(OrgIndexResultMapper.class);
        minioClient = mock(MinioClient.class);
        strategy = new MetricExportStrategy(metricDefMapper, empIndexResultMapper,
                orgIndexResultMapper, minioClient, "branch-platform");

        ObjectWriteResponse resp = mock(ObjectWriteResponse.class);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(resp);
    }

    @Test
    @DisplayName("exportType 返回 METRIC")
    void exportType_returnsMetric() {
        assertThat(strategy.exportType()).isEqualTo("METRIC");
    }

    @Test
    @DisplayName("execute：成功路径 → 查询 slots + 写 Excel + 上传 MinIO + 回写 fileKey")
    void execute_happyPath_uploadsAndReturnsRowCount() throws Exception {
        PerfExportTask task = buildTask("{\"metricCodes\":[\"M_TEST_1\"],\"baseDim\":\"EMP\","
                + "\"dataDate\":\"2026-04-01\",\"version\":\"v20260401\"}");
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TEST_1");
        def.setMetricName("测试指标");
        def.setBaseDim("EMP");
        def.setValSlot(5);
        when(metricDefMapper.selectByMetricCodes(anyList())).thenReturn(List.of(def));
        when(empIndexResultMapper.selectDistinctEmpIds(any(LocalDate.class), anyString()))
                .thenReturn(List.of("E_1", "E_2"));
        // selectSlotValuesByEmps 返回 2 行
        when(empIndexResultMapper.selectSlotValuesByEmps(anyList(), any(LocalDate.class),
                anyString(), any()))
                .thenReturn(sampleMetricRows("E_1", "E_2"));

        int rowCount = strategy.execute(task);

        assertThat(rowCount).isEqualTo(2);
        assertThat(task.getFileKey()).contains("metric_result_").endsWith(".xlsx");
        assertThat(task.getFileSize()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("execute：params 缺 metricCodes → VALIDATION_FAILED (PERF-42200)")
    void execute_missingMetricCodes_throwsValidationFailed() {
        PerfExportTask task = buildTask("{\"baseDim\":\"EMP\",\"dataDate\":\"2026-04-01\","
                + "\"version\":\"v20260401\"}");

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("execute：metricCode 不存在 → METRIC_NOT_FOUND (PERF-40001)")
    void execute_unknownMetric_throwsMetricNotFound() {
        PerfExportTask task = buildTask("{\"metricCodes\":[\"M_MISSING\"],\"baseDim\":\"EMP\","
                + "\"dataDate\":\"2026-04-01\",\"version\":\"v20260401\"}");
        when(metricDefMapper.selectByMetricCodes(anyList())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.METRIC_NOT_FOUND));
    }

    @Test
    @DisplayName("execute：MinIO 抛异常 → EXPORT_FILE_GENERATE_FAILED (PERF-50002)")
    void execute_minioThrows_wraps() throws Exception {
        PerfExportTask task = buildTask("{\"metricCodes\":[\"M_T1\"],\"baseDim\":\"EMP\","
                + "\"dataDate\":\"2026-04-01\",\"version\":\"v20260401\"}");
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_T1");
        def.setBaseDim("EMP");
        def.setValSlot(3);
        when(metricDefMapper.selectByMetricCodes(anyList())).thenReturn(List.of(def));
        when(empIndexResultMapper.selectDistinctEmpIds(any(LocalDate.class), anyString()))
                .thenReturn(List.of("E_1"));
        when(empIndexResultMapper.selectSlotValuesByEmps(anyList(), any(LocalDate.class),
                anyString(), any())).thenReturn(sampleMetricRows("E_1"));
        doThrow(new RuntimeException("S3 down")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED));
    }

    // =================== helpers ===================

    private PerfExportTask buildTask(String paramsJson) {
        PerfExportTask t = new PerfExportTask();
        t.setId("TASK_EXPT_METRIC_1");
        t.setExportType("METRIC");
        t.setParamsJson(paramsJson);
        t.setOperatorId("admin");
        t.setStatus("RUNNING");
        return t;
    }

    private static List<EmpMetricValueRow> sampleMetricRows(String... empIds) {
        List<EmpMetricValueRow> rows = new ArrayList<>();
        for (String id : empIds) {
            EmpMetricValueRow r = new EmpMetricValueRow();
            r.setEmpId(id);
            r.setMetricValue(new BigDecimal("123.45"));
            rows.add(r);
        }
        return rows;
    }
}
