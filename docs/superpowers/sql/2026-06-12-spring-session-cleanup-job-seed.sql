-- ====================================================================
-- 注册「Session 孤儿属性清理」Quartz 定时任务到 SYS_JOB_CONF
--
-- 背景：GoldenDB 去外键后 SPRING_SESSION_ATTRIBUTES 不再级联删除，
--   过期 session 的属性变孤儿无限增长。本任务每天低峰清理一次。
-- 机制：应用启动 JobService.syncJobsOnStartup() 读 status='ACTIVE' 行注册到 Quartz；
--   多实例下 QRTZ 行锁保证只跑一份。
-- 幂等：REPLACE INTO（按 uk_job_key 覆盖）。
-- 也应把本行并入 docs/schema/seed-yiti-prod-init.sql 的 SYS_JOB_CONF 段。
-- ====================================================================

REPLACE INTO SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_by, created_time)
VALUES
  ('fa11ba55ec0ffee0c1ea117c0de50001',
   'SPRING_SESSION_ATTR_CLEANUP',
   'Session孤儿属性清理',
   '0 30 3 * * ?',                                                        -- 每天 03:30 低峰
   'com.bank.branch.platform.governance.job.SpringSessionCleanupQuartzJob',
   'FIRE_ONCE_NOW',
   'ACTIVE',
   1,
   '去外键后清理孤儿 SPRING_SESSION_ATTRIBUTES，防无限增长加剧 session 锁竞争',
   'admin',
   NOW());

-- 验证：
--   SELECT job_key, cron_expr, status FROM SYS_JOB_CONF WHERE job_key='SPRING_SESSION_ATTR_CLEANUP';
--   重启应用后看日志 [JobService.syncJobsOnStartup] 是否注册成功；
--   手动触发可在「任务调度」页对该任务点执行。
