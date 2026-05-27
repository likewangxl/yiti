-- ============================================================
-- 内部评价模块 PT_RESOURCE 种子数据
-- 创建日期: 2026-05-27
-- 模块: performance-engine-center (PERF)
-- ============================================================

-- 19 条资源注册
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_NAME, URL_PATTERN, HTTP_METHOD, MODULE, STATUS) VALUES
('PERF_EVAL_1',  '查询标签列表',     '/api/admin/eval/tags',            'GET',    'PERF', 1),
('PERF_EVAL_2',  '新建标签',         '/api/admin/eval/tags',            'POST',   'PERF', 1),
('PERF_EVAL_3',  '编辑标签',         '/api/admin/eval/tags/*',          'PUT',    'PERF', 1),
('PERF_EVAL_4',  '删除标签',         '/api/admin/eval/tags/*',          'DELETE', 'PERF', 1),
('PERF_EVAL_5',  '查询人员标签',     '/api/admin/eval/user-tags',       'GET',    'PERF', 1),
('PERF_EVAL_6',  '绑定人员标签',     '/api/admin/eval/user-tags',       'POST',   'PERF', 1),
('PERF_EVAL_7',  '解绑人员标签',     '/api/admin/eval/user-tags',       'DELETE', 'PERF', 1),
('PERF_EVAL_8',  '查询规则列表',     '/api/admin/eval/rules',           'GET',    'PERF', 1),
('PERF_EVAL_9',  '规则详情',         '/api/admin/eval/rules/*',         'GET',    'PERF', 1),
('PERF_EVAL_10', '新建规则',         '/api/admin/eval/rules',           'POST',   'PERF', 1),
('PERF_EVAL_11', '编辑规则',         '/api/admin/eval/rules/*',         'PUT',    'PERF', 1),
('PERF_EVAL_12', '删除规则',         '/api/admin/eval/rules/*',         'DELETE', 'PERF', 1),
('PERF_EVAL_13', '查询任务列表',     '/api/admin/eval/tasks',           'GET',    'PERF', 1),
('PERF_EVAL_14', '任务详情',         '/api/admin/eval/tasks/*',         'GET',    'PERF', 1),
('PERF_EVAL_15', '发起评价任务',     '/api/admin/eval/tasks',           'POST',   'PERF', 1),
('PERF_EVAL_16', '关闭评价任务',     '/api/admin/eval/tasks/*/close',   'PUT',    'PERF', 1),
('PERF_EVAL_17', '我的待评价任务',   '/api/eval/my-tasks',              'GET',    'PERF', 1),
('PERF_EVAL_18', '待评价人员列表',   '/api/eval/my-tasks/*/targets',    'GET',    'PERF', 1),
('PERF_EVAL_19', '提交打分',         '/api/eval/scores',                'POST',   'PERF', 1);

-- R_ADMIN 角色授权全部 19 条
INSERT INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT 'R_ADMIN', RESOURCE_ID FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'PERF_EVAL_%';

-- R_BACK_TECH 角色授权全部 19 条
INSERT INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT 'R_BACK_TECH', RESOURCE_ID FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'PERF_EVAL_%';
