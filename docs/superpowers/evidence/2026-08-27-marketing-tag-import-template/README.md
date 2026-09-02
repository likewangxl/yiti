# 营销客户标签导入模板验收证据

验收时间：2026-08-27（Asia/Shanghai）

## 验收边界

- 使用官方 `playwright-cli`，浏览器为 Chromium，会话为 `marketing-tag-template-chromium`。
- 页面运行地址为 `http://127.0.0.1:8090/#/customers/tags`。
- 本次浏览器验收使用开发态 mock，只验证页面入口、按钮状态、请求路径、下载动作和用户反馈，不作为真实后端联调证据。
- 后端模板文件结构由 `MarketingCustomerTagImportServiceTest` 使用 Apache POI 实际读取并断言。

## Mock 路由

1. `http://127.0.0.1:8090/api/**`：空成功响应兜底。
2. `http://127.0.0.1:8090/api/auth/current-user`：系统管理员登录态。
3. `http://127.0.0.1:8090/api/auth/my-menus`：空菜单。
4. `http://127.0.0.1:8090/api/auth/permissions`：营销标签及导入批次权限。
5. `http://127.0.0.1:8090/api/marketing/customer-tags*`：一条已审批、已启用标签。
6. `http://127.0.0.1:8090/api/marketing/customer-tag-import-batches/import-template`：模拟 xlsx 二进制响应。

## 关键命令与结果

```bash
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium open about:blank --browser chromium
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium goto 'http://127.0.0.1:8090/#/customers/tags'
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium click e134
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium find '下载导入模板'
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium click e174
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium requests
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium console
./node_modules/.bin/playwright-cli -s=marketing-tag-template-chromium route-list
```

结果：

- 导入弹窗显示“下载导入模板”按钮。
- 点击后请求 `GET /api/marketing/customer-tag-import-batches/import-template`，HTTP 200。
- 浏览器下载事件文件名为 `营销客户标签导入模板.xlsx`。
- 页面提示“模板下载已开始”。
- Console：0 errors，0 warnings。

## 截图

- `import-dialog.png`：追加导入弹窗及模板下载按钮。
- `download-success.png`：下载成功提示。
