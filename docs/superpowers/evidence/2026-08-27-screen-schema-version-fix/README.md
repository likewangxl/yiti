# 大屏运行版本一致性修复验收

验收时间：2026-08-27（Asia/Shanghai）

## 结论

- 官方 `playwright-cli` 版本：`0.1.18`。
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`。
- 当前大屏：`SCR_CORP_OVERVIEW`（对公经营总览（全辖））。
- 浏览器未注册任何拦截路由；目标大屏的 5 个 `/api/screen/data` 请求均到达后端、均携带 `schemaVersion: 2`、均返回 HTTP 200。
- 页面未出现 `.scr-block-err`，也未出现“区块不属于当前发布版本”；“经营机构余额排名”实际显示 6 行。
- 控制台中的 401 是第一次使用失效的本地默认密码产生；两个 403 是经营地图资源未登记后触发既有前端 fallback，与本次图表数据请求及版本修复无关。目标 `/api/screen/data` 请求未使用 fallback。

修正前兼容性截图见 [designer-corp-overview.png](designer-corp-overview.png)，真实库修正后的复验截图见 [designer-corp-overview-postfix.png](designer-corp-overview-postfix.png)。

## 数据库执行结果

- 仅在 `yiti_test` 中执行事务内修正并立即回滚，验证更新条件、结果和恢复一致性。
- 用户于 2026-08-27 明确二次确认后，对 `yiti.RPT_SCREEN` 的 `SCR_CORP_OVERVIEW` 执行单行乐观更新，影响行数为 1。
- 修正后草稿和发布 `schemaVersion` 均为 2，`canvas_version` 从 28 增至 29；草稿组件数量仍为 6，两个 JSON 均有效。
- 写后真实页面复验无 `.scr-block-err`，排行榜仍显示 6 行，5 个目标数据请求均携带 `schemaVersion: 2` 并返回 HTTP 200。
