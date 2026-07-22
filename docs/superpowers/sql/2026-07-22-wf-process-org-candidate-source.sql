-- ============================================================================
-- 审批流参与机构快照：新增 CANDIDATE 来源 + 回填历史在途流程的候选审批机构
-- ----------------------------------------------------------------------------
-- 背景：WF_PROCESS_ORG 原先只在「任务已有具体受理人」时写入（TaskAssignmentListener
--   的 delegateTask.getAssignee() != null 分支）。而层级角色审批节点走的是**候选人模式**
--   （ACT_RU_TASK.ASSIGNEE_ = NULL，只有 ACT_RU_IDENTITYLINK 候选人），于是「轮到某分行
--   审批」这一事实在有人签收前从不进快照表。
--   后果：审批流监控页（ProcessMonitorService，按 WF_PROCESS_ORG EXISTS 过滤数据范围）
--   下，该分行 DATA_SCOPE=ORG 的秘书岗看不到正等本行审批的流程。
--   实例：金台支行(330,3级) 员工发起业绩调整 → 宝鸡分行(128,2级) 机构负责人为候选审批人，
--   但快照里只有 330(START) 一行，宝鸡分行秘书监控页为空。
--
-- 代码侧修复（同一提交）：TaskAssignmentListener 在按机构归属过滤候选人时，同步调用
--   WfProcessOrgService.recordOrg(pi, approveOrg, "CANDIDATE") 落快照。
--
-- 本脚本做两件事：
--   1) 扩充 source 列注释，登记新增的 CANDIDATE 来源；
--   2) 回填历史在途流程——按当前活跃任务的候选**用户**（ACT_RU_IDENTITYLINK.TYPE_='candidate'
--      且 USER_ID_ 非空，即经机构过滤后指派的候选人）的主机构落快照。
--      仅回填候选用户，不回填候选组（GROUP_ID_）——候选组是「不限机构」语义，没有确定的
--      审批机构，硬塞会把无关机构带进监控范围。
--
-- 幂等：uk_pi_org(process_instance_id, org_code) + INSERT IGNORE，可重复执行。
--   库表大写。dev=yiti。本脚本只增不删，无破坏性，无需备份。
-- ============================================================================

-- 1) 登记 CANDIDATE 来源
ALTER TABLE WF_PROCESS_ORG
  MODIFY COLUMN source varchar(16) NOT NULL
  COMMENT 'START/ASSIGN/CANDIDATE/CLAIM/APPROVE/TRANSFER/BACKFILL';

-- 2) 回填在途流程的候选审批机构（source 标 BACKFILL，与既有回填口径一致）
INSERT IGNORE INTO WF_PROCESS_ORG (id, process_instance_id, org_code, source, first_seen_time)
SELECT REPLACE(UUID(), '-', ''), t.PROC_INST_ID_, uo.ORG_CODE, 'BACKFILL', NOW()
  FROM ACT_RU_TASK t
  JOIN ACT_RU_IDENTITYLINK il
    ON il.TASK_ID_ = t.ID_
   AND il.TYPE_ = 'candidate'
   AND il.USER_ID_ IS NOT NULL
  JOIN EXT_USER_ORG uo
    ON uo.USER_ID = il.USER_ID_
 GROUP BY t.PROC_INST_ID_, uo.ORG_CODE;
