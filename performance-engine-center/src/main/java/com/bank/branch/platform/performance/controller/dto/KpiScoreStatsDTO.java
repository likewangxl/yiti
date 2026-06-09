package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考核计算页面统计 DTO（来源：PERF_METRIC_CALC_TASK 的 KPI 计算任务）.
 *
 * <p>"最后一次 KPI 计算任务" 的成功数/失败数/耗时 + "本月 KPI 计算任务数"。
 */
@Data
public class KpiScoreStatsDTO {

    /** 本月 KPI 计算任务数（task_type=KPI_SCORE_CALC，start_time 在本月）. */
    private long monthTaskCount;

    /** 最后一次 KPI 计算任务成功数. */
    private Integer lastSuccessCount;

    /** 最后一次 KPI 计算任务失败数. */
    private Integer lastFailCount;

    /** 最后一次 KPI 计算任务耗时（毫秒，end_time-start_time；未结束为 null）. */
    private Long lastDurationMs;

    /** 最后一次 KPI 计算任务状态（RUNNING/SUCCESS/FAILED）. */
    private String lastStatus;

    /** 最后一次 KPI 计算任务数据日期. */
    private LocalDate lastDataDate;

    /** 最后一次 KPI 计算任务结束时间. */
    private LocalDateTime lastEndTime;
}
