# STAT_SHOW 归档与指标重算日期规则浏览器验收

验收日期：2026-08-14（Asia/Shanghai）

## 环境与方式

- 页面：`http://127.0.0.1:8090`，进程工作目录为 `/home/djdev/lijh/yiti/xanzc_frontend`。
- 工具：官方 `@playwright/cli`，通过 `npx --yes --package @playwright/cli@latest playwright-cli` 调用 Firefox。
- UIAS UAT 域名在当前环境不可达，因此通过 `playwright-cli route` 隔离模拟登录用户、指标和任务列表响应。
- 浏览器会话内的 `/api/**` 请求全部由 CLI 路由拦截；没有请求写入后端或数据库。

## 验收结果

以 2026-08-14 为当天：

| 页面/字段 | 2026-08-13 | 2026-08-14 | 2026-08-15 | 2026-07-24 | 2026-07-25 | 2026-07-31 |
| --- | --- | --- | --- | --- | --- | --- |
| 指标库-数据日期 | 可选 | 禁选 | 禁选 | 禁选 | 可选 | 可选 |
| 任务监控-数据日期 | 可选 | 禁选 | 禁选 | 禁选 | 可选 | 可选 |
| 任务调度-指标任务数据日期 | 可选，默认值 | 禁选 | 禁选 | 禁选 | 可选 | 可选 |
| 任务调度-普通任务数据日期 | 可选 | 可选，默认值 | 禁选 | 可选 | 可选 | 可选 |

补充验证：

- `LEVEL1_METRIC_CALC` 的业绩分配日期允许当天、禁选未来日期，不套用 20 天/月末限制。
- 指标任务与普通任务使用不同默认日期：指标任务默认 T-1，普通任务默认当天。
- 三个指标重算入口均呈现“昨天至20天前或月末”的日期语义。
- 最终页面控制台为 0 error、1 条 Firefox scroll-linked positioning 性能提示；该提示与本次功能无关。

## 关键截图

- [指标库日期规则](page-2026-08-14T05-59-41-731Z.png)
- [任务监控日期规则](page-2026-08-14T06-05-42-581Z.png)
- [任务调度指标任务与业绩分配日期](page-2026-08-14T06-08-47-139Z.png)

## 关键命令

```bash
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc open http://127.0.0.1:8090/#/login --browser=firefox
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc route "http://127.0.0.1:8090/api/**" --body '{"code":"0","data":[]}' --content-type application/json
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc goto http://127.0.0.1:8090/#/perf/metrics
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc goto http://127.0.0.1:8090/#/perf/task-monitor
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc goto http://127.0.0.1:8090/#/system/jobs
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc console debug
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc requests
npx --yes --package @playwright/cli@latest playwright-cli -s=stat-show-recalc close
```

自动化测试另行覆盖提交函数的二次校验和精确提示文案：`当天及未来日期不可重算`、`超过20天只能选择月末`。
