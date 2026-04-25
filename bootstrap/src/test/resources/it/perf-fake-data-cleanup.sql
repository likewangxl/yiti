-- ============================================================
-- Option B.1 端到端 IT 测试 fake data 清理
-- 仅做 DELETE FROM 那 5 张表的 fake 行（不删表）
-- ============================================================

DELETE FROM emp_index_result WHERE emp_id = 'E10001';
DELETE FROM perf_target_value WHERE subject_id = 'E10001';
DELETE FROM perf_kpi_item WHERE scheme_id = 'KPI01';
DELETE FROM perf_kpi_scheme WHERE id = 'KPI01';
DELETE FROM perf_metric_def WHERE metric_code = 'DEPOSIT';
DELETE FROM sys_control WHERE id = 'SC_EMP';
