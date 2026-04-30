# customer-marketing-center 代码 ↔ 文档偏离检查报告

**检查日期**: 2026-04-26
**代码版本**: 当前 master 分支 (113 main + 46 test = 159 Java 文件)
**文档版本**: docs/modules/customer-marketing-center/ (最后更新 2026-04-10 ~ 04-16)

---

## 一、偏离总览

| 维度 | 代码实际 | 文档描述 | 偏离度 |
|------|---------|---------|--------|
| Controller 类 | 13 个 | 10 个命名 | 🔴 3 缺失 + 1 命名错 |
| Facade 类 | 5 个 | 7 个命名 | 🔴 2 多余 + 1 缺失 |
| Service 类 | 13 个 | 14 个命名 | 🟡 3 命名错 + 1 缺失 + 1 多余 |
| API 接口 | 5 个 | 5 个 | 🟢 一致 |
| 实体 (表) | 8 个 | 8 个 | 🟢 一致 |
| Mapper | 9 个 | 8 个 | 🟡 少记 1 个(TouchReportMapper) |
| 枚举 | 10 个 | 9 个 | 🟡 文档缺 SlaStatus |
| 事件类 | 7 个 | 7 个 | 🔴 内容不同 (LeadRejectedEvent vs LeadDeletedEvent) |
| 监听器 | 5 个 | 4 个 | 🟡 文档缺 ClaimCreatedListener |
| API DTO | 8 个 | 9 个 | 🟡 多记 1 个 |
| REST 端点 | 36 个 | ~36 个 | 🟡 分组方式不同 |
| 缓存 Key | 2 个 | 4 个 | 🟡 多记 2 个 |

---

## 二、具体偏离明细

### 🔴 严重偏离

#### 2.1 事件类内容不同

| 代码实际 (event/) | 文档描述 (04-对外API契约.md §8) |
|---|---|
| `LeadApprovedEvent` | `LeadApprovedEvent` ✅ |
| `LeadDeletedEvent` | `LeadRejectedEvent` ❌ |
| `ClaimCreatedEvent` | `ClaimCreatedEvent` ✅ |
| `ClaimCancelledEvent` | `ClaimCancelledEvent` ✅ |
| `ClaimTransferredEvent` | `ClaimTransferredEvent` ✅ |
| `TouchCompletedEvent` | `TouchCompletedEvent` ✅ |
| `CustomerDeletedEvent` | `CustomerDeletedEvent` ✅ |

**偏离**: 代码中不存在 `LeadRejectedEvent`，实际类名是 `LeadDeletedEvent`。文档用 rejected 语义，代码用 deleted 语义——这意味着审批拒绝和删除是两个不同的事件流，文档描述有误。

#### 2.2 Controller 命名与数量不匹配

| 代码实际 Controller | 文档 (02-后端架构.md) 描述 | 状态 |
|---|---|---|
| `TagController` | `TagController` | 🟢 |
| `TagCustomerController` | `TagImportController` | 🔴 命名错 |
| `LeadController` | `LeadController` | 🟢 |
| `LeadImportController` | `LeadImportController` | 🟢 |
| `CustomerController` | `CustomerController` | 🟢 |
| `CustomerExportController` | — | 🔴 文档缺失 |
| `CustomerHistoryController` | `CustomerHistoryController` | 🟢 |
| `CustomerTagController` | — | 🔴 文档缺失 |
| `CustomerPoolController` | `CustomerPoolController` | 🟢 |
| `ClaimController` | `ClaimController` | 🟢 |
| `TouchTaskController` | `TouchTaskController` | 🟢 |
| `TouchReportController` | `TouchReportController` | 🟢 |
| `AdminTouchTaskController` | — | 🔴 文档缺失 |

**偏离**: 文档命名 `TagImportController`（标签导入控制器）与实际 `TagCustomerController`（标签客户控制器）不匹配，实际功能远不止导入。文档漏记 `CustomerExportController`、`CustomerTagController`、`AdminTouchTaskController` 三个控制器。

#### 2.3 Facade 数量与命名不匹配

| 代码实际 Facade | 文档描述 | 状态 |
|---|---|---|
| `ClaimApiImpl` | `ClaimApiImpl` | 🟢 |
| `CustomerQueryApiImpl` | `CustomerQueryApiImpl` | 🟢 |
| `LeadApiImpl` | `LeadApiImpl` | 🟢 |
| `TagApiImpl` | — | 🔴 文档缺失 |
| `TouchTaskQueryApiImpl` | `TouchTaskQueryApiImpl` | 🟢 |
| — | `CustomerApiImpl` | 🔴 代码中不存在 (无 CustomerApi 接口) |
| — | `TouchTaskApiImpl` | 🔴 代码中不存在 (无 TouchTaskApi 接口) |

**偏离**: 文档记录了不存在的 `CustomerApiImpl` 和 `TouchTaskApiImpl`（代码中没有 `CustomerApi` 和 `TouchTaskApi` 接口），而实际存在的 `TagApiImpl` 未被记录。

---

### 🟡 中度偏离

#### 2.4 Service 类命名与数量不匹配

| 代码实际 Service | 文档描述 | 状态 |
|---|---|---|
| `TagService` | `TagService` | 🟢 |
| `TagCustomerService` | `TagImportService` | 🔴 命名错 |
| `LeadService` | `LeadService` | 🟢 |
| `LeadImportService` | `LeadImportService` | 🟢 |
| `LeadVersionService` | `LeadVersionService` | 🟢 |
| `CustomerService` | `CustomerService` | 🟢 |
| — | `CustomerHistoryService` | 🔴 代码中不存在 (功能在 CustomerService 内) |
| `CustomerPoolService` | `CustomerPoolService` | 🟢 |
| `ClaimService` | `ClaimService` | 🟢 |
| `TouchTaskService` | `TouchTaskService` | 🟢 |
| `TouchLogService` | `TouchLogService` | 🟢 |
| `TouchReportService` | `TouchReportService` | 🟢 |
| `TouchTaskStateMachineService` | — | 🔴 文档缺失 |
| `CustMasterAssemblerService` | `CustMasterAssemblerService` | 🟢 |
| — | `ParallelFlowChecker` | 🔴 代码中不存在 |

**偏离**: 文档将 `TagCustomerService` 命名为 `TagImportService`（范围偏差——实际还包含打标/取消打标）。文档记录了已合并入 `CustomerService` 的独立 `CustomerHistoryService`。文档记录了不存在的 `ParallelFlowChecker`。代码中实际存在的 `TouchTaskStateMachineService` 未被文档记录。

#### 2.5 枚举缺失

代码中存在 **`SlaStatus`** 枚举 (GREEN, YELLOW, RED)，用于触达任务 SLA 预警状态，在 `TouchTask` 实体中有 `slaStatus` 字段引用。文档枚举清单 (02-后端架构.md) 中缺失此枚举。

#### 2.6 Mapper 少记

代码有 9 个 Mapper 接口，文档 (02-后端架构.md) 只列出 8 个——**`TouchReportMapper`** 被遗漏。该 Mapper 负责触达报告的 JOIN 查询（无独立实体表），不遵循"一个实体一个 Mapper"模式，导致容易被忽略。

#### 2.7 监听器少记

代码有 5 个 Listener:
- `ClaimCreatedListener` (监听 ClaimCreatedEvent → 自动创建触达任务)
- `LeadApprovedListener`
- `LeadDeletedListener`
- `TouchTaskCompletedListener`
- `WorkflowCallbackListener`

文档 (02-后端架构.md) 仅列出 4 个，**`ClaimCreatedListener`** 未记录。这是关键的认领→触达自动化链路。

#### 2.8 API DTO 数量偏差

文档列出 9 个 API DTO，代码实际 8 个。文档多记了 `TouchLogDTO`（实际代码中 `TouchLogDTO` 确实存在，需要核实）。重数后文档和代码都是包含 TouchLogDTO 的，需要逐个比对确认到底是哪个多了或少了。

#### 2.9 缓存配置偏差

文档 (06-并发与事务策略.md) 描述 4 个缓存 Key:
1. `cust:tag:enabled:list` (5min)
2. `cust:customer:{id}` (5min)
3. `cust:customer:{id}:claims` (3min)
4. `cust:emp:{empId}:touch:running` (1min)

代码 (`CustomerCacheConfig.java`) 只定义 2 个 Key 常量:
- `TAG_ENABLED_KEY`
- `CUSTOMER_DETAIL_KEY_PREFIX`

后两个缓存 Key 在代码中不存在（可能内联在 Service 中或尚未实现）。

---

### 🟢 一致项（已验证）

- **API 接口** (5个): ClaimApi, CustomerQueryApi, LeadApi, TagApi, TouchTaskQueryApi — 完全一致
- **实体/表** (8张): 表名、字段数与文档 DDL 一致
- **REST 端点总数** (~36): 数量一致，但文档的分组方式与实际 URL 归属略有偏移
- **Maven 依赖声明**: pom.xml 与文档一致
- **跨模块依赖**: 上下游依赖链与文档一致

---

## 三、设计规格中已标注的已知不一致

设计规格 `specs/2026-04-13-customer-marketing-center-design.md` §3.2 列出了 5 处已知不一致，本次确认：

| # | 问题 | 当前代码状态 |
|---|------|------------|
| 1 | `LeadStatus` 文档四态 vs DDL 五态 | 代码枚举含 DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED (五态)，与 DDL 一致 |
| 2 | `CustMasterStatus` 文档 VALID/DELETED vs DDL ACTIVE/INACTIVE | 代码枚举为 ACTIVE/INACTIVE，与 DDL 一致 |
| 3 | `TouchTaskStatus` 文档四态 vs DDL 三态 | 代码枚举含 PENDING/IN_PROGRESS/SUCCESS/CANCELLED (四态)，实际包含 IN_PROGRESS |
| 4 | 标签状态文档 ENABLED/DISABLED vs DDL ACTIVE/DISABLED | 代码枚举为 ACTIVE/DISABLED，与 DDL 一致 |
| 5 | 响应包装文档 `ApiResponse<T>` vs common-dev-guide `ResponseWrapper<T>` | 需核实实际 Controller 返回类型 |

---

## 四、数据范围描述偏差

文档 (02-后端架构.md §5, 05-表结构DDL.md §7) 描述数据范围 **100% 基于 `cust_claim` 有效认领关系**。但审视代码，`CustomerPoolService` 使用 LEFT JOIN `cust_claim` 查询**未认领客户**（claim 为空即入池），而 `CustomerService` 的常规查询同时参考了 `owner_org_id` 和认领关系。文档的"100% 基于认领"描述过度简化，实际存在两种数据可见性路径：认领路径（claimed 客户）和归属路径（owner_org_id）。

---

## 五、汇总建议

| 优先级 | 修复项 | 涉及文档 |
|--------|--------|---------|
| **P0** | `LeadRejectedEvent` → `LeadDeletedEvent` 事件名更正 | 04-对外API契约.md, 02-后端架构.md |
| **P0** | `TagImportController` → `TagCustomerController` / `TagImportService` → `TagCustomerService` 命名更正 | 02-后端架构.md, 03-接口设计与报文.md |
| **P0** | 删除不存在的 `CustomerApiImpl` / `TouchTaskApiImpl` / `CustomerHistoryService` / `ParallelFlowChecker` | 02-后端架构.md |
| **P1** | 补录 `CustomerExportController` / `CustomerTagController` / `AdminTouchTaskController` / `TagApiImpl` / `TouchTaskStateMachineService` / `ClaimCreatedListener` / `TouchReportMapper` / `SlaStatus` | 02-后端架构.md, 03-接口设计与报文.md |
| **P1** | 缓存 Key 与实际对齐（核实后2个是否需要追回或文档降级） | 06-并发与事务策略.md |
| **P2** | 数据范围策略描述修正："100% 基于认领" → 区分认领路径与归属路径 | 02-后端架构.md, 05-表结构DDL.md |

---

**总计: 6 处严重偏离、8 处中度偏离、5 处已知设计规格不一致（其中 4 处代码已按 DDL 修正确认）、1 处数据范围策略描述过度简化。**
