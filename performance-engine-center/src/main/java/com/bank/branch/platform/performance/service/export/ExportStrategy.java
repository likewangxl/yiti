package com.bank.branch.platform.performance.service.export;

import com.bank.branch.platform.performance.entity.PerfExportTask;

/**
 * 绩效导出策略（V1.2 Task Q6.1）.
 *
 * <p>每种 {@code exportType} 对应一个策略 Bean，{@link PerfExportService} 根据 exportType
 * 分发到具体策略执行。
 *
 * <p>策略实现要求：
 * <ul>
 *   <li>保持无状态（允许被并发调用）</li>
 *   <li>{@link #execute} 内部仅负责"解析参数 + 查询数据 + 生成文件 + 上传 MinIO"，
 *       <strong>不</strong>负责任务状态机流转（由 {@link PerfExportService} 统一管理）</li>
 *   <li>返回成功写入的行数；失败时抛 {@link com.bank.branch.platform.performance.exception.PerfException}
 *       （推荐错误码：{@code EXPORT_FILE_GENERATE_FAILED / EXPORT_ROWS_EXCEEDS_LIMIT}）</li>
 *   <li>在执行过程中需要回填 {@code task.fileKey} / {@code task.fileSize} 时，
 *       通过 Mapper 直接回写，无需在策略签名中返回</li>
 * </ul>
 *
 * <p>策略 Bean 通过 {@code @Component} 注册，{@link PerfExportService} 构造时按
 * {@link #exportType} 路由装配。
 */
public interface ExportStrategy {

    /**
     * 本策略处理的 exportType，与 {@code PerfExportTask.exportType} 一致.
     *
     * @return 类型标识，如 {@code "KPI" / "METRIC" / "ALLOC" / "DETAIL"}
     */
    String exportType();

    /**
     * 执行导出.
     *
     * @param task 已落库的任务（status=RUNNING），id/paramsJson/operatorId 可用
     * @return 成功写入的行数
     */
    int execute(PerfExportTask task);
}
