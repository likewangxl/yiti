# 导出接口统一规范 — 覆盖状态与跨模块落地记录

**创建日期**：2026-04-10
**背景**：`docs/common-dev-guide.md §8.2 第 5 条` 定义了"超过 5000 行必须走异步导出"的全局规则。本文档追踪该规则在各模块的落地状态，区分"已在文档中规范化"、"需追加规范"、"不涉及"三类。

---

## 1. 规则回顾

核心规则（完整定义见 `docs/common-dev-guide.md:1563-1582`）：

| 命中行数 | 处理方式 | 说明 |
|---|---|---|
| ≤ 5000 | 同步流式返回 Excel | 直接 HTTP 流写出 |
| > 5000 | 异步任务 | 返回 `taskId`，文件上传 MinIO，通知前端下载 |

每个模块的 03-接口设计与报文.md 对自己的导出接口必须提供：
1. **同步/异步分支判定规则**（阈值 + 拒绝上限）
2. **文件命名规则**（含 `filterHash` 溯源审计）
3. **异步任务响应 DTO**（`ExportTaskRespDTO` 字段表）
4. **列定义规范**（列序、中文名、字段、格式、脱敏）
5. **错误码**（模块前缀）
6. **审计要求**（关联 07-审计要求.md）

---

## 2. 9 个模块的导出接口覆盖状态

| 模块 | 模块类型 | 是否有导出接口 | 03 文档中的导出规范 | 状态 | 备注 |
|---|---|---|---|---|---|
| `common` | 基础设施 | ❌ 不涉及 | — | N/A | 仅提供 Excel 工具类（EasyExcel 封装），不直接对外导出接口 |
| `auth-permission-center` | 支撑域（已完成） | ❌ 无 | — | **未涉及** | 当前 V1 无任何导出接口。V2 若需要用户/角色/资源导出，需追加规范。详见 §3.1 |
| `system-governance-center` | 支撑域（已完成） | ✅ 有 1 个（D.3 审计日志导出） | ⚠️ 部分规范化 | **未完全开发** | 已有文件名规则 + `export.max.rows` 配置项（默认 50000），**但**尚未按新的 §K 规范升级到同步/异步分支、`ExportTaskRespDTO`、列定义表格。详见 §3.2 |
| `workflow-center` | 支撑域（已完成） | ❌ 无 | — | **未涉及** | 当前 V1 无任何导出接口。若 V2 需要流程历史导出、任务清单导出，需追加规范。详见 §3.3 |
| `portal-content-center` | 通用域（待开发） | ✅ 1 个（D.7 产品导出） | ✅ **已规范化** | 完成 2026-04-10 | 03 附录 H，接口引用 §H.7.1 |
| `customer-marketing-center` | 核心域（待开发） | ✅ 3 个（A.7/D.6/H.3） | ✅ **已规范化** | 完成 2026-04-10 | 03 附录 K，接口分别引用 §K.7.1 / §K.7.2 / §K.7.3 |
| `business-application-center` | 核心域（待开发） | ✅ 2 个（A.8/C.7） | ✅ **已规范化** | 完成 2026-04-10 | 03 附录 H，接口分别引用 §H.7.1 / §H.7.2 |
| `performance-engine-center` | 核心域（待开发） | ✅ 4 个（07 列出但 03 原缺失） | ✅ **已规范化 + 补齐缺失接口** | 完成 2026-04-10 | 03 附录 J，同时补齐 `/kpi-results/export`、`/metric-results/export`、`/cust-alloc/export`（最高风险）、`/kpi-results/detail-batch/export` 的 HTTP 定义 |
| `report-analytics-center` | 支撑域（待开发） | ✅ 多个（A.3 主路径 + E.1/E.2 状态下载，V2 预留 4 个） | ✅ **已规范化** | 完成 2026-04-10 | 03 附录 I，强制异步、硬上限 500000 行、动态列头生成规则 |

**统计**：
- ✅ 5 个待开发模块已全部完成导出规范补充
- ⚠️ 1 个已完成模块（governance）有 1 个导出接口需要升级到新规范
- ❌ 2 个已完成模块（auth/workflow）目前没有导出接口，属于"未涉及"状态

---

## 3. 已开发模块的未开发范围详述

本节列出 **auth-permission-center / system-governance-center / workflow-center** 三个已实现模块中"涉及导出接口规范"但**尚未开发**或**尚未对齐新规范**的具体内容。

> **重要说明**：这三个模块已进入"已完成"状态，本节不要求立即修改代码或文档。仅作为**技术债清单**记录，以便 V2 或下一个迭代决定是否升级。

### 3.1 auth-permission-center — 未开发

**当前状态**：V1 无任何 `action = EXPORT` 的接口。

**V1 对齐需求**：**无**。当前设计合理，权限系统的敏感数据不应对外导出。

**V2 技术债清单**（若未来需要）：

| 潜在需求 | 说明 | 预计优先级 |
|---|---|---|
| 用户列表导出 | 管理员导出全行员工清单（用于人事对账） | P2（V2） |
| 角色-资源绑定导出 | 审计合规场景，导出当前权限矩阵快照 | P1（V2 合规） |
| 角色-BizType-数据范围导出 | 合规审计，`PT_ROLE_BIZ_SCOPE` 快照 | P1（V2 合规） |
| 登录历史导出 | 已在 governance 的 `audit_log` 中统一管理，不需要 auth 重复提供 | — |

**若 V2 需追加**：
- 新增 `docs/modules/auth-permission-center/03-接口设计与报文.md` 的"附录 X. 导出接口统一规范"
- 错误码前缀 `AUTH-422XX`
- 因涉及敏感数据（如 `PT_USER.MOBILE`），**必须**落实 `K.4/H.4` 的脱敏规则
- **必须**高危审计（审计留存 5 年）

### 3.2 system-governance-center — 部分未对齐

**当前状态**：V1 已有 1 个导出接口：

```
D.3 POST /api/admin/sys/audit-logs/export -- 导出审计日志
```

位置：`docs/modules/system-governance-center/03-接口设计与报文.md:562-584`

**已规范化部分**：
- ✅ 请求 DTO（`AuditLogExportReqDTO`）字段级定义完整
- ✅ Content-Type 声明
- ✅ 文件名模板：`审计日志_yyyyMMddHHmmss.xlsx`
- ✅ 行数上限：`sys_config_kv` 中 `export.max.rows` 配置（默认 50000）
- ✅ "导出操作本身需写入审计日志"的自我审计规则

**未对齐新规范的部分**（技术债）：

| 项目 | 当前状态 | 新规范要求 | 影响 |
|---|---|---|---|
| 同步/异步分支判定 | ❌ 未区分 | ≤ 5000 同步，> 5000 异步 | 当前默认一次性同步返回 Excel；默认 50000 行上限下可能 OOM |
| 异步任务响应 DTO | ❌ 无 | `ExportTaskRespDTO` 字段表 | 前端无法轮询任务状态 |
| 文件名 filterHash | ⚠️ 未包含 | `gov_auditlog_{filterHash}_{yyyyMMddHHmmss}.xlsx` | 多次导出相同条件时无溯源依据 |
| 列定义表格 | ❌ 仅描述 | 列序+中文名+字段+格式+脱敏 5 列表格 | 不同开发者产出列序可能不同 |
| 错误码 | ❌ 未定义 | `GOV-42207` 等 | 前端无法精确处理错误 |
| 敏感字段脱敏 | ❌ 未明示 | `requestParams` 字段可能含手机号/身份证，需先脱敏再写 Excel | 合规风险 |

**V1 对齐工作量估算**：2-4 小时（修改 `03-接口设计与报文.md` D.3 节，追加附录 X）。

**建议**：governance 是治理中心，审计导出是其核心合规能力，建议**不等 V2**，在下一个迭代窗口完成对齐。

**对齐后需要做的事**：
1. 修改 `docs/modules/system-governance-center/03-接口设计与报文.md` D.3 节
2. 在该文档末尾追加"附录 X. 导出接口统一规范"
3. 在 `governance` 模块的代码中实现同步/异步分支判定
4. 升级 `sys_config_kv` 中的 `export.max.rows` 从 50000 调整为：同步阈值 5000 + 硬上限 100000
5. 引入 `ExportTaskRespDTO` 并与 `file_object_id` 打通
6. 在 `docs/superpowers/sql/YYYY-MM-DD-gov-audit-export-upgrade.sql` 新增升级脚本

### 3.3 workflow-center — 未开发

**当前状态**：V1 无任何 `action = EXPORT` 的接口。

**V1 对齐需求**：**无**。当前设计合理，流程引擎不应直接对外导出。

**V2 技术债清单**（若未来需要）：

| 潜在需求 | 说明 | 当前归属建议 |
|---|---|---|
| 流程历史查询导出 | 审计场景需要导出某流程的历史 Task | 建议走 `report-analytics-center` 动态查询 |
| 我的代办任务导出 | 个人待办清单导出 | 建议走 `portal-content-center` 的工作台扩展 |
| 流程 SLA 监控导出 | 超期任务清单导出 | 建议走 `report-analytics-center` 固定报表 |
| 节点候选人配置导出 | 管理员维护流程配置时导出当前快照 | 若真需要，在 workflow-center 内定义 |

**结论**：workflow-center 应保持"不对外提供导出接口"的原则，流程类数据的导出能力统一由 `report-analytics-center` 提供。

---

## 4. 所有修改点清单（2026-04-10 本次变更）

### 4.1 新增章节（5 个待开发模块）

| 模块 | 文档 | 新增位置 | 章节标题 |
|---|---|---|---|
| portal | `03-接口设计与报文.md` | 末尾 | 附录 H. 导出接口统一规范 |
| customer | `03-接口设计与报文.md` | 末尾 | 附录 K. 导出接口统一规范 |
| bizapp | `03-接口设计与报文.md` | 末尾 | 附录 H. 导出接口统一规范 |
| performance | `03-接口设计与报文.md` | I 之后 | 附录 J. 导出接口统一规范（含 4 个 EXPORT 接口补齐） |
| report | `03-接口设计与报文.md` | H 之后 | 附录 I. 导出接口统一规范 |

### 4.2 就地修改的导出接口（共 10 个）

| 模块 | 接口 | 修改内容 |
|---|---|---|
| portal | D.7 `GET /api/products/export` | 补引用 §H.7.1 + 同步/异步阈值声明 |
| customer | A.7 `GET /api/tags/{id}/customers/export` | 补引用 §K.7.1 + 8 列清单指引 |
| customer | D.6 `GET /api/customers/export` | 补引用 §K.7.2 + 11 列清单指引（含脱敏） |
| customer | H.3 `GET /api/admin/touch-tasks/export` | 补引用 §K.7.3 + 10 列清单指引 |
| bizapp | A.8 `GET /api/loans/export` | 补引用 §H.7.1 + 14 列清单指引 |
| bizapp | C.7 `GET /api/support-requests/export` | 补引用 §H.7.2 + 14 列清单指引（含场景 A/B） |
| performance | `GET /api/perf/kpi-results/export` | **新增接口定义**，详见 §J.7.1 |
| performance | `GET /api/perf/metric-results/export` | **新增接口定义**，详见 §J.7.2 |
| performance | `GET /api/perf/cust-alloc/export` | **新增接口定义（最高风险）**，详见 §J.7.3 |
| performance | `GET /api/perf/kpi-results/detail-batch/export` | **新增接口定义（高危）**，详见 §J.7.4 |
| report | A.3 `POST /api/reports/dynamic-query/export` | 扩展 `ExportTaskRespDTO` 字段，补引用 §I.3 / §I.4 |

### 4.3 不涉及的模块

| 模块 | 说明 |
|---|---|
| auth-permission-center | V1 无导出接口 |
| workflow-center | V1 无导出接口 |
| common | 仅提供 Excel 工具类，不对外导出 |

---

## 5. 后续行动清单

| 优先级 | 动作 | 归属 | 预估时间 |
|---|---|---|---|
| P1 | governance 的 D.3 审计日志导出升级到新规范 | 系统治理中心 | 2-4 小时 |
| P2 | V2 规划时，评估 auth 是否需要 `PT_ROLE_RESOURCE` / `PT_ROLE_BIZ_SCOPE` 审计快照导出 | 认证授权中心 | 待定 |
| P2 | report 在 V2 补齐 C.1-C.4 固定报表的 `/export` 端点 | 报表分析中心 | 4-6 小时 |
| P3 | 每个模块的 05-表结构DDL.md 增加异步导出任务表 (`sys_async_task` 建议共享 governance) | 系统治理中心 | 4 小时 |
| P3 | 在 `common-dev-guide.md §8.2` 增加"导出规范实现参考"小节，指向 `docs/modules/*/03-接口设计与报文.md` 附录 | 共享规范 | 1 小时 |

---

## 6. 关键约定速查

| 约定项 | V1 统一值 | 备注 |
|---|---|---|
| 同步阈值 | 5000 行 | 所有模块一致 |
| 硬上限（业务模块） | 100000 行 | portal / customer / bizapp |
| 硬上限（绩效） | 200000 行 | performance（KPI 明细展开后行数更多） |
| 硬上限（报表） | 500000 行 | report（跨模块聚合查询） |
| 文件命名格式 | `{module}_{business}_{filterHash}_{yyyyMMddHHmmss}.xlsx` | `filterHash` = 请求参数 MD5 前 8 位 |
| 异步任务过期时间 | 24 小时 | 默认值，可由 sys_config_kv 覆盖 |
| 错误码前缀 | `{PREFIX}-42207` | 超出上限统一错误码后缀 |
| 审计留存（EXPORT） | 5 年 | 所有 EXPORT 都是高危动作 |
| 异步任务状态机 | PENDING → RUNNING → SUCCESS / FAILED | 允许 CANCELLED 分支（用户主动取消） |

---

**维护者**：Claude Code
**最近更新**：2026-04-10
**下一次复审**：governance D.3 升级完成后，更新本文档 §2 表格和 §3.2 章节
