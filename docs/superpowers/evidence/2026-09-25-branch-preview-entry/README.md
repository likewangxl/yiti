# 大屏中心分行经营总览演示入口验收

日期：2026-09-25。前端本地 Vite `127.0.0.1:8092`，使用项目内官方 `@playwright/cli` 0.1.18。

## 边界

本轮是**仅开发态 mock，非联调**。`verify.js` 在 CLI 的 `run-code` 中注册 `page.route('**/api/**')`：仅响应下列五个接口，未登记的 `/api/` 请求返回 `QA_UNMATCHED` 404；Vite 静态资源继续请求本地服务。演示页自身消费固定 `demoModel`，不是业务数据。正式分行大屏继续由后端目录授权、发布包及页面二次校验决定。

| 路由 | mock 响应摘要 |
| --- | --- |
| `/api/auth/current-user` | 合成验收账号 |
| `/api/auth/my-menus` | `/screens` 菜单 |
| `/api/auth/permissions` | `/api/screen/view/*` 资源 |
| `/api/screen/view/catalog` | 空目录 `[]` |
| `/api/notifications/unread-count` | `0` |

## 官方 CLI 真实命令

从 `xanzc_frontend` 目录运行：

```powershell
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa open about:blank --browser=chrome
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa run-code --filename ../docs/superpowers/evidence/2026-09-25-branch-preview-entry/verify.js
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa route-list
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa requests
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa request 55
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa response-body 55
.\node_modules\.bin\playwright-cli.cmd -s=branch-center-qa console
```

原始 CLI 回读保存在同目录的 `route-list.txt`、`requests.txt`、`catalog-request.txt`、`catalog-response.txt`、`console.txt`。`route-list.txt` 的 `No active routes` 只列出 CLI `route` 命令建立的规则，不列出 `run-code` 内的 `page.route`；实际拦截定义见 `verify.js`。

## 结果

大屏中心同时显示对公、零售、分行三个演示按钮；目录 mock 返回空数组时，计数仍为零。点击分行按钮进入 `/#/screen-preview?from=screen-center`，页面标题为“分行经营总览”，显示“本地演示 · 非业务数据”；点击页面返回按钮回到 `/#/screens`。`verify.js` 报告 `branchOpenAndReturn: true`、`pageErrors: []`。CLI 控制台错误、警告均为零。截图：`center.png`、`branch.png`。

定向 Vitest 6 文件、36 项通过；`npm run build` 通过。构建仍输出项目已有的 Sass legacy API 和大包体积提示。
