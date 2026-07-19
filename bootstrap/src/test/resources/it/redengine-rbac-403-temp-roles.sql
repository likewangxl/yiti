-- ============================================================
-- RedEngineSmokeIT 补充用例（终审 F-2 收口）：写/导出端点 RBAC 403 回归 —— 前置临时角色绑定
--
-- 用途：库内核查（2026-07-19）确认 onepl_test_bootstrap 当前无任何账号绑定
-- RE_ROLE_1..4（PT_USER_ROLE 无 RE_ROLE_% 行），故按 task-17d-report.md §五.1 同款模式，
-- 给既有测试账号 E60001(reviewer_chen) 临时追加两行 DEFAULT_ASSIGN=0 的党建角色绑定，
-- 测试内再用 POST /api/auth/switch-role 显式切换到目标角色（仅本会话生效，不落库）。
--
-- 为什么选 E60001 而非沿用 task-17d 的 tech_wu(E40002)：
--   查库发现 tech_wu 现有唯一角色 R_BACK_TECH 的 DEFAULT_ASSIGN 实际是 0（非 1）——
--   AuthService.resolvePrimaryRoleAndOrder 在查无 DEFAULT_ASSIGN=1 行时，回退到
--   selectRolesByUserId 结果集的第一个元素（该查询无 ORDER BY，PT_USER_ROLE 主键
--   为(USER_ID,ROLE_ID)，MySQL 常按聚簇索引序返回，'RE_ROLE_2' 字典序小于
--   'R_BACK_TECH'）。若给 tech_wu 追加 RE_ROLE_* 行，存在把登录后主角色"意外"从
--   R_BACK_TECH 漂移到 RE_ROLE_2 的风险，将破坏本文件既有第 3 个用例
--   （orgTree_roleWithoutRedEnginePermission_returns403，断言 tech_wu 登录后无
--   P_RE_* 权限）。E60001(reviewer_chen) 的主角色 R_CREDIT_REVIEWER 经查
--   DEFAULT_ASSIGN=1，AuthService.selectPrimaryRoleId 按 DEFAULT_ASSIGN=1 直接
--   SQL 查询定位主角色（不依赖列表顺序），追加 DEFAULT_ASSIGN=0 的新角色行不会
--   改变其登录后解析出的主角色，零副作用。
--
-- 仅执行于 onepl_test_bootstrap；WHERE NOT EXISTS 保证幂等重跑不冲突；
-- 配套 redengine-rbac-403-temp-roles-cleanup.sql 在 AFTER_TEST_METHOD 精确删除同两行，无残留。
-- ============================================================

INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN, INHERIT_ASSIGN, GROUP_ASSING)
SELECT 'E60001', 'RE_ROLE_4', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM PT_USER_ROLE WHERE USER_ID = 'E60001' AND ROLE_ID = 'RE_ROLE_4');

INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN, INHERIT_ASSIGN, GROUP_ASSING)
SELECT 'E60001', 'RE_ROLE_2', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM PT_USER_ROLE WHERE USER_ID = 'E60001' AND ROLE_ID = 'RE_ROLE_2');

-- ------------------------------------------------------------
-- 补registrant：POST /api/auth/switch-role 在 onepl_test_bootstrap 缺失 PT_RESOURCE
-- 登记（实测核实：yiti_test 已有 A_SWITCH_ROLE 资源且绑定 37/41 个角色，onepl_test_bootstrap
-- 该资源 0 行——两库历史种子漂移，onepl_test_bootstrap 这份缺口非本任务引入）。
-- 缺失时 ResourceMatcher 判定"未登记"，任何角色（含 SYS_ADMIN）调用 switch-role 都会
-- 403 AUTH-40302，而非按角色返回 200/403，导致测试内"切到党建角色"这一步本身先炸。
-- 照抄 yiti_test 的 A_SWITCH_ROLE 行结构原样登记，并仅绑定 reviewer_chen 当前激活角色
-- R_CREDIT_REVIEWER（切换发生在切换前的角色下，只需这一个角色能通过 switch-role 本身的
-- 资源校验），不扩大绑定面。INSERT IGNORE + MD5 主键保证幂等。
-- ------------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('A_SWITCH_ROLE', '/api/auth/switch-role', 'POST', '切换当前角色', 0, 0, NULL, 0, 'PLATFORM', 'redengine-merge-final-review-fix',
  '终审 F-2 收口临时补登记：onepl_test_bootstrap 缺失该资源，照抄 yiti_test 同名行，测试后清理');

INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT('R_CREDIT_REVIEWER', '#', 'A_SWITCH_ROLE')), 'R_CREDIT_REVIEWER', 'A_SWITCH_ROLE', 'PLATFORM';
