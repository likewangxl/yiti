-- 浦爱云盾菜单、接口资源及系统管理员授权增量对齐。
-- 前置条件：业务表结构由 DBA 管理，本脚本不包含任何 DDL。

START TRANSACTION;

INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
  MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('M_GROUP_VIOLATION', '#group/yundun', 'MENU', '浦爱云盾', 8, 1, '0', NULL, 0, 'YD', 'system', '浦爱云盾菜单组'),
 ('M_YD_ACCOUNTABILITY', '/yundun/accountability-violations', 'MENU', '人员违规信息', 1, 1, '1', 'M_GROUP_VIOLATION', 0, 'YD', 'system', '人员违规问责信息'),
 ('M_YD_CREDIT', '/yundun/credit-violations', 'MENU', '信贷风险信息', 2, 1, '1', 'M_GROUP_VIOLATION', 0, 'YD', 'system', '信贷风险责任认定信息');

UPDATE PT_RESOURCE
SET RESOURCE_URL = '#group/yundun',
    MENU_NAME = '浦爱云盾',
    REMARK = '浦爱云盾菜单组'
WHERE RESOURCE_ID = 'M_GROUP_VIOLATION';

UPDATE PT_RESOURCE
SET PARENT_RESOURCE_ID = 'M_GROUP_VIOLATION'
WHERE RESOURCE_ID IN ('M_YD_ACCOUNTABILITY', 'M_YD_CREDIT');

INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
  MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('P_YD_ACCT_LIST', '/api/yundun/accountability-violations', 'GET', '人员违规-列表', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_GET', '/api/yundun/accountability-violations/{id:[0-9]+}', 'GET', '人员违规-详情', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_EXPORT', '/api/yundun/accountability-violations/export', 'GET', '人员违规-导出', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_ADD', '/api/yundun/accountability-violations', 'POST', '人员违规-新增', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_UPDATE', '/api/yundun/accountability-violations/{id:[0-9]+}', 'PUT', '人员违规-编辑', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_DELETE', '/api/yundun/accountability-violations/batch-delete', 'POST', '人员违规-删除', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_IMPORT', '/api/yundun/accountability-violations/import', 'POST', '人员违规-导入', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', NULL),
 ('P_YD_ACCT_IMP_TPL', '/api/yundun/accountability-violations/import-template', 'GET', '人员违规-导入模板下载', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system', '与人员违规导入权限保持一致'),
 ('P_YD_CREDIT_LIST', '/api/yundun/credit-violations', 'GET', '信贷风险-列表', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_GET', '/api/yundun/credit-violations/{id:[0-9]+}', 'GET', '信贷风险-详情', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_EXPORT', '/api/yundun/credit-violations/export', 'GET', '信贷风险-导出', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_ADD', '/api/yundun/credit-violations', 'POST', '信贷风险-新增', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_UPDATE', '/api/yundun/credit-violations/{id:[0-9]+}', 'PUT', '信贷风险-编辑', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_DELETE', '/api/yundun/credit-violations/batch-delete', 'POST', '信贷风险-删除', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_IMPORT', '/api/yundun/credit-violations/import', 'POST', '信贷风险-导入', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', NULL),
 ('P_YD_CREDIT_IMP_TPL', '/api/yundun/credit-violations/import-template', 'GET', '信贷风险-导入模板下载', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system', '与信贷风险导入权限保持一致');

INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT SUBSTRING(MD5(CONCAT(r.ROLE_ID, '|', x.RESOURCE_ID)), 1, 16),
       r.ROLE_ID, x.RESOURCE_ID, 'YD', NOW()
FROM PT_ROLE r
CROSS JOIN (
  SELECT 'M_GROUP_VIOLATION' RESOURCE_ID UNION ALL
  SELECT 'M_YD_ACCOUNTABILITY' UNION ALL SELECT 'M_YD_CREDIT' UNION ALL
  SELECT 'P_YD_ACCT_LIST' UNION ALL SELECT 'P_YD_ACCT_GET' UNION ALL SELECT 'P_YD_ACCT_EXPORT' UNION ALL
  SELECT 'P_YD_ACCT_ADD' UNION ALL SELECT 'P_YD_ACCT_UPDATE' UNION ALL
  SELECT 'P_YD_ACCT_DELETE' UNION ALL SELECT 'P_YD_ACCT_IMPORT' UNION ALL SELECT 'P_YD_ACCT_IMP_TPL' UNION ALL
  SELECT 'P_YD_CREDIT_LIST' UNION ALL SELECT 'P_YD_CREDIT_GET' UNION ALL SELECT 'P_YD_CREDIT_EXPORT' UNION ALL
  SELECT 'P_YD_CREDIT_ADD' UNION ALL SELECT 'P_YD_CREDIT_UPDATE' UNION ALL
  SELECT 'P_YD_CREDIT_DELETE' UNION ALL SELECT 'P_YD_CREDIT_IMPORT' UNION ALL SELECT 'P_YD_CREDIT_IMP_TPL'
) x
WHERE r.ROLE_CODE = 'SYS_ADMIN';

INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE
 (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, REMARK)
SELECT SUBSTRING(MD5(CONCAT(r.ROLE_ID, '|VIOLATION')), 1, 16),
       r.ROLE_ID, 'VIOLATION', 'ALL', 0, 'system', '系统管理员违规管理全量范围'
FROM PT_ROLE r
WHERE r.ROLE_CODE = 'SYS_ADMIN';

COMMIT;
