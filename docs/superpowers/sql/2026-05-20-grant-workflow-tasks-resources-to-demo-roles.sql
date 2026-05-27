-- ============================================================
-- 给业绩调整三角色补 workflow 旧版 RES_WF_* 资源绑定（2026-05-20 修补）
-- ============================================================
-- 背景：PT_RESOURCE 里 /api/workflow/tasks* 同 URL 有两套 ID：
--   RES_WF_TODO / RES_WF_APPROVE / RES_WF_CLAIM / RES_WF_REJECT / RES_WF_DETAIL / RES_WF_DONE / RES_WF_TRANSFER
--   W_TASK_TODO / W_TASK_APPROVE  / W_TASK_CLAIM  / W_TASK_REJECT  / W_TASK_DETAIL  / W_TASK_DONE  / W_TASK_TRANSFER
-- ResourceMatcher 命中 RES_* 在前（字母序优先），BRANCH_PRE 只有 W_TASK_* → AUTH-40301。
--
-- 本脚本：给 R_RM / R_BRANCH_MGR / R_PRESIDENT 三个角色补 RES_WF_* 7 条，
-- 保证 demo 链路畅通。重复绑定由 MD5(role+res) 主键唯一性自动忽略。
-- ============================================================

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_TODO')),     'R_RM',         'RES_WF_TODO'),
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_DETAIL')),   'R_RM',         'RES_WF_DETAIL'),
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_CLAIM')),    'R_RM',         'RES_WF_CLAIM'),
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_APPROVE')),  'R_RM',         'RES_WF_APPROVE'),
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_REJECT')),   'R_RM',         'RES_WF_REJECT'),
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_DONE')),     'R_RM',         'RES_WF_DONE'),
  (MD5(CONCAT('R_RM',         '|', 'RES_WF_TRANSFER')), 'R_RM',         'RES_WF_TRANSFER'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_TODO')),     'R_BRANCH_MGR', 'RES_WF_TODO'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_DETAIL')),   'R_BRANCH_MGR', 'RES_WF_DETAIL'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_CLAIM')),    'R_BRANCH_MGR', 'RES_WF_CLAIM'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_APPROVE')),  'R_BRANCH_MGR', 'RES_WF_APPROVE'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_REJECT')),   'R_BRANCH_MGR', 'RES_WF_REJECT'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_DONE')),     'R_BRANCH_MGR', 'RES_WF_DONE'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'RES_WF_TRANSFER')), 'R_BRANCH_MGR', 'RES_WF_TRANSFER'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_TODO')),     'R_PRESIDENT',  'RES_WF_TODO'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_DETAIL')),   'R_PRESIDENT',  'RES_WF_DETAIL'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_CLAIM')),    'R_PRESIDENT',  'RES_WF_CLAIM'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_APPROVE')),  'R_PRESIDENT',  'RES_WF_APPROVE'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_REJECT')),   'R_PRESIDENT',  'RES_WF_REJECT'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_DONE')),     'R_PRESIDENT',  'RES_WF_DONE'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'RES_WF_TRANSFER')), 'R_PRESIDENT',  'RES_WF_TRANSFER')
ON DUPLICATE KEY UPDATE ROLE_ID=VALUES(ROLE_ID);
