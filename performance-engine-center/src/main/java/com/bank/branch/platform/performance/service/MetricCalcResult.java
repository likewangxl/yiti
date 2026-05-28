package com.bank.branch.platform.performance.service;

/**
 * 指标计算结果（V1.13+ 引入，供批量入口拿到真实主体处理数）.
 *
 * <p>{@code success} 等于实际写入对应宽表（EMP/ORG/CUST_INDEX_RESULT）的主体数，
 * 可作为 PERF_METRIC_CALC_LOG.row_count 真实值。
 *
 * @param runTaskId 单指标的 PERF_RUN_TASK 主键
 * @param total     主体总数（SQL 类按结果集大小；GROOVY 按 subject_sql 取出的主体数）
 * @param success   成功主体数（= 实际宽表行数）
 * @param failed    失败主体数（GROOVY 类 per-subject 容错累计；SQL 类恒为 0）
 */
public record MetricCalcResult(String runTaskId, int total, int success, int failed) {
}
