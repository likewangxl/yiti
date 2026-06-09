-- ============================================================================
-- 审批节点「审批机构归属」approve_org_scope（本机构/上级机构/不判断）
-- ----------------------------------------------------------------------------
-- 需求：审批节点可配置审批机构归属——
--   SELF   = 本机构：要求审批人所属机构号 = 发起人机构号
--   PARENT = 上级机构：要求审批人所属机构 = 发起人的上级机构
--   NULL   = 不判断：不做机构过滤（默认）
--
-- 设计：把原硬编码在 TaskAssignmentListener(nodeKey=="branch_approve") 的
--   「3级支行→上级分行」机构过滤逻辑，泛化成可配置的节点属性。
--   · WF_FLOW_NODE.approve_org_scope        —— 设计源（流程设计器节点属性）
--   · WF_NODE_CANDIDATE_CONF.approve_org_scope —— 运行时源（发布时由设计源写入，
--       TaskAssignmentListener 直读以决定候选机构过滤）
--
-- 双库：dev=yiti / prod=onepl
--   · yiti：两张表都执行（流程设计器已部署）
--   · onepl：仅 WF_NODE_CANDIDATE_CONF 执行（运行时表已存在）；WF_FLOW_NODE
--     为设计器表，onepl 尚未部署流程设计器，待部署设计器 DDL 时一并带上该列。
-- 幂等：列已存在会报 Duplicate column，可忽略。
-- ============================================================================

-- yiti + onepl 通用（运行时表）
ALTER TABLE WF_NODE_CANDIDATE_CONF
  ADD COLUMN approve_org_scope varchar(16) NULL
  COMMENT '审批机构归属:SELF=本机构/PARENT=上级机构/NULL=不判断' AFTER candidate_value;

-- 仅 yiti（流程设计器设计源表）
ALTER TABLE WF_FLOW_NODE
  ADD COLUMN approve_org_scope varchar(16) NULL
  COMMENT '审批机构归属:SELF=本机构/PARENT=上级机构/NULL=不判断' AFTER approve_mode;
