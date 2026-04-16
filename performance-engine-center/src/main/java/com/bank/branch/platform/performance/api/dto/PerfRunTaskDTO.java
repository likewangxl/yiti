package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运行任务日志 DTO.
 * <p>v1.2: id 为 String (与 DDL 一致).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfRunTaskDTO {
    /** 任务 ID (varchar 32). */
    private String id;
    /** 任务类型: METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC/DATA_IMPORT. */
    private String taskType;
    /** 关键键 (如 metric_code). */
    private String taskKey;
    /** 数据日期. */
    private LocalDate dataDate;
    /** 数据版本. */
    private String dataVersion;
    /** 状态: PENDING/RUNNING/SUCCESS/FAILED/PARTIAL/CANCELLED. */
    private String status;
    /** 发起人 emp_id. */
    private String startedBy;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 错误信息 (FAILED 时填充). */
    private String errorMsg;
    /** 结果预览 JSON. */
    private String resultPreviewJson;
    private LocalDateTime createdTime;
}
