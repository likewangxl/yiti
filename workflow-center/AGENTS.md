<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-03 | Updated: 2026-07-12 -->
# workflow-center/AGENTS.md

本文件为 `workflow-center` 模块提供当前实现态上下文。此目录下的代码、文档与测试统一使用 UTF-8；若设计文档与代码冲突，以 `src/main/java`、`src/test/java` 和 `docs/modules/workflow-center/04-对外API契约.md` 为准。

## 模块定位

- `workflow-center` 是平台内**唯一**直接调用 Flowable API 的模块。
- 当前模块同时提供四类能力：
  - `WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi`：跨模块同步 Java 契约（3 个接口）。
  - `/api/workflow/**`：面向前端、联调和真实环境测试的 REST 入口。
  - `/api/admin/workflow/**`：候选人/超时/表单等静态流程配置管理入口。
  - `/api/admin/workflow/flows/**`：可视化审批流程设计器管理入口（草稿 CRUD、发布到 Flowable、变量目录、已部署流程反向导入）。
- 近期 Flowable 联调改动后，涉及控制器、DTO、事件、权限或流程映射的调整，必须同步本文件和 `docs/modules/workflow-center/04-对外API契约.md`。

## 当前公开契约

### 跨模块 Java API

当前真正对外公开并有实现的 Java 接口共 3 个：`WorkflowApi`、`WorkflowQueryApi`、`TodoQueryApi`。

**WorkflowApi**（流程启动/控制 + 无会话审批 + 设计器解析）：

- `startProcess(StartProcessCmd cmd)` / `cancelProcess(processInstanceId, reason)`
- `getProcessByBusinessKey(...)` / `getProcessByBizTypeAndBizId(...)`
- `getProcessOutcome(processInstanceId)`：查询已完成流程的审批结论（`Optional<"APPROVED"|"REJECTED">`），供补偿场景读取 Flowable 历史变量 `approved`
- `approveByEmp(taskId, empId, opinion[, formData])` / `rejectByEmp(taskId, empId, opinion)`：**无会话版**，供 SOAP 网关 / callpu 等外部渠道按显式 `empId` 调用，不依赖登录态 ThreadLocal、不校验 assignee；带 `formData` 版本用于把节点路由变量（如 `corpRouteTo`/`finRouteTo`）写入 `complete`，供排他网关分支
- `resolveDesignerProcDefKey(flowKey)`：按设计器流程 `flowKey` 解析其「已发布」的 Flowable procDefKey（`DSN_` 前缀），流程未发布时抛 WF-40401

**WorkflowQueryApi**（只读查询）：

- `queryTodoList(...)` / `queryDoneList(...)` / `countPendingTasks(empId)` / `listRecentPendingTasks(empId, limit)`
- `getTaskDetail(taskId, empId)` / `getProcessHistory(processInstanceId)` / `getProcessNodes(processInstanceId)`
- `getProcessByBusinessKey(...)` / `getProcessByBizTypeAndBizId(...)`
- `queryParticipatedBusinessKeys(empId, processDefinitionKeyPrefix, timeWindowDays, limit)`：按 `HistoricProcessInstanceQuery.involvedUser` + 当前候选组未领取任务合并查参与过的 businessKey 集合（去重），Fail-safe：empId 为空返回空集，limit 兜底/截断 10000

**TodoQueryApi**（待办/已办按 businessKey 反查，供 perf 等模块二次过滤）：

- `listMyTodoBusinessKeys(empId, bizType)` / `findTaskRespByBusinessKeys(empId, businessKeys)`：会话版，走 `CurrentUserApi` 候选组
- `listMyDoneBusinessKeys(empId, bizType)` / `findDoneTaskRespByBusinessKeys(empId, businessKeys)`：已办，走 `HistoryService`
- `listTodoBusinessKeysByEmp(empId, bizType)` / `findTaskRespByBusinessKeysByEmp(empId, businessKeys)`：**无会话版**，候选组按传入 `empId` 实时查库解析，供 SOAP 网关 / callpu 等无登录态链路使用

### 当前未公开为 Java API 的能力

下列名称在旧文档或规划稿中出现过，但**当前代码库中仍没有对应对外接口/Facade**：

- `WorkflowConfigApi`
- `WorkflowParticipantService`

对应能力目前分别由以下实现承担：

- 任务查询/办理、流程查询、流程命令：`TaskController`、`ProcessController`、`ProcessMapController`、`ProcessCommandController`
- 静态配置管理：`WorkflowAdminController`（候选人/超时/表单/流程定义列表）
- 可视化流程设计器管理：`FlowDesignController`（草稿 CRUD、发布、变量目录、已部署流程导入）
- 参与者与候选组判定：`CandidateResolverService` + `TaskAssignmentListener`（单实例任务）+ `MultiInstanceApproverResolver`（会签 MI 任务），未抽成对外 `*Api`

## 代码结构速览

```text
workflow-center/
├─ src/main/java/com/bank/branch/platform/workflow/
│  ├─ api/                      # WorkflowApi / WorkflowQueryApi / TodoQueryApi
│  │  ├─ dto/                   # 业务 DTO（含 outgoingBranches 等 Task 2.6 新增字段）
│  │  ├─ dto/flow/               # 设计器专用 DTO：FlowDefDTO/FlowGraphDTO/FlowNodeDTO/FlowEdgeDTO/FlowApproverDTO/FlowConditionDTO/FlowVariableDTO
│  │  └─ event/                  # ProcessCompletedEvent / ProcessWithdrawnEvent
│  ├─ config/                   # FlowableConfig
│  ├─ controller/               # 6 个 REST 控制器
│  │  ├─ FlowDesignController        # 可视化审批流程设计器管理（/api/admin/workflow/flows/**）
│  │  ├─ ProcessCommandController    # 提交/撤回流程命令（/api/workflow/processes/submit|cancel）
│  │  ├─ ProcessController           # 流程实例详情/进度图/历史查询
│  │  ├─ ProcessMapController        # 按业务键查流程映射（/api/workflow/process-map）
│  │  ├─ TaskController              # 任务操作（待办/已办, 签收, 审批, 驳回, 转办）
│  │  └─ WorkflowAdminController     # 静态配置管理端（候选人配置, 超时规则, 表单配置, 流程定义列表）
│  ├─ entity/                   # 8 个实体
│  │  ├─ BizProcessMap / WfNodeCandidateConf / WfNodeFormConf / WfTimeoutRule
│  │  └─ WfFlowDef / WfFlowNode / WfFlowEdge / WfFlowNodeApprover   # 审批流程设计器（草稿/图形数据）
│  ├─ enums/                    # ProcessStatus / SlaStatus / WfErrorCode
│  ├─ facade/                   # WorkflowFacade / WorkflowQueryFacade / TodoQueryFacade
│  ├─ listener/                 # 3 个 Flowable 监听器
│  │  ├─ TaskAssignmentListener          # 单实例任务创建时解析候选人
│  │  ├─ MultiInstanceApproverResolver   # 会签(MI)入口监听器，展开 approverEmpIds 流程变量
│  │  └─ ProcessCompletedListener        # 流程结束时更新状态 + 发布事件
│  ├─ mapper/                   # 8 个 MyBatis-Plus Mapper（含 WfFlowDef/WfFlowNode/WfFlowEdge/WfFlowNodeApprover）
│  └─ service/                  # 8 个核心 Service
│     ├─ CandidateResolverService / ProcessCommandService / ProcessQueryService / ProcessStartService
│     ├─ SlaCalculationService / TaskOperationService / TodoQueryService / WorkflowAdminService
│     └─ flow/                  # 审批流程设计器 8 个 Service
│        ├─ FlowDefService            # 草稿 CRUD + 整图替换保存
│        ├─ FlowPublishService        # 校验 → 生成 BPMN → 部署（DSN_ 影子 KEY）→ 回填候选人配置
│        ├─ FlowImportService         # 已部署 Flowable 流程反向导入为只读设计器模型
│        ├─ FlowBpmnGenerator         # DTO 图形 → Flowable BpmnModel
│        ├─ FlowConditionExpressionBuilder / FlowValidator / FlowVariableCatalog
│        └─ DesignerFlowLookupService # 按 flowKey 反查已发布 procDefKey
└─ src/test/java/...            # controller / service / listener 测试 + bootstrap 集成测试
```

## REST 入口总览

### 任务相关

- `GET /api/workflow/tasks`
- `GET /api/workflow/tasks/done`
- `GET /api/workflow/tasks/{taskId}`
- `POST /api/workflow/tasks/{taskId}/claim`
- `POST /api/workflow/tasks/{taskId}/approve`
- `POST /api/workflow/tasks/{taskId}/reject`
- `POST /api/workflow/tasks/{taskId}/transfer`

### 流程相关

- `POST /api/workflow/processes/submit`（`ProcessCommandController`）
- `POST /api/workflow/processes/{processInstanceId}/cancel`（`ProcessCommandController`）
- `GET /api/workflow/processes/{processInstanceId}`（`ProcessController`）
- `GET /api/workflow/processes/{processInstanceId}/diagram`（PNG，`ProcessController`）
- `GET /api/workflow/processes/{processInstanceId}/history`（`ProcessController`）
- `GET /api/workflow/processes/{processInstanceId}/nodes`（`ProcessController`）
- `GET /api/workflow/process-map`（`ProcessMapController`，`businessKey` 或 `bizType`+`bizId` 二选一）

### 管理端（静态配置，`WorkflowAdminController`，全部 `@BizAuth(SYS_CONFIG, CONFIG)`）

- `GET/POST/PUT /api/admin/workflow/timeout-rules`，`GET /api/admin/workflow/timeout-rules/item/{id}`
- `GET/POST/PUT /api/admin/workflow/node-candidates`，`GET /api/admin/workflow/node-candidates/item/{id}`
- `GET/POST/PUT /api/admin/workflow/node-forms`，`GET /api/admin/workflow/node-forms/item/{id}`
- `GET /api/admin/workflow/process-definitions`

### 管理端（可视化流程设计器，`FlowDesignController`，全部 `@BizAuth(SYS_CONFIG, CONFIG)`）

- `GET /api/admin/workflow/flows` — 流程定义列表（不含图形数据）
- `GET /api/admin/workflow/flows/{id}` — 获取完整流程图（节点+连线+审批人）
- `POST /api/admin/workflow/flows` — 新建草稿
- `PUT /api/admin/workflow/flows/{id}` — 整图替换保存
- `POST /api/admin/workflow/flows/{id}/publish` — 发布到 Flowable（DRAFT→PUBLISHED）
- `DELETE /api/admin/workflow/flows/{id}` — 删除草稿（仅 DRAFT 且非只读导入）
- `GET /api/admin/workflow/flows/meta/variables` — 条件构造器可用流程变量目录
- `GET /api/admin/workflow/flows/meta/approver-variables` — VAR 审批人可选变量目录
- `POST /api/admin/workflow/flows/import-existing` — 导入内置 3 个已部署流程（`perf_target_adjust_v1`/`perf_alloc_adjust_corp_v1`/`perf_alloc_adjust_retail_v1`），单 key 失败不阻断其它

## 关键依赖

### auth-permission-center

- `CurrentUserApi`：当前用户、当前机构、系统管理员判定、候选组键
- `UserApi`：员工姓名查询；`getEmpIdsByRoleCode(roleCode)` 用于 `TaskAssignmentListener`/`MultiInstanceApproverResolver` 把 `ROLE:xxx` 候选规则展开成员工 ID
- `OrgApi`：主机构信息查询

### system-governance-center

- `CalendarApi`：SLA 工作日计算
- `NotifyApi`：任务创建后的通知发送

## 当前实现注意事项

- `TaskController` 暴露了 `keyword` 查询参数，但 `TodoQueryService` 当前仅对 `bizType` 做后置过滤，`keyword` 尚未真正参与查询（`queryTodoList`/`queryDoneList` 均未使用 keyword 变量）。
- `TaskDetailRespDTO.runtimeAccess` 当前仍是轻量实现：`canApprove/canReject/canTransfer` 只基于 `isAssignee`（源码 TODO 标注"需检查候选人"），候选组交集尚未完整判定。
- `TaskDetailRespDTO.processProgress` 当前仍返回空列表（源码 TODO：待通过 `HistoryService` 查询历史活动节点），占位字段尚未补齐。
- `TaskDetailRespDTO.outgoingBranches`（新增字段）仅当任务的 `processDefinitionKey` 以 `DSN_` 前缀（即设计器发布的动态流程）才会反查设计器图并填充「下一步走向」分支选项；静态 BPMN 任务恒为空列表，任何解析异常均吞掉降级为空列表。
- `ProcessCompletedListener` 把 Flowable `approved` 变量转成两套独立语义：`biz_process_map.processStatus` 写 `COMPLETED`/`CANCELLED`（workflow 自身状态机），而 `ProcessCompletedEvent.outcome` 写 `APPROVED`/`REJECTED`（下游业务语义），二者不可混用。
- 会签（MI）节点：完成条件 EL 引用 `rejected` 变量，但 `approved=true` 路径从不设置该变量，`MultiInstanceApproverResolver` 在入口监听器里显式初始化 `rejected=false`（不覆盖已存在值）以避免 `PropertyNotFoundException`。
- 审批节点经无名连线连到网关时，出边分支解析会穿透网关取其命名出边（`FlowBpmnGenerator`/出边解析逻辑），避免设计器"下一步走向"选项丢失。
- 管理端候选人/表单配置的创建与更新请求已存在，但序列化/落库格式请以真实代码和测试为准，不要沿用旧设计稿假设。

## 修改前后必须同步的文档

- `docs/modules/workflow-center/03-接口设计与报文.md`
- `docs/modules/workflow-center/04-对外API契约.md`
- `docs/modules/workflow-center/08-初始化数据清单.md`
- `docs/modules/workflow-center/09-依赖契约摘要.md`

## 额外约束

- 新增或修改 REST 端点时，除同步文档外，还要同步 `PT_RESOURCE` 种子数据与相关权限测试。
- 新增对外 Java 能力时，必须优先定义 `*Api`/DTO，再考虑其他模块接入；不要让其他模块直接依赖本模块内部 `service`/`mapper`。
- 无会话版方法（`approveByEmp`/`rejectByEmp`/`listTodoBusinessKeysByEmp`/`findTaskRespByBusinessKeysByEmp`）不依赖登录态，调用方（SOAP 网关/callpu）必须自行完成鉴权与候选人可见性校验后再调用，避免越权。
- 修改 `service/flow/**`（BPMN 生成、发布、导入）前，先确认改动是否影响已发布的影子流程定义（`DSN_` 前缀）及其候选人配置的"先删后插"语义，避免线上运行中流程被误发布覆盖。
