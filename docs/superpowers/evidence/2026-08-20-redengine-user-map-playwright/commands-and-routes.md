# CLI 命令与完整开发态 mock 路由

以下命令均在 `xanzc_frontend/` 执行；会话名为 `redengine-user-map-20260820`。

```bash
./node_modules/.bin/playwright-cli --version
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 open about:blank --browser=chromium
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/auth/current-user' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-current-user","data":{"empId":"PT_USER_ID_ADMIN","username":"admin","displayName":"开发态 Mock 管理员","mainOrgCode":"ORG001","mainOrgName":"测试机构","roles":[{"roleId":"SYS_ADMIN","roleCode":"SYS_ADMIN","roleChName":"系统管理员"}],"isSystemAdmin":true}}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/auth/my-menus' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-menus","data":[{"resourceId":"M_RE_ENGINE","resourceUrl":"/redengine/dashboard","menuName":"红色引擎","children":[{"resourceId":"P_RE_MAP_LIST","resourceUrl":"/redengine/user-map","menuName":"用户党组织映射","children":[]}]}]}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/auth/permissions' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-permissions","data":{"resourceUrls":["/api/re/user-party-maps","/api/re/orgs/tree","/api/admin/users"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/re/user-party-maps' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-user-maps","data":[{"id":1,"userId":"PT_USER_ID_1001","username":"EMP001","partyOrgId":2,"partyRole":"REPORTER","createTime":"2026-08-20 10:00:00","updateTime":"2026-08-20 10:00:00"}]}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/re/orgs/tree' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-org-tree","data":[{"id":2,"orgName":"第一党支部","children":[]}]}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/admin/users*' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-auth-users","data":{"pageNo":1,"pageSize":100,"total":2,"records":[{"userId":"PT_USER_ID_1001","username":"EMP001","userchnname":"测试用户一"},{"userId":"PT_USER_ID_1002","username":"EMP002","userchnname":"测试用户二"}]}}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route '**/api/notifications/unread-count' --status 200 --content-type application/json --body '{"code":"0","message":"success","traceId":"mock-notifications","data":0}'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 route-list
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 resize 1440 900
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 goto http://127.0.0.1:8091/#/redengine/user-map
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 snapshot
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 click e61
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 run-code 'async (page) => { await page.getByRole("combobox", { name: "* 用户工号" }).click({ force: true }); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 click e172
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 run-code 'async (page) => { await page.getByRole("combobox", { name: "* 党组织" }).click({ force: true }); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 run-code 'async (page) => { await page.getByRole("option", { name: "第一党支部" }).click(); await page.getByRole("combobox", { name: "* 党内角色" }).click({ force: true }); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 run-code 'async (page) => { await page.getByRole("option", { name: "报送员", exact: true }).click(); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 click e163
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 requests
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 request 60
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 request-body 60
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 response-body 60
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 console
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 screenshot --filename /home/djdev/leid/yiti/docs/superpowers/evidence/2026-08-20-redengine-user-map-playwright/user-map-list.png --full-page
./node_modules/.bin/playwright-cli -s=redengine-user-map-20260820 screenshot --filename /home/djdev/leid/yiti/docs/superpowers/evidence/2026-08-20-redengine-user-map-playwright/user-select-dropdown.png --full-page
```

曾在 `about:blank` 执行 `sessionstorage-set xanzc:user ...`，浏览器按规范返回 `SecurityError`（无源文档不可访问 sessionStorage）；随后由已注册的 `current-user` route 完成登录态恢复。首次直接点击 Element Plus combobox 的输入节点被透明占位层拦截并超时，后续使用同一官方 CLI 的 `run-code` + `click({ force: true })` 展开控件。
