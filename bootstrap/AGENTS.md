# bootstrap 模块开发指导

本文件只记录 `bootstrap` 目录特有的约束；仓库级架构、TDD、数据库和权限红线遵循根 `AGENTS.md`。

## 模块职责

`bootstrap` 是平台唯一的 Spring Boot 启动入口和运行时装配器，不承载业务逻辑。它负责：

- 从 `com.bank.branch.platform` 根包装配各业务模块；
- 在 `BootstrapMyBatisConfig` 注册跨模块 Mapper 包；
- 提供只能位于组合根的依赖倒置桥接，例如 `PerformanceMetricApiBridge`；
- 承载需要完整应用上下文的 `*IT.java` 集成测试；
- 通过 `SpaForwardConfig`、`SpaEntryController` 支持一体部署时的 SPA 路由回退。

新增业务模块时，必须同时检查根聚合、`bootstrap/pom.xml` 依赖和 `BootstrapMyBatisConfig`，但不得把模块业务实现搬到此目录。

## 配置与运行

- 主配置位于 `src/main/resources/application.yml`，环境差异放在同目录的 `application-<profile>.yml`；测试配置位于 `src/test/resources/`。
- 端口以配置文件、环境变量和启动参数的实际合并结果为准。主 HTTP 使用 `server.port`，SOAP Netty 使用 `platform.soap.netty.port`；不要在文档中复制某台机器当前监听值。
- 本模块使用 Spring Session JDBC；不要重新引入 Redis 作为生产 Session 存储。
- `platform.soap.netty.enabled=false` 可禁止装配 `SoapNettyServer`。不验证 SOAP 的 Spring Boot 测试应关闭它，避免多个缓存测试上下文争抢固定端口。
- `platform.sidecar.registration.enabled=false` 可关闭边车注册线程和停机注销，隔离测试环境应按用途显式设置。

## Profile 语义

Profile 以资源目录中的实际文件为准，新增或删除时同步更新本节：

- `dev`：默认本地开发配置。
- `remerge`：红色引擎合并开发/验收配置，数据源指向测试库；不得据此推断已获准写正式库。
- `screen-scope-e2e`：大屏范围真实联调的隔离配置；凭据和端口从环境变量传入，并关闭调度、外发和无关后台任务。
- `branch-dashboard-e2e`：与 `redengine-task-e2e` 同时启用的大屏 Quartz 联调配置；只运行分行大屏批次任务，关闭红色引擎旧任务调度器。
- `redengine-task-e2e`：红色引擎任务域隔离联调配置；数据源从环境变量指向 `yit_test`，并关闭调度、外联、对象存储和通知外发。
- `test`：默认集成测试 Profile，使用测试数据源并关闭不需要的后台组件。
- `flowable-e2e`：真实 Flowable 端到端测试。
- `flowable-real-env`：遗留的真实 Redis + Flowable 测试环境；它不代表生产重新使用 Redis。运行前检查 SOAP Netty 开关和端口隔离。
- `lead-e2e`：客户营销线索流程端到端测试。
- `redengine-smoke`：红色引擎真实 RBAC 链路冒烟测试；不能改用字面量 `test` Profile，否则 `WebMvcAuthConfig` 不装配，测试鉴权会被假 Filter 代替。

## 构建与测试陷阱

- 跨模块源码变化后，先在仓库根执行 `mvn clean install -DskipTests`，再运行 bootstrap 测试；否则本模块可能从本地仓库加载旧 SNAPSHOT JAR，产生与源码不一致的 Bean、方法或映射错误。
- `*Test.java`/`*Tests.java` 由 Surefire 执行，`*IT.java` 由 Failsafe 在 `verify` 阶段执行。
- `bootstrap/pom.xml` 必须保留 Failsafe 的 `classesDirectory=${project.build.outputDirectory}`。Spring Boot `repackage` 会把主构件替换为 fat JAR；若 Failsafe 从重打包 JAR 解析测试 classpath，会因类位于 `BOOT-INF/classes` 而找不到 `@SpringBootConfiguration`。
- 常用命令：从仓库根执行 `mvn clean install -DskipTests`；从 `bootstrap` 目录执行 `mvn spring-boot:run`；需要运行集成测试时执行对应模块的 `mvn verify`。

## 关键文件

- `src/main/java/com/bank/branch/platform/BranchPlatformApplication.java`：唯一启动类。
- `src/main/java/com/bank/branch/platform/config/BootstrapMyBatisConfig.java`：跨模块 Mapper 扫描。
- `src/main/java/com/bank/branch/platform/config/JacksonConfig.java`：统一 JSON 时间处理。
- `src/main/java/com/bank/branch/platform/config/SpaForwardConfig.java`、`SpaEntryController.java`：SPA 深链接与登录入口兼容。
- `src/main/resources/logback-spring.xml`：环境化日志与 traceId 输出。
