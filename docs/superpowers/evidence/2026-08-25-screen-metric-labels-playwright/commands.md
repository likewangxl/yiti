# 实际执行命令

以下命令在 `xanzc_frontend` 目录执行。浏览器使用当前 checkout 的 8091 Vite 页面；11 个 API 响应均由 CLI `route` 在浏览器上下文注册，属于**仅开发态 mock，非联调**。

```bash
npx playwright-cli -s=screen-metric-labels open 'http://127.0.0.1:8091/' --browser=chromium
npx playwright-cli -s=screen-metric-labels route '**/api/auth/current-user' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"empId":"E001","displayName":"验收用户","roles":[{"roleCode":"SYS_ADMIN"}]}}'
npx playwright-cli -s=screen-metric-labels route '**/api/auth/my-menus' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-metric-labels route '**/api/auth/permissions' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"resourceUrls":["/api/screen/admin/screens"],"isSystemAdmin":false}}'
npx playwright-cli -s=screen-metric-labels route '**/api/screen/admin/screens' --content-type 'application/json' --body '{"code":"0","message":"success","data":[{"id":9101,"screenCode":"SCR_PROVINCE","screenName":"省分行经营总览","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":5,"publishStatus":0}]}'
npx playwright-cli -s=screen-metric-labels route '**/api/screen/admin/canvas/9101' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"screenId":9101,"screenCode":"SCR_PROVINCE","screenName":"省分行经营总览","viewLevel":"PROVINCE","canvasVersion":5,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\"}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[{\"id\":\"chart-deposit\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"name\":\"全省存款核心指标(聚合)\",\"blockId\":null,\"bindJson\":\"{\\\"dsId\\\":9010,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"全省存款较上月净增\\\"},{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"全省存款月均余额\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"全省存款核心指标(聚合)\\\"}\",\"drillJson\":\"{}\",\"style\":{\"top\":80,\"left\":120,\"width\":600,\"height\":260},\"isLock\":false,\"isShow\":true,\"propValue\":{}},{\"id\":\"chart-loan\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"name\":\"全省贷款核心指标(聚合)\",\"blockId\":null,\"bindJson\":\"{\\\"dsId\\\":9011,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"贷款余额-机构\\\",\\\"label\\\":\\\"全省贷款余额\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"全省贷款核心指标(聚合)\\\"}\",\"drillJson\":\"{}\",\"style\":{\"top\":420,\"left\":120,\"width\":600,\"height\":260},\"isLock\":false,\"isShow\":true,\"propValue\":{}}]}\",\"blocks\":[]}}'
npx playwright-cli -s=screen-metric-labels route '**/api/screen/admin/screens/9101/map-region-metrics' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-metric-labels route '**/api/screen/admin/datasources*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[{"id":9010,"dsName":"全省存款聚合","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{\"scopeMode\":\"GLOBAL\",\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"一般性存款月均余额较上月-机构\"},{\"metricName\":\"一般性存款月均余额-机构\"}],\"fieldMeta\":[{\"col\":\"一般性存款月均余额较上月-机构\",\"alias\":\"全省存款较上月净增\",\"role\":\"METRIC\"},{\"col\":\"一般性存款月均余额-机构\",\"alias\":\"全省存款月均余额\",\"role\":\"METRIC\"}]}"},{"id":9011,"dsName":"全省贷款聚合","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{\"scopeMode\":\"GLOBAL\",\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"贷款余额-机构\"}],\"fieldMeta\":[{\"col\":\"贷款余额-机构\",\"alias\":\"全省贷款余额\",\"role\":\"METRIC\"}]}"}]}'
npx playwright-cli -s=screen-metric-labels route '**/api/admin/org-groups*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-metric-labels route '**/api/admin/org-profiles*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-metric-labels route '**/api/admin/roles/all*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-metric-labels route '**/api/screen/data' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"columns":[],"rows":[]}}'
npx playwright-cli -s=screen-metric-labels goto 'http://127.0.0.1:8091/#/screen-admin/designer'
npx playwright-cli -s=screen-metric-labels resize 1920 1080
npx playwright-cli -s=screen-metric-labels snapshot
npx playwright-cli -s=screen-metric-labels console --clear
npx playwright-cli -s=screen-metric-labels requests --clear
```

交互与证据命令：

```bash
npx playwright-cli -s=screen-metric-labels click e331
npx playwright-cli -s=screen-metric-labels snapshot
npx playwright-cli -s=screen-metric-labels fill e550 '存款净增（自定义）'
npx playwright-cli -s=screen-metric-labels snapshot
npx playwright-cli -s=screen-metric-labels screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-metric-labels-playwright/01-deposit-custom-label.png' --full-page
npx playwright-cli -s=screen-metric-labels click e334
npx playwright-cli -s=screen-metric-labels snapshot
npx playwright-cli -s=screen-metric-labels screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-metric-labels-playwright/02-loan-own-label.png' --full-page
npx playwright-cli -s=screen-metric-labels click e331
npx playwright-cli -s=screen-metric-labels snapshot
npx playwright-cli -s=screen-metric-labels screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-metric-labels-playwright/03-deposit-custom-label-restored.png' --full-page
npx playwright-cli -s=screen-metric-labels route-list
npx playwright-cli -s=screen-metric-labels console
npx playwright-cli -s=screen-metric-labels requests --filter '/api/'
npx playwright-cli -s=screen-metric-labels request 1
npx playwright-cli -s=screen-metric-labels response-body 1
npx playwright-cli -s=screen-metric-labels close
```
