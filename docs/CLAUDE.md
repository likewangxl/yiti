# docs/ CLAUDE.md

本文件为 `docs/` 目录提供上下文说明，供 Claude Code 理解项目文档体系。

## 目录结构

```
docs/
├── common-dev-guide.md                    # 共享开发规范（所有模块必须遵守）
├── modules/                               # 各模块的详细设计文档
│   ├── common/                            # 公共基础设施 (3 份文档)
│   ├── auth-permission-center/            # 认证授权中心 (8 份文档)
│   ├── system-governance-center/          # 系统治理中心 (9 份文档)
│   ├── workflow-center/                   # 工作流中心 (9 份文档)
│   ├── portal-content-center/             # 门户与内容中心 (9 份文档)
│   ├── customer-marketing-center/         # 客户营销中心 (9 份文档)
│   ├── business-application-center/       # 业务申请中心 (9 份文档)
│   ├── performance-engine-center/         # 绩效计算中心 (9 份文档)
│   └── report-analytics-center/           # 报表分析中心 (9 份文档)
├── schema/                                # 数据库 DDL 和初始种子数据
└── superpowers/                           # 设计规格、实现计划、运维脚本与会话存档
    ├── specs/                             # 模块设计规格
    ├── plans/                             # 实现计划 / 测试计划
    ├── sql/                               # 对齐脚本、测试种子、回归 SQL (带日期前缀)
    │   └── backup/                        # 执行破坏性 SQL 前的 mysqldump 备份
    └── sessions/                          # 关键会话记录 (按日期归档)
```

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

**全部 9 个模块文档已就绪（2026-04-10 更新）**：

| 模块 | 文档数 | 类型 |
|---|---|---|
| `common` | 3 | 公共基础层 |
| `auth-permission-center` | 8 | 支撑域 |
| `system-governance-center` | 9 | 支撑域 |
| `workflow-center` | 9 | 支撑域 |
| `portal-content-center` | 9 | 通用域 |
| `customer-marketing-center` | 9 | 核心域 |
| `business-application-center` | 9 | 核心域 |
| `performance-engine-center` | 9 | 核心域 |
| `report-analytics-center` | 9 | 支撑域（只读） |

## 数据脚本分层约定

项目有两类数据脚本，承担不同角色，**不能互相覆盖**：

| 层级 | 目录 | 命名 | 作用 | 是否允许手动重跑 |
|------|------|------|------|---------|
| 基线 DDL + 初始种子 | `docs/schema/` | `ddl-*.sql` / `seed-v1.sql` / `workflow-seed-v1.sql` | 从 0 搭库用，定义表结构和最小可用种子 | 仅首次建库 |
| 运维/对齐/测试种子 | `docs/superpowers/sql/` | `YYYY-MM-DD-*-align*.sql` 等 | 在已有库上按日期做增量对齐、测试数据重建 | 每次跑前必须备份到 `sql/backup/` |

### `docs/schema/` DDL 文件清单

| 文件 | 模块 | 说明 |
|---|---|---|
| `ddl-auth.sql` | auth-permission-center | 认证授权 + RBAC + 数据范围 |
| `ddl-governance.sql` | system-governance-center | 字典 / 配置 / 日历 / 审计 / 通知 / 文件 / 任务（不含 Quartz QRTZ_*） |
| `ddl-workflow.sql` | workflow-center | Flowable 嵌入式表 |
| `ddl-customer.sql` | customer-marketing-center | 客户营销 |
| `ddl-bizapp.sql` | business-application-center | 业务申请 |
| `ddl-portal.sql` | portal-content-center | 门户内容 |
| `ddl-performance.sql` | performance-engine-center | 绩效计算配置 + 业务数据表 |
| `ddl-report.sql` | report-analytics-center | 报表分析（待实现） |
| `ddl-quartz.sql` | system-governance-center（V1.6 引入） | Quartz JDBC JobStore 集群所需 11 张 `QRTZ_*` 表，由 `spring.quartz.jdbc.initialize-schema=never` 触发手动初始化 |

### 2026-04-10 PT_* 对齐脚本（当前最新版）

| 文件 | 内容 | 说明 |
|------|------|------|
| `docs/superpowers/sql/backup/2026-04-10-pt-tables-backup.sql` | 对齐前 mysqldump 备份 | 覆盖 `PT_RESOURCE` / `PT_ROLE_RESOURCE` / `PT_ROLE_BIZ_SCOPE` / `PT_USER` / `PT_USER_ROLE` / `EXT_USER_ORG` / `EXT_ORG_INFO` |
| `docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql` | 全清全建 + 测试种子 | 84 行 `PT_RESOURCE`、458 行 `PT_ROLE_RESOURCE`、70 行 `PT_ROLE_BIZ_SCOPE`、10 个测试账户及角色/机构绑定 |

**关键约束**：

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
- **首次搭库**参考 `schema/` 目录的 DDL + `seed-v1.sql`
- **已有库的 PT_* 对齐 / 测试数据重建**使用 `superpowers/sql/` 下带日期前缀的最新脚本，执行前务必备份到 `superpowers/sql/backup/`
- 查看模块依赖关系时，读取 `modules/<module-name>/09-依赖契约摘要.md`
- 编写新的对齐/回归 SQL 时，命名强制 `YYYY-MM-DD-<主题>.sql`，不要原地修改历史脚本
