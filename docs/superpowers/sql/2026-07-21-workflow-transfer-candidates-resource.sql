-- =====================================================================
-- 转交接收人候选查询端点 PT_RESOURCE + 角色绑定
--   GET /api/workflow/monitor/tasks/{taskId}/transfer-candidates  RES_WF_TRF_CAND
--
-- 背景：转交弹窗此前列的是「本机构全部人员」（OrgController /api/orgs/{orgCode}/users），
--   节点办理资格不在前端校验、只在提交时由 initiate 抛 WF-40912 兜底，导致用户能选中
--   一个注定失败的接收人（典型：流程发起后才被授予 BRANCH_HEAD 的人不在任务身份链接
--   快照内，选了必被打回）。新端点直接返回与 initiate 同源的可选集。
--
-- 角色绑定口径：与发起端点 RES_WF_TRF_INIT 完全一致（秘书岗=231、分行行长=2）。
--   候选人名单等价于「谁能办理这个节点」，属与发起同级的敏感信息，鉴权不得比发起更松；
--   Controller 侧同样声明 @BizAuth(WORKFLOW_MONITOR, TRANSFER)。
--
-- URL 匹配说明：'*' 为单段通配，'/api/workflow/monitor/tasks/*/transfer' 不会误匹配
--   本端点的 '.../transfer-candidates'（且二者 METHOD 不同：POST vs GET），无需担心串权。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅涉及本脚本自身新增的 RESOURCE_ID，不触碰既有资源行，故未单独 mysqldump。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_CAND';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'RES_WF_TRF_CAND';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_WF_TRF_CAND', '/api/workflow/monitor/tasks/*/transfer-candidates', 'GET',
   '转交接收人候选', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-21',
   'v2 转交弹窗只列有资格接收人');

-- 与发起端点同口径：秘书岗(231)/分行行长(2)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('WFTRF_231_CAND','231','RES_WF_TRF_CAND','PLATFORM',NOW()),
  ('WFTRF_2_CAND',  '2',  'RES_WF_TRF_CAND','PLATFORM',NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 1 行资源
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU
  FROM PT_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_CAND';
-- 预期 role_count = 2，且与 RES_WF_TRF_INIT 的角色集合一致
SELECT RESOURCE_ID, COUNT(*) AS role_count
  FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('RES_WF_TRF_CAND','RES_WF_TRF_INIT')
 GROUP BY RESOURCE_ID ORDER BY RESOURCE_ID;
