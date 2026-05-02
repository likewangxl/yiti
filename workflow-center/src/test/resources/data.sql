-- ============================================================
-- H2 Test Data for Workflow (workflow-center)
-- ============================================================

-- 流程映射
INSERT INTO BIZ_PROCESS_MAP (id, business_key, biz_type, biz_id, process_definition_key, process_instance_id, start_user, current_assignee, process_status) VALUES
    ('MAP001', 'LEAD:L20260001', 'LEAD', 'L20260001', 'lead_approve_v1', 'PI_RUN_001', 'user001', 'user002', 'RUNNING'),
    ('MAP002', 'LEAD:L20260002', 'LEAD', 'L20260002', 'lead_approve_v1', 'PI_COMP_001', 'user002', NULL, 'COMPLETED'),
    ('MAP003', 'LEAD:L20260003', 'LEAD', 'L20260003', 'lead_approve_v1', 'PI_CAN_001', 'user001', NULL, 'CANCELLED');

-- 节点候选人配置
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value) VALUES
    ('NC001', 'lead_approve_v1', 'dept_review', 'ROLE', '["BRANCH_HEAD"]'),
    ('NC002', 'lead_approve_v1', 'final_review', 'ROLE', '["ADMIN"]'),
    ('NC003', 'lead_approve_v1', 'submit', 'USER', '["user001","user002"]');

-- 节点表单配置
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES
    ('FC001', 'lead_approve_v1', 'dept_review', '["leadName","custName","industry","amount"]', '["amount","remark"]', '["leadName","custName"]'),
    ('FC002', 'lead_approve_v1', 'final_review', '["leadName","custName","industry","amount","remark"]', '["remark"]', '["leadName","custName","amount"]');

-- 超时规则
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES
    ('TR001', 'lead_approve_v1', 'dept_review', 48, 24),
    ('TR002', 'lead_approve_v1', 'final_review', 72, 36);
