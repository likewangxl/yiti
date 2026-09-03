# 触达管理流程补全页面验收

- 验收时间：2026-09-03 10:41-10:44（Asia/Shanghai）
- 前端：`http://localhost:8092`
- 工具：仓库内官方 `playwright-cli`，Chromium，会话 `touch-mgmt-final`
- 验收边界：当前真实登录会话已过期，统一登录入口返回 HTTP 502；本轮使用浏览器网络 mock 验证页面交互和请求报文，不作为真实后端或数据库联调结论。
- 数据库：未执行 DDL/DML。

## 已认领客户池

1. `PENDING` 任务展示“办理触达”。
2. `SUCCESS` 任务同时展示“补录日志”和“再次触达”。
3. 点击“补录日志”后，弹窗标题为“补录触达日志”，已完成任务仍可填写触达时间、方式、协同人员、小结、定位和分类照片；历史日志按时间倒序展示。
4. 模拟列表接口返回 500 时，页面通过 `role="alert"` 展示“已认领客户池暂时不可用”，不再将错误静默伪装为空表。

## 触达任务一览与批量改派

1. `PENDING`、`IN_PROGRESS` 行可选，`SUCCESS` 行选择框禁用。
2. 选择两条在途任务后“批量改派”按钮启用，弹窗显示已选 2 条。
3. 新办理人员工号和改派原因均为必填，原因限制 500 字。
4. 提交后发出请求并自动重新加载列表：

```text
4. GET  /api/admin/touch-tasks?pageNo=1&pageSize=20 -> 200
5. POST /api/admin/touch-tasks/batch-assign -> 200
6. GET  /api/admin/touch-tasks?pageNo=1&pageSize=20 -> 200
```

实际浏览器请求体：

```json
{"taskIds":["201","202"],"newAssigneeEmpId":"E20088","reason":"原办理人岗位调整，统一改派"}
```

## Mock 路由与控制台

成功路径注册了当前用户、菜单、通知数、已认领客户、任务详情、任务日志、营销客户详情、管理员任务列表和批量改派等 9 条 mock 路由。提交成功后的控制台结果：

```text
Total messages: 2 (Errors: 0, Warnings: 0)
Returning 0 messages for level "error"
Returning 0 messages for level "warning"
```

错误态验收另行覆盖同一已认领列表路由为 HTTP 500；该场景产生 1 条预期的浏览器网络错误，同时页面正确展示业务错误提示。

## 截图

- `claimed-pool-actions.png`：办理触达、补录日志和再次触达入口。
- `supplement-log-dialog.png`：已完成任务的补录日志表单及历史日志。
- `batch-reassign-dialog.png`：仅选中两条在途任务后的批量改派弹窗。
- `batch-reassign-success.png`：提交成功、关闭弹窗并刷新后的任务一览。
- `claimed-pool-load-error.png`：列表加载失败的明确错误态。
