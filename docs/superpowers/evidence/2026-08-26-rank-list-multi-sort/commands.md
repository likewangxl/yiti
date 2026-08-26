# Playwright CLI 原始命令

```bash
node_modules/.bin/playwright-cli -s=rank-list-multi snapshot

node_modules/.bin/playwright-cli -s=rank-list-multi run-code "async (page) => { const block = page.locator('.scr-block').first(); const before = await page.locator('.rl-name').allTextContents(); const status = await page.getByTestId('rank-sort-status').textContent(); const metrics = await page.getByTestId('rank-metric-value').allTextContents(); const styles = await block.evaluate((el) => { const b = getComputedStyle(el); const body = getComputedStyle(el.querySelector('.scr-block-body')); const wrap = el.querySelector('.rl-wrap'); return { blockDisplay: b.display, blockHeight: b.height, bodyHeight: body.height, bodyOverflow: body.overflow, wrapHeight: getComputedStyle(wrap).height, clientHeight: wrap.clientHeight }; }); await page.waitForTimeout(2200); const after = await page.locator('.rl-name').allTextContents(); return { status, before, after, changed: JSON.stringify(before) !== JSON.stringify(after), visibleRows: before.length, metricValueCount: metrics.length, styles }; }"

node_modules/.bin/playwright-cli -s=rank-list-multi run-code "async (page) => { const growth = page.getByTestId('rank-metric-button').filter({ hasText: '增幅' }); await growth.click(); const desc = { status: (await page.getByTestId('rank-sort-status').textContent()).trim(), names: await page.locator('.rl-name').allTextContents(), values: await page.getByTestId('rank-metric-value').allTextContents() }; await growth.click(); const asc = { status: (await page.getByTestId('rank-sort-status').textContent()).trim(), names: await page.locator('.rl-name').allTextContents(), values: await page.getByTestId('rank-metric-value').allTextContents() }; return { desc, asc }; }"

node_modules/.bin/playwright-cli -s=rank-list-multi console
node_modules/.bin/playwright-cli -s=rank-list-multi route-list
node_modules/.bin/playwright-cli -s=rank-list-multi requests
node_modules/.bin/playwright-cli -s=rank-list-multi request 1199
node_modules/.bin/playwright-cli -s=rank-list-multi request-body 1199
node_modules/.bin/playwright-cli -s=rank-list-multi response-body 1199
node_modules/.bin/playwright-cli -s=rank-list-multi screenshot --filename=../docs/superpowers/evidence/2026-08-26-rank-list-multi-sort/02-growth-ascending-and-scroll.png
```

## 关键运行结果

```json
{
  "status": "当前排序：存款余额（倒序）",
  "before": ["丙机构", "戊机构", "甲机构", "庚机构", "壬机构", "子机构"],
  "after": ["戊机构", "甲机构", "庚机构", "壬机构", "子机构", "乙机构"],
  "changed": true,
  "visibleRows": 6,
  "metricValueCount": 12,
  "styles": {
    "blockDisplay": "flex",
    "blockHeight": "260px",
    "bodyHeight": "239px",
    "bodyOverflow": "hidden",
    "wrapHeight": "239px",
    "clientHeight": 239
  }
}
```
```json
{
  "desc": {
    "status": "当前排序：增幅（倒序）",
    "names": ["己机构", "丑机构", "乙机构", "辛机构", "丁机构", "癸机构"]
  },
  "asc": {
    "status": "当前排序：增幅（正序）",
    "names": ["丙机构", "戊机构", "甲机构", "子机构", "庚机构", "壬机构"]
  }
}
```
