package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务调度配置实体，对应 sys_job_conf 表。
 * <p>
 * job_key 为任务唯一标识（UK 约束），status 支持 ACTIVE/PAUSED。
 * allow_manual_trigger 使用 tinyint(1)，1=允许手动触发，0=不允许。
 * </p>
 */
@Data
@TableName("SYS_JOB_CONF")
public class SysJobConf {

    /** 任务ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 任务KEY（唯一标识），对应 job_key */
    private String jobKey;

    /** 任务名称，对应 job_name */
    private String jobName;

    /** Cron表达式，对应 cron_expr */
    private String cronExpr;

    /** Quartz 包装 Job 类全限定名（V1.6 新增），对应 quartz_job_class */
    private String quartzJobClass;

    /** misfire 处理策略：FIRE_ONCE_NOW/DO_NOTHING/IGNORE_MISFIRE_POLICY（V1.6 新增），对应 misfire_policy */
    private String misfirePolicy;

    /** 状态：ACTIVE/PAUSED，对应 status */
    private String status;

    /** 是否允许手动触发：1-允许，0-不允许，对应 allow_manual_trigger */
    private Integer allowManualTrigger;

    /** 上次执行时间，对应 last_run_time */
    private LocalDateTime lastRunTime;

    /** 下次执行时间（可选），对应 next_run_time */
    private LocalDateTime nextRunTime;

    /** 备注，对应 remark */
    private String remark;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
