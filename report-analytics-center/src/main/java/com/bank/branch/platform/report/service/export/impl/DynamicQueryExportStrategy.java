package com.bank.branch.platform.report.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import com.bank.branch.platform.report.service.export.model.DynamicQueryExportRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 动态查询导出策略（Task M5.2.3，V1.0 占位实现）.
 *
 * <p>V1.0 行为：生成最小 1 行示例 Excel + 写本地虚 fileKey，回写 task 字段，
 * 让 PENDING → RUNNING → SUCCESS 状态机闭环可工作。
 *
 * <p>M6+ 接入真业务时按 03 §A.2 调用 DynamicQueryService.query 拉取多指标宽表数据，
 * 上传 MinIO 并替换 fileKey 为真实 object key。
 */
@Slf4j
@Component
public class DynamicQueryExportStrategy implements ExportStrategy {

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

        // V1.0 fileKey 直接拼路径（M6+ 接入 governance.FileApi.upload 后替换）
        String fileKey = "rpt/export/" + task.getId() + "/dynamic_query_"
            + System.currentTimeMillis() + ".xlsx";
        task.setFileKey(fileKey);
        task.setFileSize((long) bytes.length);
        log.info("[DynamicQueryExport] 占位导出完成 taskId={} rows={} fileKey={}",
            task.getId(), rows.size(), fileKey);
        return rows.size();
    }
}
