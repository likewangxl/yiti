# 大屏新增图表组件验收证据（2026-08-24）

## 验收范围

- 图表组件数量由 13 增至 19。
- 新增：双轴组合、漏斗转化、散点气泡、热力矩阵、旭日分层、迷你趋势卡。
- 真实页面切换至“省分行经营总览（全辖）”（`SCR_PROVINCE`）。
- 实际拖入“双轴组合”，选择“全省核心指标趋势(聚合时序)”数据源，确认属性面板显示视觉预设、图例、数据标签、辅助标记、曲线平滑等配置。
- 未点击保存；测试产生的未保存组件已通过页面重新加载丢弃，现有大屏数据未被改写。

## 自动化验证

```text
npm.cmd test -- --run src/views/screen/components/__tests__/NewChartVisuals.spec.js src/views/screen/components/__tests__/ScreenDataContract.spec.js src/views/screen/components/__tests__/ChartVisuals.spec.js src/views/screen/components/__tests__/ScreenSecondaryVisuals.spec.js src/views/screen/designer/widgets/__tests__/registry.spec.js src/views/screen/designer/panels/__tests__/ComponentPanel.spec.js src/views/screen/designer/widgets/chart-widget/__tests__/Attr.visual.spec.js src/views/screen/designer/widgets/chart-widget/__tests__/Attr.scope.spec.js src/utils/__tests__/globalPeriod.spec.js
结果：9 个测试文件、71 个测试全部通过。

mvn.cmd -pl report-analytics-center '-Dtest=ScreenCanvasServiceTest,ScreenConfigServiceTest' test
结果：74 个测试全部通过，BUILD SUCCESS。

npm.cmd run build
结果：Vite 4.5.14 构建成功；仅有既存 Sass legacy 与 chunk-size 警告。

mvn.cmd -pl bootstrap -am package -DskipTests
结果：18 个 Maven 模块及 bootstrap 可执行 JAR 构建成功。

git diff --check
结果：通过。
```

## Playwright CLI 关键命令

```text
playwright-cli --session screen-chart-components-20260824 goto http://127.0.0.1:8091/#/screen-admin/designer
playwright-cli --session screen-chart-components-20260824 snapshot
playwright-cli --session screen-chart-components-20260824 drag <双轴组合> <画布>
playwright-cli --session screen-chart-components-20260824 click <数据源>
playwright-cli --session screen-chart-components-20260824 click <全省核心指标趋势(聚合时序)>
playwright-cli --session screen-chart-components-20260824 route-list
playwright-cli --session screen-chart-components-20260824 console error
playwright-cli --session screen-chart-components-20260824 requests
```

## 页面与运行结果

- 前端：`http://127.0.0.1:8091/`，HTTP 200。
- 后端 OpenAPI：`http://127.0.0.1:18081/v3/api-docs`，HTTP 200。
- 页面显示图表组件计数 `19`，六个新类型及代码全部可见。
- 真实数据请求 `/api/screen/data` 返回 200。
- 未注册 Playwright mock route，详见 `routes.txt`。
- 当前管理员对地图区域指标接口仍返回 403；这是地图业务权限门禁，不影响本次新增图表组件的注册、拖拽与数据源选择，详见 `console-errors.txt` 和 `requests.txt`。

## 截图

- `chart-component-library-19.png`：19 个图表组件及六个新增组件。
- `combo-chart-config-preview.png`：双轴组合已拖入画布并选择数据源后的配置面板。
