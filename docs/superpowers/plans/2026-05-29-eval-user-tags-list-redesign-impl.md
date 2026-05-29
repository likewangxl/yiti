# 人员标签页面列表化改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把"人员标签"页从"搜一个人→绑/解绑标签"的主从页，改造成"全量人员分页列表 + 行内编辑"的表格页，被评价人角色单选、评价人角色多选。

**Architecture:** 后端在 performance-engine-center 的 eval 子域新增 1 个聚合分页接口 + 1 个覆盖式保存接口，编排调用 auth `UserApi`（分页用户 + 批量角色）和 portal `AddressBookApi`（部门/岗位）；auth `UserApi` 补 2 个方法。前端重写 `UserTags.vue` 为 el-table + 编辑弹窗。无表结构变更，被评价人单选由 Service 删-插覆盖强制。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis(-Plus) + JUnit5/Mockito（surefire 纯单测）+ Vue3 + Element Plus。

---

## 关键事实（实现前必读）

- **String/Long 边界**：`EVAL_USER_TAG.USER_ID` 是 `BIGINT`（实体 `EvalUserTag.userId` 是 `Long`），而 `PT_USER.USER_ID` 是 `varchar(50)`（工号字符串，`UserDTO.empId` 是 `String`）。聚合查询时，只有"数值型工号"才能匹配到 `EVAL_USER_TAG`；非数值工号视为无评价标签（不报错）。保存接口路径变量 `{userId}` 用 `Long`（与既有 `BindReq.userId` 一致）。
- **PT_USER.USER_ID == ADDRBOOK_EMPLOYEE.emp_id**：工号关联，`AddressBookApi.getEmployees(empIds)` 批量取 `orgName/position`，上限 200，本接口 pageSize≤100 安全。
- **PageResult**（`com.bank.branch.platform.common.web.PageResult`）：`PageResult.of(pageNo,pageSize,total,records)`；getter `getRecords()` / `getTotal()`（Lombok `@Data`）。
- **PerfException**：`throw new PerfException(PerfErrorCode.XXX, args...)`；`getErrorCode()` 返回枚举，单测用 `assertThat(ex.getErrorCode()).isEqualTo(...)`。
- **PT_RESOURCE 必须登记**：`AuthorizationInterceptor` 未匹配到资源返回 403（AUTH-40302）。新 URL 必须加 `PT_RESOURCE` 行 + 角色绑定，否则上线即 403。
- **测试分工**：纯 Mockito 单测命名 `*Test.java`（surefire，`mvn test` 触发）。本计划后端测试全部走纯 Mockito 单测，与既有 `EvalTagServiceTest` / `UserFacade` 同风格，不写 `*IT.java`。
- **子代理派遣**：如分派 subagent，model 必须 ≥ sonnet（禁用 haiku）。

## 文件结构

**auth-permission-center（新增/修改）**
- Modify: `auth/api/UserApi.java`（加 2 方法签名）
- Modify: `auth/facade/UserFacade.java`（实现 2 方法）
- Modify: `auth/mapper/UserMapper.java`（加 2 方法）
- Modify: `auth/src/main/resources/mapper/auth/UserMapper.xml`（加 2 段 SQL）
- Create: `auth/src/test/java/com/bank/branch/platform/auth/facade/UserFacadePageRolesTest.java`

**performance-engine-center（新增/修改）**
- Create: `performance/eval/dto/EvalUserRoleRowDTO.java`
- Create: `performance/eval/dto/EvalUserTagBriefDTO.java`
- Create: `performance/eval/dto/EvalUserTagRow.java`（mapper 投影）
- Modify: `performance/enums/PerfErrorCode.java`（加 1 个错误码）
- Modify: `performance/eval/mapper/EvalUserTagMapper.java`（加 1 方法）
- Modify: `performance/src/main/resources/mapper/performance/EvalUserTagMapper.xml`（加 1 段 SQL）
- Modify: `performance/eval/service/EvalUserTagService.java`（扩依赖 + 2 方法）
- Modify: `performance/eval/controller/EvalUserTagController.java`（加 2 端点 + 1 内部请求类）
- Modify: `performance/src/test/java/.../support/PerfTestConfig.java`（补 UserApi mock bean）
- Create: `performance/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java`

**权限脚本（新增）**
- Create: `docs/superpowers/sql/2026-05-29-eval-user-tags-page-resource-seed.sql`

**前端（修改）**
- Modify: `xanzc_frontend/src/api/eval.js`（加 2 函数）
- Rewrite: `xanzc_frontend/src/views/eval/UserTags.vue`（表格 + 编辑弹窗）

---

## Phase A：auth UserApi 扩展

### Task A1：UserMapper 新增 keyword OR 分页查询

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java`
- Modify: `auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml`

> 说明：现有 `selectByQuery` 对 username/userchnname 用 AND 拼接，无法满足"按姓名或工号 OR 搜索"。新增专用 OR 查询，隔离不影响既有逻辑。本任务无独立单测（XML 行为在 Task A2 的 Facade 单测中通过 mock 验证调用、真实 SQL 留待 Phase E 手工/集成验证）。

- [ ] **Step 1: UserMapper.java 加 2 个方法**

在 `UserMapper` 接口中追加（紧跟现有方法之后，import 已有 `@Param` / `List` / `PtUser`）：

```java
    /**
     * 按关键词 OR 模糊分页查询用户（工号/登录名/中文名），供人员标签列表用。
     *
     * @param keyword 关键词（null/空 表示不过滤，返回全部）
     * @param offset  偏移量（从 0 开始）
     * @param limit   每页条数
     * @return 用户列表，按 USER_ID 升序
     */
    List<PtUser> selectByKeywordPaged(@Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 与 selectByKeywordPaged 配套的总数统计。
     *
     * @param keyword 关键词（null/空 表示不过滤）
     * @return 总条数
     */
    long countByKeyword(@Param("keyword") String keyword);
```

- [ ] **Step 2: UserMapper.xml 加 2 段 SQL**

在 `UserMapper.xml` 的 `</mapper>` 之前追加（`BASE_COLUMNS` refid 已存在于该文件，被 `selectByQuery` 使用）：

```xml
    <!-- 人员标签列表：按工号/登录名/中文名 OR 模糊分页查询 -->
    <select id="selectByKeywordPaged" resultType="com.bank.branch.platform.auth.entity.PtUser">
        SELECT
            <include refid="BASE_COLUMNS"/>
        FROM PT_USER
        <where>
            <if test="keyword != null and keyword != ''">
                (USER_ID LIKE CONCAT('%', #{keyword}, '%')
                 OR USERNAME LIKE CONCAT('%', #{keyword}, '%')
                 OR USERCHNNAME LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
        ORDER BY USER_ID
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countByKeyword" resultType="long">
        SELECT COUNT(*) FROM PT_USER
        <where>
            <if test="keyword != null and keyword != ''">
                (USER_ID LIKE CONCAT('%', #{keyword}, '%')
                 OR USERNAME LIKE CONCAT('%', #{keyword}, '%')
                 OR USERCHNNAME LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
    </select>
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl auth-permission-center -am compile`
Expected: BUILD SUCCESS（仅编译，方法尚无实现引用）

- [ ] **Step 4: Commit**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java \
        auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml
git commit -m "feat(auth): UserMapper 新增 keyword OR 分页查询（人员标签列表用）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task A2：UserApi 加 pageUsers / getRolesByUserIds（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/UserApi.java`
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/facade/UserFacade.java`
- Test: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/facade/UserFacadePageRolesTest.java`

- [ ] **Step 1: 写失败的测试**

创建 `UserFacadePageRolesTest.java`：

```java
package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleItemDTO;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.web.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFacadePageRolesTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    @DisplayName("pageUsers 返回分页用户，empId/username/displayName 装配正确")
    void pageUsers_returnsMappedDtos() {
        PtUser u = new PtUser();
        u.setUserId("1001");
        u.setUsername("zhangsan");
        u.setUserchnname("张三");
        when(userMapper.selectByKeywordPaged("张", 0, 20)).thenReturn(List.of(u));
        when(userMapper.countByKeyword("张")).thenReturn(1L);

        PageResult<UserDTO> r = userFacade.pageUsers("张", 1, 20);

        assertThat(r.getTotal()).isEqualTo(1L);
        assertThat(r.getRecords()).hasSize(1);
        assertThat(r.getRecords().get(0).getEmpId()).isEqualTo("1001");
        assertThat(r.getRecords().get(0).getDisplayName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("pageUsers 入参越界时归一：pageNo<1→1，pageSize>100→100，pageSize<1→20")
    void pageUsers_normalizesPaging() {
        when(userMapper.selectByKeywordPaged(null, 0, 100)).thenReturn(List.of());
        when(userMapper.countByKeyword(null)).thenReturn(0L);

        PageResult<UserDTO> r = userFacade.pageUsers(null, 0, 999);

        assertThat(r.getPageNo()).isEqualTo(1);
        assertThat(r.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getRolesByUserIds 按 userId 分组返回角色列表")
    void getRolesByUserIds_groupsByUserId() {
        UserRoleItemDTO a = new UserRoleItemDTO();
        a.setUserId("1001"); a.setRoleId("r1"); a.setRoleCode("R_ADMIN"); a.setRoleChName("管理员");
        UserRoleItemDTO b = new UserRoleItemDTO();
        b.setUserId("1001"); b.setRoleId("r2"); b.setRoleCode("R_RM"); b.setRoleChName("客户经理");
        when(userRoleMapper.selectRolesByUserIds(List.of("1001"))).thenReturn(List.of(a, b));

        Map<String, List<RoleSimpleDTO>> map = userFacade.getRolesByUserIds(List.of("1001"));

        assertThat(map.get("1001")).hasSize(2);
        assertThat(map.get("1001")).extracting(RoleSimpleDTO::getRoleChName)
                .containsExactlyInAnyOrder("管理员", "客户经理");
    }

    @Test
    @DisplayName("getRolesByUserIds 空入参返回空 Map")
    void getRolesByUserIds_emptyInput_returnsEmptyMap() {
        assertThat(userFacade.getRolesByUserIds(List.of())).isEmpty();
        assertThat(userFacade.getRolesByUserIds(null)).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败/红**

Run: `mvn -q -pl auth-permission-center test -Dtest=UserFacadePageRolesTest`
Expected: 编译失败（`UserFacade` 无 `pageUsers` / `getRolesByUserIds` 方法、`UserApi` 无对应声明）

- [ ] **Step 3: UserApi 接口加 2 方法声明**

在 `UserApi.java` 加 import：

```java
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.common.web.PageResult;
import java.util.Map;
```

在接口尾部（`getUsersByUsernames` 之后）追加：

```java
    /**
     * 按关键词分页查询用户（工号/登录名/中文名 OR 模糊），供 performance 人员标签列表用。
     *
     * @param keyword  关键词（null/空 不过滤）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页条数（&lt;1 归一为 20，&gt;100 截断为 100）
     * @return 分页用户（empId/username/displayName 已装配，mainOrg 不填充）
     */
    PageResult<UserDTO> pageUsers(String keyword, int pageNo, int pageSize);

    /**
     * 批量查询多个用户的角色简要列表，避免逐用户 N+1。
     *
     * @param userIds 用户ID（工号）列表
     * @return Map&lt;userId, 角色列表&gt;；入参为空时返回空 Map，无角色的 userId 不在 Map 中
     */
    Map<String, List<RoleSimpleDTO>> getRolesByUserIds(List<String> userIds);
```

- [ ] **Step 4: UserFacade 实现 2 方法**

在 `UserFacade.java` 加 import：

```java
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleItemDTO;
import com.bank.branch.platform.common.web.PageResult;
import java.util.HashMap;
import java.util.Map;
```

在类尾部追加：

```java
    @Override
    public PageResult<UserDTO> pageUsers(String keyword, int pageNo, int pageSize) {
        int p = pageNo < 1 ? 1 : pageNo;
        int s = pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int offset = (p - 1) * s;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<PtUser> rows = userMapper.selectByKeywordPaged(kw, offset, s);
        long total = userMapper.countByKeyword(kw);
        List<UserDTO> items = new ArrayList<>(rows.size());
        for (PtUser u : rows) {
            UserDTO dto = new UserDTO();
            dto.setEmpId(u.getUserId());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getUserchnname());
            items.add(dto);
        }
        return PageResult.of(p, s, total, items);
    }

    @Override
    public Map<String, List<RoleSimpleDTO>> getRolesByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<UserRoleItemDTO> rows = userRoleMapper.selectRolesByUserIds(userIds);
        Map<String, List<RoleSimpleDTO>> map = new HashMap<>();
        if (rows == null) return map;
        for (UserRoleItemDTO r : rows) {
            RoleSimpleDTO dto = new RoleSimpleDTO();
            dto.setRoleId(r.getRoleId());
            dto.setRoleCode(r.getRoleCode());
            dto.setRoleChName(r.getRoleChName());
            map.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(dto);
        }
        return map;
    }
```

- [ ] **Step 5: 运行测试，确认通过/绿**

Run: `mvn -q -pl auth-permission-center test -Dtest=UserFacadePageRolesTest`
Expected: Tests run: 4, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/UserApi.java \
        auth-permission-center/src/main/java/com/bank/branch/platform/auth/facade/UserFacade.java \
        auth-permission-center/src/test/java/com/bank/branch/platform/auth/facade/UserFacadePageRolesTest.java
git commit -m "feat(auth): UserApi 新增 pageUsers / getRolesByUserIds（人员标签列表聚合用）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase B：perf 后端聚合 + 覆盖式保存

### Task B1：新增 DTO 与 mapper 投影类

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagBriefDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserRoleRowDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalUserTagRow.java`

- [ ] **Step 1: 创建 EvalUserTagBriefDTO.java**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 标签简要信息（列表/编辑回显用）：仅 tagId + tagName。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvalUserTagBriefDTO {
    /** 标签ID. */
    private Long tagId;
    /** 标签名称. */
    private String tagName;
}
```

- [ ] **Step 2: 创建 EvalUserTagRow.java（mapper 投影）**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

/** EVAL_USER_TAG JOIN EVAL_TAG 的批量查询投影行. */
@Data
public class EvalUserTagRow {
    /** 人员ID（EVAL_USER_TAG.user_id，BIGINT）. */
    private Long userId;
    /** 标签ID. */
    private Long tagId;
    /** 标签名称. */
    private String tagName;
    /** 标签类型：1=被评价人, 2=评价人. */
    private Integer tagType;
}
```

- [ ] **Step 3: 创建 EvalUserRoleRowDTO.java（列表行）**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;
import java.util.List;

/** 人员标签列表的一行：人员基本信息 + RBAC 角色 + 被评价人角色（单） + 评价人角色（多）。 */
@Data
public class EvalUserRoleRowDTO {
    /** 人员ID（工号，PT_USER.USER_ID）. */
    private String userId;
    /** 显示名（中文名优先，回退登录名）. */
    private String userName;
    /** 部门（机构名，来自通讯录）. */
    private String orgName;
    /** 岗位（来自通讯录 position）. */
    private String position;
    /** RBAC 角色中文名列表（只读展示）. */
    private List<String> roleNames;
    /** 被评价人角色（tagType=1，至多一个；无则 null）. */
    private EvalUserTagBriefDTO beEvalTag;
    /** 评价人角色（tagType=2，可多个）. */
    private List<EvalUserTagBriefDTO> evalTags;
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/
git commit -m "feat(eval): 人员标签列表 DTO + mapper 投影类

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task B2：EvalUserTagMapper 批量查询 + 错误码

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserTagMapper.java`
- Modify: `performance-engine-center/src/main/resources/mapper/performance/EvalUserTagMapper.xml`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`

- [ ] **Step 1: EvalUserTagMapper.java 加方法**

加 import：`import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;`

在接口中追加：

```java
    /**
     * 批量查询多个用户的标签（JOIN EVAL_TAG 带出 tagName/tagType），供列表聚合用。
     * 调用方须保证 userIds 非空。
     *
     * @param userIds 人员ID列表（BIGINT）
     * @return 投影行列表
     */
    List<EvalUserTagRow> selectUserTagsByUserIds(@Param("userIds") List<Long> userIds);
```

- [ ] **Step 2: EvalUserTagMapper.xml 加 SQL**

在 `</mapper>` 之前追加：

```xml
    <select id="selectUserTagsByUserIds" resultType="com.bank.branch.platform.performance.eval.dto.EvalUserTagRow">
        SELECT ut.user_id AS userId, ut.tag_id AS tagId, t.tag_name AS tagName, t.tag_type AS tagType
        FROM EVAL_USER_TAG ut
        INNER JOIN EVAL_TAG t ON t.tag_id = ut.tag_id
        WHERE ut.user_id IN
        <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
    </select>
```

- [ ] **Step 3: PerfErrorCode.java 加错误码**

在 `EVAL_RULE_NOT_FOUND("PERF-40058", "被评价人无匹配的评价规则");` 这一行，把结尾分号改为逗号并新增一行（注意枚举常量需以分号结尾）：

```java
    EVAL_RULE_NOT_FOUND("PERF-40058", "被评价人无匹配的评价规则"),
    EVAL_TAG_TYPE_MISMATCH("PERF-40059", "标签类型不匹配");
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalUserTagMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/EvalUserTagMapper.xml \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java
git commit -m "feat(eval): EvalUserTagMapper 批量查询 + EVAL_TAG_TYPE_MISMATCH 错误码

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task B3：EvalUserTagService 扩依赖 + saveUserRoles（TDD）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java`

> 注：本任务先扩展 Service 的构造器依赖（加 `EvalTagMapper` / `UserApi` / `AddressBookApi`），并实现 `saveUserRoles`。`pageUserRoles` 在 Task B4 实现，但**两者共用扩展后的构造器**——本任务一次性把构造器改到位，B4 不再改构造器。

- [ ] **Step 1: 写失败的测试（saveUserRoles 部分）**

创建 `EvalUserRoleServiceTest.java`：

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.portal.api.AddressBookApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalUserRoleServiceTest {

    @Mock private EvalUserTagMapper evalUserTagMapper;
    @Mock private EvalTagMapper evalTagMapper;
    @Mock private UserApi userApi;
    @Mock private AddressBookApi addressBookApi;

    @InjectMocks private EvalUserTagService service;

    @Captor private ArgumentCaptor<List<EvalUserTag>> insertCaptor;

    private EvalTag tag(long id, int type) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        t.setTagType(type);
        return t;
    }

    @Test
    @DisplayName("saveUserRoles 覆盖：先删该用户全部旧标签，再插入新被评价+评价人组合")
    void saveUserRoles_overwrite() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId(1001L); old1.setTagId(7L);
        EvalUserTag old2 = new EvalUserTag(); old2.setUserId(1001L); old2.setTagId(8L);
        when(evalUserTagMapper.selectByUserId(1001L)).thenReturn(List.of(old1, old2));
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L, 1)); // 被评价
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2)); // 评价人
        when(evalTagMapper.selectById(3L)).thenReturn(tag(3L, 2)); // 评价人

        service.saveUserRoles(1001L, 1L, List.of(2L, 3L));

        verify(evalUserTagMapper).batchDelete(eq(1001L), eq(List.of(7L, 8L)));
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId=null 表示清空被评价角色，仅插入评价人标签")
    void saveUserRoles_nullBeEval_onlyEvalTags() {
        when(evalUserTagMapper.selectByUserId(1001L)).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        service.saveUserRoles(1001L, null, List.of(2L));

        verify(evalUserTagMapper, never()).batchDelete(anyLong(), anyList());
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
    }

    @Test
    @DisplayName("saveUserRoles 全部清空：beEvalTagId=null + evalTagIds 空，仅删除不插入")
    void saveUserRoles_clearAll() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId(1001L); old1.setTagId(7L);
        when(evalUserTagMapper.selectByUserId(1001L)).thenReturn(List.of(old1));

        service.saveUserRoles(1001L, null, List.of());

        verify(evalUserTagMapper).batchDelete(eq(1001L), eq(List.of(7L)));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId 指向评价人标签(tagType=2) → PERF-40059")
    void saveUserRoles_beEvalWrongType_throws() {
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        assertThatThrownBy(() -> service.saveUserRoles(1001L, 2L, List.of()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoles evalTagIds 含被评价标签(tagType=1) → PERF-40059")
    void saveUserRoles_evalWrongType_throws() {
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L, 1));

        assertThatThrownBy(() -> service.saveUserRoles(1001L, null, List.of(1L)))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH));
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId 指向不存在标签 → PERF-40058")
    void saveUserRoles_beEvalNotFound_throws() {
        when(evalTagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveUserRoles(1001L, 99L, List.of()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_NOT_FOUND));
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败/红**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: 编译失败（构造器签名不符 / 无 `saveUserRoles` 方法）

- [ ] **Step 3: 改造 EvalUserTagService（扩依赖 + saveUserRoles）**

把 `EvalUserTagService.java` 顶部 import 区补齐：

```java
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import java.util.ArrayList;
```

替换字段与构造器（原来只有 `evalUserTagMapper` 单依赖）：

```java
    private final EvalUserTagMapper evalUserTagMapper;
    private final EvalTagMapper evalTagMapper;
    private final UserApi userApi;
    private final AddressBookApi addressBookApi;

    @Autowired
    public EvalUserTagService(EvalUserTagMapper evalUserTagMapper,
                              EvalTagMapper evalTagMapper,
                              UserApi userApi,
                              AddressBookApi addressBookApi) {
        this.evalUserTagMapper = evalUserTagMapper;
        this.evalTagMapper = evalTagMapper;
        this.userApi = userApi;
        this.addressBookApi = addressBookApi;
    }
```

在类中新增 `saveUserRoles`（保留既有 `getByUserId` / `getUserIdsByTagId` / `batchBind` / `batchUnbind` 不动）：

```java
    /**
     * 覆盖式保存人员的评价角色：删除该用户全部旧关联，再写入新的被评价人标签（至多一个）+ 评价人标签（多个）。
     * <p>被评价人单选由参数结构（单个 beEvalTagId）天然保证；类型校验防止前端传错类型。</p>
     *
     * @param userId      人员ID
     * @param beEvalTagId 被评价人标签ID（必须 tagType=1；null 表示清空被评价人角色）
     * @param evalTagIds  评价人标签ID列表（必须都是 tagType=2；null/空 表示清空评价人角色）
     * @throws PerfException EVAL_RULE_NOT_FOUND（标签不存在）/ EVAL_TAG_TYPE_MISMATCH（类型不符）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(Long userId, Long beEvalTagId, List<Long> evalTagIds) {
        // 1. 校验被评价人标签必须 tagType=1
        if (beEvalTagId != null) {
            EvalTag t = evalTagMapper.selectById(beEvalTagId);
            if (t == null) throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, beEvalTagId);
            if (!Integer.valueOf(1).equals(t.getTagType())) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH, beEvalTagId);
            }
        }
        // 2. 校验评价人标签必须都是 tagType=2
        List<Long> evalIds = (evalTagIds == null) ? List.of() : evalTagIds;
        for (Long tid : evalIds) {
            EvalTag t = evalTagMapper.selectById(tid);
            if (t == null) throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tid);
            if (!Integer.valueOf(2).equals(t.getTagType())) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH, tid);
            }
        }
        // 3. 覆盖：删除该用户全部旧标签关联
        List<EvalUserTag> existing = evalUserTagMapper.selectByUserId(userId);
        if (!existing.isEmpty()) {
            List<Long> oldTagIds = existing.stream().map(EvalUserTag::getTagId).collect(Collectors.toList());
            evalUserTagMapper.batchDelete(userId, oldTagIds);
        }
        // 4. 写入新组合（被评价 1 个 + 评价人 N 个）
        List<EvalUserTag> toInsert = new ArrayList<>();
        if (beEvalTagId != null) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(beEvalTagId);
            toInsert.add(u);
        }
        for (Long tid : evalIds) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(tid);
            toInsert.add(u);
        }
        if (!toInsert.isEmpty()) {
            evalUserTagMapper.batchInsert(toInsert);
        }
        log.info("[EvalUserTagService.saveUserRoles] userId={} beEvalTagId={} evalTagIds={}", userId, beEvalTagId, evalIds);
    }
```

- [ ] **Step 4: 补 PerfTestConfig 的 UserApi mock bean（防 Spring IT 上下文加载失败）**

`EvalUserTagService` 现在注入 `UserApi`。perf 既有 `@SpringBootTest` 类会 component-scan 到该 Service，而测试上下文无真实 auth Bean。在 `support/PerfTestConfig.java` 追加 import 与 bean（`AddressBookApi` mock 已存在，无需重复）：

加 import：`import com.bank.branch.platform.auth.api.UserApi;`

加 bean：

```java
    /**
     * 测试用 UserApi（2026-05-29 人员标签列表引入）：
     * EvalUserTagService 注入 UserApi 做用户分页/批量角色查询，测试上下文无真实 auth Bean，
     * 提供默认 mock 避免 Spring 上下文启动失败；单测可 {@code @MockBean} 覆盖。
     */
    @Bean
    @Primary
    public UserApi userApi() {
        return Mockito.mock(UserApi.class);
    }
```

- [ ] **Step 5: 运行测试，确认通过/绿**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: Tests run: 6, Failures: 0
（说明：本步 `pageUserRoles` 尚未实现，测试类暂只覆盖 saveUserRoles 6 个用例）

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/PerfTestConfig.java
git commit -m "feat(eval): EvalUserTagService 覆盖式保存 saveUserRoles + 类型校验

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task B4：EvalUserTagService.pageUserRoles 聚合（TDD）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java`

- [ ] **Step 1: 给测试类追加聚合用例**

在 `EvalUserRoleServiceTest` 顶部补 import：

```java
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import java.util.Map;
```

在类中追加用例：

```java
    private UserDTO user(String empId, String name) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setDisplayName(name);
        return u;
    }

    private EvalUserTagRow tagRow(long uid, long tid, String name, int type) {
        EvalUserTagRow r = new EvalUserTagRow();
        r.setUserId(uid); r.setTagId(tid); r.setTagName(name); r.setTagType(type);
        return r;
    }

    @Test
    @DisplayName("pageUserRoles 拼装：被评价取单个、评价人取列表、部门/岗位/角色到位")
    void pageUserRoles_assembles() {
        when(userApi.pageUsers("张", 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("1001", "张三"))));
        when(addressBookApi.getEmployees(List.of("1001")))
                .thenReturn(List.of(EmployeeDTO.builder().empId("1001").orgName("某支行").position("行长").build()));
        RoleSimpleDTO role = new RoleSimpleDTO();
        role.setRoleChName("管理员");
        when(userApi.getRolesByUserIds(List.of("1001"))).thenReturn(Map.of("1001", List.of(role)));
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of(1001L)))
                .thenReturn(List.of(
                        tagRow(1001L, 1L, "支行行长", 1),
                        tagRow(1001L, 2L, "副行长评委", 2),
                        tagRow(1001L, 3L, "同级评委", 2)));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles("张", 1, 20);

        assertThat(r.getTotal()).isEqualTo(1L);
        EvalUserRoleRowDTO row = r.getRecords().get(0);
        assertThat(row.getUserId()).isEqualTo("1001");
        assertThat(row.getUserName()).isEqualTo("张三");
        assertThat(row.getOrgName()).isEqualTo("某支行");
        assertThat(row.getPosition()).isEqualTo("行长");
        assertThat(row.getRoleNames()).containsExactly("管理员");
        assertThat(row.getBeEvalTag().getTagName()).isEqualTo("支行行长");
        assertThat(row.getEvalTags()).extracting(t -> t.getTagName())
                .containsExactlyInAnyOrder("副行长评委", "同级评委");
    }

    @Test
    @DisplayName("pageUserRoles 空页：用户列表为空时直接返回空 records，不查下游")
    void pageUserRoles_emptyPage() {
        when(userApi.pageUsers(null, 1, 20)).thenReturn(PageResult.of(1, 20, 0L, List.of()));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, 1, 20);

        assertThat(r.getRecords()).isEmpty();
        verify(addressBookApi, never()).getEmployees(anyList());
    }

    @Test
    @DisplayName("pageUserRoles 非数值工号：跳过 EVAL_USER_TAG 匹配，标签列为空但不报错")
    void pageUserRoles_nonNumericEmpId() {
        when(userApi.pageUsers(null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("U_ABC123", "李四"))));
        when(addressBookApi.getEmployees(List.of("U_ABC123"))).thenReturn(List.of());
        when(userApi.getRolesByUserIds(List.of("U_ABC123"))).thenReturn(Map.of());

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, 1, 20);

        EvalUserRoleRowDTO row = r.getRecords().get(0);
        assertThat(row.getBeEvalTag()).isNull();
        assertThat(row.getEvalTags()).isEmpty();
        assertThat(row.getRoleNames()).isEmpty();
        // 无数值型工号 → 不应调用批量标签查询
        verify(evalUserTagMapper, never()).selectUserTagsByUserIds(anyList());
    }
```

- [ ] **Step 2: 运行测试，确认红**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: 编译失败（无 `pageUserRoles` 方法）

- [ ] **Step 3: 实现 pageUserRoles**

在 `EvalUserTagService.java` 补 import：

```java
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagBriefDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
```

新增方法：

```java
    /**
     * 分页聚合查询人员标签列表：每行含 人员基本信息 + 部门/岗位（通讯录）+ RBAC 角色 + 被评价人/评价人标签。
     *
     * @param keyword  关键词（工号/姓名，可空）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<EvalUserRoleRowDTO> pageUserRoles(String keyword, int page, int pageSize) {
        PageResult<UserDTO> users = userApi.pageUsers(keyword, page, pageSize);
        List<UserDTO> records = users.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResult.of(page, pageSize, users.getTotal(), List.of());
        }
        List<String> empIds = records.stream().map(UserDTO::getEmpId).collect(Collectors.toList());

        // 部门/岗位：通讯录批量
        Map<String, EmployeeDTO> empMap = addressBookApi.getEmployees(empIds).stream()
                .collect(Collectors.toMap(EmployeeDTO::getEmpId, Function.identity(), (a, b) -> a));
        // RBAC 角色：批量
        Map<String, List<RoleSimpleDTO>> roleMap = userApi.getRolesByUserIds(empIds);

        // EVAL 标签：仅数值型工号能匹配 BIGINT user_id
        List<Long> numericIds = empIds.stream().map(this::toLongOrNull).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<EvalUserTagRow>> tagMap = new HashMap<>();
        if (!numericIds.isEmpty()) {
            for (EvalUserTagRow r : evalUserTagMapper.selectUserTagsByUserIds(numericIds)) {
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

            Long numId = toLongOrNull(u.getEmpId());
            List<EvalUserTagRow> tagRows = (numId == null) ? List.of() : tagMap.getOrDefault(numId, List.of());
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
        return PageResult.of(page, pageSize, users.getTotal(), rows);
    }

    /** 工号转 Long，非数值返回 null（用于匹配 EVAL_USER_TAG.user_id BIGINT）. */
    private Long toLongOrNull(String s) {
        try {
            return Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }
```

- [ ] **Step 4: 运行测试，确认绿（含 B3 的 6 个 + 本任务 3 个）**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalUserRoleServiceTest`
Expected: Tests run: 9, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalUserTagService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalUserRoleServiceTest.java
git commit -m "feat(eval): EvalUserTagService.pageUserRoles 聚合分页查询

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task B5：EvalUserTagController 新增 2 端点

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalUserTagController.java`

> 与既有 bind/unbind 一致，仅 `@BizAuth` 不加 `@AuditLog`（沿用 eval 子域现状）。

- [ ] **Step 1: 加 import**

```java
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
```

- [ ] **Step 2: 加 2 个端点 + 1 个请求类**

在 `EvalUserTagController` 类中（`unbind` 方法之后、`BindReq` 之前）追加：

```java
    @GetMapping("/page")
    @Operation(summary = "分页查询人员标签列表（含部门/岗位/角色）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalUserRoleRowDTO>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[EvalUserTagController.page] keyword={}, page={}, pageSize={}", keyword, page, pageSize);
        return ResponseWrapper.success(evalUserTagService.pageUserRoles(keyword, page, pageSize));
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "覆盖式保存人员评价角色（被评价单选/评价人多选）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> saveRoles(@PathVariable("userId") Long userId,
                                           @Validated @RequestBody SaveRolesReq req) {
        log.info("[EvalUserTagController.saveRoles] userId={}, beEvalTagId={}, evalTagIds={}",
                userId, req.getBeEvalTagId(), req.getEvalTagIds());
        evalUserTagService.saveUserRoles(userId, req.getBeEvalTagId(), req.getEvalTagIds());
        return ResponseWrapper.success();
    }
```

在 `BindReq` 静态类之后追加：

```java
    @Data
    public static class SaveRolesReq {
        /** 被评价人标签ID（null 表示清空被评价人角色）. */
        private Long beEvalTagId;
        /** 评价人标签ID列表（null/空 表示清空评价人角色）. */
        private List<Long> evalTagIds;
    }
```

> 注意：`@GetMapping("/page")` 与既有 `@GetMapping`（无路径，对应 `?userId=`）不冲突，Spring 按路径区分。

- [ ] **Step 3: 编译 + 全量 eval 相关单测**

Run: `mvn -q -pl performance-engine-center test -Dtest='Eval*Test'`
Expected: BUILD SUCCESS，全绿（含 EvalTagServiceTest / EvalUserRoleServiceTest 等）

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalUserTagController.java
git commit -m "feat(eval): user-tags 新增 /page 聚合查询 + /{userId}/roles 覆盖式保存端点

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase C：权限注册

### Task C1：PT_RESOURCE 登记 2 个新端点

**Files:**
- Create: `docs/superpowers/sql/2026-05-29-eval-user-tags-page-resource-seed.sql`

> 模板对齐既有 `docs/superpowers/sql/2026-05-27-eval-pt-resource-seed.sql`（字段 RESOURCE_ID/RESOURCE_URL/RESOURCE_METHOD/MENU_NAME/ISMENU/STATUS，STATUS=0 启用；路径变量用 AntPath `*`；授权 R_ADMIN + R_BACK_TECH）。

- [ ] **Step 1: 创建脚本**

```sql
-- ============================================================
-- 人员标签列表化改造：新增 2 个端点的 PT_RESOURCE 登记
-- 日期: 2026-05-29
-- 依赖: 2026-05-27-eval-pt-resource-seed.sql（PERF_EVAL_1~19）
-- ============================================================

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS)
VALUES
('PERF_EVAL_20', '/api/admin/eval/user-tags/page',      'GET', '人员标签列表',     0, 0),
('PERF_EVAL_21', '/api/admin/eval/user-tags/*/roles',   'PUT', '保存人员评价角色', 0, 0);

-- R_ADMIN 授权
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_20', 'PERF_EVAL_21');

-- R_BACK_TECH 授权
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_20', 'PERF_EVAL_21');
```

- [ ] **Step 2: 在开发库执行（yiti 库，root/djdev）**

Run（手工或 CI；执行前确认 `PERF_EVAL_20/21` 不存在以免主键冲突）:
```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-05-29-eval-user-tags-page-resource-seed.sql
```
Expected: 2 行 PT_RESOURCE + 4 行 PT_ROLE_RESOURCE 插入成功

> 若执行环境无 DB 访问权限，跳过 Step 2，但必须在交付说明里提示"上线前须执行此脚本，否则新端点 403"。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/sql/2026-05-29-eval-user-tags-page-resource-seed.sql
git commit -m "chore(eval): 人员标签列表 2 端点 PT_RESOURCE 登记脚本

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase D：前端

### Task D1：api/eval.js 新增 2 函数

**Files:**
- Modify: `xanzc_frontend/src/api/eval.js`

- [ ] **Step 1: 在"人员标签关联"区块（`unbindUserTags` 之后）追加**

```javascript
// 2026-05-29：人员标签列表化改造
// 分页查询人员标签列表（含部门/岗位/角色 + 被评价人/评价人标签）
export function pageUserRoles(params = {}) {
  return call('get', '/admin/eval/user-tags/page', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

// 覆盖式保存人员评价角色（被评价单选 beEvalTagId / 评价人多选 evalTagIds）
export function saveUserRoles(userId, beEvalTagId, evalTagIds) {
  return call('put', `/admin/eval/user-tags/${userId}/roles`, { data: { beEvalTagId, evalTagIds } }, { ok: true });
}
```

- [ ] **Step 2: Commit**

```bash
git add xanzc_frontend/src/api/eval.js
git commit -m "feat(eval-ui): api 新增 pageUserRoles / saveUserRoles

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task D2：UserTags.vue 重写为表格 + 编辑弹窗

**Files:**
- Rewrite: `xanzc_frontend/src/views/eval/UserTags.vue`

> 后端 `/page` 返回 `PageResult`，经 `http.js` 响应拦截器解包为 `{ pageNo, pageSize, total, records }`（见 http.js 第 42 行 `if (body.page) return body.page`）。故 `pageUserRoles` 的返回直接取 `.records` / `.total`。`listAllTags({tagType, status})` 返回数组。

- [ ] **Step 1: 用以下内容整体替换 `UserTags.vue`**

```vue
<template>
  <div>
    <div class="page-h">
      <h1>人员标签</h1>
      <span class="desc">维护人员的被评价人角色（单选）与评价人角色（多选）</span>
    </div>

    <div class="card-section">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名或工号"
          clearable
          size="small"
          class="kw-input"
          @input="onSearch"
          @clear="reload"
        />
      </div>

      <el-table :data="rows" v-loading="loading" border size="small" style="width: 100%">
        <el-table-column prop="userName" label="姓名" min-width="100" />
        <el-table-column prop="userId" label="工号" min-width="110" />
        <el-table-column prop="orgName" label="部门" min-width="140">
          <template #default="{ row }">{{ row.orgName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="position" label="岗位" min-width="120">
          <template #default="{ row }">{{ row.position || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">{{ (row.roleNames && row.roleNames.length) ? row.roleNames.join('，') : '—' }}</template>
        </el-table-column>
        <el-table-column label="被评价人角色" min-width="130">
          <template #default="{ row }">
            <el-tag v-if="row.beEvalTag" type="success" effect="plain" size="small">{{ row.beEvalTag.tagName }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="评价人角色" min-width="180">
          <template #default="{ row }">
            <span v-if="row.evalTags && row.evalTags.length">{{ row.evalTags.map(t => t.tagName).join('，') }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑人员评价角色" width="520px" @closed="onDialogClosed">
      <div v-if="editing" class="edit-info">
        <div class="info-row"><span class="info-k">姓名</span><span>{{ editing.userName }}</span></div>
        <div class="info-row"><span class="info-k">工号</span><span>{{ editing.userId }}</span></div>
        <div class="info-row"><span class="info-k">部门</span><span>{{ editing.orgName || '—' }}</span></div>
        <div class="info-row"><span class="info-k">岗位</span><span>{{ editing.position || '—' }}</span></div>
        <div class="info-row"><span class="info-k">角色</span><span>{{ (editing.roleNames && editing.roleNames.length) ? editing.roleNames.join('，') : '—' }}</span></div>
      </div>

      <el-form label-width="110px" class="edit-form">
        <el-form-item label="被评价人角色">
          <el-select v-model="form.beEvalTagId" clearable filterable placeholder="单选，可清空" style="width: 100%">
            <el-option v-for="t in tagsOfType(1)" :key="t.tagId" :value="t.tagId" :label="t.tagName" />
          </el-select>
        </el-form-item>
        <el-form-item label="评价人角色">
          <el-select v-model="form.evalTagIds" multiple filterable placeholder="可多选" style="width: 100%">
            <el-option v-for="t in tagsOfType(2)" :key="t.tagId" :value="t.tagId" :label="t.tagName" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listAllTags, pageUserRoles, saveUserRoles } from '@/api/eval';

// === 全量启用标签（编辑弹窗下拉用） ===
const allTags = ref([]);
function tagsOfType(type) {
  return allTags.value.filter(t => t.tagType === type && t.status === 1);
}
async function loadAllTags() {
  try {
    const r = await listAllTags({ status: 1 });
    allTags.value = Array.isArray(r) ? r : (r?.records || []);
  } catch {
    allTags.value = [];
  }
}

// === 列表 ===
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const loading = ref(false);
let searchTimer = null;

async function reload() {
  loading.value = true;
  try {
    const r = await pageUserRoles({ keyword: keyword.value.trim() || undefined, page: page.value, pageSize: pageSize.value });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = r?.total ?? rows.value.length;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => { page.value = 1; reload(); }, 300);
}

function onPageChange(p) {
  page.value = p;
  reload();
}

// === 编辑 ===
const editVisible = ref(false);
const editing = ref(null);
const saving = ref(false);
const form = reactive({ beEvalTagId: null, evalTagIds: [] });

function openEdit(row) {
  editing.value = row;
  form.beEvalTagId = row.beEvalTag ? row.beEvalTag.tagId : null;
  form.evalTagIds = (row.evalTags || []).map(t => t.tagId);
  editVisible.value = true;
}

function onDialogClosed() {
  editing.value = null;
  form.beEvalTagId = null;
  form.evalTagIds = [];
}

async function handleSave() {
  if (!editing.value) return;
  saving.value = true;
  try {
    await saveUserRoles(editing.value.userId, form.beEvalTagId ?? null, form.evalTagIds || []);
    ElMessage.success('保存成功');
    editVisible.value = false;
    await reload();
  } catch {
    ElMessage.error('保存失败，请重试');
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await loadAllTags();
  await reload();
});
</script>

<style lang="scss" scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.kw-input {
  width: 240px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.muted {
  color: $text-3;
}
.edit-info {
  background: $bg-soft;
  border: 1px solid $border-1;
  border-radius: 4px;
  padding: 10px 14px;
  margin-bottom: 16px;
}
.info-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.9;
}
.info-k {
  width: 40px;
  color: $text-3;
}
.edit-form {
  padding-right: 8px;
}
</style>
```

- [ ] **Step 2: 前端构建验证**

Run: `cd xanzc_frontend && npm run build`
Expected: build 成功，无语法/引用错误

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/views/eval/UserTags.vue
git commit -m "feat(eval-ui): 人员标签页重写为列表 + 行内编辑弹窗

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase E：集成验证

### Task E1：跨模块 install + 全量测试 + 手工冒烟

> 跨模块改了 auth（被 performance 依赖）。按 CLAUDE.md "Stale jar 处理"必须先 install 上游，否则 perf test 用旧 jar 加载旧 UserApi 类。

- [ ] **Step 1: 清缓存重装全部模块**

Run: `mvn clean install -DskipTests`
Expected: BUILD SUCCESS（全部模块 install 到本地 .m2）

- [ ] **Step 2: 跑 auth + performance 单测**

Run: `mvn -q -pl auth-permission-center,performance-engine-center test`
Expected: BUILD SUCCESS；新增的 `UserFacadePageRolesTest`(4) + `EvalUserRoleServiceTest`(9) 全绿；既有用例不回归（perf 既有本地基线失败项见模块 CLAUDE.md，与本次改动无关，需逐一比对确认非新引入）

- [ ] **Step 3: 手工冒烟（需本地起 bootstrap + 前端）**

1. `cd bootstrap && mvn spring-boot:run`（确保 yiti 库已执行 Task C1 脚本）
2. 前端 `cd xanzc_frontend && npm run dev`，用 R_ADMIN 账号登录，进入「内部评价 → 人员标签」
3. 验证：
   - 列表分页展示人员，部门/岗位/角色列有值（取决于通讯录/角色数据）
   - 搜索姓名/工号能过滤
   - 点「编辑」：被评价人角色单选、评价人角色多选；选项分别来自标签管理的 tagType=1/2 启用标签
   - 保存后列表对应行的两列正确刷新（被评价显示单个、评价人逗号分隔）
   - 把被评价清空保存 → 该列变「—」

- [ ] **Step 4: 最终合并提交（如有手工冒烟修正）**

按需 commit；无修正则本任务无提交。

---

## Self-Review 记录

- **Spec 覆盖**：聚合分页接口（Task B4/B5）、覆盖式保存+单选强制（Task B3/B5）、UserApi 2 方法（Task A2）、部门/岗位/角色拼装（Task B4）、前端表格+编辑弹窗（Task D2）、PT_RESOURCE 登记（Task C1）、测试矩阵（A2/B3/B4）、不改表/不改标签管理页（计划未触及）——spec 各节均有对应任务。
- **占位符**：无 TBD/TODO，所有步骤含完整代码与命令。
- **类型一致性**：`pageUsers`/`getRolesByUserIds`/`saveUserRoles`/`pageUserRoles`/`selectByKeywordPaged`/`countByKeyword`/`selectUserTagsByUserIds` 在接口、实现、测试、controller 中签名一致；`EvalUserTagBriefDTO`/`EvalUserRoleRowDTO`/`EvalUserTagRow`/`SaveRolesReq` 字段在 DTO 定义与使用处一致；`PageResult.of/getRecords/getTotal`、`UserDTO.empId/displayName/username`、`EmployeeDTO.empId/orgName/position`、`RoleSimpleDTO.roleChName` 均与既有源码核对一致。
- **String/Long 边界**：聚合查询 `toLongOrNull` 兜底；保存路径变量 Long，与既有 BindReq 一致。
