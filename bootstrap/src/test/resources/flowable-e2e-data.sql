-- ====== FLOWABLE E2E AUTH / WORKFLOW OVERLAY ======
-- 注：共享库 PT_RESOURCE 100-105 vs LR101-105 互冲突清理已抽到 it-cleanup.sql（FU-25，2026-04-29）
-- application-flowable-e2e.yml 在 data-locations 第一个加载 it-cleanup.sql 完成前置清理。

INSERT IGNORE INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED) VALUES
    ('E10001', 'rm_zhang', '张客户经理', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'rm_zhang@test.com', 0),
    ('E20001', 'branch_wang', '王分行负责人', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'branch_wang@test.com', 0),
    ('E30001', 'corp_zhao', '赵公司部审核', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'corp_zhao@test.com', 0),
    ('E60001', 'reviewer_chen', '陈授信审查', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'reviewer_chen@test.com', 0),
    ('E60002', 'approver_he', '何授信批复', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'approver_he@test.com', 0),
    ('E90001', 'no_workflow_user', '无工作流权限用户', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'no_workflow@test.com', 0);

INSERT IGNORE INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS) VALUES
    ('R_RM', 'R_RM', '客户经理', 0),
    ('R_BRANCH_MGR', 'R_BRANCH_MGR', '经营机构负责人', 0),
    ('R_CORP_DEPT', 'R_CORP_DEPT', '公司部审核员', 0),
    ('R_CREDIT_REVIEWER', 'R_CREDIT_REVIEWER', '授信审查员', 0),
    ('R_CREDIT_APPROVER', 'R_CREDIT_APPROVER', '授信批复员', 0),
    ('R_NO_WORKFLOW', 'R_NO_WORKFLOW', '无工作流权限角色', 0);

INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS) VALUES
    ('100', '/api/workflow/tasks', 'GET', '工作流待办列表', 1, 0),
    ('101', '/api/workflow/tasks/*/claim', 'POST', '工作流任务签收', 1, 0),
    ('102', '/api/workflow/tasks/*/approve', 'POST', '工作流任务审批通过', 1, 0),
    ('103', '/api/workflow/tasks/*/reject', 'POST', '工作流任务驳回', 1, 0),
    ('104', '/api/workflow/tasks/done', 'GET', '工作流已办列表', 1, 0),
    ('105', '/api/workflow/tasks/*', 'GET', '工作流任务详情', 1, 0);

INSERT IGNORE INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN) VALUES
    ('E10001', 'R_RM', 1),
    ('E20001', 'R_BRANCH_MGR', 1),
    ('E30001', 'R_CORP_DEPT', 1),
    ('E60001', 'R_CREDIT_REVIEWER', 1),
    ('E60002', 'R_CREDIT_APPROVER', 1),
    ('E90001', 'R_NO_WORKFLOW', 1);

INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
    ('WRR001', 'R_RM', '100'), ('WRR002', 'R_RM', '104'), ('WRR003', 'R_RM', '105'),
    ('WRR004', 'R_BRANCH_MGR', '100'), ('WRR005', 'R_BRANCH_MGR', '101'), ('WRR006', 'R_BRANCH_MGR', '102'), ('WRR007', 'R_BRANCH_MGR', '103'), ('WRR008', 'R_BRANCH_MGR', '104'), ('WRR009', 'R_BRANCH_MGR', '105'),
    ('WRR010', 'R_CORP_DEPT', '100'), ('WRR011', 'R_CORP_DEPT', '101'), ('WRR012', 'R_CORP_DEPT', '102'), ('WRR013', 'R_CORP_DEPT', '103'), ('WRR014', 'R_CORP_DEPT', '104'), ('WRR015', 'R_CORP_DEPT', '105'),
    ('WRR016', 'R_CREDIT_REVIEWER', '100'), ('WRR017', 'R_CREDIT_REVIEWER', '101'), ('WRR018', 'R_CREDIT_REVIEWER', '102'), ('WRR019', 'R_CREDIT_REVIEWER', '103'), ('WRR020', 'R_CREDIT_REVIEWER', '104'), ('WRR021', 'R_CREDIT_REVIEWER', '105'),
    ('WRR022', 'R_CREDIT_APPROVER', '100'), ('WRR023', 'R_CREDIT_APPROVER', '101'), ('WRR024', 'R_CREDIT_APPROVER', '102'), ('WRR025', 'R_CREDIT_APPROVER', '103'), ('WRR026', 'R_CREDIT_APPROVER', '104'), ('WRR027', 'R_CREDIT_APPROVER', '105');

INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE) VALUES
    ('WRBS001', 'R_RM', 'LOAN', 'SELF_CREATED'),
    ('WRBS002', 'R_BRANCH_MGR', 'LOAN', 'ORG'),
    ('WRBS003', 'R_CORP_DEPT', 'LOAN', 'ALL'),
    ('WRBS004', 'R_CREDIT_REVIEWER', 'LOAN', 'ALL'),
    ('WRBS005', 'R_CREDIT_APPROVER', 'LOAN', 'ALL');

INSERT IGNORE INTO EXT_ORG_INFO (ID, ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID) VALUES
    (100, '001', '测试分行', 1, NULL),
    (101, '001001', '测试支行', 2, '001');

INSERT IGNORE INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
    ('E10001', '001001'),
    ('E20001', '001001'),
    ('E30001', '001'),
    ('E60001', '001'),
    ('E60002', '001'),
    ('E90001', '001');

INSERT IGNORE INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value) VALUES
    ('LNC001', 'loan_approve_v1', 'branch_approve', 'ROLE', '["R_BRANCH_MGR"]'),
    ('LNC002', 'loan_approve_v1', 'corp_review', 'ROLE', '["R_CORP_DEPT"]'),
    ('LNC003', 'loan_approve_v1', 'credit_check', 'ROLE', '["R_CREDIT_REVIEWER"]'),
    ('LNC004', 'loan_approve_v1', 'credit_approval', 'ROLE', '["R_CREDIT_APPROVER"]');

INSERT IGNORE INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES
    ('LTR001', 'loan_approve_v1', 'branch_approve', 48, 24),
    ('LTR002', 'loan_approve_v1', 'corp_review', 72, 24),
    ('LTR003', 'loan_approve_v1', 'credit_check', 72, 24),
    ('LTR004', 'loan_approve_v1', 'credit_approval', 48, 24);
