package com.bank.branch.platform.performance.service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 指标试运行结果 DTO（V1.1 Task P3.1）.
 *
 * <p>承载 {@link com.bank.branch.platform.performance.service.MetricTrialService#trial}
 * 的返回结果，由 Controller 层再次包装为 {@code MetricTrialRespDTO} 返回给前端。
 *
 * <p><strong>职责边界</strong>：本类位于 service 层，只承载业务结果，<em>不</em>承载
 * HTTP 响应字段（如 taskId）。
 *
 * <p>字段语义：
 * <ul>
 *   <li>SQL 类指标：{@link #samples} 为 {@code baseKey+metricValue} 的样本行，
 *       {@link #totalRows} 为 SQL 返回的总行数，{@link #exprResult} 为 null</li>
 *   <li>EXPR 类指标：{@link #exprResult} 为 Groovy 求值结果，{@link #samples} 为空</li>
 * </ul>
 */
@Data
public class MetricTrialResult {

    /** 实际返回样本条数. */
    private int sampleSize;

    /** SQL 执行返回的总行数（EXPR 场景为 1）. */
    private int totalRows;

    /** 样本行（每行 {@code baseKey + metricValue}），SQL 场景填充. */
    private List<Map<String, Object>> samples;

    /** EXPR 场景单值结果；SQL 场景为 null. */
    private BigDecimal exprResult;

    /** 执行耗时（毫秒）. */
    private long executionMillis;
}
