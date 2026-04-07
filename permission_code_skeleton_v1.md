# BizType 权限控制落地代码骨架（SpringBoot 定制框架适配版）

> 本文件为实现参考与代码骨架，不回写 `project_ana.md`。
> 本文件同时承载接口命名规范与 `PT_RESOURCE` 资源清单基线，作为 V1 权限实现与资源登记的统一参考。
>
> 适用前提：
> * RBAC 资源表：`PT_RESOURCE(RESOURCE_URL, RESOURCE_METHOD, ...)`，角色资源：`PT_ROLE_RESOURCE`
> * 用户角色：`PT_USER_ROLE`
> * 用户组织：`EXT_USER_ORG` + `EXT_ORG_INFO`
> * BizType 统一范围：`PT_ROLE_BIZ_SCOPE(ROLE_ID, BIZ_TYPE, DATA_SCOPE, ...)`
>
> 目标：
> 1) 让“按钮权限”落到接口资源（URL+Method）上
> 2) 让“统一 DATA_SCOPE + ActionGuard”以 BizType 为粒度生效
> 3) 把复杂度集中在少数基础设施类，而不是散落在每个业务方法里

---

## 1. 推荐落地点（Filter + Interceptor + 注解）

* `Filter`：负责认证与 UserContext 建立（不做 BizType 逻辑）
* `HandlerInterceptor`：负责授权主流程（RBAC -> BizType -> Action -> Scope -> 审计触发）
* `@BizType/@BizAction` 注解：把“接口归属 BizType/动作”显式声明在 Controller 方法上，避免靠 URL 规则猜

---

## 2. 类职责拆分（建议）

| 组件 | 职责 | 依赖 |
| :--- | :--- | :--- |
| `CurrentUserProvider` | 提供当前登录用户（emp_id、角色列表、org_code 等） | 现有登录态/Token/Session |
| `ResourceMatcher` | 将 `requestPath + method` 匹配到 `PT_RESOURCE`（支持通配则用 Ant/PathPattern） | `PT_RESOURCE` 缓存 |
| `RbacAuthorizer` | 校验当前用户角色是否包含该 `RESOURCE_ID` | `PT_ROLE_RESOURCE` 缓存 |
| `BizMetaResolver` | 解析接口的 BizType 与 BizAction（优先注解，其次资源元数据/规则） | Spring `HandlerMethod` |
| `BizScopeService` | 读取并合并多角色的 BizType 统一范围（DATA_SCOPE） | `PT_ROLE_BIZ_SCOPE` 缓存 |
| `WorkflowParticipantService` | 统一判定流程参与者可读、当前节点可签收/办理/转交 | `biz_process_map` + Flowable + 权限中心 |
| `DataScopeContext` | ThreadLocal 保存本次请求的 BizType/Action/Scope，供 DAO 层取用 | - |
| `DataScopeEnforcer` | 在查询/写入前落地数据范围（MyBatis 插件或 Repository Guard） | 数据访问框架 |
| `AuditService` | 对高危动作写审计（含 reason、过滤条件、行数等） | `audit_log` |

---

## 2.1 `CurrentUserContext` 建议模型

```java
record CurrentUserContext(
    String empId,
    String mainOrgCode,
    Set<String> roleIds,
    Set<String> candidateGroupKeys,
    boolean systemAdmin
) {}
```

约束：
* `empId` 口径统一为 `PT_USER.USER_ID`
* `mainOrgCode` 来源于 `EXT_USER_ORG`
* `candidateGroupKeys` 按当前用户现有角色/岗位/组织边界解析，不回放历史组身份

## 2.2 `ActionGuard` 规则矩阵

| 动作 | 基础判定 | 二次守卫 |
| :--- | :--- | :--- |
| `READ/LIST` | 命中 `DATA_SCOPE` | 轻量存在性校验 |
| `EXPORT` | 命中 `DATA_SCOPE` + 查询条件 | 审计 |
| `WRITE` | 命中 `DATA_SCOPE` | 状态守卫 + 实体归属校验 |
| `DELETE` | 命中 `DATA_SCOPE` | 状态守卫 + 业务前置校验 + 审计 |
| `TRANSFER` | 命中 `DATA_SCOPE` | 当前处理人 + 候选范围 + 组织边界 + 审计 |
| `APPROVE/REJECT` | 命中 `DATA_SCOPE` | runtime task 办理权 + 节点字段权限 |
| `IMPORT/CONFIG/RECALC/JOB_TRIGGER/PERMISSION_CHANGE/EXECUTE_SQL` | 动作授权 | 专项守卫 + reason + 审计 |

---

## 3. 核心枚举与注解（骨架）

```java
package com.bank.platform.security.biz;

public enum BizType {
  NAV,
  ADDRBOOK,
  PRODUCT,
  DOC,
  TAG,
  LEAD,
  CUSTOMER,
  CUSTOMER_POOL,
  CLAIM,
  TOUCH_TASK,
  TOUCH_REPORT,
  LOAN,
  SUPPORT,
  SUPPORT_DEPT,
  REPORT,
  PERF_CONFIG,
  SYS_CONFIG
}
```

```java
package com.bank.platform.security.biz;

public enum BizAction {
  READ, LIST,
  WRITE, DELETE,
  APPROVE, REJECT,
  TRANSFER,
  IMPORT, EXPORT,
  EXECUTE, EXECUTE_SQL,
  RECALC,
  JOB_TRIGGER,
  PERMISSION_CHANGE,
  CONFIG
}
```

```java
package com.bank.platform.security.biz;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BizAuth {
  BizType bizType();
  BizAction action();
}
```

> 备注：也可以拆成 `@BizType` + `@BizAction` 两个注解；合并为一个注解更省心。

---

## 4. 数据范围模型（骨架）

`project_ana.md` 的 DataScope 已收敛到一套组织维度（EXT_ORG_INFO），推荐在代码里表达为：

```java
package com.bank.platform.security.scope;

public enum DataScopeType {
  SELF_CREATED,
  SELF,            // 对象字段=当前用户（如通讯录本人维护、个人KPI）
  SELF_ASSIGNED,   // 任务类
  ORG,
  ORG_SUBTREE,
  ALL,
  WORKFLOW_PARTICIPANT
}
```

```java
package com.bank.platform.security.scope;

import java.util.Set;

public record DataScope(
    DataScopeType type,
    String empId,
    String orgCode,        // V1 只取“单用户单主机构”对应的 orgCode
    Set<String> orgSubtree,   // 需要时才填充（可延迟查询）
    boolean allowCrossOrgRead // 给客户详情跨机构只读特例等场景用
) {}
```

---

## 5. 授权主流程（Interceptor 骨架）

```java
package com.bank.platform.security.web;

import com.bank.platform.security.biz.*;
import com.bank.platform.security.rbac.*;
import com.bank.platform.security.scope.*;
import jakarta.servlet.http.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public final class BizAuthInterceptor implements HandlerInterceptor {
  private final CurrentUserProvider currentUserProvider;
  private final ResourceMatcher resourceMatcher;
  private final RbacAuthorizer rbacAuthorizer;
  private final BizMetaResolver bizMetaResolver;
  private final BizScopeService bizScopeService;
  private final AuditService auditService;

  public BizAuthInterceptor(
      CurrentUserProvider currentUserProvider,
      ResourceMatcher resourceMatcher,
      RbacAuthorizer rbacAuthorizer,
      BizMetaResolver bizMetaResolver,
      BizScopeService bizScopeService,
      AuditService auditService
  ) {
    this.currentUserProvider = currentUserProvider;
    this.resourceMatcher = resourceMatcher;
    this.rbacAuthorizer = rbacAuthorizer;
    this.bizMetaResolver = bizMetaResolver;
    this.bizScopeService = bizScopeService;
    this.auditService = auditService;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
    // 1) 获取当前用户
    var user = currentUserProvider.requireCurrentUser();

    // 2) 资源匹配（URL+Method -> RESOURCE_ID）
    var resource = resourceMatcher.match(req.getRequestURI(), req.getMethod());
    if (resource == null) {
      throw new AccessDeniedException("RESOURCE_NOT_REGISTERED");
    }

    // 3) RBAC 校验：角色是否拥有该资源
    if (!rbacAuthorizer.isAllowed(user.roleIds(), resource.resourceId())) {
      throw new AccessDeniedException("RBAC_DENIED");
    }

    // 4) 解析 BizType + Action（优先注解）
    BizMeta meta = null;
    if (handler instanceof HandlerMethod hm) {
      meta = bizMetaResolver.resolve(hm, resource);
    } else {
      meta = bizMetaResolver.resolve(null, resource);
    }
    if (meta == null) {
      throw new AccessDeniedException("BIZ_META_MISSING");
    }

    // 5) 读取 BizType 的统一范围（合并多角色）
    var scope = bizScopeService.resolveScope(user, meta.bizType());

    // 6) 放入上下文，供 DAO 层拼条件或写前校验
    DataScopeContext.set(new DataScopeContext.Value(user, resource, meta, scope));

    // 7) 高危动作审计前置校验（例如 reason 必填）
    if (auditService.isHighRisk(meta.action())) {
      auditService.requireReason(req, meta);
    }

    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
    try {
      // 高危动作统一审计：成功/失败均可记录（按你们策略）
      auditService.maybeAudit(req, resp, ex);
    } finally {
      DataScopeContext.clear();
    }
  }
}
```

`BizMeta` 建议是不可变对象：

```java
package com.bank.platform.security.biz;

public record BizMeta(BizType bizType, BizAction action) {}
```

---

## 6. BizType/Action 解析策略（骨架）

推荐优先级：**注解 > 资源元数据表 > 规则/默认值**。

```java
package com.bank.platform.security.biz;

import com.bank.platform.security.rbac.Resource;
import org.springframework.web.method.HandlerMethod;

public interface BizMetaResolver {
  BizMeta resolve(HandlerMethod hmOrNull, Resource resource);
}
```

注解解析示例：

```java
package com.bank.platform.security.biz;

import com.bank.platform.security.rbac.Resource;
import org.springframework.web.method.HandlerMethod;

public final class AnnotationFirstBizMetaResolver implements BizMetaResolver {
  private final ResourceMetaRepository resourceMetaRepository; // 可选：RESOURCE_ID -> BizType/Action

  public AnnotationFirstBizMetaResolver(ResourceMetaRepository resourceMetaRepository) {
    this.resourceMetaRepository = resourceMetaRepository;
  }

  @Override
  public BizMeta resolve(HandlerMethod hmOrNull, Resource resource) {
    if (hmOrNull != null) {
      var ann = hmOrNull.getMethodAnnotation(BizAuth.class);
      if (ann == null) {
        ann = hmOrNull.getBeanType().getAnnotation(BizAuth.class);
      }
      if (ann != null) {
        return new BizMeta(ann.bizType(), ann.action());
      }
    }
    return resourceMetaRepository.findBizMetaByResourceId(resource.resourceId()).orElse(null);
  }
}
```

---



### 6.1 通用工作流接口的 BizType 解析补充（Flowable 7.x）

V1 冻结为统一使用 `/api/workflow/tasks/**` 作为外部工作流办理入口，因此 `BizMetaResolver` 需要支持“先解任务，再解 BizType”的动态解析规则。

建议顺序：

1. 普通业务接口：仍然优先取 `@BizAuth`
2. `/api/workflow/tasks/**`：
   - 对 `GET /api/workflow/tasks`：根据查询结果中的 `business_key` 逐条定位业务域，再结合任务节点类型或入口视图映射真实权限 BizType，用于列表聚合展示
   - 对 `POST /api/workflow/tasks/{taskId}/claim|approve|reject|transfer`：
     1. 先通过 `taskId` 查询 Flowable runtime task / `biz_process_map`
     2. 解析出 `business_key`
     3. 再按前缀定位业务域，并结合任务节点类型或入口视图映射 BizType：
        * `LEAD:* -> LEAD`
        * `LOAN:* -> LOAN`
        * `SUPPORT:* -> SUPPORT / SUPPORT_DEPT`
        * `TOUCH:* -> TOUCH_TASK`
     4. 用真实 BizType 执行 `Action + DATA_SCOPE + 状态守卫 + 业务表单守卫`
3. 不允许把 `/api/workflow/tasks/**` 固定登记成一个新的业务域来跳过真实 BizType 校验。

建议统一通过参与者服务承载流程参与者与 runtime 办理权判定：

```java
public interface WorkflowParticipantService {
  boolean isParticipant(String empId, String businessKey, String viewBizType);
  RuntimeAccess resolveRuntimeAccess(String taskId, String empId);
  Set<String> resolveCandidateGroups(String empId);
  Set<String> listReadableBusinessKeys(String empId, String bizType);
}
```

解析骨架示例：

```java
public BizMeta resolveForWorkflowTask(String taskId, String actionPath) {
  var processMeta = workflowQueryApi.requireProcessMetaByTaskId(taskId);
  var bizType = mapFromBusinessKey(processMeta.businessKey());
  var action = switch (actionPath) {
    case "claim" -> BizAction.WRITE;
    case "approve" -> BizAction.APPROVE;
    case "reject" -> BizAction.REJECT;
    case "transfer" -> BizAction.TRANSFER;
    default -> throw new IllegalArgumentException("unsupported workflow action");
  };
  return new BizMeta(bizType, action);
}
```

## 7. BizType 统一范围计算（多角色合并）

核心问题：用户可能有多个角色，对同一 BizType 的 `DATA_SCOPE` 需要合并。

建议合并策略：
* `DATA_SCOPE`：取并集（更大的可见边界优先），但不直接放宽办理权或高危动作
* `ActionGuard`：在 `DATA_SCOPE` 命中后，再根据动作语义做二次收口
    * `READ/LIST/EXPORT`：直接按 `DATA_SCOPE` 执行
    * `WRITE/DELETE/TRANSFER`：叠加实体状态、归属和业务规则守卫
    * `APPROVE/REJECT`：叠加 Flowable runtime task 守卫
    * `IMPORT/EXECUTE/EXECUTE_SQL/CONFIG/RECALC/JOB_TRIGGER/PERMISSION_CHANGE`：叠加高危动作守卫与审计
* 资源权限取并集，但 `TRANSFER/DELETE/APPROVE/REJECT/IMPORT/RECALC/...` 等动作仍需单独授权，不能因为范围更大就自动放宽

```java
package com.bank.platform.security.scope;

import com.bank.platform.security.biz.*;
import com.bank.platform.security.user.CurrentUser;

public interface BizScopeService {
  DataScope resolveScope(CurrentUser user, BizType bizType);
}
```

---

## 8. 数据范围落地两种模式（选其一）

### 8.1 模式A：DAO/Repository 显式传入 scope（最清晰，改动最大）

* Service 层从 `DataScopeContext` 取 scope
* 调用 Repository 时显式传参
* SQL 里手工拼 where（或 MyBatis 动态 SQL）

优点：可控、可测试、无“魔法拼 SQL”
缺点：侵入性强，每个查询都要改签名

### 8.2 模式B：MyBatis 插件/拦截器自动注入 where（侵入小，治理成本高）

* Interceptor 中把 scope 放 `ThreadLocal`
* MyBatis 拦截器在 `SELECT/UPDATE/DELETE` 时按表配置注入过滤条件

优点：业务代码侵入小
缺点：需要“表字段映射配置”，否则容易误伤复杂 SQL

推荐实践：
* 先用模式A把 V1 核心流程跑通（客户/线索/触达/支持/投放）
* 稳定后再迁移到模式B 做平台化

### 8.3 `ObjectMetaRegistry`（建议）

```java
record ObjectMeta(
    String objectKey,
    String tableName,
    String ownerOrgCol,
    String createdByCol,
    String assigneeCol,
    String selfCol,
    String businessKeyCol,
    String joinPolicyKey,
    Set<DataScopeType> supportedScopes,
    String viewBizType
) {}

interface ObjectMetaRegistry {
  ObjectMeta require(String objectKey);
}
```

规则：
* 未声明 `supportedScopes` 的对象不得参与判权
* 命中未支持的 scope 时必须 fail-fast
* `support_request` 允许注册为 `SUPPORT` 与 `SUPPORT_DEPT` 两个逻辑对象

---

## 9. 写操作强校验（必须做，避免绕过列表直接写）

无论你用模式A还是B，写操作都建议在 Service 层统一做实体级校验：

```java
package com.bank.platform.security.scope;

import com.bank.platform.security.biz.BizType;
import com.bank.platform.security.user.CurrentUser;

public interface DataPermissionChecker {
  void assertCanWrite(CurrentUser user, BizType bizType, Ownership ownership, String reasonForAudit);

  record Ownership(String ownerOrgId, String createdBy, String assigneeEmpId) {}
}
```

使用方式：
* 先按主键查到目标记录的 `owner_org_id/created_by/...`
* 再校验是否命中 `DATA_SCOPE` 且通过对应 `ActionGuard`
* 通过后才允许更新/删除/提交

---



## 9.1 Flowable 7.x 办理权限补充（必须做）

统一口径：

1. `claim` 只表示“候选池 -> 当前处理人”，调用 `TaskService.claim(taskId, empId)`。
2. `approve` / `reject` 统一调用 `TaskService.complete(...)`；其中 `reject` 默认通过 BPMN 显式驳回分支实现，不走任意跳转。
3. `transfer` 统一调用 `TaskService.setAssignee(taskId, targetEmpId)`，但它只是引擎层 assignee 变更；业务语义仍需自己补齐：
   - 接收人必须属于当前节点候选范围
   - 满足组织边界
   - 填写转交原因
   - 写审计日志
   - SLA 不重置
4. 办理权必须优先以 Flowable runtime task 为准，禁止仅依据业务表里的 `assigned_emp_id` 一类字段推导。
5. `WORKFLOW_PARTICIPANT` 依赖历史 assignee / identity links 判定时，Flowable history level 必须不低于 `audit`。

推荐增加一个参与者服务：

```java
public interface WorkflowParticipantService {
  boolean isParticipant(String empId, String businessKey, String viewBizType);
  RuntimeAccess resolveRuntimeAccess(String taskId, String empId);
  Set<String> resolveCandidateGroups(String empId);
  Set<String> listReadableBusinessKeys(String empId, String bizType);
}
```


## 10. 客户详情“跨机构全量历史只读特例”落地要点

需要两段式校验（防直链越权）：
1) `CUSTOMER` 的基础可见性校验通过（客户是否可见一律按 `cust_claim` 的有效认领关系判断；用户能在其 `DATA_SCOPE` 内检索到该客户主记录，或由合法列表入口进入）
2) 才允许调用 `/api/customers/{id}/history/*` 返回跨机构历史

建议把“基础可见性”做成独立接口/方法，复用到所有历史查询入口。

---

## 11. 配置与缓存（建议）

* `PT_RESOURCE`、`PT_ROLE_RESOURCE`、`PT_ROLE_BIZ_SCOPE` 全部需要缓存（Caffeine/Redis 都可）
* 变更生效：按 `project_ana.md` 建议 ≤5分钟
* 资源未登记：默认拒绝（Fail Close）

---

## 12. 最小落地清单（开发任务拆分）

1.  新增表 `PT_ROLE_BIZ_SCOPE` + 配置页（角色-BizType-统一范围）
2.  `@BizAuth` 注解 + `BizMetaResolver`
3.  `ResourceMatcher`（URL+Method -> RESOURCE_ID）
4.  `BizAuthInterceptor` 串起鉴权流水线
5.  `DataScopeContext` + 关键查询/写入的 scope 落地（先用模式A）
6.  高危动作 reason 校验 + 审计日志落库
7.  `/api/workflow/tasks/**` 的动态 BizType 解析与 Flowable 办理权守卫
8.  `WorkflowParticipantService` 与 history level=`audit` 的配置落地
---

## 13. 资源命名规范与 `PT_RESOURCE` 清单

> 本章合并自 `permission_resource_catalog_v1.md`。
> 目的：冻结 V1 的接口命名规范、资源登记规则与 BizType 资源清单，便于开发、联调、测试与权限配置在同一文档对照执行。
### 13.1 关键约定（必须统一）

#### 13.1.1 URL 命名与分层

*   统一后端 API 前缀：`/api`
*   建议按“业务域 + 子资源/动作”组织 URL（REST + 少量动作型子路径）：
    *   列表：`GET /api/{resources}`
    *   详情：`GET /api/{resources}/{id}`
    *   新增：`POST /api/{resources}`
    *   更新：`PUT/PATCH /api/{resources}/{id}`
    *   删除：`DELETE /api/{resources}/{id}`
    *   提交流程：`POST /api/{resources}/{id}/submit`
    *   导入：`POST /api/{resources}/import/preview`、`POST /api/{resources}/import`
    *   导出：`GET /api/{resources}/export`
    *   执行/试运行：`POST /api/{resources}/{id}/execute`、`POST /api/{resources}/{id}/dry-run`
*   建议把“管理/运维类”接口放到 `/api/admin/...`（便于网关/日志/审计区分），业务端仍可统一鉴权。

#### 13.1.2 PT_RESOURCE 登记与匹配规则

*   **最小鉴权单元**：`RESOURCE_URL + RESOURCE_METHOD`。
*   **按钮权限**：以“按钮触发的接口资源”是否授权为准，不额外发明按钮码。
*   **路径参数**：建议 `PT_RESOURCE.RESOURCE_URL` 支持 Ant 风格通配（实际以定制框架实现为准）：
    *   例：`/api/customers/*`、`/api/leads/*/submit`
    *   原则：只对 `{id}` 段使用 `*`，不要用 `/**` 吞掉动作路径，否则会丢失权限粒度（例如导出/导入/执行需要单独授权）。
*   **与 BizType 的关系**：一个接口资源必须归属一个 BizType（用于统一范围与动作守卫配置），见 1.3。

#### 13.1.3 BizType 归属与统一范围判定

*   每个请求在通过 RBAC（`PT_ROLE_RESOURCE`）后，还要按接口归属 BizType 做统一范围与动作守卫校验：
    *   `READ/LIST/EXPORT`：使用 `PT_ROLE_BIZ_SCOPE.DATA_SCOPE`
    *   `WRITE/DELETE/TRANSFER`：命中 `DATA_SCOPE` 后，再做实体状态与业务规则守卫
    *   `APPROVE/REJECT`：命中 `DATA_SCOPE` 后，再以 Flowable 当前任务办理权为准
    *   `IMPORT/EXECUTE/EXECUTE_SQL/CONFIG/RECALC/JOB_TRIGGER/PERMISSION_CHANGE`：依赖 `Action` 授权、业务守卫与审计
*   **通用工作流接口**（如审批/签收/转交）需要在服务端先从任务变量解析 `business_key` 前缀定位业务域，再结合任务节点类型或入口视图映射真实权限 BizType：
    *   `LEAD:*` -> `LEAD`
    *   `LOAN:*` -> `LOAN`
    *   `SUPPORT:*` -> `SUPPORT` 或 `SUPPORT_DEPT`（发起侧查询用 `SUPPORT`，承接部门派单/办理用 `SUPPORT_DEPT`）
    *   `TOUCH:*` -> `TOUCH_TASK`
    *   其他业务按 `business_key` 前缀扩展
*   **V1 冻结口径：** `/api/workflow/tasks/**` 是唯一的通用工作流办理接口；V1 不再派生 `/api/loans/workflow/**`、`/api/support-requests/workflow/**` 等并行接口风格。
*   **Flowable 7.x 特殊口径：**
    *   `/api/workflow/tasks/**` 仍需登记到 `PT_RESOURCE`。
    *   但其 BizType 不在资源静态表中固定写死，运行时必须根据 `business_key` 前缀映射回真实业务域。
    *   因此通用工作流接口本身不是独立业务域，只是统一的任务办理入口。

#### 13.1.4 高危动作必须“接口拆分 + 单独授权 + 审计”

高危动作清单与 `project_ana.md 4.6.1.5` 保持一致，建议接口层面做到“单独 URL”：

*   `IMPORT`：`/import`（含 preview/commit）
*   `EXPORT`：`/export`
*   `EXECUTE_SQL`：`/sql-explorer/execute`
*   `RECALC`：`/recalc`
*   `JOB_TRIGGER`：`/jobs/*/trigger`
*   `PERMISSION_CHANGE`：`/permissions/...`

这样才能在 `PT_RESOURCE` 上做到精确授权与审计留痕。

---

### 13.3 资源拆分检查清单（落地时必须过一遍）

1.  所有高危动作是否都有“独立 URL”（IMPORT/EXPORT/EXECUTE/RECALC/SQL/触发调度/权限变更）？
2.  同一页面是否允许承载查询与写入口，并以后端校验作为最终裁决？
3.  客户详情跨机构历史接口是否实现“基础可见性校验 + 严格只读”（禁止直链越权）？
4.  导出接口是否强制受 `DATA_SCOPE + 页面过滤条件` 约束，并记录审计（过滤条件/行数）？
5.  通用工作流接口是否能先从 `business_key` 定位业务域，再结合节点类型/入口视图映射 BizType，并正确执行 `Action + DATA_SCOPE + 守卫` 校验？

