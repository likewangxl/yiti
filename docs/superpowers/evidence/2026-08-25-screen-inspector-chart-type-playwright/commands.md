# 实际执行命令

以下命令在 `xanzc_frontend` 目录执行。长 JSON 按用途概括；注册路由见 `routes.raw.txt`，请求/响应见 `network.raw.txt`。

```bash
npx playwright-cli -s=screen-inspector-type open 'http://127.0.0.1:8091/' --browser=chromium
npx playwright-cli -s=screen-inspector-type route '**/api/auth/current-user' --content-type 'application/json' --body '<验收用户 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/auth/my-menus' --content-type 'application/json' --body '<空菜单 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/auth/permissions' --content-type 'application/json' --body '<大屏管理权限 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/screen/admin/screens' --content-type 'application/json' --body '<验收屏列表 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/screen/admin/canvas/9125' --content-type 'application/json' --body '<包含 BAR_COMPARE 和 LINE_TREND 的画布 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/screen/admin/screens/9125/map-region-metrics' --content-type 'application/json' --body '<空区域指标 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/screen/admin/datasources*' --content-type 'application/json' --body '<验收数据源 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/admin/org-groups*' --content-type 'application/json' --body '<空机构组 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/admin/org-profiles*' --content-type 'application/json' --body '<空机构画像 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/admin/roles/all*' --content-type 'application/json' --body '<空角色 JSON>'
npx playwright-cli -s=screen-inspector-type route '**/api/screen/data' --content-type 'application/json' --body '<验收数据 JSON>'
npx playwright-cli -s=screen-inspector-type goto 'http://127.0.0.1:8091/#/screen-admin/designer'
npx playwright-cli -s=screen-inspector-type resize 1920 1080
npx playwright-cli -s=screen-inspector-type snapshot
npx playwright-cli -s=screen-inspector-type console --clear
npx playwright-cli -s=screen-inspector-type requests --clear
npx playwright-cli -s=screen-inspector-type click e331
npx playwright-cli -s=screen-inspector-type snapshot
npx playwright-cli -s=screen-inspector-type screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-inspector-chart-type-playwright/01-bar-compare-selected.png' --full-page
npx playwright-cli -s=screen-inspector-type click e334
npx playwright-cli -s=screen-inspector-type snapshot
npx playwright-cli -s=screen-inspector-type screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-inspector-chart-type-playwright/02-line-trend-selected.png' --full-page
npx playwright-cli -s=screen-inspector-type route-list
npx playwright-cli -s=screen-inspector-type console
npx playwright-cli -s=screen-inspector-type requests --filter '/api/'
npx playwright-cli -s=screen-inspector-type request 1
npx playwright-cli -s=screen-inspector-type response-body 1
npx playwright-cli -s=screen-inspector-type request 2
npx playwright-cli -s=screen-inspector-type response-body 2
npx playwright-cli -s=screen-inspector-type request 3
npx playwright-cli -s=screen-inspector-type response-body 3
npx playwright-cli -s=screen-inspector-type request 4
npx playwright-cli -s=screen-inspector-type response-body 4
npx playwright-cli -s=screen-inspector-type close
```

首次打开根页面产生的未登录 Console 记录发生在 mock route 注册前；验收边界在清空 Console 和请求记录后开始。
