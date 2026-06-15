-- ============================================================================
-- 2026-06-15 PERF_KPI_SCORE 复合索引（KPI 计算结果详情页查询提速）
--
-- 【背景】KPI 计算结果详情页（GET /api/perf/kpi-score/results）对 PERF_KPI_SCORE 跑 3 条查询：
--   ① countSubjectGroups   : WHERE data_date,scheme_code[,subject_type] + 数据范围; GROUP BY subject_id,subject_type
--   ② selectSubjectGroups  : 同上 + SUM(score) + ORDER BY subject_id LIMIT（对象分页）
--   ③ selectByDateSchemeSubjects（明细批量）: WHERE data_date,scheme_code AND ((subject_type=? AND subject_id=?) OR ...)
--
-- 【问题】现有唯一键 uk_date_scheme_metric_subject = (data_date,scheme_code,metric_code,subject_type,subject_id)，
--   第 3 列是 metric_code，挡在 subject_* 前面：
--   - ①②只能用到 (data_date,scheme_code) 等值前缀，GROUP BY/ORDER BY 落到 Using temporary; Using filesort；
--   - ③不带 metric_code 过滤 → 退化为扫 (date,scheme) 全部行再筛对象（实测 rows≈626）。
--
-- 【方案】加一条覆盖三查询的复合索引（列序经 EXPLAIN 验证）：
--   (data_date, scheme_code, subject_type, subject_id, score)
--   - 选中维度时（subject_type 等值）：②的 GROUP BY subject_id + ORDER BY subject_id 完全走索引，
--     temp/filesort 消失，且 score 在索引内 → SUM 覆盖查询（Using index）。实测 Extra: Using index。
--   - ③明细批量：每个 (subject_type,subject_id) 对命中 4 列等值前缀 → ref 精确定位。
--     实测 rows 626 → 4（≈156×↓）。这是每页最重的一条，收益最大。
--   - 未选维度时：GROUP BY 仍有 temp/filesort，但已是 index-only 小数据集（covering），开销很低。
--   与唯一键不冗余（UK 第 3 列是 metric_code，本索引第 3 列是 subject_type，前缀不同）。
--   写入代价：仅 KPI 计算批量 upsert 时多维护 1 条二级索引；计算是批处理、非延迟敏感，可接受。
--
-- 【幂等】MySQL 无 CREATE INDEX IF NOT EXISTS，用 INFORMATION_SCHEMA 预检 + 动态 SQL；可重复执行。
-- 【双库】yiti + onepl 均执行。
-- ============================================================================

-- ===================== yiti =====================
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'yiti' AND TABLE_NAME = 'PERF_KPI_SCORE'
                  AND INDEX_NAME = 'idx_kpi_score_subject');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_kpi_score_subject ON yiti.PERF_KPI_SCORE (data_date, scheme_code, subject_type, subject_id, score)',
  'SELECT ''[yiti] idx_kpi_score_subject 已存在，跳过'' AS msg');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
ANALYZE TABLE yiti.PERF_KPI_SCORE;

-- ===================== onepl =====================
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'onepl' AND TABLE_NAME = 'PERF_KPI_SCORE'
                  AND INDEX_NAME = 'idx_kpi_score_subject');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_kpi_score_subject ON onepl.PERF_KPI_SCORE (data_date, scheme_code, subject_type, subject_id, score)',
  'SELECT ''[onepl] idx_kpi_score_subject 已存在，跳过'' AS msg');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
ANALYZE TABLE onepl.PERF_KPI_SCORE;

-- ===================== 验证（按需手工执行） =====================
-- 选中维度（应为 Extra: Using index，无 temporary/filesort）：
--   EXPLAIN SELECT subject_id, subject_type, SUM(score) FROM PERF_KPI_SCORE
--    WHERE data_date='2026-06-12' AND scheme_code='KPI_0610' AND subject_type='EMP'
--    GROUP BY subject_id, subject_type ORDER BY subject_id LIMIT 20;
-- 明细批量（应命中 idx_kpi_score_subject，rows 个位数）：
--   EXPLAIN SELECT * FROM PERF_KPI_SCORE
--    WHERE data_date='2026-06-12' AND scheme_code='KPI_0610'
--      AND ((subject_type='EMP' AND subject_id='E001') OR (subject_type='ORG' AND subject_id='130'));
-- 回滚：DROP INDEX idx_kpi_score_subject ON PERF_KPI_SCORE;
-- ============================================================================
