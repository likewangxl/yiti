DELETE FROM SUPPORT_REQUEST;

INSERT INTO SUPPORT_REQUEST (
  id, request_no, submit_group_id, cust_id, source_touch_task_id, product_id, support_dept_id,
  other_demand, dispatch_emp_id, dispatch_time, assigned_emp_id, status, business_key,
  process_instance_id, owner_org_id, created_by, created_time, updated_by, updated_time, deleted
) VALUES (
  'support-seed-001', 'SR_SEED_001', 'GROUP_SEED_001', 'CUST_BIZ_SEED', NULL, 'PROD_SEED_001', 'DEPT_SEED_001',
  NULL, 'user001', CURRENT_TIMESTAMP, 'user002', 'IN_PROGRESS', 'SUPPORT:support-seed-001',
  'PI_SUPPORT_SEED_001', 'BJ_CY', 'user001', CURRENT_TIMESTAMP, 'user001', CURRENT_TIMESTAMP, 0
);
