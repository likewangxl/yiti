# 实际执行命令

以下命令均在 `xanzc_frontend` 目录执行。route 的完整业务响应摘要与最终请求/响应见 `network.raw.txt`。

```bash
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 open 'http://127.0.0.1:8091/' --browser=chromium
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 resize 1920 1080
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 route '**/api/auth/current-user' --status 200 --content-type application/json --body '<开发态验收用户 ResponseWrapper>'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 route '**/api/auth/my-menus' --status 200 --content-type application/json --body '<空菜单 ResponseWrapper>'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 route '**/api/auth/permissions' --status 200 --content-type application/json --body '<仅大屏读取权限 ResponseWrapper>'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 route '**/api/screen/view/SCR_BAR_EVEN_QA*' --status 200 --content-type application/json --body '<窄/宽两个 BAR_COMPARE 草稿渲染包>'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 route '**/api/screen/data' --status 200 --content-type application/json --body '<5 指标、6 类目数据>'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 goto 'http://127.0.0.1:8091/#/screen/SCR_BAR_EVEN_QA?preview=draft'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 console --clear
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 requests --clear
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 reload
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 eval '<读取实际类目轴长度、barWidth、barGap，并计算组内与跨类目间距>'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 snapshot
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 screenshot --filename '/home/djdev/lf/yiti/docs/superpowers/evidence/2026-08-27-screen-bar-even-spacing/bar-even-spacing.png' --full-page
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 route-list
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 console
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 requests --filter '/api/'
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 request 214
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 response-body 214
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 request-body 215
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 response-body 215
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 request-body 216
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 response-body 216
./node_modules/.bin/playwright-cli -s=screen-bar-even-20260827 close
```

首次打开根页面产生的未登录记录发生在 mock route 注册前；验收边界从清空 Console 和请求记录并重新加载目标草稿页后开始。
