# 柱状对比指标栏高度与可选类目轴验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8091/#/screen/SCR_BAR_LEGEND_QA?preview=draft`
- 工具：官方 `@playwright/cli` 0.1.18，Chromium
- 会话：`screen-bar-legend-20260827`
- 视口：1440 × 900
- 模式：**仅开发态 mock，非后端联调**。浏览器上下文注册 5 条 mock route，未调用保存、发布等写接口。

## 验收结论

1. 柱状对比绑定 8 个指标，顶部指标栏全部显示；最后一个指标正常换至第二行，没有被柱状绘图区覆盖。
2. 运行时 option 的图例为 `top=4`、`height=34`、`padding=[6,8]`，柱状绘图区 `grid.top=46`，指标栏底部保留 8px 间隔。
3. 渲染包未提供 `categoryCol`。组件内部仍用返回首列生成“一月～四月”四个类目并正确绘制四组柱体，但 `axisLabel.show=false`、`axisTick.show=false`，截图中不显示类目文字和刻度。
4. 清空首次导航记录后重新加载，Console 为 0 error、0 warning；验收阶段只有权限查询、大屏读取和一次区块取数请求。

## 证据

- `bar-legend-empty-category.png`：8 指标换行和空类目轴展示的完整页面截图。
- `runtime.raw.txt`：页面实际尺寸和最终 ECharts option 关键参数。
- `commands.md`：实际 CLI 操作顺序。
- `routes.raw.txt`：5 条开发态 mock 路由清单。
- `console.raw.txt`：最终 Console 输出。
- `network.raw.txt`：目标请求、请求体和响应体摘要。
