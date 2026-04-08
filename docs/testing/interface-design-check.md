# 三模块接口设计与实现一致性核对报告

> 核对日期：2026-04-08
> 核对方式：逐模块对照设计文档（03-接口设计与报文.md）与实际 Controller/Service 实现

---

## Auth 模块核对结果

| Section | 接口 | 设计文档 | 实际实现 | 状态 |
|:---|:---|:---|:---|:---|
| A.1-3 | 会话管理 | login/logout/current-user | ✅ 完全一致 | OK |
| B.1-5 | 角色管理 | CRUD + role users | ✅ 完全一致 | OK |
| C.1-3 | 用户角色绑定 | CRUD | ✅ 完全一致 | OK |
| D.1-5 | 资源管理 | CRUD + role resources | ✅ 完全一致 | OK |
| E.1-2 | 角色资源绑定 | bind/replace | ✅ 完全一致 | OK |
| F.1/3/4 | BizScope (list/save/delete) | ✅ | ✅ 完全一致 | OK |
| **F.2** | `GET /api/admin/biz-scopes/matrix` | 角色×BizType矩阵视图 | ❌ Controller 端点缺失 | **缺失** |
| G.1/3 | Org (tree/subtree) | ✅ | ✅ 完全一致 | OK |
| **G.2** | `GET /api/orgs/{orgCode}/users` | 机构下用户列表 | ❌ Service 方法 + Controller 端点均缺失 | **缺失** |
| **H.1** | `GET /api/auth/permissions` | 当前用户完整权限集合 | ❌ Controller 端点缺失（Service 方法已实现） | **缺失** |
| **H.2** | `POST /api/auth/check-permission` | 实时权限检查 | ❌ Controller 端点缺失（Service 方法已实现） | **缺失** |

**Auth 总结：3个缺失端点（4个接口），所需 DTO 均已存在。**

---

## Governance 模块核对结果

| Section | 接口 | 问题类型 | 详情 |
|:---|:---|:---|:---|
| A.1-6 | 字典管理 A.1-A.6 | — | ✅ 全部一致 |
| B.1-3 | 日历 B.1-B.3 | — | ✅ 全部一致 |
| B.4 | `POST /api/admin/sys/calendar/import` | **缺失** | Excel 批量导入节假日未实现 |
| C.1-5 | 任务调度 C.1-C.5 | — | ✅ 全部一致 |
| D.1 | 审计日志列表 | — | ✅ 一致 |
| D.2 | 审计日志详情 | — | ✅ 一致 |
| D.3 | `POST /api/admin/sys/audit-logs/export` | **参数接收方式不符** | 设计文档要求 `@RequestBody AuditLogExportReqDTO`，实际用 `@RequestParam` 接收 6 个查询参数 |
| E.1-4 | 通知 E.1-E.4 | — | ✅ 全部一致 |
| E.5 | `GET /api/notifications/{id}` | **缺失** | 通知详情端点未实现 |
| F.1 | 配置列表 | **响应格式不符** | 设计文档：无分页，`keyword`过滤；实际：有分页（pageNo/pageSize），无 keyword |
| F.2 | 配置更新 | — | ✅ 一致 |
| G.1-4 | 文件管理 | **端点URL不符** | 设计文档 `/api/files/upload`，实际无此路径；`/api/files/{fileId}/download` 实际为 `/api/files/{fileId}/download-url`；缺少 `DELETE /api/files/{fileId}` |
| H.1-2 | SQL探针 | — | ✅ 全部一致 |

**Governance 总结：缺失 B.4（calendar import）、E.5（notification detail）；不一致 D.3、F.1、G。**

---

## Workflow 模块核对结果

| Section | 接口 | 问题类型 | 详情 |
|:---|:---|:---|:---|
| A.1 | `GET /api/workflow/tasks/todo` | **URL不符+缺BizAuth** | 设计文档为 `/api/workflow/tasks`，实际多了 `/todo` 后缀；且无 `@BizAuth` |
| A.2 | 已办列表 | **缺BizAuth** | 设计文档无 `@BizAuth`，实际也缺 |
| A.3 | 任务详情 | — | ✅ 一致 |
| B.1-4 | 签收/审批/驳回/转交 | — | ✅ 一致 |
| C.1 | 流程实例详情 | — | ✅ 一致 |
| C.2 | `GET /{processInstanceId}/diagram` | **响应格式不符** | 设计文档返回 JSON，实际返回 `MediaType.IMAGE_PNG_VALUE`（PNG 图片流） |
| C.3 | 审批历史 | — | ✅ 一致 |
| C.4 | 流程映射 | — | ✅ 一致 |
| D.1 | `GET /timeout-rules` | **URL不符+BizAuth不符** | `processDefinitionKey` 为 path 参数（应为 query）；BizAuth 为 CONFIG 应为 READ |
| D.1 item | `GET /timeout-rules/item/{id}` | **多余端点** | 设计文档未定义 |
| D.2 PUT | `PUT /timeout-rules/{id}` | **请求体不符** | 设计文档为 `@RequestBody`，实际为 `@RequestParam` |
| D.2 POST | `POST /timeout-rules` | **多余端点** | 设计文档未定义 |
| D.3 | 候选人配置列表 | **URL不符+BizAuth不符** | 同D.1 |
| D.3 item | GET/PUT item | **多余端点** | 设计文档未定义 |
| D.3 POST | `POST /candidate-configs` | **多余端点** | 设计文档未定义 |
| D.5 | 节点表单列表 | **URL不符+BizAuth不符** | 同D.1 |
| D.5 item | GET/PUT item / POST | **多余端点** | 设计文档未定义 |
| **D.7** | `GET /api/admin/workflow/process-definitions` | **缺失** | 流程定义列表端点完全缺失 |

**Workflow 总结：缺失 D.7；C.2 响应格式不符；大量 URL 参数类型 + BizAuth 不符；多处代码实现了但设计文档未定义的端点。**

---

## 待确认问题（设计 vs 代码差异）

以下问题需与团队确认方向：

1. **Workflow D.1/D.3/D.5**：`processDefinitionKey` 用 path 参数 vs query 参数——哪个是正确意图？
2. **Workflow 多余端点**：代码实现了但设计文档完全没提的 item/POST 端点，是设计漏了还是代码多做了？
3. **Governance F.1**：配置列表的分页是代码额外加的——是否应补入设计文档？
4. **Governance G**：文件管理 URL 不一致——是否应统一路径？

---

## 实现计划

### Phase 1: AUTH 模块（进行中）
- [ ] F.2: GET /api/admin/biz-scopes/matrix
- [ ] G.2: GET /api/orgs/{orgCode}/users
- [ ] H.1: GET /api/auth/permissions
- [ ] H.2: POST /api/auth/check-permission

### Phase 2: Governance 模块
- [ ] B.4: POST /api/admin/sys/calendar/import
- [ ] E.5: GET /api/notifications/{id}
- [ ] D.3: export 改为 @RequestBody
- [ ] F.1: 统一响应格式（需确认是否修改设计文档）
- [ ] G: 文件管理 URL 统一（需确认）

### Phase 3: Workflow 模块
- [ ] D.7: process-definitions 端点
- [ ] C.2: diagram 改为 JSON
- [ ] D.1/D.3/D.5: URL + BizAuth 修正
- [ ] 多余端点确认与处理
