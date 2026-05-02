-- ============================================================
-- H2 Schema for Workflow Tables (workflow-center)
-- Based on create-table.sql DDL, converted for H2 compatibility
-- ============================================================

-- 业务流程映射表
CREATE TABLE IF NOT EXISTS BIZ_PROCESS_MAP (
    id VARCHAR(32) PRIMARY KEY,
    business_key VARCHAR(100) NOT NULL UNIQUE,
    biz_type VARCHAR(50) NOT NULL,
    biz_id VARCHAR(100) NOT NULL,
    process_definition_key VARCHAR(100) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL UNIQUE,
    start_user VARCHAR(32) NOT NULL,
    current_assignee VARCHAR(32) DEFAULT NULL,
    candidate_groups TEXT,
    process_status VARCHAR(50) DEFAULT 'RUNNING',
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 流程节点候选人配置表
CREATE TABLE IF NOT EXISTS WF_NODE_CANDIDATE_CONF (
    id VARCHAR(32) PRIMARY KEY,
    process_definition_key VARCHAR(100) NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    candidate_type VARCHAR(50) NOT NULL,
    candidate_value TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 流程节点表单配置表
CREATE TABLE IF NOT EXISTS WF_NODE_FORM_CONF (
    id VARCHAR(32) PRIMARY KEY,
    process_definition_key VARCHAR(100) NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    form_fields TEXT,
    editable_fields TEXT,
    required_fields TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 流程超时规则表
CREATE TABLE IF NOT EXISTS WF_TIMEOUT_RULE (
    id VARCHAR(32) PRIMARY KEY,
    process_definition_key VARCHAR(100) NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    timeout_hours INT NOT NULL,
    warning_hours INT DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
