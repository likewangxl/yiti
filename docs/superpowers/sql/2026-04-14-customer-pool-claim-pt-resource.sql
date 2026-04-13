-- customer-marketing-center 客户池 + 认领域 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-14
-- 模块: customer-marketing-center
-- 业务域: 客户池管理（CUSTOMER_POOL）+ 认领管理（CLAIM）
-- 注意: RESOURCE_ID 长度 <= 20，路径变量使用 AntPath * 通配

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
-- 客户池管理
('C_POOL_LIST',         '/api/customer-pool',           'GET',  '客户池列表',     0, 'CUSTOMER'),

-- 认领管理
('C_CLAIM_CREATE',      '/api/claims',                  'POST', '认领客户',       0, 'CUSTOMER'),
('C_CLAIM_CANCEL',      '/api/claims/*/cancel',         'POST', '取消认领',       0, 'CUSTOMER'),
('C_CLAIM_MINE',        '/api/claims/mine',             'GET',  '我的认领列表',   0, 'CUSTOMER');
