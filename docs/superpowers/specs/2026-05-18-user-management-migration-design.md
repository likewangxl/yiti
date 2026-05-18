# 用户管理模块从 xanpd_backend 移植到 yiti — 设计文档

**作者**: Claude (受 djdev 委托)
**日期**: 2026-05-18
**目标模块**: `auth-permission-center`
**关联背景**: 将 `/home/djdev/lf/xanpd_backend` 内 `UserController` 的 12 个用户管理接口完整移植到 yiti（Branch Platform），与 yiti 现有 `PtUser` / `UserMapper` / `UserApi` 基础对齐，遵守 yiti CLAUDE.md 红线（TDD、`PT_RESOURCE` 登记、`@BizAuth`、DTO 边界、统一响应、模块分层）。

---

## 1. 背景与动机

### 1.1 源端现状（xanpd_backend）
- `com.spdb.speed4j.microservice.system.controller.authority.UserController` 提供 14 个接口：CRUD + 启用/禁用/锁定/解锁 + 重置密码 + 当前用户改密 + 用户角色查询/绑定。
- 实体使用下划线字段名（`user_id`/`pass_wrong_count`）、`Date`/`int` 原始类型、无 DTO 分离、响应为 `Map<String, Object>`。
- 密码流：前端 SM2 加密 → 后端 SM2 解密 → BCrypt 存库。

### 1.2 目标端现状（yiti `auth-permission-center`）
- 已具备：`PtUser` 实体（驼峰 + `LocalDateTime` + `Integer` + MyBatis-Plus `@TableId`），`UserMapper extends BaseMapper`（含 `selectByUserId`/`selectByUsername`/`selectByOrgCode`/分页机构用户查询/`updatePassWrongCount`/`updateLockedStatus`），`UserApi`+`UserFacade`（跨模块查询契约），`UserRoleController`（用户-角色绑定 REST）。
- 已具备 `BCryptPasswordEncoder` Bean（`AuthConfig`）、5 层安全管道（`AuthenticationFilter` + `AuthorizationInterceptor` + `@BizAuth` 元数据解析）。
- **缺失**：`UserController`、`UserService`（内部业务层）、用户写操作 Mapper 方法（insert/通用 update/批量 active/inactive/delete/password update）、用户管理类 `PT_RESOURCE` 登记。

### 1.3 范围与边界

**范围内（本次交付）**：
1. 在 `auth-permission-center` 新建 `UserController` + `UserService`。
2. 扩展 `UserMapper` 的写方法（不破坏已有 `selectByXxx`、`updateLockedStatus` 行为）。
3. 新增 `api/dto/` 下用户 CRUD 相关请求/响应 DTO。
4. 在 `PT_RESOURCE` 表登记 11 条新资源，并默认绑定到 `R_SYS_ADMIN`。
5. TDD 单元测试 + 集成测试覆盖。

**明确不在范围内**：
- 不引入 SM2 加密体系（用户决定：现阶段密码处理沿用 yiti BCrypt + 明文传输 + HTTPS 保护，与 yiti 其它模块一致）。
- 不重做用户-角色绑定接口（`UserRoleController` 已交付，13、14 号 xanpd 接口已自然覆盖）。
- 不改 `PT_USER` 表结构（schema 已完备）。
- 不动 `UserApi`/`UserFacade`（跨模块查询契约，本次只增 REST 与内部服务层，不影响跨模块）。

---

## 2. 架构与分层

遵循 yiti `auth-permission-center` 既有分层（参考 `UserRoleController` / `UserRoleService`）：

```
controller/UserController          ←  REST 入口，仅做参数绑定、调用 service、包 ResponseWrapper
   │
service/UserService                ←  业务逻辑：唯一性校验、密码加密、批量循环、审计字段填充
   │
mapper/UserMapper                  ←  MyBatis（含 MyBatis-Plus BaseMapper）
   │
PT_USER (MySQL)
```

跨模块依赖**保持不变**：`UserApi` 仍只暴露查询；本次新增的写能力**不上 Api 层**，仅模块内 `controller → service → mapper` 闭环。

---

## 3. 接口契约（12 个 REST 端点）

URL 基址 `/api/admin/users`（与现有 `UserRoleController` 风格一致，会被 `AuthorizationInterceptor` 拦截并执行 `@BizAuth` 校验）。

| # | URL | Method | `@BizAuth` | 描述 |
|---|---|---|---|---|
| 1 | `/api/admin/users` | GET | `SYS_CONFIG, READ` | 分页列表（条件：username/userchnname/email/remark 模糊 + isEnabled/isLocked 过滤） |
| 2 | `/api/admin/users/{userId}/exists` | GET | `SYS_CONFIG, READ` | 判断用户名是否存在（新增前校验） |
| 3 | `/api/admin/users/{userId}` | GET | `SYS_CONFIG, READ` | 加载单个（响应不含 `pwd`） |
| 4 | `/api/admin/users` | POST | `SYS_CONFIG, CONFIG` | 新增（密码字段 BCrypt 加密后存库） |
| 5 | `/api/admin/users/{userId}` | PUT | `SYS_CONFIG, CONFIG` | 修改用户（不改密码） |
| 6 | `/api/admin/users/{ids}` | DELETE | `SYS_CONFIG, CONFIG` | 批量删除，`{ids}` 为逗号分隔 PathVariable（沿用 xanpd 风格） |
| 7 | `/api/admin/users/{ids}/reset` | PUT | `SYS_CONFIG, PERMISSION_CHANGE` | 批量重置密码为默认值（`auth.user.default-password` 配置） |
| 8 | `/api/admin/users/me/password` | PUT | `SYS_CONFIG, PERMISSION_CHANGE` | 当前用户改密（旧/新密码均明文传输） |
| 9 | `/api/admin/users/{ids}/active` | PUT | `SYS_CONFIG, PERMISSION_CHANGE` | 批量启用（`ISENABLED=0`，按 PtUser 注释 0 为启用） |
| 10 | `/api/admin/users/{ids}/inactive` | PUT | `SYS_CONFIG, PERMISSION_CHANGE` | 批量禁用（`ISENABLED=1`） |
| 11 | `/api/admin/users/{ids}/lock` | PUT | `SYS_CONFIG, PERMISSION_CHANGE` | 批量锁定（`ISLOCKED=1`） |
| 12 | `/api/admin/users/{ids}/unlock` | PUT | `SYS_CONFIG, PERMISSION_CHANGE` | 批量解锁（`ISLOCKED=0`） |

### 3.1 统一响应

所有接口返回 `ResponseWrapper<T>`：
- 列表：`ResponseWrapper<PageResult<UserListItemRespDTO>>`
- 单个：`ResponseWrapper<UserDetailRespDTO>`
- 布尔：`ResponseWrapper<Boolean>`
- 批量受影响行数：`ResponseWrapper<Integer>`
- 无返回：`ResponseWrapper<Void>`

### 3.2 错误码

复用 / 新增 `AuthErrorCode`（实际枚举：404 区段已用 40401~40405；409 区段已用 40901~40903；400 区段尚未占用）：

| 错误码 | 名称 | 状态 | 触发场景 |
|---|---|---|---|
| `AUTH-40403` | `USER_NOT_FOUND` | 复用已有 | GET/{userId}、PUT/{userId}、修改密码时用户不存在 |
| `AUTH-40904` | `USER_ID_DUPLICATE` | **新增** | POST 时 userId 已存在 |
| `AUTH-40905` | `USERNAME_DUPLICATE` | **新增** | POST 时 username 已存在 |
| `AUTH-40001` | `OLD_PASSWORD_MISMATCH` | **新增**（400 区） | 当前用户改密旧密码不符 |
| `AUTH-40002` | `INVALID_USER_IDS` | **新增**（400 区） | 批量接口 ids 为空、含空串或超 `max-batch-ids` |

异常类型统一抛 `com.bank.branch.platform.common.web.exception.BizException`（参考 `UserRoleService.bindRoles`）。

### 3.3 请求/响应 DTO 字段

**`UserQueryReqDTO`**: `username`, `userchnname`, `email`, `remark`, `isEnabled`, `isLocked`, `pageNum`(default 1), `pageSize`(default 20, max 100)

**`UserCreateReqDTO`**:
- `@NotBlank userId`（工号，业务赋值）
- `@NotBlank username`（登录名）
- `@NotBlank userchnname`
- `email`（`@Email` 可选）
- `@NotBlank initialPassword`（明文，service 层 BCrypt 后存库；HTTPS 保护）
- `remark`

**`UserUpdateReqDTO`**: `username`, `userchnname`, `email`, `remark`（任一非空即更新；不含密码字段）

**`UserDetailRespDTO`** / **`UserListItemRespDTO`**:
- 字段：`userId`、`username`、`userchnname`、`email`、`remark`、`isExpired`、`isLocked`、`isEnabled`、`passWrongCount`、`createTime`、`createAuthor`、`updateTime`、`updateAuthor`、`pwdUpdateTime`
- **不含 `pwd`**（密码字段一律不返回前端）

**`ChangeMyPasswordReqDTO`**:
- `@NotBlank oldPassword`（明文）
- `@NotBlank newPassword`（明文，service 层校验复杂度后 BCrypt 存库）

---

## 4. Mapper 扩展

`UserMapper.java` 新增以下方法（**保留**已有所有方法，纯增量）：

```java
/** 条件分页列表 */
List<PtUser> selectByQuery(@Param("q") UserQueryReqDTO q,
                           @Param("offset") int offset,
                           @Param("limit") int limit);

/** 条件总数 */
long countByQuery(@Param("q") UserQueryReqDTO q);

/** 用户名是否存在 */
int countByUsername(@Param("username") String username);

/** 修改启用状态 */
int updateActiveStatus(@Param("userId") String userId,
                       @Param("isEnabled") int isEnabled,
                       @Param("updateAuthor") String updateAuthor);

/** 修改密码（同时更新 pwdUpdateTime / passWrongCount=0） */
int updatePassword(@Param("userId") String userId,
                   @Param("pwd") String bcryptHash,
                   @Param("updateAuthor") String updateAuthor);

/** 批量删除（物理删除，PT_USER 不存在 is_deleted 列） */
int deleteByUserIds(@Param("userIds") List<String> userIds);
```

`UserMapper.xml` 新增对应 SQL；查询统一引用 `BASE_COLUMNS`。删除采用 `<foreach>`。

**注**：`updateLockedStatus` 已有，但当前签名是 `(userId, locked)` 单条；批量锁/解锁在 service 层循环调用现有方法即可，不再加新方法（避免破坏 V1.8 既有依赖）。

---

## 5. Service 层

`service/UserService.java`（**新增，单一具体类**，参考 `UserRoleService`：`@Service` + `@RequiredArgsConstructor` + `@Slf4j`，不分离接口/实现）。方法签名（节选）：

```java
PageResult<UserListItemRespDTO> pageUsers(UserQueryReqDTO q);
boolean existsByUsername(String username);
UserDetailRespDTO getById(String userId);                   // 找不到抛 USER_NOT_FOUND
void create(UserCreateReqDTO req, String operator);         // 抛 USER_ALREADY_EXISTS
void update(String userId, UserUpdateReqDTO req, String operator);
int deleteByIds(List<String> userIds);
int resetPassword(List<String> userIds, String operator);   // 重置为默认密码（BCrypt）
void changeMyPassword(String currentUserId, ChangeMyPasswordReqDTO req);  // 抛 OLD_PASSWORD_MISMATCH
int batchActivate(List<String> userIds, String operator);
int batchInactivate(List<String> userIds, String operator);
int batchLock(List<String> userIds, String operator);
int batchUnlock(List<String> userIds, String operator);
```

### 5.1 关键实现要点
- **操作人 operator**：由 controller 通过 `CurrentUserApi.getCurrentEmpId()` 取得后传入 service（不在 service 内直接调用 ThreadLocal，便于单元测试）。
- **密码加密**：调用注入的 `BCryptPasswordEncoder` Bean（已在 `AuthConfig` 注册）。
- **审计字段**：`create` 设置 `createAuthor`/`createTime`/`pwdUpdateTime`；`update` 设置 `updateAuthor`/`updateTime`；`updatePassword` 同时刷新 `pwdUpdateTime` 并清零 `passWrongCount`。
- **默认密码配置**：新增 `auth.user.default-password`（`application.yml` 默认值 `Branch@2026`），通过 `@Value` 注入。
- **批量循环**：当前接口入参 ids 上限 50（在 controller 层用 `@Size(max=50)` 限制），超出返回 `400 INVALID_USER_IDS`。
- **不存在的 userId**：循环时跳过并累加计数，最终返回受影响数；不抛异常（与 xanpd 行为一致）。

---

## 6. Controller 层

`controller/UserController.java`（新增）参考 `UserRoleController` 写法：
- `@RestController` + `@RequestMapping("/api/admin/users")` + `@RequiredArgsConstructor` + `@Slf4j`
- 类级 `@Tag(name="用户管理", description="用户 CRUD / 状态变更 / 密码")`
- 每个方法 `@Operation`、`@BizAuth`、参数 `@Valid`
- 写操作通过 `CurrentUserApi.getCurrentEmpId()` 取操作人后调 service
- 入参/出参/traceId/耗时由 `common-trace` AOP 自动记录（参考 `docs/common-dev-guide.md`）

### 6.1 批量 PathVariable 解析
Spring MVC 对 `String[]` 的 `@PathVariable` 自动按逗号分割，沿用 xanpd 行为：
```java
@PutMapping("/{ids}/active")
public ResponseWrapper<Integer> active(@PathVariable("ids") String[] ids) { ... }
```

---

## 7. PT_RESOURCE 登记

新增 11 条记录（用户-角色相关已由 UserRoleController 注册过，本次不重复）。SQL 文件路径：

```
docs/superpowers/sql/2026-05-18-v1.14-user-management-pt-resource.sql
```

每条字段（参考现有 `PT_RESOURCE` 已有数据格式）：
- `RESOURCE_ID`：`R_USER_xxx`（如 `R_USER_LIST` / `R_USER_CREATE` / `R_USER_RESET_PWD` 等）
- `URL_PATTERN`：与接口 URL 对应
- `HTTP_METHOD`：GET/POST/PUT/DELETE
- `BIZ_TYPE` = `SYS_CONFIG`
- `BIZ_ACTION` = `READ` / `CONFIG` / `PERMISSION_CHANGE`
- `REMARK`：中文描述

同时新增 `PT_ROLE_RESOURCE` 11 条，将新资源绑定到 `R_SYS_ADMIN`（与 yiti 现有惯例一致）。

> 注：在 plan 阶段会比对 `docs/modules/auth-permission-center/` 下既有 DDL/DML 风格，确保字段顺序与现有 SQL 对齐。

---

## 8. TDD 测试策略

### 8.1 单元测试（surefire `*Test.java`）
- `UserServiceTest`：mock `UserMapper` + `BCryptPasswordEncoder`，覆盖 12 个 service 方法的成功路径 + 关键失败分支（USER_NOT_FOUND、USER_ALREADY_EXISTS、OLD_PASSWORD_MISMATCH、空 ids、批量部分失败）。
- `UserMapperTest`：可选，主要靠集成测试覆盖 SQL。

### 8.2 集成测试（failsafe `*IT.java`）
- `UserControllerIT`：`@SpringBootTest` + `MockMvc`，按 yiti 测试库一统约定使用 `onepl_test_bootstrap`。
  - 每个端点至少 1 个 happy path + 1 个鉴权/参数错误。
  - 鉴权场景通过既有 `@WithMockCurrentUser`（如已存在）或与 `UserRoleControllerTest` 同款 Mock 注入 ThreadLocal。
  - 数据准备：`@Sql` 注入 fixture（与 yiti 现有 controller test 风格保持一致）。

预计用例数 ≥ 30。

### 8.3 红绿循环
按 CLAUDE.md 红线先写失败用例，再写实现。详细顺序在 writing-plans 阶段拆为子任务。

---

## 9. 配置项

`auth-permission-center/src/main/resources/application*.yml`（如已存在则合并）新增：
```yaml
auth:
  user:
    default-password: Branch@2026   # 重置密码默认值（生产应在外部覆盖）
    max-batch-ids: 50               # 批量接口 ids 上限
```

`@ConfigurationProperties("auth.user")` 绑定 `AuthUserProperties` Bean，注入到 `UserService`。

---

## 10. 风险与权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| 移植期间引入对 `PT_USER` 写操作可能影响既有登录流（passWrongCount 字段并发） | 高 | 不复用 `updateById` 整表更新，分字段独立 update（已有 `updatePassWrongCount`/`updateLockedStatus` 即此设计），新增的 `updateActiveStatus`、`updatePassword` 同样仅更目标字段 |
| 默认密码硬编码到配置文件 | 中 | `application.yml` 给的是默认；生产环境通过外部 `application-prod.yml` 或环境变量覆盖；并在文档注明强制要求 |
| 物理删除 `PT_USER` 行可能破坏审计/外键 | 中 | xanpd 行为即物理删除，本次 1:1 移植；若有 `PT_USER_ROLE`/`EXT_USER_ORG` 外键依赖，service 层先清依赖表（与 `UserRoleService` 行为一致）— 在 plan 阶段确认外键并补级联 |
| 不引入 SM2 后，前端 SM2 加密的旧客户端无法直接复用 | 中 | 由用户明确决策，本次按 yiti 现状执行；前端配合改造由前端项目处理 |
| 批量循环逐条调用 `updateLockedStatus` 性能 | 低 | 50 上限内可接受；如需可在 plan 阶段补 `<foreach>` 单条 SQL，但不进 V1.14 范围 |

---

## 11. 交付物清单

**新增 Java 类**（约 11 个）：
- `controller/UserController.java`
- `service/UserService.java`（单一具体类，参考 `UserRoleService`）
- `api/dto/UserQueryReqDTO.java`
- `api/dto/UserCreateReqDTO.java`
- `api/dto/UserUpdateReqDTO.java`
- `api/dto/ChangeMyPasswordReqDTO.java`
- `api/dto/UserDetailRespDTO.java`
- `api/dto/UserListItemRespDTO.java`
- `config/AuthUserProperties.java`
- `enums/AuthErrorCode.java`（**修改**：追加新错误码）
- `mapper/UserMapper.java`（**修改**：追加 6 个方法）

**修改资源**：
- `mapper/auth/UserMapper.xml` —— 追加 SQL 片段
- `application.yml` —— 追加 `auth.user.*` 配置
- 可能修改 `bootstrap/src/test/resources/application-test.yml` 以镜像配置

**SQL 脚本**：
- `docs/superpowers/sql/2026-05-18-v1.14-user-management-pt-resource.sql`

**测试**：
- `UserServiceTest.java`（surefire）
- `UserControllerIT.java`（failsafe）
- 测试 fixture SQL（如需）

---

## 12. 验收标准（DoD）

1. 12 个 REST 接口在 Knife4j (`/doc.html`) 可见且 schema 正确
2. `mvn clean install -DskipTests` 通过
3. `mvn test -pl auth-permission-center` surefire 全绿
4. `mvn verify -pl auth-permission-center` failsafe 全绿
5. `mvn verify -pl bootstrap` 含本模块 IT 全绿（按 yiti V1.13 # 1 测试库一统约定）
6. `PT_RESOURCE` 11 条记录入库 + 默认绑定 `R_SYS_ADMIN`
7. 所有方法/字段有中文注释（CLAUDE.md 红线）
8. 无 SM2 / bouncycastle 新增依赖（CLAUDE.md 边界确认）
9. `UserApi`/`UserFacade` 对外契约未变化（跨模块兼容性零回归）
10. 本设计文档已 commit 到 git

---

## 13. 后续（不在本次范围）

- 接口性能压测与 `BizScope` 数据范围集成（用户管理是否要按 `orgCode` 限定可见范围）
- SM2 加密接入（如未来安全合规要求恢复）
- 操作审计日志（接入 yiti 治理中心审计表）
