-- ============================================================
-- Option B.1 端到端 IT 测试 fake data 清理
-- 仅做 DELETE FROM 那 5 张表的 fake 行（不删表）
-- 4 员工：E10001/E10002/E10003/E10004（覆盖 trend UP/FLAT/DOWN/null 4 边界）
-- ============================================================

DELETE FROM EMP_INDEX_RESULT WHERE emp_id IN ('E10001', 'E10002', 'E10003', 'E10004');
DELETE FROM PERF_TARGET_VALUE WHERE subject_id IN ('E10001', 'E10002', 'E10003', 'E10004');
DELETE FROM PERF_KPI_ITEM WHERE scheme_id = 'KPI01';
DELETE FROM PERF_KPI_SCHEME WHERE id = 'KPI01';
DELETE FROM PERF_METRIC_DEF WHERE metric_code = 'DEPOSIT';
DELETE FROM SYS_CONTROL WHERE id = 'SC_EMP';
DELETE FROM PORTAL_SHORTCUT WHERE id IN ('SC_SYS_01', 'SC_CUST_E10001');
