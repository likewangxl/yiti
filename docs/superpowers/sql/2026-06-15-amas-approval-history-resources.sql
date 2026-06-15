-- ============================================================================
-- 2026-06-15 新增端点资源注册：业绩分配审批历史（报表分析中心，只读）
--   GET /api/reports/amas-approvals        → AmasApprovalHistoryController.list   (R_RPT_AMAS_LIST)
--   GET /api/reports/amas-approvals/{no}   → AmasApprovalHistoryController.detail (R_RPT_AMAS_DET)
-- AuthorizationInterceptor 要求 URL 注册到 PT_RESOURCE，否则未注册资源返回 403。
-- 受众复制自现有报表查询资源 R_RPT_FREE_DATA（自由报表查询）。幂等 / 双库可重跑。
-- RESOURCE_ID 长度 <= 20；路径变量用 AntPath '*' 通配。
-- ============================================================================

-- ===================== yiti =====================
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_LIST', '/api/reports/amas-approvals', 'GET', '业绩分配审批历史-列表',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 amas approval history list'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST');

INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_DET', '/api/reports/amas-approvals/*', 'GET', '业绩分配审批历史-详情',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 amas approval history detail'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_DET');

-- 角色授权：复制 R_RPT_FREE_DATA 的受众
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_AMAS_LIST', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_AMAS_LIST');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_AMAS_DET', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_AMAS_DET');

-- ===================== onepl =====================
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_LIST', '/api/reports/amas-approvals', 'GET', '业绩分配审批历史-列表',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 amas approval history list'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST');

INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_DET', '/api/reports/amas-approvals/*', 'GET', '业绩分配审批历史-详情',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 amas approval history detail'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_DET');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_AMAS_LIST', 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_AMAS_LIST');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_AMAS_DET', 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_AMAS_DET');
