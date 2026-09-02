# 资产立项页面验收证据

- 验收日期：2026-08-28
- 页面：`/#/marketing/asset-projects`
- 工具：官方 `@playwright/cli`，Chromium
- 数据边界：功能页面使用浏览器显式 route mock；未将 mock 冒充真实后端联调。另开无 mock 会话验证真实未登录边界。

## 1. 功能页面 mock 验收

会话 `asset-project-fix-0828` 只注册以下 6 条路由，不设置 `/api/**` 泛化兜底：

1. `**/api/auth/current-user`
2. `**/api/auth/my-menus`
3. `**/api/auth/permissions`
4. `**/api/notifications/unread-count`
5. `**/api/marketing/asset-projects?*`
6. `**/api/marketing/customers/7`

验收结果：

- 工作台加载两条资产立项样例，项目总投资、项目贷款需求按万元展示为 `8,000`、`10,000`，状态、标签和行级操作正常。
- 新建弹窗根据精确客户详情接口回显 `C000007`、统一社会信用代码、主办客户经理和主办机构；客户来源 ID 不允许手工篡改。
- 页面提示附件单个不超过 50MB；弹窗禁止点击遮罩误关闭。
- `console debug` 为 0 条 error、0 条 warning；`requests` 中上述接口均返回 200。

截图：

- [`asset-project-workbench-fixed.png`](./asset-project-workbench-fixed.png)
- [`asset-project-create-fixed.png`](./asset-project-create-fixed.png)

## 2. 编辑详情失败可恢复性

会话 `asset-project-edit-error-0828` 将 `/api/marketing/asset-projects/9002` 显式返回 500。点击“编辑”后，编辑弹窗仍保持打开，表单区域显示“资产立项草稿加载失败”，没有出现请求失败后弹窗消失、用户无从重试的问题。`requests` 已确认详情请求为预期 500。

截图：[`asset-project-edit-error.png`](./asset-project-edit-error.png)。该 500 是故障注入 mock，不是实际后端异常。

## 3. 无 mock 真实未登录边界

会话 `asset-project-real-unauth-0828` 未注册任何 route。真实请求 `/api/auth/current-user` 返回 401，页面跳转登录页；控制台只出现与该 401 对应的资源加载错误。

截图：[`asset-project-real-unauth.png`](./asset-project-real-unauth.png)。这只能证明真实鉴权边界，不能证明登录后业务接口联调通过。

发布资源、只读数据库核验命令与审批边界见 [`release-gates.md`](./release-gates.md)。
