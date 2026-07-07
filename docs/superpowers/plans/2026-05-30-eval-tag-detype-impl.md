# 评价标签去类型化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把"被评价/评价"角色从 `EVAL_TAG.tag_type`（标签属性）搬到使用处（`EVAL_USER_TAG.role_type` + 规则的列），让标签管理页拍平为无类型标签池，人员标签页与评价规则仍区分两角色并施加局部排斥。

**Architecture:** 删 `EVAL_TAG.TAG_TYPE`；`EVAL_USER_TAG` 加 `ROLE_TYPE`；引擎查询从 JOIN tag_type 切到 role_type；`saveUserRoles`/导入/规则三处加"评价角色 ≠ 被评价角色"局部校验（新错误码 `EVAL_ROLE_CONFLICT`）；前端 Tags/UserTags/Rules 三页去类型 + 互斥下拉。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis-Plus + JUnit5/Mockito（后端）；Vue3 + Element Plus（前端）。MySQL DDL 走 SQL 直接执行（Flyway 已废弃）。

**Spec:** `docs/superpowers/specs/2026-05-30-eval-tag-detype-design.md`

**全局约束（红线）**：
- TDD 红-绿-重构，每步独立 commit
- 派遣 subagent 时 model 须为高能力模型，禁用低配模型
- 中文注释，UTF-8 编码
- 禁止引入 Flyway / `spring.flyway.*` / `V*__*.sql`

**测试运行**：`cd /home/djdev/leid/yiti && mvn test -pl performance-engine-center -Dtest=<类名>`（单测 surefire）。
**前端构建**：`cd /home/djdev/leid/yiti/xanzc_frontend && npm run build`。
**开发库**：yiti（`mysql -uroot -pdjdev yiti`）。

---

## File Structure

**后端 main**：
- `entity/EvalTag.java` — 删 tagType
- `entity/EvalUserTag.java` — 加 roleType
- `dto/EvalUserTagRow.java` — tagType→roleType
- `mapper/EvalTagMapper.java` + `resources/mapper/performance/EvalTagMapper.xml` — 去 tag_type、selectByNameAndType→selectByName
- `mapper/EvalUserTagMapper.java` + `EvalUserTagMapper.xml` — role_type 化
- `service/EvalTagService.java` — create/update/list 去 type
- `service/EvalUserTagService.java` — saveUserRoles role + 局部排斥 + assembleRows by roleType
- `service/EvalUserTagImportService.java` — 扁平解析 + 局部排斥
- `service/EvalRuleService.java` — 规则局部排斥
- `controller/EvalTagController.java` — 去 tagType 参数
- `enums/PerfErrorCode.java` — +EVAL_ROLE_CONFLICT，-EVAL_TAG_TYPE_MISMATCH

**后端 test**：
- `service/EvalTagServiceTest.java`、`EvalUserRoleServiceTest.java`、`EvalUserTagImportServiceTest.java`、`EvalRuleServiceTest.java`

**前端**：
- `src/views/eval/Tags.vue`、`src/api/eval.js`、`src/views/eval/UserTags.vue`、`src/views/eval/Rules.vue`

**SQL / 基线**：
- `docs/superpowers/sql/2026-05-30-eval-tag-detype.sql`（迁移）
- `docs/schema/ddl-eval.sql`（基线更新）

---

## Task 1: 数据库迁移 + 基线 DDL

**Files:**
- Create: `docs/superpowers/sql/2026-05-30-eval-tag-detype.sql`
- Create: `docs/superpowers/sql/backup/2026-05-30-eval-tag-usertag-backup.sql`（mysqldump 产物）
- Modify: `docs/schema/ddl-eval.sql:7-24`

- [ ] **Step 1: 备份两表**

```bash
cd /home/djdev/leid/yiti
mysqldump -uroot -pdjdev yiti EVAL_TAG EVAL_USER_TAG > docs/superpowers/sql/backup/2026-05-30-eval-tag-usertag-backup.sql
```
Expected: 文件生成，含两表 CREATE + INSERT。

- [ ] **Step 2: 冲突预检**

```bash
mysql -uroot -pdjdev yiti -e "SELECT TAG_NAME, COUNT(DISTINCT TAG_TYPE) c FROM EVAL_TAG GROUP BY TAG_NAME HAVING c>1;"
```
Expected: 空结果（yiti 已实测 0 冲突）。若非空，停止并人工合并同名 tag_id（保留较小 id，UPDATE EVAL_USER_TAG/EVAL_RULE/EVAL_RULE_GROUP 的 FK 后 DELETE 多余行）再继续。

- [ ] **Step 3: 写迁移脚本**

`docs/superpowers/sql/2026-05-30-eval-tag-detype.sql`：
```sql
-- 评价标签去类型化迁移（2026-05-30）
-- 执行前务必先 mysqldump 备份 EVAL_TAG / EVAL_USER_TAG。
-- 次序关键：先回填 EVAL_USER_TAG.ROLE_TYPE，再 DROP EVAL_TAG.TAG_TYPE。

-- 1. EVAL_USER_TAG 加角色列（临时默认 1 便于回填）
ALTER TABLE EVAL_USER_TAG
  ADD COLUMN ROLE_TYPE TINYINT NOT NULL DEFAULT 1 COMMENT '1=被评价角色,2=评价角色';

-- 2. 从旧 tag_type 回填角色
UPDATE EVAL_USER_TAG ut
  JOIN EVAL_TAG t ON t.TAG_ID = ut.TAG_ID
  SET ut.ROLE_TYPE = t.TAG_TYPE;

-- 3. 唯一键纳入角色（同人同标签可分属两角色各一行）
ALTER TABLE EVAL_USER_TAG DROP KEY UK_USER_TAG;
ALTER TABLE EVAL_USER_TAG ADD UNIQUE KEY UK_USER_TAG_ROLE (USER_ID, TAG_ID, ROLE_TYPE);

-- 4. 回填后角色必须显式，去掉默认值
ALTER TABLE EVAL_USER_TAG ALTER COLUMN ROLE_TYPE DROP DEFAULT;

-- 5. EVAL_TAG 拍平：唯一键改为按名称，删类型列
ALTER TABLE EVAL_TAG DROP KEY UK_TAG_NAME_TYPE;
ALTER TABLE EVAL_TAG ADD UNIQUE KEY UK_TAG_NAME (TAG_NAME);
ALTER TABLE EVAL_TAG DROP COLUMN TAG_TYPE;
```

- [ ] **Step 4: 在 yiti 执行并验证**

```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-05-30-eval-tag-detype.sql
mysql -uroot -pdjdev yiti -e "SHOW COLUMNS FROM EVAL_TAG; SHOW COLUMNS FROM EVAL_USER_TAG; SELECT TAG_ID,ROLE_TYPE FROM EVAL_USER_TAG;"
```
Expected: `EVAL_TAG` 无 TAG_TYPE 列；`EVAL_USER_TAG` 有 ROLE_TYPE 列且每行有 1/2 值（与迁移前 JOIN 一致）。

- [ ] **Step 5: 更新基线 ddl-eval.sql**

`docs/schema/ddl-eval.sql` 第 7-24 行替换为：
```sql
CREATE TABLE IF NOT EXISTS EVAL_TAG (
    TAG_ID      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    TAG_NAME    VARCHAR(50)  NOT NULL COMMENT '标签名称',
    STATUS      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用, 0=停用',
    CREATE_TIME DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UPDATE_TIME DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (TAG_ID),
    UNIQUE KEY UK_TAG_NAME (TAG_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价标签字典表（无类型，被评价/评价角色由使用处承载）';

CREATE TABLE IF NOT EXISTS EVAL_USER_TAG (
    ID        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    USER_ID   VARCHAR(50) NOT NULL COMMENT '人员工号，关联 PT_USER.USER_ID',
    TAG_ID    BIGINT NOT NULL COMMENT '标签ID，关联 EVAL_TAG.TAG_ID',
    ROLE_TYPE TINYINT NOT NULL COMMENT '1=被评价角色, 2=评价角色',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_USER_TAG_ROLE (USER_ID, TAG_ID, ROLE_TYPE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员标签关联表';
```

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/sql/2026-05-30-eval-tag-detype.sql docs/superpowers/sql/backup/2026-05-30-eval-tag-usertag-backup.sql docs/schema/ddl-eval.sql
git commit -m "feat(eval): EVAL_TAG 去 tag_type + EVAL_USER_TAG 加 role_type 迁移脚本与基线"
```

---

## Task 2: 错误码 EVAL_ROLE_CONFLICT

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java:176,179`

- [ ] **Step 1: 改枚举**

把第 176 行 `EVAL_TAG_TYPE_MISMATCH("PERF-40059", "标签类型不匹配"),` **删除**，并在 `EVAL_IMPORT_ROWS_EXCEEDED("PERF-40062", ...)` 之后追加（注意原第 179 行末尾分号要移到新最后一项）：
```java
    EVAL_IMPORT_ROWS_EXCEEDED("PERF-40062", "导入行数超过上限"),
    EVAL_ROLE_CONFLICT("PERF-40063", "评价角色不能与被评价角色相同");
```

- [ ] **Step 2: 编译验证（此刻 EvalUserTagService 仍引用旧码，预期失败）**

Run: `cd /home/djdev/leid/yiti && mvn -q compile -pl performance-engine-center`
Expected: 编译失败，`EvalUserTagService.java` 报 `EVAL_TAG_TYPE_MISMATCH` 找不到符号 —— 这正是 Task 4 要改的点，暂不在本任务修复。

> 说明：本任务与 Task 4 耦合（删错误码会断 EvalUserTagService 编译）。执行顺序按编号串行即可，Task 4 完成后整体绿。若希望每个 commit 都可编译，可把本任务的"删除 EVAL_TAG_TYPE_MISMATCH"合并到 Task 4 的 commit 中；新增 EVAL_ROLE_CONFLICT 可独立先提。

- [ ] **Step 3: Commit（仅新增，不删旧码，保编译绿）**

仅追加 `EVAL_ROLE_CONFLICT`，暂保留 `EVAL_TAG_TYPE_MISMATCH`（Task 4 删）：
```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java
git commit -m "feat(eval): 新增错误码 EVAL_ROLE_CONFLICT(PERF-40063)"
```

---

## Task 3: EvalTag 去类型（entity / mapper / service / controller）

**Files:**
- Modify: `entity/EvalTag.java`
- Modify: `mapper/EvalTagMapper.java`
- Modify: `resources/mapper/performance/EvalTagMapper.xml`
- Modify: `service/EvalTagService.java`
- Modify: `controller/EvalTagController.java`
- Test: `service/EvalTagServiceTest.java`

- [ ] **Step 1: 改测试为 Red**

`EvalTagServiceTest.java`：
- 第 33 行 `when(evalTagMapper.selectByNameAndType("省分行正行长", 2)).thenReturn(null);` → `when(evalTagMapper.selectByName("省分行正行长")).thenReturn(null);`
- 第 39 行 `assertThat(result.getTagType()).isEqualTo(2);` → **删除**该断言
- 创建调用 `service.create("省分行正行长", 2)` → `service.create("省分行正行长")`（去掉第二参）
- 第 49 行 `selectByNameAndType("省分行正行长", 2)` → `selectByName("省分行正行长")`
- 第 62 行 `existing.setTagType(1);` → 删除
- 第 64 行 `selectByNameAndType("新名称", 1)` → `selectByName("新名称")`
- 第 89/105/108 行所有 `xx.setTagType(n);` → 删除

- [ ] **Step 2: 运行测试确认 Red**

Run: `mvn test -pl performance-engine-center -Dtest=EvalTagServiceTest`
Expected: 编译失败/红（`selectByName` 未定义、`create(String)` 不存在）。

- [ ] **Step 3: 改 entity**

`EvalTag.java` 删除第 20-21 行：
```java
    /** 标签类型：1=被评价人标签, 2=评价人标签. */
    private Integer tagType;
```

- [ ] **Step 4: 改 mapper 接口**

`EvalTagMapper.java`：
```java
    /** 按名称查询（唯一性校验用）. */
    EvalTag selectByName(@Param("tagName") String tagName);

    /** 分页条件查询（仅按名称关键词）. */
    List<EvalTag> selectByCondition(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    /** 条件计数. */
    long countByCondition(@Param("keyword") String keyword);

    /**
     * 查询全部标签（不分页，供下拉选项用）.
     * @param status 状态筛选（可选，null 表示不过滤）
     * @return 标签列表，按 tag_id ASC 排序
     */
    List<EvalTag> selectAll(@Param("status") Integer status);
```

- [ ] **Step 5: 改 mapper XML**

`EvalTagMapper.xml` 全文替换为：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.performance.eval.mapper.EvalTagMapper">
    <sql id="BASE_COLUMNS">tag_id, tag_name, status, create_time, update_time</sql>

    <select id="selectByName" resultType="com.bank.branch.platform.performance.eval.entity.EvalTag">
        SELECT <include refid="BASE_COLUMNS"/> FROM EVAL_TAG WHERE tag_name = #{tagName} LIMIT 1
    </select>

    <select id="selectByCondition" resultType="com.bank.branch.platform.performance.eval.entity.EvalTag">
        SELECT <include refid="BASE_COLUMNS"/> FROM EVAL_TAG
        <where>
            <if test="keyword != null and keyword != ''">AND tag_name LIKE CONCAT('%', #{keyword}, '%')</if>
        </where>
        ORDER BY tag_id ASC LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countByCondition" resultType="long">
        SELECT COUNT(*) FROM EVAL_TAG
        <where>
            <if test="keyword != null and keyword != ''">AND tag_name LIKE CONCAT('%', #{keyword}, '%')</if>
        </where>
    </select>

    <select id="selectAll" resultType="com.bank.branch.platform.performance.eval.entity.EvalTag">
        SELECT <include refid="BASE_COLUMNS"/> FROM EVAL_TAG
        <where>
            <if test="status != null">AND status = #{status}</if>
        </where>
        ORDER BY tag_id ASC
    </select>
</mapper>
```

- [ ] **Step 6: 改 service**

`EvalTagService.java`：
- `create` 改签名与体：
```java
    /**
     * 创建标签（无类型，按名称唯一）.
     *
     * @param tagName 标签名称
     * @return 创建后的标签实体
     * @throws PerfException 名称重复时抛 EVAL_TAG_NAME_DUP（PERF-40050）
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalTag create(String tagName) {
        EvalTag existing = evalTagMapper.selectByName(tagName);
        if (existing != null) {
            throw new PerfException(PerfErrorCode.EVAL_TAG_NAME_DUP, tagName);
        }
        EvalTag tag = new EvalTag();
        tag.setTagName(tagName);
        tag.setStatus(1); // 默认启用
        evalTagMapper.insert(tag);
        log.info("[EvalTagService.create] 创建评价标签成功 tagName={} tagId={}", tagName, tag.getTagId());
        return tag;
    }
```
- `update` 改名唯一性校验：第 69-73 行的 `selectByNameAndType(tagName, tag.getTagType())` → `selectByName(tagName)`：
```java
        if (tagName != null && !tagName.equals(tag.getTagName())) {
            EvalTag dup = evalTagMapper.selectByName(tagName);
            if (dup != null && !dup.getTagId().equals(tagId)) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_NAME_DUP, tagName);
            }
            tag.setTagName(tagName);
        }
```
- `listAll(Integer tagType, Integer status)` → `listAll(Integer status)`，体改为 `return evalTagMapper.selectAll(status);`，javadoc 去 tagType
- `list(Integer tagType, String keyword, int page, int pageSize)` → `list(String keyword, int page, int pageSize)`，体改为：
```java
    public PageResult<EvalTag> list(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalTag> rows = evalTagMapper.selectByCondition(keyword, offset, pageSize);
        long total = evalTagMapper.countByCondition(keyword);
        return PageResult.of(page, pageSize, total, rows);
    }
```

- [ ] **Step 7: 改 controller**

`EvalTagController.java`：
- `list`：删 `tagType` 参数行，调用改 `evalTagService.list(keyword, page, pageSize)`，日志去 tagType
- `create`：删 `@RequestParam("tagType") @NotNull Integer tagType` 参数，调用改 `evalTagService.create(tagName)`，日志去 tagType；可删除未用 import `jakarta.validation.constraints.NotNull`
- `listAll`：删 `tagType` 参数，调用改 `evalTagService.listAll(status)`，日志去 tagType

- [ ] **Step 8: 运行测试确认 Green**

Run: `mvn test -pl performance-engine-center -Dtest=EvalTagServiceTest`
Expected: PASS。

- [ ] **Step 9: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalTag.java \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalTagMapper.java \
  performance-engine-center/src/main/resources/mapper/performance/EvalTagMapper.xml \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalTagService.java \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalTagController.java \
  performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalTagServiceTest.java
git commit -m "feat(eval): 标签去类型化 - EvalTag/Mapper/Service/Controller 移除 tag_type"
```

---

## Task 4: EvalUserTag 角色化（entity / dto / mapper） + saveUserRoles 局部排斥

**Files:**
- Modify: `entity/EvalUserTag.java`
- Modify: `dto/EvalUserTagRow.java`
- Modify: `mapper/EvalUserTagMapper.java`
- Modify: `resources/mapper/performance/EvalUserTagMapper.xml`
- Modify: `service/EvalUserTagService.java`
- Modify: `enums/PerfErrorCode.java`（删 EVAL_TAG_TYPE_MISMATCH）
- Test: `service/EvalUserRoleServiceTest.java`

- [ ] **Step 1: 改测试为 Red**

`EvalUserRoleServiceTest.java`：
- 第 51 行 `t.setTagType(type);` → `t.setRoleType(type);`（`tagRow` 辅助方法第 136-139 行：`r.setTagType(type)` → `r.setRoleType(type)`）
- 删除第 98-117 行两个 TYPE_MISMATCH 测试（`saveUserRoles beEvalTagId 指向评价人标签...` 与 `saveUserRoles evalTagIds 含被评价标签...`），**替换**为局部排斥测试：
```java
    @Test
    @DisplayName("saveUserRoles 评价角色含被评价标签 → EVAL_ROLE_CONFLICT(PERF-40063)")
    void saveUserRoles_evalContainsBeEval_throwsConflict() {
        // 被评价标签 100 同时出现在评价列表 → 冲突
        assertThatThrownBy(() -> service.saveUserRoles("1001", 100L, List.of(100L, 200L)))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EVAL_ROLE_CONFLICT));
    }

    @Test
    @DisplayName("saveUserRoles 角色不冲突 → 按 role_type 覆盖写入")
    void saveUserRoles_noConflict_insertsWithRoleType() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        service.saveUserRoles("1001", 100L, List.of(200L, 300L));
        // 校验 batchInsert 的实体带正确 role_type（1 个被评价 role=1 + 2 个评价 role=2）
        org.mockito.ArgumentCaptor<List<EvalUserTag>> cap = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(evalUserTagMapper).batchInsert(cap.capture());
        List<EvalUserTag> inserted = cap.getValue();
        assertThat(inserted).hasSize(3);
        assertThat(inserted.stream().filter(u -> Integer.valueOf(1).equals(u.getRoleType())).count()).isEqualTo(1);
        assertThat(inserted.stream().filter(u -> Integer.valueOf(2).equals(u.getRoleType())).count()).isEqualTo(2);
    }
```
（确保 import `com.bank.branch.platform.performance.eval.entity.EvalUserTag`、`org.mockito.ArgumentCaptor`、`static ...assertThatThrownBy`。旧测试若 mock 了 `evalTagMapper.selectById(...)` 做类型校验，一并删除这些 stub。）
- assembleRows 相关测试（第 152 行起用 `tagRow(...)` 的）：`tagRow` 已改 setRoleType，无需再动；这些用例验证按 roleType 拆分被评价/评价，逻辑等价。

- [ ] **Step 2: 运行确认 Red**

Run: `mvn test -pl performance-engine-center -Dtest=EvalUserRoleServiceTest`
Expected: 红（setRoleType 未定义、EVAL_ROLE_CONFLICT 未定义路径）。

- [ ] **Step 3: 改 entity**

`EvalUserTag.java` 在 tagId 字段后追加：
```java
    /** 角色类型：1=被评价角色, 2=评价角色. */
    private Integer roleType;
```

- [ ] **Step 4: 改 DTO**

`EvalUserTagRow.java` 第 14-15 行：
```java
    /** 角色类型：1=被评价角色, 2=评价角色. */
    private Integer roleType;
```

- [ ] **Step 5: 改 mapper 接口**

`EvalUserTagMapper.java`：`selectTagIdsByUserIdAndType` 的 `@Param("tagType")` → `@Param("roleType")`（方法名保持不变，避免 EvalScore/EvalTask 调用与测试改动）；javadoc 注释 `tagName/tagType` → `tagName/roleType`。

- [ ] **Step 6: 改 mapper XML**

`EvalUserTagMapper.xml` 全文替换为：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper">
    <select id="selectByUserId" resultType="com.bank.branch.platform.performance.eval.entity.EvalUserTag">
        SELECT id, user_id, tag_id, role_type FROM EVAL_USER_TAG WHERE user_id = #{userId}
    </select>

    <select id="selectUserIdsByTagId" resultType="java.lang.String">
        SELECT user_id FROM EVAL_USER_TAG WHERE tag_id = #{tagId}
    </select>

    <!-- 按角色取人员标签ID：直接读 EVAL_USER_TAG.role_type，不再 JOIN tag_type -->
    <select id="selectTagIdsByUserIdAndType" resultType="java.lang.Long">
        SELECT tag_id FROM EVAL_USER_TAG WHERE user_id = #{userId} AND role_type = #{roleType}
    </select>

    <insert id="batchInsert">
        INSERT INTO EVAL_USER_TAG (user_id, tag_id, role_type) VALUES
        <foreach collection="list" item="item" separator=",">(#{item.userId}, #{item.tagId}, #{item.roleType})</foreach>
    </insert>

    <delete id="batchDelete">
        DELETE FROM EVAL_USER_TAG WHERE user_id = #{userId} AND tag_id IN
        <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">#{tagId}</foreach>
    </delete>

    <!-- 批量查多人标签：role_type 取自 EVAL_USER_TAG，tag_name 仍 JOIN EVAL_TAG -->
    <select id="selectUserTagsByUserIds" resultType="com.bank.branch.platform.performance.eval.dto.EvalUserTagRow">
        SELECT ut.user_id AS userId, ut.tag_id AS tagId, t.tag_name AS tagName, ut.role_type AS roleType
        FROM EVAL_USER_TAG ut
        INNER JOIN EVAL_TAG t ON t.tag_id = ut.tag_id
        WHERE ut.user_id IN
        <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
    </select>
</mapper>
```

- [ ] **Step 7: 改 saveUserRoles**

`EvalUserTagService.java` 的 `saveUserRoles`（第 97-141 行）整体替换为（去 tagType 校验、加局部排斥、写 role_type）：
```java
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(String userId, Long beEvalTagId, List<Long> evalTagIds) {
        // 1. 评价人标签去重
        List<Long> evalIds = (evalTagIds == null) ? List.of()
                : evalTagIds.stream().distinct().collect(Collectors.toList());
        // 2. 局部排斥：评价角色不能包含被评价角色（同一人）
        if (beEvalTagId != null && evalIds.contains(beEvalTagId)) {
            throw new PerfException(PerfErrorCode.EVAL_ROLE_CONFLICT, beEvalTagId);
        }
        // 3. 标签存在性校验（标签已无类型，仅校验存在）
        if (beEvalTagId != null && evalTagMapper.selectById(beEvalTagId) == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, beEvalTagId);
        }
        for (Long tid : evalIds) {
            if (evalTagMapper.selectById(tid) == null) {
                throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tid);
            }
        }
        // 4. 覆盖：删除该用户全部旧关联
        List<EvalUserTag> existing = evalUserTagMapper.selectByUserId(userId);
        if (!existing.isEmpty()) {
            List<Long> oldTagIds = existing.stream().map(EvalUserTag::getTagId).collect(Collectors.toList());
            evalUserTagMapper.batchDelete(userId, oldTagIds);
        }
        // 5. 写入新组合：被评价 role_type=1，评价人 role_type=2
        List<EvalUserTag> toInsert = new ArrayList<>();
        if (beEvalTagId != null) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(beEvalTagId);
            u.setRoleType(1);
            toInsert.add(u);
        }
        for (Long tid : evalIds) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(tid);
            u.setRoleType(2);
            toInsert.add(u);
        }
        if (!toInsert.isEmpty()) {
            evalUserTagMapper.batchInsert(toInsert);
        }
        log.info("[EvalUserTagService.saveUserRoles] userId={} beEvalTagId={} evalTagIds={}", userId, beEvalTagId, evalIds);
    }
```
更新方法 javadoc：去掉"必须 tagType=1/2"措辞，改为"被评价单选 role=1，评价多选 role=2，二者不得包含同一标签"。

- [ ] **Step 8: 改 assembleRows 按 roleType 拆分**

`EvalUserTagService.assembleRows`（约第 334-342 行）的两个 filter：
```java
            EvalUserTagBriefDTO beEval = tagRows.stream()
                    .filter(t -> Integer.valueOf(1).equals(t.getRoleType()))
                    .findFirst()
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .orElse(null);
            List<EvalUserTagBriefDTO> evalTags = tagRows.stream()
                    .filter(t -> Integer.valueOf(2).equals(t.getRoleType()))
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .collect(Collectors.toList());
```

- [ ] **Step 9: 删除 EVAL_TAG_TYPE_MISMATCH 错误码**

`PerfErrorCode.java` 删除 `EVAL_TAG_TYPE_MISMATCH("PERF-40059", "标签类型不匹配"),` 行（Task 2 保留至此删除，确保全模块无引用）。

- [ ] **Step 10: 运行测试确认 Green**

Run: `mvn test -pl performance-engine-center -Dtest=EvalUserRoleServiceTest,EvalTagServiceTest`
Expected: PASS。

- [ ] **Step 11: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalUserTag.java \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagRow.java \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserTagMapper.java \
  performance-engine-center/src/main/resources/mapper/performance/EvalUserTagMapper.xml \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
  performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java \
  performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java
git commit -m "feat(eval): 人员标签角色化 role_type + saveUserRoles 局部排斥，删 EVAL_TAG_TYPE_MISMATCH"
```

---

## Task 5: 导入扁平解析 + 局部排斥

**Files:**
- Modify: `service/EvalUserTagImportService.java`
- Test: `service/EvalUserTagImportServiceTest.java`

- [ ] **Step 1: 改测试为 Red**

`EvalUserTagImportServiceTest.java` 第 37 行 `t.setTagType(type);` 所在的 EvalTag 构造辅助：EvalTag 已无 tagType，删除该 setter；辅助方法签名去掉 type 参数（或忽略）。新增一个局部排斥用例（被评价名与评价名解析到同一 tagId）：
```java
    @Test
    @DisplayName("导入：评价角色含被评价角色 → 该行报冲突，整体不入库")
    void importRows_roleConflict_failsRow() {
        // 假设标签「店长」tagId=10 既填在被评价列又填在评价列
        EvalTag t = new EvalTag(); t.setTagId(10L); t.setTagName("店长"); t.setStatus(1);
        when(evalTagMapper.selectAll(null, 1)).thenReturn(List.of(t));
        when(userApi.getUserByEmpIds(anyList()))
                .thenReturn(List.of(userDto("1001")));
        EvalUserTagImportRow row = new EvalUserTagImportRow();
        row.setEmpId("1001"); row.setBeEvalRoleName("店长"); row.setEvalRoleNames("店长"); row.setEvalEnabledText("是");
        EvalUserTagImportResultDTO res = service.importRows(List.of(row));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors()).anySatisfy(e -> assertThat(e.getMessage()).contains("评价角色不能与被评价角色相同"));
    }
```
（`userDto(...)` 辅助按现有测试已有的方式构造 UserDTO；若无则内联 `UserDTO u=new UserDTO(); u.setEmpId("1001"); return u;`。）
现有用例里凡断言"非被评价人类型/非评价人类型"的文案，改为断言"标签不存在"。

- [ ] **Step 2: 运行确认 Red**

Run: `mvn test -pl performance-engine-center -Dtest=EvalUserTagImportServiceTest`
Expected: 红。

- [ ] **Step 3: 改 import service（扁平 name→id + 局部排斥）**

`EvalUserTagImportService.importRows`：
- 第 87-96 行的"按类型拆分两个 map"改为单一扁平 map：
```java
        // 1. 标签名称 → tagId（仅启用，扁平池，不再分类型）
        Map<String, Long> nameToId = new HashMap<>();
        for (EvalTag t : evalTagMapper.selectAll(null, 1)) {
            nameToId.put(t.getTagName(), t.getTagId());
        }
```
- 被评价解析（原 139 行 `beEvalNameToId.get(...)`）改用 `nameToId.get(beNames.get(0))`，错误文案改为 `"被评价角色不存在：" + beNames.get(0)`
- 评价解析（原 159 行 `evalNameToId.get(name)`）改用 `nameToId.get(name)`，错误文案改为 `"评价角色不存在：" + name`
- 在"被评价角色与评价角色不能同时为空"校验（原 186 行）之前或之后，追加局部排斥：
```java
            // 局部排斥：评价角色不能包含被评价角色
            if (beEvalTagId != null && evalTagIds.contains(beEvalTagId)) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "评价角色不能与被评价角色相同"));
                continue;
            }
```
- 类注释/javadoc 去掉"类型"措辞。

- [ ] **Step 4: 运行确认 Green**

Run: `mvn test -pl performance-engine-center -Dtest=EvalUserTagImportServiceTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportService.java \
  performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportServiceTest.java
git commit -m "feat(eval): 导入按扁平标签池解析 + 评价/被评价角色局部排斥"
```

---

## Task 6: 评价规则局部排斥

**Files:**
- Modify: `service/EvalRuleService.java`
- Test: `service/EvalRuleServiceTest.java`

- [ ] **Step 1: 写失败测试**

`EvalRuleServiceTest.java` 新增（沿用文件已有的 mock 风格；`GroupParam` 构造参数顺序：groupType, evalTagId, weight, sortOrder, scoreMode）：
```java
    @Test
    @DisplayName("create 评价人组标签 == 被评价标签 → EVAL_ROLE_CONFLICT")
    void create_groupTagEqualsBeEval_throwsConflict() {
        var groups = List.of(new EvalRuleService.GroupParam(1, 100L, new java.math.BigDecimal("100.00"), 1, 1));
        assertThatThrownBy(() -> service.create("规则A", 100L, groups))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.EVAL_ROLE_CONFLICT));
    }
```
（import `PerfException`、`PerfErrorCode`、`assertThatThrownBy`。该用例应在权重校验之后、唯一性校验之前触发冲突，故权重设 100.00 以越过权重校验。）

- [ ] **Step 2: 运行确认 Red**

Run: `mvn test -pl performance-engine-center -Dtest=EvalRuleServiceTest`
Expected: 红（无冲突校验，会走到 selectByBeEvalTagId 而非抛冲突）。

- [ ] **Step 3: 加规则局部排斥**

`EvalRuleService.java` 新增私有方法并在 create/update 调用：
```java
    /**
     * 校验：任一"按标签选人"组(groupType=1)的评价人标签不得等于被评价人标签（局部排斥）.
     *
     * @param beEvalTagId 被评价人标签ID
     * @param groups      评价人组参数
     * @throws PerfException 冲突时抛 EVAL_ROLE_CONFLICT
     */
    private void validateRoleConflict(Long beEvalTagId, List<GroupParam> groups) {
        if (beEvalTagId == null) return;
        boolean conflict = groups.stream()
                .filter(g -> Integer.valueOf(1).equals(g.getGroupType()))
                .anyMatch(g -> beEvalTagId.equals(g.getEvalTagId()));
        if (conflict) {
            throw new PerfException(PerfErrorCode.EVAL_ROLE_CONFLICT, beEvalTagId);
        }
    }
```
- `create`：在 `validateWeightSum(groups);` 之后追加 `validateRoleConflict(beEvalTagId, groups);`
- `update`：在取出 `rule` 之后、`validateWeightSum` 之后追加 `validateRoleConflict(rule.getBeEvalTagId(), groups);`（update 不改 beEvalTagId，用库中现值）

- [ ] **Step 4: 运行确认 Green**

Run: `mvn test -pl performance-engine-center -Dtest=EvalRuleServiceTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRuleService.java \
  performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRuleServiceTest.java
git commit -m "feat(eval): 评价规则评价人组标签与被评价标签局部排斥"
```

---

## Task 7: 后端全量回归

**Files:** 无（验证任务）

- [ ] **Step 1: 跑全 eval 子域单测**

Run: `mvn test -pl performance-engine-center -Dtest="Eval*Test"`
Expected: 全绿。若 `EvalScoreServiceTest` / `EvalTaskServiceTest` 因 `selectTagIdsByUserIdAndType` 仍调（方法名未变）应自动通过——若红，检查是否误删/改了方法名。

- [ ] **Step 2: 跑模块全量单测确认无回归**

Run: `mvn test -pl performance-engine-center`
Expected: eval 相关全绿；非 eval 失败数应与改动前 baseline 一致（参考 CLAUDE.md：存在历史 baseline 失败，非本次引入）。

- [ ] **Step 3: 无新增文件，跳过 commit**

---

## Task 8: 前端 Tags.vue + eval.js 去类型

**Files:**
- Modify: `xanzc_frontend/src/api/eval.js:14-16`
- Modify: `xanzc_frontend/src/views/eval/Tags.vue`

- [ ] **Step 1: 改 eval.js createTag**

`eval.js` 第 14-16 行：
```javascript
export function createTag(tagName) {
  return call('post', '/admin/eval/tags', { params: { tagName } }, { tagId: Date.now() });
}
```

- [ ] **Step 2: 改 Tags.vue**

- 页头副标题第 5 行 `被评价人标签 · 评价人标签` → `评价标签`
- 删除「标签类型」筛选项（第 15-20 行 el-form-item）
- 删除表格「标签类型」列（第 46-56 行 el-table-column）
- 删除新建弹窗「标签类型」radio（第 109-114 行 el-form-item）
- script：删 `TAG_TYPE_LABEL` 常量（第 161 行）；`filters` 去 `tagType`（第 166 行 `{ tagType: null, keyword: '' }` → `{ keyword: '' }`）；`resetFilters` 去 `filters.tagType = null`；`reload` 的 params 去 `tagType`；`createDlg.form` 去 `tagType`（`{ tagName: '' }`）；`createDlg.rules` 删 tagType 规则；`resetCreateForm` 的 form 去 tagType；`saveCreate` 调用改 `await createTag(createDlg.form.tagName);`

- [ ] **Step 3: 构建验证**

Run: `cd /home/djdev/leid/yiti/xanzc_frontend && npm run build 2>&1 | grep -E "(Tags|eval\.js|ERROR|error)" | head -20`
Expected: 无 error；Tags.vue / eval.js 无报错。

- [ ] **Step 4: Commit**

```bash
git add xanzc_frontend/src/api/eval.js xanzc_frontend/src/views/eval/Tags.vue
git commit -m "feat(eval-fe): 标签管理页去掉标签类型（筛选/列/新建）"
```

---

## Task 9: 前端 UserTags.vue 全量池 + 互斥

**Files:**
- Modify: `xanzc_frontend/src/views/eval/UserTags.vue`

- [ ] **Step 1: 两个选择器改用全量池 + 评价角色互斥**

`UserTags.vue` script：
- `tagsOfType(type)`（第 156-158 行）改为全量启用标签 + 互斥派生。引入 computed：
```javascript
import { ref, reactive, onMounted, computed } from 'vue';
// ...
// 全量启用标签（拍平，无类型）
const activeTags = computed(() => allTags.value.filter(t => t.status === 1));
// 被评价角色选项 = 全量启用标签
const beEvalOptions = computed(() => activeTags.value);
// 评价角色选项 = 全量启用标签，排除已选被评价角色（局部排斥）
const evalOptions = computed(() =>
  activeTags.value.filter(t => t.tagId !== form.beEvalTagId));
```
- 删除 `tagsOfType` 函数。
- template：
  - 被评价人角色 select 的 `<el-option v-for="t in tagsOfType(1)" ...>` → `v-for="t in beEvalOptions"`
  - 评价人角色 select 的 `<el-option v-for="t in tagsOfType(2)" ...>` → `v-for="t in evalOptions"`
- 当被评价角色变更导致已选评价角色与之冲突时，清理：给被评价 select 加 `@change` 钩子：
```javascript
function onBeEvalChange() {
  // 若评价角色里包含了新选的被评价角色，移除以满足互斥
  form.evalTagIds = (form.evalTagIds || []).filter(id => id !== form.beEvalTagId);
}
```
template 被评价 select 加 `@change="onBeEvalChange"`。

- [ ] **Step 2: 构建验证**

Run: `cd /home/djdev/leid/yiti/xanzc_frontend && npm run build 2>&1 | grep -E "(UserTags|ERROR|error)" | head -20`
Expected: 无 error。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/views/eval/UserTags.vue
git commit -m "feat(eval-fe): 人员标签 两角色选择器用全量标签池 + 评价角色互斥被评价"
```

---

## Task 10: 前端 Rules.vue 全量池 + 互斥

**Files:**
- Modify: `xanzc_frontend/src/views/eval/Rules.vue`

- [ ] **Step 1: 选项改全量池 + 评价人组互斥被评价标签**

`Rules.vue` script：
- 第 280 行 `beEvalTagOptions = computed(() => allTags.value.filter((t) => t.tagType === 1))` → `computed(() => allTags.value)`
- 第 283 行 `evalTagOptions = computed(() => allTags.value.filter((t) => t.tagType === 2))` → 排除当前被评价标签：
```javascript
const evalTagOptions = computed(() =>
  allTags.value.filter((t) => t.tagId !== formData.beEvalTagId))
```
- 第 276 行 allTags 注释 `（tagType=1 被评价人标签；tagType=2 评价人标签）` → `（无类型扁平标签池）`

- [ ] **Step 2: 构建验证**

Run: `cd /home/djdev/leid/yiti/xanzc_frontend && npm run build 2>&1 | grep -E "(Rules|ERROR|error)" | head -20`
Expected: 无 error。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/views/eval/Rules.vue
git commit -m "feat(eval-fe): 评价规则 标签下拉用全量池 + 评价人组互斥被评价标签"
```

---

## Task 11: 文档收尾

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（eval 子域条目）

- [ ] **Step 1: 追加交付摘要**

在 `performance-engine-center/CLAUDE.md` 的 eval 子域说明（2026-05-29 那条附近）追加一行：
```
> 2026-05-30 eval 子域：标签去类型化——EVAL_TAG 删 tag_type（扁平池，UK 改 tag_name），EVAL_USER_TAG 加 role_type（1=被评价/2=评价）。被评价/评价区分搬到使用处；saveUserRoles/导入/评价规则三处加"评价角色≠被评价角色"局部排斥（PERF-40063 EVAL_ROLE_CONFLICT，删 EVAL_TAG_TYPE_MISMATCH）。迁移脚本 docs/superpowers/sql/2026-05-30-eval-tag-detype.sql 须在目标库手工执行。前端 Tags/UserTags/Rules 三页去类型 + 互斥下拉。
```

- [ ] **Step 2: Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(eval): 记录标签去类型化交付摘要"
```

---

## 完成后

全部 Task 完成后，调用 finishing-a-development-branch 收尾（当前分支 feat/eval-participate-flag，用户已确认就地叠加）。

**生产部署提醒**：onepl 生产库部署前需同样跑冲突预检 + 迁移脚本；EVAL_USER_SETTING 旧语义与本特性正交。
