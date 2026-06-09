-- ============================================================================
-- 业绩调整 branch_approve（机构负责人审批）再增加「支行领导(R_5F1F1A19)」候选角色
-- ----------------------------------------------------------------------------
-- 背景：在 BRANCH_HEAD(经营机构负责人) + BRANCH_PRE(分行行长) 基础上再加支行领导。
--      审批机构路由不变（3级支行→上级分行 / 2级分行→本机构），仅扩充候选角色：
--      最终 3 角色 ∩ 审批机构 = ["BRANCH_HEAD", "BRANCH_PRE", "R_5F1F1A19"]。
--      注意：支行领导 ROLE_CODE 字面为 R_5F1F1A19（ROLE_ID=R_10552DF9，ROLE_CHNAME=支行领导）。
--
-- 范围：业绩调整 corp_v1 / retail_v1（loan_approve_v1 不在范围）。
-- 双库：yiti + onepl 的 WF_NODE_CANDIDATE_CONF 均执行 §1；设计源 WF_FLOW_NODE_APPROVER 仅 yiti(§2)。
--      ⚠ onepl 暂无「支行领导」角色（PT_ROLE 无 R_5F1F1A19），该角色在 onepl 解析为空（无候选），
--        待 onepl 建好同款角色后即生效；配置先保持两库一致。
-- 生效：CandidateResolverService 运行时直读，无缓存，对新提交即时生效，无需重启。
-- ============================================================================

-- §1 运行时候选配置：追加 R_5F1F1A19（幂等：已含则跳过）
UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value = '["BRANCH_HEAD", "BRANCH_PRE", "R_5F1F1A19"]',
       updated_time = NOW()
 WHERE node_key = 'branch_approve'
   AND candidate_type = 'ROLE'
   AND process_definition_key IN ('perf_alloc_adjust_corp_v1', 'perf_alloc_adjust_retail_v1')
   AND candidate_value NOT LIKE '%R_5F1F1A19%';

-- §2 设计源审批人（仅 yiti；corp=bbfe5f84..., retail=59dd13b3...，sort_no=3，幂等）
INSERT INTO WF_FLOW_NODE_APPROVER (id, node_id, approver_type, approver_value, sort_no, created_time)
SELECT REPLACE(UUID(), '-', ''), 'bbfe5f84be2946bdb0d61992b5b3636c', 'ROLE', 'R_5F1F1A19', 3, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM WF_FLOW_NODE_APPROVER
                  WHERE node_id = 'bbfe5f84be2946bdb0d61992b5b3636c' AND approver_value = 'R_5F1F1A19');

INSERT INTO WF_FLOW_NODE_APPROVER (id, node_id, approver_type, approver_value, sort_no, created_time)
SELECT REPLACE(UUID(), '-', ''), '59dd13b3108c4150b8bdff4969299feb', 'ROLE', 'R_5F1F1A19', 3, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM WF_FLOW_NODE_APPROVER
                  WHERE node_id = '59dd13b3108c4150b8bdff4969299feb' AND approver_value = 'R_5F1F1A19');
