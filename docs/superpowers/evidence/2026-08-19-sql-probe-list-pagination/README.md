# SQL 探查列表分页验收证据

## 验收范围

- 页面：`http://127.0.0.1:8090/#/report/sql`
- 工具：官方 `@playwright/cli@0.1.18`
- 会话：`sql-probe-pagination-20260819`
- 下载任务：固定每页 5 条，由后端分页返回
- 查询历史：固定每页 10 条，由后端分页返回

## 运行态检查

- 后端 `18080`、SOAP `30522`、前端 `8090` 均处于监听状态。
- `GET http://127.0.0.1:18080/v3/api-docs` 返回 HTTP 200。
- 后端直连与前端代理的未登录 `current-user` 均返回 HTTP 401。
- 新加载的 OpenAPI 中，`GET /api/reports/sql-probe/export/tasks` 已接收 `PageRequest` 查询参数对象。

## 浏览器验收方式

当前环境没有可用于受保护页面的真实登录凭据，因此仅在开发态通过 Playwright 网络路由模拟当前用户、权限、白名单和两类分页响应。模拟数据不连接真实数据库，也不作为真实鉴权、SQL 执行或 OBS 联调通过的证据。

分页响应数据：

- 下载任务：`pageSize=5`、`total=11`、3 页，每次返回 5 条。
- 查询历史：`pageSize=10`、`total=21`、3 页，每次返回 10 条。

页面与切页请求：

```text
GET /api/reports/sql-probe/export/tasks?pageNo=1&pageSize=5 => 200 OK
GET /api/reports/sql-probe/export/tasks?pageNo=2&pageSize=5 => 200 OK
GET /api/reports/sql-probe/history?pageNo=1&pageSize=10 => 200 OK
GET /api/reports/sql-probe/history?pageNo=2&pageSize=10 => 200 OK
```

页面快照确认：

- 下载任务区域显示 5 行、共 11 条、3 页，点击第 2 页后第 2 页高亮。
- SQL 探查历史弹窗显示 10 行、共 21 条、3 页，点击第 2 页后第 2 页高亮。
- 干净验收页签控制台：`Errors: 0, Warnings: 0`。

## 截图

- `download-tasks-page-size-5.png`：下载任务 5 行与分页控件。
- `history-page-size-10.png`：查询历史 10 行与分页控件。
