# 审核角色权限继承 Playwright CLI 验收证据

## 最终权限口径

- 报送员：`首页`、`四大维度材料上报`、`上报记录`、`任务处理`。
- 支部书记：完整继承报送员上述四个入口，并增加`支部审核工作台`。
- 组织管理员：保留原型既有管理员菜单。
- 组织审核员：完整继承组织管理员菜单，并增加由“沉浸式审核工作台”更名的`工作台`。
- “支部审核员”不再作为独立演示身份。

## 实际运行与验收命令

```bash
cd /home/djdev/leid/yiti/prototypes/red-engine-task-management
npm run dev -- --host 127.0.0.1 --port 4175 --strictPort

cd /home/djdev/leid/yiti
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 open http://127.0.0.1:4175 --browser chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 resize 1440 900
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 select e79 branchSecretary
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 click e11
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 find '欢迎回来，支部书记'
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 select e79 orgReviewer
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 click e457
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 find '组织管理员可发布并查看本组织已发布任务'
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 select e79 reporter
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 console warning
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-role-inheritance-20260825 requests --static
```

本轮 `4173`、`4174` 已被其他监听占用，验收使用 `4175`；未修改 Windows 启动脚本和原型默认配置。

## 页面结果

- 支部书记菜单实际包含 5 项：4 项报送员菜单加“支部审核工作台”；点击继承得到的“首页”后显示“欢迎回来，支部书记”。
- 组织审核员菜单实际包含组织管理员全部 11 项及新增“工作台”，共 12 项；点击继承得到的“任务管理”可正常进入管理员任务列表。
- 报送员演示身份已替代“一线员工”，默认进入首页并保留原有任务待办。

## 路由、请求与控制台

- `route-list`: `No active routes`，未注册 mock route。
- 16 条请求均为 `127.0.0.1:4175` 的 Vite、React、源码与 favicon 静态资源，全部 `200`；没有 `/api` 请求。
- console: `Total messages: 3 (Errors: 0, Warnings: 0)`，warning 级别返回 0 条。

## 截图与同图比较

- `01-branch-secretary-inherits-reporter.png`：支部书记的报送员继承菜单与新增审核入口。
- `02-organization-reviewer-inherits-admin.png`：组织审核员的管理员继承菜单与新增工作台。
- `03-reporter-home.png`：报送员首页基线。
- `04-organization-reviewer-task-management.png`：组织审核员进入继承的任务管理页面。
- `05-branch-secretary-home.png`：支部书记进入继承的首页。
- `06-reporter-secretary-comparison.png`：报送员与支部书记同为首页状态的对比。
- `07-admin-reviewer-comparison.png`：组织管理员与组织审核员同为任务管理状态的对比。

结论：两个审核角色均按“基础角色权限 + 专属工作台”继承，页面与交互验收通过；仍为纯前端权限演示，非真实 RBAC/API 联调。
