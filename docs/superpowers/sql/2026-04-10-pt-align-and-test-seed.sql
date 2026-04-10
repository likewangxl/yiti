-- ================================================================
-- PT_* 表与 Controller 精确对齐 + 测试用户种子数据
-- 日期: 2026-04-10
-- 权威来源: master 分支各 Controller 实际 @RequestMapping
-- 模块: auth-permission-center / system-governance-center / workflow-center
-- 总端点: AUTH 27 + GOV 32 + WF 25 = 84
-- 幂等: 整个脚本使用事务包裹，可重复执行
-- 密码: BCrypt('123456') = $2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy
-- 机构编码: 沿用现有 HQ/BJ/SH/BJ_CY/SH_PD (不使用 001/001001 方案)
-- ================================================================

START TRANSACTION;

-- ----------------------------------------------------------------
-- 1. 清理旧 PT_RESOURCE / PT_ROLE_RESOURCE (全清全建)
-- ----------------------------------------------------------------
DELETE FROM PT_ROLE_RESOURCE;
DELETE FROM PT_RESOURCE;

-- ----------------------------------------------------------------
-- 2. 插入 PT_RESOURCE (84 条 - AUTH 27 + GOV 32 + WF 25)
-- ----------------------------------------------------------------

-- ========== AUTH 模块 (27 条) ==========
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, REMARK) VALUES
-- AuthController (5)
('A_LOGIN',         '/api/auth/login',                     'POST',   '用户登录',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_LOGOUT',        '/api/auth/logout',                    'POST',   '用户登出',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_CURR_USER',     '/api/auth/current-user',              'GET',    '当前用户信息',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_PERMS',         '/api/auth/permissions',               'GET',    '当前用户权限集',  0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_CHECK_PERM',    '/api/auth/check-permission',          'POST',   '权限校验',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
-- RoleController (5) - 注意 @GetMapping("/") 表示 URL 带结尾斜杠
('A_ROLE_LIST',     '/api/admin/roles/',                   'GET',    '角色列表',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_ROLE_CREATE',   '/api/admin/roles/',                   'POST',   '创建角色',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_ROLE_UPDATE',   '/api/admin/roles/*',                  'PUT',    '更新角色',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_ROLE_DELETE',   '/api/admin/roles/*',                  'DELETE', '删除角色',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_ROLE_USERS',    '/api/admin/roles/*/users',            'GET',    '角色下用户列表',  0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
-- ResourceController (7)
('A_RES_TREE',      '/api/admin/resources/tree',           'GET',    '资源树',         0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_RES_CREATE',    '/api/admin/resources',                'POST',   '创建资源',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_RES_UPDATE',    '/api/admin/resources/*',              'PUT',    '更新资源',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_RES_DELETE',    '/api/admin/resources/*',              'DELETE', '删除资源',       0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_RR_LIST',       '/api/admin/roles/*/resources',        'GET',    '角色资源列表',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_RR_BIND',       '/api/admin/roles/*/resources',        'POST',   '增量绑定角色资源', 0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_RR_REPLACE',    '/api/admin/roles/*/resources',        'PUT',    '全量替换角色资源', 0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
-- UserRoleController (3)
('A_UR_LIST',       '/api/admin/users/*/roles',            'GET',    '用户角色列表',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_UR_BIND',       '/api/admin/users/*/roles',            'POST',   '绑定用户角色',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_UR_DEL',        '/api/admin/users/*/roles/*',          'DELETE', '解绑用户角色',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
-- OrgController (3)
('A_ORG_TREE',      '/api/orgs/tree',                      'GET',    '组织机构树',     0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_ORG_SUBTREE',   '/api/orgs/subtree',                   'GET',    '当前机构子树',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_ORG_USERS',     '/api/orgs/*/users',                   'GET',    '机构下用户',     0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
-- BizScopeController (4)
('A_BZ_LIST',       '/api/admin/biz-scopes',               'GET',    '业务范围列表',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_BZ_MATRIX',     '/api/admin/biz-scopes/matrix',        'GET',    '业务范围矩阵',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_BZ_SAVE',       '/api/admin/biz-scopes',               'POST',   '保存业务范围',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10'),
('A_BZ_DELETE',     '/api/admin/biz-scopes/*',             'DELETE', '删除业务范围',    0, 0, 'AUTH', 'seed', 'v1 aligned 2026-04-10');

-- ========== GOVERNANCE 模块 (32 条) ==========
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, REMARK) VALUES
-- DictController (2 公共)
('G_DICT_LIST',     '/api/sys/dicts',                      'GET',    '字典类型列表(公共)', 0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_DICT_ITEMS',    '/api/sys/dicts/*/items',              'GET',    '字典项列表(公共)',   0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- AdminDictController (4)
('G_DICT_CREATE',   '/api/admin/sys/dicts',                'POST',   '创建字典项',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_DICT_UPDATE',   '/api/admin/sys/dicts/*',              'PUT',    '更新字典项',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_DICT_DELETE',   '/api/admin/sys/dicts/*',              'DELETE', '删除字典项',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_DICT_STATUS',   '/api/admin/sys/dicts/*/status',       'PUT',    '启禁字典项',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- ConfigController (2)
('G_CFG_LIST',      '/api/admin/sys/configs',              'GET',    '配置列表',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_CFG_UPDATE',    '/api/admin/sys/configs/*',            'PUT',    '更新配置',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- CalendarController (4)
('G_CAL_GET',       '/api/admin/sys/calendar',             'GET',    '查询工作日',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_CAL_SET',       '/api/admin/sys/calendar/*',           'PUT',    '设置工作日',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_CAL_INIT',      '/api/admin/sys/calendar/init',        'POST',   '初始化年份',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_CAL_IMPORT',    '/api/admin/sys/calendar/import',      'POST',   '导入节假日',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- PublicCalendarController (1 公共)
('G_CAL_PUBLIC',    '/api/sys/calendar',                   'GET',    '公共日历查询',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- AuditLogController (3)
('G_AUDIT_LIST',    '/api/admin/sys/audit-logs',           'GET',    '审计日志列表',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_AUDIT_DETAIL',  '/api/admin/sys/audit-logs/*',         'GET',    '审计日志详情',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_AUDIT_EXPORT',  '/api/admin/sys/audit-logs/export',    'POST',   '导出审计日志',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- NotificationController (5)
('G_NOTIFY_LIST',   '/api/notifications',                  'GET',    '通知列表',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_NOTIFY_COUNT',  '/api/notifications/unread-count',     'GET',    '未读通知数',     0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_NOTIFY_DETAIL', '/api/notifications/*',                'GET',    '通知详情',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_NOTIFY_READ',   '/api/notifications/*/read',           'PUT',    '标记已读',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_NOTIFY_READ_ALL','/api/notifications/read-all',        'PUT',    '全部已读',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- FileController (4)
('G_FILE_UPLOAD',   '/api/files/upload',                   'POST',   '上传文件',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_FILE_DOWNLOAD', '/api/files/*/download',               'GET',    '下载文件',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_FILE_LIST',     '/api/files',                          'GET',    '业务文件列表',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_FILE_DELETE',   '/api/files/*',                        'DELETE', '删除文件',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- JobController (5)
('G_JOB_LIST',      '/api/admin/sys/jobs',                 'GET',    '任务列表',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_JOB_LOGS',      '/api/admin/sys/jobs/*/logs',          'GET',    '任务执行日志',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_JOB_TRIGGER',   '/api/admin/sys/jobs/*/trigger',       'POST',   '手动触发任务',    0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_JOB_PAUSE',     '/api/admin/sys/jobs/*/pause',         'PUT',    '暂停任务',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_JOB_RESUME',    '/api/admin/sys/jobs/*/resume',        'PUT',    '恢复任务',       0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
-- SqlProbeController (2)
('G_SQL_EXEC',      '/api/admin/sql-probe/execute',        'POST',   'SQL 执行探查',   0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10'),
('G_SQL_HIST',      '/api/admin/sql-probe/history',        'GET',    'SQL 执行历史',   0, 0, 'GOV', 'seed', 'v1 aligned 2026-04-10');

-- ========== WORKFLOW 模块 (25 条) ==========
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, REMARK) VALUES
-- TaskController (7)
('W_TASK_TODO',     '/api/workflow/tasks',                 'GET',    '待办任务列表',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TASK_DONE',     '/api/workflow/tasks/done',            'GET',    '已办任务列表',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TASK_DETAIL',   '/api/workflow/tasks/*',               'GET',    '任务详情',       0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TASK_CLAIM',    '/api/workflow/tasks/*/claim',         'POST',   '签收任务',       0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TASK_APPROVE',  '/api/workflow/tasks/*/approve',       'POST',   '审批通过',       0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TASK_REJECT',   '/api/workflow/tasks/*/reject',        'POST',   '驳回任务',       0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TASK_TRANSFER', '/api/workflow/tasks/*/transfer',      'POST',   '转交任务',       0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
-- ProcessController (4)
('W_PROC_DETAIL',   '/api/workflow/processes/*',           'GET',    '流程实例详情',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_PROC_DIAGRAM',  '/api/workflow/processes/*/diagram',   'GET',    '流程进度图',     0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_PROC_HISTORY',  '/api/workflow/processes/*/history',   'GET',    '流程历史',       0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_PROC_NODES',    '/api/workflow/processes/*/nodes',     'GET',    '流程节点结构',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
-- ProcessMapController (1)
('W_PROC_MAP',      '/api/workflow/process-map',           'GET',    '流程映射查询',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
-- WorkflowAdminController - timeout rules (4)
('W_TR_LIST',       '/api/admin/workflow/timeout-rules',   'GET',    '超时规则列表',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TR_GET',        '/api/admin/workflow/timeout-rules/item/*','GET','超时规则详情',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TR_CREATE',     '/api/admin/workflow/timeout-rules',   'POST',   '新增超时规则',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_TR_UPDATE',     '/api/admin/workflow/timeout-rules/*', 'PUT',    '更新超时规则',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
-- WorkflowAdminController - node candidates (4)
('W_NC_LIST',       '/api/admin/workflow/node-candidates', 'GET',    '候选人配置列表',  0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_NC_GET',        '/api/admin/workflow/node-candidates/item/*','GET','候选人配置详情',0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_NC_CREATE',     '/api/admin/workflow/node-candidates', 'POST',   '新增候选人配置',  0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_NC_UPDATE',     '/api/admin/workflow/node-candidates/*','PUT',   '更新候选人配置',  0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
-- WorkflowAdminController - node forms (4)
('W_NF_LIST',       '/api/admin/workflow/node-forms',      'GET',    '节点表单列表',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_NF_GET',        '/api/admin/workflow/node-forms/item/*','GET',   '节点表单详情',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_NF_CREATE',     '/api/admin/workflow/node-forms',      'POST',   '新增节点表单',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
('W_NF_UPDATE',     '/api/admin/workflow/node-forms/*',    'PUT',    '更新节点表单',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10'),
-- WorkflowAdminController - process definitions (1)
('W_PROC_DEFS',     '/api/admin/workflow/process-definitions','GET', '流程定义列表',    0, 0, 'WF', 'seed', 'v1 aligned 2026-04-10');

-- ----------------------------------------------------------------
-- 3. 插入 PT_ROLE_RESOURCE
--    R_ADMIN / R_BACK_TECH: 绑定全部 84 条 (作为双重验证管理员)
--    其他 10 个角色: 绑定 29 条公共资源
-- ----------------------------------------------------------------

-- 3.1 R_ADMIN 绑定全部 84 条
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID, SYS_CODE FROM PT_RESOURCE;

-- 3.2 R_BACK_TECH 绑定全部 84 条 (科技部门管理员，承担配置治理)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID, SYS_CODE FROM PT_RESOURCE;

-- 3.3 其他 10 个角色绑定 29 条公共资源
-- 公共资源清单: 用户上下文/组织/字典/日历/通知/文件/工作流任务/流程查看
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, p.RESOURCE_ID, p.SYS_CODE
FROM PT_ROLE r
CROSS JOIN PT_RESOURCE p
WHERE r.ROLE_ID IN (
  'R_RM', 'R_BRANCH_MGR', 'R_CORP_DEPT', 'R_RETAIL_DEPT',
  'R_BACK_FINANCE', 'R_SUPPORT_SEC', 'R_SUPPORT_STAFF',
  'R_CREDIT_REVIEWER', 'R_CREDIT_APPROVER', 'R_PRESIDENT'
)
AND p.RESOURCE_ID IN (
  -- auth basic (3)
  'A_CURR_USER', 'A_PERMS', 'A_CHECK_PERM',
  -- org (2)
  'A_ORG_TREE', 'A_ORG_SUBTREE',
  -- dict public (2)
  'G_DICT_LIST', 'G_DICT_ITEMS',
  -- calendar public (1)
  'G_CAL_PUBLIC',
  -- notifications (5)
  'G_NOTIFY_LIST', 'G_NOTIFY_COUNT', 'G_NOTIFY_DETAIL', 'G_NOTIFY_READ', 'G_NOTIFY_READ_ALL',
  -- files (4)
  'G_FILE_UPLOAD', 'G_FILE_DOWNLOAD', 'G_FILE_LIST', 'G_FILE_DELETE',
  -- workflow tasks (7)
  'W_TASK_TODO', 'W_TASK_DONE', 'W_TASK_DETAIL', 'W_TASK_CLAIM',
  'W_TASK_APPROVE', 'W_TASK_REJECT', 'W_TASK_TRANSFER',
  -- workflow process view (5)
  'W_PROC_DETAIL', 'W_PROC_DIAGRAM', 'W_PROC_HISTORY', 'W_PROC_NODES', 'W_PROC_MAP'
);

-- ----------------------------------------------------------------
-- 4. PT_ROLE_BIZ_SCOPE 补齐 (UPSERT - 不删除现有)
--    按 01-功能规格 第 3 节矩阵配置
-- ----------------------------------------------------------------

-- R_ADMIN: 全部 BizType = ALL (系统管理员)
INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, REMARK)
VALUES
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'NAV',            'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'ADDRBOOK',       'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'PRODUCT',        'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'DOC',            'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'TAG',            'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'LEAD',           'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'CUSTOMER',       'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'CUSTOMER_POOL',  'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'CLAIM',          'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'TOUCH_TASK',     'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'TOUCH_REPORT',   'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'LOAN',           'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'SUPPORT',        'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'SUPPORT_DEPT',   'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'REPORT',         'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'PERF_CONFIG',    'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'SYS_CONFIG',     'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'ORG',            'ALL', 0, 'seed', 'v1'),
-- R_RM 客户经理
(REPLACE(UUID(),'-',''), 'R_RM', 'NAV',               'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'ADDRBOOK',          'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'PRODUCT',           'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'DOC',               'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'TAG',               'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'LEAD',              'SELF_CREATED', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'CUSTOMER_POOL',     'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'CLAIM',             'ORG',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'TOUCH_TASK',        'SELF_ASSIGNED',0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'SUPPORT',           'SELF_CREATED', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'LOAN',              'SELF_CREATED', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RM', 'REPORT',            'SELF',         0, 'seed', 'v1'),
-- R_BRANCH_MGR 经营机构负责人
(REPLACE(UUID(),'-',''), 'R_BRANCH_MGR', 'NAV',       'ALL',         0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BRANCH_MGR', 'LEAD',      'ORG_SUBTREE', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BRANCH_MGR', 'CUSTOMER',  'ORG_SUBTREE', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BRANCH_MGR', 'TOUCH_REPORT','ORG_SUBTREE',0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BRANCH_MGR', 'SUPPORT',   'ORG_SUBTREE', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BRANCH_MGR', 'REPORT',    'ORG_SUBTREE', 0, 'seed', 'v1'),
-- R_CORP_DEPT 公司部
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'NAV',        'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'TAG',        'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'LEAD',       'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'CUSTOMER',   'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'TOUCH_REPORT','ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'LOAN',       'WORKFLOW_PARTICIPANT', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CORP_DEPT', 'REPORT',     'ALL', 0, 'seed', 'v1'),
-- R_RETAIL_DEPT 零售部
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'NAV',      'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'TAG',      'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'LEAD',     'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'CUSTOMER', 'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'TOUCH_REPORT','ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'LOAN',     'WORKFLOW_PARTICIPANT', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_RETAIL_DEPT', 'REPORT',   'ALL', 0, 'seed', 'v1'),
-- R_BACK_FINANCE 中后台(资财)
(REPLACE(UUID(),'-',''), 'R_BACK_FINANCE', 'NAV',         'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BACK_FINANCE', 'PERF_CONFIG', 'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BACK_FINANCE', 'REPORT',      'ALL', 0, 'seed', 'v1'),
-- R_BACK_TECH 中后台(科技)
(REPLACE(UUID(),'-',''), 'R_BACK_TECH', 'NAV',        'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BACK_TECH', 'DOC',        'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_BACK_TECH', 'SYS_CONFIG', 'ALL', 0, 'seed', 'v1'),
-- R_SUPPORT_SEC 中场支持部门秘书
(REPLACE(UUID(),'-',''), 'R_SUPPORT_SEC', 'NAV',         'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_SUPPORT_SEC', 'SUPPORT_DEPT','ORG', 0, 'seed', 'v1'),
-- R_SUPPORT_STAFF 中场支持部门人员
(REPLACE(UUID(),'-',''), 'R_SUPPORT_STAFF', 'NAV',         'ALL',          0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_SUPPORT_STAFF', 'SUPPORT_DEPT','SELF_ASSIGNED',0, 'seed', 'v1'),
-- R_CREDIT_REVIEWER 授信审查
(REPLACE(UUID(),'-',''), 'R_CREDIT_REVIEWER', 'NAV',  'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CREDIT_REVIEWER', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, 'seed', 'v1'),
-- R_CREDIT_APPROVER 授信批复
(REPLACE(UUID(),'-',''), 'R_CREDIT_APPROVER', 'NAV',  'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_CREDIT_APPROVER', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, 'seed', 'v1'),
-- R_PRESIDENT 分行行长
(REPLACE(UUID(),'-',''), 'R_PRESIDENT', 'NAV',    'ALL', 0, 'seed', 'v1'),
(REPLACE(UUID(),'-',''), 'R_PRESIDENT', 'REPORT', 'ALL', 0, 'seed', 'v1')
ON DUPLICATE KEY UPDATE DATA_SCOPE=VALUES(DATA_SCOPE), UPDATE_USER='seed', UPDATE_TIME=NOW(), REMARK=VALUES(REMARK);

-- ----------------------------------------------------------------
-- 5. 测试用户 seed (10 个新用户 + 既有 3 个 = 13 个总测试用户)
--    密码统一: 123456
-- ----------------------------------------------------------------

-- 新增 10 个测试用户
INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, ISENABLED, ISLOCKED, PASS_WRONG_COUNT, CREATE_AUTHOR, REMARK)
VALUES
('E10002', 'rm_li',        '李四(客户经理)',    '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E20001', 'branch_wang',  '王五(支行行长)',    '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E30001', 'corp_zhao',    '赵六(公司部)',      '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E30002', 'retail_sun',   '孙七(零售部)',      '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E40001', 'finance_zhou', '周八(资财)',        '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E40002', 'tech_wu',      '吴九(科技)',        '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E50001', 'sec_zheng',    '郑十(中场秘书)',    '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E50002', 'staff_qian',   '钱十一(中场人员)',  '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E60001', 'reviewer_chen','陈十二(授信审查)',  '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user'),
('E60002', 'approver_he',  '何十三(授信批复)',  '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed', 'test-user')
ON DUPLICATE KEY UPDATE
  USERNAME=VALUES(USERNAME),
  USERCHNNAME=VALUES(USERCHNNAME),
  PWD=VALUES(PWD),
  ISENABLED=VALUES(ISENABLED),
  ISLOCKED=0,
  PASS_WRONG_COUNT=0,
  UPDATE_AUTHOR='seed';

-- 用户角色绑定
INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID)
VALUES
('E10002', 'R_RM'),
('E20001', 'R_BRANCH_MGR'),
('E30001', 'R_CORP_DEPT'),
('E30002', 'R_RETAIL_DEPT'),
('E40001', 'R_BACK_FINANCE'),
('E40002', 'R_BACK_TECH'),
('E50001', 'R_SUPPORT_SEC'),
('E50002', 'R_SUPPORT_STAFF'),
('E60001', 'R_CREDIT_REVIEWER'),
('E60002', 'R_CREDIT_APPROVER')
ON DUPLICATE KEY UPDATE USER_ID=VALUES(USER_ID);

-- 用户机构绑定 (沿用 HQ/BJ/SH 机构编码)
INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE)
VALUES
('E10002', 'SH_PD'),    -- rm_li 上海浦东
('E20001', 'BJ_CY'),    -- branch_wang 北京朝阳支行
('E30001', 'HQ'),       -- corp_zhao 总行公司部
('E30002', 'HQ'),       -- retail_sun 总行零售部
('E40001', 'HQ'),       -- finance_zhou 总行资财
('E40002', 'HQ'),       -- tech_wu 总行科技
('E50001', 'HQ'),       -- sec_zheng 总行中场
('E50002', 'HQ'),       -- staff_qian 总行中场
('E60001', 'HQ'),       -- reviewer_chen 总行授信
('E60002', 'HQ')        -- approver_he 总行授信
ON DUPLICATE KEY UPDATE USER_ID=VALUES(USER_ID);

-- ----------------------------------------------------------------
-- 6. 校验统计
-- ----------------------------------------------------------------
SELECT '=== 对齐完成，统计 ===' AS msg;
SELECT 'PT_RESOURCE 总数' AS tbl, COUNT(*) AS cnt FROM PT_RESOURCE
UNION ALL SELECT 'PT_RESOURCE AUTH', COUNT(*) FROM PT_RESOURCE WHERE SYS_CODE='AUTH'
UNION ALL SELECT 'PT_RESOURCE GOV',  COUNT(*) FROM PT_RESOURCE WHERE SYS_CODE='GOV'
UNION ALL SELECT 'PT_RESOURCE WF',   COUNT(*) FROM PT_RESOURCE WHERE SYS_CODE='WF'
UNION ALL SELECT 'PT_ROLE_RESOURCE 总数', COUNT(*) FROM PT_ROLE_RESOURCE
UNION ALL SELECT 'PT_ROLE_RESOURCE R_ADMIN', COUNT(*) FROM PT_ROLE_RESOURCE WHERE ROLE_ID='R_ADMIN'
UNION ALL SELECT 'PT_ROLE_BIZ_SCOPE 总数', COUNT(*) FROM PT_ROLE_BIZ_SCOPE
UNION ALL SELECT 'PT_USER 总数', COUNT(*) FROM PT_USER
UNION ALL SELECT 'PT_USER_ROLE 总数', COUNT(*) FROM PT_USER_ROLE
UNION ALL SELECT 'EXT_USER_ORG 总数', COUNT(*) FROM EXT_USER_ORG;

COMMIT;
