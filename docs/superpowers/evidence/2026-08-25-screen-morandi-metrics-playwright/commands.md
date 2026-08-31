# 实际执行命令

以下命令在 `xanzc_frontend` 目录执行。长 JSON 在此按用途概括；请求/响应摘要见 `network.raw.txt`，最终视觉结果见两张截图。

## 11 指标柱状图

```bash
npx playwright-cli -s=screen-morandi-bar open 'http://127.0.0.1:8091/' --browser=chromium
npx playwright-cli -s=screen-morandi-bar route '**/api/auth/current-user' --content-type 'application/json' --body '<验收用户 JSON>'
npx playwright-cli -s=screen-morandi-bar route '**/api/auth/my-menus' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-morandi-bar route '**/api/auth/permissions' --content-type 'application/json' --body '<大屏查看权限 JSON>'
npx playwright-cli -s=screen-morandi-bar route '**/api/screen/view/SCR_PROVINCE*' --content-type 'application/json' --body '<state=draft、BAR_COMPARE、11 items 的草稿包>'
npx playwright-cli -s=screen-morandi-bar route '**/api/screen/data' --content-type 'application/json' --body '<m01..m11 单行数据 JSON>'
npx playwright-cli -s=screen-morandi-bar goto 'http://127.0.0.1:8091/#/screen/SCR_PROVINCE?preview=draft'
npx playwright-cli -s=screen-morandi-bar resize 1920 1080
npx playwright-cli -s=screen-morandi-bar console --clear
npx playwright-cli -s=screen-morandi-bar requests --clear
npx playwright-cli -s=screen-morandi-bar reload
npx playwright-cli -s=screen-morandi-bar snapshot
npx playwright-cli -s=screen-morandi-bar screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-morandi-metrics-playwright/01-bar-11-metrics.png' --full-page
npx playwright-cli -s=screen-morandi-bar route-list
npx playwright-cli -s=screen-morandi-bar console
npx playwright-cli -s=screen-morandi-bar requests
npx playwright-cli -s=screen-morandi-bar close
```

## 11 指标折线图

```bash
npx playwright-cli -s=screen-morandi-line open 'http://127.0.0.1:8091/' --browser=chromium
npx playwright-cli -s=screen-morandi-line route '**/api/auth/current-user' --content-type 'application/json' --body '<验收用户 JSON>'
npx playwright-cli -s=screen-morandi-line route '**/api/auth/my-menus' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-morandi-line route '**/api/auth/permissions' --content-type 'application/json' --body '<大屏查看权限 JSON>'
npx playwright-cli -s=screen-morandi-line route '**/api/screen/view/SCR_PROVINCE*' --content-type 'application/json' --body '<state=draft、LINE_TREND、11 items 的草稿包>'
npx playwright-cli -s=screen-morandi-line route '**/api/screen/data' --content-type 'application/json' --body '<月份+m01..m11 四行数据 JSON>'
npx playwright-cli -s=screen-morandi-line goto 'http://127.0.0.1:8091/#/screen/SCR_PROVINCE?preview=draft'
npx playwright-cli -s=screen-morandi-line resize 1920 1080
npx playwright-cli -s=screen-morandi-line console --clear
npx playwright-cli -s=screen-morandi-line requests --clear
npx playwright-cli -s=screen-morandi-line reload
npx playwright-cli -s=screen-morandi-line snapshot
npx playwright-cli -s=screen-morandi-line screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-morandi-metrics-playwright/02-line-11-metrics.png' --full-page
npx playwright-cli -s=screen-morandi-line route-list
npx playwright-cli -s=screen-morandi-line console
npx playwright-cli -s=screen-morandi-line requests
npx playwright-cli -s=screen-morandi-line close
```

两次首次打开根页面产生的未登录请求均在 mock route 注册前发生；验收边界在清空 Console 和请求记录后重新加载目标草稿页。
