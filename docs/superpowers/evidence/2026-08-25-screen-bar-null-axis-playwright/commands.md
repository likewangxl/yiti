# 实际执行命令

以下命令在 `xanzc_frontend` 目录执行。路由响应均为本次命令中内联的 JSON；长 JSON 在此按用途概括，完整取数请求/响应见 `network.raw.txt`，完整页面结果见截图。

```bash
npx playwright-cli -s=screen-bar-null-axis-final open 'http://127.0.0.1:8091/' --browser=chromium
npx playwright-cli -s=screen-bar-null-axis-final route '**/api/auth/current-user' --content-type 'application/json' --body '<验收用户 JSON>'
npx playwright-cli -s=screen-bar-null-axis-final route '**/api/auth/my-menus' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-bar-null-axis-final route '**/api/auth/permissions' --content-type 'application/json' --body '<大屏查看权限 JSON>'
npx playwright-cli -s=screen-bar-null-axis-final route '**/api/screen/view/SCR_PROVINCE*' --content-type 'application/json' --body '<state=draft 的省分行经营总览草稿包：BAR_COMPARE blockId=32，items 含七个 col/label>'
npx playwright-cli -s=screen-bar-null-axis-final route '**/api/screen/data' --content-type 'application/json' --body '<单行多指标 JSON：第一项 null，其余六项有值>'
npx playwright-cli -s=screen-bar-null-axis-final goto 'http://127.0.0.1:8091/#/screen/SCR_PROVINCE?preview=draft'
npx playwright-cli -s=screen-bar-null-axis-final resize 1920 1080
npx playwright-cli -s=screen-bar-null-axis-final snapshot
npx playwright-cli -s=screen-bar-null-axis-final console --clear
npx playwright-cli -s=screen-bar-null-axis-final requests --clear
npx playwright-cli -s=screen-bar-null-axis-final reload
npx playwright-cli -s=screen-bar-null-axis-final snapshot
npx playwright-cli -s=screen-bar-null-axis-final screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-bar-null-axis-playwright/01-annual-deposit-label-axis.png' --full-page
npx playwright-cli -s=screen-bar-null-axis-final route-list
npx playwright-cli -s=screen-bar-null-axis-final console
npx playwright-cli -s=screen-bar-null-axis-final requests
npx playwright-cli -s=screen-bar-null-axis-final request 214
npx playwright-cli -s=screen-bar-null-axis-final request-body 214
npx playwright-cli -s=screen-bar-null-axis-final response-body 214
npx playwright-cli -s=screen-bar-null-axis-final close
```

首次打开根页面发生的未登录请求在 mock route 注册前产生；按验收边界清空 Console 和请求记录后重新加载目标草稿页，最终记录为 0 error、0 warning。
