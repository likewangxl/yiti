-- customer-marketing-center 客户主档域 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-14
-- 模块: customer-marketing-center
-- 业务域: 客户主档管理（CUSTOMER）
-- 注意: RESOURCE_ID 长度 <= 20，路径变量使用 AntPath * 通配

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
-- 客户主档查询
('C_CUST_LIST',         '/api/customers',                       'GET',  '客户主档列表',     0, 'CUSTOMER'),
('C_CUST_DETAIL',       '/api/customers/*',                     'GET',  '客户主档详情',     0, 'CUSTOMER'),

-- 客户维护人转交（高危操作，独立 URL）
('C_CUST_TRANSFER',     '/api/customers/*/claims/*/transfer',   'POST', '转交维护人',       0, 'CUSTOMER'),

-- 客户删除申请（触发审批流）
('C_CUST_DEL_APPLY',    '/api/customers/*/delete-apply',        'POST', '客户删除申请',     0, 'CUSTOMER');
