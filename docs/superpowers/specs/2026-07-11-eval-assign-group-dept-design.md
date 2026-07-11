# 评价任务新增「分组部门」汇总维度 — 设计规格

- 日期：2026-07-11
- 模块：performance-engine-center / eval 子域（B 体系：导入式待处理任务）
- 相关表：`EVAL_ASSIGN_ITEM`
- spec 状态：待评审

## 1. 背景与问题

导入式评价任务（B 体系）中，用户端「待处理任务」把当前登录人作为打分人，将其未提交明细**按被打分人部门（`be_eval_dept`）汇总**成若干分组，逐组进入打分。

现实痛点：当一个打分人需要给 10 个不同部门、每个部门仅 1 人的被打分人打分时，汇总界面会出现 10 个部门分组，需点击「处理」10 次、提交 10 次，效率极低。

## 2. 目标

为评价任务导入新增一个可选字段「**分组部门**」，作为汇总/下钻的**优先分组键**：

```
有效分组键 = 分组部门(group_dept) 非空 ? group_dept : 被打分人部门(be_eval_dept)
```

- 打分人导入时可把分散在多部门的少量被打分人归到同一个「分组部门」（如「零售条线」），汇总时合并成 1 组，一次处理、一键提交。
- 分组部门为空时，行为与现状完全等价（回退按被打分人部门汇总），存量数据平滑兼容。

## 3. 非目标

- 不改动 A 体系（规则驱动 `EVAL_TASK/TARGET/SCORE`）的任何逻辑。
- 不引入分组部门字典校验（与部门一致，为自由文本导入快照）。
- 不改动唯一键 `UK_BATCH_PAIR`、打分分值口径、批次状态机。

## 4. 数据模型变更

### 4.1 DDL

`EVAL_ASSIGN_ITEM` 在 `BE_EVAL_DEPT` 之后新增一列：

```sql
GROUP_DEPT VARCHAR(200) NOT NULL DEFAULT '' COMMENT '分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）'
```

- 采 `NOT NULL DEFAULT ''`，与 `BE_EVAL_DEPT` 一致，避免 `GROUP BY NULL` 丢组、并让 `COALESCE(NULLIF(...))` 表达式稳定。
- 更新基线 `docs/schema/ddl-eval.sql`。
- 新增增量脚本 `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql`：`ALTER TABLE ... ADD COLUMN`（INFORMATION_SCHEMA 幂等预检），需在目标库（yiti + onepl_test_bootstrap）**手工执行**（遵循项目 Flyway 禁令红线）。

### 4.2 实体

`EvalAssignItem` 新增：

```java
/** 分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）. */
private String groupDept;
```

## 5. 导入链路变更

### 5.1 Excel 行模型

`EvalAssignImportRow` 第 4 列插入（`被打分员工部门` 之后、`被打分员工标签` 之前）：

```java
@ExcelProperty("分组部门")
private String groupDept;
```

模板由 10 列 → **11 列**。列顺序：
1 被打分员工编号 / 2 被打分员工姓名 / 3 被打分员工部门 / **4 分组部门** / 5 被打分员工标签 / 6 打分员工编号 / 7 打分员工姓名 / 8 打分员工标签 / 9 打分员工部门 / 10 权重标签 / 11 评价类型

> 注意：EasyExcel `head(class)` 按列序读取，插入到第 4 列会使旧 10 列模板文件列错位。上线后**必须一律使用新模板**（已与需求方确认并接受）。

### 5.2 校验与入库

`EvalAssignImportService.validate`：映射时 `item.setGroupDept(trim(r.getGroupDept()))`。

- 分组部门**可为空**，**不做字典/存在性校验**（自由文本快照）。
- 其余校验规则（员工存在性、权重标签/评价类型字典、文件内配对去重、all-or-none）完全不变。

### 5.3 模板下载样例

`EvalAssignAdminController.importTemplate` 样例行补 `sample.setGroupDept("零售条线")`；类/方法 javadoc 中「10 列」表述更新为「11 列」。

## 6. 汇总 + 下钻变更（核心）

`EvalAssignItemMapper.xml`：

- `BASE_COLUMNS` 追加 `group_dept`。
- `batchInsert` 追加 `group_dept` 列与 `#{i.groupDept}`。
- `selectPendingGroups`：分组维度改为有效分组键
  ```sql
  SELECT i.batch_id AS batchId, b.task_type AS taskType, b.batch_name AS taskName,
         COALESCE(NULLIF(i.group_dept, ''), i.be_eval_dept) AS dept,
         COUNT(*) AS pendingCount, b.deadline AS deadline
  FROM EVAL_ASSIGN_ITEM i JOIN EVAL_ASSIGN_BATCH b ON i.batch_id = b.batch_id
  WHERE i.eval_user_id = #{evalUserId} AND i.submitted = 0 AND b.status = 0
    AND b.deadline IS NOT NULL AND b.deadline > NOW()
  GROUP BY i.batch_id, b.task_type, b.batch_name,
           COALESCE(NULLIF(i.group_dept, ''), i.be_eval_dept), b.deadline
  ORDER BY b.deadline ASC, i.batch_id ASC
  ```
- `selectByScorerBatchDept`：下钻过滤同表达式
  ```sql
  WHERE eval_user_id = #{evalUserId} AND batch_id = #{batchId}
    AND COALESCE(NULLIF(group_dept, ''), be_eval_dept) = #{dept}
  ```

`EvalPendingGroupDTO.dept` 语义即「有效分组键」，字段/结构不变——前端拿到的 `dept` 在 group_dept 为空时天然是 be_eval_dept，符合「分组部门为空时展示被打分人部门」的要求。

### 6.1 分组合并语义（明确）

有效分组键相同的明细会合并为同一组。例如某批次内：
- A 行 group_dept=「零售条线」、be_eval_dept=「信贷部」
- B 行 group_dept=「零售条线」、be_eval_dept=「零售部」

A、B 合并到「零售条线」一组，下钻可一次处理两人。这正是本需求要达到的效果。

边界：若 X 行 group_dept=「信贷部」而 Y 行 group_dept='' 且 be_eval_dept=「信贷部」，二者有效分组键都是「信贷部」，会归入同一组——符合回退语义，可接受。

## 7. 管理端导出

`EvalAssignAdminService.exportItems`：表头在「被打分人部门」之后插入「分组部门」列，列数 13 → 14，值取 `it.getGroupDept()`（空串照写）。

## 8. 前端

- `MyTasks.vue`：汇总表原「部门」列表头改为「**分组部门**」；数据仍绑定 `row.dept`（有效分组值，group_dept 空时即被打分人部门）。下钻明细表的「部门」列**保持**显示各人真实 `beEvalDept`（分组跨部门时逐人真实部门更有意义）。
- 管理端批次详情页 `Tasks.vue`：若其明细表展示了部门列，同步补「分组部门」列（实现时按实际列结构处理）；导入相关的列说明/提示文案若有硬编码列清单，同步为 11 列。

## 9. 兼容性与迁移

- 存量 `EVAL_ASSIGN_ITEM` 行 `group_dept` 默认 ''，有效分组键回退 be_eval_dept，行为与旧版一致。
- DDL 脚本幂等，可安全重跑。
- 唯一键、索引不变。

## 10. 测试计划（TDD 红-绿-重构）

- **Mapper IT**（扩展现有 eval assign mapper 测试）：
  1. group_dept 非空 → 按 group_dept 汇总（`selectPendingGroups` 返回 dept=group_dept，跨 be_eval_dept 合并计数）。
  2. group_dept 空 → 回退 be_eval_dept 汇总（等价旧行为）。
  3. 混合场景 `selectByScorerBatchDept` 下钻：dept=有效分组键时命中正确明细集合。
- **Import service 单测**：
  4. `validate` 将 group_dept 映射进 `EvalAssignItem`（含 trim、空值存空串）。
  5. 模板/行模型列数与列名断言（11 列，第 4 列为「分组部门」）。
- 每步独立 commit，先写失败测试（Red）→ 最简实现（Green）→ 重构。

## 11. 交付清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `docs/schema/ddl-eval.sql` | EVAL_ASSIGN_ITEM 加 GROUP_DEPT 列 |
| 2 | `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql` | 增量 DDL（幂等，手工执行） |
| 3 | `eval/entity/EvalAssignItem.java` | 加 groupDept 字段 |
| 4 | `eval/dto/EvalAssignImportRow.java` | 第 4 列加 @ExcelProperty("分组部门") groupDept |
| 5 | `eval/service/EvalAssignImportService.java` | validate 映射 groupDept |
| 6 | `eval/controller/EvalAssignAdminController.java` | 模板样例 + javadoc 列数 |
| 7 | `mapper/performance/EvalAssignItemMapper.xml` | BASE_COLUMNS / batchInsert / selectPendingGroups / selectByScorerBatchDept |
| 8 | `eval/service/EvalAssignAdminService.java` | 导出加「分组部门」列 |
| 9 | `xanzc_frontend/src/views/eval/MyTasks.vue` | 汇总列表头「分组部门」 |
| 10 | `xanzc_frontend/src/views/eval/Tasks.vue` | 管理端详情/导入列同步（按实际结构） |
| 11 | 测试类（mapper IT + import service 单测） | 新增/扩展用例 |
| 12 | `performance-engine-center/CLAUDE.md` | 追加 2026-07-11 变更条目 |
