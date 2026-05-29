-- =============================================================================
-- 2026-05-29  perf stat-show 只读资源绑定到全部角色
-- -----------------------------------------------------------------------------
-- 背景：
--   公司部人员(CORP_DEPT)审批业绩调整时，前端调
--   GET /api/perf/stat-show/cust 反显客户财务统计，报 403（AUTH-40301）。
--   根因：资源 P_PERF_STAT_CUST / P_PERF_STAT_EMP 初始 seed 只绑了 15 个角色，
--   漏了 CORP_DEPT（仅绑了 CORP_DEPT_LEADER）等审批角色。
--
-- 处置（按"权限放到最大最完整"）：
--   把两条 stat-show 只读资源绑定到 PT_ROLE 全部角色。
--   这两条均为只读统计展示端点（@BizAuth LIST，无 @AuditLog），全员可见无敏感写风险。
--
-- 幂等：NOT EXISTS 守护，已绑定的角色跳过；可重复执行。
-- ID：PT_ROLE_RESOURCE.ID 为 varchar(32)，用去横线 UUID 生成。
-- 适用库：当前开发/运行库（dev=yiti）。其他库需先确保 PT_RESOURCE 已登记
--          P_PERF_STAT_CUST / P_PERF_STAT_EMP 两条资源（v1.13 stat-show seed）。
--
-- 鉴权实时读库（PermissionCacheService 去 Redis 后直接查 DB），执行后立即生效，
-- 无需重启后端或重新登录。
-- =============================================================================

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'P_PERF_STAT_CUST' AS RESOURCE_ID
    UNION ALL
    SELECT 'P_PERF_STAT_EMP'  AS RESOURCE_ID
) res
WHERE NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE rr
    WHERE rr.ROLE_ID = r.ROLE_ID
      AND rr.RESOURCE_ID = res.RESOURCE_ID
);

-- 验证：两条资源应各绑定 = PT_ROLE 总角色数
-- SELECT RESOURCE_ID, COUNT(*) AS bound_roles
--   FROM PT_ROLE_RESOURCE
--  WHERE RESOURCE_ID IN ('P_PERF_STAT_CUST', 'P_PERF_STAT_EMP')
--  GROUP BY RESOURCE_ID;
