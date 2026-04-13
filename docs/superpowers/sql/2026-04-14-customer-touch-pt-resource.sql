-- customer-marketing-center 触达任务域 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-14
-- 模块: customer-marketing-center
-- 业务域: 触达任务管理（TOUCH_TASK）
-- 注意: RESOURCE_ID 长度 <= 20，路径变量使用 AntPath * 通配

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
-- 触达任务管理
('C_TT_LIST',           '/api/touch-tasks',                 'GET',  '触达任务列表',       0, 'CUSTOMER'),
('C_TT_DETAIL',         '/api/touch-tasks/*',               'GET',  '触达任务详情',       0, 'CUSTOMER'),
('C_TT_COMPLETE',       '/api/touch-tasks/*/complete',      'POST', '完成触达任务',       0, 'CUSTOMER'),
('C_TT_CANCEL',         '/api/touch-tasks/*/cancel',        'POST', '取消触达任务',       0, 'CUSTOMER'),
('C_TT_LOG_ADD',        '/api/touch-tasks/*/logs',          'POST', '新增触达日志',       0, 'CUSTOMER'),
('C_TT_LOG_LIST',       '/api/touch-tasks/*/logs',          'GET',  '触达日志列表',       0, 'CUSTOMER');
