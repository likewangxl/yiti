# AUTH 认证授权中心 -- 对外 API 契约

> 本文档定义 AUTH 模块提供给其他业务模块调用的 Java 接口契约。
> 所有 Api 接口在模块化单体中为本地方法调用（Spring Bean 注入），无 RPC 开销。
> 所有 Api 方法都是同步强依赖（S）。

---

## 1. AuthApi -- 认证服务API

```java
package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.common.security.context.CurrentUserContext;

/**
 * 认证服务对外API
 * 提供认证状态检查与Session管理
 */
public interface AuthApi {

    /**
     * 获取当前登录用户上下文（从Session/Token解析）
     *
     * @return CurrentUserContext 当前用户上下文，包含empId/角色/机构等
     * @throws AuthException 未登录时抛出AUTH-40105
     */
    CurrentUserContext getCurrentUser();

    /**
     * 判断当前请求是否已认证
     *
     * @return true=已认证, false=未认证
     */
    boolean isAuthenticated();

    /**
     * 强制使指定用户的Session失效
     * 用于权限变更后踢出用户，迫使其重新登录获取新权限
     *
     * @param empId 用户工号
     * @throws IllegalArgumentException empId为空时抛出
     */
    void invalidateSession(String empId);
}
```

**调用约束：**
- `getCurrentUser()` 在未认证时抛异常，调用方需确保在认证后的链路中调用
- `invalidateSession()` 应在权限变更事务提交后调用
- 频率预估：`isAuthenticated()` 每次请求调用1次，高频

---

## 2. CurrentUserApi -- 当前用户上下文API

```java
package com.bank.branch.platform.auth.api;

import java.util.Set;

/**
 * 当前用户上下文对外API
 * 提供当前登录用户的身份、角色、机构等信息
 * 所有方法均从ThreadLocal获取，无数据库IO
 */
public interface CurrentUserApi {

    /**
     * 获取完整用户上下文（含角色、机构、候选组）
     *
     * @return CurrentUserContext 完整上下文
     * @throws AuthException 未登录时抛出
     */
    com.bank.branch.platform.common.security.context.CurrentUserContext getCurrentUserContext();

    /**
     * 获取当前用户工号
     * 口径统一为 PT_USER.USER_ID
     *
     * @return 工号字符串
     * @throws AuthException 未登录时抛出
     */
    String getCurrentEmpId();

    /**
     * 获取当前用户主机构编码
     * 来源于 EXT_USER_ORG（V1单用户单主机构）
     *
     * @return 机构编码字符串
     * @throws AuthException 未登录时抛出
     */
    String getCurrentOrgCode();

    /**
     * 获取当前用户角色ID集合
     * 来源于 PT_USER_ROLE
     *
     * @return 角色ID集合，不可为null（无角色返回空Set）
     */
    Set<String> getCurrentRoleIds();

    /**
     * 获取当前用户角色编码集合
     * 来源于 PT_ROLE.ROLE_CODE
     *
     * @return 角色编码集合
     */
    Set<String> getCurrentRoleCodes();

    /**
     * 获取当前用户候选组集合（用于工作流待办查询）
     * 按当前用户现有角色/岗位/组织边界解析
     * 不回放历史组身份
     *
     * @return 候选组Key集合，格式如 "ROLE:CUST_MANAGER", "ORG:001001"
     */
    Set<String> getCurrentCandidateGroupKeys();

    /**
     * 判断当前用户是否系统管理员
     * 判定标准：角色中包含 ROLE_CODE = 'SYS_ADMIN'
     *
     * @return true=系统管理员
     */
    boolean isSystemAdmin();
}
```

**调用约束：**
- 所有方法从 ThreadLocal 获取，无数据库 IO，性能极高
- 必须在 AuthenticationFilter 之后调用（否则 ThreadLocal 为空）
- `getCurrentCandidateGroupKeys()` 返回格式需与 Flowable candidateGroups 对齐

---

## 3. ResourceApi -- 资源匹配与权限API

```java
package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.ResourceDTO;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 资源匹配与权限对外API
 * 提供URL-资源匹配、权限校验、用户资源列表等功能
 */
public interface ResourceApi {

    /**
     * 根据 URL + Method 匹配资源（用于鉴权拦截器）
     * 支持 Ant 风格通配符匹配
     *
     * @param url    请求URL（如 "/api/leads/123"）
     * @param method 请求方法（如 "GET"）
     * @return 匹配到的资源信息，未匹配返回 Optional.empty()
     */
    Optional<ResourceDTO> matchResource(String url, String method);

    /**
     * 判断用户是否有指定资源的权限
     * 查询路径：PT_USER_ROLE -> PT_ROLE_RESOURCE -> RESOURCE_ID
     *
     * @param empId      用户工号
     * @param resourceId 资源ID（PT_RESOURCE.RESOURCE_ID）
     * @return true=有权限
     */
    boolean hasResourcePermission(String empId, String resourceId);

    /**
     * 获取用户所有有权限的资源列表（用于前端菜单/按钮渲染）
     * 查询路径：PT_USER_ROLE -> PT_ROLE_RESOURCE -> PT_RESOURCE
     *
     * @param empId 用户工号
     * @return 资源DTO列表（含菜单和非菜单资源）
     */
    List<ResourceDTO> listUserResources(String empId);

    /**
     * 获取用户所有有权限的资源URL集合（用于前端快速判定）
     * 性能优化版，只返回URL字符串
     *
     * @param empId 用户工号
     * @return 资源URL集合
     */
    Set<String> listUserResourceUrls(String empId);
}
```

**调用约束：**
- `matchResource()` 每次请求调用1次，高频调用，使用 Redis 缓存（TTL=5min）
- `hasResourcePermission()` 从缓存读取，无直接DB查询
- `listUserResources()` 在登录和 `current-user` 接口中调用，可缓存结果
- `listUserResourceUrls()` 在前端权限初始化时调用

---

## 4. BizScopeApi -- BizType数据范围API

```java
package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import java.util.Map;

/**
 * BizType数据范围对外API
 * 提供数据范围解析、权限校验等核心鉴权能力
 */
public interface BizScopeApi {

    /**
     * 解析用户对指定BizType的最终数据范围（多角色取并集）
     *
     * 合并策略优先级：ALL > ORG_SUBTREE > ORG > SELF_CREATED/SELF/SELF_ASSIGNED/WORKFLOW_PARTICIPANT
     *
     * @param empId   用户工号
     * @param bizType 业务类型
     * @return 合并后的数据范围类型；无配置时返回null（Fail Close则抛异常）
     * @throws PermissionDeniedException 用户角色未配置该BizType的数据范围时
     */
    DataScopeType resolveScope(String empId, BizType bizType);

    /**
     * 构建完整的数据范围上下文（含组织子树等）
     *
     * 上下文内容：
     * - scopeType: 数据范围类型
     * - empId: 当前用户工号
     * - orgCode: 当前用户主机构编码
     * - orgSubtreeCodes: 机构子树编码集合（ORG_SUBTREE时填充）
     * - action: 当前业务动作
     *
     * @param empId   用户工号
     * @param bizType 业务类型
     * @param action  业务动作（READ/WRITE/APPROVE等）
     * @return 完整数据范围上下文
     */
    DataScopeContext buildScopeContext(String empId, BizType bizType, BizAction action);

    /**
     * 校验写操作权限（基于实体归属）
     *
     * 校验逻辑：
     * 1. 获取用户对该BizType的DATA_SCOPE
     * 2. 按DATA_SCOPE类型判定：
     *    - SELF_CREATED: entityCreatedBy == empId
     *    - ORG: entityOwnerOrgId == 用户主机构
     *    - ORG_SUBTREE: entityOwnerOrgId IN 用户机构子树
     *    - ALL: 直接通过
     *    - SELF_ASSIGNED: assigneeEmpId == empId (需额外参数)
     *    - WORKFLOW_PARTICIPANT: 需结合 workflow-center 的参与者判定能力（当前对外 Java 契约待补齐）
     *
     * @param empId             用户工号
     * @param bizType           业务类型
     * @param entityOwnerOrgId  实体归属机构编码
     * @param entityCreatedBy   实体创建人工号
     * @return true=有写权限
     */
    boolean checkWritePermission(String empId, BizType bizType,
                                  String entityOwnerOrgId, String entityCreatedBy);

    /**
     * 获取用户所有BizType的数据范围映射
     *
     * @param empId 用户工号
     * @return BizType -> DataScopeType 映射（已合并多角色）
     */
    Map<BizType, DataScopeType> getUserBizScopes(String empId);
}
```

**调用约束：**
- `resolveScope()` 每次请求调用1次，使用 Redis 缓存（TTL=5min）
- `buildScopeContext()` 在鉴权拦截器中调用，结果放入 ThreadLocal
- `checkWritePermission()` 在 Service 层写操作前调用，不可省略
- `getUserBizScopes()` 在登录和 `current-user` 接口中调用

---

## 5. OrgApi -- 组织机构API

```java
package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.OrgDTO;
import java.util.List;
import java.util.Set;

/**
 * 组织机构对外API
 * 提供机构查询、子树计算等功能
 */
public interface OrgApi {

    /**
     * 获取机构信息
     *
     * @param orgCode 机构编码
     * @return 机构DTO
     * @throws BizException AUTH-40404 机构不存在
     */
    OrgDTO getOrg(String orgCode);

    /**
     * 获取机构子树（含自身）
     * 使用递归查询 EXT_ORG_INFO，返回扁平列表
     *
     * @param orgCode 根机构编码
     * @return 子树中所有机构的DTO列表（含自身）
     */
    List<OrgDTO> getOrgSubtree(String orgCode);

    /**
     * 获取机构子树编码集合（高频调用，建议缓存）
     * 用于 SQL IN 条件构建
     *
     * @param orgCode 根机构编码
     * @return 子树中所有机构编码的Set（含自身）
     */
    Set<String> getOrgSubtreeCodes(String orgCode);

    /**
     * 获取用户主机构信息
     * 查询路径：EXT_USER_ORG -> EXT_ORG_INFO
     *
     * @param empId 用户工号
     * @return 用户主机构DTO
     * @throws BizException AUTH-40403 用户不存在或无机构
     */
    OrgDTO getUserMainOrg(String empId);

    /**
     * 模糊搜索机构
     *
     * @param keyword 搜索关键字（机构名称模糊匹配）
     * @param limit   最大返回条数（建议不超过50）
     * @return 匹配的机构列表
     */
    List<OrgDTO> searchOrgs(String keyword, int limit);
}
```

**调用约束：**
- `getOrgSubtreeCodes()` 高频调用，必须缓存（Redis key: `auth:org-subtree:{orgCode}`，TTL=5min）
- `getOrg()` 和 `getUserMainOrg()` 建议缓存
- `searchOrgs()` 直接查库，不缓存（实时性要求高）

---

## 6. DTO 定义

### 6.1 CurrentUserContext

定义在 `common-security` 模块中：

```java
package com.bank.branch.platform.common.security.context;

import java.util.Set;

/**
 * 当前用户上下文（不可变对象）
 *
 * @param empId              用户工号（PT_USER.USER_ID）
 * @param mainOrgCode        主机构编码（EXT_USER_ORG.ORG_CODE）
 * @param roleIds            角色ID集合
 * @param roleCodes          角色编码集合
 * @param candidateGroupKeys 候选组Key集合（用于工作流）
 * @param systemAdmin        是否系统管理员
 */
public record CurrentUserContext(
    String empId,
    String mainOrgCode,
    Set<String> roleIds,
    Set<String> roleCodes,
    Set<String> candidateGroupKeys,
    boolean systemAdmin
) {}
```

### 6.2 ResourceDTO

```java
package com.bank.branch.platform.auth.api.dto;

/**
 * 资源信息DTO
 */
public class ResourceDTO {
    /** 资源ID */
    private String resourceId;
    /** 资源URL */
    private String resourceUrl;
    /** 请求方法（GET/POST/PUT/DELETE/*） */
    private String resourceMethod;
    /** 菜单名称 */
    private String menuName;
    /** 是否菜单（0是/1不是） */
    private Integer isMenu;
    /** 上级资源ID */
    private String parentResourceId;
    /** 状态（0启用/1不启用） */
    private Integer status;
    // getter/setter 省略
}
```

### 6.3 OrgDTO

```java
package com.bank.branch.platform.auth.api.dto;

/**
 * 机构信息DTO
 */
public class OrgDTO {
    /** 机构编码 */
    private String orgCode;
    /** 机构名称 */
    private String orgName;
    /** 机构等级（1总行/2分行/3支行） */
    private Integer orgLevel;
    /** 上级机构编码 */
    private String parentOrgCode;
    /** 状态（0启用/1删除） */
    private Integer organState;
    // getter/setter 省略
}
```

### 6.4 BizScopeDTO

```java
package com.bank.branch.platform.auth.api.dto;

/**
 * 角色业务范围配置DTO
 */
public class BizScopeDTO {
    /** 主键ID */
    private String id;
    /** 角色ID */
    private String roleId;
    /** 业务类型（BizType枚举值） */
    private String bizType;
    /** 数据范围（DataScopeType枚举值） */
    private String dataScope;
    /** 状态（0可用/1不可用） */
    private Integer recordStatus;
    // getter/setter 省略
}
```

### 6.5 DataScopeContext

```java
package com.bank.branch.platform.auth.api.dto;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import java.util.Set;

/**
 * 数据范围上下文（ThreadLocal传递）
 *
 * @param scopeType       数据范围类型
 * @param empId           当前用户工号
 * @param orgCode         当前用户主机构编码
 * @param orgSubtreeCodes 机构子树编码集合（ORG_SUBTREE时填充，否则为空）
 * @param bizType         当前业务类型
 * @param action          当前业务动作
 */
public record DataScopeContext(
    DataScopeType scopeType,
    String empId,
    String orgCode,
    Set<String> orgSubtreeCodes,
    BizType bizType,
    BizAction action
) {}
```

### 6.6 CheckPermissionResultDTO

```java
package com.bank.branch.platform.auth.api.dto;

/**
 * 权限校验结果DTO
 */
public class CheckPermissionResultDTO {
    /** 是否允许 */
    private Boolean allowed;
    /** RBAC是否通过 */
    private Boolean rbacPassed;
    /** 数据范围是否通过 */
    private Boolean scopePassed;
    /** 命中的DataScopeType */
    private String scope;
    /** 拒绝原因 */
    private String denyReason;
    // getter/setter 省略
}
```

---

## 7. 调用约束

### 7.1 调用方式

| 约束 | 说明 |
|:---|:---|
| 调用方式 | 本地方法调用（Spring Bean 注入），非 RPC |
| 依赖注入 | 业务模块在 Service/Facade 中通过 `@Autowired` 或构造函数注入 Api 接口 |
| 线程安全 | 所有 Api 方法线程安全 |
| 事务传播 | Api 方法参与调用方的事务（REQUIRED 传播） |

### 7.2 缓存策略

| 方法 | 是否缓存 | 缓存 TTL | 缓存 Key |
|:---|:---|:---|:---|
| `ResourceApi.matchResource()` | 是 | 5min | `auth:resource:all` |
| `ResourceApi.hasResourcePermission()` | 是 | 5min | `auth:role-resource:{roleId}` |
| `BizScopeApi.resolveScope()` | 是 | 5min | `auth:biz-scope:{roleId}` |
| `OrgApi.getOrgSubtreeCodes()` | 是 | 5min | `auth:org-subtree:{orgCode}` |
| `OrgApi.getOrg()` | 是 | 10min | `auth:org:{orgCode}` |
| `CurrentUserApi.*` | 否（ThreadLocal） | - | - |

### 7.3 异常约定

| 异常类型 | 说明 | 调用方处理建议 |
|:---|:---|:---|
| `AuthException` | 认证失败（未登录/Session过期） | 重定向到登录页 |
| `PermissionDeniedException` | 权限拒绝 | 返回403 |
| `BizException` | 业务异常（资源不存在等） | 按错误码处理 |
| `RuntimeException` | 系统异常 | 返回500 |

---

## 8. 领域事件

### 8.1 governance.permission-cache.invalidated.v1

**发布时机**：角色/资源/BizScope/用户角色绑定变更，事务提交后

**发布方式**：Spring ApplicationEvent（模块化单体内部事件）

**消费方**：所有使用了权限缓存的模块

**事件类定义**：

```java
package com.bank.branch.platform.auth.api.event;

import java.util.Set;

/**
 * 权限缓存失效事件
 * 事务提交后发布，通知消费方清除本地缓存
 */
public class PermissionCacheInvalidatedEvent {
    /** 变更类型 */
    private String changeType;  // ROLE / RESOURCE / BIZ_SCOPE / USER_ROLE / ROLE_RESOURCE
    /** 受影响的角色ID集合 */
    private Set<String> affectedRoleIds;
    /** 受影响的用户ID集合（可选，当能确定影响的用户时） */
    private Set<String> affectedUserIds;
    /** 操作人工号 */
    private String operator;
    /** 变更原因 */
    private String reason;
    /** 事件ID（用于消费方去重） */
    private String eventId;
    /** 事件时间戳 */
    private Long timestamp;
    // getter/setter 省略
}
```

**Payload 示例**：

```json
{
  "changeType": "ROLE_RESOURCE",
  "affectedRoleIds": ["R_RM"],
  "affectedUserIds": ["E10001", "E10002"],
  "operator": "E99999",
  "reason": "更新客户经理角色的LEAD数据范围",
  "eventId": "evt_20260401_001",
  "timestamp": 1743494400000
}
```

**消费方处理**：
- 按 `changeType` 和 `affectedRoleIds` 清除对应的 Redis 缓存 Key
- 按 `eventId` 做幂等去重，避免重复处理
