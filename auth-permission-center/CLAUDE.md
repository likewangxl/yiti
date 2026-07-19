# auth-permission-center/ CLAUDE.md

本文件为 `auth-permission-center` 模块提供上下文说明，是本模块开发指导的唯一权威来源。

## 模块概述

**auth-permission-center** 是认证授权中心，为整个平台提供用户认证、RBAC 权限控制、BizType 数据范围、组织架构管理，以及行内 UIAS 统一认证单点登录接入。是平台安全体系的核心模块。

**基础包名**: `com.bank.branch.platform.auth`

**Maven 坐标**: `com.bank.branch.platform:auth-permission-center`

**对外契约**: 一组 `*Api` 接口（`AuthApi`/`CurrentUserApi`/`ResourceApi`/`BizScopeApi`/`OrgApi`/`RoleApi`/`UserApi`），供所有业务模块依赖。具体方法签名以源码 `api/` 目录和 `docs/modules/auth-permission-center/04-对外API契约.md` 为准。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- **不依赖**: 任何业务模块（最基础的共享模块之一）
- **被依赖**: 所有需要鉴权和权限控制的模块

## 架构规则与红线

- 模块间只能通过 `api/` 下的 `*Api` 接口交互，禁止其他模块直连本模块 `mapper`/`entity`/`serviceImpl`。
- 所有新增 REST 端点必须登记到 `PT_RESOURCE` 并声明 `@BizAuth`，否则 `ResourceMatcher` 会给未注册 URL 一律返回 403（AUTH-40302）。
- 无会话方法（供 SOAP 网关/callpu 等外部渠道按显式 `empId` 调用，如 `UserApi.getUserRoleCodes(empId)`/`getCandidateGroupKeys(empId)`）不依赖登录态，调用方必须自行完成鉴权，本模块不做二次校验。

## 安全管道（5 层链路）

请求处理顺序：

1. **AuthenticationFilter**（Servlet Filter，order=1，仅拦截 `/api/*`）：从 HttpSession 读取 `CurrentUserContext`，无效返回 401，有效存入 ThreadLocal（`CurrentUserProvider`）。
2. **AuthorizationInterceptor**（MVC Interceptor for `/api/**`）：
   - 白名单直接放行（登录/登出/UIAS 三个端点/Knife4j/actuator health 等，详见 `WebMvcAuthConfig`）；
   - `ResourceMatcher`：AntPathMatcher 匹配 URL→`PT_RESOURCE`，未注册返回 403（AUTH-40302）；
   - `RbacAuthorizer`：RBAC 检查，SYS_ADMIN 跳过；角色判定走 `PermissionCacheService.getEffectiveRoleIds()`——若当前会话已切换角色（见下文"角色切换"），仅按该激活角色解析权限，否则回退为全部已分配角色；
   - `BizMetaResolver`：解析 `@BizAuth` 注解（bizType + bizAction），缺失返回 403（AUTH-40304）；
   - `BizScopeFacade.buildScopeContext()`：构建 `DataScopeContext` 到 ThreadLocal。
3. **业务 Controller 方法执行**。
4. **`afterCompletion`** 清理 `DataScopeContext`（`common-security` 的 `DataScopeCleanupFilter` 兜底二次清理，防内存泄漏）。

## 关键实现要点与踩坑

### 权限数据读取（已去 Redis，直查 + Fail Close）

`PermissionCacheService` 原设计为 Cache-Aside（Redis，5 分钟 TTL），**2026-05-20 项目去 Redis 后已改为直接查库**：行内多实例环境无共享 Redis 可用，且 `PT_USER_ROLE`/`PT_ROLE_RESOURCE` 均为主键索引点查（约 0.1ms），业务 SQL 本身耗时更高，去掉缓存层对热路径性能无感知影响。

- `getRoleIdsByEmpId`/`getEffectiveRoleIds`/`getResourceIdsByRoleId`/`getAllResources`/`getBizScopesByRoleId` 现均直查 Mapper；权限缓存失效等同"当次即查最新"，天然 Fail Close：查不到/异常时按无权限处理，不会误放行。
- `evictXxxCache` 系列方法（`evictUserRolesCache`/`evictRoleResourceCache`/`evictBizScopeCache`/`evictAllResourceCache`/`evictOrgSubtreeCache`）**保留原方法签名，实现改为 NoOp**（仅打 debug 日志），避免动 `RoleService`/`UserRoleService`/`BizScopeService`/`RoleResourceService` 里大量调用点；后续如发现真实热点可改回本地缓存（Caffeine）。
- 权限变更时仍发布 `PermissionCacheInvalidatedEvent`（`USER_ROLE`/`ROLE_RESOURCE`/`BIZ_SCOPE` 三种事件类型），但**目前仓库内无其他监听者消费**——事件保留是为兼容未来重新引入缓存/多节点通知的扩展点，而非当前有实际效果的失效通知。

### session 单激活角色模型

- 登录（普通登录或 UIAS）时确定一个**主角色**：取用户 `DEFAULT_ASSIGN=1` 的角色（`PT_USER_ROLE.DEFAULT_ASSIGN`），缺失时回退第一个角色；`CurrentUserContext` 增加 `activeRoleId` 字段记录当前会话生效角色。
- `POST /api/auth/switch-role`（`AuthController.switchRole`）：仅本次会话内切换当前角色（必须是本人已分配角色），重新登录回落到主角色。
- 切换后影响范围：菜单（`my-menus`）、接口 RBAC、DataScope 数据范围、工作流候选组（Flowable 任务声明）全部按新激活角色重新解析（`PermissionCacheService.getEffectiveRoleIds(empId)`：若当前会话 `activeRoleId` 非空且与传入 empId 一致，只返回该激活角色；否则回退全部角色）。
- `current-user` 响应（`CurrentUserRespDTO`）内 `roles` 为当前用户全部已分配角色，并用 `primary` 字段标出当前激活角色。

### UIAS 统一认证单点登录（`uniauth` 包）

- `UniAuthSidecarClient` + `UniAuthProperties`：调用行内 UIAS 认证边车（SOAP，`S120030044` 查授权）。
- 三个端点（均在白名单，跳过 RBAC 校验）：`POST /api/auth/uniauth/login`（开发期 mock/编程式入口）、`GET /api/auth/uniauth/redirect`（302 跳转到行内 UIAS 登录页，生产链路入口）、`/api/auth/uniauth/callback`（GET/POST，UIAS 认证完成后的回调）。
- 回调**解析优先级**：优先按 **SAML 2.0 POST Binding** 解析（`SAMLResponse` 表单字段 base64 → XML → 匹配 `<saml:Attribute Name="...">`，兜底 `<saml:NameID>`），解析失败退化为 query 参数取值（兼容简单 SSO 协议）。
- SAML XML 解析**显式禁用 DOCTYPE**（`disallow-doctype-decl` 设为 true）防 XXE 攻击，任何新增 XML 解析逻辑必须沿用这一防护。
- 普通用户名密码登录（`POST /api/auth/login`）**不再**调用 UIAS 边车，避免内网边车不可达时登录被卡住。

### PT_USER.USER_TYPE 虚拟员工语义

`PT_USER.USER_TYPE`（字典 USER_TYPE：1-员工 / 2-虚拟员工）供其他模块识别"虚拟员工"场景（如客户营销/工作流模块中，原业绩所属人为虚拟员工时自动走审批捷径）；auth 模块自身只持有该字段，不感知消费方业务逻辑，扩展该字段语义前先确认消费方影响面。

## 清单与契约指引

- 控制器/Service/Mapper/实体的完整清单以 `src/main/java/com/bank/branch/platform/auth/` 源码目录为准，不在本文件维护。
- 端点契约、请求/响应报文以 `docs/modules/auth-permission-center/03-接口设计与报文.md`、`04-对外API契约.md` 为准，每次接口变更同步更新这两份文档。
- 表结构见 `docs/modules/auth-permission-center/05-表结构DDL.md`；核心表为 `PT_USER`/`PT_ROLE`/`PT_USER_ROLE`/`PT_RESOURCE`/`PT_ROLE_RESOURCE`/`PT_ROLE_BIZ_SCOPE`（平台表）+ `EXT_ORG_INFO`/`EXT_USER_ORG`（外部只读同步表）。
- 示例代码统一登记在 `docs/code-examples.md`，本文件不复制代码片段。

## 测试指引

- 单元测试使用 H2 内存库（`jdbc:h2:mem:testdb;MODE=MySQL`），配置见 `src/test/resources/application.yml` + `schema.sql`/`data.sql`；测试专用 Spring Boot 入口为 `AuthTestConfiguration`（`scanBasePackages` 限定本模块包 + `@MapperScan`，不引入 Web MVC/Redis 等外部依赖）。
- `*Test.java`/`*Tests.java` → surefire（`mvn test` 触发）；`*IT.java`（如 `it/AuthMapperIntTest` 所在包）→ failsafe（`mvn verify` 触发）。
- 跨模块 `@SpringBootTest`（bootstrap 层）依赖本模块最新类时，按根 CLAUDE.md 的 stale jar 处理流程先 `mvn clean install -DskipTests`。
