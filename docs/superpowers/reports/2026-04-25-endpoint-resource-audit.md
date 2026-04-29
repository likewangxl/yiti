# REST 端点 vs PT_RESOURCE 对账报告

> 由 `RestEndpointInventoryIT.auditEndpointVsResource_shouldGenerateReport` 自动生成，请勿手工编辑。

> 报告内容随项目 PT_RESOURCE 与 REST 端点同步演进，由 IT 触发刷新。

## 概览

| 指标 | 数量 |
|---|---|
| 代码注册 /api/ 端点（去重后 url+method）| 257 |
| seed-v1.sql PT_RESOURCE 总数（包含 STATUS=1 禁用）| 298 |
| seed-v1.sql PT_RESOURCE 启用数（STATUS=0）| 286 |
| 类型 A 端点未注册到 PT_RESOURCE | 0 |
| 类型 B PT_RESOURCE 孤儿（启用但代码无端点）| 0 |

## §1 类型 A：代码端点未注册到 PT_RESOURCE（共 0 项）

- 无

## §2 类型 B：PT_RESOURCE 孤儿（启用但代码无对应端点，共 0 项）

- 无

## §3 完整代码端点清单（折叠）

<details>
<summary>展开查看 257 条端点</summary>

| URL | METHOD | URL（归一化）| Controller |
|---|---|---|---|
| `/api/admin/biz-scopes` | GET | `/api/admin/biz-scopes` | `BizScopeController` |
| `/api/admin/biz-scopes` | POST | `/api/admin/biz-scopes` | `BizScopeController` |
| `/api/admin/biz-scopes/matrix` | GET | `/api/admin/biz-scopes/matrix` | `BizScopeController` |
| `/api/admin/biz-scopes/{id}` | DELETE | `/api/admin/biz-scopes/{X}` | `BizScopeController` |
| `/api/admin/documents` | POST | `/api/admin/documents` | `AdminDocController` |
| `/api/admin/documents/{id}` | DELETE | `/api/admin/documents/{X}` | `AdminDocController` |
| `/api/admin/documents/{id}` | PUT | `/api/admin/documents/{X}` | `AdminDocController` |
| `/api/admin/nav` | POST | `/api/admin/nav` | `AdminNavController` |
| `/api/admin/nav/sort` | PUT | `/api/admin/nav/sort` | `AdminNavController` |
| `/api/admin/nav/{id}` | DELETE | `/api/admin/nav/{X}` | `AdminNavController` |
| `/api/admin/nav/{id}` | PUT | `/api/admin/nav/{X}` | `AdminNavController` |
| `/api/admin/resources` | POST | `/api/admin/resources` | `ResourceController` |
| `/api/admin/resources/tree` | GET | `/api/admin/resources/tree` | `ResourceController` |
| `/api/admin/resources/{resourceId}` | DELETE | `/api/admin/resources/{X}` | `ResourceController` |
| `/api/admin/resources/{resourceId}` | PUT | `/api/admin/resources/{X}` | `ResourceController` |
| `/api/admin/roles/` | GET | `/api/admin/roles/` | `RoleController` |
| `/api/admin/roles/` | POST | `/api/admin/roles/` | `RoleController` |
| `/api/admin/roles/{roleId}` | DELETE | `/api/admin/roles/{X}` | `RoleController` |
| `/api/admin/roles/{roleId}` | PUT | `/api/admin/roles/{X}` | `RoleController` |
| `/api/admin/roles/{roleId}/resources` | GET | `/api/admin/roles/{X}/resources` | `ResourceController` |
| `/api/admin/roles/{roleId}/resources` | POST | `/api/admin/roles/{X}/resources` | `ResourceController` |
| `/api/admin/roles/{roleId}/resources` | PUT | `/api/admin/roles/{X}/resources` | `ResourceController` |
| `/api/admin/roles/{roleId}/users` | GET | `/api/admin/roles/{X}/users` | `RoleController` |
| `/api/admin/sql-probe/execute` | POST | `/api/admin/sql-probe/execute` | `SqlProbeController` |
| `/api/admin/sql-probe/history` | GET | `/api/admin/sql-probe/history` | `SqlProbeController` |
| `/api/admin/sys/audit-logs` | GET | `/api/admin/sys/audit-logs` | `AuditLogController` |
| `/api/admin/sys/audit-logs/export` | POST | `/api/admin/sys/audit-logs/export` | `AuditLogController` |
| `/api/admin/sys/audit-logs/{id}` | GET | `/api/admin/sys/audit-logs/{X}` | `AuditLogController` |
| `/api/admin/sys/calendar` | GET | `/api/admin/sys/calendar` | `CalendarController` |
| `/api/admin/sys/calendar/import` | POST | `/api/admin/sys/calendar/import` | `CalendarController` |
| `/api/admin/sys/calendar/init` | POST | `/api/admin/sys/calendar/init` | `CalendarController` |
| `/api/admin/sys/calendar/{date}` | PUT | `/api/admin/sys/calendar/{X}` | `CalendarController` |
| `/api/admin/sys/configs` | GET | `/api/admin/sys/configs` | `ConfigController` |
| `/api/admin/sys/configs/{configKey}` | PUT | `/api/admin/sys/configs/{X}` | `ConfigController` |
| `/api/admin/sys/dicts` | POST | `/api/admin/sys/dicts` | `AdminDictController` |
| `/api/admin/sys/dicts/{id}` | DELETE | `/api/admin/sys/dicts/{X}` | `AdminDictController` |
| `/api/admin/sys/dicts/{id}` | PUT | `/api/admin/sys/dicts/{X}` | `AdminDictController` |
| `/api/admin/sys/dicts/{id}/status` | PUT | `/api/admin/sys/dicts/{X}/status` | `AdminDictController` |
| `/api/admin/sys/jobs` | GET | `/api/admin/sys/jobs` | `JobController` |
| `/api/admin/sys/jobs/{jobId}/logs` | GET | `/api/admin/sys/jobs/{X}/logs` | `JobController` |
| `/api/admin/sys/jobs/{jobId}/pause` | PUT | `/api/admin/sys/jobs/{X}/pause` | `JobController` |
| `/api/admin/sys/jobs/{jobId}/resume` | PUT | `/api/admin/sys/jobs/{X}/resume` | `JobController` |
| `/api/admin/sys/jobs/{jobId}/trigger` | POST | `/api/admin/sys/jobs/{X}/trigger` | `JobController` |
| `/api/admin/touch-tasks` | GET | `/api/admin/touch-tasks` | `AdminTouchTaskController` |
| `/api/admin/touch-tasks/batch-assign` | POST | `/api/admin/touch-tasks/batch-assign` | `AdminTouchTaskController` |
| `/api/admin/touch-tasks/export` | GET | `/api/admin/touch-tasks/export` | `AdminTouchTaskController` |
| `/api/admin/users/{userId}/roles` | GET | `/api/admin/users/{X}/roles` | `UserRoleController` |
| `/api/admin/users/{userId}/roles` | POST | `/api/admin/users/{X}/roles` | `UserRoleController` |
| `/api/admin/users/{userId}/roles/{roleId}` | DELETE | `/api/admin/users/{X}/roles/{X}` | `UserRoleController` |
| `/api/admin/workflow/node-candidates` | GET | `/api/admin/workflow/node-candidates` | `WorkflowAdminController` |
| `/api/admin/workflow/node-candidates` | POST | `/api/admin/workflow/node-candidates` | `WorkflowAdminController` |
| `/api/admin/workflow/node-candidates/item/{id}` | GET | `/api/admin/workflow/node-candidates/item/{X}` | `WorkflowAdminController` |
| `/api/admin/workflow/node-candidates/{id}` | PUT | `/api/admin/workflow/node-candidates/{X}` | `WorkflowAdminController` |
| `/api/admin/workflow/node-forms` | GET | `/api/admin/workflow/node-forms` | `WorkflowAdminController` |
| `/api/admin/workflow/node-forms` | POST | `/api/admin/workflow/node-forms` | `WorkflowAdminController` |
| `/api/admin/workflow/node-forms/item/{id}` | GET | `/api/admin/workflow/node-forms/item/{X}` | `WorkflowAdminController` |
| `/api/admin/workflow/node-forms/{id}` | PUT | `/api/admin/workflow/node-forms/{X}` | `WorkflowAdminController` |
| `/api/admin/workflow/process-definitions` | GET | `/api/admin/workflow/process-definitions` | `WorkflowAdminController` |
| `/api/admin/workflow/timeout-rules` | GET | `/api/admin/workflow/timeout-rules` | `WorkflowAdminController` |
| `/api/admin/workflow/timeout-rules` | POST | `/api/admin/workflow/timeout-rules` | `WorkflowAdminController` |
| `/api/admin/workflow/timeout-rules/item/{id}` | GET | `/api/admin/workflow/timeout-rules/item/{X}` | `WorkflowAdminController` |
| `/api/admin/workflow/timeout-rules/{id}` | PUT | `/api/admin/workflow/timeout-rules/{X}` | `WorkflowAdminController` |
| `/api/auth/check-permission` | POST | `/api/auth/check-permission` | `AuthController` |
| `/api/auth/current-user` | GET | `/api/auth/current-user` | `AuthController` |
| `/api/auth/login` | POST | `/api/auth/login` | `AuthController` |
| `/api/auth/logout` | POST | `/api/auth/logout` | `AuthController` |
| `/api/auth/permissions` | GET | `/api/auth/permissions` | `AuthController` |
| `/api/claims` | POST | `/api/claims` | `ClaimController` |
| `/api/claims/mine` | GET | `/api/claims/mine` | `ClaimController` |
| `/api/claims/{id}/cancel` | POST | `/api/claims/{X}/cancel` | `ClaimController` |
| `/api/customer-pool` | GET | `/api/customer-pool` | `CustomerPoolController` |
| `/api/customers` | GET | `/api/customers` | `CustomerController` |
| `/api/customers/export` | GET | `/api/customers/export` | `CustomerExportController` |
| `/api/customers/{custId}/claims/{claimId}/transfer` | POST | `/api/customers/{X}/claims/{X}/transfer` | `CustomerController` |
| `/api/customers/{custId}/delete-apply` | POST | `/api/customers/{X}/delete-apply` | `CustomerController` |
| `/api/customers/{id}` | GET | `/api/customers/{X}` | `CustomerController` |
| `/api/customers/{id}/history` | GET | `/api/customers/{X}/history` | `CustomerHistoryController` |
| `/api/customers/{id}/tags` | POST | `/api/customers/{X}/tags` | `CustomerTagController` |
| `/api/customers/{id}/tags/{tagId}` | DELETE | `/api/customers/{X}/tags/{X}` | `CustomerTagController` |
| `/api/data-task/status` | POST | `/api/data-task/status` | `DataTaskController` |
| `/api/documents` | GET | `/api/documents` | `DocController` |
| `/api/documents/{id}/download` | GET | `/api/documents/{X}/download` | `DocController` |
| `/api/employees` | GET | `/api/employees` | `AddressBookController` |
| `/api/employees/search` | GET | `/api/employees/search` | `AddressBookController` |
| `/api/employees/{empId}` | GET | `/api/employees/{X}` | `AddressBookController` |
| `/api/employees/{empId}` | PUT | `/api/employees/{X}` | `AddressBookController` |
| `/api/files` | GET | `/api/files` | `FileController` |
| `/api/files/upload` | POST | `/api/files/upload` | `FileController` |
| `/api/files/{fileId}` | DELETE | `/api/files/{X}` | `FileController` |
| `/api/files/{fileId}/download` | GET | `/api/files/{X}/download` | `FileController` |
| `/api/leads` | GET | `/api/leads` | `LeadController` |
| `/api/leads` | POST | `/api/leads` | `LeadController` |
| `/api/leads/batches` | GET | `/api/leads/batches` | `LeadImportController` |
| `/api/leads/delete-version` | POST | `/api/leads/delete-version` | `LeadController` |
| `/api/leads/edit-version` | POST | `/api/leads/edit-version` | `LeadController` |
| `/api/leads/import/execute` | POST | `/api/leads/import/execute` | `LeadImportController` |
| `/api/leads/import/preview` | POST | `/api/leads/import/preview` | `LeadImportController` |
| `/api/leads/{id}` | DELETE | `/api/leads/{X}` | `LeadController` |
| `/api/leads/{id}` | GET | `/api/leads/{X}` | `LeadController` |
| `/api/leads/{id}` | PUT | `/api/leads/{X}` | `LeadController` |
| `/api/leads/{id}/submit` | POST | `/api/leads/{X}/submit` | `LeadController` |
| `/api/leads/{id}/versions` | GET | `/api/leads/{X}/versions` | `LeadController` |
| `/api/loans` | GET | `/api/loans` | `LoanController` |
| `/api/loans` | POST | `/api/loans` | `LoanController` |
| `/api/loans/export` | GET | `/api/loans/export` | `LoanController` |
| `/api/loans/{id}` | DELETE | `/api/loans/{X}` | `LoanController` |
| `/api/loans/{id}` | GET | `/api/loans/{X}` | `LoanController` |
| `/api/loans/{id}` | PUT | `/api/loans/{X}` | `LoanController` |
| `/api/loans/{id}/cancel` | POST | `/api/loans/{X}/cancel` | `LoanController` |
| `/api/loans/{id}/node-form/{nodeKey}` | GET | `/api/loans/{X}/node-form/{X}` | `LoanController` |
| `/api/loans/{id}/submit` | POST | `/api/loans/{X}/submit` | `LoanController` |
| `/api/nav` | GET | `/api/nav` | `NavController` |
| `/api/notifications` | GET | `/api/notifications` | `NotificationController` |
| `/api/notifications/read-all` | PUT | `/api/notifications/read-all` | `NotificationController` |
| `/api/notifications/unread-count` | GET | `/api/notifications/unread-count` | `NotificationController` |
| `/api/notifications/{id}` | GET | `/api/notifications/{X}` | `NotificationController` |
| `/api/notifications/{id}/read` | PUT | `/api/notifications/{X}/read` | `NotificationController` |
| `/api/orgs/subtree` | GET | `/api/orgs/subtree` | `OrgController` |
| `/api/orgs/tree` | GET | `/api/orgs/tree` | `OrgController` |
| `/api/orgs/{orgCode}/users` | GET | `/api/orgs/{X}/users` | `OrgController` |
| `/api/perf/alloc-adjust/create` | POST | `/api/perf/alloc-adjust/create` | `AllocAdjustController` |
| `/api/perf/alloc-adjust/list` | GET | `/api/perf/alloc-adjust/list` | `AllocAdjustController` |
| `/api/perf/alloc-adjust/{id}` | GET | `/api/perf/alloc-adjust/{X}` | `AllocAdjustController` |
| `/api/perf/alloc-adjust/{id}/withdraw` | POST | `/api/perf/alloc-adjust/{X}/withdraw` | `AllocAdjustController` |
| `/api/perf/alloc-relations` | GET | `/api/perf/alloc-relations` | `AllocRelationController` |
| `/api/perf/alloc-relations/history` | GET | `/api/perf/alloc-relations/history` | `AllocRelationController` |
| `/api/perf/alloc-relations/summary` | GET | `/api/perf/alloc-relations/summary` | `AllocRelationController` |
| `/api/perf/export/alloc` | POST | `/api/perf/export/alloc` | `PerfExportController` |
| `/api/perf/export/detail` | POST | `/api/perf/export/detail` | `PerfExportController` |
| `/api/perf/export/kpi` | POST | `/api/perf/export/kpi` | `PerfExportController` |
| `/api/perf/export/metric` | POST | `/api/perf/export/metric` | `PerfExportController` |
| `/api/perf/export/task/{taskId}` | GET | `/api/perf/export/task/{X}` | `PerfExportController` |
| `/api/perf/import/batches/{batchId}` | DELETE | `/api/perf/import/batches/{X}` | `PerfImportController` |
| `/api/perf/import/batches/{batchId}` | GET | `/api/perf/import/batches/{X}` | `PerfImportController` |
| `/api/perf/import/batches/{batchId}/errors` | GET | `/api/perf/import/batches/{X}/errors` | `PerfImportController` |
| `/api/perf/import/batches/{batchId}/retry` | POST | `/api/perf/import/batches/{X}/retry` | `PerfImportController` |
| `/api/perf/import/upload` | POST | `/api/perf/import/upload` | `PerfImportController` |
| `/api/perf/kpi-schemes` | GET | `/api/perf/kpi-schemes` | `KpiSchemeController` |
| `/api/perf/kpi-schemes` | POST | `/api/perf/kpi-schemes` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}` | DELETE | `/api/perf/kpi-schemes/{X}` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}` | GET | `/api/perf/kpi-schemes/{X}` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}` | PUT | `/api/perf/kpi-schemes/{X}` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}/items` | POST | `/api/perf/kpi-schemes/{X}/items` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}/items/{itemId}` | DELETE | `/api/perf/kpi-schemes/{X}/items/{X}` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}/items/{itemId}` | PUT | `/api/perf/kpi-schemes/{X}/items/{X}` | `KpiSchemeController` |
| `/api/perf/kpi-schemes/{id}/publish` | POST | `/api/perf/kpi-schemes/{X}/publish` | `KpiSchemeController` |
| `/api/perf/metrics` | GET | `/api/perf/metrics` | `MetricDefController` |
| `/api/perf/metrics` | POST | `/api/perf/metrics` | `MetricDefController` |
| `/api/perf/metrics/val-slots` | GET | `/api/perf/metrics/val-slots` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}` | DELETE | `/api/perf/metrics/{X}` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}` | GET | `/api/perf/metrics/{X}` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}` | PUT | `/api/perf/metrics/{X}` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}/execute` | POST | `/api/perf/metrics/{X}/execute` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}/ref-by` | GET | `/api/perf/metrics/{X}/ref-by` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}/refs` | GET | `/api/perf/metrics/{X}/refs` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}/slot/release` | POST | `/api/perf/metrics/{X}/slot/release` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}/status` | PUT | `/api/perf/metrics/{X}/status` | `MetricDefController` |
| `/api/perf/metrics/{metricCode}/trial-run` | POST | `/api/perf/metrics/{X}/trial-run` | `MetricDefController` |
| `/api/perf/recalc` | POST | `/api/perf/recalc` | `PerfCalcController` |
| `/api/perf/run-tasks` | GET | `/api/perf/run-tasks` | `PerfRunTaskController` |
| `/api/perf/run-tasks/{id}` | GET | `/api/perf/run-tasks/{X}` | `PerfRunTaskController` |
| `/api/perf/sys-control` | GET | `/api/perf/sys-control` | `SysControlController` |
| `/api/perf/sys-control/history` | GET | `/api/perf/sys-control/history` | `SysControlController` |
| `/api/perf/sys-control/init` | POST | `/api/perf/sys-control/init` | `SysControlController` |
| `/api/perf/sys-control/rollback` | POST | `/api/perf/sys-control/rollback` | `SysControlController` |
| `/api/perf/sys-control/switch-version` | POST | `/api/perf/sys-control/switch-version` | `SysControlController` |
| `/api/perf/target-adjust/create` | POST | `/api/perf/target-adjust/create` | `TargetAdjustController` |
| `/api/perf/target-adjust/list` | GET | `/api/perf/target-adjust/list` | `TargetAdjustController` |
| `/api/perf/target-adjust/{id}` | GET | `/api/perf/target-adjust/{X}` | `TargetAdjustController` |
| `/api/perf/target-adjust/{id}/withdraw` | POST | `/api/perf/target-adjust/{X}/withdraw` | `TargetAdjustController` |
| `/api/perf/target-plans` | GET | `/api/perf/target-plans` | `TargetPlanController` |
| `/api/perf/target-plans` | POST | `/api/perf/target-plans` | `TargetPlanController` |
| `/api/perf/target-plans/{id}` | GET | `/api/perf/target-plans/{X}` | `TargetPlanController` |
| `/api/perf/target-plans/{id}` | PUT | `/api/perf/target-plans/{X}` | `TargetPlanController` |
| `/api/perf/target-values` | GET | `/api/perf/target-values` | `TargetValueController` |
| `/api/perf/target-values` | POST | `/api/perf/target-values` | `TargetValueController` |
| `/api/perf/target-values/batch` | POST | `/api/perf/target-values/batch` | `TargetValueController` |
| `/api/portal/shortcuts` | GET | `/api/portal/shortcuts` | `ShortcutController` |
| `/api/portal/shortcuts` | PUT | `/api/portal/shortcuts` | `ShortcutController` |
| `/api/portal/workspace` | GET | `/api/portal/workspace` | `WorkspaceController` |
| `/api/products` | GET | `/api/products` | `ProductController` |
| `/api/products` | POST | `/api/products` | `ProductController` |
| `/api/products/export` | GET | `/api/products/export` | `ProductController` |
| `/api/products/support-available` | GET | `/api/products/support-available` | `ProductController` |
| `/api/products/{id:[A-Za-z0-9_-]{1,64}}` | DELETE | `/api/products/{X}` | `ProductController` |
| `/api/products/{id:[A-Za-z0-9_-]{1,64}}` | GET | `/api/products/{X}` | `ProductController` |
| `/api/products/{id:[A-Za-z0-9_-]{1,64}}` | PUT | `/api/products/{X}` | `ProductController` |
| `/api/reports/customer-pool-summary` | GET | `/api/reports/customer-pool-summary` | `CustPoolSummaryController` |
| `/api/reports/customer-pool-summary/export` | POST | `/api/reports/customer-pool-summary/export` | `CustPoolSummaryController` |
| `/api/reports/dashboard/emp/{empId}` | GET | `/api/reports/dashboard/emp/{X}` | `DashboardController` |
| `/api/reports/dashboard/org/{orgCode}` | GET | `/api/reports/dashboard/org/{X}` | `DashboardController` |
| `/api/reports/dashboard/president` | GET | `/api/reports/dashboard/president` | `DashboardController` |
| `/api/reports/dynamic-query` | POST | `/api/reports/dynamic-query` | `DynamicQueryController` |
| `/api/reports/dynamic-query/export` | POST | `/api/reports/dynamic-query/export` | `DynamicQueryExportController` |
| `/api/reports/export-tasks/{taskId}` | DELETE | `/api/reports/export-tasks/{X}` | `RptExportController` |
| `/api/reports/export-tasks/{taskId}` | GET | `/api/reports/export-tasks/{X}` | `RptExportController` |
| `/api/reports/export-tasks/{taskId}/download` | GET | `/api/reports/export-tasks/{X}/download` | `RptExportController` |
| `/api/reports/perf-summary` | GET | `/api/reports/perf-summary` | `PerfSummaryController` |
| `/api/reports/perf-summary/export` | POST | `/api/reports/perf-summary/export` | `PerfSummaryController` |
| `/api/reports/query-dimensions` | GET | `/api/reports/query-dimensions` | `MetaController` |
| `/api/reports/saved-queries` | GET | `/api/reports/saved-queries` | `SavedQueryController` |
| `/api/reports/saved-queries` | POST | `/api/reports/saved-queries` | `SavedQueryController` |
| `/api/reports/saved-queries/{id}` | DELETE | `/api/reports/saved-queries/{X}` | `SavedQueryController` |
| `/api/reports/saved-queries/{id}` | GET | `/api/reports/saved-queries/{X}` | `SavedQueryController` |
| `/api/reports/saved-queries/{id}` | PUT | `/api/reports/saved-queries/{X}` | `SavedQueryController` |
| `/api/reports/sql-probe/execute` | POST | `/api/reports/sql-probe/execute` | `RptSqlProbeController` |
| `/api/reports/sql-probe/history` | GET | `/api/reports/sql-probe/history` | `RptSqlProbeController` |
| `/api/reports/sql-probe/history/{id}` | GET | `/api/reports/sql-probe/history/{X}` | `RptSqlProbeController` |
| `/api/reports/sql-probe/schema-whitelist` | GET | `/api/reports/sql-probe/schema-whitelist` | `RptSqlProbeController` |
| `/api/reports/touch-task-summary` | GET | `/api/reports/touch-task-summary` | `TouchSummaryController` |
| `/api/reports/touch-task-summary/export` | POST | `/api/reports/touch-task-summary/export` | `TouchSummaryController` |
| `/api/support-dept/requests` | GET | `/api/support-dept/requests` | `SupportDeptController` |
| `/api/support-dept/requests/{id}/complete` | POST | `/api/support-dept/requests/{X}/complete` | `SupportDeptController` |
| `/api/support-dept/requests/{id}/dispatch` | POST | `/api/support-dept/requests/{X}/dispatch` | `SupportDeptController` |
| `/api/support-dept/requests/{id}/transfer` | POST | `/api/support-dept/requests/{X}/transfer` | `SupportDeptController` |
| `/api/support-requests` | GET | `/api/support-requests` | `SupportController` |
| `/api/support-requests` | POST | `/api/support-requests` | `SupportController` |
| `/api/support-requests/available-products` | GET | `/api/support-requests/available-products` | `SupportController` |
| `/api/support-requests/export` | GET | `/api/support-requests/export` | `SupportController` |
| `/api/support-requests/{id}` | DELETE | `/api/support-requests/{X}` | `SupportController` |
| `/api/support-requests/{id}` | GET | `/api/support-requests/{X}` | `SupportController` |
| `/api/support-requests/{id}/cancel` | POST | `/api/support-requests/{X}/cancel` | `SupportController` |
| `/api/support-requests/{id}/submit` | POST | `/api/support-requests/{X}/submit` | `SupportController` |
| `/api/sys/calendar` | GET | `/api/sys/calendar` | `PublicCalendarController` |
| `/api/sys/dicts` | GET | `/api/sys/dicts` | `DictController` |
| `/api/sys/dicts/{dictType}/items` | GET | `/api/sys/dicts/{X}/items` | `DictController` |
| `/api/tags` | GET | `/api/tags` | `TagController` |
| `/api/tags` | POST | `/api/tags` | `TagController` |
| `/api/tags/enabled` | GET | `/api/tags/enabled` | `TagController` |
| `/api/tags/{id}` | PUT | `/api/tags/{X}` | `TagController` |
| `/api/tags/{id}/status` | PUT | `/api/tags/{X}/status` | `TagController` |
| `/api/tags/{tagId}/customers` | GET | `/api/tags/{X}/customers` | `TagCustomerController` |
| `/api/tags/{tagId}/customers/export` | GET | `/api/tags/{X}/customers/export` | `TagCustomerController` |
| `/api/tags/{tagId}/customers/import` | POST | `/api/tags/{X}/customers/import` | `TagCustomerController` |
| `/api/touch-reports` | GET | `/api/touch-reports` | `TouchReportController` |
| `/api/touch-reports/export` | GET | `/api/touch-reports/export` | `TouchReportController` |
| `/api/touch-reports/statistics` | GET | `/api/touch-reports/statistics` | `TouchReportController` |
| `/api/touch-tasks` | GET | `/api/touch-tasks` | `TouchTaskController` |
| `/api/touch-tasks/{id}` | GET | `/api/touch-tasks/{X}` | `TouchTaskController` |
| `/api/touch-tasks/{id}/cancel` | POST | `/api/touch-tasks/{X}/cancel` | `TouchTaskController` |
| `/api/touch-tasks/{id}/logs` | GET | `/api/touch-tasks/{X}/logs` | `TouchTaskController` |
| `/api/touch-tasks/{id}/logs` | POST | `/api/touch-tasks/{X}/logs` | `TouchTaskController` |
| `/api/touch-tasks/{id}/success` | POST | `/api/touch-tasks/{X}/success` | `TouchTaskController` |
| `/api/workflow/process-map` | GET | `/api/workflow/process-map` | `ProcessMapController` |
| `/api/workflow/processes/submit` | POST | `/api/workflow/processes/submit` | `ProcessCommandController` |
| `/api/workflow/processes/{processInstanceId}` | GET | `/api/workflow/processes/{X}` | `ProcessController` |
| `/api/workflow/processes/{processInstanceId}/cancel` | POST | `/api/workflow/processes/{X}/cancel` | `ProcessCommandController` |
| `/api/workflow/processes/{processInstanceId}/diagram` | GET | `/api/workflow/processes/{X}/diagram` | `ProcessController` |
| `/api/workflow/processes/{processInstanceId}/history` | GET | `/api/workflow/processes/{X}/history` | `ProcessController` |
| `/api/workflow/processes/{processInstanceId}/nodes` | GET | `/api/workflow/processes/{X}/nodes` | `ProcessController` |
| `/api/workflow/tasks` | GET | `/api/workflow/tasks` | `TaskController` |
| `/api/workflow/tasks/done` | GET | `/api/workflow/tasks/done` | `TaskController` |
| `/api/workflow/tasks/{taskId}` | GET | `/api/workflow/tasks/{X}` | `TaskController` |
| `/api/workflow/tasks/{taskId}/approve` | POST | `/api/workflow/tasks/{X}/approve` | `TaskController` |
| `/api/workflow/tasks/{taskId}/claim` | POST | `/api/workflow/tasks/{X}/claim` | `TaskController` |
| `/api/workflow/tasks/{taskId}/reject` | POST | `/api/workflow/tasks/{X}/reject` | `TaskController` |
| `/api/workflow/tasks/{taskId}/transfer` | POST | `/api/workflow/tasks/{X}/transfer` | `TaskController` |

</details>

## §4 完整 PT_RESOURCE 清单（折叠）

<details>
<summary>展开查看 298 条资源</summary>

| RESOURCE_ID | URL | METHOD | URL（归一化）| STATUS |
|---|---|---|---|---|
| `A_BZ_DELETE` | `/api/admin/biz-scopes/*` | DELETE | `/api/admin/biz-scopes/{X}` | 0 |
| `A_BZ_LIST` | `/api/admin/biz-scopes` | GET | `/api/admin/biz-scopes` | 0 |
| `A_BZ_MATRIX` | `/api/admin/biz-scopes/matrix` | GET | `/api/admin/biz-scopes/matrix` | 0 |
| `A_BZ_SAVE` | `/api/admin/biz-scopes` | POST | `/api/admin/biz-scopes` | 0 |
| `A_CHECK_PERM` | `/api/auth/check-permission` | POST | `/api/auth/check-permission` | 0 |
| `A_CURR_USER` | `/api/auth/current-user` | GET | `/api/auth/current-user` | 0 |
| `A_LOGIN` | `/api/auth/login` | POST | `/api/auth/login` | 0 |
| `A_LOGOUT` | `/api/auth/logout` | POST | `/api/auth/logout` | 0 |
| `A_ORG_SUBTREE` | `/api/orgs/subtree` | GET | `/api/orgs/subtree` | 0 |
| `A_ORG_TREE` | `/api/orgs/tree` | GET | `/api/orgs/tree` | 0 |
| `A_ORG_USERS` | `/api/orgs/*/users` | GET | `/api/orgs/{X}/users` | 0 |
| `A_PERMS` | `/api/auth/permissions` | GET | `/api/auth/permissions` | 0 |
| `A_RES_CREATE` | `/api/admin/resources` | POST | `/api/admin/resources` | 0 |
| `A_RES_DELETE` | `/api/admin/resources/*` | DELETE | `/api/admin/resources/{X}` | 0 |
| `A_RES_TREE` | `/api/admin/resources/tree` | GET | `/api/admin/resources/tree` | 0 |
| `A_RES_UPDATE` | `/api/admin/resources/*` | PUT | `/api/admin/resources/{X}` | 0 |
| `A_ROLE_CREATE` | `/api/admin/roles/` | POST | `/api/admin/roles/` | 0 |
| `A_ROLE_DELETE` | `/api/admin/roles/*` | DELETE | `/api/admin/roles/{X}` | 0 |
| `A_ROLE_LIST` | `/api/admin/roles/` | GET | `/api/admin/roles/` | 0 |
| `A_ROLE_UPDATE` | `/api/admin/roles/*` | PUT | `/api/admin/roles/{X}` | 0 |
| `A_ROLE_USERS` | `/api/admin/roles/*/users` | GET | `/api/admin/roles/{X}/users` | 0 |
| `A_RR_BIND` | `/api/admin/roles/*/resources` | POST | `/api/admin/roles/{X}/resources` | 0 |
| `A_RR_LIST` | `/api/admin/roles/*/resources` | GET | `/api/admin/roles/{X}/resources` | 0 |
| `A_RR_REPLACE` | `/api/admin/roles/*/resources` | PUT | `/api/admin/roles/{X}/resources` | 0 |
| `A_UR_BIND` | `/api/admin/users/*/roles` | POST | `/api/admin/users/{X}/roles` | 0 |
| `A_UR_DEL` | `/api/admin/users/*/roles/*` | DELETE | `/api/admin/users/{X}/roles/{X}` | 0 |
| `A_UR_LIST` | `/api/admin/users/*/roles` | GET | `/api/admin/users/{X}/roles` | 0 |
| `B_LOAN_CANCEL` | `/api/loans/*/cancel` | POST | `/api/loans/{X}/cancel` | 0 |
| `B_LOAN_CREATE` | `/api/loans` | POST | `/api/loans` | 0 |
| `B_LOAN_DELETE` | `/api/loans/*` | DELETE | `/api/loans/{X}` | 0 |
| `B_LOAN_EXPORT` | `/api/loans/export` | GET | `/api/loans/export` | 0 |
| `B_LOAN_FORM` | `/api/loans/*/node-form/*` | GET | `/api/loans/{X}/node-form/{X}` | 0 |
| `B_LOAN_LIST` | `/api/loans` | GET | `/api/loans` | 0 |
| `B_LOAN_READ` | `/api/loans/*` | GET | `/api/loans/{X}` | 0 |
| `B_LOAN_SUBMIT` | `/api/loans/*/submit` | POST | `/api/loans/{X}/submit` | 0 |
| `B_LOAN_UPDATE` | `/api/loans/*` | PUT | `/api/loans/{X}` | 0 |
| `B_SUPD_DISP` | `/api/support-dept/requests/*/dispatch` | POST | `/api/support-dept/requests/{X}/dispatch` | 0 |
| `B_SUPD_DONE` | `/api/support-dept/requests/*/complete` | POST | `/api/support-dept/requests/{X}/complete` | 0 |
| `B_SUPD_LIST` | `/api/support-dept/requests` | GET | `/api/support-dept/requests` | 0 |
| `B_SUPD_XFER` | `/api/support-dept/requests/*/transfer` | POST | `/api/support-dept/requests/{X}/transfer` | 0 |
| `B_SUP_CANCEL` | `/api/support-requests/*/cancel` | POST | `/api/support-requests/{X}/cancel` | 0 |
| `B_SUP_CREATE` | `/api/support-requests` | POST | `/api/support-requests` | 0 |
| `B_SUP_DELETE` | `/api/support-requests/*` | DELETE | `/api/support-requests/{X}` | 0 |
| `B_SUP_EXPORT` | `/api/support-requests/export` | GET | `/api/support-requests/export` | 0 |
| `B_SUP_LIST` | `/api/support-requests` | GET | `/api/support-requests` | 0 |
| `B_SUP_PROD` | `/api/support-requests/available-products` | GET | `/api/support-requests/available-products` | 0 |
| `B_SUP_READ` | `/api/support-requests/*` | GET | `/api/support-requests/{X}` | 0 |
| `B_SUP_SUBMIT` | `/api/support-requests/*/submit` | POST | `/api/support-requests/{X}/submit` | 0 |
| `C_ADM_TT_ASSIGN` | `/api/admin/touch-tasks/batch-assign` | POST | `/api/admin/touch-tasks/batch-assign` | 0 |
| `C_ADM_TT_EXPORT` | `/api/admin/touch-tasks/export` | GET | `/api/admin/touch-tasks/export` | 0 |
| `C_ADM_TT_LIST` | `/api/admin/touch-tasks` | GET | `/api/admin/touch-tasks` | 0 |
| `C_CLAIM_CANCEL` | `/api/claims/*/cancel` | POST | `/api/claims/{X}/cancel` | 0 |
| `C_CLAIM_CREATE` | `/api/claims` | POST | `/api/claims` | 0 |
| `C_CLAIM_MINE` | `/api/claims/mine` | GET | `/api/claims/mine` | 0 |
| `C_CUST_DEL_APPLY` | `/api/customers/*/delete-apply` | POST | `/api/customers/{X}/delete-apply` | 0 |
| `C_CUST_DETAIL` | `/api/customers/*` | GET | `/api/customers/{X}` | 0 |
| `C_CUST_EXPORT` | `/api/customers/export` | GET | `/api/customers/export` | 0 |
| `C_CUST_HIST_XORG` | `/api/customers/*/history` | GET | `/api/customers/{X}/history` | 0 |
| `C_CUST_LIST` | `/api/customers` | GET | `/api/customers` | 0 |
| `C_CUST_TAG_ADD` | `/api/customers/*/tags` | POST | `/api/customers/{X}/tags` | 0 |
| `C_CUST_TAG_DEL` | `/api/customers/*/tags/*` | DELETE | `/api/customers/{X}/tags/{X}` | 0 |
| `C_CUST_TRANSFER` | `/api/customers/*/claims/*/transfer` | POST | `/api/customers/{X}/claims/{X}/transfer` | 0 |
| `C_LEAD_BATCHES` | `/api/leads/batches` | GET | `/api/leads/batches` | 0 |
| `C_LEAD_CREATE` | `/api/leads` | POST | `/api/leads` | 0 |
| `C_LEAD_DELETE` | `/api/leads/*` | DELETE | `/api/leads/{X}` | 0 |
| `C_LEAD_DEL_VER` | `/api/leads/delete-version` | POST | `/api/leads/delete-version` | 0 |
| `C_LEAD_DETAIL` | `/api/leads/*` | GET | `/api/leads/{X}` | 0 |
| `C_LEAD_EDIT_VER` | `/api/leads/edit-version` | POST | `/api/leads/edit-version` | 0 |
| `C_LEAD_IMP_EXEC` | `/api/leads/import/execute` | POST | `/api/leads/import/execute` | 0 |
| `C_LEAD_IMP_PRE` | `/api/leads/import/preview` | POST | `/api/leads/import/preview` | 0 |
| `C_LEAD_LIST` | `/api/leads` | GET | `/api/leads` | 0 |
| `C_LEAD_SUBMIT` | `/api/leads/*/submit` | POST | `/api/leads/{X}/submit` | 0 |
| `C_LEAD_UPDATE` | `/api/leads/*` | PUT | `/api/leads/{X}` | 0 |
| `C_LEAD_VERSIONS` | `/api/leads/*/versions` | GET | `/api/leads/{X}/versions` | 0 |
| `C_POOL_LIST` | `/api/customer-pool` | GET | `/api/customer-pool` | 0 |
| `C_TAG_CREATE` | `/api/tags` | POST | `/api/tags` | 0 |
| `C_TAG_CUST_EXPORT` | `/api/tags/*/customers/export` | GET | `/api/tags/{X}/customers/export` | 0 |
| `C_TAG_CUST_IMP` | `/api/tags/*/customers/import` | POST | `/api/tags/{X}/customers/import` | 0 |
| `C_TAG_CUST_LIST` | `/api/tags/*/customers` | GET | `/api/tags/{X}/customers` | 0 |
| `C_TAG_ENABLED` | `/api/tags/enabled` | GET | `/api/tags/enabled` | 0 |
| `C_TAG_LIST` | `/api/tags` | GET | `/api/tags` | 0 |
| `C_TAG_STATUS` | `/api/tags/*/status` | PUT | `/api/tags/{X}/status` | 0 |
| `C_TAG_UPDATE` | `/api/tags/*` | PUT | `/api/tags/{X}` | 0 |
| `C_TR_EXPORT` | `/api/touch-reports/export` | GET | `/api/touch-reports/export` | 0 |
| `C_TR_LIST` | `/api/touch-reports` | GET | `/api/touch-reports` | 0 |
| `C_TR_STAT` | `/api/touch-reports/statistics` | GET | `/api/touch-reports/statistics` | 0 |
| `C_TT_CANCEL` | `/api/touch-tasks/*/cancel` | POST | `/api/touch-tasks/{X}/cancel` | 0 |
| `C_TT_DETAIL` | `/api/touch-tasks/*` | GET | `/api/touch-tasks/{X}` | 0 |
| `C_TT_LIST` | `/api/touch-tasks` | GET | `/api/touch-tasks` | 0 |
| `C_TT_LOG_ADD` | `/api/touch-tasks/*/logs` | POST | `/api/touch-tasks/{X}/logs` | 0 |
| `C_TT_LOG_LIST` | `/api/touch-tasks/*/logs` | GET | `/api/touch-tasks/{X}/logs` | 0 |
| `C_TT_SUCCESS` | `/api/touch-tasks/*/success` | POST | `/api/touch-tasks/{X}/success` | 0 |
| `G_AUDIT_DETAIL` | `/api/admin/sys/audit-logs/*` | GET | `/api/admin/sys/audit-logs/{X}` | 0 |
| `G_AUDIT_EXPORT` | `/api/admin/sys/audit-logs/export` | POST | `/api/admin/sys/audit-logs/export` | 0 |
| `G_AUDIT_LIST` | `/api/admin/sys/audit-logs` | GET | `/api/admin/sys/audit-logs` | 0 |
| `G_CAL_GET` | `/api/admin/sys/calendar` | GET | `/api/admin/sys/calendar` | 0 |
| `G_CAL_IMPORT` | `/api/admin/sys/calendar/import` | POST | `/api/admin/sys/calendar/import` | 0 |
| `G_CAL_INIT` | `/api/admin/sys/calendar/init` | POST | `/api/admin/sys/calendar/init` | 0 |
| `G_CAL_PUBLIC` | `/api/sys/calendar` | GET | `/api/sys/calendar` | 0 |
| `G_CAL_SET` | `/api/admin/sys/calendar/*` | PUT | `/api/admin/sys/calendar/{X}` | 0 |
| `G_CFG_LIST` | `/api/admin/sys/configs` | GET | `/api/admin/sys/configs` | 0 |
| `G_CFG_UPDATE` | `/api/admin/sys/configs/*` | PUT | `/api/admin/sys/configs/{X}` | 0 |
| `G_DICT_CREATE` | `/api/admin/sys/dicts` | POST | `/api/admin/sys/dicts` | 0 |
| `G_DICT_DELETE` | `/api/admin/sys/dicts/*` | DELETE | `/api/admin/sys/dicts/{X}` | 0 |
| `G_DICT_ITEMS` | `/api/sys/dicts/*/items` | GET | `/api/sys/dicts/{X}/items` | 0 |
| `G_DICT_LIST` | `/api/sys/dicts` | GET | `/api/sys/dicts` | 0 |
| `G_DICT_STATUS` | `/api/admin/sys/dicts/*/status` | PUT | `/api/admin/sys/dicts/{X}/status` | 0 |
| `G_DICT_UPDATE` | `/api/admin/sys/dicts/*` | PUT | `/api/admin/sys/dicts/{X}` | 0 |
| `G_FILE_DELETE` | `/api/files/*` | DELETE | `/api/files/{X}` | 0 |
| `G_FILE_DOWNLOAD` | `/api/files/*/download` | GET | `/api/files/{X}/download` | 0 |
| `G_FILE_LIST` | `/api/files` | GET | `/api/files` | 0 |
| `G_FILE_UPLOAD` | `/api/files/upload` | POST | `/api/files/upload` | 0 |
| `G_JOB_LIST` | `/api/admin/sys/jobs` | GET | `/api/admin/sys/jobs` | 0 |
| `G_JOB_LOGS` | `/api/admin/sys/jobs/*/logs` | GET | `/api/admin/sys/jobs/{X}/logs` | 0 |
| `G_JOB_PAUSE` | `/api/admin/sys/jobs/*/pause` | PUT | `/api/admin/sys/jobs/{X}/pause` | 0 |
| `G_JOB_RESUME` | `/api/admin/sys/jobs/*/resume` | PUT | `/api/admin/sys/jobs/{X}/resume` | 0 |
| `G_JOB_TRIGGER` | `/api/admin/sys/jobs/*/trigger` | POST | `/api/admin/sys/jobs/{X}/trigger` | 0 |
| `G_NOTIFY_COUNT` | `/api/notifications/unread-count` | GET | `/api/notifications/unread-count` | 0 |
| `G_NOTIFY_DETAIL` | `/api/notifications/*` | GET | `/api/notifications/{X}` | 0 |
| `G_NOTIFY_LIST` | `/api/notifications` | GET | `/api/notifications` | 0 |
| `G_NOTIFY_READ` | `/api/notifications/*/read` | PUT | `/api/notifications/{X}/read` | 0 |
| `G_NOTIFY_READ_ALL` | `/api/notifications/read-all` | PUT | `/api/notifications/read-all` | 0 |
| `G_SQL_EXEC` | `/api/admin/sql-probe/execute` | POST | `/api/admin/sql-probe/execute` | 0 |
| `G_SQL_HIST` | `/api/admin/sql-probe/history` | GET | `/api/admin/sql-probe/history` | 0 |
| `P_PERF_ALLOC_AD_CRE` | `/api/perf/alloc-adjust/create` | POST | `/api/perf/alloc-adjust/create` | 0 |
| `P_PERF_ALLOC_AD_GET` | `/api/perf/alloc-adjust/*` | GET | `/api/perf/alloc-adjust/{X}` | 0 |
| `P_PERF_ALLOC_AD_LST` | `/api/perf/alloc-adjust/list` | GET | `/api/perf/alloc-adjust/list` | 0 |
| `P_PERF_ALLOC_AD_WD` | `/api/perf/alloc-adjust/*/withdraw` | POST | `/api/perf/alloc-adjust/{X}/withdraw` | 0 |
| `P_PERF_ALLOC_CUR` | `/api/perf/alloc-relations` | GET | `/api/perf/alloc-relations` | 0 |
| `P_PERF_ALLOC_HIS` | `/api/perf/alloc-relations/history` | GET | `/api/perf/alloc-relations/history` | 0 |
| `P_PERF_ALLOC_SUM` | `/api/perf/alloc-relations/summary` | GET | `/api/perf/alloc-relations/summary` | 0 |
| `P_PERF_DATA_TASK_ST` | `/api/data-task/status` | POST | `/api/data-task/status` | 0 |
| `P_PERF_EXPT_DTL` | `/api/perf/export/detail` | POST | `/api/perf/export/detail` | 0 |
| `P_PERF_EXPT_MTR` | `/api/perf/export/metric` | POST | `/api/perf/export/metric` | 0 |
| `P_PERF_EXPT_TASK` | `/api/perf/export/task/*` | GET | `/api/perf/export/task/{X}` | 0 |
| `P_PERF_EXP_ALLOC` | `/api/perf/export/alloc` | POST | `/api/perf/export/alloc` | 0 |
| `P_PERF_EXP_KPI` | `/api/perf/export/kpi` | POST | `/api/perf/export/kpi` | 0 |
| `P_PERF_IMP_BTC_DEL` | `/api/perf/import/batches/*` | DELETE | `/api/perf/import/batches/{X}` | 0 |
| `P_PERF_IMP_BTC_ERR` | `/api/perf/import/batches/*/errors` | GET | `/api/perf/import/batches/{X}/errors` | 0 |
| `P_PERF_IMP_BTC_GET` | `/api/perf/import/batches/*` | GET | `/api/perf/import/batches/{X}` | 0 |
| `P_PERF_IMP_BTC_RTY` | `/api/perf/import/batches/*/retry` | POST | `/api/perf/import/batches/{X}/retry` | 0 |
| `P_PERF_IMP_UPLOAD` | `/api/perf/import/upload` | POST | `/api/perf/import/upload` | 0 |
| `P_PERF_KPI_ADD` | `/api/perf/kpi-schemes` | POST | `/api/perf/kpi-schemes` | 0 |
| `P_PERF_KPI_DEL` | `/api/perf/kpi-schemes/*` | DELETE | `/api/perf/kpi-schemes/{X}` | 0 |
| `P_PERF_KPI_GET` | `/api/perf/kpi-schemes/*` | GET | `/api/perf/kpi-schemes/{X}` | 0 |
| `P_PERF_KPI_IADD` | `/api/perf/kpi-schemes/*/items` | POST | `/api/perf/kpi-schemes/{X}/items` | 0 |
| `P_PERF_KPI_IDEL` | `/api/perf/kpi-schemes/*/items/*` | DELETE | `/api/perf/kpi-schemes/{X}/items/{X}` | 0 |
| `P_PERF_KPI_IUPD` | `/api/perf/kpi-schemes/*/items/*` | PUT | `/api/perf/kpi-schemes/{X}/items/{X}` | 0 |
| `P_PERF_KPI_LIST` | `/api/perf/kpi-schemes` | GET | `/api/perf/kpi-schemes` | 0 |
| `P_PERF_KPI_PUB` | `/api/perf/kpi-schemes/*/publish` | POST | `/api/perf/kpi-schemes/{X}/publish` | 0 |
| `P_PERF_KPI_UPD` | `/api/perf/kpi-schemes/*` | PUT | `/api/perf/kpi-schemes/{X}` | 0 |
| `P_PERF_METRIC_ADD` | `/api/perf/metrics` | POST | `/api/perf/metrics` | 0 |
| `P_PERF_METRIC_DEL` | `/api/perf/metrics/*` | DELETE | `/api/perf/metrics/{X}` | 0 |
| `P_PERF_METRIC_GET` | `/api/perf/metrics/*` | GET | `/api/perf/metrics/{X}` | 0 |
| `P_PERF_METRIC_LIST` | `/api/perf/metrics` | GET | `/api/perf/metrics` | 0 |
| `P_PERF_METRIC_RBY` | `/api/perf/metrics/*/ref-by` | GET | `/api/perf/metrics/{X}/ref-by` | 0 |
| `P_PERF_METRIC_REFS` | `/api/perf/metrics/*/refs` | GET | `/api/perf/metrics/{X}/refs` | 0 |
| `P_PERF_METRIC_SLOT` | `/api/perf/metrics/val-slots` | GET | `/api/perf/metrics/val-slots` | 0 |
| `P_PERF_METRIC_SREL` | `/api/perf/metrics/*/slot/release` | POST | `/api/perf/metrics/{X}/slot/release` | 0 |
| `P_PERF_METRIC_STAT` | `/api/perf/metrics/*/status` | PUT | `/api/perf/metrics/{X}/status` | 0 |
| `P_PERF_METRIC_UPD` | `/api/perf/metrics/*` | PUT | `/api/perf/metrics/{X}` | 0 |
| `P_PERF_MTR_EXEC` | `/api/perf/metrics/*/execute` | POST | `/api/perf/metrics/{X}/execute` | 0 |
| `P_PERF_MTR_TRIAL` | `/api/perf/metrics/*/trial-run` | POST | `/api/perf/metrics/{X}/trial-run` | 0 |
| `P_PERF_RECALC` | `/api/perf/recalc` | POST | `/api/perf/recalc` | 0 |
| `P_PERF_RT_GET` | `/api/perf/run-tasks/*` | GET | `/api/perf/run-tasks/{X}` | 0 |
| `P_PERF_RT_LIST` | `/api/perf/run-tasks` | GET | `/api/perf/run-tasks` | 0 |
| `P_PERF_SC_GET` | `/api/perf/sys-control` | GET | `/api/perf/sys-control` | 0 |
| `P_PERF_SC_HIS` | `/api/perf/sys-control/history` | GET | `/api/perf/sys-control/history` | 0 |
| `P_PERF_SC_INIT` | `/api/perf/sys-control/init` | POST | `/api/perf/sys-control/init` | 0 |
| `P_PERF_SC_SW` | `/api/perf/sys-control/switch-version` | POST | `/api/perf/sys-control/switch-version` | 0 |
| `P_PERF_SYS_RB` | `/api/perf/sys-control/rollback` | POST | `/api/perf/sys-control/rollback` | 0 |
| `P_PERF_TGT_AD_CRE` | `/api/perf/target-adjust/create` | POST | `/api/perf/target-adjust/create` | 0 |
| `P_PERF_TGT_AD_GET` | `/api/perf/target-adjust/*` | GET | `/api/perf/target-adjust/{X}` | 0 |
| `P_PERF_TGT_AD_LST` | `/api/perf/target-adjust/list` | GET | `/api/perf/target-adjust/list` | 0 |
| `P_PERF_TGT_AD_WD` | `/api/perf/target-adjust/*/withdraw` | POST | `/api/perf/target-adjust/{X}/withdraw` | 0 |
| `P_PERF_TGT_P_ADD` | `/api/perf/target-plans` | POST | `/api/perf/target-plans` | 0 |
| `P_PERF_TGT_P_GET` | `/api/perf/target-plans/*` | GET | `/api/perf/target-plans/{X}` | 0 |
| `P_PERF_TGT_P_LIST` | `/api/perf/target-plans` | GET | `/api/perf/target-plans` | 0 |
| `P_PERF_TGT_P_UPD` | `/api/perf/target-plans/*` | PUT | `/api/perf/target-plans/{X}` | 0 |
| `P_PERF_TGT_V_ADD` | `/api/perf/target-values` | POST | `/api/perf/target-values` | 0 |
| `P_PERF_TGT_V_BAT` | `/api/perf/target-values/batch` | POST | `/api/perf/target-values/batch` | 0 |
| `P_PERF_TGT_V_LIST` | `/api/perf/target-values` | GET | `/api/perf/target-values` | 0 |
| `RES_CUSTOMER_EDIT` | `/api/customers/*/edit` | POST | `/api/customers/{X}/edit` | 1 |
| `RES_CUST_CLAIM_CANCE` | `/api/claims/*/cancel` | POST | `/api/claims/{X}/cancel` | 0 |
| `RES_CUST_CLAIM_LST` | `/api/my-claims` | GET | `/api/my-claims` | 1 |
| `RES_CUST_CLAIM_RETOU` | `/api/claims/*/re-touch` | POST | `/api/claims/{X}/re-touch` | 1 |
| `RES_CUST_CUST_DELETE` | `/api/customers/*/delete-apply` | POST | `/api/customers/{X}/delete-apply` | 0 |
| `RES_CUST_CUST_DETAIL` | `/api/customers/*` | GET | `/api/customers/{X}` | 0 |
| `RES_CUST_CUST_EXPORT` | `/api/customers/export` | GET | `/api/customers/export` | 0 |
| `RES_CUST_CUST_HIST` | `/api/customers/*/history` | GET | `/api/customers/{X}/history` | 0 |
| `RES_CUST_CUST_TRANS` | `/api/customers/*/transfer` | POST | `/api/customers/{X}/transfer` | 1 |
| `RES_CUST_CUST_UPDATE` | `/api/customers/*` | PUT | `/api/customers/{X}` | 1 |
| `RES_CUST_LD_IMP_BATC` | `/api/leads/import/batches` | GET | `/api/leads/import/batches` | 1 |
| `RES_CUST_LD_IMP_BTCH` | `/api/leads/import/batches/*` | GET | `/api/leads/import/batches/{X}` | 1 |
| `RES_CUST_LD_IMP_PRE` | `/api/leads/import/preview` | POST | `/api/leads/import/preview` | 0 |
| `RES_CUST_LEAD_DELETE` | `/api/leads/*` | DELETE | `/api/leads/{X}` | 0 |
| `RES_CUST_LEAD_DETAIL` | `/api/leads/*` | GET | `/api/leads/{X}` | 0 |
| `RES_CUST_LEAD_EDIT` | `/api/leads/*/edit` | POST | `/api/leads/{X}/edit` | 1 |
| `RES_CUST_LEAD_SUBMIT` | `/api/leads/*/submit` | POST | `/api/leads/{X}/submit` | 0 |
| `RES_CUST_LEAD_UPDATE` | `/api/leads/*` | PUT | `/api/leads/{X}` | 0 |
| `RES_CUST_POOL_CLAIM` | `/api/customer-pool/*/claim` | POST | `/api/customer-pool/{X}/claim` | 1 |
| `RES_CUST_POOL_LIST` | `/api/customer-pool` | GET | `/api/customer-pool` | 0 |
| `RES_CUST_TAG_ENA` | `/api/tags/enabled` | GET | `/api/tags/enabled` | 0 |
| `RES_CUST_TAG_EXPORT` | `/api/tags/*/customers/export` | GET | `/api/tags/{X}/customers/export` | 0 |
| `RES_CUST_TAG_IMPORT` | `/api/tags/*/customers/import` | POST | `/api/tags/{X}/customers/import` | 0 |
| `RES_CUST_TAG_READ` | `/api/tags/*` | GET | `/api/tags/{X}` | 1 |
| `RES_CUST_TAG_STATUS` | `/api/tags/*/status` | PUT | `/api/tags/{X}/status` | 0 |
| `RES_CUST_TAG_UPDATE` | `/api/tags/*` | PUT | `/api/tags/{X}` | 0 |
| `RES_CUST_TRPT_EXP` | `/api/touch-reports/export` | GET | `/api/touch-reports/export` | 0 |
| `RES_CUST_TRPT_LST` | `/api/touch-reports` | GET | `/api/touch-reports` | 0 |
| `RES_CUST_TRPT_SUM` | `/api/touch-reports/summary` | GET | `/api/touch-reports/summary` | 1 |
| `RES_CUST_TSK_CANCEL` | `/api/touch-tasks/*/cancel` | POST | `/api/touch-tasks/{X}/cancel` | 0 |
| `RES_CUST_TSK_LOGS` | `/api/touch-tasks/*/logs` | POST | `/api/touch-tasks/{X}/logs` | 0 |
| `RES_CUST_TSK_READ` | `/api/touch-tasks/*` | GET | `/api/touch-tasks/{X}` | 0 |
| `RES_CUST_TSK_SUCCESS` | `/api/touch-tasks/*/success` | POST | `/api/touch-tasks/{X}/success` | 0 |
| `RES_PORTAL_WORKSPACE` | `/api/portal/workspace` | GET | `/api/portal/workspace` | 0 |
| `RES_PRODUCT_CREATE` | `/api/products` | POST | `/api/products` | 0 |
| `RES_PRODUCT_DELETE` | `/api/products/*` | DELETE | `/api/products/{X}` | 0 |
| `RES_PRODUCT_DETAIL` | `/api/products/*` | GET | `/api/products/{X}` | 0 |
| `RES_PRODUCT_LIST` | `/api/products` | GET | `/api/products` | 0 |
| `RES_PRODUCT_UPDATE` | `/api/products/*` | PUT | `/api/products/{X}` | 0 |
| `RES_PROD_SUP_AVL` | `/api/products/support-available` | GET | `/api/products/support-available` | 0 |
| `RES_PTL_ADDR_LIST` | `/api/employees` | GET | `/api/employees` | 0 |
| `RES_PTL_ADDR_READ` | `/api/employees/*` | GET | `/api/employees/{X}` | 0 |
| `RES_PTL_ADDR_SRCH` | `/api/employees/search` | GET | `/api/employees/search` | 0 |
| `RES_PTL_ADDR_UPDATE` | `/api/employees/*` | PUT | `/api/employees/{X}` | 0 |
| `RES_PTL_DOC_CREATE` | `/api/admin/documents` | POST | `/api/admin/documents` | 0 |
| `RES_PTL_DOC_DEL` | `/api/admin/documents/*` | DELETE | `/api/admin/documents/{X}` | 0 |
| `RES_PTL_DOC_DOWNLOAD` | `/api/documents/*/download` | GET | `/api/documents/{X}/download` | 0 |
| `RES_PTL_DOC_LIST` | `/api/documents` | GET | `/api/documents` | 0 |
| `RES_PTL_DOC_UPDATE` | `/api/admin/documents/*` | PUT | `/api/admin/documents/{X}` | 0 |
| `RES_PTL_NAV_CREATE` | `/api/admin/nav` | POST | `/api/admin/nav` | 0 |
| `RES_PTL_NAV_DELETE` | `/api/admin/nav/*` | DELETE | `/api/admin/nav/{X}` | 0 |
| `RES_PTL_NAV_LIST` | `/api/nav` | GET | `/api/nav` | 0 |
| `RES_PTL_NAV_SORT` | `/api/admin/nav/sort` | PUT | `/api/admin/nav/sort` | 0 |
| `RES_PTL_NAV_UPDATE` | `/api/admin/nav/*` | PUT | `/api/admin/nav/{X}` | 0 |
| `RES_PTL_PRD_EXPRT` | `/api/products/export` | GET | `/api/products/export` | 0 |
| `RES_SHORTCUT_LIST` | `/api/portal/shortcuts` | GET | `/api/portal/shortcuts` | 0 |
| `RES_SHORTCUT_PUT` | `/api/portal/shortcuts` | PUT | `/api/portal/shortcuts` | 0 |
| `RES_WF_APPROVE` | `/api/workflow/tasks/*/approve` | POST | `/api/workflow/tasks/{X}/approve` | 0 |
| `RES_WF_CANCEL` | `/api/workflow/processes/*/cancel` | POST | `/api/workflow/processes/{X}/cancel` | 0 |
| `RES_WF_CLAIM` | `/api/workflow/tasks/*/claim` | POST | `/api/workflow/tasks/{X}/claim` | 0 |
| `RES_WF_DETAIL` | `/api/workflow/tasks/*` | GET | `/api/workflow/tasks/{X}` | 0 |
| `RES_WF_DONE` | `/api/workflow/tasks/done` | GET | `/api/workflow/tasks/done` | 0 |
| `RES_WF_REJECT` | `/api/workflow/tasks/*/reject` | POST | `/api/workflow/tasks/{X}/reject` | 0 |
| `RES_WF_SUBMIT` | `/api/workflow/processes/submit` | POST | `/api/workflow/processes/submit` | 0 |
| `RES_WF_TODO` | `/api/workflow/tasks` | GET | `/api/workflow/tasks` | 0 |
| `RES_WF_TRANSFER` | `/api/workflow/tasks/*/transfer` | POST | `/api/workflow/tasks/{X}/transfer` | 0 |
| `R_RPT_DASH_EMP` | `/api/reports/dashboard/emp/*` | GET | `/api/reports/dashboard/emp/{X}` | 0 |
| `R_RPT_DASH_ORG` | `/api/reports/dashboard/org/*` | GET | `/api/reports/dashboard/org/{X}` | 0 |
| `R_RPT_DASH_PRES` | `/api/reports/dashboard/president` | GET | `/api/reports/dashboard/president` | 0 |
| `R_RPT_DQ_EXEC` | `/api/reports/dynamic-query` | POST | `/api/reports/dynamic-query` | 0 |
| `R_RPT_DQ_EXPORT` | `/api/reports/dynamic-query/export` | POST | `/api/reports/dynamic-query/export` | 0 |
| `R_RPT_EXP_CANCEL` | `/api/reports/export-tasks/*` | DELETE | `/api/reports/export-tasks/{X}` | 0 |
| `R_RPT_EXP_DOWNLOAD` | `/api/reports/export-tasks/*/download` | GET | `/api/reports/export-tasks/{X}/download` | 0 |
| `R_RPT_EXP_STATUS` | `/api/reports/export-tasks/*` | GET | `/api/reports/export-tasks/{X}` | 0 |
| `R_RPT_META_QD` | `/api/reports/query-dimensions` | GET | `/api/reports/query-dimensions` | 0 |
| `R_RPT_SQL_EXEC` | `/api/reports/sql-probe/execute` | POST | `/api/reports/sql-probe/execute` | 0 |
| `R_RPT_SQL_EXP` | `/api/reports/sql-probe/export` | POST | `/api/reports/sql-probe/export` | 1 |
| `R_RPT_SQL_HIST` | `/api/reports/sql-probe/history` | GET | `/api/reports/sql-probe/history` | 0 |
| `R_RPT_SQL_HIST_DTL` | `/api/reports/sql-probe/history/*` | GET | `/api/reports/sql-probe/history/{X}` | 0 |
| `R_RPT_SQL_WL` | `/api/reports/sql-probe/schema-whitelist` | GET | `/api/reports/sql-probe/schema-whitelist` | 0 |
| `R_RPT_SQ_DEL` | `/api/reports/saved-queries/*` | DELETE | `/api/reports/saved-queries/{X}` | 0 |
| `R_RPT_SQ_GET` | `/api/reports/saved-queries/*` | GET | `/api/reports/saved-queries/{X}` | 0 |
| `R_RPT_SQ_LIST` | `/api/reports/saved-queries` | GET | `/api/reports/saved-queries` | 0 |
| `R_RPT_SQ_SAVE` | `/api/reports/saved-queries` | POST | `/api/reports/saved-queries` | 0 |
| `R_RPT_SQ_UPD` | `/api/reports/saved-queries/*` | PUT | `/api/reports/saved-queries/{X}` | 0 |
| `R_RPT_SUM_CUST_EXP` | `/api/reports/customer-pool-summary/export` | POST | `/api/reports/customer-pool-summary/export` | 0 |
| `R_RPT_SUM_CUST_VW` | `/api/reports/customer-pool-summary` | GET | `/api/reports/customer-pool-summary` | 0 |
| `R_RPT_SUM_PERF_EXP` | `/api/reports/perf-summary/export` | POST | `/api/reports/perf-summary/export` | 0 |
| `R_RPT_SUM_PERF_VW` | `/api/reports/perf-summary` | GET | `/api/reports/perf-summary` | 0 |
| `R_RPT_SUM_TOUCH_EXP` | `/api/reports/touch-task-summary/export` | POST | `/api/reports/touch-task-summary/export` | 0 |
| `R_RPT_SUM_TOUCH_VW` | `/api/reports/touch-task-summary` | GET | `/api/reports/touch-task-summary` | 0 |
| `W_NC_CREATE` | `/api/admin/workflow/node-candidates` | POST | `/api/admin/workflow/node-candidates` | 0 |
| `W_NC_GET` | `/api/admin/workflow/node-candidates/item/*` | GET | `/api/admin/workflow/node-candidates/item/{X}` | 0 |
| `W_NC_LIST` | `/api/admin/workflow/node-candidates` | GET | `/api/admin/workflow/node-candidates` | 0 |
| `W_NC_UPDATE` | `/api/admin/workflow/node-candidates/*` | PUT | `/api/admin/workflow/node-candidates/{X}` | 0 |
| `W_NF_CREATE` | `/api/admin/workflow/node-forms` | POST | `/api/admin/workflow/node-forms` | 0 |
| `W_NF_GET` | `/api/admin/workflow/node-forms/item/*` | GET | `/api/admin/workflow/node-forms/item/{X}` | 0 |
| `W_NF_LIST` | `/api/admin/workflow/node-forms` | GET | `/api/admin/workflow/node-forms` | 0 |
| `W_NF_UPDATE` | `/api/admin/workflow/node-forms/*` | PUT | `/api/admin/workflow/node-forms/{X}` | 0 |
| `W_PROC_DEFS` | `/api/admin/workflow/process-definitions` | GET | `/api/admin/workflow/process-definitions` | 0 |
| `W_PROC_DETAIL` | `/api/workflow/processes/*` | GET | `/api/workflow/processes/{X}` | 0 |
| `W_PROC_DIAGRAM` | `/api/workflow/processes/*/diagram` | GET | `/api/workflow/processes/{X}/diagram` | 0 |
| `W_PROC_HISTORY` | `/api/workflow/processes/*/history` | GET | `/api/workflow/processes/{X}/history` | 0 |
| `W_PROC_MAP` | `/api/workflow/process-map` | GET | `/api/workflow/process-map` | 0 |
| `W_PROC_NODES` | `/api/workflow/processes/*/nodes` | GET | `/api/workflow/processes/{X}/nodes` | 0 |
| `W_TASK_APPROVE` | `/api/workflow/tasks/*/approve` | POST | `/api/workflow/tasks/{X}/approve` | 0 |
| `W_TASK_CLAIM` | `/api/workflow/tasks/*/claim` | POST | `/api/workflow/tasks/{X}/claim` | 0 |
| `W_TASK_DETAIL` | `/api/workflow/tasks/*` | GET | `/api/workflow/tasks/{X}` | 0 |
| `W_TASK_DONE` | `/api/workflow/tasks/done` | GET | `/api/workflow/tasks/done` | 0 |
| `W_TASK_REJECT` | `/api/workflow/tasks/*/reject` | POST | `/api/workflow/tasks/{X}/reject` | 0 |
| `W_TASK_TODO` | `/api/workflow/tasks` | GET | `/api/workflow/tasks` | 0 |
| `W_TASK_TRANSFER` | `/api/workflow/tasks/*/transfer` | POST | `/api/workflow/tasks/{X}/transfer` | 0 |
| `W_TR_CREATE` | `/api/admin/workflow/timeout-rules` | POST | `/api/admin/workflow/timeout-rules` | 0 |
| `W_TR_GET` | `/api/admin/workflow/timeout-rules/item/*` | GET | `/api/admin/workflow/timeout-rules/item/{X}` | 0 |
| `W_TR_LIST` | `/api/admin/workflow/timeout-rules` | GET | `/api/admin/workflow/timeout-rules` | 0 |
| `W_TR_UPDATE` | `/api/admin/workflow/timeout-rules/*` | PUT | `/api/admin/workflow/timeout-rules/{X}` | 0 |

</details>
