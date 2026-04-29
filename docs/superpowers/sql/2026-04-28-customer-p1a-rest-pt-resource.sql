-- customer-marketing-center P1a 3 个真缺失 REST 端点 PT_RESOURCE 权限种子数据
-- 执行日期: 2026-04-28
-- 模块: customer-marketing-center
-- 业务域: P1a 补齐 03 文档要求但代码缺失的 3 个 REST 端点
--   - F.3 POST /api/claims/{claimId}/re-touch     重新发起触达
--   - H.1 GET  /api/admin/touch-tasks/summary     管理后台机构触达汇总
--   - C.4 GET  /api/leads/import/batches/{batchId} 导入批次详情
-- 注意: RESOURCE_ID 长度 <= 20，路径变量使用 AntPath * 通配
-- 前置条件:
--   - 已执行 2026-04-14-customer-pool-claim-pt-resource.sql (含 C_CLAIM_*)
--   - 已执行 2026-04-14-customer-touch-pt-resource.sql + 2026-04-21-customer-contract-alignment-pt-resource.sql

-- ============================================================
-- 1. 客户认领重新发起触达（F.3）
--    任务类型固定 FOLLOW_UP；reason ≥10 字符；@AuditLog RE_TOUCH_CLAIM
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_CLAIM_RETOUCH',   '/api/claims/*/re-touch',                'POST',   '认领重新发起触达',       0, 'CUSTOMER');

-- ============================================================
-- 2. 管理后台机构触达汇总（H.1，仅 LIST 权限，无审计）
--    支持 orgCode (必填) + startDate/endDate (可选)
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_ADM_TT_SUMMARY',  '/api/admin/touch-tasks/summary',        'GET',    '管理后台机构触达汇总',   0, 'CUSTOMER');

-- ============================================================
-- 3. 线索导入批次详情（C.4）
--    完整字段：状态 / 行数 / errorFileObjectId / processInstanceId
-- ============================================================
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE) VALUES
('C_LEAD_IMP_DETAIL', '/api/leads/import/batches/*',           'GET',    '线索导入批次详情',       0, 'CUSTOMER');
