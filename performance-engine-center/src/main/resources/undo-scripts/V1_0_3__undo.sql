-- =====================================================================
-- V1_0_3 紧急回滚脚本（undo）
-- 撤销 V1_0_3 的 sys_control + perf_metric_def DDL 变更
--
-- 警告：
--   1. 执行前务必备份数据（mysqldump 相关表）
--   2. 本脚本由 Flyway Community Edition 不支持自动 undo，仅供 DBA 手动执行
--   3. 执行后 sys_control.remark/publish_* 字段及 perf_metric_def 新字段中的数据将丢失
-- =====================================================================

-- Task B2 undo: 删除 perf_metric_def 槽位唯一键（先删依赖字段的索引）
ALTER TABLE perf_metric_def DROP INDEX uk_base_dim_slot_alive;

-- Task B4 undo: 删除 perf_metric_def 新增字段
ALTER TABLE perf_metric_def
  DROP COLUMN unit,
  DROP COLUMN decimal_places,
  DROP COLUMN deleted,
  DROP COLUMN description;

-- Task B3 undo: 删除 sys_control 新增字段
ALTER TABLE sys_control
  DROP COLUMN remark,
  DROP COLUMN updated_by,
  DROP COLUMN publish_source,
  DROP COLUMN publish_by,
  DROP COLUMN publish_time;

-- Task B1 undo: 还原 sys_control 唯一键（回到原始的二列 UK）
ALTER TABLE sys_control DROP INDEX uk_scope_dim_date_version;
ALTER TABLE sys_control ADD UNIQUE KEY uk_scope_dim_date (scope_dim, latest_data_date);
