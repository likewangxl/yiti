# 实际执行命令

以下命令在 `xanzc_frontend/` 目录执行。route 响应为页面加载所需的最小 JSON，试跑精确请求与响应见 `network.raw.txt`。

```bash
npx playwright-cli -s=screen-field-meta-col-sort open about:blank --browser=chromium
npx playwright-cli -s=screen-field-meta-col-sort resize 1920 1080
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/auth/current-user' --status 200 --content-type application/json --body '<验收用户 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/auth/my-menus' --status 200 --content-type application/json --body '<大屏数据源菜单 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/auth/permissions' --status 200 --content-type application/json --body '<数据源管理权限 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/screen/admin/datasources' --status 200 --content-type application/json --body '<9013 数据源列表 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/perf/metrics*' --status 200 --content-type application/json --body '<机构指标 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/screen/admin/kpi-schemes*' --status 200 --content-type application/json --body '<空 KPI 方案 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/admin/org-groups*' --status 200 --content-type application/json --body '<空机构组 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/notifications/unread-count' --status 200 --content-type application/json --body '<未读数 0 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort route '**/api/screen/admin/datasources/try-run' --status 200 --content-type application/json --body '<乱序中文列名试跑 JSON>'
npx playwright-cli -s=screen-field-meta-col-sort goto 'http://127.0.0.1:8091/#/screen-admin/datasources'
npx playwright-cli -s=screen-field-meta-col-sort run-code '<打开试跑，填写原因，执行后返回预览表头>'
npx playwright-cli -s=screen-field-meta-col-sort run-code '<关闭试跑弹窗，打开编辑，新增字段元数据行，尝试展开列名下拉>'
npx playwright-cli -s=screen-field-meta-col-sort snapshot
npx playwright-cli -s=screen-field-meta-col-sort run-code '<在字段元数据表内精确定位第一个 combobox，展开并返回选项顺序>'
mkdir -p ../docs/superpowers/evidence/2026-08-27-screen-field-meta-column-sort-playwright
npx playwright-cli -s=screen-field-meta-col-sort screenshot --filename ../docs/superpowers/evidence/2026-08-27-screen-field-meta-column-sort-playwright/01-column-options-chinese-order.png
npx playwright-cli -s=screen-field-meta-col-sort route-list
npx playwright-cli -s=screen-field-meta-col-sort console debug
npx playwright-cli -s=screen-field-meta-col-sort requests --filter '/api/'
npx playwright-cli -s=screen-field-meta-col-sort request 63
npx playwright-cli -s=screen-field-meta-col-sort request-body 63
npx playwright-cli -s=screen-field-meta-col-sort response-body 63
npx playwright-cli -s=screen-field-meta-col-sort close
```

首次展开下拉时使用了过宽的 placeholder 定位，CLI 在点击等待阶段超时；此前“关闭试跑、打开编辑、新增行”已完成。随后用字段元数据表内的精确 combobox 定位重试成功，不影响业务状态或验收结论。
