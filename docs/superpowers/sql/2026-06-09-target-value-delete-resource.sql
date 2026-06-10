-- ============================================================================
-- 2026-06-09 新增端点资源注册：目标值物理删除
--   DELETE /api/perf/target-values/{id}  → TargetValueController.delete
-- AuthorizationInterceptor 要求 URL 注册到 PT_RESOURCE，否则未注册资源返回 403。
-- 路径变量用 AntPath '*'；复用 P_PERF_TGT_V_ADD（目标值写权限）受众。幂等 / 双库可重跑。
-- ============================================================================

-- yiti
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_TGT_V_DEL', '/api/perf/target-values/*', 'DELETE', '目标值删除',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-09 target-value physical delete'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_TGT_V_DEL');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'P_PERF_TGT_V_DEL', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'P_PERF_TGT_V_ADD'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'P_PERF_TGT_V_DEL');

-- onepl
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                         MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_TGT_V_DEL', '/api/perf/target-values/*', 'DELETE', '目标值删除',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-09 target-value physical delete'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_TGT_V_DEL');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.role_id, 'P_PERF_TGT_V_DEL', 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'P_PERF_TGT_V_ADD'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'P_PERF_TGT_V_DEL');
