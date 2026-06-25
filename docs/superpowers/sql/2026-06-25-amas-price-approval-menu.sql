-- =====================================================================
-- 定价审批查询 菜单 —— 挂在「历史数据查询」组(M_GROUP_GUARANTEE)下（yiti 库）
-- 前端路由 /history/price-approval（report-analytics-center AmasPriceApprovalController）
-- 角色绑定：复用 R_RPT_PRICE_LIST 的角色集（凡可调价格审批查询 API 的角色均可见菜单）
-- 幂等：先删同名行再插入，可重复执行。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_HIST_PRICE_APPR';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_HIST_PRICE_APPR';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_HIST_PRICE_APPR', '/history/price-approval', 'MENU', '定价审批查询', NULL, 2,
   '1', '1', 'M_GROUP_GUARANTEE', '0', 'YITI', NOW(), 'seed', '历史数据查询-定价审批查询');

-- 绑定到与 R_RPT_PRICE_LIST（价格审批查询接口）相同的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRMHPA_', ROLE_ID), ROLE_ID, 'M_HIST_PRICE_APPR', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'R_RPT_PRICE_LIST';
