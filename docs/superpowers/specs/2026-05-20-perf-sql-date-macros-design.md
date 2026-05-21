# 指标 SQL 日期宏变量设计

- 状态：Draft，2026-05-20
- 模块：performance-engine-center
- 受影响：`MetricCalcService` / `MetricTrialService` / `xanzc_frontend/views/perf/Metrics.vue`

## 1. 背景与目标

当前指标 `sql_text` 仅自动注入两个命名参数：`:dataDate`（`LocalDate`）和 `:version`（`String`）。
要计算"上月末余额、本年初至今累计、上季度对比"等典型口径，业务编写者必须手写 MySQL 函数：
```sql
WHERE stat_date BETWEEN DATE_FORMAT(DATE_SUB(:dataDate, INTERVAL 1 MONTH), '%Y-%m-01')
                    AND LAST_DAY(DATE_SUB(:dataDate, INTERVAL 1 MONTH))
```

问题：
1. 业务人员往往不熟悉 MySQL 日期函数，容易把"上月底"写错（常见错误：忘记 `LAST_DAY`、把当月 1 号当成上月底）。
2. 同一口径分散在多个指标的 sql_text 里，难以统一勘误。
3. 试运行 / 调度 / 历史回算需要相同语义，重复实现风险高。

**目标**：在 `:dataDate` 锚点之上提供 8 个**强类型 `LocalDate` 命名参数**，作为统一日期口径来源；
SQL 编写者只用 `:dateXxx` 即可，无需写 MySQL 日期函数。

## 2. 8 个日期宏（驼峰 `:name`）

所有宏以请求传入的 `dataDate` 为锚点（生产路径来自 `MetricCalcService.calcMetric` 入参，试运行路径来自请求体 `dataDate` 字段；缺省走 trial 的"昨天"默认）。

| SQL 占位符 | 含义 | 计算规则（伪 Java） |
|---|---|---|
| `:dateToday`          | 当前日期 T            | `dataDate` |
| `:dateYesterday`      | 上一日期 T-1          | `dataDate.minusDays(1)` |
| `:dateMonthEnd`       | 本月最后一天          | `dataDate.with(lastDayOfMonth())` |
| `:datePrevMonthEnd`   | 上月最后一天          | `dataDate.withDayOfMonth(1).minusDays(1)` |
| `:dateQuarterEnd`     | 本季度最后一天        | `endOfMonth(year, quarter*3)`，quarter = `(month-1)/3 + 1` |
| `:datePrevQuarterEnd` | 上季度最后一天        | `firstDayOfCurrentQuarter.minusDays(1)`（跨年自动到去年 12-31） |
| `:dateYearEnd`        | 本年最后一天（12-31） | `LocalDate.of(year, 12, 31)` |
| `:datePrevYearEnd`    | 上年最后一天（12-31） | `LocalDate.of(year-1, 12, 31)` |

**类型**：全部 `java.time.LocalDate`，由 `NamedParameterJdbcTemplate` 通过 `MapSqlParameterSource` 绑定到 MySQL `DATE`。

**边界用例**（必须有单测）：
- 闰年 2-29：`dataDate=2024-02-29` → `dateMonthEnd=2024-02-29`, `datePrevMonthEnd=2024-01-31`, `dateYearEnd=2024-12-31`
- 季初：`dataDate=2026-01-15` → `dateQuarterEnd=2026-03-31`, `datePrevQuarterEnd=2025-12-31`, `datePrevYearEnd=2025-12-31`
- 季末：`dataDate=2026-03-31` → `dateQuarterEnd=2026-03-31`（含当日）
- 年初：`dataDate=2026-01-01` → `dateYesterday=2025-12-31`, `datePrevYearEnd=2025-12-31`

## 3. 架构

```
请求带 dataDate
     │
     ▼
MetricCalcService.executeSqlAndPersist            MetricTrialService.runSql
     │                                                  │
     │  params = {dataDate, version}                    │  mergedParams = userParams ⨁ {dataDate}
     │             ▼                                    │             ▼
     └──► DateMacroResolver.resolve(dataDate) ◄─────────┘
                   │
                   ▼
          注入 8 个 :dateXxx 到 params
                   │
                   ▼
          SqlExecutor.execute(sql, params, timeout)
```

**新组件**：`DateMacroResolver`（无状态 utility，纯函数）
- 位置：`performance-engine-center/.../service/engine/DateMacroResolver.java`
- 方法：`public static Map<String, LocalDate> resolve(LocalDate base)`
- 返回有序 `LinkedHashMap`（便于日志可读），key 顺序与上表一致。

**注入点**：两条路径在调 `sqlExecutor.execute(...)` **之前**调用 `DateMacroResolver.resolve(dataDate)`，结果合并进 params。

**覆盖规则**（重要安全约束）：
- 生产路径：`params.putAll(DateMacroResolver.resolve(dataDate))`——宏值由系统计算，**用户无法覆盖**（生产路径本就没有用户传参渠道）。
- 试运行路径：先 `putAll(userParams)` 再 `putAll(macros)`——**系统宏优先级最高**，用户即使在 trial 请求 `params` 字段里塞 `dateToday=2099-01-01` 也会被覆盖。这是审计/合规要求：禁止 trial 端通过参数注入跳过宏的真实计算。

## 4. SQL 使用示例

**例 1：当月日均存款（月初到 T）**
```sql
SELECT emp_id           AS base_key,
       AVG(dep_bal_amt) AS metric_value
FROM source_deposit_daily
WHERE stat_date BETWEEN DATE_FORMAT(:dateToday, '%Y-%m-01') AND :dateToday
GROUP BY emp_id;
```

**例 2：上月底余额**
```sql
SELECT cust_id AS base_key, dep_bal AS metric_value
FROM cust_deposit_snapshot
WHERE snap_date = :datePrevMonthEnd;
```

**例 3：本年累计中收（YTD）**
```sql
SELECT emp_id AS base_key, SUM(fee_amt) AS metric_value
FROM fee_income_daily
WHERE stat_date BETWEEN DATE_FORMAT(:dateToday, '%Y-01-01') AND :dateToday
GROUP BY emp_id;
```

**例 4：同比上季度对比（上季底 vs 本季底快照）**
```sql
SELECT cur.emp_id AS base_key,
       (cur.bal - pre.bal) AS metric_value
FROM emp_kpi_snapshot cur
LEFT JOIN emp_kpi_snapshot pre
  ON pre.emp_id = cur.emp_id AND pre.snap_date = :datePrevQuarterEnd
WHERE cur.snap_date = :dateQuarterEnd;
```

## 5. 错误处理

- **SQL 引用不存在的宏**（拼写错误 `:dateMontEnd`）：`NamedParameterJdbcTemplate` 抛
  `InvalidDataAccessApiUsageException: No value supplied for the SQL parameter 'dateMontEnd'`，
  被 `SqlExecutorImpl` 的 `DataAccessException` catch 翻译成 `PERF-42201 METRIC_CALC_LOGIC_INVALID`，
  错误消息透传"No value supplied for ..."。**无需新增错误码**。
- **`dataDate=null`**：`DateMacroResolver.resolve(null)` 抛 `IllegalArgumentException`，由调用层 fail-fast；
  生产路径 `calcMetric` 已强制 `dataDate` 必填，trial 路径会兜底为"昨天"（见 `Metrics.vue`），不会传 null。
- **不引入新的 PerfErrorCode**——所有失败复用现有 PERF-42201。

## 6. 前端配套

`xanzc_frontend/src/views/perf/Metrics.vue` 指标编辑对话框 SQL 编辑器**右侧**增一块只读提示卡：

```
可用日期变量（由系统按 dataDate 计算）：
  :dateToday          当前日期 T
  :dateYesterday      T-1
  :dateMonthEnd       本月底
  :datePrevMonthEnd   上月底
  :dateQuarterEnd     本季底
  :datePrevQuarterEnd 上季底
  :dateYearEnd        本年底
  :datePrevYearEnd    上年底

固定参数：
  :dataDate           数据日期（=dateToday）
  :version            sys_control 当前版本
```

**不做**（YAGNI）：
- 不做 monaco-editor 自动补全/语法高亮（成本远大于收益，可后续做）。
- 不做"点击插入"按钮（提示卡足够，业务人员复制即可）。
- 不做 SQL 实时校验。

## 7. 文档同步

- `docs/modules/performance-engine-center/03-接口设计与报文.md`：在指标 SQL 段落新增"附录 §M：SQL 日期宏变量"，列 8 个宏 + 计算规则表 + 4 个示例。
- `performance-engine-center/CLAUDE.md`：在"V1.x 微调"节登记一行。

## 8. 测试矩阵

| 文件 | 用例数 | 覆盖点 |
|---|---|---|
| `DateMacroResolverTest`（surefire） | 6 | 闰年 / 季初 / 季末 / 年初 / 普通日 / null 抛错 |
| `MetricTrialServiceTest` 新增 | 2 | a) 用户 params 与 macros 共存，宏不被 override；b) SQL 里引用 `:datePrevMonthEnd` 正确替换 |
| `MetricCalcServiceTest` 新增 | 1 | 调度路径自动注入 8 个宏到 params |
| `SqlExecutorImplIT`（failsafe，已有） | 不新增 | 现有 IT 即可覆盖 NamedParameterJdbcTemplate 行为 |

**预计净增**：9 个 case。

## 9. 不在本期范围（明确 YAGNI）

- **时间宏**（小时/分钟级，如 `:timestampNow`）：业务无此口径需求。
- **基于"今天"而非 `dataDate` 的宏**（如 `:wallClockToday`）：会破坏回算一致性，禁止。
- **自定义宏**（让业务在指标定义里声明宏）：增加复杂度且与"统一口径"目标冲突。
- **`${MACRO}` 文本宏**（方案 B）：与 `:name` 双轨制带来歧义，明确不做。
- **monaco-editor 自动补全**：留待后续 UX 提案，本期只做静态提示卡。

## 10. 兼容性 / 回滚

- 纯增量功能，**不破坏现有 SQL**（仍可用 MySQL 日期函数从 `:dataDate` 派生）。
- 现存 sql_text 若历史上凑巧有 `:dateXxx` 占位符——已 grep `xanzc_frontend/src` + `performance-engine-center/src` 与 `yiti.PERF_METRIC_DEF` / `onepl.PERF_METRIC_DEF` 的 `sql_text` / `subject_sql` 列（regexp `:date[A-Za-z]`），双库 0 命中，**无冲突**。
- 回滚：单纯把 `DateMacroResolver.resolve` 改为返回空 Map 即可（保持调用点结构），SQL 里没用到宏的指标完全不受影响。
