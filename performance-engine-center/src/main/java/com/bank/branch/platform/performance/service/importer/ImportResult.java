package com.bank.branch.platform.performance.service.importer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入执行结果.
 *
 * <p>每个 {@link ImportStrategy#execute} 返回该对象，由 {@link PerfImportService}
 * 聚合后写入 {@link com.bank.branch.platform.performance.entity.PerfImportBatch} 的
 * {@code totalRows / successRows / errorRows / updatedRows / remark}。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code totalRows} —— Excel/数据文件解析出的总行数</li>
 *   <li>{@code successRows} —— 业务校验通过并成功落库的行数（= insertedRows + updatedRows）</li>
 *   <li>{@code errorRows} —— 校验失败/落库失败的行数（不代表整体批次失败）</li>
 *   <li>{@code updatedRows} —— V1.11：upsert 路径命中既有记录的更新行数（仅 METRIC_DEF 使用，其他策略恒 0）</li>
 *   <li>{@code errorSummary} —— 每行错误摘要，行间用分号分隔；超过 4000 字符由调用方截断</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {

    /** 总行数. */
    private int totalRows;

    /** 成功行数（= insertedRows + updatedRows）. */
    private int successRows;

    /** 失败行数. */
    private int errorRows;

    /** V1.11：更新行数（仅 METRIC_DEF upsert 路径使用，其他策略恒 0）. */
    private int updatedRows;

    /** 错误摘要（每行 "第 N 行: xxx"，用分号分隔，可为 null）. */
    private String errorSummary;

    /**
     * 兼容旧构造器（4 参数版）：updatedRows 默认 0.
     *
     * <p>V1.11 引入 updatedRows 字段后，TARGET / BASE_DATA / ALLOC 三个策略
     * 调用此构造器保持原行为不变.
     */
    public ImportResult(int totalRows, int successRows, int errorRows, String errorSummary) {
        this(totalRows, successRows, errorRows, 0, errorSummary);
    }
}
