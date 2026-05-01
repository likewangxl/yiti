-- V1.8 撤销脚本：删除 Lead 回调补偿 Quartz Job
-- 引入版本：V1.8（2026-05-01）
-- 注：仅 DELETE sys_job_conf 行；Quartz Scheduler 中残留 trigger 由运维或重启清理
DELETE FROM SYS_JOB_CONF WHERE job_key = 'LEAD_CALLBACK_COMPENSATE';
