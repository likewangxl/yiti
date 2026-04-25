-- =====================================================================
-- report-analytics-center V1.0 异步导出任务表（M5 启用）
-- Version: V1_0_5
-- Date: 2026-04-25
-- Task: M5.1.1
--
-- 来源：与 performance-engine-center V1_2_1__perf_export_task.sql 同构
-- 路径：仅本模块独占，不复用 perf_export_task（避免跨模块表共享）
--
-- 脚本职责说明：rpt_export_task 表已在 V1_0_0__rpt_init.sql §4 创建，
-- V1_0_5 仅作为 idempotent 兜底（CREATE TABLE IF NOT EXISTS 不会重复创建），
-- 同时让 M5 阶段在 flyway_schema_history_rpt 留一行 1.0.5 success=1 记录，
-- 与 plan §M5.1 保持脚本节奏一致。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `rpt_export_task` (
  `id`             varchar(32) NOT NULL COMMENT '导出任务ID',
  `export_type`    varchar(32) NOT NULL COMMENT '类型：DYNAMIC_QUERY/TOUCH_SUMMARY/PERF_SUMMARY/CUSTPOOL_SUMMARY',
  `params_json`    text        DEFAULT NULL COMMENT '导出参数 JSON',
  `status`         varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
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
