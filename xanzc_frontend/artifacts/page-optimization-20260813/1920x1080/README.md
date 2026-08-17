# 1920×1080 会话结果

- 使用项目官方 `node_modules/.bin/playwright-cli` 的 Chromium 会话。
- 登录前 route-list 与关闭前 route-list 均为 `No active routes`；没有注册 mock route。
- 账号/密码输入框在 `/#/login?normal` 均存在、已填写，提交按钮可用；实际值未归档。
- 清空 console/network 后仅做一次明确 locator UI 登录重试；请求以 `net::ERR_ABORTED` 失败，未进入工作台。
- console 为 0 error / 0 warning；未见 `[api fallback]` 或意外业务写请求。
- 因登录阻断，59 条普通后台路由和关键页面截图均未开始。唯一截图为填写凭据前的空登录表单，用于证明真实浏览器前置界面，不作为业务页验收截图。
- 会话在最后一次无 mock route 核验后已关闭。
