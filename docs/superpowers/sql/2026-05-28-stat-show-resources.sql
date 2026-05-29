-- ============================================================================
-- 2026-05-28 财务统计展示表只读查询接口资源注册（幂等 / 双库可重跑）
-- ============================================================================
-- 新增 2 个只读端点（performance-engine-center StatShowController）：
--   GET /api/perf/stat-show/emp   → 员工维度 XAN_M9B_EMP_STAT_SHOW3
--   GET /api/perf/stat-show/cust  → 客户维度 XAN_M98_CUST_STAT_SHOW3
-- AuthorizationInterceptor 要求 URL 注册到 PT_RESOURCE，否则未注册资源返回 403。
--
-- 执行：
--   mysql -u root -p<pwd> yiti  < 2026-05-28-stat-show-resources.sql
--   mysql -u root -p<pwd> onepl < 2026-05-28-stat-show-resources.sql
-- ============================================================================

-- 1. 注册资源（幂等：NOT EXISTS）
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE,
                         CREATE_USER, REMARK)
SELECT 'P_PERF_STAT_EMP', '/api/perf/stat-show/emp', 'GET', '员工财务统计展示',
       0, 0, 0, 0, 'PERF', 'seed', 'v1.13 stat-show'
WHERE NOT EXISTS (SELECT 1 FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_STAT_EMP');

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE,
                         CREATE_USER, REMARK)
SELECT 'P_PERF_STAT_CUST', '/api/perf/stat-show/cust', 'GET', '客户财务统计展示',
       0, 0, 0, 0, 'PERF', 'seed', 'v1.13 stat-show'
WHERE NOT EXISTS (SELECT 1 FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_STAT_CUST');

-- 2. 绑定给所有已绑 P_PERF_TGT_V_LIST（目标值查询权限）的角色，复用同一受众
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, sr.resource_id, 'PLATFORM'
FROM PT_ROLE_RESOURCE rr
CROSS JOIN (SELECT 'P_PERF_STAT_EMP' AS resource_id
            UNION ALL SELECT 'P_PERF_STAT_CUST') sr
WHERE rr.resource_id = 'P_PERF_TGT_V_LIST'
  AND NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE rr2
    WHERE rr2.role_id = rr.role_id AND rr2.resource_id = sr.resource_id
  );

-- 验证
-- SELECT resource_id, resource_url FROM PT_RESOURCE WHERE resource_id LIKE 'P_PERF_STAT_%';
-- SELECT resource_id, COUNT(*) FROM PT_ROLE_RESOURCE WHERE resource_id LIKE 'P_PERF_STAT_%' GROUP BY resource_id;
