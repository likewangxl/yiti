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
