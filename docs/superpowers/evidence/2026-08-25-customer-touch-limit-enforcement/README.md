# 客户触达限制页面验收证据

## 验收边界

- 时间：2026-08-25
- 工具：项目内官方 `@playwright/cli@0.1.18`，Chromium，无头模式
- 地址：隔离前端 `http://127.0.0.1:8092`
- 数据：浏览器路由开发态 mock，**不是后端或数据库联调**；未启动后端、未执行 DDL/DML

## 验收结果

- 线索录入页可打开新建对话框，“是否触达限制”为必填项，默认选中“是”。
- 批量导入对话框明确提示模板必须包含“是否触达限制”，每行只能填写“是”或“否”；预览成功前不出现“确认执行”。
- 客户触达周期管理页成功发出 `GET /api/touch-limit-rules?pageNo=1&pageSize=20` 并展示“存量客户 / 月 / 5 次”。
- 修改对话框显示“保存后已用于发起触达校验”，标签值为“存量客户”，次数为 5。
- 验收结束时浏览器控制台 error 为 0；所有业务请求均由明确列出的 mock 路由响应 200。

## 截图

- [线索新建默认开启触达限制](./lead-create-touch-restricted.png)
- [线索导入必填列提示](./lead-import-required-column.png)
- [触达周期规则查询](./touch-limit-query.png)
- [规则修改与校验生效提示](./touch-limit-edit-enforcement-hint.png)

## Mock 路由

- `/api/auth/current-user`
- `/api/auth/my-menus`
- `/api/auth/permissions`
- `/api/notifications/unread-count`
- `/api/sys/dicts/**`
- `/api/leads?*`
- `/api/tags/enabled`
- `/api/touch-limit-rules?*`
