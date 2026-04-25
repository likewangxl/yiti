# business-application-center/ CLAUDE.md

本文件为 `business-application-center` 模块提供上下文说明。

## 模块概述

**business-application-center** 是业务申请中心，承载两大核心业务域：**资产投放申请 (Loan)** 和 **中场支持申请 (Support)**。是"营销→触达→落地"闭环的落地环节，也是绩效和报表模块最重要的事实源。

**基础包名**: `com.bank.branch.platform.bizapp`

**Maven 坐标**: `com.bank.branch.platform:business-application-center`

**对外契约**: 5 个 `*Api` 接口 + 21 个 REST 端点。

## 依赖关系

- **依赖**: `common-*` (5 个子模块), `auth-permission-center`, `workflow-center`, `system-governance-center`, `customer-marketing-center`, `portal-content-center`
- **被依赖**: `performance-engine-center`, `report-analytics-center` (通过 `*Api` 只读查询)

## 包结构

```
src/main/java/com/bank/branch/platform/bizapp/
├── api/              # 对外 API 接口 (5 个)
│   ├── LoanApi.java, LoanQueryApi.java
│   ├── SupportApi.java, SupportQueryApi.java
│   └── BizApplyQueryApi.java
│   ├── converter/    # API 层 DTO 转换器 (2 个，Phase 1 新增)
│   │   ├── LoanApplyDTOConverter.java
│   │   └── SupportRequestDTOConverter.java
│   └── dto/          # API 层 DTO (8 个，Phase 1 新增 LoanApplyListItemDTO / SupportRequestListItemDTO)
├── config/           # 配置
│   └── BizAppCacheConfig.java
├── controller/       # REST 控制器 (3 个)
│   ├── LoanController.java           # 9 端点
│   ├── SupportController.java        # 8 端点 (发起侧 SUPPORT)
│   └── SupportDeptController.java    # 4 端点 (承接侧 SUPPORT_DEPT)
├── dto/
│   ├── req/          # 请求 DTO (6 个)
│   └── resp/         # 响应 DTO (3 个，Phase 2 新增 SubmitRespDTO / SupportRequestCreateRespDTO)
│       ├── LoanDetailResp.java               # 富化：含 custInfo + canOperate
│       ├── SubmitRespDTO.java                # Loan/Support 提交统一响应
│       └── SupportRequestCreateRespDTO.java  # 含 submitGroupId
├── entity/           # 实体 (2 个)
│   ├── LoanApply.java
│   └── SupportRequest.java
├── enums/            # 枚举 (5 个)
│   ├── BizAppErrorCode.java      # BIZ-403xx / BIZ-404xx / BIZ-409xx / BIZ-422xx / BIZ-500xx
│   ├── LoanStatus.java           # DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED
│   ├── SupportStatus.java        # +IN_PROGRESS
│   ├── SupportScenario.java      # A(产品直达) / B(部门承接)
│   └── SupportSourceType.java    # EXISTING_CUSTOMER / TOUCH_TASK（Phase 3：MANUAL → EXISTING_CUSTOMER）
├── event/            # 领域事件 (7 个，Phase 3 新增 SupportRejectedEvent)
│   ├── LoanSubmittedEvent, LoanApprovedEvent, LoanRejectedEvent
│   └── SupportSubmittedEvent, SupportDispatchedEvent, SupportCompletedEvent, SupportRejectedEvent
├── facade/           # API 实现 (5 个 @Component)
├── listener/         # 工作流事件监听 (2 个，Phase 3 改造)
│   ├── LoanWorkflowListener.java      # ProcessCompletedEvent → 贷款状态更新
│   └── SupportWorkflowListener.java   # ProcessCompletedEvent → 支持状态更新（含 REJECTED 分支）
├── mapper/           # MyBatis Mapper (2 个接口 + XML)
│   ├── LoanApplyMapper (+ LoanApplyMapper.xml)
│   └── SupportRequestMapper (+ SupportRequestMapper.xml，Phase 3 新增 conditionalUpdateStatus)
└── service/          # 业务逻辑 (8 个)
    ├── BizStateMachine.java              # 统一状态机 (Loan + Support)
    ├── BizNoGenerator.java               # 编号生成 (LA/SR+日期+序号)
    ├── LoanService.java                  # 资产投放全流程
    ├── LoanFormValidator.java            # 节点表单校验
    ├── SupportService.java               # 中场支持发起侧
    ├── SupportScenarioRouter.java        # 场景 A/B 路由
    ├── SupportProductSplitService.java   # 多产品拆单
    ├── SupportDeptService.java           # 承接侧 (派单/转交/完成)
    └── BizApplySearchService.java        # 跨域查询聚合
```

## REST 端点

### LoanController (`/api/loans`) — 9 端点

| 方法 | 端点 | BizAuth | 返回类型 | 说明 |
|------|------|---------|----------|------|
| GET | /api/loans | LOAN/LIST | `LoanApplyListItemDTO` | 分页列表（Phase 1 富化） |
| GET | /api/loans/{id} | LOAN/READ | `LoanDetailResp`（含 custInfo/canOperate） | 详情（Phase 2 富化） |
| POST | /api/loans | LOAN/WRITE | Void | 创建草稿 |
| PUT | /api/loans/{id} | LOAN/WRITE | Void | 更新草稿 |
| POST | /api/loans/{id}/submit | LOAN/WRITE | `SubmitRespDTO` | 提交审批（Phase 2：Void → SubmitRespDTO） |
| DELETE | /api/loans/{id} | LOAN/WRITE | Void | 删除草稿 |
| POST | /api/loans/{id}/cancel | LOAN/WRITE | Void | 撤回（@AuditLog reasonRequired=true） |
| GET | /api/loans/export | LOAN/EXPORT | — | 导出（@AuditLog reasonRequired=true） |
| GET | /api/loans/{id}/node-form/{nodeKey} | LOAN/READ | — | 节点表单 |

### SupportController (`/api/support-requests`) — 8 端点

| 方法 | 端点 | BizAuth | 返回类型 | 说明 |
|------|------|---------|----------|------|
| GET | /api/support-requests | SUPPORT/LIST | `SupportRequestListItemDTO` | 发起侧列表（Phase 1 富化） |
| GET | /api/support-requests/{id} | SUPPORT/READ | DTO（非 Entity） | 详情（Phase 2 不再暴露 Entity） |
| POST | /api/support-requests | SUPPORT/WRITE | `SupportRequestCreateRespDTO`（含 submitGroupId） | 创建(含拆单)（Phase 2 富化） |
| POST | /api/support-requests/{id}/submit | SUPPORT/WRITE | `SubmitRespDTO` | 草稿提交（Phase 2：Void → SubmitRespDTO） |
| DELETE | /api/support-requests/{id} | SUPPORT/WRITE | Void | 删除草稿 |
| POST | /api/support-requests/{id}/cancel | SUPPORT/WRITE | Void | 撤回（@AuditLog reasonRequired=true） |
| GET | /api/support-requests/export | SUPPORT/EXPORT | — | 导出（@AuditLog reasonRequired=true） |
| GET | /api/support-requests/available-products | SUPPORT/READ | — | 可用产品 |

### SupportDeptController (`/api/support-dept/requests`) — 4 端点

| 方法 | 端点 | BizAuth | 返回类型 | 说明 |
|------|------|---------|----------|------|
| GET | /api/support-dept/requests | SUPPORT_DEPT/LIST | `SupportRequestListItemDTO` | 承接侧列表（Phase 1 富化） |
| POST | /api/support-dept/requests/{id}/dispatch | SUPPORT_DEPT/WRITE | Void | 秘书派单（Phase 3 新增 dispatchRemark 入参） |
| POST | /api/support-dept/requests/{id}/transfer | SUPPORT_DEPT/TRANSFER | Void | 转交(高危)（@AuditLog reasonRequired=true） |
| POST | /api/support-dept/requests/{id}/complete | SUPPORT_DEPT/WRITE | Void | 办理完成 |

## 数据库表 (2 张)

| 表 | 实体 | 说明 |
|----|------|------|
| `loan_apply` | LoanApply | 资产投放申请 (applyNo, custId, creditAmount, status, businessKey=LOAN:{id}) |
| `support_request` | SupportRequest | 中场支持申请 (requestNo, submitGroupId, productId, supportDeptId, assignedEmpId, status, businessKey=SUPPORT:{id}) |

## 关键设计

### 状态机
- **Loan**: DRAFT → IN_APPROVAL → COMPLETED/REJECTED/CANCELLED
- **Support**: DRAFT → IN_APPROVAL → IN_PROGRESS(仅B) → COMPLETED/REJECTED/CANCELLED

### 场景路由
- **场景 A** (产品直达): productIds 非空 + otherDemand 为空 → `support_simple_v1`
- **场景 B** (部门承接): productIds 为空 OR otherDemand 非空 → `support_complex_v1`

### 多产品拆单
场景 A 多产品时，每个 productId 生成独立 support_request，共享 submitGroupId。

### 双视图权限
- SUPPORT (发起侧): 按 `owner_org_id` + `created_by` 控制
- SUPPORT_DEPT (承接侧): 按 `support_dept_id` + `assigned_emp_id` 控制

### @AuditLog 补齐（Phase 2-3）
全模块 14 个高危端点均已补 `@AuditLog` 注解：
- `transfer`、`cancel`（Loan + Support）、`export`（Loan + Support）使用 `reasonRequired=true`
- `submit`、`dispatch`、`complete` 使用默认 `reasonRequired=false`

### Listener 改造（Phase 3）
`LoanWorkflowListener` / `SupportWorkflowListener` 由 `@EventListener` 改为：
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
```
- 确保只在事务提交后触发，避免脏读；`fallbackExecution=true` 保证非事务上下文也可处理。
- `SupportWorkflowListener` 新增 REJECTED 分支（发布 `SupportRejectedEvent`）。
- Mapper 新增 `conditionalUpdateStatus`（条件 UPDATE）保证幂等性。

### 枚举变更（Phase 3）
`SupportSourceType.MANUAL` 重命名为 `EXISTING_CUSTOMER`，语义更明确。

## 错误码

| 前缀 | 含义 |
|------|------|
| BIZ-403xx | 权限不足 (01:客户无效, 02:非执行人, 03:未认领, 04:非承接人, 05:非创建人) |
| BIZ-40401 | 申请不存在 |
| BIZ-409xx | 冲突 (01:产品不可用, 02:缺少部门, 03:参数为空, 04:字典值无效, 05:金额校验, 06:重复申请, 07:并行超限) |
| BIZ-422xx | 校验错误 (01:表单必填缺失, 02:条件必填失败, 07:导出超限, 01/03:状态迁移) |
| BIZ-500xx | 内部错误 (01:工作流异常, 02:导出失败) |

## 测试

- **168 个测试用例**, 0 失败（Phase 1-3 共新增 54 个，较初始 114 个）
- Service 单元测试: Mockito (`@ExtendWith(MockitoExtension.class)`)
- Controller 集成测试: MockMvc + `AbstractControllerIntegrationTest`
- Facade 单元测试: Mockito
- 测试配置: H2 MySQL 兼容模式

## PT_RESOURCE SQL

`docs/superpowers/sql/2026-04-14-bizapp-pt-resource.sql` — 21 条记录，覆盖全部 21 个 REST 端点。

> **契约对齐说明（Phase 1-3）**: 本轮契约对齐（2026-04）未新增或变更任何 REST URL，
> 仅对返回类型、入参字段、Listener 机制、枚举值等做了内部补齐。
> 因此**不需要新建 PT_RESOURCE SQL 脚本**，上述 2026-04-14 脚本仍为最新版本。

## V1.0 已知技术债（2026-04-25）

| # | 标题 | 优先级 | 来源 |
|---|---|---|---|
| 1 | 错误码缺 5 条（BIZ-40306 / 42203 / 42204 / 42302 / 50003），近义码语义等价覆盖 | 低 | 健康检查 |
| 2 | LoanService.java:341 V2 集成 workflow 历史查询补 processMap/approvalLogs | 低 | 代码 TODO |
| 3 | LoanDetailResp.java:16 V2 通过 ClaimApi 补 ownerEmpId | 低 | 代码 TODO |
| 4 | LoanDetailResp.java:69 V2 通过 ClaimApi 补 claimedTime | 低 | 代码 TODO |
