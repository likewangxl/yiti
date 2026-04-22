-- =====================================================================
-- V1_0_4 紧急回滚脚本（undo）
-- 撤销 V1_0_4 的 PT_RESOURCE 注册与 sys_dict_item 字典项修改
--
-- 背景：V1_0_4 是 Phase E 交付（注册 10 条 V1.1/V1.2 规划资源并禁用 +
--   metric_def.status 字典项同步）。本脚本在 Phase B 预先创建，Phase E
--   落地后通过 ID 对齐修订（2026-04-22）。
--
-- 警告：执行前确认 V1_0_4 已成功应用，否则此脚本无效果。
-- 执行顺序：与正向脚本完全对称——
--   1. DELETE 正向 INSERT 的 10 条 V1.1/V1.2 规划资源（含缩写 ID）
--   2. 还原 sys_dict_item PERF_METRIC_STATUS 的 DRAFT/PUBLISHED 项
-- =====================================================================

-- 还原 PT_RESOURCE：DELETE 正向脚本 INSERT 的 10 条 V1.1/V1.2 规划资源
-- 注意：ID 与 V1_0_4__perf_resource_cleanup.sql 正向脚本完全一致（varchar(20) 缩写规则）
DELETE FROM pt_resource WHERE RESOURCE_ID IN (
  -- V1.1 规划
  'P_PERF_METRIC_EXEC', 'P_PERF_METRIC_TRIAL', 'P_PERF_IMPORT_UPLOAD',
  'P_PERF_KPI_TRIGGER', 'P_PERF_DTASK_STATUS',
  -- V1.2 规划
  'P_PERF_ALLOC_ADJ_ADD', 'P_PERF_KPI_RECALC', 'P_PERF_SC_ROLLBACK',
  'P_PERF_EXPORT_KPI', 'P_PERF_EXPORT_ALLOC'
);

-- 还原 sys_dict_item：撤销 PERF_METRIC_STATUS 的 DRAFT/PUBLISHED 项的 PENDING 标记
-- 零行受影响不会报错
UPDATE sys_dict_item
SET status = 'ACTIVE'
WHERE dict_type = 'PERF_METRIC_STATUS'
  AND item_code IN ('DRAFT', 'PUBLISHED');
