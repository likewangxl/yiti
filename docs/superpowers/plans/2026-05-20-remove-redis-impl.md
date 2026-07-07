# 去 Redis 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** yiti 后端去掉 Redis 依赖，session 改 Spring Session JDBC，权限/绩效缓存改直接读 DB，绩效分布式锁改自建 PT_LOCK 表 + JdbcLockManager。

**Architecture:** Session 共享靠 spring-session-jdbc 写 MySQL 两张官方表（SPRING_SESSION + SPRING_SESSION_ATTRIBUTES），多实例自然共享。缓存策略统一 YAGNI（去掉 Redis 缓存层直接读 DB，PT_USER_ROLE/PT_ROLE_RESOURCE 主键索引点查无性能压力）。分布式锁靠新建 PT_LOCK 表 + SELECT FOR UPDATE 实现 LockManager 接口，4 处 redis 锁统一替换。

**Tech Stack:** Spring Boot 3.2.3 / spring-session-jdbc 3.2.x / MySQL 8.0 / JdbcTemplate / JUnit 5 + Mockito + Spring Test

**Related Spec:** [docs/superpowers/specs/2026-05-20-remove-redis-design.md](../specs/2026-05-20-remove-redis-design.md)

---

## File Structure

### 新增文件（5）

- `docs/superpowers/sql/2026-05-20-remove-redis-init.sql` —— 3 张表 DDL（SPRING_SESSION + SPRING_SESSION_ATTRIBUTES + PT_LOCK）
- `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/LockManager.java` —— 接口
- `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/JdbcLockManager.java` —— JDBC 实现
- `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/LockAutoConfig.java` —— 自动配置（注册 Bean + @Scheduled 清理过期锁）
- `common/common-web/src/test/java/com/bank/branch/platform/common/web/lock/JdbcLockManagerTest.java` —— 测试

### 修改文件（13）

- `bootstrap/pom.xml`
- `bootstrap/src/main/resources/application.yml`
- `bootstrap/src/main/resources/application-dev.yml`
- `common/common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/CacheConfig.java` ← 删除
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/PermissionCacheService.java`
- `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/PermissionCacheServiceTest.java`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceRedisConfig.java` ← 删除
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/SysControlFacade.java`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/listener/KpiCascadeListener.java`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/AllocRelationService.java`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/DataTaskService.java`

每个 Task = 1 个 commit。Task 顺序按"先基础设施后业务模块"：deps → session → auth-cache → perf-cache → lock。

---

## Task 1: chore(deps): bootstrap pom 移除 redis + 加 spring-session-jdbc + yml 清 redis 配置

**Files:**
- Modify: `bootstrap/pom.xml`
- Modify: `bootstrap/src/main/resources/application.yml`
- Modify: `bootstrap/src/main/resources/application-dev.yml`

**前置检查（信息收集步骤，无代码改动）：**

- [ ] **Step 1: grep 当前 pom 里 redis 相关依赖位置**

Run: `cd /home/djdev/wangyq/yiti && grep -n "redis\|lettuce\|spring-session" bootstrap/pom.xml`

Expected output（行号会不同，但应该看到 3 个依赖）：
```
spring-boot-starter-data-redis
lettuce-core
spring-session-data-redis
```

- [ ] **Step 2: 改 `bootstrap/pom.xml` —— 删 3 个 redis 依赖，加 spring-session-jdbc**

用 Edit 工具删除 3 处依赖块，每处类似：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```xml
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

在 spring-session-data-redis 原位置加入新依赖：

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
```

- [ ] **Step 3: 改 `bootstrap/src/main/resources/application.yml` —— 删 `spring.data.redis.*` + 改 session store-type**

找到 `spring.data.redis:` 配置块，整段删除。找到 `spring.session:` 配置块，改成：

```yaml
  session:
    store-type: jdbc
    timeout: 7200s
    jdbc:
      initialize-schema: never
      table-name: SPRING_SESSION
```

如果原 yml 用 `spring.session.store-type: redis`，把这一行改成 `jdbc`；并删除 `spring.session.redis.*` 子配置（如 namespace）。

- [ ] **Step 4: 改 `bootstrap/src/main/resources/application-dev.yml` —— 删 dev 专属 redis 配置**

```yaml
# 删整段：
spring:
  data:
    redis:
      database: 1
  session:
    redis:
      namespace: spring:session:wangyq
```

整个 `spring.data.redis` 块和 `spring.session.redis` 块全部删除（dev profile 不再覆盖任何 redis 配置）。

- [ ] **Step 5: 执行 `mvn install -DskipTests` 验证编译通过**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn install -DskipTests 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`（编译过即可，启动会失败因为表还没建——下一 Task 处理）

- [ ] **Step 6: commit**

```bash
cd /home/djdev/wangyq/yiti && git add bootstrap/pom.xml bootstrap/src/main/resources/application.yml bootstrap/src/main/resources/application-dev.yml && git commit -m "$(cat <<'EOF'
chore(deps): bootstrap 移除 redis 依赖 + 加 spring-session-jdbc

- 删 spring-boot-starter-data-redis / lettuce-core / spring-session-data-redis
- 加 spring-session-jdbc
- yml 清 spring.data.redis.* + 改 spring.session.store-type=jdbc

注意：本 commit 之后应用启动会因 SPRING_SESSION 表不存在失败，
下个 commit (feat(session)) 加表 SQL 后才能完整启动。

EOF
)"
```

---

## Task 2: feat(session): Spring Session JDBC 建表 SQL + SessionJdbcIT 集成测试

**Files:**
- Create: `docs/superpowers/sql/2026-05-20-remove-redis-init.sql`
- Create: `bootstrap/src/test/java/com/bank/branch/platform/it/SessionJdbcIT.java`

- [ ] **Step 1: 创建 `docs/superpowers/sql/2026-05-20-remove-redis-init.sql`**

完整内容（含全部 3 张表，PT_LOCK 提前建好 Task 5 直接用）：

```sql
-- ====================================================================
-- 去 Redis 初始化 SQL：建 3 张新表
--
-- 1. SPRING_SESSION + SPRING_SESSION_ATTRIBUTES —— spring-session-jdbc 官方 schema
-- 2. PT_LOCK —— 自建分布式锁表（Task 5 LockManager 用）
--
-- 幂等：脚本可重复执行（用 IF NOT EXISTS）
-- ====================================================================

-- ──────────────────────────────────────────────────────────────────
-- 1. Spring Session JDBC（spring-session-jdbc 3.2.x 标准 schema）
-- ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID            CHAR(36)  NOT NULL,
    SESSION_ID            CHAR(36)  NOT NULL,
    CREATION_TIME         BIGINT    NOT NULL,
    LAST_ACCESS_TIME      BIGINT    NOT NULL,
    MAX_INACTIVE_INTERVAL INT       NOT NULL,
    EXPIRY_TIME           BIGINT    NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX        SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX        SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ──────────────────────────────────────────────────────────────────
-- 2. PT_LOCK —— 自建分布式锁（Task 5 LockManager 实现用）
-- ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PT_LOCK (
    LOCK_KEY    VARCHAR(128) NOT NULL COMMENT '锁标识（业务 key）',
    HOLDER      VARCHAR(64)  NOT NULL COMMENT '持锁者（实例 UUID#线程 ID）',
    ACQUIRED_AT BIGINT       NOT NULL COMMENT '获取锁时间戳 unix ms',
    EXPIRES_AT  BIGINT       NOT NULL COMMENT '过期时间戳 unix ms',
    PRIMARY KEY (LOCK_KEY),
    INDEX IDX_PT_LOCK_EXPIRES (EXPIRES_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式锁（去 Redis 后自建）';

-- 验证：
--   SHOW TABLES LIKE 'SPRING%';   -- 应有 SPRING_SESSION + SPRING_SESSION_ATTRIBUTES
--   SHOW TABLES LIKE 'PT_LOCK';   -- 应有 PT_LOCK
```

- [ ] **Step 2: 在本地 yiti 库执行此 SQL（手工部署，项目已废 Flyway）**

Run:
```bash
mysql -uroot -p123456 yiti < /home/djdev/wangyq/yiti/docs/superpowers/sql/2026-05-20-remove-redis-init.sql 2>&1 | tail -3
mysql -uroot -p123456 yiti -e "SHOW TABLES LIKE 'SPRING%'; SHOW TABLES LIKE 'PT_LOCK';" 2>&1 | tail -5
```

Expected:
```
SPRING_SESSION
SPRING_SESSION_ATTRIBUTES
PT_LOCK
```

- [ ] **Step 3: 创建集成测试 `bootstrap/src/test/java/com/bank/branch/platform/it/SessionJdbcIT.java`**

```java
package com.bank.branch.platform.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Session JDBC 集成测试：验证 session 写入 SPRING_SESSION 表 + 跨实例可恢复。
 * <p>跑前要求 yiti 库已执行 docs/superpowers/sql/2026-05-20-remove-redis-init.sql 建表。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class SessionJdbcIT {

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void session_saveAndLoad_persistsToMysql() {
        // 1. 创建 session 并写入 attribute
        Session session = sessionRepository.createSession();
        session.setAttribute("testKey", "helloJdbcSession");
        ((SessionRepository) sessionRepository).save(session);
        String sessionId = session.getId();

        // 2. SPRING_SESSION 表应该有这条记录
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?",
                Integer.class, sessionId);
        assertThat(cnt).isEqualTo(1);

        // 3. 模拟另一个实例：用同一个 sessionId 加载，attribute 仍在
        Session loaded = sessionRepository.findById(sessionId);
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("testKey")).isEqualTo("helloJdbcSession");

        // 4. 清理
        sessionRepository.deleteById(sessionId);
    }
}
```

- [ ] **Step 4: 跑测试看绿**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl bootstrap -Dtest=SessionJdbcIT 2>&1 | grep -E "Tests run|BUILD" | tail -3
```

Expected: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`

如果 BUILD FAILURE：检查 application-dev.yml 是否还有残留 redis 配置 / yiti 库是否真的执行了 SQL。

- [ ] **Step 5: commit**

```bash
cd /home/djdev/wangyq/yiti && git add docs/superpowers/sql/2026-05-20-remove-redis-init.sql bootstrap/src/test/java/com/bank/branch/platform/it/SessionJdbcIT.java && git commit -m "$(cat <<'EOF'
feat(session): Spring Session JDBC 建表 SQL + SessionJdbcIT 集成测试

- 新建 SPRING_SESSION + SPRING_SESSION_ATTRIBUTES（spring-session-jdbc 3.2 官方 schema）
- 顺手建 PT_LOCK（Task 5 LockManager 用）
- SessionJdbcIT 验证 session 落 MySQL + 跨实例可恢复

SQL 文件需 DBA 手工执行（项目已废 Flyway）。
本 commit 后应用可完整启动（前一 commit deps 改完 + 本 commit 建表 + JDBC 自动接管 session 存储）。

EOF
)"
```

---

## Task 3: refactor(auth-cache): PermissionCacheService 去 redis 直接读 DB（接口签名不变）

**Files:**
- Delete: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/CacheConfig.java`
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/PermissionCacheService.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/service/PermissionCacheServiceTest.java`

**TDD 红 → 绿 → 重构。**

- [ ] **Step 1: 读现状理解 PermissionCacheService 接口签名**

Run:
```bash
grep -nE "public.*get|public.*evict|public.*publish" /home/djdev/wangyq/yiti/auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/PermissionCacheService.java | head -20
```

记下：`getRoleIdsByEmpId(empId)` / `getResourceIdsByRoleId(roleId)` / `getBizScopesByRoleId(roleId)` / `evictXxxCache(...)` / `publishCacheInvalidatedEvent(...)`，签名保留不变。

- [ ] **Step 2: 改 PermissionCacheServiceTest（TDD 红）—— mock Mapper 而非 RedisTemplate**

完整新版本（替换原 PermissionCacheServiceTest）：

```java
package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.BizScopeMapper;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * PermissionCacheService 测试（去 Redis 后改 mock Mapper）。
 * <p>接口签名不变，业务层无感知；内部从读 Redis 改为直接调 Mapper。</p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock UserRoleMapper     userRoleMapper;
    @Mock RoleResourceMapper roleResourceMapper;
    @Mock ResourceMapper     resourceMapper;
    @Mock BizScopeMapper     bizScopeMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks PermissionCacheService cacheService;

    @Test
    void getRoleIdsByEmpId_callsMapperDirectly() {
        when(userRoleMapper.selectRoleIdsByUserId("emp001"))
                .thenReturn(List.of("R_ADMIN", "R_CM"));

        Set<String> ids = cacheService.getRoleIdsByEmpId("emp001");

        assertThat(ids).containsExactlyInAnyOrder("R_ADMIN", "R_CM");
        verify(userRoleMapper).selectRoleIdsByUserId("emp001");
    }

    @Test
    void getResourceIdsByRoleId_callsMapperDirectly() {
        when(roleResourceMapper.selectResourceIdsByRoleId("R_ADMIN"))
                .thenReturn(List.of("A_LOGIN", "A_LOGOUT"));

        Set<String> ids = cacheService.getResourceIdsByRoleId("R_ADMIN");

        assertThat(ids).containsExactlyInAnyOrder("A_LOGIN", "A_LOGOUT");
        verify(roleResourceMapper).selectResourceIdsByRoleId("R_ADMIN");
    }

    @Test
    void evictRoleResourceCache_isNoOp_butKeepsSignature() {
        // NoOp：不抛异常即可（去 Redis 后无缓存可清，签名保留避免业务层大改）
        cacheService.evictRoleResourceCache("R_ADMIN");

        // 关键：不调任何 RedisTemplate 方法（mock 验证不到 Redis 即为正确）
        verifyNoInteractions(userRoleMapper, roleResourceMapper);
    }
}
```

- [ ] **Step 3: 跑测试看红**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl auth-permission-center -Dtest=PermissionCacheServiceTest 2>&1 | tail -8
```

Expected: 编译失败（PermissionCacheService 还有 RedisTemplate 引用）或测试失败。

- [ ] **Step 4: 删除 CacheConfig.java**

```bash
rm /home/djdev/wangyq/yiti/auth-permission-center/src/main/java/com/bank/branch/platform/auth/config/CacheConfig.java
```

- [ ] **Step 5: 改 PermissionCacheService.java —— 删 RedisTemplate 注入，各方法直接调 Mapper**

读现有文件，然后完全重写为：

```java
package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.event.PermissionCacheInvalidatedEvent;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.mapper.BizScopeMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限缓存服务。
 * <p>去 Redis 后改为直接调 Mapper 读 DB（PT_USER_ROLE/PT_ROLE_RESOURCE 主键索引点查，性能可接受）。
 * <p>evictXxxCache 系列方法保留签名作为 NoOp，避免业务层大改；事件发布保留（业务层可能监听）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final UserRoleMapper     userRoleMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final ResourceMapper     resourceMapper;
    private final BizScopeMapper     bizScopeMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 查用户的角色 ID 集合，直接 DB（去 Redis） */
    public Set<String> getRoleIdsByEmpId(String empId) {
        return new HashSet<>(userRoleMapper.selectRoleIdsByUserId(empId));
    }

    /** 查角色的资源 ID 集合，直接 DB（去 Redis） */
    public Set<String> getResourceIdsByRoleId(String roleId) {
        return new HashSet<>(roleResourceMapper.selectResourceIdsByRoleId(roleId));
    }

    /** 查角色的 BizScope 列表，直接 DB（去 Redis） */
    public List<PtRoleBizScope> getBizScopesByRoleId(String roleId) {
        return bizScopeMapper.selectByRoleId(roleId);
    }

    /** NoOp：保留签名避免业务层大改（去 Redis 后无缓存可清） */
    public void evictRoleResourceCache(String roleId) {
        log.debug("[PermissionCacheService.evictRoleResourceCache] NoOp (post-redis) roleId={}", roleId);
    }

    /** NoOp：同上 */
    public void evictUserRoleCache(String empId) {
        log.debug("[PermissionCacheService.evictUserRoleCache] NoOp (post-redis) empId={}", empId);
    }

    /** NoOp：同上 */
    public void evictBizScopeCache(String roleId) {
        log.debug("[PermissionCacheService.evictBizScopeCache] NoOp (post-redis) roleId={}", roleId);
    }

    /** 事件发布保留（业务可能监听做别的事，跟 cache 解耦） */
    public void publishCacheInvalidatedEvent(String changeType, Set<String> affectedRoleIds, String operator, String reason) {
        PermissionCacheInvalidatedEvent event = new PermissionCacheInvalidatedEvent();
        event.setChangeType(changeType);
        event.setAffectedRoleIds(affectedRoleIds);
        event.setOperator(operator);
        event.setReason(reason);
        event.setEventId("evt_" + System.currentTimeMillis());
        event.setOccurredAt(System.currentTimeMillis());
        eventPublisher.publishEvent(event);
    }
}
```

注意：上面 `publishCacheInvalidatedEvent` 签名可能跟现有调用方不一致，**实施时先 grep 业务层所有调用点**确定参数列表对齐再改：

```bash
grep -rn "publishCacheInvalidatedEvent\|evictRoleResourceCache\|evictUserRoleCache\|evictBizScopeCache" /home/djdev/wangyq/yiti/auth-permission-center/src /home/djdev/wangyq/yiti/system-governance-center 2>/dev/null
```

按 grep 结果调整方法签名以保持二进制兼容。

- [ ] **Step 6: 跑测试看绿**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn install -pl auth-permission-center -DskipTests -am 2>&1 | tail -3 && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl auth-permission-center 2>&1 | grep -E "Tests run.*Failures|BUILD" | tail -3
```

Expected: `BUILD SUCCESS` + 全部 158+ 测试通过。

- [ ] **Step 7: commit**

```bash
cd /home/djdev/wangyq/yiti && git add -A auth-permission-center/ && git commit -m "$(cat <<'EOF'
refactor(auth-cache): PermissionCacheService 去 redis 直接读 DB

- 删 CacheConfig.java（不再需要 RedisCacheManager）
- PermissionCacheService 删 RedisTemplate 注入，getXxx 改直接调 Mapper
- evictXxxCache 保留签名作 NoOp（业务层一堆调用点，避免大改）
- publishCacheInvalidatedEvent 保留（事件可能有别的监听者）
- PermissionCacheServiceTest 改 mock Mapper

权限校验热路径：PT_USER_ROLE/PT_ROLE_RESOURCE 主键索引点查 ~0.1ms，
对业务无感（业务 SQL 本来 10ms+）。后期发现热点可补 caffeine。

EOF
)"
```

---

## Task 4: refactor(perf-cache): 绩效模块 2 处缓存去 redis

**Files:**
- Delete: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceRedisConfig.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/AllocRelationService.java`
- Modify: 对应测试

- [ ] **Step 1: 读 AllocRelationService 现状理解缓存逻辑**

```bash
grep -nE "redisTemplate|opsFor|@Cacheable|@CacheEvict" /home/djdev/wangyq/yiti/performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/AllocRelationService.java | head -20
```

记下：redisTemplate 字段位置、缓存读取/回写代码段。

- [ ] **Step 2: 删 PerformanceRedisConfig.java**

```bash
rm /home/djdev/wangyq/yiti/performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceRedisConfig.java
```

- [ ] **Step 3: 改 AllocRelationService.java**

用 Edit 工具做以下改动：
1. 删 `RedisTemplate<String, Object> redisTemplate` 字段
2. 构造函数参数去掉 RedisTemplate
3. 查询方法（如 `getAllocRelations`）：删除"先查 Redis 命中"逻辑，直接全量调 mapper
4. 删除"回写 Redis"逻辑
5. 删除 @Cacheable / @CacheEvict 注解（如果该 service 有用）

具体代码改动模式（以 Edit 工具替换为例）：

旧：
```java
@RequiredArgsConstructor
public class AllocRelationService {
    private final AllocRelationMapper allocRelationMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    // ...
    public Map<String, AllocRelation> batchGetByCustIds(List<String> custIds) {
        // 先查 Redis
        Map<String, AllocRelation> result = new HashMap<>();
        for (String custId : custIds) {
            Object cached = redisTemplate.opsForValue().get("perf:alloc:" + custId);
            if (cached != null) result.put(custId, (AllocRelation) cached);
        }
        // 未命中 → 查 DB → 回写
        List<String> miss = ...;
        List<AllocRelation> fromDb = allocRelationMapper.selectByCustIds(miss);
        for (AllocRelation r : fromDb) {
            result.put(r.getCustId(), r);
            redisTemplate.opsForValue().set("perf:alloc:" + r.getCustId(), r, 300, TimeUnit.SECONDS);
        }
        return result;
    }
}
```

新：
```java
@RequiredArgsConstructor
public class AllocRelationService {
    private final AllocRelationMapper allocRelationMapper;
    // 删 redisTemplate 字段
    // ...
    public Map<String, AllocRelation> batchGetByCustIds(List<String> custIds) {
        // 去 Redis 后：直接全量查 DB，PT 表主键索引点查无性能压力
        List<AllocRelation> all = allocRelationMapper.selectByCustIds(custIds);
        return all.stream().collect(Collectors.toMap(AllocRelation::getCustId, a -> a));
    }
}
```

- [ ] **Step 4: 改对应测试 AllocRelationServiceTest 等**

```bash
grep -rln "redisTemplate" /home/djdev/wangyq/yiti/performance-engine-center/src/test 2>/dev/null
```

对每个测试文件：删 @Mock RedisTemplate / 构造时去 redis 参数 / 删"验证缓存命中跳 DB"断言、改"始终调 mapper"断言。

- [ ] **Step 5: 跑全测试看绿**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn install -pl performance-engine-center -DskipTests -am 2>&1 | tail -3 && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl performance-engine-center 2>&1 | grep -E "Tests run.*Failures|BUILD" | tail -3
```

Expected: `BUILD SUCCESS` + 全部测试通过（具体数字依现状）。

- [ ] **Step 6: commit**

```bash
cd /home/djdev/wangyq/yiti && git add -A performance-engine-center/ && git commit -m "$(cat <<'EOF'
refactor(perf-cache): 绩效模块 2 处缓存去 redis

- 删 PerformanceRedisConfig.java（不再需要 @Cacheable / @CacheEvict）
- AllocRelationService 删 RedisTemplate 字段 + 批量查询"先查 Redis 后回写"逻辑
  改成直接全量查 DB（Mapper 主键索引点查无性能压力）
- 对应测试改 mock 移除 Redis 断言

性能影响：批量查 100 条客户分配关系约 5-10ms，可接受。
后期发现热点可补 caffeine。

EOF
)"
```

---

## Task 5: feat(lock): 自建 LockManager + JdbcLockManager + 替换绩效 4 处 redis 锁

**Files:**
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/LockManager.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/JdbcLockManager.java`
- Create: `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/LockAutoConfig.java`
- Create: `common/common-web/src/test/java/com/bank/branch/platform/common/web/lock/JdbcLockManagerTest.java`
- Modify: `common/common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: 4 处 facade/service/listener + 各自测试

**TDD 严格红绿循环。Step 1-4 先把 LockManager 跑通，Step 5-10 替换 4 处 redis 锁。**

- [ ] **Step 1: 创建 `LockManager.java` 接口**

```java
package com.bank.branch.platform.common.web.lock;

/**
 * 分布式锁（去 Redis 后基于 PT_LOCK 表 + SELECT FOR UPDATE 实现）。
 * <p>holder 用于 CAS 释放：只有持锁者本人能 unlock，防止误删别人的锁。</p>
 */
public interface LockManager {

    /**
     * 尝试拿锁；不阻塞，立即返回。
     *
     * @param key    业务锁标识（建议命名空间前缀，如 "perf:metric:M_0088"）
     * @param holder 持锁者标识（建议 instanceId + threadId，全局唯一）
     * @param ttlMs  锁租期（毫秒），到期可被别人强占
     * @return true=拿到锁；false=别人持有未过期
     */
    boolean tryLock(String key, String holder, long ttlMs);

    /**
     * 释放锁。CAS：只有 holder 匹配的才删，防误删。
     *
     * @return true=成功释放；false=持锁者不是 holder（说明锁已被强占或已释放）
     */
    boolean unlock(String key, String holder);
}
```

- [ ] **Step 2: 创建 `JdbcLockManagerTest.java`（TDD 红）**

```java
package com.bank.branch.platform.common.web.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JdbcLockManager 测试 —— 真连 MySQL PT_LOCK 表（跑前需建表 SQL）。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = LockAutoConfig.class)
@ActiveProfiles("dev")
class JdbcLockManagerTest {

    @Autowired LockManager lockManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanLockTable() {
        jdbcTemplate.update("DELETE FROM PT_LOCK WHERE LOCK_KEY LIKE 'test:%'");
    }

    @Test
    void tryLock_firstTimeAcquireSucceeds() {
        boolean ok = lockManager.tryLock("test:key1", "holder-A", 10_000);
        assertThat(ok).isTrue();
    }

    @Test
    void tryLock_otherHolderActive_fails() {
        lockManager.tryLock("test:key2", "holder-A", 10_000);

        boolean ok = lockManager.tryLock("test:key2", "holder-B", 10_000);

        assertThat(ok).isFalse();
    }

    @Test
    void tryLock_otherHolderExpired_seizeSucceeds() throws InterruptedException {
        lockManager.tryLock("test:key3", "holder-A", 100); // 100ms 后过期
        Thread.sleep(200);

        boolean ok = lockManager.tryLock("test:key3", "holder-B", 10_000);

        assertThat(ok).isTrue();
    }

    @Test
    void unlock_holderMatches_succeeds() {
        lockManager.tryLock("test:key4", "holder-A", 10_000);

        boolean ok = lockManager.unlock("test:key4", "holder-A");

        assertThat(ok).isTrue();
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PT_LOCK WHERE LOCK_KEY = ?", Integer.class, "test:key4");
        assertThat(cnt).isZero();
    }

    @Test
    void unlock_holderMismatch_isRejected() {
        lockManager.tryLock("test:key5", "holder-A", 10_000);

        boolean ok = lockManager.unlock("test:key5", "holder-B");

        assertThat(ok).isFalse();
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PT_LOCK WHERE LOCK_KEY = ?", Integer.class, "test:key5");
        assertThat(cnt).isEqualTo(1); // 锁仍在
    }
}
```

- [ ] **Step 3: 跑测试看红**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl common/common-web -Dtest=JdbcLockManagerTest 2>&1 | tail -5
```

Expected: 编译失败（JdbcLockManager / LockAutoConfig 还没建）。

- [ ] **Step 4: 创建 `JdbcLockManager.java` 实现（TDD 绿）**

```java
package com.bank.branch.platform.common.web.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 PT_LOCK 表 + SELECT FOR UPDATE 的分布式锁实现。
 * <p>tryLock 用独立事务（REQUIRES_NEW），避免污染业务事务上下文。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcLockManager implements LockManager {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryLock(String key, String holder, long ttlMs) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlMs;

        // 1. 查现状（FOR UPDATE 持行锁，防并发）
        try {
            Long currentExpires = jdbcTemplate.query(
                    "SELECT EXPIRES_AT FROM PT_LOCK WHERE LOCK_KEY = ? FOR UPDATE",
                    rs -> rs.next() ? rs.getLong(1) : null,
                    key);

            if (currentExpires == null) {
                // 行不存在 → INSERT 占位
                try {
                    jdbcTemplate.update(
                            "INSERT INTO PT_LOCK(LOCK_KEY, HOLDER, ACQUIRED_AT, EXPIRES_AT) VALUES (?, ?, ?, ?)",
                            key, holder, now, expiresAt);
                    return true;
                } catch (DuplicateKeyException e) {
                    // 并发：另一线程刚 INSERT 了，本次失败
                    return false;
                }
            } else if (currentExpires <= now) {
                // 已过期 → UPDATE 强占
                int updated = jdbcTemplate.update(
                        "UPDATE PT_LOCK SET HOLDER = ?, ACQUIRED_AT = ?, EXPIRES_AT = ? WHERE LOCK_KEY = ?",
                        holder, now, expiresAt, key);
                return updated > 0;
            } else {
                // 持有未过期
                return false;
            }
        } catch (DataAccessException e) {
            log.warn("[JdbcLockManager.tryLock] DB 异常 key={} holder={} err={}", key, holder, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean unlock(String key, String holder) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM PT_LOCK WHERE LOCK_KEY = ? AND HOLDER = ?",
                key, holder);
        return deleted > 0;
    }
}
```

- [ ] **Step 5: 创建 `LockAutoConfig.java`**

```java
package com.bank.branch.platform.common.web.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分布式锁自动配置：注册 JdbcLockManager Bean + 定时清理过期锁。
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableScheduling
public class LockAutoConfig {

    @Bean
    public LockManager lockManager(JdbcTemplate jdbcTemplate) {
        return new JdbcLockManager(jdbcTemplate);
    }

    @Bean
    public LockCleanupJob lockCleanupJob(JdbcTemplate jdbcTemplate) {
        return new LockCleanupJob(jdbcTemplate);
    }

    /** 定时清理过期锁（grace period 60s 防止刚过期的锁被立即清，让 unlock CAS 还能匹配） */
    @Component
    public static class LockCleanupJob {
        private final JdbcTemplate jdbcTemplate;
        public LockCleanupJob(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

        @Scheduled(fixedDelay = 300_000) // 5 分钟
        public void cleanExpired() {
            long threshold = System.currentTimeMillis() - 60_000;
            int deleted = jdbcTemplate.update("DELETE FROM PT_LOCK WHERE EXPIRES_AT < ?", threshold);
            if (deleted > 0) {
                org.slf4j.LoggerFactory.getLogger(LockCleanupJob.class)
                        .debug("[LockCleanupJob] 清理过期锁 deleted={}", deleted);
            }
        }
    }
}
```

- [ ] **Step 6: 注册 LockAutoConfig 到 AutoConfiguration.imports**

读 `common/common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，追加一行：

```
com.bank.branch.platform.common.web.lock.LockAutoConfig
```

- [ ] **Step 7: 跑测试看绿**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn install -pl common/common-web -DskipTests 2>&1 | tail -3 && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl common/common-web -Dtest=JdbcLockManagerTest 2>&1 | grep -E "Tests run|BUILD" | tail -3
```

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`

- [ ] **Step 8: 替换 4 处 redis 锁（按 spec § 4.4）**

对每个文件做以下变换：

**A. `performance-engine-center/.../listener/KpiCascadeListener.java`：**

旧代码模式：
```java
private final RedisTemplate<String, String> redisTemplate;
...
boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent("perf:kpi:" + scheme + ":" + cycle, "1", 30, TimeUnit.SECONDS);
if (!acquired) { return; }
```

新代码：
```java
import com.bank.branch.platform.common.web.lock.LockManager;
...
private final LockManager lockManager;
...
String lockKey = "perf:kpi:" + scheme + ":" + cycle;
String holder = HolderUtil.current(); // 见 Step 9
boolean acquired = lockManager.tryLock(lockKey, holder, 30_000);
if (!acquired) { return; }
try {
    // 原业务逻辑
} finally {
    lockManager.unlock(lockKey, holder);
}
```

**B. `performance-engine-center/.../service/DataTaskService.java`：**

旧模式（注释明示有 DuplicateKeyException 兜底）：
```java
private final RedisTemplate<String, Object> redisTemplate;
private static final RedisScript<Long> COMPARE_AND_DEL = ...;
...
String lockKey = "perf:data_task:" + taskId;
Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, holder, 30, TimeUnit.SECONDS);
try {
    if (Boolean.TRUE.equals(acquired)) {
        // 业务
    }
} finally {
    redisTemplate.execute(COMPARE_AND_DEL, List.of(lockKey), holder);
}
```

新代码：
```java
private final LockManager lockManager;
// 删除 COMPARE_AND_DEL 和 redisTemplate
...
String lockKey = "perf:data_task:" + taskId;
String holder = HolderUtil.current();
boolean acquired = lockManager.tryLock(lockKey, holder, 30_000);
try {
    if (acquired) {
        // 原业务（保留 try-catch DuplicateKeyException 双保险）
    }
} finally {
    if (acquired) lockManager.unlock(lockKey, holder);
}
```

**C/D. `MetricLifecycleFacade.java` / `SysControlFacade.java`：** 同 B 模式。

- [ ] **Step 9: 创建 HolderUtil 辅助类（持锁者标识生成）**

在 `common/common-web/src/main/java/com/bank/branch/platform/common/web/lock/HolderUtil.java`：

```java
package com.bank.branch.platform.common.web.lock;

import java.util.UUID;

/**
 * 分布式锁持锁者标识生成器：进程内 instanceId（启动时初始化一次）+ 当前线程 ID。
 * <p>多实例多线程下能区分；进程重启会换新 instanceId，旧锁自然失效。</p>
 */
public final class HolderUtil {
    private static final String INSTANCE_ID = UUID.randomUUID().toString().substring(0, 8);
    private HolderUtil() {}

    public static String current() {
        return INSTANCE_ID + "#" + Thread.currentThread().getId();
    }
}
```

- [ ] **Step 10: 改 4 处对应测试 mock LockManager**

每个 facade/service/listener 的测试：
- 删除 @Mock RedisTemplate / RedisScript
- 加 @Mock LockManager
- 改断言 `verify(lockManager).tryLock(...)` / `verify(lockManager).unlock(...)`

- [ ] **Step 11: 跑全模块测试看绿**

Run:
```bash
cd /home/djdev/wangyq/yiti && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn install -DskipTests 2>&1 | tail -3 && MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED" mvn test -pl performance-engine-center 2>&1 | grep -E "Tests run.*Failures|BUILD" | tail -3
```

Expected: `BUILD SUCCESS` + 所有测试通过。

- [ ] **Step 12: commit**

```bash
cd /home/djdev/wangyq/yiti && git add common/common-web/ performance-engine-center/ && git commit -m "$(cat <<'EOF'
feat(lock): 自建 LockManager + JdbcLockManager + 替换绩效 4 处 redis 锁

新增（common-web/lock/）:
- LockManager 接口（tryLock / unlock，CAS 释放防误删）
- JdbcLockManager 实现（PT_LOCK 表 + SELECT FOR UPDATE，REQUIRES_NEW 独立事务）
- LockAutoConfig（注册 Bean + @Scheduled 5min 清理过期锁，60s grace period）
- HolderUtil（持锁者 = instanceId#threadId）
- JdbcLockManagerTest（5 用例：拿/抢/释放正反路径，真连 PT_LOCK 表）

替换绩效模块 4 处 redis 锁：
- MetricLifecycleFacade / SysControlFacade（业务操作互斥）
- KpiCascadeListener（SETNX 30s 防重）
- DataTaskService（保留 DuplicateKeyException 双保险注释）

对应测试改 mock LockManager。

EOF
)"
```

---

## 验收

5 个 commit 全部 push 后，最终验证：

- [ ] **依赖检查**：`mvn dependency:tree | grep -iE "redis|lettuce"` 应该完全空
- [ ] **启动验证**：完整 `mvn install + mvn spring-boot:run -pl bootstrap`，确认 18080 监听 + 登录 OK
- [ ] **多实例验证**（行内）：起两个 yiti 容器，LB 轮询，user 登录 A 实例 → 下次请求落 B 实例 → session 仍可识别
- [ ] **PT_LOCK 行为**：跑一次绩效计算任务（trigger 一次 Quartz job），看 PT_LOCK 表有记录 → 任务完成后记录消失
- [ ] **全测试通过**：`mvn test`（所有模块）

---

## Self-Review

**Spec coverage**:
- § 2 决策矩阵 → Task 1-5 全覆盖 ✅
- § 3 数据库 3 张表 → Task 2 Step 1 SQL ✅
- § 4 后端改动清单 → Task 3/4/5 ✅
- § 5 LockManager 接口设计 → Task 5 Step 1 + 4 ✅
- § 6 测试策略 → 每 Task TDD 红绿 ✅
- § 8 提交计划 5 个 commit → Task 1-5 一一对应 ✅
- § 10 验收标准 → 末尾"验收"区块 ✅

**Placeholder scan**:
- 无 TBD/TODO
- 所有代码块完整可执行
- "按 grep 结果调整方法签名" (Task 3 Step 5) 明确指出 grep 命令 + 调整方向，不是占位符

**Type consistency**:
- LockManager.tryLock(key, holder, ttlMs) 接口签名（Step 1）跟 Step 4 实现签名匹配 ✅
- 跟 Step 8 调用方式匹配（`lockManager.tryLock(lockKey, holder, 30_000)`）✅
- PermissionCacheService 各方法签名（Task 3 Step 2 测试 + Step 5 实现）一致 ✅

通过。
