# 2560×1440 会话结果

- 使用项目官方 `node_modules/.bin/playwright-cli` 的独立 Chromium 会话。
- 起止 `route-list` 均为 `No active routes`，未注册 mock route。
- 唯一登录 POST 以 `net::ERR_ABORTED` 失败，未进入工作台，故 59 条路由、截图和页面级 network/console 扫描均未开始。
- console 为 0 error / 0 warning；没有意外业务写操作。
- 会话已关闭，未影响 1920×1080 会话。
