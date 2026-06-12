-- =====================================================================
-- PERF_ALLOC_ADJUST_ITEM 删除 item_kind 字段（幂等迁移）
-- 背景：原业绩分配改由 CUST_ALLOC_RELATION(is_original=2) 承载，
--       PERF_ALLOC_ADJUST_ITEM 仅存调整明细，item_kind(NEW/ORIGIN)不再需要
-- 部署：yiti / onepl / onepl_test_bootstrap 均执行
-- 步骤：① 删除已废弃的历史 ORIGIN 行 ② 唯一键 uk_apply_emp_kind 由
--       (apply_id,emp_id,item_kind) 收敛为 (apply_id,emp_id) ③ 删列
-- 备份：yiti 20 行 ORIGIN 见 backup/2026-06-12-yiti-perf-alloc-item-origin-rows.sql
-- 幂等：各步按 INFORMATION_SCHEMA 预检，已处理则跳过
-- =====================================================================

-- ① 删除废弃 ORIGIN 行（仅当 item_kind 列尚在）
SET @hascol = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='PERF_ALLOC_ADJUST_ITEM' AND COLUMN_NAME='item_kind');
SET @d = IF(@hascol=1,
    "DELETE FROM PERF_ALLOC_ADJUST_ITEM WHERE item_kind='ORIGIN'",
    "SELECT 'item_kind gone, skip ORIGIN delete'");
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ② uk_apply_emp_kind 含 item_kind 时收敛为 (apply_id, emp_id)
SET @uk3 = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='PERF_ALLOC_ADJUST_ITEM'
              AND INDEX_NAME='uk_apply_emp_kind' AND COLUMN_NAME='item_kind');
SET @r = IF(@uk3=1,
    "ALTER TABLE PERF_ALLOC_ADJUST_ITEM DROP INDEX uk_apply_emp_kind, ADD UNIQUE KEY uk_apply_emp_kind (apply_id, emp_id)",
    "SELECT 'uk_apply_emp_kind already 2-col or absent'");
PREPARE s FROM @r; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ 删列
SET @c = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='PERF_ALLOC_ADJUST_ITEM' AND COLUMN_NAME='item_kind');
SET @ddl = IF(@c=1,
    "ALTER TABLE PERF_ALLOC_ADJUST_ITEM DROP COLUMN item_kind",
    "SELECT 'item_kind already dropped'");
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
