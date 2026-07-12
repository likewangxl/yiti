<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# common-web

## Purpose
统一响应模型、全局异常处理、分页参数标准、手动校验工具。是整个平台 API 返回格式的唯一标准来源。
所有业务模块必须依赖此模块。

**基础包名**: `com.bank.branch.platform.common.web`
**自动配置**: `WebAutoConfiguration`（通过 `AutoConfiguration.imports` 激活）

## Key Files

| File | Description |
|------|-------------|
| `ResponseWrapper.java` | 标准 API 响应信封 (code, message, traceId, data, page, timestamp)，提供 `success()`/`page()`/`error()` 工厂方法 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` 统一异常映射 (401/403/400/405/415/500) |
| `BizException.java` | 基础业务异常 (code + message)，继承 RuntimeException |
| `AuthException.java` | 认证异常 (code: AUTH_001, HTTP: 401) |
| `PermissionDeniedException.java` | 权限拒绝异常 (code: PERM_001, HTTP: 403) |
| `PageRequest.java` | 分页入参 (pageNo, pageSize, sortBy, sortDir)，默认 pageSize=20 |
| `PageResult.java` | 分页出参 (pageNo, pageSize, total, records) |
| `RequestValidator.java` | JSR-303 手动校验工具（用于非 Controller 层参数校验） |
| `CorsConfig.java` | CORS 配置（仅 dev profile 生效） |
| `config/WebAutoConfiguration.java` | 自动配置入口（Import GlobalExceptionHandler + CorsConfig） |
| `lock/LockManager.java` | 分布式锁接口（`tryLock`/`unlock`，去 Redis 后基于 DB 实现） |
| `lock/JdbcLockManager.java` | `PT_LOCK` 表 + `SELECT FOR UPDATE` 的锁实现，`tryLock`/`unlock` 均 `REQUIRES_NEW` 独立事务，CAS 释放防误删 |
| `lock/LockAutoConfig.java` | 自动配置（注册 `LockManager` Bean + `LockCleanupJob` 定时清理过期锁，`fixedDelay=5min`） |
| `sidecar/SidecarHttpClient.java` | 边车（银行 ESF 网关）HTTP 客户端，基于 JDK17 `HttpClient`，自动拼流水号、支持 JSON(11002)/SOAP(11003) 两种调用 |
| `sidecar/SidecarProperties.java` | 边车配置（`platform.sidecar.*`：baseUrl / healthBaseUrl / 超时 / logRequest） |
| `sidecar/SidecarAutoConfig.java` | 自动配置（注册 `FlowIdGenerator` + `SidecarHttpClient`） |
| `sidecar/soap/SoapEnvelopeBuilder.java` | SOAP Envelope 拼装工具（配合边车 SOAP 调用） |

## For AI Agents

### Working In This Directory
- `ResponseWrapper` 是所有 API 返回的唯一标准，不要绕过它直接返回裸对象
- 新增异常类型需在 `GlobalExceptionHandler` 中注册映射
- 分页参数遵守 `PageRequest` 标准（pageSize 上限 100）
- 需要跨实例互斥的场景（如定时任务防重）优先注入 `LockManager`，不要再引入 Redis 分布式锁
- 跨系统/边车调用统一走 `SidecarHttpClient`，不要在业务模块里另起 `HttpClient`

## Dependencies

### External
- `spring-boot-starter-web` — Spring MVC
- `jakarta.validation-api` — JSR-303 Bean Validation
- `hibernate-validator` — JSR-303 实现
- `jackson-databind` — JSON 序列化
- `spring-boot-starter-jdbc` — `JdbcLockManager`（`PT_LOCK` 表 SQL）

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
