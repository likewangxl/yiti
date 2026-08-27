# 客户营销筛选栏与线索审批卡片验收证据

## 验收边界

- 页面：营销客户标签、线索审批、线索录入
- 地址：`http://127.0.0.1:8090/#/customers/tags`、`#/customers/leads/approval`、`#/customers/leads/new`
- 浏览器：官方 `playwright-cli`，Chromium，视口 `1440 x 900`
- 会话：`marketing-filter-final-20260827`
- 数据：仅开发态 mock，用于验证布局、卡片/状态筛选联动和请求参数，非后端联调

## 开发态 mock 路由

完整清单见 [routes.txt](./routes.txt)。页面通过 `playwright-cli run-code` 注册 `page.route` 拦截器，因此 CLI 的 `route-list` 不展示这些程序化路由；本次逐条归档了实际注册模式及响应口径。

## 验收结果

### 营销客户标签

- 标签名称、标签类型、状态、查询、重置的控件中心纵坐标均为 `334`。
- `.filter-form` 的计算样式为 `flex-wrap: nowrap`。
- `scrollWidth=1130`、`clientWidth=1130`，桌面视口下没有横向溢出，也没有按钮换行。

### 线索审批

- 显示四张互斥卡片：总览、待审批、已通过、已退回；初始仅总览 `aria-pressed=true`。
- 关键词、状态、查询、重置的控件中心纵坐标均为 `370`。
- `.approval-toolbar` 的计算样式为 `flex-wrap: nowrap`，`scrollWidth=1134`、`clientWidth=1134`。
- 点击“已通过”卡片后，状态下拉反显“已通过”，列表请求切换为 `result=APPROVED`。
- 状态下拉选择“已退回”后，仅“已退回”卡片选中，列表请求切换为 `result=REJECTED`。
- 点击重置后，关键词与状态清空，卡片恢复总览，请求恢复 `/overview`。

### 线索录入

- 关键词、状态、查询、重置的控件中心纵坐标均为 `372`。
- `.filter-form` 的计算样式为 `flex-wrap: nowrap`。
- `scrollWidth=1130`、`clientWidth=1130`，桌面视口下没有横向溢出，也没有按钮换行。

## 原始结果

- 控制台：[console.txt](./console.txt)，`Errors: 0, Warnings: 0`
- 请求/响应摘要：[requests.txt](./requests.txt)
- 路由清单：[routes.txt](./routes.txt)

## 截图

- [营销客户标签筛选栏](./customer-tags-filter-row.png)
- [线索审批四卡片及筛选栏](./lead-approval-four-cards.png)
- [线索录入筛选栏](./lead-entry-filter-row.png)
