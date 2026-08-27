# 大屏指标数据项与展示名称编辑验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-metric-labels`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 11 条 mock route；未点击保存、发布或执行任何写操作。

## 验收结论

1. 选择“全省存款核心指标(聚合)”后，数据源显示“全省存款聚合”，每个指标项同时显示接口原始数据列 `col` 和可编辑展示名称 `label`。
2. 将首项展示名称改为“存款净增（自定义）”后，原始列“一般性存款月均余额较上月-机构”保持不变，第二个指标项也未受影响。
3. 切换到贷款组件后，只显示该组件自己的原始列“贷款余额-机构”和展示名称“全省贷款余额”。
4. 再切回存款组件，自定义展示名称及两个存款指标项均恢复，未沿用贷款组件配置。
5. 清空网络记录后的交互阶段只有 3 次数据源 GET 和 3 次机构组 GET，没有 POST、PUT、PATCH、DELETE 请求。
6. 交互阶段 Console 为 0 error、18 warning；warning 均为既有 Element Plus `el-radio label` 弃用提示，与本次功能无关。

## 证据

- `01-deposit-custom-label.png`：存款组件首项展示名称已自定义，原始列仍可见。
- `02-loan-own-label.png`：切换到贷款组件后显示其独立的原始列和展示名称。
- `03-deposit-custom-label-restored.png`：切回存款组件后自定义名称与两项绑定恢复。
- `routes.raw.txt`：本次注册的 11 条开发态 mock 路由。
- `network.raw.txt`：清空记录后的交互请求清单及数据源响应摘要。
- `console.raw.txt`：官方 CLI 生成的原始 Console 日志。
- `commands.md`：实际执行命令和交互顺序。
