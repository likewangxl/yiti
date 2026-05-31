-- 2026-05-31 【仅 DEV 测试库 yiti】KS001 KPI 方案指标项重指到真实指标库 code
-- 背景：种子 KPI 方案 KS001 的 6 个指标项原本引用占位假 code（M0001/M0002/M0003/M0004/M_FEE/M1001），
--      这些 code 在 PERF_METRIC_DEF 指标库中并不存在。新增目标值页指标下拉改为「只显示目标方案所选
--      KPI 中定义的指标项」后，KS001 关联的 EMP 维度方案会因 KPI∩指标库为空而下拉无项，无法验证功能。
-- 处置：把 KS001 的 6 项一对一重指到 6 个真实 ACTIVE/EMP 指标，便于在 dev 立即验证下拉过滤。
-- 范围：⚠️ 仅 dev 测试库 yiti 执行；不要在生产 onepl 执行（生产 KPI 应通过 UI 选真实指标维护）。
-- 备份：docs/superpowers/sql/backup/2026-05-31-ks001-items-backup.sql（重指前 6 行原值）。

UPDATE PERF_KPI_ITEM SET metric_code = CASE id
  WHEN 'KI001A' THEN 'M_0001'
  WHEN 'KI001B' THEN 'M_0004'
  WHEN 'KI001C' THEN 'M_0005'
  WHEN 'KI001D' THEN 'M_0006'
  WHEN 'KI001E' THEN 'M_0007'
  WHEN 'KI001F' THEN 'M_0008'
END
WHERE scheme_id = 'KS001';
