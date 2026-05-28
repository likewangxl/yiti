-- =========================================================
-- 2026-05-22 PERF_TARGET_PLAN 新增 start_date / end_date 列
-- =========================================================
-- 背景：
--   前端"新增方案"需求：方案要选起始日期、截止日期（覆盖业务周期），
--   原表只有 effective_date（生效日期）和 target_cycle（MONTHLY/QUARTERLY 等枚举），
--   缺真实业务区间字段。本次增量加列，不破坏 target_cycle / effective_date 既有语义。
--
-- 部署范围（双库执行 - feedback_dual_db_schema_deploy）：
--   - yiti  本地 dev profile 连接库
--   - onepl 文档基线 / 测试库
--
-- 幂等：INFORMATION_SCHEMA 预检 + 动态 SQL，重跑命中 0 行不报错
-- =========================================================

SET @schema = DATABASE();

-- start_date
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = @schema
                     AND TABLE_NAME   = 'PERF_TARGET_PLAN'
                     AND COLUMN_NAME  = 'start_date');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE PERF_TARGET_PLAN ADD COLUMN start_date date NULL COMMENT ''方案覆盖起始日期'' AFTER effective_date',
              'SELECT ''start_date 已存在，跳过'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- end_date
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = @schema
                     AND TABLE_NAME   = 'PERF_TARGET_PLAN'
                     AND COLUMN_NAME  = 'end_date');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE PERF_TARGET_PLAN ADD COLUMN end_date date NULL COMMENT ''方案覆盖截止日期'' AFTER start_date',
              'SELECT ''end_date 已存在，跳过'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证：
--   SHOW COLUMNS FROM PERF_TARGET_PLAN LIKE 'start_date';
--   SHOW COLUMNS FROM PERF_TARGET_PLAN LIKE 'end_date';
