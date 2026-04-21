-- customer-marketing-center 契约对齐补充 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-21
-- 模块: customer-marketing-center
-- 业务域: 契约对齐新增端点 (跨机构历史 / 打标 / 导出 / 版本链 / 管理后台 / 状态端点改名)
-- 注意: RESOURCE_ID 长度 <= 20，路径变量使用 AntPath * 通配
-- 前置条件: 已执行 2026-04-14-customer-touch-pt-resource.sql (含 C_TT_COMPLETE)

-- ============================================================
-- 1. 客户跨机构全量历史查询（高危 / 独立审计 / CROSS_ORG 类型）
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_CUST_HIST_XORG',  '/api/customers/*/history',             'GET',    '客户跨机构历史查询',     0, 'CUSTOMER');

-- ============================================================
-- 2. 客户打标（追加 / 取消单个标签）
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_CUST_TAG_ADD',    '/api/customers/*/tags',                'POST',   '客户追加打标',           0, 'CUSTOMER'),
('C_CUST_TAG_DEL',    '/api/customers/*/tags/*',              'DELETE',  '客户取消单个标签',       0, 'CUSTOMER');

-- ============================================================
-- 3. 导出接口（高危，超过 5000 行须异步）
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_CUST_EXPORT',     '/api/customers/export',                'GET',    '客户列表导出',           0, 'CUSTOMER'),
('C_TAG_CUST_EXPORT', '/api/tags/*/customers/export',         'GET',    '标签客户导出',           0, 'CUSTOMER');

-- ============================================================
-- 4. 线索版本链查询
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_LEAD_VERSIONS',   '/api/leads/*/versions',                'GET',    '查询线索版本链',         0, 'CUSTOMER');

-- ============================================================
-- 5. 触达状态端点改名: /complete → /success
--    原 C_TT_COMPLETE 记录更新为新路径和新 ID
-- ============================================================
UPDATE PT_RESOURCE
SET RESOURCE_URL = '/api/touch-tasks/*/success',
    RESOURCE_ID  = 'C_TT_SUCCESS',
    MENU_NAME    = '标记触达任务成功'
WHERE RESOURCE_ID = 'C_TT_COMPLETE';

-- ============================================================
-- 6. 管理后台触达任务（高危 / ADMIN_ONLY）
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_ADM_TT_LIST',     '/api/admin/touch-tasks',               'GET',    '管理后台触达任务列表',   0, 'CUSTOMER'),
('C_ADM_TT_EXPORT',   '/api/admin/touch-tasks/export',        'GET',    '管理后台触达任务导出',   0, 'CUSTOMER'),
('C_ADM_TT_ASSIGN',   '/api/admin/touch-tasks/batch-assign',  'POST',   '批量分配触达任务',       0, 'CUSTOMER');
