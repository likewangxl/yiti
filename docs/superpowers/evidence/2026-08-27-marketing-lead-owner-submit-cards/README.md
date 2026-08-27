# 线索录入主办权、送审与状态卡验收

## 验收边界

- 日期：2026-08-27
- 真实页面：`http://127.0.0.1:8090/#/customers/leads/new`
- 浏览器：官方 `playwright-cli`，Chromium
- 会话：`lead-owner-cards`
- mock route：无
- 数据库写入：无；未保存草稿、未提交审批
- 目标库当前无有效客户和手工线索，因此真实页面验收覆盖布局、互斥卡片和抽屉双入口；主办命中锁定由前后端定向测试覆盖。

## 执行命令

```bash
PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
PLAYWRIGHT_MCP_BROWSER=chromium \
npx --no-install playwright-cli -s=lead-owner-cards open http://127.0.0.1:8090

# 通过本地开发登录端点建立会话（凭据不归档），然后进入线索录入路由
npx --no-install playwright-cli -s=lead-owner-cards run-code '<login-and-goto-redacted>'

npx --no-install playwright-cli -s=lead-owner-cards snapshot
npx --no-install playwright-cli -s=lead-owner-cards click e441
npx --no-install playwright-cli -s=lead-owner-cards click e447
npx --no-install playwright-cli -s=lead-owner-cards click e447
npx --no-install playwright-cli -s=lead-owner-cards click e428
npx --no-install playwright-cli -s=lead-owner-cards route-list
npx --no-install playwright-cli -s=lead-owner-cards console
npx --no-install playwright-cli -s=lead-owner-cards requests
npx --no-install playwright-cli -s=lead-owner-cards request 63
npx --no-install playwright-cli -s=lead-owner-cards response-body 64
npx --no-install playwright-cli -s=lead-owner-cards screenshot --filename <evidence>/lead-entry-drawer.png --full-page
npx --no-install playwright-cli -s=lead-owner-cards screenshot --filename <evidence>/lead-status-cards.png --full-page
```

## route-list 原始结果

```text
No active routes
```

## console 原始结果

```text
Total messages: 4 (Errors: 1, Warnings: 1)
Returning 2 messages for level "info"

[ERROR] Failed to load resource: the server responded with a status of 401 (Unauthorized) @ http://127.0.0.1:8090/api/auth/current-user:0
[WARNING] [Vue Router warn]: No match found for location with path "/bizexec/supports" @ http://127.0.0.1:8090/node_modules/.vite/deps/vue-router.js?v=7de43bc9:52
```

401 发生在建立开发会话之前；登录后 `current-user`、菜单、权限和线索接口均返回 200。`/bizexec/supports` 是现有菜单路由告警，与本页改动无关。

## request/response 原始摘要

```text
63. [GET] /api/marketing/leads?keyword=&status=&leadSource=MANUAL&pageNo=1&pageSize=20 => [200] OK
64. [GET] /api/marketing/leads?status=DRAFT&pageNo=1&pageSize=1&leadSource=MANUAL => [200] OK
65. [GET] /api/marketing/leads?status=IN_APPROVAL&pageNo=1&pageSize=1&leadSource=MANUAL => [200] OK
66. [GET] /api/marketing/leads?status=APPROVED&pageNo=1&pageSize=1&leadSource=MANUAL => [200] OK
67. [GET] /api/marketing/leads?status=REJECTED&pageNo=1&pageSize=1&leadSource=MANUAL => [200] OK
73. [GET] /api/marketing/leads?keyword=&status=DRAFT&leadSource=MANUAL&pageNo=1&pageSize=20 => [200] OK
74. [GET] /api/marketing/leads?keyword=&status=IN_APPROVAL&leadSource=MANUAL&pageNo=1&pageSize=20 => [200] OK
75. [GET] /api/marketing/leads?keyword=&status=&leadSource=MANUAL&pageNo=1&pageSize=20 => [200] OK
76. [GET] /api/marketing/customer-tags?status=ENABLED&approvalStatus=APPROVED&pageNo=1&pageSize=100 => [200] OK

#63 status=200, duration=39ms, type=xhr, mimeType=application/json
#64 body={"code":"0","message":"success","page":{"pageNo":1,"pageSize":1,"total":0,"records":[],"totalPages":0}}
```

## 交互结果

```text
点击“草稿”：草稿 aria-pressed=true，其他三张=false。
再点击“审批中”：审批中=true，草稿/已通过/已退回=false。
重复点击“审批中”：四张卡片全部=false，筛选清除。
点击“录入线索”：打开 1080px 抽屉，显示 01–04 四个分节；底部同时显示“保存草稿”和“提交审批”。
```

截图：`lead-status-cards.png`、`lead-entry-drawer.png`。
