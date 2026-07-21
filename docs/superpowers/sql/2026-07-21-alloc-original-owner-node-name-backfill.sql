-- =====================================================================
-- original_owner_approve 节点显示名回填：Flowable 三张快照表
--
-- 背景：2026-07-16 的 ...-alloc-original-owner-org-leader-switch.sql 把该节点从
--   「原业绩分配人本人」切到「原业绩所属 2 级机构负责人(BRANCH_HEAD)会签」，
--   同时改了设计器源表 WF_FLOW_NODE.name / WF_FLOW_EDGE.name。但节点显示名在 Flowable 侧
--   是<多处快照>：部署期写进 BPMN、运行期写进任务行、历史期写进活动/任务历史行。
--   该脚本执行时既没有重新发布流程（BPMN 未刷新），也没有回填这些快照，于是：
--     - 工作台待办任务名   ← ACT_RU_TASK.NAME_        （任务创建时快照）
--     - 业绩调整审批历史   ← ACT_HI_ACTINST.ACT_NAME_ （活动开始时快照，ProcessQueryService
--                                                      经 HistoricActivityInstance 取用）
--     - 已办任务列表       ← ACT_HI_TASKINST.NAME_
--   三处都仍显示旧名「原业绩所属人审批」。
--
-- 治本已完成：设计器两条流程已重新发布（DSN_alloc_corp_designer v7 / retail v4，
--   BPMN 内已确认为 name="原业绩所属机构负责人审批"），此后<新发起>的流程显示正确。
--   本脚本只处理<已存在>的快照行。
--
-- ⚠️ 时间边界是本脚本的关键：仅回填 2026-07-16（切换日）之后的记录。
--   之前的 22 条是<真实历史>——当时审批人确实是原业绩所属人本人，
--   改名等于伪造审计痕迹，绝不能动。
--
-- ⚠️ 同样不改静态 BPMN（perf_alloc_adjust_{corp,retail}_v1.bpmn20.xml）的节点名：
--   其 assignee=${ownerEmpId} 仍是原业绩所属人本人语义，旧名与其行为相符
--   （详见 AllocAdjustService#resolveProcessKey 的回退补注）。
--
-- 幂等：带 ACT_NAME_/NAME_ 旧值条件，重复执行影响 0 行。仅执行库 yiti（dev）。
-- 执行记录：2026-07-21 已在 yiti 执行，ACT_HI_TASKINST 1 行、ACT_HI_ACTINST 1 行，
--   ACT_RU_TASK 当时已无在途行（该实例已流转完）。
-- =====================================================================

-- 1) 运行时任务（在途待办的显示名）
UPDATE ACT_RU_TASK SET NAME_ = '原业绩所属机构负责人审批'
 WHERE TASK_DEF_KEY_ = 'original_owner_approve'
   AND NAME_ = '原业绩所属人审批'
   AND CREATE_TIME_ >= '2026-07-16';

-- 2) 历史任务实例（已办列表）
UPDATE ACT_HI_TASKINST SET NAME_ = '原业绩所属机构负责人审批'
 WHERE TASK_DEF_KEY_ = 'original_owner_approve'
   AND NAME_ = '原业绩所属人审批'
   AND START_TIME_ >= '2026-07-16';

-- 3) 历史活动实例（业绩调整「审批历史」时间线取此表）
UPDATE ACT_HI_ACTINST SET ACT_NAME_ = '原业绩所属机构负责人审批'
 WHERE ACT_ID_ = 'original_owner_approve'
   AND ACT_NAME_ = '原业绩所属人审批'
   AND START_TIME_ >= '2026-07-16';

-- ── 验证 ────────────────────────────────────────────────────
-- 预期：切换前一律旧名（真实历史，不得被改动）；切换后一律新名
SELECT 'ACT_HI_ACTINST' AS tbl,
       CASE WHEN START_TIME_ >= '2026-07-16' THEN '切换后' ELSE '切换前(历史)' END AS seg,
       ACT_NAME_, COUNT(*) AS cnt
  FROM ACT_HI_ACTINST WHERE ACT_ID_ = 'original_owner_approve' GROUP BY seg, ACT_NAME_
UNION ALL
SELECT 'ACT_HI_TASKINST',
       CASE WHEN START_TIME_ >= '2026-07-16' THEN '切换后' ELSE '切换前(历史)' END,
       NAME_, COUNT(*)
  FROM ACT_HI_TASKINST WHERE TASK_DEF_KEY_ = 'original_owner_approve' GROUP BY 2, NAME_
UNION ALL
SELECT 'ACT_RU_TASK', '在途', NAME_, COUNT(*)
  FROM ACT_RU_TASK WHERE TASK_DEF_KEY_ = 'original_owner_approve' GROUP BY NAME_;
