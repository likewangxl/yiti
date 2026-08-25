# 大屏柱状图与折线图多指标莫兰迪配色验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen/SCR_PROVINCE?preview=draft`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-morandi-bar`、`screen-morandi-line`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。每个会话注册 5 条 mock route；未执行保存、发布或其他写操作。

## 验收结论

1. 单行 11 指标柱状图按指标逐柱使用莫兰迪颜色，前 10 柱颜色不同，第 11 柱循环使用第 1 色。
2. 11 指标折线图按指标逐线使用相同色板，线、数据点和面积渐变保持同色；第 11 条线循环使用第 1 色。
3. 草稿预览顶部大屏标题保持隐藏，组件标题、返回按钮和时钟正常。
4. 两个会话清空初始导航记录并重新加载后，Console 均为 0 error、0 warning。
5. 验收阶段仅发生 GET 查询和 `/api/screen/data` POST 取数，没有保存、发布或其他状态变更请求。

## 证据

- `01-bar-11-metrics.png`：11 指标柱状图逐柱配色。
- `02-line-11-metrics.png`：11 指标折线图逐线配色。
- `routes.raw.txt`：两个会话的 5 条开发态 mock 路由。
- `network.raw.txt`：请求清单及两种取数响应摘要。
- `console.raw.txt`：两个会话的 CLI Console 结果。
- `commands.md`：实际 CLI 操作顺序。
