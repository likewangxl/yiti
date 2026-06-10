-- ============================================================================
-- 新建调整申请-余额概览：新增 4 个 CUST 维度「贷款余额」指标 MC_005-008，固定槽位 5,6,7,8，
-- 与存款 MC_001-004(槽位 1-4) 一一对应（当前/较上日/年均/较上年均）。
--
-- yiti（dev）槽位 5-8 原被 M_0091/0093/0095/0097（一般性/基础性存款均值，CUST）占用且有数据，
-- 而 (val_slot, base_dim) 为唯一键，故先将这 4 个指标迁到空闲槽位 37-40（连同 CUST_INDEX_RESULT
-- 的 val_5-8 数据迁到 val_37-40 并清空 val_5-8，零数据丢失），再建 MC_005-008 于 5-8。
-- onepl（prod）无 CUST 指标、5-8 空闲，直接建。
-- 备份：docs/superpowers/sql/backup/2026-06-09-cust-metric-slot5to8-backup.sql
-- 注意：MC_005-008 calc_mode=MANUAL（贷款余额由业务导入 CUST_INDEX_RESULT.val_5-8），导入前页面显示 '-'。
-- ============================================================================

-- ---------- yiti：腾出槽位 5-8 ----------
-- 1) 迁移 M_009x 的宽表数据 val_5-8 → val_37-40，并清空 val_5-8（留给贷款）
UPDATE yiti.CUST_INDEX_RESULT
   SET val_37 = val_5, val_38 = val_6, val_39 = val_7, val_40 = val_8,
       val_5 = NULL, val_6 = NULL, val_7 = NULL, val_8 = NULL;

-- 2) 重挂 M_009x 指标到 37-40（与上面数据对应）
UPDATE yiti.PERF_METRIC_DEF SET val_slot = 37 WHERE metric_code = 'M_0091';
UPDATE yiti.PERF_METRIC_DEF SET val_slot = 38 WHERE metric_code = 'M_0093';
UPDATE yiti.PERF_METRIC_DEF SET val_slot = 39 WHERE metric_code = 'M_0095';
UPDATE yiti.PERF_METRIC_DEF SET val_slot = 40 WHERE metric_code = 'M_0097';

-- 3) 新建贷款余额指标 MC_005-008 于槽位 5-8
INSERT IGNORE INTO yiti.PERF_METRIC_DEF
  (id, metric_code, metric_name, base_dim, metric_level, calc_freq, calc_mode, calc_logic_type,
   summary_rule, ref_metric_codes, val_slot, decimal_places, deleted, metric_category, status, created_by, updated_by)
VALUES
  (REPLACE(UUID(),'-',''), 'MC_005', '当前贷款余额',     'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 5, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed'),
  (REPLACE(UUID(),'-',''), 'MC_006', '较上日贷款余额',   'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 6, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed'),
  (REPLACE(UUID(),'-',''), 'MC_007', '年均贷款余额',     'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 7, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed'),
  (REPLACE(UUID(),'-',''), 'MC_008', '较上年均贷款余额', 'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 8, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed');

-- ---------- onepl：5-8 空闲，直接建 ----------
INSERT IGNORE INTO onepl.PERF_METRIC_DEF
  (id, metric_code, metric_name, base_dim, metric_level, calc_freq, calc_mode, calc_logic_type,
   summary_rule, ref_metric_codes, val_slot, decimal_places, deleted, metric_category, status, created_by, updated_by)
VALUES
  (REPLACE(UUID(),'-',''), 'MC_005', '当前贷款余额',     'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 5, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed'),
  (REPLACE(UUID(),'-',''), 'MC_006', '较上日贷款余额',   'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 6, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed'),
  (REPLACE(UUID(),'-',''), 'MC_007', '年均贷款余额',     'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 7, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed'),
  (REPLACE(UUID(),'-',''), 'MC_008', '较上年均贷款余额', 'CUST', 1, 'DAY', 'MANUAL', NULL, 'SUM', '[]', 8, 2, 0, '规模类', 'ACTIVE', 'seed', 'seed');
