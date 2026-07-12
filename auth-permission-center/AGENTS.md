<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-03 | Updated: 2026-07-12 -->
# auth-permission-center/AGENTS.md

本文件为 `auth-permission-center` 模块提供上下文说明。

## 模块概述

**auth-permission-center** 是认证授权中心，为整个平台提供用户认证、RBAC 权限控制、BizType 数据范围、组织架构管理，以及行内 UIAS 统一认证单点登录接入。是平台安全体系的核心模块。

**基础包名**: `com.bank.branch.platform.auth`

**Maven 坐标**: `com.bank.branch.platform:auth-permission-center`

**对外契约**: 7 个 `*Api` 接口（AuthApi / CurrentUserApi / ResourceApi / BizScopeApi / OrgApi / RoleApi / UserApi），供所有业务模块依赖。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- **不依赖**: 任何业务模块 (最基础的共享模块之一)
- **被依赖**: 所有需要鉴权和权限控制的模块

## 包结构

```
src/main/java/com/bank/branch/platform/auth/
├── api/              # 对外 API 接口 (7 个接口)
│   ├── AuthApi.java
│   ├── CurrentUserApi.java
│   ├── ResourceApi.java
│   ├── BizScopeApi.java
│   ├── OrgApi.java
│   ├── RoleApi.java         # 角色只读查询（业务模块下拉用）
│   └── UserApi.java         # 用户信息批量查询（跨模块高频消费，多方法已做批量优化）
├── api/dto/          # 请求/响应 DTO (40+ 类)
├── api/event/        # Spring 事件 (PermissionCacheInvalidatedEvent)
├── controller/       # REST 控制器 (8 个)
├── controller/dto/   # Controller 私有 DTO（如 UserExportRow，用户导出 Excel 行模型）
├── config/           # Spring 配置 (AuthConfig, AuthUserProperties, WebMvcAuthConfig)
├── entity/           # 数据库实体 (8 类)
├── enums/            # 错误码枚举 (AuthErrorCode)
├── facade/           # API 实现 (7 个 @Component，对应 7 个 *Api)
├── mapper/           # MyBatis(-Plus) Mapper (9 个接口)
├── security/         # 安全管道
│   ├── context/      # CurrentUserProvider (ThreadLocal)
│   ├── filter/       # AuthenticationFilter (Servlet Filter)
│   ├── interceptor/  # AuthorizationInterceptor (MVC Interceptor)
│   ├── matcher/      # ResourceMatcher (AntPath URL 匹配)
│   └── resolver/     # RbacAuthorizer, BizMetaResolver, BizMeta
├── service/          # 业务逻辑 (10 个 Service)
└── uniauth/          # UIAS 单点登录边车客户端（UniAuthSidecarClient / UniAuthProperties / dto）
```

> `config/` 已不再有 `CacheConfig`：Redis 缓存层已下线（见下文"权限数据读取（已去 Redis）"）。

## 7 个对外 API 接口

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
资源/权限查询。
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
组织管理查询。
- `getOrg(orgCode)` / `getOrgSubtree(orgCode)` / `getOrgSubtreeCodes(orgCode)`
- `getUserMainOrg(empId)` / `searchOrgs(keyword, limit)`

### RoleApi
角色只读查询（系统级配置，结果集小，按需实时查询，不缓存）。
- `listEnabledRoles()` - 查询全部「可用」角色 (RECORD_STATUS=0)，按角色名称升序

### UserApi
用户信息查询与批量操作，是跨模块被调用最频繁的接口之一；大部分方法专为避免逐条 N+1 设计（导入/导出场景可达数万行）。
- `getUserByEmpId` / `getUserName` / `getUserByEmpIds` - 按工号查询用户信息
- `getUserRoleCodes(empId)` / `getCandidateGroupKeys(empId)` - 按任意 empId 查角色编码集合/工作流候选组标识（不依赖登录态，供 SOAP 网关、callpu 等无会话链路使用）
- `getEmpIdsByRoleCode` / `getEmpIdsByRoleCodeAndOrg` / `getEmpIdsByOrg` - 按角色/机构反查启用员工列表（审批候选人展开、机构负责人过滤）
- `getUsersByUsernames` / `filterExistingUsernames` / `mapUsernamesToEmpId` / `mapEmpIdsToUsername` - 批量存在性校验 + 工号↔用户名映射（大批量导入/导出用，`filterExistingUsernames`/`mapXxx` 系列只做单次/分片 IN 查询，刻意不逐条装配 DTO）
- `pageUsers(keyword, pageNo, pageSize)` - 关键字模糊分页查询
- `getRolesByUserIds(userIds)` - 批量查多用户角色列表（避免逐用户 N+1）

## 安全管道 (5 层链路)

请求处理顺序:

1. **AuthenticationFilter** (Servlet Filter, order=1, 仅拦截 `/api/*`)
   - 从 HttpSession 读取 `CurrentUserContext`
   - 无效返回 401, 有效存入 ThreadLocal (`CurrentUserProvider`)

2. **AuthorizationInterceptor** (MVC Interceptor for `/api/**`)
   - 白名单直接放行（登录/登出/UIAS 三个端点/Knife4j/actuator health/`receiveCallPuRequest`/`perf/metric-calc/trigger`，详见 `WebMvcAuthConfig`）
   - `ResourceMatcher`: AntPathMatcher 匹配 URL→PT_RESOURCE, 未注册返回 403 (AUTH-40302)
   - `RbacAuthorizer`: RBAC 检查, SYS_ADMIN 跳过；角色判定走 `PermissionCacheService.getEffectiveRoleIds()`——若当前会话已切换角色（见下文"角色切换"），仅按该激活角色解析权限，否则回退为全部已分配角色
   - `BizMetaResolver`: 解析 `@BizAuth` 注解 (bizType + bizAction), 缺失返回 403 (AUTH-40304)
   - `BizScopeFacade.buildScopeContext()`: 构建 DataScopeContext 到 ThreadLocal

3. **业务 Controller 方法执行**

4. **`afterCompletion`** 清理 DataScopeContext

## 权限数据读取（已去 Redis）

`PermissionCacheService` 已从 Cache-Aside(Redis, 5 分钟 TTL) 改为**直接查库**：行内多实例环境无共享 Redis 可用，且 `PT_USER_ROLE`/`PT_ROLE_RESOURCE` 均为主键索引点查（~0.1ms），业务 SQL 本身耗时更高，去掉缓存层对热路径性能无感知影响。

- `getRoleIdsByEmpId` / `getEffectiveRoleIds` / `getResourceIdsByRoleId` / `getAllResources` / `getBizScopesByRoleId` 现均直查 Mapper
- `getEffectiveRoleIds(empId)`：若当前会话 `CurrentUserContext.activeRoleId()` 非空且与传入 empId 一致，只返回该激活角色（供角色切换后精确控权）；否则回退全部角色
- `evictXxxCache` 系列方法保留原方法签名，实现改为 NoOp（仅打 debug 日志），避免动 `RoleService`/`UserRoleService`/`BizScopeService`/`RoleResourceService` 里大量调用点；后续如发现真实热点可改回 Caffeine 本地缓存
- `UserRoleService`/`RoleResourceService`/`BizScopeService` 变更权限时仍会发布 `PermissionCacheInvalidatedEvent`（`api/event/`），但目前仓库内无其他监听者消费——事件保留是为兼容未来重新引入缓存/多节点通知的扩展点，而非当前有实际效果的失效通知

## 角色切换 / 用户主角色

- 登录（普通登录或 UIAS）时确定一个**主角色**；`CurrentUserContext` 增加 `activeRoleId` 字段记录当前会话生效角色
- `POST /api/auth/switch-role`（`AuthController.switchRole`）：仅本次会话内切换当前角色（必须是本人已分配角色），重新登录回落到主角色
- 切换后影响范围：菜单 (`my-menus`)、接口 RBAC、DataScope 数据范围、工作流候选组（Flowable 任务声明）全部按新激活角色重新解析
- `current-user` 响应 (`CurrentUserRespDTO`) 内 `roles` 为当前用户全部已分配角色，并用 `primary` 字段标出当前激活角色

## UIAS 统一认证单点登录（uniauth 包）

- `UniAuthSidecarClient` + `UniAuthProperties`：调用行内 UIAS 认证边车（SOAP，`S120030044` 查授权），`UniAuthReqDTO`/`UniAuthRespDTO` 承载请求响应
- 三个端点（均在白名单，跳过 RBAC 校验）：
  - `POST /api/auth/uniauth/login` - 开发期 mock/编程式入口，传 `userDomainName` 跳过密码校验直接建 session
  - `GET /api/auth/uniauth/redirect` - 302 跳转到行内 UIAS 登录页（生产链路入口，前端按钮触发）
  - `/api/auth/uniauth/callback`（GET/POST）- UIAS 认证完成后的回调；优先按 **SAML 2.0 POST Binding** 解析（`SAMLResponse` 表单字段 base64 → XML → 匹配 `<saml:Attribute Name="...">` 或兜底 `<saml:NameID>`），解析失败退化为 query 参数取值
- 普通用户名密码登录 (`POST /api/auth/login`) **不再**调用 UIAS 边车，避免内网边车不可达时登录被卡住
- SAML 解析显式禁用 DOCTYPE (`disallow-doctype-decl`) 防 XXE

## 数据库表 (8 张)

### 平台表 (RBAC 核心)

| 表 | 实体 | 说明 |
|----|------|------|
| `PT_USER` | PtUser | 用户信息 (USER_ID, USERNAME, PWD(BCrypt), USER_TYPE(1员工/2虚拟员工), PASS_WRONG_COUNT, ISLOCKED, ISENABLED) |
| `PT_ROLE` | PtRole | 角色信息 (ROLE_ID, ROLE_CODE `R_XXXXXXXX`) |
| `PT_USER_ROLE` | PtUserRole | 用户-角色关系 ((USER_ID, ROLE_ID) 联合主键) |
| `PT_RESOURCE` | PtResource | 资源/URL注册表 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, IS_MENU) |
| `PT_ROLE_RESOURCE` | PtRoleResource | 角色-资源关系 (M:N 授权) |
| `PT_ROLE_BIZ_SCOPE` | PtRoleBizScope | 角色-BizType-数据范围映射 |

### 外部同步表 (只读)

| 表 | 实体 | 说明 |
|----|------|------|
| `EXT_ORG_INFO` | ExtOrgInfo | 组织信息 (ORG_CODE, P_ID, DEPT_NO 机构号, 层级: 1=总部, 2=分行, 3=支行) |
| `EXT_USER_ORG` | ExtUserOrg | 用户-组织关系 (USER_ID, ORG_CODE 联合主键) |

> `PT_USER.USER_TYPE`（字典 USER_TYPE：1-员工 / 2-虚拟员工）供其他模块识别"虚拟员工"场景（如客户营销/工作流模块中，原业绩所属人为虚拟员工时自动走审批捷径）；auth 模块自身只持有该字段，不感知消费方业务逻辑。

## REST 端点

| Controller | 路径 | 说明 |
|------------|------|------|
| AuthController | `/api/auth/` | 登录/UIAS 单点登录/角色切换/登出/当前用户/权限查询 |
| RoleController | `/api/admin/roles/` | 角色 CRUD (需 @BizAuth(SYS_CONFIG))，含 `GET /all` 查全部角色（不分页，供联动下拉用） |
| UserController | `/api/admin/users/` | 用户 CRUD + 导出（含绑定角色）+ 密码重置/修改 + 启用禁用/锁定解锁 |
| UserRoleController | `/api/admin/users/{userId}/roles/` | 用户角色绑定 |
| UserDirectoryController | `/api/users/directory/` | 用户通讯录三表联查（替代原 addrbook，作为审批人选择数据源），仅读 |
| ResourceController | `/api/admin/resources/`、`/api/admin/roles/{roleId}/...` | 资源/菜单 CRUD + 角色-资源绑定 + 角色-菜单绑定 + 「资源维度查询/设置绑定角色」 |
| BizScopeController | `/api/admin/biz-scopes/` | 数据范围 CRUD + 角色×业务类型矩阵查询（`/matrix`，含 bizTypeLabels 中文名） |
| OrgController | `/api/orgs/` | 组织架构查询/树/子树/机构下用户列表 + 机构 CRUD（`organState` 字段控制启用禁用，禁用前校验无用户） |

## 业务 Service

| Service | 职责 |
|---------|------|
| `AuthService` | 登录 (BCrypt 密码校验, 密码错误计数, 5 次错误自动锁定)、UIAS 登录、登出、会话管理、角色切换、菜单树/权限集合装配 |
| `RoleService` | 角色 CRUD, 角色码唯一性 (AUTH-40901), 逻辑软删除, `listAll` 查全部角色 |
| `UserService` | 用户 CRUD、导出（批量取角色避免 N+1）、密码重置/修改、启用禁用/锁定解锁、用户类型 |
| `UserDirectoryService` | 用户通讯录三表联查（替代 addrbook 作为审批人选择数据源） |
| `UserRoleService` | 用户-角色绑定/解绑, 批量绑定幂等, 发布缓存失效事件 |
| `ResourceService` | 资源 CRUD, 资源树构建, URL+Method+SysCode 唯一约束, 删除时级联清理 |
| `RoleResourceService` | 角色-资源增量绑定 (幂等) 和全量替换, 发布事件 |
| `BizScopeService` | 数据范围解析 (多角色联合策略), BizScope CRUD (upsert), 角色×业务类型矩阵装配 |
| `OrgService` | 组织架构查询, 递归子树收集, 模糊搜索, 机构 CRUD (含启用禁用前置校验) |
| `PermissionCacheService` | 权限数据直查（已去 Redis）, 生效角色计算 (`getEffectiveRoleIds`), 缓存失效 NoOp 兼容层, 事件发布 |

<!-- MANUAL: 手工补充内容写在此行以下，重新生成时会被保留 -->
