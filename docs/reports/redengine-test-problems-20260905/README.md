# 红色引擎《测试问题.docx》页面问题验收

验收时间：2026-09-05（Asia/Shanghai）

## 结论

- 支部书记“任务填报”和“四大维度材料上报”已改为同一 `tabpanel` 内的来源页签，列表不再上下堆叠；当前页签支持单一 Tab 焦点以及方向键、Home、End 切换。
- 支部书记点击“已驳回”后，真实任务队列只显示 `E2E-TEMP-D-REJECT-20260901`，未混入待处理任务 `E2E-TEMP-A-20260901-1320`。任务摘要在同一响应式行中展示任务名称、说明、党支部、提交人、提交时间和任务性质。
- 报送员四个状态卡片的尺寸和交互已与书记页对齐；真实浏览器测量两页卡片均约为 `101.19 × 56px`，数字字号/行高均为 `24/36px`。
- 报送员已驳回任务详情底部展示完整审核处理记录。真实 assignment 4 显示 4 条记录，其中包含两次驳回，并逐条展示处理人、时间、阶段/动作、状态变化和意见。
- 两个浏览器会话的 `route-list` 均为 `No active routes`；本次任务接口请求到达当前 checkout 的真实后端。

## 数据库环境偏差

当前 `yit_test` 中，角色 `R_RE_SECR` 对 `P_RE_REVIEW_Q`、`P_RE_REVIEW_APPR`、`P_RE_REVIEW_REJ` 的授权计数均为 `0`。因此直接四维材料队列 `/api/re/reviews/queue` 返回 `403`，页面显示“旧材料审核队列加载失败”。

该偏差与 2026-09-03 已归档的 `3/3` 授权结果不一致，说明测试库之后发生过回退或重建。本轮没有获得数据库写入授权，未擅自执行既有授权脚本；因此“四维材料页签布局”已验证，但“四维材料真实数据加载”明确未通过联调。

## 运行边界

- 前端：`127.0.0.1:8094`，`VITE_USE_MOCK=false`，代理到 `127.0.0.1:18092`。
- 后端：当前 checkout 构建产物，profile 为 `redengine-task-e2e`，数据源为 `yit_test`。
- 隔离项：Quartz、任务调度、外发通知、Sidecar 注册、OBS、SOAP 监听均关闭或使用本机不可达占位地址。
- 未点击任何审核、提交或填报写操作；登录仅产生隔离 Session 数据。
- 页面现有文件提示仍明确标注“开发态 mock，非真实文件联调”；附件不在本次验收范围。

## 证据索引

- [实际命令](commands.md)
- [请求与响应摘要](network.raw.md)
- [浏览器控制台](console.raw.md)
- [报送员状态卡片](screenshots/reporter-status-cards-rejected.png)
- [报送员任务详情](screenshots/reporter-rejected-review-history.png)
- [完整审核处理记录](screenshots/reporter-review-history-panel.png)
- [书记已驳回任务页签](screenshots/secretary-rejected-task-tab.png)

截图 SHA-256：

```text
2d03d1093938a6f9ce8284ef1965f7a79dcb100f70a9f36639760ed6bd29f5f1  reporter-rejected-review-history.png
fa657dad95d5a336537212e56bf45dae347c0fba43a2983b051c3149f5fc3bf7  reporter-review-history-panel.png
853b55b279f462a50322d19e4b7e9f3cd916905226c9267b29b64a5f50e40f54  reporter-status-cards-rejected.png
c7fb72b42af9d9ec79f18761dd1b19002b83ca5eb9bb72c1df469e49a2da5260  secretary-rejected-task-tab.png
```
