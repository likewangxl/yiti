-- ============================================================
-- 人员标签列表化改造：新增 2 个端点的 PT_RESOURCE 登记
-- 日期: 2026-05-29
-- 说明: PERF_EVAL_20 已被 /api/admin/eval/tags/all GET 占用，
--       本次使用 PERF_EVAL_21 / PERF_EVAL_22
-- 依赖: 2026-05-27-eval-pt-resource-seed.sql（PERF_EVAL_1~19）
--       + PERF_EVAL_20（/api/admin/eval/tags/all GET，已存在）
-- ============================================================

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS)
VALUES
('PERF_EVAL_21', '/api/admin/eval/user-tags/page',      'GET', '人员标签列表',     0, 0),
('PERF_EVAL_22', '/api/admin/eval/user-tags/*/roles',   'PUT', '保存人员评价角色', 0, 0);

-- R_ADMIN 授权
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_21', 'PERF_EVAL_22');

-- R_BACK_TECH 授权
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_21', 'PERF_EVAL_22');
