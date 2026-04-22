-- =====================================================================
-- performance-engine-center V1.0 Resource Cleanup (V1_0_4)
-- Version: V1_0_4
-- Date: 2026-04-22
--
-- 目的：将 V1.1/V1.2 规划但 V1.0 尚未实现的 10 条端点注册为禁用状态，
--   防止在 V1.0 对外误授权这些未实现的接口。
--
-- STATUS 语义（int）：0=启用，1=不启用（禁用）
--
-- V1.1/V1.2 上线时通过对应版本脚本将 STATUS 更新为 0（激活）。
-- =====================================================================

-- 注册 10 条 V1.1/V1.2 规划资源，STATUS=1（禁用），避免 V1.0 误授权
-- RESOURCE_ID 遵守 varchar(20) 约束：缩写规则
--   P_PERF_METRIC_EXEC       ← 指标执行（V1.1  /api/perf/metrics/*/execute）
--   P_PERF_METRIC_TRIAL      ← 指标试算（V1.1  /api/perf/metrics/*/trial-run）
--   P_PERF_IMPORT_UPLOAD     ← 数据导入（V1.1  /api/perf/import/upload）
--   P_PERF_ALLOC_ADJ_ADD     ← 分配调整申请（V1.2 /api/perf/alloc-adjust/apply）
--   P_PERF_KPI_TRIGGER       ← KPI 计算触发（V1.1 /api/perf/kpi-calc/trigger）
--   P_PERF_KPI_RECALC        ← KPI 回算（V1.2  /api/perf/kpi-calc/recalc）
--   P_PERF_DTASK_STATUS      ← 外部任务状态上报（V1.1 /api/perf/data-task/status）
--   P_PERF_SC_ROLLBACK       ← 版本回滚（V1.2  /api/perf/sys-control/rollback）
--   P_PERF_EXPORT_KPI        ← KPI 结果导出（V1.2 /api/perf/export/kpi）
--   P_PERF_EXPORT_ALLOC      ← 分配结果导出（V1.2 /api/perf/export/alloc）
INSERT INTO pt_resource (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
-- V1.1 规划功能（STATUS=1 禁用）
('P_PERF_METRIC_EXEC',   '/api/perf/metrics/*/execute',       'POST',  '指标执行（V1.1）',    NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.1 规划，V1.0 禁用'),
('P_PERF_METRIC_TRIAL',  '/api/perf/metrics/*/trial-run',     'POST',  '指标试算（V1.1）',    NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.1 规划，V1.0 禁用'),
('P_PERF_IMPORT_UPLOAD', '/api/perf/import/upload',           'POST',  '数据导入（V1.1）',    NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.1 规划，V1.0 禁用'),
('P_PERF_KPI_TRIGGER',   '/api/perf/kpi-calc/trigger',        'POST',  'KPI计算触发（V1.1）', NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.1 规划，V1.0 禁用'),
('P_PERF_DTASK_STATUS',  '/api/perf/data-task/status',        'POST',  '外部上报（V1.1）',    NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.1 规划，V1.0 禁用'),
-- V1.2 规划功能（STATUS=1 禁用）
('P_PERF_ALLOC_ADJ_ADD', '/api/perf/alloc-adjust/apply',      'POST',  '分配调整申请（V1.2）',NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.2 规划，V1.0 禁用'),
('P_PERF_KPI_RECALC',    '/api/perf/kpi-calc/recalc',         'POST',  'KPI回算（V1.2）',     NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.2 规划，V1.0 禁用'),
('P_PERF_SC_ROLLBACK',   '/api/perf/sys-control/rollback',    'POST',  '版本回滚（V1.2）',    NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.2 规划，V1.0 禁用'),
('P_PERF_EXPORT_KPI',    '/api/perf/export/kpi',              'GET',   'KPI导出（V1.2）',     NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.2 规划，V1.0 禁用'),
('P_PERF_EXPORT_ALLOC',  '/api/perf/export/alloc',            'GET',   '分配导出（V1.2）',    NULL, 0, 0, '0', NULL, 1, 'PERF', NOW(), 'seed', 'V1.2 规划，V1.0 禁用')
ON DUPLICATE KEY UPDATE
  STATUS      = VALUES(STATUS),
  UPDATE_TIME = NOW(),
  UPDATE_USER = 'seed',
  REMARK      = VALUES(REMARK);

-- =====================================================================
-- 追加：同步字典项
-- 若 sys_dict_item 中存在 PERF_METRIC_STATUS 字典的 DRAFT/PUBLISHED 条目，
-- 将其标记为禁用（V1.0 代码使用 ACTIVE/DISABLED，文档已统一，字典项同步）
-- 零行受影响不会报错，可安全执行
-- =====================================================================
UPDATE sys_dict_item
SET status = 'PENDING'
WHERE dict_type = 'PERF_METRIC_STATUS'
  AND item_code IN ('DRAFT', 'PUBLISHED');
