-- ============================================================
-- 给业绩调整三角色补 alloc-adjust 4 个 PT_RESOURCE 绑定（2026-05-20）
-- ============================================================
-- 背景：P_PERF_ALLOC_AD_CRE / _LST / _GET / _WD 当前只绑 R_ADMIN。
--   rm_zhang (R_RM) 点"提交申请"被 AuthorizationInterceptor.RbacAuthorizer
--   403 拦截（AUTH-40301），请求未到 Controller，ApiLog 看不到入口。
--
-- 本脚本：把上述 4 个资源 + 撤回，统一绑给：
--   R_RM         (rm_zhang)    — 申请人
--   R_BRANCH_MGR (branch_wang) — 一级审批
--   R_PRESIDENT  (user002)     — 二级审批
--
-- PT_ROLE_RESOURCE.ID 为 varchar(32) 非自增，需显式 MD5。
-- UNIQUE 由 (ROLE_ID, RESOURCE_ID) 业务唯一约束防重复绑定。
-- ============================================================

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
  (MD5(CONCAT('R_RM',         '|', 'P_PERF_ALLOC_AD_CRE')), 'R_RM',         'P_PERF_ALLOC_AD_CRE'),
  (MD5(CONCAT('R_RM',         '|', 'P_PERF_ALLOC_AD_LST')), 'R_RM',         'P_PERF_ALLOC_AD_LST'),
  (MD5(CONCAT('R_RM',         '|', 'P_PERF_ALLOC_AD_GET')), 'R_RM',         'P_PERF_ALLOC_AD_GET'),
  (MD5(CONCAT('R_RM',         '|', 'P_PERF_ALLOC_AD_WD')),  'R_RM',         'P_PERF_ALLOC_AD_WD'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'P_PERF_ALLOC_AD_CRE')), 'R_BRANCH_MGR', 'P_PERF_ALLOC_AD_CRE'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'P_PERF_ALLOC_AD_LST')), 'R_BRANCH_MGR', 'P_PERF_ALLOC_AD_LST'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'P_PERF_ALLOC_AD_GET')), 'R_BRANCH_MGR', 'P_PERF_ALLOC_AD_GET'),
  (MD5(CONCAT('R_BRANCH_MGR', '|', 'P_PERF_ALLOC_AD_WD')),  'R_BRANCH_MGR', 'P_PERF_ALLOC_AD_WD'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'P_PERF_ALLOC_AD_CRE')), 'R_PRESIDENT',  'P_PERF_ALLOC_AD_CRE'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'P_PERF_ALLOC_AD_LST')), 'R_PRESIDENT',  'P_PERF_ALLOC_AD_LST'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'P_PERF_ALLOC_AD_GET')), 'R_PRESIDENT',  'P_PERF_ALLOC_AD_GET'),
  (MD5(CONCAT('R_PRESIDENT',  '|', 'P_PERF_ALLOC_AD_WD')),  'R_PRESIDENT',  'P_PERF_ALLOC_AD_WD')
ON DUPLICATE KEY UPDATE ROLE_ID=VALUES(ROLE_ID);
