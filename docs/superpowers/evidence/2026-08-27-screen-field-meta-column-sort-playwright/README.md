# 大屏数据源字段列名中文排序验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8091/#/screen-admin/datasources`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-field-meta-col-sort`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。注册 9 条 route；试跑 POST 也由 route 拦截，未保存、未修改后端或数据库。

## 验收结论

1. 试跑响应故意按乱序返回列名：`余额、存款余额、机构名称、区域`。
2. 试跑预览表头仍保留接口原顺序，说明没有改写原始 `columns`。
3. 打开“编辑数据源”，在“字段元数据”新增一行并展开“列名”下拉，实际顺序为：`存款余额、机构名称、区域、余额`。
4. 下拉仍使用原列名作为 value，只调整展示顺序，不改变字段元数据契约。
5. Console 为 0 error、0 warning；仅有 Vite 连接调试日志。

## 证据

- `01-column-options-chinese-order.png`：编辑页“列名”下拉的中文排序结果。
- `interaction.raw.txt`：试跑原始表头和下拉实际顺序。
- `routes.raw.txt`：9 条开发态 mock route 清单。
- `network.raw.txt`：原始 API 请求摘要、试跑请求与乱序响应。
- `console.raw.txt`：原始 Console 摘要。
- `commands.md`：实际 CLI 操作顺序。
