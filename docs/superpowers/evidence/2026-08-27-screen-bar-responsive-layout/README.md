# 柱状对比自适应柱宽与间隔验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8091/#/screen/SCR_BAR_LAYOUT_QA?preview=draft`
- 工具：官方 `@playwright/cli` 0.1.18，Chromium
- 会话：`screen-bar-layout-20260827`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非后端联调**。浏览器上下文注册 5 条 mock route，未调用保存、发布等写接口。

## 验收结论

1. 两个柱状对比组件使用完全相同的 5 个指标和 6 个类目，仅组件宽度不同。
2. 窄组件实际绘图区为 598 × 330，计算得到 `barWidth=10`、`barGap=20%`、`barCategoryGap=34%`。
3. 宽组件实际绘图区为 1678 × 350，计算得到 `barWidth=32`、`barGap=20%`、`barCategoryGap=31%`。
4. 截图中两种宽度下同类目的 5 根柱体均保留可见间隔，没有粘连或重叠；宽组件充分利用可用空间增大柱宽。
5. 清空首次导航记录后重新加载，Console 为 0 error、0 warning；验收阶段只有权限查询、大屏读取和两次区块取数请求。

## 证据

- `bar-responsive-layout.png`：窄、宽两个同数据组件的完整页面截图。
- `layout.raw.txt`：页面实际尺寸和组件内最终 ECharts 布局参数。
- `commands.md`：实际 CLI 操作顺序。
- `routes.raw.txt`：5 条开发态 mock 路由清单。
- `console.raw.txt`：最终 Console 输出。
- `network.raw.txt`：目标请求、请求体和响应体摘要。
