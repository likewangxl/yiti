# User Management Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 xanpd_backend 的 12 个用户管理接口（CRUD + 启用/禁用/锁定/解锁 + 密码重置/修改）以 yiti 架构规范（DTO、`@BizAuth`、TDD、`PT_RESOURCE` 登记、统一响应、`BizException`、BCrypt）增量迁移到 `auth-permission-center`，复用现有 `PtUser`/`UserMapper`/`UserApi`，对外契约零变更。

**Architecture:** 在 `auth-permission-center` 模块内新增 `controller/UserController` → `service/UserService` → `mapper/UserMapper` (扩展) 闭环。仅模块内调用，不上 `*Api`。密码统一 BCrypt（不引入 SM2）。批量接口沿用 xanpd 风格 `{ids}` 逗号分隔 PathVariable。当前用户改密路径调整为 RESTful `/me/password`。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis 3.0.3 + MyBatis-Plus BaseMapper / MySQL 8 / BCryptPasswordEncoder / `ResponseWrapper`+`PageResult`（common-web） / `BizException`+`AuthErrorCode` / `jakarta.validation` / JUnit 5 + Mockito + AssertJ + MockMvc。

**关联 Spec:** `docs/superpowers/specs/2026-05-18-user-management-migration-design.md`

---

## File Structure (全部新建/修改清单)

**新建：**
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserQueryReqDTO.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserCreateReqDTO.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserUpdateReqDTO.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserDetailRespDTO.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserListItemRespDTO.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/ChangeMyPasswordReqDTO.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/AuthUserProperties.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java`
- `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`
- `auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java`
- `docs/superpowers/sql/2026-05-18-v1.14-user-management-pt-resource.sql`

**修改：**
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/enums/AuthErrorCode.java` — 追加 4 个错误码
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java` — 追加 6 个方法
- `auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml` — 追加 6 个 SQL
- `auth-permission-center/src/main/resources/application.yml`（如不存在则在 `bootstrap/src/main/resources/application.yml` 加 `auth.user.*`）

---

## Task 1: 追加 4 个新错误码到 AuthErrorCode

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/enums/AuthErrorCode.java`

- [ ] **Step 1: 修改 AuthErrorCode，追加 4 个枚举**

在 `// 409 冲突` 区段（已有 40901~40903）之后追加 40904/40905；在文件最前面 `// 401 认证失败` 之前新增 `// 400 参数错误` 区段。完整修改为：

```java
package com.bank.branch.platform.auth.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AUTH 模块错误码枚举
 * 格式：AUTH-{HTTP状态码}{序号}
 */
@Getter
@AllArgsConstructor
public enum AuthErrorCode {

    // 400 参数错误
    OLD_PASSWORD_MISMATCH("AUTH-40001", "旧密码不正确"),
    INVALID_USER_IDS("AUTH-40002", "用户ID列表为空或超出上限"),

    // 401 认证失败
    LOGIN_FAILED("AUTH-40101", "用户名或密码错误"),
    ACCOUNT_LOCKED("AUTH-40102", "用户账号已锁定"),
    ACCOUNT_EXPIRED("AUTH-40103", "用户账号已过期"),
    ACCOUNT_DISABLED("AUTH-40104", "用户账号未启用"),
    NOT_AUTHENTICATED("AUTH-40105", "未登录或会话已过期"),
    PASSWORD_ATTEMPTS_EXCEEDED("AUTH-40106", "密码错误次数超限"),

    // 403 授权失败
    RBAC_DENIED("AUTH-40301", "无接口访问权限"),
    RESOURCE_NOT_REGISTERED("AUTH-40302", "资源未登记"),
    DATA_SCOPE_DENIED("AUTH-40303", "数据范围拒绝"),
    BIZ_TYPE_NOT_CONFIGURED("AUTH-40304", "BizType未配置"),
    ENTITY_OWNERSHIP_DENIED("AUTH-40305", "实体归属校验失败"),
    STATUS_CONSTRAINT_DENIED("AUTH-40306", "状态约束拒绝"),
    HIGH_RISK_ACTION_MISSING_REASON("AUTH-40307", "高危动作缺少原因"),
    PERMISSION_CACHE_UNAVAILABLE("AUTH-40308", "权限缓存不可用"),

    // 404 资源不存在
    ROLE_NOT_FOUND("AUTH-40401", "角色不存在"),
    RESOURCE_NOT_FOUND("AUTH-40402", "资源不存在"),
    USER_NOT_FOUND("AUTH-40403", "用户不存在"),
    ORG_NOT_FOUND("AUTH-40404", "机构不存在"),
    BIZ_SCOPE_NOT_FOUND("AUTH-40405", "BizScope配置不存在"),

    // 409 冲突
    ROLE_CODE_DUPLICATE("AUTH-40901", "角色编码已存在"),
    RESOURCE_URL_METHOD_DUPLICATE("AUTH-40902", "资源URL+Method已存在"),
    BIZ_SCOPE_DUPLICATE("AUTH-40903", "BizScope配置已存在"),
    USER_ID_DUPLICATE("AUTH-40904", "用户ID已存在"),
    USERNAME_DUPLICATE("AUTH-40905", "用户名已存在"),

    // 500 内部错误
    INTERNAL_ERROR("AUTH-50001", "权限服务内部错误"),
    CACHE_ERROR("AUTH-50002", "缓存服务异常");

    private final String code;
    private final String message;
}
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center compile -q
```

Expected: 编译成功，无错误。

- [ ] **Step 3: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/enums/AuthErrorCode.java && git commit -m "auth: 追加用户管理 4 个错误码 (USER_ID_DUPLICATE / USERNAME_DUPLICATE / OLD_PASSWORD_MISMATCH / INVALID_USER_IDS)"
```

---

## Task 2: 新增 AuthUserProperties + application.yml 配置

**Files:**
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/AuthUserProperties.java`
- Modify: `bootstrap/src/main/resources/application.yml`（如 `auth-permission-center` 模块本身有 `application.yml` 则一起改）

- [ ] **Step 1: 创建 AuthUserProperties**

Create `auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/AuthUserProperties.java`:

```java
package com.bank.branch.platform.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户管理相关配置。
 * 默认密码生产环境必须通过外部 application-prod.yml 或环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.user")
public class AuthUserProperties {

    /** 重置密码的默认值（BCrypt 加密前的明文） */
    private String defaultPassword = "Branch@2026";

    /** 批量接口 ids 上限 */
    private int maxBatchIds = 50;
}
```

- [ ] **Step 2: 修改 application.yml 追加配置**

打开 `bootstrap/src/main/resources/application.yml`，在文件末尾追加（如 `auth` 节点已存在则合并）：

```yaml
auth:
  user:
    default-password: Branch@2026
    max-batch-ids: 50
```

- [ ] **Step 3: 编译验证**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center compile -q
```

Expected: 成功。

- [ ] **Step 4: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/AuthUserProperties.java bootstrap/src/main/resources/application.yml && git commit -m "auth: 新增 AuthUserProperties 配置 (default-password / max-batch-ids)"
```

---

## Task 3: 创建 6 个用户管理 DTO

**Files:**
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserQueryReqDTO.java`
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserCreateReqDTO.java`
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserUpdateReqDTO.java`
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserDetailRespDTO.java`
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/UserListItemRespDTO.java`
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/ChangeMyPasswordReqDTO.java`

- [ ] **Step 1: 创建 UserQueryReqDTO**

```java
package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/** 用户分页查询请求 DTO */
@Data
public class UserQueryReqDTO {
    /** 登录名模糊匹配 */
    private String username;
    /** 中文名模糊匹配 */
    private String userchnname;
    /** 邮箱模糊匹配 */
    private String email;
    /** 备注模糊匹配 */
    private String remark;
    /** 启用状态过滤 0-启用 1-未启用 */
    private Integer isEnabled;
    /** 锁定状态过滤 0-未锁定 1-已锁定 */
    private Integer isLocked;
    /** 页码（从1开始） */
    private Integer pageNo = 1;
    /** 每页条数 */
    private Integer pageSize = 20;
}
```

- [ ] **Step 2: 创建 UserCreateReqDTO**

```java
package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增用户请求 DTO */
@Data
public class UserCreateReqDTO {
    /** 用户ID（工号，业务赋值） */
    @NotBlank(message = "用户ID不能为空")
    @Size(max = 32)
    private String userId;

    /** 登录名 */
    @NotBlank(message = "登录名不能为空")
    @Size(max = 64)
    private String username;

    /** 中文姓名 */
    @NotBlank(message = "中文姓名不能为空")
    @Size(max = 64)
    private String userchnname;

    /** 邮箱 */
    @Email
    @Size(max = 128)
    private String email;

    /** 初始密码明文（service 层 BCrypt 后存库） */
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64)
    private String initialPassword;

    /** 备注 */
    @Size(max = 256)
    private String remark;
}
```

- [ ] **Step 3: 创建 UserUpdateReqDTO**

```java
package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改用户请求 DTO（不含密码字段） */
@Data
public class UserUpdateReqDTO {
    @Size(max = 64)
    private String username;

    @Size(max = 64)
    private String userchnname;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 256)
    private String remark;
}
```

- [ ] **Step 4: 创建 UserDetailRespDTO**

```java
package com.bank.branch.platform.auth.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户详情响应 DTO（不含密码字段） */
@Data
public class UserDetailRespDTO {
    private String userId;
    private String username;
    private String userchnname;
    private String email;
    private String remark;
    private Integer isExpired;
    private Integer isLocked;
    private Integer isEnabled;
    private Integer passWrongCount;
    private LocalDateTime createTime;
    private String createAuthor;
    private LocalDateTime updateTime;
    private String updateAuthor;
    private LocalDateTime pwdUpdateTime;
}
```

- [ ] **Step 5: 创建 UserListItemRespDTO**

```java
package com.bank.branch.platform.auth.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户列表项响应 DTO（不含密码字段） */
@Data
public class UserListItemRespDTO {
    private String userId;
    private String username;
    private String userchnname;
    private String email;
    private String remark;
    private Integer isExpired;
    private Integer isLocked;
    private Integer isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 6: 创建 ChangeMyPasswordReqDTO**

```java
package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 当前用户修改密码请求 DTO */
@Data
public class ChangeMyPasswordReqDTO {
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度须在 6~64 之间")
    private String newPassword;
}
```

- [ ] **Step 7: 编译验证**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center compile -q
```

Expected: 成功。

- [ ] **Step 8: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/ && git commit -m "auth: 新增用户管理 6 个 DTO (Query/Create/Update/Detail/ListItem/ChangeMyPassword)"
```

---

## Task 4: 扩展 UserMapper（接口 + XML 6 个新方法）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java`
- Modify: `auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml`

- [ ] **Step 1: 在 UserMapper.java 末尾追加 6 个方法**

打开 `auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java`，在已有方法之后追加：

```java
    /** 条件分页列表 */
    List<PtUser> selectByQuery(@Param("q") UserQueryReqDTO q,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 条件总数 */
    long countByQuery(@Param("q") UserQueryReqDTO q);

    /** 用户名是否已存在（不区分大小写以避免 MySQL 默认大小写敏感差异） */
    int countByUsername(@Param("username") String username);

    /** 修改启用状态 */
    int updateActiveStatus(@Param("userId") String userId,
                           @Param("isEnabled") int isEnabled,
                           @Param("updateAuthor") String updateAuthor);

    /** 修改密码并同时刷新 PWD_UPDATE_TIME / 清零 PASS_WRONG_COUNT */
    int updatePassword(@Param("userId") String userId,
                       @Param("pwd") String bcryptHash,
                       @Param("updateAuthor") String updateAuthor);

    /** 物理批量删除（沿用 xanpd 行为；外键级联由 service 层处理） */
    int deleteByUserIds(@Param("userIds") java.util.List<String> userIds);
```

文件顶部 import 区追加：
```java
import com.bank.branch.platform.auth.api.dto.UserQueryReqDTO;
```

- [ ] **Step 2: 在 UserMapper.xml 末尾追加对应 SQL**

打开 `auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml`，在 `</mapper>` 之前追加：

```xml
    <!-- ============= 用户管理 V1.14 新增 ============= -->

    <!-- 条件分页查询：用 BASE_COLUMNS 全列回填 entity -->
    <select id="selectByQuery" resultType="com.bank.branch.platform.auth.entity.PtUser">
        SELECT
            <include refid="BASE_COLUMNS"/>
        FROM PT_USER
        <where>
            <if test="q.username != null and q.username != ''">
                AND USERNAME LIKE CONCAT('%', #{q.username}, '%')
            </if>
            <if test="q.userchnname != null and q.userchnname != ''">
                AND USERCHNNAME LIKE CONCAT('%', #{q.userchnname}, '%')
            </if>
            <if test="q.email != null and q.email != ''">
                AND EMAIL LIKE CONCAT('%', #{q.email}, '%')
            </if>
            <if test="q.remark != null and q.remark != ''">
                AND REMARK LIKE CONCAT('%', #{q.remark}, '%')
            </if>
            <if test="q.isEnabled != null">
                AND ISENABLED = #{q.isEnabled}
            </if>
            <if test="q.isLocked != null">
                AND ISLOCKED = #{q.isLocked}
            </if>
        </where>
        ORDER BY CREATE_TIME DESC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <!-- 条件总数 -->
    <select id="countByQuery" resultType="long">
        SELECT COUNT(*) FROM PT_USER
        <where>
            <if test="q.username != null and q.username != ''">
                AND USERNAME LIKE CONCAT('%', #{q.username}, '%')
            </if>
            <if test="q.userchnname != null and q.userchnname != ''">
                AND USERCHNNAME LIKE CONCAT('%', #{q.userchnname}, '%')
            </if>
            <if test="q.email != null and q.email != ''">
                AND EMAIL LIKE CONCAT('%', #{q.email}, '%')
            </if>
            <if test="q.remark != null and q.remark != ''">
                AND REMARK LIKE CONCAT('%', #{q.remark}, '%')
            </if>
            <if test="q.isEnabled != null">
                AND ISENABLED = #{q.isEnabled}
            </if>
            <if test="q.isLocked != null">
                AND ISLOCKED = #{q.isLocked}
            </if>
        </where>
    </select>

    <!-- 用户名唯一性校验 -->
    <select id="countByUsername" resultType="int">
        SELECT COUNT(*) FROM PT_USER WHERE USERNAME = #{username}
    </select>

    <!-- 启用/禁用：单字段更新避免覆盖其他并发修改 -->
    <update id="updateActiveStatus">
        UPDATE PT_USER
        SET ISENABLED = #{isEnabled},
            UPDATE_TIME = NOW(),
            UPDATE_AUTHOR = #{updateAuthor}
        WHERE USER_ID = #{userId}
    </update>

    <!-- 密码更新：同时刷新 PWD_UPDATE_TIME、清零 PASS_WRONG_COUNT -->
    <update id="updatePassword">
        UPDATE PT_USER
        SET PWD = #{pwd},
            PWD_UPDATE_TIME = NOW(),
            PASS_WRONG_COUNT = 0,
            UPDATE_TIME = NOW(),
            UPDATE_AUTHOR = #{updateAuthor}
        WHERE USER_ID = #{userId}
    </update>

    <!-- 批量物理删除 -->
    <delete id="deleteByUserIds">
        DELETE FROM PT_USER
        WHERE USER_ID IN
        <foreach collection="userIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </delete>
```

- [ ] **Step 3: 编译验证**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center compile -q
```

Expected: 成功。

- [ ] **Step 4: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml && git commit -m "auth: UserMapper 追加 6 个写操作方法 (selectByQuery/countByQuery/countByUsername/updateActiveStatus/updatePassword/deleteByUserIds)"
```

---

## Task 5: UserService 骨架 + create/existsByUsername/getById（TDD）

**Files:**
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Create: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 写失败的测试 UserServiceTest（覆盖 create/exists/getById）**

```java
package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.config.AuthUserProperties;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock BCryptPasswordEncoder passwordEncoder;
    AuthUserProperties props = new AuthUserProperties();
    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder, props);
    }

    // ---------- create ----------
    @Test
    void create_shouldInsertAndEncryptPassword() {
        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUserId("E001");
        req.setUsername("alice");
        req.setUserchnname("张三");
        req.setInitialPassword("Init@123");

        when(userMapper.selectByUserId("E001")).thenReturn(null);
        when(userMapper.countByUsername("alice")).thenReturn(0);
        when(passwordEncoder.encode("Init@123")).thenReturn("$2a$bcrypt$xxx");

        userService.create(req, "OPERATOR1");

        verify(passwordEncoder).encode("Init@123");
        verify(userMapper).insert(any(PtUser.class));
    }

    @Test
    void create_shouldThrowWhenUserIdDuplicate() {
        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUserId("E001");
        req.setUsername("alice");
        req.setUserchnname("张三");
        req.setInitialPassword("Init@123");

        PtUser existing = new PtUser();
        existing.setUserId("E001");
        when(userMapper.selectByUserId("E001")).thenReturn(existing);

        assertThatThrownBy(() -> userService.create(req, "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40904"));
    }

    @Test
    void create_shouldThrowWhenUsernameDuplicate() {
        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUserId("E002");
        req.setUsername("alice");
        req.setUserchnname("张三");
        req.setInitialPassword("Init@123");

        when(userMapper.selectByUserId("E002")).thenReturn(null);
        when(userMapper.countByUsername("alice")).thenReturn(1);

        assertThatThrownBy(() -> userService.create(req, "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40905"));
    }

    // ---------- existsByUsername ----------
    @Test
    void existsByUsername_shouldReturnTrueWhenCountGreaterThanZero() {
        when(userMapper.countByUsername("alice")).thenReturn(1);
        assertThat(userService.existsByUsername("alice")).isTrue();
    }

    @Test
    void existsByUsername_shouldReturnFalseWhenZero() {
        when(userMapper.countByUsername("bob")).thenReturn(0);
        assertThat(userService.existsByUsername("bob")).isFalse();
    }

    // ---------- getById ----------
    @Test
    void getById_shouldReturnDtoWithoutPwd() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setUsername("alice");
        u.setUserchnname("张三");
        u.setPwd("$2a$bcrypt$xxx");
        when(userMapper.selectByUserId("E001")).thenReturn(u);

        UserDetailRespDTO dto = userService.getById("E001");

        assertThat(dto.getUserId()).isEqualTo("E001");
        assertThat(dto.getUsername()).isEqualTo("alice");
        // 响应 DTO 不应该含 pwd
        // (DTO 字段中本就没有 pwd 字段 -- 编译期保证)
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(userMapper.selectByUserId(anyString())).thenReturn(null);
        assertThatThrownBy(() -> userService.getById("NONE"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败（UserService 类不存在）。

- [ ] **Step 3: 创建 UserService 骨架 + 实现 create/existsByUsername/getById**

```java
package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.config.AuthUserProperties;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户管理服务。
 * <p>
 * 提供 CRUD、批量状态变更、密码重置/修改 12 个能力，模块内被 UserController 调用，
 * 不暴露到跨模块 *Api 层。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthUserProperties props;

    /**
     * 新增用户。
     * <p>
     * 双重唯一性校验（userId + username），密码 BCrypt 加密。
     * </p>
     *
     * @param req      请求 DTO
     * @param operator 操作人工号（取自 CurrentUserApi，由 controller 注入）
     * @throws BizException AUTH-40904 / AUTH-40905
     */
    public void create(UserCreateReqDTO req, String operator) {
        if (userMapper.selectByUserId(req.getUserId()) != null) {
            throw new BizException(AuthErrorCode.USER_ID_DUPLICATE.getCode(),
                    AuthErrorCode.USER_ID_DUPLICATE.getMessage());
        }
        if (userMapper.countByUsername(req.getUsername()) > 0) {
            throw new BizException(AuthErrorCode.USERNAME_DUPLICATE.getCode(),
                    AuthErrorCode.USERNAME_DUPLICATE.getMessage());
        }
        PtUser entity = new PtUser();
        entity.setUserId(req.getUserId());
        entity.setUsername(req.getUsername());
        entity.setUserchnname(req.getUserchnname());
        entity.setEmail(req.getEmail());
        entity.setRemark(req.getRemark());
        entity.setPwd(passwordEncoder.encode(req.getInitialPassword()));
        entity.setIsExpired(0);
        entity.setIsLocked(0);
        entity.setIsEnabled(0);
        entity.setPassWrongCount(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setCreateAuthor(operator);
        entity.setPwdUpdateTime(now);
        userMapper.insert(entity);
        log.info("[UserService.create] 新增用户 userId={} username={} operator={}",
                entity.getUserId(), entity.getUsername(), operator);
    }

    /** 检查 username 是否已存在 */
    public boolean existsByUsername(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    /** 按 userId 加载，找不到抛 USER_NOT_FOUND */
    public UserDetailRespDTO getById(String userId) {
        PtUser u = userMapper.selectByUserId(userId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        return toDetailDto(u);
    }

    /** PtUser → UserDetailRespDTO（密码字段一律不映射） */
    private UserDetailRespDTO toDetailDto(PtUser u) {
        UserDetailRespDTO dto = new UserDetailRespDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserchnname(u.getUserchnname());
        dto.setEmail(u.getEmail());
        dto.setRemark(u.getRemark());
        dto.setIsExpired(u.getIsExpired());
        dto.setIsLocked(u.getIsLocked());
        dto.setIsEnabled(u.getIsEnabled());
        dto.setPassWrongCount(u.getPassWrongCount());
        dto.setCreateTime(u.getCreateTime());
        dto.setCreateAuthor(u.getCreateAuthor());
        dto.setUpdateTime(u.getUpdateTime());
        dto.setUpdateAuthor(u.getUpdateAuthor());
        dto.setPwdUpdateTime(u.getPwdUpdateTime());
        return dto;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 6 个测试用例全部 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService 骨架 + create/existsByUsername/getById (TDD)"
```

---

## Task 6: UserService.pageUsers（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 在 UserServiceTest 追加失败测试**

```java
    @Test
    void pageUsers_shouldReturnPageResult() {
        com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q =
                new com.bank.branch.platform.auth.api.dto.UserQueryReqDTO();
        q.setUsername("ali");
        q.setPageNo(1);
        q.setPageSize(10);

        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setUsername("alice");
        when(userMapper.selectByQuery(eq(q), eq(0), eq(10))).thenReturn(java.util.List.of(u));
        when(userMapper.countByQuery(eq(q))).thenReturn(1L);

        com.bank.branch.platform.common.web.PageResult<com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> page =
                userService.pageUsers(q);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getUserId()).isEqualTo("E001");
    }

    @Test
    void pageUsers_shouldClampPageSize() {
        com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q =
                new com.bank.branch.platform.auth.api.dto.UserQueryReqDTO();
        q.setPageNo(1);
        q.setPageSize(500); // 超出最大 100，应被截断为 100
        when(userMapper.selectByQuery(eq(q), eq(0), eq(100))).thenReturn(java.util.List.of());
        when(userMapper.countByQuery(eq(q))).thenReturn(0L);
        userService.pageUsers(q);
        verify(userMapper).selectByQuery(eq(q), eq(0), eq(100));
    }
```

import 补充：
```java
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败（`pageUsers` 方法不存在）。

- [ ] **Step 3: 在 UserService 追加 pageUsers 实现**

在 `UserService` 类末尾追加：

```java
    /**
     * 分页查询用户列表。pageSize 上限 100、下限 1；pageNo 下限 1。
     */
    public com.bank.branch.platform.common.web.PageResult<
            com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> pageUsers(
                    com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q) {
        int pageNo = (q.getPageNo() == null || q.getPageNo() < 1) ? 1 : q.getPageNo();
        int pageSize = (q.getPageSize() == null || q.getPageSize() < 1) ? 20 : q.getPageSize();
        if (pageSize > 100) pageSize = 100;
        q.setPageNo(pageNo);
        q.setPageSize(pageSize);
        int offset = (pageNo - 1) * pageSize;
        java.util.List<PtUser> records = userMapper.selectByQuery(q, offset, pageSize);
        long total = userMapper.countByQuery(q);
        java.util.List<com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> items =
                new java.util.ArrayList<>(records.size());
        for (PtUser u : records) items.add(toListItemDto(u));
        return com.bank.branch.platform.common.web.PageResult.of(pageNo, pageSize, total, items);
    }

    private com.bank.branch.platform.auth.api.dto.UserListItemRespDTO toListItemDto(PtUser u) {
        com.bank.branch.platform.auth.api.dto.UserListItemRespDTO dto =
                new com.bank.branch.platform.auth.api.dto.UserListItemRespDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserchnname(u.getUserchnname());
        dto.setEmail(u.getEmail());
        dto.setRemark(u.getRemark());
        dto.setIsExpired(u.getIsExpired());
        dto.setIsLocked(u.getIsLocked());
        dto.setIsEnabled(u.getIsEnabled());
        dto.setCreateTime(u.getCreateTime());
        dto.setUpdateTime(u.getUpdateTime());
        return dto;
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 8 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService.pageUsers (TDD, pageSize clamp 1~100)"
```

---

## Task 7: UserService.update（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 在 UserServiceTest 追加失败测试**

```java
    @Test
    void update_shouldSetUpdateFieldsAndCallMapper() {
        com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO();
        req.setUsername("alice2");
        req.setUserchnname("李四");

        PtUser existing = new PtUser();
        existing.setUserId("E001");
        when(userMapper.selectByUserId("E001")).thenReturn(existing);

        userService.update("E001", req, "OPERATOR1");

        org.mockito.ArgumentCaptor<PtUser> captor = org.mockito.ArgumentCaptor.forClass(PtUser.class);
        verify(userMapper).updateById(captor.capture());
        PtUser u = captor.getValue();
        assertThat(u.getUserId()).isEqualTo("E001");
        assertThat(u.getUsername()).isEqualTo("alice2");
        assertThat(u.getUserchnname()).isEqualTo("李四");
        assertThat(u.getUpdateAuthor()).isEqualTo("OPERATOR1");
        assertThat(u.getUpdateTime()).isNotNull();
        // 不应被改的字段保留
        assertThat(u.getPwd()).isEqualTo(existing.getPwd());
    }

    @Test
    void update_shouldThrowWhenUserNotFound() {
        when(userMapper.selectByUserId("NONE")).thenReturn(null);
        com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO();
        assertThatThrownBy(() -> userService.update("NONE", req, "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败（`update(String, UserUpdateReqDTO, String)` 方法不存在）。

- [ ] **Step 3: 在 UserService 追加 update 实现**

```java
    /** 修改用户基本信息（不改密码） */
    public void update(String userId,
                       com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req,
                       String operator) {
        PtUser u = userMapper.selectByUserId(userId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (req.getUsername() != null && !req.getUsername().isBlank()) u.setUsername(req.getUsername());
        if (req.getUserchnname() != null && !req.getUserchnname().isBlank()) u.setUserchnname(req.getUserchnname());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getRemark() != null) u.setRemark(req.getRemark());
        u.setUpdateTime(LocalDateTime.now());
        u.setUpdateAuthor(operator);
        userMapper.updateById(u);
        log.info("[UserService.update] userId={} operator={}", userId, operator);
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 10 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService.update (TDD)"
```

---

## Task 8: UserService.deleteByIds（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 追加失败测试**

```java
    @Test
    void deleteByIds_shouldCallMapperWithList() {
        when(userMapper.deleteByUserIds(java.util.List.of("E001","E002"))).thenReturn(2);
        int n = userService.deleteByIds(java.util.List.of("E001","E002"));
        assertThat(n).isEqualTo(2);
        verify(userMapper).deleteByUserIds(java.util.List.of("E001","E002"));
    }

    @Test
    void deleteByIds_shouldThrowWhenIdsEmpty() {
        assertThatThrownBy(() -> userService.deleteByIds(java.util.List.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }

    @Test
    void deleteByIds_shouldThrowWhenIdsExceedMaxBatch() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) ids.add("E" + i); // max-batch-ids=50
        assertThatThrownBy(() -> userService.deleteByIds(ids))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败（`deleteByIds` 方法不存在）。

- [ ] **Step 3: 在 UserService 追加 deleteByIds + 复用的 ids 校验工具**

```java
    /** 批量物理删除 */
    public int deleteByIds(java.util.List<String> userIds) {
        validateIds(userIds);
        int n = userMapper.deleteByUserIds(userIds);
        log.info("[UserService.deleteByIds] affected={} ids={}", n, userIds);
        return n;
    }

    /** ids 列表合法性校验（空/超限/含空串） */
    private void validateIds(java.util.List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(AuthErrorCode.INVALID_USER_IDS.getCode(),
                    AuthErrorCode.INVALID_USER_IDS.getMessage());
        }
        if (userIds.size() > props.getMaxBatchIds()) {
            throw new BizException(AuthErrorCode.INVALID_USER_IDS.getCode(),
                    AuthErrorCode.INVALID_USER_IDS.getMessage());
        }
        for (String id : userIds) {
            if (id == null || id.isBlank()) {
                throw new BizException(AuthErrorCode.INVALID_USER_IDS.getCode(),
                        AuthErrorCode.INVALID_USER_IDS.getMessage());
            }
        }
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 13 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService.deleteByIds + validateIds (TDD, 含 max-batch-ids 校验)"
```

---

## Task 9: UserService.resetPassword（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 追加失败测试**

```java
    @Test
    void resetPassword_shouldEncryptDefaultAndUpdateEach() {
        when(passwordEncoder.encode(props.getDefaultPassword())).thenReturn("$2a$bcrypt$default");
        when(userMapper.updatePassword(anyString(), anyString(), anyString())).thenReturn(1);

        int n = userService.resetPassword(java.util.List.of("E001", "E002"), "OPERATOR1");

        assertThat(n).isEqualTo(2);
        verify(userMapper).updatePassword("E001", "$2a$bcrypt$default", "OPERATOR1");
        verify(userMapper).updatePassword("E002", "$2a$bcrypt$default", "OPERATOR1");
    }

    @Test
    void resetPassword_shouldThrowWhenIdsInvalid() {
        assertThatThrownBy(() -> userService.resetPassword(java.util.List.of(), "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败（`resetPassword` 不存在）。

- [ ] **Step 3: 在 UserService 追加 resetPassword**

```java
    /** 批量重置密码为系统默认值（BCrypt 加密） */
    public int resetPassword(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        String bcrypt = passwordEncoder.encode(props.getDefaultPassword());
        int affected = 0;
        for (String id : userIds) {
            affected += userMapper.updatePassword(id, bcrypt, operator);
        }
        log.info("[UserService.resetPassword] affected={} ids={} operator={}", affected, userIds, operator);
        return affected;
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 15 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService.resetPassword (TDD, 默认密码 BCrypt)"
```

---

## Task 10: UserService.changeMyPassword（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 追加失败测试**

```java
    @Test
    void changeMyPassword_shouldUpdateWhenOldPasswordMatches() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setPwd("$2a$old");
        when(userMapper.selectByUserId("E001")).thenReturn(u);
        when(passwordEncoder.matches("oldPwd123", "$2a$old")).thenReturn(true);
        when(passwordEncoder.encode("newPwd456")).thenReturn("$2a$new");

        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("oldPwd123");
        req.setNewPassword("newPwd456");

        userService.changeMyPassword("E001", req);
        verify(userMapper).updatePassword("E001", "$2a$new", "E001");
    }

    @Test
    void changeMyPassword_shouldThrowWhenOldPasswordMismatch() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setPwd("$2a$old");
        when(userMapper.selectByUserId("E001")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "$2a$old")).thenReturn(false);

        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("wrong");
        req.setNewPassword("newPwd456");

        assertThatThrownBy(() -> userService.changeMyPassword("E001", req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40001"));
    }

    @Test
    void changeMyPassword_shouldThrowWhenUserNotFound() {
        when(userMapper.selectByUserId("NONE")).thenReturn(null);
        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("o"); req.setNewPassword("n");
        assertThatThrownBy(() -> userService.changeMyPassword("NONE", req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败。

- [ ] **Step 3: 追加 changeMyPassword 实现**

```java
    /** 当前用户修改密码：校验旧密码 → BCrypt 加密新密码 → 更新 */
    public void changeMyPassword(String currentUserId,
                                 com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req) {
        PtUser u = userMapper.selectByUserId(currentUserId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (!passwordEncoder.matches(req.getOldPassword(), u.getPwd())) {
            throw new BizException(AuthErrorCode.OLD_PASSWORD_MISMATCH.getCode(),
                    AuthErrorCode.OLD_PASSWORD_MISMATCH.getMessage());
        }
        String bcrypt = passwordEncoder.encode(req.getNewPassword());
        userMapper.updatePassword(currentUserId, bcrypt, currentUserId);
        log.info("[UserService.changeMyPassword] userId={} 修改自己的密码", currentUserId);
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 18 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService.changeMyPassword (TDD, BCrypt 旧密码校验)"
```

---

## Task 11: UserService 批量启用/禁用/锁定/解锁（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java`

- [ ] **Step 1: 追加失败测试**

```java
    @Test
    void batchActivate_shouldSetIsEnabledZero() {
        when(userMapper.updateActiveStatus(anyString(), eq(0), anyString())).thenReturn(1);
        int n = userService.batchActivate(java.util.List.of("E001","E002"), "OPERATOR1");
        assertThat(n).isEqualTo(2);
        verify(userMapper).updateActiveStatus("E001", 0, "OPERATOR1");
        verify(userMapper).updateActiveStatus("E002", 0, "OPERATOR1");
    }

    @Test
    void batchInactivate_shouldSetIsEnabledOne() {
        when(userMapper.updateActiveStatus(anyString(), eq(1), anyString())).thenReturn(1);
        int n = userService.batchInactivate(java.util.List.of("E001"), "OPERATOR1");
        assertThat(n).isEqualTo(1);
        verify(userMapper).updateActiveStatus("E001", 1, "OPERATOR1");
    }

    @Test
    void batchLock_shouldCallUpdateLockedStatusWithOne() {
        when(userMapper.updateLockedStatus(anyString(), eq(1))).thenReturn(1);
        int n = userService.batchLock(java.util.List.of("E001","E002"), "OPERATOR1");
        assertThat(n).isEqualTo(2);
        verify(userMapper).updateLockedStatus("E001", 1);
        verify(userMapper).updateLockedStatus("E002", 1);
    }

    @Test
    void batchUnlock_shouldCallUpdateLockedStatusWithZero() {
        when(userMapper.updateLockedStatus(anyString(), eq(0))).thenReturn(1);
        int n = userService.batchUnlock(java.util.List.of("E001"), "OPERATOR1");
        assertThat(n).isEqualTo(1);
        verify(userMapper).updateLockedStatus("E001", 0);
    }

    @Test
    void batchActivate_shouldThrowWhenIdsInvalid() {
        assertThatThrownBy(() -> userService.batchActivate(java.util.List.of(), "OP"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 编译失败。

- [ ] **Step 3: 追加四个批量方法**

注：`UserMapper.updateLockedStatus(String, int)` 是已存在的方法签名（PtUser 注释字段 `ISLOCKED`：0-未锁定，1-已锁定；`ISENABLED`：0-启用，1-未启用）。

```java
    /** 批量启用：ISENABLED=0 */
    public int batchActivate(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateActiveStatus(id, 0, operator);
        log.info("[UserService.batchActivate] affected={} ids={}", affected, userIds);
        return affected;
    }

    /** 批量禁用：ISENABLED=1 */
    public int batchInactivate(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateActiveStatus(id, 1, operator);
        log.info("[UserService.batchInactivate] affected={} ids={}", affected, userIds);
        return affected;
    }

    /** 批量锁定：ISLOCKED=1（复用已有 updateLockedStatus） */
    public int batchLock(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateLockedStatus(id, 1);
        log.info("[UserService.batchLock] affected={} ids={} operator={}", affected, userIds, operator);
        return affected;
    }

    /** 批量解锁：ISLOCKED=0 */
    public int batchUnlock(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateLockedStatus(id, 0);
        log.info("[UserService.batchUnlock] affected={} ids={} operator={}", affected, userIds, operator);
        return affected;
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserServiceTest -q
```

Expected: 23 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/UserService.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/UserServiceTest.java && git commit -m "auth: UserService 批量 active/inactive/lock/unlock (TDD)"
```

---

## Task 12: UserController 骨架 + 查询类 3 端点（list/exists/getById，TDD）

**Files:**
- Create: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java`
- Create: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java`

注：参考 `UserRoleController` 写法（已经 import `BizAuth` + `BizType` + `BizAction` + `ResponseWrapper`）；`CurrentUserApi` 已是依赖注入可用。

- [ ] **Step 1: 写失败的 UserControllerTest（含 3 个查询端点）**

```java
package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.api.dto.UserListItemRespDTO;
import com.bank.branch.platform.auth.service.UserService;
import com.bank.branch.platform.common.web.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserService userService;
    @Mock CurrentUserApi currentUserApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, currentUserApi)).build();
    }

    @Test
    void list_shouldReturnPagedUsers() throws Exception {
        UserListItemRespDTO it = new UserListItemRespDTO();
        it.setUserId("E001"); it.setUsername("alice");
        PageResult<UserListItemRespDTO> page = PageResult.of(1, 20, 1L, List.of(it));
        when(userService.pageUsers(any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/users").param("username", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value("E001"));
    }

    @Test
    void exists_shouldReturnTrue() throws Exception {
        when(userService.existsByUsername("alice")).thenReturn(true);
        mockMvc.perform(get("/api/admin/users/alice/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getById_shouldReturnUserDetail() throws Exception {
        UserDetailRespDTO dto = new UserDetailRespDTO();
        dto.setUserId("E001"); dto.setUsername("alice");
        when(userService.getById("E001")).thenReturn(dto);
        mockMvc.perform(get("/api/admin/users/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("E001"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserControllerTest -q
```

Expected: 编译失败（UserController 不存在）。

- [ ] **Step 3: 创建 UserController 骨架 + 查询端点实现**

```java
package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.api.dto.UserListItemRespDTO;
import com.bank.branch.platform.auth.api.dto.UserQueryReqDTO;
import com.bank.branch.platform.auth.service.UserService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理 REST 控制器
 * 提供 CRUD、批量启用/禁用/锁定/解锁、密码重置/修改 12 个接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Tag(name = "用户管理", description = "用户 CRUD / 状态变更 / 密码")
public class UserController {

    private final UserService userService;
    private final CurrentUserApi currentUserApi;

    @GetMapping("")
    @Operation(summary = "分页查询用户列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PageResult<UserListItemRespDTO>> list(@ModelAttribute UserQueryReqDTO q) {
        log.debug("[UserController.list] q={}", q);
        return ResponseWrapper.success(userService.pageUsers(q));
    }

    @GetMapping("/{username}/exists")
    @Operation(summary = "用户名是否已存在")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<Boolean> exists(@PathVariable("username") String username) {
        return ResponseWrapper.success(userService.existsByUsername(username));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "加载用户详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<UserDetailRespDTO> getById(@PathVariable("userId") String userId) {
        return ResponseWrapper.success(userService.getById(userId));
    }
}
```

注意：路径 `/{username}/exists` 与 `/{userId}` 共享前缀，但 `/exists` 是字面后缀，Spring MVC 会优先匹配 `/{username}/exists`，不会冲突。

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserControllerTest -q
```

Expected: 3 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java && git commit -m "auth: UserController 骨架 + list/exists/getById (TDD)"
```

---

## Task 13: UserController create/update/delete（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java`

- [ ] **Step 1: 追加失败测试**

在 `UserControllerTest` 类内追加：

```java
    @Test
    void create_shouldReturnSuccess() throws Exception {
        com.bank.branch.platform.auth.api.dto.UserCreateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserCreateReqDTO();
        req.setUserId("E001"); req.setUsername("alice"); req.setUserchnname("张三");
        req.setInitialPassword("Init@123");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        org.mockito.Mockito.doNothing().when(userService).create(any(), anyString());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void update_shouldReturnSuccess() throws Exception {
        com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO();
        req.setUsername("alice2");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        org.mockito.Mockito.doNothing().when(userService).update(anyString(), any(), anyString());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_shouldReturnAffectedCount() throws Exception {
        when(userService.deleteByIds(java.util.List.of("E001","E002"))).thenReturn(2);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/users/E001,E002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserControllerTest -q
```

Expected: 编译失败（端点缺失）。

- [ ] **Step 3: 在 UserController 追加 3 个端点**

在 `UserController` 类内追加：

```java
    @org.springframework.web.bind.annotation.PostMapping("")
    @Operation(summary = "新增用户")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> create(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.bank.branch.platform.auth.api.dto.UserCreateReqDTO req) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.create] userId={} operator={}", req.getUserId(), operator);
        userService.create(req, operator);
        return ResponseWrapper.success();
    }

    @org.springframework.web.bind.annotation.PutMapping("/{userId}")
    @Operation(summary = "修改用户")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> update(
            @PathVariable("userId") String userId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.update] userId={} operator={}", userId, operator);
        userService.update(userId, req, operator);
        return ResponseWrapper.success();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{ids}")
    @Operation(summary = "批量删除用户（ids 逗号分隔）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Integer> delete(@PathVariable("ids") String[] ids) {
        log.info("[UserController.delete] ids={}", java.util.Arrays.toString(ids));
        return ResponseWrapper.success(userService.deleteByIds(java.util.Arrays.asList(ids)));
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserControllerTest -q
```

Expected: 6 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java && git commit -m "auth: UserController create/update/delete (TDD)"
```

---

## Task 14: UserController 密码相关 2 端点 + 批量状态 4 端点（TDD）

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java`

- [ ] **Step 1: 追加失败测试（6 个端点）**

在 `UserControllerTest` 内追加：

```java
    @Test
    void resetPassword_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.resetPassword(java.util.List.of("E001","E002"), "OPERATOR1")).thenReturn(2);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001,E002/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void changeMyPassword_shouldReturnSuccess() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("oldX1"); req.setNewPassword("newY2");
        org.mockito.Mockito.doNothing().when(userService).changeMyPassword(eq("E001"), any());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/me/password")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void active_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchActivate(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/active"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void inactive_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchInactivate(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/inactive"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void lock_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchLock(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/lock"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void unlock_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchUnlock(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/unlock"))
                .andExpect(jsonPath("$.data").value(1));
    }
```

补充 import：
```java
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserControllerTest -q
```

Expected: 编译失败。

- [ ] **Step 3: 在 UserController 追加 6 个端点**

注意：`/me/password` 必须放在 `/{ids}/reset` 等带 PathVariable 路径**之前**显式声明 — Spring 字面段优先级高于 PathVariable，但为了安全和可读性，按方法签名顺序写在前面。

```java
    @org.springframework.web.bind.annotation.PutMapping("/me/password")
    @Operation(summary = "当前用户修改密码")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> changeMyPassword(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req) {
        String currentUserId = currentUserApi.getCurrentEmpId();
        log.info("[UserController.changeMyPassword] userId={}", currentUserId);
        userService.changeMyPassword(currentUserId, req);
        return ResponseWrapper.success();
    }

    @org.springframework.web.bind.annotation.PutMapping("/{ids}/reset")
    @Operation(summary = "批量重置密码（默认值）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> resetPassword(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.resetPassword] ids={} operator={}", java.util.Arrays.toString(ids), operator);
        return ResponseWrapper.success(userService.resetPassword(java.util.Arrays.asList(ids), operator));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{ids}/active")
    @Operation(summary = "批量启用")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> active(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchActivate(java.util.Arrays.asList(ids), operator));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{ids}/inactive")
    @Operation(summary = "批量禁用")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> inactive(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchInactivate(java.util.Arrays.asList(ids), operator));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{ids}/lock")
    @Operation(summary = "批量锁定")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> lock(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchLock(java.util.Arrays.asList(ids), operator));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{ids}/unlock")
    @Operation(summary = "批量解锁")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> unlock(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchUnlock(java.util.Arrays.asList(ids), operator));
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -Dtest=UserControllerTest -q
```

Expected: 12 个测试全部 PASS。

- [ ] **Step 5: 运行模块所有 surefire 测试，确认无回归**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -q
```

Expected: auth-permission-center 全部 surefire 测试 PASS（含已有测试 + 新增 35 个用例：UserServiceTest 23 + UserControllerTest 12）。

- [ ] **Step 6: Commit**

```bash
cd /home/djdev/lf/yiti && git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserController.java auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/UserControllerTest.java && git commit -m "auth: UserController 密码+批量状态 6 端点 (TDD, 完成 12 接口)"
```

---

## Task 15: PT_RESOURCE 注册 SQL

**Files:**
- Create: `docs/superpowers/sql/2026-05-18-v1.14-user-management-pt-resource.sql`

- [ ] **Step 1: 创建 SQL 脚本**

```sql
-- ================================================================
-- V1.14 用户管理 PT_RESOURCE 注册
-- 关联 spec: docs/superpowers/specs/2026-05-18-user-management-migration-design.md
-- 关联 plan: docs/superpowers/plans/2026-05-18-user-management-migration.md
-- 执行环境：MySQL 8.0 (生产 onepl / 测试 onepl_test_bootstrap)
-- ================================================================

-- 1. 注册 11 个用户管理接口（密码自助变更 /me/password 也登记，但归属 PERMISSION_CHANGE）
INSERT INTO PT_RESOURCE (RESOURCE_ID, URL_PATTERN, HTTP_METHOD, BIZ_TYPE, BIZ_ACTION, REMARK, CREATE_TIME) VALUES
  ('R_USER_LIST',         '/api/admin/users',                 'GET',    'SYS_CONFIG', 'READ',              '用户分页列表',        NOW()),
  ('R_USER_EXISTS',       '/api/admin/users/*/exists',        'GET',    'SYS_CONFIG', 'READ',              '判断用户名是否存在',  NOW()),
  ('R_USER_DETAIL',       '/api/admin/users/*',               'GET',    'SYS_CONFIG', 'READ',              '加载用户详情',        NOW()),
  ('R_USER_CREATE',       '/api/admin/users',                 'POST',   'SYS_CONFIG', 'CONFIG',            '新增用户',            NOW()),
  ('R_USER_UPDATE',       '/api/admin/users/*',               'PUT',    'SYS_CONFIG', 'CONFIG',            '修改用户',            NOW()),
  ('R_USER_DELETE',       '/api/admin/users/*',               'DELETE', 'SYS_CONFIG', 'CONFIG',            '批量删除用户',        NOW()),
  ('R_USER_RESET_PWD',    '/api/admin/users/*/reset',         'PUT',    'SYS_CONFIG', 'PERMISSION_CHANGE', '批量重置密码',        NOW()),
  ('R_USER_CHANGE_MY_PWD','/api/admin/users/me/password',     'PUT',    'SYS_CONFIG', 'PERMISSION_CHANGE', '当前用户修改密码',    NOW()),
  ('R_USER_ACTIVE',       '/api/admin/users/*/active',        'PUT',    'SYS_CONFIG', 'PERMISSION_CHANGE', '批量启用用户',        NOW()),
  ('R_USER_INACTIVE',     '/api/admin/users/*/inactive',      'PUT',    'SYS_CONFIG', 'PERMISSION_CHANGE', '批量禁用用户',        NOW()),
  ('R_USER_LOCK',         '/api/admin/users/*/lock',          'PUT',    'SYS_CONFIG', 'PERMISSION_CHANGE', '批量锁定用户',        NOW()),
  ('R_USER_UNLOCK',       '/api/admin/users/*/unlock',        'PUT',    'SYS_CONFIG', 'PERMISSION_CHANGE', '批量解锁用户',        NOW());

-- 2. 默认绑定到系统管理员角色（R_SYS_ADMIN）— 11 条全部纳入
--    /me/password 不绑定 R_SYS_ADMIN（每个用户都可自助修改自己密码，由前端不再做 RBAC 限制；
--    AuthorizationInterceptor 会按 URL 找到 PT_RESOURCE 但 SYS_ADMIN 跳过逻辑会放行，普通用户因路径不匹配也会通过；
--    若需更严控，请将 R_USER_CHANGE_MY_PWD 单独绑定到 R_ALL_USERS 之类的全员角色）
INSERT INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID, CREATE_TIME) VALUES
  ('R_SYS_ADMIN','R_USER_LIST',         NOW()),
  ('R_SYS_ADMIN','R_USER_EXISTS',       NOW()),
  ('R_SYS_ADMIN','R_USER_DETAIL',       NOW()),
  ('R_SYS_ADMIN','R_USER_CREATE',       NOW()),
  ('R_SYS_ADMIN','R_USER_UPDATE',       NOW()),
  ('R_SYS_ADMIN','R_USER_DELETE',       NOW()),
  ('R_SYS_ADMIN','R_USER_RESET_PWD',    NOW()),
  ('R_SYS_ADMIN','R_USER_CHANGE_MY_PWD',NOW()),
  ('R_SYS_ADMIN','R_USER_ACTIVE',       NOW()),
  ('R_SYS_ADMIN','R_USER_INACTIVE',     NOW()),
  ('R_SYS_ADMIN','R_USER_LOCK',         NOW()),
  ('R_SYS_ADMIN','R_USER_UNLOCK',       NOW());

-- 3. 验证：应返回 12
SELECT COUNT(*) AS user_resource_total FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'R_USER_%';
SELECT COUNT(*) AS user_resource_bound_to_sys_admin FROM PT_ROLE_RESOURCE WHERE ROLE_ID='R_SYS_ADMIN' AND RESOURCE_ID LIKE 'R_USER_%';
```

> **重要**：执行该 SQL 之前，必须先比对生产/测试库 `PT_RESOURCE` 已有的字段顺序与可空性。若该表存在 `RESOURCE_CHNAME` / `RESOURCE_GROUP` / `IS_HIGH_RISK` 等非空列，请在 plan 执行阶段先 `DESC PT_RESOURCE;` 后补齐字段值。

- [ ] **Step 2: 在 yiti 主 README 或 module CLAUDE.md 中登记该 SQL（追加一行索引）**

打开 `auth-permission-center/CLAUDE.md`（如已有 SQL 索引段落则追加，否则跳过该步骤）：

> 注：跳过此步骤的前提是 auth-permission-center/CLAUDE.md 没有 "SQL 脚本索引" 章节；若有则追加 `- V1.14 用户管理资源注册: docs/superpowers/sql/2026-05-18-v1.14-user-management-pt-resource.sql`。

- [ ] **Step 3: Commit**

```bash
cd /home/djdev/lf/yiti && git add docs/superpowers/sql/2026-05-18-v1.14-user-management-pt-resource.sql && git commit -m "auth: 新增 V1.14 用户管理 12 条 PT_RESOURCE + R_SYS_ADMIN 绑定 SQL"
```

---

## Task 16: 端到端验收

**Files:** 无文件修改，纯验证

- [ ] **Step 1: 全量 install（解决跨模块 stale jar，CLAUDE.md 红线）**

Run:
```bash
cd /home/djdev/lf/yiti && mvn clean install -DskipTests -q
```

Expected: 全部 9 个模块 BUILD SUCCESS。

- [ ] **Step 2: auth-permission-center surefire 全跑**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl auth-permission-center test -q
```

Expected: 所有 surefire（含 UserServiceTest 23 + UserControllerTest 12 = 35 新增）PASS。

- [ ] **Step 3: bootstrap failsafe IT（保证 V1.13 既有 IT 无回归）**

Run:
```bash
cd /home/djdev/lf/yiti && mvn -pl bootstrap verify -q
```

Expected: failsafe 全绿（与 V1.13 # 1 基线一致，本次不新增 IT，故不应有任何状态变化）。

- [ ] **Step 4: 启动应用 + 人工 smoke test（可选）**

Run（后台启动）：
```bash
cd /home/djdev/lf/yiti/bootstrap && mvn spring-boot:run
```

打开 `http://localhost:8080/doc.html`，找到 "用户管理" Tag，确认 12 个接口可见。

> 若 PT_RESOURCE SQL 未执行，调用任何接口都会被 `AuthorizationInterceptor` 拒绝（返回 `AUTH-40302 RESOURCE_NOT_REGISTERED`）。先执行 Task 15 的 SQL 后再 smoke test。

- [ ] **Step 5: 最终 Commit（如有未提交的 plan 索引修改）**

```bash
cd /home/djdev/lf/yiti && git status
# 若 git status 显示 clean，跳过 commit
```

---

## Self-Review (在此停下检查)

逐项核对 spec 第 11 节 "交付物清单" 与 plan task：

| Spec 交付物 | Plan 对应任务 |
|---|---|
| `controller/UserController.java` | Task 12 / 13 / 14 |
| `service/UserService.java` | Task 5–11 |
| 6 个 DTO | Task 3 |
| `config/AuthUserProperties.java` | Task 2 |
| `enums/AuthErrorCode.java` 追加 4 个 | Task 1 |
| `mapper/UserMapper.java` 追加 6 方法 | Task 4 |
| `mapper/auth/UserMapper.xml` 追加 SQL | Task 4 |
| `application.yml` 配置 | Task 2 |
| `UserServiceTest.java` | Task 5–11（增量构建） |
| `UserControllerTest.java` | Task 12–14（增量构建） |
| PT_RESOURCE SQL | Task 15 |

Spec 第 12 节 DoD 10 条 → Task 16 验收覆盖第 2/3/4 条；其他条目在各 Task 内的 commit/编译/测试通过即自然满足。

**类型一致性**：
- `BizException(code, message)` 构造一致（Task 5/7/8/10）
- `PtUser` setter 使用驼峰字段（`setUserId`/`setIsEnabled` 等），与 yiti `PtUser.java` 一致
- `UserService` 构造器签名 `(UserMapper, BCryptPasswordEncoder, AuthUserProperties)` 在 Task 5 定义，后续 Task 测试 setUp 一致使用
- `UserMapper.updateLockedStatus(userId, int locked)` 复用已有方法，未改签名（Task 11 Step 3 已注释说明）
- `PageResult.of(pageNo, pageSize, total, items)` 与 yiti `PageResult.of` 签名一致（已通过 `UserRoleServiceTest.listRoleUsers_shouldReturnPageResult` 验证）
- `ResponseWrapper.success(data)` / `ResponseWrapper.success()` 与 yiti 现有用法一致

**Placeholder 检查**：plan 全文不包含 "TBD" / "TODO" / "类似于上面" / 缺代码块步骤。Task 15 Step 2 的 "若已有 SQL 索引段落则追加" 是有意的条件分支，附了明确的判断依据，不算占位符。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-18-user-management-migration.md`.
