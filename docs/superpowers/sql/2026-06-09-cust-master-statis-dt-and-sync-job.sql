-- ============================================================================
-- 客户信息同步：CUST_MASTER 增加统计日期 statis_dt + 注册 Quartz 定时任务 CUST_INFO_SYNC
--
-- 1) CUST_MASTER 新增 statis_dt varchar(10)（客户信息同步来源统计日期）
-- 2) SYS_JOB_CONF 注册 CUST_INFO_SYNC（quartz_job_class=customer.job.quartz.CustMasterSyncJob，
--    每日 00:30 触发，调用 CustMasterSyncService.syncYesterday：把 XAN_M98_CUST_STAT_SHOW3
--    昨日新出现、CUST_MASTER 不存在的客户按 CUST_ID/CUST_NAME 去重插入客户主档）
--
-- yiti(dev) + onepl(prod) 双库；ALTER 带 INFORMATION_SCHEMA 预检幂等；SYS_JOB_CONF INSERT IGNORE 幂等。
-- ============================================================================

-- ---------- 1) ALTER CUST_MASTER ADD statis_dt（幂等） ----------
-- yiti
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='CUST_MASTER' AND COLUMN_NAME='statis_dt');
SET @s := IF(@c=0,
  'ALTER TABLE yiti.CUST_MASTER ADD COLUMN statis_dt varchar(10) NULL COMMENT ''统计日期(yyyy-MM-dd)，客户信息同步来源日期'' AFTER deleted',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- onepl
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME='CUST_MASTER' AND COLUMN_NAME='statis_dt');
SET @s := IF(@c=0,
  'ALTER TABLE onepl.CUST_MASTER ADD COLUMN statis_dt varchar(10) NULL COMMENT ''统计日期(yyyy-MM-dd)，客户信息同步来源日期'' AFTER deleted',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2) 注册定时任务 CUST_INFO_SYNC（幂等：job_key 唯一键） ----------
INSERT IGNORE INTO yiti.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'CUST_INFO_SYNC', '客户信息同步', '0 30 0 * * ?',
   'com.bank.branch.platform.customer.job.quartz.CustMasterSyncJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每日同步 XAN_M98_CUST_STAT_SHOW3 昨日新客户到 CUST_MASTER', NOW());

INSERT IGNORE INTO onepl.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'CUST_INFO_SYNC', '客户信息同步', '0 30 0 * * ?',
   'com.bank.branch.platform.customer.job.quartz.CustMasterSyncJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每日同步 XAN_M98_CUST_STAT_SHOW3 昨日新客户到 CUST_MASTER', NOW());
