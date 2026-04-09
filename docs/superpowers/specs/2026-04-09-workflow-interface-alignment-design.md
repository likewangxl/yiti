# Workflow Center 接口对齐设计文档

> 版本：V1.0 | 日期：2026-04-09
> 目标：将 workflow-center 所有 REST 接口的请求/响应 DTO 和 Service 逻辑与设计文档 `03-接口设计与报文.md` 完全对齐。

---

## 1. 背景与目标

设计文档 `03-接口设计与报文.md`（V1.0, 2026-04-02）定义了 workflow-center 所有 HTTP 接口的字段级精度。经实际代码对比分析，存在以下两类差异：

1. **DTO 字段差异**：字段缺失、字段名不一致、类型不匹配（JSON 字符串 vs 对象数组）
2. **Service 逻辑缺失**：任务详情缺少 `runtimeAccess` 计算、`processProgress` 节点列表构建、审批日志 action 区分等

本文档定义完整的修复方案，确保实际接口与设计文档完全一致。

---

## 2. 修复范围

### 2.1 待办/已办查询（TaskController, A.1 / A.2）

#### TaskRespDTO 字段补全

| 字段 | 当前状态 | 目标状态 | 修复方式 |
|:---|:---|:---|:---|
| `bizId` | 存在 ✅ | 存在 ✅ | 无需修改 |
| `title` | 当前用 `businessKey` | 需从 `BizProcessMap.title` 获取真实标题 | 修改 `TodoQueryService.convertTaskToDTO` |
| `startUserName` | ❌ 缺失 | ✅ 需补充 | 调用 `UserApi.getUserName(startUser)` |
| `startOrgName` | ❌ 缺失 | ✅ 需补充 | 调用 `OrgApi.getOrgName(startUser)` |
| `startTime` | ❌ 缺失 | ✅ 需补充 | 从 `BizProcessMap.startTime` 获取 |
| `warningTime` | ❌ 缺失 | ✅ 需补充 | 由 `SlaCalculationService.calculateWarningTime()` 提供 |
| `timeoutTime` | ❌ 缺失 | ✅ 需补充 | 由 `SlaCalculationService.calculateTimeoutTime()` 提供 |
| `candidateGroups` | 当前为 `String`（JSON） | 改为 `List<String>` | 修改 `TaskRespDTO` 类型 + 修改 mapper SQL |
| `slaStatus` | ✅ 存在 | 字段名对齐为 `slaStatus` ✅ | 无需修改 |
| `claimable` | ✅ 存在 | ✅ | 无需修改 |

#### 已办列表额外字段

| 字段 | 当前状态 | 目标状态 | 修复方式 |
|:---|:---|:---|:---|
| `completeTime` | ❌ 缺失 | ✅ 需补充 | 从 `HistoricTaskInstance.endTime` 获取 |
| `approvalResult` | ❌ 缺失 | ✅ 需补充 | 从 Flowable 变量 `approved` 获取（true=APPROVE, false=REJECT） |
| `opinion` | ❌ 缺失 | ✅ 需补充 | 从历史任务的 Comment 获取 |

**说明**：已办列表和待办列表复用同一个 `TaskRespDTO`，通过 `completeTime` 是否为 null 来区分。

---

### 2.2 任务详情（TaskController, A.3）

#### TaskDetailRespDTO 结构改造

设计文档的 `nodeFormConf` 为嵌套对象结构，实际为 JSON 字符串。改造方案：

| 字段 | 当前 | 目标 |
|:---|:---|:---|
| `nodeFormConf.processDefinitionKey` | 缺失 | 从 task.getProcessDefinitionId() 提取 |
| `nodeFormConf.nodeKey` | 缺失 | 从 task.getTaskDefinitionKey() 获取 |
| `nodeFormConf.formFields` | JSON String | 改为 `List<FormFieldDTO>` 对象数组 |
| `nodeFormConf.editableFields` | JSON String | 改为 `List<String>` |
| `nodeFormConf.requiredFields` | JSON String | 改为 `List<String>` |

**实现**：在 `TodoQueryService.getTaskDetail()` 中，将存储的 JSON 字符串反序列化为对象后塞入 `NodeFormConfDTO`。

#### RuntimeAccessDTO 新增

新建 `RuntimeAccessDTO` 类，补充到 `TaskDetailRespDTO.runtimeAccess` 字段：

```java
@Data
public class RuntimeAccessDTO {
    private Boolean canClaim;      // 可签收
    private Boolean canApprove;    // 可审批
    private Boolean canReject;     // 可驳回
    private Boolean canTransfer;   // 可转交
    private Boolean isAssignee;    // 是否当前处理人
    private Boolean isCandidate;   // 是否候选人
}
```

**计算逻辑**（在 `TodoQueryService.getTaskDetail()` 中）：
1. 查询 Flowable `IdentityLink` 获取候选人列表
2. `isAssignee` = `task.assignee == empId`
3. `isCandidate` = `identityLinks` 中存在该用户或用户的角色组
4. `canClaim` = `!isAssignee && isCandidate && task.assignee == null`
5. `canApprove` = `isAssignee`
6. `canReject` = `isAssignee`
7. `canTransfer` = `isAssignee || 是任务管理员`（需调用 `RoleApi.hasRole(empId, "WORKFLOW_ADMIN")`）

#### ProcessProgress 节点列表新增

在 `TaskDetailRespDTO` 中新增 `processProgress` 字段，类型为 `List<ProcessNodeDTO>`：

```java
@Data
public class ProcessNodeDTO {
    private String nodeKey;           // BPMN 节点ID
    private String nodeName;          // 节点名称
    private String status;            // COMPLETED / ACTIVE / PENDING
    private String assignee;          // 处理人工号
    private String assigneeName;      // 处理人姓名
    private LocalDateTime completeTime;
}
```

**构建逻辑**（在 `TodoQueryService.getTaskDetail()` 中）：
1. 查 Flowable `HistoricActivityInstance` 获取所有历史节点（按 startTime 升序）
2. 查 Flowable `Task` 获取当前活动节点
3. 合并构建：已完成节点（endTime != null）→ COMPLETED；当前任务节点 → ACTIVE；其余 → PENDING
4. 对 COMPLETED/ACTIVE 节点，通过 `UserApi` 获取 `assigneeName`

#### ApprovalLogDTO 字段改造

| 字段 | 当前 | 目标 | 修复方式 |
|:---|:---|:---|:---|
| `nodeKey` | ❌ 缺失 | ✅ | 从 HistoricActivityInstance.activityId 获取 |
| `nodeName` | ❌ 缺失 | ✅ | 从 HistoricActivityInstance.activityName 获取 |
| `operator` | 实际用 `userId` | ✅ 重命名为 `operator` | 重命名字段 |
| `operatorName` | ❌ 缺失 | ✅ | 调用 `UserApi.getUserName(operator)` |
| `operatorOrgName` | ❌ 缺失 | ✅ | 调用 `OrgApi.getOrgNameByEmpId(operator)` |
| `action` | ❌ 缺失 | ✅ | 解析 Flowable 事件类型：START → SUBMIT, COMPLETE(approved=true) → APPROVE, COMPLETE(approved=false) → REJECT, ADD_ATTACHMENT/DELETE... → TRANSFER |
| `opinion` | 实际用 `comment` | ✅ 重命名为 `opinion` | 重命名字段 |
| `operateTime` | 实际用 `time` | ✅ 重命名为 `operateTime` | 重命名字段 |

**action 解析策略**：
- 流程启动时的 comment → `SUBMIT`
- 任务完成时根据 Flowable 变量 `approved` → `APPROVE` / `REJECT`
- 任务签收操作（查 IdentityLink.type=candidate） → `CLAIM`
- 转交操作（历史变量含 transfer） → `TRANSFER`

---

### 2.3 流程实例详情（ProcessController, C.1）

#### ProcessInstanceInfo Enrichment

当前 record 缺少设计文档中的业务字段。改造方案：

| 字段 | 当前 | 目标 | 修复方式 |
|:---|:---|:---|:---|
| `bizType` | ❌ 缺失 | ✅ | 从 `BizProcessMap.bizType` 获取 |
| `bizId` | ❌ 缺失 | ✅ | 从 `BizProcessMap.bizId` 获取 |
| `title` | ❌ 缺失 | ✅ | 从 `BizProcessMap.title` 获取 |
| `startUserName` | ❌ 缺失 | ✅ | 调用 `UserApi.getUserName(startUserId)` |
| `startOrgName` | ❌ 缺失 | ✅ | 调用 `OrgApi.getOrgNameByEmpId(startUserId)` |
| `currentNodeName` | ❌ 缺失 | ✅ | 从 ProcessDefinition 或 Task 获取 |
| `currentAssigneeName` | ❌ 缺失 | ✅ | 调用 `UserApi.getUserName(currentAssignee)` |
| `candidateGroups` | ❌ 缺失 | ✅ | 从 `BizProcessMap.candidateGroups` 获取 |
| `processStatus` | 当前用 `isEnded` (Boolean) | 改为 String: RUNNING/COMPLETED/CANCELLED | 改造返回类型 |

**实现**：修改 `ProcessQueryService.getProcessInstanceInfo()`，在返回 `ProcessInstanceInfo` 前关联查询 `BizProcessMap` 补充业务字段。

---

### 2.4 流程进度图（ProcessController, C.2）

设计文档要求返回结构化 JSON 节点列表，当前直接返回 PNG 图像。

**方案**：新增接口 `GET /api/workflow/processes/{processInstanceId}/nodes` 返回结构化进度数据。

```json
{
  "code": 200,
  "data": {
    "processInstanceId": "PRC_00001",
    "processDefinitionKey": "loan_approve_v1",
    "nodes": [
      {
        "nodeKey": "task_submit",
        "nodeName": "提交",
        "nodeType": "userTask",
        "status": "COMPLETED",
        "assignee": "E10001",
        "assigneeName": "张三",
        "startTime": "2026-03-06T09:00:00",
        "endTime": "2026-03-06T09:00:00"
      }
    ]
  }
}
```

- 新增 `ProcessDiagramDTO` 包含 `nodes: List<ProcessDiagramNodeDTO>`
- 保留原 PNG 接口作为兼容（`GET .../diagram` 仍返回 PNG）
- 新接口 `GET .../nodes` 返回结构化 JSON

---

### 2.5 审批日志（ProcessController, C.3）

设计文档中 `ApprovalLogDTO` 包含 `nodeKey/nodeName/operatorOrgName/action`，需与任务详情中的 ApprovalLogDTO 保持一致。

| 字段 | 当前 ProcessHistoryDTO | 目标 |
|:---|:---|:---|
| `nodeKey` | 当前用 `activityId` | 重命名为 `nodeKey` |
| `nodeName` | 当前用 `activityName` | ✅ 对齐 |
| `operator` | ❌ 缺失 | 从 `HistoricTaskInstance.assignee` 获取 |
| `operatorName` | ❌ 缺失 | 调用 `UserApi.getUserName(operator)` |
| `operatorOrgName` | ❌ 缺失 | 调用 `OrgApi.getOrgNameByEmpId(operator)` |
| `action` | ❌ 缺失 | 解析历史任务完成事件：COMPLETE(approved=true) → APPROVE, COMPLETE(approved=false) → REJECT |
| `opinion` | ❌ 缺失 | 从历史任务的 comment 获取 |
| `operateTime` | 当前用 `startTime` | 重命名为 `operateTime` |

---

### 2.6 管理接口 DTO 对齐（WorkflowAdminController, D.1-D.7）

#### WfTimeoutRule → TimeoutRuleRespDTO

| 字段 | 当前 | 目标 |
|:---|:---|:---|
| `processDefinitionName` | ❌ 缺失 | 查询 ProcessDefinition.name 补充 |
| `nodeName` | ❌ 缺失 | 需从 BPMN 模型中获取节点名称 |
| `createdTime` / `updatedTime` | ✅ 存在 | ✅ |

**方案**：新建 `TimeoutRuleRespDTO`，将 `WfTimeoutRule` 作为内部实体不直接暴露。

#### WfNodeCandidateConf → NodeCandidateRespDTO

| 字段 | 当前 | 目标 |
|:---|:---|:---|
| `processDefinitionName` | ❌ 缺失 | 查询补充 |
| `nodeName` | ❌ 缺失 | 查询补充 |
| `candidateValue` | 当前为 `String`（逗号拼接） | 改为 `List<String>` |
| `createdTime` / `updatedTime` | ✅ 存在 | ✅ |

#### WfNodeFormConf → NodeFormRespDTO

| 字段 | 当前 | 目标 |
|:---|:---|:---|
| `processDefinitionName` | ❌ 缺失 | 查询补充 |
| `nodeName` | ❌ 缺失 | 查询补充 |
| `formFields` | 当前为 JSON String | 改为 `List<FormFieldDTO>` |
| `editableFields` | 当前为 JSON String | 改为 `List<String>` |
| `requiredFields` | 当前为 JSON String | 改为 `List<String>` |
| `createdTime` / `updatedTime` | ✅ 存在 | ✅ |

---

## 3. 文件修改清单

### 3.1 新增文件

| 文件 | 说明 |
|:---|:---|
| `api/dto/RuntimeAccessDTO.java` | 运行时权限 DTO |
| `api/dto/NodeFormConfDTO.java` | 节点表单配置 DTO（结构化对象） |
| `api/dto/ProcessNodeDTO.java` | 流程节点进度 DTO |
| `api/dto/ProcessDiagramDTO.java` | 流程进度图结构化 DTO |
| `api/dto/ProcessDiagramNodeDTO.java` | 进度图节点 DTO |
| `api/dto/ApprovalLogV2DTO.java` | 统一审批日志 DTO（含 action/nodeKey 等） |
| `api/dto/TimeoutRuleRespDTO.java` | 超时规则响应 DTO |
| `api/dto/NodeCandidateRespDTO.java` | 候选人配置响应 DTO |
| `api/dto/NodeFormRespDTO.java` | 节点表单响应 DTO |
| `api/dto/TaskDetailV2DTO.java` | 任务详情 V2 DTO（含 runtimeAccess/processProgress） |

### 3.2 修改文件

| 文件 | 修改内容 |
|:---|:---|
| `api/dto/TaskRespDTO.java` | 补充 startUserName/startOrgName/startTime/warningTime/timeoutTime；candidateGroups 改为 List<String> |
| `api/dto/TaskDetailRespDTO.java` | 改为包含 runtimeAccess + processProgress；nodeFormConf 改为结构化对象 |
| `api/dto/ApprovalLogDTO.java` | 重命名字段（userId→operator, comment→opinion, time→operateTime）；新增 nodeKey/nodeName/operatorName/operatorOrgName/action |
| `api/dto/BizProcessMapDTO.java` | 补充 currentTaskId/currentNodeId/candidateGroups |
| `service/TodoQueryService.java` | 补充 runtimeAccess 计算、processProgress 构建、审批日志 action 解析 |
| `service/ProcessQueryService.java` | 补充 ProcessInstanceInfo 业务字段、C.3 历史日志字段 |
| `controller/ProcessController.java` | 新增 C.2 结构化 JSON 接口 `/nodes` |
| `mapper/BizProcessMapMapper.xml` | 补充查询字段（title/bizType/candidateGroups） |

### 3.3 废弃/兼容性文件

- 原 `GET /api/workflow/processes/{id}/diagram` PNG 接口保留（不删除，作为兼容）
- 原 `TaskDetailRespDTO` 保持兼容，但新增 `runtimeAccess` 和 `processProgress` 字段后向前兼容

---

## 4. 依赖说明

新增对以下外部 API 的依赖（均在 auth-permission-center 中）：

| API | 用途 |
|:---|:---|
| `UserApi.getUserName(empId)` | 获取用户姓名 |
| `UserApi.getUserByEmpId(empId)` | 获取用户信息（含机构） |
| `OrgApi.getOrgName(orgId)` | 根据机构ID获取机构名称 |
| `OrgApi.getOrgNameByEmpId(empId)` | 根据工号获取机构名称 |
| `RoleApi.hasRole(empId, roleCode)` | 检查用户是否有指定角色 |

---

## 5. 测试策略

1. **DTO 字段对齐验证**：为每个 DTO 编写单元测试，验证序列化/反序列化字段数量和名称
2. **Service 逻辑测试**：为 `TodoQueryService.getTaskDetail()` 编写集成测试，验证 runtimeAccess/processProgress 计算结果
3. **接口契约测试**：使用 `@WebMvcTest` 验证每个 Controller 接口的响应 JSON 结构与设计文档一致
4. **回归测试**：确保不破坏现有接口行为
