-- ============================================================
-- 评价模块 PT_RESOURCE 资源注册
-- 日期: 2026-05-27
-- 字段对齐: RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS
-- ============================================================

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS)
VALUES
('PERF_EVAL_1',  '/api/admin/eval/tags',           'GET',    '查询标签列表',   0, 0),
('PERF_EVAL_2',  '/api/admin/eval/tags',           'POST',   '新建标签',       0, 0),
('PERF_EVAL_3',  '/api/admin/eval/tags/*',         'PUT',    '编辑标签',       0, 0),
('PERF_EVAL_4',  '/api/admin/eval/tags/*',         'DELETE', '删除标签',       0, 0),
('PERF_EVAL_5',  '/api/admin/eval/user-tags',      'GET',    '查询人员标签',   0, 0),
('PERF_EVAL_6',  '/api/admin/eval/user-tags',      'POST',   '绑定人员标签',   0, 0),
('PERF_EVAL_7',  '/api/admin/eval/user-tags',      'DELETE', '解绑人员标签',   0, 0),
('PERF_EVAL_8',  '/api/admin/eval/rules',          'GET',    '查询规则列表',   0, 0),
('PERF_EVAL_9',  '/api/admin/eval/rules/*',        'GET',    '规则详情',       0, 0),
('PERF_EVAL_10', '/api/admin/eval/rules',          'POST',   '新建规则',       0, 0),
('PERF_EVAL_11', '/api/admin/eval/rules/*',        'PUT',    '编辑规则',       0, 0),
('PERF_EVAL_12', '/api/admin/eval/rules/*',        'DELETE', '删除规则',       0, 0),
('PERF_EVAL_13', '/api/admin/eval/tasks',          'GET',    '查询任务列表',   0, 0),
('PERF_EVAL_14', '/api/admin/eval/tasks/*',        'GET',    '任务详情',       0, 0),
('PERF_EVAL_15', '/api/admin/eval/tasks',          'POST',   '发起评价任务',   0, 0),
('PERF_EVAL_16', '/api/admin/eval/tasks/*/close',  'PUT',    '关闭评价任务',   0, 0),
('PERF_EVAL_17', '/api/eval/my-tasks',             'GET',    '我的待评价任务', 0, 0),
('PERF_EVAL_18', '/api/eval/my-tasks/*/targets',   'GET',    '待评价人员列表', 0, 0),
('PERF_EVAL_19', '/api/eval/scores',               'POST',   '提交打分',       0, 0);

-- R_ADMIN 角色授权全部 19 条
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'PERF_EVAL_%';

-- R_BACK_TECH 角色授权全部 19 条
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'PERF_EVAL_%';
