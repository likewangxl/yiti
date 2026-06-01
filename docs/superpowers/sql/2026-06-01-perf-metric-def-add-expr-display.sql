-- =====================================================================
-- 2026-06-01  PERF_METRIC_DEF 新增 expr_display 列
-- 用途：保存"含标签"的 Groovy 表达式展示串（指标编号·名称 + 运算符），仅供查看显示；
--       计算仍用 expr_text（指标编号 Groovy）。两列在保存指标时同步更新。
-- 幂等：通过 INFORMATION_SCHEMA 预检，列已存在则跳过（MySQL 8.0 ADD COLUMN 无 IF NOT EXISTS）。
-- 部署：dev 库 yiti + 生产库 onepl 双跑。
-- =====================================================================

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE PERF_METRIC_DEF ADD COLUMN expr_display VARCHAR(1000) NULL COMMENT ''表达式含标签展示串（指标编号·名称，查看用）'' AFTER expr_text',
    'SELECT ''expr_display already exists, skip'' AS msg'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'PERF_METRIC_DEF'
    AND COLUMN_NAME = 'expr_display'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
