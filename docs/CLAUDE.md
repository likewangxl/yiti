# docs/ CLAUDE.md

本文件为 `docs/` 目录提供上下文说明，供 研发 理解项目文档体系。

## 目录结构

```
docs/
├── common-dev-guide.md                    # 共享开发规范（所有模块必须遵守）
├── export-spec-coverage.md                # 导出接口统一规范覆盖状态跟踪文档
├── yiti-系统详细设计文档.md                # 总体详细设计（架构、横切机制、各模块设计、典型交易流、数据模型，Mermaid 图）
├── dafen.md                                # 银行内部相互评价（eval）模块设计方案，performance-engine-center eval 子域的原始设计源
├── customer-marketing-center-文档偏离检查报告-2026-04-26.md  # customer 模块文档-代码偏离核对报告（历史存档）
├── 指标结果模板.xlsx / 指标表上传模板.xlsx / 目标值上传模板.xlsx  # 绩效导入 Excel 模板样例
├── exports/                                # 独立 PlantUML ER 图产物链（build_yiti_schema_doc.py + plantuml.jar + yiti_er_*.puml/.png/.svg）
├── modules/                               # 各模块的详细设计文档
│   ├── common/                            # 公共基础设施 (3 份文档)
│   ├── auth-permission-center/            # 认证授权中心 (8 份文档)
│   ├── system-governance-center/          # 系统治理中心 (10 份文档，含 09-运维Runbook.md)
│   ├── workflow-center/                   # 工作流中心 (9 份文档)
│   ├── portal-content-center/             # 门户与内容中心 (9 份文档)
│   ├── customer-marketing-center/         # 客户营销中心 (9 份文档)
│   ├── business-application-center/       # 业务申请中心 (9 份文档)
│   ├── performance-engine-center/         # 绩效计算中心 (10 份文档，含 原业绩分配预览查询口径.md)
│   └── report-analytics-center/           # 报表分析中心 (9 份文档)
├── schema/                                # 数据库 DDL、种子数据、schema 可视化生成脚本
│   └── migrations/                        # 历史增量迁移脚本归档（非 Flyway）
├── testing/                               # API 集成测试脚本与测试数据
└── superpowers/                           # 设计规格、实现计划、运维脚本与会话存档（规模已大幅增长，见下方说明）
    ├── specs/                             # 模块设计规格
    ├── plans/                             # 实现计划 / 测试计划
    ├── sql/                               # 对齐脚本、测试种子、回归 SQL (带日期前缀)
    │   └── backup/                        # 执行破坏性 SQL 前的 mysqldump 备份
    ├── sessions/                          # 关键会话记录 (按日期归档)
    ├── reports/                           # 专项审计/核对报告 (按日期归档)
    └── scripts/                           # 一次性辅助脚本归档 (按日期归档)
```

> **规模说明（2026-07-12）**：`superpowers/specs/` 与 `superpowers/plans/` 已各自增长到 90+ 份文档，`superpowers/sql/` 已积累 150+ 个带日期前缀的对齐脚本，覆盖 4 月至 7 月的全部迭代（eval/奖励分配、工作流可视化设计器、绩效审批三模块等）。本文件不逐一列举，仅维护目录结构与分层约定；查找具体变更请按日期前缀在对应子目录搜索，或先查模块根 `CLAUDE.md` 的版本变更日志定位关键词再回溯 spec/plan。

## 开发规范文档

`common-dev-guide.md` 是所有模块必须遵守的共享开发规范，包含 9 个核心章节：

| 章节 | 内容 |
|------|------|
| 1. 统一响应模型 | `ResponseWrapper<T>` JSON 结构 (code, message, traceId, data, page, timestamp) |
| 2. 统一错误码规范 | `{PREFIX}-{HTTP_STATUS}{SEQ}` 格式，模块前缀注册表 (SYS/AUTH/GOV/PORTAL/CUST/WF/BIZ/PERF/RPT) |
| 3. 统一分页参数标准 | `PageRequest`/`PageResult`/`PageInfo`，pageSize 最大 100，超过 5000 行必须异步导出 |
| 4. 鉴权链路使用指南 | 5 层鉴权链: Filter → HandlerInterceptor → @BizAuth → DataPermissionChecker → AuditService |
| 5. DATA_SCOPE to SQL Filter | 7 种数据范围类型及 MyBatis XML 动态 SQL 模板 |
| 6. 统一审计切面 | `@AuditLog` 注解 + AOP 切面，8 类高危操作清单 |
| 7. 领域事件发布规范 | 命名约定、幂等要求、`@TransactionalEventListener(AFTER_COMMIT)` 模式 |
| 8. 统一数据传递规范 | DTO 分层、跨模块 QueryApi 模式、数据脱敏规则 |
| 9. 统一日志规范 | traceId 生成、MDC 注入、Logback 输出模式、慢 SQL 告警 |

## 模块文档

各模块子目录遵循统一编号文档体系：

| 文档 | 内容 |
|------|------|
| `01-功能规格.md` | 功能描述和用例 |
| `02-后端架构.md` | 包结构和类设计 |
| `03-接口设计与报文.md` | API 设计和请求/响应报文 |
| `04-对外API契约.md` | 跨模块 API 合约 |
| `05-表结构DDL.md` | 数据库表结构 |
| `06-并发与事务策略.md` | 事务和并发控制方案 |
| `07-审计要求.md` | 审计日志需求 |
| `08-初始化数据清单.md` | 种子数据 |
| `09-依赖契约摘要.md` | 依赖其他模块的契约 |

**全部 9 个模块文档已就绪（2026-07-13 刷新，共 77 份）**：

| 模块 | 文档数 | 类型 |
|---|---|---|
| `common` | 3 | 公共基础层 |
| `auth-permission-center` | 8 | 支撑域 |
| `system-governance-center` | 10（含 `09-运维Runbook.md`） | 支撑域 |
| `workflow-center` | 9 | 支撑域 |
| `portal-content-center` | 9 | 通用域 |
| `customer-marketing-center` | 9 | 核心域 |
| `business-application-center` | 9 | 核心域 |
| `performance-engine-center` | 10（含 `原业绩分配预览查询口径.md`） | 核心域 |
| `report-analytics-center` | 10（含 `10-大屏设计器操作指南.md`） | 支撑域（只读） |

## 数据脚本分层约定

项目有两类数据脚本，承担不同角色，**不能互相覆盖**：

| 层级 | 目录 | 命名 | 作用 | 是否允许手动重跑 |
|------|------|------|------|---------|
| 基线 DDL + 初始种子 | `docs/schema/` | `ddl-*.sql` / `seed-v1.sql` / `workflow-seed-v1.sql` / `ddl-quartz.sql`（V1.6）/ `ddl-eval.sql`（eval 子域）/ `ddl-yiti-prod-golive.sql` + `seed-yiti-prod-golive.sql`（生产上线基线） | 从 0 搭库用，定义表结构和最小可用种子 | 仅首次建库 |
| 运维/对齐/测试种子 | `docs/superpowers/sql/` | `YYYY-MM-DD-*-align*.sql` 等 | 在已有库上按日期做增量对齐、测试数据重建 | 每次跑前必须备份到 `sql/backup/` |

> **V1.6 quartz 整合（2026-04-25）**：`docs/schema/ddl-quartz.sql` 新增，包含 11 张 `QRTZ_*` 表，由 `spring.quartz.jdbc.initialize-schema=never` 触发手动初始化，仅 `system-governance-center` 持有。
> **eval 子域（2026-05-27 起持续演进）**：`docs/schema/ddl-eval.sql` 承载内部相互评价 + 奖励分配（REWARD）表结构，`docs/modules/performance-engine-center/05-表结构DDL.md` 尚未同步收录，详情见模块根 `performance-engine-center/CLAUDE.md` 的版本变更日志。
> **schema 可视化产物**：详见 `docs/schema/AGENTS.md`（`_gen_schema_doc.py`/`_render_er_image.py` 生成的 ER 图 + `docs/exports/` 下并存的另一套 PlantUML 产物链）。

### `docs/schema/` DDL 文件清单

| 文件 | 模块 | 说明 |
|---|---|---|
| `ddl-auth.sql` | auth-permission-center | 认证授权 + RBAC + 数据范围 |
| `ddl-governance.sql` | system-governance-center | 字典 / 配置 / 日历 / 审计 / 通知 / 文件 / 任务（不含 Quartz QRTZ_*） |
| `ddl-quartz.sql` | system-governance-center（V1.6 引入） | Quartz JDBC JobStore 集群所需 11 张 `QRTZ_*` 表，由 `spring.quartz.jdbc.initialize-schema=never` 触发手动初始化 |
| `ddl-workflow.sql` | workflow-center | Flowable 嵌入式表 |
| `ddl-customer.sql` | customer-marketing-center | 客户营销 |
| `ddl-bizapp.sql` | business-application-center | 业务申请 |
| `ddl-portal.sql` | portal-content-center | 门户内容 |
| `ddl-performance.sql` | performance-engine-center | 绩效计算配置 + 业务数据表 |
| `ddl-report.sql` | report-analytics-center | 报表分析，4 张自有表（已随模块 V1.0 交付，非待实现） |
| `ddl-eval.sql` | performance-engine-center / eval 子域（2026-05-27 引入） | 内部相互评价 + 奖励分配（REWARD）表，`05-表结构DDL.md` 尚未同步收录 |
| `ddl-yiti-prod-golive.sql` | 全量（生产上线基线） | 从权威源 `yiti` 库导出的全表建库建表脚本，与 `seed-yiti-prod-golive.sql` 成对 |

> **历史记录，非当前最新版**：以下 2026-04-10 记录是 PT_* 对齐脚本机制刚建立时的首个样例。此后 `docs/superpowers/sql/` 下持续新增了大量按日期前缀的对齐/授权/资源注册脚本（截至 2026-07-12 已超过 150 个），**查找当前有效的权限/资源配置务必按文件名日期从新到旧检索**，不要只看本节示例就当作现状。

### 2026-04-10 PT_* 对齐脚本（历史记录）

| 文件 | 内容 | 说明 |
|------|------|------|
| `docs/superpowers/sql/backup/2026-04-10-pt-tables-backup.sql` | 对齐前 mysqldump 备份 | 覆盖 `PT_RESOURCE` / `PT_ROLE_RESOURCE` / `PT_ROLE_BIZ_SCOPE` / `PT_USER` / `PT_USER_ROLE` / `EXT_USER_ORG` / `EXT_ORG_INFO` |
| `docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql` | 全清全建 + 测试种子 | 84 行 `PT_RESOURCE`、458 行 `PT_ROLE_RESOURCE`、70 行 `PT_ROLE_BIZ_SCOPE`、10 个测试账户及角色/机构绑定 |

**关键约束（该 2026-04-10 样例脚本的约束，机制层面长期有效）**：

1. `PT_RESOURCE.RESOURCE_ID` 长度 ≤ 20，命名规范：`A_*`（auth）/ `G_*`（governance）/ `W_*`（workflow）
2. 路径变量统一写成 AntPath `*`（如 `/api/admin/roles/*`），由 `ResourceMatcher` 匹配
3. 角色分两档：`R_ADMIN` + `R_BACK_TECH` 拥有全部 84 条；其余 10 个业务角色只授予 29 条通用资源
4. 10 个测试账户统一密码 `123456`，BCrypt hash 为 `$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m`（python bcrypt 生成，已验证）
5. 该脚本**覆盖** `docs/schema/seed-v1.sql` 里 PT_* 的旧种子；未来如要再次对齐，必须：先备份 → 再以新日期前缀新增脚本 → 不要原地修改已归档脚本

完整变更背景和测试结果见：
- `docs/superpowers/plans/2026-04-10-interface-full-test-plan.md`（100 条 curl 用例 + 实测 80% 通过率）
- `docs/superpowers/sessions/2026-04-10-interface-test-session-record.md`（会话全记录、修复的 12 个缺陷清单）

## 使用指引

- 开发新功能时，先读取 `common-dev-guide.md` 了解规范
- 开发具体模块时，读取 `modules/<module-name>/` 下的详细设计文档
- **首次搭库**参考 `schema/` 目录的 DDL + `seed-v1.sql`（或生产口径用 `ddl-yiti-prod-golive.sql` + `seed-yiti-prod-golive.sql`）
- **已有库的 PT_* 对齐 / 测试数据重建**使用 `superpowers/sql/` 下带日期前缀的最新脚本（按日期排序取最新，不要只看 2026-04-10 的首个样例），执行前务必备份到 `superpowers/sql/backup/`
- 查看模块依赖关系时，读取 `modules/<module-name>/09-依赖契约摘要.md`
- **API 集成测试**参考 `testing/` 目录（`CLAUDE.md` 有当前环境差异说明）
- 编写新的对齐/回归 SQL 时，命名强制 `YYYY-MM-DD-<主题>.sql`，不要原地修改历史脚本
