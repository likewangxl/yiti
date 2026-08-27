# 实际执行命令

以下命令均在 `xanzc_frontend` 目录执行。route 的完整响应体与最终请求/响应见 `network.raw.txt`。

```bash
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 open 'http://127.0.0.1:8091/' --browser=chromium
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 resize 1920 1080
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 route '**/api/auth/current-user' --status 200 --content-type application/json --body '<开发态验收用户 ResponseWrapper>'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 route '**/api/auth/my-menus' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-menus","data":[]}'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 route '**/api/auth/permissions' --status 200 --content-type application/json --body '<仅大屏读取权限 ResponseWrapper>'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 route '**/api/screen/view/SCR_LABEL_QA*' --status 200 --content-type application/json --body '<COMBO_CHART + BAR_COMPARE 草稿渲染包，完整 body 见 network.raw.txt>'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 route '**/api/screen/data' --status 200 --content-type application/json --body '<month/metric_a/metric_b 四行数据，完整 body 见 network.raw.txt>'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 goto 'http://127.0.0.1:8091/#/screen/SCR_LABEL_QA?preview=draft'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 console --clear
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 requests --clear
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 reload
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 snapshot
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 eval '() => ({url: location.href, comboTitle: [...document.querySelectorAll(".scr-block-h span")].map(node => node.textContent.trim()), comboCharts: document.querySelectorAll(".cc-chart").length, barCharts: document.querySelectorAll(".bc-chart").length, canvases: document.querySelectorAll("canvas").length})'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 screenshot --filename '/home/djdev/lf/yiti/docs/superpowers/evidence/2026-08-27-screen-chart-metric-labels-playwright/chart-metric-labels.png' --full-page
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 route-list
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 console
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 requests --filter '/api/'
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 request 213
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 response-body 213
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 request-body 214
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 response-body 214
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 request-body 215
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 response-body 215
./node_modules/.bin/playwright-cli -s=screen-chart-labels-20260827 close
```

首次打开根页面产生的未登录 Console 记录发生在 mock route 注册前；验收边界从清空 Console 和请求记录并重新加载目标草稿页后开始。

