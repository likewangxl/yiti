<!-- Parent: ../AGENTS.md -->

# workflow-center 文档维护指导

本文件约束 `docs/modules/workflow-center/`，继承上级文档规则。

## 目录职责

- 记录流程启动、任务操作、候选人、表单、SLA、流程查询、业务映射和公开事件。
- workflow 是唯一允许直接调用 Flowable 的模块；其他模块只能使用公开 API/事件。

## 阅读顺序

1. `01-功能规格.md`：流程能力和状态边界。
2. `03-接口设计与报文.md`：REST 操作与校验。
3. `04-对外API契约.md`：`WorkflowApi`、`WorkflowQueryApi` 及公开事件。
4. `06-并发与事务策略.md`、`08-初始化数据清单.md`、`09-依赖契约摘要.md`：回调、配置和消费者。

## 契约同步与边界

- 文档中的 Java 接口、Bean、事件字段和 businessKey 必须能在当前 `api/` 源码定位；未来设想明确标为规划态。
- 流程终态、事件载荷、候选人解析或 REST 路径变化时，同步 `03`、`04`、`06`、`09` 及 customer/business/performance/portal 消费文档。
- 公开事件放在 `api.event`；不得要求消费者监听 workflow 内部 Service 事件或查询内部 mapper/entity。
- `08` 描述流程配置要求，不授权执行历史 seed；实际数据操作服从数据库门禁。
