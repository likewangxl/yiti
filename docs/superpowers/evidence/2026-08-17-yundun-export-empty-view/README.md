# 云盾导出与查看态空字段验收（2026-08-17）

## 结论

- 人员违规信息、信贷风险信息的导出行模型均忽略 `inUse`，Excel 不再包含“是否可用/是否启用”列。
- 按当前筛选导出和按勾选 ID 导出均在服务端增加 `in_use = 1`，返回前再次过滤不可用记录。
- 两个页面的查看弹窗中，空文本、下拉、日期和数字字段不显示“请输入/请选择”等默认提示；新增、编辑态提示不变。

## 自动化验证

- 后端定向导出契约：`ViolationExportContractTest`，5/5 通过。
- 云盾模块全量测试：14/14 通过。
- 前端定向测试：`violationConfig.spec.js`，37/37 通过。
- 前端 `npm run build`：成功。
- Maven 隔离仓库完整构建：19 个 Reactor 模块全部成功。
- 运行态：18080 的 `/v3/api-docs` 返回 200；未登录 `current-user` 返回 401；8090 代理同样返回 401。
- 运行时云盾 JAR SHA-256 与本次构建产物一致：`c0d84c09bf34cfa3478a4296a8805e743f971f35e3807102ef011d61f7430dd3`。

## Playwright CLI 页面验收

使用官方 CLI：

`/home/djdev/.npm/_npx/9b853437c4cd15c0/node_modules/.bin/playwright-cli`

访问路由：

- `http://127.0.0.1:8090/#/yundun/accountability-violations`
- `http://127.0.0.1:8090/#/yundun/credit-violations`

当前没有可用登录会话，因此只对鉴权、菜单、通知、两类列表和详情 GET 接口做开发态路由模拟；页面使用本地 8090 Vite 服务的真实源码和浏览器渲染。该步骤验证 UI 行为，不作为真实鉴权、数据库或导出 HTTP 集成证据；导出约束由后端测试和运行构件校验覆盖。未写入数据库。

模拟路由共 8 条：`current-user`、`my-menus`、`permissions`、`unread-count`，以及两类违规的列表和详情接口。两页列表和详情请求均为 200。

弹窗断言结果：

- 人员违规：`prompts=[]`、`hasInputPrompt=false`、`hasSelectPrompt=false`，工号 `E001` 正常反显。
- 信贷风险：`prompts=[]`、`hasInputPrompt=false`、`hasSelectPrompt=false`，员工工号 `E002` 正常反显。
- 控制台：0 error、0 warning。

截图：

- [人员违规查看态空字段](./accountability-view-empty-fields.png)
- [信贷风险查看态空字段](./credit-view-empty-fields.png)
