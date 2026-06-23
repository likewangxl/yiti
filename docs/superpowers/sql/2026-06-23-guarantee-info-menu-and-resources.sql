-- =============================================================================
-- 担保信息查询 —— 菜单 + API 资源 + 角色绑定 seed（yiti 库）
-- 配套后端：portal-content-center GuaranteeController (/api/guarantee)
-- 配套前端：xanzc_frontend /guarantee/query
--
-- 幂等：先按 RESOURCE_ID / 绑定 ID 删除既有同名行，再插入。可重复执行。
-- 说明：菜单行 SYS_CODE='YITI'、RESOURCE_METHOD='MENU'（与现有 M_GROUP_INFO 等一致）；
--       API 资源 SYS_CODE='PLATFORM'（与现有 RES_PRODUCT_* 一致）。
--       角色绑定仅绑系统管理员（ROLE_ID=1，SYS_ADMIN）；如需对其他角色可见，
--       在「权限配置」页给对应角色勾选 M_GROUP_GUARANTEE / M_GUARANTEE_QUERY 及 RES_GUARANTEE_* 即可。
-- =============================================================================

-- ---------- 1. 清理旧行（幂等） ----------
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN (
  'M_GROUP_GUARANTEE','M_GUARANTEE_QUERY',
  'RES_GUARANTEE_LIST','RES_GUARANTEE_DETAIL','RES_GUARANTEE_CREATE',
  'RES_GUARANTEE_UPDATE','RES_GUARANTEE_DELETE','RES_GUARANTEE_EXPORT'
);
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN (
  'M_GROUP_GUARANTEE','M_GUARANTEE_QUERY',
  'RES_GUARANTEE_LIST','RES_GUARANTEE_DETAIL','RES_GUARANTEE_CREATE',
  'RES_GUARANTEE_UPDATE','RES_GUARANTEE_DELETE','RES_GUARANTEE_EXPORT'
);

-- ---------- 2. 菜单资源（ISMENU=1, SYS_CODE=YITI, METHOD=MENU） ----------
-- 2.1 顶层目录「担保信息查询」
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_GROUP_GUARANTEE', '#group/guarantee', 'MENU', '担保信息查询', NULL, 6,
   '1', '0', NULL, '0', 'YITI', NOW(), 'seed', '担保信息查询菜单目录');

-- 2.2 子页「担保查询」
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_GUARANTEE_QUERY', '/guarantee/query', 'MENU', '担保查询', NULL, 1,
   '1', '1', 'M_GROUP_GUARANTEE', '0', 'YITI', NOW(), 'seed', '担保查询页面');

-- ---------- 3. API 资源（ISMENU=0, SYS_CODE=PLATFORM） ----------
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_GUARANTEE_LIST',   '/api/guarantee',              'GET',    '担保信息列表', NULL, 0, '0', '0', NULL, '0', 'PLATFORM', NOW(), 'seed', NULL),
  ('RES_GUARANTEE_DETAIL', '/api/guarantee/*',            'GET',    '担保信息详情', NULL, 0, '0', '0', NULL, '0', 'PLATFORM', NOW(), 'seed', NULL),
  ('RES_GUARANTEE_CREATE', '/api/guarantee',              'POST',   '担保信息新增', NULL, 0, '0', '0', NULL, '0', 'PLATFORM', NOW(), 'seed', NULL),
  ('RES_GUARANTEE_UPDATE', '/api/guarantee/*',            'PUT',    '担保信息编辑', NULL, 0, '0', '0', NULL, '0', 'PLATFORM', NOW(), 'seed', NULL),
  ('RES_GUARANTEE_DELETE', '/api/guarantee/batch-delete', 'POST',   '担保信息删除', NULL, 0, '0', '0', NULL, '0', 'PLATFORM', NOW(), 'seed', NULL),
  ('RES_GUARANTEE_EXPORT', '/api/guarantee/export',       'GET',    '担保信息导出', NULL, 0, '0', '0', NULL, '0', 'PLATFORM', NOW(), 'seed', NULL);

-- ---------- 4. 角色绑定（SYS_ADMIN, ROLE_ID=1） ----------
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('RR_GUA_GROUP',  '1', 'M_GROUP_GUARANTEE',   'PLATFORM', NOW()),
  ('RR_GUA_MENU',   '1', 'M_GUARANTEE_QUERY',   'PLATFORM', NOW()),
  ('RR_GUA_LIST',   '1', 'RES_GUARANTEE_LIST',  'PLATFORM', NOW()),
  ('RR_GUA_DETAIL', '1', 'RES_GUARANTEE_DETAIL','PLATFORM', NOW()),
  ('RR_GUA_CREATE', '1', 'RES_GUARANTEE_CREATE','PLATFORM', NOW()),
  ('RR_GUA_UPDATE', '1', 'RES_GUARANTEE_UPDATE','PLATFORM', NOW()),
  ('RR_GUA_DELETE', '1', 'RES_GUARANTEE_DELETE','PLATFORM', NOW()),
  ('RR_GUA_EXPORT', '1', 'RES_GUARANTEE_EXPORT','PLATFORM', NOW());
