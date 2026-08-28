# 触达页面真实浏览器验收证据（2026-08-28）

## 验收边界

- 页面：`/#/touches/mine`
- 前端：本 checkout 构建产物，临时通过 `vite preview --host 127.0.0.1 --port 8094 --strictPort` 提供
- 后端代理：本 checkout 已运行的 `18080`
- 浏览器：官方 `@playwright/cli`，Firefox
- 网络 Mock：无（`route-list` 输出 `No active routes`）

## 原始命令

```bash
NO_PROXY=127.0.0.1,localhost no_proxy=127.0.0.1,localhost \
  npx --yes @playwright/cli -s=touch-evidence-20260828 \
  open 'http://127.0.0.1:8094/#/touches/mine' --browser firefox

npx --yes @playwright/cli -s=touch-evidence-20260828 route-list
npx --yes @playwright/cli -s=touch-evidence-20260828 console warning
npx --yes @playwright/cli -s=touch-evidence-20260828 requests
npx --yes @playwright/cli -s=touch-evidence-20260828 request 4
npx --yes @playwright/cli -s=touch-evidence-20260828 response-body 4
```

## 原始结果

```text
Page URL: http://127.0.0.1:8094/#/login?redirect=/touches/mine
No active routes
Total messages: 0 (Errors: 0, Warnings: 0)
GET http://127.0.0.1:8094/api/auth/current-user => 401 Unauthorized
{"code":"AUTH-40105","message":"未登录或会话已过期","traceId":"pc-1787886569224-93316","timestamp":"2026-08-28T03:09:29.228113368Z"}
```

## 结论

真实页面请求未使用 Mock，前端路由已发起真实认证请求；但当前没有有效登录会话，被后端以 `AUTH-40105` 拦截并跳转登录页，因此本轮不能将触达任务详情、日志写入、取消和完成标记为真实联调通过。

截图：[`login-redirect.png`](./login-redirect.png)
