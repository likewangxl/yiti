-- V1.12 # 5 schema column drift 一次性 cleanup SQL（2026-05-01）
--
-- 问题描述：
-- bootstrap/src/test/resources/customer-marketing-schema.sql 用 `CREATE TABLE IF NOT EXISTS`，
-- 不会更新已存在表的列定义。当 schema.sql 后续加新列（如 V1.6+ 加 TOUCH_TASK.sla_warning）时，
-- 已存在的旧表（V1.6 之前创建的）会缺这些列，导致 IT 跑时 mybatis 报
-- 'Unknown column XYZ in field list' 错误。
--
-- 影响范围：
-- - 任何用 V1.10 之前 onepl_test_bootstrap 测试库的 CI / 本地实例
-- - V1.11 # 1 P4 实测发现 TOUCH_TASK.sla_warning 缺失（schema.sql 含但旧表无）
--
-- 修复策略：
-- 这是一次性修复脚本，用于把现役 onepl_test_bootstrap 库与 schema.sql 列定义对齐。
-- 后续如再有 schema.sql 加新列，需要在本目录新建对应日期前缀的 ALTER 脚本。
--
-- 永久解决方案（可选，未在 V1.12 落地）：
-- - 选 A：customer-marketing-schema.sql 改 DROP TABLE + CREATE TABLE（每次 IT @Sql 都重建）
-- - 选 B：CI 自动 audit + 自动生成 ALTER 脚本
-- 当前选保守方案 A 注释（schema.sql 加 V1.12 # 5 注释指向本脚本）。
--
-- 使用方法：
--   mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-05-01-v1.12-schema-column-drift-fix.sql

USE onepl_test_bootstrap;

-- =====================================================================
-- TOUCH_TASK：补 sla_warning 列（V1.6+ 加，对应 schema.sql 行 136）
-- =====================================================================
-- mysql 8.0 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS，使用 INFORMATION_SCHEMA 检查
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'onepl_test_bootstrap'
      AND BINARY table_name = 'TOUCH_TASK'
      AND column_name = 'sla_warning'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE TOUCH_TASK ADD COLUMN sla_warning TINYINT DEFAULT 0 AFTER sla_status',
    'SELECT "sla_warning column already exists, skipping" AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 验证：列举 TOUCH_TASK 应有的列（与 schema.sql line 125-143 对齐）
-- =====================================================================
SELECT 'TOUCH_TASK columns audit' AS info;
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name = 'TOUCH_TASK'
ORDER BY ordinal_position;

-- =====================================================================
-- 验证：sla_warning 列是否已加（应为 TINYINT，default 0）
-- =====================================================================
SELECT 'sla_warning_present' AS check_item, COUNT(*) AS result
FROM information_schema.columns
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name = 'TOUCH_TASK'
  AND column_name = 'sla_warning';

-- =====================================================================
-- PERF_METRIC_DEF：补 V1.7 加的 3 列（cron_expr / subject_sql / last_run_time）
-- 对应 Flyway 脚本 V1_7_0__perf_metric_def_schedule_cols.sql
-- 影响：MetricScheduledE2EIT 在缺这 3 列时 metricSchedulerService.register 失败 → Quartz JobKey 未注入 → 断言 fail
-- =====================================================================
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'onepl_test_bootstrap'
      AND BINARY table_name = 'PERF_METRIC_DEF'
      AND column_name = 'cron_expr'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE PERF_METRIC_DEF ADD COLUMN cron_expr VARCHAR(50) AFTER calc_freq',
    'SELECT "cron_expr column already exists, skipping" AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'onepl_test_bootstrap'
      AND BINARY table_name = 'PERF_METRIC_DEF'
      AND column_name = 'subject_sql'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE PERF_METRIC_DEF ADD COLUMN subject_sql TEXT AFTER cron_expr',
    'SELECT "subject_sql column already exists, skipping" AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'onepl_test_bootstrap'
      AND BINARY table_name = 'PERF_METRIC_DEF'
      AND column_name = 'last_run_time'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE PERF_METRIC_DEF ADD COLUMN last_run_time DATETIME AFTER subject_sql',
    'SELECT "last_run_time column already exists, skipping" AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证：PERF_METRIC_DEF 应有 V1.7 三列
SELECT 'perf_metric_def_v17_cols_present' AS check_item,
       COUNT(*) AS result
FROM information_schema.columns
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name = 'PERF_METRIC_DEF'
  AND column_name IN ('cron_expr', 'subject_sql', 'last_run_time');
-- 期望 result=3

-- =====================================================================
-- V1.12 # 5 一次性 cleanup 完成
-- =====================================================================
-- 后续 schema 列变更应：
-- 1. 在 customer-marketing-schema.sql 或 V_*.sql 加新列定义
-- 2. 在 docs/superpowers/sql/ 新建日期前缀脚本含 ALTER 兜底
-- 3. 测试库实例手工跑该脚本对齐
