# 官方 playwright-cli 命令记录

```text
PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright
CLI=/home/djdev/.npm/_npx/9b853437c4cd15c0/node_modules/.bin/playwright-cli
SESSION=pageopt2-pass-2560-4f5d9ceb
```

关键命令：

```bash
$CLI -s=$SESSION open --browser chromium http://127.0.0.1:8091/
$CLI -s=$SESSION resize 2560 1440
$CLI -s=$SESSION route-list --json
$CLI -s=$SESSION run-code --filename /home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560/raw/scan-59-shell-actions.js
$CLI -s=$SESSION run-code --filename /home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560/raw/tabs-focus-intents.js
$CLI -s=$SESSION run-code --filename /home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560/raw/row-actions-focus-scheme.js
$CLI -s=$SESSION run-code --filename /home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560/raw/designer-readonly.js
$CLI -s=$SESSION route-list --json
$CLI -s=$SESSION console --json
$CLI -s=$SESSION requests --json
$CLI -s=$SESSION close
```

normal admin 登录通过 CLI snapshot 引用执行；登录 fill/click 命令为避免凭据落盘不归档。登录 POST 是本轮唯一允许的写请求。一次页签脚本重跑用于修正证据断言中的真实类名；一次行操作脚本重跑用于避免 Escape 同时关闭 Scheme 父对话框。两次均无业务写和产品错误。
