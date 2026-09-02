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

## 重启后真实提交联调

- 2026-09-02 17:44:11 点击“确认发起”，真实 `POST /api/claims/1/touch` 返回 200。
- 生成任务 `TOUCH_1788342250658_0371`，任务 ID 为 `1`，状态为“待办理”、SLA 为“蓝灯”。
- 创建时间为 `2026-09-02 17:44:11`，计划完成时间为 `2026-09-09 17:44:11`，验证未配置后台参数时按默认 7 天计算。
- 联调发现详情弹窗原先仍请求旧 `/api/customers/1`；已切换为正式 `/api/marketing/customers/1`，复验返回 200。
- 最终请求链路中任务详情、日志和正式营销客户详情均返回 200；浏览器控制台无业务错误。
- `real-confirm-success.png`：真实任务创建后，从“我的触达任务”打开办理详情的截图。

## 进行中任务重复发起修复复验

- 2026-09-02 18:06 使用官方 `playwright-cli` 会话 `touch-running-fix` 复验，未注册任何 mock route。
- 已认领池返回原任务 ID `1`、状态 `PENDING`；页面显示“待触达”“触达任务进行中”和“办理触达”，不再显示“发起触达”。
- 点击“办理触达”直接打开原任务详情和补录入口；请求只有任务、日志和正式营销客户详情 GET，没有再次调用 `POST /api/claims/1/touch`。
- 相关真实请求均为 200，复验阶段浏览器控制台无错误、无警告。
- 复验前后客户 ID `1` 的任务总数均为 1，未创建重复任务，也未修改历史任务数据。

## 关键命令

```bash
npx --yes @playwright/cli -s=touch-start-jxe open --browser=chromium 'http://localhost:8092/#/customers/pool/claimed'
npx --yes @playwright/cli -s=touch-start-jxe goto 'http://localhost:8092/#/customers/pool/claimed'
npx --yes @playwright/cli -s=touch-start-jxe click e602
npx --yes @playwright/cli -s=touch-start-jxe route-list
npx --yes @playwright/cli -s=touch-start-jxe requests
npx --yes @playwright/cli -s=touch-start-jxe console
npx --yes @playwright/cli -s=touch-start-jxe screenshot e623 --filename=/home/djdev/jxe/yiti/docs/superpowers/evidence/2026-09-02-touch-start-dialog/start-dialog.png
./node_modules/.bin/playwright-cli -s=touch-running-fix route-list
./node_modules/.bin/playwright-cli -s=touch-running-fix click f6e401
./node_modules/.bin/playwright-cli -s=touch-running-fix requests
./node_modules/.bin/playwright-cli -s=touch-running-fix console warning
./node_modules/.bin/playwright-cli -s=touch-running-fix screenshot --filename=/home/djdev/jxe/yiti/docs/superpowers/evidence/2026-09-02-touch-start-dialog/running-task-detail.png --full-page
```

## 文件

- `start-dialog.png`：真实弹窗截图。
- `snapshot.yml`：弹窗可访问性树摘要。
- `routes.txt`：浏览器 route 注册清单。
- `requests.txt`：真实业务请求摘要。
- `console.txt`：浏览器控制台原始摘要及说明。
- `real-confirm-success.png`：真实确认发起成功后的任务详情。
- `running-task-claimed-pool.png`：进行中任务在已认领池显示“办理触达”的截图。
- `running-task-detail.png`：点击“办理触达”后打开原任务办理详情的截图。
- `routes-running-task.txt`、`requests-running-task.txt`、`console-running-task.txt`：无 mock、真实请求和控制台复验证据。
