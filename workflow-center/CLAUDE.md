# workflow-center/ CLAUDE.md

本文件为 `workflow-center` 模块提供上下文说明。

## 模块概述

**workflow-center** 是工作流中心，集成 Flowable 7.0.1 工作流引擎，为整个平台提供流程启动、任务审批、SLA 超时管理、候选人解析等能力。是**唯一**直接调用 Flowable API 的模块。

**基础包名**: `com.bank.branch.platform.workflow`

**Maven 坐标**: `com.bank.branch.platform:workflow-center`

**对外契约**: `WorkflowApi` 接口 + REST 端点。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`, `system-governance-center`
- **被依赖**: 所有需要工作流能力的模块 (通过 `WorkflowApi`)

## 包结构

```
src/main/java/com/bank/branch/platform/workflow/
├── api/              # 对外 API 接口
│   ├── WorkflowApi.java
│   └── dto/          # 10 个 DTO 类
├── config/           # Flowable 配置 (极简, 委托 Spring Boot 自动配置)
│   └── FlowableConfig.java
├── controller/       # REST 控制器 (3 个)
│   ├── TaskController.java           # 任务操作 (待办/已办, 签收, 审批, 驳回, 转办)
│   ├── ProcessController.java        # 流程映射查询
│   └── WorkflowAdminController.java # 管理端 (候选人配置, 超时规则)
├── entity/           # 业务实体 (4 个)
│   ├── BizProcessMap.java
│   ├── WfNodeCandidateConf.java
│   ├── WfNodeFormConf.java
│   └── WfTimeoutRule.java
├── enums/            # 枚举
│   ├── WfErrorCode.java              # 错误码 (WF-404xx, WF-409xx, WF-500xx)
│   ├── ProcessStatus.java            # RUNNING / COMPLETED / CANCELLED
│   └── SlaStatus.java                # GREEN / YELLOW / RED
├── facade/           # API 实现
│   └── WorkflowFacade.java
├── listener/         # Flowable 监听器
│   ├── TaskAssignmentListener.java   # 任务创建时解析候选人
│   └── ProcessCompletedListener.java # 流程结束时更新状态
├── mapper/           # MyBatis Mapper (4 个接口)
└── service/          # 业务逻辑 (6 个 Service)
    ├── CandidateResolverService.java # 解析候选人配置, 添加类型前缀
    ├── ProcessStartService.java      # 启动流程, 写入 biz_process_map
    ├── TaskOperationService.java     # 签收/审批/驳回/转办
    ├── SlaCalculationService.java    # 计算 SLA 交通灯状态
    ├── TodoQueryService.java         # 分页待办/已办列表, 任务详情
    └── WorkflowAdminService.java     # 候选人配置和超时规则 CRUD
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

## 对外 API: WorkflowApi

其他模块通过 `WorkflowApi` 接口与工作流交互:

| 方法 | 用途 | 异常 |
|------|------|------|
| `startProcess(StartProcessCmd cmd)` | 启动新流程 | WF-40401 (无定义), WF-40901 (同一 businessKey 已运行) |
| `getProcessByBusinessKey(businessKey)` | 按 businessKey 查询 | WF-40402 |
| `getProcessByBizTypeAndBizId(bizType, bizId)` | 按 bizType + bizId 查询 | WF-40402 |

支持的 bizType: `LEAD`, `LOAN`, `SUPPORT`, `TOUCH`, `TARGET_ADJUST`, `ALLOC_ADJUST`

## REST 端点

### TaskController (`/api/workflow/tasks`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/workflow/tasks/todo` | 分页待办列表 (候选人或已分配) |
| GET | `/api/workflow/tasks/done` | 分页已办列表 (已完成任务) |
| GET | `/api/workflow/tasks/{taskId}` | 任务详情 (含表单配置和审批记录) |
| POST | `/api/workflow/tasks/{taskId}/claim` | 签收任务 (候选人→受理人) |
| POST | `/api/workflow/tasks/{taskId}/approve` | 审批通过 (设置 approved=true) |
| POST | `/api/workflow/tasks/{taskId}/reject` | 驳回 (设置 approved=false) |
| POST | `/api/workflow/tasks/{taskId}/transfer` | 转办给其他员工 |

### ProcessController (`/api/workflow/processes`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/workflow/processes/{businessKey}` | 按 businessKey 查询 |
| GET | `/api/workflow/processes/biz/{bizType}/{bizId}` | 按 bizType + bizId 查询 |

### WorkflowAdminController (`/api/admin/workflow`) - 全部需 `@BizAuth(SYS_CONFIG)`

| 方法 | 端点 | 说明 |
|------|------|------|
| GET/POST | `/api/admin/workflow/candidate-configs` | 候选人配置 CRUD |
| GET/POST | `/api/admin/workflow/timeout-rules` | 超时规则 CRUD |

## 数据库表 (4 张业务表, 不含 Flowable 内部 ACT_* 表)

| 表 | 实体 | 说明 |
|----|------|------|
| `biz_process_map` | BizProcessMap | 业务实体与 Flowable 流程实例的桥梁 (businessKey 格式: `BIZ_TYPE:id`) |
| `wf_node_candidate_conf` | WfNodeCandidateConf | 节点候选人配置 (candidateType: ROLE/ORG/USER, candidateValue: JSON 数组) |
| `wf_node_form_conf` | WfNodeFormConf | 节点表单字段配置 (JSON: form_fields, editable_fields, required_fields) |
| `wf_timeout_rule` | WfTimeoutRule | SLA 超时阈值 (timeout_hours=红灯, warning_hours=黄灯) |

## Flowable 监听器

### TaskAssignmentListener (`${taskAssignmentListener}`)

实现 `org.flowable.task.service.delegate.TaskListener`, 在任务创建时触发:
1. 从 `DelegateTask` 提取 `processDefinitionKey` 和 `nodeKey`
2. 调用 `CandidateResolverService.resolveCandidates()` 解析候选人
3. 为每个候选人调用 `delegateTask.addCandidateGroup(group)`
4. 通过 `NotifyApi` 发送任务通知 (失败时吞异常, 不阻塞流程)

### ProcessCompletedListener (`${processCompletedListener}`)

实现 `org.flowable.engine.delegate.ExecutionListener`, 在流程实例结束时触发:
1. 按 `processInstanceId` 查找 `BizProcessMap` 记录
2. 更新 `process_status` 为 COMPLETED, 设置 `end_time`
3. 发布 `ProcessCompletedEvent` 事件

## Spring 内部事件

其他模块可通过 `@EventListener` 监听:

| 事件 | 来源 | 载荷 |
|------|------|------|
| `ProcessStartedEvent` | ProcessStartService | processInstanceId, businessKey, bizType |
| `ProcessCompletedEvent` | ProcessCompletedListener | processInstanceId, businessKey, outcome, reason |
| `TaskApprovedEvent` | TaskOperationService | taskId, processInstanceId, empId |
| `TaskRejectedEvent` | TaskOperationService | taskId, processInstanceId, empId |
| `TaskTransferredEvent` | TaskOperationService | taskId, processInstanceId, fromEmpId, toEmpId |

## 跨模块依赖

| 依赖 | 来源模块 | 用途 |
|------|----------|------|
| `CalendarApi` | system-governance-center | SLA 计算 (统计工作日, 计算任务截止日期) |
| `NotifyApi` | system-governance-center | 任务创建时发送通知给候选人 |
