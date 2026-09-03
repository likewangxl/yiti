# 工作流中心——对外 API 契约

## 1. 契约范围

本文件是 `workflow-center` 对其他 Maven 模块提供的 Java 契约摘要。稳定依赖面只包括
`workflow.api` 下的三个接口、其 DTO 和 `workflow.api.event` 下的公开事件。Controller、
Service、Mapper、Entity、Flowable 类型和内部事件均不属于跨模块契约。

业务模块调用前应完成自己的业务状态、RBAC、数据范围、主表事务和表单校验；工作流服务
仍会对任务、流程和转交目标做二次校验。无会话方法的 `empId` 来自已认证的外部渠道，
调用方必须承担渠道认证和授权责任。

## 2. WorkflowApi

### 2.1 方法

| 方法 | 作用 | 关键边界 |
| --- | --- | --- |
| `startProcess(StartProcessCmd)` | 启动流程实例并返回首任务 | 流程定义必须已部署；同一运行中 `businessKey` 唯一；写 `BIZ_PROCESS_MAP` |
| `cancelProcess(String processInstanceId, String reason)` | 取消/撤回运行中实例 | 由服务层验证申请人或管理员；结束映射并发布撤回事件 |
| `getProcessByBusinessKey(String businessKey)` | 按业务键查映射 | 未找到返回工作流资源错误 |
| `getProcessByBizTypeAndBizId(String bizType, String bizId)` | 按业务类型和 ID 查映射 | 未找到返回工作流资源错误 |
| `getProcessOutcome(String processInstanceId)` | 从历史变量读取结论 | 仅能确定 `approved` 时返回 `Optional<APPROVED/REJECTED>`，否则为空 |
| `approveByEmp(String taskId, String empId, String opinion)` | 无会话审批通过 | 外部渠道先完成可见性/授权；不依赖登录 ThreadLocal |
| `approveByEmp(String taskId, String empId, String opinion, Map<String,Object> formData)` | 无会话审批并传入表单/路由变量 | `formData` 在完成任务前写入流程变量 |
| `rejectByEmp(String taskId, String empId, String opinion)` | 无会话驳回 | 外部渠道负责认证；服务层检查任务和转交锁 |
| `resolveDesignerProcDefKey(String flowKey)` | 解析设计器已发布流程定义键 | 未发布或无部署键返回 `WF-40401`，不静默回退 |

`StartProcessCmd` 字段为 `bizType`、`bizId`、`businessKey`（最长 100）、
`processDefinitionKey`、`startUser`、`startOrgId`、`title`（最长 200）和可选 `variables`。
跨模块调用方应传入真实当前员工/机构；REST 提交接口的当前上下文补齐逻辑见
`03-接口设计与报文.md`。

### 2.2 状态和结论

`BIZ_PROCESS_MAP.processStatus` 使用 `RUNNING`、`COMPLETED`、`CANCELLED`。引擎历史
变量 `approved=true/false` 转换为 `getProcessOutcome` 的 `APPROVED/REJECTED`；驳回
终止、撤回和取消的流程状态与下游审批结论不是同一个维度。

`cancelProcess` 只处理运行中实例，成功后发布 `ProcessWithdrawnEvent`。`rejectByEmp`
及会话版驳回终止运行时实例并发布 `ProcessCompletedEvent(outcome=REJECTED)`。

## 3. WorkflowQueryApi

### 3.1 方法

| 方法 | 作用 | 返回/边界 |
| --- | --- | --- |
| `queryTodoList(String empId, String bizType, String keyword, int pageNo, int pageSize)` | 查询待办分页 | `PageResult<TaskRespDTO>`；按员工候选/assignee过滤 |
| `queryDoneList(String empId, String bizType, String keyword, int pageNo, int pageSize)` | 查询已办分页 | `PageResult<TaskRespDTO>`；读取历史任务 |
| `countPendingTasks(String empId)` | 统计待办 | 服务层按员工候选范围统计 |
| `listRecentPendingTasks(String empId, int limit)` | 读取最近待办 | `List<TaskRespDTO>`；仍受员工范围限制 |
| `getTaskDetail(String taskId, String empId)` | 任务详情 | `TaskDetailRespDTO`，包含运行时权限提示、表单、进度、历史和动态出边 |
| `getProcessHistory(String processInstanceId)` | 流程审批历史 | `List<ApprovalLogDTO>` |
| `getProcessNodes(String processInstanceId)` | 流程结构化进度 | `ProcessDiagramDTO` |
| `getActiveTaskCandidates(String processInstanceId)` | 当前活动任务可审批员工 | `List<TaskCandidateUserDTO>`；无活动任务返回空列表 |
| `getProcessByBusinessKey(String businessKey)` | 只读查映射 | `BizProcessMapDTO` |
| `getProcessByBizTypeAndBizId(String bizType, String bizId)` | 只读查映射 | `BizProcessMapDTO` |
| `queryParticipatedBusinessKeys(String empId, String prefix, Integer days, Integer limit)` | 查询员工参与过的流程业务键 | 去重 `Set<String>`；空员工返回空集并受前缀/时间/上限约束 |

待办候选解析以运行时身份链接和认证权限中心为准。历史参与查询主路径为 Flowable
历史 involved user；当 `empId` 是当前登录员工时，才合并其当前候选组下的未领取任务。
空/未知权限不能扩展结果集。

`queryParticipatedBusinessKeys` 的 `prefix` 为空表示不按定义键过滤；`days` 为空或非正
表示不限制时间窗口；`limit` 为空或非正使用服务端安全上限，超过上限会截断。调用方应
使用合理的窗口和上限，不能把它当作全量历史导出接口。

## 4. TodoQueryApi

此接口用于业务模块先取得工作流业务键，再在业务模块按自身字段二次过滤，不允许业务
模块直接访问 Flowable 表。

| 方法 | 作用 | 会话语义 |
| --- | --- | --- |
| `listMyTodoBusinessKeys(String empId, String bizType)` | 员工某业务类型待办业务键 | 使用当前登录上下文解析候选组 |
| `findTaskRespByBusinessKeys(String empId, List<String> businessKeys)` | 按业务键批量反查待办任务元信息 | 只返回该员工有权查看的任务 |
| `listMyDoneBusinessKeys(String empId, String bizType)` | 员工某业务类型已办业务键 | 使用历史任务范围 |
| `findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys)` | 按业务键批量反查已办元信息 | 只返回该员工历史办理任务 |
| `listTodoBusinessKeysByEmp(String empId, String bizType)` | 无会话待办业务键 | 供已完成外部认证的渠道；授权由调用方负责 |
| `findTaskRespByBusinessKeysByEmp(String empId, List<String> businessKeys)` | 无会话批量反查待办 | 不读取登录 ThreadLocal；调用方负责授权 |

列表可为空；未命中的业务键不出现在返回 Map。业务模块不得把显式 `empId` 方法暴露成
通用匿名查询。

## 5. DTO 语义

### 5.1 任务和流程

`TaskRespDTO` 以任务、流程和业务映射组成列表视图，字段包括：

- 标识：`taskId`、`processInstanceId`、`businessKey`、`bizType`、`bizId`；
- 展示：`title`、`startUser`、`startUserName`、`startUserEmpNo`、`startOrgId`、
  `startOrgName`、`startOrgDeptNo`、`startTime`；
- 当前节点：`taskName`、`nodeKey`、`taskCreateTime`、`assignee`、`candidateGroups`；
- SLA/状态：`slaStatus`、`warningTime`、`timeoutTime`、`claimable`、`processStatus`；
- 已办补充：`completeTime`、`approvalResult`、`opinion`。

`TaskDetailRespDTO` 包含 `taskInfo`、`runtimeAccess`、`nodeFormConf`、`processProgress`、
`approvalLogs` 和 `outgoingBranches`。`runtimeAccess` 是界面提示，不是授权凭据；
`outgoingBranches` 只对动态 `DSN_` 流程提供命名出边和路由变量，静态流程可以为空。

`BizProcessMapDTO` 字段为 `id`、业务键/类型/ID、流程定义/实例 ID、流程状态、标题、
发起人和机构、当前处理人/候选组、起止时间。`WorkflowLaunchResp` 返回流程实例 ID、
业务键和首个用户任务 ID（无首个用户任务时为空）。

### 5.2 历史和候选

`ApprovalLogDTO`：`nodeKey`、`nodeName`、`operator`、`operatorEmpNo`、`operatorName`、
`operatorOrgName`、`action`、`opinion`、`operateTime`。当前动作包括提交、审批、驳回、
签收和转交。

`ProcessDiagramDTO`：流程实例 ID、流程定义键和节点列表。节点包含任务 ID、节点键/名称/
类型、`COMPLETED`/`ACTIVE`/`PENDING` 状态、处理人及起止时间。

`TaskCandidateUserDTO`：`empId`、展示工号 `employeeNo`、姓名 `employeeName`。候选来源
是活动任务的真实身份链接，流程没有活动任务时返回空。

## 6. 公开事件

跨模块只消费以下两个顶级 record；事件不携带 Flowable 服务对象或内部实体。

| 事件 | 载荷 | 语义 |
| --- | --- | --- |
| `ProcessCompletedEvent` | `processInstanceId`、`businessKey`、`outcome`、`reason` | 流程自然完成或驳回终止；`outcome` 为 `APPROVED`/`REJECTED`，原因可空 |
| `ProcessWithdrawnEvent` | `processInstanceId`、`businessKey`、`withdrawnByEmpId`、`currentAssigneeEmpId`、`opinion` | 流程撤回；撤回时未签收则当前处理人可空 |

事件发布者和消费者都必须按业务键/流程实例设计幂等处理。消费者不得根据
`CANCELLED` 单独猜测完成事件结论，也不得依赖内部监听器事件名称。

## 7. REST 对应关系和依赖约束

完整 HTTP 路由、参数、响应和资源要求见 `03-接口设计与报文.md`。Java API 与 REST
不是两套业务规则：REST 使用当前会话，Java API 使用显式命令或员工 ID，但都必须经过
工作流服务的任务状态、候选/处理人、转交锁和数据范围规则。

业务模块只能依赖本文件声明的 API/DTO/事件；不得引用 Controller、ServiceImpl、Mapper、
Entity、Flowable `RuntimeService`/`TaskService` 等实现细节。跨模块接口变更必须同时更新
03、04、资源配置并运行契约检查。
