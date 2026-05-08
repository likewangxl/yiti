# xanzc_frontend

> 银行营销 · 业务执行 · 绩效平台 — PC 前端
>
> 上线日：2026-06-10 · Vue 3 + Element Plus

## 技术栈

- Vue 3.4 + `<script setup>` + Composition API
- Vue Router 4（hash mode）+ 全局守卫（未登录跳 `/login?redirect=`）
- Pinia（user store，sessionStorage 持久化）
- Element Plus 2.5（全量注册）+ ECharts 5（vue-echarts）
- Vite 4.5（**注意 Node 16 兼容**，不要升 5）+ SCSS（design tokens）
- Axios（带 `withCredentials` + traceId 注入 + ResponseWrapper 解包 + 401 自动跳登录）

## 目录结构

```
src/
├── api/            # 后端接口封装（http.js + 业务模块）
│   ├── http.js     # axios 实例 + ResponseWrapper 解包 + unwrapPage 分页助手
│   ├── auth.js
│   ├── workspace.js
│   ├── perf.js / report.js / system.js
│   └── index.js
├── components/     # 全局组件（AppHeader / AppSidebar / AppBreadcrumb）
├── layouts/        # DefaultLayout（侧边栏 + 头部 + 内容区）
├── mock/           # 全部 mock 数据（VITE_USE_MOCK=true 时启用）
├── router/         # 路由表 + 全局守卫
├── stores/         # Pinia stores（当前只 user）
├── styles/         # tokens.scss + index.scss
├── views/
│   ├── login/
│   ├── workspace/
│   ├── perf/
│   ├── report/
│   └── system/
├── App.vue
└── main.js
```

## 开发启动

```bash
# 1. 安装依赖（Node 16；vite 4 必须）
npm install --legacy-peer-deps --no-fund --no-audit

# 2. 启动 dev 服务器（端口 8090）
npm run dev
# or 显式：
NO_PROXY=localhost,127.0.0.1 ./node_modules/.bin/vite --port 8090 --host 0.0.0.0
```

> Linux/sandbox 环境下若有全局 http_proxy，需带 `NO_PROXY=localhost,127.0.0.1` 否则
> `curl http://localhost:8090/` 会被代理拦截误报 502。

打开 `http://localhost:8090/`：

- 自动跳到 `#/login?redirect=/workspace`
- 默认账号 `admin / 123456`（由 yiti `PT_USER` 表 seed）

## 后端联调

- yiti 后端默认 8080；vite proxy 配置 `/api` → `http://localhost:8080`
- mock 模式：`.env.development` 设 `VITE_USE_MOCK=true`，所有 api 直接返 mock 数据
- 真实模式：`VITE_USE_MOCK=false` + yiti 启动后自动走真接口
- 失败兜底：真接口失败时 `call()` 会自动用 mock 兜底（控制台 `[api fallback]` warn）

## 关键设计点

### 1. 响应包装解包（`api/http.js`）

yiti `ResponseWrapper` 形如：

```json
{ "code": "0", "message": "success", "traceId": "...", "data": ..., "page": {...} }
```

- `code === "0"` 视为成功
- 业务失败：`ElMessage.error(message)` 后 reject
- 401：`gotoLogin()` 自动跳 `/login`，并把当前 hash 写入 redirect query
- 分页响应：`page` 字段优先于 `data`（PageResult 走 page）
- `unwrapPage(r)`：自动从 `records / list / content / rows / data` 抽数组，view 不用关心

### 2. mock-first 开发模式

每个 view 按照下面这个固定模式：

```js
import { mockData } from '@/mock';
import { someApi } from '@/api/...';

const data = ref(mockData);
onMounted(async () => {
  try { const r = await someApi(); if (r) data.value = r; } catch {}
});
```

后端没起 / 接口出错时永远有 mock 兜底，UI 不会白屏。

### 3. 路由守卫

`router/index.js` 有全局 `beforeEach`：

- `meta.public === true` 的路由（如 `/login`）放行
- 否则检查 Pinia user store；未登录跳 `/login?redirect=<原路径>`
- 登录成功后 `LoginPage` 读 `route.query.redirect` 跳回去

## 与 yiti 后端的对应

| 前端模块 | yiti 模块 | 主要 endpoint |
|---|---|---|
| `views/workspace/` | portal-content-center | `/api/portal/workspace`, `/api/notifications/*` |
| `views/perf/` | performance-engine-center | `/api/perf/*` |
| `views/report/` | report-analytics-center | `/api/reports/*`, `/api/dynamic-query/*` |
| `views/system/` | system-governance-center / auth-permission-center | `/api/admin/sys/*`, `/api/admin/roles/*`, `/api/admin/biz-scopes/*`, `/api/admin/resources/*` |

## 已知约束

- **Vite 必须 4.x**：vite 5 用 `crypto.getRandomValues` Node 16 没有
- **Element Plus 全量注册**：不要用 `unplugin-vue-components` 的 ElementPlusResolver 按需，
  否则 vite 会在路由切换时懒发现新依赖触发 `optimized dependencies changed. reloading`，
  导致"点新菜单 URL 闪一下却回到原页面，再点一次才进去"的 bug
- **后端表名约定**：业务表全小写（mapper SQL 用小写），但 `PT_*` / `EXT_*` / `ACT_*` / `QRTZ_*` 大写
- **新接口必须先登记 PT_RESOURCE**：否则 RBAC 拦截器返 `AUTH-40302 资源未登记`

## 上线倒排（W0=2026-05-04 → W6=2026-06-10）

详见 `银行营销绩效平台-倒排计划-2026-05-06.md`。
