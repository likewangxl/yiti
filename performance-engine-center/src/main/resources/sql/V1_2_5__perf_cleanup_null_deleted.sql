-- =====================================================================
-- performance-engine-center V1.3 Phase R0.1 历史 NULL deleted 清理
-- Version: V1_2_5
-- Date: 2026-04-24
-- Task: R0.1
--
-- 背景：V1.0 MetricDefService.create 未显式设置 deleted=0（默认 Service
--   层未初始化），可能存在 `deleted IS NULL` 的历史孤儿行。这些行在
--   `selectByMetricCode(WHERE deleted=0)` 查询下不可见，造成"看得见但查不到"
--   的数据一致性坑。
--
--   Q8.5b 已从 Service 层补齐 `entity.setDeleted(0)` 默认值（新增行不再有 NULL），
--   但历史 NULL 数据未在 V1.2 Q8 清理；本脚本补齐。
--
--   V1_0_3 脚本已有同样的 UPDATE（作为补 deleted 字段后的数据初始化），
--   V1.1/V1.2 期间 Service bug 可能再次产生 NULL 行，故本脚本幂等重跑
--   确保清理彻底。
--
-- 幂等性：仅影响 deleted IS NULL 的行；已为 0 或 1 的行不动。
-- 生产安全：单条 UPDATE，耗时与 NULL 行数线性相关，可控。
--
-- 【生产运维核对清单】
--   1) 预检查受影响行数：
--      SELECT COUNT(*) FROM PERF_METRIC_DEF WHERE deleted IS NULL;
--   2) 若结果过大（> 1 万），建议分批 UPDATE 以避免长事务锁表
--      （本脚本未分批，默认数据量 < 1 万场景）
--   3) 本脚本生效后，任何再次出现 NULL 行均表明 Service 层或直接 SQL
--      绕过 Q8.5b 修复；运维需排查应用调用点
-- =====================================================================

UPDATE PERF_METRIC_DEF
   SET deleted = 0
 WHERE deleted IS NULL;
