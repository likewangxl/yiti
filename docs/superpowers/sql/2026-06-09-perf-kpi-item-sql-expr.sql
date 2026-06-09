-- ============================================================================
-- PERF_KPI_ITEM 新增 sql_expr 列：KPI 方案指标项的「SQL 表达式」（支持 #{slot} 占位符），
-- 前端 KpiRules.vue 指标配置中单独一行编辑/展示，用于自定义取数/计算逻辑。
-- 新增可空字段，无需回填。yiti（dev） + onepl（prod）双库执行。
-- ============================================================================

-- yiti
ALTER TABLE yiti.PERF_KPI_ITEM
    ADD COLUMN sql_expr varchar(2000) NULL COMMENT 'SQL 表达式，支持 #{slot} 占位符' AFTER formula;

-- onepl
ALTER TABLE onepl.PERF_KPI_ITEM
    ADD COLUMN sql_expr varchar(2000) NULL COMMENT 'SQL 表达式，支持 #{slot} 占位符' AFTER formula;
