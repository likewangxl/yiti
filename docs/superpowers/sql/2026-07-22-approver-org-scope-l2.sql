-- ============================================================================
-- 层级角色审批人：org_scope 新增「二级机构」层级值 L2（仅列注释，无结构/数据变更）
-- ----------------------------------------------------------------------------
-- 背景：实际机构树最深达 4 级（总行1/分行2/支行3/网点4）。层级角色原只有
--   SELF(发起机构)/PARENT(发起上级机构，只跳一级)；四级网点发起时 PARENT 只能到三级支行。
--   新增 L2=二级机构：运行时沿 P_ID 上溯到机构等级=2 的分行，让机构负责人审批稳定落到分行。
-- 说明：L2 仅 2 字符，现有列宽 org_scope varchar(8) / approve_org_scope varchar(16) 已够，
--   无需扩宽、无需回填历史数据，本脚本只把列注释补上 L2 语义（幂等，可重复执行）。
-- 双库：
--   WF_FLOW_NODE_APPROVER（设计源表）—— 仅 yiti（onepl 未部署设计器表）。
--   WF_NODE_CANDIDATE_CONF（运行时表）—— yiti + onepl。
-- ============================================================================

-- 设计源表（仅 yiti）
ALTER TABLE WF_FLOW_NODE_APPROVER
  MODIFY COLUMN org_scope varchar(8) NULL COMMENT '层级角色:SELF=发起机构/PARENT=发起上级机构/L2=二级机构(上溯到所属分行)';

-- 运行时表（yiti + onepl）
ALTER TABLE WF_NODE_CANDIDATE_CONF
  MODIFY COLUMN approve_org_scope varchar(16) NULL COMMENT '审批机构归属:SELF=本机构/PARENT=上级机构/L2=二级机构(上溯到所属分行)/NULL=不判断';
