# 柱状对比类目轴单选验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-bar-category-axis`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。注册 12 条只读 mock route，未注册或触发保存、发布等写接口。

## 验收结论

1. 柱状对比组件属性出现“类目轴”单选下拉，初始绑定 `categoryCol=org_code`，界面按字段元数据 alias 显示“机构编码”。
2. 下拉候选实际为“机构编码、区域、余额、customer_count、org_name”：同时覆盖 DIM/METRIC 字段元数据、数据源指标快照和机构主体聚合推导列，并按原始列名去重。
3. 实际单选“区域”后，界面只显示一个类目轴选中值，指标列“余额、customer_count”保持不变，页面进入“有未保存修改”状态。
4. 重新展开下拉后仅“区域”为选中状态，证明类目轴是单选而不是指标列式多选。
5. 画布预置为横向柱形；运行时“横向使用 Y 轴、基础/堆叠使用 X 轴”由 56/56 定向组件测试覆盖。
6. Console 为 0 error；6 条 warning 均为既有 Element Plus `el-radio label` 弃用提示。
7. 网络仅有认证与大屏管理只读 GET；没有保存、发布、PUT、PATCH、POST 或 DELETE 请求。

## 证据

- `01-category-options.png`：类目轴下拉的完整候选及初始“机构编码”单选状态。
- `02-region-selected.png`：实际选择“区域”后，类目轴单值与指标列互不影响。
- `03-region-option-selected.png`：重新展开后只有“区域”为选中状态。
- `commands.md`：实际命令和关键交互返回。
- `routes.raw.txt`：12 条开发态 mock route 清单。
- `network.raw.txt`：原始请求摘要与关键画布、数据源响应。
- `console.raw.txt`：原始 Console 摘要。
