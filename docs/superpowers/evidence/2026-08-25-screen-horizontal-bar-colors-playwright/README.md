# 横向柱状图十色循环验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen/SCR_HORIZONTAL?preview=draft`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-horizontal-colors`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。注册 5 条 mock route，未执行保存、发布或其他状态变更。

## 验收结论

1. 草稿渲染包中图表类型为 `BAR_COMPARE`，`propValue.barMode` 为 `horizontal`。
2. 11 行单指标数据渲染为 11 根横向柱，每根柱按莫兰迪色板稳定轮换。
3. 前 10 根使用 10 种颜色，第 11 根循环使用第 1 种颜色；不使用会在重新渲染时跳色的真随机。
4. 清空初始导航记录后，Console 为 0 error、0 warning。
5. 网络请求仅包含认证/权限 GET、草稿渲染包 GET 和取数 POST；没有保存或发布请求。

## 证据

- `01-horizontal-11-bars.png`：11 根横向柱的实际渲染截图。
- `routes.raw.txt`：5 条开发态 mock 路由。
- `network.raw.txt`：草稿包和取数的请求/响应摘要。
- `console.raw.txt`：CLI Console 结果。
- `commands.md`：实际 CLI 操作顺序。
