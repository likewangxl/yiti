-- =============================================================================
-- 2026-05-19 V1.12: PERF_METRIC_DEF.val_slot 历史 NULL 行批量补齐
-- =============================================================================
-- 背景：
--   V1.9 引入"维度无关型指标"(baseDim=NULL → val_slot=NULL) 后，
--   PERF_METRIC_DEF 中还存在一批 base_dim 非空但 val_slot=NULL 的历史脏数据
--   （MetricDefService.create 早期版本漏分配 / 手工 INSERT 等遗留），
--   这些行无法被 MetricCalcService 计算（slot 校验抛错）。
--
-- 策略（方案 A：应用层补齐 + 保留 base_dim 联合唯一槽位语义）：
--   1. 仅补 base_dim IS NOT NULL AND val_slot IS NULL AND deleted=0 的行
--   2. 按 (base_dim, metric_level) 分组、按 metric_code 升序，递增分配
--   3. 起始点 = MAX(已占用 slot 在该 level 新区间内) + 1，避免冲突
--   4. V1.9 维度无关型指标 (base_dim IS NULL) 保留 NULL，不触碰
--   5. metric_level 新分段（与 MetricSlotService.rangeOf 同步）：
--      - L1 [1, 200]  ;  L2 [201, 300]  ;  L3 [301, 400]
--
-- 兼容性：
--   - 旧 L2/L3 行已落 [101,150]/[151,200] 旧区间 → 不动，alloc 不会回头校验，安全
--   - 新分配按新区间起点放，保证不与旧行冲突（uk_base_dim_slot_alive 不破）
--
-- 备份：执行前 mysqldump 备份 PERF_METRIC_DEF.val_slot 列
--   mysqldump -h localhost -u root -p<pwd> onepl PERF_METRIC_DEF \
--     --no-create-info --where="val_slot IS NULL OR val_slot > 200" \
--     > docs/superpowers/sql/backup/2026-05-19-perf-metric-def-slot-backfill.sql
--
-- 幂等：再次执行 0 行匹配（UPDATE WHERE val_slot IS NULL 自然过滤）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 预检 1：列出待补齐行的 (base_dim, metric_level) 分布
-- -----------------------------------------------------------------------------
SELECT base_dim,
       metric_level,
       COUNT(*) AS null_rows_to_backfill
  FROM PERF_METRIC_DEF
 WHERE val_slot IS NULL
   AND base_dim IS NOT NULL
   AND deleted = 0
 GROUP BY base_dim, metric_level
 ORDER BY base_dim, metric_level;

-- -----------------------------------------------------------------------------
-- 预检 2：每个 (base_dim, level) 在新区间内的当前 max(val_slot)
--         + 该 level 新区间容量是否够装
-- -----------------------------------------------------------------------------
SELECT d.base_dim,
       d.metric_level,
       (CASE d.metric_level WHEN 1 THEN 200 WHEN 2 THEN 100 WHEN 3 THEN 100 ELSE 0 END) AS new_range_capacity,
       (SELECT COUNT(*) FROM PERF_METRIC_DEF e
         WHERE e.deleted = 0
           AND e.base_dim = d.base_dim
           AND e.metric_level = d.metric_level
           AND e.val_slot IS NOT NULL
           AND ((e.metric_level = 1 AND e.val_slot BETWEEN 1 AND 200)
             OR (e.metric_level = 2 AND e.val_slot BETWEEN 201 AND 300)
             OR (e.metric_level = 3 AND e.val_slot BETWEEN 301 AND 400))) AS occupied_in_new_range,
       (SELECT COUNT(*) FROM PERF_METRIC_DEF n
         WHERE n.deleted = 0
           AND n.base_dim = d.base_dim
           AND n.metric_level = d.metric_level
           AND n.val_slot IS NULL) AS null_to_backfill
  FROM PERF_METRIC_DEF d
 WHERE d.val_slot IS NULL AND d.base_dim IS NOT NULL AND d.deleted = 0
 GROUP BY d.base_dim, d.metric_level;
-- 必须保证：每行 occupied_in_new_range + null_to_backfill <= new_range_capacity
-- 否则有 (base_dim, level) 容量溢出，需先扩容（即 MetricSlotService.rangeOf 也要再调）

-- -----------------------------------------------------------------------------
-- 主补齐：用 CTE + ROW_NUMBER 分配 slot
-- MySQL 8.0 语法
-- -----------------------------------------------------------------------------
WITH null_rows AS (
  SELECT id,
         base_dim,
         metric_level,
         (CASE metric_level WHEN 1 THEN 1 WHEN 2 THEN 201 WHEN 3 THEN 301 END) AS range_start,
         ROW_NUMBER() OVER (PARTITION BY base_dim, metric_level ORDER BY metric_code) AS rn
    FROM PERF_METRIC_DEF
   WHERE val_slot IS NULL
     AND base_dim IS NOT NULL
     AND deleted = 0
     AND metric_level IN (1, 2, 3)
),
range_max AS (
  SELECT base_dim,
         metric_level,
         COALESCE(MAX(val_slot), 0) AS cur_max_in_new_range
    FROM PERF_METRIC_DEF
   WHERE val_slot IS NOT NULL
     AND deleted = 0
     AND ((metric_level = 1 AND val_slot BETWEEN 1 AND 200)
       OR (metric_level = 2 AND val_slot BETWEEN 201 AND 300)
       OR (metric_level = 3 AND val_slot BETWEEN 301 AND 400))
   GROUP BY base_dim, metric_level
)
UPDATE PERF_METRIC_DEF t
  JOIN null_rows n ON n.id = t.id
  LEFT JOIN range_max rm ON rm.base_dim = n.base_dim AND rm.metric_level = n.metric_level
   SET t.val_slot = GREATEST(COALESCE(rm.cur_max_in_new_range, 0), n.range_start - 1) + n.rn,
       t.updated_by = 'SQL_BACKFILL_2026_05_19',
       t.updated_time = NOW();

-- -----------------------------------------------------------------------------
-- 校验 1：补齐后 NULL 行清单（应只剩 base_dim IS NULL 的维度无关型）
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS remaining_null_with_base_dim
  FROM PERF_METRIC_DEF
 WHERE val_slot IS NULL
   AND base_dim IS NOT NULL
   AND deleted = 0;
-- 期望: 0

-- -----------------------------------------------------------------------------
-- 校验 2：补齐后是否破坏 uk_base_dim_slot_alive
-- -----------------------------------------------------------------------------
SELECT base_dim, val_slot, COUNT(*) AS conflict_rows
  FROM PERF_METRIC_DEF
 WHERE val_slot IS NOT NULL AND deleted = 0
 GROUP BY base_dim, val_slot
HAVING COUNT(*) > 1;
-- 期望: 0 行
