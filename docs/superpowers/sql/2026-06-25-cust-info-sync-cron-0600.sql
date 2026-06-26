-- ============================================================================
-- 客户信息同步定时任务 CUST_INFO_SYNC 调度时间调整：每日 00:30 → 每日 06:00
--
-- 背景：CUST_INFO_SYNC（quartz_job_class=customer.job.quartz.CustMasterSyncJob）每日把
--       XAN_M98_CUST_STAT_SHOW3 中"前一日"新出现、CUST_MASTER 不存在的客户同步进客户主档
--       （CustMasterSyncService.syncYesterday）。原 cron '0 30 0 * * ?'（00:30）调整为
--       '0 0 6 * * ?'（每天上午 6:00 执行）。
--
-- 生效方式（见 运维Runbook §5.1）：本脚本仅改 SYS_JOB_CONF.cron_expr；需重启应用，或调用
--       POST /api/admin/sys/jobs/{jobId}/reschedule，由 JobService.syncJobsOnStartup
--       （overwriteExistingJobs=true）覆盖 QRTZ_CRON_TRIGGERS 后才真正改变触发时间。
--
-- yiti(dev) + onepl(prod) 双库；UPDATE 按 job_key 定位，天然幂等。
-- ============================================================================

-- ---------- yiti ----------
UPDATE yiti.SYS_JOB_CONF
   SET cron_expr = '0 0 6 * * ?'
 WHERE job_key = 'CUST_INFO_SYNC';

-- ---------- onepl ----------
UPDATE onepl.SYS_JOB_CONF
   SET cron_expr = '0 0 6 * * ?'
 WHERE job_key = 'CUST_INFO_SYNC';

-- ---------- 校验（可选）：确认 cron_expr 已为 06:00 ----------
-- SELECT job_key, cron_expr, status FROM yiti.SYS_JOB_CONF  WHERE job_key = 'CUST_INFO_SYNC';
-- SELECT job_key, cron_expr, status FROM onepl.SYS_JOB_CONF WHERE job_key = 'CUST_INFO_SYNC';
