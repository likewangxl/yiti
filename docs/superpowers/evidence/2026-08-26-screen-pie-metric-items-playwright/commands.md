# 实际执行命令

以下命令在仓库根目录执行；长 JSON 按用途概括，精确绑定和取数内容见 `network.raw.txt`。

```bash
xanzc_frontend/node_modules/.bin/playwright-cli --version
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items open 'http://127.0.0.1:8091/' --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items resize 1920 1080

xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/auth/current-user' --body '<验收用户 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/auth/my-menus' --body '<空菜单 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/auth/permissions' --body '<设计器和运行页资源权限 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/screen/admin/screens' --body '<验收大屏列表 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/screen/admin/canvas/9201' --body '<PIE_SHARE 草稿画布 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/screen/admin/screens/9201/map-region-metrics' --body '<空地图指标 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/screen/admin/datasources*' --body '<业务结构聚合数据源 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/admin/org-groups*' --body '<空机构组 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/admin/org-profiles*' --body '<空机构画像 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/admin/roles/all*' --body '<空角色 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route '**/api/screen/data' --body '<两期四列取数 JSON>' --content-type application/json

xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items goto 'http://127.0.0.1:8091/#/screen-admin/designer'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items run-code 'async (page) => { await page.locator(".dsn-shape").click({ position: { x: 20, y: 20 } }); }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items console --clear
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items requests --clear
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items screenshot --filename docs/superpowers/evidence/2026-08-26-screen-pie-metric-items-playwright/01-deposit-loan-selected.png

xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items run-code '<注册 SCR_PIE_METRICS 草稿运行包 route；绑定 deposit、loan>'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items goto 'http://127.0.0.1:8091/#/screen/SCR_PIE_METRICS?preview=draft'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items console --clear
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items requests --clear
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items reload
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items screenshot --filename docs/superpowers/evidence/2026-08-26-screen-pie-metric-items-playwright/02-runtime-selected-metrics.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items console
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items requests --filter '/api/'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items request 213
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items response-body 213
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items request-body 214
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-pie-metric-items response-body 214
```

运行页 route 使用 `run-code` 直接在浏览器上下文注册，因此不出现在 CLI 自身的 `route-list` 注册表；其实际命中由请求 213 的 200 响应和响应体证明。
