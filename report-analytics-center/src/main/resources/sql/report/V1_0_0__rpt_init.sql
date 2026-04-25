-- =====================================================================
-- report-analytics-center V1.0 基线 DDL
-- Version: V1_0_0
-- Date: 2026-04-25
-- Task: M0.2.1
--
-- 来源：
--   1. docs/modules/report-analytics-center/05-表结构DDL.md（前 3 张自有表）
--   2. performance-engine-center V1_2_1__perf_export_task.sql 同构（rpt_export_task）
--
-- 4 张表：
--   - rpt_saved_query     动态查询保存方案（每用户最多 10 条）
--   - sql_probe_history   SQL 探查历史（审计 + 技术运维复盘，保留 3 个月）
--   - rpt_snapshot_task   快照任务配置（V1 仅建表不启用，V2 扩展）
--   - rpt_export_task     报表异步导出任务（V1 M5 启用，与 perf_export_task 同构）
-- =====================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. rpt_saved_query — 动态查询保存方案
-- ----------------------------------------------------------------------------
-- 业务约束：
--   - 每用户最多保存 10 条方案，超限时保存接口删除最旧的一条
--   - 对象数 ≤ 100，指标数 ≤ 20（应用层校验，由 sys_config_kv 控制）
--   - subject_ids / metric_codes 为 JSON 数组字符串
--   - version 字段用于乐观锁，防止多标签页并发编辑冲突
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

-- ----------------------------------------------------------------------------
-- 2. sql_probe_history — SQL 探查历史
-- ----------------------------------------------------------------------------
-- 业务约束：
--   - 属于"系统级探查日志"，但物理上放在 report 模块内管理
--   - 每次执行前先 INSERT status=RUNNING 占位（拿 id），完成时 UPDATE 终态
--   - 保留 3 个月（约 90 天），由 sys_job_conf 调度清理任务
--   - 配合 audit_log 做安全审计追溯
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 3. rpt_snapshot_task — 快照任务配置（V1 预留）
-- ----------------------------------------------------------------------------
-- V1 状态：
--   - 仅建表，不写入任何数据，不启动任何任务
--   - V2 扩展：当日活跃 > 1000 或仪表盘并发 > 500 QPS 时引入本地快照加速
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 4. rpt_export_task — 报表异步导出任务
-- ----------------------------------------------------------------------------
-- 业务约束（与 performance-engine-center.perf_export_task 同构）：
--   - export_type: 报表导出策略（V1 M5 落地：DYNAMIC_QUERY / FIXED_REPORT / SQL_PROBE 等）
--   - status 机：PENDING → RUNNING → SUCCESS / FAILED
--   - file_key 保存 MinIO object key（成功时回填）
--   - expire_at 文件过期时间（消费方判定文件是否可下载）
--   - params_json 保存导出参数 JSON（策略按需反序列化）
--   - operator_id 归属字段，下载时用于 EXPORT_TASK_OWNER_MISMATCH 校验
-- ----------------------------------------------------------------------------
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

-- =====================================================================
-- 建表执行顺序：
-- 1) rpt_saved_query     无外部依赖
-- 2) sql_probe_history   无外部依赖
-- 3) rpt_snapshot_task   V1 预留，不启用
-- 4) rpt_export_task     V1 M5 启用，与 perf_export_task 同构
-- =====================================================================
