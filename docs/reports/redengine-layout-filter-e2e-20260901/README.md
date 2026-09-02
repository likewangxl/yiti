# 红色引擎侧栏与任务管理筛选区验收

验收日期：2026-09-01

验收提交：`4b307107`（页面验收在当前 checkout 的运行构件上进行）

## 范围

- 仅验证红色引擎侧栏与主系统侧栏的宽度、左边界和菜单项对齐。
- 仅验证组织审核员 `yangdb1` 的任务管理页面筛选区。
- 未测试四大维度材料上报，也未执行新增、发布、编辑、删除、导出等写操作。

## 运行边界

现场检查发现 8091 已由本 checkout 的既有 Vite 进程（PID 833639）占用，18081 已由本 checkout 的既有 Java 进程占用；两者启动参数/配置不能证明是本次隔离 profile，因此未停止或复用。为避免误连正式库，本次使用隔离 profile 和独立端口：

- 后端：`127.0.0.1:18082`，PID 900081，工作目录 `/home/djdev/leid/yiti/bootstrap`，`redengine-task-e2e` profile，数据源 `yit_test`。
- 前端：`0.0.0.0:8092`，PID 900598，工作目录 `/home/djdev/leid/yiti/xanzc_frontend`，代理目标 `http://127.0.0.1:18082`，`VITE_USE_MOCK=false`。
- profile 实际关闭 Quartz、任务调度、OBS、边车、SOAP 和通知外发。
- 验收完成后已关闭本次启动的 8092/18082 服务和两个浏览器会话；既有 8091/18081 未停止。

启动和浏览器命令见 [commands/commands.txt](commands/commands.txt)。

## 结果

| 视口 | 红色引擎侧栏 | 筛选区横向滚动 | 页面横向溢出 | 五个条件 | 查询/重置按钮 |
| --- | --- | --- | --- | --- | --- |
| 1440×900 | x=0，width=220px | `clientWidth=1178`，`scrollWidth=1178` | body/html 均 1440/1440 | 全部可见 | 均在筛选卡片内，中心命中 |
| 1024×768 | x=0，width=220px | `clientWidth=762`，`scrollWidth=762` | body/html 均 1024/1024 | 全部可见 | 均在筛选卡片内，中心命中 |

任务管理筛选条件均实际渲染：任务标题、任务性质、任务类型、周期、状态；允许在 1024×768 下换行为两行，未出现横向滚动条。1440×900 下筛选卡片为 x=240、width=1180、bottom=252.5；查询和重置按钮 bottom=251.5，完全落在卡片内。

主系统工作台对照：`aside.side` x=0、width=220px，红色引擎 `.re-sidebar` x=0、width=220px；两侧栏均从页面左边界开始。主系统菜单项外框 x=0、width=219px；红色引擎菜单项外框 x=0、width=220px，图标左边界约为 16px（当前选中项为 18px，差异来自 Element Plus 选中态边框），与主系统内容左边界近似一致。对照截图见 [screenshots/workspace-1440x900.png](screenshots/workspace-1440x900.png)。

## 真实请求与 console

- 官方项目内 `xanzc_frontend/node_modules/.bin/playwright-cli`，Chromium；未使用 Playwright Test、MCP 或 mock。
- 两个会话 `route-list` 均为 `No active routes`，见 [routes/routes.txt](routes/routes.txt)。
- 登录、菜单、权限、任务列表请求均到达隔离后端并返回 200；查询按钮触发的第二次任务列表 GET 也返回 200，见 [network/requests.txt](network/requests.txt)。
- console 错误数：0。存在 2 类既有 Vue Router 警告（未匹配 `/marketing/asset-projects`、transition/keep-alive 用法），原始内容见 [console/console-raw.log](console/console-raw.log)；与本次筛选布局无关。

截图：

- [task-management-1440x900.png](screenshots/task-management-1440x900.png)
- [task-management-1024x768.png](screenshots/task-management-1024x768.png)
- [workspace-1440x900.png](screenshots/workspace-1440x900.png)

## 未验证项

- 未在被占用的 8091/18081 服务上操作，也未证明其对应本次隔离 profile。
- 未执行任何任务写操作，未测试四大维度材料上报。
- 未审计或修复 console 中已有 Vue Router 警告。
