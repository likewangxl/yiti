package com.bank.branch.platform.performance.service.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * 指标 SQL 执行器（V1.1 Task P2.1）.
 *
 * <p>职责：以只读方式执行指标 SQL，将结果集规约为 {@code baseKey -> metricValue}
 * 的 {@link Map}，供 {@code MetricCalcService} 写入宽表。
 *
 * <p><strong>SQL 语法约束（硬约束，调用方必须遵守）</strong>：
 * <ul>
 *   <li>只允许 {@code SELECT} / {@code WITH}（CTE）查询；任何 DML/DDL 在
 *       {@code SqlValidator} 层拦截并抛 {@code METRIC_CALC_LOGIC_INVALID(PERF-42201)}。</li>
 *   <li>结果集必须恰好含两列：
 *       <ul>
 *         <li>{@code base_key}（{@code VARCHAR}）——维度键：
 *             EMP 指标 → emp_id；ORG 指标 → org_code；CUST 指标 → cust_id。</li>
 *         <li>{@code metric_value}（{@code DECIMAL}）——指标数值。</li>
 *       </ul>
 *       列名不符合约定将抛 {@code METRIC_CALC_LOGIC_INVALID}。</li>
 *   <li>参数名使用 Spring {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}
 *       风格（{@code :name}）。</li>
 * </ul>
 *
 * <p><strong>超时处理</strong>：通过底层 {@code Statement.setQueryTimeout(int)}
 * 传递 {@code timeout.getSeconds()}，超时后抛
 * {@code METRIC_CALC_LOGIC_INVALID(PERF-42201)}。
 */
public interface SqlExecutor {

    /**
     * 执行指标 SQL 并返回 {@code (baseKey -> metricValue)} 映射.
     *
     * @param sql     指标 SQL（必须符合上述约束）
     * @param params  命名参数（{@code :name} 形式），可为空 Map
     * @param timeout 查询超时；底层转为 {@code (int) timeout.getSeconds()}
     * @return 有序 Map（保持数据库返回顺序），未命中维度键不会出现在 Map 中
     * @throws com.bank.branch.platform.performance.exception.PerfException
     *         当 SQL 执行失败 / 超时 / 结果列名不符合 {@code base_key}+{@code metric_value} 约定时
     */
    Map<String, BigDecimal> execute(String sql, Map<String, Object> params, Duration timeout);
}
