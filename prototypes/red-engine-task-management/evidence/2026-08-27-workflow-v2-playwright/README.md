# 2026-08-27 红色引擎任务协同原型 Playwright CLI 验收

## 环境与边界

- 原型目录：`/home/djdev/leid/yiti/prototypes/red-engine-task-management`
- 预览命令：`npm run dev -- --host 127.0.0.1 --port 4176 --strictPort`
- 浏览器：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`redengine-workflow-v2-20260827`
- 视口：`1440 x 900`，DPR 1；`clientWidth=scrollWidth=1440`
- 没有注册 mock route；数据来自原型内存种子，非 API/数据库/RBAC 联调。

## 实际命令

```bash
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 open http://127.0.0.1:4176 --browser chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 resize 1440 900
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 snapshot
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 tab-select 1
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 tab-close 1
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 screenshot --filename=prototypes/red-engine-task-management/evidence/2026-08-27-workflow-v2-playwright/01-admin-task-management.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 run-code "(async (page) => { await page.getByLabel('演示身份').selectOption('reporter'); await page.getByRole('button', { name: '上报信息' }).click(); return await page.getByRole('tab').allTextContents(); })"
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 console warning
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 console error
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 requests --static
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-workflow-v2-20260827 eval "() => ({ viewport: [window.innerWidth, window.innerHeight], clientWidth: document.documentElement.clientWidth, scrollWidth: document.documentElement.scrollWidth, dpr: window.devicePixelRatio })"
```

`run-code` 只调用 Playwright 页面 API，完成身份选择、点击、填报、上传、审核、筛选、扣分和断言；没有注入或修改应用状态。

## 关键结果

- 报送员菜单为首页、四大维度材料上报、上报信息、红黄牌预警池，无任务处理。上报信息有待处理、审核中、已通过、已驳回四页签。
- 临时任务标题真实新开同源窗口；任务说明 URL 的 `target=_blank`、`rel=noreferrer noopener`；填报内容与 PDF 附件提交成功，父窗口待处理数由 2 变 1、审核中由 2 变 3。
- 审核中临时任务表头只有维度、考核项、提交人、提交日期，不展示审核状态、审核意见和端员得分。
- 四维任务进入四大维度材料上报页面；联建规范度从 1 次上传增加到 2 次，首次上传即完成该明细。
- 支部书记菜单只有首页、支部审核工作台、红黄牌预警池；临时任务依次显示待支部处理、支部已通过、组织审核中。
- 组织审核员不显示四维材料、上报信息和任务处理；工作台左侧有全部、四维材料、临时任务筛选，能查看填报内容和附件，并完成提交通过、直接驳回。
- 任务管理始终保留全部 7 个已发布任务，不随 submitted/approved/rejected 流程状态消失。临时任务导出预览包含 Excel、党支部一和党支部二附件目录。
- 四维任务未选择明细时提示“请先选择明细项”；选择联建规范度、业务转换实质后，预览只出现两个选中明细及其文件。
- 红黄牌板块对四种角色可见；报送员、支部书记、组织审核员均不显示扣分区。组织管理员显示逾期上报待执行扣分，录入 2 分后显示“已扣 2 分”。逾期未上报明确排除并保留通知说明。

## 路由、请求与控制台

- `routes.txt`：`No active routes`。
- `console.txt`：Errors 0、Warnings 0。
- `requests.txt`：35 条均为 4176 本地静态资源请求，全部 200，无 `/api`。
- 由于没有 mock route、没有 API 请求，本次是可交互原型验收，不是后端联调。

## 截图

- `01-admin-task-management.png`：组织管理员任务管理。
- `02-reporter-report-info.png`：报送员上报信息待处理。
- `03-reporter-popup-filled.png`：独立窗口填报与附件。
- `04-reporter-reviewing-history.png`：提交后审核中临时任务历史列。
- `05-branch-secretary-reviewing.png`：支部书记提交组织审核后状态。
- `06-org-reviewer-temporary.png`：组织审核员临时任务详情。
- `07-task-export-preview.png`：多支部附件 ZIP 结构预览。
- `08-material-export-detail-selection.png`：四维明细选择导出。
- `09-admin-warning-deduction.png`：管理员预警池与扣分。
- `10-source-implementation-comparison.png`：旧版任务管理与本版同视口并排视觉对照。

结论：本轮原型关键业务流、角色边界、导出分支、预警池差异与视觉基线验收通过。
