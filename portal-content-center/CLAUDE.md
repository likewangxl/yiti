# portal-content-center/ CLAUDE.md

本文件为 `portal-content-center` 模块提供上下文说明。

## 模块概述

**portal-content-center** 是门户与内容中心，负责工作台聚合、网址导航、通讯录、产品资料库、文档下载等通用门户能力。

**基础包名**: `com.bank.branch.platform.portal`

**Maven 坐标**: `com.bank.branch.platform:portal-content-center`

**对外契约**: 5 个 `*Api` 接口 + 2 个 `*Adapter` 接口 + 24 个 REST 端点。

**定位**: 通用域，只做只读聚合与内容管理，不持有核心业务状态。
> **当前进度**: V1 首版切片已落地。模块已包含 controller / service / mapper / api / facade / config / adapter / listener / test 基础设施，覆盖工作台聚合、快捷入口、导航、通讯录、产品资料库、文档管理等能力。

## 依赖关系

- **依赖**:
  - `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
  - `auth-permission-center` (CurrentUserApi, BizScopeApi, OrgApi)
  - `system-governance-center` (DictApi, ConfigApi, FileApi, NotifyApi, AuditApi)
  - `workflow-center` (WorkflowApi — 工作台待办查询)
- **不依赖**: `customer-marketing-center`, `business-application-center`, `performance-engine-center`, `report-analytics-center` (portal 只聚合，不依赖核心域)
- **被依赖**: 其他业务模块可通过 `*Api` 接口复用产品库、文档库、通讯录等能力

## 包结构

```
src/main/java/com/bank/branch/platform/portal/
├── api/              # 对外 API 接口 (5 个)
│   ├── ProductApi.java
│   ├── NavApi.java
│   ├── DocumentApi.java
│   ├── AddressBookApi.java
│   └── PortalApi.java
│   └── dto/          # API 层 DTO (12+ 类)
├── adapter/          # 跨模块适配器 (3 个)
│   ├── DataScopeAdapter.java       # 数据范围适配
│   ├── MetricAdapter.java          # 指标卡片适配 (→ MetricApi)
│   ├── WorkflowQueryAdapter.java   # 工作流待办适配 (→ WorkflowQueryApi)
│   └── dto/                        # 适配层 DTO
├── config/           # Spring 配置 (3 个)
│   ├── PortalAsyncConfig.java      # 异步线程池
│   ├── PortalCacheConfig.java      # 缓存 Key/TTL + 防雪崩抖动
│   └── PortalMyBatisConfig.java    # MyBatis 扫描
├── controller/       # REST 控制器 (8 个)
│   ├── ProductController.java      # 产品 CRUD + 导出 (7 端点)
│   ├── AdminNavController.java     # 导航管理 (4 端点)
│   ├── NavController.java          # 导航查询 (1 端点)
│   ├── AdminDocController.java     # 文档管理 (3 端点)
│   ├── DocController.java          # 文档查询 + 下载 (2 端点)
│   ├── AddressBookController.java  # 通讯录 (4 端点)
│   ├── ShortcutController.java     # 快捷方式 (2 端点)
│   └── WorkspaceController.java    # 工作台聚合 (1 端点)
│   └── dto/                        # Controller 层 DTO (按域分包)
├── convert/          # 手动 DTO 转换器 (6 个)
│   ├── ProductConverter, NavConverter, DocumentConverter
│   ├── EmployeeConverter, ShortcutConverter
│   └── MetricCardProjection
├── entity/           # 数据库实体 (5 个)
│   ├── ProductInfo.java
│   ├── PortalNav.java
│   ├── DocInfo.java
│   ├── AddrbookEmployee.java
│   └── PortalShortcut.java
├── enums/            # 错误码枚举
│   └── PortalErrorCode.java        # PORTAL-400xx / PORTAL-403xx / PORTAL-409xx / PORTAL-422xx / PORTAL-500xx
├── event/            # Spring 内部事件 (2 个)
│   ├── AddrbookUpdatedEvent.java
│   └── ProductResponsibleUpdatedEvent.java
├── facade/           # API 实现 (5 个 @Service)
│   ├── ProductFacade, NavFacade, DocumentFacade
│   ├── AddressBookFacade, PortalFacade
├── listener/         # 事件监听器 (1 个)
│   └── ProductResponsibleSyncListener.java  # 产品负责人变更同步
├── mapper/           # MyBatis Mapper (5 个接口 + XML)
│   ├── ProductInfoMapper, PortalNavMapper, DocInfoMapper
│   ├── AddrbookEmployeeMapper, PortalShortcutMapper
├── service/          # 业务逻辑 (8 个 Service)
│   ├── ProductService.java         # 产品 CRUD + 负责人管理
│   ├── ProductExportService.java   # 产品导出 (EasyExcel)
│   ├── NavService.java             # 导航 CRUD + 排序
│   ├── DocService.java             # 文档 CRUD + 下载
│   ├── AddressBookService.java     # 通讯录编辑
│   ├── AddrbookQueryService.java   # 通讯录查询 (分页/搜索/详情)
│   ├── ShortcutService.java        # 个人快捷方式管理
│   └── WorkspaceService.java       # 工作台数据聚合
│   └── dto/                        # Service 层命令/查询对象
└── typehandler/      # MyBatis 类型处理器
    └── JsonStringListTypeHandler.java  # JSON 字符串列表 ↔ List<String>
```

## 对外 API (5 个接口)

| 接口 | 实现 | 说明 |
|------|------|------|
| `ProductApi` | ProductFacade | 产品查询/详情/列表 |
| `NavApi` | NavFacade | 导航查询 |
| `DocumentApi` | DocumentFacade | 文档查询/下载 URL |
| `AddressBookApi` | AddressBookFacade | 员工信息查询 |
| `PortalApi` | PortalFacade | 工作台聚合数据 |

## 适配器接口 (2 个，供外部模块实现注入)

| 接口 | 默认实现 | 说明 |
|------|---------|------|
| `MetricApi` | MetricAdapter | 指标卡片数据 (工作台展示) |
| `WorkflowQueryApi` | WorkflowQueryAdapter | 待办任务列表 (工作台展示) |

## REST 端点

### ProductController (`/api/products`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/products` | 分页产品列表 |
| GET | `/api/products/support-available` | 支持中产品列表 |
| GET | `/api/products/export` | 产品导出 (EasyExcel) |
| GET | `/api/products/{id}` | 产品详情 |
| POST | `/api/products` | 创建产品 |
| PUT | `/api/products/{id}` | 更新产品 |
| DELETE | `/api/products/{id}` | 删除产品 |

### AdminNavController (`/api/admin/nav`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/admin/nav` | 创建导航 |
| PUT | `/api/admin/nav/{id}` | 更新导航 |
| DELETE | `/api/admin/nav/{id}` | 删除导航 |
| PUT | `/api/admin/nav/sort` | 批量排序 |

### NavController (`/api/nav`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/nav` | 分组查询导航列表 |

### AdminDocController (`/api/admin/documents`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/admin/documents` | 创建文档 |
| PUT | `/api/admin/documents/{id}` | 更新文档 |
| DELETE | `/api/admin/documents/{id}` | 删除文档 |

### DocController (`/api/documents`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/documents` | 分页文档列表 |
| GET | `/api/documents/{id}/download` | 获取下载 URL |

### AddressBookController (`/api/employees`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/employees` | 分页员工列表 |
| GET | `/api/employees/search` | 模糊搜索员工 |
| GET | `/api/employees/{empId}` | 员工详情 |
| PUT | `/api/employees/{empId}` | 更新员工信息 |

### ShortcutController (`/api/portal/shortcuts`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/portal/shortcuts` | 查询个人快捷方式 |
| PUT | `/api/portal/shortcuts` | 保存个人快捷方式 |

### WorkspaceController (`/api/portal`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/portal/workspace` | 工作台聚合 (指标卡片 + 待办 + 快捷方式 + 通知) |

## 数据库表 (5 张)

| 表 | 实体 | 说明 |
|----|------|------|
| `product_info` | ProductInfo | 产品信息 (productCode, productName, responsibleEmpIds, status) |
| `portal_nav` | PortalNav | 导航条目 (navName, navUrl, navCategory, sortOrder) |
| `doc_info` | DocInfo | 文档信息 (docTitle, fileObjectId, docCategory, publishStatus) |
| `addrbook_employee` | AddrbookEmployee | 通讯录员工 (empId, empName, orgCode, mobile, skillTags) |
| `portal_shortcut` | PortalShortcut | 个人快捷方式 (empId, navId, sortOrder) |

## 事件驱动

| 事件 | 发布者 | 监听者 | 触发条件 |
|------|--------|--------|---------|
| `ProductResponsibleUpdatedEvent` | ProductService | ProductResponsibleSyncListener | 产品负责人变更 |
| `AddrbookUpdatedEvent` | AddressBookService | (预留) | 通讯录更新 |

## 错误码

| 错误码 | 含义 |
|--------|------|
| `PORTAL-400xx` | 资源不存在 (01: 导航, 02: 员工, 03: 产品) |
| `PORTAL-403xx` | 权限不足 (01: 通讯录, 02: 产品, 04: 文档) |
| `PORTAL-404xx` | 文档域资源不存在 (01: 文档) |
| `PORTAL-409xx` | 冲突 (01: 产品代码重复, 02: 员工已离职, 03: 导航名重复, 04: 负责产品超限, 05: 产品仍被引用) |
| `PORTAL-422xx` | 参数校验 (00: 通用, 02: URL 格式, 03: 附件不存在, 07: 导出行数超限) |
| `PORTAL-500xx` | 内部错误 (02: 导出文件生成失败) |

## 缓存配置

Cache-Aside 模式，所有 Key 前缀 `portal:`，默认 TTL 5 分钟 + 10% 随机抖动防雪崩。

| Key | 内容 | TTL |
|-----|------|-----|
| `portal:product:support-available` | 支持中产品列表 | 5 分钟 |
| `portal:nav:active` | 启用导航列表 | 5 分钟 |

## 第三方依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `com.alibaba:easyexcel` | 3.3.4 (根 pom 管理) | 产品目录导出 |
| `testcontainers-mysql` | 1.19.7 (BOM 导入) | Mapper 集成测试 MySQL 容器 |
| `testcontainers-junit-jupiter` | 1.19.7 (BOM 导入) | Testcontainers JUnit 5 扩展 |
| `spring-security-test` | Spring Boot 管理 | 鉴权过滤器单元测试 |

## 测试

- **单元测试**: Mockito (Service + Adapter + Converter + Facade + Listener)
- **集成测试**: MockMvc + H2 (Controller 层), 基类 `AbstractControllerIntegrationTest`
- **Mapper 集成测试**: Testcontainers MySQL, 基类 `AbstractMapperIntegrationTest`
- **Mock 用户上下文**: `@WithMockEmpContext` 注解 + `MockEmpContextExtension`
- **测试文件数**: 38 个测试类, 80 个测试用例, 0 失败

## 跨模块依赖

| 依赖 | 来源模块 | 用途 |
|------|----------|------|
| `CurrentUserApi` | auth-permission-center | 获取当前用户工号/机构 |
| `BizScopeApi` | auth-permission-center | 数据范围校验 |
| `OrgApi` | auth-permission-center | 组织架构查询 |
| `DictApi` | system-governance-center | 字典值查询 |
| `FileApi` | system-governance-center | 文件存储/下载 |
| `AuditApi` | system-governance-center | 审计日志 |

## 开发参考

- 模块设计文档: `docs/modules/portal-content-center/` (9 份)
- 切片实现计划: `docs/superpowers/plans/` (portal V1 slice 计划 r3)
- 共享开发规范: `docs/common-dev-guide.md`
- 错误码前缀: `PORTAL-{HTTP_STATUS}{SEQ}` (例如 PORTAL-40003, PORTAL-40905), 模块前缀注册见 `docs/common-dev-guide.md` §2

## 当前实现说明

- 已实现 REST 端点见 `src/main/java/com/bank/branch/platform/portal/controller/`
- 已实现对外 `*Api` 见 `src/main/java/com/bank/branch/platform/portal/api/`
- 已实现数据库实体/Mapper 见 `src/main/java/com/bank/branch/platform/portal/entity/` 与 `src/main/resources/mapper/portal/`
- 当前对 workflow 待读查询仍使用本地 `WorkflowQueryAdapter` 降级
- 当前产品导出为 **V1 同步导出**（`<=5000` 行），超过阈值直接拒绝，不做异步导出
