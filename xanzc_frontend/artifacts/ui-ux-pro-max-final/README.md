# UI/UX Pro Max 官方浏览器验收归档

## 结论

本归档结论为：**前端页面结构验收通过，真实联调部分失败**。

- 验收工具：官方 `playwright-cli`，版本 `0.1.18`；使用 Chromium for Testing。
- 验收时间：2026-08-12（Asia/Shanghai）。
- 前端服务：真实 Vite `http://127.0.0.1:8091`，状态探测返回 200。
- 后端服务：真实 `http://127.0.0.1:18081`；未登录 `GET /api/auth/current-user` 返回 401，认证 state 加载后工作台 API 返回 200。
- 认证：按要求执行 `open -> state-load /tmp/yiti-uiux-auth-state.json -> goto`。临时 state 仅加载使用，未复制、未归档、未读取 cookie/storage 内容；未提交登录凭据。
- mock 证明：两套验收会话的 `route-list` 原始输出均为 `No active routes`；未注册 mock route。
- 业务操作：只读路由巡检；侧栏折叠/展开与页签聚焦是唯一交互；未执行新增、编辑、删除、提交、审批、导入、导出或上传。

## 两视口路由统计

指定的 47 条只读路由在两种视口均逐条由官方 CLI `goto` 导航，再执行等待和 DOM `eval`。汇总 JSON：

| 视口 | 路由数 | 唯一路由 | `main` 数量 | 横向溢出 | 登录重定向 | 专用 no-access 页 | 页面级错误文本 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1920×1080 | 47 | 47 | 全部 1 | 0 | 0 | 0 | 0 |
| 2560×1440 | 47 | 47 | 全部 1 | 0 | 0 | 0 | 0 |

对应文件为 `json/routes-1920.json`、`json/routes-2560.json`，分组原始 CLI 输出为 `raw/15-routes-1920-group*.txt` 和 `raw/22-routes-2560-group*.txt`。

`/system/permission` 曾因页面业务说明中的 `NONE 无权限` 被过宽的初版文本探针误报；已改为只检查 `main.no-access`、`h1#no-access-title` 或 `#/no-access`，修正结果见 `raw/17-corrected-system-permission-1920.txt` 和 `json/route-1920-system-permission-corrected.json`。初版脚本错误 `TypeError: __fn__ is not a function` 原始输出保留在 `raw/13-routes-1920-part1.txt`，不计入统计。

## 真实联调失败

`/system/audit` 在 2560×1440 新认证会话中真实请求：

`GET /api/admin/sys/audit-logs?pageNo=1&pageSize=20` → **500 Internal Server Error**。

官方 CLI error console 与 API URL/status 原始输出见 `raw/35-known-audit-500-2560.txt`。该失败已知为现有 `yiti` schema 漂移：数据库 `AUDIT_LOG` 实际列结构与代码查询所需 `target_type` 等列不一致；本次验收未执行任何 DDL/DML，也未修改数据库。该项因此不宣称真实联调全通过。

其余 `/system/audit` 页面请求在同一证据中为 200；路由结构本身仍满足 `main=1`、无溢出、无登录重定向的页面检查。

## Console 原始结果

- 1920×1080 工作台：0 errors、15 warnings。
- 2560×1440 工作台：0 errors、15 warnings。
- 两视口 warnings 均为既有数据库菜单包含但前端没有对应路由的 Vue Router warning，涉及 `/customers/*`、`/touches/*`、`/bizexec/*`、`/yundun/*`；原始输出完整保留在 `raw/23-console-1920-final.txt`、`raw/27-console-2560-final.txt`，未静默删除。
- `/system/audit`：1 个真实 500 resource error、16 warnings；原始 error 和请求摘要见 `raw/35-known-audit-500-2560.txt`。

## API 请求摘要

请求证据只保存 URL/status 摘要，未调用 `request`、`request-body` 或 `response-body`：

- 1920×1080 工作台：9 条动态 `/api/` 请求，9×200；见 `raw/25-requests-1920-auth-session.txt`。
- 2560×1440 工作台：9 条动态 `/api/` 请求，9×200；见 `raw/26-requests-2560-auth-session.txt`。
- 2560×1440 `/system/audit`：5×200、1×500；见 `raw/35-known-audit-500-2560.txt`。

## 登录页与只读交互

登录页 `/#/login?normal` 未提交凭据，两种视口均满足：最终 URL 正确、`h1=1`、`main=1`、无横向溢出、无页面级错误文本、密码输入框 1 个、可见“登 录”按钮 1 个。结构 JSON：`json/login-1920.json`、`json/login-2560.json`；安全截图：

- `screenshots/login-1920x1080.png`
- `screenshots/login-2560x1440.png`

只读交互原始输出：

- 侧栏：展开宽度 220、折叠宽度 64，`aria-expanded` 从 `true` 变 `false` 后恢复；见 `raw/31-sidebar-collapse-expand-1920-corrected.txt`。
- 页签：`nav[aria-label="工作区页签"]` 可见，点击“公告列表”页签后焦点在页签按钮内且可见；见 `raw/32-tabs-focus-visible-1920.txt`。

## 证据文件说明

- `command-manifest.txt`：实际官方 CLI、服务探测、认证、路由分组、console、requests、截图和交互命令清单。
- `raw/`：官方 CLI 原始输出；包含 route-list、state-load、goto、console、requests、eval、交互和截图命令输出。
- `json/`：仅由官方 CLI DOM eval 输出整理的结构化结果；不含 cookie、storage state、响应正文或敏感请求头。
- `scripts/`：路由 DOM 检查、登录 DOM 检查、侧栏/页签检查及官方 CLI 分批驱动脚本。
- `screenshots/`：仅未登录登录页截图，不归档用户、客户、账号、金额或通知业务数据。

敏感字段扫描已完成：未发现密码值、token、Authorization、Cookie、Set-Cookie、响应正文或敏感请求头；`passwordFieldCount` 仅表示登录页存在密码输入控件，不是密码值。
