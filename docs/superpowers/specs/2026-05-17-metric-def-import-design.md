# 指标定义 Excel 导入设计（METRIC_DEF Importer）

**日期**: 2026-05-17
**模块**: performance-engine-center
**状态**: ✅ 已通过用户口头确认（追加约束："指标编号必须唯一" + "任一行失败整批失败"）

## 1. 背景与目标

`docs/指标表上传模板.xlsx` 是业务方提供的银行指标定义导入模板，9 列 88 行示例数据，覆盖
"指标层级 / 名称 / 编号 / 分类 / 来源 / 计算规则 / 定时任务 / 状态"全部维度。当前
`performance-engine-center` 的 `PerfImportService` 框架已支持 `TARGET / BASE_DATA / ALLOC`
3 种导入类型，**缺少 METRIC_DEF（指标定义）导入**。

**目标**：新增 `MetricDefImportStrategy`，复用现有 `POST /api/perf/import/upload?importType=METRIC_DEF`
端点，把 Excel 模板成行批量创建 `PERF_METRIC_DEF` 表记录。

## 2. 用户硬约束

| # | 约束 | 来源 |
|---|---|---|
| H1 | `metric_code` 全表唯一（DB `uk_metric_code` 已强制） | 用户 2026-05-17 |
| H2 | 任一行失败整批 FAILED，返回失败行数与原因（与现有 3 个策略的"行级最大努力"不同） | 用户 2026-05-17 |

## 3. Excel 列 → DDL 字段映射

模板列头与 `PERF_METRIC_DEF` 的精确映射：

| Excel 列 (中文) | DDL 列 | Java 字段 | 取值/转换 |
|---|---|---|---|
| 指标序号 | `val_slot` | `valSlot: Integer` | 直接落库；同时作为 `metric_code` 兜底来源 |
| 指标层级 | `metric_level` | `metricLevel: Integer` | 1 / 2 / 3 |
| 指标名称 | `metric_name` | `metricName: String` | 必填，varchar(200) |
| 指标编号 | `metric_code` | `metricCode: String` | **空** → 按 `M_{val_slot:04d}` 生成 (如 `M_0001`)；**非空** → 直接使用；最终值全表唯一 |
| 指标分类 | `metric_category` (**V1.9 新增**) | `metricCategory: String` | varchar(50) DEFAULT NULL，如"规模类" |
| 指标来源 | `calc_mode` + `calc_logic_type` | 派生 | 1 → MANUAL/EXPR；2/3 → AUTO/SQL |
| 计算规则 | `sql_text` 或 `expr_text` | 见上 | 2/3 → `sql_text`；1 → `expr_text`（可空） |
| 定时任务 | `calc_freq` | `calcFreq: String` | 1→DAY 2→MONTH 3→QUARTER 4→YEAR |
| 指标状态 | `status` | `status: String` | 1→ACTIVE 0→DISABLED |

**Excel 未提供但 DDL 必填字段**（导入时使用默认值）：

| DDL 列 | 默认值 | 备注 |
|---|---|---|
| `base_dim` | `EMP` | 与现有 KPI 卡片场景一致；后续可在导入参数里添加 override |
| `summary_rule` | `SUM` | 默认机构汇总规则 |
| `deleted` | `0` | 软删除标志 |
| `id` | UUID32 | 由 `MetricDefService.generateId` 生成 |

## 4. DDL 变更

`PERF_METRIC_DEF` 新增 `metric_category` 列。

### 4.1 修改 `docs/schema/ddl-performance.sql`

在 `description` 字段之后插入：

```sql
  `metric_category` varchar(50) DEFAULT NULL COMMENT 'V1.9 指标分类（规模类/效益类/质量类/合规类等）',
```

新增索引：
```sql
  KEY `idx_metric_category` (`metric_category`),
```

### 4.2 增量脚本 `docs/superpowers/sql/2026-05-17-perf-metric-def-add-category.sql`

```sql
-- 2026-05-17 V1.9：PERF_METRIC_DEF 新增 metric_category 列
-- 幂等：跑前先备份 `mysqldump onepl PERF_METRIC_DEF > backup.sql`
ALTER TABLE PERF_METRIC_DEF
  ADD COLUMN IF NOT EXISTS metric_category varchar(50) DEFAULT NULL
    COMMENT 'V1.9 指标分类（规模类/效益类/质量类/合规类等）'
    AFTER description,
  ADD INDEX IF NOT EXISTS idx_metric_category (metric_category);
```

## 5. 架构与组件

```
PerfImportController.upload(importType="METRIC_DEF", file, operator)
   └─> PerfImportServiceImpl.startImport
        ├─ insert PerfImportBatch CREATED → RUNNING
        └─> MetricDefImportStrategy.execute (NEW)
             ├─ EasyExcel.read(file).head(MetricDefImportRow.class).doReadSync()
             ├─ buildMetricDefCmds(rows)        # 列值翻译 + metric_code 兜底生成
             ├─ preValidate(cmds)               # 必填 + 文件内 metric_code 重复扫描 + DB 唯一性
             │   └─ 任一错误 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED 携全错列表
             └─ metricDefService.batchCreateMetricDefs(cmds, operator)  # @Transactional
                  └─> for cmd: metricDefService.create(cmd)  # 复用单条 create 业务规则
```

### 5.1 新增组件
| 类 | 包 | 职责 |
|---|---|---|
| `MetricDefImportRow` | `service.importer.model` | EasyExcel 行模型（9 列） |
| `MetricDefImportStrategy implements ImportStrategy` | `service.importer.impl` | importType=`METRIC_DEF`；预扫描 + 整批事务调用 |
| 新增方法 `MetricDefService.batchCreateMetricDefs(List<CreateMetricDefCmd>, String operator)` | `service` | `@Transactional`，逐条调 `create`，任一异常整批回滚 |
| 新增错误码 `IMPORT_BATCH_ALL_OR_NONE_FAILED` (PERF-42211) | `enums.PerfErrorCode` | 整批校验失败语义（含每行原因明细） |

### 5.2 Entity / DTO 字段扩展
| 文件 | 改动 |
|---|---|
| `entity/PerfMetricDef.java` | 新增 `private String metricCategory;` |
| `service/cmd/CreateMetricDefCmd.java` | 新增 `private String metricCategory;` |
| `service/cmd/UpdateMetricDefCmd.java` | 新增（保持完整性） |
| `controller/dto/MetricDefRespDTO.java` | 新增（前端可读取） |
| `controller/dto/CreateMetricReqDTO.java` | 新增（前端可写入） |
| `facade/assembler/MetricAssembler.java` | 透传 metricCategory |
| `mapper/PerfMetricDefMapper.xml` | `BASE_COLUMNS` + `updateByIdSelective` 加 metric_category |

## 6. 错误处理与契约

### 6.1 整文件级错误（抛异常 → 批次 FAILED）
- 文件为空 / 文件不是 xlsx → `IMPORT_COLUMN_MAPPING_INVALID (PERF-42203)`
- 列头不匹配（所有关键字段全 null） → `IMPORT_COLUMN_MAPPING_INVALID (PERF-42203)`
- 预校验阶段：任一行必填缺失、metric_code 重复、`calc_freq` 不在允许枚举内、来源 2/3 但 sql_text 为空 →
  抛 `IMPORT_BATCH_ALL_OR_NONE_FAILED (PERF-42211)`，**所有错误一次性收集**到 message 中
- 落库阶段：单条 DB 异常（并发唯一键冲突等） → 整批事务回滚 → 同样写 FAILED

### 6.2 错误消息格式
预校验失败的 errorSummary 内容（写入 `perf_import_batch.remark`，varchar(4000)）：

```
共3行失败; 第2行: metric_code 必填; 第5行: metric_code 重复(文件内): M_0001; 第88行: calc_freq 非法: 5
```

### 6.3 成功路径
- 全部行成功 → `status=SUCCESS`, `totalRows=N`, `successRows=N`, `errorRows=0`, `remark=null`
- 整批失败 → `status=FAILED`, `totalRows=N`, `successRows=0`, `errorRows=K` (K=错误明细行数), `remark=上述格式`

## 7. 测试矩阵 (TDD)

### 7.1 单元测试 (`MetricDefImportStrategyTest`)
| # | Case | 期望 |
|---|---|---|
| U1 | 模板 fixture 88 行全合法 → 调用 `service.batchCreateMetricDefs` 1 次，cmd 列表 size=88 | Green |
| U2 | metric_code 为空 → 按 `M_{序号:04d}` 自动填充 | M_0001 / M_0002 |
| U3 | 文件内 metric_code 重复 → 抛 `IMPORT_BATCH_ALL_OR_NONE_FAILED`，message 包含两行号 | Red |
| U4 | 来源=1，calc_mode=MANUAL, calc_logic_type=EXPR | |
| U5 | 来源=2/3，calc_mode=AUTO, calc_logic_type=SQL，sql_text 必填校验 | |
| U6 | 定时任务=5（越界） → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED | |
| U7 | 来源=2 但 sql_text 为空 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED | |
| U8 | 整批校验通过但 service.batchCreateMetricDefs 抛 DuplicateKey → 透传，外层处理 | |

### 7.2 集成测试 (`MetricDefImportStrategyIT extends PerformanceMapperTestBase`)
| # | Case | 期望 |
|---|---|---|
| I1 | 全合法模板 88 行 → DB 88 行新建 + batch.status=SUCCESS | |
| I2 | 第 5 行 metric_code=空 + 序号也为空 → 整批回滚 0 行入库 + batch.status=FAILED + remark 含"第5行" | |
| I3 | DB 已存在 metric_code=M_0001 → 整批失败，DB 行数不变 | |
| I4 | 88 行合法 + 1 行 metric_level 越界 → 整批失败 | |

### 7.3 端到端
- 用 `docs/指标表上传模板.xlsx` 作为测试 fixture（拷贝到 `src/test/resources/fixtures/metric-def-import-template.xlsx`）

## 8. 鉴权与审计

- **复用现有 `PerfImportController.upload`**，已含 `@BizAuth(bizType=PERF_CONFIG, action=IMPORT)` + `@AuditLog`
- 无需新增 PT_RESOURCE（`P_PERF_IMP_UPLOAD` 已在 V1.1 注册时覆盖）
- 高危：导入会批量创建指标定义，但 `@AuditLog(action="PERF_IMPORT_UPLOAD", resourceType="PERF_IMPORT_BATCH")`
  已记录批次 ID，每条指标后续通过 audit 重放可追溯

## 9. 风险与边界

| 风险 | 缓解 |
|---|---|
| Excel 88 行 → 88 次 INSERT 在同一事务，长事务 | 88 行典型规模可控；超过 1000 行的导入应改用批量 `INSERT ... VALUES (...), (...)`，本期不优化 |
| 并发上传重复文件 → 第二次整批失败 | 业务可接受语义；MD5 幂等保留到后续迭代 |
| `val_slot` 与 `MetricSlotService.allocSlot` 冲突 | Excel 序号直接做 valSlot 会与 slot 自动分配冲突；本期策略：导入时 **绕过 allocSlot**，直接使用 Excel 提供的序号写入 val_slot；如果序号 0 或与 base_dim 内已有 slot 冲突，整批失败 |
| metric_category 列上线前，调用方不识别 | 新列 DEFAULT NULL，向后兼容；老代码读取自动得到 null |

## 10. 上线步骤

1. 在 onepl 库执行 `docs/superpowers/sql/2026-05-17-perf-metric-def-add-category.sql`
2. 部署新版本 jar（含 MetricDefImportStrategy + Entity 扩展）
3. 前端联调：上传 `指标表上传模板.xlsx` 验证 88 行入库
4. 部署后烟雾测试：随便挑 1 行编辑，验证 metric_category 字段保存正确

## 11. 不在本期范围内的项

- ❌ 增量"更新已有指标"（UPSERT 语义）：本期只做 INSERT，重名则失败
- ❌ Excel 同时含"指标定义 + 指标值"两个 sheet（仅 sheet 1）
- ❌ 在线下载已填充 metric_code 的模板
- ❌ 上传后自动触发 KPI 方案重新发布
