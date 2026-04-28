# 客户营销中心 — 表结构 DDL

> 本文档定义客户营销中心模块 (customer-marketing-center) 所有数据表的完整 DDL、索引、逻辑外键、取值枚举及关键设计。
> 所有表使用 InnoDB 引擎 + utf8mb4_unicode_ci 字符集（与 docs/schema/ddl-customer.sql 一致）。
> 主键策略：所有表使用 `varchar(32)` 存储 UUID（去掉连字符），应用层生成。
> 时间字段：所有时间字段统一使用 `datetime(0)`。
> 逻辑删除：使用 `deleted tinyint(1)` 字段，0=未删除，1=已删除。

---

## 1. 表清单

| 序号 | 表名 | 说明 | 主键策略 | 所属域 |
|---|---|---|---|---|
| 1 | `cust_tag` | 客户标签表 | UUID(id) | 标签管理 |
| 2 | `cust_tag_rel` | 客户-标签关联表 | UUID(id) | 标签管理 |
| 3 | `cust_lead` | 客户线索表（含版本管理） | UUID(id) | 线索管理 |
| 4 | `lead_import_batch` | 线索导入批次表 | UUID(id) | 线索管理 |
| 5 | `cust_master` | 客户主档表 | UUID(id) | 客户主档 |
| 6 | `cust_claim` | 客户认领关系表 | UUID(id) | 客户池/认领 |
| 7 | `touch_task` | 触达任务表 | UUID(id) | 触达管理 |
| 8 | `touch_log` | 触达日志表 | UUID(id) | 触达管理 |

---

## 2. 完整 DDL

### 2.1 cust_tag — 客户标签表

```sql
CREATE TABLE `cust_tag` (
  `id`             VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `tag_name`       VARCHAR(100) NOT NULL COMMENT '标签名称（唯一）',
  `tag_code`       VARCHAR(100) NOT NULL COMMENT '标签编码（唯一，业务使用）',
  `tag_category`   VARCHAR(50)  DEFAULT NULL COMMENT '标签分类（如：价值类/行业类/风险类）',
  `tag_priority`   INT(11)      NOT NULL DEFAULT 0 COMMENT '标签优先级（数字越大优先级越高，用于排序）',
  `description`    VARCHAR(500) DEFAULT NULL COMMENT '标签描述',
  `status`         VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用/DISABLED-停用',
  `created_by`     VARCHAR(32)  DEFAULT NULL COMMENT '创建人（员工工号）',
  `created_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`     VARCHAR(32)  DEFAULT NULL COMMENT '最后更新人',
  `updated_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `deleted`        TINYINT(1)   DEFAULT 0 COMMENT '逻辑删除：0-未删除/1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_code` (`tag_code`),
  UNIQUE KEY `uk_tag_name` (`tag_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户标签表';
```

### 2.2 cust_tag_rel — 客户-标签关联表

```sql
CREATE TABLE `cust_tag_rel` (
  `id`             VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `cust_id`        VARCHAR(32)  NOT NULL COMMENT '客户ID（关联 cust_master.id）',
  `tag_id`         VARCHAR(32)  NOT NULL COMMENT '标签ID（关联 cust_tag.id）',
  `created_by`     VARCHAR(32)  DEFAULT NULL COMMENT '打标人（员工工号）',
  `created_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '打标时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_tag` (`cust_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户-标签关联表';
```

### 2.3 cust_lead — 客户线索表

```sql
CREATE TABLE `cust_lead` (
  `id`                      VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `lead_no`                 VARCHAR(100) NOT NULL COMMENT '线索编号（对外展示，唯一）',
  `lead_op`                 VARCHAR(20)  NOT NULL DEFAULT 'CREATE' COMMENT '线索操作类型：CREATE-新建/UPDATE-修改/DELETE-删除',
  `source_cust_id`          VARCHAR(32)  DEFAULT NULL COMMENT '源客户ID（UPDATE/DELETE 时指向已有 cust_master.id）',
  `prev_lead_id`            VARCHAR(32)  DEFAULT NULL COMMENT '上一版本线索ID（UPDATE 时指向被修订的 cust_lead.id）',
  `version_no`              INT          NOT NULL DEFAULT 1 COMMENT '版本号（从 1 开始）',
  `is_latest`               TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否最新版本：0-否/1-是',
  `cust_name`               VARCHAR(200) NOT NULL COMMENT '客户名称',
  `unified_credit_code`     VARCHAR(50)  DEFAULT NULL COMMENT '统一社会信用代码',
  `tag_ids`                 TEXT         DEFAULT NULL COMMENT '标签ID列表（JSON 数组，如 ["TAG_001","TAG_002"]）',
  `contact_person`          VARCHAR(100) DEFAULT NULL COMMENT '联系人姓名',
  `contact_mobile`          VARCHAR(20)  DEFAULT NULL COMMENT '联系人手机号',
  `industry`                VARCHAR(100) DEFAULT NULL COMMENT '行业分类（字典 INDUSTRY）',
  `group_type`              VARCHAR(50)  DEFAULT NULL COMMENT '集团类型（字典 GROUP_TYPE）',
  `customer_type`           VARCHAR(50)  DEFAULT NULL COMMENT '客户类型（字典 CUSTOMER_TYPE）',
  `is_keystone`             TINYINT(1)   DEFAULT NULL COMMENT '是否重点客户：0-否/1-是',
  `enterprise_type`         VARCHAR(50)  DEFAULT NULL COMMENT '企业类型（字典 ENTERPRISE_TYPE）',
  `group_name`              VARCHAR(200) DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened`       TINYINT(1)   DEFAULT NULL COMMENT '是否已开户：0-否/1-是',
  `customer_desc`           TEXT         DEFAULT NULL COMMENT '客户描述',
  `credit_amount`           DECIMAL(20,4) DEFAULT NULL COMMENT '授信金额（元）',
  `credit_exposure_amount`  DECIMAL(20,4) DEFAULT NULL COMMENT '授信敞口金额（元）',
  `lead_source`             VARCHAR(50)  DEFAULT NULL COMMENT '线索来源（字典 LEAD_SOURCE）',
  `lead_status`             VARCHAR(50)  DEFAULT 'DRAFT' COMMENT '线索状态：DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED',
  `owner_org_id`            VARCHAR(50)  NOT NULL COMMENT '归属机构代码（来源，不承载可见性）',
  `assigned_to`             VARCHAR(50)  DEFAULT NULL COMMENT '线索指派人（员工工号）',
  `created_by`              VARCHAR(32)  NOT NULL COMMENT '创建人（员工工号）',
  `business_key`            VARCHAR(100) DEFAULT NULL COMMENT '流程业务键（格式 LEAD:{leadId}）',
  `import_batch_id`         VARCHAR(32)  DEFAULT NULL COMMENT '导入批次ID（关联 lead_import_batch.id）',
  `process_instance_id`     VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID（Flowable）',
  `remark`                  TEXT         DEFAULT NULL COMMENT '备注',
  `created_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`              VARCHAR(32)  DEFAULT NULL COMMENT '最后更新人',
  `updated_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `deleted`                 TINYINT(4)   DEFAULT 0 COMMENT '逻辑删除：0-未删除/1-已删除',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户线索表（含版本管理）';
```

### 2.4 lead_import_batch — 线索导入批次表

```sql
CREATE TABLE `lead_import_batch` (
  `id`                      VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `batch_no`                VARCHAR(64)  NOT NULL COMMENT '批次号（对外展示，唯一）',
  `source_file_name`        VARCHAR(255) DEFAULT NULL COMMENT '源文件名',
  `file_md5`                VARCHAR(64)  DEFAULT NULL COMMENT '文件 MD5（用于去重校验）',
  `status`                  VARCHAR(20)  NOT NULL DEFAULT 'CREATED' COMMENT '批次状态：CREATED/PENDING_APPROVAL/APPROVED/REJECTED',
  `total_row_count`         INT(11)      NOT NULL DEFAULT 0 COMMENT '总行数',
  `error_row_count`         INT(11)      NOT NULL DEFAULT 0 COMMENT '错误行数',
  `error_summary`           VARCHAR(512) DEFAULT NULL COMMENT '错误摘要（JSON 字符串，超长时截断）',
  `error_file_object_id`    VARCHAR(32)  DEFAULT NULL COMMENT '错误明细文件对象ID（MinIO）',
  `business_key`            VARCHAR(100) DEFAULT NULL COMMENT '流程业务键（格式 LEAD:IMP_{batchId}）',
  `process_instance_id`     VARCHAR(64)  DEFAULT NULL COMMENT '流程实例ID（Flowable）',
  `owner_org_id`            VARCHAR(50)  NOT NULL COMMENT '归属机构代码',
  `created_by`              VARCHAR(32)  NOT NULL COMMENT '创建人（员工工号）',
  `created_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`              VARCHAR(32)  DEFAULT NULL COMMENT '最后更新人',
  `updated_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='线索导入批次表';
```

### 2.5 cust_master — 客户主档表

```sql
CREATE TABLE `cust_master` (
  `id`                      VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `cust_no`                 VARCHAR(100) NOT NULL COMMENT '客户编号（对外展示，唯一）',
  `cust_name`               VARCHAR(200) NOT NULL COMMENT '客户名称（唯一）',
  `unified_credit_code`     VARCHAR(50)  DEFAULT NULL COMMENT '统一社会信用代码',
  `contact_person`          VARCHAR(100) DEFAULT NULL COMMENT '联系人姓名',
  `contact_mobile`          VARCHAR(20)  DEFAULT NULL COMMENT '联系人手机号',
  `industry`                VARCHAR(100) DEFAULT NULL COMMENT '行业分类（字典 INDUSTRY）',
  `group_type`              VARCHAR(50)  DEFAULT NULL COMMENT '集团类型（字典 GROUP_TYPE）',
  `customer_type`           VARCHAR(50)  DEFAULT NULL COMMENT '客户类型（字典 CUSTOMER_TYPE）',
  `is_keystone`             TINYINT(1)   DEFAULT NULL COMMENT '是否重点客户：0-否/1-是',
  `enterprise_type`         VARCHAR(50)  DEFAULT NULL COMMENT '企业类型（字典 ENTERPRISE_TYPE）',
  `group_name`              VARCHAR(200) DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened`       TINYINT(1)   DEFAULT NULL COMMENT '是否已开户：0-否/1-是',
  `customer_desc`           TEXT         DEFAULT NULL COMMENT '客户描述',
  `credit_amount`           DECIMAL(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount`  DECIMAL(20,4) DEFAULT NULL COMMENT '授信敞口金额',
  `owner_org_id`            VARCHAR(50)  DEFAULT NULL COMMENT '来源机构代码（不承载可见性，仅作来源属性）',
  `lead_id`                 VARCHAR(32)  DEFAULT NULL COMMENT '来源线索ID（关联 cust_lead.id）',
  `status`                  VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '客户状态：ACTIVE/INACTIVE',
  `deleted`                 TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除/1-已删除',
  `created_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_no` (`cust_no`),
  UNIQUE KEY `uk_cust_name` (`cust_name`),
  KEY `idx_lead_id` (`lead_id`),
  KEY `idx_unified_credit_code` (`unified_credit_code`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户主档表';
```

### 2.6 cust_claim — 客户认领关系表

```sql
CREATE TABLE `cust_claim` (
  `id`                      VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `cust_id`                 VARCHAR(32)  NOT NULL COMMENT '客户ID（关联 cust_master.id）',
  `org_id`                  VARCHAR(50)  NOT NULL COMMENT '认领机构代码',
  `claimed_by`              VARCHAR(32)  NOT NULL COMMENT '认领人（员工工号）',
  `maintainer_emp_id`       VARCHAR(32)  DEFAULT NULL COMMENT '维护人（员工工号，可转交）',
  `claim_status`            VARCHAR(50)  DEFAULT 'CLAIMED' COMMENT '认领状态：CLAIMED-已认领/CANCELLED-已取消',
  `claim_time`              DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '认领时间',
  `cancel_time`             DATETIME     DEFAULT NULL COMMENT '取消时间',
  `cancel_reason`           VARCHAR(500) DEFAULT NULL COMMENT '取消原因',
  `created_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_org` (`cust_id`, `org_id`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_claimed_by` (`claimed_by`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_status` (`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户认领关系表';
```

### 2.7 touch_task — 触达任务表

```sql
CREATE TABLE `touch_task` (
  `id`                      VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `task_no`                 VARCHAR(100) NOT NULL COMMENT '任务编号（对外展示，唯一）',
  `cust_id`                 VARCHAR(32)  NOT NULL COMMENT '客户ID（关联 cust_master.id）',
  `org_id`                  VARCHAR(50)  NOT NULL COMMENT '所属机构代码',
  `assignee_emp_id`         VARCHAR(32)  NOT NULL COMMENT '执行人（员工工号）',
  `task_type`               VARCHAR(50)  DEFAULT NULL COMMENT '任务类型：FIRST_TOUCH-首次触达/FOLLOW_UP-后续跟进',
  `task_status`             VARCHAR(50)  DEFAULT 'PENDING' COMMENT '任务状态：PENDING/IN_PROGRESS/SUCCESS/CANCELLED',
  `plan_finish_time`        DATETIME     DEFAULT NULL COMMENT '计划完成时间',
  `warning_time`            DATETIME     DEFAULT NULL COMMENT '预警时间（SLA 黄灯阈值）',
  `sla_status`              VARCHAR(20)  DEFAULT NULL COMMENT 'SLA 状态：GREEN/YELLOW/RED',
  `sla_warning`             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'SLA 预警标记：0-否/1-是（sla_status 为 YELLOW/RED 时置 1）',
  `business_key`            VARCHAR(100) DEFAULT NULL COMMENT '流程业务键（格式 TOUCH:{taskId}）',
  `success_time`            DATETIME     DEFAULT NULL COMMENT '成功完成时间',
  `cancel_time`             DATETIME     DEFAULT NULL COMMENT '取消时间',
  `created_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_assignee` (`assignee_emp_id`),
  KEY `idx_status` (`task_status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_assignee_status` (`assignee_emp_id`, `task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触达任务表';
```

### 2.8 touch_log — 触达日志表

```sql
CREATE TABLE `touch_log` (
  `id`                      VARCHAR(32)  NOT NULL COMMENT '主键ID（UUID）',
  `touch_task_id`           VARCHAR(32)  NOT NULL COMMENT '触达任务ID（关联 touch_task.id）',
  `log_time`                DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '触达时间（业务时间）',
  `client_uuid`             VARCHAR(64)  DEFAULT NULL COMMENT '客户端幂等键（防重复提交）',
  `log_content`             TEXT         DEFAULT NULL COMMENT '触达内容（文字描述）',
  `photo_urls`              TEXT         DEFAULT NULL COMMENT '照片URL列表（JSON 数组）',
  `owner_org_id`            VARCHAR(50)  DEFAULT NULL COMMENT '归属机构代码',
  `created_by`              VARCHAR(32)  DEFAULT NULL COMMENT '创建人（员工工号）',
  `created_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_client_uuid` (`touch_task_id`, `client_uuid`),
  KEY `idx_task_id` (`touch_task_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_task_log_time` (`touch_task_id`, `log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触达日志表';
```

---

## 3. 索引说明

### 3.1 cust_tag 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_tag_name | tag_name | UK | 标签名唯一约束，按名称快速查找 |
| uk_tag_code | tag_code | UK | 标签编码唯一约束，业务代码引用 |
| idx_status | status | KEY | 过滤启用标签列表 |

### 3.2 cust_tag_rel 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_cust_tag | (cust_id, tag_id) | UK | 防重复打标、快速查询某客户的标签 |
| idx_tag_id | tag_id | KEY | 按标签反查客户列表（标签详情页） |

### 3.3 cust_lead 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_lead_no | lead_no | UK | 线索编号唯一、对外展示查询 |
| idx_owner_org | owner_org_id | KEY | 按机构筛选线索列表 |
| idx_created_by | created_by | KEY | 我创建的线索查询 |
| idx_business_key | business_key | KEY | 通过业务键关联流程实例 |
| idx_status | lead_status | KEY | 按状态筛选（DRAFT/IN_APPROVAL 等） |
| idx_import_batch | import_batch_id | KEY | 按批次查询批量导入的线索 |
| idx_cust_name | cust_name | KEY | 按客户名搜索 |
| idx_unified_credit_code | unified_credit_code | KEY | 按统一信用代码查重 |
| idx_source_cust | source_cust_id | KEY | 查询某客户的所有修订/删除线索 |
| idx_lead_op_status | (lead_op, lead_status) | 组合KEY | 复合筛选：审批中的修改/删除线索 |
| idx_is_latest | is_latest | KEY | 只查最新版本的线索 |

### 3.4 lead_import_batch 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_batch_no | batch_no | UK | 批次号唯一、对外展示 |
| idx_owner_org | owner_org_id | KEY | 按机构查批次列表 |
| idx_status | status | KEY | 按状态筛选（PENDING_APPROVAL 等） |
| idx_created_time | created_time | KEY | 按创建时间倒序 |

### 3.5 cust_master 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_cust_no | cust_no | UK | 客户编号唯一 |
| uk_cust_name | cust_name | UK | 客户名唯一（防重复） |
| idx_lead_id | lead_id | KEY | 从线索反查客户 |
| idx_unified_credit_code | unified_credit_code | KEY | 按统一信用代码查重 |
| idx_deleted | deleted | KEY | 逻辑删除过滤 |

### 3.6 cust_claim 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_cust_org | (cust_id, org_id) | UK | 防同一客户被同一机构重复认领 |
| idx_cust_id | cust_id | KEY | 查询某客户的所有认领机构 |
| idx_org_id | org_id | KEY | 查询某机构认领的所有客户 |
| idx_claimed_by | claimed_by | KEY | 查询某员工认领的客户 |
| idx_maintainer | maintainer_emp_id | KEY | 按维护人筛选（我维护的客户） |
| idx_status | claim_status | KEY | 只查有效认领 |

### 3.7 touch_task 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_task_no | task_no | UK | 任务编号唯一 |
| idx_cust_id | cust_id | KEY | 按客户查任务列表 |
| idx_org_id | org_id | KEY | 按机构查任务（管理员视图） |
| idx_assignee | assignee_emp_id | KEY | 按执行人查任务 |
| idx_status | task_status | KEY | 按状态筛选（PENDING 等） |
| idx_business_key | business_key | KEY | 通过业务键关联流程实例 |
| idx_assignee_status | (assignee_emp_id, task_status) | 组合KEY | 我的待办任务查询（高频） |

### 3.8 touch_log 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| PRIMARY | id | PK | 主键查询 |
| uk_task_client_uuid | (touch_task_id, client_uuid) | UK | 幂等防重，移动端重试安全 |
| idx_task_id | touch_task_id | KEY | 按任务查日志列表 |
| idx_created_by | created_by | KEY | 按员工查其提交的日志 |
| idx_task_log_time | (touch_task_id, log_time) | 组合KEY | 按任务查日志时间线（倒序） |

---

## 4. 逻辑外键

> 说明：本项目不使用物理外键（便于分表、重构及性能），所有关联通过应用层校验。下表列出逻辑外键关系。

| 表 | 字段 | 关联表 | 关联字段 | 说明 | 级联策略 |
|---|---|---|---|---|---|
| cust_tag_rel | cust_id | cust_master | id | 客户 | 删除客户时由应用层清理 |
| cust_tag_rel | tag_id | cust_tag | id | 标签 | 标签停用时保留关联 |
| cust_lead | source_cust_id | cust_master | id | 关联客户（UPDATE/DELETE） | 不级联 |
| cust_lead | prev_lead_id | cust_lead | id | 上一版本线索 | 不级联 |
| cust_lead | import_batch_id | lead_import_batch | id | 批次 | 不级联 |
| cust_lead | owner_org_id | EXT_ORG_INFO | org_code | 归属机构 | 不级联 |
| cust_lead | created_by | PT_USER | emp_id | 创建人 | 不级联 |
| cust_lead | assigned_to | PT_USER | emp_id | 指派人 | 不级联 |
| lead_import_batch | owner_org_id | EXT_ORG_INFO | org_code | 归属机构 | 不级联 |
| lead_import_batch | created_by | PT_USER | emp_id | 创建人 | 不级联 |
| lead_import_batch | error_file_object_id | PT_FILE_OBJECT | id | 错误文件 | 不级联 |
| cust_master | lead_id | cust_lead | id | 来源线索 | 不级联 |
| cust_master | owner_org_id | EXT_ORG_INFO | org_code | 来源机构（不承载可见性） | 不级联 |
| cust_claim | cust_id | cust_master | id | 客户 | 客户删除时由应用层清理 |
| cust_claim | org_id | EXT_ORG_INFO | org_code | 认领机构 | 不级联 |
| cust_claim | claimed_by | PT_USER | emp_id | 认领人 | 不级联 |
| cust_claim | maintainer_emp_id | PT_USER | emp_id | 维护人 | 不级联 |
| touch_task | cust_id | cust_master | id | 客户 | 不级联 |
| touch_task | org_id | EXT_ORG_INFO | org_code | 所属机构 | 不级联 |
| touch_task | assignee_emp_id | PT_USER | emp_id | 执行人 | 不级联 |
| touch_log | touch_task_id | touch_task | id | 触达任务 | 不级联 |
| touch_log | created_by | PT_USER | emp_id | 创建人 | 不级联 |

---

## 5. 关键字段取值枚举

### 5.1 cust_tag.status（标签状态）

| 值 | 说明 |
|---|---|
| ACTIVE | 启用（可被选择打标） |
| DISABLED | 停用（历史数据保留，不再出现在选择列表） |

### 5.2 cust_lead.lead_op（线索操作类型）

| 值 | 说明 |
|---|---|
| CREATE | 新建客户线索（source_cust_id 为空） |
| UPDATE | 修改已有客户（source_cust_id 指向 cust_master.id） |
| DELETE | 删除已有客户（source_cust_id 指向 cust_master.id） |

### 5.3 cust_lead.lead_status（线索状态）

| 值 | 说明 | 可转移到 |
|---|---|---|
| DRAFT | 草稿 | SUBMITTED / 删除 |
| SUBMITTED | 已提交 | IN_APPROVAL |
| IN_APPROVAL | 审批中 | APPROVED / REJECTED |
| APPROVED | 审批通过 | 终态 |
| REJECTED | 审批驳回 | DRAFT（再编辑） |

### 5.4 lead_import_batch.status（批次状态）

| 值 | 说明 | 可转移到 |
|---|---|---|
| CREATED | 已创建 | PENDING_APPROVAL |
| PENDING_APPROVAL | 待审批 | APPROVED / REJECTED |
| APPROVED | 审批通过（终态） | - |
| REJECTED | 审批驳回（终态） | - |

### 5.5 cust_master.status（客户状态）

| 值 | 说明 |
|---|---|
| ACTIVE | 正常 |
| INACTIVE | 非活跃 |

### 5.6 cust_claim.claim_status（认领状态）

| 值 | 说明 |
|---|---|
| CLAIMED | 已认领（有效） |
| CANCELLED | 已取消 |

### 5.7 touch_task.task_type（任务类型）

| 值 | 说明 |
|---|---|
| FIRST_TOUCH | 首次触达（认领后系统自动创建） |
| FOLLOW_UP | 后续跟进（首次触达成功后创建，由业务规则触发） |

### 5.8 touch_task.task_status（任务状态）

| 值 | 说明 | 可转移到 |
|---|---|---|
| PENDING | 待处理 | SUCCESS / CANCELLED |
| SUCCESS | 成功完成（终态） | - |
| CANCELLED | 已取消（终态） | - |

### 5.9 touch_task.sla_status（SLA 状态）

| 值 | 说明 | 判定规则 |
|---|---|---|
| GREEN | 绿灯（正常） | now < warning_time |
| YELLOW | 黄灯（预警） | warning_time <= now < plan_finish_time |
| RED | 红灯（超时） | now >= plan_finish_time 且 task_status=PENDING |

---

## 6. 线索版本管理设计

### 6.1 设计目标

1. 支持对已审批通过并成为客户主档的线索进行修改和删除，所有修改需再次走审批流程。
2. 保留所有历史版本，可审计追溯。
3. 当前最新版本可快速查询，不影响原有线索列表性能。

### 6.2 字段语义

| 字段 | 语义 |
|---|---|
| lead_op | 本条线索的操作类型：CREATE/UPDATE/DELETE |
| source_cust_id | 指向已存在的 cust_master.id；CREATE 时为空 |
| prev_lead_id | 指向被修订的上一版 cust_lead.id；第一版时为空 |
| version_no | 版本号，从 1 开始递增 |
| is_latest | 是否最新版本；每个 "线索链" 最多 1 条为 1 |

### 6.3 场景示例

#### 场景 A — 新建线索

```
INSERT cust_lead (id=L1, lead_op=CREATE, source_cust_id=NULL,
                   prev_lead_id=NULL, version_no=1, is_latest=1, ...)
```

审批通过后生成 cust_master (M1, lead_id=L1)。

#### 场景 B — 修改已有客户

```
步骤1 (同一事务):
  UPDATE cust_lead SET is_latest=0 WHERE id=L1
  INSERT cust_lead (id=L2, lead_op=UPDATE,
                    source_cust_id=M1, prev_lead_id=L1,
                    version_no=2, is_latest=1, ...)
```

L2 审批通过后更新 cust_master (M1) 字段，cust_master.lead_id 不改写（保留首次来源线索）。

#### 场景 C — 删除已有客户

```
INSERT cust_lead (id=L3, lead_op=DELETE,
                  source_cust_id=M1, prev_lead_id=L2,
                  version_no=3, is_latest=1, ...)
UPDATE cust_lead SET is_latest=0 WHERE id=L2
```

L3 审批通过后：
- 更新 cust_master SET deleted=1 WHERE id=M1
- 同时将 cust_claim 关联的所有认领置为 CANCELLED

#### 场景 D — 审批驳回

审批驳回不影响前一版本的 is_latest 标记（因为新版本创建时已将旧版置 0），需在驳回回调中：

```
UPDATE cust_lead SET is_latest=0 WHERE id=L3
UPDATE cust_lead SET is_latest=1 WHERE id=L2
```

### 6.4 查询模板

```sql
-- 查询某客户的所有线索版本（历史时间线）
SELECT * FROM cust_lead
WHERE source_cust_id = 'M1' OR id = (
  SELECT lead_id FROM cust_master WHERE id = 'M1'
)
ORDER BY version_no ASC;

-- 查询所有最新版本的线索（线索列表页默认视图）
SELECT * FROM cust_lead
WHERE is_latest = 1 AND deleted = 0;

-- 查询审批中的修改/删除线索
SELECT * FROM cust_lead
WHERE lead_op IN ('UPDATE','DELETE')
  AND lead_status = 'IN_APPROVAL'
  AND is_latest = 1;
```

### 6.5 约束

- 每个 `source_cust_id` 的线索链中，`is_latest=1` 的记录最多 1 条（应用层保证，无唯一约束）。
- `CREATE` 线索链中，通过 `prev_lead_id` 链表追溯，`source_cust_id` 可为空。
- `version_no` 在同一线索链内单调递增，用作乐观锁。

---

## 7. 客户可见性口径（V1 冻结）

### 7.1 核心规则

- **CUSTOMER 业务类型的数据范围一律按 `cust_claim` 有效认领关系判定**
- `cust_master.owner_org_id` **仅作来源属性，不承载可见性**
- 跨机构全量历史访问通过**独立只读接口**（GET /api/customers/{id}/history），单独授权与审计

### 7.2 CUSTOMER 数据范围 SQL 模板

```sql
-- 基础过滤：只看本人主机构有效认领的客户
SELECT DISTINCT cm.*
FROM cust_master cm
INNER JOIN cust_claim cc ON cc.cust_id = cm.id
WHERE cm.deleted = 0
  AND cc.claim_status = 'CLAIMED'
  AND cc.org_id IN (<CURRENT_USER_ORG_SUBTREE>)
  [AND (cc.maintainer_emp_id = <EMP_ID> OR cc.claimed_by = <EMP_ID>)]  -- PERSONAL 范围再加此过滤
```

### 7.3 为什么不用 owner_org_id

- 客户可被多个机构认领（多对多关系），owner_org_id 只能表达单一来源
- 认领/取消认领是高频操作，通过 cust_claim 判定天然跟随
- 跨机构重复客户场景：不同机构基于各自认领关系独立运营

---

## 8. JSON 字段说明

### 8.1 cust_lead.tag_ids

存储格式：JSON 数组，每个元素为标签ID字符串。

```json
["TAG_001","TAG_002","TAG_003"]
```

**查询建议**：
- 使用 `JSON_CONTAINS(tag_ids, JSON_QUOTE('TAG_001'))` 查询包含某标签的线索
- 或使用应用层过滤（标签数量较少时）
- 不建议对该字段建 JSON 虚拟列索引（变更频繁）

### 8.2 touch_log.photo_urls

存储格式：JSON 数组，每个元素为 MinIO 文件对象的 URL 或对象 ID。

```json
[
  "http://minio.bank.local/touch/202603/abc123.jpg",
  "http://minio.bank.local/touch/202603/def456.jpg"
]
```

**规范**：
- 单条触达日志最多 9 张照片
- URL 为 MinIO 签名 URL，有效期 7 天；过期后通过 FileApi 重新签名
- 对象 ID 存储时通过 `/api/files/{objectId}/presign` 动态换取签名 URL

### 8.3 lead_import_batch.error_summary

存储格式：JSON 对象，包含错误类型统计与前 N 条错误行样例。

```json
{
  "totalErrors": 5,
  "errorTypes": {
    "FIELD_REQUIRED": 2,
    "DUPLICATE_NAME": 3
  },
  "samples": [
    {"row": 3, "field": "cust_name", "error": "客户名称不能为空"},
    {"row": 7, "field": "cust_name", "error": "客户名称已存在"}
  ]
}
```

---

## 9. 分区与归档建议

### 9.1 touch_log 按月分区

数据规模预估：每位客户经理每日 5~10 条日志，全行按 5 万客户经理预计，年增量约 1.5 亿行。

**分区策略**：按 `log_time` 月度 RANGE 分区，保留近 6 个月在线数据。

```sql
ALTER TABLE touch_log
PARTITION BY RANGE (TO_DAYS(log_time)) (
  PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
  PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
  -- ...
  PARTITION p_max VALUES LESS THAN MAXVALUE
);
```

**归档策略**：
- 每月 1 日由定时任务导出前 7 月数据到归档表 `touch_log_archive`
- 归档完成后 DROP 对应分区
- 归档表独立数据库，不参与业务查询

### 9.2 cust_lead 不分区

- 线索数据规模较小（预估年增 100 万），无需分区。
- 历史版本可通过 `is_latest = 0 AND updated_time < now() - 3年` 条件定期归档。
- 归档前导出到 `cust_lead_archive` 表，保留线索链完整。

### 9.3 cust_master 不分区

- 客户主档数据规模中等（年增 20~30 万），采用逻辑删除保留历史。
- 通过 `idx_deleted` 索引过滤，性能可控。
- 五年以上的逻辑删除客户可归档到 `cust_master_archive`。

### 9.4 其他表

| 表 | 策略 |
|---|---|
| cust_tag | 不分区、不归档（数据量小，标签需长期引用） |
| cust_tag_rel | 不分区；随客户归档一起处理 |
| lead_import_batch | 不分区；保留 3 年，超期归档 |
| cust_claim | 不分区；CANCELLED 状态保留 3 年后可归档 |
| touch_task | 不分区；SUCCESS/CANCELLED 终态保留 2 年后归档 |
