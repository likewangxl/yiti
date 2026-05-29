-- ============================================================================
-- 2026-05-28 目标修正审批候选人配置（幂等 / 双库可重跑）
-- ============================================================================
-- 问题：提交目标值修正审批后，资财部负责人的「待办」看不到任务。
-- 根因：WF_NODE_CANDIDATE_CONF 有 perf_alloc_adjust_corp/retail_v1 的配置，
--       但缺 perf_target_adjust_v1 / finance_leader_approve 行。
--       TaskAssignmentListener 调 CandidateResolverService.resolveCandidates(
--       'perf_target_adjust_v1','finance_leader_approve') 返回空 → 任务未设候选组
--       → 不出现在任何人的待办列表。
-- 修复：补一行 ROLE=FINANCE_LEADER（资财部负责人），与 alloc-adjust 的
--       finance_leader_approve 节点保持一致。
--
-- 执行：
--   mysql -u root -p<pwd> yiti  < 2026-05-28-target-adjust-candidate-conf.sql
--   mysql -u root -p<pwd> onepl < 2026-05-28-target-adjust-candidate-conf.sql
-- ============================================================================

INSERT INTO WF_NODE_CANDIDATE_CONF (id, process_definition_key, node_key,
                                    candidate_type, candidate_value)
SELECT 'WNC_TGT_ADJ_FIN_LDR', 'perf_target_adjust_v1', 'finance_leader_approve',
       'ROLE', '["FINANCE_LEADER"]'
WHERE NOT EXISTS (
    SELECT 1 FROM WF_NODE_CANDIDATE_CONF
    WHERE process_definition_key = 'perf_target_adjust_v1'
      AND node_key = 'finance_leader_approve'
);

-- 验证
-- SELECT * FROM WF_NODE_CANDIDATE_CONF WHERE process_definition_key='perf_target_adjust_v1';
