# 柱状对比动态均匀间隔验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8091/#/screen/SCR_BAR_EVEN_QA?preview=draft`
- 工具：官方 `@playwright/cli` 0.1.18，Chromium
- 会话：`screen-bar-even-20260827`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非后端联调**。浏览器上下文注册 5 条 mock route，未调用保存、发布等写接口。

## 验收结论

1. 两个柱状对比组件使用完全相同的 5 个指标和 6 个类目，仅组件宽度不同。
2. 窄组件类目轴可用长度 526px，`barWidth=13`、`barGap=34.87%`；组内间距和跨类目间距均为 4.53px。
3. 宽组件类目轴可用长度 1606px，`barWidth=36`、`barGap=48.7%`；组内间距为 17.53px，跨类目间距为 17.54px，差异来自百分比保留两位小数。
4. 截图中全部 30 根柱沿类目轴连续等距分布，不再出现同类目柱挤在一起、类目之间留大块空白的情况。
5. 清空首次导航记录后重新加载，Console 为 0 error、0 warning；验收阶段只有权限查询、大屏读取和两次区块取数请求。

## 证据

- `bar-even-spacing.png`：窄、宽两个同数据组件的完整页面截图。
- `layout.raw.txt`：页面实际尺寸、最终布局参数和两类间距计算结果。
- `commands.md`：实际 CLI 操作顺序。
- `routes.raw.txt`：5 条开发态 mock 路由清单。
- `console.raw.txt`：最终 Console 输出。
- `network.raw.txt`：目标请求、请求体和响应体摘要。
