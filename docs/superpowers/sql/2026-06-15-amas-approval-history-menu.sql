-- ============================================================================
-- 2026-06-15 业绩分配审批历史 —— 菜单项资源（前端侧边栏 my-menus 驱动）
-- 说明：侧边栏由 GET /api/auth/my-menus 渲染，菜单项是独立于 API 资源的一类
--   PT_RESOURCE 行（RESOURCE_METHOD='MENU'、URL=前端路由、SYS_CODE=YITI、ISMENU=1）。
--   API 鉴权资源（R_RPT_AMAS_LIST/DET）见 2026-06-15-amas-approval-history-resources.sql。
-- 仅 yiti：onepl 无 M_GROUP_REPORT 菜单父节点（该菜单体系为 yiti 开发库专用）。
-- 授权复制自同组菜单项 M_REPORT_FREE（自由报表）。幂等可重跑。
-- ============================================================================

-- 菜单项：业绩分配审批历史（挂在「报表分析」M_GROUP_REPORT 下，排在自由报表之后）
INSERT INTO yiti.PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
     MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'M_REPORT_AMAS', '/report/amas-approvals', 'MENU', '业绩分配审批历史', 6, 1,
       '1', 'M_GROUP_REPORT', 0, 'YITI', 'seed', '业绩分配审批历史菜单'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'M_REPORT_AMAS');

-- 角色授权：复制 M_REPORT_FREE 的受众
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'M_REPORT_AMAS', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'M_REPORT_FREE'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'M_REPORT_AMAS');
