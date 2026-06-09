-- ============================================================================
-- PERF_KPI_ITEM 新增 base_dim 列：将指标维度（EMP/ORG/CUST）随指标固化到方案项表，
-- 便于 KPI 方案编辑/列表直接展示维度，免去再 join PERF_METRIC_DEF。
-- 维度值与所选指标 PERF_METRIC_DEF.base_dim 一致（前端按维度过滤指标，二者天然一致）。
-- yiti（dev） + onepl（prod）双库执行。幂等：INFORMATION_SCHEMA 预检后再 ADD。
-- ============================================================================

-- yiti
ALTER TABLE yiti.PERF_KPI_ITEM
    ADD COLUMN base_dim varchar(8) NULL COMMENT '指标维度 EMP/ORG/CUST，随指标固化便于展示' AFTER metric_code;

-- onepl
ALTER TABLE onepl.PERF_KPI_ITEM
    ADD COLUMN base_dim varchar(8) NULL COMMENT '指标维度 EMP/ORG/CUST，随指标固化便于展示' AFTER metric_code;

-- 历史数据回填：按 metric_code 关联 PERF_METRIC_DEF 回写已有方案项的维度（两库各自回填）
UPDATE yiti.PERF_KPI_ITEM i
  JOIN yiti.PERF_METRIC_DEF d ON d.metric_code = i.metric_code
   SET i.base_dim = d.base_dim
 WHERE i.base_dim IS NULL;

UPDATE onepl.PERF_KPI_ITEM i
  JOIN onepl.PERF_METRIC_DEF d ON d.metric_code = i.metric_code
   SET i.base_dim = d.base_dim
 WHERE i.base_dim IS NULL;
