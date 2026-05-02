-- =====================================================================
-- performance-engine-center V1.2 Phase Q8.1 业务示例种子数据
-- Version: V1_2_3
-- Date: 2026-04-24
-- Task: Q8.1
--
-- 目的：为 dev / test 环境预置一组可用的指标、KPI 方案、目标方案示例数据，
--   方便联调 / 演示 / 试算。所有记录使用幂等 UPSERT，多次执行结果一致。
--
-- ⚠ 生产环境慎用：本脚本 INSERT 的数据均为示例（code 以 M_ / KS_ / TGT_ 标识
--   但非实际业务代码），生产投产时应通过配置管理流程定义真实指标。如被生产
--   Flyway 执行，可执行 undo-scripts/V1_2_3__undo.sql 反向删除（见末尾）。
--
-- 覆盖范围：
--   1. 4 个示例指标（1 个 SQL 一级指标 + 1 个 EXPR 二级指标 + 1 个 ORG 一级指标
--      + 1 个 CUST 一级指标，覆盖 EMP/ORG/CUST 三种 base_dim 和 level=1/2 + 两种
--      calc_logic_type=SQL/EXPR）
--   2. 1 个示例 KPI 方案 + 2 个方案项（权重 60+40=100）
--   3. 1 个示例目标方案（关联上面的 KPI 方案）
-- =====================================================================

-- -------------------------------------------------------------
-- 1. 示例指标（perf_metric_def）
-- -------------------------------------------------------------
INSERT INTO PERF_METRIC_DEF
  (id, metric_code, metric_name, metric_name_en, metric_desc, base_dim, metric_level,
   calc_freq, calc_mode, calc_logic_type, sql_text, expr_text, summary_rule,
   ref_metric_codes, val_slot, status, created_by, created_time, updated_by, updated_time,
   unit, decimal_places, deleted, description)
VALUES
  ('SEED_M_EMP_DEP_AVG_BAL', 'M_EMP_DEP_AVG_BAL', '员工存款日均余额', 'emp_dep_avg_bal',
   '示例：员工名下客户存款的日均余额', 'EMP', 1,
   'DAY', 'AUTO', 'SQL',
   'SELECT 0 AS metric_value FROM dual',  -- 占位 SQL，实际由 MetricCalcService 动态执行
   NULL, 'SUM', '[]', 101, 'ACTIVE', 'seed', NOW(), 'seed', NOW(),
   '万元', 2, 0, 'V1.2 Q8.1 种子数据：员工存款日均余额示例指标'),

  ('SEED_M_EMP_FEE_INCOME', 'M_EMP_FEE_INCOME', '员工中间业务收入', 'emp_fee_income',
   '示例：员工名下客户产生的中间业务收入', 'EMP', 1,
   'DAY', 'AUTO', 'SQL',
   'SELECT 0 AS metric_value FROM dual',
   NULL, 'SUM', '[]', 102, 'ACTIVE', 'seed', NOW(), 'seed', NOW(),
   '元', 2, 0, 'V1.2 Q8.1 种子数据：员工中间业务收入示例指标'),

  ('SEED_M_EMP_COMPREHENSIVE', 'M_EMP_COMPREHENSIVE', '员工综合贡献', 'emp_comprehensive',
   '示例：员工综合贡献（存款*0.6 + 中收*0.4 的复合二级指标）', 'EMP', 2,
   'DAY', 'AUTO', 'EXPR',
   NULL, '#A * 0.6 + #B * 0.4', 'SUM',
   '["M_EMP_DEP_AVG_BAL","M_EMP_FEE_INCOME"]', 103, 'ACTIVE', 'seed', NOW(), 'seed', NOW(),
   '万元', 2, 0, 'V1.2 Q8.1 种子数据：EMP 二级指标（依赖上面两条一级指标）'),

  ('SEED_M_ORG_DEP_TOTAL', 'M_ORG_DEP_TOTAL', '机构存款总额', 'org_dep_total',
   '示例：机构层存款余额汇总', 'ORG', 1,
   'DAY', 'AUTO', 'SQL',
   'SELECT 0 AS metric_value FROM dual',
   NULL, 'SUM', '[]', 201, 'ACTIVE', 'seed', NOW(), 'seed', NOW(),
   '万元', 2, 0, 'V1.2 Q8.1 种子数据：ORG 一级指标示例'),

  ('SEED_M_CUST_AUM', 'M_CUST_AUM', '客户管理资产', 'cust_aum',
   '示例：客户管理资产（AUM）', 'CUST', 1,
   'DAY', 'AUTO', 'SQL',
   'SELECT 0 AS metric_value FROM dual',
   NULL, 'SUM', '[]', 301, 'ACTIVE', 'seed', NOW(), 'seed', NOW(),
   '万元', 2, 0, 'V1.2 Q8.1 种子数据：CUST 一级指标示例')
ON DUPLICATE KEY UPDATE
  metric_name     = VALUES(metric_name),
  metric_desc     = VALUES(metric_desc),
  status          = VALUES(status),
  updated_by      = 'seed',
  updated_time    = NOW(),
  deleted         = 0,
  description     = VALUES(description);

-- -------------------------------------------------------------
-- 2. 指标引用关系（二级 EMP 指标依赖两条一级指标）
-- -------------------------------------------------------------
INSERT INTO PERF_METRIC_REF (id, metric_code, ref_metric_code, created_time)
VALUES
  ('SEED_REF_EMP_DEP',  'M_EMP_COMPREHENSIVE', 'M_EMP_DEP_AVG_BAL',  NOW()),
  ('SEED_REF_EMP_FEE',  'M_EMP_COMPREHENSIVE', 'M_EMP_FEE_INCOME',   NOW())
ON DUPLICATE KEY UPDATE
  created_time = VALUES(created_time);

-- -------------------------------------------------------------
-- 3. 示例 KPI 方案（perf_kpi_scheme）
-- -------------------------------------------------------------
INSERT INTO PERF_KPI_SCHEME
  (id, scheme_code, scheme_name, cycle_type, open_detail, status,
   created_by, created_time, updated_by, updated_time)
VALUES
  ('SEED_KS_EMP_2026', 'KS_EMP_MONTHLY_2026', '2026 员工月度考核方案', 'MONTHLY', 1, 'ACTIVE',
   'seed', NOW(), 'seed', NOW()),
  ('SEED_KS_EMP_Q',    'KS_EMP_QUARTERLY',    '员工季度考核方案（示例）',    'QUARTERLY', 0, 'ACTIVE',
   'seed', NOW(), 'seed', NOW())
ON DUPLICATE KEY UPDATE
  scheme_name  = VALUES(scheme_name),
  status       = VALUES(status),
  updated_by   = 'seed',
  updated_time = NOW();

-- -------------------------------------------------------------
-- 4. KPI 方案项（perf_kpi_item）—— 权重之和 100
-- -------------------------------------------------------------
INSERT INTO PERF_KPI_ITEM
  (id, scheme_id, metric_code, weight, multiplier, min_score, max_score, created_time)
VALUES
  ('SEED_KI_DEP',  'SEED_KS_EMP_2026', 'M_EMP_DEP_AVG_BAL', 60.0000, 1.0000, 0, 100, NOW()),
  ('SEED_KI_FEE',  'SEED_KS_EMP_2026', 'M_EMP_FEE_INCOME',  40.0000, 1.0000, 0, 100, NOW())
ON DUPLICATE KEY UPDATE
  weight       = VALUES(weight),
  multiplier   = VALUES(multiplier);

-- -------------------------------------------------------------
-- 5. 示例目标方案（perf_target_plan）
-- -------------------------------------------------------------
INSERT INTO PERF_TARGET_PLAN
  (id, plan_code, plan_name, kpi_scheme_id, target_dim, target_cycle, effective_date,
   status, created_by, created_time, updated_by, updated_time)
VALUES
  ('SEED_TGT_EMP_2026', 'TGT_EMP_2026', '2026 员工年度目标方案（示例）',
   'SEED_KS_EMP_2026', 'EMP', 'YEAR', '2026-01-01', 'ACTIVE',
   'seed', NOW(), 'seed', NOW())
ON DUPLICATE KEY UPDATE
  plan_name    = VALUES(plan_name),
  status       = VALUES(status),
  updated_by   = 'seed',
  updated_time = NOW();
