# 官方 CLI 命令清单（凭据已排除）

工具：`xanzc_frontend/node_modules/.bin/playwright-cli`（项目官方 CLI）；浏览器：Chromium；地址：`http://127.0.0.1:8091`。

执行过的安全步骤：

1. `ss -ltnp` 与 `/proc/<pid>/cwd`：核验 8091/18081 均由当前 checkout 启动。
2. `curl --noproxy 127.0.0.1`：只读检查 8091 登录页、18081 未认证 current-user 与 `/v3/api-docs`。
3. `playwright-cli -s=pageopt-1920 open ... --browser chromium`。
4. `playwright-cli -s=pageopt-1920 resize 1920 1080`。
5. `route-list`、`console --clear`、`requests --clear`：确认没有 mock route 并建立干净记录。
6. 登录页结构截图与快照：仅在填写凭据前保存。
7. `eval`：仅返回 URL、字段存在性、字段是否已填写和提交按钮可用性；不返回用户名或密码。
8. 单次 UI 登录重试：通过 `getByRole('button', { name: '登 录', exact: true })` 点击，并并行等待 `/api/auth/login` response、requestfailed 或 URL 变更，超时 10 秒。
9. 读取 `console`、`requests`、`route-list`，以及前端日志标记计数；不读取请求/响应正文、headers、Cookie 或 Authorization。
10. 独立 2560×1440 会话采用相同步骤；会话已关闭。

未使用：`route`/`unroute`（没有注册 mock）、`request-body`、`response-body`、`request-headers`、`response-headers`、保存/删除/导入/提交类 UI 操作。

登录凭据从不写入本目录，也不出现在本清单中。
