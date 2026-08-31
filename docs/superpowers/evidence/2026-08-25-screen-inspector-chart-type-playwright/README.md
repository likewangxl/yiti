# 大屏设计器检查器图表类型验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-inspector-type`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 11 条 mock route；未执行保存、发布或其他写操作。

## 验收结论

1. 选中柱状对比组件后，右侧检查器显示「柱状对比（BAR_COMPARE）」。
2. 切换选中趋势折线组件后，同一位置同步更新为「趋势折线（LINE_TREND）」。
3. 两种类型的中文名称来自图表注册表，类型编码来自当前组件 `innerType`。
4. 清空初始导航记录后，Console 为 0 error；存在 9 条项目既有的 Element Plus `el-radio label` 弃用警告，与本次类型显示改动无关。
5. 交互期间网络记录只有 4 个 GET 请求，没有 POST、PUT、PATCH 或 DELETE。

## 证据

- `01-bar-compare-selected.png`：柱状对比组件选中后的检查器类型。
- `02-line-trend-selected.png`：切换到趋势折线后的检查器类型。
- `routes.raw.txt`：浏览器上下文注册的 11 条 mock route。
- `network.raw.txt`：清空记录后的原始请求/响应摘要。
- `console.raw.txt`：CLI Console 计数和警告内容。
- `commands.md`：实际 CLI 操作顺序。
