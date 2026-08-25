# 分配维度 ACCOUNT 名称调整验收证据

验收时间：2026-08-25（Asia/Shanghai）

## 验收边界

- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 前端：`/home/djdev/lijh/yiti/xanzc_frontend` 当前 Vite 运行态
- 工具：项目本地官方 `playwright-cli`，Firefox，命名会话 `alloc-account-label`
- 本次使用显式开发态 mock，只验证前端名称归一行为，不代表后端或数据库联调。

## Mock 路由

验收通过 `playwright-cli route` 注册以下路由：

- `GET /api/auth/permissions`：验收用绩效接口资源
- `GET /api/auth/my-menus`：空菜单
- `GET /api/sys/dicts/PERF_ALLOC_DIM/items`：`ACCOUNT` 故意返回旧标签“按台账分配”
- `GET /api/sys/dicts/PERF_BIZ_KIND/items`：存款、贷款
- `GET /api/perf/alloc-adjust/my-applies`、`my-todos`、`my-dones`：空分页

## 验收结果

1. 打开“新建调整申请”的分配维度下拉，选项为“按规则分配 / 按账号分配 / 新开户”。
2. 页面未出现字典 mock 返回的旧名称“按台账分配”。
3. `route-list` 显示上述 7 条显式 mock 路由；业务请求摘要显示字典和我的申请接口均返回 HTTP 200。
4. `console warning` 返回 0 error、0 warning。

## 截图

- `account-label.png`：新建申请分配维度下拉显示“按账号分配”。
