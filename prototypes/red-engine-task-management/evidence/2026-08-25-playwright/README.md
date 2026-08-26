# 红色引擎任务原型 Playwright CLI 验收证据

## 验收边界

- 日期：2026-08-25（Asia/Shanghai）。
- 工具：项目内官方 `playwright-cli 0.1.18`，Chromium，会话 `redengine-task-prototype-20260825`。
- 页面：独立原型 `http://127.0.0.1:4173/`，视口 `1440 x 900`，`devicePixelRatio=1`。
- 数据：浏览器内存演示数据；没有后端、数据库、RBAC、OBS、Quartz 或真实员工数据。
- 路由拦截：`No active routes`，未注册任何 mock route。
- 请求：只有 Vite/React/字体/图标等静态资源请求；没有 `/api` 请求，因此没有 API request/response body 可归档。本轮是纯前端原型验收，不是联调。

## 关键命令

以下命令均从仓库根目录执行：

```bash
xanzc_frontend/node_modules/.bin/playwright-cli --version
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-task-prototype-20260825 open http://127.0.0.1:4173 --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-task-prototype-20260825 resize 1440 900
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-task-prototype-20260825 route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-task-prototype-20260825 console
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-task-prototype-20260825 requests
xanzc_frontend/node_modules/.bin/playwright-cli -s=redengine-task-prototype-20260825 screenshot --filename /home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-playwright/01-task-management.png
```

交互使用同一官方 CLI 的 `run-code`，依次执行：

```text
任务管理：填写“本月” -> 查询 -> 重置 -> 下一页 -> 返回第 1 页
新增任务：点击发布验证必填 -> 切临时任务 -> 选择整改反馈 -> 填标题和含 https 链接的说明 -> 要求上传 -> 勾选 PDF -> 发布
员工处理：切换一线员工 -> 工作台打开新待办 -> 空附件提交 -> 选择本地临时 PDF -> 提交 -> 确认提交
完成校验：任务处理按标题查询 -> 状态为已完成 -> 返回工作台 -> 新任务待办数量为 0
四大维度：点击本月四大维度材料上报 -> 验证进入现有材料上报入口说明分支
```

本地上传验收使用 `/tmp/redengine-prototype-attachment.pdf`，仅用于浏览器选择文件，不纳入仓库。

## 原始结果摘要

### 路由清单

```text
No active routes
```

### 请求摘要

```text
Note: 93 static requests not shown, run with --static option to see them.
```

没有 `/api` 请求，故无 API request/response body。

### Console

首轮发现并修复 `favicon.ico` 404；最终原始结果：

```text
Total messages: 3 (Errors: 0, Warnings: 0)
Returning 1 messages for level "info"

[INFO] Download the React DevTools for a better development experience
```

### 布局与密度

```json
{"viewport":{"width":1440,"height":900},"devicePixelRatio":1,"documentWidth":{"client":1440,"scroll":1440},"sidebar":{"width":275},"topbar":{"height":60}}
```

页面没有横向溢出，侧栏和顶栏尺寸与现有红色引擎视觉基线一致。

## 交互断言

- 查询“本月”只返回“本月四大维度材料上报”；第 2 页显示其余两条任务。
- 空表单发布显示“请输入任务标题、请输入任务说明”；填写后错误即时清除。
- 发布“网点整改情况反馈”后，一线员工工作台出现对应待办。
- 任务说明链接属性为 `target=_blank`、`rel=noreferrer noopener`。
- 必传附件为空时提示“请先选择文件后再提交”；选择本地 PDF 后可确认提交。
- 提交后任务处理列表显示“已完成”，工作台中该任务数量为 0，剩余待办为 2 条。
- 四大维度任务进入只读分支说明，显示“进入现有材料上报入口”按钮，不进入通用任务详情。

## 截图

- `01-task-management.png`：任务管理首屏。
- `02-new-temporary-task.png`：临时任务新增表单和附件类型联动。
- `03-employee-workbench.png`：发布后的一线员工工作台待办。
- `04-task-detail-before-submit.png`：链接、附件和提交确认。
- `05-workbench-after-submit.png`：提交后待办移除。
- `06-four-dimensions-branch.png`：四大维度材料上报分支。
- `07-source-implementation-comparison.png`：左侧为现有红色引擎参考截图，右侧为任务管理实现，同为 `1440 x 900`。

## 视觉修复记录

1. 首轮 console 出现 favicon 404：复用平台现有 favicon 后错误归零。
2. 首轮新增表单中已填写字段仍保留红色错误，且 radio/checkbox 受全宽输入样式影响：改为输入后清除对应错误，并固定选择控件为 `16 x 16`；复测错误由 2 条降为 0。
3. 首轮侧栏 logo 和菜单左对齐，与参考图居中布局不一致：恢复居中布局并更新最终比较图。

最终结论：纯前端原型关键流程和视觉基线通过；不代表后端、权限或数据库联调。
