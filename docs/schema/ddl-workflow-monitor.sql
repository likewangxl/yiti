-- ============================================
-- 模块：流程中心 (workflow-center)
-- 描述：审批流监控 + 转交待认领 —— 参与机构快照表
-- 说明：Flowable ACT_* 表由引擎自动创建，不在此脚本中
-- 版本：V1
-- 创建日期：2026-07-13
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 审批流参与机构快照表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `WF_PROCESS_ORG` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `process_instance_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
  `org_code` varchar(32) NOT NULL COMMENT '参与人主机构编码',
  `source` varchar(16) NOT NULL COMMENT 'START/ASSIGN/CLAIM/APPROVE/TRANSFER/BACKFILL',
  `first_seen_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pi_org` (`process_instance_id`,`org_code`),
  KEY `idx_org_pi` (`org_code`,`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批流参与机构快照';

-- -------------------------------------------
-- 2. 任务转交待认领生命周期表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `WF_TASK_TRANSFER` (
  `id` varchar(32) NOT NULL,
  `process_instance_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `business_key` varchar(100) DEFAULT NULL,
  `biz_type` varchar(50) DEFAULT NULL,
  `node_key` varchar(100) DEFAULT NULL,
  `node_name` varchar(200) DEFAULT NULL,
  `from_emp_id` varchar(32) NOT NULL,
  `initiator_emp_id` varchar(32) NOT NULL,
  `to_emp_id` varchar(32) NOT NULL,
  `org_code` varchar(32) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_ACCEPT',
  `transfer_reason` varchar(500) NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `initiated_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `decided_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_status` (`task_id`,`status`),
  KEY `idx_to_status` (`to_emp_id`,`status`),
  KEY `idx_from` (`from_emp_id`),
  KEY `idx_pi` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务转交待认领生命周期';
