# 大屏图表复制粘贴身份修复验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli` 0.1.18
- 会话：`screen-paste-blockid`
- 模式：仅开发态 mock，非联调；未写入真实大屏数据。

## 结论

- 原图表节点为 `id=w-chart-original`、`blockId=88`。
- 通过右键菜单复制、粘贴后，画布从 1 个图表变为 2 个；复制图偏移到 `(140, 140)`，并显示“已选择数据源，保存后预览”。
- 保存请求中原图仍为 `blockId=88`，复制图生成新节点 `id=w-b13zd1`，且 `blockId=null`。
- 复制图完整保留 `bindJson`、`styleJson`、`drillJson` 和 `propValue`。
- mock 保存响应为复制图分配 `blockId=89` 后，页面显示“已保存”，两个图表均正常显示 `123,456`，不再出现“大屏布局配置非法”。
- 控制台 0 error；12 条 warning 均为既有 Element Plus `el-radio label` 弃用提示。

## 截图

- `01-pasted-chart-awaits-new-block.png`：粘贴后复制图等待保存生成新区块身份。
- `02-saved-with-new-block.png`：保存后两个图表均正常渲染。
