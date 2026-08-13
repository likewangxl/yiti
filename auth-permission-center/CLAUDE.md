# auth-permission-center/ CLAUDE.md

本文件为 `auth-permission-center` 模块提供上下文说明，是本模块开发指导的唯一权威来源。

## 模块概述

**auth-permission-center** 是认证授权中心，为整个平台提供用户认证、RBAC 权限控制、BizType 数据范围、组织架构管理，以及行内 UIAS 统一认证单点登录接入。是平台安全体系的核心模块。

**基础包名**: `com.bank.branch.platform.auth`

**Maven 坐标**: `com.bank.branch.platform:auth-permission-center`

**对外契约**: 一组 `*Api` 接口（`AuthApi`/`CurrentUserApi`/`ResourceApi`/`BizScopeApi`/`OrgApi`/`RoleApi`/`UserApi`/`OrgGroupApi`），供所有业务模块依赖。具体方法签名以源码 `api/` 目录和 `docs/modules/auth-permission-center/04-对外API契约.md` 为准。

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

1. **AuthenticationFilter**（Servlet Filter，order=1，仅拦截 `/api/*`）：从 HttpSession 读取 `CurrentUserContext`，实时重查账号状态和数据库有效角色；用户删除/禁用/锁定/过期或无有效角色时销毁 Session 并返回 401；数据库及其他认证基础设施异常按 Fail Close 返回标准 503，但保留 Session，避免瞬时故障批量踢用户下线。有效上下文写回 Session 并存入 ThreadLocal（`CurrentUserProvider`），旧的单角色 Session 会在首次请求自动升级。
2. **AuthorizationInterceptor**（MVC Interceptor for `/api/**`）：
   - 白名单直接放行（登录/登出/UIAS 三个端点/Knife4j/actuator health 等，详见 `WebMvcAuthConfig`）；
   - `ResourceMatcher`：AntPathMatcher 匹配 URL→`PT_RESOURCE`，未注册返回 403（AUTH-40302）；
   - `current-user`/`my-menus`/`permissions` 是已认证用户自服务资源：仍须通过 AuthenticationFilter，仍须在 `PT_RESOURCE` 登记，但不依赖角色资源绑定或 BizScope；
   - `RbacAuthorizer`：RBAC 检查，SYS_ADMIN 跳过；角色判定走 `PermissionCacheService.getEffectiveRoleIds()`，始终使用数据库中全部有效角色的并集；
   - `BizMetaResolver`：解析 `@BizAuth` 注解（bizType + bizAction），缺失时构建仅含身份/机构/候选组的最小上下文；
   - `BizScopeFacade.buildScopeContext()`：构建 `DataScopeContext` 到 ThreadLocal。
3. **业务 Controller 方法执行**。
4. **`afterCompletion`** 清理 `DataScopeContext`（`common-security` 的 `DataScopeCleanupFilter` 兜底二次清理，防内存泄漏）。

## 关键实现要点与踩坑

### 权限数据读取（已去 Redis，直查 + Fail Close）

`PermissionCacheService` 原设计为 Cache-Aside（Redis，5 分钟 TTL），**2026-05-20 项目去 Redis 后已改为直接查库**：行内多实例环境无共享 Redis 可用，且 `PT_USER_ROLE`/`PT_ROLE_RESOURCE` 均为主键索引点查（约 0.1ms），业务 SQL 本身耗时更高，去掉缓存层对热路径性能无感知影响。

- `getRoleIdsByEmpId`/`getResourceIdsByRoleId`/`getAllResources`/`getBizScopesByRoleId` 现均直查 Mapper；`getEffectiveRoleIds` 对当前请求用户复用 AuthenticationFilter 刚按数据库刷新的 `CurrentUserContext.roleIds`（忽略兼容字段 `activeRoleId`），查询其他用户或无请求上下文时才直查 Mapper。权限数据查不到/异常时按无权限或请求失败处理，不会误放行。
- `evictXxxCache` 系列方法（`evictUserRolesCache`/`evictRoleResourceCache`/`evictBizScopeCache`/`evictAllResourceCache`/`evictOrgSubtreeCache`）**保留原方法签名，实现改为 NoOp**（仅打 debug 日志），避免动 `RoleService`/`UserRoleService`/`BizScopeService`/`RoleResourceService` 里大量调用点；后续如发现真实热点可改回本地缓存（Caffeine）。
- 权限变更时仍发布 `PermissionCacheInvalidatedEvent`（`USER_ROLE`/`ROLE_RESOURCE`/`BIZ_SCOPE` 三种事件类型），但**目前仓库内无其他监听者消费**——事件保留是为兼容未来重新引入缓存/多节点通知的扩展点，而非当前有实际效果的失效通知。

### session 全部有效角色并集模型

- 普通登录与 UIAS 登录均把数据库中全部有效角色写入 `CurrentUserContext.roleIds`/`roleCodes`；工作流候选组包含全部 `ROLE:{roleCode}`，并保留 `USER:{empId}` 与可用的 `ORG:{mainOrgCode}`。
- 任一有效角色的 `ROLE_CODE=SYS_ADMIN` 时 `systemAdmin=true`。AuthenticationFilter 每请求重建这些角色派生字段，因此角色撤销、角色禁用和 SYS_ADMIN 回收在下一次请求立即生效。
- `activeRoleId` 为兼容既有 Spring Session JDBC 序列化结构而保留，新建和刷新上下文恒为 `null`。菜单、接口 RBAC、DataScope 与工作流候选组均按全部有效角色并集解析；DataScope 本阶段继续使用既有优先级合并规则。
- `DEFAULT_ASSIGN=1` 的主角色仅用于登录响应和 `current-user.roles[].primary` 的默认展示标记，不收窄权限。
- `POST /api/auth/switch-role` 保留一个版本周期作为 `Deprecated` 兼容端点：仍校验目标角色属于当前用户，但不修改 Session，也不改变权限。

### 菜单与 API 授权解耦

- `RoleResourceService.replaceMenus` 会在删除旧绑定前逐个校验资源存在、`STATUS=0` 且 `ISMENU=1`；任一不存在、禁用或接口资源 ID 都整体拒绝且不改变原绑定。校验通过后只删除并重建菜单绑定。
- `ISMENU=0` 的 API 绑定必须通过显式权限配置维护；菜单替换不会删除已有 API，也不会根据菜单父子关系或“公共接口”自动授予 API。

### 机构画像与命名机构组（2026-08-11）

- `PT_ORG_PROFILE` 是 auth 自有的本地经营画像，不回写外部同步表 `EXT_ORG_INFO`；画像保存前必须校验外部机构有效、经营等级和坐标声明。`SUBORDINATE` 必须显式绑定祖先链上的有效 `PRIMARY` 机构，`DEPARTMENT + PRIMARY` 与 `SECONDARY_BRANCH + PRIMARY` 均为合法组合。
- `PT_ORG_GROUP`/`PT_ORG_GROUP_MEMBER`/`PT_ROLE_ORG_GROUP` 只表达直接机构成员和角色绑定，不自动展开组织子树。运行时有效机构集合为“直接成员 ∩ EXT_ORG_INFO 有效 ∩ PT_ORG_PROFILE ACTIVE”。
- `OrgGroupApi.resolveAuthorizedScope` 强制求“员工全部有效角色 ∩ 屏级角色白名单 ∩ 机构组有效绑定角色”，不得跨角色拼接权限；组不存在、停用、空组、画像缺失或查询异常均 Fail Close。
- 四张新增表的手工对齐 SQL 为 `docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql`，管理端画像、成员和角色覆盖接口使用独立 `PT_RESOURCE`、`@BizAuth` 与 `@AuditLog`。

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
- 表结构见 `docs/modules/auth-permission-center/05-表结构DDL.md`；核心表为 `PT_USER`/`PT_ROLE`/`PT_USER_ROLE`/`PT_RESOURCE`/`PT_ROLE_RESOURCE`/`PT_ROLE_BIZ_SCOPE`（平台表）+ `EXT_ORG_INFO`/`EXT_USER_ORG`（外部只读同步表）+ `PT_ORG_PROFILE`/`PT_ORG_GROUP`/`PT_ORG_GROUP_MEMBER`/`PT_ROLE_ORG_GROUP`（auth 本地配置表）。
- 示例代码统一登记在 `docs/code-examples.md`，本文件不复制代码片段。

## 测试指引

- 单元测试使用 H2 内存库（`jdbc:h2:mem:testdb;MODE=MySQL`），配置见 `src/test/resources/application.yml` + `schema.sql`/`data.sql`；测试专用 Spring Boot 入口为 `AuthTestConfiguration`（`scanBasePackages` 限定本模块包 + `@MapperScan`，不引入 Web MVC/Redis 等外部依赖）。
- `*Test.java`/`*Tests.java` → surefire（`mvn test` 触发）；`*IT.java`（如 `it/AuthMapperIntTest` 所在包）→ failsafe（`mvn verify` 触发）。
- 跨模块 `@SpringBootTest`（bootstrap 层）依赖本模块最新类时，按根 CLAUDE.md 的 stale jar 处理流程先 `mvn clean install -DskipTests`。
