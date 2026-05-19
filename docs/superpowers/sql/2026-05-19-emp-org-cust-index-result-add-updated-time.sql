-- ============================================================================
-- V1.12 指标结果导入：EMP/ORG/CUST_INDEX_RESULT 增加 updated_time 列
-- 生效日期: 2026-05-19
--
-- 背景：V1.12 新增 importType=METRIC_RESULT 通道，按 sheet 名作 dataDate、
--      每行 (基础维度, 维度对象, 指标名称, 指标数值) 长格式导入；
--      上线后宽表行存在重复主键时 UPSERT 仅更新 val_${slot}，
--      但产品要求"刷新导入时间戳"以便运维和报表追溯，故新增 updated_time 列。
--
-- 字段语义：
--   - DEFAULT CURRENT_TIMESTAMP：首次 INSERT 自动填当前时间
--   - ON UPDATE CURRENT_TIMESTAMP：ON DUPLICATE KEY UPDATE 命中时 MySQL 自动
--     刷新（前提是至少一个 val_* 列真实变化，业务侧仍显式 SET updated_time=NOW()
--     兜底相同值更新场景）
--
-- 幂等性：INFORMATION_SCHEMA 预检 + ALTER TABLE 仅在缺列时执行。
-- 回滚：执行 ALTER TABLE ... DROP COLUMN updated_time，业务回退到 V1.11。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) EMP_INDEX_RESULT
-- ----------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EMP_INDEX_RESULT'
      AND COLUMN_NAME = 'updated_time'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE EMP_INDEX_RESULT ADD COLUMN `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''最近更新时间（V1.12 指标结果导入）'' AFTER `created_time`',
    'SELECT ''EMP_INDEX_RESULT.updated_time 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2) ORG_INDEX_RESULT
-- ----------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ORG_INDEX_RESULT'
      AND COLUMN_NAME = 'updated_time'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE ORG_INDEX_RESULT ADD COLUMN `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''最近更新时间（V1.12 指标结果导入）'' AFTER `created_time`',
    'SELECT ''ORG_INDEX_RESULT.updated_time 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3) CUST_INDEX_RESULT
-- ----------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'CUST_INDEX_RESULT'
      AND COLUMN_NAME = 'updated_time'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE CUST_INDEX_RESULT ADD COLUMN `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''最近更新时间（V1.12 指标结果导入）'' AFTER `created_time`',
    'SELECT ''CUST_INDEX_RESULT.updated_time 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 验证
-- ----------------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('EMP_INDEX_RESULT', 'ORG_INDEX_RESULT', 'CUST_INDEX_RESULT')
   AND COLUMN_NAME = 'updated_time';
-- 预期：3 行，EXTRA 含 'on update CURRENT_TIMESTAMP'
