# Playwright CLI 命令摘要

```bash
node_modules/.bin/playwright-cli -s=screen-paste-blockid open about:blank --browser chromium
node_modules/.bin/playwright-cli -s=screen-paste-blockid route-list
node_modules/.bin/playwright-cli -s=screen-paste-blockid goto http://127.0.0.1:8091/#/screen-admin/designer
node_modules/.bin/playwright-cli -s=screen-paste-blockid snapshot
node_modules/.bin/playwright-cli -s=screen-paste-blockid request-body 247
node_modules/.bin/playwright-cli -s=screen-paste-blockid requests
node_modules/.bin/playwright-cli -s=screen-paste-blockid console
node_modules/.bin/playwright-cli -s=screen-paste-blockid screenshot --filename=../docs/superpowers/evidence/2026-08-26-screen-paste-blockid/01-pasted-chart-awaits-new-block.png
node_modules/.bin/playwright-cli -s=screen-paste-blockid screenshot --filename=../docs/superpowers/evidence/2026-08-26-screen-paste-blockid/02-saved-with-new-block.png
```

关键页面断言：

```json
{"shapeCount":2,"shapes":[{"text":"待复制指标卡存款余额123,456","left":"120px","top":"120px"},{"text":"已选择数据源，保存后预览","left":"140px","top":"140px"}],"placeholderCount":1}
```

```json
{"status":"已保存","shapeCount":2,"placeholderCount":0,"values":2}
```
