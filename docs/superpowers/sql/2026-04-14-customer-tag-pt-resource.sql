-- customer-marketing-center 标签管理域 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-14
-- 模块: customer-marketing-center
-- 业务域: 标签管理（TAG）

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_TAG_LIST',      '/api/tags',                        'GET',  '标签列表',       0, 'CUSTOMER'),
('C_TAG_ENABLED',   '/api/tags/enabled',                'GET',  '启用标签列表',   0, 'CUSTOMER'),
('C_TAG_CREATE',    '/api/tags',                        'POST', '新增标签',       0, 'CUSTOMER'),
('C_TAG_UPDATE',    '/api/tags/*',                      'PUT',  '编辑标签',       0, 'CUSTOMER'),
('C_TAG_STATUS',    '/api/tags/*/status',               'PUT',  '标签启停',       0, 'CUSTOMER'),
('C_TAG_CUST_IMP',  '/api/tags/*/customers/import',     'POST', '标签客户导入',   0, 'CUSTOMER'),
('C_TAG_CUST_LIST', '/api/tags/*/customers',            'GET',  '标签客户列表',   0, 'CUSTOMER');
