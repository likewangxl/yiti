-- =====================================================================
-- AMAS_PRICE_APPROVAL 价格审批查询 —— PT_RESOURCE 资源注册 + 角色绑定（yiti 库）
-- 配套后端：report-analytics-center AmasPriceApprovalController
--   GET /api/reports/amas-price-approvals       → R_RPT_PRICE_LIST（列表）
--   GET /api/reports/amas-price-approvals/{id}   → R_RPT_PRICE_DET （详情）
-- 角色绑定：复用 R_RPT_AMAS_LIST 的角色集（与"业绩分配审批历史"列表一致）
-- 幂等：先删同名行再插入，可重复执行。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('R_RPT_PRICE_LIST','R_RPT_PRICE_DET');
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID IN ('R_RPT_PRICE_LIST','R_RPT_PRICE_DET');

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('R_RPT_PRICE_LIST', '/api/reports/amas-price-approvals',   'GET', '价格审批-列表', NULL, 0, '0', '0', NULL, '0', 'RPT', NOW(), 'seed', NULL),
  ('R_RPT_PRICE_DET',  '/api/reports/amas-price-approvals/*', 'GET', '价格审批-详情', NULL, 0, '0', '0', NULL, '0', 'RPT', NOW(), 'seed', NULL);

-- 绑定到与 R_RPT_AMAS_LIST 相同的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPRCL_', ROLE_ID), ROLE_ID, 'R_RPT_PRICE_LIST', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST';

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPRCD_', ROLE_ID), ROLE_ID, 'R_RPT_PRICE_DET', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST';
