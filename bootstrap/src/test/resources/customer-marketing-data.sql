DELETE FROM TOUCH_LOG;
DELETE FROM TOUCH_TASK;
DELETE FROM CUST_CLAIM;
DELETE FROM CUST_TAG_REL;
DELETE FROM CUST_LEAD;
DELETE FROM CUST_TAG;
DELETE FROM LEAD_IMPORT_BATCH;
DELETE FROM CUST_MASTER;

INSERT INTO CUST_MASTER (
    id, cust_no, cust_name, unified_credit_code, contact_person, contact_mobile,
    industry, group_type, customer_type, is_keystone, enterprise_type, group_name,
    is_account_opened, customer_desc, credit_amount, credit_exposure_amount,
    owner_org_id, lead_id, status, deleted, created_time, updated_time
) VALUES (
    'cust-seed-001', 'CUST_SEED_001', '种子客户A', '91310000SEED0001', '李四', '13900000001',
    'IT', 'GROUP', 'ENTERPRISE', 1, 'PRIVATE', 'Seed Group',
    1, 'bootstrap customer seed', 500000.00, 200000.00,
    'BJ_CY', NULL, 'ACTIVE', 0, NOW(), NOW()
);
