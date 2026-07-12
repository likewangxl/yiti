<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-12 | Updated: 2026-07-12 -->

# soap-gateway-center

## Purpose
外部渠道接入网关，把手机端 / ICPS / ESB 等外部渠道的报文转换为对内部各业务模块 `*Api` 的调用。包含两条独立接入链路，**共用同一套业务分发逻辑**（下沉到 `CallPuDispatchService`）：

1. **Netty SOAP 服务**：独立端口（`SoapNettyProperties` 代码默认 `30522`，当前 `bootstrap/application.yml` 显式配置为 `30523`，避免与其他实例端口冲突）。`SoapDispatchHandler` 按 `request.uri()`（即服务号）路由到对应 `SoapEndpoint` 实现；无匹配端点回 404，端点异常回 SOAP Fault；`/ishealth`（忽略大小写）走边车健康检查，不解析 SOAP。
2. **callpu HTTP 网关**：走主应用 Spring MVC，`POST /api/callpu`，按报文 `RuleName` 分发，返回 `{ReturnCd, RspMsg}` 信封，始终 HTTP 200。

**基础包名**: `com.bank.branch.platform.soap`
**Maven 坐标**: `com.bank.branch.platform:soap-gateway-center`

**与主平台的关系**：本模块**不独立启动**，无自己的 `@SpringBootApplication`；由根 `pom.xml` 聚合为一个 module，并被 `bootstrap/pom.xml` 显式声明依赖，随 `bootstrap` 的 `BranchPlatformApplication`（唯一启动入口，基础包 `com.bank.branch.platform`）一起装配。`SoapNettyServer` 实现 `SmartLifecycle`，随 Spring 容器启停，在与主 HTTP 端口（`server.port`）相同的 JVM 进程内，额外绑定并监听独立的 Netty 端口（`platform.soap.netty.port`）。

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/soap/config/SoapNettyProperties.java` | `platform.soap.netty.*` 配置绑定（port/线程数/最大报文长度/idle 超时/是否记录报文） |
| `src/main/java/com/bank/branch/platform/soap/config/SoapNettyServer.java` | Netty 服务端，`SmartLifecycle` 随 Spring 容器启停；端口绑定失败时抛出带端口号的明确异常 |
| `src/main/java/com/bank/branch/platform/soap/config/CallPuContentTypeNormalizationFilter.java` | callpu 入口 `application/x-www-form-urlencoded` 兼容过滤器（见下「SYS_415 修复」） |
| `src/main/java/com/bank/branch/platform/soap/config/SidecarUniauthProperties.java` | `platform.sidecar.uniauth.source-sys-id` 绑定，供 `RspEnvelopeBuilder` 装配本系统号 |
| `src/main/java/com/bank/branch/platform/soap/config/SidecarRegistrationChecker.java` | 应用就绪后异步轮询边车 `/isready` + `/up` 完成注册，`@PreDestroy` 同步调 `/down` 注销 |
| `src/main/java/com/bank/branch/platform/soap/config/SidecarProbe.java` / `HttpSidecarProbe.java` | 边车探测抽象接口 + JDK `HttpClient` 生产实现（调 `platform.sidecar.health-base-url`，默认 `8089`） |
| `src/main/java/com/bank/branch/platform/soap/controller/CallPuController.java` | `POST /api/callpu` 统一分发入口 |
| `src/main/java/com/bank/branch/platform/soap/controller/SideCarHealthCheckController.java` | `GET /ishealth`（HTTP 侧，Spring MVC），边车定时探活，恒定返回 0（健康） |
| `src/main/java/com/bank/branch/platform/soap/endpoint/AxlryPrsRvrSysSvcEndpoint.java` | `AxlryPrsRvrSysSvc`（服务号 `/S080021264`）SOAP 端点，SOAP body 拆包后委托 `CallPuDispatchService` |
| `src/main/java/com/bank/branch/platform/soap/endpoint/RspEnvelopeBuilder.java` | 按 respXml 结构装配 SOAP 响应信封（可注入 `Clock`，测试可用固定时钟断言） |
| `src/main/java/com/bank/branch/platform/soap/handler/SoapDispatchHandler.java` | Netty pipeline handler，按 uri 路由到 `SoapEndpoint`，含 `/ishealth`（Netty 侧）分流 |
| `src/main/java/com/bank/branch/platform/soap/parse/SoapBodyBinder.java` | 用 `StreamReaderDelegate` 剥离命名空间后按元素本地名做 JAXB 绑定，一套绑定类适配任意服务号 |
| `src/main/java/com/bank/branch/platform/soap/service/CallPuDispatchService.java` | callpu 分发业务核心（HTTP + SOAP 两条链路共用），556 行，按 `RuleName` 分发 9 种业务 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `config/` | Netty 属性绑定 / 服务端生命周期 / callpu 415 兼容过滤器 / 边车（sidecar）注册与探测 |
| `controller/` | `CallPuController`（callpu 分发）+ `SideCarHealthCheckController`（HTTP 健康检查） |
| `controller/dto/` | `CallPuRequest` / `CallPuResponse` / `CustInfoData` / `DictItemData` / `OrigAllocData` / `PerfDetailData` / `PerfListData` / `PerfListItem`（外部字段用 `@JsonProperty` 对齐大写命名） |
| `endpoint/` | `SoapEndpoint` 接口 + `EchoSoapEndpoint`（示例）/ `AxlryPrsRvrSysSvcEndpoint`（真实业务）+ `RspEnvelopeBuilder` |
| `endpoint/bind/` | `ReqAxlryPrsRvrSysSvcType` / `ReqSvcHeaderType`（SOAP body 强类型 JAXB 绑定） |
| `handler/` | `SoapChannelInitializer`（Netty pipeline 装配）+ `SoapDispatchHandler`（按 uri 路由） |
| `parse/` | `SoapEnvelopeParser` / `SoapMessage` / `SoapBodyBinder`（SOAP 信封解析 + 命名空间剥离绑定） |
| `service/` | `CallPuDispatchService`（callpu 分发业务核心，HTTP + SOAP 共用，模块唯一 Service） |

## For AI Agents

### Working In This Directory
- **两条接入链路共用业务核心**：HTTP 入口 `CallPuController` 与 SOAP 端点 `AxlryPrsRvrSysSvcEndpoint` 都委托 `CallPuDispatchService.dispatch`，新增/修改业务规则时改这一处即可，禁止在两个入口分别实现。
- **分发键**取自内层 `Parm` JSON 的 `RuleName`（如 `PERF_LIST`），**不是** SOAP 报文 `SvcBody.RuleName`（那里恒为 `post`）——排查"分发不到业务"问题时先看这一层。
- `CallPuDispatchService.dispatch` 当前支持 9 个 `RuleName`：`PERF_LIST` / `PERF_MY_LIST` / `PERF_SAVE` / `CASH_GETCUST_INFO` / `PERF_RECALL` / `PERF_APPR` / `PERF_ORIG_ALLOC` / `PERF_INFO` / `SYS_DICT_ITEMS`；未命中一律降级为 `CallPuResponse.fail("不支持的 RuleName: ...")`，不抛异常。
- **命名空间剥离**：`SoapBodyBinder` 用 `StreamReaderDelegate` 把 XML 命名空间置空后按本地名匹配，因为业务命名空间里含服务号（`.../services/S080021264`），各服务不同；一套绑定类可适配任意服务号，不要为新服务号单独写绑定类。
- **本系统号取配置**：`AxlryPrsRvrSysSvcEndpoint` 装配响应时，`TargetSysId`/`BackendSysId`/`BackendSeqNo` 前 4 位均取 `platform.sidecar.uniauth.source-sys-id`（`SidecarUniauthProperties`），**不是**请求报文里的 `SourceSysId`。
- **callpu 鉴权**：外部渠道入口，认证由上游 callpu/ESB 完成（员工号随报文传入），故 `CallPuController` **未挂** `@BizAuth`，也**未注册** `PT_RESOURCE`——这是本模块唯二不遵守"所有接口必须登记 PT_RESOURCE"红线的入口（另一个是 `/ishealth`），因为它们服务外部渠道/基础设施探活而非内部用户。
- **响应永远 HTTP 200**：`CallPuResponse` 用 `ReturnCd`（"0"=成功 / "99"=失败）区分业务结果，`dispatch` 内统一 try-catch 把业务异常降级为失败信封，避免被 `common-web` 的 `GlobalExceptionHandler` 改写成平台标准响应格式。
- **两个独立的健康检查入口**，均恒定返回健康态（尚未接入真实判断逻辑）：HTTP 侧 `SideCarHealthCheckController#/ishealth`（Spring MVC，走 `server.port`）；SOAP/Netty 侧 `SoapDispatchHandler#isHealthCheck`（走 `platform.soap.netty.port`，路径忽略大小写匹配 `ishealth`）。改健康检查逻辑时两处都要改。
- **配置来源已收敛**：模块自身 `CLAUDE.md` 提到的 `application-soap.yml` 已删除，`platform.soap.netty.*` 与 `platform.sidecar.*` 当前唯一生效来源是 `bootstrap/src/main/resources/application.yml`（`bootstrap/pom.xml` 无此文件加载路径时不要再去找它，直接改 bootstrap 的 yml）。
- **边车（sidecar）注册/注销**：`SidecarRegistrationChecker` 在 `ApplicationReadyEvent` 后启独立守护线程轮询 `/isready`→`/up`，不阻塞 Spring 启动和 Netty 端口监听；`@PreDestroy` 同步调 `/down`。排查"启动很快但边车侧显示未注册"时看这条异步线程的日志（`[Sidecar] ...`），不是启动阻塞问题。

### Testing Requirements
- `CallPuFormUrlencodedCompatTest`：守护 SYS_415 修复，覆盖 urlencoded+JSON body 返回 200、标准 `application/json` 不回归两个用例。
- 现有测试类共 7 个：`SidecarRegistrationCheckerTest` / `CallPuControllerTest` / `CallPuFormUrlencodedCompatTest` / `AxlryPrsRvrSysSvcEndpointTest` / `RspEnvelopeBuilderTest` / `SoapDispatchHandlerTest` / `SoapBodyBinderTest` / `CallPuDispatchServiceTest`。
- `RspEnvelopeBuilder` 用可注入 `Clock` 便于固定时钟断言（`TranDate`/`TranTime`/`BackendSeqNo` 含时间戳片段）。
- **本机编译/测试必须用 JDK 17**：lombok 1.18.30 在本机默认 JDK 26 下注解处理器会静默失效，需前置 `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64`；跨模块改动后按根 `CLAUDE.md`「Stale jar」先对上游模块 `mvn clean install -DskipTests`。

### Common Patterns
- **跨模块红线**：仅通过对方 `*Api` 调用，禁止直连 `service`/`mapper`/`entity`。当前使用：`performance-engine-center` 的 `PerfApprovalQueryApi` / `PerfApprovalCmdApi` / `AllocApi` / `CustStatQueryApi`；`customer-marketing-center` 的 `CustomerQueryApi`；`system-governance-center` 的 `DictApi`；`auth-permission-center` 的 `UserApi`（报文 `EmployeeNo`/`allocater` 实为 `PT_USER.USERNAME`，经 `UserApi.mapUsernamesToEmpId` 批量转 `USER_ID`）。
- **业务类型口径**：手机端业务类型（PERF_BIZ_KIND）从 `SYS_DICT_ITEMS` 实时拉字典，`PERF_SAVE` 直接落字典码，经 `DictApi.isValidDictValue` 校验，不做中文↔码值翻译。
- **字段约定**：外部渠道 DTO 用 `@JsonProperty` 显式对齐手机端大写字段名（`RuleName`/`Parm`/`EmployeeNo` 等），新增渠道适配优先在网关层（filter/converter）做归一化，保持业务 Controller 签名干净。

## Dependencies

### Internal
- `common-web`, `common-trace`
- `performance-engine-center`（`PerfApprovalQueryApi` / `PerfApprovalCmdApi` / `AllocApi` / `CustStatQueryApi`；模块 pom 注释标注"手机端审批列表网关用"）
- `auth-permission-center`（仅 `UserApi`，工号↔`USER_ID` 转换）
- `system-governance-center`（仅 `DictApi`，原经 perf 传递依赖，现已显式声明）

### External
- `io.netty:netty-codec-http` / `netty-handler` — Netty SOAP 服务端
- `jakarta.xml.bind:jakarta.xml.bind-api` + `org.glassfish.jaxb:jaxb-runtime` — SOAP XML 解析/序列化（JAXB）
- `org.springframework.boot:spring-boot-starter` — 配置属性绑定、生命周期管理（`@ConfigurationProperties`、`SmartLifecycle`）
- `cn.hutool:hutool-json:5.8.0` — SOAP 报文 / callpu JSON 处理（版本就近声明在本模块）

<!-- MANUAL: 手工补充内容写在此行以下，重新生成时会保留 -->
