package com.bank.branch.platform.report.service.export.impl;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4 个 ExportStrategy 装配 + 最小 execute 行为守护（Task M5.2.3 / M6.0.1 切真 upload）.
 *
 * <p>V1.0 占位 → M6.0 真实 upload 升级范围：
 * <ul>
 *   <li>{@link DynamicQueryExportStrategy#exportType()} → "DYNAMIC_QUERY"</li>
 *   <li>{@link TouchSummaryExportStrategy#exportType()} → "TOUCH_SUMMARY"</li>
 *   <li>{@link PerfSummaryExportStrategy#exportType()} → "PERF_SUMMARY"</li>
 *   <li>{@link CustPoolSummaryExportStrategy#exportType()} → "CUSTPOOL_SUMMARY"</li>
 * </ul>
 *
 * <p>每个 strategy 的 execute 必须：
 * <ol>
 *   <li>调用 {@link FileApi#upload(MultipartFile, String)} 上传 EasyExcel 字节流</li>
 *   <li>把返回 {@link FileObjectDTO#getId()} 写入 {@code task.fileKey}（消费方与
 *       {@link FileApi#getDownloadUrl(String)} 对齐）</li>
 *   <li>把 {@link FileObjectDTO#getFileSize()} 写入 {@code task.fileSize}</li>
 *   <li>返回非负 rowCount</li>
 *   <li>不抛运行时异常</li>
 * </ol>
 *
 * <p>M6.0 之前 V1.0 占位行为：本地拼 fileKey 字符串（如 "rpt/export/{taskId}/dynamic_query_*.xlsx"），
 * 不调 upload，导致 download 端点 302 跳预签名 URL 时 MinIO 找不到 key。
 * M6.0 切真后 4 个 strategy 都委托 governance.FileApi.upload，端到端可 work。
 */
class ExportStrategiesTest {

    @Test
    void allFourStrategies_shouldExposeUniqueExportType() {
        FileApi fileApi = Mockito.mock(FileApi.class);
        List<ExportStrategy> strategies = List.of(
            new DynamicQueryExportStrategy(fileApi),
            new TouchSummaryExportStrategy(fileApi),
            new PerfSummaryExportStrategy(fileApi),
            new CustPoolSummaryExportStrategy(fileApi)
        );

        List<String> types = strategies.stream().map(ExportStrategy::exportType).toList();
        assertThat(types).containsExactly(
            "DYNAMIC_QUERY", "TOUCH_SUMMARY", "PERF_SUMMARY", "CUSTPOOL_SUMMARY");
        // 互不重复
        assertThat(types).doesNotHaveDuplicates();
    }

    @Test
    void dynamicQueryExport_execute_shouldCallFileApiUploadAndWriteFileId() {
        FileApi fileApi = Mockito.mock(FileApi.class);
        when(fileApi.upload(any(), anyString(), anyString())).thenReturn(stubFileObject("FID-DQ-001", 1024L));

        ExportStrategy s = new DynamicQueryExportStrategy(fileApi);
        RptExportTask t = newTask("DYNAMIC_QUERY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);

        // 关键：fileKey 必须存 fileApi 返回的 fileId（消费方 fileApi.getDownloadUrl(fileId) 对齐）
        assertThat(t.getFileKey()).isEqualTo("FID-DQ-001");
        assertThat(t.getFileSize()).isEqualTo(1024L);

        ArgumentCaptor<MultipartFile> fileCap = ArgumentCaptor.forClass(MultipartFile.class);
        verify(fileApi, times(1)).upload(fileCap.capture(), anyString(), anyString());
        // 文件名包含 taskId 便于运维定位
        assertThat(fileCap.getValue().getOriginalFilename()).contains(t.getId());
        assertThat(fileCap.getValue().getSize()).isPositive();
    }

    @Test
    void touchSummaryExport_execute_shouldCallFileApiUploadAndWriteFileId() {
        FileApi fileApi = Mockito.mock(FileApi.class);
        when(fileApi.upload(any(), anyString(), anyString())).thenReturn(stubFileObject("FID-TS-001", 512L));

        ExportStrategy s = new TouchSummaryExportStrategy(fileApi);
        RptExportTask t = newTask("TOUCH_SUMMARY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isEqualTo("FID-TS-001");
        assertThat(t.getFileSize()).isEqualTo(512L);

        verify(fileApi, times(1)).upload(any(), anyString(), anyString());
    }

    @Test
    void perfSummaryExport_execute_shouldCallFileApiUploadAndWriteFileId() {
        FileApi fileApi = Mockito.mock(FileApi.class);
        when(fileApi.upload(any(), anyString(), anyString())).thenReturn(stubFileObject("FID-PS-001", 768L));

        ExportStrategy s = new PerfSummaryExportStrategy(fileApi);
        RptExportTask t = newTask("PERF_SUMMARY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isEqualTo("FID-PS-001");
        assertThat(t.getFileSize()).isEqualTo(768L);

        verify(fileApi, times(1)).upload(any(), anyString(), anyString());
    }

    @Test
    void custPoolSummaryExport_execute_shouldCallFileApiUploadAndWriteFileId() {
        FileApi fileApi = Mockito.mock(FileApi.class);
        when(fileApi.upload(any(), anyString(), anyString())).thenReturn(stubFileObject("FID-CP-001", 256L));

        ExportStrategy s = new CustPoolSummaryExportStrategy(fileApi);
        RptExportTask t = newTask("CUSTPOOL_SUMMARY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isEqualTo("FID-CP-001");
        assertThat(t.getFileSize()).isEqualTo(256L);

        verify(fileApi, times(1)).upload(any(), anyString(), anyString());
    }

    private RptExportTask newTask(String type) {
        RptExportTask t = new RptExportTask();
        t.setId(UUID.randomUUID().toString().replace("-", ""));
        t.setExportType(type);
        t.setOperatorId("E001");
        t.setStatus("RUNNING");
        t.setParamsJson("{}");
        return t;
    }

    private FileObjectDTO stubFileObject(String fileId, Long size) {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId(fileId);
        dto.setFileSize(size);
        dto.setFileName("rpt-export.xlsx");
        return dto;
    }
}
