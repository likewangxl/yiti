-- 注：UNIQUE KEY 内联在 CREATE TABLE 中（兼容 MySQL 真库 + H2 IF NOT EXISTS 语义）
-- MySQL 不支持独立的 CREATE UNIQUE INDEX IF NOT EXISTS，改成内联 UNIQUE KEY 子句
--
-- ⚠️ V1.12 # 5 注（2026-05-01）：CREATE TABLE IF NOT EXISTS 不更新现有表列。
-- 当本文件后续加新列时（如 V1.6 加 TOUCH_TASK.sla_warning），已存在的旧表（V1.6 之前
-- 创建的实例如 onepl_test_bootstrap）会缺这些列。修复办法：
-- 1. 在 docs/superpowers/sql/ 新建日期前缀脚本含 ALTER TABLE ADD COLUMN（INFORMATION_SCHEMA 兜底）
-- 2. 现役脚本：docs/superpowers/sql/2026-05-01-v1.12-schema-column-drift-fix.sql（修 sla_warning）
-- 3. 测试库实例手工跑该脚本对齐

CREATE TABLE IF NOT EXISTS CUST_TAG (
    id VARCHAR(32) PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL,
    tag_category VARCHAR(50),
    tag_priority INT,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    tag_type VARCHAR(30),
    approval_status VARCHAR(20) DEFAULT 'APPROVED',
    expires_at DATE,
    owner_org_id VARCHAR(50),
    reviewed_by VARCHAR(32),
    reviewed_time DATETIME,
    reject_reason VARCHAR(500),
    created_by VARCHAR(32),
    created_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_cust_tag_name (tag_name)
);

CREATE TABLE IF NOT EXISTS CROSS_ORG_MARKETING_RULE (
    id VARCHAR(32) PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    enabled TINYINT DEFAULT 1,
    data_source VARCHAR(64),
    failure_message VARCHAR(256),
    extension_params VARCHAR(1000),
    sort_no INT,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    UNIQUE KEY uk_cross_rule_code (rule_code)
);

CREATE TABLE IF NOT EXISTS CROSS_ORG_MARKETING_APPLY (
    id VARCHAR(32) PRIMARY KEY,
    apply_no VARCHAR(64) NOT NULL,
    cust_id VARCHAR(32) NOT NULL,
    cust_no VARCHAR(100),
    applicant_emp_id VARCHAR(32) NOT NULL,
    applicant_org_id VARCHAR(50) NOT NULL,
    main_manager_id VARCHAR(32),
    main_org_id VARCHAR(50),
    applicant_not_main_check TINYINT NOT NULL,
    main_org_different_check TINYINT NOT NULL,
    applicant_no_performance_check TINYINT NOT NULL,
    applicant_org_no_performance_check TINYINT NOT NULL,
    check_snapshot_time DATETIME,
    apply_reason VARCHAR(500),
    status VARCHAR(20),
    generated_touch_task_id VARCHAR(32),
    business_key VARCHAR(100),
    process_instance_id VARCHAR(64),
    reviewed_by VARCHAR(32),
    reviewed_time DATETIME,
    reject_reason VARCHAR(500),
    created_by VARCHAR(32),
    created_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    UNIQUE KEY uk_cross_apply_no (apply_no)
);

CREATE TABLE IF NOT EXISTS CUST_PERFORMANCE_RELATION_SNAPSHOT (
    id VARCHAR(32) PRIMARY KEY,
    cust_id VARCHAR(32) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    subject_id VARCHAR(50) NOT NULL,
    related_emp_id VARCHAR(32),
    related_org_id VARCHAR(50) NOT NULL,
    relation_type VARCHAR(30) NOT NULL,
    ratio DECIMAL(8,4),
    effective_date DATE NOT NULL,
    expiry_date DATE,
    source_system VARCHAR(32) NOT NULL DEFAULT 'M98',
    source_batch_id VARCHAR(64) NOT NULL,
    refreshed_at DATETIME NOT NULL,
    refresh_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_perf_rel_snapshot (cust_id, subject_type, subject_id, source_batch_id)
);

CREATE TABLE IF NOT EXISTS CUST_TRANSFER_LOG (
    id VARCHAR(32) PRIMARY KEY,
    transfer_no VARCHAR(64) NOT NULL,
    cust_id VARCHAR(32) NOT NULL,
    claim_id VARCHAR(32),
    from_manager_id VARCHAR(32),
    from_org_id VARCHAR(50),
    primary_to_manager_id VARCHAR(32) NOT NULL,
    primary_to_org_id VARCHAR(50) NOT NULL,
    account_opened_snapshot TINYINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    operator_emp_id VARCHAR(32) NOT NULL,
    completed_time DATETIME,
    failure_reason VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_transfer_no (transfer_no)
);

CREATE TABLE IF NOT EXISTS CUST_TRANSFER_TARGET (
    id VARCHAR(32) PRIMARY KEY,
    transfer_id VARCHAR(32) NOT NULL,
    target_emp_id VARCHAR(32) NOT NULL,
    target_org_id VARCHAR(50) NOT NULL,
    target_role VARCHAR(20) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_transfer_target (transfer_id, target_emp_id)
);

CREATE TABLE IF NOT EXISTS CUST_TAG_REL (
    id VARCHAR(32) PRIMARY KEY,
    cust_id VARCHAR(32) NOT NULL,
    tag_id VARCHAR(32) NOT NULL,
    created_by VARCHAR(32),
    created_time DATETIME,
    active TINYINT DEFAULT 1,
    effective_time DATETIME,
    expired_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    UNIQUE KEY uk_cust_tag_rel (cust_id, tag_id)
);

CREATE TABLE IF NOT EXISTS CUST_LEAD (
    id VARCHAR(32) PRIMARY KEY,
    lead_no VARCHAR(64) NOT NULL,
    lead_op VARCHAR(20) NOT NULL,
    lead_type VARCHAR(30) NOT NULL DEFAULT 'NEW_ACCOUNT',
    source_cust_id VARCHAR(32),
    prev_lead_id VARCHAR(32),
    version_no INT NOT NULL,
    is_latest TINYINT NOT NULL,
    cust_no VARCHAR(100),
    cust_name VARCHAR(200) NOT NULL,
    unified_credit_code VARCHAR(50),
    tag_ids VARCHAR(1000),
    contact_person VARCHAR(100),
    contact_mobile VARCHAR(50),
    industry VARCHAR(50),
    group_type VARCHAR(50),
    customer_type VARCHAR(50),
    is_keystone TINYINT,
    enterprise_type VARCHAR(50),
    group_name VARCHAR(200),
    is_account_opened TINYINT,
    customer_desc TEXT,
    credit_amount DECIMAL(18, 2),
    credit_exposure_amount DECIMAL(18, 2),
    lead_source VARCHAR(50),
    distribution_mode VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    main_manager_id VARCHAR(32),
    main_manager_org_id VARCHAR(50),
    lead_status VARCHAR(20) NOT NULL,
    owner_org_id VARCHAR(32),
    assigned_to VARCHAR(32),
    created_by VARCHAR(32),
    submitted_by VARCHAR(32),
    submitted_time DATETIME,
    business_key VARCHAR(100),
    import_batch_id VARCHAR(32),
    batch_row_no INT,
    process_instance_id VARCHAR(64),
    reviewed_by VARCHAR(32),
    reviewed_time DATETIME,
    reject_reason VARCHAR(500),
    remark VARCHAR(500),
    created_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0,
    lock_version INT NOT NULL DEFAULT 0,
    active_new_credit_code VARCHAR(50) AS
        (CASE WHEN lead_type = 'NEW_ACCOUNT' AND is_latest = 1 AND deleted = 0
              THEN unified_credit_code ELSE NULL END),
    UNIQUE KEY uk_cust_lead_no (lead_no),
    UNIQUE KEY uk_cust_lead_credit (active_new_credit_code)
);

CREATE TABLE IF NOT EXISTS CUST_LEAD_MANAGER_SCOPE (
    id VARCHAR(32) PRIMARY KEY,
    lead_id VARCHAR(32) NOT NULL,
    manager_emp_id VARCHAR(32) NOT NULL,
    manager_org_id VARCHAR(50) NOT NULL,
    assignment_type VARCHAR(20) NOT NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    created_by VARCHAR(32) NOT NULL,
    created_time DATETIME NOT NULL,
    UNIQUE KEY uk_lead_manager_scope (lead_id, manager_emp_id)
);

CREATE TABLE IF NOT EXISTS CUST_LEAD_TAG_REL (
    id VARCHAR(32) PRIMARY KEY,
    lead_id VARCHAR(32) NOT NULL,
    tag_id VARCHAR(32) NOT NULL,
    tag_name_snapshot VARCHAR(100) NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    created_time DATETIME NOT NULL,
    UNIQUE KEY uk_lead_tag_rel (lead_id, tag_id)
);

CREATE TABLE IF NOT EXISTS LEAD_IMPORT_BATCH (
    id VARCHAR(32) PRIMARY KEY,
    source_file_name VARCHAR(255),
    total_count INT,
    success_count INT,
    fail_count INT,
    status VARCHAR(20),
    business_key VARCHAR(100),
    created_by VARCHAR(32),
    created_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0
);

-- M98 存量客户主档：仅用于 T-1 同步/客户号反显，和客户营销主档隔离。
CREATE TABLE IF NOT EXISTS CUST_MASTER (
    id VARCHAR(32) PRIMARY KEY,
    cust_no VARCHAR(64),
    cust_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deleted TINYINT DEFAULT 0,
    statis_dt VARCHAR(10),
    created_time DATETIME,
    updated_time DATETIME,
    UNIQUE KEY uk_m98_cust_master_no (cust_no)
);

CREATE TABLE IF NOT EXISTS CUSTOMER_MARKET_CUSTOMER (
    id VARCHAR(32) PRIMARY KEY,
    cust_no VARCHAR(64),
    cust_name VARCHAR(200) NOT NULL,
    unified_credit_code VARCHAR(50),
    contact_person VARCHAR(100),
    contact_mobile VARCHAR(50),
    industry VARCHAR(50),
    group_type VARCHAR(50),
    customer_type VARCHAR(50),
    is_keystone TINYINT,
    enterprise_type VARCHAR(50),
    group_name VARCHAR(200),
    is_account_opened TINYINT,
    customer_desc TEXT,
    credit_amount DECIMAL(18, 2),
    credit_exposure_amount DECIMAL(18, 2),
    owner_org_id VARCHAR(32),
    lead_id VARCHAR(32),
    current_lead_id VARCHAR(32),
    main_manager_id VARCHAR(32),
    main_org_id VARCHAR(50),
    ownership_status VARCHAR(30) NOT NULL DEFAULT 'UNASSIGNED',
    last_touch_time DATETIME,
    source_system VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    source_updated_time DATETIME,
    status VARCHAR(20) NOT NULL,
    deleted TINYINT DEFAULT 0,
    created_time DATETIME,
    updated_time DATETIME,
    lock_version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_customer_market_cust_no (cust_no),
    UNIQUE KEY uk_customer_market_credit_code (unified_credit_code)
);

CREATE TABLE IF NOT EXISTS CUST_CLAIM (
    id VARCHAR(32) PRIMARY KEY,
    cust_id VARCHAR(32) NOT NULL,
    org_id VARCHAR(32) NOT NULL,
    claimed_by VARCHAR(32),
    maintainer_emp_id VARCHAR(32),
    claim_status VARCHAR(20) NOT NULL,
    claim_time DATETIME,
    cancel_time DATETIME,
    cancel_reason VARCHAR(500),
    created_time DATETIME,
    updated_time DATETIME,
    UNIQUE KEY uk_cust_claim_emp (cust_id, claimed_by)
);

CREATE TABLE IF NOT EXISTS TOUCH_TASK (
    id VARCHAR(32) PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    cust_id VARCHAR(32) NOT NULL,
    org_id VARCHAR(32),
    assignee_emp_id VARCHAR(32),
    task_type VARCHAR(20) NOT NULL,
    task_status VARCHAR(20) NOT NULL,
    plan_finish_time DATETIME,
    warning_time DATETIME,
    sla_status VARCHAR(20),
    sla_warning TINYINT DEFAULT 0,
    business_key VARCHAR(100),
    success_time DATETIME,
    cancel_time DATETIME,
    created_time DATETIME,
    updated_time DATETIME,
    UNIQUE KEY uk_touch_task_no (task_no)
);

CREATE TABLE IF NOT EXISTS TOUCH_LOG (
    id VARCHAR(32) PRIMARY KEY,
    touch_task_id VARCHAR(32) NOT NULL,
    log_time DATETIME,
    client_uuid VARCHAR(64) NOT NULL,
    log_content VARCHAR(2000),
    touch_method VARCHAR(30),
    participant_emp_ids VARCHAR(2000),
    photo_urls VARCHAR(2000),
    photo_groups VARCHAR(4000),
    operator_location VARCHAR(1000),
    owner_org_id VARCHAR(32),
    created_by VARCHAR(32),
    created_time DATETIME,
    UNIQUE KEY uk_touch_log_client (touch_task_id, client_uuid)
);
