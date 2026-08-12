-- ============================================================================
-- REPORT：命名机构组壳与复合地图大屏草稿种子
-- 日期：2026-08-11
--
-- 前置顺序：
--   1) 2026-08-11-auth-org-profile-group.sql：由 auth 以数字 ROLE_ID 创建/解析
--      R_SCREEN_CORP_VIEWER、R_SCREEN_RETAIL_VIEWER；本脚本会对这两个 ROLE_CODE 精确判重；
--   2) 2026-08-11-screen-scope-map-align.sql：创建 report 扩展结构；
--   3) 本脚本。
--
-- 本脚本绝不新增 PT_ROLE、绝不硬编码查看角色的数值 ID、绝不静默忽略重复。
-- 若角色缺失、ROLE_CODE 重复或 ROLE_ID 非数字，一律 SIGNAL 并由 auth 权限迁移修复。
-- 任何 DDL/DML 前先对获批准的隔离目标做只读盘点；克隆、覆盖、清空、备份及执行均须另获
-- 明确授权。获准隔离验证后保留双跑/结构数据 diff 证据，报告完整证据后还须再次明确确认
-- 才能对 yiti 执行。本 seed 不写 PT_ROLE_RESOURCE，AR_SAVE 的默认 SYS_ADMIN 精确授权
-- 仅由 align 脚本处理，绝不从 R_RPT_SQL_EXEC 推导。
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
-- SOURCE docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql;
-- ---------------------------------------------------------------------------

-- ============================================================================
-- 外置只读预检（强制）：必须在本文件任何 DROP/CREATE PROCEDURE、INSERT 或 CALL 前，在最终
-- 获权写会话中作为只读阶段逐条运行。任何 violation 行、或结构清单与下列预期不一致都必须停止；
-- 不得只在另一只读连接完成后再换写连接执行。隔离验证与生产执行须分别获得批准，审批 manifest
-- 中的目标实例和 schema 是唯一机器判定依据，不得通过固定逻辑库名推断授权；任何获批目标仍须
-- 重新完成本段同一写会话内的预检。本段覆盖过程内全部 SIGNAL 条件，并额外盘点 seed 会写入的
-- PT_ORG_GROUP/RPT_SCREEN/RPT_SCREEN_ACCESS_ROLE 目标列、长度、默认值、索引和业务键冲突；
-- 过程内 SIGNAL 检查保留为第二层，不能替代外置门禁。
-- ============================================================================
-- 1) 基础对象与完整结构盘点。目标列：PT_ORG_GROUP.GROUP_CODE VARCHAR(64) NOT NULL、
-- GROUP_NAME VARCHAR(100) NOT NULL、GROUP_PURPOSE VARCHAR(30) NOT NULL、STATUS VARCHAR(10)
-- NOT NULL DEFAULT ACTIVE、VERSION INT NOT NULL DEFAULT 0、REMARK VARCHAR(500) NULL；
-- RPT_SCREEN/ACCESS_ROLE 的定义必须与 align 脚本完全一致。
SELECT table_name,table_type FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN ('PT_ROLE','PT_ORG_GROUP','RPT_SCREEN','RPT_SCREEN_ACCESS_ROLE')
 ORDER BY table_name;
SELECT table_name,column_name,ordinal_position,data_type,character_maximum_length,is_nullable,column_default,
       extra,generation_expression
  FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN ('PT_ROLE','PT_ORG_GROUP','RPT_SCREEN','RPT_SCREEN_ACCESS_ROLE')
 ORDER BY table_name,ordinal_position;
SELECT table_name,index_name,non_unique,seq_in_index,column_name
  FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name IN ('PT_ROLE','PT_ORG_GROUP','RPT_SCREEN','RPT_SCREEN_ACCESS_ROLE')
 ORDER BY table_name,index_name,seq_in_index;
SELECT 'REPORT seed external preflight: 前置表缺失' AS violation,expected.table_name
  FROM (SELECT 'PT_ROLE' table_name UNION ALL SELECT 'PT_ORG_GROUP' UNION ALL SELECT 'RPT_SCREEN'
        UNION ALL SELECT 'RPT_SCREEN_ACCESS_ROLE') expected
 WHERE NOT EXISTS (SELECT 1 FROM information_schema.tables t
                    WHERE t.table_schema=DATABASE() AND t.table_name=expected.table_name);
SELECT 'REPORT seed external preflight: RPT_SCREEN 缺少本期列' AS violation
  FROM DUAL WHERE (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='RPT_SCREEN' AND UPPER(column_name) IN ('ID','SCREEN_CODE','SCREEN_NAME','VIEW_LEVEL','BIZ_LINE',
       'ORG_SCOPE_MODE','ORG_GROUP_CODE','STATUS','CANVAS_STYLE_JSON','CANVAS_DRAFT_JSON','CANVAS_PUBLISHED_JSON',
       'CANVAS_VERSION','PUBLISH_STATUS','UPDATED_BY','DELETED','ACTIVE_SCREEN_CODE'))<>16;
SELECT 'REPORT seed external preflight: active_screen_code/唯一索引定义异常' AS violation
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='RPT_SCREEN' AND column_name='active_screen_code' AND data_type='varchar'
       AND character_maximum_length=64 AND is_nullable='YES' AND UPPER(extra) LIKE '%STORED GENERATED%'
       AND UPPER(generation_expression) LIKE '%SCREEN_CODE%' AND UPPER(generation_expression) LIKE '%DELETED%')
    OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
       AND index_name='uk_rpt_screen_active_code' AND non_unique=0 GROUP BY index_name
       HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='ACTIVE_SCREEN_CODE');
SELECT 'REPORT seed external preflight: RPT_SCREEN_ACCESS_ROLE 列/唯一键/重复行异常' AS violation
  FROM DUAL WHERE (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='RPT_SCREEN_ACCESS_ROLE' AND UPPER(column_name) IN
       ('SCREEN_ID','ROLE_CODE','STATUS','CREATED_BY','CREATED_TIME','UPDATED_BY','UPDATED_TIME'))<>7
    OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
       AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='uk_rpt_screen_access_role' AND non_unique=0
       GROUP BY index_name HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='SCREEN_ID,ROLE_CODE')
    OR EXISTS (SELECT 1 FROM (SELECT screen_id,role_code FROM RPT_SCREEN_ACCESS_ROLE
                               GROUP BY screen_id,role_code HAVING COUNT(*)>1) duplicated_access_role);
SELECT 'REPORT seed external preflight: PT_ORG_GROUP 目标列/长度/默认值/唯一键异常' AS violation
  FROM DUAL WHERE (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='PT_ORG_GROUP' AND ((column_name='GROUP_CODE' AND data_type='varchar' AND character_maximum_length=64 AND is_nullable='NO')
       OR (column_name='GROUP_NAME' AND data_type='varchar' AND character_maximum_length=100 AND is_nullable='NO')
       OR (column_name='GROUP_PURPOSE' AND data_type='varchar' AND character_maximum_length=30 AND is_nullable='NO')
       OR (column_name='STATUS' AND data_type='varchar' AND character_maximum_length=10 AND is_nullable='NO' AND column_default='ACTIVE')
       OR (column_name='VERSION' AND data_type='int' AND is_nullable='NO' AND CAST(column_default AS CHAR)='0')
       OR (column_name='CREATED_BY' AND data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES')
       OR (column_name='UPDATED_BY' AND data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES')
       OR (column_name='REMARK' AND data_type='varchar' AND character_maximum_length=500 AND is_nullable='YES')))<>8
    OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='PT_ORG_GROUP'
       AND index_name='UK_PT_ORG_GROUP_CODE' AND non_unique=0 GROUP BY index_name
       HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='GROUP_CODE');

-- 2) 过程内全部身份 SIGNAL 的外置镜像：角色精确判重/数字 ID、业务键重复、已有对象语义冲突。
SELECT 'REPORT seed external preflight: 查看角色缺失、ROLE_CODE 不唯一或 ROLE_ID 非数字' AS violation
  FROM DUAL WHERE (SELECT COUNT(*) FROM PT_ROLE WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER'))<>2
    OR EXISTS (SELECT 1 FROM (SELECT ROLE_CODE FROM PT_ROLE WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER')
                               GROUP BY ROLE_CODE HAVING COUNT(*)<>1) duplicate_role_code)
    OR EXISTS (SELECT 1 FROM PT_ROLE WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER')
                 AND CAST(ROLE_ID AS CHAR) NOT REGEXP '^[0-9]+$');
SELECT 'REPORT seed external preflight: 目标 screen_code 重复' AS violation,screen_code
  FROM RPT_SCREEN WHERE deleted=0 AND screen_code IN ('SCR_CORP_OVERVIEW','SCR_RETAIL_OVERVIEW')
 GROUP BY screen_code HAVING COUNT(*)>1;
SELECT 'REPORT seed external preflight: 目标 group_code 重复' AS violation,group_code
  FROM PT_ORG_GROUP WHERE group_code IN ('ORG_GRP_PRIMARY_OPERATING_UNITS','ORG_GRP_CORP_DEPARTMENTS')
 GROUP BY group_code HAVING COUNT(*)>1;
SELECT 'REPORT seed external preflight: 同 group_code 已绑定不同业务身份' AS violation,group_code
  FROM PT_ORG_GROUP
 WHERE (group_code='ORG_GRP_PRIMARY_OPERATING_UNITS' AND (group_name<>'全辖一级经营机构' OR group_purpose<>'REPORT_SCREEN'))
    OR (group_code='ORG_GRP_CORP_DEPARTMENTS' AND (group_name<>'对公部门组' OR group_purpose<>'REPORT_SCREEN'));
SELECT 'REPORT seed external preflight: 同 screen_code 已绑定不同范围身份' AS violation,screen_code
  FROM RPT_SCREEN WHERE deleted=0 AND ((screen_code='SCR_CORP_OVERVIEW' AND
       (biz_line<>'CORP' OR org_scope_mode<>'NAMED_GROUP' OR org_group_code<>'ORG_GRP_CORP_DEPARTMENTS'))
    OR (screen_code='SCR_RETAIL_OVERVIEW' AND
       (biz_line<>'RETAIL' OR org_scope_mode<>'NAMED_GROUP' OR org_group_code<>'ORG_GRP_PRIMARY_OPERATING_UNITS')));

-- 3) 待插入文字和 JSON 长度/身份清单：任一超出上方目标列即停止；不新增 PT_ROLE/ROLE_RESOURCE。
WITH expected_seed_value AS (
    SELECT 'PT_ORG_GROUP.GROUP_CODE' target_col,'ORG_GRP_PRIMARY_OPERATING_UNITS' value_text,64 max_len
    UNION ALL SELECT 'PT_ORG_GROUP.GROUP_CODE','ORG_GRP_CORP_DEPARTMENTS',64
    UNION ALL SELECT 'PT_ORG_GROUP.GROUP_NAME','全辖一级经营机构',100
    UNION ALL SELECT 'PT_ORG_GROUP.GROUP_NAME','对公部门组',100
    UNION ALL SELECT 'RPT_SCREEN.SCREEN_CODE','SCR_CORP_OVERVIEW',64
    UNION ALL SELECT 'RPT_SCREEN.SCREEN_CODE','SCR_RETAIL_OVERVIEW',64
    UNION ALL SELECT 'RPT_SCREEN.ORG_GROUP_CODE','ORG_GRP_PRIMARY_OPERATING_UNITS',64
    UNION ALL SELECT 'RPT_SCREEN.ORG_GROUP_CODE','ORG_GRP_CORP_DEPARTMENTS',64
)
SELECT 'REPORT seed external preflight: 待插入值超出目标列长度' AS violation,target_col,value_text
  FROM expected_seed_value WHERE CHAR_LENGTH(value_text)>max_len;

-- 4) 机器硬停止门：不是人工提示。下列 CASE 覆盖过程内所有 SIGNAL（并含 PT_ORG_GROUP
-- 目标列定义）；若任一前置条件失败，PREPARE UUID 不存在表查询会在首个 DDL 前稳定报错。
-- 禁止 mysql --force 或任何吞错执行器，否则会破坏 fail-close。
SET @rpt_seed_preflight_error := (
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
            THEN 'REPORT seed target identity guard: approval variables missing or invalid'
        WHEN @@server_uuid IS NULL
             OR CHAR_LENGTH(TRIM(@@server_uuid)) = 0
             OR @@hostname IS NULL
             OR CHAR_LENGTH(TRIM(@@hostname)) = 0
             OR @@port IS NULL
             OR CHAR_LENGTH(TRIM(CAST(@@port AS CHAR))) = 0
             OR DATABASE() IS NULL
             OR CHAR_LENGTH(TRIM(DATABASE())) = 0
            THEN 'REPORT seed target identity guard: actual session identity missing'
        WHEN BINARY @approved_target_server_uuid <> BINARY @@server_uuid
             OR BINARY @approved_target_hostname <> BINARY @@hostname
             OR BINARY @approved_target_port <> BINARY CAST(@@port AS CHAR)
             OR BINARY @approved_target_schema <> BINARY DATABASE()
            THEN 'REPORT seed target identity guard: approval target does not exactly match current write session'
        WHEN (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
              AND table_name IN ('PT_ROLE','PT_ORG_GROUP','RPT_SCREEN','RPT_SCREEN_ACCESS_ROLE'))<>4
            THEN 'REPORT seed zero-DDL preflight: 前置表缺失'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND UPPER(column_name) IN ('ID','SCREEN_CODE','SCREEN_NAME','VIEW_LEVEL','BIZ_LINE','ORG_SCOPE_MODE',
              'ORG_GROUP_CODE','STATUS','CANVAS_STYLE_JSON','CANVAS_DRAFT_JSON','CANVAS_PUBLISHED_JSON','CANVAS_VERSION',
              'PUBLISH_STATUS','UPDATED_BY','DELETED','ACTIVE_SCREEN_CODE'))<>16
            THEN 'REPORT seed zero-DDL preflight: RPT_SCREEN 缺少本期列'
        WHEN NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND column_name='active_screen_code' AND data_type='varchar' AND character_maximum_length=64
              AND is_nullable='YES' AND UPPER(extra) LIKE '%STORED GENERATED%'
              AND UPPER(generation_expression) LIKE '%SCREEN_CODE%' AND UPPER(generation_expression) LIKE '%DELETED%')
          OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND index_name='uk_rpt_screen_active_code' AND non_unique=0 GROUP BY index_name
              HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='ACTIVE_SCREEN_CODE')
            THEN 'REPORT seed zero-DDL preflight: active_screen_code 或唯一键异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_ACCESS_ROLE'
              AND UPPER(column_name) IN ('SCREEN_ID','ROLE_CODE','STATUS','CREATED_BY','CREATED_TIME','UPDATED_BY','UPDATED_TIME'))<>7
          OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_ACCESS_ROLE'
              AND index_name='uk_rpt_screen_access_role' AND non_unique=0 GROUP BY index_name
              HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='SCREEN_ID,ROLE_CODE')
          OR EXISTS (SELECT 1 FROM (SELECT screen_id,role_code FROM RPT_SCREEN_ACCESS_ROLE GROUP BY screen_id,role_code HAVING COUNT(*)>1) d)
            THEN 'REPORT seed zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 定义/唯一键/重复异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='PT_ORG_GROUP'
              AND ((column_name='GROUP_CODE' AND data_type='varchar' AND character_maximum_length=64 AND is_nullable='NO')
                OR (column_name='GROUP_NAME' AND data_type='varchar' AND character_maximum_length=100 AND is_nullable='NO')
                OR (column_name='GROUP_PURPOSE' AND data_type='varchar' AND character_maximum_length=30 AND is_nullable='NO')
                OR (column_name='STATUS' AND data_type='varchar' AND character_maximum_length=10 AND is_nullable='NO' AND column_default='ACTIVE')
                OR (column_name='VERSION' AND data_type='int' AND is_nullable='NO' AND CAST(column_default AS CHAR)='0')
                OR (column_name='CREATED_BY' AND data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES')
                OR (column_name='UPDATED_BY' AND data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES')
                OR (column_name='REMARK' AND data_type='varchar' AND character_maximum_length=500 AND is_nullable='YES')))<>8
          OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='PT_ORG_GROUP'
              AND index_name='UK_PT_ORG_GROUP_CODE' AND non_unique=0 GROUP BY index_name
              HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='GROUP_CODE')
            THEN 'REPORT seed zero-DDL preflight: PT_ORG_GROUP 目标列或唯一键异常'
        WHEN (SELECT COUNT(*) FROM PT_ROLE WHERE role_code IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER'))<>2
          OR EXISTS (SELECT 1 FROM (SELECT role_code FROM PT_ROLE WHERE role_code IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER')
                                     GROUP BY role_code HAVING COUNT(*)<>1) d)
          OR EXISTS (SELECT 1 FROM PT_ROLE WHERE role_code IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER')
                     AND CAST(role_id AS CHAR) NOT REGEXP '^[0-9]+$')
            THEN 'REPORT seed zero-DDL preflight: 查看角色身份异常'
        WHEN EXISTS (SELECT 1 FROM (SELECT screen_code FROM RPT_SCREEN WHERE deleted=0
                     AND screen_code IN ('SCR_CORP_OVERVIEW','SCR_RETAIL_OVERVIEW') GROUP BY screen_code HAVING COUNT(*)>1) d)
            THEN 'REPORT seed zero-DDL preflight: 目标 screen_code 重复'
        WHEN EXISTS (SELECT 1 FROM (SELECT group_code FROM PT_ORG_GROUP
                     WHERE group_code IN ('ORG_GRP_PRIMARY_OPERATING_UNITS','ORG_GRP_CORP_DEPARTMENTS')
                     GROUP BY group_code HAVING COUNT(*)>1) d)
            THEN 'REPORT seed zero-DDL preflight: 目标 group_code 重复'
        WHEN EXISTS (SELECT 1 FROM PT_ORG_GROUP WHERE
                    (group_code='ORG_GRP_PRIMARY_OPERATING_UNITS' AND (group_name<>'全辖一级经营机构' OR group_purpose<>'REPORT_SCREEN'))
                 OR (group_code='ORG_GRP_CORP_DEPARTMENTS' AND (group_name<>'对公部门组' OR group_purpose<>'REPORT_SCREEN')))
            THEN 'REPORT seed zero-DDL preflight: group_code 身份冲突'
        WHEN EXISTS (SELECT 1 FROM RPT_SCREEN WHERE deleted=0 AND
                    ((screen_code='SCR_CORP_OVERVIEW' AND (biz_line<>'CORP' OR org_scope_mode<>'NAMED_GROUP' OR org_group_code<>'ORG_GRP_CORP_DEPARTMENTS'))
                  OR (screen_code='SCR_RETAIL_OVERVIEW' AND (biz_line<>'RETAIL' OR org_scope_mode<>'NAMED_GROUP' OR org_group_code<>'ORG_GRP_PRIMARY_OPERATING_UNITS'))))
            THEN 'REPORT seed zero-DDL preflight: screen_code 范围身份冲突'
    END
);
-- 4a) seed 会直接写入下列列；上一段的业务身份检查之外，继续在硬停止前镜像 auth/align
-- 的完整结构契约。这样不会出现“过程内才发现列/索引不兼容，但已先 DROP PROCEDURE”的半执行。
SET @rpt_seed_preflight_error := COALESCE(@rpt_seed_preflight_error, (
    SELECT CASE
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='PT_ORG_GROUP'
                 AND ((column_name='ID' AND data_type='bigint' AND is_nullable='NO'
                       AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
                   OR (column_name='GROUP_CODE' AND data_type='varchar' AND character_maximum_length=64
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='GROUP_NAME' AND data_type='varchar' AND character_maximum_length=100
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='GROUP_PURPOSE' AND data_type='varchar' AND character_maximum_length=30
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='STATUS' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='NO' AND column_default='ACTIVE')
                   OR (column_name='VERSION' AND data_type='int' AND is_nullable='NO'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='CREATED_BY' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='CREATED_TIME' AND data_type='datetime' AND is_nullable='NO'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP')
                   OR (column_name='UPDATED_BY' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='UPDATED_TIME' AND data_type='datetime' AND is_nullable='NO'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP'
                       AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                   OR (column_name='REMARK' AND data_type='varchar' AND character_maximum_length=500
                       AND is_nullable='YES' AND column_default IS NULL))) <> 11
            THEN 'REPORT seed zero-DDL preflight: PT_ORG_GROUP 完整列定义或长度异常'
        WHEN COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='PT_ORG_GROUP' AND index_name='PRIMARY' AND non_unique=0),'') <> 'ID'
          OR COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='PT_ORG_GROUP' AND index_name='UK_PT_ORG_GROUP_CODE'),'') <> 'GROUP_CODE'
          OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                       AND table_name='PT_ORG_GROUP' AND index_name='UK_PT_ORG_GROUP_CODE'),-1) <> 0
          OR COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='PT_ORG_GROUP' AND index_name='IDX_PT_ORG_GROUP_STATUS'),'') <> 'STATUS'
          OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                       AND table_name='PT_ORG_GROUP' AND index_name='IDX_PT_ORG_GROUP_STATUS'),-1) <> 1
            THEN 'REPORT seed zero-DDL preflight: PT_ORG_GROUP 索引定义异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
                 AND ((column_name='id' AND data_type='bigint' AND is_nullable='NO'
                       AND column_default IS NULL AND LOWER(extra) LIKE '%auto_increment%')
                   OR (column_name='screen_code' AND data_type='varchar' AND character_maximum_length=64
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='screen_name' AND data_type='varchar' AND character_maximum_length=100
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='view_level' AND data_type='varchar' AND character_maximum_length=20
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='biz_line' AND data_type='varchar' AND character_maximum_length=20
                       AND is_nullable='NO' AND column_default='COMMON')
                   OR (column_name='org_scope_mode' AND data_type='varchar' AND character_maximum_length=20
                       AND is_nullable='NO' AND column_default='LEGACY_CONTEXT')
                   OR (column_name='org_group_code' AND data_type='varchar' AND character_maximum_length=64
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='theme_json' AND data_type='varchar' AND character_maximum_length=1000
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='status' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='NO' AND column_default='ACTIVE')
                   OR (column_name='canvas_style_json' AND data_type='longtext' AND is_nullable='YES'
                       AND column_default IS NULL)
                   OR (column_name='canvas_draft_json' AND data_type='longtext' AND is_nullable='YES'
                       AND column_default IS NULL)
                   OR (column_name='canvas_published_json' AND data_type='longtext' AND is_nullable='YES'
                       AND column_default IS NULL)
                   OR (column_name='canvas_version' AND data_type='int' AND is_nullable='NO'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='publish_status' AND data_type='tinyint' AND is_nullable='NO'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='published_at' AND data_type='datetime' AND is_nullable='YES'
                       AND column_default IS NULL)
                   OR (column_name='published_by' AND data_type='varchar' AND character_maximum_length=32
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='updated_by' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='created_by' AND data_type='varchar' AND character_maximum_length=32
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='created_time' AND data_type='datetime' AND is_nullable='YES'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP')
                   OR (column_name='updated_time' AND data_type='datetime' AND is_nullable='YES'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP'
                       AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                   OR (column_name='deleted' AND data_type='tinyint' AND is_nullable='NO'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='active_screen_code' AND data_type='varchar' AND character_maximum_length=64
                       AND is_nullable='YES' AND UPPER(extra) LIKE '%STORED GENERATED%'
                       AND UPPER(generation_expression) LIKE '%SCREEN_CODE%'
                       AND UPPER(generation_expression) LIKE '%DELETED%'))) <> 22
            THEN 'REPORT seed zero-DDL preflight: RPT_SCREEN 完整目标列定义或长度异常'
        WHEN COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='RPT_SCREEN' AND index_name='PRIMARY' AND non_unique=0),'') <> 'ID'
          OR COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='RPT_SCREEN' AND index_name='uk_rpt_screen_active_code'),'') <> 'ACTIVE_SCREEN_CODE'
          OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                       AND table_name='RPT_SCREEN' AND index_name='uk_rpt_screen_active_code'),-1) <> 0
          OR COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='RPT_SCREEN' AND index_name='idx_scr_scope_group'),'') <> 'ORG_SCOPE_MODE,ORG_GROUP_CODE'
          OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                       AND table_name='RPT_SCREEN' AND index_name='idx_scr_scope_group'),-1) <> 1
            THEN 'REPORT seed zero-DDL preflight: RPT_SCREEN 主键或本期索引异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_ACCESS_ROLE'
                 AND ((column_name='id' AND data_type='bigint' AND is_nullable='NO'
                       AND column_default IS NULL AND UPPER(extra) LIKE '%AUTO_INCREMENT%')
                   OR (column_name='screen_id' AND data_type='bigint' AND is_nullable='NO'
                       AND column_default IS NULL)
                   OR (column_name='role_code' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='status' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='NO' AND column_default='ACTIVE')
                   OR (column_name='created_by' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='created_time' AND data_type='datetime' AND is_nullable='NO'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP')
                   OR (column_name='updated_by' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='YES' AND column_default IS NULL)
                   OR (column_name='updated_time' AND data_type='datetime' AND is_nullable='NO'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP'
                       AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%'))) <> 8
            THEN 'REPORT seed zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 完整列定义异常'
        WHEN COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='PRIMARY' AND non_unique=0),'') <> 'ID'
          OR COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='uk_rpt_screen_access_role'),'') <> 'SCREEN_ID,ROLE_CODE'
          OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                       AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='uk_rpt_screen_access_role'),-1) <> 0
          OR COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='idx_rpt_screen_access_role_status'),'') <> 'SCREEN_ID,STATUS'
          OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                       AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='idx_rpt_screen_access_role_status'),-1) <> 1
            THEN 'REPORT seed zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 索引定义异常'
        WHEN EXISTS (SELECT 1 FROM (
                SELECT 'PT_ORG_GROUP.GROUP_CODE' target_col,'ORG_GRP_PRIMARY_OPERATING_UNITS' value_text,64 max_len
                UNION ALL SELECT 'PT_ORG_GROUP.GROUP_CODE','ORG_GRP_CORP_DEPARTMENTS',64
                UNION ALL SELECT 'PT_ORG_GROUP.GROUP_NAME','全辖一级经营机构',100
                UNION ALL SELECT 'PT_ORG_GROUP.GROUP_NAME','对公部门组',100
                UNION ALL SELECT 'PT_ORG_GROUP.GROUP_PURPOSE','REPORT_SCREEN',30
                UNION ALL SELECT 'RPT_SCREEN.SCREEN_CODE','SCR_CORP_OVERVIEW',64
                UNION ALL SELECT 'RPT_SCREEN.SCREEN_CODE','SCR_RETAIL_OVERVIEW',64
                UNION ALL SELECT 'RPT_SCREEN.SCREEN_NAME','对公经营总览',100
                UNION ALL SELECT 'RPT_SCREEN.SCREEN_NAME','零售经营总览',100
                UNION ALL SELECT 'RPT_SCREEN.ORG_GROUP_CODE','ORG_GRP_PRIMARY_OPERATING_UNITS',64
                UNION ALL SELECT 'RPT_SCREEN.ORG_GROUP_CODE','ORG_GRP_CORP_DEPARTMENTS',64
                UNION ALL SELECT 'RPT_SCREEN.CREATED_BY','2026-08-11-screen-seed',32
                UNION ALL SELECT 'RPT_SCREEN.UPDATED_BY','2026-08-11-screen-seed',50
                UNION ALL SELECT 'RPT_SCREEN_ACCESS_ROLE.CREATED_BY','2026-08-11-screen-seed',50
                UNION ALL SELECT 'RPT_SCREEN_ACCESS_ROLE.UPDATED_BY','2026-08-11-screen-seed',50
            ) expected_seed_value WHERE CHAR_LENGTH(value_text)>max_len)
            THEN 'REPORT seed zero-DDL preflight: 待插入值超出目标列长度'
    END
));
SELECT COALESCE(@rpt_seed_preflight_error,'REPORT seed zero-DDL preflight passed') AS preflight_result;
SET @rpt_seed_preflight_guard_sql := IF(@rpt_seed_preflight_error IS NULL,
    'SELECT ''REPORT seed zero-DDL preflight passed'' AS preflight_guard',
    CONCAT('SELECT * FROM __rpt_seed_preflight_stop_',REPLACE(UUID(),'-',''),'__'));
PREPARE rpt_seed_preflight_guard FROM @rpt_seed_preflight_guard_sql;
EXECUTE rpt_seed_preflight_guard;
DEALLOCATE PREPARE rpt_seed_preflight_guard;

-- 只有硬停止门以成功路径完成，才可以执行以下非只读部分。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sp_screen_scope_map_seed_20260811;
DELIMITER $$
CREATE PROCEDURE sp_screen_scope_map_seed_20260811()
BEGIN
    DECLARE v_count BIGINT DEFAULT 0;
    DECLARE v_columns VARCHAR(512);
    DECLARE v_index_non_unique INT DEFAULT -1;

    -- 1. 前置对象完整性。seed 只插入无歧义的新壳，不负责修复残缺库。
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: PT_ROLE 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ORG_GROUP';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: PT_ORG_GROUP 不存在，先执行 auth 脚本';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: RPT_SCREEN 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: RPT_SCREEN_ACCESS_ROLE 不存在，先执行 align 脚本';
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
       AND UPPER(column_name) IN ('ID','SCREEN_CODE','SCREEN_NAME','VIEW_LEVEL','BIZ_LINE','ORG_SCOPE_MODE',
                                  'ORG_GROUP_CODE','STATUS','CANVAS_STYLE_JSON','CANVAS_DRAFT_JSON',
                                  'CANVAS_PUBLISHED_JSON','CANVAS_VERSION','PUBLISH_STATUS','UPDATED_BY','DELETED',
                                  'ACTIVE_SCREEN_CODE');
    IF v_count <> 16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: RPT_SCREEN 缺少本期列';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'active_screen_code'
       AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES'
       AND UPPER(extra) LIKE '%STORED GENERATED%'
       AND UPPER(generation_expression) LIKE '%SCREEN_CODE%'
       AND UPPER(generation_expression) LIKE '%DELETED%';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: active_screen_code 生成列异常';
    END IF;
    SELECT MIN(non_unique) INTO v_index_non_unique FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND index_name = 'uk_rpt_screen_active_code';
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
       AND index_name = 'uk_rpt_screen_active_code';
    IF v_index_non_unique <> 0 OR COALESCE(v_columns, '') <> 'ACTIVE_SCREEN_CODE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: uk_rpt_screen_active_code 唯一键异常';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
       AND UPPER(column_name) IN ('SCREEN_ID','ROLE_CODE','STATUS','CREATED_BY','CREATED_TIME','UPDATED_BY','UPDATED_TIME');
    IF v_count <> 7 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: RPT_SCREEN_ACCESS_ROLE 缺少本期列';
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
       AND index_name = 'uk_rpt_screen_access_role';
    IF v_columns <> 'SCREEN_ID,ROLE_CODE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: RPT_SCREEN_ACCESS_ROLE 唯一键异常';
    END IF;

    -- 2. auth 角色按 ROLE_CODE 解析；ROLE_ID 仅用于验证它已由 auth 以当前数字 ID 安全创建。
    SELECT COUNT(*) INTO v_count FROM PT_ROLE
     WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: 缺少 auth 创建的大屏查看角色';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT ROLE_CODE FROM PT_ROLE
         WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER')
         GROUP BY ROLE_CODE HAVING COUNT(*) <> 1
    ) AS duplicate_role_code;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: 大屏 ROLE_CODE 不唯一';
    END IF;
    SELECT COUNT(*) INTO v_count FROM PT_ROLE
     WHERE ROLE_CODE IN ('R_SCREEN_CORP_VIEWER','R_SCREEN_RETAIL_VIEWER')
       AND CAST(ROLE_ID AS CHAR) NOT REGEXP '^[0-9]+$';
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: 大屏角色必须使用 auth 数字 ROLE_ID';
    END IF;

    -- 3. 业务键重复/身份冲突一律停。screen_code 和 group_code 均不能靠“任选一条”继续。
    SELECT COUNT(*) INTO v_count FROM (
        SELECT screen_code FROM RPT_SCREEN WHERE deleted = 0
          AND screen_code IN ('SCR_CORP_OVERVIEW','SCR_RETAIL_OVERVIEW')
         GROUP BY screen_code HAVING COUNT(*) > 1
    ) AS duplicate_screen_code;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: 目标 screen_code 重复';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT group_code FROM PT_ORG_GROUP
         WHERE group_code IN ('ORG_GRP_PRIMARY_OPERATING_UNITS','ORG_GRP_CORP_DEPARTMENTS')
         GROUP BY group_code HAVING COUNT(*) > 1
    ) AS duplicate_group_code;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed preflight: 目标 group_code 重复';
    END IF;
    SELECT COUNT(*) INTO v_count FROM PT_ORG_GROUP
     WHERE (group_code = 'ORG_GRP_PRIMARY_OPERATING_UNITS'
            AND (group_name <> '全辖一级经营机构' OR group_purpose <> 'REPORT_SCREEN'))
        OR (group_code = 'ORG_GRP_CORP_DEPARTMENTS'
            AND (group_name <> '对公部门组' OR group_purpose <> 'REPORT_SCREEN'));
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed conflict: 同 group_code 对应不同业务身份';
    END IF;
    SELECT COUNT(*) INTO v_count FROM RPT_SCREEN
     WHERE deleted = 0 AND ((screen_code = 'SCR_CORP_OVERVIEW'
            AND (biz_line <> 'CORP' OR org_scope_mode <> 'NAMED_GROUP' OR org_group_code <> 'ORG_GRP_CORP_DEPARTMENTS'))
        OR (screen_code = 'SCR_RETAIL_OVERVIEW'
            AND (biz_line <> 'RETAIL' OR org_scope_mode <> 'NAMED_GROUP' OR org_group_code <> 'ORG_GRP_PRIMARY_OPERATING_UNITS')));
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT seed conflict: 同 screen_code 对应不同范围身份';
    END IF;

    -- 4. 只创建稳定机构组壳；成员和角色-机构组绑定只能由权限管理端经审批覆盖保存。
    INSERT INTO PT_ORG_GROUP (GROUP_CODE,GROUP_NAME,GROUP_PURPOSE,STATUS,VERSION,CREATED_BY,CREATED_TIME,
                              UPDATED_BY,UPDATED_TIME,REMARK)
    SELECT 'ORG_GRP_PRIMARY_OPERATING_UNITS','全辖一级经营机构','REPORT_SCREEN','ACTIVE',0,
           '2026-08-11-screen-seed',NOW(),'2026-08-11-screen-seed',NOW(),
           '成员需由业务负责人复核；包含西安本地及异地一级经营机构'
      FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PT_ORG_GROUP WHERE GROUP_CODE = 'ORG_GRP_PRIMARY_OPERATING_UNITS');
    INSERT INTO PT_ORG_GROUP (GROUP_CODE,GROUP_NAME,GROUP_PURPOSE,STATUS,VERSION,CREATED_BY,CREATED_TIME,
                              UPDATED_BY,UPDATED_TIME,REMARK)
    SELECT 'ORG_GRP_CORP_DEPARTMENTS','对公部门组','REPORT_SCREEN','ACTIVE',0,
           '2026-08-11-screen-seed',NOW(),'2026-08-11-screen-seed',NOW(),
           '成员需由业务负责人复核；不按机构名称或层级自动推导'
      FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PT_ORG_GROUP WHERE GROUP_CODE = 'ORG_GRP_CORP_DEPARTMENTS');

    -- 5. 新屏仅为草稿；不覆写任何既有草稿/发布态，也不原地升级 SCR_PROVINCE。
    INSERT INTO RPT_SCREEN (screen_code,screen_name,view_level,biz_line,org_scope_mode,org_group_code,
                            theme_json,status,created_by,created_time,updated_by,updated_time,deleted,
                            canvas_style_json,canvas_draft_json,canvas_published_json,canvas_version,
                            publish_status,published_at,published_by)
    SELECT 'SCR_CORP_OVERVIEW','对公经营总览','PROVINCE','CORP','NAMED_GROUP','ORG_GRP_CORP_DEPARTMENTS',
           NULL,'ACTIVE','2026-08-11-screen-seed',NOW(),'2026-08-11-screen-seed',NOW(),0,
           '{"schemaVersion":1,"designWidth":1920,"designHeight":1080,"background":"#03081c","adaptor":"keepProportion"}',
           '{"schemaVersion":1,"components":[{"id":"seed-corp-title","component":"TitleBar","style":{"top":0,"left":0,"width":1920,"height":80},"propValue":{"text":"对公经营总览"}}]}',
           NULL,0,0,NULL,NULL
      FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM RPT_SCREEN WHERE screen_code = 'SCR_CORP_OVERVIEW' AND deleted = 0);
    INSERT INTO RPT_SCREEN (screen_code,screen_name,view_level,biz_line,org_scope_mode,org_group_code,
                            theme_json,status,created_by,created_time,updated_by,updated_time,deleted,
                            canvas_style_json,canvas_draft_json,canvas_published_json,canvas_version,
                            publish_status,published_at,published_by)
    SELECT 'SCR_RETAIL_OVERVIEW','零售经营总览','PROVINCE','RETAIL','NAMED_GROUP','ORG_GRP_PRIMARY_OPERATING_UNITS',
           NULL,'ACTIVE','2026-08-11-screen-seed',NOW(),'2026-08-11-screen-seed',NOW(),0,
           '{"schemaVersion":2,"designWidth":1920,"designHeight":1080,"background":"#03081c","adaptor":"keepProportion"}',
           '{"schemaVersion":2,"components":[{"id":"seed-retail-map","component":"MapCenter","style":{"top":0,"left":0,"width":1920,"height":1080},"propValue":{"schemaVersion":2,"mode":"XIAN_COMPOSITE","baseRegion":"XIAN_OUTLINE","localSelector":{"cityCode":"610100","operatingLevel":"PRIMARY"},"satelliteNodes":[{"orgCode":"128","anchor":"LEFT","targetScreenCode":"SCR_BRANCH"},{"orgCode":"191","anchor":"RIGHT","targetScreenCode":"SCR_BRANCH"},{"orgCode":"169","anchor":"TOP","targetScreenCode":"SCR_BRANCH"},{"orgCode":"129","anchor":"FAR_TOP","targetScreenCode":"SCR_BRANCH"}],"disclaimer":"组织分布示意，非地理比例"}}]}',
           NULL,0,0,NULL,NULL
      FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM RPT_SCREEN WHERE screen_code = 'SCR_RETAIL_OVERVIEW' AND deleted = 0);

    -- 6. 屏白名单以 ROLE_CODE 写入，但每次均 JOIN auth 角色验证身份；不重复插入且不写人员绑定。
    INSERT INTO RPT_SCREEN_ACCESS_ROLE (screen_id,role_code,status,created_by,created_time,updated_by,updated_time)
    SELECT s.id, role_def.ROLE_CODE, 'ACTIVE','2026-08-11-screen-seed',NOW(),'2026-08-11-screen-seed',NOW()
      FROM RPT_SCREEN s JOIN PT_ROLE role_def ON role_def.ROLE_CODE = 'R_SCREEN_CORP_VIEWER'
     WHERE s.screen_code = 'SCR_CORP_OVERVIEW' AND s.deleted = 0
       AND NOT EXISTS (SELECT 1 FROM RPT_SCREEN_ACCESS_ROLE ar
                        WHERE ar.screen_id = s.id AND ar.role_code = role_def.ROLE_CODE);
    INSERT INTO RPT_SCREEN_ACCESS_ROLE (screen_id,role_code,status,created_by,created_time,updated_by,updated_time)
    SELECT s.id, role_def.ROLE_CODE, 'ACTIVE','2026-08-11-screen-seed',NOW(),'2026-08-11-screen-seed',NOW()
      FROM RPT_SCREEN s JOIN PT_ROLE role_def ON role_def.ROLE_CODE = 'R_SCREEN_RETAIL_VIEWER'
     WHERE s.screen_code = 'SCR_RETAIL_OVERVIEW' AND s.deleted = 0
       AND NOT EXISTS (SELECT 1 FROM RPT_SCREEN_ACCESS_ROLE ar
                        WHERE ar.screen_id = s.id AND ar.role_code = role_def.ROLE_CODE);
END$$
DELIMITER ;

CALL sp_screen_scope_map_seed_20260811();
DROP PROCEDURE IF EXISTS sp_screen_scope_map_seed_20260811;

-- 只读核验；双跑时以下业务键、角色白名单和草稿版本应不发生额外变化。
SELECT screen_code,biz_line,org_scope_mode,org_group_code,publish_status,canvas_version
  FROM RPT_SCREEN WHERE deleted = 0 AND screen_code IN ('SCR_CORP_OVERVIEW','SCR_RETAIL_OVERVIEW');
SELECT ar.screen_id,ar.role_code,ar.status,role_def.ROLE_ID
  FROM RPT_SCREEN_ACCESS_ROLE ar JOIN PT_ROLE role_def ON role_def.ROLE_CODE = ar.role_code
 WHERE ar.screen_id IN (SELECT id FROM RPT_SCREEN
                         WHERE deleted = 0 AND screen_code IN ('SCR_CORP_OVERVIEW','SCR_RETAIL_OVERVIEW'))
 ORDER BY ar.screen_id,ar.role_code;
