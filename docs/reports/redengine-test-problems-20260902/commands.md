# 官方 playwright-cli 命令摘要

所有命令均在 `xanzc_frontend/` 下执行。登录凭据只在浏览器交互中输入，证据文件不保存密码、Cookie、Session 或请求头。

```bash
env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=<role-session> \
  goto 'http://127.0.0.1:8093/#/login?normal'

env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=<role-session> snapshot

env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=<role-session> tab-list

env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=<role-session> requests --filter '/api/re/'

env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=<role-session> route-list

env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=<role-session> console
```

实际会话为 `round2-reporter`、`round2-secretary`、`round2-org-final`。实际访问路由为：

- 报送员：`/redengine/dashboard`、`/redengine/records`、`/redengine/task-entry?...`
- 支部书记：`/redengine/branch-review`
- 组织审核员：`/redengine/review`、`/redengine/task-management/new`

任务标题点击前后 `tab-list` 均为 5 个历史页签，当前页签从 `/redengine/records` 变为 `/redengine/task-entry?...`，没有创建第 6 个页签。
