-- =====================================================================
-- 绩效域员工搜索端点 PT_RESOURCE + 角色绑定
--   GET /api/perf/employees/search  P_PERF_EMP_SRCH
--
-- 背景：目标值管理页「按工号选人」的输入建议原先走管理员接口 /api/admin/users，
--   该接口资源 A_USER_LIST 仅授予角色 1/3/4/131/169，资财部经办人(238)等业务角色
--   进页面即 403「没有权限」。现由绩效模块自开轻量端点替代。
--
-- 为什么不复用现成端点（三者各差一项，详见 PerfEmployeeQueryService 类注释）：
--   /api/admin/users                → 字段对，但管理员接口，业务角色无权且不宜授权
--   /api/employees（通讯录）         → 权限够，但数据源 ADDRBOOK_EMPLOYEE 覆盖不足（实测 12 vs 82）
--   /api/reports/employees/search   → 权限与覆盖面都够，但返回 USER_ID 而非工号，
--                                     与目标值 subject_id(=PT_USER.USERNAME) 对不上
--
-- 角色绑定口径：与**目标值列表** /api/perf/target-values (GET) 完全一致
--   （1 系统管理员、17 零售部负责人、128 公司部负责人、129 资财部负责人、
--     130 公司主管行领导、229 中后台员工(科技)、238 资财部经办人，实测确认）。
--   ⚠️ 刻意不按「持有 PERF_CONFIG 数据范围的全部 37 个角色」授权——那批角色里多数
--   根本进不了目标值页面，本端点是为该页面选人服务的，受众应与页面一致，避免过度授权。
--   Controller 侧 @BizAuth(PERF_CONFIG, LIST) 与目标值列表同一权限位。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅新增本脚本自身的资源与授权行，不触碰既有资源。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_EMP_SRCH';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'P_PERF_EMP_SRCH';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('P_PERF_EMP_SRCH', '/api/perf/employees/search', 'GET',
   '绩效员工搜索', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'perf-emp-2026-07-21',
   '目标值选人输入建议，返回工号');

-- 与 /api/perf/target-values (GET) 同一批角色
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('PEMPS_1',   '1',   'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_17',  '17',  'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_128', '128', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_129', '129', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_130', '130', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_229', '229', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_238', '238', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 1 行资源
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU
  FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_EMP_SRCH';

-- 预期两行角色集合完全相同（新端点 与 目标值列表）
SELECT '新端点' AS src, GROUP_CONCAT(rr.ROLE_ID ORDER BY CAST(rr.ROLE_ID AS UNSIGNED)) AS roles
  FROM PT_ROLE_RESOURCE rr WHERE rr.RESOURCE_ID = 'P_PERF_EMP_SRCH'
UNION ALL
SELECT '目标值列表', GROUP_CONCAT(rr.ROLE_ID ORDER BY CAST(rr.ROLE_ID AS UNSIGNED))
  FROM PT_ROLE_RESOURCE rr JOIN PT_RESOURCE r ON r.RESOURCE_ID = rr.RESOURCE_ID
 WHERE r.RESOURCE_URL = '/api/perf/target-values' AND r.RESOURCE_METHOD = 'GET';
