<!-- Parent: ../AGENTS.md -->

# system-governance-center 文档维护指导

本文件约束 `docs/modules/system-governance-center/`，继承上级文档规则。

## 目录职责

- 记录字典、配置、日历、审计、通知、文件和 Quartz/`sys_job_conf` 调度治理。
- governance 依赖 auth，并通过公开 API 向 workflow、portal、customer、business、performance、report 等模块提供能力。

## 阅读顺序

1. `01-功能规格.md`：治理能力和所有权。
2. `04-对外API契约.md`：业务模块调用治理能力的首选入口。
3. `06-并发与事务策略.md`、`07-审计要求.md`：锁、任务和审计一致性。
4. `09-依赖契约摘要.md`：上下游关系；`09-运维Runbook.md`：Quartz 与任务运维。

## 契约同步与边界

- `DictApi`、`FileApi`、`NotifyApi`、`AuditApi`、`JobApi` 等变化时，同步 `04`、`09` 和实际消费者文档。
- 业务模块只能通过治理公开 API 使用文件、通知、审计和任务能力，不得文档化为直连内部 Service 或对象存储客户端。
- Quartz 表、job key、集群排障和调度恢复以 `09-运维Runbook.md` 集中维护，其他文档只链接，不复制现场步骤。
- 调度配置或数据模型变化由 DBA/运维按审批实施；`05`、`08` 仅记录核实后的模型与配置要求。
