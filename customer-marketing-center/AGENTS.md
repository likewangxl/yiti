# customer-marketing-center 开发指导

本文件是客户营销中心的模块级开发权威，与仓库根目录 `AGENTS.md` 同时适用；根文件规定的 TDD、鉴权、数据范围、SQL、MyBatis-Plus、日志和测试门禁不在此重复。发生冲突时以根文件为准。

## 模块职责

- 基础包：`com.bank.branch.platform.customer`
- Maven 坐标：`com.bank.branch.platform:customer-marketing-center`
- 负责标签、营销线索及审批回调、客户主档、客户池与认领、触达任务/日志和触达报表。
- 客户主档、认领关系和触达状态属于本模块私有状态；其他模块只能通过 `api/` 下的 `*Api`/`*QueryApi` 访问。

## 依赖与边界

- 依赖 `auth-permission-center`、`system-governance-center`、`workflow-center` 及 `common-*`；跨模块调用必须使用对方公开 API。当前线索能力还使用 `UserApi` 查询用户、使用 `WorkflowQueryApi` 查询待办/已办。
- `business-application-center`、`performance-engine-center` 会消费本模块公开契约；`report-analytics-center` 只读消费查询契约。本模块不得反向依赖这些上层消费者。
- 本模块不得直接调用 Flowable。线索/客户相关流程由 `WorkflowApi` 发起，完成结果只消费 `workflow.api.event.ProcessCompletedEvent`。
- 定时任务统一由治理中心的 Quartz/`SYS_JOB_CONF` 管理，模块内禁止新增 Spring `@Scheduled`；`NoCustomerScheduledArchTest` 守护该约束。
- 新 Mapper 必须使用 MyBatis-Plus；实体的 `@TableId` 必须匹配物理表实际主键策略。当前大量业务 ID 由应用生成并使用 `IdType.INPUT`，不得机械改成 `AUTO`。

## 数据权限与写入规则

- 列表、详情、导出必须使用同一套后端数据范围，不能只在前端隐藏；员工、机构和跨机构能力均以 `BizScopeApi` 结果为准。
- 写操作必须在 Service 层按数据库实体再次校验归属、当前状态和操作者权限。
- 跨机构历史、导出、批量分配等高风险能力必须使用独立资源、独立授权和独立审计。
- `CUST_CLAIM` 的 `(cust_id, claimed_by)` 唯一约束是并发认领的最终防线；同一员工不能重复认领同一客户，不同员工可分别认领；重复键统一转换为 `CUSTOMER_ALREADY_CLAIMED`。
- `TOUCH_LOG` 的 `(touch_task_id, client_uuid)` 唯一约束负责客户端重试幂等；重复键统一转换为 `TOUCH_LOG_DUPLICATE`。
- 标签新增后固定为 `PENDING + DISABLED`，审核通过后才可用于打标；标签客户导入支持 `APPEND` 追加和 `REPLACE` 全量替换，替换时保留历史关系并将未命中关系置为失效。

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
- 普通客户认领只建立认领关系，不自动创建任务；员工通过 `POST /api/claims/{claimId}/touch` 手动创建首次触达任务。`OWNER` 线索审批通过是例外：装配客户主档后同步建立主办人认领关系并创建首次触达任务。上述流程均不得改成嵌套异步事件链。

## 2026-08-11 客户营销阶段口径

### 客户主档与线索

- 客户营销主档使用 `CUSTOMER_MARKET_CUSTOMER`；存量 `CUST_MASTER` 仅承接 `XAN_M98_CUST_STAT_SHOW3` 的 T-1 同步及存量业务按客户号查询。`CustMasterMapper` 操作营销表，`M98CustMasterMapper` 操作 M98 表，两张表不得交叉写入。
- 客户列表不再以 `is_account_opened=1` 为准入条件；通过 `BizScopeApi` 解析 `CUSTOMER` 数据范围，并在主办关系或有效认领关系内返回未删除客户。
- 线索录入由 `LeadEntryService` 统一处理。总行/分行用户可选 `PUBLIC`、`SCOPE`、`OWNER`；机构等级为 3（支行）及更下层级的非管理员强制 `OWNER` 且指派本人，系统管理员不受该层级限制。`SCOPE` 人员必须是启用的 `R_RM`，`OWNER` 必须与存量客户主办查询结果一致。
- 录入详情和审批详情共用 `LeadRespDTO`，包含基础、经营、授信金额/敞口、分配范围、标签快照、附件和审批结果。审批列表通过 `WorkflowQueryApi` 复用待办/已办，不直连 Flowable 表。
- 批量导入归入后续阶段；当前阶段交付单条录入和已审批记录 Excel 导出。

### 客户池与触达

- 待认领池只包含审批通过的 `PUBLIC` 客户，并排除当前员工已有效认领的客户。`SCOPE` 线索审批通过后直接为指定客户经理建立有效认领关系，不进入待认领池且不自动生成触达任务。
- “我的客户”使用 `GET /api/claims/mine/customers` 聚合客户、认领和最新任务；首次触达使用 `POST /api/claims/{claimId}/touch`，重新触达使用 `POST /api/claims/{claimId}/re-touch`。两者只允许认领本人操作，且同一客户与执行人不能同时存在多个进行中任务。
- 新任务 SLA 初始为 `BLUE`，临近计划完成时间为 `YELLOW`，逾期为 `RED`；历史 `GREEN` 只兼容读取。
- 触达日志记录触达时间、方式、内容和至少一张分类照片；关键人合影、企业门牌、经营场所各最多 3 张，并可记录协同人员和定位。只有任务执行人可写，同机构人员和系统管理员可查看。
- 触达管理列表、汇总和导出对系统管理员开放全量机构筛选；非系统管理员在 Controller 层强制收敛到当前机构。

### 标签、跨机构营销与转交

- 标签管理支持新增、审核、退回、启停、客户详情和 `APPEND`/`REPLACE` 名单导入；待审核或已退回标签不能手工启用或关联客户。
- 跨机构营销校验申请人非主办、申请机构不同于主办机构、申请人无业绩归属、申请机构无业绩归属；审核通过后只为申请人生成触达任务，不改变客户资产权限。
- 客户转交支持多人接收，首位为新主办、其余为协办快照；转交时关闭旧进行中触达任务、更新客户主办关系并为新主办创建首次触达任务。非系统管理员只能转交本人主办或本机构主办的客户。

## 契约与实现入口

- 公开契约：`src/main/java/com/bank/branch/platform/customer/api/`
- 业务实现：`service/`、`facade/`、`listener/`
- 状态和错误码：`enums/`
- 接口及事务文档：`docs/modules/customer-marketing-center/03-接口设计与报文.md`、`04-对外API契约.md`、`06-并发与事务策略.md`
- 表结构以当前数据库和实体/Mapper 映射为准；文档或历史 SQL 与运行结构不一致时先只读核实，不凭旧脚本推断。
- 当前资源对齐脚本参考 `docs/superpowers/sql/2026-08-11-customer-phase1-resource-align.sql` 和 `2026-08-11-customer-phase3-resource-align.sql`；新增资源默认只绑定 `SYS_ADMIN`，其他角色由管理员重新分配。
- 当前文档基线记录 16 张相关表：客户营销主档已从存量 `CUST_MASTER` 拆为 `CUSTOMER_MARKET_CUSTOMER`，并包含跨机构营销、转交、认领和触达相关表。字段仍须以目标库只读核实结果为准。

## 测试入口

- 遵循根目录的 Red-Green-Refactor；模块测试从仓库根目录运行 `mvn -pl customer-marketing-center -am test`，集成测试用 `mvn -pl customer-marketing-center -am verify`。
- Service 使用 Mockito；Controller 集成测试继承 `support/AbstractControllerIntegrationTest`，用户上下文使用 `support/WithMockEmpContext` 和 `support/MockEmpContextExtension`。
- 定时任务或调度相关改动必须运行 `arch/NoCustomerScheduledArchTest`。
- 跨模块契约变更后先按根目录指引刷新本地 SNAPSHOT，再运行依赖模块测试，避免旧 JAR 造成假失败。
