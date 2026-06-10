-- ============================================
-- 模块：客户营销中心 (customer-marketing-center)
-- 描述：客户标签、线索、导入批次、客户主档、认领、触达任务与日志
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 客户标签表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_TAG` (
  `id` varchar(32) NOT NULL COMMENT '标签ID',
  `tag_name` varchar(100) NOT NULL COMMENT '标签名称',
  `tag_code` varchar(100) NOT NULL COMMENT '标签编码',
  `tag_category` varchar(50) DEFAULT NULL COMMENT '标签分类',
  `tag_priority` int(11) NOT NULL DEFAULT 0 COMMENT '标签优先级(数字越大越靠前)',
  `description` varchar(500) DEFAULT NULL COMMENT '标签描述',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-否, 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_code` (`tag_code`),
  UNIQUE KEY `uk_tag_name` (`tag_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户标签表';

-- -------------------------------------------
-- 2. 客户-标签关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_TAG_REL` (
  `id` varchar(32) NOT NULL COMMENT '关联ID',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `tag_id` varchar(32) NOT NULL COMMENT '标签ID',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_tag` (`cust_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户-标签关联表';

-- -------------------------------------------
-- 3. 客户线索表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_LEAD` (
  `id` varchar(32) NOT NULL COMMENT '线索ID',
  `lead_no` varchar(100) NOT NULL COMMENT '线索编号',
  `lead_op` varchar(20) NOT NULL DEFAULT 'CREATE' COMMENT '线索操作：CREATE/UPDATE/DELETE',
  `source_cust_id` varchar(32) DEFAULT NULL COMMENT '关联客户ID(UPDATE/DELETE时必填)',
  `prev_lead_id` varchar(32) DEFAULT NULL COMMENT '上一版本线索ID(UPDATE/DELETE时必填)',
  `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '版本号(从1开始)',
  `is_latest` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否最新版本(1-是,0-否)',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码(纳税人识别号)',
  `tag_ids` text COMMENT '标签ID列表(JSON数组)',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) DEFAULT NULL COMMENT '所属行业',
  `group_type` varchar(50) DEFAULT NULL COMMENT '所属集团类型(字典)',
  `customer_type` varchar(50) DEFAULT NULL COMMENT '客户类型(字典)',
  `is_keystone` tinyint(1) DEFAULT NULL COMMENT '是否基石客户(1-是,0-否)',
  `enterprise_type` varchar(50) DEFAULT NULL COMMENT '企业类型(字典)',
  `group_name` varchar(200) DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened` tinyint(1) DEFAULT NULL COMMENT '是否开户(1-是,0-否)',
  `customer_desc` text COMMENT '客户说明',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额',
  `lead_source` varchar(50) DEFAULT NULL COMMENT '线索来源',
  `lead_status` varchar(50) DEFAULT 'DRAFT' COMMENT '线索状态：DRAFT-草稿, SUBMITTED-已提交, IN_APPROVAL-审批中, APPROVED-已通过, REJECTED-已驳回',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构代码',
  `assigned_to` varchar(50) DEFAULT NULL COMMENT '分配用户',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键（LEAD:{id}）',
  `import_batch_id` varchar(32) DEFAULT NULL COMMENT '导入批次ID(批量导入)',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `remark` text COMMENT '备注',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_status` (`lead_status`),
  KEY `idx_import_batch` (`import_batch_id`),
  KEY `idx_cust_name` (`cust_name`),
  KEY `idx_unified_credit_code` (`unified_credit_code`),
  KEY `idx_source_cust` (`source_cust_id`),
  KEY `idx_lead_op_status` (`lead_op`, `lead_status`),
  KEY `idx_is_latest` (`is_latest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户线索表';

-- -------------------------------------------
-- 4. 线索导入批次表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `LEAD_IMPORT_BATCH` (
  `id` varchar(32) NOT NULL COMMENT '批次ID',
  `batch_no` varchar(64) NOT NULL COMMENT '批次号(展示用)',
  `source_file_name` varchar(255) DEFAULT NULL COMMENT '源文件名',
  `file_md5` varchar(64) DEFAULT NULL COMMENT '文件MD5',
  `status` varchar(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/PENDING_APPROVAL/APPROVED/REJECTED',
  `total_row_count` int(11) NOT NULL DEFAULT 0 COMMENT '总行数',
  `error_row_count` int(11) NOT NULL DEFAULT 0 COMMENT '错误行数',
  `error_summary` varchar(512) DEFAULT NULL COMMENT '错误摘要',
  `error_file_object_id` varchar(32) DEFAULT NULL COMMENT '错误明细文件ID(可选)',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键(LEAD:IMP_{id})',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索导入批次表';

-- -------------------------------------------
-- 5. 客户主档表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_MASTER` (
  `id` varchar(32) NOT NULL COMMENT '客户ID',
  `cust_no` varchar(100) NOT NULL COMMENT '客户编号',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码(纳税人识别号)',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) DEFAULT NULL COMMENT '所属行业',
  `group_type` varchar(50) DEFAULT NULL COMMENT '所属集团类型(字典)',
  `customer_type` varchar(50) DEFAULT NULL COMMENT '客户类型(字典)',
  `is_keystone` tinyint(1) DEFAULT NULL COMMENT '是否基石客户(1-是,0-否)',
  `enterprise_type` varchar(50) DEFAULT NULL COMMENT '企业类型(字典)',
  `group_name` varchar(200) DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened` tinyint(1) DEFAULT NULL COMMENT '是否开户(1-是,0-否)',
  `customer_desc` text COMMENT '客户说明',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '来源机构代码（不承载可见性）',
  `lead_id` varchar(32) DEFAULT NULL COMMENT '来源线索ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, INACTIVE-停用',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记(0-否,1-是)',
  `statis_dt` varchar(10) DEFAULT NULL COMMENT '统计日期(yyyy-MM-dd)，客户信息同步来源日期',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_no` (`cust_no`),
  UNIQUE KEY `uk_cust_name` (`cust_name`),
  KEY `idx_lead_id` (`lead_id`),
  KEY `idx_unified_credit_code` (`unified_credit_code`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户主档表';

-- -------------------------------------------
-- 6. 客户认领关系表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_CLAIM` (
  `id` varchar(32) NOT NULL COMMENT '认领ID',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `org_id` varchar(50) NOT NULL COMMENT '认领机构代码',
  `claimed_by` varchar(32) NOT NULL COMMENT '认领人工号',
  `maintainer_emp_id` varchar(32) DEFAULT NULL COMMENT '维护人工号',
  `claim_status` varchar(50) DEFAULT 'CLAIMED' COMMENT '认领状态：CLAIMED-已认领, CANCELLED-已取消',
  `claim_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '认领时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_org` (`cust_id`, `org_id`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_claimed_by` (`claimed_by`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_status` (`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户认领关系表';

-- -------------------------------------------
-- 7. 触达任务表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `TOUCH_TASK` (
  `id` varchar(32) NOT NULL COMMENT '任务ID',
  `task_no` varchar(100) NOT NULL COMMENT '任务编号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `org_id` varchar(50) NOT NULL COMMENT '归属机构代码',
  `assignee_emp_id` varchar(32) NOT NULL COMMENT '执行人工号',
  `task_type` varchar(50) DEFAULT NULL COMMENT '任务类型：FIRST_TOUCH-首次触达, FOLLOW_UP-跟进',
  `task_status` varchar(50) DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待办, IN_PROGRESS-进行中, SUCCESS-成功, CANCELLED-取消',
  `plan_finish_time` datetime DEFAULT NULL COMMENT '计划完成时间(SLA)',
  `warning_time` datetime DEFAULT NULL COMMENT '预警时间(SLA)',
  `sla_status` varchar(20) DEFAULT NULL COMMENT 'SLA状态：GREEN/YELLOW/RED',
  `sla_warning` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'SLA预警标记：0-否, 1-是（sla_status 为 YELLOW/RED 时置 1）',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键（TOUCH:{id}）',
  `success_time` datetime DEFAULT NULL COMMENT '成功时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_assignee` (`assignee_emp_id`),
  KEY `idx_status` (`task_status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_assignee_status` (`assignee_emp_id`, `task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触达任务表';

-- -------------------------------------------
-- 8. 触达日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `TOUCH_LOG` (
  `id` varchar(32) NOT NULL COMMENT '日志ID',
  `touch_task_id` varchar(32) NOT NULL COMMENT '触达任务ID',
  `log_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间(业务时间)',
  `client_uuid` varchar(64) DEFAULT NULL COMMENT '客户端幂等UUID(移动端重试去重)',
  `log_content` text COMMENT '日志内容',
  `photo_urls` text COMMENT '照片URL列表（JSON数组）',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '归属机构代码',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人工号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_client_uuid` (`touch_task_id`, `client_uuid`),
  KEY `idx_task_id` (`touch_task_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_task_log_time` (`touch_task_id`, `log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触达日志表';
