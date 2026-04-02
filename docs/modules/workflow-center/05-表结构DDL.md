# 工作流中心 — 表结构 DDL

> 本文档描述 workflow-center 模块管理的所有数据表结构，包括自有表和 Flowable 引擎表。
> DDL 源文件：`docs/schema/ddl-workflow.sql`

---

## 1. 表清单

| 表名 | 说明 | 主键策略 | 所属 |
|---|---|---|---|
| `biz_process_map` | 业务流程映射表 — 业务实体与 Flowable 流程实例的桥接 | UUID(32位字符串) | 自有 |
| `wf_node_candidate_conf` | 节点候选人配置 — 定义每个流程节点的候选处理角色/机构/用户 | UUID(32位字符串) | 自有 |
| `wf_node_form_conf` | 节点表单配置 — 定义每个流程节点的审批表单字段权限 | UUID(32位字符串) | 自有 |
| `wf_timeout_rule` | 超时规则 — 定义每个流程节点的黄灯/红灯超时阈值 | UUID(32位字符串) | 自有 |
| `ACT_GE_*` | Flowable 通用表（属性/字节数组） | 引擎管理 | Flowable |
| `ACT_RE_*` | Flowable 仓库表（流程定义/部署） | 引擎管理 | Flowable |
| `ACT_RU_*` | Flowable 运行时表（执行/任务/变量/事件订阅等） | 引擎管理 | Flowable |
| `ACT_HI_*` | Flowable 历史表（历史流程/任务/变量/评论等） | 引擎管理 | Flowable |

---

## 2. 自有表完整 DDL

### 2.1 biz_process_map（业务流程映射表）

**用途**：桥接业务实体与 Flowable 流程实例，实现"一个业务实体最多对应一个运行中流程"的约束。所有业务模块通过 `WorkflowApi` 发起流程时自动写入此表。

```sql
CREATE TABLE IF NOT EXISTS `biz_process_map` (
  `id`                    varchar(32)   NOT NULL                          COMMENT '映射ID',
  `business_key`          varchar(100)  NOT NULL                          COMMENT '业务键（格式：BIZ_TYPE:{id}）',
  `biz_type`              varchar(50)   NOT NULL                          COMMENT '业务类型',
  `biz_id`                varchar(100)  NOT NULL                          COMMENT '业务ID',
  `process_definition_key` varchar(100) NOT NULL                          COMMENT '流程定义KEY',
  `process_instance_id`   varchar(64)   NOT NULL                          COMMENT 'Flowable流程实例ID',
  `start_user`            varchar(32)   NOT NULL                          COMMENT '发起人工号',
  `current_assignee`      varchar(32)   DEFAULT NULL                      COMMENT '当前处理人工号',
  `candidate_groups`      text                                            COMMENT '候选组列表（JSON数组）',
  `process_status`        varchar(50)   DEFAULT 'RUNNING'                 COMMENT '流程状态：RUNNING-运行中, COMPLETED-已完成, CANCELLED-已取消',
  `start_time`            datetime      DEFAULT CURRENT_TIMESTAMP         COMMENT '发起时间',
  `end_time`              datetime      DEFAULT NULL                      COMMENT '结束时间',
  `created_time`          datetime      DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `updated_time`          datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_key` (`business_key`),
  UNIQUE KEY `uk_process_instance` (`process_instance_id`),
  KEY `idx_biz_type_id` (`biz_type`, `biz_id`),
  KEY `idx_start_user` (`start_user`),
  KEY `idx_current_assignee` (`current_assignee`),
  KEY `idx_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务流程映射表';
```

**字段清单**：

| 字段名 | 类型 | NOT NULL | DEFAULT | 说明 |
|---|---|---|---|---|
| `id` | varchar(32) | YES | - | 映射ID，UUID生成 |
| `business_key` | varchar(100) | YES | - | 业务键，格式 `BIZ_TYPE:{id}`，如 `LOAN:LA202603060001` |
| `biz_type` | varchar(50) | YES | - | 业务类型，对应 `BizType` 枚举值 |
| `biz_id` | varchar(100) | YES | - | 业务ID，具体业务实体的主键 |
| `process_definition_key` | varchar(100) | YES | - | Flowable 流程定义 KEY，如 `loan_approve_v1` |
| `process_instance_id` | varchar(64) | YES | - | Flowable 流程实例ID，由引擎生成 |
| `start_user` | varchar(32) | YES | - | 发起人工号，关联 `PT_USER.USER_ID` |
| `current_assignee` | varchar(32) | NO | NULL | 当前处理人工号，签收后填入，关联 `PT_USER.USER_ID` |
| `candidate_groups` | text | NO | NULL | 候选组列表，JSON数组格式，如 `["ROLE:BRANCH_HEAD"]` |
| `process_status` | varchar(50) | NO | 'RUNNING' | 流程状态枚举：`RUNNING` / `COMPLETED` / `CANCELLED` |
| `start_time` | datetime | NO | CURRENT_TIMESTAMP | 流程发起时间 |
| `end_time` | datetime | NO | NULL | 流程结束时间，完成或取消时填入 |
| `created_time` | datetime | NO | CURRENT_TIMESTAMP | 记录创建时间 |
| `updated_time` | datetime | NO | CURRENT_TIMESTAMP ON UPDATE | 记录最后更新时间 |

---

### 2.2 wf_node_candidate_conf（流程节点候选人配置表）

**用途**：配置每个流程定义的每个用户任务节点的候选处理人规则。支持按角色(ROLE)、机构(ORG)、指定用户(USER)三种方式配置。Flowable `TaskListener` 在任务创建时读取此配置，动态设置 `candidateGroups` 或 `candidateUsers`。

```sql
CREATE TABLE IF NOT EXISTS `wf_node_candidate_conf` (
  `id`                      varchar(32)   NOT NULL                          COMMENT '配置ID',
  `process_definition_key`  varchar(100)  NOT NULL                          COMMENT '流程定义KEY',
  `node_key`                varchar(100)  NOT NULL                          COMMENT '节点KEY',
  `candidate_type`          varchar(50)   NOT NULL                          COMMENT '候选类型：ROLE-角色, ORG-机构, USER-指定用户',
  `candidate_value`         text                                            COMMENT '候选值（JSON数组）',
  `created_time`            datetime      DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `updated_time`            datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pd_node_type` (`process_definition_key`, `node_key`, `candidate_type`),
  KEY `idx_process_node` (`process_definition_key`, `node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点候选人配置表';
```

**字段清单**：

| 字段名 | 类型 | NOT NULL | DEFAULT | 说明 |
|---|---|---|---|---|
| `id` | varchar(32) | YES | - | 配置ID，UUID生成 |
| `process_definition_key` | varchar(100) | YES | - | 流程定义KEY，关联 `ACT_RE_PROCDEF.KEY_` |
| `node_key` | varchar(100) | YES | - | 节点KEY，对应 BPMN 中 UserTask 的 `id` 属性 |
| `candidate_type` | varchar(50) | YES | - | 候选类型枚举：`ROLE` / `ORG` / `USER` |
| `candidate_value` | text | NO | NULL | 候选值，JSON数组，如 `["BRANCH_HEAD"]`、`["ORG_001"]`、`["E10001","E10002"]` |
| `created_time` | datetime | NO | CURRENT_TIMESTAMP | 记录创建时间 |
| `updated_time` | datetime | NO | CURRENT_TIMESTAMP ON UPDATE | 记录最后更新时间 |

**`candidate_value` JSON 格式说明**：

| candidate_type | candidate_value 示例 | 含义 |
|---|---|---|
| `ROLE` | `["BRANCH_HEAD"]` | 拥有 `BRANCH_HEAD` 角色的用户为候选人 |
| `ROLE` | `["CORP_DEPT","RETAIL_DEPT"]` | 拥有 `CORP_DEPT` 或 `RETAIL_DEPT` 角色的用户为候选人 |
| `ORG` | `["ORG_001","ORG_002"]` | 属于指定机构的用户为候选人 |
| `USER` | `["E10001","E10002"]` | 指定工号的用户为候选人 |

---

### 2.3 wf_node_form_conf（流程节点表单配置表）

**用途**：配置每个流程节点的审批表单字段定义、可编辑字段和必填字段。前端根据此配置动态渲染审批表单，后端根据此配置校验提交数据。

```sql
CREATE TABLE IF NOT EXISTS `wf_node_form_conf` (
  `id`                      varchar(32)   NOT NULL                          COMMENT '配置ID',
  `process_definition_key`  varchar(100)  NOT NULL                          COMMENT '流程定义KEY',
  `node_key`                varchar(100)  NOT NULL                          COMMENT '节点KEY',
  `form_fields`             text                                            COMMENT '表单字段配置（JSON数组）',
  `editable_fields`         text                                            COMMENT '可编辑字段列表（JSON数组）',
  `required_fields`         text                                            COMMENT '必填字段列表（JSON数组）',
  `created_time`            datetime      DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `updated_time`            datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`, `node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点表单配置表';
```

**字段清单**：

| 字段名 | 类型 | NOT NULL | DEFAULT | 说明 |
|---|---|---|---|---|
| `id` | varchar(32) | YES | - | 配置ID，UUID生成 |
| `process_definition_key` | varchar(100) | YES | - | 流程定义KEY |
| `node_key` | varchar(100) | YES | - | 节点KEY |
| `form_fields` | text | NO | NULL | 表单字段定义，JSON数组 |
| `editable_fields` | text | NO | NULL | 可编辑字段KEY列表，JSON数组 |
| `required_fields` | text | NO | NULL | 必填字段KEY列表，JSON数组（必须是 editable_fields 的子集） |
| `created_time` | datetime | NO | CURRENT_TIMESTAMP | 记录创建时间 |
| `updated_time` | datetime | NO | CURRENT_TIMESTAMP ON UPDATE | 记录最后更新时间 |

**`form_fields` JSON 结构**：

```json
[
  {
    "key": "bmOpinion",
    "label": "机构负责人意见",
    "type": "TEXTAREA"
  },
  {
    "key": "riskLevel",
    "label": "风险等级",
    "type": "SELECT",
    "dictType": "RISK_LEVEL"
  },
  {
    "key": "creditCheckResult",
    "label": "审查结论",
    "type": "SELECT",
    "options": [
      {"label": "通过", "value": "PASS"},
      {"label": "补充材料", "value": "SUPPLEMENT"},
      {"label": "拒绝", "value": "REJECT"}
    ]
  },
  {
    "key": "visitPhotoUrls",
    "label": "拜访照片",
    "type": "FILE_LIST"
  },
  {
    "key": "assignedEmpId",
    "label": "指定支持人员",
    "type": "USER_SELECT"
  }
]
```

**支持的字段类型**：

| type | 说明 | 额外属性 |
|---|---|---|
| `TEXTAREA` | 多行文本框 | 无 |
| `SELECT` | 下拉选择 | `dictType`（关联字典）或 `options`（静态选项） |
| `RADIO` | 单选按钮 | `dictType` |
| `NUMBER` | 数字输入 | 无 |
| `FILE_LIST` | 文件列表（MinIO） | 无 |
| `USER_SELECT` | 用户选择器 | 无 |

---

### 2.4 wf_timeout_rule（流程超时规则表）

**用途**：配置每个流程节点的超时阈值。定时任务每30分钟扫描运行中的任务，根据此配置计算红绿灯状态（绿灯=正常、黄灯=预警、红灯=超时）。超时计算使用工作日小时（需调用 `CalendarApi`）。

```sql
CREATE TABLE IF NOT EXISTS `wf_timeout_rule` (
  `id`                      varchar(32)   NOT NULL                          COMMENT '规则ID',
  `process_definition_key`  varchar(100)  NOT NULL                          COMMENT '流程定义KEY',
  `node_key`                varchar(100)  NOT NULL                          COMMENT '节点KEY',
  `timeout_hours`           int(11)       NOT NULL                          COMMENT '超时小时数（红灯阈值）',
  `warning_hours`           int(11)       DEFAULT NULL                      COMMENT '预警小时数（黄灯阈值）',
  `created_time`            datetime      DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `updated_time`            datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`, `node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程超时规则表';
```

**字段清单**：

| 字段名 | 类型 | NOT NULL | DEFAULT | 说明 |
|---|---|---|---|---|
| `id` | varchar(32) | YES | - | 规则ID，UUID生成 |
| `process_definition_key` | varchar(100) | YES | - | 流程定义KEY |
| `node_key` | varchar(100) | YES | - | 节点KEY |
| `timeout_hours` | int(11) | YES | - | 红灯阈值，单位：小时 |
| `warning_hours` | int(11) | NO | NULL | 黄灯阈值，单位：小时 |
| `created_time` | datetime | NO | CURRENT_TIMESTAMP | 记录创建时间 |
| `updated_time` | datetime | NO | CURRENT_TIMESTAMP ON UPDATE | 记录最后更新时间 |

**红绿灯逻辑**：
- 已耗工时 < `warning_hours` → 绿灯（正常）
- `warning_hours` <= 已耗工时 < `timeout_hours` → 黄灯（预警）
- 已耗工时 >= `timeout_hours` → 红灯（超时）

---

## 3. 索引说明

### 3.1 biz_process_map 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | 主键索引 |
| `uk_business_key` | `business_key` | 唯一索引 | 防止同一业务实体重复发起流程；按业务键精确查询 |
| `uk_process_instance` | `process_instance_id` | 唯一索引 | 防止同一流程实例重复映射；Flowable 事件回调时按流程实例ID查找 |
| `idx_biz_type_id` | `biz_type`, `biz_id` | 普通索引 | 按业务类型+业务ID查询流程状态（业务模块查询用） |
| `idx_start_user` | `start_user` | 普通索引 | 查询"我发起的"流程列表 |
| `idx_current_assignee` | `current_assignee` | 普通索引 | 查询"我待办的"流程列表（签收后） |
| `idx_status` | `process_status` | 普通索引 | 按流程状态筛选（运行中/已完成/已取消） |

### 3.2 wf_node_candidate_conf 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | 主键索引 |
| `uk_pd_node_type` | `process_definition_key`, `node_key`, `candidate_type` | 唯一索引 | 同一流程同一节点同一候选类型只允许一条配置 |
| `idx_process_node` | `process_definition_key`, `node_key` | 普通索引 | TaskListener 创建任务时按流程+节点查询所有候选配置 |

### 3.3 wf_node_form_conf 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | 主键索引 |
| `uk_process_node` | `process_definition_key`, `node_key` | 唯一索引 | 同一流程同一节点只允许一套表单配置 |

### 3.4 wf_timeout_rule 索引

| 索引名 | 字段 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | 主键索引 |
| `uk_process_node` | `process_definition_key`, `node_key` | 唯一索引 | 同一流程同一节点只允许一条超时规则 |

---

## 4. 逻辑外键

> 本项目不使用物理外键约束，以下为逻辑关联关系，需在应用层保证参照完整性。

| 表 | 字段 | 关联表 | 关联字段 | 关联类型 | 说明 |
|---|---|---|---|---|---|
| `biz_process_map` | `start_user` | `PT_USER` | `USER_ID` | N:1 | 发起人，流程发起时写入 |
| `biz_process_map` | `current_assignee` | `PT_USER` | `USER_ID` | N:1 | 当前处理人，签收(Claim)时写入 |
| `biz_process_map` | `process_instance_id` | `ACT_RU_EXECUTION` / `ACT_HI_PROCINST` | `PROC_INST_ID_` | 1:1 | 流程实例关联，运行时查 `ACT_RU_*`，历史查 `ACT_HI_*` |
| `biz_process_map` | `process_definition_key` | `ACT_RE_PROCDEF` | `KEY_` | N:1 | 流程定义关联 |
| `biz_process_map` | `biz_type` + `biz_id` | 各业务主表 | 主键 | N:1 | 业务实体关联（如线索表、资产投放表等） |
| `wf_node_candidate_conf` | `process_definition_key` | `ACT_RE_PROCDEF` | `KEY_` | N:1 | 流程定义关联 |
| `wf_node_candidate_conf` | `candidate_value`(ROLE) | `PT_ROLE` | `ROLE_CODE` | N:M(JSON) | 角色编码关联 |
| `wf_node_form_conf` | `process_definition_key` | `ACT_RE_PROCDEF` | `KEY_` | N:1 | 流程定义关联 |
| `wf_timeout_rule` | `process_definition_key` | `ACT_RE_PROCDEF` | `KEY_` | N:1 | 流程定义关联 |

---

## 5. Flowable 引擎表说明

Flowable 7.0.1 嵌入式部署时，由引擎自动创建和管理以下表。**严禁**在业务代码中直接操作这些表，必须通过 Flowable Service API 访问。

### 5.1 表分类

| 分类 | 前缀 | 说明 | V1 必需 |
|---|---|---|---|
| 通用表 | `ACT_GE_` | 通用数据表，存储属性配置和字节数组资源 | 是 |
| 仓库表 | `ACT_RE_` | 存储流程定义(PROCDEF)、部署(DEPLOYMENT)等静态数据 | 是 |
| 运行时表 | `ACT_RU_` | 存储运行中的流程实例、任务、变量、事件订阅等 | 是 |
| 历史表 | `ACT_HI_` | 存储已完成的流程实例、任务、变量、评论等历史数据 | 是 |
| 身份表 | `ACT_ID_*` | 用户/组身份表 — **V1 不使用**（由 auth-permission-center 管理） | 否 |

### 5.2 通用表 ACT_GE_*

| 表名 | 说明 |
|---|---|
| `ACT_GE_PROPERTY` | 引擎属性表，存储版本号、schema版本等 |
| `ACT_GE_BYTEARRAY` | 字节数组资源表，存储 BPMN XML、流程图片等二进制资源 |

### 5.3 仓库表 ACT_RE_*

| 表名 | 说明 |
|---|---|
| `ACT_RE_DEPLOYMENT` | 部署记录表，每次部署 BPMN 文件生成一条记录 |
| `ACT_RE_PROCDEF` | 流程定义表，每个 BPMN 文件解析后生成流程定义（含 KEY_、VERSION_、DEPLOYMENT_ID_） |
| `ACT_RE_MODEL` | 模型表（V1 不使用在线建模，但表会自动创建） |

### 5.4 运行时表 ACT_RU_*

| 表名 | 说明 |
|---|---|
| `ACT_RU_EXECUTION` | 执行实例表，存储流程实例和执行路径 |
| `ACT_RU_TASK` | 运行时任务表，存储当前待办任务 |
| `ACT_RU_VARIABLE` | 运行时变量表，存储流程变量 |
| `ACT_RU_IDENTITYLINK` | 运行时身份关联表，存储任务的 `assignee`/`candidateUser`/`candidateGroup` |
| `ACT_RU_EVENT_SUBSCR` | 事件订阅表，存储信号/消息事件订阅 |
| `ACT_RU_TIMER_JOB` | 定时任务表（引擎内置超时边界事件使用） |
| `ACT_RU_DEADLETTER_JOB` | 死信任务表，存储执行失败的异步任务 |
| `ACT_RU_SUSPENDED_JOB` | 挂起任务表 |

### 5.5 历史表 ACT_HI_*

| 表名 | 说明 | history level = audit |
|---|---|---|
| `ACT_HI_PROCINST` | 历史流程实例表 | 记录 |
| `ACT_HI_ACTINST` | 历史活动实例表（节点级别） | 记录 |
| `ACT_HI_TASKINST` | 历史任务实例表 | 记录 |
| `ACT_HI_VARINST` | 历史变量表 | 记录 |
| `ACT_HI_IDENTITYLINK` | 历史身份关联表 | 记录 |
| `ACT_HI_COMMENT` | 历史评论表（审批意见存储） | 记录 |
| `ACT_HI_DETAIL` | 历史明细表（变量更新明细） | **不记录**（audit 级别不记录 detail） |

### 5.6 history level = audit 的影响

项目配置 `flowable.history-level=audit`，影响如下：

| 级别 | 记录内容 |
|---|---|
| `none` | 不记录任何历史数据 |
| `activity` | 记录流程实例 + 活动实例 |
| **`audit`（当前）** | 记录流程实例 + 活动实例 + 任务实例 + 变量实例 + 身份关联 + 评论 |
| `full` | audit 基础上增加变量更新明细（ACT_HI_DETAIL） |

**选择 `audit` 的原因**：
1. 满足审批历史追溯需求（审批意见、处理人、处理时间均可追溯）
2. 不记录变量更新明细，避免历史表膨胀
3. 银行合规审计要求留存审批全链路记录

---

## 6. 审计字段规范

所有自有表统一包含以下审计字段：

| 字段名 | 类型 | NOT NULL | DEFAULT | 说明 |
|---|---|---|---|---|
| `created_time` | datetime | NO | `CURRENT_TIMESTAMP` | 记录创建时间，INSERT 时由数据库自动填充 |
| `updated_time` | datetime | NO | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 记录最后更新时间，UPDATE 时由数据库自动更新 |

**设计决策**：
- V1 版本未加 `created_by` / `updated_by` 字段，原因是自有表的写入主要通过系统内部（流程引擎回调、定时任务），非直接的用户操作
- 配置类表（`wf_node_candidate_conf`、`wf_node_form_conf`、`wf_timeout_rule`）的变更通过审计日志系统追踪操作人
- `biz_process_map` 的 `start_user` 字段兼具审计作用

---

## 7. 关键字段取值枚举

### 7.1 process_status（流程状态）

| 枚举值 | 说明 | 写入时机 |
|---|---|---|
| `RUNNING` | 运行中 | 流程发起时默认状态 |
| `COMPLETED` | 已完成 | 流程正常结束（末节点审批通过）时由 ProcessEndListener 更新 |
| `CANCELLED` | 已取消 | 流程被撤销/驳回终止时由业务操作更新 |

### 7.2 candidate_type（候选类型）

| 枚举值 | 说明 | candidate_value 格式 |
|---|---|---|
| `ROLE` | 按角色匹配候选人 | JSON数组，元素为 `PT_ROLE.ROLE_CODE`，如 `["BRANCH_HEAD"]` |
| `ORG` | 按机构匹配候选人 | JSON数组，元素为 `EXT_ORG_INFO.ORG_CODE`，如 `["ORG_001"]` |
| `USER` | 指定用户为候选人 | JSON数组，元素为 `PT_USER.USER_ID`，如 `["E10001"]` |

### 7.3 biz_type（业务类型）

`biz_process_map.biz_type` 与 `common-security` 中 `BizType` 枚举对齐：

| biz_type 值 | 说明 | 对应 process_definition_key |
|---|---|---|
| `LEAD` | 线索 | `lead_approve_v1` |
| `LEAD_IMPORT` | 线索批量导入 | `lead_import_approve_v1` |
| `LEAD_DELETE` | 线索删除 | `lead_delete_approve_v1` |
| `TOUCH` | 触达任务 | `touch_process_v1` |
| `LOAN` | 资产投放 | `loan_approve_v1` |
| `SUPPORT_SIMPLE` | 中场支持(产品直达) | `support_simple_v1` |
| `SUPPORT_COMPLEX` | 中场支持(部门承接) | `support_complex_v1` |
| `TARGET_ADJUST` | 目标修正 | `target_adjust_approve_v1` |
| `ALLOC_ADJUST` | 分配关系调整 | `alloc_adjust_approve_v1` |

### 7.4 business_key 格式规范

`business_key` 格式为 `{BIZ_TYPE}:{biz_id}`，示例：

| business_key | 说明 |
|---|---|
| `LEAD:LD202603060001` | 线索审批 |
| `LEAD_IMPORT:LI202603060001` | 线索批量导入审批 |
| `LEAD_DELETE:LDDEL202603060001` | 线索删除审批 |
| `LOAN:LA202603060001` | 资产投放审批 |
| `SUPPORT_SIMPLE:SS202603060001` | 中场支持(产品直达) |
| `SUPPORT_COMPLEX:SC202603060001` | 中场支持(部门承接) |
| `TARGET_ADJUST:TA202603060001` | 目标修正审批 |
| `ALLOC_ADJUST:AA202603060001` | 分配关系调整审批 |
