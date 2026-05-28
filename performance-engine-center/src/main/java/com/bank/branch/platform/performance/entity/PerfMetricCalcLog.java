package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标计算执行日志
 */
@Data
@TableName("PERF_METRIC_CALC_LOG")
public class PerfMetricCalcLog {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String taskId;
    private String metricCode;
    private String metricName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer rowCount;
    private String errorMsg;
}
