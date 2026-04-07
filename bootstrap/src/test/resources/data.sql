-- ============================================================
-- Combined H2 Test Data for Bootstrap E2E Tests
-- Auth + Governance + Workflow
-- ============================================================

-- ====== AUTH DATA ======

INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED) VALUES
    ('admin', 'admin', '系统管理员', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@test.com', 0),
    ('user001', 'user001', '张三', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'zhangsan@test.com', 0),
    ('user002', 'user002', '李四', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'lisi@test.com', 0);

INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS) VALUES
    ('R001', 'ADMIN', '系统管理员', 0),
    ('R002', 'CUST_MANAGER', '客户经理', 0),
    ('R003', 'BRANCH_HEAD', '分行行长', 0),
    ('R004', 'AUDITOR', '审计员', 0);

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS) VALUES
    ('1', '/api/auth/login', 'POST', '登录', 1, 0),
    ('2', '/api/auth/logout', 'POST', '登出', 1, 0),
    ('3', '/api/auth/currentUser', 'GET', '当前用户', 1, 0),
    ('4', '/api/role/list', 'GET', '查询角色', 1, 0),
    ('5', '/api/role/create', 'POST', '创建角色', 1, 0),
    ('6', '/api/dict/list', 'GET', '查询字典', 1, 0),
    ('7', '/api/config/list', 'GET', '查询配置', 1, 0);

INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN) VALUES
    ('admin', 'R001', 1),
    ('user001', 'R002', 1),
    ('user002', 'R003', 1);

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
    ('RR001', 'R001', '1'), ('RR002', 'R001', '2'), ('RR003', 'R001', '3'),
    ('RR004', 'R001', '4'), ('RR005', 'R001', '5'), ('RR006', 'R001', '6'), ('RR007', 'R001', '7'),
    ('RR008', 'R002', '1'), ('RR009', 'R002', '2'), ('RR010', 'R002', '3'),
    ('RR011', 'R002', '4'), ('RR012', 'R002', '6'),
    ('RR013', 'R003', '1'), ('RR014', 'R003', '2'), ('RR015', 'R003', '3'),
    ('RR016', 'R003', '4'), ('RR017', 'R003', '6'),
    ('RR018', 'R004', '1'), ('RR019', 'R004', '2'), ('RR020', 'R004', '3'),
    ('RR021', 'R004', '6'), ('RR022', 'R004', '7');

INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE) VALUES
    ('RBS001', 'R001', 'LEAD', 'ALL'),
    ('RBS002', 'R001', 'CUSTOMER', 'ALL'),
    ('RBS003', 'R002', 'LEAD', 'SELF_CREATED'),
    ('RBS004', 'R002', 'CUSTOMER', 'SELF_CREATED'),
    ('RBS005', 'R003', 'LEAD', 'ORG'),
    ('RBS006', 'R003', 'CUSTOMER', 'ORG');

INSERT INTO EXT_ORG_INFO (ID, ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID) VALUES
    (1, 'HQ', '总行', 1, NULL),
    (2, 'BJ', '北京分行', 2, 'HQ'),
    (3, 'SH', '上海分行', 2, 'HQ'),
    (4, 'BJ_CY', '北京分行朝阳支行', 3, 'BJ'),
    (5, 'SH_PD', '上海分行浦东支行', 3, 'SH');

INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
    ('admin', 'HQ'),
    ('user001', 'BJ_CY'),
    ('user002', 'SH_PD');

-- ====== GOVERNANCE DATA ======

INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status) VALUES
    ('D001', 'INDUSTRY', 'IT', '信息技术', 'IT', 1, 'ACTIVE'),
    ('D002', 'INDUSTRY', 'FIN', '金融', 'FIN', 2, 'ACTIVE'),
    ('D003', 'STATUS', 'ACT', '激活', 'ACTIVE', 1, 'ACTIVE'),
    ('D004', 'STATUS', 'INACT', '停用', 'DISABLED', 2, 'ACTIVE');

INSERT INTO sys_config_kv (id, config_key, config_value, value_type, status) VALUES
    ('C001', 'app.name', '分行业务平台', 'STRING', 'ACTIVE'),
    ('C002', 'feature.loan.enabled', 'true', 'BOOL', 'ACTIVE'),
    ('C003', 'max.login.retry', '5', 'NUMBER', 'ACTIVE'),
    ('C004', 'notification.settings', '{"email":true,"sms":false}', 'JSON', 'ACTIVE');

INSERT INTO sys_calendar_day (day, is_workday, remark) VALUES
    ('2026-04-01', 1, NULL),
    ('2026-04-02', 1, NULL),
    ('2026-04-03', 1, NULL),
    ('2026-04-04', 0, '周六'),
    ('2026-04-05', 0, '周日'),
    ('2026-04-06', 1, NULL),
    ('2026-04-07', 1, NULL);

INSERT INTO sys_job_conf (id, job_key, job_name, cron_expr, status, allow_manual_trigger) VALUES
    ('J001', 'DAILY_REPORT', '日报生成', '0 0 8 * * ?', 'ACTIVE', 1),
    ('J002', 'MONTHLY_PERF', '月度绩效计算', '0 0 1 1 * ?', 'ACTIVE', 1);

INSERT INTO user_notification (id, emp_id, title, content, notify_type, biz_type, biz_id, is_read) VALUES
    ('N001', 'user001', '审批通知', '您有新的审批待处理', 'WORKFLOW', 'LEAD', 'L100001', 0),
    ('N002', 'user001', '系统公告', '系统将于今晚升级', 'SYSTEM', NULL, NULL, 1);

INSERT INTO audit_log (id, trace_id, emp_id, emp_name, biz_type, biz_action, resource_url, request_method, response_status, execution_time, ip_address) VALUES
    ('L001', 'trace-001', 'admin', '系统管理员', 'AUTH', 'LOGIN', '/api/auth/login', 'POST', 200, 45, '192.168.1.100'),
    ('L002', 'trace-002', 'user001', '张三', 'LEAD', 'CREATE', '/api/lead/create', 'POST', 200, 120, '192.168.1.101');

INSERT INTO file_object (id, file_name, file_size, file_type, storage_path, bucket_name, uploaded_by) VALUES
    ('F001', 'test_document.pdf', 102400, 'application/pdf', '/files/2026/03/test_document.pdf', 'branch-platform', 'user001');

INSERT INTO biz_file_rel (id, biz_type, biz_id, file_object_id, file_role) VALUES
    ('BFR001', 'LEAD', 'L100001', 'F001', 'ATTACHMENT');

-- ====== WORKFLOW DATA ======

INSERT INTO biz_process_map (id, business_key, biz_type, biz_id, process_definition_key, process_instance_id, start_user, current_assignee, process_status) VALUES
    ('MAP001', 'LEAD:L20260001', 'LEAD', 'L20260001', 'lead_approve_v1', 'PI_RUN_001', 'user001', 'user002', 'RUNNING'),
    ('MAP002', 'LEAD:L20260002', 'LEAD', 'L20260002', 'lead_approve_v1', 'PI_COMP_001', 'user002', NULL, 'COMPLETED'),
    ('MAP003', 'LEAD:L20260003', 'LEAD', 'L20260003', 'lead_approve_v1', 'PI_CAN_001', 'user001', NULL, 'CANCELLED');

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value) VALUES
    ('NC001', 'lead_approve_v1', 'dept_review', 'ROLE', '["BRANCH_HEAD"]'),
    ('NC002', 'lead_approve_v1', 'final_review', 'ROLE', '["ADMIN"]');

INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES
    ('FC001', 'lead_approve_v1', 'dept_review', '["leadName","custName","amount"]', '["amount"]', '["leadName","custName"]');

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES
    ('TR001', 'lead_approve_v1', 'dept_review', 48, 24),
    ('TR002', 'lead_approve_v1', 'final_review', 72, 36);
