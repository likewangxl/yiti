package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 指标重算任务按指标分组汇总行（任务监控列表）. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSummaryDTO {
    /** 指标编码（= perf_run_task.task_key）. */
    private String metricCode;
    /** 指标中文名（LEFT JOIN perf_metric_def；查不到为 null）. */
    private String metricName;
    /** 该指标首次进入回算时间（= MIN(created_time)）. */
    private LocalDateTime firstCreatedTime;
    /** 该指标累计执行次数（= COUNT(*)）. */
    private long runCount;
}
