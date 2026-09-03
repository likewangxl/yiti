# 系统治理中心数据模型说明

> 本文件描述当前实现使用的数据模型，不是可执行结构脚本。目标库实际 schema 和已批准的 DBA 记录是结构权威；任何结构调整都必须走独立审批和实施流程。

## 1. 归属与公共约定

治理中心维护下表业务数据及 Quartz 持久化表；`PT_USER`、`EXT_USER_ORG`、`EXT_ORG_INFO` 和指标计算任务表由其他模块维护，治理只通过公开 API 或只读关联查询使用。

治理表的审计字段按实体实际定义使用：有 `created_by/created_time` 的记录在创建时填充；支持更新的表另有 `updated_by/updated_time`。日志、通知和任务运行记录没有通用更新字段时，按其专属状态更新语义处理。

## 2. 字典与日历

### 2.1 `SYS_DICT`

| 字段 | 语义 |
| --- | --- |
| `id` | 字典项主键 |
| `dict_type` | 字典类型编码 |
| `dict_code` | 类型内编码 |
| `dict_label` | 展示标签 |
| `dict_value` | 存储/传输值 |
| `sort_order` | 展示顺序 |
| `status` | `ACTIVE`/`DISABLED` |
| `remark` | 备注 |
| `created_by/created_time` | 创建审计 |
| `updated_by/updated_time` | 更新审计 |

约束：`(dict_type, dict_code)` 唯一；按 `dict_type` 查询启用项。禁用是逻辑状态，不删除字典项记录。

### 2.2 `SYS_CALENDAR_DAY`

| 字段 | 语义 |
| --- | --- |
| `day` | 日期自然主键 |
| `is_workday` | `1` 工作日，`0` 休息日 |
| `remark` | 节假日/调休备注 |
| `created_by/created_time` | 创建审计 |
| `updated_by/updated_time` | 更新审计 |

缺失日期由服务按周规则判断；管理端写入会创建或更新记录。日历表按日期范围查询，不额外拆分类型表。

## 3. 调度与配置

### 3.1 `SYS_JOB_CONF`

| 字段 | 语义 |
| --- | --- |
| `id` | 任务配置主键 |
| `job_key` | 全局唯一任务标识 |
| `job_name` | 展示名称 |
| `cron_expr` | Quartz Cron 表达式 |
| `quartz_job_class` | Job 实现类全限定名 |
| `misfire_policy` | `FIRE_ONCE_NOW`/`DO_NOTHING`/`IGNORE_MISFIRE_POLICY` |
| `status` | `ACTIVE`/`PAUSED` |
| `allow_manual_trigger` | `1` 允许，`0` 禁止 |
| `last_run_time/next_run_time` | 最近/计划执行时间 |
| `remark` | 备注 |
| `created_by/created_time`、`updated_by/updated_time` | 审计字段 |

`job_key` 唯一。Quartz JobDetail、CronTrigger 与该表的配置由 `JobService` 同步；表中不存在固定 Job 数量的约束。

### 3.2 `SYS_JOB_RUN_LOG`

| 字段 | 语义 |
| --- | --- |
| `id` | 执行日志主键 |
| `job_id` | 对应 `SYS_JOB_CONF.id` |
| `trigger_type` | `SCHEDULED`/`MANUAL`（协调调用可传 `AUTO`，监听器负责记录实际触发类型） |
| `reason` | 触发原因 |
| `start_time/end_time` | 执行起止时间 |
| `status` | `RUNNING`/`SUCCESS`/`FAILED` |
| `error_msg` | 失败信息 |
| `created_by/created_time` | 触发人和创建时间 |
| `scheduled_fire_time` | Quartz 计划触发时间 |

`processStatus` 是查询 DTO 的非本表字段，可由指标计算任务关联得到；不得作为本表列写入。

### 3.3 `SYS_CONFIG_KV`

| 字段 | 语义 |
| --- | --- |
| `id` | 配置主键 |
| `config_key` | 全局唯一配置键 |
| `config_value` | 配置原文（长文本） |
| `value_type` | `STRING`/`JSON`/`NUMBER`/`BOOL` |
| `status` | `ACTIVE`/`DISABLED` |
| `remark` | 备注 |
| `created_by/created_time`、`updated_by/updated_time` | 审计字段 |

配置值的类型转换由 `ConfigService` 负责；凭据类配置的可见性和日志脱敏由权限与安全规范约束。

## 4. 通知、文件与关联

### 4.1 `USER_NOTIFICATION`

| 字段 | 语义 |
| --- | --- |
| `id` | 通知主键 |
| `emp_id` | 接收人工号 |
| `title`、`content` | 标题和内容 |
| `notify_type` | `SYSTEM`/`WORKFLOW`/`BUSINESS` |
| `biz_type`、`biz_id`、`link_url` | 可选业务关联和跳转 |
| `is_read`、`read_time` | `0/1` 已读状态和时间 |
| `created_time` | 创建时间 |

查询索引应覆盖接收人工号及已读条件；已读状态通过 Service 的用户范围校验后更新。

### 4.2 `FILE_OBJECT`

| 字段 | 语义 |
| --- | --- |
| `id` | 文件对象主键 |
| `file_name` | 原始文件名 |
| `file_size` | 字节数 |
| `file_type` | MIME 类型或扩展信息 |
| `storage_path` | OBS 对象 key |
| `bucket_name` | OBS 桶标识 |
| `md5_hash` | 内容摘要，用于去重 |
| `uploaded_by`、`uploaded_time` | 上传人和时间 |

内容不存数据库，数据库仅保存元数据；对象生命周期由 `FileService` 与 `ObsStorageClient` 协同处理。

### 4.3 `BIZ_FILE_REL`

| 字段 | 语义 |
| --- | --- |
| `id` | 关联主键 |
| `biz_type`、`biz_id` | 业务对象标识 |
| `file_object_id` | 对应 `FILE_OBJECT.id` |
| `file_role` | 附件用途，可为空 |
| `created_by`、`created_time` | 关联审计 |

约束：`(biz_type, biz_id, file_object_id)` 唯一，保证绑定幂等。解绑只删除关联，删除文件对象时才清理其全部关联。

## 5. 审计与标签

### 5.1 `AUDIT_LOG`

| 字段 | 语义 |
| --- | --- |
| `id` | 日志主键 |
| `trace_id`、`emp_id`、`emp_name` | 链路和操作人 |
| `biz_type`、`biz_action` | 业务类型和动作 |
| `resource_url`、`request_method` | 请求资源 |
| `request_params` | 脱敏请求参数 |
| `response_status`、`error_msg`、`execution_time` | 结果、错误和耗时 |
| `ip_address`、`user_agent` | 请求环境 |
| `reason` | 操作原因 |
| `target_type`、`target_id` | 结构化目标 |
| `before_snapshot`、`after_snapshot` | 前后状态快照 |
| `added_items`、`removed_items` | 变更集合 |
| `created_time` | 写入时间 |

审计记录为追加型数据；只有 `created_time`，不提供治理接口修改/删除。结构化字段和自由文本都必须做脱敏、长度控制。

### 5.2 `PERSON_TAG`

| 字段 | 语义 |
| --- | --- |
| `TAG_ID` | 自增标签主键 |
| `TAG_NAME` | 全局唯一标签名称 |
| `REMARK` | 备注 |
| `CREATE_BY`、`CREATE_TIME` | 创建审计 |
| `UPDATE_BY`、`UPDATE_TIME` | 更新审计 |

### 5.3 `PERSON_TAG_REL`

| 字段 | 语义 |
| --- | --- |
| `ID` | 自增关联主键 |
| `TAG_ID` | 对应 `PERSON_TAG.TAG_ID` |
| `DIM_TYPE` | `EMP` 或 `ORG` |
| `USERNAME` | EMP 维度的 `PT_USER.USERNAME` |
| `ORG_DEPT_NO` | ORG 维度的 `EXT_ORG_INFO.DEPT_NO` |
| `CREATE_BY`、`CREATE_TIME` | 创建审计 |

EMP 行只填 `USERNAME`，ORG 行只填 `ORG_DEPT_NO`；两列均不存展示名称。标签+员工、标签+机构业务编号分别具备唯一约束，删除标签级联清理关联。

## 6. 外部持有表与 Quartz 表

- `PT_USER`、`EXT_USER_ORG`、`EXT_ORG_INFO` 是 auth/组织域的外部数据源；治理只保存标签关联所需的工号/机构业务编号，并通过公开 API 校验和解析名称。
- 指标任务状态等 `processStatus` 来源于性能域查询，不改变治理运行日志模型。
- `QRTZ_*` 是 Quartz JDBC JobStore 的持久化表，由 Quartz 官方 schema 管理；治理代码不得把它们当作业务表直接改写。
- Spring Session 表由 Session 基础设施管理；治理仅在清理任务中按配置引用，不负责初始化或重建。

## 7. 数据完整性要求

1. 自有表的唯一约束、自然主键和外键语义以目标库实际结构为准，Service 仍必须做存在性和范围校验。
2. 写入前后的审计字段、状态值和关联维度必须符合 API 契约；未知状态拒绝写入。
3. 结构实施、数据修复和权限资源调整不在本文件或模块交付物中附带 SQL；执行前后分别按 DBA 审批和只读验收流程留证。
