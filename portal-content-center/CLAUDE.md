# portal-content-center/ CLAUDE.md

本文件为 `portal-content-center` 模块提供上下文说明，是该模块开发指导的唯一权威来源（同目录 `AGENTS.md` 只放指针，不重复维护）。

## 模块概述

**portal-content-center** 是门户与内容中心，负责工作台聚合、网址导航、通讯录、产品资料库、文档下载、担保信息、公告等通用门户能力。

- **基础包名**: `com.bank.branch.platform.portal`
- **Maven 坐标**: `com.bank.branch.platform:portal-content-center`
- **定位**: 通用域，只做只读聚合与内容管理，不持有核心业务状态。

## 依赖关系

- **公共基础设施**: `common-web` / `common-trace` / `common-security` / `common-aop` / `common-db`
- **auth-permission-center**: `CurrentUserApi`（当前用户）、`BizScopeApi`（数据范围）、`OrgApi`（组织架构）、`UserApi`（员工信息）
- **system-governance-center**: `DictApi`（字典）、`FileApi`（文件存储，经其读写华为云 OBS，portal 不直连 `ObsStorageClient`）、`NotifyApi`（通知，仅 `WorkspaceService` 合规使用）、`AuditApi`（审计）
  - 注意：`pom.xml` 依赖注释与旧版 `AGENTS.md` 都写了 `ConfigApi`，经 Grep 全模块源码确认**不存在任何 `ConfigApi` 引用**，属历史误记，本文档不再收录这条依赖。
- **workflow-center**: 仅正式只读 `WorkflowQueryApi`（工作台待办查询，经 `WorkflowQueryAdapter` 封装降级）。**例外**：`listener/` 下有绕开 `*Api` 直连 workflow 内部实现的技术债，见下文「已知技术债/例外」。
- **不依赖**: `customer-marketing-center` / `business-application-center` / `performance-engine-center` / `report-analytics-center`（通用域不持有核心域状态；确需核心域数据时走 bootstrap 层 DIP 桥接，见下文 `MetricAdapter`）
- **被依赖**: `performance-engine-center`（经 `AddressBookApi` 校验通讯录员工存在性，避免直连 `PT_USER`）、`business-application-center`（经 `AddressBookApi` / `ProductApi`）、`bootstrap`（全量装配）

## 架构规则与红线

- 遵循根 `CLAUDE.md` 的模块边界总则：跨模块只能经 `*Api`/`*QueryApi` 交互，禁止直连其他模块的 `mapper`/`entity`/`service`/`facade`。
- portal 是通用域，不持有、不直接依赖核心域（如 performance）状态；若确需核心域能力，只能通过 bootstrap 层做 DIP 桥接（portal 定义抽象接口，bootstrap 注入具体实现），portal 的 `pom.xml` 不得新增对核心域模块的依赖。
- 高危操作、写操作二次鉴权、`DATA_SCOPE` 应用等按全局 `CLAUDE.md` / `docs/common-dev-guide.md` 执行，本文件不重复。

## 关键实现要点与踩坑

1. **产品/担保导出 V1 同步导出 ≤5000 行**：`ProductExportService.SYNC_EXPORT_THRESHOLD` 与 `GuaranteeExportService.SYNC_EXPORT_THRESHOLD` 均为 `5000L`，超过阈值直接抛 `PortalErrorCode.EXPORT_ROWS_LIMIT_EXCEEDED`（`PORTAL-42207`），提示用户增加过滤条件，而不是分页/异步导出。
   - 为什么现在是同步：当前数据量可控，先用最简单方案满足需求。
   - 超过阈值会怎样：直接拒绝，不落任何导出任务，不做分页续传。
   - V2 打算怎么做：`docs/modules/portal-content-center/03-接口设计与报文.md` 明确写了"当前实现尚未落地异步导出任务"——方向是引入异步导出任务，但代码和设计文档都还没有具体方案，属已知留白而非已规划细节。

2. **`MetricAdapter` 经 bootstrap `PerformanceMetricApiBridge` 做 DIP 桥接**：`portal/adapter/MetricAdapter.java` 只依赖 portal 自己定义的防腐层接口 `MetricApi`（同包 `adapter` 下），用 `@Autowired(required = false)` 注入，Bean 不存在时降级返回空列表。
   - 真正的实现类 `PerformanceMetricApiBridge` 位于 **bootstrap** 模块的 `bridge` 包，`implements` portal 的 `MetricApi`，内部再注入 performance-engine-center 正式对外的 `performance.api.MetricApi` 完成 DTO 投影（`trend` 由 `mom` 推导 UP/FLAT/DOWN）。
   - 为什么要绕这一层：如果 portal 直接 `pom` 依赖 performance-engine-center，就打破了"通用域不持有核心域状态"的分层；只有同时装配两个模块的 bootstrap 层适合做桥接，portal 和 performance 因此可以互不感知对方存在，各自仍只对外暴露 `*Api`。

3. **缓存去 Redis 后仅剩常量位**：`PortalCacheConfig` 目前是纯常量工具类，只保留 Key 前缀（`portal:`）、默认 TTL（5 分钟）常量与 `jitteredTtl()` 防雪崩抖动算法，**模块未接入任何缓存中间件**（2026-05 去 Redis 改造后的残留现状）。
   - `PRODUCT_SUPPORT_KEY` / `NAV_ACTIVE_KEY` 只是设计语义预留位，当前代码没有真正的缓存读写逻辑。
   - 后续如需恢复 Cache-Aside，需先决定接入的缓存实现。

## 已知技术债/例外

- **边界违规（`listener/WorkflowApprovalNotificationListener.java`，git log 显示 2026-05-22 引入）**：该监听器同时触犯"跨模块只能走 `*Api`/`*QueryApi`"红线三处，均登记为待整改技术债：
  1. 直接 `import` 并注入 workflow-center 的 `mapper.BizProcessMapMapper`（Mapper 层实现细节），查询 `entity.BizProcessMap`；
  2. `@EventListener` 监听 workflow-center `service.TaskOperationService` 内部嵌套定义的 `TaskApprovedEvent` / `TaskRejectedEvent`，这不是经 `*Api` 暴露的公共契约，而是一个内部 Service 类的嵌套事件；
  3. 直接 `import` 并注入 system-governance-center 的 `service.NotificationService`（Service 实现类）发通知，而不是走 governance 对外的 `NotifyApi`——注意 `WorkspaceService` 里已有合规先例正确使用了 `NotifyApi`，说明"走 Api"的路径本就存在，此处属于绕开而非无路可走。
  - 同一监听器里还用到 `workflow.api.event.ProcessWithdrawnEvent`，这个在 `api.event` 包下，是合规的公共事件契约，**不算**违规，可作为「事件应该长什么样」的参照：正确整改方向是让 `TaskApprovedEvent`/`TaskRejectedEvent` 也提升到 workflow `api.event` 包下，`BizProcessMap` 查询改走 workflow 某个 `*QueryApi`，通知发送改走 `NotifyApi`。
- `WorkflowQueryAdapter.java:82-83` `overdueInfo` / `bizDetailUrl` 硬编码 `null`（低优先级，待 workflow-center 提供超期信息 + 业务详情 URL 解析能力后再接入）。
- `adapter/dto/PortalTodoItem.java` 仍标 `@Deprecated` 但被 `WorkflowQueryAdapter` 等生产代码正常使用（低优先级，待决议保留并去掉 `@Deprecated`，或改名如 `PortalTodoVO`）。

## 清单与契约指引

- Controller / 端点 / 类的逐条清单一律不在本文档维护（会过期），以源码目录为准：`src/main/java/com/bank/branch/platform/portal/` 下 `controller/` `api/` `service/` `entity/` `mapper/` 等。
- 端点契约细节（请求/响应报文、错误码明细）见 `docs/modules/portal-content-center/03-接口设计与报文.md`、`docs/modules/portal-content-center/04-对外API契约.md`。
- 错误码前缀 `PORTAL-{HTTP_STATUS}{SEQ}`（如 `PORTAL-40003`），具体码表以 `enums/PortalErrorCode.java` 源码为准；模块前缀注册见 `docs/common-dev-guide.md` §2。
- 示例代码位置统一登记在 `docs/code-examples.md`，本文件不复制代码块。
- 其他模块设计文档全集见 `docs/modules/portal-content-center/`（含 01~09 及该目录下的 `AGENTS.md`）。

## 测试指引

- 单元测试用 Mockito（Service / Adapter / Converter / Facade / Listener）；Controller 层用 MockMvc + H2，基类 `AbstractControllerIntegrationTest`；Mapper 集成测试用 Testcontainers MySQL，基类 `AbstractMapperIntegrationTest`。
- Mock 用户上下文用 `@WithMockEmpContext` 注解 + `MockEmpContextExtension`。
- **共享物理表清理**：本模块 `PORTAL_SHORTCUT` 表与 bootstrap 模块 IT 直连的 `onepl_test_bootstrap.PORTAL_SHORTCUT` 是同一张物理表。`PortalShortcutMapperIntegrationTest` 因此用了双 `@Sql`：一个 `BEFORE_TEST_METHOD` 清理遗留脏数据保证每个用例从干净状态开始，另一个 `executionPhase = AFTER_TEST_METHOD` 在用例结束后收尾清理本次写入的数据。原因：如果只清前不清后，最后一个测试方法写入的 `TEST_%` 行会遗留在共享物理表里，污染之后运行的 bootstrap 侧 `PortalWorkspaceMetricIT`（其快捷方式计数断言会因残留脏数据而失败）。今后新增涉及与 bootstrap 共享物理表的 Mapper/Controller 集成测试，都应遵循这个「准备 + 收尾」双 `@Sql` 模式。
