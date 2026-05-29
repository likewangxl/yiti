-- =============================================================================
-- 2026-05-29  清除指标自动调度任务残留（PERF_METRIC）
-- -----------------------------------------------------------------------------
-- 根因：
--   V1.13+ 已把 MetricSchedulerService.register() 改为空操作（拦住"新增"指标调度任务），
--   但**从未删除**此前已注册的 65 条 PERF_METRIC 记录。
--   - 任务调度页读 SYS_JOB_CONF → 一直显示这 65 条；
--   - governance JobService.syncJobsOnStartup 每次启动把 SYS_JOB_CONF 的 ACTIVE 任务
--     重新 scheduleJob 进 QRTZ_* → 每次重启"又出现"。
--   代码侧已无任何路径再写入 PERF_METRIC（perf 唯一 registerJob 调用即被禁用的 register）。
--
-- 处置：彻底删除残留数据。prevention（register 空操作）已在代码中，删除后不会再生成。
-- 幂等：可重复执行。建议**停应用**后执行（避免运行中 Quartz 与 SQL 删除竞态），再启动验证。
-- 适用库：dev=yiti（运行库）。生产 onepl 如有同样残留需同样执行（双库部署惯例）。
-- =============================================================================

-- 1) 清 QRTZ_* 中 JOB_GROUP='PERF_METRIC' 的作业及其触发器（按 JOB_GROUP join，兼容任意 trigger group）
DELETE ct FROM QRTZ_CRON_TRIGGERS ct
  JOIN QRTZ_TRIGGERS t ON ct.TRIGGER_NAME=t.TRIGGER_NAME AND ct.TRIGGER_GROUP=t.TRIGGER_GROUP
 WHERE t.JOB_GROUP='PERF_METRIC';

DELETE st FROM QRTZ_SIMPLE_TRIGGERS st
  JOIN QRTZ_TRIGGERS t ON st.TRIGGER_NAME=t.TRIGGER_NAME AND st.TRIGGER_GROUP=t.TRIGGER_GROUP
 WHERE t.JOB_GROUP='PERF_METRIC';

DELETE sp FROM QRTZ_SIMPROP_TRIGGERS sp
  JOIN QRTZ_TRIGGERS t ON sp.TRIGGER_NAME=t.TRIGGER_NAME AND sp.TRIGGER_GROUP=t.TRIGGER_GROUP
 WHERE t.JOB_GROUP='PERF_METRIC';

DELETE bt FROM QRTZ_BLOB_TRIGGERS bt
  JOIN QRTZ_TRIGGERS t ON bt.TRIGGER_NAME=t.TRIGGER_NAME AND bt.TRIGGER_GROUP=t.TRIGGER_GROUP
 WHERE t.JOB_GROUP='PERF_METRIC';

DELETE FROM QRTZ_FIRED_TRIGGERS WHERE JOB_GROUP='PERF_METRIC';
DELETE FROM QRTZ_TRIGGERS      WHERE JOB_GROUP='PERF_METRIC';
DELETE FROM QRTZ_JOB_DETAILS   WHERE JOB_GROUP='PERF_METRIC';

-- 2) 清任务调度页数据源 SYS_JOB_CONF 的 PERF_METRIC 残留
DELETE FROM SYS_JOB_CONF WHERE job_key LIKE 'PERF_METRIC%';

-- 验证（均应为 0）：
-- SELECT COUNT(*) FROM SYS_JOB_CONF   WHERE job_key  LIKE 'PERF_METRIC%';
-- SELECT COUNT(*) FROM QRTZ_JOB_DETAILS WHERE JOB_GROUP='PERF_METRIC';
-- SELECT COUNT(*) FROM QRTZ_TRIGGERS    WHERE JOB_GROUP='PERF_METRIC';
