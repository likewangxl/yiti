-- ============================================================================
-- 注册 PT_RESOURCE：GET /api/perf/kpi-score/logs/latest-date（考核计算页默认数据日期=最大数据日期）。
-- 新端点是 /logs 的子路径，不被既有精确资源 P_PERF_KPISCORE_LOG 匹配 → fail-close 403。
-- 这里登记新资源 P_PERF_KPISCORE_LD，并把角色绑定克隆自 P_PERF_KPISCORE_LOG（同样可见）。
-- yiti + onepl 双库执行；幂等（INSERT IGNORE 资源 + 先删后插绑定）。执行后需刷新资源缓存（重启或 evict auth:resource:all）。
-- ============================================================================

-- ---------- yiti ----------
INSERT IGNORE INTO yiti.PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('P_PERF_KPISCORE_LD', '/api/perf/kpi-score/logs/latest-date', 'GET', 'KPI计算记录最大数据日期', 0, '0', 'M_PERF_KPI_RULES', 0, 'PERF', 'seed', 'seed', 'kpi-calc-log-latest-date');

DELETE FROM yiti.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_LD';
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT UPPER(REPLACE(UUID(), '-', '')), rr.ROLE_ID, 'P_PERF_KPISCORE_LD', rr.SYS_CODE
FROM (SELECT ROLE_ID, SYS_CODE FROM yiti.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_LOG') rr;

-- ---------- onepl ----------
INSERT IGNORE INTO onepl.PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('P_PERF_KPISCORE_LD', '/api/perf/kpi-score/logs/latest-date', 'GET', 'KPI计算记录最大数据日期', 0, '0', 'M_PERF_KPI_RULES', 0, 'PERF', 'seed', 'seed', 'kpi-calc-log-latest-date');

DELETE FROM onepl.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_LD';
INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT UPPER(REPLACE(UUID(), '-', '')), rr.ROLE_ID, 'P_PERF_KPISCORE_LD', rr.SYS_CODE
FROM (SELECT ROLE_ID, SYS_CODE FROM onepl.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_LOG') rr;
