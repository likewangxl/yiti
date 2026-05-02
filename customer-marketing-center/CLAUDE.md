# customer-marketing-center/ CLAUDE.md

本文件为 `customer-marketing-center` 模块提供上下文说明。

> ⚠️ **Flyway 已彻底废弃**（详见根 [CLAUDE.md](../CLAUDE.md) "Flyway 禁令"红线）。
> 本文件下方 V1.x 历史变更日志中提到的 `V1_8_0__register_lead_callback_compensate_job.sql` /
> `U1_8_0__remove_lead_callback_compensate_job.sql` / `FlywayTestBase` / `FlywayIT` 等内容仅作为
> **历史档案**保留，对应文件已从源码中删除。新增 schema 变更请直接以 SQL 在目标库执行，**禁止**重新引入 Flyway。

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
├── job/              # V1.8 Quartz Job (1 个)
│   └── quartz/
│       └── LeadCallbackCompensateQuartzJob.java
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
| GET | `/api/leads/import/batches/{batchId}` | 导入批次详情 (P1a 2026-04-28) |

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
| POST | `/api/claims/{claimId}/re-touch` | 重新发起触达 (P1a 2026-04-28，FOLLOW_UP) |

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
| GET | `/api/admin/touch-tasks/summary` | 管理后台机构触达汇总 (P1a 2026-04-28) |
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
| `CUST-400xx` | 参数/业务规则错误 (01-02 标签校验, 03-04 线索状态, 05 文件空, 07 触达任务非待办, 08-09 转交/取消原因)；CUST-40006 已废弃 (P1B 迁至 42205) |
| `CUST-40010` | 触达任务非法状态转移 |
| `CUST-403xx` | 403 权限/越权 (01 无权编辑非草稿线索 P1C 升级自 40003，05 跨机构认领越权 P1a→P1C 扩展到 cancelClaim，06 转交角色不符 P1C，07 转交机构不符 P1C；02/03/04 占位待 V1.x) |
| `CUST-404xx` | 资源不存在 (01: 标签, 02: 线索, 03: 客户, 04: 认领, 05: 任务, 06: 批次) |
| `CUST-409xx` | 冲突 (01-02: 标签重复, 03: 线索编号重复, 04: 重复认领, 05: 日志重复, 06: 编码不可改, 08-09: re-touch 状态冲突, 12: 客户在途流程不可删) |
| `CUST-422xx` | 422 业务校验 (01: 线索行级校验占位, 02: 标签客户ID 无效, 03: 文件格式, 04: 文件过大 10MB, 05: 行数 > 5000, 06: 日志必填, 07: 照片 > 9, 08: 照片格式 jpg/png/heic) — P1B 2026-04-29 落地 |
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

## P1 三批改动进度（2026-04-29 已交付）

完整批次记录见 **[`docs/superpowers/sessions/2026-04-29-customer-p1-three-batches-progress.md`](../docs/superpowers/sessions/2026-04-29-customer-p1-three-batches-progress.md)**：

| 批次 | commit | 范围 | 测试 |
|---|---|---|---|
| P1a | `9c98e46` | 3 REST 端点（re-touch / admin summary / batch detail）+ CUST-40305/40908/40909 | +14 |
| P1B | `a0b3ea8` | 422 业务校验 8 条（CUST-42201~42208）+ CUST-40006 迁移 | +11 |
| P1C | `d702b7f` | 403 权限校验 7 条（CUST-40301~40307）+ 跨模块 UserApi.getUserRoleCodes | +6 |

**⚠ BREAKING CHANGE (P1C)**：CUST-40003 (LEAD_NOT_DRAFT) 重命名为 CUST-40301 (LEAD_EDIT_FORBIDDEN)，
message 由 "线索非草稿状态，不允许编辑" 改为 "无权编辑非草稿状态线索"。前端 i18n 需同步更新。

累计 surefire 测试 321 → 352；累计 P1A/P1B/P1C 三批 follow-up 共 15 条技术债（详见进度文档 §5）。

## V1.8 改动进度（2026-05-01 已交付）

LeadCallbackCompensationService 由 Spring `@Scheduled` 迁移到 Quartz：

- 新增 `customer/job/quartz/LeadCallbackCompensateQuartzJob`（实现 `org.quartz.Job`）
- 删除 `service/LeadCallbackCompensationService.scheduledScan()` + `@Scheduled` 入口
- 删除 `config/CustomerSchedulingConfig`（@EnableScheduling 归还 performance 模块）
- 新增 ArchUnit `arch/NoCustomerScheduledArchTest`（防回退）
- DDL: `sql/V1_8_0__register_lead_callback_compensate_job.sql` + `U1_8_0`
- IT: `bootstrap/.../LeadCallbackJobRegisteredIT`（启动注册验证）+ `LeadCallbackCompensationIT` 顺手修表名大写化
- bootstrap data.sql：sys_job_conf 加 LEAD_CALLBACK_COMPENSATE 行 + 12 处小写表名 → 大写治理

cron='0 */5 * * * ?', misfire=DO_NOTHING；多实例由 QRTZ_LOCKS 行锁防重。

测试增量：surefire +3（QuartzJobTest x2 + ArchUnit x1） / failsafe +3（LeadCallbackJobRegisteredIT 3 case）；customer 模块 355 全绿。

> **运维 Runbook**: V1.9 已整合，详见 [`docs/modules/system-governance-center/09-运维Runbook.md`](../docs/modules/system-governance-center/09-运维Runbook.md)。

## V1.9 处置（2026-05-01）

V1.8 后登记的 5 项 V1.9 候选事项经 brainstorming 二次收敛，处置如下：

| # | 事项 | V1.9 处置 | 备注 |
|---|---|---|---|
| 1 | HealthCheck 也 Quartz 化 | **永久关闭** | brainstorming 评审：V1.7 spec § 7 设计意图（补偿器不依赖 Quartz）反转代价大于收益 |
| 2 | sys_job_conf 运维 Runbook | **本期交付（V1.9 / 2026-05-01）** | `docs/modules/system-governance-center/09-运维Runbook.md`（600 行整合 V1.6-V1.8） |
| 3 | Quartz JobStore 反向清理 | **永久关闭** | brainstorming 评审：实际产生路径 < 1 次/年，成本/收益严重失衡 |
| 4 | 仓库大小写一致性治理 | **本期交付（V1.9 / 2026-05-01）** | bootstrap test data SQL 4 文件 29 处大写化 |
| 5 | 测试库环境完整治理 | **延期 V1.10** | onepl_test_v103 多模块 DDL 协同，影响面大 |

**brainstorming 决策溯源**：见 `docs/superpowers/specs/2026-05-01-v1.9-runbook-and-case-consistency-design.md` § 10

## V1.10 处置（2026-05-01）

V1.10 候选事项 6 项二次收敛后处置：

| # | 事项 | V1.10 处置 | 备注 |
|---|---|---|---|
| 1 | LeadApprovedCreatesCustomerMasterIT 失败 | **延期 V1.11** | brainstorming 二次收敛后类 A（5 IT 失败）转 V1.11 单独诊断 |
| 2 | WorkflowCallbackEventChainBugIT 失败 | **延期 V1.11** | 同上 |
| 3 | WorkflowCallbackIdempotencyBugIT 失败 | **延期 V1.11** | 同上 |
| 4 | CustomerMarketingCenterIT 错误 | **延期 V1.11** | 同上 |
| 5 | WorkflowCallbackExceptionSwallowBugIT 错误 | **延期 V1.11** | 同上 |
| 6 | 测试库环境完整治理（合一到 onepl_test_bootstrap）| **本期交付（V1.10 / 2026-05-01）** | path Y：FlywayTestBase 指 onepl_test_bootstrap，baseline-version=0，Flyway 真接管 perf/rpt schema |

**brainstorming 决策溯源**：见 `docs/superpowers/specs/2026-05-01-v1.10-test-db-unification-design.md` § 0 / § 10

## V1.11 处置（2026-05-01）

V1.11 候选事项 5 项二次收敛后处置：

| # | 事项 | V1.11 处置 | 备注 |
|---|---|---|---|
| 1 | 5 IT 失败（LeadApprovedCreatesCustomerMasterIT / WorkflowCallback*BugIT / CustomerMarketingCenterIT）| ✅ **本期交付（V1.11 / 2026-05-01）** | 方向 C 改造：删除 3 个 listener + 3 个 event，reconcileApproved/ClaimService.claim 改同步调用，与 bizapp.LoanWorkflowListener 单层 pattern 对齐 |
| 2 | perf failsafe Spring Context threshold cascade | **延期 V1.12** | 与 V1.11 # 1 独立，需单独诊断 |
| 3 | MetricScheduledE2EIT Duplicate entry | **延期 V1.12** | 测试间数据残留，需 cleanup 改造 |
| 4 | V1.8 P6 已登记业务/数据状态问题 | **延期 V1.12** | KpiSchemeControllerIT / AllocRelationControllerIT / PerfRunTaskMapperIT / CustAllocRelationMapperIT |
| 5 | 3 个 *SummaryControllerIT.submit*Export Status 500 | **延期 V1.12** | 数据库连接池或前置数据缺失 |

**brainstorming 决策溯源**：见 `docs/superpowers/specs/2026-05-01-v1.11-5it-diagnosis-design.md` § 0 / § 9

## V1.12 处置（2026-05-01）

V1.12 范围 = schema 治理三件套（# 5 + # 6 + # 7）+ MetricScheduledE2EIT 数据 cleanup（# 2）：

| # | 事项 | V1.12 处置 | 备注 |
|---|---|---|---|
| 1 | perf failsafe Spring Context threshold cascade | **延期 V1.13** | 不在本期 schema 治理 scope |
| 2 | MetricScheduledE2EIT 数据残留 cleanup | ✅ **本期交付（V1.12 / 2026-05-01）** | IT 加 @Sql executionPhase=BEFORE_TEST_METHOD DELETE PERF_METRIC_DEF；PERF_METRIC_DEF 缺 V1.7 三列（cron_expr/subject_sql/last_run_time）顺手修复（合并到 # 5 cleanup SQL）。**业务层 Quartz JobKey 注入失败转 V1.13** |
| 3 | V1.8 P6 业务/数据状态问题（25 fail/error）| **延期 V1.13** | 历史欠债，需独立子项 |
| 4 | 3 个 *SummaryControllerIT.submit*Export Status 500 | **延期 V1.13** | 低优先级 |
| 5 | onepl_test_bootstrap schema column drift | ✅ **本期交付（V1.12 / 2026-05-01）** | 一次性 cleanup SQL `docs/superpowers/sql/2026-05-01-v1.12-schema-column-drift-fix.sql`（修 TOUCH_TASK.sla_warning + PERF_METRIC_DEF V1.7 三列）+ schema.sql 加 V1.12 # 5 注释 |
| 6 | 8 customer 小写历史表 | ✅ **本期交付（V1.12 / 2026-05-01）** | mysqldump backup（cust_master 1 行 cust-seed-001 = 重复 seed）+ DROP 8 表（保留 8 张大写业务表）|
| 7 | BusinessApplicationCenterIT 数据残留 | ✅ **本期交付（V1.12 / 2026-05-01）** | IT @Sql 加引用 business-application-data.sql（已含完整 DELETE cleanup 段） |

**brainstorming 决策溯源**：见 `docs/superpowers/specs/2026-05-01-v1.12-schema-and-data-cleanup-design.md` § 0 / § 5

## V1.13 候选事项（2026-05-01 新登记）

V1.12 交付后未解决项 + 实施过程发现的副产品延期至 V1.13：

| # | 事项 | 优先级 | 来源 | 处置 |
|---|---|---|---|---|
| 1 | perf failsafe Spring Context threshold cascade（V1_2_0/V1_4_0/V1_3_0/V1_2_5/V1_0_4/V1_0_3 FlywayIT 等大量 ApplicationContext 加载失败级联） | 中 | V1.10 P2 retry-3 探查（延期 V1.11→V1.12→V1.13）| V1.13 候选 |
| 2 | V1.8 P6 已登记业务/数据状态问题：KpiSchemeControllerIT 4E + AllocRelationControllerIT 4F + PerfRunTaskMapperIT 7F+1E + CustAllocRelationMapperIT 10F | 中 | V1.8 P6 转入（延期 V1.11→V1.12→V1.13）| V1.13 候选 |
| 3 | 3 个 *SummaryControllerIT.submit*Export Status 500（疑数据库连接池或前置数据缺失）| 低 | V1.10 P2 retry-3 探查（延期 V1.11→V1.12→V1.13）| V1.13 候选 |
| 4 | MetricScheduledE2EIT 业务层 Quartz JobKey 注入失败（V1.7 e2e 测试在 V1.10 测试库合一后暴露；schema column drift 修复后仍 fail）| 中 | V1.12 # 2 实施暴露 | V1.13 候选 |
| 5 | WorkflowCallbackListener REQUIRES_NEW + reconcileApproved REQUIRES_NEW 嵌套冗余简化（外层防御层是否必要） | 低 | V1.11 # 1 architect Opus 建议 | V1.13 候选 |
| 6 | LeadRejectedEvent dead code 定调（删除 / 加 V2 listener / 加 TODO） | 低 | V1.11 # 1 architect Opus 建议 | V1.13 候选 |

## V1.0 已知技术债（2026-04-25）

| # | 标题 | 优先级 | 来源 |
|---|---|---|---|
| 1 | 错误码：~~403 系列 7 条~~ **P1C 2026-04-29 已补 4 条触发逻辑（40301/40305/40306/40307）+ 3 条占位（40302/40303/40304）**；~~422 系列 8 条~~ **P1B 已补 7 条（CUST-42202~42208）+ 1 条占位（42201）**；部分 500 仍缺 | 中 | 健康检查 |
| 2 | 零 ArchUnit 守护（无 arch/ 子目录） | 中 | 健康检查 |
| 3 | 线索导入行级校验简化未实现（4 处 TODO） | 低 | 代码 TODO |
| 4 | CROSS_ORG 审计待 @AuditLog 升级（3 处 TODO） | 低 | 代码 TODO |
| 5 | WorkflowCallbackListener 未区分 APPROVED/REJECTED | 低 | 代码 TODO |
| 6 | LeadDeletedListener 线索独立标签清理预留 | 低 | 代码 TODO |
| 7 | TouchTaskMapper.xml H2/MySQL 函数方言 TODO | 低 | 代码 TODO |
