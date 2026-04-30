-- ============================================
-- 模块：系统治理中心 (system-governance-center)
-- 描述：字典、工作日历、任务调度、系统配置、通知、文件、审计日志等
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 字典表（含字典类型与字典项，通过 dict_type 分组）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `SYS_DICT` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典表';

-- -------------------------------------------
-- 2. 字典项表（独立字典项表，供未来扩展使用）
-- 说明：当前 V1 版本字典类型与字典项统一存储在 sys_dict 中，
--       如需拆分可启用此表。暂保留 DDL 备用。
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `SYS_DICT_ITEM` (
  `id` varchar(32) NOT NULL COMMENT '字典项ID',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型（关联 sys_dict.dict_type）',
  `item_code` varchar(100) NOT NULL COMMENT '字典项编码',
  `item_label` varchar(200) NOT NULL COMMENT '字典项标签',
  `item_value` varchar(500) NOT NULL COMMENT '字典项值',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_item_code` (`dict_type`,`item_code`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项表';

-- -------------------------------------------
-- 3. 工作日历（按天）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `SYS_CALENDAR_DAY` (
  `day` date NOT NULL COMMENT '日期',
  `is_workday` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否工作日：1-工作日,0-休息日',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作日历(按天)';

-- -------------------------------------------
-- 4. 任务调度配置
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `SYS_JOB_CONF` (
  `id` varchar(32) NOT NULL COMMENT '任务ID',
  `job_key` varchar(100) NOT NULL COMMENT '任务KEY(唯一)',
  `job_name` varchar(200) NOT NULL COMMENT '任务名称',
  `cron_expr` varchar(100) NOT NULL COMMENT 'Cron表达式',
  `quartz_job_class` varchar(255) NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
  `misfire_policy` varchar(32) NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略（V1.6 新增）',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/PAUSED',
  `allow_manual_trigger` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许手动触发',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次执行时间',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次执行时间(可选)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_key` (`job_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务调度配置';

-- -------------------------------------------
-- 5. 任务执行日志
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `SYS_JOB_RUN_LOG` (
  `id` varchar(32) NOT NULL COMMENT '执行日志ID',
  `job_id` varchar(32) NOT NULL COMMENT '任务ID',
  `trigger_type` varchar(20) NOT NULL COMMENT '触发类型：SCHEDULED/MANUAL',
  `reason` varchar(500) DEFAULT NULL COMMENT '原因(手动触发必填)',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `scheduled_fire_time` datetime(3) DEFAULT NULL COMMENT 'Quartz 计划触发时间（V1.6 新增）',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `error_msg` longtext COMMENT '错误信息',
  `created_by` varchar(32) DEFAULT NULL COMMENT '触发人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务执行日志';

-- -------------------------------------------
-- 6. 系统配置KV
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `SYS_CONFIG_KV` (
  `id` varchar(32) NOT NULL COMMENT '配置ID',
  `config_key` varchar(200) NOT NULL COMMENT '配置键(唯一)',
  `config_value` longtext COMMENT '配置值',
  `value_type` varchar(20) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/JSON/NUMBER/BOOL',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置KV';

-- -------------------------------------------
-- 7. 用户通知表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `USER_NOTIFICATION` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户通知表';

-- -------------------------------------------
-- 8. 文件对象表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `FILE_OBJECT` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件对象表';

-- -------------------------------------------
-- 9. 业务-附件关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `BIZ_FILE_REL` (
  `id` varchar(32) NOT NULL COMMENT '关联ID',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型(BizType或业务域)',
  `biz_id` varchar(100) NOT NULL COMMENT '业务ID(字符串)',
  `file_object_id` varchar(32) NOT NULL COMMENT '文件对象ID',
  `file_role` varchar(32) DEFAULT NULL COMMENT '用途：ATTACHMENT/PHOTO/...',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_file` (`biz_type`, `biz_id`, `file_object_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_file` (`file_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务-附件关联表';

-- -------------------------------------------
-- 10. 审计日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `AUDIT_LOG` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审计日志表';
