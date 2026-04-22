package com.bank.branch.platform.performance.service.importer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入执行结果.
 *
 * <p>每个 {@link ImportStrategy#execute} 返回该对象，由 {@link PerfImportService}
 * 聚合后写入 {@link com.bank.branch.platform.performance.entity.PerfImportBatch} 的
 * {@code totalRows / successRows / errorRows / remark}。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code totalRows} —— Excel/数据文件解析出的总行数</li>
 *   <li>{@code successRows} —— 业务校验通过并成功落库的行数</li>
 *   <li>{@code errorRows} —— 校验失败/落库失败的行数（不代表整体批次失败）</li>
 *   <li>{@code errorSummary} —— 每行错误摘要，行间用分号分隔；超过 4000 字符由调用方截断</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {

    /** 总行数. */
    private int totalRows;

    /** 成功行数. */
    private int successRows;

    /** 失败行数. */
    private int errorRows;

    /** 错误摘要（每行 "第 N 行: xxx"，用分号分隔，可为 null）. */
    private String errorSummary;
}
