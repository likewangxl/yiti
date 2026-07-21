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
