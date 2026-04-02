-- ============================================
-- 模块：业务申请中心 (business-application-center)
-- 描述：中场支持申请、资产投放申请
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 中场支持申请
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `support_request` (
  `id` varchar(32) NOT NULL COMMENT '申请ID',
  `request_no` varchar(100) DEFAULT NULL COMMENT '申请编号',
  `submit_group_id` varchar(64) DEFAULT NULL COMMENT '同批提交分组ID(可选)',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `source_touch_task_id` varchar(32) DEFAULT NULL COMMENT '来源触达任务ID',
  `product_id` varchar(64) DEFAULT NULL COMMENT '产品ID',
  `support_dept_id` varchar(50) DEFAULT NULL COMMENT '承接部门ORG_CODE',
  `other_demand` text COMMENT '其他需求',
  `dispatch_emp_id` varchar(32) DEFAULT NULL COMMENT '派单人(部门秘书)',
  `dispatch_time` datetime DEFAULT NULL COMMENT '派单时间',
  `assigned_emp_id` varchar(32) DEFAULT NULL COMMENT '承接办理人',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键(SUPPORT:{id})',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记(0-否,1-是)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_support_dept` (`support_dept_id`),
  KEY `idx_assigned_emp` (`assigned_emp_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='中场支持申请';

-- -------------------------------------------
-- 2. 资产投放申请
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `loan_apply` (
  `id` varchar(32) NOT NULL COMMENT '申请ID',
  `apply_no` varchar(100) DEFAULT NULL COMMENT '申请编号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `source_touch_task_id` varchar(32) DEFAULT NULL COMMENT '来源触达任务ID',
  `project_type` varchar(32) DEFAULT NULL COMMENT '项目类型(字典)',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '业务类型(字典)',
  `guarantee_type` varchar(32) DEFAULT NULL COMMENT '担保方式(字典)',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '敞口金额',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键(LOAN:{id})',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记(0-否,1-是)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资产投放申请';
