# 大屏图表组件专属图标验收证据（2026-08-24）

## 变更范围

- 左侧 19 个图表组件由字符图标/统一 `▤` 回退改为按 `innerType` 渲染的专属 SVG 微缩图。
- 图标统一使用 `32 × 32` viewBox、28px 展示尺寸、圆角线帽、`currentColor` 和两级透明度。
- 素材组件、地图组件、搜索和 HTML5 拖拽协议保持不变。

## 图形映射

- 面积图使用叠层填充轮廓；柱状图使用三柱；组合图使用柱线叠加。
- 流程状态使用连接节点；漏斗使用三级收口；仪表盘使用半圆刻度和指针。
- 热力矩阵使用九宫格；KPI 雷达使用多边形；趋势折线使用折线节点；水波完成度使用圆形波面。
- 数值卡片使用信息卡轮廓；占比饼图使用扇区；进度列表使用多行进度；排行榜使用带序位的长短列表。
- 散点气泡使用坐标轴与不同半径圆点；迷你趋势卡使用卡片内折线；旭日分层使用同心层级；明细表格使用网格。

## TDD 与构建

```text
Red：ChartTypeIcon.vue 不存在，图标测试无法收集；ComponentPanel 专属 SVG 断言失败。

npm.cmd test -- --run src/views/screen/designer/panels/__tests__/ChartTypeIcon.spec.js src/views/screen/designer/panels/__tests__/ComponentPanel.spec.js
结果：2 个文件、7 个测试全部通过。

npm.cmd run build
结果：Vite 4.5.14，3056 modules transformed，构建成功；仅有既存 Sass legacy 与 chunk-size 警告。

git diff --check
结果：通过。
```

## Playwright CLI

```text
playwright-cli --session screen-chart-components-20260824 snapshot
playwright-cli --session screen-chart-components-20260824 eval "(el) => el.scrollIntoView({ block: 'center' })" <明细表格组件>
playwright-cli --session screen-chart-components-20260824 screenshot
playwright-cli --session screen-chart-components-20260824 route-list
playwright-cli --session screen-chart-components-20260824 console error
playwright-cli --session screen-chart-components-20260824 requests
```

## 真实页面结果

- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`。
- 当前大屏：“省分行经营总览（全辖）”（`SCR_PROVINCE`）。
- 图表组件计数为 19，所有组件均呈现不同 SVG 轮廓，截图见 `chart-icons-19.png`。
- Playwright 未注册 mock route。
- `/api/screen/admin/screens`、`/api/screen/admin/canvas/9101` 和 `/api/screen/data` 均返回 200。
- 地图区域指标接口仍返回既存 403，属于地图业务权限门禁，与图标修改无关。
