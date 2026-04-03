# docs/modules/ CLAUDE.md

本文件为 `docs/modules/` 目录提供上下文说明。这里存放了各模块的详细设计文档。

## 目录结构

```
docs/modules/
├── auth-permission-center/
│   ├── 01-功能规格.md
│   ├── 02-后端架构.md
│   ├── 03-接口设计与报文.md
│   ├── 04-对外API契约.md
│   ├── 05-表结构DDL.md
│   ├── 06-并发与事务策略.md
│   ├── 07-审计要求.md
│   └── 08-初始化数据清单.md
├── common/
│   ├── 01-功能规格.md
│   ├── 02-后端架构.md
│   └── 03-关键组件设计.md
├── system-governance-center/
│   ├── 01-功能规格.md
│   ├── 02-后端架构.md
│   ├── 03-接口设计与报文.md
│   ├── 04-对外API契约.md
│   ├── 05-表结构DDL.md
│   ├── 06-并发与事务策略.md
│   ├── 07-审计要求.md
│   ├── 08-初始化数据清单.md
│   └── 09-依赖契约摘要.md
└── workflow-center/
    ├── 01-功能规格.md
    ├── 02-后端架构.md
    ├── 03-接口设计与报文.md
    ├── 04-对外API契约.md
    ├── 05-表结构DDL.md
    ├── 06-并发与事务策略.md
    ├── 07-审计要求.md
    ├── 08-初始化数据清单.md
    └── 09-依赖契约摘要.md
```

## 各模块文档索引

### common (3 份文档)
公共基础设施层，包含统一响应模型、错误码规范、鉴权链路等核心组件的详细设计。
详见 [common/CLAUDE.md](../../common/CLAUDE.md)

### auth-permission-center (8 份文档)
认证授权中心，涵盖用户认证、RBAC 权限控制、BizType 数据范围、组织架构等全部设计文档。
详见 [auth-permission-center/CLAUDE.md](../../auth-permission-center/CLAUDE.md)

### system-governance-center (9 份文档)
系统治理中心，提供字典管理、系统配置、工作日历、审计日志、通知、文件管理、定时任务等治理功能。
详见 [system-governance-center/CLAUDE.md](../../system-governance-center/CLAUDE.md)

### workflow-center (9 份文档)
工作流中心，嵌入 Flowable 7.0.1，提供流程启动、任务审批、SLA 超时管理、候选人解析等工作流能力。
详见 [workflow-center/CLAUDE.md](../../workflow-center/CLAUDE.md)

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

## 缺失文档的模块

以下模块仅有代码实现，尚未创建文档目录：
- customer-marketing-center
- portal-content-center
- business-application-center
- performance-engine-center
- report-analytics-center
