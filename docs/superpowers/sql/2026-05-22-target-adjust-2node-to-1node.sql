-- =========================================================
-- 2026-05-22 目标修正流程精简：6 节点 → 1 节点（finance_leader_approve）
-- =========================================================
-- 背景：
--   业务调整：目标修正改为"仅资财部经办发起 → 资财部负责人审批"。
--   BPMN perf_target_adjust_v1 从 6 节点（branch_approve / biz_dept_review /
--   original_owner_approve / biz_dept_leader_approve / finance_review /
--   finance_leader_approve）精简为 1 节点（finance_leader_approve）。
--   候选 ROLE:FINANCE_LEADER，48h 超时 / 24h 告警，表单只含 opinion textarea。
--
--   原 BPMN 文件已在 perf-engine-center 重写，本脚本清理 WF_NODE_CANDIDATE_CONF /
--   WF_TIMEOUT_RULE / WF_NODE_FORM_CONF 中 perf_target_adjust_v1 的 6 节点旧配置
--   并替换为 1 节点新配置。
--
-- 部署范围：yiti + onepl 双库
--
-- 执行前备份：
--   docs/superpowers/sql/backup/2026-05-22-pre-target-adjust-2node-yiti.sql
--   docs/superpowers/sql/backup/2026-05-22-pre-target-adjust-2node-onepl.sql
--
-- 幂等：DELETE 限定 process_definition_key='perf_target_adjust_v1'，
--      INSERT 用 ON DUPLICATE KEY UPDATE，可重复执行
-- =========================================================

-- A) 清理旧配置（perf_target_adjust_v1 在三张表里的所有行）
DELETE FROM WF_NODE_CANDIDATE_CONF WHERE process_definition_key = 'perf_target_adjust_v1';
DELETE FROM WF_TIMEOUT_RULE        WHERE process_definition_key = 'perf_target_adjust_v1';
DELETE FROM WF_NODE_FORM_CONF      WHERE process_definition_key = 'perf_target_adjust_v1';

-- B) 新插 1 节点配置（仅 finance_leader_approve）
INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key, candidate_type, candidate_value)
VALUES ('WNC_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve', 'ROLE', '["FINANCE_LEADER"]')
ON DUPLICATE KEY UPDATE candidate_value = VALUES(candidate_value), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_TIMEOUT_RULE (id, process_definition_key, node_key, timeout_hours, warning_hours)
VALUES ('WTR_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve', 48, 24)
ON DUPLICATE KEY UPDATE timeout_hours = VALUES(timeout_hours), warning_hours = VALUES(warning_hours), updated_time = CURRENT_TIMESTAMP;

INSERT INTO WF_NODE_FORM_CONF (id, process_definition_key, node_key, form_fields, editable_fields, required_fields)
VALUES ('WFF_TGT_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve',
        '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]',
        '["finLeaderOpinion"]', '["finLeaderOpinion"]')
ON DUPLICATE KEY UPDATE form_fields = VALUES(form_fields), editable_fields = VALUES(editable_fields), required_fields = VALUES(required_fields), updated_time = CURRENT_TIMESTAMP;

-- =========================================================
-- 验证
--   1) 旧节点应全部清零
--      SELECT COUNT(*) FROM WF_NODE_CANDIDATE_CONF
--       WHERE process_definition_key='perf_target_adjust_v1'
--         AND node_key IN ('branch_approve','biz_dept_review','original_owner_approve',
--                          'biz_dept_leader_approve','finance_review');
--      -- 期望 0
--
--   2) 新 1 节点应存在
--      SELECT node_key, candidate_value FROM WF_NODE_CANDIDATE_CONF
--       WHERE process_definition_key='perf_target_adjust_v1';
--      -- 期望 1 行 finance_leader_approve, ["FINANCE_LEADER"]
-- =========================================================
