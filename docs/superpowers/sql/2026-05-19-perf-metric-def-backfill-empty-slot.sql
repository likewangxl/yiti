-- =============================================================
-- 2026-05-19 PERF_METRIC_DEF.val_slot 空值回填脚本
--
-- 背景:
--   yiti 库历史导入 90 行 base_dim=NULL 维度无关型指标，但调用方
--   实际希望它们都进 EMP 宽表。本脚本把这 90 行
--     base_dim NULL  → 'EMP'
--     val_slot NULL  → 按 metric_level 分桶递增分配空闲 slot
--
-- 槽位规则 (V1.12, 见 MetricSlotService.rangeOf):
--   L1: [1, 200]  L2: [201, 300]  L3: [301, 400]
--
-- 已占 slot 集为 EMP 整桶全局共享 (listOccupied 不分 level),
-- 因此排除已占候选时按 base_dim='EMP' 全局排除, 不再按 level 分.
--
-- 期望影响: 90 行 (L1 34 + L2 56), 备份在 PERF_METRIC_DEF_BAK_20260519.
-- =============================================================

USE yiti;

-- 1) 备份
DROP TABLE IF EXISTS PERF_METRIC_DEF_BAK_20260519;
CREATE TABLE PERF_METRIC_DEF_BAK_20260519 AS
SELECT id, metric_code, metric_name, base_dim, metric_level, val_slot,
       updated_by, updated_time
  FROM PERF_METRIC_DEF
 WHERE val_slot IS NULL AND deleted = 0;

-- 2) 一条 UPDATE 同时回填 base_dim='EMP' + val_slot
WITH RECURSIVE num AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM num WHERE n < 400
),
candidate AS (
    SELECT n AS slot,
           CASE
               WHEN n BETWEEN 1   AND 200 THEN 1
               WHEN n BETWEEN 201 AND 300 THEN 2
               WHEN n BETWEEN 301 AND 400 THEN 3
           END AS metric_level
      FROM num
),
free_slots AS (
    SELECT c.metric_level, c.slot,
           ROW_NUMBER() OVER (PARTITION BY c.metric_level ORDER BY c.slot) AS rn
      FROM candidate c
     WHERE NOT EXISTS (
         SELECT 1 FROM PERF_METRIC_DEF p
          WHERE p.base_dim = 'EMP'
            AND p.val_slot = c.slot
            AND p.deleted = 0
     )
),
null_rows AS (
    SELECT id, metric_level,
           ROW_NUMBER() OVER (PARTITION BY metric_level ORDER BY metric_code) AS rn
      FROM PERF_METRIC_DEF
     WHERE val_slot IS NULL AND deleted = 0
)
UPDATE PERF_METRIC_DEF p
  JOIN null_rows  n ON p.id = n.id
  JOIN free_slots f ON f.metric_level = n.metric_level AND f.rn = n.rn
   SET p.base_dim    = 'EMP',
       p.val_slot    = f.slot,
       p.updated_by  = 'SYS_BACKFILL_20260519',
       p.updated_time = NOW();

-- 3) 验证: 仍为 NULL 应为 0; 每桶不重复 (uk_base_dim_slot_alive 若已建会硬约束).
SELECT 'still_null' AS metric, COUNT(*) AS cnt
  FROM PERF_METRIC_DEF WHERE val_slot IS NULL AND deleted = 0
 UNION ALL
SELECT 'emp_slot_dup' AS metric, COUNT(*) - COUNT(DISTINCT val_slot) AS cnt
  FROM PERF_METRIC_DEF WHERE base_dim = 'EMP' AND val_slot IS NOT NULL AND deleted = 0;

SELECT base_dim, metric_level, COUNT(*) AS cnt, MIN(val_slot) AS min_s, MAX(val_slot) AS max_s
  FROM PERF_METRIC_DEF WHERE deleted = 0
 GROUP BY base_dim, metric_level
 ORDER BY base_dim, metric_level;

-- 回滚 (如需): 见备份表
--   UPDATE PERF_METRIC_DEF p JOIN PERF_METRIC_DEF_BAK_20260519 b USING (id)
--      SET p.base_dim = b.base_dim, p.val_slot = b.val_slot,
--          p.updated_by = b.updated_by, p.updated_time = b.updated_time;
