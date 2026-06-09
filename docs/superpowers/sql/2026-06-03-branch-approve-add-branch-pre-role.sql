-- ============================================================================
-- 业绩调整审批：branch_approve（机构负责人审批）节点增加「分行行长(BRANCH_PRE)」候选角色
-- ----------------------------------------------------------------------------
-- 背景：branch_approve 节点原仅 ROLE=BRANCH_HEAD（经营机构负责人）可审批。
--      现要求二级分行的分行行长(ROLE_CODE=BRANCH_PRE / 分行行长)也可审批。
--      审批机构仍按发起机构层级解析（支行→上级分行），候选人 = 该分行下持
--      BRANCH_HEAD 或 BRANCH_PRE 角色的在职用户（TaskAssignmentListener 按机构过滤）。
--
-- 范围：仅业绩调整两条流程 perf_alloc_adjust_corp_v1 / perf_alloc_adjust_retail_v1
--      （loan_approve_v1 属贷款审批，另议，不在此变更）。
--
-- 生效：CandidateResolverService 运行时直读 WF_NODE_CANDIDATE_CONF（无缓存），
--      对“新提交”的申请即时生效，无需重启；已在途任务的候选人不回灌（创建时已定）。
--
-- 双库：dev=yiti / prod=onepl 均需执行。
--   · WF_NODE_CANDIDATE_CONF（运行时源，两库结构/键一致）→ 两库都执行 §1
--   · WF_FLOW_NODE_APPROVER（流程编辑器设计源，仅 yiti 有该节点设计行）→ 仅 yiti 执行 §2
--     作用：防止将来从流程编辑器“重新发布”时 FlowPublishService 先删后插覆盖掉 §1。
-- ============================================================================

-- §1 运行时候选配置：BRANCH_HEAD → [BRANCH_HEAD, BRANCH_PRE]（幂等：已含则跳过）
UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value = '["BRANCH_HEAD", "BRANCH_PRE"]',
       updated_time = NOW()
 WHERE node_key = 'branch_approve'
   AND candidate_type = 'ROLE'
   AND process_definition_key IN ('perf_alloc_adjust_corp_v1', 'perf_alloc_adjust_retail_v1')
   AND candidate_value NOT LIKE '%BRANCH_PRE%';

-- §2 设计源审批人（仅 yiti 执行；onepl 无对应 WF_FLOW_NODE 设计行，跳过）
--    node_id：corp_v1=bbfe5f84be2946bdb0d61992b5b3636c / retail_v1=59dd13b3108c4150b8bdff4969299feb
INSERT INTO WF_FLOW_NODE_APPROVER (id, node_id, approver_type, approver_value, sort_no, created_time)
SELECT REPLACE(UUID(), '-', ''), 'bbfe5f84be2946bdb0d61992b5b3636c', 'ROLE', 'BRANCH_PRE', 2, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM WF_FLOW_NODE_APPROVER
                  WHERE node_id = 'bbfe5f84be2946bdb0d61992b5b3636c' AND approver_value = 'BRANCH_PRE');

INSERT INTO WF_FLOW_NODE_APPROVER (id, node_id, approver_type, approver_value, sort_no, created_time)
SELECT REPLACE(UUID(), '-', ''), '59dd13b3108c4150b8bdff4969299feb', 'ROLE', 'BRANCH_PRE', 2, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM WF_FLOW_NODE_APPROVER
                  WHERE node_id = '59dd13b3108c4150b8bdff4969299feb' AND approver_value = 'BRANCH_PRE');
