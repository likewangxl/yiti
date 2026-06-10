# 评价体系单一角色化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将评价模块从「每人 1 被评价标签 + N 评价标签」改为「每人至多 1 个标签、无 role_type」，方向完全由评价规则承载。

**Architecture:** `EVAL_USER_TAG` 去 `role_type` 列、唯一键改 `(USER_ID)`。后端取标签从「按 role_type 过滤」改为「直取单标签」；任务生成与打分匹配逻辑等价（方向仍由 `rule.beEvalTagId` / `group.evalTagId` 区分）。规则后端不变，前端规则页仅术语中性化。前端人员标签页两组下拉合并为一个单选。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis + MyBatis-Plus + JUnit5/Mockito/AssertJ（后端）；Vue 3 + Element Plus（前端）；MySQL 8.0。

**设计依据：** `docs/superpowers/specs/2026-06-10-eval-single-role-design.md`

---

## 文件结构（改动地图）

**后端 performance-engine-center：**
- `entity/EvalUserTag.java` — 删 `roleType` 字段
- `mapper/EvalUserTagMapper.java` — `selectTagIdsByUserIdAndType` → `selectTagIdByUserId`
- `resources/mapper/performance/EvalUserTagMapper.xml` — 去 `role_type`
- `service/EvalUserTagService.java` — `saveUserRole(userId, tagId)` 单标签覆盖；`assembleRows` 单 tag；删人员侧排斥；删/收敛 `batchBind/batchUnbind`
- `service/EvalTaskService.java` — `createTask` 用单标签
- `service/EvalScoreService.java` — `resolveGroup` 用单标签
- `service/EvalUserTagImportService.java` — 单角色列导入
- `controller/EvalUserTagController.java` — `SaveRolesReq{tagId,evalEnabled}`；删 bind/unbind；模板单列
- `dto/EvalUserRoleRowDTO.java` — `beEvalTag/evalTags` → 单 `tag`
- `dto/EvalUserTagRow.java` — 删 `roleType`
- `dto/EvalUserTagImportRow.java` — 单 `roleName`
- `dto/EvalUserRoleExportRow.java` — 单 `role`

**后端测试：**
- `eval/service/EvalUserRoleServiceTest.java`
- `eval/service/EvalUserTagImportServiceTest.java`
- `eval/service/EvalUserTagExportTest.java`
- `eval/service/EvalTaskServiceTest.java`
- `eval/service/EvalScoreServiceTest.java`

**前端 xanzc_frontend：**
- `src/views/eval/UserTags.vue`
- `src/views/eval/Rules.vue`
- `src/api/eval.js`

**迁移 / 文档：**
- `docs/superpowers/sql/2026-06-10-eval-single-role.sql`（新建）
- `performance-engine-center/CLAUDE.md`（变更日志）

---

## Phase 0：迁移脚本（先产出，最后执行）

### Task 0: 编写迁移 SQL 脚本

**Files:**
- Create: `docs/superpowers/sql/2026-06-10-eval-single-role.sql`

- [ ] **Step 1: 写脚本**

```sql
-- 评价体系单一角色化迁移（2026-06-10）
-- 标签即角色：EVAL_USER_TAG 收敛为每人至多 1 行、删除 ROLE_TYPE 列。
-- 执行前务必先 mysqldump 备份 EVAL_USER_TAG。
--   mysqldump -uroot -p<db> EVAL_USER_TAG > 2026-06-10-eval-user-tag-backup.sql
-- 需在三个库执行：yiti(开发) / onepl(生产基线) / onepl_test_bootstrap(测试)。

-- ⚠ 预检 1：当前每人标签行数分布（收敛前可能 >1）
--   SELECT user_id, COUNT(*) c FROM EVAL_USER_TAG GROUP BY user_id HAVING c>1;

-- 1. 收敛为单标签：每人保留 role_type 升序(1 被评价优先)、id 升序的第一行，删其余
DELETE u FROM EVAL_USER_TAG u
JOIN (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY role_type ASC, id ASC) AS rn
  FROM EVAL_USER_TAG
) ranked ON ranked.id = u.id
WHERE ranked.rn > 1;

-- ⚠ 预检 2：收敛后应返回 0 行，再继续 DDL
--   SELECT user_id, COUNT(*) c FROM EVAL_USER_TAG GROUP BY user_id HAVING c>1;

-- 2. 唯一键改为按人唯一（每人一标签）
ALTER TABLE EVAL_USER_TAG DROP KEY UK_USER_TAG_ROLE;
ALTER TABLE EVAL_USER_TAG ADD UNIQUE KEY UK_USER (USER_ID);

-- 3. 删除角色列
ALTER TABLE EVAL_USER_TAG DROP COLUMN ROLE_TYPE;
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/sql/2026-06-10-eval-single-role.sql
git commit -m "chore(eval): 单一角色化数据迁移脚本"
```

> 脚本**暂不执行**。在 Phase 1-3 后端改完、XML 不再引用 role_type 后，再于 Task 15 统一执行（否则旧 XML select role_type 会撞已删列）。

---

## Phase 1：后端数据层（Entity + Mapper + XML）

### Task 1: EvalUserTag 实体去 roleType

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalUserTag.java`

- [ ] **Step 1: 删除 roleType 字段**

把 `entity/EvalUserTag.java` 整体替换为：

```java
package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 人员标签关联表 EVAL_USER_TAG 贫血实体（单一角色：每人至多一标签，无 role_type）.
 */
@Data
@TableName("EVAL_USER_TAG")
public class EvalUserTag {
    /** 主键. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 人员ID，关联 PT_USER.USER_ID（工号，String）. */
    private String userId;
    /** 标签ID，关联 EVAL_TAG.TAG_ID. */
    private Long tagId;
}
```

- [ ] **Step 2: 不单独编译**（与 Task 2/3 一起在 Task 4 后编译，因为有引用方未改完）。先继续。

### Task 2: Mapper 接口 selectTagIdByUserId

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserTagMapper.java`

- [ ] **Step 1: 替换方法签名**

把 `selectTagIdsByUserIdAndType` 行替换为 `selectTagIdByUserId`：

```java
package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 人员标签关联 Mapper（单一角色）.
 */
@Mapper
public interface EvalUserTagMapper extends BaseMapper<EvalUserTag> {
    List<EvalUserTag> selectByUserId(@Param("userId") String userId);
    List<String> selectUserIdsByTagId(@Param("tagId") Long tagId);

    /** 取人员唯一标签ID；无则返回 null. */
    Long selectTagIdByUserId(@Param("userId") String userId);

    int batchInsert(@Param("list") List<EvalUserTag> list);
    int batchDelete(@Param("userId") String userId, @Param("tagIds") List<Long> tagIds);

    /**
     * 批量查询多个用户的标签（JOIN EVAL_TAG 带出 tagName），供列表聚合用。
     * 调用方须保证 userIds 非空。
     */
    List<EvalUserTagRow> selectUserTagsByUserIds(@Param("userIds") List<String> userIds);
}
```

### Task 3: Mapper XML 去 role_type

**Files:**
- Modify: `performance-engine-center/src/main/resources/mapper/performance/EvalUserTagMapper.xml`

- [ ] **Step 1: 整体替换 XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper">
    <select id="selectByUserId" resultType="com.bank.branch.platform.performance.eval.entity.EvalUserTag">
        SELECT id, user_id, tag_id FROM EVAL_USER_TAG WHERE user_id = #{userId}
    </select>

    <select id="selectUserIdsByTagId" resultType="java.lang.String">
        SELECT user_id FROM EVAL_USER_TAG WHERE tag_id = #{tagId}
    </select>

    <!-- 单一角色：取人员唯一标签ID -->
    <select id="selectTagIdByUserId" resultType="java.lang.Long">
        SELECT tag_id FROM EVAL_USER_TAG WHERE user_id = #{userId} LIMIT 1
    </select>

    <insert id="batchInsert">
        INSERT INTO EVAL_USER_TAG (user_id, tag_id) VALUES
        <foreach collection="list" item="item" separator=",">(#{item.userId}, #{item.tagId})</foreach>
    </insert>

    <delete id="batchDelete">
        DELETE FROM EVAL_USER_TAG WHERE user_id = #{userId} AND tag_id IN
        <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">#{tagId}</foreach>
    </delete>

    <!-- 批量查多人标签：tag_name JOIN EVAL_TAG -->
    <select id="selectUserTagsByUserIds" resultType="com.bank.branch.platform.performance.eval.dto.EvalUserTagRow">
        SELECT ut.user_id AS userId, ut.tag_id AS tagId, t.tag_name AS tagName
        FROM EVAL_USER_TAG ut
        INNER JOIN EVAL_TAG t ON t.tag_id = ut.tag_id
        WHERE ut.user_id IN
        <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
    </select>
</mapper>
```

### Task 4: DTO 去 roleType / 收敛单 tag

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagRow.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleRowDTO.java`

- [ ] **Step 1: EvalUserTagRow 删 roleType**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

/** 人员标签投影行（单一角色）. */
@Data
public class EvalUserTagRow {
    private String userId;
    private Long tagId;
    private String tagName;
}
```

- [ ] **Step 2: EvalUserRoleRowDTO 用单 tag**

读现文件确认字段后，把 `beEvalTag` / `evalTags` 两字段替换为单 `tag`：

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;
import java.util.List;

/** 人员标签列表行 DTO（单一角色）. */
@Data
public class EvalUserRoleRowDTO {
    private String userId;
    private String userName;
    private String orgName;
    private String position;
    private List<String> roleNames;
    /** 该人的评价角色标签（至多一个，null 表示未设置）. */
    private EvalUserTagBriefDTO tag;
    private Integer evalEnabled;
}
```

> `EvalUserTagBriefDTO`（tagId/tagName）不变，复用。

---

## Phase 2：后端服务层（TDD）

### Task 5: EvalUserTagService 单标签覆盖保存

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java`

- [ ] **Step 1: 重写测试（Red）**

`EvalUserRoleServiceTest.java` 全量替换为下列内容（删去旧 beEval+eval 双角色与 batchBind/排斥用例，改为单标签语义）：

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalUserRoleServiceTest {

    @Mock private EvalUserTagMapper evalUserTagMapper;
    @Mock private EvalTagMapper evalTagMapper;
    @Mock private UserApi userApi;
    @Mock private AddressBookApi addressBookApi;
    @Mock private EvalUserSettingMapper evalUserSettingMapper;

    @InjectMocks private EvalUserTagService service;

    @Captor private ArgumentCaptor<List<EvalUserTag>> insertCaptor;

    private EvalTag tag(long id) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        return t;
    }

    private UserDTO user(String empId, String name) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setDisplayName(name);
        return u;
    }

    private EvalUserTagRow tagRow(String uid, long tid, String name) {
        EvalUserTagRow r = new EvalUserTagRow();
        r.setUserId(uid); r.setTagId(tid); r.setTagName(name);
        return r;
    }

    @Test
    @DisplayName("saveUserRole 覆盖：先删该用户旧标签，再插入单标签")
    void saveUserRole_overwrite() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId("1001"); old1.setTagId(7L);
        EvalUserTag old2 = new EvalUserTag(); old2.setUserId("1001"); old2.setTagId(8L);
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of(old1, old2));
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L));

        service.saveUserRole("1001", 1L);

        verify(evalUserTagMapper).batchDelete(eq("1001"), eq(List.of(7L, 8L)));
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(1L);
    }

    @Test
    @DisplayName("saveUserRole tagId=null 表示清空：仅删除不插入")
    void saveUserRole_clear() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId("1001"); old1.setTagId(7L);
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of(old1));

        service.saveUserRole("1001", null);

        verify(evalUserTagMapper).batchDelete(eq("1001"), eq(List.of(7L)));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRole tagId 指向不存在标签 → PERF-40058")
    void saveUserRole_tagNotFound_throws() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveUserRole("1001", 99L))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_NOT_FOUND));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoleWithSetting：evalEnabled=1(参与) → 写标签 + 移出排除名单")
    void saveUserRoleWithSetting_participate() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L));

        service.saveUserRoleWithSetting("1001", 2L, 1);

        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
        verify(evalUserSettingMapper).clearExcluded("1001");
        verify(evalUserSettingMapper, never()).markExcluded(anyString());
    }

    @Test
    @DisplayName("saveUserRoleWithSetting：evalEnabled=0(不参与) → 写入排除名单")
    void saveUserRoleWithSetting_notParticipate() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L));

        service.saveUserRoleWithSetting("1001", 2L, 0);

        verify(evalUserSettingMapper).markExcluded("1001");
        verify(evalUserSettingMapper, never()).clearExcluded(anyString());
    }

    @Test
    @DisplayName("saveUserRoleWithSetting：evalEnabled=null 兜底参与 → 移出排除名单")
    void saveUserRoleWithSetting_nullDefaultsParticipate() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());

        service.saveUserRoleWithSetting("1001", null, null);

        verify(evalUserSettingMapper).clearExcluded("1001");
        verify(evalUserSettingMapper, never()).markExcluded(anyString());
    }

    @Test
    @DisplayName("pageUserRoles 拼装：单标签、部门/岗位/角色到位")
    void pageUserRoles_assembles() {
        when(userApi.pageUsers("张", 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("1001", "张三"))));
        when(addressBookApi.getEmployees(List.of("1001")))
                .thenReturn(List.of(EmployeeDTO.builder().empId("1001").orgName("某支行").position("行长").build()));
        RoleSimpleDTO role = new RoleSimpleDTO();
        role.setRoleChName("管理员");
        when(userApi.getRolesByUserIds(List.of("1001"))).thenReturn(Map.of("1001", List.of(role)));
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of("1001")))
                .thenReturn(List.of(tagRow("1001", 1L, "支行行长")));
        when(evalUserSettingMapper.selectExcludedUserIdsIn(List.of("1001"))).thenReturn(List.of());

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles("张", "all", 1, 20);

        EvalUserRoleRowDTO row = r.getRecords().get(0);
        assertThat(row.getUserId()).isEqualTo("1001");
        assertThat(row.getUserName()).isEqualTo("张三");
        assertThat(row.getOrgName()).isEqualTo("某支行");
        assertThat(row.getRoleNames()).containsExactly("管理员");
        assertThat(row.getTag().getTagName()).isEqualTo("支行行长");
        assertThat(row.getEvalEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("pageUserRoles 无标签：tag 为 null 不报错")
    void pageUserRoles_noTag() {
        when(userApi.pageUsers(null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("U_ABC", "李四"))));
        when(addressBookApi.getEmployees(List.of("U_ABC"))).thenReturn(List.of());
        when(userApi.getRolesByUserIds(List.of("U_ABC"))).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of("U_ABC"))).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of());

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "all", 1, 20);

        assertThat(r.getRecords().get(0).getTag()).isNull();
    }

    @Test
    @DisplayName("pageUserRoles 默认(参与)：剔除排除名单内的人")
    void pageUserRoles_participateDefault() {
        when(userApi.pageUsers(null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 2L, List.of(user("1001", "张三"), user("1002", "李四"))));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(List.of("1001", "1002"))).thenReturn(List.of("1002"));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "1", 1, 20);

        assertThat(r.getRecords()).extracting(EvalUserRoleRowDTO::getUserId).containsExactly("1001");
    }

    @Test
    @DisplayName("pageUserRoles 不参与：名单驱动，total 准确")
    void pageUserRoles_excludedDriven() {
        when(evalUserSettingMapper.selectExcludedUserIds()).thenReturn(List.of("1001", "1002"));
        when(userApi.getUserByEmpIds(List.of("1001", "1002")))
                .thenReturn(List.of(user("1001", "张三"), user("1002", "李四")));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of("1001", "1002"));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "0", 1, 20);

        assertThat(r.getTotal()).isEqualTo(2L);
        assertThat(r.getRecords()).allSatisfy(row -> assertThat(row.getEvalEnabled()).isEqualTo(0));
        verify(userApi, never()).pageUsers(any(), anyInt(), anyInt());
    }
}
```

- [ ] **Step 2: 运行测试确认编译失败（Red）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest -q`
Expected: 编译失败（`saveUserRole` / `saveUserRoleWithSetting` / `getTag` 不存在）。

- [ ] **Step 3: 改 EvalUserTagService（Green）**

`service/EvalUserTagService.java` 改动点：

(a) 把 `saveUserRoles(String, Long, List<Long>)` 替换为单标签版本：

```java
    /**
     * 覆盖式保存人员评价角色（单标签）：删除该用户旧关联，再写入至多一个标签。
     *
     * @param userId 人员工号
     * @param tagId  标签ID（null 表示清空角色）
     * @throws PerfException EVAL_RULE_NOT_FOUND（标签不存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRole(String userId, Long tagId) {
        // 1. 标签存在性校验
        if (tagId != null && evalTagMapper.selectById(tagId) == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tagId);
        }
        // 2. 覆盖：删除该用户全部旧关联
        List<EvalUserTag> existing = evalUserTagMapper.selectByUserId(userId);
        if (!existing.isEmpty()) {
            List<Long> oldTagIds = existing.stream().map(EvalUserTag::getTagId).distinct().collect(Collectors.toList());
            evalUserTagMapper.batchDelete(userId, oldTagIds);
        }
        // 3. 写入新标签（至多一个）
        if (tagId != null) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(tagId);
            evalUserTagMapper.batchInsert(List.of(u));
        }
        log.info("[EvalUserTagService.saveUserRole] userId={} tagId={}", userId, tagId);
    }
```

(b) 把 `saveUserRolesWithSetting(String, Long, List<Long>, Integer)` 替换为：

```java
    /**
     * 覆盖式保存人员评价角色（单标签）+ 写"是否参与评价"标记（同一事务，原子）.
     *
     * @param userId      人员工号
     * @param tagId       标签ID（null 表示清空）
     * @param evalEnabled 是否参与评价：1=是 0=否；null 兜底为参与
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoleWithSetting(String userId, Long tagId, Integer evalEnabled) {
        saveUserRole(userId, tagId);
        boolean excluded = Integer.valueOf(0).equals(evalEnabled);
        if (excluded) {
            evalUserSettingMapper.markExcluded(userId);
        } else {
            evalUserSettingMapper.clearExcluded(userId);
        }
        log.info("[EvalUserTagService.saveUserRoleWithSetting] userId={} excluded={}", userId, excluded);
    }
```

(c) 删除 `batchBind` 与 `batchUnbind` 方法（连同其上方注释）——旧增量绑定带 roleType，单一角色后无对侧概念，且端点将在 Task 9 移除。同时删除 `getUserIdsByTagId` 之外不再使用的 import（保留仍用的）。

(d) `assembleRows` 内标签装配段替换（把 beEval+eval 双列改为单 tag）：

```java
            List<EvalUserTagRow> tagRows = tagMap.getOrDefault(u.getEmpId(), List.of());
            EvalUserTagBriefDTO tag = tagRows.stream()
                    .findFirst()
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .orElse(null);
            row.setTag(tag);
```

> 删除原 `beEval` / `evalTags` 两段 stream 与 `row.setBeEvalTag/​setEvalTags` 调用。

- [ ] **Step 4: 运行测试确认通过（Green）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest -q`
Expected: PASS（注意此时整模块尚未编译通过，单测试类靠 test 编译可能仍受其他引用方阻塞；若编译被 Controller/Import 阻塞，先跳到 Task 6-9 改完再统一跑，见 Task 10）。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalUserTag.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserTagMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/EvalUserTagMapper.xml \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/
git commit -m "feat(eval): 人员标签单一角色化（数据层+保存服务）"
```

### Task 6: EvalTaskService 用单标签取数

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalTaskService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalTaskServiceTest.java`

- [ ] **Step 1: 新增 createTask 角色取数测试（Red）**

在 `EvalTaskServiceTest.java` 增加（import 已有 `when/verify/eq`，再补 `org.mockito.Mockito.never` 已含通配）：

```java
    @Test
    @DisplayName("createTask：用单标签匹配规则生成 target")
    void createTask_singleTag_matchesRule() {
        java.time.LocalDateTime future = java.time.LocalDateTime.now().plusDays(1);
        com.bank.branch.platform.performance.eval.entity.EvalRule rule =
                new com.bank.branch.platform.performance.eval.entity.EvalRule();
        rule.setRuleId(200L);
        rule.setBeEvalTagId(5L);
        rule.setStatus(1);
        when(evalUserTagMapper.selectTagIdByUserId("101")).thenReturn(5L);
        when(evalRuleMapper.selectByBeEvalTagId(5L)).thenReturn(rule);

        evalTaskService.createTask("任务A", future, java.util.List.of("101"), "1");

        verify(evalTaskTargetMapper).batchInsert(org.mockito.ArgumentMatchers.argThat(
                list -> list.size() == 1 && list.get(0).getRuleId().equals(200L)
                        && list.get(0).getBeEvalUserId().equals("101")));
    }

    @Test
    @DisplayName("createTask：无标签的人跳过，不生成 target")
    void createTask_noTag_skips() {
        java.time.LocalDateTime future = java.time.LocalDateTime.now().plusDays(1);
        when(evalUserTagMapper.selectTagIdByUserId("101")).thenReturn(null);

        evalTaskService.createTask("任务A", future, java.util.List.of("101"), "1");

        verify(evalTaskTargetMapper, org.mockito.Mockito.never()).batchInsert(org.mockito.ArgumentMatchers.anyList());
    }
```

- [ ] **Step 2: 运行确认失败（Red）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalTaskServiceTest -q`
Expected: 编译失败（`selectTagIdByUserId` mock 与现 `createTask` 仍调 `selectTagIdsByUserIdAndType`）。

- [ ] **Step 3: 改 createTask（Green）**

`EvalTaskService.createTask` 中第 3 步 for 循环体替换为：

```java
        // 3. 为每个被评价人生成 target（用单标签匹配规则）
        List<EvalTaskTarget> targets = new ArrayList<>();
        for (String userId : beEvalUserIds) {
            // 取被评价人唯一标签
            Long tagId = evalUserTagMapper.selectTagIdByUserId(userId);
            Long ruleId = null;
            if (tagId != null) {
                var rule = evalRuleMapper.selectByBeEvalTagId(tagId);
                if (rule != null && rule.getStatus() == 1) {
                    ruleId = rule.getRuleId();
                }
            }
            // 未匹配到规则时跳过（允许部分被评价人无规则）
            if (ruleId == null) {
                log.warn("[EvalTaskService.createTask] 被评价人 userId={} 无匹配启用规则，跳过生成 target", userId);
                continue;
            }
            EvalTaskTarget target = new EvalTaskTarget();
            target.setTaskId(task.getTaskId());
            target.setBeEvalUserId(userId);
            target.setRuleId(ruleId);
            targets.add(target);
        }
```

- [ ] **Step 4: 运行确认通过（Green）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalTaskServiceTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalTaskService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalTaskServiceTest.java
git commit -m "feat(eval): 任务生成改用单标签匹配规则"
```

### Task 7: EvalScoreService 用单标签匹配评价人组

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalScoreService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalScoreServiceTest.java`

- [ ] **Step 1: 读现有测试找到 stub 点（Red）**

打开 `EvalScoreServiceTest.java`，把所有
`evalUserTagMapper.selectTagIdsByUserIdAndType(<id>, 2)` 的 stub 改为
`evalUserTagMapper.selectTagIdByUserId(<id>)`，返回值由 `List.of(tagX)` 改为单值 `tagX`
（若原返回空列表表示无评价标签，则改为返回 `null`）。

> 例：`when(evalUserTagMapper.selectTagIdsByUserIdAndType("u2", 2)).thenReturn(List.of(10L));`
> → `when(evalUserTagMapper.selectTagIdByUserId("u2")).thenReturn(10L);`
> 空列表场景：`...thenReturn(List.of());` → `...thenReturn(null);`

- [ ] **Step 2: 运行确认失败（Red）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalScoreServiceTest -q`
Expected: 编译失败或行为失败。

- [ ] **Step 3: 改 resolveGroup（Green）**

`EvalScoreService.resolveGroup` 中取标签段替换：

```java
        Long evalUserTagId = evalUserTagMapper.selectTagIdByUserId(evalUserId);

        for (EvalRuleGroup group : groups) {
            if (group.getGroupType() == 1) {
                if (group.getEvalTagId() != null && group.getEvalTagId().equals(evalUserTagId)) {
                    return group;
                }
            } else if (group.getGroupType() == 2) {
                if (isSameOrg(evalUserId, beEvalUserId)) {
                    return group;
                }
            }
        }
        return null;
```

> 同步更新方法 javadoc：`查询评价人持有的评价人标签（role_type=2）` → `查询评价人持有的唯一标签`。

- [ ] **Step 4: 运行确认通过（Green）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalScoreServiceTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalScoreService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalScoreServiceTest.java
git commit -m "feat(eval): 打分评价人匹配改用单标签"
```

---

## Phase 3：后端导入 + Controller

### Task 8: 导入服务单角色列

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagImportRow.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportServiceTest.java`

- [ ] **Step 1: 改导入行 DTO**

先读现文件确认 `@ExcelProperty` 注解写法，然后把 `beEvalRoleName`+`evalRoleNames` 两列改为单 `roleName`：

```java
package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员评价角色导入行（单一角色）. */
@Data
public class EvalUserTagImportRow {
    @ExcelProperty("工号")
    private String empId;
    @ExcelProperty("角色")
    private String roleName;
    @ExcelProperty("是否参与评价")
    private String evalEnabledText;
}
```

> 注：列顺序/列名以现文件实际 `@ExcelProperty` 为准微调，保持「工号 / 角色 / 是否参与评价」三列。

- [ ] **Step 2: 改导入测试（Red）**

打开 `EvalUserTagImportServiceTest.java`：
- 把构造导入行的 `setBeEvalRoleName(...)` / `setEvalRoleNames(...)` 改为 `setRoleName(...)`；
- 删除「评价角色不能与被评价角色相同」局部排斥用例；
- 删除「被评价角色只能填一个」用例（单列后无意义）；
- 把「被评价角色与评价角色不能同时为空」用例改为「角色不能为空」；
- 入库断言：`saveUserRoleWithSetting(empId, tagId, enabled)` 单标签签名。

（按现文件用例逐条对应调整，保持其余工号校验/重复/不存在用例不变。）

- [ ] **Step 3: 运行确认失败（Red）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalUserTagImportServiceTest -q`
Expected: 编译/行为失败。

- [ ] **Step 4: 改 importRows（Green）**

`EvalUserTagImportService.importRows` 逐行体替换核心段（删被评价/评价双列解析、删局部排斥，改单列）：

```java
            // 角色：必填，从扁平池解析单个标签
            String roleRaw = r.getRoleName() == null ? "" : r.getRoleName().trim();
            if (roleRaw.isEmpty()) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "角色不能为空"));
                continue;
            }
            List<String> roleNames = splitNames(roleRaw);
            if (roleNames.size() > 1) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "角色只能填一个"));
                continue;
            }
            Long tagId = nameToId.get(roleNames.get(0));
            if (tagId == null) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "角色不存在：" + roleNames.get(0)));
                continue;
            }

            // 是否参与评价：必填，仅"是"/"否"
            String enabledRaw = r.getEvalEnabledText() == null ? "" : r.getEvalEnabledText().trim();
            int evalEnabled;
            if ("是".equals(enabledRaw)) {
                evalEnabled = 1;
            } else if ("否".equals(enabledRaw)) {
                evalEnabled = 0;
            } else {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "是否参与评价只能填\"是\"或\"否\""));
                continue;
            }

            parsed.add(new ParsedRow(empId, tagId, evalEnabled));
```

`ParsedRow` 内部类与入库循环改单标签：

```java
        // 5. 全部通过 → 逐行覆盖式入库
        for (ParsedRow p : parsed) {
            evalUserTagService.saveUserRoleWithSetting(p.empId, p.tagId, p.evalEnabled);
        }
```

```java
    /** 校验通过的一行解析结果（单标签）。 */
    private static class ParsedRow {
        final String empId;
        final Long tagId;
        final int evalEnabled;

        ParsedRow(String empId, Long tagId, int evalEnabled) {
            this.empId = empId;
            this.tagId = tagId;
            this.evalEnabled = evalEnabled;
        }
    }
```

> 同步更新类 javadoc 去掉「被评价/评价局部排斥」描述。

- [ ] **Step 5: 运行确认通过（Green）**

Run: `mvn -pl performance-engine-center test -Dtest=EvalUserTagImportServiceTest -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportService.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagImportRow.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagImportServiceTest.java
git commit -m "feat(eval): 人员角色导入改单角色列"
```

### Task 9: Controller + 导出行单列

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalUserTagController.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleExportRow.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagExportTest.java`

- [ ] **Step 1: 改导出行 DTO**

先读现文件确认 `@ExcelProperty`，把 `beEvalRole`+`evalRoles` 改为单 `role`：

```java
package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员标签导出行（单一角色）. */
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
    @ExcelProperty("系统角色")
    private String roleNames;
    @ExcelProperty("评价角色")
    private String role;
    @ExcelProperty("是否参与评价")
    private String evalEnabled;
}
```

> 列名/顺序以现文件实际为准微调；把原「被评价角色」「评价角色」两列并为单「评价角色」列。

- [ ] **Step 2: 改 Controller（Red→Green 一步，编译驱动）**

`EvalUserTagController` 改动：

(a) `SaveRolesReq` 内部类替换：

```java
    @Data
    public static class SaveRolesReq {
        /** 标签ID（null 表示清空角色）. */
        private Long tagId;
        /** 是否参与评价：1=是 0=否；null 兜底为参与. */
        private Integer evalEnabled;
    }
```

(b) `saveRoles` 方法体：

```java
    @PutMapping("/{userId}/roles")
    @Operation(summary = "覆盖式保存人员评价角色（单标签）及参与评价开关")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> saveRoles(@PathVariable("userId") String userId,
                                           @Validated @RequestBody SaveRolesReq req) {
        log.info("[EvalUserTagController.saveRoles] userId={}, tagId={}, evalEnabled={}",
                userId, req.getTagId(), req.getEvalEnabled());
        evalUserTagService.saveUserRoleWithSetting(userId, req.getTagId(), req.getEvalEnabled());
        return ResponseWrapper.success();
    }
```

(c) 删除 `bind` / `unbind` 两个端点方法（`@PostMapping` / `@DeleteMapping`）与 `BindReq` 内部类（dead code，前端未引用）。同步删 `list(@RequestParam userId)` 若仅服务旧绑定页——**保留**（它返回 `getByUserId`，无害；如无引用可一并删，默认保留以缩小风险）。

(d) `importTemplate` 的 sample 改单列：

```java
        EvalUserTagImportRow sample = new EvalUserTagImportRow();
        sample.setEmpId("100001");
        sample.setRoleName("支行行长");
        sample.setEvalEnabledText("是");
```

(e) `export` 方法的装配段改单列：

```java
        for (EvalUserRoleRowDTO r : rows) {
            EvalUserRoleExportRow e = new EvalUserRoleExportRow();
            e.setUserName(r.getUserName());
            e.setEmpId(r.getUserId());
            e.setOrgName(r.getOrgName());
            e.setPosition(r.getPosition());
            e.setRoleNames(r.getRoleNames() == null ? "" : String.join("，", r.getRoleNames()));
            e.setRole(r.getTag() == null ? "" : r.getTag().getTagName());
            e.setEvalEnabled(Integer.valueOf(1).equals(r.getEvalEnabled()) ? "是" : "否");
            out.add(e);
        }
```

- [ ] **Step 3: 改导出测试**

打开 `EvalUserTagExportTest.java`，把断言中 `getBeEvalRole()/getEvalRoles()` 改为 `getRole()`，
构造数据用 `setTag(...)`（单 brief）替代 `setBeEvalTag/setEvalTags`。

- [ ] **Step 4: 全模块编译 + 相关测试**

见 Task 10。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalUserTagController.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleExportRow.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserTagExportTest.java
git commit -m "feat(eval): Controller/导出单角色列 + 删除旧 bind/unbind 端点"
```

### Task 10: 全模块编译与 eval 测试回归

**Files:** 无（验证步骤）

- [ ] **Step 1: 编译整模块**

Run: `mvn -pl performance-engine-center -am clean install -DskipTests -q`
Expected: BUILD SUCCESS（若失败，按报错定位遗漏的 `beEvalTag/evalTags/roleType/selectTagIdsByUserIdAndType` 引用）。

- [ ] **Step 2: 跑 eval 全部单测**

Run: `mvn -pl performance-engine-center test -Dtest="Eval*Test" -q`
Expected: PASS（EvalRuleServiceTest 自评排斥用例应仍绿）。

- [ ] **Step 3: 若有失败，按 systematic-debugging 修复后再次运行直至全绿。**

---

## Phase 4：前端

### Task 11: api/eval.js 单标签保存

**Files:**
- Modify: `xanzc_frontend/src/api/eval.js`

- [ ] **Step 1: 改 saveUserRoles 签名**

把 `saveUserRoles` 函数替换为：

```javascript
// 覆盖式保存人员评价角色（单标签）+ 是否参与评价(1/0)
export function saveUserRoles(userId, tagId, evalEnabled) {
  return call('put', `/admin/eval/user-tags/${userId}/roles`, { data: { tagId, evalEnabled } }, { ok: true });
}
```

- [ ] **Step 2: 删除 bindUserTags / unbindUserTags**（后端端点已移除）。保留 `listUserTags`（若别处引用）。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/api/eval.js
git commit -m "feat(eval-fe): saveUserRoles 改单标签签名"
```

### Task 12: UserTags.vue 合并为单角色

**Files:**
- Modify: `xanzc_frontend/src/views/eval/UserTags.vue`

- [ ] **Step 1: 模板改造**

(a) 页头描述（行 5）改为：

```html
      <span class="desc">维护人员评价角色（每人单选一个）</span>
```

(b) 删除表格「被评价人角色」「评价人角色」两列（行 42-53），替换为单列：

```html
        <el-table-column label="评价角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.tag" type="success" effect="plain" size="small">{{ row.tag.tagName }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
```

(c) 导入提示文案（行 81）改为：

```html
          <span class="muted">模板列：工号 / 角色 / 是否参与评价。全部校验通过才会导入。</span>
```

(d) 编辑弹窗表单（行 122-139）的「被评价人角色」「评价人角色」两个 form-item 替换为单个：

```html
        <el-form-item label="评价角色">
          <el-select v-model="form.tagId" clearable filterable placeholder="单选，可清空" style="width: 100%">
            <el-option v-for="t in activeTags" :key="t.tagId" :value="t.tagId" :label="t.tagName" />
          </el-select>
        </el-form-item>
```

- [ ] **Step 2: 脚本改造**

(a) `form` 初值（行 271）改为：

```javascript
const form = reactive({ tagId: null, evalEnabled: 0 });
```

(b) 删除 `beEvalOptions` / `evalOptions` / `onBeEvalChange`（行 276-283）。`activeTags` 保留。

(c) `openEdit`（行 285-291）改为：

```javascript
function openEdit(row) {
  editing.value = row;
  form.tagId = row.tag ? row.tag.tagId : null;
  form.evalEnabled = (row.evalEnabled === 1) ? 1 : 0;
  editVisible.value = true;
}
```

(d) `onDialogClosed`（行 293-298）改为：

```javascript
function onDialogClosed() {
  editing.value = null;
  form.tagId = null;
  form.evalEnabled = 0;
}
```

(e) `handleSave`（行 304）的保存调用改为：

```javascript
    await saveUserRoles(editing.value.userId, form.tagId ?? null, form.evalEnabled);
```

- [ ] **Step 3: 前端构建验证**

Run: `cd xanzc_frontend && npx vite build` （或 `npm run build`）
Expected: 构建成功，无 `beEvalTag/evalTags/beEvalOptions` 未定义引用报错。

- [ ] **Step 4: Commit**

```bash
git add xanzc_frontend/src/views/eval/UserTags.vue
git commit -m "feat(eval-fe): 人员标签页合并为单角色单选"
```

### Task 13: Rules.vue 术语中性化

**Files:**
- Modify: `xanzc_frontend/src/views/eval/Rules.vue`

- [ ] **Step 1: 文案替换（仅展示层，不动 beEvalTagId 字段名/接口）**

把页面中所有「被评价人标签」展示文案改为「评价对象标签」：
- 页头描述（行 6）`为每类被评价人配置评价人组及权重` → `为每类评价对象配置评价人组及权重`
- 表格列头（行 38）`被评价人标签` → `评价对象标签`
- 新建/编辑表单 label（行 93）`被评价人标签` → `评价对象标签`
- 编辑禁用提示（行 108）`编辑时不允许更改被评价人标签` → `编辑时不允许更改评价对象标签`
- 详情项 label（行 218）`被评价人标签` → `评价对象标签`

> `formData.beEvalTagId`、`beEvalTagOptions`、`createRule({beEvalTagId})` 等**字段名/接口保持不变**。
> `evalTagOptions` 排除 `formData.beEvalTagId`（自评排斥）保留不动。

- [ ] **Step 2: 构建验证**

Run: `cd xanzc_frontend && npx vite build`
Expected: 构建成功。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/views/eval/Rules.vue
git commit -m "feat(eval-fe): 评价规则页术语中性化（评价对象标签）"
```

---

## Phase 5：迁移执行 + 文档 + 端到端验证

### Task 14: 启动验证（迁移前）

**Files:** 无

- [ ] **Step 1: stale jar 防护 + 全量 install**

Run: `mvn clean install -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 说明**：此时**尚未执行 DB 迁移**，`EVAL_USER_TAG` 仍有 `role_type` 列但 XML 不再 select 它——读路径兼容（多余列不影响）；但 `batchInsert` 不再写 `role_type`，若该列为 `NOT NULL` 无默认值会插入失败。**因此迁移必须在重启后端写入前完成**（见 Task 15）。先不重启后端，直接执行迁移。

### Task 15: 执行数据迁移（三库）

**Files:** 使用 Task 0 的脚本

- [ ] **Step 1: 备份**

```bash
mysqldump -uroot -pdjdev yiti EVAL_USER_TAG > /tmp/2026-06-10-eval-user-tag-yiti-backup.sql
```

（onepl / onepl_test_bootstrap 同理，按各库口令）

- [ ] **Step 2: 预检每库重复行**

对每个库执行：
`SELECT user_id, COUNT(*) c FROM EVAL_USER_TAG GROUP BY user_id HAVING c>1;`

- [ ] **Step 3: 执行迁移脚本（开发库 yiti）**

```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-06-10-eval-single-role.sql
```

- [ ] **Step 4: 执行迁移脚本（onepl_test_bootstrap 测试库）**

```bash
mysql -uroot -p<onepl_pwd> onepl_test_bootstrap < docs/superpowers/sql/2026-06-10-eval-single-role.sql
```

- [ ] **Step 5: 执行迁移脚本（onepl 生产基线）**

> 生产库执行需 DBA 授权与备份确认。开发环境若无 onepl 实例可跳过，登记到运维 Runbook 待生产部署时执行。

- [ ] **Step 6: 验证表结构**

对 yiti / onepl_test_bootstrap：
`SHOW COLUMNS FROM EVAL_USER_TAG;`（确认无 ROLE_TYPE 列）
`SHOW INDEX FROM EVAL_USER_TAG;`（确认 UK_USER 唯一键存在）

### Task 16: 端到端启动验证

**Files:** 无

- [ ] **Step 1: 启动后端**

Run（后台）: `mvn -pl bootstrap spring-boot:run`
等待 `Started BranchPlatformApplication`。

- [ ] **Step 2: 探活**

Run: `curl -s --noproxy '*' -o /dev/null -w "%{http_code}\n" http://localhost:18081/doc.html`
Expected: 200

- [ ] **Step 3: 人员标签分页接口冒烟**（需登录态 cookie 或在前端页面验证）

在前端 UserTags 页面：查询 → 列表「评价角色」单列显示 → 编辑单选保存成功 → 重新查询回显正确。

- [ ] **Step 4: 评价规则页**：列表/详情/编辑「评价对象标签」文案正确，规则保存正常，自评排斥仍生效（评价人组下拉不出现目标标签）。

### Task 17: 文档同步

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`

- [ ] **Step 1: 在模块概述 eval 子域处追加变更日志条目**

在现有「2026-05-30 eval 子域：标签去类型化」条目下方追加：

```markdown
> 2026-06-10 eval 子域：单一角色化——`EVAL_USER_TAG` 删 `role_type`、唯一键改 `UK_USER(USER_ID)`（每人至多一标签）。
> 「被评价/评价」方向不再存于人员侧，完全由规则承载（标签出现在 `EVAL_RULE.be_eval_tag_id`=被评价，
> 出现在 `EVAL_RULE_GROUP.eval_tag_id`=评价人）。`saveUserRoles`→`saveUserRole(userId,tagId)` 单标签覆盖；
> 删人员侧局部排斥（规则自评排斥 `EVAL_ROLE_CONFLICT` 保留）；任务生成/打分匹配改 `selectTagIdByUserId`；
> 导入/导出/模板收敛单角色列；删旧 bind/unbind 端点。前端 UserTags 合并单选、Rules 术语中性化「评价对象标签」。
> 迁移脚本 `docs/superpowers/sql/2026-06-10-eval-single-role.sql`（三库手工执行，优先保留被评价标签收敛）。
> spec/plan：`docs/superpowers/specs/2026-06-10-eval-single-role-design.md` / `docs/superpowers/plans/2026-06-10-eval-single-role-impl.md`。
```

- [ ] **Step 2: Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(eval): 单一角色化变更日志"
```

---

## 自检对照（spec 覆盖）

| spec 要求 | 对应 Task |
|---|---|
| EVAL_USER_TAG 去 role_type + 唯一键改 | Task 0/1/3/15 |
| 迁移收敛优先保留被评价标签 | Task 0 |
| Mapper selectTagIdByUserId | Task 2/3 |
| saveUserRole 单标签 + 删人员侧排斥 | Task 5 |
| 任务生成单标签 | Task 6 |
| 打分匹配单标签 | Task 7 |
| 导入单角色列 + 删排斥 | Task 8 |
| Controller SaveRolesReq{tagId} + 删 bind/unbind + 模板 | Task 9 |
| 导出单角色列 | Task 9 |
| DTO 收敛单 tag | Task 4/9 |
| EVAL_ROLE_CONFLICT 保留（规则自评） | Task 10 验证 EvalRuleServiceTest |
| UserTags 合并单选 | Task 12 |
| Rules 术语中性化 | Task 13 |
| api/eval.js 单标签 | Task 11 |
| Tasks/MyTasks 不改 | （无 Task，明确不动） |
| 三库迁移 + 文档 | Task 15/17 |
```
