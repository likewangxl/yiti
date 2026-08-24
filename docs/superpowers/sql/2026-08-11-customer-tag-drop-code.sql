-- 客户标签以 id 作为唯一标识，所有关系表均通过 tag_id 关联。
-- tag_code 不再对外暴露，也不再作为业务引用键，删除该冗余列及其单列唯一索引。
SET @has_tag_code := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND UPPER(TABLE_NAME) = 'CUST_TAG'
    AND LOWER(COLUMN_NAME) = 'tag_code'
);

SET @drop_tag_code_sql := IF(
  @has_tag_code > 0,
  'ALTER TABLE CUST_TAG DROP COLUMN tag_code',
  'SELECT ''CUST_TAG.tag_code already absent'' AS result'
);

PREPARE drop_tag_code_stmt FROM @drop_tag_code_sql;
EXECUTE drop_tag_code_stmt;
DEALLOCATE PREPARE drop_tag_code_stmt;
