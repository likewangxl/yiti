# workflow-center/ CLAUDE.md

本文件为 `workflow-center` 模块提供上下文说明。

## 模块概述

**workflow-center** 是工作流中心，集成 Flowable 7.0.1 工作流引擎，为整个平台提供流程启动、任务审批、SLA 超时管理、候选人解析等能力。是**唯一**直接调用 Flowable API 的模块。

**基础包名**: `com.bank.branch.platform.workflow`

**Maven 坐标**: `com.bank.branch.platform:workflow-center`

**对外契约**: 3 个跨模块 Java 接口（`WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi`）+ 6 个 REST 控制器（任务/流程/管理端配置/可视化流程设计器）。

> **模块定位补充（引入可视化审批流程设计器后）**：本模块除原有「流程启动/任务办理/静态候选人与超时配置」外，V1.x 演进新增了**可视化审批流程设计器**（草稿 CRUD、发布到 Flowable、变量目录、已部署流程反向导入），入口为 `/api/admin/workflow/flows/**`，由 `FlowDesignController` + `service/flow/` 8 个 Service 承载。设计文档以 `docs/modules/workflow-center/04-对外API契约.md` 与源码为准。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`, `system-governance-center`
- **被依赖**: 所有需要工作流能力的模块 (通过 `WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi`)

## 包结构

```
src/main/java/com/bank/branch/platform/workflow/
├── api/              # 对外 API 接口 (3 个)
│   ├── WorkflowApi.java
│   ├── WorkflowQueryApi.java
│   ├── TodoQueryApi.java
│   ├── dto/          # 业务 DTO
│   │   └── flow/     # 设计器专用 DTO (7 个): FlowDefDTO / FlowGraphDTO / FlowNodeDTO / FlowEdgeDTO / FlowApproverDTO / FlowConditionDTO / FlowVariableDTO
│   └── event/        # ProcessCompletedEvent / ProcessWithdrawnEvent
├── config/           # Flowable 配置 (极简, 委托 Spring Boot 自动配置)
│   └── FlowableConfig.java
├── controller/       # REST 控制器 (6 个)
│   ├── TaskController.java           # 任务操作 (待办/已办, 签收, 审批, 驳回, 转办)
│   ├── ProcessController.java        # 流程实例详情/进度图/历史查询
│   ├── ProcessCommandController.java # 提交/撤回流程命令
│   ├── ProcessMapController.java     # 按业务键查流程映射
│   ├── WorkflowAdminController.java  # 管理端 (候选人配置, 超时规则, 表单配置, 流程定义列表)
│   └── FlowDesignController.java     # 可视化审批流程设计器管理 (草稿 CRUD、发布、变量目录、已部署流程导入)
├── entity/           # 业务实体 (8 个)
│   ├── BizProcessMap.java
│   ├── WfNodeCandidateConf.java
│   ├── WfNodeFormConf.java
│   ├── WfTimeoutRule.java
│   └── WfFlowDef.java / WfFlowNode.java / WfFlowEdge.java / WfFlowNodeApprover.java  # 审批流程设计器 (草稿/图形数据)
├── enums/            # 枚举
│   ├── WfErrorCode.java              # 错误码 (WF-404xx, WF-409xx, WF-500xx)
│   ├── ProcessStatus.java            # RUNNING / COMPLETED / CANCELLED
│   └── SlaStatus.java                # GREEN / YELLOW / RED
├── facade/           # API 实现 (3 个)
│   ├── WorkflowFacade.java
│   ├── WorkflowQueryFacade.java
│   └── TodoQueryFacade.java
├── listener/         # Flowable 监听器 (3 个)
│   ├── TaskAssignmentListener.java        # 单实例任务创建时解析候选人
│   ├── MultiInstanceApproverResolver.java # 会签(MI)入口监听器，展开 approverEmpIds 流程变量
│   └── ProcessCompletedListener.java      # 流程结束时更新状态 + 发布事件
├── mapper/           # MyBatis-Plus Mapper (8 个接口)
│   ├── BizProcessMapMapper, NodeCandidateConfMapper, NodeFormConfMapper, TimeoutRuleMapper
│   └── WfFlowDefMapper, WfFlowNodeMapper, WfFlowEdgeMapper, WfFlowNodeApproverMapper
└── service/          # 业务逻辑 (8 个核心 Service + service/flow/ 8 个设计器 Service)
    ├── CandidateResolverService.java # 解析候选人配置, 添加类型前缀
    ├── ProcessStartService.java      # 启动流程, 写入 biz_process_map
    ├── ProcessCommandService.java    # 提交/撤回流程命令
    ├── ProcessQueryService.java      # 流程实例详情/进度图/历史查询
    ├── TaskOperationService.java     # 签收/审批/驳回/转办
    ├── SlaCalculationService.java    # 计算 SLA 交通灯状态
    ├── TodoQueryService.java         # 分页待办/已办列表, 任务详情
    ├── WorkflowAdminService.java     # 候选人配置和超时规则 CRUD
    └── flow/                         # 可视化审批流程设计器 (8 个 Service)
        ├── FlowDefService.java              # 草稿 CRUD + 整图替换保存
        ├── FlowPublishService.java          # 校验 → 生成 BPMN → 部署 (DSN_ 影子 KEY) → 回填候选人配置
        ├── FlowImportService.java           # 已部署 Flowable 流程反向导入为只读设计器模型
        ├── FlowBpmnGenerator.java           # DTO 图形 → Flowable BpmnModel
        ├── FlowConditionExpressionBuilder.java
        ├── FlowValidator.java
        ├── FlowVariableCatalog.java
        └── DesignerFlowLookupService.java   # 按 flowKey 反查已发布 procDefKey
```

## Flowable 配置

配置极简, 全部通过 Spring Boot 自动配置 + `application.yml` 管理:

```yaml
flowable:
  history-level: audit        # 记录历史数据 (用于已办列表)
  idm:
    enabled: false            # 禁用内置身份管理 - 使用自有 auth 模块
  database-schema-update: true # 自动创建/更新 Flowable 表
```

使用的 Flowable 服务:
- `RepositoryService` - 查询流程定义 (启动前校验)
- `RuntimeService` - `startProcessInstanceByKey()` 启动流程
- `TaskService` - 签收, 完成, 设置受理人, 添加评论, 查询任务
- `HistoryService` - `createHistoricTaskInstanceQuery()` 查询已办列表

## 对外 API: WorkflowApi / WorkflowQueryApi / TodoQueryApi

当前真正对外公开并有实现的 Java 接口共 3 个（旧文档/规划稿中出现过的 `WorkflowConfigApi`、`WorkflowParticipantService` **当前代码库中没有对应实现**，勿再引用）：

**WorkflowApi**（流程启动/控制 + 无会话审批 + 设计器解析，实现 `WorkflowFacade`）：

| 方法 | 用途 | 异常 |
|------|------|------|
| `startProcess(StartProcessCmd cmd)` | 启动新流程 | WF-40401 (无定义), WF-40901 (同一 businessKey 已运行) |
| `cancelProcess(processInstanceId, reason)` | 撤回流程，发布 `ProcessWithdrawnEvent` | — |
| `getProcessByBusinessKey(businessKey)` | 按 businessKey 查询 | WF-40402 |
| `getProcessByBizTypeAndBizId(bizType, bizId)` | 按 bizType + bizId 查询 | WF-40402 |
| `getProcessOutcome(processInstanceId)` | 查询已完成流程的审批结论 (`Optional<"APPROVED"\|"REJECTED">`)，供补偿场景读历史变量 `approved` | — |
| `approveByEmp(taskId, empId, opinion[, formData])` / `rejectByEmp(taskId, empId, opinion)` | **无会话版**，供 SOAP 网关/callpu 等外部渠道按显式 `empId` 调用，不依赖登录态、不校验 assignee | — |
| `resolveDesignerProcDefKey(flowKey)` | 按设计器流程 `flowKey` 解析其「已发布」procDefKey（`DSN_` 前缀） | WF-40401 (未发布) |

支持的 bizType: `LEAD`, `LOAN`, `SUPPORT`, `TOUCH`, `TARGET_ADJUST`, `ALLOC_ADJUST`

**WorkflowQueryApi**（只读查询，实现 `WorkflowQueryFacade`）：`queryTodoList` / `queryDoneList` / `countPendingTasks` / `listRecentPendingTasks` / `getTaskDetail` / `getProcessHistory` / `getProcessNodes` / `getProcessByBusinessKey` / `getProcessByBizTypeAndBizId` / `queryParticipatedBusinessKeys`。

**TodoQueryApi**（待办/已办按 businessKey 反查，供 perf 等模块二次过滤，实现 `TodoQueryFacade`）：`listMyTodoBusinessKeys` / `findTaskRespByBusinessKeys` / `listMyDoneBusinessKeys` / `findDoneTaskRespByBusinessKeys`（会话版）；`listTodoBusinessKeysByEmp` / `findTaskRespByBusinessKeysByEmp`（**无会话版**，供 SOAP 网关/callpu 等无登录态链路使用）。

> 无会话版方法（`approveByEmp`/`rejectByEmp`/`listTodoBusinessKeysByEmp`/`findTaskRespByBusinessKeysByEmp`）不依赖登录态，调用方必须自行完成鉴权与候选人可见性校验后再调用，避免越权。

## REST 端点

### TaskController (`/api/workflow/tasks`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/workflow/tasks` | 分页待办列表 (候选人或已分配) |
| GET | `/api/workflow/tasks/done` | 分页已办列表 (已完成任务) |
| GET | `/api/workflow/tasks/{taskId}` | 任务详情 (含表单配置和审批记录) |
| POST | `/api/workflow/tasks/{taskId}/claim` | 签收任务 (候选人→受理人) |
| POST | `/api/workflow/tasks/{taskId}/approve` | 审批通过 (设置 approved=true) |
| POST | `/api/workflow/tasks/{taskId}/reject` | 驳回 (设置 approved=false) |
| POST | `/api/workflow/tasks/{taskId}/transfer` | 转办给其他员工 |

### ProcessController (`/api/workflow/processes`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/workflow/processes/{processInstanceId}` | 流程实例详情 |
| GET | `/api/workflow/processes/{processInstanceId}/diagram` | 流程进度图 (PNG) |
| GET | `/api/workflow/processes/{processInstanceId}/history` | 流程历史查询 |
| GET | `/api/workflow/processes/{processInstanceId}/nodes` | 流程节点查询 |

### ProcessCommandController (`/api/workflow/processes`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/workflow/processes/submit` | 提交流程命令 |
| POST | `/api/workflow/processes/{processInstanceId}/cancel` | 撤回流程命令 |

### ProcessMapController (`/api/workflow/process-map`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/workflow/process-map` | 按 `businessKey` 或 `bizType`+`bizId`（二选一）查流程映射 |

### WorkflowAdminController (`/api/admin/workflow`) - 全部需 `@BizAuth(SYS_CONFIG, CONFIG)`

| 方法 | 端点 | 说明 |
|------|------|------|
| GET/POST/PUT | `/api/admin/workflow/timeout-rules`，`GET .../item/{id}` | 超时规则 CRUD |
| GET/POST/PUT | `/api/admin/workflow/node-candidates`，`GET .../item/{id}` | 候选人配置 CRUD |
| GET/POST/PUT | `/api/admin/workflow/node-forms`，`GET .../item/{id}` | 表单配置 CRUD |
| GET | `/api/admin/workflow/process-definitions` | 流程定义列表 |

### FlowDesignController (`/api/admin/workflow/flows`) - 全部需 `@BizAuth(SYS_CONFIG, CONFIG)`

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/admin/workflow/flows` | 流程定义列表 (不含图形数据) |
| GET | `/api/admin/workflow/flows/{id}` | 获取完整流程图 (节点+连线+审批人) |
| POST | `/api/admin/workflow/flows` | 新建草稿 |
| PUT | `/api/admin/workflow/flows/{id}` | 整图替换保存 |
| POST | `/api/admin/workflow/flows/{id}/publish` | 发布到 Flowable (DRAFT→PUBLISHED，DSN_ 影子 KEY) |
| DELETE | `/api/admin/workflow/flows/{id}` | 删除草稿 (仅 DRAFT 且非只读导入) |
| GET | `/api/admin/workflow/flows/meta/variables` | 条件构造器可用流程变量目录 |
| GET | `/api/admin/workflow/flows/meta/approver-variables` | VAR 审批人可选变量目录 |
| POST | `/api/admin/workflow/flows/import-existing` | 导入内置 3 个已部署流程（`perf_target_adjust_v1`/`perf_alloc_adjust_corp_v1`/`perf_alloc_adjust_retail_v1`），单 key 失败不阻断其它 |

## 数据库表 (8 张业务表, 不含 Flowable 内部 ACT_* 表)

| 表 | 实体 | 说明 |
|----|------|------|
| `BIZ_PROCESS_MAP` | BizProcessMap | 业务实体与 Flowable 流程实例的桥梁 (businessKey 格式: `BIZ_TYPE:id`) |
| `WF_NODE_CANDIDATE_CONF` | WfNodeCandidateConf | 节点候选人配置 (candidateType: ROLE/ORG/USER, candidateValue: JSON 数组) |
| `WF_NODE_FORM_CONF` | WfNodeFormConf | 节点表单字段配置 (JSON: form_fields, editable_fields, required_fields) |
| `WF_TIMEOUT_RULE` | WfTimeoutRule | SLA 超时阈值 (timeout_hours=红灯, warning_hours=黄灯) |
| `WF_FLOW_DEF` | WfFlowDef | 可视化设计器流程定义 (草稿/已发布元数据) |
| `WF_FLOW_NODE` | WfFlowNode | 设计器流程节点 |
| `WF_FLOW_EDGE` | WfFlowEdge | 设计器流程连线 (含条件表达式) |
| `WF_FLOW_NODE_APPROVER` | WfFlowNodeApprover | 设计器节点审批人配置 |

## Flowable 监听器

### TaskAssignmentListener (`${taskAssignmentListener}`)

实现 `org.flowable.task.service.delegate.TaskListener`, 在单实例任务创建时触发:
1. 从 `DelegateTask` 提取 `processDefinitionKey` 和 `nodeKey`
2. 调用 `CandidateResolverService.resolveCandidates()` 解析候选人
3. 为每个候选人调用 `delegateTask.addCandidateGroup(group)`
4. 通过 `NotifyApi` 发送任务通知 (失败时吞异常, 不阻塞流程)

### MultiInstanceApproverResolver (会签 MI 入口监听器)

会签(MI)节点入口监听器，展开 `approverEmpIds` 流程变量供 Flowable MI 循环使用；显式初始化 `rejected=false`（不覆盖已存在值），避免完成条件 EL 引用 `rejected` 时因变量不存在抛 `PropertyNotFoundException`。

### ProcessCompletedListener (`${processCompletedListener}`)

实现 `org.flowable.engine.delegate.ExecutionListener`, 在流程实例结束时触发:
1. 按 `processInstanceId` 查找 `BizProcessMap` 记录
2. 更新 `process_status` 为 COMPLETED/CANCELLED, 设置 `end_time`
3. 发布 `ProcessCompletedEvent` 事件（`outcome` 写 APPROVED/REJECTED，与 `process_status` 的 COMPLETED/CANCELLED 是两套独立语义，不可混用）

## Spring 内部事件

其他模块可通过 `@EventListener` 监听:

| 事件 | 来源 | 载荷 |
|------|------|------|
| `ProcessStartedEvent` | ProcessStartService | processInstanceId, businessKey, bizType |
| `ProcessCompletedEvent` | ProcessCompletedListener / TaskOperationService (REJECTED 分支) | processInstanceId, businessKey, outcome, reason |
| `ProcessWithdrawnEvent` | ProcessCommandService (`cancelProcess`) | processInstanceId, businessKey, withdrawnByEmpId, currentAssigneeEmpId, opinion |
| `TaskApprovedEvent` | TaskOperationService | taskId, processInstanceId, empId |
| `TaskRejectedEvent` | TaskOperationService | taskId, processInstanceId, empId |
| `TaskTransferredEvent` | TaskOperationService | taskId, processInstanceId, fromEmpId, toEmpId |

> `ProcessStartedEvent`/`TaskApprovedEvent`/`TaskRejectedEvent`/`TaskTransferredEvent` 当前以嵌套 `record` 形式定义在各自发布方 Service 内（`ProcessStartService`/`TaskOperationService`），非独立顶层类；`ProcessCompletedEvent`/`ProcessWithdrawnEvent` 是独立顶层类，位于 `api/event/` 包（跨模块共享契约）。

## 跨模块依赖

| 依赖 | 来源模块 | 用途 |
|------|----------|------|
| `CalendarApi` | system-governance-center | SLA 计算 (统计工作日, 计算任务截止日期) |
| `NotifyApi` | system-governance-center | 任务创建时发送通知给候选人 |
| `CurrentUserApi` | auth-permission-center | 当前用户、当前机构、系统管理员判定、候选组键 |
| `UserApi` | auth-permission-center | 员工姓名查询；`getEmpIdsByRoleCode(roleCode)` 供 `TaskAssignmentListener`/`MultiInstanceApproverResolver` 把 `ROLE:xxx` 候选规则展开成员工 ID |
| `OrgApi` | auth-permission-center | 主机构信息查询 |
