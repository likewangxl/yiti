package com.bank.branch.platform.report.service.export.impl;

import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4 个 ExportStrategy 装配 + 最小 execute 行为守护（Task M5.2.3）.
 *
 * <p>V1.0 落地范围：
 * <ul>
 *   <li>{@link DynamicQueryExportStrategy#exportType()} → "DYNAMIC_QUERY"</li>
 *   <li>{@link TouchSummaryExportStrategy#exportType()} → "TOUCH_SUMMARY"</li>
 *   <li>{@link PerfSummaryExportStrategy#exportType()} → "PERF_SUMMARY"</li>
 *   <li>{@link CustPoolSummaryExportStrategy#exportType()} → "CUSTPOOL_SUMMARY"</li>
 * </ul>
 *
 * <p>每个 strategy 的 execute 必须：
 * <ol>
 *   <li>执行后回写 {@code task.fileKey}（非空）+ {@code task.fileSize}（非负）</li>
 *   <li>返回非负 rowCount</li>
 *   <li>不抛运行时异常</li>
 * </ol>
 *
 * <p>实际 EasyExcel + MinIO 上传的端到端行为留给 M6+ 接入真业务依赖时验证。
 * V1.0 strategy 实现采用占位骨架（写最小 1 行示例 Excel + 假 fileKey），
 * 关键是保持装配链路 + 状态机驱动可工作.
 */
class ExportStrategiesTest {

    @Test
    void allFourStrategies_shouldExposeUniqueExportType() {
        List<ExportStrategy> strategies = List.of(
            new DynamicQueryExportStrategy(),
            new TouchSummaryExportStrategy(),
            new PerfSummaryExportStrategy(),
            new CustPoolSummaryExportStrategy()
        );

        List<String> types = strategies.stream().map(ExportStrategy::exportType).toList();
        assertThat(types).containsExactly(
            "DYNAMIC_QUERY", "TOUCH_SUMMARY", "PERF_SUMMARY", "CUSTPOOL_SUMMARY");
        // 互不重复
        assertThat(types).doesNotHaveDuplicates();
    }

    @Test
    void dynamicQueryExport_execute_shouldWriteFileKeyAndReturnRowCount() {
        ExportStrategy s = new DynamicQueryExportStrategy();
        RptExportTask t = newTask("DYNAMIC_QUERY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isNotBlank().contains(t.getId());
        assertThat(t.getFileSize()).isNotNull().isGreaterThan(0L);
    }

    @Test
    void touchSummaryExport_execute_shouldWriteFileKeyAndReturnRowCount() {
        ExportStrategy s = new TouchSummaryExportStrategy();
        RptExportTask t = newTask("TOUCH_SUMMARY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isNotBlank();
        assertThat(t.getFileSize()).isNotNull().isGreaterThan(0L);
    }

    @Test
    void perfSummaryExport_execute_shouldWriteFileKeyAndReturnRowCount() {
        ExportStrategy s = new PerfSummaryExportStrategy();
        RptExportTask t = newTask("PERF_SUMMARY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isNotBlank();
        assertThat(t.getFileSize()).isNotNull().isGreaterThan(0L);
    }

    @Test
    void custPoolSummaryExport_execute_shouldWriteFileKeyAndReturnRowCount() {
        ExportStrategy s = new CustPoolSummaryExportStrategy();
        RptExportTask t = newTask("CUSTPOOL_SUMMARY");
        int rows = s.execute(t);
        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(t.getFileKey()).isNotBlank();
        assertThat(t.getFileSize()).isNotNull().isGreaterThan(0L);
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
}
