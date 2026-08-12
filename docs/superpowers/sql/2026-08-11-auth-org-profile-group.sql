-- ============================================================================
-- AUTH：机构本地画像、命名机构组、角色-机构组授权与结构化审计
-- 日期：2026-08-11
--
-- 手工执行脚本；禁止由 Flyway、应用启动或自动迁移框架执行。
--
-- 执行门禁：
-- 1. 先对获批准的隔离目标做只读盘点。覆盖、清空、重建、从 yiti 克隆或为这些操作做备份
--    都必须先取得明确确认；测试或备份均不构成授权，绝不直接改动 yiti。
-- 2. 获准后仅对获准目标保存受控备份、表/行数清单和 checksum，并在独立隔离实例完成
--    可恢复性演练；备份、diff、日志、截图和工单须脱敏、最小化导出并限制留存/访问。
-- 3. 克隆到获准隔离实例后须隔离 SPRING_SESSION、PT_LOCK、Quartz/sys_job_conf、消息收发、
--    缓存命名空间、对象存储及全部外联凭据/回调，禁用调度和外发；再完整执行、核验二次
--    执行零变化并归档前后 diff/checksum/恢复证据。
-- 4. 隔离验证通过后，先报告完整证据并再次取得生产目标的单独执行确认，才可在生产执行。
-- 5. 本脚本遇到已有残缺表、索引不匹配、ROLE_CODE 重复、主键/业务键身份冲突时立即
--    失败：首个 DDL 前由会话级 FAIL CLOSE 门中断，过程内其余检查使用 SIGNAL；
--    禁止用忽略冲突的插入或 REPLACE 掩盖冲突。
--
-- 幂等契约：首次仅创建缺失对象和缺失资源/角色；再次执行不更新既有正确记录，受影响
-- 行数为零。并发执行时严格 INSERT 的唯一键错误同样视为冲突，必须人工复核。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 目标实例身份会话契约（强制，fail-close）：本脚本不预设固定逻辑库名。获权操作者必须先
-- 核对审批/工单 manifest，再在同一个已核身份的写会话中完成下列所有 SET @approved... 和
-- SOURCE；只可通过 docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh 执行，禁止裸 mysql。
-- 禁止把只读预检和写执行分连接，禁止 mysql --force 或任何吞错执行器。
-- 所有值须来自该次审批 manifest：实例值与当前会话显示值必须逐字节一致，port 使用 @@port
-- 的十进制文本；manifest hash 必须是获批清单的 SHA-256，而不是临时口头确认。
-- SET @approved_target_server_uuid = '<approved @@server_uuid>';
-- SET @approved_target_hostname = '<approved @@hostname>';
-- SET @approved_target_port = '<approved @@port decimal text>';
-- SET @approved_target_schema = '<approved DATABASE() schema>';
-- SET @approved_change_ticket = '<approved change or ticket id>';
-- SET @approved_manifest_sha256 = '<64-hex approved manifest SHA-256>';
-- SOURCE docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql;
-- ---------------------------------------------------------------------------

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 零 DDL 预检门。此段只读取 information_schema / 既有身份表、当前会话身份，并只使用
--    会话变量与 PREPARE；不会创建、删除或修改任何数据库对象或业务数据。
--
--    冲突时先输出精确原因，再 PREPARE 一个带 UUID 的不存在表查询，使默认“遇错停止”
--    的 MySQL 客户端在首个 DDL 前失败。禁止使用 mysql --force 或任何忽略错误的执行器，
--    否则会破坏 FAIL CLOSE 语义。
-- ---------------------------------------------------------------------------
SET @auth_preflight_error := (
    SELECT CASE
        WHEN @approved_target_server_uuid IS NULL
             OR CHAR_LENGTH(TRIM(@approved_target_server_uuid)) = 0
             OR @approved_target_hostname IS NULL
             OR CHAR_LENGTH(TRIM(@approved_target_hostname)) = 0
             OR @approved_target_port IS NULL
             OR CHAR_LENGTH(TRIM(@approved_target_port)) = 0
             OR @approved_target_schema IS NULL
             OR CHAR_LENGTH(TRIM(@approved_target_schema)) = 0
             OR @approved_change_ticket IS NULL
             OR CHAR_LENGTH(TRIM(@approved_change_ticket)) = 0
             OR @approved_manifest_sha256 IS NULL
             OR CHAR_LENGTH(TRIM(@approved_manifest_sha256)) = 0
             OR @approved_manifest_sha256 NOT REGEXP '^[0-9A-Fa-f]{64}$'
            THEN 'AUTH deploy target identity guard: approval variables missing or invalid'
        WHEN @@server_uuid IS NULL
             OR CHAR_LENGTH(TRIM(@@server_uuid)) = 0
             OR @@hostname IS NULL
             OR CHAR_LENGTH(TRIM(@@hostname)) = 0
             OR @@port IS NULL
             OR CHAR_LENGTH(TRIM(CAST(@@port AS CHAR))) = 0
             OR DATABASE() IS NULL
             OR CHAR_LENGTH(TRIM(DATABASE())) = 0
            THEN 'AUTH deploy target identity guard: actual session identity missing'
        WHEN BINARY @approved_target_server_uuid <> BINARY @@server_uuid
             OR BINARY @approved_target_hostname <> BINARY @@hostname
             OR BINARY @approved_target_port <> BINARY CAST(@@port AS CHAR)
             OR BINARY @approved_target_schema <> BINARY DATABASE()
            THEN 'AUTH deploy target identity guard: approval target does not exactly match current write session'
        WHEN (
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
        ) <> 1 THEN 'AUTH deploy preflight: PT_ROLE 不存在'
        WHEN (
            SELECT COUNT(*)
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
               AND (
                    (column_name = 'ROLE_ID' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'ROLE_CODE' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'ROLE_CHNAME' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'RECORD_STATUS' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
                 OR (column_name = 'SYS_CODE' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default = 'PLATFORM')
                 OR (column_name = 'CREATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
                 OR (column_name = 'CREATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'UPDATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                 OR (column_name = 'UPDATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
               )
        ) <> 10 THEN 'AUTH deploy preflight: PT_ROLE 完整列定义或长度异常'
        WHEN COALESCE((
            SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
               AND index_name = 'PRIMARY' AND non_unique = 0
        ), '') <> 'ROLE_ID' THEN 'AUTH deploy preflight: PT_ROLE 主键必须为 ROLE_ID'
        WHEN EXISTS (
            SELECT 1 FROM PT_ROLE GROUP BY ROLE_CODE HAVING COUNT(*) > 1
        ) THEN 'AUTH deploy preflight: PT_ROLE 存在重复 ROLE_CODE'
        WHEN EXISTS (
            SELECT 1 FROM PT_ROLE
             WHERE ROLE_CODE REGEXP '^R_SCREEN_'
               AND (ROLE_ID IS NULL OR ROLE_ID NOT REGEXP '^[0-9]+$')
        ) THEN 'AUTH deploy preflight: 既有 R_SCREEN_* 角色 ROLE_ID 必须为纯数字'
        -- 仅在本次仍需分配大屏角色 ID 时检查数值序列上界，避免后续 CAST/MAX 在已经发生
        -- DDL 后才因 UNSIGNED BIGINT 溢出失败。全零前缀按数值零处理。
        WHEN (
            SELECT COUNT(*) FROM PT_ROLE
             WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER', 'R_SCREEN_RETAIL_VIEWER')
        ) < 2 AND EXISTS (
            SELECT 1 FROM PT_ROLE
             WHERE ROLE_ID REGEXP '^[0-9]+$'
               AND (
                    CHAR_LENGTH(TRIM(LEADING '0' FROM ROLE_ID)) > 20
                 OR (
                        CHAR_LENGTH(TRIM(LEADING '0' FROM ROLE_ID)) = 20
                    AND TRIM(LEADING '0' FROM ROLE_ID) >= '18446744073709551615'
                    )
               )
        ) THEN 'AUTH deploy preflight: 新 R_SCREEN_* ROLE_ID 数值序列已耗尽'
        WHEN EXISTS (
            SELECT 1
              FROM (
                    SELECT 'R_SCREEN_CORP_VIEWER' AS role_code, '对公大屏查看' AS role_chname,
                           'PLATFORM' AS sys_code, '2026-08-11-auth' AS operator_name,
                           '大屏查看能力角色，人员分配须经业务审批' AS remark
                    UNION ALL
                    SELECT 'R_SCREEN_RETAIL_VIEWER', '零售大屏查看', 'PLATFORM', '2026-08-11-auth',
                           '大屏查看能力角色，人员分配须经业务审批'
                   ) AS expected_role
             WHERE CHAR_LENGTH(role_code) > 50
                OR CHAR_LENGTH(role_chname) > 100
                OR CHAR_LENGTH(sys_code) > 10
                OR CHAR_LENGTH(operator_name) > 50
                OR CHAR_LENGTH(remark) > 100
        ) THEN 'AUTH deploy preflight: 待写 PT_ROLE 值超过目标列长度'
        WHEN EXISTS (
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
               AND index_name = 'UK_PT_ROLE_ROLE_CODE'
        ) AND (
            (
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
                   AND index_name = 'UK_PT_ROLE_ROLE_CODE' AND non_unique = 0
            ) <> 1
            OR COALESCE((
                SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
                   AND index_name = 'UK_PT_ROLE_ROLE_CODE'
            ), '') <> 'ROLE_CODE'
        ) THEN 'AUTH deploy preflight: PT_ROLE 同名 ROLE_CODE 唯一索引异常'
        WHEN (
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
        ) <> 1 THEN 'AUTH deploy preflight: PT_RESOURCE 不存在'
        WHEN (
            SELECT COUNT(*)
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
               AND (
                    (column_name = 'RESOURCE_ID' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'RESOURCE_URL' AND data_type = 'varchar' AND character_maximum_length = 256 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'RESOURCE_METHOD' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'MENU_NAME' AND data_type = 'varchar' AND character_maximum_length = 256 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'MENU_ICON_URL' AND data_type = 'varchar' AND character_maximum_length = 256 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'MENU_RANK_NO' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
                 OR (column_name = 'ISMENU' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
                 OR (column_name = 'MENU_ENDFLAG' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default = '0')
                 OR (column_name = 'PARENT_RESOURCE_ID' AND data_type = 'varchar' AND character_maximum_length = 60 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'STATUS' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
                 OR (column_name = 'SYS_CODE' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default = 'PLATFORM')
                 OR (column_name = 'CREATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
                 OR (column_name = 'CREATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'UPDATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                 OR (column_name = 'UPDATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
               )
        ) <> 16 THEN 'AUTH deploy preflight: PT_RESOURCE 完整列定义或长度异常'
        WHEN COALESCE((
            SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
               AND index_name = 'PRIMARY' AND non_unique = 0
        ), '') <> 'RESOURCE_ID' THEN 'AUTH deploy preflight: PT_RESOURCE 主键必须为 RESOURCE_ID'
        WHEN EXISTS (
            SELECT 1 FROM PT_RESOURCE
             GROUP BY RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
            HAVING COUNT(*) > 1
        ) THEN 'AUTH deploy preflight: PT_RESOURCE 存在重复 URL/METHOD/SYS_CODE'
        WHEN EXISTS (
            SELECT 1
              FROM (
                    SELECT 'A_ORG_PROF_LIST' AS resource_id, '/api/admin/org-profiles' AS resource_url, 'GET' AS resource_method, '机构画像-列表' AS menu_name, 'PLATFORM' AS sys_code, '机构本地画像列表' AS remark
                    UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', '机构画像-保存', 'PLATFORM', '机构本地画像保存'
                    UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', '机构组-列表', 'PLATFORM', '命名机构组列表'
                    UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', '机构组-新建', 'PLATFORM', '命名机构组新建'
                    UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', '机构组-修改', 'PLATFORM', '命名机构组基本信息修改'
                    UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', '机构组-成员覆盖', 'PLATFORM', '命名机构组成员覆盖保存'
                    UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', '机构组-角色绑定', 'PLATFORM', '命名机构组角色覆盖保存'
                   ) AS expected_resource
             WHERE CHAR_LENGTH(resource_id) > 20
                OR CHAR_LENGTH(resource_url) > 256
                OR CHAR_LENGTH(resource_method) > 10
                OR CHAR_LENGTH(menu_name) > 256
                OR CHAR_LENGTH(sys_code) > 10
                OR CHAR_LENGTH(remark) > 100
        ) THEN 'AUTH deploy preflight: 待写 PT_RESOURCE 值超过目标列长度'
        WHEN EXISTS (
            SELECT 1
              FROM PT_RESOURCE existing_resource
              JOIN (
                    SELECT 'A_ORG_PROF_LIST' AS resource_id, '/api/admin/org-profiles' AS resource_url, 'GET' AS resource_method, 'PLATFORM' AS sys_code
                    UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', 'PLATFORM'
                   ) AS expected_resource
                ON existing_resource.RESOURCE_ID = expected_resource.resource_id COLLATE utf8mb4_general_ci
             WHERE NOT (existing_resource.RESOURCE_URL <=> expected_resource.resource_url COLLATE utf8mb4_general_ci
                    AND existing_resource.RESOURCE_METHOD <=> expected_resource.resource_method COLLATE utf8mb4_general_ci
                    AND existing_resource.SYS_CODE <=> expected_resource.sys_code COLLATE utf8mb4_general_ci)
        ) THEN 'AUTH deploy conflict: RESOURCE_ID 已对应不同 URL/METHOD/SYS_CODE'
        WHEN EXISTS (
            SELECT 1
              FROM PT_RESOURCE existing_resource
              JOIN (
                    SELECT 'A_ORG_PROF_LIST' AS resource_id, '/api/admin/org-profiles' AS resource_url, 'GET' AS resource_method, 'PLATFORM' AS sys_code
                    UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', 'PLATFORM'
                    UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', 'PLATFORM'
                   ) AS expected_resource
                ON existing_resource.RESOURCE_URL = expected_resource.resource_url COLLATE utf8mb4_general_ci
               AND existing_resource.RESOURCE_METHOD = expected_resource.resource_method COLLATE utf8mb4_general_ci
               AND existing_resource.SYS_CODE = expected_resource.sys_code COLLATE utf8mb4_general_ci
             WHERE existing_resource.RESOURCE_ID <> expected_resource.resource_id COLLATE utf8mb4_general_ci
        ) THEN 'AUTH deploy conflict: URL/METHOD/SYS_CODE 已对应不同 RESOURCE_ID'
        WHEN EXISTS (
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
               AND index_name = 'UK_PT_RESOURCE_URL_METHOD_SYS'
        ) AND (
            (
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
                   AND index_name = 'UK_PT_RESOURCE_URL_METHOD_SYS' AND non_unique = 0
            ) <> 3
            OR COALESCE((
                SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
                   AND index_name = 'UK_PT_RESOURCE_URL_METHOD_SYS'
            ), '') <> 'RESOURCE_URL,RESOURCE_METHOD,SYS_CODE'
        ) THEN 'AUTH deploy preflight: PT_RESOURCE 同名唯一索引异常'
    END
);

-- 已有目标表时，必须先在零 DDL 阶段验证其完整列集合；缺表由后续 CREATE TABLE 创建。
SET @auth_preflight_error := COALESCE(@auth_preflight_error, (
    SELECT CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE'
               AND column_name IN ('ORG_CODE', 'ORG_NATURE', 'OPERATING_LEVEL', 'OWNER_OPERATING_ORG_CODE',
                                   'CITY_CODE', 'CITY_NAME', 'LNG', 'LAT', 'COORD_SYS', 'STATUS', 'VERSION',
                                   'CREATED_BY', 'CREATED_TIME', 'UPDATED_BY', 'UPDATED_TIME', 'REMARK')
        ) <> 16 THEN 'AUTH deploy preflight: PT_ORG_PROFILE 为残缺表'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP'
               AND column_name IN ('ID', 'GROUP_CODE', 'GROUP_NAME', 'GROUP_PURPOSE', 'STATUS', 'VERSION',
                                   'CREATED_BY', 'CREATED_TIME', 'UPDATED_BY', 'UPDATED_TIME', 'REMARK')
        ) <> 11 THEN 'AUTH deploy preflight: PT_ORG_GROUP 为残缺表'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER'
               AND column_name IN ('ID', 'GROUP_CODE', 'ORG_CODE', 'STATUS', 'CREATED_BY', 'CREATED_TIME',
                                   'UPDATED_BY', 'UPDATED_TIME', 'REMARK')
        ) <> 9 THEN 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 为残缺表'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP'
               AND column_name IN ('ID', 'ROLE_ID', 'GROUP_CODE', 'STATUS', 'CREATED_BY', 'CREATED_TIME',
                                   'UPDATED_BY', 'UPDATED_TIME', 'REMARK')
        ) <> 9 THEN 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 为残缺表'
    END
));

-- 名称齐全并不等于可安全写入：已有 AUTH 目标表还必须精确匹配数据类型、长度、空值、
-- 默认值和自动更新时间。下列检查与过程内诊断保持同一完整列定义。
SET @auth_preflight_error := COALESCE(@auth_preflight_error, (
    SELECT CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE'
               AND (
                    (column_name = 'ORG_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'ORG_NATURE' AND data_type = 'varchar' AND character_maximum_length = 30 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'OPERATING_LEVEL' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'OWNER_OPERATING_ORG_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'CITY_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'CITY_NAME' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'LNG' AND data_type = 'decimal' AND numeric_precision = 10 AND numeric_scale = 6 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'LAT' AND data_type = 'decimal' AND numeric_precision = 10 AND numeric_scale = 6 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'COORD_SYS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
                 OR (column_name = 'VERSION' AND data_type = 'int' AND is_nullable = 'NO' AND CAST(column_default AS CHAR) = '0')
                 OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
                 OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                 OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
               )
        ) <> 16 THEN 'AUTH deploy preflight: PT_ORG_PROFILE 完整列定义或长度异常'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP'
               AND (
                    (column_name = 'ID' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
                 OR (column_name = 'GROUP_CODE' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'GROUP_NAME' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'GROUP_PURPOSE' AND data_type = 'varchar' AND character_maximum_length = 30 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
                 OR (column_name = 'VERSION' AND data_type = 'int' AND is_nullable = 'NO' AND CAST(column_default AS CHAR) = '0')
                 OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
                 OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                 OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
               )
        ) <> 11 THEN 'AUTH deploy preflight: PT_ORG_GROUP 完整列定义或长度异常'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER'
               AND (
                    (column_name = 'ID' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
                 OR (column_name = 'GROUP_CODE' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'ORG_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
                 OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
                 OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                 OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
               )
        ) <> 9 THEN 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 完整列定义或长度异常'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP'
        ) AND (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP'
               AND (
                    (column_name = 'ID' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
                 OR (column_name = 'ROLE_ID' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'GROUP_CODE' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
                 OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
                 OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                 OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
               )
        ) <> 9 THEN 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 完整列定义或长度异常'
    END
));

-- 目标表存在时，索引必须是本期精确列序与唯一性；绝不让 CREATE TABLE IF NOT EXISTS 掩盖残缺索引。
SET @auth_preflight_error := COALESCE(@auth_preflight_error, (
    SELECT CASE WHEN EXISTS (
        SELECT 1
          FROM (
                SELECT 'PT_ORG_PROFILE' AS table_name, 'PRIMARY' AS index_name, 0 AS non_unique, 'ORG_CODE' AS index_columns
                UNION ALL SELECT 'PT_ORG_PROFILE', 'IDX_PT_ORG_PROFILE_STATUS_LEVEL', 1, 'STATUS,OPERATING_LEVEL'
                UNION ALL SELECT 'PT_ORG_PROFILE', 'IDX_PT_ORG_PROFILE_OWNER', 1, 'OWNER_OPERATING_ORG_CODE'
                UNION ALL SELECT 'PT_ORG_PROFILE', 'IDX_PT_ORG_PROFILE_CITY', 1, 'CITY_CODE'
                UNION ALL SELECT 'PT_ORG_GROUP', 'PRIMARY', 0, 'ID'
                UNION ALL SELECT 'PT_ORG_GROUP', 'UK_PT_ORG_GROUP_CODE', 0, 'GROUP_CODE'
                UNION ALL SELECT 'PT_ORG_GROUP', 'IDX_PT_ORG_GROUP_STATUS', 1, 'STATUS'
                UNION ALL SELECT 'PT_ORG_GROUP_MEMBER', 'PRIMARY', 0, 'ID'
                UNION ALL SELECT 'PT_ORG_GROUP_MEMBER', 'UK_PT_ORG_GROUP_MEMBER', 0, 'GROUP_CODE,ORG_CODE'
                UNION ALL SELECT 'PT_ORG_GROUP_MEMBER', 'IDX_PT_ORG_GROUP_MEMBER_GROUP', 1, 'GROUP_CODE,STATUS'
                UNION ALL SELECT 'PT_ORG_GROUP_MEMBER', 'IDX_PT_ORG_GROUP_MEMBER_ORG', 1, 'ORG_CODE'
                UNION ALL SELECT 'PT_ROLE_ORG_GROUP', 'PRIMARY', 0, 'ID'
                UNION ALL SELECT 'PT_ROLE_ORG_GROUP', 'UK_PT_ROLE_ORG_GROUP', 0, 'ROLE_ID,GROUP_CODE'
                UNION ALL SELECT 'PT_ROLE_ORG_GROUP', 'IDX_PT_ROLE_ORG_GROUP_GROUP', 1, 'GROUP_CODE,STATUS'
                UNION ALL SELECT 'PT_ROLE_ORG_GROUP', 'IDX_PT_ROLE_ORG_GROUP_ROLE', 1, 'ROLE_ID,STATUS'
               ) AS expected_index
          JOIN information_schema.tables target_table
            ON target_table.table_schema = DATABASE()
           AND target_table.table_name = expected_index.table_name
          LEFT JOIN (
                SELECT table_name, index_name, MIN(non_unique) AS non_unique,
                       GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') AS index_columns
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE()
                 GROUP BY table_name, index_name
               ) AS actual_index
            ON actual_index.table_name = expected_index.table_name
           AND actual_index.index_name = expected_index.index_name
         WHERE actual_index.index_name IS NULL
            OR actual_index.non_unique <> expected_index.non_unique
            OR COALESCE(actual_index.index_columns, '') <> expected_index.index_columns
    ) THEN 'AUTH deploy preflight: 本期 AUTH 目标表索引残缺或定义异常' END
));

-- 治理审计表必须先完好；已有本期结构化字段/索引也必须在任何 DDL 前校验完整定义。
SET @auth_preflight_error := COALESCE(@auth_preflight_error, (
    SELECT CASE
        WHEN (
            SELECT COUNT(*) FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
        ) <> 1 THEN 'AUTH deploy preflight: AUDIT_LOG 不存在，不能降级为 logger 审计'
        WHEN (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
               AND column_name IN ('id', 'trace_id', 'emp_id', 'emp_name', 'biz_type', 'biz_action', 'resource_url',
                                   'request_method', 'request_params', 'response_status', 'error_msg', 'ip_address',
                                   'user_agent', 'execution_time', 'reason', 'created_time')
        ) <> 16 THEN 'AUTH deploy preflight: AUDIT_LOG 基础结构残缺'
        WHEN (
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
               AND (
                    (column_name = 'id' AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'trace_id' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'emp_id' AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO' AND column_default IS NULL)
                 OR (column_name = 'emp_name' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'biz_type' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'biz_action' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'resource_url' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'request_method' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'request_params' AND data_type = 'text' AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'response_status' AND data_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'error_msg' AND data_type = 'text' AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'ip_address' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'user_agent' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'execution_time' AND data_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'reason' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
                 OR (column_name = 'created_time' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
               )
        ) <> 16 THEN 'AUTH deploy preflight: AUDIT_LOG 完整基础列定义或长度异常'
        WHEN EXISTS (
            SELECT 1
              FROM (
                    SELECT 'target_type' AS column_name, 'varchar' AS data_type, 'varchar(100)' AS column_type,
                           100 AS character_maximum_length, 'YES' AS is_nullable, NULL AS column_default, '' AS extra
                    UNION ALL SELECT 'target_id', 'varchar', 'varchar(128)', 128, 'YES', NULL, ''
                    UNION ALL SELECT 'before_snapshot', 'text', 'text', 65535, 'YES', NULL, ''
                    UNION ALL SELECT 'after_snapshot', 'text', 'text', 65535, 'YES', NULL, ''
                    UNION ALL SELECT 'added_items', 'text', 'text', 65535, 'YES', NULL, ''
                    UNION ALL SELECT 'removed_items', 'text', 'text', 65535, 'YES', NULL, ''
                   ) AS expected_audit_column
              JOIN information_schema.columns actual_column
                ON actual_column.table_schema = DATABASE()
               AND actual_column.table_name = 'AUDIT_LOG'
               AND actual_column.column_name = expected_audit_column.column_name
             WHERE actual_column.data_type <> expected_audit_column.data_type
                OR actual_column.column_type <> expected_audit_column.column_type
                OR NOT (actual_column.character_maximum_length <=> expected_audit_column.character_maximum_length)
                OR actual_column.is_nullable <> expected_audit_column.is_nullable
                OR NOT (actual_column.column_default <=> expected_audit_column.column_default)
                OR actual_column.extra <> expected_audit_column.extra
        ) THEN 'AUTH deploy preflight: AUDIT_LOG 结构化字段完整列定义异常'
        WHEN EXISTS (
            SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
               AND index_name = 'IDX_AUDIT_LOG_TARGET'
        ) AND (
            (
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
                   AND index_name = 'IDX_AUDIT_LOG_TARGET' AND non_unique = 1
            ) <> 2
            OR COALESCE((
                SELECT GROUP_CONCAT(LOWER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
                   AND index_name = 'IDX_AUDIT_LOG_TARGET'
            ), '') <> 'target_type,target_id'
        ) THEN 'AUTH deploy preflight: AUDIT_LOG 结构化目标索引异常'
    END
));

SELECT COALESCE(@auth_preflight_error, 'AUTH deploy zero-DDL preflight passed') AS auth_preflight_result;
SET @auth_preflight_guard_sql := IF(
    @auth_preflight_error IS NULL,
    'SELECT ''AUTH deploy zero-DDL preflight passed'' AS auth_preflight_guard',
    CONCAT('SELECT * FROM __auth_preflight_stop_', REPLACE(UUID(), '-', ''), '__')
);
PREPARE auth_preflight_guard FROM @auth_preflight_guard_sql;
EXECUTE auth_preflight_guard;
DEALLOCATE PREPARE auth_preflight_guard;

-- 下列过程仅提供第二层 MySQL 条件预检/FAIL FAST 控制流，不触达应用表、索引或业务数据；
-- 上述零 DDL 门已先覆盖角色、资源、目标表/索引与治理审计冲突，任何冲突均不会走到此处。
DROP PROCEDURE IF EXISTS sp_auth_org_profile_group_20260811;

DELIMITER $$
CREATE PROCEDURE sp_auth_org_profile_group_20260811()
BEGIN
    DECLARE v_count BIGINT DEFAULT 0;
    DECLARE v_column_length BIGINT DEFAULT 0;
    DECLARE v_index_columns VARCHAR(512);
    DECLARE v_index_non_unique TINYINT DEFAULT -1;
    DECLARE v_profile_exists TINYINT DEFAULT 0;
    DECLARE v_group_exists TINYINT DEFAULT 0;
    DECLARE v_member_exists TINYINT DEFAULT 0;
    DECLARE v_role_group_exists TINYINT DEFAULT 0;
    DECLARE v_role_code_unique_exists TINYINT DEFAULT 0;
    DECLARE v_resource_identity_unique_exists TINYINT DEFAULT 0;
    DECLARE v_audit_target_index_exists TINYINT DEFAULT 0;
    DECLARE v_temp_resource_table_created TINYINT DEFAULT 0;
    DECLARE v_corp_role_id VARCHAR(50);
    DECLARE v_retail_role_id VARCHAR(50);
    DECLARE v_next_role_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_candidate_role_id VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF v_temp_resource_table_created = 1 THEN
            DROP TEMPORARY TABLE IF EXISTS tmp_auth_org_resources_20260811;
        END IF;
        RESIGNAL;
    END;

    -- ------------------------------------------------------------------------
    -- 1. 基础身份表预检：这两个表不是本脚本创建对象，缺失或结构异常必须先人工修复。
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE 不存在';
    END IF;

    -- 身份表既有列也必须具有完整、可写的定义，不能只因列名存在就允许后续 INSERT 截断。
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
       AND (
            (column_name = 'ROLE_ID' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'ROLE_CODE' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'ROLE_CHNAME' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RECORD_STATUS' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'SYS_CODE' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default = 'PLATFORM')
         OR (column_name = 'CREATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
         OR (column_name = 'CREATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'UPDATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
         OR (column_name = 'UPDATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
       );
    IF v_count <> 10 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE 列定义或长度异常';
    END IF;

    SELECT COALESCE(MAX(character_maximum_length), 0) INTO v_column_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE' AND column_name = 'ROLE_CODE';
    IF v_column_length < 22 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE.ROLE_CODE 长度不足 22';
    END IF;

    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
      INTO v_index_columns
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
       AND index_name = 'PRIMARY' AND non_unique = 0;
    IF COALESCE(v_index_columns, '') <> 'ROLE_ID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE 主键必须为 ROLE_ID';
    END IF;

    -- ROLE_CODE 是业务身份；先拒绝任何历史重复，再补唯一索引，避免不同 ROLE_ID 被误解析。
    SELECT COUNT(*) INTO v_count
      FROM (SELECT ROLE_CODE FROM PT_ROLE GROUP BY ROLE_CODE HAVING COUNT(*) > 1) AS duplicate_role_codes;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE 存在重复 ROLE_CODE';
    END IF;

    -- 既有大屏角色必须沿用数字 ROLE_ID；本脚本绝不把 R_SCREEN_* 角色编码写进 ROLE_ID。
    SELECT COUNT(*) INTO v_count
      FROM PT_ROLE
     WHERE ROLE_CODE REGEXP '^R_SCREEN_'
       AND (ROLE_ID IS NULL OR ROLE_ID NOT REGEXP '^[0-9]+$');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: 既有 R_SCREEN_* 角色 ROLE_ID 必须为纯数字';
    END IF;

    -- 新建角色只从 BIGINT 数值序列派生 ROLE_ID（最多 20 位）；其余待写文字值也在首个 DDL 前校验容量。
    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT 'R_SCREEN_CORP_VIEWER' AS role_code, '对公大屏查看' AS role_chname,
                   'PLATFORM' AS sys_code, '2026-08-11-auth' AS operator_name,
                   '大屏查看能力角色，人员分配须经业务审批' AS remark
            UNION ALL
            SELECT 'R_SCREEN_RETAIL_VIEWER', '零售大屏查看', 'PLATFORM', '2026-08-11-auth',
                   '大屏查看能力角色，人员分配须经业务审批'
           ) AS expected_role
     WHERE CHAR_LENGTH(role_code) > 50
        OR CHAR_LENGTH(role_chname) > 100
        OR CHAR_LENGTH(sys_code) > 10
        OR CHAR_LENGTH(operator_name) > 50
        OR CHAR_LENGTH(remark) > 100;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: 待写 PT_ROLE 值超过目标列长度';
    END IF;

    -- 若已有本脚本目标索引名，必须就是 ROLE_CODE 单列唯一键；不能复用残缺同名索引。
    SELECT COUNT(*) INTO v_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE' AND index_name = 'UK_PT_ROLE_ROLE_CODE';
    IF v_count > 0 THEN
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE' AND index_name = 'UK_PT_ROLE_ROLE_CODE';
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE' AND index_name = 'UK_PT_ROLE_ROLE_CODE';
        IF v_count <> 1 OR v_index_non_unique <> 0 OR COALESCE(v_index_columns, '') <> 'ROLE_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE 同名 ROLE_CODE 唯一索引异常';
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT index_name
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE' AND non_unique = 0
             GROUP BY index_name
            HAVING COUNT(*) = 1 AND MAX(UPPER(column_name)) = 'ROLE_CODE'
           ) AS role_code_unique_indexes;
    SET v_role_code_unique_exists = IF(v_count > 0, 1, 0);

    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_RESOURCE 不存在';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
       AND (
            (column_name = 'RESOURCE_ID' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RESOURCE_URL' AND data_type = 'varchar' AND character_maximum_length = 256 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RESOURCE_METHOD' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'MENU_NAME' AND data_type = 'varchar' AND character_maximum_length = 256 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'MENU_ICON_URL' AND data_type = 'varchar' AND character_maximum_length = 256 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'MENU_RANK_NO' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'ISMENU' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'MENU_ENDFLAG' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default = '0')
         OR (column_name = 'PARENT_RESOURCE_ID' AND data_type = 'varchar' AND character_maximum_length = 60 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'STATUS' AND data_type = 'int' AND is_nullable = 'YES' AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'SYS_CODE' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default = 'PLATFORM')
         OR (column_name = 'CREATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
         OR (column_name = 'CREATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'UPDATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
         OR (column_name = 'UPDATE_USER' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
       );
    IF v_count <> 16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_RESOURCE 列定义或长度异常';
    END IF;

    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
      INTO v_index_columns
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
       AND index_name = 'PRIMARY' AND non_unique = 0;
    IF COALESCE(v_index_columns, '') <> 'RESOURCE_ID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_RESOURCE 主键必须为 RESOURCE_ID';
    END IF;

    -- 历史数据先去重检查；不存在唯一键是可修复结构缺口，会在所有身份预检后补齐。
    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
              FROM PT_RESOURCE
             GROUP BY RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
            HAVING COUNT(*) > 1
           ) AS duplicate_resource_identities;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_RESOURCE 存在重复 URL/METHOD/SYS_CODE';
    END IF;

    -- 若历史环境已占用本脚本的索引名，必须与本期唯一业务键完全一致，不能静默复用残缺索引。
    SELECT COUNT(*) INTO v_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
       AND index_name = 'UK_PT_RESOURCE_URL_METHOD_SYS' AND non_unique = 0;
    IF v_count > 0 THEN
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
           AND index_name = 'UK_PT_RESOURCE_URL_METHOD_SYS';
        IF v_count <> 3 OR COALESCE(v_index_columns, '') <> 'RESOURCE_URL,RESOURCE_METHOD,SYS_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_RESOURCE 同名唯一索引异常';
        END IF;
    ELSE
        SELECT COUNT(*) INTO v_count
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
           AND index_name = 'UK_PT_RESOURCE_URL_METHOD_SYS';
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_RESOURCE 同名索引不是唯一业务键';
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT index_name
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE' AND non_unique = 0
             GROUP BY index_name
            HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                   = 'RESOURCE_URL,RESOURCE_METHOD,SYS_CODE'
           ) AS resource_identity_unique_indexes;
    SET v_resource_identity_unique_exists = IF(v_count > 0, 1, 0);

    -- ------------------------------------------------------------------------
    -- 2. 资源容量与双向身份预检。此段只读，不创建临时表；任何冲突均在首个对象 DDL 前失败。
    --    PT_RESOURCE 沿用 general_ci；UNION 派生值可能继承连接默认 0900_ai_ci，故比较时固定
    --    派生操作数为 general_ci，保持既有资源键的比较语义。
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT 'A_ORG_PROF_LIST' AS resource_id, '/api/admin/org-profiles' AS resource_url, 'GET' AS resource_method, '机构画像-列表' AS menu_name, 'PLATFORM' AS sys_code, '机构本地画像列表' AS remark
            UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', '机构画像-保存', 'PLATFORM', '机构本地画像保存'
            UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', '机构组-列表', 'PLATFORM', '命名机构组列表'
            UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', '机构组-新建', 'PLATFORM', '命名机构组新建'
            UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', '机构组-修改', 'PLATFORM', '命名机构组基本信息修改'
            UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', '机构组-成员覆盖', 'PLATFORM', '命名机构组成员覆盖保存'
            UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', '机构组-角色绑定', 'PLATFORM', '命名机构组角色覆盖保存'
           ) AS expected_resource
     WHERE CHAR_LENGTH(resource_id) > 20
        OR CHAR_LENGTH(resource_url) > 256
        OR CHAR_LENGTH(resource_method) > 10
        OR CHAR_LENGTH(menu_name) > 256
        OR CHAR_LENGTH(sys_code) > 10
        OR CHAR_LENGTH(remark) > 100;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: 待写 PT_RESOURCE 值超过目标列长度';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM PT_RESOURCE existing_resource
      JOIN (
            SELECT 'A_ORG_PROF_LIST' AS resource_id, '/api/admin/org-profiles' AS resource_url, 'GET' AS resource_method, 'PLATFORM' AS sys_code
            UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', 'PLATFORM'
           ) AS expected_resource
        ON existing_resource.RESOURCE_ID = expected_resource.resource_id COLLATE utf8mb4_general_ci
     WHERE NOT (existing_resource.RESOURCE_URL <=> expected_resource.resource_url COLLATE utf8mb4_general_ci
            AND existing_resource.RESOURCE_METHOD <=> expected_resource.resource_method COLLATE utf8mb4_general_ci
            AND existing_resource.SYS_CODE <=> expected_resource.sys_code COLLATE utf8mb4_general_ci);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy conflict: RESOURCE_ID 已对应不同 URL/METHOD/SYS_CODE';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM PT_RESOURCE existing_resource
      JOIN (
            SELECT 'A_ORG_PROF_LIST' AS resource_id, '/api/admin/org-profiles' AS resource_url, 'GET' AS resource_method, 'PLATFORM' AS sys_code
            UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', 'PLATFORM'
            UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', 'PLATFORM'
           ) AS expected_resource
        ON existing_resource.RESOURCE_URL = expected_resource.resource_url COLLATE utf8mb4_general_ci
       AND existing_resource.RESOURCE_METHOD = expected_resource.resource_method COLLATE utf8mb4_general_ci
       AND existing_resource.SYS_CODE = expected_resource.sys_code COLLATE utf8mb4_general_ci
     WHERE existing_resource.RESOURCE_ID <> expected_resource.resource_id COLLATE utf8mb4_general_ci;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy conflict: URL/METHOD/SYS_CODE 已对应不同 RESOURCE_ID';
    END IF;

    -- ------------------------------------------------------------------------
    -- 3. 本期 AUTH 表的残缺表/索引预检。已有表不能被 CREATE TABLE 静默掩盖。
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE';
    SET v_profile_exists = IF(v_count = 1, 1, 0);
    IF v_profile_exists = 1 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE'
           AND column_name IN ('ORG_CODE', 'ORG_NATURE', 'OPERATING_LEVEL', 'OWNER_OPERATING_ORG_CODE',
                               'CITY_CODE', 'CITY_NAME', 'LNG', 'LAT', 'COORD_SYS', 'STATUS', 'VERSION',
                               'CREATED_BY', 'CREATED_TIME', 'UPDATED_BY', 'UPDATED_TIME', 'REMARK');
        IF v_count <> 16 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_PROFILE 为残缺表';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE'
           AND (
                (column_name = 'ORG_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'ORG_NATURE' AND data_type = 'varchar' AND character_maximum_length = 30 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'OPERATING_LEVEL' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'OWNER_OPERATING_ORG_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'CITY_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'CITY_NAME' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'LNG' AND data_type = 'decimal' AND numeric_precision = 10 AND numeric_scale = 6 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'LAT' AND data_type = 'decimal' AND numeric_precision = 10 AND numeric_scale = 6 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'COORD_SYS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
             OR (column_name = 'VERSION' AND data_type = 'int' AND is_nullable = 'NO' AND CAST(column_default AS CHAR) = '0')
             OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
             OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
             OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
           );
        IF v_count <> 16 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_PROFILE 完整列定义或长度异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'PRIMARY';
        IF COALESCE(v_index_columns, '') <> 'ORG_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_PROFILE 主键异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'IDX_PT_ORG_PROFILE_STATUS_LEVEL';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'IDX_PT_ORG_PROFILE_STATUS_LEVEL';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'STATUS,OPERATING_LEVEL' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_PROFILE 状态索引异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'IDX_PT_ORG_PROFILE_OWNER';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'IDX_PT_ORG_PROFILE_OWNER';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'OWNER_OPERATING_ORG_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_PROFILE 归属索引异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'IDX_PT_ORG_PROFILE_CITY';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_PROFILE' AND index_name = 'IDX_PT_ORG_PROFILE_CITY';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'CITY_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_PROFILE 城市索引异常';
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP';
    SET v_group_exists = IF(v_count = 1, 1, 0);
    IF v_group_exists = 1 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP'
           AND column_name IN ('ID', 'GROUP_CODE', 'GROUP_NAME', 'GROUP_PURPOSE', 'STATUS', 'VERSION',
                               'CREATED_BY', 'CREATED_TIME', 'UPDATED_BY', 'UPDATED_TIME', 'REMARK');
        IF v_count <> 11 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP 为残缺表';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP'
           AND (
                (column_name = 'ID' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
             OR (column_name = 'GROUP_CODE' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'GROUP_NAME' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'GROUP_PURPOSE' AND data_type = 'varchar' AND character_maximum_length = 30 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
             OR (column_name = 'VERSION' AND data_type = 'int' AND is_nullable = 'NO' AND CAST(column_default AS CHAR) = '0')
             OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
             OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
             OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
           );
        IF v_count <> 11 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP 完整列定义或长度异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP' AND index_name = 'PRIMARY';
        IF COALESCE(v_index_columns, '') <> 'ID' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP 主键异常';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP' AND index_name = 'UK_PT_ORG_GROUP_CODE' AND non_unique = 0;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP' AND index_name = 'UK_PT_ORG_GROUP_CODE';
        IF v_count <> 1 OR COALESCE(v_index_columns, '') <> 'GROUP_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP 业务唯一键异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP' AND index_name = 'IDX_PT_ORG_GROUP_STATUS';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP' AND index_name = 'IDX_PT_ORG_GROUP_STATUS';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'STATUS' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP 状态索引异常';
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER';
    SET v_member_exists = IF(v_count = 1, 1, 0);
    IF v_member_exists = 1 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER'
           AND column_name IN ('ID', 'GROUP_CODE', 'ORG_CODE', 'STATUS', 'CREATED_BY', 'CREATED_TIME',
                               'UPDATED_BY', 'UPDATED_TIME', 'REMARK');
        IF v_count <> 9 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 为残缺表';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER'
           AND (
                (column_name = 'ID' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
             OR (column_name = 'GROUP_CODE' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'ORG_CODE' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
             OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
             OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
             OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
           );
        IF v_count <> 9 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 完整列定义或长度异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'PRIMARY';
        IF COALESCE(v_index_columns, '') <> 'ID' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 主键异常';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'UK_PT_ORG_GROUP_MEMBER' AND non_unique = 0;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'UK_PT_ORG_GROUP_MEMBER';
        IF v_count <> 2 OR COALESCE(v_index_columns, '') <> 'GROUP_CODE,ORG_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 业务唯一键异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'IDX_PT_ORG_GROUP_MEMBER_GROUP';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'IDX_PT_ORG_GROUP_MEMBER_GROUP';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'GROUP_CODE,STATUS' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 组索引异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'IDX_PT_ORG_GROUP_MEMBER_ORG';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP_MEMBER' AND index_name = 'IDX_PT_ORG_GROUP_MEMBER_ORG';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'ORG_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ORG_GROUP_MEMBER 机构索引异常';
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP';
    SET v_role_group_exists = IF(v_count = 1, 1, 0);
    IF v_role_group_exists = 1 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP'
           AND column_name IN ('ID', 'ROLE_ID', 'GROUP_CODE', 'STATUS', 'CREATED_BY', 'CREATED_TIME',
                               'UPDATED_BY', 'UPDATED_TIME', 'REMARK');
        IF v_count <> 9 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 为残缺表';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP'
           AND (
                (column_name = 'ID' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
             OR (column_name = 'ROLE_ID' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'GROUP_CODE' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO' AND column_default IS NULL)
             OR (column_name = 'STATUS' AND data_type = 'varchar' AND character_maximum_length = 10 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
             OR (column_name = 'CREATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'CREATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
             OR (column_name = 'UPDATED_BY' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'UPDATED_TIME' AND data_type = 'datetime' AND is_nullable = 'NO' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP' AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
             OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
           );
        IF v_count <> 9 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 完整列定义或长度异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'PRIMARY';
        IF COALESCE(v_index_columns, '') <> 'ID' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 主键异常';
        END IF;
        SELECT COUNT(*) INTO v_count FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'UK_PT_ROLE_ORG_GROUP' AND non_unique = 0;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'UK_PT_ROLE_ORG_GROUP';
        IF v_count <> 2 OR COALESCE(v_index_columns, '') <> 'ROLE_ID,GROUP_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 业务唯一键异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'IDX_PT_ROLE_ORG_GROUP_GROUP';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'IDX_PT_ROLE_ORG_GROUP_GROUP';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'GROUP_CODE,STATUS' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 组索引异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'IDX_PT_ROLE_ORG_GROUP_ROLE';
        SELECT COALESCE(MIN(non_unique), -1) INTO v_index_non_unique
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_ORG_GROUP' AND index_name = 'IDX_PT_ROLE_ORG_GROUP_ROLE';
        IF v_index_non_unique <> 1 OR COALESCE(v_index_columns, '') <> 'ROLE_ID,STATUS' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: PT_ROLE_ORG_GROUP 角色索引异常';
        END IF;
    END IF;

    -- ------------------------------------------------------------------------
    -- 4. 治理审计表预检。AUDIT_LOG 是治理模块持有的既有持久化表；这里只做本期字段扩展。
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG 不存在，不能降级为 logger 审计';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
       AND column_name IN ('id', 'trace_id', 'emp_id', 'emp_name', 'biz_type', 'biz_action', 'resource_url',
                           'request_method', 'request_params', 'response_status', 'error_msg', 'ip_address',
                           'user_agent', 'execution_time', 'reason', 'created_time');
    IF v_count <> 16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG 基础结构残缺';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG'
       AND (
            (column_name = 'id' AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'trace_id' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'emp_id' AND data_type = 'varchar' AND character_maximum_length = 32 AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'emp_name' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'biz_type' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'biz_action' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'resource_url' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'request_method' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'request_params' AND data_type = 'text' AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'response_status' AND data_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'error_msg' AND data_type = 'text' AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'ip_address' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'user_agent' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'execution_time' AND data_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'reason' AND data_type = 'varchar' AND character_maximum_length = 500 AND is_nullable = 'YES' AND column_default IS NULL)
         OR (column_name = 'created_time' AND data_type = 'datetime' AND is_nullable = 'YES' AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
       );
    IF v_count <> 16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG 完整基础列定义或长度异常';
    END IF;

    -- 已有同名结构化字段时，先校验完整列定义，拒绝静默写入截断或错误类型的列。
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'target_type';
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'target_type'
           AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'YES' AND column_default IS NULL;
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG.target_type 完整列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'target_id';
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'target_id'
           AND data_type = 'varchar' AND character_maximum_length = 128 AND is_nullable = 'YES' AND column_default IS NULL;
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG.target_id 完整列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'before_snapshot';
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'before_snapshot'
           AND data_type = 'text' AND column_type = 'text' AND character_maximum_length = 65535 AND is_nullable = 'YES'
           AND column_default IS NULL AND extra = '';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG.before_snapshot 完整列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'after_snapshot';
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'after_snapshot'
           AND data_type = 'text' AND column_type = 'text' AND character_maximum_length = 65535 AND is_nullable = 'YES'
           AND column_default IS NULL AND extra = '';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG.after_snapshot 完整列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'added_items';
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'added_items'
           AND data_type = 'text' AND column_type = 'text' AND character_maximum_length = 65535 AND is_nullable = 'YES'
           AND column_default IS NULL AND extra = '';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG.added_items 完整列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'removed_items';
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'removed_items'
           AND data_type = 'text' AND column_type = 'text' AND character_maximum_length = 65535 AND is_nullable = 'YES'
           AND column_default IS NULL AND extra = '';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG.removed_items 完整列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND index_name = 'IDX_AUDIT_LOG_TARGET';
    SET v_audit_target_index_exists = IF(v_count > 0, 1, 0);
    IF v_audit_target_index_exists = 1 THEN
        SELECT COALESCE(MIN(non_unique), -1) INTO v_count
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND index_name = 'IDX_AUDIT_LOG_TARGET';
        SELECT GROUP_CONCAT(LOWER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_index_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND index_name = 'IDX_AUDIT_LOG_TARGET';
        IF v_count <> 1 OR COALESCE(v_index_columns, '') <> 'target_type,target_id' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy preflight: AUDIT_LOG 结构化目标索引异常';
        END IF;
    END IF;

    -- ------------------------------------------------------------------------
    -- 5. 所有只读预检通过后才开始任何应用对象 DDL。临时资源表仅作后续严格 INSERT 的载体，
    --    并显式沿用既有 PT_RESOURCE 的 general_ci，避免继承 MySQL 8 默认 0900_ai_ci。
    -- ------------------------------------------------------------------------
    DROP TEMPORARY TABLE IF EXISTS tmp_auth_org_resources_20260811;
    CREATE TEMPORARY TABLE tmp_auth_org_resources_20260811 (
        resource_id     VARCHAR(20)  NOT NULL,
        resource_url    VARCHAR(256) NOT NULL,
        resource_method VARCHAR(10)  NOT NULL,
        menu_name       VARCHAR(256) NOT NULL,
        sys_code        VARCHAR(10)  NOT NULL,
        remark          VARCHAR(100) NULL,
        PRIMARY KEY (resource_id),
        UNIQUE KEY uk_tmp_auth_org_resource_identity (resource_url, resource_method, sys_code)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
    SET v_temp_resource_table_created = 1;

    INSERT INTO tmp_auth_org_resources_20260811
        (resource_id, resource_url, resource_method, menu_name, sys_code, remark)
    VALUES
        ('A_ORG_PROF_LIST',  '/api/admin/org-profiles',             'GET',  '机构画像-列表',   'PLATFORM', '机构本地画像列表'),
        ('A_ORG_PROF_EDIT',  '/api/admin/org-profiles/*',           'PUT',  '机构画像-保存',   'PLATFORM', '机构本地画像保存'),
        ('A_ORG_GRP_LIST',   '/api/admin/org-groups',               'GET',  '机构组-列表',     'PLATFORM', '命名机构组列表'),
        ('A_ORG_GRP_CREATE', '/api/admin/org-groups',               'POST', '机构组-新建',     'PLATFORM', '命名机构组新建'),
        ('A_ORG_GRP_EDIT',   '/api/admin/org-groups/*',             'PUT',  '机构组-修改',     'PLATFORM', '命名机构组基本信息修改'),
        ('A_ORG_GRP_MEM',    '/api/admin/org-groups/*/members',     'PUT',  '机构组-成员覆盖', 'PLATFORM', '命名机构组成员覆盖保存'),
        ('A_ORG_GRP_ROLE',   '/api/admin/org-groups/*/roles',       'PUT',  '机构组-角色绑定', 'PLATFORM', '命名机构组角色覆盖保存');

    -- 不存在的表创建，存在但残缺的表已在上方被拒绝。
    IF v_profile_exists = 0 THEN
        CREATE TABLE PT_ORG_PROFILE (
            ORG_CODE                  VARCHAR(20)  NOT NULL COMMENT '机构编码，对应 EXT_ORG_INFO.ORG_CODE',
            ORG_NATURE                VARCHAR(30)  NOT NULL COMMENT 'DEPARTMENT/LOCAL_BRANCH/SECONDARY_BRANCH/OUTLET/OTHER',
            OPERATING_LEVEL           VARCHAR(20)  NOT NULL COMMENT 'PRIMARY/SUBORDINATE/NONE',
            OWNER_OPERATING_ORG_CODE  VARCHAR(20)  NULL COMMENT '下属机构归属的一级经营机构',
            CITY_CODE                 VARCHAR(20)  NULL COMMENT '城市/行政区划编码',
            CITY_NAME                 VARCHAR(100) NULL COMMENT '城市名称',
            LNG                       DECIMAL(10,6) NULL COMMENT '经度，坐标系固定 GCJ02',
            LAT                       DECIMAL(10,6) NULL COMMENT '纬度，坐标系固定 GCJ02',
            COORD_SYS                 VARCHAR(10)  NULL COMMENT '坐标系，本期仅允许 GCJ02',
            STATUS                    VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
            VERSION                   INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
            CREATED_BY                VARCHAR(50)  NULL COMMENT '创建人',
            CREATED_TIME              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            UPDATED_BY                VARCHAR(50)  NULL COMMENT '更新人',
            UPDATED_TIME              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            REMARK                    VARCHAR(500) NULL COMMENT '经营口径说明',
            PRIMARY KEY (ORG_CODE),
            KEY IDX_PT_ORG_PROFILE_STATUS_LEVEL (STATUS, OPERATING_LEVEL),
            KEY IDX_PT_ORG_PROFILE_OWNER (OWNER_OPERATING_ORG_CODE),
            KEY IDX_PT_ORG_PROFILE_CITY (CITY_CODE)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构本地经营画像（不回写外部机构表）';
    END IF;

    IF v_group_exists = 0 THEN
        CREATE TABLE PT_ORG_GROUP (
            ID             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
            GROUP_CODE     VARCHAR(64)  NOT NULL COMMENT '稳定业务编码',
            GROUP_NAME     VARCHAR(100) NOT NULL COMMENT '机构组名称',
            GROUP_PURPOSE  VARCHAR(30)  NOT NULL COMMENT '本期固定 REPORT_SCREEN',
            STATUS         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
            VERSION        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
            CREATED_BY     VARCHAR(50)  NULL COMMENT '创建人',
            CREATED_TIME   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            UPDATED_BY     VARCHAR(50)  NULL COMMENT '更新人',
            UPDATED_TIME   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            REMARK         VARCHAR(500) NULL COMMENT '机构组口径说明',
            PRIMARY KEY (ID),
            UNIQUE KEY UK_PT_ORG_GROUP_CODE (GROUP_CODE),
            KEY IDX_PT_ORG_GROUP_STATUS (STATUS)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='命名机构组';
    END IF;

    IF v_member_exists = 0 THEN
        CREATE TABLE PT_ORG_GROUP_MEMBER (
            ID           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
            GROUP_CODE   VARCHAR(64)  NOT NULL COMMENT '机构组编码',
            ORG_CODE     VARCHAR(20)  NOT NULL COMMENT '直接成员机构编码',
            STATUS       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
            CREATED_BY   VARCHAR(50)  NULL COMMENT '创建人',
            CREATED_TIME DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            UPDATED_BY   VARCHAR(50)  NULL COMMENT '更新人',
            UPDATED_TIME DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            REMARK       VARCHAR(500) NULL COMMENT '成员口径说明',
            PRIMARY KEY (ID),
            UNIQUE KEY UK_PT_ORG_GROUP_MEMBER (GROUP_CODE, ORG_CODE),
            KEY IDX_PT_ORG_GROUP_MEMBER_GROUP (GROUP_CODE, STATUS),
            KEY IDX_PT_ORG_GROUP_MEMBER_ORG (ORG_CODE)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='命名机构组直接成员';
    END IF;

    IF v_role_group_exists = 0 THEN
        CREATE TABLE PT_ROLE_ORG_GROUP (
            ID           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
            ROLE_ID      VARCHAR(50)  NOT NULL COMMENT 'PT_ROLE.ROLE_ID',
            GROUP_CODE   VARCHAR(64)  NOT NULL COMMENT 'PT_ORG_GROUP.GROUP_CODE',
            STATUS       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
            CREATED_BY   VARCHAR(50)  NULL COMMENT '创建人',
            CREATED_TIME DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            UPDATED_BY   VARCHAR(50)  NULL COMMENT '更新人',
            UPDATED_TIME DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            REMARK       VARCHAR(500) NULL COMMENT '授权说明',
            PRIMARY KEY (ID),
            UNIQUE KEY UK_PT_ROLE_ORG_GROUP (ROLE_ID, GROUP_CODE),
            KEY IDX_PT_ROLE_ORG_GROUP_GROUP (GROUP_CODE, STATUS),
            KEY IDX_PT_ROLE_ORG_GROUP_ROLE (ROLE_ID, STATUS)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-命名机构组授权';
    END IF;

    -- AUDIT_LOG 的本期新增列：只有缺列才追加；已有同名列已在预检阶段验证容量。
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'target_type';
    IF v_count = 0 THEN
        ALTER TABLE AUDIT_LOG ADD COLUMN target_type VARCHAR(100) NULL COMMENT '结构化审计目标类型' AFTER reason;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'target_id';
    IF v_count = 0 THEN
        ALTER TABLE AUDIT_LOG ADD COLUMN target_id VARCHAR(128) NULL COMMENT '结构化审计目标标识' AFTER target_type;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'before_snapshot';
    IF v_count = 0 THEN
        ALTER TABLE AUDIT_LOG ADD COLUMN before_snapshot TEXT NULL COMMENT '变更前 JSON 快照' AFTER target_id;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'after_snapshot';
    IF v_count = 0 THEN
        ALTER TABLE AUDIT_LOG ADD COLUMN after_snapshot TEXT NULL COMMENT '变更后 JSON 快照' AFTER before_snapshot;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'added_items';
    IF v_count = 0 THEN
        ALTER TABLE AUDIT_LOG ADD COLUMN added_items TEXT NULL COMMENT '新增项 JSON 数组' AFTER after_snapshot;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'AUDIT_LOG' AND column_name = 'removed_items';
    IF v_count = 0 THEN
        ALTER TABLE AUDIT_LOG ADD COLUMN removed_items TEXT NULL COMMENT '移除项 JSON 数组' AFTER added_items;
    END IF;
    IF v_audit_target_index_exists = 0 THEN
        ALTER TABLE AUDIT_LOG ADD KEY IDX_AUDIT_LOG_TARGET (target_type, target_id);
    END IF;

    -- ROLE_CODE 已在预检中确认为全局无重复；补齐业务唯一键后才按代码解析/创建角色。
    IF v_role_code_unique_exists = 0 THEN
        ALTER TABLE PT_ROLE ADD UNIQUE KEY UK_PT_ROLE_ROLE_CODE (ROLE_CODE);
    END IF;

    -- 去重、双向身份预检均已完成后才补业务唯一键；二次执行不会产生任何 DDL。
    IF v_resource_identity_unique_exists = 0 THEN
        ALTER TABLE PT_RESOURCE ADD UNIQUE KEY UK_PT_RESOURCE_URL_METHOD_SYS
            (RESOURCE_URL, RESOURCE_METHOD, SYS_CODE);
    END IF;

    -- ------------------------------------------------------------------------
    -- 6. 角色按 ROLE_CODE 解析当前 ID。只有 ROLE_CODE 缺失时分配新的纯数字 ROLE_ID。
    -- ------------------------------------------------------------------------
    SELECT COUNT(*), MIN(ROLE_ID) INTO v_count, v_corp_role_id
      FROM PT_ROLE WHERE ROLE_CODE = 'R_SCREEN_CORP_VIEWER';
    IF v_count > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy conflict: R_SCREEN_CORP_VIEWER 重复';
    END IF;
    IF v_count = 0 THEN
        SELECT COALESCE(MAX(CAST(ROLE_ID AS UNSIGNED)), 0) + 1 INTO v_next_role_id
          FROM PT_ROLE WHERE ROLE_ID REGEXP '^[0-9]+$';
        SET v_candidate_role_id = CAST(v_next_role_id AS CHAR);
        IF v_candidate_role_id NOT REGEXP '^[0-9]+$' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy internal: 新 ROLE_ID 必须为数字';
        END IF;
        SELECT COUNT(*) INTO v_count FROM PT_ROLE WHERE ROLE_ID = v_candidate_role_id;
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy conflict: 新 ROLE_ID 主键已存在';
        END IF;
        INSERT INTO PT_ROLE
            (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
        VALUES
            (v_candidate_role_id, 'R_SCREEN_CORP_VIEWER', '对公大屏查看', 0, 'PLATFORM', NOW(),
             '2026-08-11-auth', NOW(), '2026-08-11-auth', '大屏查看能力角色，人员分配须经业务审批');
        SET v_corp_role_id = v_candidate_role_id;
    END IF;

    SELECT COUNT(*), MIN(ROLE_ID) INTO v_count, v_retail_role_id
      FROM PT_ROLE WHERE ROLE_CODE = 'R_SCREEN_RETAIL_VIEWER';
    IF v_count > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy conflict: R_SCREEN_RETAIL_VIEWER 重复';
    END IF;
    IF v_count = 0 THEN
        SELECT COALESCE(MAX(CAST(ROLE_ID AS UNSIGNED)), 0) + 1 INTO v_next_role_id
          FROM PT_ROLE WHERE ROLE_ID REGEXP '^[0-9]+$';
        SET v_candidate_role_id = CAST(v_next_role_id AS CHAR);
        IF v_candidate_role_id NOT REGEXP '^[0-9]+$' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy internal: 新 ROLE_ID 必须为数字';
        END IF;
        SELECT COUNT(*) INTO v_count FROM PT_ROLE WHERE ROLE_ID = v_candidate_role_id;
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUTH deploy conflict: 新 ROLE_ID 主键已存在';
        END IF;
        INSERT INTO PT_ROLE
            (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
        VALUES
            (v_candidate_role_id, 'R_SCREEN_RETAIL_VIEWER', '零售大屏查看', 0, 'PLATFORM', NOW(),
             '2026-08-11-auth', NOW(), '2026-08-11-auth', '大屏查看能力角色，人员分配须经业务审批');
        SET v_retail_role_id = v_candidate_role_id;
    END IF;

    -- 资源 preflight 已双向确认；严格 INSERT 仅补缺失 RESOURCE_ID，二次执行零变化。
    INSERT INTO PT_RESOURCE
        (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG,
         PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
    SELECT expected_resource.resource_id, expected_resource.resource_url, expected_resource.resource_method,
           expected_resource.menu_name, 0, 0, '0', NULL, 0, expected_resource.sys_code,
           NOW(), '2026-08-11-auth', NOW(), '2026-08-11-auth', expected_resource.remark
      FROM tmp_auth_org_resources_20260811 expected_resource
      LEFT JOIN PT_RESOURCE existing_resource
        ON existing_resource.RESOURCE_ID = expected_resource.resource_id
     WHERE existing_resource.RESOURCE_ID IS NULL;

    -- 成功后仅输出身份和数量，便于把 yiti_test 的前后 diff 归档。
    SELECT ROLE_ID, ROLE_CODE, ROLE_CHNAME
      FROM PT_ROLE
     WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER', 'R_SCREEN_RETAIL_VIEWER')
     ORDER BY ROLE_CODE;
    SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
      FROM PT_RESOURCE
     WHERE RESOURCE_ID IN ('A_ORG_PROF_LIST', 'A_ORG_PROF_EDIT', 'A_ORG_GRP_LIST', 'A_ORG_GRP_CREATE',
                           'A_ORG_GRP_EDIT', 'A_ORG_GRP_MEM', 'A_ORG_GRP_ROLE')
     ORDER BY RESOURCE_ID;
    SELECT 'PT_ORG_PROFILE' AS table_name, COUNT(*) AS row_count FROM PT_ORG_PROFILE
    UNION ALL SELECT 'PT_ORG_GROUP', COUNT(*) FROM PT_ORG_GROUP
    UNION ALL SELECT 'PT_ORG_GROUP_MEMBER', COUNT(*) FROM PT_ORG_GROUP_MEMBER
    UNION ALL SELECT 'PT_ROLE_ORG_GROUP', COUNT(*) FROM PT_ROLE_ORG_GROUP;

    DROP TEMPORARY TABLE IF EXISTS tmp_auth_org_resources_20260811;
    SET v_temp_resource_table_created = 0;
END$$
DELIMITER ;

CALL sp_auth_org_profile_group_20260811();
DROP PROCEDURE sp_auth_org_profile_group_20260811;
