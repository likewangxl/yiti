package com.bank.branch.platform.report.service.export;

import com.bank.branch.platform.report.entity.RptExportTask;

/**
 * 报表异步导出策略 SPI（Task M5.2.2）.
 *
 * <p>4 个内置实现（M5.2.3）：
 * <ul>
 *   <li>{@code DYNAMIC_QUERY}     —— 动态查询导出（A.3）</li>
 *   <li>{@code TOUCH_SUMMARY}     —— 触达汇总导出（C.2）</li>
 *   <li>{@code PERF_SUMMARY}      —— 绩效汇总导出（C.3）</li>
 *   <li>{@code CUSTPOOL_SUMMARY}  —— 客户池汇总导出（C.4）</li>
 * </ul>
 *
 * <p>实现类需保证：
 * <ol>
 *   <li>{@link #exportType()} 返回常量值，与 RptExportService 路由 key 完全一致</li>
 *   <li>{@link #execute(RptExportTask)} 内部回写 {@code task.fileKey} + {@code task.fileSize}，
 *       Service 层负责 status / row_count / expire_at 的最终落库</li>
 * </ol>
 *
 * <p>对照 performance-engine-center 的 {@code ExportStrategy}，保持跨模块一致风格.
 */
public interface ExportStrategy {

    /** 导出类型常量，作为 Service 路由 key. */
    String exportType();

    /**
     * 执行导出，返回写入文件的实际行数.
     *
     * @param task 任务（contains operatorId/paramsJson/exportType；执行后回写 fileKey/fileSize）
     * @return 实际行数（用于落库 row_count）
     */
    int execute(RptExportTask task);
}
