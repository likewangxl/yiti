<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-14 | Updated: 2026-07-12 -->

# docs/modules/performance-engine-center/AGENTS.md

本文件约束 `docs/modules/performance-engine-center/` 下的绩效模块文档，统一使用 UTF-8。

## 目录职责

- 本目录记录指标库、KPI 规则、目标管理、分配关系调整等设计，共 9 份编号文档 + `原业绩分配预览查询口径.md`（补充说明文档，非编号系列）。
- 模块已迭代至 V1.7 + V1.13 # 1（Quartz 集群调度整合 + 指标级调度 + 测试库一统），并持续在演进 eval（内部相互评价）与奖励分配（REWARD）子域；这两个子域目前只有 `docs/superpowers/specs|plans` 下的设计/实现记录和 `docs/schema/ddl-eval.sql`，尚未回写到本目录的 01-09 编号文档，引用时需注明“见 superpowers 设计记录，05/09 文档未同步”。

## 维护要求

- `workflow-center` 当前对外 Java 契约包含 `WorkflowApi` 与 `WorkflowQueryApi`（均为正式 Bean，`WORKFLOW_PARTICIPANT` 数据范围已在 V1.4 落地真实查询）；文档不要再写成“流程查询类 Java 接口尚未落地”。
- 流程完成事件的消费说明必须标明：当前实现中的 `ProcessCompletedEvent` 仅包含最小字段，若需要审批结果、发起人、完成时间等扩展数据，需要二次查询或先扩展 workflow-center 事件契约。
- 目标修正、分配关系调整流程定义 key 与 businessKey 约定要与 workflow-center 当前实现态一致。
- eval/REWARD 子域的表结构（`EVAL_TAG`/`EVAL_USER_TAG`/`EVAL_RULE`/`EVAL_ASSIGN_BATCH`/`EVAL_ASSIGN_ITEM`/`EVAL_REWARD_ITEM` 等）以 `docs/schema/ddl-eval.sql` 为准，`05-表结构DDL.md` 尚未覆盖。

## 推荐阅读

- `01-功能规格.md`：绩效域职责。
- `06-并发与事务策略.md`：审批回写和后置同步链路。
- `09-依赖契约摘要.md`：上游依赖与事件消费差异。
- `原业绩分配预览查询口径.md`：分配预览查询口径补充说明。
- eval/REWARD 子域现状：`docs/schema/ddl-eval.sql` + `docs/superpowers/specs/2026-07-11-eval-reward-assign-design.md` + `../../../performance-engine-center/CLAUDE.md`（模块根 CLAUDE.md 有完整变更日志）。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
