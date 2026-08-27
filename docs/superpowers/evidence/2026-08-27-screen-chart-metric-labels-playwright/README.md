# 大屏图表指标 label 展示验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8091/#/screen/SCR_LABEL_QA?preview=draft`
- 工具：官方 `@playwright/cli` 0.1.18，Chromium
- 会话：`screen-chart-labels-20260827`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非后端联调**。浏览器上下文注册 5 条 mock route，未调用保存、发布等写接口。

## 验收结论

1. 双轴组合的图例和左右 Y 轴名称显示 `items[].label`：`存款余额`、`余额增幅`，未显示字段名 `metric_a`、`metric_b`，也未显示字段元数据 alias `数据字段A`、`数据字段B`。
2. 柱状对比的图例显示同一组 `items[].label`，未显示字段名或字段元数据 alias。
3. 页面渲染 1 个双轴组合 canvas 和 1 个柱状对比 canvas；两个区块标题正常。
4. 清空首次导航记录后重新加载，Console 为 0 error、0 warning。
5. 验收阶段请求为查询和两次 `/api/screen/data` 取数；两个 POST 请求分别携带 `blockId=101`、`blockId=102`，没有状态变更请求。

## 证据

- `chart-metric-labels.png`：两个组件同时显示 label 的页面截图。
- `commands.md`：实际 CLI 操作顺序。
- `routes.raw.txt`：5 条开发态 mock 路由清单。
- `console.raw.txt`：最终 Console 输出。
- `network.raw.txt`：目标请求、请求体和响应体摘要。

