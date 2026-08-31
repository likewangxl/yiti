# 业绩调整撤回单弹窗验收证据

- 验收时间：2026-08-31
- 验收基线：`5d4d21ebeedde5203e9cb2006282ad4fca307d89` 加本次未提交改动
- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 工具：项目本地官方 `playwright-cli 0.1.18`
- 验收性质：**仅开发态 mock，非联调**。未向真实后端提交撤回请求，未修改数据库。

## 启动与页面归属

已运行的 Vite 进程工作目录为 `/home/djdev/lijh/yiti/xanzc_frontend`，PID `394636`，监听 `0.0.0.0:8090`。本次未启动、停止或修改任何服务。

## 关键命令

所有 CLI 命令均在 `xanzc_frontend` 目录执行；为保证本地环回访问，命令前移除了当前 shell 的代理环境变量。

```bash
npm exec -- playwright-cli -s=perf-withdraw-final open http://127.0.0.1:8090/#/login --browser chromium
npm exec -- playwright-cli -s=perf-withdraw-final run-code '<注册下述拦截器，写入开发态用户 sessionStorage，并导航到 #/perf/adjust>'
npm exec -- playwright-cli -s=perf-withdraw-final snapshot
npm exec -- playwright-cli -s=perf-withdraw-final click e219
npm exec -- playwright-cli -s=perf-withdraw-final snapshot
npm exec -- playwright-cli -s=perf-withdraw-final screenshot e245 --filename docs/superpowers/evidence/2026-08-31-perf-adjust-withdraw-dialog/withdraw-single-dialog.png
npm exec -- playwright-cli -s=perf-withdraw-final click e266
npm exec -- playwright-cli -s=perf-withdraw-final snapshot
npm exec -- playwright-cli -s=perf-withdraw-final screenshot e245 --filename docs/superpowers/evidence/2026-08-31-perf-adjust-withdraw-dialog/withdraw-reason-required.png
npm exec -- playwright-cli -s=perf-withdraw-final requests
npm exec -- playwright-cli -s=perf-withdraw-final fill e262 '申请信息填写有误'
npm exec -- playwright-cli -s=perf-withdraw-final click e266
npm exec -- playwright-cli -s=perf-withdraw-final request 67
npm exec -- playwright-cli -s=perf-withdraw-final request-body 67
npm exec -- playwright-cli -s=perf-withdraw-final response-body 67
npm exec -- playwright-cli -s=perf-withdraw-final console debug
npm exec -- playwright-cli -s=perf-withdraw-final screenshot --filename docs/superpowers/evidence/2026-08-31-perf-adjust-withdraw-dialog/withdraw-success.png --full-page
```

## 路由与拦截器清单

通过 `run-code` 注册一个 `page.route("**/api/**", handler)`。handler 仅处理以 `http://127.0.0.1:8090/api/` 开头的请求；`/src/api/*.js` 等静态模块明确 `route.continue()`。CLI 的 `route-list` 只记录 `route` 子命令注册项，因此对该程序化拦截器输出 `No active routes`；以下为实际 handler 分支：

| 方法与路径 | mock 响应用途 |
|---|---|
| `GET /api/auth/current-user` | 开发态验收用户 |
| `GET /api/auth/my-menus` | 空菜单树 |
| `GET /api/auth/permissions` | 非审批人员权限 |
| `GET /api/notifications/unread-count` | 未读数 `0` |
| `GET /api/sys/dicts/PERF_ALLOC_DIM/items` | 空字典，页面使用内置展示口径 |
| `GET /api/sys/dicts/PERF_BIZ_KIND/items` | 空字典 |
| `GET /api/perf/alloc-adjust/my-applies` | 一条审批中申请 `ADJ-UI-1` |
| `POST /api/perf/alloc-adjust/ADJ-UI-1/withdraw` | 成功响应 `{ ok: true }` |

## 页面行为结果

1. 点击申请 `ADJ-UI-1` 的“撤回”后，页面只有一个标题为“撤回申请”的对话框；同一框内包含“确认撤回申请 ADJ-UI-1？”、“请填写撤回原因”、输入框、取消和确认撤回按钮。见 `withdraw-single-dialog.png`。
2. 原因留空点击“确认撤回”，对话框保持打开并显示红色错误“撤回原因必填”。此时请求清单没有撤回 POST。见 `withdraw-reason-required.png`。
3. 填写“申请信息填写有误”后确认，对话框关闭并显示“已撤回”；请求清单只出现一次撤回 POST，随后刷新我的申请列表。见 `withdraw-success.png`。

## 原始请求摘要

```text
49. [GET] http://127.0.0.1:8090/api/auth/current-user => [200] OK
61. [GET] http://127.0.0.1:8090/api/auth/my-menus => [200] OK
62. [GET] http://127.0.0.1:8090/api/notifications/unread-count => [200] OK
63. [GET] http://127.0.0.1:8090/api/sys/dicts/PERF_ALLOC_DIM/items => [200] OK
64. [GET] http://127.0.0.1:8090/api/sys/dicts/PERF_BIZ_KIND/items => [200] OK
65. [GET] http://127.0.0.1:8090/api/auth/permissions => [200] OK
66. [GET] http://127.0.0.1:8090/api/perf/alloc-adjust/my-applies?pageNo=1&pageSize=20 => [200] OK
67. [POST] http://127.0.0.1:8090/api/perf/alloc-adjust/ADJ-UI-1/withdraw => [200] OK
68. [GET] http://127.0.0.1:8090/api/perf/alloc-adjust/my-applies?pageNo=1&pageSize=20 => [200] OK
```

撤回请求与响应原文：

```json
{"reason":"申请信息填写有误"}
{"code":"0","message":"success","data":{"ok":true}}
```

## 原始 console

```text
Total messages: 2 (Errors: 0, Warnings: 0)

[DEBUG] [vite] connecting... @ http://127.0.0.1:8090/@vite/client:228
[DEBUG] [vite] connected. @ http://127.0.0.1:8090/@vite/client:324
```
