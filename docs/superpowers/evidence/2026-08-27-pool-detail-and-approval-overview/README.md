# 待认领池详情与审批总览验收

## 验收环境

- 日期：2026-08-27
- 前端：`http://127.0.0.1:8090`，Vite 真实服务
- 后端：`http://127.0.0.1:18080`，`/home/djdev/lijh/yiti` 重启后的 dev 实例
- 浏览器：官方 `playwright-cli`，Chromium，1440x900
- 用户：`lzy`（含客户经理和线索审批角色）
- Mock 路由：无，请求均经 Vite `/api` 代理到真实后端

## 结果

1. 待认领池列表不再展示“线索编号”。
2. 点击“详情”后请求 `GET /api/customer-pool?leadId=1`，HTTP 200，返回客户“测试客户1”的 `APPROVED + PUBLIC + AVAILABLE` 详情。
3. 详情抽屉复用营销线索共享只读展示，页面不存在“线索编号”标签，后端元单位金额 `50000000` 正确展示为 `5,000` 万元。
4. 线索审批页保持“总览”卡片选中，真实返回 1 条 `IN_APPROVAL` 记录，该行同时展示“通过”和“退回”按钮。
5. 两个页面验收阶段 console 均为 0 error / 0 warning。

## 证据文件

- `available-pool-detail.png`：待认领池详情抽屉
- `approval-overview.png`：总览中待审批行的通过/退回按钮
- `commands.txt`：官方 CLI 验收命令（Cookie 已脱敏）
- `routes.txt`、`console.txt`、`requests.txt`：路由、控制台和真实请求/响应摘要

## 自动化验证

- `AvailablePool.spec.js` + `MarketingLeadApproval.spec.js`：14/14 通过。
- `npm run build`：通过；仅有项目已有 Sass 弃用和 chunk 大小警告。
- Maven `clean install -Dmaven.test.skip=true`：通过。
- 后端定向服务、XML 契约及 MockMvc 路由验证：通过。
- `scripts/check-contract-docs.sh`：本模块通过；全库仍被既有 `red-engine-center` 文档时间戳基线阻断。
