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
-- 目标修正审批          : perf_target_adjust_v1 (单 BPMN，不拆 corp/retail)
-- 对公分配关系调整审批  : perf_alloc_adjust_corp_v1
-- 零售分配关系调整审批  : perf_alloc_adjust_retail_v1
-- 三流程统一 6 节点 + 1 可选分支：
--   - branch_approve                机构负责人审批
--   - biz_dept_review               业务部门经办审批（含 needsOriginalOwnerApprove CHECKBOX）
--   - [exclusive gateway]           若 needsOriginalOwnerApprove==true 进入原业绩所属人审批
--   - original_owner_approve        原业绩所属人审批（flowable:assignee=${originalOwnerEmpId} 单人指派）
--   - biz_dept_leader_approve       业务部门负责人审批
--   - finance_review                资财部经办审批
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
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_V1_BM_ROLE', 'lead_approve_v1', 'branch_manager_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_V1_HQ_ROLE', 'lead_approve_v1', 'hq_review', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.2 线索批量导入审批 lead_import_approve_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_IMP_V1_HQ_ROLE', 'lead_import_approve_v1', 'hq_batch_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.3 线索删除审批 lead_delete_approve_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LEAD_DEL_V1_HQ_ROLE', 'lead_delete_approve_v1', 'hq_delete_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.4 触达任务流程 touch_process_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TOUCH_V1_RM_ROLE', 'touch_process_v1', 'touch_execute', 'ROLE', '["R_RM"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.5 资产投放审批 loan_approve_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', 'ROLE', '["CORP_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', 'ROLE', '["CREDIT_REVIEWER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', 'ROLE', '["CREDIT_APPROVER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.6 中场支持-场景A support_simple_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', 'ROLE', '["SUPPORT_STAFF"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.7 中场支持-场景B support_complex_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', 'ROLE', '["SUPPORT_SECRETARY"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', 'ROLE', '["SUPPORT_STAFF"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.8 目标修正审批 perf_target_adjust_v1（单 BPMN，不拆 corp/retail）
-- 2026-05-20 重写：从老 target_adjust_approve_v1（仅 finance_leader 一节点）扩展到完整 6 节点；
--                  原 WNC_TGT_ADJ_FL 死配置已废弃
-- 节点：branch_approve / biz_dept_review / original_owner_approve /
--       biz_dept_leader_approve / finance_review / finance_leader_approve
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_BM', 'perf_target_adjust_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_BIZ', 'perf_target_adjust_v1', 'biz_dept_review', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_ORIG', 'perf_target_adjust_v1', 'original_owner_approve', 'ROLE', '["R_RM"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_BIZ_LDR', 'perf_target_adjust_v1', 'biz_dept_leader_approve', 'ROLE', '["CORP_DEPT_LEADER","RETAIL_DEPT_LEADER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_FIN', 'perf_target_adjust_v1', 'finance_review', 'ROLE', '["BACK_FINANCE"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve', 'ROLE', '["FINANCE_LEADER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.9a 对公分配关系调整审批 perf_alloc_adjust_corp_v1
-- 2026-05-20 重写：从老 alloc_adjust_approve_v1（公司部/零售部混在一个流程）拆为
--                  perf_alloc_adjust_corp_v1 + perf_alloc_adjust_retail_v1
--                  对公仅 CORP_DEPT / CORP_DEPT_LEADER 候选
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ACORP_BM', 'perf_alloc_adjust_corp_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ACORP_BIZ', 'perf_alloc_adjust_corp_v1', 'biz_dept_review', 'ROLE', '["CORP_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ACORP_ORIG', 'perf_alloc_adjust_corp_v1', 'original_owner_approve', 'ROLE', '["R_RM"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ACORP_BIZ_LDR', 'perf_alloc_adjust_corp_v1', 'biz_dept_leader_approve', 'ROLE', '["CORP_DEPT_LEADER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ACORP_FIN', 'perf_alloc_adjust_corp_v1', 'finance_review', 'ROLE', '["BACK_FINANCE"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ACORP_FIN_LDR', 'perf_alloc_adjust_corp_v1', 'finance_leader_approve', 'ROLE', '["FINANCE_LEADER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 1.9b 零售分配关系调整审批 perf_alloc_adjust_retail_v1
-- ---------------------------------------------------------
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ARTL_BM', 'perf_alloc_adjust_retail_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ARTL_BIZ', 'perf_alloc_adjust_retail_v1', 'biz_dept_review', 'ROLE', '["RETAIL_DEPT"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ARTL_ORIG', 'perf_alloc_adjust_retail_v1', 'original_owner_approve', 'ROLE', '["R_RM"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ARTL_BIZ_LDR', 'perf_alloc_adjust_retail_v1', 'biz_dept_leader_approve', 'ROLE', '["RETAIL_DEPT_LEADER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ARTL_FIN', 'perf_alloc_adjust_retail_v1', 'finance_review', 'ROLE', '["BACK_FINANCE"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_ARTL_FIN_LDR', 'perf_alloc_adjust_retail_v1', 'finance_leader_approve', 'ROLE', '["FINANCE_LEADER"]')
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
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_V1_BM', 'lead_approve_v1', 'branch_manager_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_V1_HQ', 'lead_approve_v1', 'hq_review', 96, 48)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.2 线索批量导入审批 lead_import_approve_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_IMP_V1_HQ', 'lead_import_approve_v1', 'hq_batch_approve', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.3 线索删除审批 lead_delete_approve_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LEAD_DEL_V1_HQ', 'lead_delete_approve_v1', 'hq_delete_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.4 触达任务流程 touch_process_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TOUCH_V1_EXEC', 'touch_process_v1', 'touch_execute', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.5 资产投放审批 loan_approve_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.6 中场支持-场景A support_simple_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.7 中场支持-场景B support_complex_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', 24, 8)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', 72, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.8 目标修正审批 perf_target_adjust_v1（6 节点统一 48h 红 / 24h 黄）
-- 2026-05-20 重写：从老 WTR_TGT_ADJ_FL 单节点扩展到 6 节点
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_BM', 'perf_target_adjust_v1', 'branch_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_BIZ', 'perf_target_adjust_v1', 'biz_dept_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_ORIG', 'perf_target_adjust_v1', 'original_owner_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_BIZ_LDR', 'perf_target_adjust_v1', 'biz_dept_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_FIN', 'perf_target_adjust_v1', 'finance_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.9a 对公分配关系调整审批 perf_alloc_adjust_corp_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ACORP_BM', 'perf_alloc_adjust_corp_v1', 'branch_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ACORP_BIZ', 'perf_alloc_adjust_corp_v1', 'biz_dept_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ACORP_ORIG', 'perf_alloc_adjust_corp_v1', 'original_owner_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ACORP_BIZ_LDR', 'perf_alloc_adjust_corp_v1', 'biz_dept_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ACORP_FIN', 'perf_alloc_adjust_corp_v1', 'finance_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ACORP_FIN_LDR', 'perf_alloc_adjust_corp_v1', 'finance_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 2.9b 零售分配关系调整审批 perf_alloc_adjust_retail_v1
-- ---------------------------------------------------------
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ARTL_BM', 'perf_alloc_adjust_retail_v1', 'branch_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ARTL_BIZ', 'perf_alloc_adjust_retail_v1', 'biz_dept_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ARTL_ORIG', 'perf_alloc_adjust_retail_v1', 'original_owner_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ARTL_BIZ_LDR', 'perf_alloc_adjust_retail_v1', 'biz_dept_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ARTL_FIN', 'perf_alloc_adjust_retail_v1', 'finance_review', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_ARTL_FIN_LDR', 'perf_alloc_adjust_retail_v1', 'finance_leader_approve', 48, 24)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
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
-- 3.8 目标修正审批 perf_target_adjust_v1（6 节点表单）
-- 2026-05-20 重写：从老 WFF_TGT_ADJ_FL 单节点扩展到 6 节点
-- 关键节点：biz_dept_review 含 needsOriginalOwnerApprove CHECKBOX，由业务部门经办勾选
-- ---------------------------------------------------------
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_BM', 'perf_target_adjust_v1', 'branch_approve',
  '[{"key":"branchAllocOpinion","label":"机构负责人审批意见","type":"TEXTAREA"}]',
  '["branchAllocOpinion"]', '["branchAllocOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_BIZ', 'perf_target_adjust_v1', 'biz_dept_review',
  '[{"key":"bizDeptOpinion","label":"业务部门经办审核意见","type":"TEXTAREA"},{"key":"needsOriginalOwnerApprove","label":"是否需要原业绩所属人审批","type":"CHECKBOX"}]',
  '["bizDeptOpinion","needsOriginalOwnerApprove"]', '["bizDeptOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_ORIG', 'perf_target_adjust_v1', 'original_owner_approve',
  '[{"key":"ownerConfirm","label":"原业绩所属人确认意见","type":"TEXTAREA"}]',
  '["ownerConfirm"]', '["ownerConfirm"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_BIZ_LDR', 'perf_target_adjust_v1', 'biz_dept_leader_approve',
  '[{"key":"bizLeaderOpinion","label":"业务部门负责人审批意见","type":"TEXTAREA"}]',
  '["bizLeaderOpinion"]', '["bizLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_FIN', 'perf_target_adjust_v1', 'finance_review',
  '[{"key":"financeOpinion","label":"资财部经办审核意见","type":"TEXTAREA"}]',
  '["financeOpinion"]', '["financeOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve',
  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]',
  '["finLeaderOpinion"]', '["finLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.9a 对公分配关系调整审批 perf_alloc_adjust_corp_v1（6 节点表单）
-- 关键节点：biz_dept_review (公司部经办) 含 needsOriginalOwnerApprove CHECKBOX
-- ---------------------------------------------------------
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ACORP_BM', 'perf_alloc_adjust_corp_v1', 'branch_approve',
  '[{"key":"branchAllocOpinion","label":"机构负责人审批意见","type":"TEXTAREA"}]',
  '["branchAllocOpinion"]', '["branchAllocOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ACORP_BIZ', 'perf_alloc_adjust_corp_v1', 'biz_dept_review',
  '[{"key":"bizDeptOpinion","label":"公司部经办审核意见","type":"TEXTAREA"},{"key":"needsOriginalOwnerApprove","label":"是否需要原业绩所属人审批","type":"CHECKBOX"}]',
  '["bizDeptOpinion","needsOriginalOwnerApprove"]', '["bizDeptOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ACORP_ORIG', 'perf_alloc_adjust_corp_v1', 'original_owner_approve',
  '[{"key":"ownerConfirm","label":"原业绩所属人确认意见","type":"TEXTAREA"}]',
  '["ownerConfirm"]', '["ownerConfirm"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ACORP_BIZ_LDR', 'perf_alloc_adjust_corp_v1', 'biz_dept_leader_approve',
  '[{"key":"bizLeaderOpinion","label":"公司部负责人审批意见","type":"TEXTAREA"}]',
  '["bizLeaderOpinion"]', '["bizLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ACORP_FIN', 'perf_alloc_adjust_corp_v1', 'finance_review',
  '[{"key":"financeOpinion","label":"资财部经办审核意见","type":"TEXTAREA"},{"key":"recalcRequired","label":"是否需要历史重算","type":"RADIO","dictType":"YES_NO"}]',
  '["financeOpinion","recalcRequired"]', '["financeOpinion","recalcRequired"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ACORP_FIN_LDR', 'perf_alloc_adjust_corp_v1', 'finance_leader_approve',
  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]',
  '["finLeaderOpinion"]', '["finLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------
-- 3.9b 零售分配关系调整审批 perf_alloc_adjust_retail_v1（6 节点表单）
-- 关键节点：biz_dept_review (零售部经办) 含 needsOriginalOwnerApprove CHECKBOX
-- ---------------------------------------------------------
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ARTL_BM', 'perf_alloc_adjust_retail_v1', 'branch_approve',
  '[{"key":"branchAllocOpinion","label":"机构负责人审批意见","type":"TEXTAREA"}]',
  '["branchAllocOpinion"]', '["branchAllocOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ARTL_BIZ', 'perf_alloc_adjust_retail_v1', 'biz_dept_review',
  '[{"key":"bizDeptOpinion","label":"零售部经办审核意见","type":"TEXTAREA"},{"key":"needsOriginalOwnerApprove","label":"是否需要原业绩所属人审批","type":"CHECKBOX"}]',
  '["bizDeptOpinion","needsOriginalOwnerApprove"]', '["bizDeptOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ARTL_ORIG', 'perf_alloc_adjust_retail_v1', 'original_owner_approve',
  '[{"key":"ownerConfirm","label":"原业绩所属人确认意见","type":"TEXTAREA"}]',
  '["ownerConfirm"]', '["ownerConfirm"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ARTL_BIZ_LDR', 'perf_alloc_adjust_retail_v1', 'biz_dept_leader_approve',
  '[{"key":"bizLeaderOpinion","label":"零售部负责人审批意见","type":"TEXTAREA"}]',
  '["bizLeaderOpinion"]', '["bizLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ARTL_FIN', 'perf_alloc_adjust_retail_v1', 'finance_review',
  '[{"key":"financeOpinion","label":"资财部经办审核意见","type":"TEXTAREA"},{"key":"recalcRequired","label":"是否需要历史重算","type":"RADIO","dictType":"YES_NO"}]',
  '["financeOpinion","recalcRequired"]', '["financeOpinion","recalcRequired"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_ARTL_FIN_LDR', 'perf_alloc_adjust_retail_v1', 'finance_leader_approve',
  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]',
  '["finLeaderOpinion"]', '["finLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- ============================================================================
-- END OF workflow-seed-v1.sql
-- ============================================================================
