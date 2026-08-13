-- ============================================================================
-- 红色引擎无角色切换一期：上传权限与用户党组织映射对齐
-- 日期：2026-08-10
-- 适用：MySQL 8.0；由开发/DBA 显式执行，非 Flyway 脚本
--
-- 目标：
--   1. 仅给 R_RE_REPORT / R_RE_SECR 显式补 G_FILE_UPLOAD；
--   2. 将 RE_USER_PARTY_MAP.USER_ID 中遗留的 PT_USER.USERNAME 安全换成 PT_USER.USER_ID；
--   3. 任一映射无匹配、多匹配或目标 uk_user 冲突时整笔回滚（Fail Close）；
--   4. 可重复执行：已对齐映射不更新，已存在权限不重复插入。
--
-- 不在本脚本范围：
--   - 不删除/停用 A_SWITCH_ROLE（兼容期保留为 no-op）；
--   - 不修改任何 P_RE_* 权限矩阵；
--   - 不改 PT_ROLE_BIZ_SCOPE，RED_ENGINE 仍统一为 ALL；
--   - 不执行复合 DataScope 或党组织审核隔离改造。
--
-- 执行后必须运行：2026-08-10-redengine-auth-data-verify.sql
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_20260810_redengine_auth_data_align;

DELIMITER //

CREATE PROCEDURE sp_20260810_redengine_auth_data_align()
BEGIN
    DECLARE v_upload_resource_count INT DEFAULT 0;
    DECLARE v_report_role_total_count INT DEFAULT 0;
    DECLARE v_report_role_enabled_count INT DEFAULT 0;
    DECLARE v_secretary_role_total_count INT DEFAULT 0;
    DECLARE v_secretary_role_enabled_count INT DEFAULT 0;
    DECLARE v_unexpected_upload_grant_count INT DEFAULT 0;
    DECLARE v_report_upload_grant_count INT DEFAULT 0;
    DECLARE v_secretary_upload_grant_count INT DEFAULT 0;
    DECLARE v_unresolved_count INT DEFAULT 0;
    DECLARE v_ambiguous_count INT DEFAULT 0;
    DECLARE v_existing_target_conflict_count INT DEFAULT 0;
    DECLARE v_duplicate_target_count INT DEFAULT 0;
    DECLARE v_unaligned_after_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_re_user_party_resolution_20260810;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- 先验证共享资源完整 HTTP 契约，防止同 ID 的停用/错 URL/错 Method/菜单行被静默授权。
    SELECT COUNT(*)
      INTO v_upload_resource_count
     FROM PT_RESOURCE
     WHERE RESOURCE_ID = 'G_FILE_UPLOAD'
       AND CONVERT(RESOURCE_URL USING utf8mb4) COLLATE utf8mb4_bin
             = CONVERT('/api/files/upload' USING utf8mb4) COLLATE utf8mb4_bin
       AND CONVERT(RESOURCE_METHOD USING utf8mb4) COLLATE utf8mb4_bin
             = CONVERT('POST' USING utf8mb4) COLLATE utf8mb4_bin
       AND STATUS = 0
       AND ISMENU = 0;

    IF v_upload_resource_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: G_FILE_UPLOAD must be enabled POST /api/files/upload API resource';
    END IF;

    -- 每个 code 均要求“总行数=1 且启用行数=1”，拒绝一启用一停用等重复角色漂移。
    SELECT COUNT(*),
           COALESCE(SUM(CASE WHEN RECORD_STATUS = 0 THEN 1 ELSE 0 END), 0)
      INTO v_report_role_total_count, v_report_role_enabled_count
      FROM PT_ROLE
     WHERE ROLE_CODE = 'R_RE_REPORT';

    IF v_report_role_total_count <> 1 OR v_report_role_enabled_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: R_RE_REPORT total/enabled role counts must both be exactly 1';
    END IF;

    SELECT COUNT(*),
           COALESCE(SUM(CASE WHEN RECORD_STATUS = 0 THEN 1 ELSE 0 END), 0)
      INTO v_secretary_role_total_count, v_secretary_role_enabled_count
      FROM PT_ROLE
     WHERE ROLE_CODE = 'R_RE_SECR';

    IF v_secretary_role_total_count <> 1 OR v_secretary_role_enabled_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: R_RE_SECR total/enabled role counts must both be exactly 1';
    END IF;

    SELECT COUNT(*)
      INTO v_unexpected_upload_grant_count
      FROM PT_ROLE_RESOURCE rr
      JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
     WHERE rr.RESOURCE_ID = 'G_FILE_UPLOAD'
       AND r.ROLE_CODE IN ('R_RE_BRREV', 'R_RE_ORGREV');

    IF v_unexpected_upload_grant_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: review-only red roles already have G_FILE_UPLOAD';
    END IF;

    -- 只处理“当前值不是任何 PT_USER.USER_ID”的遗留行；已经是平台工号的行保持不动。
    -- 两侧显式转为 utf8mb4_unicode_ci，规避历史表 collation 不一致导致的比较错误。
    CREATE TEMPORARY TABLE tmp_re_user_party_resolution_20260810 AS
    SELECT m.ID AS MAP_ID,
           m.USER_ID AS OLD_USER_ID,
           MIN(u.USER_ID) AS RESOLVED_USER_ID,
           COUNT(u.USER_ID) AS USERNAME_MATCH_COUNT
      FROM RE_USER_PARTY_MAP m
      LEFT JOIN PT_USER direct_user
        ON CONVERT(direct_user.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
         = CONVERT(m.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
      LEFT JOIN PT_USER u
        ON direct_user.USER_ID IS NULL
       AND CONVERT(u.USERNAME USING utf8mb4) COLLATE utf8mb4_unicode_ci
         = CONVERT(m.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     WHERE direct_user.USER_ID IS NULL
     GROUP BY m.ID, m.USER_ID;

    SELECT COUNT(*)
      INTO v_unresolved_count
      FROM tmp_re_user_party_resolution_20260810
     WHERE USERNAME_MATCH_COUNT = 0;

    IF v_unresolved_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: RE_USER_PARTY_MAP contains username with no PT_USER match';
    END IF;

    SELECT COUNT(*)
      INTO v_ambiguous_count
      FROM tmp_re_user_party_resolution_20260810
     WHERE USERNAME_MATCH_COUNT > 1;

    IF v_ambiguous_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: RE_USER_PARTY_MAP username matches multiple PT_USER rows';
    END IF;

    -- 目标平台工号若已被另一映射占用，更新会撞 uk_user；显式检查并给出可诊断错误。
    SELECT COUNT(*)
      INTO v_existing_target_conflict_count
      FROM tmp_re_user_party_resolution_20260810 r
      JOIN RE_USER_PARTY_MAP existing_map
        ON existing_map.ID <> r.MAP_ID
       AND CONVERT(existing_map.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
         = CONVERT(r.RESOLVED_USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     WHERE r.USERNAME_MATCH_COUNT = 1;

    IF v_existing_target_conflict_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: resolved PT_USER.USER_ID is already mapped';
    END IF;

    -- 两个遗留字符串在目标库 collation 下解析到同一 USER_ID 时，同样拒绝猜测覆盖。
    SELECT COUNT(*)
      INTO v_duplicate_target_count
      FROM (
          SELECT CONVERT(RESOLVED_USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci AS TARGET_USER_ID
            FROM tmp_re_user_party_resolution_20260810
           WHERE USERNAME_MATCH_COUNT = 1
           GROUP BY CONVERT(RESOLVED_USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
          HAVING COUNT(*) > 1
      ) duplicate_target;

    IF v_duplicate_target_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: multiple legacy rows resolve to the same PT_USER.USER_ID';
    END IF;

    -- 全部前置检查通过后才执行写操作。
    UPDATE RE_USER_PARTY_MAP m
    JOIN tmp_re_user_party_resolution_20260810 r ON r.MAP_ID = m.ID
       SET m.USER_ID = r.RESOLVED_USER_ID,
           m.UPDATE_TIME = CURRENT_TIMESTAMP
     WHERE r.USERNAME_MATCH_COUNT = 1;

    INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
    SELECT MD5(CONCAT(r.ROLE_ID, '#G_FILE_UPLOAD')),
           r.ROLE_ID,
           'G_FILE_UPLOAD',
           COALESCE(NULLIF(p.SYS_CODE, ''), 'PLATFORM')
      FROM PT_ROLE r
      JOIN PT_RESOURCE p ON p.RESOURCE_ID = 'G_FILE_UPLOAD' AND p.STATUS = 0
     WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR')
       AND r.RECORD_STATUS = 0
       AND NOT EXISTS (
           SELECT 1
             FROM PT_ROLE_RESOURCE rr
            WHERE rr.ROLE_ID = r.ROLE_ID
              AND rr.RESOURCE_ID = 'G_FILE_UPLOAD'
       );

    -- 分 code 做精确后置检查；COUNT(*)=1 同时拒绝缺失和重复绑定。
    SELECT COUNT(*)
      INTO v_report_upload_grant_count
      FROM PT_ROLE_RESOURCE rr
      JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
     WHERE rr.RESOURCE_ID = 'G_FILE_UPLOAD'
       AND r.ROLE_CODE = 'R_RE_REPORT'
       AND r.RECORD_STATUS = 0;

    IF v_report_upload_grant_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: R_RE_REPORT G_FILE_UPLOAD binding count must be exactly 1';
    END IF;

    SELECT COUNT(*)
      INTO v_secretary_upload_grant_count
      FROM PT_ROLE_RESOURCE rr
      JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
     WHERE rr.RESOURCE_ID = 'G_FILE_UPLOAD'
       AND r.ROLE_CODE = 'R_RE_SECR'
       AND r.RECORD_STATUS = 0;

    IF v_secretary_upload_grant_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: R_RE_SECR G_FILE_UPLOAD binding count must be exactly 1';
    END IF;

    -- 事务内后置校验：任何映射仍不能按 PT_USER.USER_ID 唯一解析都回滚。
    SELECT COUNT(*)
      INTO v_unaligned_after_count
      FROM RE_USER_PARTY_MAP m
      LEFT JOIN PT_USER u
        ON CONVERT(u.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
         = CONVERT(m.USER_ID USING utf8mb4) COLLATE utf8mb4_unicode_ci
     WHERE u.USER_ID IS NULL;

    IF v_unaligned_after_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'redengine align aborted: post-check found RE_USER_PARTY_MAP row not aligned to PT_USER.USER_ID';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_re_user_party_resolution_20260810;
    COMMIT;
END//

DELIMITER ;

CALL sp_20260810_redengine_auth_data_align();
DROP PROCEDURE IF EXISTS sp_20260810_redengine_auth_data_align;
