# 大屏设计器现代化 UI 验收证据（2026-08-24）

## 验收结论

- 前端：`http://127.0.0.1:8091`
- 后端代理目标：`http://127.0.0.1:18081`
- 登录后可直接进入 `#/screen-admin/designer`，未出现“无权限访问该页面”。
- 已显式切换到 `省分行经营总览（全辖）`（`SCR_PROVINCE`）；画布标题为“省分行经营总览”，地图标题为“陕西省经营指标地图”，不是“西安六区经营地图”。
- 新工作台工具栏、当前大屏上下文、分组操作区、保存状态、左右语义化面板和组件搜索均在真实页面生效。
- 组件搜索输入“趋势折线”后，基础组件计数为 0、地图组件计数为 0、图表组件计数为 1，仅展示 `LINE_TREND`。
- Playwright CLI `route-list` 返回 `No active routes`，未注册浏览器 mock；请求确实到达本地后端代理。

## 截图

- [省分行设计器现代化界面](./01-province-designer-modern.png)
- [组件搜索结果](./02-component-search.png)

## 自动化验证

```text
定向测试：2 files passed；32 tests passed
生产构建：Vite 4.5.14；3035 modules transformed；built in 12.76s
```

完整测试套件另有既存基线问题：`RedEngineLogout.spec.js` 1 项断言失败、7 个 operation-cell 测试因 Windows 绝对 Sass `@use` 路径失败，随后 Vitest 进程达到约 4 GB 堆上限。上述失败不在本次大屏设计器改动范围；本次两个目标测试文件全部通过。

## 已知运行态问题

设计器本体、屏列表、画布和 `/screen/data` 均请求成功，但当前运行的后端实例对以下新增接口返回 `AUTH-40302 / 资源未登记`：

- `GET /api/screen/admin/screens/9105/map-region-metrics` → 403
- `GET /api/screen/admin/screens/9101/map-region-metrics` → 403

前端开发态随后触发 `src/api/http.js` 的 mock fallback，因此地图指标能显示，但这只能标记为“仅开发态 mock，非联调”。本次 UI 验收不把该接口记为联调通过。

详细原始记录见 [commands.md](./commands.md)、[route-list.txt](./route-list.txt)、[console.txt](./console.txt) 和 [requests.txt](./requests.txt)。
