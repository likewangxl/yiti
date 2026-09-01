# 业务申请中心 — 表结构 DDL

> **当前运行边界（2026-08-28）**：本模块当前只持有 `SUPPORT_REQUEST`。本文 `LOAN_APPLY` 相关结构
> 仅为历史归档，不是当前数据模型，也不是可执行 DDL。资产立项现行模型见
> `docs/modules/customer-marketing-center/05-表结构DDL.md`。

> 模块: business-application-center
> 版本: V1.0
> 更新日期: 2026-04-10
> 编码: UTF-8
> 数据库: MySQL 8.0 + InnoDB + utf8mb4

---

## 1. 表清单

| 序号 | 表名 | 说明 | 主键策略 | 预估数据量 | 写入频度 | 读取频度 |
|----|-----|-----|--------|----------|--------|--------|
| 1 | `SUPPORT_REQUEST` | 中台支持申请表 | UUID(32) | 20万/年 | 中 | 高 |
| 2 | `SUPPORT_PROCESS_LOG` | 中台支持办理过程记录（本次新增，待 DBA 实施） | UUID(32) | 100万/年 | 高 | 高 |
| — | `LOAN_APPLY` | 资产投放申请表（历史归档） | UUID(32) | — | — | — |

**重要说明：**

1. V1 版本中**未单独定义** `loan_apply_attachment` 和 `support_dispatch_log` 表：
   - **附件关联**：统一通过 `system-governance-center` 模块的 `BIZ_FILE_REL` 表进行关联，通过 `biz_type='LOAN'/'SUPPORT'` + `biz_id` 匹配；
   - **派单历史**：通过 `SUPPORT_REQUEST` 表的 `dispatch_emp_id`、`dispatch_time`、`assigned_emp_id` 字段记录当前态，历史态通过 `AUDIT_LOG`（governance 模块）保留；
   - **审批历史**：通过 Flowable 内置的 `ACT_HI_TASKINST` / `ACT_HI_VARINST` / `ACT_HI_ACTINST` 表记录，由 `workflow-center` 提供查询 API。
2. `SUPPORT_PROCESS_LOG` 是本次中台支持办理过程记录的新增业务表，由外层 [中台支持_数据库变更.sql](/home/djdev/jxe/中台支持_数据库变更.sql) 交 DBA 评审实施；当前代理未执行该脚本。申请主表的流程态仍由 `workflow-center` 管理，照片/附件对象仍由 `system-governance-center` 管理。
3. 所有表统一遵守 `docs/common-dev-guide.md` 的通用字段规范：`created_by`、`created_time`、`updated_by`、`updated_time`、`deleted`。

---

## 2. 完整 DDL

### 2.1 loan_apply — 资产投放申请表

```sql
-- ==========================================================================
-- 表名: loan_apply
-- 说明: 资产投放申请主表，承载授信/贷款/押品等投放类业务的申请信息
-- 创建: 2026-04-10
-- ==========================================================================
DROP TABLE IF EXISTS `LOAN_APPLY`;
CREATE TABLE `LOAN_APPLY` (
  `id`                        VARCHAR(32)    NOT NULL COMMENT '申请ID(UUID)',
  `apply_no`                  VARCHAR(100)   DEFAULT NULL COMMENT '申请编号(LA+yyyyMMdd+6位序号)',
  `cust_id`                   VARCHAR(32)    NOT NULL COMMENT '客户ID,逻辑外键→cust_master.id',
  `source_touch_task_id`      VARCHAR(32)    DEFAULT NULL COMMENT '来源触达任务ID,逻辑外键→touch_task.id',
  `project_type`              VARCHAR(32)    DEFAULT NULL COMMENT '项目类型(字典PROJECT_TYPE)',
  `biz_type`                  VARCHAR(32)    DEFAULT NULL COMMENT '业务类型(字典BIZ_TYPE)',
  `guarantee_type`            VARCHAR(32)    DEFAULT NULL COMMENT '担保方式(字典GUARANTEE_TYPE)',
  `credit_amount`             DECIMAL(20,4)  DEFAULT NULL COMMENT '授信金额(元,保留4位小数)',
  `credit_exposure_amount`    DECIMAL(20,4)  DEFAULT NULL COMMENT '敞口金额(元,保留4位小数)',
  `status`                    VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED',
  `business_key`              VARCHAR(100)   DEFAULT NULL COMMENT '流程业务键,固定格式LOAN:{id}',
  `process_instance_id`       VARCHAR(64)    DEFAULT NULL COMMENT '流程实例ID,对应ACT_RU_EXECUTION/ACT_HI_PROCINST',
  `owner_org_id`              VARCHAR(50)    NOT NULL COMMENT '归属机构(ORG_CODE),用于数据范围过滤',
  `created_by`                VARCHAR(32)    NOT NULL COMMENT '创建人工号(PT_USER.emp_id)',
  `created_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`                VARCHAR(32)    DEFAULT NULL COMMENT '更新人工号',
  `updated_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0=未删,1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_process_inst` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资产投放申请表';
```

### 2.2 support_request — 中台支持申请表

```sql
-- ==========================================================================
-- 表名: support_request
-- 说明: 中台支持申请表,承载客户经理向中台部门发起的支持需求
--      支持多产品拆单(同一提交批次按产品拆为多条记录,共享submit_group_id)
--      支持双视图: SUPPORT(发起侧)与SUPPORT_DEPT(承接侧)
-- 创建: 2026-04-10
-- ==========================================================================
DROP TABLE IF EXISTS `SUPPORT_REQUEST`;
CREATE TABLE `SUPPORT_REQUEST` (
  `id`                        VARCHAR(32)    NOT NULL COMMENT '申请ID(UUID)',
  `request_no`                VARCHAR(100)   DEFAULT NULL COMMENT '申请编号(SR+yyyyMMdd+6位序号)',
  `submit_group_id`           VARCHAR(64)    DEFAULT NULL COMMENT '同批提交分组ID(多产品拆单时同组共享)',
  `cust_id`                   VARCHAR(32)    NOT NULL COMMENT '客户ID,逻辑外键→cust_master.id',
  `source_touch_task_id`      VARCHAR(32)    DEFAULT NULL COMMENT '来源触达任务ID,逻辑外键→touch_task.id',
  `product_id`                VARCHAR(64)    DEFAULT NULL COMMENT '产品ID,逻辑外键→product_info.id',
  `support_dept_id`           VARCHAR(50)    DEFAULT NULL COMMENT '承接部门ORG_CODE,逻辑外键→EXT_ORG_INFO.org_code',
  `other_demand`              TEXT           DEFAULT NULL COMMENT '其他需求/补充说明',
  `dispatch_emp_id`           VARCHAR(32)    DEFAULT NULL COMMENT '派单人工号(部门秘书,仅场景B)',
  `dispatch_time`             DATETIME       DEFAULT NULL COMMENT '派单时间',
  `assigned_emp_id`           VARCHAR(32)    DEFAULT NULL COMMENT '承接办理人工号(场景A=产品负责人变量,场景B=秘书派单)',
  `status`                    VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED',
  `business_key`              VARCHAR(100)   DEFAULT NULL COMMENT '流程业务键,固定格式SUPPORT:{id}',
  `process_instance_id`       VARCHAR(64)    DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id`              VARCHAR(50)    NOT NULL COMMENT '归属机构(发起侧ORG_CODE)',
  `created_by`                VARCHAR(32)    NOT NULL COMMENT '创建人工号(发起人)',
  `created_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`                VARCHAR(32)    DEFAULT NULL COMMENT '更新人工号',
  `updated_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_support_dept` (`support_dept_id`),
  KEY `idx_assigned_emp` (`assigned_emp_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_submit_group` (`submit_group_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='中台支持申请表';
```

### 2.3 support_process_log — 中台支持办理过程记录（新增目标结构）

该表按申请追加办理过程/结果记录，服务端通过 `client_uuid` 吸收移动端网络重试；照片和附件只在 `BIZ_FILE_REL` 绑定，不在本表复制 URL。下列字段与当前 `SupportProcessLog` 实体一致，最终结构由外层数据库变更脚本实施后再以目标库为准。

| 字段 | 类型 | 必填/默认 | 说明 |
|---|---|---|---|
| `id` | `VARCHAR(32)` | 必填，主键 | 记录 ID |
| `support_request_id` | `VARCHAR(32)` | 必填 | 逻辑关联 `SUPPORT_REQUEST.id` |
| `client_uuid` | `VARCHAR(64)` | 必填 | 同一申请内的客户端幂等键 |
| `log_type` | `VARCHAR(20)` | 必填，`PROCESS` | `PROCESS` 过程记录或 `RESULT` 结果记录 |
| `content` | `TEXT` | 必填 | 办理内容/结果说明 |
| `checkin_time` | `DATETIME` | 可空 | 现场打卡时间 |
| `longitude` / `latitude` | `DECIMAL(12,8)` | 可空 | 定位经纬度 |
| `location_address` | `VARCHAR(255)` | 可空 | 定位地址快照 |
| `created_by` | `VARCHAR(32)` | 必填 | 记录人工号 |
| `created_time` | `DATETIME` | `CURRENT_TIMESTAMP` | 记录时间 |
| `deleted` | `TINYINT(1)` | `0` | 逻辑删除标记；过程记录接口只读未删除记录 |

唯一约束 `uk_request_client(support_request_id, client_uuid)`；列表按 `idx_spl_request_time(support_request_id, created_time)` 倒序查询，审计/运营按 `idx_spl_creator_time(created_by, created_time)` 查询。该表不设置跨模块物理外键。

---

## 3. 索引说明

### 3.1 loan_apply

| 索引名 | 字段 | 类型 | 用途 | 命中场景 |
|-------|------|-----|-----|---------|
| PRIMARY | id | 主键 | 主键检索 | `getById(id)` / 详情查询 |
| uk_apply_no | apply_no | 唯一 | 业务编号唯一 | 按编号查询、防重复生成 |
| idx_cust_id | cust_id | 普通 | 客户维度查询 | 客户详情页的申请列表 |
| idx_owner_org | owner_org_id | 普通 | 数据范围过滤 | `DATA_SCOPE=ORG` 查询 |
| idx_created_by | created_by | 普通 | 我的申请 | "我发起的"列表 |
| idx_status | status | 普通 | 按状态筛选 | 草稿箱/审批中列表 |
| idx_business_key | business_key | 普通 | 流程回查 | 流程结束事件回写 |
| idx_created_time | created_time | 普通 | 时间范围排序 | 分页排序、月度报表 |
| idx_process_inst | process_instance_id | 普通 | 流程实例反查 | 通过流程回查业务单 |

**典型SQL与命中索引：**
```sql
-- 我的草稿箱: 命中 idx_created_by + idx_status
SELECT * FROM loan_apply
 WHERE created_by = ? AND status = 'DRAFT' AND deleted = 0
 ORDER BY created_time DESC LIMIT 20;

-- 机构数据范围列表: 命中 idx_owner_org
SELECT * FROM loan_apply
 WHERE owner_org_id IN (?,?,?) AND deleted = 0
 ORDER BY created_time DESC LIMIT 20;

-- 流程回写: 命中 idx_business_key
UPDATE loan_apply SET status = 'COMPLETED', updated_time = NOW()
 WHERE business_key = 'LOAN:LA202604100001' AND status = 'IN_APPROVAL';
```

### 3.2 support_request

| 索引名 | 字段 | 类型 | 用途 | 命中场景 |
|-------|------|-----|-----|---------|
| PRIMARY | id | 主键 | 主键检索 | 详情查询 |
| uk_request_no | request_no | 唯一 | 业务编号唯一 | 按编号查询 |
| idx_cust_id | cust_id | 普通 | 客户维度 | 客户详情的支持历史 |
| idx_support_dept | support_dept_id | 普通 | 部门维度 | 承接部门列表(SUPPORT_DEPT视图) |
| idx_assigned_emp | assigned_emp_id | 普通 | 个人维度 | 我的待办/处理中 |
| idx_created_by | created_by | 普通 | 发起人维度 | 我发起的支持 |
| idx_status | status | 普通 | 状态筛选 | 各状态列表 |
| idx_business_key | business_key | 普通 | 流程回写 | 流程结束回写 |
| idx_submit_group | submit_group_id | 普通 | 拆单关联 | 查询同批所有产品单 |
| idx_owner_org | owner_org_id | 普通 | 数据范围 | SUPPORT视图数据范围 |
| idx_created_time | created_time | 普通 | 时间排序 | 分页、报表 |
| idx_product | product_id | 普通 | 产品维度 | 产品统计/按产品搜索 |

**典型SQL与命中索引：**
```sql
-- SUPPORT视图-我发起的: 命中 idx_created_by + idx_status
SELECT * FROM support_request
 WHERE created_by = ? AND status IN ('DRAFT','IN_APPROVAL','IN_PROGRESS') AND deleted = 0
 ORDER BY created_time DESC LIMIT 20;

-- SUPPORT_DEPT视图-部门未派单: 命中 idx_support_dept
SELECT * FROM support_request
 WHERE support_dept_id = ? AND assigned_emp_id IS NULL AND status = 'IN_APPROVAL' AND deleted = 0
 ORDER BY created_time ASC LIMIT 20;

-- SUPPORT_DEPT视图-我承接的: 命中 idx_assigned_emp
SELECT * FROM support_request
 WHERE assigned_emp_id = ? AND status = 'IN_PROGRESS' AND deleted = 0
 ORDER BY dispatch_time DESC LIMIT 20;

-- 同批拆单回查: 命中 idx_submit_group
SELECT id, product_id, status FROM support_request
 WHERE submit_group_id = ? AND deleted = 0;
```

### 3.3 support_process_log

| 索引名 | 字段 | 类型 | 用途 | 命中场景 |
|---|---|---|---|---|
| PRIMARY | `id` | 主键 | 记录检索 | 过程记录详情 |
| `uk_request_client` | `support_request_id, client_uuid` | 唯一 | 客户端重试幂等 | 新增过程记录前查重 |
| `idx_spl_request_time` | `support_request_id, created_time` | 复合普通 | 申请过程时间线 | 详情页日志列表 |
| `idx_spl_creator_time` | `created_by, created_time` | 复合普通 | 操作人留痕查询 | 审计/运营追溯 |

### 3.4 索引设计原则

1. **避免过多索引**：每多一个索引，写入成本增加 ~5%，本表核心读多写少，索引数量可控；
2. **复合索引优先**：后续优化可按 `(owner_org_id, status, created_time)` 建复合索引提升列表性能；
3. **覆盖索引**：列表页仅展示少量字段时可建覆盖索引避免回表（V2 优化项）；
4. **逻辑删除字段**：`deleted` 不单建索引，依赖 `WHERE deleted = 0` 的 MySQL 优化器剪枝。

---

## 4. 逻辑外键

| 表 | 字段 | 关联 | 关联字段 | 说明 | 校验时机 |
|----|-----|------|--------|-----|---------|
| loan_apply | cust_id | cust_master (customer-marketing) | id | 申请客户 | 创建/更新时调用 `CustomerQueryApi.isValidCustomer()` |
| loan_apply | source_touch_task_id | touch_task (customer-marketing) | id | 来源触达任务 | 创建时调用 `TouchTaskQueryApi.getTouchTask()` |
| loan_apply | owner_org_id | EXT_ORG_INFO | org_code | 归属机构(自动从发起人继承) | 无需额外校验,由CurrentUserApi取 |
| loan_apply | created_by | PT_USER | emp_id | 发起人工号 | 由CurrentUserApi取 |
| loan_apply | process_instance_id | ACT_RU_EXECUTION / ACT_HI_PROCINST | proc_inst_id | Flowable流程实例 | 由WorkflowApi.startProcess回填 |
| loan_apply | project_type | DICT_ITEM | item_value(dict_type=PROJECT_TYPE) | 项目类型字典 | 调用DictApi.isValidDictValue |
| loan_apply | biz_type | DICT_ITEM | item_value(dict_type=BIZ_TYPE) | 业务类型字典 | 同上 |
| loan_apply | guarantee_type | DICT_ITEM | item_value(dict_type=GUARANTEE_TYPE) | 担保方式字典 | 同上 |
| support_request | cust_id | cust_master | id | 申请客户 | 同 loan_apply |
| support_request | product_id | product_info (portal-content) | id | 申请产品 | 调用 `ProductApi.getProduct()` 校验可用性 |
| support_request | support_dept_id | EXT_ORG_INFO | org_code | 承接部门 | 场景 B 用户选择；场景 A 通过产品资料解析知悉部门，不强写此列 |
| support_request | dispatch_emp_id | PT_USER | emp_id | 派单人(秘书) | 场景B派单时记录 |
| support_request | assigned_emp_id | PT_USER | emp_id | 承接办理人 | 场景A写入流程变量 `assignedEmpId`,场景B由秘书派单 |
| support_request | source_touch_task_id | touch_task | id | 来源触达任务 | 同 loan_apply |
| support_request | owner_org_id | EXT_ORG_INFO | org_code | 发起机构 | 继承发起人 |
| support_request | created_by | PT_USER | emp_id | 发起人 | CurrentUserApi |
| support_process_log | support_request_id | SUPPORT_REQUEST | id | 过程记录所属申请 | 新增日志时锁定申请并校验可写状态 |
| support_process_log | created_by | PT_USER | emp_id | 记录人 | CurrentUserApi |

**逻辑外键的执行规则：**

1. **不使用物理外键**：MySQL 外键约束与分库分表、迁移均有兼容性问题，本项目一律使用逻辑外键；
2. **Service 层强制校验**：所有逻辑外键在 Service 层通过对应 `QueryApi` 校验，校验失败抛 `BizException`；
3. **允许历史悬挂**：如员工离职后 `assigned_emp_id` 可能指向已停用账号，允许查询返回但不允许新建绑定；
4. **级联删除**：业务表均为逻辑删除 (`deleted=1`)，不涉及物理级联。
5. **过程记录幂等**：`support_process_log` 以 `(support_request_id, client_uuid)` 唯一约束防止移动端重试重复写入；照片文件 ID 通过 `FileApi.bindFile("SUPPORT_LOG", logId, fileId, ...)` 关联。

---

## 4a. 字段来源与赋值时机表（2026-04-10 新增）

本章节明确每个字段的**数据来源**和**赋值时机**，消除"字段何时该有值、何时可变"的歧义。开发时按此表实现字段的写入和更新逻辑。

### 4a.1 loan_apply 字段溯源

| 字段 | 来源 | 赋值时机 | 可变性 | 是否可为空 | 赋值方 |
|---|---|---|---|---|---|
| `id` | 应用层生成（UUID） | 创建草稿（POST /api/loans） | 不可变 | 不可空 | `LoanService.createDraft` |
| `apply_no` | 应用层生成（`LA+yyyyMMdd+6位序号`） | 提交审批（POST /api/loans/{id}/submit）时生成 | 不可变 | 草稿期可空，提交后必填 | `LoanService.submit` |
| `cust_id` | 前端选择器（来自 `CustomerQueryApi.searchCustomers`） | 创建草稿 | 草稿期可改，提交后不可变 | 不可空 | 前端传参 → `LoanService.createDraft/updateDraft` |
| `source_touch_task_id` | 前端从"触达完成弹窗"跳转时自动带入 | 创建草稿 | 不可变 | 可空（非触达发起则为 null） | `LoanService.createDraft`（从请求参数） |
| `project_type` | 前端下拉（字典 PROJECT_TYPE） | 创建草稿 / 更新草稿 | 草稿期可改，提交后不可变 | 提交时必填 | 前端传参 → Service 校验字典 |
| `biz_type` | 同上（字典 BIZ_TYPE） | 同上 | 同上 | 提交时必填 | 同上 |
| `guarantee_type` | 同上（字典 GUARANTEE_TYPE） | 同上 | 同上 | 提交时必填 | 同上 |
| `credit_amount` | 用户输入 | 创建草稿 / 更新草稿 | 草稿期可改，提交后不可变 | 提交时必填，`> 0` | 前端传参，Service 校验 `> 0` |
| `credit_exposure_amount` | 用户输入 | 同上 | 同上 | 提交时必填，`>= 0 && <= credit_amount` | 同上 |
| `status` | Service 层状态机 | 状态转移时 | 按状态机规则可变 | 不可空（默认 `DRAFT`） | `LoanService.*` 各方法 / `WorkflowCompletedListener` |
| `business_key` | 应用层固定格式 `LOAN:{id}` | 提交审批时生成 | 不可变 | 草稿期可空，提交后必填 | `LoanService.submit`（格式：`"LOAN:" + id`） |
| `process_instance_id` | `WorkflowApi.startProcess` 返回 | 提交审批的**同一事务**中回填 | 不可变 | 草稿期可空，提交后必填 | `LoanService.submit` 的事务内 |
| `owner_org_id` | `CurrentUserApi.getCurrentUser().getOrgCode()` | 创建草稿 | **不可变**（即使用户换机构） | 不可空 | `LoanService.createDraft` 从上下文取 |
| `created_by` | `CurrentUserApi.getCurrentUser().getEmpId()` | 创建草稿 | 不可变 | 不可空 | 同上 |
| `created_time` | 数据库 `DEFAULT CURRENT_TIMESTAMP` | INSERT 时 | 不可变 | 不可空 | 数据库 |
| `updated_by` | `CurrentUserApi.getCurrentUser().getEmpId()` | 每次 UPDATE | 可变 | 首次 INSERT 时可空 | `LoanService.*` 各方法 |
| `updated_time` | 数据库 `ON UPDATE CURRENT_TIMESTAMP` | 每次 UPDATE | 可变 | 不可空 | 数据库 |
| `deleted` | 应用层逻辑删除 | 删除草稿时 | `0 → 1`（不可逆） | 不可空，默认 0 | `LoanService.deleteDraft`（仅 DRAFT 可删） |

**关键提示**：
- `cust_id` / `project_type` / `biz_type` / `guarantee_type` / `credit_amount` 等**业务字段**在 `DRAFT` 状态可改；一旦 `status = IN_APPROVAL`（即提交审批），所有业务字段**冻结**，任何修改均返回 `BIZ-40902` 终态保护
- `owner_org_id` **不随用户换机构变化**：假设用户 A 以 ORG_SZ_001 发起申请，后被调至 ORG_SH_002，该申请仍归属 ORG_SZ_001

### 4a.2 support_request 字段溯源

| 字段 | 来源 | 赋值时机 | 可变性 | 是否可为空 | 赋值方 |
|---|---|---|---|---|---|
| `sourceType` | 前端入口 | 创建请求解析 | 不落库，仅应用层字段 | 可空（由 `sourceTouchTaskId` 推断时可省略） | `CreateSupportReq`；旧 `SUPPORT_REQUEST` 无此列 |
| `id` | 应用层生成（UUID） | 创建草稿 | 不可变 | 不可空 | `SupportService.createDraft` |
| `request_no` | 应用层生成（`SR+yyyyMMdd+6位序号`） | 创建草稿时 | 不可变 | 不可空 | `SupportService.create` / `SupportProductSplitService` |
| `submit_group_id` | 应用层生成（UUID） | 创建草稿时；多产品同批共享 | 不可变 | 当前单条场景 B 也生成 | `SupportService.create` / `SupportProductSplitService` |
| `cust_id` | 前端选择器 | 创建草稿 | 草稿期可改，提交后不可变 | 不可空 | 前端传参 |
| `source_touch_task_id` | 前端从"触达完成弹窗"带入 | 创建草稿 | 不可变 | 可空 | 前端传参 |
| `product_id` | 前端产品选择器（来自 `ProductApi.listSupportAvailable`） | **创建草稿时确定**（因拆单后不可变更） | 不可变（拆单后） | 可空（场景 B 无具体产品时） | `SupportService.createDraft` / `splitAndSave` |
| `support_dept_id` | 场景 B 用户选择的承接部门；场景 A 的产品所属部门仅用于完成后的知悉人解析 | 创建草稿 | 草稿期可改，提交后不可变 | 场景 A 可空，场景 B 必填 | `SupportService.create` / `SupportWorkflowListener` |
| `other_demand` | 用户输入 | 创建草稿 / 更新草稿 | 草稿期可改，提交后不可变 | 场景 B 必填（若 product_id 为空） | 前端传参 |
| `dispatch_emp_id` | `CurrentUserApi`（派单人） | **派单时**（POST /api/support-dept/requests/{id}/dispatch） | 仅派单时赋值，不可再改 | 场景 A 永远为空 | `SupportDeptService.dispatch` |
| `dispatch_time` | `System.now()` | 派单时 | 同上 | 同上 | 同上 |
| `assigned_emp_id` | 场景 A：产品负责人；场景 B：秘书选择 | 场景 A：创建/提交时作为 `assignedEmpId` 流程变量；场景 B：派单时赋值 | 场景 B 仅通过受限 TRANSFER 接口可变 | 场景 A 提交后、场景 B 派单后必填 | `SupportProductSplitService` / `SupportService.submit` / `SupportDeptService.dispatch` / `SupportDeptService.transfer` |
| `status` | Service 状态机 | 状态转移时 | 按状态机规则可变 | 不可空，默认 `DRAFT` | `SupportService.*` / `SupportDeptService.*` / `WorkflowCompletedListener` |
| `business_key` | 应用层 `SUPPORT:{id}` | 创建草稿时 | 不可变 | 草稿即存在 | `SupportService.create` / `SupportProductSplitService` |
| `process_instance_id` | `WorkflowApi.startProcess` 返回 | 提交审批的同一事务 | 不可变 | 草稿可空 | 同上 |
| `owner_org_id` | `CurrentUserApi.getOrgCode()` | 创建草稿 | 不可变 | 不可空 | `SupportService.createDraft` |
| `created_by` | `CurrentUserApi.getEmpId()` | 创建草稿 | 不可变 | 不可空 | 同上 |
| `created_time` | 数据库 | INSERT 时 | 不可变 | 不可空 | 数据库 |
| `updated_by` | `CurrentUserApi.getEmpId()` | 每次 UPDATE | 可变 | 首次可空 | Service |
| `updated_time` | 数据库 | 每次 UPDATE | 可变 | 不可空 | 数据库 |
| `deleted` | 应用层 | 删除草稿时 | `0 → 1` | 不可空，默认 0 | `SupportService.deleteDraft` |

### 4a.2a support_process_log 字段溯源

| 字段 | 来源 | 赋值时机 | 可变性 | 是否可为空 | 赋值方 |
|---|---|---|---|---|---|
| `id` | 应用层 UUID | 新增过程记录 | 不可变 | 不可空 | `SupportProcessLogService` |
| `support_request_id` | URL 路径申请 ID | 新增过程记录 | 不可变 | 不可空 | 服务端校验 `SUPPORT_REQUEST` |
| `client_uuid` | 移动端本次提交 | 新增过程记录 | 不可变 | 不可空 | 请求体；同申请唯一 |
| `log_type` | 请求体 | 新增过程记录 | 不可变 | 不可空，默认 `PROCESS` | 服务端白名单校验 `PROCESS/RESULT` |
| `content` | 请求体 | 新增过程记录 | 不可变 | 不可空 | 承接人员 |
| `checkin_time` | 请求体现场打卡 | 新增过程记录 | 不可变 | 可空 | 承接人员 |
| `longitude` / `latitude` | 请求体定位 | 新增过程记录 | 不可变 | 可空 | 承接人员 |
| `location_address` | 请求体定位地址 | 新增过程记录 | 不可变 | 可空 | 承接人员 |
| `created_by` | `CurrentUserApi.getCurrentEmpId()` | 新增过程记录 | 不可变 | 不可空 | 服务端上下文，不信任客户端 |
| `created_time` | 数据库默认值 | INSERT | 不可变 | 不可空 | 数据库 |
| `deleted` | 应用层 | 预留逻辑删除 | `0 → 1` | 不可空，默认 0 | 服务端 |

`fileIds`/`photoFileIds` 不写入 `SUPPORT_PROCESS_LOG`，由服务端校验后通过 `FileApi` 绑定 `biz_type=SUPPORT_LOG`、`biz_id=log.id`。

**关键提示**：
- `product_id` 一旦拆单保存就**不可再改**（即使是草稿状态也不能改）。如需改，必须**删除整组草稿重建**
- `support_dept_id` 的推导逻辑：
  - 场景 A（`product_id != null`）：产品部门由 `ProductApi.getProduct(product_id).getProductDeptOrgCode()` 解析，供 ZT-04 知悉通知使用，不强写 `support_dept_id`
  - 场景 B（`product_id == null`）：用户前端选择承接部门并写入 `support_dept_id`
- `assigned_emp_id` 的 TRANSFER 是**受限变更**：只有当前承接人可在 `IN_PROGRESS` 状态下转交给本部门其他人员

### 4a.3 赋值时机 vs 状态对照表

| 操作 | 触发接口 | 写入字段 | 目标状态 |
|---|---|---|---|
| 创建草稿（LOAN） | POST /api/loans | id/cust_id/project_type/biz_type/guarantee_type/credit_amount/credit_exposure_amount/owner_org_id/created_by/created_time/status=DRAFT | `DRAFT` |
| 创建草稿（SUPPORT） | POST /api/support-requests | id/cust_id/product_id 或 other_demand/support_dept_id/owner_org_id/created_by/created_time/status=DRAFT/submit_group_id | `DRAFT` |
| 更新草稿 | 当前中台支持 REST 未提供 PUT 更新入口；需删除草稿后重建 | — | `DRAFT` |
| 提交审批 | POST /api/loans/{id}/submit 或 /api/support-requests/{id}/submit | apply_no/request_no + business_key + process_instance_id + status=IN_APPROVAL | `IN_APPROVAL` |
| 秘书派单（仅场景 B） | POST /api/support-dept/requests/{id}/dispatch | dispatch_emp_id + dispatch_time + assigned_emp_id + status=IN_PROGRESS | `IN_PROGRESS` |
| 当前承接人转交 | POST /api/support-dept/requests/{id}/transfer | assigned_emp_id（更新为新人） + updated_by/updated_time | `IN_PROGRESS`（不变） |
| 支持人员完成 | POST /api/support-dept/requests/{id}/complete | 通过 workflow 任务办理，结束监听器回写 status；失败由强制终止回写 `REJECTED` | `COMPLETED` / `REJECTED` |
| 流程通过回写（LOAN） | `WorkflowCompletedListener`（异步） | status=COMPLETED + updated_by=SYSTEM + updated_time | `COMPLETED` |
| 流程驳回回写 | 同上 | status=REJECTED + updated_by=SYSTEM + updated_time | `REJECTED` |
| 撤回 | POST /api/loans/{id}/cancel 或 /api/support-requests/{id}/cancel | status=CANCELLED + updated_by/updated_time | `CANCELLED` |
| 逻辑删除 | DELETE /api/loans/{id} 或 /api/support-requests/{id} | deleted=1 + updated_by/updated_time | 状态不变 |

---

## 5. 关键字段取值枚举

### 5.1 loan_apply.status

| 取值 | 含义 | 进入条件 | 可达状态 |
|------|------|--------|---------|
| DRAFT | 草稿 | 创建/保存草稿 | IN_APPROVAL / CANCELLED |
| IN_APPROVAL | 审批中 | 提交审批,process_instance_id已回填 | COMPLETED / REJECTED |
| COMPLETED | 已完成 | 流程正常结束 | (终态) |
| REJECTED | 已驳回 | 流程被驳回 | (终态) |
| CANCELLED | 已取消 | 用户主动撤销草稿 | (终态) |

状态机图：
```
    DRAFT ──submit──▶ IN_APPROVAL ──approve──▶ COMPLETED
      │                     │
      └──cancel──▶ CANCELLED │
                            └──reject──▶ REJECTED
```

### 5.2 support_request.status

| 取值 | 含义 | 进入条件 | 可达状态 |
|------|------|--------|---------|
| DRAFT | 草稿 | 创建/保存草稿 | IN_APPROVAL / CANCELLED |
| IN_APPROVAL | 工作流处理中（含派单环节） | 提交后启动流程 | IN_PROGRESS / COMPLETED / REJECTED |
| IN_PROGRESS | 处理中 | 秘书派单后(场景B) | COMPLETED / REJECTED |
| COMPLETED | 已完成 | 正常路径到达流程结束节点，由 `processCompletedListener` 回写 | (终态) |
| REJECTED | 已驳回 | workflow service 强制终止流程后回写 | (终态) |
| CANCELLED | 已取消 | 用户撤销草稿 | (终态) |

状态机图：
```
  DRAFT ──submit──▶ IN_APPROVAL ──dispatch──▶ IN_PROGRESS ──approve──▶ COMPLETED
    │                   │                         │
    │                   ├──approve(场景A)──▶ COMPLETED
    │                   └──reject(强制终止)──▶ REJECTED
    │                                             └──reject──▶ REJECTED
    └──cancel──▶ CANCELLED
```

**场景A与场景B的状态差异：**
- **场景A (`support_simple_v1`)**：提交后由 `assignedEmpId` 对应的产品负责人办理，正常完成由流程结束监听器从 `IN_APPROVAL` 回写 `COMPLETED`，不要求经过 `IN_PROGRESS`；
- **场景B (`support_complex_v1`)**：提交后等待秘书派单，`IN_APPROVAL` 期间 `assigned_emp_id` 为 NULL，派单后变更为 `IN_PROGRESS`。

### 5.3 字典引用

| 字段 | 字典类型 | 示例值 |
|------|---------|-------|
| loan_apply.project_type | PROJECT_TYPE | NEW_BUILDING / RENOVATION / EQUIPMENT / WORKING_CAPITAL |
| loan_apply.biz_type | BIZ_TYPE | LOAN / GUARANTEE / ACCEPTANCE / LC |
| loan_apply.guarantee_type | GUARANTEE_TYPE | MORTGAGE / PLEDGE / CREDIT / THIRD_PARTY |

字典维护在 `system-governance-center` 模块的 `dict_type` 与 `dict_item` 表中，本模块只读。

---

## 6. business_key 格式

| 业务 | 格式 | 示例 | 长度 |
|------|------|------|-----|
| 资产投放申请 | `LOAN:{id}` | `LOAN:LA202604100001abcdef...` | ≤ 100 |
| 中台支持申请 | `SUPPORT:{id}` | `SUPPORT:SR202604100001abcdef...` | ≤ 100 |

**格式设计原则：**
1. **前缀分类**：便于在 Flowable 中直接按前缀检索不同类型的流程；
2. **自带ID**：解析 business_key 即可反查业务主表，无需额外映射；
3. **稳定不变**：一旦生成不可修改，保持业务与流程的稳定映射；
4. **全局唯一**：由业务ID保证唯一，不存在冲突。

**使用场景：**
- `WorkflowApi.startProcess()` 传入；
- 流程事件消息体携带，用于反查业务表；
- 审计日志 `resource_id` 或 `business_key` 字段记录；
- 跨模块通信的统一业务标识。

---

## 7. 多产品拆单设计

### 7.1 业务场景

客户经理为同一客户向同一部门同时申请多个产品的支持（例如：同时申请"对公存款"、"结构性存款"、"资产管理计划"三个产品），需要分别审批和分别办理。

### 7.2 设计方案

**一次提交，多条记录，共享分组ID：**

```
用户提交[客户C1 + 产品P1、P2、P3]
    │
    ▼
Facade 生成 submit_group_id = UUID
    │
    ├──▶ INSERT support_request(id=SR001, submit_group_id=GROUP_001, product_id=P1)
    │        └──▶ 调用 ScenarioRouter 判定 → 启动 support_simple_v1 流程
    │
    ├──▶ INSERT support_request(id=SR002, submit_group_id=GROUP_001, product_id=P2)
    │        └──▶ 启动 support_complex_v1 流程
    │
    └──▶ INSERT support_request(id=SR003, submit_group_id=GROUP_001, product_id=P3)
             └──▶ 启动 support_simple_v1 流程
```

### 7.3 关键特性

| 特性 | 说明 |
|------|------|
| 同批标识 | 三条记录共享同一 `submit_group_id` |
| 独立流程 | 每条记录启动独立流程实例(`process_instance_id` 不同) |
| 独立状态 | 每条记录状态独立流转,互不影响 |
| 独立办理 | 每条记录的办理人可能不同(秘书可能分派给不同人) |
| 独立审批 | 节点表单、审批意见各自独立 |
| 统一展示 | 发起人"我的提交"页面可按 submit_group_id 聚合展示 |

### 7.4 事务语义

**V1 策略：整批事务，任一失败全部回滚**

```java
@Transactional(rollbackFor = Exception.class)
public BatchSubmitResp batchSubmit(BatchSubmitCmd cmd) {
    String groupId = UUID.randomUUID().toString();
    List<String> ids = new ArrayList<>();
    for (String productId : cmd.getProductIds()) {
        // 1. 校验产品
        Product product = productApi.getProduct(productId)
            .orElseThrow(() -> new BizException("产品不存在:" + productId));
        // 2. 插入记录
        SupportRequest req = build(cmd, productId, groupId);
        supportRequestMapper.insert(req);
        // 3. 场景路由 + 启动流程
        String sceneKey = scenarioRouter.route(product);
        WorkflowLaunchResp launchResp = workflowApi.startProcess(buildStartCmd(req, sceneKey));
        // 4. 回填 processInstanceId
        supportRequestMapper.updateProcessInstance(req.getId(), launchResp.getProcessInstanceId());
        ids.add(req.getId());
    }
    return new BatchSubmitResp(groupId, ids);
}
```

**策略说明：**
- 任一产品校验失败或流程启动失败，整批事务回滚；
- 确保"要么全部成功，要么全部没发起"，避免半成功状态；
- V2 可考虑异步批量模式，支持部分成功 + 重试。

---

## 8. 附件关联

本模块不单独设计附件表，统一通过 `system-governance-center` 模块的 `BIZ_FILE_REL` 表进行关联。

### 8.1 biz_file_rel 表结构（governance 模块）

```sql
-- 简化摘要,完整定义见 docs/modules/system-governance-center/05-表结构DDL.md
CREATE TABLE `BIZ_FILE_REL` (
  `id`              VARCHAR(32)  NOT NULL,
  `biz_type`        VARCHAR(32)  NOT NULL COMMENT '业务类型:LOAN/SUPPORT/...',
  `biz_id`          VARCHAR(32)  NOT NULL COMMENT '业务ID:loan_apply.id / support_request.id',
  `file_object_id`  VARCHAR(32)  NOT NULL COMMENT '文件对象ID→file_object.id',
  `file_role`       VARCHAR(32)  DEFAULT NULL COMMENT '附件角色:ID_CARD/BIZ_LICENSE/CREDIT_REPORT/...',
  `sort`            INT          DEFAULT 0,
  `created_by`      VARCHAR(32)  NOT NULL,
  `created_time`    DATETIME,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
);
```

### 8.2 使用方式

**上传并绑定：**
```java
// 1. 用户上传文件
FileObjectDTO file = fileApi.upload(multipartFile, currentUserApi.getCurrentEmpId());
// 2. 绑定到业务
fileApi.bindFile("LOAN", loanApplyId, file.getId(), "CREDIT_REPORT");
```

**查询业务所有附件：**
```java
List<FileObjectDTO> files = fileApi.listBizFiles("LOAN", loanApplyId);
```

**解绑附件：**
```java
fileApi.unbindFile("LOAN", loanApplyId, fileObjectId);
```

### 8.3 附件角色字典

| 业务类型 | 常用附件角色 |
|---------|------------|
| LOAN | ID_CARD(身份证)、BIZ_LICENSE(营业执照)、CREDIT_REPORT(征信报告)、FINANCIAL_REPORT(财报)、APPROVAL_DOC(批复件) |
| SUPPORT | REQUIREMENT_DOC(需求说明)、SUPPORT_DOC(支持材料)、RESULT_DOC(办理结果) |

附件角色属于非强制性规范，各业务可根据实际需要扩展。

---

## 9. 双视图设计（SUPPORT / SUPPORT_DEPT）

### 9.1 设计理念

同一物理表 `SUPPORT_REQUEST` 承载两种完全不同的业务视角：

| 视角 | BizType | 业务含义 | 对应用户角色 |
|------|---------|--------|-----------|
| **发起侧** | `SUPPORT` | 我向中后台发起的支持需求 | 客户经理 / 团队长 / 分行长 |
| **承接侧** | `SUPPORT_DEPT` | 中后台承接的待处理/已处理支持需求 | 部门秘书 / 产品经理 / 支持人员 |

### 9.2 数据范围差异

发起侧与承接侧的数据范围规则完全不同，通过 `BizScopeApi.resolveScope(empId, BizType)` 返回不同的 `DataScopeType`：

| BizType | 角色 | 数据范围 | SQL 条件 |
|---------|------|---------|---------|
| SUPPORT | 客户经理 | SELF | `WHERE created_by = :empId` |
| SUPPORT | 团队长 | ORG+下级 | `WHERE owner_org_id IN (:orgSubtree)` |
| SUPPORT | 分行长 | ORG+下级 | `WHERE owner_org_id IN (:orgSubtree)` |
| SUPPORT_DEPT | 部门秘书 | ORG(部门) | `WHERE support_dept_id = :deptOrgCode` |
| SUPPORT_DEPT | 支持人员 | SELF_ASSIGNED | `WHERE assigned_emp_id = :empId` |
| SUPPORT_DEPT | 部门总 | ORG | `WHERE support_dept_id = :deptOrgCode` |

### 9.3 接口分离

两个视图使用完全独立的 Controller、Facade、资源编码：

| 视图 | Controller | 路径前缀 | 资源编码前缀 |
|------|----------|---------|-----------|
| SUPPORT | `SupportRequestController` | `/api/support-requests` | `BIZAPP:SUPPORT:*` |
| SUPPORT_DEPT | `SupportDeptController` | `/api/support-dept/requests` | `BIZAPP:SUPPORT_DEPT:*` |

**好处：**
1. 权限授权灵活：可分别授权，客户经理看不到承接侧视图；
2. 菜单分离：两个独立菜单，避免混淆；
3. 审计清晰：审计日志 `resource_type` 区分清楚；
4. 数据范围独立：两套规则互不干扰。

---

## 10. 分区与归档建议

### 10.1 V1 策略（不分区）

V1 阶段数据量较小（预估 20-30 万/年），**不实施分区**，通过索引和缓存即可满足性能要求。

### 10.2 V2 策略（按月分区）

预估数据量超过 100 万后启用分区：

```sql
-- 示例: loan_apply 按 created_time 月分区
ALTER TABLE loan_apply
PARTITION BY RANGE (TO_DAYS(created_time)) (
  PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
  PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
  PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
  ...
  PARTITION pmax    VALUES LESS THAN MAXVALUE
);
```

**分区优势：**
- 按月检索时自动分区裁剪，扫描范围显著缩小；
- 老月份分区可单独归档/冷备；
- 批量删除历史数据使用 `ALTER TABLE DROP PARTITION` 秒级完成。

### 10.3 归档策略

| 数据 | 在线保留 | 归档存储 | 归档方式 |
|-----|--------|---------|---------|
| `LOAN_APPLY`（COMPLETED/REJECTED） | 12 个月 | 归档库/对象存储 | 定期 `INSERT INTO ... SELECT` 后 `DELETE` |
| `SUPPORT_REQUEST`（COMPLETED/REJECTED） | 12 个月 | 同上 | 同上 |
| `DRAFT`/`IN_APPROVAL`/`IN_PROGRESS` | 永不归档 | - | 终态前必须在线 |
| 审计日志 | 3 年 | governance 模块负责 | 按月归档 |
| Flowable 流程历史 | 同业务 | workflow-center 负责 | 由 `ACT_HI_*` 表独立归档 |

### 10.4 备份策略

| 频次 | 内容 | 方式 |
|-----|-----|-----|
| 每日 | 全量备份 | MySQL Dump/Xtrabackup |
| 每小时 | Binlog 增量 | Binlog 备份 |
| 每月 | 归档备份 | 冷备归档 |

---

## 11. 建表执行顺序

本模块建表无前置依赖（所有外键为逻辑外键），可直接执行：

```bash
# 初始化脚本路径
docs/modules/business-application-center/sql/ddl-bizapp.sql

# 执行顺序
1. DROP TABLE IF EXISTS loan_apply;
2. CREATE TABLE loan_apply ...;
3. DROP TABLE IF EXISTS support_request;
4. CREATE TABLE support_request ...;
```

**跨模块依赖的建表顺序（供参考）：**
```
common (无表)
  ↓
auth-permission-center (PT_USER / PT_ROLE / PT_RESOURCE / EXT_ORG_INFO)
  ↓
system-governance-center (dict / file_object / biz_file_rel / audit_log)
  ↓
workflow-center (ACT_* 由 Flowable 自动建表, wf_node_form_conf 手工建)
  ↓
portal-content-center (product_info)
  ↓
customer-marketing-center (cust_master / touch_task)
  ↓
business-application-center (loan_apply / support_request)  ← 本模块
```

---

## 12. 兼容与演进

### 12.1 V1 → V2 演进点

| 字段 | 建议变更 | 原因 |
|------|--------|------|
| `loan_apply.credit_amount` | 拆分币种字段 `currency_code` | 支持多币种 |
| `LOAN_APPLY` | 新增 `rate_type` / `rate` 字段 | 利率字段 |
| `SUPPORT_REQUEST` | 新增 `sla_hours` / `due_time` | SLA 管控 |
| `support_request.submit_group_id` | 独立 `support_submit_group` 汇总表 | 优化聚合查询 |

### 12.2 字段长度规范

| 字段类型 | 长度规范 |
|---------|--------|
| ID | VARCHAR(32) — UUID without dash |
| 编号 | VARCHAR(100) — 前缀+日期+序号 |
| ORG_CODE | VARCHAR(50) — 层级编码 |
| EMP_ID | VARCHAR(32) — 工号 |
| 字典值 | VARCHAR(32) — 英文大写+下划线 |
| 金额 | DECIMAL(20,4) — 最大 9999 万亿,保留 4 位小数 |
| 状态 | VARCHAR(20) — 英文大写 |
| business_key | VARCHAR(100) — 含前缀 |
| process_instance_id | VARCHAR(64) — Flowable 规范 |

---

## 13. 参考文档

- [common/AGENTS.md](../../../common/AGENTS.md) — 公共组件规范
- [docs/common-dev-guide.md](../../common-dev-guide.md) — 通用开发规范
- [docs/modules/system-governance-center/05-表结构DDL.md](../system-governance-center/05-表结构DDL.md) — biz_file_rel 定义
- [docs/modules/workflow-center/05-表结构DDL.md](../workflow-center/05-表结构DDL.md) — wf_node_form_conf 定义
- [docs/modules/business-application-center/04-领域模型与API设计.md](./04-领域模型与API设计.md) — API 详细设计
