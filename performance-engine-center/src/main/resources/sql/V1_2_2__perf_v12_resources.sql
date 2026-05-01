-- =====================================================================
-- performance-engine-center V1.2 Phase Q6.4 导出资源激活 + 新增
-- Version: V1_2_2
-- Date: 2026-04-23
-- Task: Q6.4
--
-- 目的：
--   1) 激活 V1_0_4 规划的 2 条导出资源（STATUS 1 → 0）：
--      - P_PERF_EXPORT_KPI  （V1_0_4 URL 为 /api/perf/export/kpi  GET，Q6.4 端点改为 POST）
--      - P_PERF_EXPORT_ALLOC（V1_0_4 URL 为 /api/perf/export/alloc GET，Q6.4 端点改为 POST）
--   2) 新增 3 条 V1.2 Q6.4 细粒度资源：
--      - P_PERF_EXPT_MTR    METRIC 指标宽表导出
--      - P_PERF_EXPT_DTL    DETAIL KPI 明细导出
--      - P_PERF_EXPT_TASK   GET 导出任务状态查询
--
-- 对应 Controller：PerfExportController（5 端点）
--   POST /api/perf/export/kpi      @BizAuth action=EXPORT
--   POST /api/perf/export/metric   @BizAuth action=EXPORT
--   POST /api/perf/export/alloc    @BizAuth action=EXPORT（高危，reasonRequired）
--   POST /api/perf/export/detail   @BizAuth action=EXPORT（高危，reasonRequired）
--   GET  /api/perf/export/task/*   @BizAuth action=READ
-- =====================================================================

-- 1) 激活 V1_0_4 已登记的 2 条（URL 对齐 + STATUS 0）
UPDATE PT_RESOURCE
SET STATUS = 0,
    RESOURCE_URL    = '/api/perf/export/kpi',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = 'KPI 导出',
    REMARK          = 'V1.2 Q6.4 激活',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_EXPORT_KPI';

UPDATE PT_RESOURCE
SET STATUS = 0,
    RESOURCE_URL    = '/api/perf/export/alloc',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = '分配关系导出',
    REMARK          = 'V1.2 Q6.4 激活（高危）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_EXPORT_ALLOC';

-- 2) 新增 3 条资源（V1_0_4 未注册 METRIC / DETAIL / TASK）
--    RESOURCE_ID 遵守 varchar(20) 约束
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('P_PERF_EXPT_MTR',  '/api/perf/export/metric',   'POST', '指标宽表导出',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.2 Q6.4'),
('P_PERF_EXPT_DTL',  '/api/perf/export/detail',   'POST', 'KPI 明细导出',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.2 Q6.4 高危'),
('P_PERF_EXPT_TASK', '/api/perf/export/task/*',   'GET',  '导出任务状态',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'V1.2 Q6.4')
ON DUPLICATE KEY UPDATE
  STATUS      = VALUES(STATUS),
  UPDATE_TIME = NOW(),
  UPDATE_USER = 'seed',
  REMARK      = VALUES(REMARK);
