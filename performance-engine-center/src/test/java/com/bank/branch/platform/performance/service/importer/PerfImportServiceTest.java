package com.bank.branch.platform.performance.service.importer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
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
    private LocalImportFileStorage localFileStorage;
    private CurrentUserApi currentUserApi;
    private BizScopeApi bizScopeApi;
    private PerfImportServiceImpl service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(PerfImportBatchMapper.class);
        targetStrategy = mock(TargetImportStrategy.class);
        baseDataStrategy = mock(BaseDataImportStrategy.class);
        allocStrategy = mock(AllocRelationImportStrategy.class);
        localFileStorage = mock(LocalImportFileStorage.class);
        currentUserApi = mock(CurrentUserApi.class);
        bizScopeApi = mock(BizScopeApi.class);

        when(targetStrategy.importType()).thenReturn("TARGET");
        when(baseDataStrategy.importType()).thenReturn("BASE_DATA");
        when(allocStrategy.importType()).thenReturn("ALLOC");

        // 默认每个策略返回空结果（子测试按需覆盖）
        ImportResult empty = new ImportResult(0, 0, 0, null);
        when(targetStrategy.execute(any(), any(), any())).thenReturn(empty);
        when(baseDataStrategy.execute(any(), any(), any())).thenReturn(empty);
        when(allocStrategy.execute(any(), any(), any())).thenReturn(empty);

        // 源文件保存到本地默认返回一个 key
        when(localFileStorage.save(any(MultipartFile.class))).thenReturn("F_IMP_SRC");

        // 默认当前用户 admin + 数据范围 ALL（管理员全见），子测试按需覆盖
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope(anyString(), any(BizType.class))).thenReturn(DataScopeType.ALL);

        List<ImportStrategy> strategies = List.of(targetStrategy, baseDataStrategy, allocStrategy);
        service = new PerfImportServiceImpl(batchMapper, strategies, localFileStorage, currentUserApi, bizScopeApi);
    }

    private MultipartFile fakeFile(String name) {
        return new MockMultipartFile("file", name, "application/vnd.ms-excel",
                new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("startImport：importType=TARGET → 路由到 TargetImportStrategy")
    void startImport_routesByImportType_target() {
        MultipartFile f = fakeFile("targets.xlsx");

        String batchId = service.startImport("TARGET", f, "admin", null, null, true);

        assertThat(batchId).isNotBlank();
        verify(targetStrategy).execute(any(PerfImportBatch.class), eq(f), any());
        verify(baseDataStrategy, never()).execute(any(), any(), any());
        verify(allocStrategy, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("startImport：importType=BASE_DATA → 路由到 BaseDataImportStrategy")
    void startImport_routesByImportType_baseData() {
        MultipartFile f = fakeFile("base.xlsx");

        service.startImport("BASE_DATA", f, "admin", null, null, true);

        verify(baseDataStrategy).execute(any(PerfImportBatch.class), eq(f), any());
        verify(targetStrategy, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("startImport：importType=ALLOC → 路由到 AllocRelationImportStrategy")
    void startImport_routesByImportType_alloc() {
        MultipartFile f = fakeFile("alloc.xlsx");

        service.startImport("ALLOC", f, "admin", null, null, true);

        verify(allocStrategy).execute(any(PerfImportBatch.class), eq(f), any());
        verify(targetStrategy, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("startImport：未知 importType → 抛 BIZ_KIND_INVALID (PERF-40002)")
    void startImport_unknownImportType_throwsBizKindInvalid() {
        MultipartFile f = fakeFile("foo.xlsx");

        assertThatThrownBy(() -> service.startImport("UNKNOWN_TYPE", f, "admin", null, null, true))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.BIZ_KIND_INVALID));
    }

    @Test
    @DisplayName("startImport：importType 为空白 → 抛 VALIDATION_FAILED (PERF-42200)")
    void startImport_blankImportType_throwsValidationFailed() {
        assertThatThrownBy(() -> service.startImport("", fakeFile("f.xlsx"), "admin", null, null, true))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("startImport：file 为空 → 抛 VALIDATION_FAILED (PERF-42200)")
    void startImport_nullFile_throwsValidationFailed() {
        assertThatThrownBy(() -> service.startImport("TARGET", null, "admin", null, null, true))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("startImport：成功路径 → 批次 insert 后状态迁移为 SUCCESS + 行数写入")
    void startImport_happyPath_insertsAndUpdatesToSuccess() {
        when(targetStrategy.execute(any(), any(), any()))
                .thenReturn(new ImportResult(10, 10, 0, null));

        String batchId = service.startImport("TARGET", fakeFile("t.xlsx"), "admin", null, null, true);

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
        verify(batchMapper).updateCounts(eq(batchId), eq(10), eq(10), eq(0), eq(0));
    }

    @Test
    @DisplayName("startImport archiveSource=true → 存本地目录：localFileStorage.save 调用 + sourceObjectKey 落库")
    void startImport_archiveSourceTrue_savesToLocalDir() {
        String batchId = service.startImport("TARGET", fakeFile("imp.xlsx"), "admin", null, null, true);

        assertThat(batchId).isNotBlank();
        verify(localFileStorage).save(any(MultipartFile.class));

        ArgumentCaptor<PerfImportBatch> cap = ArgumentCaptor.forClass(PerfImportBatch.class);
        verify(batchMapper).insert(cap.capture());
        assertThat(cap.getValue().getSourceObjectKey()).isEqualTo("F_IMP_SRC");
    }

    @Test
    @DisplayName("startImport：强制保存——archiveSource=false 仍存本地目录并落 sourceObjectKey")
    void startImport_forcedSave_evenWhenArchiveSourceFalse() {
        String batchId = service.startImport("TARGET", fakeFile("imp.xlsx"), "admin", null, null, false);

        assertThat(batchId).isNotBlank();
        verify(localFileStorage).save(any(MultipartFile.class));

        ArgumentCaptor<PerfImportBatch> cap = ArgumentCaptor.forClass(PerfImportBatch.class);
        verify(batchMapper).insert(cap.capture());
        assertThat(cap.getValue().getSourceObjectKey()).isEqualTo("F_IMP_SRC");
    }

    @Test
    @DisplayName("startImport：策略抛异常 → 批次 updateStatus=FAILED 并向上抛")
    void startImport_strategyThrows_marksFailed_andRethrows() {
        doAnswer(inv -> {
            throw new RuntimeException("parse fail");
        }).when(targetStrategy).execute(any(), any(), any());

        assertThatThrownBy(() -> service.startImport("TARGET", fakeFile("t.xlsx"), "admin", null, null, true))
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

    // ---------- pageBatches（DATA_SCOPE）----------

    @Test
    @DisplayName("pageBatches：管理员(ALL) → 返回映射后的 DTO 分页（含 sourceObjectKey）")
    void pageBatches_adminScopeAll_returnsMappedDtos() {
        when(bizScopeApi.resolveScope(anyString(), any(BizType.class))).thenReturn(DataScopeType.ALL);
        stubSelectPageReturnsOne();

        PageResult<PerfImportBatchRespDTO> r = service.pageBatches(1, 10);

        assertThat(r.getRecords()).hasSize(1);
        assertThat(r.getTotal()).isEqualTo(1L);
        assertThat(r.getRecords().get(0).getSourceObjectKey()).isEqualTo("OBJ1");
        verify(batchMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("pageBatches：普通用户(非ALL) → 正常返回（created_by 过滤由 resolveSelfEmpId 施加，见下载越权用例）")
    void pageBatches_nonAdminScope_returnsList() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("u001");
        when(bizScopeApi.resolveScope(anyString(), any(BizType.class))).thenReturn(DataScopeType.SELF);
        stubSelectPageReturnsOne();

        PageResult<PerfImportBatchRespDTO> r = service.pageBatches(1, 10);

        assertThat(r.getRecords()).hasSize(1);
        verify(batchMapper).selectPage(any(), any());
    }

    // ---------- getSourceFile（下载 + 数据范围）----------

    @Test
    @DisplayName("getSourceFile：正常 → 返回文件名 + 本地目录字节")
    void getSourceFile_happyPath_returnsBytes() {
        PerfImportBatch b = batchWith("B1", "admin", "OBJ1");
        when(batchMapper.selectByBatchId("B1")).thenReturn(b);
        when(localFileStorage.read("OBJ1")).thenReturn(new byte[]{9, 8, 7});

        PerfImportService.ImportSourceFile src = service.getSourceFile("B1");

        assertThat(src.fileName()).isEqualTo("imp.xlsx");
        assertThat(src.content()).containsExactly(9, 8, 7);
    }

    @Test
    @DisplayName("getSourceFile：普通用户下载他人批次 → IMPORT_BATCH_NO_PERMISSION")
    void getSourceFile_nonAdminOthersBatch_throwsNoPermission() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("u001");
        when(bizScopeApi.resolveScope(anyString(), any(BizType.class))).thenReturn(DataScopeType.SELF);
        when(batchMapper.selectByBatchId("B1")).thenReturn(batchWith("B1", "someoneElse", "OBJ1"));

        assertThatThrownBy(() -> service.getSourceFile("B1"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_NO_PERMISSION));
    }

    @Test
    @DisplayName("getSourceFile：管理员可下载他人批次")
    void getSourceFile_adminOthersBatch_allowed() {
        when(bizScopeApi.resolveScope(anyString(), any(BizType.class))).thenReturn(DataScopeType.ALL);
        when(batchMapper.selectByBatchId("B1")).thenReturn(batchWith("B1", "someoneElse", "OBJ1"));
        when(localFileStorage.read("OBJ1")).thenReturn(new byte[]{1});

        PerfImportService.ImportSourceFile src = service.getSourceFile("B1");
        assertThat(src.content()).containsExactly(1);
    }

    @Test
    @DisplayName("getSourceFile：source_object_key 为空（旧数据）→ IMPORT_BATCH_NO_SOURCE_FILE")
    void getSourceFile_nullObjectKey_throwsNoSourceFile() {
        when(batchMapper.selectByBatchId("B1")).thenReturn(batchWith("B1", "admin", null));

        assertThatThrownBy(() -> service.getSourceFile("B1"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_NO_SOURCE_FILE));
    }

    @Test
    @DisplayName("getBatchDto：含 sourceObjectKey")
    void getBatchDto_exposesSourceObjectKey() {
        when(batchMapper.selectByBatchId("B1")).thenReturn(batchWith("B1", "admin", "OBJ1"));
        PerfImportBatchRespDTO dto = service.getBatchDto("B1");
        assertThat(dto.getSourceObjectKey()).isEqualTo("OBJ1");
    }

    // ---------- helpers ----------

    private PerfImportBatch batchWith(String id, String createdBy, String objectKey) {
        PerfImportBatch b = new PerfImportBatch();
        b.setId(id);
        b.setBatchNo("IMP" + id);
        b.setImportType("TARGET");
        b.setFileName("imp.xlsx");
        b.setSourceObjectKey(objectKey);
        b.setStatus("SUCCESS");
        b.setCreatedBy(createdBy);
        return b;
    }

    private void stubSelectPageReturnsOne() {
        when(batchMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<PerfImportBatch> p = inv.getArgument(0);
            p.setRecords(List.of(batchWith("B1", "admin", "OBJ1")));
            p.setTotal(1L);
            return p;
        });
    }
}
