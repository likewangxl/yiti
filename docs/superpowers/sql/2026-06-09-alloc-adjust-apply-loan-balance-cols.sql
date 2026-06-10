-- ============================================================================
-- 新建调整申请-余额概览：PERF_ALLOC_ADJUST_APPLY 增加贷款余额 4 列快照
--   loan_curr_bal / loan_m_avg_bal / loan_q_avg_bal / loan_y_avg_bal（decimal(20,4)）
--   分别对应贷款 当前/较上日/年均/较上年均余额（MC_005-008），提交时落库，查看/编辑反显。
-- 与存款 curr_bal/m_avg_bal/q_avg_bal/y_avg_bal 同构。yiti+onepl 双库；INFORMATION_SCHEMA 预检幂等。
-- ============================================================================

-- yiti
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME='PERF_ALLOC_ADJUST_APPLY' AND COLUMN_NAME='loan_curr_bal');
SET @s := IF(@c=0,
  'ALTER TABLE yiti.PERF_ALLOC_ADJUST_APPLY
     ADD COLUMN loan_curr_bal  decimal(20,4) NULL COMMENT ''贷款-当前余额快照(MC_005)''   AFTER y_avg_bal,
     ADD COLUMN loan_m_avg_bal decimal(20,4) NULL COMMENT ''贷款-较上日余额快照(MC_006)'' AFTER loan_curr_bal,
     ADD COLUMN loan_q_avg_bal decimal(20,4) NULL COMMENT ''贷款-年均余额快照(MC_007)''   AFTER loan_m_avg_bal,
     ADD COLUMN loan_y_avg_bal decimal(20,4) NULL COMMENT ''贷款-较上年均余额快照(MC_008)'' AFTER loan_q_avg_bal',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- onepl
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME='PERF_ALLOC_ADJUST_APPLY' AND COLUMN_NAME='loan_curr_bal');
SET @s := IF(@c=0,
  'ALTER TABLE onepl.PERF_ALLOC_ADJUST_APPLY
     ADD COLUMN loan_curr_bal  decimal(20,4) NULL COMMENT ''贷款-当前余额快照(MC_005)''   AFTER y_avg_bal,
     ADD COLUMN loan_m_avg_bal decimal(20,4) NULL COMMENT ''贷款-较上日余额快照(MC_006)'' AFTER loan_curr_bal,
     ADD COLUMN loan_q_avg_bal decimal(20,4) NULL COMMENT ''贷款-年均余额快照(MC_007)''   AFTER loan_m_avg_bal,
     ADD COLUMN loan_y_avg_bal decimal(20,4) NULL COMMENT ''贷款-较上年均余额快照(MC_008)'' AFTER loan_q_avg_bal',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
