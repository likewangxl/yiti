-- ============================================================================
-- 为新 BizType「考核计算 KPI_CALC」播种数据范围：克隆现有 PERF_CONFIG 的角色-数据范围行，
-- 使「考核计算」数据范围默认与绩效配置一致（多数角色 ALL），上线不锁权限；
-- 之后可在「权限管理(Permission.vue) → 数据范围矩阵」按角色调整为 本人/本机构+下级/全部。
-- yiti + onepl 双库；幂等（NOT EXISTS 跳过已存在 KPI_CALC 行）。执行后刷新缓存（重启或 evict auth:biz-scope:*）。
-- ============================================================================

INSERT INTO yiti.PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER, REMARK)
SELECT REPLACE(UUID(), '-', ''), s.ROLE_ID, 'KPI_CALC', s.DATA_SCOPE, s.RECORD_STATUS, 'seed', 'seed', 'clone from PERF_CONFIG'
FROM (SELECT ROLE_ID, DATA_SCOPE, RECORD_STATUS FROM yiti.PT_ROLE_BIZ_SCOPE WHERE BIZ_TYPE = 'PERF_CONFIG') s
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_BIZ_SCOPE x WHERE x.ROLE_ID = s.ROLE_ID AND x.BIZ_TYPE = 'KPI_CALC');

INSERT INTO onepl.PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER, REMARK)
SELECT REPLACE(UUID(), '-', ''), s.ROLE_ID, 'KPI_CALC', s.DATA_SCOPE, s.RECORD_STATUS, 'seed', 'seed', 'clone from PERF_CONFIG'
FROM (SELECT ROLE_ID, DATA_SCOPE, RECORD_STATUS FROM onepl.PT_ROLE_BIZ_SCOPE WHERE BIZ_TYPE = 'PERF_CONFIG') s
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_BIZ_SCOPE x WHERE x.ROLE_ID = s.ROLE_ID AND x.BIZ_TYPE = 'KPI_CALC');
