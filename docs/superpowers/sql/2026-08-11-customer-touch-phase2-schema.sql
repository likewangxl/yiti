-- 客户营销 V2 第二阶段：个人认领与触达日志结构对齐
-- 执行前：USE yiti，并备份 CUST_CLAIM / TOUCH_TASK / TOUCH_LOG。
-- 本脚本可重复执行；不使用 Flyway。

SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS customer_touch_add_column$$
CREATE PROCEDURE customer_touch_add_column(
    IN p_table varchar(64), IN p_column varchar(64), IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS customer_touch_drop_index$$
CREATE PROCEDURE customer_touch_drop_index(IN p_table varchar(64), IN p_index varchar(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS customer_touch_add_index$$
CREATE PROCEDURE customer_touch_add_index(
    IN p_table varchar(64), IN p_index varchar(64), IN p_columns varchar(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 最新口径允许同一客户被多名员工分别认领，员工本人不可重复认领。
CALL customer_touch_drop_index('CUST_CLAIM', 'uk_cust_org');
CALL customer_touch_drop_index('CUST_CLAIM', 'uk_cust_claim_org');
CALL customer_touch_add_index('CUST_CLAIM', 'uk_cust_claim_emp', '`cust_id`,`claimed_by`');

CALL customer_touch_add_column('TOUCH_LOG', 'touch_method',
    'varchar(30) NULL COMMENT ''触达方式：VISIT/PHONE/WECHAT/OTHER'' AFTER `log_content`');
CALL customer_touch_add_column('TOUCH_LOG', 'participant_emp_ids',
    'text NULL COMMENT ''协同人员工工号JSON数组'' AFTER `touch_method`');
CALL customer_touch_add_column('TOUCH_LOG', 'photo_groups',
    'text NULL COMMENT ''分类照片JSON：keyPerson/doorplate/workplace'' AFTER `photo_urls`');
CALL customer_touch_add_column('TOUCH_LOG', 'operator_location',
    'varchar(1000) NULL COMMENT ''办理定位JSON或地址'' AFTER `photo_groups`');

-- 空库可直接切换；存量 GREEN 在读取端兼容，新增任务统一写 BLUE。
UPDATE TOUCH_TASK SET sla_status = 'BLUE' WHERE sla_status = 'GREEN';

DROP PROCEDURE IF EXISTS customer_touch_add_column;
DROP PROCEDURE IF EXISTS customer_touch_drop_index;
DROP PROCEDURE IF EXISTS customer_touch_add_index;

SELECT 'customer touch phase2 schema aligned' AS result;
