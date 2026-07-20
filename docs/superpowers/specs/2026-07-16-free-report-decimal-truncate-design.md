# 自由报表 数字列两位小数截断显示 设计（spec）

- 日期：2026-07-16
- 作者：刘杨
- 范围：`report-analytics-center`（下载 Excel）+ `xanzc_frontend`（查看页）
- 关联：查看页 `FreeReportDetail.vue`；下载 `FreeReportController.downloadFile` → `FreeReportServiceImpl.exportFilteredExcel`

## 1. 需求

1. **查看页**：数字列显示保留小数点后两位，**截断不四舍五入**；原始导入数据不动。
2. **下载 Excel**：数字列同样显示截断两位；**点击该单元格时可看到完整原值**。
   - 例：原值 `3.1779998` → 查看页显示 `3.17`；下载的 Excel/WPS 里格内显示 `3.17`，点击该格编辑栏显示 `=TRUNC(3.1779998,2)`。

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

> **2026-07-20 方案修订**：初版用「数据有效性输入提示」（`createPromptBox`）承载完整原值，实测 WPS
> 不稳定渲染该提示、观感也像批注，用户明确否决。改为 **TRUNC 公式**方案，见下。

对每个数据格值 `val`（`col_1`/`col_2` 除外，工号/姓名原样文本）：
- **`val` 匹配小数** `^-?\d+\.\d+$`，套数字格式 `0.00`，并按小数位数分流：
  - **`frac.length > 2`**（确有精度被砍）→ 写**公式** `TRUNC(<原值>,2)`，再 `setCellValue(parseDouble(truncate2(val)))` 写入缓存结果。
    - 格内显示 = 公式结果 = 截断两位（`3.1779998` → `3.17`，**不四舍五入**）。
    - 点击该格 → 编辑栏显示 `=TRUNC(3.1779998,2)`，**完整原值可见**。
    - 关键约束：Excel/WPS 的数字格式**只会四舍五入、无法截断**（`0.00` 套在 `3.1779998` 上必显示 `3.18`），
      所以「格内截断显示」与「单元格里留完整值」不可能在同一个裸数值格上共存；公式是唯一能同时满足两者的原生手段。
  - **`frac.length ≤ 2`**（无精度损失）→ 写普通数值 `parseDouble(val)`，不套公式。
- **否则**（非小数/工号/姓名）：原样 `setCellValue(val)` 文本。
- 表头行不变。
- 写过公式时 `wb.setForceFormulaRecalculation(true)`，保证各类阅读器打开即重算；无公式则不设，避免文件被标记为已修改。

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
- 后端 `FreeReportServiceImplTest`（2026-07-20 随方案修订重写）：
  - `exportFilteredExcel_longDecimal_writesTruncFormulaCarryingFullValue`——`3.1779998` → 该格 `getCellType()==FORMULA`、
    `getCellFormula()=="TRUNC(3.1779998,2)"`、缓存 `getNumericCellValue()≈3.17`、dataFormat `0.00`、`sheet.getDataValidations()` 为空；工号/姓名保持文本。
  - `exportFilteredExcel_shortDecimal_writesPlainNumber`——`3.1` → 普通 NUMERIC 3.1 + `0.00` 格式，不套公式。

## 8. 不做（YAGNI）

- 不改导入/入库逻辑（原值完整保留）、不改存储结构、不改行级过滤/权限。
- 查看页"点击弹框"不做（悬停 `title` 已满足"可查看完整值"；需求1 只要求截断显示）。

## 9. 风险

- ~~方案 D 逐格挂提示~~（2026-07-20 已废弃，WPS 渲染不稳定 + 观感像批注）。
- TRUNC 公式方案的代价：被截断的格里是**公式而非裸数字**，下游若拿这份 xlsx 再做汇总，取到的是截断后的值（少 0.00x 量级）。
  已用「只给 `frac>2` 的格套公式」把范围压到最小；若将来有下游精确汇总诉求，再加一列原值或改回纯数值 + 接受四舍五入。
- `truncate2` 用字符串处理规避浮点误差；后端数值格 `parseDouble(截断串)` 再套 `0.00`，二次校验显示为两位。
