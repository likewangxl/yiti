# 主平台页面优化审计与后续设计规范

> 审计日期：2026-08-13
> 范围：`src/router/index.js` 中的普通后台命名路由，以及三个普通浅色的大屏管理页；其中 Login、NoAccess 为独立信息架构，不由 `DefaultLayout` 承载。
> 排除：12 个 `/redengine/**` 路由、大屏运行态 `/screen/:screenCode`、大屏设计器 `/screen-admin/designer`、未注册废弃页面。
> 本文只记录前端呈现与交互规则；不改变 API URL、HTTP method、payload、权限或数据口径。

## 1. 审计结论

- 路由表静态解析后，普通后台范围精确为 **59** 个命名路由。
- 其中 **55** 个是 CRUD、详情或配置工作面，统一接入受控 `.bp-crud` 基线；**4** 个是独立信息架构，不能套用 CRUD 表格规则：登录、无权限、工作台、行长仪表盘。
- 范围内有 **50** 个页面包含 Element Plus 表格，其中 49 个标准页面共 **89** 张表接入 `v-bp-overflow-tooltip`；共享基线统一表格正文 14px、表头/数据行 40px、状态标签最小高 24px、单元格省略和右侧操作列不换行。
- `v-bp-overflow-tooltip` 只会在根 `.bp-crud` 挂载时监听，且只对实际溢出的 `.el-table .cell` 写入原生 `title`；不覆盖页面已有业务 `title`。两个 `append-to-body` 弹窗以 `.bp-crud-dialog` 显式延续同一范围。红色引擎、运行态、设计器没有指令使用，零 DOM/交互影响。
- 命名路由审计由 `src/views/__tests__/crud-route-audit.spec.js` 固化：若新增普通后台路由但未接入 `.bp-crud` 或受控提示，测试会失败；红色引擎、运行态、设计器不会进入该断言。

## 2. 59 路由矩阵

| 路由名 | 路径 | 页面 | 处理 |
|---|---|---|---|
| `Login` | `/login` | `views/login/Index.vue` | 独立信息架构 |
| `NoAccess` | `/no-access` | `views/NoAccess.vue` | 独立信息架构 |
| `Workspace` | `/workspace` | `views/workspace/Index.vue` | 独立信息架构 |
| `AnnouncementList` | `/workspace/announcements` | `views/workspace/AnnouncementList.vue` | CRUD 基线 |
| `AnnouncementDetail` | `/announcement/:id` | `views/system/AnnouncementDetail.vue` | CRUD 基线 |
| `NotificationList` | `/workspace/notifications` | `views/workspace/NotificationList.vue` | CRUD 基线 |
| `InfoNav` | `/info/nav` | `views/info/NavHub.vue` | CRUD 基线 |
| `InfoAddressBook` | `/info/address-book` | `views/info/AddressBook.vue` | CRUD 基线 |
| `InfoProducts` | `/info/products` | `views/info/ProductLib.vue` | CRUD 基线 |
| `InfoDocuments` | `/info/documents` | `views/info/DocCenter.vue` | CRUD 基线 |
| `PerfMetrics` | `/perf/metrics` | `views/perf/Metrics.vue` | CRUD 基线 |
| `PerfKpiRules` | `/perf/kpi-rules` | `views/perf/KpiRules.vue` | CRUD 基线 |
| `PerfTargets` | `/perf/targets` | `views/perf/Targets.vue` | CRUD 基线 |
| `PerfTargetValues` | `/perf/target-values` | `views/perf/TargetValues.vue` | CRUD 基线 |
| `PerfImport` | `/perf/import` | `views/perf/Import.vue` | CRUD 基线 |
| `PerfAdjust` | `/perf/adjust` | `views/perf/Adjust.vue` | CRUD 基线 |
| `PerfCompute` | `/perf/compute` | `views/perf/Compute.vue` | CRUD 基线 |
| `PerfTaskMonitor` | `/perf/task-monitor` | `views/perf/TaskMonitor.vue` | CRUD 基线 |
| `PerfKpiScoreDetail` | `/perf/kpi-score-detail` | `views/perf/KpiScoreDetail.vue` | CRUD 基线 |
| `EvalTags` | `/eval/tags` | `views/eval/Tags.vue` | CRUD 基线 |
| `EvalUserTags` | `/eval/user-tags` | `views/eval/UserTags.vue` | CRUD 基线 |
| `EvalRules` | `/eval/rules` | `views/eval/Rules.vue` | CRUD 基线 |
| `EvalTasks` | `/eval/tasks` | `views/eval/Tasks.vue` | CRUD 基线 |
| `EvalMyTasks` | `/eval/my-tasks` | `views/eval/MyTasks.vue` | CRUD 基线 |
| `ReportDynamic` | `/report/dynamic` | `views/report/Dynamic.vue` | CRUD 基线 |
| `ReportDash` | `/report/dashboard` | `views/report/Dashboard.vue` | 独立信息架构 |
| `ReportPresets` | `/report/presets` | `views/report/Presets.vue` | CRUD 基线 |
| `ReportFree` | `/report/free` | `views/report/FreeReport.vue` | CRUD 基线 |
| `ReportFreeDetail` | `/report/free/:batchId` | `views/report/FreeReportDetail.vue` | CRUD 基线 |
| `ReportSql` | `/report/sql` | `views/report/Sql.vue` | CRUD 基线 |
| `ReportAmasApprovals` | `/report/amas-approvals` | `views/report/AmasApprovals.vue` | CRUD 基线 |
| `ReportAmasApprovalDetail` | `/report/amas-approvals/:perfAdjustNo` | `views/report/AmasApprovalDetail.vue` | CRUD 基线 |
| `ScreenAdminDs` | `/screen-admin/datasources` | `views/screen/admin/Datasources.vue` | CRUD 基线和点名修复 |
| `ScreenAdminOrgProfiles` | `/screen-admin/org-profiles` | `views/screen/admin/OrgProfiles.vue` | CRUD 基线和点名修复 |
| `ScreenAdminOrgGroups` | `/screen-admin/org-groups` | `views/screen/admin/OrgGroups.vue` | CRUD 基线和点名修复 |
| `GuaranteeQuery` | `/guarantee/query` | `views/guarantee/Query.vue` | CRUD 基线 |
| `HistoryDataImport` | `/guarantee/data-import` | `views/guarantee/DataImport.vue` | CRUD 基线 |
| `HistoryNotice` | `/guarantee/notice` | `views/guarantee/Notice.vue` | CRUD 基线 |
| `HistoryPriceApproval` | `/history/price-approval` | `views/history/PriceApproval.vue` | CRUD 基线 |
| `HistoryPriceApprovalDetail` | `/history/price-approval/:priceApprId` | `views/history/PriceApprovalDetail.vue` | CRUD 基线 |
| `HistoryPerfAdjust` | `/history/perf-adjust` | `views/history/PerfAdjustQuery.vue` | CRUD 基线 |
| `SysUsers` | `/system/users` | `views/system/Users.vue` | CRUD 基线和点名修复 |
| `SysRoles` | `/system/roles` | `views/system/Roles.vue` | CRUD 基线 |
| `SysResources` | `/system/resources` | `views/system/Resources.vue` | CRUD 基线 |
| `SysPermission` | `/system/permission` | `views/system/Permission.vue` | CRUD 基线 |
| `SysDict` | `/system/dict` | `views/system/Dict.vue` | CRUD 基线 |
| `SysCalendar` | `/system/calendar` | `views/system/Calendar.vue` | CRUD 基线 |
| `SysJobs` | `/system/jobs` | `views/system/Jobs.vue` | CRUD 基线 |
| `SysAudit` | `/system/audit` | `views/system/Audit.vue` | CRUD 基线 |
| `SysNotifications` | `/system/notifications` | `views/system/Notifications.vue` | CRUD 基线 |
| `SysConfig` | `/system/config` | `views/system/Config.vue` | CRUD 基线 |
| `SysFiles` | `/system/files` | `views/system/Files.vue` | CRUD 基线 |
| `SysTimeoutRules` | `/system/timeout-rules` | `views/system/TimeoutRules.vue` | CRUD 基线 |
| `SysAnnouncements` | `/system/announcements` | `views/system/Announcements.vue` | CRUD 基线 |
| `SysAnnouncementDetail` | `/system/announcements/:id` | `views/system/AnnouncementDetail.vue` | CRUD 基线 |
| `SysWorkflowFlows` | `/system/workflow-flows` | `views/system/FlowList.vue` | CRUD 基线 |
| `SysWorkflowFlowEdit` | `/system/workflow-flows/:id` | `views/system/FlowEdit.vue` | CRUD 基线 |
| `SysWorkflowMonitor` | `/system/workflow-monitor` | `views/system/WorkflowMonitor.vue` | CRUD 基线 |
| `SysPersonTags` | `/system/person-tags` | `views/system/PersonTags.vue` | CRUD 基线 |

### 2.1 长文本适用矩阵

“适用”表示页面根节点显式挂载 `v-bp-overflow-tooltip`，因此该页面所有表格单元格会在**真实溢出**时显示完整原生提示；表格内已有 `show-overflow-tooltip` 仍保留。数量是静态模板中的 `el-table` 数，包含弹窗/抽屉内表格。

| 路由名 | 长文本完整值策略 |
|---|---|
| `Login`、`NoAccess`、`Workspace`、`ReportDash` | 不适用：独立信息架构，不接入 CRUD 指令 |
| `AnnouncementDetail`、`InfoNav`、`HistoryPriceApprovalDetail`、`SysCalendar`、`SysAnnouncementDetail`、`SysWorkflowFlowEdit` | 不适用：页面无 `el-table` |
| `AnnouncementList`、`NotificationList`、`InfoAddressBook`、`InfoProducts`、`InfoDocuments`、`PerfTargetValues`、`PerfImport`、`PerfKpiScoreDetail`、`EvalTags`、`ReportDynamic`、`ReportPresets`、`ReportFree`、`ReportFreeDetail`、`ScreenAdminOrgProfiles`、`ScreenAdminOrgGroups`、`GuaranteeQuery`、`HistoryNotice`、`HistoryPriceApproval`、`SysUsers`、`SysResources`、`SysPermission`、`SysDict`、`SysAudit`、`SysNotifications`、`SysFiles`、`SysTimeoutRules`、`SysAnnouncements`、`SysWorkflowFlows` | 适用：各 1 张表 |
| `PerfKpiRules`、`PerfCompute`、`PerfTaskMonitor`、`EvalUserTags`、`EvalMyTasks`、`ReportAmasApprovals`、`ReportAmasApprovalDetail`、`HistoryDataImport`、`SysRoles`、`SysJobs` | 适用：各 2 张表 |
| `PerfTargets`、`EvalRules`、`ReportSql`、`ScreenAdminDs`、`SysConfig`、`SysWorkflowMonitor`、`SysPersonTags` | 适用：各 3 张表 |
| `PerfMetrics`、`HistoryPerfAdjust` | 适用：各 4 张表 |
| `PerfAdjust`、`EvalTasks` | 适用：各 6 张表 |

### 2.2 行级操作自适应矩阵

全量盘点共 **58 个物理操作列**（含 `SchemeListDialog`，也含 3 个 screen-admin CRUD），其中 **30 个多操作列**接入 `BpAdaptiveRowActions`，其余 28 个单操作列保持原行为。自适应只依据当前行实际渲染后的按钮总宽、8px 间距和操作列真实可用宽度，不按文字长度、源码动作总数或视口宽度猜测：空间充足时按原键盘顺序直出全部可见操作，包括危险操作；空间不足或尺寸不可用时 fail-close 为既有主操作直出、其他可见操作进入“更多”。危险操作仍使用 danger 语义，权限/状态条件、disabled/loading、二次确认、原因输入、事件参数和 API/审计链不变。

`—`表示该页面没有行级表格操作，而非遗漏。下表的“紧凑态主操作”始终直出；“自适应次级操作”在宽度足够时全部直出，在宽度不足时全部进入“更多”。

物理列计数矩阵（`页面 × 操作列数`）：`EvalMyTasks×2`、`EvalRules×2`、`EvalTags×1`、`EvalTasks×1`、`EvalUserTags×1`、`GuaranteeDataImport×1`、`GuaranteeNotice×1`、`GuaranteeQuery×1`、`HistoryPriceApproval×1`、`InfoAddressBook×1`、`InfoDocuments×1`、`InfoProducts×1`、`PerfAdjust×5`、`PerfImport×1`、`PerfKpiRules×2`、`PerfMetrics×1`、`PerfTargetValues×1`、`PerfTargets×3`、`PerfTaskMonitor×1`、`ReportAmasApprovals×2`、`ReportFree×1`、`ReportSql×1`、`SchemeListDialog×1`、`ScreenAdminDs×1`、`ScreenAdminOrgProfiles×1`、`SysAnnouncements×1`、`SysAudit×1`、`SysConfig×3`、`SysDict×1`、`SysFiles×1`、`SysWorkflowFlows×1`、`SysJobs×1`、`SysNotifications×1`、`SysPermission×1`、`SysPersonTags×3`、`SysResources×1`、`SysRoles×1`、`SysTimeoutRules×1`、`SysUsers×1`、`SysWorkflowMonitor×1`、`AnnouncementList×1`、`Workspace×3`，合计 58 列。

| 路由名 | 紧凑态主操作 | 自适应次级操作 / 说明 |
|---|---|---|
| `Login` | — | 独立登录信息架构，无表格 |
| `NoAccess` | — | 独立无权限信息架构，无表格 |
| `Workspace` | 办理或详情；认领；查看 | 待认领转交：拒绝；我发起的转交：撤回 |
| `AnnouncementList` | 详情 | — |
| `AnnouncementDetail` | — | 详情页无行级表格操作 |
| `NotificationList` | — | 通知卡片入口，无行级表格操作 |
| `InfoNav` | — | 导航页无行级表格操作 |
| `InfoAddressBook` | 编辑 | — |
| `InfoProducts` | 编辑 | 附件；删除 |
| `InfoDocuments` | 下载 | 编辑；删除 |
| `PerfMetrics` | 查看 | 编辑 |
| `PerfKpiRules` | 查看 | 复制版本；编辑（原资财部/创建人条件不变） |
| `PerfTargets` | 目标值 | 编辑；删除（原创建人条件和确认不变）；审批/已审批表仍各自直出单项 |
| `PerfTargetValues` | 修改 | 调整（原权限条件）；删除 |
| `PerfImport` | 刷新 | 下载文件；下载错误；重试；删除 |
| `PerfAdjust` | 查看 | 编辑（草稿）；撤回（原原因输入不变）；待办和历史表均为单项 |
| `PerfCompute` | — | 无行级表格操作 |
| `PerfTaskMonitor` | 执行 | 历史 |
| `PerfKpiScoreDetail` | — | 无行级表格操作 |
| `EvalTags` | 编辑 | 删除 |
| `EvalUserTags` | 编辑 | — |
| `EvalRules` | 详情 | 编辑；删除；规则组弹窗内删除为单项 |
| `EvalTasks` | 详情 | 发布、关闭、导出、删除（原状态互斥和禁用条件不变） |
| `EvalMyTasks` | 处理；提交 | 各表均为单项 |
| `ReportDynamic` | — | 无行级表格操作 |
| `ReportDash` | — | 独立仪表盘信息架构，无行级表格操作 |
| `ReportPresets` | — | 无行级表格操作 |
| `ReportFree` | 查看 | 下载；启用/禁用（原状态互斥）；删除 |
| `ReportFreeDetail` | — | 详情页无行级表格操作 |
| `ReportSql` | 主表：下载文件；方案对话框：载入 | 方案对话框：编辑；删除 |
| `ReportAmasApprovals` | 详情 | 两张审批表均为单项 |
| `ReportAmasApprovalDetail` | — | 详情页无行级表格操作 |
| `ScreenAdminDs` | 编辑 | 试跑；探测列；新建副本；删除 |
| `ScreenAdminOrgProfiles` | 编辑 | — |
| `ScreenAdminOrgGroups` | — | 无行级表格操作 |
| `GuaranteeQuery` | 编辑 | — |
| `HistoryDataImport` | 查看 | 下载 |
| `HistoryNotice` | 查看 | — |
| `HistoryPriceApproval` | 下载 | 申请资料查看仍为独立信息列 |
| `HistoryPriceApprovalDetail` | — | 详情页无行级表格操作 |
| `HistoryPerfAdjust` | — | 无行级表格操作 |
| `SysUsers` | 编辑 | 分配角色；删除 |
| `SysRoles` | 编辑 | 分配菜单；已绑用户；删除 |
| `SysResources` | 编辑 | 新增子菜单；分配角色（叶子菜单）；删除 |
| `SysPermission` | 修改 | — |
| `SysDict` | 编辑 | 启用或禁用（原状态互斥与确认不变） |
| `SysCalendar` | — | 日历页无行级表格操作 |
| `SysJobs` | 日志 | 暂停或恢复（原状态互斥）；手动触发（原允许条件） |
| `SysAudit` | 详情 | — |
| `SysNotifications` | 详情 | 标记已读（未读时）；跳转（有关联时） |
| `SysConfig` | 编辑 | 三张配置表均为单项 |
| `SysFiles` | 下载 | 删除 |
| `SysTimeoutRules` | 编辑 | — |
| `SysAnnouncements` | 详情 | — |
| `SysAnnouncementDetail` | — | 详情页无行级表格操作 |
| `SysWorkflowFlows` | 编辑 | 发布；克隆；删除（草稿且非只读导入时） |
| `SysWorkflowFlowEdit` | — | 编辑页无行级表格操作 |
| `SysWorkflowMonitor` | 查看 | 转交或指派（原流程状态条件不变） |
| `SysPersonTags` | 标签表：详情；成员表：修改 | 标签表：编辑、删除；员工/机构成员表：删除 |

静态门禁位于 `src/views/__tests__/row-action-audit.spec.js`：它会检查 59 个普通命名路由与 58 个物理操作列的精确矩阵，逐列验证 30 个多操作列同时保留主操作、宽度足够时的全直出分支和 fail-close“更多”分支；`ReportSql` 的 `SchemeListDialog` 也单独纳入。真实 DOM/ResizeObserver 门禁位于 `row-action-responsive.spec.js`，覆盖未知尺寸 fail-close、宽度足够（含危险操作）与缩窄恢复，以及测量副本退出读屏和键盘顺序。

## 3. 已修复的问题类型

| 问题类型 | 统一裁决 | 实现边界 |
|---|---|---|
| 筛选条件松散、查询按钮漂移 | `.filter-form` 为显式弹性栅格；最后一个操作项右对齐 | 仅 `.bp-crud` |
| 控件、表格密度不一致 | 正文/表单/表格 14px；常规控件最小 32px；行高 40px | 仅 `.bp-crud` |
| 状态标签尺度不一 | 状态标签最小高 24px，使用已有语义 tag class | 仅 `.bp-crud` |
| 长字段挤压布局 | 默认单行省略；`v-bp-overflow-tooltip` 仅在真实溢出时提供完整原生提示，已有 `show-overflow-tooltip` 保留 | 仅 `.bp-crud`，不强制截断操作列 |
| 操作按钮换行或超出 | 操作列固定右侧，`.operation-cell` 不换行；空间足够时全部直出，空间不足时保留主操作、其余进入“更多” | 需要页面 API 不变 |
| 数据源页面 | 纳入统一自适应契约；宽度足够时编辑/试跑/探测/副本/删除全部直出；补筛选重置；状态语义化 | `/screen-admin/datasources` |
| 机构经营画像 | 筛选栅格和重置；列表状态、完整值提示和编辑弹窗样式统一 | `/screen-admin/org-profiles` |
| 命名机构组状态越界 | 组名、编码、状态在固定网格内截断；仅 `ACTIVE`/`DISABLED` 显示为启停，未知值显式标为“未知状态” | `/screen-admin/org-groups` |
| 用户管理 | 保留机构树与批量工具栏；筛选保持明确栅格；行内只留编辑，角色/删除进更多 | `/system/users` |
| 评价标签和人员角色 | 两表均为有边框高密度表格；标签名称列固定 220px 且完整值提示；人员角色导入/导出移至页头 | `/eval/tags`、`/eval/user-tags` |

## 4. 后续页面设计规范

### 4.1 使用范围

新建或重构的主平台 CRUD、查询、配置、详情工作面，根节点使用：

```vue
<main v-bp-overflow-tooltip class="bp-crud feature-page" aria-labelledby="feature-page-title">
```

不得将 `.bp-crud` 加到红色引擎、大屏运行态或大屏设计器。独立信息架构页面可遵守 token、焦点和无障碍规则，但不强制采用表格筛选框架。

### 4.2 页面骨架

```vue
<main v-bp-overflow-tooltip class="bp-crud feature-page" aria-labelledby="feature-page-title">
  <header class="page-h">
    <PageTitle id="feature-page-title" />
    <div class="actions action-group" role="group" aria-label="页面操作">
      <!-- 一个主操作；刷新/导出等次级操作在此处 -->
    </div>
  </header>

  <section class="card-section filter-bar" aria-label="筛选条件">
    <el-form class="filter-form" inline>
      <!-- 可见 label 的字段 -->
      <el-form-item>
        <el-button type="primary">查询</el-button>
        <el-button>重置</el-button>
      </el-form-item>
    </el-form>
  </section>

  <section class="card-section data-panel" aria-label="列表">
    <div class="toolbar"><!-- 标题、口径提示、数量/加载状态 --></div>
    <el-table border size="default"><!-- 表头、数据和固定操作列 --></el-table>
    <nav class="pager" aria-label="列表分页"><el-pagination /></nav>
  </section>
</main>
```

### 4.3 规则清单

1. 使用 `--color-*`、`--space-*`、`--radius-control` 和 `--shadow-*`，页面样式不新增原始色值。
2. 筛选字段有可见 label；查询与重置为同一末尾操作项。筛选条件超过一行时，由栅格自然换行，操作仍右对齐。
3. 数据表默认 `border size="default"`。表头和数据行约 40px，正文 14px；数值、时间、业务键优先使用合适固定列宽或等宽数字。
4. 表格页面根节点必须显式使用 `v-bp-overflow-tooltip`，它会对实际溢出的单元格提供完整原生提示；特别复杂的字段可额外使用 `show-overflow-tooltip`。操作列不可截断或换行。
5. 操作列 `fixed="right" class-name="operation-cell"`。多操作列使用 `BpAdaptiveRowActions`：按当前行实际可见操作的真实渲染宽度判断，全部操作及间距可容纳时全部直出（危险操作也直出并保留 danger）；不可容纳或无法可靠测量时只直出最常用主操作，其余进入“更多”。“更多”仅在至少一项次级操作满足当前权限/状态时渲染，不能出现空菜单；不得用文字长度、源码总动作数或视口断点代替测量，也不得改变确认和审计原因。
6. 状态必须有文本，不可仅用颜色。只对已知后端枚举映射“启用/停用”等语义；未知值必须显式显示“未知状态”，不能默认当作成功。
7. 弹窗/抽屉使用 `class="bp-crud-dialog"`，尤其 `append-to-body` 时作为受控提示边界；保留取消出口、加载禁用与原有校验/确认语义。
8. 异步读取必须区分加载、空数据和错误；异步写入保持单飞、禁用重复操作以及明确的错误恢复。多个次级操作共用“更多”时，每一项只继承自身原有的 loading/disabled 条件，不得因另一项 pending 而封锁仍可执行的操作。
9. 提交前新增静态/组件测试；变更后运行受影响测试、普通后台路由审计、构建和真实页面验收。真实浏览器只走查询、打开和取消路径，避免验收产生业务写入。

## 5. 验收与维护

- 静态范围门禁：`npm test -- --run src/views/__tests__/crud-route-audit.spec.js`。
- 共享基线门禁：`npm test -- --run src/styles/__tests__/crud-baseline.spec.js`。
- 页面契约：各业务目录下 `__tests__` 的定向用例。
- 视觉验收：真实登录 `/#/login?normal`，账号 `admin/123456`；在 1920×1080 与 2560×1440 检查筛选栅格、表格列、固定操作列、完整值提示与弹窗。仅执行查询、打开和取消，不执行保存、删除、导入或其他写操作。
