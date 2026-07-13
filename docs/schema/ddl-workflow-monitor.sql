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
