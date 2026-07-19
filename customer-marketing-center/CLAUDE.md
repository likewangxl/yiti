# customer-marketing-center/ CLAUDE.md

本文件为 `customer-marketing-center` 模块提供开发上下文，是本模块开发指导的唯一权威来源（同目录 `AGENTS.md` 仅作指针，不承载内容）。

## 模块概述

**customer-marketing-center** 是客户营销中心，覆盖客户全生命周期管理：标签体系、线索管理（含审批流）、客户主档、客户池与认领、触达任务、触达报表。是平台核心业务模块之一。

- **基础包名**: `com.bank.branch.platform.customer`
- **Maven 坐标**: `com.bank.branch.platform:customer-marketing-center`

## 依赖关系

- **依赖**（均通过 `*Api`/`*QueryApi`）：`auth-permission-center`（`CurrentUserApi`/`BizScopeApi`/`OrgApi`）、`workflow-center`（`WorkflowApi` —— 线索/客户删除审批流程启动）、`system-governance-center`（`DictApi`/`FileApi`/`NotifyApi`/`AuditApi`）；此外依赖 `common-web`/`common-trace`/`common-security`/`common-aop`/`common-db`。
- **被依赖**：`business-application-center`、`performance-engine-center` 直接依赖本模块；`report-analytics-center` 只读依赖本模块 `*Api`/`*QueryApi`（禁止反向依赖，也禁止任何业务模块依赖 report-analytics-center）。
- 本模块**不接入 Flowable 直连**：线索/客户删除审批统一通过 `workflow-center` 暴露的 `WorkflowApi` 发起，回调经 `ProcessCompletedEvent` 收敛回本模块处理（`workflow-center` 是唯一直接调 Flowable API 的模块，见根 CLAUDE.md）。

## 架构规则与红线

- 模块间只通过 `*Api`/`*QueryApi` 交互，禁止其他模块直接依赖本模块的 `mapper`/`entity`/`serviceImpl`。
- 所有接口必须注册到 `PT_RESOURCE` 并声明 `@BizAuth`；高危操作（跨机构历史查询、各类导出、批量分配）必须独立 URL、单独授权、单独审计。
- **禁止在本模块内新增 Spring `@Scheduled`**：定时任务一律走 Quartz + `SYS_JOB_CONF`（`job/quartz/CustMasterSyncJob` 对应 `job_key=CUST_INFO_SYNC`，`job/quartz/LeadCallbackCompensateQuartzJob` 对应 `job_key=LEAD_CALLBACK_COMPENSATE`）。`src/test/.../arch/NoCustomerScheduledArchTest.java` 用 ArchUnit 守护此规则，回退使用 `@Scheduled` 会导致该测试失败。
- **事件监听器禁止嵌套异步事件**：见下方"线索审批回调"踩坑，本模块内新增事件驱动逻辑前必须先读该节。

## 关键实现要点与踩坑

### 线索审批回调：为什么不能嵌套事件监听器
早期实现中，线索审批通过事件链驱动客户主档装配与首次触达任务创建：`WorkflowCallbackListener` 在 `@TransactionalEventListener(AFTER_COMMIT)` 内发布领域事件，再由另一层 `@TransactionalEventListener(AFTER_COMMIT)` 监听器消费。这种"事件套事件"在 AFTER_COMMIT 阶段的嵌套触发时序下曾导致一次 INSERT 未真正持久化的 bug（事务边界与监听器触发顺序不符合直觉）。

修复后（当前实现）：`listener/WorkflowCallbackListener` 监听 workflow-center 发布的 `com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent`（`@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`），按 `outcome` 直接**同步方法调用**（不再发布下游事件）委托 `service/LeadCallbackReconcileService`：
- `reconcileApproved` 内部同步调用 `CustMasterAssemblerService.assembleFromLead` 完成客户主档装配（CREATE/UPDATE/DELETE 按 `leadOp` 分支）；
- `reconcileRejected` 仅推进状态并发布 `LeadRejectedEvent`（REJECTED 分支无下游装配，暂无消费者，保留扩展点，非嵌套风险点）。

`LeadCallbackReconcileService` 同时被 `LeadCallbackCompensationService`（Quartz 补偿扫描）复用，两条路径共用同一套幂等语义。**教训**：跨模块/跨子系统的异步回调，一旦需要"处理结果 A 触发处理逻辑 B"，优先选同步方法调用 + 独立事务边界（`REQUIRES_NEW`），而不是再叠一层事件监听器；事件-监听器链路每加一层，AFTER_COMMIT 时序的心智负担和出错面都会成倍增加。

同样的教训也体现在客户认领上：`ClaimService.claim()` 认领成功后**同步**调用 `TouchTaskService.createFromClaim` 创建首次触达任务，而不是发布事件再监听。

### 幂等设计：认领 UK / 触达日志 UK
- `CUST_CLAIM` 表 `UNIQUE KEY uk_cust_claim_org (cust_id, org_id)`：同一客户在同一机构只能有一条有效认领关系。`ClaimService.claim()` 直接 INSERT，捕获 `DuplicateKeyException` 转为 `CUSTOMER_ALREADY_CLAIMED`（`CUST-40904`），以此代替分布式锁防并发抢认领。
- `TOUCH_LOG` 表 `UNIQUE KEY uk_touch_log_client (touch_task_id, client_uuid)`：客户端提交触达日志时自带 `clientUuid`，重复提交（如前端重试/弱网双发）命中该 UK 冲突，捕获 `DuplicateKeyException` 转为 `TOUCH_LOG_DUPLICATE`（`CUST-40905`），保证同一次触达记录不被重复落库。

### 触达任务状态机
`enums/TouchTaskStatus`：`PENDING`（待处理）→ `IN_PROGRESS`（进行中）/`CANCELLED`（已取消）；`IN_PROGRESS` → `SUCCESS`（已完成）/`CANCELLED`；`SUCCESS`/`CANCELLED` 为终态。首次触达日志会自动驱动 `PENDING → IN_PROGRESS`。所有转移由 `service/TouchTaskStateMachineService.assertTransition` 校验，非法转移抛 `CUST-40010`。

### 错误码演进：CUST-40006 已废弃
`CUST-40006` 早期代表"导入行数过多"，已废弃并迁移语义到 `CUST-42205`（`IMPORT_ROWS_TOO_MANY`，`enums/CustomerErrorCode`）。**看到代码/日志/前端 i18n 里的 `CUST-40006` 时应知道它已被 `CUST-42205` 取代**，不要再新建或复用 `CUST-40006`。同批次 `CUST-40003` 也已废弃，语义升级为 `CUST-40301`（`LEAD_EDIT_FORBIDDEN`）。

### 标签客户覆盖式导入
`service/TagCustomerService.importCustomers()` 采用**先删后插**：`deleteByTagId()` 清空该标签下全部旧关联，再 `insertBatch()` 批量写入本次导入内容。这是**覆盖式**而非增量合并语义 —— 对调用方的含义是：重复导入同一标签会让标签下客户列表完全等于最近一次导入的内容，不会与历史导入结果叠加；如需增量追加需调用方自行先查询再合并后整体导入。

### 线索提交审批的并发控制
`service/LeadService.submitForApproval()` 先 `leadMapper.selectForUpdate(id)` 加行锁（要求线索当前状态为 `DRAFT`），锁内确认状态后再组装 `StartProcessCmd` 调用 `workflowApi.startProcess(...)` 启动 Flowable 流程，避免同一线索被并发重复提交审批。完整调用链参见源码 `src/main/java/com/bank/branch/platform/customer/service/LeadService.java`（`submitForApproval` 方法），该模式在 `docs/code-examples.md` 中也有索引条目。

## 已知技术债/例外

- 线索导入行级校验为简化实现：`LeadImportService`/`LeadImportPreviewResp` 中多处 TODO 标记，当前 `failCount` 恒为 0、错误明细恒为空列表，真实行级校验待后续补齐。
- 跨机构客户历史查询（`CustomerHistoryController`）等高危操作的 `CROSS_ORG` 特殊审计分类，待 `@AuditLog` 支持 `specialCategory` 属性后再补齐（`CustomerService`/`CustomerCrossOrgHistoryVO` 均有对应 TODO）。
- `TouchTaskMapper.xml` 中触达耗时统计的日期函数存在 H2（`DATEDIFF`）与 MySQL 生产（`TIMESTAMPDIFF`）方言差异，当前按 H2 兼容写法实现，TODO 标记待 DBA 确认生产环境后切换。
- ArchUnit 守护目前仅覆盖"禁止 `@Scheduled`回退"这一条规则（`NoCustomerScheduledArchTest`），非全面架构守护。
- 模块历史上曾直接使用原生 MyBatis Mapper + XML；按根 CLAUDE.md 红线，本模块**新增**数据库访问需改用 MyBatis-Plus（`BaseMapper`/`LambdaQueryWrapper`），既有遗留 Mapper 不强制回改。

## 清单与契约指引

- 对外 `*Api`/`*QueryApi` 接口清单以 `src/main/java/com/bank/branch/platform/customer/api/` 目录源码为准（`TagApi`/`LeadApi`/`CustomerQueryApi`/`ClaimApi`/`TouchTaskQueryApi`）。
- Controller/端点/DTO/枚举/事件等逐条清单一律不在本文件维护，以 `src/main/java/com/bank/branch/platform/customer/` 各子包源码为准。
- REST 端点契约细节（请求/响应报文、字段级校验）见 `docs/modules/customer-marketing-center/03-接口设计与报文.md` 与 `04-对外API契约.md`；并发与事务策略另见 `docs/modules/customer-marketing-center/06-并发与事务策略.md`；表结构见 `05-表结构DDL.md`。
- `PT_RESOURCE` 注册 SQL 见 `docs/superpowers/sql/2026-04-14-customer-*.sql` 及 `2026-04-21-customer-contract-alignment-pt-resource.sql`。
- 数据库表：`CUST_TAG`/`CUST_TAG_REL`/`CUST_LEAD`/`LEAD_IMPORT_BATCH`/`CUST_MASTER`/`CUST_CLAIM`/`TOUCH_TASK`/`TOUCH_LOG`，均已大写化（历史小写表已 DROP），字段定义见 `docs/schema/ddl-customer.sql`。

## 测试指引

- `*Test.java`/`*Tests.java` 走 surefire（`mvn test`），`*IT.java` 走 failsafe（`mvn verify`），命名与分工遵循根 CLAUDE.md 规范。
- Service 层：Mockito 单元测试，每个 Service 对应一个 `*Test.java`。
- Controller 层：MockMvc + H2 集成测试，基类 `support/AbstractControllerIntegrationTest`，测试配置 `CustomerTestConfiguration` + `application-test.yml`（H2 MySQL 兼容模式）。
- Mock 用户上下文使用 `support/WithMockEmpContext` 注解 + `support/MockEmpContextExtension`。
- 新增/修改定时任务相关代码后，务必确认 `arch/NoCustomerScheduledArchTest` 仍通过，避免误用 `@Scheduled` 回退。
