# Playwright CLI 原始命令

```bash
export PLAYWRIGHT_MCP_EXECUTABLE_PATH=/home/djdev/.cache/ms-playwright/chromium-1237/chrome-linux64/chrome
node_modules/.bin/playwright-cli -s=rank-no-share open about:blank

# 逐条注册 routes.raw.txt 列出的 12 条 route；所有响应均使用：
node_modules/.bin/playwright-cli -s=rank-no-share route '<pattern>' --status 200 --content-type application/json --body '<routes.raw.txt/network.raw.txt 所记录的 JSON 响应>'

node_modules/.bin/playwright-cli -s=rank-no-share goto 'http://127.0.0.1:8091/#/screen-admin/designer'
node_modules/.bin/playwright-cli -s=rank-no-share snapshot
node_modules/.bin/playwright-cli -s=rank-no-share run-code "async (page) => { await page.getByTestId('rank-sort-status').waitFor(); const names = await page.locator('.rl-name').allTextContents(); const first = page.locator('.rl-name').first(); const firstStyle = await first.evaluate(el => ({ width: getComputedStyle(el).width, clientWidth: el.clientWidth, scrollWidth: el.scrollWidth })); return { names, rowCount: await page.locator('.rl-row').count(), metricValueCount: await page.getByTestId('rank-metric-value').count(), colorBlockCount: await page.locator('.rl-bar, .rl-fill').count(), shareColumnCount: await page.locator('.rl-share').count(), shareTextCount: await page.getByText(/^占比 /).count(), firstColumn: firstStyle, sortStatus: (await page.getByTestId('rank-sort-status').textContent()).trim() }; }"
node_modules/.bin/playwright-cli -s=rank-no-share screenshot --filename=../docs/superpowers/evidence/2026-08-26-rank-list-no-share/01-rank-no-color-share-wide-category.png
node_modules/.bin/playwright-cli -s=rank-no-share route-list
node_modules/.bin/playwright-cli -s=rank-no-share console
node_modules/.bin/playwright-cli -s=rank-no-share requests
node_modules/.bin/playwright-cli -s=rank-no-share request 237
node_modules/.bin/playwright-cli -s=rank-no-share request-body 237
node_modules/.bin/playwright-cli -s=rank-no-share response-body 237
```

## DOM 验收原始结果

```json
{"names":["超长机构名称用于验证第一列加宽展示","甲机构","乙机构","丁机构"],"rowCount":4,"metricValueCount":8,"colorBlockCount":0,"shareColumnCount":0,"shareTextCount":0,"firstColumn":{"width":"140px","clientWidth":140,"scrollWidth":221},"sortStatus":"当前排序：存款余额（倒序）"}
```
