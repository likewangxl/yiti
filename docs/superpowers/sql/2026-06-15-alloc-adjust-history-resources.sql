-- ============================================================================
-- 2026-06-15 业绩调整（PERF_ALLOC_ADJUST_APPLY）只读查看 —— API 鉴权资源
--   GET /api/reports/alloc-adjust-applies        → AllocAdjustHistoryController.list   (R_RPT_ALC_LIST)
--   GET /api/reports/alloc-adjust-applies/{id}    → AllocAdjustHistoryController.detail (R_RPT_ALC_DET)
-- 与「业绩分配审批历史」同一页面的「业绩调整」Tab。受众复制 R_RPT_FREE_DATA。双库幂等可重跑。
-- ============================================================================

-- ===================== yiti =====================
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_LIST', '/api/reports/alloc-adjust-applies', 'GET', '业绩调整-列表',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 alloc adjust apply list'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_LIST');

INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_DET', '/api/reports/alloc-adjust-applies/*', 'GET', '业绩调整-详情',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 alloc adjust apply detail'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_DET');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_ALC_LIST', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_ALC_LIST');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_ALC_DET', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_ALC_DET');

-- ===================== onepl =====================
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_LIST', '/api/reports/alloc-adjust-applies', 'GET', '业绩调整-列表',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 alloc adjust apply list'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_LIST');

INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_DET', '/api/reports/alloc-adjust-applies/*', 'GET', '业绩调整-详情',
       0, 0, 0, 0, 'RPT', 'seed', '2026-06-15 alloc adjust apply detail'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_DET');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_ALC_LIST', 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_ALC_LIST');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'R_RPT_ALC_DET', 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'R_RPT_ALC_DET');
