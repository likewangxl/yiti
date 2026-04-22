-- =====================================================================
-- performance-engine-center V1.0.3 Schema Alignment Script
-- Version: V1_0_3
-- Date: 2026-04-22
--
-- 目标：修复 V1.0 与文档/DDL 权威源的偏离，不引入新业务能力。
-- 顺序：
--   1. sys_control UK 改造（Task B1）
--   2. sys_control 补 5 字段（Task B3）
--   3. perf_metric_def 补 4 字段（Task B4，含 deleted）
--   4. perf_metric_def 槽位唯一键（Task B2，依赖 deleted 字段）
-- =====================================================================

-- =====================================================================
-- Task B1: sys_control 唯一键补 current_version 列
-- 拆旧 UK (scope_dim, latest_data_date) + 建新 UK (scope_dim, latest_data_date, current_version)
-- =====================================================================
ALTER TABLE sys_control DROP INDEX uk_scope_dim_date;
ALTER TABLE sys_control
  ADD UNIQUE KEY uk_scope_dim_date_version (scope_dim, latest_data_date, current_version);

-- =====================================================================
-- Task B3: sys_control 补 5 个字段
-- 文档 01 §2.2 要求字段：remark、updated_by、publish_source、publish_by、publish_time
-- =====================================================================
ALTER TABLE sys_control
  ADD COLUMN remark VARCHAR(255) NULL COMMENT '切版备注' AFTER current_version,
  ADD COLUMN updated_by VARCHAR(32) NULL COMMENT '最后更新人',
  ADD COLUMN publish_source VARCHAR(32) NULL COMMENT '发布来源：MANUAL/AUTO/ROLLBACK',
  ADD COLUMN publish_by VARCHAR(32) NULL COMMENT '发布人',
  ADD COLUMN publish_time DATETIME NULL COMMENT '发布时间';

-- =====================================================================
-- Task B4: perf_metric_def 补 4 个字段
-- 文档 05 §2.2 要求字段：unit、decimal_places、deleted、description
-- =====================================================================
ALTER TABLE perf_metric_def
  ADD COLUMN unit VARCHAR(16) NULL COMMENT '单位：元/万元/%',
  ADD COLUMN decimal_places TINYINT DEFAULT 2 COMMENT '小数位数',
  ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '0=存在 1=删除',
  ADD COLUMN description VARCHAR(500) NULL COMMENT '指标详细描述（补充 metric_desc）';
-- 所有现存行 deleted=0
UPDATE perf_metric_def SET deleted = 0 WHERE deleted IS NULL;

-- =====================================================================
-- Task B2: perf_metric_def 槽位唯一键（仅对未删除行）
-- 依赖 Task B4 引入的 deleted 字段，必须在 B4 之后执行。
-- MySQL 8 函数索引：仅当 deleted=0 时约束 (base_dim, val_slot) 唯一；
-- deleted=1 时 IF 返回 NULL，NULL 不参与唯一键约束，允许同 slot 的软删除行。
-- =====================================================================
ALTER TABLE perf_metric_def
  ADD UNIQUE KEY uk_base_dim_slot_alive
  ((IF(deleted=0, CONCAT(base_dim,'#',val_slot), NULL)));
