# 评价任务新增「分组部门」汇总维度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为导入式评价任务（B 体系）新增「分组部门」字段，汇总/下钻以「分组部门优先、空则回退被打分人部门」为分组键，解决"给多个小部门打分需点开多次"的痛点。

**Architecture:** `EVAL_ASSIGN_ITEM` 加 `GROUP_DEPT` 列（导入快照）。汇总 SQL `selectPendingGroups` 与下钻 SQL `selectByScorerBatchDept` 的部门维度统一改为 `COALESCE(NULLIF(group_dept,''), be_eval_dept)`。导入模板第 4 列、管理端导出、前端两页同步。存量 `group_dept=''` → 行为等价旧逻辑。

**Tech Stack:** Spring Boot 3.2 + MyBatis(-Plus) + EasyExcel + Apache POI(SXSSF) 后端；Vue3 + Element Plus 前端；MySQL 8。TDD：Mapper IT 走真实库（`onepl_test_bootstrap`），Service 单测走 Mockito。

**参考 spec：** `docs/superpowers/specs/2026-07-11-eval-assign-group-dept-design.md`

---

## 文件结构（改动清单）

| 文件 | 职责 | 动作 |
|---|---|---|
| `docs/schema/ddl-eval.sql` | 基线 DDL | 加 GROUP_DEPT 列 |
| `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql` | 增量 DDL（手工执行） | 新建 |
| `eval/entity/EvalAssignItem.java` | 明细实体 | 加 groupDept 字段 |
| `mapper/performance/EvalAssignItemMapper.xml` | 汇总/下钻/插入 SQL | 4 处改动 |
| `eval/dto/EvalAssignImportRow.java` | 导入行模型 | 第 4 列加 groupDept |
| `eval/service/EvalAssignImportService.java` | 导入校验入库 | validate 映射 groupDept |
| `eval/controller/EvalAssignAdminController.java` | 模板下载 | 样例 + javadoc 列数 |
| `eval/service/EvalAssignAdminService.java` | 批次导出 | 加「分组部门」列 |
| `xanzc_frontend/src/views/eval/MyTasks.vue` | 用户端待处理 | 汇总列头「分组部门」 |
| `xanzc_frontend/src/views/eval/Tasks.vue` | 管理端批次 | 明细列 + 导入列说明 |
| `performance-engine-center/CLAUDE.md` | 模块变更日志 | 追加条目 |
| 测试：`mapper/EvalAssignItemMapperIT.java`（新建）、`EvalAssignImportServiceTest`、`EvalAssignAdminServiceTest` | 用例 | 新增/改 |

---

## Task 1: DDL — EVAL_ASSIGN_ITEM 新增 GROUP_DEPT 列

**Files:**
- Modify: `docs/schema/ddl-eval.sql`（EVAL_ASSIGN_ITEM 段，`BE_EVAL_TAG` 行之后）
- Create: `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql`

- [ ] **Step 1: 更新基线 DDL**

在 `docs/schema/ddl-eval.sql` 的 `EVAL_ASSIGN_ITEM` 建表中，`BE_EVAL_DEPT ...` 行之后新增一行（保持与 BE_EVAL_DEPT 相邻）：

```sql
    GROUP_DEPT        VARCHAR(200) NOT NULL DEFAULT '' COMMENT '分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）',
```

- [ ] **Step 2: 新建增量脚本（幂等）**

创建 `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql`：

```sql
-- 评价任务待处理明细新增「分组部门」列（汇总优先键，空串回退 be_eval_dept）
-- 目标库：yiti + onepl_test_bootstrap，手工执行（项目已废弃 Flyway）
-- 幂等：仅当列不存在时 ADD
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EVAL_ASSIGN_ITEM'
      AND COLUMN_NAME = 'GROUP_DEPT'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE EVAL_ASSIGN_ITEM ADD COLUMN GROUP_DEPT VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）'' AFTER BE_EVAL_DEPT',
    'SELECT ''GROUP_DEPT already exists, skip'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 3: 在测试库执行（Mapper IT 依赖真实列）**

Run:
```bash
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql
```
Expected: 输出无错误；重复执行输出 `GROUP_DEPT already exists, skip`。

验证列已存在：
```bash
mysql -uroot -pdjdev onepl_test_bootstrap -e "SHOW COLUMNS FROM EVAL_ASSIGN_ITEM LIKE 'GROUP_DEPT';"
```
Expected: 返回 1 行，Type=`varchar(200)`，Null=`NO`，Default=空串。

> 备注：`yiti` 开发库上线时同样执行本脚本。

- [ ] **Step 4: Commit**

```bash
git add docs/schema/ddl-eval.sql docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql
git commit -m "feat(perf-eval): EVAL_ASSIGN_ITEM 新增 GROUP_DEPT 列(分组部门汇总键)"
```

---

## Task 2: 实体 EvalAssignItem 加 groupDept 字段

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalAssignItem.java`

- [ ] **Step 1: 加字段（`beEvalDept` 之后，`beEvalTag` 之前，保持列序直觉）**

在 `beEvalDept` 声明之后插入：

```java
    /** 分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）. */
    private String groupDept;
```

（`map-underscore-to-camel-case` 全局开启，`group_dept` ↔ `groupDept` 自动映射，无需 @TableField。）

- [ ] **Step 2: 编译校验**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalAssignItem.java
git commit -m "feat(perf-eval): EvalAssignItem 加 groupDept 字段"
```

---

## Task 3: Mapper — 汇总/下钻按有效分组键 + batchInsert 落 group_dept

**Files:**
- Create Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/EvalAssignItemMapperIT.java`
- Modify: `performance-engine-center/src/main/resources/mapper/performance/EvalAssignItemMapper.xml`

- [ ] **Step 1: 写失败的 Mapper IT**

创建 `EvalAssignItemMapperIT.java`：

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalAssignItemMapper 汇总/下钻集成测试（分组部门优先，空回退被打分人部门）。
 * <p>测试数据前缀 {@code TEST_GD_*}，@Transactional + @Rollback 自动回滚。</p>
 */
class EvalAssignItemMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private EvalAssignItemMapper itemMapper;
    @Autowired
    private EvalAssignBatchMapper batchMapper;

    /** 建一个进行中(0)、截止未过的批次并返回其自增 ID。 */
    private Long newActiveBatch() {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setTaskType("EVAL");
        b.setBatchName("TEST_GD_批次");
        b.setSource("IMPORT");
        b.setDeadline(LocalDateTime.now().plusDays(7));
        b.setStatus(0);
        b.setCreateBy("TEST_GD_OP");
        b.setCreateTime(LocalDateTime.now());
        batchMapper.insert(b);
        return b.getBatchId();
    }

    private EvalAssignItem item(Long batchId, String evalUserId, String beUserId,
                                String beDept, String groupDept) {
        EvalAssignItem it = new EvalAssignItem();
        it.setBatchId(batchId);
        it.setEvalUserId(evalUserId);
        it.setEvalUserName("评");
        it.setEvalUserTag("t");
        it.setEvalUserDept("管理部");
        it.setBeEvalUserId(beUserId);
        it.setBeEvalUserName("被");
        it.setBeEvalDept(beDept);
        it.setBeEvalTag("t");
        it.setGroupDept(groupDept);
        it.setWeightTag("主要");
        it.setScoreType("NUM");
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("selectPendingGroups：group_dept 非空按分组部门汇总(跨被打分人部门合并计数)，空则回退 be_eval_dept")
    void selectPendingGroups_groupByEffectiveDept() {
        Long batchId = newActiveBatch();
        String ev = "TEST_GD_E1";
        itemMapper.batchInsert(List.of(
                item(batchId, ev, "TEST_GD_B1", "信贷部", "零售条线"),
                item(batchId, ev, "TEST_GD_B2", "零售部", "零售条线"),
                item(batchId, ev, "TEST_GD_B3", "对公部", "")));

        List<EvalPendingGroupDTO> groups = itemMapper.selectPendingGroups(ev);

        assertThat(groups).hasSize(2);
        EvalPendingGroupDTO retail = groups.stream()
                .filter(g -> "零售条线".equals(g.getDept())).findFirst().orElseThrow();
        assertThat(retail.getPendingCount()).isEqualTo(2); // B1+B2 跨部门合并
        EvalPendingGroupDTO corp = groups.stream()
                .filter(g -> "对公部".equals(g.getDept())).findFirst().orElseThrow();
        assertThat(corp.getPendingCount()).isEqualTo(1); // group_dept 空 → 回退 be_eval_dept
    }

    @Test
    @DisplayName("selectByScorerBatchDept：按有效分组键下钻(分组部门命中2条、回退部门命中1条)")
    void selectByScorerBatchDept_filterByEffectiveDept() {
        Long batchId = newActiveBatch();
        String ev = "TEST_GD_E2";
        itemMapper.batchInsert(List.of(
                item(batchId, ev, "TEST_GD_C1", "信贷部", "零售条线"),
                item(batchId, ev, "TEST_GD_C2", "零售部", "零售条线"),
                item(batchId, ev, "TEST_GD_C3", "对公部", "")));

        List<EvalAssignItem> retail = itemMapper.selectByScorerBatchDept(ev, batchId, "零售条线");
        assertThat(retail).hasSize(2);
        assertThat(retail).extracting(EvalAssignItem::getGroupDept).containsOnly("零售条线");

        List<EvalAssignItem> corp = itemMapper.selectByScorerBatchDept(ev, batchId, "对公部");
        assertThat(corp).hasSize(1);
        assertThat(corp.get(0).getBeEvalDept()).isEqualTo("对公部");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignItemMapperIT`
Expected: FAIL —— `batchInsert` 尚未写 group_dept 列（IT 前先 `mvn -q -pl <上游> install` 视需要），或汇总仍按 be_eval_dept 使「零售条线」计数不为 2。

- [ ] **Step 3: 改 Mapper XML（4 处）**

编辑 `EvalAssignItemMapper.xml`：

(a) `BASE_COLUMNS` 追加 `group_dept`（放在 `be_eval_dept` 之后）：
```xml
    <sql id="BASE_COLUMNS">
        item_id, batch_id, eval_user_id, eval_user_name, eval_user_tag, eval_user_dept,
        be_eval_user_id, be_eval_user_name, be_eval_dept, group_dept, be_eval_tag,
        weight_tag, score_type, score, submitted, submit_time
    </sql>
```

(b) `batchInsert` 列清单与 VALUES 各加 group_dept：
```xml
    <insert id="batchInsert">
        INSERT INTO EVAL_ASSIGN_ITEM
        (batch_id, eval_user_id, eval_user_name, eval_user_tag, eval_user_dept,
         be_eval_user_id, be_eval_user_name, be_eval_dept, group_dept, be_eval_tag,
         weight_tag, score_type, score, submitted, submit_time)
        VALUES
        <foreach collection="items" item="i" separator=",">
            (#{i.batchId}, #{i.evalUserId}, #{i.evalUserName}, #{i.evalUserTag}, #{i.evalUserDept},
             #{i.beEvalUserId}, #{i.beEvalUserName}, #{i.beEvalDept}, #{i.groupDept}, #{i.beEvalTag},
             #{i.weightTag}, #{i.scoreType}, #{i.score}, #{i.submitted}, #{i.submitTime})
        </foreach>
    </insert>
```

(c) `selectPendingGroups` 的 dept 维度改为有效分组键（SELECT 与 GROUP BY 同步）：
```xml
    <!-- 当前打分人未提交明细，按 批次 + 有效分组键(分组部门优先，空回退被打分人部门) 聚合 -->
    <select id="selectPendingGroups" resultType="com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO">
        SELECT i.batch_id        AS batchId,
               b.task_type       AS taskType,
               b.batch_name      AS taskName,
               COALESCE(NULLIF(i.group_dept, ''), i.be_eval_dept) AS dept,
               COUNT(*)          AS pendingCount,
               b.deadline        AS deadline
        FROM EVAL_ASSIGN_ITEM i
        JOIN EVAL_ASSIGN_BATCH b ON i.batch_id = b.batch_id
        WHERE i.eval_user_id = #{evalUserId} AND i.submitted = 0 AND b.status = 0
          AND b.deadline IS NOT NULL AND b.deadline > NOW()
        GROUP BY i.batch_id, b.task_type, b.batch_name,
                 COALESCE(NULLIF(i.group_dept, ''), i.be_eval_dept), b.deadline
        ORDER BY b.deadline ASC, i.batch_id ASC
    </select>
```

(d) `selectByScorerBatchDept` 的 WHERE 用同表达式匹配：
```xml
    <select id="selectByScorerBatchDept" resultType="com.bank.branch.platform.performance.eval.entity.EvalAssignItem">
        SELECT <include refid="BASE_COLUMNS"/> FROM EVAL_ASSIGN_ITEM
        WHERE eval_user_id = #{evalUserId} AND batch_id = #{batchId}
          AND COALESCE(NULLIF(group_dept, ''), be_eval_dept) = #{dept}
        ORDER BY submitted ASC, item_id ASC
    </select>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignItemMapperIT`
Expected: PASS（2 个用例绿）。

> 若报 stale jar / ConflictingBeanDefinition：先 `mvn -q clean install -DskipTests` 再重跑（见根 CLAUDE.md）。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/resources/mapper/performance/EvalAssignItemMapper.xml \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/EvalAssignItemMapperIT.java
git commit -m "feat(perf-eval): 汇总/下钻按有效分组键(分组部门优先,空回退被打分人部门)"
```

---

## Task 4: 导入行模型 + 校验映射 groupDept

**Files:**
- Modify: `eval/dto/EvalAssignImportRow.java`
- Modify: `eval/service/EvalAssignImportService.java:343`（`item.setBeEvalDept(...)` 附近）
- Test: `eval/service/EvalAssignImportServiceTest.java`

- [ ] **Step 1: 写失败的单测（groupDept 映射进 item）**

在 `EvalAssignImportServiceTest` 追加测试。注意现有 `row(...)` helper 为 10 参不含 groupDept，本测试直接构造行对象：

```java
    @Test
    @DisplayName("processImport：分组部门列映射进 item（含 trim），未填则存空串")
    void processImport_mapsGroupDept() {
        mockUsersExist("B1", "B2", "E1");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        EvalAssignImportRow withGroup = row("B1", "被一", "信贷部", "客户经理",
                "E1", "评一", "支行长", "管理部", "主要", "数值打分");
        withGroup.setGroupDept("  零售条线  "); // 前后空格验证 trim
        EvalAssignImportRow noGroup = row("B2", "被二", "零售部", "客户经理",
                "E1", "评一", "支行长", "管理部", "主要", "数值打分");
        // noGroup.groupDept 未设置 → null → 入库空串

        service.processImport(99L, List.of(withGroup, noGroup), "EVAL", "测试任务", deadline, "ADMIN");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<EvalAssignItem>> cap =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(itemMapper).batchInsert(cap.capture());
        java.util.List<EvalAssignItem> items = cap.getValue();
        assertThat(items.get(0).getGroupDept()).isEqualTo("零售条线");
        assertThat(items.get(1).getGroupDept()).isEqualTo("");
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignImportServiceTest#processImport_mapsGroupDept`
Expected: FAIL —— `EvalAssignImportRow` 无 `setGroupDept` 方法（编译失败）或 item.groupDept 为 null。

- [ ] **Step 3: 加 ImportRow 第 4 列**

在 `EvalAssignImportRow.java` 的 `beEvalDept` 之后、`beEvalTag` 之前插入，并把类 javadoc「10 列」改为「11 列」：

```java
    @ExcelProperty("被打分员工部门")
    private String beEvalDept;
    @ExcelProperty("分组部门")
    private String groupDept;
    @ExcelProperty("被打分员工标签")
    private String beEvalTag;
```

- [ ] **Step 4: 校验时映射 groupDept**

在 `EvalAssignImportService.validate` 组装 item 处（`item.setBeEvalDept(trim(r.getBeEvalDept()));` 之后）加一行：

```java
            item.setBeEvalDept(trim(r.getBeEvalDept()));
            item.setGroupDept(trim(r.getGroupDept()));
```

- [ ] **Step 5: 跑测试确认通过（含既有用例不回归）**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignImportServiceTest`
Expected: PASS（新用例绿，既有 ~15 用例保持绿——旧 `row(...)` 未设 groupDept，入库为空串，不影响既有断言）。

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalAssignImportRow.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalAssignImportService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalAssignImportServiceTest.java
git commit -m "feat(perf-eval): 导入模板新增分组部门列(第4列)+校验映射"
```

---

## Task 5: 管理端导出新增「分组部门」列

**Files:**
- Modify: `eval/service/EvalAssignAdminService.java:195-197`（表头）与 `:217-221`（数据行）
- Test: `eval/service/EvalAssignAdminServiceTest.java`

- [ ] **Step 1: 改现有导出测试的列索引 + 加分组部门断言**

导出列在「被打分人部门」(旧 col7) 后插入「分组部门」，使「评价类型」由 col9 → col10。更新 `exportItems_scoreTypeColumn_showsDictLabel`：把两处 `getCell(9)` 改为 `getCell(10)`，并在断言前补分组部门列断言。改后该测试方法体核心断言为：

```java
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            // 第 8 列(0-based)=分组部门（新插入，被打分人部门之后）
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("分组部门");
            // 第 10 列=评价类型（因分组部门插入右移 1）
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("评价类型");
            Row r1 = sheet.getRow(1);
            Row r2 = sheet.getRow(2);
            assertThat(r1.getCell(10).getStringCellValue()).isEqualTo("数值打分");
            assertThat(r2.getCell(10).getStringCellValue()).isEqualTo("等级打分");
        }
```

并给 helper `item(...)` 补 `it.setGroupDept("零售条线");`（放在 setScoreType 前后均可），再在 `exportItems_scoreTypeColumn_showsDictLabel` 追加：
```java
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("零售条线");
```

> `exportItems_userIdColumns_showUsername` 断言的是 col0/col4，不受右移影响，无需改。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignAdminServiceTest#exportItems_scoreTypeColumn_showsDictLabel`
Expected: FAIL —— 当前 col8=权重标签、col9=评价类型，断言 col8=分组部门 / col10=评价类型 不成立。

- [ ] **Step 3: 导出加列**

在 `EvalAssignAdminService.exportItems` 表头数组的 `"被打分人部门"` 之后插入 `"分组部门"`：

```java
            String[] cols = {"打分人工号", "打分人姓名", "打分人标签", "打分人部门",
                    "被打分人工号", "被打分人姓名", "被打分人标签", "被打分人部门", "分组部门",
                    "权重标签", "评价类型", "分数", "提交状态", "提交时间"};
```

数据行：原 `row.createCell(7)`（被打分人部门）之后插入 col8=分组部门，并把后续 cell 索引整体 +1（8→9 权重、9→10 评价类型、10→11 分数、11→12 提交状态、12→13 提交时间）：

```java
                    row.createCell(7).setCellValue(it.getBeEvalDept() == null ? "" : it.getBeEvalDept());
                    row.createCell(8).setCellValue(it.getGroupDept() == null ? "" : it.getGroupDept());
                    row.createCell(9).setCellValue(it.getWeightTag() == null ? "" : it.getWeightTag());
                    String st = it.getScoreType();
                    row.createCell(10).setCellValue(st == null ? ""
                            : scoreTypeLabelCache.computeIfAbsent(st, k -> dictApi.getDictLabel(DICT_SCORE_TYPE, k)));
                    row.createCell(11).setCellValue(it.getScore() == null ? "" : String.valueOf(it.getScore()));
                    row.createCell(12).setCellValue(it.getSubmitted() != null && it.getSubmitted() == 1 ? "已提交" : "未提交");
                    row.createCell(13).setCellValue(it.getSubmitTime() == null ? "" : it.getSubmitTime().format(dtf));
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignAdminServiceTest`
Expected: PASS（3 用例全绿）。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalAssignAdminService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalAssignAdminServiceTest.java
git commit -m "feat(perf-eval): 批次导出新增分组部门列"
```

---

## Task 6: 导入模板下载样例 + Controller javadoc

**Files:**
- Modify: `eval/controller/EvalAssignAdminController.java:44-65`

- [ ] **Step 1: 模板样例补分组部门 + javadoc 列数**

在 `importTemplate` 的样例装配中（`sample.setBeEvalDept("信贷部");` 之后）加：

```java
        sample.setBeEvalDept("信贷部");
        sample.setGroupDept("零售条线");
```

并把方法上方 javadoc `下载评价任务导入模板（10 列）` 改为 `（11 列）`；类 javadoc 若含「10 列」表述同步为「11 列」。

- [ ] **Step 2: 编译校验**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS。

> 无需新增单测：模板列由 `EvalAssignImportRow` 的 @ExcelProperty 决定，已在 Task 4 覆盖；此步仅样例值与文档。

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalAssignAdminController.java
git commit -m "feat(perf-eval): 导入模板样例补分组部门列 + javadoc 11 列"
```

---

## Task 7: 前端 — 用户端汇总列头 + 管理端明细列/导入说明

**Files:**
- Modify: `xanzc_frontend/src/views/eval/MyTasks.vue:24-26`
- Modify: `xanzc_frontend/src/views/eval/Tasks.vue:142`、`:242`

- [ ] **Step 1: MyTasks.vue 汇总列头改「分组部门」**

把汇总表的部门列（第 24-26 行）label 改为「分组部门」（数据仍绑 `row.dept`，group_dept 为空时后端已回退为被打分人部门）：

```html
        <el-table-column prop="dept" label="分组部门" min-width="180">
          <template #default="{ row }">{{ row.dept || '—' }}</template>
        </el-table-column>
```

> 处理视图（明细打分）内的「部门」列绑 `beEvalDept`，保持不动——分组跨部门时逐人真实部门更有意义。

- [ ] **Step 2: Tasks.vue 导入列说明 10→11 列**

把第 142 行 form-tip 文案更新为 11 列并列出分组部门：

```html
                  <span class="form-tip">11 列：被打分人 编号/姓名/部门 + 分组部门 + 被打分人标签 + 打分人 编号/姓名/标签/部门 + 权重标签 + 评价类型</span>
```

- [ ] **Step 3: Tasks.vue 明细表加「分组部门」列**

在被打分人部门列（第 242 行 `beEvalDept`）之后插入：

```html
          <el-table-column prop="beEvalDept" label="被打分人部门" min-width="120" />
          <el-table-column prop="groupDept" label="分组部门" min-width="120" />
```

（`getBatchDetail` 返回的 `EvalAssignItem` 已含 `groupDept`，前端直接渲染。）

- [ ] **Step 4: 前端构建校验**

Run: `cd xanzc_frontend && npm run build`
Expected: 构建成功，无语法错误。（若本地未装依赖，退而求其次用 `npx vite build` 或跳过，交由 CI。）

- [ ] **Step 5: Commit**

```bash
git add xanzc_frontend/src/views/eval/MyTasks.vue xanzc_frontend/src/views/eval/Tasks.vue
git commit -m "feat(perf-fe): 评价待处理汇总列头改分组部门 + 管理端明细/导入说明补分组部门"
```

---

## Task 8: 回归 + 模块变更日志

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（2026-06-10 待处理任务条目附近追加）

- [ ] **Step 1: 全量回归 perf 模块测试**

Run:
```bash
mvn -q clean install -DskipTests
mvn -q -pl performance-engine-center test
```
Expected: surefire 全绿（含 `EvalAssignImportServiceTest` / `EvalAssignAdminServiceTest`）。

- [ ] **Step 2: 跑 Mapper IT（failsafe/真实库）**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalAssignItemMapperIT`
Expected: PASS（2 用例）。

- [ ] **Step 3: 追加 CLAUDE.md 变更条目**

在 `performance-engine-center/CLAUDE.md` 的 eval 子域 2026-06-10 条目之后追加一段：

```markdown
> 2026-07-11 eval 子域：待处理任务新增「分组部门」汇总维度——`EVAL_ASSIGN_ITEM` 加 `GROUP_DEPT`(NOT NULL DEFAULT '')，导入模板由 10→11 列（第 4 列「分组部门」，可空、无字典校验、导入快照）。用户端「待处理任务」汇总/下钻分组键由 `be_eval_dept` 改为有效分组键 `COALESCE(NULLIF(group_dept,''), be_eval_dept)`（分组部门优先、空回退被打分人部门），解决"给多个小部门各 1 人打分需点开多次"痛点。管理端批次导出加「分组部门」列(13→14)。前端 MyTasks.vue 汇总列头→「分组部门」、Tasks.vue 明细加列 + 导入说明 11 列。存量 group_dept='' 行为等价旧逻辑。迁移脚本 `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql`（幂等，yiti + onepl_test_bootstrap 手工执行）。spec/plan：`docs/superpowers/specs/2026-07-11-eval-assign-group-dept-design.md` / `docs/superpowers/plans/2026-07-11-eval-assign-group-dept-impl.md`。
```

- [ ] **Step 4: Commit（含 spec/plan 落库）**

```bash
git add performance-engine-center/CLAUDE.md \
        docs/superpowers/specs/2026-07-11-eval-assign-group-dept-design.md \
        docs/superpowers/plans/2026-07-11-eval-assign-group-dept-impl.md
git commit -m "docs(perf-eval): 分组部门汇总维度 spec/plan + CLAUDE.md 变更日志"
```

---

## Self-Review 记录

- **Spec 覆盖**：DDL(§4.1)→T1；实体(§4.2)→T2；汇总/下钻 SQL(§6)→T3；导入列+校验(§5)→T4；导出(§7)→T5；模板样例(§5.3)→T6；前端(§8)→T7；兼容(§9)/文档(§11)→T8。全部有对应任务。
- **无占位**：各步含完整代码/命令/期望输出。
- **类型/命名一致**：字段 `groupDept`↔列 `group_dept`；SQL 表达式 `COALESCE(NULLIF(group_dept,''), be_eval_dept)` 在 T3 的 (c)(d) 一致；导出 cell 索引右移在 T5 测试(Step1)与实现(Step3)一致（分组部门=col8，评价类型=col10）。
- **列序风险**：导入第 4 列插入使旧模板文件列错位（spec §5.1 已确认接受）；导出列右移已同步更新既有测试索引，避免假失败。
