# 大屏中心个人入口官方 CLI 验收

- 页面：`http://127.0.0.1:8092/#/screens`
- 工具：`xanzc_frontend/node_modules/.bin/playwright-cli`
- 建议会话：`personal-center-review`
- 数据边界：**仅开发态 mock，非联调**。脚本只拦截 `/api/` 下登记的认证、权限和大屏目录请求，不登录真实账号、不改后端或数据库。

## 执行

主代理在前端开发服务已运行时执行：

```bash
cd /Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/xanzc_frontend
export PATH="/Users/likewang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
mkdir -p /tmp/personal-screen-center-evidence
./node_modules/.bin/playwright-cli -s=personal-center-review open about:blank --browser=chrome
./node_modules/.bin/playwright-cli -s=personal-center-review run-code --filename ../docs/superpowers/evidence/2026-09-20-personal-dashboard/scripts/register-mocks.js
./node_modules/.bin/playwright-cli -s=personal-center-review run-code --filename ../docs/superpowers/evidence/2026-09-20-personal-screen-center/scripts/review.js
```

先复用上一轮个人驾驶舱基础 mock，再注册本轮中心入口的窄路由；后注册的中心路由覆盖基础 mock 的同名请求。脚本覆盖空机构目录下的授权个人卡片、中文搜索、零售筛选隐藏、点击进入 `#/personal-dashboard?from=screen-center`、返回 `#/screens`，以及无 `/workspace` 菜单时不显示个人卡片。截图和运行输出写入 `/tmp/personal-screen-center-evidence/`。

## Mock 路由

本轮脚本额外登记：

- `/api/auth/my-menus`：`qa=base` 返回 `/workspace`，`qa=nopersonal` 不返回 `/workspace`。
- `/api/auth/permissions`：返回 `/api/screen/view/*`，允许大屏中心路由守卫通过。
- `/api/screen/view/catalog`：返回空数组，验证个人入口不依赖机构目录条目。

个人驾驶舱业务请求复用上一轮合成响应；未登记的 API 返回 `QA_UNMATCHED` 404，不访问真实服务。所有响应均为“仅开发态 mock，非联调”。

## 最近一次结果

主代理的 `personal-center-review` 会话已通过，`/tmp/personal-screen-center-evidence/review.log` 的 Result 字段全部为 `true`。原始 `open`、基础 mock、review、console、requests、route-list 输出和截图 `center.png` 均保存在该目录；结果只证明前端合成流程，不代表真实后端业务联调。
