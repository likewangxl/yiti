package com.bank.branch.platform.performance.service.dto;

import java.time.LocalDate;

/**
 * run_task 列表查询过滤条件.
 *
 * <p>全部字段 nullable，null/空串表示不按该字段过滤。
 *
 * @param taskType     任务类型（METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC/DATA_IMPORT）
 * @param triggerType  触发来源（RECALC/SCHEDULED/MANUAL）
 * @param taskKey      关键键（如 metric_code）
 * @param status       状态（PENDING/RUNNING/SUCCESS/FAILED/PARTIAL/CANCELLED）
 * @param startedBy    发起人 emp_id
 * @param dataDate     数据日期（精确匹配）
 * @param dataDateFrom 数据日期起（含），与 dataDate 可组合但通常二选一
 * @param dataDateTo   数据日期止（含）
 */
public record RunTaskQuery(String taskType, String triggerType, String taskKey, String status,
                           String startedBy, LocalDate dataDate,
                           LocalDate dataDateFrom, LocalDate dataDateTo) {
}
