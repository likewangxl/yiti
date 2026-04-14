# docs/modules/system-governance-center/AGENTS.md

本文件约束 `docs/modules/system-governance-center/` 下的治理域文档，统一使用 UTF-8。

## 目录职责

- 本目录记录字典、通知、文件、工作日历、审计、系统配置、任务调度等治理能力，共 9 份文档。
- 该模块是 `workflow-center`、`portal-content-center`、`performance-engine-center` 等模块的重要上游。

## 阅读顺序

- `01-功能规格.md`：理解治理域职责。
- `04-对外API契约.md`：跨模块调用治理能力时的首选文档。
- `09-依赖契约摘要.md`：查看上下游依赖与调用强度。

## 维护要求

- `CalendarApi`、`NotifyApi`、`FileApi` 等接口变更时，必须同步检查 `workflow-center`、`portal-content-center` 和 `performance-engine-center` 的文档描述。
- 治理域文档应清楚区分共享组件、管理后台能力与被业务模块复用的对外接口。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
