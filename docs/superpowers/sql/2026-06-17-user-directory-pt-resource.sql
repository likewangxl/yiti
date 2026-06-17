-- =============================================================================
-- 2026-06-17  用户通讯录三表联查端点 —— PT_RESOURCE 注册 + 角色授权
-- -----------------------------------------------------------------------------
-- 背景：
--   新增 auth 模块端点（PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查），
--   作为原 portal /api/employees（ADDRBOOK_EMPLOYEE）员工查询的替代数据源，
--   供审批流程「指定人」选择器使用。原 /api/employees 资源保持不动。
--
--   新端点：
--     GET /api/users/directory/search   模糊搜索员工（姓名/工号）
--     GET /api/users/directory/{empId}  员工详情（empId = PT_USER.USER_ID）
--   均使用 @BizAuth(ADDRBOOK, READ)，权限口径与原 /api/employees 查询接口一致。
--
-- 授权策略（权限对等）：
--   新「搜索」端点 → 绑定到当前拥有 RES_PTL_ADDR_SRCH（原员工搜索）的全部角色；
--   新「详情」端点 → 绑定到当前拥有 RES_PTL_ADDR_READ（原员工详情）的全部角色。
--   这样原本能选员工的角色，切换后仍能选；不放大也不缩小权限。
--
-- 幂等：资源用 INSERT IGNORE；角色绑定用 INSERT ... SELECT ... WHERE NOT EXISTS，可安全重跑。
--
-- 执行前请先备份：
--   mysqldump ... PT_RESOURCE PT_ROLE_RESOURCE > docs/superpowers/sql/backup/2026-06-17-pre-user-directory.sql
-- =============================================================================

-- 1) 注册资源（STATUS 默认 0=启用；ResourceMatcher 只匹配 status=0）
INSERT IGNORE INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE)
VALUES
    ('RES_AUTH_UDIR_SRCH', '/api/users/directory/search', 'GET', '员工搜索(通讯录)', 0, 0, 'PLATFORM'),
    ('RES_AUTH_UDIR_READ', '/api/users/directory/*',      'GET', '员工详情(通讯录)', 0, 0, 'PLATFORM');

-- 2) 角色授权：新「搜索」端点对齐原员工搜索（RES_PTL_ADDR_SRCH）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT CONCAT('RRUD_S_', rr.ROLE_ID), rr.ROLE_ID, 'RES_AUTH_UDIR_SRCH'
FROM PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'RES_PTL_ADDR_SRCH'
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE x
      WHERE x.ROLE_ID = rr.ROLE_ID AND x.RESOURCE_ID = 'RES_AUTH_UDIR_SRCH'
  );

-- 3) 角色授权：新「详情」端点对齐原员工详情（RES_PTL_ADDR_READ）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT CONCAT('RRUD_R_', rr.ROLE_ID), rr.ROLE_ID, 'RES_AUTH_UDIR_READ'
FROM PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'RES_PTL_ADDR_READ'
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE x
      WHERE x.ROLE_ID = rr.ROLE_ID AND x.RESOURCE_ID = 'RES_AUTH_UDIR_READ'
  );

-- 4) 校验
SELECT '新增资源' AS info, COUNT(*) AS cnt FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_AUTH_UDIR%';
SELECT '搜索端点授权角色数' AS info, COUNT(*) AS cnt FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_AUTH_UDIR_SRCH';
SELECT '详情端点授权角色数' AS info, COUNT(*) AS cnt FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_AUTH_UDIR_READ';

-- 注意：执行后需让 auth 缓存失效（资源缓存 auth:resource:all），重启服务或触发缓存刷新后生效。
