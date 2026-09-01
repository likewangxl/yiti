# 红色引擎三角色非四大维度任务验收命令摘要

执行日期：2026-09-01（Asia/Shanghai）

本文件只记录本轮官方 `playwright-cli` 的实际验收动作与可复核证据位置，不记录密码、Cookie、token、登录响应体或请求体。

## 环境与会话

- 登录入口：`http://127.0.0.1:8093/#/login?normal`
- 官方 CLI：`playwright-cli 0.1.18`
- 浏览器：本机 Chromium
- 前端：`http://127.0.0.1:8093`，`VITE_USE_MOCK=false`
- 后端代理：`http://127.0.0.1:18091`
- 三个独立 CLI 会话：`round2-reporter`、`round2-secretary`、`round2-org-final`
- 会话中分别使用报送员 `rm_zhang`、支部书记 `wangw67`、组织审核员 `yangdb1`；凭据只在浏览器交互中输入。

命令基线（在 `xanzc_frontend/` 下执行，示例不含凭据）：

```bash
env PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-playwright-daemon-root \
  PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
  ./node_modules/.bin/playwright-cli -s=round2-reporter \
  open 'http://127.0.0.1:8093/#/login?normal' --browser chromium
```

登录后对三个会话分别执行 `snapshot`、`screenshot`、`route-list`、`console`，并通过页面操作核对真实请求。三个最终会话的 `route-list` 均为 `No active routes`，因此本轮不是开发态 mock 验收。

## 实际验收动作

### 角色菜单、首页与入口

- 报送员：登录后访问首页、上报信息、待处理/审核中/已通过/已驳回页签及红黄牌预警池；核对任务标题、任务说明、任务类型、查询与链接展示。首页展示所在机构得分、排名，未展示全员达标率。
- 支部书记：登录后访问首页、支部审核工作台及红黄牌预警池；工作台使用“待处理、审核中、已通过、已驳回”页签，原任务处理入口已合并；未展示四大维度材料上报、上报信息。首页展示所在机构得分、排名，未展示全员达标率。
- 组织审核员：登录后访问首页、工作台、任务管理及红黄牌预警池；菜单不展示四大维度材料上报、上报信息、年度考核归档、数据导出，任务管理承担任务发布与导出入口；工作台支持左侧“四大维度材料上报/临时任务”筛选。

### 组织审核员创建临时任务

在任务管理页通过“新增任务”创建并发布了以下真实临时任务，目标为单个党支部，每个任务一份填报：

- `E2E-TEMP-A-20260901-1320`
- `E2E-TEMP-B-20260901-1320`
- `E2E-TEMP-C-PASS-20260901`（任务/分配编号 3，用于最终通过）
- `E2E-TEMP-D-REJECT-20260901`（任务/分配编号 4，用于最终驳回）

任务创建请求 `POST /api/re/tasks` 均返回 `200 OK`。临时任务详情中的网页链接可点击；本轮采用不要求附件的任务验证文字填报和状态流转。

### 定时但非四大维度的普通任务

- 在任务管理“新增任务”中选择定时任务、普通任务、指定曲江支行、每月初、持续 5 天、无需附件，创建 `E2E-RECURRING-GENERAL-20260901`。
- 周期下拉选项实际包含每月末、每月初、每周末、每周初、每季度末、每季度初六项。
- 将持续天数输入为 32 天并点击发布时，页面提示“持续天数不能超过当月自然天数”；该次请求仅有组织树 `GET`，没有发送 `POST /api/re/tasks`。
- 恢复为 5 天后，`POST /api/re/tasks` 返回 `200 OK`；任务管理列表展示“定时任务、普通任务、每月初·持续5天、0/1”。数据库验收事实为 `NATURE=SCHEDULED`、`TYPE_CODE=GENERAL`、`CYCLE=MONTH_START`、`DURATION_DAYS=5`，窗口为 `2026-09-01 00:00:00` 至 `2026-09-05 23:59:59`。
- 报送员待处理列表可查询并打开该任务；详情最终显示“定时任务填报/任务性质定时任务”，审核中/已通过列表显示“普通任务/定时任务/每月初”。前端 `RECURRING` 接口别名兼容已通过页面验证。
- 该任务完整链路为 `SUBMIT → APPROVE_BRANCH → SUBMIT_TO_ORG → APPROVE_ORG`，最终状态 `APPROVED`；支部卡片显示定时任务和每月初周期，组织工作台显示“定时任务·每月初”且无评分区。

### 报送员填报与重提

- 在“待处理”中查询并打开临时任务，确认详情展示开始/结束时间、任务标题、任务说明及可点击网页链接；不要求附件的任务不展示上传要求。
- 报送员分别提交任务 3、4 的在线填报内容，`POST /api/re/tasks/submissions` 返回 `200 OK`，任务进入支部审核。
- 支部驳回任务 4 后，报送员重新打开同一任务分配并重提，仍调用 `POST /api/re/tasks/submissions`，返回 `200 OK`，复用原任务分配。
- 审核中、已通过、已驳回页签保留原上报信息展示规则；临时任务维度显示为“临时任务”，考核项显示任务标题，不展示审核状态、审核意见和端员得分列。

### 支部书记审核

- 任务 3：点击“通过”调用 `POST /api/re/reviews/tasks/branch/3/approve` 返回 `200 OK`；随后单独点击“提交组织审核”，调用 `POST /api/re/reviews/tasks/branch/3/submit-to-org` 返回 `200 OK`。验证通过与提交是两个独立动作。
- 任务 4：支部书记填写驳回意见后调用 `POST /api/re/reviews/tasks/branch/4/reject` 返回 `200 OK`，任务回退报送员端；报送员重提后，支部书记再次通过并单独提交组织审核，两个请求均返回 `200 OK`。
- 支部工作台的待处理/审核中/已通过/已驳回页签及任务详情随上述状态更新；支部驳回能力已实际执行验证。
- 定时普通任务 task5 的 `POST /api/re/reviews/tasks/branch/5/approve` 与 `POST /api/re/reviews/tasks/branch/5/submit-to-org` 均返回 `200 OK`。

### 组织审核员终审

- 任务 3：调用 `POST /api/re/reviews/tasks/org/3/approve` 返回 `200 OK`，任务进入最终通过。
- 任务 4：组织审核员未填写意见时，页面校验阻止请求，未产生组织驳回 `POST`；填写必填意见后调用 `POST /api/re/reviews/tasks/org/4/reject` 返回 `200 OK`，任务回退报送员端并可继续重提。
- 终态页签、报送员首页待办去除/回退、支部审核工作台数据与任务管理详情均通过页面截图和请求摘要核对。
- 定时普通任务 task5 的 `POST /api/re/reviews/tasks/org/5/approve` 返回 `200 OK`，最终状态为 `APPROVED`；组织工作台列表及任务处理区显示“定时任务·每月初”，未显示评分区。

### 任务详情、导出与预警池

- 组织审核员打开任务 3 详情，`GET /api/re/tasks/3`、`GET /api/re/tasks/3/assignments?pageNo=1&pageSize=10`、`GET /api/sys/dicts/RE_ITEM_CODE/items` 均返回 `200 OK`；详情展示支部填报时间、提交人和填报内容。
- 点击任务导出创建 `POST /api/re/tasks/3/exports` 返回 `200 OK` 并进入 `QUEUED`，状态轮询请求返回 `200 OK`。本轮未把排队状态误记为 ZIP 已生成。
- 三角色均打开红黄牌预警池，接口可用但当前红牌、黄牌测试数据为空；未据此判定自动统计规则。

## 证据索引

- 请求摘要：`../network/round2-reporter-final.network.txt`、`../network/round2-secretary-final.network.txt`、`../network/round2-org-final.network.txt`
- mock/路由核验：`../routes/round2-reporter-final.route-list.txt`、`../routes/round2-secretary-final.route-list.txt`、`../routes/round2-org-final.route-list.txt`
- 最终 console 摘要：`../console/round2-reporter-final.console.txt`、`../console/round2-secretary-final.console.txt`、`../console/round2-org-final.console.txt`
- 页面截图：见 `../screenshots/`；本轮新增终态截图见证据目录根部的六个流程 PNG 和三个定时普通任务 PNG，具体清单见 `../README.md`。

## 明确未覆盖项

- 按用户明确要求，四大维度材料上报页面及其多次上传规则未测试。
- 当前无 OBS；附件真实上传、在线下载及导出 ZIP/Excel/附件目录未做真实联调。导出仅验证任务创建、排队和状态轮询；隔离测试配置关闭异步 worker，故未生成文件。
- 红黄牌自动统计所需测试数据为空。
- 组织管理员专属“逾期上报待执行扣分”板块不在本次三个账号验收范围内。
- 仅记录新增功能的验收结果，不审计或优化既有页面缺陷。
