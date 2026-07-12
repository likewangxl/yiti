<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# common-db

## Purpose
MyBatis 拦截器集合：分页拦截、审计字段自动填充、慢 SQL 检测。Druid 连接池配置。

**基础包名**: `com.bank.branch.platform.common.db`
**自动配置**: `DbAutoConfiguration`（通过 `AutoConfiguration.imports` 激活）

## Key Files

| File | Description |
|------|-------------|
| `PageInterceptor.java` | MyBatis 分页拦截器，校验 sortField 防 SQL 注入（正则 `^[a-zA-Z0-9_]+$`），非法时降级为 `created_time` |
| `AuditFieldFiller.java` | MyBatis INSERT/UPDATE 拦截器，自动填充 created_by / created_time / updated_by / updated_time（从 DataScopeContext 获取 empId） |
| `SlowSqlInterceptor.java` | MyBatis SQL 执行耗时监控，超过阈值（默认 5000ms）输出 WARN 日志含原始 SQL |
| `DruidConfig.java` | `@Configuration` 标记类，实际配置通过 `spring.datasource.druid.*` 由 druid-spring-boot-3-starter 接管 |
| `config/DbAutoConfiguration.java` | 自动配置（注册 PageInterceptor + AuditFieldFiller + SlowSqlInterceptor，Import DruidConfig） |
| `config/MybatisPlusConfig.java` | MyBatis-Plus 核心配置，注册 `MybatisPlusInterceptor`（含 `PaginationInnerInterceptor`，MySQL 方言，maxLimit=100）；与老三个拦截器正交并存 |

## For AI Agents

### Working In This Directory
- 新的 MyBatis 拦截器需在此模块中添加，并在 `DbAutoConfiguration` 注册
- `PageInterceptor` 依赖 `PageRequest` 参数出现在 Mapper 方法签名中
- `AuditFieldFiller` 的字段名是约定的（`createdBy`, `createdTime`, `updatedBy`, `updatedTime`），新实体必须使用相同命名
- 慢 SQL 阈值可通过 `platform.slow-sql-threshold-ms` 配置
- 新增功能的数据库访问统一走 MyBatis-Plus（`BaseMapper` + `MybatisPlusConfig` 分页插件），老三个拦截器（PageInterceptor/AuditFieldFiller/SlowSqlInterceptor）继续对遗留 MyBatis XML 生效，两套机制并存不冲突

## Dependencies

### Internal
- `common-web`（PageRequest / PageResult）
- `common-security`（DataScopeContext — 获取当前 empId）

### External
- `mybatis-spring` — MyBatis Interceptor
- `druid-spring-boot-3-starter` — Druid 连接池

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
