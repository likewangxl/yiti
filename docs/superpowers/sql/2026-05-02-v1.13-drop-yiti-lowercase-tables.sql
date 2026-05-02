-- V1.13 # 1d DROP yiti 开发库小写历史表（2026-05-02）
--
-- 问题描述：
-- 与 V1.13 # 1 onepl_test_bootstrap 同根因 —— mysql Linux lower_case_table_names=0
-- 区分大小写，yiti 开发库共存 32 张小写历史表（V1.10 治理之前手工创建残留）。
-- 业务 mapper xml 用大写表名，小写表为冗余历史数据，全部 0 行（实测）。
--
-- 范围（32 张）：
--   - customer 模块：cust_alloc_relation, cust_index_result（V1.12 已 DROP 8 张，
--     这 2 张是 V1.13 # 1 范围发现的遗漏，yiti 也清理）
--   - performance 模块：emp_index_result, org_index_result, kpi_result,
--     perf_alloc_adjust_apply, perf_alloc_adjust_item, perf_import_batch,
--     perf_kpi_item, perf_kpi_scheme, perf_metric_def, perf_metric_ref,
--     perf_run_task, perf_target_adjust_apply, perf_target_plan, perf_target_value
--   - portal 模块：portal_nav, portal_shortcut, product_info, addrbook_employee,
--     doc_info, file_object
--   - governance 模块：sys_calendar_day, sys_config_kv, sys_control, sys_dict,
--     sys_dict_item, sys_job_conf, sys_job_run_log, audit_log, biz_file_rel,
--     user_notification
--
-- 数据 baseline：32 张全部 0 行（实测 2026-05-02）
-- 备份：docs/superpowers/sql/backup/2026-05-02-v1.13-yiti-pre-cleanup.sql.gz
--
-- 使用方法：
--   mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-05-02-v1.13-drop-yiti-lowercase-tables.sql

USE yiti;

-- DROP 顺序无依赖（小写表无外键约束）
-- customer 模块剩余 2 张
DROP TABLE IF EXISTS `cust_alloc_relation`;
DROP TABLE IF EXISTS `cust_index_result`;

-- performance 模块 14 张
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

-- portal 模块 6 张
DROP TABLE IF EXISTS `portal_nav`;
DROP TABLE IF EXISTS `portal_shortcut`;
DROP TABLE IF EXISTS `product_info`;
DROP TABLE IF EXISTS `addrbook_employee`;
DROP TABLE IF EXISTS `doc_info`;
DROP TABLE IF EXISTS `file_object`;

-- governance 模块 10 张
DROP TABLE IF EXISTS `sys_calendar_day`;
DROP TABLE IF EXISTS `sys_config_kv`;
DROP TABLE IF EXISTS `sys_control`;
DROP TABLE IF EXISTS `sys_dict`;
DROP TABLE IF EXISTS `sys_dict_item`;
DROP TABLE IF EXISTS `sys_job_conf`;
DROP TABLE IF EXISTS `sys_job_run_log`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `biz_file_rel`;
DROP TABLE IF EXISTS `user_notification`;

-- 验证：32 张小写表全部不存在
SELECT 'lowercase_remaining' AS chk, COUNT(*) AS cnt
FROM information_schema.tables
WHERE table_schema='yiti'
  AND HEX(LEFT(table_name,1)) BETWEEN HEX('a') AND HEX('z')
  AND table_name NOT LIKE 'flyway%'
  AND table_name NOT LIKE 'qrtz_%';
-- 期望 cnt=0

-- 回滚方法：
--   gunzip -c docs/superpowers/sql/backup/2026-05-02-v1.13-yiti-pre-cleanup.sql.gz | \
--     mysql -uroot -pdjdev yiti
