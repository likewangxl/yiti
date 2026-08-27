# 实际执行命令

以下命令在 `xanzc_frontend` 目录执行。长 JSON 按用途概括，请求/响应摘要见 `network.raw.txt`。

```bash
npx playwright-cli -s=screen-horizontal-colors open 'http://127.0.0.1:8091/' --browser=chromium
npx playwright-cli -s=screen-horizontal-colors route '**/api/auth/current-user' --content-type 'application/json' --body '<验收用户 JSON>'
npx playwright-cli -s=screen-horizontal-colors route '**/api/auth/my-menus' --content-type 'application/json' --body '<空菜单 JSON>'
npx playwright-cli -s=screen-horizontal-colors route '**/api/auth/permissions' --content-type 'application/json' --body '<大屏读权限 JSON>'
npx playwright-cli -s=screen-horizontal-colors route '**/api/screen/view/SCR_HORIZONTAL*' --content-type 'application/json' --body '<BAR_COMPARE + barMode=horizontal 草稿包>'
npx playwright-cli -s=screen-horizontal-colors route '**/api/screen/data' --content-type 'application/json' --body '<11 行类目与指标数据 JSON>'
npx playwright-cli -s=screen-horizontal-colors goto 'http://127.0.0.1:8091/#/screen/SCR_HORIZONTAL?preview=draft'
npx playwright-cli -s=screen-horizontal-colors resize 1920 1080
npx playwright-cli -s=screen-horizontal-colors console --clear
npx playwright-cli -s=screen-horizontal-colors requests --clear
npx playwright-cli -s=screen-horizontal-colors reload
npx playwright-cli -s=screen-horizontal-colors snapshot
npx playwright-cli -s=screen-horizontal-colors screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-horizontal-bar-colors-playwright/01-horizontal-11-bars.png' --full-page
npx playwright-cli -s=screen-horizontal-colors route-list
npx playwright-cli -s=screen-horizontal-colors console
npx playwright-cli -s=screen-horizontal-colors requests
npx playwright-cli -s=screen-horizontal-colors request 213
npx playwright-cli -s=screen-horizontal-colors response-body 213
npx playwright-cli -s=screen-horizontal-colors request 214
npx playwright-cli -s=screen-horizontal-colors response-body 214
npx playwright-cli -s=screen-horizontal-colors close
```

首次打开根页面产生的未登录 Console 记录发生在 mock route 注册前；验收边界从清空 Console 和请求记录并重新加载草稿页后开始。
