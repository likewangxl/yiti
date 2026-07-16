-- =====================================================================
-- 分配关系调整审批：original_owner_approve 节点审批人切换
--   「原业绩分配人本人」 → 「原业绩所属 2 级机构的机构负责人(BRANCH_HEAD)」
--
-- ⚠️ 执行时机（两步上线的第 2 步）：
--   必须在包含 AllocAdjustService.resolveOriginalOwnerOrgLeaderEmpIds 的
--   新后端部署重启之后执行。新代码先上（同时写 originalOwnerEmpIds 与
--   originalOwnerOrgLeaderEmpIds 两个变量，节点仍用旧变量，行为不变）；
--   执行本脚本后，新发起的流程该节点即按机构负责人会签。
--   在途流程实例读的是发起时已写入的变量，新旧切换互不影响。
--
-- 影响面：alloc_corp_designer（对公）/ alloc_retail_designer（零售）
-- 回滚：将 candidate_value / approver_value 改回 originalOwnerEmpIds，
--       节点/边名改回「原业绩所属人审批/会签」即可。
-- =====================================================================

-- 1) 运行时候选配置（MultiInstanceApproverResolver 直读此表，改后立即对新任务生效）
UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value = '["originalOwnerOrgLeaderEmpIds"]'
 WHERE process_definition_key IN ('DSN_alloc_corp_designer', 'DSN_alloc_retail_designer')
   AND node_key = 'original_owner_approve'
   AND candidate_type = 'VAR';

-- 2) 设计器源表同步（防止下次在设计器重新发布时回退为旧变量）
UPDATE WF_FLOW_NODE_APPROVER a
  JOIN WF_FLOW_NODE n ON a.node_id = n.id
  JOIN WF_FLOW_DEF d ON n.flow_def_id = d.id
   SET a.approver_value = 'originalOwnerOrgLeaderEmpIds'
 WHERE d.flow_key IN ('alloc_corp_designer', 'alloc_retail_designer')
   AND n.node_key = 'original_owner_approve'
   AND a.approver_type = 'VAR';

-- 3) 节点显示名（设计器/进度展示）
UPDATE WF_FLOW_NODE n
  JOIN WF_FLOW_DEF d ON n.flow_def_id = d.id
   SET n.name = '原业绩所属机构负责人审批'
 WHERE d.flow_key IN ('alloc_corp_designer', 'alloc_retail_designer')
   AND n.node_key = 'original_owner_approve';

-- 4) 指向该节点的网关出边名（审批弹窗「下一步走向」选项文案即边名）
UPDATE WF_FLOW_EDGE e
  JOIN WF_FLOW_DEF d ON e.flow_def_id = d.id
  JOIN WF_FLOW_NODE tn ON e.to_node_id = tn.id
   SET e.name = CASE WHEN e.name LIKE '%会签%' THEN '原业绩所属机构负责人会签'
                     ELSE '原业绩所属机构负责人审批' END
 WHERE d.flow_key IN ('alloc_corp_designer', 'alloc_retail_designer')
   AND tn.node_key = 'original_owner_approve';

-- 验证（预期各 2 行）：
-- SELECT node_key, candidate_value FROM WF_NODE_CANDIDATE_CONF
--  WHERE process_definition_key LIKE 'DSN_alloc_%' AND node_key='original_owner_approve';
-- SELECT n.name FROM WF_FLOW_NODE n JOIN WF_FLOW_DEF d ON n.flow_def_id=d.id
--  WHERE d.flow_key LIKE 'alloc_%' AND n.node_key='original_owner_approve';

-- 备注：
-- * 待办列表的任务名若取自已部署 BPMN（Flowable ACT_ 表），本脚本改名后旧名可能仍显示，
--   在设计器打开两条流程各重新发布一次即可彻底同步（candidate 解析不受影响，已即时生效）。
-- * 上线前请确认各分行(2级机构)均已给 BRANCH_HEAD 角色配置持有者，否则发起时会被
--   fail-fast 拦截：「原业绩所属机构[X]未配置机构负责人(BRANCH_HEAD)，无法发起审批」。
--   预检 SQL（列出无 BRANCH_HEAD 持有者的 2 级机构）：
--   SELECT o.ORG_CODE, o.ORG_NAME FROM EXT_ORG_INFO o
--    WHERE o.ORG_LEVEL = 2 AND o.ORGAN_STATE = 0
--      AND NOT EXISTS (
--        SELECT 1 FROM PT_USER_ROLE ur
--          JOIN PT_ROLE r ON ur.ROLE_ID = r.ROLE_ID AND r.ROLE_CODE = 'BRANCH_HEAD'
--          JOIN EXT_USER_ORG uo ON uo.USER_ID = ur.USER_ID AND uo.ORG_CODE = o.ORG_CODE);
