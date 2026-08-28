<!-- Parent: ../AGENTS.md -->

# business-application-center 文档维护指导

本文件约束 `docs/modules/business-application-center/`，继承上级文档规则。

## 目录职责

- 记录中场支持申请的接口、状态、权限、事务和依赖。
- `SUPPORT_REQUEST` 及业务状态由本模块持有，外部只能通过公开契约访问。
- 旧 `Loan*`、`LOAN_APPLY`、`/api/loans` 章节仅为 2026-08-28 切换前历史；当前资产立项归属
  `customer-marketing-center`，现行契约见其 `03-接口设计与报文.md` 与 `04-对外API契约.md`。

## 阅读顺序

1. `01-功能规格.md`：Support 状态机和中场场景 A/B；Loan 段落仅作历史迁移参考。
2. `03-接口设计与报文.md`、`04-对外API契约.md`：REST、公开 API 和事件。
3. `06-并发与事务策略.md`：提交、回调和条件更新幂等。
4. `07-审计要求.md`、`09-依赖契约摘要.md`：双视图权限与上下游。

## 契约同步与边界

- business 依赖 auth、governance、workflow、customer、portal；不得直连这些模块的 mapper/entity/内部 Service。
- Support 发起侧与承接侧共享表但使用不同 BizType 和数据范围；文档必须分别说明 owner/creator 与 department/assignee 口径。
- 中场路由、多产品拆单、submitGroupId、状态转移或回调事务变化时，同步 `01`、`03`、`06`。
- 工作流只通过公开 API/`ProcessCompletedEvent`；businessKey 和结果映射必须与当前源码一致，规划字段明确标注。
