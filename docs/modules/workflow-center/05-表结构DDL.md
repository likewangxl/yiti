# 工作流中心——数据模型

## 1. 使用边界

本文是当前实体和表关系的模型说明，不是可执行 DDL，也不规定一次性初始化或执行顺序。
目标库实际 schema、索引、字符集和约束由 DBA/目标库只读核对结果决定；不得把本文或仓库
中的历史脚本直接当作生产变更。

Flowable `ACT_*` 表由引擎管理，工作流中心不定义、不直接写入；业务模块不得跨边界读取。
下表中的列名按当前实体/查询语义表达，具体长度和数据库类型以目标库为准。

## 2. 业务流程映射

### `BIZ_PROCESS_MAP`

一条业务对象与一个流程实例的映射记录，工作流中心是唯一写入方。

| 字段 | 语义 |
| --- | --- |
| `id` | 映射主键 |
| `business_key` | 业务唯一键，推荐 `BIZ_TYPE:bizId` |
| `biz_type`、`biz_id` | 业务类型和业务对象 ID |
| `process_definition_key` | 启动时使用的 Flowable 定义键；设计器流程为已发布 `DSN_` 键 |
| `process_instance_id` | Flowable 流程实例 ID |
| `start_user`、`current_assignee` | 发起人和当前处理员工标识，可为空的仅为当前处理人 |
| `candidate_groups` | 当前/最近候选组快照，按当前序列化约定保存 |
| `process_status` | `RUNNING`、`COMPLETED`、`CANCELLED` |
| `title` | 流程展示标题 |
| `start_time`、`end_time` | 流程开始/结束时间 |
| `created_time`、`updated_time` | 本地记录审计时间 |

业务键和流程实例 ID 应有唯一约束，业务类型/ID、状态、发起人、当前处理人需要支持查询。
启动服务通过业务键锁定和状态守卫防止并发重复运行；终态重启沿用业务键映射策略，不能
创建无法追踪的旁路记录。

## 3. 节点运行配置

### `WF_NODE_CANDIDATE_CONF`

按流程定义键和节点键维护候选规则：

| 字段 | 语义 |
| --- | --- |
| `id` | 配置主键 |
| `process_definition_key`、`node_key` | 目标部署定义和用户任务节点 |
| `candidate_type` | 候选规则类型，如 `ROLE`、`ORG`、`USER`；设计器还可表达层级角色/流程变量 |
| `candidate_value` | 规则值或序列化值，由服务解析，不是权限快照 |
| `approve_org_scope` | 审批机构范围，如 `SELF`、`PARENT` 或空 |
| `org_code` | 可选机构限定 |
| `created_time`、`updated_time` | 配置审计时间 |

配置唯一性至少按流程定义、节点和规则类型控制。运行时身份链接优先；配置只作为受控
来源或无身份链接时的兜底。`approve_org_scope`、`org_code` 已在当前实体承载，仓库中的
历史 schema 描述可能未同步，实施前必须以目标库核对，不在此文自行补 DDL。

### `WF_NODE_FORM_CONF`

按流程定义/节点唯一保存节点表单规则：

| 字段 | 语义 |
| --- | --- |
| `id` | 配置主键 |
| `process_definition_key`、`node_key` | 目标流程和节点 |
| `form_fields` | 表单字段定义序列化值 |
| `editable_fields` | 可编辑字段键集合 |
| `required_fields` | 必填字段键集合 |
| `created_time`、`updated_time` | 配置审计时间 |

前端用这些字段渲染节点面板；业务模块仍负责业务领域校验。审批服务将允许的表单/路由
值写成流程变量，不应让客户端直接写入此表。

### `WF_TIMEOUT_RULE`

按流程定义/节点保存 `timeout_hours`、`warning_hours` 及主键、审计时间。规则由查询时
结合治理 `CalendarApi` 计算 `GREEN`/`YELLOW`/`RED`；无规则为 `GREEN`。工作流中心不
创建独立调度表、缓存或本地日历。

## 4. 设计器图模型

设计器草稿拆分为以下本模块表，均由工作流中心维护：

| 表 | 核心字段/关系 |
| --- | --- |
| `WF_FLOW_DEF` | `id`、`flow_key`、`biz_type`、`name`、`description`、`status`（`DRAFT`/`PUBLISHED`/`DISABLED`）、`version`、部署定义键/ID、来源定义键、只读导入标记、创建/更新人和时间 |
| `WF_FLOW_NODE` | `id`、`flow_def_id`、`node_key`、`node_type`（`START`/`APPROVAL`/`GATEWAY`/`END`）、名称、审批模式、机构范围、排序和画布坐标 |
| `WF_FLOW_EDGE` | `id`、`flow_def_id`、起止节点、名称、默认标记、条件 JSON、排序和创建时间 |
| `WF_FLOW_NODE_APPROVER` | 节点 ID、审批人类型/值、机构范围、角色编码、排序和创建时间 |

`node_key` 在图内唯一，边只引用同一流程图节点。审批模式保留 `ANY`、`ALL`、`GROUP_ALL`
语义；条件只引用变量目录允许的流程变量。发布生成 `DSN_<flowKey>` 定义并保存部署
元数据；保存采用整图替换，已发布导入定义只读。

## 5. 流程机构和转交

### `WF_PROCESS_ORG`

记录流程涉及的机构快照：

| 字段 | 语义 |
| --- | --- |
| `id` | 快照主键 |
| `process_instance_id` | Flowable 流程实例 ID |
| `org_code` | 参与机构编码 |
| `source` | 运行时发现来源，如启动、分配、签收、审批、转交 |
| `first_seen_time` | 首次发现时间 |

`(process_instance_id, org_code)` 应唯一。`WfProcessOrgService` 是唯一写入口并采用幂等
插入；快照写入失败不能回滚主流程命令。监控服务用该表结合平台数据范围过滤流程。

### `WF_TASK_TRANSFER`

记录任务转交的两阶段生命周期：

| 字段 | 语义 |
| --- | --- |
| `id` | 转交主键 |
| `process_instance_id`、`task_id` | 流程和运行时任务 ID |
| `business_key`、`biz_type` | 冗余展示和查询键 |
| `node_key`、`node_name` | 发起时节点快照 |
| `from_emp_id` | 原处理人；未签收定向分配时可空 |
| `initiator_emp_id`、`to_emp_id` | 发起人和接收人 |
| `org_code` | 转交组织边界 |
| `status` | `PENDING_ACCEPT`、`ACCEPTED`、`REJECTED`、`CANCELLED`、`INVALIDATED` |
| `transfer_reason`、`reject_reason` | 发起原因和拒绝原因 |
| `initiated_time`、`decided_time` | 发起和决策时间 |

同一活动任务最多允许一条 `PENDING_ACCEPT`；可用条件唯一键/生成列或等价数据库约束实现，
具体实现以目标库核对结果为准。终态记录可以保留用于历史，状态决策必须带当前状态条件。

## 6. 引擎表和一致性

- `ACT_RU_*` 保存运行中实例、任务、身份链接和变量；由 Flowable 服务管理；
- `ACT_HI_*` 保存审计级历史任务、活动、身份链接、评论和变量；查询只经工作流服务；
- `BIZ_PROCESS_MAP` 是业务键到流程实例的本模块投影，不能替代引擎运行态；
- `WF_PROCESS_ORG` 和 `WF_TASK_TRANSFER` 是监控/转交旁路模型，不能作为业务主单状态源；
- 所有表的主键、唯一性、索引、字符集、外键和字段长度必须在目标库执行前单独审查。

任何 schema 变更由 DBA 按审批流程在目标库实施。本文不生成迁移脚本、不包含删除/DDL，
也不描述历史回填或一次性数据操作。
