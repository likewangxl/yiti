# 工作流中心 -- 对外 API 契约

> 版本：V1.5
> 最后更新：2026-08-11（V1.2 基线 2026-04-16 / 2026-04-24）
> 本文档以 `workflow-center` 当前代码实现为准，用于说明真实可用的 Java 契约、REST 契约、内部事件与实现边界。
> **2026-08-11 增量说明**：`WorkflowQueryApi` 新增 `getActiveTaskCandidates`，基于 Flowable 当前活动任务的实际 assignee/candidate 身份链接返回可审批员工；节点已审核或无活动任务时返回空列表。当前该查询的节点级消费按串行单活动 `userTask` 假设，返回值不含任务/节点分组；并行网关或多实例任务仅会被合并去重，不构成节点级审批人契约。
> **2026-07-19 回填说明**：对照 `api/`（含 `api/dto/`、`api/event/`）与 `controller/` 源码全量核实，补齐 `WorkflowApi` 2026-04 之后新增的 5 个方法、此前完全未收录的 `TodoQueryApi`、`ProcessWithdrawnEvent`、审批流监控/两阶段任务转交/审批流程设计器三组 REST 控制器；并订正 `WorkflowQueryApi.queryParticipatedBusinessKeys` 签名、`ProcessCompletedEvent` 实际归属包、`TaskController` 旧转交端点下线等与代码不符的旧描述。

## 1. 当前实现边界

### 1.1 能力暴露总览

| 能力 | 当前暴露形态 | 入口 | 说明 |
|---|---|---|---|
| 跨模块同步调用（流程控制） | Java `*Api` | `WorkflowApi` / `WorkflowFacade` | 流程启动/撤回/映射查询/审批结论补偿/无会话审批驳回/设计器流程 key 解析，当前已落地的跨模块 Java 契约 |
| 跨模块同步调用（只读查询） | Java `*Api` | `WorkflowQueryApi` / `WorkflowQueryFacade` | 待办已办分页、任务详情、流程历史、流程节点图、当前活动任务可审批员工、流程映射、参与者 businessKey 查询 |
| 跨模块同步调用（businessKey 反查） | Java `*Api` | `TodoQueryApi` / `TodoQueryFacade` | **2026-07-19 回填**：按 businessKey 批量反查任务元信息，含无会话（by-Emp）版本，此前文档完全未收录 |
| 任务查询与办理 | REST | `TaskController` | 面向前端、联调和真实环境测试 |
| 流程提交/撤回 | REST | `ProcessCommandController` | 与 `WorkflowApi` 共享底层 service |
| 流程详情/进度图(PNG)/进度图(JSON)/历史 | REST + Java `*Api` | `ProcessController` / `WorkflowQueryApi` | Java 侧已开放只读查询能力 |
| 流程映射查询 | REST + Java `*Api` | `ProcessMapController` / `WorkflowApi` / `WorkflowQueryApi` | Java 侧同时暴露命令侧与查询侧映射方法 |
| 管理端配置 | REST | `WorkflowAdminController` | 超时规则、候选人、节点表单、流程定义列表（各含列表/详情/新增/编辑） |
| 审批流程设计器 | REST | `FlowDesignController` | **2026-07-19 回填**：流程定义草稿 CRUD、发布、变量目录、内置流程反向导入（2026-05~06 上线，此前文档未收录） |
| 审批流监控 | REST | `ProcessMonitorController` | **2026-07-19 回填**：秘书岗/行长按机构数据范围监控审批流实例（2026-07-13 上线） |
| 两阶段任务转交 | REST | `TaskTransferController` | **2026-07-19 回填**：发起待认领→接收人认领/拒绝，替代旧单阶段转交（2026-07-13 上线，同日下线旧机制） |
| 模块内事件 | Spring 内部事件 | `api/event/` 顶级 record + service 内嵌 record | 供同 JVM 内其他模块监听，部分事件已从内部类迁移为 `api.event` 顶级契约（见 §4） |

### 1.2 当前**未**落地的旧名称

以下名称在旧设计稿、历史计划或其他模块文档中出现过，但 `workflow-center` 当前代码库中**没有对应对外接口/Facade**（2026-07-19 复核：结论不变）：

- `WorkflowConfigApi`
- `WorkflowParticipantService`

需要这些能力的模块，当前只能：

1. 通过 `WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi` 使用已落地的同步 Java 能力。
2. 对前端/联调场景调用 `/api/workflow/**` 标准 REST 入口。
3. 若坚持模块间 Java 调用，先在 `workflow-center` 内新增正式 `*Api` 契约，再接入其他模块。

### 1.3 已下线的旧机制（2026-07-19 新增说明）

- **旧单阶段任务转交**（`TaskController#transferTask` + 请求体 `TransferReqDTO`/旧文档中的 `TaskTransferReqDTO`）已于 **2026-07-13 彻底下线**：`TaskController` 不再声明 `/transfer` 路由，`api/dto/` 下已无对应请求 DTO 类文件，`TaskOperationService` 也不再有 `transferTask` 方法与 `TaskTransferredEvent`。替代方案是 `TaskTransferController` 的两阶段转交（发起待认领 → 接收人认领/拒绝），详见 §5.8。

---

## 2. Java 对外契约：WorkflowApi

### 2.1 接口定义（2026-07-19 核实更新）

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;

import java.util.Map;
import java.util.Optional;

public interface WorkflowApi {

    WorkflowLaunchResp startProcess(StartProcessCmd cmd);

    void cancelProcess(String processInstanceId, String reason);

    BizProcessMapDTO getProcessByBusinessKey(String businessKey);

    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);

    // ---- 以下 5 个方法为 2026-04-24 之后新增，2026-07-19 本次回填 ----

    Optional<String> getProcessOutcome(String processInstanceId);

    void approveByEmp(String taskId, String empId, String opinion);

    void approveByEmp(String taskId, String empId, String opinion, Map<String, Object> formData);

    void rejectByEmp(String taskId, String empId, String opinion);

    String resolveDesignerProcDefKey(String flowKey);
}
```

> **订正（2026-07-19）**：原文档只收录前 4 个方法，实际接口自 2026-04-24 之后已扩展到 9 个方法，本次全部补齐。

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

- 当前实现只校验"流程定义存在"，**未额外校验是否挂起**。
- `variables` 由调用方传入；REST 提交流程时会额外注入 `bizType`、`bizId`、`businessKey`、`startUser`、`startOrgId`、`title`。

### 2.3 `cancelProcess(String processInstanceId, String reason)`

当前实现路径：`WorkflowFacade -> ProcessCommandService`

执行语义：

1. 先查 `biz_process_map`，要求流程映射存在且状态为 `RUNNING`，否则抛 `WF-40905`（`PROCESS_NOT_RUNNING`）。
2. 调用 `CurrentUserApi` 获取当前用户；仅系统管理员或流程发起人可撤回，否则抛 `PermissionDeniedException("AUTH-40305", ...)`。
3. 再查 Flowable runtime 中的流程实例；若不存在，同样抛 `WF-40905`。
4. 撤回前先查当前 active task 的 `assignee`（供 `ProcessWithdrawnEvent` 使用），再调用 `RuntimeService.deleteProcessInstance(processInstanceId, reason)`。
5. 将 `biz_process_map` 更新为 `CANCELLED`，并清空 `currentAssignee`、`candidateGroups`，写入 `endTime`。
6. 发布 `ProcessWithdrawnEvent(processInstanceId, businessKey, withdrawnByEmpId, currentAssigneeEmpId, reason)`。

> **订正（2026-07-19）**：原文档记为 `WF-40902`，实际 `ProcessCommandService.cancelProcess` 抛出的是 `WF-40905`（`PROCESS_NOT_RUNNING`，"流程实例不存在或已结束"）；`WF-40902` 在当前 `WfErrorCode` 枚举中实际含义是 `TASK_NOT_OWNED`（"任务非当前用户"），与撤回流程无关。另原文档"该方法本身不主动发布额外完成事件"的表述已过期——**当前会显式发布 `ProcessWithdrawnEvent`**（2026-07-19 新增记录，见 §4），该事件此前从未被本文档收录。

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

### 2.8 `getProcessOutcome(String processInstanceId)`（2026-07-19 回填，新增于 2026-04-24 之后）

当前实现路径：`WorkflowFacade.getProcessOutcome`

用途：补偿场景下查询**已完成**流程的审批结论，不抛异常、以 `Optional` 表达"未知"。

执行语义：

1. 调 Flowable `HistoryService.createHistoricVariableInstanceQuery()` 按 `processInstanceId` + `variableName=approved` 查历史变量。
2. 变量值为 `Boolean.TRUE` → 返回 `Optional.of("APPROVED")`；`Boolean.FALSE` → `Optional.of("REJECTED")`。
3. 变量不存在 / 非 `Boolean` 类型 / 查询异常（如测试环境 `ACT_HI_VARINST` 表不存在）→ 一律返回 `Optional.empty()`，**不抛异常**。

语义对齐：与 `ProcessCompletedListener`/`TaskOperationService` 发布的 `ProcessCompletedEvent.outcome()` 计算逻辑保持一致。调用方应先自行确认 `processStatus=COMPLETED` 后再调用，避免对 `RUNNING` 流程产生误判。

### 2.9 `approveByEmp` / `rejectByEmp`（无会话版，2026-07-19 回填，新增于 2026-04-24 之后）

当前实现路径：`WorkflowFacade -> TaskOperationService.approveTaskByEmp` / `rejectTaskByEmp`

| 方法 | 说明 |
|---|---|
| `approveByEmp(taskId, empId, opinion)` | 三参版本，等价于 `approveByEmp(taskId, empId, opinion, null)` |
| `approveByEmp(taskId, empId, opinion, formData)` | 四参版本，`formData` 作为流程变量随 `complete` 写入，供 BPMN 排他网关路由（如 `corpRouteTo`/`finRouteTo`） |
| `rejectByEmp(taskId, empId, opinion)` | 驳回，语义同 `TaskOperationService.rejectTaskByEmp` |

**用途**：供 SOAP 网关 / callpu 等**外部渠道**按显式 `empId` 调用（不依赖登录态 ThreadLocal）。

**关键差异（与 REST `/approve`/`/reject` 相比）**：

- **不校验 `assignee`、不要求签收**——候选组任务未签收也可直接审批/驳回。
- `complete` 前会显式 `taskService.setAssignee(taskId, empId)`，保证 `ACT_HI_TASKINST.ASSIGNEE_` 留痕，"已审批"查询才能命中该 empId。
- 调用方（如 perf 侧无会话渠道）须自行保证该 `empId` 对该 `taskId` 有权限（按候选组/角色可见性查出 taskId 才调用），本方法**不做二次候选人校验**，越权风险由上游保证。
- 转交待认领期间（`WF_TASK_TRANSFER` 存在 `PENDING_ACCEPT` 记录）同样会被拦截，抛 `WF-40913`，防止绕过"原办理人只读"约束把待认领转交卡成孤儿。

**异常**：`WF-40403` 任务不存在；`WF-40913` 转交待认领锁定中。

### 2.10 `resolveDesignerProcDefKey(String flowKey)`（2026-07-19 回填，新增于 2026-04-24 之后）

当前实现路径：`WorkflowFacade -> DesignerFlowLookupService.resolveDeployedProcDefKey`

用途：供 perf 等业务模块在起流程时，按设计器流程唯一键 `flowKey`（如 `alloc_corp_designer`）解析其"已发布"的 Flowable 流程定义 KEY（`deployed_proc_def_key`，形如 `DSN_alloc_corp_designer`）。

异常：流程未发布（`status≠PUBLISHED` 或 `deployed_proc_def_key` 为空）时抛 `WF-40401`，调用方据此 fail-fast 提示"请先发布对应审批流程"。

### 2.11 调用约束

- 其他模块**只能**依赖正式 `WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi`，不得直接注入内部 `service`、`mapper`、`entity`。
- `startProcess()` 与调用方业务事务共享数据库事务边界。
- 其他模块读取待办、任务详情、流程历史、流程节点图时，应优先依赖 `WorkflowQueryApi`，不得直接注入 `TodoQueryService` / `ProcessQueryService`。
- 无会话方法（`approveByEmp`/`rejectByEmp`/`TodoQueryApi` 的 `*ByEmp` 系列）不依赖登录态、不做鉴权与候选人可见性校验，调用方必须自行完成鉴权后再调用，避免越权（见 `workflow-center/AGENTS.md`）。

---

## 3. Java 对外契约：WorkflowQueryApi

### 3.1 接口定义（2026-07-19 核实：`queryParticipatedBusinessKeys` 签名订正）

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.TaskCandidateUserDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;

import java.util.List;
import java.util.Set;

public interface WorkflowQueryApi {

    PageResult<TaskRespDTO> queryTodoList(String empId, String bizType, String keyword, int pageNo, int pageSize);

    PageResult<TaskRespDTO> queryDoneList(String empId, String bizType, String keyword, int pageNo, int pageSize);

    int countPendingTasks(String empId);

    List<TaskRespDTO> listRecentPendingTasks(String empId, int limit);

    TaskDetailRespDTO getTaskDetail(String taskId, String empId);

    List<ApprovalLogDTO> getProcessHistory(String processInstanceId);

    ProcessDiagramDTO getProcessNodes(String processInstanceId);

    List<TaskCandidateUserDTO> getActiveTaskCandidates(String processInstanceId);

    BizProcessMapDTO getProcessByBusinessKey(String businessKey);

    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);

    /**
     * 查询指定员工参与过的流程实例 businessKey 集合（去重，V1.4 S1.1 新增）。
     * 注意实际签名是 Integer 包装类型（非 int 基本类型），null 与 <=0 语义不同（见下方能力说明）。
     */
    Set<String> queryParticipatedBusinessKeys(String empId,
                                              String processDefinitionKeyPrefix,
                                              Integer timeWindowDays,
                                              Integer limit);
}
```

> **重大订正（2026-07-19 核实，源码 `WorkflowQueryApi.java`）**：原文档 `queryParticipatedBusinessKeys` 签名写作 `(String empId, String prefix, int days, int limit)`（全 `int` 基本类型），实际代码为 `(String empId, String processDefinitionKeyPrefix, Integer timeWindowDays, Integer limit)`（后两个参数是 `Integer` 包装类型，允许传 `null` 表示"无限制/兜底默认值"，与 `int` 基本类型的 0 语义不同）；原文档代码块也缺失 `import java.util.Set;`，本次一并补全。

当前实现路径：`WorkflowQueryFacade -> TodoQueryService / ProcessQueryService / ProcessStartService / HistoryService(Flowable)`

### 3.2 WorkflowQueryApi 能力说明

| 方法 | 底层实现 | 说明 |
|---|---|---|
| `queryTodoList(...)` | `TodoQueryService.queryTodoList(...)` | 查询待办分页列表 |
| `queryDoneList(...)` | `TodoQueryService.queryDoneList(...)` | 查询已办分页列表 |
| `countPendingTasks(empId)` | `queryTodoList(empId, null, null, 1, 1)` | 复用待办分页的 `total` 统计 |
| `listRecentPendingTasks(empId, limit)` | `queryTodoList(empId, null, null, 1, limit)` | 返回最近待办记录；`limit<=0` 时返回空列表 |
| `getTaskDetail(taskId, empId)` | `TodoQueryService.getTaskDetail(...)` | 获取任务详情与运行时办理信息 |
| `getProcessHistory(processInstanceId)` | `ProcessQueryService.getProcessHistory(...)` | 获取审批历史日志 |
| `getProcessNodes(processInstanceId)` | `ProcessQueryService.getProcessNodes(...)` | 获取流程节点图数据 |
| `getActiveTaskCandidates(processInstanceId)` | Flowable `TaskService` + auth `UserApi` | 查询当前活动任务：已签收任务仅取 assignee，未签收任务按实际 candidate 身份链接展开 `USER:`/`ROLE:`/`ORG:` 组，去重后补齐姓名和工号；节点已审核、终态或无活动任务时返回空列表。当前按串行单活动 `userTask` 使用；并行/多实例时结果扁平合并，不提供节点级区分 |
| `getProcessByBusinessKey(...)` | `ProcessStartService.getProcessByBusinessKey(...)` | 查询流程映射 |
| `getProcessByBizTypeAndBizId(...)` | `ProcessStartService.getProcessByBizTypeAndBizId(...)` | 查询流程映射 |
| `queryParticipatedBusinessKeys(empId, processDefinitionKeyPrefix, timeWindowDays, limit)` | Flowable `HistoricProcessInstanceQuery.involvedUser` 为主路径 + 当前登录用户候选组未领取任务为辅助路径 | 支撑 performance 模块 WORKFLOW_PARTICIPANT 数据范围语义。**"参与"定义（模式 B 简化版）**：主路径覆盖 assignee/owner/显式身份链接用户；若 `empId` 恰为当前登录用户，额外合并其候选组下未领取任务，`empId` 非当前登录用户时仅走主路径。`processDefinitionKeyPrefix` 在 Java 侧做 `startsWith` 过滤（Flowable 7 不支持 `keyLike`）。Fail-safe：`empId` 空返回空集；`timeWindowDays`/`limit` 为 `null`/`≤0` 时分别按"无限制"/"兜底 10000（`limit` 上限 10000）"处理 |

### 3.3 `TaskCandidateUserDTO` 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `empId` | String | `PT_USER.USER_ID`，Flowable 办理人内部标识 |
| `employeeNo` | String | 展示用员工工号，取 `PT_USER.USERNAME` |
| `employeeName` | String | 员工姓名，取 `PT_USER.USERCHNNAME` |

### 3.4 当前活动任务候选人边界

- 任务身份链接是候选范围的权威快照；查询不会重新读取节点配置来扩大候选人范围。
- `USER:` 直接映射员工；`ROLE:` 和 `ORG:` 分别通过 auth 的 `UserApi.getEmpIdsByRoleCode`、`UserApi.getEmpIdsByOrg` 按当前目录展开。角色/机构目录变化不会改写已创建任务的身份链接，但查询展示会按当前目录补齐组成员。
- 该 API 返回扁平列表，业务方只应在串行、同一时刻一个活动审批节点的流程中解释为“当前节点审批人”。并行网关、多实例或多个活动任务需要节点键、任务键分组等新契约，当前不作节点级正确性承诺。

---

## 4. Java 对外契约：TodoQueryApi（2026-07-19 回填，此前文档完全未收录）

### 4.1 接口定义

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;

import java.util.List;
import java.util.Map;

public interface TodoQueryApi {

    List<String> listMyTodoBusinessKeys(String empId, String bizType);

    Map<String, TaskRespDTO> findTaskRespByBusinessKeys(String empId, List<String> businessKeys);

    List<String> listMyDoneBusinessKeys(String empId, String bizType);

    Map<String, TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys);

    List<String> listTodoBusinessKeysByEmp(String empId, String bizType);

    Map<String, TaskRespDTO> findTaskRespByBusinessKeysByEmp(String empId, List<String> businessKeys);
}
```

当前实现路径：`TodoQueryFacade -> TodoQueryService`（thin wrapper，所有方法委托 `TodoQueryService` 拿数据 + 构造 `Map` 形态）。

### 4.2 设计目的与能力说明

**设计目的**：业务模块（如 performance）需要按业务字段二次过滤待办，但不应直连 Flowable 表；本 API 提供 businessKey 列表 + 反向 join 能力，把"业务字段过滤"留给业务模块，"Flowable 查询"留在 workflow-center。

| 方法 | 说明 | 会话依赖 |
|---|---|---|
| `listMyTodoBusinessKeys(empId, bizType)` | 查询某员工某 bizType 的所有待办 task 的 businessKey（去重） | 会话版：按登录态候选组解析 |
| `findTaskRespByBusinessKeys(empId, businessKeys)` | 按 businessKey 批量反查 `TaskRespDTO`；`empId` 用于鉴权，只返该员工候选或受理的 task | 会话版 |
| `listMyDoneBusinessKeys(empId, bizType)` | 已办：查询某员工某 bizType 下所有历史已办 task 的 businessKey（去重） | 会话版 |
| `findDoneTaskRespByBusinessKeys(empId, businessKeys)` | 已办：按 businessKey 反查 `TaskRespDTO`（走 `HistoryService`，仅返该员工 assignee 的） | 会话版 |
| `listTodoBusinessKeysByEmp(empId, bizType)` | 同 `listMyTodoBusinessKeys`，但候选组按传入 `empId` 查库实时解析 | **无会话**，供 SOAP 网关/callpu 使用（会话版在无会话线程调用会抛 `AUTH-40105`） |
| `findTaskRespByBusinessKeysByEmp(empId, businessKeys)` | 同 `findTaskRespByBusinessKeys`，同样按传入 `empId` 实时解析候选组 | **无会话** |

所有 `find*` 方法返回值均为 `Map<businessKey, TaskRespDTO>`，不命中的 key 在 Map 中缺失（不会返回 null 值项）；`businessKeys` 为空或 `empId` 为空时直接返回空 Map，不查库。

---

## 5. REST 契约

> 说明：以下接口为 `workflow-center` 当前已实现的 HTTP 入口。它们是真实可联调的控制器契约，但**不等同于**跨模块 Java `*Api` 契约。字段级请求/响应报文以 `03-接口设计与报文.md` 对应分组（A~H）为准，本节只做边界层面的归纳。

### 5.1 提交流程与撤回流程

控制器：`ProcessCommandController`（对应 03 文档 D 分组）
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

### 5.2 任务查询与任务办理

控制器：`TaskController`（对应 03 文档 A/B 分组）
基础路径：`/api/workflow/tasks`

#### 5.2.1 列表与详情

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| `GET` | `` | `bizType?` `keyword?` `pageNo=1` `pageSize=20` | `ResponseWrapper.page(PageResult<TaskRespDTO>)` |
| `GET` | `/done` | `bizType?` `keyword?` `pageNo=1` `pageSize=20` | `ResponseWrapper.page(PageResult<TaskRespDTO>)` |
| `GET` | `/{taskId}` | 路径参数 `taskId` | `ResponseWrapper<TaskDetailRespDTO>` |

当前实现差异：

- `keyword` 参数已暴露，但 `TodoQueryService` 目前**尚未真正参与过滤**。
- `GET /api/workflow/tasks` 没有旧文档中的 `/todo` 子路径。
- `claimable` 当前只按"任务是否未签收"计算，未严格校验候选组命中。

`TaskRespDTO` 关键字段：

| 字段 | 说明 |
|---|---|
| `taskId` / `processInstanceId` / `businessKey` | 任务与流程关联标识 |
| `bizType` / `bizId` / `title` | 业务侧识别信息 |
| `startUser` / `startTime` | 发起信息（`startUser` 实为 `PT_USER.user_id` 内部主键，工号见 `startUserEmpNo`） |
| `taskName` / `taskCreateTime` / `nodeKey` | 当前节点信息（`nodeKey`=2026-07-19 补充，`taskDefinitionKey`） |
| `assignee` / `candidateGroups` | 受理人与候选组 |
| `slaStatus` / `warningTime` / `timeoutTime` | SLA 信息 |
| `claimable` | 是否可签收 |
| `processStatus` | **2026-07-19 补充**：流程实例状态 RUNNING/COMPLETED/CANCELLED，已办列表必填 |
| `completeTime` / `approvalResult` / `opinion` | 已办补充字段 |

> 完整字段清单（含 `startUserEmpNo`/`startOrgId`/`startOrgDeptNo` 等）见 03 文档 A.1。

`TaskDetailRespDTO` 结构：

| 字段 | 类型 | 当前实现情况 |
|---|---|---|
| `taskInfo` | `TaskRespDTO` | 已实现 |
| `runtimeAccess` | `RuntimeAccessDTO` | 已实现，但为轻量判定 |
| `nodeFormConf` | `NodeFormConfDTO` | 已实现 |
| `processProgress` | `List<ProcessNodeDTO>` | 当前固定返回空列表 |
| `approvalLogs` | `List<ApprovalLogDTO>` | 已实现 |
| `outgoingBranches` | `List<BranchOptionDTO>` | **2026-07-19 补充**：当前审批节点"下一步走向"分支选项，仅设计器动态流程（`DSN_` 前缀）任务填充，静态 BPMN 任务恒为空列表 |

`RuntimeAccessDTO` 当前语义：

- `canApprove` / `canReject` / `canTransfer`：主要基于"当前用户是否等于 `assignee`"。
- `canClaim`：当前仅判断"任务未签收且当前用户不是 assignee"。
- `isCandidate`：当前未做真实候选组判定，默认未补齐。

#### 5.2.2 任务办理

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| `POST` | `/{taskId}/claim` | 无 | `ResponseWrapper<Void>` |
| `POST` | `/{taskId}/approve` | `ApproveReqDTO` | `ResponseWrapper<Void>` |
| `POST` | `/{taskId}/reject` | `RejectReqDTO` | `ResponseWrapper<Void>` |

请求体字段：

| DTO | 字段 |
|---|---|
| `ApproveReqDTO` | `opinion`、`formData` |
| `RejectReqDTO` | `opinion` |

办理语义：

- `claim`：要求任务未签收，否则抛 `WF-40904`；转交待认领锁定中抛 `WF-40913`。
- `approve` / `reject`：要求当前用户就是当前 `assignee`，否则抛 `WF-40903`；转交待认领锁定中同样抛 `WF-40913`。
- `approve` 会写入 `approved=true`；`reject` 会写入 `approved=false` 并**强制终止流程实例**（`deleteProcessInstance`），同时把 `biz_process_map.processStatus` 置 `CANCELLED`、发布 `ProcessCompletedEvent(outcome="REJECTED")`。

> **重大订正（2026-07-19）**：原文档此处还列有第 4 行 `POST /{taskId}/transfer`（请求体 `TransferReqDTO`）——**该端点已于 2026-07-13 下线**，`TaskController` 当前代码中不存在此路由，`TransferReqDTO` 类文件也已被删除。转交能力现由 `TaskTransferController` 的两阶段流程提供，见 §5.8；本表已据实移除该行。

### 5.3 流程详情、进度图与历史

控制器：`ProcessController`（对应 03 文档 C.1~C.4 分组）
基础路径：`/api/workflow/processes`

| 方法 | 路径 | 返回 |
|---|---|---|
| `GET` | `/{processInstanceId}` | `ResponseWrapper<ProcessQueryService.ProcessInstanceInfo>` |
| `GET` | `/{processInstanceId}/diagram` | `image/png` 字节流 |
| `GET` | `/{processInstanceId}/history` | `ResponseWrapper<List<ApprovalLogDTO>>` |
| `GET` | `/{processInstanceId}/nodes` | `ResponseWrapper<ProcessDiagramDTO>` |

`ProcessDiagramDTO.nodes[].taskId` 为 2026-08-28 补充的只读字段：活动或历史 `userTask` 返回
Flowable 任务 ID，事件/网关节点为空。调用方可用它将“当前节点”与后续业务申请做一致性绑定。

`ProcessInstanceInfo` 关键字段：

- 流程基本信息：`processInstanceId`、`processDefinitionKey`、`processDefinitionName`、`processDefinitionVersion`
- 实例状态：`businessKey`、`startUserId`、`isEnded`、`startTime`、`endTime`、`durationMs`
- 业务扩展：`bizType`、`bizId`、`title`、`startUserName`、`startOrgName`
- 当前节点：`processStatus`、`currentNodeId`、`currentNodeName`、`currentAssignee`、`currentAssigneeName`、`candidateGroups`

`GET /history` 当前语义：

- 仅返回历史 `userTask` 节点。
- `action` / `opinion` 为 best-effort 填充，不保证每条历史节点都能完整回溯审批意见。
- `ApprovalLogDTO` 实际还含 `operatorEmpNo`/`operatorOrgName` 两个字段，详见 03 文档 A.3。

### 5.4 流程映射查询

控制器：`ProcessMapController`（对应 03 文档 C.5 分组）
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

### 5.5 管理端配置

控制器：`WorkflowAdminController`（对应 03 文档 E 分组）
基础路径：`/api/admin/workflow`

全部接口都要求：

```java
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
```

#### 5.5.1 超时规则

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/timeout-rules` | 列表，可按 `processDefinitionKey` 过滤 |
| `GET` | `/timeout-rules/item/{id}` | 详情（**2026-07-19 补充**：原文档未收录，返回原始 `WfTimeoutRule` 实体） |
| `PUT` | `/timeout-rules/{id}` | 更新 |
| `POST` | `/timeout-rules` | 新增（**2026-07-19 补充**：原文档未收录，请求体为 `WfTimeoutRule` 实体，无 `@Valid`） |

#### 5.5.2 节点候选人配置

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/node-candidates` | 列表，可按 `processDefinitionKey` 过滤 |
| `GET` | `/node-candidates/item/{id}` | 详情（**2026-07-19 补充**：返回原始 `WfNodeCandidateConf` 实体，含 03 文档列表 DTO 未暴露的 `approveOrgScope`/`orgCode` 字段） |
| `PUT` | `/node-candidates/{id}` | 更新 |
| `POST` | `/node-candidates` | 新增（**2026-07-19 补充**：请求体为 `Map<String,Object>`，非强类型 DTO，控制器手工解析并以逗号拼接落库） |

#### 5.5.3 节点表单配置

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/node-forms` | 列表，可按 `processDefinitionKey` 过滤 |
| `GET` | `/node-forms/item/{id}` | 详情（**2026-07-19 补充**：返回原始实体，`formFields`/`editableFields`/`requiredFields` 为未解析的 JSON 字符串） |
| `PUT` | `/node-forms/{id}` | 更新 |
| `POST` | `/node-forms` | 新增（**2026-07-19 补充**：请求体为 `WfNodeFormConf` 实体，无 `@Valid`） |

> **字段名订正（2026-07-19）**：`FormFieldDTO` 的字段展示名实际字段名为 `fieldName`（原文档误写作 `fieldLabel`），另有 `description` 字段原文档未收录。`TimeoutRuleUpdateReqDTO`/`NodeCandidateUpdateReqDTO`/`NodeFormUpdateReqDTO` 三个更新请求 DTO 均为纯 `@Data` POJO，**当前代码没有任何 Bean Validation 注解**（原文档标注的 `@Min(1)`/`@NotBlank`/`@NotEmpty` 均不存在），`@Valid` 目前不产生实际拦截效果；详见 03 文档 E.4/E.8/E.12。

#### 5.5.4 流程定义列表

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| `GET` | `/process-definitions` | `active=true/false` | 查询已部署流程定义 |

### 5.6 审批流程设计器（2026-07-19 回填，此前文档完全未收录）

控制器：`FlowDesignController`（对应 03 文档 F 分组，2026-05-29~2026-06-08 上线）
基础路径：`/api/admin/workflow/flows`，全部接口 `@BizAuth(bizType = SYS_CONFIG, action = CONFIG)`。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/meta/variables?bizType=` | 查询可用流程变量目录 |
| `GET` | `/meta/approver-variables?bizType=` | 查询 VAR 审批人可选变量目录 |
| `GET` | `` | 查询流程定义列表（不含图形数据） |
| `GET` | `/{id}` | 获取流程图（节点+连线+审批人） |
| `POST` | `` | 新建流程定义草稿 |
| `PUT` | `/{id}` | 整图替换保存 |
| `POST` | `/{id}/publish` | 发布到 Flowable（DRAFT → PUBLISHED，影子 KEY `DSN_` + flowKey） |
| `DELETE` | `/{id}` | 删除草稿（仅 DRAFT 且非只读导入） |
| `POST` | `/import-existing` | 导入内置 3 个已部署流程为只读定义（幂等） |

请求/响应 DTO（`FlowGraphDTO`/`FlowNodeDTO`/`FlowEdgeDTO`/`FlowApproverDTO`/`FlowConditionDTO`/`FlowVariableDTO`/`FlowDefDTO`）字段级定义见 03 文档 F 分组。**关键实现要点**（源码 `FlowDefService`/`FlowPublishService`/`FlowImportService`）：

- 草稿保存/新建（`create`/`saveGraph`）请求体**无 `@Valid`**，图结构合法性校验只在发布（`publish`）时由 `FlowValidator` 触发，校验不通过抛 `WF-40906`（`FLOW_PUBLISH_VALIDATION_FAILED`）。
- 发布采用"影子 KEY"策略（`DSN_` + flowKey）部署到 Flowable，并整图替换（先删旧再插新）候选人配置（`WfNodeCandidateConf`）。
- 只读导入的流程定义（`isReadonlyImport=1`）禁止 `saveGraph`/`deleteDraft`，均抛 `WF-40400`（`RESOURCE_NOT_FOUND`）。

### 5.7 审批流监控（2026-07-19 回填，2026-07-13 上线）

控制器：`ProcessMonitorController`（对应 03 文档 G 分组）
路径：`GET /api/workflow/monitor/processes`

`@BizAuth(bizType = BizType.WORKFLOW_MONITOR, action = BizAction.LIST)`

分页参数：`status?` `bizType?` `keyword?` `startedBy?` `pageNo=1` `pageSize=20`，返回 `ResponseWrapper.page(PageResult<ProcessMonitorItemDTO>)`。

数据范围过滤 **Fail-Close**：系统管理员或 `DataScopeType.ALL` 不加机构过滤；`ORG`/`ORG_SUBTREE` 按机构编码集合过滤（通过 `WF_PROCESS_ORG` 参与机构快照表 EXISTS 过滤）；其余范围（`SELF` 系列/`WORKFLOW_PARTICIPANT`）或范围未知/为空一律返回空结果。字段定义见 03 文档 G.1。

`WfProcessOrgService.record(processInstanceId, empId, source)` 是"参与机构快照"唯一写入入口，所有挂点（流程启动、Flowable 监听器、审批 REST、两阶段转交认领）均调用此方法；内部整体 try-catch 兜底，绝不把异常抛回调用方。

### 5.8 两阶段任务转交待认领（2026-07-19 回填，2026-07-13 上线，替代旧单阶段转交）

控制器：`TaskTransferController`（对应 03 文档 H 分组）

| 方法 | 路径 | 请求体 | `@BizAuth` | 说明 |
|---|---|---|---|---|
| `POST` | `/api/workflow/monitor/tasks/{taskId}/transfer` | `TransferInitiateReqDTO` | `WORKFLOW_MONITOR:TRANSFER` | 发起转交（待认领），鉴权对齐审批流监控 |
| `GET` | `/api/workflow/monitor/tasks/{taskId}/transfer-candidates` | 无 | `WORKFLOW_MONITOR:TRANSFER` | **2026-07-21 新增**：查询可转交接收人，返回 `List<TransferCandidateDTO>`，与发起端点同鉴权、与 `initiate` 资格校验同源 |
| `GET` | `/api/workflow/monitor/processes/{processInstanceId}/transfers` | 无 | `WORKFLOW_MONITOR:READ` | **2026-07-21 新增**：流程转交历史，返回 `List<TransferHistoryDTO>`（含全部终态与拒绝原因、三方姓名），供监控详情抽屉「转交历史」区块消费 |
| `GET` | `/api/workflow/transfers/inbox` | 无 | 无 | 转交收件箱（当前用户待认领） |
| `POST` | `/api/workflow/transfers/{id}/accept` | 无 | 无（服务层校验接收人本人） | 认领转交 |
| `POST` | `/api/workflow/transfers/{id}/decline` | `TransferDecisionReqDTO` | 无（服务层校验接收人本人） | 拒绝转交（理由必填） |
| `GET` | `/api/workflow/transfers/outbox` | 无 | 无 | 转交发件箱（当前用户发起） |
| `POST` | `/api/workflow/transfers/{id}/cancel` | 无 | 无（服务层校验发起人本人） | 撤回转交 |

关键实现要点（源码 `TaskTransferService`）：

- 发起后落 `WF_TASK_TRANSFER` 表 `status=PENDING_ACCEPT`，**不**立即改变 Flowable 任务 `assignee`；`fromEmpId`（被转出者）取任务当前 `assignee`，区别于 `initiatorEmpId`（发起人，秘书代发起场景下二者不同）。
- 转交待认领期间，原任务对 `TaskController` 的 claim/approve/reject 及无会话 `approveByEmp`/`rejectByEmp` 全部只读（抛 `WF-40913`）。
- 接收人资格校验（`isEligibleReceiver`）以任务实际 `candidate` 身份链接为权威依据（`TaskAssignmentListener` 可能已按发起机构收窄候选范围），只有任务完全没有 candidate 身份链接时才回退到节点原始配置，避免放行越权接收人。
- 认领/拒绝/撤回均为乐观状态流转（`updateStatusIfPending`），并发败者拦截为 `WF-40915`；认领/拒绝前会校验原 Flowable 任务是否仍存在，任务已被其它路径（撤单/无会话审批完成）删除时把转交置 `INVALIDATED` 终态并抛 `WF-40916`，避免脏 500 与僵尸转交记录。
- 完整错误码清单、`TransferItemDTO`/`TransferInitiateReqDTO`/`TransferDecisionReqDTO` 字段定义见 03 文档 H 分组。

---

## 6. 内部事件契约

### 6.1 当前实际发布的 Spring 事件（2026-07-19 全量核实）

| 事件 | 类型 | 发布方 | 载荷 |
|---|---|---|---|
| `ProcessStartedEvent` | `ProcessStartService` 内嵌 `record` | `ProcessStartService` | `processInstanceId`、`businessKey`、`bizType` |
| `ProcessCompletedEvent` | `api.event` 包顶级 `record`（2026-04-29 由 `ProcessCompletedListener` 内部类迁出，**订正**：原文档误标为 `ProcessCompletedListener.ProcessCompletedEvent`，实际类路径是 `com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent`） | ① `ProcessCompletedListener`（BPMN 自然结束）；② `TaskOperationService.doReject`（驳回强制终止流程时手动 publish，**2026-07-19 补充**：此前文档未提及第二个发布点） | `processInstanceId`、`businessKey`、`outcome`（`APPROVED`/`REJECTED`）、`reason` |
| `ProcessWithdrawnEvent` | `api.event` 包顶级 `record`（**2026-07-19 新增记录，此前文档完全未收录**） | `ProcessCommandService.cancelProcess`（REST/`WorkflowApi.cancelProcess` 共享同一实现） | `processInstanceId`、`businessKey`、`withdrawnByEmpId`、`currentAssigneeEmpId`（可能为 null）、`opinion` |
| `TaskApprovedEvent` | `TaskOperationService` 内嵌 `record` | `TaskOperationService` | `taskId`、`processInstanceId`、`empId` |
| `TaskRejectedEvent` | `TaskOperationService` 内嵌 `record` | `TaskOperationService` | `taskId`、`processInstanceId`、`empId` |

### 6.2 当前没有发布的旧事件名称

以下事件名称在旧稿中出现过，但当前代码未发布：

- `TaskCreatedEvent`
- `ProcessTerminatedEvent`
- 独立的 `workflow.process.completed.v1` 包装 DTO（带 `result`、`lastApprover`、`variables` 等）
- `TaskOperationService.TaskTransferredEvent`（**2026-07-19 订正**：原文档收录此事件，但随旧单阶段转交机制于 2026-07-13 一并下线，当前 `TaskOperationService` 源码中已无此 `record` 定义，不会再被发布；两阶段转交的认领/拒绝/撤回结果目前**不**通过 Spring 事件对外广播，只在 `TaskTransferService` 内部直接调用 `NotifyApi` 发通知，跨模块如需订阅需自行扩展）

### 6.3 终态语义

- `ProcessCompletedListener` 会读取流程变量 `approved`。
- 若 `approved == false`，会把 `biz_process_map.process_status` 写成 `CANCELLED`。
- 其他结束场景写成 `COMPLETED`。

这意味着"驳回"和"撤回"在当前持久化层都可能表现为 `CANCELLED`，消费方若需要更细粒度结果，需要二次查询（`WorkflowApi.getProcessOutcome`，见 §2.8）或依赖 `ProcessCompletedEvent.outcome`/`ProcessWithdrawnEvent` 区分。

---

## 7. 上游依赖

| 依赖接口 | 来源模块 | 用途 |
|---|---|---|
| `CurrentUserApi` | auth-permission-center | 当前用户、机构、管理员判定、候选组键 |
| `UserApi` | auth-permission-center | 用户姓名查询、按任意 empId 计算候选组标识（两阶段转交接收人资格校验用） |
| `OrgApi` | auth-permission-center | 主机构查询（审批流监控批量补全机构编码、两阶段转交机构校验均依赖） |
| `CalendarApi` | system-governance-center | SLA 工作日计算 |
| `NotifyApi` | system-governance-center | 任务创建后发送通知；两阶段转交认领/拒绝/撤回结果通知 |

---

## 8. 兼容性与落地说明

- 旧文档中的 `WorkflowConfigApi`、`WorkflowParticipantService` 仍可作为**规划态目标名**保留，但不能写成当前已落地 Java Bean。
- 其他模块若需要"待办列表 / 节点表单 / 流程图 / 历史节点 / 流程映射"等只读能力，应优先走 `WorkflowQueryApi`；按 businessKey 反查任务元信息应优先走 `TodoQueryApi`（2026-07-19 起本文档正式收录）；参与者判定等尚未公开的能力仍需补齐新的 Java `*Api` 或通过标准 REST 解决。
- `TaskController`、`ProcessController`、`ProcessCommandController`、`WorkflowAdminController`、`FlowDesignController`、`ProcessMonitorController`、`TaskTransferController` 属于当前真实联调入口；但按项目分层规则，业务模块之间仍应优先通过正式 `*Api` 契约协作。
- 任何新增对外能力，都应先补 `api/` 接口与 DTO，再同步本文件、`03-接口设计与报文.md` 及依赖模块文档——本次回填过程中发现审批流程设计器/审批流监控/两阶段转交三组控制器上线时均未同步这两份文档，导致 2026-04 快照与 2026-07 代码现状产生 3 个月的漂移，请后续迭代避免重演。
