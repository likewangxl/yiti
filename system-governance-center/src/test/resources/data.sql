-- ============================================================
-- H2 Test Data for Governance (system-governance-center)
-- ============================================================

-- 字典数据
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status) VALUES
    ('D001', 'INDUSTRY', 'IT', '信息技术', 'IT', 1, 'ACTIVE'),
    ('D002', 'INDUSTRY', 'FIN', '金融', 'FIN', 2, 'ACTIVE'),
    ('D003', 'INDUSTRY', 'MFG', '制造业', 'MFG', 3, 'ACTIVE'),
    ('D004', 'STATUS', 'ACT', '激活', 'ACTIVE', 1, 'ACTIVE'),
    ('D005', 'STATUS', 'INACT', '停用', 'DISABLED', 2, 'ACTIVE'),
    ('D006', 'LEAD_SOURCE', 'COLD_CALL', '陌生电呼', 'COLD_CALL', 1, 'ACTIVE'),
    ('D007', 'LEAD_SOURCE', 'REFERRAL', '客户转介', 'REFERRAL', 2, 'ACTIVE'),
    ('D008', 'LEAD_SOURCE', 'WEBSITE', '网站留言', 'WEBSITE', 3, 'ACTIVE');

-- 系统配置
INSERT INTO sys_config_kv (id, config_key, config_value, value_type, status) VALUES
    ('C001', 'app.name', '分行业务平台', 'STRING', 'ACTIVE'),
    ('C002', 'feature.loan.enabled', 'true', 'BOOL', 'ACTIVE'),
    ('C003', 'max.login.retry', '5', 'NUMBER', 'ACTIVE'),
    ('C004', 'notification.settings', '{"email":true,"sms":false}', 'JSON', 'ACTIVE'),
    ('C005', 'session.timeout', '7200', 'NUMBER', 'ACTIVE');

-- 日历数据（含工作日和休息日）
INSERT INTO sys_calendar_day (day, is_workday, remark) VALUES
    ('2026-04-01', 1, NULL),
    ('2026-04-02', 1, NULL),
    ('2026-04-03', 1, NULL),
    ('2026-04-04', 0, '周六'),
    ('2026-04-05', 0, '周日'),
    ('2026-04-06', 1, NULL),
    ('2026-04-07', 1, NULL),
    ('2026-04-08', 1, NULL),
    ('2026-04-09', 1, NULL),
    ('2026-04-10', 1, NULL),
    ('2026-04-11', 0, '周六'),
    ('2026-04-12', 0, '周日'),
    ('2026-05-01', 0, '劳动节'),
    ('2026-05-02', 0, '劳动节'),
    ('2026-05-03', 0, '劳动节');

-- 审计日志（测试查询和分页）
INSERT INTO audit_log (id, trace_id, emp_id, emp_name, biz_type, biz_action, resource_url, request_method, response_status, execution_time, ip_address) VALUES
    ('L001', 'trace-001', 'admin', '系统管理员', 'AUTH', 'LOGIN', '/api/auth/login', 'POST', 200, 45, '192.168.1.100'),
    ('L002', 'trace-002', 'user001', '张三', 'LEAD', 'CREATE', '/api/lead/create', 'POST', 200, 120, '192.168.1.101'),
    ('L003', 'trace-003', 'user002', '李四', 'LEAD', 'SUBMIT', '/api/lead/submit', 'POST', 200, 88, '192.168.1.102'),
    ('L004', 'trace-004', 'user001', '张三', 'CUST', 'CREATE', '/api/cust/create', 'POST', 500, 230, '192.168.1.101'),
    ('L005', 'trace-005', 'admin', '系统管理员', 'SYS', 'CONFIG_UPDATE', '/api/config/update', 'PUT', 200, 35, '192.168.1.100');

-- 通知数据
INSERT INTO user_notification (id, emp_id, title, content, notify_type, biz_type, biz_id, is_read) VALUES
    ('N001', 'user001', '审批通知', '您有新的线索审批待处理', 'WORKFLOW', 'LEAD', 'L100001', 0),
    ('N002', 'user001', '系统公告', '系统将于今晚22:00升级维护', 'SYSTEM', NULL, NULL, 1),
    ('N003', 'user002', '客户分配', '您被分配维护客户C100001', 'BUSINESS', 'CUSTOMER', 'C100001', 0),
    ('N004', 'user001', '审批完成', '线索L100001审批已完成', 'WORKFLOW', 'LEAD', 'L100001', 0);

-- 文件对象测试数据
INSERT INTO file_object (id, file_name, file_size, file_type, storage_path, bucket_name, uploaded_by) VALUES
    ('F001', 'test_document.pdf', 102400, 'application/pdf', '/files/2026/03/test_document.pdf', 'branch-platform', 'user001'),
    ('F002', 'customer_photo.jpg', 256000, 'image/jpeg', '/files/2026/03/customer_photo.jpg', 'branch-platform', 'user002'),
    ('F003', 'report.xlsx', 51200, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', '/files/2026/03/report.xlsx', 'branch-platform', 'admin');

-- 业务-附件关联
INSERT INTO biz_file_rel (id, biz_type, biz_id, file_object_id, file_role) VALUES
    ('BFR001', 'LEAD', 'L100001', 'F001', 'ATTACHMENT'),
    ('BFR002', 'CUSTOMER', 'C100001', 'F002', 'PHOTO'),
    ('BFR003', 'LEAD', 'L100001', 'F003', 'ATTACHMENT');

-- 任务配置
INSERT INTO sys_job_conf (id, job_key, job_name, cron_expr, status, allow_manual_trigger, remark) VALUES
    ('J001', 'DAILY_REPORT', '日报生成', '0 0 8 * * ?', 'ACTIVE', 1, '每天8点生成昨日业务日报'),
    ('J002', 'MONTHLY_PERF', '月度绩效计算', '0 0 1 1 * ?', 'ACTIVE', 1, '每月1号1点计算上月绩效'),
    ('J003', 'DATA_SYNC', '数据同步', '0 */30 * * * ?', 'PAUSED', 0, '每30分钟同步外部数据');

-- 任务执行日志
INSERT INTO sys_job_run_log (id, job_id, trigger_type, status, start_time, end_time) VALUES
    ('JRL001', 'J001', 'SCHEDULED', 'SUCCESS', '2026-04-01 08:00:00', '2026-04-01 08:00:15'),
    ('JRL002', 'J001', 'SCHEDULED', 'SUCCESS', '2026-04-02 08:00:00', '2026-04-02 08:00:12'),
    ('JRL003', 'J002', 'SCHEDULED', 'FAILED', '2026-04-01 01:00:00', '2026-04-01 01:00:45'),
    ('JRL004', 'J001', 'MANUAL', 'RUNNING', '2026-04-02 10:00:00', NULL);
