# portal-content-center/ CLAUDE.md

本文件为 `portal-content-center` 模块提供上下文说明。

## 模块概述

**portal-content-center** 是门户与内容中心，负责工作台聚合、网址导航、通讯录、产品资料库、文档下载等通用门户能力。

**基础包名**: `com.bank.branch.platform.portal`

**Maven 坐标**: `com.bank.branch.platform:portal-content-center`

**定位**: 通用域，只做只读聚合，不持有业务状态。

> **当前进度**: Phase 0 骨架阶段。本模块只有 Maven 工程结构，没有 Java 源代码。后续任务 (Task 0.2 ~ Task 0.N) 会陆续填充包结构、错误码枚举、配置类、测试基础设施等。

## 依赖关系

- **依赖**:
  - `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
  - `auth-permission-center` (CurrentUserApi, BizScopeApi, OrgApi)
  - `system-governance-center` (DictApi, ConfigApi, FileApi, NotifyApi, AuditApi)
  - `workflow-center` (WorkflowApi — 工作台待办查询)
- **不依赖**: `customer-marketing-center`, `business-application-center`, `performance-engine-center`, `report-analytics-center` (portal 只聚合，不依赖核心域)
- **被依赖**: `customer-marketing-center`, `business-application-center` 等业务模块 (通过 `*Api` 接口复用产品库、文档库等能力)

## 包结构 (Phase 0 完成后目标结构)

```
src/main/java/com/bank/branch/platform/portal/
├── api/              # 对外 API 接口 (Phase 1+ 填充)
│   └── dto/          # 请求/响应 DTO
├── controller/       # REST 控制器
├── facade/           # API 实现
├── service/          # 业务逻辑
├── mapper/           # MyBatis Mapper
├── entity/           # 数据库实体
├── enums/            # 错误码枚举 (PortalErrorCode, PORTAL-xxxyy)
├── config/           # Spring 配置 (PortalCacheConfig 等)
└── exception/        # 模块异常
```

## 第三方依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `com.alibaba:easyexcel` | 3.3.4 (根 pom 管理) | 产品目录、通讯录导出 |
| `testcontainers-mysql` | 1.19.7 (BOM 导入) | 集成测试 MySQL 容器 |
| `testcontainers-junit-jupiter` | 1.19.7 (BOM 导入) | Testcontainers JUnit 5 扩展 |
| `spring-security-test` | Spring Boot 管理 | 鉴权过滤器单元测试 |

## 开发参考

- 模块设计文档: `docs/modules/portal-content-center/` (9 份)
- 切片实现计划: `docs/superpowers/plans/` (portal V1 slice 计划 r3)
- 共享开发规范: `docs/common-dev-guide.md`
- 错误码前缀: `PORTAL-xxxyy`, 模块前缀注册见 `docs/common-dev-guide.md` §2

## Phase 0 说明

本文件目前只是最小化骨架说明。在 Phase 0 全部任务完成后，将补充：
- 对外 API 接口清单和方法签名
- REST 端点映射表
- 数据库表清单
- 缓存策略
- 审计要求对齐说明
