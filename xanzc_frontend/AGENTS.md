# xanzc_frontend 工程开发指导

本文件只记录前端工程特有的技术和交互边界；仓库级 TDD、安全与浏览器验收门禁遵循根 `AGENTS.md`。

## 工程定位

`xanzc_frontend` 是独立的 Vue 3 + Pinia + Element Plus 单页应用，不属于 Maven 聚合，也不与后端共享构建产物。它通过 `/api` REST 对接由 `bootstrap` 启动的后端模块。

开发命令：

```bash
npm install
npm run dev
npm test
npm run build
```

依赖版本和运行时约束以 `package.json`、`package-lock.json` 的 `engines` 声明为准；当前项目要求 Node `>=18.12.1`。Vite 保持 4.x；升级到 5 或更高版本前，必须先统一目标环境 Node 版本并完成开发、构建和部署验证。

## 配置来源

- `vite.config.js` 通过 `VITE_DEV_HOST`、`VITE_DEV_PORT`、`VITE_DEV_STRICT_PORT`、`VITE_PROXY_TARGET` 配置开发服务和 `/api` 代理；环境变量优先于默认值。
- `VITE_API_BASE` 控制 API 基路径，`VITE_USE_MOCK` 仅在明确设为 `true` 时启用主动 mock。
- 不在文档里复制当前机器端口、代理目标或穿透域名。修改配置前检查对应环境文件和实际启动命令，不得为了临时联调改写共享默认值。

## Element Plus 必须全量注册

`src/main.js` 使用 `app.use(ElementPlus, { locale: zhCn })` 和全量样式，必须保持全量注册。不要重新启用 `ElementPlusResolver` 按需组件发现：Vite 开发态在首次导航到新懒加载路由时可能为新依赖触发 full reload，造成首次点击闪回、第二次才进入。

图标并未因 `app.use(ElementPlus)` 自动全局注册；使用 Element Plus 图标时显式导入，或按现有组件约定处理。

## HTTP、mock 与 fallback 边界

- `src/api/http.js` 是唯一 axios 实例，负责 traceId、平台 `ResponseWrapper` 解包、错误提示、401 会话清理和登录跳转。
- `VITE_USE_MOCK=true` 时，`call()` 直接返回调用方 fallback 并跳过网络；该模式只能用于明确标注的开发态演示。
- 真实模式下，仅 GET 查询失败可以回退调用方提供的 fallback；POST、PUT、DELETE、PATCH 等写操作失败必须抛出，禁止用 mock 伪装成功。
- `current-user`、`my-menus`、`permissions` 等安全关键查询必须直接请求后端；401、403 或网络错误不得由 fallback、旧缓存或开发 mock 掩盖。
- `src/mock/index.js` 只提供防崩溃的空形态，不是可作为联调证据的仿真数据。

## 路由与权限

- 路由使用 `createWebHashHistory`。登录恢复、菜单与资源加载均以当前后端 Session 为准。
- 主平台菜单由后端菜单树驱动；敏感路由通过 `meta.requiredResource` 在挂载业务视图前 fail-close。
- 页面隐藏、`canSee` 和路由守卫只改善用户体验，**不构成安全边界**。后端必须继续按 HTTP method + URL 执行 RBAC，并在 Service 层做数据与实体权限校验。
- 登录、退出、401 或用户切换时必须废弃旧的菜单/权限 pending 请求，防止迟到响应污染新会话。

## 视觉域隔离

- `src/views/screen/` 是大屏设计器与运行时，拥有独立画布、主题变量和全屏布局。修改时保持设计态、保存态和运行态一致，并优先检查画布高度契约与既有测试。
- `src/views/redengine/` 使用独立 `RedEngineLayout` 和 `re-` 类名前缀维护红色视觉主题，但认证仍复用平台 Session/RBAC。
- 大屏和红色引擎的深色、红色、沉浸式布局均为专属视觉系统，未经明确需求不得传播到普通平台页面；普通页面继续遵循平台现有浅色桌面体系。
- 修改上述子系统前先阅读其相邻测试与 API 封装，避免把域内约定推广成全局样式或全局组件行为。

## 测试与浏览器验收

- 组件、store、路由和工具函数优先沿用相邻 `__tests__` 中的 Vitest 写法；新增功能或修复必须先写失败测试。
- `npm test` 和 `npm run build` 不能替代真实页面验收。
- 任何前端功能、交互、样式或 API 契约变化，都必须按根 `AGENTS.md` 使用官方 `playwright-cli` 验证真实运行页面，并归档命令、路由/拦截器、console、请求/响应摘要和截图。不得用 Playwright Test、Vitest 或 MCP 代替该门禁。
- 使用 mock 时逐条列出 route 与响应并标记“仅开发态 mock，非联调”；无 mock 验收必须证明未注册 mock route 且请求到达目标服务。

## 关键文件

- `src/main.js`：Vue、Pinia、router 与 Element Plus 装配。
- `src/router/index.js`：hash 路由和全局访问守卫。
- `src/stores/user.js`、`menu.js`、`permission.js`：会话、菜单和资源快照。
- `src/api/http.js`：统一 HTTP 行为；业务 API 封装位于 `src/api/`。
- `vite.config.js`：开发服务、代理、依赖预打包和别名。
