# docs/modules/ CLAUDE.md

本文件为 `docs/modules/` 目录提供上下文说明。这里存放了各模块的详细设计文档。

**文档状态（2026-07-13 刷新）**：全部 9 个模块文档已就绪，共计 77 份文档（较 2026-04-10 基线新增 3 份：`system-governance-center/09-运维Runbook.md`、`performance-engine-center/原业绩分配预览查询口径.md`、`report-analytics-center/10-大屏设计器操作指南.md`）。所有 9 个业务模块目前均已完整交付（详见根 `CLAUDE.md` 模块状态表），本目录文档需以此为准，不要再假设某模块"尚未整体落地"。

## 目录结构

```
docs/modules/
├── common/                                # 公共基础设施 (3 份)
│   ├── 01-功能规格.md
│   ├── 02-后端架构.md
│   └── 03-关键组件设计.md
├── auth-permission-center/                # 认证授权中心 (8 份)
│   ├── 01-功能规格.md
│   ├── 02-后端架构.md
│   ├── 03-接口设计与报文.md
│   ├── 04-对外API契约.md
│   ├── 05-表结构DDL.md
│   ├── 06-并发与事务策略.md
│   ├── 07-审计要求.md
│   └── 08-初始化数据清单.md
├── system-governance-center/              # 系统治理中心 (10 份)
│   ├── 01-功能规格.md ~ 09-依赖契约摘要.md（9 份）
│   └── 09-运维Runbook.md                  # sys_job_conf / Quartz 集群调度运维权威指南（V1.9 整合新增）
├── workflow-center/                       # 工作流中心 (9 份)
│   ├── 01-功能规格.md ~ 09-依赖契约摘要.md
├── portal-content-center/                 # 门户与内容中心 (9 份)
│   ├── 01-功能规格.md ~ 09-依赖契约摘要.md
├── customer-marketing-center/             # 客户营销中心 (9 份)
│   ├── 01-功能规格.md ~ 09-依赖契约摘要.md
├── business-application-center/           # 业务申请中心 (9 份)
│   ├── 01-功能规格.md ~ 09-依赖契约摘要.md
├── performance-engine-center/             # 绩效计算中心 (10 份)
│   ├── 01-功能规格.md ~ 09-依赖契约摘要.md（9 份）
│   └── 原业绩分配预览查询口径.md          # 分配预览查询口径补充说明（非编号系列，2026-06-09 新增）
└── report-analytics-center/               # 报表分析中心 (10 份)
    ├── 01-功能规格.md ~ 09-依赖契约摘要.md（9 份）
    └── 10-大屏设计器操作指南.md            # 大屏画布设计器V2操作手册（2026-07-13 新增）
```

## 各模块文档索引

### common (3 份文档)
公共基础设施层，包含统一响应模型、错误码规范、鉴权链路等核心组件的详细设计。
提供 5 个子模块：common-web / common-trace / common-aop / common-db / common-security。
所有业务模块必须依赖 common。

### auth-permission-center (8 份文档)
**支撑域** — 认证授权中心，涵盖用户认证、RBAC 权限控制、BizType 数据范围、组织架构等全部设计文档。
可被所有模块依赖，不依赖任何业务模块。

### system-governance-center (10 份文档，含 `09-运维Runbook.md`)
**支撑域** — 系统治理中心，提供字典管理、系统配置、工作日历、审计日志、通知、文件管理、定时任务等治理功能。
依赖：auth。被：workflow / portal / performance / report 等依赖。

### workflow-center (9 份文档)
**支撑域** — 工作流中心，嵌入 Flowable 7.0.1，提供流程启动、任务审批、SLA 超时管理、候选人解析等工作流能力。
系统唯一的 Flowable 集成边界。依赖：auth / governance。

### portal-content-center (9 份文档)
**通用域** — 门户与内容中心，负责工作台聚合、网址导航、通讯录、产品资料库、文档下载。
只做只读聚合，不持有业务状态。依赖：auth / governance / workflow / performance。

### customer-marketing-center (9 份文档)
**核心域** — 客户营销中心，覆盖客户营销全生命周期：标签→线索→审批→客户入池→认领→首次触达。已交付 V1.8（2026-05-01，114 Java + 49 测试）。
依赖：auth / workflow / portal。被：business-application / performance / report 依赖。

### business-application-center (9 份文档)
**核心域** — 业务申请中心，提供资产投放申请和中场支持申请两大业务流程。
场景 A/B 路由、多产品拆单、SUPPORT/SUPPORT_DEPT 双视图。
依赖：auth / workflow / customer / portal。

### performance-engine-center (10 份文档，含 `原业绩分配预览查询口径.md`)
**核心域** — 绩效计算中心，负责指标库、KPI 规则、目标管理、考核计算、分配关系调整、sys_control 版本控制，V1.6 起 Quartz 集群调度整合，并持续在演进 eval（内部相互评价）/ 奖励分配（REWARD）子域（表结构见 `docs/schema/ddl-eval.sql`，尚未回写本目录 05/09 文档）。
依赖：auth / governance / workflow / customer。

### report-analytics-center (10 份文档，含 `10-大屏设计器操作指南.md`)
**支撑域（纯只读）** — 报表分析中心，提供动态指标查询、固定管理报表、SQL 探查与大屏画布设计器（2026-07-13 画布设计器 V2 交付，操作手册见 `10-大屏设计器操作指南.md`）。已交付 V1.0（2026-04-25）。
**只读原则**：不反向写业务数据，不被任何模块依赖。
依赖：auth / governance / customer / performance。

## 文档编号说明

| 编号 | 主题 | 开发时关注 |
|------|------|------------|
| 01 | 功能规格 | 了解模块的业务职责 |
| 02 | 后端架构 | 了解包结构和类层级 |
| 03 | 接口设计与报文 | 了解 REST API 端点和请求/响应格式 |
| 04 | 对外API契约 | **跨模块开发时必须阅读** |
| 05 | 表结构DDL | 数据库表结构和约束 |
| 06 | 并发与事务策略 | 事务边界和并发控制 |
| 07 | 审计要求 | 审计日志记录规范 |
| 08 | 初始化数据清单 | 种子数据 |
| 09 | 依赖契约摘要 | **了解模块间依赖关系** |

> **说明**：`common` 模块只有 01-03 共 3 份文档（基础设施层无需完整的 9 份）；
> `auth-permission-center` 只有 01-08 共 8 份文档（作为最底层支撑域，无向下依赖，不需要 09）。

## 模块依赖层级图

```
                      ┌────────────┐
                      │  common    │  ← 所有模块必须依赖
                      └─────┬──────┘
                            │
                            ↓
                ┌───────────────────────┐
                │ auth-permission-center│  ← 支撑域（被所有模块依赖）
                └───────────┬───────────┘
                            │
         ┌──────────────────┼──────────────────┐
         ↓                  ↓                  ↓
┌─────────────────┐  ┌────────────┐  ┌──────────────────┐
│system-governance│  │  workflow  │  │ portal-content   │
│  (支撑域)        │  │  (支撑域)  │  │   (通用域)       │
└────────┬────────┘  └─────┬──────┘  └────────┬─────────┘
         │                 │                  │
         └─────────┬───────┴──────┬───────────┘
                   │              │
                   ↓              ↓
         ┌──────────────┐  ┌──────────────────┐
         │  customer    │  │   performance    │
         │   (核心域)    │  │    (核心域)      │
         └──────┬───────┘  └────────┬─────────┘
                │                   │
                └────────┬──────────┘
                         ↓
                ┌──────────────────┐
                │business-application│
                │    (核心域)       │
                └────────┬─────────┘
                         │
                         ↓
                ┌─────────────────┐
                │report-analytics │  ← 纯只读，不被任何模块依赖
                │   (支撑域)       │
                └─────────────────┘
```

## 使用指引

1. **了解模块职责**：先读 `01-功能规格.md`
2. **开发具体接口**：参考 `03-接口设计与报文.md`（字段级可执行精度）
3. **跨模块调用**：**必须**阅读目标模块的 `04-对外API契约.md`
4. **本模块依赖速查**：读 `09-依赖契约摘要.md`，一份文档看清所有依赖
5. **新建数据库表**：参考 `05-表结构DDL.md` + 项目根 `docs/schema/ddl-*.sql`
6. **并发/事务设计**：参考 `06-并发与事务策略.md`
7. **审计埋点**：参考 `07-审计要求.md` + `common-dev-guide.md` §6
8. **初始化数据**：参考 `08-初始化数据清单.md` + `docs/schema/seed-v1.sql`

## 2026-04-10 文档维度补齐记录

本次基于 `拆分要求.txt` 的 10 项维度对 5 个待开发模块做了系统性补充，消除了 P0/P1/P2 级别的关键缺口：

### 03-接口设计与报文 新增章节

| 模块 | 新增内容 | 章节 |
|------|---------|------|
| portal-content-center | 导出接口统一规范 + 产品导出列清单 + 错误码汇总 | 附录 H + 附录 I |
| customer-marketing-center | 导出接口统一规范（3 个导出接口的列清单 + 脱敏规则）+ 错误码汇总 | 附录 K + 附录 L |
| business-application-center | 导出接口统一规范（2 个导出接口的 14 列清单）+ 错误码汇总 | 附录 H + 附录 I |
| performance-engine-center | 导出接口统一规范 + **补齐 4 个原缺失的导出接口定义**（含最高风险的 cust-alloc 导出）+ 错误码汇总 | 附录 J + 附录 K |
| report-analytics-center | 导出接口统一规范（强制异步，500000 行上限）+ 错误码汇总 + 10 个错误响应 JSON 示例 + 快速导航章节 | 附录 I + 附录 J + 开头导航 |

### 04-对外API契约 补充

| 模块 | 新增内容 | 章节 |
|------|---------|------|
| business-application-center | 订阅的上游事件 `WorkflowProcessCompletedEvent` 完整 DTO + businessKey 解析 + 状态迁移规则 + 幂等要求 + 可观测性 | §8.0（新增） |
| performance-engine-center | AllocApi 补齐（从 3 个方法扩展到 10 个），含 `AllocSummaryDTO` / `AllocVersionDTO` 新增 DTO、调用示例、缓存策略 | §7 全面重写 |

### 05-表结构DDL 补充

| 模块 | 新增内容 | 章节 |
|------|---------|------|
| business-application-center | 字段来源与赋值时机溯源表（2 张主表 × 每个字段的来源/时机/可变性/赋值方） + 赋值时机 vs 状态对照表 | §4a（新增） |

### 01-功能规格 补充

| 模块 | 新增内容 | 章节 |
|------|---------|------|
| customer-marketing-center | 触达任务完整状态机图 + 状态转移矩阵 + SLA 规则 + 并发控制 + 定时任务清单 | §7.3bis（新增） |

### 08-初始化数据清单 补充

所有 5 个待开发模块新增"共享基线脚本指引"章节，明确：
- 共享 DDL 指向
- 共享种子数据 `seed-v1.sql` 的本模块段落
- 共享流程配置 `workflow-seed-v1.sql` 的本模块段落
- 首次建库 / 升级现有库的标准流程
- 权威路径表（开发者 / DBA / 权限管理员 / 流程工程师 / 运维）
- 关键校验 SQL

performance 模块额外新增 **定时任务汇总表**：5 个 job 的 cron/依赖/时长/失败处理矩阵 + 参数化配置 + JobApi 集成示例（§4.0）。

### 09-依赖契约摘要 补充

所有 5 个待开发模块新增 "DATA_SCOPE SQL 过滤片段具化示例" 章节，包括：
- 本模块表与 DATA_SCOPE 字段映射表
- MyBatis XML 过滤片段的完整示例（包括跨表 JOIN 场景）
- DATA_SCOPE 规则对应表（角色 × BizType 矩阵）
- 单元测试用例

### docs/schema DDL 脚本对齐

| 脚本 | 状态 | 变更 |
|---|---|---|
| `docs/schema/ddl-bizapp.sql` | 从 73 行 2 表 → 对齐 05 文档的完整 DDL | 补齐字段注释、索引（idx_created_time、idx_process_inst 等）、字符集统一 |
| `docs/schema/ddl-report.sql` | 从 24 行 1 表 → 对齐 05 文档的 3 张表 | 补齐 sql_probe_history、rpt_snapshot_task 表定义 |

### 新增全局文档

| 文件 | 说明 |
|---|---|
| `docs/export-spec-coverage.md` | 导出接口统一规范的覆盖状态跟踪文档，记录 5 个待开发模块的完成情况 + auth/governance/workflow 3 个已完成模块中未对齐范围的技术债清单 |

### 未处理项（已达成共识的跳过项）

- **P0-1 BPMN XML 目录**：`docs/workflow/` 目录和 6 份 BPMN 文件（lead_approve_v1.bpmn 等）暂未创建。用户明确表示暂时忽略。

### 后续建议

1. 为 5 个模块的 03 文档补充剩余接口的字段级校验注解（已有基础，个别接口需完善）
2. 为 customer-marketing-center 补齐 08 文档中 `lead_delete_approve_v1` 的节点候选配置（workflow-seed-v1.sql 已有，08 文档需引用）
3. 等待 BPMN 文件补充后，重新跑一次"模块文档完备性"评审
