-- =====================================================================
-- 菜单管理「分配角色」接口资源登记（资源维度查询/设置绑定角色）
-- 新端点（ResourceController）：
--   GET /api/admin/resources/*/roles  → 查询资源已绑角色（A_RES_ROLES_GET）
--   PUT /api/admin/resources/*/roles  → 设置资源绑定角色（A_RES_ROLES_BIND）
-- AntPath 单 '*' 不跨 '/'，故须单独登记（/api/admin/resources/* 不能匹配 .../roles）。
-- 角色绑定：复用 A_RR_REPLACE（角色资源全量替换接口）相同角色集，权限语义一致。
-- 幂等：先删后插，可重复执行。yiti（dev）与 onepl（prod）双库执行。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('A_RES_ROLES_GET', 'A_RES_ROLES_BIND');
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID IN ('A_RES_ROLES_GET', 'A_RES_ROLES_BIND');

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('A_RES_ROLES_GET',  '/api/admin/resources/*/roles', 'GET', '查询资源已绑角色', '0', '0', 'YITI', NOW(), 'seed', '菜单管理-分配角色-回显'),
  ('A_RES_ROLES_BIND', '/api/admin/resources/*/roles', 'PUT', '设置资源绑定角色', '0', '0', 'YITI', NOW(), 'seed', '菜单管理-分配角色-保存');

-- 绑定到与 A_RR_REPLACE 相同的角色集（凡可全量替换角色资源者均可分配角色）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRARG_', ROLE_ID), ROLE_ID, 'A_RES_ROLES_GET', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'A_RR_REPLACE';
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRARB_', ROLE_ID), ROLE_ID, 'A_RES_ROLES_BIND', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'A_RR_REPLACE';
