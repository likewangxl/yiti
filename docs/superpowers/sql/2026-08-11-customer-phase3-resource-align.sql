-- 客户营销 V2 第三阶段菜单、API 资源与角色权限；可重复执行。
-- 系统管理员保留全量权限，跨机构审核角色仅获得菜单、查询与审批资源。

START TRANSACTION;

-- 调整既有菜单顺序，为第三阶段菜单留出稳定位置。
UPDATE PT_RESOURCE SET MENU_RANK_NO=6 WHERE RESOURCE_ID='M_MKT_POOL_AVAIL';
UPDATE PT_RESOURCE SET MENU_RANK_NO=7 WHERE RESOURCE_ID='M_MKT_POOL_CLAIM';
UPDATE PT_RESOURCE SET MENU_RANK_NO=9 WHERE RESOURCE_ID='M_MKT_TOUCH_MINE';
UPDATE PT_RESOURCE SET MENU_RANK_NO=10 WHERE RESOURCE_ID='M_MKT_TOUCH_OV';

INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('M_MKT_TAGS', '/customers/tags', 'MENU', '客户标签管理', NULL, 4, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI', 'customer-phase3-0811', 'customer-phase3-0811', '客户营销第三阶段'),
    ('M_MKT_TAG_APPR', '/customers/tags/approval', 'MENU', '标签审核', NULL, 5, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI', 'customer-phase3-0811', 'customer-phase3-0811', '客户营销第三阶段'),
    ('M_MKT_CROSS_ORG', '/customers/cross-org', 'MENU', '跨机构营销', NULL, 8, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI', 'customer-phase3-0811', 'customer-phase3-0811', '客户营销第三阶段'),
    ('M_MKT_TRANSFER', '/customers/transfer-log', 'MENU', '客户转交记录', NULL, 11, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI', 'customer-phase3-0811', 'customer-phase3-0811', '客户营销第三阶段') AS new
ON DUPLICATE KEY UPDATE RESOURCE_URL=new.RESOURCE_URL, RESOURCE_METHOD=new.RESOURCE_METHOD,
    MENU_NAME=new.MENU_NAME, MENU_RANK_NO=new.MENU_RANK_NO, ISMENU=new.ISMENU,
    MENU_ENDFLAG=new.MENU_ENDFLAG, PARENT_RESOURCE_ID=new.PARENT_RESOURCE_ID,
    STATUS=new.STATUS, SYS_CODE=new.SYS_CODE, UPDATE_USER=new.UPDATE_USER, REMARK=new.REMARK;

INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('C_TAG_LIST', '/api/tags', 'GET', '客户标签列表', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_CREATE', '/api/tags', 'POST', '新增客户标签', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_ENABLED', '/api/tags/enabled', 'GET', '查询可用客户标签', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_UPDATE', '/api/tags/*', 'PUT', '编辑客户标签', NULL, 10, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_STATUS', '/api/tags/*/status', 'PUT', '启停客户标签', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_CUSTOMERS', '/api/tags/*/customers', 'GET', '标签客户详情', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_IMPORT', '/api/tags/*/customers/import', 'POST', '导入标签客户', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_EXPORT', '/api/tags/*/customers/export', 'GET', '导出标签客户', NULL, 0, 0, '0', 'M_MKT_TAGS', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签管理'),
    ('C_TAG_APPROVE', '/api/tags/*/approve', 'POST', '标签审核通过', NULL, 0, 0, '0', 'M_MKT_TAG_APPR', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签审核'),
    ('C_TAG_REJECT', '/api/tags/*/reject', 'POST', '标签审核退回', NULL, 0, 0, '0', 'M_MKT_TAG_APPR', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '标签审核'),
    ('C_CROSS_VALIDATE', '/api/cross-org-marketing/validate', 'GET', '跨机构营销条件校验', NULL, 0, 0, '0', 'M_MKT_CROSS_ORG', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销'),
    ('C_CROSS_LIST', '/api/cross-org-marketing', 'GET', '跨机构营销申请列表', NULL, 0, 0, '0', 'M_MKT_CROSS_ORG', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销'),
    ('C_CROSS_CREATE', '/api/cross-org-marketing', 'POST', '新建跨机构营销申请', NULL, 0, 0, '0', 'M_MKT_CROSS_ORG', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销'),
    ('C_CROSS_DETAIL', '/api/cross-org-marketing/*', 'GET', '跨机构营销申请详情', NULL, 10, 0, '0', 'M_MKT_CROSS_ORG', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销'),
    ('C_CROSS_APPROVE', '/api/cross-org-marketing/*/approve', 'POST', '跨机构营销审核通过', NULL, 0, 0, '0', 'M_MKT_CROSS_ORG', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销'),
    ('C_CROSS_REJECT', '/api/cross-org-marketing/*/reject', 'POST', '跨机构营销审核退回', NULL, 0, 0, '0', 'M_MKT_CROSS_ORG', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销'),
    ('C_TRANSFER_LIST', '/api/customer-transfers', 'GET', '客户转交记录列表', NULL, 0, 0, '0', 'M_MKT_TRANSFER', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '客户转交'),
    ('C_TRANSFER_CREATE', '/api/customer-transfers', 'POST', '发起客户转交', NULL, 0, 0, '0', 'M_MKT_TRANSFER', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '客户转交'),
    ('C_TRANSFER_CAND', '/api/customer-transfers/candidates', 'GET', '转交接收人候选', NULL, 0, 0, '0', 'M_MKT_TRANSFER', 0, 'CUSTOMER', 'customer-phase3-0811', 'customer-phase3-0811', '客户转交') AS new
ON DUPLICATE KEY UPDATE RESOURCE_URL=new.RESOURCE_URL, RESOURCE_METHOD=new.RESOURCE_METHOD,
    MENU_NAME=new.MENU_NAME, MENU_RANK_NO=new.MENU_RANK_NO, ISMENU=new.ISMENU,
    MENU_ENDFLAG=new.MENU_ENDFLAG, PARENT_RESOURCE_ID=new.PARENT_RESOURCE_ID,
    STATUS=new.STATUS, SYS_CODE=new.SYS_CODE, UPDATE_USER=new.UPDATE_USER, REMARK=new.REMARK;

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'CUSTOMER'
FROM PT_ROLE r
JOIN PT_RESOURCE x ON x.RESOURCE_ID IN (
    'M_MKT_TAGS','M_MKT_TAG_APPR','M_MKT_CROSS_ORG','M_MKT_TRANSFER',
    'C_TAG_LIST','C_TAG_CREATE','C_TAG_ENABLED','C_TAG_UPDATE','C_TAG_STATUS',
    'C_TAG_CUSTOMERS','C_TAG_IMPORT','C_TAG_EXPORT','C_TAG_APPROVE','C_TAG_REJECT',
    'C_CROSS_VALIDATE','C_CROSS_LIST','C_CROSS_CREATE','C_CROSS_DETAIL',
    'C_CROSS_APPROVE','C_CROSS_REJECT','C_TRANSFER_LIST','C_TRANSFER_CREATE','C_TRANSFER_CAND')
WHERE r.ROLE_CODE='SYS_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE rr
                  WHERE rr.ROLE_ID=r.ROLE_ID AND rr.RESOURCE_ID=x.RESOURCE_ID);

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'CUSTOMER'
FROM PT_ROLE r
JOIN PT_RESOURCE x ON x.RESOURCE_ID IN
    ('M_MKT_CROSS_ORG','C_CROSS_LIST','C_CROSS_DETAIL','C_CROSS_APPROVE','C_CROSS_REJECT')
WHERE r.ROLE_CODE IN ('CORP_DEPT','CORP_DEPT_LEADER','RETAIL_DEPT','RETAIL_DEPT_LEADER')
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE rr
                  WHERE rr.ROLE_ID=r.ROLE_ID AND rr.RESOURCE_ID=x.RESOURCE_ID);

INSERT INTO PT_ROLE_BIZ_SCOPE
    (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER, REMARK)
SELECT MD5(CONCAT(r.ROLE_ID, '#CROSS_ORG_MARKETING')), r.ROLE_ID,
       'CROSS_ORG_MARKETING', 'ALL', 0, 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销全量审批'
FROM PT_ROLE r WHERE r.ROLE_CODE='SYS_ADMIN'
ON DUPLICATE KEY UPDATE DATA_SCOPE='ALL', RECORD_STATUS=0,
    UPDATE_USER='customer-phase3-0811', REMARK='跨机构营销全量审批';

INSERT INTO PT_ROLE_BIZ_SCOPE
    (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER, REMARK)
SELECT MD5(CONCAT(r.ROLE_ID, '#CROSS_ORG_MARKETING')), r.ROLE_ID,
       'CROSS_ORG_MARKETING', 'ALL', 0, 'customer-phase3-0811', 'customer-phase3-0811', '跨机构营销审核角色全量范围'
FROM PT_ROLE r
WHERE r.ROLE_CODE IN ('CORP_DEPT','CORP_DEPT_LEADER','RETAIL_DEPT','RETAIL_DEPT_LEADER')
ON DUPLICATE KEY UPDATE DATA_SCOPE='ALL', RECORD_STATUS=0,
    UPDATE_USER='customer-phase3-0811', REMARK='跨机构营销审核角色全量范围';

COMMIT;
