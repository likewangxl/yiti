# Round 2 恢复静态路由、拦截器与 fallback 清单

本文件是对本次实际运行的前端源代码的只读检查；未修改产品代码。

## 受测路由

`xanzc_frontend/src/router/index.js` 注册了以下四个受测管理端路由及后端权限资源：

| 路由 | 页面组件 | `requiredResource` |
| --- | --- | --- |
| `#/screen-admin/designer` | `views/screen/designer/DesignerV2.vue` | `/api/screen/admin/screens` |
| `#/screen-admin/datasources` | `views/screen/admin/Datasources.vue` | `/api/screen/admin/datasources` |
| `#/screen-admin/org-groups` | `views/screen/admin/OrgGroups.vue` | `/api/admin/org-groups` |
| `#/screen-admin/org-profiles` | `views/screen/admin/OrgProfiles.vue` | `/api/admin/org-profiles` |

## 运行时网络路径

- 受控 Vite 命令显式设置 `VITE_USE_MOCK=false`、`VITE_API_BASE=/api`、`VITE_DEV_HOST=127.0.0.1`、`VITE_DEV_PORT=8092`、`VITE_DEV_STRICT_PORT=true`、`VITE_PROXY_TARGET=http://127.0.0.1:18082`；原始命令和 Vite ready 输出见 `round2-recovery-04-official-cli-login-and-routing.raw.txt`。
- `xanzc_frontend/vite.config.js` 解析 `VITE_PROXY_TARGET`，并在 `server.proxy['/api']` 中将其设为 target。
- `xanzc_frontend/src/api/http.js` 将 `USE_MOCK` 定义为 `import.meta.env.VITE_USE_MOCK === 'true'`，并将每个 `call()` 请求发往 `API_BASE + url`。
- CLI 路由列表的初始和最终输出均为 `No active routes`，没有注册 mock/拦截 route。
- 实际 CLI 请求均经 `127.0.0.1:8092/api/...` 获得 200；后端日志记录同一类处理器在 `http-nio-127.0.0.1-18082-exec-*` 执行，见 `round2-recovery-11-proxy-and-runtime-log-gate.raw.txt`。

## HTTP 拦截器与 fallback

`http.js` 的请求拦截器仅注入 `X-Trace-Id`；响应拦截器只解包 ResponseWrapper、处理 401/4xx/5xx。`call()` 在 `USE_MOCK=false` 时先发真实 HTTP；只在传入 fallback 且 GET 请求失败时才记录：

```text
[api fallback] <METHOD> <url> 失败，使用 mock 兜底
```

本次 CLI 的完整控制台原始输出见 `round2-recovery-05-cli-scoped-pages.raw.txt`：每页为 0 errors、13 条既有 Vue Router sidebar 警告，未出现该 fallback 文本。所有页面请求摘要为 HTTP 200；因此没有以 fallback/mock 作为本次结果。

虽然 `screen.js` 的若干列表读取函数保留 GET fallback 参数以兼容开发期骨架，但运行时 `VITE_USE_MOCK=false`、无路由拦截、真实 200、无 fallback console 四项证据共同证明本次结果来自隔离后端，而非 mock。
