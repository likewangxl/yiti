-- =====================================================================
-- 指标重算任务监控：任务类型字典 + 汇总/批量执行资源 + 角色绑定
-- 幂等：先删同键再插；yiti(dev) 执行。
-- 关联 spec: docs/superpowers/specs/2026-07-15-perf-task-monitor-execute-history-design.md
-- =====================================================================

-- 1) 任务类型字典（DictFacade 从 SYS_DICT 读；前端 /sys/dicts/PERF_TASK_TYPE/items）
DELETE FROM SYS_DICT WHERE dict_type = 'PERF_TASK_TYPE';
INSERT INTO SYS_DICT
  (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by, created_time)
VALUES
  ('DICT_PERF_TASK_TYPE_RECALC', 'PERF_TASK_TYPE', 'METRIC_RECALC', '指标重算', 'METRIC_RECALC',
   1, 'ACTIVE', '任务监控-任务类型', 'seed', NOW());

-- 2) 资源：分组汇总（只读 LIST）+ 批量执行（高危 EXECUTE）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_RT_SUM', 'P_PERF_MTR_BEXEC');
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID IN ('P_PERF_RT_SUM', 'P_PERF_MTR_BEXEC');

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('P_PERF_RT_SUM', '/api/perf/run-tasks/metric-summary', 'GET', '任务监控-指标汇总', NULL, 0,
   0, '0', 'M_PERF_METRICS', 0, 'PERF', NOW(), 'seed', '任务监控-按指标分组汇总'),
  ('P_PERF_MTR_BEXEC', '/api/perf/metrics/batch-execute', 'POST', '指标批量执行', NULL, 0,
   0, '0', 'M_PERF_METRICS', 0, 'PERF', NOW(), 'seed', '任务监控-批量执行(高危)');

-- 3) 角色绑定：
--    汇总资源 → 复用 P_PERF_RT_LIST（能看任务列表的角色都能看汇总）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPRTS_', ROLE_ID), ROLE_ID, 'P_PERF_RT_SUM', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_RT_LIST';
--    批量执行资源 → 复用 P_PERF_MTR_EXEC（能单执行的角色才能批量执行）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPMBX_', ROLE_ID), ROLE_ID, 'P_PERF_MTR_BEXEC', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_MTR_EXEC';
