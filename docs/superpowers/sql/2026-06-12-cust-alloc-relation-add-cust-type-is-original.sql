-- =====================================================================
-- CUST_ALLOC_RELATION 增加 cust_type / is_original 两列（幂等）
-- 背景：分配审批通过落地时，先按 cust_id+cust_type+alloc_dim+account_no 命中存量分配
--       置 is_original='1'，新分配以 is_original='2'（当前生效）+ cust_type 入库
-- 部署：dev 连 yiti（已加），onepl / onepl_test_bootstrap 需补（双库部署红线）
-- 幂等：INFORMATION_SCHEMA 预检，列已存在则跳过
-- =====================================================================

-- cust_type
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'CUST_ALLOC_RELATION'
                     AND COLUMN_NAME = 'cust_type');
SET @ddl = IF(@col_exists = 0,
    "ALTER TABLE CUST_ALLOC_RELATION ADD COLUMN cust_type varchar(20) NULL COMMENT '客户类型 CORP/RETAIL' AFTER account_no",
    "SELECT 'cust_type already exists' AS msg");
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- is_original
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'CUST_ALLOC_RELATION'
                     AND COLUMN_NAME = 'is_original');
SET @ddl = IF(@col_exists = 0,
    "ALTER TABLE CUST_ALLOC_RELATION ADD COLUMN is_original varchar(2) NULL COMMENT '是否是原分配关系,1,是,2,否' AFTER cust_type",
    "SELECT 'is_original already exists' AS msg");
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
