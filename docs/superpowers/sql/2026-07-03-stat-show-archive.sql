-- ============================================================================
-- 统计展示表「日增量归档 + 分批清理 + 主表瘦身」(v2)：注册 Quartz 任务 STAT_SHOW_ARCHIVE
--
-- 主表：XAN_M98_CUST_STAT_SHOW3（客户）、XAN_M98_EMP_STAT_SHOW3（员工）
-- 历史表：主表名 + _H1/_H2/_H3（_H2=每月1~10日、_H3=11~20日、_H1=21~月末）
--   ★ 6 张历史表的显式建表 DDL 见同目录 2026-07-03-stat-show-archive-hist-ddl.sql（先在两库各执行该 DDL）。
--
-- 任务 STAT_SHOW_ARCHIVE：quartz_job_class=performance.job.quartz.StatShowArchiveQuartzJob
--   cron '0 30 6-18 * * ?' = 每天 6:30、7:30 … 18:30（共 13 次）循环触发。
--   每次：把昨天所属旬(旬首~昨天)逐日增量搬进历史表(count比对幂等)；1/11/21 号按天分批清上一代旧旬；
--         1 号按天分批把上月非月末从主表删掉(保留月末)。全程幂等、逐日提交、@DisallowConcurrentExecution。
--
-- yiti(dev) + onepl(prod) 双库；均幂等：建表见 -hist-ddl.sql + SYS_JOB_CONF INSERT IGNORE。
-- 生效：重启应用或调 reschedule；JobService.syncJobsOnStartup(overwriteExistingJobs=true) 覆盖 QRTZ_ 触发器。
-- ============================================================================

-- ---------- 前置：先在 yiti 与 onepl 两库各执行 2026-07-03-stat-show-archive-hist-ddl.sql 建 6 张历史表 ----------

-- ---------- yiti：注册任务 ----------
INSERT IGNORE INTO yiti.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'STAT_SHOW_ARCHIVE', '统计展示表日增量归档', '0 30 6-18 * * ?',
   'com.bank.branch.platform.performance.job.quartz.StatShowArchiveQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天6:30~18:30循环：昨天旬块逐日增量搬入_H1/H2/H3(幂等)；1/11/21按天分批清上一代旧旬；1号按天分批瘦身主表留月末', NOW());

-- ---------- onepl：注册任务 ----------
INSERT IGNORE INTO onepl.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'STAT_SHOW_ARCHIVE', '统计展示表日增量归档', '0 30 6-18 * * ?',
   'com.bank.branch.platform.performance.job.quartz.StatShowArchiveQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天6:30~18:30循环：昨天旬块逐日增量搬入_H1/H2/H3(幂等)；1/11/21按天分批清上一代旧旬；1号按天分批瘦身主表留月末', NOW());

-- ---------- 校验（可选） ----------
-- SHOW TABLES FROM yiti  LIKE 'XAN_M98_%_STAT_SHOW3\_H_';
-- SELECT job_key, cron_expr, status, quartz_job_class FROM yiti.SYS_JOB_CONF  WHERE job_key='STAT_SHOW_ARCHIVE';
-- SELECT job_key, cron_expr, status, quartz_job_class FROM onepl.SYS_JOB_CONF WHERE job_key='STAT_SHOW_ARCHIVE';
