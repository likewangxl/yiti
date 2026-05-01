-- 反向：恢复 DailyKpiCalcJob（占位）
-- 警告：实际类已删除，回滚需先恢复 jar 才有意义；本脚本仅用于回滚 sys_job_conf 行
-- 字段值与 V1.6 历史真实数据对齐（备份证据见 docs/superpowers/sql/backup/2026-04-25-pre-option-a-backup.sql）
-- 此脚本需手动执行；非 Flyway 自动迁移（项目无 Flyway 自动加载配置）
INSERT INTO SYS_JOB_CONF (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, created_by, created_time, updated_by, updated_time)
VALUES ('JOB_DAILY_KPI_CALC', 'DAILY_KPI_CALC', '日常 KPI 计算', '0 30 1 * * ?',
        'com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob',
        'FIRE_ONCE_NOW', 'ACTIVE', 1, 'SYSTEM', NOW(), 'SYSTEM', NOW())
ON DUPLICATE KEY UPDATE updated_time = NOW();
