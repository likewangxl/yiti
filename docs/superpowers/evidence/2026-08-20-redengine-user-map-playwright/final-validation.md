# 红色引擎用户党组织映射最终 Playwright CLI 验收

## 结论与验证边界

- 验收日期：2026-08-20（Asia/Shanghai）。
- 工具：项目内官方 `playwright-cli` 0.1.18，Chromium；最终会话为 `redengine-user-map-final-20260820`，视口为 1440×900。
- 前端：Vite 运行于 `http://127.0.0.1:18092/`，启动时明确设置 `VITE_USE_MOCK=false`。
- 但浏览器 context 实际注册了 7 条显式 CLI route，完整清单见本文件后文。因此本轮结论必须标注为：**仅开发态 mock，非联调**。
- 本轮没有启动后端，没有连接或操作数据库，也没有真实 API、RBAC、数据范围或 `PT_USER` 数据库联调通过的证据。

在上述边界内，页面验收结果如下：

1. 列表展示“用户工号、姓名、党组织、党内角色、创建时间、更新时间、操作”列，mock 数据实际渲染了 `EMP001 / 测试用户一 / 第一党支部 / 报送员` 和 `EMP002 / 测试用户二 / 第二党支部 / 支部书记`。
2. 查询区域实际展示“用户工号、姓名、党组织、党内角色”四个条件及“查询、重置”操作。
3. 页面实际展示 `共 23 条`、`10条/页`、3 个页码及上一页/下一页/跳页控件。
4. 四条件查询请求包含 `username`、`displayName`、`partyOrgId`、`partyRole`，并从 `pageNo=1` 开始。
5. 点击下一页后，请求变为 `pageNo=2`，同时保留全部四个查询条件。由于显式 route 对任意分页 URL 返回同一份 `pageNo=1` mock body，本证据只证明前端发出了正确的第二页请求，不声称真实第二页数据已联调或渲染。
6. “新增映射”的“用户工号”下拉实际显示 `EMP001 / 测试用户一` 和 `EMP002 / 测试用户二`，即同时展示工号和 `USERCHNNAME` 对应的中文姓名 mock 值。

## 完整 route 注册清单

官方 `route-list` 命令显示 7 条 route，但会截断 body 预览；以下记录本次实际注册的完整 pattern、status、content-type 和 body。

1. `**/api/auth/current-user`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-current-user-final","data":{"empId":"PT_USER_ID_ADMIN","username":"admin","displayName":"开发态 Mock 管理员","mainOrgCode":"ORG001","mainOrgName":"测试机构","roles":[{"roleId":"SYS_ADMIN","roleCode":"SYS_ADMIN","roleChName":"系统管理员"}],"isSystemAdmin":true}}
     ```

2. `**/api/auth/my-menus`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-menus-final","data":[{"resourceId":"M_RE_ENGINE","resourceUrl":"/redengine/dashboard","menuName":"红色引擎","children":[{"resourceId":"P_RE_MAP_LIST","resourceUrl":"/redengine/user-map","menuName":"用户党组织映射","children":[]}]}]}
     ```

3. `**/api/auth/permissions`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-permissions-final","data":{"resourceUrls":["/api/re/user-party-maps","/api/re/orgs/tree","/api/admin/users"],"roleCodes":["SYS_ADMIN"],"isSystemAdmin":true}}
     ```

4. `**/api/re/user-party-maps?*`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-user-maps-page-final","data":{"pageNo":1,"pageSize":10,"total":23,"totalPages":3,"records":[{"id":1,"userId":"PT_USER_ID_1001","username":"EMP001","displayName":"测试用户一","partyOrgId":2,"partyRole":"REPORTER","createTime":"2026-08-20 10:00:00","updateTime":"2026-08-20 10:00:00"},{"id":2,"userId":"PT_USER_ID_1002","username":"EMP002","displayName":"测试用户二","partyOrgId":3,"partyRole":"SECRETARY","createTime":"2026-08-20 10:05:00","updateTime":"2026-08-20 10:05:00"}]}}
     ```

5. `**/api/re/orgs/tree`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-org-tree-final","data":[{"id":2,"orgName":"第一党支部","children":[]},{"id":3,"orgName":"第二党支部","children":[]}]}
     ```

6. `**/api/admin/users*`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-auth-users-final","data":{"pageNo":1,"pageSize":100,"total":2,"totalPages":1,"records":[{"userId":"PT_USER_ID_1001","username":"EMP001","userchnname":"测试用户一"},{"userId":"PT_USER_ID_1002","username":"EMP002","userchnname":"测试用户二"}]}}
     ```

7. `**/api/notifications/unread-count`

   - status：`200`
   - content-type：`application/json`
   - body：

     ```json
     {"code":"0","message":"success","traceId":"mock-notifications-final","data":0}
     ```

## 运行环境与关键命令

以下命令在 `xanzc_frontend/` 执行。Vite 启动命令为：

```bash
env VITE_DEV_HOST=127.0.0.1 VITE_DEV_PORT=18092 VITE_DEV_STRICT_PORT=true VITE_USE_MOCK=false VITE_PROXY_TARGET=http://127.0.0.1:18081 npm run dev
```

官方 CLI 会话建立、route 清点、页面打开和初始清理：

```bash
./node_modules/.bin/playwright-cli --version
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 open about:blank --browser=chromium
# 实际逐条执行了 7 次 route PATTERN --status 200 --content-type application/json --body BODY；完整定义见本文件上文
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 route-list
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 resize 1440 900
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 goto http://127.0.0.1:18092/#/redengine/user-map
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 snapshot
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 requests --clear
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 console --clear
```

查询交互中，主代理先向用户工号和姓名分别填入带前后空格的 ` EMP001 `、` 测试用户一 `；普通点击 Element Plus 党组织下拉的尝试被覆盖层拦截并超时，随后用同一官方 CLI 强制展开下拉，再根据当时快照 ref 选择“第一党支部”和“报送员”，最后点击“查询”。成功阶段的关键命令如下（`e218`、`e236`、`e102` 为当时快照 ref，快照变化后不可复用）：

```bash
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 run-code 'async (page) => { await page.getByRole("combobox", { name: "党组织" }).click({ force: true }); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 snapshot
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 click e218
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 run-code 'async (page) => { await page.getByRole("combobox", { name: "党内角色" }).click({ force: true }); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 snapshot
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 click e236
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 click e102
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 requests
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 console
```

分页和新增映射下拉验收的关键命令：

```bash
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 requests --clear
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 run-code 'async (page) => { await page.getByRole("button", { name: "下一页" }).click(); await page.waitForTimeout(200); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 requests
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 requests --clear
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 run-code 'async (page) => { await page.getByRole("button", { name: "+ 新增映射" }).click(); await page.waitForTimeout(200); await page.getByRole("combobox", { name: "* 用户工号" }).click({ force: true }); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 snapshot
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 requests
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 console
```

截图和关闭会话的关键命令：

```bash
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 screenshot --filename /home/djdev/leid/yiti/docs/superpowers/evidence/2026-08-20-redengine-user-map-playwright/final-user-select-dropdown.png --full-page
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 run-code 'async (page) => { await page.getByRole("button", { name: "关闭此对话框" }).click(); }'
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 screenshot --filename /home/djdev/leid/yiti/docs/superpowers/evidence/2026-08-20-redengine-user-map-playwright/final-filtered-list.png --full-page
./node_modules/.bin/playwright-cli -s=redengine-user-map-final-20260820 close
```

## 原始请求摘要

四条件查询，`pageNo=1`：

```text
### Result
1. [GET] http://127.0.0.1:18092/api/re/user-party-maps?pageNo=1&pageSize=10&username=EMP001&displayName=%E6%B5%8B%E8%AF%95%E7%94%A8%E6%88%B7%E4%B8%80&partyOrgId=2&partyRole=REPORTER => [200] OK
```

点击下一页后，`pageNo=2` 且保留四个条件：

```text
### Result
1. [GET] http://127.0.0.1:18092/api/re/user-party-maps?pageNo=2&pageSize=10&username=EMP001&displayName=%E6%B5%8B%E8%AF%95%E7%94%A8%E6%88%B7%E4%B8%80&partyOrgId=2&partyRole=REPORTER => [200] OK
```

打开“新增映射”并展开“用户工号”下拉后，平台用户接口原始请求摘要为：

```text
### Result
1. [GET] http://127.0.0.1:18092/api/admin/users?pageNo=1&pageSize=100 => [200] OK
2. [GET] http://127.0.0.1:18092/api/admin/users?pageNo=1&pageSize=100 => [200] OK
```

以上请求均被浏览器 context 的显式 route 响应，HTTP 200 不能作为真实后端、RBAC 或数据库联调通过的依据。

## 原始 console

清空既有消息后，四条件查询及最终新增映射下拉验收得到的 CLI console 结果均为：

```text
### Result
Total messages: 0 (Errors: 0, Warnings: 0)
```

## 快照与截图结论

- 页面快照确认四个查询控件均有实际值：`EMP001`、`测试用户一`、`第一党支部`、`报送员`；列表含姓名列及两条 mock 数据；分页显示 `共 23 条`、`10条/页` 和 3 页。
- 新增映射快照确认“用户工号”下拉处于展开状态，包含 `EMP001 / 测试用户一` 与 `EMP002 / 测试用户二` 两个选项。
- [final-filtered-list.png](final-filtered-list.png)：四条件查询后的列表、姓名列和分页视觉证据。
- [final-user-select-dropdown.png](final-user-select-dropdown.png)：新增映射用户工号与中文姓名下拉视觉证据。

本文件只归档最终开发态 mock 页面验收事实，不取代真实后端、权限、数据范围和数据库联调验收。
