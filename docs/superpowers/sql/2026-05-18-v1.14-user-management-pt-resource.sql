-- ===========================================================
-- V1.14 用户管理 PT_RESOURCE 资源注册脚本
-- 关联 spec : docs/superpowers/specs/2026-05-18-user-management-migration-design.md
-- 关联 plan : docs/superpowers/plans/2026-05-18-user-management-migration.md
-- 关联代码 : auth-permission-center/UserController (12 个 REST 接口)
-- 适用库  : MySQL 8.0 (生产 onepl / 测试 onepl_test_bootstrap)
--
-- Schema 对齐：
--   - PT_RESOURCE        ddl-auth.sql RESOURCE_ID(20)/RESOURCE_URL/RESOURCE_METHOD/MENU_NAME(NOT NULL)/SYS_CODE/...
--   - PT_ROLE_RESOURCE   ddl-auth.sql ID(32 UUID) PK + ROLE_ID + RESOURCE_ID + SYS_CODE
--   - 角色 R_ADMIN (ROLE_CODE=SYS_ADMIN)  来自 seed-v1.sql 已有 seed
--   - 资源命名前缀沿用 `A_` (auth)，与 ddl-auth.sql 已有资源（A_LOGIN/A_USER/A_RR/...）一致
--
-- 注意：本脚本不写入 BIZ_TYPE/BIZ_ACTION —— PT_RESOURCE 表无此字段，
--      BIZ_TYPE/BIZ_ACTION 语义由 Controller 上的 @BizAuth 注解承载，由
--      BizMetaResolver 在请求拦截器中解析（参考 AuthorizationInterceptor）。
-- ===========================================================

-- 1. 注册 12 个用户管理接口资源
INSERT IGNORE INTO PT_RESOURCE
  (`RESOURCE_ID`,        `RESOURCE_URL`,                       `RESOURCE_METHOD`, `MENU_NAME`,             `MENU_ICON_URL`, `MENU_RANK_NO`, `ISMENU`, `MENU_ENDFLAG`, `PARENT_RESOURCE_ID`, `STATUS`, `SYS_CODE`, `CREATE_TIME`,        `CREATE_USER`, `UPDATE_TIME`,        `UPDATE_USER`, `REMARK`)
VALUES
  ('A_USER_LIST',        '/api/admin/users',                   'GET',             '用户分页列表',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_EXISTS',      '/api/admin/users/*/exists',          'GET',             '判断用户名是否存在',    NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_DETAIL',      '/api/admin/users/*',                 'GET',             '加载用户详情',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_CREATE',      '/api/admin/users',                   'POST',            '新增用户',              NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_UPDATE',      '/api/admin/users/*',                 'PUT',             '修改用户',              NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_DELETE',      '/api/admin/users/*',                 'DELETE',          '批量删除用户',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_RESET_PWD',   '/api/admin/users/*/reset',           'PUT',             '批量重置密码',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_CHANGE_PWD',  '/api/admin/users/me/password',       'PUT',             '当前用户修改密码',      NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理-自助'),
  ('A_USER_ACTIVE',      '/api/admin/users/*/active',          'PUT',             '批量启用用户',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_INACTIVE',    '/api/admin/users/*/inactive',        'PUT',             '批量禁用用户',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_LOCK',        '/api/admin/users/*/lock',            'PUT',             '批量锁定用户',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理'),
  ('A_USER_UNLOCK',      '/api/admin/users/*/unlock',          'PUT',             '批量解锁用户',          NULL,            0,              0,        '0',            NULL,                 0,        'AUTH',     '2026-05-18 00:00:00','v1.14-seed', '2026-05-18 00:00:00', NULL,         'V1.14 用户管理');

-- 2. 默认绑定到系统管理员角色 R_ADMIN（ROLE_CODE = SYS_ADMIN）
--    主键 ID 使用 32 字符无连字符 UUID，与 seed-v1.sql 已有 PT_ROLE_RESOURCE 风格一致。
INSERT IGNORE INTO PT_ROLE_RESOURCE (`ID`, `ROLE_ID`, `RESOURCE_ID`, `SYS_CODE`, `CREATE_TIME`) VALUES
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_LIST',       'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_EXISTS',     'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_DETAIL',     'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_CREATE',     'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_UPDATE',     'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_DELETE',     'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_RESET_PWD',  'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_CHANGE_PWD', 'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_ACTIVE',     'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_INACTIVE',   'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_LOCK',       'AUTH', '2026-05-18 00:00:00'),
  (REPLACE(UUID(),'-',''), 'R_ADMIN', 'A_USER_UNLOCK',     'AUTH', '2026-05-18 00:00:00');

-- 3. 验证（执行后应分别返回 12）
SELECT COUNT(*) AS user_resource_total
  FROM PT_RESOURCE
 WHERE RESOURCE_ID LIKE 'A_USER_%';

SELECT COUNT(*) AS user_resource_bound_to_admin
  FROM PT_ROLE_RESOURCE
 WHERE ROLE_ID = 'R_ADMIN'
   AND RESOURCE_ID LIKE 'A_USER_%';
