-- ============================================
-- 模块：客户营销中心 (customer-marketing-center)
-- 描述：客户标签、线索录入与审批、客户主档与主办关系、认领、触达任务与日志
-- 版本：V2 Phase 3（标签审核、跨机构营销、客户转交）
-- 创建日期：2026-03-25
-- 更新日期：2026-08-11
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 客户标签表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_TAG` (
  `id` varchar(32) NOT NULL COMMENT '标签ID',
  `tag_name` varchar(100) NOT NULL COMMENT '标签名称',
  `tag_category` varchar(50) DEFAULT NULL COMMENT '标签分类',
  `tag_priority` int(11) NOT NULL DEFAULT 0 COMMENT '标签优先级(数字越大越靠前)',
  `description` varchar(500) DEFAULT NULL COMMENT '标签描述',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `tag_type` varchar(30) DEFAULT NULL COMMENT '标签类型：PROJECT/CERTIFICATION',
  `approval_status` varchar(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
  `expires_at` date DEFAULT NULL COMMENT '失效日期，空为长期有效',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '创建机构代码',
  `reviewed_by` varchar(32) DEFAULT NULL COMMENT '审核人工号',
  `reviewed_time` datetime DEFAULT NULL COMMENT '审核时间',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '退回原因',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-否, 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`),
  KEY `idx_status` (`status`),
  KEY `idx_tag_approval` (`approval_status`, `created_time`)
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
  `active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '关系状态：1-当前有效/0-历史失效',
  `effective_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `expired_time` datetime DEFAULT NULL COMMENT '失效时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '最近更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_tag` (`cust_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户-标签关联表';

-- -------------------------------------------
-- 2.1 跨机构营销条件规则表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CROSS_ORG_MARKETING_RULE` (
  `id` varchar(32) NOT NULL COMMENT '规则ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `data_source` varchar(64) NOT NULL COMMENT '校验数据源',
  `failure_message` varchar(256) NOT NULL COMMENT '失败提示',
  `extension_params` varchar(1000) DEFAULT NULL COMMENT '扩展参数JSON',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `updated_by` varchar(32) DEFAULT NULL,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cross_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨机构客户营销校验规则';

-- -------------------------------------------
-- 2.2 跨机构营销申请表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CROSS_ORG_MARKETING_APPLY` (
  `id` varchar(32) NOT NULL COMMENT '申请ID',
  `apply_no` varchar(64) NOT NULL COMMENT '申请编号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `cust_no` varchar(100) DEFAULT NULL COMMENT '客户号快照',
  `applicant_emp_id` varchar(32) NOT NULL COMMENT '申请人工号',
  `applicant_org_id` varchar(50) NOT NULL COMMENT '申请机构',
  `main_manager_id` varchar(32) DEFAULT NULL COMMENT '原主办客户经理快照',
  `main_org_id` varchar(50) DEFAULT NULL COMMENT '原主办机构快照',
  `applicant_not_main_check` tinyint(1) NOT NULL,
  `main_org_different_check` tinyint(1) NOT NULL,
  `applicant_no_performance_check` tinyint(1) NOT NULL,
  `applicant_org_no_performance_check` tinyint(1) NOT NULL,
  `check_snapshot_time` datetime NOT NULL,
  `apply_reason` varchar(500) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `generated_touch_task_id` varchar(32) DEFAULT NULL,
  `business_key` varchar(100) NOT NULL,
  `process_instance_id` varchar(64) DEFAULT NULL,
  `reviewed_by` varchar(32) DEFAULT NULL,
  `reviewed_time` datetime DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `created_by` varchar(32) NOT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` varchar(32) DEFAULT NULL,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cross_apply_no` (`apply_no`),
  UNIQUE KEY `uk_cross_business_key` (`business_key`),
  KEY `idx_cross_applicant_status` (`applicant_emp_id`, `status`, `created_time`),
  KEY `idx_cross_customer_status` (`cust_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨机构客户营销申请';

-- -------------------------------------------
-- 3. 客户线索表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_LEAD` (
  `id` varchar(32) NOT NULL COMMENT '线索ID',
  `lead_no` varchar(100) NOT NULL COMMENT '线索编号',
  `lead_op` varchar(20) NOT NULL DEFAULT 'CREATE' COMMENT '线索操作：CREATE/UPDATE/DELETE',
  `lead_type` varchar(30) NOT NULL DEFAULT 'NEW_ACCOUNT' COMMENT '线索类型：NEW_ACCOUNT-新客户开户/EXISTING_MARKETING-存量客户营销',
  `source_cust_id` varchar(32) DEFAULT NULL COMMENT '关联客户ID(UPDATE/DELETE时必填)',
  `prev_lead_id` varchar(32) DEFAULT NULL COMMENT '上一版本线索ID(UPDATE/DELETE时必填)',
  `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '版本号(从1开始)',
  `is_latest` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否最新版本(1-是,0-否)',
  `cust_no` varchar(100) DEFAULT NULL COMMENT 'CCRM客户号；新客户未开户时为空',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) NOT NULL COMMENT '统一社会信用代码(18位，V2客户幂等键)',
  `tag_ids` text COMMENT '兼容字段：标签ID列表(JSON数组)，V2以CUST_LEAD_TAG_REL为准',
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
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额(元)',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额(元)',
  `lead_source` varchar(50) DEFAULT NULL COMMENT '线索来源',
  `distribution_mode` varchar(20) NOT NULL DEFAULT 'PUBLIC' COMMENT '分配方式：PUBLIC-全行公开/SCOPE-指定范围/OWNER-主办专属',
  `main_manager_id` varchar(32) DEFAULT NULL COMMENT '主办专属客户经理工号(仅OWNER方式)',
  `main_manager_org_id` varchar(50) DEFAULT NULL COMMENT '主办客户经理机构快照',
  `lead_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '线索状态：DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构代码',
  `assigned_to` varchar(50) DEFAULT NULL COMMENT '兼容字段：原单一分配用户，V2以CUST_LEAD_MANAGER_SCOPE为准',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `submitted_by` varchar(32) DEFAULT NULL COMMENT '提交审批人工号',
  `submitted_time` datetime DEFAULT NULL COMMENT '提交审批时间',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键（LEAD:{id}）',
  `import_batch_id` varchar(32) DEFAULT NULL COMMENT '导入批次ID(批量导入)',
  `batch_row_no` int DEFAULT NULL COMMENT '源文件数据行号(批量导入时使用)',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `reviewed_by` varchar(32) DEFAULT NULL COMMENT '最终审批人工号快照',
  `reviewed_time` datetime DEFAULT NULL COMMENT '最终审批时间',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '最终退回原因',
  `remark` text COMMENT '备注',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记',
  `lock_version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `active_new_credit_code` varchar(50) GENERATED ALWAYS AS (
    CASE WHEN `lead_type` = 'NEW_ACCOUNT' AND `is_latest` = 1 AND `deleted` = 0
         THEN `unified_credit_code` ELSE NULL END
  ) STORED COMMENT '新客户最新线索幂等键(生成列)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`),
  UNIQUE KEY `uk_lead_active_new_credit_code` (`active_new_credit_code`),
  UNIQUE KEY `uk_lead_business_key` (`business_key`),
  UNIQUE KEY `uk_lead_process_instance` (`process_instance_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`lead_status`),
  KEY `idx_import_batch` (`import_batch_id`),
  KEY `idx_import_batch_row` (`import_batch_id`, `batch_row_no`),
  KEY `idx_cust_name` (`cust_name`),
  KEY `idx_unified_credit_code` (`unified_credit_code`),
  KEY `idx_source_cust` (`source_cust_id`),
  KEY `idx_lead_op_status` (`lead_op`, `lead_status`),
  KEY `idx_lead_entry_list` (`created_by`, `is_latest`, `deleted`, `lead_status`, `created_time`),
  KEY `idx_lead_approval_list` (`lead_status`, `owner_org_id`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户线索表';

-- -------------------------------------------
-- 4. 线索-指定客户经理范围表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_LEAD_MANAGER_SCOPE` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `lead_id` varchar(32) NOT NULL COMMENT '线索ID',
  `manager_emp_id` varchar(32) NOT NULL COMMENT '客户经理工号',
  `manager_org_id` varchar(50) NOT NULL COMMENT '客户经理机构代码快照',
  `assignment_type` varchar(20) NOT NULL COMMENT '分配类型：SCOPE-指定范围/OWNER-主办专属',
  `is_primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主接收人：0-否/1-是',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_manager_scope` (`lead_id`, `manager_emp_id`),
  KEY `idx_manager_visible_leads` (`manager_emp_id`, `assignment_type`, `lead_id`),
  KEY `idx_manager_org` (`manager_org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索指定客户经理范围表';

-- -------------------------------------------
-- 5. 线索-标签快照关系表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_LEAD_TAG_REL` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `lead_id` varchar(32) NOT NULL COMMENT '线索ID',
  `tag_id` varchar(32) NOT NULL COMMENT '标签ID',
  `tag_name_snapshot` varchar(100) NOT NULL COMMENT '提交时标签名称快照',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_tag_rel` (`lead_id`, `tag_id`),
  KEY `idx_lead_tag_tag_id` (`tag_id`, `lead_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索-标签快照关系表';

-- -------------------------------------------
-- 6. 线索导入批次表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `LEAD_IMPORT_BATCH` (
  `id` varchar(32) NOT NULL COMMENT '批次ID',
  `batch_no` varchar(64) NOT NULL COMMENT '批次号(展示用)',
  `source_file_name` varchar(255) DEFAULT NULL COMMENT '源文件名',
  `source_file_object_id` varchar(32) DEFAULT NULL COMMENT '原始导入文件对象ID(通过FileApi管理)',
  `file_md5` varchar(64) DEFAULT NULL COMMENT '文件MD5',
  `status` varchar(30) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/VALIDATION_FAILED/PENDING_APPROVAL/APPROVED/REJECTED',
  `total_row_count` int(11) NOT NULL DEFAULT 0 COMMENT '总行数',
  `valid_row_count` int(11) NOT NULL DEFAULT 0 COMMENT '校验通过行数',
  `error_row_count` int(11) NOT NULL DEFAULT 0 COMMENT '错误行数',
  `error_summary` varchar(512) DEFAULT NULL COMMENT '错误摘要',
  `error_file_object_id` varchar(32) DEFAULT NULL COMMENT '错误明细文件ID(可选)',
  `validation_finished_time` datetime DEFAULT NULL COMMENT '整批校验完成时间',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键(LEAD:IMP_{id})',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) NOT NULL COMMENT '创建人',
  `submitted_by` varchar(32) DEFAULT NULL COMMENT '提交审批人工号',
  `submitted_time` datetime DEFAULT NULL COMMENT '提交审批时间',
  `reviewed_by` varchar(32) DEFAULT NULL COMMENT '最终审批人工号快照',
  `reviewed_time` datetime DEFAULT NULL COMMENT '最终审批时间',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '最终退回原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `lock_version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  UNIQUE KEY `uk_batch_business_key` (`business_key`),
  UNIQUE KEY `uk_batch_process_instance` (`process_instance_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_status` (`status`),
  KEY `idx_batch_approval_list` (`status`, `owner_org_id`, `submitted_time`),
  KEY `idx_created_by_time` (`created_by`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索导入批次表';

-- -------------------------------------------
-- 7.1 M98 客户主档表（仅承接 T-1 同步及存量业务查询）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_MASTER` (
  `id` varchar(50) NOT NULL COMMENT 'M98客户主档内部ID',
  `cust_no` varchar(100) NOT NULL COMMENT 'M98客户号',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记',
  `statis_dt` varchar(10) DEFAULT NULL COMMENT 'M98统计日期(yyyy-MM-dd)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_m98_cust_no` (`cust_no`),
  KEY `idx_m98_statis_dt` (`statis_dt`),
  KEY `idx_m98_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='M98客户主档表（不承载客户营销）';

-- -------------------------------------------
-- 7.2 客户营销主档表（与 M98 CUST_MASTER 隔离）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUSTOMER_MARKET_CUSTOMER` (
  `id` varchar(32) NOT NULL COMMENT '客户ID',
  `cust_no` varchar(100) DEFAULT NULL COMMENT 'CCRM客户号；未开户潜客为空',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码(客户营销幂等键；迁移历史数据可为空)',
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
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额(元)',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额(元)',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '来源机构代码（不承载可见性）',
  `lead_id` varchar(32) DEFAULT NULL COMMENT '兼容字段：首次来源线索ID',
  `current_lead_id` varchar(32) DEFAULT NULL COMMENT '当前生效的审批通过线索版本ID',
  `main_manager_id` varchar(32) DEFAULT NULL COMMENT '当前主办客户经理工号',
  `main_org_id` varchar(50) DEFAULT NULL COMMENT '当前主办客户经理机构代码',
  `ownership_status` varchar(30) NOT NULL DEFAULT 'UNASSIGNED' COMMENT '主办状态：UNASSIGNED/ASSIGNED/WAITING_CLAIM/MULTI_CLAIMED',
  `last_touch_time` datetime DEFAULT NULL COMMENT '最近一次有效触达时间(列表展示快照)',
  `source_system` varchar(32) NOT NULL DEFAULT 'LOCAL' COMMENT '主数据来源：LOCAL/CCRM/YB0/CW35',
  `source_updated_time` datetime DEFAULT NULL COMMENT '源系统最后更新时间',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, INACTIVE-停用',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记(0-否,1-是)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `lock_version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_market_cust_no` (`cust_no`),
  UNIQUE KEY `uk_customer_market_credit_code` (`unified_credit_code`),
  KEY `idx_customer_market_name` (`cust_name`),
  KEY `idx_customer_market_lead` (`lead_id`),
  KEY `idx_customer_market_current_lead` (`current_lead_id`),
  KEY `idx_customer_market_manager` (`main_manager_id`),
  KEY `idx_customer_market_org` (`main_org_id`),
  KEY `idx_customer_market_opened_manager` (`deleted`, `is_account_opened`, `main_manager_id`, `updated_time`),
  KEY `idx_customer_market_opened_org` (`deleted`, `is_account_opened`, `main_org_id`, `updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户营销主档表（不承接M98同步）';

-- -------------------------------------------
-- 8. 客户业绩归属快照表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_PERFORMANCE_RELATION_SNAPSHOT` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `subject_type` varchar(20) NOT NULL COMMENT '归属主体类型：EMP-人员/ORG-机构',
  `subject_id` varchar(50) NOT NULL COMMENT '归属主体ID(员工工号或机构代码)',
  `related_emp_id` varchar(32) DEFAULT NULL COMMENT '业绩相关人工号(主体为EMP时填写)',
  `related_org_id` varchar(50) NOT NULL COMMENT '业绩归属机构代码',
  `relation_type` varchar(30) NOT NULL COMMENT '关系类型：MAIN-主办/CO_MANAGER-协办/ORG_POOL-机构公共',
  `ratio` decimal(8,4) DEFAULT NULL COMMENT '业绩占比(0~1)',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期',
  `source_system` varchar(32) NOT NULL DEFAULT 'M98' COMMENT '来源系统',
  `source_batch_id` varchar(64) NOT NULL COMMENT '来源批次ID',
  `refreshed_at` datetime NOT NULL COMMENT '快照刷新时间',
  `refresh_status` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '刷新状态：SUCCESS/STALE',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perf_rel_snapshot` (`cust_id`, `subject_type`, `subject_id`, `source_batch_id`),
  KEY `idx_perf_rel_customer` (`cust_id`, `refresh_status`, `effective_date`),
  KEY `idx_perf_rel_subject` (`subject_type`, `subject_id`, `refresh_status`),
  KEY `idx_perf_rel_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户业绩归属快照表';

-- -------------------------------------------
-- 9. 客户认领关系表
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
  UNIQUE KEY `uk_cust_claim_emp` (`cust_id`, `claimed_by`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_claimed_by` (`claimed_by`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_status` (`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户认领关系表';

-- -------------------------------------------
-- 10. 客户转交记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_TRANSFER_LOG` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `transfer_no` varchar(64) NOT NULL COMMENT '转交单号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `claim_id` varchar(32) DEFAULT NULL COMMENT '关联认领关系ID(按机构转交时填写)',
  `from_manager_id` varchar(32) DEFAULT NULL COMMENT '原主办/维护人工号',
  `from_org_id` varchar(50) DEFAULT NULL COMMENT '原主办/维护机构代码',
  `primary_to_manager_id` varchar(32) NOT NULL COMMENT '新主办客户经理工号',
  `primary_to_org_id` varchar(50) NOT NULL COMMENT '新主办客户经理机构代码',
  `account_opened_snapshot` tinyint(1) NOT NULL COMMENT '转交时是否已开户快照',
  `reason` varchar(500) NOT NULL COMMENT '转交原因',
  `status` varchar(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/COMPLETED/CANCELLED/FAILED',
  `operator_emp_id` varchar(32) NOT NULL COMMENT '操作人工号',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `failure_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  KEY `idx_transfer_customer_time` (`cust_id`, `created_time`),
  KEY `idx_transfer_claim` (`claim_id`),
  KEY `idx_transfer_operator` (`operator_emp_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户转交记录表';

-- -------------------------------------------
-- 11. 客户转交接收人表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_TRANSFER_TARGET` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `transfer_id` varchar(32) NOT NULL COMMENT '转交记录ID',
  `target_emp_id` varchar(32) NOT NULL COMMENT '接收客户经理工号',
  `target_org_id` varchar(50) NOT NULL COMMENT '接收客户经理机构代码快照',
  `target_role` varchar(20) NOT NULL COMMENT '接收角色：PRIMARY-主办/CO_MANAGER-协办',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '接收顺序(首位为新主办)',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_target` (`transfer_id`, `target_emp_id`),
  KEY `idx_transfer_target_emp` (`target_emp_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户转交接收人表';

-- -------------------------------------------
-- 12. 触达任务表
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
  `sla_status` varchar(20) DEFAULT NULL COMMENT 'SLA状态：BLUE/YELLOW/RED（GREEN仅兼容历史值）',
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
-- 13. 触达日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `TOUCH_LOG` (
  `id` varchar(32) NOT NULL COMMENT '日志ID',
  `touch_task_id` varchar(32) NOT NULL COMMENT '触达任务ID',
  `log_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间(业务时间)',
  `client_uuid` varchar(64) DEFAULT NULL COMMENT '客户端幂等UUID(移动端重试去重)',
  `log_content` text COMMENT '日志内容',
  `touch_method` varchar(30) NOT NULL COMMENT '触达方式：VISIT/PHONE/WECHAT/OTHER',
  `participant_emp_ids` text COMMENT '协同人员工工号JSON数组',
  `photo_urls` text COMMENT '照片URL列表（JSON数组）',
  `photo_groups` text NOT NULL COMMENT '分类照片JSON：keyPerson/doorplate/workplace',
  `operator_location` varchar(1000) DEFAULT NULL COMMENT '办理定位JSON或地址',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '归属机构代码',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人工号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_client_uuid` (`touch_task_id`, `client_uuid`),
  KEY `idx_task_id` (`touch_task_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_task_log_time` (`touch_task_id`, `log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触达日志表';
