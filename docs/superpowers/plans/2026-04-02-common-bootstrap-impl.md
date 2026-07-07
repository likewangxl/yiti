# Common + Bootstrap 模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零搭建 Branch Platform 后端 Maven 多模块项目骨架，完成 common（5 子模块）和 bootstrap 启动模块的 TDD 实现。

**Architecture:** Maven 多模块项目，common 拆分为 common-web / common-trace / common-security / common-aop / common-db 五个子模块，bootstrap 为 Spring Boot 启动模块。按依赖关系自底向上构建，每个组件 TDD 驱动（Red-Green-Refactor）。

**Tech Stack:** JDK 17, Spring Boot 3.2.3, MyBatis 3.0.3, Druid 1.2.21, Knife4j 4.4.0, Lombok, JUnit 5, Mockito

**Spec:** `docs/superpowers/specs/2026-04-02-common-bootstrap-module-design.md`

**Source docs:** `docs/modules/common/01-功能规格.md`, `02-后端架构.md`, `03-关键组件设计.md`

---

## Task 1: 根 POM + common 聚合 POM

**Files:**
- Create: `pom.xml`
- Create: `common/pom.xml`

- [ ] **Step 1: 创建根 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.bank.branch.platform</groupId>
    <artifactId>branch-platform</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Branch Platform</name>
    <description>银行省分行一体化营销与绩效管理平台</description>

    <modules>
        <module>common</module>
        <module>bootstrap</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.3</spring-boot.version>
        <mybatis-starter.version>3.0.3</mybatis-starter.version>
        <druid-starter.version>1.2.21</druid-starter.version>
        <knife4j.version>4.4.0</knife4j.version>
        <flowable.version>7.0.1</flowable.version>
        <minio.version>8.5.7</minio.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- 内部模块 -->
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>common-web</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>common-trace</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>common-security</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>common-aop</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>common-db</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- 第三方 -->
            <dependency>
                <groupId>org.mybatis.spring.boot</groupId>
                <artifactId>mybatis-spring-boot-starter</artifactId>
                <version>${mybatis-starter.version}</version>
            </dependency>
            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid-spring-boot-3-starter</artifactId>
                <version>${druid-starter.version}</version>
            </dependency>
            <dependency>
                <groupId>com.github.xiaoymin</groupId>
                <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
                <version>${knife4j.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                        <encoding>UTF-8</encoding>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.5</version>
                </plugin>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 创建 common 聚合 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>branch-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>common</artifactId>
    <packaging>pom</packaging>
    <name>Common</name>

    <modules>
        <module>common-web</module>
        <module>common-trace</module>
        <module>common-security</module>
        <module>common-aop</module>
        <module>common-db</module>
    </modules>
</project>
```

- [ ] **Step 3: 验证 POM 结构**

Run: `mvn validate -N` (在根目录，仅验证根 POM 语法)
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add pom.xml common/pom.xml
git commit -m "chore: init root POM and common aggregator POM with BOM"
```

---

## Task 2: common-web — 异常体系 (BizException / AuthException / PermissionDeniedException)

**Files:**
- Create: `common/common-web/pom.xml`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/exception/BizException.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/exception/AuthException.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/exception/PermissionDeniedException.java`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/exception/BizExceptionTest.java`

- [ ] **Step 1: 创建 common-web pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>common</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>common-web</artifactId>
    <name>Common Web</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 写失败测试 — BizException**

```java
package com.bank.branch.platform.common.web.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BizExceptionTest {

    @Test
    void shouldCarryCodeAndMessage() {
        BizException ex = new BizException("USER_001", "用户不存在");
        assertEquals("USER_001", ex.getCode());
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void shouldCarryCodeMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BizException ex = new BizException("DB_001", "数据库异常", cause);
        assertEquals("DB_001", ex.getCode());
        assertEquals("数据库异常", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void authExceptionShouldUseDefaultCode() {
        AuthException ex = new AuthException("登录态失效");
        assertEquals("AUTH_001", ex.getCode());
        assertEquals("登录态失效", ex.getMessage());
        assertInstanceOf(BizException.class, ex);
    }

    @Test
    void authExceptionShouldAcceptCustomCode() {
        AuthException ex = new AuthException("AUTH-40106", "账户已锁定");
        assertEquals("AUTH-40106", ex.getCode());
    }

    @Test
    void permissionDeniedShouldUseDefaultCode() {
        PermissionDeniedException ex = new PermissionDeniedException("无权访问");
        assertEquals("PERM_001", ex.getCode());
        assertInstanceOf(BizException.class, ex);
    }

    @Test
    void permissionDeniedShouldAcceptCustomCode() {
        PermissionDeniedException ex = new PermissionDeniedException("SCOPE_001", "数据范围不足");
        assertEquals("SCOPE_001", ex.getCode());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `cd common/common-web && mvn test -Dtest=BizExceptionTest -pl .`
Expected: FAIL — 类不存在

- [ ] **Step 4: 实现 BizException + AuthException + PermissionDeniedException**

BizException.java — 见 spec 03-关键组件设计.md 第 216-245 行代码，原样实现。
AuthException.java — 见 spec 03-关键组件设计.md 第 253-271 行代码。
PermissionDeniedException.java — 见 spec 03-关键组件设计.md 第 279-297 行代码。

- [ ] **Step 5: 运行测试验证通过**

Run: `cd common/common-web && mvn test -Dtest=BizExceptionTest`
Expected: 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add common/common-web/
git commit -m "feat(common-web): add exception hierarchy - BizException, AuthException, PermissionDeniedException"
```

---

## Task 3: common-web — ResponseWrapper

**Files:**
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/ResponseWrapper.java`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/ResponseWrapperTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.common.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResponseWrapperTest {

    @Test
    void successWithDataShouldSetCodeZero() {
        ResponseWrapper<String> result = ResponseWrapper.success("hello");
        assertEquals("0", result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void successWithoutDataShouldReturnVoid() {
        ResponseWrapper<Void> result = ResponseWrapper.success();
        assertEquals("0", result.getCode());
        assertNull(result.getData());
    }

    @Test
    void pageShouldSetPageResult() {
        PageResult<String> pageResult = PageResult.of(1, 20, 100L, java.util.List.of("a", "b"));
        ResponseWrapper<String> result = ResponseWrapper.page(pageResult);
        assertEquals("0", result.getCode());
        assertNotNull(result.getPage());
        assertEquals(100L, result.getPage().getTotal());
        assertNull(result.getData());
    }

    @Test
    void errorShouldSetCodeAndMessage() {
        ResponseWrapper<?> result = ResponseWrapper.error("AUTH-40101", "用户名或密码错误");
        assertEquals("AUTH-40101", result.getCode());
        assertEquals("用户名或密码错误", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void timestampShouldBeIso8601() {
        ResponseWrapper<Void> result = ResponseWrapper.success();
        // Instant.toString() 产出 ISO 8601 格式
        assertNotNull(result.getTimestamp());
        assertFalse(result.getTimestamp().isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd common/common-web && mvn test -Dtest=ResponseWrapperTest`
Expected: FAIL

- [ ] **Step 3: 实现 ResponseWrapper**

按 spec 03-关键组件设计.md 实现，但 `MdcUtils.getTraceId()` 改为 `org.slf4j.MDC.get("traceId")`（设计决策 #6）。

```java
package com.bank.branch.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;
import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseWrapper<T> {

    private String code;
    private String message;
    private String traceId;
    private T data;
    private PageResult<?> page;
    private String timestamp;

    private ResponseWrapper() {
        this.timestamp = Instant.now().toString();
    }

    public static <T> ResponseWrapper<T> success(T data) {
        ResponseWrapper<T> wrapper = new ResponseWrapper<>();
        wrapper.setCode("0");
        wrapper.setMessage("success");
        wrapper.setTraceId(MDC.get("traceId"));
        wrapper.setData(data);
        return wrapper;
    }

    public static ResponseWrapper<Void> success() {
        return success(null);
    }

    public static <T> ResponseWrapper<T> page(PageResult<T> pageResult) {
        ResponseWrapper<T> wrapper = new ResponseWrapper<>();
        wrapper.setCode("0");
        wrapper.setMessage("success");
        wrapper.setTraceId(MDC.get("traceId"));
        wrapper.setPage(pageResult);
        return wrapper;
    }

    public static ResponseWrapper<?> error(String code, String message) {
        ResponseWrapper<?> wrapper = new ResponseWrapper<>();
        wrapper.setCode(code);
        wrapper.setMessage(message);
        wrapper.setTraceId(MDC.get("traceId"));
        return wrapper;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd common/common-web && mvn test -Dtest=ResponseWrapperTest`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add common/common-web/src/
git commit -m "feat(common-web): add ResponseWrapper with success/page/error factory methods"
```

---

## Task 4: common-web — PageRequest + PageResult

**Files:**
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/PageRequest.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/PageResult.java`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/PageRequestTest.java`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/PageResultTest.java`

- [ ] **Step 1: 写失败测试 — PageRequest**

```java
package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PageRequestTest {

    @Test
    void defaultValuesShouldBeCorrect() {
        PageRequest req = new PageRequest();
        assertEquals(1, req.getPageNo());
        assertEquals(20, req.getPageSize());
        assertEquals("createdTime", req.getSortBy());
        assertEquals("desc", req.getSortDir());
    }

    @Test
    void offsetShouldCalculateCorrectly() {
        PageRequest req = new PageRequest();
        req.setPageNo(3);
        req.setPageSize(10);
        assertEquals(20, req.getOffset());
    }

    @Test
    void offsetForFirstPageShouldBeZero() {
        PageRequest req = new PageRequest();
        assertEquals(0, req.getOffset());
    }

    @Test
    void validateSortByShouldPassForAllowedField() {
        PageRequest req = new PageRequest();
        req.setSortBy("createdTime");
        assertDoesNotThrow(() -> req.validateSortBy(Set.of("createdTime", "name")));
    }

    @Test
    void validateSortByShouldThrowForDisallowedField() {
        PageRequest req = new PageRequest();
        req.setSortBy("hackerField");
        BizException ex = assertThrows(BizException.class,
            () -> req.validateSortBy(Set.of("createdTime", "name")));
        assertEquals("PAGE_001", ex.getCode());
    }
}
```

- [ ] **Step 2: 写失败测试 — PageResult**

```java
package com.bank.branch.platform.common.web;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void ofShouldBuildCorrectResult() {
        PageResult<String> result = PageResult.of(1, 20, 100L, List.of("a", "b"));
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(100L, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    void getTotalPagesShouldCalculateCorrectly() {
        PageResult<String> result = PageResult.of(1, 20, 100L, List.of());
        assertEquals(5, result.getTotalPages());
    }

    @Test
    void getTotalPagesShouldRoundUp() {
        PageResult<String> result = PageResult.of(1, 20, 101L, List.of());
        assertEquals(6, result.getTotalPages());
    }

    @Test
    void getTotalPagesShouldReturnOneForSinglePage() {
        PageResult<String> result = PageResult.of(1, 20, 5L, List.of("a"));
        assertEquals(1, result.getTotalPages());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `cd common/common-web && mvn test -Dtest="PageRequestTest,PageResultTest"`
Expected: FAIL

- [ ] **Step 4: 实现 PageRequest**

按 spec 03-关键组件设计.md 实现，增加 `validateSortBy` 方法：

```java
package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.Set;

@Data
public class PageRequest {

    @Min(value = 1, message = "页码最小为 1")
    private int pageNo = 1;

    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private int pageSize = 20;

    private String sortBy = "createdTime";

    @Pattern(regexp = "^(asc|desc)$", message = "排序方向只能为 asc 或 desc")
    private String sortDir = "desc";

    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }

    /**
     * 校验排序字段是否在白名单内
     * @param allowedFields 允许的排序字段集合
     * @throws BizException 排序字段不在白名单内时抛出
     */
    public void validateSortBy(Set<String> allowedFields) {
        if (sortBy != null && !allowedFields.contains(sortBy)) {
            throw new BizException("PAGE_001",
                String.format("不允许的排序字段: %s，允许的字段: %s", sortBy, allowedFields));
        }
    }
}
```

- [ ] **Step 5: 实现 PageResult** — 按 spec 原样实现

- [ ] **Step 6: 运行测试验证通过**

Run: `cd common/common-web && mvn test -Dtest="PageRequestTest,PageResultTest"`
Expected: 9 tests PASS

- [ ] **Step 7: Commit**

```bash
git add common/common-web/src/
git commit -m "feat(common-web): add PageRequest with sortBy validation and PageResult"
```

---

## Task 5: common-web — RequestValidator + GlobalExceptionHandler + CorsConfig + AutoConfiguration

**Files:**
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/RequestValidator.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/GlobalExceptionHandler.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/CorsConfig.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/config/WebAutoConfiguration.java`
- Create: `common/common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/RequestValidatorTest.java`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 写失败测试 — RequestValidator**

```java
package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidatorTest {

    @Data
    static class TestDTO {
        @NotBlank(message = "名称不能为空")
        private String name;
    }

    @Test
    void shouldPassForValidObject() {
        TestDTO dto = new TestDTO();
        dto.setName("test");
        assertDoesNotThrow(() -> RequestValidator.validate(dto));
    }

    @Test
    void shouldThrowBizExceptionForInvalidObject() {
        TestDTO dto = new TestDTO();
        BizException ex = assertThrows(BizException.class, () -> RequestValidator.validate(dto));
        assertEquals("VALID_001", ex.getCode());
        assertTrue(ex.getMessage().contains("名称不能为空"));
    }
}
```

- [ ] **Step 2: 写失败测试 — GlobalExceptionHandler**

```java
package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBizExceptionShouldReturn200WithErrorCode() {
        BizException ex = new BizException("USER_001", "用户不存在");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleBizException(ex);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("USER_001", resp.getBody().getCode());
    }

    @Test
    void handleAuthExceptionShouldReturn401() {
        AuthException ex = new AuthException("登录态失效");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleAuthException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("AUTH_001", resp.getBody().getCode());
    }

    @Test
    void handlePermissionDeniedShouldReturn403() {
        PermissionDeniedException ex = new PermissionDeniedException("无权访问");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handlePermissionDeniedException(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("PERM_001", resp.getBody().getCode());
    }

    @Test
    void handleMethodNotSupportedShouldReturn405() {
        HttpRequestMethodNotSupportedException ex =
            new HttpRequestMethodNotSupportedException("PATCH");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleMethodNotSupported(ex);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
    }

    @Test
    void handleUnknownExceptionShouldReturn500() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("SYS_500", resp.getBody().getCode());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `cd common/common-web && mvn test -Dtest="RequestValidatorTest,GlobalExceptionHandlerTest"`
Expected: FAIL

- [ ] **Step 4: 实现 RequestValidator**

```java
package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 参数校验增强工具
 * 封装 jakarta.validation，校验失败自动抛出 BizException
 */
public class RequestValidator {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private RequestValidator() {}

    public static <T> void validate(T object) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
            throw new BizException("VALID_001", message);
        }
    }
}
```

- [ ] **Step 5: 实现 GlobalExceptionHandler**

```java
package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ResponseWrapper<?>> handleAuthException(AuthException ex) {
        log.warn("认证异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ResponseWrapper.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ResponseWrapper<?>> handlePermissionDeniedException(PermissionDeniedException ex) {
        log.warn("权限异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ResponseWrapper.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ResponseWrapper<?>> handleBizException(BizException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok(ResponseWrapper.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseWrapper<?>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ResponseWrapper.error("VALID_001", msg));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
            .body(ResponseWrapper.error("VALID_002", "缺少请求参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ResponseWrapper.error("SYS_405", "不支持的请求方法: " + ex.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ResponseWrapper.error("SYS_415", "不支持的媒体类型"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseWrapper<?>> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseWrapper.error("SYS_500", "系统繁忙，请稍后重试"));
    }
}
```

- [ ] **Step 6: 实现 CorsConfig + WebAutoConfiguration + AutoConfiguration.imports**

CorsConfig:
```java
package com.bank.branch.platform.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("dev")
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("*")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
```

WebAutoConfiguration:
```java
package com.bank.branch.platform.common.web.config;

import com.bank.branch.platform.common.web.CorsConfig;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorsConfig.class})
public class WebAutoConfiguration {
}
```

AutoConfiguration.imports:
```
com.bank.branch.platform.common.web.config.WebAutoConfiguration
```

- [ ] **Step 7: 运行全部 common-web 测试**

Run: `cd common/common-web && mvn test`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git add common/common-web/
git commit -m "feat(common-web): add RequestValidator, GlobalExceptionHandler, CorsConfig, WebAutoConfiguration"
```

---

## Task 6: common-trace — MdcUtils + TraceContext + TraceIdFilter + TraceIdInterceptor

**Files:**
- Create: `common/common-trace/pom.xml`
- Create: `common/common-trace/src/main/java/com/bank/branch/platform/common/trace/MdcUtils.java`
- Create: `common/common-trace/src/main/java/com/bank/branch/platform/common/trace/TraceContext.java`
- Create: `common/common-trace/src/main/java/com/bank/branch/platform/common/trace/TraceIdFilter.java`
- Create: `common/common-trace/src/main/java/com/bank/branch/platform/common/trace/TraceIdInterceptor.java`
- Create: `common/common-trace/src/main/java/com/bank/branch/platform/common/trace/config/TraceAutoConfiguration.java`
- Create: `common/common-trace/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `common/common-trace/src/test/java/com/bank/branch/platform/common/trace/MdcUtilsTest.java`
- Create: `common/common-trace/src/test/java/com/bank/branch/platform/common/trace/TraceIdFilterTest.java`
- Create: `common/common-trace/src/test/java/com/bank/branch/platform/common/trace/TraceIdInterceptorTest.java`

- [ ] **Step 1: 创建 common-trace pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>common</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>common-trace</artifactId>
    <name>Common Trace</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 写失败测试 — MdcUtils**

```java
package com.bank.branch.platform.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class MdcUtilsTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void putAndGetTraceId() {
        MdcUtils.putTraceId("abc123");
        assertEquals("abc123", MdcUtils.getTraceId());
    }

    @Test
    void removeTraceId() {
        MdcUtils.putTraceId("abc123");
        MdcUtils.removeTraceId();
        assertNull(MdcUtils.getTraceId());
    }

    @Test
    void traceIdKeyShouldBeConstant() {
        assertEquals("traceId", MdcUtils.TRACE_ID_KEY);
    }
}
```

- [ ] **Step 3: 写失败测试 — TraceIdFilter**

```java
package com.bank.branch.platform.common.trace;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void shouldGenerateTraceIdAndCleanAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 在 filterChain 内部能读到 traceId
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.Servlet() {
            @Override public void init(jakarta.servlet.ServletConfig config) {}
            @Override public jakarta.servlet.ServletConfig getServletConfig() { return null; }
            @Override public void service(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertNotNull(MDC.get("traceId"));
                assertEquals(16, MDC.get("traceId").length());
            }
            @Override public String getServletInfo() { return null; }
            @Override public void destroy() {}
        });
        filter.doFilter(request, response, chain);
        // 请求结束后 MDC 应清理
        assertNull(MDC.get("traceId"));
    }

    @Test
    void shouldReuseTraceIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "external12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.Servlet() {
            @Override public void init(jakarta.servlet.ServletConfig config) {}
            @Override public jakarta.servlet.ServletConfig getServletConfig() { return null; }
            @Override public void service(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertEquals("external12345678", MDC.get("traceId"));
            }
            @Override public String getServletInfo() { return null; }
            @Override public void destroy() {}
        });
        filter.doFilter(request, response, chain);
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `cd common/common-trace && mvn test`
Expected: FAIL

- [ ] **Step 5: 实现全部 common-trace 组件**

MdcUtils.java:
```java
package com.bank.branch.platform.common.trace;

import org.slf4j.MDC;

public class MdcUtils {
    public static final String TRACE_ID_KEY = "traceId";
    private MdcUtils() {}
    public static void putTraceId(String traceId) { MDC.put(TRACE_ID_KEY, traceId); }
    public static String getTraceId() { return MDC.get(TRACE_ID_KEY); }
    public static void removeTraceId() { MDC.remove(TRACE_ID_KEY); }
}
```

TraceContext.java:
```java
package com.bank.branch.platform.common.trace;

import java.time.Instant;

public record TraceContext(String traceId, String source, Instant startTime) {}
```

TraceIdFilter.java:
```java
package com.bank.branch.platform.common.trace;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.io.IOException;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter implements Filter {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String traceId = httpReq.getHeader(HEADER_TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            MdcUtils.putTraceId(traceId);
            chain.doFilter(request, response);
        } finally {
            MdcUtils.removeTraceId();
        }
    }
}
```

TraceIdInterceptor.java — 用于 WebMvcConfigurer 注册，将 traceId 写入响应头以便客户端追踪：
```java
package com.bank.branch.platform.common.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class TraceIdInterceptor implements HandlerInterceptor {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = MdcUtils.getTraceId();
        if (traceId != null) {
            response.setHeader(HEADER_TRACE_ID, traceId);
        }
        return true;
    }
}
```

TraceAutoConfiguration.java + imports 文件。

- [ ] **Step 6: 运行测试验证通过**

Run: `cd common/common-trace && mvn test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add common/common-trace/
git commit -m "feat(common-trace): add MdcUtils, TraceIdFilter, TraceIdInterceptor, TraceContext"
```

---

## Task 7: common-security — 枚举 + 注解 + 上下文

**Files:**
- Create: `common/common-security/pom.xml`
- Create: 枚举类 BizType / BizAction / DataScopeType
- Create: 注解 @BizAuth
- Create: CurrentUserContext / DataScopeContext
- Create: 对应测试类

- [ ] **Step 1: 创建 common-security pom.xml**（依赖 common-web）

- [ ] **Step 2: 写失败测试 — 枚举完整性**

```java
package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BizTypeTest {
    @Test
    void shouldHave17Values() { assertEquals(17, BizType.values().length); }
    @Test
    void navShouldHaveCorrectCode() { assertEquals("NAV", BizType.NAV.getCode()); }
}

class BizActionTest {
    @Test
    void shouldHave15Values() { assertEquals(15, BizAction.values().length); }
    @Test
    void readShouldHaveCorrectCode() { assertEquals("READ", BizAction.READ.getCode()); }
}

class DataScopeTypeTest {
    @Test
    void shouldHave7Values() { assertEquals(7, DataScopeType.values().length); }
    @Test
    void selfCreatedShouldHaveCorrectCode() { assertEquals("SELF_CREATED", DataScopeType.SELF_CREATED.getCode()); }
}
```

- [ ] **Step 3: 写失败测试 — CurrentUserContext**

```java
package com.bank.branch.platform.common.security.context;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CurrentUserContextTest {
    @Test
    void hasRoleShouldReturnTrueForExistingRole() {
        var ctx = new CurrentUserContext("E001", "ORG001", Set.of("R1", "R2"), Set.of(), false);
        assertTrue(ctx.hasRole("R1"));
        assertFalse(ctx.hasRole("R3"));
    }
    @Test
    void hasAnyRoleShouldMatchPartially() {
        var ctx = new CurrentUserContext("E001", "ORG001", Set.of("R1"), Set.of(), false);
        assertTrue(ctx.hasAnyRole(Set.of("R1", "R99")));
        assertFalse(ctx.hasAnyRole(Set.of("R99")));
    }
}
```

- [ ] **Step 4: 写失败测试 — DataScopeContext**

```java
package com.bank.branch.platform.common.security.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataScopeContextTest {
    @AfterEach
    void cleanup() { DataScopeContext.clear(); }

    @Test
    void setAndCurrentShouldWork() {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("E001");
        DataScopeContext.set(ctx);
        assertNotNull(DataScopeContext.current());
        assertEquals("E001", DataScopeContext.current().getEmpId());
    }
    @Test
    void clearShouldRemoveContext() {
        DataScopeContext.set(new DataScopeContext());
        DataScopeContext.clear();
        assertNull(DataScopeContext.current());
    }
}
```

- [ ] **Step 5: 运行测试验证失败 → 实现所有组件 → 运行测试验证通过**

- [ ] **Step 6: Commit**

```bash
git add common/common-security/
git commit -m "feat(common-security): add enums, @BizAuth, CurrentUserContext, DataScopeContext"
```

---

## Task 8: common-security — ObjectMeta + ObjectMetaRegistry

**Files:**
- Create: `common/common-security/src/main/java/com/bank/branch/platform/common/security/meta/ObjectMeta.java`
- Create: `common/common-security/src/main/java/com/bank/branch/platform/common/security/meta/ObjectMetaRegistry.java`
- Test: `ObjectMetaTest.java`, `ObjectMetaRegistryTest.java`

- [ ] **Step 1: 写失败测试 — ObjectMeta**

```java
package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMetaTest {
    @Test
    void validateScopeShouldPassForSupportedScope() {
        ObjectMeta meta = new ObjectMeta("LEAD", "cust_lead", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.SELF_CREATED, DataScopeType.ALL), null);
        assertDoesNotThrow(() -> meta.validateScope(DataScopeType.SELF_CREATED));
    }
    @Test
    void validateScopeShouldThrowForUnsupportedScope() {
        ObjectMeta meta = new ObjectMeta("LEAD", "cust_lead", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.SELF_CREATED), null);
        assertThrows(PermissionDeniedException.class, () -> meta.validateScope(DataScopeType.ALL));
    }
}
```

- [ ] **Step 2: 写失败测试 — ObjectMetaRegistry**

```java
package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMetaRegistryTest {
    private ObjectMetaRegistry registry;
    private ObjectMeta leadMeta;

    @BeforeEach
    void setUp() {
        registry = new ObjectMetaRegistry();
        leadMeta = new ObjectMeta("LEAD", "cust_lead", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.ALL), null);
    }
    @Test
    void registerAndGetShouldWork() {
        registry.register(leadMeta);
        assertTrue(registry.get("LEAD").isPresent());
    }
    @Test
    void duplicateRegisterShouldThrow() {
        registry.register(leadMeta);
        assertThrows(IllegalStateException.class, () -> registry.register(leadMeta));
    }
    @Test
    void getRequiredShouldThrowForMissing() {
        assertThrows(BizException.class, () -> registry.getRequired("NONEXIST"));
    }
    @Test
    void getMissingShouldReturnEmpty() {
        assertTrue(registry.get("NONEXIST").isEmpty());
    }
}
```

- [ ] **Step 3: 运行测试验证失败 → 实现 → 验证通过**

- [ ] **Step 4: Commit**

```bash
git add common/common-security/src/
git commit -m "feat(common-security): add ObjectMeta and ObjectMetaRegistry"
```

---

## Task 9: common-security — SensitiveDataMasker + SignatureUtils + AutoConfiguration

**Files:**
- Create: SensitiveDataMasker.java, SignatureUtils.java, SecurityAutoConfiguration.java, imports 文件
- Test: SensitiveDataMaskerTest.java, SignatureUtilsTest.java

- [ ] **Step 1: 写失败测试 — SensitiveDataMasker**

```java
package com.bank.branch.platform.common.security.masker;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskerTest {
    @Test
    void maskPhoneShouldWork() { assertEquals("138****5678", SensitiveDataMasker.maskPhone("13812345678")); }
    @Test
    void maskPhoneShortInputShouldReturnStars() { assertEquals("***", SensitiveDataMasker.maskPhone("123")); }
    @Test
    void maskPhoneNullShouldReturnStars() { assertEquals("***", SensitiveDataMasker.maskPhone(null)); }
    @Test
    void maskIdCardShouldWork() { assertEquals("110***********1234", SensitiveDataMasker.maskIdCard("110101199001011234")); }
    @Test
    void maskBankAccountShouldWork() { assertEquals("****7890", SensitiveDataMasker.maskBankAccount("6222021234567890")); }
    @Test
    void maskAmountShouldReturnStars() { assertEquals("***.**", SensitiveDataMasker.maskAmount(new BigDecimal("12345.67"))); }
    @Test
    void maskAmountNullShouldReturnStars() { assertEquals("***.**", SensitiveDataMasker.maskAmount(null)); }
}
```

- [ ] **Step 2: 写失败测试 — SignatureUtils**

```java
package com.bank.branch.platform.common.security.sign;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SignatureUtilsTest {
    @Test
    void signAndVerifyShouldMatch() {
        Map<String, String> params = Map.of("userId", "E001", "action", "login");
        String sig = SignatureUtils.sign(params, 1000L, "nonce1", "secret123");
        assertTrue(SignatureUtils.verify(params, 1000L, "nonce1", "secret123", sig));
    }
    @Test
    void verifyShouldFailForTamperedParams() {
        Map<String, String> params = Map.of("userId", "E001");
        String sig = SignatureUtils.sign(params, 1000L, "nonce1", "secret123");
        assertFalse(SignatureUtils.verify(Map.of("userId", "E002"), 1000L, "nonce1", "secret123", sig));
    }
    @Test
    void signShouldBeDeterministic() {
        Map<String, String> params = Map.of("a", "1", "b", "2");
        String sig1 = SignatureUtils.sign(params, 100L, "n", "key");
        String sig2 = SignatureUtils.sign(params, 100L, "n", "key");
        assertEquals(sig1, sig2);
    }
}
```

- [ ] **Step 3: 运行测试验证失败 → 实现全部（按 spec 03-关键组件设计.md，SignatureUtils 异常改为 BizException） → 验证通过**

- [ ] **Step 4: 实现 SecurityAutoConfiguration + imports**

```java
package com.bank.branch.platform.common.security.config;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.meta.ObjectMetaRegistry;
import jakarta.servlet.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import java.io.IOException;

@AutoConfiguration
public class SecurityAutoConfiguration {

    @Bean
    public ObjectMetaRegistry objectMetaRegistry() {
        return new ObjectMetaRegistry();
    }

    @Bean
    public FilterRegistrationBean<Filter> dataScopeCleanupFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                try {
                    chain.doFilter(request, response);
                } finally {
                    DataScopeContext.clear();
                }
            }
        });
        reg.setOrder(Ordered.LOWEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
```

- [ ] **Step 5: 运行全部 common-security 测试**

Run: `cd common/common-security && mvn test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add common/common-security/
git commit -m "feat(common-security): add SensitiveDataMasker, SignatureUtils, SecurityAutoConfiguration"
```

---

## Task 10: common-aop — 注解 + 事件模型 + Handler 接口

**Files:**
- Create: `common/common-aop/pom.xml`
- Create: annotation/AuditLog.java, event/AuditLogEvent.java, handler/AuditLogHandler.java, handler/NoopAuditLogHandler.java
- Test: AuditLogEventTest.java

- [ ] **Step 1: 创建 common-aop pom.xml**（依赖 common-trace + common-security）

- [ ] **Step 2: 写失败测试 — AuditLogEvent**

```java
package com.bank.branch.platform.common.aop.event;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogEventTest {
    @Test
    void shouldHoldAllFields() {
        Instant now = Instant.now();
        AuditLogEvent event = new AuditLogEvent("DELETE", "CUSTOMER", "C001", "BK001",
            "E001", "ORG001", now, "127.0.0.1", "Mozilla/5.0", "{}", "{}", "客户迁移");
        assertEquals("DELETE", event.action());
        assertEquals("C001", event.resourceId());
        assertEquals("E001", event.operatorEmpId());
    }
}
```

- [ ] **Step 3: 运行测试验证失败 → 实现全部 → 验证通过**

- [ ] **Step 4: Commit**

```bash
git add common/common-aop/
git commit -m "feat(common-aop): add @AuditLog, AuditLogEvent, AuditLogHandler, NoopAuditLogHandler"
```

---

## Task 11: common-aop — 三个切面 + AutoConfiguration

**Files:**
- Create: ApiLogAspect.java, MethodTimingAspect.java, AuditLogAspect.java, config/AopAutoConfiguration.java, imports
- Test: ApiLogAspectTest.java, MethodTimingAspectTest.java, AuditLogAspectTest.java

- [ ] **Step 1: 写失败测试 — MethodTimingAspect（最易单测的切面）**

```java
package com.bank.branch.platform.common.aop;

import org.junit.jupiter.api.Test;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MethodTimingAspectTest {
    @Test
    void shouldProceedAndReturnResult() throws Throwable {
        MethodTimingAspect aspect = new MethodTimingAspect();
        aspect.setWarnThresholdMs(500);
        aspect.setErrorThresholdMs(5000);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.toShortString()).thenReturn("TestService.doSomething()");
        when(pjp.proceed()).thenReturn("result");
        Object result = aspect.around(pjp);
        assertEquals("result", result);
        verify(pjp).proceed();
    }
}
```

- [ ] **Step 2: 写失败测试 — AuditLogAspect**

```java
package com.bank.branch.platform.common.aop;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogAspectTest {

    @Test
    void shouldDelegateToHandler() throws Throwable {
        AuditLogHandler handler = mock(AuditLogHandler.class);
        AuditLogAspect aspect = new AuditLogAspect(handler);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(AuditLogAspectTest.class
            .getDeclaredMethod("annotatedMethod"));
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp);
        assertEquals("ok", result);
        verify(handler).handle(any(AuditLogEvent.class));
    }

    @AuditLog(action = "TEST", resourceType = "UNIT")
    void annotatedMethod() {}
}
```

- [ ] **Step 3: 运行测试验证失败 → 实现三个切面 + AopAutoConfiguration → 验证通过**

- [ ] **Step 4: Commit**

```bash
git add common/common-aop/
git commit -m "feat(common-aop): add ApiLogAspect, MethodTimingAspect, AuditLogAspect, AopAutoConfiguration"
```

---

## Task 12: common-db — PageInterceptor + AuditFieldFiller + SlowSqlInterceptor + DruidConfig + AutoConfiguration

**Files:**
- Create: `common/common-db/pom.xml`
- Create: 全部 common-db 实现类和测试类

- [ ] **Step 1: 创建 common-db pom.xml**（依赖 common-security + mybatis + druid）

- [ ] **Step 2: 写失败测试 — PageInterceptor**

```java
package com.bank.branch.platform.common.db;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageInterceptorTest {
    @Test
    void sanitizeSortFieldShouldAllowValidField() {
        assertTrue(PageInterceptor.isSafeSortField("created_time"));
        assertTrue(PageInterceptor.isSafeSortField("userName"));
    }
    @Test
    void sanitizeSortFieldShouldRejectInjection() {
        assertFalse(PageInterceptor.isSafeSortField("1; DROP TABLE--"));
        assertFalse(PageInterceptor.isSafeSortField("field OR 1=1"));
    }
}
```

- [ ] **Step 3: 写失败测试 — SlowSqlInterceptor**

```java
package com.bank.branch.platform.common.db;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlowSqlInterceptorTest {
    @Test
    void defaultThresholdShouldBe5000() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        assertEquals(5000L, interceptor.getThresholdMs());
    }
    @Test
    void shouldAcceptCustomThreshold() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        interceptor.setThresholdMs(3000L);
        assertEquals(3000L, interceptor.getThresholdMs());
    }
}
```

- [ ] **Step 4: 运行测试验证失败 → 实现全部 common-db 组件 → 验证通过**

实现要点：
- PageInterceptor: MyBatis Interceptor，拦截 `Executor.query`，用正则 `^[a-zA-Z0-9_]+$` 校验 sortBy 字段
- AuditFieldFiller: MyBatis Interceptor，拦截 `Executor.update`，通过反射填充审计字段
- SlowSqlInterceptor: MyBatis Interceptor，拦截 `StatementHandler.query` 和 `StatementHandler.update`，计时并 WARN
- DruidConfig: `@Configuration` + `@ConfigurationProperties("spring.datasource.druid")`
- DbAutoConfiguration: `@AutoConfiguration` 注册所有 Bean

- [ ] **Step 5: 运行全部 common-db 测试**

Run: `cd common/common-db && mvn test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add common/common-db/
git commit -m "feat(common-db): add PageInterceptor, AuditFieldFiller, SlowSqlInterceptor, DruidConfig"
```

---

## Task 13: bootstrap — 启动模块 + 配置文件 + 集成验证

**Files:**
- Create: `bootstrap/pom.xml`
- Create: `bootstrap/src/main/java/com/bank/branch/platform/BranchPlatformApplication.java`
- Create: `bootstrap/src/main/resources/application.yml`
- Create: `bootstrap/src/main/resources/application-dev.yml`
- Create: `bootstrap/src/main/resources/logback-spring.xml`
- Create: `bootstrap/src/test/java/com/bank/branch/platform/BranchPlatformApplicationTest.java`

- [ ] **Step 1: 创建 bootstrap pom.xml**

依赖：所有 common 子模块 + spring-boot-starter-data-redis + spring-session-data-redis + knife4j + mysql-connector-j。spring-boot-maven-plugin 配置 repackage。

- [ ] **Step 2: 创建 BranchPlatformApplication.java**

```java
package com.bank.branch.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BranchPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(BranchPlatformApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml** — 按 spec 7.2 完整内容

- [ ] **Step 4: 创建 application-dev.yml** — 按 spec 7.3 完整内容

- [ ] **Step 5: 创建 logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n"/>

    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>${LOG_PATTERN}</pattern>
                <charset>UTF-8</charset>
            </encoder>
        </appender>
        <logger name="com.bank.branch.platform" level="DEBUG"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="!dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>${LOG_PATTERN}</pattern>
                <charset>UTF-8</charset>
            </encoder>
        </appender>
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>logs/branch-platform.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>logs/branch-platform.%d{yyyy-MM-dd}.log</fileNamePattern>
                <maxHistory>30</maxHistory>
            </rollingPolicy>
            <encoder>
                <pattern>${LOG_PATTERN}</pattern>
                <charset>UTF-8</charset>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

- [ ] **Step 6: 写集成测试**

```java
package com.bank.branch.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class BranchPlatformApplicationTest {

    @Test
    void contextLoads() {
        // ApplicationContext 加载成功即通过
    }
}
```

注意：此测试需要 MySQL 和 Redis 可用（或使用嵌入式替代）。如果 CI 环境没有外部依赖，可通过 `@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:test", ...})` 或 `@TestPropertySource` 隔离。

- [ ] **Step 7: 全量编译验证**

Run: `mvn clean install` (根目录)
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add bootstrap/
git commit -m "feat(bootstrap): add Spring Boot application entry, config files, and context load test"
```

---

## Task 14: 全量验证 + 最终提交

- [ ] **Step 1: 从根目录运行全部测试**

Run: `mvn clean test`
Expected: ALL modules BUILD SUCCESS, all tests PASS

- [ ] **Step 2: 验证 mvn clean install**

Run: `mvn clean install`
Expected: BUILD SUCCESS

- [ ] **Step 3: 总结提交（如果有遗漏修复）**

Run: `git log --oneline -15` 确认所有 commit 记录完整。
