# docs/modules/performance-engine-center/AGENTS.md

本文件约束 `docs/modules/performance-engine-center/` 下的绩效模块文档，统一使用 UTF-8。

## 目录职责

- 本目录记录指标库、KPI 规则、目标管理、分配关系调整等设计，共 9 份文档。
- 模块当前以设计态为主，尤其要区分 workflow 相关内容的“当前实现态”与“目标态”。

## 维护要求

- `workflow-center` 当前对外 Java 契约只有 `WorkflowApi`，流程查询类 Java 接口尚未落地；文档不能直接把旧版 `WorkflowQueryApi` 当成现成依赖。
- 流程完成事件的消费说明必须标明：当前实现中的 `ProcessCompletedEvent` 仅包含最小字段，若需要审批结果、发起人、完成时间等扩展数据，需要二次查询或先扩展 workflow-center 事件契约。
- 目标修正、分配关系调整流程定义 key 与 businessKey 约定要与 workflow-center 当前实现态一致。

## 推荐阅读

- `01-功能规格.md`：绩效域职责。
- `06-并发与事务策略.md`：审批回写和后置同步链路。
- `09-依赖契约摘要.md`：上游依赖与事件消费差异。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
