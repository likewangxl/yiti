# 客户触达周期管理 V1 实施规格

> 日期：2026-08-25
>
> 范围：仅实现客户标签维度的触达周期上限配置页面与接口，不接入发起触达校验。
> 数据库状态：已只读核对 `yiti`、`yiti_test`，当前均未存在 `CUST_TOUCH_LIMIT_RULE`；结构变更须由 DBA 审批后直接实施，本规格不是可执行 DDL。

## 1. 阶段边界

本阶段交付：

- 按客户标签分页查看触达周期规则；
- 未单独配置的标签按“月、5 次”展示；
- 修改单个标签的周期维度和次数上限；
- 独立菜单、接口资源和写操作审计。

本阶段不交付：

- 线索“是否触达限制”字段及 Excel 导入列；
- 发起首次触达、再次触达时的开户状态、存量客户标签例外和周期次数校验；
- 对 `xa_touch_name_list_record` 导入流程的改造；
- 对 `yiti`、`yiti_test` 的任何 DDL/DML 执行。

## 2. 规则来源

周期规则以现有 `CUST_TAG.id` 为唯一业务键，不以 `xa_touch_name_list_record.nameType` 的文本聚合结果作为配置主键。名单导入成功后的 `GROUP BY nameType` 可以用于候选标签盘点和对账，但不能覆盖已维护的周期规则，原因如下：

1. `nameType` 是可变文本，存在改名、空格、重复和历史别名风险；
2. 重复导入会反复生成相同候选，不能天然保证规则幂等；
3. 管理员已修改的周期和次数不能被下一次名单导入重置；
4. 现有标签审核、启停和删除生命周期都以 `CUST_TAG.id` 为真相。

名单标签必须先按精确名称解析到唯一、有效的客户标签；未解析项进入待处理清单，不自动创建标签或周期规则。

## 3. 接口契约

### 3.1 分页查询

`GET /api/touch-limit-rules?keyword=&pageNo=1&pageSize=20`

- 权限：`TAG / LIST`；
- 范围：所有 `deleted=0` 的客户标签；
- 查询：`keyword` 模糊匹配标签名称；
- 缺省：没有规则行时返回 `cycleUnit=MONTH`、`maxTouches=5`，不在读取时回写数据库；
- 查询实现：先分页查询标签，再按本页标签 ID 一次性查询规则，禁止 N+1。

### 3.2 修改规则

`PUT /api/touch-limit-rules/{tagId}`

```json
{
  "cycleUnit": "MONTH",
  "maxTouches": 5
}
```

- 权限：`TAG / WRITE`；
- `cycleUnit`：`DAY`、`WEEK`、`MONTH`、`QUARTER`、`YEAR`；
- `maxTouches`：1 到 9999；
- Service 层必须重新校验标签存在且未删除；
- 按 `tag_id` 新增或更新，数据库唯一键作为并发最终防线；
- 写操作记录 `UPDATE_TOUCH_LIMIT_RULE` 审计动作。

## 4. DBA 结构变更契约

目标表：`CUST_TOUCH_LIMIT_RULE`。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `id` | `varchar(32)` | 主键，应用生成 32 位 UUID |
| `tag_id` | `varchar(32)` | 非空、唯一，逻辑关联 `CUST_TAG.id` |
| `cycle_unit` | `varchar(16)` | 非空，取值 DAY/WEEK/MONTH/QUARTER/YEAR |
| `max_touches` | `int` | 非空，业务范围 1..9999 |
| `created_by` | `varchar(32)` | 非空，创建人工号 |
| `created_time` | `datetime` | 非空，创建时间 |
| `updated_by` | `varchar(32)` | 非空，最后修改人工号 |
| `updated_time` | `datetime` | 非空，最后修改时间 |

约束与索引：主键 `id`，唯一键 `tag_id`。项目不建立物理外键，标签存在性由 Service 校验。默认“月、5 次”由接口在无规则行时提供，因此不要求为所有存量标签预灌规则行。

DBA 实施前须按仓库门禁先完成隔离库盘点、备份与恢复演练；本文不提供或执行 DDL。

## 5. 菜单与资源前置

资源登记白名单位于
`docs/superpowers/sql/2026-08-25-customer-touch-limit-resource-align.sql`，只包含幂等
`INSERT`/`UPDATE` 和事务控制，本次不执行。默认仅绑定 `SYS_ADMIN`；其他角色需管理员按岗位重新授权。

目标库完成规则表建表、资源对齐和角色授权前，可以完成单元测试和前端开发态 mock 页面验收，但不能声称已完成真实联调。

## 6. 后续触达校验预留

下一阶段在首次/再次触达写入口统一校验，不把规则放在前端：

1. 仅线索/客户“是否触达限制”为是时进入校验；
2. 客户标签包含“存量客户”时跳过开户状态校验；
3. 其他已开户客户拒绝并提示“该企业经判定已开户，无法再创建工作日志”；
4. 再按全部有效客户标签的周期规则统计有效工作日志次数；
5. 多标签命中时采用最严格规则还是指定优先级，须在下一阶段由业务确认后实现。
