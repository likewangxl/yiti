-- ============================================================
-- Lead E2E Data
-- Phase 2 (b) lead_approve_v1 真 BPMN 端到端 IT 用
--   - 1 个发起人（客户经理 rm_li）+ 1 个分行经理审批人（branch_mgr_qian）
--   - PT_RESOURCE 注册 lead 接口 + workflow 任务接口
--   - wf_node_candidate_conf 配置 lead_approve_v1 / branch_manager_approve 节点候选人
-- 沿用 flowable-e2e 已验证密码组合：
--   明文 password="password"，BCrypt hash=$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy
-- ============================================================

-- ====== PT_USER：发起人 + 审批人 ======
INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED) VALUES
    ('LE10001', 'rm_li', '李客户经理', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'rm_li@test.com', 0),
    ('LE20001', 'branch_mgr_qian', '钱分行经理', '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 'branch_mgr_qian@test.com', 0);

-- ====== PT_ROLE：客户经理（发起人）+ 分行经理（审批人）======
INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS) VALUES
    ('R_LEAD_RM', 'R_LEAD_RM', '客户经理-线索创建', 0),
    ('R_LEAD_BRANCH_MGR', 'R_LEAD_BRANCH_MGR', '分行经理-线索审批', 0);

-- ====== PT_RESOURCE：lead 接口（发起人用）+ workflow 任务接口（审批人用） ======
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS) VALUES
    ('LR001', '/api/leads',          'POST', '新建线索',     1, 0),
    ('LR002', '/api/leads/*/submit', 'POST', '提交审批',     1, 0),
    ('LR003', '/api/leads/*',        'GET',  '线索详情',     1, 0),
    ('LR101', '/api/workflow/tasks', 'GET',  '工作流待办',   1, 0),
    ('LR102', '/api/workflow/tasks/*/claim',   'POST', '工作流任务签收', 1, 0),
    ('LR103', '/api/workflow/tasks/*/approve', 'POST', '工作流任务通过', 1, 0),
    ('LR104', '/api/workflow/tasks/*/reject',  'POST', '工作流任务驳回', 1, 0),
    ('LR105', '/api/workflow/tasks/*',         'GET',  '工作流任务详情', 1, 0);

-- ====== PT_USER_ROLE：账号绑定角色 ======
INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN) VALUES
    ('LE10001', 'R_LEAD_RM', 1),
    ('LE20001', 'R_LEAD_BRANCH_MGR', 1);

-- ====== PT_ROLE_RESOURCE：角色到资源的授权 ======
-- 客户经理：lead 全套 + workflow 详情/待办（自查进度）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
    ('LRR001', 'R_LEAD_RM', 'LR001'),
    ('LRR002', 'R_LEAD_RM', 'LR002'),
    ('LRR003', 'R_LEAD_RM', 'LR003'),
    ('LRR004', 'R_LEAD_RM', 'LR101'),
    ('LRR005', 'R_LEAD_RM', 'LR105'),
    ('LRR101', 'R_LEAD_BRANCH_MGR', 'LR101'),
    ('LRR102', 'R_LEAD_BRANCH_MGR', 'LR102'),
    ('LRR103', 'R_LEAD_BRANCH_MGR', 'LR103'),
    ('LRR104', 'R_LEAD_BRANCH_MGR', 'LR104'),
    ('LRR105', 'R_LEAD_BRANCH_MGR', 'LR105'),
    ('LRR106', 'R_LEAD_BRANCH_MGR', 'LR003');

-- ====== PT_ROLE_BIZ_SCOPE：BizType=LEAD 数据范围 ======
-- 发起人 SELF_CREATED（够用：本人创建的线索）；审批人 ALL（看得见所有 lead 待办）
INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE) VALUES
    ('LRBS001', 'R_LEAD_RM', 'LEAD', 'SELF_CREATED'),
    ('LRBS002', 'R_LEAD_BRANCH_MGR', 'LEAD', 'ALL');

-- ====== EXT_ORG_INFO + EXT_USER_ORG：发起人有 orgCode ======
-- 复用 schema 中的组织（数据 data.sql 已插过 BJ/SH 等总分行；新增本测试专用支行避免与既有数据冲突）
INSERT INTO EXT_ORG_INFO (ID, ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID) VALUES
    (200, 'LEAD_BR', '线索测试分行', 2, 'HQ'),
    (201, 'LEAD_BR_001', '线索测试支行', 3, 'LEAD_BR');

INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
    ('LE10001', 'LEAD_BR_001'),
    ('LE20001', 'LEAD_BR');

-- ====== wf_node_candidate_conf：lead_approve_v1 / branch_manager_approve 节点候选 ======
-- 候选 ROLE 必须用 ROLE_CODE（候选组 Key 格式 ROLE:{ROLE_CODE}）
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value) VALUES
    ('LNC_LEAD_001', 'lead_approve_v1', 'branch_manager_approve', 'ROLE', '["R_LEAD_BRANCH_MGR"]');

-- ====== wf_timeout_rule：可选 SLA 规则（避免下游告警计算失败） ======
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES
    ('LTR_LEAD_001', 'lead_approve_v1', 'branch_manager_approve', 48, 24);
