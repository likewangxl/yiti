# 线索录入字段与数据库适配结论

## 核对范围

- V2_DEMO 需求：`/home/djdev/lijh/V2_DEMO/公司部演示源码/03_功能需求与业务设计.md`
- yiti 功能设计：`docs/modules/customer-marketing-center/10-营销客户管理前后端功能设计.md`
- yiti 表设计：`docs/modules/customer-marketing-center/05-表结构DDL.md`
- 当前运行库：`yiti`，仅通过 `information_schema` 做只读结构核对

## 结论

当前数据库已覆盖页面设计字段，无需新增字段或执行 DDL。页面字段分布在主表、关系表和统一附件表中，符合设计文档的快照模型；原实现的缺口在应用层：标签、分配接收人和附件没有随草稿保存与回显。本次已补齐应用层持久化和详情回显。

| 页面字段 | 数据库存储 | 结论 |
| --- | --- | --- |
| 客户号 | `MARKETING_LEAD_INFO.cust_no_snapshot` | 满足 |
| 统一社会信用代码 | `MARKETING_LEAD_INFO.unified_credit_code` | 满足 |
| 客户名称 | `MARKETING_LEAD_INFO.cust_name` | 满足 |
| 所属行业 | `MARKETING_LEAD_INFO.industry` | 满足 |
| 所属集团类型 | `MARKETING_LEAD_INFO.group_type` | 满足 |
| 所属集团名称 | `MARKETING_LEAD_INFO.group_name` | 满足 |
| 客户类型 | `MARKETING_LEAD_INFO.customer_type` | 满足 |
| 是否基石客户 | `MARKETING_LEAD_INFO.is_keystone` | 满足；物理字段非空且无默认值，服务端兜底为 0 |
| 企业类型 | `MARKETING_LEAD_INFO.enterprise_type` | 满足 |
| 是否开户 | `MARKETING_LEAD_INFO.is_account_opened_snapshot` | 满足 |
| 客户说明 | `MARKETING_LEAD_INFO.customer_desc` | 满足 |
| 授信金额 | `MARKETING_LEAD_INFO.credit_amount` | 满足；库内单位元，页面单位万元 |
| 授信敞口金额 | `MARKETING_LEAD_INFO.credit_exposure_amount` | 满足；库内单位元，页面单位万元 |
| 分配方式 | `MARKETING_LEAD_INFO.distribution_mode` | 满足 |
| 指定客户经理 / 主办专属 | `MARKETING_LEAD_MANAGER_SCOPE` | 满足；本次补齐保存、替换和回显 |
| 客户标签 | `MARKETING_LEAD_TAG_REL` | 满足；本次补齐已审批启用标签校验、名称快照和回显 |
| 附件 | `FILE_OBJECT` + `BIZ_FILE_REL`，业务类型 `LEAD` | 满足；本次补齐绑定和详情回显 |
| 批量导入批次与明细 | `MARKETING_LEAD_IMPORT_BATCH`、`MARKETING_LEAD_IMPORT_DETAIL` | 满足；页面入口复用现有导入页签与接口 |

生产表 `MARKETING_LEAD_INFO.touch_restricted` 同样为非空且无默认值。该字段不属于 V2_DEMO 可见表单，页面隐藏默认值和服务端默认值均保持为 1，以满足现有领域约束，不额外暴露页面字段。

本轮未执行任何数据库写入或结构变更。
