-- ============================================================
-- PerfTriggerAuthWhitelistRemovalIT 前置夹具：临时登记 P_PERF_CALC_TRIG 资源
--
-- 背景：/api/perf/metric-calc/trigger 的匿名白名单摘除后（2026-07-19），请求进入
-- AuthorizationInterceptor 真实 RBAC 链路；库内核查确认 onepl_test_bootstrap 的
-- PT_RESOURCE 没有该 URL 的资源行（生产基线 seed-yiti-prod-golive.sql 有，测试库
-- 2026-04 基线早于该资源 2026-05-27 的登记时间），ResourceMatcher 查无匹配按
-- Fail Close 拒绝（403），导致"admin 登录后可达参数绑定层"用例无法验证。
-- 故按 redengine-rbac-403-temp-roles.sql 同款模式临时插入资源行 + R_ADMIN 绑定，
-- 字段值对齐生产基线同一行（seed-yiti-prod-golive.sql L1345）。
--
-- 仅执行于 onepl_test_bootstrap；WHERE NOT EXISTS 幂等；CREATE_USER='it-fixture'
-- 作为清理指纹，配套 perf-trigger-auth-resource-cleanup.sql 在 AFTER_TEST_METHOD
-- 精确删除本文件插入的行（若未来测试库基线真的补了这条资源，指纹不匹配不会误删）。
-- ============================================================

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
SELECT 'P_PERF_CALC_TRIG', '/api/perf/metric-calc/trigger', 'POST', '手动触发指标计算', 0, 0, '0', 'M_PERF_METRICS', 0, 'PLATFORM', NOW(), 'it-fixture', 'IT fixture: PerfTriggerAuthWhitelistRemovalIT'
WHERE NOT EXISTS (SELECT 1 FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_CALC_TRIG');

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT 'ITPERFTRIG00001', 'R_ADMIN', 'P_PERF_CALC_TRIG', 'PLATFORM', NOW()
WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE WHERE ID = 'ITPERFTRIG00001');
