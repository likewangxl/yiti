-- ============================================================
-- API 测试用测试数据 (dev/test 环境初始化)
-- 用途: 手动测试 API 接口时初始化数据库
-- 使用: mysql -uroot -p123456 onepl < docs/testing/test-data.sql
-- ============================================================

-- ====== AUTH 测试数据 ======

-- 用户表（密码是 BCrypt 加密后的 "password"）
-- BCrypt hash: $2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy
INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED, ISEXPIRED, ISLOCKED, PASS_WRONG_COUNT) VALUES
    ('admin', 'admin', '系统管理员', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'admin@test.com', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE USERNAME=VALUES(USERNAME), PWD=VALUES(PWD);

INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED, ISEXPIRED, ISLOCKED, PASS_WRONG_COUNT) VALUES
    ('user001', 'user001', '张三', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'zhangsan@test.com', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE USERNAME=VALUES(USERNAME), PWD=VALUES(PWD);

INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED, ISEXPIRED, ISLOCKED, PASS_WRONG_COUNT) VALUES
    ('user002', 'user002', '李四', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'lisi@test.com', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE USERNAME=VALUES(USERNAME), PWD=VALUES(PWD);

-- 角色表（V1规范角色已在seed-v1.sql中定义，此处仅记录旧测试数据中曾用过的角色ID用于参考）
-- INSERT IGNORE INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE) VALUES
--     ('R001', 'ADMIN', '系统管理员(旧)', 0, 'PLATFORM'),  -- 已迁移到 R_ADMIN
--     ('R002', 'CUST_MGR', '客户经理(旧)', 0, 'PLATFORM'), -- 已迁移到 R_RM
--     ('R003', 'BRANCH_HD', '分行行长(旧)', 0, 'PLATFORM'), -- 已迁移到 R_PRESIDENT
--     ('R004', 'AUDITOR', '审计员(旧)', 0, 'PLATFORM');   -- 已迁移到 R_BACK_TECH

-- 资源表（INSERT IGNORE：V1规范资源已在seed-v1.sql中，此处仅确保基础测试资源存在）
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, STATUS, SYS_CODE) VALUES
    ('1', '/api/auth/login', 'POST', '登录', 0, 1, 0, 'PLATFORM'),
    ('2', '/api/auth/logout', 'POST', '登出', 0, 1, 0, 'PLATFORM'),
    ('3', '/api/auth/currentUser', 'GET', '当前用户', 0, 1, 0, 'PLATFORM'),
    ('4', '/api/role/list', 'GET', '查询角色', 0, 1, 0, 'PLATFORM'),
    ('5', '/api/role/create', 'POST', '创建角色', 0, 1, 0, 'PLATFORM'),
    ('6', '/api/dict/list', 'GET', '查询字典', 0, 1, 0, 'PLATFORM'),
    ('7', '/api/config/list', 'GET', '查询配置', 0, 1, 0, 'PLATFORM'),
    ('8', '/api/sys/dicts/**', 'GET', '字典查询', 0, 1, 0, 'PLATFORM'),
    ('9', '/api/orgs/tree', 'GET', '组织架构', 0, 1, 0, 'PLATFORM'),
    ('10', '/api/orgs/**', 'GET', '组织查询', 0, 1, 0, 'PLATFORM')
ON DUPLICATE KEY UPDATE MENU_NAME=VALUES(MENU_NAME);

-- 用户角色关联（V1规范角色ID：R_ADMIN=系统管理员，R_RM=客户经理，R_PRESIDENT=分行行长）
INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN) VALUES
    ('admin', 'R_ADMIN', 1),
    ('user001', 'R_RM', 1),
    ('user002', 'R_PRESIDENT', 1)
ON DUPLICATE KEY UPDATE DEFAULT_ASSIGN=VALUES(DEFAULT_ASSIGN);

-- 角色资源关联（V1规范资源ID已在seed-v1.sql中定义，此处仅补充测试所需的核心资源绑定）
-- 使用 INSERT IGNORE 避免与seed-v1.sql重复绑定冲突
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
    ('RR_T01', 'R_ADMIN',     '1',               'PLATFORM', NOW()),
    ('RR_T02', 'R_ADMIN',     '2',               'PLATFORM', NOW()),
    ('RR_T03', 'R_ADMIN',     'RES_AUTH_CURRENT','PLATFORM', NOW()),
    ('RR_T04', 'R_RM',        '1',               'PLATFORM', NOW()),
    ('RR_T05', 'R_RM',        '2',               'PLATFORM', NOW()),
    ('RR_T06', 'R_RM',        'RES_AUTH_CURRENT','PLATFORM', NOW()),
    ('RR_T07', 'R_PRESIDENT', '1',               'PLATFORM', NOW()),
    ('RR_T08', 'R_PRESIDENT', '2',               'PLATFORM', NOW()),
    ('RR_T09', 'R_PRESIDENT', 'RES_AUTH_CURRENT','PLATFORM', NOW())
ON DUPLICATE KEY UPDATE ROLE_ID=VALUES(ROLE_ID);

-- 角色业务范围（V1规范角色ID：R_ADMIN=ALL，R_RM=SELF_CREATED，SELF=ORG_SUBTREE，R_PRESIDENT=ORG_SUBTREE）
INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK) VALUES
    ('RBS_T01', 'R_ADMIN',     'LEAD',        'ALL', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T02', 'R_ADMIN',     'CUSTOMER',    'ALL', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T03', 'R_ADMIN',     'LOAN',        'ALL', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T04', 'R_ADMIN',     'SUPPORT',     'ALL', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T05', 'R_RM',        'LEAD',        'SELF_CREATED', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T06', 'R_RM',        'CUSTOMER',    'SELF_CREATED', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T07', 'R_RM',        'SUPPORT',     'SELF_CREATED', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T08', 'R_PRESIDENT', 'LEAD',        'ORG_SUBTREE', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T09', 'R_PRESIDENT', 'CUSTOMER',    'ORG_SUBTREE', 0, NOW(), 'seed', '测试数据'),
    ('RBS_T10', 'R_PRESIDENT', 'REPORT',      'ORG_SUBTREE', 0, NOW(), 'seed', '测试数据')
ON DUPLICATE KEY UPDATE DATA_SCOPE=VALUES(DATA_SCOPE);

-- 机构数据
INSERT INTO EXT_ORG_INFO (ID, ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID, ORGAN_STATE) VALUES
    (1, 'HQ', '总行', 1, NULL, 0),
    (2, 'BJ', '北京分行', 2, 'HQ', 0),
    (3, 'SH', '上海分行', 2, 'HQ', 0),
    (4, 'BJ_CY', '北京分行朝阳支行', 3, 'BJ', 0),
    (5, 'SH_PD', '上海分行浦东支行', 3, 'SH', 0)
ON DUPLICATE KEY UPDATE ORG_NAME=VALUES(ORG_NAME);

-- 用户机构关联
INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
    ('admin', 'HQ'),
    ('user001', 'BJ_CY'),
    ('user002', 'SH_PD')
ON DUPLICATE KEY UPDATE ORG_CODE=VALUES(ORG_CODE);

-- ====== GOVERNANCE 测试数据 ======

INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status) VALUES
    ('D001', 'INDUSTRY', 'IT', '信息技术', 'IT', 1, 'ACTIVE'),
    ('D002', 'INDUSTRY', 'FIN', '金融', 'FIN', 2, 'ACTIVE'),
    ('D003', 'STATUS', 'ACT', '激活', 'ACTIVE', 1, 'ACTIVE'),
    ('D004', 'STATUS', 'INACT', '停用', 'DISABLED', 2, 'ACTIVE')
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label);

INSERT INTO sys_config_kv (id, config_key, config_value, value_type, status) VALUES
    ('C001', 'app.name', '分行业务平台', 'STRING', 'ACTIVE'),
    ('C002', 'feature.loan.enabled', 'true', 'BOOL', 'ACTIVE'),
    ('C003', 'max.login.retry', '5', 'NUMBER', 'ACTIVE'),
    ('C004', 'notification.settings', '{"email":true,"sms":false}', 'JSON', 'ACTIVE')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);

INSERT INTO sys_calendar_day (`day`, is_workday, remark) VALUES
    ('2026-04-01', 1, NULL),
    ('2026-04-02', 1, NULL),
    ('2026-04-03', 1, NULL),
    ('2026-04-04', 0, '周六'),
    ('2026-04-05', 0, '周日'),
    ('2026-04-06', 1, NULL),
    ('2026-04-07', 1, NULL)
ON DUPLICATE KEY UPDATE is_workday=VALUES(is_workday);

INSERT INTO sys_job_conf (id, job_key, job_name, cron_expr, status, allow_manual_trigger) VALUES
    ('J001', 'DAILY_REPORT', '日报生成', '0 0 8 * * ?', 'ACTIVE', 1),
    ('J002', 'MONTHLY_PERF', '月度绩效计算', '0 0 1 1 * ?', 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE job_name=VALUES(job_name);

INSERT INTO user_notification (id, emp_id, title, content, notify_type, biz_type, biz_id, is_read) VALUES
    ('N001', 'user001', '审批通知', '您有新的审批待处理', 'WORKFLOW', 'LEAD', 'L100001', 0),
    ('N002', 'user001', '系统公告', '系统将于今晚升级', 'SYSTEM', NULL, NULL, 1)
ON DUPLICATE KEY UPDATE is_read=VALUES(is_read);

-- ====== WORKFLOW 测试数据 ======

INSERT INTO biz_process_map (id, business_key, biz_type, biz_id, process_definition_key, process_instance_id, start_user, current_assignee, process_status) VALUES
    ('MAP001', 'LEAD:L20260001', 'LEAD', 'L20260001', 'lead_approve_v1', 'PI_RUN_001', 'user001', 'user002', 'RUNNING'),
    ('MAP002', 'LEAD:L20260002', 'LEAD', 'L20260002', 'lead_approve_v1', 'PI_COMP_001', 'user002', NULL, 'COMPLETED'),
    ('MAP003', 'LEAD:L20260003', 'LEAD', 'L20260003', 'lead_approve_v1', 'PI_CAN_001', 'user001', NULL, 'CANCELLED')
ON DUPLICATE KEY UPDATE process_status=VALUES(process_status);

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value) VALUES
    ('NC001', 'lead_approve_v1', 'dept_review', 'ROLE', '["BRANCH_HD"]'),
    ('NC002', 'lead_approve_v1', 'final_review', 'ROLE', '["ADMIN"]')
ON DUPLICATE KEY UPDATE candidate_value=VALUES(candidate_value);

INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES
    ('FC001', 'lead_approve_v1', 'dept_review', '["leadName","custName","amount"]', '["amount"]', '["leadName","custName"]')
ON DUPLICATE KEY UPDATE form_fields=VALUES(form_fields);

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES
    ('TR001', 'lead_approve_v1', 'dept_review', 48, 24),
    ('TR002', 'lead_approve_v1', 'final_review', 72, 36)
ON DUPLICATE KEY UPDATE timeout_hours=VALUES(timeout_hours);
