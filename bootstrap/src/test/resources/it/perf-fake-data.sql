-- ============================================================
-- Option B.1 端到端 IT 测试 fake data
-- 验证 portal MetricAdapter → bridge → MetricApiImpl → emp_index_result 真实链路
-- 期望：empId=E10001 → DEPOSIT 当期 1200000 / 上期 1000000 → mom=20% → trend="UP"
-- ============================================================

-- 0. 先清理（确保多次跑测试不冲突）
DELETE FROM emp_index_result WHERE emp_id = 'E10001';
DELETE FROM perf_target_value WHERE subject_id = 'E10001';
DELETE FROM perf_kpi_item WHERE scheme_id = 'KPI01';
DELETE FROM perf_kpi_scheme WHERE id = 'KPI01';
DELETE FROM perf_metric_def WHERE metric_code = 'DEPOSIT';
DELETE FROM sys_control WHERE id = 'SC_EMP';

-- 1. 指标定义（baseDim=EMP，valSlot=1，对应 emp_index_result.val_1）
INSERT INTO perf_metric_def
    (id, metric_code, metric_name, base_dim, metric_level, calc_freq, calc_mode, val_slot, status, unit, decimal_places, deleted)
VALUES
    ('MD01', 'DEPOSIT', '存款余额', 'EMP', 1, 'MONTH', 'AUTO', 1, 'ACTIVE', '元', 2, 0);

-- 2. KPI 方案（cycleType=MONTHLY → cycleKey 走 yyyyMM；previousDate 走 minusMonths(1)）
INSERT INTO perf_kpi_scheme
    (id, scheme_code, scheme_name, cycle_type, open_detail, status)
VALUES
    ('KPI01', 'SCHEME_A', '测试方案', 'MONTHLY', 0, 'ACTIVE');

-- 3. KPI 方案项（DEPOSIT 入选）
INSERT INTO perf_kpi_item
    (id, scheme_id, metric_code, weight, multiplier, min_score, max_score)
VALUES
    ('ITEM01', 'KPI01', 'DEPOSIT', 1.0, 1.0, 0, 999999);

-- 4. sys_control（EMP 维度，is_valid=1, latestDataDate=2026-04-01, version=V1）
INSERT INTO sys_control
    (id, scope_dim, latest_data_date, current_version, is_valid)
VALUES
    ('SC_EMP', 'EMP', '2026-04-01', 'V1', 1);

-- 5. emp_index_result 当期 + 上月（mom=(1200000-1000000)/1000000*100=20.00 → trend=UP）
--    val_1 对应 metric_def.val_slot=1 (DEPOSIT)
INSERT INTO emp_index_result (emp_id, data_date, version, val_1)
VALUES ('E10001', '2026-04-01', 'V1', 1200000.00);

INSERT INTO emp_index_result (emp_id, data_date, version, val_1)
VALUES ('E10001', '2026-03-01', 'V1', 1000000.00);
