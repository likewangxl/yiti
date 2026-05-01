-- V1.8 注册 Lead 回调补偿 Quartz Job
-- 引入版本：V1.8（2026-05-01）
-- 由 Spring @Scheduled(fixedDelay 5min) 迁移而来
-- 此脚本需手动执行；非 Flyway 自动迁移（项目无 Flyway 自动加载配置）

INSERT INTO SYS_JOB_CONF (
    id,
    job_key,
    job_name,
    cron_expr,
    quartz_job_class,
    misfire_policy,
    status,
    allow_manual_trigger,
    remark,
    created_by,
    created_time,
    updated_by,
    updated_time
) VALUES (
    'JOB_LEAD_CALLBACK_COMPENSATE',
    'LEAD_CALLBACK_COMPENSATE',
    'Lead 回调补偿巡检',
    '0 */5 * * * ?',
    'com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob',
    'DO_NOTHING',
    'ACTIVE',
    1,
    'V1.8 由 Spring @Scheduled 迁移；扫 stuck IN_APPROVAL 线索补偿，错过即跳过',
    'SYSTEM',
    NOW(),
    'SYSTEM',
    NOW()
);
