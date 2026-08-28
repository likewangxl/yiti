<!--
本文由已确认的《客户营销模块表结构及字段说明_20260826》同步整理。
这是目标数据模型设计，不表示当前数据库、实体、Mapper或接口已经完成改名和迁移。
结构实施仍由DBA按审批结果执行，本文不是可执行DDL。
-->

# 客户营销模块表结构及字段说明

> 目标设计稿  v1.1；编制日期：2026年8月26日

> **说明：** 本文件为业务与数据模型评审稿，不是可直接执行的DDL。字段长度、索引及数据库兼容性需经DBA评审。
> 六个管理页面、角色权限、规划REST契约和后端事务逻辑见 [10-营销客户管理前后端功能设计.md](10-营销客户管理前后端功能设计.md)。

## 1. 设计范围与确认口径

编制依据：《01_需求说明书_公司部_V1.2》、《0811-客户营销-会议纪要》以及2026年8月26日本轮已确认的客户主档、线索导入审批、主办权同步、客户池和触达规则。发生冲突时，以本轮明确确认的口径为本设计稿基准。

- 客户营销域自有表统一使用 MARKETING_ 前缀；平台共享表和外部每日客户经理关系快照表不改名。

- 不建设CCRM接入表；客户主办关系来自外部每日全量快照，源表至少提供客户号和客户经理工号。

- 每日任务只更新 MARKETING_CUSTOMER_INFO 中已存在且维护模式为AUTO的客户主办信息，不自动新增客户。

- 不建设 MARKETING_CUSTOMER_OWNER_SYNC_RUN、MARKETING_CUSTOMER_OWNER_HISTORY、MARKETING_CUSTOMER_OWNER_SYNC_ERROR；异常使用治理中心任务日志/告警，由营销管理员处理。

- 客户主办权支持AUTO自动同步与MANUAL人工维护两个模式。切为MANUAL后每日同步跳过；恢复AUTO时立即读取最新全量快照刷新。

- 营销客户一企一档，统一社会信用代码为唯一业务键，主键使用BIGINT UNSIGNED AUTO_INCREMENT。

- 线索不保存版本链。每次营销事项是一条独立线索，线索保存录入人、录入时间和完整提交快照；审批通过后才新增或更新客户主档。

- 线索分为 NEW_ACCOUNT 和 EXISTING_MARKETING。客户主档已存在或已经开户都不是拒绝导入的理由；审批页必须展示当前开户和主办信息。

- 同一统一社会信用代码同一时间只允许一条DRAFT、SUBMITTED或IN_APPROVAL线索。重复新增/导入时拒绝并提示原录入人、机构、时间、线索编号和状态。

- 未审批线索只进入线索工作台/审批列表；待认领池仅包含 APPROVED + PUBLIC + AVAILABLE 的线索。

- 存量已开户客户仍可营销；有有效主办权时直接进入主办人的已认领客户池，不进入公共待认领池。再次录入时必须反显并提示当前主办归属。

- 历史 xa_touch_* 表允许改名为 MARKETING_TOUCH_*，并按任务、日志、图片、参与人、团队和名单职责规范化。

- 线索录入历史页以单条 MARKETING_LEAD_INFO 为数据粒度，不按客户聚合；手工录入与批量导入分Tab展示。

- 标签客户导入必须先进入批次和客户明细待审区；标签与客户均满足审批条件后才能写入正式标签关系。全量替换采用延迟生效，不在逐条审批过程中修改正式客户群。

## 2. 统一技术规范

| 项目 | 统一规范 |
| --- | --- |
| 主键 | BIGINT UNSIGNED AUTO_INCREMENT |
| 客户关联 | cust_id BIGINT UNSIGNED |
| 员工工号 | VARCHAR(32) |
| 机构代码 | VARCHAR(50) |
| 金额 | DECIMAL(18,2)，单位为元 |
| 时间 | DATETIME；业务日期使用DATE |
| 字符集 | 统一采用utf8mb4及同一排序规则 |
| 外键 | 不建立物理外键，由Service事务、唯一索引和逻辑校验保证完整性 |
| 删除 | 业务数据原则上不物理删除，使用状态或有效标志 |
| 并发 | 可修改主表使用lock_version；关键唯一性同时由数据库约束兜底 |

## 3. 营销自有表清单

| 序号 | 领域 | 表名 | 主要用途 |
| --- | --- | --- | --- |
| 1 | 客户域 | MARKETING_CUSTOMER_INFO | 营销客户唯一主档，一企一档；也是营销客户总列表的数据来源。 |
| 2 | 客户域 | MARKETING_CUSTOMER_TAG | 正式客户标签字典，保存标签审核、启停和有效期。 |
| 3 | 客户域 | MARKETING_CUSTOMER_TAG_REL | 客户与正式标签的关系，保存当前有效关系及失效时间。 |
| 4 | 客户域 | MARKETING_CUSTOMER_CLAIM | 按线索记录客户认领及取消关系；同一客户可因不同营销线索产生多次认领。 |
| 5 | 客户域 | MARKETING_CUSTOMER_TRANSFER_LOG | 管理员或业务流程变更主办/维护关系的转交主记录。 |
| 6 | 客户域 | MARKETING_CUSTOMER_TRANSFER_TARGET | 转交接收人明细，支持一名主办、多名协办。 |
| 7 | 客户域 | MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT | 客户业绩相关人和机构的查询快照，服务跨机构营销校验；不直接授予客户权限，也不是绩效计算结果表。 |
| 8 | 客户域 | MARKETING_CUSTOMER_OPEN_CONFIRM_APPLY | 在缺少直接开户状态回传时，由客户经理发起、公司部审批的人工确认开户单据；可关联完成开户的触达任务。 |
| 9 | 线索域 | MARKETING_LEAD_INFO | 一次独立营销线索的完整提交快照、分配方式和审批结果；不保存版本链。 |
| 10 | 线索域 | MARKETING_LEAD_MANAGER_SCOPE | 保存SCOPE指定客户经理范围或OWNER专属接收人。 |
| 11 | 线索域 | MARKETING_LEAD_TAG_REL | 保存线索提交时的标签名称快照，不受标签后续改名或失效影响。 |
| 12 | 线索域 | MARKETING_LEAD_IMPORT_BATCH | 一次Excel线索导入批次头，记录文件、统计、处理和审批状态。 |
| 13 | 线索域 | MARKETING_LEAD_IMPORT_DETAIL | 保存导入文件的全部行，包括成功、拒绝和异常行，用于导入历史详情及问题前排展示。 |
| 14 | 触达域 | MARKETING_TOUCH_TASK | 触达任务，保存来源、执行人、机构、任务状态和SLA。 |
| 15 | 触达域 | MARKETING_TOUCH_WORKLOG | 触达工作日志事实表，由原 xa_touch_custom_worklogs 规范化改名；新数据关联任务，历史迁移数据的 task_id 允许为空。 |
| 16 | 触达域 | MARKETING_TOUCH_WORKLOG_PICTURE | 触达日志图片，一张图片一行；由原xa_touch_custom_worklogs_picture_record规范化改名。 |
| 17 | 触达域 | MARKETING_TOUCH_WORKLOG_PARTICIPANT | 触达参与人明细，替代历史逗号分隔协同人员字段。 |
| 18 | 触达域 | MARKETING_TOUCH_TEAM | 触达团队主数据。 |
| 19 | 触达域 | MARKETING_TOUCH_TEAM_MEMBER | 触达团队成员明细。 |
| 20 | 触达域 | MARKETING_TOUCH_CUSTOMER_TAG_SOURCE | 预导入企业—标签名单来源；使用 loaded_flag 区分是否已载入正式客户标签关系。 |
| 21 | 触达域 | MARKETING_TOUCH_LIMIT_RULE | 按正式客户标签配置触达周期和次数限制。 |
| 22 | 跨机构营销域 | MARKETING_CROSS_ORG_RULE | 跨机构营销申请的可配置校验规则。 |
| 23 | 跨机构营销域 | MARKETING_CROSS_ORG_APPLY | 跨机构营销申请、四项校验快照、审批结果及生成触达任务。 |
| 24 | 客户域 | MARKETING_CUSTOMER_TAG_IMPORT_BATCH | 单个标签的追加或全量替换导入批次，保存OBS文件、统计、审批汇总和生效状态。 |
| 25 | 客户域 | MARKETING_CUSTOMER_TAG_IMPORT_DETAIL | 标签导入逐客户明细，保存校验、客户审批、线索关联和正式标签关系载入结果。 |
| 26 | 资产立项域 | MARKETING_ASSET_PROJECT_APPLY | 资产立项主申请与台账，关联营销客户、来源触达任务/日志、项目金额、标签和主流程。 |
| 27 | 资产立项域 | MARKETING_ASSET_PROJECT_URGENT_APPLY | 审批中的独立加急申请，保存发起节点快照、审批状态和并发幂等键。 |

本设计共包含 27 张营销模块自有表。待认领池和已认领池均为组合查询结果，不单独建设客户池实体表。表清单中的24至27为后续评审新增；为保持已评审编号稳定，未重排原1至23的序号。

## 4. 主要业务关系

```text
外部每日客户经理关系全量快照（只读）
    └─按cust_no更新→ MARKETING_CUSTOMER_INFO 当前主办权

MARKETING_LEAD_IMPORT_BATCH
    └─包含→ MARKETING_LEAD_IMPORT_DETAIL
             └─校验通过生成→ MARKETING_LEAD_INFO
                                  └─审批通过新增/更新→ MARKETING_CUSTOMER_INFO
                                  ├─指定范围→ MARKETING_LEAD_MANAGER_SCOPE
                                  ├─标签快照→ MARKETING_LEAD_TAG_REL
                                  └─认领→ MARKETING_CUSTOMER_CLAIM

MARKETING_CUSTOMER_INFO
    ├─正式标签→ MARKETING_CUSTOMER_TAG_REL
    ├─转交→ MARKETING_CUSTOMER_TRANSFER_LOG / TARGET
    ├─业绩关系→ MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT
    └─触达任务→ MARKETING_TOUCH_TASK
                   └─工作日志→ MARKETING_TOUCH_WORKLOG
                                  ├─图片→ MARKETING_TOUCH_WORKLOG_PICTURE
                                  └─参与人→ MARKETING_TOUCH_WORKLOG_PARTICIPANT

MARKETING_CUSTOMER_TAG_IMPORT_BATCH（一批次一标签）
    └─逐客户→ MARKETING_CUSTOMER_TAG_IMPORT_DETAIL
                    ├─新客户或资料变更→ MARKETING_LEAD_INFO
                    ├─审批通过→ MARKETING_CUSTOMER_INFO（新增/受控更新）
                    └─批次满足生效条件→ MARKETING_CUSTOMER_TAG_REL

MARKETING_CUSTOMER_INFO
    └─资产立项→ MARKETING_ASSET_PROJECT_APPLY
                    ├─可选来源任务→ MARKETING_TOUCH_TASK
                    ├─可选来源日志→ MARKETING_TOUCH_WORKLOG
                    └─中途加急→ MARKETING_ASSET_PROJECT_URGENT_APPLY
```

## 5. 详细数据字典

### 5.1 MARKETING_CUSTOMER_INFO

所属领域：客户域
表含义：营销客户唯一主档，一企一档；也是营销客户总列表的数据来源。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 自增主键 |
| cust_no | VARCHAR(64) | 否 | 客户号；未开户或尚未取得客户号时为空，非空时唯一 |
| cust_name | VARCHAR(200) | 是 | 企业名称；允许管理员受控修改 |
| unified_credit_code | CHAR(18) | 是 | 统一社会信用代码；标准化后一企一档唯一键 |
| legal_representative | VARCHAR(100) | 否 | 法定代表人 |
| registered_capital | DECIMAL(18,2) | 否 | 注册资本，单位元 |
| registered_address | VARCHAR(500) | 否 | 企业注册地址 |
| business_address | VARCHAR(500) | 否 | 实际经营地址 |
| business_scope | VARCHAR(2000) | 否 | 经营范围 |
| contact_person | VARCHAR(100) | 否 | 企业联系人 |
| contact_mobile | VARCHAR(50) | 否 | 联系电话 |
| industry | VARCHAR(50) | 否 | 行业代码 |
| group_type | VARCHAR(50) | 否 | 集团类型 |
| group_name | VARCHAR(200) | 否 | 所属集团 |
| customer_type | VARCHAR(50) | 否 | 客户类型 |
| enterprise_type | VARCHAR(50) | 否 | 企业类型/企业性质 |
| is_keystone | TINYINT | 是 | 是否重点客户：0否、1是 |
| customer_desc | VARCHAR(2000) | 否 | 客户描述 |
| is_account_opened | TINYINT | 否 | 开户状态：1已开户、0未开户、NULL未知 |
| credit_amount | DECIMAL(18,2) | 否 | 授信金额，单位元 |
| credit_exposure_amount | DECIMAL(18,2) | 否 | 授信敞口金额，单位元 |
| touch_restricted | TINYINT | 是 | 是否执行触达限制：0否、1是 |
| current_lead_id | BIGINT UNSIGNED | 否 | 最近一次审批通过并更新该主档的线索ID；不作为线索版本链 |
| last_touch_time | DATETIME | 否 | 最近一次有效触达时间的列表快照 |
| main_manager_id | VARCHAR(32) | 否 | 当前主办客户经理工号 |
| main_org_id | VARCHAR(50) | 否 | 当前主办机构；根据客户经理工号解析 |
| ownership_status | VARCHAR(20) | 是 | 主办状态：ASSIGNED/UNASSIGNED/UNKNOWN/CONFLICT |
| ownership_source | VARCHAR(20) | 是 | 主办来源：DAILY_SYNC/MANUAL/LEAD/LOCAL/OPEN_ACCOUNT |
| ownership_maintain_mode | VARCHAR(10) | 是 | 维护模式：AUTO自动同步、MANUAL人工维护 |
| ownership_data_date | DATE | 否 | 最近自动同步采用的上游数据日期 |
| ownership_updated_by | VARCHAR(32) | 否 | 主办权最近更新人；自动任务填写SYSTEM |
| ownership_updated_time | DATETIME | 否 | 主办权最近更新时间 |
| ownership_manual_reason | VARCHAR(500) | 否 | 转人工维护或人工调整主办权的原因 |
| profile_version | INT | 是 | 客户资料乐观版本；审批更新主档前用于检查资料是否已变化 |
| account_opened_by_emp_id | VARCHAR(32) | 否 | 完成开户的客户经理工号；历史开户数据允许为空 |
| account_opened_time | DATETIME | 否 | 确认开户完成时间；历史开户数据允许为空 |
| opening_touch_task_id | BIGINT UNSIGNED | 否 | 完成开户的触达任务ID；历史开户数据允许为空，新系统触达开户时必须填写 |
| record_status | VARCHAR(20) | 是 | 记录状态：ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 通用乐观锁版本 |

主要约束与索引：PK(id)；UK(unified_credit_code)；UK(cust_no)，允许多个NULL；IDX(main_manager_id, record_status)；IDX(main_org_id, record_status)；IDX(ownership_maintain_mode)；IDX(cust_name)。

### 5.2 MARKETING_CUSTOMER_TAG

所属领域：客户域
表含义：正式客户标签字典，保存标签审核、启停和有效期。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| tag_name | VARCHAR(100) | 是 | 标签名称，全行唯一 |
| tag_category | VARCHAR(50) | 否 | 标签分类 |
| tag_type | VARCHAR(30) | 否 | 标签类型 |
| tag_priority | INT | 是 | 展示优先级，数值越小越靠前 |
| description | VARCHAR(500) | 否 | 标签说明 |
| status | VARCHAR(20) | 是 | 启停状态：ENABLED/DISABLED |
| approval_status | VARCHAR(20) | 是 | 审核状态：PENDING/APPROVED/REJECTED |
| expires_at | DATE | 否 | 失效日期 |
| owner_org_id | VARCHAR(50) | 是 | 标签创建机构 |
| reviewed_by | VARCHAR(32) | 否 | 审核人工号 |
| reviewed_time | DATETIME | 否 | 审核时间 |
| reject_reason | VARCHAR(500) | 否 | 退回原因 |
| record_status | VARCHAR(20) | 是 | 逻辑状态：ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(tag_name)；IDX(approval_status, status)。

### 5.3 MARKETING_CUSTOMER_TAG_REL

所属领域：客户域
表含义：客户与正式标签的关系，保存当前有效关系及失效时间。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| tag_id | BIGINT UNSIGNED | 是 | 正式标签ID |
| active | TINYINT | 是 | 当前是否有效：0否、1是 |
| source_type | VARCHAR(30) | 是 | 来源：LEAD/IMPORT/MANUAL/NAME_LIST |
| source_ref_id | VARCHAR(64) | 否 | 来源线索、批次或名单记录ID |
| effective_time | DATETIME | 是 | 生效时间 |
| expired_time | DATETIME | 否 | 失效时间 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |

主要约束与索引：PK(id)；UK(cust_id, tag_id)；IDX(tag_id, active)。

### 5.4 MARKETING_CUSTOMER_CLAIM

所属领域：客户域
表含义：按线索记录客户认领及取消关系；同一客户可因不同营销线索产生多次认领。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| source_lead_id | BIGINT UNSIGNED | 是 | 来源线索ID；认领业务的防重维度 |
| org_id | VARCHAR(50) | 是 | 认领时机构快照 |
| claimed_by | VARCHAR(32) | 是 | 认领人工号 |
| maintainer_emp_id | VARCHAR(32) | 是 | 当前维护人工号 |
| claim_status | VARCHAR(20) | 是 | 认领状态：CLAIMED/CANCELLED |
| claim_time | DATETIME | 是 | 认领时间 |
| cancel_time | DATETIME | 否 | 取消时间 |
| cancel_reason | VARCHAR(500) | 否 | 取消原因 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(source_lead_id, claimed_by)：同一线索同一员工不得重复认领；IDX(cust_id, claim_status)；IDX(claimed_by, claim_status)。

### 5.5 MARKETING_CUSTOMER_TRANSFER_LOG

所属领域：客户域
表含义：管理员或业务流程变更主办/维护关系的转交主记录。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| transfer_no | VARCHAR(64) | 是 | 转交编号，唯一 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| claim_id | BIGINT UNSIGNED | 否 | 原认领关系ID |
| transfer_action | VARCHAR(20) | 是 | 主办操作：ASSIGN首次指定、TRANSFER转交、UNASSIGN取消主办 |
| from_manager_id | VARCHAR(32) | 否 | 原主办或维护人工号 |
| from_org_id | VARCHAR(50) | 否 | 原机构 |
| primary_to_manager_id | VARCHAR(32) | 否 | 新主办人工号；UNASSIGN时必须为空 |
| primary_to_org_id | VARCHAR(50) | 否 | 新主办机构；UNASSIGN时必须为空 |
| account_opened_snapshot | TINYINT | 否 | 转交时开户状态快照 |
| transfer_source | VARCHAR(30) | 是 | 来源：ADMIN/LEAD/WORKFLOW/OPEN_ACCOUNT |
| reason | VARCHAR(500) | 是 | 转交原因 |
| status | VARCHAR(20) | 是 | 状态：CREATED/COMPLETED/FAILED |
| operator_emp_id | VARCHAR(32) | 是 | 操作人工号 |
| completed_time | DATETIME | 否 | 完成时间 |
| failure_reason | VARCHAR(500) | 否 | 失败原因 |
| created_time | DATETIME | 是 | 创建时间 |
| updated_time | DATETIME | 是 | 更新时间 |

主要约束与索引：PK(id)；UK(transfer_no)；IDX(cust_id, created_time)；ASSIGN/TRANSFER必须有且仅有一名PRIMARY接收人，UNASSIGN时两个主接收人字段必须为空且不生成MARKETING_CUSTOMER_TRANSFER_TARGET。明确取消主办时，客户主档写ownership_status=UNASSIGNED、ownership_source=MANUAL、ownership_maintain_mode=MANUAL，防止次日自动同步直接覆盖。

### 5.6 MARKETING_CUSTOMER_TRANSFER_TARGET

所属领域：客户域
表含义：转交接收人明细，支持一名主办、多名协办。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| transfer_id | BIGINT UNSIGNED | 是 | 转交主记录ID |
| target_emp_id | VARCHAR(32) | 是 | 接收人工号 |
| target_org_id | VARCHAR(50) | 是 | 接收人机构快照 |
| target_role | VARCHAR(20) | 是 | 接收角色：PRIMARY/COLLABORATOR |
| sort_no | INT | 是 | 接收人顺序 |
| created_by | VARCHAR(32) | 是 | 创建人工号 |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；UK(transfer_id, target_emp_id)；同一transfer_id只能有一条PRIMARY。

### 5.7 MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT

所属领域：客户域
表含义：客户业绩相关人和机构的查询快照，服务跨机构营销校验；不直接授予客户权限，也不是绩效计算结果表。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| subject_type | VARCHAR(20) | 是 | 主体类型：EMP/ORG |
| subject_id | VARCHAR(50) | 是 | 员工工号或机构代码 |
| related_emp_id | VARCHAR(32) | 否 | 相关员工工号 |
| related_org_id | VARCHAR(50) | 否 | 相关机构代码 |
| relation_type | VARCHAR(30) | 是 | 业绩关系类型 |
| ratio | DECIMAL(8,4) | 否 | 业绩比例 |
| effective_date | DATE | 否 | 生效日期 |
| expiry_date | DATE | 否 | 失效日期 |
| source_system | VARCHAR(32) | 是 | 来源系统 |
| source_batch_id | VARCHAR(64) | 否 | 来源批次标识 |
| refreshed_at | DATETIME | 是 | 快照刷新时间 |
| refresh_status | VARCHAR(20) | 是 | 刷新状态 |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；IDX(cust_id, subject_type, subject_id)；IDX(source_batch_id)。

### 5.8 MARKETING_CUSTOMER_OPEN_CONFIRM_APPLY

所属领域：客户域
表含义：在缺少直接开户状态回传时，由客户经理发起、公司部审批的人工确认开户单据；可关联实际完成开户的触达任务。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| apply_no | VARCHAR(64) | 是 | 申请编号，唯一 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| source_lead_id | BIGINT UNSIGNED | 否 | 来源线索ID |
| source_touch_task_id | BIGINT UNSIGNED | 否 | 完成开户的触达任务ID；非触达来源或历史申请允许为空 |
| applicant_emp_id | VARCHAR(32) | 是 | 申请人工号 |
| applicant_org_id | VARCHAR(50) | 是 | 申请机构 |
| proposed_cust_no | VARCHAR(64) | 是 | 拟确认客户号 |
| proposed_manager_id | VARCHAR(32) | 否 | 拟确认主办客户经理 |
| proposed_org_id | VARCHAR(50) | 否 | 拟确认主办机构 |
| account_open_date | DATE | 否 | 开户日期 |
| apply_reason | VARCHAR(500) | 是 | 申请说明 |
| status | VARCHAR(20) | 是 | DRAFT/IN_APPROVAL/APPROVED/REJECTED |
| business_key | VARCHAR(100) | 否 | 流程业务键 |
| process_instance_id | VARCHAR(64) | 否 | 流程实例ID |
| reviewed_by | VARCHAR(32) | 否 | 审批人工号 |
| reviewed_time | DATETIME | 否 | 审批时间 |
| reject_reason | VARCHAR(500) | 否 | 驳回原因 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(apply_no)；UK(business_key)；UK(process_instance_id)。

### 5.9 MARKETING_LEAD_INFO

所属领域：线索域
表含义：一次独立营销线索的完整提交快照、分配方式和审批结果；不保存版本链。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 线索主键 |
| lead_no | VARCHAR(64) | 是 | 线索编号，唯一 |
| cust_id | BIGINT UNSIGNED | 否 | 匹配的营销客户ID；新客户审批前为空，审批通过后回填 |
| lead_type | VARCHAR(30) | 是 | NEW_ACCOUNT新客户开户线索/EXISTING_MARKETING存量客户营销线索 |
| customer_match_status | VARCHAR(40) | 是 | NEW_CUSTOMER/MATCHED_EXISTING_UNOPENED/MATCHED_EXISTING_OPENED |
| base_customer_profile_version | INT | 否 | 反显存量客户资料时的profile_version，用于审批更新冲突检查 |
| cust_no_snapshot | VARCHAR(64) | 否 | 提交时客户号快照 |
| cust_name | VARCHAR(200) | 是 | 录入人确认后的企业名称快照 |
| unified_credit_code | CHAR(18) | 是 | 统一社会信用代码 |
| legal_representative | VARCHAR(100) | 否 | 法定代表人快照 |
| registered_capital | DECIMAL(18,2) | 否 | 注册资本快照，单位元 |
| registered_address | VARCHAR(500) | 否 | 注册地址快照 |
| business_address | VARCHAR(500) | 否 | 经营地址快照 |
| business_scope | VARCHAR(2000) | 否 | 经营范围快照 |
| contact_person | VARCHAR(100) | 否 | 联系人快照 |
| contact_mobile | VARCHAR(50) | 否 | 联系电话快照 |
| industry | VARCHAR(50) | 否 | 行业快照 |
| group_type | VARCHAR(50) | 否 | 集团类型快照 |
| group_name | VARCHAR(200) | 否 | 集团名称快照 |
| customer_type | VARCHAR(50) | 否 | 客户类型快照 |
| enterprise_type | VARCHAR(50) | 否 | 企业类型快照 |
| is_keystone | TINYINT | 是 | 是否重点客户快照 |
| is_account_opened_snapshot | TINYINT | 否 | 提交时开户状态快照 |
| touch_restricted | TINYINT | 是 | 本次确认的触达限制标志 |
| customer_desc | VARCHAR(2000) | 否 | 客户描述快照 |
| credit_amount | DECIMAL(18,2) | 否 | 授信金额快照，单位元 |
| credit_exposure_amount | DECIMAL(18,2) | 否 | 授信敞口快照，单位元 |
| lead_source | VARCHAR(20) | 是 | 录入来源：MANUAL手工录入、LEAD_IMPORT线索批量导入、TAG_IMPORT标签客户导入 |
| distribution_mode | VARCHAR(20) | 是 | 分配方式：PUBLIC/SCOPE/OWNER |
| pool_status | VARCHAR(20) | 是 | 线索池状态：NOT_READY/AVAILABLE/CLAIMED/CLOSED |
| main_manager_id_snapshot | VARCHAR(32) | 否 | 提交时主办客户经理快照 |
| main_org_id_snapshot | VARCHAR(50) | 否 | 提交时主办机构快照 |
| entry_emp_id | VARCHAR(32) | 是 | 原录入人工号；重复线索提示使用 |
| entry_org_id | VARCHAR(50) | 是 | 原录入机构 |
| entry_time | DATETIME | 是 | 原录入时间 |
| lead_status | VARCHAR(20) | 是 | DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED/CANCELLED |
| active_dedup_key | CHAR(18) | 否 | 在途线索去重键；在途状态取统一码，终态置NULL |
| submitted_by | VARCHAR(32) | 否 | 提交人工号 |
| submitted_time | DATETIME | 否 | 提交时间 |
| business_key | VARCHAR(100) | 否 | 流程业务键 |
| process_instance_id | VARCHAR(64) | 否 | 流程实例ID |
| import_batch_id | BIGINT UNSIGNED | 否 | 线索批量导入批次ID；仅LEAD_IMPORT来源填写 |
| batch_row_no | INT | 否 | Excel原始行号 |
| tag_import_detail_id | BIGINT UNSIGNED | 否 | 标签客户导入明细ID；仅TAG_IMPORT来源填写 |
| reviewed_by | VARCHAR(32) | 否 | 最终审批人工号 |
| reviewed_time | DATETIME | 否 | 最终审批时间 |
| reject_reason | VARCHAR(500) | 否 | 驳回原因 |
| remark | VARCHAR(1000) | 否 | 线索备注 |
| record_status | VARCHAR(20) | 是 | 逻辑状态：ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(lead_no)；UK(active_dedup_key)，多个NULL可并存；UK(business_key)；UK(process_instance_id)；UK(tag_import_detail_id)，允许多个NULL；IDX(unified_credit_code, lead_status)；IDX(entry_emp_id, lead_source, entry_time)；IDX(lead_status, distribution_mode, pool_status)。线索录入历史页直接按id分页，以entry_emp_id=当前人且lead_source=MANUAL过滤，不按cust_id或统一社会信用代码聚合。

### 5.10 MARKETING_LEAD_MANAGER_SCOPE

所属领域：线索域
表含义：保存SCOPE指定客户经理范围或OWNER专属接收人。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| lead_id | BIGINT UNSIGNED | 是 | 线索ID |
| manager_emp_id | VARCHAR(32) | 是 | 指定客户经理工号 |
| manager_org_id | VARCHAR(50) | 是 | 指定人机构快照 |
| assignment_type | VARCHAR(20) | 是 | SCOPE/OWNER |
| is_primary | TINYINT | 是 | 是否主接收人 |
| created_by | VARCHAR(32) | 是 | 创建人 |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；UK(lead_id, manager_emp_id)；OWNER模式必须且只能有一条主接收人。

### 5.11 MARKETING_LEAD_TAG_REL

所属领域：线索域
表含义：保存线索提交时的标签名称快照，不受标签后续改名或失效影响。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| lead_id | BIGINT UNSIGNED | 是 | 线索ID |
| tag_id | BIGINT UNSIGNED | 是 | 正式标签ID |
| tag_name_snapshot | VARCHAR(100) | 是 | 标签名称快照 |
| tag_source | VARCHAR(30) | 是 | MANUAL/NAME_LIST/IMPORT |
| created_by | VARCHAR(32) | 是 | 创建人 |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；UK(lead_id, tag_id)。

### 5.12 MARKETING_LEAD_IMPORT_BATCH

所属领域：线索域
表含义：一次Excel线索导入批次头，记录本地暂存文件、校验统计、人工确认和各线索审批汇总；批次本身不作为整批审批单。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| batch_no | VARCHAR(64) | 是 | 导入批次编号，唯一 |
| source_file_name | VARCHAR(255) | 是 | 原始文件名 |
| source_file_id | VARCHAR(64) | 否 | 导入文件存储键；当前为 local: 前缀的本地键，兼容历史平台文件对象ID |
| file_checksum | VARCHAR(128) | 否 | 文件摘要，用于重复文件提示 |
| total_count | INT | 是 | 总行数 |
| valid_count | INT | 是 | 校验通过行数 |
| warning_count | INT | 是 | 需导入人确认的告警行数，如文件主办人与客户主档冲突 |
| rejected_count | INT | 是 | 业务拒绝行数 |
| error_count | INT | 是 | 字段或企业身份异常行数 |
| generated_lead_count | INT | 是 | 实际生成正式线索数 |
| import_status | VARCHAR(30) | 是 | IMPORTING/WAITING_CONFIRM/COMPLETED/ALL_FAILED/ABANDONED；仅表示导入处理结果 |
| approval_summary_status | VARCHAR(30) | 是 | NOT_SUBMITTED/IN_APPROVAL/PARTIAL_FINISHED/ALL_APPROVED/HAS_REJECTED；由关联线索汇总 |
| error_file_id | VARCHAR(64) | 否 | 错误明细存储键；当前为 local: 前缀的本地键，兼容历史平台文件对象ID |
| confirm_action | VARCHAR(30) | 否 | WAITING_CONFIRM批次的选择：PROCESS_VALID仅处理正常行、ABANDON_REIMPORT放弃后修改文件重新导入 |
| confirmed_by | VARCHAR(32) | 否 | 导入确认人；必须是原导入人或受权管理员 |
| confirmed_time | DATETIME | 否 | 导入确认时间 |
| confirm_remark | VARCHAR(500) | 否 | 确认说明 |
| import_emp_id | VARCHAR(32) | 是 | 导入人工号 |
| import_org_id | VARCHAR(50) | 是 | 导入机构 |
| import_time | DATETIME | 是 | 导入时间 |
| record_status | VARCHAR(20) | 是 | 逻辑状态 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 导入确认操作的乐观锁版本 |

主要约束与索引：PK(id)；UK(batch_no)；IDX(import_emp_id, import_time)；IDX(import_status, import_time)。当前原始文件和错误明细保存到应用节点本地目录，表中保存 `local:` 存储键；历史平台文件对象ID仍按FileApi读取。用户放弃并重传时保留原批次为ABANDONED，新文件必须创建新批次。

### 5.13 MARKETING_LEAD_IMPORT_DETAIL

所属领域：线索域
表含义：保存导入文件的全部行，包括成功、拒绝和异常行，用于导入历史详情及问题前排展示。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| batch_id | BIGINT UNSIGNED | 是 | 导入批次ID |
| row_no | INT | 是 | Excel原始行号 |
| cust_no | VARCHAR(64) | 否 | 文件中的客户号 |
| cust_name | VARCHAR(200) | 否 | 文件中的企业名称 |
| unified_credit_code | CHAR(18) | 否 | 文件中的统一社会信用代码 |
| contact_person | VARCHAR(100) | 否 | 文件中的联系人 |
| contact_mobile | VARCHAR(50) | 否 | 文件中的联系方式 |
| registered_address | VARCHAR(500) | 否 | 文件中的注册地址 |
| business_address | VARCHAR(500) | 否 | 文件中的经营地址 |
| industry | VARCHAR(50) | 否 | 文件中的行业 |
| customer_type | VARCHAR(50) | 否 | 文件中的客户类型 |
| credit_amount | DECIMAL(18,2) | 否 | 文件中的授信金额 |
| credit_exposure_amount | DECIMAL(18,2) | 否 | 文件中的敞口金额 |
| raw_row_json | TEXT | 否 | 原始行完整字段快照，便于模板扩展和审计 |
| customer_match_status | VARCHAR(40) | 否 | 新客户、存量未开户、存量已开户等匹配结果 |
| requested_manager_id | VARCHAR(32) | 否 | 导入文件携带的拟主办客户经理工号；仅用于校验和路由，不直接覆盖客户主档 |
| requested_manager_org_id | VARCHAR(50) | 否 | 文件主办人的机构解析快照 |
| validation_status | VARCHAR(20) | 是 | VALID/WARNING/REJECTED/ERROR |
| warning_code | VARCHAR(50) | 否 | 待确认告警编码，如OWNER_CONFLICT |
| warning_message | VARCHAR(500) | 否 | 待确认原因 |
| error_code | VARCHAR(50) | 否 | 错误编码，如PENDING_LEAD_DUPLICATE |
| error_message | VARCHAR(500) | 否 | 错误说明 |
| matched_customer_id | BIGINT UNSIGNED | 否 | 命中的客户主档ID |
| matched_lead_id | BIGINT UNSIGNED | 否 | 命中的在途线索ID |
| matched_entry_emp_id | VARCHAR(32) | 否 | 命中在途线索的原录入人工号 |
| matched_entry_org_id | VARCHAR(50) | 否 | 命中在途线索的原录入机构 |
| matched_entry_time | DATETIME | 否 | 命中在途线索的原录入时间 |
| handling_status | VARCHAR(20) | 是 | PENDING/GENERATED/SKIPPED；区分尚未处理、已生成线索和确认后跳过 |
| generated_lead_id | BIGINT UNSIGNED | 否 | 校验通过后生成的正式线索ID |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；UK(batch_id, row_no)；IDX(batch_id, validation_status)；IDX(unified_credit_code)；默认排序REJECTED→ERROR→WARNING→VALID，同状态按row_no升序。WAITING_CONFIRM期间不生成任何线索；选择PROCESS_VALID后重新校验VALID行并生成独立MARKETING_LEAD_INFO，WARNING/REJECTED/ERROR行统一置SKIPPED。

### 5.14 MARKETING_TOUCH_TASK

所属领域：触达域
表含义：触达任务，保存来源、执行人、机构、任务状态和SLA。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| task_no | VARCHAR(64) | 是 | 任务编号，唯一 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| source_type | VARCHAR(30) | 是 | LEAD/CLAIM/TRANSFER/CROSS_ORG/MANUAL |
| source_biz_id | BIGINT UNSIGNED | 否 | 来源业务记录ID |
| org_id | VARCHAR(50) | 是 | 任务所属机构 |
| assignee_emp_id | VARCHAR(32) | 是 | 任务执行人工号 |
| task_type | VARCHAR(20) | 是 | FIRST_TOUCH/FOLLOW_UP |
| task_status | VARCHAR(20) | 是 | PENDING/IN_PROGRESS/SUCCESS/CANCELLED |
| plan_finish_time | DATETIME | 否 | 计划完成时间 |
| warning_time | DATETIME | 否 | 预警时间 |
| sla_status | VARCHAR(20) | 是 | BLUE/YELLOW/RED |
| success_time | DATETIME | 否 | 完成时间 |
| cancel_time | DATETIME | 否 | 取消时间 |
| cancel_reason | VARCHAR(500) | 否 | 取消原因 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(task_no)；IDX(cust_id, task_status)；IDX(assignee_emp_id, task_status)；IDX(org_id, task_status)。

### 5.15 MARKETING_TOUCH_WORKLOG

所属领域：触达域
表含义：触达工作日志事实表，由原xa_touch_custom_worklogs规范化改名；新产生的数据支持一条任务多条日志，历史迁移数据没有任务信息时task_id允许为空。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| worklog_no | VARCHAR(64) | 是 | 工作日志编号，唯一 |
| legacy_worklog_id | VARCHAR(150) | 否 | 迁移前原日志ID |
| task_id | BIGINT UNSIGNED | 否 | 触达任务ID；历史迁移数据无任务信息时允许为空，系统上线后新产生的日志必须关联任务 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| customer_name_snapshot | VARCHAR(200) | 是 | 触达时企业名称快照 |
| unified_credit_code_snapshot | CHAR(18) | 是 | 触达时统一社会信用代码快照 |
| customer_tag_snapshot | VARCHAR(1000) | 否 | 触达时标签快照 |
| operator_emp_id | VARCHAR(32) | 是 | 日志记录人工号 |
| operator_org_id | VARCHAR(50) | 是 | 记录时机构快照 |
| touch_time | DATETIME | 是 | 实际触达时间 |
| touch_method | VARCHAR(20) | 是 | PHONE/ONSITE/WECHAT/OTHER |
| touch_points | VARCHAR(1000) | 否 | 触达要点 |
| touch_result | VARCHAR(1000) | 否 | 触达结果 |
| is_first_touch | TINYINT | 是 | 是否首次触达 |
| account_open_progress | VARCHAR(20) | 否 | 开户进度 |
| location_status | VARCHAR(20) | 否 | 定位打卡状态 |
| location_time | DATETIME | 否 | 定位时间 |
| location_address | VARCHAR(500) | 否 | 定位地址 |
| location_city_area | VARCHAR(100) | 否 | 市/区/县 |
| location_remark | VARCHAR(500) | 否 | 定位备注 |
| client_uuid | VARCHAR(64) | 否 | 客户端幂等号 |
| record_status | VARCHAR(20) | 是 | VALID/VOID |
| void_by | VARCHAR(32) | 否 | 作废人 |
| void_time | DATETIME | 否 | 作废时间 |
| void_reason | VARCHAR(500) | 否 | 作废原因 |
| created_time | DATETIME | 是 | 创建时间 |
| updated_time | DATETIME | 是 | 更新时间 |

主要约束与索引：PK(id)；UK(worklog_no)；UK(legacy_worklog_id)；UK(task_id, client_uuid)，兼容task_id为NULL的历史数据；新产生的日志由业务服务强制校验task_id非空；IDX(cust_id, touch_time)；IDX(operator_org_id, touch_time)。

### 5.16 MARKETING_TOUCH_WORKLOG_PICTURE

所属领域：触达域
表含义：触达日志图片，一张图片一行；由原xa_touch_custom_worklogs_picture_record规范化改名。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| worklog_id | BIGINT UNSIGNED | 是 | 工作日志ID |
| picture_type | VARCHAR(30) | 是 | KEY_PERSON/COMPANY_SIGN/BUSINESS_SITE |
| file_object_id | VARCHAR(64) | 是 | 平台文件对象ID |
| sort_no | TINYINT | 是 | 同类图片序号1～3 |
| created_by | VARCHAR(32) | 是 | 上传人 |
| created_time | DATETIME | 是 | 上传时间 |
| record_status | VARCHAR(20) | 是 | ACTIVE/INACTIVE |

主要约束与索引：PK(id)；UK(worklog_id, picture_type, sort_no)；每类图片最多3张，每次日志至少1张图片。

### 5.17 MARKETING_TOUCH_WORKLOG_PARTICIPANT

所属领域：触达域
表含义：触达参与人明细，替代历史逗号分隔协同人员字段。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| worklog_id | BIGINT UNSIGNED | 是 | 工作日志ID |
| participant_emp_id | VARCHAR(32) | 是 | 参与人工号 |
| participant_org_id | VARCHAR(50) | 是 | 参与人机构快照 |
| participant_role | VARCHAR(20) | 是 | OPERATOR/COLLABORATOR/SUPPORT |
| created_by | VARCHAR(32) | 是 | 创建人 |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；UK(worklog_id, participant_emp_id)。

### 5.18 MARKETING_TOUCH_TEAM

所属领域：触达域
表含义：触达团队主数据。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| team_name | VARCHAR(200) | 是 | 团队名称 |
| owner_org_id | VARCHAR(50) | 是 | 所属机构 |
| remark | VARCHAR(500) | 否 | 备注 |
| record_status | VARCHAR(20) | 是 | ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；IDX(owner_org_id, record_status)。

### 5.19 MARKETING_TOUCH_TEAM_MEMBER

所属领域：触达域
表含义：触达团队成员明细。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| team_id | BIGINT UNSIGNED | 是 | 团队ID |
| member_emp_id | VARCHAR(32) | 是 | 成员工号 |
| member_name_snapshot | VARCHAR(100) | 是 | 成员姓名快照 |
| member_org_id | VARCHAR(50) | 是 | 成员机构快照 |
| record_status | VARCHAR(20) | 是 | ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |

主要约束与索引：PK(id)；UK(team_id, member_emp_id)。

### 5.20 MARKETING_TOUCH_CUSTOMER_TAG_SOURCE

所属领域：触达域
表含义：预导入企业—标签名单来源；通过loaded_flag区分是否已载入正式客户标签关系，不替代MARKETING_CUSTOMER_TAG_REL。由原xa_touch_name_list_record改名。

> 本表继续用于历史名单兼容和简单预载入，不承载新的标签客户导入审批流程。新导入、追加、全量替换及逐客户审批使用MARKETING_CUSTOMER_TAG_IMPORT_BATCH/DETAIL。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| cust_name | VARCHAR(200) | 是 | 企业名称 |
| unified_credit_code | CHAR(18) | 是 | 统一社会信用代码 |
| registered_address | VARCHAR(500) | 否 | 企业地址 |
| contact_person | VARCHAR(100) | 否 | 联系人 |
| contact_mobile | VARCHAR(50) | 否 | 联系方式 |
| tag_name | VARCHAR(100) | 是 | 标签名称 |
| source_batch_no | VARCHAR(64) | 否 | 导入批次 |
| remark | VARCHAR(600) | 否 | 备注 |
| loaded_flag | TINYINT | 是 | 是否已载入正式客户标签关系：0未载入、1已载入；默认0 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| loaded_rel_id | BIGINT UNSIGNED | 否 | 成功载入后写入或命中的 MARKETING_CUSTOMER_TAG_REL.id |
| loaded_time | DATETIME | 否 | 成功载入正式客户标签关系的时间 |

主要约束与索引：PK(id)；UK(unified_credit_code, tag_name)；IDX(loaded_flag, source_batch_no)；程序只处理loaded_flag=0的数据；正式载入成功时在同一事务内写入或恢复MARKETING_CUSTOMER_TAG_REL，并将loaded_flag置为1、回填loaded_rel_id和loaded_time；正式关系写source_type=NAME_LIST、source_ref_id=本表id。

### 5.21 MARKETING_TOUCH_LIMIT_RULE

所属领域：触达域
表含义：按正式客户标签配置触达周期和次数限制。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| tag_id | BIGINT UNSIGNED | 是 | 正式客户标签ID |
| cycle_type | VARCHAR(20) | 是 | DAY/WEEK/MONTH/QUARTER/YEAR |
| max_touch_count | INT | 是 | 周期内最多触达次数 |
| enabled | TINYINT | 是 | 是否启用 |
| effective_date | DATE | 是 | 生效日期 |
| expiry_date | DATE | 否 | 失效日期 |
| remark | VARCHAR(500) | 否 | 规则说明 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(tag_id)。

### 5.22 MARKETING_CROSS_ORG_RULE

所属领域：跨机构营销域
表含义：跨机构营销申请的可配置校验规则。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| rule_code | VARCHAR(64) | 是 | 规则编码，唯一 |
| rule_name | VARCHAR(128) | 是 | 规则名称 |
| enabled | TINYINT | 是 | 是否启用 |
| data_source | VARCHAR(64) | 是 | 校验数据来源 |
| failure_message | VARCHAR(256) | 是 | 校验失败提示 |
| extension_params | VARCHAR(1000) | 否 | 扩展配置JSON |
| sort_no | INT | 是 | 执行顺序 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(rule_code)。

### 5.23 MARKETING_CROSS_ORG_APPLY

所属领域：跨机构营销域
表含义：跨机构营销申请、四项校验快照、审批结果及生成触达任务。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| apply_no | VARCHAR(64) | 是 | 申请编号，唯一 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID |
| applicant_emp_id | VARCHAR(32) | 是 | 申请人工号 |
| applicant_org_id | VARCHAR(50) | 是 | 申请机构 |
| main_manager_id_snapshot | VARCHAR(32) | 否 | 申请时主办人快照 |
| main_org_id_snapshot | VARCHAR(50) | 否 | 申请时主办机构快照 |
| applicant_not_main_check | TINYINT | 是 | 申请人不是主办客户经理的校验结果 |
| main_org_different_check | TINYINT | 是 | 申请机构与主办机构不同的校验结果 |
| applicant_no_performance_check | TINYINT | 是 | 申请人无该客户业绩归属的校验结果 |
| applicant_org_no_performance_check | TINYINT | 是 | 申请机构无该客户业绩归属的校验结果 |
| check_snapshot_time | DATETIME | 是 | 校验快照时间 |
| apply_reason | VARCHAR(500) | 是 | 申请理由 |
| status | VARCHAR(20) | 是 | DRAFT/IN_APPROVAL/APPROVED/REJECTED |
| generated_touch_task_id | BIGINT UNSIGNED | 否 | 审批通过后生成的触达任务ID |
| business_key | VARCHAR(100) | 否 | 流程业务键 |
| process_instance_id | VARCHAR(64) | 否 | 流程实例ID |
| reviewed_by | VARCHAR(32) | 否 | 审批人工号 |
| reviewed_time | DATETIME | 否 | 审批时间 |
| reject_reason | VARCHAR(500) | 否 | 驳回原因 |
| created_by | VARCHAR(32) | 是 | 创建人工号；系统任务可填写 SYSTEM |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号；系统任务可填写 SYSTEM |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(apply_no)；UK(business_key)；UK(process_instance_id)；IDX(cust_id, status)。

### 5.24 MARKETING_CUSTOMER_TAG_IMPORT_BATCH

所属领域：客户域
表含义：单个标签的追加或全量替换导入批次，保存OBS文件、统计、标签审批前置、客户审批汇总和正式关系生效状态。一个批次只能对应一个标签。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| batch_no | VARCHAR(64) | 是 | 标签客户导入批次号，唯一 |
| tag_id | BIGINT UNSIGNED | 是 | 目标标签ID；新标签先创建PENDING记录再建批次 |
| tag_name_snapshot | VARCHAR(100) | 是 | 导入时标签名称快照 |
| import_mode | VARCHAR(20) | 是 | APPEND追加、REPLACE全量替换 |
| source_file_name | VARCHAR(255) | 是 | 原始文件名 |
| source_file_id | VARCHAR(64) | 是 | 平台文件对象ID，实际文件存放在OBS |
| file_checksum | VARCHAR(128) | 否 | 文件摘要，用于重复文件提示 |
| total_count | INT | 是 | 文件总行数 |
| valid_count | INT | 是 | 校验通过数 |
| error_count | INT | 是 | 校验失败数 |
| pending_approval_count | INT | 是 | 待客户审批数 |
| approved_count | INT | 是 | 客户审批通过数 |
| rejected_count | INT | 是 | 客户审批拒绝数 |
| loaded_count | INT | 是 | 已生效正式标签关系数 |
| tag_approval_required | TINYINT | 是 | 是否需要先审批新标签：0否、1是 |
| customer_approval_status | VARCHAR(30) | 是 | NOT_SUBMITTED/IN_APPROVAL/PARTIAL_FINISHED/ALL_APPROVED/HAS_REJECTED |
| status | VARCHAR(30) | 是 | IMPORTING/VALIDATED/IN_APPROVAL/READY_TO_LOAD/REPLACE_BLOCKED/COMPLETED/CANCELLED |
| replace_block_reason | VARCHAR(500) | 否 | REPLACE批次因客户被拒绝或并发变化无法生效时的原因 |
| import_emp_id | VARCHAR(32) | 是 | 导入人工号 |
| import_org_id | VARCHAR(50) | 是 | 导入机构 |
| import_time | DATETIME | 是 | 导入时间 |
| completed_time | DATETIME | 否 | 正式关系生效完成时间 |
| record_status | VARCHAR(20) | 是 | 逻辑状态：ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号 |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号 |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(batch_no)；IDX(tag_id, status)；IDX(import_emp_id, import_time)。应用层对tag_id加共享锁后检查，同一标签同一时刻只允许一个未结束REPLACE批次。REPLACE只有在标签已审批、全部有效客户均审批通过时才进入READY_TO_LOAD；任一客户被拒绝则进入REPLACE_BLOCKED，不修改已有正式关系。

### 5.25 MARKETING_CUSTOMER_TAG_IMPORT_DETAIL

所属领域：客户域
表含义：标签导入逐客户待审明细，保存原始行快照、客户匹配、校验、客户审批、线索关联和正式标签关系载入结果。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| batch_id | BIGINT UNSIGNED | 是 | 标签导入批次ID |
| row_no | INT | 是 | Excel原始行号 |
| cust_name | VARCHAR(200) | 是 | 企业名称快照 |
| unified_credit_code | CHAR(18) | 是 | 标准化统一社会信用代码 |
| contact_person | VARCHAR(100) | 否 | 联系人快照 |
| contact_mobile | VARCHAR(50) | 否 | 联系方式快照 |
| registered_address | VARCHAR(500) | 否 | 注册地址快照 |
| business_address | VARCHAR(500) | 否 | 经营地址快照 |
| raw_row_json | TEXT | 否 | 原始行完整字段快照 |
| customer_change_type | VARCHAR(30) | 是 | NEW_CUSTOMER/EXISTING_NO_CHANGE/EXISTING_UPDATE |
| matched_customer_id | BIGINT UNSIGNED | 否 | 命中的营销客户ID |
| generated_lead_id | BIGINT UNSIGNED | 否 | 新客户或存量资料变更时生成的MARKETING_LEAD_INFO.id |
| validation_status | VARCHAR(20) | 是 | VALID/REJECTED/ERROR |
| error_code | VARCHAR(50) | 否 | 校验错误编码 |
| error_message | VARCHAR(500) | 否 | 校验失败原因 |
| approval_status | VARCHAR(20) | 是 | NOT_REQUIRED/PENDING/APPROVED/REJECTED |
| reviewed_by | VARCHAR(32) | 否 | 客户审批人工号 |
| reviewed_time | DATETIME | 否 | 客户审批时间 |
| reject_reason | VARCHAR(500) | 否 | 客户审批拒绝原因 |
| loaded_flag | TINYINT | 是 | 是否已写入或恢复正式标签关系：0否、1是 |
| loaded_rel_id | BIGINT UNSIGNED | 否 | 生效的MARKETING_CUSTOMER_TAG_REL.id |
| loaded_time | DATETIME | 否 | 正式标签关系生效时间 |
| created_time | DATETIME | 是 | 创建时间 |

主要约束与索引：PK(id)；UK(batch_id, row_no)；UK(generated_lead_id)，允许多个NULL；IDX(batch_id, approval_status)；IDX(unified_credit_code)。新客户或存量资料变更通过generated_lead_id关联独立线索审批；存量资料无变化时仅审批标签客户关系，不重复更新客户主档。正式关系写source_type=IMPORT、source_ref_id=本表id。

### 5.26 MARKETING_ASSET_PROJECT_APPLY

所属领域：资产立项域
表含义：资产立项主申请与台账。客户以 `cust_id` 逻辑关联 `MARKETING_CUSTOMER_INFO.id`，不重复保存客户全量字段。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| apply_no | VARCHAR(64) | 是 | 资产立项申请编号，唯一 |
| legacy_apply_id | VARCHAR(64) | 否 | 迁移前LOAN_APPLY字符串ID，迁移数据唯一；新数据为空 |
| cust_id | BIGINT UNSIGNED | 是 | 营销客户ID，关联MARKETING_CUSTOMER_INFO.id |
| source_touch_task_id | BIGINT UNSIGNED | 否 | 发起立项的来源触达任务ID |
| source_worklog_id | BIGINT UNSIGNED | 否 | 精确来源工作日志ID；必须属于source_touch_task_id和cust_id |
| project_name | VARCHAR(200) | 提交时 | 项目名称；草稿可空 |
| project_type | VARCHAR(50) | 提交时 | 项目类型字典值；草稿可空 |
| biz_type | VARCHAR(50) | 提交时 | 业务类型字典值；草稿可空 |
| guarantee_type | VARCHAR(50) | 提交时 | 主要担保方式；草稿可空 |
| project_total_investment | DECIMAL(18,2) | 提交时 | 项目总投资额，单位人民币元；草稿可空 |
| project_loan_amount | DECIMAL(18,2) | 提交时 | 项目贷款金额，单位人民币元；草稿可空 |
| credit_amount | DECIMAL(18,2) | 提交时 | 申请授信金额；草稿可空 |
| credit_exposure_amount | DECIMAL(18,2) | 提交时 | 申请授信敞口金额；草稿可空 |
| is_urgent | TINYINT | 是 | 0否、1是；中途加急仅审批通过后回写1 |
| is_key_project | TINYINT | 是 | 是否重点项目：0否、1是 |
| urgent_source | VARCHAR(20) | 否 | INITIATION启动时、MID_PROCESS中途；未加急时为NULL |
| applicant_emp_id | VARCHAR(32) | 是 | 发起人工号快照 |
| applicant_org_id | VARCHAR(50) | 是 | 发起机构快照 |
| main_manager_id_snapshot | VARCHAR(32) | 否 | 立项时客户主办人快照 |
| main_org_id_snapshot | VARCHAR(50) | 否 | 立项时客户主办机构快照 |
| status | VARCHAR(20) | 是 | DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED |
| business_key | VARCHAR(100) | 否 | 主流程业务键，提交后唯一 |
| process_instance_id | VARCHAR(64) | 否 | 主流程实例ID |
| submitted_time | DATETIME | 否 | 提交时间 |
| completed_time | DATETIME | 否 | 最终完成时间 |
| record_status | VARCHAR(20) | 是 | ACTIVE/DELETED；仅草稿允许逻辑删除 |
| created_by | VARCHAR(32) | 是 | 创建人工号 |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号 |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(apply_no)；UK(legacy_apply_id)，允许多个NULL；UK(business_key)；UK(process_instance_id)；IDX(cust_id, status, record_status)；IDX(applicant_emp_id, status, created_time)；IDX(applicant_org_id, status, created_time)；IDX(source_touch_task_id)；IDX(source_worklog_id)。数据库不建跨域物理外键，Service必须校验客户、任务、日志的归属关系及当前数据范围。

### 5.27 MARKETING_ASSET_PROJECT_URGENT_APPLY

所属领域：资产立项域
表含义：主申请已进入审批后的独立中途加急申请。启动时加急直接作为主流程变量，不写本表。

| 字段名 | 建议类型 | 必填 | 字段含义 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED AUTO_INCREMENT | 是 | 主键 |
| urgent_apply_no | VARCHAR(64) | 是 | 中途加急申请编号，唯一 |
| legacy_urgent_apply_id | VARCHAR(64) | 否 | 迁移前LOAN_URGENT_REQUEST字符串ID，迁移数据唯一；新数据为空 |
| asset_project_apply_id | BIGINT UNSIGNED | 是 | 关联MARKETING_ASSET_PROJECT_APPLY.id |
| cust_id | BIGINT UNSIGNED | 是 | 客户ID冗余，仅用于数据范围和审计，必须与主申请一致 |
| requested_at_node_key | VARCHAR(64) | 是 | 发起时主流程节点key快照 |
| requested_at_task_id | VARCHAR(64) | 是 | 发起时主流程当前任务ID |
| apply_reason | VARCHAR(1000) | 是 | 加急原因 |
| status | VARCHAR(20) | 是 | DRAFT/IN_APPROVAL/APPROVED/REJECTED/CANCELLED |
| active_dedup_key | VARCHAR(80) | 否 | 在途时写ASSET_PROJECT:{主申请ID}，终态置NULL |
| business_key | VARCHAR(100) | 否 | 独立加急流程业务键 |
| process_instance_id | VARCHAR(64) | 否 | 独立加急流程实例ID |
| requested_by | VARCHAR(32) | 是 | 申请人工号 |
| requested_org_id | VARCHAR(50) | 是 | 申请机构 |
| requested_time | DATETIME | 是 | 申请时间 |
| reviewed_by | VARCHAR(32) | 否 | 最终审批人工号 |
| reviewed_time | DATETIME | 否 | 最终审批时间 |
| approval_comment | VARCHAR(1000) | 否 | 最终审批意见 |
| record_status | VARCHAR(20) | 是 | ACTIVE/INACTIVE |
| created_by | VARCHAR(32) | 是 | 创建人工号 |
| created_time | DATETIME | 是 | 创建时间 |
| updated_by | VARCHAR(32) | 是 | 最后修改人工号 |
| updated_time | DATETIME | 是 | 最后修改时间 |
| lock_version | INT | 是 | 乐观锁版本 |

主要约束与索引：PK(id)；UK(urgent_apply_no)；UK(legacy_urgent_apply_id)，允许多个NULL；UK(active_dedup_key)；UK(business_key)；UK(process_instance_id)；IDX(asset_project_apply_id, status)；IDX(cust_id, requested_time)。创建前必须锁定主申请并重查真实流程节点；审批通过时在同一本地事务内将主申请更新为 `is_urgent=1, urgent_source=MID_PROCESS`。

## 6. 关键业务规则

### 6.1 线索重复录入与导入

- 标准化统一社会信用代码后，先检查MARKETING_LEAD_INFO中是否存在DRAFT、SUBMITTED、IN_APPROVAL状态的同码线索。

- 命中时拒绝本次新增或该条导入明细，提示原线索编号、状态、原录入人、所属机构和录入时间；同一操作者也不重复创建，应返回继续处理原线索。

- REJECTED、APPROVED、CANCELLED为终态，不占用active_dedup_key；已驳回允许重新录入，已审批历史不阻止后续存量客户营销。

- 应用层预检查后仍必须依赖UK(active_dedup_key)处理并发，重复键统一转换为PENDING_LEAD_DUPLICATE。

- 批量导入按行处理：拒绝/异常行只进入MARKETING_LEAD_IMPORT_DETAIL，不因一条失败拒绝整个文件。如果不存在WARNING，正常行可在完成二次校验后生成独立线索；存在WARNING时整个批次先进入WAITING_CONFIRM，未确认前不生成线索。

- 导入人选择PROCESS_VALID后，后端只对VALID行重新校验并生成MARKETING_LEAD_INFO；选择ABANDON_REIMPORT时保留原批次和OBS文件证据，修改文件后创建新批次。导入成功仅表示线索生成，不等于线索审批通过。

### 6.1.1 线索录入历史页粒度

- 第一Tab每条MARKETING_LEAD_INFO手工录入记录展示一行，后端固定过滤entry_emp_id=当前登录人且lead_source=MANUAL，不做cust_id、客户号或统一社会信用代码聚合。

- 同一企业在旧线索已进入终态后可以因新营销事项产生多条历史记录；详情只展示当前该条线索快照、审批信息及与当前客户主档的差异，不默认聚合企业全部历史线索。

- 第二Tab以MARKETING_LEAD_IMPORT_BATCH.id为一行，点击详情后按batch_id展示全部MARKETING_LEAD_IMPORT_DETAIL，失败行排在前面。

### 6.2 客户主档更新

- 新客户线索审批通过后新增MARKETING_CUSTOMER_INFO并回填MARKETING_LEAD_INFO.cust_id。

- 存量客户线索审批通过后按cust_id更新企业名称、联系人、地址、行业、企业类型、授信金额、敞口金额等可维护资料。

- 线索不得覆盖客户ID、客户号、统一社会信用代码、开户状态、主办客户经理、主办机构、主办来源和维护模式。

- 审批更新前比较base_customer_profile_version和profile_version；不一致时停止自动覆盖，要求审批人重新确认。

- 营销管理员在客户总列表中的受权直接维护可以即时更新主档，并写平台AUDIT_LOG。

### 6.3 待认领与已认领客户池

| 场景 | 页面/去向 | 处理规则 |
| --- | --- | --- |
| 未审批线索 | 线索工作台/审批列表 | 不可认领 |
| APPROVED + PUBLIC + AVAILABLE，且无有效主办 | 公共待认领池 | 当前员工未认领时可认领 |
| APPROVED + SCOPE | 指定人员已认领客户池 | 非指定人员不可认领 |
| APPROVED + OWNER | 主办人已认领客户池 | 直接归主办人 |
| 存量已开户且有有效主办 | 主办人已认领客户池 | 允许营销，但不进入公共池 |
| 存量已开户但无主办或主办冲突 | 管理员异常处理 | 默认不开放公共认领 |

认领查询和认领提交必须重新读取当前客户主办权，防止每日同步或人工维护在页面打开后改变主办关系。触达任务标记完成本身不等于开户完成；只有取得客户号或人工确认开户审批通过后，才在同一业务事务中：更新客户开户状态并将主办权移交给完成开户的客户经理；将该客户其他PENDING/IN_PROGRESS触达任务置为CANCELLED，取消其他人员的有效认领关系，并关闭该客户仍为AVAILABLE的公共池线索。此后再次录入该企业且不存在未结束线索时，页面必须提示当前主办客户经理及所属机构；线索按EXISTING_MARKETING处理并强制distribution_mode=OWNER，审批通过后直接进入主办人的已认领客户池。录入人不是主办人时，可选择继续录入并推送主办人、发起跨机构营销申请或取消录入，不得改为PUBLIC或SCOPE。

### 6.4 每日主办权同步

- 上游关系表是每日全量快照，客户营销模块只读，至少提供cust_no、manager_emp_id和可识别的刷新完成标志/数据日期。

- 只处理MARKETING_CUSTOMER_INFO中cust_no非空、record_status=ACTIVE、ownership_maintain_mode=AUTO的现有客户。

- 唯一有效匹配时更新main_manager_id、main_org_id、ownership_status、ownership_source、ownership_data_date、ownership_updated_by/time。

- 全量快照无匹配时可将AUTO客户更新为UNASSIGNED；多主办、无效员工或机构解析失败时标记CONFLICT/UNKNOWN并通过任务日志告警，不自动建客户。

- MANUAL客户每日同步跳过；管理员点击恢复自动同步时必须立即按最新全量快照校验并刷新。

### 6.5 营销客户列表与我的客户

- 营销客户列表的详情和条件查询以MARKETING_CUSTOMER_INFO为唯一主数据源。营销管理员修改可维护资料时使用profile_version/lock_version乐观校验并记录前后值审计。

- 人工取消主办后，该客户对全行客户经理可见；可见不等于可修改、可认领或自动进入公共待认领池。

- 我的客户固定按main_manager_id=当前登录人、ownership_status=ASSIGNED查询。客户经理只能转给另一名有效客户经理，不能转为无主办；转交后进入MANUAL维护模式，只有营销管理员可恢复AUTO。

### 6.6 线索审批页

- 待审批列表以MARKETING_LEAD_INFO.id为业务粒度，通过WorkflowApi只返回当前审批人有权办理的全部待办；审批记录通过WorkflowApi按当前登录人过滤历史任务。

- 手工和批量导入线索均逐线索审批；MARKETING_LEAD_IMPORT_BATCH只保存导入结果和审批汇总，不启动一个覆盖全部有效行的整批审批流程。

### 6.7 标签客户导入与审批

- 一个MARKETING_CUSTOMER_TAG_IMPORT_BATCH只对应一个标签。新标签先创建MARKETING_CUSTOMER_TAG.approval_status=PENDING；已有标签直接关联原tag_id。

- 所有校验通过的导入客户均产生客户审批事项。NEW_CUSTOMER和EXISTING_UPDATE通过MARKETING_LEAD_INFO保存完整快照并在审批通过后新增/更新主档；EXISTING_NO_CHANGE只审批标签关系，不重复改写客户主档。

- 审批客户时如标签仍为PENDING，后端必须返回TAG_APPROVAL_REQUIRED；前端弹窗确认后重新提交原选中客户ID并带approveTag=true，后端先完成标签审批，再幂等处理原选中客户。

- APPEND客户审批通过后可逐条写入或恢复MARKETING_CUSTOMER_TAG_REL。REPLACE必须等待标签已审批且批次全部有效客户通过，再以单个本地事务差异化失效旧关系并激活本批次关系。任一客户被拒绝时整个REPLACE进入REPLACE_BLOCKED，正式关系不变。

### 6.8 资产立项

- 资产立项客户只能来自 `MARKETING_CUSTOMER_INFO` 的有效企业客户；提交时必须重查客户状态、当前主办人/机构和申请人数据范围，页面选中值不能代替服务端校验。
- `source_touch_task_id` 可空；传入时必须关联同一 `cust_id` 的 `MARKETING_TOUCH_TASK`。`source_worklog_id` 可空；传入时必须同时属于该任务和客户的 `MARKETING_TOUCH_WORKLOG`。
- 资产立项上的“加急/重点项目”是申请级标签，不写入 `MARKETING_CUSTOMER_TAG` 或 `MARKETING_CUSTOMER_TAG_REL`，避免把项目属性污染成客户长期标签。
- 启动时加急在主申请上写 `is_urgent=1, urgent_source=INITIATION`，并由主流程进入加急分支；不产生中途加急表记录。
- 中途加急必须写 `MARKETING_ASSET_PROJECT_URGENT_APPLY`。审批中主申请仍为普通；只有加急审批通过后才回写主表标签。
- 附件继续使用 `FILE_OBJECT/BIZ_FILE_REL`，流程实例和审批历史继续使用 `BIZ_PROCESS_MAP` 与 Workflow API；不在资产立项域复制平台附件表或 Flowable `ACT_*` 表。

## 7. 外部及平台共享表

| 表或数据源 | 归属 | 与营销模块关系 |
| --- | --- | --- |
| 外部客户经理关系全量快照表 | 外部只读 | 每日提供cust_no和manager_emp_id，用于刷新AUTO客户主办权 |
| SYS_JOB_CONF及治理中心任务日志 | 平台共享 | 配置每日同步任务、保存执行日志和告警 |
| BIZ_PROCESS_MAP | 平台共享 | 营销业务记录与工作流实例映射 |
| Flowable ACT_RU_* / ACT_HI_* | 平台共享 | 当前待办及完整审批历史；营销模块通过Workflow API访问 |
| FILE_OBJECT / BIZ_FILE_REL | 平台共享 | 线索导入文件、附件和触达图片文件对象 |
| AUDIT_LOG | 平台共享 | 管理员修改客户资料、主办权和高风险操作的前后值审计 |
| LOAN_APPLY / LOAN_URGENT_REQUEST | 迁移前资产立项实现 | 仅作为历史迁移来源；新模型使用MARKETING_ASSET_PROJECT_APPLY/URGENT_APPLY |
| CUST_MASTER | M98存量域 | 存量/绩效查询用途，不作为营销客户主档 |

## 8. 明确不建设或已删除的设计

- MARKETING_CCRM_CUSTOMER_SOURCE：已确认不接入CCRM。

- MARKETING_CUSTOMER_OWNER_SYNC_RUN：同步执行信息使用治理中心任务日志。

- MARKETING_CUSTOMER_OWNER_HISTORY：当前范围只保存主档当前态，人工转交写转交记录。

- MARKETING_CUSTOMER_OWNER_SYNC_ERROR：异常通过任务日志/告警并由营销管理员处理。

- MARKETING_TOUCH_TASK_WORKLOG_REL：工作日志直接保存task_id。

- 任何MARKETING_*_POOL实体表：待认领和已认领客户池均通过线索、客户主档、指定范围和认领关系组合查询。

- 线索prev_lead_id、version_no、is_latest、lead_op等版本字段：每次营销事项作为独立线索，不建设版本链。

## 9. 历史触达表改名映射

| 历史表名 | 目标表名 |
| --- | --- |
| xa_touch_custom_worklogs | MARKETING_TOUCH_WORKLOG |
| xa_touch_custom_worklogs_picture_record | MARKETING_TOUCH_WORKLOG_PICTURE |
| xa_touch_team_manage | MARKETING_TOUCH_TEAM |
| xa_touch_team_member_manage | MARKETING_TOUCH_TEAM_MEMBER |
| xa_touch_name_list_record | MARKETING_TOUCH_CUSTOMER_TAG_SOURCE |
| TOUCH_TASK | MARKETING_TOUCH_TASK |
| CUST_TOUCH_LIMIT_RULE | MARKETING_TOUCH_LIMIT_RULE |
| LOAN_APPLY | MARKETING_ASSET_PROJECT_APPLY |
| LOAN_URGENT_REQUEST | MARKETING_ASSET_PROJECT_URGENT_APPLY |

## 10. 实施前评审事项

- 确认目标数据库对生成列/可空唯一索引的兼容性；如不支持生成列，由应用事务维护active_dedup_key。

- 线索批量导入已确定为按行校验、在有WARNING时先由原导入人确认，最终生成的线索均逐条审批；实施前需校验新状态与历史批次状态的兼容映射。

- 确认目标库对“同一标签仅一个在途REPLACE批次”的约束实现方式；如不使用可空唯一键，应通过LockManager共享锁和事务内再查保证。

- 确认外部全量关系快照的刷新完成标志、数据日期、同一客户号唯一主办保证和员工机构解析接口。

- 确认已开户但上游无主办关系时默认禁止公共认领的安全口径。

- 资产立项迁移前必须确认 `LOAN_APPLY` 字符串ID到 `MARKETING_ASSET_PROJECT_APPLY` BIGINT ID的映射表/回填策略，同步修改实体、Mapper、测试数据、跨模块API、工作流businessKey和附件bizId，不能只改表名。

- 确认旧xa_touch_*数据迁移、ID映射、附件文件对象映射和停机/双写切换方案；历史工作日志不强制补造触达任务，task_id可为空。

- 由DBA根据目标库实际字符集、数据量、查询计划和分片规则最终确定索引。
