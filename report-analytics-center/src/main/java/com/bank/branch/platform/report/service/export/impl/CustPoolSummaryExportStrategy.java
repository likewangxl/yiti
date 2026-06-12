package com.bank.branch.platform.report.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import com.bank.branch.platform.report.service.export.model.CustPoolExportRow;
import com.bank.branch.platform.report.support.ByteArrayMultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 客户池汇总导出策略（Task M5.2.3 占位 → M6.0 切真 governance.FileApi.upload）.
 *
 * <p>V1.0 行为：生成最小 1 行示例 Excel，调 {@link FileApi#upload} 上传，
 * fileKey 存 fileId 供 RptExportFacade.getDownloadUrl 消费.
 * V1.1+ 接入 CustPoolSummaryService.summarize 切真数据.
 */
@Slf4j
@Component
public class CustPoolSummaryExportStrategy implements ExportStrategy {

    private static final String CONTENT_TYPE_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final FileApi fileApi;

    public CustPoolSummaryExportStrategy(FileApi fileApi) {
        this.fileApi = fileApi;
    }

    @Override
    public String exportType() {
        return "CUSTPOOL_SUMMARY";
    }

    @Override
    public int execute(RptExportTask task) {
        List<CustPoolExportRow> rows = List.of(
            CustPoolExportRow.builder()
                .orgId("PLACEHOLDER")
                .orgName("V1.0 占位行")
                .publicPoolCount(0)
                .privatePoolCount(0)
                .flowRate("0.00%")
                .build()
        );

        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, CustPoolExportRow.class)
                .sheet("客户池汇总")
                .doWrite(rows);
            bytes = out.toByteArray();
        } catch (Exception ex) {
            log.warn("[CustPoolExport] EasyExcel 生成失败 taskId={}: {}",
                task.getId(), ex.getMessage());
            throw new RuntimeException("CustPoolSummary 导出失败：" + ex.getMessage(), ex);
        }

        String fileName = "custpool_summary_" + task.getId() + ".xlsx";
        MultipartFile mf = new ByteArrayMultipartFile(bytes, "file", fileName, CONTENT_TYPE_XLSX);
        FileObjectDTO uploaded = fileApi.upload(mf, task.getOperatorId(), FileCategory.EXPORT_CUSTPOOL);
        task.setFileKey(uploaded.getId());
        task.setFileSize(uploaded.getFileSize() != null ? uploaded.getFileSize() : (long) bytes.length);
        log.info("[CustPoolExport] 上传完成 taskId={} rows={} fileId={} size={}",
            task.getId(), rows.size(), uploaded.getId(), task.getFileSize());
        return rows.size();
    }
}
