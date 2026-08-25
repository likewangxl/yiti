# 大屏数据源范围冲突与组件名称验收

- 日期：2026-08-25
- 前端：http://127.0.0.1:8091
- 后端代理目标：http://127.0.0.1:18081
- 页面：`#/screen-admin/designer`
- 大屏：零售经营总览（`SCR_RETAIL_OVERVIEW`）
- 浏览器会话：`screen-services-20260825`

## Playwright CLI 命令

```powershell
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 route-list
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 requests
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 console
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 drag e757 e865
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 click e1451
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 screenshot
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 find "LINE_TREND"
.\node_modules\.bin\playwright-cli.cmd -s=screen-services-20260825 fill e636 LINE_TREND
```

## 路由与网络证据

- `route-list` 原始结果：`No active routes`。本次没有注册任何 mock route。
- `GET /api/screen/admin/datasources`：200，真实返回 11 条启用数据源。
- 返回中有两条 `KPI_DETAIL`：`个人KPI细项趋势(机构负责人方案)` 与 `个人KPI细项快照(机构负责人方案)`。
- `GET /api/screen/admin/screens`：200；当前屏的真实范围是 `bizLine=RETAIL`、`orgScopeMode=NAMED_GROUP`。
- 普通时序图表在同一屏可选 `机构核心指标(宽表)`；KPI 细项组件与 NAMED_GROUP 的机构宽表安全范围无交集。
- 页面把两条 KPI 数据源作为禁用项列出，并提示“需切换为传统上下文”；没有发起保存或发布请求。

请求清单中的关键原始条目：

```text
223. [GET] http://127.0.0.1:8091/api/screen/admin/datasources => [200] OK
224. [GET] http://127.0.0.1:8091/api/screen/admin/screens => [200] OK
225. [GET] http://127.0.0.1:8091/api/screen/admin/datasources => [200] OK
226. [GET] http://127.0.0.1:8091/api/screen/admin/datasources => [200] OK
231. [GET] http://127.0.0.1:8091/api/screen/admin/datasources => [200] OK
```

## 可见结果

- 左侧 19 个图表卡片只显示中文名称，不显示 `AREA_STACK`、`LINE_TREND` 等内部编码。
- `find "LINE_TREND"` 在未搜索时返回 `No matches found`。
- 搜索框输入 `LINE_TREND` 后只保留“趋势折线”一个组件，说明内部英文搜索仍可用。
- 拖入 KPI 细项表后，检查器明确展示范围冲突原因与两条真实数据源；下拉中的两项均为 disabled。
- 截图：[datasource-scope-conflict.png](datasource-scope-conflict.png)

## Console

原始日志：[console-raw.log](console-raw.log)。本次页面交互未产生新的数据源组件异常。日志包含已有问题：首次未登录探测产生 401、地图指标资源未登记产生 403，以及既有路由/ECharts/Element Plus 警告；这些不由本次改动引入。

## 自动化验证

- 聚焦测试：`ComponentPanel.spec.js`、`Attr.scope.spec.js`、`dsFilter.spec.js`，3 个文件 15 项全部通过。
- 生产构建：`npm run build` 成功，3056 个模块完成转换；只有既有 Sass 弃用与大 chunk 警告。
- 全量 `npm test` 发现 8 项既有失败：`RedEngineLogout.spec.js` 的退出目标期望不一致 1 项；`operation-cell-dom.spec.js` 在 Windows Sass 绝对路径导入失败 7 项。两个失败集合单独复跑仍稳定失败，与本次四个目标文件无依赖关系；全量运行末尾还发生 Node 堆内存不足。
