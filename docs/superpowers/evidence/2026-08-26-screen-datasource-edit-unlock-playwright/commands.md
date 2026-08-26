# 实际执行命令

以下命令在仓库根目录执行；长 JSON 按用途概括，精确保存请求见 `network.raw.txt`。

```bash
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock open 'http://127.0.0.1:8091/' --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock resize 1920 1080
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/auth/current-user' --body '<验收用户 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/auth/my-menus' --body '<大屏数据源菜单 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/auth/permissions' --body '<数据源管理权限 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/screen/admin/datasources' --body '<9012 且发布引用 SCR_PROVINCE 的列表 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/perf/metrics*' --body '<机构指标 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/screen/admin/kpi-schemes*' --body '<空 KPI 方案 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/admin/org-groups*' --body '<空机构组 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/screen/admin/datasources/9012' --body '<更新成功 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route '**/api/notifications/unread-count' --body '<未读数 0 JSON>' --content-type application/json
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock goto 'http://127.0.0.1:8091/#/screen-admin/datasources'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock click '<编辑按钮>'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock run-code '<点击停用 DISABLED>'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock fill '<操作原因输入框>' '验证发布引用下允许停用并保留审计'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock console --clear
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock requests --clear
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock screenshot --filename docs/superpowers/evidence/2026-08-26-screen-datasource-edit-unlock-playwright/01-published-reference-editable.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock run-code '<点击保存>'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock screenshot --filename docs/superpowers/evidence/2026-08-26-screen-datasource-edit-unlock-playwright/02-save-success-delete-blocked.png
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock requests --filter '/api/'
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock request 1
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock request-body 1
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock response-body 1
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock route-list
xanzc_frontend/node_modules/.bin/playwright-cli -s=screen-datasource-edit-unlock console
```
