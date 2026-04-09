-- ============================================
-- 模块：流程中心 (workflow-center)
-- 描述：业务流程映射、节点候选人配置、节点表单配置、超时规则
-- 说明：Flowable ACT_* 表由引擎自动创建，不在此脚本中
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 业务流程映射表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_process_map` (
  `id` varchar(32) NOT NULL COMMENT '映射ID',
  `business_key` varchar(100) NOT NULL COMMENT '业务键（格式：BIZ_TYPE:{id}）',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型',
  `biz_id` varchar(100) NOT NULL COMMENT '业务ID',
  `process_definition_key` varchar(100) NOT NULL COMMENT '流程定义KEY',
  `process_instance_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
  `start_user` varchar(32) NOT NULL COMMENT '发起人工号',
  `current_assignee` varchar(32) DEFAULT NULL COMMENT '当前处理人工号',
  `candidate_groups` text COMMENT '候选组列表（JSON数组）',
  `process_status` varchar(50) DEFAULT 'RUNNING' COMMENT '流程状态：RUNNING-运行中, COMPLETED-已完成, CANCELLED-已取消',
  `title` varchar(200) DEFAULT NULL COMMENT '流程标题',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_key` (`business_key`),
  UNIQUE KEY `uk_process_instance` (`process_instance_id`),
  KEY `idx_biz_type_id` (`biz_type`,`biz_id`),
  KEY `idx_start_user` (`start_user`),
  KEY `idx_current_assignee` (`current_assignee`),
  KEY `idx_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务流程映射表';

-- -------------------------------------------
-- 2. 流程节点候选人配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_node_candidate_conf` (
  `id` varchar(32) NOT NULL COMMENT '配置ID',
  `process_definition_key` varchar(100) NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) NOT NULL COMMENT '节点KEY',
  `candidate_type` varchar(50) NOT NULL COMMENT '候选类型：ROLE-角色, ORG-机构, USER-指定用户',
  `candidate_value` text COMMENT '候选值（JSON）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pd_node_type` (`process_definition_key`, `node_key`, `candidate_type`),
  KEY `idx_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点候选人配置表';

-- -------------------------------------------
-- 3. 流程节点表单配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_node_form_conf` (
  `id` varchar(32) NOT NULL COMMENT '配置ID',
  `process_definition_key` varchar(100) NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) NOT NULL COMMENT '节点KEY',
  `form_fields` text COMMENT '表单字段配置（JSON）',
  `editable_fields` text COMMENT '可编辑字段列表（JSON数组）',
  `required_fields` text COMMENT '必填字段列表（JSON数组）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点表单配置表';

-- -------------------------------------------
-- 4. 流程超时规则表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_timeout_rule` (
  `id` varchar(32) NOT NULL COMMENT '规则ID',
  `process_definition_key` varchar(100) NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) NOT NULL COMMENT '节点KEY',
  `timeout_hours` int(11) NOT NULL COMMENT '超时小时数',
  `warning_hours` int(11) DEFAULT NULL COMMENT '预警小时数',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程超时规则表';
