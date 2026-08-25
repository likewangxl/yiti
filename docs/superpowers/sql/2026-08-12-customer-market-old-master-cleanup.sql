-- 客户营销主档拆分收尾：清理 CUST_MASTER 中已迁入新表的营销记录。
--
-- 安全边界：
-- 1. 只处理已存在于 CUSTOMER_MARKET_CUSTOMER 的同 ID 记录；
-- 2. 只处理 statis_dt IS NULL 的非 M98 抽取记录；
-- 3. 删除前完整备份到 CUSTOMER_MARKET_MIGRATION_BAK_20260812；
-- 4. 可重复执行，不删除任何带 M98 统计日期的数据。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS CUSTOMER_MARKET_MIGRATION_BAK_20260812 LIKE CUST_MASTER;
ALTER TABLE CUSTOMER_MARKET_MIGRATION_BAK_20260812
    COMMENT = '客户营销主档拆分回滚备份（2026-08-12）';
ALTER TABLE CUST_MASTER
    COMMENT = 'M98客户主档表（不承载客户营销）';

INSERT IGNORE INTO CUSTOMER_MARKET_MIGRATION_BAK_20260812
SELECT old_customer.*
FROM CUST_MASTER old_customer
INNER JOIN CUSTOMER_MARKET_CUSTOMER market_customer
        ON CONVERT(market_customer.id USING utf8mb4) COLLATE utf8mb4_general_ci
         = CONVERT(old_customer.id USING utf8mb4) COLLATE utf8mb4_general_ci
WHERE old_customer.statis_dt IS NULL;

START TRANSACTION;

DELETE old_customer
FROM CUST_MASTER old_customer
INNER JOIN CUSTOMER_MARKET_MIGRATION_BAK_20260812 backup_customer
        ON backup_customer.id = old_customer.id
INNER JOIN CUSTOMER_MARKET_CUSTOMER market_customer
        ON CONVERT(market_customer.id USING utf8mb4) COLLATE utf8mb4_general_ci
         = CONVERT(old_customer.id USING utf8mb4) COLLATE utf8mb4_general_ci
WHERE old_customer.statis_dt IS NULL;

COMMIT;

SELECT COUNT(*) AS backup_count
FROM CUSTOMER_MARKET_MIGRATION_BAK_20260812;

SELECT COUNT(*) AS old_master_market_overlap
FROM CUST_MASTER old_customer
INNER JOIN CUSTOMER_MARKET_CUSTOMER market_customer
        ON CONVERT(market_customer.id USING utf8mb4) COLLATE utf8mb4_general_ci
         = CONVERT(old_customer.id USING utf8mb4) COLLATE utf8mb4_general_ci
WHERE old_customer.statis_dt IS NULL;

SELECT COUNT(*) AS m98_dated_customer_count
FROM CUST_MASTER
WHERE statis_dt IS NOT NULL;

-- 如需回滚本次清理，人工确认后执行：
-- INSERT IGNORE INTO CUST_MASTER
-- SELECT * FROM CUSTOMER_MARKET_MIGRATION_BAK_20260812;
