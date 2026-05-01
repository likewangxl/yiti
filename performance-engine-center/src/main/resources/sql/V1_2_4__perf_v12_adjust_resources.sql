-- =====================================================================
-- performance-engine-center V1.2 Phase Q8.6 遗留规划资源激活
-- Version: V1_2_3
-- Date: 2026-04-24
-- Task: Q8.6
--
-- 目的：清理 V1_0_4 登记但尚未激活的 V1.1/V1.2 规划资源。V1.1 Q8 已激活
--   P_PERF_IMPORT_UPLOAD，V1.2 Q6 激活 P_PERF_EXPORT_KPI / P_PERF_EXPORT_ALLOC；
--   本脚本补齐余下 V1.1/V1.2 已交付功能对应的资源，并对 URL 做权威校准：
--
-- V1.1 交付项（URL 对齐 Controller 实际映射）：
--   - P_PERF_METRIC_EXEC   /api/perf/metrics/*/execute      V1.1 P3 交付
--   - P_PERF_METRIC_TRIAL  /api/perf/metrics/*/trial-run    V1.1 P3 交付
--   - P_PERF_KPI_TRIGGER   /api/perf/kpi-calc/trigger       V1.1 P4 交付
--   - P_PERF_DTASK_STATUS  /api/data-task/status            V1.1 P6 交付（URL 简化为 /api/data-task）
--
-- V1.2 交付项：
--   - P_PERF_ALLOC_ADJ_ADD /api/perf/alloc-adjust/create    V1.2 Q2 交付（URL /apply → /create）
--   - P_PERF_KPI_RECALC    /api/perf/recalc                 V1.2 Q3 交付（URL 对齐 PerfCalcController）
--   - P_PERF_SC_ROLLBACK   /api/perf/sys-control/rollback   V1.2 Q1 交付
--
-- 幂等：ON DUPLICATE KEY UPDATE；STATUS 1→0 激活。
-- =====================================================================

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/perf/metrics/*/execute',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = '指标执行',
    REMARK          = 'V1.1 P3 激活（Q8.6 补遗）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_METRIC_EXEC';

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/perf/metrics/*/trial-run',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = '指标试算',
    REMARK          = 'V1.1 P3 激活（Q8.6 补遗）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_METRIC_TRIAL';

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/perf/kpi-calc/trigger',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = 'KPI 计算触发',
    REMARK          = 'V1.1 P4 激活（Q8.6 补遗）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_KPI_TRIGGER';

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/data-task/status',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = '外部数据任务状态上报',
    REMARK          = 'V1.1 P6 激活（Q8.6 补遗，URL 简化）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_DTASK_STATUS';

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/perf/alloc-adjust/create',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = '分配调整申请',
    REMARK          = 'V1.2 Q2 激活（Q8.6 补遗）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_ALLOC_ADJ_ADD';

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/perf/recalc',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = 'KPI 历史回算',
    REMARK          = 'V1.2 Q3 激活（Q8.6 补遗）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_KPI_RECALC';

UPDATE PT_RESOURCE
SET STATUS          = 0,
    RESOURCE_URL    = '/api/perf/sys-control/rollback',
    RESOURCE_METHOD = 'POST',
    MENU_NAME       = '版本回滚（高危）',
    REMARK          = 'V1.2 Q1 激活（Q8.6 补遗）',
    UPDATE_TIME     = NOW(),
    UPDATE_USER     = 'seed'
WHERE RESOURCE_ID = 'P_PERF_SC_ROLLBACK';
