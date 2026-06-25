-- =====================================================================
-- 业绩调整查询 菜单 —— 挂在「历史数据查询」组(M_GROUP_GUARANTEE)下（yiti 库）
-- 前端路由 /history/perf-adjust（复用 report-analytics-center AmasApprovalHistoryController：
--   GET /api/reports/amas-approvals[/{perfAdjustNo}]，资源 R_RPT_AMAS_LIST / R_RPT_AMAS_DET 已注册）
-- 角色绑定：复用 R_RPT_AMAS_LIST 的角色集（凡可调业绩审批历史 API 的角色均可见菜单）
-- 幂等：先删同名行再插入，可重复执行。yiti（dev）与 onepl（prod）双库执行。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_HIST_PERF_ADJUST';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_HIST_PERF_ADJUST';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_HIST_PERF_ADJUST', '/history/perf-adjust', 'MENU', '业绩调整查询', NULL, 3,
   '1', '1', 'M_GROUP_GUARANTEE', '0', 'YITI', NOW(), 'seed', '历史数据查询-业绩调整查询');

-- 绑定到与 R_RPT_AMAS_LIST（业绩审批历史接口）相同的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRMHPJ_', ROLE_ID), ROLE_ID, 'M_HIST_PERF_ADJUST', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST';
