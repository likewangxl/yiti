# business-application-center 设计规格

> 提取自 `docs/modules/business-application-center/` 9 份设计文档 + DDL 对齐
> 日期: 2026-04-14
> 基础包名: `com.bank.branch.platform.bizapp`
> 错误码前缀: `BIZ-{HTTP后两位}{序号}`

---

## 1. 模块定位

业务申请中心承载两大业务场景：**资产投放申请 (Loan)** 和 **中场支持申请 (Support)**。
是"营销→触达→落地"闭环的落地环节，也是绩效和报表模块最重要的事实源。

## 2. 数据库表 (2 张，DDL 权威源: `docs/schema/ddl-bizapp.sql`)

### 2.1 loan_apply — 资产投放申请表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) PK | UUID |
| apply_no | VARCHAR(100) UK | 编号 LA+yyyyMMdd+6位序号 |
| cust_id | VARCHAR(32) NOT NULL | 客户ID → cust_master.id |
| source_touch_task_id | VARCHAR(32) | 来源触达任务ID |
| project_type | VARCHAR(32) | 字典 PROJECT_TYPE |
| biz_type | VARCHAR(32) | 字典 BIZ_TYPE |
| guarantee_type | VARCHAR(32) | 字典 GUARANTEE_TYPE |
| credit_amount | DECIMAL(20,4) | 授信金额 |
| credit_exposure_amount | DECIMAL(20,4) | 敞口金额 |
| status | VARCHAR(20) NOT NULL DEFAULT 'DRAFT' | DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED |
| business_key | VARCHAR(100) | 格式 LOAN:{id} |
| process_instance_id | VARCHAR(64) | Flowable 流程实例ID |
| owner_org_id | VARCHAR(50) NOT NULL | 归属机构 |
| created_by | VARCHAR(32) NOT NULL | 创建人 |
| created_time | DATETIME | 创建时间 |
| updated_by | VARCHAR(32) | 更新人 |
| updated_time | DATETIME | 更新时间 |
| deleted | TINYINT(1) DEFAULT 0 | 逻辑删除 |

### 2.2 support_request — 中场支持申请表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) PK | UUID |
| request_no | VARCHAR(100) UK | 编号 SR+yyyyMMdd+6位序号 |
| submit_group_id | VARCHAR(64) | 多产品拆单同组ID |
| cust_id | VARCHAR(32) NOT NULL | 客户ID |
| source_touch_task_id | VARCHAR(32) | 来源触达任务ID |
| product_id | VARCHAR(64) | 产品ID → product_info.id |
| support_dept_id | VARCHAR(50) | 承接部门 ORG_CODE |
| other_demand | TEXT | 其他需求 |
| dispatch_emp_id | VARCHAR(32) | 派单人(秘书,仅场景B) |
| dispatch_time | DATETIME | 派单时间 |
| assigned_emp_id | VARCHAR(32) | 承接办理人 |
| status | VARCHAR(20) NOT NULL DEFAULT 'DRAFT' | DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED |
| business_key | VARCHAR(100) | 格式 SUPPORT:{id} |
| process_instance_id | VARCHAR(64) | Flowable 流程实例ID |
| owner_org_id | VARCHAR(50) NOT NULL | 归属机构(发起侧) |
| created_by | VARCHAR(32) NOT NULL | 创建人(发起人) |
| created_time | DATETIME | 创建时间 |
| updated_by | VARCHAR(32) | 更新人 |
| updated_time | DATETIME | 更新时间 |
| deleted | TINYINT(1) DEFAULT 0 | 逻辑删除 |

## 3. 状态机

### 3.1 LoanStatus

```
DRAFT → submit() → IN_APPROVAL
IN_APPROVAL → workflow approved → COMPLETED
IN_APPROVAL → workflow rejected → REJECTED
IN_APPROVAL → initiator cancel → CANCELLED
DRAFT → delete → (soft delete, deleted=1)
```

### 3.2 SupportStatus

```
DRAFT → submit() → IN_APPROVAL
IN_APPROVAL → dispatch() → IN_PROGRESS     (仅场景B)
IN_APPROVAL → complete(SUCCESS) → COMPLETED (场景A直接完成)
IN_PROGRESS → complete(SUCCESS) → COMPLETED
IN_PROGRESS → complete(FAILED) → REJECTED
IN_APPROVAL/IN_PROGRESS → cancel → CANCELLED
DRAFT → delete → (soft delete)
```

场景 A: DRAFT → IN_APPROVAL → COMPLETED (无 IN_PROGRESS)
场景 B: DRAFT → IN_APPROVAL → IN_PROGRESS → COMPLETED

## 4. 场景路由

```
if (productIds 非空 && otherDemand 为空):
    scenario = A  // 明确产品，流程 support_simple_v1
    assigned_emp_id = 产品负责人 (从 ProductApi.getProductResponsibleEmpIds)
elif (productIds 为空 || otherDemand 非空):
    scenario = B  // 含模糊需求，流程 support_complex_v1
    support_dept_id 必填
else:
    BIZ-40903
```

## 5. 多产品拆单

选择多个产品时(场景A)：
1. 生成 submit_group_id = UUID
2. 每个 productId 生成独立 support_request 记录
3. 共享 cust_id、submit_group_id，独立的 request_no/business_key
4. 每条独立走 support_simple_v1 流程
5. 同一客户同一产品不允许 >2 条 IN_APPROVAL/IN_PROGRESS，否则 BIZ-40906

## 6. REST 端点 (21 个)

### 6.1 LoanController (`/api/loans`) — 9 端点

| 方法 | 端点 | BizAuth | 说明 |
|------|------|---------|------|
| GET | /api/loans | LOAN/LIST | 分页列表 |
| GET | /api/loans/{id} | LOAN/READ | 详情(含流程进度+审批日志+可操作判定) |
| POST | /api/loans | LOAN/WRITE | 创建草稿 |
| PUT | /api/loans/{id} | LOAN/WRITE | 更新草稿 |
| POST | /api/loans/{id}/submit | LOAN/WRITE | 提交审批(SELECT FOR UPDATE) |
| DELETE | /api/loans/{id} | LOAN/WRITE | 删除草稿(软删) |
| POST | /api/loans/{id}/cancel | LOAN/WRITE | 撤回申请 |
| GET | /api/loans/export | LOAN/EXPORT | 导出(高危) |
| GET | /api/loans/{id}/node-form/{nodeKey} | LOAN/READ | 节点表单配置 |

### 6.2 SupportController (`/api/support-requests`) — 8 端点

| 方法 | 端点 | BizAuth | 说明 |
|------|------|---------|------|
| GET | /api/support-requests | SUPPORT/LIST | 发起侧列表 |
| GET | /api/support-requests/{id} | SUPPORT/READ | 详情 |
| POST | /api/support-requests | SUPPORT/WRITE | 创建(含多产品拆单) |
| POST | /api/support-requests/{id}/submit | SUPPORT/WRITE | 草稿提交 |
| DELETE | /api/support-requests/{id} | SUPPORT/WRITE | 删除草稿 |
| POST | /api/support-requests/{id}/cancel | SUPPORT/WRITE | 撤回 |
| GET | /api/support-requests/export | SUPPORT/EXPORT | 导出(高危) |
| GET | /api/support-requests/available-products | SUPPORT/READ | 可用产品列表 |

### 6.3 SupportDeptController (`/api/support-dept/requests`) — 4 端点

| 方法 | 端点 | BizAuth | 说明 |
|------|------|---------|------|
| GET | /api/support-dept/requests | SUPPORT_DEPT/LIST | 承接侧列表 |
| POST | /api/support-dept/requests/{id}/dispatch | SUPPORT_DEPT/WRITE | 秘书派单 |
| POST | /api/support-dept/requests/{id}/transfer | SUPPORT_DEPT/TRANSFER | 转交(高危) |
| POST | /api/support-dept/requests/{id}/complete | SUPPORT_DEPT/WRITE | 办理完成 |

## 7. 对外 API (5 个接口)

### 7.1 LoanApi

```java
Optional<LoanApplyDTO> getLoanApply(String applyId);
Optional<LoanApplyDTO> getLoanApplyByBusinessKey(String businessKey);
List<LoanApplyDTO> getCustomerLoanHistory(String custId);
List<LoanApplyDTO> getLoanApplyBatch(List<String> applyIds);
```

### 7.2 LoanQueryApi

```java
PageResult<LoanApplyDTO> pageQuery(LoanQueryConditionDTO condition);
long countCompletedByOrg(String orgId, LocalDateTime start, LocalDateTime end);
BigDecimal sumCreditAmountByEmp(String empId, LocalDateTime start, LocalDateTime end);
```

### 7.3 SupportApi

```java
Optional<SupportRequestDTO> getSupportRequest(String requestId);
Optional<SupportRequestDTO> getSupportRequestByBusinessKey(String businessKey);
List<SupportRequestDTO> getCustomerSupportHistory(String custId);
List<SupportRequestDTO> getBySubmitGroup(String submitGroupId);
List<SupportRequestDTO> getSupportRequestBatch(List<String> requestIds);
```

### 7.4 SupportQueryApi

```java
PageResult<SupportRequestDTO> pageQuery(SupportQueryConditionDTO condition);
long countCompletedByCreator(String empId, LocalDateTime start, LocalDateTime end);
long countCompletedByAssignee(String empId, LocalDateTime start, LocalDateTime end);
```

### 7.5 BizApplyQueryApi

```java
RunningAppCountDTO countRunningApplications(String custId);
boolean hasRunningLoan(String custId);
boolean hasRunningSupport(String custId);
BizApplyStatDTO getEmpStatistics(String empId);
BizApplyStatDTO getEmpStatisticsByPeriod(String empId, LocalDateTime start, LocalDateTime end);
```

## 8. 上游 API 实际签名 (与设计文档差异标注)

### 8.1 WorkflowApi (实际已实现)

```java
WorkflowLaunchResp startProcess(StartProcessCmd cmd);
BizProcessMapDTO getProcessByBusinessKey(String businessKey);
BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);
```

**注意**: `completeTask`/`cancelProcess`/`setTaskVariables`/`reassignTask` 等操作不在 API 接口中，
而是通过 `TaskController` REST 端点 (`/api/workflow/tasks/{taskId}/approve|reject|transfer`) 实现。
本模块不直接调用 Flowable，而是：
- 启动流程: 调用 `WorkflowApi.startProcess()`
- 审批/驳回: 由前端直接调 workflow-center REST 端点，workflow-center 发布事件回调本模块
- 撤回: 需在 WorkflowApi 中新增 `cancelProcess()` 方法 (或通过 REST)

### 8.2 CustomerQueryApi (实际已实现)

```java
CustMaster getCustomer(String id);
CustMaster getCustomerByCustNo(String custNo);
boolean isValidCustomer(String custId);
boolean isClaimedByOrg(String custId, String orgId);
List<CustClaim> getCustomerClaims(String custId);
```

**差异**: 设计文档提到的 `isCompanyApproved()` 不存在。
**适配**: 使用 `isValidCustomer()` + `getCustomer()` 检查 `status=ACTIVE`。

### 8.3 TouchTaskQueryApi (实际已实现)

```java
TouchTask getTaskById(String taskId);
String getTaskStatus(String taskId);
String getSlaStatus(String taskId);
```

**差异**: 方法名是 `getTaskById()` 而非 `getTouchTask()`。
`TouchTask` 实体有 `assigneeEmpId` 字段用于校验执行人。

### 8.4 ProductApi (实际已实现)

```java
Optional<ProductDTO> getProduct(String productId);
List<ProductDTO> getProducts(List<String> productIds);
List<ProductDTO> listSupportAvailableProducts();
List<ProductDTO> listProductsByDept(String productDeptOrgCode);
List<String> getProductResponsibleEmpIds(String productId);
```

完全匹配设计。

### 8.5 AddressBookApi (实际已实现)

```java
Optional<EmployeeDTO> getEmployee(String empId);
List<EmployeeDTO> getEmployees(List<String> empIds);
List<EmployeeDTO> searchEmployees(String keyword, int limit);
List<EmployeeDTO> listEmployeesByOrg(String orgCode);
boolean isCustomerManager(String empId);
```

**差异**: `listSupportDepartments()` 和 `listOrgSecretaries()` 不存在。
**适配**: 秘书/支持部门由 `OrgApi` + 角色配置 + `listEmployeesByOrg()` 组合实现。

## 9. 领域事件 (6 个)

| 事件 | 发布时机 | 消费方 |
|------|---------|--------|
| LoanSubmittedEvent | 资产投放提交成功 | portal(工作台) |
| LoanApprovedEvent | 流程完成(通过) | performance, customer |
| LoanRejectedEvent | 流程驳回 | portal(通知) |
| SupportSubmittedEvent | 中场支持提交(按拆单后的每条) | portal |
| SupportDispatchedEvent | 秘书派单完成 | portal(通知) |
| SupportCompletedEvent | 办理完成 | performance, customer |

监听: workflow-center 的 `ProcessCompletedEvent` / `TaskApprovedEvent` / `TaskRejectedEvent`

## 10. 错误码

| 错误码 | HTTP | 含义 |
|--------|------|------|
| BIZ-40301 | 403 | 客户非有效公司客户 |
| BIZ-40302 | 403 | 非触达任务执行人 |
| BIZ-40303 | 403 | 客户未被本机构认领 |
| BIZ-40304 | 403 | 非承接部门人员/非当前承接人 |
| BIZ-40305 | 403 | 非申请创建人无权操作 |
| BIZ-40901 | 409 | 产品不支持中场支持 |
| BIZ-40902 | 409 | 场景B缺少supportDeptId |
| BIZ-40903 | 409 | productIds和otherDemand都为空 |
| BIZ-40904 | 409 | 字典值不合法 |
| BIZ-40905 | 409 | 敞口金额 > 授信金额 |
| BIZ-40906 | 409 | 同客户同产品重复申请 |
| BIZ-40907 | 409 | 并行申请超限(未二次确认) |
| BIZ-40401 | 404 | 申请不存在 |
| BIZ-42201 | 422 | 节点表单必填字段缺失 |
| BIZ-42202 | 422 | 节点表单条件必填校验失败 |
| BIZ-42207 | 422 | 导出行数超上限 |
| BIZ-42301 | 422 | 非法状态迁移 |
| BIZ-42303 | 422 | 申请非草稿状态不可编辑 |
| BIZ-50001 | 500 | 工作流调用异常 |
| BIZ-50002 | 500 | 导出文件生成失败 |

## 11. 包结构

```
com.bank.branch.platform.bizapp/
├── api/                    # 对外 API (5接口 + DTO + 事件)
├── controller/             # REST 控制器 (4个: Loan, LoanNodeForm, Support, SupportDept)
├── dto/req/                # 请求 DTO
├── dto/resp/               # 响应 DTO
├── entity/                 # 实体 (LoanApply, SupportRequest)
├── enums/                  # 枚举 (BizAppErrorCode, LoanStatus, SupportStatus, SupportScenario, SupportSourceType, BizType)
├── event/                  # 领域事件 (6个)
├── facade/                 # API 实现 (5个)
├── listener/               # 工作流事件监听 (ProcessCompleted, TaskApproved, TaskRejected)
├── mapper/                 # MyBatis Mapper (2个 + XML)
├── service/                # 业务逻辑
│   ├── LoanService.java              # 资产投放编排
│   ├── LoanFormValidator.java        # 节点表单校验
│   ├── SupportService.java           # 中场支持编排(发起侧)
│   ├── SupportScenarioRouter.java    # 场景A/B路由
│   ├── SupportProductSplitService.java # 多产品拆单
│   ├── SupportDeptService.java       # 承接侧(派单/转交/完成)
│   ├── BizStateMachine.java          # 统一状态机
│   ├── BizApplySearchService.java    # 查询聚合
│   └── BizNoGenerator.java           # 编号生成器
└── config/                 # 配置
    └── BizAppCacheConfig.java
```

## 12. V1 导出能力 — 降级为预留

导出接口(A.8/C.7)在 V1 中标记为预留(返回 501 Not Implemented)，原因：
- 需要依赖 `system-governance-center` 的 `sys_async_task` 表和异步导出框架
- EasyExcel 导出 + MinIO 上传的完整链路在 V1 阶段不作为核心优先级
- 端点路由和 @BizAuth 注解照常注册，后续迭代激活

## 13. 关键设计决策

1. **贫血模型**: Entity 只承载数据，状态迁移由 `BizStateMachine` 管理
2. **跨模块只走 Api**: 严禁直连 customer/portal 的 mapper/entity
3. **工作流分离**: 审批动作由前端调 workflow-center REST，本模块通过事件回调更新状态
4. **节点表单解耦**: V1 仅硬编码 loan_corp_review 节点，但按通用 Validator 模式实现
5. **双视图权限**: SUPPORT(发起侧) vs SUPPORT_DEPT(承接侧) 分离 Controller
6. **SELECT FOR UPDATE**: submit 操作锁行防并发
7. **编号生成**: LA/SR + yyyyMMdd + 6位序号，通过数据库自增或 Redis 原子递增
