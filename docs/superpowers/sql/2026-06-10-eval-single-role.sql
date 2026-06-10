-- 评价体系单一角色化迁移（2026-06-10）
-- 标签即角色：EVAL_USER_TAG 收敛为每人至多 1 行、删除 ROLE_TYPE 列。
-- 执行前务必先 mysqldump 备份 EVAL_USER_TAG。
--   mysqldump -uroot -p<db> EVAL_USER_TAG > 2026-06-10-eval-user-tag-backup.sql
-- 需在三个库执行：yiti(开发) / onepl(生产基线) / onepl_test_bootstrap(测试)。

-- ⚠ 预检 1：当前每人标签行数分布（收敛前可能 >1）
--   SELECT user_id, COUNT(*) c FROM EVAL_USER_TAG GROUP BY user_id HAVING c>1;

-- 1. 收敛为单标签：每人保留 role_type 升序(1 被评价优先)、id 升序的第一行，删其余
DELETE u FROM EVAL_USER_TAG u
JOIN (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY role_type ASC, id ASC) AS rn
  FROM EVAL_USER_TAG
) ranked ON ranked.id = u.id
WHERE ranked.rn > 1;

-- ⚠ 预检 2：收敛后应返回 0 行，再继续 DDL
--   SELECT user_id, COUNT(*) c FROM EVAL_USER_TAG GROUP BY user_id HAVING c>1;

-- 2. 唯一键改为按人唯一（每人一标签）
ALTER TABLE EVAL_USER_TAG DROP KEY UK_USER_TAG_ROLE;
ALTER TABLE EVAL_USER_TAG ADD UNIQUE KEY UK_USER (USER_ID);

-- 3. 删除角色列
ALTER TABLE EVAL_USER_TAG DROP COLUMN ROLE_TYPE;
