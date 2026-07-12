<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# common-trace

## Purpose
全链路 traceId 传播与 MDC 注入。通过 Servlet Filter 和 MVC Interceptor 自动为每个请求生成/传递 traceId，并通过 Logback MDC 自动注入所有日志输出。

**基础包名**: `com.bank.branch.platform.common.trace`
**自动配置**: `TraceAutoConfiguration`（通过 `AutoConfiguration.imports` 激活）

## Key Files

| File | Description |
|------|-------------|
| `TraceContext.java` | Java record 持有 traceId / source / startTime |
| `MdcUtils.java` | SLF4J MDC 操作工具 (putTraceId / getTraceId / removeTraceId)，MDC key 为 `"traceId"` |
| `TraceIdFilter.java` | Servlet Filter (order=HIGHEST_PRECEDENCE+10)，读取 `X-Trace-Id` 请求头或生成 16 位随机 UUID，注入 MDC |
| `TraceIdInterceptor.java` | MVC Interceptor，将 traceId 写回响应头 `X-Trace-Id` |
| `config/TraceAutoConfiguration.java` | 自动配置（注册 Filter + Interceptor） |

## For AI Agents

### Working In This Directory
- 不要修改 traceId 生成算法（16 位随机 UUID，格式为 `TR_{random}`）
- 不要在 Filter 之前读取 MDC（traceId 尚未注入）
- Logback 模式中使用 `%X{traceId}` 引用 MDC 变量

## Dependencies

### External
- `spring-boot-starter-web` — Servlet Filter + MVC Interceptor
- `slf4j-api` — MDC
- `logback-classic` — 日志输出

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
