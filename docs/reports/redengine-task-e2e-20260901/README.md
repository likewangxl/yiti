# 红色引擎任务/RBAC 集成验收证据

日期：2026-09-01（Asia/Shanghai）

本目录仅保存本 checkout 的集成验收证据，不包含业务源码修改。验收必须使用官方 `playwright-cli` 0.1.18、`redengine-task-e2e` 隔离 profile 和 `yit_test` 数据库；禁止使用 `red-engine-center` 的 `application-test.yml` / `onepl_test_bootstrap`。

## 当前阶段

- MySQL `127.0.0.1:3306` 已恢复响应，`yit_test`、`yiti`、`yiti_test` 均存在；截至本记录，未对任何数据库执行 DML/DDL/DELETE。
- `yit_test` 任务相关表在验收前只读盘点为 0 行，候选角色/党组织映射已只读核对；启动前 ACT_* 快照见 `commands/06-prestart-db-third-attempt.md`。
- 后端第三次启动成功：`redengine-task-e2e` profile，Flowable 同步引擎开启，Quartz、异步执行器/异步历史、外发、OBS 关闭；PID/cwd 和响应证据见 `commands/07-backend-start-success.md`。
- 前端已在 8093 启动，代理 18091 且 `VITE_USE_MOCK=false`；PID/cwd 和 HTTP 200 证据见 `commands/08-frontend-start.md`。
- 官方 `@playwright/cli` 版本为 0.1.18，Chromium 使用本机已有 1237 缓存；CLI 会话使用 `PWTEST_DAEMON_SESSION_DIR` 可写临时目录。
- 已打开真实红色引擎登录页并验证无 mock route。页面演示账号 `admin/admin123` 通过真实后端返回 401，业务页面验收因此暂停；不得在未获授权时枚举其他密码。失败证据见 `routes/sys-admin-login.route-list.txt`、`network/sys-admin-login-summary.txt`、`console/sys-admin-login.txt` 和 `screenshots/sys-admin-login-401.png`。
- 业务验收（任务创建/状态流转/导出/附件）尚未执行，因此没有任务 DML，也未注册文件开发态 mock。
- 四个目标角色及 `yit_test` 账号/党组织映射的只读盘点见 `commands/09-roles-pre-credential.md`。临时口令准备尚未执行：安全审查阻止读取/备份原始 `PT_USER.PWD`，待取得直接凭据操作授权或改用已有测试认证方案。

## 证据约束

- `commands/`：精确命令及退出状态。
- `routes/`：每个 session 的 `route-list` 原文；真实联调应为空。仅文件上传/下载/附件 ZIP 的开发态 mock 必须逐条列出并标注“仅开发态 mock，非联调”。
- `console/`：CLI `console` 原文。
- `network/`：CLI `requests`、关键 `request`/`response` 的脱敏摘要；不要保存密码、Cookie、Authorization、哈希、手机号或附件内容。
- `screenshots/`：关键页面和状态截图，文件名带角色和页面。

## 暂停条件

父代理已发现后端 P1，任务业务页面路径暂缓执行；未获得继续通知前不得创建任务、提交任务或运行审核状态链路。OBS 在隔离 profile 中关闭，真实附件上传、下载、ZIP/Excel 导出不能以开发态 mock 结果替代真实联调结论。
