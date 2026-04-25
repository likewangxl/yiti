-- ============================================================================
-- 2026-04-25 Option A 启动级集成测试前置数据库对齐脚本
-- 创建日期：2026-04-25
-- 用途：将本地 onepl 数据库更新到 Option A 集成测试可执行的最新状态
-- 前置：执行 mysqldump 备份 → docs/superpowers/sql/backup/2026-04-25-pre-option-a-backup.sql
-- 幂等：所有 DDL/DML 用 IF NOT EXISTS / INSERT IGNORE / ON DUPLICATE KEY UPDATE 保护
-- ============================================================================
--
-- 执行内容（按顺序）：
--   阶段 A：DROP 废表 report_saved_query（错误命名，应为 rpt_saved_query；已确认 0 行）
--   阶段 B：建 report 4 张表（rpt_saved_query / rpt_snapshot_task / sql_probe_history / rpt_export_task）
--   阶段 C：portal 3 条 PT_RESOURCE 修复版（原 2026-04-11-portal-resources-align.sql 列名错配）
--   阶段 D：bizapp 21 条 PT_RESOURCE 修复版（原 2026-04-14-bizapp-pt-resource.sql 列名错配）
--
-- 阶段 E（在外部按顺序串行执行，本脚本不内联）：
--   - docs/superpowers/sql/2026-04-14-customer-{lead,master,pool-claim,report,tag,touch}-pt-resource.sql（6 个）
--   - docs/superpowers/sql/2026-04-14-workflow-real-env-align.sql
--   - docs/superpowers/sql/2026-04-18-pt-resource-fix.sql
--   - docs/superpowers/sql/2026-04-18-pt-resource-portal-customer-fix.sql
--   - docs/superpowers/sql/2026-04-21-customer-contract-alignment-pt-resource.sql
--   - report-analytics-center/src/main/resources/sql/report/V1_0_{1,2,3,4,6,7}__rpt_*.sql（6 个）
-- ============================================================================

USE onepl;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 阶段 A：DROP 废表 report_saved_query
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS report_saved_query;

-- ----------------------------------------------------------------------------
-- 阶段 B：建 report 4 张表（同 V1_0_0__rpt_init.sql，幂等 IF NOT EXISTS）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rpt_saved_query` (
  `id`            varchar(32)  NOT NULL COMMENT '方案ID（UUID）',
  `emp_id`        varchar(32)  NOT NULL COMMENT '员工工号',
  `name`          varchar(200) NOT NULL COMMENT '方案名称',
  `dim`           varchar(20)  NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids`   text         NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes`  text         NOT NULL COMMENT '指标编码列表(JSON数组)',
  `version`       int(11)      DEFAULT 0 COMMENT '乐观锁版本号',
  `created_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`, `created_time`),
  KEY `idx_emp_id_name` (`emp_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';

CREATE TABLE IF NOT EXISTS `sql_probe_history` (
  `id`                 varchar(32)  NOT NULL COMMENT '历史ID（UUID）',
  `emp_id`             varchar(32)  NOT NULL COMMENT '执行人工号',
  `sql_text`           text         NOT NULL COMMENT 'SQL语句',
  `remark`             varchar(500) DEFAULT NULL COMMENT '备注(reason)',
  `row_count`          int(11)      DEFAULT NULL COMMENT '影响行数',
  `execution_time_ms`  int(11)      DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `status`             varchar(20)  DEFAULT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED/TIMEOUT',
  `error_msg`          text         DEFAULT NULL COMMENT '错误信息',
  `created_time`       datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_emp_time` (`emp_id`, `created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SQL探查历史';

CREATE TABLE IF NOT EXISTS `rpt_snapshot_task` (
  `id`             varchar(32)  NOT NULL COMMENT '任务ID',
  `task_name`      varchar(200) NOT NULL COMMENT '任务名称',
  `snapshot_type`  varchar(50)  NOT NULL COMMENT '快照类型（DAILY/MONTHLY，V2扩展）',
  `cron_expr`      varchar(100) NOT NULL COMMENT 'Cron表达式',
  `status`         varchar(20)  DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `last_run_time`  datetime     DEFAULT NULL COMMENT '最近执行时间',
  `next_run_time`  datetime     DEFAULT NULL COMMENT '下次执行时间',
  `created_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_next_run_time` (`next_run_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='快照任务配置（V1预留）';

CREATE TABLE IF NOT EXISTS `rpt_export_task` (
  `id`             varchar(32) NOT NULL COMMENT '导出任务ID',
  `export_type`    varchar(32) NOT NULL COMMENT '类型：DYNAMIC_QUERY/FIXED_REPORT/SQL_PROBE 等',
  `params_json`    text        DEFAULT NULL COMMENT '导出参数 JSON',
  `status`         varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `file_key`       varchar(200) DEFAULT NULL COMMENT 'MinIO object key',
  `file_size`      bigint      DEFAULT NULL COMMENT '文件大小（字节）',
  `row_count`      int         DEFAULT NULL COMMENT '导出行数',
  `expire_at`      datetime    DEFAULT NULL COMMENT '文件过期时间',
  `operator_id`    varchar(32) NOT NULL COMMENT '操作人员工号',
  `error_msg`      text        DEFAULT NULL COMMENT '失败原因',
  `created_time`   datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_export_type` (`export_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表异步导出任务';

-- ----------------------------------------------------------------------------
-- 阶段 C：portal 3 条 PT_RESOURCE 修复版（原 2026-04-11 列名错配）
-- 真实 schema：MENU_NAME / ISMENU / STATUS / CREATE_USER（非 RESOURCE_NAME / IS_MENU / IS_DELETED / CREATE_BY）
-- 4 条旧 dashboard 资源不存在 → 第二段 UPDATE 跳过
-- ----------------------------------------------------------------------------
-- 注：RESOURCE_ID varchar(20) 约束 → 缩短 ID 至 ≤ 20 字符
INSERT IGNORE INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('RES_PORTAL_WORKSPACE', '/api/portal/workspace',           'GET', '工作台聚合',       0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1 portal slice'),
  ('RES_PROD_SUP_AVL',     '/api/products/support-available', 'GET', '中场支持产品查询', 0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1 portal slice'),
  ('RES_SHORTCUT_PUT',     '/api/portal/shortcuts',           'PUT', '快捷入口全量替换', 0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1 portal slice');

-- ----------------------------------------------------------------------------
-- 阶段 D：bizapp 21 条 PT_RESOURCE 修复版（原 2026-04-14 列名错配）
-- ----------------------------------------------------------------------------
-- 资产投放申请 (LOAN) — 9 条
INSERT IGNORE INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('B_LOAN_LIST',   '/api/loans',                  'GET',    '资产投放列表',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '资产投放分页列表'),
  ('B_LOAN_READ',   '/api/loans/*',                'GET',    '资产投放详情',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '资产投放申请详情'),
  ('B_LOAN_CREATE', '/api/loans',                  'POST',   '创建资产投放',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '创建资产投放草稿'),
  ('B_LOAN_UPDATE', '/api/loans/*',                'PUT',    '更新资产投放',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '更新资产投放草稿'),
  ('B_LOAN_SUBMIT', '/api/loans/*/submit',         'POST',   '提交资产投放审批', 0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '提交资产投放审批'),
  ('B_LOAN_DELETE', '/api/loans/*',                'DELETE', '删除资产投放',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '删除资产投放草稿'),
  ('B_LOAN_CANCEL', '/api/loans/*/cancel',         'POST',   '撤回资产投放',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '撤回资产投放申请'),
  ('B_LOAN_EXPORT', '/api/loans/export',           'GET',    '导出资产投放',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '导出资产投放申请(高危)'),
  ('B_LOAN_FORM',   '/api/loans/*/node-form/*',    'GET',    '节点表单配置',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '获取节点表单配置');

-- 中场支持申请-发起侧 (SUPPORT) — 8 条
INSERT IGNORE INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('B_SUP_LIST',    '/api/support-requests',                   'GET',    '中场支持列表',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '中场支持发起侧列表'),
  ('B_SUP_READ',    '/api/support-requests/*',                 'GET',    '中场支持详情',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '中场支持申请详情'),
  ('B_SUP_CREATE',  '/api/support-requests',                   'POST',   '创建中场支持',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '创建中场支持申请'),
  ('B_SUP_SUBMIT',  '/api/support-requests/*/submit',          'POST',   '提交中场支持',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '提交中场支持审批'),
  ('B_SUP_DELETE',  '/api/support-requests/*',                 'DELETE', '删除中场支持',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '删除中场支持草稿'),
  ('B_SUP_CANCEL',  '/api/support-requests/*/cancel',          'POST',   '撤回中场支持',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '撤回中场支持申请'),
  ('B_SUP_EXPORT',  '/api/support-requests/export',            'GET',    '导出中场支持',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '导出中场支持申请(高危)'),
  ('B_SUP_PROD',    '/api/support-requests/available-products','GET',    '可用产品列表',   0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '中场支持可用产品');

-- 中场支持申请-承接侧 (SUPPORT_DEPT) — 4 条
INSERT IGNORE INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('B_SUPD_LIST',   '/api/support-dept/requests',              'GET',    '承接侧列表',     0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '承接侧申请列表'),
  ('B_SUPD_DISP',   '/api/support-dept/requests/*/dispatch',   'POST',   '秘书派单',       0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '秘书派单'),
  ('B_SUPD_XFER',   '/api/support-dept/requests/*/transfer',   'POST',   '秘书转交',       0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '秘书转交(高危)'),
  ('B_SUPD_DONE',   '/api/support-dept/requests/*/complete',   'POST',   '办理完成',       0, 0, 'BRANCH', 'align-2026-04-25', 'align-2026-04-25', '支持人员办理完成');

-- ============================================================================
-- 验证 SQL（脚本结尾用，对账）
-- ============================================================================
SELECT 'rpt tables' AS scope, COUNT(*) AS cnt FROM information_schema.TABLES
  WHERE TABLE_SCHEMA='onepl' AND TABLE_NAME IN ('rpt_saved_query','rpt_snapshot_task','sql_probe_history','rpt_export_task');
-- 期望 4

SELECT 'portal_resource' AS scope, COUNT(*) AS cnt FROM PT_RESOURCE
  WHERE RESOURCE_ID IN ('RES_PORTAL_WORKSPACE','RES_PROD_SUP_AVL','RES_SHORTCUT_PUT');
-- 期望 3（ID 必须 ≤ 20 字符以满足 RESOURCE_ID varchar(20) 约束）

SELECT 'bizapp_resource' AS scope, COUNT(*) AS cnt FROM PT_RESOURCE
  WHERE RESOURCE_ID LIKE 'B_LOAN_%' OR RESOURCE_ID LIKE 'B_SUP_%' OR RESOURCE_ID LIKE 'B_SUPD_%';
-- 期望 21（B_ 前缀已足够区分，无需 ESCAPE）
