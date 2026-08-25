-- ============================================================================
-- customer-marketing-center V2 Phase 1 schema alignment
-- Scope: customer list / lead entry / lead approval
-- Target: the currently selected MySQL 8.0 schema (USE <database> before running)
--
-- Important:
--   1. Back up CUST_MASTER / CUST_LEAD / LEAD_IMPORT_BATCH before execution.
--   2. This script does not update business data and does not deploy Flowable definitions.
--   3. Adding uk_cust_unified_credit_code fails closed when non-empty duplicate credit codes exist;
--      resolve duplicates first and rerun the script.
--   4. For compatibility with historical rows, this script does not tighten the existing
--      CUST_LEAD.unified_credit_code column to NOT NULL. New V2 writes must enforce it in the API;
--      a newly initialized database gets the NOT NULL constraint from docs/schema/ddl-customer.sql.
--   5. No Flyway dependency is used. The script is idempotent through INFORMATION_SCHEMA checks.
-- ============================================================================

SET NAMES utf8mb4;

-- Preflight: a database must be explicitly selected.
SET @customer_v2_schema := DATABASE();
SET @customer_v2_schema_missing := IF(@customer_v2_schema IS NULL, 1, 0);
SELECT IF(@customer_v2_schema_missing = 0,
          CONCAT('customer V2 target schema: ', @customer_v2_schema),
          'ERROR: run USE <database> before this script') AS preflight_message;

DELIMITER $$

DROP PROCEDURE IF EXISTS customer_v2_add_column$$
CREATE PROCEDURE customer_v2_add_column(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_column_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = p_table_name
           AND COLUMN_NAME = p_column_name
    ) THEN
        SET @customer_v2_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD COLUMN `', REPLACE(p_column_name, '`', '``'), '` ',
            p_column_definition
        );
        PREPARE customer_v2_stmt FROM @customer_v2_ddl;
        EXECUTE customer_v2_stmt;
        DEALLOCATE PREPARE customer_v2_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS customer_v2_add_index$$
CREATE PROCEDURE customer_v2_add_index(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_index_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = p_table_name
           AND INDEX_NAME = p_index_name
    ) THEN
        SET @customer_v2_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD ', p_index_definition
        );
        PREPARE customer_v2_stmt FROM @customer_v2_ddl;
        EXECUTE customer_v2_stmt;
        DEALLOCATE PREPARE customer_v2_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS customer_v2_drop_index$$
CREATE PROCEDURE customer_v2_drop_index(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = p_table_name
           AND INDEX_NAME = p_index_name
    ) THEN
        SET @customer_v2_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` DROP INDEX `', REPLACE(p_index_name, '`', '``'), '`'
        );
        PREPARE customer_v2_stmt FROM @customer_v2_ddl;
        EXECUTE customer_v2_stmt;
        DEALLOCATE PREPARE customer_v2_stmt;
    END IF;
END$$

DELIMITER ;

-- --------------------------------------------------------------------------
-- 1. Extend CUST_LEAD
-- --------------------------------------------------------------------------
CALL customer_v2_add_column('CUST_LEAD', 'lead_type',
    'varchar(30) NOT NULL DEFAULT ''NEW_ACCOUNT'' COMMENT ''线索类型：NEW_ACCOUNT/EXISTING_MARKETING'' AFTER `lead_op`');
CALL customer_v2_add_column('CUST_LEAD', 'cust_no',
    'varchar(100) NULL COMMENT ''CCRM客户号；新客户未开户时为空'' AFTER `is_latest`');
CALL customer_v2_add_column('CUST_LEAD', 'distribution_mode',
    'varchar(20) NOT NULL DEFAULT ''PUBLIC'' COMMENT ''分配方式：PUBLIC/SCOPE/OWNER'' AFTER `lead_source`');
CALL customer_v2_add_column('CUST_LEAD', 'main_manager_id',
    'varchar(32) NULL COMMENT ''主办专属客户经理工号'' AFTER `distribution_mode`');
CALL customer_v2_add_column('CUST_LEAD', 'main_manager_org_id',
    'varchar(50) NULL COMMENT ''主办客户经理机构快照'' AFTER `main_manager_id`');
CALL customer_v2_add_column('CUST_LEAD', 'submitted_by',
    'varchar(32) NULL COMMENT ''提交审批人工号'' AFTER `created_by`');
CALL customer_v2_add_column('CUST_LEAD', 'submitted_time',
    'datetime NULL COMMENT ''提交审批时间'' AFTER `submitted_by`');
CALL customer_v2_add_column('CUST_LEAD', 'batch_row_no',
    'int NULL COMMENT ''源文件数据行号'' AFTER `import_batch_id`');
CALL customer_v2_add_column('CUST_LEAD', 'reviewed_by',
    'varchar(32) NULL COMMENT ''最终审批人工号快照'' AFTER `process_instance_id`');
CALL customer_v2_add_column('CUST_LEAD', 'reviewed_time',
    'datetime NULL COMMENT ''最终审批时间'' AFTER `reviewed_by`');
CALL customer_v2_add_column('CUST_LEAD', 'reject_reason',
    'varchar(500) NULL COMMENT ''最终退回原因'' AFTER `reviewed_time`');
CALL customer_v2_add_column('CUST_LEAD', 'lock_version',
    'int NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');
CALL customer_v2_add_column('CUST_LEAD', 'active_new_credit_code',
    'varchar(50) GENERATED ALWAYS AS (CASE WHEN `lead_type` = ''NEW_ACCOUNT'' AND `is_latest` = 1 AND `deleted` = 0 THEN `unified_credit_code` ELSE NULL END) STORED COMMENT ''新客户最新线索幂等键'' AFTER `lock_version`');

CALL customer_v2_add_index('CUST_LEAD', 'uk_lead_active_new_credit_code',
    'UNIQUE KEY `uk_lead_active_new_credit_code` (`active_new_credit_code`)');
CALL customer_v2_add_index('CUST_LEAD', 'uk_lead_business_key',
    'UNIQUE KEY `uk_lead_business_key` (`business_key`)');
CALL customer_v2_add_index('CUST_LEAD', 'uk_lead_process_instance',
    'UNIQUE KEY `uk_lead_process_instance` (`process_instance_id`)');
CALL customer_v2_add_index('CUST_LEAD', 'idx_import_batch_row',
    'KEY `idx_import_batch_row` (`import_batch_id`, `batch_row_no`)');
CALL customer_v2_add_index('CUST_LEAD', 'idx_lead_entry_list',
    'KEY `idx_lead_entry_list` (`created_by`, `is_latest`, `deleted`, `lead_status`, `created_time`)');
CALL customer_v2_add_index('CUST_LEAD', 'idx_lead_approval_list',
    'KEY `idx_lead_approval_list` (`lead_status`, `owner_org_id`, `submitted_time`)');

-- --------------------------------------------------------------------------
-- 2. Extend LEAD_IMPORT_BATCH
-- --------------------------------------------------------------------------
ALTER TABLE `LEAD_IMPORT_BATCH`
    MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'CREATED'
        COMMENT '状态：CREATED/VALIDATION_FAILED/PENDING_APPROVAL/APPROVED/REJECTED';

CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'source_file_object_id',
    'varchar(32) NULL COMMENT ''原始导入文件对象ID'' AFTER `source_file_name`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'valid_row_count',
    'int NOT NULL DEFAULT 0 COMMENT ''校验通过行数'' AFTER `total_row_count`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'validation_finished_time',
    'datetime NULL COMMENT ''整批校验完成时间'' AFTER `error_file_object_id`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'submitted_by',
    'varchar(32) NULL COMMENT ''提交审批人工号'' AFTER `created_by`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'submitted_time',
    'datetime NULL COMMENT ''提交审批时间'' AFTER `submitted_by`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'reviewed_by',
    'varchar(32) NULL COMMENT ''最终审批人工号快照'' AFTER `submitted_time`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'reviewed_time',
    'datetime NULL COMMENT ''最终审批时间'' AFTER `reviewed_by`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'reject_reason',
    'varchar(500) NULL COMMENT ''最终退回原因'' AFTER `reviewed_time`');
CALL customer_v2_add_column('LEAD_IMPORT_BATCH', 'lock_version',
    'int NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `updated_time`');

CALL customer_v2_add_index('LEAD_IMPORT_BATCH', 'uk_batch_business_key',
    'UNIQUE KEY `uk_batch_business_key` (`business_key`)');
CALL customer_v2_add_index('LEAD_IMPORT_BATCH', 'uk_batch_process_instance',
    'UNIQUE KEY `uk_batch_process_instance` (`process_instance_id`)');
CALL customer_v2_add_index('LEAD_IMPORT_BATCH', 'idx_batch_approval_list',
    'KEY `idx_batch_approval_list` (`status`, `owner_org_id`, `submitted_time`)');
CALL customer_v2_add_index('LEAD_IMPORT_BATCH', 'idx_created_by_time',
    'KEY `idx_created_by_time` (`created_by`, `created_time`)');

-- --------------------------------------------------------------------------
-- 3. Extend CUST_MASTER
-- --------------------------------------------------------------------------
-- NULL cust_no represents an approved prospect that has not opened an account.
ALTER TABLE `CUST_MASTER`
    MODIFY COLUMN `cust_no` varchar(100) NULL COMMENT 'CCRM客户号；未开户潜客为空';

CALL customer_v2_add_column('CUST_MASTER', 'current_lead_id',
    'varchar(32) NULL COMMENT ''当前生效的审批通过线索版本ID'' AFTER `lead_id`');
CALL customer_v2_add_column('CUST_MASTER', 'main_manager_id',
    'varchar(32) NULL COMMENT ''当前主办客户经理工号'' AFTER `current_lead_id`');
CALL customer_v2_add_column('CUST_MASTER', 'main_org_id',
    'varchar(50) NULL COMMENT ''当前主办客户经理机构代码'' AFTER `main_manager_id`');
CALL customer_v2_add_column('CUST_MASTER', 'ownership_status',
    'varchar(30) NOT NULL DEFAULT ''UNASSIGNED'' COMMENT ''主办状态：UNASSIGNED/ASSIGNED/WAITING_CLAIM/MULTI_CLAIMED'' AFTER `main_org_id`');
CALL customer_v2_add_column('CUST_MASTER', 'last_touch_time',
    'datetime NULL COMMENT ''最近一次有效触达时间'' AFTER `ownership_status`');
CALL customer_v2_add_column('CUST_MASTER', 'source_system',
    'varchar(32) NOT NULL DEFAULT ''LOCAL'' COMMENT ''主数据来源：LOCAL/CCRM/YB0/CW35'' AFTER `last_touch_time`');
CALL customer_v2_add_column('CUST_MASTER', 'source_updated_time',
    'datetime NULL COMMENT ''源系统最后更新时间'' AFTER `source_system`');
CALL customer_v2_add_column('CUST_MASTER', 'lock_version',
    'int NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `updated_time`');

-- V2 freezes credit code (not customer name) as the idempotency key.
-- The UNIQUE index allows multiple NULL values for legacy CCRM rows.
CALL customer_v2_drop_index('CUST_MASTER', 'idx_unified_credit_code');
CALL customer_v2_add_index('CUST_MASTER', 'uk_cust_unified_credit_code',
    'UNIQUE KEY `uk_cust_unified_credit_code` (`unified_credit_code`)');
CALL customer_v2_drop_index('CUST_MASTER', 'uk_cust_name');
CALL customer_v2_add_index('CUST_MASTER', 'idx_cust_name',
    'KEY `idx_cust_name` (`cust_name`)');
CALL customer_v2_add_index('CUST_MASTER', 'idx_current_lead',
    'KEY `idx_current_lead` (`current_lead_id`)');
CALL customer_v2_add_index('CUST_MASTER', 'idx_main_manager',
    'KEY `idx_main_manager` (`main_manager_id`)');
CALL customer_v2_add_index('CUST_MASTER', 'idx_main_org',
    'KEY `idx_main_org` (`main_org_id`)');
CALL customer_v2_add_index('CUST_MASTER', 'idx_cust_opened_manager',
    'KEY `idx_cust_opened_manager` (`deleted`, `is_account_opened`, `main_manager_id`, `updated_time`)');
CALL customer_v2_add_index('CUST_MASTER', 'idx_cust_opened_org',
    'KEY `idx_cust_opened_org` (`deleted`, `is_account_opened`, `main_org_id`, `updated_time`)');

-- --------------------------------------------------------------------------
-- 4. New relation and audit tables
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `CUST_LEAD_MANAGER_SCOPE` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `lead_id` varchar(32) NOT NULL COMMENT '线索ID',
  `manager_emp_id` varchar(32) NOT NULL COMMENT '客户经理工号',
  `manager_org_id` varchar(50) NOT NULL COMMENT '客户经理机构代码快照',
  `assignment_type` varchar(20) NOT NULL COMMENT '分配类型：SCOPE/OWNER',
  `is_primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主接收人',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_manager_scope` (`lead_id`, `manager_emp_id`),
  KEY `idx_manager_visible_leads` (`manager_emp_id`, `assignment_type`, `lead_id`),
  KEY `idx_manager_org` (`manager_org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索指定客户经理范围表';

CREATE TABLE IF NOT EXISTS `CUST_LEAD_TAG_REL` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `lead_id` varchar(32) NOT NULL COMMENT '线索ID',
  `tag_id` varchar(32) NOT NULL COMMENT '标签ID',
  `tag_name_snapshot` varchar(100) NOT NULL COMMENT '提交时标签名称快照',
  `created_by` varchar(32) NOT NULL COMMENT '创建人工号',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_tag_rel` (`lead_id`, `tag_id`),
  KEY `idx_lead_tag_tag_id` (`tag_id`, `lead_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索-标签快照关系表';

CREATE TABLE IF NOT EXISTS `CUST_PERFORMANCE_RELATION_SNAPSHOT` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `subject_type` varchar(20) NOT NULL COMMENT '归属主体类型：EMP/ORG',
  `subject_id` varchar(50) NOT NULL COMMENT '归属主体ID',
  `related_emp_id` varchar(32) DEFAULT NULL COMMENT '业绩相关人工号',
  `related_org_id` varchar(50) NOT NULL COMMENT '业绩归属机构代码',
  `relation_type` varchar(30) NOT NULL COMMENT '关系类型：MAIN/CO_MANAGER/ORG_POOL',
  `ratio` decimal(8,4) DEFAULT NULL COMMENT '业绩占比(0~1)',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期',
  `source_system` varchar(32) NOT NULL DEFAULT 'M98' COMMENT '来源系统',
  `source_batch_id` varchar(64) NOT NULL COMMENT '来源批次ID',
  `refreshed_at` datetime NOT NULL COMMENT '快照刷新时间',
  `refresh_status` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '刷新状态：SUCCESS/STALE',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perf_rel_snapshot` (`cust_id`, `subject_type`, `subject_id`, `source_batch_id`),
  KEY `idx_perf_rel_customer` (`cust_id`, `refresh_status`, `effective_date`),
  KEY `idx_perf_rel_subject` (`subject_type`, `subject_id`, `refresh_status`),
  KEY `idx_perf_rel_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户业绩归属快照表';

CREATE TABLE IF NOT EXISTS `CUST_TRANSFER_LOG` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `transfer_no` varchar(64) NOT NULL COMMENT '转交单号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `claim_id` varchar(32) DEFAULT NULL COMMENT '关联认领关系ID',
  `from_manager_id` varchar(32) DEFAULT NULL COMMENT '原主办/维护人工号',
  `from_org_id` varchar(50) DEFAULT NULL COMMENT '原主办/维护机构代码',
  `primary_to_manager_id` varchar(32) NOT NULL COMMENT '新主办客户经理工号',
  `primary_to_org_id` varchar(50) NOT NULL COMMENT '新主办客户经理机构代码',
  `account_opened_snapshot` tinyint(1) NOT NULL COMMENT '转交时是否已开户快照',
  `reason` varchar(500) NOT NULL COMMENT '转交原因',
  `status` varchar(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/COMPLETED/CANCELLED/FAILED',
  `operator_emp_id` varchar(32) NOT NULL COMMENT '操作人工号',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `failure_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  KEY `idx_transfer_customer_time` (`cust_id`, `created_time`),
  KEY `idx_transfer_claim` (`claim_id`),
  KEY `idx_transfer_operator` (`operator_emp_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户转交记录表';

CREATE TABLE IF NOT EXISTS `CUST_TRANSFER_TARGET` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `transfer_id` varchar(32) NOT NULL COMMENT '转交记录ID',
  `target_emp_id` varchar(32) NOT NULL COMMENT '接收客户经理工号',
  `target_org_id` varchar(50) NOT NULL COMMENT '接收客户经理机构代码快照',
  `target_role` varchar(20) NOT NULL COMMENT '接收角色：PRIMARY/CO_MANAGER',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '接收顺序',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_target` (`transfer_id`, `target_emp_id`),
  KEY `idx_transfer_target_emp` (`target_emp_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户转交接收人表';

-- Clean up temporary procedures so the target schema is not polluted.
DROP PROCEDURE IF EXISTS customer_v2_add_column;
DROP PROCEDURE IF EXISTS customer_v2_add_index;
DROP PROCEDURE IF EXISTS customer_v2_drop_index;

-- Verification: expect 13 customer-marketing tables in the V2 baseline and the listed columns/indexes.
SELECT TABLE_NAME
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN (
       'CUST_LEAD_MANAGER_SCOPE', 'CUST_LEAD_TAG_REL',
       'CUST_PERFORMANCE_RELATION_SNAPSHOT', 'CUST_TRANSFER_LOG', 'CUST_TRANSFER_TARGET'
   )
 ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND (
       (TABLE_NAME = 'CUST_LEAD' AND COLUMN_NAME IN ('lead_type', 'distribution_mode', 'submitted_time', 'reject_reason'))
       OR (TABLE_NAME = 'CUST_MASTER' AND COLUMN_NAME IN ('current_lead_id', 'main_manager_id', 'main_org_id', 'last_touch_time'))
       OR (TABLE_NAME = 'LEAD_IMPORT_BATCH' AND COLUMN_NAME IN ('source_file_object_id', 'valid_row_count', 'submitted_time'))
   )
 ORDER BY TABLE_NAME, COLUMN_NAME;
