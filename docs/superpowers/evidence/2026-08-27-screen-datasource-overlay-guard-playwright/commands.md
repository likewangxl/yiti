# 实际执行命令

以下命令在 `xanzc_frontend/` 目录执行。route 响应为页面加载所需的最小 JSON，精确数据源响应见 `network.raw.txt`。

```bash
npx playwright-cli -s=screen-datasource-overlay open about:blank --browser=chromium
npx playwright-cli -s=screen-datasource-overlay resize 1920 1080
npx playwright-cli -s=screen-datasource-overlay route '**/api/auth/current-user' --status 200 --content-type application/json --body '<验收用户 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/auth/my-menus' --status 200 --content-type application/json --body '<大屏数据源菜单 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/auth/permissions' --status 200 --content-type application/json --body '<数据源管理权限 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/screen/admin/datasources' --status 200 --content-type application/json --body '<9012 数据源列表 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/perf/metrics*' --status 200 --content-type application/json --body '<机构指标 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/screen/admin/kpi-schemes*' --status 200 --content-type application/json --body '<空 KPI 方案 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/admin/org-groups*' --status 200 --content-type application/json --body '<空机构组 JSON>'
npx playwright-cli -s=screen-datasource-overlay route '**/api/notifications/unread-count' --status 200 --content-type application/json --body '<未读数 0 JSON>'
npx playwright-cli -s=screen-datasource-overlay goto 'http://127.0.0.1:8091/#/screen-admin/datasources'
npx playwright-cli -s=screen-datasource-overlay find '编辑'
npx playwright-cli -s=screen-datasource-overlay run-code 'async page => ({url:page.url(), rows:await page.locator(".el-table__row").count(), editButtons:await page.getByText("编辑", {exact:true}).count()})'
npx playwright-cli -s=screen-datasource-overlay run-code '<点击可见编辑按钮，将名称改为“日均存款详图-未保存”，返回弹窗状态>'
npx playwright-cli -s=screen-datasource-overlay run-code '<在遮罩坐标 200,500 单击，等待 300ms，返回弹窗状态>'
mkdir -p ../docs/superpowers/evidence/2026-08-27-screen-datasource-overlay-guard-playwright
npx playwright-cli -s=screen-datasource-overlay screenshot --filename ../docs/superpowers/evidence/2026-08-27-screen-datasource-overlay-guard-playwright/01-editor-remains-open-after-backdrop-click.png
npx playwright-cli -s=screen-datasource-overlay route-list
npx playwright-cli -s=screen-datasource-overlay console
npx playwright-cli -s=screen-datasource-overlay console debug
npx playwright-cli -s=screen-datasource-overlay requests --filter '/api/'
npx playwright-cli -s=screen-datasource-overlay request 59
npx playwright-cli -s=screen-datasource-overlay response-body 59
npx playwright-cli -s=screen-datasource-overlay close
```

首次截图命令在证据目录尚未建立时返回 `ENOENT`；建立精确目录后使用同一会话重试成功，不影响页面交互结论。
