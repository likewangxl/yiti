# portal-content-center/AGENTS.md

本文件为 `portal-content-center` 模块提供上下文说明。

## 模块概述

**portal-content-center** 是门户与内容中心，负责工作台聚合、网址导航、通讯录、产品资料库、文档下载等通用门户能力。

**基础包名**: `com.bank.branch.platform.portal`

**Maven 坐标**: `com.bank.branch.platform:portal-content-center`

**定位**: 通用域，只做只读聚合，不持有业务状态。

> **当前进度**: V1 首版切片已落地。模块已包含 controller / service / mapper / api / facade / config / adapter / listener / test 基础设施，覆盖工作台聚合、快捷入口、导航、通讯录、产品资料库、文档管理等能力。

## 依赖关系

- **依赖**:
  - `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
  - `auth-permission-center` (CurrentUserApi, BizScopeApi, OrgApi)
  - `system-governance-center` (DictApi, ConfigApi, FileApi, NotifyApi, AuditApi)
  - `workflow-center` (WorkflowApi + WorkflowQueryApi — 工作台待办/流程映射只读能力)
- **不依赖**: `customer-marketing-center`, `business-application-center`, `performance-engine-center`, `report-analytics-center` (portal 只聚合，不依赖核心域)
- **被依赖**: `customer-marketing-center`, `business-application-center` 等业务模块 (通过 `*Api` 接口复用产品库、文档库等能力)

## 包结构（当前已落地）

```
src/main/java/com/bank/branch/platform/portal/
├── api/              # 对外 API 接口
│   └── dto/          # 请求/响应 DTO
├── controller/       # REST 控制器
├── facade/           # API 实现
├── service/          # 业务逻辑
├── mapper/           # MyBatis Mapper
├── entity/           # 数据库实体
├── enums/            # 错误码枚举 (PortalErrorCode, PORTAL-{HTTP_STATUS}{SEQ})
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
- 错误码前缀: `PORTAL-{HTTP_STATUS}{SEQ}` (例如 PORTAL-40003, PORTAL-40905), 模块前缀注册见 `docs/common-dev-guide.md` §2

## 当前实现说明

- 已实现 REST 端点见 `src/main/java/com/bank/branch/platform/portal/controller/`
- 已实现对外 `*Api` 见 `src/main/java/com/bank/branch/platform/portal/api/`
- 已实现数据库实体/Mapper 见 `src/main/java/com/bank/branch/platform/portal/entity/` 与 `src/main/resources/mapper/portal/`
- 当前对 workflow 待读查询通过正式 `WorkflowQueryApi` + 本地 `WorkflowQueryAdapter` 做降级封装
- 当前产品导出为 **V1 同步导出**（`<=5000` 行），超过阈值直接拒绝，不做异步导出
