# common/ CLAUDE.md

本文件为 `common` 模块（含 5 个子模块）提供上下文说明，是本模块开发指导的唯一权威来源。

## 模块概述

**common** 是公共基础设施层，为所有业务模块提供统一的基础组件。模块采用 Maven 多模块结构，包含 `common-web`/`common-trace`/`common-security`/`common-aop`/`common-db` 5 个子模块，通过 Spring Boot 3 自动配置机制（`AutoConfiguration.imports`）实现零配置接入。

**基础包名**: `com.bank.branch.platform.common`

### 依赖链路

```
common-web (基础)
  ├── common-security (依赖 common-web)
  │     ├── common-aop    (依赖 common-security + common-trace)
  │     └── common-db     (依赖 common-security)
  └── common-trace    (仅依赖 spring-boot-starter-web)
```

## 架构规则与红线

- 使用 common 模块时，**只**通过自动配置提供的 Bean 进行交互，不要绕过自动配置直接 `new` 内部类。
- `AuditLogHandler`（`common-aop` 定义）是 SPI 接口，如需自定义审计逻辑应实现此接口并注册为 `@Primary` Bean 覆盖 `NoopAuditLogHandler`，不要修改 `common-aop` 内部实现。
- 新的 MyBatis 拦截器需在 `common-db` 中添加并在对应 AutoConfiguration 中注册，不要在业务模块里另起拦截器。

## common-web（`com.bank.branch.platform.common.web`）

统一响应模型、全局异常处理、分页参数标准、分布式锁、边车 HTTP 客户端。是整个平台 API 返回格式的唯一标准来源，所有业务模块必须依赖此模块。

- `ResponseWrapper<T>` 是所有 API 返回的唯一标准信封（code/message/traceId/data/page/timestamp），不要绕过它直接返回裸对象；新增异常类型需在 `GlobalExceptionHandler` 中注册映射。
- `PageRequest`/`PageResult` 是分页入参/出参标准（pageSize 默认 20，上限 100）。
- `LockManager`/`JdbcLockManager`：分布式锁基于 `PT_LOCK` 表 + `SELECT FOR UPDATE`（去 Redis 后自建），`tryLock`/`unlock` 均 `REQUIRES_NEW` 独立事务；释放锁时按 `HOLDER` 做 CAS 校验（`DELETE FROM PT_LOCK WHERE LOCK_KEY = ? AND HOLDER = ?`），防止误删他人持有的锁。需要跨实例互斥的场景（如定时任务防重）优先注入 `LockManager`，不要再引入 Redis 分布式锁。
- `SidecarHttpClient`：边车（银行 ESF 网关）HTTP 客户端，基于 JDK 17 `HttpClient`，支持 JSON（11002）/SOAP（11003）两种调用协议，自动拼流水号。跨系统/边车调用统一走这个客户端，不要在业务模块里另起 `HttpClient`。

## common-trace（`com.bank.branch.platform.common.trace`）

全链路 traceId 传播与 MDC 注入：`TraceIdFilter`（Servlet Filter）读取 `X-Trace-Id` 请求头或生成随机 traceId 并注入 MDC，`TraceIdInterceptor`（MVC Interceptor）将 traceId 写回响应头。

- 不要修改 traceId 生成算法；不要在 `TraceIdFilter` 之前读取 MDC（此时 traceId 尚未注入）。
- Logback 日志模式中用 `%X{traceId}` 引用 MDC 变量。

## common-security（`com.bank.branch.platform.common.security`）

数据权限范围管理、`@BizAuth` 注解模型、数据脱敏、API 签名校验。是平台安全体系的基础组件。

- `@BizAuth` 方法级鉴权注解（`bizType` + 可选 `action`）；`DataScopeContext` 是数据权限范围 ThreadLocal 上下文（bizType/action/scope/empId/orgCode/orgSubtreeCodes）。**ThreadLocal 清理**：`SecurityAutoConfiguration` 注册最低优先级的 `DataScopeCleanupFilter`，在每个请求结束时调用 `DataScopeContext.clear()`，防止内存泄漏。
- `BizType`/`BizAction`/`DataScopeType` 均为枚举，取值以枚举源码为准，不在文档里维护数量或逐条列表（历史上文档反复写死"18 种"，源码早已扩展含 `RED_ENGINE`/`EVAL`/`WORKFLOW_MONITOR` 等新增值，导致文档与代码脱节）。扩展新 `BizType` 前先评估是否真的需要独立业务类型，优先复用现有枚举。
- `ObjectMeta`/`ObjectMetaRegistry`：业务对象元数据注册表（`ConcurrentHashMap`，线程安全），新增业务表时需在此注册 `ObjectMeta`。
- `CurrentUserContext` 是 Java record，实现 `Serializable` 供 HttpSession 序列化持久化。**注意**：类内 Javadoc 注释仍写着"支持 Spring Session Redis 存储"，这是历史遗留表述，**当前 session 已改为 Spring Session JDBC，落 MySQL `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` 表（2026-05 去 Redis）**，并非存 Redis；新增字段仍需注意序列化兼容性（旧 session 反序列化为新 record 结构可能失败），不要以为去 Redis 后这个顾虑就消失了。
- `SensitiveDataMasker`：手机号/身份证/银行账号/金额脱敏工具；`SignatureUtils`：HmacSHA256 API 签名/验签工具。

## common-aop（`com.bank.branch.platform.common.aop`）

API 日志、方法计时、审计日志切面，通过 Spring AOP 为所有模块提供统一横切关注点。

- `ApiLogAspect` 环绕通知所有 `@RestController`，记录入参/出参和耗时；`MethodTimingAspect` 环绕通知所有 `@Service`，双阈值监控（默认 WARN 500ms / ERROR 5000ms，可通过 `platform.method-timing-warn-ms`/`platform.method-timing-error-ms` 配置）。
- `AuditLogAspect` 在 `@AuditLog` 注解方法**返回后**（非环绕前后）执行并发布 `AuditLogEvent`，异常时不记录审计日志——需要"失败也审计"的场景不能只靠这个切面。
- `AuditLogHandler` 是 **SPI 扩展点**：`common-aop` 提供默认实现 `NoopAuditLogHandler`（仅输出 INFO 日志），`system-governance-center` 的 `GovAuditLogHandler` 以 `@Primary` Bean 覆盖之，写入 `audit_log` 表。如需自定义审计持久化，实现该接口并注册为 `@Primary` Bean。

## common-db（`com.bank.branch.platform.common.db`）

MyBatis 拦截器集合（分页拦截、审计字段自动填充、慢 SQL 检测）+ Druid 连接池配置 + MyBatis-Plus 核心配置。

- `PageInterceptor` 校验 `sortField` 防 SQL 注入（正则 `^[a-zA-Z0-9_]+$`），非法时降级为 `created_time`；依赖 `PageRequest` 参数出现在 Mapper 方法签名中。
- `AuditFieldFiller` 自动填充 `created_by`/`created_time`/`updated_by`/`updated_time`（从 `DataScopeContext` 获取 empId），字段名是约定命名，新实体必须使用相同命名。
- `SlowSqlInterceptor` 慢 SQL 检测，默认阈值 5000ms，可通过 `platform.slow-sql-threshold-ms` 配置。
- `MybatisPlusConfig`：MyBatis-Plus 核心配置（`MybatisPlusInterceptor` + 分页插件），与老三个拦截器（`PageInterceptor`/`AuditFieldFiller`/`SlowSqlInterceptor`）正交并存——老三个继续对遗留 MyBatis XML 生效，**新增功能的数据库访问统一走 MyBatis-Plus**（`BaseMapper` + 本配置的分页插件），两套机制并存不冲突，不要为了"统一"去改造遗留 XML。

## 自动配置

所有子模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 实现自动激活（`WebAutoConfiguration`/`TraceAutoConfiguration`/`SecurityAutoConfiguration`/`AopAutoConfiguration`/`DbAutoConfiguration`），具体注册的 Bean 清单以各子模块 `config/*AutoConfiguration.java` 源码为准。

## 清单与契约指引

- 各子模块类清单、方法签名以对应 `src/main/java/com/bank/branch/platform/common/<子模块>/` 源码目录为准，不在本文件维护数量或逐条列表。
- 示例代码统一登记在 `docs/code-examples.md`，本文件不复制代码片段。

## 测试指引

- 5 个子模块均为纯单元测试（无 Spring 容器依赖，枚举/工具类/上下文对象各自独立测试），随 `mvn test` 由 surefire 执行，无需额外 profile 或数据库。
- 跨模块 `@SpringBootTest`（bootstrap 或业务模块层）依赖本模块最新类时，按根 CLAUDE.md 的 stale jar 处理流程先 `mvn clean install -DskipTests`。
