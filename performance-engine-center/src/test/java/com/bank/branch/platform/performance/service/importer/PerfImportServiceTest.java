package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfImportBatchMapper;
import com.bank.branch.platform.performance.service.importer.impl.AllocRelationImportStrategy;
import com.bank.branch.platform.performance.service.importer.impl.BaseDataImportStrategy;
import com.bank.branch.platform.performance.service.importer.impl.PerfImportServiceImpl;
import com.bank.branch.platform.performance.service.importer.impl.TargetImportStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfImportService 单元测试（Task P5.1 Red）.
 *
 * <p>目标：验证"importType → 策略"路由正确性、批次生命周期（CREATED → RUNNING → SUCCESS/FAILED）、
 * 以及未知 importType 的 BIZ_KIND_INVALID 错误码。
 *
 * <p>测试风格：策略 mock 化，仅验证 Service 的分发 + 批次落库，不测试实际 Excel 解析。
 */
class PerfImportServiceTest {

    private PerfImportBatchMapper batchMapper;
    private TargetImportStrategy targetStrategy;
    private BaseDataImportStrategy baseDataStrategy;
    private AllocRelationImportStrategy allocStrategy;
    private PerfImportServiceImpl service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(PerfImportBatchMapper.class);
        targetStrategy = mock(TargetImportStrategy.class);
        baseDataStrategy = mock(BaseDataImportStrategy.class);
        allocStrategy = mock(AllocRelationImportStrategy.class);

        when(targetStrategy.importType()).thenReturn("TARGET");
        when(baseDataStrategy.importType()).thenReturn("BASE_DATA");
        when(allocStrategy.importType()).thenReturn("ALLOC");

        // 默认每个策略返回空结果（子测试按需覆盖）
        ImportResult empty = new ImportResult(0, 0, 0, null);
        when(targetStrategy.execute(any(), any())).thenReturn(empty);
        when(baseDataStrategy.execute(any(), any())).thenReturn(empty);
        when(allocStrategy.execute(any(), any())).thenReturn(empty);

        List<ImportStrategy> strategies = List.of(targetStrategy, baseDataStrategy, allocStrategy);
        service = new PerfImportServiceImpl(batchMapper, strategies);
    }

    private MultipartFile fakeFile(String name) {
        return new MockMultipartFile("file", name, "application/vnd.ms-excel",
                new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("startImport：importType=TARGET → 路由到 TargetImportStrategy")
    void startImport_routesByImportType_target() {
        MultipartFile f = fakeFile("targets.xlsx");

        String batchId = service.startImport("TARGET", f, "admin");

        assertThat(batchId).isNotBlank();
        verify(targetStrategy).execute(any(PerfImportBatch.class), eq(f));
        verify(baseDataStrategy, never()).execute(any(), any());
        verify(allocStrategy, never()).execute(any(), any());
    }

    @Test
    @DisplayName("startImport：importType=BASE_DATA → 路由到 BaseDataImportStrategy")
    void startImport_routesByImportType_baseData() {
        MultipartFile f = fakeFile("base.xlsx");

        service.startImport("BASE_DATA", f, "admin");

        verify(baseDataStrategy).execute(any(PerfImportBatch.class), eq(f));
        verify(targetStrategy, never()).execute(any(), any());
    }

    @Test
    @DisplayName("startImport：importType=ALLOC → 路由到 AllocRelationImportStrategy")
    void startImport_routesByImportType_alloc() {
        MultipartFile f = fakeFile("alloc.xlsx");

        service.startImport("ALLOC", f, "admin");

        verify(allocStrategy).execute(any(PerfImportBatch.class), eq(f));
        verify(targetStrategy, never()).execute(any(), any());
    }

    @Test
    @DisplayName("startImport：未知 importType → 抛 BIZ_KIND_INVALID (PERF-40002)")
    void startImport_unknownImportType_throwsBizKindInvalid() {
        MultipartFile f = fakeFile("foo.xlsx");

        assertThatThrownBy(() -> service.startImport("UNKNOWN_TYPE", f, "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.BIZ_KIND_INVALID));
    }

    @Test
    @DisplayName("startImport：importType 为空白 → 抛 VALIDATION_FAILED (PERF-42200)")
    void startImport_blankImportType_throwsValidationFailed() {
        assertThatThrownBy(() -> service.startImport("", fakeFile("f.xlsx"), "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("startImport：file 为空 → 抛 VALIDATION_FAILED (PERF-42200)")
    void startImport_nullFile_throwsValidationFailed() {
        assertThatThrownBy(() -> service.startImport("TARGET", null, "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("startImport：成功路径 → 批次 insert 后状态迁移为 SUCCESS + 行数写入")
    void startImport_happyPath_insertsAndUpdatesToSuccess() {
        when(targetStrategy.execute(any(), any()))
                .thenReturn(new ImportResult(10, 10, 0, null));

        String batchId = service.startImport("TARGET", fakeFile("t.xlsx"), "admin");

        assertThat(batchId).isNotBlank();

        // 批次 insert 一次（CREATED 初始状态）
        ArgumentCaptor<PerfImportBatch> insertCaptor = ArgumentCaptor.forClass(PerfImportBatch.class);
        verify(batchMapper).insert(insertCaptor.capture());
        PerfImportBatch inserted = insertCaptor.getValue();
        assertThat(inserted.getImportType()).isEqualTo("TARGET");
        assertThat(inserted.getCreatedBy()).isEqualTo("admin");
        assertThat(inserted.getStatus()).isIn("CREATED", "RUNNING");

        // updateStatus 至少调 1 次迁移到 RUNNING，最终 SUCCESS
        verify(batchMapper).updateStatus(eq(batchId), eq("RUNNING"), any());
        verify(batchMapper).updateStatus(eq(batchId), eq("SUCCESS"), any());
        verify(batchMapper).updateCounts(eq(batchId), eq(10), eq(10), eq(0));
    }

    @Test
    @DisplayName("startImport：策略抛异常 → 批次 updateStatus=FAILED 并向上抛")
    void startImport_strategyThrows_marksFailed_andRethrows() {
        doAnswer(inv -> {
            throw new RuntimeException("parse fail");
        }).when(targetStrategy).execute(any(), any());

        assertThatThrownBy(() -> service.startImport("TARGET", fakeFile("t.xlsx"), "admin"))
                .isInstanceOf(RuntimeException.class);

        verify(batchMapper).updateStatus(anyString(), eq("FAILED"), any());
    }

    @Test
    @DisplayName("getBatch：批次存在 → 返回；不存在 → 抛 IMPORT_BATCH_NOT_FOUND (PERF-40017)")
    void getBatch_notFound_throwsImportBatchNotFound() {
        when(batchMapper.selectByBatchId("MISSING")).thenReturn(null);

        assertThatThrownBy(() -> service.getBatch("MISSING"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_NOT_FOUND));
    }

    @Test
    @DisplayName("delete：仅终态可删 → 非终态抛 VALIDATION_FAILED")
    void delete_whenStatusIsRunning_throwsValidationFailed() {
        PerfImportBatch b = new PerfImportBatch();
        b.setId("B100");
        b.setStatus("RUNNING");
        when(batchMapper.selectByBatchId("B100")).thenReturn(b);

        assertThatThrownBy(() -> service.delete("B100"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("retry：仅 FAILED 可重试 → SUCCESS 状态抛 VALIDATION_FAILED")
    void retry_whenStatusIsSuccess_throwsValidationFailed() {
        PerfImportBatch b = new PerfImportBatch();
        b.setId("B200");
        b.setStatus("SUCCESS");
        when(batchMapper.selectByBatchId("B200")).thenReturn(b);

        assertThatThrownBy(() -> service.retry("B200"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }
}
