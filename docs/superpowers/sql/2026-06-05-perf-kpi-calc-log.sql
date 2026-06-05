-- ============================================================================
-- PERF_KPI_CALC_LOG —— KPI 方案级计算记录表
-- ----------------------------------------------------------------------------
-- 背景：KPI 分值计算任务（PERF_METRIC_CALC_TASK 是"整任务"流水）下，每处理完 /
--      异常结束一个 KPI 方案，落一条方案级记录，便于按方案查看计算结果与异常。
-- 字段：数据日期、KPI方案编码、触发方式(AUTO自动/MANUAL手动)、触发人(工号=PT_USER.username，
--      自动触发为空)、开始/结束时间、计算结果(SUCCESS/FAILED)、计分对象数、跳过项数、异常信息、
--      关联整任务 task_id。
-- 双库：dev=yiti / prod=onepl 均需执行。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `PERF_KPI_CALC_LOG` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `data_date` date NOT NULL COMMENT '数据日期',
  `scheme_code` varchar(64) NOT NULL COMMENT 'KPI方案编码',
  `trigger_type` varchar(10) NOT NULL COMMENT '触发方式 AUTO自动 / MANUAL手动',
  `trigger_by` varchar(64) DEFAULT NULL COMMENT '触发人工号(PT_USER.username)，自动触发为空',
  `start_time` datetime DEFAULT NULL COMMENT '该方案计算开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '该方案计算结束时间',
  `result` varchar(20) DEFAULT NULL COMMENT '计算结果 SUCCESS / FAILED',
  `scored_count` int DEFAULT 0 COMMENT '计分对象数',
  `skipped_count` int DEFAULT 0 COMMENT '跳过指标项数',
  `error_msg` text COMMENT '异常信息',
  `task_id` varchar(32) DEFAULT NULL COMMENT '关联 PERF_METRIC_CALC_TASK.id',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_date_scheme` (`data_date`, `scheme_code`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='KPI方案级计算记录';
