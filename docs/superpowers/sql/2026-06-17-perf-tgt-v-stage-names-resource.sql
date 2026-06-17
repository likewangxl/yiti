-- ============================================================================
-- 2026-06-17 目标值「阶段名称」下拉端点 —— API 鉴权资源注册
--   GET /api/perf/target-values/stage-names → TargetValueController.stageNames → P_PERF_TGT_V_STAGE
-- 属性/受众镜像 P_PERF_TGT_V_SUBJ（SYS_CODE='PERF'，ISMENU=0，STATUS=0），授权复制其受众。
-- 幂等 + 双库 yiti/onepl。
-- ============================================================================

-- ---------- yiti ----------
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                              MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_TGT_V_STAGE', '/api/perf/target-values/stage-names', 'GET', '目标值阶段名称下拉',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-17 目标值阶段名称下拉'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID='P_PERF_TGT_V_STAGE');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(),'-',''), rr.ROLE_ID, 'P_PERF_TGT_V_STAGE', rr.SYS_CODE
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID='P_PERF_TGT_V_SUBJ'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID=rr.ROLE_ID AND rr2.RESOURCE_ID='P_PERF_TGT_V_STAGE');

-- ---------- onepl ----------
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                               MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_TGT_V_STAGE', '/api/perf/target-values/stage-names', 'GET', '目标值阶段名称下拉',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-17 目标值阶段名称下拉'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID='P_PERF_TGT_V_STAGE');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(),'-',''), rr.ROLE_ID, 'P_PERF_TGT_V_STAGE', rr.SYS_CODE
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID='P_PERF_TGT_V_SUBJ'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID=rr.ROLE_ID AND rr2.RESOURCE_ID='P_PERF_TGT_V_STAGE');
-- ============================================================================
