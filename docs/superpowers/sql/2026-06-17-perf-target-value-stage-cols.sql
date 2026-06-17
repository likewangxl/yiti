-- ============================================================================
-- 2026-06-17 目标值新增「阶段名称 / 起始日期 / 截止日期」三列
--   PERF_TARGET_VALUE ADD stage_name / start_date / end_date（均可空）
--   维度沿用既有 subject_type 列，无需新增。
-- 幂等（INFORMATION_SCHEMA 预检）+ 双库 yiti / onepl。
-- ============================================================================

-- ---------- yiti ----------
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME='stage_name') = 0,
  'ALTER TABLE yiti.PERF_TARGET_VALUE ADD COLUMN stage_name varchar(100) NULL COMMENT ''阶段名称''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME='start_date') = 0,
  'ALTER TABLE yiti.PERF_TARGET_VALUE ADD COLUMN start_date date NULL COMMENT ''起始日期''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME='end_date') = 0,
  'ALTER TABLE yiti.PERF_TARGET_VALUE ADD COLUMN end_date date NULL COMMENT ''截止日期''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- onepl ----------
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME='stage_name') = 0,
  'ALTER TABLE onepl.PERF_TARGET_VALUE ADD COLUMN stage_name varchar(100) NULL COMMENT ''阶段名称''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME='start_date') = 0,
  'ALTER TABLE onepl.PERF_TARGET_VALUE ADD COLUMN start_date date NULL COMMENT ''起始日期''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME='end_date') = 0,
  'ALTER TABLE onepl.PERF_TARGET_VALUE ADD COLUMN end_date date NULL COMMENT ''截止日期''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
-- ============================================================================
