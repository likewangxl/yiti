# 个人经营驾驶舱：设计与验收说明

状态：容器与聚合实现完成，官方 CLI 合成前端验收已通过；真实后端业务联调仍待部署和回填。正式路由为 `#/personal-dashboard`，登录态顶层路由，`requiredMenu=/workspace`、`hideInMenu=true`；工作台提供“我的经营驾驶舱”入口。

## 目标与页面布局

驾驶舱服务当前登录员工，采用 `CorporateDashboard` 的深蓝青色视觉，全屏显示员工姓名、机构、刷新时间、刷新、全屏和返回工作台。

| 区域 | 内容与要求 |
| --- | --- |
| 顶部 | 工作台真实 `metricCards` 经营指标，按员工配置返回，最多展示前 6 项；不把待办、客户或申请数量冒充经营指标。 |
| 中部左 | 今日优先事项：工作流待办与两类触达近期项合并展示，按服务端 SLA 紧急程度排序，最多展示近期 6 项。 |
| 中部右 | 我的主办客户：只使用营销客户“我的”列表，展示服务端返回的近期 6 项。 |
| 底部 | 我发起的业务进度：资产立项与中台支持各取近期 6 项后按时间归并，保留两个来源独立统计。 |

宽屏为“顶部指标—中部两列—底部进度”，窄屏纵向排列；允许页面纵向滚动。触达办理复用既有 `TouchTaskDetailDialog` 与服务端能力判断，其余详情进入原业务页面；本功能不新增业务写接口。

## 数据源与个人口径

| 分区/来源 | 请求与口径 |
| --- | --- |
| 指标 | `GET /api/portal/workspace`；`completionRate→achievementRate`、`dataTime→dataDate`、`changeRate→mom`；只有 `aggregateErrors.metricCards` 严格归入指标错误。 |
| 工作流待办 | `GET /api/workflow/tasks?pageNo=1&pageSize=20`；服务端按当前登录员工返回，不传 `empId`。仅作为近期待办样本，不能据此宣称今日总数。 |
| 触达待处理 | `GET /api/touch-tasks?status=PENDING&pageNo=1&pageSize=10`；结果是本人执行或同机构协同可见项，不能称纯执行量。 |
| 触达进行中 | `GET /api/touch-tasks?status=IN_PROGRESS&pageNo=1&pageSize=10`；`canOperateTask` 决定 `handle`，`canWriteLog` 决定 `supplement`，否则为 `view`。 |
| 主办客户 | `GET /api/marketing/customers/mine?pageSize=6`；本人范围由服务端确定，不用其他客户列表补数。 |
| 资产立项 | `GET /api/marketing/asset-projects?tab=MY&pageSize=6`；状态按来源展示：草稿、审批中、办理中、已完成、已驳回、已撤回等。 |
| 中台支持 | `GET /api/support-requests?onlyMine=true&pageSize=6`；状态按来源展示；仅有 `createdTime` 时标注“创建时间”。 |

指标字段只做映射，不自行计算完成率或环比。资产有 `submittedTime` 时标注“提交时间”，否则用 `createdTime` 标注“创建时间”。业务来源 `total` 各自保留，缺失为未知；当前页长度不能冒充总数，不能合并成“今日事项总数”或“运行中业务总数”。工作流事项按 `RED→YELLOW→GREEN/BLUE→未知` 和来源期限排序，未知期限排末；页面只展示近期排序结果并明确“非全量排序”。

有效数字 `0` 与 `null`、缺字段、空来源、403、请求失败分别表达；不能把缺失或失败格式化为 0。页面使用统一加载提示，分区独立表达空、无权限、错误和部分成功状态；API 使用 `Promise.allSettled` 整体提交，部分来源失败时保留成功来源。未知业务状态原样保留，不能从标题或节点推断结论。

## 身份、竞态与导航

页面加载个人数据前严格请求 `/api/auth/current-user`，以该响应确定姓名和机构；不接受 URL 或客户端员工号。刷新、退出、换人和 401 都递增 generation 并清空旧模型；迟到身份、聚合或客户详情响应不得写回当前页面。客户详情请求另有序号保护，避免 A 客户迟到覆盖 B 客户。

导航只接受 `customer`、`touch`、`asset`、`support`、`todo`、`todos`、`customers`、`progress` 等受控 kind，不接受任意 URL。客户详情严格请求 `/api/marketing/customers/:id`；工作流按 `ASSET_PROJECT`、`SUPPORT`、`TARGET_ADJUST`、`ALLOC_ADJUST` 映射既有页面，未知类型回工作台；业务进度“查看更多”提供资产立项和中台支持两个入口。

## 实现文件

- [personalDashboard.js](/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/xanzc_frontend/src/api/personalDashboard.js) 及其测试：7 源真实 HTTP、字段投影、分区状态和排序。
- [PersonalDashboardPage.vue](/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/xanzc_frontend/src/views/screen/personal/PersonalDashboardPage.vue) 及其测试：Session 确认、代际清理、导航、全屏和既有弹窗复用。
- [PersonalDashboard.vue](/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/xanzc_frontend/src/views/screen/personal/PersonalDashboard.vue)：四区视觉展示。
- [router/index.js](/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/xanzc_frontend/src/router/index.js) 及路由测试；[workspace/Index.vue](/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/xanzc_frontend/src/views/workspace/Index.vue) 及入口回归测试。

## 验收矩阵

| 项目 | 结果 | 证据/备注 |
| --- | --- | --- |
| 后端相关测试 | 55 项通过 | 主代理汇总的后端测试结果；不等于真实库联调。 |
| 前端个人驾驶舱定向测试 | 30 项通过 | API 4、容器 8、展示 5、工作台 12、路由 1；另有最新 Personal + Page + Corporate 22 项回归通过，属于额外子集，不能与 30 项相加。 |
| 生产构建 | 通过 | `vite build` 通过；保留既有 Sass legacy API 与大 chunk 警告。 |
| 主代理 CLI 未登录访问 | 通过 | `route-list` 为 `No active routes`；`/api/auth/current-user` 返回 401 后跳转登录。 |
| personal_ui 合成业务浏览器验收 | 通过（合成） | 官方 CLI 覆盖 6 指标、6 事项、6 客户、6 业务；1440/1920 首屏四标题可见；客户详情抽屉、触达详情、业务入口、刷新通过；workflow 500 与 customer 403 保留其他分区；console 0 errors/0 warnings。证据：[README](/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/docs/superpowers/evidence/2026-09-20-personal-dashboard/README.md)，原始资料 `/tmp/personal-dashboard-evidence`。 |
| 图标与布局 QA | 通过（合成） | 主代理已查看 3 尺寸真实截图并完成布局/图标检查；该结果仍属于前端合成 mock 验收。 |

## 部署边界与后续

本任务未重启现有后端、未改数据库。新增指标字段映射、`onlyMine=true` 和相关后端能力必须随兼容后端部署后才生效；本次浏览器结果是真实前端页面配合合成 mock，仍不宣称真实业务数据联调通过。浏览器验收应归档真实命令、route-list、console、请求摘要和截图，不以合成数据替代联调结论。

二期可增加指标趋势、客户经营提醒、业务时间线和来源更新时间；写操作、通知联动或导出需另行定义资源授权、服务端校验、审计和验收。
