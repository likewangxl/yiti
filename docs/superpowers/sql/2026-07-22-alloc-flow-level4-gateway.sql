-- ============================================================================
-- 分配调整审批流程：机构层级路由网关支持「四级机构（网点）」发起
-- ----------------------------------------------------------------------------
-- 背景：alloc 两条流程（对公 FDEF_ALLOC_CORP / 零售 FDEF_ALLOC_RETAIL）的机构层级路由
--   排他网关(gw_level)原只有 startOrgLevel==2 / ==3 两条出边；实际业务已有 4 级机构，
--   四级网点发起时 startOrgLevel==4 无匹配出边，排他网关抛 "No outgoing sequence flow"。
--
-- 修法（最小改动，见 spec 2026-06-08-approver-model-level-org-role 的 L2 层级）：
--   1) branch_approve_l3 的审批人机构归属 PARENT → L2。
--      ⚠️ 现网流程用「新审批人模型」：层级角色 org_scope 落在 WF_FLOW_NODE_APPROVER 行上
--      （branch_approve_l3 两个 LEVEL_ROLE 审批人 BRANCH_PRE/BRANCH_HEAD 现为 PARENT），
--      节点级 WF_FLOW_NODE.approve_org_scope 为 NULL（新模型不用节点级），故必须改审批人行、
--      不能改节点列。L2=沿 P_ID 上溯到 2 级分行：三级支行 L2 = 其上级分行(与旧 PARENT 等价)，
--      四级网点 L2 = 所属分行(PARENT 只能到三级支行，故必须用 L2)。
--   2) gw_level → branch_approve_l3 入边条件 startOrgLevel EQ 3 → GE 3（覆盖 3 级及更深，
--      EL 变 `startOrgLevel >= 3`，startOrgLevel 种入为 Integer，数字比较）。
--   3) branch_approve_l2 保持 SELF（二级分行本机构审批），不变。
--   结果：level 2→SELF(分行本身)、level≥3→L2(所属分行)，任意深度不再无匹配出边。
--
-- 前置：本机构树需为规范的 总行1→分行2→支行3→网点4 层级（L2 依赖能上溯到 ORG_LEVEL=2）。
-- 幂等：可重复执行（按 node_key 定位；org_scope 加 =PARENT 守卫，重复执行不重复翻转）。
--   库表大写。双库 dev=yiti（本期）。
--
-- ⚠️ 执行后必须在流程设计器里【重新发布】对公/零售两条流程，运行时(DSN_ 影子定义 +
--    WF_NODE_CANDIDATE_CONF)才会用上新的网关条件与 L2 审批机构归属；只改设计源表不发布不生效。
-- ============================================================================

-- 1) branch_approve_l3 审批人层级角色机构归属 PARENT → L2（审批人行，非节点级）
UPDATE WF_FLOW_NODE_APPROVER a
   JOIN WF_FLOW_NODE n
     ON n.id = a.node_id
    SET a.org_scope = 'L2'
 WHERE n.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
   AND n.node_key = 'branch_approve_l3'
   AND a.org_scope = 'PARENT';

-- 2) branch_approve_l3 节点名去掉"3级"歧义（节点级 name，覆盖 3 级及以下）
UPDATE WF_FLOW_NODE
   SET name = '机构负责人审批·所属分行'
 WHERE flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
   AND node_key = 'branch_approve_l3';

-- 3) gw_level → branch_approve_l3 出边条件 EQ 3 → GE 3（覆盖 3 级及更深）
UPDATE WF_FLOW_EDGE e
   JOIN WF_FLOW_NODE n
     ON n.id = e.to_node_id
    AND n.flow_def_id = e.flow_def_id
    SET e.condition_json = '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"GE","value":"3"}]}',
        e.name = '3级及以下机构（所属分行审批）'
 WHERE e.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
   AND n.node_key = 'branch_approve_l3';
