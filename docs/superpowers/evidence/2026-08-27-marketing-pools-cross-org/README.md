# 客户池与跨机构营销页面验收证据

- 日期：2026-08-27
- 前端：`http://127.0.0.1:8090`
- 工具：项目本地 `xanzc_frontend/node_modules/.bin/playwright-cli`
- 浏览器：Chromium（Firefox 对当前 Vite 开发服务出现模块传输异常，最终证据以干净重载后的 Chromium 会话为准）
- 边界：浏览器会话内 mock API，仅验证真实构建页面的路由、渲染、筛选、详情和申请条件交互，不代表目标数据库联调通过。
- 数据库：未执行建表、DDL 或 DML。

截图：

- `01-available-pool.png`：待认领线索池字段与详情入口。
- `02-claimed-pool.png`：服务端页签语义、来源和认领关系状态。
- `03-cross-org-list.png`：跨机构申请列表、四项规则说明和审批边界。
- `04-cross-org-validation.png`：按客户号校验后的四项规则结果。

文本证据：

- `routes.txt`：会话内 API mock 路由。
- `requests.txt`：三页面实际请求。
- `console.txt`：浏览器控制台。
- `assertions.txt`：关键页面断言结果。
