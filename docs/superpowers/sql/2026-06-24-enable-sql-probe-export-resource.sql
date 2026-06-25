-- 2026-06-24 启用 SQL 探查「导出/下载」接口资源。
-- R_RPT_SQL_EXP(POST /api/reports/sql-probe/export) 原 STATUS=1(禁用)，
-- ResourceMatcher 只匹配 STATUS=0 的资源 → 视为"资源未登记"返回 AUTH-40302(连 admin 也被挡)。
-- 下载与执行(R_RPT_SQL_EXEC, STATUS=0)应同权限：有菜单即可下载，故启用本资源。
UPDATE PT_RESOURCE SET STATUS = 0 WHERE RESOURCE_ID = 'R_RPT_SQL_EXP';
