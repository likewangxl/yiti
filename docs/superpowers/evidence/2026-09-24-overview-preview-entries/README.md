# 大屏中心改造版总览入口验收（2026-09-24）

## 边界

仅开发态 mock，非联调。页面位于本地 Vite 服务 `http://127.0.0.1:8092/#/screens`，业务后端未参与本次浏览器验收。两个预览页显示固定演示数据。正式大屏仍由后端目录授权和发布包控制。

## 官方 CLI 命令

在 `xanzc_frontend` 目录执行，工具为项目依赖的 `@playwright/cli` 0.1.18：

```powershell
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final open about:blank --browser=chrome
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final run-code --filename ../docs/superpowers/evidence/2026-09-24-overview-preview-entries/verify.js
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final route-list
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final requests
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final request 55
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final response-body 55
.\node_modules\.bin\playwright-cli.cmd -s=overview-preview-final console
```

`verify.js` 只注册一个 `page.route('**/api/**')` 拦截器，匹配下述五条 API；其他 `/api/` 请求返回 `QA_UNMATCHED` 404，静态资源继续向本地 Vite 请求。`route-list.txt` 显示 `No active routes`，这是 CLI 自身 route 命令的列表，不包含 `run-code` 内的 `page.route`。实际拦截器和响应定义以 `verify.js` 为准。

| 路由 | 开发态 mock 响应摘要 |
| --- | --- |
| `/api/auth/current-user` | 合成验收账号 |
| `/api/auth/my-menus` | `/screens` 菜单 |
| `/api/auth/permissions` | `/api/screen/view/*` 资源 |
| `/api/screen/view/catalog` | 空目录 `[]` |
| `/api/notifications/unread-count` | `0` |

`catalog-request.txt`、`catalog-response.txt`、`requests.txt`、`console.txt` 和 `route-list.txt` 为原始 CLI 回读。目录请求为 HTTP 200、`data: []`；验收过程中页面 JavaScript 异常为零，CLI 控制台错误和警告均为零。

## 结果

大屏中心同时出现对公和零售演示按钮，明确标记“本地演示 · 非业务数据”，空目录时不计入可访问大屏数量。分别点击后进入 `/#/screen-preview/corporate?from=screen-center` 和 `/#/screen-preview/retail?from=screen-center`，两页均渲染演示内容，返回按钮均回到 `/#/screens`。截图为 `center.png`、`corporate.png`、`retail.png`。

独立复核的定向 Vitest：6 文件、58 项通过。`npm run build` 通过；生产 `dist/assets` 未检出两个预览路由标记。构建仍报告原有 Sass legacy API 和大包体积警告。
