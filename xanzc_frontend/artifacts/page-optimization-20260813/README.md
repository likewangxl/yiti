# 页面优化真实 QA 归档（阻断）

日期：2026-08-13
范围：59 条普通后台命名路由及其只读弹窗/抽屉；排除红色引擎、大屏运行态与大屏设计器。
工具：项目官方 `node_modules/.bin/playwright-cli`，没有注册 mock route。

## 结论

**未通过，也不能宣称完成页面 QA。** 两个独立视口会话都在真实账号登录前被阻断：`POST /api/auth/login` 返回 `net::ERR_ABORTED`，因此没有进入工作台，实际业务路由扫描为 **0/59**，也未执行任何保存、删除、导入、提交或其他业务写操作。

这不是本轮 CRUD 页面变更的浏览器通过证据；它是可复现的环境/登录前置阻断证据，需由主代理裁决恢复环境后再从干净会话重新执行完整扫描。

此前大屏 v2 的相关代码已完成，但 v2 E2E 仍受既有 **RPT-43017** 阻断；本次不改权限、不重试该场景，仅将它保留为与本次登录阻断并列的既有验收限制，绝不据此宣称大屏或普通后台验收通过。

## 已核验的事实

| 项目 | 结果 |
|---|---|
| 8091 前端归属 | Vite cwd 为当前 `xanzc_frontend` checkout |
| 18081 后端归属 | Java cwd 为当前仓库 checkout |
| 8091 登录页 | HTTP 200 |
| 18081 未认证 current-user | HTTP 401（预期） |
| 18081 `/v3/api-docs` | 5 秒与 20 秒两次读取均超时（000） |
| 1920×1080 route-list | 起止均 `No active routes` |
| 2560×1440 route-list | 起止均 `No active routes` |
| 当前 1920 登录表单 | `/#/login?normal`，账号/密码字段均存在且已填写，提交按钮可用；字段值未归档 |
| console | 1920：0 error / 0 warning；2560：0 error / 0 warning |
| `[api fallback]` | 未发现 |
| 意外业务写请求 | 未发现；唯一允许的登录 POST 失败 |

## 登录阻断原始摘要

- 1920×1080：清空 console/network 后，以明确 locator 执行唯一登录重试；10 秒未收到 response 或 URL 变更，随后 requests 显示 `POST /api/auth/login => [FAILED] net::ERR_ABORTED`。
- 2560×1440：独立会话同样显示该唯一登录 POST 为 `net::ERR_ABORTED`，随后关闭会话。
- 前端当前 Vite 日志的 `auth/login`、`proxy error`、`error`、`ECONN`、`ERR` 标记均为 0。后端 stdout/stderr 均连接运行中的 `/dev/pts/4`，没有可读取的文件日志；本次不操作该运行进程。

## 证据目录

- [included-excluded-routes.md](included-excluded-routes.md)：完整 59 路由矩阵、排除范围和未开始原因。
- [command-manifest.md](command-manifest.md)：使用的官方 CLI 命令类别与禁止项。
- [preflight](preflight/)：进程归属、HTTP 健康检查、无敏感字段值的表单状态、干净 route-list、登录前截图/快照和敏感信息扫描。
- [1920x1080](1920x1080/)：当前 1920 会话的 route-list、console、请求摘要。
- [2560x1440](2560x1440/)：独立 2560 会话的 route-list、console、请求摘要和关闭说明。
- [raw](raw/)：两视口的原始 CLI 摘要副本；不含请求/响应正文或任何认证材料。

目录中不保存密码、Cookie、session、Authorization 或敏感响应正文。登录前截图仅展示空表单；填充后快照已在归档前删除，随后敏感扫描无匹配。

`preflight/console-after-login-1920.txt` 与 `preflight/requests-after-login-1920.txt` 是进入本次最终阻断步骤前已有的 preliminary 记录，保留以免丢失历史线索，但**不用于本次页面 QA 结论**；本次最终 1920 原始结果以文件名含 `current` 的记录及 `raw/1920/` 为准。
