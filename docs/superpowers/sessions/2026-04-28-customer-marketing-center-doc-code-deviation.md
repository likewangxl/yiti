# customer-marketing-center 代码 vs 文档偏离度分析

> 检查日期：2026-04-28
> 分支：master（HEAD `6a8f186 fix(customer-v1): touch_task.sla_warning 列补齐 + DDL 文档全量字段对齐`）
> 检查范围：`customer-marketing-center/` 模块代码 ↔ `docs/modules/customer-marketing-center/` 9 份设计文档 + `docs/schema/ddl-customer.sql`

---

## 0. 总体结论

整体高度一致（约 **85%**）。模块自带的 `customer-marketing-center/CLAUDE.md` 已经把绝大多数偏离点显式记录为 V1.0 技术债。真正的偏离都集中在 `docs/modules/customer-marketing-center/02·03·04·09` 这几份"原始设计文档（v1.0 / 2026-04-10 冻结稿）"——代码相对于这些原始文档已经走在前面。

代码侧实测：
- Controller 14 个、API 5 个、Mapper 9 个、Entity 8 个、Service 16 个、Listener 5、Event 8、Enum 10
- 测试用例 321 个、0 失败

---

## 1. REST 端点（03-接口设计与报文.md）

文档列出 32 个端点（含 J 节权限矩阵），代码实现 33 个。

### 1.1 已实现但路径/形参不一致

| 设计文档 | 实际代码 | 偏离 |
|---|---|---|
| `POST /api/leads/import`（C.2） | `POST /api/leads/import/execute` | 路径形态不同 |
| `GET /api/leads/import/batches`（C.3） | `GET /api/leads/batches` | 路径前缀不同 |
| `POST /api/leads/{id}/edit`（B.7 编辑版本） | `POST /api/leads/edit-version`（无 `{id}`，sourceLeadId 在 body） | 形参方式不同 |
| `POST /api/customers/{id}/transfer`（D.4） | `POST /api/customers/{custId}/claims/{claimId}/transfer` | 多两段路径 |
| `POST /api/customer-pool/{custId}/claim`（E.2） | `POST /api/claims`（custId 在 body） | 资源归属重组 |
| `GET /api/my-claims`（F.1） | `GET /api/claims/mine` | 命名风格不同 |

### 1.2 文档要求但代码未实现

- `POST /api/claims/{claimId}/re-touch`（F.3 重新发起触达）— **完全缺失**
- `GET /api/admin/touch-tasks/summary`（H.1 机构汇总）— 缺失（汇总能力放到 `TouchTaskQueryApi.getOrgTouchSummary`，但没有 admin REST 端点）
- `GET /api/leads/import/batches/{batchId}`（C.4 批次详情独立接口）— 缺失

### 1.3 代码超出文档的端点

- `GET /api/customers/{id}/history` — 跨机构历史（D.3 文档存在）
- `POST /api/customers/{id}/tags`、`DELETE /api/customers/{id}/tags/{tagId}` — 客户追加/取消打标
- `GET /api/customers/export`、`GET /api/tags/{tagId}/customers/export`、`GET /api/touch-reports/export` — 三个导出（K.7 列出列定义）
- `GET /api/touch-reports`、`GET /api/touch-reports/statistics` — 报表两个查询端点
- `GET /api/admin/touch-tasks`、`GET /api/admin/touch-tasks/export`、`POST /api/admin/touch-tasks/batch-assign` — admin 控制器三件套（仅 export 在 03 文档 H.3 提及）
- `GET /api/touch-tasks/{id}/logs` — 日志只读列表（文档仅有 G.5 写入端点）
- `POST /api/leads/delete-version` — 删除版本提交
- `GET /api/leads/{id}/versions` — 版本链查询

### 1.4 BizAuth 高危标记与文档不一致

03 文档 J 节标注 IMPORT/EXPORT/TRANSFER/DELETE/batch-assign 为 `highRisk=true`，**但代码全部 `@BizAuth` 注解都没有传 `highRisk` 参数**（grep 验证）。这是显著的实现偏离。

---

## 2. 对外 API 契约（04-对外API契约.md）

| Api | 文档方法数 | 代码方法数 | 一致性 |
|---|---|---|---|
| TagApi | 7 | 7 | ✅ 完全一致 |
| LeadApi | 5 | 5 | ✅ 完全一致 |
| CustomerQueryApi | 9 | 9 | ✅ 完全一致 |
| ClaimApi | 6 | 6 | ✅ 完全一致 |
| TouchTaskQueryApi | 8 | 8 | ✅ 完全一致 |

但 `02-后端架构.md §3` 列出的包结构里写了 **`CustomerApi` / `TouchTaskApi`（写入 API，预留）**，代码未实现这两个 Api（实际写操作都通过 Controller 暴露）。**契约文档 04 没有这两个**，所以契约是对齐的，只是 02 的"预留"未落地。

---

## 3. 数据库 DDL（05-表结构DDL.md vs ddl-customer.sql vs entity）

**8 张表全部一致**：表名、字段名、UK、索引完全对齐，且 2026-04-27 刚做过 `touch_task.sla_warning` 列补齐 + 全量字段对齐（commit `6a8f186`）。

唯一一处**枚举值偏离**：

- 文档 §5.3 `cust_lead.lead_status` 取值：`DRAFT / SUBMITTED / IN_APPROVAL / APPROVED / REJECTED`
- 代码 `LeadStatus.java` + CLAUDE.md：`DRAFT / PENDING / APPROVED / REJECTED`（缺 `SUBMITTED` 和 `IN_APPROVAL`，合并为 `PENDING`）

`TouchTaskStatus` 也存在类似偏离：
- DDL 注释只写 `PENDING / SUCCESS / CANCELLED`
- 代码与 01 文档 §7.3bis（功能规格补的状态机）：`PENDING / IN_PROGRESS / SUCCESS / CANCELLED`
- DDL 文档 §5.8 状态机也是 3 态，未含 `IN_PROGRESS`

→ DDL 文档 §5.3、§5.8 的状态枚举落后于 01 文档与代码。

---

## 4. 错误码（02-后端架构.md §4.2 vs CustomerErrorCode.java）

| 维度 | 文档 | 代码 |
|---|---|---|
| 错误码总数 | **35 条** | **24 条** |
| 4xx 业务（CUST-400xx） | 9 | 10（多了 `CUST-40010 触达非法状态转移`，编号空间不同：文档 01-08，代码 01-10） |
| 4xx 资源不存在（CUST-404xx） | 8 | 6 |
| 4xx 冲突（CUST-409xx） | 11 | 6 |
| 4xx 转交/越权（CUST-403xx） | **7** | **0** ← 全部缺失 |
| 4xx 校验（CUST-422xx） | **8** | **0** ← 全部缺失 |
| 5xx | 3 | 2（缺 MinIO 上传） |
| 含义重叠/编号冲突 | — | `CUST-40904`：文档=客户有在途流程不允许删除；代码=客户已被认领（重复认领） |

**这是当前最大的实质性偏离点。** CLAUDE.md V1.0 技术债 #1 已记录"错误码 26 vs 设计 35，缺 403 系列 7 条 + 422 系列 8 条 + 部分 500"。

---

## 5. 包结构（02-后端架构.md §3）

| 02 文档要求 | 代码现状 | 偏离 |
|---|---|---|
| `convert/` 目录（MapStruct） | 实际是 `api/converter/` | 路径与命名不同 |
| `util/` 目录（4 个工具类） | 不存在（项目内未建） | 缺失 |
| `service/ParallelFlowChecker` | 不存在（合并到 `CustomerQueryApiImpl.listRunningProcesses`） | 缺失 |
| `service/CustomerHistoryService` | 不存在（合并到 `CustomerHistoryController` + `CustomerService`） | 缺失 |
| `service/TagImportService` | 不存在（标签客户覆盖式导入直接放在 `TagCustomerService`） | 命名不同 |
| `config/CustomerModuleConfig` + `ExcelTemplateConfig` + `CustomerEventPublisher` | 实际只有 `CustomerCacheConfig` + `CustomerSchedulingConfig` | 多/少 |
| `service/`（文档计划 14 个）| 代码 16 个（多 `LeadCallbackReconcileService` / `LeadCallbackCompensationService` / `TouchTaskStateMachineService` / `CustMasterAssemblerService`）| 代码做了状态机/对账增量 |

01 文档 §7.3bis 后补的 **触达任务完整状态机**：代码 `TouchTaskStateMachineService` 严格落地（包含 `assertTransition` 抛 `CUST-40010`），但 02 文档没有同步状态机服务、03 文档错误码部分仍未列 `CUST-40010`。

---

## 6. 09-依赖契约摘要 偏离

- **§1.2 BizType 枚举**：文档写 `LEAD / CUSTOMER / TAG / TOUCH / LEAD_BATCH / TOUCH_TASK`；代码 `@BizAuth` 实际使用 `LEAD / CUSTOMER / TAG / CLAIM / TOUCH_TASK / TOUCH_REPORT / CUSTOMER_POOL`。文档缺 `CLAIM / TOUCH_REPORT / CUSTOMER_POOL`，多 `TOUCH / LEAD_BATCH`。
- **§1.2 DataScopeType 枚举**：文档写 `ALL / DEPARTMENT_AND_BELOW / DEPARTMENT / PERSONAL`；代码与 03 文档 J 节实际使用 `ALL / ORG_SUBTREE / SELF_CREATED / SELF_ASSIGNED / SELF`。**枚举名完全不同**。
- **§5.1 / §5.2 列出的对外 API 名**（`CustomerQueryApi.getCustomerById` / `isMaintainedBy` / `listCustomersByEmp` / `TouchQueryApi` / `LeadQueryApi.countApprovedByEmpAndPeriod`）**全部都不在 04 契约和代码里**。这是 09 文档自身与 04 文档+代码不一致。

---

## 7. 文档自身已声明的"未对齐项"（V1.0 技术债，CLAUDE.md 末尾 7 条）

模块 CLAUDE.md 已坦诚记录：
1. 错误码 26 vs 35 — 与本文 §4 一致
2. 零 ArchUnit 守护
3. 线索导入行级校验简化未实现（4 处 TODO）
4. CROSS_ORG 审计待 @AuditLog 升级（3 处 TODO）
5. WorkflowCallbackListener 未区分 APPROVED/REJECTED
6. LeadDeletedListener 线索独立标签清理预留
7. TouchTaskMapper.xml H2/MySQL 函数方言 TODO

---

## 8. 偏离度量化

| 维度 | 一致 | 偏离 | 一致率 |
|---|---|---|---|
| 对外 API 契约（04） | 5/5 接口、35/35 方法 | DTO 个别字段未抽样验证 | **~98%** |
| DDL（05） | 8/8 表、所有字段索引 | 2 处状态枚举值 | **~95%** |
| REST 端点（03） | 主路径 25/32 对齐 | 4 个文档端点未实现、9 个代码端点文档未列、命名风格 6 处差异、`highRisk` 标记全部缺 | **~70%** |
| 错误码（02 §4） | 24/35 | 缺 403 全 7 条 + 422 全 8 条 + 编号语义冲突 1 条 | **~60%** |
| 包结构（02 §3） | 大方向一致 | 7 处类/目录命名差异 | **~75%** |
| 09 依赖契约（BizType / DataScopeType / 下游 API 名） | — | 枚举名、API 名几乎全部不一致 | **~30%** |

---

## 9. 处理建议（按收益排序）

1. **更新 02 §4 错误码清单**：把 `CustomerErrorCode.java` 实际 24 条复制过来，删去未实现的 403/422 系列，或反过来在代码补齐——CLAUDE.md 已经把这条列为技术债 #1，结论是哪边为准需要业务确认。
2. **修正 03 端点路径与权限矩阵**（以代码为准）：J 节权限矩阵直接重写，特别是 `transfer` / `claim` / `my-claims` / `import` 路径。同时统一 `highRisk` 标记策略——要么注解全部补，要么文档全部去掉。
3. **补 09 文档 BizType / DataScopeType 枚举值**：以 `auth-permission-center` 当前实际值为准重写。
4. **同步 05 文档状态机**：`lead_status` 枚举改为 4 态、`task_status` 增列 `IN_PROGRESS`，与 01 §7.3bis、`TouchTaskStateMachineService` 对齐。
5. **02 §3 包结构改写**：删 `CustomerApi`/`TouchTaskApi`/`util/`/`ParallelFlowChecker` 等未落地条目，加入 `TouchTaskStateMachineService` / `LeadCallbackReconcileService` / `CustMasterAssemblerService` / `controller/admin/AdminTouchTaskController`。

---

## 10. 2026-04-28 子代理深核增量（4 路并行复核）

> 由 只读代理并行核验 §1/§4/§6/§3+§5，本节为补充结论与证据。原 §0~§9 不动以保留时间线。

### 10.1 推翻原稿判断

| 原稿位置 | 原结论 | 复核结论 | 证据 |
|---|---|---|---|
| §3 末尾 "枚举值偏离" | DDL 文档 §5.3 落后于代码 | **错**。代码 + `ddl-customer.sql` + 05 §5.3 三方完全一致使用 5 值方案 (`DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED`) | 真正落后的是 **01-功能规格.md §3.6** 写作 `PENDING_APPROVAL`，以及模块 CLAUDE.md 错误地概括为 4 态 |
| §3 TouchTaskStatus | 方向正确 | 加强：`ddl-customer.sql` 与 05 §5.8 注释**双双漏写** `IN_PROGRESS`，需同步两份；代码 + 01 §7.3bis 是真值 | `TouchTaskStateMachineService.assertTransition` 实际允许 `PENDING→{IN_PROGRESS,SUCCESS,CANCELLED}`、`IN_PROGRESS→{SUCCESS,CANCELLED}` |
| §4 "24 vs 35" | 数量差异 | **数量是假象**：编号体系已完全分叉，"双方编号+message 完全一致"条目数 = **0** | 文档用 `CUST-400xx` 混编 400/404，代码已分离 `400xx/404xx/409xx` |

### 10.2 原稿未列的新发现

- **`CustMasterStatus` 偏离**：02 后端架构 §3 标注 `VALID/DELETED` vs 代码实际 `ACTIVE/INACTIVE`
- **错误码同号双义（高危，生产风险）**：`CUST-40904` 文档=客户在途流程不允许删除 / 代码=客户已被认领 → 必须立即拆分
- **错误码同号语义偏**：`CUST-40901` 文档=客户名称已存在 / 代码=标签名称已存在
- **跨模块通用码硬编码**：`CustomerQueryApiImpl` 与 `TagApiImpl` 共 4 处 `throw new BizException("COMMON-40000", ...)` 未走 `CustomerErrorCode`，待复核 `TouchTaskStateMachineService` 第 53/58 行 2 处 throw

### 10.3 09 §1.2 真值（取代原稿 §6 的描述）

`BizType`（定义在 common-security 模块，**实际 18 值**）：
```
NAV, ADDRBOOK, PRODUCT, DOC, TAG, LEAD, CUSTOMER, CUSTOMER_POOL,
CLAIM, TOUCH_TASK, TOUCH_REPORT, LOAN, SUPPORT, SUPPORT_DEPT,
REPORT, PERF_CONFIG, SYS_CONFIG, ORG
```
- 仅文档存在（**虚构**）：`TOUCH`、`LEAD_BATCH`
- customer-marketing-center 实际 `@BizAuth` 用到 7 值：`TAG, LEAD, CUSTOMER, CUSTOMER_POOL, CLAIM, TOUCH_TASK, TOUCH_REPORT`

`DataScopeType`（定义在 auth-permission-center，**实际 7 值**）：
```
SELF_CREATED, SELF, SELF_ASSIGNED, ORG, ORG_SUBTREE, ALL, WORKFLOW_PARTICIPANT
```
- 仅文档存在（**全部虚构**）：`DEPARTMENT_AND_BELOW`、`DEPARTMENT`、`PERSONAL` — auth-permission-center 代码中均无此名

09 §5 下游 API 名核验（5 个全错）：
- `CustomerQueryApi.getCustomerById` → 真名 `getCustomer(custId)` 返 `Optional`
- `CustomerQueryApi.isMaintainedBy` → ✗ 不存在；最近的是 `isClaimedByOrg(custId, orgCode)`，**语义不同**（机构是否认领，非维护人校验）
- `CustomerQueryApi.listCustomersByEmp` → ✗ 不存在
- `TouchQueryApi` → 接口名错；真名 `TouchTaskQueryApi`
- `LeadQueryApi.countApprovedByEmpAndPeriod` → ✗ 不存在；接口真名为 `LeadApi`，无 `countApproved*` 方法

### 10.4 REST 端点 file:line 全证据（补充 §1）

A 类「改文档」12 条（路径以代码为准）：

| Controller:line | 实际 | 文档（03 节） |
|---|---|---|
| LeadImportController.java:55 / :73 | `POST /api/leads/import/preview` + `/execute`（拆为 2 端点） | C.2 仅列单一 `/import` |
| LeadImportController.java:93 | `GET /api/leads/batches` | C.3 写作 `/leads/import/batches` |
| LeadController.java:192 | `POST /api/leads/edit-version`（sourceLeadId 在 body） | B.7 写作 `/leads/{id}/edit` |
| CustomerController.java:92 | `POST /api/customers/{custId}/claims/{claimId}/transfer` | D.4 写作 `/customers/{id}/transfer` |
| ClaimController.java:55 | `POST /api/claims`（custId 在 body） | E.2 写作 `/customer-pool/{custId}/claim` |
| ClaimController.java:95 | `GET /api/claims/mine` | F.1 写作 `/my-claims` |
| CustomerTagController.java:55 / :77 | `POST/DELETE /api/customers/{id}/tags(/{tagId})` | 文档未列 |
| CustomerHistoryController.java:59 | `GET /api/customers/{id}/history` | D.3 存在但报文未展开 |
| LeadController.java:219 / :240 | `POST /api/leads/delete-version` + `GET /api/leads/{id}/versions` | 文档未列 |
| TouchTaskController.java:178 | `GET /api/touch-tasks/{id}/logs` | 文档仅 G.5 写入端点 |
| TouchReportController.java:58 / :83 / :107 | 报表 3 端点 | 文档未列 |
| AdminTouchTaskController.java:62 / :140 | `GET /api/admin/touch-tasks` + `POST /batch-assign` | 文档仅 H.3 export |

B 类「改代码」3 条：`POST /api/claims/{claimId}/re-touch`（F.3）、`GET /api/admin/touch-tasks/summary`（H.1）、`GET /api/leads/import/batches/{batchId}`（C.4）— 全模块 grep 0 命中。**✓ 已完成 P1a 2026-04-28**：3 端点全部落地，新增 `CUST-40908/40909` 错误码 + `ReTouchReqDTO` + `LeadImportBatchDetailRespDTO`，335 测试全绿（新增 14 测试），PT_RESOURCE 落 `docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql`。

### 10.5 `@BizAuth.highRisk` 缺失全量清单（补 §1.4）

文档 J 节标注 `highRisk=true` 但代码注解全部缺该参数，9 处具体落地：

```
LeadImportController.java:56  (IMPORT preview)
LeadImportController.java:74  (IMPORT execute)
CustomerExportController.java:58  (EXPORT)
TagCustomerController.java:61   (TAG IMPORT)
TagCustomerController.java:98   (TAG EXPORT)
TouchReportController.java:108  (REPORT EXPORT)
CustomerController.java:92      (TRANSFER)
AdminTouchTaskController.java:140 (BATCH_ASSIGN)
LeadController.java:158         (DELETE)
```

待业务确认：`@BizAuth` 注解类是否支持 `highRisk` 字段；若支持需补 9 处，若框架未实现则记技术债。

### 10.6 错误码全量分类（取代原 §4 的概要）

- **仅文档存在（待确认是否补实现）16 条**：~~`CUST-40301~40307`（403 系列 7 条）~~ **[done] P1C 2026-04-29**（40301 升级自 40003 / 40302 markSuccess+cancel 校验 / 40303-40304 占位 / 40305 P1a→cancelClaim 扩展 / 40306-40307 transfer 接收人校验，依赖新增 UserApi.getUserRoleCodes）+ ~~`CUST-42201~42208`（422 系列 8 条）~~ **[done] P1B 2026-04-29**（8 条已落地，CUST-42201 仅占位待 V1.x 行级校验补齐）+ `CUST-50003`（MinIO 上传失败）
- **仅代码存在（待补回文档）17 条**：`CUST-40001/40002/40004~40009`（参数校验 8 条）+ `CUST-40401~40406`（404 资源不存在 6 条）+ `CUST-40902/40903/40906`（409 冲突 3 条）。**P1B 2026-04-29 备注**：`CUST-40006` 已删除（语义迁至 CUST-42205）
- **同号但语义/message 不同 2 条（最危险）**：`CUST-40904`（双义，必修）、`CUST-40901`（语义偏）
- **双方编号+message 完全一致：0 条**
- **实际 throw 但 `CustomerErrorCode` 未定义**：4 处硬编码 `COMMON-40000`（`CustomerQueryApiImpl` / `TagApiImpl`），2 处 `TouchTaskStateMachineService:53/58` 待复核是否内联字符串

### 10.7 包结构核验（补 §5）

| 类别 | 条目 |
|---|---|
| ⚠ 位置/命名不同 | `convert/` → `api/converter/`（5 个转换器） |
| ➕ 代码多出（02 未列） | `CustomerExportController`、`CustomerTagController`、`controller/admin/AdminTouchTaskController`、`TouchTaskStateMachineService`、`LeadCallbackReconcileService`、`LeadCallbackCompensationService`、`CustomerSchedulingConfig` |
| ✗ 02 列出但代码不存在 | `controller/TagImportController`、`service/{ParallelFlowChecker, CustomerHistoryService, TagImportService}`、`config/{CustomerModuleConfig, ExcelTemplateConfig, CustomerEventPublisher}`、`util/` 整目录 4 个工具类、`facade/{CustomerApiImpl, TouchTaskApiImpl}`、`api/{CustomerApi, TouchTaskApi}` |

### 10.8 修复优先级修订（取代原 §9）

| 优先级 | 类型 | 任务 |
|---|---|---|
| **P0** | 改代码 | `CUST-40904` 编号双义拆分（生产风险） |
| **P0** | 改文档 | 09 §1.2 `BizType`/`DataScopeType` 用真值整体重写；09 §5 删除 5 个虚构 API 名或更名 |
| **P1** | 改文档 | `ddl-customer.sql` + 05 §5.8 `task_status` COMMENT 补 `IN_PROGRESS`；01 §3.6 `LeadStatus` 改 5 值；02 §3 `CustMasterStatus` 改 `ACTIVE/INACTIVE`、删 `CustomerApi/TouchTaskApi/util/` 等未落地条目 |
| **P1** | 业务确认 | ~~3 个真缺失 REST 端点（re-touch / summary / batch detail）是否要补~~ **[done] P1a 2026-04-28**；`@BizAuth.highRisk` 字段是否落地 9 处；~~422 系列 8 条错误码补实现~~ **[done] P1B 2026-04-29**；~~403 系列 7 条错误码补实现~~ **[done] P1C 2026-04-29**（40301 LeadService 升级自 40003 + 40302 TouchTaskService.markSuccess/cancel 加 assignee 校验 + 40305 ClaimService.cancelClaim 扩展跨机构校验 + 40306/40307 CustomerService.transfer 加接收人角色/机构校验；UserApi 新增 getUserRoleCodes API；40303/40304 占位）|

### P1C follow-up（必须创建）

1. **CUST-FU-40003-breaking-frontend**：CUST-40003 (LEAD_NOT_DRAFT) → CUST-40301 (LEAD_EDIT_FORBIDDEN) message 由 "线索非草稿状态，不允许编辑" 改 "无权编辑非草稿状态线索"。**Breaking 给前端**：i18n 字符串映射需更新；如有 curl-test-plan.md 引用同步修改。
2. **CUST-FU-40303-admin-delete**：admin force-delete 端点本批占位未实施（依赖 CustMasterMapper 缺 deleteById 方法 + 跨范围）。需新增 mapper 方法 + AdminCustomerController 端点 + AuditLog；service 层加 isAdmin 守卫抛 CUST-40303。
3. **CUST-FU-40304-history-doc**：跨机构历史已由 `@BizAuth(CUSTOMER, READ)` + DataScope 过滤覆盖，无 service 层二次校验。需要 03 文档 + CLAUDE.md 标注 "由 @BizAuth + 角色级 DataScope 拦截"，CUST-40304 仅占位。
4. **CUST-FU-40306-rolecode-const**：CustomerService.REQUIRED_RECEIVER_ROLE = "R_RM" 字符串硬编码；建议 auth 模块暴露 `RoleCodeConstants` 公共常量类。
5. **CUST-FU-40307-receiver-not-found**：assertReceiverEligible 把 receiver=null 误归类为机构不符（CUST-40307），语义模糊；建议新增 CUST-404xx RECEIVER_NOT_FOUND。
6. **CUST-FU-userapi-cache**：UserApi.getUserRoleCodes 不走缓存（每次 PT_USER_ROLE JOIN PT_ROLE）。高频转交场景需加 cache key `auth:user-role-codes:{empId}` 5min TTL，并在 UserRoleService.bind/unbind 时 evict。
7. **CUST-FU-claim-org-check-extract**：ClaimService.reTouch (用 `.equals` 有 NPE 风险) 与 cancelClaim (用 `Objects.equals`) 跨机构校验逻辑重复；抽 `assertSameOrg(claim, operatorOrgCode)` 共用方法。
| **P2** | 长期 | 错误码编号体系整体重对齐（双方零一致条目，需统一规划而非逐条修） |
