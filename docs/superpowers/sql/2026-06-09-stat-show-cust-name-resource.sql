-- ============================================================================
-- 2026-06-09 新增端点资源注册：客户名称改从客户主档 CUST_MASTER 查询
--   GET /api/perf/stat-show/cust-name  → StatShowController.getCustMasterName
-- AuthorizationInterceptor 要求 URL 注册到 PT_RESOURCE，否则未注册资源返回 403。
-- 复用 P_PERF_TGT_V_LIST 受众（与既有 stat-show 资源同一批角色）。幂等 / 双库可重跑。
-- ============================================================================

-- yiti
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_STAT_CUSTNAME', '/api/perf/stat-show/cust-name', 'GET', '客户主档名称反显',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-09 cust-name from CUST_MASTER'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_STAT_CUSTNAME');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'P_PERF_STAT_CUSTNAME', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'P_PERF_TGT_V_LIST'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'P_PERF_STAT_CUSTNAME');

-- onepl
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_STAT_CUSTNAME', '/api/perf/stat-show/cust-name', 'GET', '客户主档名称反显',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-09 cust-name from CUST_MASTER'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_STAT_CUSTNAME');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'P_PERF_STAT_CUSTNAME', 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'P_PERF_TGT_V_LIST'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'P_PERF_STAT_CUSTNAME');
