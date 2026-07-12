<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-14 | Updated: 2026-07-12 -->

# docs/modules/report-analytics-center/AGENTS.md

本文件约束 `docs/modules/report-analytics-center/` 下的报表分析文档，统一使用 UTF-8。

## 目录职责

- 本目录记录动态指标、固定报表、SQL 探查与快照任务设计，共 9 份文档。
- 报表分析中心已交付 V1.0（2026-04-25，25 REST + 4 表 + 4 ExportStrategy 异步导出 + SQL 探查），坚持只读原则，不反向写业务状态，也不应被业务模块依赖。

## 维护要求

- 当前实现态不依赖 `workflow-center`（只读消费 auth/governance/performance/customer-marketing 的 `*Api`），若文档提及 workflow 数据，只能作为只读查询或读模型来源描述，不能写成对 workflow-center 的反向控制依赖或新增依赖。
- 任何跨模块查询说明，都要遵循“只读、异步、脱敏、可审计”原则。
- 若文档中出现尚未落地的跨模块查询 Java 契约，应标注为规划态，避免误导成当前可直接注入的 Bean。

## 推荐阅读

- `01-功能规格.md`：报表范围与非目标。
- `05-表结构DDL.md`：快照/探查表结构。
- `09-依赖契约摘要.md`：读侧依赖来源。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
