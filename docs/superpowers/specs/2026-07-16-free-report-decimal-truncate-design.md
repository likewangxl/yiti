# 自由报表 数字列两位小数截断显示 设计（spec）

- 日期：2026-07-16
- 作者：刘杨
- 范围：`report-analytics-center`（下载 Excel）+ `xanzc_frontend`（查看页）
- 关联：查看页 `FreeReportDetail.vue`；下载 `FreeReportController.downloadFile` → `FreeReportServiceImpl.exportFilteredExcel`

## 1. 需求

1. **查看页**：数字列显示保留小数点后两位，**截断不四舍五入**；原始导入数据不动。
2. **下载 Excel**：数字列同样显示截断两位；**点击/选中该单元格时弹出显示完整原值**。
   - 例：原值 `3.1779998` → 查看页显示 `3.17`；下载 Excel 显示 `3.17`，点击该格弹出 `3.1779998`。

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

「精度被砍」判定（决定 Excel 是否挂提示）：小数位数 `frac.length > 2`。`3.1779998`(7)→挂；`3.1`(1)/`3.17`(2)→不挂。

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

对每个数据格值 `val`（`col_1`/`col_2` 除外，工号/姓名原样文本）：
- **`val` 匹配小数** `^-?\d+\.\d+$`：
  - 写**数值单元格** = `Double.parseDouble(truncate2(val))`（如 3.17），套数字格式 `0.00`（保证两位显示；值已截断，`0.00` 不会再 round）。
  - **若 `frac.length > 2`**（确有精度被砍）→ 给该格挂**数据有效性输入提示**：`dvHelper.createCustomConstraint("TRUE")` + `createPromptBox("完整值", val)` + `setShowPromptBox(true)` + `setSuppressDropDownArrow(true)`，作用域 = 该单格 `CellRangeAddressList(r,r,c,c)`。点击/选中即弹完整原值。
- **否则**（非小数/工号/姓名）：原样 `setCellValue(val)` 文本。
- 表头行不变。

数字格式样式复用：为整个 workbook 建**一个** `CellStyle`（dataFormat `0.00`）复用到所有截断数值格，避免超 64000 样式上限。

## 6. 改动文件

| 文件 | 改动 |
|---|---|
| 新增 `xanzc_frontend/src/utils/numFmt.js` | `truncate2(s)` 纯函数 |
| 改 `xanzc_frontend/src/views/report/FreeReportDetail.vue` | 动态列套 `truncate2` + `:title` 原值 |
| 改 `report-analytics-center/.../FreeReportServiceImpl.java` | `exportFilteredExcel` 数字格截断+格式+提示；抽 `truncate2`/`isDecimal` helper |

## 7. 测试

- 前端 vitest `numFmt.spec.js`：`truncate2` 覆盖 `3.1779998→3.17`/`3.1→3.10`/`-2.999→-2.99`/`1001→1001`/`张三→张三`/空/`0.005→0.00`。
- 前端 vitest `FreeReportDetail`（happy-dom mount，stub el-table 取 slot）：动态列渲染截断值、`title` 为原值。（若挂载 el-table 过重，则至少覆盖 `truncate2` 单测 + 手动看页面。）
- 后端 `FreeReportServiceImplTest`：新增 case——mock `rowMapper` 返回含 `3.1779998` 的行，调 `exportFilteredExcel` 读回 workbook 断言：该格 `getNumericCellValue()≈3.17`、样式 dataFormat 为 `0.00`、该格 `DataValidation` 的 `getPromptBoxText()` 含 `3.1779998`；整数/文本格保持文本。

## 8. 不做（YAGNI）

- 不改导入/入库逻辑（原值完整保留）、不改存储结构、不改行级过滤/权限。
- 查看页"点击弹框"不做（悬停 `title` 已满足"可查看完整值"；需求1 只要求截断显示）。

## 9. 风险

- 方案 D 逐格挂提示：数字列极多 + 行数几千时 xlsx 变大、生成变慢。已用「只给确被截断的格挂」缓解；若真超大再考虑上限或降级为整列提示/批注。
- `truncate2` 用字符串处理规避浮点误差；后端数值格 `parseDouble(截断串)` 再套 `0.00`，二次校验显示为两位。
