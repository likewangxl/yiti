# 线索录入 V2_DEMO 对齐验收证据

- 验收时间：2026-08-27
- 页面：`http://127.0.0.1:8090/#/customers/leads/new`
- 前端 checkout：`/home/djdev/lijh/yiti/xanzc_frontend`
- 后端：`http://127.0.0.1:18080`
- 浏览器：官方 `playwright-cli`，复用本机 Playwright Chromium
- 网络路由：`route-list` 返回 `No active routes`，未使用 mock
- 数据边界：仅执行一次登录 POST，其余业务请求均为 GET；未保存草稿、未上传文件、未导入文件、未写数据库

## 验收动作

1. 打开真实路由并完成登录前置。
2. 检查页头同时存在“批量导入”和“录入线索”。
3. 点击“录入线索”，检查 1080px 抽屉和 01 基础信息、02 经营属性、03 分配信息、04 补充资料四个分节。
4. 检查 V2_DEMO 字段：客户号、统一社会信用代码、客户名称、客户标签、所属行业、所属集团类型、客户类型、是否基石客户、企业类型、所属集团名称、是否开户、客户说明、授信金额、授信敞口金额、附件。
5. 点击“批量导入”，确认切换至真实批次列表，且导入批次 API 返回 200。
6. 检查控制台、请求列表和活动路由。

## 结果

- 页面主数据、四类字典、正式标签和导入批次请求均返回 200。
- 控制台 0 error、1 warning；warning 为既有 `/bizexec/supports` 路由未匹配，与本页面无关。
- 请求摘要：
  - `GET /api/marketing/leads?...leadSource=MANUAL...` => 200
  - `GET /api/sys/dicts/INDUSTRY/items` => 200
  - `GET /api/sys/dicts/GROUP_TYPE/items` => 200
  - `GET /api/sys/dicts/CUSTOMER_TYPE/items` => 200
  - `GET /api/sys/dicts/ENTERPRISE_TYPE/items` => 200
  - `GET /api/marketing/customer-tags?status=ENABLED&approvalStatus=APPROVED...` => 200
  - `GET /api/marketing/lead-import-batches?...` => 200

## 截图

- `lead-entry-drawer.png`：录入抽屉上半部分与基础、经营字段。
- `lead-entry-drawer-bottom.png`：分配信息、金额、附件和保存草稿操作。
- `batch-import-tab.png`：页头批量导入按钮及批次列表页签。

## 命令模板

实际命令均在 `xanzc_frontend` 目录执行；认证值不归档。

```bash
PLAYWRIGHT_MCP_EXECUTABLE_PATH=<local-chromium> PLAYWRIGHT_MCP_BROWSER=chromium \
  npx --no-install playwright-cli -s=lead-entry-v2-20260827 open \
  'http://127.0.0.1:8090/#/customers/leads/new'

PLAYWRIGHT_MCP_EXECUTABLE_PATH=<local-chromium> PLAYWRIGHT_MCP_BROWSER=chromium \
  npx --no-install playwright-cli -s=lead-entry-v2-20260827 snapshot

PLAYWRIGHT_MCP_EXECUTABLE_PATH=<local-chromium> PLAYWRIGHT_MCP_BROWSER=chromium \
  npx --no-install playwright-cli -s=lead-entry-v2-20260827 route-list

PLAYWRIGHT_MCP_EXECUTABLE_PATH=<local-chromium> PLAYWRIGHT_MCP_BROWSER=chromium \
  npx --no-install playwright-cli -s=lead-entry-v2-20260827 console

PLAYWRIGHT_MCP_EXECUTABLE_PATH=<local-chromium> PLAYWRIGHT_MCP_BROWSER=chromium \
  npx --no-install playwright-cli -s=lead-entry-v2-20260827 requests
```
