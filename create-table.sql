-- ============================================
-- Branch Platform merged business schema
-- Generated from current database: branch_platform
-- Generated at: 2026-03-14
-- Scope: non-Flowable business tables and views
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop compatibility views first
DROP VIEW IF EXISTS `tag`;

-- Drop business tables
DROP TABLE IF EXISTS `addrbook_employee`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `biz_process_map`;
DROP TABLE IF EXISTS `cust_claim`;
DROP TABLE IF EXISTS `cust_lead`;
DROP TABLE IF EXISTS `cust_master`;
DROP TABLE IF EXISTS `cust_tag`;
DROP TABLE IF EXISTS `doc_info`;
DROP TABLE IF EXISTS `ext_org_info`;
DROP TABLE IF EXISTS `ext_user_org`;
DROP TABLE IF EXISTS `file_object`;
DROP TABLE IF EXISTS `portal_nav`;
DROP TABLE IF EXISTS `portal_shortcut`;
DROP TABLE IF EXISTS `product_info`;
DROP TABLE IF EXISTS `pt_resource`;
DROP TABLE IF EXISTS `pt_role`;
DROP TABLE IF EXISTS `pt_role_biz_scope`;
DROP TABLE IF EXISTS `pt_role_resource`;
DROP TABLE IF EXISTS `pt_user`;
DROP TABLE IF EXISTS `pt_user_role`;
DROP TABLE IF EXISTS `statistics_data`;
DROP TABLE IF EXISTS `sys_dict`;
DROP TABLE IF EXISTS `touch_log`;
DROP TABLE IF EXISTS `touch_task`;
DROP TABLE IF EXISTS `user_notification`;
DROP TABLE IF EXISTS `wf_node_candidate_conf`;
DROP TABLE IF EXISTS `wf_node_form_conf`;
DROP TABLE IF EXISTS `wf_timeout_rule`;

-- Recreate business tables
CREATE TABLE `addrbook_employee` (
  `emp_id` varchar(32) NOT NULL COMMENT '员工工号',
  `emp_name` varchar(100) NOT NULL COMMENT '员工姓名',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `org_code` varchar(50) DEFAULT NULL COMMENT '所属机构代码',
  `org_name` varchar(200) DEFAULT NULL COMMENT '所属机构名称',
  `position` varchar(100) DEFAULT NULL COMMENT '岗位',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-在职, RESIGNED-离职',
  `maintainer_emp_id` varchar(32) DEFAULT NULL COMMENT '维护人工号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int(11) DEFAULT '0' COMMENT 'Delete flag: 0=active, 1=deleted',
  PRIMARY KEY (`emp_id`),
  KEY `idx_org_code` (`org_code`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通讯录员工表';

CREATE TABLE `audit_log` (
  `id` varchar(32) NOT NULL COMMENT '日志ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `emp_id` varchar(32) NOT NULL COMMENT '操作人工号',
  `emp_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `biz_action` varchar(50) DEFAULT NULL COMMENT '业务动作',
  `resource_url` varchar(500) DEFAULT NULL COMMENT '资源URL',
  `request_method` varchar(20) DEFAULT NULL COMMENT '请求方法',
  `request_params` text COMMENT '请求参数（脱敏）',
  `response_status` int(11) DEFAULT NULL COMMENT '响应状态码',
  `error_msg` text COMMENT '错误信息',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `execution_time` int(11) DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `reason` varchar(500) DEFAULT NULL COMMENT '操作原因（高危动作必填）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';

CREATE TABLE `biz_process_map` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务流程映射表';

CREATE TABLE `cust_claim` (
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
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_claimed_by` (`claimed_by`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_status` (`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户认领关系表';

CREATE TABLE `cust_lead` (
  `id` varchar(32) NOT NULL COMMENT '线索ID',
  `lead_no` varchar(100) NOT NULL COMMENT '线索编号',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) DEFAULT NULL COMMENT '所属行业',
  `lead_source` varchar(50) DEFAULT NULL COMMENT '线索来源',
  `lead_status` varchar(50) DEFAULT 'DRAFT' COMMENT '线索状态：DRAFT-草稿, SUBMITTED-已提交, IN_APPROVAL-审批中, APPROVED-已通过, REJECTED-已驳回',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构代码',
  `assigned_to` varchar(50) DEFAULT NULL COMMENT 'Assigned user',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键（LEAD:{id}）',
  `remark` text COMMENT '备注',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT 'Soft delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_status` (`lead_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户线索表';

CREATE TABLE `cust_master` (
  `id` varchar(32) NOT NULL COMMENT '客户ID',
  `cust_no` varchar(100) NOT NULL COMMENT '客户编号',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) DEFAULT NULL COMMENT '所属行业',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '来源机构代码（不承载可见性）',
  `lead_id` varchar(32) DEFAULT NULL COMMENT '来源线索ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, INACTIVE-停用',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_no` (`cust_no`),
  KEY `idx_lead_id` (`lead_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户主档表';

CREATE TABLE `cust_tag` (
  `id` varchar(32) NOT NULL COMMENT '标签ID',
  `tag_name` varchar(100) NOT NULL COMMENT '标签名称',
  `tag_code` varchar(100) NOT NULL COMMENT '标签编码',
  `tag_category` varchar(50) DEFAULT NULL COMMENT '标签分类',
  `description` varchar(500) DEFAULT NULL COMMENT '标签描述',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-否, 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_code` (`tag_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户标签表';

CREATE TABLE `doc_info` (
  `id` varchar(32) NOT NULL COMMENT '文档ID',
  `doc_title` varchar(200) NOT NULL COMMENT '文档标题',
  `doc_category` varchar(50) DEFAULT NULL COMMENT '文档分类',
  `file_object_id` varchar(32) DEFAULT NULL COMMENT '文件对象ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档信息表';
-- 机构表（新增补充）
CREATE TABLE `EXT_ORG_INFO` (
  `ID` int NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  `ORG_CODE` varchar(20) NOT NULL COMMENT '机构编号',
  `ORG_NAME` varchar(200) NOT NULL COMMENT '机构名称',
  `ORG_LEVEL` int DEFAULT NULL COMMENT '机构等级 1 总行 2 分行 3 支行',
  `P_ID` varchar(20) DEFAULT NULL COMMENT '上级机构编码',
  `ORGAN_STATE` int DEFAULT '0' COMMENT '状态 0 启用 1 删除',
  `ADM_DIVISION_CODE` varchar(20) DEFAULT NULL COMMENT '行政区划代码',
  `ADM_DIVISION_NAME` varchar(255) DEFAULT NULL COMMENT '行政区划名称',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_ext_org_info_org_code` (`ORG_CODE`),
  KEY `idx_p_id` (`P_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机构表';

-- 用户机构关联表（新增补充）
CREATE TABLE `EXT_USER_ORG` (
  `USER_ID` varchar(50) NOT NULL COMMENT '用户ID',
  `ORG_CODE` varchar(20) NOT NULL COMMENT '机构编码',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ORG_CODE`),
  KEY `idx_org_code` (`ORG_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户机构关联表';

CREATE TABLE `file_object` (
  `id` varchar(32) NOT NULL COMMENT '文件对象ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `storage_path` varchar(500) NOT NULL COMMENT '存储路径（对象存储）',
  `bucket_name` varchar(100) DEFAULT NULL COMMENT '存储桶名称',
  `md5_hash` varchar(64) DEFAULT NULL COMMENT 'MD5哈希值',
  `uploaded_by` varchar(32) DEFAULT NULL COMMENT '上传人',
  `uploaded_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_uploaded_by` (`uploaded_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件对象表';

CREATE TABLE `portal_nav` (
  `id` varchar(32) NOT NULL COMMENT '导航ID',
  `nav_name` varchar(100) NOT NULL COMMENT '导航名称',
  `nav_url` varchar(500) NOT NULL COMMENT '导航URL',
  `nav_icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `nav_category` varchar(50) DEFAULT NULL COMMENT '分类',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='网址导航表';

CREATE TABLE `portal_shortcut` (
  `id` varchar(32) NOT NULL COMMENT '快捷入口ID',
  `shortcut_name` varchar(100) NOT NULL COMMENT '快捷入口名称',
  `shortcut_url` varchar(500) NOT NULL COMMENT '跳转URL',
  `shortcut_icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `shortcut_type` varchar(50) DEFAULT NULL COMMENT '类型：SYSTEM-系统, CUSTOM-自定义',
  `target_type` varchar(50) DEFAULT NULL COMMENT '目标类型：INTERNAL-内部, EXTERNAL-外部',
  `emp_id` varchar(32) DEFAULT NULL COMMENT '所属用户工号（自定义快捷入口）',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_type` (`shortcut_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作台快捷入口表';

CREATE TABLE `product_info` (
  `id` varchar(64) NOT NULL COMMENT '产品ID',
  `product_code` varchar(64) NOT NULL COMMENT '产品代码',
  `product_name` varchar(255) NOT NULL COMMENT '产品名称',
  `product_category` varchar(64) NOT NULL COMMENT '产品类别',
  `description` text COMMENT '产品描述',
  `status` varchar(32) NOT NULL COMMENT '产品状态',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_product_code` (`product_code`),
  KEY `idx_category` (`product_category`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品信息表';

-- 用户表
CREATE TABLE `PT_USER` (
  `USER_ID` varchar(50) NOT NULL COMMENT '用户ID（工号）',
  `USERNAME` varchar(200) NOT NULL COMMENT '用户姓名',
  `USERCHNNAME` varchar(200) NOT NULL COMMENT '用户中文姓名',
  `PWD` varchar(64) DEFAULT NULL COMMENT '密码（加密）',
  `EMAIL` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `ISEXPIRED` int DEFAULT '0' COMMENT '1 过期 0 未过期',
  `ISLOCKED` int DEFAULT '0' COMMENT '1 被锁 0 未被锁',
  `PASS_WRONG_COUNT` int DEFAULT '0' COMMENT '密码错误次数',
  `ISENABLED` int DEFAULT '1' COMMENT '0 启用 1 未启用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_AUTHOR` varchar(50) DEFAULT NULL COMMENT '创建者',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_AUTHOR` varchar(50) DEFAULT NULL COMMENT '更新者',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  `PWD_UPDATE_TIME` datetime DEFAULT NULL COMMENT '密码更新时间',
  PRIMARY KEY (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员表';

-- 资源表
CREATE TABLE `PT_RESOURCE` (
  `RESOURCE_ID` varchar(20) NOT NULL COMMENT '资源ID',
  `RESOURCE_URL` varchar(256) NOT NULL COMMENT '资源URL（支持Ant通配符）',
  `RESOURCE_METHOD` varchar(10) NOT NULL COMMENT '请求方法：GET/POST/PUT/DELETE，支持 *',
  `MENU_NAME` varchar(256) NOT NULL COMMENT '菜单名称',
  `MENU_ICON_URL` varchar(256) DEFAULT NULL COMMENT '图标路径',
  `MENU_RANK_NO` int DEFAULT '0' COMMENT '菜单排序',
  `ISMENU` int DEFAULT '0' COMMENT '是否菜单 0 是 1 不是',
  `MENU_ENDFLAG` varchar(10) DEFAULT '0' COMMENT '表单结束标志，是否叶子节点菜单 1 是 0 不是',
  `PARENT_RESOURCE_ID` varchar(60) DEFAULT NULL COMMENT '上级资源ID',
  `STATUS` int DEFAULT '0' COMMENT '状态 0启用 1 不启用',
  `SYS_CODE` varchar(10) DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`RESOURCE_ID`),
  UNIQUE KEY `uk_pt_resource_url_method_sys` (`RESOURCE_URL`,`RESOURCE_METHOD`,`SYS_CODE`),
  KEY `idx_pt_resource_status` (`STATUS`,`SYS_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源表';

-- 角色表
CREATE TABLE `PT_ROLE` (
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `ROLE_CODE` varchar(10) NOT NULL COMMENT '角色编码',
  `ROLE_CHNAME` varchar(100) NOT NULL COMMENT '角色中文名',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `SYS_CODE` varchar(10) DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- 角色业务范围表（原SQL中的扩展表，保留）
CREATE TABLE `PT_ROLE_BIZ_SCOPE` (
  `ID` varchar(32) NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `BIZ_TYPE` varchar(50) NOT NULL COMMENT '业务类型：NAV/PRODUCT/LEAD/CUSTOMER等',
  `DATA_SCOPE` varchar(50) NOT NULL COMMENT '数据范围：SELF_CREATED/SELF/SELF_ASSIGNED/ORG/ORG_SUBTREE/ALL/WORKFLOW_PARTICIPANT',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_pt_role_biz_scope_role_biz` (`ROLE_ID`,`BIZ_TYPE`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_biz_type` (`BIZ_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色业务范围表';

-- 角色资源关联表
CREATE TABLE `PT_ROLE_RESOURCE` (
  `ID` varchar(32) NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `RESOURCE_ID` varchar(20) NOT NULL COMMENT '资源ID',
  `SYS_CODE` varchar(10) DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_resource_id` (`RESOURCE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色资源关联表';

-- 用户角色关联表（新增补充）
CREATE TABLE `PT_USER_ROLE` (
  `USER_ID` varchar(50) NOT NULL COMMENT '用户ID',
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `DEFAULT_ASSIGN` int DEFAULT '0' COMMENT '默认分配',
  `INHERIT_ASSIGN` int DEFAULT '0' COMMENT '用户组角色继承',
  `GROUP_ASSING` int DEFAULT '0' COMMENT '角色组分配',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ROLE_ID`),
  KEY `idx_role_id` (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';


CREATE TABLE `sys_dict` (
  `id` varchar(32) NOT NULL COMMENT '字典ID',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型',
  `dict_code` varchar(100) NOT NULL COMMENT '字典编码',
  `dict_label` varchar(200) NOT NULL COMMENT '字典标签',
  `dict_value` varchar(500) NOT NULL COMMENT '字典值',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`,`dict_code`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典表';

CREATE TABLE `touch_log` (
  `id` varchar(32) NOT NULL COMMENT '日志ID',
  `touch_task_id` varchar(32) NOT NULL COMMENT '触达任务ID',
  `log_content` text COMMENT '日志内容',
  `photo_urls` text COMMENT '照片URL列表（JSON数组）',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '归属机构代码',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人工号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`touch_task_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触达日志表';

CREATE TABLE `touch_task` (
  `id` varchar(32) NOT NULL COMMENT '任务ID',
  `task_no` varchar(100) NOT NULL COMMENT '任务编号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `org_id` varchar(50) NOT NULL COMMENT '归属机构代码',
  `assignee_emp_id` varchar(32) NOT NULL COMMENT '执行人工号',
  `task_type` varchar(50) DEFAULT NULL COMMENT '任务类型：FIRST_TOUCH-首次触达, FOLLOW_UP-跟进',
  `task_status` varchar(50) DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待办, SUCCESS-成功, CANCELLED-取消',
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
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触达任务表';

CREATE TABLE `user_notification` (
  `id` varchar(32) NOT NULL COMMENT '通知ID',
  `emp_id` varchar(32) NOT NULL COMMENT '接收人工号',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` text COMMENT '通知内容',
  `notify_type` varchar(50) DEFAULT NULL COMMENT '通知类型：SYSTEM-系统, WORKFLOW-流程, BUSINESS-业务',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(100) DEFAULT NULL COMMENT '业务ID',
  `link_url` varchar(500) DEFAULT NULL COMMENT '跳转链接',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读：1-已读, 0-未读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_read` (`emp_id`,`is_read`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户通知表';

CREATE TABLE `wf_node_candidate_conf` (
  `id` varchar(32) NOT NULL COMMENT '配置ID',
  `process_definition_key` varchar(100) NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) NOT NULL COMMENT '节点KEY',
  `candidate_type` varchar(50) NOT NULL COMMENT '候选类型：ROLE-角色, ORG-机构, USER-指定用户',
  `candidate_value` text COMMENT '候选值（JSON）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点候选人配置表';

CREATE TABLE `wf_node_form_conf` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点表单配置表';

CREATE TABLE `wf_timeout_rule` (
  `id` varchar(32) NOT NULL COMMENT '规则ID',
  `process_definition_key` varchar(100) NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) NOT NULL COMMENT '节点KEY',
  `timeout_hours` int(11) NOT NULL COMMENT '超时小时数',
  `warning_hours` int(11) DEFAULT NULL COMMENT '预警小时数',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程超时规则表';

SET FOREIGN_KEY_CHECKS = 1;
