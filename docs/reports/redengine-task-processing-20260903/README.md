# 红色引擎“任务处理”真实页面验收

## 验收结论

- 验收时间：2026-09-03（Asia/Shanghai）
- 验收前端：`/home/djdev/leid/yiti/xanzc_frontend`
- 前端地址：`http://127.0.0.1:8093/#/login?normal`
- 前端代理：`http://127.0.0.1:18091`
- 开发态 API mock：关闭（`VITE_USE_MOCK=false`）
- 后端：当前 checkout 的 `bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar`，端口 18091
- 数据库：隔离测试库 `yit_test`；未操作 `yiti`
- 浏览器工具：仓库本地官方 `@playwright/cli` 0.1.18

验收结果：

1. 报送员菜单和页面标题均显示“任务处理”，不再显示“上报信息”。
2. 支部书记菜单和页面标题均显示“任务处理”，不再显示“支部审核工作台”。
3. 报送员通过四大维度页面提交 `2.1` 材料后，接口返回 200，支部书记待处理数量变为 1，并显示对应任务卡片。
4. 点击书记任务卡片后，页面 URL 不变，在当前页弹出详情。
5. 四大维度任务弹窗显示维度、任务名称、材料明细编码、结构化材料、材料摘要和附件。
6. 临时任务弹窗显示任务性质、任务说明、本次填报内容和任务附件，与四大维度详情不同。
7. 两个浏览器会话均未注册拦截路由；所有列出的红色引擎请求均到达 8093 前端代理及 18091 后端并返回 200。

## 测试数据

- 任务：`E2E-FOURDIM-VISIBILITY-20260903-1317`
- 任务 ID / 实例 ID / 分配 ID：`6 / 6 / 8`
- 材料：`E2E四维可见性验证材料-20260903-1323`
- 维度 / 明细：`dim2 / 2.1`
- 提交后分配状态：`BRANCH_PENDING`
- 任务提交版本：`1`

## 截图

- `screenshots/reporter-task-processing.png`：报送员“任务处理”页面。
- `screenshots/reporter-fourdim-submit.png`：四大维度材料真实提交后的页面。
- `screenshots/secretary-task-processing-fourdim.png`：书记“任务处理”待办出现四维任务。
- `screenshots/secretary-fourdim-detail-modal.png`：当前页四维材料详情弹窗。
- `screenshots/secretary-general-detail-modal.png`：当前页临时任务详情弹窗。

## 已知非阻断告警

- 浏览器控制台错误数为 0。
- 存在 Vue Router 旧写法和 Element Plus `el-radio label` API 的既有弃用警告；不影响本次流程，但建议后续单独治理。
- 文件上传、下载仍由页面明确标注为开发态 mock，本次没有把它当成真实 OBS 文件联调结论。
