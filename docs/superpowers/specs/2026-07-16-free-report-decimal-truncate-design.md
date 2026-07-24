# 自由报表 数字列两位小数截断显示 设计（spec）

- 日期：2026-07-16
- 作者：刘杨
- 范围：`report-analytics-center`（下载 Excel）+ `xanzc_frontend`（查看页）
- 关联：查看页 `FreeReportDetail.vue`；下载 `FreeReportController.downloadFile` → `FreeReportServiceImpl.exportFilteredExcel`

## 1. 需求

1. **查看页**：数字列显示保留小数点后两位，**截断不四舍五入**；原始导入数据不动。
2. **下载 Excel**：数字列显示两位；**点击该单元格时编辑栏干净显示完整原值**（2026-07-20 定稿，优先级高于截断）。
   - 例：原值 `3.1779998` → 查看页显示 `3.17`（截断）；下载的 Excel/WPS 里格内显示 `3.18`（四舍五入），点击该格编辑栏显示 `3.1779998`。

## 2. 现状（已核实）

- 自由报表所有单元格按**字符串**存：`RptFreeReportRow.col1`(工号)/`col2`(姓名)，其余列在 `dataJson`。
- 导入 `getCellString` 把数字转字符串**保留完整精度**（`3.1779998`→`"3.1779998"`）→ **原始完整值本就在库里，截断纯显示层，天然满足"不动原始数据"**。
- 查看页 el-table 直接渲染字符串、无格式化。
- 下载 `exportFilteredExcel` 逐格 `setCellValue(字符串)` 写成文本。

## 3. 核心规则 `truncate2`（前后端逐字一致）

输入字符串 `s`：
- **仅当** `s` 匹配 `^-?\d+\.\d+$`（纯数字且含小数点）才处理，否则**原样返回**。
- 处理：`[intPart, frac] = s.split('.')`；`frac2 = (frac + "00").substring(0,2)`；返回 `intPart + "." + frac2`。
- 例：`3.1779998`→`3.17`；`3.1`→`3.10`；`-2.999`→`-2.99`；`0.005`→`0.00`；`1001`/`张三`/`2026-07-16`/空 → 不变。
- **不进位**（直接砍尾），**toward-zero**（负数砍尾即向零截断）。

「精度被砍」判定（决定 Excel 该格是否套 TRUNC 公式）：小数位数 `frac.length > 2`。`3.1779998`(7)→套；`3.1`(1)/`3.17`(2)→不套。

## 4. 需求1 · 查看页 `FreeReportDetail.vue`

- 新增前端工具 `src/utils/numFmt.js` 导出 `truncate2(s)`。
- 动态数据列（`dynamicCols`，第 3 列起）单元格改为自定义模板：
  ```html
  <template #default="{ row }">
    <span :title="row[col.key]">{{ truncate2(row[col.key]) }}</span>
  </template>
  ```
  - 显示截断值；`:title="原值"` → 鼠标悬停显示完整原值（与下载"可查看完整值"一致）。
- 工号(col_1)/姓名(col_2) 两列**不套** `truncate2`（保持原样，且 `truncate2` 对整数工号本就不变）。
- 接口返回的 `rows` 原始数据**不改**。

## 5. 需求2 · 下载 `FreeReportServiceImpl.exportFilteredExcel`

> **方案演进（两次修订，最终以第 3 版为准）**
>
> | 版本 | 做法 | 结果 |
> |---|---|---|
> | v1（07-16） | 截断值入格 + 完整原值挂「数据有效性输入提示」`createPromptBox` | ❌ WPS 不渲染该提示；观感等同批注，用户否决 |
> | v2（07-20 上午） | 截断值 + `TRUNC(原值,2)` 公式承载完整值 | ❌ 功能达标，但用户不接受编辑栏里的 `=TRUNC(...)` 壳 |
> | **v3（07-20 下午，当前）** | **完整原值直接入格 + `0.00` 格式** | ✅ 编辑栏干净显示完整原值；**代价：格内四舍五入** |
>
> **不可共存的根本原因**：Excel/WPS 编辑栏显示的就是单元格存的内容，格内显示 = 数字格式渲染该内容，
> 而数字格式**只会四舍五入、不会截断**。所以「编辑栏干净显示完整值」与「格内截断显示」必掉一个。
> 用户 2026-07-20 明确取舍：**保完整原值，舍截断**（下载场景）。

对每个数据格值 `val`（`col_1`/`col_2` 除外，工号/姓名原样文本）：
- **`val` 匹配小数** `^-?\d+\.\d+$` → `setCellValue(Double.parseDouble(val))` 写**完整原值**，套数字格式 `0.00`。
  - 点击该格 → 编辑栏显示 `3.1779998`（完整、无公式壳）。
  - 格内显示 `3.18`（`0.00` 四舍五入）。与查看页的截断显示（`3.17`）**口径不同**，见 §8。
- **否则**（整数/文本/工号/姓名）：原样 `setCellValue(val)` 文本——刻意不转数值，防编号类纯数字丢前导零或变科学计数法。
- 表头行不变。

数字格式样式复用：为整个 workbook 建**一个** `CellStyle`（dataFormat `0.00`）复用到所有数值格，避免超 64000 样式上限。

## 6. 改动文件

| 文件 | 改动 |
|---|---|
| 新增 `xanzc_frontend/src/utils/numFmt.js` | `truncate2(s)` 纯函数 |
| 改 `xanzc_frontend/src/views/report/FreeReportDetail.vue` | 动态列套 `truncate2` + `:title` 原值 |
| 改 `report-analytics-center/.../FreeReportServiceImpl.java` | `exportFilteredExcel` 数字格截断+格式+提示；抽 `truncate2`/`isDecimal` helper |

## 7. 测试

- 前端 vitest `numFmt.spec.js`：`truncate2` 覆盖 `3.1779998→3.17`/`3.1→3.10`/`-2.999→-2.99`/`1001→1001`/`张三→张三`/空/`0.005→0.00`。
- 前端 vitest `FreeReportDetail`（happy-dom mount，stub el-table 取 slot）：动态列渲染截断值、`title` 为原值。（若挂载 el-table 过重，则至少覆盖 `truncate2` 单测 + 手动看页面。）
- 后端 `FreeReportServiceImplTest`（2026-07-20 随 v3 方案重写）：
  - `exportFilteredExcel_longDecimal_keepsFullValueInCell`——`3.1779998` → `getCellType()==NUMERIC`、
    `getNumericCellValue()≈3.1779998`（**完整原值**）、dataFormat `0.00`、`sheet.getDataValidations()` 为空；工号/姓名保持文本。
  - `exportFilteredExcel_shortDecimal_writesPlainNumber`——`3.1` → NUMERIC 3.1 + `0.00`。
  - `isDecimal_rule`——纯小数 true；整数/文本/空/null false。（v2 的 `truncate2` 后端已随方案删除，前端 `numFmt.js` 的同名函数仍服务查看页。）

## 8. 不做（YAGNI）

- 不改导入/入库逻辑（原值完整保留）、不改存储结构、不改行级过滤/权限。
- 查看页"点击弹框"不做（悬停 `title` 已满足"可查看完整值"；需求1 只要求截断显示）。

## 9. 风险

- ~~v1 逐格挂数据有效性提示~~、~~v2 TRUNC 公式~~ 均已废弃，原因见 §5 演进表。
- **v3 遗留的口径不一致（已知、用户接受）**：同一个值，**查看页显示截断**（`3.1779998`→`3.17`），
  **下载的 Excel 显示四舍五入**（→`3.18`）。第三位小数 ≥5 时两处差 0.01。
  若将来要求两处一致，只有两条路：查看页也改成四舍五入，或下载改「截断列 + 原值列」双列。
- 整数列（如户数 `152452`）当前按**文本**写入 Excel，会有"数字以文本形式存储"绿色三角、不能直接求和排序。
  这是刻意保守（防客户号/卡号类纯数字被转成数值丢前导零），非本次引入；如确认无编号类整数列可另行放开。
- `truncate2` 用字符串处理规避浮点误差；后端数值格 `parseDouble(截断串)` 再套 `0.00`，二次校验显示为两位。

## 11. 2026-07-21 方案再修订：改为「保留 Excel 原始显示格式」（当前生效）

### 触发问题
用户实测发现两类展示错误，根子都在导入丢格式：
1. **科学计数法**：源表格内显示 `-0.0`（格式 `0.0`），编辑栏 `-5.00000000069889E-7`。
   导入时 `getCellString` 走 `String.valueOf(double)` → 存成 `-5.00000000069889E-7`，
   页面与下载都把科学计数法直接显示出来。
2. **百分比丢失**：源表格内显示 `54.5%`（格式 `0.0%`，值 `0.545175438596492`）。
   导入只取原始 double，% 格式丢失，页面显示 `0.545175438596492`。

### 关键认识
**源 Excel 自己就同时做到了「格内显示 -0.0」与「编辑栏显示完整值」**——靠的是
「单元格存完整数值 + 数字格式 `0.0`」。所以只要把原格式一并带过来，就能完整复刻，
**不存在 §5 v3 那种「显示 vs 完整值」二选一的取舍**（该取舍就此作废）。

### 落地
- **导入**（`FreeReportServiceImpl.importExcel`）每个数据列存三份到 `DATA_JSON`：
  - `col_N` —— 显示文本，由 POI `DataFormatter.formatCellValue` 渲染，与 Excel 所见一字不差
  - `col_N__raw` —— 完整原值，`BigDecimal(Double.toString(v)).toPlainString()` 去科学计数法
  - `col_N__fmt` —— 原数字格式串（`General`/`@` 视为无格式，不存）
  - 仅第 3 列起生成；`col_1`/`col_2`（工号/姓名）是文本，不生成
  - 新增私有方法 `getCellDisplay` / `getCellRaw` / `getCellFormat`
- **导出**（`exportFilteredExcel`）数值格写 `col_N__raw` 的完整值 + `col_N__fmt` 的原格式；
  样式按格式串缓存（`styleCache`），避免逐格建样式撞 Excel 64000 上限。
  老批次无 `__raw`/`__fmt` 时回退「原值 + `0.00`」，保持既有行为。
- **查看页**（`FreeReportDetail.vue`）新增 `utils/cellFmt.js`：
  `cellDisplay(row,key)` —— 有 `__raw`（新批次）直接显示 `col_N`；无则沿用 `truncate2`（老批次）
  `cellFull(row,key)` —— 优先 `col_N__raw`，回退 `col_N`
  单元格改 `el-popover` 点击展开完整值，`title` 同步给完整值。

### 端到端实测
源值 `-5.00000000069889E-7`(格式 `0.0`) / `0.545175438596492`(格式 `0.0%`)：
```
DATA_JSON = {"col_3":"-0.0","col_3__raw":"-0.000000500000000069889","col_3__fmt":"0.0",
             "col_4":"54.5%","col_4__raw":"0.545175438596492","col_4__fmt":"0.0%"}
导出列3 | 格内显示=-0.0  | 单元格实际值=-0.000000500000000069889 | 数字格式=0.0
导出列4 | 格内显示=54.5% | 单元格实际值=0.545175438596492        | 数字格式=0.0%
```

### 测试
- 后端 `importExcel_keepsDisplayTextRawValueAndFormat`、`exportFilteredExcel_usesOriginalFormatAndRawValue`
- 前端 `utils/__tests__/cellFmt.spec.js` 7 例（含老批次回退）

### 遗留
存量老批次仍是「原始值 + 截断两位」展示，要拿到新效果需重新上传。

## 12. 2026-07-22 修复：格式在真实报表上失效的三个根因

§11 的方案在代码构造的样例上通过，但现场真实报表仍不对。拿到用户问题文件
（含会计格式 numFmtId=41、自定义 numFmtId=177）逐格复核，查出三处：

### 根因1（致命）：格式串被 trim 破坏 → Excel 判非法 → 退回常规格式
`getCellFormat` 原有 `fmt = fmt.trim()`。Excel 格式里 `_`(占位一字符宽) 与
`\`(转义) 后面**必须再跟一个字符**，而真实格式常以空格结尾：
```
会计41 : _ * #,##0_ ;_ * \-#,##0_ ;_ * "-"_ ;_ @_      trim后 -> ..._ @_    孤立下划线
自定177: 0.00_ ;[Red]\-0.00\                          trim后 -> ...\-0.00\ 孤立反斜杠
```
Excel/WPS 判定整串非法后**退回常规格式** —— 表现为「下载后格子显示 0、编辑栏也是 0」，
即用户反馈的现象。修复：判空/判 General 用 trim 后的副本，**返回原串**。

### 根因2：POI 对「从文件读入的自定义格式」渲染退化成科学计数法
`formatCellValue(cell)` 对源文件里 numFmtId=177 的 `0.00_ ;[Red]\-0.00\ `
把 -5e-7 渲染成 `-5.00000000069889E-07`；而 `formatRawCellContents(值, formatIndex, 格式串)`
渲染为 `-0.00`（与 WPS 一致）。**代码新建**自定义格式时 POI 分配 numFmtId=164，两者表现相同，
所以此前用构造样例的测试复现不出。修复：数值格改走 `formatRawCellContents`（见 `renderByFormat`）。

### 根因3：分段格式下「舍入后为零」的负值，POI 走错分段
Excel 选格式的哪一段（正;负;零）看的是**按该格式舍入后**的值：`#,##0` 把 -5e-7 舍成 0，
故走第三段显示 `-`；POI 只看原始值符号，走负数段渲染成 `-0` —— 即用户反馈的「页面展示 -0」。
修复：`fixNegativeZeroByZeroSection` —— 当渲染结果数字部分全为 0 且格式确有零段(≥3段)时，
改用零值重新渲染。只有两段(正;负)的格式不受影响，`-0.00` 仍正常显示。

### 回归固件
`src/test/resources/freereport/real-formats.xlsx`（取自现场问题文件）。
构造式样例无法复现根因2，必须用真实文件守护。新增 4 个用例：
- `importExcel_keepsFormatStringVerbatim_noTrimming` 格式串逐字保留、尾空格不丢
- `importExcel_accountingFormat_tinyValueRoundsToZero_showsDash` 会计格式极小值显示 `-`
- `importExcel_realFile_noScientificNotation_andAccountingDash` 全表无科学计数法 + 逐格断言
- 前端 `cellFmt.spec.js` 补会计格式/极小值/仅 __fmt 无 __raw 三类场景

### 实测（真实文件）
```
行3 col_9: 显示 "-"      原值 0.0
行4 col_9: 显示 "-0.00"  原值 -0.000000500000000069889   (修复前是科学计数法)
行5 col_9: 显示 "-"      原值 0.0
导出格式尾字符 = ' '（空格保住，格式在 Excel 中有效）
```

## 13. 2026-07-22 修正：完整原值改为「与 Excel 编辑栏一字不差」

### 需求澄清（此前理解偏差）
§11/§12 一直把 `-5.00000000069889E-07` 当成「要消除的科学计数法」，用
`BigDecimal.toPlainString()` 转成 `-0.000000500000000069889`。**这是错的**——
用户要的是「和原始报表保持一致」，而 **Excel 编辑栏本来就用科学计数法**：

| 格内显示 | Excel 编辑栏（= 完整值应有的样子） |
|---|---|
| `-`      | `0`                        |
| `-0.00`  | `-5.00000000069889E-07`    |

### 落地
新增 `excelRawText(double)` 取代原先的 `BigDecimal.toPlainString()`，抹平 Java 与 Excel 的两处差异：
- **整数尾巴**：`Double.toString(0.0)`="0.0" → Excel 是 `0`
- **指数写法**：`Double.toString(-5e-7)`="-5.00000000069889E-7" → Excel 是 `E-07`（补两位带符号）

阈值：绝对值落在 `[1e-4, 1e15)` 用十进制平铺（与 Excel 编辑栏一致），超出才用科学计数法。
不能用 Java 默认阈值（1e-3 / 1e7 就切科学计数法，会与 Excel 不符），也不能用 POI 的
`General` 渲染（会把 `0.498888897666` 截成 `0.4988888977`，丢精度）。
平铺分支用 `stripTrailingZeros()`——`Double.toString(0.0005)`="5.0E-4"，
直接 `toPlainString()` 会得到 `0.00050`。

### 连带修复：导出侧不能再用 isDecimal 判定数值格
`__raw` 现在可能是科学计数法，而 `isDecimal` 的正则 `^-?\d+\.\d+$` 匹配不上，
会把该格当**文本**写死（不受数字格式控制、点击也看不到原值）。
改为：**有 `__raw` 就直接 `Double.parseDouble`**（导入时只对 NUMERIC 生成该键，必为数值）；
无 `__raw` 的老批次才沿用 `isDecimal(val)` 的保守判定（防工号/卡号被转数值丢前导零）。

### 实测（真实文件，四处口径全部一致）
```
                 页面显示   页面完整值                 下载格内   下载编辑栏
会计格式 0        -         0                          -         0
极小值           -0.00     -5.00000000069889E-07      -0.00     -5.00000000069889E-07
```

### 测试
后端 11 例（新增 `excelRawText_matchesExcelFormulaBar` 覆盖零/整数/普通小数/极小值/
阈值边界，`exportFilteredExcel_scientificNotationRaw_writtenAsNumberWithFormat` 守导出）；
前端 10 例（`cellFull` 断言改为原样透传科学计数法，不再做"消除"加工）。

## 14. 2026-07-22 全表逐格验收 + 由此揪出的两个遗漏

此前只盯着出问题的那一列验证，改为**整张表逐格比对**（源 Excel vs 导入→导出后的 Excel，
每格的「格内显示」与「编辑栏原值」都必须相同）后，立刻暴露两处遗漏：

### 遗漏1：General 格式的整数被写成文本
源里 `85`（无自定义格式，General）：导入时 `__raw`("85") 与显示文本("85") 相同，
原逻辑 `!raw.equals(val)` 判定为"无需存"→ 不写 `__raw`；导出时便无从知道该格是数值，
而 `isDecimal("85")` 又不认整数（正则要求必须有小数点）→ **当文本写死**，
Excel 里出现"数字以文本形式存储"绿三角且不能求和。

修复两处：
- 导入：只要是数值格就存 `__raw`，**即便与显示文本相同** —— 它同时承担「源里是数值」的标记作用
- 导出：`__fmt` 为空（源本就是 General）时**不套任何格式**，而非退回 `0.00`；
  否则源显示 `85` 会变成 `85.00`。老批次（无 `__raw`）仍走 `0.00`，保持既有观感

### 遗漏2（测试侧）：比对逻辑没处理公式格
固件里 `I5` 是**公式格**（公式 `-5.00000000069889E-07`，缓存值 `-5.0000000006988898E-7`）。
生产代码本就正确（`getCellDisplay`/`getCellRaw` 均对 FORMULA 取 `getCachedFormulaResultType()`），
是比对代码把 `getCellType()==FORMULA` 误判成文本列。导出侧一律落成数值/文本快照、不保留公式，
这是自由报表作为数据快照的设计选择。

### 验收用例
`roundTrip_everyCell_displayAndRawValueMatchSource` —— 全表逐格（数值格比显示+编辑栏原值，
文本格比内容），断言比对格数下限防止空跑。这是本特性的总验收。

## 15. 2026-07-23 修复：百分比列的「完整值」必须带 % 且已乘 100

### 问题（用户实测）
源报表某列格内显示 `-1683.2%`，点开编辑栏是 `-1683.24723247232%`；
页面格内显示对了，但点开的完整值是 `-16.8324723247232`（底层小数），与源对不上。

### 根因
Excel 对**百分比格式**的单元格，编辑栏显示的是「底层值 ×100 加 %」，
而 `getCellRaw` 一直直接输出底层 double。固件上同样复现：
`0.91 + 0%` 给出 `0.91`（应为 `91%`）、`0.498888897666 + 0.00%` 给出底层小数（应为 `49.8888897666%`）。

### 落地
- 新增 `isPercentFormat(fmt)`：扫描格式串找**生效的** `%`，跳过 `\%` 转义、
  `"…%…"` 引号字面量、`[Red]`/`[$-409]` 方括号段。
- `getCellRaw` 命中百分比格式时用 `BigDecimal.movePointRight(2)` **精确移位**再加 `%`
  （`×100` 的浮点乘法会引入误差，如 0.498888897666×100 = 49.88888976660001）。
- `excelRawText` 增加 BigDecimal 重载，百分比移位后直接走它，避免二次转 double 丢精度；
  科学计数法分支抽为 `sciText`。

### 连带修复：导出解析 __raw 要能处理 % 后缀
`__raw` 现在可能形如 `91%`，`Double.parseDouble` 会抛异常 → 该格被当**文本**写入
（导出比对立刻报 `expected NUMERIC but was STRING`）。新增 `parseRawValue`：
末尾带 % 时 `movePointLeft(2)` 还原为底层值（`91%` → `0.91`），解析失败才退回文本。

### 测试
`importExcel_percentFormat_rawValueCarriesPercentSign`（0% 与 0.00% 两种格式 + 非百分比列不受影响）；
`roundTrip_everyCell_displayAndRawValueMatchSource` 覆盖导出侧的百分比回写。
