-- V1.7：删除 DailyKpiCalcJob 调度任务（KPI 改为事件驱动重算）
-- 引入版本：V1.7（2026-04-30）
-- 注：历史真实 job_key 为 'DAILY_KPI_CALC'（无 PERF_ 前缀）
-- 此脚本需手动执行；非 Flyway 自动迁移（项目无 Flyway 自动加载配置）
DELETE FROM SYS_JOB_CONF WHERE job_key = 'DAILY_KPI_CALC';
