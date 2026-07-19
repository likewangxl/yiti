# bootstrap/ CLAUDE.md

本文件为 `bootstrap` 模块提供上下文说明。

## 模块概述

**bootstrap** 是 Spring Boot 启动入口，唯一的应用装配器：负责运行时组件扫描、跨模块 MyBatis Mapper 注册、DIP 桥接（portal ↔ performance），以及全部集成级测试（`*IT.java`）。**不包含任何业务逻辑**。

**基础包名**: `com.bank.branch.platform`
**Maven 坐标**: `com.bank.branch.platform:bootstrap`

## Key Files

| File | Description |
|------|------|
| `src/main/java/.../BranchPlatformApplication.java` | Spring Boot 启动入口（`@SpringBootApplication`），组件扫描从 `com.bank.branch.platform` 根包自动发现所有模块 Bean，无 `@ComponentScan` 覆盖 |
| `src/main/java/.../config/BootstrapMyBatisConfig.java` | 跨模块 MapperScan（注册全部业务模块的 mapper 包） |
| `src/main/java/.../config/JacksonConfig.java` | Jackson ObjectMapper 自定义（JavaTimeModule + 时间戳） |
| `src/main/java/.../config/SpaForwardConfig.java` | 前后端一体部署的 SPA 路由回退（history 模式深链接 → index.html，`/api/**` 与带扩展名资源不回退） |
| `src/main/java/.../config/SpaEntryController.java` | 路径式 `/login` 302 重定向到 hash 登录页 `/#/login` |
| `src/main/java/.../bridge/PerformanceMetricApiBridge.java` | Portal ↔ Performance DIP 桥（实现 portal.adapter.MetricApi） |
| `src/main/resources/application.yml` | 主配置（MySQL+Druid / **Spring Session JDBC**——session 落 MySQL `SPRING_SESSION` 表，**已去 Redis** / Flowable / MyBatis-Plus / Knife4j / SOAP 网关端口） |
| `src/main/resources/logback-spring.xml` | Logback（dev: CONSOLE / 非 dev: CONSOLE+FILE / traceId 注入） |

## Profile 清单（按用途，以 `src/main/resources`、`src/test/resources` 实际文件为准，不写死总数）

**主配置（`src/main/resources/`）**：
- `dev`（默认激活）：本地开发，Druid 监控 / CORS / DEBUG 日志
- `remerge`：红色引擎合并开发/验收专用，数据源切到 `yiti_test`，其余继承默认配置；用法 `cd bootstrap && mvn spring-boot:run -Dspring-boot.run.profiles=remerge`

**测试配置（`src/test/resources/`）**：
- `test`：默认 IT profile，本地真 MySQL `onepl_test_bootstrap`，禁用 Redis/Quartz/Flowable，`platform.soap.netty.enabled=false`
- `flowable-e2e`：真实 Flowable 引擎端到端测试
- `flowable-real-env`：MySQL + **真实 Redis** + Flowable——全仓库**唯一**仍需真实 Redis 的遗留测试 profile（`FlowableWorkflowCenterRealEnvTest` 专用），未关闭 SOAP Netty
- `lead-e2e`：`LeadWorkflowE2EIT` 专用
- `redengine-smoke`：`RedEngineSmokeIT` 专用——`test` profile 下 `auth-permission-center` 的 `WebMvcAuthConfig`（`@Profile("!test")`）不加载，鉴权走 `TestSecurityConfig` 假 Filter，无法真实触发 401/403；本 profile 沿用 lead-e2e/flowable-e2e 的做法（只改 profile 名字，非字面量 `test`），换取真实 RBAC 鉴权链路，数据源仍连 `onepl_test_bootstrap`

## For AI Agents

### Working In This Directory
- 新增业务模块时，必须在 `BootstrapMyBatisConfig.java` 中追加 mapper 包名，并在 `pom.xml` 中添加 Maven 依赖
- `PerformanceMetricApiBridge` 遵循 DIP：portal 定义抽象，bootstrap 提供实现
- 应用启动通过 `mvn spring-boot:run`（从 bootstrap 目录）或 IDE 直接启动
- **SoapNettyServer 可关断**：`platform.soap.netty.enabled=false`（默认 `true`）跳过该 Bean 装配，避免固定端口与 Spring Test 上下文缓存共存时互相抢占报 "Address already in use"；上表 `test`/`flowable-e2e`/`lead-e2e`/`redengine-smoke` 均已关闭，仅 `flowable-real-env` 未关（详见 `soap-gateway-center/CLAUDE.md`「Netty 端口与 platform.soap.netty.enabled 开关」）
- **failsafe 的 `classesDirectory` 显式指向 `target/classes`**（`pom.xml` build/plugins 覆盖）：bootstrap 的 `spring-boot-maven-plugin:repackage` 会在 package 阶段把 `target/classes` 打成 fat jar 替换主构件，标准生命周期下 repackage 先于 failsafe 的 integration-test 执行，若不覆盖会导致 failsafe 解析测试 classpath 时错误取到 fat jar（`BOOT-INF/classes/...` 嵌套结构），`*IT.java` 报 "Unable to find a @SpringBootConfiguration"

### Testing Requirements
- 集成测试从 bootstrap 运行（全模块上下文），`SmokeTest` 验证应用上下文能正常加载
- 测试 profile 隔离见上「Profile 清单」

### 端口双轨（细节见根 `CLAUDE.md`「端口与 API 文档」节）
`bootstrap/src/main/resources/application.yml` 当前存在仓库基线（`server.port: 18080` / `platform.soap.netty.port: 30522`，已提交）与本机有意未提交改动（`18081` / `30523`）两轨并存；本文件及根 `CLAUDE.md` 按本机当前生效值描述，改动该文件前先确认目标环境用哪一轨。

## Dependencies

### Internal
依赖全部项目模块: `common-*`（5 个子模块）+ `auth-permission-center` + `system-governance-center` + `workflow-center` + `portal-content-center` + `customer-marketing-center` + `business-application-center` + `performance-engine-center` + `report-analytics-center` + `red-engine-center` + `soap-gateway-center`

### External
- `spring-boot-starter-web` — Spring Boot Web
- `spring-session-jdbc` — Session 落 MySQL（`SPRING_SESSION` 表；已去 Redis，`spring-boot-starter-data-redis` 现仅 test scope，供 `LeadE2E`/`FlowableE2E` 测试类的 `RedisTemplate` 引用使用，主代码已不依赖）
- `mysql-connector-j` + Druid — 数据库连接池
- `knife4j-openapi3-jakarta` — API 文档
- `flowable-spring-boot-starter` — 工作流引擎
- `commons-compress:1.25.0`（显式升级）— 匹配 POI 5.2.5 需要的 `putArchiveEntry` 新签名，原 MinIO 传递依赖 1.24.0 与 POI 5.2.5 存在 `NoSuchMethodError`

<!-- MANUAL: 手工补充内容写在此行以下，重新生成时会保留 -->
