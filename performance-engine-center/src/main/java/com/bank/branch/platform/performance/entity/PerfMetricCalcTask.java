package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 指标计算任务流水
 */
@Data
@TableName("PERF_METRIC_CALC_TASK")
public class PerfMetricCalcTask {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String taskName;
    private Integer metricLevel;
    private LocalDate dataDate;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private Integer skipCount;
    private String errorMsg;
    private LocalDateTime createdTime;
}
