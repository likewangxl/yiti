-- ============================================================================
-- 「人员标签」更名为「业务标签」（系统设置 > 业务标签）
-- 日期：2026-07-24
-- 说明：菜单名 DB 驱动（侧边栏/面包屑/页面标题同源于 PT_RESOURCE.MENU_NAME）。
--       仅改系统治理的业务标签菜单 M_SYS_PERSON_TAGS 及其子资源；
--       eval 子域的同名「人员标签」菜单 M_EVAL_USER_TAGS 是另一功能，保持不动。
-- ============================================================================

-- 1) 菜单本体改名
UPDATE PT_RESOURCE
SET MENU_NAME = '业务标签', UPDATE_TIME = NOW()
WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';

-- 2) 子资源（按钮/接口权限）名称同步改名，保持权限管理页可读（如「人员标签-新建」→「业务标签-新建」）
UPDATE PT_RESOURCE
SET MENU_NAME = REPLACE(MENU_NAME, '人员标签', '业务标签'), UPDATE_TIME = NOW()
WHERE PARENT_RESOURCE_ID = 'M_SYS_PERSON_TAGS' AND MENU_NAME LIKE '人员标签%';
