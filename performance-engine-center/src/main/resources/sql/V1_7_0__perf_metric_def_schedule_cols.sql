-- V1.7 指标级调度改造：补 cron_expr / subject_sql / last_run_time
-- 引入版本：V1.7（2026-04-30）
ALTER TABLE PERF_METRIC_DEF
  ADD COLUMN cron_expr     VARCHAR(120) NULL COMMENT '自定义 cron；留空按 calc_freq 推导默认',
  ADD COLUMN subject_sql   LONGTEXT     NULL COMMENT 'EXPR/GROOVY 类型主体集合 SQL',
  ADD COLUMN last_run_time DATETIME     NULL COMMENT '最近一次自动调度执行时间';

CREATE INDEX idx_metric_def_schedulable
  ON PERF_METRIC_DEF (status, calc_mode, deleted);
