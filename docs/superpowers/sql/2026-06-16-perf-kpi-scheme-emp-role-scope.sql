-- ============================================================================
-- 2026-06-16 PERF_KPI_SCHEME 增加「员工角色范围」列（KPI 方案按角色限定计算员工范围）
--   emp_role_scope varchar(500)：角色编码 CSV（如 R_BACK_FINANCE,R_FIN_LEAD），空=不限定。
-- 幂等（INFORMATION_SCHEMA 预检 + 动态 SQL）；双库 yiti + onepl。
-- ============================================================================

-- ===================== yiti =====================
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='PERF_KPI_SCHEME' AND COLUMN_NAME='emp_role_scope');
SET @ddl := IF(@exists=0,
  'ALTER TABLE yiti.PERF_KPI_SCHEME ADD COLUMN emp_role_scope varchar(500) NULL COMMENT ''员工角色范围(角色编码CSV)，空=不限定''',
  'SELECT ''[yiti] emp_role_scope 已存在，跳过'' AS msg');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ===================== onepl =====================
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME='PERF_KPI_SCHEME' AND COLUMN_NAME='emp_role_scope');
SET @ddl := IF(@exists=0,
  'ALTER TABLE onepl.PERF_KPI_SCHEME ADD COLUMN emp_role_scope varchar(500) NULL COMMENT ''员工角色范围(角色编码CSV)，空=不限定''',
  'SELECT ''[onepl] emp_role_scope 已存在，跳过'' AS msg');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
-- ============================================================================
