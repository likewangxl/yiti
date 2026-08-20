# AUTH 认证授权中心 -- 对外 API 契约

> 本文档定义 AUTH 模块提供给其他业务模块调用的 Java 接口契约。
> 所有 Api 接口在模块化单体中为本地方法调用（Spring Bean 注入），无 RPC 开销。
> 所有 Api 方法都是同步强依赖（S）。

> **2026-07-19 回填说明**：2026-04-14 版遗漏 `RoleApi`（1 个方法）与 `UserApi`（全部 14 个方法）两个已在源码 `api/` 目录落地的接口，均按源码 Javadoc 逐字核实补齐为「6. RoleApi」「7. UserApi」，原「6. DTO 定义」「7. 调用约束」「8. 领域事件」相应顺延为「8」「9」「10」；同时补齐 `UserApi` 引用的 `UserDTO`（新增 8.7）。顺带在「9.2 缓存策略」加注：该表所述 Redis TTL/Key 为历史设计，项目已于 2026-05-20 去 Redis 改直查库，详情见 `auth-permission-center/AGENTS.md`（表内容本身未删改，仅作提醒，全面重写留待后续专项更新）。

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

## 6. RoleApi -- 角色查询API（2026-07-19 补齐）

```java
package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.RoleRespDTO;

import java.util.List;

/**
 * 角色查询对外 API（供业务模块只读消费，如 KPI 方案的「员工角色范围」下拉）.
 *
 * <p>角色为系统级配置，结果不大，按需实时查询。</p>
 */
public interface RoleApi {

    /**
     * 查询全部「可用」角色（RECORD_STATUS=0），按角色名称（roleChName）升序排序.
     *
     * @return 可用角色列表（按名称排序；无可用角色返回空列表）
     */
    List<RoleRespDTO> listEnabledRoles();
}
```

**调用约束：**
- 直接查库（委托 `RoleMapper.selectAllFiltered`），**不走缓存**；角色为低频变更的系统级配置，结果集通常 < 100 行，无需缓存层
- 无会话方法，任意模块 Service/Facade 层可直接注入调用，无需登录态
- `RoleRespDTO` 字段结构见 `03-接口设计与报文.md` B.1

---

## 7. UserApi -- 用户信息对外API（2026-07-19 补齐）

```java
package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户信息对外API
 * <p>
 * 提供用户姓名、用户信息查询等能力。
 * </p>
 */
public interface UserApi {

    /**
     * 根据工号查询用户信息
     *
     * @param empId 员工ID
     * @return 用户DTO，不存在时返回 null
     */
    UserDTO getUserByEmpId(String empId);

    /**
     * 根据工号获取用户姓名
     *
     * @param empId 员工ID
     * @return 用户姓名，不存在时返回 null
     */
    String getUserName(String empId);

    /**
     * 批量获取用户信息
     *
     * @param empIds 员工ID列表
     * @return 用户DTO列表
     */
    List<UserDTO> getUserByEmpIds(List<String> empIds);

    /**
     * 查询任意员工的角色编码集合（roleCode，如 R_RM / R_ADMIN / R_BACK_TECH）。
     * <p>
     * 与 {@code CurrentUserApi.getCurrentRoleCodes()} 区别：本方法可查任意员工，需要 DB IO。
     * 跨模块业务校验场景使用，如客户认领转交时校验接收人角色（CUST-40306）。
     * empId 为空或不存在时返回空集合，永不返回 null。
     * </p>
     * <p>
     * <b>Performance note (P1C 2026-04-29)</b>：本方法当前 <b>不走缓存</b>，
     * 每次调用执行 PT_USER_ROLE JOIN PT_ROLE 查询。auth 模块既有 cache `auth:user-roles:{empId}`
     * 缓存的是 roleIds（非 roleCodes），语义不同无法复用。
     * 高频调用场景请自行做应用层缓存或后续 follow-up 加 PermissionCacheService 支持。
     * </p>
     *
     * @param empId 员工ID（工号）
     * @return 角色编码集合（roleCode），从 PT_USER_ROLE 联 PT_ROLE 查得；员工无角色或不存在时为空集
     */
    Set<String> getUserRoleCodes(String empId);

    /**
     * 按 empId 计算工作流候选组标识集合（带前缀：{@code ROLE:{roleCode}} / {@code USER:{empId}} / {@code ORG:{mainOrgCode}}）。
     * <p>
     * 与 {@code CurrentUserApi.getCurrentCandidateGroupKeys()} 的区别：后者读登录态 ThreadLocal，
     * 只能取「当前登录用户」的候选组；本方法按传入 empId 查库实时计算，<b>不依赖会话上下文</b>，
     * 供 SOAP 网关 / callpu 等<b>无登录态</b>链路按指定员工查询工作流待办的候选可见性使用
     * （否则会触发 AUTH-40105「未登录或会话已过期」）。
     * 口径与 {@code AuthService.login} 构建 candidateGroupKeys 的逻辑保持一致：
     * 每个 roleCode 加 {@code ROLE:} 前缀、固定加一项 {@code USER:{empId}}、有主机构时加 {@code ORG:{mainOrgCode}}。
     * empId 为空时返回空集合，永不返回 null。
     * </p>
     *
     * @param empId 员工ID（工号）
     * @return 候选组标识集合（ROLE:/USER:/ORG: 前缀）；员工无角色/无主机构时仅含可推导项
     */
    Set<String> getCandidateGroupKeys(String empId);

    /**
     * 按 roleCode 查所有启用员工 ID。
     * <p>用于 workflow / portal 等模块把候选组 roleCode 展开成员工列表（如发审批通知）。
     * 仅返启用 (PT_USER.ISENABLED=0) + 未被逻辑删除的角色 (PT_ROLE.RECORD_STATUS=0)。</p>
     *
     * @param roleCode 角色编码（如 BRANCH_HEAD）
     * @return 员工 ID 列表，roleCode 为空或无人时返空 List
     */
    List<String> getEmpIdsByRoleCode(String roleCode);

    /**
     * 按 roleCode + orgCode 查同机构启用员工 ID。
     * <p>用于审批候选人按发起人机构过滤（如机构负责人必须与发起人同机构）。
     * 查询逻辑：PT_USER_ROLE JOIN PT_ROLE JOIN EXT_USER_ORG，
     * 仅返启用 (ISENABLED=0) + 角色有效 (RECORD_STATUS=0) + 机构匹配。</p>
     *
     * @param roleCode 角色编码
     * @param orgCode  机构编码
     * @return 员工 ID 列表，无人时返空 List
     */
    List<String> getEmpIdsByRoleCodeAndOrg(String roleCode, String orgCode);

    /**
     * 按 orgCode 查该机构下全部用户工号（任一角色），供审批人「机构角色」不选角色场景。
     *
     * @param orgCode 机构编码
     * @return 员工 ID 列表，无人或入参空时返空 List
     */
    List<String> getEmpIdsByOrg(String orgCode);

    /**
     * 按 username 批量查用户信息。
     * <p>用于数据湖 Allocater_Id（对应 PT_USER.USERNAME）关联查员工姓名和机构。</p>
     *
     * @param usernames 用户名列表
     * @return 用户DTO列表
     */
    List<UserDTO> getUsersByUsernames(List<String> usernames);

    /**
     * 批量过滤出「确实存在」的用户名（工号），用于大批量存在性校验.
     *
     * <p>与 {@link #getUsersByUsernames(List)} 不同：本方法**只做存在性判断**，单次/分片 IN 查询，
     * 不装配机构等 DTO 信息，适合只需存在性结果的大批量场景。
     *
     * @param usernames 待校验用户名列表（null/空 → 返回空）
     * @return 其中在 PT_USER 中存在的用户名子集（去重，顺序不保证）
     */
    List<String> filterExistingUsernames(List<String> usernames);

    /**
     * 批量查询「用户名 → USER_ID」映射，用于大批量导入时的存在性校验 + 工号归一。
     *
     * <p>与 {@link #getUsersByUsernames(List)} 不同：本方法**只做单次/分片 IN 查询**，
     * 仅取 USERNAME / USER_ID 两列，不装配机构等完整 DTO。{@code getUsersByUsernames}
     * 当前同样使用批量用户查询和批量机构装配，不存在逐用户回调导致的 N+1；本方法的优势是返回字段更少。
     *
     * <p>典型场景：评价任务/分配明细导入时，Excel「员工编号」列填登录用户名，
     * 需一次性校验是否为系统有效员工，并把用户名归一为 USER_ID 存储。
     *
     * @param usernames 待查询用户名列表（null/空 → 返回空 Map）
     * @return 其中在 PT_USER 中存在的「用户名 → USER_ID」映射（去重，仅含存在者）
     */
    Map<String, String> mapUsernamesToEmpId(List<String> usernames);

    /**
     * 批量查询「USER_ID → 用户名(工号 USERNAME)」映射，用于大批量导出时按 USER_ID 反查登录名。
     *
     * <p>与 {@link #getUserByEmpIds(List)} 不同：本方法**只做单次/分片 IN 查询**，
     * 仅取 USER_ID / USERNAME 两列，不装配机构等完整 DTO。{@code getUserByEmpIds}
     * 当前使用 {@code selectByUserIds} 批量查用户，再由 {@code buildUserDtos} 批量查主机构关联和机构名称，
     * 不存在逐 USER_ID 回调 {@code getUserByEmpId} 导致的 N+1；本方法的优势是返回字段更少。
     *
     * <p>典型场景：评价明细 Excel 导出（20 万行）时，去重 USER_ID 后一次性反查工号展示，
     * 由于一个批次的去重人数有限（打分人+被打分人），分片查询次数 ≈ 去重数/1000。
     *
     * @param empIds 待查询 USER_ID 列表（null/空 → 返回空 Map）
     * @return 其中在 PT_USER 中存在的「USER_ID → 用户名」映射（去重，仅含存在者）
     */
    Map<String, String> mapEmpIdsToUsername(List<String> empIds);

    /**
     * 按关键词分页查询用户（工号/登录名/中文名 OR 模糊），供 performance 人员标签列表用。
     *
     * @param keyword  关键词（null/空 不过滤）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页条数（&lt;1 归一为 20，&gt;100 截断为 100）
     * @return 分页用户（empId/username/displayName 已装配，mainOrg 不填充）
     */
    PageResult<UserDTO> pageUsers(String keyword, int pageNo, int pageSize);

    /**
     * 按登录工号与中文姓名查询用户，两个非空条件之间为 AND，各字段均为包含式模糊匹配。
     * <p>用于跨模块先形成候选 USER_ID 集合，再在消费模块自己的数据表内做分页，避免跨模块
     * 直接关联 PT_USER。两个条件均为空时返回空列表，防止误触发无条件全量查询。</p>
     *
     * @param username    登录工号（PT_USER.USERNAME，null/空表示不限制）
     * @param displayName 中文姓名（PT_USER.USERCHNNAME，null/空表示不限制）
     * @return 匹配用户的最小展示信息（empId/username/displayName）；无匹配返回空列表
     */
    List<UserDTO> findUsersByUsernameAndDisplayName(String username, String displayName);

    /**
     * 批量查询多个用户的角色简要列表，避免逐用户 N+1。
     *
     * @param userIds 用户ID（工号）列表
     * @return Map&lt;userId, 角色列表&gt;；入参为空时返回空 Map，无角色的 userId 不在 Map 中
     */
    Map<String, List<RoleSimpleDTO>> getRolesByUserIds(List<String> userIds);
}
```

**调用约束：**
- 全部方法均为「显式传参 empId/username/roleCode 等」的**无会话方法**，不依赖登录态 ThreadLocal；供 `workflow-center` 候选人解析、`customer-marketing-center` 数据导入导出、`red-engine-center` 用户映射筛选、`soap-gateway-center`（SOAP/callpu 网关）等**无登录态**场景直接调用——调用方需自行完成上游鉴权，本模块不做二次校验（与 `auth-permission-center/AGENTS.md` 的模块边界一致）
- `getUserRoleCodes()` 当前**不走缓存**（2026-04-29 性能说明）：每次调用直接 `PT_USER_ROLE JOIN PT_ROLE`；auth 模块既有 `auth:user-roles:{empId}` 缓存的是 roleIds（非 roleCodes），语义不同无法复用；高频场景需调用方自行加应用层缓存
- 批量存在性校验/归一化场景**优先使用** `filterExistingUsernames()` / `mapUsernamesToEmpId()` / `mapEmpIdsToUsername()`，因为它们仅返回存在性或 ID 映射，不装配机构等完整 DTO。`getUsersByUsernames()` / `getUserByEmpIds()` 当前也是批量查用户、批量装配机构，不存在逐用户回调造成的 N+1；需要完整 `UserDTO` 时可直接使用
- `pageUsers()`：`pageNo<1` 归一为 1，`pageSize<1` 归一为 20，`pageSize>100` 截断为 100，与 auth 模块内部分页参数口径一致
- `findUsersByUsernameAndDisplayName()`：`username` 与 `displayName` 先 trim，非空条件分别对
  `PT_USER.USERNAME`/`PT_USER.USERCHNNAME` 做包含式模糊查询，同时传入时用 AND 组合；两者均空时
  直接返回空列表且不访问数据库。返回 DTO 只装配 `empId`/`username`/`displayName`，不装配机构和角色。
- `getRolesByUserIds()`：批量查询避免逐用户 N+1；入参为空返回空 Map，无角色的 userId 不在返回 Map 中（而非补 null）
- `UserDTO` 字段结构见本文档「8. DTO 定义」补充；`RoleSimpleDTO` 字段结构见 `03-接口设计与报文.md` A.1

---

## 8. DTO 定义

### 8.1 CurrentUserContext

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

### 8.2 ResourceDTO

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

### 8.3 OrgDTO

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

### 8.4 BizScopeDTO

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

### 8.5 DataScopeContext

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

### 8.6 CheckPermissionResultDTO

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

### 8.7 UserDTO（2026-07-19 补齐，`UserApi` 主要返回类型）

```java
package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 用户信息 DTO
 */
@Data
public class UserDTO {

    /** 员工ID */
    private String empId;

    /** 登录名 */
    private String username;

    /** 中文姓名 */
    private String displayName;

    /** 主机构编码 */
    private String mainOrgCode;

    /** 主机构名称 */
    private String mainOrgName;

    /** 用户类型（字典 USER_TYPE：1-员工 / 2-虚拟员工） */
    private String userType;

    /** 是否启用（true=启用；映射 PT_USER.ISENABLED==0，反向语义） */
    private Boolean enabled;
}
```

---

## 9. 调用约束

### 9.1 调用方式

| 约束 | 说明 |
|:---|:---|
| 调用方式 | 本地方法调用（Spring Bean 注入），非 RPC |
| 依赖注入 | 业务模块在 Service/Facade 中通过 `@Autowired` 或构造函数注入 Api 接口 |
| 线程安全 | 所有 Api 方法线程安全 |
| 事务传播 | Api 方法参与调用方的事务（REQUIRED 传播） |

### 9.2 缓存策略

> **2026-07-19 核实提醒（超出本次回填范围，留待后续专项更新）**：本表描述的是 Redis Cache-Aside 设计。`auth-permission-center/AGENTS.md` 记录**项目已于 2026-05-20 去 Redis**：`PermissionCacheService` 现直接查库（`PT_USER_ROLE`/`PT_ROLE_RESOURCE` 均主键索引点查，约 0.1ms），`evictXxxCache` 系列方法保留签名但改为 NoOp。下表 TTL/Key 已不代表当前实现，仅作历史设计参考；权限变更后的"缓存失效"现状即为"当次即查最新"，天然 Fail Close。RoleApi/UserApi 全部方法均**不**在下表范围内（详见各自小节"调用约束"）。

| 方法 | 是否缓存（历史设计，现状见上方提醒） | 缓存 TTL | 缓存 Key |
|:---|:---|:---|:---|
| `ResourceApi.matchResource()` | 是 | 5min | `auth:resource:all` |
| `ResourceApi.hasResourcePermission()` | 是 | 5min | `auth:role-resource:{roleId}` |
| `BizScopeApi.resolveScope()` | 是 | 5min | `auth:biz-scope:{roleId}` |
| `OrgApi.getOrgSubtreeCodes()` | 是 | 5min | `auth:org-subtree:{orgCode}` |
| `OrgApi.getOrg()` | 是 | 10min | `auth:org:{orgCode}` |
| `CurrentUserApi.*` | 否（ThreadLocal） | - | - |

### 9.3 异常约定

| 异常类型 | 说明 | 调用方处理建议 |
|:---|:---|:---|
| `AuthException` | 认证失败（未登录/Session过期） | 重定向到登录页 |
| `PermissionDeniedException` | 权限拒绝 | 返回403 |
| `BizException` | 业务异常（资源不存在等） | 按错误码处理 |
| `RuntimeException` | 系统异常 | 返回500 |

---

## 10. 领域事件

### 10.1 governance.permission-cache.invalidated.v1

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

## 11. OrgGroupApi -- 机构画像与命名机构组 API（2026-08-11）

`report-analytics-center` 只能依赖本接口及 `api.dto`，不得访问 auth 的 Mapper、Entity 或 Service。接口实现为本地 Spring Bean 调用。

```java
public interface OrgGroupApi {
    OrgGroupDTO getGroup(String groupCode);
    Set<String> listActiveMemberCodes(String groupCode);
    OrgGroupScopeDTO resolveAuthorizedScope(
            String empId, String groupCode, Collection<String> allowedRoleCodes);
    OrgGroupRoleCheckDTO checkRoleBindings(
            String groupCode, Collection<String> roleCodes);
    Map<String, OrgProfileDTO> getActiveProfiles(Collection<String> orgCodes);
}
```

### 11.1 调用语义

| 方法 | 语义与安全边界 |
|:---|:---|
| `getGroup` | 返回机构组基本信息、直接成员和有效角色编码；绑定表的 `ROLE_ID` 必须先解析为规范化 `ROLE_CODE`，不存在返回 `null` |
| `listActiveMemberCodes` | 返回直接成员与有效 `EXT_ORG_INFO`、ACTIVE 画像的交集；异常返回空集 |
| `resolveAuthorizedScope` | 求员工当前全部有效角色、屏级白名单、组绑定角色三方交集；失败 Fail Close，不拼接不同角色权限 |
| `checkRoleBindings` | 返回有效、未绑定、非法角色编码以及 `satisfiable` 发布校验结果 |
| `getActiveProfiles` | 返回请求机构编码中外部机构有效且画像 ACTIVE 的画像索引 |

`OrgGroupDTO.memberCodes()` 是 `memberOrgCodes` 的只读集合视图，供发布校验等调用方安全读取直接成员；不会展开组织子树。`OrgGroupScopeDTO.deniedReasonCode` 仅用于内部审计/诊断，不得向普通用户暴露角色或绑定细节。组停用、无成员、画像缺失、机构停用和任一查询异常均不能返回全量范围。

`report-analytics-center` 是只读消费者：它只能调用上述 `OrgGroupApi` 并传递/接收规范化 `ROLE_CODE`，不得调用 `AuditLogHandler`、治理 `AuditLogService` 或 auth 的 Mapper/Entity 来补写审计。配置写入接口在 auth 服务事务内自行持久化审计。
