-- ============================================================================
-- 2026-06-15 PERF_ALLOC_ADJUST_APPLY 排序列索引（业绩分配查询/业绩调整 列表提速）
--
-- 【背景】业绩分配查询页「业绩调整」Tab 列表（AllocAdjustQueryServiceImpl.pageList）
--   恒按 created_time DESC 排序 + DB 分页（selectPage）。该表现有索引为
--   created_by / cust_id / status / apply_no，唯独缺 created_time，故 ORDER BY 走 filesort。
--   （列表本身无 N+1：DB COUNT+LIMIT 分页 + 申请人姓名一次批量解析。）
--
-- 【方案】补排序列索引，消除 ORDER BY created_time DESC 的 filesort：
--   idx_alloc_apply_created_time (created_time)
--   - 默认"最新优先"列表 + scope=ALL：可走索引倒序 + LIMIT，免 filesort。
--   - 与现有 created_by / status 等值过滤共存（优化器按选择性择优）。
--   说明：当前该表仅数十行，优化器会直接全表扫、暂不用本索引；本索引为数据量
--   增长（数千行+）后的前瞻准备，写入维护成本极低（单列、小表）。
--
-- 【可选复合索引】按高频「过滤/数据范围 + 排序」维度，需要时再单独追加（本脚本不建）：
--   (owner_org_id, created_time)  -- ORG / ORG_SUBTREE 数据范围用户
--   (status, created_time)        -- 按状态筛 + 排序
--   (created_by, created_time)    -- 按申请人 / SELF 数据范围 + 排序
--
-- 【幂等】MySQL 无 CREATE INDEX IF NOT EXISTS，用 INFORMATION_SCHEMA 预检 + 动态 SQL；可重复执行。
-- 【双库】yiti + onepl 均执行。
-- ============================================================================

-- ===================== yiti =====================
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'yiti' AND TABLE_NAME = 'PERF_ALLOC_ADJUST_APPLY'
                  AND INDEX_NAME = 'idx_alloc_apply_created_time');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_alloc_apply_created_time ON yiti.PERF_ALLOC_ADJUST_APPLY (created_time)',
  'SELECT ''[yiti] idx_alloc_apply_created_time 已存在，跳过'' AS msg');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
ANALYZE TABLE yiti.PERF_ALLOC_ADJUST_APPLY;

-- ===================== onepl =====================
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'onepl' AND TABLE_NAME = 'PERF_ALLOC_ADJUST_APPLY'
                  AND INDEX_NAME = 'idx_alloc_apply_created_time');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_alloc_apply_created_time ON onepl.PERF_ALLOC_ADJUST_APPLY (created_time)',
  'SELECT ''[onepl] idx_alloc_apply_created_time 已存在，跳过'' AS msg');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
ANALYZE TABLE onepl.PERF_ALLOC_ADJUST_APPLY;

-- ===================== 验证（按需手工执行） =====================
-- 数据量增长后 EXPLAIN 应命中 idx_alloc_apply_created_time、无 Using filesort：
--   EXPLAIN SELECT * FROM PERF_ALLOC_ADJUST_APPLY WHERE status<>'DRAFT'
--    ORDER BY created_time DESC LIMIT 20;
-- 回滚：DROP INDEX idx_alloc_apply_created_time ON PERF_ALLOC_ADJUST_APPLY;
-- ============================================================================
