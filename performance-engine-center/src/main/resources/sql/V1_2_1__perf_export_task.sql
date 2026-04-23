-- =====================================================================
-- performance-engine-center V1.2 Phase Q6.1 异步导出任务表
-- Version: V1_2_1
-- Date: 2026-04-23
-- Task: Q6.1
--
-- 目的：为 V1.2 异步导出框架创建 perf_export_task 表。
--
-- 涵盖：
--   - export_type: KPI / METRIC / ALLOC / DETAIL 四种导出策略
--   - status 机：PENDING → RUNNING → SUCCESS / FAILED
--   - file_key 保存 MinIO object key（成功时回填）
--   - expire_at 文件过期时间（消费方判定文件是否可下载）
--   - params_json 保存导出参数 JSON（策略按需反序列化）
--   - operator_id 归属字段，下载时用于 EXPORT_TASK_OWNER_MISMATCH 校验
-- =====================================================================

CREATE TABLE IF NOT EXISTS `perf_export_task` (
  `id` varchar(32) NOT NULL COMMENT '导出任务ID',
  `export_type` varchar(32) NOT NULL COMMENT '类型：KPI/METRIC/ALLOC/DETAIL',
  `params_json` text DEFAULT NULL COMMENT '导出参数 JSON',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `file_key` varchar(200) DEFAULT NULL COMMENT 'MinIO object key',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `row_count` int DEFAULT NULL COMMENT '导出行数',
  `expire_at` datetime DEFAULT NULL COMMENT '文件过期时间',
  `operator_id` varchar(32) NOT NULL COMMENT '操作人员工号',
  `error_msg` text DEFAULT NULL COMMENT '失败原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_export_type` (`export_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效异步导出任务';
