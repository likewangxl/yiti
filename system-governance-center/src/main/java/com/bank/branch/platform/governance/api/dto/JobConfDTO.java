package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 任务配置传输对象
 */
@Data
public class JobConfDTO {

    /** 任务ID */
    private String id;

    /** 任务唯一标识 */
    private String jobKey;

    /** 任务名称 */
    private String jobName;

    /** Cron 表达式 */
    private String cronExpr;

    /** 状态 ACTIVE/PAUSED */
    private String status;

    /** 是否允许手动触发 */
    private Boolean allowManualTrigger;

    /** 上次执行时间（ISO 8601） */
    private String lastRunTime;

    /** 下次执行时间（ISO 8601） */
    private String nextRunTime;

    /** 备注 */
    private String remark;
}
