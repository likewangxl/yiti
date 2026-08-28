# 资产立项 MARKETING_ 数据模型与页面设计 V2

> 状态：DBA/业务/架构联合评审稿，不代表数据库已实施。
> 编制日期：2026-08-28。
> 替代：`2026-08-24-asset-project-schema-v1.md` 中的 `LOAN_APPLY/LOAN_URGENT_REQUEST` 目标设计。

## 1. 已确认基线

1. 客户营销自有表统一使用 `MARKETING_` 前缀，主键统一为 `BIGINT UNSIGNED AUTO_INCREMENT`。
2. 营销客户主档是 `MARKETING_CUSTOMER_INFO`，资产立项只保存 `cust_id`，不复制客户全量字段。
3. 触达任务和工作日志已分别使用 `MARKETING_TOUCH_TASK`、`MARKETING_TOUCH_WORKLOG`。日志直接保存 `task_id`，新数据支持一任务多日志。
4. 资产立项可从客户页或触达页发起；来源任务和精确工作日志均为可选关联。
5. 附件使用 `FILE_OBJECT/BIZ_FILE_REL`，流程映射及审批历史使用 `BIZ_PROCESS_MAP` 和 Workflow API，不复制平台共享表。

## 2. 表清单

| 表名 | 职责 | 关系 |
|---|---|---|
| `MARKETING_ASSET_PROJECT_APPLY` | 资产立项主申请、当前标签和台账 | N:1 `MARKETING_CUSTOMER_INFO`；可选关联触达任务/日志 |
| `MARKETING_ASSET_PROJECT_URGENT_APPLY` | 审批中的独立中途加急申请 | N:1 主申请；同一主申请同时最多一条在途申请 |

不单独建立附件表、审批历史表、客户快照主表或项目标签字典表。

## 3. 主要关系

```text
MARKETING_CUSTOMER_INFO
    └─ MARKETING_ASSET_PROJECT_APPLY
           ├─ source_touch_task_id  → MARKETING_TOUCH_TASK.id       (可空)
           ├─ source_worklog_id    → MARKETING_TOUCH_WORKLOG.id    (可空)
           ├─ 附件                  → BIZ_FILE_REL / FILE_OBJECT
           ├─ 主流程                → BIZ_PROCESS_MAP / Workflow API
           └─ MARKETING_ASSET_PROJECT_URGENT_APPLY
                  └─ 独立加急流程 → BIZ_PROCESS_MAP / Workflow API
```

`source_worklog_id` 非空时，必须满足工作日志的 `task_id=source_touch_task_id`、`cust_id=主申请.cust_id`。这些为 Service 逻辑关联，不建跨域物理外键。

## 4. 关键建模决策

### 4.1 客户与触达来源

- `cust_id` 为必填稳定关联，提交时通过客户营销公开 Query API 重查客户有效性、主办人/机构及数据范围。
- 从触达页发起时可带 `source_touch_task_id`；如用户在某条工作日志上发起，再带 `source_worklog_id`。
- 不保存客户名称、统一社会信用代码等全量重复列；主办人和主办机构仅保存立项时快照，用于审批回溯。

### 4.2 加急与重点项目

- `is_urgent/is_key_project` 是项目申请属性，不写入 `MARKETING_CUSTOMER_TAG_REL`。
- 启动时加急：草稿提交前写主表 `is_urgent=1, urgent_source=INITIATION`，主流程走加急分支，不写子表。
- 中途加急：创建 `MARKETING_ASSET_PROJECT_URGENT_APPLY`；审批过程中主表仍为普通，审批通过后与子表终态在同一本地事务中回写 `is_urgent=1, urgent_source=MID_PROCESS`。
- 一旦加急通过不再回退为普通；重点项目标签在主流程提交后冻结。

### 4.3 金额口径

- 金额统一 `DECIMAL(18,2)`，数据库单位人民币元，页面可按万元展示但接口不改单位。
- 硬校验：总投资、贷款金额、授信金额非负，敞口金额在 `[0, credit_amount]` 内。
- `project_loan_amount <= credit_amount <= project_total_investment` 在业务口径未冻结前只做页面提示，不做数据库硬约束。

## 5. 状态与并发

### 5.1 主申请

```text
DRAFT → IN_APPROVAL → COMPLETED
                    ├→ REJECTED
                    └→ CANCELLED
```

- 仅 `DRAFT` 可编辑、逻辑删除和修改标签；提交后业务字段冻结。
- 撤回必须以 Workflow API 实时任务节点为准，未知节点、多活动任务或查询异常均 Fail Close。
- 可修改记录用 `lock_version`，不以前端状态作为并发判定。

### 5.2 中途加急

```text
DRAFT → IN_APPROVAL → APPROVED / REJECTED / CANCELLED
```

`active_dedup_key` 在 `DRAFT/IN_APPROVAL` 时写 `ASSET_PROJECT:{asset_project_apply_id}`，终态置空，通过可空唯一索引防止同一主申请同时有多条在途加急。创建时还必须锁定主申请并重查真实当前节点。

## 6. 页面功能

### 6.1 列表/工作台

| 区域 | 字段或操作 |
|---|---|
| 页签 | 我的申请、待办、已办 |
| 查询 | 申请编号、客户名称、项目名称、状态、加急、重点项目、发起日期 |
| 列表 | 申请编号、客户、项目、项目贷款金额、授信金额、两个标签、当前节点/处理人、状态、最后处理时间 |
| 操作 | 草稿编辑/提交/删除，审批中查看/撤回/申请加急，终态只读查看 |

### 6.2 新建/编辑

1. **客户信息**：从 `MARKETING_CUSTOMER_INFO` 分页搜索，反显客户号、统一社会信用代码、主办人和主办机构。
2. **触达来源**：从触达进入时只读反显任务和工作日志；普通新建时可空。
3. **项目概况**：项目名称、项目类型、业务类型、总投资、项目贷款金额。
4. **授信与担保**：授信金额、敞口金额、担保方式。
5. **标签与附件**：启动时加急、重点项目、附件上传/预览/下载。

### 6.3 详情

统一详情组件包含“申请信息、来源触达、附件、流程进度、审批记录、加急记录”六个区块。授信部普通用户只查看加急/重点标签，不展示加急原因、来源和独立审批意见。

### 6.4 中途加急弹窗

只在服务端确认主申请处于允许节点、当前申请未加急且没有在途加急时显示。项目、客户、主申请号、当前节点/任务由后端返回并只读，用户只填加急原因。

## 7. API 与模块边界

- 正式资源统一为 `/api/marketing/asset-projects`；旧 `/api/loans` 及其页面、Controller、Service、Mapper、Entity 直接删除，不保留兼容路由，也不做新旧双写。
- 资产立项表若归属客户营销模块，其他模块只能通过公开 `AssetProjectApi/AssetProjectQueryApi` 访问，不跨模块直连 Mapper。
- 流程操作继续只通过 `workflow-center` 公开契约，营销模块不直接操作 Flowable `ACT_*` 表。

## 8. 迁移与发布门禁

1. 旧 `LOAN_APPLY/LOAN_URGENT_REQUEST` 与新表的 ID 映射、businessKey、processInstanceId、附件bizId和历史待办跳转必须统一迁移，不做新旧双写。
   迁移记录分别把旧字符串主键写入 `legacy_apply_id`、`legacy_urgent_apply_id`；新数据这两列为空。
2. 先在隔离库建新表和映射数据，做数量、金额、状态、流程、附件和逻辑关联对账；未通过不切换。
3. 同步调整实体、Mapper/XML、Service、公开 API、页面、测试schema/data、字典、权限、审计和文档；不允许只改 `@TableName`。
4. 本轮已交付应用接口、页面、测试与候选 SQL；业务 DDL 和权限迁移 SQL 未执行，仍须经 DBA/权限管理员评审后实施。
