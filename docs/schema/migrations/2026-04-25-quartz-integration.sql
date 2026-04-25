-- ============================================================
-- 子项目 B（Quartz 整合）数据库迁移脚本
-- 引入版本: V1.6（2026-04-25）
-- 前置: docs/schema/ddl-quartz.sql 已先执行（11 张 QRTZ_* 表已创建）
-- ============================================================

-- Step 1: sys_job_conf 表字段扩展
ALTER TABLE sys_job_conf
    ADD COLUMN quartz_job_class VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
    ADD COLUMN misfire_policy   VARCHAR(32)  NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略：FIRE_ONCE_NOW/DO_NOTHING/IGNORE_MISFIRE_POLICY（V1.6 新增）';

-- Step 2: sys_job_run_log 表字段扩展
ALTER TABLE sys_job_run_log
    ADD COLUMN scheduled_fire_time DATETIME(3) NULL COMMENT 'Quartz 计划触发时间（V1.6 新增，用于 misfire 排查）';

-- Step 3: 写入 3 条业务 Job 记录（performance-engine-center 现有 3 个 Job）
INSERT INTO sys_job_conf (
    id, job_key, job_name, cron_expr, status, allow_manual_trigger,
    quartz_job_class, misfire_policy,
    remark, created_by, created_time, updated_by, updated_time
) VALUES
('JOB_DAILY_KPI_CALC',
 'DAILY_KPI_CALC',
 '日常 KPI 计算',
 '0 30 1 * * ?',
 'ACTIVE',
 1,
 'com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob',
 'FIRE_ONCE_NOW',
 'V1.6 quartz 整合引入；KPI 计算（T-1）错过补跑一次',
 'SYSTEM', NOW(), 'SYSTEM', NOW()),
('JOB_SYS_CONTROL_CLEANUP',
 'SYS_CONTROL_CLEANUP',
 '系统控制历史清理',
 '0 0 3 * * ?',
 'ACTIVE',
 1,
 'com.bank.branch.platform.performance.job.quartz.SysControlCleanupQuartzJob',
 'DO_NOTHING',
 'V1.6 quartz 整合引入；按 scope_dim 分组保留最新 12 条历史，错过即跳过',
 'SYSTEM', NOW(), 'SYSTEM', NOW()),
('JOB_PERF_RUN_TASK_CLEANUP',
 'PERF_RUN_TASK_CLEANUP',
 '绩效执行任务清理',
 '0 30 3 * * ?',
 'ACTIVE',
 1,
 'com.bank.branch.platform.performance.job.quartz.PerfRunTaskCleanupQuartzJob',
 'DO_NOTHING',
 'V1.6 quartz 整合引入；删除 SUCCESS + end_time<now-90d 任务，错过即跳过',
 'SYSTEM', NOW(), 'SYSTEM', NOW());
