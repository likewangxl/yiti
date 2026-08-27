# 官方 CLI 验收命令

以下命令在 `xanzc_frontend/` 执行。登录密码仅通过临时环境变量传入，未写入证据文件。

```bash
PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix open \
  'http://127.0.0.1:8091/#/login?normal'

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix fill e34 admin

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix fill e42 "${SCREEN_E2E_PASSWORD}"

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix click e43

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix goto \
  'http://127.0.0.1:8091/#/screen-admin/designer'

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix requests --filter '/api/screen/data'

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix route-list

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix console

PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome \
  node_modules/.bin/playwright-cli -s=schema-version-fix screenshot \
  --filename ../docs/superpowers/evidence/2026-08-27-screen-schema-version-fix/designer-corp-overview.png \
  --full-page
```

页面状态检查使用 CLI 的 `run-code`，结果为：

```json
{
  "screen": "当前大屏\n对公经营总览（全辖）\nSCR_CORP_OVERVIEW",
  "errors": [],
  "rankingRows": 6
}
```
