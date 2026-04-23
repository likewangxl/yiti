package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AllocExportStrategy 单元测试（V1.2 Task Q6.3 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>exportType 返回 ALLOC</li>
 *   <li>成功路径：查询分配关系 → 写 Excel → 上传 MinIO</li>
 *   <li>params 缺 effectiveDate → VALIDATION_FAILED</li>
 *   <li>行数超限 → EXPORT_ROWS_EXCEEDS_LIMIT</li>
 *   <li>MinIO 抛异常 → EXPORT_FILE_GENERATE_FAILED</li>
 * </ul>
 */
class AllocExportStrategyTest {

    private CustAllocRelationMapper allocRelationMapper;
    private MinioClient minioClient;
    private AllocExportStrategy strategy;

    @BeforeEach
    void setUp() throws Exception {
        allocRelationMapper = mock(CustAllocRelationMapper.class);
        minioClient = mock(MinioClient.class);
        strategy = new AllocExportStrategy(allocRelationMapper, minioClient, "branch-platform");

        ObjectWriteResponse resp = mock(ObjectWriteResponse.class);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(resp);
    }

    @Test
    @DisplayName("exportType 返回 ALLOC")
    void exportType_returnsAlloc() {
        assertThat(strategy.exportType()).isEqualTo("ALLOC");
    }

    @Test
    @DisplayName("execute：成功路径 → 查询分配关系 → 写 Excel → 上传 MinIO")
    void execute_happyPath_uploadsAndReturnsRowCount() {
        PerfExportTask task = buildTask("{\"effectiveDate\":\"2026-04-01\",\"bizKind\":\"CORP_LOAN\"}");
        when(allocRelationMapper.countForExport(any(), any(), any(LocalDate.class))).thenReturn(3L);
        when(allocRelationMapper.selectForExport(any(), any(), any(LocalDate.class), anyInt()))
                .thenReturn(sampleAllocRelations(3));

        int rowCount = strategy.execute(task);

        assertThat(rowCount).isEqualTo(3);
        assertThat(task.getFileKey()).contains("alloc_relation_").endsWith(".xlsx");
    }

    @Test
    @DisplayName("execute：params 缺 effectiveDate → VALIDATION_FAILED")
    void execute_missingEffectiveDate_throwsValidationFailed() {
        PerfExportTask task = buildTask("{\"bizKind\":\"CORP_LOAN\"}");

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("execute：行数超限 → EXPORT_ROWS_EXCEEDS_LIMIT")
    void execute_rowsExceedLimit_throws() {
        PerfExportTask task = buildTask("{\"effectiveDate\":\"2026-04-01\"}");
        when(allocRelationMapper.countForExport(any(), any(), any(LocalDate.class))).thenReturn(200001L);

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT));
    }

    @Test
    @DisplayName("execute：MinIO 抛异常 → EXPORT_FILE_GENERATE_FAILED")
    void execute_minioThrows_wraps() throws Exception {
        PerfExportTask task = buildTask("{\"effectiveDate\":\"2026-04-01\"}");
        when(allocRelationMapper.countForExport(any(), any(), any(LocalDate.class))).thenReturn(1L);
        when(allocRelationMapper.selectForExport(any(), any(), any(LocalDate.class), anyInt()))
                .thenReturn(sampleAllocRelations(1));
        doThrow(new RuntimeException("S3 down")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> strategy.execute(task))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED));
    }

    // =================== helpers ===================

    private PerfExportTask buildTask(String paramsJson) {
        PerfExportTask t = new PerfExportTask();
        t.setId("TASK_EXPT_ALLOC_1");
        t.setExportType("ALLOC");
        t.setParamsJson(paramsJson);
        t.setOperatorId("admin");
        t.setStatus("RUNNING");
        return t;
    }

    private static List<CustAllocRelation> sampleAllocRelations(int count) {
        List<CustAllocRelation> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CustAllocRelation r = new CustAllocRelation();
            r.setId("AR_" + i);
            r.setCustId("C_" + i);
            r.setAllocDim("RULE");
            r.setBizKind("CORP_LOAN");
            r.setEmpId("E_" + i);
            r.setRatio(new BigDecimal("100.00"));
            r.setEffectiveDate(LocalDate.of(2026, 1, 1));
            rows.add(r);
        }
        return rows;
    }
}
