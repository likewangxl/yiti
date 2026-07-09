-- =====================================================================
-- 指标重算任务监控 菜单 —— 挂在「绩效与考核」组(M_GROUP_PERF)下，考核计算之后（rank 7）
-- 前端路由 /perf/task-monitor（TaskMonitor.vue；列表走 GET /api/perf/run-tasks，
--   资源 P_PERF_RT_LIST 已注册）
-- 角色绑定：复用 P_PERF_RT_LIST（任务日志列表接口）的角色集
--   —— 凡可调 run-tasks 列表 API 的角色均可见本菜单
-- 幂等：先删同名行再插入，可重复执行。yiti（dev）与 onepl（prod）双库执行。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_PERF_TASK_MONITOR';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_PERF_TASK_MONITOR';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_PERF_TASK_MONITOR', '/perf/task-monitor', 'MENU', '任务监控', NULL, 7,
   '1', '1', 'M_GROUP_PERF', '0', 'YITI', NOW(), 'seed', '绩效与考核-指标重算任务监控');

-- 绑定到与 P_PERF_RT_LIST（run-tasks 列表接口）相同的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRMPTM_', ROLE_ID), ROLE_ID, 'M_PERF_TASK_MONITOR', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_RT_LIST';
