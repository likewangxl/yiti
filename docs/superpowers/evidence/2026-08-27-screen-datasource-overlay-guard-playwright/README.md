# 大屏数据源编辑遮罩交互验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8091/#/screen-admin/datasources`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-datasource-overlay`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。注册 8 条只读 route，未注册或触发任何写接口。

## 验收结论

1. 数据源列表返回 1 条验收数据，打开其“编辑数据源”弹窗。
2. 将名称从“日均存款详图”改为“日均存款详图-未保存”，在弹窗外遮罩处点击。
3. 点击后可见编辑弹窗仍为 1 个，标题仍为“编辑数据源”，未保存名称完整保留。
4. Console 为 0 error、0 warning；仅有 Vite 连接调试日志。
5. 网络共 8 个 API 请求，全部为开发态 route 返回的只读 GET，无 POST、PUT、PATCH 或 DELETE，未修改后端或数据库。

## 证据

- `01-editor-remains-open-after-backdrop-click.png`：点击遮罩后仍可见的编辑弹窗与未保存名称。
- `interaction.raw.txt`：点击前后弹窗数量、标题、表单值和遮罩数量。
- `routes.raw.txt`：8 条开发态 mock route 清单。
- `network.raw.txt`：原始 API 请求摘要和数据源列表响应。
- `console.raw.txt`：原始 Console 摘要。
- `commands.md`：实际 CLI 操作顺序。
