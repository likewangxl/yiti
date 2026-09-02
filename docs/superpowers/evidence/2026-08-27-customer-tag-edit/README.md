# 待认领池与营销客户标签编辑验收

- 日期：2026-08-27
- 工具：项目本地官方 `@playwright/cli` 0.1.18，Chromium，会话 `customer-tag-edit-20260827`
- 页面：`http://127.0.0.1:8090/#/customers/manage` 与 `#/customers/pool/available`
- 验收性质：**仅开发态 mock，非真实联调**。本次不向数据库写入营销客户资料。

## 验收结果

1. 待认领池筛选区只有关键词、查询和重置；可访问性查询返回 `distributionFilters=0`。
2. 营销客户编辑回显标签。标签下拉只展示“项目类客户”和“认定类客户”；Mock 返回的过期与非 ACTIVE 标签未进入可选项。
3. 多选后 PUT 请求体包含 `tagIds:[11,12]`；收敛后不包含 `custNo`、`unifiedCreditCode`、主办权字段或 `tagNames`。
4. 最终待认领池页 console 为 0 errors / 0 warnings，所有列出请求均为 HTTP 200。

## 证据

- `marketing-customer-tag-edit.png`：营销客户编辑标签多选。
- `available-pool-filter.png`：待认领池筛选区。
- `commands.txt`：官方 CLI 关键命令。
- `routes.txt`：最终 mock 路由清单。
- `requests.txt`：关键请求及 PUT 请求体。
- `console.txt`：最终 console 原始摘要。
