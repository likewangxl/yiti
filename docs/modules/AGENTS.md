# docs/modules 文档维护指导

本文件约束 `docs/modules/` 下的模块设计文档，继承 `../AGENTS.md` 与仓库根 `AGENTS.md`。模块特有的阅读和同步要求集中在下方差异表；不在模块文档目录重复维护子目录级 `AGENTS.md`。

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

1. 编辑本目录模块文档时，先读仓库根 `../../AGENTS.md`，再读实际代码模块根 `../../<模块>/AGENTS.md`；common 子模块还要读取对应的 `../../common/<子模块>/AGENTS.md`。
2. 再读上级 `../AGENTS.md` 与本文件，确认文档事实来源、模块差异和特殊红线。
3. `01` 理解职责和状态机，`02` 理解分层，`03` 核对 REST 契约。
4. 跨模块开发必须读提供方 `04`，并用源码 `api/` 包复核。
5. 并发、权限和依赖改动继续读 `06`、`07`、`09`。
6. `05`、`08` 仅描述数据模型和初始化数据设计；数据库实施服从 `docs/schema/AGENTS.md` 和根目录审批门禁。

## 模块差异与特有门禁

下表只保留各模块相对于上级通用规则的稳定差异。依赖关系以实际代码模块的 POM 和公开 API 为准；表中路径用于进入对应代码模块的权威指导文件。

| 模块 | 现行文档需要额外保持的规则 |
| --- | --- |
| [`common`](../../common/AGENTS.md) | 只记录 `common-web`、`common-trace`、`common-security`、`common-aop`、`common-db` 公共基础设施及 `LockManager`/`PT_LOCK`，不放业务流程、端点或 DTO 清单；common 不反向依赖业务模块。公共响应、异常、鉴权、审计、数据范围、锁、Trace 或日志变化先同步 `common-dev-guide.md`，再同步本目录，并检查所有消费者和对应测试。 |
| [`auth-permission-center`](../../auth-permission-center/AGENTS.md) | 认证、Session、用户、组织、RBAC、资源、BizType 和数据范围属于平台底座；只依赖 common，权限解析失败必须 fail-close。`CurrentUserApi`、`BizScopeApi`、`OrgApi`、`UserApi`、`RoleApi` 等公开能力以代码 `api/` 为准；资源匹配、`@BizAuth`、BizType/Action 变化要核对现有枚举、资源和范围解析链，同步 REST、并发、审计和消费者契约，不能用前端隐藏替代后端权限；`05`/`08` 仅记录 RBAC/PT_* 模型与初始化要求。 |
| [`system-governance-center`](../../system-governance-center/AGENTS.md) | 维护字典、配置、日历、审计、通知、文件和 Quartz/`sys_job_conf`；依赖 auth，并通过公开 API 向 workflow、portal、customer、business、performance、report 提供治理能力。业务模块只能经 `DictApi`、`FileApi`、`NotifyApi`、`AuditApi`、`JobApi` 等公开 API 使用。Quartz 表、job key、集群排障和恢复步骤集中在 `09-运维Runbook.md`，其他文档只链接；`05`/`08` 只记录核实后的模型和配置要求。 |
| [`workflow-center`](../../workflow-center/AGENTS.md) | 覆盖流程启动、任务、候选人、表单、SLA、查询、业务映射和公开事件；只有 workflow 可直接调用 Flowable，其他模块只能用 `WorkflowApi`、`WorkflowQueryApi` 等公开契约。Java 接口、Bean、事件字段和 businessKey 必须能在当前 `api/` 源码定位，规划态必须明确标注；公开事件位于 `api.event`，不得要求消费者监听内部事件或读取私表。流程终态、事件载荷、候选人解析或 REST 路径变化时，需同步相关消费者文档；`08` 仅描述流程配置要求。 |
| [`portal-content-center`](../../portal-content-center/AGENTS.md) | 维护工作台聚合、导航、快捷方式、通讯录、产品、文档、担保和公告，不持有客户、业务申请、绩效或报表核心状态。直接依赖 auth、governance、workflow，不直接依赖核心业务域；待办经 `WorkflowQueryApi` 和 Adapter 聚合并保留降级，绩效工作台由 bootstrap 桥接。business/performance 使用 portal 的公开通讯录/产品契约；文件、通知分别使用治理 `FileApi`、`NotifyApi`，已有越界调用只能标为技术债。 |
| [`customer-marketing-center`](../../customer-marketing-center/AGENTS.md) | 维护标签、线索、审批回调、客户主档、客户池、认领、触达、资产立项和报表；依赖 auth、governance、workflow，客户及认领/触达/资产立项状态由本模块持有，business、performance、report 只能消费公开契约，本模块不反向依赖这些消费者。资产立项现行 REST 前缀为 `/api/marketing/asset-projects`，统计使用 `AssetProjectQueryApi`，旧 `LoanApi`/`LoanQueryApi`/`/api/loans` 不属于现行契约；流程只经 `WorkflowApi` 和 `ProcessCompletedEvent`，不得直接调用 Flowable。线索状态、客户装配、认领唯一性、触达状态机或补偿语义变化时，需同步状态、REST、事务和消费者说明；列表、详情、导出和写入必须使用一致的数据范围。 |
| [`business-application-center`](../../business-application-center/AGENTS.md) | 当前文档重点是 `SUPPORT_REQUEST` 中场支持申请；旧 `Loan*`、`LOAN_APPLY`、`/api/loans` 只作迁移历史，资产立项归 customer。模块依赖 auth、governance、workflow、customer、portal，但只能经公开 API/事件交互。`01` 中的 Support 场景 A/B 与状态机是当前阅读重点；发起侧与承接侧共享表但分别按 owner/creator 与 department/assignee 口径、BizType 和数据范围校验；中场路由、多产品拆单、`submitGroupId`、工作流 businessKey/结果映射或 `ProcessCompletedEvent` 回调变化时同步状态、REST、事务和消费者说明。 |
| [`performance-engine-center`](../../performance-engine-center/AGENTS.md) | 覆盖指标、KPI、目标、客户分配、调整审批、数据版本、导入导出、调度及 eval/REWARD；专题口径只作补充，不替代模块契约。模块依赖 auth、governance、workflow、customer、portal，report 只读消费其公开契约；portal 的 `AddressBookApi` 是正式员工校验依赖，主干与 eval 使用不同 BizType；手工任务、异步 taskId、锁、KPI 级联、Quartz 或审批状态变化需同步状态/REST/事务/依赖文档。eval/REWARD 结构需由目标库、实体和 Mapper 共同核实，旧 `ddl-eval.sql` 和历史 spec/plan 仅作档案。 |
| [`report-analytics-center`](../../report-analytics-center/AGENTS.md) | 报表、保存查询、导出、SQL 探查、自由报表、外部数据查询和大屏可维护本模块自有状态，但不得写上游或外部数据；依赖 auth、governance、customer、performance，不依赖 workflow，业务模块不得依赖 report。当前不暴露 `*Api`/`*QueryApi`，外部/上游读取应回到数据所有者公开 API（存量越界读取标为技术债）；外部同步表只读且不归 report 管理结构。保存查询乐观锁、导出状态、大屏草稿/发布快照和访问范围变化要同步相关契约；SQL 探查、导出和大屏访问必须独立资源、理由、审计、脱敏并 fail-close。大屏操作细节见 `10-大屏设计器操作指南.md`。 |
| [`red-engine-center`](../../red-engine-center/AGENTS.md) | 覆盖党组织、材料上报、两级审核、评分、驾驶舱/预警、年度归档和导出；目录缺少编号文档时以实际文件为准，不承诺补齐。依赖 auth 和 governance；审核状态机由本模块自管，不接 Flowable，也不使用流程实例、businessKey 或 `BIZ_PROCESS_MAP` 语义；公开 API 是否存在以当前 `api/` 源码为准，DTO、错误码、状态、范围、资源或导出变化要核对 `PT_RESOURCE`、角色绑定和测试，历史种子/对齐 SQL 只作追溯。 |

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
