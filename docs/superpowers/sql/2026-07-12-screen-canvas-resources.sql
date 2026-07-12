-- 大屏画布设计器 V2 端点 PT_RESOURCE 注册 + 角色绑定（2026-07-12）
-- 6 条 API 资源（load/save/publish/rollback/discard/publish-logs；view 端点复用既有 R_RPT_SCR_VIEW，不重注）。
-- 发布/回滚为高危：R_RPT_SCR_CV_PUB / R_RPT_SCR_CV_RB 独立资源，仅授管理角色 + R_ADMIN。
-- 幂等：先删后插。目标库 yiti + onepl_test_bootstrap 手工执行。

-- 1) 清理旧行（幂等重跑）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_CV_GET','R_RPT_SCR_CV_SAVE','R_RPT_SCR_CV_PUB','R_RPT_SCR_CV_RB',
   'R_RPT_SCR_CV_DISC','R_RPT_SCR_CV_LOG');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_CV_GET','R_RPT_SCR_CV_SAVE','R_RPT_SCR_CV_PUB','R_RPT_SCR_CV_RB',
   'R_RPT_SCR_CV_DISC','R_RPT_SCR_CV_LOG');

-- 2) API 资源（ISMENU=0，STATUS=0 启用，SYS_CODE='RPT' 对齐 R_RPT_* 现场约定）
-- 注：GET /api/screen/admin/canvas/*（单层 *）与 GET /api/screen/admin/canvas/*/publish-logs（* 后跟字面量段）
--     在 Spring AntPathMatcher 下按路径段数区分，互不误匹配，无需额外收紧。
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_CV_GET', '/api/screen/admin/canvas/*',              'GET',  '大屏-画布加载',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_SAVE','/api/screen/admin/canvas/save',           'POST', '大屏-画布保存',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_PUB', '/api/screen/admin/canvas/publish',        'POST', '大屏-画布发布(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_RB',  '/api/screen/admin/canvas/rollback',       'POST', '大屏-画布回滚(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_DISC','/api/screen/admin/canvas/discard',        'POST', '大屏-放弃草稿',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_LOG', '/api/screen/admin/canvas/*/publish-logs', 'GET',  '大屏-发布归档',   0,0,0,0,'RPT',NOW(),NOW());

-- 3) 角色绑定
-- 3a) 管理类（load/save/discard/log）复制 R_RPT_SCR_CFG_SAVE 的角色集（与既有屏管理同档）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCV_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_CV_GET' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CV_DISC' UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SCR_CFG_SAVE';

-- 3b) 高危类（publish/rollback）复制 R_RPT_SQL_EXEC 的角色集（R_BACK_TECH，与试跑同档高危）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCVH_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_CV_PUB' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_RB'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SQL_EXEC';

-- 3c) 全量兜底：R_ADMIN 补齐 6 条尚未持有的（防 AUTH-40304）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCVB_', t.RESOURCE_ID, '_R_ADMIN'), 'R_ADMIN', t.RESOURCE_ID, 'PLATFORM', NOW()
FROM (
  SELECT 'R_RPT_SCR_CV_GET' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CV_PUB' UNION ALL SELECT 'R_RPT_SCR_CV_RB' UNION ALL
  SELECT 'R_RPT_SCR_CV_DISC' UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM PT_ROLE_RESOURCE x WHERE x.ROLE_ID = 'R_ADMIN' AND x.RESOURCE_ID = t.RESOURCE_ID
);
