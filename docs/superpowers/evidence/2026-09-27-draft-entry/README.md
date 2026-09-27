# 新版草稿入口：生产构建浏览器验收（2026-09-27）

## 验收边界

项目生产构建由 `npm run build` 生成，以 `npm run preview -- --host 127.0.0.1 --port 8094 --strictPort` 运行。浏览器工具是项目内官方 `@playwright/cli` 0.1.18。

本轮浏览器流程使用**仅开发态 mock，非真实联调**。`verify-production.js` 在 CLI `run-code` 内注册 `page.route('**/api/**')`，只为以下接口提供合成响应；未登记的 `/api/` 返回 `QA_UNMATCHED` 404，静态资源继续请求真实 Vite preview 服务。

| API | 合成响应摘要 |
| --- | --- |
| `/api/auth/current-user` | 合成登录账号 |
| `/api/auth/my-menus` | `/screens` 菜单 |
| `/api/auth/permissions` | manager 有整屏查看和画布管理读取资源；viewer 只有整屏查看资源 |
| `/api/screen/view/catalog` | 三个固定屏的授权目录 |
| `/api/screen/view/SCR_PROVINCE?preview=draft` | `state=draft`、`displaySchemaVersion=1` 的**空展示组件**合成包；只验证新版渲染分支，不代表实际草稿内容 |
| `/api/notifications/unread-count` | 0 |

## 官方 CLI 命令

在 `xanzc_frontend` 目录执行：

```powershell
.\node_modules\.bin\playwright-cli.cmd -s=draft-production-qa open about:blank --browser=chrome
.\node_modules\.bin\playwright-cli.cmd -s=draft-production-qa run-code --filename ../docs/superpowers/evidence/2026-09-27-draft-entry/verify-production.js
.\node_modules\.bin\playwright-cli.cmd -s=draft-production-qa route-list
.\node_modules\.bin\playwright-cli.cmd -s=draft-production-qa requests
.\node_modules\.bin\playwright-cli.cmd -s=draft-production-qa request 25
.\node_modules\.bin\playwright-cli.cmd -s=draft-production-qa console
```

原始 CLI 输出为 `route-list.txt`、`requests.txt`、`draft-request.txt`、`console.txt`。`route-list.txt` 的 `No active routes` 仅列 CLI `route` 命令注册的规则；本轮 `page.route` 注册代码及逐条合成响应见 `verify-production.js`。

## 结果

生产构建中的大屏中心显示三个“未发布草稿预览”按钮。manager 点击分行按钮后，请求 `GET /api/screen/view/SCR_PROVINCE?preview=draft`，后端状态字段为 `draft` 时显示“未发布草稿预览”，进入 `data-testid=presentation-layout` 的新版配置分支；点击返回回到大屏中心。viewer 不显示草稿入口。浏览器控制台错误和警告均为 0。截图为 `production-center.png`、`production-draft.png`。

此合成验收只证明生产代码路径和客户端权限展示。真实账号、实际草稿展示内容与业务取数须另做无 mock 验收，不能以本记录代替。
