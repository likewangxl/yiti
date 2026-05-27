# 2026-05-18 指标定义 Excel 导入改造为按名称 upsert 设计

## 背景

V1.9（2026-05-17）交付的指标定义 Excel 导入策略 `MetricDefImportStrategy` 采用**整批 all-or-none** 语义：以 `metric_code` 为唯一匹配键，遇到与 DB 已有 `metric_code` 冲突时整批回滚。业务侧反馈该语义不便于"周期性补录、批量微调"，希望改为：

1. `PERF_METRIC_DEF.metric_name`（指标中文名称）作为业务唯一键
2. 导入时按 `metric_name` 命中已有指标 → **更新**，未命中 → **新增**
3. 上传响应返回 **新增笔数 + 更新笔数 + 总行数**

## 范围

- performance-engine-center 模块
- 仅影响 `importType = "METRIC_DEF"` 的导入路径，**不**影响 TARGET / BASE_DATA / ALLOC 三个既有策略（行级最大努力语义保留）
- `POST /api/perf/import/upload` 响应破坏性变更（METRIC_DEF 类型从返回 `String batchId` 变为返回 `PerfImportUploadRespDTO`），前端需联动调整

## 设计

### 1. DDL 变更

**SQL 文件**：`docs/superpowers/sql/2026-05-18-perf-metric-def-name-unique-and-upsert-cols.sql`

```sql
-- 步骤 1：现网重复 metric_name 排查 + 清理
--   规则：deleted=0 中按 metric_name 分组，COUNT(*) > 1 视为重复
--   清理策略：保留 created_time 最小（最早创建）的一行，其余软删除 (deleted=1)
--   说明：业务侧明确"如有重复，删除重复记录"。最早创建者优先以保护既有引用关系。

-- 1.1 预检：先看哪些行将被影响（DBA 跑这条评估）
SELECT metric_name, COUNT(*) AS cnt, GROUP_CONCAT(id) AS ids,
       GROUP_CONCAT(metric_code) AS codes
  FROM PERF_METRIC_DEF
 WHERE deleted = 0
 GROUP BY metric_name
HAVING COUNT(*) > 1;

-- 1.2 清理重复（保留 created_time 最早 + 同 created_time 时 id 字典序最小）
UPDATE PERF_METRIC_DEF d
  JOIN (
        SELECT t.metric_name,
               (SELECT id FROM PERF_METRIC_DEF
                 WHERE metric_name = t.metric_name AND deleted = 0
                 ORDER BY created_time ASC, id ASC
                 LIMIT 1) AS keep_id
          FROM PERF_METRIC_DEF t
         WHERE t.deleted = 0
         GROUP BY t.metric_name
        HAVING COUNT(*) > 1
       ) dup ON d.metric_name = dup.metric_name
   SET d.deleted = 1,
       d.updated_time = NOW(),
       d.updated_by = 'V1.11_DEDUP'
 WHERE d.deleted = 0
   AND d.id <> dup.keep_id;

-- 步骤 2：加唯一索引（按"未软删除行唯一"语义，对齐既有 uk_base_dim_slot_alive 函数索引风格）
ALTER TABLE PERF_METRIC_DEF
  ADD UNIQUE KEY `uk_metric_name_alive` ((IF(deleted=0, metric_name, NULL)));

-- 步骤 3：PERF_IMPORT_BATCH 加 updated_rows 列
ALTER TABLE PERF_IMPORT_BATCH
  ADD COLUMN `updated_rows` INT NOT NULL DEFAULT 0 COMMENT '更新行数（V1.11：仅 METRIC_DEF 导入使用，其他类型恒 0）'
  AFTER `error_rows`;
```

**回滚 SQL**：

```sql
ALTER TABLE PERF_METRIC_DEF DROP KEY `uk_metric_name_alive`;
ALTER TABLE PERF_IMPORT_BATCH DROP COLUMN `updated_rows`;
-- 重复行清理无法逆向（软删除可手工 UPDATE deleted=0 回滚单行，但需 DBA 介入）
```

### 2. Mapper 层

**`PerfMetricDefMapper` 新增**：

```java
/** V1.11：按指标名称查询（deleted=0 过滤，对齐 selectByMetricCode 语义）. */
PerfMetricDef selectByMetricName(@Param("metricName") String metricName);
```

XML：

```xml
<select id="selectByMetricName" resultType="com.bank.branch.platform.performance.entity.PerfMetricDef">
    SELECT <include refid="BASE_COLUMNS"/>
    FROM PERF_METRIC_DEF
    WHERE metric_name = #{metricName} AND deleted = 0
    LIMIT 1
</select>
```

**`PerfImportBatchMapper.updateCounts` 签名扩展**：

```java
int updateCounts(@Param("id") String id,
                 @Param("totalRows") int totalRows,
                 @Param("successRows") int successRows,
                 @Param("errorRows") int errorRows,
                 @Param("updatedRows") int updatedRows);  // V1.11 新增
```

### 3. Service 层

**`MetricDefService.upsertByName` 新增**：

```java
/**
 * V1.11：按 metric_name 命中执行 upsert.
 *
 * <p>命中已存在指标（deleted=0）→ updateByIdSelective，保留 DB 原 id / metric_code，
 * 仅更新业务字段（metric_desc / calcFreq / calcMode / calcLogicType / sqlText / exprText /
 * summaryRule / metricCategory / status / metricLevel / refMetricCodes 等）。
 *
 * <p>未命中 → 走现有 create() 路径。
 *
 * @param cmd      命令（包含 metric_name 等 Excel 翻译后字段）
 * @param operator 操作人
 * @return UpsertResult { inserted: boolean, def: PerfMetricDef }
 */
@Transactional(rollbackFor = Exception.class)
public UpsertResult upsertByName(CreateMetricDefCmd cmd, String operator) { ... }
```

**`MetricDefService.batchUpsertByName` 新增**：

```java
/**
 * V1.11：批量按 metric_name upsert（整批事务）.
 *
 * <p>调用方契约同 batchCreateMetricDefs：任一行 DB 异常整批回滚.
 *
 * @return BatchUpsertResult { insertedRows, updatedRows, defs }
 */
@Transactional(rollbackFor = Exception.class)
public BatchUpsertResult batchUpsertByName(List<CreateMetricDefCmd> cmds, String operator) { ... }
```

**关键约束**：
- 更新路径**保留**原 `metric_code` / `id` / `val_slot`，**不**重新分配 slot
- 更新路径**不**触发 `metricCycleDetectService` 全图重建（仅更新 ref 关系时按需调用）
- 更新后 `registerSchedulerHookIfNeeded(def, false)` 仍触发调度同步（状态可能从 ACTIVE → DISABLED）
- 旧 `batchCreateMetricDefs` 保留为 deprecated 入口（无现存调用方后可删除）

### 4. ImportStrategy 改造

`MetricDefImportStrategy.execute` 流程变更：

```
旧流程（V1.9）：
  parseRows → translateRow (基础校验)
  → 文件内 metric_code 重复检测
  → DB metric_code 已存在检测 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED
  → batchCreateMetricDefs

新流程（V1.11）：
  parseRows → translateRow (基础校验)
  → 文件内 metric_name 重复检测 (新)
  → 文件内 metric_code 重复检测 (保留，避免 uk_metric_code 冲突)
  → batchUpsertByName (按名称匹配，命中改 update 路径)
```

**保留**：基础格式校验失败（必填、枚举、scheduleType 越界等）仍走 `IMPORT_BATCH_ALL_OR_NONE_FAILED` 整批失败。这是数据**质量**问题，不是**冲突**问题。

**删除**：DB `metric_code` 已存在 → 整批失败（语义上被 upsert 取代）。

### 5. ImportResult / 响应 DTO

**`ImportResult` 加 `updatedRows` 字段**：

```java
public class ImportResult {
    private int totalRows;
    private int successRows;   // = insertedRows + updatedRows
    private int errorRows;
    private int updatedRows;   // V1.11 新增；其他策略恒 0
    private String errorSummary;
}
```

**`PerfImportBatch` entity 加 `updatedRows`**（持久化到 DB 新列）

**`PerfImportBatchRespDTO` 加 `updatedRows`** + **新派生字段 `insertedRows = successRows - updatedRows`**（DTO 装配时计算，不入库）

**新增 `PerfImportUploadRespDTO`**（仅 METRIC_DEF 路径使用）：

```java
@Data @Builder
public class PerfImportUploadRespDTO {
    private String batchId;
    private Integer totalRows;
    private Integer insertedRows;
    private Integer updatedRows;
    private Integer errorRows;
}
```

**`PerfImportController.upload` 响应类型从 `ResponseWrapper<String>` 改为 `ResponseWrapper<PerfImportUploadRespDTO>`**。

> ⚠️ **破坏性变更**：前端 `POST /api/perf/import/upload` 调用方从 `data: string` 改为 `data: { batchId, totalRows, insertedRows, updatedRows, errorRows }`。V1.9 上线时间近，影响可控。

### 6. 错误码

`PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED` 语义保留（基础格式校验 + 文件内重名两类）。**无新增**错误码。

## 测试矩阵（TDD 红-绿-重构）

| 层 | 测试 | 用例 |
|---|---|---|
| Mapper | `PerfMetricDefMapperIT.selectByMetricName` | 命中存在行 / 未命中 null / 软删除行返回 null（3 case） |
| Service | `MetricDefServiceUpsertTest.upsertByName` | 新增路径（不命中） / 更新路径保留原 metric_code / 更新路径不重分 slot（3 case） |
| Service | `MetricDefServiceUpsertTest.batchUpsertByName` | 全新增 / 全更新 / 混合 3 insert + 2 update 计数正确 / 任一行抛整批回滚（4 case） |
| Strategy | `MetricDefImportStrategyTest` 改造 + 新增 | 全新增 → insertedRows=N, updatedRows=0；全更新 → insertedRows=0, updatedRows=N；混合 → 计数正确；文件内 metric_name 重名 → 整批失败（4 case 新增 + 既有 11 case 中"DB metric_code 已存在 → 整批失败"用例改为"DB metric_name 已存在 → 走更新路径"） |
| Controller | `PerfImportControllerIT.upload` | METRIC_DEF 上传响应字段齐全（1 case） |

预估总增量：~12 case 新增 + ~3 case 改造，全模块 surefire 不破。

## 风险与约束

- **R1**：现网 metric_name 重复行清理由 SQL 步骤 1.2 自动软删除"保留最早创建者"，业务侧应在执行前用步骤 1.1 预检确认影响范围
- **R2**：响应 DTO 破坏性变更，前端需联动；上线窗口建议与前端同步发布
- **R3**：旧 `batchCreateMetricDefs` 标 deprecated 但保留方法体，等下个版本确认无引用后再删除
- **R4**：更新路径保留原 `val_slot` —— 如业务后续需要导入触发 slot 变更，应走指标管理 UI 而不是导入

## 上线步骤

1. DBA 执行预检 SQL（步骤 1.1），核对重复 metric_name 影响范围与业务侧确认
2. DBA 执行清理 + DDL（步骤 1.2 / 2 / 3）
3. 部署后端（含本期代码）
4. 前端联动发布（适配新响应 DTO）
5. 业务侧验证一轮 Excel 导入

## 相关文档

- V1.9 原 spec: `docs/superpowers/specs/2026-05-17-metric-def-import-design.md`
- 模块 CLAUDE.md: `performance-engine-center/CLAUDE.md`
- 共通开发规范: `docs/common-dev-guide.md`
