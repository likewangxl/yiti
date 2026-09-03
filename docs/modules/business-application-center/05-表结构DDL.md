# 业务申请中心 — 表结构与字段说明

> 本模块当前实体为 `SupportRequest`，映射表 `SUPPORT_REQUEST`。本文是模型说明，不是可执行 DDL；目标库实际 schema、索引和字符集以 DBA 核准后的目标库为准。

## 1. 表职责

`SUPPORT_REQUEST` 保存中场支持申请及其流程关联，使用 `deleted=0/1` 逻辑删除。一个创建请求在场景 A 可能拆成多行，每行对应一个产品；这些行共享 `submit_group_id`，但各自独立提交和流转。场景 B 只生成一行，不保存 `product_id`。

## 2. 字段模型

| 字段 | 类型语义 | 说明 |
|---|---|---|
| `id` | `VARCHAR(32)` | 主键，无连字符 UUID |
| `request_no` | `VARCHAR(100)` | 申请编号，业务编号生成器维护唯一性 |
| `submit_group_id` | `VARCHAR(64)` | 同批拆单分组；场景 B 也生成分组值 |
| `cust_id` | `VARCHAR(32)` | 客户 ID，逻辑关联客户营销模块 |
| `source_touch_task_id` | `VARCHAR(32)` | 来源触达任务 ID，可为空 |
| `product_id` | `VARCHAR(64)` | 场景 A 产品 ID；场景 B 为空 |
| `support_dept_id` | `VARCHAR(50)` | 场景 B 承接机构编码 |
| `other_demand` | 文本 | 其他需求或补充说明 |
| `dispatch_emp_id` | `VARCHAR(32)` | 派单人，部门承接场景使用 |
| `dispatch_time` | 时间 | 派单时间 |
| `assigned_emp_id` | `VARCHAR(32)` | 产品负责人或部门承接人 |
| `status` | `VARCHAR(20)` | `DRAFT`、`IN_APPROVAL`、`IN_PROGRESS`、`COMPLETED`、`REJECTED`、`CANCELLED` |
| `business_key` | `VARCHAR(100)` | 固定为 `SUPPORT:{id}` |
| `process_instance_id` | `VARCHAR(64)` | 工作流实例 ID，草稿为空 |
| `owner_org_id` | `VARCHAR(50)` | 发起侧归属机构编码 |
| `created_by` | `VARCHAR(32)` | 创建/发起人工号 |
| `created_time` | 时间 | 创建时间 |
| `updated_by` | `VARCHAR(32)` | 最近更新人工号 |
| `updated_time` | 时间 | 最近更新时间 |
| `deleted` | 整数/布尔语义 | `0` 有效，`1` 逻辑删除 |

实体使用 `@TableName("SUPPORT_REQUEST")` 和输入型主键；`SupportRequestMapper` 的全部业务查询带 `deleted=0`。

## 3. 查询与索引要求

当前 Mapper 使用以下访问模式，目标库应据实际数据量和 DBA 审核结果维护相应索引：

- 主键查询、业务键查询、申请编号查询；
- `cust_id` 历史和运行中数量；
- `submit_group_id` 同批查询；
- 发起侧按 `owner_org_id`、`status`、`created_time` 分页；
- 承接侧按 `support_dept_id`、`assigned_emp_id`、`status`、`created_time` 分页；
- 同客户同产品、状态为 `IN_APPROVAL/IN_PROGRESS` 的重复申请检查；
- 创建人/承接人加完成状态和时间范围的统计。

`request_no`、`business_key` 是否唯一及复合索引的具体定义，以目标库现状和审批后的 schema 为准，不在本文生成或执行 DDL。

## 4. 状态与流程关联

```text
DRAFT -> IN_APPROVAL
IN_APPROVAL -> IN_PROGRESS | COMPLETED | REJECTED | CANCELLED
IN_PROGRESS -> COMPLETED | REJECTED | CANCELLED
```

`SupportService.submit` 启动 `support_simple_v1` 或 `support_complex_v1` 并写入 `process_instance_id`；流程回调只允许按 `business_key` 找到申请，并以 `IN_APPROVAL` 为前置条件更新结果。

## 5. 逻辑关联边界

| 外部对象 | 关联字段 | 访问方式 |
|---|---|---|
| 客户营销中心客户 | `cust_id` | `CustomerQueryApi`，不建跨模块物理外键 |
| 客户营销中心触达任务 | `source_touch_task_id` | 仅保存来源 ID；不直连任务表 |
| 门户产品 | `product_id` | `ProductApi`，不直连产品表 |
| 机构/员工 | `owner_org_id`、`support_dept_id`、`*_emp_id` | auth/portal 公开 API 或统一数据范围 |
| 工作流实例 | `business_key`、`process_instance_id` | `WorkflowApi` 和 `ProcessCompletedEvent` |

附件、通知和审计如使用平台公共表/接口，归属治理模块；本表没有附件 JSON 或流程节点表单列。

## 6. 维护边界

- schema 变更由 DBA 审批后在目标库实施；本模块文档不承担建表、回滚或一次性执行步骤。
- 新增字段必须同时更新 `SupportRequest`、DTO、Mapper 查询和本模块接口文档，并核查数据范围、审计和事务影响。
