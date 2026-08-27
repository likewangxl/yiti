# 实际执行命令

以下命令在 `xanzc_frontend` 目录执行。

首次直接 `open` 时，CLI 默认查找 `/opt/google/chrome/chrome` 并失败；`install-browser chrome` 又因当前账户没有免密 sudo 无法安装系统 Chrome。随后核对本机已有 Playwright Chromium，使用 CLI 支持的 `--browser=chromium` 成功启动：

```bash
npx playwright-cli -s=screen-ds-switch open 'http://127.0.0.1:8091/' --browser=chromium
```

之后逐条执行以下路由注册：

```bash
npx playwright-cli -s=screen-ds-switch route '**/api/auth/current-user' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"empId":"E001","displayName":"验收用户","roles":[{"roleCode":"SYS_ADMIN"}]}}'
npx playwright-cli -s=screen-ds-switch route '**/api/auth/my-menus' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-ds-switch route '**/api/auth/permissions' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"resourceUrls":["/api/screen/admin/screens"],"isSystemAdmin":false}}'
npx playwright-cli -s=screen-ds-switch route '**/api/screen/admin/screens' --content-type 'application/json' --body '{"code":"0","message":"success","data":[{"id":9105,"screenCode":"SCR_DS_SWITCH","screenName":"数据源切换验收屏","viewLevel":"PROVINCE","bizLine":"COMMON","orgScopeMode":"LEGACY_CONTEXT","canvasVersion":3,"publishStatus":0}]}'
npx playwright-cli -s=screen-ds-switch route '**/api/screen/admin/canvas/9105' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"screenId":9105,"screenCode":"SCR_DS_SWITCH","screenName":"数据源切换验收屏","viewLevel":"PROVINCE","canvasVersion":3,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\"}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[{\"id\":\"chart-a\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"name\":\"图表A\",\"blockId\":null,\"bindJson\":\"{\\\"dsId\\\":101,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"图表A-存款余额\\\"}\",\"drillJson\":\"{}\",\"style\":{\"top\":80,\"left\":120,\"width\":500,\"height\":260},\"isLock\":false,\"isShow\":true,\"propValue\":{}},{\"id\":\"chart-b\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"name\":\"图表B\",\"blockId\":null,\"bindJson\":\"{\\\"dsId\\\":202,\\\"dsType\\\":\\\"SINGLE\\\",\\\"period\\\":\\\"LATEST\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"图表B-贷款余额\\\"}\",\"drillJson\":\"{}\",\"style\":{\"top\":420,\"left\":120,\"width\":500,\"height\":260},\"isLock\":false,\"isShow\":true,\"propValue\":{}}]}","blocks":[]}}'
npx playwright-cli -s=screen-ds-switch route '**/api/screen/admin/screens/9105/map-region-metrics' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-ds-switch route '**/api/screen/admin/datasources*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[{"id":101,"dsName":"全省存款聚合","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{}"},{"id":202,"dsName":"全省贷款聚合","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"COMMON","status":"ACTIVE","configJson":"{}"}]}'
npx playwright-cli -s=screen-ds-switch route '**/api/admin/org-groups*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-ds-switch route '**/api/admin/org-profiles*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-ds-switch route '**/api/admin/roles/all*' --content-type 'application/json' --body '{"code":"0","message":"success","data":[]}'
npx playwright-cli -s=screen-ds-switch route '**/api/screen/data' --content-type 'application/json' --body '{"code":"0","message":"success","data":{"columns":[],"rows":[]}}'
```

页面与交互命令：

```bash
npx playwright-cli -s=screen-ds-switch goto 'http://127.0.0.1:8091/#/screen-admin/designer'
npx playwright-cli -s=screen-ds-switch resize 1920 1080
npx playwright-cli -s=screen-ds-switch snapshot
npx playwright-cli -s=screen-ds-switch console --clear
npx playwright-cli -s=screen-ds-switch requests --clear
npx playwright-cli -s=screen-ds-switch click e331
npx playwright-cli -s=screen-ds-switch snapshot
npx playwright-cli -s=screen-ds-switch screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-datasource-switch-playwright/01-chart-a-selected.png' --full-page
npx playwright-cli -s=screen-ds-switch click e334
npx playwright-cli -s=screen-ds-switch snapshot
npx playwright-cli -s=screen-ds-switch screenshot --filename '../docs/superpowers/evidence/2026-08-25-screen-datasource-switch-playwright/02-chart-b-selected.png' --full-page
npx playwright-cli -s=screen-ds-switch route-list
npx playwright-cli -s=screen-ds-switch console
npx playwright-cli -s=screen-ds-switch requests --filter '/api/'
npx playwright-cli -s=screen-ds-switch request 1
npx playwright-cli -s=screen-ds-switch request 3
npx playwright-cli -s=screen-ds-switch response-body 1
npx playwright-cli -s=screen-ds-switch response-body 3
npx playwright-cli -s=screen-ds-switch close
```
