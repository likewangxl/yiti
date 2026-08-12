# Round 2 大屏前端契约验收（官方 Playwright CLI）

> **仅开发态 mock，非 yiti_test 联调。**
>
> 验收时间：2026-08-11；浏览器只访问 `http://127.0.0.1:18191` 的本地 Vite。
> 所有 `/api/**` 请求均在浏览器 context 内由下列 `playwright-cli route` 规则响应；未连接数据库、`yiti_test`、真实后端或 Vite proxy 的上游服务。
> 页面中使用 `SYS_ADMIN` mock 身份只用于平台 RBAC 路由可见性；设计器明确展示“SYS_ADMIN 不存在大屏业务旁路”，未改动全局 `permissionStore` 语义。

## 启动与会话（原始命令）

```bash
cd /home/djdev/leid/yiti/xanzc_frontend
npm run dev -- --host 127.0.0.1 --port 18191
npx playwright-cli install-browser chromium
npx playwright-cli -s=screen-round2 open --browser=chromium http://127.0.0.1:18191/#/login
npx playwright-cli -s=screen-round2 sessionstorage-set xanzc:user '{"empId":"u-demo","username":"u-demo","displayName":"开发态 Mock 管理员","mainOrgCode":"ORG_001","mainOrgName":"西安管理行","roles":[{"roleId":"9001","roleCode":"SYS_ADMIN","roleChName":"系统管理员"}],"isSystemAdmin":true}'
```

官方 CLI 实际打开回执：

```text
### Browser `screen-round2` opened with pid 3385142.
### Ran Playwright code
await page.goto('http://127.0.0.1:18191/#/login');
```

## 开发态 mock route 注册（原始命令）

所有 route 都是官方 `@playwright/cli` 的 browser-context intercept；没有注册泛化的失败回退路由。

```bash
npx playwright-cli -s=screen-round2 route '**/api/auth/current-user' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-auth-current","data":{"empId":"u-demo","username":"u-demo","displayName":"开发态 Mock 管理员","mainOrgCode":"ORG_001","mainOrgName":"西安管理行","roles":[{"roleId":"9001","roleCode":"SYS_ADMIN","roleChName":"系统管理员"}],"isSystemAdmin":true}}'
npx playwright-cli -s=screen-round2 route '**/api/auth/my-menus' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-auth-menus","data":[{"resourceId":"M_REPORT","resourceUrl":"/report","menuName":"报表分析","children":[{"resourceId":"M_SCREEN_DS","resourceUrl":"/screen-admin/datasources","menuName":"大屏数据源"},{"resourceId":"M_SCREEN_DESIGN","resourceUrl":"/screen-admin/designer","menuName":"大屏设计器"}]}]}'
npx playwright-cli -s=screen-round2 route '**/api/auth/permissions' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-auth-permissions","data":{"resourceUrls":["/api/screen/admin/datasources","/api/screen/admin/screens","/api/screen/admin/datasources/72/probe-columns","/api/screen/admin/screens/101/metadata","/api/screen/admin/canvas/discard","/api/screen/view/*"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}'
npx playwright-cli -s=screen-round2 route '**/api/notifications/unread-count' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-notifications","data":0}'
npx playwright-cli -s=screen-round2 route '**/api/perf/metrics*' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-metrics","data":[{"metricCode":"M_ORG_A","metricName":"机构指标A","baseDim":"ORG"}]}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/kpi-schemes' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-kpi-schemes","data":[]}'
npx playwright-cli -s=screen-round2 route '**/api/admin/org-groups' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-org-groups","data":[{"groupCode":"G_XIAN_DEMO","groupName":"西安测试机构组","status":"ACTIVE","groupPurpose":"REPORT_SCREEN","memberOrgCodes":["610101"]}]}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/screens' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-screens","data":[{"id":101,"screenCode":"SCR_XIAN_MOCK","screenName":"西安复合演示屏","viewLevel":"BRANCH","bizLine":"CORP","orgScopeMode":"NAMED_GROUP","orgGroupCode":"G_XIAN_DEMO","canvasVersion":7,"status":"DRAFT","themeJson":"{}"}]}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/canvas/101' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-canvas-101","data":{"screenId":101,"screenCode":"SCR_XIAN_MOCK","canvasVersion":7,"publishStatus":0,"canvasStyleJson":"{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#050e2b\",\"adaptor\":\"keepProportion\",\"themeOverride\":{}}","canvasDraftJson":"{\"schemaVersion\":1,\"components\":[]}","blocks":[]}}'
npx playwright-cli -s=screen-round2 route '**/api/admin/org-profiles*' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-org-profiles","data":[{"orgCode":"610101","orgName":"高新支行","cityCode":"610100","operatingLevel":"PRIMARY","coordSys":"GCJ02","lng":108.94,"lat":34.23,"status":"ACTIVE"}]}'
npx playwright-cli -s=screen-round2 route '**/api/admin/roles/all*' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-roles","data":[{"roleCode":"ROLE_XIAN","roleChName":"西安经营角色"}]}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/screens/101/access-roles' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-access-roles","data":["ROLE_XIAN"]}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/screens/101/metadata' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-metadata","data":null}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/datasources/72/probe-columns' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-probe","data":{"columns":["org_code","完成率"],"rows":[["610101",0.92]]}}'
npx playwright-cli -s=screen-round2 route '**/api/screen/view/**' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-screen-view","data":{"screenCode":"SCR_XIAN_MOCK","screenName":"西安复合地图验收（开发态 mock）","orgScopeMode":"NAMED_GROUP","runtimeSchemaVersion":2,"renderPackageJson":"{\"canvasStyle\":{\"adaptor\":\"keepProportion\"},\"components\":[{\"id\":\"map-v2\",\"component\":\"MapCenter\",\"style\":{\"top\":140,\"left\":260,\"width\":1400,\"height\":760,\"opacity\":1},\"propValue\":{\"schemaVersion\":2,\"mode\":\"XIAN_COMPOSITE\",\"disclaimer\":\"组织分布示意，非地理比例\"}}],\"bindSnapshots\":{}}","mapPackage":{"schemaVersion":2,"mode":"XIAN_COMPOSITE","localPoints":[{"orgCode":"610101","orgName":"高新支行","lng":108.94,"lat":34.23},{"orgCode":"610102","orgName":"曲江支行","lng":109.02,"lat":34.19}],"satelliteNodes":[{"orgCode":"128","orgName":"宝鸡分行","anchor":"LEFT","targetScreenCode":"SCR_BRANCH"},{"orgCode":"191","orgName":"渭南分行","anchor":"RIGHT","targetScreenCode":"SCR_BRANCH"},{"orgCode":"169","orgName":"咸阳分行","anchor":"TOP","targetScreenCode":"SCR_BRANCH"},{"orgCode":"129","orgName":"榆林分行","anchor":"FAR_TOP","targetScreenCode":"SCR_BRANCH"}]}}}'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/datasources*' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-datasources-v2","data":[{"id":72,"dsCode":"DS_XIAN_ORG","dsName":"西安机构宽表（冻结示例）","remark":"允许修改展示备注","bizLine":"CORP","dsType":"SINGLE","sourceKind":"WIDE_TABLE","status":"ENABLED","configJson":"{\"schemaVersion\":2,\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_ORG_A\",\"slot\":1}],\"scopeMode\":\"NAMED_GROUP\"}","timeParamJson":"[]","draftReferenceScreenCodes":["SCR_XIAN_DRAFT"],"publishedReferenceScreenCodes":["SCR_XIAN_PUBLISHED"]}]}'
```

`datasources*` 的首次 mock 数据曾使用了错误的嵌套结构；在截图/结论前已真实执行以下替换，最终证据只采用上面正确的后端 `configJson` 形态：

```bash
npx playwright-cli -s=screen-round2 unroute '**/api/screen/admin/datasources*'
# 随后执行上方 mock-datasources-v2 route 命令
```

`discard` 先注册成功响应，完成成功路径后再替换为冲突响应：

```bash
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/canvas/discard' --status 200 --content-type 'application/json' --body '{"code":"0","message":"success","traceId":"mock-discard","data":null}'
npx playwright-cli -s=screen-round2 unroute '**/api/screen/admin/canvas/discard'
npx playwright-cli -s=screen-round2 route '**/api/screen/admin/canvas/discard' --status 200 --content-type 'application/json' --body '{"code":"RPT-43012","message":"开发态 mock：版本冲突","traceId":"mock-discard-conflict","data":null}'
```

### 最终 `route-list` 原始输出

```text
1. **/api/auth/current-user (status=200, body={"code":"0","message":"success","traceId":"mock-au..., contentType=application/json)
2. **/api/notifications/unread-count (status=200, body={"code":"0","message":"success","traceId":"mock-no..., contentType=application/json)
3. **/api/auth/permissions (status=200, body={"code":"0","message":"success","traceId":"mock-au..., contentType=application/json)
4. **/api/auth/my-menus (status=200, body={"code":"0","message":"success","traceId":"mock-au..., contentType=application/json)
5. **/api/admin/org-groups (status=200, body={"code":"0","message":"success","traceId":"mock-or..., contentType=application/json)
6. **/api/screen/admin/screens/101/access-roles (status=200, body={"code":"0","message":"success","traceId":"mock-ac..., contentType=application/json)
7. **/api/screen/admin/canvas/101 (status=200, body={"code":"0","message":"success","traceId":"mock-ca..., contentType=application/json)
8. **/api/screen/admin/screens (status=200, body={"code":"0","message":"success","traceId":"mock-sc..., contentType=application/json)
9. **/api/screen/admin/screens/101/metadata (status=200, body={"code":"0","message":"success","traceId":"mock-me..., contentType=application/json)
10. **/api/admin/org-profiles* (status=200, body={"code":"0","message":"success","traceId":"mock-or..., contentType=application/json)
11. **/api/perf/metrics* (status=200, body={"code":"0","message":"success","traceId":"mock-me..., contentType=application/json)
12. **/api/screen/admin/kpi-schemes (status=200, body={"code":"0","message":"success","traceId":"mock-kp..., contentType=application/json)
13. **/api/admin/roles/all* (status=200, body={"code":"0","message":"success","traceId":"mock-ro..., contentType=application/json)
14. **/api/screen/admin/datasources/72/probe-columns (status=200, body={"code":"0","message":"success","traceId":"mock-pr..., contentType=application/json)
15. **/api/screen/view/** (status=200, body={"code":"0","message":"success","traceId":"mock-sc..., contentType=application/json)
16. **/api/screen/admin/datasources* (status=200, body={"code":"0","message":"success","traceId":"mock-da..., contentType=application/json)
17. **/api/screen/admin/canvas/discard (status=200, body={"code":"RPT-43012","message":"开发态 mock：版本冲突","tra..., contentType=application/json)
```

## 验收结果、原始请求/响应摘要与截图

### 1. 独立列探测：原因 + 测试机构组，未走 `/screen/data`

真实 UI 操作：数据源列表点击“探测列” → 真实展开 Element Plus 下拉 → 选择 `西安测试机构组 (G_XIAN_DEMO)` → 填原因 → 点击“执行”。

```text
### Result
#15 [POST] http://127.0.0.1:18191/api/screen/admin/datasources/72/probe-columns

  General
    status:    [200] OK
    duration:  3ms
    type:      xhr
    mimeType:  application/json

Run `request-body 15` to read the request body.
Run `response-body 15` to read the response body.
```

```text
{"period":"LATEST","dateFrom":null,"dateTo":null,"contextParams":{"orgCode":null,"empId":null},"reason":"开发态验收：验证独立列探测审计与测试机构组","testOrgGroupCode":"G_XIAN_DEMO"}
```

```text
{"code":"0","message":"success","traceId":"mock-probe","data":{"columns":["org_code","完成率"],"rows":[["610101",0.92]]}}
```

请求清单原始输出（其中没有 `/api/screen/data`）：

```text
15. [POST] http://127.0.0.1:18191/api/screen/admin/datasources/72/probe-columns => [200] OK
```

截图：[04-round2-probe-development-mock.png](04-round2-probe-development-mock.png)。UI 显示测试机构组、审计原因和 `org_code`/`完成率` 返回列。

### 2. 发布/归档引用冻结与 NAMED_GROUP

数据源列表真实 UI 显示完整引用：`草稿：SCR_XIAN_DRAFT；已发布：SCR_XIAN_PUBLISHED`。编辑页真实 UI 显示：

```text
发布/归档引用已冻结查询语义
该数据源存在发布/归档引用，查询语义字段已冻结；请新建副本→改草稿绑定→重新发布。
```

快照显示语义字段 disabled、备注 textbox 仍可编辑，删除按钮 disabled。截图：[05-round2-datasource-freeze-development-mock.png](05-round2-datasource-freeze-development-mock.png)。

点击“新建副本”后，真实快照显示：

```text
radio "指标宽表(引导式)" [checked]
radio "KPI结果(引导式)" [disabled]
radio "KPI细项(引导式)" [disabled]
radio "自定义 SQL" [disabled]
combobox "*宽表": 机构指标宽表 (ORG_INDEX_RESULT)
NAMED_GROUP 仅允许 WIDE_TABLE 的 ORG_INDEX_RESULT + org_code；CUSTOM_SQL 仅保留给 LEGACY_CONTEXT。
```

截图：[06-round2-named-group-development-mock.png](06-round2-named-group-development-mock.png)。

### 3. metadata：CAS + 原因

设计器“编辑范围”真实 UI 显示 `版本 7`、必填“变更原因”，页面同时显示：

```text
草稿预览仍须通过屏白名单、机构组、同一角色及画布读取门禁；SYS_ADMIN 不存在大屏业务旁路。
```

原始请求摘要：

```text
#156 [PUT] http://127.0.0.1:18191/api/screen/admin/screens/101/metadata
  status: [200] OK
  type: xhr
  mimeType: application/json
```

原始 body：

```json
{"screenName":"西安复合演示屏","viewLevel":"BRANCH","bizLine":"CORP","orgScopeMode":"NAMED_GROUP","screenCode":"SCR_XIAN_MOCK","themeJson":"{}","status":"DRAFT","expectedVersion":7,"reason":"开发态验收：验证元数据 CAS 版本与审计原因","orgGroupCode":"G_XIAN_DEMO"}
```

原始 response：

```json
{"code":"0","message":"success","traceId":"mock-metadata","data":null}
```

随后请求 #157 `GET /api/screen/admin/screens`、#158 `GET /api/screen/admin/canvas/101` 都为 200。截图：[07-round2-designer-cas-development-mock.png](07-round2-designer-cas-development-mock.png)。空串显式清组、`null` 不改字段的序列化路径另由 `screen.scope-map.spec.js` 单测覆盖。

### 4. discard：成功 + RPT-43012 重载

放弃对话框真实 UI 显示版本 7、必填原因和“发生 CAS 冲突，会重新加载服务端最新草稿”。

成功路径原始请求/响应：

```text
#159 [POST] http://127.0.0.1:18191/api/screen/admin/canvas/discard
  status: [200] OK
  type: xhr
```

```json
{"screenId":101,"expectedVersion":7,"reason":"开发态验收：验证放弃草稿 CAS 版本与审计原因"}
```

```json
{"code":"0","message":"success","traceId":"mock-discard","data":null}
```

成功后原始清单：`#160 [GET] http://127.0.0.1:18191/api/screen/admin/canvas/101 => [200] OK`。

冲突路径原始请求/响应：

```text
#161 [POST] http://127.0.0.1:18191/api/screen/admin/canvas/discard
  status: [200] OK
  type: xhr
```

```json
{"screenId":101,"expectedVersion":7,"reason":"开发态验收：验证 RPT-43012 后重新加载最新草稿"}
```

```json
{"code":"RPT-43012","message":"开发态 mock：版本冲突","traceId":"mock-discard-conflict","data":null}
```

紧随其后的原始请求证明客户端重载而非保留旧版本：

```text
#162 [GET] http://127.0.0.1:18191/api/screen/admin/canvas/101
  status: [200] OK
  type: xhr
```

### 5. 四地图节点 bbox 与键盘 Enter

真实 CLI bbox 命令：

```bash
npx playwright-cli -s=screen-round2 resize 1440 900
npx playwright-cli -s=screen-round2 eval '() => [...document.querySelectorAll("[data-anchor]")].map(node => { const box = node.getBoundingClientRect(); return { anchor: node.dataset.anchor, x: Math.round(box.x), y: Math.round(box.y), width: Math.round(box.width), height: Math.round(box.height), ariaLabel: node.getAttribute("aria-label") }; })'
```

原始结果：

```json
[
  {"anchor":"LEFT","x":202,"y":426,"width":56,"height":20,"ariaLabel":"宝鸡分行，左侧示意导航节点"},
  {"anchor":"RIGHT","x":1127,"y":426,"width":56,"height":20,"ariaLabel":"渭南分行，右侧示意导航节点"},
  {"anchor":"TOP","x":692,"y":208,"width":56,"height":20,"ariaLabel":"咸阳分行，上方示意导航节点"},
  {"anchor":"FAR_TOP","x":692,"y":178,"width":56,"height":20,"ariaLabel":"榆林分行，远上方示意导航节点"}
]
```

四个节点逐一先真实 focus，再通过官方 CLI 的键盘命令 `press Enter` 回归；每次后回到 `SCR_XIAN_MOCK` 继续下一个节点。

```text
LEFT    -> #/screen/SCR_BRANCH?orgCode=128
RIGHT   -> #/screen/SCR_BRANCH?orgCode=191
TOP     -> #/screen/SCR_BRANCH?orgCode=169
FAR_TOP -> #/screen/SCR_BRANCH?orgCode=129
```

每一项所用原始命令形态：

```bash
npx playwright-cli -s=screen-round2 eval '() => { const node = document.querySelector("[data-anchor=\"LEFT\"]"); node.focus(); return { focusedAnchor: document.activeElement?.dataset?.anchor, ariaLabel: document.activeElement?.getAttribute("aria-label") }; }'
npx playwright-cli -s=screen-round2 press Enter
# RIGHT、TOP、FAR_TOP 仅替换 data-anchor 值，均执行同一 press Enter 命令。
```

地图最终原始请求/console：

```text
1. [GET] http://127.0.0.1:18191/api/screen/view/SCR_XIAN_MOCK => [200] OK

### Result
Total messages: 0 (Errors: 0, Warnings: 0)
```

截图：[08-round2-map-four-nodes-development-mock.png](08-round2-map-four-nodes-development-mock.png)。

## 原始 console 输出

probe、metadata/discard（含冲突）、地图均在清空 console 后得到相同的官方 CLI 输出：

```text
### Result
Total messages: 0 (Errors: 0, Warnings: 0)
```

数据源“编辑/副本”页的原始 CLI 输出起始如下；没有 error，7 条 warning 均为既有 Element Plus `el-radio` 的 v3 API 弃用提示，未作为本功能修复范围扩大修改：

```text
### Result
Total messages: 7 (Errors: 0, Warnings: 7)

[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
For more detail, please visit: https://element-plus.org/en-US/component/radio.html

    at debugWarn (http://127.0.0.1:18191/node_modules/.vite/deps/element-plus.js?v=41ded0b2:10209:37)
    at watch.immediate (http://127.0.0.1:18191/node_modules/.vite/deps/element-plus.js?v=41ded0b2:10254:7)
    at callWithErrorHandling (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:2391:19)
    at callWithAsyncErrorHandling (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:2398:17)
    at baseWatchOptions.call (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:3087:47)
    at job (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:2118:17)
    at watch (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:2154:7)
    at doWatch (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:3115:23)
    at watch2 (http://127.0.0.1:18191/node_modules/.vite/deps/chunk-OBWMZAUI.js?v=41ded0b2:3047:10)
    at useDeprecated (http://127.0.0.1:18191/node_modules/.vite/deps/element-plus.js?v=41ded0b2:10252:3)
```

> CLI 原始输出中的同一 warning/stack 共出现 7 次；命令计数 `Warnings: 7` 是原始输出。其余页面在同一会话清空后均为 0 warnings/0 errors。

## 截图清单

- [04-round2-probe-development-mock.png](04-round2-probe-development-mock.png)
- [05-round2-datasource-freeze-development-mock.png](05-round2-datasource-freeze-development-mock.png)
- [06-round2-named-group-development-mock.png](06-round2-named-group-development-mock.png)
- [07-round2-designer-cas-development-mock.png](07-round2-designer-cas-development-mock.png)
- [08-round2-map-four-nodes-development-mock.png](08-round2-map-four-nodes-development-mock.png)
