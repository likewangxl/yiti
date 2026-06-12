package com.bank.branch.platform.report.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import com.bank.branch.platform.report.service.export.model.DynamicQueryExportRow;
import com.bank.branch.platform.report.support.ByteArrayMultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 动态查询导出策略（Task M5.2.3 占位 → M6.0 切真 governance.FileApi.upload）.
 *
 * <p>V1.0 行为：生成最小 1 行示例 Excel，调 {@link FileApi#upload} 上传到 MinIO，
 * 把返回的 {@link FileObjectDTO#getId() fileId} 写入 task.fileKey
 * （消费方 RptExportFacade.getDownloadUrl 用 fileApi.getDownloadUrl(fileKey) 拉预签名 URL，
 *  fileKey 就是 file_object.id）.
 *
 * <p>V1.1+ 接入真业务时按 03 §A.2 调用 DynamicQueryService.query 拉取多指标宽表数据替换占位行.
 *
 * <p>历史：M5.2.3 V1.0 仅本地拼路径不调 upload，导致 download 端点 302 跳后 MinIO 找不到 key。
 * M6.0.1 reviewer 标记必交付项，强制走真实上传链路.
 */
@Slf4j
@Component
public class DynamicQueryExportStrategy implements ExportStrategy {

    private static final String CONTENT_TYPE_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final FileApi fileApi;

    public DynamicQueryExportStrategy(FileApi fileApi) {
        this.fileApi = fileApi;
    }

    @Override
    public String exportType() {
        return "DYNAMIC_QUERY";
    }

    @Override
    public int execute(RptExportTask task) {
        // V1.0 占位：写最小示例行。M6+ 替换为 DynamicQueryService 真实数据.
        List<DynamicQueryExportRow> rows = List.of(
            DynamicQueryExportRow.builder()
                .subjectId("PLACEHOLDER")
                .subjectName("V1.0 占位行")
                .dataDate("")
                .metricCode("")
                .metricValue("")
                .build()
        );

        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, DynamicQueryExportRow.class)
                .sheet("动态查询")
                .doWrite(rows);
            bytes = out.toByteArray();
        } catch (Exception ex) {
            log.warn("[DynamicQueryExport] EasyExcel 生成失败 taskId={}: {}",
                task.getId(), ex.getMessage());
            throw new RuntimeException("DynamicQuery 导出失败：" + ex.getMessage(), ex);
        }

        // M6.0：走 governance.FileApi.upload 真实上传到 MinIO
        String fileName = "dynamic_query_" + task.getId() + ".xlsx";
        MultipartFile mf = new ByteArrayMultipartFile(bytes, "file", fileName, CONTENT_TYPE_XLSX);
        FileObjectDTO uploaded = fileApi.upload(mf, task.getOperatorId(), FileCategory.EXPORT_DYNAMIC);
        task.setFileKey(uploaded.getId());
        task.setFileSize(uploaded.getFileSize() != null ? uploaded.getFileSize() : (long) bytes.length);
        log.info("[DynamicQueryExport] 上传完成 taskId={} rows={} fileId={} size={}",
            task.getId(), rows.size(), uploaded.getId(), task.getFileSize());
        return rows.size();
    }
}
