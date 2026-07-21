-- =====================================================================
-- 流程转交历史查询端点 PT_RESOURCE + 角色绑定
--   GET /api/workflow/monitor/processes/{processInstanceId}/transfers  RES_WF_TRF_HIST
--
-- 背景：审批流监控详情抽屉去掉「流程进度图」后，底部新增「转交历史」区块，展示该流程实例被
--   转交/指派的全过程——谁转给谁、谁认领了、谁拒绝了、拒绝原因、发起与处理时间。
--   数据全部来自既有 WF_TASK_TRANSFER 表，无 schema 变更。
--
-- 角色绑定口径：与监控列表 RES_WF_MONITOR_LIST 一致（分行行长=2、秘书岗=231，实测确认）。
--   转交历史是监控详情的一部分，能看列表的人即可看；Controller 侧用
--   @BizAuth(WORKFLOW_MONITOR, READ)，而非 TRANSFER——后者是「能发起转交」的权限，
--   看历史不必要求这么高。
--
-- URL 匹配说明：'*' 为单段通配，与既有 '/api/workflow/monitor/processes'（GET，精确）
--   路径深度不同，不会互相误匹配。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅涉及本脚本自身新增的 RESOURCE_ID，不触碰既有资源行，故未单独 mysqldump。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_HIST';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'RES_WF_TRF_HIST';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_WF_TRF_HIST', '/api/workflow/monitor/processes/*/transfers', 'GET',
   '流程转交历史', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-21',
   'v2 监控详情转交历史');

-- 与监控列表同口径：分行行长(2)/秘书岗(231)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('WFTRF_2_HIST',  '2',  'RES_WF_TRF_HIST','PLATFORM',NOW()),
  ('WFTRF_231_HIST','231','RES_WF_TRF_HIST','PLATFORM',NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 1 行资源
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU
  FROM PT_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_HIST';
-- 预期两者 role_count 均为 2（与监控列表口径一致）
SELECT RESOURCE_ID, COUNT(*) AS role_count
  FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('RES_WF_TRF_HIST','RES_WF_MONITOR_LIST')
 GROUP BY RESOURCE_ID ORDER BY RESOURCE_ID;
