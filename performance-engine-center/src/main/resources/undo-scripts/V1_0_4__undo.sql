-- =====================================================================
-- V1_0_4 紧急回滚脚本（undo）
-- 撤销 V1_0_4 的 PT_RESOURCE PENDING 标记与字典项修改
--
-- 背景：V1_0_4 是 Phase E 交付（PT_RESOURCE 冗余清理 + metric_def.status 统一）。
--   本脚本在 Phase B 阶段预先创建，供 Phase E 交付后使用。
--
-- 警告：执行前确认 V1_0_4 已成功应用，否则此脚本无效果。
-- =====================================================================

-- 还原 PT_RESOURCE 状态：将 V1_0_4 标记为 PENDING 的资源恢复为 ACTIVE
UPDATE pt_resource SET STATUS = 'ACTIVE' WHERE RESOURCE_ID IN (
  'P_PERF_METRIC_EXECUTE', 'P_PERF_METRIC_TRIAL_RUN', 'P_PERF_IMPORT_UPLOAD',
  'P_PERF_ALLOC_ADJUST_CREATE', 'P_PERF_KPI_TRIGGER', 'P_PERF_KPI_RECALC',
  'P_PERF_DATA_TASK_STATUS', 'P_PERF_SYS_CONTROL_ROLLBACK',
  'P_PERF_EXPORT_KPI', 'P_PERF_EXPORT_ALLOC'
);

-- 还原字典项状态
UPDATE sys_dict_item SET status = 'ACTIVE'
 WHERE dict_type = 'PERF_METRIC_STATUS' AND item_code IN ('DRAFT', 'PUBLISHED');
