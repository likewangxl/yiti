# Workflow-Center 设计规格

## 概述

**目标**：实现工作流中心模块，作为 Flowable 7.x 的唯一集成边界，提供流程发起、待办/已办、任务审批、SLA 计算等能力。

**架构**：嵌入式 Flowable 7.0.1 + Spring Boot 3.2.3 + MyBatis + Redis，遵循模块化单体架构。workflow-center 不保存业务主数据，只维护流程映射和审批状态。

**公共开发规范**：遵循 `docs/common-dev-guide.md`

---

## 分阶段交付

| Phase | 内容 | 核心能力 |
|-------|------|---------|
| 1 | 脚手架 + 4 自定义表 + CandidateResolver + ProcessStartService | 流程发起 |
| 2 | TaskOperation + TodoQuery + SLA + Flowable Listeners | 审批/待办/SLA |
| 3 | Controllers + FlowableConfig + AutoConfiguration | REST 端点、引擎配置 |

---

## 包结构

```
com.bank.branch.platform.workflow/
├── api/              # WorkflowApi + DTOs + Events
│   ├── dto/          # StartProcessCmd, WorkflowLaunchResp, TaskRespDTO 等
│   └── event/        # 领域事件
├── controller/       # REST 控制器
├── facade/           # Api 实现
├── service/          # 核心业务逻辑
├── listener/         # Flowable 事件监听器
├── mapper/           # MyBatis Mapper
├── entity/           # 自定义表实体
├── enums/            # WfErrorCode, ProcessStatus
└── config/           # Flowable + WebMvc 配置
```

---

## 依赖关系

**Maven 依赖**：
- `auth-permission-center`：CurrentUserApi, ResourceApi
- `system-governance-center`：CalendarApi, DictApi, NotifyApi
- `common`（5 个子模块）
- `flowable-spring-boot-starter` 7.0.1
- spring-boot-starter-web, spring-boot-starter-data-redis, mybatis-spring-boot-starter
- knife4j, lombok, spring-boot-starter-test

---

## Phase 1：流程发起能力

### 自定义表实体

**BizProcessMap** → 表 `biz_process_map`

| 字段 | Java 类型 | 说明 |
|------|----------|------|
| id | String UUID | 主键 |
| bizType | String | 业务类型 |
| bizId | String | 业务实体 ID |
| businessKey | String | 业务键（唯一标识一次流程） |
| processDefinitionKey | String | 流程定义 Key |
| processInstanceId | String | Flowable 流程实例 ID |
| status | String | RUNNING/COMPLETED/CANCELLED |
| title | String | 流程标题 |
| startUser | String | 发起人 empId |
| startOrgId | String | 发起人机构 |
| currentAssignee | String | 当前办理人 |
| candidateGroups | String | 候选组 JSON |
| createdTime | LocalDateTime | 创建时间 |
| updatedTime | LocalDateTime | 更新时间 |

**WfNodeCandidateConf** → 表 `wf_node_candidate_conf`

| 字段 | Java 类型 | 说明 |
|------|----------|------|
| id | String UUID | 主键 |
| processDefinitionKey | String | 流程定义 Key |
| nodeId | String | 节点 ID |
| candidateType | String | ROLE/ORG/USER |
| candidateValue | String | JSON 数组 |
| createdTime | LocalDateTime | 创建时间 |
| updatedTime | LocalDateTime | 更新时间 |

**WfNodeFormConf** → 表 `wf_node_form_conf`

| 字段 | Java 类型 | 说明 |
|------|----------|------|
| id | String UUID | 主键 |
| processDefinitionKey | String | 流程定义 Key |
| nodeId | String | 节点 ID |
| formFields | String | 所有字段 JSON |
| editableFields | String | 可编辑字段 JSON |
| requiredFields | String | 必填字段 JSON |
| createdTime | LocalDateTime | 创建时间 |
| updatedTime | LocalDateTime | 更新时间 |

**WfTimeoutRule** → 表 `wf_timeout_rule`

| 字段 | Java 类型 | 说明 |
|------|----------|------|
| id | String UUID | 主键 |
| processDefinitionKey | String | 流程定义 Key |
| nodeId | String | 节点 ID |
| timeoutHours | Integer | 超时小时数（RED 阈值） |
| warningHours | Integer | 预警小时数（YELLOW 阈值） |
| createdTime | LocalDateTime | 创建时间 |
| updatedTime | LocalDateTime | 更新时间 |

### CandidateResolverService

- `resolveCandidates(String processDefinitionKey, String nodeId)` → `List<String>`
  - 查 wf_node_candidate_conf
  - ROLE → `ROLE:{roleCode}`, ORG → `ORG:{orgCode}`, USER → `USER:{empId}`

### ProcessStartService

- `startProcess(StartProcessCmd cmd)` → `WorkflowLaunchResp`：
  1. 校验 processDefinitionKey 存在（RepositoryService）→ WF-40401
  2. 校验 businessKey 无 RUNNING 实例 → WF-40901
  3. RuntimeService.startProcessInstanceByKey()
  4. 写入 biz_process_map（status=RUNNING）
  5. 获取第一个 userTask taskId
  6. 发布事件 `workflow.process.started.v1`
  7. 返回 WorkflowLaunchResp

### WorkflowApi

```java
public interface WorkflowApi {
    WorkflowLaunchResp startProcess(StartProcessCmd cmd);
    BizProcessMapDTO getProcessByBusinessKey(String businessKey);
    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);
}
```

---

## Phase 2：审批 / SLA 能力

### TaskOperationService

- `claimTask(taskId, empId)` — Flowable claim，已签收 → WF-40904
- `approveTask(taskId, empId, variables, comment)` — 校验 assignee（WF-40903），complete + comment，发布事件
- `rejectTask(taskId, empId, comment)` — 设 approved=false，complete，发布事件
- `transferTask(taskId, fromEmpId, toEmpId, reason)` — setAssignee，addComment，发布事件

### TodoQueryService

- `queryTodoList(empId, bizType, keyword, pageNo, pageSize)` → PageResult<TaskRespDTO>
  - TaskService.createTaskQuery().taskCandidateOrAssigned(empId)
  - 关联 biz_process_map 获取业务信息
  - 计算 SLA 状态
- `queryDoneList(empId, bizType, keyword, pageNo, pageSize)` → PageResult<TaskRespDTO>
  - HistoryService.createHistoricTaskInstanceQuery().taskAssignee(empId).finished()
- `getTaskDetail(taskId, empId)` → TaskDetailRespDTO
  - 任务信息 + 审批意见 + 表单权限 + SLA

### SlaCalculationService

- `calculateSlaStatus(processDefinitionKey, nodeId, taskCreateTime)` → SlaStatus(GREEN/YELLOW/RED)
  - 查 wf_timeout_rule
  - CalendarApi.countWorkingDays() 计算已过工时
  - GREEN < warningHours, YELLOW < timeoutHours, RED >= timeoutHours

### Flowable Listeners

**TaskAssignmentListener** (TaskListener)
- 任务创建 → CandidateResolverService → 设置候选组 → 发通知

**ProcessCompletedListener** (ExecutionListener)
- 流程结束 → 更新 biz_process_map.status=COMPLETED → 发布事件

---

## Phase 3：Controllers + Config

### Controllers

| Controller | 路径 | 端点 |
|-----------|------|------|
| TaskController | `/api/workflow/tasks` | GET /todo, GET /done, GET /{taskId}, POST /{taskId}/claim, POST /{taskId}/approve, POST /{taskId}/reject, POST /{taskId}/transfer |
| ProcessController | `/api/workflow/processes` | GET /{businessKey}, GET /biz/{bizType}/{bizId} |
| WorkflowAdminController | `/api/admin/workflow` | CRUD for candidate-configs, form-configs, timeout-rules |

### FlowableConfig

- 嵌入式引擎，history-level=audit
- 注册 TaskAssignmentListener + ProcessCompletedListener
- 禁用 Flowable IDM（用 auth-permission-center 代替）

---

## 错误码

| 错误码 | HTTP | 说明 |
|--------|------|------|
| WF-40401 | 404 | 流程定义不存在 |
| WF-40402 | 404 | 流程实例不存在 |
| WF-40403 | 404 | 任务不存在 |
| WF-40404 | 404 | 候选组配置不存在 |
| WF-40405 | 404 | 超时规则不存在 |
| WF-40901 | 409 | businessKey 已有 RUNNING 实例 |
| WF-40902 | 409 | 任务非当前用户 |
| WF-40903 | 403 | 非任务办理人 |
| WF-40904 | 409 | 任务已被签收 |
| WF-50001 | 500 | 流程引擎异常 |

---

## 领域事件

| 事件 | 触发时机 |
|------|---------|
| `workflow.process.started.v1` | 流程发起成功 |
| `workflow.process.completed.v1` | 流程结束 |
| `workflow.task.approved.v1` | 审批通过 |
| `workflow.task.rejected.v1` | 驳回 |
| `workflow.task.transferred.v1` | 转办 |

---

## 测试策略

- Service 层：Mock Flowable RuntimeService/TaskService/HistoryService/RepositoryService
- Listener 层：Mock DelegateTask/DelegateExecution
- Controller 层：MockMvcBuilders.standaloneSetup
- TDD 驱动：先写失败测试 → 最小实现 → 重构
