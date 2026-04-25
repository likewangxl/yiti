package com.bank.branch.platform.report.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import com.bank.branch.platform.report.service.export.model.CustPoolExportRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 客户池汇总导出策略（Task M5.2.3，V1.0 占位实现）.
 *
 * <p>V1.0 行为：生成最小 1 行示例 Excel + 写本地虚 fileKey，让 PENDING → RUNNING →
 * SUCCESS 状态机闭环可工作。M6+ 接入 CustPoolSummaryService.summarize 后切真数据.
 */
@Slf4j
@Component
public class CustPoolSummaryExportStrategy implements ExportStrategy {

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

        String fileKey = "rpt/export/" + task.getId() + "/custpool_summary_"
            + System.currentTimeMillis() + ".xlsx";
        task.setFileKey(fileKey);
        task.setFileSize((long) bytes.length);
        log.info("[CustPoolExport] 占位导出完成 taskId={} rows={} fileKey={}",
            task.getId(), rows.size(), fileKey);
        return rows.size();
    }
}
