-- ============================================================================
-- 红色引擎平台菜单入口对齐
-- 日期：2026-07-29
-- 目的：
--   1. 将红色引擎作为 Branch Platform 动态菜单中的一个顶层叶子菜单；
--   2. 复用平台 PT_USER + Spring Session 登录态，不再提供 /redengine/login；
--   3. 保持 /redengine/** 下 RedEngineLayout 与红色主题页面风格不变；
--   4. 将 17 条 P_RE_* API 资源挂到菜单下，使平台“权限配置”分配菜单时自动联动接口权限。
--
-- 执行要求：
--   - 本脚本属于已有库增量对齐脚本，执行前必须备份目标库；
--   - 幂等：INSERT IGNORE + 精确 UPDATE，可安全重复执行；
--   - 不包含 DDL，不使用 Flyway。
-- ============================================================================

-- 1) 平台顶层叶子菜单：AppSidebar 点击后进入红色引擎工作台。
INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
  MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('M_RE_ENGINE', '/redengine/dashboard', 'MENU', '红色引擎', 7, 1,
  '1', NULL, 0, 'RE', 'redengine-menu-align', '平台统一菜单入口，页面保持红色引擎原风格');

-- INSERT IGNORE 不会刷新既有行，显式对齐所有菜单契约字段。
UPDATE PT_RESOURCE
SET RESOURCE_URL = '/redengine/dashboard',
    RESOURCE_METHOD = 'MENU',
    MENU_NAME = '红色引擎',
    MENU_RANK_NO = 7,
    ISMENU = 1,
    MENU_ENDFLAG = '1',
    PARENT_RESOURCE_ID = NULL,
    STATUS = 0,
    SYS_CODE = 'RE',
    UPDATE_USER = 'redengine-menu-align',
    UPDATE_TIME = CURRENT_TIMESTAMP
WHERE RESOURCE_ID = 'M_RE_ENGINE';

-- 2) API 资源挂到菜单下，供 RoleResourceService.replaceMenus 自动联动授权。
UPDATE PT_RESOURCE
SET PARENT_RESOURCE_ID = 'M_RE_ENGINE',
    UPDATE_USER = 'redengine-menu-align',
    UPDATE_TIME = CURRENT_TIMESTAMP
WHERE RESOURCE_ID LIKE 'P\_RE\_%';

-- 3) 四个党建角色均显示红色引擎平台菜单。
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM (
    SELECT ROLE_ID FROM PT_ROLE
    WHERE ROLE_CODE IN ('R_RE_ORGREV','R_RE_BRREV','R_RE_SECR','R_RE_REPORT')
) r
JOIN (SELECT 'M_RE_ENGINE' rid) x;

-- 4) 系统管理员显示红色引擎平台菜单。
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM (
    SELECT ROLE_ID FROM PT_ROLE
    WHERE ROLE_CODE = 'SYS_ADMIN'
) r
JOIN (SELECT 'M_RE_ENGINE' rid) x;
