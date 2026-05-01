-- =====================================================================
-- report-analytics-center M5 阶段 PT_RESOURCE 注册（Task M5.4.2）
-- Version: V1_0_6
-- Date: 2026-04-25
--
-- 注册 3 个 M5 E 章导出任务 REST 端点：
--   - E.1 GET    /api/reports/export-tasks/{taskId}             → R_RPT_EXP_STATUS
--   - E.2 DELETE /api/reports/export-tasks/{taskId}             → R_RPT_EXP_CANCEL
--   - E.3 GET    /api/reports/export-tasks/{taskId}/download    → R_RPT_EXP_DOWNLOAD
--
-- v1.0 note：
--   - RESOURCE_ID < 20 字符（pt_resource 列约束）
--   - @BizAuth: REPORT/READ（status）+ REPORT/WRITE（cancel）+ REPORT/EXPORT（download）
--   - 角色绑定：所有 4 个核心角色（R_PRESIDENT / R_ORG_HEAD / R_BACK_TECH / R_ADMIN）
--     + 业务角色（R_HR / R_RM 等）按需后续追加
--
-- 幂等设计：INSERT ... ON DUPLICATE KEY UPDATE / NOT EXISTS 兜底，可重复运行
-- =====================================================================

-- 1. 注册 3 个 REST endpoint
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('R_RPT_EXP_STATUS',   '/api/reports/export-tasks/*',          'GET',    '导出任务状态查询', NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M5.3 E.1'),
('R_RPT_EXP_CANCEL',   '/api/reports/export-tasks/*',          'DELETE', '导出任务取消',     NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M5.3 E.2'),
('R_RPT_EXP_DOWNLOAD', '/api/reports/export-tasks/*/download', 'GET',    '导出任务下载',     NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M5.3 E.3')
ON DUPLICATE KEY UPDATE REMARK = VALUES(REMARK), UPDATE_TIME = NOW();

-- 2. 角色-资源绑定：4 个核心角色 + 3 个资源
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'RPT', NOW()
  FROM PT_ROLE r
  CROSS JOIN (
        SELECT 'R_RPT_EXP_STATUS'   AS RESOURCE_ID UNION ALL
        SELECT 'R_RPT_EXP_CANCEL'   UNION ALL
        SELECT 'R_RPT_EXP_DOWNLOAD'
  ) res
 WHERE r.ROLE_CODE IN ('R_PRESIDENT', 'R_ORG_HEAD', 'R_BACK_TECH', 'R_ADMIN')
   AND NOT EXISTS (
       SELECT 1 FROM PT_ROLE_RESOURCE prr
        WHERE prr.ROLE_ID = r.ROLE_ID AND prr.RESOURCE_ID = res.RESOURCE_ID
   );

-- 注：业务侧 R_HR / R_RM 等角色若需访问导出，由 V1.1+ 单独追加 INSERT。
