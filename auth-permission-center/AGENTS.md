# auth-permission-center/AGENTS.md

本文件为 `auth-permission-center` 模块提供上下文说明。

## 模块概述

**auth-permission-center** 是认证授权中心，为整个平台提供用户认证、RBAC 权限控制、BizType 数据范围和架构管理。是平台安全体系的核心模块。

**基础包名**: `com.bank.branch.platform.auth`

**Maven 坐标**: `com.bank.branch.platform:auth-permission-center`

**对外契约**: 5 个 `*Api` 接口，供所有业务模块依赖。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- **不依赖**: 任何业务模块 (最基础的共享模块之一)
- **被依赖**: 所有需要鉴权和权限控制的模块

## 包结构

```
src/main/java/com/bank/branch/platform/auth/
├── api/              # 对外 API 接口 (5 个接口)
│   ├── AuthApi.java
│   ├── CurrentUserApi.java
│   ├── ResourceApi.java
│   ├── BizScopeApi.java
│   └── OrgApi.java
├── api/dto/          # 请求/响应 DTO (16+ 类)
├── api/event/        # Spring 事件 (PermissionCacheInvalidatedEvent)
├── controller/       # REST 控制器 (6 个)
├── config/           # Spring 配置 (AuthConfig, CacheConfig, WebMvcAuthConfig)
├── entity/           # 数据库实体 (7 类)
├── enums/            # 错误码枚举 (AuthErrorCode)
├── facade/           # API 实现 (5 个 @Service)
├── mapper/           # MyBatis Mapper (8 个接口)
├── security/         # 安全管道
│   ├── context/      # CurrentUserProvider (ThreadLocal)
│   ├── filter/       # AuthenticationFilter (Servlet Filter)
│   ├── interceptor/  # AuthorizationInterceptor (MVC Interceptor)
│   ├── matcher/      # ResourceMatcher (AntPath URL 匹配)
│   └── resolver/     # RbacAuthorizer, BizMetaResolver, BizMeta
└── service/          # 业务逻辑 (7 个 Service)
```

## 5 个对外 API 接口

### AuthApi
用户认证和会话管理。
- `getCurrentUser()` - 获取当前用户上下文 (未登录抛 AUTH-40105)
- `isAuthenticated()` - 检查当前请求是否已认证
- `invalidateSession(String empId)` - 强制失效用户会话 (高危: PERMISSION_CHANGE)

### CurrentUserApi
轻量级用户上下文读取 (读 ThreadLocal, 零 DB I/O)。
- `getCurrentUserContext()` / `getCurrentEmpId()` / `getCurrentOrgCode()`
- `getCurrentRoleIds()` / `getCurrentRoleCodes()` / `getCurrentCandidateGroupKeys()` (用于 Flowable 任务声明)
- `isSystemAdmin()`

### ResourceApi
资源/权限查询 (Redis 缓存)。
- `matchResource(url, method)` - 匹配请求 URL+Method 到已注册的 PT_RESOURCE
- `hasResourcePermission(empId, resourceId)` - 检查用户是否有 RBAC 权限访问该资源
- `listUserResources(empId)` / `listUserResourceUrls(empId)` - 获取用户所有可访问资源/URL

### BizScopeApi
数据范围解析 (细粒度数据权限)。
- `resolveScope(empId, bizType)` - 解析用户对某业务类型的最大数据范围
- `buildScopeContext(empId, bizType, action)` - 构建完整 DataScopeContext (含 orgSubtreeCodes)
- `checkWritePermission(empId, bizType, entityOwnerOrgId, entityCreatedBy)` - 写权限校验 (Fail-Close)
- `getUserBizScopes(empId)` - 批量获取用户所有 BizType 到 DataScope 的映射

### OrgApi
组织管理查询 (Redis 缓存)。
- `getOrg(orgCode)` / `getOrgSubtree(orgCode)` / `getOrgSubtreeCodes(orgCode)`
- `getUserMainOrg(empId)` / `searchOrgs(keyword, limit)`

## 安全管道 (5 层链路)

请求处理顺序:

1. **AuthenticationFilter** (Servlet Filter, order=1)
   - 跳过白名单 (login/logout/swagger/actuator)
   - 从 HttpSession 读取 `CurrentUserContext`
   - 无效返回 401, 有效存入 ThreadLocal (`CurrentUserProvider`)

2. **AuthorizationInterceptor** (MVC Interceptor for `/api/**`)
   - `ResourceMatcher`: AntPathMatcher 匹配 URL→PT_RESOURCE, 未注册返回 403 (AUTH-40302)
   - `RbacAuthorizer`: RBAC 检查, SYS_ADMIN 跳过
   - `BizMetaResolver`: 解析 `@BizAuth` 注解 (bizType + bizAction), 缺失返回 403 (AUTH-40304)
   - `BizScopeFacade.buildScopeContext()`: 构建 DataScopeContext 到 ThreadLocal

3. **业务 Controller 方法执行**

4. **`afterCompletion`** 清理 DataScopeContext

## 数据库表 (8 张)

### 平台表 (RBAC 核心)

| 表 | 实体 | 说明 |
|----|------|------|
| `PT_USER` | PtUser | 用户信息 (USER_ID, USERNAME, PWD(BCrypt), PASS_WRONG_COUNT, ISLOCKED, ISENABLED) |
| `PT_ROLE` | PtRole | 角色信息 (ROLE_ID, ROLE_CODE `R_XXXXXXXX`) |
| `PT_USER_ROLE` | PtUserRole | 用户-角色关系 ((USER_ID, ROLE_ID) 联合主键) |
| `PT_RESOURCE` | PtResource | 资源/URL注册表 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, IS_MENU) |
| `PT_ROLE_RESOURCE` | PtRoleResource | 角色-资源关系 (M:N 授权) |
| `PT_ROLE_BIZ_SCOPE` | PtRoleBizScope | 角色-BizType-数据范围映射 |

### 外部同步表 (只读)

| 表 | 实体 | 说明 |
|----|------|------|
| `EXT_ORG_INFO` | ExtOrgInfo | 组织信息 (ORG_CODE, P_ID, 层级: 1=总部, 2=分行, 3=支行) |
| `EXT_USER_ORG` | ExtUserOrg | 用户-组织关系 (USER_ID, ORG_CODE 联合主键) |

## 缓存 (Cache-Aside, 5 分钟 TTL)

| Key 模式 | 内容 |
|----------|------|
| `auth:user-roles:{empId}` | 用户的角色列表 |
| `auth:role-resource:{roleId}` | 角色的资源列表 |
| `auth:biz-scope:{roleId}` | 角色的 BizScope 列表 |
| `auth:resource:all` | 全站资源映射 |
| `auth:org-subtree:{orgCode}` | 组织子树代码 |

权限变更时发布 `PermissionCacheInvalidatedEvent` 事件通知其他模块刷新缓存。事件类型: `USER_ROLE`, `ROLE_RESOURCE`, `BIZ_SCOPE`。

## REST 端点

| Controller | 路径 | 说明 |
|------------|------|------|
| AuthController | `/api/auth/` | 登录/登出/当前用户 |
| RoleController | `/api/admin/roles/` | 角色 CRUD (需 @BizAuth(SYS_CONFIG)) |
| UserRoleController | `/api/admin/users/{userId}/roles/` | 用户角色绑定 |
| ResourceController | `/api/admin/resources/` | 资源/菜单 CRUD + 角色-资源绑定 |
| BizScopeController | `/api/admin/biz-scopes/` | 数据范围 CRUD |
| OrgController | `/api/orgs/` | 组织架构查询 |

## 业务 Service

| Service | 职责 |
|---------|------|
| `AuthService` | 登录 (BCrypt 密码校验, 密码错误计数, 5 次错误自动锁定), 登出, 会话管理 |
| `RoleService` | 角色 CRUD, 角色码唯一性 (AUTH-40901), 逻辑软删除 |
| `UserRoleService` | 用户-角色绑定/解绑, 批量绑定幂等, 发布缓存失效事件 |
| `ResourceService` | 资源 CRUD, 资源树构建, URL+Method+SysCode 唯一约束, 删除时级联清理 |
| `RoleResourceService` | 角色-资源增量绑定 (幂等) 和全量替换, 发布事件 |
| `BizScopeService` | 数据范围解析 (多角色联合策略), BizScope CRUD (upsert) |
| `OrgService` | 组织架构查询, 递归子树收集, 模糊搜索 |
| `PermissionCacheService` | Redis 缓存管理, 缓存失效, 事件发布 |
