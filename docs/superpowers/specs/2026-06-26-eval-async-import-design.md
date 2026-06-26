# 评价任务导入异步化改造 设计方案

- 日期：2026-06-26
- 模块：performance-engine-center / eval 子域 + xanzc_frontend
- 背景：`POST /api/admin/eval/assign/import` 当前同步、原子处理 Excel。20 万行级导入耗时远超前端
  axios 全局 15s 超时，导致前端 abort 报「网络异常」，但后端线程继续跑并可能成功入库，造成
  「前端显示失败、数据实际入库」的不一致 + 用户重复导入风险。

## 目标

接口立即返回 `batchId`，把「逐行校验 + 入库」挪到后台异步执行；前端轮询批次状态获取结果。
彻底消除超时不一致与重复入库。

## 架构选型

异步执行机制：**Spring `@Async` 线程池 + 启动/定时补偿**（项目已有 `@EnableAsync`、
`KpiCascadeAsyncConfig` 自定义线程池范式可参考）。

- 不选 Quartz one-shot：导入是低频管理操作，序列化 20 万行/文件落库偏重，收益有限。
- 重启丢任务的兜底：启动补偿 + 定时补偿扫描超时 IMPORTING 批次置失败（见 §4）。

## 1. 批次状态机扩展（DDL）

`EVAL_ASSIGN_BATCH.STATUS` 现有 `0=进行中(ACTIVE) / 1=已结束(CLOSED) / 2=草稿(DRAFT)`，新增：

| 值 | 含义 | 触发 |
|---|---|---|
| 3 | IMPORTING（处理中） | 接口落库即置此态 |
| 4 | IMPORT_FAILED（导入失败） | all-or-none 校验未过 / 后台异常 / 超时补偿 |

新增脚本 `docs/superpowers/sql/2026-06-26-eval-assign-async-import.sql`
（目标库 `yiti` + `onepl_test_bootstrap`，幂等 `ADD COLUMN IF NOT EXISTS` 等价手法）：

```sql
ALTER TABLE EVAL_ASSIGN_BATCH
  ADD COLUMN TOTAL_ROWS     INT        NULL COMMENT '解析出的总行数',
  ADD COLUMN IMPORTED_COUNT INT        NULL COMMENT '成功入库条数',
  ADD COLUMN ERROR_SUMMARY  MEDIUMTEXT NULL COMMENT '失败时行级错误明细 JSON（封顶前 N 条）';
-- STATUS 注释更新为 0=进行中,1=已结束,2=草稿,3=处理中,4=导入失败
```

实体 `EvalAssignBatch` 同步加 `totalRows` / `importedCount` / `errorSummary` 三字段。

## 2. 后端时序

### 2.1 接口线程（`EvalAssignAdminController.importExcel`，破坏性变更）

请求线程内只做「快、会立即失败」的事：

1. 校验文件非空 + EasyExcel 全量解析成内存行集 `List<EvalAssignImportRow>`
   （解析约数秒，文件格式错在此**同步快速反馈**，仍走原错误码）
2. 建批次行 `STATUS=3(IMPORTING)` 并**独立事务提交**，使 `batchId` 立即对轮询可见
3. 把行集 + 批次元数据交给 `@Async` 方法，**立即返回** `{ batchId, status: "IMPORTING" }`

响应体由原「`EvalAssignImportResultDTO` JSON / 错误 CSV 文件流」变为
`ResponseWrapper<EvalAssignImportAcceptedDTO{ batchId, status }>`。

### 2.2 异步执行（`@Async("evalImportExecutor")`）

新增线程池 `EvalImportAsyncConfig`（仿 `KpiCascadeAsyncConfig`：core=1 / max=2 /
queueCapacity 小 / `CallerRunsPolicy`，导入低频不丢任务）。

`processImport(batchId, rows, taskType, taskName, deadline, createBy)`：

1. 加载字典 + 员工存在性映射 → 逐行业务校验（**完全复用**现有 `importRows` 校验逻辑）
2. 有 errors → 更新批次 `STATUS=4 + ERROR_SUMMARY(前 N 条 JSON) + TOTAL_ROWS`，明细一条不写
   （天然 all-or-none）
3. 全通过 → `@Transactional` 批量插明细 + 更新批次 `STATUS=2(草稿) + IMPORTED_COUNT + TOTAL_ROWS`
   （**保持现有「草稿 → 人工确认发布」两段式语义不变**）
4. 兜底 `catch` 真异常 → 独立事务置 `STATUS=4 + ERROR_SUMMARY(异常摘要)`

`EvalAssignImportService` 拆分：
- `parseRows(file)` — 同步解析（接口线程调用）
- `createImportingBatch(...)` — 建 IMPORTING 批次（独立事务，REQUIRES_NEW）
- `@Async processImport(...)` — 异步校验入库
- 内部复用既有 `importRows` 的校验段（重构为可被异步路径调用的私有校验方法）

事务边界：
- `createImportingBatch` 用 `REQUIRES_NEW` 提交，保证 batchId 可见
- `processImport` 成功路径：插明细 + 更新草稿状态同一 `@Transactional`（一起成功/回滚）
- 失败/异常路径：更新失败状态用**独立事务**，不被业务事务回滚牵连

## 3. 错误明细呈现

后台失败时把行级错误（**前 N 条** + 总错误数，`N=perf.eval.import.error-keep:500`，
防 MEDIUMTEXT 撑爆）以 JSON 写入 `ERROR_SUMMARY`。前端轮询到 `STATUS=4` 后，从既有
`GET /batches/{batchId}` 详情接口拿 `errorSummary`，用现有 `importErrors` 表格展示。
不引入 MinIO，保持链路轻量。

## 4. 启动 / 定时补偿

`EvalImportCompensation`：
- `ApplicationRunner` 启动时扫一次
- `@Scheduled`（复用 `PerformanceSchedulingConfig` 的 `@EnableScheduling`，每 5 分钟）

扫描 `STATUS=3(IMPORTING)` 且 `CREATE_TIME` 早于阈值
（`perf.eval.import.importing-timeout-min:10` 分钟）的批次 → 置 `STATUS=4 +
ERROR_SUMMARY="导入中断或超时，请重传"`。覆盖「JVM 重启 / 实例宕机时正在跑的导入丢失」。

## 5. 前端轮询交互（`Tasks.vue` + `api/eval.js`）

- `importAssign()`：去掉 `responseType: blob` 的 JSON/CSV 分流，改普通 JSON 返回
  `{ batchId, status }`；该请求单独设 `timeout: 60000`（保险，防接口线程解析慢）
- `doImportAssign()`：拿到 `batchId` 后 → 按钮转「导入处理中…」→ 轮询
  `getAssignBatchDetail(batchId)`，间隔 2s，上限 5 分钟：
  - `status===2` → `ElMessage.success('导入成功，共 N 条，请在批次中确认发布')` + 刷新列表 + 关向导
  - `status===4` → `errorSummary` 灌入 `importErrors` 表格展示，提示修正重传
  - 超 5 分钟仍 `IMPORTING` → 提示「仍在处理，可稍后在批次列表查看结果」并停止轮询（后台不受影响）
- 批次列表/详情状态标签增加「处理中(3)/导入失败(4)」展示

## 6. 测试策略（TDD 红线：红 → 绿 → 重构，每步独立 commit）

后端单测（service 层为主）：
- `processImport` 成功 → 批次 `STATUS=2` + `importedCount` 正确 + 明细入库
- `processImport` 行错误 → 批次 `STATUS=4` + `errorSummary` 非空 + 零明细
- `processImport` 真异常 → 批次 `STATUS=4`
- `createImportingBatch` → 批次 `STATUS=3` 落库
- `compensateStaleImporting` → 超时 IMPORTING 置 4，未超时不动
- Controller：返回 `{batchId,status}`、`@BizAuth(EVAL, IMPORT)` 不变

前端：按现有前端测试惯例（无强制单测则手动验证轮询三态）。

## 7. 范围与兼容性

- `/import` 响应体破坏性变更，前端同步改造，无其他调用方（仅 `Tasks.vue` 使用，已确认）
- 不动「草稿 → 确认发布」既有业务流程；不动 `EVAL_ASSIGN_ITEM` 表结构
- 幂等：前端立即拿 batchId 不再盲目重试，超时重复入库问题自然消除；不额外加文件指纹去重（YAGNI）
- `@BizAuth` / `PT_RESOURCE` 资源（`PERF_EVAL_30`）不变，无需新增资源登记
