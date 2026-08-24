# 实际执行命令

以下命令均在 `xanzc_frontend` 目录执行。

```powershell
npx playwright-cli -s=screen-save-fix unroute '<pattern>'
npx playwright-cli -s=screen-save-fix route '<pattern>' --content-type 'application/json' --body '<json>'
npx playwright-cli -s=screen-save-fix goto 'http://127.0.0.1:8092/#/login?normal'
npx playwright-cli -s=screen-save-fix goto 'http://127.0.0.1:8092/#/screen-admin/designer'
npx playwright-cli -s=screen-save-fix reload
npx playwright-cli -s=screen-save-fix snapshot
npx playwright-cli -s=screen-save-fix click f2e137
npx playwright-cli -s=screen-save-fix click f2e303
npx playwright-cli -s=screen-save-fix screenshot --filename '..\docs\superpowers\evidence\2026-08-24-screen-datasource-scope-playwright\01-filtered-options.png' --full-page
npx playwright-cli -s=screen-save-fix requests --clear
npx playwright-cli -s=screen-save-fix console --clear
npx playwright-cli -s=screen-save-fix click f2e378
npx playwright-cli -s=screen-save-fix click f2e38
npx playwright-cli -s=screen-save-fix snapshot
npx playwright-cli -s=screen-save-fix screenshot --filename '..\docs\superpowers\evidence\2026-08-24-screen-datasource-scope-playwright\02-valid-save.png' --full-page
npx playwright-cli -s=screen-save-fix route-list
npx playwright-cli -s=screen-save-fix console
npx playwright-cli -s=screen-save-fix requests --filter '/api/'
npx playwright-cli -s=screen-save-fix request-body 1
npx playwright-cli -s=screen-save-fix response-body 1
```

说明：13 条 route 的完整 URL pattern 和关键响应见 `routes.txt`。它们是仅开发态 mock，不代表真实后端联调。
