# 实际执行命令

以下命令均在 `xanzc_frontend` 目录执行。

```powershell
npx playwright-cli -s=screen-save-fix open 'http://127.0.0.1:8092/#/login?normal'
npx playwright-cli -s=screen-save-fix route '<pattern>' --content-type 'application/json' --body '<json>'
npx playwright-cli -s=screen-save-fix route-list
npx playwright-cli -s=screen-save-fix run-code 'async (page) => { /* 写入开发态用户快照并打开设计器 */ }'
npx playwright-cli -s=screen-save-fix console --clear
npx playwright-cli -s=screen-save-fix reload
npx playwright-cli -s=screen-save-fix snapshot
npx playwright-cli -s=screen-save-fix screenshot --filename '..\docs\superpowers\evidence\2026-08-24-screen-save-binding-playwright\01-before-save.png' --full-page
npx playwright-cli -s=screen-save-fix requests --clear
npx playwright-cli -s=screen-save-fix console --clear
npx playwright-cli -s=screen-save-fix click f1e38
npx playwright-cli -s=screen-save-fix snapshot
npx playwright-cli -s=screen-save-fix screenshot --filename '..\docs\superpowers\evidence\2026-08-24-screen-save-binding-playwright\02-after-save.png' --full-page
npx playwright-cli -s=screen-save-fix requests --filter '/api/'
npx playwright-cli -s=screen-save-fix request-body 1
npx playwright-cli -s=screen-save-fix response-body 1
npx playwright-cli -s=screen-save-fix console
```

说明：13 条 route 的完整 URL pattern 和关键权限失败响应见 `routes.txt`；它们是仅开发态 mock，不代表真实后端联调。
