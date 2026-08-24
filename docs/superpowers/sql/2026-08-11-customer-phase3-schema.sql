-- 客户营销 V2 第三阶段：客户标签审核、跨机构营销、客户转交。
-- 执行前：USE yiti，并备份 CUST_TAG / CUST_TAG_REL / PT_RESOURCE 权限表。
-- 可重复执行；不使用 Flyway。

SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS customer_phase3_add_column$$
CREATE PROCEDURE customer_phase3_add_column(
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

DROP PROCEDURE IF EXISTS customer_phase3_add_index$$
CREATE PROCEDURE customer_phase3_add_index(
    IN p_table varchar(64), IN p_index varchar(64), IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL customer_phase3_add_column('CUST_TAG', 'tag_type',
    'varchar(30) NULL COMMENT ''标签类型：PROJECT/CERTIFICATION'' AFTER `status`');
CALL customer_phase3_add_column('CUST_TAG', 'approval_status',
    'varchar(20) NOT NULL DEFAULT ''APPROVED'' COMMENT ''PENDING/APPROVED/REJECTED'' AFTER `tag_type`');
CALL customer_phase3_add_column('CUST_TAG', 'expires_at',
    'date NULL COMMENT ''失效日期'' AFTER `approval_status`');
CALL customer_phase3_add_column('CUST_TAG', 'owner_org_id',
    'varchar(50) NULL COMMENT ''创建机构'' AFTER `expires_at`');
CALL customer_phase3_add_column('CUST_TAG', 'reviewed_by',
    'varchar(32) NULL COMMENT ''审核人工号'' AFTER `owner_org_id`');
CALL customer_phase3_add_column('CUST_TAG', 'reviewed_time',
    'datetime NULL COMMENT ''审核时间'' AFTER `reviewed_by`');
CALL customer_phase3_add_column('CUST_TAG', 'reject_reason',
    'varchar(500) NULL COMMENT ''退回原因'' AFTER `reviewed_time`');
CALL customer_phase3_add_index('CUST_TAG', 'idx_tag_approval', '(`approval_status`,`created_time`)');

CALL customer_phase3_add_column('CUST_TAG_REL', 'active',
    'tinyint(1) NOT NULL DEFAULT 1 COMMENT ''当前有效标记'' AFTER `created_time`');
CALL customer_phase3_add_column('CUST_TAG_REL', 'effective_time',
    'datetime NULL COMMENT ''生效时间'' AFTER `active`');
CALL customer_phase3_add_column('CUST_TAG_REL', 'expired_time',
    'datetime NULL COMMENT ''失效时间'' AFTER `effective_time`');
CALL customer_phase3_add_column('CUST_TAG_REL', 'updated_by',
    'varchar(32) NULL COMMENT ''最近更新人'' AFTER `expired_time`');
CALL customer_phase3_add_column('CUST_TAG_REL', 'updated_time',
    'datetime NULL COMMENT ''最近更新时间'' AFTER `updated_by`');
UPDATE CUST_TAG_REL
SET active = 1,
    effective_time = COALESCE(effective_time, created_time),
    updated_time = COALESCE(updated_time, created_time)
WHERE active IS NULL OR effective_time IS NULL OR updated_time IS NULL;

CREATE TABLE IF NOT EXISTS CROSS_ORG_MARKETING_RULE (
    id varchar(32) NOT NULL,
    rule_code varchar(64) NOT NULL,
    rule_name varchar(128) NOT NULL,
    enabled tinyint(1) NOT NULL DEFAULT 1,
    data_source varchar(64) NOT NULL,
    failure_message varchar(256) NOT NULL,
    extension_params varchar(1000) DEFAULT NULL,
    sort_no int NOT NULL DEFAULT 0,
    updated_by varchar(32) DEFAULT NULL,
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_cross_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨机构客户营销校验规则';

CREATE TABLE IF NOT EXISTS CROSS_ORG_MARKETING_APPLY (
    id varchar(32) NOT NULL,
    apply_no varchar(64) NOT NULL,
    cust_id varchar(32) NOT NULL,
    cust_no varchar(100) DEFAULT NULL,
    applicant_emp_id varchar(32) NOT NULL,
    applicant_org_id varchar(50) NOT NULL,
    main_manager_id varchar(32) DEFAULT NULL,
    main_org_id varchar(50) DEFAULT NULL,
    applicant_not_main_check tinyint(1) NOT NULL,
    main_org_different_check tinyint(1) NOT NULL,
    applicant_no_performance_check tinyint(1) NOT NULL,
    applicant_org_no_performance_check tinyint(1) NOT NULL,
    check_snapshot_time datetime NOT NULL,
    apply_reason varchar(500) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    generated_touch_task_id varchar(32) DEFAULT NULL,
    business_key varchar(100) NOT NULL,
    process_instance_id varchar(64) DEFAULT NULL,
    reviewed_by varchar(32) DEFAULT NULL,
    reviewed_time datetime DEFAULT NULL,
    reject_reason varchar(500) DEFAULT NULL,
    created_by varchar(32) NOT NULL,
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by varchar(32) DEFAULT NULL,
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cross_apply_no (apply_no),
    UNIQUE KEY uk_cross_business_key (business_key),
    KEY idx_cross_applicant_status (applicant_emp_id, status, created_time),
    KEY idx_cross_customer_status (cust_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨机构客户营销申请';

CREATE TABLE IF NOT EXISTS CUST_PERFORMANCE_RELATION_SNAPSHOT (
    id varchar(32) NOT NULL, cust_id varchar(32) NOT NULL,
    subject_type varchar(20) NOT NULL, subject_id varchar(50) NOT NULL,
    related_emp_id varchar(32) DEFAULT NULL, related_org_id varchar(50) NOT NULL,
    relation_type varchar(30) NOT NULL, ratio decimal(8,4) DEFAULT NULL,
    effective_date date NOT NULL, expiry_date date DEFAULT NULL,
    source_system varchar(32) NOT NULL DEFAULT 'M98', source_batch_id varchar(64) NOT NULL,
    refreshed_at datetime NOT NULL, refresh_status varchar(20) NOT NULL DEFAULT 'SUCCESS',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perf_rel_snapshot (cust_id, subject_type, subject_id, source_batch_id),
    KEY idx_perf_rel_customer (cust_id, refresh_status, effective_date),
    KEY idx_perf_rel_subject (subject_type, subject_id, refresh_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户业绩归属快照';

CREATE TABLE IF NOT EXISTS CUST_TRANSFER_LOG (
    id varchar(32) NOT NULL, transfer_no varchar(64) NOT NULL, cust_id varchar(32) NOT NULL,
    claim_id varchar(32) DEFAULT NULL, from_manager_id varchar(32) DEFAULT NULL,
    from_org_id varchar(50) DEFAULT NULL, primary_to_manager_id varchar(32) NOT NULL,
    primary_to_org_id varchar(50) NOT NULL, account_opened_snapshot tinyint(1) NOT NULL,
    reason varchar(500) NOT NULL, status varchar(20) NOT NULL DEFAULT 'CREATED',
    operator_emp_id varchar(32) NOT NULL, completed_time datetime DEFAULT NULL,
    failure_reason varchar(500) DEFAULT NULL, created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_transfer_customer_time (cust_id, created_time),
    KEY idx_transfer_operator (operator_emp_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户转交记录';

CREATE TABLE IF NOT EXISTS CUST_TRANSFER_TARGET (
    id varchar(32) NOT NULL, transfer_id varchar(32) NOT NULL,
    target_emp_id varchar(32) NOT NULL, target_org_id varchar(50) NOT NULL,
    target_role varchar(20) NOT NULL, sort_no int NOT NULL DEFAULT 0,
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_transfer_target (transfer_id, target_emp_id),
    KEY idx_transfer_target_emp (target_emp_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户转交接收人';

INSERT INTO CROSS_ORG_MARKETING_RULE
    (id, rule_code, rule_name, enabled, data_source, failure_message, sort_no, updated_by)
VALUES
    ('RULE_CROSS_NOT_MAIN', 'APPLICANT_NOT_MAIN_MANAGER', '申请人不是客户主办', 1, 'CCRM', '申请人是当前主办客户经理', 10, 'customer-phase3-0811'),
    ('RULE_CROSS_ORG_DIFF', 'MAIN_ORG_DIFFERENT', '申请机构与主办机构不同', 1, 'CCRM', '申请机构与主办机构相同', 20, 'customer-phase3-0811'),
    ('RULE_CROSS_EMP_NOPF', 'APPLICANT_NO_PERFORMANCE', '申请人无业绩归属', 1, 'M98_SNAPSHOT', '申请人已有业绩归属', 30, 'customer-phase3-0811'),
    ('RULE_CROSS_ORG_NOPF', 'APPLICANT_ORG_NO_PERFORMANCE', '申请机构无业绩归属', 1, 'M98_SNAPSHOT', '申请机构已有业绩归属', 40, 'customer-phase3-0811') AS new
ON DUPLICATE KEY UPDATE rule_name=new.rule_name, enabled=new.enabled, data_source=new.data_source,
    failure_message=new.failure_message, sort_no=new.sort_no, updated_by=new.updated_by;

DROP PROCEDURE IF EXISTS customer_phase3_add_column;
DROP PROCEDURE IF EXISTS customer_phase3_add_index;

SELECT 'customer phase3 schema aligned' AS result;
