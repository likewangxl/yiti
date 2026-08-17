<!-- Parent: ../AGENTS.md -->

# portal-content-center 文档维护指导

本文件约束 `docs/modules/portal-content-center/`，继承上级文档规则。

## 目录职责

- 记录工作台聚合、导航、快捷方式、通讯录、产品、文档、担保和公告设计。
- portal 可以维护门户内容和用户配置，但不持有客户、业务申请、绩效或报表核心状态。

## 阅读顺序

1. `01-功能规格.md`：门户行为和内容边界。
2. `02-后端架构.md`：Adapter、Service、Facade 和降级方式。
3. `03-接口设计与报文.md`、`04-对外API契约.md`：REST 与通讯录/产品等公开契约。
4. `09-依赖契约摘要.md`：工作台聚合的上下游与降级。

## 契约同步与边界

- portal 直接依赖 auth、governance、workflow；不直接依赖核心业务域。绩效工作台数据由 bootstrap 桥接，不在 portal POM 增加 performance 依赖。
- business 和 performance 通过 portal 公开 API 使用通讯录/产品能力；契约变化时同步消费者文档。
- 待办聚合使用正式 `WorkflowQueryApi` 并由 Adapter 封装降级；不得文档化为读取 workflow 私表。
- 文件统一经 governance `FileApi`，通知经 `NotifyApi`；现存边界例外只能标为技术债，不作为设计范例。
