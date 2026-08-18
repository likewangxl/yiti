-- M98 指标数据就绪协调调度与按级别重算授权。
-- 仅在审批后的目标库执行；执行后重启应用或触发治理中心同步，使 Quartz 清除旧的 Level/KPI CronTrigger。
START TRANSACTION;

-- STAT_SHOW_ARCHIVE 保留小时级兜底；数据就绪协调器发现 _TMP 与 H 表不一致时会立即触发一次归档。
UPDATE SYS_JOB_CONF
   SET cron_expr = '0 30 6-18 * * ?',
       misfire_policy = 'DO_NOTHING',
       status = 'ACTIVE',
       allow_manual_trigger = 1,
       remark = '6:30-18:30小时兜底归档；METRIC_CALC_READY_COORDINATOR 发现 _TMP/H 不一致时即时触发'
 WHERE job_key = 'STAT_SHOW_ARCHIVE';

-- 三级指标与 KPI 仅保留可被 Quartz programmatic trigger 的 JobDetail；取消各自的固定 Cron，避免空数据早跑和重复执行。
UPDATE SYS_JOB_CONF
   SET cron_expr = '',
       misfire_policy = 'DO_NOTHING',
       status = 'ACTIVE',
       allow_manual_trigger = 1,
       remark = '无独立Cron；由 METRIC_CALC_READY_COORDINATOR 在 H 表数据就绪后顺序触发，也可由绩效任务监控页手动触发'
 WHERE job_key IN ('LEVEL1_METRIC_CALC', 'LEVEL2_METRIC_CALC', 'LEVEL3_METRIC_CALC', 'KPI_SCORE_CALC');

-- 协调器每 5 分钟检查 T-1 的 _TMP/H 行数；每轮至多触发一个下游任务。
INSERT INTO SYS_JOB_CONF
       (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status,
        allow_manual_trigger, remark, created_by, created_time, updated_by, updated_time)
SELECT REPLACE(UUID(), '-', ''),
       'METRIC_CALC_READY_COORDINATOR',
       'M98指标数据就绪协调',
       '0 0/5 6-19 * * ?',
       'com.bank.branch.platform.performance.job.quartz.MetricCalcReadyCoordinatorQuartzJob',
       'DO_NOTHING', 'ACTIVE', 0,
       '每5分钟检查T-1 _TMP与H表；未归档先触发STAT_SHOW_ARCHIVE，数据就绪后依次触发L1/L2/L3/KPI',
       'deploy', NOW(), 'deploy', NOW()
 WHERE NOT EXISTS (
       SELECT 1 FROM SYS_JOB_CONF WHERE job_key = 'METRIC_CALC_READY_COORDINATOR'
 );

UPDATE SYS_JOB_CONF
   SET job_name = 'M98指标数据就绪协调',
       cron_expr = '0 0/5 6-19 * * ?',
       quartz_job_class = 'com.bank.branch.platform.performance.job.quartz.MetricCalcReadyCoordinatorQuartzJob',
       misfire_policy = 'DO_NOTHING',
       status = 'ACTIVE',
       allow_manual_trigger = 0,
       remark = '每5分钟检查T-1 _TMP与H表；未归档先触发STAT_SHOW_ARCHIVE，数据就绪后依次触发L1/L2/L3/KPI',
       updated_by = 'deploy',
       updated_time = NOW()
 WHERE job_key = 'METRIC_CALC_READY_COORDINATOR';

-- 绩效任务监控页专用高危入口：权限范围与原手动批量计算触发保持一致。
INSERT INTO PT_RESOURCE
       (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
        MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
SELECT 'P_PERF_METRIC_LEVEL_RECALC', '/api/perf/metric-calc/level-trigger', 'POST', '按级别重算指标',
       0, 0, '0', 'M_PERF_METRICS', 0, 'PLATFORM', NOW(), 'deploy',
       '绩效任务监控页按级别触发一级、二级、三级指标计算'
 WHERE NOT EXISTS (
       SELECT 1 FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_METRIC_LEVEL_RECALC'
 );

-- 沿用已获准的原手动批量计算触发角色，避免扩大授权面。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), source.ROLE_ID, 'P_PERF_METRIC_LEVEL_RECALC', source.SYS_CODE, NOW()
  FROM PT_ROLE_RESOURCE source
 WHERE source.RESOURCE_ID = 'P_PERF_CALC_TRIG'
   AND NOT EXISTS (
       SELECT 1
         FROM PT_ROLE_RESOURCE existing
        WHERE existing.ROLE_ID = source.ROLE_ID
          AND existing.RESOURCE_ID = 'P_PERF_METRIC_LEVEL_RECALC'
   );

COMMIT;
