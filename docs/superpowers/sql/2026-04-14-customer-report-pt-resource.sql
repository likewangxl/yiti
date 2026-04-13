-- customer-marketing-center 触达报告域 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-14
-- 模块: customer-marketing-center
-- 业务域: 触达报告（TOUCH_REPORT）
-- 注意: RESOURCE_ID 长度 <= 20，路径变量使用 AntPath * 通配

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
-- 触达报告列表（分页查询）
('C_TR_LIST',      '/api/touch-reports',            'GET',  '触达报告列表',   0, 'CUSTOMER'),

-- 触达任务状态统计
('C_TR_STAT',      '/api/touch-reports/statistics', 'GET',  '触达统计',       0, 'CUSTOMER'),

-- 触达报告导出（高危操作，独立 URL、独立授权）
('C_TR_EXPORT',    '/api/touch-reports/export',     'GET',  '触达报告导出',   0, 'CUSTOMER');
