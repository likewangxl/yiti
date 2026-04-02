# 公共开发规范文档

> **适用范围**：银行省分行一体化营销与绩效管理平台（Branch Platform）全部模块
> **技术栈**：Spring Boot 3.2.3 / JDK 17 / MyBatis 3.0.3 / Flowable 7.0.1 / MySQL 8.0 / Redis 6.X
> **版本**：V1.0
> **最后更新**：2026-04-02

---

## 目录

- [1. 统一响应模型](#1-统一响应模型)
- [2. 统一错误码规范](#2-统一错误码规范)
- [3. 统一分页参数标准](#3-统一分页参数标准)
- [4. 鉴权链路使用指南](#4-鉴权链路使用指南)
- [5. DATA_SCOPE → SQL 过滤条件模板](#5-data_scope--sql-过滤条件模板)
- [6. 统一审计切面使用方式](#6-统一审计切面使用方式)
- [7. 领域事件发布规范](#7-领域事件发布规范)
- [8. 统一数据传递规范](#8-统一数据传递规范)
- [9. 统一日志规范](#9-统一日志规范)

---

## 1. 统一响应模型

### 1.1 JSON 结构定义

所有 HTTP 接口的响应体必须遵循以下统一结构：

```json
{
  "code": "0",
  "message": "OK",
  "traceId": "9f3b6c1c7f0a4b31",
  "data": {},
  "page": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 100
  },
  "timestamp": "2026-03-06T10:30:00+08:00"
}
```

### 1.2 字段说明

| 字段 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `code` | String | 是 | 响应码。`"0"` 表示成功，非零为错误码（参见第 2 章错误码规范） |
| `message` | String | 是 | 响应描述。成功时为 `"OK"`，错误时为面向用户的可读提示信息 |
| `traceId` | String | 是 | 全链路追踪 ID，由 Filter 层生成并注入 MDC，贯穿请求全生命周期 |
| `data` | Object / Array | 否 | 业务数据载体。无数据时返回 `null` 或不返回该字段 |
| `page` | Object | 否 | 分页信息对象。仅分页接口返回，非分页接口禁止返回此字段 |
| `page.pageNo` | int | 分页时必填 | 当前页码，从 1 开始 |
| `page.pageSize` | int | 分页时必填 | 每页条数 |
| `page.total` | long | 分页时必填 | 符合条件的总记录数 |
| `timestamp` | String | 是 | 响应时间，ISO 8601 格式，带时区偏移量（`+08:00`） |

### 1.3 强制规则

1. **所有响应必须携带 `traceId`**：无论成功还是失败，便于问题排查与全链路追踪。
2. **非分页接口不返回 `page` 字段**：避免前端误判为分页结果。
3. **错误响应禁止直接回传数据库异常原文**：所有数据库异常必须由全局异常处理器捕获，转换为业务可读的错误信息。SQL 异常、堆栈信息等仅记录到服务端日志，绝不暴露给前端。
4. **`code` 字段使用字符串类型**：便于承载带前缀的模块化错误码（如 `"AUTH-40101"`）。
5. **`data` 字段在错误响应中设为 `null`**：错误时不应返回部分业务数据，避免歧义。

### 1.4 Java 模型定义

```java
package com.bank.branch.platform.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 统一响应包装器
 * 所有 Controller 接口的返回值必须使用此类包装
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseWrapper<T> {

    /** 响应码，"0" 表示成功 */
    private String code;

    /** 响应描述 */
    private String message;

    /** 全链路追踪 ID */
    private String traceId;

    /** 业务数据 */
    private T data;

    /** 分页信息（仅分页接口返回） */
    private PageInfo page;

    /** 响应时间 */
    private OffsetDateTime timestamp;

    // ---- 静态工厂方法 ----

    /** 成功响应（无数据） */
    public static ResponseWrapper<Void> ok() {
        return build("0", "OK", null, null);
    }

    /** 成功响应（带数据） */
    public static <T> ResponseWrapper<T> ok(T data) {
        return build("0", "OK", data, null);
    }

    /** 成功响应（带分页数据） */
    public static <T> ResponseWrapper<T> ok(T data, PageInfo page) {
        return build("0", "OK", data, page);
    }

    /** 错误响应 */
    public static ResponseWrapper<Void> error(String code, String message) {
        return build(code, message, null, null);
    }

    private static <T> ResponseWrapper<T> build(String code, String message, T data, PageInfo page) {
        ResponseWrapper<T> wrapper = new ResponseWrapper<>();
        wrapper.setCode(code);
        wrapper.setMessage(message);
        wrapper.setData(data);
        wrapper.setPage(page);
        wrapper.setTraceId(TraceIdUtil.currentTraceId());
        wrapper.setTimestamp(OffsetDateTime.now());
        return wrapper;
    }

    // ---- getter / setter 省略 ----
}
```

### 1.5 响应示例

#### 1.5.1 成功响应（单条数据）

```json
{
  "code": "0",
  "message": "OK",
  "traceId": "9f3b6c1c7f0a4b31",
  "data": {
    "leadId": "LD20260306001",
    "customerName": "张三",
    "status": "PENDING"
  },
  "timestamp": "2026-03-06T10:30:00+08:00"
}
```

#### 1.5.2 成功响应（分页数据）

```json
{
  "code": "0",
  "message": "OK",
  "traceId": "a1b2c3d4e5f60001",
  "data": [
    {
      "leadId": "LD20260306001",
      "customerName": "张三",
      "status": "PENDING"
    },
    {
      "leadId": "LD20260306002",
      "customerName": "李四",
      "status": "APPROVED"
    }
  ],
  "page": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 156
  },
  "timestamp": "2026-03-06T10:31:00+08:00"
}
```

#### 1.5.3 错误响应

```json
{
  "code": "AUTH-40301",
  "message": "您没有访问该资源的权限，请联系管理员",
  "traceId": "b2c3d4e5f6a70002",
  "data": null,
  "timestamp": "2026-03-06T10:32:00+08:00"
}
```

#### 1.5.4 参数校验失败响应

```json
{
  "code": "SYS-40001",
  "message": "参数校验失败",
  "traceId": "c3d4e5f6a7b80003",
  "data": {
    "errors": [
      {
        "field": "customerName",
        "rejected": "",
        "message": "客户名称不能为空"
      },
      {
        "field": "phoneNumber",
        "rejected": "123",
        "message": "手机号格式不正确"
      }
    ]
  },
  "timestamp": "2026-03-06T10:33:00+08:00"
}
```

---

## 2. 统一错误码规范

### 2.1 错误码格式

```
{模块前缀}-{HTTP状态码后两位}{序号}
```

- **模块前缀**：标识错误发生的模块，使用大写字母缩写。
- **HTTP 状态码后两位**：取 HTTP 标准状态码的后两位（如 401 -> 01，403 -> 03，404 -> 04，500 -> 00）。
- **序号**：两位数字序号，同一模块同一 HTTP 状态码下的细分错误。

### 2.2 模块前缀清单

| 模块前缀 | 对应模块 | 说明 |
|---|---|---|
| `SYS` | common / 跨模块通用 | 系统级通用错误 |
| `AUTH` | auth-permission-center | 认证授权中心 |
| `GOV` | system-governance-center | 系统治理中心 |
| `PORTAL` | portal-content-center | 门户与内容中心 |
| `CUST` | customer-marketing-center | 客户营销中心 |
| `WF` | workflow-center | 工作流中心 |
| `BIZ` | business-application-center | 业务申请中心 |
| `PERF` | performance-engine-center | 绩效计算中心 |
| `RPT` | report-analytics-center | 报表分析中心 |

### 2.3 通用错误码（SYS）

| 错误码 | HTTP 状态 | 说明 |
|---|---|---|
| `SYS-40001` | 400 | 请求参数校验失败 |
| `SYS-40002` | 400 | 请求体格式错误（JSON 解析失败） |
| `SYS-40003` | 400 | 必填参数缺失 |
| `SYS-40004` | 400 | 参数值超出允许范围 |
| `SYS-40501` | 405 | HTTP 方法不允许 |
| `SYS-40901` | 409 | 数据冲突（并发修改） |
| `SYS-41301` | 413 | 请求体过大 |
| `SYS-42901` | 429 | 请求频率超限 |
| `SYS-50001` | 500 | 系统内部错误 |
| `SYS-50002` | 500 | 数据库操作异常 |
| `SYS-50003` | 500 | 缓存服务异常 |
| `SYS-50004` | 500 | 外部服务调用超时 |
| `SYS-50301` | 503 | 服务暂不可用 |

### 2.4 权限类错误码（AUTH）

| 错误码 | HTTP 状态 | 说明 | 典型场景 |
|---|---|---|---|
| `AUTH-40101` | 401 | 登录态失效 | Session 过期、Token 无效、未登录访问 |
| `AUTH-40102` | 401 | 账户已被锁定 | 密码错误次数超限 |
| `AUTH-40103` | 401 | 账户已被禁用 | 管理员禁用用户 |
| `AUTH-40301` | 403 | PT_RESOURCE 未授权 | 用户角色未配置该接口资源的访问权限 |
| `AUTH-40302` | 403 | BizType 未配置范围 | PT_ROLE_BIZ_SCOPE 中用户角色未配置该 BizType 的数据范围 |
| `AUTH-40303` | 403 | 写范围校验失败 | 用户尝试修改不在其数据范围内的实体 |
| `AUTH-40304` | 403 | 高危操作未提供原因 | TRANSFER / DELETE / IMPORT 等高危操作未填写 reason |
| `AUTH-40305` | 403 | 流程办理权不足 | 用户不是当前流程任务的办理人或候选人 |
| `AUTH-40401` | 404 | 资源未登记 | 接口 URL+Method 未在 PT_RESOURCE 中注册 |

### 2.5 权限错误响应体示例

```json
{
  "code": "AUTH-40301",
  "message": "您的角色未被授权访问此接口，请联系管理员配置权限",
  "traceId": "d4e5f6a7b8c90004",
  "data": null,
  "timestamp": "2026-03-06T10:35:00+08:00"
}
```

```json
{
  "code": "AUTH-40302",
  "message": "您的角色未配置线索（LEAD）业务类型的数据访问范围，请联系管理员",
  "traceId": "e5f6a7b8c9d00005",
  "data": null,
  "timestamp": "2026-03-06T10:36:00+08:00"
}
```

```json
{
  "code": "AUTH-40101",
  "message": "登录态已失效，请重新登录",
  "traceId": "f6a7b8c9d0e10006",
  "data": null,
  "timestamp": "2026-03-06T10:37:00+08:00"
}
```

### 2.6 各模块扩展错误码示例

各模块在各自范围内自行编号，以下为示例：

| 错误码 | 说明 |
|---|---|
| `CUST-40401` | 客户记录不存在 |
| `CUST-40901` | 客户已被其他客户经理认领 |
| `WF-40001` | 流程定义不存在 |
| `WF-40901` | 流程任务已被签收 |
| `PERF-40001` | 绩效规则配置不完整 |
| `RPT-50001` | 报表生成超时 |

### 2.7 错误码使用规范

1. **禁止凭空造码**：新增错误码必须在对应模块的错误码枚举类中注册，并在本文档更新。
2. **message 必须面向用户可读**：禁止将 SQL 异常信息、堆栈信息、变量名等技术细节暴露给前端。
3. **同一语义使用同一错误码**：避免同一类错误在不同接口使用不同错误码。
4. **错误码枚举类建议定义在各模块的 `api` 包中**：便于跨模块引用。

---

## 3. 统一分页参数标准

### 3.1 请求参数定义

| 参数 | 类型 | 默认值 | 约束 | 说明 |
|---|---|---|---|---|
| `pageNo` | int | 1 | >= 1 | 页码，从 1 开始 |
| `pageSize` | int | 20 | 1 ~ 100 | 每页条数，最大 100 |
| `sortBy` | String | `createdTime` | 仅允许白名单字段 | 排序字段，必须在服务端白名单内 |
| `sortDir` | String | `desc` | 仅允许 `asc` / `desc` | 排序方向 |

### 3.2 强制约束

1. **`sortBy` 必须做白名单校验**：禁止将前端传入的任意字段名直接拼入 SQL，防止 SQL 注入。每个业务接口必须显式声明允许排序的字段列表。
2. **`pageSize` 最大不超过 100**：防止单次查询数据量过大影响性能。超出范围时自动修正为 100。
3. **超过 5000 行的导出必须走异步导出任务**：前端发起导出请求后，后端创建异步任务，导出完成后通知前端下载。
4. **跨模块查询必须分页**：通过 `*QueryApi` 进行跨模块查询时，强制使用分页参数，最大 `pageSize = 100`。
5. **分页总数查询优化**：当 `total` 超过 10000 时，允许返回近似值并标记，避免 `COUNT(*)` 对大表造成性能问题。

### 3.3 Java 模型定义

#### PageRequest（分页请求）

```java
package com.bank.branch.platform.common.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;

/**
 * 统一分页请求参数
 * 所有分页查询接口的请求 DTO 应继承或组合此类
 */
public class PageRequest {

    /** 页码，从 1 开始，默认 1 */
    @Min(value = 1, message = "pageNo 不能小于 1")
    private int pageNo = 1;

    /** 每页条数，默认 20，最大 100 */
    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 100, message = "pageSize 不能超过 100")
    private int pageSize = 20;

    /** 排序字段，默认 createdTime */
    private String sortBy = "createdTime";

    /** 排序方向，默认 desc */
    private String sortDir = "desc";

    /**
     * 校验排序字段是否在白名单内
     * 若不在白名单内则重置为默认值 createdTime
     *
     * @param allowedFields 允许排序的字段白名单
     */
    public void validateSortBy(Set<String> allowedFields) {
        if (this.sortBy == null || !allowedFields.contains(this.sortBy)) {
            this.sortBy = "createdTime";
        }
        if (!"asc".equalsIgnoreCase(this.sortDir) && !"desc".equalsIgnoreCase(this.sortDir)) {
            this.sortDir = "desc";
        }
    }

    /**
     * 计算 MyBatis 分页偏移量
     *
     * @return 偏移量 = (pageNo - 1) * pageSize
     */
    public int getOffset() {
        return (this.pageNo - 1) * this.pageSize;
    }

    // ---- getter / setter 省略 ----
}
```

#### PageResult（分页响应）

```java
package com.bank.branch.platform.common.model;

import java.util.List;

/**
 * 统一分页结果包装
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    /** 当前页数据列表 */
    private List<T> records;

    /** 分页信息 */
    private PageInfo page;

    public static <T> PageResult<T> of(List<T> records, int pageNo, int pageSize, long total) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        PageInfo pageInfo = new PageInfo();
        pageInfo.setPageNo(pageNo);
        pageInfo.setPageSize(pageSize);
        pageInfo.setTotal(total);
        result.setPage(pageInfo);
        return result;
    }

    /**
     * 包装为统一响应
     */
    public ResponseWrapper<List<T>> toResponse() {
        return ResponseWrapper.ok(this.records, this.page);
    }

    // ---- getter / setter 省略 ----
}
```

#### PageInfo（分页信息）

```java
package com.bank.branch.platform.common.model;

/**
 * 分页元信息
 */
public class PageInfo {

    /** 当前页码 */
    private int pageNo;

    /** 每页条数 */
    private int pageSize;

    /** 总记录数 */
    private long total;

    // ---- getter / setter 省略 ----
}
```

### 3.4 使用示例

```java
@BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
@GetMapping("/api/leads")
public ResponseWrapper<List<LeadVO>> listLeads(@Valid LeadQueryReqDTO req) {
    // 排序白名单校验
    req.validateSortBy(Set.of("createdTime", "customerName", "status", "updatedTime"));

    // 执行分页查询
    PageResult<LeadVO> result = leadService.queryLeads(req);

    // 返回统一分页响应
    return result.toResponse();
}
```

---

## 4. 鉴权链路使用指南

### 4.1 鉴权主链路

系统鉴权采用分层拦截、逐步深入的设计模式，完整链路如下：

```
Filter → HandlerInterceptor → @BizAuth → DataPermissionChecker → AuditService
```

#### 第 1 层：Filter（认证层）

**职责**：认证、解析登录态、建立 CurrentUser 上下文。

| 功能 | 说明 |
|---|---|
| 登录态验证 | 从 HTTP 请求中提取 Session / Token，验证有效性 |
| 用户信息加载 | 根据登录态从 Redis 缓存或数据库加载用户基本信息 |
| CurrentUser 建立 | 构建 `CurrentUserContext` 并放入 ThreadLocal |
| traceId 生成 | 生成全局唯一 traceId 并注入 MDC |
| 白名单放行 | 对公开接口（登录、健康检查等）跳过认证 |

**处理规则**：
- 认证失败立即返回 `AUTH-40101`（登录态失效），不进入后续链路。
- Filter 不做任何业务级权限判断，只负责"你是谁"。

```java
// CurrentUserContext 数据模型
record CurrentUserContext(
    String empId,           // 员工工号（PT_USER.USER_ID）
    String mainOrgCode,     // 主机构编码（EXT_USER_ORG 唯一有效主机构）
    Set<String> roleIds,    // 角色 ID 集合
    Set<String> candidateGroupKeys, // 候选组标识
    boolean systemAdmin     // 是否系统管理员
) {}
```

#### 第 2 层：HandlerInterceptor（授权层）

**职责**：执行资源权限校验与 BizType 权限校验。

处理流程：

1. **资源匹配**：将 `请求URI + HTTP Method` 与 `PT_RESOURCE` 表进行匹配，找到对应的 `RESOURCE_ID`。若未找到匹配资源，返回 `AUTH-40401`（资源未登记）。
2. **RBAC 校验**：检查当前用户的角色是否在 `PT_ROLE_RESOURCE` 中拥有该 `RESOURCE_ID` 的访问权限。若无权限，返回 `AUTH-40301`（PT_RESOURCE 未授权）。
3. **BizType 解析**：解析接口归属的 BizType 和 BizAction（优先从 `@BizAuth` 注解读取）。
4. **数据范围计算**：根据用户角色从 `PT_ROLE_BIZ_SCOPE` 读取该 BizType 的 `DATA_SCOPE`，多角色取并集（更大的可见边界优先）。
5. **上下文注入**：将 BizType、Action、DataScope 等信息放入 `DataScopeContext`（ThreadLocal），供后续 Service / DAO 层使用。
6. **高危动作前置校验**：对高危动作（EXPORT / DELETE / TRANSFER 等），执行 reason 必填校验。

#### 第 3 层：@BizAuth 注解（声明层）

**职责**：显式声明接口的 BizType + Action，是鉴权的核心声明方式。

**强制规则**：
- 所有业务接口必须标注 `@BizAuth` 注解。
- 注解中的 `bizType` 和 `action` 必须与接口实际语义一致。
- 禁止在注解中使用宽泛的 BizType 代替精确的业务类型。

#### 第 4 层：DataPermissionChecker（实体校验层）

**职责**：在业务层对真实实体做读写范围校验。

**核心原则**：
- 列表查询（READ / LIST）：在 DAO 层通过 SQL 谓词过滤，依赖 `DataScopeContext` 中的范围信息。
- 写操作（WRITE / DELETE / TRANSFER）：必须在 Service 层先查出实体，再基于实体属性做二次校验。
- 防止绕过列表直接通过 ID 操作不属于自己范围的数据。

#### 第 5 层：AuditService（审计层）

**职责**：高危动作审计与原因校验。

**处理规则**：
- 在 Interceptor 的 `afterCompletion` 中统一记录审计日志。
- 高危动作必须携带 `reason` 参数，否则前置拦截返回 `AUTH-40304`。
- 审计日志记录操作的前后状态快照（JSON diff）。

### 4.2 @BizAuth 注解使用方式

#### 注解定义

```java
package com.bank.branch.platform.common.security.biz;

import java.lang.annotation.*;

/**
 * BizType 权限声明注解
 * 标注在 Controller 方法上，声明该接口归属的业务类型与动作类型
 * HandlerInterceptor 会据此执行 BizType 级别的权限校验
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BizAuth {

    /** 业务类型 */
    BizType bizType();

    /** 动作类型 */
    BizAction action();
}
```

#### 使用示例

**场景一：线索新增（写操作）**

```java
@BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
@PostMapping("/api/leads")
public ResponseWrapper<LeadVO> createLead(@Valid @RequestBody LeadCreateReqDTO req) {
    LeadVO result = leadService.createLead(req);
    return ResponseWrapper.ok(result);
}
```

**场景二：客户列表查询（读操作）**

```java
@BizAuth(bizType = BizType.CUSTOMER, action = BizAction.LIST)
@GetMapping("/api/customers")
public ResponseWrapper<List<CustomerVO>> listCustomers(@Valid CustomerQueryReqDTO req) {
    PageResult<CustomerVO> result = customerService.queryCustomers(req);
    return result.toResponse();
}
```

**场景三：数据导出（高危操作）**

```java
@BizAuth(bizType = BizType.CUSTOMER, action = BizAction.EXPORT)
@GetMapping("/api/customers/export")
public ResponseWrapper<ExportTaskVO> exportCustomers(@Valid CustomerExportReqDTO req) {
    // 导出走异步任务
    ExportTaskVO task = exportService.createExportTask(req);
    return ResponseWrapper.ok(task);
}
```

**场景四：流程审批（工作流动作）**

```java
@BizAuth(bizType = BizType.LOAN, action = BizAction.APPROVE)
@PostMapping("/api/workflow/tasks/{taskId}/approve")
public ResponseWrapper<Void> approveTask(
        @PathVariable String taskId,
        @Valid @RequestBody ApproveReqDTO req) {
    workflowService.approve(taskId, req);
    return ResponseWrapper.ok();
}
```

**场景五：权限变更（需要审计原因）**

```java
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
@PutMapping("/api/admin/roles/{roleId}/biz-scope")
public ResponseWrapper<Void> updateBizScope(
        @PathVariable String roleId,
        @Valid @RequestBody BizScopeUpdateReqDTO req) {
    // reason 由审计切面强制校验
    roleBizScopeService.updateBizScope(roleId, req);
    return ResponseWrapper.ok();
}
```

### 4.3 DataScopeContext 获取方式

`DataScopeContext` 基于 ThreadLocal 模式实现，在 HandlerInterceptor 中设置，在请求结束后清除。Service 层可直接获取当前请求的权限上下文。

#### 上下文模型

```java
package com.bank.branch.platform.common.security.scope;

/**
 * 数据范围上下文（ThreadLocal）
 * 由 HandlerInterceptor 在鉴权通过后写入
 * 由 Service / DAO 层读取，用于数据范围过滤和写操作校验
 *
 * 注意：请求结束后必须在 afterCompletion 中清除，防止线程复用导致的数据泄漏
 */
public final class DataScopeContext {

    private static final ThreadLocal<Value> HOLDER = new ThreadLocal<>();

    /** 设置上下文（仅 Interceptor 调用） */
    public static void set(Value value) {
        HOLDER.set(value);
    }

    /** 获取当前上下文 */
    public static Value current() {
        Value value = HOLDER.get();
        if (value == null) {
            throw new IllegalStateException("DataScopeContext 未初始化，请检查鉴权链路");
        }
        return value;
    }

    /** 清除上下文（Interceptor afterCompletion 中调用） */
    public static void clear() {
        HOLDER.remove();
    }

    public record Value(
        CurrentUserContext user,
        Resource resource,
        BizMeta bizMeta,
        DataScope scope
    ) {}
}
```

#### Service 层使用方式

```java
@Service
public class LeadServiceImpl implements LeadService {

    /**
     * 查询线索列表
     * 自动应用数据范围过滤
     */
    @Override
    public PageResult<LeadVO> queryLeads(LeadQueryReqDTO req) {
        // 1. 获取数据范围上下文
        DataScopeContext.Value ctx = DataScopeContext.current();
        DataScopeType scope = ctx.scope().type();
        String empId = ctx.user().empId();
        String orgCode = ctx.user().mainOrgCode();

        // 2. 根据 scope 构建查询条件，传入 DAO 层
        LeadQueryParam param = LeadQueryParam.builder()
                .keyword(req.getKeyword())
                .status(req.getStatus())
                .scopeType(scope)
                .empId(empId)
                .orgCode(orgCode)
                .offset(req.getOffset())
                .pageSize(req.getPageSize())
                .build();

        // 3. 执行分页查询
        List<LeadVO> records = leadMapper.selectLeadsByScope(param);
        long total = leadMapper.countLeadsByScope(param);

        return PageResult.of(records, req.getPageNo(), req.getPageSize(), total);
    }
}
```

### 4.4 写操作二次校验模式

所有写操作（新增除外）必须在 Service 层基于实体做二次权限校验，防止用户绕过列表直接通过 ID 操作不属于自己数据范围的记录。

#### 标准流程

```java
@Service
public class LeadServiceImpl implements LeadService {

    /**
     * 更新线索信息
     * 必须先查实体，再做二次权限校验
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadVO updateLead(String leadId, LeadUpdateReqDTO req) {
        // 1. 按主键取实体
        CustLead entity = leadMapper.selectById(leadId);
        if (entity == null) {
            throw new BizException("CUST-40401", "线索不存在");
        }

        // 2. 状态守卫：检查实体当前状态是否允许修改
        StateGuard.check(entity.getStatus(), Set.of("PENDING", "RETURNED"));

        // 3. 写范围校验：检查当前用户是否有权修改该实体
        DataPermissionChecker.checkWrite(entity.getCreatedBy(), entity.getOwnerOrgId());

        // 4. 业务校验
        BusinessValidator.validate(entity, "UPDATE");

        // 5. 执行更新
        BeanUtils.copyProperties(req, entity);
        entity.setUpdatedBy(DataScopeContext.current().user().empId());
        entity.setUpdatedTime(LocalDateTime.now());
        leadMapper.updateById(entity);

        return convertToVO(entity);
    }
}
```

#### DataPermissionChecker 校验逻辑

```java
package com.bank.branch.platform.common.security.scope;

/**
 * 数据权限校验器
 * 在 Service 层写操作前调用，基于实体属性做二次权限校验
 */
public final class DataPermissionChecker {

    /**
     * 校验当前用户是否有权写入目标实体
     *
     * @param entityCreatedBy  实体创建人工号
     * @param entityOwnerOrgId 实体归属机构编码
     * @throws AccessDeniedException 权限不足时抛出
     */
    public static void checkWrite(String entityCreatedBy, String entityOwnerOrgId) {
        DataScopeContext.Value ctx = DataScopeContext.current();
        DataScopeType scope = ctx.scope().type();
        String empId = ctx.user().empId();
        String orgCode = ctx.user().mainOrgCode();

        boolean allowed = switch (scope) {
            case SELF_CREATED -> empId.equals(entityCreatedBy);
            case SELF, SELF_ASSIGNED -> empId.equals(entityCreatedBy);
            case ORG -> orgCode.equals(entityOwnerOrgId);
            case ORG_SUBTREE -> isOrgInSubtree(orgCode, entityOwnerOrgId);
            case ALL -> true;
            case WORKFLOW_PARTICIPANT -> false; // 流程参与者范围仅限只读
        };

        if (!allowed) {
            throw new AccessDeniedException("AUTH-40303", "写范围校验失败：当前用户无权修改该记录");
        }
    }

    /**
     * 校验当前用户是否有权写入目标实体（完整实体版本）
     *
     * @param entity 目标实体（需实现 Ownable 接口）
     */
    public static void checkWrite(Ownable entity) {
        checkWrite(entity.getCreatedBy(), entity.getOwnerOrgId());
    }

    private static boolean isOrgInSubtree(String userOrgCode, String entityOrgCode) {
        // 查询组织树判断 entityOrgCode 是否在 userOrgCode 的子树中
        // 建议缓存组织树结构
        return OrgTreeCache.isAncestorOrSelf(userOrgCode, entityOrgCode);
    }

    private DataPermissionChecker() {}
}
```

---

## 5. DATA_SCOPE → SQL 过滤条件模板

### 5.1 七种 DataScopeType 通用谓词模板

以下是每种 `DataScopeType` 对应的 SQL 谓词模板，`t` 为业务表别名：

| DataScopeType | SQL 谓词模板 | 参数来源 | 适用说明 |
|---|---|---|---|
| `SELF_CREATED` | `t.created_by = #{empId}` | `CurrentUserContext.empId` | 仅查看/操作本人创建的数据 |
| `SELF` | `t.{selfCol} = #{empId}` | `CurrentUserContext.empId` | 对象字段=本人，如通讯录维护人、个人 KPI。`selfCol` 由 ObjectMeta 配置 |
| `SELF_ASSIGNED` | `t.{assigneeCol} = #{empId}` | `CurrentUserContext.empId` | 仅查看分配给本人的任务/待办。`assigneeCol` 由 ObjectMeta 配置 |
| `ORG` | `t.{ownerOrgCol} = #{orgCode}` | `CurrentUserContext.mainOrgCode` | 本机构数据。`ownerOrgCol` 由 ObjectMeta 配置 |
| `ORG_SUBTREE` | `t.{ownerOrgCol} IN (SELECT org_code FROM ext_org_info WHERE org_code = #{orgCode} OR p_id = #{orgCode})` | `CurrentUserContext.mainOrgCode` | 本机构及下级机构数据，支持递归写法 |
| `ALL` | `1=1` | 无 | 全量数据，无过滤条件 |
| `WORKFLOW_PARTICIPANT` | `EXISTS (SELECT 1 FROM biz_process_map m WHERE m.business_key = t.business_key AND (m.start_user = #{empId} OR m.current_assignee = #{empId}))` | `CurrentUserContext.empId` + `WorkflowParticipantService` | 流程参与者可见，需结合 Flowable history 判定 |

#### MyBatis 动态 SQL 示例

```xml
<!-- 通用数据范围过滤片段 -->
<sql id="dataScopeFilter">
    <choose>
        <when test="scopeType == 'SELF_CREATED'">
            AND t.created_by = #{empId}
        </when>
        <when test="scopeType == 'SELF'">
            AND t.${selfCol} = #{empId}
        </when>
        <when test="scopeType == 'SELF_ASSIGNED'">
            AND t.${assigneeCol} = #{empId}
        </when>
        <when test="scopeType == 'ORG'">
            AND t.${ownerOrgCol} = #{orgCode}
        </when>
        <when test="scopeType == 'ORG_SUBTREE'">
            AND t.${ownerOrgCol} IN (
                SELECT oi.org_code FROM ext_org_info oi
                WHERE oi.org_code = #{orgCode}
                   OR oi.p_id = #{orgCode}
            )
        </when>
        <when test="scopeType == 'ALL'">
            <!-- 不添加过滤条件 -->
        </when>
        <when test="scopeType == 'WORKFLOW_PARTICIPANT'">
            AND EXISTS (
                SELECT 1 FROM biz_process_map m
                WHERE m.business_key = t.business_key
                  AND (m.start_user = #{empId} OR m.current_assignee = #{empId})
            )
        </when>
        <otherwise>
            <!-- 未匹配到的 scope 类型，fail-fast，拒绝访问 -->
            AND 1 = 0
        </otherwise>
    </choose>
</sql>
```

#### ORG_SUBTREE 递归写法（适用于多层组织树）

```sql
-- MySQL 8.0 CTE 递归写法
WITH RECURSIVE org_tree AS (
    SELECT org_code FROM ext_org_info WHERE org_code = #{orgCode}
    UNION ALL
    SELECT oi.org_code FROM ext_org_info oi
    INNER JOIN org_tree ot ON oi.p_id = ot.org_code
)
SELECT t.* FROM {tableName} t
WHERE t.{ownerOrgCol} IN (SELECT org_code FROM org_tree)
```

### 5.2 ObjectMeta Registry 注册规范

每个需要数据范围控制的业务对象必须在 `ObjectMetaRegistry` 中注册元数据，用于自动化谓词生成。

#### ObjectMeta 记录模型

```java
package com.bank.branch.platform.common.security.scope;

import java.util.Set;

/**
 * 业务对象元数据
 * 描述一个业务对象（表/逻辑视图）的数据范围映射关系
 * 所有需要 DATA_SCOPE 控制的对象必须注册
 */
public record ObjectMeta(
    /** 对象唯一标识，如 "LEAD"、"CUSTOMER"、"SUPPORT_DEPT" */
    String objectKey,

    /** 物理表名 */
    String tableName,

    /** 归属机构列名（对应 ORG / ORG_SUBTREE） */
    String ownerOrgCol,

    /** 创建人列名（对应 SELF_CREATED） */
    String createdByCol,

    /** 被分配人列名（对应 SELF_ASSIGNED），可为 null */
    String assigneeCol,

    /** 对象字段=本人的列名（对应 SELF），可为 null */
    String selfCol,

    /** 业务键列名（对应 WORKFLOW_PARTICIPANT），可为 null */
    String businessKeyCol,

    /** join 策略标识，用于无法纯字段过滤的场景（如 TOUCH_LOG -> TOUCH_TASK），可为 null */
    String joinPolicyKey,

    /** 该对象显式支持的数据范围类型集合 */
    Set<DataScopeType> supportedScopes,

    /** 逻辑视图的业务域标识，同一物理表可注册多个逻辑对象 */
    String viewBizType
) {}
```

#### 注册接口

```java
package com.bank.branch.platform.common.security.scope;

/**
 * 对象元数据注册中心
 * 系统启动时由各模块注册自己的 ObjectMeta
 */
public interface ObjectMetaRegistry {

    /**
     * 注册对象元数据
     */
    void register(ObjectMeta meta);

    /**
     * 获取指定对象的元数据
     * 未注册的对象调用此方法将抛出异常（fail-fast）
     *
     * @param objectKey 对象标识
     * @return 对象元数据
     * @throws IllegalStateException 对象未注册时抛出
     */
    ObjectMeta require(String objectKey);
}
```

#### 注册示例

```java
@Configuration
public class CustomerMarketingMetaConfig {

    @Bean
    public ObjectMeta leadObjectMeta(ObjectMetaRegistry registry) {
        ObjectMeta meta = new ObjectMeta(
            "LEAD",                     // objectKey
            "cust_lead",                // tableName
            "owner_org_id",             // ownerOrgCol
            "created_by",               // createdByCol
            null,                       // assigneeCol（线索无分配人）
            null,                       // selfCol
            "business_key",             // businessKeyCol
            null,                       // joinPolicyKey
            Set.of(                     // supportedScopes
                DataScopeType.SELF_CREATED,
                DataScopeType.ORG,
                DataScopeType.ORG_SUBTREE,
                DataScopeType.ALL,
                DataScopeType.WORKFLOW_PARTICIPANT
            ),
            "LEAD"                      // viewBizType
        );
        registry.register(meta);
        return meta;
    }
}
```

#### 强约束规则

1. **每个对象必须显式声明 `supportedScopes`**：不在集合中的 scope 类型请求到达时，必须 fail-fast 抛出异常，禁止默认降级为 `ALL`。
2. **未注册的对象禁止参与权限判定**：`ObjectMetaRegistry.require()` 对未注册对象抛 `IllegalStateException`。
3. **同一物理表可注册多个逻辑对象**：例如 `support_request` 表可同时注册为 `SUPPORT`（发起侧）和 `SUPPORT_DEPT`（承接部门侧），二者的 `ownerOrgCol`、`assigneeCol`、`supportedScopes` 可不同。
4. **`joinPolicyKey` 用于复杂关联场景**：当数据范围不能直接通过单表字段过滤时（如触达日志需要 join 触达任务表），通过 `joinPolicyKey` 指定预定义的 join 策略。

### 5.3 写前校验通用模式

所有写操作（更新、删除、提交审批、转交等）必须遵循以下标准校验流程：

```java
/**
 * 写操作通用校验模板
 * 适用于：UPDATE / DELETE / SUBMIT / TRANSFER 等操作
 */
public <T extends Ownable & Stateful> void preWriteCheck(String id, String operation) {
    // 1. 按主键取实体
    T entity = mapper.selectById(id);
    if (entity == null) {
        throw new BizException("{MODULE}-40401", "记录不存在");
    }

    // 2. 状态守卫：检查当前状态是否允许该操作
    StateGuard.check(entity.getStatus(), allowedStatuses(operation));

    // 3. 范围校验：检查当前用户是否有权操作该实体
    DataPermissionChecker.checkWrite(entity);

    // 4. 业务校验：执行业务规则验证
    BusinessValidator.validate(entity, operation);
}
```

#### StateGuard 实现

```java
package com.bank.branch.platform.common.guard;

import java.util.Set;

/**
 * 状态守卫
 * 确保实体在允许的状态下才能执行操作
 */
public final class StateGuard {

    /**
     * 检查当前状态是否在允许列表中
     *
     * @param currentStatus   实体当前状态
     * @param allowedStatuses 允许操作的状态集合
     * @throws BizException 状态不允许时抛出
     */
    public static void check(String currentStatus, Set<String> allowedStatuses) {
        if (!allowedStatuses.contains(currentStatus)) {
            throw new BizException("SYS-40901",
                String.format("当前状态 [%s] 不允许执行该操作", currentStatus));
        }
    }

    private StateGuard() {}
}
```

---

## 6. 统一审计切面使用方式

### 6.1 高危动作清单

以下操作被定义为高危动作，必须记录审计日志并通过独立 URL 单独授权：

| 动作标识 | 说明 | 是否需要 reason |
|---|---|---|
| `EXPORT` | 数据导出 | 否（但需记录过滤条件和行数） |
| `IMPORT` | 数据导入 | 是 |
| `DELETE` | 数据删除 | 是 |
| `TRANSFER` | 转交/移交 | 是 |
| `EXECUTE_SQL` | 执行自定义 SQL | 是 |
| `RECALC` | 绩效重算 | 是 |
| `PERMISSION_CHANGE` | 权限/角色配置变更 | 是 |
| `JOB_TRIGGER` | 手动触发定时任务 | 是 |

### 6.2 审计日志最小字段集

审计日志表 `audit_log` 必须包含以下最小字段集：

| 字段 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `id` | bigint | 是 | 主键，自增 |
| `action` | varchar(50) | 是 | 动作类型（EXPORT / IMPORT / DELETE / TRANSFER 等） |
| `resource_type` | varchar(50) | 是 | 资源类型，对应 BizType（如 LEAD / CUSTOMER / LOAN） |
| `resource_id` | varchar(100) | 否 | 资源 ID，对应被操作的实体主键 |
| `business_key` | varchar(100) | 否 | 业务键，流程类操作时记录 |
| `operator_emp_id` | varchar(50) | 是 | 操作人工号 |
| `operator_org_id` | varchar(20) | 是 | 操作人所属机构编码 |
| `request_time` | datetime | 是 | 请求时间 |
| `ip` | varchar(50) | 是 | 请求来源 IP 地址 |
| `user_agent` | varchar(256) | 否 | 浏览器 User-Agent |
| `trace_id` | varchar(64) | 是 | 全链路追踪 ID |
| `before_snapshot` | text | 否 | 变更前数据快照（JSON），DELETE / TRANSFER / PERMISSION_CHANGE 时必填 |
| `after_snapshot` | text | 否 | 变更后数据快照（JSON），IMPORT / PERMISSION_CHANGE 时必填 |
| `reason` | varchar(500) | 条件必填 | 操作原因，TRANSFER / DELETE / IMPORT / RECALC / PERMISSION_CHANGE 必填 |
| `extra_info` | text | 否 | 额外信息（如 EXPORT 的过滤条件、行数；IMPORT 的文件名、行数） |
| `success` | tinyint(1) | 是 | 操作是否成功（1=成功，0=失败） |
| `error_message` | varchar(500) | 否 | 失败时的错误信息 |

#### 建表语句

```sql
CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    action          VARCHAR(50)  NOT NULL COMMENT '动作类型',
    resource_type   VARCHAR(50)  NOT NULL COMMENT '资源类型（BizType）',
    resource_id     VARCHAR(100) NULL     COMMENT '资源ID',
    business_key    VARCHAR(100) NULL     COMMENT '业务键',
    operator_emp_id VARCHAR(50)  NOT NULL COMMENT '操作人工号',
    operator_org_id VARCHAR(20)  NOT NULL COMMENT '操作人机构',
    request_time    DATETIME     NOT NULL COMMENT '请求时间',
    ip              VARCHAR(50)  NOT NULL COMMENT '请求IP',
    user_agent      VARCHAR(256) NULL     COMMENT '浏览器信息',
    trace_id        VARCHAR(64)  NOT NULL COMMENT '追踪ID',
    before_snapshot TEXT         NULL     COMMENT '变更前（JSON）',
    after_snapshot  TEXT         NULL     COMMENT '变更后（JSON）',
    reason          VARCHAR(500) NULL     COMMENT '原因',
    extra_info      TEXT         NULL     COMMENT '额外信息',
    success         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功',
    error_message   VARCHAR(500) NULL     COMMENT '失败信息',
    created_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_action_time (action, request_time),
    INDEX idx_operator (operator_emp_id, request_time),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';
```

### 6.3 使用模式

#### 6.3.1 注解驱动模式

通过 `@AuditLog` 注解标注需要审计的方法，由 AOP 切面自动完成审计日志的采集和写入：

```java
package com.bank.branch.platform.common.audit;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 标注在 Service 方法上，由审计切面自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 动作类型 */
    String action();

    /** 资源类型（BizType） */
    String resourceType();

    /** 是否需要记录变更前后快照 */
    boolean snapshot() default false;

    /** 是否强制要求填写原因 */
    boolean reasonRequired() default false;
}
```

#### 6.3.2 注解使用示例

**权限变更审计**

```java
@AuditLog(action = "PERMISSION_CHANGE", resourceType = "ROLE_BIZ_SCOPE",
          snapshot = true, reasonRequired = true)
public void updateBizScope(BizScopeUpdateReqDTO req) {
    // 切面会自动在方法执行前获取变更前快照
    // 方法执行后获取变更后快照
    // 写入审计日志
    roleBizScopeMapper.updateByRoleIdAndBizType(req);
}
```

**数据删除审计**

```java
@AuditLog(action = "DELETE", resourceType = "LEAD",
          snapshot = true, reasonRequired = true)
public void deleteLead(String leadId, String reason) {
    CustLead entity = leadMapper.selectById(leadId);
    StateGuard.check(entity.getStatus(), Set.of("PENDING", "RETURNED"));
    DataPermissionChecker.checkWrite(entity);
    leadMapper.deleteById(leadId);
}
```

**数据导出审计**

```java
@AuditLog(action = "EXPORT", resourceType = "CUSTOMER")
public ExportTaskVO exportCustomers(CustomerExportReqDTO req) {
    // 切面会自动记录查询条件到 extraInfo
    ExportTask task = new ExportTask();
    task.setFilterConditions(JsonUtil.toJson(req));
    task.setStatus("PENDING");
    exportTaskMapper.insert(task);
    // 异步执行导出
    asyncExportService.executeAsync(task.getId());
    return convertToVO(task);
}
```

**转交操作审计**

```java
@AuditLog(action = "TRANSFER", resourceType = "SUPPORT",
          snapshot = true, reasonRequired = true)
public void transferSupportRequest(String requestId, String targetEmpId, String reason) {
    SupportRequest entity = supportRequestMapper.selectById(requestId);
    // 状态守卫 + 写权限校验 + 候选范围校验 + 组织边界校验
    StateGuard.check(entity.getStatus(), Set.of("PROCESSING"));
    DataPermissionChecker.checkWrite(entity);
    CandidateValidator.checkTarget(targetEmpId, entity.getSupportDeptId());

    entity.setAssignedEmpId(targetEmpId);
    entity.setUpdatedBy(DataScopeContext.current().user().empId());
    supportRequestMapper.updateById(entity);
}
```

#### 6.3.3 审计切面实现要点

```java
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        DataScopeContext.Value ctx = DataScopeContext.current();
        HttpServletRequest request = getCurrentRequest();

        // 1. reason 必填校验
        if (auditLog.reasonRequired()) {
            String reason = extractReason(joinPoint);
            if (reason == null || reason.isBlank()) {
                throw new BizException("AUTH-40304", "该操作需要填写原因");
            }
        }

        // 2. 变更前快照
        String beforeSnapshot = null;
        if (auditLog.snapshot()) {
            beforeSnapshot = captureBeforeSnapshot(joinPoint);
        }

        // 3. 执行目标方法
        Object result;
        boolean success = true;
        String errorMsg = null;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            // 4. 变更后快照
            String afterSnapshot = null;
            if (auditLog.snapshot() && success) {
                afterSnapshot = captureAfterSnapshot(joinPoint);
            }

            // 5. 写入审计日志（异步写入，不影响主流程性能）
            auditLogService.saveAsync(AuditLogEntry.builder()
                .action(auditLog.action())
                .resourceType(auditLog.resourceType())
                .resourceId(extractResourceId(joinPoint))
                .operatorEmpId(ctx.user().empId())
                .operatorOrgId(ctx.user().mainOrgCode())
                .requestTime(LocalDateTime.now())
                .ip(IpUtil.getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .traceId(TraceIdUtil.currentTraceId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(afterSnapshot)
                .reason(extractReason(joinPoint))
                .success(success)
                .errorMessage(errorMsg)
                .build());
        }

        return result;
    }
}
```

---

## 7. 领域事件发布规范

### 7.1 命名规范

所有领域事件和集成事件的类型名必须遵循以下格式：

#### 领域事件

格式：`<domain>.<aggregate>.<past-tense>.v1`

| 示例 | 说明 |
|---|---|
| `customer.claim.created.v1` | 客户认领创建 |
| `customer.claim.released.v1` | 客户认领释放 |
| `lead.lead.submitted.v1` | 线索已提交 |
| `lead.lead.approved.v1` | 线索已审批通过 |
| `loan.application.created.v1` | 贷款申请创建 |
| `support.request.transferred.v1` | 中场支持已转交 |
| `touch.task.completed.v1` | 触达任务已完成 |

#### 集成事件

格式：`<context>.<event>.v1`

| 示例 | 说明 |
|---|---|
| `workflow.task-assigned.v1` | 工作流任务已分配 |
| `performance.score-calculated.v1` | 绩效已计算完成 |
| `notification.message-sent.v1` | 通知已发送 |

#### 命名规则

1. **使用小写字母和连字符**：单词间使用 `.` 分隔层级，同层级复合词使用 `-` 连接。
2. **动词使用过去时态**：事件描述已经发生的事实，必须使用过去分词形式。
3. **版本号必须携带**：格式为 `.v{N}`，便于后续升级事件结构时向前兼容。
4. **禁止使用缩写**：事件名应完整可读。

### 7.2 消息结构

所有事件消息必须遵循以下统一 JSON 结构：

```json
{
  "eventId": "01HTX9J3Y0R4T7M5X8N2",
  "eventType": "customer.claim.created.v1",
  "occurredAt": "2026-03-06T10:30:00+08:00",
  "traceId": "9f3b6c1c7f0a4b31",
  "schemaVersion": "1.0",
  "operator": "E10001",
  "bizType": "CLAIM",
  "bizId": "CL202603060001",
  "payload": {
    "customerId": "C00012345",
    "claimType": "ACTIVE",
    "orgCode": "310001"
  }
}
```

#### 字段说明

| 字段 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `eventId` | String | 是 | 全局唯一事件 ID，建议使用 ULID 或 UUID，生产者必须保证唯一性 |
| `eventType` | String | 是 | 事件类型，遵循命名规范 |
| `occurredAt` | String | 是 | 事件发生时间，ISO 8601 格式，带时区偏移量 |
| `traceId` | String | 是 | 全链路追踪 ID，从请求上下文透传 |
| `schemaVersion` | String | 是 | 事件结构版本号，用于消费端判断兼容性 |
| `operator` | String | 是 | 操作人工号 |
| `bizType` | String | 是 | 业务类型（BizType） |
| `bizId` | String | 是 | 业务实体 ID |
| `payload` | Object | 是 | 事件业务数据负载，不同事件类型对应不同结构 |

#### Java 模型

```java
package com.bank.branch.platform.common.event;

import java.time.OffsetDateTime;

/**
 * 统一领域事件基类
 * 所有领域事件必须继承此类
 */
public abstract class DomainEvent {

    /** 全局唯一事件 ID */
    private final String eventId;

    /** 事件类型 */
    private final String eventType;

    /** 事件发生时间 */
    private final OffsetDateTime occurredAt;

    /** 全链路追踪 ID */
    private final String traceId;

    /** 事件结构版本号 */
    private final String schemaVersion;

    /** 操作人工号 */
    private final String operator;

    /** 业务类型 */
    private final String bizType;

    /** 业务实体 ID */
    private final String bizId;

    protected DomainEvent(String eventType, String bizType, String bizId) {
        this.eventId = UlidUtil.generate();
        this.eventType = eventType;
        this.occurredAt = OffsetDateTime.now();
        this.traceId = TraceIdUtil.currentTraceId();
        this.schemaVersion = "1.0";
        this.operator = CurrentUserContext.currentEmpId();
        this.bizType = bizType;
        this.bizId = bizId;
    }

    // ---- getter 省略 ----
}
```

### 7.3 幂等要求

领域事件的生产和消费必须满足幂等性要求：

#### 生产者端

1. **必须生成全局唯一 `eventId`**：推荐使用 ULID（有序且唯一）或 UUID v7。
2. **同一业务操作不能重复发布事件**：在事务提交后发布，确保"一次业务操作 = 一个事件"。
3. **事件发布失败时的重试机制**：V1 阶段可接受丢失，后续版本可通过 Outbox 模式保证。

#### 消费者端

1. **必须按 `eventId` 去重**：在消费前查询去重表，已处理过的事件直接跳过。
2. **重复消费不能产生重复业务副作用**：即使收到重复事件，业务结果必须保持一致。
3. **去重表建议结构**：

```sql
CREATE TABLE event_dedup (
    event_id     VARCHAR(64) PRIMARY KEY COMMENT '事件ID',
    event_type   VARCHAR(100) NOT NULL   COMMENT '事件类型',
    consumed_at  DATETIME NOT NULL       COMMENT '消费时间',
    INDEX idx_consumed_at (consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件消费去重表';
```

4. **去重记录保留策略**：建议保留 7 天，超期清理。

### 7.4 事务后发布原则

#### 核心规则

事件必须在数据库事务成功提交之后发布，确保事件与业务数据的一致性。

#### V1 实现方式

使用 Spring 的 `@TransactionalEventListener` 注解，指定在事务提交后执行：

```java
// 生产者：在 Service 层发布事件
@Service
public class ClaimServiceImpl implements ClaimService {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createClaim(ClaimCreateReqDTO req) {
        // 1. 业务逻辑
        CustClaim claim = new CustClaim();
        // ... 设置属性 ...
        claimMapper.insert(claim);

        // 2. 发布领域事件（此时事务尚未提交，事件仅注册，不会立即被消费）
        eventPublisher.publishEvent(new ClaimCreatedEvent(claim.getClaimId(), claim.getCustomerId()));
    }
}
```

```java
// 消费者：使用 @TransactionalEventListener
@Component
public class ClaimEventListener {

    /**
     * 客户认领创建后触发
     * phase = AFTER_COMMIT 确保只有事务成功提交后才执行
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimCreated(ClaimCreatedEvent event) {
        // 通知相关方、更新统计、触发后续流程等
        notificationService.sendClaimNotification(event.getClaimId());
    }
}
```

#### 重要约束

1. **对外事件必须在事务提交后发布**：使用 `TransactionPhase.AFTER_COMMIT`，避免事务回滚后已发出事件。
2. **Flowable 监听器只做流程侧元数据刷新**：Flowable 的 `TaskListener` / `ExecutionListener` 内部只负责更新 `biz_process_map` 等流程映射表，不做对外事件出口。对外事件统一由业务 Service 发布。
3. **事务后发布可能丢失事件**：V1 阶段可接受（概率极低），后续可升级为 Outbox 模式或事务消息。
4. **消费者异常不影响生产者事务**：`AFTER_COMMIT` 阶段的异常不会导致业务事务回滚，但需要记录日志并告警。

---

## 8. 统一数据传递规范

### 8.1 DTO 分层规则

| 层次 | 命名规范 | 说明 | 示例 |
|---|---|---|---|
| Controller 入参 | `*ReqDTO` | 请求数据传输对象 | `LeadCreateReqDTO`、`CustomerQueryReqDTO` |
| Controller 出参 | `*RespDTO` 或 `*VO` | 响应数据传输对象 / 视图对象 | `LeadRespDTO`、`CustomerVO` |
| 模块间传递 | `*DTO` | 跨模块 API 调用的数据载体 | `CustomerInfoDTO`、`OrgNodeDTO` |
| 内部 Service 层 | Entity / 内部 DTO | 模块私有，禁止对外暴露 | `CustLead`（Entity） |

### 8.2 强制规则

1. **Controller 入参使用 ReqDTO，出参使用 RespDTO/VO**：
   - 入参 DTO 负责参数校验（使用 `@Valid` + JSR 303 注解）。
   - 出参 VO 只包含前端需要展示的字段，禁止透传 Entity。

2. **模块间只传 DTO，禁传 Entity**：
   - 跨模块的 `*Api` / `*QueryApi` 接口的参数和返回值只能使用 DTO。
   - DTO 定义在各模块的 `api.dto` 包下。
   - 禁止将 Entity 类放在 `api` 包中。

3. **列表和导出分离设计**：
   - 列表接口和导出接口必须是独立的 URL，分别授权。
   - 列表接口：`GET /api/{resources}`。
   - 导出接口：`GET /api/{resources}/export`。
   - 导出接口需要独立的 `@BizAuth` 声明（`action = BizAction.EXPORT`）。

4. **跨模块查询必须分页**：
   - 默认 `pageSize = 20`，最大 `pageSize = 100`。
   - 禁止跨模块一次性查询全量数据。

5. **超过 5000 行的导出走异步导出任务**：
   - 前端发起导出请求，后端返回任务 ID。
   - 后端异步执行导出，生成文件上传到 MinIO。
   - 导出完成后通知前端下载。

6. **敏感字段日志输出脱敏**：
   - 以下字段在日志输出时必须脱敏处理：

| 字段类型 | 脱敏规则 | 示例 |
|---|---|---|
| 手机号 | 保留前 3 后 4，中间用 `****` 替换 | `138****5678` |
| 身份证号 | 保留前 3 后 4，中间用 `****` 替换 | `310****4321` |
| 银行账号 | 保留后 4 位，其余用 `****` 替换 | `****5678` |
| 金额 | 脱敏为 `***` | `***` |
| 姓名 | 保留姓，名用 `*` 替换 | `张*`、`欧阳**` |

### 8.3 DTO 设计示例

```java
// ---- 请求 DTO（入参） ----
package com.bank.branch.platform.customer.api.dto;

import jakarta.validation.constraints.*;

/**
 * 线索创建请求 DTO
 */
public class LeadCreateReqDTO {

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 50, message = "客户名称不能超过50个字符")
    private String customerName;

    /** 联系电话 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;

    /** 线索来源 */
    @NotBlank(message = "线索来源不能为空")
    private String source;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    // ---- getter / setter 省略 ----
}

// ---- 响应 VO（出参） ----
package com.bank.branch.platform.customer.api.dto;

/**
 * 线索视图对象
 */
public class LeadVO {

    /** 线索 ID */
    private String leadId;

    /** 客户名称 */
    private String customerName;

    /** 联系电话（脱敏后） */
    private String phoneNumber;

    /** 线索状态 */
    private String status;

    /** 线索来源 */
    private String source;

    /** 创建时间 */
    private String createdTime;

    /** 创建人姓名 */
    private String createdByName;

    // ---- getter / setter 省略 ----
}
```

### 8.4 跨模块调用示例

```java
// ---- 跨模块 QueryApi 定义（在 api 包中） ----
package com.bank.branch.platform.customer.api;

/**
 * 客户模块对外查询接口
 * 仅在 api 包中定义，其他模块通过此接口查询客户信息
 */
public interface CustomerQueryApi {

    /**
     * 根据客户 ID 查询基本信息
     *
     * @param customerId 客户 ID
     * @return 客户基本信息 DTO
     */
    CustomerInfoDTO queryCustomerById(String customerId);

    /**
     * 分页查询客户列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CustomerInfoDTO> queryCustomersByPage(CustomerPageQueryDTO query);
}

// ---- 其他模块调用方式 ----
@Service
public class LoanServiceImpl implements LoanService {

    private final CustomerQueryApi customerQueryApi;

    @Override
    public LoanDetailVO getLoanDetail(String loanId) {
        LoanApplication loan = loanMapper.selectById(loanId);
        // 通过 QueryApi 跨模块查询客户信息，禁止直接访问客户模块的 Mapper
        CustomerInfoDTO customer = customerQueryApi.queryCustomerById(loan.getCustomerId());
        return assembleLoanDetail(loan, customer);
    }
}
```

---

## 9. 统一日志规范

### 9.1 traceId 生成与传递

#### 生成规则

- traceId 在 Filter 层生成，格式为 16 位十六进制字符串（如 `9f3b6c1c7f0a4b31`）。
- 生成后立即注入 SLF4J MDC，键名为 `traceId`。
- 如果请求头中已携带 `X-Trace-Id`，则复用该值（支持上游系统传入）。

```java
package com.bank.branch.platform.common.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import java.io.IOException;
import java.util.UUID;

/**
 * 全链路追踪 Filter
 * 负责生成 traceId 并注入 MDC
 * 顺序：所有 Filter 中最先执行
 */
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        try {
            // 优先使用上游传入的 traceId
            String traceId = httpReq.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = generateTraceId();
            }

            // 注入 MDC
            MDC.put(TRACE_ID_KEY, traceId);

            // 在响应头中返回 traceId，便于前端排查
            httpResp.setHeader(TRACE_ID_HEADER, traceId);

            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

#### Logback 配置

```xml
<!-- logback-spring.xml 日志格式配置 -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

### 9.2 接口入口日志

每个接口调用入口必须记录以下信息：

| 字段 | 说明 |
|---|---|
| traceId | 全链路追踪 ID |
| requestPath | 请求路径（如 `/api/leads`） |
| httpMethod | HTTP 方法（GET / POST / PUT / DELETE） |
| requestParams | 请求参数（脱敏后），GET 请求记录 query 参数，POST 请求记录 body（截断至 2000 字符） |
| operatorEmpId | 操作人工号 |
| clientIp | 客户端 IP |

#### 日志格式示例

```
2026-03-06 10:30:00.123 [http-nio-8080-exec-1] [9f3b6c1c7f0a4b31] INFO  c.b.b.p.c.web.RequestLogInterceptor
- [REQUEST] POST /api/leads | emp=E10001 | ip=192.168.1.100 | params={"customerName":"张三","phoneNumber":"138****5678","source":"BRANCH_REFERRAL"}
```

### 9.3 接口出口日志

每个接口调用出口必须记录以下信息：

| 字段 | 说明 |
|---|---|
| traceId | 全链路追踪 ID |
| responseStatus | 响应状态码（HTTP Status + 业务 code） |
| duration | 接口耗时（毫秒） |

#### 日志格式示例

```
2026-03-06 10:30:00.234 [http-nio-8080-exec-1] [9f3b6c1c7f0a4b31] INFO  c.b.b.p.c.web.RequestLogInterceptor
- [RESPONSE] POST /api/leads | code=0 | http=200 | duration=111ms
```

### 9.4 慢查询告警

- 所有 API 响应时间超过 **5 秒**的请求必须以 WARN 级别记录日志。
- 慢查询日志需要额外记录 SQL 执行信息（通过 MyBatis 拦截器采集）。
- 建议配合 Druid 监控面板查看慢 SQL 统计。

```
2026-03-06 10:30:05.456 [http-nio-8080-exec-1] [9f3b6c1c7f0a4b31] WARN  c.b.b.p.c.web.RequestLogInterceptor
- [SLOW_REQUEST] GET /api/customers | code=0 | http=200 | duration=5123ms | emp=E10001
```

### 9.5 跨模块调用日志

跨模块调用（通过 `*Api` / `*QueryApi`）必须透传 traceId，并在调用入口和出口记录日志：

```
2026-03-06 10:30:00.150 [http-nio-8080-exec-1] [9f3b6c1c7f0a4b31] INFO  c.b.b.p.c.facade.LoanFacade
- [CROSS_MODULE_CALL] CustomerQueryApi.queryCustomerById | input={"customerId":"C00012345"} | duration=15ms | result=OK
```

#### 跨模块调用规范

1. **traceId 必须透传**：跨模块调用时从 MDC 获取 traceId，传递给被调用模块。由于本系统为模块化单体架构（同一 JVM），MDC 中的 traceId 自然透传，无需额外处理。
2. **入参和出参记录**：跨模块调用的入参和出参需要记录到日志中（敏感字段脱敏）。
3. **耗时记录**：记录每次跨模块调用的耗时，便于性能分析和瓶颈定位。
4. **异常记录**：跨模块调用异常时以 ERROR 级别记录，包含完整异常信息。

### 9.6 日志级别使用规范

| 级别 | 使用场景 |
|---|---|
| `ERROR` | 系统异常、数据库异常、外部服务调用失败等需要立即关注的错误 |
| `WARN` | 慢查询（>5s）、权限校验失败、降级处理、非致命异常 |
| `INFO` | 接口入口/出口日志、业务关键节点（状态变更、审批通过等）、跨模块调用 |
| `DEBUG` | 详细的执行过程（仅开发环境启用）、SQL 参数、缓存命中情况 |

### 9.7 日志安全规范

1. **禁止记录密码**：任何场景下禁止将密码明文或密文记录到日志。
2. **敏感字段脱敏**：参照第 8 章数据传递规范中的脱敏规则。
3. **日志中禁止记录完整 SQL**：生产环境禁止将包含真实数据的完整 SQL 输出到日志。
4. **异常堆栈控制**：ERROR 级别记录完整堆栈；WARN 级别仅记录一行异常信息；日志输出禁止使用 `e.printStackTrace()`，统一使用 `log.error("message", e)`。
5. **日志文件安全**：日志文件权限仅限运维人员访问，禁止通过 API 暴露日志内容。

---

## 附录 A：开发 Checklist

每次新功能开发或接口变更时，请对照以下清单逐项检查：

- [ ] 新增接口前确定归属模块，禁止"顺手写到别的模块"
- [ ] 每个新接口必须登记到 `PT_RESOURCE` 并声明 `@BizAuth`
- [ ] 跨模块调用必须走 `*Api` / `*QueryApi`，禁止直连 `mapper` / `entity`
- [ ] 所有写操作必须在 Service 层基于实体做二次权限校验
- [ ] 所有读接口、导出接口必须应用统一 `DATA_SCOPE`
- [ ] 高危操作必须独立 URL、单独授权、单独审计
- [ ] 所有流程类业务必须维护 `business_key` 和 `biz_process_map`
- [ ] 所有 Service 类与 public 方法必须补齐注释
- [ ] 所有接口记录入参/出参、traceId 和耗时
- [ ] 排序字段做白名单校验，防止 SQL 注入
- [ ] 敏感字段日志输出已脱敏
- [ ] ObjectMeta 已注册并声明 supportedScopes
- [ ] 错误响应使用规范错误码，不暴露数据库异常原文
- [ ] 领域事件在事务提交后发布
- [ ] 消费者按 eventId 去重

## 附录 B：模块前缀速查表

| 模块 | 前缀 | 包名 |
|---|---|---|
| 通用 | SYS | `com.bank.branch.platform.common` |
| 认证授权中心 | AUTH | `com.bank.branch.platform.auth` |
| 系统治理中心 | GOV | `com.bank.branch.platform.governance` |
| 门户与内容中心 | PORTAL | `com.bank.branch.platform.portal` |
| 客户营销中心 | CUST | `com.bank.branch.platform.customer` |
| 工作流中心 | WF | `com.bank.branch.platform.workflow` |
| 业务申请中心 | BIZ | `com.bank.branch.platform.business` |
| 绩效计算中心 | PERF | `com.bank.branch.platform.performance` |
| 报表分析中心 | RPT | `com.bank.branch.platform.report` |
