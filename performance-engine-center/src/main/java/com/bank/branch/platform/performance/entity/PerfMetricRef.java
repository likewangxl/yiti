package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标引用关系表 perf_metric_ref 贫血实体.
 *
 * <p>唯一键：uk_metric_ref(metric_code, ref_metric_code)
 * <p>索引：idx_ref_metric(ref_metric_code)
 */
@Data
public class PerfMetricRef {

    /** 引用关系ID. */
    private String id;

    /** 上层指标编码. */
    private String metricCode;

    /** 被引用下层指标编码. */
    private String refMetricCode;

    /** 创建时间. */
    private LocalDateTime createdTime;
}
