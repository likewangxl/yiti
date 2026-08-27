# 营销客户标签筛选验收证据

## 验收边界

- 页面：`http://127.0.0.1:8090/#/customers/tags`
- 浏览器：官方 `playwright-cli`，Chromium
- 会话：`marketing-tag-filter-20260827`
- 数据：仅开发态 mock，用于校验页面与请求参数，非后端联调结果
- 真实后端：`127.0.0.1:18080` 已使用当前 checkout 新构件重启；未认证模板请求返回 401，未再出现 `batchId=import-template` 参数转换错误

## 路由

```text
**/api/auth/current-user
**/api/auth/permissions
**/api/auth/my-menus
**/api/notifications/unread-count
**/api/sys/dicts/CUSTOMER_TAG_TYPE/items
**/api/marketing/customer-tags*
```

`CUSTOMER_TAG_TYPE` mock 返回 `PROJECT/项目类`、`CERTIFICATION/认定类`；标签列表 mock 覆盖有效、待审核、禁用、已退回四种组合状态。

## 交互结果

- 首屏展示“标签总览、有效标签、待审核标签、异常标签”四张互斥卡片，默认仅“标签总览”的 `aria-pressed=true`。
- 点击“异常标签”后，仅该卡片 `aria-pressed=true`，请求包含 `viewStatus=EXCEPTION`：

```text
GET /api/marketing/customer-tags?keyword=&tagType=&viewStatus=EXCEPTION&pageNo=1&pageSize=20 => 200
```

- 输入标签名称、选择“项目类”并点击重置后，名称与类型均清空，卡片恢复“标签总览”；请求恢复 `viewStatus=`，并重新读取字典及四组统计。
- 页面不再展示“标签分类”；表格标签类型显示中文，状态列显示“有效、待审核、禁用、已退回”。
- 新增弹窗不再包含标签分类，标签类型为必填字典下拉，实时选项为“项目类、认定类”。

关键请求：

```text
GET /api/sys/dicts/CUSTOMER_TAG_TYPE/items => 200
GET /api/marketing/customer-tags?viewStatus=&pageNo=1&pageSize=1 => 200
GET /api/marketing/customer-tags?viewStatus=ACTIVE&pageNo=1&pageSize=1 => 200
GET /api/marketing/customer-tags?viewStatus=PENDING&pageNo=1&pageSize=1 => 200
GET /api/marketing/customer-tags?viewStatus=EXCEPTION&pageNo=1&pageSize=1 => 200
```

控制台：`Errors: 0, Warnings: 0`。

## 截图

- [标签总览](./marketing-tag-overview.png)
- [新增弹窗标签类型](./create-dialog-types.png)
