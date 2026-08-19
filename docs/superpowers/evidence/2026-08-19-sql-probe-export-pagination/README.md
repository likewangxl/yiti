# SQL 探查分页与导出选项验收证据

## 验收范围

- 页面：`http://127.0.0.1:8090/#/report/sql`
- 工具：官方 `@playwright/cli@0.1.18`（命令名 `playwright-cli`）
- 会话：`sql-probe-export-20260819`
- 前端：当前 checkout 已运行的 Vite 页面；页面模块通过 Vite HMR 加载本次源码
- 后端：现有进程早于本次后端提交，且 `/actuator/health` 返回 500，因此本次仅做真实页面加网络拦截的前端契约验收，不把它作为新后端或 OBS 联调通过的证据

## 网络拦截

为进入受保护页面并稳定验证交互，仅拦截以下接口：

- `**/api/auth/current-user`
- `**/api/auth/my-menus`
- `**/api/auth/permissions`
- `**/api/notifications/unread-count`
- `**/api/reports/sql-probe/schema-whitelist`
- `**/api/reports/sql-probe/history?*`
- `**/api/reports/sql-probe/export/tasks`
- `**/api/reports/sql-probe/export`

所有拦截响应均为测试数据；未连接真实数据库或 OBS，未执行真实 SQL。

## 关键命令与结果

```bash
npx --yes @playwright/cli@0.1.18 -s=sql-probe-export-20260819 goto 'http://127.0.0.1:8090/#/report/sql'
npx --yes @playwright/cli@0.1.18 -s=sql-probe-export-20260819 click <查看历史按钮引用>
npx --yes @playwright/cli@0.1.18 -s=sql-probe-export-20260819 requests
```

历史请求：

```text
GET /api/reports/sql-probe/history?pageNo=1&pageSize=5 => 200 OK
```

页面快照显示 5 行历史记录、总数 12、共 3 页，且没有每页条数选择器。

```bash
npx --yes @playwright/cli@0.1.18 -s=sql-probe-export-20260819 fill <执行原因输入框引用> 'SQL导出分页验收'
npx --yes @playwright/cli@0.1.18 -s=sql-probe-export-20260819 click <下载按钮引用>
```

下载选项弹窗默认值为 1000，并显示“请输入 1 至 50000 条，默认下载 1000 条”。把数值改为 50000 后确认，创建任务请求为：

```json
{
  "sql": "<AES 加密 SQL，证据中不保存密文>",
  "remark": "SQL导出分页验收",
  "exportCount": 50000
}
```

接口结果：

```text
POST /api/reports/sql-probe/export => 200 OK
GET  /api/reports/sql-probe/export/tasks => 200 OK
```

干净页签完成交互后的 CLI console：`Errors: 0, Warnings: 0`。

## 截图

- `history-page-size-5.png`：历史弹窗 5 行、总数 12、3 页
- `export-default-1000.png`：下载条数默认 1000、范围提示 1 至 50000

## 未覆盖项

- 未用本次新构件重启后端，未发起真实 SQL 导出任务。
- 未验证真实 OBS 上传、OBS 下载流、超过 10000 行 ZIP 内容及部署环境临时目录清理；这些链路由后端单元/控制器测试覆盖，仍需集成环境验收。
