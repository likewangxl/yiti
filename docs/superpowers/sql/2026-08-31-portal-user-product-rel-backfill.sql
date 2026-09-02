-- 通讯录用户-产品负责关系历史数据回填候选脚本
-- 目标库: yiti
-- 适用: MySQL 8.0+ (JSON_TABLE)
-- 状态: 待 DBA 评审，本脚本未由 Codex 执行
-- 前提: 已建立大写表 PORTAL_USER_PRODUCT_REL；Linux MySQL lower_case_table_names=0。

USE yiti;

-- 1. 执行回填前必须核对两侧旧 JSON。下面两个 *_only 必须都为 0。
WITH product_rel AS (
    SELECT DISTINCT
           CONVERT(p.ID USING utf8mb4) COLLATE utf8mb4_general_ci AS PRODUCT_ID,
           CONVERT(j.USER_ID USING utf8mb4) COLLATE utf8mb4_general_ci AS USER_ID
      FROM PRODUCT_INFO p
      JOIN JSON_TABLE(
             CASE WHEN JSON_VALID(p.RESPONSIBLE_EMP_IDS)
                  THEN p.RESPONSIBLE_EMP_IDS ELSE JSON_ARRAY() END,
             '$[*]' COLUMNS (USER_ID VARCHAR(50) PATH '$')
           ) j
     WHERE j.USER_ID IS NOT NULL AND j.USER_ID <> ''
),
employee_rel AS (
    SELECT DISTINCT
           CONVERT(j.PRODUCT_ID USING utf8mb4) COLLATE utf8mb4_general_ci AS PRODUCT_ID,
           CONVERT(e.EMP_ID USING utf8mb4) COLLATE utf8mb4_general_ci AS USER_ID
      FROM ADDRBOOK_EMPLOYEE e
      JOIN JSON_TABLE(
             CASE WHEN JSON_VALID(e.RESPONSIBLE_PRODUCT_IDS)
                  THEN e.RESPONSIBLE_PRODUCT_IDS ELSE JSON_ARRAY() END,
             '$[*]' COLUMNS (PRODUCT_ID VARCHAR(64) PATH '$')
           ) j
     WHERE j.PRODUCT_ID IS NOT NULL AND j.PRODUCT_ID <> ''
)
SELECT 'product_pairs' AS METRIC, COUNT(*) AS CNT FROM product_rel
UNION ALL
SELECT 'employee_pairs', COUNT(*) FROM employee_rel
UNION ALL
SELECT 'product_only', COUNT(*)
  FROM product_rel p
  LEFT JOIN employee_rel e
    ON e.PRODUCT_ID = p.PRODUCT_ID AND e.USER_ID = p.USER_ID
 WHERE e.USER_ID IS NULL
UNION ALL
SELECT 'employee_only', COUNT(*)
  FROM employee_rel e
  LEFT JOIN product_rel p
    ON p.PRODUCT_ID = e.PRODUCT_ID AND p.USER_ID = e.USER_ID
 WHERE p.USER_ID IS NULL;

-- 2. 确认两侧一致后再执行本事务。以 PRODUCT_INFO 旧 JSON 为单一回填源，
--    并仅接收现存、启用的 PT_USER 与未删除产品。INSERT IGNORE 保证重跑幂等。
START TRANSACTION;

INSERT IGNORE INTO PORTAL_USER_PRODUCT_REL
    (USER_ID, PRODUCT_ID, ASSIGNED_TIME, UPDATED_TIME, UPDATED_BY)
SELECT DISTINCT
       u.USER_ID,
       p.ID,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       'MIGRATION_20260831'
  FROM PRODUCT_INFO p
  JOIN JSON_TABLE(
         CASE WHEN JSON_VALID(p.RESPONSIBLE_EMP_IDS)
              THEN p.RESPONSIBLE_EMP_IDS ELSE JSON_ARRAY() END,
         '$[*]' COLUMNS (USER_ID VARCHAR(50) PATH '$')
       ) j
  JOIN PT_USER u
    ON CONVERT(u.USER_ID USING utf8mb4) COLLATE utf8mb4_general_ci
     = CONVERT(j.USER_ID USING utf8mb4) COLLATE utf8mb4_general_ci
 WHERE j.USER_ID IS NOT NULL
   AND j.USER_ID <> ''
   AND p.DELETED = 0
   AND u.ISENABLED = 0;

-- 事务内验证：回填行数应与通过有效用户/产品过滤后的源关系数一致。
SELECT COUNT(*) AS RELATION_ROWS FROM PORTAL_USER_PRODUCT_REL;

-- DBA 核对无误后改为 COMMIT；首次演练建议保持 ROLLBACK。
ROLLBACK;
-- COMMIT;

-- 3. 上线验收完成前不删除 ADDRBOOK_EMPLOYEE，不删除两侧旧 JSON 字段。
--    物理清理应单独发布，并保留备份与回滚方案。
