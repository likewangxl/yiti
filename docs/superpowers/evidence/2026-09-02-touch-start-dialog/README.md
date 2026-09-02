# 已认领线索池发起触达弹窗验收

- 日期：2026-09-02
- 地址：`http://localhost:8092/#/customers/pool/claimed`
- 工具：官方 `playwright-cli`，独立会话 `touch-start-jxe`，Chromium，无 mock route
- 数据来源：真实 `GET /api/claims/mine/customers?tab=UNTOUCHED&pageNo=1&pageSize=20`
- 验收动作：登录后进入已认领线索池，点击客户“测试客户1”的“发起触达”，只打开弹窗；未点击“确认发起”，未产生触达任务写入。

## 结论

- 弹窗展示客户名称、客户统一社会信用代码、客户联系人、联系方式。
- 弹窗不再出现计划完成时间日期选择器。
- 提示明确说明计划完成时间由“发起时间 + 后台触达时限配置”计算。
- 当前样例客户的联系人和联系方式源数据为空，页面以 `-` 如实展示。

## 关键命令

```bash
npx --yes @playwright/cli -s=touch-start-jxe open --browser=chromium 'http://localhost:8092/#/customers/pool/claimed'
npx --yes @playwright/cli -s=touch-start-jxe goto 'http://localhost:8092/#/customers/pool/claimed'
npx --yes @playwright/cli -s=touch-start-jxe click e602
npx --yes @playwright/cli -s=touch-start-jxe route-list
npx --yes @playwright/cli -s=touch-start-jxe requests
npx --yes @playwright/cli -s=touch-start-jxe console
npx --yes @playwright/cli -s=touch-start-jxe screenshot e623 --filename=/home/djdev/jxe/yiti/docs/superpowers/evidence/2026-09-02-touch-start-dialog/start-dialog.png
```

## 文件

- `start-dialog.png`：真实弹窗截图。
- `snapshot.yml`：弹窗可访问性树摘要。
- `routes.txt`：浏览器 route 注册清单。
- `requests.txt`：真实业务请求摘要。
- `console.txt`：浏览器控制台原始摘要及说明。
