# 人员标签页 工号字符型治理 + 导入/导出/模板/查询按钮 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 先把 eval 模块 4 处工号列从 BIGINT 治理为 VARCHAR(50)（工号本质字符型），再在其上为人员标签页新增显式查询按钮、Excel 同步原子导入（全部校验通过才入库）、导入模板下载、按关键词导出。

**Architecture:** Phase 0 把 `EVAL_USER_TAG.USER_ID / EVAL_TASK_TARGET.BE_EVAL_USER_ID / EVAL_SCORE.EVAL_USER_ID / EVAL_TASK.CREATE_BY` 改 VARCHAR，并把 eval 子域所有承载工号的 `Long` 贯通改 `String`，删除 `toLongOrNull` / `Long.parseLong` 转换。Phase 1 在 String 化的 `saveUserRoles(String, …)` 之上新增 3 个 REST 端点与 `EvalUserTagImportService`；工号有效性复用既有 `UserApi.getUserByEmpIds`，**不改 auth**。前端 `eval.js` + `UserTags.vue` 增加查询/重置/导入/下载。

**Tech Stack:** Spring Boot 3.2.3 / MyBatis-Plus / EasyExcel（perf 已依赖）/ Vue3 `<script setup>` + Element Plus。

**Spec:** `docs/superpowers/specs/2026-05-29-eval-user-tags-import-export-design.md`（§3.0 工号字符型治理）

**子代理派遣红线：** 所有 subagent 的 model 须为高能力模型，禁用低配模型。

**前置事实（实现者必读）:**
- 现网 yiti 库：`EVAL_USER_TAG` 仅 3 行（USER_ID=3431，数字字符串）；`PT_USER.USER_ID` 为 varchar(50)，样例工号 `2280/3431` 等。
- `UserApi.getUserByEmpIds(List<String>) → List<UserDTO>`（`UserDTO.getEmpId()/getDisplayName()/getUsername()`）。
- `EvalTagMapper.selectAll(Integer tagType, Integer status)` 返回全部标签（status=1 启用）；`EvalTag.getTagId()/getTagName()/getTagType()`。
- `PerfException` 用法：`throw new PerfException(PerfErrorCode.XXX, args...)`；现有最大 eval 码 `PERF-40059`。
- EasyExcel：读 `EasyExcel.read(is).head(Row.class).sheet().doReadSync()`；写 `EasyExcel.write(os, Row.class).sheet(name).doWrite(rows)`。
- 测试 mock：`PerfTestConfig` 已提供 `@Bean @Primary UserApi`。纯 mock service 测试命名 `*Test.java`（surefire）。
- 前端 `call(method, url, config, fallback)`；上传范式见 `perf.js` `uploadImportFile`；http.js 拦截器对非 envelope（Blob）原样返回；分页响应解包为 `{records,total}`。

---

## Phase 0 — 工号字符型治理（前置基础，必须先完成）

### Task 1: DB 迁移（4 列 BIGINT → VARCHAR(50)）+ 更新基线 DDL

**Files:**
- Create: `docs/superpowers/sql/2026-05-29-eval-userid-to-varchar.sql`
- Modify: `docs/schema/ddl-eval.sql`（4 处列定义）

- [ ] **Step 1: 编写迁移 SQL**

```sql
-- ============================================================
-- eval 模块工号列 BIGINT → VARCHAR(50) 治理
-- 日期: 2026-05-29
-- 背景: 工号本质字符型（PT_USER.USER_ID varchar(50)），eval 4 处误用 BIGINT。
-- 现网 EVAL_USER_TAG 仅少量行且为数字字符串，转换无损。
-- 注意: 仅改 USER_ID/CREATE_BY 列；TAG_ID/TASK_ID/RULE_ID/GROUP_ID/SCORE_ID/TARGET_ID 等主键/外键保持 BIGINT。
-- ============================================================

ALTER TABLE EVAL_USER_TAG    MODIFY COLUMN USER_ID         VARCHAR(50) NOT NULL COMMENT '人员工号，关联 PT_USER.USER_ID';
ALTER TABLE EVAL_TASK_TARGET MODIFY COLUMN BE_EVAL_USER_ID VARCHAR(50) NOT NULL COMMENT '被评价人工号';
ALTER TABLE EVAL_SCORE       MODIFY COLUMN EVAL_USER_ID    VARCHAR(50) NOT NULL COMMENT '评价人工号';
ALTER TABLE EVAL_TASK        MODIFY COLUMN CREATE_BY        VARCHAR(50) DEFAULT NULL COMMENT '创建人工号';
```

- [ ] **Step 2: 同步更新基线 DDL `docs/schema/ddl-eval.sql`**

把以下 4 处列定义的 `BIGINT` 改为 `VARCHAR(50)`（注释同步为"工号"）：
- `EVAL_USER_TAG`: `USER_ID  BIGINT NOT NULL COMMENT '人员ID，关联 PT_USER.USER_ID'` → `USER_ID  VARCHAR(50) NOT NULL COMMENT '人员工号，关联 PT_USER.USER_ID'`
- `EVAL_TASK`: `CREATE_BY   BIGINT       DEFAULT NULL COMMENT '创建人 USER_ID'` → `CREATE_BY   VARCHAR(50)  DEFAULT NULL COMMENT '创建人工号'`
- `EVAL_TASK_TARGET`: `BE_EVAL_USER_ID  BIGINT        NOT NULL COMMENT '被评价人 USER_ID'` → `BE_EVAL_USER_ID  VARCHAR(50)   NOT NULL COMMENT '被评价人工号'`
- `EVAL_SCORE`: `EVAL_USER_ID  BIGINT    NOT NULL COMMENT '评价人 USER_ID'` → `EVAL_USER_ID  VARCHAR(50)   NOT NULL COMMENT '评价人工号'`

> `UK_USER_TAG(USER_ID, TAG_ID)`、`UK_TASK_USER(TASK_ID, BE_EVAL_USER_ID)`、`UK_TARGET_EVALUATOR(TARGET_ID, EVAL_USER_ID)` 等唯一键定义不变（VARCHAR 列可继续做唯一键）。

- [ ] **Step 3: 在本地 yiti 库执行迁移**

Run（windows MySQL，localhost:3306/yiti root/djdev）执行 `2026-05-29-eval-userid-to-varchar.sql`，然后核对：
```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='yiti' AND TABLE_NAME LIKE 'EVAL_%'
  AND (COLUMN_NAME LIKE '%USER_ID%' OR COLUMN_NAME='CREATE_BY');
```
Expected: 4 列均为 `varchar(50)`。

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/sql/2026-05-29-eval-userid-to-varchar.sql docs/schema/ddl-eval.sql
git commit -m "chore(eval): 工号列 BIGINT→VARCHAR(50) 迁移 SQL + 基线 DDL 更新"
```

---

### Task 2: eval 子域 Long → String 贯通（实体/DTO/Mapper/Service/Controller + 测试 fixture）

> 这是一次"类型治理"重构：保持行为不变，把工号从 `Long` 改为 `String`。因 14 个源文件编译相互依赖，必须**一并修改后再编译**；随后修正 3 个测试 fixture，跑 eval 全量单测验证零行为变化。改完不应有任何 `toLongOrNull` / `Long.parseLong(empId)` / `String.valueOf(userId)` 残留。

**Files（源 14）:**
- Modify: `eval/entity/EvalUserTag.java`、`eval/entity/EvalTaskTarget.java`、`eval/entity/EvalScore.java`、`eval/entity/EvalTask.java`
- Modify: `eval/dto/EvalUserTagRow.java`
- Modify: `eval/mapper/EvalUserTagMapper.java`、`eval/mapper/EvalScoreMapper.java`
- Modify: `src/main/resources/mapper/performance/EvalUserTagMapper.xml`
- Modify: `eval/service/EvalUserTagService.java`、`eval/service/EvalTaskService.java`、`eval/service/EvalScoreService.java`
- Modify: `eval/controller/EvalUserTagController.java`、`eval/controller/EvalTaskController.java`、`eval/controller/EvalScoreController.java`

**Files（测试 3）:**
- Modify: `src/test/java/.../eval/service/EvalUserRoleServiceTest.java`、`EvalScoreServiceTest.java`、`EvalTaskServiceTest.java`

（以上相对路径基于 `performance-engine-center/src/main/java/com/bank/branch/platform/performance/` 与 `.../src/test/java/...`。）

- [ ] **Step 1: 改 4 个实体字段 Long → String**

- `EvalUserTag.java`：`private Long userId;` → `private String userId;`
- `EvalTaskTarget.java`：`private Long beEvalUserId;` → `private String beEvalUserId;`
- `EvalScore.java`：`private Long evalUserId;` → `private String evalUserId;`
- `EvalTask.java`：`private Long createBy;` → `private String createBy;`

- [ ] **Step 2: 改 DTO `EvalUserTagRow.userId` Long → String**

`EvalUserTagRow.java`：`private Long userId;` → `private String userId;`（注释由"BIGINT"改为"工号"）。

- [ ] **Step 3: 改 Mapper 接口签名**

`EvalUserTagMapper.java`：
- `List<EvalUserTag> selectByUserId(@Param("userId") Long userId);` → `String userId`
- `List<Long> selectUserIdsByTagId(@Param("tagId") Long tagId);` → 返回 `List<String>`
- `List<Long> selectTagIdsByUserIdAndType(@Param("userId") Long userId, @Param("tagType") Integer tagType);` → `userId` 参数改 `String`（返回仍 `List<Long>` 是 tag_id，不变）
- `void batchDelete(@Param("userId") Long userId, @Param("tagIds") List<Long> tagIds);` → `userId` 参数改 `String`
- `List<EvalUserTagRow> selectUserTagsByUserIds(@Param("userIds") List<Long> userIds);` → 参数 `List<String> userIds`

`EvalScoreMapper.java`：
- `selectByTaskIdAndEvalUserId(@Param("taskId") Long taskId, @Param("evalUserId") Long evalUserId)` → `evalUserId` 参数改 `String`
- `countByTargetIdAndEvalUserId(@Param("targetId") Long targetId, @Param("evalUserId") Long evalUserId)` → `evalUserId` 参数改 `String`

- [ ] **Step 4: 改 `EvalUserTagMapper.xml` resultType**

把 `selectUserIdsByTagId` 的 `resultType="java.lang.Long"` 改为 `resultType="java.lang.String"`。其余语句 SQL 文字不变（`#{userId}` 等绑定随参数类型自动适配，`EvalUserTagRow.userId` 改 String 后 `selectUserTagsByUserIds` 投影自动对齐）。

- [ ] **Step 5: 改 `EvalUserTagService` 方法签名并删除 toLongOrNull 链**

把以下公开方法的 `Long userId` 参数改 `String userId`：`getByUserId`、`batchBind`、`batchUnbind`、`saveUserRoles`；`getUserIdsByTagId(Long tagId)` 返回类型 `List<Long>` → `List<String>`。

`pageUserRoles` 内消除 `toLongOrNull`/`numericIds`：直接用 `empIds`（List<String>）查标签，`tagMap` 改 `Map<String, List<EvalUserTagRow>>`。把这段：
```java
        List<Long> numericIds = empIds.stream().map(this::toLongOrNull).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<EvalUserTagRow>> tagMap = new HashMap<>();
        if (!numericIds.isEmpty()) {
            for (EvalUserTagRow r : evalUserTagMapper.selectUserTagsByUserIds(numericIds)) {
                tagMap.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
            }
        }
```
改为：
```java
        Map<String, List<EvalUserTagRow>> tagMap = new HashMap<>();
        if (!empIds.isEmpty()) {
            for (EvalUserTagRow r : evalUserTagMapper.selectUserTagsByUserIds(empIds)) {
                tagMap.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
            }
        }
```
并把循环体里：
```java
            Long numId = toLongOrNull(u.getEmpId());
            List<EvalUserTagRow> tagRows = (numId == null) ? List.of() : tagMap.getOrDefault(numId, List.of());
```
改为：
```java
            List<EvalUserTagRow> tagRows = tagMap.getOrDefault(u.getEmpId(), List.of());
```
最后**删除** `private Long toLongOrNull(String s)` 方法，并清理不再用到的 import（如确认 `Objects` 仍被其它处使用则保留）。

- [ ] **Step 6: 改 `EvalTaskService`**

- `createTask(String taskName, LocalDateTime endTime, List<Long> beEvalUserIds, Long createBy)` → `List<String> beEvalUserIds, String createBy`
- 循环 `for (Long userId : beEvalUserIds)` → `for (String userId : beEvalUserIds)`
- `evalUserTagMapper.selectTagIdsByUserIdAndType(userId, 1)`、`target.setBeEvalUserId(userId)`、`task.setCreateBy(createBy)` 随类型自动适配，无需额外改。

- [ ] **Step 7: 改 `EvalScoreService`**

把以下方法的 `evalUserId`/`beEvalUserId` 参数 `Long` 改 `String`：`submitScore`、`getScoresByTaskAndUser`、`resolveScoreModeForUser`、私有 `resolveGroup`、私有 `isSameOrg`。`isSameOrg` 内的 `String.valueOf(userIdA)/String.valueOf(userIdB)` 包装直接删除（参数已是 String）。`evalUserTagMapper.selectTagIdsByUserIdAndType(evalUserId, 2)`、`countByTargetIdAndEvalUserId(targetId, evalUserId)`、`evalScore.setEvalUserId(evalUserId)` 随类型自动适配。

- [ ] **Step 8: 改 3 个 Controller**

`EvalUserTagController.java`：
- `list(@RequestParam("userId") Long userId)` → `String userId`
- `saveRoles(@PathVariable("userId") Long userId, ...)` → `String userId`
- 内部类 `BindReq`：`private Long userId;` → `private String userId;`

`EvalTaskController.java`：
- 内部类 `CreateTaskReq`：`private List<Long> beEvalUserIds;` → `private List<String> beEvalUserIds;`
- 把 `Long createBy = Long.parseLong(empIdStr);` 改为 `String createBy = empIdStr;`（删除 parseLong）

`EvalScoreController.java`：
- 两处 `Long evalUserId = Long.parseLong(empIdStr);` 改为 `String evalUserId = empIdStr;`（删除 parseLong）

- [ ] **Step 9: 编译（源码）**

Run: `mvn -q -pl performance-engine-center -am compile -DskipTests 2>&1 | tail -20`
Expected: BUILD SUCCESS（如报错，多为漏改的 Long/String 不匹配，按报错点补齐）

- [ ] **Step 10: 修正测试 fixture（3 文件）**

把以下测试里承载工号的 Long 字面量改成 String 字面量（**只改工号相关**，taskId/targetId/tagId/ruleId 等主键保持 Long）：

`EvalUserRoleServiceTest.java`：
- `tagRow` helper 形参 `long uid` → `String uid`；其内部 `r.setUserId(uid)` 不变。
- 所有 `setUserId(1001L)` / `selectByUserId(1001L)` / `saveUserRoles(1001L, …)` / `batchDelete(eq(1001L), …)` / `selectUserTagsByUserIds(List.of(1001L))` / `tagRow(1001L, …)` 中的 `1001L` → `"1001"`。

`EvalScoreServiceTest.java`：
- 所有 `Long evalUserId = 100L;` → `String evalUserId = "100";`；调用/mock/断言里直接出现的工号 `100L` → `"100"`（如 `submitScore(taskId, 10L, 100L, 9)` 的第三参）。注意 `taskId`/`targetId`（如 `1L`/`10L` 作为任务/目标主键）保持 Long。

`EvalTaskServiceTest.java`：
- `createTask("任务A", pastTime, List.of(101L), 1L)` → `createTask("任务A", pastTime, List.of("101"), "1")`（beEvalUserIds 与 createBy 改 String）。

- [ ] **Step 11: 跑 eval 全量单测验证零回归**

Run: `mvn -q -pl performance-engine-center test -Dtest='EvalUserRoleServiceTest,EvalScoreServiceTest,EvalTaskServiceTest,EvalTagServiceTest,EvalRuleServiceTest' 2>&1 | tail -25`
Expected: 全部通过（5 个测试类全绿）

- [ ] **Step 12: Commit**

```bash
git add performance-engine-center/src/main performance-engine-center/src/test
git commit -m "refactor(eval): 工号 Long→String 贯通治理（实体/Mapper/Service/Controller/测试）"
```

---

## Phase 1 — 导入 / 导出 / 模板 / 查询按钮（建立在 String 工号之上）

### Task 3: Excel 行模型与导入结果 DTO

**Files:**
- Create: `performance-engine-center/.../eval/dto/EvalUserTagImportRow.java`
- Create: `performance-engine-center/.../eval/dto/EvalUserRoleExportRow.java`
- Create: `performance-engine-center/.../eval/dto/EvalUserTagImportResultDTO.java`

- [ ] **Step 1: 导入行模型 `EvalUserTagImportRow`**

```java
package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员评价角色导入 Excel 行模型（3 列，填角色名称）。 */
@Data
public class EvalUserTagImportRow {
    @ExcelProperty("工号")
    private String empId;
    @ExcelProperty("被评价角色")
    private String beEvalRoleName;
    @ExcelProperty("评价角色")
    private String evalRoleNames;
}
```

- [ ] **Step 2: 导出行模型 `EvalUserRoleExportRow`**

```java
package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员标签列表导出 Excel 行模型，列序与页面列表一致。 */
@Data
public class EvalUserRoleExportRow {
    @ExcelProperty("姓名")
    private String userName;
    @ExcelProperty("工号")
    private String empId;
    @ExcelProperty("部门")
    private String orgName;
    @ExcelProperty("岗位")
    private String position;
    @ExcelProperty("角色")
    private String roleNames;
    @ExcelProperty("被评价人角色")
    private String beEvalRole;
    @ExcelProperty("评价人角色")
    private String evalRoles;
}
```

- [ ] **Step 3: 导入结果 DTO `EvalUserTagImportResultDTO`**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/** 人员评价角色导入结果：success=true 时 importedCount 为入库条数；否则 errors 非空且不写库。 */
@Data
public class EvalUserTagImportResultDTO {
    private boolean success;
    private int importedCount;
    private List<RowError> errors = new ArrayList<>();

    /** 单行错误：Excel 行号（从 1 开始，不含表头）+ 工号 + 原因。 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RowError {
        private int row;
        private String empId;
        private String message;
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q -pl performance-engine-center -am compile -DskipTests 2>&1 | tail -15`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagImportRow.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleExportRow.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagImportResultDTO.java
git commit -m "feat(eval): 人员角色导入/导出 Excel 行模型与结果 DTO"
```

---

### Task 4: 新增导入文件错误码 (PerfErrorCode)

**Files:**
- Modify: `performance-engine-center/.../enums/PerfErrorCode.java`

- [ ] **Step 1: 在 `EVAL_TAG_TYPE_MISMATCH` 行后追加 3 个错误码**

把：
```java
    EVAL_TAG_TYPE_MISMATCH("PERF-40059", "标签类型不匹配");
```
改为：
```java
    EVAL_TAG_TYPE_MISMATCH("PERF-40059", "标签类型不匹配"),
    EVAL_IMPORT_FILE_EMPTY("PERF-40060", "导入文件为空"),
    EVAL_IMPORT_FILE_INVALID("PERF-40061", "导入文件解析失败"),
    EVAL_IMPORT_ROWS_EXCEEDED("PERF-40062", "导入行数超过上限");
```

- [ ] **Step 2: 编译验证**

Run: `mvn -q -pl performance-engine-center -am compile -DskipTests 2>&1 | tail -15`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java
git commit -m "feat(eval): 新增导入文件级错误码 PERF-40060~40062"
```

---

### Task 5: EvalUserTagService 抽取 assembleRows + 新增 listForExport（TDD）

**Files:**
- Modify: `performance-engine-center/.../eval/service/EvalUserTagService.java`
- Test: `performance-engine-center/src/test/java/.../eval/service/EvalUserTagExportTest.java`

> 经 Task 2 治理，`pageUserRoles` 已是 String 化、`tagMap` 为 `Map<String,...>`、无 `toLongOrNull`。本任务把 records→rows 拼装抽成 `assembleRows`，并新增循环翻页累积的 `listForExport`。

- [ ] **Step 1: 写失败测试 `EvalUserTagExportTest`**

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.portal.api.AddressBookApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalUserTagExportTest {

    @Mock EvalUserTagMapper evalUserTagMapper;
    @Mock EvalTagMapper evalTagMapper;
    @Mock UserApi userApi;
    @Mock AddressBookApi addressBookApi;
    @InjectMocks EvalUserTagService service;

    private UserDTO user(String empId, String name) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setDisplayName(name);
        return u;
    }

    @Test
    void listForExport_accumulatesAcrossPages() {
        List<UserDTO> page1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) page1.add(user("E1" + i, "U" + i));
        List<UserDTO> page2 = new ArrayList<>();
        for (int i = 0; i < 50; i++) page2.add(user("E2" + i, "V" + i));
        when(userApi.pageUsers(eq("k"), eq(1), eq(100))).thenReturn(PageResult.of(1, 100, 150, page1));
        when(userApi.pageUsers(eq("k"), eq(2), eq(100))).thenReturn(PageResult.of(2, 100, 150, page2));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());

        List<EvalUserRoleRowDTO> rows = service.listForExport("k", 10000);

        assertThat(rows).hasSize(150);
        assertThat(rows.get(0).getUserName()).isEqualTo("U0");
        assertThat(rows.get(149).getUserName()).isEqualTo("V49");
    }

    @Test
    void listForExport_stopsAtCap() {
        List<UserDTO> page1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) page1.add(user("E1" + i, "U" + i));
        when(userApi.pageUsers(eq(null), eq(1), eq(100))).thenReturn(PageResult.of(1, 100, 500, page1));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());

        List<EvalUserRoleRowDTO> rows = service.listForExport(null, 100);

        assertThat(rows).hasSize(100);
    }

    @Test
    void listForExport_emptyWhenNoUsers() {
        when(userApi.pageUsers(any(), eq(1), eq(100)))
                .thenReturn(PageResult.of(1, 100, 0, List.of()));

        List<EvalUserRoleRowDTO> rows = service.listForExport("none", 10000);

        assertThat(rows).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagExportTest 2>&1 | tail -20`
Expected: 编译/测试失败（`listForExport` 不存在）

- [ ] **Step 3: 抽取 assembleRows 并实现 listForExport**

在 `EvalUserTagService` 新增私有方法（把现有 `pageUserRoles` 里"records→rows"的整段移过来，注意已是 String 版）：

```java
    /** 把一批用户装配成列表行（部门/岗位 + RBAC 角色 + 被评价/评价标签）。供分页与导出复用。 */
    private List<EvalUserRoleRowDTO> assembleRows(List<UserDTO> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> empIds = records.stream().map(UserDTO::getEmpId).collect(Collectors.toList());

        Map<String, EmployeeDTO> empMap = addressBookApi.getEmployees(empIds).stream()
                .collect(Collectors.toMap(EmployeeDTO::getEmpId, Function.identity(), (a, b) -> a));
        Map<String, List<RoleSimpleDTO>> roleMap = userApi.getRolesByUserIds(empIds);

        Map<String, List<EvalUserTagRow>> tagMap = new HashMap<>();
        if (!empIds.isEmpty()) {
            for (EvalUserTagRow r : evalUserTagMapper.selectUserTagsByUserIds(empIds)) {
                tagMap.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<EvalUserRoleRowDTO> rows = new ArrayList<>(records.size());
        for (UserDTO u : records) {
            EvalUserRoleRowDTO row = new EvalUserRoleRowDTO();
            row.setUserId(u.getEmpId());
            row.setUserName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
            EmployeeDTO emp = empMap.get(u.getEmpId());
            if (emp != null) {
                row.setOrgName(emp.getOrgName());
                row.setPosition(emp.getPosition());
            }
            List<RoleSimpleDTO> roles = roleMap.getOrDefault(u.getEmpId(), List.of());
            row.setRoleNames(roles.stream().map(RoleSimpleDTO::getRoleChName).collect(Collectors.toList()));

            List<EvalUserTagRow> tagRows = tagMap.getOrDefault(u.getEmpId(), List.of());
            EvalUserTagBriefDTO beEval = tagRows.stream()
                    .filter(t -> Integer.valueOf(1).equals(t.getTagType()))
                    .findFirst()
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .orElse(null);
            List<EvalUserTagBriefDTO> evalTags = tagRows.stream()
                    .filter(t -> Integer.valueOf(2).equals(t.getTagType()))
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .collect(Collectors.toList());
            row.setBeEvalTag(beEval);
            row.setEvalTags(evalTags);
            rows.add(row);
        }
        return rows;
    }
```

把 `pageUserRoles` 方法体替换为：
```java
    public PageResult<EvalUserRoleRowDTO> pageUserRoles(String keyword, int page, int pageSize) {
        PageResult<UserDTO> users = userApi.pageUsers(keyword, page, pageSize);
        List<EvalUserRoleRowDTO> rows = assembleRows(users.getRecords());
        return PageResult.of(page, pageSize, users.getTotal(), rows);
    }
```

新增 `listForExport`（放在 `pageUserRoles` 之后）：
```java
    /**
     * 导出用：取关键词匹配的全部人员（翻页累积，每页 100），上限 cap 行。
     *
     * @param keyword 关键词（工号/姓名，可空）
     * @param cap     最大导出行数（保护，超出截断）
     * @return 装配好的列表行
     */
    public List<EvalUserRoleRowDTO> listForExport(String keyword, int cap) {
        List<EvalUserRoleRowDTO> all = new ArrayList<>();
        int pageSize = 100;
        int pageNo = 1;
        while (all.size() < cap) {
            PageResult<UserDTO> users = userApi.pageUsers(keyword, pageNo, pageSize);
            List<UserDTO> records = users.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            all.addAll(assembleRows(records));
            if (all.size() >= users.getTotal()) {
                break;
            }
            pageNo++;
        }
        if (all.size() > cap) {
            return new ArrayList<>(all.subList(0, cap));
        }
        return all;
    }
```

- [ ] **Step 4: 运行新测试 + 回归**

Run: `mvn -q -pl performance-engine-center test -Dtest='EvalUserTagExportTest,EvalUserRoleServiceTest' 2>&1 | tail -20`
Expected: EvalUserTagExportTest 3 绿 + EvalUserRoleServiceTest 10 绿

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagExportTest.java
git commit -m "feat(eval): EvalUserTagService 抽取 assembleRows + 新增 listForExport"
```

---

### Task 6: EvalUserTagImportService 导入服务（核心，TDD）

**Files:**
- Create: `performance-engine-center/.../eval/service/EvalUserTagImportService.java`
- Test: `performance-engine-center/src/test/java/.../eval/service/EvalUserTagImportServiceTest.java`

> 设计：`importExcel(MultipartFile)` 负责文件级校验 + EasyExcel 解析，得到 `List<EvalUserTagImportRow>` 后委托 `importRows(...)` 做行校验 + 原子入库。工号为字符型，**无数字要求**；有效性由 `UserApi.getUserByEmpIds` 判定。入库复用 `EvalUserTagService.saveUserRoles(String empId, …)`。

- [ ] **Step 1: 写失败测试 `EvalUserTagImportServiceTest`**

```java
package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalUserTagImportServiceTest {

    @Mock EvalTagMapper evalTagMapper;
    @Mock UserApi userApi;
    @Mock EvalUserTagService evalUserTagService;
    EvalUserTagImportService service;

    private EvalTag tag(long id, String name, int type) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        t.setTagName(name);
        t.setTagType(type);
        t.setStatus(1);
        return t;
    }

    private UserDTO user(String empId) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        return u;
    }

    private EvalUserTagImportRow row(String empId, String beEval, String eval) {
        EvalUserTagImportRow r = new EvalUserTagImportRow();
        r.setEmpId(empId);
        r.setBeEvalRoleName(beEval);
        r.setEvalRoleNames(eval);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new EvalUserTagImportService(evalTagMapper, userApi, evalUserTagService);
        lenient().when(evalTagMapper.selectAll(isNull(), eq(1))).thenReturn(List.of(
                tag(1, "支行行长", 1), tag(2, "副行长", 2), tag(3, "客户经理", 2)));
    }

    @Test
    void importRows_allValid_savesEachAndReturnsSuccess() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280"), user("E001")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "副行长,客户经理"),
                row("E001", "", "副行长"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(2);
        assertThat(res.getErrors()).isEmpty();
        verify(evalUserTagService).saveUserRoles("2280", 1L, List.of(2L, 3L));
        verify(evalUserTagService).saveUserRoles("E001", null, List.of(2L));
    }

    @Test
    void importRows_anyError_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "副行长"),
                row("9999", "支行行长", "副行长"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(res.getErrors().get(0).getEmpId()).isEqualTo("9999");
        verify(evalUserTagService, never()).saveUserRoles(anyString(), any(), anyList());
    }

    @Test
    void importRows_empIdNotExist_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "副行长")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("工号不存在");
    }

    @Test
    void importRows_nonNumericEmpId_importsOk() {
        // §3.0 治理后非数字工号只要存在即可
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("E001")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("E001", "支行行长", "副行长")));
        assertThat(res.isSuccess()).isTrue();
        verify(evalUserTagService).saveUserRoles("E001", 1L, List.of(2L));
    }

    @Test
    void importRows_beEvalRoleNotFound_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "不存在角色", "副行长")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被评价角色");
    }

    @Test
    void importRows_beEvalRoleWrongType_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "副行长", "客户经理")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被评价角色");
    }

    @Test
    void importRows_beEvalMultiple_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长,副行长", "客户经理")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("只能");
    }

    @Test
    void importRows_evalRoleWrongType_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "支行行长")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("评价角色");
    }

    @Test
    void importRows_bothEmpty_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "", "")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("不能同时为空");
    }

    @Test
    void importRows_duplicateEmpIdInFile_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(
                row("2280", "支行行长", "副行长"),
                row("2280", "支行行长", "客户经理")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().stream().anyMatch(e -> e.getMessage().contains("重复"))).isTrue();
    }

    @Test
    void importRows_evalRoleDedup() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        service.importRows(List.of(row("2280", "", "副行长,副行长")));
        verify(evalUserTagService).saveUserRoles("2280", null, List.of(2L));
    }

    @Test
    void importExcel_parsesXlsxAndImports() throws Exception {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> data = new ArrayList<>();
        data.add(row("2280", "支行行长", "副行长,客户经理"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, EvalUserTagImportRow.class).sheet("人员角色").doWrite(data);
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bos.toByteArray());

        EvalUserTagImportResultDTO res = service.importExcel(file);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(1);
        verify(evalUserTagService).saveUserRoles("2280", 1L, List.of(2L, 3L));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagImportServiceTest 2>&1 | tail -20`
Expected: 编译失败（`EvalUserTagImportService` 不存在）

- [ ] **Step 3: 实现 `EvalUserTagImportService`**

```java
package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员评价角色 Excel 导入服务。
 * <p>同步、原子：全部行校验通过才逐行覆盖式入库；任一行错误则一条都不写，返回行级错误明细。</p>
 */
@Slf4j
@Service
public class EvalUserTagImportService {

    /** 单次导入最大行数保护。 */
    private static final int MAX_IMPORT_ROWS = 5000;

    private final EvalTagMapper evalTagMapper;
    private final UserApi userApi;
    private final EvalUserTagService evalUserTagService;

    public EvalUserTagImportService(EvalTagMapper evalTagMapper,
                                    UserApi userApi,
                                    EvalUserTagService evalUserTagService) {
        this.evalTagMapper = evalTagMapper;
        this.userApi = userApi;
        this.evalUserTagService = evalUserTagService;
    }

    /**
     * 解析并导入 Excel。
     *
     * @param file 上传的 .xlsx 文件
     * @return 导入结果（成功条数或行级错误明细）
     */
    public EvalUserTagImportResultDTO importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        List<EvalUserTagImportRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(EvalUserTagImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.warn("[EvalUserTagImportService.importExcel] 解析失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, e.getMessage());
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_ROWS_EXCEEDED, rows.size(), MAX_IMPORT_ROWS);
        }
        return importRows(rows);
    }

    /**
     * 校验全部行并原子入库。
     *
     * @param rows 解析后的行
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalUserTagImportResultDTO importRows(List<EvalUserTagImportRow> rows) {
        EvalUserTagImportResultDTO result = new EvalUserTagImportResultDTO();
        if (rows == null || rows.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }

        // 1. 标签名称 → tagId（仅启用），按类型拆分
        Map<String, Long> beEvalNameToId = new HashMap<>();
        Map<String, Long> evalNameToId = new HashMap<>();
        for (EvalTag t : evalTagMapper.selectAll(null, 1)) {
            if (Integer.valueOf(1).equals(t.getTagType())) {
                beEvalNameToId.put(t.getTagName(), t.getTagId());
            } else if (Integer.valueOf(2).equals(t.getTagType())) {
                evalNameToId.put(t.getTagName(), t.getTagId());
            }
        }

        // 2. 工号有效性：批量查存在的工号
        List<String> empIds = rows.stream()
                .map(r -> r.getEmpId() == null ? "" : r.getEmpId().trim())
                .collect(Collectors.toList());
        Set<String> existingEmpIds = userApi.getUserByEmpIds(empIds).stream()
                .map(UserDTO::getEmpId)
                .collect(Collectors.toSet());

        // 3. 逐行校验
        List<EvalUserTagImportResultDTO.RowError> errors = new ArrayList<>();
        List<ParsedRow> parsed = new ArrayList<>();
        Set<String> seenEmpIds = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            EvalUserTagImportRow r = rows.get(i);
            String empId = r.getEmpId() == null ? "" : r.getEmpId().trim();

            if (empId.isEmpty()) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "工号不能为空"));
                continue;
            }
            if (!seenEmpIds.add(empId)) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "工号在文件内重复"));
                continue;
            }
            if (!existingEmpIds.contains(empId)) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "工号不存在"));
                continue;
            }

            // 被评价角色：可空；填了则只能一个且类型=1
            Long beEvalTagId = null;
            String beEvalRaw = r.getBeEvalRoleName() == null ? "" : r.getBeEvalRoleName().trim();
            boolean rowFailed = false;
            if (!beEvalRaw.isEmpty()) {
                List<String> beNames = splitNames(beEvalRaw);
                if (beNames.size() > 1) {
                    errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "被评价角色只能填一个"));
                    rowFailed = true;
                } else {
                    Long id = beEvalNameToId.get(beNames.get(0));
                    if (id == null) {
                        errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId,
                                "被评价角色无效或非被评价人类型：" + beNames.get(0)));
                        rowFailed = true;
                    } else {
                        beEvalTagId = id;
                    }
                }
            }
            if (rowFailed) {
                continue;
            }

            // 评价角色：可空；逗号分隔去重；每个类型=2
            List<Long> evalTagIds = new ArrayList<>();
            String evalRaw = r.getEvalRoleNames() == null ? "" : r.getEvalRoleNames().trim();
            if (!evalRaw.isEmpty()) {
                for (String name : splitNames(evalRaw)) {
                    Long id = evalNameToId.get(name);
                    if (id == null) {
                        errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId,
                                "评价角色无效或非评价人类型：" + name));
                        rowFailed = true;
                        break;
                    }
                    if (!evalTagIds.contains(id)) {
                        evalTagIds.add(id);
                    }
                }
            }
            if (rowFailed) {
                continue;
            }

            if (beEvalTagId == null && evalTagIds.isEmpty()) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "被评价角色与评价角色不能同时为空"));
                continue;
            }

            parsed.add(new ParsedRow(empId, beEvalTagId, evalTagIds));
        }

        // 4. 任一行错误 → 整体不入库
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setImportedCount(0);
            result.setErrors(errors);
            return result;
        }

        // 5. 全部通过 → 逐行覆盖式入库
        for (ParsedRow p : parsed) {
            evalUserTagService.saveUserRoles(p.empId, p.beEvalTagId, p.evalTagIds);
        }
        result.setSuccess(true);
        result.setImportedCount(parsed.size());
        log.info("[EvalUserTagImportService.importRows] 导入成功 {} 条", parsed.size());
        return result;
    }

    /** 拆分逗号分隔名称（兼容中英文逗号），去空白与空项。 */
    private static List<String> splitNames(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split("[,，]")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 校验通过的一行解析结果。 */
    private static class ParsedRow {
        final String empId;
        final Long beEvalTagId;
        final List<Long> evalTagIds;

        ParsedRow(String empId, Long beEvalTagId, List<Long> evalTagIds) {
            this.empId = empId;
            this.beEvalTagId = beEvalTagId;
            this.evalTagIds = evalTagIds;
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagImportServiceTest 2>&1 | tail -25`
Expected: Tests run: 12, Failures: 0, Errors: 0

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportService.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportServiceTest.java
git commit -m "feat(eval): 新增人员评价角色 Excel 同步原子导入服务"
```

---

### Task 7: Controller 新增 import / import-template / export 三端点

**Files:**
- Modify: `performance-engine-center/.../eval/controller/EvalUserTagController.java`

> 现有 controller 用 `@RequiredArgsConstructor` 注入 `EvalUserTagService`。新增 `EvalUserTagImportService` 依赖。模板与导出走同步流式写到 `HttpServletResponse`。

- [ ] **Step 1: 追加 import 字段、常量与三个端点方法**

类字段处（`private final EvalUserTagService evalUserTagService;` 下一行）追加：
```java
    private final EvalUserTagImportService evalUserTagImportService;
```

类声明 `{` 之后第一行加常量：
```java
    /** 导出最大行数保护。 */
    private static final int EXPORT_ROWS_CAP = 10000;
```

在 `saveRoles` 方法之后、内部静态类 `BindReq` 之前插入：
```java
    @PostMapping("/import")
    @Operation(summary = "导入人员评价角色（Excel，同步原子）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public ResponseWrapper<EvalUserTagImportResultDTO> importExcel(
            @RequestPart("file") MultipartFile file) {
        log.info("[EvalUserTagController.importExcel] fileName={}",
                file != null ? file.getOriginalFilename() : null);
        return ResponseWrapper.success(evalUserTagImportService.importExcel(file));
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载人员评价角色导入模板")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("人员评价角色导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        EvalUserTagImportRow sample = new EvalUserTagImportRow();
        sample.setEmpId("100001");
        sample.setBeEvalRoleName("支行行长");
        sample.setEvalRoleNames("副行长,客户经理");
        EasyExcel.write(response.getOutputStream(), EvalUserTagImportRow.class)
                .sheet("人员评价角色")
                .doWrite(List.of(sample));
    }

    @GetMapping("/export")
    @Operation(summary = "导出人员标签列表（Excel，按关键词）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.EXPORT)
    public void export(@RequestParam(value = "keyword", required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        log.info("[EvalUserTagController.export] keyword={}", keyword);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("人员标签列表.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        List<EvalUserRoleRowDTO> rows = evalUserTagService.listForExport(keyword, EXPORT_ROWS_CAP);
        List<EvalUserRoleExportRow> out = new ArrayList<>(rows.size());
        for (EvalUserRoleRowDTO r : rows) {
            EvalUserRoleExportRow e = new EvalUserRoleExportRow();
            e.setUserName(r.getUserName());
            e.setEmpId(r.getUserId());
            e.setOrgName(r.getOrgName());
            e.setPosition(r.getPosition());
            e.setRoleNames(r.getRoleNames() == null ? "" : String.join("，", r.getRoleNames()));
            e.setBeEvalRole(r.getBeEvalTag() == null ? "" : r.getBeEvalTag().getTagName());
            e.setEvalRoles(r.getEvalTags() == null ? "" : r.getEvalTags().stream()
                    .map(t -> t.getTagName()).collect(Collectors.joining("，")));
            out.add(e);
        }
        EasyExcel.write(response.getOutputStream(), EvalUserRoleExportRow.class)
                .sheet("人员标签列表")
                .doWrite(out);
    }
```

- [ ] **Step 2: 补 import**

```java
import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleExportRow;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.service.EvalUserTagImportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;
```
（`java.util.List`、`EvalUserRoleRowDTO`、`PageResult`、`@RequestParam`、`BizAction`、`BizType` 等已存在。）

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl performance-engine-center -am compile -DskipTests 2>&1 | tail -15`
Expected: BUILD SUCCESS

- [ ] **Step 4: 跑 perf eval 相关全部单测**

Run: `mvn -q -pl performance-engine-center test -Dtest='EvalUserRoleServiceTest,EvalUserTagExportTest,EvalUserTagImportServiceTest' 2>&1 | tail -20`
Expected: 全绿（10 + 3 + 12）

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalUserTagController.java
git commit -m "feat(eval): 人员标签 controller 新增 导入/模板/导出 三端点"
```

---

### Task 8: PT_RESOURCE 权限种子 SQL

**Files:**
- Create: `docs/superpowers/sql/2026-05-29-eval-user-tags-import-export-seed.sql`

> 上一批人员标签改造用到 `PERF_EVAL_22`。本批用 23/24/25。执行前先核对 DB 最大编号，冲突则顺延并在脚本注释记录。

- [ ] **Step 1: 编写种子 SQL**

```sql
-- ============================================================
-- 人员标签 导入/导出/模板 PT_RESOURCE 资源注册
-- 日期: 2026-05-29
-- 承接 2026-05-29-eval-user-tags-page-resource-seed.sql（已用到 PERF_EVAL_22）
-- 字段对齐: RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS
-- ============================================================

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS)
VALUES
('PERF_EVAL_23', '/api/admin/eval/user-tags/import',          'POST', '导入人员评价角色', 0, 0),
('PERF_EVAL_24', '/api/admin/eval/user-tags/import-template', 'GET',  '下载导入模板',     0, 0),
('PERF_EVAL_25', '/api/admin/eval/user-tags/export',          'GET',  '导出人员标签列表', 0, 0);

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_23', 'PERF_EVAL_24', 'PERF_EVAL_25');

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_23', 'PERF_EVAL_24', 'PERF_EVAL_25');
```

- [ ] **Step 2: 在本地 yiti 库执行（先核对编号）**

```sql
SELECT RESOURCE_ID FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'PERF_EVAL_2_' ORDER BY RESOURCE_ID;
-- 确认 23/24/25 未占用后执行 INSERT
```
Expected: 3 行 PT_RESOURCE + 6 行 PT_ROLE_RESOURCE 插入成功。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/sql/2026-05-29-eval-user-tags-import-export-seed.sql
git commit -m "chore(eval): 人员标签 导入/导出/模板 PT_RESOURCE 种子 SQL"
```

---

### Task 9: 前端 eval.js 新增三个 API

**Files:**
- Modify: `xanzc_frontend/src/api/eval.js`

- [ ] **Step 1: 在 `saveUserRoles` 之后追加 3 个函数**

```js
// 2026-05-29：人员标签 导入 / 模板下载 / 导出
export async function importUserRoles(file) {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', '/admin/eval/user-tags/import', {
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }
  }, null);
}

export function downloadImportTemplate() {
  return call('get', '/admin/eval/user-tags/import-template', { responseType: 'blob' }, null);
}

export function exportUserRoles(keyword) {
  return call('get', '/admin/eval/user-tags/export', { params: { keyword }, responseType: 'blob' }, null);
}
```

- [ ] **Step 2: 语法检查**

Run: `node --check xanzc_frontend/src/api/eval.js && echo OK`
Expected: OK

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/api/eval.js
git commit -m "feat(eval-fe): eval.js 新增 导入/模板下载/导出 API"
```

---

### Task 10: 前端 UserTags.vue 查询按钮 + 导入弹窗 + 下载

**Files:**
- Modify: `xanzc_frontend/src/views/eval/UserTags.vue`

> 现有工具栏只有一个 `el-input`（`@input="onSearch"`）。改为：输入框 + 查询 + 重置 + 导入 + 下载；新增导入 `el-dialog`。

- [ ] **Step 1: 替换工具栏模板**

把现有 `<div class="toolbar">...</div>` 整块替换为：
```html
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名或工号"
          clearable
          size="small"
          class="kw-input"
          @keyup.enter="doSearch"
          @clear="doSearch"
        />
        <el-button type="primary" size="small" @click="doSearch">查询</el-button>
        <el-button size="small" @click="resetSearch">重置</el-button>
        <el-button size="small" @click="openImport">导入</el-button>
        <el-button size="small" :loading="exporting" @click="doExport">下载</el-button>
      </div>
```

- [ ] **Step 2: 在 `.pager` 之后、card-section `</div>` 之前追加导入弹窗**

```html
      <el-dialog v-model="importVisible" title="导入人员评价角色" width="640px">
        <div class="imp-tip">
          <el-button size="small" @click="doDownloadTpl">📥 下载导入模板</el-button>
          <span class="muted">模板列：工号 / 被评价角色 / 评价角色；评价角色用逗号分隔。全部校验通过才会导入。</span>
        </div>
        <el-upload
          ref="impUploaderRef"
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          :on-change="onImpFilePick"
          accept=".xlsx"
          style="margin-top:12px">
          <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
        </el-upload>

        <div v-if="importErrors.length" class="imp-errors">
          <div class="err-title">导入失败，请修正后重传（共 {{ importErrors.length }} 条问题）：</div>
          <el-table :data="importErrors" size="small" border max-height="240">
            <el-table-column prop="row" label="行号" width="80" />
            <el-table-column prop="empId" label="工号" width="140" />
            <el-table-column prop="message" label="原因" min-width="240" />
          </el-table>
        </div>

        <template #footer>
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importing" :disabled="!impFile" @click="doImport">开始导入</el-button>
        </template>
      </el-dialog>
```

- [ ] **Step 3: 脚本——补 import 与状态/方法**

把从 `'@/api/eval'` 的 import 合并新成员：
```js
import { pageUserRoles, saveUserRoles, listAllTags, importUserRoles, downloadImportTemplate, exportUserRoles } from '@/api/eval';
```

新增响应式状态（与现有 `keyword/rows/...` 并列）：
```js
const exporting = ref(false);
const importVisible = ref(false);
const importing = ref(false);
const impFile = ref(null);
const impUploaderRef = ref(null);
const importErrors = ref([]);
```

新增/调整方法（把原 `onSearch` 即时搜索改为按钮驱动 `doSearch`）：
```js
function doSearch() {
  page.value = 1;
  reload();
}
function resetSearch() {
  keyword.value = '';
  page.value = 1;
  reload();
}

function openImport() {
  importErrors.value = [];
  impFile.value = null;
  impUploaderRef.value?.clearFiles();
  importVisible.value = true;
}
function onImpFilePick(uploadFile) {
  impFile.value = uploadFile.raw || null;
}
async function doImport() {
  if (!impFile.value) return;
  importing.value = true;
  importErrors.value = [];
  try {
    const res = await importUserRoles(impFile.value);
    if (res && res.success) {
      ElMessage.success(`导入成功 ${res.importedCount} 条`);
      importVisible.value = false;
      reload();
    } else {
      importErrors.value = (res && res.errors) || [];
      ElMessage.error('导入未通过校验，请查看错误明细');
    }
  } catch (e) {
    // http.js 已弹错误消息
  } finally {
    importing.value = false;
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
async function doDownloadTpl() {
  try {
    const blob = await downloadImportTemplate();
    saveBlob(blob, '人员评价角色导入模板.xlsx');
  } catch (e) { /* 已提示 */ }
}
async function doExport() {
  exporting.value = true;
  try {
    const blob = await exportUserRoles(keyword.value);
    saveBlob(blob, '人员标签列表.xlsx');
  } catch (e) { /* 已提示 */ } finally {
    exporting.value = false;
  }
}
```

> 确认脚本顶部已 import `ref`（vue）与 `ElMessage`（element-plus）；若 `ElMessage` 未引入则补 `import { ElMessage } from 'element-plus';`。若旧模板残留对 `onSearch` 的引用，删除其定义（已被 `doSearch` 取代）。

- [ ] **Step 4: 构建校验（只关注 UserTags 相关错误）**

Run: `cd xanzc_frontend && npx vite build 2>&1 | grep -iE "UserTags|eval/UserTags" || echo "无 UserTags 相关错误"`
Expected: "无 UserTags 相关错误"（注：`report/Dashboard.vue` 缺 html2canvas 的既有报错与本改动无关）

- [ ] **Step 5: Commit**

```bash
git add xanzc_frontend/src/views/eval/UserTags.vue
git commit -m "feat(eval-fe): 人员标签页 查询按钮 + 导入弹窗 + 模板/列表下载"
```

---

### Task 11: 全量回归与收尾

- [ ] **Step 1: install 上游模块（避免 stale jar）**

Run: `mvn -q clean install -pl auth-permission-center,portal-content-center -am -DskipTests 2>&1 | tail -8`
Expected: BUILD SUCCESS

- [ ] **Step 2: 跑 perf eval 全量单测（含 §3.0 治理回归）**

Run: `mvn -q -pl performance-engine-center test -Dtest='EvalUserRoleServiceTest,EvalScoreServiceTest,EvalTaskServiceTest,EvalTagServiceTest,EvalRuleServiceTest,EvalUserTagExportTest,EvalUserTagImportServiceTest' 2>&1 | tail -25`
Expected: 全绿（无 Failures / Errors）

- [ ] **Step 3: 前端构建无 UserTags 报错（同 Task 10 Step 4）**

- [ ] **Step 4: 最终代码评审 + finishing-a-development-branch**

交由 subagent-driven-development 的 final review 流程；合并策略届时询问用户（参考上次：合并回 master 保留分支）。

---

## Self-Review（写计划后自检）

**Spec coverage:**
- §3.0 工号字符型治理（4 列 + 17 文件）→ Task 1（DB+DDL）+ Task 2（代码贯通+测试）。✅
- 查询按钮 → Task 10（doSearch/resetSearch + 按钮）。✅
- Excel 同步原子导入（全部通过才入库、非数字工号 OK）→ Task 6 + Task 7。✅
- 导入模板下载 → Task 7（import-template）+ Task 9/10。✅
- 导出（当前关键词全部匹配）→ Task 5（listForExport）+ Task 7（export）+ Task 9/10。✅
- 导入字段 工号/被评价/评价 + 逗号分隔 + 校验有效性 → Task 3（行模型）+ Task 6（校验全覆盖）。✅
- 权限登记 → Task 8。✅

**Placeholder scan:** 无 TBD/TODO；每个代码步骤含完整代码或精确 old→new。✅

**Type consistency:**
- 工号全链 String：实体/DTO/Mapper/Service/Controller/import/export 一致；`saveUserRoles(String empId, …)` 被 import 服务正确调用。✅
- `EvalUserTagImportResultDTO.RowError(row,empId,message)`、`EvalUserTagImportRow.empId/beEvalRoleName/evalRoleNames`、`EvalUserRoleExportRow` setter 跨 Task 3/6/7 一致。✅
- `listForExport`/`assembleRows`/`pageUserRoles` 签名一致；`tagMap` 为 `Map<String,...>`。✅

**注意点：**
1. Task 2 是大范围类型重构，建议派 executor 子代理一次性改完 14 源文件再编译（符合"大范围改动用子代理"经验），中途不可分提交以免非编译态。
2. `importRows` 标注 `@Transactional` 但单测用 Mockito 直接调用（不经 Spring 代理），事务不真生效——故测试用 `verify(...never())` 校验"未调用 saveUserRoles"而非校验回滚；真实回滚由运行期 Spring 代理保证（且设计在写库前已收集错误并 return，写库阶段不会半途失败）。
3. 上线需对 onepl/yiti 各环境执行 `2026-05-29-eval-userid-to-varchar.sql` 与 `PT_RESOURCE` 种子，否则迁移缺失 / 端点 403。
