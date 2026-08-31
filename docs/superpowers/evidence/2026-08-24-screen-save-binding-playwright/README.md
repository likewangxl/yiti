# 大屏保存、图表绑定与权限提示验收

- 日期：2026-08-24
- 前端：`http://127.0.0.1:8092/#/screen-admin/designer`
- 工具：官方 `playwright-cli`，会话 `screen-save-fix`
- 模式：仅开发态 mock，非联调。真实登录会话另行完成了无 mock 的保存验证。

## 结论

1. 进入设计器后页面正常显示，没有“无权限访问该页面”提示。
2. `map-region-metrics` 返回业务码 `RPT-43017` 时只触发无 toast 的查询回退，设计器主页面不被误报为无权限。
3. 保存请求包含 `backgroundType`、`bgGradient`、`bgImage`，以及图表 `bindJson.dsId=9009`。
4. 保存响应将新图表的 `blockId` 从 `null` 解析为 `77001`；保存后“已选择数据源，保存后预览”占位消失。
5. 保存后的 console 为 0 error、0 warning；保存及后续草稿取数请求均为 200。

## 截图

- `01-before-save.png`：保存前显示“已选择数据源，保存后预览”。
- `02-after-save.png`：保存后进入已解析区块状态，页面无无权限提示。

## 归档

- `routes.txt`：全部 mock route 清单。
- `console.txt`：加载和保存后的原始 console 摘要。
- `network.txt`：保存后的原始请求摘要。
- `save-request.json` / `save-response.json`：保存请求及响应正文。
- `commands.md`：真实执行命令。
