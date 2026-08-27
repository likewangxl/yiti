# 实际执行命令

以下命令均在仓库根目录执行。认证、菜单、权限、机构组、机构画像、角色和未读数 route 均返回最小成功响应；关键业务 mock 正文在下方完整保留。

```bash
mkdir -p docs/superpowers/evidence/2026-08-26-screen-org-name-column-playwright
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column open about:blank --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column resize 1920 1080
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route '**/api/auth/current-user' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-current","data":{"empId":"E001","username":"org-name-tester","displayName":"机构名称列验收用户","mainOrgCode":"ORG001","roles":[{"roleCode":"SYS_ADMIN"}],"isSystemAdmin":true}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route '**/api/auth/my-menus' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-menus","data":[]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route '**/api/auth/permissions' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-permissions","data":{"resourceUrls":["/api/screen/admin/screens"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route '**/api/screen/admin/screens' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screens","data":[{"id":9301,"screenCode":"SCR_ORG_NAME_COLUMN","screenName":"机构名称列验收","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":1,"publishStatus":0}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route '**/api/screen/admin/datasources*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-datasources","data":[{"id":9302,"dsCode":"DS_ORG_SUBJECT","dsName":"机构指标主体聚合","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{\"scopeMode\":\"GLOBAL\",\"table\":\"ORG_INDEX_RESULT\",\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},\"metrics\":[{\"metricName\":\"存款余额\"}]}"}]}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route '**/api/screen/data' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screen-data","data":{"columns":["org_code","org_name","存款余额"],"rows":[["001","机构甲",1234.5],["002","机构乙",6789]],"columnsMeta":[{"col":"org_name","alias":"机构名称","role":"DIM"},{"col":"存款余额","role":"METRIC","unit":"万元","decimals":2}]}}'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column goto 'http://127.0.0.1:8091/#/screen-admin/designer'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column run-code 'async page => { await page.waitForTimeout(1000); return {headers:await page.locator(".tl-table thead th").allTextContents(),rows:await page.locator(".tl-table tbody tr").evaluateAll(rows=>rows.map(row=>Array.from(row.querySelectorAll("td")).map(td=>td.textContent?.trim())))}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column screenshot --filename docs/superpowers/evidence/2026-08-26-screen-org-name-column-playwright/01-table-selected-columns.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column run-code 'async page => { await page.getByText("机构甲", {exact:true}).click(); await page.waitForTimeout(500); return {selectorCount:await page.locator("[data-testid=chart-metric-columns]").count(),selected:await page.locator("[data-testid=chart-metric-columns] .el-tag__content").allTextContents()}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column run-code 'async page => { const select=page.locator("[data-testid=chart-metric-columns]"); await select.click(); await page.waitForTimeout(300); return await page.locator(".el-select-dropdown:visible .el-select-dropdown__item").evaluateAll(els=>els.map(el=>({label:el.textContent?.trim(),selected:el.classList.contains("is-selected")}))); }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column screenshot --filename docs/superpowers/evidence/2026-08-26-screen-org-name-column-playwright/02-org-name-metric-option.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column run-code 'async page => { const dropdown=page.locator(".el-select-dropdown:visible"); const org=dropdown.locator(".el-select-dropdown__item", {hasText:"org_name"}); await org.click(); await page.waitForTimeout(200); const afterRemove=await page.locator("[data-testid=chart-metric-columns] .el-tag__content").allTextContents(); await org.click(); await page.waitForTimeout(200); const afterAdd=await page.locator("[data-testid=chart-metric-columns] .el-tag__content").allTextContents(); return {afterRemove,afterAdd}; }'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column press Escape
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column screenshot --filename docs/superpowers/evidence/2026-08-26-screen-org-name-column-playwright/03-org-name-reselected.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column console error
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column requests --filter '/api/'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column request 237
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column request-body 237
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column response-body 237
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column request 241
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column response-body 241
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column console
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-org-name-column close
```

画布 route `**/api/screen/admin/canvas/9301` 返回一个 `TABLE_LIST` 组件及对应 block，绑定项为 `org_name`、`存款余额`；其余 route 为 `map-region-metrics`、`org-groups`、`org-profiles`、`roles/all`、`notifications/unread-count` 的最小空响应。完整 route 注册结果见 `routes.raw.txt`。

关键交互返回：

```json
{"headers":["机构名称","存款余额(万元)"],"rows":[["机构甲","1,234.50"],["机构乙","6,789.00"]]}
{"selectorCount":1,"selected":["org_name","存款余额"]}
[{"label":"存款余额","selected":true},{"label":"org_name","selected":true}]
{"afterRemove":["存款余额"],"afterAdd":["存款余额","org_name"]}
```
