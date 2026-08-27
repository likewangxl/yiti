# 营销客户所属行业中文展示验收与审批入池诊断

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8090/#/customers/manage`
- 工具：项目本地官方 `@playwright/cli`、Chromium
- 验收性质：开发态精确 mock，仅验证前端展示和交互，不写业务数据库

## 前端验收结果

1. 查询表单新增“所属行业”字典下拉。
2. 列表中的行业代码 `IT` 显示为“信息技术”。
3. 详情中的所属行业显示为“信息技术”。
4. 编辑表单使用行业字典下拉，并将当前值回显为“信息技术”。
5. 验收结束时浏览器控制台为 0 errors、0 warnings。

截图：

- `marketing-customer-detail-industry.png`
- `marketing-customer-edit-industry.png`

## 审批后未进入待认领池的只读诊断

数据库中的目标线索为 `APPROVED + PUBLIC`，但仍是 `cust_id = NULL`、
`pool_status = NOT_READY`，因此不满足待认领池要求的
`APPROVED + PUBLIC + AVAILABLE` 及客户主档关联条件。

审批日志显示：客户主档 INSERT 成功；随后回写线索 `cust_id` 与
`pool_status` 时，MyBatis 乐观锁使用旧版本号，UPDATE 影响行数为 0；
服务未检查该返回值，事务仍提交并返回成功。当前数据库未做修复性写入。
