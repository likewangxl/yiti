# 去 Redis 设计

**日期**：2026-05-20  
**模块**：bootstrap / common-web / auth-permission-center / performance-engine-center  
**关联记忆**：[[2026-launch-context]] 6-10 上线 · [[wangyq-uias-edgecar-protocol]]（Spring Session JDBC 注意 Serializable）

---

## 1. 背景与硬约束

### 1.1 现状

yiti 后端当前依赖 Redis 5 处：

| # | 用途 | 文件 / 位置 |
|---|---|---|
| 1 | **Spring Session** —— HTTP session 跨节点共享 | `application*.yml` `spring.session.store-type=redis` + `spring.session.redis.namespace` |
| 2 | **RBAC 权限缓存**（5 分钟 TTL）| `auth-permission-center/.../CacheConfig.java` + `PermissionCacheService.java` |
| 3 | **绩效模块缓存配置** | `performance-engine-center/.../PerformanceRedisConfig.java`（@Cacheable / @CacheEvict） |
| 4 | **绩效批量查询缓存合并策略** | `performance-engine-center/.../AllocRelationService.java`（先查 redis → 未命中查 DB → 回写 redis） |
| 5 | **绩效分布式锁**（4 处 SETNX + Lua CAS 释放） | `MetricLifecycleFacade` / `SysControlFacade` / `KpiCascadeListener` / `DataTaskService` |

### 1.2 硬约束

- **行内环境没有 Redis 服务可用**——必须全部替换，不能保留任何 redis 依赖
- **多实例 Docker 容器部署 + LB**——session 必须**跨实例共享**（内存 session / 全局 Map 都不行）
- 6-10 上线倒排，改造工作量需可控（目标 1.5-2 天）

---

## 2. 决策矩阵

| 现 redis 用途 | 替代方案 | 选定理由 |
|---|---|---|
| Spring Session | **Spring Session JDBC** —— session 存 MySQL 2 张表 | 改 pom + yml + 加表 SQL 即可，servlet session API 不变，业务层零改动；性能比 redis 慢 1-3ms 无感 |
| RBAC 权限缓存 | **直接读 DB** —— PermissionCacheService 改 NoOp 风格 | PT_USER_ROLE/PT_ROLE_RESOURCE 主键索引点查 ~0.1ms，业务 SQL 本来就 10ms+，无感；YAGNI，发现热点再补 Caffeine |
| 绩效模块缓存 | **直接读 DB** —— 删 PerformanceRedisConfig + AllocRelationService 去缓存逻辑 | 同上策略一致 |
| 绩效分布式锁（4 处）| **自建 `PT_LOCK` 表 + `LockManager` 接口** —— SELECT FOR UPDATE 实现 | 零新依赖；接口抽象后 4 处统一替换 |

---

## 3. 数据库变更（手工 SQL，项目已废 Flyway）

3 张新表，零 ALTER 既有表。

### 3.1 `SPRING_SESSION` + `SPRING_SESSION_ATTRIBUTES`

完全沿用 spring-session-jdbc 4.0.x 官方 schema（`org/springframework/session/jdbc/schema-mysql.sql`），不做改动。

```sql
CREATE TABLE SPRING_SESSION (
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

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 `PT_LOCK`（自建分布式锁表）

```sql
CREATE TABLE PT_LOCK (
    LOCK_KEY    VARCHAR(128) NOT NULL COMMENT '锁标识（业务 key）',
    HOLDER      VARCHAR(64)  NOT NULL COMMENT '持锁者（实例 UUID + 线程标识）',
    ACQUIRED_AT BIGINT       NOT NULL COMMENT '获取锁的时间戳（unix ms）',
    EXPIRES_AT  BIGINT       NOT NULL COMMENT '过期时间戳（unix ms），过期可被强占',
    PRIMARY KEY (LOCK_KEY),
    INDEX IDX_PT_LOCK_EXPIRES (EXPIRES_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式锁（去 Redis 后自建）';
```

种子 SQL 文件：`docs/superpowers/sql/2026-05-20-remove-redis-init.sql`（含上述全部建表语句）。

---

## 4. 后端改动清单（按模块）

### 4.1 `bootstrap` 模块

**pom.xml**：
- 删依赖：`spring-boot-starter-data-redis` / `lettuce-core` / `spring-session-data-redis`
- 加依赖：`spring-session-jdbc`

**`application.yml`**：
```yaml
spring:
  session:
    store-type: jdbc
    timeout: 7200s
    jdbc:
      initialize-schema: never   # 手工建表
      table-name: SPRING_SESSION
  # 删整段 spring.data.redis.* 配置
```

**`application-dev.yml`**：
```yaml
# 删 spring.data.redis.database / spring.session.redis.namespace 整段
```

### 4.2 `common-web` 模块

如果有 RedisAutoConfiguration / RedisTemplate Bean 引用，删除。grep 一遍确认。

### 4.3 `auth-permission-center` 模块

**pom.xml**：删 spring-boot-starter-data-redis 依赖（如有直接声明）。

**`CacheConfig.java`**：整个删除（不再需要 RedisCacheManager / RedisTemplate Bean）。

**`PermissionCacheService.java`**：
- 删 `RedisTemplate` / `stringRedisTemplate` 注入
- `getRoleIdsByEmpId(empId)` → 直接调 `userRoleMapper.selectRoleIdsByUserId(empId)`
- `getResourceIdsByRoleId(roleId)` → 直接调 `roleResourceMapper.selectResourceIdsByRoleId(roleId)`
- `getBizScopesByRoleId(roleId)` → 直接调对应 mapper
- `evictXxxCache(...)` 方法**保留签名变 NoOp**（业务层一堆地方调用，签名不变避免大改）
- `publishCacheInvalidatedEvent(...)` 保留（事件本身有别的用途，跟 cache 解耦）

**测试**：
- `PermissionCacheServiceTest.java`：mock RedisTemplate 改 mock 对应 Mapper，断言"调 Mapper"而不是"调 Redis"

### 4.4 `performance-engine-center` 模块

**`PerformanceRedisConfig.java`**：整个删除。

**`MetricLifecycleFacade.java`** —— 业务操作互斥锁：
- 删 `RedisTemplate` / `RedisScript COMPARE_AND_DEL` 字段
- 锁逻辑改 `lockManager.tryLock(key, holder, ttl)` / `lockManager.unlock(key, holder)`

**`SysControlFacade.java`** —— 同上

**`KpiCascadeListener.java`** —— SETNX 30s 防重：
- 删 `RedisTemplate` 字段
- 改 `lockManager.tryLock("kpi-cascade:" + scheme + ":" + cycle, holder, 30000)`

**`DataTaskService.java`** —— SETNX 30s + DB 唯一键兜底：
- 删 `RedisTemplate` 字段
- 改 `lockManager.tryLock(...)` —— 保留 `try-catch DuplicateKeyException` 双保险（注释明确"两道防线"）

**`AllocRelationService.java`** —— 批量查询缓存：
- 删 `RedisTemplate` 字段 + 缓存命中合并逻辑
- `getXxx` 直接调 mapper 全量查（接口签名不变）

**测试**：6 处 redis 测试改 mock LockManager / 移除缓存断言。

### 4.5 全局测试配置

`bootstrap/src/test/java/.../TestMockConfig.java` 等：删 RedisTemplate / RedisConnectionFactory mock；改 mock LockManager。
其他模块 `application-test.yml` 删 redis 段。

---

## 5. LockManager 接口设计

放 `common-web/src/main/java/com/bank/branch/platform/common/web/lock/`（让其他模块通用，不放 perf 私有）。

### 5.1 接口

```java
package com.bank.branch.platform.common.web.lock;

/**
 * 分布式锁（去 Redis 后基于 PT_LOCK 表 + SELECT FOR UPDATE 实现）。
 * <p>holder 用于 CAS 释放：只有持锁者本人能 unlock，防止误删别人的锁。</p>
 */
public interface LockManager {

    /**
     * 尝试拿锁；不阻塞，立即返回。
     * @param key     业务锁标识（建议命名空间前缀，如 "perf:metric:M_0088"）
     * @param holder  持锁者标识（建议 instanceId + threadId，全局唯一）
     * @param ttlMs   锁租期（毫秒），到期可被别人强占
     * @return        true=拿到锁；false=别人持有未过期
     */
    boolean tryLock(String key, String holder, long ttlMs);

    /**
     * 释放锁。CAS：只有 holder 匹配的才删，防误删。
     * @return true=成功释放；false=持锁者不是 holder（说明锁已被强占或已释放）
     */
    boolean unlock(String key, String holder);
}
```

### 5.2 实现

`JdbcLockManager implements LockManager`，思路：

**tryLock**：
```
BEGIN (REQUIRES_NEW，独立事务避免污染业务事务)
  SELECT * FROM PT_LOCK WHERE LOCK_KEY = ? FOR UPDATE
  IF 行不存在
      INSERT INTO PT_LOCK(LOCK_KEY, HOLDER, ACQUIRED_AT, EXPIRES_AT) VALUES (?, ?, now, now+ttl)
      COMMIT; return true
  ELSE IF EXPIRES_AT <= now (锁过期可强占)
      UPDATE PT_LOCK SET HOLDER=?, ACQUIRED_AT=now, EXPIRES_AT=now+ttl WHERE LOCK_KEY=?
      COMMIT; return true
  ELSE
      COMMIT; return false
END
```

**unlock**：
```
DELETE FROM PT_LOCK WHERE LOCK_KEY = ? AND HOLDER = ?
return rowsAffected > 0
```

**清理过期锁**（@Scheduled 5 分钟一次）：
```
DELETE FROM PT_LOCK WHERE EXPIRES_AT < now - 60000  -- 留 1 分钟 grace period
```

### 5.3 持锁者 holder 怎么生成

建议每个实例启动时生成一个 `instanceId = UUID.randomUUID()`（Bean 启动时一次），调用时 `holder = instanceId + "#" + Thread.currentThread().getId()`。这样多实例多线程都能区分。

---

## 6. 测试策略

### 6.1 单元测试（TDD 红 → 绿 → 重构）

- `JdbcLockManagerTest`（新增）
  - `tryLock_首次拿_成功`
  - `tryLock_别人持有未过期_失败`
  - `tryLock_别人持有但过期_成功强占`
  - `unlock_持锁者匹配_成功删除`
  - `unlock_持锁者不匹配_拒绝删除`
- `PermissionCacheServiceTest`（改造）
  - mock RedisTemplate → mock Mapper
  - 断言"调 Mapper"而不是"读缓存"
- `MetricLifecycleFacadeTest` / `SysControlFacadeTest` / `KpiCascadeListenerTest` / `DataTaskServiceTest`（改造）
  - mock LockManager.tryLock / unlock
- `AllocRelationServiceTest`（改造）
  - 删除"缓存命中→不查 DB"断言
  - 加"始终查 DB"断言

### 6.2 集成测试

- `SessionJdbcIT`（新增）—— 启动 yiti，login → 关 HttpSession → 检查 SPRING_SESSION 表有记录 → 重启 → 用同 cookie 再访问 → session 仍可恢复
- `PtLockConcurrentIT`（新增）—— 多线程并发 tryLock 同 key，只有 1 个成功

### 6.3 删除的测试

- 凡是 `mock RedisTemplate` / `embedded redis` 的，删除或重写

---

## 7. 兼容性 & 风险

### 7.1 兼容性

| 现有功能 | 影响 |
|---|---|
| HTTP session API（`session.setAttribute` / `getAttribute`）| 0 影响（spring-session-jdbc 完全 servlet 兼容）|
| `CurrentUserContext` 存 session | 0 影响（继续 implements Serializable，前 UIAS 联调时已处理）|
| RBAC 鉴权链路（AuthenticationFilter / AuthorizationInterceptor）| 0 影响（PermissionCacheService 接口签名不变）|
| `PermissionCacheInvalidatedEvent` 事件 | 保留发布（业务可能监听），但 cache 已无所谓"失效"，事件改为通知作用 |
| Quartz 调度（QRTZ_LOCKS）| 0 影响（本来就不依赖 redis）|

### 7.2 风险与缓解

| 风险 | 概率 | 缓解 |
|---|---|---|
| Spring Session 表存的 attribute 必须 Serializable | 低（已踩过坑）| Code review 时 grep `setAttribute` 复核；UIAS 那个坑已经修过同类问题 |
| `PT_LOCK` 表锁竞争激烈时 SELECT FOR UPDATE 阻塞 | 中 | tryLock 立即返回不阻塞（非阻塞获取）；过期锁有 @Scheduled 清理 |
| 持锁者宕机不能 unlock | 中 | TTL 到期后别人能强占；重启实例的 holder UUID 变化，旧锁自然过期 |
| 权限读 DB 在峰值时 QPS 高 | 低 | MySQL buffer pool 缓存热数据；监控 + 后期加 Caffeine 应急 |
| Session JDBC 在 MySQL 故障时全员登出 | 低 | 跟原 redis 故障同等性质；MySQL 一般更稳 |

---

## 8. 提交计划（5 个原子 commit）

按依赖顺序，每个 commit 自带测试 + 可独立 review：

```
commit 1: chore(deps): bootstrap pom 移除 redis + 加 spring-session-jdbc + yml 清 redis 配置
          - bootstrap/pom.xml（删 redis/lettuce/spring-session-data-redis，加 spring-session-jdbc）
          - application.yml / application-dev.yml（删 spring.data.redis.* + 改 spring.session.store-type=jdbc）

commit 2: feat(session): Spring Session JDBC 建表 SQL + 集成测试
          - docs/superpowers/sql/2026-05-20-remove-redis-init.sql（SPRING_SESSION + SPRING_SESSION_ATTRIBUTES + PT_LOCK 一起建）
          - SessionJdbcIT.java（新增）

commit 3: refactor(auth-cache): PermissionCacheService 去 redis 直接读 DB（接口签名不变）
          - auth-permission-center/.../CacheConfig.java（删）
          - PermissionCacheService.java（改）
          - PermissionCacheServiceTest.java（改 mock）

commit 4: refactor(perf-cache): 绩效模块 2 处缓存去 redis + PerformanceRedisConfig 删
          - performance-engine-center/.../PerformanceRedisConfig.java（删）
          - AllocRelationService.java（改）
          - 对应测试改 mock

commit 5: feat(lock): 自建 LockManager + JdbcLockManager + 替换绩效 4 处 redis 锁
          - common-web/.../lock/LockManager.java（新增接口）
          - common-web/.../lock/JdbcLockManager.java（新增实现）
          - common-web/.../lock/LockAutoConfig.java（新增 + 注册到 AutoConfiguration.imports）
          - MetricLifecycleFacade / SysControlFacade / KpiCascadeListener / DataTaskService（改）
          - JdbcLockManagerTest + PtLockConcurrentIT（新增）
          - 4 个 facade/service/listener 的测试（改 mock）
```

依赖关系：
- commit 1 完成后服务能起，但 session 还没建表（启动时报错）
- commit 2 加表 SQL 后才能完整启动（手工执行 SQL）
- commit 3-5 后端代码增量改造，每个独立 ship

---

## 9. 回滚预案

如果生产出问题，按 commit 倒序 revert：

1. 单个 commit 失败 → `git revert <commit>` 然后重新部署
2. 全部失败 → revert 5 个 commit 一并部署 + 重新启用 redis（行内 redis 服务先重新拉起）

**SQL 不回滚**——3 张新表保留，反正 yiti 库不缺这点空间。下次重做时数据还在。

---

## 10. 验收标准

- [ ] bootstrap 启动**零 redis 依赖** —— `mvn dependency:tree | grep redis` 应该完全空
- [ ] yiti 启动后 `SPRING_SESSION` 表能看到记录（login 一次就有）
- [ ] 多实例（2 个 yiti container）背靠 LB 轮询，用户 login 后任一实例都能恢复 session
- [ ] 绩效模块 4 处锁场景 `PT_LOCK` 表能看到记录，到期自动清理
- [ ] 权限校验功能完整可用（RBAC + 数据范围）
- [ ] 全部模块单元测试 + 集成测试通过（auth-permission-center 158+、performance-engine-center 全套）
- [ ] **TDD 红线**：每个新方法（LockManager）测试先红再绿

---

## 11. 关联记忆 / 文档

- `[[2026-launch-context]]` 6-10 上线，本期改造需在 W2 完成
- `[[wangyq-uias-edgecar-protocol]]` § 6 提到 Spring Session attribute Serializable 坑，本次 JDBC 同坑
- 现有 `common/CLAUDE.md` § common-aop / common-db 不受本次影响

---

**文件位置**：`docs/superpowers/specs/2026-05-20-remove-redis-design.md`  
**关联 SQL**：`docs/superpowers/sql/2026-05-20-remove-redis-init.sql`（实施 commit 2 创建）
