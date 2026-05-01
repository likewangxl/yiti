package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 注册调度任务命令 (V1.7).
 *
 * <p>由业务模块（performance）通过 {@code JobApi.registerJob} 调用，承载
 * sys_job_conf upsert + Quartz JobDetail/CronTrigger 注册所需的全部参数.
 */
@Data
public class RegisterJobCmd {

    /** 必填：任务唯一标识，对应 sys_job_conf.job_key. */
    private String jobKey;

    /** 必填：任务展示名. */
    private String jobName;

    /** 必填：合法 cron 表达式. */
    private String cronExpr;

    /** 必填：QuartzJobBean 子类全限定名. */
    private String quartzJobClass;

    /** 选填：透传到 JobDataMap. */
    private Map<String, String> jobData;

    /** 选填：misfire 策略，默认 "FIRE_ONCE_NOW". */
    private String misfirePolicy = "FIRE_ONCE_NOW";

    /** 选填：是否允许 JobController 手动触发，默认 true. */
    private boolean allowManualTrigger = true;

    /** 选填：备注. */
    private String remark;
}
