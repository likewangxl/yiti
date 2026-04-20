# docs/modules/business-application-center/AGENTS.md

本文件约束 `docs/modules/business-application-center/` 下的业务申请文档，统一使用 UTF-8。

## 目录职责

- 本目录记录资产投放申请与中场支持申请设计，共 9 份文档。
- 模块代码已落地，文档必须以当前实现态为准，并清楚标注 workflow 依赖中哪些能力已经落地、哪些仍是目标契约。

## 维护要求

- 当前 `workflow-center` 对外 Java 契约已包含 `WorkflowApi` 与 `WorkflowQueryApi`；涉及参与者判定、流程结束扩展事件等仍未落地的能力时，必须显式写明“待 workflow-center 补齐”。
- 若文档使用 `WorkflowProcessCompletedEvent` 之类扩展 DTO，需要同时注明当前实现中的最小事件载荷与补齐路径。
- 中场支持场景 A/B 的流程 key、businessKey 规则、状态回写说明，要与 workflow-center 当前实现态保持可映射关系。

## 推荐阅读

- `01-功能规格.md`：场景 A/B 和状态机。
- `04-对外API契约.md`：业务申请模块自身的对外契约和上游事件假设。
- `09-依赖契约摘要.md`：当前实现态与规划态依赖差异。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
