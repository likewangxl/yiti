# 所有大屏省级地图检查（2026-11-10）

范围：所有已发布屏与省级地图复用入口。分行、对公的默认地图和配置地图显式启用无机构地市隐藏；通用 PresentationMapWidget 默认启用，并根据授权机构明细生成地市机构数量。零售沿用既有规则。市级区县保留原行为，不将 cityCode 当成区县编码。

旧画布 MapCenter 同步关闭已确认空地市的标签、hover、tooltip、点击弹窗，并将对应 ECharts 区域设置为 silent。机构证据合并点位和有效非部门画像；无法确认归属时保留未知，不按金额零值判断空城。

## 实际发布范围与验证

只读 API 盘点 5 个 ACTIVE 已发布大屏：SCR_PROVINCE、SCR_CORP_OVERVIEW、SCR_RETAIL_OVERVIEW 有省级地图；SCR_BRANCH、SCR_PERSON 无省级地图。另有 DISABLED 测试屏未启用。本次未修改数据库、端口或代理。

证据目录：`E:/cx-workspace/runtime/all-screen-map-20261110/`。

- `inventory.txt`、`map-layouts.txt`：当前屏目录及发布包地图清单。
- `branch-live.txt`、`corporate-live.txt`、`retail-live.txt`：官方 playwright-cli 对真实 WebGL 页面逐城实际鼠标悬停和点击。三个页面均仅保留西安、宝鸡、咸阳、渭南、榆林标记；铜川、延安、汉中、安康、商洛无标记、hover、tooltip、状态弹窗或选择变化。西安正常高亮、详情与选择。
- `routes.txt`：No active routes。`commands.jsonl` 归档命令，三个 live 文件包含原始 console 以及真实 API 请求/响应摘要；`summary.json` 保存后端匹配 trace 的最小记录。无 API mock。
- 三个 `*-overview-v1.png` 已目视检查，地市轮廓保留。
- `legacy-browser-verified.txt`、`legacy-component-fixture.png`：旧 MapCenter 当前不在已发布页面中，使用真实组件、显式机构夹具通过 CLI 验证，空区域无高亮、可见 tooltip 或详情弹窗。此项仅组件夹具验收，不是正式业务数据联调；临时挂载已清理。前期脚本动态导入和 DOM 检查失败记录保留在同目录，最终以 verified 记录为准。
- `legacy-silent-red.log` 记录修复前失败；`independent-tests.log`：8 个文件、82 项测试通过，覆盖机构缺坐标、未知归属、两种地图分支、市级范围兼容。
- `build.log`：构建通过；既有 Sass 弃用及 bundle 大小提示仍存在。

代码由执行代理分工修改，主代理检查实际 diff、补充 silent 回归并独立运行上述测试和浏览器验收。结果是本次运行快照。
