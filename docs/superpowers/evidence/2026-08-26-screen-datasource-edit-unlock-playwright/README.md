# 大屏数据源发布引用下允许编辑验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/datasources`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-datasource-edit-unlock`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。为覆盖发布引用场景，mock 将 `日均存款详图 / 9012` 的 `publishedReferenceScreenCodes` 设置为 `SCR_PROVINCE`；未修改真实数据源或数据库。

## 验收结论

1. 列表明确显示“已发布：SCR_PROVINCE”，删除按钮保持禁用，新建副本仍作为可选操作。
2. 编辑弹窗不再显示“冻结查询语义”，改为风险提示：保存后会直接影响引用该数据源的已发布大屏。
3. 发布引用场景下，状态字段可以从 `ACTIVE` 改为 `DISABLED`，业务条线、宽表、指标、周期等原冻结字段均可交互；既有数据源的来源类型仍遵守原有独立约束。
4. 保存请求 `PUT /api/screen/admin/datasources/9012` 携带 `status=DISABLED` 和非空审计原因，返回 HTTP 200，页面提示“已保存”。
5. 验收阶段 Console 为 0 error、0 warning；所有接口均被开发态 route 拦截，没有真实后端或数据库写入。

## 证据

- `01-published-reference-editable.png`：发布引用风险提示、可修改状态和语义配置。
- `02-save-success-delete-blocked.png`：保存完成后列表仍保留发布引用及删除禁用状态。
- `routes.raw.txt`：9 条开发态 mock route。
- `network.raw.txt`：保存请求、请求体和响应体。
- `console.raw.txt`：清空初始记录后的 Console 结果。
- `commands.md`：实际 CLI 操作顺序。
- `database-readonly.md`：真实 `yiti` 中 `9012` 引用状态的只读核对结果。
