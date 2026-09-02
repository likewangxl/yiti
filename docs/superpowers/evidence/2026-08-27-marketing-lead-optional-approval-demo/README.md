# 线索经营属性非必填与审批页 DEMO 化验收

日期：2026-08-27

## 验收范围

- 线索录入页经营属性均为选填，新开户企业可暂不填写。
- 新建线索不预设“是否基石客户”；切换已匹配客户时清理旧经营属性快照。
- 线索审批页参考 V2_DEMO，提供审批分类卡片、卡片式列表、完整展示字段和分组详情抽屉。
- 分类卡片固定为“待审批、已通过、已退回”，三者互斥并直接驱动列表筛选。
- 已通过、已退回分别向历史接口传递 `result=APPROVED/REJECTED`；后端遍历当前登录人的完整工作流任务集合后再按页面业务来源、审批结果和关键字分页，避免只过滤当前工作流页造成数量失真。

## 自动化验证

- 本次增量前端专项 Vitest：2 个文件、13 个测试通过。
- 前端全量 Vitest：1033 个测试通过、5 个既存工作树用例失败；失败集中在名单管理、已认领客户池、客户营销路由和行操作审计，与本次线索审批卡片改动无重叠。
- 后端 `MarketingLeadApprovalServiceTest`：3 个测试通过（因工作树中无关未跟踪 `NameListServiceTest` 缺少对应生产类，标准 `testCompile` 基线失败；本专项测试通过定向编译后由 Surefire 执行）。
- customer-marketing-center 主代码及依赖模块编译通过；随后使用本 checkout 隔离 Maven 仓库完成 19 个生产模块全量安装。
- 前端生产构建通过；仅有既存 Sass legacy API 和大分块告警。
- `git diff --check` 通过。

## 真实页面验证

- 工具：官方 `playwright-cli`，Chromium。
- 会话：`lead-approval-status-final`。
- 页面：`http://127.0.0.1:8090/#/customers/leads/approval`。
- `route-list`：`No active routes`，未使用网络 mock。
- 待审批、已通过、已退回三类汇总和列表请求均返回 HTTP 200。
- 依次点击“已通过”“已退回”后，选中态与工作区标题互斥切换，并真实请求 `/api/marketing/lead-approvals/history?result=APPROVED`、`result=REJECTED`。
- 后端重启后 OpenAPI 已确认历史接口 `result` 为必填，并包含 `reviewedBy`、`reviewedTime`、`rejectReason` 响应字段。
- 页面控制台 0 error；仅有既存 `/bizexec/supports` Vue Router warning。
- 当前数据库无可办理线索，因此本轮未执行通过/退回写操作，详情抽屉由契约测试和生产构建覆盖。

## 截图

- `lead-entry-operating-optional.png`：经营属性不显示必填星号，“是否基石客户”无预设值。
- `lead-approval-demo-layout.png`：待审批、已通过、已退回三张互斥卡片、筛选区和 DEMO 风格列表。
