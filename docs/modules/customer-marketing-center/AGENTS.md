<!-- Parent: ../AGENTS.md -->

# customer-marketing-center 文档维护指导

本文件约束 `docs/modules/customer-marketing-center/`，继承上级文档规则。

## 目录职责

- 记录标签、线索、审批回调、客户主档、客户池、认领、触达任务、资产立项和报表设计。
- 客户主档、认领、触达及资产立项状态由 customer 持有；其他模块只能消费公开 API/事件。

## 阅读顺序

1. `01-功能规格.md`：线索到触达的领域链路和状态。
2. `03-接口设计与报文.md`、`04-对外API契约.md`：REST 与跨模块查询。
3. `06-并发与事务策略.md`：审批回调、认领和触达幂等。
4. `07-审计要求.md`、`09-依赖契约摘要.md`：高危操作和上下游。

## 契约同步与边界

- customer 依赖 auth、governance、workflow；business、performance 和 report 消费其公开契约。本模块不反向依赖这些消费者。
- 资产立项当前 REST 前缀为 `/api/marketing/asset-projects`，跨模块统计只通过
  `AssetProjectQueryApi`；旧 `LoanApi`、`LoanQueryApi` 和 `/api/loans` 不再是运行契约。
- 流程通过 `WorkflowApi` 发起并消费公开 `ProcessCompletedEvent`；不得直接调用 Flowable 或监听 workflow 内部事件。
- 线索状态、客户装配、认领唯一性、触达状态机或补偿语义变化时，同步 `01`、`03`、`06` 和相关消费者说明。
- 列表、详情、导出和写入使用一致的后端数据范围；权限或资源变化同步 `07`、`09`。
