-- =====================================================================
-- report-analytics-center M2 阶段 PT_RESOURCE 注册（Task M2.4.1）
-- Version: V1_0_2
-- Date: 2026-04-25
--
-- 注册 3 个 M2 仪表盘 REST 端点（C 章 1-3）：
--   - C.1 GET /api/reports/dashboard/president          → R_RPT_DASH_PRES
--   - C.2 GET /api/reports/dashboard/org/{orgCode}      → R_RPT_DASH_ORG
--   - C.3 GET /api/reports/dashboard/emp/{empId}        → R_RPT_DASH_EMP
--
-- v1.0 note：
--   - RESOURCE_ID < 20 字符（pt_resource 列约束），全部用 R_RPT_DASH_* 形式（max 13 字符）
--   - @BizAuth: BizType.REPORT, BizAction.READ（仪表盘三接口同款）
--   - 角色绑定沿用 M1.6 同款策略：R_ADMIN + R_BACK_TECH 默认全开
--   - BizScope 已在 M1.6 V1_0_1 注册过 R_ADMIN/R_BACK_TECH 的 REPORT/ALL，本脚本不再重复
--
-- 幂等设计：INSERT ... ON DUPLICATE KEY UPDATE / NOT EXISTS 兜底，可重复运行
-- =====================================================================

-- 1. 注册 3 个 REST endpoint
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('R_RPT_DASH_PRES',  '/api/reports/dashboard/president',  'GET', '行长仪表盘',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M2.2 C.1'),
('R_RPT_DASH_ORG',   '/api/reports/dashboard/org/*',      'GET', '机构仪表盘',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M2.3 C.2'),
('R_RPT_DASH_EMP',   '/api/reports/dashboard/emp/*',      'GET', '员工仪表盘',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M2.3 C.3')
ON DUPLICATE KEY UPDATE REMARK = VALUES(REMARK), UPDATE_TIME = NOW();

-- 2. 角色-资源绑定：R_ADMIN + R_BACK_TECH 默认获得全部 3 条
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'RPT', NOW()
  FROM PT_ROLE r
  CROSS JOIN (
        SELECT 'R_RPT_DASH_PRES' AS RESOURCE_ID UNION ALL
        SELECT 'R_RPT_DASH_ORG'  UNION ALL
        SELECT 'R_RPT_DASH_EMP'
  ) res
 WHERE r.ROLE_CODE IN ('R_ADMIN', 'R_BACK_TECH')
   AND NOT EXISTS (
       SELECT 1 FROM PT_ROLE_RESOURCE prr
        WHERE prr.ROLE_ID = r.ROLE_ID AND prr.RESOURCE_ID = res.RESOURCE_ID
   );

-- 注：R_PRESIDENT 角色对 RPT_DASH_PRES 的绑定属于业务策略授权，
-- 由共享 seed-v1.sql §4.12 R_PRESIDENT 节统一维护，不在本脚本内联（避免与其他模块冲突）。
