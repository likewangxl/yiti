# auth-permission-center/AGENTS.md

本文件补充根 [AGENTS.md](../AGENTS.md)，适用于认证授权中心。

## 模块边界

本模块负责用户认证、UIAS 单点登录、RBAC、业务数据范围、组织架构、机构画像和命名机构组。基础包为 `com.bank.branch.platform.auth`。

- 只依赖 common，不依赖业务模块。
- 其他模块只能使用 `api/` 中的 `*Api` 和 DTO，不得访问本模块 mapper、entity 或内部 service。
- 对外接口、REST 契约和表结构分别以源码、`docs/modules/auth-permission-center/03-接口设计与报文.md`、`04-对外API契约.md`、`05-表结构DDL.md` 为准，不在本文件维护类或端点清单。

## 认证与授权管道

- `AuthenticationFilter` 从 HttpSession 读取用户上下文，并以数据库中的账号状态和全部有效角色刷新身份；账号不可用或无有效角色时销毁 Session，认证基础设施异常必须 Fail Close，不能把异常当匿名或放行。
- `AuthorizationInterceptor` 对受保护 `/api/**` 依次执行资源匹配、RBAC、`@BizAuth` 元数据解析和数据范围组装。未登记的受保护 URL 必须拒绝。
- 认证/RBAC 白名单分别维护在 `AuthenticationFilter.WHITELIST` 和 `WebMvcAuthConfig`，修改时必须同步两处及测试。不要把白名单扩大为“无 `@BizAuth` 即公开”。
- 新增非白名单 REST 端点必须登记 `PT_RESOURCE` 并声明 `@BizAuth`。确需认证自服务且不构建 BizScope 的例外，要显式写入契约并由资源/RBAC测试证明，禁止靠漏注解实现。
- `DataScopeContext` 在请求完成后必须清理，异常路径同样如此；异步和非 Web 入口不能依赖 Web Filter 兜底。

## 多角色与权限语义

- 登录、菜单、接口 RBAC、数据范围与工作流候选组都基于用户全部有效角色的并集；`activeRoleId` 仅为兼容字段，不得用于收窄或切换授权结果。
- `SYS_ADMIN` 只跳过明确实现的 RBAC 检查，不自动绕过业务白名单、机构组、画像或数据范围。
- 主角色标记仅用于展示，不影响授权。兼容角色切换端点不得改变 Session 权限语义。
- 权限读取当前以数据库事实为准并 Fail Close；名称仍含 `Cache`/`evict` 的兼容方法不能被当作存在共享缓存的证据。若重新引入缓存，必须先解决多节点一致性和失效传播。
- 菜单资源与 API 资源分开维护。替换菜单绑定只能处理有效菜单资源，不得顺带删除、推导或授予 API 权限。

## 无会话调用

按显式 `empId` 工作的 `*Api` 方法供 SOAP/callpu 等已认证外部渠道调用，本模块不会从登录 Session 二次鉴权。调用方必须先完成渠道认证、身份绑定和业务授权；不得从普通 Controller 直接透传任意 empId。

## 机构画像与命名机构组

- `PT_ORG_PROFILE` 是 auth 自有经营画像，不回写外部同步组织表。保存前验证外部机构有效性、经营属性、坐标及上下级约束。
- `PT_ORG_GROUP`、`PT_ORG_GROUP_MEMBER`、`PT_ROLE_ORG_GROUP` 表达直接成员和角色绑定，不自动展开组织子树。有效机构必须同时满足外部机构有效和画像有效。
- `OrgGroupApi.resolveAuthorizedScope` 必须计算“员工有效角色 ∩ 屏级角色白名单 ∩ 机构组有效绑定角色”，不得跨角色拼接权限；组不存在、停用、空组、画像缺失和查询异常均 Fail Close。
- 管理画像、成员和角色绑定的写接口必须使用独立资源授权和审计；前端不得提供服务端角色白名单或替代服务端校验。

## UIAS

- UIAS 统一认证通过 `uniauth` 包和边车客户端接入；普通用户名密码登录不得依赖 UIAS 边车可用性。
- 回调优先解析 SAML 响应，并保留明确支持的兼容协议。所有 XML 解析必须禁用 DOCTYPE/外部实体，防止 XXE。
- 登录、重定向和回调是否在白名单，以认证过滤器和 MVC 配置源码为准；新增入口必须同步安全测试，不能只改其中一层。

## 数据与实现规则

- 外部组织/人员同步表只读；auth 自有表的写入必须经过 Service 权限校验和审计。
- `PT_USER.USER_TYPE` 的业务含义由消费模块通过 API 使用；修改枚举或字段语义前检查全部消费方，不在 auth 内耦合营销或工作流规则。
- 新增数据库访问遵循根文件的 MyBatis-Plus 规则。资源、角色、组织和范围变更必须保持事务原子性，失败时不能留下半套授权。

## 验证

- 模块测试使用 `src/test/resources` 中的隔离配置；执行前仍需核对实际 profile 和数据源。
- 认证变更至少覆盖白名单、Session 刷新、账号状态、全角色并集、资源未登记、RBAC、BizScope 和 Fail Close 异常路径。
- 资源/组织配置写入要验证失败不改变原绑定；跨模块或 bootstrap 测试前按根文件刷新本地 SNAPSHOT。
