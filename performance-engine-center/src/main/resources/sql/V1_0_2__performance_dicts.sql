-- =====================================================================
-- performance-engine-center V1.0 Dictionary Seed (v1.2)
-- Version: V1_0_2
-- Date: 2026-04-15
--
-- Tables: sys_dict (type metadata) + sys_dict_item (items)
-- Registers: 10 PERF_* dict types + 39 items
-- =====================================================================

-- 1. Dict type metadata in sys_dict
INSERT INTO SYS_DICT (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by, created_time)
VALUES
('PERF_DICT_001', 'PERF_BASE_DIM',          'PERF_BASE_DIM',          '指标维度',       'EMP/ORG/CUST',             1,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_002', 'PERF_METRIC_LEVEL',      'PERF_METRIC_LEVEL',      '指标级次',       '1/2/3',                    2,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_003', 'PERF_METRIC_CALC_LOGIC', 'PERF_METRIC_CALC_LOGIC', '计算逻辑类型',   'SQL/PROC/EXPR/SUMMARY',    3,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_004', 'PERF_CALC_FREQ',         'PERF_CALC_FREQ',         '计算频率',       'DAY/MONTH/QUARTER/YEAR',   4,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_005', 'PERF_CYCLE_TYPE',        'PERF_CYCLE_TYPE',        '考核周期类型',   'MONTHLY/QUARTERLY/YEARLY', 5,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_006', 'PERF_TASK_TYPE',         'PERF_TASK_TYPE',         '任务类型',       'METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC/DATA_IMPORT', 6, 'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_007', 'PERF_TASK_STATUS',       'PERF_TASK_STATUS',       '任务状态',       'PENDING/RUNNING/SUCCESS/FAILED/PARTIAL/CANCELLED', 7, 'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_008', 'PERF_METRIC_STATUS',     'PERF_METRIC_STATUS',     '指标状态',       'DRAFT/PUBLISHED/DISABLED/ACTIVE', 8, 'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_009', 'PERF_APPLY_STATUS',      'PERF_APPLY_STATUS',      '申请状态',       '(V1.2 use)',               9,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_010', 'PERF_ALLOC_DIM',         'PERF_ALLOC_DIM',         '分配维度',       'RULE/ACCOUNT',             10, 'ACTIVE', 'perf v1.0', 'seed', NOW())
ON DUPLICATE KEY UPDATE
  dict_label = VALUES(dict_label),
  dict_value = VALUES(dict_value),
  status = VALUES(status),
  updated_time = NOW();

-- 2. Dict items in sys_dict_item (39 entries)
-- Uses deterministic IDs so ON DUPLICATE KEY UPDATE works for idempotent reruns.
INSERT INTO SYS_DICT_ITEM (id, dict_type, item_code, item_label, item_value, sort_order, status, remark, created_by, created_time)
VALUES
-- PERF_BASE_DIM (3)
('PI_BD_01', 'PERF_BASE_DIM',          'EMP',          '员工',        'EMP',          1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_BD_02', 'PERF_BASE_DIM',          'ORG',          '机构',        'ORG',          2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_BD_03', 'PERF_BASE_DIM',          'CUST',         '客户',        'CUST',         3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_METRIC_LEVEL (3)
('PI_ML_01', 'PERF_METRIC_LEVEL',      '1',            '一级',        '1',            1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_ML_02', 'PERF_METRIC_LEVEL',      '2',            '二级',        '2',            2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_ML_03', 'PERF_METRIC_LEVEL',      '3',            '三级',        '3',            3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_METRIC_CALC_LOGIC (4)
('PI_CL_01', 'PERF_METRIC_CALC_LOGIC', 'SQL',          'SQL查询',     'SQL',          1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CL_02', 'PERF_METRIC_CALC_LOGIC', 'PROC',         '存储过程',    'PROC',         2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CL_03', 'PERF_METRIC_CALC_LOGIC', 'EXPR',         '表达式',      'EXPR',         3,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CL_04', 'PERF_METRIC_CALC_LOGIC', 'SUMMARY',      '汇总规则',    'SUMMARY',      4,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_CALC_FREQ (4)
('PI_CF_01', 'PERF_CALC_FREQ',         'DAY',          '日',          'DAY',          1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CF_02', 'PERF_CALC_FREQ',         'MONTH',        '月',          'MONTH',        2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CF_03', 'PERF_CALC_FREQ',         'QUARTER',      '季',          'QUARTER',      3,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CF_04', 'PERF_CALC_FREQ',         'YEAR',         '年',          'YEAR',         4,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_CYCLE_TYPE (3)
('PI_CT_01', 'PERF_CYCLE_TYPE',        'MONTHLY',      '月度',        'MONTHLY',      1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CT_02', 'PERF_CYCLE_TYPE',        'QUARTERLY',    '季度',        'QUARTERLY',    2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_CT_03', 'PERF_CYCLE_TYPE',        'YEARLY',       '年度',        'YEARLY',       3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_METRIC_STATUS (4)
('PI_MS_01', 'PERF_METRIC_STATUS',     'DRAFT',        '草稿',        'DRAFT',        1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_MS_02', 'PERF_METRIC_STATUS',     'PUBLISHED',    '已发布',      'PUBLISHED',    2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_MS_03', 'PERF_METRIC_STATUS',     'DISABLED',     '已停用',      'DISABLED',     3,  'ACTIVE', NULL, 'seed', NOW()),
('PI_MS_04', 'PERF_METRIC_STATUS',     'ACTIVE',       '激活中',      'ACTIVE',       4,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_TASK_TYPE (5)
('PI_TT_01', 'PERF_TASK_TYPE',         'METRIC_TRIAL', '指标试运行',  'METRIC_TRIAL', 1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TT_02', 'PERF_TASK_TYPE',         'METRIC_RUN',   '指标计算',    'METRIC_RUN',   2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TT_03', 'PERF_TASK_TYPE',         'KPI_RUN',      'KPI计算',     'KPI_RUN',      3,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TT_04', 'PERF_TASK_TYPE',         'RECALC',       '历史回算',    'RECALC',       4,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TT_05', 'PERF_TASK_TYPE',         'DATA_IMPORT',  '数据导入',    'DATA_IMPORT',  5,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_TASK_STATUS (6)
('PI_TS_01', 'PERF_TASK_STATUS',       'PENDING',      '待执行',      'PENDING',      1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TS_02', 'PERF_TASK_STATUS',       'RUNNING',      '执行中',      'RUNNING',      2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TS_03', 'PERF_TASK_STATUS',       'SUCCESS',      '成功',        'SUCCESS',      3,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TS_04', 'PERF_TASK_STATUS',       'FAILED',       '失败',        'FAILED',       4,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TS_05', 'PERF_TASK_STATUS',       'PARTIAL',      '部分成功',    'PARTIAL',      5,  'ACTIVE', NULL, 'seed', NOW()),
('PI_TS_06', 'PERF_TASK_STATUS',       'CANCELLED',    '已取消',      'CANCELLED',    6,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_APPLY_STATUS (5) - V1.2 use
('PI_AS_01', 'PERF_APPLY_STATUS',      'DRAFT',        '草稿',        'DRAFT',        1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_AS_02', 'PERF_APPLY_STATUS',      'IN_APPROVAL',  '审批中',      'IN_APPROVAL',  2,  'ACTIVE', NULL, 'seed', NOW()),
('PI_AS_03', 'PERF_APPLY_STATUS',      'APPROVED',     '审批通过',    'APPROVED',     3,  'ACTIVE', NULL, 'seed', NOW()),
('PI_AS_04', 'PERF_APPLY_STATUS',      'REJECTED',     '已驳回',      'REJECTED',     4,  'ACTIVE', NULL, 'seed', NOW()),
('PI_AS_05', 'PERF_APPLY_STATUS',      'CANCELLED',    '已撤回',      'CANCELLED',    5,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_ALLOC_DIM (2)
('PI_AD_01', 'PERF_ALLOC_DIM',         'RULE',         '规则维度',    'RULE',         1,  'ACTIVE', NULL, 'seed', NOW()),
('PI_AD_02', 'PERF_ALLOC_DIM',         'ACCOUNT',      '账号维度',    'ACCOUNT',      2,  'ACTIVE', NULL, 'seed', NOW())
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  item_value = VALUES(item_value),
  status = VALUES(status),
  updated_time = NOW();
