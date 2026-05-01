-- =====================================================================
-- report-analytics-center M3 阶段 PT_RESOURCE 注册（Task M3.4.1）
-- Version: V1_0_3
-- Date: 2026-04-25
--
-- 注册 6 个 M3 汇总报表 REST 端点（C 章 C.2-C.4 view + export）：
--   - C.2 GET  /api/reports/touch-task-summary               → R_RPT_SUM_TOUCH_VW
--   - C.2 POST /api/reports/touch-task-summary/export        → R_RPT_SUM_TOUCH_EXP
--   - C.3 GET  /api/reports/perf-summary                     → R_RPT_SUM_PERF_VW
--   - C.3 POST /api/reports/perf-summary/export              → R_RPT_SUM_PERF_EXP
--   - C.4 GET  /api/reports/customer-pool-summary            → R_RPT_SUM_CUST_VW
--   - C.4 POST /api/reports/customer-pool-summary/export     → R_RPT_SUM_CUST_EXP
--
-- v1.0 note：
--   - RESOURCE_ID < 20 字符（pt_resource 列约束），全部 R_RPT_SUM_* 形式
--   - @BizAuth: BizType.REPORT, BizAction.READ（view）/ BizAction.EXPORT（export）
--   - 角色绑定沿用 M1.6/M2.4 同款策略：R_ADMIN + R_BACK_TECH 默认全开
--   - BizScope 已在 M1.6 V1_0_1 注册过 R_ADMIN/R_BACK_TECH 的 REPORT/ALL，本脚本不再重复
--
-- 幂等设计：INSERT ... ON DUPLICATE KEY UPDATE / NOT EXISTS 兜底，可重复运行
-- =====================================================================

-- 1. 注册 6 个 REST endpoint
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('R_RPT_SUM_TOUCH_VW',  '/api/reports/touch-task-summary',          'GET',  '触达汇总查看',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M3.1 C.2'),
('R_RPT_SUM_TOUCH_EXP', '/api/reports/touch-task-summary/export',   'POST', '触达汇总导出',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M3.1 C.2'),
('R_RPT_SUM_PERF_VW',   '/api/reports/perf-summary',                'GET',  '绩效汇总查看',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M3.2 C.3'),
('R_RPT_SUM_PERF_EXP',  '/api/reports/perf-summary/export',         'POST', '绩效汇总导出',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M3.2 C.3'),
('R_RPT_SUM_CUST_VW',   '/api/reports/customer-pool-summary',       'GET',  '客户池统计查看', NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M3.3 C.4'),
('R_RPT_SUM_CUST_EXP',  '/api/reports/customer-pool-summary/export','POST', '客户池统计导出', NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M3.3 C.4')
ON DUPLICATE KEY UPDATE REMARK = VALUES(REMARK), UPDATE_TIME = NOW();

-- 2. 角色-资源绑定：R_ADMIN + R_BACK_TECH 默认获得全部 6 条
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'RPT', NOW()
  FROM PT_ROLE r
  CROSS JOIN (
        SELECT 'R_RPT_SUM_TOUCH_VW'  AS RESOURCE_ID UNION ALL
        SELECT 'R_RPT_SUM_TOUCH_EXP' UNION ALL
        SELECT 'R_RPT_SUM_PERF_VW'   UNION ALL
        SELECT 'R_RPT_SUM_PERF_EXP'  UNION ALL
        SELECT 'R_RPT_SUM_CUST_VW'   UNION ALL
        SELECT 'R_RPT_SUM_CUST_EXP'
  ) res
 WHERE r.ROLE_CODE IN ('R_ADMIN', 'R_BACK_TECH')
   AND NOT EXISTS (
       SELECT 1 FROM PT_ROLE_RESOURCE prr
        WHERE prr.ROLE_ID = r.ROLE_ID AND prr.RESOURCE_ID = res.RESOURCE_ID
   );

-- 注：R_PRESIDENT / R_HR / R_RM 等业务角色对汇总查看/导出资源的绑定，
-- 由共享 seed-v1.sql 业务策略统一维护，不在本脚本内联（避免与其他模块冲突）。
