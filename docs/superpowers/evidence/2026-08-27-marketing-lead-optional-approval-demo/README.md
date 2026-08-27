# 线索经营属性非必填与审批页 DEMO 化验收

日期：2026-08-27

## 验收范围

- 线索录入页经营属性均为选填，新开户企业可暂不填写。
- 新建线索不预设“是否基石客户”；切换已匹配客户时清理旧经营属性快照。
- 线索审批页参考 V2_DEMO，提供审批分类卡片、卡片式列表、完整展示字段和分组详情抽屉。
- 待审批与审批记录卡片互斥，并驱动对应真实接口查询。

## 自动化验证

- 前端专项 Vitest：4 个文件、34 个测试通过。
- 后端 `MarketingLeadApprovalServiceTest`：2 个测试通过。
- customer-marketing-center 主代码及依赖模块编译通过。
- 前端生产构建通过；仅有既存 Sass legacy API 和大分块告警。
- `git diff --check` 通过。

## 真实页面验证

- 工具：官方 `playwright-cli`，Chromium。
- 会话：`lead-approval-demo`。
- 页面：`http://127.0.0.1:8090/#/customers/leads/approval`。
- `route-list`：`No active routes`，未使用网络 mock。
- 待审批、审批记录汇总和列表请求均返回 HTTP 200。
- 点击“审批记录”卡片后，卡片和页签同时切换，并真实请求 `/api/marketing/lead-approvals/history`。
- 页面控制台 0 error；仅有既存 `/bizexec/supports` Vue Router warning。
- 当前数据库无可办理线索，因此本轮未执行通过/退回写操作，详情抽屉由契约测试和生产构建覆盖。

## 截图

- `lead-entry-operating-optional.png`：经营属性不显示必填星号，“是否基石客户”无预设值。
- `lead-approval-demo-layout.png`：审批分类卡片、页签、筛选区和 DEMO 风格列表。
