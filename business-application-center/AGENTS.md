# business-application-center 开发指导

本文件是业务申请中心的模块级开发权威，与仓库根目录 `AGENTS.md` 同时适用；根文件的 TDD、鉴权、数据范围、SQL、MyBatis-Plus、日志和测试门禁不在此重复。发生冲突时以根文件为准。

## 模块职责

- 基础包：`com.bank.branch.platform.bizapp`
- Maven 坐标：`com.bank.branch.platform:business-application-center`
- 承载资产投放申请（Loan）和中场支持申请（Support），是营销触达后的业务落地核心域。
- `LOAN_APPLY`、`SUPPORT_REQUEST` 及其业务状态由本模块持有；外部只能经 `api/` 下公开契约访问。

## 依赖与边界

- 依赖 `auth-permission-center`、`system-governance-center`、`workflow-center`、`customer-marketing-center`、`portal-content-center` 及 `common-*`。
- 客户、通讯录、产品、流程和治理能力只能通过对应 `*Api`/`*QueryApi` 调用，禁止跨模块引用 mapper、entity、内部 service 或 facade。
- 当前没有业务模块声明对本模块的 Maven 依赖；新增消费者前应先设计稳定的 `*Api`/`*QueryApi`，不得直接查询本模块私表。
- 本模块不得引入或直接调用 Flowable，所有流程操作通过 `WorkflowApi`/`WorkflowQueryApi` 完成。
- `LoanApplyMapper`、`SupportRequestMapper` 均继承 `BaseMapper`；简单 CRUD 使用 MyBatis-Plus，自定义 XML 仅承载分页、统计和条件更新等内置方法无法表达的 SQL。
- 两张主表的 ID 由应用侧提供，实体使用 `IdType.INPUT`；新增实体的 `@TableId` 必须按实际物理主键生成策略选择，不能统一套用自增。

## 数据权限与审计

- 发起侧 Support 视图按 `owner_org_id`、`created_by` 约束；承接侧 Support Dept 视图按 `support_dept_id`、`assigned_emp_id` 约束。两套视图共享表，但必须使用各自的 `BizType` 和数据范围口径。
- Loan/Support 的列表、详情、导出和写操作必须复用一致的后端范围判断；Service 写入前还要验证记录归属和状态。
- 撤回、导出、转交属于高风险操作，必须独立授权、记录审计并要求理由；草稿编辑、提交、派单、办理完成仍需审计，但不强制理由。
- 所有资源均须登记 `PT_RESOURCE`；新增端点优先复用已有 BizType/BizAction，不能通过漏标 `@BizAuth` 绕过细粒度校验。

## 领域规则与状态机

### 中场支持场景

- 场景 A：`productIds` 非空且 `otherDemand` 为空，产品直达。
- 场景 B：`productIds` 为空或 `otherDemand` 非空，必须提供 `supportDeptId`。
- 产品和其他需求都为空时拒绝创建。上述判定统一由 `SupportScenarioRouter` 完成，不在 Controller 或前端复制。
- 场景 A 的多个产品由 `SupportProductSplitService` 拆成多条独立 `DRAFT` 记录；同批次共享 `submitGroupId`，后续提交和审计必须保持批次可追溯。

### 申请状态

- Loan 状态：`DRAFT`、`IN_APPROVAL`、`COMPLETED`、`REJECTED`、`CANCELLED`。
- Support 状态：`DRAFT`、`IN_APPROVAL`、`IN_PROGRESS`、`COMPLETED`、`REJECTED`、`CANCELLED`。
- 状态转换必须由 Service 的显式校验或 Mapper 条件更新完成，不允许 Controller 直接改状态。
- `SupportSourceType` 仅使用 `EXISTING_CUSTOMER` 与 `TOUCH_TASK`，不得恢复语义模糊的 `MANUAL`。

## 工作流回调事务

- `LoanWorkflowListener`、`SupportWorkflowListener` 只消费公开的 `ProcessCompletedEvent`，分别过滤 `LOAN:`、`SUPPORT:` 业务键。
- 回调使用 `@TransactionalEventListener(AFTER_COMMIT)` 和 `@Transactional(REQUIRES_NEW)`；不得启用 `fallbackExecution` 代替明确的新事务。
- 只允许从 `IN_APPROVAL` 条件更新到目标状态；影响行数为 0 时跳过领域事件，保证重复回调幂等。
- 状态更新和领域事件发布在同一个新事务内完成；通知失败只记录告警，不得回滚已经完成的核心状态。

## 契约与实现入口

- 公开契约：`src/main/java/com/bank/branch/platform/bizapp/api/`
- 控制器：`controller/`；业务编排：`facade/`；核心规则：`service/`；流程回调：`listener/`
- 接口及审计文档：`docs/modules/business-application-center/03-接口设计与报文.md`、`04-对外API契约.md`、`07-审计要求.md`
- 表结构以当前数据库和实体/Mapper 映射为准；旧设计文档仅作上下文，结构判断必须只读核实。

## 测试入口

- 从仓库根目录运行 `mvn -pl business-application-center -am test`；包含 `*IT` 时运行 `mvn -pl business-application-center -am verify`。
- Service、Facade 单元测试使用 Mockito；Controller 集成测试继承 `support/AbstractControllerIntegrationTest`，用户上下文使用 `WithMockEmpContext`/`MockEmpContextExtension`。
- 流程回调改动必须覆盖注解结构、仅 `IN_APPROVAL` 可推进、重复事件不二次发布以及通知失败不影响核心状态。
- 涉及 customer、portal、workflow 公共契约的变更，按根目录 stale SNAPSHOT 指引刷新依赖后再执行本模块测试。
