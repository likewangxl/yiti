-- ============================================================================
-- workflow-seed-v1.sql — 工作流配置种子数据（DEV/TEST only）
-- 版本：V1.0
-- 描述：wf_node_candidate_conf / wf_timeout_rule / wf_node_form_conf 完整种子
-- 日期：2026-03-25
-- 执行前提：create-table.sql + v1-additions.sql + seed-v1.sql 已执行
-- 幂等策略：INSERT ... ON DUPLICATE KEY UPDATE
-- ============================================================================

SET NAMES utf8mb4;

-- =========================================================
-- 0) 流程定义 Key / Node Key 完整清单（V1）
-- =========================================================
-- 线索单条审批          : lead_approve_v1
--   - branch_manager_approve   经营机构负责人审核
--   - hq_review                公司部/零售部审核
--
-- 线索批量导入审批      : lead_import_approve_v1
--   - hq_batch_approve         总部批次审批
--
-- 线索删除审批          : lead_delete_approve_v1
--   - hq_delete_approve        总部删除审批
--
-- 触达任务流程          : touch_process_v1
--   - touch_execute            触达执行
--
-- 资产投放审批          : loan_approve_v1
--   - branch_approve           经营机构负责人审批
--   - corp_review              公司部审核
--   - credit_check             授信审查
--   - credit_approval          授信批复
--
-- 中场支持-场景A（产品直达）: support_simple_v1
--   - product_owner_handle     产品负责人办理
--
-- 中场支持-场景B（部门承接）: support_complex_v1
--   - dept_secretary_dispatch  秘书派单
--   - support_staff_handle     支持人员办理
--
-- 目标修正审批          : target_adjust_approve_v1
--   - finance_leader_approve   资财部负责人审批
--
-- 分配关系调整审批      : alloc_adjust_approve_v1
--   - branch_approve                机构负责人审批
--   - biz_dept_review               业务部门审核
--   - original_owner_approve        原管户人确认
--   - biz_dept_leader_approve       业务部门负责人审批
--   - finance_review                资财部审核
--   - finance_leader_approve        资财部负责人审批


-- =========================================================
-- 1) wf_node_candidate_conf（节点候选配置）
-- 说明：
-- - candidateType = ROLE 时，candidateValue 为 JSON 数组（角色编码）
-- - 角色编码对应 PT_ROLE.ROLE_CODE
-- =========================================================

-- ---------------------------------------------------------
-- 1.1 线索单条审批 lead_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_V1_BM_ROLE', 'lead_approve_v1', 'branch_manager_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_V1_HQ_ROLE', 'lead_approve_v1', 'hq_review', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.2 线索批量导入审批 lead_import_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_IMP_V1_HQ_ROLE', 'lead_import_approve_v1', 'hq_batch_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.3 线索删除审批 lead_delete_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_DEL_V1_HQ_ROLE', 'lead_delete_approve_v1', 'hq_delete_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.4 触达任务流程 touch_process_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TOUCH_V1_RM_ROLE', 'touch_process_v1', 'touch_execute', 'ROLE', '["CUST_MANAGER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.5 资产投放审批 loan_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', 'ROLE', '["CORP_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', 'ROLE', '["CREDIT_REVIEWER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', 'ROLE', '["CREDIT_APPROVER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.6 中场支持-场景A support_simple_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', 'ROLE', '["SUPPORT_STAFF"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.7 中场支持-场景B support_complex_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', 'ROLE', '["SUPPORT_SECRETARY"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', 'ROLE', '["SUPPORT_STAFF"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.8 目标修正审批 target_adjust_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_ADJ_FL', 'target_adjust_approve_v1', 'finance_leader_approve', 'ROLE', '["BACK_FINANCE"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.9 分配关系调整审批 alloc_adjust_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ALLOC_BM', 'alloc_adjust_approve_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ALLOC_BIZ', 'alloc_adjust_approve_v1', 'biz_dept_review', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ALLOC_ORIG', 'alloc_adjust_approve_v1', 'original_owner_approve', 'ROLE', '["CUST_MANAGER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ALLOC_BIZ_LDR', 'alloc_adjust_approve_v1', 'biz_dept_leader_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ALLOC_FIN', 'alloc_adjust_approve_v1', 'finance_review', 'ROLE', '["BACK_FINANCE"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_node_candidate_conf (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ALLOC_FIN_LDR', 'alloc_adjust_approve_v1', 'finance_leader_approve', 'ROLE', '["BACK_FINANCE"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;


-- =========================================================
-- 2) wf_timeout_rule（节点超时规则，单位：小时）
-- 说明：
-- - warning_hours = 黄灯阈值，timeout_hours = 红灯阈值
-- - 前端用"工作日天数"配置，服务端换算为小时（1天=24h，不计非工作日）
-- =========================================================

-- ---------------------------------------------------------
-- 2.1 线索单条审批 lead_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_V1_BM', 'lead_approve_v1', 'branch_manager_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_V1_HQ', 'lead_approve_v1', 'hq_review', 96, 48)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.2 线索批量导入审批 lead_import_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_IMP_V1_HQ', 'lead_import_approve_v1', 'hq_batch_approve', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.3 线索删除审批 lead_delete_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_DEL_V1_HQ', 'lead_delete_approve_v1', 'hq_delete_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.4 触达任务流程 touch_process_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TOUCH_V1_EXEC', 'touch_process_v1', 'touch_execute', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.5 资产投放审批 loan_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.6 中场支持-场景A support_simple_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.7 中场支持-场景B support_complex_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', 24, 8)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.8 目标修正审批 target_adjust_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_ADJ_FL', 'target_adjust_approve_v1', 'finance_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.9 分配关系调整审批 alloc_adjust_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ALLOC_BM', 'alloc_adjust_approve_v1', 'branch_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ALLOC_BIZ', 'alloc_adjust_approve_v1', 'biz_dept_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ALLOC_ORIG', 'alloc_adjust_approve_v1', 'original_owner_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ALLOC_BIZ_LDR', 'alloc_adjust_approve_v1', 'biz_dept_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ALLOC_FIN', 'alloc_adjust_approve_v1', 'finance_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO wf_timeout_rule (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ALLOC_FIN_LDR', 'alloc_adjust_approve_v1', 'finance_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;


-- =========================================================
-- 3) wf_node_form_conf（节点表单字段权限）
-- 说明：
-- - form_fields: JSON 数组，定义审批表单追加字段
-- - editable_fields: 可编辑字段 key 列表
-- - required_fields: 必填字段 key 列表（必须是 editable_fields 的子集）
-- =========================================================

-- ---------------------------------------------------------
-- 3.1 线索单条审批 lead_approve_v1
-- ---------------------------------------------------------
-- 经营机构负责人审核
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LEAD_V1_BM',
  'lead_approve_v1',
  'branch_manager_approve',
  '[{"key":"bmOpinion","label":"机构负责人意见","type":"TEXTAREA"}]',
  '["bmOpinion"]',
  '["bmOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 公司部/零售部审核
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LEAD_V1_HQ',
  'lead_approve_v1',
  'hq_review',
  '[{"key":"hqConclusion","label":"总部审核结论","type":"TEXTAREA"},{"key":"riskLevel","label":"风险等级","type":"SELECT","dictType":"RISK_LEVEL"}]',
  '["hqConclusion","riskLevel"]',
  '["hqConclusion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.2 线索批量导入审批 lead_import_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LEAD_IMP_V1_HQ',
  'lead_import_approve_v1',
  'hq_batch_approve',
  '[{"key":"batchOpinion","label":"批次审批意见","type":"TEXTAREA"}]',
  '["batchOpinion"]',
  '["batchOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.3 线索删除审批 lead_delete_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LEAD_DEL_V1_HQ',
  'lead_delete_approve_v1',
  'hq_delete_approve',
  '[{"key":"deleteOpinion","label":"删除审批意见","type":"TEXTAREA"}]',
  '["deleteOpinion"]',
  '["deleteOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.4 触达任务流程 touch_process_v1（无额外审批表单字段）
-- ---------------------------------------------------------
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_TOUCH_V1_EXEC',
  'touch_process_v1',
  'touch_execute',
  '[]',
  '[]',
  '[]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.5 资产投放审批 loan_approve_v1
-- ---------------------------------------------------------
-- 机构负责人审批
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LOAN_V1_BM',
  'loan_approve_v1',
  'branch_approve',
  '[{"key":"branchOpinion","label":"机构审批意见","type":"TEXTAREA"}]',
  '["branchOpinion"]',
  '["branchOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 公司部审核
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LOAN_V1_CORP',
  'loan_approve_v1',
  'corp_review',
  '[{"key":"corpOpinion","label":"公司部审核意见","type":"TEXTAREA"},{"key":"needCreditCommittee","label":"是否需要上会","type":"RADIO","dictType":"YES_NO"},{"key":"creditCommitteeConclusion","label":"上会结论","type":"TEXTAREA"}]',
  '["corpOpinion","needCreditCommittee","creditCommitteeConclusion"]',
  '["corpOpinion","needCreditCommittee"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 授信审查
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LOAN_V1_CK',
  'loan_approve_v1',
  'credit_check',
  '[{"key":"creditCheckOpinion","label":"授信审查意见","type":"TEXTAREA"},{"key":"creditCheckResult","label":"审查结论","type":"SELECT","options":[{"label":"通过","value":"PASS"},{"label":"补充材料","value":"SUPPLEMENT"},{"label":"拒绝","value":"REJECT"}]}]',
  '["creditCheckOpinion","creditCheckResult"]',
  '["creditCheckOpinion","creditCheckResult"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 授信批复
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_LOAN_V1_CA',
  'loan_approve_v1',
  'credit_approval',
  '[{"key":"approvalOpinion","label":"批复意见","type":"TEXTAREA"},{"key":"approvedAmount","label":"批复金额(万元)","type":"NUMBER"},{"key":"approvedTerm","label":"批复期限(月)","type":"NUMBER"}]',
  '["approvalOpinion","approvedAmount","approvedTerm"]',
  '["approvalOpinion","approvedAmount","approvedTerm"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.6 中场支持-场景A support_simple_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_SUP_SIMPLE_OWNER',
  'support_simple_v1',
  'product_owner_handle',
  '[{"key":"handleResult","label":"办理结果","type":"TEXTAREA"},{"key":"visitPhotoUrls","label":"拜访照片","type":"FILE_LIST"}]',
  '["handleResult","visitPhotoUrls"]',
  '["handleResult"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.7 中场支持-场景B support_complex_v1
-- ---------------------------------------------------------
-- 秘书派单
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_SUP_COMPLEX_SEC',
  'support_complex_v1',
  'dept_secretary_dispatch',
  '[{"key":"assignedEmpId","label":"指定支持人员","type":"USER_SELECT"},{"key":"dispatchRemark","label":"派单备注","type":"TEXTAREA"}]',
  '["assignedEmpId","dispatchRemark"]',
  '["assignedEmpId"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 支持人员办理
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_SUP_COMPLEX_STAFF',
  'support_complex_v1',
  'support_staff_handle',
  '[{"key":"handleResult","label":"办理结果","type":"TEXTAREA"},{"key":"visitPhotoUrls","label":"拜访照片","type":"FILE_LIST"}]',
  '["handleResult","visitPhotoUrls"]',
  '["handleResult"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.8 目标修正审批 target_adjust_approve_v1
-- ---------------------------------------------------------
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_TGT_ADJ_FL',
  'target_adjust_approve_v1',
  'finance_leader_approve',
  '[{"key":"adjustOpinion","label":"修正审批意见","type":"TEXTAREA"}]',
  '["adjustOpinion"]',
  '["adjustOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.9 分配关系调整审批 alloc_adjust_approve_v1
-- ---------------------------------------------------------
-- 机构负责人审批
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_ALLOC_BM',
  'alloc_adjust_approve_v1',
  'branch_approve',
  '[{"key":"branchAllocOpinion","label":"机构审批意见","type":"TEXTAREA"}]',
  '["branchAllocOpinion"]',
  '["branchAllocOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 业务部门审核
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_ALLOC_BIZ',
  'alloc_adjust_approve_v1',
  'biz_dept_review',
  '[{"key":"bizDeptOpinion","label":"业务部门审核意见","type":"TEXTAREA"}]',
  '["bizDeptOpinion"]',
  '["bizDeptOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 原管户人确认
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_ALLOC_ORIG',
  'alloc_adjust_approve_v1',
  'original_owner_approve',
  '[{"key":"ownerConfirm","label":"原管户人确认意见","type":"TEXTAREA"}]',
  '["ownerConfirm"]',
  '["ownerConfirm"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 业务部门负责人审批
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_ALLOC_BIZ_LDR',
  'alloc_adjust_approve_v1',
  'biz_dept_leader_approve',
  '[{"key":"bizLeaderOpinion","label":"业务部门负责人意见","type":"TEXTAREA"}]',
  '["bizLeaderOpinion"]',
  '["bizLeaderOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 资财部审核
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_ALLOC_FIN',
  'alloc_adjust_approve_v1',
  'finance_review',
  '[{"key":"financeOpinion","label":"资财部审核意见","type":"TEXTAREA"},{"key":"recalcRequired","label":"是否需要历史重算","type":"RADIO","dictType":"YES_NO"}]',
  '["financeOpinion","recalcRequired"]',
  '["financeOpinion","recalcRequired"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- 资财部负责人审批
INSERT INTO wf_node_form_conf (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES (
  'WFF_ALLOC_FIN_LDR',
  'alloc_adjust_approve_v1',
  'finance_leader_approve',
  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]',
  '["finLeaderOpinion"]',
  '["finLeaderOpinion"]'
)
ON DUPLICATE KEY UPDATE
  form_fields = VALUES(form_fields),
  editable_fields = VALUES(editable_fields),
  required_fields = VALUES(required_fields),
  updated_time = CURRENT_TIMESTAMP;

-- ============================================================================
-- END OF workflow-seed-v1.sql
-- ============================================================================
