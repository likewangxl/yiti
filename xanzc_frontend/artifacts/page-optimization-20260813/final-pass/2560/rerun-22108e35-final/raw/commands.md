# 官方 playwright-cli 命令清单（脱敏）

工作目录：`/home/djdev/leid/yiti`。

CLI：`xanzc_frontend/node_modules/.bin/playwright-cli`；浏览器缓存使用既有 `PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright`。

会话步骤：

1. `-s=pageopt-final-2560-22108e35 open http://127.0.0.1:8091/#/login?normal --browser chromium`
2. `resize 2560 1440`
3. `route-list` → `No active routes`
4. `snapshot`，填写普通管理员账号并点击登录；凭据省略且未写入证据。
5. `run-code` 执行 `raw/scan-59.js`，逐路由增量写入 `json/routes-59.json`。
6. `run-code` 执行 PerfMetrics/WorkflowMonitor 专项只读 DOM/hover 探针，写入 `json/targeted-regression.json`。
7. `run-code` 打开 `/screen/SCR_RETAIL_OVERVIEW?preview=draft`，读取标题、四节点、OSM、redengine 隔离、canvas 与截图，写入概览探针记录。
8. `run-code` 同源 GET 草稿视图并仅保留 schema2/mapPackage 最小投影，写入 `json/xian-draft-contract.json`。
9. `run-code --filename raw/xian-drill.js`，从头执行四节点 click/Enter/Space 钻取，逐条验证请求体与响应，写入 `json/xian-drill.json`。
10. `route-list` → `No active routes`；`console error` → 0；`requests` → 所有已列请求 HTTP 200。
11. `close`；本会话关闭。未关闭并行代理的 `final1920xian` 会话，未停止 8091/18081。

全程未执行 `route`、`unroute`、Playwright Test 或 MCP，未注入/修改 DOM 数据，未创建测试数据。登录以外只允许 GET/HEAD/OPTIONS 与 `/api/screen/data` POST。
