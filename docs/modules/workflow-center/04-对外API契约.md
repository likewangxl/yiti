# 工作流中心 -- 对外 API 契约

> 版本：V1.1
> 最后更新：2026-04-14
> 本文档以 `workflow-center` 当前代码实现为准，用于说明真实可用的 Java 契约、REST 契约、内部事件与实现边界。

## 1. 当前实现边界

### 1.1 能力暴露总览

| 能力 | 当前暴露形态 | 入口 | 说明 |
|---|---|---|---|
| 跨模块同步调用 | Java `*Api` | `WorkflowApi` / `WorkflowFacade` | 当前唯一已落地的跨模块 Java 契约 |
| 任务查询与办理 | REST | `TaskController` | 面向前端、联调和真实环境测试 |
| 流程提交/撤回 | REST | `ProcessCommandController` | 与 `WorkflowApi` 共享底层 service |
| 流程详情/进度图/历史 | REST | `ProcessController` | 当前无独立 Java QueryApi |
| 流程映射查询 | REST + `WorkflowApi` | `ProcessMapController` / `WorkflowApi` | Java 侧只暴露两种映射查询方法 |
| 管理端配置 | REST | `WorkflowAdminController` | 超时规则、候选人、节点表单、流程定义列表 |
| 模块内事件 | Spring 内部事件 | service / listener record 事件 | 供同 JVM 内其他模块监听 |

### 1.2 当前**未**落地的旧名称

以下名称在旧设计稿、历史计划或其他模块文档中出现过，但 `workflow-center` 当前代码库中**没有对应对外接口/Facade**：

- `WorkflowQueryApi`
- `WorkflowConfigApi`
- `WorkflowParticipantService`

需要这些能力的模块，当前只能：

1. 通过 `WorkflowApi` 使用已落地的同步 Java 能力。
2. 对前端/联调场景调用 `/api/workflow/**` 标准 REST 入口。
3. 若坚持模块间 Java 调用，先在 `workflow-center` 内新增正式 `*Api` 契约，再接入其他模块。

---

## 2. Java 对外契约：WorkflowApi

### 2.1 接口定义

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;

public interface WorkflowApi {

    WorkflowLaunchResp startProcess(StartProcessCmd cmd);

    void cancelProcess(String processInstanceId, String reason);

    BizProcessMapDTO getProcessByBusinessKey(String businessKey);

    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);
}
```

### 2.2 `startProcess(StartProcessCmd cmd)`

当前实现路径：`WorkflowFacade -> ProcessStartService`

执行语义：

1. 通过 `RepositoryService` 按 `processDefinitionKey` 查询最新版本流程定义。
2. 若流程定义不存在，抛 `WF-40401`。
3. 通过 `biz_process_map` 校验 `businessKey` 是否已有运行中流程；若有，抛 `WF-40901`。
4. 调用 `RuntimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, variables)` 启动流程。
5. 写入 `biz_process_map`，状态置为 `RUNNING`。
6. 查询首个任务，返回 `firstTaskId`（若首节点不是 `userTask`，则为 `null`）。
7. 发布内部事件 `ProcessStartedEvent(processInstanceId, businessKey, bizType)`。

注意：

- 当前实现只校验“流程定义存在”，**未额外校验是否挂起**。
- `variables` 由调用方传入；REST 提交流程时会额外注入 `bizType`、`bizId`、`businessKey`、`startUser`、`startOrgId`、`title`。

### 2.3 `cancelProcess(String processInstanceId, String reason)`

当前实现路径：`WorkflowFacade -> ProcessCommandService`

执行语义：

1. 先查 `biz_process_map`，要求流程映射存在且状态为 `RUNNING`，否则抛 `WF-40902`。
2. 调用 `CurrentUserApi` 获取当前用户；仅系统管理员或流程发起人可撤回。
3. 再查 Flowable runtime 中的流程实例；若不存在，同样抛 `WF-40902`。
4. 调用 `RuntimeService.deleteProcessInstance(processInstanceId, reason)`。
5. 将 `biz_process_map` 更新为 `CANCELLED`，并清空 `currentAssignee`、`candidateGroups`，写入 `endTime`。

注意：

- 当前 service 本身不主动发布额外完成事件；是否触发流程结束监听，取决于 Flowable 删除流程后的执行路径。
- 该方法依赖 `CurrentUserApi`，因此在跨模块 Java 调用时同样要求存在当前登录态上下文。

### 2.4 映射查询方法

| 方法 | 返回 | 未命中行为 |
|---|---|---|
| `getProcessByBusinessKey(String businessKey)` | `BizProcessMapDTO` | 抛 `WF-40402` |
| `getProcessByBizTypeAndBizId(String bizType, String bizId)` | `BizProcessMapDTO` | 抛 `WF-40402` |

### 2.5 `StartProcessCmd`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bizType` | `String` | 是 | 业务类型，如 `LEAD` / `LOAN` / `SUPPORT` / `TOUCH` / `TARGET_ADJUST` / `ALLOC_ADJUST` |
| `bizId` | `String` | 是 | 业务对象 ID |
| `businessKey` | `String` | 是 | 业务键，格式通常为 `BIZ_TYPE:{id}` |
| `processDefinitionKey` | `String` | 是 | Flowable 流程定义 key |
| `startUser` | `String` | 是 | 发起人工号 |
| `startOrgId` | `String` | 是 | 发起人机构代码 |
| `title` | `String` | 是 | 流程标题 |
| `variables` | `Map<String, Object>` | 否 | 额外流程变量 |

### 2.6 `WorkflowLaunchResp`

| 字段 | 类型 | 说明 |
|---|---|---|
| `processInstanceId` | `String` | Flowable 流程实例 ID |
| `businessKey` | `String` | 业务键 |
| `firstTaskId` | `String` | 首个用户任务 ID；可能为 `null` |

### 2.7 `BizProcessMapDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `String` | 映射主键 |
| `businessKey` | `String` | 业务键 |
| `bizType` | `String` | 业务类型 |
| `bizId` | `String` | 业务 ID |
| `processDefinitionKey` | `String` | 流程定义 key |
| `processInstanceId` | `String` | 流程实例 ID |
| `processStatus` | `String` | `RUNNING` / `COMPLETED` / `CANCELLED` |
| `title` | `String` | 流程标题 |
| `startUser` | `String` | 发起人工号 |
| `startOrgId` | `String` | 发起人机构代码 |
| `currentAssignee` | `String` | 当前处理人工号 |
| `candidateGroups` | `List<String>` | 候选组列表 |
| `startTime` | `LocalDateTime` | 发起时间 |
| `endTime` | `LocalDateTime` | 结束时间 |

### 2.8 调用约束

- 其他模块**只能**依赖 `WorkflowApi`，不得直接注入 `ProcessStartService`、`ProcessCommandService`、`mapper`、`entity`。
- `startProcess()` 与调用方业务事务共享数据库事务边界。
- Java 查询能力当前仅限流程映射；待办、流程图、历史节点等查询不属于当前 Java 对外契约。

---

## 3. REST 契约

> 说明：以下接口为 `workflow-center` 当前已实现的 HTTP 入口。它们是真实可联调的控制器契约，但**不等同于**跨模块 Java `*Api` 契约。

### 3.1 提交流程与撤回流程

控制器：`ProcessCommandController`
基础路径：`/api/workflow/processes`

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| `POST` | `/submit` | `ProcessSubmitReqDTO` | `ResponseWrapper<WorkflowLaunchResp>` |
| `POST` | `/{processInstanceId}/cancel` | `CancelProcessReqDTO` | `ResponseWrapper<Void>` |

`ProcessSubmitReqDTO` 字段：

| 字段 | 类型 | 必填 |
|---|---|---|
| `bizType` | `String` | 是 |
| `bizId` | `String` | 是 |
| `businessKey` | `String` | 是 |
| `processDefinitionKey` | `String` | 是 |
| `title` | `String` | 是 |
| `variables` | `Map<String, Object>` | 否 |

`CancelProcessReqDTO` 字段：

| 字段 | 类型 | 必填 |
|---|---|---|
| `reason` | `String` | 是 |

### 3.2 任务查询与任务办理

控制器：`TaskController`
基础路径：`/api/workflow/tasks`

#### 3.2.1 列表与详情

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| `GET` | `` | `bizType?` `keyword?` `pageNo=1` `pageSize=20` | `ResponseWrapper.page(PageResult<TaskRespDTO>)` |
| `GET` | `/done` | `bizType?` `keyword?` `pageNo=1` `pageSize=20` | `ResponseWrapper.page(PageResult<TaskRespDTO>)` |
| `GET` | `/{taskId}` | 路径参数 `taskId` | `ResponseWrapper<TaskDetailRespDTO>` |

当前实现差异：

- `keyword` 参数已暴露，但 `TodoQueryService` 目前**尚未真正参与过滤**。
- `GET /api/workflow/tasks` 没有旧文档中的 `/todo` 子路径。
- `claimable` 当前只按“任务是否未签收”计算，未严格校验候选组命中。

`TaskRespDTO` 关键字段：

| 字段 | 说明 |
|---|---|
| `taskId` / `processInstanceId` / `businessKey` | 任务与流程关联标识 |
| `bizType` / `bizId` / `title` | 业务侧识别信息 |
| `startUser` / `startTime` | 发起信息 |
| `taskName` / `taskCreateTime` | 当前节点信息 |
| `assignee` / `candidateGroups` | 受理人与候选组 |
| `slaStatus` / `warningTime` / `timeoutTime` | SLA 信息 |
| `claimable` | 是否可签收 |
| `completeTime` / `approvalResult` / `opinion` | 已办补充字段 |

`TaskDetailRespDTO` 结构：

| 字段 | 类型 | 当前实现情况 |
|---|---|---|
| `taskInfo` | `TaskRespDTO` | 已实现 |
| `runtimeAccess` | `RuntimeAccessDTO` | 已实现，但为轻量判定 |
| `nodeFormConf` | `NodeFormConfDTO` | 已实现 |
| `processProgress` | `List<ProcessNodeDTO>` | 当前固定返回空列表 |
| `approvalLogs` | `List<ApprovalLogDTO>` | 已实现 |

`RuntimeAccessDTO` 当前语义：

- `canApprove` / `canReject` / `canTransfer`：主要基于“当前用户是否等于 `assignee`”。
- `canClaim`：当前仅判断“任务未签收且当前用户不是 assignee”。
- `isCandidate`：当前未做真实候选组判定，默认未补齐。

#### 3.2.2 任务办理

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| `POST` | `/{taskId}/claim` | 无 | `ResponseWrapper<Void>` |
| `POST` | `/{taskId}/approve` | `ApproveReqDTO` | `ResponseWrapper<Void>` |
| `POST` | `/{taskId}/reject` | `RejectReqDTO` | `ResponseWrapper<Void>` |
| `POST` | `/{taskId}/transfer` | `TransferReqDTO` | `ResponseWrapper<Void>` |

请求体字段：

| DTO | 字段 |
|---|---|
| `ApproveReqDTO` | `opinion`、`formData` |
| `RejectReqDTO` | `opinion` |
| `TransferReqDTO` | `targetEmpId`、`reason` |

办理语义：

- `claim`：要求任务未签收，否则抛 `WF-40904`。
- `approve` / `reject` / `transfer`：要求当前用户就是当前 `assignee`，否则抛 `WF-40903`。
- `approve` 会写入 `approved=true`；`reject` 会写入 `approved=false`。

### 3.3 流程详情、进度图与历史

控制器：`ProcessController`
基础路径：`/api/workflow/processes`

| 方法 | 路径 | 返回 |
|---|---|---|
| `GET` | `/{processInstanceId}` | `ResponseWrapper<ProcessQueryService.ProcessInstanceInfo>` |
| `GET` | `/{processInstanceId}/diagram` | `image/png` 字节流 |
| `GET` | `/{processInstanceId}/history` | `ResponseWrapper<List<ApprovalLogDTO>>` |
| `GET` | `/{processInstanceId}/nodes` | `ResponseWrapper<ProcessDiagramDTO>` |

`ProcessInstanceInfo` 关键字段：

- 流程基本信息：`processInstanceId`、`processDefinitionKey`、`processDefinitionName`、`processDefinitionVersion`
- 实例状态：`businessKey`、`startUserId`、`isEnded`、`startTime`、`endTime`、`durationMs`
- 业务扩展：`bizType`、`bizId`、`title`、`startUserName`、`startOrgName`
- 当前节点：`processStatus`、`currentNodeId`、`currentNodeName`、`currentAssignee`、`currentAssigneeName`、`candidateGroups`

`GET /history` 当前语义：

- 仅返回历史 `userTask` 节点。
- `action` / `opinion` 为 best-effort 填充，不保证每条历史节点都能完整回溯审批意见。

### 3.4 流程映射查询

控制器：`ProcessMapController`
路径：`GET /api/workflow/process-map`

支持两种查询方式：

| 查询方式 | 参数 |
|---|---|
| 按业务键查询 | `businessKey` |
| 按业务类型 + 业务 ID 查询 | `bizType` + `bizId` |

返回：`ResponseWrapper<BizProcessMapDTO>`

注意：

- `businessKey` 与 `bizType+bizId` 二选一。
- 当前控制器在缺少参数时直接抛 `IllegalArgumentException`，最终映射为 400 还是 500 取决于全局异常处理配置。

### 3.5 管理端配置

控制器：`WorkflowAdminController`
基础路径：`/api/admin/workflow`

全部接口都要求：

```java
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
```

#### 3.5.1 超时规则

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/timeout-rules` | 列表，可按 `processDefinitionKey` 过滤 |
| `GET` | `/timeout-rules/item/{id}` | 详情 |
| `PUT` | `/timeout-rules/{id}` | 更新 |
| `POST` | `/timeout-rules` | 新增 |

#### 3.5.2 节点候选人配置

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/node-candidates` | 列表，可按 `processDefinitionKey` 过滤 |
| `GET` | `/node-candidates/item/{id}` | 详情 |
| `PUT` | `/node-candidates/{id}` | 更新 |
| `POST` | `/node-candidates` | 新增 |

#### 3.5.3 节点表单配置

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/node-forms` | 列表，可按 `processDefinitionKey` 过滤 |
| `GET` | `/node-forms/item/{id}` | 详情 |
| `PUT` | `/node-forms/{id}` | 更新 |
| `POST` | `/node-forms` | 新增 |

#### 3.5.4 流程定义列表

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| `GET` | `/process-definitions` | `active=true/false` | 查询已部署流程定义 |

---

## 4. 内部事件契约

### 4.1 当前实际发布的 Spring 事件

| 事件 | 来源 | 载荷 |
|---|---|---|
| `ProcessStartService.ProcessStartedEvent` | `ProcessStartService` | `processInstanceId`、`businessKey`、`bizType` |
| `ProcessCompletedListener.ProcessCompletedEvent` | `ProcessCompletedListener` | `processInstanceId`、`businessKey` |
| `TaskOperationService.TaskApprovedEvent` | `TaskOperationService` | `taskId`、`processInstanceId`、`empId` |
| `TaskOperationService.TaskRejectedEvent` | `TaskOperationService` | `taskId`、`processInstanceId`、`empId` |
| `TaskOperationService.TaskTransferredEvent` | `TaskOperationService` | `taskId`、`processInstanceId`、`fromEmpId`、`toEmpId` |

### 4.2 当前没有发布的旧事件名称

以下事件名称在旧稿中出现过，但当前代码未发布：

- `TaskCreatedEvent`
- `ProcessTerminatedEvent`
- 独立的 `workflow.process.completed.v1` 包装 DTO（带 `result`、`lastApprover`、`variables` 等）

### 4.3 终态语义

- `ProcessCompletedListener` 会读取流程变量 `approved`。
- 若 `approved == false`，会把 `biz_process_map.process_status` 写成 `CANCELLED`。
- 其他结束场景写成 `COMPLETED`。

这意味着“驳回”和“撤回”在当前持久化层都可能表现为 `CANCELLED`，消费方若需要更细粒度结果，需要二次查询或扩展事件载荷。

---

## 5. 上游依赖

| 依赖接口 | 来源模块 | 用途 |
|---|---|---|
| `CurrentUserApi` | auth-permission-center | 当前用户、机构、管理员判定、候选组键 |
| `UserApi` | auth-permission-center | 用户姓名查询 |
| `OrgApi` | auth-permission-center | 主机构查询 |
| `CalendarApi` | system-governance-center | SLA 工作日计算 |
| `NotifyApi` | system-governance-center | 任务创建后发送通知 |

---

## 6. 兼容性与落地说明

- 旧文档中的 `WorkflowQueryApi`、`WorkflowConfigApi`、`WorkflowParticipantService` 仍可作为**规划态目标名**保留，但不能写成当前已落地 Java Bean。
- 若其他模块需要“待办列表 / 节点表单 / 流程图 / 历史节点 / 参与者判定”等能力，当前要么走 `workflow-center` 标准 REST，要么先补齐新的 Java `*Api`。
- `TaskController`、`ProcessController`、`WorkflowAdminController` 属于当前真实联调入口；但按项目分层规则，业务模块之间仍应优先通过正式 `*Api` 契约协作。
- 任何新增对外能力，都应先补 `api/` 接口与 DTO，再同步本文件和依赖模块文档。
