-- V1.13 # 1 DROP 其他模块小写历史表（2026-05-02）
--
-- 问题描述：
-- mysql Linux 默认 lower_case_table_names=0（case-sensitive），onepl_test_bootstrap
-- 除 V1.12 已 DROP 的 8 张 customer 小写表外，还有 23 张其他模块（perf/rpt/bizapp/sys）
-- 小写历史表残留（V1.10 治理前手工创建）。业务 mapper 仅用大写表，小写表为冗余历史数据。
--
-- 数据 baseline 实测（2026-05-02）：
-- - 21 张 = 0 行（安全 DROP）
-- - loan_apply = 1 行       ← V1.10 之前 BusinessApplicationCenterIT 残留
-- - support_request = 1 行  ← V1.10 之前 BusinessApplicationCenterIT 残留
-- - 全量备份：docs/superpowers/sql/backup/2026-05-02-v1.13-onepl-test-bootstrap-pre-cleanup.sql.gz
-- - 业务依赖：无（mapper *.xml 仅引用大写表）
--
-- 处置：安全 DROP（无业务依赖 + 完整 mysqldump 备份 + 残留行重复无独立价值）
--
-- 使用方法：
--   mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-05-02-v1.13-drop-other-lowercase-tables.sql

USE onepl_test_bootstrap;

-- DROP 顺序无依赖（小写表无外键约束）

-- customer 模块剩余 2 张（V1.12 drop 漏的）
DROP TABLE IF EXISTS `cust_alloc_relation`;
DROP TABLE IF EXISTS `cust_index_result`;

-- bizapp 模块 2 张（含 1 行残留）
DROP TABLE IF EXISTS `loan_apply`;
DROP TABLE IF EXISTS `support_request`;

-- performance 模块 14 张（cust_index_result/emp_index_result/org_index_result/kpi_result + perf_*）
DROP TABLE IF EXISTS `emp_index_result`;
DROP TABLE IF EXISTS `org_index_result`;
DROP TABLE IF EXISTS `kpi_result`;
DROP TABLE IF EXISTS `perf_alloc_adjust_apply`;
DROP TABLE IF EXISTS `perf_alloc_adjust_item`;
DROP TABLE IF EXISTS `perf_import_batch`;
DROP TABLE IF EXISTS `perf_kpi_item`;
DROP TABLE IF EXISTS `perf_kpi_scheme`;
DROP TABLE IF EXISTS `perf_metric_def`;
DROP TABLE IF EXISTS `perf_metric_ref`;
DROP TABLE IF EXISTS `perf_run_task`;
DROP TABLE IF EXISTS `perf_target_adjust_apply`;
DROP TABLE IF EXISTS `perf_target_plan`;
DROP TABLE IF EXISTS `perf_target_value`;

-- report 模块 4 张
DROP TABLE IF EXISTS `rpt_export_task`;
DROP TABLE IF EXISTS `rpt_saved_query`;
DROP TABLE IF EXISTS `rpt_snapshot_task`;
DROP TABLE IF EXISTS `sql_probe_history`;

-- governance 1 张
DROP TABLE IF EXISTS `sys_control`;

-- 验证：23 张小写表全部不存在
SELECT 'lowercase_other_tables_remaining' AS check_item,
       COUNT(*) AS result
FROM information_schema.tables
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name IN (
      'cust_alloc_relation','cust_index_result',
      'loan_apply','support_request',
      'emp_index_result','org_index_result','kpi_result',
      'perf_alloc_adjust_apply','perf_alloc_adjust_item',
      'perf_import_batch','perf_kpi_item','perf_kpi_scheme',
      'perf_metric_def','perf_metric_ref','perf_run_task',
      'perf_target_adjust_apply','perf_target_plan','perf_target_value',
      'rpt_export_task','rpt_saved_query','rpt_snapshot_task','sql_probe_history',
      'sys_control'
  );
-- 期望 result=0

-- 验证：对应大写业务表仍存在（业务正常）
SELECT 'uppercase_tables_present' AS check_item,
       COUNT(*) AS result
FROM information_schema.tables
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name IN (
      'CUST_ALLOC_RELATION','CUST_INDEX_RESULT',
      'LOAN_APPLY','SUPPORT_REQUEST',
      'EMP_INDEX_RESULT','ORG_INDEX_RESULT','KPI_RESULT',
      'PERF_ALLOC_ADJUST_APPLY','PERF_ALLOC_ADJUST_ITEM',
      'PERF_IMPORT_BATCH','PERF_KPI_ITEM','PERF_KPI_SCHEME',
      'PERF_METRIC_DEF','PERF_METRIC_REF','PERF_RUN_TASK',
      'PERF_TARGET_ADJUST_APPLY','PERF_TARGET_PLAN','PERF_TARGET_VALUE',
      'RPT_EXPORT_TASK','RPT_SAVED_QUERY','RPT_SNAPSHOT_TASK','SQL_PROBE_HISTORY',
      'SYS_CONTROL'
  );
-- 期望 result=23

-- 回滚方法（如需恢复小写表）：
--   gunzip -c docs/superpowers/sql/backup/2026-05-02-v1.13-onepl-test-bootstrap-pre-cleanup.sql.gz | \
--     mysql -uroot -pdjdev onepl_test_bootstrap
-- 注意：备份是全库 dump，回滚会同步覆盖大写表数据 → 用于灾难恢复，不用于精细回滚
