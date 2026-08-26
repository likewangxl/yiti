# 角色与工作台调整 Playwright CLI 验收证据

## 验收范围

- “支部审核员”合并为“支部书记”，演示身份中不再出现“支部审核员”。
- 支部书记只显示并进入“支部审核工作台”。
- “沉浸式审核工作台”更名为“工作台”，只对组织审核员显示。
- 一线员工任务待办入口显示为“首页”，避免与组织审核员“工作台”重名。

## 运行与浏览器命令

```bash
cd /home/djdev/leid/yiti/prototypes/red-engine-task-management
npm run dev -- --host 127.0.0.1 --port 4174 --strictPort

cd /home/djdev/leid/yiti
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 open http://127.0.0.1:4174 --browser chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 resize 1440 900
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 select e79 branchSecretary
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 screenshot --filename prototypes/red-engine-task-management/evidence/2026-08-25-role-adjustment-playwright/01-branch-secretary-review-workbench.png --full-page
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 select e79 orgReviewer
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 screenshot --filename prototypes/red-engine-task-management/evidence/2026-08-25-role-adjustment-playwright/02-organization-reviewer-workbench.png --full-page
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 select e79 employee
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 screenshot --filename prototypes/red-engine-task-management/evidence/2026-08-25-role-adjustment-playwright/03-employee-home.png --full-page
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 console warning
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-20260825 requests --static
```

本轮本地 `4173` 端口已被其他监听占用，验收预览改用 `4174`；这不修改原型配置或 Windows 启动脚本。

## 页面与权限结果

- 支部书记：导航只有“支部审核工作台”，页面标题与说明均显示支部书记专属入口。
- 组织审核员：导航只有“工作台”，页面说明明确该入口由原沉浸式审核工作台更名。
- 一线员工：导航显示“首页”“四大维度材料上报”“上报记录”“任务处理”，首页待办仍正常展示。
- 组织管理员：不显示支部书记或组织审核员专属工作台，默认进入任务管理。

## 路由、请求与控制台

- `route-list`: `No active routes`，没有注册开发态 mock 路由。
- 请求共 16 条，均为 `127.0.0.1:4174` 的 Vite/React/源码/favicon 静态资源，全部 `200`；没有 `/api` 或后端请求。
- console: `Total messages: 3 (Errors: 0, Warnings: 0)`，warning 级别返回 0 条。

## 截图

- `01-branch-secretary-review-workbench.png`：支部书记仅显示支部审核工作台。
- `02-organization-reviewer-workbench.png`：组织审核员仅显示更名后的工作台。
- `03-employee-home.png`：一线员工首页待办入口。
- `04-source-branch-comparison.png`：既有视觉基线与支部书记页面同图比较。
- `05-source-organization-comparison.png`：既有视觉基线与组织审核员页面同图比较。

结论：本轮角色合并、工作台更名及入口隔离均通过官方 `playwright-cli` 真实页面验收；原型仍为纯前端静态演示，非 RBAC/API 联调。
