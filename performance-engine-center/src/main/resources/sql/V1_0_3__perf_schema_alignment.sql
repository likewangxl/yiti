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
