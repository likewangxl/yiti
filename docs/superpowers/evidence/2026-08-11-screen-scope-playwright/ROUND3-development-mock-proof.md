# Round 3 发布/回滚证据修正（官方 Playwright CLI）

> **仅开发态 mock，非 `yiti_test`、非真实后端联调。**
>
> 本文件是针对发布/回滚证据可复现性、route 完整性及 console 原始计数的修正记录。浏览器仅访问 `http://127.0.0.1:18194` 本地 Vite；所有 `/api/**` 由当前 browser context 的官方 Playwright CLI route 返回。未连接数据库、`yiti_test`、真实后端或 Vite proxy 上游。

## 可复跑命令与完整 route

```bash
cd /home/djdev/leid/yiti/xanzc_frontend
npm run dev -- --host 127.0.0.1 --port 18194
npx playwright-cli -s=screen-round3-proof-clean open --browser=chromium about:blank
bash /home/djdev/leid/yiti/docs/superpowers/evidence/2026-08-11-screen-scope-playwright/ROUND3-development-mock-routes.sh screen-round3-proof-clean
npx playwright-cli -s=screen-round3-proof-clean goto http://127.0.0.1:18194/#/screen-admin/designer
npx playwright-cli -s=screen-round3-proof-clean route-list
```

脚本 [ROUND3-development-mock-routes.sh](ROUND3-development-mock-routes.sh) 包含全部 19 条 `playwright-cli route` 原始命令、完整 body、status 与 content type，可直接由 `bash` 重放。官方 CLI 的 route-list 只截断**预览中的 body**；完整、未截断的 route pattern/status/content-type 清单见 [ROUND3-development-mock-route-list.full.txt](ROUND3-development-mock-route-list.full.txt)，完整 body 以脚本为唯一来源。

官方 CLI `route-list` 的逐字 stdout 也已归档在 [ROUND3-development-mock-route-list-cli.raw.txt](ROUND3-development-mock-route-list-cli.raw.txt)；其中 body 省略号是 CLI 固有的展示截断，不是归档省略。

最终 route 数：**19**。没有未注册的真实接口 fallback。

## 发布：原因对话框、真实 body 与响应

提交前的真实 UI：标题“发布大屏”、版本 7、必填“发布原因”、独立测试标识 `screen-publish-reason`，以及“先保存当前草稿，再以最新版本提交发布”的 CAS 文案。截图：[16-round3-proof-publish-reason-dialog-development-mock.png](16-round3-proof-publish-reason-dialog-development-mock.png)。

原始请求清单中的发布序列：`#206 POST /api/screen/admin/canvas/save`、`#207 POST /api/screen/admin/canvas/publish`、`#208 GET /api/screen/admin/canvas/101`，均为 200。完整清单见 [ROUND3-development-mock-requests-final.raw.txt](ROUND3-development-mock-requests-final.raw.txt)。

`#207` 原始 body：[ROUND3-development-mock-publish-request-body.json](ROUND3-development-mock-publish-request-body.json)。响应：[ROUND3-development-mock-publish-response.json](ROUND3-development-mock-publish-response.json)。

## 回滚：原因对话框、真实 body 与响应

提交前的真实 UI：标题“回滚大屏”、目标归档 `2026-08-12 09:00:00`、版本 7、必填“回滚原因”、独立测试标识 `screen-rollback-reason`，以及 CAS 冲突重载文案。截图：[17-round3-proof-rollback-reason-dialog-development-mock.png](17-round3-proof-rollback-reason-dialog-development-mock.png)。

原始请求清单中的回滚序列：`#209 GET /api/screen/admin/canvas/101/publish-logs`、`#210 POST /api/screen/admin/canvas/rollback`、`#211 GET /api/screen/admin/canvas/101`，均为 200。

`#210` 原始 body：[ROUND3-development-mock-rollback-request-body.json](ROUND3-development-mock-rollback-request-body.json)。响应：[ROUND3-development-mock-rollback-response.json](ROUND3-development-mock-rollback-response.json)。

## 原始 console 与计数

官方 CLI 的 `console debug` 原始输出在 [ROUND3-development-mock-console-final.raw.txt](ROUND3-development-mock-console-final.raw.txt)：总计 2 条 Vite DEBUG，`Errors: 0`、`Warnings: 0`。文件中 `[WARNING]` 行数为 **0**，与 CLI 汇总完全一致；未隐藏任何 warning。

本次修正前曾主动丢弃两个不干净会话：一个在 route 注册前加载登录页，另一个含 favicon 404。两者均未用于本文件的请求、console 或截图结论。
