# 大屏设计器数据源指标候选合并验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-metric-source-options`、`screen-metric-label-amount`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 12 条 mock route；未点击保存、发布或其他写操作。

## 验收结论

1. 数值卡片绑定“全省存款指标数据源”，数据源包含 3 个 `metrics`，字段元数据仅覆盖“存款余额”。
2. “指标列”下拉同时显示“核心存款余额”“存款日均增量”“存款客户数”。
3. “存款余额”与字段元数据同名，按列名去重并优先显示元数据别名“核心存款余额”。
4. 未配置字段元数据的“存款日均增量”“存款客户数”仍可选择。
5. 实际选择“核心存款余额”和“存款日均增量”后，生成 2 个独立指标编辑项，原始列与展示名称保持正确。
6. Console 为 0 error；6 条 warning 均为既有 Element Plus `el-radio label` 弃用提示。
7. 清空网络记录后的候选展开、选择和本地编辑交互没有产生 API 请求；重新加载核验时所有业务请求均为 GET。
8. 修改指标编辑项 label 为“自定义卡片标题”后，“指标列”选中项仍显示来源名称“核心存款余额”，两者不再联动。
9. 数值卡接口返回原始值 `100000000`，字段元数据为 `HUNDRED_MILLION_YUAN / 亿元 / 2 位`；组件实际显示 `1.00亿元`，未显示原始金额。

## 证据

- `01-merged-metric-options-selected.png`：字段元数据指标与数据源指标同时选中并生成编辑项。
- `02-all-merged-metric-options.png`：下拉展示合并后的 3 个候选，两个已选、一个未选。
- `03-billion-metric-card.png`：按字段元数据将原始金额换算为 `1.00亿元`。
- `04-custom-label-isolated.png`：指标列选中项保持“核心存款余额”，编辑项单独显示“自定义卡片标题”。
- `commands.md`：关键实际命令和交互顺序。
- `routes.raw.txt`：12 条开发态 mock 路由清单。
- `network.raw.txt`：页面 GET 请求及数据源响应正文。
- `console.raw.txt`：Console 原始摘要与 warning 类型。
