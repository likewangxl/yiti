-- ============================================================================
-- 模块：报表分析中心 (report-analytics-center)
-- 描述：动态查询保存方案 + SQL 探查历史 + 快照任务配置（V1 预留）
-- 版本：V1
-- 更新日期：2026-04-10（从 docs/modules/report-analytics-center/05-表结构DDL.md 对齐）
-- ============================================================================
--
-- 重要说明：
-- 1. 本模块是"纯只读支撑域"，只有 3 张自有表：
--    - rpt_saved_query     动态查询保存方案（每用户最多 10 条）
--    - sql_probe_history   SQL 探查历史（审计 + 技术运维复盘，保留 3 个月）
--    - rpt_snapshot_task   快照任务配置（V1 仅建表不启用，V2 扩展）
--
-- 2. 本模块不维护任何业务汇总快照表，所有业务数据通过 *Api 实时查询：
--    - 员工/机构/客户指标值  → performance.MetricApi
--    - KPI 结果               → performance.KpiApi
--    - 客户信息               → customer-marketing.CustomerQueryApi
--    - 审计日志               → governance.AuditApi
--
-- 3. 异步导出任务表 sys_async_task 由 governance 模块统一提供，本模块不重复建表
--
-- 4. 所有表遵循 docs/common-dev-guide.md 的通用字段规范
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. rpt_saved_query — 动态查询保存方案
-- ----------------------------------------------------------------------------
-- 业务约束：
--   - 每用户最多保存 10 条方案，超限时保存接口删除最旧的一条
--   - 对象数 ≤ 100，指标数 ≤ 20（应用层校验，由 sys_config_kv 控制）
--   - subject_ids / metric_codes 为 JSON 数组字符串（不使用 MySQL JSON 类型，避免字符集差异）
--   - version 字段用于乐观锁，防止多标签页并发编辑冲突
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `rpt_saved_query`;
CREATE TABLE `rpt_saved_query` (
  `id`            VARCHAR(32)  NOT NULL COMMENT '方案ID（UUID）',
  `emp_id`        VARCHAR(32)  NOT NULL COMMENT '员工工号',
  `name`          VARCHAR(200) NOT NULL COMMENT '方案名称',
  `dim`           VARCHAR(20)  NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids`   TEXT         NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes`  TEXT         NOT NULL COMMENT '指标编码列表(JSON数组)',
  `version`       INT(11)      DEFAULT 0 COMMENT '乐观锁版本号',
  `created_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`, `created_time`),
  KEY `idx_emp_id_name` (`emp_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';

-- ----------------------------------------------------------------------------
-- 2. sql_probe_history — SQL 探查历史
-- ----------------------------------------------------------------------------
-- 业务约束：
--   - 属于"系统级探查日志"，但物理上放在 report 模块内管理
--   - 每次执行前先 INSERT 一条 status=RUNNING 占位记录（拿到 id），完成时 UPDATE 最终状态
--   - 保留 3 个月（约 90 天），由 sys_job_conf 调度 SqlProbeHistoryCleanJob 定期清理
--   - 配合 audit_log 做安全审计追溯
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sql_probe_history`;
CREATE TABLE `sql_probe_history` (
  `id`                 VARCHAR(32)  NOT NULL COMMENT '历史ID（UUID）',
  `emp_id`             VARCHAR(32)  NOT NULL COMMENT '执行人工号',
  `sql_text`           TEXT         NOT NULL COMMENT 'SQL语句',
  `remark`             VARCHAR(500) DEFAULT NULL COMMENT '备注(reason)',
  `row_count`          INT(11)      DEFAULT NULL COMMENT '影响行数',
  `execution_time_ms`  INT(11)      DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `status`             VARCHAR(20)  DEFAULT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED/TIMEOUT',
  `error_msg`          TEXT         DEFAULT NULL COMMENT '错误信息',
  `created_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_emp_time` (`emp_id`, `created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SQL探查历史';

-- ----------------------------------------------------------------------------
-- 3. rpt_snapshot_task — 快照任务配置（V1 预留）
-- ----------------------------------------------------------------------------
-- V1 状态：
--   - 仅建表，不写入任何数据，不启动任何任务
--   - V2 扩展：当日活跃 > 1000 或仪表盘并发 > 500 QPS 时引入本地快照加速
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `rpt_snapshot_task`;
CREATE TABLE `rpt_snapshot_task` (
  `id`             VARCHAR(32)  NOT NULL COMMENT '任务ID',
  `task_name`      VARCHAR(200) NOT NULL COMMENT '任务名称',
  `snapshot_type`  VARCHAR(50)  NOT NULL COMMENT '快照类型（DAILY/MONTHLY，V2扩展）',
  `cron_expr`      VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
  `status`         VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `last_run_time`  DATETIME     DEFAULT NULL COMMENT '最近执行时间',
  `next_run_time`  DATETIME     DEFAULT NULL COMMENT '下次执行时间',
  `created_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_next_run_time` (`next_run_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='快照任务配置（V1预留）';

-- ============================================================================
-- 建表执行顺序：
-- 1) 先建 rpt_saved_query（无外部依赖）
-- 2) 再建 sql_probe_history（无外部依赖）
-- 3) 最后建 rpt_snapshot_task（V1 预留，不启用）
--
-- V1 不需要种子数据，相关配置（SQL 探查白名单等）在 docs/schema/seed-v1.sql 第 10 节
-- ============================================================================
