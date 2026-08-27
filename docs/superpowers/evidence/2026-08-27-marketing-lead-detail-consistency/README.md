# 线索分配方式与详情一致性验收

日期：2026-08-27

## 验收范围

- 编辑草稿时仅以详情接口返回的 `currentCustomer.mainManagerId` 判断是否存在主办权。
- 有主办权时展示并固定选择“主办专属”，客户经理按“姓名（工号）”展示；无主办权时不展示该选项，旧 `OWNER` 值回退为 `PUBLIC`。
- 线索录入详情和线索审批详情复用同一个只读组件，统一展示基础信息、经营属性、分配信息、补充资料四个分节。
- 审批详情接口与录入详情接口一致返回 `managerEmpIds`、`tagIds`、`attachments`。

## 自动化验证

- 前端定向 Vitest：4 个文件、34 个测试通过。
- 后端 `MarketingLeadApprovalServiceTest`：4 个测试通过。
- `customer-marketing-center` 及依赖模块生产构建：11/11 SUCCESS（`-Dmaven.test.skip=true`；单元测试另行定向执行）。
- 前端生产构建通过；仅有既存 Sass legacy API 和大分块告警。
- `git diff --check` 通过。

## 官方 Playwright CLI 真实页面验收

- 工具：`xanzc_frontend/node_modules/.bin/playwright-cli`，Chromium。
- 会话：`lead-detail-final`。
- 页面：`http://127.0.0.1:8090/#/customers/leads/new`、`http://127.0.0.1:8090/#/customers/leads/approval`。
- `route-list`：`No active routes`，未注册网络 mock。
- 真实草稿编辑结果：四个分节均存在；“是否触达限制”存在；当前客户无主办权时 `ownerVisible=0`，且“全行公开认领”选中。
- 真实草稿详情结果：基础信息、经营属性、分配信息、补充资料四个分节及录入字段均展示；截图中可见字段值与编辑抽屉一致。
- 审批详情接口真实请求返回 HTTP 200，响应包含 `lead`、`currentCustomer`、`profileChanged`、`managerEmpIds`、`tagIds`、`attachments`。
- 审批页待审批、已通过、已退回统计和列表请求均返回 HTTP 200。
- 最终会话控制台 0 error；仅有既存 `/bizexec/supports` 路由 warning。
- 当前数据没有带主办权的可编辑草稿，也没有审批记录，因此“有主办权时姓名（工号）”和审批详情抽屉无法用现存数据实点；这两项由定向测试、生产构建和真实审批详情 API 契约覆盖，未写入或伪造测试数据。

## 截图

- `lead-entry-detail-consistent.png`：真实草稿的四分节线索详情。
- `lead-approval-page.png`：线索审批三张互斥卡片和工作区。
