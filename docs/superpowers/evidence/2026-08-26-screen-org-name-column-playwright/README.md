# 机构主体聚合 org_name 与明细表格选列验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-org-name-column`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 12 条 mock route；没有注册或触发保存、发布等写接口。

## 验收结论

1. 模拟数据源满足 `WIDE_TABLE + ORG_INDEX_RESULT + aggregation.groupBy=SUBJECT`，未配置 `org_name` 字段元数据或指标快照。
2. 模拟取数响应列为 `org_code、org_name、存款余额`，明细表格按已保存 `items` 只展示“机构名称、存款余额(万元)”，不展示未选择的 `org_code`。
3. 两行展示值分别为“机构甲 / 1,234.50”和“机构乙 / 6,789.00”，证明选列后的值仍按原响应列下标投影，字段元数据单位和小数位继续生效。
4. 选中明细表格后，“指标列”已显示 `org_name` 和“存款余额”；下拉候选中的 `org_name` 来自数据源语义推导。
5. 实际取消 `org_name` 后选中项只剩“存款余额”，再次点击后恢复为“存款余额、org_name”，证明该自动字段可交互选择。
6. Console 为 0 error；3 条 warning 均为既有 Element Plus `el-radio label` 弃用提示。
7. 网络请求只有目录 GET 和只读取数的 `POST /api/screen/data`；没有保存、发布、PUT、PATCH 或 DELETE 请求。

## 证据

- `01-table-selected-columns.png`：明细表格仅展示已选机构名称和存款余额列。
- `02-org-name-metric-option.png`：指标列下拉出现并选中 `org_name`。
- `03-org-name-reselected.png`：取消后重新选择 `org_name`。
- `commands.md`：实际命令与交互返回值。
- `routes.raw.txt`：12 条开发态 mock route 清单。
- `network.raw.txt`：关键请求、请求体和响应体。
- `console.raw.txt`：Console 原始摘要。
