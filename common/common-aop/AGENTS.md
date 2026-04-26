<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-04-26 -->

# common-aop

## Purpose
API 日志 AOP、方法计时监控、审计日志切面。通过 Spring AOP 切面为所有模块提供统一的横切关注点。

**基础包名**: `com.bank.branch.platform.common.aop`
**自动配置**: `AopAutoConfiguration`（通过 `AutoConfiguration.imports` 激活）

## Key Files

| File | Description |
|------|-------------|
| `ApiLogAspect.java` | AOP 环绕通知所有 `@RestController`，记录入参/出参和耗时（含 traceId） |
| `MethodTimingAspect.java` | AOP 环绕通知所有 `@Service`，双阈值监控（WARN: 500ms, ERROR: 5000ms），可通过 `platform.method-timing-*` 配置 |
| `AuditLogAspect.java` | AOP 环绕通知 `@AuditLog` 注解的方法，发布 `AuditLogEvent` 事件 |
| `annotation/AuditLog.java` | 方法级审计注解 (action, resourceType, reasonRequired) |
| `event/AuditLogEvent.java` | Java record 审计事件 (action, resourceType, resourceId, businessKey, operatorEmpId, reason 等) |
| `handler/AuditLogHandler.java` | **SPI 接口**，由 `system-governance-center` 的 `GovAuditLogHandler` 实现 |
| `handler/NoopAuditLogHandler.java` | 默认实现（仅输出 INFO 日志），可被其他模块覆盖 |
| `config/AopAutoConfiguration.java` | 自动配置（注册 3 个 Aspect + `@ConditionalOnMissingBean` 注册 NoopAuditLogHandler） |

## For AI Agents

### Working In This Directory
- `AuditLogHandler` 是 SPI 扩展点——如需自定义审计持久化，实现此接口并注册为 `@Primary` Bean
- `AuditLogAspect` 在方法返回后（非环绕前后）执行，异常时不记录
- `MethodTimingAspect` 的阈值可通过 `application.yml` 的 `platform.method-timing-warn-ms` / `platform.method-timing-error-ms` 配置

## Dependencies

### Internal
- `common-trace`（MdcUtils）
- `common-security`（DataScopeContext）

### External
- `spring-boot-starter-aop` — Spring AOP
- `spring-context` — ApplicationEvent 发布

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
