-- ============================================================
-- RedEngineSmokeIT 补充用例（终审 F-2 收口）：写/导出端点 RBAC 403 回归 —— 临时角色绑定清理
-- 精确删除 redengine-rbac-403-temp-roles.sql 插入的同两行，测试自清理不留残留。
-- 仅执行于 onepl_test_bootstrap。
-- ============================================================

DELETE FROM PT_USER_ROLE WHERE USER_ID = 'E60001' AND ROLE_ID IN ('RE_ROLE_4', 'RE_ROLE_2');

-- 精确清理 redengine-rbac-403-temp-roles.sql 补登记的 A_SWITCH_ROLE 资源 + 绑定，
-- 恢复 onepl_test_bootstrap 到测试前状态（该资源测试前 0 行，测试后同样 0 行，无残留）。
DELETE FROM PT_ROLE_RESOURCE WHERE ROLE_ID = 'R_CREDIT_REVIEWER' AND RESOURCE_ID = 'A_SWITCH_ROLE';
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID = 'A_SWITCH_ROLE';
