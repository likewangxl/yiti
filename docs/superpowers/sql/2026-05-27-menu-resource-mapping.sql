-- 菜单 → 接口资源 联动映射
-- 2026-05-27
--
-- 方案：给 PT_RESOURCE 中 ISMENU=0 的接口资源填上 PARENT_RESOURCE_ID，
--      指向所属菜单 RESOURCE_ID。后端 replaceMenus 会按此联动绑/解。
--
-- 未覆盖的接口（约 130+ 条）PARENT_RESOURCE_ID 保持 NULL，作为"公共基础接口"，
-- 后端逻辑：只要分配 ≥1 个菜单就把所有 NULL parent 的接口一起绑给角色；
-- 菜单全清则全清。
--
-- ⚠️ 执行前请人工 review 各 UPDATE 段后面带 "⚠️" 标记的边界归属是否合理。
-- ⚠️ 不影响表结构，只填 PARENT_RESOURCE_ID 字段数据。

USE yiti;

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 1. 绩效与考核（M_GROUP_PERF 下 6 个菜单）                              ║
-- ╚══════════════════════════════════════════════════════════════════════╝

-- 指标库
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_PERF_METRICS' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/perf/metrics%' OR
  RESOURCE_URL LIKE '/api/perf/metric-calc%'
);

-- KPI 规则
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_PERF_KPI_RULES' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/perf/kpi-schemes%';

-- 目标管理（含目标方案 + 目标值 + 目标调整审批流）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_PERF_TARGETS' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/perf/target-plans%' OR
  RESOURCE_URL LIKE '/api/perf/target-values%' OR
  RESOURCE_URL LIKE '/api/perf/target-adjust%'
);

-- 数据导入
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_PERF_IMPORT' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/perf/import%';

-- 业绩调整（含申请审批 + 分配关系查询）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_PERF_ADJUST' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/perf/alloc-adjust%' OR
  RESOURCE_URL LIKE '/api/perf/alloc-relations%'
);

-- 考核计算（含历史重算、版本控制、运行任务、导出）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_PERF_COMPUTE' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/perf/recalc%' OR
  RESOURCE_URL LIKE '/api/perf/run-tasks%' OR
  RESOURCE_URL LIKE '/api/perf/sys-control%' OR
  RESOURCE_URL LIKE '/api/perf/export%'
);

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 2. 报表分析（M_GROUP_REPORT 下 5 个菜单）                              ║
-- ╚══════════════════════════════════════════════════════════════════════╝

-- 动态指标查询（动态查询执行/导出 + 保存方案 + 维度元数据）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_REPORT_DYNAMIC' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/reports/dynamic-query%' OR
  RESOURCE_URL LIKE '/api/reports/saved-queries%' OR
  RESOURCE_URL LIKE '/api/reports/query-dimensions%'
);

-- 行长仪表盘
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_REPORT_DASHBOARD' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/reports/dashboard%';

-- 预置报表（客户池/绩效/触达汇总 + 导出任务管理）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_REPORT_PRESETS' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/reports/customer-pool-summary%' OR
  RESOURCE_URL LIKE '/api/reports/perf-summary%' OR
  RESOURCE_URL LIKE '/api/reports/touch-task-summary%' OR
  RESOURCE_URL LIKE '/api/reports/export-tasks%'
);

-- SQL 探查
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_REPORT_SQL' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/reports/sql-probe%' OR
  RESOURCE_URL LIKE '/api/admin/sql-probe%'
);

-- 自由报表（KPI/积分自由报表）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_REPORT_FREE' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/reports/free%';

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 3. 内部评价（M_GROUP_EVAL 下 5 个菜单）                                ║
-- ╚══════════════════════════════════════════════════════════════════════╝

-- 标签管理（评价标签 + 客户标签 /api/tags）
-- ⚠️ /api/tags 既是客户营销标签也用于评价场景 — 这里统一归"标签管理"菜单
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_EVAL_TAGS' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/admin/eval/tags%' OR
  RESOURCE_URL LIKE '/api/tags%'
);

-- 人员标签
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_EVAL_USER_TAGS' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/eval/user-tags%';

-- 评价规则
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_EVAL_RULES' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/eval/rules%';

-- 评价任务（管理端）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_EVAL_TASKS' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/eval/tasks%';

-- 我的评价（用户端）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_EVAL_MY_TASKS' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/eval%';

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 4. 系统设置（M_GROUP_SYSTEM 下 12 个菜单）                             ║
-- ╚══════════════════════════════════════════════════════════════════════╝

-- 用户管理
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_USERS' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/users%';

-- 角色管理（含角色的资源/菜单绑定接口）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_ROLES' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/roles%';

-- 资源/菜单维护
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_RESOURCES' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/resources%';

-- 权限配置（数据范围 BizScope 矩阵）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_PERMISSION' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/biz-scopes%';

-- 字典管理（仅写操作；只读 /api/sys/dicts 留 NULL 作公共）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_DICT' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/dicts%';

-- 工作日历（仅写；只读 /api/sys/calendar 留 NULL 作公共）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_CALENDAR' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/calendar%';

-- 任务调度
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_JOBS' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/jobs%';

-- 审计日志
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_AUDIT' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/audit%';

-- 通知消息（仅管理端的发通知配置；用户自己拿通知 /api/notifications 留 NULL 公共）
-- 当前 PT_RESOURCE 里没有 /api/admin/sys/notifications，所以此 UPDATE 0 行也正常
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_NOTIFICATIONS' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/notifications%';

-- 系统配置
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_CONFIG' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/configs%';

-- 文件管理（管理端文件；普通文件操作 /api/files 留 NULL 公共，所有人能传/下载自己文件）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_FILES' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/sys/files%';

-- 公告管理（管理端；用户端读 /api/portal/announcements 归"工作台"）
UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_SYS_ANN' WHERE ISMENU=0 AND
  RESOURCE_URL LIKE '/api/admin/announcements%';

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 5. 工作台（M_ROOT_WORKSPACE 单页菜单）                                  ║
-- ╚══════════════════════════════════════════════════════════════════════╝

UPDATE PT_RESOURCE SET PARENT_RESOURCE_ID='M_ROOT_WORKSPACE' WHERE ISMENU=0 AND (
  RESOURCE_URL LIKE '/api/portal%' OR
  RESOURCE_URL LIKE '/api/admin/nav%' OR
  RESOURCE_URL = '/api/nav'
);

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 6. 留 NULL 的「公共基础接口」（约 130+ 条，分菜单时一起绑）             ║
-- ╚══════════════════════════════════════════════════════════════════════╝
--
-- 这些接口的 PARENT_RESOURCE_ID 保持 NULL，包括：
--   /api/auth/*           认证（登录/登出/我的菜单/权限校验）
--   /api/notifications/*  用户自己的通知（拉取/标记已读）
--   /api/files/*          普通文件上传/下载
--   /api/sys/dicts        只读字典
--   /api/sys/calendar     只读工作日历
--   /api/customers/*      客户营销 — 客户档案
--   /api/leads/*          客户营销 — 线索
--   /api/customer-pool/*, /api/cust-pool/*, /api/claims/*, /api/my-claims/*  客户池
--   /api/touch-tasks/*, /api/touch-reports/*, /api/admin/touch-tasks/*       触达
--   /api/loans/*, /api/business-application/*                                业务申请
--   /api/support-*/*                                                         中场支持
--   /api/products/*, /api/documents/*, /api/admin/documents/*               产品/文档
--   /api/orgs/*, /api/employees/*                                            组织/员工
--   /api/admin/workflow/*  ⚠️ 工作流配置（高危）— 当前无对应菜单，按"公共"处理
--   /api/workflow/*                                                          业务工作流
--   /api/report, /api/data-task                                              孤儿
--
-- 后端 replaceMenus 行为：分配任何菜单时这些接口都跟着绑；菜单全清则一并解绑。
--
-- 如果你不想 workflow 配置/业务接口对所有角色开放，
-- 后续可以补"客户营销/业务执行/工作流"菜单，再重跑相应 UPDATE。

-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║ 验证：每个菜单挂了几个接口                                              ║
-- ╚══════════════════════════════════════════════════════════════════════╝

-- 查看每菜单挂载数（应非 0；为 0 说明该菜单还没接口）
SELECT m.RESOURCE_ID, m.MENU_NAME, COUNT(api.RESOURCE_ID) AS api_count
FROM PT_RESOURCE m
LEFT JOIN PT_RESOURCE api ON api.PARENT_RESOURCE_ID = m.RESOURCE_ID AND api.ISMENU = 0
WHERE m.ISMENU = 1
GROUP BY m.RESOURCE_ID, m.MENU_NAME
ORDER BY api_count DESC;

-- 查 NULL 公共接口总数（预期 ~130+）
SELECT COUNT(*) AS public_api_count
FROM PT_RESOURCE
WHERE ISMENU = 0 AND (PARENT_RESOURCE_ID IS NULL OR PARENT_RESOURCE_ID = '');
