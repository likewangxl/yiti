<!-- Parent: ../AGENTS.md -->

# performance-engine-center 文档维护指导

本文件约束 `docs/modules/performance-engine-center/`，继承上级文档规则。

## 目录职责

- 记录指标、KPI、目标、客户分配、调整审批、数据版本、导入导出、调度和 eval/REWARD 子域。
- 编号文档以外的专题说明只补充特定口径，不替代模块根 `AGENTS.md` 或公开契约。

## 阅读顺序

1. `01-功能规格.md`：绩效主干与评价子域职责。
2. `03-接口设计与报文.md`、`04-对外API契约.md`：REST、公开 API 和事件。
3. `06-并发与事务策略.md`：计算任务、锁、审批回调和补偿。
4. `09-依赖契约摘要.md`：上游与 report 读侧消费者；专题口径按任务选读。

## 契约同步与边界

- performance 依赖 auth、governance、workflow、customer、portal；其中 portal `AddressBookApi` 是正式员工校验依赖。report 只读消费其公开契约。
- 主干与 eval 使用不同 BizType；文档变更时必须保持鉴权、数据范围和公开 DTO 分层一致。
- 手工执行任务、异步 taskId、分布式锁、KPI 级联、Quartz 或审批状态变化时，同步 `01`、`03`、`06`、`09`。
- eval/REWARD 的当前表结构以目标库只读盘点、实体和 Mapper 共同核实；旧 `ddl-eval.sql` 与历史 spec/plan 仅作档案，不能替代当前结构或实施流程。
- 当前模块权威入口为 `../../../performance-engine-center/AGENTS.md`，历史演进通过 Git 追溯。
