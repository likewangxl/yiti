-- 注：UNIQUE KEY 内联在 CREATE TABLE 中（兼容 MySQL 真库 + H2 IF NOT EXISTS 语义）
-- MySQL 不支持独立的 CREATE UNIQUE INDEX IF NOT EXISTS，改成内联 UNIQUE KEY 子句

CREATE TABLE IF NOT EXISTS cust_tag (
    id VARCHAR(32) PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL,
    tag_code VARCHAR(100) NOT NULL,
    tag_category VARCHAR(50),
    tag_priority INT,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(32),
    created_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_cust_tag_name (tag_name),
    UNIQUE KEY uk_cust_tag_code (tag_code)
);

CREATE TABLE IF NOT EXISTS cust_tag_rel (
    id VARCHAR(32) PRIMARY KEY,
    cust_id VARCHAR(32) NOT NULL,
    tag_id VARCHAR(32) NOT NULL,
    created_by VARCHAR(32),
    created_time DATETIME,
    UNIQUE KEY uk_cust_tag_rel (cust_id, tag_id)
);

CREATE TABLE IF NOT EXISTS cust_lead (
    id VARCHAR(32) PRIMARY KEY,
    lead_no VARCHAR(64) NOT NULL,
    lead_op VARCHAR(20) NOT NULL,
    source_cust_id VARCHAR(32),
    prev_lead_id VARCHAR(32),
    version_no INT NOT NULL,
    is_latest TINYINT NOT NULL,
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
    lead_status VARCHAR(20) NOT NULL,
    owner_org_id VARCHAR(32),
    assigned_to VARCHAR(32),
    created_by VARCHAR(32),
    business_key VARCHAR(100),
    import_batch_id VARCHAR(32),
    process_instance_id VARCHAR(64),
    remark VARCHAR(500),
    created_time DATETIME,
    updated_by VARCHAR(32),
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_cust_lead_no (lead_no)
);

CREATE TABLE IF NOT EXISTS lead_import_batch (
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

CREATE TABLE IF NOT EXISTS cust_master (
    id VARCHAR(32) PRIMARY KEY,
    cust_no VARCHAR(64) NOT NULL,
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
    status VARCHAR(20) NOT NULL,
    deleted TINYINT DEFAULT 0,
    created_time DATETIME,
    updated_time DATETIME,
    UNIQUE KEY uk_cust_master_no (cust_no)
);

CREATE TABLE IF NOT EXISTS cust_claim (
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
    UNIQUE KEY uk_cust_claim_org (cust_id, org_id)
);

CREATE TABLE IF NOT EXISTS touch_task (
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

CREATE TABLE IF NOT EXISTS touch_log (
    id VARCHAR(32) PRIMARY KEY,
    touch_task_id VARCHAR(32) NOT NULL,
    log_time DATETIME,
    client_uuid VARCHAR(64) NOT NULL,
    log_content VARCHAR(2000),
    photo_urls VARCHAR(2000),
    owner_org_id VARCHAR(32),
    created_by VARCHAR(32),
    created_time DATETIME,
    UNIQUE KEY uk_touch_log_client (touch_task_id, client_uuid)
);
