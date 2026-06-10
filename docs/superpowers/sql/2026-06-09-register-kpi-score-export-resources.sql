-- ============================================================================
-- 注册 PT_RESOURCE：KPI 计算结果详情页两个导出端点（GET）。
--   P_PERF_KPISCORE_EXS → /api/perf/kpi-score/export-scores  （导出KPI得分，页面透视格式）
--   P_PERF_KPISCORE_EXD → /api/perf/kpi-score/export-details （导出KPI明细，PERF_KPI_SCORE 平铺）
-- 角色绑定克隆自结果详情资源 P_PERF_KPISCORE_RES（同样可见）。yiti + onepl 双库；幂等。
-- 执行后刷新资源缓存（重启或 evict auth:resource:all）。
-- ============================================================================

-- ============ yiti ============
INSERT IGNORE INTO yiti.PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('P_PERF_KPISCORE_EXS', '/api/perf/kpi-score/export-scores',  'GET', '导出KPI得分',   0, '0', 'M_PERF_KPI_RULES', 0, 'PERF', 'seed', 'seed', 'kpi-score-export'),
  ('P_PERF_KPISCORE_EXD', '/api/perf/kpi-score/export-details', 'GET', '导出KPI明细数据', 0, '0', 'M_PERF_KPI_RULES', 0, 'PERF', 'seed', 'seed', 'kpi-score-export');

DELETE FROM yiti.PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_KPISCORE_EXS', 'P_PERF_KPISCORE_EXD');
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT UPPER(REPLACE(UUID(), '-', '')), rr.ROLE_ID, 'P_PERF_KPISCORE_EXS', rr.SYS_CODE
FROM (SELECT ROLE_ID, SYS_CODE FROM yiti.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_RES') rr;
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT UPPER(REPLACE(UUID(), '-', '')), rr.ROLE_ID, 'P_PERF_KPISCORE_EXD', rr.SYS_CODE
FROM (SELECT ROLE_ID, SYS_CODE FROM yiti.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_RES') rr;

-- ============ onepl ============
INSERT IGNORE INTO onepl.PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('P_PERF_KPISCORE_EXS', '/api/perf/kpi-score/export-scores',  'GET', '导出KPI得分',   0, '0', 'M_PERF_KPI_RULES', 0, 'PERF', 'seed', 'seed', 'kpi-score-export'),
  ('P_PERF_KPISCORE_EXD', '/api/perf/kpi-score/export-details', 'GET', '导出KPI明细数据', 0, '0', 'M_PERF_KPI_RULES', 0, 'PERF', 'seed', 'seed', 'kpi-score-export');

DELETE FROM onepl.PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_KPISCORE_EXS', 'P_PERF_KPISCORE_EXD');
INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT UPPER(REPLACE(UUID(), '-', '')), rr.ROLE_ID, 'P_PERF_KPISCORE_EXS', rr.SYS_CODE
FROM (SELECT ROLE_ID, SYS_CODE FROM onepl.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_RES') rr;
INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT UPPER(REPLACE(UUID(), '-', '')), rr.ROLE_ID, 'P_PERF_KPISCORE_EXD', rr.SYS_CODE
FROM (SELECT ROLE_ID, SYS_CODE FROM onepl.PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPISCORE_RES') rr;
