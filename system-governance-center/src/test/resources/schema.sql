-- ============================================================
-- H2 Schema for Governance Tables (system-governance-center)
-- Based on create-table.sql DDL, converted for H2 compatibility
-- ============================================================

-- 字典表
CREATE TABLE IF NOT EXISTS sys_dict (
    id VARCHAR(32) PRIMARY KEY,
    dict_type VARCHAR(100) NOT NULL,
    dict_code VARCHAR(100) NOT NULL,
    dict_label VARCHAR(200) NOT NULL,
    dict_value VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    remark VARCHAR(500) DEFAULT NULL,
    created_by VARCHAR(32) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(32) DEFAULT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 系统配置KV表
CREATE TABLE IF NOT EXISTS sys_config_kv (
    id VARCHAR(32) PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    value_type VARCHAR(20) DEFAULT 'STRING',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    remark VARCHAR(500) DEFAULT NULL,
    created_by VARCHAR(32) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(32) DEFAULT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 工作日历表
CREATE TABLE IF NOT EXISTS sys_calendar_day (
    day DATE PRIMARY KEY,
    is_workday INT DEFAULT 1,
    remark VARCHAR(200) DEFAULT NULL,
    created_by VARCHAR(32) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(32) DEFAULT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(32) PRIMARY KEY,
    trace_id VARCHAR(64) DEFAULT NULL,
    emp_id VARCHAR(32) NOT NULL,
    emp_name VARCHAR(100) DEFAULT NULL,
    biz_type VARCHAR(50) DEFAULT NULL,
    biz_action VARCHAR(50) DEFAULT NULL,
    resource_url VARCHAR(500) DEFAULT NULL,
    request_method VARCHAR(20) DEFAULT NULL,
    request_params TEXT,
    response_status INT DEFAULT NULL,
    error_msg TEXT,
    ip_address VARCHAR(50) DEFAULT NULL,
    user_agent VARCHAR(500) DEFAULT NULL,
    execution_time INT DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 用户通知表
CREATE TABLE IF NOT EXISTS user_notification (
    id VARCHAR(32) PRIMARY KEY,
    emp_id VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    notify_type VARCHAR(50) DEFAULT NULL,
    biz_type VARCHAR(50) DEFAULT NULL,
    biz_id VARCHAR(100) DEFAULT NULL,
    link_url VARCHAR(500) DEFAULT NULL,
    is_read TINYINT(1) DEFAULT 0,
    read_time DATETIME DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 文件对象表
CREATE TABLE IF NOT EXISTS file_object (
    id VARCHAR(32) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT DEFAULT NULL,
    file_type VARCHAR(100) DEFAULT NULL,
    storage_path VARCHAR(500) NOT NULL,
    bucket_name VARCHAR(100) DEFAULT NULL,
    md5_hash VARCHAR(64) DEFAULT NULL,
    uploaded_by VARCHAR(32) DEFAULT NULL,
    uploaded_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 业务-附件关联表
CREATE TABLE IF NOT EXISTS biz_file_rel (
    id VARCHAR(32) PRIMARY KEY,
    biz_type VARCHAR(32),
    biz_id VARCHAR(100),
    file_object_id VARCHAR(32),
    file_role VARCHAR(32),
    created_by VARCHAR(32),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 任务配置表
CREATE TABLE IF NOT EXISTS sys_job_conf (
    id VARCHAR(32) PRIMARY KEY,
    job_key VARCHAR(100) NOT NULL UNIQUE,
    job_name VARCHAR(100),
    cron_expr VARCHAR(64),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    allow_manual_trigger TINYINT(1) DEFAULT 1,
    last_run_time DATETIME DEFAULT NULL,
    next_run_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    created_by VARCHAR(32) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(32) DEFAULT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 任务执行日志表
CREATE TABLE IF NOT EXISTS sys_job_run_log (
    id VARCHAR(32) PRIMARY KEY,
    job_id VARCHAR(32),
    trigger_type VARCHAR(20) DEFAULT 'SCHEDULED',
    reason VARCHAR(500) DEFAULT NULL,
    start_time DATETIME,
    end_time DATETIME,
    status VARCHAR(20) DEFAULT 'RUNNING',
    error_msg TEXT,
    created_by VARCHAR(32) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
