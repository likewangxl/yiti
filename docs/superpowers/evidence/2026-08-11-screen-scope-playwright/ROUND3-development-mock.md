# Round 3 大屏前端契约验收（官方 Playwright CLI）

> 发布/回滚的可复现 route、对话框截图、原始请求/响应和 console 计数，以后续的 [ROUND3-development-mock-proof.md](ROUND3-development-mock-proof.md) 为准；该修正记录使用独立干净会话，明确为 19 条 route、0 条 warning。

> **仅开发态 mock，非 `yiti_test` 联调。**
>
> 验收日期：2026-08-12。浏览器只访问本地 Vite `http://127.0.0.1:18192`；未连接数据库、`yiti_test` 或真实后端。所有 `/api/**` 都由本文件所列的 browser-context route 返回，不能作为后端联调、数据库验证或权限服务验证的证据。
>
> mock 身份含 `SYS_ADMIN`，仅用于平台 RBAC 菜单/资源可见性；设计器实际展示“SYS_ADMIN 不存在大屏业务旁路”。本轮未修改全局 `permissionStore`。

## 启动与会话（原始命令）

```bash
cd /home/djdev/leid/yiti/xanzc_frontend
npm run dev -- --host 127.0.0.1 --port 18192
npx playwright-cli -s=screen-round3 open --browser=chromium http://127.0.0.1:18192/#/login
npx playwright-cli -s=screen-round3 sessionstorage-set xanzc:user '{"empId":"u-round3","username":"u-round3","displayName":"开发态 Mock 管理员","mainOrgCode":"ORG_001","mainOrgName":"西安管理行","roles":[{"roleId":"9001","roleCode":"SYS_ADMIN","roleChName":"系统管理员"}],"isSystemAdmin":true}'
```

实际 CLI 打开回执：

```text
Browser `screen-round3` opened with pid 3464975.
```

## 开发态 mock route

下列 route 均由官方 `@playwright/cli` 注册。最终共 28 条；不存在访问真实后端的 fallback。集合读取规则与资源 ID 写入规则并存时，后者使用精确 pattern，避免 query-string 或具体 ID 被集合规则误吞。

本轮新增/替换的原始注册命令：

```bash
npx playwright-cli -s=screen-round3 route '**/api/screen/admin/datasources/73' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"round3-datasource-save","data":null}'
npx playwright-cli -s=screen-round3 route '**/api/screen/admin/datasources/73?*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"round3-datasource-delete","data":null}'
npx playwright-cli -s=screen-round3 route '**/api/screen/view/SCR_RPT43023*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"round3-rpt43023-view","data":{"screenCode":"SCR_RPT43023","screenName":"RPT-43023 发布快照校验","runtimeSchemaVersion":2,"renderPackageJson":"{\"canvasStyle\":{\"adaptor\":\"keepProportion\"},\"components\":[{\"id\":\"rpt-card\",\"component\":\"ChartWidget\",\"blockId\":123,\"innerType\":\"METRIC_CARD\",\"style\":{\"top\":180,\"left\":300,\"width\":800,\"height\":280}}],\"bindSnapshots\":{\"123\":{\"bind\":{\"period\":\"LATEST\",\"items\":[{\"col\":\"M_ORG_A\",\"label\":\"机构指标\"}]},\"styleCfg\":{\"title\":\"发布快照校验\"},\"drill\":{}}}}","mapPoints":[]}}'
npx playwright-cli -s=screen-round3 route '**/api/screen/data' --status 200 --content-type application/json --body '{"code":"RPT-43023","message":"开发态 mock：当前发布包缺少可信绑定快照","traceId":"round3-rpt43023-data","data":null}'
```

最终 `route-list` 原始输出（body 省略号来自 CLI 本身的截断）：

```text
1. **/api/auth/current-user
2. **/api/auth/my-menus
3. **/api/auth/permissions
4. **/api/notifications/unread-count
5. **/api/perf/metrics*
6. **/api/screen/admin/kpi-schemes
7. **/api/admin/org-groups
8. **/api/screen/admin/datasources/72/probe-columns
9. **/api/screen/admin/datasources/try-run
10. **/api/screen/admin/datasources*
11. **/api/screen/admin/screens
12. **/api/screen/admin/canvas/101
13. **/api/screen/admin/canvas/save
14. **/api/screen/admin/canvas/publish
15. **/api/screen/admin/canvas/rollback
16. **/api/screen/admin/canvas/101/publish-logs
17. **/api/screen/admin/screens/101/metadata
18. **/api/screen/admin/canvas/discard
19. **/api/admin/org-profiles*
20. **/api/admin/roles/all*
21. **/api/screen/admin/screens/101/access-roles
22. **/api/screen/view/SCR_XIAN_MOCK*
23. **/api/screen/view/SCR_STRICT_STRING*
24. **/api/screen/view/SCR_BRANCH*
25. **/api/screen/admin/datasources/73
26. **/api/screen/admin/datasources/73?*
27. **/api/screen/view/SCR_RPT43023*
28. **/api/screen/data
```

## 实际 UI 请求、响应与截图

### 发布与回滚：独立原因 + CAS body

设计器中“发布”与“回滚”分别打开独立原因对话框，不复用无语义确认框。实际 UI 请求：

```text
#160 [POST] /api/screen/admin/canvas/save => 200
#161 [POST] /api/screen/admin/canvas/publish => 200
#162 [GET]  /api/screen/admin/canvas/101 => 200
```

```json
{"screenId":101,"expectedVersion":7,"reason":"开发态验收：验证发布 CAS 与审计原因"}
```

```json
{"code":"0","message":"success","traceId":"round3-publish","data":null}
```

```text
#163 [GET]  /api/screen/admin/canvas/101/publish-logs => 200
#164 [POST] /api/screen/admin/canvas/rollback => 200
#165 [GET]  /api/screen/admin/canvas/101 => 200
```

```json
{"screenId":101,"publishLogId":501,"expectedVersion":7,"reason":"开发态验收：验证回滚 CAS 与审计原因"}
```

```json
{"code":"0","message":"success","traceId":"round3-rollback","data":null}
```

截图：[09-round3-publish-development-mock.png](09-round3-publish-development-mock.png)、[10-round3-rollback-development-mock.png](10-round3-rollback-development-mock.png)。

### 数据源：冻结、NAMED_GROUP 限制、ACTIVE 保存与 query 删除原因

真实编辑 UI 对已发布/归档引用显示完整清单 `草稿：SCR_XIAN_DRAFT；已发布：SCR_XIAN_PUBLISHED`，并显示：

```text
发布/归档引用已冻结查询语义
该数据源存在发布/归档引用，查询语义字段已冻结；请新建副本→改草稿绑定→重新发布。
```

同一 UI 中 NAMED_GROUP 下“自定义 SQL”为 disabled。截图：[11-round3-datasource-freeze-development-mock.png](11-round3-datasource-freeze-development-mock.png)。

首次 route 调试中，集合 route 未覆盖具体 `PUT /73` 与带 query 的 `DELETE /73?reason=...`，产生 #8/#11 `net::ERR_ABORTED`；随后加入上文精确 route。为避免将调试失败计入验收，重新加载数据源页后只采用以下干净加载周期结果：

```text
1. [GET] /api/notifications/unread-count => 200
2. [GET] /api/screen/admin/datasources => 200
3. [GET] /api/perf/metrics?pageSize=100 => 200
4. [GET] /api/screen/admin/kpi-schemes => 200
5. [GET] /api/admin/org-groups => 200
6. [PUT] /api/screen/admin/datasources/73 => 200
7. [GET] /api/screen/admin/datasources => 200
8. [DELETE] /api/screen/admin/datasources/73?reason=%E5%BC%80%E5%8F%91%E6%80%81%E6%9C%80%E7%BB%88%E5%A4%8D%E6%B5%8B%EF%BC%9A%E5%88%A0%E9%99%A4%E9%87%8D%E5%A4%8D%E6%95%B0%E6%8D%AE%E6%BA%90 => 200
9. [GET] /api/screen/admin/datasources => 200
```

干净周期内没有 `ERR_ABORTED`。保存请求的原始 body 明确保持当前合法状态：

```json
{"dsName":"可编辑机构宽表","remark":"开发态 mock 可保存/删除","sourceKind":"WIDE_TABLE","bizLine":"CORP","dsType":"TIMESERIES","configJson":"{\"schemaVersion\":2,\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_ORG_A\"}],\"scopeMode\":\"SUBJECT\"}","timeParamJson":"[]","status":"ACTIVE","reason":"开发态最终复测：保留 ACTIVE 并保存数据源"}
```

```json
{"code":"0","message":"success","traceId":"round3-datasource-save","data":null}
```

删除请求没有 body，原因只在 URL query；原始响应为：

```json
{"code":"0","message":"success","traceId":"round3-datasource-delete","data":null}
```

截图：[12-round3-datasource-delete-development-mock.png](12-round3-datasource-delete-development-mock.png)。

### RPT-43023：不自动 fallback

真实运行页 `#/screen/SCR_RPT43023` 显示：

```text
当前发布绑定快照不可用，需受控迁移或重新发布
```

该页原始请求清单只有 view 与一次 v2 取数：

```text
5. [GET]  /api/screen/view/SCR_RPT43023 => 200
6. [POST] /api/screen/data => 200
```

```json
{"schemaVersion":2,"screenCode":"SCR_RPT43023","blockId":123,"period":"LATEST","dateFrom":null,"dateTo":null,"contextParams":{"orgCode":null,"empId":null}}
```

```json
{"code":"RPT-43023","message":"开发态 mock：当前发布包缺少可信绑定快照","traceId":"round3-rpt43023-data","data":null}
```

没有第二次 v1 `/screen/data` 请求。截图：[13-round3-rpt43023-development-mock.png](13-round3-rpt43023-development-mock.png)。

### 严格 JSON 整数：字符串响应 fail-close

`SCR_STRICT_STRING` mock 返回字符串 `runtimeSchemaVersion: "2"` 与字符串 `blockId: "123"`。真实运行页显示：

```text
大屏运行协议版本不受支持，已拒绝取数
```

原始请求清单只包含：

```text
1. [GET] /api/screen/view/SCR_STRICT_STRING => 200
```

没有 `/api/screen/data` 请求，证明没有使用 `Number()` 宽松转换后取数。截图：[14-round3-strict-string-development-mock.png](14-round3-strict-string-development-mock.png)。

### 四地图节点 bbox 与键盘 Enter

原始 CLI bbox 命令：

```bash
npx playwright-cli -s=screen-round3 resize 1440 900
npx playwright-cli -s=screen-round3 eval '() => [...document.querySelectorAll("[data-anchor]")].map(node => { const r = node.getBoundingClientRect(); return { anchor: node.dataset.anchor, x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height), visible: r.width > 0 && r.height > 0 }; })'
```

```json
[
  {"anchor":"LEFT","x":202,"y":426,"width":56,"height":20,"visible":true},
  {"anchor":"RIGHT","x":1127,"y":426,"width":56,"height":20,"visible":true},
  {"anchor":"TOP","x":692,"y":208,"width":56,"height":20,"visible":true},
  {"anchor":"FAR_TOP","x":692,"y":178,"width":56,"height":20,"visible":true}
]
```

每个节点均先执行 `node.focus()`，再由官方 CLI `press Enter` 触发，实际结果：

```text
LEFT    -> #/screen/SCR_BRANCH?orgCode=128
RIGHT   -> #/screen/SCR_BRANCH?orgCode=191
TOP     -> #/screen/SCR_BRANCH?orgCode=169
FAR_TOP -> #/screen/SCR_BRANCH?orgCode=129
```

截图：[15-round3-map-development-mock.png](15-round3-map-development-mock.png)。

## 原始 console、测试与边界

此前非干净会话的 CLI 汇总曾显示 `Errors: 0, Warnings: 18`，但其保存的原始 log 实际含 **9** 条 `[WARNING]`（均为 Element Plus `el-radio` 的 `label act as value` 弃用告警）。两者计数不一致，因此该会话不再作为修正后的验收结论；原始文件仍完整保留在 [ROUND3-development-mock-console.raw.log](ROUND3-development-mock-console.raw.log)，没有隐藏既有 warning。计数一致的干净会话 console 见 [ROUND3-development-mock-proof.md](ROUND3-development-mock-proof.md)。

本轮代码完成后已执行：

```text
npm test -- --run src/api/__tests__/screen.scope-map.spec.js src/utils/__tests__/screenScope.spec.js src/views/screen/components/__tests__/ScreenDataContract.spec.js src/views/screen/admin/__tests__/Datasources.contract.spec.js src/views/screen/designer/__tests__/DesignerV2.spec.js
58/58 passed

npm test -- --run <31 个相关测试文件>
276/276 passed

npm test
73 files / 438 tests passed；仅既有 src/views/redengine/__tests__/RedEngineLogout.spec.js 失败
（期望 /#/redengine/login，实际 /#/login；本轮未修改）

npm run build
passed；仅 Sass legacy JS API 与大 chunk 警告
```

本轮不重做 Round 2 已归档的 probe、metadata、discard CLI 场景；其历史开发态 mock 证据见 [ROUND2-development-mock.md](ROUND2-development-mock.md)。本轮的 API/组件定向测试仍覆盖 metadata/discard 的 reason、CAS、`ACTIVE`/`DISABLED` 约束与 fail-close 序列化。
