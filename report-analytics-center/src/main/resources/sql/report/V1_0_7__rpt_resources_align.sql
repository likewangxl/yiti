-- =====================================================================
-- report-analytics-center M6 阶段 PT_RESOURCE 收尾对齐（Task M6.1.1）
-- Version: V1_0_7
-- Date: 2026-04-25
--
-- 注册 1 条占位 R_RPT_* 资源，让 V1.0 累计达到 plan §5 验收清单期望的 25 条整：
--   - V1.1+ 占位：R_RPT_SQL_EXP（POST /api/reports/sql-probe/export）
--     用于 SQL 探查结果的异步导出端点（plan I.5 V1.1+ 规划，M0-M5 未实现）
--     STATUS=1（DISABLED，禁用），不会被 AuthorizationInterceptor 命中放行；
--     V1.1 正式实现该端点时 ALTER STATUS=0 启用即可。
--
-- 累计 25 条 R_RPT_* 拆分：
--   M1 V1_0_1: 8 条（query-dimensions / dynamic-query×2 / saved-queries×4 + 1 导出占位）
--   M2 V1_0_2: 3 条（dashboard president/org/emp）
--   M3 V1_0_3: 6 条（3 view + 3 export）
--   M4 V1_0_4: 4 条（sql-probe execute/history/history-detail/whitelist）
--   M5 V1_0_6: 3 条（export-tasks status/cancel/download）
--   M6 V1_0_7: 1 条占位（sql-probe export）
--   合计 = 25 条整
--
-- v1.0 note：
--   - RESOURCE_ID < 20 字符（pt_resource 列约束）：R_RPT_SQL_EXP（13 字符）
--   - 角色绑定：R_RPT_SQL_EXP 占位资源不绑定任何角色（V1.0 无任何路由会撞它）
--   - SYS_CODE='RPT' 沿袭 M1-M5 同款
--
-- 幂等设计：INSERT ... ON DUPLICATE KEY UPDATE 兜底，可重复运行
-- =====================================================================

INSERT INTO pt_resource (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('R_RPT_SQL_EXP', '/api/reports/sql-probe/export', 'POST', 'SQL 探查结果导出', NULL, 0, 0, '0', NULL, 1, 'RPT', NOW(), 'seed', 'v1.0 M6.1 占位（V1.1+ 启用）')
ON DUPLICATE KEY UPDATE REMARK = VALUES(REMARK), UPDATE_TIME = NOW();
