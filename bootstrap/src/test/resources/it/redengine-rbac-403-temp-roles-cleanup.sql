-- ============================================================
-- RedEngineSmokeIT 无角色切换夹具清理。
-- 只清理 CREATE_AUTHOR/REMARK 与固定 ID/USERNAME 全部匹配的本夹具用户；
-- 即使固定 ID 被其他数据占用，也绝不删除或接管。
-- ============================================================

DROP TEMPORARY TABLE IF EXISTS TMP_RE_IT_OWNED_USER;
CREATE TEMPORARY TABLE TMP_RE_IT_OWNED_USER AS
SELECT USER_ID
  FROM PT_USER
 WHERE CREATE_AUTHOR = 'RedEngineSmokeIT'
   AND REMARK = 'RedEngineSmokeIT managed fixture 2026-08-10'
   AND ((USER_ID = 'RE_IT_REPORT' AND USERNAME = 're_it_report')
     OR (USER_ID = 'RE_IT_BRREV' AND USERNAME = 're_it_brrev')
     OR (USER_ID = 'RE_IT_UNION' AND USERNAME = 're_it_union'));

DELETE score
  FROM RE_SCORE score
  JOIN RE_SUBMIT submit_record ON submit_record.ID = score.SUBMIT_ID
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(submit_record.SUBMITTER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE submit_file
  FROM RE_SUBMIT_FILE submit_file
  JOIN RE_SUBMIT submit_record ON submit_record.ID = submit_file.SUBMIT_ID
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(submit_record.SUBMITTER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE submit_record
  FROM RE_SUBMIT submit_record
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(submit_record.SUBMITTER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE audit_record
  FROM AUDIT_LOG audit_record
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(audit_record.EMP_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
 WHERE audit_record.BIZ_TYPE = 'RED_ENGINE';

DELETE party_map
  FROM RE_USER_PARTY_MAP party_map
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(party_map.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE user_role
  FROM PT_USER_ROLE user_role
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(user_role.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE platform_user
  FROM PT_USER platform_user
  JOIN TMP_RE_IT_OWNED_USER owned
    ON CONVERT(owned.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONVERT(platform_user.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE TMP_RE_IT_OWNED_USER;
