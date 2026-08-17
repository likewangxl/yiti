# common/AGENTS.md

本文件补充根 [AGENTS.md](../AGENTS.md)，适用于 `common` 聚合工程及其子模块。

## 定位与结构

`common` 提供平台公共基础设施，通过各子模块的 `AutoConfiguration.imports` 自动装配。子模块及 Maven 依赖以 `common/pom.xml` 和各子模块 `pom.xml` 为准：

- `common-web`：统一 Web 模型、异常、分页、JDBC 锁、边车客户端。
- `common-trace`：traceId 传播和 MDC。
- `common-security`：`@BizAuth` 元数据、数据范围上下文、脱敏和签名。
- `common-aop`：API 日志、方法计时、审计切面和审计 SPI。
- `common-db`：Druid、MyBatis/MyBatis-Plus 配置及数据库拦截器。

新增公共能力前先确认归属；不要在 common 放置具体业务规则，也不要在业务模块复制 common 已提供的基础设施。

## 公共规则

- 通过自动配置注入公开 Bean，不要绕过装配流程直接 `new` common 内部实现。
- 新增或调整自动配置时同步维护对应 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，并用最小 Spring 上下文测试验证装配与禁用条件。
- 类、Bean 和方法的完整清单以源码为准；不在本文件维护数量。规范范例统一登记到 `docs/code-examples.md`。

## common-web

- 常规 JSON API 使用 `ResponseWrapper<T>` 和统一异常映射；文件流、重定向等特殊响应遵循其明确契约，不要强套 JSON 信封。
- 分页统一使用 `PageRequest`/`PageResult`，不得绕过统一的页码、页大小和排序字段校验。
- 跨实例业务互斥使用 `LockManager`/`JdbcLockManager` 和 `PT_LOCK`，释放锁必须校验 holder；不要另引 Redis 锁。Quartz 作业的集群互斥遵循 governance 的 Quartz 规则。
- 银行 ESF 边车 HTTP/SOAP 调用复用 `SidecarHttpClient`，不要在业务模块另起同类客户端；流水号、超时和协议配置以 sidecar 源码及运行配置为准。

## common-trace

- `TraceIdFilter` 从请求头读取或生成 traceId 并写入 MDC，`TraceIdInterceptor` 回写响应头。不要在 Filter 之前依赖 MDC，也不要自行改变 traceId 生成和传播协议。
- 日志通过 `%X{traceId}` 取值；异步或跨线程执行必须显式传播并最终清理上下文。

## common-security

- `@BizAuth` 只描述业务类型和动作，真正的认证、资源 RBAC 与数据范围组装由 auth 模块完成；不要把注解本身当作完整授权。
- `DataScopeContext` 是 ThreadLocal 请求上下文。每个入口和异步任务都必须保证清理；`DataScopeCleanupFilter` 只是 Web 请求兜底。
- `BizType`、`BizAction`、`DataScopeType` 的合法值以枚举源码为准，不在文档复制枚举清单。扩展枚举前先确认不能复用现有语义及所有消费方。
- `CurrentUserContext` 需要适配 Spring Session JDBC 序列化；字段变化必须考虑已有 Session 的反序列化兼容性。源码中残留的 Redis 表述不是当前运行架构。
- 新业务对象只有确需通用对象元数据时才注册到 `ObjectMetaRegistry`；敏感数据统一使用 `SensitiveDataMasker`，签名统一使用 `SignatureUtils`。

## common-aop

- `ApiLogAspect` 和 `MethodTimingAspect` 是统一日志/计时入口；阈值从 `platform.method-timing-*` 配置读取，不在业务模块创建平行切面。
- `AuditLogAspect` 在方法成功返回后调用 `AuditLogHandler`；它不会天然记录失败操作。要求失败也审计的高危场景必须设计独立审计链路。
- `AuditLogHandler` 是 SPI。默认 `NoopAuditLogHandler` 只兜底，治理模块提供持久化实现；新增实现要处理 Bean 唯一性，不要直接改切面绑定具体业务模块。

## common-db

- `MybatisPlusConfig` 是平台 MyBatis-Plus 公共配置。新增通用插件在 `common-db` 注册，不要在各业务模块重复创建拦截器链。
- `PageInterceptor`、`AuditFieldFiller`、`SlowSqlInterceptor` 继续服务遗留 MyBatis/XML；MyBatis-Plus 分页与 BaseMapper 服务新增代码。两套机制并存，不做无关迁移。
- 排序字段必须经过白名单/格式校验；审计字段沿用统一命名并从安全上下文取操作者；慢 SQL 阈值以 `platform.slow-sql-threshold-ms` 配置为准。
- 实体映射和审计填充必须匹配真实 schema，不能仅凭驼峰约定推断字段。

## 验证

修改 common 时至少运行受影响子模块测试；跨模块装配行为还要从真实消费模块或 bootstrap 验证。跨模块测试前遵循根文件的 stale jar 处理流程。
