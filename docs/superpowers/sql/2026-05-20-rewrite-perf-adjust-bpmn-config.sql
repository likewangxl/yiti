-- =========================================================
-- 2026-05-20 业绩调整审批流程重写 - WF 配置对齐脚本
-- =========================================================
-- 背景：
--   原 V1.2 Q0.3 perf_target_adjust_v1 / perf_alloc_adjust_corp_v1 /
--   perf_alloc_adjust_retail_v1 三个 BPMN 仅 branch_mgr_review + hq_mgr_review
--   两节点占位，远不满足"发起人 → 机构负责人 → 业务部门经办 → (原业绩所属人
--   可选) → 业务部门负责人 → 资财部经办 → 资财部负责人"7 节点流程要求.
--
--   2026-05-20 三个 BPMN 已重写为完整 6 节点 + 1 可选分支，节点 id 统一为：
--     branch_approve / biz_dept_review / original_owner_approve /
--     biz_dept_leader_approve / finance_review / finance_leader_approve
--
--   原 workflow-seed-v1.sql 中针对 alloc_adjust_approve_v1（死配置 processKey，
--   无对应 BPMN）和 target_adjust_approve_v1（同样死配置）的 WF_NODE_CANDIDATE_CONF
--   / WF_TIMEOUT_RULE / WF_NODE_FORM_CONF 记录全部清理，并替换为指向真实 BPMN 的
--   3 套配置.
--
-- 部署范围：onepl + yiti 双库
--
-- 执行前备份：
--   mysqldump -uroot -pdjdev onepl WF_NODE_CANDIDATE_CONF WF_TIMEOUT_RULE \
--       WF_NODE_FORM_CONF > docs/superpowers/sql/backup/2026-05-20-wf-pre-rewrite-onepl.sql
--   mysqldump -uroot -pdjdev yiti  WF_NODE_CANDIDATE_CONF WF_TIMEOUT_RULE \
--       WF_NODE_FORM_CONF > docs/superpowers/sql/backup/2026-05-20-wf-pre-rewrite-yiti.sql
--
-- 幂等性：所有 INSERT 使用 ON DUPLICATE KEY UPDATE；所有 DELETE 带 WHERE 限定到
--         死 processKey；可重复执行
-- =========================================================

-- =========================================================
-- A) 清理老 processKey 配置（死配置：无对应 BPMN）
-- =========================================================
DELETE FROM WF_NODE_CANDIDATE_CONF
WHERE process_definition_key IN ('target_adjust_approve_v1', 'alloc_adjust_approve_v1');

DELETE FROM WF_TIMEOUT_RULE
WHERE process_definition_key IN ('target_adjust_approve_v1', 'alloc_adjust_approve_v1');

DELETE FROM WF_NODE_FORM_CONF
WHERE process_definition_key IN ('target_adjust_approve_v1', 'alloc_adjust_approve_v1');

-- =========================================================
-- B) WF_NODE_CANDIDATE_CONF（3 流程 × 6 节点 = 18 行）
-- =========================================================
-- B.1 perf_target_adjust_v1（单 BPMN，业务部门用双候选）
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

-- B.2 perf_alloc_adjust_corp_v1（对公独占 CORP_DEPT / CORP_DEPT_LEADER）
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

-- B.3 perf_alloc_adjust_retail_v1（零售独占 RETAIL_DEPT / RETAIL_DEPT_LEADER）
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
-- C) WF_TIMEOUT_RULE（3 流程 × 6 节点 = 18 行，统一 48h 红 / 24h 黄）
-- =========================================================
-- C.1 perf_target_adjust_v1
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_TGT_BM',      'perf_target_adjust_v1',       'branch_approve',          48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_TGT_BIZ',     'perf_target_adjust_v1',       'biz_dept_review',         48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_TGT_ORIG',    'perf_target_adjust_v1',       'original_owner_approve',  48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_TGT_BIZ_LDR', 'perf_target_adjust_v1',       'biz_dept_leader_approve', 48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_TGT_FIN',     'perf_target_adjust_v1',       'finance_review',          48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_TGT_FIN_LDR', 'perf_target_adjust_v1',       'finance_leader_approve',  48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- C.2 perf_alloc_adjust_corp_v1
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ACORP_BM',      'perf_alloc_adjust_corp_v1',  'branch_approve',          48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ACORP_BIZ',     'perf_alloc_adjust_corp_v1',  'biz_dept_review',         48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ACORP_ORIG',    'perf_alloc_adjust_corp_v1',  'original_owner_approve',  48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ACORP_BIZ_LDR', 'perf_alloc_adjust_corp_v1',  'biz_dept_leader_approve', 48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ACORP_FIN',     'perf_alloc_adjust_corp_v1',  'finance_review',          48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ACORP_FIN_LDR', 'perf_alloc_adjust_corp_v1',  'finance_leader_approve',  48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- C.3 perf_alloc_adjust_retail_v1
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ARTL_BM',      'perf_alloc_adjust_retail_v1', 'branch_approve',          48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ARTL_BIZ',     'perf_alloc_adjust_retail_v1', 'biz_dept_review',         48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ARTL_ORIG',    'perf_alloc_adjust_retail_v1', 'original_owner_approve',  48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ARTL_BIZ_LDR', 'perf_alloc_adjust_retail_v1', 'biz_dept_leader_approve', 48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ARTL_FIN',     'perf_alloc_adjust_retail_v1', 'finance_review',          48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours) VALUES ('WTR_ARTL_FIN_LDR', 'perf_alloc_adjust_retail_v1', 'finance_leader_approve',  48, 24) ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

-- =========================================================
-- D) WF_NODE_FORM_CONF（3 流程 × 6 节点 = 18 行）
-- 关键：biz_dept_review 节点表单包含 needsOriginalOwnerApprove (CHECKBOX)
--       让公司部/零售部经办勾选决定是否进入"原业绩所属人审批"节点
-- =========================================================
-- D.1 perf_target_adjust_v1
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_TGT_BM',      'perf_target_adjust_v1', 'branch_approve',          '[{"key":"branchAllocOpinion","label":"机构负责人审批意见","type":"TEXTAREA"}]', '["branchAllocOpinion"]', '["branchAllocOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_TGT_BIZ',     'perf_target_adjust_v1', 'biz_dept_review',         '[{"key":"bizDeptOpinion","label":"业务部门经办审核意见","type":"TEXTAREA"},{"key":"needsOriginalOwnerApprove","label":"是否需要原业绩所属人审批","type":"CHECKBOX"}]', '["bizDeptOpinion","needsOriginalOwnerApprove"]', '["bizDeptOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_TGT_ORIG',    'perf_target_adjust_v1', 'original_owner_approve',  '[{"key":"ownerConfirm","label":"原业绩所属人确认意见","type":"TEXTAREA"}]', '["ownerConfirm"]', '["ownerConfirm"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_TGT_BIZ_LDR', 'perf_target_adjust_v1', 'biz_dept_leader_approve', '[{"key":"bizLeaderOpinion","label":"业务部门负责人审批意见","type":"TEXTAREA"}]', '["bizLeaderOpinion"]', '["bizLeaderOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_TGT_FIN',     'perf_target_adjust_v1', 'finance_review',          '[{"key":"financeOpinion","label":"资财部经办审核意见","type":"TEXTAREA"}]', '["financeOpinion"]', '["financeOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve',  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]', '["finLeaderOpinion"]', '["finLeaderOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- D.2 perf_alloc_adjust_corp_v1
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ACORP_BM',      'perf_alloc_adjust_corp_v1', 'branch_approve',          '[{"key":"branchAllocOpinion","label":"机构负责人审批意见","type":"TEXTAREA"}]', '["branchAllocOpinion"]', '["branchAllocOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ACORP_BIZ',     'perf_alloc_adjust_corp_v1', 'biz_dept_review',         '[{"key":"bizDeptOpinion","label":"公司部经办审核意见","type":"TEXTAREA"},{"key":"needsOriginalOwnerApprove","label":"是否需要原业绩所属人审批","type":"CHECKBOX"}]', '["bizDeptOpinion","needsOriginalOwnerApprove"]', '["bizDeptOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ACORP_ORIG',    'perf_alloc_adjust_corp_v1', 'original_owner_approve',  '[{"key":"ownerConfirm","label":"原业绩所属人确认意见","type":"TEXTAREA"}]', '["ownerConfirm"]', '["ownerConfirm"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ACORP_BIZ_LDR', 'perf_alloc_adjust_corp_v1', 'biz_dept_leader_approve', '[{"key":"bizLeaderOpinion","label":"公司部负责人审批意见","type":"TEXTAREA"}]', '["bizLeaderOpinion"]', '["bizLeaderOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ACORP_FIN',     'perf_alloc_adjust_corp_v1', 'finance_review',          '[{"key":"financeOpinion","label":"资财部经办审核意见","type":"TEXTAREA"},{"key":"recalcRequired","label":"是否需要历史重算","type":"RADIO","dictType":"YES_NO"}]', '["financeOpinion","recalcRequired"]', '["financeOpinion","recalcRequired"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ACORP_FIN_LDR', 'perf_alloc_adjust_corp_v1', 'finance_leader_approve',  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]', '["finLeaderOpinion"]', '["finLeaderOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- D.3 perf_alloc_adjust_retail_v1
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ARTL_BM',      'perf_alloc_adjust_retail_v1', 'branch_approve',          '[{"key":"branchAllocOpinion","label":"机构负责人审批意见","type":"TEXTAREA"}]', '["branchAllocOpinion"]', '["branchAllocOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ARTL_BIZ',     'perf_alloc_adjust_retail_v1', 'biz_dept_review',         '[{"key":"bizDeptOpinion","label":"零售部经办审核意见","type":"TEXTAREA"},{"key":"needsOriginalOwnerApprove","label":"是否需要原业绩所属人审批","type":"CHECKBOX"}]', '["bizDeptOpinion","needsOriginalOwnerApprove"]', '["bizDeptOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ARTL_ORIG',    'perf_alloc_adjust_retail_v1', 'original_owner_approve',  '[{"key":"ownerConfirm","label":"原业绩所属人确认意见","type":"TEXTAREA"}]', '["ownerConfirm"]', '["ownerConfirm"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ARTL_BIZ_LDR', 'perf_alloc_adjust_retail_v1', 'biz_dept_leader_approve', '[{"key":"bizLeaderOpinion","label":"零售部负责人审批意见","type":"TEXTAREA"}]', '["bizLeaderOpinion"]', '["bizLeaderOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ARTL_FIN',     'perf_alloc_adjust_retail_v1', 'finance_review',          '[{"key":"financeOpinion","label":"资财部经办审核意见","type":"TEXTAREA"},{"key":"recalcRequired","label":"是否需要历史重算","type":"RADIO","dictType":"YES_NO"}]', '["financeOpinion","recalcRequired"]', '["financeOpinion","recalcRequired"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;
INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields) VALUES ('WFF_ARTL_FIN_LDR', 'perf_alloc_adjust_retail_v1', 'finance_leader_approve',  '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]', '["finLeaderOpinion"]', '["finLeaderOpinion"]') ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- =========================================================
-- 验证（执行后核对）
-- =========================================================
-- 1) 老 processKey 死配置应全部清零
--    SELECT COUNT(*) FROM WF_NODE_CANDIDATE_CONF
--    WHERE process_definition_key IN ('target_adjust_approve_v1','alloc_adjust_approve_v1');
--    -- 期望 0
--
-- 2) 3 新 processKey 各 6 节点候选
--    SELECT process_definition_key, COUNT(*) FROM WF_NODE_CANDIDATE_CONF
--    WHERE process_definition_key IN ('perf_target_adjust_v1','perf_alloc_adjust_corp_v1','perf_alloc_adjust_retail_v1')
--    GROUP BY process_definition_key;
--    -- 期望每个 6 行
--
-- 3) biz_dept_review 节点表单含 needsOriginalOwnerApprove
--    SELECT process_definition_key, form_fields FROM WF_NODE_FORM_CONF
--    WHERE node_key='biz_dept_review';
--    -- 期望 3 行，form_fields JSON 都含 "needsOriginalOwnerApprove"
-- =========================================================
