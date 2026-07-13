-- =====================================================================
-- 审批流监控 菜单 —— 挂在「系统设置」组(M_GROUP_SYSTEM)下，排在审批流程之后（rank 14）
-- 前端路由 /system/workflow-monitor（WorkflowMonitor.vue；列表走
--   GET /api/workflow/monitor/processes，资源 RES_WF_MONITOR_LIST 已由 Task 6 登记）
-- 角色绑定：秘书岗(中场支持部门秘书)/分行行长 —— LIVE DB(yiti) 当前 ROLE_ID 为
--   231 / 2（见 2026-06-10-role-id-realign-to-intranet.sql 的号段对齐，PT_ROLE
--   已从语义 ID R_SUPPORT_SEC/R_PRESIDENT 迁移为数字 ID，本脚本直接用数字 ID
--   落库；docs/schema/seed-v1.sql 的 PT_ROLE 仍是语义 ID，另在该文件追加对应
--   语义 ID 版本，两者不冲突，各自面向不同基线）
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_SYS_WF_MONITOR', '/system/workflow-monitor', 'GET', '审批流监控', NULL, 14,
   1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', '系统设置-审批流监控（workflow-monitor-transfer Task 7）');

-- 绑定角色：231=中场支持部门秘书（本机构范围），2=分行行长（全行范围）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
  ('RRMWFM_231', '231', 'M_SYS_WF_MONITOR', 'YITI', NOW()),
  ('RRMWFM_2',   '2',   'M_SYS_WF_MONITOR', 'YITI', NOW());

-- ── 验证 ────────────────────────────────────────────────────
SELECT RESOURCE_ID, RESOURCE_URL, MENU_NAME, ISMENU, PARENT_RESOURCE_ID, MENU_RANK_NO
FROM PT_RESOURCE WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';

SELECT ID, ROLE_ID, RESOURCE_ID FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';

-- =====================================================================
-- 补漏：Task 6 的 76cbaacd 只登记了 RES_WF_MONITOR_LIST 资源行
-- （PT_RESOURCE）+ 秘书/行长数据范围（PT_ROLE_BIZ_SCOPE），漏了
-- RBAC 授权绑定（PT_ROLE_RESOURCE）——AuthorizationInterceptor Step 2
-- rbacAuthorizer.authorize() 是独立于 DataScopeContext 的前置门禁，
-- 未绑定时秘书/行长调用 GET /api/workflow/monitor/processes 一律 403
-- AUTH-40301，菜单能看见但列表打不开。这里一并补上（同 M_SYS_WF_MONITOR
-- 绑定的两个角色，Task 7 发现，随手修复）。
-- =====================================================================
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_MONITOR_LIST';
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
  ('RRWFML_231', '231', 'RES_WF_MONITOR_LIST', 'YITI', NOW()),
  ('RRWFML_2',   '2',   'RES_WF_MONITOR_LIST', 'YITI', NOW());

SELECT ID, ROLE_ID, RESOURCE_ID FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_MONITOR_LIST';
