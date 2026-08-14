# portal-content-center 开发指导

本文件是门户与内容中心的模块级开发权威，与仓库根目录 `AGENTS.md` 同时适用；根文件的 TDD、鉴权、数据范围、SQL、MyBatis-Plus、日志和测试门禁不在此重复。发生冲突时以根文件为准。

## 模块职责

- 基础包：`com.bank.branch.platform.portal`
- Maven 坐标：`com.bank.branch.platform:portal-content-center`
- 负责工作台聚合、网址导航、快捷方式、通讯录、产品资料、文档、担保信息和公告等通用门户能力。
- 本模块可以维护门户内容和用户门户配置，但不持有客户、业务申请、绩效或报表等核心域状态。

## 依赖与边界

- 依赖 `auth-permission-center`、`system-governance-center`、`workflow-center` 及 `common-*`。
- 用户、组织、字典、文件、通知、审计和待办查询只能通过对应公开 API 访问。文件上传下载统一经治理中心 `FileApi`，不得直连对象存储客户端。
- 不依赖 `customer-marketing-center`、`business-application-center`、`performance-engine-center` 或 `report-analytics-center`；`pom.xml` 不得为工作台聚合而新增反向核心域依赖。
- `business-application-center` 通过 `AddressBookApi`、`ProductApi` 使用门户能力；`performance-engine-center` 通过 `AddressBookApi` 校验员工。
- 工作台需要绩效指标时，portal 只依赖本模块定义的 `adapter.MetricApi`，由 bootstrap 的 `PerformanceMetricApiBridge` 完成依赖倒置；桥接 Bean 缺失时 `MetricAdapter` 按现有契约降级为空结果。
- 新 Mapper 使用 MyBatis-Plus，实体 `@TableId` 必须与物理主键一致：模块内同时存在应用生成的 `INPUT` 和数据库自增的 `AUTO`，不能统一套用一种策略。

## 数据权限与内容写入

- 需要机构或人员范围的导航、通讯录、产品、文档、担保和公告查询，必须在后端应用对应数据范围；明确属于全局内容的查询按领域契约处理。写操作在 Service 层基于当前持久化实体复核权限。
- 公告等管理端点必须标注 `@BizAuth` 并登记 `PT_RESOURCE`。`ShortcutController`、`WorkspaceController` 的登录态访问是架构测试中的显式例外；新增例外必须在测试白名单中写明业务理由。
- 导出是数据外流操作，必须独立授权、审计并限制结果规模；具体阈值和错误码以 Service 常量及 `PortalErrorCode` 为准，文档不复制易漂移数值。
- 通知发送使用治理中心 `NotifyApi`；通知失败的降级边界必须由调用场景显式定义，不能直接调用治理模块内部 Service。

## 聚合、事务与兼容规则

- portal 只做通用域聚合。核心域缺失或查询降级时，Adapter 应返回契约规定的空值/空集合并记录可诊断日志，不得在 portal 私表复制核心域状态。
- 产品、担保等当前导出行为以各自 ExportService 为准；超过同步处理能力时拒绝请求，不得在没有任务模型的情况下伪装成异步导出。
- `PortalCacheConfig` 目前只提供 key/TTL 常量和抖动算法，没有真实缓存读写；新增缓存前必须先确定实现、失效语义和多实例一致性，不能把常量存在当作缓存已启用。
- `WorkflowApprovalNotificationListener` 仍存在直接引用 workflow mapper/entity/内部事件及治理内部 Service 的存量边界例外。不得复制这种模式；整改时应把事件提升到 workflow 的 `api.event`、查询改走 `*QueryApi`、通知改走 `NotifyApi`。

## 契约与实现入口

- 公开契约：`src/main/java/com/bank/branch/platform/portal/api/`
- 工作台防腐层：`adapter/`；内容业务：`service/`、`facade/`；流程通知：`listener/`
- 接口文档：`docs/modules/portal-content-center/03-接口设计与报文.md`、`04-对外API契约.md`
- 当前契约和结构以源码、实际数据库及 Mapper 映射为准；文档与实现冲突时先只读核实。

## 测试入口

- 从仓库根目录运行 `mvn -pl portal-content-center -am test`；包含 `*IT` 时运行 `mvn -pl portal-content-center -am verify`。
- Service、Adapter、Converter、Facade、Listener 使用 Mockito；Controller 集成测试继承 `support/AbstractControllerIntegrationTest`。
- Mapper 集成测试继承 `support/AbstractMapperIntegrationTest`，使用 Testcontainers MySQL；用户上下文使用 `WithMockEmpContext`/`MockEmpContextExtension`。
- `arch/PortalBizAuthArchTest` 守护 Controller 鉴权及显式豁免。
- 涉及与 bootstrap 共用的物理表时，测试数据必须使用唯一前缀，并在 `BEFORE_TEST_METHOD` 准备、`AFTER_TEST_METHOD` 清理，避免跨模块 IT 相互污染。
