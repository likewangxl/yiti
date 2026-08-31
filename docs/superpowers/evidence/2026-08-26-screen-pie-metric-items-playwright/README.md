# 占比饼图指标列验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`、`http://127.0.0.1:8091/#/screen/SCR_PIE_METRICS?preview=draft`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-pie-metric-items`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。未执行保存、发布、数据库写入或其他状态变更操作。

## 验收结论

1. 设计器选中 `PIE_SHARE` 后显示“指标列”，支持多选指标，并逐项编辑展示名称；截图中配置为 `deposit / 存款余额`、`loan / 贷款余额`。
2. 运行态取数包含最新一行 `deposit=300`、`loan=100`、`other=888`，饼图仅使用已选的两个指标，显示“存款余额 75%”和“贷款余额 25%”；未选的 `other` 没有进入图例或扇区。
3. 运行态画布尺寸为 1378 × 670，图表实际渲染成功。
4. 验收边界内只有鉴权、草稿包读取和 `/api/screen/data` 取数请求；所有接口均被开发态 route 拦截，返回 200。
5. Console 有 1 条 ECharts 基线错误：`Component graphic is used but not imported`。本次改动前 `PieShare.vue` 已使用 `graphic` 配置，本需求未引入该错误；该错误未阻断本次饼图渲染。

## 证据

- `01-deposit-loan-selected.png`：设计器“指标列”多选项及展示名称。
- `02-runtime-selected-metrics.png`：运行态只显示两个已选指标，比例为 75% / 25%。
- `routes.raw.txt`：开发态 mock 路由和拦截器注册清单。
- `network.raw.txt`：目标页面请求、取数请求体和响应摘要。
- `console.raw.txt`：清空初始记录并重新加载后的原始 Console 结果。
- `commands.md`：实际 CLI 操作顺序。
