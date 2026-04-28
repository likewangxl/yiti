# customer-marketing-center/ CLAUDE.md

本文件为 `customer-marketing-center` 模块提供上下文说明。

## 模块概述

**customer-marketing-center** 是客户营销中心，覆盖客户全生命周期管理：标签体系、线索管理（含审批流）、客户主档、客户池与认领、触达任务、触达报表。是平台核心业务模块之一。

**基础包名**: `com.bank.branch.platform.customer`

**Maven 坐标**: `com.bank.branch.platform:customer-marketing-center`

**对外契约**: 5 个 `*Api` 接口 + 36 个 REST 端点。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`, `workflow-center`, `system-governance-center`
- **被依赖**: 需要客户/标签/线索/认领/触达查询能力的模块 (通过 `*Api`)

## 包结构

```
src/main/java/com/bank/branch/platform/customer/
├── api/              # 对外 API 接口 (5 个)
│   ├── TagApi.java
│   ├── LeadApi.java
│   ├── CustomerQueryApi.java
│   ├── ClaimApi.java
│   └── TouchTaskQueryApi.java
├── config/           # 模块配置
│   └── CustomerCacheConfig.java    # 缓存 Key/TTL 常量 + 防雪崩抖动
├── controller/       # REST 控制器 (9 个)
│   ├── TagController.java          # 标签 CRUD (5 端点)
│   ├── TagCustomerController.java  # 标签客户导入/查询 (2 端点)
│   ├── LeadController.java         # 线索 CRUD + 审批 (8 端点)
│   ├── LeadImportController.java   # 线索批量导入 (3 端点)
│   ├── CustomerController.java     # 客户主档 (4 端点)
│   ├── CustomerPoolController.java # 客户池 (1 端点)
│   ├── ClaimController.java        # 认领/取消/我的认领 (3 端点)
│   ├── TouchTaskController.java    # 触达任务 (6 端点)
│   └── TouchReportController.java  # 触达报表 (3 端点)
├── dto/
│   ├── req/          # 请求 DTO (15 个)
│   └── resp/         # 响应 DTO (4 个)
├── entity/           # 数据库实体 (8 个)
│   ├── CustTag.java
│   ├── CustTagRel.java
│   ├── CustLead.java
│   ├── LeadImportBatch.java
│   ├── CustMaster.java
│   ├── CustClaim.java
│   ├── TouchTask.java
│   └── TouchLog.java
├── enums/            # 枚举 (10 个)
│   ├── CustomerErrorCode.java      # CUST-400xx / CUST-404xx / CUST-409xx / CUST-500xx
│   ├── TagStatus.java              # ENABLED / DISABLED
│   ├── LeadStatus.java             # DRAFT / SUBMITTED / IN_APPROVAL / APPROVED / REJECTED
│   ├── LeadOp.java                 # CREATE / UPDATE / DELETE
│   ├── BatchStatus.java            # PENDING / PROCESSING / SUCCESS / FAIL
│   ├── ClaimStatus.java            # ACTIVE / CANCELLED
│   ├── CustMasterStatus.java       # ACTIVE / INACTIVE
│   ├── TouchTaskStatus.java        # PENDING / IN_PROGRESS / SUCCESS / CANCELLED
│   ├── TouchTaskType.java          # FIRST_TOUCH / FOLLOW_UP
│   └── SlaStatus.java              # GREEN / YELLOW / RED
├── event/            # Spring 内部事件 (7 个)
│   ├── LeadApprovedEvent.java
│   ├── LeadDeletedEvent.java
│   ├── ClaimCreatedEvent.java
│   ├── ClaimCancelledEvent.java
│   ├── ClaimTransferredEvent.java
│   ├── CustomerDeletedEvent.java
│   └── TouchCompletedEvent.java
├── facade/           # API 实现 (5 个 @Service)
│   ├── TagApiImpl.java
│   ├── LeadApiImpl.java
│   ├── CustomerQueryApiImpl.java
│   ├── ClaimApiImpl.java
│   └── TouchTaskQueryApiImpl.java
├── listener/         # 事件监听器 (5 个)
│   ├── WorkflowCallbackListener.java    # ProcessCompletedEvent → 线索审批结果
│   ├── LeadApprovedListener.java        # LeadApprovedEvent → 客户主档组装
│   ├── LeadDeletedListener.java         # LeadDeletedEvent → 客户主档失效
│   ├── ClaimCreatedListener.java        # ClaimCreatedEvent → 创建首次触达任务
│   └── TouchTaskCompletedListener.java  # 触达完成后处理
├── mapper/           # MyBatis Mapper (9 个接口 + XML)
│   ├── CustTagMapper, CustTagRelMapper
│   ├── CustLeadMapper, LeadImportBatchMapper
│   ├── CustMasterMapper, CustClaimMapper
│   ├── TouchTaskMapper, TouchLogMapper
│   └── TouchReportMapper              # 报表专用 JOIN 查询
└── service/          # 业务逻辑 (12 个 Service)
    ├── TagService.java                 # 标签 CRUD + 状态切换
    ├── TagCustomerService.java         # 标签客户覆盖式导入
    ├── LeadService.java                # 线索 CRUD + 提交审批 (SELECT FOR UPDATE)
    ├── LeadVersionService.java         # 线索编辑/删除版本创建
    ├── LeadImportService.java          # 批量导入预览与执行
    ├── CustMasterAssemblerService.java # 线索→客户主档 (CREATE/UPDATE/DELETE)
    ├── CustomerService.java            # 客户查询 + 转交 + 删除申请
    ├── CustomerPoolService.java        # 客户池 (未认领客户 LEFT JOIN)
    ├── ClaimService.java               # 认领 (DuplicateKeyException 防并发)
    ├── TouchTaskService.java           # 触达任务全生命周期 + SLA 刷新
    ├── TouchLogService.java            # 触达日志 (UK 幂等)
    └── TouchReportService.java         # 触达报表统计
```

## 对外 API (5 个接口)

| 接口 | 实现 | 主要方法 |
|------|------|---------|
| `TagApi` | TagApiImpl | listEnabledTags, getTagByCode, getCustomerTags, batchGetCustomerTags, getCustomerIdsByTag, isTagNameExists |
| `LeadApi` | LeadApiImpl | getLead, getLeadByBusinessKey, getLeadsByBatch, getLeadVersionChain, isLeadCustNameAvailable |
| `CustomerQueryApi` | CustomerQueryApiImpl | getCustomer, listCustomers, searchCustomers, isValidCustomer, isClaimedByOrg, getCustomerClaims, hasRunningProcess, listRunningProcesses, countCustomers |
| `ClaimApi` | ClaimApiImpl | getClaim, getEmpClaims, getOrgClaims, isClaimActive, countEmpClaims, countOrgClaims |
| `TouchTaskQueryApi` | TouchTaskQueryApiImpl | getTouchTask, getTouchTaskByBusinessKey, getEmpTouchTasks, countRunningTouchTasks, getCustomerTouchHistory, getCustomerTouchHistoryByOrg, hasCompletedFirstTouch, getOrgTouchSummary |

## REST 端点

### TagController (`/api/tags`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/tags` | 分页标签列表 |
| GET | `/api/tags/enabled` | 启用状态标签列表 |
| POST | `/api/tags` | 创建标签 |
| PUT | `/api/tags/{id}` | 更新标签 |
| PUT | `/api/tags/{id}/status` | 切换标签状态 |

### TagCustomerController (`/api/tags`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/tags/{tagId}/customers/import` | 覆盖式导入标签客户 |
| GET | `/api/tags/{tagId}/customers` | 查询标签下客户列表 |

### LeadController (`/api/leads`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/leads` | 分页线索列表 |
| GET | `/api/leads/{id}` | 线索详情 |
| POST | `/api/leads` | 创建线索草稿 |
| PUT | `/api/leads/{id}` | 更新线索草稿 |
| DELETE | `/api/leads/{id}` | 删除线索草稿 |
| POST | `/api/leads/{id}/submit` | 提交审批 (SELECT FOR UPDATE + WorkflowApi) |
| POST | `/api/leads/edit-version` | 创建编辑版本 (已生效线索修改) |
| POST | `/api/leads/delete-version` | 创建删除版本 (已生效线索删除) |

### LeadImportController (`/api/leads`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/leads/import/preview` | 导入预览 (校验) |
| POST | `/api/leads/import/execute` | 执行导入 |
| GET | `/api/leads/batches` | 导入批次列表 |

### CustomerController (`/api/customers`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/customers` | 分页客户列表 |
| GET | `/api/customers/{id}` | 客户详情 |
| POST | `/api/customers/{custId}/claims/{claimId}/transfer` | 转交维护人 (@AuditLog) |
| POST | `/api/customers/{custId}/delete-apply` | 删除申请 (WorkflowApi) |

### CustomerPoolController (`/api/customer-pool`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/customer-pool` | 未认领客户池 (LEFT JOIN) |

### ClaimController (`/api/claims`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/claims` | 认领客户 (UK 防并发) |
| POST | `/api/claims/{id}/cancel` | 取消认领 |
| GET | `/api/claims/mine` | 我的认领列表 |

### TouchTaskController (`/api/touch-tasks`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/touch-tasks` | 分页触达任务列表 |
| GET | `/api/touch-tasks/{id}` | 任务详情 |
| POST | `/api/touch-tasks/{id}/success` | 标记任务成功（原 /complete）|
| POST | `/api/touch-tasks/{id}/cancel` | 取消任务 |
| POST | `/api/touch-tasks/{id}/logs` | 添加触达日志 (UK 幂等) |
| GET | `/api/touch-tasks/{id}/logs` | 查询触达日志 |

### CustomerHistoryController (`/api/customers`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/customers/{id}/history` | 跨机构全量历史查询（高危 / 独立审计）|

### CustomerTagController (`/api/customers`)

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/customers/{id}/tags` | 客户追加打标 |
| DELETE | `/api/customers/{id}/tags/{tagId}` | 客户取消单个标签 |

### CustomerExportController (`/api/customers`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/customers/export` | 客户列表导出（高危）|

### LeadController 新增端点

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/leads/{id}/versions` | 查询线索版本链 |

### TagCustomerController 新增端点

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/tags/{tagId}/customers/export` | 标签客户导出（高危）|

### TouchReportController (`/api/touch-reports`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/touch-reports` | 分页触达报表 |
| GET | `/api/touch-reports/statistics` | 触达统计汇总 |
| GET | `/api/touch-reports/export` | 触达报表导出（高危，已实现 CSV）|

### AdminTouchTaskController (`/api/admin/touch-tasks`)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/admin/touch-tasks` | 管理后台全局触达任务列表 |
| GET | `/api/admin/touch-tasks/export` | 管理后台导出（高危）|
| POST | `/api/admin/touch-tasks/batch-assign` | 批量分配触达任务（高危）|

## 数据库表 (8 张)

| 表 | 实体 | 说明 |
|----|------|------|
| `cust_tag` | CustTag | 客户标签 (tagCode, tagName, tagCategory, description, status) |
| `cust_tag_rel` | CustTagRel | 标签-客户关系 (tagId, custId) |
| `cust_lead` | CustLead | 客户线索 (leadNo, leadOp, leadStatus, sourceCustId, 含完整客户快照字段) |
| `lead_import_batch` | LeadImportBatch | 线索导入批次 (sourceFileName, totalCount/successCount/failCount, businessKey) |
| `cust_master` | CustMaster | 客户主档 (custNo, custName, idType/idNo, mobile, ownerOrgId, status) |
| `cust_claim` | CustClaim | 客户认领 (custId + orgId UK, maintainerEmpId, claimStatus) |
| `touch_task` | TouchTask | 触达任务 (taskNo, custId, assigneeEmpId, taskType, taskStatus, slaStatus, slaWarning, planFinishTime 等) |
| `touch_log` | TouchLog | 触达日志 (touchTaskId + clientUuid UK, logContent, logTime) |

## 事件驱动架构

模块内部通过 Spring 事件实现松耦合：

| 事件 | 发布者 | 监听者 | 触发条件 |
|------|--------|--------|---------|
| `ProcessCompletedEvent` | workflow-center | WorkflowCallbackListener | 线索审批流程结束 |
| `LeadApprovedEvent` | WorkflowCallbackListener | LeadApprovedListener | 线索审批通过 |
| `LeadDeletedEvent` | WorkflowCallbackListener | LeadDeletedListener | 删除版本审批通过 |
| `ClaimCreatedEvent` | ClaimService | ClaimCreatedListener | 客户被认领 |
| `ClaimCancelledEvent` | ClaimService | (预留) | 认领被取消 |
| `ClaimTransferredEvent` | CustomerService | (预留) | 维护人转交 |
| `CustomerDeletedEvent` | CustomerService | (预留) | 客户删除审批通过 |
| `TouchCompletedEvent` | TouchTaskService | TouchTaskCompletedListener | 触达任务完成 |

所有事件监听器使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`。

## 关键设计决策

### 线索审批流程
线索提交审批通过 `WorkflowApi.startProcess()` 启动 Flowable 流程。`LeadService.submitForApproval()` 使用 `SELECT FOR UPDATE` 防止并发提交。审批结果通过 `ProcessCompletedEvent` 回调，由 `WorkflowCallbackListener` 分发。

### 客户认领并发控制
`cust_claim` 表有 `(cust_id, org_id)` 唯一约束。`ClaimService.claim()` 直接 INSERT，捕获 `DuplicateKeyException` 返回 CUST-40904 错误，避免分布式锁。

### 触达日志幂等
`touch_log` 表有 `(touch_task_id, client_uuid)` 唯一约束。客户端传入 `clientUuid`，重复提交时捕获 `DuplicateKeyException` 返回 CUST-40905。

### 标签客户覆盖式导入
`TagCustomerService.importCustomers()` 先 `deleteByTagId()` 再 `insertBatch()`，保证每次导入后标签下客户列表完全等于本次导入内容。

### 触达任务状态机 (V1.0)
依据《功能规格》§7.3bis：
- PENDING → IN_PROGRESS / SUCCESS / CANCELLED
- IN_PROGRESS → SUCCESS / CANCELLED
- SUCCESS / CANCELLED 为终态

首次触达日志自动驱动 PENDING → IN_PROGRESS（由 `TouchLogService` 触发）。
状态转移由 `TouchTaskStateMachineService.assertTransition` 校验，非法抛 CUST-40010。

## 错误码

| 错误码 | 含义 |
|--------|------|
| `CUST-400xx` | 参数/业务规则错误 (01-09) |
| `CUST-40010` | 触达任务非法状态转移 |
| `CUST-404xx` | 资源不存在 (01: 标签, 02: 线索, 03: 客户, 04: 认领, 05: 任务, 06: 批次) |
| `CUST-409xx` | 冲突 (01-02: 标签重复, 03: 线索编号重复, 04: 重复认领, 05: 日志重复, 06: 编码不可改) |
| `CUST-500xx` | 内部错误 (01: 通用, 02: 工作流调用) |

## 跨模块依赖

| 依赖 | 来源模块 | 用途 |
|------|----------|------|
| `CurrentUserApi` | auth-permission-center | 获取当前用户工号/机构 |
| `BizScopeApi` | auth-permission-center | 数据范围校验 |
| `OrgApi` | auth-permission-center | 组织架构查询 |
| `WorkflowApi` | workflow-center | 线索/删除审批流程启动 |
| `DictApi` | system-governance-center | 字典值查询 |
| `FileApi` | system-governance-center | 导入文件读取 |
| `NotifyApi` | system-governance-center | 通知发送 |
| `AuditApi` | system-governance-center | 审计日志 |

## 缓存配置

Cache-Aside 模式，所有 Key 前缀 `customer:`，默认 TTL 5 分钟 + 10% 随机抖动防雪崩。

| Key | 内容 | TTL |
|-----|------|-----|
| `customer:tag:enabled` | 启用状态标签列表 | 5 分钟 |
| `customer:master:{custId}` | 客户主档详情 | 5 分钟 |

## 测试

- **单元测试**: Mockito (Service 层), 每个 Service 对应 `*Test.java`
- **集成测试**: MockMvc + H2 (Controller 层), 基类 `AbstractControllerIntegrationTest`
- **测试配置**: `CustomerTestConfiguration.java` + `application-test.yml` (H2 MySQL 兼容模式)
- **Mock 用户上下文**: `@WithMockEmpContext` 注解 + `MockEmpContextExtension`
- **当前测试数**: 321 个测试用例, 0 失败

## PT_RESOURCE 注册 SQL

模块所有接口对应的 `PT_RESOURCE` 注册记录存放于：
- `docs/superpowers/sql/2026-04-14-customer-tag-pt-resource.sql` (7 条)
- `docs/superpowers/sql/2026-04-14-customer-lead-pt-resource.sql` (11 条)
- `docs/superpowers/sql/2026-04-14-customer-master-pt-resource.sql` (4 条)
- `docs/superpowers/sql/2026-04-14-customer-pool-claim-pt-resource.sql` (4 条)
- `docs/superpowers/sql/2026-04-14-customer-touch-pt-resource.sql` (6 条)
- `docs/superpowers/sql/2026-04-14-customer-report-pt-resource.sql` (3 条)
- `docs/superpowers/sql/2026-04-21-customer-contract-alignment-pt-resource.sql` (10 条，契约对齐新增端点)

## V1.0 已知技术债（2026-04-25）

| # | 标题 | 优先级 | 来源 |
|---|---|---|---|
| 1 | 错误码 26 vs 设计 35，缺 403 系列 7 条 + 422 系列 8 条 + 部分 500 | 中 | 健康检查 |
| 2 | 零 ArchUnit 守护（无 arch/ 子目录） | 中 | 健康检查 |
| 3 | 线索导入行级校验简化未实现（4 处 TODO） | 低 | 代码 TODO |
| 4 | CROSS_ORG 审计待 @AuditLog 升级（3 处 TODO） | 低 | 代码 TODO |
| 5 | WorkflowCallbackListener 未区分 APPROVED/REJECTED | 低 | 代码 TODO |
| 6 | LeadDeletedListener 线索独立标签清理预留 | 低 | 代码 TODO |
| 7 | TouchTaskMapper.xml H2/MySQL 函数方言 TODO | 低 | 代码 TODO |
