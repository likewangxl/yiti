-- ====================================================================
-- 去 Redis 初始化 SQL：建 3 张新表
--
-- 1. SPRING_SESSION + SPRING_SESSION_ATTRIBUTES —— spring-session-jdbc 官方 schema
-- 2. PT_LOCK —— 自建分布式锁表（Task 5 LockManager 用）
--
-- 幂等：脚本可重复执行（用 IF NOT EXISTS）
-- 关联 spec: docs/superpowers/specs/2026-05-20-remove-redis-design.md
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
