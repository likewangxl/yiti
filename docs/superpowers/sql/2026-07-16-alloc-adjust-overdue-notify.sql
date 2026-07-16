-- ============================================================================
-- 业绩分配调整审批「超时提醒」定时任务 PERF_ALLOC_OVERDUE_NOTIFY
--
-- 功能：每天 9 点扫描 PERF_ALLOC_ADJUST_APPLY 中「status=IN_APPROVAL 且从申请时间(created_time)
--       起满 14 天未办结」的记录，给申请人(created_by)在通知中心发一条提醒（含当前审批环节 + 已用天数），
--       仅提醒一次（靠新增列 overdue_notified_time 去重：发过即置时间，扫描 IS NULL 过滤）。
--   quartz_job_class = com.bank.branch.platform.performance.job.quartz.PerfAllocOverdueNotifyQuartzJob
--   cron '0 0 9 * * ?' = 每天 9:00 触发一次。
--
-- yiti(dev) + onepl(prod) 双库；均幂等：加列用 INFORMATION_SCHEMA 预检（MySQL8 无 ADD COLUMN IF NOT EXISTS）；
-- 注册用 INSERT IGNORE。生效：重启应用（syncJobsOnStartup 覆盖注册 QRTZ_ 触发器）。
-- 注意：集群下所有连同一库的后端节点都必须含 PerfAllocOverdueNotifyQuartzJob 类，否则该节点抢到触发会 ERROR。
-- ============================================================================

-- ---------- yiti：加 overdue_notified_time 列（幂等）----------
SET @exist_yiti := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'yiti' AND TABLE_NAME = 'PERF_ALLOC_ADJUST_APPLY'
      AND COLUMN_NAME = 'overdue_notified_time');
SET @sql_yiti := IF(@exist_yiti = 0,
    'ALTER TABLE yiti.PERF_ALLOC_ADJUST_APPLY ADD COLUMN overdue_notified_time DATETIME NULL COMMENT ''超时提醒已发送时间(仅提醒一次)''',
    'SELECT ''yiti.PERF_ALLOC_ADJUST_APPLY.overdue_notified_time already exists''');
PREPARE s_yiti FROM @sql_yiti; EXECUTE s_yiti; DEALLOCATE PREPARE s_yiti;

-- ---------- onepl：加 overdue_notified_time 列（幂等；若无该库/表请跳过本块）----------
SET @exist_onepl := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'onepl' AND TABLE_NAME = 'PERF_ALLOC_ADJUST_APPLY'
      AND COLUMN_NAME = 'overdue_notified_time');
SET @has_onepl_tbl := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'onepl' AND TABLE_NAME = 'PERF_ALLOC_ADJUST_APPLY');
SET @sql_onepl := IF(@has_onepl_tbl > 0 AND @exist_onepl = 0,
    'ALTER TABLE onepl.PERF_ALLOC_ADJUST_APPLY ADD COLUMN overdue_notified_time DATETIME NULL COMMENT ''超时提醒已发送时间(仅提醒一次)''',
    'SELECT ''onepl skipped (no table or column exists)''');
PREPARE s_onepl FROM @sql_onepl; EXECUTE s_onepl; DEALLOCATE PREPARE s_onepl;

-- ---------- yiti：注册任务 ----------
INSERT IGNORE INTO yiti.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'PERF_ALLOC_OVERDUE_NOTIFY', '业绩分配调整审批超时提醒', '0 0 9 * * ?',
   'com.bank.branch.platform.performance.job.quartz.PerfAllocOverdueNotifyQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天9点扫描审批中且申请满14天未办结的分配调整，给申请人发通知(含当前环节+已用天数)，仅提醒一次', NOW());

-- ---------- onepl：注册任务 ----------
INSERT IGNORE INTO onepl.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'PERF_ALLOC_OVERDUE_NOTIFY', '业绩分配调整审批超时提醒', '0 0 9 * * ?',
   'com.bank.branch.platform.performance.job.quartz.PerfAllocOverdueNotifyQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天9点扫描审批中且申请满14天未办结的分配调整，给申请人发通知(含当前环节+已用天数)，仅提醒一次', NOW());

-- ---------- 校验（可选） ----------
-- SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='PERF_ALLOC_ADJUST_APPLY' AND COLUMN_NAME='overdue_notified_time';
-- SELECT job_key, cron_expr, status, quartz_job_class FROM yiti.SYS_JOB_CONF WHERE job_key='PERF_ALLOC_OVERDUE_NOTIFY';
