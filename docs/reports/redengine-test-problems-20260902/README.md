# 红色引擎《测试问题》修复验收证据

执行日期：2026-09-02（Asia/Shanghai）

## 验收环境

- 当前 checkout：`/home/djdev/leid/yiti`
- 前端：`http://127.0.0.1:8093`，进程 cwd 为当前 checkout 的 `xanzc_frontend`
- 后端：`http://127.0.0.1:18091`，进程 cwd 为当前 checkout，`SPRING_PROFILES_ACTIVE=redengine-task-e2e`
- 前端代理：`VITE_PROXY_TARGET=http://127.0.0.1:18091`
- mock 开关：`VITE_USE_MOCK=false`
- 浏览器：官方 `playwright-cli 0.1.18`，Chromium，三个独立角色会话

## 真实页面结论

- 报送员首页待办已成为可聚焦、可点击的按钮；点击后在同一 SPA 页签跳转到 `/redengine/records`，并携带 `taskId/taskInstanceId/assignmentId/status`。
- 报送员“上报信息”四个页签显示 `3/0/2/1`，网络证据证明当前页读取正常分页、其他页签以 `pageSize=1` 读取真实 `total`，不是当前页 `records.length`。
- 普通任务标题点击后没有新增浏览器页签；已通过任务显示“已通过”，填报框禁用且不展示“提交填报”按钮。
- 支部审核页四个页签显示 `0/0/2/1`；已通过卡片明确标注“任务说明”和“本次填报内容”，不再视觉上误认为两次提交。
- 组织审核页四个页签显示 `0/0/2/1`，四个队列请求均返回 200。
- 新增任务页面的任务性质、类型、标题、说明、对象、开始时间和截止时间均显示必填标记；条件必填项由对应选择状态控制。
- 三个最终会话的 `route-list` 均为 `No active routes`；console 最终均为 0 error。保留的 Vue Router/Element Plus warning 是既有告警，未作为本次功能通过证据。
- 页面继续显示“文件上传、下载当前为开发态 mock，非真实文件联调”，没有把无 OBS 环境误报为真实附件闭环。

## 运行边界

- 本轮浏览器操作只有登录和 GET 查询，没有提交、审核、发布、映射修改或其他业务 DML。
- 前端 Vite 已加载当前源码；运行中的后端 JAR 未重启，因此“映射变更同步待办”“驳回意见新 DTO 字段”“映射支部书记附件权限”只由后端单元测试验证，不能据本目录声称新后端代码已完成真实运行联调。
- 现有测试数据中支部/组织待处理均为 0，未通过页面重新执行审核状态流转；既定正确链路仍为 `BRANCH_PENDING -> BRANCH_APPROVED -> ORG_PENDING -> APPROVED`。

## 文件索引

- 命令摘要：`commands.md`
- 无 mock 路由：`routes/final-route-list.txt`
- 最终 console：`console/final-console-summary.txt`
- 请求摘要：`network/final-requests.txt`
- 截图：`screenshots/`
