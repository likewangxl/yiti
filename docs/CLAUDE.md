# docs/ CLAUDE.md

本文件为 `docs/` 目录提供上下文说明，供 Claude Code 理解项目文档体系。

## 目录结构

```
docs/
├── common-dev-guide.md          # 共享开发规范（所有模块必须遵守）
├── modules/                     # 各模块的详细设计文档
│   ├── auth-permission-center/  # 认证授权中心 (8 份文档)
│   ├── common/                  # 公共基础设施 (3 份文档)
│   ├── system-governance-center/# 系统治理中心 (9 份文档)
│   └── workflow-center/         # 工作流中心 (9 份文档)
├── schema/                      # 数据库 DDL 和种子数据
└── superpowers/                 # 设计规格说明和实现计划
    ├── specs/                   # 模块设计规格
    └── plans/                   # 实现计划
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

**注意**: 以下模块尚未创建文档目录 (仅被 common-dev-guide.md 引用):
- customer-marketing-center
- portal-content-center
- business-application-center
- performance-engine-center
- report-analytics-center

## 使用指引

- 开发新功能时，先读取 `common-dev-guide.md` 了解规范
- 开发具体模块时，读取 `modules/<module-name>/` 下的详细设计文档
- 涉及数据库变更时，参考 `schema/` 目录的 DDL
- 查看模块依赖关系时，读取 `modules/<module-name>/09-依赖契约摘要.md`
