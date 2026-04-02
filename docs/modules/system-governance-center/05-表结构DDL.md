# 系统治理中心 -- 表结构 DDL

> 版本：V1 | 最后更新：2026-04-02
> DDL 源文件：`docs/schema/ddl-governance.sql`

---

## 1. 表清单

| 序号 | 表名 | 说明 | 主键策略 | 所属 |
|:---:|:---|:---|:---|:---|
| 1 | sys_dict | 字典表 | UUID(id) varchar(32) | 自有 |
| 2 | sys_dict_item | 字典项表（V1备用） | UUID(id) varchar(32) | 自有 |
| 3 | sys_calendar_day | 工作日历（按天） | 自然主键(day) DATE | 自有 |
| 4 | sys_job_conf | 任务调度配置 | UUID(id) varchar(32) | 自有 |
| 5 | sys_job_run_log | 任务执行日志 | UUID(id) varchar(32) | 自有 |
| 6 | sys_config_kv | 系统配置KV | UUID(id) varchar(32) | 自有 |
| 7 | user_notification | 用户通知 | UUID(id) varchar(32) | 自有 |
| 8 | file_object | 文件对象 | UUID(id) varchar(32) | 自有 |
| 9 | biz_file_rel | 业务附件关联 | UUID(id) varchar(32) | 自有 |
| 10 | audit_log | 审计日志 | UUID(id) varchar(32) | 自有 |

---

## 2. 完整 DDL

### 2.1 sys_dict -- 字典表

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 字典ID（UUID主键） |
| dict_type | varchar(100) | NOT NULL | - | 字典类型 |
| dict_code | varchar(100) | NOT NULL | - | 字典编码 |
| dict_label | varchar(200) | NOT NULL | - | 字典标签（显示名称） |
| dict_value | varchar(500) | NOT NULL | - | 字典值（实际存储值） |
| sort_order | int(11) | NULL | 0 | 排序号 |
| status | varchar(20) | NULL | 'ACTIVE' | 状态：ACTIVE-启用, DISABLED-禁用 |
| remark | varchar(500) | NULL | NULL | 备注 |
| created_by | varchar(32) | NULL | NULL | 创建人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_by | varchar(32) | NULL | NULL | 更新人 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `sys_dict` (
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
```

### 2.2 sys_dict_item -- 字典项表（V1备用）

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 字典项ID（UUID主键） |
| dict_type | varchar(100) | NOT NULL | - | 字典类型（关联 sys_dict.dict_type） |
| item_code | varchar(100) | NOT NULL | - | 字典项编码 |
| item_label | varchar(200) | NOT NULL | - | 字典项标签 |
| item_value | varchar(500) | NOT NULL | - | 字典项值 |
| sort_order | int(11) | NULL | 0 | 排序号 |
| status | varchar(20) | NULL | 'ACTIVE' | 状态：ACTIVE-启用, DISABLED-禁用 |
| remark | varchar(500) | NULL | NULL | 备注 |
| created_by | varchar(32) | NULL | NULL | 创建人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_by | varchar(32) | NULL | NULL | 更新人 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `sys_dict_item` (
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
```

> **说明**：V1 版本字典类型与字典项统一存储在 `sys_dict` 中。`sys_dict_item` 保留 DDL 备用，供未来拆分字典类型和字典项时使用。

### 2.3 sys_calendar_day -- 工作日历（按天）

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| day | date | NOT NULL | - | 日期（自然主键） |
| is_workday | tinyint(1) | NOT NULL | 1 | 是否工作日：1-工作日, 0-休息日 |
| remark | varchar(500) | NULL | NULL | 备注（如"国庆节"、"调休"等） |
| created_by | varchar(32) | NULL | NULL | 创建人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_by | varchar(32) | NULL | NULL | 更新人 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `sys_calendar_day` (
  `day` date NOT NULL COMMENT '日期',
  `is_workday` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否工作日：1-工作日,0-休息日',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作日历(按天)';
```

### 2.4 sys_job_conf -- 任务调度配置

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 任务ID（UUID主键） |
| job_key | varchar(100) | NOT NULL | - | 任务KEY（唯一标识） |
| job_name | varchar(200) | NOT NULL | - | 任务名称 |
| cron_expr | varchar(100) | NOT NULL | - | Cron表达式 |
| status | varchar(20) | NOT NULL | 'ACTIVE' | 状态：ACTIVE/PAUSED |
| allow_manual_trigger | tinyint(1) | NOT NULL | 1 | 是否允许手动触发 |
| last_run_time | datetime | NULL | NULL | 上次执行时间 |
| next_run_time | datetime | NULL | NULL | 下次执行时间（可选） |
| remark | varchar(500) | NULL | NULL | 备注 |
| created_by | varchar(32) | NULL | NULL | 创建人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_by | varchar(32) | NULL | NULL | 更新人 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `sys_job_conf` (
  `id` varchar(32) NOT NULL COMMENT '任务ID',
  `job_key` varchar(100) NOT NULL COMMENT '任务KEY(唯一)',
  `job_name` varchar(200) NOT NULL COMMENT '任务名称',
  `cron_expr` varchar(100) NOT NULL COMMENT 'Cron表达式',
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
```

### 2.5 sys_job_run_log -- 任务执行日志

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 执行日志ID（UUID主键） |
| job_id | varchar(32) | NOT NULL | - | 任务ID（关联 sys_job_conf.id） |
| trigger_type | varchar(20) | NOT NULL | - | 触发类型：SCHEDULED/MANUAL |
| reason | varchar(500) | NULL | NULL | 原因（手动触发必填） |
| start_time | datetime | NULL | CURRENT_TIMESTAMP | 开始时间 |
| end_time | datetime | NULL | NULL | 结束时间 |
| status | varchar(20) | NOT NULL | 'RUNNING' | 状态：RUNNING/SUCCESS/FAILED |
| error_msg | longtext | NULL | NULL | 错误信息 |
| created_by | varchar(32) | NULL | NULL | 触发人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

```sql
CREATE TABLE IF NOT EXISTS `sys_job_run_log` (
  `id` varchar(32) NOT NULL COMMENT '执行日志ID',
  `job_id` varchar(32) NOT NULL COMMENT '任务ID',
  `trigger_type` varchar(20) NOT NULL COMMENT '触发类型：SCHEDULED/MANUAL',
  `reason` varchar(500) DEFAULT NULL COMMENT '原因(手动触发必填)',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `error_msg` longtext COMMENT '错误信息',
  `created_by` varchar(32) DEFAULT NULL COMMENT '触发人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务执行日志';
```

### 2.6 sys_config_kv -- 系统配置KV

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 配置ID（UUID主键） |
| config_key | varchar(200) | NOT NULL | - | 配置键（唯一） |
| config_value | longtext | NULL | NULL | 配置值 |
| value_type | varchar(20) | NOT NULL | 'STRING' | 值类型：STRING/JSON/NUMBER/BOOL |
| status | varchar(20) | NOT NULL | 'ACTIVE' | 状态：ACTIVE/DISABLED |
| remark | varchar(500) | NULL | NULL | 备注 |
| created_by | varchar(32) | NULL | NULL | 创建人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_by | varchar(32) | NULL | NULL | 更新人 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `sys_config_kv` (
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
```

### 2.7 user_notification -- 用户通知表

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 通知ID（UUID主键） |
| emp_id | varchar(32) | NOT NULL | - | 接收人工号 |
| title | varchar(200) | NOT NULL | - | 通知标题 |
| content | text | NULL | NULL | 通知内容 |
| notify_type | varchar(50) | NULL | NULL | 通知类型：SYSTEM-系统, WORKFLOW-流程, BUSINESS-业务 |
| biz_type | varchar(50) | NULL | NULL | 业务类型（如 LEAD/LOAN 等） |
| biz_id | varchar(100) | NULL | NULL | 业务ID |
| link_url | varchar(500) | NULL | NULL | 跳转链接 |
| is_read | tinyint(1) | NULL | 0 | 是否已读：1-已读, 0-未读 |
| read_time | datetime | NULL | NULL | 阅读时间 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

```sql
CREATE TABLE IF NOT EXISTS `user_notification` (
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
```

### 2.8 file_object -- 文件对象表

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 文件对象ID（UUID主键） |
| file_name | varchar(255) | NOT NULL | - | 文件名 |
| file_size | bigint(20) | NULL | NULL | 文件大小（字节） |
| file_type | varchar(100) | NULL | NULL | 文件类型（MIME类型或扩展名） |
| storage_path | varchar(500) | NOT NULL | - | 存储路径（MinIO对象存储） |
| bucket_name | varchar(100) | NULL | NULL | 存储桶名称 |
| md5_hash | varchar(64) | NULL | NULL | MD5哈希值（用于去重校验） |
| uploaded_by | varchar(32) | NULL | NULL | 上传人 |
| uploaded_time | datetime | NULL | CURRENT_TIMESTAMP | 上传时间 |

```sql
CREATE TABLE IF NOT EXISTS `file_object` (
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
```

### 2.9 biz_file_rel -- 业务-附件关联表

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 关联ID（UUID主键） |
| biz_type | varchar(32) | NOT NULL | - | 业务类型（BizType或业务域标识） |
| biz_id | varchar(100) | NOT NULL | - | 业务ID（字符串） |
| file_object_id | varchar(32) | NOT NULL | - | 文件对象ID（关联 file_object.id） |
| file_role | varchar(32) | NULL | NULL | 用途：ATTACHMENT/PHOTO/... |
| created_by | varchar(32) | NULL | NULL | 创建人 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

```sql
CREATE TABLE IF NOT EXISTS `biz_file_rel` (
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
```

### 2.10 audit_log -- 审计日志表

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 日志ID（UUID主键） |
| trace_id | varchar(64) | NULL | NULL | 链路追踪ID |
| emp_id | varchar(32) | NOT NULL | - | 操作人工号 |
| emp_name | varchar(100) | NULL | NULL | 操作人姓名 |
| biz_type | varchar(50) | NULL | NULL | 业务类型 |
| biz_action | varchar(50) | NULL | NULL | 业务动作 |
| resource_url | varchar(500) | NULL | NULL | 资源URL |
| request_method | varchar(20) | NULL | NULL | 请求方法（GET/POST/PUT/DELETE） |
| request_params | text | NULL | NULL | 请求参数（脱敏后） |
| response_status | int(11) | NULL | NULL | 响应状态码 |
| error_msg | text | NULL | NULL | 错误信息 |
| ip_address | varchar(50) | NULL | NULL | IP地址 |
| user_agent | varchar(500) | NULL | NULL | 用户代理 |
| execution_time | int(11) | NULL | NULL | 执行耗时（毫秒） |
| reason | varchar(500) | NULL | NULL | 操作原因（高危动作必填） |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

```sql
CREATE TABLE IF NOT EXISTS `audit_log` (
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
```

---

## 3. 索引说明

### 3.1 sys_dict

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| uk_dict_type_code | dict_type, dict_code | 唯一索引 | 同一字典类型下编码唯一，防止重复录入 |
| idx_dict_type | dict_type | 普通索引 | 按字典类型查询字典项列表（高频查询） |

### 3.2 sys_dict_item

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| uk_dict_type_item_code | dict_type, item_code | 唯一索引 | 同一字典类型下字典项编码唯一 |
| idx_dict_type | dict_type | 普通索引 | 按字典类型查询字典项列表 |

### 3.3 sys_calendar_day

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | day | 主键 | 日期天然唯一，直接作为主键 |

### 3.4 sys_job_conf

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| uk_job_key | job_key | 唯一索引 | 任务KEY全局唯一，用于代码中按KEY查找任务 |
| idx_status | status | 普通索引 | 按状态过滤活跃/暂停任务 |

### 3.5 sys_job_run_log

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_job_id | job_id | 普通索引 | 按任务ID查询执行记录 |
| idx_created_time | created_time | 普通索引 | 按时间范围查询执行日志，支持归档清理 |

### 3.6 sys_config_kv

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| uk_config_key | config_key | 唯一索引 | 配置键全局唯一，防止重复配置 |
| idx_status | status | 普通索引 | 按状态过滤有效/禁用配置 |

### 3.7 user_notification

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_emp_id_read | emp_id, is_read | 普通联合索引 | 查询某用户的未读/已读通知（核心高频查询） |
| idx_created_time | created_time | 普通索引 | 按时间排序、按时间范围过滤，支持归档清理 |

### 3.8 file_object

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_uploaded_by | uploaded_by | 普通索引 | 按上传人查询文件列表 |

### 3.9 biz_file_rel

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| uk_biz_file | biz_type, biz_id, file_object_id | 唯一索引 | 同一业务对象不能重复关联同一文件 |
| idx_biz | biz_type, biz_id | 普通联合索引 | 按业务类型和业务ID查询关联文件列表 |
| idx_file | file_object_id | 普通索引 | 按文件ID反查关联的业务记录 |

### 3.10 audit_log

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_emp_id | emp_id | 普通索引 | 按操作人工号查询审计记录 |
| idx_biz_type | biz_type | 普通索引 | 按业务类型过滤审计记录 |
| idx_created_time | created_time | 普通索引 | 按时间范围查询审计日志，支持月度分区和归档 |
| idx_trace_id | trace_id | 普通索引 | 按链路追踪ID查询同一请求的审计记录 |

---

## 4. 逻辑外键

> 本系统不使用物理外键约束，全部通过应用层保证引用完整性。

| 表 | 字段 | 关联表 | 关联字段 | 说明 |
|:---|:---|:---|:---|:---|
| user_notification | emp_id | PT_USER | USER_ID | 通知接收人，来自 auth-permission-center 管理的用户表 |
| audit_log | emp_id | PT_USER | USER_ID | 审计日志操作人 |
| sys_job_run_log | job_id | sys_job_conf | id | 执行日志关联的任务配置 |
| sys_job_run_log | created_by | PT_USER | USER_ID | 手动触发的触发人 |
| biz_file_rel | file_object_id | file_object | id | 文件关联，删除文件前需检查关联 |
| file_object | uploaded_by | PT_USER | USER_ID | 文件上传人 |
| sys_dict | created_by | PT_USER | USER_ID | 字典创建人 |
| sys_dict | updated_by | PT_USER | USER_ID | 字典更新人 |
| sys_config_kv | created_by | PT_USER | USER_ID | 配置创建人 |
| sys_config_kv | updated_by | PT_USER | USER_ID | 配置更新人 |
| sys_calendar_day | created_by | PT_USER | USER_ID | 日历创建人 |
| sys_calendar_day | updated_by | PT_USER | USER_ID | 日历更新人 |

---

## 5. 审计字段规范

本模块各表的审计字段设计遵循以下规范：

### 5.1 标准审计字段（4字段）

适用于普通管理类表，包含完整的创建和更新追踪：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| created_by | varchar(32) | 创建人工号 |
| created_time | datetime DEFAULT CURRENT_TIMESTAMP | 创建时间，数据库自动填充 |
| updated_by | varchar(32) | 最后更新人工号 |
| updated_time | datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 最后更新时间，数据库自动维护 |

**适用表**：sys_dict, sys_dict_item, sys_calendar_day, sys_job_conf, sys_config_kv

### 5.2 仅创建时间

通知为系统生成，不需要更新人和更新时间：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| created_time | datetime DEFAULT CURRENT_TIMESTAMP | 通知创建时间 |

**适用表**：user_notification

> 说明：user_notification 的 `is_read` 和 `read_time` 虽然会更新，但不属于审计字段范畴，不需要记录"谁标记已读"（通知接收人本人操作）。

### 5.3 仅创建时间（append-only）

审计日志和执行日志为不可修改的追加记录：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| created_by | varchar(32) | 触发人/操作人（sys_job_run_log 使用） |
| created_time | datetime DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**适用表**：audit_log, sys_job_run_log

> 说明：audit_log 表不设 updated 字段，因为审计记录不可修改、不可删除。sys_job_run_log 的 `end_time` 和 `status` 会在任务执行完成时更新，但不需要记录"谁更新"（系统自动更新）。

### 5.4 上传审计

文件表使用专用的上传审计字段：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| uploaded_by | varchar(32) | 上传人工号 |
| uploaded_time | datetime DEFAULT CURRENT_TIMESTAMP | 上传时间 |

**适用表**：file_object

### 5.5 仅创建审计

业务附件关联表只记录创建信息：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| created_by | varchar(32) | 创建人工号 |
| created_time | datetime DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**适用表**：biz_file_rel

---

## 6. 关键字段取值枚举

### 6.1 通用状态

| 字段位置 | 枚举值 | 说明 |
|:---|:---|:---|
| sys_dict.status | `ACTIVE` | 启用 |
| sys_dict.status | `DISABLED` | 禁用 |
| sys_dict_item.status | `ACTIVE` | 启用 |
| sys_dict_item.status | `DISABLED` | 禁用 |
| sys_config_kv.status | `ACTIVE` | 启用 |
| sys_config_kv.status | `DISABLED` | 禁用 |

### 6.2 工作日历

| 字段 | 枚举值 | 说明 |
|:---|:---|:---|
| sys_calendar_day.is_workday | `1` | 工作日 |
| sys_calendar_day.is_workday | `0` | 休息日 |

### 6.3 任务调度

| 字段 | 枚举值 | 说明 |
|:---|:---|:---|
| sys_job_conf.status | `ACTIVE` | 活跃（定时执行中） |
| sys_job_conf.status | `PAUSED` | 暂停 |
| sys_job_conf.allow_manual_trigger | `1` | 允许手动触发 |
| sys_job_conf.allow_manual_trigger | `0` | 不允许手动触发 |
| sys_job_run_log.trigger_type | `SCHEDULED` | 定时触发 |
| sys_job_run_log.trigger_type | `MANUAL` | 手动触发 |
| sys_job_run_log.status | `RUNNING` | 运行中 |
| sys_job_run_log.status | `SUCCESS` | 成功 |
| sys_job_run_log.status | `FAILED` | 失败 |

### 6.4 系统配置

| 字段 | 枚举值 | 说明 |
|:---|:---|:---|
| sys_config_kv.value_type | `STRING` | 字符串类型 |
| sys_config_kv.value_type | `JSON` | JSON类型 |
| sys_config_kv.value_type | `NUMBER` | 数值类型 |
| sys_config_kv.value_type | `BOOL` | 布尔类型 |

### 6.5 用户通知

| 字段 | 枚举值 | 说明 |
|:---|:---|:---|
| user_notification.notify_type | `SYSTEM` | 系统通知 |
| user_notification.notify_type | `WORKFLOW` | 流程通知 |
| user_notification.notify_type | `BUSINESS` | 业务通知 |
| user_notification.is_read | `0` | 未读 |
| user_notification.is_read | `1` | 已读 |

### 6.6 文件管理

| 字段 | 枚举值 | 说明 |
|:---|:---|:---|
| biz_file_rel.file_role | `ATTACHMENT` | 附件 |
| biz_file_rel.file_role | `PHOTO` | 图片 |
| biz_file_rel.biz_type | 由调用方决定 | 如 LEAD / LOAN / SUPPORT / PRODUCT / DOC 等 |

### 6.7 审计日志

| 字段 | 枚举值 | 说明 |
|:---|:---|:---|
| audit_log.biz_type | `SYS_CONFIG` | 系统配置管理 |
| audit_log.biz_type | `SYS_JOB` | 任务调度管理 |
| audit_log.biz_type | `SQL_PROBE` | SQL探查 |
| audit_log.biz_type | `LEAD` | 线索管理 |
| audit_log.biz_type | `CUSTOMER` | 客户管理 |
| audit_log.biz_type | `LOAN` | 资产投放 |
| audit_log.biz_type | `SUPPORT` | 中场支持 |
| audit_log.biz_type | `WORKFLOW` | 工作流 |
| audit_log.biz_type | `AUTH` | 认证授权 |
| audit_log.biz_action | `READ` | 读取/查询 |
| audit_log.biz_action | `CONFIG` | 配置变更（增/改/删） |
| audit_log.biz_action | `JOB_TRIGGER` | 手动触发任务 |
| audit_log.biz_action | `EXECUTE_SQL` | 执行SQL探查 |
| audit_log.biz_action | `EXPORT` | 数据导出 |
| audit_log.biz_action | `IMPORT` | 数据导入 |
| audit_log.request_method | `GET` | GET 请求 |
| audit_log.request_method | `POST` | POST 请求 |
| audit_log.request_method | `PUT` | PUT 请求 |
| audit_log.request_method | `DELETE` | DELETE 请求 |

---

## 7. 分区/归档策略

### 7.1 audit_log -- 审计日志

- **分区方式**：建议按 `created_time` 月度 RANGE 分区
- **在线保留**：至少 12 个月
- **归档策略**：超期数据导出至冷存储（如 OSS/NAS），然后 DROP 对应分区
- **分区 DDL 示例**：

```sql
ALTER TABLE audit_log PARTITION BY RANGE (TO_DAYS(created_time)) (
  PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
  PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
  -- 按月追加分区...
  PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

> **注意**：需提前创建未来月份的分区，建议通过定时任务在每月月末自动创建下月分区。

### 7.2 sys_job_run_log -- 任务执行日志

- **在线保留**：3 个月
- **归档策略**：按 `created_time` 定期清理超期数据
- **清理方式**：通过定时任务执行 DELETE 或 PARTITION DROP
- **建议**：可通过 sys_job_conf 配置一个 `JOB_LOG_CLEANUP` 定时任务自动清理

### 7.3 user_notification -- 用户通知

- **在线保留**：6 个月
- **归档策略**：按 `created_time` 定期清理超期数据
- **清理方式**：已读超过 6 个月的通知可安全删除
- **建议**：通过定时任务定期清理已读的历史通知

### 7.4 file_object / biz_file_rel -- 文件相关

- **归档策略**：不主动清理
- **孤立文件清理**：可定期检查 `file_object` 中无 `biz_file_rel` 关联且上传超过 30 天的文件，标记为待清理
- **MinIO 同步**：删除数据库记录时需同步删除 MinIO 中的对象
