# 前端启动核验

执行日期：2026-09-01（Asia/Shanghai）

## 启动命令

在 `/home/djdev/leid/yiti/xanzc_frontend` 执行：

```bash
VITE_DEV_HOST=127.0.0.1 \
VITE_DEV_PORT=8093 \
VITE_DEV_STRICT_PORT=true \
VITE_PROXY_TARGET=http://127.0.0.1:18091 \
VITE_USE_MOCK=false \
npm run dev
```

Vite 输出 `Local: http://127.0.0.1:8093/`、`ready`。

## 现场核验

- 8093 监听进程：Node PID `4145932`，cwd `/home/djdev/leid/yiti/xanzc_frontend`；父进程为该命令对应的 `npm run dev`。
- `GET http://127.0.0.1:8093/` 返回 `200 OK`，Content-Type 为 `text/html`。
- 前端代理目标为本 checkout 后端 `http://127.0.0.1:18091`，`VITE_USE_MOCK=false`。
- 既有其他 checkout 的 Vite/原型进程未作为本次验收服务使用；仅记录本次 8093 归属。

