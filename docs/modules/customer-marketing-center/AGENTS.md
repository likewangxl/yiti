# docs/modules/customer-marketing-center/AGENTS.md

本文件约束 `docs/modules/customer-marketing-center/` 下的客户营销文档，统一使用 UTF-8。

## 目录职责

- 本目录记录客户、线索、触达、入池、认领等设计，共 9 份文档。
- 该模块尚未整体落地实现，文档需要明确区分“当前上游已存在契约”和“未来期望补齐契约”。

## 维护要求

- 与 `workflow-center` 的联动，当前已实现的跨模块 Java 契约只有 `WorkflowApi`；涉及任务查询、参与者判定等能力时，必须注明是规划态接口还是当前需改走 REST/适配层。
- 线索审批、触达流程、删除审批等流程定义引用必须与 `workflow-center` 当前已存在的流程 key 保持一致。
- 涉及 SLA、流程轨迹、在途流程判断的描述，优先同步 `09-依赖契约摘要.md`。

## 推荐阅读

- `01-功能规格.md`：业务全链路说明。
- `03-接口设计与报文.md`：接口与流程动作。
- `09-依赖契约摘要.md`：与 auth/workflow/portal 等模块的依赖关系。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
