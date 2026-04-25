package com.bank.branch.platform.governance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务手动触发响应DTO.
 *
 * <p>P3.2 起：含 {@link #jobId}/{@link #triggerType}（=MANUAL）字段，与
 * Quartz JobDataMap 的 triggerType 透传保持语义一致。
 *
 * <p>{@link #runLogId}/{@link #jobKey} 为 P1 旧字段，保留作向后兼容；
 * P3.2 走 scheduler.triggerJob 路径时不再由 service 直接生成 runLogId
 * （由 JobExecutionLogger.jobToBeExecuted 在异步执行时写入），故新触发响应中
 * runLogId 可能为 null。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTriggerRespDTO {

    /**
     * 任务 ID（sys_job_conf.id）
     */
    private String jobId;

    /**
     * 触发类型（P3.2 起恒为 "MANUAL"）
     */
    private String triggerType;

    /**
     * 本次执行的日志ID（P3.2 走 Quartz 路径时由 JobListener 异步写入，本响应字段可能为 null）
     */
    private String runLogId;

    /**
     * 任务 KEY（sys_job_conf.job_key，旧字段保留兼容）
     */
    private String jobKey;

    /**
     * 触发时间（ISO_LOCAL_DATE_TIME 格式字符串）
     */
    private String triggerTime;
}