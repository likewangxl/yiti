-- ====================================================================
-- 菜单分配 v1 种子数据：PT_RESOURCE 补 25 条菜单记录 + 修正历史脏数据
--
-- 背景：项目至今 PT_RESOURCE 303 条全是 API 接口/操作记录，
-- 菜单维度（IS_MENU=1 + parent_resource_id 树）从未建立。
-- 本脚本补齐菜单层级，使"角色 → 分配菜单"对话框有真实数据可勾。
--
-- 字段顺序：RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
--           MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID,
--           STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK
--
-- 命名规约：菜单 ID 一律 M_ 开头，与现有 A_/P_/R_/S_ 等接口资源前缀隔离。
-- RESOURCE_METHOD 用约定值 'MENU'（仅占位，不参与 URL 拦截）。
-- 表结构未变更（零 ALTER），完全沿用现有列。
-- ====================================================================

-- ──────────────────────────────────────────────────────────────────
-- Step 0：修正历史脏数据
-- 项目早期种子里 9 条接口型记录被误标为 ISMENU=1（如"工作流任务审批通过"），
-- 本步骤把它们改回 ISMENU=0，避免菜单树查询里出现伪菜单。
-- ──────────────────────────────────────────────────────────────────
UPDATE PT_RESOURCE
   SET ISMENU = 0
 WHERE ISMENU = 1
   AND RESOURCE_METHOD <> 'MENU';

-- ──────────────────────────────────────────────────────────────────
-- Step 0.5：清掉历史菜单种子让脚本幂等（重跑安全）
-- 仅删 M_ 前缀的菜单记录，不影响其他 303 条接口资源。
-- 同时清掉 PT_ROLE_RESOURCE 里跟菜单的绑定（避免外键约束 + 脏数据）。
-- ──────────────────────────────────────────────────────────────────
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'M_%';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID LIKE 'M_%';

-- ──────────────────────────────────────────────────────────────────
-- Step 1：根级菜单（1 条）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_ROOT_WORKSPACE', '/workspace', 'MENU', '工作台', '🏠', 0, 1, '1', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- Step 2：分组节点（3 条，parent=NULL，endflag=0 表示非叶）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
-- 分组节点 URL 必须唯一（uk_pt_resource_url_method_sys 约束），且不是真路由（# 前缀避免被前端 sidebar 误匹配）
('M_GROUP_PERF',   '#group/perf',   'MENU', '绩效与考核', '📈',  1, 1, '0', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1 - 分组'),
('M_GROUP_REPORT', '#group/report', 'MENU', '报表分析',   '📊',  2, 1, '0', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1 - 分组'),
('M_GROUP_SYSTEM', '#group/system', 'MENU', '系统设置',   '⚙️', 3, 1, '0', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1 - 分组');

-- ──────────────────────────────────────────────────────────────────
-- Step 3：绩效与考核（6 个叶子菜单）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_PERF_METRICS',   '/perf/metrics',   'MENU', '指标库',   NULL, 1, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_KPI_RULES', '/perf/kpi-rules', 'MENU', 'KPI 规则', NULL, 2, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_TARGETS',   '/perf/targets',   'MENU', '目标管理', NULL, 3, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_IMPORT',    '/perf/import',    'MENU', '数据导入', NULL, 4, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_ADJUST',    '/perf/adjust',    'MENU', '业绩调整', NULL, 5, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_COMPUTE',   '/perf/compute',   'MENU', '考核计算', NULL, 6, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- Step 4：报表分析（4 个叶子菜单）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_REPORT_DYNAMIC',   '/report/dynamic',   'MENU', '动态指标查询', NULL, 1, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_REPORT_DASHBOARD', '/report/dashboard', 'MENU', '行长仪表盘',   NULL, 2, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_REPORT_PRESETS',   '/report/presets',   'MENU', '预置报表',     NULL, 3, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_REPORT_SQL',       '/report/sql',       'MENU', 'SQL 探查',     NULL, 4, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- Step 5：系统设置（11 个叶子菜单）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_SYS_USERS',         '/system/users',         'MENU', '用户管理',  NULL,  1, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_ROLES',         '/system/roles',         'MENU', '角色管理',  NULL,  2, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_RESOURCES',     '/system/resources',     'MENU', '资源/菜单', NULL,  3, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_PERMISSION',    '/system/permission',    'MENU', '权限配置',  NULL,  4, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_DICT',          '/system/dict',          'MENU', '字典管理',  NULL,  5, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_CALENDAR',      '/system/calendar',      'MENU', '工作日历',  NULL,  6, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_JOBS',          '/system/jobs',          'MENU', '任务调度',  NULL,  7, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_AUDIT',         '/system/audit',         'MENU', '审计日志',  NULL,  8, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_NOTIFICATIONS', '/system/notifications', 'MENU', '通知消息',  NULL,  9, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_CONFIG',        '/system/config',        'MENU', '系统配置',  NULL, 10, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_FILES',         '/system/files',         'MENU', '文件管理',  NULL, 11, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- 验证：菜单总数应为 25（含 1 根级 + 3 分组 + 21 叶子）
--   SELECT COUNT(*) FROM PT_RESOURCE WHERE ISMENU = 1;
-- 验证树结构：
--   SELECT MENU_RANK_NO, RESOURCE_ID, MENU_NAME, MENU_ENDFLAG, PARENT_RESOURCE_ID
--     FROM PT_RESOURCE WHERE ISMENU = 1 ORDER BY PARENT_RESOURCE_ID, MENU_RANK_NO;
-- ──────────────────────────────────────────────────────────────────
