# 客户触达周期管理页面验收证据

## 验收边界

- 时间：2026-08-25
- 工具：官方 `playwright-cli`，Firefox 153
- 地址：`http://127.0.0.1:8092/#/customers/touch-limits`
- 数据：开发态路由 mock，**不是后端/数据库联调**。当前数据库未建 `CUST_TOUCH_LIMIT_RULE` 表，也未执行资源对齐 SQL。

## 验收结果

- 菜单可进入“客户触达周期管理”，页面标题、查询区、列表、分页和修改入口正常。
- 列表显示标签状态、审核状态、周期和次数上限；默认规则文案为“月、5 次”。
- 修改“存量客户”为“年、8 次”并保存后，发出 `PUT /api/touch-limit-rules/TAG_STOCK`，请求体为 `{"cycleUnit":"YEAR","maxTouches":8}`，返回 200，随后重新查询列表。
- 页面控制台 0 error、1 warning；warning 仅是未专门 mock 的通知数量接口返回 500 后触发既有 GET fallback，与本页功能无关。

## 路由与请求证据

`route-list` 确认了以下 mock：

- `**/api/auth/current-user`
- `**/api/auth/my-menus`
- `**/api/auth/permissions`
- `**/api/touch-limit-rules?*`
- `**/api/touch-limit-rules/*`

关键请求：

- `GET /api/auth/current-user` -> 200
- `GET /api/auth/my-menus` -> 200
- `GET /api/auth/permissions` -> 200
- `GET /api/touch-limit-rules?pageNo=1&pageSize=20` -> 200
- `PUT /api/touch-limit-rules/TAG_STOCK` -> 200
- 保存后 `GET /api/touch-limit-rules?pageNo=1&pageSize=20` -> 200

## 截图

- [列表页](./list-page.png)
- [修改对话框（年、8 次）](./edit-dialog.png)
