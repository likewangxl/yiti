# COMMON + AUTH 模块拆分设计规格

> 日期：2026-04-02
> 状态：已批准
> 范围：补全缺口 + 拆分出 COMMON 基础层和 AUTH 认证授权中心模块文档

---

## 1. 背景与目标

项目已有两份核心文档：
- `project_ana.md` — 功能规格说明书
- `project_ana_技术方案与架构拆分.md` — 技术方案与架构设计

目标是将这两份文档按模块拆分为可直接用于开发的独立模块文档。本次先补全公共缺口，再拆分 COMMON 和 AUTH 两个模块。

### 1.1 需要补全的缺口

| 缺口 | 说明 | 解决方式 |
|---|---|---|
| 公共开发规范缺失 | 鉴权链路、响应模型、错误码等跨模块知识无独立文档 | 新建 `docs/common-dev-guide.md` |
| 接口报文定义缺失 | 无完整的 HTTP 接口 ReqDTO/RespDTO 定义 | 在 AUTH 模块 `03-接口设计与报文.md` 中补全 |
| DATA_SCOPE→SQL 模板映射缺失 | 7 种 DataScopeType 无通用 SQL 谓词模板 | 纳入 `common-dev-guide.md` |
| 领域事件契约不完整 | AUTH 相关事件（权限缓存失效）未定义 payload | 在 AUTH `04-对外API契约.md` 中补全 |
| 对外 API 缺 Java 方法签名级定义 | 仅列出 API 名称 | 在 AUTH `04-对外API契约.md` 中补全 |

---

## 2. 产出物结构

```
docs/
├── common-dev-guide.md                    ← 公共开发规范（所有模块共享）
├── modules/
│   ├── common/                            ← COMMON 基础层
│   │   ├── 01-功能规格.md
│   │   ├── 02-后端架构.md
│   │   └── 03-关键组件设计.md
│   ├── auth-permission-center/            ← AUTH 认证授权中心
│   │   ├── 01-功能规格.md
│   │   ├── 02-后端架构.md
│   │   ├── 03-接口设计与报文.md           ★ 最大增量
│   │   ├── 04-对外API契约.md
│   │   ├── 05-表结构DDL.md
│   │   ├── 06-并发与事务策略.md
│   │   ├── 07-审计要求.md
│   │   └── 08-初始化数据清单.md
├── schema/                                ← 已有 DDL 文件
└── workflow/                              ← BPMN 文件
```

---

## 3. common-dev-guide.md 内容设计

### 3.1 统一响应模型
- `ResponseWrapper` JSON 结构（code/message/traceId/data/page/timestamp）
- code=0 表示成功，错误时返回业务错误码
- 分页响应 vs 非分页响应区别
- 错误响应禁止回传数据库异常原文

### 3.2 统一错误码规范
- 前缀体系：AUTH/GOV/PORTAL/CUST/WF/BIZ/PERF/RPT
- 错误码格式：`{模块前缀}-{HTTP状态码}{序号}`
- 权限类错误码详表（AUTH-40101 ~ AUTH-40401）

### 3.3 统一分页参数
- pageNo(int, default=1, >=1)
- pageSize(int, default=20, 1~100)
- sortBy(string, default=createdTime, 白名单)
- sortDir(string, default=desc, asc/desc)

### 3.4 鉴权链路使用指南
- Filter → Interceptor → @BizAuth → DataPermissionChecker → AuditService
- `@BizAuth(bizType, action)` 注解使用方式与示例
- `DataScopeContext` 获取与线程安全
- 写操作二次校验模式

### 3.5 DATA_SCOPE → SQL 过滤条件模板
- 7 种 DataScopeType 的通用 SQL 谓词模板
- ObjectMeta Registry 注册规范
- fail-fast 原则：未声明的 scope 直接拒绝
- 特殊场景处理：WORKFLOW_PARTICIPANT、客户历史只读旁路

### 3.6 统一审计切面使用方式
- 高危动作清单
- 审计日志最小字段集
- `@AuditLog` 注解使用模式
- 审计记录示例

### 3.7 领域事件发布规范
- 命名规范：`<domain>.<aggregate>.<past-tense>.v1`
- 消息结构（eventId/eventType/occurredAt/traceId/operator/bizType/bizId/payload）
- 幂等要求（eventId 去重）
- 事务后发布原则

### 3.8 统一数据传递规范
- Controller 入参用 ReqDTO，出参用 RespDTO/VO
- 模块间只传 DTO，禁传 Entity
- 列表与导出分离
- 敏感字段脱敏规则

### 3.9 统一日志规范
- traceId 生成与 MDC 注入
- 入参/出参日志切面
- 敏感字段脱敏

---

## 4. COMMON 基础层内容设计

### 4.1 01-功能规格.md
- common-web：ResponseWrapper、分页模型、统一异常处理、参数校验
- common-trace：TraceIdFilter、MDC 工具、链路上下文
- common-aop：接口出入参日志、方法耗时、高危动作 AOP
- common-db：MyBatis 分页插件、审计字段自动填充、慢 SQL 拦截
- common-security：@BizAuth 注解定义、DataScopeType 枚举、脱敏工具

### 4.2 02-后端架构.md
- 包结构：common-web / common-trace / common-aop / common-db / common-security
- 依赖原则：所有业务模块依赖 common，common 不依赖任何业务模块
- 模块边界：common 只提供工具和框架，不包含业务逻辑

### 4.3 03-关键组件设计.md
- ResponseWrapper、PageResult、PageRequest 模型定义
- BizType / BizAction / DataScopeType 枚举定义
- @BizAuth 注解定义（属性：bizType、action）
- DataScopeContext 线程上下文模型
- CurrentUserContext 模型
- ObjectMeta Registry 模型（objectKey/tableName/ownerOrgCol/...）
- 通用异常类层次（BizException / AuthException / PermissionDeniedException）
- 审计注解定义

---

## 5. AUTH 模块内容设计

### 5.1 01-功能规格.md
从 project_ana.md 裁剪：
- 2.x 用户角色与权限矩阵
- 2.1 V1 权限矩阵（BizType 绑定、角色×BizType×DataScope 矩阵）
- 4.6.1 RBAC 权限与数据隔离（完整的 4.6.1.1 ~ 4.6.1.6）
- 跨模块调用点标注

### 5.2 02-后端架构.md
- 模块职责边界：负责什么 / 不负责什么
- 模块依赖：只依赖 common，被所有业务模块依赖
- 包结构（api / controller / facade / service / mapper / entity / security / annotation / enums）
- 模块内分层规范
- 错误码前缀 AUTH

### 5.3 03-接口设计与报文.md（★ 最大增量）

完整的 HTTP 接口定义，每个接口包含：
- HTTP Method + URL
- 请求体 ReqDTO（字段名、类型、是否必填、校验注解、字典取值、示例值）
- 响应体 RespDTO（字段名、类型、说明、嵌套结构）
- 错误响应示例

接口分组：

**A. 会话管理（3 个接口）**
- POST /api/auth/login — 用户登录
- POST /api/auth/logout — 用户登出
- GET /api/auth/current-user — 获取当前用户信息

**B. 角色管理（5 个接口）**
- GET /api/admin/roles — 角色列表（分页）
- POST /api/admin/roles — 新增角色
- PUT /api/admin/roles/{roleId} — 编辑角色
- DELETE /api/admin/roles/{roleId} — 删除角色（逻辑删除）
- GET /api/admin/roles/{roleId}/users — 查询角色下用户

**C. 用户角色绑定（3 个接口）**
- GET /api/admin/users/{userId}/roles — 查询用户角色
- POST /api/admin/users/{userId}/roles — 绑定角色
- DELETE /api/admin/users/{userId}/roles/{roleId} — 解绑角色

**D. 资源管理（5 个接口）**
- GET /api/admin/resources/tree — 资源树查询
- POST /api/admin/resources — 新增资源
- PUT /api/admin/resources/{resourceId} — 编辑资源
- DELETE /api/admin/resources/{resourceId} — 删除资源
- GET /api/admin/roles/{roleId}/resources — 查询角色已绑定资源

**E. 角色资源绑定（2 个接口）**
- POST /api/admin/roles/{roleId}/resources — 批量绑定角色资源
- PUT /api/admin/roles/{roleId}/resources — 全量更新角色资源绑定

**F. BizScope 配置（4 个接口）**
- GET /api/admin/biz-scopes — 查询 BizScope 配置列表
- GET /api/admin/biz-scopes/matrix — 查询角色×BizType 矩阵
- POST /api/admin/biz-scopes — 保存/更新 BizScope 配置
- DELETE /api/admin/biz-scopes/{id} — 删除 BizScope 配置

**G. 组织机构（3 个接口）**
- GET /api/orgs/tree — 组织机构树
- GET /api/orgs/{orgCode}/users — 查询机构下用户
- GET /api/orgs/subtree — 查询当前用户组织子树

**H. 权限查询（2 个接口）**
- GET /api/auth/permissions — 获取当前用户完整权限集合（用于前端菜单/按钮渲染）
- POST /api/auth/check-permission — 校验当前用户对指定资源的权限

### 5.4 04-对外API契约.md

Java 方法签名级定义：

```
AuthApi
├── CurrentUserContext getCurrentUser()
├── boolean isAuthenticated()
└── void invalidateSession(String empId)

CurrentUserApi
├── CurrentUserContext getCurrentUserContext()
├── String getCurrentEmpId()
├── String getCurrentOrgCode()
└── Set<String> getCurrentRoleIds()

ResourceApi
├── Optional<ResourceDTO> matchResource(String url, String method)
├── boolean hasResourcePermission(String empId, String resourceId)
└── List<ResourceDTO> listUserResources(String empId)

BizScopeApi
├── DataScopeType resolveScope(String empId, BizType bizType)
├── DataScopeContext buildScopeContext(String empId, BizType bizType, BizAction action)
└── boolean checkWritePermission(String empId, BizType bizType, String entityOwnerId)

OrgApi
├── OrgDTO getOrg(String orgCode)
├── List<OrgDTO> getOrgSubtree(String orgCode)
├── List<String> getOrgSubtreeCodes(String orgCode)
└── OrgDTO getUserMainOrg(String empId)
```

领域事件：
- `governance.permission-cache.invalidated.v1` — 权限配置变更后缓存失效通知

### 5.5 05-表结构DDL.md
- 引用 `docs/schema/ddl-auth.sql` 已有 DDL
- 补充约束说明：主键策略、逻辑外键关联、审计字段规范
- 补充 DATA_SCOPE 枚举值与 BizType 枚举值映射

### 5.6 06-并发与事务策略.md
- 权限缓存失效策略（≤5分钟生效窗口）
- 角色/资源变更事务边界
- 多角色并集的一致性保证
- 缓存失效后 Fail Close 原则

### 5.7 07-审计要求.md
- AUTH 模块高危动作清单：PERMISSION_CHANGE
- 涉及接口：角色管理、资源管理、BizScope 配置、角色资源绑定
- 审计记录示例
- 权限变更前后快照

### 5.8 08-初始化数据清单.md
- 指向 `docs/schema/seed-v1.sql` 中 AUTH 相关部分
- V1 默认角色清单（11个角色）
- V1 默认 BizScope 矩阵（对应 2.1.2 的建议值）
- 资源登记基线（指向 permission_code_skeleton_v1.md）

---

## 6. COMMON 与 AUTH 的分工边界

| 内容 | 归属 | 说明 |
|---|---|---|
| `@BizAuth` 注解定义 | common-security | 纯注解，无业务逻辑 |
| `@BizAuth` 拦截实现 | auth-permission-center | HandlerInterceptor |
| `BizType` / `BizAction` / `DataScopeType` 枚举 | common-security | 所有模块引用 |
| `CurrentUserContext` 模型 | common-security | 数据结构定义 |
| `CurrentUserProvider` 实现 | auth-permission-center | 从 Session/Token 解析 |
| `ResourceMatcher` | auth-permission-center | PT_RESOURCE 匹配 |
| `RbacAuthorizer` | auth-permission-center | 角色资源校验 |
| `BizScopeService` | auth-permission-center | 读取并合并多角色范围 |
| `DataScopeContext` | auth-permission-center | ThreadLocal 管理 |
| `DataPermissionChecker` | auth-permission-center | 实体级校验 |
| `ObjectMeta` 模型定义 | common-security | record 定义 |
| `ObjectMetaRegistry` 实现 | auth-permission-center | 注册表管理 |
| `AuditService` 接口 | common-aop | 接口定义 |
| `AuditService` 实现 | system-governance-center | 落库实现 |
| 通用异常类 | common-web | BizException 等 |
| ResponseWrapper | common-web | 统一响应 |

---

## 7. 数据来源映射

| 产出物内容 | 数据来源 |
|---|---|
| AUTH 功能规格 | project_ana.md §2.x + §4.6.1 |
| AUTH 后端架构 | 技术方案 §2 + §3 + §6 |
| AUTH 接口报文 | 从功能规格推导设计（最大增量） |
| AUTH 对外 API | 技术方案 §2 模块定义表 + §4.1.3 + §4.2 |
| AUTH 表结构 | docs/schema/ddl-auth.sql |
| AUTH 并发事务 | 技术方案 §3.3.4 + project_ana §4.6.1.6 |
| AUTH 审计 | project_ana §4.6.1.5 |
| AUTH 初始化数据 | docs/schema/seed-v1.sql |
| COMMON 全部内容 | 技术方案 §2.2 + §4.1~4.4 + §6 + §7 |
| 公共开发规范 | 技术方案 §4.x + §7.x + data_scope_field_mapping_v1.md + permission_code_skeleton_v1.md |
