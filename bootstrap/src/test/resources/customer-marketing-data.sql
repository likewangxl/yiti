DELETE FROM MARKETING_ASSET_PROJECT_URGENT_APPLY;
DELETE FROM MARKETING_ASSET_PROJECT_APPLY;
DELETE FROM MARKETING_TOUCH_WORKLOG_PARTICIPANT;
DELETE FROM MARKETING_TOUCH_WORKLOG_PICTURE;
DELETE FROM MARKETING_TOUCH_WORKLOG;
DELETE FROM MARKETING_TOUCH_TASK;
DELETE FROM CUST_CLAIM;
DELETE FROM CUST_TAG_REL;
DELETE FROM CUST_LEAD_TAG_REL;
DELETE FROM CUST_LEAD_MANAGER_SCOPE;
DELETE FROM CUST_LEAD;
DELETE FROM CUST_TAG;
DELETE FROM LEAD_IMPORT_BATCH;
DELETE FROM CUSTOMER_MARKET_CUSTOMER;
DELETE FROM MARKETING_CUSTOMER_INFO;
DELETE FROM CUST_MASTER;

INSERT INTO CUSTOMER_MARKET_CUSTOMER (
    id, cust_no, cust_name, unified_credit_code, contact_person, contact_mobile,
    industry, group_type, customer_type, is_keystone, enterprise_type, group_name,
    is_account_opened, customer_desc, credit_amount, credit_exposure_amount,
    owner_org_id, lead_id, status, deleted, created_time, updated_time
) VALUES (
    '1001', 'CUST_SEED_001', '种子客户A', '91310000SEED0001', '李四', '13900000001',
    'IT', 'GROUP', 'ENTERPRISE', 1, 'PRIVATE', 'Seed Group',
    1, 'bootstrap customer seed', 500000.00, 200000.00,
    'BJ_CY', NULL, 'ACTIVE', 0, NOW(), NOW()
);

INSERT INTO MARKETING_CUSTOMER_INFO (
    id, cust_no, cust_name, unified_credit_code, contact_person, contact_mobile,
    industry, group_type, group_name, customer_type, enterprise_type, is_keystone,
    customer_desc, is_account_opened, credit_amount, credit_exposure_amount,
    touch_restricted, main_manager_id, main_org_id, ownership_status,
    ownership_source, ownership_maintain_mode, profile_version, record_status,
    created_by, created_time, updated_by, updated_time, lock_version
) VALUES (
    1001, 'CUST_SEED_001', '种子客户A', '91310000SEED0001', '李四', '13900000001',
    'IT', 'GROUP', 'Seed Group', 'ENTERPRISE', 'PRIVATE', 1,
    'bootstrap customer seed', 1, 500000.00, 200000.00,
    0, 'user001', 'BJ_CY', 'ASSIGNED',
    'LOCAL', 'AUTO', 0, 'ACTIVE',
    'SYSTEM', NOW(), 'SYSTEM', NOW(), 0
);

INSERT INTO CUST_MASTER (
    id, cust_no, cust_name, status, deleted, statis_dt, created_time, updated_time
) VALUES (
    'm98-seed-001', 'M98_SEED_001', 'M98种子客户', 'ACTIVE', 0, CURRENT_DATE, NOW(), NOW()
);
