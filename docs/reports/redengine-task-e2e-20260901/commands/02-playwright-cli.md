# Playwright CLI 准备

## 现场命令

```text
./node_modules/.bin/playwright-cli --version
./node_modules/.bin/playwright-cli --help screenshot
./node_modules/.bin/playwright-cli --help open
./node_modules/.bin/playwright-cli --help route
./node_modules/.bin/playwright-cli --help route-list
./node_modules/.bin/playwright-cli --help requests
./node_modules/.bin/playwright-cli --help console
PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright ./node_modules/.bin/playwright-cli -s=redengine-prep open about:blank --browser chromium
PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright ./node_modules/.bin/playwright-cli -s=redengine-prep snapshot
PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright ./node_modules/.bin/playwright-cli -s=redengine-prep screenshot --filename=/home/djdev/leid/yiti/docs/reports/redengine-task-e2e-20260901/screenshots/playwright-cli-about-blank.png
```

## 结果

- CLI 版本：`0.1.18`。
- `open --browser chromium about:blank` 成功，说明官方 CLI 可以使用系统已有的 Chromium 1237 缓存；该缓存位于 `/home/djdev/.cache/ms-playwright`。
- CLI daemon 默认目录在受限环境不可写，因此本次使用 `PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon`；这不是业务配置。
- 通过官方 CLI 生成准备截图：`screenshots/playwright-cli-about-blank.png`。
- 按计划尝试下载到 `/tmp/redengine-playwright-browsers` 时因文件系统剩余空间约 189 MB、下载包约 114 MB 且需解压，报 `ENOSPC`；没有删除已有文件。使用已有匹配缓存完成 CLI 启动探测。

业务页面验收仍需等待后端 P1 修复提交和服务启动。
