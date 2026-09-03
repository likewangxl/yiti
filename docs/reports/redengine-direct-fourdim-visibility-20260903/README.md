# 支部书记直报四大维度材料可见性验收

验收时间：2026-09-03（Asia/Shanghai）

## 结论

- 当前目录前端 `xanzc_frontend` 通过 `8091` 提供页面，并代理到当前目录后端 `18081`。
- 支部书记 `wangw67` 登录后，工作台显示两条“联建规范度”待办；点击最新一条后进入 `submitId=4`。
- 页面入口及标题均为“任务处理”，页面按业务来源分成“任务填报”和“四大维度材料上报”两个独立列表。
- `submitId=4` 在当前“任务处理”页面弹出“四大维度材料详情”，显示提交人 `E10001`、维度“外联共建”、任务“1.1 联建规范度”和材料内容“测试上报”。本次只读验收未点击“审核通过”或“驳回”。
- 浏览器未注册任何 mock route；首页汇总、两类队列和材料详情请求均到达真实后端并返回 `200`。
- 生产 `yiti` 已补齐支部书记角色的三项材料审核资源授权；验收后材料 `ID=4` 仍为状态 `1`（已提交），页面查看未改变业务状态。

## 证据

- [工作台待办截图](screenshots/wangw67-dashboard-material-todos.png)
- [当前页材料详情弹窗截图](screenshots/wangw67-submit-4-detail.png)
- [Playwright CLI 命令](commands.md)
- [请求与路由证据](network.raw.md)
- [浏览器控制台证据](console.raw.md)
- [数据库与运行态证据](database-and-runtime.md)

截图 SHA-256：

```text
bc1df28ee15f89b8e62df64ab8ed8ea5b5f6c53f7ed1ab57aa67a4330caf7628  wangw67-dashboard-material-todos.png
2c3f4fc7d564da64d6546f73d4f2d0ebd5780165c33abf26c7ceab3b1bc30426  wangw67-submit-4-detail.png
```

## 已知非阻断告警

页面无 console error。存在 Vue Router 旧式 `router-view`/`transition` 用法告警，以及 Element Plus `el-radio label` 即将弃用告警；它们不影响本次材料可见性与详情弹窗链路。
