# workflow-center/AGENTS.md

本文件补充根 [AGENTS.md](../AGENTS.md)，适用于工作流中心。

## 模块边界

本模块负责流程启动、任务办理、候选人、SLA、流程设计器、流程监控和任务转交，基础包为 `com.bank.branch.platform.workflow`。

- 依赖 common、`auth-permission-center` 和 `system-governance-center`。
- 本模块是平台唯一可直接调用 Flowable API 的模块；其他模块只能使用 `WorkflowApi`、`WorkflowQueryApi`、`TodoQueryApi` 等 `api/` 契约，不得注入 Flowable Service 或访问本模块私有实现和表。
- 方法、端点、事件和表结构以源码及 `docs/modules/workflow-center/` 契约文档为准，不在本文件维护数量或完整清单。

## 流程身份与授权

- 每个流程业务必须维护唯一 `business_key`（格式 `BIZ_TYPE:id`）及 `BIZ_PROCESS_MAP` 映射；启动前检查同一业务键的运行态冲突。
- REST 入口执行资源 RBAC、`@BizAuth` 和业务对象权限。按显式 `empId` 执行的无会话 API 不读取登录态，也不会替调用方完成渠道鉴权；SOAP/callpu 等调用方必须先验证身份、权限和 empId 绑定。
- 候选组、受理人和转交接收人只能由服务端根据有效用户、角色、机构和流程配置解析，不能信任前端直传的权限结论。
- 权限、机构范围或流程映射无法解析时 Fail Close；不得用“流程引擎已有任务”替代平台业务授权。

## 流程监控与机构快照

- `ProcessMonitorService` 按机构数据范围过滤流程。系统管理员或明确的 ALL 范围才可不加机构条件；ORG/ORG_SUBTREE 使用机构编码集合；未知、空范围和不适用的范围类型返回空结果。
- `WfProcessOrgService` 是参与机构快照的唯一写入入口。流程启动、分配、候选、认领、审批和转交挂点统一调用它，禁止各处直接写 `WF_PROCESS_ORG`。
- 快照是辅助授权索引，写入失败不能反向破坏已经完成的 Flowable 命令；服务内部必须兜住机构查询和持久化异常，同时保留可定位日志。
- 层级角色候选任务在尚无 assignee 时也要记录确定的候选机构；无法确定机构的不限机构候选组不应伪造快照。
- 监控中的活动任务解析当前基于顺序单活假设。引入并行网关或多实例并行时，必须先改造任务集合语义及转交入口，不能继续只保留单个 taskId。

## 任务转交

- 转交采用“发起待认领 → 接收人认领或拒绝”的两阶段模型；只有认领成功后才真正变更办理权。
- 发起、认领、拒绝、撤回都要校验当前记录状态、操作者身份、任务仍有效以及并发版本，禁止恢复一步直接改 assignee 的旧路径。
- 转交过程同样维护流程机构快照和审计事件；任何重试必须幂等，不能生成多个有效接收关系。

## 流程状态与事件

- `BIZ_PROCESS_MAP.processStatus` 表示 workflow 状态机状态；`ProcessCompletedEvent.outcome` 表示下游业务审批结论。消费方必须使用 outcome 判断批准/拒绝，禁止混用两套语义。
- 会签完成条件依赖的变量必须在进入多实例节点前显式初始化；尤其不能假定“未拒绝”变量会由批准路径自动创建。
- Spring 内部事件载荷以 `api/event/` 和发布方源码为准。新增或修改事件字段时检查全部监听模块及序列化兼容性。

## 流程设计器

- 设计器发布流程使用 `DSN_` 影子 definition key。发布时校验完整图，再部署 BPMN，并按该影子 key 整图替换候选人配置；不能只局部追加导致运行定义与候选规则不一致。
- 变更发布逻辑前评估已发布定义和在途实例，禁止无意覆盖生产运行流程。
- `outgoingBranches` 只对设计器动态流程解析；审批节点连接网关时需穿透网关取得命名出边。静态流程或解析失败按明确契约返回空集合，不得影响任务详情主链路。
- 修改图模型、BPMN 生成或候选规则时，必须同时验证保存、发布、重新加载和运行时解析，而不只测前端画布。

## Quartz 与通知

工作流 SLA 和通知依赖 governance 的 `CalendarApi`、`NotifyApi`、`JobApi`。本模块不得自建 Scheduler、Trigger 或平行作业日志；定时任务遵循 governance 的声明式注册和集群防重规则。

## 测试安全与验证

- workflow 测试需要真实关系型数据库和部分真实 Flowable 语义，不能假定是 H2 或完全无副作用。运行前必须打开实际 test profile 核对 datasource、schema 自动更新和外部组件开关。
- 如果任何测试配置指向 `yiti`，必须停止执行并按根文件数据库门禁处理；`@Transactional`/`@Rollback`、Mapper 测试基类或“只跑一个 IT”都不构成写入授权，也覆盖不了引擎建表和独立事务。
- 获准的隔离测试要覆盖业务键唯一性、候选人、流程映射、事件 outcome、机构快照、转交状态机、发布影子 key、事务回滚和异常 Fail Close。
- 跨模块或 bootstrap 测试前按根文件刷新本地 SNAPSHOT；前端工作流页面改动还必须完成根文件要求的官方 `playwright-cli` 真实页面验收。
