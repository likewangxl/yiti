-- =====================================================================
-- CUST_ALLOC_RELATION 增加 fullname / dept_no / dept_name 三列（幂等）
-- 背景：原业绩分配反显直接读这三列（不再 UserApi 补全）；
--       审批落地的 NEW 分配 + 手工录入的原业绩分配 均写入这三列快照
-- 部署：yiti 已加，onepl / onepl_test_bootstrap 需补（双库部署红线）
-- =====================================================================

-- fullname
SET @c = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='CUST_ALLOC_RELATION' AND COLUMN_NAME='fullname');
SET @ddl = IF(@c=0,
    "ALTER TABLE CUST_ALLOC_RELATION ADD COLUMN fullname varchar(255) NULL COMMENT '员工姓名' AFTER emp_id",
    "SELECT 'fullname exists'");
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- dept_no
SET @c = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='CUST_ALLOC_RELATION' AND COLUMN_NAME='dept_no');
SET @ddl = IF(@c=0,
    "ALTER TABLE CUST_ALLOC_RELATION ADD COLUMN dept_no varchar(60) NULL COMMENT '部门编号' AFTER fullname",
    "SELECT 'dept_no exists'");
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- dept_name
SET @c = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='CUST_ALLOC_RELATION' AND COLUMN_NAME='dept_name');
SET @ddl = IF(@c=0,
    "ALTER TABLE CUST_ALLOC_RELATION ADD COLUMN dept_name varchar(255) NULL COMMENT '部门名称' AFTER dept_no",
    "SELECT 'dept_name exists'");
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
