-- ============================================================
-- V1.13 # 1f: PERF_METRIC_DEF 补 V1.7 指标级调度 3 个字段
-- ============================================================
-- 背景：根据 onepl 重建 yiti / onepl_test_bootstrap 后跑 mvn verify，
-- perf failsafe 8 个用例失败，根因为 onepl 的 PERF_METRIC_DEF 缺
-- V1.7 引入的 cron_expr / subject_sql / last_run_time 三列。
--
-- 验证（schema-drift 子代理 2026-05-02 全库扫）：仅此一处 drift。
--
-- 执行：在 onepl / yiti / onepl_test_bootstrap 三库各跑一次。
-- ============================================================

ALTER TABLE PERF_METRIC_DEF
  ADD COLUMN cron_expr     VARCHAR(64)  NULL COMMENT 'V1.7 Quartz cron 表达式',
  ADD COLUMN subject_sql   LONGTEXT     NULL COMMENT 'V1.7 EXPR/GROOVY 主体集合 SQL',
  ADD COLUMN last_run_time DATETIME     NULL COMMENT 'V1.7 上次自动调度执行时间';
