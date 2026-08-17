# soap-gateway-center 模块开发指导

本文件只记录外部渠道网关特有的协议与安全边界；通用模块规范遵循根 `AGENTS.md`。

## 模块职责与边界

`soap-gateway-center` 把手机端、ICPS、ESB 等外部渠道报文转换为平台内部 `*Api` 调用。本模块没有独立启动类，随 `bootstrap` 在同一 JVM 装配；`SoapNettyServer` 额外监听独立 Netty 端口。

跨模块调用只能依赖对方 `*Api`/`*QueryApi`，禁止访问其他模块的 service、mapper 或 entity。配置集中在 `bootstrap/src/main/resources/application*.yml`，部署时优先通过环境变量或启动参数覆盖，禁止在本文固化机器端口、地址或凭据。

## 两条入口，共用一套分发

- Spring MVC HTTP 入口由 `CallPuController` 接收 callpu 请求。
- Netty SOAP 入口由 `SoapDispatchHandler` 按 URI 服务号路由到 `SoapEndpoint`；未知服务返回 404，端点异常返回 SOAP Fault。
- `CallPuController` 与 `AxlryPrsRvrSysSvcEndpoint` 必须共同委托 `CallPuDispatchService`。新增规则、校验或错误处理不得在两条入口各写一份。
- SOAP 内层 `SvcBody.Parm` 是 callpu JSON，实际分发键是其中的 `RuleName`，不是外层同名字段。
- `SoapBodyBinder` 会剥离业务命名空间并按元素本地名绑定；新增服务号不应复制一套 JAXB 类型。
- SOAP 响应中的本系统标识取 `platform.sidecar.uniauth.source-sys-id`，不得信任请求方传入值；时间继续使用可注入 `Clock`，保证测试可重复。

## callpu 响应与安全链约束

- callpu 业务响应保持 `{ReturnCd, RspMsg}` 信封：成功码为 `0`，失败码为 `99`，业务失败仍返回 HTTP 200。异常必须在 `CallPuDispatchService` 内降级为失败信封，不能泄漏为平台通用响应格式。
- 渠道字段大小写通过 DTO 的 `@JsonProperty` 显式适配；新的协议差异优先在 filter、binder 或 converter 层归一化，保持 Controller 和业务分发签名稳定。
- `/api/callpu` 当前仍受平台 `AuthenticationFilter` 与 `WebMvcAuthConfig` 安全链约束，不得把上游渠道认证或协议约定当成已经生效的平台鉴权白名单例外，也不能假定其 `@BizAuth`/`PT_RESOURCE` 例外已生效。
- 如需让外部渠道免平台 Session 调用，必须先经过明确的安全决策，并在 `AuthenticationFilter.WHITELIST` 与 `WebMvcAuthConfig` 中同时显式配置；即使启用该能力，也必须保留渠道自身鉴权、业务层员工号/权限/实体校验和独立审计，不得扩展成其他 REST 接口的通用豁免。

## Content-Type 归一化

旧渠道可能以 `application/x-www-form-urlencoded` 发送实际为 JSON 的原始 body。`CallPuContentTypeNormalizationFilter` 只针对 callpu 路径把对外可见的 Content-Type 包装为 `application/json`，且只改请求头、不读取 body。

不要改成“让 Jackson 直接接受 form-urlencoded”：Servlet 可能先把 body 当表单消费，再反向编码参数，Jackson 得到的将不是原始 JSON。修改此兼容逻辑时必须同时覆盖旧 Content-Type 和标准 JSON 请求。

## 健康检查与边车生命周期

- Spring MVC 与 Netty 各有一个 `/ishealth` 入口；Netty 匹配忽略大小写且在解析 SOAP 前短路。两处当前都返回渠道约定的健康值，变更健康判定时必须同步修改并分别测试。
- `SidecarRegistrationChecker` 在应用就绪后以守护线程异步执行 `/isready` → `/up`，不阻塞 Spring 启动；停机时同步调用 `/down`。
- `platform.sidecar.registration.enabled=false` 用于完全禁用注册与注销；`platform.soap.netty.enabled=false` 用于不装配 Netty 服务。无关的集成测试应显式关闭二者。
- `platform.soap.netty.*` 是 Netty 开关、端口、线程和超时的配置入口。单机多实例必须为每个启用 Netty 的实例分配不同端口。
- `platform.soap.netty.log-request` 或边车报文日志可能包含渠道敏感字段；生产环境保持关闭或先完成脱敏，不得把完整报文作为普通验收日志归档。

## 关键代码与验证

- `config/`：Netty 生命周期、边车注册、协议兼容过滤器和属性绑定。
- `handler/`、`parse/`、`endpoint/`：SOAP 路由、解析、绑定和响应组装。
- `controller/dto/`：HTTP 渠道契约。
- `service/CallPuDispatchService.java`：HTTP/SOAP 唯一业务分发核心。

修改协议或分发逻辑时，先写失败测试，再覆盖 HTTP 与 SOAP 两条链路、未知规则、异常信封、健康检查和 Content-Type 兼容；跨模块改动后按根规则刷新 SNAPSHOT 依赖。
