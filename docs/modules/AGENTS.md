# docs/modules 文档维护指导

本文件约束 `docs/modules/` 下的模块设计文档，继承 `../AGENTS.md` 与仓库根 `AGENTS.md`。各子目录的 `AGENTS.md` 提供更具体的阅读和同步要求。

## 目录与职责

- `common/`：公共基础设施组件。
- `auth-permission-center/`：认证、RBAC、组织和数据范围。
- `system-governance-center/`：字典、配置、日历、审计、通知、文件和调度治理。
- `workflow-center/`：平台唯一的 Flowable 集成边界。
- `portal-content-center/`：工作台聚合及门户内容。
- `customer-marketing-center/`：线索、客户、认领和触达。
- `business-application-center/`：中场支持申请；旧 Loan/资产投放内容仅作迁移历史，不是当前契约。
- `performance-engine-center/`：指标、KPI、目标、分配与 eval/REWARD。
- `report-analytics-center/`：只读聚合、报表、自有查询/导出任务和大屏配置。
- `red-engine-center/`：党建材料、审核、驾驶舱和归档。

目录中实际存在的文件就是可用索引；不要在本文件维护文档数量、版本完成度、类/端点/测试/资源数量。

## 阅读顺序

1. 先读模块根目录 `AGENTS.md`，确认当前依赖、领域状态与特殊红线。
2. `01` 理解职责和状态机，`02` 理解分层，`03` 核对 REST 契约。
3. 跨模块开发必须读提供方 `04`，并用源码 `api/` 包复核。
4. 并发、权限和依赖改动继续读 `06`、`07`、`09`。
5. `05`、`08` 仅描述数据模型和初始化数据设计；数据库实施服从 `docs/schema/AGENTS.md` 和根目录审批门禁。

## 模块边界

- 模块间仅通过 `*Api`/`*QueryApi` 和公开事件交互，文档不得建议跨模块引用 mapper、entity 或内部 service。
- auth 不依赖业务模块；workflow 是唯一直接调用 Flowable 的模块；report 不得被业务模块依赖。
- portal 不直接依赖核心业务域；需要绩效数据时由 bootstrap 桥接。performance 正式依赖 portal 的公开通讯录契约。
- report 的“只读”指不写上游业务数据；保存查询、导出任务、自由报表和大屏配置等自有表可以按本模块事务写入。
- red-engine 使用自管审核状态机，不套用 Flowable 的流程实例、businessKey 或流程映射概念。
- 依赖关系以当前 POM 和公开 API 为准；本目录图表或历史描述冲突时必须同步修正。

## 契约同步

- REST/DTO/错误码变化同步 `03`；公开 Java API/事件变化同步 `04` 和所有消费者的 `09`。
- 状态机、幂等、锁、事务和补偿变化同步 `01`、`06`。
- `@BizAuth`、数据范围、资源和审计变化同步 `03`、`07`、`09`，并核对 `common-dev-guide.md`。
- 数据模型文档只记录已由 DBA 实施并经只读核实的最终结构；不得把 `05` 当作 DDL 交付物或据此生成迁移脚本。
- 当前实现与规划必须显式分栏或标注；不能把尚未存在的接口、Bean、事件字段或调度能力写成已交付。

## 文档质量

- 保留稳定的领域边界、字段语义、状态转移、事务原因和降级策略。
- 删除版本流水账、历史修复叙事和易漂移数量；历史背景移入带日期的归档并清楚标注。
- 示例路径和类名必须可在当前仓库定位；外部系统表和自有表必须明确区分所有权。
