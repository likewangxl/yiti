# 客户营销中心 — 表结构与字段说明

> 本文描述当前实体与表的职责和稳定字段语义，不是可执行 DDL。目标库实际 schema、索引、字段长度及字符集以目标环境只读核查和 DBA 审批结果为准。

## 1. 客户主档与线索

| 实体 | 表 | 关键字段/语义 |
|---|---|---|
| `MarketingCustomerInfo` | `MARKETING_CUSTOMER_INFO` | `custNo`、`custName`、统一社会信用代码、企业资料、开户/重点/触达限制、`mainManagerId`、`mainOrgId`、`ownershipStatus`、`profileVersion`、`lockVersion`、`recordStatus` |
| `CustMaster` | `CUSTOMER_MARKET_CUSTOMER` | 客户营销主档的兼容读写模型；客户号、客户资料、当前线索、主办权、状态、逻辑删除和 `lockVersion` |
| `M98CustMaster` | `CUST_MASTER` | M98 同步/查询兼容模型；不作为营销接口的写入主表 |
| `MarketingLeadInfo` | `MARKETING_LEAD_INFO` | 线索资料快照、`leadNo`、来源/分配、主办快照、`leadStatus`、`businessKey`、流程实例、批次、审核和 `lockVersion` |
| `CustLead` | `CUST_LEAD` | 传统线索模型；`leadOp`、`sourceCustId`、`prevLeadId`、`versionNo`/`isLatest`、状态、审批和批次字段 |
| `MarketingLeadManagerScope` | `MARKETING_LEAD_MANAGER_SCOPE` | 线索可见/分配的员工、机构和主次标识 |
| `CustLeadManagerScope` | `CUST_LEAD_MANAGER_SCOPE` | 传统线索的员工/机构范围 |
| `MarketingLeadTagRel` | `MARKETING_LEAD_TAG_REL` | 营销线索与标签关系及标签快照 |
| `CustLeadTagRel` | `CUST_LEAD_TAG_REL` | 传统线索与标签关系 |

线索状态由 `LeadStatus` 定义为 `DRAFT`、`SUBMITTED`、`IN_APPROVAL`、`APPROVED`、`REJECTED`；`leadOp` 为 `CREATE`、`UPDATE`、`DELETE`。新旧实体并存时，以调用的 Controller/Service 和表映射为准，不跨表猜测主档来源。

## 2. 标签与导入

| 实体 | 表 | 关键字段/语义 |
|---|---|---|
| `MarketingCustomerTag` | `MARKETING_CUSTOMER_TAG` | 标签名称、分类/类型、优先级、状态、审核状态、有效期、归属机构、`lockVersion` |
| `CustTag` | `CUST_TAG` | 传统标签模型，含标签状态、审核状态、归属和逻辑删除 |
| `MarketingCustomerTagRel` | `MARKETING_CUSTOMER_TAG_REL` | 客户/标签关系、来源、有效/失效时间和 `active` |
| `CustTagRel` | `CUST_TAG_REL` | 传统客户标签关系及生效时间 |
| `MarketingCustomerTagImportBatch` / `Detail` | `MARKETING_CUSTOMER_TAG_IMPORT_BATCH` / `..._DETAIL` | 文件、校验计数、导入模式、审批状态、行级错误、生成线索、装载关系和锁版本 |
| `MarketingLeadImportBatch` / `Detail` | `MARKETING_LEAD_IMPORT_BATCH` / `..._DETAIL` | 文件校验、行级匹配、警告/错误、生成线索、确认动作和锁版本 |
| `LeadImportBatch` | `LEAD_IMPORT_BATCH` | 传统线索批次、错误文件、流程实例和归属信息 |

`TagStatus` 为 `ACTIVE/DISABLED`；有效客户标签须满足当前服务的审核、启用和生效条件。批次状态字段以各实体/Service 为准，不能把文件预览计数当作主档写入结果。

## 3. 认领、主办权与转交

| 实体 | 表 | 关键字段/语义 |
|---|---|---|
| `MarketingCustomerClaim` | `MARKETING_CUSTOMER_CLAIM` | 客户、来源线索、机构、认领员工、`CLAIMED/CANCELLED`、时间、原因、锁版本 |
| `CustClaim` | `CUST_CLAIM` | 传统认领关系、维护人、状态、取消时间和原因 |
| `MarketingCustomerTransferLog` / `Target` | `MARKETING_CUSTOMER_TRANSFER_LOG` / `..._TARGET` | 主办权变更前后、动作、原因、操作人、状态及接收人 |
| `CustTransferLog` / `Target` | `CUST_TRANSFER_LOG` / `CUST_TRANSFER_TARGET` | 传统转交记录和接收人 |
| `MarketingCustomerPerformanceRelSnapshot` | `MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT` | 客户与员工/机构业绩关系、比例、生效/失效日期、来源批次和刷新状态 |
| `CustPerformanceRelationSnapshot` | `CUST_PERFORMANCE_RELATION_SNAPSHOT` | 传统业绩关系快照 |

主办权和认领更新使用目标行锁或 `lock_version` CAS，并记录转交日志；`CLAIMED` 之外的关系不作为有效认领。

## 4. 触达与工作日志

| 实体 | 表 | 关键字段/语义 |
|---|---|---|
| `TouchTask` | `MARKETING_TOUCH_TASK` | 客户、来源、机构、执行人、`FIRST_TOUCH/FOLLOW_UP`、任务状态、计划/SLA、结果和锁版本 |
| `TouchWorklog` | `MARKETING_TOUCH_WORKLOG` | 任务、客户快照、操作人、触达时间/方式/结果、首次触达标识、`clientUuid`、作废信息 |
| `TouchWorklogPictureRecord` | `MARKETING_TOUCH_WORKLOG_PICTURE` | 工作日志图片文件对象、类型、顺序和有效状态 |
| `TouchWorklogParticipant` | `MARKETING_TOUCH_WORKLOG_PARTICIPANT` | 工作日志参与员工、机构和角色 |
| `TouchLimitRule` | `CUST_TOUCH_LIMIT_RULE` | 标签触达周期、周期单位、启用状态和规则版本 |

任务状态为 `PENDING`、`IN_PROGRESS`、`SUCCESS`、`CANCELLED`；工作日志以 `(task_id, client_uuid)` 防止重复写入，文件对象由治理模块管理。

## 5. 跨机构营销

| 实体 | 表 | 关键字段/语义 |
|---|---|---|
| `MarketingCrossOrgRule` | `MARKETING_CROSS_ORG_RULE` | 规则编码、启用状态、数据来源、失败提示和排序 |
| `MarketingCrossOrgApply` | `MARKETING_CROSS_ORG_APPLY` | 客户、申请人/机构、主办快照、条件校验快照、原因、状态、生成触达任务、业务键/流程实例、审核结果和锁版本 |
| `CrossOrgMarketingRule` | `CROSS_ORG_MARKETING_RULE` | 传统规则模型 |
| `CrossOrgMarketingApply` | `CROSS_ORG_MARKETING_APPLY` | 传统申请模型，字段语义与营销申请相同但类型/ID 兼容旧 Controller |

跨机构申请由 Service 校验主办权、机构、业绩归属和重复申请；状态更新必须以待审批状态为条件，避免重复审核覆盖。

## 6. 资产立项

| 实体 | 表 | 关键字段/语义 |
|---|---|---|
| `AssetProjectApply` | `MARKETING_ASSET_PROJECT_APPLY` | 申请编号、客户/触达来源、项目及金额、加急/重点标识、申请人机构、状态、`ASSET_PROJECT:{id}` 业务键、流程实例、提交/完成时间、`recordStatus`、`lockVersion` |
| `AssetProjectUrgentApply` | `MARKETING_ASSET_PROJECT_URGENT_APPLY` | 主项目、客户、申请节点/任务、原因、状态、`activeDedupKey`、`ASSET_PROJECT_URGENT:{id}` 业务键、审核结果和锁版本 |

资产主申请状态为 `DRAFT`、`IN_APPROVAL`、`COMPLETED`、`REJECTED`、`CANCELLED`；草稿编辑、提交、撤回和加急均由 `AssetProjectService` 按版本/流程节点校验。加急活动记录通过 `activeDedupKey` 防重。

## 7. 关系与维护边界

- 客户、线索、标签、认领、触达和资产申请通过逻辑字段关联，跨模块不建立私有表 JOIN。
- 逻辑删除/记录状态按实体字段过滤；查询和统计必须与对应 Mapper 的有效状态条件一致。
- 文件、字典、通知、用户/组织和工作流实例由公共模块提供；本模块只保存业务引用。
- schema 变更由 DBA 在目标库实施，必须同时核对 Entity、Mapper、DTO、数据范围、审计和事务影响；本文不包含建表、迁移或回滚脚本。
