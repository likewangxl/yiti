# 个人核心指标官方 CLI 验收

- 页面：`http://127.0.0.1:8092/#/personal-dashboard`
- 工具：项目内官方 `xanzc_frontend/node_modules/.bin/playwright-cli`
- 视口：`1440×900`
- 数据边界：**仅开发态 mock，非联调**。`scripts/register-mocks.js` 只在浏览器进程内拦截请求，未登录真实账号、不启动或重启后端、不读写数据库。

运行：

```bash
cd yiti/xanzc_frontend
../docs/superpowers/evidence/2026-09-20-personal-core-metrics/scripts/run-qa.sh
```

本轮覆盖个人核心区的六项存贷候选、`sourceType=EMP_LATEST_IMPORT`、`comparisonType=PREVIOUS_MONTH_END`、`previousValue`、有效 `0`、`null`、单位缺失、未关联目标，以及无员工宽表行/有数据日期但 actual 全空两种提示。前者提示“当前账号暂无员工指标结果”，后者提示“当前账号在该数据日期暂无所选指标结果”。同时检查四个区域标题、无“日环比”误导标签、上月末比较文案和目标空值文案。

原始 routes、console、requests、断言和 1440 截图写入 `/tmp/personal-core-metrics-evidence/`；仓库内只保留本说明和可复用脚本。
`routes.raw.txt` 是官方 CLI `run-code` 返回的实际拦截器注册结果；`routes-list.raw.txt` 作为 CLI 查询快照一并保留，部分 CLI 版本对在 `run-code` 页面上下文注册的 handler 会显示 `No active routes`，不以此覆盖注册结果。
