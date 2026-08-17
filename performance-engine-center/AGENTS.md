# performance-engine-center 开发指导

本文件是绩效计算中心的模块级开发权威，与仓库根目录 `AGENTS.md` 同时适用；根文件的 TDD、鉴权、数据范围、SQL、MyBatis-Plus、日志和测试门禁不在此重复。发生冲突时以根文件为准。

## 模块职责

- 基础包：`com.bank.branch.platform.performance`
- Maven 坐标：`com.bank.branch.platform:performance-engine-center`
- 主干负责指标定义与计算、KPI 方案/计分、目标、客户分配、调整审批、数据版本、导入导出和指标调度。
- `eval/` 子域负责标签、规则、评价任务和打分；手工导入的 EVAL 评价任务与 REWARD 奖励分配是物理隔离的两条业务路径，仅在展示层合并。

## 依赖与边界

- 依赖 `auth-permission-center`、`system-governance-center`、`workflow-center`、`customer-marketing-center`、`portal-content-center` 及 `common-*`。
- 对 portal 的依赖是正式依赖：通过 `AddressBookApi` 校验指标结果导入中的员工；不得绕过 API 直查门户或认证模块用户表。
- 流程、组织用户、治理、客户与门户能力一律走对方 `*Api`/`*QueryApi`，禁止跨模块引用 mapper、entity 或内部 service。
- 分配/目标调整流程只能通过 workflow 公共 API 操作，不得直接调用 Flowable。
- `report-analytics-center` 可以只读消费本模块公开查询契约；本模块不得反向依赖 report。
- 新数据库访问使用 MyBatis-Plus；`eval` 下的 Mapper 可作为参考。`@TableId` 必须按物理表选择 `INPUT` 或 `AUTO`，本模块两种策略均存在，不得机械统一。

## 鉴权与数据范围

- 主干 `controller/` 只使用 `BizType.PERF_CONFIG` 或 `BizType.KPI_CALC`；`eval/controller/` 使用 `BizType.EVAL`。
- 所有 HTTP 处理方法必须声明 `@BizAuth`。`BizAuthRequiredArchTest` 中的例外只代表已登记的存量边界，不得作为新增端点模板。
- 列表、详情、计算、导入导出和调整审批均须在后端执行机构/人员数据范围；写操作必须结合实体状态做二次权限校验。
- `DataTaskController.reportStatus` 当前仍依赖 Session/RBAC 链路，只是被显式豁免细粒度 `@BizAuth`；仓库内没有可依赖的外部 API Token 机制，调用方不得假设存在。

## 计算、任务与事务规则

- 指标逻辑支持 SQL、PROC、EXPR/Groovy、SUMMARY；新增策略应通过既有策略接口扩展，不在 Controller 分支执行计算。
- 手工执行采用“提交即返回”：请求线程完成校验并预建 `PENDING` 的 `PERF_RUN_TASK`，保留当前用户作为 `started_by`，随后由 `MetricAsyncRunner` 后台计算。
- 异步计算必须复用预建 taskId；不得另插一条运行任务，否则轮询对象会永久停留在 `PENDING`，统计也会重复计数。
- 执行线程池拒绝策略不得回落到请求线程；拒绝后应把预建任务标记为 `FAILED`。批量响应的成功/失败表示提交结果，不代表最终计算结果。
- `PERF_RUN_TASK.task_key` 没有唯一约束，每次执行允许累积记录；不得依赖重复键实现任务幂等。
- `SysControlFacade`、`MetricLifecycleFacade` 在 Facade 层持有并释放分布式锁，Service 保持事务职责；`DataTaskService.report` 是薄 Facade 场景下的显式例外。
- `MetricCalcCompletedEvent` 触发 KPI 级联重算是 best-effort，健康检查提供补偿而非强一致消息保证；修改链路时必须覆盖事件丢失和重复处理。

## 调度、评价与文件规则

- 指标 Job 通过治理中心 `JobApi` 和 `sys_job_conf`/Quartz 管理；Job 类实现 Quartz `Job`，不添加 `@Component`，禁止恢复本地 `@Scheduled` 或不存在的 `PerfQuartzConfig`。
- `MetricSchedulerService.register()` 当前按运维要求不自动写入调度配置；不要把它当作注册范例或擅自恢复写入。
- `EVAL_USER_TAG` 每个用户至多一个标签；被评价人与评价人方向由标签在规则中的使用位置决定，不在人员标签上增加角色类型。
- REWARD 分配值允许 0、禁止 null 和负数；同组分配值总和必须严格等于组总额。
- 导入导出文件统一经治理中心 `FileApi` 访问对象存储；字段或旧注释中出现的 MinIO 字样不构成实现契约。

## 契约与实现入口

- 公开契约：`src/main/java/com/bank/branch/platform/performance/api/`
- 主干：`controller/`、`facade/`、`service/`、`strategy/`；评价子域：`eval/`
- 接口文档：`docs/modules/performance-engine-center/03-接口设计与报文.md`、`04-对外API契约.md`
- 表和主键策略以当前数据库、实体和 Mapper 映射共同核实；旧 DDL 或增量脚本不得单独作为当前结构真相。

## 测试入口

- 从仓库根目录运行 `mvn -pl performance-engine-center -am test`；包含 `*IT` 时运行 `mvn -pl performance-engine-center -am verify`。
- 单线程 Mapper IT 继承 `PerformanceMapperTestBase`；并发 IT 继承无 `@Transactional` 的 `PerformanceConcurrentTestBase`，配合 `TestDbCleaner` 和独立数据前缀，不能依赖测试线程的回滚清理工作线程写入。
- `support/PerfTestConfig` 是外部 API mock 装配入口；新增跨模块依赖时同步补齐，否则 Spring 测试上下文无法启动。
- 改动 Controller、DTO 或端点鉴权时运行 `arch/` 下的 BizAuth 与 Entity 泄漏守护；现有冻结白名单只允许存量签名，不得增加新违规。
- 跨模块契约变更后按根目录 stale SNAPSHOT 指引刷新依赖，再执行本模块和消费者测试。
