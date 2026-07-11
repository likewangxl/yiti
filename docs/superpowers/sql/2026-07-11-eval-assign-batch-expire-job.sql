-- 导入批次过期关闭 Quartz Job 注册（2026-07-11）
-- 每 10 分钟扫描 EVAL_ASSIGN_BATCH：status=0(进行中) 且 deadline 已过 → status=1(已结束)。
-- 覆盖评价任务导入(EVAL) 与 奖励分配(REWARD) 两类导入批次。
-- governance JobService.syncJobsOnStartup 启动时按本行注册 JobDetail+Trigger（需应用重启生效）。
-- 幂等：先删后插。目标库 yiti（运行库）执行。

DELETE FROM SYS_JOB_CONF WHERE job_key = 'EVAL_ASSIGN_BATCH_EXPIRE';

INSERT INTO SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_by, created_time)
VALUES
  (REPLACE(UUID(), '-', ''),
   'EVAL_ASSIGN_BATCH_EXPIRE',
   '导入批次过期关闭',
   '0 */10 * * * ?',
   'com.bank.branch.platform.performance.eval.job.EvalAssignBatchExpireJob',
   'FIRE_ONCE_NOW',
   'ACTIVE',
   1,
   '每10分钟扫描 EVAL_ASSIGN_BATCH 过期进行中批次(评价导入+奖励分配)置已结束',
   'admin',
   NOW());
