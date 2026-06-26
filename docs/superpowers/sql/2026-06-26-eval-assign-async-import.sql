-- ============================================================================
-- 评价任务导入异步化：EVAL_ASSIGN_BATCH 增加进度/结果列 + 扩展 STATUS 状态机
-- 生效日期: 2026-06-26  模块: performance-engine-center / eval 子域
-- 目标库: yiti（开发）/ onepl_test_bootstrap（测试）；onepl 生产上线时按需执行
--
-- 背景：POST /api/admin/eval/assign/import 由「同步原子」改为「异步 + 前端轮询」。
--      接口落库即建 IMPORTING(3) 批次并立即返回 batchId，后台 @Async 逐行校验入库，
--      前端轮询 GET /batches/{batchId} 拿 status / errorSummary / importedCount 展示结果。
--
-- 新增列：
--   TOTAL_ROWS     解析出的总行数（处理结束时回填）
--   IMPORTED_COUNT 成功入库条数（全部通过时回填）
--   ERROR_SUMMARY  失败时行级错误明细 JSON（封顶前 N 条，N=perf.eval.import.error-keep）
--
-- STATUS 状态机扩展：0=进行中, 1=已结束, 2=草稿, 3=处理中(IMPORTING), 4=导入失败(IMPORT_FAILED)
--
-- 幂等性：INFORMATION_SCHEMA 预检 + ALTER TABLE 仅在缺列时执行；STATUS 注释 MODIFY 可重复执行。
-- 回滚：DROP COLUMN TOTAL_ROWS / IMPORTED_COUNT / ERROR_SUMMARY，并把 STATUS 注释改回旧版。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) TOTAL_ROWS
-- ----------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EVAL_ASSIGN_BATCH'
      AND COLUMN_NAME = 'TOTAL_ROWS'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE EVAL_ASSIGN_BATCH ADD COLUMN `TOTAL_ROWS` INT NULL COMMENT ''解析出的总行数'' AFTER `STATUS`',
    'SELECT ''EVAL_ASSIGN_BATCH.TOTAL_ROWS 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2) IMPORTED_COUNT
-- ----------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EVAL_ASSIGN_BATCH'
      AND COLUMN_NAME = 'IMPORTED_COUNT'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE EVAL_ASSIGN_BATCH ADD COLUMN `IMPORTED_COUNT` INT NULL COMMENT ''成功入库条数'' AFTER `TOTAL_ROWS`',
    'SELECT ''EVAL_ASSIGN_BATCH.IMPORTED_COUNT 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3) ERROR_SUMMARY
-- ----------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EVAL_ASSIGN_BATCH'
      AND COLUMN_NAME = 'ERROR_SUMMARY'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE EVAL_ASSIGN_BATCH ADD COLUMN `ERROR_SUMMARY` MEDIUMTEXT NULL COMMENT ''失败时行级错误明细 JSON（封顶前 N 条）'' AFTER `IMPORTED_COUNT`',
    'SELECT ''EVAL_ASSIGN_BATCH.ERROR_SUMMARY 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4) STATUS 注释更新（扩展状态机说明）—— MODIFY 不改类型/默认值，仅刷注释，可重复执行
-- ----------------------------------------------------------------------------
ALTER TABLE EVAL_ASSIGN_BATCH
    MODIFY COLUMN `STATUS` TINYINT NOT NULL DEFAULT 0
    COMMENT '0=进行中,1=已结束,2=草稿,3=处理中,4=导入失败';

-- ----------------------------------------------------------------------------
-- 验证
-- ----------------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'EVAL_ASSIGN_BATCH'
   AND COLUMN_NAME IN ('TOTAL_ROWS', 'IMPORTED_COUNT', 'ERROR_SUMMARY', 'STATUS');
-- 预期：4 行；STATUS 注释含 '3=处理中,4=导入失败'
