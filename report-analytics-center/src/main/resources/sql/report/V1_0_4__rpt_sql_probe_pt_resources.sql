-- =====================================================================
-- report-analytics-center M4 阶段 PT_RESOURCE 注册（Task M4.4.1）
-- Version: V1_0_4
-- Date: 2026-04-25
--
-- 注册 4 个 M4 SQL 探查 D 章 REST 端点：
--   - D.1 POST /api/reports/sql-probe/execute             → R_RPT_SQL_EXEC
--   - D.2 GET  /api/reports/sql-probe/history             → R_RPT_SQL_HIST
--   - D.3 GET  /api/reports/sql-probe/history/{id}        → R_RPT_SQL_HIST_DTL
--   - D.4 GET  /api/reports/sql-probe/schema-whitelist    → R_RPT_SQL_WL
--
-- v1.0 note：
--   - RESOURCE_ID < 20 字符（pt_resource 列约束）
--   - @BizAuth: REPORT/EXECUTE_SQL（D.1）+ REPORT/LIST|READ（D.2-D.4）
--     （bizType 全部 REPORT，由 RptBizAuthConsistencyArchTest 守护单档策略；
--      action EXECUTE_SQL 复用 common-security 现有枚举值）
--   - 角色绑定：D.1 仅 R_BACK_TECH（SQL 探查执行高危）；D.2-D.4 R_ADMIN + R_BACK_TECH
--     （历史 / 白名单仅技术运维使用，业务角色无需）
--
-- 幂等设计：INSERT ... ON DUPLICATE KEY UPDATE / NOT EXISTS 兜底，可重复运行
-- =====================================================================

-- 1. 注册 4 个 REST endpoint
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('R_RPT_SQL_EXEC',     '/api/reports/sql-probe/execute',           'POST', 'SQL 探查执行',       NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M4.2 D.1'),
('R_RPT_SQL_HIST',     '/api/reports/sql-probe/history',           'GET',  'SQL 探查历史列表',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M4.3 D.2'),
('R_RPT_SQL_HIST_DTL', '/api/reports/sql-probe/history/*',         'GET',  'SQL 探查历史详情',   NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M4.3 D.3'),
('R_RPT_SQL_WL',       '/api/reports/sql-probe/schema-whitelist',  'GET',  'SQL 探查白名单展示', NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M4.3 D.4')
ON DUPLICATE KEY UPDATE REMARK = VALUES(REMARK), UPDATE_TIME = NOW();

-- 2. 角色-资源绑定：D.1 execute 仅 R_BACK_TECH（SQL 执行高危）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, 'R_RPT_SQL_EXEC', 'RPT', NOW()
  FROM PT_ROLE r
 WHERE r.ROLE_CODE IN ('R_BACK_TECH')
   AND NOT EXISTS (
       SELECT 1 FROM PT_ROLE_RESOURCE prr
        WHERE prr.ROLE_ID = r.ROLE_ID AND prr.RESOURCE_ID = 'R_RPT_SQL_EXEC'
   );

-- 3. 角色-资源绑定：D.2-D.4 历史 / 白名单 R_ADMIN + R_BACK_TECH
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'RPT', NOW()
  FROM PT_ROLE r
  CROSS JOIN (
        SELECT 'R_RPT_SQL_HIST'     AS RESOURCE_ID UNION ALL
        SELECT 'R_RPT_SQL_HIST_DTL' UNION ALL
        SELECT 'R_RPT_SQL_WL'
  ) res
 WHERE r.ROLE_CODE IN ('R_ADMIN', 'R_BACK_TECH')
   AND NOT EXISTS (
       SELECT 1 FROM PT_ROLE_RESOURCE prr
        WHERE prr.ROLE_ID = r.ROLE_ID AND prr.RESOURCE_ID = res.RESOURCE_ID
   );

-- 注：业务角色（R_PRESIDENT / R_HR / R_RM 等）默认不可访问 SQL 探查域
-- （仅技术运维使用，由 BR-1 决策点强约束）。
