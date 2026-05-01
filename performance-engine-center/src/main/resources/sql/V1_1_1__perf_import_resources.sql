-- =====================================================================
-- performance-engine-center V1.1 Data Import Resources (V1_1_1)
-- Version: V1_1_1
-- Date: 2026-04-23
-- Task: P5.5
--
-- 目的：
--   1) 激活 V1_0_4 规划的 P_PERF_IMPORT_UPLOAD（STATUS 1 → 0 启用）
--   2) 新增 4 条 V1.1 导入通道的细粒度资源：
--      batchGet / batchErrors / batchRetry / batchDelete
--   3) 更新资源 URL 从 /api/perf/import/upload 对齐 Controller 实际路径
--
-- 对应 Controller：PerfImportController（5 端点）
-- 对应 @BizAuth action：IMPORT / READ / READ / EXECUTE / DELETE
-- =====================================================================

-- 激活 P_PERF_IMPORT_UPLOAD（URL 已对齐，仅 STATUS 从 1 → 0）
UPDATE PT_RESOURCE
SET STATUS = 0,
    MENU_NAME = '数据导入-上传',
    REMARK    = 'V1.1 P5.5 激活',
    UPDATE_TIME = NOW(),
    UPDATE_USER = 'seed'
WHERE RESOURCE_ID = 'P_PERF_IMPORT_UPLOAD';

-- 新增 4 条 V1.1 导入批次管理资源
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('P_PERF_IMP_B_GET',  '/api/perf/import/batches/*',         'GET',    '数据导入-批次详情', NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.1 P5.5'),
('P_PERF_IMP_B_ERR',  '/api/perf/import/batches/*/errors',  'GET',    '数据导入-错误明细', NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.1 P5.5'),
('P_PERF_IMP_B_RT',   '/api/perf/import/batches/*/retry',   'POST',   '数据导入-重试批次', NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.1 P5.5'),
('P_PERF_IMP_B_DEL',  '/api/perf/import/batches/*',         'DELETE', '数据导入-删除批次', NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.1 P5.5 高危')
ON DUPLICATE KEY UPDATE
  STATUS      = VALUES(STATUS),
  UPDATE_TIME = NOW(),
  UPDATE_USER = 'seed',
  REMARK      = VALUES(REMARK);
