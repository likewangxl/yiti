# 待认领线索池分配规则验收

验收时间：2026-08-13（Asia/Shanghai）

## 结论

- 前端页面没有内置公司列表，数据来自真实 `GET /api/customer-pool` 请求。
- 浏览器无任何拦截路由，开发环境 `VITE_USE_MOCK=false`。
- 真实接口返回 HTTP 200、`total=0`、`records=[]`；页面显示“暂无数据”，原来由无关联线索客户造成的 8 条记录不再显示。
- 后端真实 SQL 要求 `CUST_LEAD.lead_status='APPROVED'` 且 `distribution_mode='PUBLIC'`。
- 本次真实页面验收只读取待认领池，没有创建、审批、认领或删除客户业务数据。`SCOPE` 审批后的自动认领关系由单元测试覆盖，未在当前 `yiti` 数据库制造测试业务记录。

## 环境

- 前端：`http://127.0.0.1:8090`
- 后端：`http://127.0.0.1:18080`
- SOAP：`30522`
- 浏览器：官方 `@playwright/cli`，Chromium

## 证据

- `01-official-playwright-cli.raw.txt`：CLI 命令、无 route 清单、页面断言、console、请求和响应体。
- `02-runtime-backend.raw.txt`：启动端口、健康检查、真实 SQL 与接口返回日志摘录。
- `customer-pool-public-only.png`：1440×900 真实页面截图。

说明：首次尝试加载 `/tmp/yiti-lead-detail-auth-20260813.json` 时文件已不存在，浏览器按预期得到 `current-user` 401 并回到登录页；该未认证尝试已丢弃，不作为验收结果。随后使用本地开发账号建立真实会话，认证口令不归档。
