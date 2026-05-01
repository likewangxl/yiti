package com.bank.branch.platform.performance.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 指标计算完成事件（V1.7）.
 *
 * <p>由 MetricCalcService.calcMetric 在终态写入 perf_run_task 后发布；
 * 由 KpiCascadeListener 监听并触发依赖该指标的 KPI 方案重算.
 *
 * @param metricCode     指标编码
 * @param baseDim        基础维度（EMP / ORG / CUST）
 * @param dataDate       数据日期
 * @param version        数据版本
 * @param runStatus      终态状态（SUCCESS / PARTIAL_FAILED / FAILED）
 * @param subjectTotal   主体总数
 * @param subjectSuccess 成功主体数
 * @param subjectFailed  失败主体数
 * @param runTaskId      关联的 perf_run_task.id
 * @param triggerType    触发类型（SCHEDULED / MANUAL / RECALC）
 * @param occurredAt     事件发生时间
 */
public record MetricCalcCompletedEvent(
    String metricCode,
    String baseDim,
    LocalDate dataDate,
    String version,
    String runStatus,
    int subjectTotal,
    int subjectSuccess,
    int subjectFailed,
    String runTaskId,
    String triggerType,
    LocalDateTime occurredAt
) {}
