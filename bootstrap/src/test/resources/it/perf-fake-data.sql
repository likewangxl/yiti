-- ============================================================
-- Option B.1 端到端 IT 测试 fake data（V1.6 reviewer §D 边界 case 扩展）
-- 验证 portal MetricAdapter → bridge → MetricApiImpl → emp_index_result 真实链路
--
-- 4 个员工对应 4 种 trend 状态（覆盖 PerformanceMetricApiBridge.deriveTrend 全分支）：
--   E10001: current=1200000 / previous=1000000 → mom=+20.00 → trend="UP"
--   E10002: current=1000000 / previous=1000000 → mom=0.00   → trend="FLAT"
--   E10003: current=800000  / previous=1000000 → mom=-20.00 → trend="DOWN"
--   E10004: current=1000000 / previous=0       → mom=null   → trend=null（previous=0 守护）
-- ============================================================

-- 0. 先清理（确保多次跑测试不冲突）
DELETE FROM emp_index_result WHERE emp_id IN ('E10001', 'E10002', 'E10003', 'E10004');
DELETE FROM perf_target_value WHERE subject_id IN ('E10001', 'E10002', 'E10003', 'E10004');
DELETE FROM perf_kpi_item WHERE scheme_id = 'KPI01';
DELETE FROM perf_kpi_scheme WHERE id = 'KPI01';
DELETE FROM perf_metric_def WHERE metric_code = 'DEPOSIT';
DELETE FROM sys_control WHERE id = 'SC_EMP';
DELETE FROM portal_shortcut WHERE id IN ('SC_SYS_01', 'SC_CUST_E10001');

-- 0bis. FU-15 B 切真 MySQL（onepl_test_bootstrap）后必须清掉生产种子方案，
-- 否则 listActiveSchemes 会同时返回 SEED_KS_EMP_2026 (M_EMP_DEP_AVG_BAL/M_EMP_FEE_INCOME) 污染断言
DELETE FROM perf_kpi_item WHERE scheme_id LIKE 'SEED_%';
DELETE FROM perf_kpi_scheme WHERE id LIKE 'SEED_%';

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

-- 5. emp_index_result 4 员工 × 2 月份 = 8 行
--    val_1 对应 metric_def.val_slot=1 (DEPOSIT)
INSERT INTO emp_index_result (emp_id, data_date, version, val_1) VALUES
    -- E10001 → mom=+20% → UP
    ('E10001', '2026-04-01', 'V1', 1200000.00),
    ('E10001', '2026-03-01', 'V1', 1000000.00),
    -- E10002 → mom=0 → FLAT
    ('E10002', '2026-04-01', 'V1', 1000000.00),
    ('E10002', '2026-03-01', 'V1', 1000000.00),
    -- E10003 → mom=-20% → DOWN
    ('E10003', '2026-04-01', 'V1', 800000.00),
    ('E10003', '2026-03-01', 'V1', 1000000.00),
    -- E10004 → previous=0 → mom=null → trend=null
    ('E10004', '2026-04-01', 'V1', 1000000.00),
    ('E10004', '2026-03-01', 'V1', 0.00);

-- 6. portal_shortcut（V1.6 reviewer §G-1 整改：补 shortcut 真实链路覆盖）
--    SYSTEM 1 行（全员可见）+ CUSTOM 1 行（仅 E10001 可见）
--    case workspace_shouldAggregateShortcutsAndGracefullyDegradeOtherPaths 验证：
--      - empId=E10001 看到 2 行（SYSTEM + 自己 CUSTOM）
INSERT INTO portal_shortcut
    (id, shortcut_name, shortcut_url, shortcut_icon, shortcut_type, target_type, emp_id, sort_order, status, created_by, created_time, updated_by, updated_time)
VALUES
    ('SC_SYS_01',      '系统快捷-客户中心', '/customers',    'icon-customer', 'SYSTEM', 'INTERNAL', NULL,    10, 'ACTIVE', 'seed', CURRENT_TIMESTAMP, 'seed', CURRENT_TIMESTAMP),
    ('SC_CUST_E10001', '我的快捷-工作台',   '/portal/myhome', 'icon-home',     'CUSTOM', 'INTERNAL', 'E10001', 20, 'ACTIVE', 'E10001', CURRENT_TIMESTAMP, 'E10001', CURRENT_TIMESTAMP);
