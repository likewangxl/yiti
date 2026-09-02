# 资产立项待办审批浏览器验收

- 验收时间：2026-09-02
- 页面地址：`http://127.0.0.1:8092/#/marketing/asset-projects?tab=PENDING`
- 工具：项目内 `playwright-cli` 0.1.18，Headless Chromium 152
- 结论：客户营销菜单下可见“资产立项”；待办行可见“通过/驳回”；通过意见默认为“同意”；驳回原因为空时提示“驳回原因不能为空”；提交后均刷新待办列表；浏览器控制台 0 error。

## 验收边界

本次使用开发态显式路由 mock 隔离登录、权限、菜单、资产立项列表和工作流审批接口，用于验证前端页面行为与请求契约，不等同于真实数据库、真实 UIAS 或真实 Flowable 联调。数据库候选 SQL 未执行。

## 请求结果

- `GET /api/auth/current-user` -> 200
- `GET /api/auth/my-menus` -> 200
- `GET /api/auth/permissions` -> 200
- `GET /api/marketing/asset-projects?tab=PENDING&pageNo=1&pageSize=20` -> 200
- `POST /api/workflow/tasks/TASK-9001/approve` -> 200，请求体 `{"opinion":"同意"}`
- `POST /api/workflow/tasks/TASK-9001/reject` -> 200，请求体 `{"opinion":"材料不完整，请补充尽调附件"}`
- 审批动作后均再次请求待办列表。

## 证据

- `pending-approval.png`：客户营销菜单、资产立项待办列表和审批操作入口。
- `requests.raw.txt`：业务请求状态及请求体。
- `routes.raw.txt`：本次显式 mock 清单。
- `console.raw.txt`：浏览器控制台错误计数。
