# 分配维度 NEW 名称调整验收证据

验收时间：2026-08-25（Asia/Shanghai）

## 验收边界

- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 前端：`/home/djdev/lijh/yiti/xanzc_frontend` 当前 Vite 运行态
- 工具：项目本地官方 `playwright-cli`，命名会话 `alloc-label`
- 本次使用显式开发态 mock，只验证前端名称归一行为，不代表后端/数据库联调。

## Mock 路由

`page.route("http://127.0.0.1:8090/api/**", handler)` 限定拦截当前前端开发服务下的 `/api/` 请求。关键响应：

- `GET /api/auth/current-user`：验收用系统管理员
- `GET /api/auth/permissions`：绩效接口资源
- `GET /api/sys/dicts/PERF_ALLOC_DIM/items`：`NEW` 故意返回旧标签“新客户”
- `GET /api/sys/dicts/PERF_BIZ_KIND/items`：对公存款/对公贷款
- `GET /api/perf/alloc-adjust/my-applies`：空分页
- 其他 `/api/` 请求：空成功响应

## 验收结果

1. 打开“新建调整申请”的分配维度下拉，选项为“按规则分配 / 按账户分配 / 新开户”。
2. 选中 `NEW` 后，分配维度显示“新开户”。
3. 页面不再出现“新客户”。
4. 控制台为 0 error / 0 warning。

## 截图

- `new-account-label.png`：新建申请中 `NEW` 已显示为“新开户”。
