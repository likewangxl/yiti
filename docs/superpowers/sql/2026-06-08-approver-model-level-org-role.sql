-- ============================================================================
-- 审批人模型重构：层级角色 / 机构角色（schema ALTER）
-- ----------------------------------------------------------------------------
-- 见 spec docs/superpowers/specs/2026-06-08-approver-model-level-org-role-design.md
-- 设计源表 WF_FLOW_NODE_APPROVER：approver_type 扩到 16（容 LEVEL_ROLE/ORG_ROLE），
--   加 org_scope（层级角色 SELF/PARENT）/ role_code（机构角色可选角色）。
-- 运行时表 WF_NODE_CANDIDATE_CONF：加 org_code（机构角色固定机构）。
-- 幂等：列已存在报 Duplicate column 可忽略。
-- 双库：yiti（设计源+运行时都执行）；onepl 仅 WF_NODE_CANDIDATE_CONF（设计器表未部署）。
-- ============================================================================

-- 设计源表（仅 yiti）
ALTER TABLE WF_FLOW_NODE_APPROVER MODIFY COLUMN approver_type varchar(16) NOT NULL;
ALTER TABLE WF_FLOW_NODE_APPROVER ADD COLUMN org_scope varchar(8) NULL COMMENT '层级角色:SELF/PARENT' AFTER approver_value;
ALTER TABLE WF_FLOW_NODE_APPROVER ADD COLUMN role_code varchar(64) NULL COMMENT '机构角色可选角色码' AFTER org_scope;

-- 运行时表（yiti + onepl）
ALTER TABLE WF_NODE_CANDIDATE_CONF ADD COLUMN org_code varchar(64) NULL COMMENT '机构角色固定机构码' AFTER approve_org_scope;
