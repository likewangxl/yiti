-- ====================================================================
-- SPRING_SESSION_ATTRIBUTES 孤儿清理（GoldenDB 去外键后必需）
--
-- 背景：Spring Session JDBC 每分钟过期清理只发一条
--   DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?
-- 删属性靠 SPRING_SESSION_ATTRIBUTES → SPRING_SESSION 的
--   FOREIGN KEY ... ON DELETE CASCADE。
-- GoldenDB 不支持外键、已去掉 → 属性行不再被级联删除，
-- SPRING_SESSION_ATTRIBUTES 会无限增长，表越大 session 读写锁越重，
-- 加剧登录 Lock wait timeout。
--
-- 方案：DBA 在 GoldenDB 上配一个低峰期定时任务（如每天凌晨）跑下面的清理。
-- 注意：分布式库上 NOT IN 子查询可能跨分片，建议两表都按 PRIMARY_ID /
--       SESSION_PRIMARY_ID 分片后再跑；或低峰期执行。
-- ====================================================================

DELETE FROM SPRING_SESSION_ATTRIBUTES
WHERE SESSION_PRIMARY_ID NOT IN (
    SELECT PRIMARY_ID FROM SPRING_SESSION
);

-- 验证：
--   SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES a
--     LEFT JOIN SPRING_SESSION s ON a.SESSION_PRIMARY_ID = s.PRIMARY_ID
--    WHERE s.PRIMARY_ID IS NULL;   -- 清理后应为 0
