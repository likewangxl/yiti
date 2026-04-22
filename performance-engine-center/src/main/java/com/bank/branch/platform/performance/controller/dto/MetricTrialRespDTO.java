package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 指标试运行响应 DTO（03 §A.5）.
 *
 * <p>包装 {@link com.bank.branch.platform.performance.service.dto.MetricTrialResult} 暴露给前端的字段：
 * <ul>
 *   <li>{@code metricCode} —— 回显的指标编码</li>
 *   <li>{@code sampleSize} —— 实际返回样本条数</li>
 *   <li>{@code totalRows} —— SQL 返回的总行数（EXPR 为 1）</li>
 *   <li>{@code samples} —— 样本行（SQL）</li>
 *   <li>{@code exprResult} —— EXPR 单值结果</li>
 *   <li>{@code executionMillis} —— 执行耗时（毫秒）</li>
 * </ul>
 */
@Data
public class MetricTrialRespDTO {

    /** 回显的指标编码. */
    private String metricCode;

    /** 实际返回样本条数. */
    private int sampleSize;

    /** 总行数（EXPR 为 1）. */
    private int totalRows;

    /** 样本行（SQL 场景填充，EXPR 为空列表）. */
    private List<Map<String, Object>> samples;

    /** EXPR 单值结果（SQL 场景为 null）. */
    private BigDecimal exprResult;

    /** 执行耗时（毫秒）. */
    private long executionMillis;
}
