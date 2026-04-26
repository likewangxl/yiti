<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-04-26 -->

# bootstrap

## Purpose
Spring Boot 启动入口，唯一的应用装配器。负责运行时组件扫描、跨模块 MyBatis Mapper 注册、DIP 桥接（portal ↔ performance），以及所有集成级测试。**不包含任何业务逻辑**。

**基础包名**: `com.bank.branch.platform`
**Maven 坐标**: `com.bank.branch.platform:bootstrap`
**配置文件**: 4 个 profile（dev / test / flowable-e2e / flowable-real-env）

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/BranchPlatformApplication.java` | Spring Boot 启动入口（`@SpringBootApplication`） |
| `src/main/java/com/bank/branch/platform/config/BootstrapMyBatisConfig.java` | 跨模块 MapperScan（注册全部 8 个业务模块的 mapper 包） |
| `src/main/java/com/bank/branch/platform/config/JacksonConfig.java` | Jackson ObjectMapper 自定义（JavaTimeModule + 时间戳） |
| `src/main/java/com/bank/branch/platform/bridge/PerformanceMetricApiBridge.java` | Portal ↔ Performance DIP 桥（实现 portal.adapter.MetricApi） |
| `src/main/resources/application.yml` | 主配置（MySQL+Druid / Redis Session / Flowable / MyBatis / Knife4j） |
| `src/main/resources/application-dev.yml` | 开发环境（Druid 监控 / CORS / DEBUG 日志） |
| `src/main/resources/logback-spring.xml` | Logback（dev: CONSOLE / 非 dev: CONSOLE+FILE / traceId 注入） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/java/com/bank/branch/platform/config/` | 启动配置（BootstrapMyBatisConfig / JacksonConfig） |
| `src/main/java/com/bank/branch/platform/bridge/` | DIP 桥接（Portal MetricApi → Performance MetricApi） |
| `src/test/java/com/bank/branch/platform/it/` | 集成测试（SmokeTest / CrossModuleApiTest / FullAuthChainTest / Flowable E2E / 各模块 IT） |
| `src/test/java/com/bank/branch/platform/it/config/` | 测试配置（TestMockConfig / TestSecurityConfig / FlowableE2ETestConfig） |
| `src/test/resources/` | 多 profile 测试配置（test / flowable-e2e / flowable-real-env） |

## For AI Agents

### Working In This Directory
- 新增业务模块时，必须在 `BootstrapMyBatisConfig.java` 中追加 mapper 包名
- 新增业务模块时，必须在 `pom.xml` 中添加 Maven 依赖
- `PerformanceMetricApiBridge` 遵循 DIP：portal 定义抽象，bootstrap 提供实现
- 应用启动通过 `mvn spring-boot:run`（从 bootstrap 目录）或 IDE 直接启动

### Testing Requirements
- 集成测试从 bootstrap 运行（全模块上下文）
- 测试 profile 隔离：`test`（H2）→ `flowable-e2e`（H2 LEGACY + Flowable）→ `flowable-real-env`（MySQL+Redis+Flowable）
- SmokeTest 验证应用上下文能正常加载

### Common Patterns
- 组件扫描从 `com.bank.branch.platform` 根包自动发现所有模块 Bean
- 无 `@ComponentScan` 覆盖，依赖 Spring Boot 默认行为
- 4 个 Spring profile 分层隔离（dev / test / flowable-e2e / flowable-real-env）

## Dependencies

### Internal
- 依赖所有项目模块: `common-*` (5) + `auth-permission-center` + `system-governance-center` + `workflow-center` + `portal-content-center` + `customer-marketing-center` + `business-application-center` + `performance-engine-center` + `report-analytics-center`

### External
- `spring-boot-starter-web` — Spring Boot Web
- `spring-boot-starter-data-redis` + `spring-session-data-redis` — Redis Session
- `mysql-connector-j` + `Druid` — 数据库连接池
- `knife4j-openapi3-jakarta` — API 文档
- `flowable-spring-boot-starter` — 工作流引擎

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
