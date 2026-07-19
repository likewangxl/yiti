# workflow-center/ CLAUDE.md

本文件为 `workflow-center` 模块提供上下文说明，是本模块开发指导的唯一权威来源。

## 模块概述

**workflow-center** 是工作流中心，集成 Flowable 7.0.1 工作流引擎，为整个平台提供流程启动、任务审批、SLA 超时管理、候选人解析、可视化审批流程设计器、审批流监控、任务转办等能力。是平台内**唯一**直接调用 Flowable API 的模块。

**基础包名**: `com.bank.branch.platform.workflow`

**Maven 坐标**: `com.bank.branch.platform:workflow-center`

**对外契约**: 跨模块 Java 接口 `WorkflowApi`/`WorkflowQueryApi`/`TodoQueryApi`，供其他模块依赖；另有一组面向前端/管理端的 REST 控制器（任务办理、流程查询、静态候选人/超时/表单配置、可视化流程设计器、审批流监控、任务转办待认领）。具体方法签名与端点以源码 `api/`、`controller/` 目录和 `docs/modules/workflow-center/04-对外API契约.md` 为准。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`, `system-governance-center`
- **被依赖**: 所有需要工作流能力的模块（通过 `WorkflowApi`/`WorkflowQueryApi`/`TodoQueryApi`）

## 架构规则与红线

- 模块间只能通过 `api/` 下的 `*Api` 接口交互，禁止其他模块直连本模块 `mapper`/`entity`/`serviceImpl`。
- 本模块是**唯一**直连 Flowable API 的模块，其他模块不得直接依赖 `RuntimeService`/`TaskService`/`HistoryService` 等 Flowable Bean。
- 所有流程类业务必须维护 `business_key`（格式 `BIZ_TYPE:id`）和 `BIZ_PROCESS_MAP`；新增业务类型接入工作流前先确认 `business_key` 唯一性约束（同一 businessKey 已运行会抛 WF-40901）。
- **无会话方法**（`approveByEmp`/`rejectByEmp`/`listTodoBusinessKeysByEmp`/`findTaskRespByBusinessKeysByEmp`，供 SOAP 网关/callpu 等外部渠道按显式 `empId` 调用）不依赖登录态、不做鉴权与候选人可见性校验，调用方必须自行完成鉴权后再调用，避免越权。

## 关键实现要点与踩坑

### 审批流监控 + 任务转办（两阶段）

- `ProcessMonitorController`（`/api/workflow/monitor/processes`）+ `ProcessMonitorService`：供秘书岗/行长按机构数据范围监控进行中与已完成的审批流实例。数据范围过滤 **Fail-Close**：系统管理员或 `DataScopeType.ALL` 不加机构过滤；`ORG`/`ORG_SUBTREE` 按机构编码集合过滤（通过 `WF_PROCESS_ORG` 参与机构快照表 EXISTS 过滤）；范围未知/为空或其余范围类型（SELF 系列/`WORKFLOW_PARTICIPANT`）一律返回空结果，不下发任何数据。
- `WfProcessOrgService.record(processInstanceId, empId, source)` 是"参与机构快照"**唯一写入入口**，所有挂点（流程启动/Flowable 监听器/审批 REST）都调这里；方法内部整体 try-catch 兜底（机构查询与落库都可能抛未受检异常），绝不能把异常抛回调用方，否则会打断 Flowable 命令执行或让一个已提交的操作报 500。
- `TaskTransferController`/`TaskTransferService`：任务转办已从旧的**单阶段**"一步到位直接改 assignee"（`TaskController#transferTask`，已下线）升级为**两阶段**转交——发起后先落 `WF_TASK_TRANSFER` 待认领记录，须接收人主动认领/拒绝才真正转移办理权。发起端点挂在 `/monitor/tasks/{taskId}/transfer` 下（鉴权对齐审批流监控，`@BizAuth(WORKFLOW_MONITOR, TRANSFER)`）；收件箱/认领/拒绝/发件箱/撤回挂在 `/transfers/*` 下，接收人可以是任意具备任务办理角色的人，不限秘书岗/行长。
- `ProcessMonitorService.resolveActiveTaskIds` 有**单活假设**：假定审批流均为顺序单办理人 `userTask`（无并行网关），同一流程实例任意时刻至多一个活跃任务；若未来引入并行网关产生多活跃任务，会丢失除最后一个之外的任务 ID——不阻断监控列表展示，仅影响转交入口精确性，属已知限制。

### ProcessCompletedEvent.outcome 与 processStatus：两套独立语义

`ProcessCompletedListener` 把 Flowable `approved` 变量转成两套独立语义：`biz_process_map.processStatus` 写 `COMPLETED`/`CANCELLED`（workflow 自身状态机），而 `ProcessCompletedEvent.outcome` 写 `APPROVED`/`REJECTED`（下游业务语义）。**二者不可混用**——监听 `ProcessCompletedEvent` 判断业务结论时必须用 `outcome`，不能拿 `processStatus` 当审批结论用。

### 会签（MI）rejected 变量必须显式初始化

会签（MI）节点完成条件 EL 引用 `rejected` 变量，但 `approved=true` 路径从不设置该变量；`MultiInstanceApproverResolver`（会签入口监听器）在入口处显式初始化 `rejected=false`（不覆盖已存在值），避免完成条件 EL 求值时因变量不存在抛 `PropertyNotFoundException`。新增会签节点或改动完成条件表达式时，注意这一初始化时机不能省略。

### 可视化流程设计器：DSN_ 影子 KEY 与候选人"先删后插"

`FlowPublishService` 发布流程时：校验 → 生成 BPMN → 以影子 KEY（`DSN_` + flowKey）部署到 Flowable `RepositoryService` → 按最新审批人规则**整图替换**候选人配置（`WfNodeCandidateConf`，以影子 KEY 为粒度先删旧再插新），保证发布后运行时读到的候选人配置与最新审批人规则一致。改动 `service/flow/**` 前，先确认改动是否影响已发布的影子流程定义及其候选人配置的这一语义，避免线上运行中流程被误发布覆盖。

### outgoingBranches：穿透网关取命名出边

`TaskDetailRespDTO.outgoingBranches` 仅当任务的 `processDefinitionKey` 以 `DSN_` 前缀（即设计器发布的动态流程）才会反查设计器图并填充"下一步走向"分支选项；审批节点经无名连线连到网关时，出边分支解析会**穿透网关取其命名出边**（`FlowBpmnGenerator`/出边解析逻辑），避免设计器"下一步走向"选项丢失。静态 BPMN 任务恒为空列表，任何解析异常均吞掉降级为空列表。

### 已知占位实现（未完工，勿当作已实现功能依赖）

- `TaskController` 暴露了 `keyword` 查询参数，但 `TodoQueryService` 当前仅对 `bizType` 做后置过滤，`keyword` 尚未真正参与查询（`queryTodoList`/`queryDoneList` 均未使用该变量）。
- `TaskDetailRespDTO.runtimeAccess` 当前仍是轻量实现：`canApprove`/`canReject`/`canTransfer` 只基于 `isAssignee`，候选组交集尚未完整判定（源码 TODO）。
- `TaskDetailRespDTO.processProgress` 当前仍返回空列表，待通过 `HistoryService` 查询历史活动节点补齐（源码 TODO）。

## 清单与契约指引

- 控制器/Service/Mapper/实体/枚举/事件的完整清单以 `src/main/java/com/bank/branch/platform/workflow/` 源码目录为准，不在本文件维护；新增能力后先 `ls` 对应目录确认现状再改文档，避免再次漂移。
- 端点契约、请求/响应报文以 `docs/modules/workflow-center/03-接口设计与报文.md`、`04-对外API契约.md` 为准，每次接口变更同步更新这两份文档（连带 `08-初始化数据清单.md`、`09-依赖契约摘要.md`）。
- 表结构见 `docs/modules/workflow-center/05-表结构DDL.md`；核心表含 `BIZ_PROCESS_MAP`（业务实体↔Flowable 流程实例桥梁）、`WF_NODE_CANDIDATE_CONF`/`WF_NODE_FORM_CONF`/`WF_TIMEOUT_RULE`（静态配置）、`WF_FLOW_DEF`/`WF_FLOW_NODE`/`WF_FLOW_EDGE`/`WF_FLOW_NODE_APPROVER`（设计器）、`WF_PROCESS_ORG`（参与机构快照）、`WF_TASK_TRANSFER`（两阶段转交记录）。
- Spring 内部事件（`ProcessStartedEvent`/`ProcessCompletedEvent`/`ProcessWithdrawnEvent`/`TaskApprovedEvent`/`TaskRejectedEvent`/`TaskTransferredEvent`）供其他模块 `@EventListener` 监听，载荷字段以 `api/event/` 包与各发布方 Service 内嵌 `record` 定义为准。
- 新增或修改 REST 端点时，除同步文档外，还要同步 `PT_RESOURCE` 种子数据与相关权限测试。
- 示例代码统一登记在 `docs/code-examples.md`，本文件不复制代码片段。

## 测试指引

- 本模块测试**全部连接本地 `yiti` MySQL 开发库**（`root`/`djdev`），不使用 H2——Flowable 需要真实关系型引擎语义，`*Test`/`*IT` 通用配置见 `src/test/resources/application-test.yml`（`@ActiveProfiles("test")` 激活；显式指定 Druid 数据源类；排除 Redis/Session/Quartz 自动配置，但保留 Flowable 全部自动配置，因为部分 IT 需要真实 `ProcessEngine`）。
- Mapper 级集成测试可继承 `support/WfMapperTestBase`：默认 `@Transactional + @Rollback(true)`，测试完毕自动回滚，无需手工清理测试数据；同时排除 Flowable ProcessEngine 避免 ACT_* 表初始化失败。
- `*Test.java`/`*Tests.java` → surefire（`mvn test` 触发）；`*IT.java` → failsafe（`mvn verify` 触发）。
- 跨模块 `@SpringBootTest`（bootstrap 层）依赖本模块最新类时，按根 CLAUDE.md 的 stale jar 处理流程先 `mvn clean install -DskipTests`。
