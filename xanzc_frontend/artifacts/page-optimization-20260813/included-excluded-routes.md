# 路由范围与实际扫描状态

## 纳入：59 条普通后台命名路由

本次均计划以 `/#` 直达方式进行只读打开、`main/h1/overflow/console/network` 扫描。真实登录阻断发生在进入工作台之前，因此下表所有路由的实际扫描状态均为 **未开始（0/59）**；参数化详情路由也未尝试猜测 ID。

| 路由名 | 路径 | 状态 |
|---|---|---|
| Login | `/login` | 未开始 |
| NoAccess | `/no-access` | 未开始 |
| Workspace | `/workspace` | 未开始 |
| AnnouncementList | `/workspace/announcements` | 未开始 |
| AnnouncementDetail | `/announcement/:id` | 未开始（缺少安全发现的真实 ID） |
| NotificationList | `/workspace/notifications` | 未开始 |
| InfoNav | `/info/nav` | 未开始 |
| InfoAddressBook | `/info/address-book` | 未开始 |
| InfoProducts | `/info/products` | 未开始 |
| InfoDocuments | `/info/documents` | 未开始 |
| PerfMetrics | `/perf/metrics` | 未开始 |
| PerfKpiRules | `/perf/kpi-rules` | 未开始 |
| PerfTargets | `/perf/targets` | 未开始 |
| PerfTargetValues | `/perf/target-values` | 未开始 |
| PerfImport | `/perf/import` | 未开始 |
| PerfAdjust | `/perf/adjust` | 未开始 |
| PerfCompute | `/perf/compute` | 未开始 |
| PerfTaskMonitor | `/perf/task-monitor` | 未开始 |
| PerfKpiScoreDetail | `/perf/kpi-score-detail` | 未开始 |
| EvalTags | `/eval/tags` | 未开始 |
| EvalUserTags | `/eval/user-tags` | 未开始 |
| EvalRules | `/eval/rules` | 未开始 |
| EvalTasks | `/eval/tasks` | 未开始 |
| EvalMyTasks | `/eval/my-tasks` | 未开始 |
| ReportDynamic | `/report/dynamic` | 未开始 |
| ReportDash | `/report/dashboard` | 未开始 |
| ReportPresets | `/report/presets` | 未开始 |
| ReportFree | `/report/free` | 未开始 |
| ReportFreeDetail | `/report/free/:batchId` | 未开始（缺少安全发现的真实 ID） |
| ReportSql | `/report/sql` | 未开始 |
| ReportAmasApprovals | `/report/amas-approvals` | 未开始 |
| ReportAmasApprovalDetail | `/report/amas-approvals/:perfAdjustNo` | 未开始（缺少安全发现的真实 ID） |
| ScreenAdminDs | `/screen-admin/datasources` | 未开始 |
| ScreenAdminOrgProfiles | `/screen-admin/org-profiles` | 未开始 |
| ScreenAdminOrgGroups | `/screen-admin/org-groups` | 未开始 |
| GuaranteeQuery | `/guarantee/query` | 未开始 |
| HistoryDataImport | `/guarantee/data-import` | 未开始 |
| HistoryNotice | `/guarantee/notice` | 未开始 |
| HistoryPriceApproval | `/history/price-approval` | 未开始 |
| HistoryPriceApprovalDetail | `/history/price-approval/:priceApprId` | 未开始（缺少安全发现的真实 ID） |
| HistoryPerfAdjust | `/history/perf-adjust` | 未开始 |
| SysUsers | `/system/users` | 未开始 |
| SysRoles | `/system/roles` | 未开始 |
| SysResources | `/system/resources` | 未开始 |
| SysPermission | `/system/permission` | 未开始 |
| SysDict | `/system/dict` | 未开始 |
| SysCalendar | `/system/calendar` | 未开始 |
| SysJobs | `/system/jobs` | 未开始 |
| SysAudit | `/system/audit` | 未开始 |
| SysNotifications | `/system/notifications` | 未开始 |
| SysConfig | `/system/config` | 未开始 |
| SysFiles | `/system/files` | 未开始 |
| SysTimeoutRules | `/system/timeout-rules` | 未开始 |
| SysAnnouncements | `/system/announcements` | 未开始 |
| SysAnnouncementDetail | `/system/announcements/:id` | 未开始（缺少安全发现的真实 ID） |
| SysWorkflowFlows | `/system/workflow-flows` | 未开始 |
| SysWorkflowFlowEdit | `/system/workflow-flows/:id` | 未开始（缺少安全发现的真实 ID） |
| SysWorkflowMonitor | `/system/workflow-monitor` | 未开始 |
| SysPersonTags | `/system/person-tags` | 未开始 |

## 排除

- 12 条红色引擎路由（含 `/redengine/login` 与 11 条业务路由）：用户冻结范围明确排除，未打开。
- 大屏运行态 `/screen/:screenCode`：排除。
- 大屏设计器 `/screen-admin/designer`：排除。
- 未注册废弃页：不属于命名路由矩阵，未打开。

`/screen-admin/datasources`、`/screen-admin/org-profiles`、`/screen-admin/org-groups` 是普通浅色后台管理页，属于上方纳入范围，并非大屏运行态/设计器。
