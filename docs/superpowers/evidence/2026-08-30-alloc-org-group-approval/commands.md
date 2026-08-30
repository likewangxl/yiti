# Playwright 验收记录

## 启动命令

```bash
VITE_DEV_HOST=127.0.0.1 VITE_DEV_PORT=18190 VITE_DEV_STRICT_PORT=true npm run dev
npx --no-install playwright-cli -s=alloc-org-group-20260830 open http://127.0.0.1:18190/#/system/workflow-flows/FDEF-GROUP-TEST
```

## 开发环境 mock 路由

- `**/api/auth/current-user`
- `**/api/auth/permissions`
- `**/api/auth/my-menus`
- `**/api/admin/workflow/flows/FDEF-GROUP-TEST`
- `**/api/admin/workflow/flows/meta/variables?*`
- `**/api/admin/workflow/flows/meta/approver-variables?*`
- `**/api/notifications/unread-count`
- `**/api/admin/roles/all?*`
- `**/api/orgs/tree`

以上路由均为浏览器会话内的开发 mock，只用于验证页面展示与交互，不属于真实后端联调。

## 最终检查

```bash
npx --no-install playwright-cli -s=alloc-org-group-20260830 reload
npx --no-install playwright-cli -s=alloc-org-group-20260830 click f1e89
npx --no-install playwright-cli -s=alloc-org-group-20260830 console
npx --no-install playwright-cli -s=alloc-org-group-20260830 requests
npx --no-install playwright-cli -s=alloc-org-group-20260830 screenshot \
  --filename=/home/djdev/lijh/yiti/docs/superpowers/evidence/2026-08-30-alloc-org-group-approval/workflow-group-all.png \
  --full-page
```

检查结果：

- 画布节点显示“按机构会签”和“审批机构组 1”，节点正文位于节点边界内。
- 属性面板选中“按机构会签”，显示“机构间按顺序会签；同一机构内任一负责人审批即可”。
- 审批变量显示“原业绩所属机构负责人分组（originalOwnerOrgApprovalGroups）”。
- 控制台错误 0、警告 0。
- 流程详情、变量目录、审批变量、角色和机构树等 mock 请求均返回 HTTP 200。
- 截图见 `workflow-group-all.png`。
