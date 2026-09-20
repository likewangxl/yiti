# 个人经营驾驶舱官方 CLI 验收

- 日期：2026-09-20
- 页面：`http://127.0.0.1:8092/#/personal-dashboard`
- 工具：`xanzc_frontend/node_modules/.bin/playwright-cli`，Chromium，会话 `personal-qa`
- 视口：1920×1080、1440×900、390×844
- 数据边界：**仅开发态 mock，非联调**。所有响应由 `scripts/register-mocks.js` 通过一次 `run-code` 注册；未知 API 返回 `QA_UNMATCHED` 404，避免请求触达真实服务。
- 真实系统：不登录真实账号，不改真实后端、数据库、Session 或生产路由。

## 执行

在 `yiti/xanzc_frontend` 目录执行：

```bash
./../docs/superpowers/evidence/2026-09-20-personal-dashboard/scripts/run-qa.sh
```

脚本使用 bundled Node 路径和官方 CLI，不安装依赖。目标服务未运行时脚本在开始处停止；服务启动后会把截图、原始 console、请求/响应摘要、断言和 mock 路由清单写入 `/tmp/personal-dashboard-evidence/`。

## Mock 路由清单

完整路径和合成响应见 `scripts/register-mocks.js`，`fixtures/README.md` 记录场景边界。基础场景覆盖：

`/api/auth/current-user`、`/api/auth/my-menus`、`/api/auth/permissions`、`/api/portal/workspace`、`/api/workflow/tasks`、`/api/touch-tasks?status=PENDING`、`/api/touch-tasks?status=IN_PROGRESS`、`/api/marketing/customers/mine`、`/api/marketing/asset-projects`、`/api/support-requests`，以及客户详情、触达详情和日志、资产详情、中台支持详情、客户候选、支持产品、机构树和字典请求。

`qa=partial` 将工作流待办设为 500、客户列表设为 403，验证错误区独立可见且指标、触达和业务进度保留。`qa=unauth` 将当前用户设为 401，验证未登录跳转 `/login`。

## 证据文件

截图和原始 CLI 输出外置在 `/tmp/personal-dashboard-evidence/`，命名包括 `base-1920x1080.png`、`base-1440x900.png`、`base-390x844.png`、对应的 `-full.png`、`partial-1920x1080.png`、`routes.raw.txt`、`requests-*.raw.txt`、`console-*.raw.txt`、`assertions-*.json` 和 `commands.log`。请求清单中的状态码和响应摘要来自官方 CLI `requests` 输出；没有另行生成未执行的 response 文件。截图右上角由浏览器脚本注入“仅开发态 mock · 非联调”标签，不修改生产页面源代码。

基础场景 console 期望为 0 error/0 warning；partial 场景的 500/403 和 unauth 场景的 401 是该场景故意注入的 HTTP 错误，原始输出分别保存在 `console-partial.raw.txt` 与 `console-unauth.raw.txt`。

## 验收范围

基础场景检查四区、0/null、返回完成率、长名称、受限触达、刷新和全屏提示；点击客户条目只打开详情抽屉，点击触达条目只打开详情对话框，不提交任何写操作；通过进度入口验证资产立项和中台支持路由；部分 403/500 场景验证其余区域继续呈现；未登录场景验证认证守卫。
