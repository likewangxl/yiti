-- =============================================================================
-- 2026-06-02 新建调整申请「原业绩分配手工录入」：PERF_ALLOC_ADJUST_ITEM 复用存储
-- -----------------------------------------------------------------------------
-- 背景：原业绩分配原先由后端按客户编号查历史审批通过分配自动派生；新增「查不到则
--       手工录入」后，手工原业绩分配记录复用 PERF_ALLOC_ADJUST_ITEM 存储，用 item_kind
--       区分 NEW(新分配明细) / ORIGIN(原业绩分配)，并加 acct_no(账号，原业绩分配选填)。
-- 唯一键：原 uk_apply_emp(apply_id, emp_id) 会让「同一员工既是原业绩所属人又是新分配人」
--         撞唯一键，故扩展为 uk_apply_emp_kind(apply_id, emp_id, item_kind)。
-- 执行：在 yiti(dev) 与 onepl(prod) 双库执行（已于 2026-06-02 应用）。
-- =============================================================================

ALTER TABLE PERF_ALLOC_ADJUST_ITEM
  ADD COLUMN item_kind VARCHAR(16) NOT NULL DEFAULT 'NEW'
      COMMENT '明细类型:NEW新分配/ORIGIN原业绩分配' AFTER apply_id,
  ADD COLUMN acct_no VARCHAR(64) DEFAULT NULL
      COMMENT '账号(原业绩分配选填)' AFTER item_kind;

ALTER TABLE PERF_ALLOC_ADJUST_ITEM
  DROP INDEX uk_apply_emp,
  ADD UNIQUE KEY uk_apply_emp_kind (apply_id, emp_id, item_kind);
