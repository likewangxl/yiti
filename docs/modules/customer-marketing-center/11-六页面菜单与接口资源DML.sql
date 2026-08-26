-- 客户营销六页面菜单与新版 /api/marketing/** 接口资源。
-- 只供 DBA 评审和上线执行，不由应用自动执行。
-- 前置：已存在 M_GROUP_MARKETING 及客户营销一期、三期菜单。
-- 口径：总列表只授 SYS_ADMIN；客户经理获得“我的客户”和线索录入；
-- 线索审批人获得线索审批；标签页沿用旧菜单的已授权角色，不扩大人员范围。

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('M_MKT_CUST_MGMT', '/customers/manage', 'MENU', '营销客户列表', NULL, 1, 1, '1',
     'M_GROUP_MARKETING', 0, 'YITI', 'marketing-six-pages', 'marketing-six-pages', '营销管理员专用'),
    ('M_MKT_CUST_LIST', '/customers/list', 'MENU', '我的客户', NULL, 2, 1, '1',
     'M_GROUP_MARKETING', 0, 'YITI', 'marketing-six-pages', 'marketing-six-pages', '本人主办客户'),
    ('M_MKT_LEAD_ENTRY', '/customers/leads/new', 'MENU', '线索录入', NULL, 3, 1, '1',
     'M_GROUP_MARKETING', 0, 'YITI', 'marketing-six-pages', 'marketing-six-pages', '手工线索和批量导入'),
    ('M_MKT_LEAD_APPR', '/customers/leads/approval', 'MENU', '线索审批', NULL, 4, 1, '1',
     'M_GROUP_MARKETING', 0, 'YITI', 'marketing-six-pages', 'marketing-six-pages', '待办和本人已办'),
    ('M_MKT_TAGS', '/customers/tags', 'MENU', '营销客户标签', NULL, 5, 1, '1',
     'M_GROUP_MARKETING', 0, 'YITI', 'marketing-six-pages', 'marketing-six-pages', '标签及客户群管理'),
    ('M_MKT_TAG_APPR', '/customers/tags/approval', 'MENU', '标签客户审核', NULL, 6, 1, '1',
     'M_GROUP_MARKETING', 0, 'YITI', 'marketing-six-pages', 'marketing-six-pages', '标签和标签客户审核') AS new
ON DUPLICATE KEY UPDATE
    RESOURCE_URL = new.RESOURCE_URL,
    RESOURCE_METHOD = new.RESOURCE_METHOD,
    MENU_NAME = new.MENU_NAME,
    MENU_RANK_NO = new.MENU_RANK_NO,
    ISMENU = new.ISMENU,
    MENU_ENDFLAG = new.MENU_ENDFLAG,
    PARENT_RESOURCE_ID = new.PARENT_RESOURCE_ID,
    STATUS = new.STATUS,
    SYS_CODE = new.SYS_CODE,
    UPDATE_TIME = NOW(),
    UPDATE_USER = new.UPDATE_USER,
    REMARK = new.REMARK;

-- 同一页面下的子路径按 HTTP 方法分资源；服务层再由 @BizAuth 校验业务动作和数据范围。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('C_MKC_LIST', '/api/marketing/customers', 'GET', '营销客户总列表', NULL, 0, 0, '0', 'M_MKT_CUST_MGMT', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面一'),
    ('C_MKC_MINE', '/api/marketing/customers/mine', 'GET', '我的客户列表', NULL, 0, 0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面二'),
    ('C_MKC_GET', '/api/marketing/customers/*', 'GET', '营销客户详情', NULL, 10, 0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面一二共用；不覆盖总列表根路径'),
    ('C_MKC_PUT', '/api/marketing/customers/**', 'PUT', '修改营销客户资料', NULL, 10, 0, '0', 'M_MKT_CUST_MGMT', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '管理员操作'),
    ('C_MKC_POST', '/api/marketing/customers/**', 'POST', '转交或恢复客户主办权', NULL, 10, 0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面一二共用'),

    ('C_ML_LIST', '/api/marketing/leads', 'GET', '本人线索记录', NULL, 0, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_ML_GET', '/api/marketing/leads/**', 'GET', '线索详情与客户反显', NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_ML_CREATE', '/api/marketing/leads', 'POST', '创建线索', NULL, 0, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_ML_POST', '/api/marketing/leads/**', 'POST', '提交或取消线索', NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_ML_PUT', '/api/marketing/leads/**', 'PUT', '编辑线索', NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_ML_DELETE', '/api/marketing/leads/**', 'DELETE', '取消草稿线索', NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_MLI_GET', '/api/marketing/lead-import-batches/**', 'GET', '线索导入查询与下载', NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),
    ('C_MLI_POST', '/api/marketing/lead-import-batches/**', 'POST', '预览、导入与确认线索', NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面三'),

    ('C_MLA_GET', '/api/marketing/lead-approvals/**', 'GET', '线索审批待办已办', NULL, 10, 0, '0', 'M_MKT_LEAD_APPR', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面四'),
    ('C_MLA_POST', '/api/marketing/lead-approvals/**', 'POST', '线索审批通过或驳回', NULL, 10, 0, '0', 'M_MKT_LEAD_APPR', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面四'),

    ('C_MCT_LIST', '/api/marketing/customer-tags', 'GET', '营销客户标签列表', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面五'),
    ('C_MCT_GET', '/api/marketing/customer-tags/**', 'GET', '标签和客户群详情', NULL, 10, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面五'),
    ('C_MCT_CREATE', '/api/marketing/customer-tags', 'POST', '新增营销客户标签', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面五'),
    ('C_MCT_PUT', '/api/marketing/customer-tags/**', 'PUT', '修改营销客户标签', NULL, 10, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面五'),
    ('C_MTI_GET', '/api/marketing/customer-tag-import-batches/**', 'GET', '标签客户导入查询与下载', NULL, 10, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面五'),
    ('C_MTI_POST', '/api/marketing/customer-tag-import-batches/**', 'POST', '标签客户预览、导入或取消', NULL, 10, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面五'),

    ('C_MTA_GET', '/api/marketing/customer-tag-approvals/**', 'GET', '标签客户审核待办已办', NULL, 10, 0, '0', 'M_MKT_TAG_APPR', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面六'),
    ('C_MTA_POST', '/api/marketing/customer-tag-approvals/**', 'POST', '标签及客户审核', NULL, 10, 0, '0', 'M_MKT_TAG_APPR', 0, 'CUSTOMER', 'marketing-six-pages', 'marketing-six-pages', '页面六') AS new
ON DUPLICATE KEY UPDATE
    RESOURCE_URL = new.RESOURCE_URL,
    RESOURCE_METHOD = new.RESOURCE_METHOD,
    MENU_NAME = new.MENU_NAME,
    PARENT_RESOURCE_ID = new.PARENT_RESOURCE_ID,
    STATUS = new.STATUS,
    SYS_CODE = new.SYS_CODE,
    UPDATE_TIME = NOW(),
    UPDATE_USER = new.UPDATE_USER,
    REMARK = new.REMARK;

-- SYS_ADMIN 获得全部新资源。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(r.ROLE_ID, '#', res.RESOURCE_ID)), r.ROLE_ID, res.RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE r
JOIN PT_RESOURCE res ON res.RESOURCE_ID IN (
    'M_MKT_CUST_MGMT','M_MKT_CUST_LIST','M_MKT_LEAD_ENTRY','M_MKT_LEAD_APPR','M_MKT_TAGS','M_MKT_TAG_APPR',
    'C_MKC_LIST','C_MKC_MINE','C_MKC_GET','C_MKC_PUT','C_MKC_POST',
    'C_ML_LIST','C_ML_GET','C_ML_CREATE','C_ML_POST','C_ML_PUT','C_ML_DELETE','C_MLI_GET','C_MLI_POST',
    'C_MLA_GET','C_MLA_POST','C_MCT_LIST','C_MCT_GET','C_MCT_CREATE','C_MCT_PUT','C_MTI_GET','C_MTI_POST',
    'C_MTA_GET','C_MTA_POST')
WHERE r.ROLE_CODE = 'SYS_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE rr
                  WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = res.RESOURCE_ID);

-- 客户经理：我的客户和线索录入，不授予总列表和审批页。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(r.ROLE_ID, '#', res.RESOURCE_ID)), r.ROLE_ID, res.RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE r
JOIN PT_RESOURCE res ON res.RESOURCE_ID IN (
    'M_MKT_CUST_LIST','M_MKT_LEAD_ENTRY','C_MKC_MINE','C_MKC_GET','C_MKC_POST',
    'C_ML_LIST','C_ML_GET','C_ML_CREATE','C_ML_POST','C_ML_PUT','C_ML_DELETE','C_MLI_GET','C_MLI_POST')
WHERE r.ROLE_CODE = 'CUST_MARKETING_MANAGER'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE rr
                  WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = res.RESOURCE_ID);

-- 线索审批人：仅页面四。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(r.ROLE_ID, '#', res.RESOURCE_ID)), r.ROLE_ID, res.RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE r
JOIN PT_RESOURCE res ON res.RESOURCE_ID IN ('M_MKT_LEAD_APPR','C_MLA_GET','C_MLA_POST')
WHERE r.ROLE_CODE = 'CUST_LEAD_APPROVER'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE rr
                  WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = res.RESOURCE_ID);

-- 标签页按原菜单已授权角色继承新 API，不新增角色或人员授权。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(rr.ROLE_ID, '#', res.RESOURCE_ID)), rr.ROLE_ID, res.RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE_RESOURCE rr
JOIN PT_RESOURCE res ON res.RESOURCE_ID IN
    ('C_MCT_LIST','C_MCT_GET','C_MCT_CREATE','C_MCT_PUT','C_MTI_GET','C_MTI_POST')
WHERE rr.RESOURCE_ID = 'M_MKT_TAGS'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE existing
                  WHERE existing.ROLE_ID = rr.ROLE_ID AND existing.RESOURCE_ID = res.RESOURCE_ID);

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(rr.ROLE_ID, '#', res.RESOURCE_ID)), rr.ROLE_ID, res.RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE_RESOURCE rr
JOIN PT_RESOURCE res ON res.RESOURCE_ID IN ('C_MTA_GET','C_MTA_POST')
WHERE rr.RESOURCE_ID = 'M_MKT_TAG_APPR'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE existing
                  WHERE existing.ROLE_ID = rr.ROLE_ID AND existing.RESOURCE_ID = res.RESOURCE_ID);

COMMIT;

-- 验收查询。
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, PARENT_RESOURCE_ID, STATUS
FROM PT_RESOURCE
WHERE RESOURCE_ID IN ('M_MKT_CUST_MGMT','M_MKT_CUST_LIST','M_MKT_LEAD_ENTRY','M_MKT_LEAD_APPR','M_MKT_TAGS','M_MKT_TAG_APPR')
   OR RESOURCE_URL LIKE '/api/marketing/%'
ORDER BY ISMENU DESC, MENU_RANK_NO, RESOURCE_ID;

SELECT r.ROLE_CODE, res.RESOURCE_ID, res.RESOURCE_URL, res.RESOURCE_METHOD
FROM PT_ROLE_RESOURCE rr
JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
JOIN PT_RESOURCE res ON res.RESOURCE_ID = rr.RESOURCE_ID
WHERE r.ROLE_CODE IN ('SYS_ADMIN','CUST_MARKETING_MANAGER','CUST_LEAD_APPROVER')
  AND (res.RESOURCE_ID LIKE 'M_MKT_%' OR res.RESOURCE_URL LIKE '/api/marketing/%')
ORDER BY r.ROLE_CODE, res.ISMENU DESC, res.RESOURCE_ID;
