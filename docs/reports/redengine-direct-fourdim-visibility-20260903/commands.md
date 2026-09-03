# 官方 Playwright CLI 命令

工作目录：`/home/djdev/leid/yiti/xanzc_frontend`

工具：项目内官方 `playwright-cli 0.1.18`，Chromium，视口 `1440x900`。

以下为实际命令的脱敏形式；登录口令未归档：

```bash
export PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-direct-fourdim-3
export PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright

./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence open \
  'http://127.0.0.1:8091/#/login?normal' --browser=chromium
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence resize 1440 900
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence run-code \
  '<填写 wangw67 和脱敏口令，点击登录，等待离开登录页>'
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence console --clear
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence requests --clear
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence goto \
  'http://127.0.0.1:8091/#/redengine/dashboard'
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence run-code \
  '<读取联建规范度待办和任务处理菜单，点击最新待办，等待 submitId=4 当前页弹窗>'
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence screenshot \
  --filename '/home/djdev/leid/yiti/docs/reports/redengine-direct-fourdim-visibility-20260903/screenshots/wangw67-dashboard-material-todos.png' \
  --full-page
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence screenshot \
  --filename '/home/djdev/leid/yiti/docs/reports/redengine-direct-fourdim-visibility-20260903/screenshots/wangw67-submit-4-detail.png' \
  --full-page
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence route-list
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence console error
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence console warning
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence requests --filter '/api/re/'
./node_modules/.bin/playwright-cli -s=direct-fourdim-evidence close
```

关键浏览器断言原始结果：

```json
{"url":"http://127.0.0.1:8091/#/redengine/dashboard","todoLabels":["联建规范度PENDING--","联建规范度PENDING--"],"taskProcessingMenuCount":1}
{"url":"http://127.0.0.1:8091/#/redengine/branch-review?tab=pending&source=material&submitId=4&status=PENDING","heading":"任务处理","taskSection":"任务填报","materialSection":"四大维度材料上报","dialogTitle":"四大维度材料详情","containsSubmittedText":true}
```
