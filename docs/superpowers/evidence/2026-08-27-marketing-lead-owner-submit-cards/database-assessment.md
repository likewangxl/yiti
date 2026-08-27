# 数据库设计核对

本次仅执行目标库 `INFORMATION_SCHEMA` 和业务表聚合的只读查询，未执行 DDL/DML。

| 需求 | 现有表/字段 | 结论 |
|---|---|---|
| 按客户名称反查主档 | `MARKETING_CUSTOMER_INFO.cust_name`，`idx_cust_name` | 满足；名称不唯一，应由应用显式识别同名歧义 |
| 反显统一社会信用代码 | `MARKETING_CUSTOMER_INFO.unified_credit_code` | 满足；已有唯一索引 `uk_unified_credit_code` |
| 判断主办权 | `main_manager_id`、`main_org_id`、`ownership_status` | 满足 |
| 展示客户经理工号姓名 | 工号取 `main_manager_id`，姓名/机构名通过 `UserApi` 查询 | 满足；无需在客户或线索表重复存姓名 |
| 主办专属快照 | `MARKETING_LEAD_INFO.main_manager_id_snapshot`、`main_org_id_snapshot`、`distribution_mode` | 满足 |
| 主办专属接收人 | `MARKETING_LEAD_MANAGER_SCOPE.manager_emp_id`、`manager_org_id`、`assignment_type` | 满足；已有 `uk_lead_id_manager_emp_id` |
| 保存草稿但不送审 | `MARKETING_LEAD_INFO.lead_status=DRAFT` | 满足 |
| 提交审批 | `lead_status`、`submitted_by/time`、`process_instance_id`、`business_key` | 满足 |
| 草稿/审批中/已通过/已退回筛选 | `lead_status`，已有录入人和状态相关索引 | 功能满足，无需状态统计表 |

目标库盘点时 `MARKETING_CUSTOMER_INFO` 有效记录为 0，手工线索各状态记录也为 0。因此本次不对索引性能作压测结论；若后续数据量增大，可基于真实 `EXPLAIN` 再评估是否把 `lead_status` 加入 `(entry_emp_id, lead_source, entry_time)` 组合索引。这是性能候选，不是当前功能必需的数据库变更。
