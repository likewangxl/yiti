# 实际执行命令与结果

以下命令均在仓库根目录执行。所有 route 均为**仅开发态 mock，非联调**。

```bash
xanzc_frontend/node_modules/.bin/playwright-cli --version
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta open about:blank --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta resize 1920 1080
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/auth/current-user' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-current","data":{"empId":"E001","username":"field-meta-tester","displayName":"字段元数据验收用户","mainOrgCode":"ORG001","roles":[{"roleCode":"SYS_ADMIN"}],"isSystemAdmin":true}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/auth/my-menus' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-menus","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/auth/permissions' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-permissions","data":{"resourceUrls":["/api/screen/admin/screens"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/screen/admin/screens' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screens","data":[{"id":9401,"screenCode":"SCR_TABLE_FIELD_META","screenName":"明细表格字段元数据验收","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":1,"publishStatus":0}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/screen/admin/canvas/9401' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-canvas","data":{"screenId":9401,"screenCode":"SCR_TABLE_FIELD_META","screenName":"明细表格字段元数据验收","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":1,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\"}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[{\"id\":\"table-field-meta\",\"component\":\"ChartWidget\",\"innerType\":\"TABLE_LIST\",\"name\":\"字段元数据明细表\",\"blockId\":null,\"bindJson\":\"{\\\"dsId\\\":9402,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[]}\",\"styleJson\":\"{\\\"title\\\":\\\"字段元数据明细表\\\"}\",\"drillJson\":\"{}\",\"style\":{\"top\":100,\"left\":150,\"width\":760,\"height\":320},\"isLock\":false,\"isShow\":true,\"propValue\":{}}]}","blocks":[]}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/screen/admin/screens/9401/map-region-metrics' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-map","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/screen/admin/datasources*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-datasources","data":[{"id":9402,"dsCode":"DS_TABLE_FIELD_META","dsName":"明细表格字段元数据源","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{\"scopeMode\":\"GLOBAL\",\"table\":\"ORG_INDEX_RESULT\",\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},\"fieldMeta\":[{\"col\":\"org_name\",\"alias\":\"机构名称\",\"role\":\"DIM\"},{\"col\":\"region_name\",\"alias\":\"区域\",\"role\":\"DIM\"},{\"col\":\"balance\",\"alias\":\"余额\",\"role\":\"METRIC\"}],\"metrics\":[{\"metricName\":\"balance\"},{\"metricName\":\"customer_count\"},{\"metricName\":\"org_name\"}]}"}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/admin/org-groups*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-groups","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/admin/org-profiles*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-profiles","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/admin/roles/all*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-roles","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/screen/data' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screen-data","data":{"columns":["org_name","region_name","balance","customer_count"],"rows":[["机构甲","城区",1234.5,88]],"columnsMeta":[{"col":"org_name","alias":"机构名称","role":"DIM"},{"col":"region_name","alias":"区域","role":"DIM"},{"col":"balance","alias":"余额","role":"METRIC"}]}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route '**/api/notifications/unread-count' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-unread","data":0}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta goto 'http://127.0.0.1:8091/#/screen-admin/designer'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta run-code 'async page => { await page.getByText("已选择数据源，保存后预览", {exact:true}).click(); await page.waitForTimeout(500); return {selectorCount:await page.locator("[data-testid=chart-metric-columns]").count(), selected:await page.locator("[data-testid=chart-metric-columns] .el-tag__content").allTextContents()}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta run-code 'async page => { const select=page.locator("[data-testid=chart-metric-columns]"); await select.click(); await page.waitForTimeout(300); return await page.locator(".el-select-dropdown:visible .el-select-dropdown__item").evaluateAll(els=>els.map(el=>({label:el.textContent?.trim(),selected:el.classList.contains("is-selected")}))); }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta screenshot --filename docs/superpowers/evidence/2026-08-26-screen-table-field-meta-playwright/01-field-meta-options.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta run-code 'async page => { const dropdown=page.locator(".el-select-dropdown:visible"); await dropdown.locator(".el-select-dropdown__item", {hasText:"机构名称"}).click(); await dropdown.locator(".el-select-dropdown__item", {hasText:"区域"}).click(); return {selected:await page.locator("[data-testid=chart-metric-columns] .el-tag__content").allTextContents()}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta press Escape
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta screenshot --filename docs/superpowers/evidence/2026-08-26-screen-table-field-meta-playwright/02-dimension-field-meta-selected.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta run-code 'async page => { const select=page.locator("[data-testid=chart-metric-columns]"); await select.click(); return {selected:await select.locator(".el-tag__content").allTextContents(),options:await page.locator(".el-select-dropdown:visible .el-select-dropdown__item").evaluateAll(els=>els.map(el=>({label:el.textContent?.trim(),selected:el.classList.contains("is-selected")})))}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta screenshot --filename docs/superpowers/evidence/2026-08-26-screen-table-field-meta-playwright/03-selected-options.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta console error
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta console
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta requests --filter '/api/'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta request 240
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta response-body 240
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta request 235
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta response-body 235
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-table-field-meta close
```

关键交互返回：

```json
[{"label":"机构名称","selected":false},{"label":"区域","selected":false},{"label":"余额","selected":false},{"label":"customer_count","selected":false}]
{"selected":["机构名称","区域"],"options":[{"label":"机构名称","selected":true},{"label":"区域","selected":true},{"label":"余额","selected":false},{"label":"customer_count","selected":false}]}
{"selected":["机构名称","区域"],"editorCols":["org_name","region_name"],"editorLabels":[],"unsaved":1}
```

选择完成后的第一次扩展 DOM 检查误用了不存在的 Locator `inputValues()`，CLI 返回 `TypeError`；两个点击已在报错前完成。随后改用 `evaluateAll(els => els.map(el => el.value))` 重新检查，交互状态与原始列写回均正常。该错误只发生在验收脚本读取阶段，没有修改应用代码或业务数据。
