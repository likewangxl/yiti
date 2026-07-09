# 引用指标「取值时间」设计方案

- 日期：2026-07-09
- 模块：performance-engine-center
- 主题：二级/三级指标公式引用一级指标结果时，可为**每一次引用**单独指定取值时间（今日/昨日/上月末/上季末/上年末）
- 相关代码：`service/MetricCalcService.java`、`service/engine/DateMacroResolver.java`、`service/engine/GroovyExecutorImpl.java`、`service/MetricCycleDetectService.java`

## 1. 背景与目标

现有 EXPR/GROOVY 型指标（二/三级）计算时，公式里引用的每个 `M_xxx` 指标值**只能取当日（dataDate）**的宽表结果（见 `MetricCalcService.loadRefValuesBySlotMap:390`，取值固定用 `dataDate`）。业务实际需要按不同时间点取历史结果，典型：

- 指标A今日 − 指标A昨日（日环比差额）
- 取上月末 / 上季末 / 上年末的余额类指标做同比、环比

**目标**：让操作人在配置公式引用某个指标时，能从下拉里选定"取值时间"，计算引擎据此到指标结果宽表读对应日期的历史行，参与公式运算，结果写回本指标宽表。

## 2. 范围与非目标

**范围**
- 作用对象：EXPR/GROOVY 指标公式对**其他指标结果**（`emp/org/cust_index_result` 宽表历史行）的引用。
- 取值时间：固定 5 档枚举（见 §3）。

**非目标（本期不做）**
- SQL 一级指标查业务源表的日期（`:dateYesterday` 等宏已支持，不在本期）。
- 任意偏移（T-N、近 N 月末等自定义偏移）——仅固定枚举。
- **自引用递归**（指标引用自身历史值，如无界累计 `X_今日 = X_昨日 + 增量`）。环检测 `MetricCycleDetectService` **保持现状**，一个指标引用自身（含带历史取值时间）继续按环拦截。跨时间需求统一用"另建一个引用他人历史值的指标"表达，例如 `C(今日) = B(昨日) + A(今日)`，C 引用 B、A，均非 C 自身，无环。

## 3. 取值时间枚举（复用现有日期宏，零新增日期数学）

锚点 = 本指标计算的数据日期 `dataDate`。5 档直接映射 `DateMacroResolver.resolve(dataDate)`（`DateMacroResolver.java:34`）已算好的锚点：

| 取值时间 | token 后缀 | 复用的宏 key | 语义 |
|---|---|---|---|
| 今日（默认） | 无 | `dateToday` | dataDate 当日 |
| 昨日 | `__D1` | `dateYesterday` | dataDate − 1 天 |
| 上月末 | `__PME` | `datePrevMonthEnd` | 上月最后一天 |
| 上季末 | `__PQE` | `datePrevQuarterEnd` | 上季度最后一天 |
| 上年末 | `__PYE` | `datePrevYearEnd` | 上年 12/31 |

本期把 `DateMacroResolver` 从"仅 SQL 路径使用"扩展到 EXPR 路径共用，不新增日期推导逻辑。

## 4. Token 语法与归一化

- **落库规范 token**（存进 `PerfMetricDef.exprText`，必须是合法 Groovy 标识符）：
  - `M_A`（今日，默认）
  - `M_A__D1` / `M_A__PME` / `M_A__PQE` / `M_A__PYE`
- **展示糖**：前端表达式构建器可显示 `M_A@上月末`，**保存时归一化为 `M_A__PME`**。`@` 不落库（Groovy 变量名不含 `@`）。
- **保留后缀**：`__D1/__PME/__PQE/__PYE` 为系统保留后缀，真实指标编码禁止以其结尾（新建/导入校验拦截，见 §10）。
- **解析规则**：对每个 `M_...` token，按**最后一个 `__`** 拆分；尾段命中枚举 → `(baseCode, timePoint)`；否则整体为 baseCode（今日）。
  - 例：`M_AUM_TOTAL__PYE` → `(M_AUM_TOTAL, PYE)`；`M_AUM_TOTAL` → `(M_AUM_TOTAL, TODAY)`。含下划线的真实编码不误伤。

## 5. 数据模型

**无表结构变更。** 取值时间完全承载在 `exprText` 的 token 里，随指标定义一起存。宽表 `(主体, data_date, version)` 三元组主键天然支持按任意历史日期读取。

`refMetricCodes` 字段：解析器按 baseCode（去后缀）参与 slot 预查即可，`refMetricCodes` 是否登记带后缀 token 不影响计算（后端已有"正则扫 exprText 兜底"路径，见 `MetricCalcService:321-326`）。

## 6. 计算引擎改造点（集中在 `MetricCalcService`）

现状 `executeGroovyAndPersist:299` → per-subject `loadRefValuesBySlotMap:390` 用固定 `dataDate` 读宽表、以 metricCode 为变量名绑定。改造：

1. **Token 解析器**（改 `extractMetricCodesFromExpr:519`）：产出 `List<RefToken{token, baseCode, tp}>`（tp ∈ TODAY/D1/PME/PQE/PYE），token 去重保序。
2. **Slot 预查**（`resolveSlotMap:370`）：用 **baseCode** 去查 `selectValSlotsByCodes`（slot 属于基础指标，与取值时间无关）。
3. **取值**（`loadRefValuesBySlotMap:390`）：对每个 RefToken：
   - 目标日期 = `DateMacroResolver.resolve(dataDate).get(宏key(tp))`
   - 目标版本 = 见 §7
   - 值 = `selectValBySlot(subject, slot, 目标日期, 目标版本)`，缺值 `BigDecimal.ZERO`（§8）
   - **以完整 token 作为 Groovy 变量名**绑定（`M_A__PME` → 值）
4. **公式执行**：Groovy 直接用 `M_A__PME - M_A__D1` 等 token 运算，结果写回本指标宽表 slot（`persistValues` 不变）。

## 7. 版本归属（决策②：历史档用今日同一 version）

- 所有取值时间档（含历史档 D1/PME/PQE/PYE）**统一使用本轮计算传入的 `version`**。
- 历史日期若不存在该 version 的行 → 按缺值处理（§8，取 0）。
- 理由：口径简单一致、可预测；数据完整性由导入侧保证。
  （备选"按主体该日最近导入版本反查"未采用。）

## 8. 缺值策略

任一 token 在（目标日期, version）下查无值 → `BigDecimal.ZERO`（沿用现有 `loadRefValuesBySlotMap` 兜底语义）。不因缺值使主体失败；per-subject 成功/失败统计（`SubjectStats`）语义不变。

## 9. 除法安全（除数为 0 不报错）

**需求**：公式做除法时除数为 0 不得抛异常导致计算失败。（缺值当 0 会放大除数为 0 的概率。）

**策略**：除数为 0 或 null 时，该次除法结果取 `0`。

**实现**
- 向 Groovy 绑定安全除法闭包 `div(a, b)`（在 `GroovyExecutorImpl` 执行前注入 `Binding`）：
  - `b == null || b.compareTo(ZERO) == 0` → 返回 `BigDecimal.ZERO`
  - 否则 `a.divide(b, 10, RoundingMode.HALF_UP)`，固定内部 scale=10、HALF_UP。
  - 固定 scale 同时规避 `BigDecimal` 对无限小数（如 `1/3`）不指定 scale 时抛 `ArithmeticException` 的坑。
  - **不做**指标级 `decimalPlaces` 全局末端四舍五入：现有 EXPR 输出（如 `42`）若统一 round 成 `42.00` 会破坏既有断言与落库精度，本期不引入该回归。最终精度由公式自身决定。
- 前端表达式构建器的**除法节点自动生成 `div(x, y)`**，操作人无需手写 `/`。
- **兜底防护**：`GroovyExecutorImpl.execute` 捕获 `ArithmeticException`（含手写裸 `/0`、无限小数）→ 返回 `BigDecimal.ZERO` + warn 日志，不抛异常、不使该主体失败。

> 说明：中文里"除数"为分母、"被除数"为分子。`0` 作为**被除数**（`0 / x`）本不报错；本节按"除数（分母）为 0"处理。若业务本意不同，请在评审时指出。

## 10. 校验与边界

- **保留后缀校验**：新建/导入指标定义时，`metricCode` 不得以 `__D1/__PME/__PQE/__PYE` 结尾。命中报新增错误码（拟 `PERF-422xx METRIC_CODE_RESERVED_SUFFIX`，编号落地时确认）。
- **未知后缀**：`M_A__XXX` 中 `XXX` 不在枚举 → 整体视为 baseCode `M_A__XXX`（今日），slot 查无 → ZERO；不额外报错（与现有"缺失即 ZERO"一致）。构建器只产出合法后缀，脏数据不放大风险。
- **层级约束不变**：L2→L1 / L3→L2 引用约束与取值时间正交，互不影响。
- **自引用仍拦截**：`MetricCycleDetectService` 不改，指标引用自身（含带历史取值时间）继续报环（§2 非目标）。

## 11. 试运行一致性

试运行取值路径 `loadGroovyVarsForSubject:471`（及其调用的 `extractMetricCodesFromExpr` / `loadRefValuesBySlotMap`）与真实计算共用同一套解析器与取值逻辑，保证试算 = 真实计算。`div` 安全函数在试运行 Groovy 执行时同样注入。

## 12. 测试计划（TDD）

**解析器单测**
- `M_A` → (M_A, TODAY)
- `M_A__PME` → (M_A, PME)；`__PQE`/`__PYE`/`__D1` 各一例
- `M_AUM_TOTAL__PYE` → (M_AUM_TOTAL, PYE)（含下划线编码不误伤）
- `M_AUM_TOTAL` → (M_AUM_TOTAL, TODAY)

**取值单测**
- 各档命中 `DateMacroResolver` 正确目标日期
- 历史日 + 同 version 命中 / 未命中→ZERO
- token 作为变量名正确绑定

**除法安全单测**
- `div(10, 0)` → 0；`div(10, null)` → 0
- `div(1, 3)` → 按 decimalPlaces 舍入不抛异常
- 手写 `10 / 0` → 主体结果 0 + warn，不中断整批

**端到端**
- `B = M_A - M_A__D1`：造数宽表两日值，算出正确差额并写回 B 的 slot
- `C = div(M_X, M_Y__PME)`：Y 上月末为 0 时 C=0 不报错

**校验单测**
- 新建/导入 `metricCode` 以保留后缀结尾 → 报错拦截

## 13. 风险与决策记录

| 项 | 决策 | 备注 |
|---|---|---|
| 作用范围 | 指标结果引用 + 固定枚举 | 不含 SQL 源表、不含任意偏移 |
| 配置形态 | 表达式内 token（`__后缀`） | `@` 仅前端展示，落库归一化 |
| 历史版本 | 用今日同一 version | 简单一致；缺行→ZERO |
| 缺值 | 当 0 | 沿用现有兜底 |
| 除数为 0 | 结果取 0 | `div()` 安全函数 + 兜底 catch + decimalPlaces 舍入 |
| 自引用 | 本期不做，环检测不改 | 累计递推场景另行评估 |

## 14. 交付边界

- 后端：`MetricCalcService`（解析器/取值/slot 预查改造）、`GroovyExecutorImpl`（注入 `div`）、`MetricDefService`（保留后缀校验）、新增错误码。
- 前端：表达式构建器新增"取值时间"下拉 + 除法节点生成 `div(x,y)` + 公式 token 反解析展示。
- 无 DDL 变更。
