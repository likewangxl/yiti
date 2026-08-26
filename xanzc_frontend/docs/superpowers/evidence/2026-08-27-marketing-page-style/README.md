# 客户营销页面样式一致性验收

- 验收日期：2026-08-27
- 验收工具：项目本地 `@playwright/cli`，Firefox，1440 × 1000
- 页面来源：当前 checkout 在 8090 端口运行的 Vite 页面
- 数据边界：仅对登录、权限和列表 GET 请求做开发态网络拦截，未发起审批或其他业务写操作；因此这是真实渲染验收，不是后端联调结论。

## 验收结果

`/customers/list`、`/customers/leads/new`、`/customers/leads/approval`、`/customers/tags` 和 `/customers/tags/approval` 的计算样式一致：

- 页面标题：18px
- 页面说明：12px
- 筛选表单标签：14px
- 表格单元格：14px
- 五个页面均存在筛选卡片、斑马线表格和背景式分页。
- 对“标签客户审核”另行使用全新浏览器会话复核，控制台为 0 errors、0 warnings。

## 截图

- `my-customers.png`：我的客户，作为对照基准
- `lead-entry.png`：线索录入
- `lead-approval.png`：线索审批
- `customer-tags.png`：营销客户标签
- `tag-customer-approval.png`：标签客户审核
