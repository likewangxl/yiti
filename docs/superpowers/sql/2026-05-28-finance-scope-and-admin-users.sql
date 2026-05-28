-- ============================================================================
-- 2026-05-28 资财权限 + 目标方案 owner 回填 (幂等 / 双库可重跑)
-- ============================================================================
--
-- 背景：
--   1. 目标值管理页面调 /api/admin/users GET 拿员工列表，资财角色无此资源 → 403
--   2. 目标管理 tab 资财用户看不到自己刚新建的方案
--      根因 A：V1.4 S2 引入 PERF_TARGET_PLAN.owner_emp_id 字段，但 yiti 库的回填
--              UPDATE 从未执行 → 7 行 owner_emp_id 全 NULL
--      根因 B：R_BACK_FINANCE / R_FIN_LEAD 的 PERF_CONFIG BizScope=SELF，配合
--              NULL owner_emp_id 永远 0 行命中
--
-- 修复策略：
--   A. PT_ROLE_RESOURCE：把 A_USER_LIST (GET /api/admin/users) 绑给所有已绑
--      P_PERF_TGT_V_ADD 的业务角色（资财/零售/公司/行长/RM/支持等 15 个）
--   B. PT_ROLE_BIZ_SCOPE：R_BACK_FINANCE + R_FIN_LEAD 的 PERF_CONFIG 升 ALL，
--      让资财人员看全行目标方案（前端编辑按钮限 createdBy==自己，看到 ≠ 能改）
--   C. PERF_TARGET_PLAN：兜底回填 owner_emp_id = created_by，让 SELF 兼容
--      scope 的角色也能看到自己创建的方案（即使后续 BizScope 改回 SELF 也不
--      影响 ALL 路径）
--
-- 执行：
--   mysql -u root -p<pwd> yiti  < 2026-05-28-finance-scope-and-admin-users.sql
--   mysql -u root -p<pwd> onepl < 2026-05-28-finance-scope-and-admin-users.sql
-- ============================================================================

-- ----------------------------------------------------------------------------
-- A. 给业务角色补绑 A_USER_LIST (GET /api/admin/users)
--    幂等：NOT EXISTS 防重复
-- ----------------------------------------------------------------------------
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', '') AS ID,
       rr.role_id,
       'A_USER_LIST' AS RESOURCE_ID,
       'PLATFORM' AS SYS_CODE
FROM PT_ROLE_RESOURCE rr
WHERE rr.resource_id = 'P_PERF_TGT_V_ADD'
  AND NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE rr2
    WHERE rr2.role_id = rr.role_id AND rr2.resource_id = 'A_USER_LIST'
  );

-- ----------------------------------------------------------------------------
-- B. 资财角色 PERF_CONFIG BizScope SELF → ALL
--    幂等：WHERE DATA_SCOPE='SELF' 已是 ALL 时跳过
-- ----------------------------------------------------------------------------
UPDATE PT_ROLE_BIZ_SCOPE
SET DATA_SCOPE = 'ALL', UPDATE_TIME = NOW()
WHERE ROLE_ID IN ('R_BACK_FINANCE', 'R_FIN_LEAD')
  AND BIZ_TYPE = 'PERF_CONFIG'
  AND DATA_SCOPE = 'SELF';

-- ----------------------------------------------------------------------------
-- C. PERF_TARGET_PLAN.owner_emp_id 兜底回填（V1.4 S2 DDL 配套回填补做）
--    幂等：仅处理 NULL 行
-- ----------------------------------------------------------------------------
UPDATE PERF_TARGET_PLAN
SET owner_emp_id = created_by, updated_time = NOW()
WHERE owner_emp_id IS NULL AND created_by IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 验证（执行后手工核对）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE resource_id='A_USER_LIST';
-- SELECT role_id, data_scope FROM PT_ROLE_BIZ_SCOPE
--  WHERE role_id IN ('R_BACK_FINANCE','R_FIN_LEAD') AND biz_type='PERF_CONFIG';
-- SELECT COUNT(*) FROM PERF_TARGET_PLAN WHERE owner_emp_id IS NULL;
-- ----------------------------------------------------------------------------
