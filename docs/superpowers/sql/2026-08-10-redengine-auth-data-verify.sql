-- ============================================================================
-- 红色引擎 2026-08-10 对齐脚本：只读验收 SQL
--
-- 全文件只有 SELECT/CTE，不创建对象、不写表。每个结果集 CHECK_STATUS 均应为 PASS，
-- 差异查询应只返回一行 PASS；否则不要发布并保留原始结果给开发/DBA 排查。
-- 权威资源契约：docs/superpowers/sql/2026-07-18-redengine-seed.sql
-- ============================================================================

-- 0) 四个党建 ROLE_CODE 必须各自恰好一行且处于启用状态；显式拦截 ROLE_CODE 无唯一键导致的重复。
WITH expected_role AS (
    SELECT 'R_RE_REPORT' AS ROLE_CODE
    UNION ALL SELECT 'R_RE_SECR'
    UNION ALL SELECT 'R_RE_BRREV'
    UNION ALL SELECT 'R_RE_ORGREV'
),
role_cardinality AS (
    SELECT e.ROLE_CODE,
           COUNT(r.ROLE_ID) AS TOTAL_COUNT,
           SUM(CASE WHEN r.RECORD_STATUS = 0 THEN 1 ELSE 0 END) AS ENABLED_COUNT
      FROM expected_role e
      LEFT JOIN PT_ROLE r ON r.ROLE_CODE = e.ROLE_CODE
     GROUP BY e.ROLE_CODE
),
role_diff AS (
    SELECT ROLE_CODE, TOTAL_COUNT, ENABLED_COUNT
      FROM role_cardinality
     WHERE TOTAL_COUNT <> 1 OR ENABLED_COUNT <> 1
)
SELECT 'RED_ROLE_CODE_CARDINALITY' AS CHECK_ITEM,
       'PASS' AS CHECK_STATUS,
       'four role codes are unique and enabled' AS DETAIL
 WHERE NOT EXISTS (SELECT 1 FROM role_diff)
UNION ALL
SELECT 'RED_ROLE_CODE_CARDINALITY', 'FAIL',
       CONCAT(ROLE_CODE, ':total=', TOTAL_COUNT, ',enabled=', ENABLED_COUNT)
  FROM role_diff;

-- 1) 共享上传资源自身必须唯一命中权威契约：启用、POST /api/files/upload。
SELECT 'G_FILE_UPLOAD_RESOURCE_CONTRACT' AS CHECK_ITEM,
       CASE WHEN COUNT(*) = 1
                  AND COALESCE(SUM(CASE
                      WHEN CONVERT(RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_bin
                             = CONVERT('/api/files/upload' USING utf8mb4) COLLATE utf8mb4_bin
                       AND CONVERT(RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_bin
                             = CONVERT('POST' USING utf8mb4) COLLATE utf8mb4_bin
                       AND STATUS = 0
                      THEN 1 ELSE 0 END), 0) = 1
            THEN 'PASS' ELSE 'FAIL' END AS CHECK_STATUS,
       CONCAT('rows=', COUNT(*), ', exact_enabled_contract=',
              COALESCE(SUM(CASE
                  WHEN CONVERT(RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_bin
                         = CONVERT('/api/files/upload' USING utf8mb4) COLLATE utf8mb4_bin
                   AND CONVERT(RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_bin
                         = CONVERT('POST' USING utf8mb4) COLLATE utf8mb4_bin
                   AND STATUS = 0
                  THEN 1 ELSE 0 END), 0)) AS DETAIL
  FROM PT_RESOURCE
 WHERE RESOURCE_ID = 'G_FILE_UPLOAD';

-- 2) G_FILE_UPLOAD 在四个党建角色中只能各绑定：报送员1、支部书记1、两类审核员0。
--    COUNT=1 同时验证存在且无重复；通过 PT_RESOURCE JOIN 只承认可解析到资源实体的绑定。
WITH expected_upload_grant AS (
    SELECT 'R_RE_REPORT' AS ROLE_CODE, 1 AS EXPECTED_COUNT
    UNION ALL SELECT 'R_RE_SECR', 1
    UNION ALL SELECT 'R_RE_BRREV', 0
    UNION ALL SELECT 'R_RE_ORGREV', 0
),
actual_upload_grant AS (
    SELECT r.ROLE_CODE, COUNT(p.RESOURCE_ID) AS ACTUAL_COUNT
      FROM PT_ROLE r
      LEFT JOIN PT_ROLE_RESOURCE rr
        ON rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = 'G_FILE_UPLOAD'
      LEFT JOIN PT_RESOURCE p ON p.RESOURCE_ID = rr.RESOURCE_ID
     WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR', 'R_RE_BRREV', 'R_RE_ORGREV')
     GROUP BY r.ROLE_CODE
),
upload_grant_diff AS (
    SELECT e.ROLE_CODE, e.EXPECTED_COUNT, COALESCE(a.ACTUAL_COUNT, 0) AS ACTUAL_COUNT
      FROM expected_upload_grant e
      LEFT JOIN actual_upload_grant a ON a.ROLE_CODE = e.ROLE_CODE
     WHERE COALESCE(a.ACTUAL_COUNT, 0) <> e.EXPECTED_COUNT
)
SELECT 'G_FILE_UPLOAD_ROLE_MATRIX' AS CHECK_ITEM,
       'PASS' AS CHECK_STATUS,
       'reporter=1, secretary=1, reviewers=0' AS DETAIL
 WHERE NOT EXISTS (SELECT 1 FROM upload_grant_diff)
UNION ALL
SELECT 'G_FILE_UPLOAD_ROLE_MATRIX', 'FAIL',
       CONCAT(ROLE_CODE, ':expected=', EXPECTED_COUNT, ',actual=', ACTUAL_COUNT)
  FROM upload_grant_diff;

-- 3) 17 条 P_RE_* 资源必须与权威种子的 ID/URL/Method 完全一致且全部启用，不能多也不能少。
WITH expected_re_resource AS (
    SELECT 'P_RE_ORG_TREE' RESOURCE_ID, '/api/re/orgs/tree' RESOURCE_URL, 'GET' RESOURCE_METHOD
    UNION ALL SELECT 'P_RE_ORG_GET', '/api/re/orgs/*', 'GET'
    UNION ALL SELECT 'P_RE_ORG_ADD', '/api/re/orgs', 'POST'
    UNION ALL SELECT 'P_RE_ORG_UPD', '/api/re/orgs/*', 'PUT'
    UNION ALL SELECT 'P_RE_ORG_DEL', '/api/re/orgs/*', 'DELETE'
    UNION ALL SELECT 'P_RE_MAP_LIST', '/api/re/user-party-maps', 'GET'
    UNION ALL SELECT 'P_RE_MAP_BIND', '/api/re/user-party-maps', 'POST'
    UNION ALL SELECT 'P_RE_SUBMIT_ADD', '/api/re/submits', 'POST'
    UNION ALL SELECT 'P_RE_SUBMIT_MY', '/api/re/submits/my', 'GET'
    UNION ALL SELECT 'P_RE_SUBMIT_GET', '/api/re/submits/*', 'GET'
    UNION ALL SELECT 'P_RE_REVIEW_Q', '/api/re/reviews/**', 'GET'
    UNION ALL SELECT 'P_RE_REVIEW_APPR', '/api/re/reviews/*/approve', 'POST'
    UNION ALL SELECT 'P_RE_REVIEW_REJ', '/api/re/reviews/*/reject', 'POST'
    UNION ALL SELECT 'P_RE_CKPT_VIEW', '/api/re/cockpit/**', 'GET'
    UNION ALL SELECT 'P_RE_CKPT_EXEC', '/api/re/cockpit/overdue/execute', 'POST'
    UNION ALL SELECT 'P_RE_CKPT_ANNUAL', '/api/re/cockpit/archive/generate/*', 'POST'
    UNION ALL SELECT 'P_RE_EXPORT', '/api/re/export/*', 'GET'
),
resource_diff AS (
    SELECT 'MISSING' AS DIFF_TYPE,
           CONVERT(e.RESOURCE_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci AS RESOURCE_ID,
           CONVERT(e.RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_unicode_ci AS RESOURCE_URL,
           CONVERT(e.RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_unicode_ci AS RESOURCE_METHOD,
           NULL AS STATUS
      FROM expected_re_resource e
      LEFT JOIN PT_RESOURCE p ON p.RESOURCE_ID = e.RESOURCE_ID
     WHERE p.RESOURCE_ID IS NULL
    UNION ALL
    SELECT 'CONTRACT_MISMATCH',
           CONVERT(e.RESOURCE_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci,
           CONVERT(p.RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_unicode_ci,
           CONVERT(p.RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_unicode_ci,
           p.STATUS
      FROM expected_re_resource e
      JOIN PT_RESOURCE p ON p.RESOURCE_ID = e.RESOURCE_ID
     WHERE CONVERT(p.RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_bin
               <> CONVERT(e.RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_bin
        OR CONVERT(p.RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_bin
               <> CONVERT(e.RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_bin
        OR p.STATUS <> 0
    UNION ALL
    SELECT 'UNEXPECTED',
           CONVERT(p.RESOURCE_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci,
           CONVERT(p.RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_unicode_ci,
           CONVERT(p.RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_unicode_ci,
           p.STATUS
      FROM PT_RESOURCE p
      LEFT JOIN expected_re_resource e ON e.RESOURCE_ID = p.RESOURCE_ID
     WHERE LEFT(p.RESOURCE_ID, 5) = 'P_RE_'
       AND e.RESOURCE_ID IS NULL
)
SELECT 'P_RE_RESOURCE_CONTRACT' AS CHECK_ITEM,
       'PASS' AS CHECK_STATUS,
       'exactly 17 enabled resources with authoritative URL/method' AS DETAIL
 WHERE NOT EXISTS (SELECT 1 FROM resource_diff)
UNION ALL
SELECT 'P_RE_RESOURCE_CONTRACT', 'FAIL',
       CONCAT(DIFF_TYPE, ':', RESOURCE_ID, ':', COALESCE(RESOURCE_METHOD, 'NULL'), ':',
              COALESCE(RESOURCE_URL, 'NULL'), ':status=', COALESCE(CAST(STATUS AS CHAR), 'NULL'))
  FROM resource_diff;

-- 4) 四角色 P_RE_* 绑定必须与权威 5/7/7/10 矩阵完全一致，且每个预期绑定只能有一行。
WITH expected_re_permission AS (
    SELECT 'R_RE_REPORT' ROLE_CODE, 'P_RE_ORG_TREE' RESOURCE_ID
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_ORG_GET'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_SUBMIT_ADD'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_SUBMIT_MY'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_SUBMIT_GET'

    UNION ALL SELECT 'R_RE_SECR', 'P_RE_ORG_TREE'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_ORG_GET'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_SUBMIT_ADD'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_SUBMIT_MY'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_SUBMIT_GET'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_CKPT_VIEW'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_EXPORT'

    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_ORG_TREE'
    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_ORG_GET'
    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_SUBMIT_MY'
    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_SUBMIT_GET'
    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_REVIEW_Q'
    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_REVIEW_APPR'
    UNION ALL SELECT 'R_RE_BRREV', 'P_RE_REVIEW_REJ'

    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_ORG_TREE'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_ORG_GET'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_SUBMIT_GET'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_REVIEW_Q'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_REVIEW_APPR'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_REVIEW_REJ'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_CKPT_VIEW'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_CKPT_EXEC'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_CKPT_ANNUAL'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_EXPORT'
),
actual_re_permission AS (
    SELECT r.ROLE_CODE, rr.RESOURCE_ID, COUNT(*) AS BINDING_COUNT
      FROM PT_ROLE_RESOURCE rr
      JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
      JOIN PT_RESOURCE p ON p.RESOURCE_ID = rr.RESOURCE_ID
     WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR', 'R_RE_BRREV', 'R_RE_ORGREV')
       AND LEFT(p.RESOURCE_ID, 5) = 'P_RE_'
     GROUP BY r.ROLE_CODE, rr.RESOURCE_ID
),
orphan_re_permission AS (
    SELECT r.ROLE_CODE, rr.RESOURCE_ID, COUNT(*) AS BINDING_COUNT
      FROM PT_ROLE_RESOURCE rr
      JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
      LEFT JOIN PT_RESOURCE p ON p.RESOURCE_ID = rr.RESOURCE_ID
     WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR', 'R_RE_BRREV', 'R_RE_ORGREV')
       AND LEFT(rr.RESOURCE_ID, 5) = 'P_RE_'
       AND p.RESOURCE_ID IS NULL
     GROUP BY r.ROLE_CODE, rr.RESOURCE_ID
),
matrix_diff AS (
    SELECT 'MISSING' AS DIFF_TYPE, e.ROLE_CODE, e.RESOURCE_ID, 0 AS BINDING_COUNT
      FROM expected_re_permission e
      LEFT JOIN actual_re_permission a
        ON a.ROLE_CODE = e.ROLE_CODE AND a.RESOURCE_ID = e.RESOURCE_ID
     WHERE a.RESOURCE_ID IS NULL
    UNION ALL
    SELECT 'DUPLICATE', a.ROLE_CODE, a.RESOURCE_ID, a.BINDING_COUNT
      FROM actual_re_permission a
      JOIN expected_re_permission e
        ON e.ROLE_CODE = a.ROLE_CODE AND e.RESOURCE_ID = a.RESOURCE_ID
     WHERE a.BINDING_COUNT <> 1
    UNION ALL
    SELECT 'UNEXPECTED', a.ROLE_CODE, a.RESOURCE_ID, a.BINDING_COUNT
      FROM actual_re_permission a
      LEFT JOIN expected_re_permission e
        ON e.ROLE_CODE = a.ROLE_CODE AND e.RESOURCE_ID = a.RESOURCE_ID
     WHERE e.RESOURCE_ID IS NULL
    UNION ALL
    SELECT 'ORPHAN', ROLE_CODE, RESOURCE_ID, BINDING_COUNT
      FROM orphan_re_permission
)
SELECT 'P_RE_ROLE_MATRIX' AS CHECK_ITEM,
       'PASS' AS CHECK_STATUS,
       'exact 5/7/7/10 API bindings without duplicates' AS DETAIL
 WHERE NOT EXISTS (SELECT 1 FROM matrix_diff)
UNION ALL
SELECT 'P_RE_ROLE_MATRIX', 'FAIL',
       CONCAT(DIFF_TYPE, ':', ROLE_CODE, ':', RESOURCE_ID, ':count=', BINDING_COUNT)
  FROM matrix_diff;

-- 5) 每条 RE_USER_PARTY_MAP.USER_ID 都必须唯一命中 PT_USER.USER_ID。
SELECT 'RE_USER_PARTY_MAP_USER_ID_ALIGNMENT' AS CHECK_ITEM,
       CASE WHEN COALESCE(SUM(CASE WHEN u.USER_ID IS NULL THEN 1 ELSE 0 END), 0) = 0
            THEN 'PASS' ELSE 'FAIL' END AS CHECK_STATUS,
       CONCAT('total=', COUNT(*),
              ', aligned=', COALESCE(SUM(CASE WHEN u.USER_ID IS NOT NULL THEN 1 ELSE 0 END), 0),
              ', unaligned=', COALESCE(SUM(CASE WHEN u.USER_ID IS NULL THEN 1 ELSE 0 END), 0)) AS DETAIL
  FROM RE_USER_PARTY_MAP m
  LEFT JOIN PT_USER u
    ON CONVERT(u.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(m.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

-- 6) 幂等性：再次运行迁移时，映射 UPDATE 候选和权限 INSERT 候选都必须为 0。
WITH legacy_mapping_candidate AS (
    SELECT m.ID
      FROM RE_USER_PARTY_MAP m
      LEFT JOIN PT_USER u
        ON CONVERT(u.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
         = CONVERT(m.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     WHERE u.USER_ID IS NULL
),
missing_upload_grant AS (
    SELECT r.ROLE_ID
      FROM PT_ROLE r
     WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR')
       AND r.RECORD_STATUS = 0
       AND NOT EXISTS (
           SELECT 1
             FROM PT_ROLE_RESOURCE rr
            WHERE rr.ROLE_ID = r.ROLE_ID
              AND rr.RESOURCE_ID = 'G_FILE_UPLOAD'
       )
)
SELECT 'MIGRATION_RERUN_NOOP' AS CHECK_ITEM,
       CASE WHEN (SELECT COUNT(*) FROM legacy_mapping_candidate) = 0
                  AND (SELECT COUNT(*) FROM missing_upload_grant) = 0
            THEN 'PASS' ELSE 'FAIL' END AS CHECK_STATUS,
       CONCAT('mapping_updates=', (SELECT COUNT(*) FROM legacy_mapping_candidate),
              ', permission_inserts=', (SELECT COUNT(*) FROM missing_upload_grant)) AS DETAIL;

-- 7) RED_ENGINE 范围边界保持一期裁决：四党建角色仍全部为单值 ALL。
SELECT 'RED_ENGINE_SCOPE_PHASE1' AS CHECK_ITEM,
       CASE WHEN COUNT(*) = 4
                  AND SUM(CASE WHEN s.DATA_SCOPE = 'ALL' AND s.RECORD_STATUS = 0 THEN 1 ELSE 0 END) = 4
            THEN 'PASS' ELSE 'FAIL' END AS CHECK_STATUS,
       CONCAT('role_rows=', COUNT(*),
              ', enabled_all_rows=', SUM(CASE WHEN s.DATA_SCOPE = 'ALL' AND s.RECORD_STATUS = 0 THEN 1 ELSE 0 END)) AS DETAIL
  FROM PT_ROLE r
  LEFT JOIN PT_ROLE_BIZ_SCOPE s
    ON s.ROLE_ID = r.ROLE_ID AND s.BIZ_TYPE = 'RED_ENGINE'
 WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR', 'R_RE_BRREV', 'R_RE_ORGREV');
