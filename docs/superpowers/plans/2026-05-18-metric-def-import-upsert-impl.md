# 2026-05-18 指标定义 Excel 导入改造为 upsert 实现计划

**Spec**: `docs/superpowers/specs/2026-05-18-metric-def-import-upsert-design.md`

## 总体节奏

严格 TDD：每个子任务先写失败测试（Red）→ 写最简实现（Green）→ 重构（Refactor）→ 独立 commit。

预估增量：~12 case 新增 + ~3 case 改造，单 phase 内的 commit 数 = Red + Green + 可选 Refactor。

## Phase 1 — DDL 与 Mapper 基础

### P1.1 编写 SQL 文件（非代码任务）
- 新建 `docs/superpowers/sql/2026-05-18-perf-metric-def-name-unique-and-upsert-cols.sql`
- 内容含 spec §1 全部三步：预检 + 重名清理 + 加唯一键 + 加 updated_rows 列
- 在本地 `onepl` 库执行（验证幂等）

### P1.2 PerfMetricDefMapper.selectByMetricName（Red → Green）
- Red: `PerfMetricDefMapperIT.selectByMetricName` 3 case（命中 / 未命中 null / deleted=1 行返回 null）
- Green: Mapper 接口加方法 + XML 加 `<select>`
- 注：本期 IT 跑 surefire 或 failsafe 由现有约定决定（mapper 测试已绿 → 跟既有命名 `*IT` 走 failsafe）

### P1.3 PerfImportBatch 加 updatedRows 字段
- Entity 加 `private Integer updatedRows`
- `PerfImportBatchMapper.xml` BASE_COLUMNS + insert + selectByBatchId resultMap 加 `updated_rows`
- `updateCounts` SQL 与签名加 `updated_rows = #{updatedRows}` 参数
- Red: `PerfImportBatchMapperIT.updateCounts_includesUpdatedRows` 1 case
- Green: 实现

## Phase 2 — Service 层 upsert

### P2.1 UpsertResult / BatchUpsertResult value class
- 包 `com.bank.branch.platform.performance.service.cmd` 或 sibling
- `UpsertResult { boolean inserted; PerfMetricDef def; }` — Lombok @Value 或 @Data
- `BatchUpsertResult { int insertedRows; int updatedRows; List<PerfMetricDef> defs; }`
- 不带行为，纯数据载体，**不需要**单独测试

### P2.2 MetricDefService.upsertByName Green-only（Red → Green）
- Red: `MetricDefServiceUpsertTest` 3 case
  - 不命中 → 走 create 路径，inserted=true，def.metric_code = cmd.metricCode
  - 命中 → 走 update 路径，inserted=false，def.metric_code = **原 DB 值**（不是 cmd 里的）
  - 命中 → val_slot 保留 DB 原值
- Green: 实现 upsertByName，命中路径调用既有 `updateByIdSelective` + 复用 `getByCodeOrNull`
- 关键：更新路径**不**走 `metricSlotService.allocSlot`、**不**走 `metricCycleDetectService.checkNoCycle`（除非 cmd 显式带 refMetricCodes，这是 V1.11 范围外）

### P2.3 MetricDefService.batchUpsertByName（Red → Green）
- Red: `MetricDefServiceUpsertTest` 4 case
  - 全 5 行新增 → insertedRows=5, updatedRows=0
  - 全 5 行更新 → insertedRows=0, updatedRows=5
  - 混合 3+2 → insertedRows=3, updatedRows=2
  - 第 3 行抛 PerfException → 整批回滚（前 2 行不可见）
- Green: 实现 batchUpsertByName，整批 `@Transactional`
- 旧 `batchCreateMetricDefs` 加 `@Deprecated` javadoc，**不**删除（保留兼容）

## Phase 3 — ImportResult / DTO 扩展

### P3.1 ImportResult 加 updatedRows
- 字段 + getter/setter（Lombok @Data 已含）
- 既有 3 构造器 (`(0,0,0,null)` / `(rows.size(), rows.size(), 0, null)`) 需要补一个参数
- 所有调用方（4 个 ImportStrategy）补 0 默认值
- 编译 + 跑现有测试不破即 Green

### P3.2 PerfImportBatchRespDTO 加 updatedRows / insertedRows
- DTO 加两字段：`updatedRows`（来自实体）+ `insertedRows`（派生：`successRows - updatedRows`）
- `PerfImportServiceImpl.getBatchDto` 装配时计算 insertedRows
- Red: `PerfImportServiceImplTest`（如有）或 controller IT 加 1 case
- Green: 装配代码

### P3.3 新增 PerfImportUploadRespDTO
- 包 `com.bank.branch.platform.performance.controller.dto`
- 字段：batchId / totalRows / insertedRows / updatedRows / errorRows
- 无单独测试（DTO 由 controller IT 覆盖）

## Phase 4 — ImportStrategy 改造

### P4.1 MetricDefImportStrategyTest 改造 + 新增（Red）
- **改造**既有用例：
  - `execute_dbCodeExists_throws` → 改名 `execute_dbNameExists_goesUpdatePath`，断言 service.batchUpsertByName 调用，**不**抛异常
  - `execute_allValid_callBatchOnce` → 改断言为 `batchUpsertByName` + `result.updatedRows == 0`
  - 其他 9 case 把 `verify(metricDefService).batchCreateMetricDefs(...)` 改为 `batchUpsertByName(...)` 即可
- **新增**用例：
  - `execute_mixed3insert2update_counts` → mock service 返回 `BatchUpsertResult(3, 2, ...)`，断言 ImportResult.totalRows=5/successRows=5/updatedRows=2/errorRows=0
  - `execute_duplicateMetricNameInFile_throws` → 文件内 2 行 metric_name="A" → 整批失败
  - `execute_allUpdate_countsCorrect` → mock 全部命中

### P4.2 MetricDefImportStrategy.execute 实现（Green）
- 删除"DB metric_code 已存在 → 抛"分支
- 新增"文件内 metric_name 重名检测"，对齐既有 metric_code 检测的行号报错风格
- 调用 `batchUpsertByName` 替换 `batchCreateMetricDefs`
- 把 BatchUpsertResult 映射到 ImportResult（successRows = insertedRows+updatedRows，updatedRows = result.updatedRows）

## Phase 5 — PerfImportService 流程衔接

### P5.1 PerfImportServiceImpl.startImport 写 updatedRows（Red → Green）
- Red: `PerfImportServiceImplTest` 新增 1 case，mock 策略返回 `ImportResult(5,5,0,2,null)`，断言 `batchMapper.updateCounts` 调用参数 updatedRows=2
- Green: 改 `updateCounts(id, total, success, error)` 为 5 参数版本，透传 updatedRows

### P5.2 startImport 返回 PerfImportUploadRespDTO 还是 batchId？
- **决策**：保持 `startImport(...)` 返回 `String batchId`（被 Controller 重复调用），新增 `getUploadResp(batchId)` 一站式装配 DTO
- 或者：Controller 内 `String batchId = service.startImport(...)`; `PerfImportBatch b = service.getBatch(batchId)`; 装配 DTO 返回
- 选第二种（更少改动 service 接口）

### P5.3 PerfImportController.upload 改返回 DTO（Red → Green）
- Red: `PerfImportControllerIT.upload_returnsRespDTO`（或 unit test with MockMvc）
- Green: Controller 改返回类型为 `ResponseWrapper<PerfImportUploadRespDTO>`，body 含 5 字段

## Phase 6 — 验证与收尾

### P6.1 全模块 surefire + 主要 failsafe 跑通
- `mvn clean install -DskipTests`（确保上游模块 stale jar 不影响）
- `mvn test -pl performance-engine-center`
- `mvn verify -pl performance-engine-center`（如本地 MySQL 配置允许）
- 任一红 → 回到对应 Phase 修复

### P6.2 更新模块 CLAUDE.md
- `performance-engine-center/CLAUDE.md` 顶部加 V1.11 章节
- 更新依赖图、模块状态表（如需）
- 累计测试 case 数更新

### P6.3 Commit 历史 review
- 每个 Red commit + Green commit 独立
- commit message 中文，遵循"perf: V1.11 xxx (TDD Red/Green)"格式
- code-reviewer 友好

## 命令清单

```bash
# 跑单文件单测
mvn test -pl performance-engine-center -Dtest=MetricDefServiceUpsertTest

# 跑单文件 IT
mvn verify -pl performance-engine-center -Dit.test=PerfMetricDefMapperIT -DfailIfNoTests=false

# 全模块 surefire
mvn test -pl performance-engine-center

# 全模块 surefire + failsafe
mvn verify -pl performance-engine-center
```

## 风险与回退

- 任一 Phase Red 写不出来 → 暂停，回 spec 重新确认契约
- Service 层重构若发现 `metricCycleDetectService` 必须重建图 → P2.2 增加额外 case 守护，但不扩范围
- Controller 响应破坏性变更若前端来不及联动 → 临时回退仅返回 batchId，DTO 字段下版本上线（spec § 风险 R2 已预警）
