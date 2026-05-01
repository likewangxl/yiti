-- 反向：恢复 DailyKpiCalcJob（仅占位，实际类已删除，回滚需先恢复 jar）
INSERT INTO SYS_JOB_CONF (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, created_by, created_time, updated_by, updated_time)
VALUES (UUID(), 'PERF_DAILY_KPI_CALC', '日常 KPI 计算', '0 0 2 * * ?',
        'com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob',
        'FIRE_ONCE_NOW', 'ACTIVE', 1, 'SYSTEM', NOW(), 'SYSTEM', NOW())
ON DUPLICATE KEY UPDATE updated_time = NOW();
