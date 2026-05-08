-- ============================================================================
-- 给 admin 账号补 R_PRESIDENT 角色，解决「无权访问仪表盘」(RPT-40301)
-- ----------------------------------------------------------------------------
-- 背景
--   DashboardServiceImpl#getPresidentDashboard() 第 100~107 行做硬编码角色校验：
--     Set<String> roles = currentUserApi.getCurrentRoleIds();
--     if (!roles.contains("R_PRESIDENT")) throw new RptException(DASHBOARD_NO_ACCESS);
--   注意校验的是 PT_ROLE.ROLE_ID（不是 ROLE_CODE），所以 admin 必须在 PT_USER_ROLE
--   里和 ROLE_ID='R_PRESIDENT' 关联。
--
-- 适用环境
--   开发/测试库 onepl（生产请走正式权限申请流程，不要直接跑这条 SQL）
-- 执行
--   mysql -uroot -p123456 -h127.0.0.1 -P3306 onepl < 2026-05-07-grant-admin-r-president.sql
-- 幂等性
--   - PT_ROLE 用 INSERT ... ON DUPLICATE KEY UPDATE，重复执行只刷新 ROLE_CHNAME
--   - PT_USER_ROLE 主键 (USER_ID, ROLE_ID) 用 INSERT IGNORE，已存在不报错
-- 回滚
--   DELETE FROM PT_USER_ROLE WHERE USER_ID='admin' AND ROLE_ID='R_PRESIDENT';
-- ============================================================================

-- 1) 确保 R_PRESIDENT 角色行存在（生产 ROLE_CODE='BRANCH_PRE'）
INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER)
VALUES ('R_PRESIDENT', 'BRANCH_PRE', '分行行长', 0, 'PLATFORM', NOW(), 'manual-grant')
ON DUPLICATE KEY UPDATE
  ROLE_CHNAME   = VALUES(ROLE_CHNAME),
  RECORD_STATUS = 0;

-- 2) 给 admin 账号绑定 R_PRESIDENT
INSERT IGNORE INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN, INHERIT_ASSIGN, GROUP_ASSING, CREATE_TIME)
VALUES ('admin', 'R_PRESIDENT', 0, 0, 0, NOW());

-- 3) 验证
SELECT ur.USER_ID, ur.ROLE_ID, r.ROLE_CODE, r.ROLE_CHNAME, ur.CREATE_TIME
FROM PT_USER_ROLE ur
JOIN PT_ROLE r ON ur.ROLE_ID = r.ROLE_ID
WHERE ur.USER_ID = 'admin';
