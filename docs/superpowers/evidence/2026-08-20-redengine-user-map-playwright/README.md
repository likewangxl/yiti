# 红色引擎用户党组织映射 Playwright CLI 验收

## 结论与边界

- 日期：2026-08-20（Asia/Shanghai）
- 工具：项目内官方 `playwright-cli` 0.1.18，Chromium，1440×900。
- 页面：`http://127.0.0.1:8091/#/redengine/user-map`。
- 结论：开发态 mock 页面验收通过。列表显示 `PT_USER.USERNAME=EMP001`；新增映射的 auth 用户下拉显示 `EMP001/EMP002`；选择 `EMP002` 后，POST 请求体提交的是对应的 `PT_USER.USER_ID=PT_USER_ID_1002`，未提交 `username`。
- 边界：本 checkout 的 18081 后端未运行；默认后端配置连接 `yiti` 且启用 JDBC Session/Quartz，因此未擅自启动。下述 7 条浏览器 route 均为开发态 mock，请勿将本证据表述为真实后端联调或数据库验证。

## 归档

- [commands-and-routes.md](commands-and-routes.md)：原始 CLI 命令和完整 mock body。
- [route-list.raw.txt](route-list.raw.txt)：CLI 最终 route-list 原始输出。
- [requests.raw.txt](requests.raw.txt)：请求清单、POST 请求/响应摘要及原始 body。
- [console.raw.txt](console.raw.txt)：CLI console 原始输出。
- [user-map-list.png](user-map-list.png)：列表显示 USERNAME。
- [user-select-dropdown.png](user-select-dropdown.png)：新增映射 auth 用户下拉。

## 验收要点

1. 页面列表首列为“用户工号”，真实渲染值为 `EMP001`，不是持久化主键 `PT_USER_ID_1001`。
2. “新增映射”中的用户工号由远程下拉提供，选项为 `EMP001`、`EMP002`，不再允许自由输入。
3. 选择 `EMP002`、第一党支部、报送员后提交，CLI `request-body 60` 原始结果为：

```json
{"userId":"PT_USER_ID_1002","partyOrgId":2,"partyRole":"REPORTER"}
```

4. console 为 0 error、1 条仓库既有 Vue Router 用法 warning。
