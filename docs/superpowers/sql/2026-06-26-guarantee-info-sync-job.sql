-- ============================================================================
-- 担保信息每日同步：注册 Quartz 定时任务 GUARANTEE_INFO_SYNC（每日 06:30）
--
-- quartz_job_class=com.bank.branch.platform.portal.job.quartz.GuaranteeSyncJob，
-- 调用 GuaranteeSyncService.processing()：把前一日 clms_ed_credit_info 担保类授信额度
-- 同步进 zh_guarantee_info（先 updateToGuarantee 更新已存在客户，再 saveToGuarantee 补录
-- 新客户，saveToCcmsBusiness 归集 000020 类到 ccms_business_contract，最后
-- updateToGuaranteeUpdate 刷新 update_time）。
--
-- cron '0 30 6 * * ?' = 每天上午 6:30:00 触发。
-- 生效方式（见 运维Runbook §5.1）：重启应用或调 reschedule 端点，JobService.syncJobsOnStartup
--   （overwriteExistingJobs=true）覆盖 QRTZ_CRON_TRIGGERS 后生效。
--
-- yiti(dev) + onepl(prod) 双库；SYS_JOB_CONF INSERT IGNORE 按 job_key 唯一键幂等。
-- ============================================================================

-- ---------- yiti ----------
INSERT IGNORE INTO yiti.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'GUARANTEE_INFO_SYNC', '担保信息同步', '0 30 6 * * ?',
   'com.bank.branch.platform.portal.job.quartz.GuaranteeSyncJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每日 06:30 同步前一日 clms_ed_credit_info 担保类额度到 zh_guarantee_info', NOW());

-- ---------- onepl ----------
INSERT IGNORE INTO onepl.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'GUARANTEE_INFO_SYNC', '担保信息同步', '0 30 6 * * ?',
   'com.bank.branch.platform.portal.job.quartz.GuaranteeSyncJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每日 06:30 同步前一日 clms_ed_credit_info 担保类额度到 zh_guarantee_info', NOW());

-- ---------- 校验（可选） ----------
-- SELECT job_key, cron_expr, status, quartz_job_class FROM yiti.SYS_JOB_CONF  WHERE job_key='GUARANTEE_INFO_SYNC';
-- SELECT job_key, cron_expr, status, quartz_job_class FROM onepl.SYS_JOB_CONF WHERE job_key='GUARANTEE_INFO_SYNC';
