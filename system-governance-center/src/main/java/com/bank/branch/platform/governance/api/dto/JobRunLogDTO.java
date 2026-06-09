package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 任务执行日志传输对象
 */
@Data
public class JobRunLogDTO {

    /** 日志ID */
    private String id;

    /** 任务ID */
    private String jobId;

    /** 触发类型 SCHEDULED/MANUAL */
    private String triggerType;

    /** 触发原因（手动触发时） */
    private String reason;

    /** 开始时间（ISO 8601） */
    private String startTime;

    /** 结束时间（ISO 8601） */
    private String endTime;

    /** 运行状态 RUNNING/SUCCESS/FAILED */
    private String status;

    /** 错误信息（失败时） */
    private String errorMsg;

    /** 触发人工号 */
    private String createdBy;

    /** 触发人姓名（按工号 createdBy 解析；手动触发展示用，解析失败为 null） */
    private String operatorName;
}
