-- ============================================================================
-- 2026-06-15 目标值「对象」下拉端点 —— API 鉴权资源注册
--   GET /api/perf/target-values/subjects → TargetValueController.subjects → P_PERF_TGT_V_SUBJ
-- 目标值管理页查询区新增「对象」下拉（方案内目标值去重）；AuthorizationInterceptor 要求 URL
-- 注册到 PT_RESOURCE，否则未注册资源返回 403。
-- 资源属性与受众完全对齐既有列表资源 P_PERF_TGT_V_LIST（SYS_CODE='PERF'，ISMENU=0，STATUS=0），
-- 角色授权复制 P_PERF_TGT_V_LIST 的受众（连同 PT_ROLE_RESOURCE.SYS_CODE 一并复制，兼容 yiti/onepl
-- 两库不同的角色主键与 sys_code 约定）。双库幂等可重跑。
-- ============================================================================

-- ===================== yiti =====================
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                              MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_TGT_V_SUBJ', '/api/perf/target-values/subjects', 'GET', '目标值对象下拉',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-15 目标值对象下拉（方案内去重）'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_TGT_V_SUBJ');

INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, 'P_PERF_TGT_V_SUBJ', rr.SYS_CODE
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'P_PERF_TGT_V_LIST'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID = rr.ROLE_ID AND rr2.RESOURCE_ID = 'P_PERF_TGT_V_SUBJ');

-- ===================== onepl =====================
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                               MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'P_PERF_TGT_V_SUBJ', '/api/perf/target-values/subjects', 'GET', '目标值对象下拉',
       0, 0, 0, 0, 'PERF', 'seed', '2026-06-15 目标值对象下拉（方案内去重）'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_TGT_V_SUBJ');

INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, 'P_PERF_TGT_V_SUBJ', rr.SYS_CODE
FROM onepl.PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'P_PERF_TGT_V_LIST'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID = rr.ROLE_ID AND rr2.RESOURCE_ID = 'P_PERF_TGT_V_SUBJ');

-- ===================== 验证 =====================
-- SELECT RESOURCE_ID, RESOURCE_URL FROM yiti.PT_RESOURCE  WHERE RESOURCE_ID='P_PERF_TGT_V_SUBJ';
-- SELECT COUNT(*) FROM yiti.PT_ROLE_RESOURCE  WHERE RESOURCE_ID='P_PERF_TGT_V_SUBJ';  -- 应=P_PERF_TGT_V_LIST 的授权数
-- SELECT COUNT(*) FROM onepl.PT_ROLE_RESOURCE WHERE RESOURCE_ID='P_PERF_TGT_V_SUBJ';
