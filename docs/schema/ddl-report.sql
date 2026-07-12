-- ============================================================================
-- 模块：报表分析中心 (report-analytics-center)
-- 描述：动态查询保存方案 + SQL 探查历史 + 快照任务配置 + 异步导出任务
-- 版本：V1
-- 更新日期：2026-04-26（P0 修复：补 rpt_export_task 表，注释与代码对齐）
-- ============================================================================
--
-- 重要说明：
-- 1. 本模块有 4 张自有表：
--    - rpt_saved_query     动态查询保存方案（每用户最多 10 条）
--    - sql_probe_history   SQL 探查历史（审计 + 技术运维复盘，保留 3 个月）
--    - rpt_snapshot_task   快照任务配置（V1 仅建表不启用，V2 扩展）
--    - rpt_export_task     异步导出任务（与 perf_export_task 同构，模块独立）
--
-- 2. 本模块不维护任何业务汇总快照表，所有业务数据通过 *Api 实时查询：
--    - 员工/机构/客户指标值  → performance.MetricApi
--    - KPI 结果               → performance.KpiApi
--    - 客户信息               → customer-marketing.CustomerQueryApi
--    - 审计日志               → governance.AuditApi
--    - 文件上传               → governance.FileApi（导出文件走 MinIO 上传）
--
-- 3. 所有表遵循 docs/common-dev-guide.md 的通用字段规范
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
DROP TABLE IF EXISTS `RPT_SAVED_QUERY`;
CREATE TABLE `RPT_SAVED_QUERY` (
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
DROP TABLE IF EXISTS `SQL_PROBE_HISTORY`;
CREATE TABLE `SQL_PROBE_HISTORY` (
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
DROP TABLE IF EXISTS `RPT_SNAPSHOT_TASK`;
CREATE TABLE `RPT_SNAPSHOT_TASK` (
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

-- ----------------------------------------------------------------------------
-- 4. rpt_export_task — 异步导出任务
-- ----------------------------------------------------------------------------
-- 说明：
--   - 与 performance-engine-center perf_export_task 同构，模块独立
--   - V1.0 同步执行模型（createTask 内串联 strategy.execute）
--   - file_key 存储 governance.file_object.id（非 MinIO object key）
--   - 下载链路：RptExportFacade.getDownloadUrl → governance.FileApi.getDownloadUrl → presigned URL
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `RPT_EXPORT_TASK`;
CREATE TABLE `RPT_EXPORT_TASK` (
  `id`             varchar(32) NOT NULL COMMENT '导出任务ID',
  `export_type`    varchar(32) NOT NULL COMMENT '类型：DYNAMIC_QUERY/TOUCH_SUMMARY/PERF_SUMMARY/CUSTPOOL_SUMMARY',
  `params_json`    text        DEFAULT NULL COMMENT '导出参数 JSON',
  `status`         varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  `file_key`       varchar(200) DEFAULT NULL COMMENT 'governance.file_object.id',
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

-- ============================================================================
-- 建表执行顺序：
-- 1) 先建 rpt_saved_query（无外部依赖）
-- 2) 再建 sql_probe_history（无外部依赖）
-- 3) 再建 rpt_snapshot_task（V1 预留，不启用）
-- 4) 最后建 rpt_export_task（无外部依赖）
--
-- V1 不需要种子数据，相关配置（SQL 探查白名单等）在 docs/schema/seed-v1.sql 第 10 节
-- ============================================================================

-- ============================================================================
-- screen 子域：经营管理大屏（三级视角 + 全配置化），2026-07-12 随基线追加
-- 描述：大屏数据源 / 大屏定义 / 大屏区块（布局+组件+绑定+钻取）/ 支行地图点位，共 4 张自有表
-- 来源：docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql（已在 yiti + onepl_test_bootstrap
--       手工执行；此处并入基线仅为留档，不重复执行，去除原脚本的 DROP TABLE IF EXISTS 行）
-- 设计文档：docs/superpowers/specs/2026-07-12-screen-dashboard-design.md §4
-- ============================================================================

CREATE TABLE `RPT_SCREEN_DATASOURCE` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ds_code`         VARCHAR(64)  NOT NULL COMMENT '数据源编码（应用层保证 deleted=0 内唯一）',
  `ds_name`         VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `ds_type`         VARCHAR(20)  NOT NULL COMMENT '能力标签：TIMESERIES 时序/SINGLE 单值',
  `source_kind`     VARCHAR(20)  NOT NULL COMMENT '来源：WIDE_TABLE/KPI_RESULT/CUSTOM_SQL',
  `config_json`     TEXT         NOT NULL COMMENT '类型化配置 JSON（三形态见 spec §5）',
  `time_param_json` VARCHAR(500) DEFAULT NULL COMMENT '允许的预设周期 JSON 数组，如 ["LATEST","LAST_10D"]',
  `status`          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by`      VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_ds_code` (`ds_code`),
  KEY `idx_scr_ds_type` (`ds_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏数据源定义';

CREATE TABLE `RPT_SCREEN` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_code`  VARCHAR(64)  NOT NULL COMMENT '大屏编码（应用层保证 deleted=0 内唯一）',
  `screen_name`  VARCHAR(100) NOT NULL COMMENT '大屏名称',
  `view_level`   VARCHAR(20)  NOT NULL COMMENT '视角：PROVINCE/BRANCH/PERSON',
  `theme_json`   VARCHAR(1000) DEFAULT NULL COMMENT '主题变量覆盖 JSON（一期留空）',
  `status`       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_by`   VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_code` (`screen_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏定义';

CREATE TABLE `RPT_SCREEN_BLOCK` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`      BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `region`         VARCHAR(10) NOT NULL COMMENT '区域：LEFT/MAIN/RIGHT',
  `row_no`         INT         NOT NULL DEFAULT 1 COMMENT '区域内行号（从 1 起）',
  `col_no`         INT         NOT NULL DEFAULT 1 COMMENT '行内列号（从 1 起）',
  `width_pct`      INT         NOT NULL DEFAULT 100 COMMENT '行内宽度百分比 1~100',
  `height_pct`     INT         NOT NULL DEFAULT 100 COMMENT '区域内行高百分比 1~100（同行取首块值）',
  `component_type` VARCHAR(20) NOT NULL COMMENT 'METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS',
  `bind_json`      TEXT        NOT NULL COMMENT '数据绑定 JSON',
  `style_json`     TEXT        DEFAULT NULL COMMENT '样式 JSON',
  `drill_json`     TEXT        DEFAULT NULL COMMENT '钻取/跳转 JSON',
  `created_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_block_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏区块（布局+组件+绑定+钻取）';

CREATE TABLE `RPT_SCREEN_MAP_POINT` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_code`           VARCHAR(32)   NOT NULL COMMENT '支行机构号',
  `org_name`           VARCHAR(100)  NOT NULL COMMENT '支行名称',
  `lng`                DECIMAL(10,6) NOT NULL COMMENT '经度',
  `lat`                DECIMAL(10,6) NOT NULL COMMENT '纬度',
  `target_screen_code` VARCHAR(64)   DEFAULT 'SCR_BRANCH' COMMENT '点击跳转目标屏编码',
  `status`             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scr_map_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏地图支行点位';
