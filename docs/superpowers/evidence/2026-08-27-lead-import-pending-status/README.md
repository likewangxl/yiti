# 线索批量导入待审批状态验收

- 时间：2026-08-27 23:36（Asia/Shanghai）
- 页面：`http://127.0.0.1:8090/#/customers/leads/new`
- 方式：官方 `playwright-cli`，会话 `marketing-filter-final-20260827`
- 路由 mock：无

## 验收结果

1. 将历史批量导入线索“测试客户5”提交审批后，线索录入记录从“草稿”变为“待审批”，待审批卡片由 1 增为 2。
2. 线索审批页面出现“测试客户5”，状态为“待审批”，证明 Flowable 流程实例已启动且审批查询可见。
3. 状态卡与状态标签语义色一致：草稿 `primary`、待审批 `warning`、已通过 `success`、已退回 `danger`。
4. 页面无控制台错误；存在与本功能无关的路由告警。

截图：[线索录入状态](./lead-entry-status.png)
