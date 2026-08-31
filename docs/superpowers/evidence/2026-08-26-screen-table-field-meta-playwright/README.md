# 明细表格字段元数据候选验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-table-field-meta`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 12 条 mock route；未注册或触发保存、发布等写接口。

## 验收结论

1. 数据源字段元数据包含两个维度字段：`org_name / 机构名称 / DIM`、`region_name / 区域 / DIM`，以及一个指标字段：`balance / 余额 / METRIC`。
2. 明细表格“指标列”候选实际显示“机构名称、区域、余额、customer_count”，证明 DIM 与 METRIC 字段元数据均可选择，且数据源指标仍参与候选合并。
3. `balance` 同时存在于 `fieldMeta` 和 `metrics` 时只显示一次，并使用字段元数据别名“余额”；`org_name` 同时来自字段元数据和机构主体聚合推导时也只显示一次。
4. 实际选择“机构名称、区域”后，编辑区原始数据项为 `org_name、region_name`，展示标签保持字段元数据别名；页面进入“有未保存修改”状态，证明 DIM 字段可写入当前组件绑定。
5. Console 为 0 error；3 条 warning 均为既有 Element Plus `el-radio label` 弃用提示。
6. 网络请求仅有认证和大屏管理只读 GET；没有保存、发布、PUT、PATCH、POST 或 DELETE 请求。

## 证据

- `01-field-meta-options.png`：候选下拉同时显示两个 DIM 字段元数据、一个 METRIC 字段元数据和一个数据源指标。
- `02-dimension-field-meta-selected.png`：两个 DIM 字段元数据已选中，并显示原始数据项与展示标签。
- `03-selected-options.png`：下拉中“机构名称、区域”为选中状态，其余候选未选中。
- `commands.md`：实际命令、交互返回和一次已纠正的只读 DOM 检查错误。
- `routes.raw.txt`：12 条开发态 mock route 清单。
- `network.raw.txt`：原始请求摘要与关键数据源/画布响应。
- `console.raw.txt`：原始 Console 摘要。
