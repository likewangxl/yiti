# 人员标签"是否启用评价"字段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给"人员标签"页每个人增加"是否启用评价"布尔位，列表默认只显示启用者，并把该字段贯通查询过滤、覆盖式保存、导入模板/校验、导出。

**Architecture:** eval 子域新建 `EVAL_USER_SETTING(user_id PK, eval_enabled)` 表（无记录=否）。分页查询按 `evalEnabled` 三态分派：是（默认，从 setting 取启用工号 → 批量解析 → 内存关键词过滤+分页）/ 全部（PT_USER 驱动 + overlay）/ 否（PT_USER 驱动 + overlay + 本页内存剔除已启用，近似分页）。保存复用一个新的编排方法 `saveUserRolesWithSetting`（角色覆盖 + setting upsert 同事务）。导入/导出新增"是否启用评价"列（填"是/否"）。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis(-Plus) / EasyExcel / JUnit5 + Mockito + AssertJ（surefire 单测）/ Vue3 + Element Plus。

**关键约束（务必遵守）:**
- TDD 红-绿-重构，每步独立 commit；子代理 model 必须 ≥ sonnet（禁 haiku）。
- 中文注释 + UTF-8；Mapper XML 用 `#{}`。
- eval 子域**当前没有任何 Mapper IT**，统一用 mock service 单测（surefire `*Test.java`）+ 手工 smoke 验证 SQL；本计划沿用该模式，**不新增真实 DB Mapper IT**。
- 项目已废弃 Flyway：DDL 手工执行，禁止 `V*__*.sql` 命名 / `*FlywayIT`。
- 测试运行：跨模块改动后先 `mvn clean install -DskipTests`；单测跑 `mvn -q -pl performance-engine-center test -Dtest=<类名>`。

---

## File Structure

**后端（performance-engine-center）**
- Create `docs/superpowers/sql/2026-05-29-eval-user-setting.sql` — 建表脚本（幂等）
- Modify `docs/schema/ddl-eval.sql` — 基线追加 EVAL_USER_SETTING
- Create `.../performance/eval/entity/EvalUserSetting.java` — 贫血实体
- Create `.../performance/eval/mapper/EvalUserSettingMapper.java` — Mapper 接口
- Create `.../resources/mapper/performance/EvalUserSettingMapper.xml` — SQL
- Modify `.../performance/eval/service/EvalUserTagService.java` — 注入 settingMapper；新增 `saveUserRolesWithSetting`、`pageUserRoles` 三态、`listForExport` 加 evalEnabled、`assembleRows` overlay；新增 `ENABLED_DRIVEN_CAP`
- Modify `.../performance/eval/dto/EvalUserRoleRowDTO.java` — 加 `evalEnabled`
- Modify `.../performance/eval/dto/EvalUserTagImportRow.java` — 加 `evalEnabledText`
- Modify `.../performance/eval/dto/EvalUserRoleExportRow.java` — 加 `evalEnabled`
- Modify `.../performance/eval/service/EvalUserTagImportService.java` — 校验新列 + 调 `saveUserRolesWithSetting`
- Modify `.../performance/eval/controller/EvalUserTagController.java` — page 加 evalEnabled 参；SaveRolesReq 加 evalEnabled；模板样例；export 加 evalEnabled 参 + 列映射

**测试（surefire 单测）**
- Modify `.../eval/service/EvalUserRoleServiceTest.java` — 加 `@Mock EvalUserSettingMapper`；pageUserRoles 三态新用例；saveUserRolesWithSetting 用例
- Modify `.../eval/service/EvalUserTagExportTest.java` — 加 `@Mock EvalUserSettingMapper`；listForExport 新签名 + evalEnabled 用例
- Modify `.../eval/service/EvalUserTagImportServiceTest.java` — 新列校验用例 + 调用断言改为 saveUserRolesWithSetting

**前端（xanzc_frontend）**
- Modify `src/api/eval.js` — `saveUserRoles` / `exportUserRoles` 加 evalEnabled 参
- Modify `src/views/eval/UserTags.vue` — 过滤下拉 + 列表列 + 编辑下拉 + 传参

---

## Task 1: 建表 DDL（EVAL_USER_SETTING）

**Files:**
- Create: `docs/superpowers/sql/2026-05-29-eval-user-setting.sql`
- Modify: `docs/schema/ddl-eval.sql`（在 EVAL_USER_TAG 段之后追加）

- [ ] **Step 1: 写幂等建表脚本**

`docs/superpowers/sql/2026-05-29-eval-user-setting.sql`：

```sql
-- =============================================================
-- 人员评价设置表 EVAL_USER_SETTING（是否启用评价）
-- 模块: performance-engine-center / eval 子域
-- 日期: 2026-05-29
-- 语义: 表中无该工号记录 = 否（未启用）；eval_enabled=1 才视为启用
-- 执行: 在 yiti / onepl / onepl_test_bootstrap 三库手工执行（项目已废弃 Flyway）
-- 幂等: CREATE TABLE IF NOT EXISTS
-- =============================================================
CREATE TABLE IF NOT EXISTS EVAL_USER_SETTING (
    USER_ID       VARCHAR(50) NOT NULL COMMENT '工号，关联 PT_USER.USER_ID',
    EVAL_ENABLED  TINYINT     NOT NULL DEFAULT 0 COMMENT '是否启用评价：1=是 0=否',
    CREATED_TIME  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UPDATED_TIME  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    PRIMARY KEY (USER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员评价设置（是否启用评价）';
```

- [ ] **Step 2: 同步基线 DDL**

在 `docs/schema/ddl-eval.sql` 的 `EVAL_USER_TAG` 建表语句之后，插入同样的 `CREATE TABLE IF NOT EXISTS EVAL_USER_SETTING (...)` 块（与上方完全一致）。

- [ ] **Step 3: 在开发库执行（验证 SQL 合法）**

Run:
```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-05-29-eval-user-setting.sql && \
mysql -uroot -pdjdev -e "DESC yiti.EVAL_USER_SETTING;"
```
Expected: 打印 4 列（USER_ID/EVAL_ENABLED/CREATED_TIME/UPDATED_TIME），无报错。

> 注：测试库 `onepl_test_bootstrap` 也需执行同脚本，否则后续若有人加 Mapper IT 会报表不存在；本计划不加 Mapper IT，但仍建议一并执行：
> `mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-05-29-eval-user-setting.sql`

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/sql/2026-05-29-eval-user-setting.sql docs/schema/ddl-eval.sql
git commit -m "feat(eval): EVAL_USER_SETTING 建表脚本 + 基线 DDL（是否启用评价）"
```

---

## Task 2: 实体 + Mapper（EvalUserSetting / EvalUserSettingMapper）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalUserSetting.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserSettingMapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/EvalUserSettingMapper.xml`

> 该 Mapper 是接口，后续在 Service 单测中被 mock；SQL 由手工 smoke 验证（eval 子域无 Mapper IT 的既定模式）。本任务为编译期脚手架，验证手段=`test-compile` 通过。

- [ ] **Step 1: 写实体**

`EvalUserSetting.java`：

```java
package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 人员评价设置表 EVAL_USER_SETTING 贫血实体。
 * <p>无该工号记录视为"否"（未启用）。</p>
 */
@Data
@TableName("EVAL_USER_SETTING")
public class EvalUserSetting {
    /** 工号，主键，关联 PT_USER.USER_ID（String）. */
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;
    /** 是否启用评价：1=是 0=否. */
    private Integer evalEnabled;
    /** 创建时间. */
    private LocalDateTime createdTime;
    /** 最近更新时间. */
    private LocalDateTime updatedTime;
}
```

- [ ] **Step 2: 写 Mapper 接口**

`EvalUserSettingMapper.java`：

```java
package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalUserSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 人员评价设置 Mapper。
 */
@Mapper
public interface EvalUserSettingMapper extends BaseMapper<EvalUserSetting> {

    /** 取全部"启用=是"的工号（用于"是"过滤的 eval 侧驱动分页）。 */
    List<String> selectEnabledUserIds();

    /**
     * 在给定工号集合内，取"启用=是"的子集（用于列表 overlay）。
     * 调用方须保证 userIds 非空。
     *
     * @param userIds 工号集合
     * @return 其中启用的工号
     */
    List<String> selectEnabledUserIdsIn(@Param("userIds") List<String> userIds);

    /**
     * upsert 启用位：存在则更新，不存在则插入。
     *
     * @param userId      工号
     * @param evalEnabled 1=是 0=否
     * @return 受影响行数
     */
    int upsert(@Param("userId") String userId, @Param("evalEnabled") int evalEnabled);
}
```

- [ ] **Step 3: 写 Mapper XML**

`EvalUserSettingMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper">

    <select id="selectEnabledUserIds" resultType="java.lang.String">
        SELECT user_id FROM EVAL_USER_SETTING WHERE eval_enabled = 1
    </select>

    <select id="selectEnabledUserIdsIn" resultType="java.lang.String">
        SELECT user_id FROM EVAL_USER_SETTING WHERE eval_enabled = 1 AND user_id IN
        <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
    </select>

    <insert id="upsert">
        INSERT INTO EVAL_USER_SETTING (user_id, eval_enabled)
        VALUES (#{userId}, #{evalEnabled})
        ON DUPLICATE KEY UPDATE eval_enabled = #{evalEnabled}, updated_time = NOW()
    </insert>
</mapper>
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q -pl performance-engine-center test-compile`
Expected: BUILD SUCCESS（无编译错误）。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalUserSetting.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserSettingMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/EvalUserSettingMapper.xml
git commit -m "feat(eval): EvalUserSetting 实体 + Mapper（selectEnabled* / upsert）"
```

---

## Task 3: Service 覆盖式保存 + setting upsert（saveUserRolesWithSetting）

**Files:**
- Modify: `performance-engine-center/.../eval/service/EvalUserTagService.java`
- Test: `performance-engine-center/.../eval/service/EvalUserRoleServiceTest.java`

设计要点：`saveUserRoles(userId, beEval, evalTagIds)` **保持不变**（纯角色覆盖，现有用例全绿）。新增编排方法 `saveUserRolesWithSetting(...)` `@Transactional`，内部调 `saveUserRoles(...)`（同事务，self-invocation 不新开事务）+ `evalUserSettingMapper.upsert(...)`。

- [ ] **Step 1: 先改构造器注入 settingMapper（让测试可编译）**

在 `EvalUserTagService` 加字段与构造器参数：

```java
private final EvalUserSettingMapper evalUserSettingMapper;
```

构造器签名改为（追加最后一个参数）：

```java
@Autowired
public EvalUserTagService(EvalUserTagMapper evalUserTagMapper,
                          EvalTagMapper evalTagMapper,
                          UserApi userApi,
                          AddressBookApi addressBookApi,
                          EvalUserSettingMapper evalUserSettingMapper) {
    this.evalUserTagMapper = evalUserTagMapper;
    this.evalTagMapper = evalTagMapper;
    this.userApi = userApi;
    this.addressBookApi = addressBookApi;
    this.evalUserSettingMapper = evalUserSettingMapper;
}
```

并加 import：`import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;`

- [ ] **Step 2: 在 EvalUserRoleServiceTest 加 @Mock 字段并写失败测试**

在 `EvalUserRoleServiceTest` 类加：

```java
@Mock private EvalUserSettingMapper evalUserSettingMapper;
```

加 import：`import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;`
（`@InjectMocks` 会自动把新 mock 注入新构造器参数。）

新增测试方法：

```java
@Test
@DisplayName("saveUserRolesWithSetting：覆盖角色 + upsert 启用位(1) 同次调用")
void saveUserRolesWithSetting_savesRolesAndEnabled() {
    when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
    when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

    service.saveUserRolesWithSetting("1001", null, List.of(2L), 1);

    verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
    assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
    verify(evalUserSettingMapper).upsert("1001", 1);
}

@Test
@DisplayName("saveUserRolesWithSetting：evalEnabled=null 兜底为 0")
void saveUserRolesWithSetting_nullEnabled_defaultsZero() {
    when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());

    service.saveUserRolesWithSetting("1001", null, List.of(), null);

    verify(evalUserSettingMapper).upsert("1001", 0);
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: 编译失败或 `saveUserRolesWithSetting` 方法不存在 → FAIL。

- [ ] **Step 4: 实现 saveUserRolesWithSetting**

在 `EvalUserTagService` 新增方法（放在 `saveUserRoles` 之后）：

```java
/**
 * 覆盖式保存人员评价角色 + 写"是否启用评价"位（同一事务，原子）。
 *
 * @param userId      人员工号
 * @param beEvalTagId 被评价人标签ID（null 表示清空）
 * @param evalTagIds  评价人标签ID列表（null/空 表示清空）
 * @param evalEnabled 是否启用评价：1=是 0=否；null 兜底为 0
 */
@Transactional(rollbackFor = Exception.class)
public void saveUserRolesWithSetting(String userId, Long beEvalTagId, List<Long> evalTagIds, Integer evalEnabled) {
    saveUserRoles(userId, beEvalTagId, evalTagIds);
    int enabled = (evalEnabled != null && evalEnabled == 1) ? 1 : 0;
    evalUserSettingMapper.upsert(userId, enabled);
    log.info("[EvalUserTagService.saveUserRolesWithSetting] userId={} evalEnabled={}", userId, enabled);
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: BUILD SUCCESS（含既有用例全绿 + 2 新用例）。

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java
git commit -m "feat(eval): saveUserRolesWithSetting 角色+启用位同事务保存"
```

---

## Task 4: Service 列表三态过滤 + evalEnabled overlay

**Files:**
- Modify: `performance-engine-center/.../eval/dto/EvalUserRoleRowDTO.java`
- Modify: `performance-engine-center/.../eval/service/EvalUserTagService.java`
- Test: `performance-engine-center/.../eval/service/EvalUserRoleServiceTest.java`

设计要点：
- `pageUserRoles` 签名加 `String evalEnabled`：`pageUserRoles(String keyword, String evalEnabled, int page, int pageSize)`。
- 模式归一：`null`/`"1"` → 只看启用（默认）；`"0"` → 否；其它（`""`/`"all"`）→ 全部。
- `assembleRows` 统一 overlay `evalEnabled`（用 `selectEnabledUserIdsIn`）。
- 启用驱动路径：`selectEnabledUserIds()`（cap 5000）→ `getUserByEmpIds` → 内存关键词过滤 → 内存分页 → `assembleRows`。

- [ ] **Step 1: DTO 加字段**

`EvalUserRoleRowDTO.java` 增：

```java
/** 是否启用评价：1=是 0=否. */
private Integer evalEnabled;
```

- [ ] **Step 2: 写失败测试（三态 + overlay）**

> 现有两个 pageUserRoles 用例（`pageUserRoles_assembles` / `pageUserRoles_emptyPage` / `pageUserRoles_nonNumericEmpId`）调用旧 3 参签名，需改为 4 参并按"全部"路径补 overlay 桩。请在本步一并改造它们。

改造现有 3 个用例：把 `service.pageUserRoles(kw, page, size)` 改为 `service.pageUserRoles(kw, "all", page, size)`，并为每个用例补桩：
- `when(evalUserSettingMapper.selectEnabledUserIdsIn(anyList())).thenReturn(List.of());`（除非该用例显式断言启用值）。
- `pageUserRoles_assembles` 额外断言：`assertThat(row.getEvalEnabled()).isEqualTo(1);` 并把桩改为 `when(evalUserSettingMapper.selectEnabledUserIdsIn(List.of("1001"))).thenReturn(List.of("1001"));`

新增 3 个用例：

```java
@Test
@DisplayName("pageUserRoles 默认(是)：从 setting 取启用工号，仅返回启用者，total 准确")
void pageUserRoles_enabledDriven_default() {
    when(evalUserSettingMapper.selectEnabledUserIds()).thenReturn(List.of("1001", "1002"));
    when(userApi.getUserByEmpIds(List.of("1001", "1002")))
            .thenReturn(List.of(user("1001", "张三"), user("1002", "李四")));
    // 本页装配下游（assembleRows 内）
    when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
    when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
    when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
    when(evalUserSettingMapper.selectEnabledUserIdsIn(anyList())).thenReturn(List.of("1001", "1002"));

    PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "1", 1, 20);

    assertThat(r.getTotal()).isEqualTo(2L);
    assertThat(r.getRecords()).extracting(EvalUserRoleRowDTO::getUserId)
            .containsExactlyInAnyOrder("1001", "1002");
    assertThat(r.getRecords()).allSatisfy(row -> assertThat(row.getEvalEnabled()).isEqualTo(1));
    verify(userApi, never()).pageUsers(any(), anyInt(), anyInt());
}

@Test
@DisplayName("pageUserRoles 默认(是)：关键词内存过滤姓名/工号")
void pageUserRoles_enabledDriven_keywordFilter() {
    when(evalUserSettingMapper.selectEnabledUserIds()).thenReturn(List.of("1001", "1002"));
    when(userApi.getUserByEmpIds(List.of("1001", "1002")))
            .thenReturn(List.of(user("1001", "张三"), user("1002", "李四")));
    when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
    when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
    when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
    when(evalUserSettingMapper.selectEnabledUserIdsIn(anyList())).thenReturn(List.of("1001"));

    PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles("张", "1", 1, 20);

    assertThat(r.getTotal()).isEqualTo(1L);
    assertThat(r.getRecords().get(0).getUserId()).isEqualTo("1001");
}

@Test
@DisplayName("pageUserRoles 否：PT_USER 驱动 + 本页剔除已启用者")
void pageUserRoles_disabled_excludesEnabled() {
    when(userApi.pageUsers(null, 1, 20))
            .thenReturn(PageResult.of(1, 20, 2L, List.of(user("1001", "张三"), user("1002", "李四"))));
    when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
    when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
    when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
    when(evalUserSettingMapper.selectEnabledUserIdsIn(List.of("1001", "1002"))).thenReturn(List.of("1001"));

    PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "0", 1, 20);

    assertThat(r.getRecords()).extracting(EvalUserRoleRowDTO::getUserId).containsExactly("1002");
    assertThat(r.getRecords().get(0).getEvalEnabled()).isEqualTo(0);
}
```

加 import（若缺）：`import static org.mockito.ArgumentMatchers.anyInt;`

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: 编译失败（pageUserRoles 4 参不存在）→ FAIL。

- [ ] **Step 4: 实现三态 pageUserRoles + assembleRows overlay**

在 `EvalUserTagService` 加常量：

```java
/** 启用驱动分页取启用工号的上限保护。 */
private static final int ENABLED_DRIVEN_CAP = 5000;
```

替换 `pageUserRoles` 方法为：

```java
/**
 * 分页聚合查询人员标签列表，按"是否启用评价"三态分派。
 *
 * @param keyword     关键词（工号/姓名，可空）
 * @param evalEnabled 过滤态：null/"1"=只看启用(默认)，"0"=否，其它("all"/"")=全部
 * @param page        页码（从 1 开始）
 * @param pageSize    每页条数
 */
public PageResult<EvalUserRoleRowDTO> pageUserRoles(String keyword, String evalEnabled, int page, int pageSize) {
    String mode = normalizeEnabledMode(evalEnabled);
    if ("1".equals(mode)) {
        return pageEnabledDriven(keyword, page, pageSize);
    }
    // "all" / "0"：PT_USER 驱动
    PageResult<UserDTO> users = userApi.pageUsers(keyword, page, pageSize);
    List<EvalUserRoleRowDTO> rows = assembleRows(users.getRecords());
    if ("0".equals(mode)) {
        // 近似分页：本页内存剔除已启用者；total 沿用 PT_USER 总数（轻微高估，"否"为浏览用途）
        rows = rows.stream()
                .filter(r -> r.getEvalEnabled() == null || r.getEvalEnabled() == 0)
                .collect(Collectors.toList());
    }
    return PageResult.of(page, pageSize, users.getTotal(), rows);
}

/** 归一过滤态：null/"1"->"1"；"0"->"0"；其它->"all"。 */
private String normalizeEnabledMode(String evalEnabled) {
    if (evalEnabled == null || "1".equals(evalEnabled.trim())) {
        return "1";
    }
    if ("0".equals(evalEnabled.trim())) {
        return "0";
    }
    return "all";
}

/** "是"过滤：eval 侧驱动——取全部启用工号，批量解析，内存关键词过滤+内存分页。 */
private PageResult<EvalUserRoleRowDTO> pageEnabledDriven(String keyword, int page, int pageSize) {
    List<String> enabledIds = evalUserSettingMapper.selectEnabledUserIds();
    if (enabledIds.size() > ENABLED_DRIVEN_CAP) {
        log.warn("[EvalUserTagService.pageEnabledDriven] 启用工号数 {} 超上限 {}，截断", enabledIds.size(), ENABLED_DRIVEN_CAP);
        enabledIds = new ArrayList<>(enabledIds.subList(0, ENABLED_DRIVEN_CAP));
    }
    if (enabledIds.isEmpty()) {
        return PageResult.of(page, pageSize, 0L, new ArrayList<>());
    }
    List<UserDTO> users = userApi.getUserByEmpIds(enabledIds);
    String kw = keyword == null ? "" : keyword.trim();
    if (!kw.isEmpty()) {
        users = users.stream().filter(u -> matchesKeyword(u, kw)).collect(Collectors.toList());
    }
    long total = users.size();
    int from = Math.max(0, (page - 1) * pageSize);
    int to = Math.min(users.size(), from + pageSize);
    List<UserDTO> pageUsers = from >= users.size() ? new ArrayList<>() : users.subList(from, to);
    List<EvalUserRoleRowDTO> rows = assembleRows(pageUsers);
    return PageResult.of(page, pageSize, total, rows);
}

/** 关键词匹配：工号 / 中文名 / 登录名 任一 contains。 */
private boolean matchesKeyword(UserDTO u, String kw) {
    return (u.getEmpId() != null && u.getEmpId().contains(kw))
            || (u.getDisplayName() != null && u.getDisplayName().contains(kw))
            || (u.getUsername() != null && u.getUsername().contains(kw));
}
```

在 `assembleRows` 末尾装配 `evalEnabled` overlay。把现有 `assembleRows` 内 `for (UserDTO u : records)` 循环**之前**加上：

```java
Set<String> enabledSet = empIds.isEmpty()
        ? java.util.Set.of()
        : new HashSet<>(evalUserSettingMapper.selectEnabledUserIdsIn(empIds));
```

并在循环内（设置完 beEval/evalTags 之后、`rows.add(row)` 之前）加：

```java
row.setEvalEnabled(enabledSet.contains(u.getEmpId()) ? 1 : 0);
```

（`HashSet` 已 import；`Set` 用全限定或加 import `java.util.Set`。）

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: BUILD SUCCESS（全部用例绿）。

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleRowDTO.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java
git commit -m "feat(eval): pageUserRoles 三态过滤 + evalEnabled overlay"
```

---

## Task 5: Service 导出取数加 evalEnabled

**Files:**
- Modify: `performance-engine-center/.../eval/service/EvalUserTagService.java`
- Test: `performance-engine-center/.../eval/service/EvalUserTagExportTest.java`

设计要点：`listForExport` 签名加 `String evalEnabled`：`listForExport(String keyword, String evalEnabled, int cap)`。"1" 走启用驱动全量（取全部启用工号→cap 截断→关键词过滤→assembleRows）；"all"/"0" 走现有 PT_USER 翻页累积，"0" 再剔除已启用。

- [ ] **Step 1: 在 EvalUserTagExportTest 加 @Mock + 改签名 + 新用例**

加字段：

```java
@Mock EvalUserSettingMapper evalUserSettingMapper;
```

加 import：`import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;`

把现有 3 个用例的 `service.listForExport(kw, cap)` 改为 `service.listForExport(kw, "all", cap)`，并对每个补桩 `when(evalUserSettingMapper.selectEnabledUserIdsIn(anyList())).thenReturn(List.of());`（`listForExport_emptyWhenNoUsers` 因 empIds 为空不会触发，可不加）。

新增用例：

```java
@Test
void listForExport_enabledMode_usesSettingDriven() {
    when(evalUserSettingMapper.selectEnabledUserIds()).thenReturn(List.of("1001", "1002"));
    when(userApi.getUserByEmpIds(List.of("1001", "1002")))
            .thenReturn(List.of(user("1001", "张三"), user("1002", "李四")));
    when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
    when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
    when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
    when(evalUserSettingMapper.selectEnabledUserIdsIn(anyList())).thenReturn(List.of("1001", "1002"));

    List<EvalUserRoleRowDTO> rows = service.listForExport(null, "1", 10000);

    assertThat(rows).hasSize(2);
    assertThat(rows).allSatisfy(r -> assertThat(r.getEvalEnabled()).isEqualTo(1));
    verify(userApi, never()).pageUsers(any(), anyInt(), anyInt());
}
```

加 import：`import static org.mockito.ArgumentMatchers.anyInt;`（若缺）。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagExportTest`
Expected: 编译失败（listForExport 3 参不存在）→ FAIL。

- [ ] **Step 3: 实现 listForExport 新签名**

替换 `listForExport` 方法为：

```java
/**
 * 导出用：取关键词匹配的全部人员，按"是否启用评价"过滤态分派。
 *
 * @param keyword     关键词（工号/姓名，可空）
 * @param evalEnabled 过滤态：null/"1"=只看启用，"0"=否，其它=全部
 * @param cap         最大导出行数
 */
public List<EvalUserRoleRowDTO> listForExport(String keyword, String evalEnabled, int cap) {
    String mode = normalizeEnabledMode(evalEnabled);
    if ("1".equals(mode)) {
        List<String> enabledIds = evalUserSettingMapper.selectEnabledUserIds();
        if (enabledIds.size() > cap) {
            log.warn("[EvalUserTagService.listForExport] 启用工号数 {} 超 cap {}，截断", enabledIds.size(), cap);
            enabledIds = new ArrayList<>(enabledIds.subList(0, cap));
        }
        if (enabledIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<UserDTO> users = userApi.getUserByEmpIds(enabledIds);
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            users = users.stream().filter(u -> matchesKeyword(u, kw)).collect(Collectors.toList());
        }
        return assembleRows(users);
    }
    // "all" / "0"：PT_USER 翻页累积
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
    if ("0".equals(mode)) {
        all = all.stream()
                .filter(r -> r.getEvalEnabled() == null || r.getEvalEnabled() == 0)
                .collect(Collectors.toList());
    }
    if (all.size() > cap) {
        return new ArrayList<>(all.subList(0, cap));
    }
    return all;
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagExportTest`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagExportTest.java
git commit -m "feat(eval): listForExport 按是否启用评价过滤态取数"
```

---

## Task 6: 导入行/导出行 DTO 加"是否启用评价"列

**Files:**
- Modify: `performance-engine-center/.../eval/dto/EvalUserTagImportRow.java`
- Modify: `performance-engine-center/.../eval/dto/EvalUserRoleExportRow.java`

> 纯字段新增，行为由 Task 7（导入校验）与 Task 9（导出映射）的测试/验证覆盖。

- [ ] **Step 1: 导入行加列**

`EvalUserTagImportRow.java` 增字段（放在 evalRoleNames 之后）：

```java
@ExcelProperty("是否启用评价")
private String evalEnabledText;
```

- [ ] **Step 2: 导出行加列**

`EvalUserRoleExportRow.java` 增字段（放在 evalRoles 之后）：

```java
@ExcelProperty("是否启用评价")
private String evalEnabled;
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl performance-engine-center test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagImportRow.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleExportRow.java
git commit -m "feat(eval): 导入/导出行 DTO 加'是否启用评价'列"
```

---

## Task 7: 导入校验新列 + 调 saveUserRolesWithSetting

**Files:**
- Modify: `performance-engine-center/.../eval/service/EvalUserTagImportService.java`
- Test: `performance-engine-center/.../eval/service/EvalUserTagImportServiceTest.java`

设计要点：
- `ParsedRow` 加 `int evalEnabled`。
- 逐行校验"是否启用评价"列：trim 后**必须**是 `是` 或 `否`，否则该行报错 `是否启用评价只能填"是"或"否"`；`是`→1，`否`→0。
- 入库改调 `evalUserTagService.saveUserRolesWithSetting(empId, beEvalTagId, evalTagIds, evalEnabled)`。
- 现有"被评价/评价不能同时为空"规则不变。

- [ ] **Step 1: 改测试 helper + 写失败测试**

修改 `row(...)` helper 增第 4 参，并把现有用例对应补 "是"（保持现有用例通过）：

```java
private EvalUserTagImportRow row(String empId, String beEval, String eval, String enabledText) {
    EvalUserTagImportRow r = new EvalUserTagImportRow();
    r.setEmpId(empId);
    r.setBeEvalRoleName(beEval);
    r.setEvalRoleNames(eval);
    r.setEvalEnabledText(enabledText);
    return r;
}
```

> 现有调用 `row(a,b,c)` 全部改为 `row(a,b,c,"是")`（除非用例本身在测启用列校验）。同时把断言 `verify(evalUserTagService).saveUserRoles(...)` 改为 `verify(evalUserTagService).saveUserRolesWithSetting(...)` 并补第 4 参。

例如 `importRows_allValid_savesEachAndReturnsSuccess` 改为：

```java
List<EvalUserTagImportRow> rows = List.of(
        row("2280", "支行行长", "副行长,客户经理", "是"),
        row("E001", "", "副行长", "否"));

EvalUserTagImportResultDTO res = service.importRows(rows);

assertThat(res.isSuccess()).isTrue();
assertThat(res.getImportedCount()).isEqualTo(2);
verify(evalUserTagService).saveUserRolesWithSetting("2280", 1L, List.of(2L, 3L), 1);
verify(evalUserTagService).saveUserRolesWithSetting("E001", null, List.of(2L), 0);
```

新增校验用例：

```java
@Test
void importRows_invalidEnabledText_savesNothing() {
    when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
    List<EvalUserTagImportRow> rows = List.of(
            row("2280", "支行行长", "副行长", "Y"));

    EvalUserTagImportResultDTO res = service.importRows(rows);

    assertThat(res.isSuccess()).isFalse();
    assertThat(res.getImportedCount()).isZero();
    assertThat(res.getErrors()).hasSize(1);
    assertThat(res.getErrors().get(0).getMessage()).contains("是否启用评价");
    verify(evalUserTagService, never()).saveUserRolesWithSetting(any(), any(), anyList(), any());
}

@Test
void importRows_emptyEnabledText_savesNothing() {
    when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
    List<EvalUserTagImportRow> rows = List.of(
            row("2280", "支行行长", "副行长", ""));

    EvalUserTagImportResultDTO res = service.importRows(rows);

    assertThat(res.isSuccess()).isFalse();
    assertThat(res.getErrors().get(0).getMessage()).contains("是否启用评价");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagImportServiceTest`
Expected: 编译失败（setEvalEnabledText/saveUserRolesWithSetting 不存在或断言不符）→ FAIL。

- [ ] **Step 3: 实现校验 + 入库**

在 `EvalUserTagImportService.importRows` 的逐行校验循环里，在"评价角色"校验通过、`if (beEvalTagId == null && evalTagIds.isEmpty())` 这段**之前**插入启用列校验：

```java
// 是否启用评价：必填，仅"是"/"否"
String enabledRaw = r.getEvalEnabledText() == null ? "" : r.getEvalEnabledText().trim();
int evalEnabled;
if ("是".equals(enabledRaw)) {
    evalEnabled = 1;
} else if ("否".equals(enabledRaw)) {
    evalEnabled = 0;
} else {
    errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "是否启用评价只能填\"是\"或\"否\""));
    continue;
}
```

把 `parsed.add(new ParsedRow(empId, beEvalTagId, evalTagIds));` 改为：

```java
parsed.add(new ParsedRow(empId, beEvalTagId, evalTagIds, evalEnabled));
```

入库循环改为：

```java
for (ParsedRow p : parsed) {
    evalUserTagService.saveUserRolesWithSetting(p.empId, p.beEvalTagId, p.evalTagIds, p.evalEnabled);
}
```

`ParsedRow` 内部类增字段与构造器参数：

```java
private static class ParsedRow {
    final String empId;
    final Long beEvalTagId;
    final List<Long> evalTagIds;
    final int evalEnabled;

    ParsedRow(String empId, Long beEvalTagId, List<Long> evalTagIds, int evalEnabled) {
        this.empId = empId;
        this.beEvalTagId = beEvalTagId;
        this.evalTagIds = evalTagIds;
        this.evalEnabled = evalEnabled;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserTagImportServiceTest`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportServiceTest.java
git commit -m "feat(eval): 导入校验'是否启用评价'列并随角色原子入库"
```

---

## Task 8: Controller 接线（page/saveRoles/import-template/export）

**Files:**
- Modify: `performance-engine-center/.../eval/controller/EvalUserTagController.java`

> 控制器为接线层，eval 无控制器单测的既定模式；本任务验证手段=`test-compile` + 全模块 surefire 回归 + 手工 smoke。所有承载逻辑的方法已在 Task 3-7 被单测覆盖。

- [ ] **Step 1: page 端点加 evalEnabled 参**

把 `page(...)` 方法签名与调用改为：

```java
@GetMapping("/page")
@Operation(summary = "分页查询人员标签列表（含部门/岗位/角色/是否启用评价）")
@BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
public ResponseWrapper<PageResult<EvalUserRoleRowDTO>> page(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "evalEnabled", required = false) String evalEnabled,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    log.debug("[EvalUserTagController.page] keyword={}, evalEnabled={}, page={}, pageSize={}",
            keyword, evalEnabled, page, pageSize);
    return ResponseWrapper.success(evalUserTagService.pageUserRoles(keyword, evalEnabled, page, pageSize));
}
```

- [ ] **Step 2: saveRoles 加 evalEnabled 并改调编排方法**

`SaveRolesReq` 加字段：

```java
/** 是否启用评价：1=是 0=否；null 兜底为 0. */
private Integer evalEnabled;
```

`saveRoles(...)` 方法体改为：

```java
log.info("[EvalUserTagController.saveRoles] userId={}, beEvalTagId={}, evalTagIds={}, evalEnabled={}",
        userId, req.getBeEvalTagId(), req.getEvalTagIds(), req.getEvalEnabled());
evalUserTagService.saveUserRolesWithSetting(userId, req.getBeEvalTagId(), req.getEvalTagIds(), req.getEvalEnabled());
return ResponseWrapper.success();
```

- [ ] **Step 3: 导入模板样例加启用列**

在 `importTemplate(...)` 的 sample 构造里加：

```java
sample.setEvalEnabledText("是");
```

- [ ] **Step 4: export 加 evalEnabled 参 + 列映射**

`export(...)` 方法签名加参并调用新 `listForExport`：

```java
public void export(@RequestParam(value = "keyword", required = false) String keyword,
                   @RequestParam(value = "evalEnabled", required = false) String evalEnabled,
                   HttpServletResponse response) throws IOException {
    log.info("[EvalUserTagController.export] keyword={}, evalEnabled={}", keyword, evalEnabled);
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    String fileName = URLEncoder.encode("人员标签列表.xlsx", StandardCharsets.UTF_8);
    response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
    List<EvalUserRoleRowDTO> rows = evalUserTagService.listForExport(keyword, evalEnabled, EXPORT_ROWS_CAP);
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
        e.setEvalEnabled(Integer.valueOf(1).equals(r.getEvalEnabled()) ? "是" : "否");
        out.add(e);
    }
    EasyExcel.write(response.getOutputStream(), EvalUserRoleExportRow.class)
            .sheet("人员标签列表")
            .doWrite(out);
}
```

- [ ] **Step 5: 编译 + 全模块 surefire 回归**

Run: `mvn -q -pl performance-engine-center test -Dtest='EvalUser*Test'`
Expected: BUILD SUCCESS（EvalUserRoleServiceTest / EvalUserTagExportTest / EvalUserTagImportServiceTest 全绿）。

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalUserTagController.java
git commit -m "feat(eval): controller 接线 evalEnabled（page/saveRoles/模板/export）"
```

---

## Task 9: 前端 api/eval.js 加 evalEnabled 参

**Files:**
- Modify: `xanzc_frontend/src/api/eval.js`

> `pageUserRoles(params)` 已透传 params，调用方传 `evalEnabled` 即可，无需改它。仅改 `saveUserRoles` / `exportUserRoles`。前端无单测，验证=构建通过 + 手工。

- [ ] **Step 1: 改 saveUserRoles / exportUserRoles**

把第 56-77 行对应两函数替换为：

```js
// 覆盖式保存人员评价角色（被评价单选 / 评价人多选）+ 是否启用评价(1/0)
export function saveUserRoles(userId, beEvalTagId, evalTagIds, evalEnabled) {
  return call('put', `/admin/eval/user-tags/${userId}/roles`, { data: { beEvalTagId, evalTagIds, evalEnabled } }, { ok: true });
}
```

```js
export function exportUserRoles(keyword, evalEnabled) {
  return call('get', '/admin/eval/user-tags/export', { params: { keyword, evalEnabled }, responseType: 'blob' }, null);
}
```

- [ ] **Step 2: 前端构建验证**

Run: `cd /home/djdev/leid/yiti/xanzc_frontend && npm run build 2>&1 | tail -20`
Expected: 构建成功（无该文件语法错误）。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/api/eval.js
git commit -m "feat(eval-fe): api 加 evalEnabled 参（saveUserRoles/exportUserRoles）"
```

---

## Task 10: 前端 UserTags.vue 过滤下拉 + 列 + 编辑下拉

**Files:**
- Modify: `xanzc_frontend/src/views/eval/UserTags.vue`

- [ ] **Step 1: 工具栏加"是否启用评价"过滤下拉**

在 `<div class="toolbar">` 内，关键词 `el-input` 之后、查询按钮之前插入：

```html
<el-select v-model="evalEnabledFilter" size="small" style="width:140px" @change="doSearch">
  <el-option label="启用：是" value="1" />
  <el-option label="启用：否" value="0" />
  <el-option label="全部" value="all" />
</el-select>
```

- [ ] **Step 2: 列表加"是否启用评价"列**

在"评价人角色"列之后、"操作"列之前插入：

```html
<el-table-column label="是否启用评价" min-width="120">
  <template #default="{ row }">
    <el-tag v-if="row.evalEnabled === 1" type="success" effect="plain" size="small">是</el-tag>
    <el-tag v-else type="info" effect="plain" size="small">否</el-tag>
  </template>
</el-table-column>
```

- [ ] **Step 3: 编辑弹窗加"是否启用评价"下拉**

在编辑弹窗 `<el-form>` 内"评价人角色" `el-form-item` 之后插入：

```html
<el-form-item label="是否启用评价">
  <el-select v-model="form.evalEnabled" style="width: 100%">
    <el-option :value="1" label="是" />
    <el-option :value="0" label="否" />
  </el-select>
</el-form-item>
```

- [ ] **Step 4: script 增状态与传参**

在 `const keyword = ref('');` 之后加：

```js
const evalEnabledFilter = ref('1'); // 默认只看启用=是
```

`reload()` 内 `pageUserRoles` 调用增 `evalEnabled`：

```js
const r = await pageUserRoles({ keyword: keyword.value.trim() || undefined, evalEnabled: evalEnabledFilter.value, page: page.value, pageSize: pageSize.value });
```

`resetSearch()` 内重置过滤态（在 `keyword.value=''` 之后加）：

```js
evalEnabledFilter.value = '1';
```

`form` 默认值加 evalEnabled（把 `const form = reactive({ beEvalTagId: null, evalTagIds: [] });` 改为）：

```js
const form = reactive({ beEvalTagId: null, evalTagIds: [], evalEnabled: 0 });
```

`openEdit(row)` 内加：

```js
form.evalEnabled = (row.evalEnabled === 1) ? 1 : 0;
```

`onDialogClosed()` 内加：

```js
form.evalEnabled = 0;
```

`handleSave()` 内 `saveUserRoles` 调用改为：

```js
await saveUserRoles(editing.value.userId, form.beEvalTagId ?? null, form.evalTagIds || [], form.evalEnabled);
```

`doExport()` 内调用改为：

```js
const blob = await exportUserRoles(keyword.value, evalEnabledFilter.value);
```

- [ ] **Step 5: 前端构建验证**

Run: `cd /home/djdev/leid/yiti/xanzc_frontend && npm run build 2>&1 | tail -20`
Expected: 构建成功。

- [ ] **Step 6: Commit**

```bash
git add xanzc_frontend/src/views/eval/UserTags.vue
git commit -m "feat(eval-fe): 人员标签页 是否启用评价 过滤/列/编辑下拉"
```

---

## Task 11: 全量回归 + 文档同步

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（追加本次变更摘要）

- [ ] **Step 1: 跨模块 install + perf 模块 surefire 回归**

Run:
```bash
mvn clean install -DskipTests -q && mvn -q -pl performance-engine-center test
```
Expected: perf surefire 全绿（含本次 EvalUser* 新用例）；与 V1.13 已知 baseline 失败一致，无新增回归。若有失败，逐一归因（新增 vs baseline）。

- [ ] **Step 2: 文档摘要**

在 `performance-engine-center/CLAUDE.md` 顶部"模块概述"附近追加一行交付摘要（一句话）：`2026-05-29 eval 人员标签新增"是否启用评价"（EVAL_USER_SETTING 表 + 三态过滤 + 导入/导出/模板贯通）`。

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(eval): 记录'是否启用评价'交付摘要"
```

---

## 上线提醒（执行计划外，交付时告知用户）

- **三库手工执行** `docs/superpowers/sql/2026-05-29-eval-user-setting.sql`（yiti / onepl / onepl_test_bootstrap），否则 page/save/import 报"表不存在"。
- **默认列表语义变化**：上线后默认只显示"启用=是"的人；历史数据全为"否"→默认页为空，需切"全部"或按工号搜索找人启用。
- **导入模板破坏性**：新增必填列"是否启用评价"，旧模板导入会整批报错；需发新模板给业务方。

---

## Self-Review

**1. Spec coverage:**
- §3.1 新表/实体/Mapper → Task 1/2 ✅
- §3.2 三态过滤 + evalEnabled overlay + cap → Task 4 ✅
- §3.3 复用保存（saveUserRolesWithSetting）+ 编辑下拉 → Task 3/8/10 ✅
- §3.4 导入新列校验 → Task 6/7 ✅
- §3.5 导出新列 + 过滤态取数 → Task 5/6/8 ✅
- §3.6 前端过滤/列/编辑/传参 → Task 9/10 ✅
- §4 测试（mapper 走 mock service 单测）→ Task 3/4/5/7 ✅
- §5 无新端点/权限 → 无任务（正确）✅
- §7 风险（上线提醒）→ 计划末尾 ✅

**2. Placeholder scan:** 无 TBD/TODO/"类似 TaskN"；每个改代码步骤均含完整代码。✅

**3. Type consistency:**
- `saveUserRolesWithSetting(String, Long, List<Long>, Integer)` — Task 3 定义，Task 7/8 调用一致 ✅
- `pageUserRoles(String keyword, String evalEnabled, int page, int pageSize)` — Task 4 定义，Task 8 调用一致 ✅
- `listForExport(String keyword, String evalEnabled, int cap)` — Task 5 定义，Task 8 调用一致 ✅
- `EvalUserSettingMapper.selectEnabledUserIds()/selectEnabledUserIdsIn(List)/upsert(String,int)` — Task 2 定义，Task 3/4/5 使用一致 ✅
- DTO 字段：`EvalUserRoleRowDTO.evalEnabled`(Integer) / `EvalUserTagImportRow.evalEnabledText`(String) / `EvalUserRoleExportRow.evalEnabled`(String) — 命名在各任务一致 ✅
- 前端 `evalEnabledFilter` 默认 `'1'`，`saveUserRoles(...,evalEnabled)` / `exportUserRoles(keyword,evalEnabled)` 与 api 签名一致 ✅
