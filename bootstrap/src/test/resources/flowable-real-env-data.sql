SET @biz_process_map_title_ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'BIZ_PROCESS_MAP'
              AND COLUMN_NAME = 'title'
        ),
        'SELECT 1',
        'ALTER TABLE BIZ_PROCESS_MAP ADD COLUMN title VARCHAR(200) NULL COMMENT ''流程标题'' AFTER process_status'
    )
);
PREPARE biz_process_map_title_stmt FROM @biz_process_map_title_ddl;
EXECUTE biz_process_map_title_stmt;
DEALLOCATE PREPARE biz_process_map_title_stmt;

INSERT INTO PT_ROLE (
    ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE,
    CREATE_USER, UPDATE_USER, REMARK
) VALUES
    ('R_RM', 'R_RM', '客户经理', 0, 'PLATFORM', 'flowable-real-env', 'flowable-real-env', 'flowable real env role'),
    ('R_BRANCH_MGR', 'BRANCH_HEA', '经营机构负责人', 0, 'PLATFORM', 'flowable-real-env', 'flowable-real-env', 'flowable real env role'),
    ('R_CORP_DEPT', 'CORP_DEPT', '公司部人员', 0, 'PLATFORM', 'flowable-real-env', 'flowable-real-env', 'flowable real env role'),
    ('R_CREDIT_REVIEWER', 'CREDIT_REV', '授信审查人员', 0, 'PLATFORM', 'flowable-real-env', 'flowable-real-env', 'flowable real env role'),
    ('R_CREDIT_APPROVER', 'CREDIT_APP', '授信批复人员', 0, 'PLATFORM', 'flowable-real-env', 'flowable-real-env', 'flowable real env role')
ON DUPLICATE KEY UPDATE
    ROLE_CODE = VALUES(ROLE_CODE),
    ROLE_CHNAME = VALUES(ROLE_CHNAME),
    RECORD_STATUS = VALUES(RECORD_STATUS),
    SYS_CODE = VALUES(SYS_CODE),
    UPDATE_USER = VALUES(UPDATE_USER),
    REMARK = VALUES(REMARK);

INSERT INTO PT_USER (
    USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL,
    ISEXPIRED, ISLOCKED, PASS_WRONG_COUNT, ISENABLED,
    CREATE_AUTHOR, UPDATE_AUTHOR, REMARK
) VALUES
    ('E10001', 'rm_zhang', '张客户经理', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'rm_zhang@test.com', 0, 0, 0, 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env test user'),
    ('E20001', 'branch_wang', '王分行负责人', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'branch_wang@test.com', 0, 0, 0, 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env test user'),
    ('E30001', 'corp_zhao', '赵公司部审核', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'corp_zhao@test.com', 0, 0, 0, 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env test user'),
    ('E60001', 'reviewer_chen', '陈授信审查', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'reviewer_chen@test.com', 0, 0, 0, 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env test user'),
    ('E60002', 'approver_he', '何授信批复', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'approver_he@test.com', 0, 0, 0, 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env test user'),
    ('E90001', 'no_workflow_user', '无工作流权限用户', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'no_workflow@test.com', 0, 0, 0, 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env test user')
ON DUPLICATE KEY UPDATE
    USERNAME = VALUES(USERNAME),
    USERCHNNAME = VALUES(USERCHNNAME),
    PWD = VALUES(PWD),
    EMAIL = VALUES(EMAIL),
    ISEXPIRED = VALUES(ISEXPIRED),
    ISLOCKED = VALUES(ISLOCKED),
    PASS_WRONG_COUNT = VALUES(PASS_WRONG_COUNT),
    ISENABLED = VALUES(ISENABLED),
    UPDATE_AUTHOR = VALUES(UPDATE_AUTHOR),
    REMARK = VALUES(REMARK);

INSERT INTO PT_USER_ROLE (
    USER_ID, ROLE_ID, DEFAULT_ASSIGN, INHERIT_ASSIGN, GROUP_ASSING
) VALUES
    ('E10001', 'R_RM', 1, 0, 0),
    ('E20001', 'R_BRANCH_MGR', 1, 0, 0),
    ('E30001', 'R_CORP_DEPT', 1, 0, 0),
    ('E60001', 'R_CREDIT_REVIEWER', 1, 0, 0),
    ('E60002', 'R_CREDIT_APPROVER', 1, 0, 0)
ON DUPLICATE KEY UPDATE
    DEFAULT_ASSIGN = VALUES(DEFAULT_ASSIGN),
    INHERIT_ASSIGN = VALUES(INHERIT_ASSIGN),
    GROUP_ASSING = VALUES(GROUP_ASSING);

INSERT INTO PT_RESOURCE (
    RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE
) VALUES
    ('RES_WF_TODO', '/api/workflow/tasks', 'GET', '工作流待办列表', 1, 0, 'PLATFORM'),
    ('RES_WF_DONE', '/api/workflow/tasks/done', 'GET', '工作流已办列表', 1, 0, 'PLATFORM'),
    ('RES_WF_DETAIL', '/api/workflow/tasks/*', 'GET', '工作流任务详情', 1, 0, 'PLATFORM'),
    ('RES_WF_CLAIM', '/api/workflow/tasks/*/claim', 'POST', '工作流任务签收', 1, 0, 'PLATFORM'),
    ('RES_WF_APPROVE', '/api/workflow/tasks/*/approve', 'POST', '工作流任务审批通过', 1, 0, 'PLATFORM'),
    ('RES_WF_REJECT', '/api/workflow/tasks/*/reject', 'POST', '工作流任务驳回', 1, 0, 'PLATFORM'),
    ('RES_WF_TRANSFER', '/api/workflow/tasks/*/transfer', 'POST', '工作流任务转交', 1, 0, 'PLATFORM'),
    ('RES_WF_SUBMIT', '/api/workflow/processes/submit', 'POST', '工作流流程提交', 1, 0, 'PLATFORM'),
    ('RES_WF_CANCEL', '/api/workflow/processes/*/cancel', 'POST', '工作流流程撤回', 1, 0, 'PLATFORM')
ON DUPLICATE KEY UPDATE
    RESOURCE_URL = VALUES(RESOURCE_URL),
    RESOURCE_METHOD = VALUES(RESOURCE_METHOD),
    MENU_NAME = VALUES(MENU_NAME),
    ISMENU = VALUES(ISMENU),
    STATUS = VALUES(STATUS),
    SYS_CODE = VALUES(SYS_CODE);

INSERT INTO PT_ROLE_RESOURCE (
    ID, ROLE_ID, RESOURCE_ID, SYS_CODE
) VALUES
    ('FER_WF_TODO_RM', 'R_RM', 'RES_WF_TODO', 'PLATFORM'),
    ('FER_WF_DONE_RM', 'R_RM', 'RES_WF_DONE', 'PLATFORM'),
    ('FER_WF_DETAIL_RM', 'R_RM', 'RES_WF_DETAIL', 'PLATFORM'),
    ('FER_WF_CLAIM_RM', 'R_RM', 'RES_WF_CLAIM', 'PLATFORM'),
    ('FER_WF_APPROVE_RM', 'R_RM', 'RES_WF_APPROVE', 'PLATFORM'),
    ('FER_WF_REJECT_RM', 'R_RM', 'RES_WF_REJECT', 'PLATFORM'),
    ('FER_WF_TRANSFER_RM', 'R_RM', 'RES_WF_TRANSFER', 'PLATFORM'),
    ('FER_WF_SUBMIT_RM', 'R_RM', 'RES_WF_SUBMIT', 'PLATFORM'),
    ('FER_WF_CANCEL_RM', 'R_RM', 'RES_WF_CANCEL', 'PLATFORM'),
    ('FER_WF_TODO_BM', 'R_BRANCH_MGR', 'RES_WF_TODO', 'PLATFORM'),
    ('FER_WF_DONE_BM', 'R_BRANCH_MGR', 'RES_WF_DONE', 'PLATFORM'),
    ('FER_WF_DETAIL_BM', 'R_BRANCH_MGR', 'RES_WF_DETAIL', 'PLATFORM'),
    ('FER_WF_CLAIM_BM', 'R_BRANCH_MGR', 'RES_WF_CLAIM', 'PLATFORM'),
    ('FER_WF_APPROVE_BM', 'R_BRANCH_MGR', 'RES_WF_APPROVE', 'PLATFORM'),
    ('FER_WF_REJECT_BM', 'R_BRANCH_MGR', 'RES_WF_REJECT', 'PLATFORM'),
    ('FER_WF_TRANSFER_BM', 'R_BRANCH_MGR', 'RES_WF_TRANSFER', 'PLATFORM'),
    ('FER_WF_TODO_CD', 'R_CORP_DEPT', 'RES_WF_TODO', 'PLATFORM'),
    ('FER_WF_DONE_CD', 'R_CORP_DEPT', 'RES_WF_DONE', 'PLATFORM'),
    ('FER_WF_DETAIL_CD', 'R_CORP_DEPT', 'RES_WF_DETAIL', 'PLATFORM'),
    ('FER_WF_CLAIM_CD', 'R_CORP_DEPT', 'RES_WF_CLAIM', 'PLATFORM'),
    ('FER_WF_APPROVE_CD', 'R_CORP_DEPT', 'RES_WF_APPROVE', 'PLATFORM'),
    ('FER_WF_REJECT_CD', 'R_CORP_DEPT', 'RES_WF_REJECT', 'PLATFORM'),
    ('FER_WF_TRANSFER_CD', 'R_CORP_DEPT', 'RES_WF_TRANSFER', 'PLATFORM'),
    ('FER_WF_TODO_CR', 'R_CREDIT_REVIEWER', 'RES_WF_TODO', 'PLATFORM'),
    ('FER_WF_DONE_CR', 'R_CREDIT_REVIEWER', 'RES_WF_DONE', 'PLATFORM'),
    ('FER_WF_DETAIL_CR', 'R_CREDIT_REVIEWER', 'RES_WF_DETAIL', 'PLATFORM'),
    ('FER_WF_CLAIM_CR', 'R_CREDIT_REVIEWER', 'RES_WF_CLAIM', 'PLATFORM'),
    ('FER_WF_APPROVE_CR', 'R_CREDIT_REVIEWER', 'RES_WF_APPROVE', 'PLATFORM'),
    ('FER_WF_REJECT_CR', 'R_CREDIT_REVIEWER', 'RES_WF_REJECT', 'PLATFORM'),
    ('FER_WF_TRANSFER_CR', 'R_CREDIT_REVIEWER', 'RES_WF_TRANSFER', 'PLATFORM'),
    ('FER_WF_TODO_CA', 'R_CREDIT_APPROVER', 'RES_WF_TODO', 'PLATFORM'),
    ('FER_WF_DONE_CA', 'R_CREDIT_APPROVER', 'RES_WF_DONE', 'PLATFORM'),
    ('FER_WF_DETAIL_CA', 'R_CREDIT_APPROVER', 'RES_WF_DETAIL', 'PLATFORM'),
    ('FER_WF_CLAIM_CA', 'R_CREDIT_APPROVER', 'RES_WF_CLAIM', 'PLATFORM'),
    ('FER_WF_APPROVE_CA', 'R_CREDIT_APPROVER', 'RES_WF_APPROVE', 'PLATFORM'),
    ('FER_WF_REJECT_CA', 'R_CREDIT_APPROVER', 'RES_WF_REJECT', 'PLATFORM'),
    ('FER_WF_TRANSFER_CA', 'R_CREDIT_APPROVER', 'RES_WF_TRANSFER', 'PLATFORM')
ON DUPLICATE KEY UPDATE
    ROLE_ID = VALUES(ROLE_ID),
    RESOURCE_ID = VALUES(RESOURCE_ID),
    SYS_CODE = VALUES(SYS_CODE);

INSERT INTO PT_ROLE_BIZ_SCOPE (
    ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER, REMARK
) VALUES
    ('FER_SCOPE_BM_LOAN', 'R_BRANCH_MGR', 'LOAN', 'ORG', 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env scope'),
    ('FER_SCOPE_CD_LOAN', 'R_CORP_DEPT', 'LOAN', 'ALL', 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env scope'),
    ('FER_SCOPE_CR_LOAN', 'R_CREDIT_REVIEWER', 'LOAN', 'ALL', 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env scope'),
    ('FER_SCOPE_CA_LOAN', 'R_CREDIT_APPROVER', 'LOAN', 'ALL', 0, 'flowable-real-env', 'flowable-real-env', 'flowable real env scope')
ON DUPLICATE KEY UPDATE
    DATA_SCOPE = VALUES(DATA_SCOPE),
    RECORD_STATUS = VALUES(RECORD_STATUS),
    UPDATE_USER = VALUES(UPDATE_USER),
    REMARK = VALUES(REMARK);

INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
    ('E10001', 'BJ_CY'),
    ('E20001', 'BJ_CY'),
    ('E30001', 'HQ'),
    ('E60001', 'HQ'),
    ('E60002', 'HQ'),
    ('E90001', 'HQ')
ON DUPLICATE KEY UPDATE
    ORG_CODE = VALUES(ORG_CODE);

INSERT INTO EXT_ORG_INFO (
    ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID, ORGAN_STATE, CREATE_USER
) VALUES
    ('HQ', '总行', 1, NULL, 0, 'flowable-real-env'),
    ('BJ', '北京分行', 2, 'HQ', 0, 'flowable-real-env'),
    ('BJ_CY', '北京分行朝阳支行', 3, 'BJ', 0, 'flowable-real-env')
ON DUPLICATE KEY UPDATE
    ORG_NAME = VALUES(ORG_NAME),
    ORG_LEVEL = VALUES(ORG_LEVEL),
    P_ID = VALUES(P_ID),
    ORGAN_STATE = VALUES(ORGAN_STATE);

INSERT INTO WF_NODE_CANDIDATE_CONF (
    id, process_definition_key, node_key, candidate_type, candidate_value
) VALUES
    ('REALENV_LOAN_BRANCH', 'loan_approve_v1', 'branch_approve', 'ROLE', '["BRANCH_HEA"]'),
    ('REALENV_LOAN_CORP', 'loan_approve_v1', 'corp_review', 'ROLE', '["CORP_DEPT"]'),
    ('REALENV_LOAN_REVIEW', 'loan_approve_v1', 'credit_check', 'ROLE', '["CREDIT_REV"]'),
    ('REALENV_LOAN_APPROVAL', 'loan_approve_v1', 'credit_approval', 'ROLE', '["CREDIT_APP"]')
ON DUPLICATE KEY UPDATE
    candidate_value = VALUES(candidate_value),
    updated_time = CURRENT_TIMESTAMP;
