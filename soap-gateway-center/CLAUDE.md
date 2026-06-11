# soap-gateway-center/ CLAUDE.md

本文件为 `soap-gateway-center` 模块提供上下文说明。

## 模块概述

**soap-gateway-center** 是**外部渠道接入网关**模块，负责把手机端 / ICPS / ESB 等外部渠道的报文，转换为对内部各业务模块 `*Api` 的调用。模块包含两条独立的接入链路：

1. **Netty SOAP 服务**（独立端口，默认 `30522`，见 `application-soap.yml`）：基于 Netty 的轻量 HTTP/SOAP 服务端，`SoapDispatchHandler` 按 **uri(=服务号) 路由**到对应 `SoapEndpoint` 实现；无匹配端点回 404，端点异常回 SOAP Fault。
2. **callpu HTTP 网关**（走主应用 Spring MVC，`POST /api/callpu`）：手机端绩效审批网关，按报文 `RuleName` 分发到具体业务方法，返回 `{ReturnCd, RspMsg}` 信封。

> **两条链路共用业务核心**：分发逻辑统一下沉到 `service/CallPuDispatchService`，HTTP 入口（`CallPuController`）与 SOAP 端点（`AxlryPrsRvrSysSvcEndpoint`）都委托它，避免重复实现 PERF_LIST / PERF_SAVE / CASH_GETCUST_INFO / PERF_RECALL。

**基础包名**: `com.bank.branch.platform.soap`
**Maven 坐标**: `com.bank.branch.platform:soap-gateway-center`

## 跨模块依赖

仅通过对方 `*Api` 调用，**禁止**直接依赖其 service/mapper/entity：

| 上游模块 | *Api | 用途 |
|---|---|---|
| performance-engine-center | PerfApprovalQueryApi / PerfApprovalCmdApi | 分配关系调整审批列表 / 新增 / 撤回 |
| customer-marketing-center | CustomerQueryApi | 客户号查名 |
| system-governance-center | DictApi | SYS_DICT_ITEMS 查字典 + PERF_SAVE 业务类型字典码校验（PERF_BIZ_KIND） |

## 包结构

```
src/main/java/com/bank/branch/platform/soap/
├── config/
│   ├── SoapNettyProperties.java                  # platform.soap.netty.* 配置（port/线程/超时等）
│   ├── SoapNettyServer.java                      # Netty 服务端（SmartLifecycle 随 Spring 启停）
│   └── CallPuContentTypeNormalizationFilter.java # callpu 入口 Content-Type 归一化（见下「415 修复」）
├── controller/
│   ├── CallPuController.java                      # POST /api/callpu 统一分发入口
│   └── dto/                                       # CallPuRequest / CallPuResponse / *Data / *Item
├── endpoint/                                      # SoapEndpoint 接口 + EchoSoapEndpoint / AxlryPrsRvrSysSvcEndpoint
│   └── bind/                                       # SOAP body 强类型绑定（ReqAxlryPrsRvrSysSvcType / ReqSvcHeaderType）
├── handler/                                       # SoapChannelInitializer / SoapDispatchHandler（Netty pipeline，按 uri 路由）
├── parse/                                         # SoapEnvelopeParser / SoapMessage / SoapBodyBinder（含命名空间剥离 JAXB 绑定）
└── service/                                        # CallPuDispatchService（callpu 分发业务核心，HTTP + SOAP 共用）
```

## AxlryPrsRvrSysSvc SOAP 接入（XazcCallPuSvr / 新 Call 浦）

外部渠道（Axis2）以 SOAP 1.1 报文 `POST /S080021264`（uri 即服务号）下发,链路：

```
Netty(30522) → SoapDispatchHandler 按 request.uri() 分流：
   - uri = /ishealth(忽略大小写) → 健康检查,回 0(健康)/1(不健康),不解析 SOAP
   - uri = /S080021264          → AxlryPrsRvrSysSvcEndpoint
        → SoapBodyBinder 把 soap:Body 绑定为 ReqAxlryPrsRvrSysSvcType（剥离含服务号的命名空间）
        → 取 SvcBody.Parm（一段 callpu JSON）→ Jackson 反序列化为 CallPuRequest
        → CallPuDispatchService.dispatch（与 /api/callpu 同一套业务）
        → RspEnvelopeBuilder 按实际响应报文结构装配 SOAP 响应
   - 其余 uri → 404
```

- **分发键**取自内层 `Parm` JSON 的 `RuleName`（如 `PERF_LIST`），**不是** `SvcBody.RuleName`（报文里那是 `post`）。
- **命名空间剥离**：业务命名空间含服务号（`.../services/S080021264`），各服务不同；`SoapBodyBinder` 用 `StreamReaderDelegate` 置空命名空间，按元素本地名匹配,一套绑定类适配任意服务号。
- **本系统号取配置**：`TargetSysId` / `BackendSysId` / `BackendSeqNo` 前 4 位均取本系统配置
  `platform.sidecar.uniauth.source-sys-id`（bootstrap/application-dev.yml,值如 `37150001`），
  **不是**请求里的 `SourceSysId`。由 `config/SidecarUniauthProperties` 绑定,端点注入后传给 `RspEnvelopeBuilder`。
- **响应装配**（`RspEnvelopeBuilder`，结构见 respXml）：
  - `RspHeader`: `d:MsgId`=UUID、`d:TargetSysId`=配置 source-sys-id
  - `RspSvcHeader`: `TranDate`=当前 yyyyMMdd、`TranTime`=当前 HHmmssSSS、`BackendSeqNo`=配置 source-sys-id 前 4 位+yyMMdd+16 位随机数字、`BackendSysId`=配置 source-sys-id、`ReturnCode`=固定 12 个 0、`ReturnMsg`=固定 `操作成功`
  - `SvcBody`: `ReturnCd`=callpu 业务返回码、`RspMsg`=整个 callpu 响应 JSON（XML 转义内嵌）
  - 时间用可注入 `Clock`（生产=系统时钟,测试=固定时钟,便于断言）;`RspEnvelopeBuilder` 保持纯格式化,sysId 由端点传入。

## callpu HTTP 网关（`POST /api/callpu`）

- **统一入口**：`CallPuController#dispatch`，按请求体 `RuleName` 分发。
- **已支持 RuleName**：`PERF_LIST`（审批列表）/ `PERF_MY_LIST`（我的申请）/ `PERF_SAVE`（新增分配调整申请）/ `CASH_GETCUST_INFO`（客户号查名）/ `PERF_RECALL`（撤回申请）/ `PERF_APPR`（审批通过/驳回）/ `PERF_ORIG_ALLOC`（原分配回显）/ `PERF_INFO`（单据详情）/ `SYS_DICT_ITEMS`（按 `dictType` 查启用字典项，手机端业务类型选项 PERF_BIZ_KIND 用，回传 `{items:[{dictCode,dictLabel}]}`）。
- **业务类型口径（PERF_BIZ_KIND）**：手机端业务类型从 `SYS_DICT_ITEMS` 实时拉取（label=dictLabel 展示、value=dictCode 提交），`PERF_SAVE` 直接以字典码逗号串落 perf `biz_kind` 并经 `DictApi.isValidDictValue` 校验，**不再做中文→码翻译**（与 PC 管理端 `listDictItems`/`submitAdjust` 完全一致）。
- **响应信封**：`CallPuResponse`，成功 `ReturnCd="0"`，失败 `ReturnCd="99"`，**始终 HTTP 200**（前端以 `response.ReturnCd == "0"` 判定成功）。`dispatch` 内统一 try-catch，业务异常降级为失败信封，避免被全局异常处理器改写成平台标准响应格式。
- **鉴权**：外部渠道入口，身份认证由上游 callpu/ESB 完成（员工号随报文传入），故**未挂** `@BizAuth`。
- **字段约定**：DTO 用 `@JsonProperty` 对齐手机端大写字段名（`RuleName`/`Parm`/`EmployeeNo` 等）。

## SYS_415 修复：callpu 入口兼容 `application/x-www-form-urlencoded`

### 现象
手机端 / ICPS 旧网关调用 `POST /api/callpu` 时返回 **SYS_415（不支持的媒体类型）**。

### 根因
`CallPuController#dispatch` 用 `@RequestBody CallPuRequest` 接收，能把请求体反序列化为该 POJO 的转换器只有 Jackson（`MappingJackson2HttpMessageConverter`），而 Jackson 默认只接受 `application/json`。旧网关发的是 `Content-Type: application/x-www-form-urlencoded`（但 body 其实是 JSON 文本），Spring 找不到匹配转换器 → 抛 `HttpMediaTypeNotSupportedException` → 被 `common-web` 的 `GlobalExceptionHandler` 翻译成 `SYS_415`。

### 为什么不能"只给 Jackson 加 urlencoded 媒体类型"
当 Content-Type 是 urlencoded 时，Servlet 容器会把请求体当**表单参数**解析；Spring 取 `@RequestBody` 的 body 时会按表单参数**反向 URL 编码重建**请求体（`ServletServerHttpRequest#getBodyFromServletRequestParameters`）。结果 Jackson 收到的是被 URL 编码后的 `%7B...` 而非原始 JSON，解析失败（400）。**`@RequestBody`(JSON) 与 `application/x-www-form-urlencoded` 在 Spring 里本质上不兼容**——该方案已验证证伪并废弃。

### 采用方案：`CallPuContentTypeNormalizationFilter`（控制器零改动）
最高优先级（`Ordered.HIGHEST_PRECEDENCE`）的 `OncePerRequestFilter`：对 `/api/callpu` 且 Content-Type 为 urlencoded 的请求，包一层 `HttpServletRequestWrapper`，把 `getContentType()`/`getHeader("Content-Type")` **对外伪装成 `application/json`**。

- 容器不再把它当表单解析 → 不消费 body → Jackson 直接读到**原始 JSON** 体。
- 过滤器**只改写头、不读取 body**，确保 `@RequestBody` 仍拿到完整请求体。
- `application/json` 请求与其它路径完全不受影响（过滤器只命中 `/api/callpu` + urlencoded）。

> **更优解仍是改前端**：让渠道发 `Content-Type: application/json` 并把数据放进 JSON body。本过滤器是"前端改不动时"的稳妥兜底，二者不冲突。

### 守护测试
`CallPuFormUrlencodedCompatTest`：
- `formUrlencodedContentType_withJsonBody_isAcceptedNot415` —— urlencoded + JSON body 现返回 200（不再 415）。
- `applicationJsonContentType_stillWorks` —— 标准 `application/json` 行为不回归。

## 环境与构建注意

- **本机编译/测试必须用 JDK 17**：项目用 lombok 1.18.30，本机默认 JDK 26 会导致 lombok 注解处理器静默失效、全模块"假编译失败"。在 Claude Code 的 Bash 工具里需逐条前置：
  ```bash
  cd /home/djdev/lijh/yiti
  JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -pl soap-gateway-center test
  ```
  （上游模块已在 `.m2`；跨模块改动后按根 CLAUDE.md「Stale jar」先 `mvn clean install -DskipTests` 上游）

## 开发 Checklist

1. ✅ 跨模块只走对方 `*Api`，禁止直连 mapper/entity
2. ✅ 外部渠道入口的报文字段大小写用 `@JsonProperty` 显式对齐
3. ✅ callpu 业务异常一律降级为 `CallPuResponse.fail(...)`（不向外抛、不改写信封）
4. ✅ 新增渠道适配（新的 Content-Type / 报文格式）优先在网关层做归一化，保持业务控制器签名干净
5. ✅ 严格 TDD 红-绿-重构；中文注释 + UTF-8 编码
