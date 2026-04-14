# workflow-center/AGENTS.md

本文件为 `workflow-center` 模块提供当前实现态上下文。此目录下的代码、文档与测试统一使用 UTF-8；若设计文档与代码冲突，以 `src/main/java`、`src/test/java` 和 `docs/modules/workflow-center/04-对外API契约.md` 为准。

## 模块定位

- `workflow-center` 是平台内**唯一**直接调用 Flowable API 的模块。
- 当前模块同时提供三类能力：
  - `WorkflowApi`：跨模块同步 Java 契约。
  - `/api/workflow/**`：面向前端、联调和真实环境测试的 REST 入口。
  - `/api/admin/workflow/**`：流程配置管理入口。
- 近期 Flowable 联调改动后，涉及控制器、DTO、事件、权限或流程映射的调整，必须同步本文件和 `docs/modules/workflow-center/04-对外API契约.md`。

## 当前公开契约

### 跨模块 Java API

当前真正对外公开并有实现的 Java 接口只有 `WorkflowApi`：

- `startProcess(StartProcessCmd cmd)`
- `cancelProcess(String processInstanceId, String reason)`
- `getProcessByBusinessKey(String businessKey)`
- `getProcessByBizTypeAndBizId(String bizType, String bizId)`

### 当前未公开为 Java API 的能力

下列名称在旧文档或规划稿中出现过，但**当前代码库中没有对应对外接口/Facade**：

- `WorkflowQueryApi`
- `WorkflowConfigApi`
- `WorkflowParticipantService`

对应能力目前分别由以下实现承担：

- 任务查询/办理、流程查询：`TaskController`、`ProcessController`、`ProcessMapController`
- 管理配置：`WorkflowAdminController`
- 参与者与候选组判定：控制器/服务内部逻辑，未抽成对外 `*Api`

## 代码结构速览

```text
workflow-center/
├─ src/main/java/com/bank/branch/platform/workflow/
│  ├─ api/                      # 仅 WorkflowApi + DTO
│  ├─ config/                   # FlowableConfig
│  ├─ controller/               # 5 个 REST 控制器
│  │  ├─ ProcessCommandController
│  │  ├─ ProcessController
│  │  ├─ ProcessMapController
│  │  ├─ TaskController
│  │  └─ WorkflowAdminController
│  ├─ entity/                   # BizProcessMap / WfNodeCandidateConf / WfNodeFormConf / WfTimeoutRule
│  ├─ enums/                    # ProcessStatus / SlaStatus / WfErrorCode
│  ├─ facade/                   # WorkflowFacade（WorkflowApi 实现）
│  ├─ listener/                 # TaskAssignmentListener / ProcessCompletedListener
│  ├─ mapper/                   # 4 个 MyBatis Mapper
│  └─ service/                  # 8 个 Service
│     ├─ CandidateResolverService
│     ├─ ProcessCommandService
│     ├─ ProcessQueryService
│     ├─ ProcessStartService
│     ├─ SlaCalculationService
│     ├─ TaskOperationService
│     ├─ TodoQueryService
│     └─ WorkflowAdminService
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

- `POST /api/workflow/processes/submit`
- `POST /api/workflow/processes/{processInstanceId}/cancel`
- `GET /api/workflow/processes/{processInstanceId}`
- `GET /api/workflow/processes/{processInstanceId}/diagram`
- `GET /api/workflow/processes/{processInstanceId}/history`
- `GET /api/workflow/processes/{processInstanceId}/nodes`
- `GET /api/workflow/process-map`

### 管理端

- `GET/POST/PUT /api/admin/workflow/timeout-rules`
- `GET /api/admin/workflow/timeout-rules/item/{id}`
- `GET/POST/PUT /api/admin/workflow/node-candidates`
- `GET /api/admin/workflow/node-candidates/item/{id}`
- `GET/POST/PUT /api/admin/workflow/node-forms`
- `GET /api/admin/workflow/node-forms/item/{id}`
- `GET /api/admin/workflow/process-definitions`

## 关键依赖

### auth-permission-center

- `CurrentUserApi`：当前用户、当前机构、系统管理员判定、候选组键
- `UserApi`：员工姓名查询
- `OrgApi`：主机构信息查询

### system-governance-center

- `CalendarApi`：SLA 工作日计算
- `NotifyApi`：任务创建后的通知发送

## 当前实现注意事项

- `TaskController` 暴露了 `keyword` 查询参数，但 `TodoQueryService` 当前仅对 `bizType` 做后置过滤，`keyword` 尚未真正参与查询。
- `TaskDetailRespDTO.runtimeAccess` 当前是轻量实现：`canApprove/canReject/canTransfer` 主要基于当前 `assignee`，候选组交集尚未完整判定。
- `TaskDetailRespDTO.processProgress` 当前返回空列表，占位字段尚未补齐真实历史节点数据。
- `ProcessCompletedListener` 会将 `approved=false` 的终态写成 `CANCELLED`，不是旧文档中的 `REJECTED`。
- 管理端候选人/表单配置的创建与更新请求已存在，但序列化/落库格式请以真实代码和测试为准，不要沿用旧设计稿假设。

## 修改前后必须同步的文档

- `docs/modules/workflow-center/03-接口设计与报文.md`
- `docs/modules/workflow-center/04-对外API契约.md`
- `docs/modules/workflow-center/08-初始化数据清单.md`
- `docs/modules/workflow-center/09-依赖契约摘要.md`

## 额外约束

- 新增或修改 REST 端点时，除同步文档外，还要同步 `PT_RESOURCE` 种子数据与相关权限测试。
- 新增对外 Java 能力时，必须优先定义 `*Api`/DTO，再考虑其他模块接入；不要让其他模块直接依赖本模块内部 `service`/`mapper`。
