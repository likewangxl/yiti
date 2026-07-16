# 动态指标查询 —「我的方案」编辑功能增强 设计（spec）

- 日期：2026-07-16
- 作者：刘杨
- 范围：`report-analytics-center`（后端能力已具备，本次基本不改）+ `xanzc_frontend`（主要改动）
- 关联页面：动态指标查询 `xanzc_frontend/src/views/report/Dynamic.vue` +「我的方案」`components/SchemeListDialog.vue`

## 1. 背景与目标

「我的方案」（saved query）的「编辑」当前**只能改方案名称**：`SchemeListDialog.vue` 的 `rename()`（:80）用 `ElMessageBox.prompt` 只收一个名称字段。

**目标**：编辑功能支持同时修改**维度、名称、指标、对象**，让用户能在不删除重建的前提下调整已保存方案。

## 2. 现状诊断（关键）

| 层 | 现状 | 结论 |
|---|---|---|
| 后端 `SavedQueryServiceImpl.updateQuery` | 乐观锁全量更新 `name/dim/subjectIds/metricCodes`（:123-151），Mapper `updateWithOptimisticLock`（xml:59）`WHERE id=? AND version=?` | ✅ 能力已具备，本次不改 |
| api `updateSavedQuery`（report.js:183） | 已会把 `subjects→subjectIds`、`metrics→metricCodes` 序列化 | ⚠️ 但有字段名 **bug** |
| 前端「编辑」`rename()` | 只弹输入框收名称、只发 `{name}` | ❌ 缺口所在 |

**要害 bug**：后端 `SavedQueryUpdateReqDTO.expectedVersion` 是 `@NotNull` + Controller `@Valid`，乐观锁 SQL 用 `expectedVersion`；但 api 发出去的字段名叫 **`version`**（report.js:193），且 `rename()` 根本没带。`call()` 对写操作失败是**抛错不兜底**（http.js:123）→ 对真后端的更新会因缺 `expectedVersion` 校验失败（现"改名成功"疑为未真正落库）。**做编辑必须先修此 bug。**

## 3. 方案（B：独立「编辑方案」弹框）

### 3.1 改动清单（5 处）

| 文件 | 改动 | 性质 |
|---|---|---|
| **新增** `components/SubjectPicker.vue` | 把 `Dynamic.vue` 的"对象选择弹框"原样抽取为可复用组件 | 纯抽取，行为不变 |
| 改 `Dynamic.vue` | 用 `<SubjectPicker>` 替换内联对象弹框（移除约 260 行内联逻辑） | 纯重构，行为不变 |
| **新增** `components/SchemeEditDialog.vue` | 编辑弹框：维度 + 名称 + `MetricPicker`(改指标) + `SubjectPicker`(改对象) + 保存 | 新增 |
| 改 `components/SchemeListDialog.vue` | 「编辑」→ `getSavedQuery` 取详情 → 打开编辑弹框预填 → 保存后刷新 | 小改 |
| 改 `api/report.js` | 修 bug：`updateSavedQuery` 字段 `version` → `expectedVersion` | bug 修复 |

### 3.2 `SubjectPicker.vue`（抽取）

- 从 `Dynamic.vue` 抽取对象选择弹框（模板 :113-180 + 脚本 :258-492 一带）。
- Props：`visible`(v-model) / `modelValue`(v-model，对象数组 `[{id,name,org}]`) / `dim`。
- **行为逐字节不变**：数据范围裁剪（`filterTreeByCodes`/`scopedOrgTree`/`pickerScope`）、包含下级机构、分批并发加载员工、SELF 仅本人、员工/客户搜索防抖、`orgUsersCache` 缓存。
- 组件自身负责 `getOrgTree()` / `getPickerScope(dim)`（维度变或打开时拉取）。
- `Dynamic.vue` 改为 `<SubjectPicker v-model:visible="subjectDlg.show" v-model="subjects" :dim="dim" />`，删除内联实现。

### 3.3 `SchemeEditDialog.vue`（新增）

- Props：`visible`(v-model) / `scheme`（编辑目标：`{id, name, dim, metrics:[code], subjects:[{id,name,org}], version}`）。
- 内容：
  - 维度单选（员工/机构/客户），默认选中方案原维度。
  - 名称输入（非空，maxlength 200）。
  - 「指标」标签区 + `MetricPicker`（复用，按当前 dim）。
  - 「对象」标签区 + `SubjectPicker`（复用，按当前 dim）。
- **维度切换**：若当前已选指标或对象，弹确认「切换维度会清空已选指标和对象，确定？」；确认才切并清空 `metrics`+`subjects`，取消则维度回退。
- **保存**：校验（名称非空、指标 ≥1、**对象可空**）→ 调 `updateSavedQuery(scheme.id, { name, dim, metrics, subjects, expectedVersion: scheme.version })` → 成功 `emit('saved')` + 关闭；失败由 http.js 拦截器弹后端错误。
- 与既有 `SchemeSaveDialog` 保持同一模式（组件内部调 api、`emit('saved')`）。

### 3.4 `SchemeListDialog.vue`（改）

- 「编辑」按钮 handler 改为：`getSavedQuery(row.id)` → 得含 `version/metrics/subjects` 的详情 → 打开 `SchemeEditDialog` 预填 → `saved` 后 `refresh()`。
- 移除 `rename()` 的 `ElMessageBox.prompt` 逻辑。

### 3.5 `api/report.js`（修 bug）

- `updateSavedQuery`：把请求体字段 `version` 改为后端契约的 `expectedVersion`。取值兼容既有调用方——`const ev = payload.expectedVersion ?? payload.version; if (ev != null) body.expectedVersion = ev;`（编辑弹框传 `expectedVersion`，老「改名」若仍传 `version` 也能带上），保证乐观锁字段名与后端 `SavedQueryUpdateReqDTO.expectedVersion` 一致。

## 4. 编辑数据流

```
点「编辑」→ getSavedQuery(id)
  → { name, dim, metrics[], subjects[{id,name,org}], version }
  → 打开 SchemeEditDialog 预填
  → 用户改 维度/名称/指标(MetricPicker)/对象(SubjectPicker)
  → 保存：updateSavedQuery(id, { name, dim, metrics, subjects, expectedVersion: version })
  → 后端乐观锁更新（version+1）
  → 成功：emit saved → SchemeListDialog.refresh()
  → 冲突（后端归 RPT-40002 NO_ACCESS）：提示"方案已被改动，请重新打开编辑"
```

## 5. 决策记录

1. **维度可改**：切换时确认并清空已选指标/对象（与主页面 `watch(dim)` 一致，指标/对象按维度绑定）。
2. **校验**：名称非空、指标 ≥1、**对象可空**（空 = 按数据范围查全部，后端 `noSubjectSelected` 分支支持，序列化为 `subjectIds:"[]"`，与主页面一致）。
3. **乐观锁冲突**：后端无独立错误码、归并到 RPT-40002 NO_ACCESS；前端提示重新打开编辑。
4. **SubjectPicker 抽取 = 纯重构**：Dynamic.vue 对象选择行为不变。

## 6. 边界与错误处理

- `getSavedQuery` 失败 → 提示"载入方案失败"，不打开弹框。
- 保存 version 冲突 → 上述提示；用户重新打开即拿到新 version。
- 空对象持久化为 `"[]"`；下次载入 `subjects` 为空 → 主页面/查询走"查全部"，一致。
- 维度切换取消时，UI 维度单选需回退到切换前的值（不产生"选中变了但没清空"的错位）。

## 7. 测试（vitest，项目已引入）

- `SchemeEditDialog`
  - 预填：给定 scheme，维度/名称/指标/对象正确回显。
  - 维度切换：有选择时弹确认；确认清空、取消回退。
  - 校验：指标为空拦截；名称空拦截；**对象为空放行**。
  - 保存 payload：字段齐全且含 `expectedVersion`（= scheme.version）。
- `updateSavedQuery`（api）：断言请求体字段名为 `expectedVersion`（回归本次 bug）。
- `SubjectPicker` 抽取：抽取后对 `Dynamic.vue` 三个维度（员工/机构/客户）做**手动回归**——选择、搜索、数据范围裁剪、包含下级、SELF 仅本人。

## 8. 不做（YAGNI / 范围外）

- 不改 `SchemeSaveDialog` 的"保存"校验（保存仍要求对象 ≥1；仅"编辑"放开对象可空，按需求）。
- 不做后端改动（`updateQuery` 能力已具备）。
- 不做动态查询性能优化（后端单条循环 N+1 属另一优化项，另开 spec）。

## 9. 风险

| 风险 | 缓解 |
|---|---|
| SubjectPicker 抽取导致 Dynamic.vue 对象选择行为漂移 | 只搬不改逻辑；抽取后三维度手动回归 |
| 乐观锁字段名修复影响既有"改名" | 改名也走同一 api，一并带 `expectedVersion`；vitest 回归 |
