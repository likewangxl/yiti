-- ============================================================================
-- 客户营销 V2 资源重置
-- 目标：删除历史客户营销菜单/API资源及角色绑定，重建一期与客户池/触达菜单资源。
-- 注意：不删除 PT_USER_ROLE，不调整 PT_ROLE_BIZ_SCOPE，不删除任何客户业务数据。
-- 可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS TMP_CUSTOMER_RESOURCE_RESET;
CREATE TEMPORARY TABLE TMP_CUSTOMER_RESOURCE_RESET (
    RESOURCE_ID VARCHAR(20) PRIMARY KEY
);

-- 历史客户营销模块接口资源，以及“客户营销”分组下的旧子菜单。
-- M_GROUP_MARKETING 作为父菜单保留，随后统一校准属性。
INSERT IGNORE INTO TMP_CUSTOMER_RESOURCE_RESET (RESOURCE_ID)
SELECT RESOURCE_ID
FROM PT_RESOURCE
WHERE RESOURCE_ID <> 'M_GROUP_MARKETING'
  AND (
      SYS_CODE = 'CUSTOMER'
      OR RESOURCE_ID LIKE 'RES_CUST%'
      OR PARENT_RESOURCE_ID = 'M_GROUP_MARKETING'
  );

-- 先清角色资源关系，再删资源，避免留下孤儿权限。
DELETE rr
FROM PT_ROLE_RESOURCE rr
JOIN TMP_CUSTOMER_RESOURCE_RESET t ON t.RESOURCE_ID = rr.RESOURCE_ID;

DELETE r
FROM PT_RESOURCE r
JOIN TMP_CUSTOMER_RESOURCE_RESET t ON t.RESOURCE_ID = r.RESOURCE_ID;

-- 清理父菜单的历史角色绑定；后面仅恢复系统管理员绑定。
DELETE FROM PT_ROLE_RESOURCE
WHERE RESOURCE_ID = 'M_GROUP_MARKETING';

-- 校准客户营销父菜单。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('M_GROUP_MARKETING', '#group/marketing', 'MENU', '客户营销', NULL,
     2, 1, '0', NULL, 0, 'YITI',
     'customer-v2-0811', 'customer-v2-0811', '客户营销父菜单') AS new
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
    UPDATE_USER = new.UPDATE_USER,
    REMARK = new.REMARK;

-- 一期菜单：路由与 V2_DEMO 页面保持一致。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('M_MKT_CUST_LIST',  '/customers/list',           'MENU', '客户列表', NULL,
     1, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-phase1-0811', 'customer-phase1-0811', '客户营销一期'),
    ('M_MKT_LEAD_ENTRY', '/customers/leads/new',      'MENU', '线索录入', NULL,
     2, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-phase1-0811', 'customer-phase1-0811', '客户营销一期'),
    ('M_MKT_LEAD_APPR',  '/customers/leads/approval', 'MENU', '线索审批', NULL,
     3, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-phase1-0811', 'customer-phase1-0811', '客户营销一期'),
    ('M_MKT_POOL_AVAIL', '/customers/pool/available',  'MENU', '待认领客户', NULL,
     4, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('M_MKT_POOL_CLAIM', '/customers/pool/claimed',   'MENU', '已认领客户', NULL,
     5, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('M_MKT_TOUCH_MINE', '/touches/mine',             'MENU', '我的触达任务', NULL,
     6, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-v2-0811', 'customer-v2-0811', '触达任务'),
    ('M_MKT_TOUCH_OV',   '/touches/overview',         'MENU', '触达任务一览', NULL,
     7, 1, '1', 'M_GROUP_MARKETING', 0, 'YITI',
     'customer-v2-0811', 'customer-v2-0811', '触达任务管理视图');

-- 客户列表接口资源。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('C_CUST_LIST',      '/api/customers',                     'GET',    '客户列表查询',       NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_EXPORT',    '/api/customers/export',              'GET',    '客户列表导出',       NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_TRANSFER',  '/api/customers/*/claims/*/transfer', 'POST',   '转交客户维护人',     NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_DEL_APPLY', '/api/customers/*/delete-apply',      'POST',   '客户删除申请',       NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_HIST_XORG', '/api/customers/*/history',           'GET',    '客户跨机构历史查询', NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_TAG_ADD',   '/api/customers/*/tags',              'POST',   '客户追加打标',       NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_TAG_DEL',   '/api/customers/*/tags/*',            'DELETE', '客户取消单个标签',   NULL, 0,  0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '客户列表'),
    ('C_CUST_DETAIL',    '/api/customers/*',                   'GET',    '客户详情',           NULL, 10, 0, '0', 'M_MKT_CUST_LIST', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '通配详情后匹配');

-- 线索录入接口资源；字面量 GET 资源排序在通配详情之前。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('C_LEAD_LIST',      '/api/leads',                    'GET',    '线索列表',           NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_CREATE',    '/api/leads',                    'POST',   '新建线索',           NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_OWNER_Q',   '/api/leads/main-manager',       'GET',    '查询存量客户主办权', NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_EDIT_VER',  '/api/leads/edit-version',       'POST',   '创建修改版本',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_DEL_VER',   '/api/leads/delete-version',     'POST',   '创建删除版本',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_IMP_PRE',   '/api/leads/import/preview',     'POST',   '线索导入预览',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_IMP_EXEC',  '/api/leads/import/execute',     'POST',   '执行线索导入',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_IMP_BATCH', '/api/leads/import/batches/*',   'GET',    '导入批次详情',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_BATCHES',   '/api/leads/batches',            'GET',    '导入批次列表',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_TAG_ENABLED',    '/api/tags/enabled',             'GET',    '查询可用客户标签',   NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_SUBMIT',    '/api/leads/*/submit',           'POST',   '提交线索审批',       NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_VERSIONS',  '/api/leads/*/versions',         'GET',    '查询线索版本链',     NULL, 0,  0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_UPDATE',    '/api/leads/*',                  'PUT',    '编辑线索',           NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_DELETE',    '/api/leads/*',                  'DELETE', '删除线索',           NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索录入'),
    ('C_LEAD_DETAIL',    '/api/leads/*',                  'GET',    '线索详情',           NULL, 10, 0, '0', 'M_MKT_LEAD_ENTRY', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '通配详情后匹配');

-- 线索审批接口资源；审批动作继续复用工作流中心既有资源。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('C_LEAD_APPR_LIST', '/api/lead-approvals',        'GET', '线索审批待办已办', NULL, 0,  0, '0', 'M_MKT_LEAD_APPR', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索审批'),
    ('C_LEAD_APPR_EXP',  '/api/lead-approvals/export', 'GET', '导出已审批线索',   NULL, 0,  0, '0', 'M_MKT_LEAD_APPR', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '线索审批'),
    ('C_LEAD_APPR_DET',  '/api/lead-approvals/*',      'GET', '线索审批完整详情', NULL, 10, 0, '0', 'M_MKT_LEAD_APPR', 0, 'CUSTOMER', 'customer-phase1-0811', 'customer-phase1-0811', '通配详情后匹配');

-- 客户池与触达任务资源。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('C_POOL_LIST',       '/api/customer-pool',                  'GET',  '待认领客户查询', NULL, 0,  0, '0', 'M_MKT_POOL_AVAIL', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('C_CLAIM_CREATE',    '/api/claims',                         'POST', '认领客户',       NULL, 0,  0, '0', 'M_MKT_POOL_AVAIL', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('C_CLAIM_MINE',      '/api/claims/mine/customers',          'GET',  '已认领客户查询', NULL, 0,  0, '0', 'M_MKT_POOL_CLAIM', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('C_CLAIM_START',     '/api/claims/*/touch',                 'POST', '发起首次触达',   NULL, 0,  0, '0', 'M_MKT_POOL_CLAIM', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('C_CLAIM_RETOUCH',   '/api/claims/*/re-touch',              'POST', '再次发起触达',   NULL, 0,  0, '0', 'M_MKT_POOL_CLAIM', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '客户池'),
    ('C_TOUCH_MINE',      '/api/touch-tasks',                    'GET',  '我的触达任务',   NULL, 0,  0, '0', 'M_MKT_TOUCH_MINE', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达任务'),
    ('C_TOUCH_SUCCESS',   '/api/touch-tasks/*/success',          'POST', '完成触达任务',   NULL, 0,  0, '0', 'M_MKT_TOUCH_MINE', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达任务'),
    ('C_TOUCH_CANCEL',    '/api/touch-tasks/*/cancel',           'POST', '取消触达任务',   NULL, 0,  0, '0', 'M_MKT_TOUCH_MINE', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达任务'),
    ('C_TOUCH_LOG_ADD',   '/api/touch-tasks/*/logs',             'POST', '新增触达日志',   NULL, 0,  0, '0', 'M_MKT_TOUCH_MINE', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达任务'),
    ('C_TOUCH_LOG_LIST',  '/api/touch-tasks/*/logs',             'GET',  '查询触达日志',   NULL, 0,  0, '0', 'M_MKT_TOUCH_OV', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达任务一览'),
    ('C_TOUCH_DETAIL',    '/api/touch-tasks/*',                  'GET',  '触达任务详情',   NULL, 10, 0, '0', 'M_MKT_TOUCH_MINE', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '通配详情后匹配'),
    ('C_TOUCH_ADMIN_LIST','/api/admin/touch-tasks',              'GET',  '触达任务管理查询',NULL, 0,  0, '0', 'M_MKT_TOUCH_OV', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达管理'),
    ('C_TOUCH_ADMIN_SUM', '/api/admin/touch-tasks/summary',      'GET',  '触达任务汇总',   NULL, 0,  0, '0', 'M_MKT_TOUCH_OV', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达管理'),
    ('C_TOUCH_ADMIN_EXP', '/api/admin/touch-tasks/export',       'GET',  '触达任务导出',   NULL, 0,  0, '0', 'M_MKT_TOUCH_OV', 0, 'CUSTOMER', 'customer-v2-0811', 'customer-v2-0811', '触达管理');

-- 历史客户营销菜单只有 SYS_ADMIN 绑定；重置后同样仅恢复系统管理员，
-- 其他业务角色由管理员在“权限配置”页面按新菜单重新分配。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'CUSTOMER'
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'M_GROUP_MARKETING' AS RESOURCE_ID
    UNION ALL SELECT 'M_MKT_CUST_LIST'
    UNION ALL SELECT 'M_MKT_LEAD_ENTRY'
    UNION ALL SELECT 'M_MKT_LEAD_APPR'
    UNION ALL SELECT 'M_MKT_POOL_AVAIL'
    UNION ALL SELECT 'M_MKT_POOL_CLAIM'
    UNION ALL SELECT 'M_MKT_TOUCH_MINE'
    UNION ALL SELECT 'M_MKT_TOUCH_OV'
    UNION ALL SELECT 'C_CUST_LIST'
    UNION ALL SELECT 'C_CUST_EXPORT'
    UNION ALL SELECT 'C_CUST_TRANSFER'
    UNION ALL SELECT 'C_CUST_DEL_APPLY'
    UNION ALL SELECT 'C_CUST_HIST_XORG'
    UNION ALL SELECT 'C_CUST_TAG_ADD'
    UNION ALL SELECT 'C_CUST_TAG_DEL'
    UNION ALL SELECT 'C_CUST_DETAIL'
    UNION ALL SELECT 'C_LEAD_LIST'
    UNION ALL SELECT 'C_LEAD_CREATE'
    UNION ALL SELECT 'C_LEAD_OWNER_Q'
    UNION ALL SELECT 'C_LEAD_EDIT_VER'
    UNION ALL SELECT 'C_LEAD_DEL_VER'
    UNION ALL SELECT 'C_LEAD_IMP_PRE'
    UNION ALL SELECT 'C_LEAD_IMP_EXEC'
    UNION ALL SELECT 'C_LEAD_IMP_BATCH'
    UNION ALL SELECT 'C_LEAD_BATCHES'
    UNION ALL SELECT 'C_TAG_ENABLED'
    UNION ALL SELECT 'C_LEAD_SUBMIT'
    UNION ALL SELECT 'C_LEAD_VERSIONS'
    UNION ALL SELECT 'C_LEAD_UPDATE'
    UNION ALL SELECT 'C_LEAD_DELETE'
    UNION ALL SELECT 'C_LEAD_DETAIL'
    UNION ALL SELECT 'C_LEAD_APPR_LIST'
    UNION ALL SELECT 'C_LEAD_APPR_EXP'
    UNION ALL SELECT 'C_LEAD_APPR_DET'
    UNION ALL SELECT 'C_POOL_LIST'
    UNION ALL SELECT 'C_CLAIM_CREATE'
    UNION ALL SELECT 'C_CLAIM_MINE'
    UNION ALL SELECT 'C_CLAIM_START'
    UNION ALL SELECT 'C_CLAIM_RETOUCH'
    UNION ALL SELECT 'C_TOUCH_MINE'
    UNION ALL SELECT 'C_TOUCH_SUCCESS'
    UNION ALL SELECT 'C_TOUCH_CANCEL'
    UNION ALL SELECT 'C_TOUCH_LOG_ADD'
    UNION ALL SELECT 'C_TOUCH_LOG_LIST'
    UNION ALL SELECT 'C_TOUCH_DETAIL'
    UNION ALL SELECT 'C_TOUCH_ADMIN_LIST'
    UNION ALL SELECT 'C_TOUCH_ADMIN_SUM'
    UNION ALL SELECT 'C_TOUCH_ADMIN_EXP'
) x
WHERE r.ROLE_CODE = 'SYS_ADMIN';

DROP TEMPORARY TABLE TMP_CUSTOMER_RESOURCE_RESET;
COMMIT;

-- 验收查询。
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
       MENU_RANK_NO, ISMENU, PARENT_RESOURCE_ID, STATUS, SYS_CODE
FROM PT_RESOURCE
WHERE RESOURCE_ID = 'M_GROUP_MARKETING'
   OR PARENT_RESOURCE_ID IN (
       'M_GROUP_MARKETING', 'M_MKT_CUST_LIST', 'M_MKT_LEAD_ENTRY', 'M_MKT_LEAD_APPR',
       'M_MKT_POOL_AVAIL', 'M_MKT_POOL_CLAIM', 'M_MKT_TOUCH_MINE',
       'M_MKT_TOUCH_OV'
   )
ORDER BY ISMENU DESC, PARENT_RESOURCE_ID, MENU_RANK_NO, RESOURCE_ID;

SELECT r.ROLE_CODE, COUNT(*) AS RESOURCE_COUNT
FROM PT_ROLE_RESOURCE rr
JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
WHERE rr.RESOURCE_ID = 'M_GROUP_MARKETING'
   OR rr.RESOURCE_ID IN (
       SELECT RESOURCE_ID FROM PT_RESOURCE
       WHERE PARENT_RESOURCE_ID IN (
           'M_GROUP_MARKETING', 'M_MKT_CUST_LIST', 'M_MKT_LEAD_ENTRY', 'M_MKT_LEAD_APPR',
           'M_MKT_POOL_AVAIL', 'M_MKT_POOL_CLAIM', 'M_MKT_TOUCH_MINE',
           'M_MKT_TOUCH_OV'
       )
   )
GROUP BY r.ROLE_CODE;
