DELETE FROM touch_log;
DELETE FROM touch_task;
DELETE FROM cust_claim;
DELETE FROM cust_tag_rel;
DELETE FROM cust_lead;
DELETE FROM cust_tag;
DELETE FROM lead_import_batch;
DELETE FROM cust_master;

INSERT INTO cust_master (
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
