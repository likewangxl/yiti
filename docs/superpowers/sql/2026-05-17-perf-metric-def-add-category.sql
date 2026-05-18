-- ============================================
-- 2026-05-17 V1.9：PERF_METRIC_DEF 新增 metric_category 列
-- 配合 docs/指标表上传模板.xlsx 的"指标分类"列落库
-- 幂等：跑前先备份 `mysqldump onepl PERF_METRIC_DEF > backup/2026-05-17-perf-metric-def-backup.sql`
-- ============================================

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），需用 INFORMATION_SCHEMA 兜底幂等
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_METRIC_DEF' AND COLUMN_NAME = 'metric_category');
SET @stmt := IF(@col_exists = 0,
  'ALTER TABLE PERF_METRIC_DEF ADD COLUMN metric_category varchar(50) DEFAULT NULL COMMENT ''V1.9 指标分类（规模类/效益类/质量类/合规类等）'' AFTER description',
  'SELECT ''metric_category already exists'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_METRIC_DEF' AND INDEX_NAME = 'idx_metric_category');
SET @stmt := IF(@idx_exists = 0,
  'ALTER TABLE PERF_METRIC_DEF ADD INDEX idx_metric_category (metric_category)',
  'SELECT ''idx_metric_category already exists'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 验证：
-- SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT
--   FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'PERF_METRIC_DEF'
--    AND COLUMN_NAME = 'metric_category';
