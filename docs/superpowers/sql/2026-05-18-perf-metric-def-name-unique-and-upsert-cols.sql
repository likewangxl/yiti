-- =========================================================================
-- 2026-05-18 V1.11：指标定义按名称 upsert 改造
--   1) PERF_METRIC_DEF 现网重名清理（保留 created_time 最早 + id 字典序最小，其余软删）
--   2) PERF_METRIC_DEF 加 metric_name 唯一索引（按 deleted=0 函数索引）
--   3) PERF_IMPORT_BATCH 加 updated_rows 列
--
-- 部署目标库：onepl（生产）/ onepl_test_bootstrap（测试）/ yiti（开发，如使用）
-- 注意：项目已废弃 Flyway，本脚本由 DBA 手工或 CI 直接执行
-- =========================================================================

-- -------------------------------------------------------------------------
-- 步骤 1：现网 metric_name 重复行清理
-- -------------------------------------------------------------------------

-- 1.1 预检：先看哪些行将被影响（DBA 跑这条评估）
SELECT metric_name, COUNT(*) AS cnt, GROUP_CONCAT(id) AS ids,
       GROUP_CONCAT(metric_code) AS codes
  FROM PERF_METRIC_DEF
 WHERE deleted = 0
 GROUP BY metric_name
HAVING COUNT(*) > 1;

-- 1.2 清理重复（保留 created_time 最早 + 同 created_time 时 id 字典序最小）
--      规则：业务侧明确"如有重复，删除重复记录"，最早创建者优先以保护既有引用关系
UPDATE PERF_METRIC_DEF d
  JOIN (
        SELECT t.metric_name,
               (SELECT id FROM PERF_METRIC_DEF
                 WHERE metric_name = t.metric_name AND deleted = 0
                 ORDER BY created_time ASC, id ASC
                 LIMIT 1) AS keep_id
          FROM PERF_METRIC_DEF t
         WHERE t.deleted = 0
         GROUP BY t.metric_name
        HAVING COUNT(*) > 1
       ) dup ON d.metric_name = dup.metric_name
   SET d.deleted = 1,
       d.updated_time = NOW(),
       d.updated_by = 'V1.11_DEDUP'
 WHERE d.deleted = 0
   AND d.id <> dup.keep_id;

-- -------------------------------------------------------------------------
-- 步骤 2：加 metric_name 唯一索引（按 "未软删除行唯一" 语义，对齐已有 uk_base_dim_slot_alive 函数索引风格）
-- -------------------------------------------------------------------------
ALTER TABLE PERF_METRIC_DEF
  ADD UNIQUE KEY `uk_metric_name_alive` ((IF(deleted=0, metric_name, NULL)));

-- -------------------------------------------------------------------------
-- 步骤 3：PERF_IMPORT_BATCH 加 updated_rows 列（METRIC_DEF 导入专用计数，其他类型恒 0）
-- -------------------------------------------------------------------------
ALTER TABLE PERF_IMPORT_BATCH
  ADD COLUMN `updated_rows` INT NOT NULL DEFAULT 0 COMMENT '更新行数（V1.11：仅 METRIC_DEF 导入使用，其他类型恒 0）'
  AFTER `error_rows`;

-- -------------------------------------------------------------------------
-- 回滚 SQL（保留供应急使用）
-- -------------------------------------------------------------------------
-- ALTER TABLE PERF_METRIC_DEF DROP KEY `uk_metric_name_alive`;
-- ALTER TABLE PERF_IMPORT_BATCH DROP COLUMN `updated_rows`;
-- -- 重复行软删除可手工 UPDATE deleted=0 回滚单行，但需 DBA 介入
