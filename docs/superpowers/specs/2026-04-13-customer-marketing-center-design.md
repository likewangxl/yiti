# customer-marketing-center 设计规格

> **模块**: 客户营销中心  
> **日期**: 2026-04-13  
> **状态**: Draft  
> **依赖文档**: `docs/modules/customer-marketing-center/01~09` (9 份详细设计文档)  
> **统一响应**: 所有接口使用 `ResponseWrapper<T>` 统一响应模型（见 `common-dev-guide.md` 第 1 章）

---

## 1. 概述

### 1.1 模块定位

客户营销中心是银行分行业务平台的核心域模块，负责客户全生命周期管理：从标签管理、线索录入审批、客户主档生成、客户池认领，到触达任务跟进的完整销售链路。

### 1.2 开发范围

本次开发覆盖全部 **7 个能力域**：

| 能力域 | 主要职责 | 核心表 |
|--------|----------|--------|
| 标签管理 | 全行统一标签字典、客户打标、标签客户导入/导出 | `cust_tag` / `cust_tag_rel` |
| 线索管理 | 版本化 CRUD、审批流程、批量导入 | `cust_lead` / `lead_import_batch` |
| 客户主档 | 统一客户视图、自动生成、跨机构历史、转交、删除申请 | `cust_master` |
| 客户池 | 待认领池管理 | `cust_claim` |
| 认领管理 | 争抢认领、取消、已认领列表 | `cust_claim` |
| 触达任务 | 首次触达、SLA 红绿灯、任务完成/取消、触达日志 | `touch_task` / `touch_log` |
| 触达管理视图 | 触达报告列表、统计、导出 | 聚合查询（无独立表） |

### 1.3 排除范围

- 依赖 `portal-content-center` 的功能（ProductApi、AddressBookApi 弱依赖），待该模块完成后补充
- 前端页面（纯后端模块）

---

## 2. 模块结构

### 2.1 Maven 模块

```xml
<!-- customer-marketing-center/pom.xml -->
<parent>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>branch-platform</artifactId>
</parent>
<artifactId>customer-marketing-center</artifactId>

<dependencies>
    <!-- common 基础设施 -->
    <dependency>common-web</dependency>
    
    <!-- 强依赖模块 -->
    <dependency>auth-permission-center</dependency>
    <dependency>workflow-center</dependency>
    
    <!-- 弱依赖模块 -->
    <dependency>system-governance-center</dependency>
    <!-- portal-content-center: 待实现后添加 -->
</dependencies>
```

### 2.2 包结构

```
com.bank.branch.platform.customer/
├── api/                     # 对外 API 接口（唯一可跨模块依赖）
│   ├── CustomerQueryApi.java
│   ├── LeadApi.java
│   ├── ClaimApi.java
│   ├── TouchTaskQueryApi.java
│   ├── TagApi.java
│   └── dto/                 # 对外 DTO
├── controller/              # REST 控制器 (10 个)
│   ├── TagController.java
│   ├── TagImportController.java
│   ├── LeadController.java
│   ├── LeadImportController.java
│   ├── CustomerController.java
│   ├── CustomerHistoryController.java
│   ├── CustomerPoolController.java
│   ├── ClaimController.java
│   ├── TouchTaskController.java
│   └── TouchReportController.java
├── facade/                  # 对外 API 实现 (7 个, *ApiImpl 命名)
│   ├── TagApiImpl.java
│   ├── LeadApiImpl.java
│   ├── CustomerApiImpl.java
│   ├── CustomerQueryApiImpl.java
│   ├── ClaimApiImpl.java
│   ├── TouchTaskApiImpl.java
│   └── TouchTaskQueryApiImpl.java
├── service/                 # 业务逻辑 (14 个)
│   ├── TagService.java
│   ├── TagImportService.java
│   ├── LeadService.java
│   ├── LeadImportService.java
│   ├── LeadVersionService.java
│   ├── CustomerService.java
│   ├── CustomerHistoryService.java
│   ├── CustomerPoolService.java
│   ├── ClaimService.java
│   ├── TouchTaskService.java
│   ├── TouchLogService.java
│   ├── TouchReportService.java
│   ├── ParallelFlowChecker.java
│   └── CustMasterAssemblerService.java
├── mapper/                  # MyBatis Mapper (8 个, 模块私有)
│   ├── CustTagMapper.java
│   ├── CustTagRelMapper.java
│   ├── CustLeadMapper.java
│   ├── LeadImportBatchMapper.java
│   ├── CustMasterMapper.java
│   ├── CustClaimMapper.java
│   ├── TouchTaskMapper.java
│   └── TouchLogMapper.java
├── entity/                  # 数据库实体（模块私有）
├── listener/                # 领域事件监听器
│   ├── LeadApprovedListener.java
│   ├── LeadDeletedListener.java
│   ├── TouchTaskCompletedListener.java
│   └── WorkflowCallbackListener.java
├── event/                   # 领域事件定义
│   ├── LeadApprovedEvent.java
│   ├── ClaimCreatedEvent.java
│   ├── ClaimCancelledEvent.java
│   ├── ClaimTransferredEvent.java
│   ├── TouchCompletedEvent.java
│   └── CustomerDeletedEvent.java
├── enums/                   # 枚举
│   ├── CustomerErrorCode.java
│   ├── LeadStatus.java
│   ├── LeadOp.java
│   ├── BatchStatus.java
│   ├── ClaimStatus.java
│   ├── TagStatus.java
│   ├── TouchTaskStatus.java
│   ├── TouchTaskType.java
│   └── CustMasterStatus.java
├── convert/                 # MapStruct 转换器
├── dto/                     # Controller 层 DTO
│   ├── req/                 # 请求 DTO
│   └── resp/                # 响应 DTO
└── config/                  # 模块配置
```

### 2.3 依赖关系

```
customer-marketing-center 依赖：
├── common (common-web → common-trace → common-security → common-aop → common-db)  [强依赖]
├── auth-permission-center (CurrentUserApi, BizScopeApi, OrgApi, EmployeeApi)  [强依赖]
├── workflow-center (WorkflowApi, WorkflowQueryApi)  [强依赖]
└── system-governance-center (DictApi, FileApi, AuditApi)  [弱依赖]

被以下模块依赖：
← business-application-center (CustomerQueryApi)
← performance-engine-center (ClaimApi + CustomerQueryApi)
← report-analytics-center (CustomerQueryApi, 只读)
← workflow-center (LeadApi/TouchTaskQueryApi, 流程回写)
```

---

## 3. 数据库设计

8 张表，完整 DDL 见 `docs/modules/customer-marketing-center/05-表结构DDL.md`。

### 3.1 表清单

| 表名 | 说明 | 主键 | 关键唯一约束 |
|------|------|------|-------------|
| `cust_tag` | 客户标签 | UUID | `uk_tag_name`, `uk_tag_code` |
| `cust_tag_rel` | 客户-标签关联 | UUID | `uk_cust_tag(cust_id,tag_id)` |
| `cust_lead` | 客户线索（版本管理） | UUID | `uk_lead_no` |
| `lead_import_batch` | 线索导入批次 | UUID | `uk_batch_no` |
| `cust_master` | 客户主档 | UUID | `uk_cust_no`, `uk_cust_name` |
| `cust_claim` | 客户认领关系 | UUID | `uk_cust_org(cust_id,org_id)` |
| `touch_task` | 触达任务 | UUID | `uk_task_no` |
| `touch_log` | 触达日志 | UUID | `uk_task_uuid(touch_task_id,client_uuid)` |

### 3.2 关键枚举值（与 DDL 严格对齐）

| 表 | 字段 | 枚举值 | DEFAULT |
|----|------|--------|---------|
| `cust_tag` | `status` | `ACTIVE` / `DISABLED` | `ACTIVE` |
| `cust_lead` | `lead_status` | `DRAFT` / `SUBMITTED` / `IN_APPROVAL` / `APPROVED` / `REJECTED` | `DRAFT` |
| `cust_lead` | `lead_op` | `CREATE` / `UPDATE` / `DELETE` | — |
| `cust_master` | `status` | `ACTIVE` / `INACTIVE` | `ACTIVE` |
| `touch_task` | `task_status` | `PENDING` / `SUCCESS` / `CANCELLED` | `PENDING` |
| `touch_task` | `sla_status` | `GREEN` / `YELLOW` / `RED` | `GREEN` |

### 3.3 关键设计要点

**线索版本控制** (`cust_lead`):
- `lead_op`: CREATE/UPDATE/DELETE（操作类型）
- `is_latest`: 1/0（最新版本标记）
- `version_no`: 版本号（从 1 开始递增）
- `prev_lead_id`: 上一版本指针
- `lead_status`: 五态状态机 DRAFT → SUBMITTED → IN_APPROVAL → APPROVED / REJECTED

**认领防重复** (`cust_claim`):
- 唯一键 `(cust_id, org_id)` 天然互斥
- `claimed_by` + `maintainer_emp_id` 角色分离

**触达幂等** (`touch_log`):
- 唯一键 `(touch_task_id, client_uuid)`
- `client_uuid` 由移动端生成

---

## 4. 各能力域设计

### 4.1 标签管理域

**完整接口清单 (7 个)**:

| 方法 | 路径 | 说明 | BizAuth |
|------|------|------|---------|
| GET | `/api/tags` | 标签列表（分页） | `@BizAuth(bizType=TAG, action=LIST)` |
| GET | `/api/tags/enabled` | 启用标签列表（选择器） | — |
| POST | `/api/tags` | 新增标签 | `@BizAuth(bizType=TAG, action=WRITE)` |
| PUT | `/api/tags/{id}` | 编辑标签 | `@BizAuth(bizType=TAG, action=WRITE)` |
| PUT | `/api/tags/{id}/status` | 启用/禁用标签 | `@BizAuth(bizType=TAG, action=WRITE)` |
| POST | `/api/tags/{id}/customers/import` | 标签关联客户批量导入 | `@BizAuth(bizType=TAG, action=IMPORT, highRisk=true)` |
| GET | `/api/tags/{id}/customers/export` | 导出标签客户 | `@BizAuth(bizType=TAG, action=EXPORT, highRisk=true)` |

**业务规则**:
- `tag_name` 全行唯一（忽略大小写）
- `tag_code` 创建后锁定，不可修改
- 状态枚举: `ACTIVE`（启用）/ `DISABLED`（停用），禁用 ≠ 删除
- 禁用标签不出现在选择器（`/api/tags/enabled`）
- 启用标签列表缓存 5min，标签变更时 evict
- 标签客户导入为覆盖式（delete + insert），单标签粒度

### 4.2 线索管理域

**完整接口清单 (11 个)**:

| 方法 | 路径 | 说明 | BizAuth |
|------|------|------|---------|
| GET | `/api/leads` | 线索列表（分页） | `@BizAuth(bizType=LEAD, action=LIST)` |
| GET | `/api/leads/{id}` | 线索详情 | `@BizAuth(bizType=LEAD, action=READ)` |
| POST | `/api/leads` | 新建线索草稿 | `@BizAuth(bizType=LEAD, action=WRITE)` |
| PUT | `/api/leads/{id}` | 编辑草稿 | `@BizAuth(bizType=LEAD, action=WRITE)` |
| POST | `/api/leads/{id}/submit` | 提交审批 | `@BizAuth(bizType=LEAD, action=WRITE)` |
| DELETE | `/api/leads/{id}` | 删除草稿 | `@BizAuth(bizType=LEAD, action=WRITE)` |
| POST | `/api/leads/{id}/edit` | 已通过线索生成新版本并重审 | `@BizAuth(bizType=LEAD, action=WRITE)` |
| POST | `/api/leads/import/preview` | 导入预览 | `@BizAuth(bizType=LEAD, action=IMPORT, highRisk=true)` |
| POST | `/api/leads/import` | 执行导入 | `@BizAuth(bizType=LEAD, action=IMPORT, highRisk=true)` |
| GET | `/api/leads/import/batches` | 导入批次列表 | `@BizAuth(bizType=LEAD, action=LIST)` |
| GET | `/api/leads/import/batches/{batchId}` | 批次详情 | `@BizAuth(bizType=LEAD, action=READ)` |

**状态机** (五态，与 DDL 对齐):
```
DRAFT → SUBMITTED → IN_APPROVAL → APPROVED / REJECTED
```

**版本化操作**:
- **CREATE**: 新建 v1，`is_latest=1`，`source_cust_id` 为空
- **UPDATE**: 通过 `/api/leads/{id}/edit` 基于 APPROVED 创建新版本，`prev_lead_id` 指向前版本，旧版本 `is_latest=0`
- **DELETE**: 创建删除版本，走审批流程

**审批集成**:
- `WorkflowApi.startProcess()` 发起审批
- 审批通过 → 发布 `LeadApprovedEvent` → 监听器更新线索状态 + 调用 `CustMasterAssemblerService` 自动生成客户主档
- `SELECT ... FOR UPDATE` 防止并发提交

**批量导入**:
- 预览阶段（`/api/leads/import/preview`）只读，不写库
- 执行阶段（`/api/leads/import`）整批事务，一失败全回滚

### 4.3 客户主档域

**完整接口清单 (6 个)**:

| 方法 | 路径 | 说明 | BizAuth |
|------|------|------|---------|
| GET | `/api/customers` | 客户列表（分页） | `@BizAuth(bizType=CUSTOMER, action=LIST)` |
| GET | `/api/customers/{id}` | 客户详情 | `@BizAuth(bizType=CUSTOMER, action=READ)` |
| GET | `/api/customers/{id}/history` | 跨机构全量历史（只读） | `@BizAuth(bizType=CUSTOMER, action=READ)` |
| POST | `/api/customers/{id}/transfer` | 转交维护负责人 | `@BizAuth(bizType=CUSTOMER, action=TRANSFER, highRisk=true)` |
| POST | `/api/customers/{id}/delete-apply` | 发起删除审批 | `@BizAuth(bizType=CUSTOMER, action=DELETE, highRisk=true)` |
| GET | `/api/customers/export` | 导出客户 | `@BizAuth(bizType=CUSTOMER, action=EXPORT, highRisk=true)` |

**关键逻辑**:
- 客户主档**自动生成**：线索审批通过后，`CustMasterAssemblerService` 从线索数据装配
- 客户状态: `ACTIVE`（正常）/ `INACTIVE`（非活跃），另有 `deleted` 字段做逻辑删除
- 跨机构历史：通过 `cust_claim` 关联查认领记录，只读（独立 Controller）
- 转交：更新 `maintainer_emp_id`，需 `reason`，高危操作
- 删除申请：通过工作流审批

### 4.4 客户池 + 认领域

**完整接口清单 (4 个)**:

| 方法 | 路径 | 说明 | BizAuth |
|------|------|------|---------|
| GET | `/api/customer-pool` | 待认领客户池列表 | `@BizAuth(bizType=CUSTOMER_POOL, action=LIST)` |
| POST | `/api/customer-pool/{custId}/claim` | 认领客户 | `@BizAuth(bizType=CLAIM, action=WRITE)` |
| GET | `/api/my-claims` | 已认领客户列表 | `@BizAuth(bizType=CLAIM, action=LIST)` |
| POST | `/api/claims/{claimId}/cancel` | 取消认领 | `@BizAuth(bizType=CLAIM, action=WRITE)` |

**认领争抢机制**:
- 客户审批通过 → 自动进入公海池
- 员工点击认领 → INSERT `cust_claim`
- 数据库唯一键 `(cust_id, org_id)` 天然互斥
- 插入失败 = 已被认领，返回 `CUST-40901`（客户已被其他客户经理认领）
- 无需分布式锁
- 发布 `ClaimCreatedEvent` / `ClaimCancelledEvent` 领域事件

### 4.5 触达任务域

**完整接口清单 (5 个)**:

| 方法 | 路径 | 说明 | BizAuth |
|------|------|------|---------|
| GET | `/api/touch-tasks` | 我的触达任务列表 | `@BizAuth(bizType=TOUCH_TASK, action=LIST)` |
| GET | `/api/touch-tasks/{id}` | 触达任务详情 | `@BizAuth(bizType=TOUCH_TASK, action=READ)` |
| POST | `/api/touch-tasks/{id}/complete` | 完成任务 | `@BizAuth(bizType=TOUCH_TASK, action=WRITE)` |
| POST | `/api/touch-tasks/{id}/cancel` | 取消任务 | `@BizAuth(bizType=TOUCH_TASK, action=WRITE)` |
| POST | `/api/touch-tasks/{id}/logs` | 添加触达日志 | `@BizAuth(bizType=TOUCH_TASK, action=WRITE)` |

**任务状态** (三态，与 DDL 对齐):
- `PENDING` → `SUCCESS` / `CANCELLED`

**SLA 红绿灯**:
- `sla_status`: GREEN → YELLOW → RED
- 定时任务 `TouchSlaRefreshJob`，cron: `0 */30 * * * ?`（每 30 分钟）
- 基于工作日历计算（剔除非工作日）
- `warning_time` 为黄灯阈值

**触达日志**:
- `(touch_task_id, client_uuid)` 唯一键防重
- 照片存 MinIO（通过 FileApi），路径存 JSON 字段
- 发布 `TouchCompletedEvent` 领域事件

### 4.6 触达管理视图

**完整接口清单 (3 个)**:

| 方法 | 路径 | 说明 | BizAuth |
|------|------|------|---------|
| GET | `/api/touch-reports` | 触达管理视图列表 | `@BizAuth(bizType=TOUCH_REPORT, action=LIST)` |
| GET | `/api/touch-reports/statistic` | 触达统计 | `@BizAuth(bizType=TOUCH_REPORT, action=LIST)` |
| GET | `/api/touch-reports/export` | 导出触达报告 | `@BizAuth(bizType=TOUCH_REPORT, action=EXPORT, highRisk=true)` |

- 聚合查询，基于 `touch_task` + `touch_log` 联合查询
- 无独立表

### 4.7 对外 API

5 个对外 API，7 个 `*ApiImpl` 实现类：

| API | 方法数 | 缓存 |
|-----|--------|------|
| `CustomerQueryApi` | 9 个 (getCustomer, listCustomers, searchCustomers, isValidCustomer, isClaimedByOrg, getCustomerClaims, hasRunningProcess, listRunningProcesses, countCustomers) | 部分 5min TTL |
| `LeadApi` | 线索详情、版本查询、批量查询 | 无 |
| `ClaimApi` | 获取认领机构、认领人、维护人 | 无 |
| `TouchTaskQueryApi` | 任务状态、SLA 查询 | 无 |
| `TagApi` | 启用标签列表、标签详情 | 5min TTL |

---

## 5. 横切关注点

### 5.1 权限与数据范围

所有 Controller 方法标注 `@BizAuth`，DataScope 通过 `common-db` SQL 模板自动注入。

| BizType | DataScope | 适用角色 |
|---------|-----------|----------|
| TAG | ALL | 公司部/零售部人员 |
| LEAD | SELF_CREATED / ORG_SUBTREE | 客户经理 / 负责人 |
| CUSTOMER | ORG_SUBTREE | 按机构层级 |
| CUSTOMER_POOL | ALL / ORG_SUBTREE | 全行/按机构 |
| CLAIM | ORG | 本机构认领 |
| TOUCH_TASK | SELF_ASSIGNED | 被指派人 |
| TOUCH_REPORT | ALL / ORG_SUBTREE | 管理员 / 负责人 |

### 5.2 审计

高危操作清单，`@AuditLog(level=HIGH)`，5 年留存：

| # | 操作 | 接口 | 要求 |
|---|------|------|------|
| 1 | 批量导入线索 | `POST /api/leads/import` | `reason` 必填 |
| 2 | 标签关联客户导入 | `POST /api/tags/{id}/customers/import` | `reason` 必填 |
| 3 | 标签客户导出 | `GET /api/tags/{id}/customers/export` | 实时 SMS 告警 |
| 4 | 客户列表导出 | `GET /api/customers/export` | 实时 SMS 告警 |
| 5 | 客户转交维护人 | `POST /api/customers/{id}/transfer` | `reason` 必填，次日报表 |
| 6 | 客户删除申请 | `POST /api/customers/{id}/delete-apply` | `reason` 必填 |
| 7 | 取消认领 | `POST /api/claims/{claimId}/cancel` | `reason` 必填 |
| 8 | 触达报告导出 | `GET /api/touch-reports/export` | 次日报表 |

**脱敏规则**:
- 手机号: `****1234`
- 身份证: `****5678`
- 金额: `***`

### 5.3 缓存策略

| 缓存项 | TTL | 失效策略 |
|--------|-----|----------|
| 启用标签列表 | 5min | 标签 CRUD 时 evict |
| 客户详情（对外 API） | 5min | 客户变更时 evict |
| 客户认领关系（对外 API） | 3min | 认领变更时 evict |

### 5.4 异常与错误码

错误码格式: `CUST-{HTTP状态码后两位}{序号}`（遵循 `common-dev-guide.md` 规范）

| 场景 | 错误码 | 说明 |
|------|--------|------|
| 客户记录不存在 | `CUST-40401` | 404 系列 |
| 客户已被其他客户经理认领 | `CUST-40901` | 409 系列（冲突） |
| 线索状态不允许提交 | `CUST-40001` | 400 系列（参数/状态错误） |
| 标签名已存在 | `CUST-40902` | 409 系列（冲突） |
| 标签编码已存在 | `CUST-40903` | 409 系列（冲突） |

完整错误码枚举见 `CustomerErrorCode.java`，详细定义见 `02-后端架构.md` 第 304-398 行。

### 5.5 领域事件

| 事件 | 触发时机 | 监听器动作 |
|------|----------|------------|
| `LeadApprovedEvent` | 线索审批通过 | 自动生成客户主档 |
| `LeadDeletedEvent` | 线索删除审批通过 | 标记客户主档 INACTIVE |
| `ClaimCreatedEvent` | 认领成功 | 创建首次触达任务 |
| `ClaimCancelledEvent` | 取消认领 | 取消未完成触达任务 |
| `ClaimTransferredEvent` | 转交维护人 | 更新触达任务负责人 |
| `TouchCompletedEvent` | 触达任务完成 | 记录审计日志 |
| `CustomerDeletedEvent` | 客户删除审批通过 | 清理关联数据 |

所有事件使用 `@TransactionalEventListener(AFTER_COMMIT)` 模式（见 `common-dev-guide.md` 第 7 章）。

---

## 6. 完整接口汇总

共 **36 个接口**：

| # | 方法 | 路径 | 域 | 高危 |
|---|------|------|----|------|
| 1 | GET | `/api/tags` | 标签 | |
| 2 | GET | `/api/tags/enabled` | 标签 | |
| 3 | POST | `/api/tags` | 标签 | |
| 4 | PUT | `/api/tags/{id}` | 标签 | |
| 5 | PUT | `/api/tags/{id}/status` | 标签 | |
| 6 | POST | `/api/tags/{id}/customers/import` | 标签 | Y |
| 7 | GET | `/api/tags/{id}/customers/export` | 标签 | Y |
| 8 | GET | `/api/leads` | 线索 | |
| 9 | GET | `/api/leads/{id}` | 线索 | |
| 10 | POST | `/api/leads` | 线索 | |
| 11 | PUT | `/api/leads/{id}` | 线索 | |
| 12 | POST | `/api/leads/{id}/submit` | 线索 | |
| 13 | DELETE | `/api/leads/{id}` | 线索 | |
| 14 | POST | `/api/leads/{id}/edit` | 线索 | |
| 15 | POST | `/api/leads/import/preview` | 线索 | Y |
| 16 | POST | `/api/leads/import` | 线索 | Y |
| 17 | GET | `/api/leads/import/batches` | 线索 | |
| 18 | GET | `/api/leads/import/batches/{batchId}` | 线索 | |
| 19 | GET | `/api/customers` | 客户 | |
| 20 | GET | `/api/customers/{id}` | 客户 | |
| 21 | GET | `/api/customers/{id}/history` | 客户 | |
| 22 | POST | `/api/customers/{id}/transfer` | 客户 | Y |
| 23 | POST | `/api/customers/{id}/delete-apply` | 客户 | Y |
| 24 | GET | `/api/customers/export` | 客户 | Y |
| 25 | GET | `/api/customer-pool` | 客户池 | |
| 26 | POST | `/api/customer-pool/{custId}/claim` | 认领 | |
| 27 | GET | `/api/my-claims` | 认领 | |
| 28 | POST | `/api/claims/{claimId}/cancel` | 认领 | Y |
| 29 | GET | `/api/touch-tasks` | 触达 | |
| 30 | GET | `/api/touch-tasks/{id}` | 触达 | |
| 31 | POST | `/api/touch-tasks/{id}/complete` | 触达 | |
| 32 | POST | `/api/touch-tasks/{id}/cancel` | 触达 | |
| 33 | POST | `/api/touch-tasks/{id}/logs` | 触达 | |
| 34 | GET | `/api/touch-reports` | 报告 | |
| 35 | GET | `/api/touch-reports/statistic` | 报告 | |
| 36 | GET | `/api/touch-reports/export` | 报告 | Y |

---

## 7. 开发策略

### 7.1 批次划分

```
P1 基础层（串行）
├── Task 1: Maven 模块 + POM 依赖 + bootstrap 集成
├── Task 2: DDL 建表 + 全部 Entity + Mapper + XML + 枚举类
└── Task 3: 标签管理域全栈 (TDD, 7 个接口)

P2 核心链路（子代理并行）
├── Agent A: 线索管理域 (11 个接口: 版本化 + 审批 + 批量导入)
├── Agent B: 客户主档域 (6 个接口: 自动生成 + 历史 + 转交 + 删除申请)
└── Agent C: 客户池 + 认领域 (4 个接口: 争抢认领 + 取消 + 已认领列表)

P3 销售流程（子代理并行）
├── Agent D: 触达任务域 (5 个接口: SLA + 完成/取消 + 日志)
├── Agent E: 触达管理视图 (3 个接口: 列表 + 统计 + 导出)
└── Agent F: 对外 API 实现 (7 个 *ApiImpl 类)
```

### 7.2 TDD 策略

每个域严格遵循 Red-Green-Refactor 闭环：

1. **Red**: 先写 Service 层单元测试（Mockito mock Mapper + 跨模块 API）
2. **Green**: 写最简代码让测试通过
3. **Refactor**: 优化实现，提取公共逻辑
4. **Controller 层**: 写 MockMvc 测试 → 实现 Controller
5. **Mapper 层**: 写 MyBatis 测试（H2） → 实现 SQL

### 7.3 子代理隔离

- P2 的 Agent A/B/C 各自在 git worktree 中工作
- P3 的 Agent D/E/F 同理
- 每批次完成后合并回主分支，运行全量测试

### 7.4 测试策略

| 层级 | 框架 | 范围 |
|------|------|------|
| Service 单元测试 | JUnit 5 + Mockito | mock Mapper + 跨模块 API |
| Mapper 测试 | MyBatis-Spring-Boot-Test + H2 | SQL 正确性 |
| Controller 测试 | MockMvc | mock Service |
| 集成测试 | SpringBootTest | 全链路 |

---

## 8. 并发与事务策略

| 场景 | 并发控制 | 事务边界 |
|------|----------|----------|
| 线索提交审批 | `SELECT ... FOR UPDATE` + 状态机 | 单事务 |
| 批量导入 | 预览只读，执行整批回滚 | 单事务 |
| 认领争抢 | 唯一键 `(cust_id, org_id)` 互斥 | 单事务 |
| 触达幂等 | `(touch_task_id, client_uuid)` 唯一键 | 单事务 |
| 标签导入 | 乐观锁 | 单事务 |

**跨模块调用原则**: WorkflowApi、FileApi 等跨模块调用放在事务最后一步，失败则回滚业务数据。

---

## 9. 接口注册

所有 36 个接口必须注册到 `PT_RESOURCE` 表。`RESOURCE_ID` 长度 ≤ 20，命名前缀 `C_*`（customer-marketing-center）。

---

## 10. 关键约束与决策

| 决策项 | 方案 | 原因 |
|--------|------|------|
| 包名 | `com.bank.branch.platform.customer` | 与 02-后端架构.md 和 common-dev-guide 对齐 |
| 错误码前缀 | `CUST-` | 遵循 common-dev-guide.md 模块前缀注册表 |
| 版本管理 | `is_latest + prev_lead_id` 链表 | 支持版本追溯，查询简单 |
| 防重复认领 | 数据库唯一键 | 无分布式锁开销，简单可靠 |
| 触达 SLA | 定时任务 `0 */30 * * * ?` + 工作日历 | 不强依赖 Flowable |
| 标签导入 | 覆盖式（delete + insert） | 单标签粒度，不影响其他标签 |
| 照片存储 | MinIO + JSON 路径（通过 FileApi） | 支持扩展 |
| 缓存 | Redis TTL + evict | 变更时主动失效 |
| 依赖强度 | system-governance-center 为弱依赖 | 与 02-后端架构.md 对齐 |
| Facade 命名 | `*ApiImpl` | 与 02-后端架构.md 对齐 |
