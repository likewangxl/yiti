-- V1.7：删除 DailyKpiCalcJob 调度任务（KPI 改为事件驱动重算）
-- 引入版本：V1.7（2026-04-30）
DELETE FROM SYS_JOB_CONF WHERE job_key = 'PERF_DAILY_KPI_CALC';
