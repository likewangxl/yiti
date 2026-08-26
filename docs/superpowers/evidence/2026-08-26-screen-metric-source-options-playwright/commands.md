# 实际执行命令

以下命令均在仓库根目录执行。为便于审查，长 JSON 保持与实际 mock 契约一致。

```bash
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options open about:blank --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options resize 1920 1080
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/auth/current-user' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-current","data":{"empId":"E001","username":"metric-options-tester","displayName":"指标候选验收用户","mainOrgCode":"ORG001","roles":[{"roleCode":"SYS_ADMIN"}],"isSystemAdmin":true}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/auth/my-menus' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-menus","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/auth/permissions' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-permissions","data":{"resourceUrls":["/api/screen/admin/screens"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/screen/admin/screens' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screens","data":[{"id":9201,"screenCode":"SCR_METRIC_OPTIONS","screenName":"指标候选合并验收","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":3,"publishStatus":0}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/screen/admin/canvas/9201' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-canvas","data":{"screenId":9201,"screenCode":"SCR_METRIC_OPTIONS","screenName":"指标候选合并验收","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":3,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\"}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[{\"id\":\"metric-card-merge\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"name\":\"存款指标候选验证\",\"blockId\":null,\"bindJson\":\"{\\\"dsId\\\":9020,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"存款指标候选验证\\\"}\",\"drillJson\":\"{}\",\"style\":{\"top\":100,\"left\":150,\"width\":720,\"height\":300},\"isLock\":false,\"isShow\":true,\"propValue\":{}}]}"}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/screen/admin/screens/9201/map-region-metrics' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-map-metrics","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/screen/admin/datasources*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-datasources","data":[{"id":9020,"dsCode":"DS_METRIC_MERGE","dsName":"全省存款指标数据源","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{\"scopeMode\":\"GLOBAL\",\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\"},{\"metricName\":\"存款日均增量\"},{\"metricName\":\"存款客户数\"}],\"fieldMeta\":[{\"col\":\"存款余额\",\"alias\":\"核心存款余额\",\"role\":\"METRIC\"},{\"col\":\"机构\",\"alias\":\"机构名称\",\"role\":\"DIM\"}]}"}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/admin/org-groups*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-groups","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/admin/org-profiles*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-profiles","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/admin/roles/all*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-roles","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/screen/data' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screen-data","data":{"columns":["存款余额","存款日均增量","存款客户数"],"rows":[[100000000,3500000,1280]],"columnsMeta":[{"col":"存款余额","alias":"核心存款余额","role":"METRIC"}]}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route '**/api/notifications/unread-count' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-unread","data":0}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options goto 'http://127.0.0.1:8091/#/screen-admin/designer'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options run-code 'async page => { const t=page.getByText("已选择数据源，保存后预览", {exact:true}); await t.click(); await page.waitForTimeout(500); return await page.locator("[data-testid=chart-metric-columns]").count(); }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options run-code 'async page => { const select=page.locator("[data-testid=chart-metric-columns]"); await select.click(); await page.waitForTimeout(300); return await page.locator(".el-select-dropdown:visible .el-select-dropdown__item").allTextContents(); }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options click e564
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options click e565
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options press Escape
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options run-code 'async page => ({selected:await page.locator("[data-testid=chart-metric-columns] .el-tag__content").evaluateAll(els=>els.map(el=>el.textContent?.trim())), editorRows:await page.locator("[data-testid=chart-metric-item-row]").count(), editorLabels:await page.locator("[data-testid=chart-metric-item-label]").evaluateAll(els=>els.map(el=>el.value))})'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options screenshot --filename docs/superpowers/evidence/2026-08-26-screen-metric-source-options-playwright/01-merged-metric-options-selected.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options run-code 'async page => { const select=page.locator("[data-testid=chart-metric-columns]"); await select.click(); await page.waitForTimeout(300); return await page.locator(".el-select-dropdown:visible .el-select-dropdown__item").evaluateAll(els=>els.map(el=>({label:el.textContent?.trim(),selected:el.classList.contains("is-selected")}))); }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options screenshot --filename docs/superpowers/evidence/2026-08-26-screen-metric-source-options-playwright/02-all-merged-metric-options.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options requests --filter '/api/'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options request 239
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options response-body 239
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options console error
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-source-options route-list
```

候选核验返回：

```json
[
  {"label":"核心存款余额","selected":true},
  {"label":"存款日均增量","selected":true},
  {"label":"存款客户数","selected":false}
]
```

第一次读取编辑项时误用了 Playwright Locator 不存在的 `inputValues()`，CLI 返回 `TypeError`；随后改用 `evaluateAll` 完成相同只读核验。该定位错误未触发业务请求或页面错误。

## label 隔离与亿元换算补充验收

第二个会话使用相同的认证、权限和只读目录 mock；画布改为带已保存 block 的数值卡，关键差异命令如下：

```bash
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount open about:blank --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount resize 1920 1080
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount route '**/api/screen/admin/canvas/9202' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-canvas","data":{"screenId":9202,"screenCode":"SCR_METRIC_AMOUNT","screenName":"数值卡金额与标签验收","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":4,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\"}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[{\"id\":\"metric-card-amount\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"name\":\"亿元数值卡\",\"blockId\":93,\"bindJson\":\"{\\\"dsId\\\":9020,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"存款余额\\\",\\\"label\\\":\\\"核心存款余额\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"亿元数值卡\\\",\\\"refreshSec\\\":0}\",\"drillJson\":\"{}\",\"style\":{\"top\":100,\"left\":150,\"width\":720,\"height\":300},\"isLock\":false,\"isShow\":true,\"propValue\":{}}]}","blocks":[{"id":93,"componentType":"METRIC_CARD","bindJson":"{\"dsId\":9020,\"dsType\":\"SINGLE\",\"period\":\"LATEST\",\"items\":[{\"col\":\"存款余额\",\"label\":\"核心存款余额\"}]}","styleJson":"{\"title\":\"亿元数值卡\",\"refreshSec\":0}","drillJson":"{}"}]}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount route '**/api/screen/admin/datasources*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-datasources","data":[{"id":9020,"dsCode":"DS_METRIC_AMOUNT","dsName":"全省存款指标数据源","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{\"scopeMode\":\"GLOBAL\",\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\"},{\"metricName\":\"存款日均增量\"},{\"metricName\":\"存款客户数\"}],\"fieldMeta\":[{\"col\":\"存款余额\",\"alias\":\"核心存款余额\",\"role\":\"METRIC\",\"amountScale\":\"HUNDRED_MILLION_YUAN\"},{\"col\":\"机构\",\"alias\":\"机构名称\",\"role\":\"DIM\"}]}"}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount route '**/api/screen/data' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screen-data","data":{"columns":["存款余额","存款日均增量","存款客户数"],"rows":[[100000000,3500000,1280]],"columnsMeta":[{"col":"存款余额","alias":"核心存款余额","role":"METRIC","amountScale":"HUNDRED_MILLION_YUAN","unit":"亿元","decimals":2}]}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount goto 'http://127.0.0.1:8091/#/screen-admin/designer'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount run-code 'async page => { await page.waitForTimeout(800); return {value:await page.locator(".mc-value").textContent(),label:await page.locator(".mc-label").textContent()}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount screenshot --filename docs/superpowers/evidence/2026-08-26-screen-metric-source-options-playwright/03-billion-metric-card.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount run-code 'async page => { await page.locator(".mc-item").click(); await page.waitForTimeout(500); const input=page.locator("[data-testid=chart-metric-item-label]"); await input.fill("自定义卡片标题"); await page.waitForTimeout(300); return {selectedTags:await page.locator("[data-testid=chart-metric-columns] .el-tag__content").evaluateAll(els=>els.map(el=>el.textContent?.trim())),editorValue:await input.inputValue()}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount screenshot --filename docs/superpowers/evidence/2026-08-26-screen-metric-source-options-playwright/04-custom-label-isolated.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount console error
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount requests --filter '/api/'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount request 237
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount request-body 237
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-metric-label-amount response-body 237
```

只读核验结果：

```json
{"value":"1.00亿元","label":"核心存款余额"}
{"selectedTags":["核心存款余额"],"editorValue":"自定义卡片标题"}
```
