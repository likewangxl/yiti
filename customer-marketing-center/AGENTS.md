# customer-marketing-center 开发指导

本文件是客户营销中心的模块级开发权威，与仓库根目录 `AGENTS.md` 同时适用；根文件规定的 TDD、鉴权、数据范围、SQL、MyBatis-Plus、日志和测试门禁不在此重复。发生冲突时以根文件为准。

## 模块职责

- 基础包：`com.bank.branch.platform.customer`
- Maven 坐标：`com.bank.branch.platform:customer-marketing-center`
- 负责标签、营销线索及审批回调、客户主档、客户池与认领、触达任务/日志和触达报表。
- 客户主档、认领关系和触达状态属于本模块私有状态；其他模块只能通过 `api/` 下的 `*Api`/`*QueryApi` 访问。

## 依赖与边界

- 依赖 `auth-permission-center`、`system-governance-center`、`workflow-center` 及 `common-*`；跨模块调用必须使用对方公开 API。
- `business-application-center`、`performance-engine-center` 会消费本模块公开契约；`report-analytics-center` 只读消费查询契约。本模块不得反向依赖这些上层消费者。
- 本模块不得直接调用 Flowable。线索/客户相关流程由 `WorkflowApi` 发起，完成结果只消费 `workflow.api.event.ProcessCompletedEvent`。
- 定时任务统一由治理中心的 Quartz/`SYS_JOB_CONF` 管理，模块内禁止新增 Spring `@Scheduled`；`NoCustomerScheduledArchTest` 守护该约束。
- 新 Mapper 必须使用 MyBatis-Plus；实体的 `@TableId` 必须匹配物理表实际主键策略。当前大量业务 ID 由应用生成并使用 `IdType.INPUT`，不得机械改成 `AUTO`。

## 数据权限与写入规则

- 列表、详情、导出必须使用同一套后端数据范围，不能只在前端隐藏；员工、机构和跨机构能力均以 `BizScopeApi` 结果为准。
- 写操作必须在 Service 层按数据库实体再次校验归属、当前状态和操作者权限。
- 跨机构历史、导出、批量分配等高风险能力必须使用独立资源、独立授权和独立审计。
- `CUST_CLAIM` 的 `(cust_id, org_id)` 唯一约束是并发认领的最终防线；重复键统一转换为 `CUSTOMER_ALREADY_CLAIMED`。
- `TOUCH_LOG` 的 `(touch_task_id, client_uuid)` 唯一约束负责客户端重试幂等；重复键统一转换为 `TOUCH_LOG_DUPLICATE`。
- 标签客户导入是覆盖语义：同一事务内先移除该标签旧关联，再写入本次完整集合；不得擅自改成增量合并。

## 状态机与事务

### 线索审批

- 线索状态为 `DRAFT`、`SUBMITTED`、`IN_APPROVAL`、`APPROVED`、`REJECTED`；具体合法操作以 `LeadService` 的状态校验为准。
- `submitForApproval` 必须先对线索行 `SELECT ... FOR UPDATE`，锁内确认草稿状态后再启动流程，防止并发重复提交。
- `WorkflowCallbackListener` 在工作流事务提交后处理回调，并以 `REQUIRES_NEW` 开启新事务。
- 审批结果只能通过 `conditionalUpdateStatus(IN_APPROVAL, target)` 推进；影响行数为 0 时必须停止下游处理，以保证重复回调和补偿任务并发时幂等。
- 审批通过后，同一新事务内同步调用 `CustMasterAssemblerService.assembleFromLead`，按 `leadOp` 执行创建、更新或失效；不要在 `AFTER_COMMIT` 监听器中再发布由另一层 `AFTER_COMMIT` 监听器消费的嵌套事件。
- 审批驳回只推进为 `REJECTED`，不装配或失效客户主档。监听器异常会被记录并由 `LeadCallbackCompensationService` 的补偿路径复用同一 reconcile 语义。

### 触达任务

- 合法状态转换：`PENDING -> IN_PROGRESS | CANCELLED`，`IN_PROGRESS -> SUCCESS | CANCELLED`；`SUCCESS`、`CANCELLED` 为终态。
- 首次触达日志会驱动 `PENDING -> IN_PROGRESS`；所有转换必须经过 `TouchTaskStateMachineService.assertTransition`。
- 客户认领成功后同步创建首次触达任务，保持认领与任务创建的事务语义，不改为嵌套异步事件链。

## 契约与实现入口

- 公开契约：`src/main/java/com/bank/branch/platform/customer/api/`
- 业务实现：`service/`、`facade/`、`listener/`
- 状态和错误码：`enums/`
- 接口及事务文档：`docs/modules/customer-marketing-center/03-接口设计与报文.md`、`04-对外API契约.md`、`06-并发与事务策略.md`
- 表结构以当前数据库和实体/Mapper 映射为准；文档或历史 SQL 与运行结构不一致时先只读核实，不凭旧脚本推断。

## 测试入口

- 遵循根目录的 Red-Green-Refactor；模块测试从仓库根目录运行 `mvn -pl customer-marketing-center -am test`，集成测试用 `mvn -pl customer-marketing-center -am verify`。
- Service 使用 Mockito；Controller 集成测试继承 `support/AbstractControllerIntegrationTest`，用户上下文使用 `support/WithMockEmpContext` 和 `support/MockEmpContextExtension`。
- 定时任务或调度相关改动必须运行 `arch/NoCustomerScheduledArchTest`。
- 跨模块契约变更后先按根目录指引刷新本地 SNAPSHOT，再运行依赖模块测试，避免旧 JAR 造成假失败。
