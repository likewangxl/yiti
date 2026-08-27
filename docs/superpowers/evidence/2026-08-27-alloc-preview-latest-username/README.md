# 业绩调整原业绩分配最新批次与用户名展示验收

## 验收边界

- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 场景：新建调整申请，输入客户编号后查看“原业绩分配”
- 浏览器：项目本地官方 `xanzc_frontend/node_modules/.bin/playwright-cli`，Firefox，视口 `1440x900`
- 当前 checkout 前端 PID 292267，cwd 为 `/home/djdev/lijh/yiti/xanzc_frontend`，监听 8090
- 当前 checkout 后端 18080 未启动；本次使用开发态 route mock，只验证真实页面展示，不代表后端、RBAC 或数据库联调

## Mock 契约

`GET /api/report/alloc-preview` 仅返回模拟“最新批次”的两条关系。每条数据同时包含 `empId`（PT_USER.USER_ID）和 `username`（PT_USER.USERNAME），用于确认页面展示后者：

- `2347 -> 12038011（刘鑫） -> 60%`
- `2458 -> 12038012（王梅） -> 40%`

完整响应见 [alloc-preview-response.json](./alloc-preview-response.json)，全部拦截路由见 [route-list.txt](./route-list.txt)。

## 验收结果

- 原业绩分配表只显示两条模拟最新批次关系，比例为 60% 和 40%
- 员工名称显示 `12038011（刘鑫）`、`12038012（王梅）`
- 页面未显示 PT_USER.USER_ID `2347`、`2458`
- `GET /api/report/alloc-preview?...` 返回 HTTP 200
- Console：Errors 0，Warnings 0
- 截图：[latest-batch-username-display.png](./latest-batch-username-display.png)
- DOM 断言：[assertions.json](./assertions.json)

后端“仅取最新来源批次”和 USER_ID 批量反查 USERNAME 的正确性由 Java 单元测试与隔离测试库 Mapper IT 覆盖。
