# 大屏设计器指标列编辑与组件切换验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-metric-items`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 11 条 mock route，复现“省分行经营总览”的两个指标卡；未点击保存、发布或任何其他写操作。

## 验收结论

1. 选择“全省存款核心指标(聚合)”后，数据源显示“全省存款聚合”，右侧出现“指标列”多选框。
2. 指标候选展示数据源别名“全省存款较上月净增”“全省存款月均余额”；选择后两项同时回显。
3. 切换到第二个指标卡后，数据源切换为“全省贷款聚合”，指标项只显示该组件自己的“全省贷款余额”。
4. 再切回存款指标卡，两项存款指标仍完整回显，没有沿用贷款指标。
5. 清空网络记录后，交互阶段只有 3 次数据源 GET 和 3 次机构组 GET；没有 POST、PUT、PATCH、DELETE 请求。
6. 交互阶段 Console 为 0 error、18 warning；18 条均为既有 Element Plus `el-radio label` 弃用提示，每次重建属性面板重复 6 条，与本次指标列功能无关。

## 证据

- `01-deposit-two-items.png`：存款组件选择两项指标。
- `02-loan-item.png`：切换到贷款组件后仅显示贷款指标。
- `03-deposit-items-restored.png`：切回存款组件后恢复两项存款指标。
- `routes.raw.txt`：11 条 mock 路由及响应摘要。
- `network.raw.txt`：交互阶段请求清单及数据源响应正文。
- `console.raw.txt`：官方 CLI 生成的原始 Console 日志。
- `commands.md`：实际执行命令和交互顺序。
