-- ============================================================
-- V1.13 # 1g: bootstrap test fixture 恢复（仅 onepl_test_bootstrap）
-- ============================================================
-- 背景：根据 onepl 重建 onepl_test_bootstrap 后，bootstrap surefire 3 个测试失败：
--   - CrossModuleApiTest.dictApi_getDictItems_returnsList: SYS_DICT INDUSTRY 期望 2 条，
--     onepl 真实数据 11 条
--   - CrossModuleApiTest.dictApi_batchGetDictItems_returnsMultiple: 同上
--   - FullAuthChainTest.login_success_establishesSession: admin PWD hash 不能验证明文 "password"
--
-- 关键发现（BCrypt 实测验证）：
--   $2a$10$N9qo8uLOick…  ← 测试代码 FullAuthChainTest.java:43 注释里的 hash，验证为 NONE（注释错的）
--   $2a$10$nURd20BPbYG…  ← bootstrap data.sql 里的 hash，验证为 "password" ✅ 真值
--   $2b$10$16t1Spyl…     ← onepl 库 admin PWD（生产数据），验证为 "123456"
--
-- 决策：minimal fixture 覆盖 onepl 镜像的 INDUSTRY 字典 + admin 密码改成 data.sql 已知的真 hash，
-- 其他字典 / 用户保持 onepl 真实数据不变。
--
-- 执行：仅 onepl_test_bootstrap，不动 onepl / yiti。
-- ============================================================

USE onepl_test_bootstrap;

-- ============================================================
-- 1. SYS_DICT INDUSTRY 字典：11 条（onepl 镜像）→ 2 条（bootstrap 测试期望）
-- ============================================================
DELETE FROM SYS_DICT WHERE dict_type='INDUSTRY';
INSERT INTO SYS_DICT
  (id, dict_type, dict_code, dict_label, dict_value, sort_order, status)
VALUES
  ('D001','INDUSTRY','IT', '信息技术','IT', 1,'ACTIVE'),
  ('D002','INDUSTRY','FIN','金融',    'FIN',2,'ACTIVE');
-- 列顺序：(id, dict_type, dict_code, dict_label, dict_value, sort_order, status)

-- ============================================================
-- 2. admin 密码 hash 改成 data.sql 已知能验证 "password" 的真 hash
-- ============================================================
UPDATE PT_USER
   SET PWD='$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy'
 WHERE USER_ID='admin';

-- ============================================================
-- 3. 撤销之前误写：SYS_DICT_ITEM 不是测试查询的表，恢复 onepl 镜像（INDUSTRY=0）
-- ============================================================
DELETE FROM SYS_DICT_ITEM WHERE dict_type='INDUSTRY';

-- ============================================================
-- 4. PORTAL_SHORTCUT 清 onepl 镜像污染（让 perf-fake-data.sql 自给自足）
-- ============================================================
-- onepl 镜像 3 条：TEST_SYS_01 (SYSTEM) + TEST_CUSTOM_E1 (CUSTOM emp_id=E10001) + TEST_CUSTOM_E2 (CUSTOM emp_id=E10002)
-- PortalWorkspaceMetricIT.workspace_shouldAggregateShortcuts_andGracefullyDegradeOtherPaths
-- 期望 listMyShortcuts(E10001)=2（fake data 注入 SC_SYS_01 + SC_CUST_E10001），
-- 但 onepl 镜像的 TEST_SYS_01 + TEST_CUSTOM_E1(E10001) 也会命中查询，导致总计 4 条。
-- perf-fake-data.sql 只清自己注入的 2 条 id，不能清 onepl 污染。
DELETE FROM PORTAL_SHORTCUT;
