# Portal-Content-Center V1 Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the portal-content-center module's V1 first slice — 10 REST endpoints (workspace aggregation A.1-A.3 + product catalog D.1-D.7) using TDD with Service unit tests + Mapper Testcontainers integration tests + Controller MockMvc integration tests.

**Architecture:** Maven module following auth/governance/workflow patterns — Entity → Mapper XML → Service → Facade → Controller. Cross-module Adapter pattern for unimplemented dependencies (MetricApi, WorkflowQueryApi). `@TransactionalEventListener(AFTER_COMMIT)` for product responsible sync (in-process events; outbox upgrade in V2).

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, MySQL 8.0, Testcontainers 1.19.7, EasyExcel 3.3.x, JUnit 5, Mockito, AssertJ

**Reference files:**
- Spec: `docs/superpowers/specs/2026-04-11-portal-content-center-v1-slice-design.md`
- 接口契约权威: `docs/modules/portal-content-center/03-接口设计与报文.md`
- 并发事务策略: `docs/modules/portal-content-center/06-并发与事务策略.md`
- 跨模块依赖: `docs/modules/portal-content-center/09-依赖契约摘要.md`
- 表结构 DDL: `docs/modules/portal-content-center/05-表结构DDL.md`
- DDL 源: `docs/schema/ddl-portal.sql`
- 共享规范: `docs/common-dev-guide.md`
- 参考模块: `system-governance-center/`、`auth-permission-center/`（follow same patterns exactly）

**IMPORTANT - Before each task:**
1. Read the spec sections referenced in the task
2. Verify the actual method signatures of cross-module APIs by reading the source files (don't trust your memory)
3. Follow `system-governance-center` patterns for Entity/Mapper/Service (governance is the most recent reference module)
4. Use the `superpowers:test-driven-development` skill for every coding task — write the failing test FIRST, run it to verify red, then implement minimal code to green, then refactor

**IMPORTANT - Common module classes to use:**

| Class | Package | Purpose |
|---|---|---|
| `ResponseWrapper<T>` | `com.bank.branch.platform.common.web` | Standard API response envelope (`success(data)` / `success()` / `error(code, msg)`) |
| `PageRequest` / `PageResult<T>` | `com.bank.branch.platform.common.web` | Standard pagination |
| `BizException` | `com.bank.branch.platform.common.web.exception` | Business exception (`new BizException(code, message)`) |
| `@BizAuth` | `com.bank.branch.platform.common.security.annotation` | Method-level RBAC annotation |
| `BizType` enum | `com.bank.branch.platform.common.security.enums` | `PRODUCT`, `ADDRBOOK`, etc. |
| `BizAction` enum | `com.bank.branch.platform.common.security.enums` | `READ` / `LIST` / `WRITE` / `DELETE` / `EXPORT` |
| `DataScopeContext` | `com.bank.branch.platform.auth.api.dto` (record) | Holds `scopeType / empId / orgCode / orgSubtreeCodes / bizType / action` |
| `DataScopeType` enum | `com.bank.branch.platform.common.security.enums` | `SELF_CREATED / SELF / SELF_ASSIGNED / ORG / ORG_SUBTREE / ALL / WORKFLOW_PARTICIPANT` |
| `CurrentUserApi` | `com.bank.branch.platform.auth.api` | `getCurrentEmpId()` / `getCurrentOrgCode()` / `isSystemAdmin()` |
| `BizScopeApi` | `com.bank.branch.platform.auth.api` | `buildScopeContext(empId, BizType, BizAction)` / `checkWritePermission(...)` |
| `OrgApi` | `com.bank.branch.platform.auth.api` | `getOrg(orgCode)` / `getOrgSubtreeCodes(orgCode)` |
| `DictApi` | `com.bank.branch.platform.governance.api` | `getDictLabel(type, code)` / `isValidDictValue(type, code)` |
| `FileApi` | `com.bank.branch.platform.governance.api` | `getDownloadUrl(fileId)` / `bindFile(bizType, bizId, fileObjectId, fileRole)` |
| `NotifyApi` | `com.bank.branch.platform.governance.api` | `countUnread(empId)` / `queryNotifications(empId, isRead, page)` |
| `AuditApi` | `com.bank.branch.platform.governance.api` | `log(cmd)` |
| `WorkflowApi` | `com.bank.branch.platform.workflow.api` | `startProcess(cmd)` (NOT used in this slice — workflow is mocked) |

**Verified facts (source-checked at spec r3 time)**:
- `WorkflowQueryApi` does NOT exist in workflow-center → use `portal.adapter.WorkflowQueryApi` placeholder
- `MetricApi` does NOT exist (performance-engine-center not created) → use `portal.adapter.MetricApi` placeholder
- `FileApi` has NO `getFileInfo / attachBizRelation / detachBizRelation` → use `getDownloadUrl` for existence check + `bindFile` (idempotent) for attach
- `sys_event_outbox` table does NOT exist → V1 uses `@TransactionalEventListener(AFTER_COMMIT)` only
- `DataScopeType` enum has `ORG` (NOT `ORG_SELF`) — verified at `common-security/.../DataScopeType.java:16`

---

## Phase 0: Module Scaffolding & Test Infrastructure

### Task 0.1: Create portal-content-center Maven Module + Update Parent POM

**Files:**
- Create: `portal-content-center/pom.xml`
- Create: `portal-content-center/CLAUDE.md`
- Modify: `pom.xml` (root) — add module + dependencyManagement
- Modify: `bootstrap/pom.xml` — add portal-content-center dependency

**Context:** Follow `system-governance-center/pom.xml` exactly. The root pom.xml needs `portal-content-center` added between `<module>workflow-center</module>` and `<module>bootstrap</module>` (or after workflow-center).

- [ ] **Step 1:** Read `system-governance-center/pom.xml` and root `pom.xml` to understand the dependency pattern
- [ ] **Step 2:** Create `portal-content-center/pom.xml` with these dependencies:
  - `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
  - `auth-permission-center`
  - `system-governance-center`
  - `workflow-center`
  - `spring-boot-starter-web`
  - `mybatis-spring-boot-starter`
  - `spring-boot-starter-data-redis`
  - `com.alibaba:easyexcel:3.3.4` (NEW dependency, declare version in root pom.xml dependencyManagement)
  - `lombok` (provided)
  - Test deps: `spring-boot-starter-test`, `mybatis-spring-boot-starter-test`, `testcontainers-mysql:1.19.7`, `testcontainers-junit-jupiter:1.19.7`, `spring-security-test`
- [ ] **Step 3:** Modify root `pom.xml`:
  - Add `<module>portal-content-center</module>` (after workflow-center, before bootstrap)
  - Add portal-content-center to `<dependencyManagement>` section
  - Add `<easyexcel.version>3.3.4</easyexcel.version>` property and the EasyExcel dependency to dependencyManagement
  - Add `<testcontainers.version>1.19.7</testcontainers.version>` property and testcontainers BOM to dependencyManagement
- [ ] **Step 4:** Modify `bootstrap/pom.xml` — add portal-content-center as a dependency
- [ ] **Step 5:** Create `portal-content-center/CLAUDE.md` (minimal — module overview, package structure, key APIs reference)
- [ ] **Step 6:** Verify compilation: `mvn compile -pl portal-content-center -am -q`
- [ ] **Step 7:** Commit: `feat(portal): Task 0.1 - 创建 portal-content-center Maven 模块骨架`

### Task 0.2: Package Structure + PortalErrorCodes Enum

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/enums/PortalErrorCodes.java`
- Create: directory skeletons under `portal-content-center/src/main/java/com/bank/branch/platform/portal/{api,api/dto,adapter,adapter/dto,controller,facade,service,listener,mapper,entity,enums,convert,config,typehandler}` (use empty placeholder Java files or `.gitkeep`)
- Create: `portal-content-center/src/main/resources/mapper/.gitkeep`

**Context:** PortalErrorCodes is used by all subsequent tasks. Follow `auth-permission-center/.../enums/AuthErrorCode.java` pattern (enum with `code` + `message` fields).

- [ ] **Step 1:** Read `auth-permission-center/src/main/java/com/bank/branch/platform/auth/enums/AuthErrorCode.java` for the enum pattern
- [ ] **Step 2:** Create `PortalErrorCodes.java` enum with these constants (V1 used + 1 newly added):

```java
package com.bank.branch.platform.portal.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * portal-content-center V1 错误码常量
 * 权威来源：docs/modules/portal-content-center/03-接口设计与报文.md §I
 * + 1 个 V1 spec 新增码 PORTAL-40905（来自 06 §3.4 的"产品仍被员工引用"场景）
 */
@Getter
@AllArgsConstructor
public enum PortalErrorCodes {

    PRODUCT_NOT_FOUND          ("PORTAL-40003", "产品不存在"),
    NO_RIGHT_TO_PRODUCT_DEPT   ("PORTAL-40302", "无权维护非本机构产品"),
    PRODUCT_CODE_DUPLICATE     ("PORTAL-40901", "产品代码已存在"),
    EMPLOYEE_RESIGNED          ("PORTAL-40902", "员工已离职"),
    PRODUCT_STILL_REFERRED     ("PORTAL-40905", "产品仍被员工引用，不可删除"),
    PARAM_INVALID              ("PORTAL-42200", "参数校验失败"),
    FILE_OBJECT_NOT_FOUND      ("PORTAL-42203", "附件对象不存在"),
    EXPORT_ROWS_LIMIT_EXCEEDED ("PORTAL-42207", "导出行数超过限制"),
    EXPORT_FILE_GENERATE_FAIL  ("PORTAL-50002", "导出文件生成失败");

    private final String code;
    private final String message;
}
```

- [ ] **Step 3:** Create empty `package-info.java` files in each subpackage (or `.gitkeep` markers) so the directories are committed
- [ ] **Step 4:** Verify compilation: `mvn compile -pl portal-content-center -q`
- [ ] **Step 5:** Commit: `feat(portal): Task 0.2 - 包结构骨架 + PortalErrorCodes 枚举`

### Task 0.3: PortalMyBatisConfig + PortalAsyncConfig

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/config/PortalMyBatisConfig.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/config/PortalAsyncConfig.java`

**Context:**
- `PortalMyBatisConfig` uses `@MapperScan` to scan portal mappers (follow `system-governance-center` pattern)
- `PortalAsyncConfig` defines a dedicated `portalAggregateExecutor` for A.1 workspace aggregation (5-way parallel)

- [ ] **Step 1:** Read `system-governance-center/src/main/java/.../governance/config/GovernanceMyBatisConfig.java` (or equivalent) for the pattern
- [ ] **Step 2:** Create `PortalMyBatisConfig.java`:

```java
package com.bank.branch.platform.portal.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * portal-content-center MyBatis 配置
 * 扫描 portal 模块下的所有 Mapper 接口
 */
@Configuration
@MapperScan(basePackages = "com.bank.branch.platform.portal.mapper")
public class PortalMyBatisConfig {
}
```

- [ ] **Step 3:** Create `PortalAsyncConfig.java`:

```java
package com.bank.branch.platform.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * portal-content-center 异步线程池配置
 * portalAggregateExecutor: A.1 工作台聚合的 5 路并行查询专用线程池
 */
@Configuration
public class PortalAsyncConfig {

    @Bean(name = "portalAggregateExecutor")
    public Executor portalAggregateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("portal-agg-");
        executor.setKeepAliveSeconds(60);
        // queue 满时降级到调用线程，避免请求被静默丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 4:** Verify compilation: `mvn compile -pl portal-content-center -q`
- [ ] **Step 5:** Commit: `feat(portal): Task 0.3 - PortalMyBatisConfig + PortalAsyncConfig`

### Task 0.4: Generate test-ddl-*-clean.sql + refresh-test-ddl.sh

**Files:**
- Create: `portal-content-center/src/test/resources/sql/test-ddl-auth-clean.sql`
- Create: `portal-content-center/src/test/resources/sql/test-ddl-governance-clean.sql`
- Create: `portal-content-center/src/test/resources/sql/test-ddl-portal-clean.sql`
- Create: `portal-content-center/src/test/resources/sql/portal-test-data.sql` (empty placeholder)
- Create: `portal-content-center/scripts/refresh-test-ddl.sh`

**Context (CRITICAL — read carefully):**
- The Testcontainers MySQL needs DDL to bootstrap. We don't manually merge 3 DDL files (technical debt). Instead, we use Spring Boot's `spring.sql.init.schema-locations` with multiple scripts.
- Each `test-ddl-*-clean.sql` is a copy of `docs/schema/ddl-*.sql` with `CREATE DATABASE` and `USE` statements removed (Testcontainers picks the database from `.withDatabaseName()`).
- The `refresh-test-ddl.sh` script regenerates these clean files whenever the source DDL changes (avoiding manual editing).

- [ ] **Step 1:** Create `portal-content-center/scripts/refresh-test-ddl.sh`:

```bash
#!/usr/bin/env bash
# Regenerate test-ddl-*-clean.sql files from docs/schema/ddl-*.sql
# Run from project root: bash portal-content-center/scripts/refresh-test-ddl.sh
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE_DIR="$PROJECT_ROOT/docs/schema"
TARGET_DIR="$PROJECT_ROOT/portal-content-center/src/test/resources/sql"

mkdir -p "$TARGET_DIR"

for module in auth governance portal; do
    src="$SOURCE_DIR/ddl-$module.sql"
    dst="$TARGET_DIR/test-ddl-$module-clean.sql"
    if [ ! -f "$src" ]; then
        echo "ERROR: $src not found"
        exit 1
    fi
    # Strip CREATE DATABASE and USE statements (case-insensitive)
    sed -E '/^[[:space:]]*CREATE[[:space:]]+DATABASE/Id; /^[[:space:]]*USE[[:space:]]+/Id' "$src" > "$dst"
    echo "Generated $dst"
done
```

- [ ] **Step 2:** Run the script: `bash portal-content-center/scripts/refresh-test-ddl.sh`
- [ ] **Step 3:** Verify the 3 clean DDL files exist and contain the expected CREATE TABLE statements (no CREATE DATABASE / USE)
- [ ] **Step 4:** Create empty `portal-test-data.sql` placeholder (will be filled in subsequent tasks)
- [ ] **Step 5:** Commit: `feat(portal): Task 0.4 - 测试 DDL 清理脚本与生成的 clean DDL`

### Task 0.5: AbstractMapperIntegrationTest Base Class + Smoke Test

**Files:**
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/support/AbstractMapperIntegrationTest.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/support/MapperContainerSmokeTest.java`
- Create: `portal-content-center/src/test/resources/application-test.yml`

**Context:** Testcontainers + MySQL 8.0 base class. Container is reused across test classes (`withReuse(true)`). Spring Boot loads the 3 DDL files in order via `spring.sql.init.schema-locations`.

- [ ] **Step 1:** Create `application-test.yml`:

```yaml
spring:
  sql:
    init:
      mode: always
      continue-on-error: false
  datasource:
    druid:
      initial-size: 1
      min-idle: 1
      max-active: 5
mybatis:
  configuration:
    map-underscore-to-camel-case: true
  mapper-locations: classpath*:mapper/**/*.xml
```

- [ ] **Step 2:** Create `AbstractMapperIntegrationTest.java`:

```java
package com.bank.branch.platform.portal.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mapper 集成测试基类
 * 启动 Testcontainers MySQL 8.0 容器，按序加载 3 份 DDL（auth + governance + portal）
 * 容器跨测试类复用以降低启动开销
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = {
    com.bank.branch.platform.portal.config.PortalMyBatisConfig.class
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractMapperIntegrationTest {

    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("onepl")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
        reg.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        reg.add("spring.sql.init.schema-locations", () -> String.join(",",
            "classpath:sql/test-ddl-auth-clean.sql",
            "classpath:sql/test-ddl-governance-clean.sql",
            "classpath:sql/test-ddl-portal-clean.sql"
        ));
    }
}
```

- [ ] **Step 3:** Create `MapperContainerSmokeTest.java` (RED-GREEN cycle):

```java
package com.bank.branch.platform.portal.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.assertThat;

class MapperContainerSmokeTest extends AbstractMapperIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void shouldStartMysqlContainerAndLoadAllDdls() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Verify product_info table exists (from ddl-portal.sql)
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'product_info'")) {
                assertThat(rs.next()).as("product_info table should exist").isTrue();
            }
            // Verify portal_shortcut table exists
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'portal_shortcut'")) {
                assertThat(rs.next()).as("portal_shortcut table should exist").isTrue();
            }
            // Verify addrbook_employee table exists
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'addrbook_employee'")) {
                assertThat(rs.next()).as("addrbook_employee table should exist").isTrue();
            }
            // Verify PT_USER table exists (from ddl-auth.sql)
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'PT_USER'")) {
                assertThat(rs.next()).as("PT_USER table should exist").isTrue();
            }
        }
    }
}
```

- [ ] **Step 4:** Run the smoke test: `mvn test -pl portal-content-center -Dtest=MapperContainerSmokeTest -DfailIfNoTests=false`
  - Expected: First run downloads MySQL 8.0.36 image (~500MB, 1-3 minutes), then PASS
  - If fails: check that Docker daemon is running, that `withReuse(true)` is set, and inspect Testcontainers logs
- [ ] **Step 5:** Commit: `feat(portal): Task 0.5 - Testcontainers Mapper 集成测试基类 + smoke test`

### Task 0.6: @WithMockEmpContext + MockEmpContextExtension

**Files:**
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/support/WithMockEmpContext.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/support/MockEmpContextExtension.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/support/AbstractControllerIntegrationTest.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/support/WithMockEmpContextSelfTest.java`

**Context:** Custom JUnit 5 annotation that sets up `CurrentUserContext` (ThreadLocal) and `DataScopeContext` (ThreadLocal) before each test, and clears them after. Used by Controller integration tests to simulate authentication.

- [ ] **Step 1:** Read `common/common-security/src/main/java/com/bank/branch/platform/common/security/context/CurrentUserContext.java` and `auth-permission-center/.../security/context/CurrentUserProvider.java` to understand how the ThreadLocal is set
- [ ] **Step 2:** Create `WithMockEmpContext.java`:

```java
package com.bank.branch.platform.portal.support;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试用：注入 mock 的 CurrentUserContext 和 DataScopeContext
 * 默认 dataScope 为 ORG_SUBTREE，避免测试漏掉真实过滤分支
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockEmpContextExtension.class)
public @interface WithMockEmpContext {
    String empId() default "E10001";
    String orgCode() default "ORG_SZ_001";
    String[] roleCodes() default {"R_RM"};
    String dataScope() default "ORG_SUBTREE";
    String[] orgSubtree() default {"ORG_SZ_001"};
    boolean systemAdmin() default false;
}
```

- [ ] **Step 3:** Create `MockEmpContextExtension.java`:

```java
package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.context.CurrentUserContextHolder;
// NOTE: replace with actual ThreadLocal holder class — verify by reading common-security source
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * JUnit 5 扩展：根据 @WithMockEmpContext 注解参数设置 ThreadLocal 上下文
 */
public class MockEmpContextExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Method m = context.getRequiredTestMethod();
        WithMockEmpContext anno = m.getAnnotation(WithMockEmpContext.class);
        if (anno == null) return;

        Set<String> roleCodes = new HashSet<>(Arrays.asList(anno.roleCodes()));
        Set<String> roleIds = new HashSet<>(Arrays.asList(anno.roleCodes())); // simplified
        Set<String> candidateGroups = new HashSet<>();

        CurrentUserContext userCtx = new CurrentUserContext(
            anno.empId(),
            anno.orgCode(),
            roleIds,
            roleCodes,
            candidateGroups,
            anno.systemAdmin()
        );
        CurrentUserContextHolder.set(userCtx);

        // DataScope context: see Step 4 below — needs DataScopeContextHolder reference
    }

    @Override
    public void afterEach(ExtensionContext context) {
        CurrentUserContextHolder.clear();
        // Clear DataScopeContext too
    }
}
```

- [ ] **Step 4:** Read `common-security` source to find the actual `CurrentUserContextHolder` class (or `CurrentUserProvider`) and `DataScopeContextHolder`. Adjust the imports and method calls in `MockEmpContextExtension` to match the real API. The pattern likely is:
  ```java
  CurrentUserProvider.set(userCtx);  // or similar
  ```
- [ ] **Step 5:** Add DataScope setup in `beforeEach`:
  ```java
  if (!anno.dataScope().equals("ALL")) {
      DataScopeContext scopeCtx = new DataScopeContext(
          DataScopeType.valueOf(anno.dataScope()),
          anno.empId(),
          anno.orgCode(),
          new HashSet<>(Arrays.asList(anno.orgSubtree())),
          BizType.PRODUCT,  // default; tests can override with system property
          BizAction.LIST
      );
      DataScopeContextHolder.set(scopeCtx);
  }
  ```
- [ ] **Step 6:** Create `AbstractControllerIntegrationTest.java`:

```java
package com.bank.branch.platform.portal.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Controller 集成测试基类
 * - SpringBootTest with MockMvc
 * - 使用 @MockBean 替换所有 Mapper 和跨模块 Api（不连真实数据库）
 * - @WithMockEmpContext 注入测试用户上下文
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractControllerIntegrationTest {
}
```

- [ ] **Step 7:** Create `WithMockEmpContextSelfTest.java` to verify the extension works:

```java
package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class WithMockEmpContextSelfTest extends AbstractControllerIntegrationTest {

    @Autowired
    CurrentUserApi currentUserApi;

    @Test
    @WithMockEmpContext(empId = "E99999", orgCode = "ORG_TEST", dataScope = "ORG", orgSubtree = {"ORG_TEST"})
    void shouldInjectMockContext() {
        assertThat(currentUserApi.getCurrentEmpId()).isEqualTo("E99999");
        assertThat(currentUserApi.getCurrentOrgCode()).isEqualTo("ORG_TEST");
    }
}
```

- [ ] **Step 8:** Run the self-test: `mvn test -pl portal-content-center -Dtest=WithMockEmpContextSelfTest`. Expected: PASS
- [ ] **Step 9:** **TROUBLESHOOTING NOTE:** If `CurrentUserContextHolder` doesn't exist (the actual class might be named differently), grep `auth-permission-center/src/main/java` for `ThreadLocal<CurrentUserContext>` and use whatever class wraps it. Alternative approach: use `MockBean(CurrentUserApi.class)` and stub its methods directly with Mockito instead of touching ThreadLocal.
- [ ] **Step 10:** Commit: `feat(portal): Task 0.6 - @WithMockEmpContext 自定义 JUnit 5 扩展`

### Task 0.7: JsonStringListTypeHandler + Unit Tests

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/typehandler/JsonStringListTypeHandler.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/typehandler/JsonStringListTypeHandlerTest.java`

**Context:** MyBatis TypeHandler that serializes/deserializes `List<String>` to JSON text. Used for `product_info.responsible_emp_ids` and `addrbook_employee.responsible_product_ids`.

- [ ] **Step 1 (RED):** Create `JsonStringListTypeHandlerTest.java`:

```java
package com.bank.branch.platform.portal.typehandler;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class JsonStringListTypeHandlerTest {

    private final JsonStringListTypeHandler handler = new JsonStringListTypeHandler();

    @Test
    void shouldSerializeNullToNull() {
        assertThat(handler.serialize(null)).isNull();
    }

    @Test
    void shouldSerializeEmptyListToEmptyJsonArray() {
        assertThat(handler.serialize(Collections.emptyList())).isEqualTo("[]");
    }

    @Test
    void shouldSerializeListToJsonArray() {
        assertThat(handler.serialize(Arrays.asList("E001", "E002")))
            .isEqualTo("[\"E001\",\"E002\"]");
    }

    @Test
    void shouldDeserializeNullToEmptyList() {
        assertThat(handler.deserialize(null)).isEmpty();
    }

    @Test
    void shouldDeserializeEmptyStringToEmptyList() {
        assertThat(handler.deserialize("")).isEmpty();
    }

    @Test
    void shouldDeserializeEmptyJsonArrayToEmptyList() {
        assertThat(handler.deserialize("[]")).isEmpty();
    }

    @Test
    void shouldDeserializeJsonArrayToList() {
        assertThat(handler.deserialize("[\"E001\",\"E002\"]"))
            .containsExactly("E001", "E002");
    }
}
```

- [ ] **Step 2:** Run tests, expect FAIL (class not exists yet): `mvn test -pl portal-content-center -Dtest=JsonStringListTypeHandlerTest`
- [ ] **Step 3 (GREEN):** Implement `JsonStringListTypeHandler.java`:

```java
package com.bank.branch.platform.portal.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis TypeHandler: List<String> ↔ JSON 数组字符串
 *
 * 用于 product_info.responsible_emp_ids 和 addrbook_employee.responsible_product_ids 字段
 *
 * 边界处理：
 * - serialize(null) → null
 * - serialize(empty) → "[]"
 * - deserialize(null/empty/whitespace) → emptyList
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.LONGVARCHAR)
public class JsonStringListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_REF = new TypeReference<>() {};

    public String serialize(List<String> list) {
        if (list == null) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialize failed: " + list, e);
        }
    }

    public List<String> deserialize(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, LIST_REF);
        } catch (Exception e) {
            throw new IllegalStateException("JSON deserialize failed: " + json, e);
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, serialize(parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return deserialize(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return deserialize(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return deserialize(cs.getString(columnIndex));
    }
}
```

- [ ] **Step 4:** Run tests, expect PASS
- [ ] **Step 5:** Commit: `feat(portal): Task 0.7 - JsonStringListTypeHandler + 边界单测`

### Task 0.8: PT_RESOURCE Alignment SQL

**Files:**
- Create: `docs/superpowers/sql/2026-04-11-portal-resources-align.sql`

**Context:** seed-v1.sql §3.2 is missing 3 portal resources required by V1, and contains 4 obsolete dashboard resources that should be marked deleted. This task produces the align SQL.

- [ ] **Step 1:** Read `docs/schema/seed-v1.sql` lines 347-380 to confirm the current PT_RESOURCE state for portal
- [ ] **Step 2:** Create `docs/superpowers/sql/2026-04-11-portal-resources-align.sql`:

```sql
-- ============================================================================
-- portal-content-center V1 切片 PT_RESOURCE 对齐脚本
-- 创建日期：2026-04-11
-- 关联 spec：docs/superpowers/specs/2026-04-11-portal-content-center-v1-slice-design.md
-- 执行前：备份 PT_RESOURCE 表
-- 幂等：使用 ON DUPLICATE KEY UPDATE
-- ============================================================================

-- (a) 新增 3 条 V1 切片需要的资源
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, RESOURCE_NAME, IS_MENU, SYS_CODE, IS_DELETED, OWNER_BY, CREATE_TIME, CREATE_BY, UPDATE_TIME, UPDATE_BY, REMARK)
VALUES
  ('RES_PORTAL_WORKSPACE',          '/api/portal/workspace',           'GET', '工作台聚合',         0, '1', 0, 'PLATFORM', NOW(), 'align-2026-04-11', NOW(), 'align-2026-04-11', 'V1 portal slice'),
  ('RES_PRODUCT_SUPPORT_AVAILABLE', '/api/products/support-available', 'GET', '中场支持产品查询',   0, '1', 0, 'PLATFORM', NOW(), 'align-2026-04-11', NOW(), 'align-2026-04-11', 'V1 portal slice'),
  ('RES_SHORTCUT_REPLACE_PUT',      '/api/portal/shortcuts',           'PUT', '快捷入口全量替换',   0, '1', 0, 'PLATFORM', NOW(), 'align-2026-04-11', NOW(), 'align-2026-04-11', 'V1 portal slice')
ON DUPLICATE KEY UPDATE
  RESOURCE_NAME=VALUES(RESOURCE_NAME),
  UPDATE_TIME=NOW(),
  UPDATE_BY='align-2026-04-11';

-- (b) 标记 4 条已废弃的旧 dashboard 资源为 deleted=1
-- (这些 URL /api/portal/dashboard/* 已被统一接口 /api/portal/workspace 取代)
UPDATE PT_RESOURCE
SET IS_DELETED = 1, UPDATE_TIME = NOW(), UPDATE_BY = 'align-2026-04-11',
    REMARK = CONCAT(IFNULL(REMARK, ''), ' [deprecated by 2026-04-11 portal slice]')
WHERE RESOURCE_ID IN (
  'RES_PORTAL_TODOS',
  'RES_PORTAL_NOTIFY',
  'RES_PORTAL_NOTIFY_READ',
  'RES_PORTAL_CARDS'
);

-- 验证 SQL（执行后核对）
-- 1. 新增的 3 条应该存在
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, IS_DELETED FROM PT_RESOURCE
WHERE RESOURCE_ID IN ('RES_PORTAL_WORKSPACE','RES_PRODUCT_SUPPORT_AVAILABLE','RES_SHORTCUT_REPLACE_PUT');
-- 期望：3 行，IS_DELETED=0

-- 2. 废弃的 4 条应该 IS_DELETED=1
SELECT RESOURCE_ID, IS_DELETED FROM PT_RESOURCE
WHERE RESOURCE_ID IN ('RES_PORTAL_TODOS','RES_PORTAL_NOTIFY','RES_PORTAL_NOTIFY_READ','RES_PORTAL_CARDS');
-- 期望：4 行，IS_DELETED=1
```

- [ ] **Step 3:** Verify the SQL syntactically by running it against a local MySQL: `mysql -uroot -p123456 onepl < docs/superpowers/sql/2026-04-11-portal-resources-align.sql` (or skip if no DB access; the integration test will validate it)
- [ ] **Step 4:** Commit: `chore(portal): Task 0.8 - PT_RESOURCE 对齐 SQL（新增 3 条 + 废弃 4 条）`

---

## Phase 1: Adapter Layer (Cross-Module Placeholders)

### Task 1.1: MetricApi Placeholder Interface

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricApi.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/MetricCardDTO.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/MetricTrendPoint.java`

**Context:** TEMPORARY placeholder. Real `MetricApi` will be created in `performance-engine-center` later. Method signatures must match `09-依赖契约摘要.md §4.1` exactly. Class header javadoc MUST mark this as deprecated/temporary.

- [ ] **Step 1:** Create `MetricCardDTO.java` (11 fields per 09 §4.1) — `@Deprecated @Data` Lombok class with fields: `metricCode (String) / metricName (String) / currentValue (BigDecimal) / targetValue (BigDecimal) / achievementRate (BigDecimal) / unit (String) / trend (String) / changeRate (BigDecimal) / period (String) / dataTime (LocalDateTime) / colorHint (String)`. Class javadoc: `@deprecated V1 临时占位，待 performance-engine-center 模块创建后迁移`
- [ ] **Step 2:** Create `MetricTrendPoint.java` — `@Deprecated @Data` with `dataTime (LocalDateTime) + value (BigDecimal)`
- [ ] **Step 3:** Create `MetricApi.java` interface with javadoc `@deprecated V1 临时占位接口` and 3 methods: `List<MetricCardDTO> getUserMetricCards(String empId)` / `MetricCardDTO getMetricCard(String empId, String metricCode)` / `List<MetricTrendPoint> getMetricTrend(String empId, String metricCode, String period)`. **Do NOT** annotate with `@Service` — bean is intentionally absent so `@Autowired(required=false)` resolves to null
- [ ] **Step 4:** Verify compilation: `mvn compile -pl portal-content-center -q`
- [ ] **Step 5:** Commit: `feat(portal): Task 1.1 - MetricApi 占位接口（V1 临时）`

### Task 1.2: MetricAdapter with Graceful Fallback

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/api/dto/PortalMetricCard.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/convert/MetricCardProjection.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricAdapter.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/adapter/MetricAdapterTest.java`

**Context:** Adapter wraps `@Autowired(required=false) MetricApi`. When bean is null (V1 default), returns empty list. When real bean exists (V2), invokes it and projects to `PortalMetricCard` (7 fields per 03 §A.1).

- [ ] **Step 1:** Create `PortalMetricCard.java` — `@Data` Lombok with 7 fields per 03 §A.1: `metricCode (String) / metricName (String) / currentValue (String, formatted) / targetValue (String, formatted) / completionRate (BigDecimal) / trend (String) / unit (String)`
- [ ] **Step 2:** Create `MetricCardProjection.java`:

```java
package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import com.bank.branch.platform.portal.api.dto.PortalMetricCard;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MetricCardProjection {
    private MetricCardProjection() {}

    public static PortalMetricCard toPortal(MetricCardDTO src) {
        if (src == null) return null;
        PortalMetricCard out = new PortalMetricCard();
        out.setMetricCode(src.getMetricCode());
        out.setMetricName(src.getMetricName());
        out.setUnit(src.getUnit());
        out.setTrend(src.getTrend());
        out.setCurrentValue(formatNumber(src.getCurrentValue()));
        out.setTargetValue(formatNumber(src.getTargetValue()));
        out.setCompletionRate(src.getAchievementRate()); // 字段重命名
        return out;
    }

    private static String formatNumber(BigDecimal v) {
        if (v == null) return "";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
```

- [ ] **Step 3 (RED):** Create `MetricAdapterTest.java` with 3 test methods:
  1. `shouldReturnEmptyListWhenMetricApiBeanIsNull()` — instantiate `MetricAdapter` directly (no Spring), verify `fetch("E10001")` returns empty
  2. `shouldProjectMetricCardDTOToPortalMetricCardWhenBeanExists()` — Mockito mock `MetricApi`, return one `MetricCardDTO`, call `adapter.setMetricApi(mock)`, verify projected fields (especially `currentValue` rounded to 2 decimals)
  3. `shouldReturnEmptyListWhenApiThrows()` — mock throws exception, verify empty list returned (graceful degrade)

- [ ] **Step 4:** Run test, expect FAIL: `mvn test -pl portal-content-center -Dtest=MetricAdapterTest`
- [ ] **Step 5 (GREEN):** Create `MetricAdapter.java`:

```java
package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.api.dto.PortalMetricCard;
import com.bank.branch.platform.portal.convert.MetricCardProjection;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetricAdapter {

    @Autowired(required = false)
    @Setter // for testing
    private MetricApi metricApi;

    public List<PortalMetricCard> fetch(String empId) {
        if (metricApi == null) {
            log.debug("MetricApi bean is null, returning empty list (V1 fallback)");
            return Collections.emptyList();
        }
        try {
            return metricApi.getUserMetricCards(empId).stream()
                .map(MetricCardProjection::toPortal)
                .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("MetricApi.getUserMetricCards failed for empId={}", empId, ex);
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 6:** Run tests, expect PASS
- [ ] **Step 7:** Commit: `feat(portal): Task 1.2 - MetricAdapter + 字段投影 + 降级测试`

### Task 1.3: WorkflowQueryApi Placeholder + WorkflowQueryAdapter

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/PortalTodoItem.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/WorkflowQueryApi.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/WorkflowQueryAdapter.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/adapter/WorkflowQueryAdapterTest.java`

**Context:** Same pattern as MetricAdapter. Used by A.1 workspace aggregation.

- [ ] **Step 1:** Create `PortalTodoItem.java` — `@Deprecated @Data` Lombok with 9 fields per 03 §A.1: `taskId / processInstanceId / processName / taskTitle / initiatorName / initiatedTime / lightStatus / overdueInfo / bizDetailUrl` (all String)
- [ ] **Step 2:** Create `WorkflowQueryApi.java` — `@Deprecated` interface with 2 methods: `int countPendingTasks(String empId)` and `List<PortalTodoItem> listRecentPendingTasks(String empId, int limit)`. Class javadoc: `V1 临时占位接口，workflow-center 仅暴露 WorkflowApi（流程启动），无 query 方法。待 WorkflowQueryFacade 创建后迁移`
- [ ] **Step 3 (RED):** Create `WorkflowQueryAdapterTest.java` with 3 tests mirroring `MetricAdapterTest`:
  1. `shouldReturnZeroAndEmptyWhenWorkflowQueryApiBeanIsNull()` — count returns 0, list returns empty
  2. `shouldDelegateWhenBeanExists()` — Mockito stub returns 12 + 1-element list; verify pass-through
  3. `shouldReturnFallbackOnException()` — mock throws; verify count=0 + empty list
- [ ] **Step 4:** Run, expect FAIL
- [ ] **Step 5 (GREEN):** Create `WorkflowQueryAdapter.java` — `@Service` Lombok class with `@Autowired(required=false) @Setter WorkflowQueryApi workflowQueryApi`, two methods that null-check + try-catch + log.warn. Same shape as MetricAdapter.
- [ ] **Step 6:** Run tests, expect PASS
- [ ] **Step 7:** Commit: `feat(portal): Task 1.3 - WorkflowQueryApi 占位 + WorkflowQueryAdapter`

---

## Phase 2: Entity / Mapper / Convert Foundation

### Task 2.1: ProductInfo Entity + ProductInfoMapper Basic CRUD

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/entity/ProductInfo.java`
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/mapper/ProductInfoMapper.java`
- Create: `portal-content-center/src/main/resources/mapper/portal/ProductInfoMapper.xml`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/mapper/ProductInfoMapperIntegrationTest.java`
- Modify: `portal-content-center/src/test/resources/sql/portal-test-data.sql` — add fixture rows for product_info

**Context:** Read `docs/modules/portal-content-center/05-表结构DDL.md` for exact column mappings. Read `docs/schema/ddl-portal.sql` §4 for the actual `product_info` table DDL. Follow `system-governance-center/.../entity/SysDict.java` pattern (Lombok @Data, no business logic, fields match DB columns).

**Field mapping (from ddl-portal.sql §4):**
- `id` VARCHAR(64) → `String id`
- `product_code` VARCHAR(64) → `String productCode`
- `product_name` VARCHAR(255) → `String productName`
- `product_category` VARCHAR(64) → `String productCategory`
- `description` TEXT → `String description`
- `support_for_support_request` TINYINT(1) → `Boolean supportForSupportRequest`
- `owner_org_id` VARCHAR(50) → `String ownerOrgId`
- `product_dept_org_code` VARCHAR(50) → `String productDeptOrgCode`
- `file_object_id` VARCHAR(32) → `String fileObjectId`
- `responsible_emp_ids` TEXT → `List<String> responsibleEmpIds` (with JsonStringListTypeHandler)
- `status` VARCHAR(32) → `String status`
- `created_by` / `updated_by` VARCHAR(32) → `String`
- `created_time` / `updated_time` DATETIME → `LocalDateTime`
- `deleted` TINYINT → `Integer deleted`

- [ ] **Step 1:** Read `system-governance-center/src/main/java/com/bank/branch/platform/governance/entity/SysDict.java` and `governance/mapper/DictMapper.java` and `resources/mapper/governance/DictMapper.xml` for the entity/mapper pattern
- [ ] **Step 2:** Create `ProductInfo.java` — `@Data` Lombok class with all fields above. Add javadoc explaining each field's meaning.
- [ ] **Step 3:** Create `ProductInfoMapper.java` interface with these methods:

```java
package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ProductInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductInfoMapper {

    /** 插入新产品 */
    int insert(ProductInfo entity);

    /** 按 id 查询（含逻辑删除过滤） */
    ProductInfo selectById(@Param("id") String id);

    /** 按 id 查询并加 FOR UPDATE 行锁（D.5 编辑用） */
    ProductInfo selectByIdForUpdate(@Param("id") String id);

    /** 按 productCode 查询（用于 D.4 唯一性预检） */
    ProductInfo selectByProductCode(@Param("productCode") String productCode);

    /** 按部分字段更新（D.5） */
    int updateById(ProductInfo entity);

    /** 逻辑删除（D.6） */
    int softDeleteById(@Param("id") String id, @Param("updatedBy") String updatedBy);

    /** D.3 查询所有支持中场支持的产品（active + 未删除） */
    List<ProductInfo> listSupportAvailable();

    /** 批量按 id 列表查询（D.2 详情聚合时用） */
    List<ProductInfo> listByIds(@Param("ids") List<String> ids);
}
```

- [ ] **Step 4:** Create `ProductInfoMapper.xml`. Key points:
  - `<resultMap>` for `ProductInfo` with `<result column="responsible_emp_ids" property="responsibleEmpIds" typeHandler="com.bank.branch.platform.portal.typehandler.JsonStringListTypeHandler"/>`
  - `<sql id="BASE_COLUMNS">` listing all columns
  - All SELECT statements include `WHERE deleted = 0` (except `selectByIdForUpdate` which uses it for locking)
  - `selectByIdForUpdate` ends with `FOR UPDATE`
  - `listSupportAvailable` SQL: `WHERE support_for_support_request = 1 AND status = 'ACTIVE' AND deleted = 0 ORDER BY product_name`
  - Follow exact format of `system-governance-center/.../resources/mapper/governance/DictMapper.xml`
- [ ] **Step 5 (RED):** Create `ProductInfoMapperIntegrationTest.java` extends `AbstractMapperIntegrationTest`:

```java
package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductInfoMapperIntegrationTest extends AbstractMapperIntegrationTest {

    @Autowired
    ProductInfoMapper mapper;

    @BeforeEach
    void clean() {
        // Use a JdbcTemplate or direct mapper to clean: DELETE FROM product_info
        // Insert 3 fixture rows for the tests
    }

    @Test
    void insertAndSelectByIdShouldRoundTripJsonField() {
        ProductInfo p = newProduct("TEST_001");
        p.setResponsibleEmpIds(Arrays.asList("E10001", "E10002"));
        mapper.insert(p);

        ProductInfo loaded = mapper.selectById(p.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getProductCode()).isEqualTo("TEST_001");
        assertThat(loaded.getResponsibleEmpIds()).containsExactly("E10001", "E10002");
    }

    @Test
    void selectByIdShouldReturnNullForDeletedProduct() {
        ProductInfo p = newProduct("TEST_002");
        mapper.insert(p);
        mapper.softDeleteById(p.getId(), "tester");

        assertThat(mapper.selectById(p.getId())).isNull();
    }

    @Test
    void listSupportAvailableShouldReturnOnlyActiveAndSupporting() {
        ProductInfo a = newProduct("AVAIL_A"); a.setSupportForSupportRequest(true); a.setStatus("ACTIVE");
        ProductInfo b = newProduct("AVAIL_B"); b.setSupportForSupportRequest(false); b.setStatus("ACTIVE");
        ProductInfo c = newProduct("AVAIL_C"); c.setSupportForSupportRequest(true); c.setStatus("DISABLED");
        mapper.insert(a); mapper.insert(b); mapper.insert(c);

        List<ProductInfo> result = mapper.listSupportAvailable();
        assertThat(result).extracting(ProductInfo::getProductCode).contains("AVAIL_A");
        assertThat(result).extracting(ProductInfo::getProductCode).doesNotContain("AVAIL_B", "AVAIL_C");
    }

    private ProductInfo newProduct(String code) {
        ProductInfo p = new ProductInfo();
        p.setId(UUID.randomUUID().toString().replace("-", ""));
        p.setProductCode(code);
        p.setProductName("产品 " + code);
        p.setProductCategory("CAT_DEPOSIT");
        p.setStatus("ACTIVE");
        p.setProductDeptOrgCode("ORG_SZ_001");
        p.setCreatedBy("tester");
        p.setSupportForSupportRequest(false);
        return p;
    }
}
```

- [ ] **Step 6:** Run integration test, expect FAIL (mapper not implemented yet — first run will fail because XML not finalized): `mvn test -pl portal-content-center -Dtest=ProductInfoMapperIntegrationTest`
- [ ] **Step 7:** Iterate Mapper.xml until tests PASS. Common pitfalls:
  - JSON column type handler — make sure `typeHandler` attribute is set in `<result>`
  - `INSERT` statement: include all NOT NULL columns
  - `selectById` must filter `deleted = 0`
- [ ] **Step 8:** Commit: `feat(portal): Task 2.1 - ProductInfo Entity + Mapper 基础 CRUD + JSON 字段集成测试`

### Task 2.2: PortalShortcut Entity + Mapper

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/entity/PortalShortcut.java`
- Create: `portal-content-center/src/main/java/.../portal/mapper/PortalShortcutMapper.java`
- Create: `portal-content-center/src/main/resources/mapper/portal/PortalShortcutMapper.xml`
- Create: `portal-content-center/src/test/java/.../portal/mapper/PortalShortcutMapperIntegrationTest.java`

**Context:** Read `ddl-portal.sql §2` for `portal_shortcut` columns. Note: this table has NO `deleted` column.

**Field mapping:**
- `id` VARCHAR(32) → `String id`
- `shortcut_name / shortcut_url / shortcut_icon / shortcut_type / target_type` → String
- `emp_id` VARCHAR(32) → String (nullable for SYSTEM type)
- `sort_order` INT → Integer
- `status` VARCHAR(20) → String
- `created_by / updated_by / created_time / updated_time` audit fields

- [ ] **Step 1:** Create `PortalShortcut.java` Entity — Lombok @Data
- [ ] **Step 2:** Create `PortalShortcutMapper.java` with methods:
  - `int insert(PortalShortcut entity)`
  - `int batchInsert(@Param("list") List<PortalShortcut> list)` (for A.3 batch save)
  - `List<PortalShortcut> listByEmpIdOrSystem(@Param("empId") String empId)` — `WHERE shortcut_type='SYSTEM' OR (shortcut_type='CUSTOM' AND emp_id=#{empId})` ORDER BY shortcut_type DESC, sort_order ASC
  - `int deleteCustomByEmpId(@Param("empId") String empId)` — `DELETE FROM portal_shortcut WHERE shortcut_type='CUSTOM' AND emp_id=#{empId}`
- [ ] **Step 3:** Create `PortalShortcutMapper.xml` with corresponding SQL
- [ ] **Step 4 (RED):** Create `PortalShortcutMapperIntegrationTest.java` with tests:
  1. `insertAndListShouldReturnSystemAndOwnCustom()` — insert 1 SYSTEM + 2 CUSTOM (one for E10001, one for E10002), verify `listByEmpIdOrSystem("E10001")` returns SYSTEM + own CUSTOM only
  2. `deleteCustomByEmpIdShouldNotTouchSystemOrOthers()` — delete E10001's CUSTOM, verify SYSTEM and E10002's CUSTOM still exist
  3. `batchInsertShouldPersistAllRows()` — batch insert 3 rows, verify count
- [ ] **Step 5:** Run tests, iterate until PASS
- [ ] **Step 6:** Commit: `feat(portal): Task 2.2 - PortalShortcut Entity + Mapper`

### Task 2.3: AddrbookEmployee Entity + Mapper (含 D.6 前置检查方法)

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/entity/AddrbookEmployee.java`
- Create: `portal-content-center/src/main/java/.../portal/mapper/AddrbookEmployeeMapper.java`
- Create: `portal-content-center/src/main/resources/mapper/portal/AddrbookEmployeeMapper.xml`
- Create: `portal-content-center/src/test/java/.../portal/mapper/AddrbookEmployeeMapperIntegrationTest.java`

**Context:** Read `ddl-portal.sql §3` for `addrbook_employee`. Primary key is `emp_id` (not `id`!). The `responsible_product_ids` field uses JSON TypeHandler. The mapper needs `countEmployeesReferringProduct(productId)` for D.6's pre-deletion check.

**Field mapping:**
- `emp_id` VARCHAR(32) PK → `String empId`
- `emp_name / mobile / email / org_code / org_name / position / self_desc` → String
- `responsible_product_ids` TEXT → `List<String>` (JsonStringListTypeHandler)
- `status` VARCHAR(20) → String (`ACTIVE` / `RESIGNED`)
- `maintainer_emp_id` VARCHAR(32) → String
- `created_time / updated_time` LocalDateTime
- `deleted` INT(11) → Integer

- [ ] **Step 1:** Create `AddrbookEmployee.java` Entity
- [ ] **Step 2:** Create `AddrbookEmployeeMapper.java` with these methods:

```java
@Mapper
public interface AddrbookEmployeeMapper {

    int insert(AddrbookEmployee entity);

    AddrbookEmployee selectByEmpId(@Param("empId") String empId);

    /** D.2 详情用：批量按 empId 查员工，含 mobile 等字段 */
    List<AddrbookEmployee> listByEmpIds(@Param("empIds") List<String> empIds);

    /** D.6 前置引用检查：统计仍把 productId 列为负责产品的员工数 */
    int countEmployeesReferringProduct(@Param("productId") String productId);

    /** D.4/D.5/D.6 双向同步：更新单个员工的 responsible_product_ids（乐观锁） */
    int updateResponsibleProductsWithOptimisticLock(
        @Param("empId") String empId,
        @Param("responsibleProductIds") List<String> responsibleProductIds,
        @Param("expectedUpdatedTime") LocalDateTime expectedUpdatedTime,
        @Param("operatorEmpId") String operatorEmpId
    );

    /** D.4/D.5 校验 empId 是否存在且 ACTIVE */
    int countActiveByEmpIds(@Param("empIds") List<String> empIds);
}
```

- [ ] **Step 3:** Create `AddrbookEmployeeMapper.xml`. **Key point**: `countEmployeesReferringProduct` SQL must use `JSON_CONTAINS` or `LIKE` for JSON column matching. Since we agreed not to use `JSON_CONTAINS` (방언 issue), use `LIKE`:
  ```xml
  <select id="countEmployeesReferringProduct" resultType="int">
      SELECT COUNT(*) FROM addrbook_employee
      WHERE deleted = 0
        AND responsible_product_ids LIKE CONCAT('%"', #{productId}, '"%')
  </select>
  ```
  **Caveat**: This `LIKE` approach works for the JSON storage format `["P001","P002"]`, where each id is wrapped in double quotes. False positives possible if a productId is a substring of another (e.g., `P001` matches `P0010`). Add a regression test for this case.
- [ ] **Step 4 (RED):** Create integration tests:
  1. `insertAndSelectByEmpIdShouldRoundTripJsonField()` — insert with `responsibleProductIds=["P001","P002"]`, select, verify list
  2. `countEmployeesReferringProductShouldFindAllOwners()` — insert 2 employees with P001 + 1 without, verify count == 2
  3. `countEmployeesReferringProductShouldNotMatchSubstring()` — insert employee with `["P0010"]`, search for `"P001"`, verify count == 0 (test the LIKE precision)
  4. `updateResponsibleProductsWithOptimisticLockShouldReturnZeroOnVersionMismatch()` — insert, then call update with stale `expectedUpdatedTime`, verify rowsAffected == 0
  5. `listByEmpIdsShouldReturnRequestedEmployees()` — insert 3 employees, request 2 by empId list, verify 2 returned
- [ ] **Step 5:** Run, iterate XML until PASS
- [ ] **Step 6:** Commit: `feat(portal): Task 2.3 - AddrbookEmployee Entity + Mapper（含 D.6 引用检查）`

### Task 2.4: ProductConverter + ShortcutConverter

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/convert/ProductConverter.java`
- Create: `portal-content-center/src/main/java/.../portal/convert/ShortcutConverter.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ProductSimpleDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ProductDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ProductDetailDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ResponsibleEmpDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ShortcutDTO.java`
- Create: `portal-content-center/src/test/java/.../portal/convert/ProductConverterTest.java`

**Context:** DTO definitions strictly follow `docs/modules/portal-content-center/03-接口设计与报文.md §D.1 / §D.2 / §A.2`. Converters are pure functions Entity ↔ DTO.

- [ ] **Step 1:** Read `03 §D.1` for ProductDTO field list (used by list response)
- [ ] **Step 2:** Create `ProductSimpleDTO.java` (5 fields for D.3): `id / productCode / productName / productCategory / productDeptOrgCode`
- [ ] **Step 3:** Create `ProductDTO.java` (D.1 list response): `id / productCode / productName / productCategory / productCategoryDesc / productDeptOrgCode / productDeptOrgName / supportForSupportRequest / status / fileName / responsibleEmps (List<ResponsibleEmpDTO>) / updatedByName / updatedTime`
- [ ] **Step 4:** Create `ProductDetailDTO.java` (D.2): extend ProductDTO with `description / fileDownloadUrl / canEdit (Boolean)`
- [ ] **Step 5:** Create `ResponsibleEmpDTO.java`: `empId / empName / mobile (脱敏后) / position`
- [ ] **Step 6:** Create `ShortcutDTO.java` (A.1/A.2): `id (Long) / shortcutName / shortcutUrl / shortcutIcon / shortcutType / targetType / sortOrder`
- [ ] **Step 7:** Create `ProductConverter.java`:

```java
package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;

public final class ProductConverter {
    private ProductConverter() {}

    public static ProductSimpleDTO toSimple(ProductInfo entity) {
        if (entity == null) return null;
        ProductSimpleDTO dto = new ProductSimpleDTO();
        dto.setId(entity.getId());
        dto.setProductCode(entity.getProductCode());
        dto.setProductName(entity.getProductName());
        dto.setProductCategory(entity.getProductCategory());
        dto.setProductDeptOrgCode(entity.getProductDeptOrgCode());
        return dto;
    }

    // toListItem and toDetailDTO will be added in later tasks (Phase 4 / 5)
    // when their full DTOs are needed; this minimal version unblocks Phase 3 (D.3)
}
```

- [ ] **Step 8:** Create `ShortcutConverter.java` similarly with `toDTO(PortalShortcut)`
- [ ] **Step 9 (TEST):** Create `ProductConverterTest.java` — verify `toSimple` returns null on null input, returns mapped DTO on entity input
- [ ] **Step 10:** Run unit tests, expect PASS
- [ ] **Step 11:** Commit: `feat(portal): Task 2.4 - DTO 定义 + Converter 基础`

---

## Phase 3: D.3 GET /api/products/support-available — First Complete TDD Loop

**This phase establishes the full TDD cycle (Service unit test → Mapper integration test → Controller integration test → commit) that subsequent business phases will follow. Read this carefully — Phase 4-11 reference its patterns.**

### Task 3.1: ProductService.listSupportAvailable (Service Unit Test First)

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/service/ProductService.java`
- Create: `portal-content-center/src/test/java/.../portal/service/ProductServiceTest.java`

- [ ] **Step 1 (RED):** Create `ProductServiceTest.java`:

```java
package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductInfoMapper productInfoMapper;

    @InjectMocks
    ProductService productService;

    @Test
    void listSupportAvailableShouldReturnSimpleDTOsFromMapper() {
        // Arrange
        ProductInfo p1 = new ProductInfo();
        p1.setId("P001");
        p1.setProductCode("DEPOSIT_001");
        p1.setProductName("活期存款");
        p1.setProductCategory("CAT_DEPOSIT");
        p1.setProductDeptOrgCode("ORG_HQ_FIN");

        ProductInfo p2 = new ProductInfo();
        p2.setId("P002");
        p2.setProductCode("LOAN_001");
        p2.setProductName("个人消费贷");
        p2.setProductCategory("CAT_LOAN");
        p2.setProductDeptOrgCode("ORG_HQ_LOAN");

        when(productInfoMapper.listSupportAvailable()).thenReturn(Arrays.asList(p1, p2));

        // Act
        List<ProductSimpleDTO> result = productService.listSupportAvailable();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductCode()).isEqualTo("DEPOSIT_001");
        assertThat(result.get(0).getProductName()).isEqualTo("活期存款");
        assertThat(result.get(1).getProductCode()).isEqualTo("LOAN_001");
    }

    @Test
    void listSupportAvailableShouldReturnEmptyListWhenMapperReturnsEmpty() {
        when(productInfoMapper.listSupportAvailable()).thenReturn(Collections.emptyList());

        List<ProductSimpleDTO> result = productService.listSupportAvailable();

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2:** Run test, expect FAIL (`ProductService` class does not exist):
  ```
  mvn test -pl portal-content-center -Dtest=ProductServiceTest
  Expected: COMPILATION ERROR or test class init failure
  ```
- [ ] **Step 3 (GREEN):** Create `ProductService.java`:

```java
package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.convert.ProductConverter;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品资料库 Service
 * D.1-D.7 的业务逻辑实现入口
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductInfoMapper productInfoMapper;

    /**
     * D.3 查询所有支持中场支持的产品（active + 未删除）
     * V1 不实现 5min Redis 缓存（spec 附录 B2 登记的 V2 待办）
     */
    public List<ProductSimpleDTO> listSupportAvailable() {
        return productInfoMapper.listSupportAvailable().stream()
            .map(ProductConverter::toSimple)
            .collect(Collectors.toList());
    }
}
```

- [ ] **Step 4:** Run tests, expect PASS:
  ```
  mvn test -pl portal-content-center -Dtest=ProductServiceTest
  Expected: 2 tests, 0 failures
  ```
- [ ] **Step 5 (REFACTOR):** No refactor needed at this size. Service is minimal.
- [ ] **Step 6:** **DO NOT commit yet** — Task 3.1 is part of Task 3.x (full D.3) and will commit at the end (Step in Task 3.3)

### Task 3.2: ProductInfoMapper.listSupportAvailable Integration Test

**Files:**
- Modify: `portal-content-center/src/test/java/.../portal/mapper/ProductInfoMapperIntegrationTest.java` (extend with one more test if not already covered in Task 2.1)

**Context:** Task 2.1 already includes `listSupportAvailableShouldReturnOnlyActiveAndSupporting`. Verify it still passes.

- [ ] **Step 1:** Run the existing integration test:
  ```
  mvn test -pl portal-content-center -Dtest=ProductInfoMapperIntegrationTest#listSupportAvailableShouldReturnOnlyActiveAndSupporting
  Expected: PASS
  ```
- [ ] **Step 2:** If failing, debug the XML — common issues:
  - `WHERE support_for_support_request = 1` (not `= true`, MySQL stores as 1/0)
  - `AND status = 'ACTIVE'`
  - `AND deleted = 0`
- [ ] **Step 3:** No commit yet — proceed to Task 3.3

### Task 3.3: ProductController + Controller Integration Test

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/controller/ProductController.java`
- Create: `portal-content-center/src/test/java/.../portal/controller/ProductControllerTest.java`

**Critical (route order):** `@GetMapping("/support-available")` MUST be declared BEFORE `@GetMapping("/{id:[A-Za-z0-9_-]{1,64}}")` in the source file. Spring registers handlers in declaration order.

- [ ] **Step 1 (RED):** Create `ProductControllerTest.java`:

```java
package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ProductService productService;

    @Test
    @WithMockEmpContext(empId = "E10001", roleCodes = {"R_RM"})
    void getSupportAvailableShouldReturn200WithProductList() throws Exception {
        ProductSimpleDTO p1 = new ProductSimpleDTO();
        p1.setId("P001");
        p1.setProductCode("DEPOSIT_001");
        p1.setProductName("活期存款");
        p1.setProductCategory("CAT_DEPOSIT");
        p1.setProductDeptOrgCode("ORG_HQ_FIN");
        when(productService.listSupportAvailable()).thenReturn(Arrays.asList(p1));

        mockMvc.perform(get("/api/products/support-available"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].productCode").value("DEPOSIT_001"))
            .andExpect(jsonPath("$.data[0].productName").value("活期存款"));
    }

    @Test
    @WithMockEmpContext
    void getSupportAvailableShouldReturn200WithEmptyArrayWhenNoProducts() throws Exception {
        when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/products/support-available"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }
}
```

- [ ] **Step 2:** Run test, expect FAIL (Controller does not exist):
  ```
  mvn test -pl portal-content-center -Dtest=ProductControllerTest
  Expected: 404 (no handler for /api/products/support-available)
  ```
- [ ] **Step 3 (GREEN):** Create `ProductController.java`:

```java
package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品资料库 REST Controller (D.1-D.7)
 *
 * 路由顺序约束：/support-available 必须在 /{id} 之前声明，
 * 否则 Spring MVC 会把 "support-available" 匹配为 {id} 路径变量。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** D.3 查询支持中场支持的产品 */
    @GetMapping("/support-available")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.READ)
    public ResponseWrapper<List<ProductSimpleDTO>> getSupportAvailable() {
        return ResponseWrapper.success(productService.listSupportAvailable());
    }

    // D.1 list / D.2 detail / D.4-D.7 will be added in later tasks
    // IMPORTANT: when adding @GetMapping("/{id}"), declare it AFTER /support-available
}
```

- [ ] **Step 4:** Run test, expect PASS:
  ```
  mvn test -pl portal-content-center -Dtest=ProductControllerTest
  Expected: 2 tests, 0 failures
  ```
- [ ] **Step 5 (REFACTOR):** No refactoring needed.
- [ ] **Step 6:** Run ALL portal tests to make sure nothing else broke:
  ```
  mvn test -pl portal-content-center
  Expected: All tests pass
  ```
- [ ] **Step 7:** Commit:
  ```
  feat(portal): D.3 查询支持中场支持的产品 (首个 TDD 闭环)

  - ProductService.listSupportAvailable Service 单测通过
  - ProductInfoMapper.listSupportAvailable Mapper 集成测试通过
  - ProductController GET /api/products/support-available 集成测试通过
  - 路由顺序约束：/support-available 在 /{id} 之前声明
  ```

---

## Phase 4: D.1 GET /api/products — Product List with DATA_SCOPE

### Task 4.1: ProductInfoMapper.listProducts (含 DATA_SCOPE 过滤片段)

**Files:**
- Modify: `portal-content-center/src/main/java/.../portal/mapper/ProductInfoMapper.java` — add `listProducts` and `countProducts`
- Modify: `portal-content-center/src/main/resources/mapper/portal/ProductInfoMapper.xml` — add `<sql id="dataScopeFilter">` and `<select>` blocks
- Create: `portal-content-center/src/main/java/.../portal/service/dto/ProductListQuery.java` — query parameter object
- Modify: `portal-content-center/src/test/java/.../portal/mapper/ProductInfoMapperIntegrationTest.java` — add 4 tests for DATA_SCOPE filter

**Context:** This is the first task that uses DATA_SCOPE. Read `docs/common-dev-guide.md §5.1` for the full DataScopeType reference. The XML `dataScopeFilter` SQL fragment is reusable across all portal mappers (extract to a shared file in V2).

- [ ] **Step 1:** Create `ProductListQuery.java` POJO with fields:

```java
package com.bank.branch.platform.portal.service.dto;

import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductListQuery {
    private String keyword;
    private String category;
    private String status;             // ACTIVE / DISABLED / ALL，默认 ACTIVE
    private String productDeptOrgCode; // 03 §D.1 列表的可选过滤
    private Boolean supportForSupportRequest;
    private Integer offset;            // pageNo - 1) * pageSize
    private Integer limit;             // pageSize
    private DataScopeContext dataScope;
}
```

- [ ] **Step 2:** Add to `ProductInfoMapper.java`:

```java
List<ProductInfo> listProducts(@Param("q") ProductListQuery query);

long countProducts(@Param("q") ProductListQuery query);
```

- [ ] **Step 3:** Add to `ProductInfoMapper.xml`:

```xml
<!-- 通用 DATA_SCOPE 过滤片段。reuse 到 portal 其他 mapper 时可复制 -->
<sql id="dataScopeFilter">
    <choose>
        <when test="q.dataScope == null"> AND 1 = 0 </when>
        <when test="q.dataScope.scopeType.name() == 'ALL'"/>
        <when test="q.dataScope.scopeType.name() == 'ORG_SUBTREE'">
            AND product_dept_org_code IN
            <foreach collection="q.dataScope.orgSubtreeCodes" item="o" open="(" close=")" separator=",">#{o}</foreach>
        </when>
        <when test="q.dataScope.scopeType.name() == 'ORG'">
            AND product_dept_org_code = #{q.dataScope.orgCode}
        </when>
        <when test="q.dataScope.scopeType.name() == 'SELF_CREATED'">
            AND created_by = #{q.dataScope.empId}
        </when>
        <otherwise> AND 1 = 0 </otherwise>
    </choose>
</sql>

<sql id="listProductsWhere">
    <where>
        AND deleted = 0
        <if test="q.status == null or q.status == 'ACTIVE'"> AND status = 'ACTIVE' </if>
        <if test="q.status != null and q.status == 'DISABLED'"> AND status = 'DISABLED' </if>
        <if test="q.keyword != null and q.keyword != ''">
            AND (product_name LIKE CONCAT('%', #{q.keyword}, '%')
              OR product_code LIKE CONCAT('%', #{q.keyword}, '%'))
        </if>
        <if test="q.category != null and q.category != ''"> AND product_category = #{q.category} </if>
        <if test="q.productDeptOrgCode != null and q.productDeptOrgCode != ''">
            AND product_dept_org_code = #{q.productDeptOrgCode}
        </if>
        <if test="q.supportForSupportRequest != null">
            AND support_for_support_request = #{q.supportForSupportRequest}
        </if>
        <include refid="dataScopeFilter"/>
    </where>
</sql>

<select id="listProducts" resultMap="ProductInfoResultMap">
    SELECT <include refid="BASE_COLUMNS"/> FROM product_info
    <include refid="listProductsWhere"/>
    ORDER BY updated_time DESC
    LIMIT #{q.offset}, #{q.limit}
</select>

<select id="countProducts" resultType="long">
    SELECT COUNT(*) FROM product_info
    <include refid="listProductsWhere"/>
</select>
```

- [ ] **Step 4 (RED):** Add 4 integration tests to `ProductInfoMapperIntegrationTest.java`:
  1. `listProductsWithDataScopeAllShouldReturnAll()` — insert 3 products in 3 different orgs, query with `scopeType=ALL`, verify all 3 returned
  2. `listProductsWithDataScopeOrgSubtreeShouldFilter()` — insert 3 products in `ORG_SZ_001 / ORG_SZ_002 / ORG_BJ_001`, query with `scopeType=ORG_SUBTREE` and `orgSubtreeCodes=[ORG_SZ_001, ORG_SZ_002]`, verify 2 returned
  3. `listProductsWithDataScopeOrgShouldReturnOnlyOwnOrg()` — `scopeType=ORG, orgCode=ORG_SZ_001`, verify only that org's product returned
  4. `listProductsWithUnknownScopeShouldReturnEmpty()` — pass a `null` DataScope or invalid scopeType, verify 0 results (Fail Close)
- [ ] **Step 5:** Run, iterate XML, expect PASS
- [ ] **Step 6:** Don't commit yet — combine with Task 4.2 and Task 4.3.

### Task 4.2: ProductService.listProducts + Service Test

**Files:**
- Modify: `portal-content-center/src/main/java/.../portal/service/ProductService.java` — add `listProducts` method
- Modify: `portal-content-center/src/main/java/.../portal/convert/ProductConverter.java` — add `toListItem(ProductInfo)` method
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ProductListReqDTO.java`
- Modify: `portal-content-center/src/test/java/.../portal/service/ProductServiceTest.java` — add tests

**Context:** Service should:
1. Translate request DTO + DataScope → ProductListQuery
2. Call `mapper.listProducts` and `mapper.countProducts`
3. Convert entities to `ProductDTO` (call DictApi for category translation, OrgApi for org name)
4. Return `PageResult<ProductDTO>`

For now, the conversion of `productCategoryDesc / productDeptOrgName / fileName / responsibleEmps / updatedByName` might be simplified — leave TODO comments and implement minimal projection (just the entity → DTO field copy).

- [ ] **Step 1:** Create `ProductListReqDTO.java` (Controller-facing request DTO with `@Min` validation)
- [ ] **Step 2 (RED):** Add to `ProductServiceTest.java`:

```java
@Test
void listProductsShouldReturnPageResultWithMappedDTO() {
    DataScopeContext scope = new DataScopeContext(
        DataScopeType.ORG_SUBTREE, "E10001", "ORG_SZ_001",
        Set.of("ORG_SZ_001", "ORG_SZ_002"),
        BizType.PRODUCT, BizAction.LIST);

    ProductInfo entity = newEntity("P001", "DEPOSIT_001", "ORG_SZ_001");
    when(productInfoMapper.countProducts(any())).thenReturn(1L);
    when(productInfoMapper.listProducts(any())).thenReturn(List.of(entity));

    PageResult<ProductDTO> result = productService.listProducts(
        ProductListReqDTO.builder().pageNo(1).pageSize(20).status("ACTIVE").build(),
        scope);

    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getRecords()).hasSize(1);
    assertThat(result.getRecords().get(0).getProductCode()).isEqualTo("DEPOSIT_001");
}

@Test
void listProductsShouldReturnEmptyPageWhenCountIsZero() {
    when(productInfoMapper.countProducts(any())).thenReturn(0L);
    DataScopeContext scope = new DataScopeContext(
        DataScopeType.ALL, "E10001", "ORG_SZ_001", Set.of(),
        BizType.PRODUCT, BizAction.LIST);

    PageResult<ProductDTO> result = productService.listProducts(
        ProductListReqDTO.builder().pageNo(1).pageSize(20).build(), scope);

    assertThat(result.getTotal()).isZero();
    assertThat(result.getRecords()).isEmpty();
    // Should not call listProducts when count == 0 (optimization)
    verify(productInfoMapper, never()).listProducts(any());
}
```

- [ ] **Step 3:** Run, expect FAIL
- [ ] **Step 4 (GREEN):** Implement `ProductService.listProducts` (with the count==0 optimization). Use `ProductConverter.toListItem(entity)` (which initially is a minimal field-copy converter — full enrichment with DictApi/OrgApi happens in Task 5 for D.2 detail; for D.1 list V1 leaves productCategoryDesc/productDeptOrgName empty with TODO comment)
- [ ] **Step 5:** Run, expect PASS

### Task 4.3: ProductController GET /api/products + Controller Test + Commit

**Files:**
- Modify: `portal-content-center/src/main/java/.../portal/controller/ProductController.java` — add `listProducts` endpoint
- Modify: `portal-content-center/src/test/java/.../portal/controller/ProductControllerTest.java` — add tests
- Modify: `portal-content-center/src/main/java/.../portal/controller/ProductController.java` — inject `BizScopeApi` to obtain DataScopeContext

- [ ] **Step 1 (RED):** Add to `ProductControllerTest.java`:

```java
@Test
@WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001", "ORG_SZ_002"})
void listProductsShouldReturn200WithPagedResult() throws Exception {
    PageResult<ProductDTO> page = PageResult.of(1, 20, 1L, List.of(/* 1 ProductDTO */));
    when(productService.listProducts(any(), any())).thenReturn(page);

    mockMvc.perform(get("/api/products")
            .param("pageNo", "1")
            .param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("0"))
        .andExpect(jsonPath("$.page.total").value(1));
}

@Test
@WithMockEmpContext
void listProductsShouldReturn400WhenPageSizeExceedsLimit() throws Exception {
    mockMvc.perform(get("/api/products").param("pageSize", "200"))
        .andExpect(status().is4xxClientError());
}
```

- [ ] **Step 2:** Run, expect FAIL
- [ ] **Step 3 (GREEN):** Add to `ProductController.java`:

```java
@GetMapping
@BizAuth(bizType = BizType.PRODUCT, action = BizAction.LIST)
public ResponseWrapper<PageResult<ProductDTO>> listProducts(
        @Valid ProductListReqDTO req
) {
    String empId = currentUserApi.getCurrentEmpId();
    DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.PRODUCT, BizAction.LIST);
    PageResult<ProductDTO> result = productService.listProducts(req, scope);
    return ResponseWrapper.page(result);
}
```

- [ ] **Step 4:** Run all tests, expect PASS
- [ ] **Step 5:** Commit:
  ```
  feat(portal): D.1 产品列表分页查询 + DATA_SCOPE 过滤

  - ProductInfoMapper.listProducts/countProducts (含可复用 dataScopeFilter SQL 片段)
  - 4 个集成测试覆盖 ALL/ORG_SUBTREE/ORG/Fail Close 4 种数据范围
  - ProductService.listProducts (count==0 优化)
  - ProductController GET /api/products (引入 BizScopeApi 构建 DataScopeContext)
  ```

---

## Phase 5: D.2 GET /api/products/{id} — Product Detail (Cross-Module Aggregation)

### Task 5.1: ProductService.getProduct + DTO 字段补齐

**Files:**
- Modify: `portal-content-center/src/main/java/.../portal/service/ProductService.java` — add `getProduct` method
- Modify: `portal-content-center/src/main/java/.../portal/convert/ProductConverter.java` — add `toDetail` method
- Create: `portal-content-center/src/main/java/.../portal/service/AddrbookQueryService.java` — internal service for addrbook queries (used by D.2 + D.4/D.5/D.6 validation)
- Modify: `portal-content-center/src/test/java/.../portal/service/ProductServiceTest.java` — add `getProduct` tests
- Create: `portal-content-center/src/test/java/.../portal/service/AddrbookQueryServiceTest.java`

**Context:** D.2 is the first interface that aggregates data from multiple sources:
- `ProductInfoMapper.selectById` — local
- `DictApi.getDictLabel("PRODUCT_CATEGORY", productCategory)` — governance
- `OrgApi.getOrg(productDeptOrgCode)` — auth
- `FileApi.getDownloadUrl(fileObjectId)` — governance
- `AddrbookEmployeeMapper.listByEmpIds(responsibleEmpIds)` — local (used via AddrbookQueryService)
- Mobile masking with `SensitiveDataMasker.maskMobile(...)` — common-security

- [ ] **Step 1:** Create `AddrbookQueryService.java`:

```java
package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.common.security.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通讯录内部查询服务（不开 REST，仅供 portal 模块内部 D.2/D.4/D.5/D.6 使用）
 */
@Service
@RequiredArgsConstructor
public class AddrbookQueryService {

    private final AddrbookEmployeeMapper addrbookMapper;

    /**
     * 批量按 empId 查询并转换为 ResponsibleEmpDTO（含手机号脱敏）
     */
    public List<ResponsibleEmpDTO> listResponsibleEmps(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) return Collections.emptyList();
        return addrbookMapper.listByEmpIds(empIds).stream()
            .map(this::toResponsibleEmpDTO)
            .collect(Collectors.toList());
    }

    /**
     * 校验 empIds 全部存在且 ACTIVE，返回不存在或非 ACTIVE 的 empId 集合
     */
    public List<String> findInvalidEmpIds(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) return Collections.emptyList();
        int activeCount = addrbookMapper.countActiveByEmpIds(empIds);
        if (activeCount == empIds.size()) return Collections.emptyList();
        // Slow path: load all and find missing
        List<String> foundActive = addrbookMapper.listByEmpIds(empIds).stream()
            .filter(e -> "ACTIVE".equals(e.getStatus()))
            .map(AddrbookEmployee::getEmpId)
            .collect(Collectors.toList());
        return empIds.stream().filter(id -> !foundActive.contains(id)).collect(Collectors.toList());
    }

    private ResponsibleEmpDTO toResponsibleEmpDTO(AddrbookEmployee e) {
        ResponsibleEmpDTO dto = new ResponsibleEmpDTO();
        dto.setEmpId(e.getEmpId());
        dto.setEmpName(e.getEmpName());
        dto.setMobile(SensitiveDataMasker.maskMobile(e.getMobile()));
        dto.setPosition(e.getPosition());
        return dto;
    }
}
```

- [ ] **Step 2 (RED):** Create `AddrbookQueryServiceTest.java` — Mockito test for both methods:
  - `listResponsibleEmps` should return DTOs with masked mobile
  - `findInvalidEmpIds` should return only missing/inactive empIds
- [ ] **Step 3 (GREEN):** Implementation done in Step 1
- [ ] **Step 4:** Add to `ProductConverter.java`:

```java
public static ProductDetailDTO toDetail(
        ProductInfo entity,
        String categoryDesc,
        String orgName,
        String fileDownloadUrl,
        List<ResponsibleEmpDTO> responsibleEmps,
        boolean canEdit) {
    if (entity == null) return null;
    ProductDetailDTO dto = new ProductDetailDTO();
    // ... copy all fields, set categoryDesc/orgName/fileDownloadUrl/responsibleEmps/canEdit
    return dto;
}
```

- [ ] **Step 5 (RED):** Add to `ProductServiceTest.java`:

```java
@Mock DictApi dictApi;
@Mock OrgApi orgApi;
@Mock FileApi fileApi;
@Mock AddrbookQueryService addrbookQueryService;
@Mock CurrentUserApi currentUserApi;

@Test
void getProductShouldThrowProductNotFoundWhenNoEntity() {
    when(productInfoMapper.selectById("P999")).thenReturn(null);

    assertThatThrownBy(() -> productService.getProduct("P999"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-40003");
}

@Test
void getProductShouldEnrichWithDictAndOrgAndFile() {
    ProductInfo entity = newEntity("P001", "DEPOSIT_001", "ORG_SZ_001");
    entity.setProductCategory("CAT_DEPOSIT");
    entity.setFileObjectId("FILE_ABC");
    entity.setResponsibleEmpIds(List.of("E10001"));

    when(productInfoMapper.selectById("P001")).thenReturn(entity);
    when(dictApi.getDictLabel("PRODUCT_CATEGORY", "CAT_DEPOSIT")).thenReturn("存款类");
    OrgDTO org = new OrgDTO(); org.setOrgName("深圳分行");
    when(orgApi.getOrg("ORG_SZ_001")).thenReturn(org);
    when(fileApi.getDownloadUrl("FILE_ABC")).thenReturn("https://minio/file/abc");
    when(addrbookQueryService.listResponsibleEmps(List.of("E10001")))
        .thenReturn(List.of(/* one ResponsibleEmpDTO */));
    when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");

    ProductDetailDTO result = productService.getProduct("P001");

    assertThat(result.getProductCategoryDesc()).isEqualTo("存款类");
    assertThat(result.getProductDeptOrgName()).isEqualTo("深圳分行");
    assertThat(result.getFileDownloadUrl()).isEqualTo("https://minio/file/abc");
    assertThat(result.getCanEdit()).isTrue();  // current user's org matches
}
```

- [ ] **Step 6:** Run, expect FAIL
- [ ] **Step 7 (GREEN):** Implement `ProductService.getProduct` to call all the dependencies and pass through `ProductConverter.toDetail`. Catch `BizException(GOV-40005)` from `FileApi.getDownloadUrl` and set `fileDownloadUrl=null` (file deleted, but product still viewable)
- [ ] **Step 8:** Run, expect PASS
- [ ] **Step 9:** No commit yet — proceed to Task 5.2

### Task 5.2: ProductController.getProduct + Controller Test + Commit

**Files:**
- Modify: `ProductController.java` — add `getProduct` endpoint with the route order约束
- Modify: `ProductControllerTest.java` — add tests

- [ ] **Step 1 (RED):** Add to `ProductControllerTest.java`:

```java
@Test
@WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001", dataScope = "ORG_SUBTREE")
void getProductShouldReturn200WithDetailDTO() throws Exception {
    ProductDetailDTO dto = new ProductDetailDTO();
    dto.setId("P001");
    dto.setProductCode("DEPOSIT_001");
    when(productService.getProduct("P001")).thenReturn(dto);

    mockMvc.perform(get("/api/products/P001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("P001"))
        .andExpect(jsonPath("$.data.productCode").value("DEPOSIT_001"));
}

@Test
@WithMockEmpContext
void getProductShouldReturn4xxWhenNotFound() throws Exception {
    when(productService.getProduct("P999"))
        .thenThrow(new BizException("PORTAL-40003", "产品不存在"));

    mockMvc.perform(get("/api/products/P999"))
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.code").value("PORTAL-40003"));
}

@Test
@WithMockEmpContext
void supportAvailableEndpointShouldNotBeMatchedAsIdPathVariable() throws Exception {
    // Critical: ensure /support-available is matched by D.3, not D.2 with id="support-available"
    when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());
    // If D.2 was matched, productService.getProduct("support-available") would be invoked instead
    mockMvc.perform(get("/api/products/support-available"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
    verify(productService, never()).getProduct("support-available");
}
```

- [ ] **Step 2:** Run, expect FAIL
- [ ] **Step 3 (GREEN):** Add to `ProductController.java` (AFTER the `/support-available` mapping):

```java
/** D.2 产品详情 */
@GetMapping("/{id:[A-Za-z0-9_-]{1,64}}")
@BizAuth(bizType = BizType.PRODUCT, action = BizAction.READ)
public ResponseWrapper<ProductDetailDTO> getProduct(@PathVariable String id) {
    return ResponseWrapper.success(productService.getProduct(id));
}
```

- [ ] **Step 4:** Run all tests, expect PASS (especially the route-order test)
- [ ] **Step 5:** Commit:
  ```
  feat(portal): D.2 产品详情含字典翻译/机构名/附件下载链接/负责人脱敏

  - ProductService.getProduct 聚合 DictApi/OrgApi/FileApi/AddrbookQueryService
  - AddrbookQueryService 内部查询服务（不开 REST）
  - SensitiveDataMasker 对负责人手机号脱敏
  - 路由顺序约束测试：/support-available 不被 /{id} 匹配
  ```

---

## Phase 6: D.4 POST /api/products — Create Product with AFTER_COMMIT Event

### Task 6.0: ProductResponsibleUpdatedEvent + Listener (基础设施)

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/api/event/ProductResponsibleUpdatedEvent.java`
- Create: `portal-content-center/src/main/java/.../portal/listener/ProductResponsibleSyncListener.java`
- Create: `portal-content-center/src/test/java/.../portal/listener/ProductResponsibleSyncListenerTest.java`

**Context:** This is the in-process event used by D.4/D.5/D.6 for the bidirectional addrbook sync. Per spec §4a, V1 uses `@TransactionalEventListener(AFTER_COMMIT)` (no outbox).

- [ ] **Step 1:** Create `ProductResponsibleUpdatedEvent.java`:

```java
package com.bank.branch.platform.portal.api.event;

import lombok.Value;

import java.util.List;

/**
 * 产品负责人列表变更事件（V1 进程内发布，AFTER_COMMIT 触发消费者）
 *
 * V2 升级路径：sys_event_outbox 基础设施就绪后，replace publishEvent with outboxWriter.write
 */
@Value
public class ProductResponsibleUpdatedEvent {
    String productId;
    List<String> removed;  // 被移除的 empId 列表
    List<String> added;    // 新增的 empId 列表
    String source;         // PRODUCT_SIDE / ADDRBOOK_SIDE — 防止反向触发
    String operatorEmpId;  // 操作人，用于乐观锁更新的 updated_by
}
```

- [ ] **Step 2:** Create `ProductResponsibleSyncListener.java`:

```java
package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.portal.api.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductResponsibleSyncListener {

    private final AddrbookEmployeeMapper addrbookMapper;

    /**
     * 在主事务提交后，独立事务内更新 addrbook_employee 的 responsible_product_ids
     * 失败重试 3 次（指数退避 100/500/2000ms），仍失败记 ERROR 日志（V1 无 DLQ）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProductResponsibleUpdated(ProductResponsibleUpdatedEvent event) {
        // 仅处理 PRODUCT_SIDE 来源，避免循环同步
        if (!"PRODUCT_SIDE".equals(event.getSource())) {
            log.debug("Skip event from source={}", event.getSource());
            return;
        }

        // 处理 removed: 从这些员工的 responsibleProductIds 中移除 productId
        for (String empId : event.getRemoved()) {
            updateWithRetry(empId, event.getProductId(), event.getOperatorEmpId(), false);
        }
        // 处理 added: 追加 productId
        for (String empId : event.getAdded()) {
            updateWithRetry(empId, event.getProductId(), event.getOperatorEmpId(), true);
        }
    }

    private void updateWithRetry(String empId, String productId, String operator, boolean isAdd) {
        long[] backoffMs = {100, 500, 2000};
        for (int attempt = 0; attempt < 3; attempt++) {
            AddrbookEmployee emp = addrbookMapper.selectByEmpId(empId);
            if (emp == null) {
                log.warn("addrbook employee {} not found, skip sync", empId);
                return;
            }
            List<String> current = emp.getResponsibleProductIds() != null
                ? new ArrayList<>(emp.getResponsibleProductIds())
                : new ArrayList<>();
            if (isAdd) {
                if (!current.contains(productId)) current.add(productId);
            } else {
                current.remove(productId);
            }
            int rows = addrbookMapper.updateResponsibleProductsWithOptimisticLock(
                empId, current, emp.getUpdatedTime(), operator);
            if (rows > 0) return;
            // 乐观锁失败，重试
            try { Thread.sleep(backoffMs[attempt]); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            log.debug("Optimistic lock retry {} for empId={}", attempt + 1, empId);
        }
        log.error("addrbook sync failed after 3 retries: empId={} productId={} isAdd={}", empId, productId, isAdd);
    }
}
```

- [ ] **Step 3 (RED):** Create `ProductResponsibleSyncListenerTest.java` (Mockito test, not Spring integration). Tests:
  1. `shouldSkipNonProductSideEvent()` — event.source=ADDRBOOK_SIDE, verify mapper never called
  2. `shouldAppendProductIdToAddedEmployees()` — fire event with `added=[E001]`, mock selectByEmpId returning emp with `responsibleProductIds=[P_OLD]`, verify update called with `[P_OLD, NEW_P]`
  3. `shouldRemoveProductIdFromRemovedEmployees()` — similar but for removed
  4. `shouldRetry3TimesOnOptimisticLockFailureAndLogError()` — mock update returns 0 always; verify call count == 3, verify log captured (use `LogCaptor` or assert no exception thrown)
- [ ] **Step 4:** Run, expect FAIL → Step 5 GREEN → PASS
- [ ] **Step 5:** No commit yet — combine with Task 6.1/6.2/6.3

### Task 6.1: ProductService.createProduct (Service Test)

**Files:**
- Modify: `portal-content-center/src/main/java/.../portal/service/ProductService.java` — add `createProduct(cmd, operatorEmpId)` method
- Create: `portal-content-center/src/main/java/.../portal/service/dto/ProductCreateCmd.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ProductCreateReqDTO.java`
- Modify: `ProductServiceTest.java` — add 6 test cases

**Context:** Implements all business rules from spec §4.4. Pre-validation runs OUTSIDE the transaction (read-only checks). Transaction body: insert + bindFile + audit + publishEvent.

- [ ] **Step 1:** Create `ProductCreateReqDTO.java` with fields per 03 §D.4 + JSR-303 annotations:

```java
package com.bank.branch.platform.portal.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ProductCreateReqDTO {
    @NotBlank @Size(max = 64)
    private String productCode;

    @NotBlank @Size(max = 255)
    private String productName;

    @NotBlank @Size(max = 64)
    private String productCategory;

    @Size(max = 5000)
    private String description;

    @NotNull
    private Boolean supportForSupportRequest;

    @NotBlank @Size(max = 50)
    private String productDeptOrgCode;

    @Size(max = 64)
    private String fileObjectId;

    @Size(max = 10, message = "负责人最多 10 个")
    private List<String> responsibleEmpIds;
}
```

- [ ] **Step 2:** Create `ProductCreateCmd.java` (Service-internal command, similar but without JSR-303)
- [ ] **Step 3 (RED):** Add to `ProductServiceTest.java`:

```java
@Mock ApplicationEventPublisher eventPublisher;

@Test
void createProductShouldThrow40302WhenUserOrgMismatchAndNotAdmin() {
    when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
    when(currentUserApi.isSystemAdmin()).thenReturn(false);
    ProductCreateCmd cmd = ProductCreateCmd.builder()
        .productDeptOrgCode("ORG_BJ_001")  // different org
        .productCode("DEPOSIT_001")
        .build();

    assertThatThrownBy(() -> productService.createProduct(cmd, "E10001"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-40302");
}

@Test
void createProductShouldThrow42200WhenCategoryDictInvalid() {
    when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
    when(dictApi.isValidDictValue("PRODUCT_CATEGORY", "INVALID_CAT")).thenReturn(false);
    ProductCreateCmd cmd = ProductCreateCmd.builder()
        .productCategory("INVALID_CAT")
        .productDeptOrgCode("ORG_SZ_001")
        .build();

    assertThatThrownBy(() -> productService.createProduct(cmd, "E10001"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-42200");
}

@Test
void createProductShouldThrow42203WhenFileObjectNotExist() {
    // Setup pre-checks pass except fileApi throws GOV-40005
    when(fileApi.getDownloadUrl("BAD_FILE")).thenThrow(new BizException("GOV-40005", "文件不存在"));
    // ... rest of setup

    assertThatThrownBy(() -> productService.createProduct(cmdWithFile, "E10001"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-42203");
}

@Test
void createProductShouldThrow40902WhenResponsibleEmpInvalid() {
    when(addrbookQueryService.findInvalidEmpIds(List.of("E_RESIGNED")))
        .thenReturn(List.of("E_RESIGNED"));
    // ... rest of setup

    assertThatThrownBy(() -> productService.createProduct(cmdWithBadEmp, "E10001"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-40902");
}

@Test
void createProductShouldInsertAndBindFileAndPublishEvent() {
    // Setup all pre-checks to pass
    when(productInfoMapper.insert(any())).thenReturn(1);
    // ...

    String productId = productService.createProduct(validCmd, "E10001");

    assertThat(productId).isNotEmpty();
    verify(productInfoMapper).insert(any());
    verify(fileApi).bindFile(eq("PRODUCT"), eq(productId), eq("FILE_ABC"), eq("MAIN"));
    verify(auditApi).log(any());
    ArgumentCaptor<ProductResponsibleUpdatedEvent> evtCaptor = ArgumentCaptor.forClass(ProductResponsibleUpdatedEvent.class);
    verify(eventPublisher).publishEvent(evtCaptor.capture());
    assertThat(evtCaptor.getValue().getAdded()).containsExactly("E10001", "E10002");
    assertThat(evtCaptor.getValue().getRemoved()).isEmpty();
    assertThat(evtCaptor.getValue().getSource()).isEqualTo("PRODUCT_SIDE");
}

@Test
void createProductShouldThrow40901WhenProductCodeDuplicate() {
    when(productInfoMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_product_code"));
    // ... rest of setup

    assertThatThrownBy(() -> productService.createProduct(validCmd, "E10001"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-40901");
}
```

- [ ] **Step 4:** Run, expect FAIL
- [ ] **Step 5 (GREEN):** Implement `ProductService.createProduct`. Use `@Transactional(rollbackFor = Exception.class)`. UUID generation: `java.util.UUID.randomUUID().toString().replace("-", "")`.
- [ ] **Step 6:** Run, expect PASS
- [ ] **Step 7:** No commit — proceed to Task 6.2

### Task 6.2: ProductController POST /api/products + Controller Test

**Files:**
- Modify: `ProductController.java` — add `createProduct` POST endpoint
- Modify: `ProductControllerTest.java` — add tests

- [ ] **Step 1 (RED):** Add to `ProductControllerTest.java`:

```java
@Test
@WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
void createProductShouldReturn200WithProductId() throws Exception {
    when(productService.createProduct(any(), eq("E10001"))).thenReturn("NEW_P_001");

    String body = """
        {"productCode":"DEPOSIT_001","productName":"活期存款","productCategory":"CAT_DEPOSIT","supportForSupportRequest":true,"productDeptOrgCode":"ORG_SZ_001"}
        """;
    mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("NEW_P_001"));
}

@Test
@WithMockEmpContext
void createProductShouldReturn4xxWhenProductCodeMissing() throws Exception {
    String body = "{}";
    mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().is4xxClientError());
}
```

- [ ] **Step 2 (GREEN):** Add to `ProductController.java`:

```java
@PostMapping
@BizAuth(bizType = BizType.PRODUCT, action = BizAction.WRITE)
public ResponseWrapper<String> createProduct(@Valid @RequestBody ProductCreateReqDTO req) {
    String empId = currentUserApi.getCurrentEmpId();
    ProductCreateCmd cmd = /* convert req to cmd */;
    return ResponseWrapper.success(productService.createProduct(cmd, empId));
}
```

- [ ] **Step 3:** Run, expect PASS
- [ ] **Step 4:** Commit:
  ```
  feat(portal): D.4 新增产品 + AFTER_COMMIT 事件 + 双向同步消费者

  - ProductResponsibleUpdatedEvent 事件类
  - ProductResponsibleSyncListener (TransactionPhase.AFTER_COMMIT + REQUIRES_NEW)
    - 乐观锁失败重试 3 次 (100/500/2000ms 指数退避)
    - 仅处理 PRODUCT_SIDE 来源，防止循环同步
  - ProductService.createProduct (6 个 Service 单测覆盖所有错误码路径)
  - ProductController POST /api/products
  ```

---

## Phase 7: D.5 PUT /api/products/{id} — Edit Product with FOR UPDATE Lock

### Task 7.1: ProductService.updateProduct + Tests + Controller + Commit

**Files:**
- Modify: `ProductService.java` — add `updateProduct(id, cmd, operatorEmpId)`
- Create: `ProductUpdateReqDTO.java` / `ProductUpdateCmd.java` (similar pattern to D.4 but all fields optional)
- Modify: `ProductController.java` — add PUT endpoint
- Modify: `ProductServiceTest.java` and `ProductControllerTest.java` — add tests

**Context:** D.5 differs from D.4 in:
1. **Pessimistic lock**: `selectByIdForUpdate(id)` to lock the row
2. **Diff calculation**: compute `removed` and `added` from old vs new `responsibleEmpIds`
3. **Immutable fields**: `productCode` and `productDeptOrgCode` are NOT in the request DTO (compile-time enforced)
4. **File swap**: V1 only `bindFile` (no unbind, technical debt B8)

- [ ] **Step 1:** Create `ProductUpdateReqDTO.java` (NO productCode / NO productDeptOrgCode):

```java
@Data
public class ProductUpdateReqDTO {
    @Size(max = 255) private String productName;
    @Size(max = 64) private String productCategory;
    @Size(max = 5000) private String description;
    private Boolean supportForSupportRequest;
    @Size(max = 64) private String fileObjectId;
    @Pattern(regexp = "ACTIVE|DISABLED") private String status;
    @Size(max = 10) private List<String> responsibleEmpIds;
}
```

- [ ] **Step 2 (RED):** Add tests to `ProductServiceTest.java`:
  1. `updateProductShouldThrow40003WhenNotFound()`
  2. `updateProductShouldThrow40302WhenUserOrgMismatch()`
  3. `updateProductShouldUseForUpdateLock()` — verify `selectByIdForUpdate` called (not `selectById`)
  4. `updateProductShouldComputeDiffAndPublishEventForResponsibleEmpIds()` — old=[E1,E2], new=[E2,E3], expect event with removed=[E1], added=[E3]
  5. `updateProductShouldNotPublishEventWhenResponsibleEmpIdsUnchanged()`
  6. `updateProductShouldCallBindFileWhenFileObjectIdChanged()`
- [ ] **Step 3 (GREEN):** Implement `updateProduct`. Diff logic:
  ```java
  Set<String> oldSet = new HashSet<>(oldEntity.getResponsibleEmpIds() != null ? oldEntity.getResponsibleEmpIds() : List.of());
  Set<String> newSet = new HashSet<>(req.getResponsibleEmpIds() != null ? req.getResponsibleEmpIds() : List.of());
  Set<String> removed = new HashSet<>(oldSet); removed.removeAll(newSet);
  Set<String> added = new HashSet<>(newSet); added.removeAll(oldSet);
  if (!removed.isEmpty() || !added.isEmpty()) {
      eventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(id, new ArrayList<>(removed), new ArrayList<>(added), "PRODUCT_SIDE", operatorEmpId));
  }
  ```
- [ ] **Step 4 (RED+GREEN):** Add Controller test + endpoint
- [ ] **Step 5:** Run all tests, expect PASS
- [ ] **Step 6:** Commit:
  ```
  feat(portal): D.5 编辑产品含 FOR UPDATE 锁 + 负责人 diff 同步

  - selectByIdForUpdate 悲观锁
  - responsibleEmpIds diff 计算 (removed/added)
  - productCode 和 productDeptOrgCode 编译期不可改 (不在 ReqDTO 中)
  - 6 个 Service 单测覆盖所有路径
  - 附件旧关联清理列为技术债 B8 (V1 不实现)
  ```

---

## Phase 8: D.6 DELETE /api/products/{id} — Delete with Pre-check

### Task 8.1: ProductService.deleteProduct + Tests + Commit

**Files:**
- Modify: `ProductService.java` — add `deleteProduct(id, operatorEmpId)`
- Modify: `ProductController.java` — add DELETE endpoint
- Modify: tests

**Context:** D.6 has the unique pre-check via `addrbookMapper.countEmployeesReferringProduct(productId)`. Per spec §4.6: if refCount > 0, throw `PORTAL-40905`.

- [ ] **Step 1 (RED):** Add test `deleteProductShouldThrow40905WhenStillReferenced()`:

```java
@Test
void deleteProductShouldThrow40905WhenStillReferenced() {
    ProductInfo entity = newEntity("P001", "DEPOSIT_001", "ORG_SZ_001");
    when(productInfoMapper.selectById("P001")).thenReturn(entity);
    when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
    when(addrbookEmployeeMapper.countEmployeesReferringProduct("P001")).thenReturn(2);

    assertThatThrownBy(() -> productService.deleteProduct("P001", "E10001"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-40905")
        .hasMessageContaining("仍有 2");
}

@Test
void deleteProductShouldSoftDeleteAndPublishCleanupEvent() {
    ProductInfo entity = newEntity("P001", "DEPOSIT_001", "ORG_SZ_001");
    entity.setResponsibleEmpIds(List.of("E10001"));
    when(productInfoMapper.selectById("P001")).thenReturn(entity);
    when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
    when(addrbookEmployeeMapper.countEmployeesReferringProduct("P001")).thenReturn(0);
    when(productInfoMapper.softDeleteById("P001", "E10001")).thenReturn(1);

    productService.deleteProduct("P001", "E10001");

    verify(productInfoMapper).softDeleteById("P001", "E10001");
    verify(auditApi).log(argThat(cmd -> /* assert level=HIGH */));
    // Note: refCount==0 means responsibleEmpIds was already empty by D.5 cleanup,
    // BUT spec §4.6 step 7 says publish event with removed=oldResponsibleEmpIds for safety
    verify(eventPublisher).publishEvent(any(ProductResponsibleUpdatedEvent.class));
}
```

- [ ] **Step 2:** Run, expect FAIL → GREEN → PASS
- [ ] **Step 3 (RED):** Add Controller test:

```java
@Test
@WithMockEmpContext
void deleteProductShouldReturn200() throws Exception {
    doNothing().when(productService).deleteProduct("P001", "E10001");
    mockMvc.perform(delete("/api/products/P001"))
        .andExpect(status().isOk());
}
```

- [ ] **Step 4 (GREEN):** Add `@DeleteMapping("/{id:[A-Za-z0-9_-]{1,64}}")` to controller
- [ ] **Step 5:** Commit: `feat(portal): D.6 删除产品含前置引用检查 + 高危审计 + 反向清理事件`

---

## Phase 9: D.7 GET /api/products/export — Sync Export Only

### Task 9.1: ProductExportService + Tests + Commit

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/service/ProductExportService.java`
- Create: `portal-content-center/src/main/java/.../portal/service/dto/ProductExportRow.java` — EasyExcel POJO with @ExcelProperty annotations matching 03 §H.7.1 (10 columns)
- Modify: `ProductController.java` — add export endpoint with HttpServletResponse
- Create: `ProductExportServiceTest.java`

**Context:** V1 only implements sync branch (≤5000 rows). Async branch returns `PORTAL-42207`. Reference 03 §H.7.1 for the EXACT 10 column definitions. Use EasyExcel.

- [ ] **Step 1:** Create `ProductExportRow.java` with 10 fields per 03 §H.7.1:

```java
package com.bank.branch.platform.portal.service.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class ProductExportRow {
    @ExcelProperty(value = "产品编码", index = 0) @ColumnWidth(20) private String productCode;
    @ExcelProperty(value = "产品名称", index = 1) @ColumnWidth(30) private String productName;
    @ExcelProperty(value = "产品类别", index = 2) @ColumnWidth(20) private String productCategoryDesc;
    @ExcelProperty(value = "产品说明", index = 3) @ColumnWidth(50) private String description;
    @ExcelProperty(value = "是否支持中场支持", index = 4) @ColumnWidth(15) private String supportForSupportRequest;  // "是" / "否"
    @ExcelProperty(value = "维护组织", index = 5) @ColumnWidth(25) private String productDeptOrgName;
    @ExcelProperty(value = "负责人姓名列表", index = 6) @ColumnWidth(30) private String responsibleEmpNames;
    @ExcelProperty(value = "负责人手机号列表", index = 7) @ColumnWidth(30) private String responsibleEmpMobiles;  // 脱敏
    @ExcelProperty(value = "状态", index = 8) @ColumnWidth(10) private String statusDesc;  // "启用" / "停用"
    @ExcelProperty(value = "更新时间", index = 9) @ColumnWidth(20) private String updatedTime;
}
```

- [ ] **Step 2 (RED):** Create `ProductExportServiceTest.java`:

```java
@Test
void exportShouldThrow42207WhenRowCountExceeds5000() {
    when(productInfoMapper.countProducts(any())).thenReturn(5001L);

    assertThatThrownBy(() -> productExportService.exportToStream(/* req */, mockOutputStream))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("PORTAL-42207");
}

@Test
void exportShouldWriteExcelStreamWhenWithinThreshold() throws Exception {
    when(productInfoMapper.countProducts(any())).thenReturn(2L);
    when(productInfoMapper.listProducts(any())).thenReturn(List.of(/* 2 entities */));
    // mock dictApi, orgApi, addrbookQueryService

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    productExportService.exportToStream(/* req */, baos);

    assertThat(baos.size()).isGreaterThan(0);  // Excel was written
    // Optionally parse the Excel back and verify column headers / row count
}
```

- [ ] **Step 3 (GREEN):** Implement `ProductExportService.exportToStream`:

```java
@Service
@RequiredArgsConstructor
public class ProductExportService {

    private final ProductInfoMapper productInfoMapper;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    private final AddrbookQueryService addrbookQueryService;
    private final AuditApi auditApi;

    private static final long SYNC_THRESHOLD = 5000L;

    public void exportToStream(ProductListReqDTO req, DataScopeContext scope, OutputStream output) {
        ProductListQuery q = /* build query */;
        long count = productInfoMapper.countProducts(q);
        if (count > SYNC_THRESHOLD) {
            throw new BizException("PORTAL-42207",
                "V1 暂不支持异步导出，当前命中 " + count + " 行超过同步阈值 5000，请增加过滤条件");
        }
        List<ProductInfo> entities = productInfoMapper.listProducts(q);
        List<ProductExportRow> rows = entities.stream()
            .map(this::toExportRow)
            .collect(Collectors.toList());

        EasyExcel.write(output, ProductExportRow.class)
            .sheet("产品资料")
            .doWrite(rows);

        // Audit
        auditApi.log(/* AuditCmd: action=EXPORT, bizType=PRODUCT, rowCount=count, level=HIGH */);
    }

    private ProductExportRow toExportRow(ProductInfo entity) {
        ProductExportRow row = new ProductExportRow();
        row.setProductCode(entity.getProductCode());
        row.setProductName(entity.getProductName());
        row.setProductCategoryDesc(dictApi.getDictLabel("PRODUCT_CATEGORY", entity.getProductCategory()));
        row.setDescription(entity.getDescription() != null ? entity.getDescription().replaceAll("[\\r\\n]+", " ") : "");
        row.setSupportForSupportRequest(Boolean.TRUE.equals(entity.getSupportForSupportRequest()) ? "是" : "否");
        OrgDTO org = orgApi.getOrg(entity.getProductDeptOrgCode());
        row.setProductDeptOrgName(org != null ? org.getOrgName() : "");
        if (entity.getResponsibleEmpIds() != null && !entity.getResponsibleEmpIds().isEmpty()) {
            List<ResponsibleEmpDTO> emps = addrbookQueryService.listResponsibleEmps(entity.getResponsibleEmpIds());
            row.setResponsibleEmpNames(emps.stream().map(ResponsibleEmpDTO::getEmpName).collect(Collectors.joining("、")));
            row.setResponsibleEmpMobiles(emps.stream().map(ResponsibleEmpDTO::getMobile).collect(Collectors.joining("、")));
        }
        row.setStatusDesc("ACTIVE".equals(entity.getStatus()) ? "启用" : "停用");
        row.setUpdatedTime(entity.getUpdatedTime() != null ? entity.getUpdatedTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        return row;
    }
}
```

- [ ] **Step 4:** Run, expect PASS
- [ ] **Step 5:** Add `ProductController.export` endpoint:

```java
@GetMapping("/export")
@BizAuth(bizType = BizType.PRODUCT, action = BizAction.EXPORT)
public void export(@Valid ProductListReqDTO req, HttpServletResponse response) throws IOException {
    String empId = currentUserApi.getCurrentEmpId();
    DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.PRODUCT, BizAction.EXPORT);
    String filename = "portal_product_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=" + filename);
    productExportService.exportToStream(req, scope, response.getOutputStream());
}
```

- [ ] **Step 6:** Add Controller integration test for the 200 + 4xx (when count > 5000) scenarios
- [ ] **Step 7:** Commit:
  ```
  feat(portal): D.7 产品导出同步分支 (V1 ≤5000 行)

  - EasyExcel 写出 10 列 (严格按 03 §H.7.1)
  - 负责人手机号脱敏
  - count > 5000 返回 PORTAL-42207 (V1 不实现异步分支)
  - 审计记录 EXPORT 高危
  ```

---

## Phase 10: A.2 + A.3 Shortcuts

### Task 10.1: ShortcutService + A.2/A.3 Controller + Tests + Commit

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/service/ShortcutService.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ShortcutSaveReqDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/ShortcutItemDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/controller/ShortcutController.java`
- Create: tests

**Context:** A.2/A.3 are simple CRUD without DATA_SCOPE. Both require NO `@BizAuth` (per 03 §A.2/A.3). The save operation is "delete-then-batch-insert" all CUSTOM shortcuts for the current user.

- [ ] **Step 1:** Create DTOs
- [ ] **Step 2 (RED):** Create `ShortcutServiceTest.java`:

```java
@Test
void listMyShortcutsShouldReturnSystemAndOwnCustomMerged() {
    when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
    PortalShortcut sys = newShortcut("S1", "SYSTEM", null);
    PortalShortcut own = newShortcut("C1", "CUSTOM", "E10001");
    when(shortcutMapper.listByEmpIdOrSystem("E10001")).thenReturn(List.of(sys, own));

    List<ShortcutDTO> result = shortcutService.listMyShortcuts();
    assertThat(result).hasSize(2);
}

@Test
void saveMyCustomShortcutsShouldDeleteOldAndBatchInsert() {
    when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
    ShortcutSaveReqDTO req = new ShortcutSaveReqDTO();
    req.setShortcuts(List.of(/* 3 items */));

    shortcutService.saveMyCustomShortcuts(req);

    verify(shortcutMapper).deleteCustomByEmpId("E10001");
    verify(shortcutMapper).batchInsert(argThat(list -> list.size() == 3));
}
```

- [ ] **Step 3 (GREEN):** Implement `ShortcutService` with `@Transactional` on save method
- [ ] **Step 4 (RED+GREEN):** Add Controller integration tests:

```java
@Test
@WithMockEmpContext(empId = "E10001")
void listShortcutsShouldReturn200() throws Exception {
    when(shortcutService.listMyShortcuts()).thenReturn(List.of());
    mockMvc.perform(get("/api/portal/shortcuts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
}

@Test
@WithMockEmpContext
void saveShortcutsShouldReturn200() throws Exception {
    String body = """
        {"shortcuts":[{"shortcutName":"我的","shortcutUrl":"/x","targetType":"INTERNAL","sortOrder":1}]}
        """;
    mockMvc.perform(put("/api/portal/shortcuts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
}
```

- [ ] **Step 5:** Create `ShortcutController.java` (NO `@BizAuth`!):

```java
@RestController
@RequestMapping("/api/portal/shortcuts")
@RequiredArgsConstructor
public class ShortcutController {

    private final ShortcutService shortcutService;

    @GetMapping
    public ResponseWrapper<List<ShortcutDTO>> list() {
        return ResponseWrapper.success(shortcutService.listMyShortcuts());
    }

    @PutMapping
    public ResponseWrapper<Void> save(@Valid @RequestBody ShortcutSaveReqDTO req) {
        shortcutService.saveMyCustomShortcuts(req);
        return ResponseWrapper.success();
    }
}
```

- [ ] **Step 6:** Run, expect PASS
- [ ] **Step 7:** Commit: `feat(portal): A.2 + A.3 快捷入口列表查询 + 个性化保存`

---

## Phase 11: A.1 Workspace Aggregation (5-Way Parallel)

### Task 11.1: WorkspaceService + Service Test + Commit

**Files:**
- Create: `portal-content-center/src/main/java/.../portal/service/WorkspaceService.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/WorkspaceRespDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/TodoItemDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/api/dto/NotificationItemDTO.java`
- Create: `portal-content-center/src/main/java/.../portal/controller/WorkspaceController.java`
- Create: tests

**Context:** This is the most complex phase. WorkspaceRespDTO has 7 fields per 03 §A.1. The service uses `CompletableFuture.allOf` with 5 parallel calls (`shortcuts / pendingTasks / unreadNotifications / metricCards / aggregateErrors`). Per spec §4.10.1, `todoCount / recentTodos` are V1 fallbacks (WorkflowQueryApi placeholder).

- [ ] **Step 1:** Create DTOs:
  - `WorkspaceRespDTO` (7 fields per 03 §A.1)
  - `TodoItemDTO` (9 fields per 03 §A.1)
  - `NotificationItemDTO` (8 fields per 03 §A.1)
  - Reuse `PortalMetricCard` and `ShortcutDTO` from earlier tasks
- [ ] **Step 2:** Create `WorkspaceService.java`:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final ShortcutService shortcutService;
    private final NotifyApi notifyApi;
    private final WorkflowQueryAdapter workflowQueryAdapter;
    private final MetricAdapter metricAdapter;
    private final CurrentUserApi currentUserApi;
    private final Executor portalAggregateExecutor;

    public WorkspaceRespDTO getWorkspace() {
        String empId = currentUserApi.getCurrentEmpId();
        Map<String, String> errors = new ConcurrentHashMap<>();

        CompletableFuture<List<ShortcutDTO>> fShortcuts = supplyAsync(
            () -> shortcutService.listMyShortcuts(), "shortcuts", errors, 100);

        CompletableFuture<Integer> fTodoCount = supplyAsync(
            () -> workflowQueryAdapter.countPendingTasks(empId), "todos", errors, 500);

        CompletableFuture<List<TodoItemDTO>> fRecentTodos = supplyAsync(
            () -> {
                List<PortalTodoItem> items = workflowQueryAdapter.listRecentPendingTasks(empId, 5);
                return items.stream().map(this::toTodoItemDTO).collect(Collectors.toList());
            }, "recent_todos", errors, 500);

        CompletableFuture<Integer> fUnreadCount = supplyAsync(
            () -> notifyApi.countUnread(empId), "notifications_count", errors, 500);

        CompletableFuture<List<NotificationItemDTO>> fRecentNotif = supplyAsync(
            () -> {
                PageResult<NotificationDTO> page = notifyApi.queryNotifications(
                    empId, false, new PageRequest(1, 5));
                return page.getRecords().stream().map(this::toNotifItemDTO).collect(Collectors.toList());
            }, "recent_notifications", errors, 500);

        CompletableFuture<List<PortalMetricCard>> fMetrics = supplyAsync(
            () -> metricAdapter.fetch(empId), "metrics", errors, 800);

        CompletableFuture.allOf(fShortcuts, fTodoCount, fRecentTodos, fUnreadCount, fRecentNotif, fMetrics)
            .orTimeout(2, TimeUnit.SECONDS)
            .exceptionally(ex -> { log.warn("workspace aggregation timeout", ex); return null; })
            .join();

        WorkspaceRespDTO resp = new WorkspaceRespDTO();
        resp.setShortcuts(fShortcuts.getNow(Collections.emptyList()));
        resp.setTodoCount(fTodoCount.getNow(0));
        resp.setRecentTodos(fRecentTodos.getNow(Collections.emptyList()));
        resp.setUnreadNotificationCount(fUnreadCount.getNow(0));
        resp.setRecentNotifications(fRecentNotif.getNow(Collections.emptyList()));
        resp.setMetricCards(fMetrics.getNow(Collections.emptyList()));
        resp.setAggregateErrors(errors);
        return resp;
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String name, Map<String, String> errors, int timeoutMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception ex) {
                errors.put(name, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                throw new CompletionException(ex);
            }
        }, portalAggregateExecutor)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> {
            errors.putIfAbsent(name, "timeout");
            return null;  // signal default value via getNow()
        });
    }

    // toTodoItemDTO and toNotifItemDTO conversion methods
}
```

- [ ] **Step 3 (RED):** Create `WorkspaceServiceTest.java` with these tests:
  1. `getWorkspaceShouldAggregateAllSuccessSourcesWithEmptyErrors()` — all mocks return data, verify all 7 fields populated and `aggregateErrors` is empty
  2. `getWorkspaceShouldReturnFallbackWhenMetricsFail()` — `metricAdapter.fetch` throws, verify `metricCards=[]` and `aggregateErrors.containsKey("metrics")`
  3. `getWorkspaceShouldReturnFallbackWhenWorkflowAdapterReturnsZero()` — `workflowQueryAdapter` is null bean (V1 default), verify `todoCount=0` and `recentTodos=[]`
  4. `getWorkspaceShouldRespectPerSourceTimeout()` — make `notifyApi.countUnread` sleep 2s, verify it times out after 500ms
- [ ] **Step 4:** Run, expect FAIL → GREEN → PASS
- [ ] **Step 5 (RED):** Create `WorkspaceController.java` and integration test:

```java
@RestController
@RequestMapping("/api/portal/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseWrapper<WorkspaceRespDTO> getWorkspace() {
        return ResponseWrapper.success(workspaceService.getWorkspace());
    }
}
```

- [ ] **Step 6:** Add `WorkspaceControllerTest`:

```java
@Test
@WithMockEmpContext
void getWorkspaceShouldReturn200() throws Exception {
    WorkspaceRespDTO resp = new WorkspaceRespDTO();
    resp.setTodoCount(0);
    resp.setShortcuts(List.of());
    when(workspaceService.getWorkspace()).thenReturn(resp);

    mockMvc.perform(get("/api/portal/workspace"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.todoCount").value(0))
        .andExpect(jsonPath("$.data.shortcuts").isArray())
        .andExpect(jsonPath("$.data.aggregateErrors").exists());
}
```

- [ ] **Step 7:** Commit:
  ```
  feat(portal): A.1 工作台聚合 (5 路 CompletableFuture 并行 + 降级)

  - WorkspaceRespDTO 7 字段严格对齐 03 §A.1
  - 5 路并行查询 (shortcuts/todos/notifications/metrics + counts)
  - 每路独立超时 + aggregateErrors 收集失败信息
  - todos 路通过 WorkflowQueryAdapter (V1 占位 → 返回 0)
  - metrics 路通过 MetricAdapter (V1 占位 → 返回空)
  - 整体 200 OK + aggregateErrors 标识降级源
  ```

---

## Phase 12: Cleanup & Smoke Test

### Task 12.1: bootstrap 启动验证 + 全量测试

- [ ] **Step 1:** Run all portal tests:
  ```
  mvn test -pl portal-content-center
  Expected: All 100+ tests pass
  ```
- [ ] **Step 2:** Run all module tests (regression):
  ```
  mvn test
  Expected: All modules pass, no broken tests
  ```
- [ ] **Step 3:** Start the bootstrap module (manual smoke test):
  ```
  cd bootstrap && mvn spring-boot:run
  Expected: Application starts on :8080, no ERROR logs
  ```
- [ ] **Step 4:** Manual curl smoke test (use `R_ADMIN` credentials from existing seed):
  ```bash
  # Login
  curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
       -H "Content-Type: application/json" \
       -d '{"userId":"E0000001","password":"123456"}'

  # D.3
  curl -b cookies.txt http://localhost:8080/api/products/support-available

  # A.2
  curl -b cookies.txt http://localhost:8080/api/portal/shortcuts

  # A.1
  curl -b cookies.txt http://localhost:8080/api/portal/workspace
  ```
- [ ] **Step 5:** Apply PT_RESOURCE align SQL to dev DB:
  ```
  mysql -uroot -p123456 onepl < docs/superpowers/sql/2026-04-11-portal-resources-align.sql
  ```
- [ ] **Step 6:** Verify in DB:
  ```sql
  SELECT RESOURCE_ID, IS_DELETED FROM PT_RESOURCE
  WHERE RESOURCE_ID IN ('RES_PORTAL_WORKSPACE','RES_PRODUCT_SUPPORT_AVAILABLE','RES_SHORTCUT_REPLACE_PUT');
  -- Expected: 3 rows, IS_DELETED=0
  ```
- [ ] **Step 7:** Commit:
  ```
  chore(portal): V1 首版 10 个接口全部就绪 + smoke test 通过

  - All tests pass: mvn test
  - bootstrap starts cleanly
  - 10 个接口手动 curl 验证 200 OK
  - PT_RESOURCE 对齐 SQL 已应用到 dev DB
  ```

---

## Final Verification Checklist

Before declaring V1 slice complete:

- [ ] `mvn compile -pl portal-content-center` succeeds
- [ ] `mvn test -pl portal-content-center` — all tests pass
- [ ] `mvn test` (full module reactor) — no regression
- [ ] Service unit test coverage ≥ 70% (run `mvn jacoco:report -pl portal-content-center`)
- [ ] `mvn -pl bootstrap spring-boot:run` starts successfully, no ERROR logs
- [ ] All 10 endpoints respond 200 OK to curl smoke tests
- [ ] 14+ commits in `git log` (one per phase/interface)
- [ ] PT_RESOURCE align SQL committed and applied
- [ ] Run `superpowers:requesting-code-review` skill to invoke code-reviewer subagent for the entire V1 slice



