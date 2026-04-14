# docs/modules/workflow-center/AGENTS.md

本文件约束 `docs/modules/workflow-center/` 下的工作流文档，统一使用 UTF-8。

## 目录职责

- 本目录记录 Flowable 工作流中心设计与接口，共 9 份文档。
- 当前目录中，`04-对外API契约.md` 必须以**代码实现态**为准；历史规划稿中的接口名称若未落地，必须显式标注为“规划态”。

## 阅读顺序

- `01-功能规格.md`：先看工作流职责和流程能力边界。
- `03-接口设计与报文.md`：查看 REST 入口与报文结构。
- `04-对外API契约.md`：查看当前对外 Java 契约、REST 契约、内部事件与实现差异。
- `08-初始化数据清单.md` 与 `09-依赖契约摘要.md`：查看流程种子、节点配置和上下游关系。

## 维护要求

- 近期 Flowable 联调后，当前真正公开的跨模块 Java 契约只有 `WorkflowApi`；若文档提到 `WorkflowQueryApi`、`WorkflowConfigApi`、`WorkflowParticipantService`，必须注明其是否仍处于规划态。
- 修改控制器、DTO、事件、权限资源或种子数据时，至少同步 `03`、`04`、`08`、`09` 四份文档。
- 若流程终态、事件载荷或 REST 路径变更，要同步检查依赖模块文档。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
