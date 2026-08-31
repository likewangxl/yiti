# 首页排名与角色菜单 Playwright CLI 验收

## 环境

- 原型地址：`http://127.0.0.1:4179/`
- 浏览器：官方 `playwright-cli 0.1.18`，`--browser chromium`
- 会话：`redengine-home-ranking-20260831-v2`
- 视口：`1440 x 900`
- DPR：`1`
- 路由拦截：无
- API：无；本轮为纯前端 mock 原型

## 真实命令

```bash
npm run dev -- --host 127.0.0.1 --port 4179 --strictPort
/home/djdev/.npm/_npx/31e32ef8478fbf80/node_modules/.bin/playwright-cli -s=redengine-home-ranking-20260831-v2 open http://127.0.0.1:4179 --browser chromium
/home/djdev/.npm/_npx/31e32ef8478fbf80/node_modules/.bin/playwright-cli -s=redengine-home-ranking-20260831-v2 resize 1440 900
/home/djdev/.npm/_npx/31e32ef8478fbf80/node_modules/.bin/playwright-cli -s=redengine-home-ranking-20260831-v2 route-list
/home/djdev/.npm/_npx/31e32ef8478fbf80/node_modules/.bin/playwright-cli -s=redengine-home-ranking-20260831-v2 console warning
/home/djdev/.npm/_npx/31e32ef8478fbf80/node_modules/.bin/playwright-cli -s=redengine-home-ranking-20260831-v2 console error
/home/djdev/.npm/_npx/31e32ef8478fbf80/node_modules/.bin/playwright-cli -s=redengine-home-ranking-20260831-v2 requests --static
```

角色断言和截图均通过 `run-code`、`screenshot --filename ...` 执行，完整断言结论如下：

- `admin`：菜单无年度考核归档、数据导出，保留任务管理；排名表 5 行且字段为支部名称、得分、排名；`scrollWidth=innerWidth=1440`。
- `orgReviewer`：菜单无年度考核归档、数据导出，保留工作台、任务管理；排名表 5 行。
- `reporter`：指标为待办事项、所在机构得分 `92.5 分`、所在机构排名 `3/12`；无全员达标率、无支部排名表。
- `branchSecretary`：指标为待办事项、所在机构得分 `89.8 分`、所在机构排名 `5/12`；菜单未越权。
- 临时任务待办新窗口：打开 `?popup=1&task=task-101`，任务标题和提交入口均可见。

## 截图

- `00-source-admin-home.png`、`00-source-reporter-home.png`：修改前视觉基线。
- `01` 至 `04`：四种身份的实现截图。
- `05`、`06`：相同视口的修改前/修改后并排对比图。

## 验收边界

本轮未接后端、未注册 mock route、未发起 API 请求；得分、排名和支部列表均为可交互原型演示数据，不代表真实生产统计结果。
