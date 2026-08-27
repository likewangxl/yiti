# 大屏设计器数据源下拉回归验收

> 仅开发态 mock，非联调。
>
> 验收日期：2026-08-25；工具：官方 `@playwright/cli` 0.1.18；页面只访问本地 Vite
> `http://127.0.0.1:18195`。最终验收标签页的所有 `/api/**` 均由下列 browser-context route 响应，未连接后端、
> 数据库或 Vite proxy 上游，也未执行保存、发布、数据库写入或其他外部操作。

## 启动与浏览器命令

```bash
cd /home/djdev/lf/yiti/xanzc_frontend
npm run dev -- --host 127.0.0.1 --port 18195 --strictPort
./node_modules/.bin/playwright-cli --version
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown open --browser=chromium about:blank
```

## 路由/拦截器注册命令

```bash
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/auth/current-user' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-current","data":{"empId":"u-demo","username":"u-demo","displayName":"开发态 Mock 管理员","mainOrgCode":"ORG_001","roles":[{"roleCode":"SYS_ADMIN"}],"isSystemAdmin":true}}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/auth/my-menus' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-menus","data":[{"resourceId":"M_SCREEN_DESIGN","resourceUrl":"/screen-admin/designer","menuName":"大屏设计器","children":[]}]}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/auth/permissions' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-permissions","data":{"resourceUrls":["/api/screen/admin/screens","/api/screen/admin/datasources"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/screen/admin/screens' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-screens","data":[{"id":101,"screenCode":"SCR_DROPDOWN","screenName":"数据源下拉验收屏","viewLevel":"BRANCH","bizLine":"CORP","orgScopeMode":"ALL_ORGS","canvasVersion":1,"status":"ACTIVE"}]}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/screen/admin/canvas/101' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-canvas","data":{"screenId":101,"screenCode":"SCR_DROPDOWN","screenName":"数据源下拉验收屏","viewLevel":"BRANCH","bizLine":"CORP","orgScopeMode":"ALL_ORGS","canvasVersion":1,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\"}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[]}","blocks":[]}}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/admin/org-groups*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-groups","data":[]}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/admin/org-profiles*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-profiles","data":[]}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/screen/admin/screens/101/access-roles' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-access-roles","data":[]}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/admin/roles/all*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-roles","data":[]}'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown route '**/api/screen/admin/datasources*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-datasources-page","page":{"pageNo":1,"pageSize":20,"total":3,"totalPages":1,"records":[{"id":72,"dsCode":"DS_TREND_WIDE","dsName":"机构指标趋势","dsType":"TIMESERIES","sourceKind":"WIDE_TABLE","bizLine":"CORP","status":"ACTIVE","configJson":"{}"},{"id":73,"dsCode":"DS_SNAPSHOT_WIDE","dsName":"机构指标快照","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"CORP","status":"ACTIVE","configJson":"{}"},{"id":74,"dsCode":"DS_TREND_KPI","dsName":"KPI细项趋势","dsType":"TIMESERIES","sourceKind":"KPI_DETAIL","bizLine":"CORP","status":"ACTIVE","configJson":"{}"}]}}'
```

最终 `route-list` 原始输出：

```text
1. **/api/auth/current-user (status=200, body={"code":"0","message":"success","traceId":"mock-cu..., contentType=application/json)
2. **/api/auth/my-menus (status=200, body={"code":"0","message":"success","traceId":"mock-me..., contentType=application/json)
3. **/api/auth/permissions (status=200, body={"code":"0","message":"success","traceId":"mock-pe..., contentType=application/json)
4. **/api/screen/admin/screens (status=200, body={"code":"0","message":"success","traceId":"mock-sc..., contentType=application/json)
5. **/api/screen/admin/canvas/101 (status=200, body={"code":"0","message":"success","traceId":"mock-ca..., contentType=application/json)
6. **/api/admin/org-groups* (status=200, body={"code":"0","message":"success","traceId":"mock-gr..., contentType=application/json)
7. **/api/admin/org-profiles* (status=200, body={"code":"0","message":"success","traceId":"mock-pr..., contentType=application/json)
8. **/api/screen/admin/screens/101/access-roles (status=200, body={"code":"0","message":"success","traceId":"mock-ac..., contentType=application/json)
9. **/api/screen/admin/datasources* (status=200, body={"code":"0","message":"success","traceId":"mock-da..., contentType=application/json)
10. **/api/admin/roles/all* (status=200, body={"code":"0","message":"success","traceId":"mock-ro..., contentType=application/json)
```

预检第一个标签页曾遗漏 `roles/all` route，产生一次 500；补齐 route 后新开干净标签页，最终证据只取新标签页。

## 关键交互命令与结果

```bash
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown tab-new http://127.0.0.1:18195/#/screen-admin/designer
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown drag e112 '.dsn-stage'
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown click e296
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown click e353
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown click e296
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown screenshot --filename /home/djdev/lf/yiti/docs/superpowers/evidence/2026-08-25-designer-datasource-dropdown/designer-datasource-options.png
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown requests
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown request 207
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown response-body 207
./node_modules/.bin/playwright-cli -s=designer-ds-dropdown console
```

真实页面先把“趋势折线”拖入画布，右侧数据源下拉展示：

```text
机构指标趋势
KPI细项趋势
```

选择“机构指标趋势”成功后再次展开，下拉仍保留上述两个选项。返回的第三条“机构指标快照”为
`SINGLE`，按趋势折线的 `needTimeseries` 约束未展示，证明既修复了分页包装解包，也保留了既有过滤语义。

截图：[designer-datasource-options.png](designer-datasource-options.png)

## 原始请求/响应摘要

最终非静态请求全部为 GET 200：

```text
GET /api/auth/current-user => 200 OK
GET /api/auth/my-menus => 200 OK
GET /api/auth/permissions => 200 OK
GET /api/screen/admin/screens => 200 OK
GET /api/screen/admin/canvas/101 => 200 OK
GET /api/admin/org-groups => 200 OK
GET /api/admin/roles/all?recordStatus=0 => 200 OK
GET /api/admin/org-profiles => 200 OK
GET /api/screen/admin/datasources => 200 OK
GET /api/admin/org-groups => 200 OK
```

目标请求原始摘要：

```text
#207 [GET] http://127.0.0.1:18195/api/screen/admin/datasources
status: [200] OK
duration: 2ms
type: xhr
mimeType: application/json
```

目标响应原文：

```json
{"code":"0","message":"success","traceId":"mock-datasources-page","page":{"pageNo":1,"pageSize":20,"total":3,"totalPages":1,"records":[{"id":72,"dsCode":"DS_TREND_WIDE","dsName":"机构指标趋势","dsType":"TIMESERIES","sourceKind":"WIDE_TABLE","bizLine":"CORP","status":"ACTIVE","configJson":"{}"},{"id":73,"dsCode":"DS_SNAPSHOT_WIDE","dsName":"机构指标快照","dsType":"SINGLE","sourceKind":"WIDE_TABLE","bizLine":"CORP","status":"ACTIVE","configJson":"{}"},{"id":74,"dsCode":"DS_TREND_KPI","dsName":"KPI细项趋势","dsType":"TIMESERIES","sourceKind":"KPI_DETAIL","bizLine":"CORP","status":"ACTIVE","configJson":"{}"}]}}
```

## 原始 console

最终标签页：`Errors: 0, Warnings: 3`。完整 CLI 原始输出见
[console.raw.md](console.raw.md)。三条 warning 均为同一既有 Element Plus `el-radio label` 弃用提示，
无数据源加载错误、Vue 异常或网络错误：

```text
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
```

## 结论

PASS（仅开发态 mock 契约验收）：分页包装 `{ page: { records: [...] } }` 经统一 HTTP 层解包后，
设计器属性面板可正确渲染和选择数据源，并继续按图表元数据过滤。此结论不代表真实后端、权限数据或数据库联调通过。
