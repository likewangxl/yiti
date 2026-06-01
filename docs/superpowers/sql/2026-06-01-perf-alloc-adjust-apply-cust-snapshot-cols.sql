-- =====================================================================
-- 2026-06-01  PERF_ALLOC_ADJUST_APPLY 新增客户快照列
-- 用途：新建调整申请时，把反显的客户编号/客户名称 + 余额概览（当前/月均/季均/年均）
--       一并快照存入业绩调整申请表；后续列表/详情直接读这些列，不再实时反查客户/统计表。
-- 幂等：INFORMATION_SCHEMA 预检，列已存在则跳过。
-- 部署：dev 库 yiti + 生产库 onepl 双跑。
-- =====================================================================

DROP PROCEDURE IF EXISTS pf_add_alloc_apply_cust_cols;
DELIMITER //
CREATE PROCEDURE pf_add_alloc_apply_cust_cols()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_ALLOC_ADJUST_APPLY'
                   AND COLUMN_NAME = 'cust_no') THEN
    ALTER TABLE PERF_ALLOC_ADJUST_APPLY
      ADD COLUMN cust_no   VARCHAR(64)    NULL COMMENT '客户编号（提交时快照，业务编号）' AFTER cust_id,
      ADD COLUMN cust_name VARCHAR(200)   NULL COMMENT '客户名称（提交时反显快照）'       AFTER cust_no,
      ADD COLUMN curr_bal  DECIMAL(20,4)  NULL COMMENT '当前余额（提交时快照）'           AFTER cust_name,
      ADD COLUMN m_avg_bal DECIMAL(20,4)  NULL COMMENT '月均余额（提交时快照）'           AFTER curr_bal,
      ADD COLUMN q_avg_bal DECIMAL(20,4)  NULL COMMENT '季日均余额（提交时快照）'         AFTER m_avg_bal,
      ADD COLUMN y_avg_bal DECIMAL(20,4)  NULL COMMENT '年日均余额（提交时快照）'         AFTER q_avg_bal;
  END IF;
END //
DELIMITER ;
CALL pf_add_alloc_apply_cust_cols();
DROP PROCEDURE IF EXISTS pf_add_alloc_apply_cust_cols;
