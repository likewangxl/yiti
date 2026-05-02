-- loan_apply (H2 MySQL 兼容模式)
CREATE TABLE IF NOT EXISTS LOAN_APPLY (
  id                     VARCHAR(32)    NOT NULL,
  apply_no               VARCHAR(100)   DEFAULT NULL,
  cust_id                VARCHAR(32)    NOT NULL,
  source_touch_task_id   VARCHAR(32)    DEFAULT NULL,
  project_type           VARCHAR(32)    DEFAULT NULL,
  biz_type               VARCHAR(32)    DEFAULT NULL,
  guarantee_type         VARCHAR(32)    DEFAULT NULL,
  credit_amount          DECIMAL(20,4)  DEFAULT NULL,
  credit_exposure_amount DECIMAL(20,4)  DEFAULT NULL,
  status                 VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
  business_key           VARCHAR(100)   DEFAULT NULL,
  process_instance_id    VARCHAR(64)    DEFAULT NULL,
  owner_org_id           VARCHAR(50)    NOT NULL,
  created_by             VARCHAR(32)    NOT NULL,
  created_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  updated_by             VARCHAR(32)    DEFAULT NULL,
  updated_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  deleted                INT            NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- support_request (H2 MySQL 兼容模式)
CREATE TABLE IF NOT EXISTS SUPPORT_REQUEST (
  id                     VARCHAR(32)    NOT NULL,
  request_no             VARCHAR(100)   DEFAULT NULL,
  submit_group_id        VARCHAR(64)    DEFAULT NULL,
  cust_id                VARCHAR(32)    NOT NULL,
  source_touch_task_id   VARCHAR(32)    DEFAULT NULL,
  product_id             VARCHAR(64)    DEFAULT NULL,
  support_dept_id        VARCHAR(50)    DEFAULT NULL,
  other_demand           CLOB           DEFAULT NULL,
  dispatch_emp_id        VARCHAR(32)    DEFAULT NULL,
  dispatch_time          TIMESTAMP      DEFAULT NULL,
  assigned_emp_id        VARCHAR(32)    DEFAULT NULL,
  status                 VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
  business_key           VARCHAR(100)   DEFAULT NULL,
  process_instance_id    VARCHAR(64)    DEFAULT NULL,
  owner_org_id           VARCHAR(50)    NOT NULL,
  created_by             VARCHAR(32)    NOT NULL,
  created_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  updated_by             VARCHAR(32)    DEFAULT NULL,
  updated_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  deleted                INT            NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
