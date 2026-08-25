# 线索详情字典中文展示验收

## 结论

- 页面：`http://127.0.0.1:8090/#/customers/leads/new`
- 真实接口：Vite 8090 的运行时代理目标为 `http://127.0.0.1:18080`。
- mock：官方 `playwright-cli route-list` 返回 `No active routes`。
- 线索列表、详情和五类字典接口均返回 HTTP 200。
- 详情抽屉实际展示：所属行业“信息技术”、客户类型“对公客户”、集团类型“非集团客户”、企业类型“民营”、线索来源“自行挖掘”。
- 控制台：0 errors；2 条既有 Vue Router 未匹配警告（`/bizexec/loans`、`/bizexec/supports`），与本次详情字典展示无关。

## 归档

- `01-official-playwright-cli.raw.txt`：官方 CLI 命令、路由清单、请求/响应摘要、控制台原始输出和页面文本匹配结果。
- `02-runtime-backend.raw.txt`：8090 运行时代理目标与 18080 后端实际处理请求的日志证据。
- `lead-detail-dictionary-chinese.png`：1440×900 真实页面截图。

前置登录仅用于建立受控浏览器会话，不属于本次登录功能验收。凭据、Cookie、Session 和 Authorization 均未写入归档。
