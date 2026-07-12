<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-14 | Updated: 2026-07-12 -->

# docs/modules/system-governance-center/AGENTS.md

本文件约束 `docs/modules/system-governance-center/` 下的治理域文档，统一使用 UTF-8。

## 目录职责

- 本目录记录字典、通知、文件、工作日历、审计、系统配置、任务调度等治理能力，共 10 份文档：`01-功能规格.md` ~ `09-依赖契约摘要.md`（9 份编号文档）+ `09-运维Runbook.md`（sys_job_conf / Quartz 集群调度运维权威指南，V1.9 整合新增，与 `09-依赖契约摘要.md` 编号重复但主题不同，引用时需按文件名全称区分）。
- 该模块是 `workflow-center`、`portal-content-center`、`performance-engine-center` 等模块的重要上游。

## 阅读顺序

- `01-功能规格.md`：理解治理域职责。
- `04-对外API契约.md`：跨模块调用治理能力时的首选文档。
- `09-依赖契约摘要.md`：查看上下游依赖与调用强度。
- `09-运维Runbook.md`：定时任务（sys_job_conf）与 Quartz 集群调度的运维操作手册。

## 维护要求

- `CalendarApi`、`NotifyApi`、`FileApi` 等接口变更时，必须同步检查 `workflow-center`、`portal-content-center` 和 `performance-engine-center` 的文档描述。
- 治理域文档应清楚区分共享组件、管理后台能力与被业务模块复用的对外接口。
- Quartz 相关运维操作（QRTZ_* 表、job_key 排障）以 `09-运维Runbook.md` 为权威来源，不要在其他文档中重复维护过时的运维步骤。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
