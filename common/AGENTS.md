<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# common/AGENTS.md

本文件为 `common` 模块提供上下文说明。

## 模块概述

**common** 是公共基础设施层，为所有业务模块提供统一的基础组件。模块采用 Maven 多模块结构，包含 5 个子模块，通过 Spring Boot 3 自动配置机制 (`AutoConfiguration.imports`) 实现零配置接入。

**基础包名**: `com.bank.branch.platform.common`

## 子模块结构

```
common/
├── pom.xml                    # 父 POM, 5 个子模块
├── common-web/                # 统一响应、异常处理、分页、校验
├── common-trace/              # 链路追踪 (traceId + MDC)
├── common-security/           # 数据权限范围、鉴权注解、数据脱敏
├── common-aop/                # API 日志、方法计时、审计日志
└── common-db/                 # MyBatis 分页、审计字段自动填充、慢 SQL 检测
```

### 依赖链路

```
common-web (基础)
  ├── common-security (依赖 common-web)
  │     ├── common-aop    (依赖 common-security + common-trace)
  │     └── common-db     (依赖 common-security)
  └── common-trace    (仅依赖 spring-boot-starter-web)
```

### common-web (`com.bank.branch.platform.common.web`)

统一响应模型、全局异常处理、分页支持。

| 类 | 用途 |
|----|------|
| `ResponseWrapper<T>` | 标准 API 响应信封 (code, message, traceId, data, page, timestamp) |
| `GlobalExceptionHandler` | 统一异常映射 (401/403/400/405/415/500) |
| `BizException` | 基础业务异常 (code + message) |
| `AuthException` | 认证异常 (code: AUTH_001, HTTP: 401) |
| `PermissionDeniedException` | 权限拒绝异常 (code: PERM_001, HTTP: 403) |
| `PageRequest` | 分页入参 (pageNo, pageSize, sortBy, sortDir) |
| `PageResult<T>` | 分页出参 (pageNo, pageSize, total, records) |
| `RequestValidator` | 手动 JSR-303 校验工具 |
| `LockManager` / `JdbcLockManager` | 分布式锁 (`PT_LOCK` 表 + `SELECT FOR UPDATE`，去 Redis 后自建，CAS 释放防误删) |
| `SidecarHttpClient` | 边车 (银行 ESF 网关) HTTP 客户端，支持 JSON(11002)/SOAP(11003) 调用，自动拼流水号 |

### common-trace (`com.bank.branch.platform.common.trace`)

全链路 traceId 传播与 MDC 注入。

| 类 | 用途 |
|----|------|
| `TraceContext` | traceId 持有 (traceId, source, startTime) |
| `MdcUtils` | SLF4J MDC 操作工具 |
| `TraceIdFilter` | Servlet Filter, 读取 `X-Trace-Id` 头或生成 16 位随机 traceId, 注入 MDC |
| `TraceIdInterceptor` | MVC Interceptor, 将 traceId 写回响应头 `X-Trace-Id` |

### common-security (`com.bank.branch.platform.common.security`)

数据权限范围管理、`@BizAuth` 注解、数据脱敏。

| 类/枚举 | 用途 |
|---------|------|
| `@BizAuth` | 方法级鉴权注解, 要求 `bizType` 和可选 `action` |
| `CurrentUserContext` | 当前用户上下文 (empId, mainOrgCode, roleIds, roleCodes, candidateGroupKeys, systemAdmin) |
| `DataScopeContext` | 数据权限范围 ThreadLocal 上下文 (bizType, action, scope, empId, orgCode, orgSubtreeCodes) |
| `BizType` (枚举) | 18 种业务类型常量 |
| `BizAction` (枚举) | 15 种操作类型常量 (READ, WRITE, DELETE, EXPORT, IMPORT 等) |
| `DataScopeType` (枚举) | 7 种数据范围级别: SELF_CREATED, SELF, SELF_ASSIGNED, ORG, ORG_SUBTREE, ALL, WORKFLOW_PARTICIPANT |
| `ObjectMeta` / `ObjectMetaRegistry` | 业务对象元数据注册表 |
| `SensitiveDataMasker` | 数据脱敏工具 (手机号、身份证、银行账号、金额) |
| `SignatureUtils` | HmacSHA256 API 签名/验签工具 |

> **ThreadLocal 清理**: `SecurityAutoConfiguration` 注册最低优先级的 `DataScopeCleanupFilter`，在每个请求结束时调用 `DataScopeContext.clear()`，防止内存泄漏。

### common-aop (`com.bank.branch.platform.common.aop`)

API 日志、方法计时、审计日志切面。

| 类 | 用途 |
|----|------|
| `ApiLogAspect` | AOP 环绕通知所有 `@RestController`, 记录入参/出参和耗时 |
| `MethodTimingAspect` | AOP 环绕通知所有 `@Service`, 双阈值监控 (WARN: 500ms, ERROR: 5000ms) |
| `AuditLogAspect` | AOP 环绕通知 `@AuditLog` 注解方法, 发布 `AuditLogEvent` 事件 |
| `AuditLog` | 方法级审计注解 (action, resourceType, reasonRequired) |
| `AuditLogEvent` | 审计事件记录 (action, resourceType, resourceId, businessKey, operator 等) |
| `AuditLogHandler` | **SPI 接口**, 由 `system-governance-center` 实现 |
| `NoopAuditLogHandler` | 默认/独立模式实现 (仅输出 INFO 日志), 可被覆盖 |

### common-db (`com.bank.branch.platform.common.db`)

MyBatis 拦截器、审计字段自动填充、Druid 配置。

| 类 | 用途 |
|----|------|
| `PageInterceptor` | MyBatis 分页拦截器, 校验 sortField 防 SQL 注入 (正则 `^[a-zA-Z0-9_]+$`) |
| `AuditFieldFiller` | MyBatis 拦截器, 自动填充 created_by, created_time, updated_by, updated_time |
| `SlowSqlInterceptor` | MyBatis 拦截器, 慢 SQL 检测 (默认 5000ms, 可通过 `platform.slow-sql-threshold-ms` 配置) |
| `DruidConfig` | 标记配置类, 实际 Druid 配置来自 `spring.datasource.druid.*` |
| `MybatisPlusConfig` | MyBatis-Plus 核心配置 (`MybatisPlusInterceptor` + 分页插件)，与老三个拦截器正交并存，新增功能统一走 MyBatis-Plus `BaseMapper` |

## 自动配置

所有子模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 实现自动激活:
- `common-web`: WebAutoConfiguration (GlobalExceptionHandler, CorsConfig)
- `common-trace`: TraceAutoConfiguration (TraceIdFilter, TraceIdInterceptor)
- `common-security`: SecurityAutoConfiguration (ObjectMetaRegistry, DataScopeCleanupFilter)
- `common-aop`: AopAutoConfiguration (ApiLogAspect, MethodTimingAspect, AuditLogAspect, NoopAuditLogHandler)
- `common-db`: DbAutoConfiguration (PageInterceptor, AuditFieldFiller, SlowSqlInterceptor)

## 开发规范

- 使用 common 模块时，**只**通过自动配置提供的 Bean 进行交互
- `AuditLogHandler` 是 SPI 接口，如需自定义审计逻辑应实现此接口并注册为 Bean 覆盖 `NoopAuditLogHandler`
- 新的 MyBatis 拦截器需在 `common-db` 中添加并在对应 AutoConfiguration 中注册
