package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行日志实体，对应 sys_job_run_log 表。
 * <p>
 * 记录每次任务执行的状态变迁：RUNNING → SUCCESS / FAILED。
 * 注意：本表没有 updated_by / updated_time 字段，状态由系统自动更新。
 * </p>
 */
@Data
@TableName("sys_job_run_log")
public class SysJobRunLog {

    /** 执行日志ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 任务ID（关联 sys_job_conf.id），对应 job_id */
    private String jobId;

    /** 触发类型：SCHEDULED/MANUAL，对应 trigger_type */
    private String triggerType;

    /** 原因（手动触发必填），对应 reason */
    private String reason;

    /** 开始时间，对应 start_time */
    private LocalDateTime startTime;

    /** 结束时间，对应 end_time */
    private LocalDateTime endTime;

    /** 状态：RUNNING/SUCCESS/FAILED，对应 status */
    private String status;

    /** 错误信息（失败时），对应 error_msg */
    private String errorMsg;

    /** 触发人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** Quartz 计划触发时间（V1.6 新增，用于 misfire 排查），对应 scheduled_fire_time */
    private LocalDateTime scheduledFireTime;
}
