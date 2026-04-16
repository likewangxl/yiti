-- 线索管理域接口资源注册
-- 执行时间: 2026-04-14
-- 模块: customer-marketing-center / lead domain

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_LEAD_LIST',     '/api/leads',               'GET',    '线索列表',     0, 'CUSTOMER'),
('C_LEAD_DETAIL',   '/api/leads/*',             'GET',    '线索详情',     0, 'CUSTOMER'),
('C_LEAD_CREATE',   '/api/leads',               'POST',   '新建线索',     0, 'CUSTOMER'),
('C_LEAD_UPDATE',   '/api/leads/*',             'PUT',    '编辑线索',     0, 'CUSTOMER'),
('C_LEAD_DELETE',   '/api/leads/*',             'DELETE', '删除线索',     0, 'CUSTOMER'),
('C_LEAD_SUBMIT',   '/api/leads/*/submit',      'POST',   '提交审批',     0, 'CUSTOMER'),
('C_LEAD_EDIT_VER', '/api/leads/edit-version',  'POST',   '创建修改版本', 0, 'CUSTOMER'),
('C_LEAD_DEL_VER',  '/api/leads/delete-version','POST',   '创建删除版本', 0, 'CUSTOMER'),
('C_LEAD_IMP_PRE',  '/api/leads/import/preview','POST',   '导入预览',     0, 'CUSTOMER'),
('C_LEAD_IMP_EXEC', '/api/leads/import/execute','POST',   '执行导入',     0, 'CUSTOMER'),
('C_LEAD_BATCHES',  '/api/leads/batches',       'GET',    '导入批次列表', 0, 'CUSTOMER');
