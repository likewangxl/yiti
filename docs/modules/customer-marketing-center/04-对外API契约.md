# 客户营销中心 — 对外 API 契约

> 本文以 `customer-marketing-center/src/main/java/.../api` 的接口和 DTO 为准。公共 API 只返回 DTO/统计结果，调用方不得直连本模块 Mapper、Entity 或私有表。

## 1. `CustomerQueryApi`

| 方法 | 当前语义 |
|---|---|
| `getCustomer(String custId)` | 按客户 ID 返回 `Optional<CustomerDTO>` |
| `getCustomerByCustNo(String custNo)` | 按客户编号返回客户 |
| `listCustomers(List<String> custIds)` | 批量查询客户；实现限制为最多 500 个，空列表返回空列表 |
| `searchCustomers(String keyword, int limit)` | 按客户名/客户号/统一社会信用代码搜索，limit 为 1–50 |
| `isValidCustomer(String custId)` | 校验有效客户且未逻辑删除 |
| `isClaimedByOrg(String custId, String orgCode)` | 判断机构是否有 `CLAIMED` 认领 |
| `getCustomerClaims(String custId)` | 返回该客户有效 `CLAIMED` 认领关系列表 |
| `hasRunningProcess(String custId, String bizType)` | 当前支持 `TOUCH_TASK`，其他业务类型按实现返回 false |
| `listRunningProcesses(String custId)` | 当前返回客户在途触达任务 |
| `countCustomers(CustomerFilterDTO filter)` | 按过滤条件统计未删除客户 |

`CustomerDTO` 包含客户基本资料、行业/客户类型等字典值、主办机构/人员、认领池信息、标签和状态；`CustomerFilterDTO` 支持关键词、行业、客户类型、重点标识、状态、机构和创建时间范围。

## 2. `LeadApi`

| 方法 | 当前语义 |
|---|---|
| `getLead(String leadId)` | 查询单条线索 |
| `getLeadByBusinessKey(String businessKey)` | 按 `LEAD:{id}` 或批次业务键查询 |
| `getLeadsByBatch(String importBatchId)` | 查询批次下线索 |
| `getLeadVersionChain(String leadId)` | 按 `version_no` 升序返回线索版本链 |
| `isLeadCustNameAvailable(String custName, String excludeLeadId)` | 校验线索客户名称唯一性，编辑时排除自身 |

`LeadDTO` 暴露线索资料、`leadOp`、来源客户、版本关系、状态、业务键、批次和归属字段。线索写操作仍通过 REST，不在该公共 API 暴露。

## 3. `TagApi`

| 方法 | 当前语义 |
|---|---|
| `listEnabledTags()` | 按优先级/名称返回启用标签 |
| `getCustomerTags(String custId)` | 返回客户的启用标签 |
| `batchGetCustomerTags(List<String> custIds)` | 批量获取客户标签，最多 500 个客户 ID |
| `getCustomerIdsByTag(String tagId)` | 按标签返回客户 ID 列表 |
| `isTagNameExists(String tagName)` | 校验标签名称是否已存在 |

`TagDTO` 字段为 `id`、`tagName`、`tagCategory`、`tagPriority`、`status`、`description`。

## 4. `ClaimApi`

| 方法 | 当前语义 |
|---|---|
| `getClaim(String custId, String orgCode)` | 查询机构下有效 `CLAIMED` 认领 |
| `getEmpClaims(String empId)` | 查询员工有效认领 |
| `getOrgClaims(String orgCode)` | 查询机构有效认领 |
| `isClaimActive(String custId, String orgCode)` | 判断有效认领 |
| `countEmpClaims(String empId)` / `countOrgClaims(String orgCode)` | 统计有效认领数量 |

`CustClaimDTO` 包含认领 ID、客户、机构、维护人、状态和认领/取消时间及原因；`CANCELLED` 记录不作为有效认领返回。

## 5. `TouchTaskQueryApi`

| 方法 | 当前语义 |
|---|---|
| `getTouchTask(String taskId)` | 查询任务详情 |
| `getTouchTaskByBusinessKey(String businessKey)` | 按 `TOUCH:{taskId}` 查询 |
| `getEmpTouchTasks(String empId, String status)` | 按员工和可选状态查询任务 |
| `countRunningTouchTasks(String empId)` | 统计 `PENDING/IN_PROGRESS` 任务 |
| `getCustomerTouchHistory(String custId)` | 查询客户触达历史 |
| `getCustomerTouchHistoryByOrg(String custId, String orgCode)` | 查询客户在机构内触达历史 |
| `hasCompletedFirstTouch(String custId, String orgCode)` | 判断机构内首次触达是否成功完成 |
| `getOrgTouchSummary(String orgCode, String startDate, String endDate)` | 按日期字符串统计机构触达汇总 |

`TouchTaskDTO` 返回客户、机构、执行人、类型、状态、业务键、SLA、结果和工作日志计数；`TouchTaskSummaryDTO` 返回各状态计数和时长统计。

## 6. `AssetProjectQueryApi`

这是资产立项跨模块只读统计契约，禁止调用方直连 `MARKETING_ASSET_PROJECT_APPLY`：

| 方法 | 返回值 |
|---|---|
| `countRunningByCustomer(Long custId)` | 客户运行中资产立项数量 |
| `countByApplicant(String empId)` | 申请人创建数量 |
| `countCompletedByApplicant(String empId, LocalDateTime startTime, LocalDateTime endTime)` | 时间范围内已完成数量 |
| `sumCompletedCreditByApplicant(String empId, LocalDateTime startTime, LocalDateTime endTime)` | 时间范围内已完成授信金额 |

## 7. 领域事件

通过 Spring `ApplicationEventPublisher` 发布的当前事件及字段：

| 事件 | 字段 |
|---|---|
| `LeadRejectedEvent` | `leadId`、`leadNo`、`leadOp`、`sourceCustId`、`ownerOrgId`、`rejectReason`、`operatorEmpId` |
| `ClaimCancelledEvent` | `claimId`、`custId`、`orgId`、`cancelReason`、`operatorEmpId` |
| `ClaimTransferredEvent` | `claimId`、`custId`、`fromEmpId`、`toEmpId`、`operatorEmpId` |
| `TouchCompletedEvent` | `taskId`、`taskNo`、`custId`、`assigneeEmpId`、`taskType` |
| `CustomerDeletedEvent` | `custId`、`custNo`、`operatorEmpId` |

工作流回调使用 `ProcessCompletedEvent` 的业务键/结果更新线索、客户删除、资产立项等业务状态；回调幂等和事务见 `06-并发与事务策略.md`。

## 8. 调用约束

- 公共 API 的集合返回空集合而非 `null`；单条查询按接口声明使用 `Optional`。
- 批量查询遵守接口实现的上限，超过上限由业务异常返回；不得拆成跨模块逐行 Mapper 查询。
- 业务模块只能依赖上述 API、DTO 和事件，不得将 Entity 字段、SQL 列或缓存键当作契约。
- 修改接口、DTO、事件字段或 REST 资源时，必须同步更新本模块 `03`、资源授权和契约检查。
