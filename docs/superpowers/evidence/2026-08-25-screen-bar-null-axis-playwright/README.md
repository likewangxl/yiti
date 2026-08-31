# 省分行经营总览“年日均存款”柱状图横轴验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen/SCR_PROVINCE?preview=draft`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-bar-null-axis-final`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。浏览器上下文注册 5 条 mock route；未执行保存、发布或其他写操作。

## 验收场景

草稿预览使用现场问题对应的 blockId `32`、数据源绑定 `9013` 和 `LATEST` 周期。`/api/screen/data` 模拟当前真实数据形态：单行多指标，第一项指标值为 `null`，其余指标有数值。

## 验收结论

1. 后端确认 `state=draft` 后，顶部大屏标题不显示；返回按钮、时钟保留，组件标题仍显示“年日均存款”。
2. 类目轴显示七个 `items[].label`，包括“一般性存款年日均余额”“基础性存款年日均余额”等，不再显示 `null`。
3. 第一项真实空值保留为空柱；其他六项指标正常显示柱体和数值，没有伪造或替换数据。
4. 草稿取数请求为 `schemaVersion=1 + previewState=draft + screenCode=SCR_PROVINCE + blockId=32 + period=LATEST`。
5. 清空初始导航记录并重新加载后，Console 为 0 error、0 warning。

## 证据

- `01-annual-deposit-label-axis.png`：完整草稿预览截图。
- `routes.raw.txt`：5 条开发态 mock 路由。
- `network.raw.txt`：草稿取数请求体和带首项 `null` 的响应正文。
- `console.raw.txt`：清空初始导航记录后的 CLI Console 输出。
- `commands.md`：实际 CLI 操作顺序。
