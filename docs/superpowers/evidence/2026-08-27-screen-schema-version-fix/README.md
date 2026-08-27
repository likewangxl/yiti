# 大屏运行版本一致性修复验收

验收时间：2026-08-27（Asia/Shanghai）

## 结论

- 官方 `playwright-cli` 版本：`0.1.18`。
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`。
- 当前大屏：`SCR_CORP_OVERVIEW`（对公经营总览（全辖））。
- 浏览器未注册任何拦截路由；目标大屏的 5 个 `/api/screen/data` 请求均到达后端、均携带 `schemaVersion: 2`、均返回 HTTP 200。
- 页面未出现 `.scr-block-err`，也未出现“区块不属于当前发布版本”；“经营机构余额排名”实际显示 6 行。
- 控制台中的 401 是第一次使用失效的本地默认密码产生；两个 403 是经营地图资源未登记后触发既有前端 fallback，与本次图表数据请求及版本修复无关。目标 `/api/screen/data` 请求未使用 fallback。

截图见 [designer-corp-overview.png](designer-corp-overview.png)。

## 数据库边界

- 仅在 `yiti_test` 中执行事务内修正并立即回滚，验证更新条件、结果和恢复一致性。
- `yiti` 只读复核结果仍为草稿版本 1、发布版本 2；本轮未对 `yiti` 执行写操作。
- 按仓库门禁，必须获得再次明确确认后，才可对 `yiti` 修正草稿版本。
