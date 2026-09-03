# 业务申请中心 — 对外 API 契约

> 本文只保留当前 Java 公共 API。实现以 `api/`、DTO 和 `facade/` 源码为准；调用方不得依赖本模块的 Entity、Mapper、Service 或数据库表。

## 1. 契约原则

- 公共 API 只提供只读查询和统计；写操作通过本模块 REST 与业务权限链路完成。
- 集合查询无数据返回空集合，单条查询使用 `Optional`。
- `SupportRequestDTO` 不包含 `deleted` 等内部运维字段；展示名称由 facade 转换器通过客户、产品和组织公开 API 补充。
- 资产立项由 `customer-marketing-center` 所有。本模块只通过其 `AssetProjectQueryApi` 获取统计，不直连资产表。

## 2. `SupportApi`

包名：`com.bank.branch.platform.bizapp.api.SupportApi`。

| 方法 | 返回值 | 语义 |
|---|---|---|
| `getSupportRequest(String requestId)` | `Optional<SupportRequestDTO>` | 按申请 ID 查询 |
| `getSupportRequestByBusinessKey(String businessKey)` | `Optional<SupportRequestDTO>` | 按 `SUPPORT:{id}` 查询 |
| `getCustomerSupportHistory(String custId)` | `List<SupportRequestDTO>` | 按客户查询历史，最新在前 |
| `getBySubmitGroup(String submitGroupId)` | `List<SupportRequestDTO>` | 查询同批拆单申请 |
| `getSupportRequestBatch(List<String> requestIds)` | `List<SupportRequestDTO>` | 按 ID 批量查询；实现对批量规模有限制 |

## 3. `SupportQueryApi`

| 方法 | 返回值 | 语义 |
|---|---|---|
| `pageQuery(SupportQueryConditionDTO condition)` | `PageResult<SupportRequestDTO>` | 支持关键字、状态、归属机构、承接部门和分页条件 |
| `countCompletedByCreator(String empId, LocalDateTime startTime, LocalDateTime endTime)` | `long` | 时间范围内创建人已完成数量 |
| `countCompletedByAssignee(String empId, LocalDateTime startTime, LocalDateTime endTime)` | `long` | 时间范围内承接人已完成数量 |

`SupportQueryConditionDTO` 字段为 `keyword`、`status`、`ownerOrgId`、`supportDeptId`、`pageNo`、`pageSize`；状态值为 `DRAFT`、`IN_APPROVAL`、`IN_PROGRESS`、`COMPLETED`、`REJECTED`、`CANCELLED`。

## 4. `BizApplyQueryApi`

该接口提供跨域统计，方法签名保持 Java 兼容：

| 方法 | 语义 |
|---|---|
| `countRunningApplications(String custId)` | 返回 `RunningAppCountDTO`，其中支持申请运行中数量来自本模块，资产统计通过客户模块公开 API 获取 |
| `hasRunningLoan(String custId)` | 兼容字段名；实际委托客户模块 `AssetProjectQueryApi.countRunningByCustomer` |
| `hasRunningSupport(String custId)` | 判断本模块支持申请是否处于 `IN_APPROVAL` 或 `IN_PROGRESS` |
| `getEmpStatistics(String empId)` | 返回员工全时段 `BizApplyStatDTO` |
| `getEmpStatisticsByPeriod(String empId, LocalDateTime start, LocalDateTime end)` | 返回员工时间范围统计 |

`RunningAppCountDTO` 的 `runningLoanCount`、`runningSupportCount` 和 `BizApplyStatDTO` 的 `totalLoans`、`completedLoans`、`totalSupports`、`completedSupports`、`totalCreditAmount` 是现行 DTO 字段。资产统计的事实源是客户营销模块，当前业务申请模块不再提供相应实体或 REST 契约。

## 5. DTO 边界

### `SupportRequestDTO`

基础字段：`id`、`requestNo`、`submitGroupId`、`custId`、`sourceTouchTaskId`、`productId`、`supportDeptId`、`otherDemand`、`dispatchEmpId`、`dispatchTime`、`assignedEmpId`、`status`、`businessKey`、`processInstanceId`、`ownerOrgId`、`createdBy`、`createdTime`、`updatedBy`、`updatedTime`。

展示字段：`custName`、`productName`、`supportDeptName`。展示字段由 converter 组装，不代表本模块拥有客户、产品或组织主数据。

### `SupportQueryConditionDTO`

字段：`keyword`、`status`、`ownerOrgId`、`supportDeptId`、`pageNo`、`pageSize`。调用方应传递有效分页值，不得假定数据库分页细节。

## 6. 领域事件

事件通过 Spring `ApplicationEventPublisher` 在业务事务内发布，字段以对应 Java class 为准：

| 事件 | 字段 | 发布时机 |
|---|---|---|
| `SupportSubmittedEvent` | `requestId`、`requestNo`、`custId`、`productId`、`ownerOrgId`、`operatorEmpId` | `DRAFT → IN_APPROVAL` 后 |
| `SupportDispatchedEvent` | `requestId`、`requestNo`、`assignedEmpId`、`dispatcherEmpId`、`supportDeptId`、`dispatchRemark` | 部门派单后 |
| `SupportCompletedEvent` | `requestId`、`requestNo`、`custId`、`productId`、`assignedEmpId`、`success` | 办理成功或流程通过后 |
| `SupportRejectedEvent` | `requestId`、`requestNo`、`custId`、`createdBy`、`rejectReason` | 流程结果为驳回后 |

工作流中心向本模块发布 `ProcessCompletedEvent`；`SupportWorkflowListener` 仅消费业务键前缀为 `SUPPORT:` 的事件，将 `IN_APPROVAL` 条件更新为完成或驳回，并保证重复回调不重复发布领域事件。

## 7. 调用与变更约束

- 调用方只依赖上述接口和 DTO，通过 Spring 注入 facade 实现。
- 统计时间范围按 `LocalDateTime` 的包含边界处理，分页与批量参数限制以实现和 DTO 校验为准。
- 新增或修改方法、DTO、事件字段时，必须同步更新 REST 契约、资源授权和契约检查；不得用删除旧方法的方式掩盖实现迁移。
