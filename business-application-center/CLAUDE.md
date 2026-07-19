# business-application-center/ CLAUDE.md

本文件是 `business-application-center` 模块开发指导的唯一权威来源。

## 1. 模块概述

**business-application-center** 是业务申请中心（核心域），承载两大业务域：**资产投放申请 (Loan)** 和 **中场支持申请 (Support)**。是"营销→触达→落地"闭环的落地环节，也是绩效和报表模块最重要的事实源。

- **基础包名**: `com.bank.branch.platform.bizapp`
- **Maven 坐标**: `com.bank.branch.platform:business-application-center`

## 2. 依赖关系

- **依赖**: `common-*`（common-web/common-trace/common-security/common-aop/common-db）、`auth-permission-center`、`system-governance-center`、`workflow-center`、`customer-marketing-center`、`portal-content-center`（均通过各自 `*Api`/`*QueryApi`，见各模块 `pom.xml`）
- **被依赖**: 当前无业务模块声明对本模块的 Maven 依赖；仅被 `bootstrap` 作为聚合启动模块依赖。`performance-engine-center`/`report-analytics-center` 目前经由 `customer-marketing-center` 等其他路径取数，并未直连本模块——若未来需要消费 Loan/Support 数据，须新增 `*QueryApi` 并显式声明 Maven 依赖，不要绕开接口直连。

## 3. 架构规则与红线

- 模块间只通过 `api/` 下的 `LoanApi`/`LoanQueryApi`/`SupportApi`/`SupportQueryApi`/`BizApplyQueryApi` 交互，禁止跨模块直接依赖本模块的 `mapper`/`entity`/`serviceImpl`。
- 所有写接口必须声明 `@BizAuth`（`bizType` + `action`），并登记到 `PT_RESOURCE`。
- 高危端点（撤回、导出、转交等）必须叠加 `@AuditLog`，规则见第 4 节。
- 2 个 Mapper（`LoanApplyMapper`、`SupportRequestMapper`）均 `extends BaseMapper`，单条 CRUD 走 BaseMapper 内置方法，`*Mapper.xml` 只保留分页/统计/条件更新等自定义 SQL（遵循根 CLAUDE.md 的 MyBatis-Plus 红线）。
- `workflow-center` 是唯一直连 Flowable API 的模块，本模块只能通过 `WorkflowApi` 发起/查询流程，不得引入 Flowable 依赖。

## 4. 关键实现要点与踩坑

### 4.1 场景 A/B 路由 + 多产品拆单 + 双视图权限

`SupportScenarioRouter.route(productIds, otherDemand, supportDeptId)` 是判定入口：
- **场景 A**（产品直达）：`productIds` 非空 且 `otherDemand` 为空。
- **场景 B**（部门承接）：`productIds` 为空 或 `otherDemand` 非空，此时 `supportDeptId` 必填（缺失抛 `BIZ-40902`）。
- 两者皆空直接抛 `BIZ-40903`。

场景 A 命中多产品时，由 `SupportProductSplitService.splitByProducts(...)` 为每个 `productId` 拆出一条独立 `DRAFT` 状态的 `SupportRequest`，同批次记录共享同一个 `submitGroupId`（UUID），便于批量追溯与审计（见 `docs/modules/business-application-center/07-审计要求.md` 的 BATCH_CREATE 审计示例）。

双视图权限体现为两套独立的数据范围口径：`SupportController`（发起侧 SUPPORT）按 `owner_org_id` + `created_by` 过滤；`SupportDeptController`（承接侧 SUPPORT_DEPT）按 `support_dept_id` + `assigned_emp_id` 过滤，二者共用同一张 `SUPPORT_REQUEST` 表但通过 `@BizAuth` 的 `bizType` 区分数据范围规则。

### 4.2 Listener 事务时机：`@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`

`LoanWorkflowListener`/`SupportWorkflowListener` 监听 `workflow-center` 发布的 `ProcessCompletedEvent`，`onProcessCompleted` 方法当前注解为：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
```

而非 `@EventListener`，原因：若在事务提交前触发，读到的是未提交的流程状态（脏读风险），且若后续事务回滚会导致业务状态（LOAN_APPLY/SUPPORT_REQUEST）与流程实例状态不一致。

> **注意（与历史文档的偏差）**：早期版本曾叠加 `fallbackExecution = true`，用于在无事务上下文时降级为立即执行；当前实现**已明确移除**该参数（`fallbackExecution` 现为默认值 `false`，见 `LoanWorkflowListenerTest`/`SupportWorkflowListenerTest` 的注解结构验证用例），改为显式声明 `@Transactional(REQUIRES_NEW)` 在 AFTER_COMMIT 阶段开新事务执行状态更新与事件发布（P0 bug 防御性修复，与 customer 模块 `WorkflowCallbackListener` 的修复 pattern 对齐，详见 customer 模块 `WorkflowCallbackEventChainBugIT`）。这样一来，本方法内的 `conditionalUpdateStatus` 与 `publishEvent` 都运行在同一个新事务里，未来新增的下游 `AFTER_COMMIT` listener 订阅 `LoanApprovedEvent`/`SupportRejectedEvent` 等事件时也无需再配置 `fallbackExecution` 即可正确触发，不必再依赖“无事务降级”这条路径。

状态更新通过 `SupportRequestMapper`/`LoanApplyMapper` 的条件 UPDATE（`conditionalUpdateStatus`，仅当当前状态为 `IN_APPROVAL` 才更新）保证幂等，返回 rowsAffected=0 表示已被其他实例处理，跳过重复发布事件。

### 4.3 `@AuditLog` 的 `reasonRequired` 判断标准

`@AuditLog` 定义在 `common-aop` 的 `com.bank.branch.platform.common.aop.annotation.AuditLog`（`action`/`resourceType`/`reasonRequired`，默认 `false`）。本模块的用法：
- **`reasonRequired = true`**：`LoanController.cancel`（撤回）、`LoanController.export`（导出）、`SupportController.cancel`、`SupportController.export`、`SupportDeptController.transfer`（转交）。共同点是**不可逆的状态终止、批量数据外流或责任人变更**，需要留痕原因供合规追溯（转交若原因为空或不足 10 字，AOP 层直接拒绝，见 `07-审计要求.md` §7.3）。
- **默认 `reasonRequired = false`**：`create`/`update`/`delete`（草稿态操作）、`submit`（提交审批）、`SupportDeptController.dispatch`（派单）、`complete`（办理完成）。共同点是**正向业务流转**，审计仍会记录，但不强制要求填写理由。

### 4.4 `SupportSourceType` 已完成 MANUAL → EXISTING_CUSTOMER 改名

`SupportSourceType` 当前取值为 `EXISTING_CUSTOMER("存量客户")` 与 `TOUCH_TASK("触达任务转入")`，`MANUAL` 名称已不存在。改名原因：`MANUAL`（手工创建）描述的是创建方式而非业务语义，容易和"是否有触达任务来源"混淆；`EXISTING_CUSTOMER` 直接表达"该申请来自存量客户主动办理，而非触达任务转化"，与 `TOUCH_TASK` 构成语义对称的二元来源分类。

## 5. 已知技术债/例外

- `LoanService`（`src/main/java/.../service/LoanService.java:341` 附近）与 `LoanDetailResp`（`src/main/java/.../dto/resp/LoanDetailResp.java` 类注释）仍保留 `TODO V2`：待集成 `workflow-center` 历史查询补齐 `processMap`/`approvalLogs`。`LoanDetailResp.CustInfoVO`（`src/main/java/.../dto/resp/LoanDetailResp.java:69`）另有一条 `TODO V2`：待通过 `ClaimApi` 补齐 `ownerEmpId`（客户维护人工号）。以上两处均未实现，V2 规划前不要假设这些字段已具备真实数据。
- `PT_RESOURCE` 注册脚本以 `docs/superpowers/sql/` 下按日期前缀的最新对齐脚本为准，不要只认某个历史样例脚本。

## 6. 清单与契约指引

- Controller/端点/DTO/枚举/错误码的具体条目与数量以源码目录 `src/main/java/com/bank/branch/platform/bizapp/` 为准，本文件不维护清单，避免过期。
- REST 端点请求/响应报文细节见 `docs/modules/business-application-center/03-接口设计与报文.md`。
- 跨模块调用的 `*Api`/`*QueryApi` 契约见 `docs/modules/business-application-center/04-对外API契约.md`。
- 审计动作清单与告警规则见 `docs/modules/business-application-center/07-审计要求.md`。
- 表结构见 `docs/modules/business-application-center/05-表结构DDL.md` + `docs/schema/ddl-bizapp.sql`（表：`LOAN_APPLY`、`SUPPORT_REQUEST`）。
- 示例代码统一登记在 `docs/code-examples.md`，本文件不复制代码块。

## 7. 测试指引

- Service 单元测试用 Mockito（`@ExtendWith(MockitoExtension.class)`）。
- Controller 集成测试用 MockMvc + `AbstractControllerIntegrationTest`。
- Facade 单元测试用 Mockito。
- 测试配置基于 H2 MySQL 兼容模式。
- `*Test.java`/`*Tests.java` 走 surefire（`mvn test`）；`*IT.java` 走 failsafe（`mvn verify`），命名与执行方式见根 CLAUDE.md「测试 / IT 执行注意事项」。
