-- 2026-05-29 corp_v1 原业绩所属人审批改并行会签后，移除其候选组配置
-- 原因：original_owner_approve 改为多实例会签（每个原业绩所属人一个子任务，assignee=${ownerEmpId}）。
--      若保留 ROLE:["R_RM"] 候选配置，TaskAssignmentListener 会给每个子任务附加 R_RM 候选组，
--      导致任意 RM 都可认领特定所属人的会签任务，违背"原业绩分配模块所有用户各自审批"语义。
-- 范围：仅 corp_v1（retail_v1 不在本次改造范围，保留其单人 original_owner_approve + 候选配置）。
-- 双库执行：dev(yiti) + 生产(onepl)。

DELETE FROM WF_NODE_CANDIDATE_CONF
 WHERE process_definition_key = 'perf_alloc_adjust_corp_v1'
   AND node_key = 'original_owner_approve';
