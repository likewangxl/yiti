-- 奖励分配（REWARD）端点 PT_RESOURCE 资源注册 + 角色绑定（2026-07-11）
-- 9 个端点：管理端 6（/api/admin/eval/reward/*）+ 用户端 3（/api/eval/reward-tasks*）
-- RESOURCE_ID 续编 PERF_EVAL_39..47；角色绑定复用 PERF_EVAL_38（submit-batch）的全量角色集。
-- 幂等：先删后插。目标库 yiti + onepl_test_bootstrap 手工执行。

-- 1) 清理旧行（幂等重跑）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('PERF_EVAL_39','PERF_EVAL_40','PERF_EVAL_41','PERF_EVAL_42','PERF_EVAL_43',
   'PERF_EVAL_44','PERF_EVAL_45','PERF_EVAL_46','PERF_EVAL_47');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('PERF_EVAL_39','PERF_EVAL_40','PERF_EVAL_41','PERF_EVAL_42','PERF_EVAL_43',
   'PERF_EVAL_44','PERF_EVAL_45','PERF_EVAL_46','PERF_EVAL_47');

-- 2) 注册资源（ISMENU=0 非菜单 API 资源，STATUS=0 启用，SYS_CODE=PLATFORM）
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('PERF_EVAL_39', '/api/admin/eval/reward/import-template',      'GET',  '下载奖励分配导入模板',   0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_40', '/api/admin/eval/reward/import',               'POST', '导入奖励分配',           0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_41', '/api/admin/eval/reward/batches',              'GET',  '管理端-奖励分配批次列表', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_42', '/api/admin/eval/reward/batches/*',            'GET',  '管理端-奖励分配批次详情', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_43', '/api/admin/eval/reward/batches/*/publish',    'POST', '管理端-发布奖励分配批次', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_44', '/api/admin/eval/reward/batches/*/export',     'GET',  '管理端-导出奖励分配明细', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_45', '/api/eval/reward-tasks',                      'GET',  '我的奖励分配待处理汇总', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_46', '/api/eval/reward-tasks/items',                'GET',  '奖励分配明细',           0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_47', '/api/eval/reward-tasks/submit-batch',         'POST', '提交奖励分配',           0, 0, 0, 0, 'PLATFORM', NOW(), NOW());

-- 3) 角色绑定：为每个新资源复用 PERF_EVAL_38 的全量角色集（R_ADMIN/R_BACK_TECH + 全部业务角色）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('RWD_', SUBSTRING(t.RESOURCE_ID, 11), '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'PERF_EVAL_39' AS RESOURCE_ID UNION ALL SELECT 'PERF_EVAL_40' UNION ALL
  SELECT 'PERF_EVAL_41' UNION ALL SELECT 'PERF_EVAL_42' UNION ALL SELECT 'PERF_EVAL_43' UNION ALL
  SELECT 'PERF_EVAL_44' UNION ALL SELECT 'PERF_EVAL_45' UNION ALL SELECT 'PERF_EVAL_46' UNION ALL
  SELECT 'PERF_EVAL_47'
) t
WHERE r.RESOURCE_ID = 'PERF_EVAL_38';
