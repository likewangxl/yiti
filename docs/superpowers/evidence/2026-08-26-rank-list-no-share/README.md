# 排行榜去色块、去占比列及类目列加宽验收

- 验收日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli` 0.1.18
- 会话：`rank-no-share`
- 验收性质：仅开发态 mock，非联调。

## 结论

- 排行榜正常显示 4 行排名、类目和 2 个指标值。
- `.rl-bar` / `.rl-fill` 色块节点数为 0。
- `.rl-share` 占比列节点数为 0，`占比 ` 文字数为 0。
- 类目第一列 `.rl-name` 计算宽度为 `140px`（`clientWidth=140`）。
- 默认仍按第一个指标“存款余额”倒序排列，指标值共 8 个。
- console 为 0 error、0 warning。

## 证据

- `commands.md`：实际 Playwright CLI 命令与 DOM 验收结果。
- `routes.raw.txt`：12 条开发态 mock 路由清单。
- `console.raw.txt`：原始 console 摘要。
- `network.raw.txt`：原始请求/响应摘要。
- `01-rank-no-color-share-wide-category.png`：设计器真实渲染截图。
