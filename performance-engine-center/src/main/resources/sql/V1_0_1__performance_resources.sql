-- =====================================================================
-- performance-engine-center V1.0 Resources Registration (v1.2)
-- Version: V1_0_1
-- Date: 2026-04-15
--
-- Registers:
--   - 35 PT_RESOURCE entries (prefix P_PERF_*, SYS_CODE='PERF')
--   - Role assignments (R_ADMIN + R_BACK_TECH) for all 35 resources
--   - 2 BIZ_SCOPE entries (R_ADMIN + R_BACK_TECH with BizType PERF_CONFIG, DataScope ALL)
--
-- v1.2 note: PT_RESOURCE actual columns are
--   RESOURCE_ID / RESOURCE_URL / RESOURCE_METHOD / MENU_NAME / SYS_CODE / STATUS / ...
--   (no BIZ_TYPE, ACTION, MODULE fields — BizType lives in pt_role_biz_scope).
--   SYS_CODE='PERF' identifies the performance module.
--   Uses common-security BizType.PERF_CONFIG (coarse grained);
--   fine-grained control is by RESOURCE_ID (P_PERF_*).
-- =====================================================================

-- 1. Register 35 REST endpoints in pt_resource
INSERT INTO pt_resource (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
-- MetricDef (10)
('P_PERF_METRIC_LIST', '/api/perf/metrics',                      'GET',    '指标列表',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_GET',  '/api/perf/metrics/*',                    'GET',    '指标详情',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_ADD',  '/api/perf/metrics',                      'POST',   '新增指标',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_UPD',  '/api/perf/metrics/*',                    'PUT',    '编辑指标',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_DEL',  '/api/perf/metrics/*',                    'DELETE', '删除指标',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_STAT', '/api/perf/metrics/*/status',             'PUT',    '指标状态流转',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_REFS', '/api/perf/metrics/*/refs',               'GET',    '查指标上游依赖', NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_RBY',  '/api/perf/metrics/*/ref-by',             'GET',    '查谁引用了我',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_SLOT', '/api/perf/metrics/val-slots',            'GET',    '槽位占用查询',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_SREL', '/api/perf/metrics/*/slot/release',       'POST',   '强制释放槽位',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- KpiScheme (9)
('P_PERF_KPI_LIST',    '/api/perf/kpi-schemes',                  'GET',    'KPI方案列表',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_GET',     '/api/perf/kpi-schemes/*',                'GET',    'KPI方案详情',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_ADD',     '/api/perf/kpi-schemes',                  'POST',   '新增KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_UPD',     '/api/perf/kpi-schemes/*',                'PUT',    '编辑KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_DEL',     '/api/perf/kpi-schemes/*',                'DELETE', '删除KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_PUB',     '/api/perf/kpi-schemes/*/publish',        'POST',   '发布KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_IADD',    '/api/perf/kpi-schemes/*/items',          'POST',   '添加指标项',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_IUPD',    '/api/perf/kpi-schemes/*/items/*',        'PUT',    '编辑指标项',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_IDEL',    '/api/perf/kpi-schemes/*/items/*',        'DELETE', '删除指标项',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- TargetPlan (4)
('P_PERF_TGT_P_LIST',  '/api/perf/target-plans',                 'GET',    '目标方案列表',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_P_GET',   '/api/perf/target-plans/*',               'GET',    '目标方案详情',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_P_ADD',   '/api/perf/target-plans',                 'POST',   '新增目标方案',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_P_UPD',   '/api/perf/target-plans/*',               'PUT',    '编辑目标方案',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- TargetValue (3)
('P_PERF_TGT_V_LIST',  '/api/perf/target-values',                'GET',    '目标值查询',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_V_ADD',   '/api/perf/target-values',                'POST',   '目标值upsert',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_V_BAT',   '/api/perf/target-values/batch',          'POST',   '目标值批量',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- Alloc (3)
('P_PERF_ALLOC_CUR',   '/api/perf/alloc-relations',              'GET',    '当前分配关系',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_ALLOC_HIS',   '/api/perf/alloc-relations/history',      'GET',    '历史分配关系',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_ALLOC_SUM',   '/api/perf/alloc-relations/summary',      'GET',    '分配关系汇总',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- RunTask (2)
('P_PERF_RT_LIST',     '/api/perf/run-tasks',                    'GET',    '任务日志列表',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_RT_GET',      '/api/perf/run-tasks/*',                  'GET',    '任务日志详情',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- SysControl (4)
('P_PERF_SC_GET',      '/api/perf/sys-control',                  'GET',    '版本查询',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_SC_HIS',      '/api/perf/sys-control/history',          'GET',    '版本历史',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_SC_INIT',     '/api/perf/sys-control/init',             'POST',   '版本初始化',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_SC_SW',       '/api/perf/sys-control/switch-version',   'POST',   '版本切换',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0')
ON DUPLICATE KEY UPDATE
  RESOURCE_URL    = VALUES(RESOURCE_URL),
  RESOURCE_METHOD = VALUES(RESOURCE_METHOD),
  MENU_NAME       = VALUES(MENU_NAME),
  STATUS          = VALUES(STATUS),
  UPDATE_TIME     = NOW(),
  UPDATE_USER     = 'seed',
  REMARK          = VALUES(REMARK);

-- 2. Grant all 35 PERF resources to R_ADMIN and R_BACK_TECH
--    Use INSERT IGNORE to achieve idempotency (no need for ON DUPLICATE KEY UPDATE)
INSERT IGNORE INTO pt_role_resource (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT(r.ROLE_ID, '_', res.RESOURCE_ID) AS ID, r.ROLE_ID, res.RESOURCE_ID, 'PERF', NOW()
FROM (SELECT 'R_ADMIN' AS ROLE_ID UNION ALL SELECT 'R_BACK_TECH') r
CROSS JOIN pt_resource res
WHERE res.SYS_CODE = 'PERF' AND res.RESOURCE_ID LIKE 'P_PERF_%';

-- 3. BizType data scope configuration in pt_role_biz_scope
--    v1.2: using common-security BizType.PERF_CONFIG (single coarse-grained type)
--    2 entries: R_ADMIN and R_BACK_TECH both get DataScope=ALL for PERF_CONFIG
INSERT INTO pt_role_biz_scope (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
(REPLACE(UUID(), '-', ''), 'R_ADMIN',     'PERF_CONFIG', 'ALL', 0, NOW(), 'seed', 'perf v1.0 - full access'),
(REPLACE(UUID(), '-', ''), 'R_BACK_TECH', 'PERF_CONFIG', 'ALL', 0, NOW(), 'seed', 'perf v1.0 - full access')
ON DUPLICATE KEY UPDATE
  DATA_SCOPE  = VALUES(DATA_SCOPE),
  UPDATE_TIME = NOW(),
  UPDATE_USER = 'seed',
  REMARK      = VALUES(REMARK);
