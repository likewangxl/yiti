-- ============================================================================
-- PERF_ALLOC_ADJUST_APPLY 删除 cust_no 字段，统一并入 cust_id
-- ----------------------------------------------------------------------------
-- 背景：原 cust_id=客户主档内部主键 / cust_no=业务客户编号 双字段模型。现网数据中
--      cust_id 恒等于 cust_no（无真实主档主键差异），故合并为单字段 cust_id（直接存
--      用户输入的客户编号）。后端已去除 cust_master 反查；前端字段 custNo→custId。
--
-- 安全：先把任何 cust_id 为空的历史行用 cust_no 回填（现网为 0 行，幂等兜底），再删列。
-- 双库：dev=yiti / prod=onepl 均需执行。
-- 执行顺序：必须在"去除 cust_no 引用的新版后端已部署"之后再删列（旧版后端 SELECT cust_no）。
-- ============================================================================

-- 1. 兜底回填（现网 cust_id 已全部=cust_no，此处仅防御历史空值）
UPDATE PERF_ALLOC_ADJUST_APPLY
   SET cust_id = cust_no
 WHERE (cust_id IS NULL OR cust_id = '') AND cust_no IS NOT NULL;

-- 2. 删除 cust_no 列
ALTER TABLE PERF_ALLOC_ADJUST_APPLY DROP COLUMN cust_no;
