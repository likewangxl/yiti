-- 反向：drop 索引 + 3 列
ALTER TABLE PERF_METRIC_DEF DROP INDEX idx_metric_def_schedulable;
ALTER TABLE PERF_METRIC_DEF
  DROP COLUMN cron_expr,
  DROP COLUMN subject_sql,
  DROP COLUMN last_run_time;
