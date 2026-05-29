-- ============================================================
-- 人员标签 导入/导出/模板 PT_RESOURCE 资源注册
-- 日期: 2026-05-29
-- 承接 2026-05-29-eval-user-tags-page-resource-seed.sql（已用到 PERF_EVAL_22）
-- 字段对齐: RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS
-- ============================================================

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS)
VALUES
('PERF_EVAL_23', '/api/admin/eval/user-tags/import',          'POST', '导入人员评价角色', 0, 0),
('PERF_EVAL_24', '/api/admin/eval/user-tags/import-template', 'GET',  '下载导入模板',     0, 0),
('PERF_EVAL_25', '/api/admin/eval/user-tags/export',          'GET',  '导出人员标签列表', 0, 0);

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_23', 'PERF_EVAL_24', 'PERF_EVAL_25');

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_23', 'PERF_EVAL_24', 'PERF_EVAL_25');
