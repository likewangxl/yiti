-- ============================================================================
-- REPORT：大屏业务条线、命名机构组范围与屏级角色白名单结构对齐
-- 日期：2026-08-11
--
-- 手工迁移，禁止 Flyway/应用启动自动执行。
-- 执行顺序：先 auth-org-profile-group.sql（其负责目标 ROLE_CODE 的精确判重与数字角色 ID），
-- 再执行本脚本，最后执行 screen-scope-map-seed.sql。
--
-- 执行门禁：任何 DDL/DML 前，先对获批准的隔离目标做只读盘点。克隆、覆盖、清空、备份及
-- 执行本脚本均须另获明确授权；获准隔离验证后须保留一次执行、二次零 diff 及结构/数据
-- diff 证据。任何 yiti 执行还须在报告完整证据后再次取得明确确认。脚本故意对残缺对象、
-- 身份冲突、重复绑定 SIGNAL 失败，绝不使用静默忽略重复或覆盖式写入掩盖问题。
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
-- SOURCE docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql;
-- ---------------------------------------------------------------------------

-- ============================================================================
-- 外置只读预检（强制）：以下 SELECT 必须在任何 DROP/CREATE PROCEDURE、ALTER、CREATE TABLE、
-- UPDATE、INSERT、CALL 前，在最终获权写会话中作为只读阶段逐条运行。任一“violation”查询返回
-- 行、或对象清单与本注释的目标定义不一致，立即停止；不得只在另一只读连接完成后再换写连接执行。
--
-- 隔离验证与生产执行须分别取得批准；审批 manifest 中的目标实例和 schema 是唯一机器判定依据，
-- 不得通过固定逻辑库名推断授权。任何获批目标均必须重新完成本段同一写会话内的只读预检。
-- 目标定义：RPT_SCREEN 新增 biz_line VARCHAR(20) NOT NULL DEFAULT COMMON、org_scope_mode
-- VARCHAR(20) NOT NULL DEFAULT LEGACY_CONTEXT、org_group_code VARCHAR(64) NULL、updated_by
-- VARCHAR(50) NULL、active_screen_code VARCHAR(64) STORED；RPT_SCREEN_DATASOURCE 新增
-- biz_line VARCHAR(20) NOT NULL DEFAULT COMMON、updated_by VARCHAR(50) NULL；资源 ID 最大 20，
-- URL 最大 256，method 最大 10，remark 最大 100；RPT_SCREEN_ACCESS_ROLE 固定 8 列及两个索引。
-- 过程内的 SIGNAL 预检保留为第二层，绝不替代本段。
-- ============================================================================
-- 1) 基础对象、所有相关列的实际类型/长度/NULL/default/generation expression（人工逐字段比对上方目标定义）。
SELECT table_name, table_type
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('RPT_SCREEN','RPT_SCREEN_DATASOURCE','PT_RESOURCE','PT_ROLE','PT_ROLE_RESOURCE',
                      'RPT_SCREEN_ACCESS_ROLE')
 ORDER BY table_name;
SELECT table_name,column_name,ordinal_position,data_type,character_maximum_length,is_nullable,column_default,
       extra,generation_expression
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name IN ('RPT_SCREEN','RPT_SCREEN_DATASOURCE','PT_RESOURCE','PT_ROLE','PT_ROLE_RESOURCE',
                      'RPT_SCREEN_ACCESS_ROLE')
 ORDER BY table_name,ordinal_position;
SELECT table_name,index_name,non_unique,seq_in_index,column_name
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name IN ('RPT_SCREEN','RPT_SCREEN_DATASOURCE','PT_RESOURCE','PT_ROLE','PT_ROLE_RESOURCE',
                      'RPT_SCREEN_ACCESS_ROLE')
 ORDER BY table_name,index_name,seq_in_index;

-- 2) 任何返回行即失败：基础对象缺失、已存在扩展列/索引定义冲突、活跃 screenCode 冲突或白名单重复。
SELECT 'REPORT external preflight: 基础对象缺失' AS violation
  FROM (SELECT 'RPT_SCREEN' AS table_name UNION ALL SELECT 'RPT_SCREEN_DATASOURCE' UNION ALL SELECT 'PT_RESOURCE'
        UNION ALL SELECT 'PT_ROLE' UNION ALL SELECT 'PT_ROLE_RESOURCE') expected
 WHERE NOT EXISTS (SELECT 1 FROM information_schema.tables t
                    WHERE t.table_schema = DATABASE() AND t.table_name = expected.table_name);
SELECT 'REPORT external preflight: RPT_SCREEN 已存在扩展列定义冲突' AS violation
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
   AND ((column_name = 'biz_line' AND NOT (data_type='varchar' AND character_maximum_length=20 AND is_nullable='NO' AND column_default='COMMON'))
     OR (column_name = 'org_scope_mode' AND NOT (data_type='varchar' AND character_maximum_length=20 AND is_nullable='NO' AND column_default='LEGACY_CONTEXT'))
     OR (column_name = 'org_group_code' AND NOT (data_type='varchar' AND character_maximum_length=64 AND is_nullable='YES' AND column_default IS NULL))
     OR (column_name = 'updated_by' AND NOT (data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES' AND column_default IS NULL))
     OR (column_name = 'active_screen_code' AND NOT (data_type='varchar' AND character_maximum_length=64 AND is_nullable='YES'
         AND UPPER(extra) LIKE '%STORED GENERATED%' AND UPPER(generation_expression) LIKE '%SCREEN_CODE%'
         AND UPPER(generation_expression) LIKE '%DELETED%')));
SELECT 'REPORT external preflight: RPT_SCREEN_DATASOURCE 已存在扩展列定义冲突' AS violation
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE'
   AND ((column_name = 'biz_line' AND NOT (data_type='varchar' AND character_maximum_length=20 AND is_nullable='NO' AND column_default='COMMON'))
     OR (column_name = 'updated_by' AND NOT (data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES' AND column_default IS NULL)));
SELECT 'REPORT external preflight: 已有目标索引定义冲突' AS violation
  FROM (SELECT table_name,index_name,GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') cols,
               MIN(non_unique) non_unique
          FROM information_schema.statistics
         WHERE table_schema=DATABASE()
           AND ((table_name='RPT_SCREEN' AND index_name IN ('uk_rpt_screen_active_code','idx_scr_scope_group'))
             OR (table_name='RPT_SCREEN_DATASOURCE' AND index_name='idx_scr_ds_biz_line')
             OR (table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name IN ('uk_rpt_screen_access_role','idx_rpt_screen_access_role_status')))
         GROUP BY table_name,index_name) i
 WHERE (table_name='RPT_SCREEN' AND index_name='uk_rpt_screen_active_code' AND (cols<>'ACTIVE_SCREEN_CODE' OR non_unique<>0))
    OR (table_name='RPT_SCREEN' AND index_name='idx_scr_scope_group' AND cols<>'ORG_SCOPE_MODE,ORG_GROUP_CODE')
    OR (table_name='RPT_SCREEN_DATASOURCE' AND index_name='idx_scr_ds_biz_line' AND cols<>'BIZ_LINE')
    OR (table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='uk_rpt_screen_access_role' AND (cols<>'SCREEN_ID,ROLE_CODE' OR non_unique<>0))
    OR (table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='idx_rpt_screen_access_role_status' AND (cols<>'SCREEN_ID,STATUS' OR non_unique<>1));
SELECT 'REPORT external preflight: RPT_SCREEN 存在重复活跃 screen_code' AS violation,screen_code
  FROM RPT_SCREEN WHERE deleted=0 GROUP BY screen_code HAVING COUNT(*)>1;

-- 3) 资源、角色、角色资源身份/长度/唯一性：所有返回行均为失败，先人工修复后才能 DDL。
SELECT 'REPORT external preflight: PT_RESOURCE URL/METHOD/SYS_CODE 重复' AS violation,
       resource_url,resource_method,sys_code
  FROM PT_RESOURCE GROUP BY resource_url,resource_method,sys_code HAVING COUNT(*)>1;
SELECT 'REPORT external preflight: 废弃超长资源 ID 仍存在' AS violation
  FROM PT_RESOURCE WHERE resource_id='R_RPT_SCR_CFG_META_SAVE';
WITH expected_resource AS (
    SELECT 'R_RPT_SCR_AR_LIST' resource_id,'/api/screen/admin/screens/*/access-roles' resource_url,'GET' resource_method,'大屏-查看角色列表' menu_name,'RPT' sys_code,'查询屏级查看角色白名单' remark
    UNION ALL SELECT 'R_RPT_SCR_AR_SAVE','/api/screen/admin/screens/*/access-roles','PUT','大屏-查看角色覆盖保存','RPT','高危覆盖保存屏级查看角色白名单（默认精确授予SYS_ADMIN）'
    UNION ALL SELECT 'R_RPT_SCR_META_SAVE','/api/screen/admin/screens/*/metadata','PUT','大屏-元数据范围更新','RPT','仅更新屏元数据与机构范围，不修改画布区块'
    UNION ALL SELECT 'R_RPT_SCR_DS_PROBE','/api/screen/admin/datasources/*/probe-columns','POST','大屏-数据源列探测','RPT','高危设计器列探测（默认不自动授权）'
)
SELECT 'REPORT external preflight: 待写资源长度或双向身份冲突' AS violation,e.resource_id
  FROM expected_resource e LEFT JOIN PT_RESOURCE by_id ON by_id.resource_id=e.resource_id COLLATE utf8mb4_general_ci
  LEFT JOIN PT_RESOURCE by_identity ON by_identity.resource_url=e.resource_url COLLATE utf8mb4_general_ci
       AND by_identity.resource_method=e.resource_method COLLATE utf8mb4_general_ci
       AND by_identity.sys_code=e.sys_code COLLATE utf8mb4_general_ci
 WHERE CHAR_LENGTH(e.resource_id)>20 OR CHAR_LENGTH(e.resource_url)>256 OR CHAR_LENGTH(e.resource_method)>10
    OR CHAR_LENGTH(e.menu_name)>256 OR CHAR_LENGTH(e.sys_code)>10 OR CHAR_LENGTH(e.remark)>100
    OR (by_id.resource_id IS NOT NULL AND NOT (by_id.resource_url <=> e.resource_url COLLATE utf8mb4_general_ci
        AND by_id.resource_method <=> e.resource_method COLLATE utf8mb4_general_ci
        AND by_id.sys_code <=> e.sys_code COLLATE utf8mb4_general_ci))
    OR (by_identity.resource_id IS NOT NULL
        AND by_identity.resource_id<>e.resource_id COLLATE utf8mb4_general_ci);
SELECT 'REPORT external preflight: SYS_ADMIN 必须唯一、启用且 ROLE_ID 为数字' AS violation
  FROM DUAL WHERE (SELECT COUNT(*) FROM PT_ROLE WHERE role_code='SYS_ADMIN')<>1
     OR EXISTS (SELECT 1 FROM PT_ROLE WHERE role_code='SYS_ADMIN'
                 AND (record_status<>0 OR CAST(role_id AS CHAR) NOT REGEXP '^[0-9]+$'));
SELECT 'REPORT external preflight: 目标 PT_ROLE_RESOURCE 存在重复绑定' AS violation,role_id,resource_id
  FROM PT_ROLE_RESOURCE
 WHERE resource_id IN ('R_RPT_SCR_AR_LIST','R_RPT_SCR_AR_SAVE','R_RPT_SCR_META_SAVE','R_RPT_SCR_DS_PROBE')
 GROUP BY role_id,resource_id HAVING COUNT(*)>1;

-- 4) 机器硬停止门：不能仅依赖人工阅读上面的 violation 结果。以下聚合与过程内全部 SIGNAL
-- 条件同源；任一条件成立即 PREPARE 一个 UUID 不存在表查询，使默认“遇错停止”的 mysql 客户端
-- 在首个 DDL 前确定失败。严禁 mysql --force 或任何忽略错误的执行器，否则破坏 fail-close。
SET @rpt_align_preflight_error := (
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
            THEN 'REPORT align target identity guard: approval variables missing or invalid'
        WHEN @@server_uuid IS NULL
             OR CHAR_LENGTH(TRIM(@@server_uuid)) = 0
             OR @@hostname IS NULL
             OR CHAR_LENGTH(TRIM(@@hostname)) = 0
             OR @@port IS NULL
             OR CHAR_LENGTH(TRIM(CAST(@@port AS CHAR))) = 0
             OR DATABASE() IS NULL
             OR CHAR_LENGTH(TRIM(DATABASE())) = 0
            THEN 'REPORT align target identity guard: actual session identity missing'
        WHEN BINARY @approved_target_server_uuid <> BINARY @@server_uuid
             OR BINARY @approved_target_hostname <> BINARY @@hostname
             OR BINARY @approved_target_port <> BINARY CAST(@@port AS CHAR)
             OR BINARY @approved_target_schema <> BINARY DATABASE()
            THEN 'REPORT align target identity guard: approval target does not exactly match current write session'
        WHEN (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
              AND table_name IN ('RPT_SCREEN','RPT_SCREEN_DATASOURCE','PT_RESOURCE','PT_ROLE','PT_ROLE_RESOURCE')) <> 5
            THEN 'REPORT align zero-DDL preflight: 基础表缺失'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND UPPER(column_name) IN ('ID','SCREEN_CODE','VIEW_LEVEL','STATUS','DELETED','CANVAS_DRAFT_JSON',
              'CANVAS_PUBLISHED_JSON','CANVAS_VERSION','PUBLISH_STATUS')) <> 9
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN 基础列异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_DATASOURCE'
              AND UPPER(column_name) IN ('ID','DS_CODE','DS_NAME','SOURCE_KIND','CONFIG_JSON','STATUS','DELETED')) <> 7
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN_DATASOURCE 基础列异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='PT_RESOURCE'
              AND UPPER(column_name) IN ('RESOURCE_ID','RESOURCE_URL','RESOURCE_METHOD','MENU_NAME','MENU_RANK_NO',
              'ISMENU','MENU_ENDFLAG','STATUS','SYS_CODE','CREATE_TIME','UPDATE_TIME','REMARK')) <> 12
            THEN 'REPORT align zero-DDL preflight: PT_RESOURCE 基础列异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='PT_ROLE'
              AND UPPER(column_name) IN ('ROLE_ID','ROLE_CODE','RECORD_STATUS')) <> 3
            THEN 'REPORT align zero-DDL preflight: PT_ROLE 基础列异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='PT_ROLE_RESOURCE'
              AND UPPER(column_name) IN ('ID','ROLE_ID','RESOURCE_ID','SYS_CODE','CREATE_TIME')) <> 5
            THEN 'REPORT align zero-DDL preflight: PT_ROLE_RESOURCE 基础列异常'
        WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND ((column_name='biz_line' AND NOT (data_type='varchar' AND character_maximum_length=20 AND is_nullable='NO' AND column_default='COMMON'))
                OR (column_name='org_scope_mode' AND NOT (data_type='varchar' AND character_maximum_length=20 AND is_nullable='NO' AND column_default='LEGACY_CONTEXT'))
                OR (column_name='org_group_code' AND NOT (data_type='varchar' AND character_maximum_length=64 AND is_nullable='YES' AND column_default IS NULL))
                OR (column_name='updated_by' AND NOT (data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES' AND column_default IS NULL))
                OR (column_name='active_screen_code' AND NOT (data_type='varchar' AND character_maximum_length=64 AND is_nullable='YES'
                    AND UPPER(extra) LIKE '%STORED GENERATED%' AND UPPER(generation_expression) LIKE '%SCREEN_CODE%' AND UPPER(generation_expression) LIKE '%DELETED%'))))
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN 扩展列异常'
        WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_DATASOURCE'
              AND ((column_name='biz_line' AND NOT (data_type='varchar' AND character_maximum_length=20 AND is_nullable='NO' AND column_default='COMMON'))
                OR (column_name='updated_by' AND NOT (data_type='varchar' AND character_maximum_length=50 AND is_nullable='YES' AND column_default IS NULL))))
            THEN 'REPORT align zero-DDL preflight: 数据源扩展列异常'
        WHEN EXISTS (SELECT 1 FROM (SELECT screen_code FROM RPT_SCREEN WHERE deleted=0 GROUP BY screen_code HAVING COUNT(*)>1) d)
            THEN 'REPORT align zero-DDL preflight: 活跃 screen_code 重复'
        WHEN EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND index_name='uk_rpt_screen_active_code' GROUP BY index_name
              HAVING COUNT(*)<>1 OR MIN(non_unique)<>0 OR GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')<>'ACTIVE_SCREEN_CODE')
          OR EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
              AND index_name='idx_scr_scope_group' GROUP BY index_name
              HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')<>'ORG_SCOPE_MODE,ORG_GROUP_CODE')
          OR EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_DATASOURCE'
              AND index_name='idx_scr_ds_biz_line' GROUP BY index_name
              HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')<>'BIZ_LINE')
            THEN 'REPORT align zero-DDL preflight: 已有目标索引冲突'
        WHEN EXISTS (SELECT 1 FROM PT_RESOURCE GROUP BY resource_url,resource_method,sys_code HAVING COUNT(*)>1)
          OR EXISTS (SELECT 1 FROM PT_RESOURCE WHERE resource_id='R_RPT_SCR_CFG_META_SAVE')
            THEN 'REPORT align zero-DDL preflight: PT_RESOURCE 身份冲突或废弃资源未清理'
        WHEN (SELECT COUNT(*) FROM PT_ROLE WHERE role_code='SYS_ADMIN')<>1
          OR EXISTS (SELECT 1 FROM PT_ROLE WHERE role_code='SYS_ADMIN' AND (record_status<>0 OR CAST(role_id AS CHAR) NOT REGEXP '^[0-9]+$'))
            THEN 'REPORT align zero-DDL preflight: SYS_ADMIN 身份异常'
        WHEN EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE WHERE resource_id IN ('R_RPT_SCR_AR_LIST','R_RPT_SCR_AR_SAVE','R_RPT_SCR_META_SAVE','R_RPT_SCR_DS_PROBE')
                     GROUP BY role_id,resource_id HAVING COUNT(*)>1)
            THEN 'REPORT align zero-DDL preflight: PT_ROLE_RESOURCE 重复绑定'
        WHEN (SELECT COUNT(*) FROM PT_RESOURCE WHERE resource_id IN ('R_RPT_SCR_CFG_GET','R_RPT_SCR_CFG_SAVE'))<>2
            THEN 'REPORT align zero-DDL preflight: 既有屏配置资源缺失'
        WHEN EXISTS (SELECT 1 FROM PT_RESOURCE p JOIN (
                 SELECT 'R_RPT_SCR_AR_LIST' resource_id,'/api/screen/admin/screens/*/access-roles' resource_url,'GET' resource_method,'RPT' sys_code
                 UNION ALL SELECT 'R_RPT_SCR_AR_SAVE','/api/screen/admin/screens/*/access-roles','PUT','RPT'
                 UNION ALL SELECT 'R_RPT_SCR_META_SAVE','/api/screen/admin/screens/*/metadata','PUT','RPT'
                 UNION ALL SELECT 'R_RPT_SCR_DS_PROBE','/api/screen/admin/datasources/*/probe-columns','POST','RPT') e
                 ON p.resource_id=e.resource_id COLLATE utf8mb4_general_ci
                 WHERE NOT (p.resource_url<=>e.resource_url COLLATE utf8mb4_general_ci
                     AND p.resource_method<=>e.resource_method COLLATE utf8mb4_general_ci
                     AND p.sys_code<=>e.sys_code COLLATE utf8mb4_general_ci))
          OR EXISTS (SELECT 1 FROM PT_RESOURCE p JOIN (
                 SELECT 'R_RPT_SCR_AR_LIST' resource_id,'/api/screen/admin/screens/*/access-roles' resource_url,'GET' resource_method,'RPT' sys_code
                 UNION ALL SELECT 'R_RPT_SCR_AR_SAVE','/api/screen/admin/screens/*/access-roles','PUT','RPT'
                 UNION ALL SELECT 'R_RPT_SCR_META_SAVE','/api/screen/admin/screens/*/metadata','PUT','RPT'
                 UNION ALL SELECT 'R_RPT_SCR_DS_PROBE','/api/screen/admin/datasources/*/probe-columns','POST','RPT') e
                 ON p.resource_url=e.resource_url COLLATE utf8mb4_general_ci
                AND p.resource_method=e.resource_method COLLATE utf8mb4_general_ci
                AND p.sys_code=e.sys_code COLLATE utf8mb4_general_ci
                 WHERE p.resource_id<>e.resource_id COLLATE utf8mb4_general_ci)
            THEN 'REPORT align zero-DDL preflight: 待写资源双向身份冲突'
    END
);
-- 4a) 上一段负责对象/身份冲突；本段再按真正写入的列与已有索引逐项精确核验。
-- 不能只因列名存在就继续：任何类型、长度、NULL、默认值、主键或索引列序不一致均在
-- 首个 DROP/CREATE PROCEDURE 前进入同一硬停止门。白名单表和本期索引若尚不存在，后续
-- 过程会按固定 DDL 创建；若已经存在，则必须完整正确，绝不靠 IF NOT EXISTS 掩盖残缺。
SET @rpt_align_preflight_error := COALESCE(@rpt_align_preflight_error, (
    SELECT CASE
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN'
                 AND ((column_name='screen_code' AND data_type='varchar' AND character_maximum_length=64
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='deleted' AND data_type='tinyint' AND is_nullable='NO'
                       AND CAST(column_default AS CHAR)='0'))) <> 2
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN.screen_code/deleted 列定义异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='PT_RESOURCE'
                 AND ((column_name='RESOURCE_ID' AND data_type='varchar' AND character_maximum_length=20
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='RESOURCE_URL' AND data_type='varchar' AND character_maximum_length=256
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='RESOURCE_METHOD' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='MENU_NAME' AND data_type='varchar' AND character_maximum_length=256
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='MENU_RANK_NO' AND data_type='int' AND is_nullable='YES'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='ISMENU' AND data_type='int' AND is_nullable='YES'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='MENU_ENDFLAG' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='YES' AND column_default='0')
                   OR (column_name='STATUS' AND data_type='int' AND is_nullable='YES'
                       AND CAST(column_default AS CHAR)='0')
                   OR (column_name='SYS_CODE' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='YES' AND column_default='PLATFORM')
                   OR (column_name='CREATE_TIME' AND data_type='datetime' AND is_nullable='YES'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP')
                   OR (column_name='UPDATE_TIME' AND data_type='datetime' AND is_nullable='YES'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP'
                       AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
                   OR (column_name='REMARK' AND data_type='varchar' AND character_maximum_length=100
                       AND is_nullable='YES' AND column_default IS NULL))) <> 12
            THEN 'REPORT align zero-DDL preflight: PT_RESOURCE 目标列定义或长度异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='PT_ROLE'
                 AND ((column_name='ROLE_ID' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='ROLE_CODE' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='RECORD_STATUS' AND data_type='int' AND is_nullable='YES'
                       AND CAST(column_default AS CHAR)='0'))) <> 3
            THEN 'REPORT align zero-DDL preflight: PT_ROLE 目标列定义异常'
        WHEN (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='PT_ROLE_RESOURCE'
                 AND ((column_name='ID' AND data_type='varchar' AND character_maximum_length=32
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='ROLE_ID' AND data_type='varchar' AND character_maximum_length=50
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='RESOURCE_ID' AND data_type='varchar' AND character_maximum_length=20
                       AND is_nullable='NO' AND column_default IS NULL)
                   OR (column_name='SYS_CODE' AND data_type='varchar' AND character_maximum_length=10
                       AND is_nullable='YES' AND column_default='PLATFORM')
                   OR (column_name='CREATE_TIME' AND data_type='datetime' AND is_nullable='YES'
                       AND UPPER(CAST(column_default AS CHAR))='CURRENT_TIMESTAMP'))) <> 5
            THEN 'REPORT align zero-DDL preflight: PT_ROLE_RESOURCE 目标列定义或长度异常'
        WHEN EXISTS (SELECT 1 FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_ACCESS_ROLE')
          AND (SELECT COUNT(*) FROM information_schema.columns
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
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 完整列定义异常'
        WHEN EXISTS (SELECT 1 FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_ACCESS_ROLE')
          AND (COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
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
                            AND table_name='RPT_SCREEN_ACCESS_ROLE' AND index_name='idx_rpt_screen_access_role_status'),-1) <> 1)
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 索引定义异常'
        WHEN COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='PT_RESOURCE' AND index_name='PRIMARY' AND non_unique=0),'') <> 'RESOURCE_ID'
            THEN 'REPORT align zero-DDL preflight: PT_RESOURCE 主键必须为 RESOURCE_ID'
        WHEN NOT EXISTS (SELECT 1 FROM information_schema.statistics
                         WHERE table_schema=DATABASE() AND table_name='PT_RESOURCE' AND non_unique=0
                         GROUP BY index_name
                         HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')='RESOURCE_URL,RESOURCE_METHOD,SYS_CODE')
            THEN 'REPORT align zero-DDL preflight: PT_RESOURCE 缺少 URL/METHOD/SYS_CODE 唯一键'
        WHEN COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='PT_ROLE' AND index_name='PRIMARY' AND non_unique=0),'') <> 'ROLE_ID'
            THEN 'REPORT align zero-DDL preflight: PT_ROLE 主键必须为 ROLE_ID'
        WHEN COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                       FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='PT_ROLE_RESOURCE' AND index_name='PRIMARY' AND non_unique=0),'') <> 'ID'
            THEN 'REPORT align zero-DDL preflight: PT_ROLE_RESOURCE 主键必须为 ID'
        WHEN EXISTS (SELECT 1 FROM information_schema.statistics
                     WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN' AND index_name='idx_scr_scope_group')
          AND (COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                         FROM information_schema.statistics WHERE table_schema=DATABASE()
                           AND table_name='RPT_SCREEN' AND index_name='idx_scr_scope_group'),'') <> 'ORG_SCOPE_MODE,ORG_GROUP_CODE'
               OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                            AND table_name='RPT_SCREEN' AND index_name='idx_scr_scope_group'),-1) <> 1)
            THEN 'REPORT align zero-DDL preflight: idx_scr_scope_group 索引定义异常'
        WHEN EXISTS (SELECT 1 FROM information_schema.statistics
                     WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_DATASOURCE' AND index_name='idx_scr_ds_biz_line')
          AND (COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                         FROM information_schema.statistics WHERE table_schema=DATABASE()
                           AND table_name='RPT_SCREEN_DATASOURCE' AND index_name='idx_scr_ds_biz_line'),'') <> 'BIZ_LINE'
               OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                            AND table_name='RPT_SCREEN_DATASOURCE' AND index_name='idx_scr_ds_biz_line'),-1) <> 1)
            THEN 'REPORT align zero-DDL preflight: idx_scr_ds_biz_line 索引定义异常'
        WHEN EXISTS (SELECT 1 FROM information_schema.statistics
                     WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN' AND index_name='uk_rpt_screen_active_code')
          AND (COALESCE((SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
                         FROM information_schema.statistics WHERE table_schema=DATABASE()
                           AND table_name='RPT_SCREEN' AND index_name='uk_rpt_screen_active_code'),'') <> 'ACTIVE_SCREEN_CODE'
               OR COALESCE((SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
                            AND table_name='RPT_SCREEN' AND index_name='uk_rpt_screen_active_code'),-1) <> 0)
            THEN 'REPORT align zero-DDL preflight: uk_rpt_screen_active_code 索引定义异常'
        WHEN EXISTS (SELECT 1 FROM (
                SELECT 'R_RPT_SCR_AR_LIST' resource_id,'/api/screen/admin/screens/*/access-roles' resource_url,'GET' resource_method,'大屏-查看角色列表' menu_name,'RPT' sys_code,'查询屏级查看角色白名单' remark
                UNION ALL SELECT 'R_RPT_SCR_AR_SAVE','/api/screen/admin/screens/*/access-roles','PUT','大屏-查看角色覆盖保存','RPT','高危覆盖保存屏级查看角色白名单（默认精确授予SYS_ADMIN）'
                UNION ALL SELECT 'R_RPT_SCR_META_SAVE','/api/screen/admin/screens/*/metadata','PUT','大屏-元数据范围更新','RPT','仅更新屏元数据与机构范围，不修改画布区块'
                UNION ALL SELECT 'R_RPT_SCR_DS_PROBE','/api/screen/admin/datasources/*/probe-columns','POST','大屏-数据源列探测','RPT','高危设计器列探测（默认不自动授权）'
            ) expected_resource
            WHERE CHAR_LENGTH(resource_id)>20 OR CHAR_LENGTH(resource_url)>256 OR CHAR_LENGTH(resource_method)>10
               OR CHAR_LENGTH(menu_name)>256 OR CHAR_LENGTH(sys_code)>10 OR CHAR_LENGTH(remark)>100)
            THEN 'REPORT align zero-DDL preflight: 待写 PT_RESOURCE 值超过目标列长度'
    END
));
SELECT COALESCE(@rpt_align_preflight_error,'REPORT align zero-DDL preflight passed') AS preflight_result;
SET @rpt_align_preflight_guard_sql := IF(@rpt_align_preflight_error IS NULL,
    'SELECT ''REPORT align zero-DDL preflight passed'' AS preflight_guard',
    CONCAT('SELECT * FROM __rpt_align_preflight_stop_',REPLACE(UUID(),'-',''),'__'));
PREPARE rpt_align_preflight_guard FROM @rpt_align_preflight_guard_sql;
EXECUTE rpt_align_preflight_guard;
DEALLOCATE PREPARE rpt_align_preflight_guard;

-- 只有硬停止门以成功路径完成，才可执行以下非只读部分。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sp_screen_scope_map_align_20260811;
DELIMITER $$
CREATE PROCEDURE sp_screen_scope_map_align_20260811()
BEGIN
    DECLARE v_count BIGINT DEFAULT 0;
    DECLARE v_columns VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
    DECLARE v_index_non_unique INT DEFAULT -1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        DROP TEMPORARY TABLE IF EXISTS tmp_rpt_scope_resources_20260811;
        RESIGNAL;
    END;

    -- 1. 基础表与既有资源先 fail-fast；本脚本只创建本期白名单表和缺失扩展列。
    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
       AND UPPER(column_name) IN ('ID','SCREEN_CODE','VIEW_LEVEL','STATUS','DELETED',
                                  'CANVAS_DRAFT_JSON','CANVAS_PUBLISHED_JSON','CANVAS_VERSION','PUBLISH_STATUS');
    IF v_count <> 9 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN 缺少画布基础列';
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_DATASOURCE 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE'
       AND UPPER(column_name) IN ('ID','DS_CODE','DS_NAME','SOURCE_KIND','CONFIG_JSON','STATUS','DELETED');
    IF v_count <> 7 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_DATASOURCE 缺少基础列';
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_RESOURCE 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
       AND UPPER(column_name) IN ('RESOURCE_ID','RESOURCE_URL','RESOURCE_METHOD','MENU_NAME','MENU_RANK_NO',
                                  'ISMENU','MENU_ENDFLAG','STATUS','SYS_CODE','CREATE_TIME','UPDATE_TIME','REMARK');
    IF v_count <> 12 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_RESOURCE 缺少必需列';
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
       AND UPPER(column_name) IN ('ROLE_ID','ROLE_CODE','RECORD_STATUS');
    IF v_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE 缺少 ROLE_ID/ROLE_CODE/RECORD_STATUS';
    END IF;

    SELECT COUNT(*) INTO v_count FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_RESOURCE';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE_RESOURCE 不存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_RESOURCE'
       AND UPPER(column_name) IN ('ID','ROLE_ID','RESOURCE_ID','SYS_CODE','CREATE_TIME');
    IF v_count <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE_RESOURCE 缺少必需列';
    END IF;

    -- ------------------------------------------------------------------------
    -- 所有本期目标列、资源值、既有身份和索引均在首个目标 DDL 前预检。任何不兼容
    -- 环境都必须在尚未 ALTER/CREATE 业务表前停下，禁止半执行后再靠人工猜测修复。
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
       AND ((column_name = 'screen_code' AND data_type = 'varchar' AND character_maximum_length = 64
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'deleted' AND data_type = 'tinyint' AND is_nullable = 'NO'
             AND CAST(column_default AS CHAR) = '0'));
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN.screen_code/deleted 列定义异常';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT screen_code FROM RPT_SCREEN WHERE deleted = 0 GROUP BY screen_code HAVING COUNT(*) > 1
    ) AS duplicate_active_screen_code;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN 存在重复活跃 screen_code';
    END IF;

    -- 这些扩展列允许不存在（由本脚本补齐），一旦已存在则必须就是本期精确定义。
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'biz_line') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'biz_line'
           AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO'
           AND column_default = 'COMMON';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN.biz_line 列定义异常';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'org_scope_mode') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'org_scope_mode'
           AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO'
           AND column_default = 'LEGACY_CONTEXT';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN.org_scope_mode 列定义异常';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'org_group_code') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'org_group_code'
           AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES'
           AND column_default IS NULL;
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN.org_group_code 列定义异常';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'updated_by') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'updated_by'
           AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES'
           AND column_default IS NULL;
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN.updated_by 列定义异常';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE' AND column_name = 'biz_line') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE' AND column_name = 'biz_line'
           AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO'
           AND column_default = 'COMMON';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_DATASOURCE.biz_line 列定义异常';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE' AND column_name = 'updated_by') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE' AND column_name = 'updated_by'
           AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'YES'
           AND column_default IS NULL;
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_DATASOURCE.updated_by 列定义异常';
        END IF;
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
       AND index_name = 'idx_scr_scope_group';
    IF v_columns IS NOT NULL AND v_columns <> 'ORG_SCOPE_MODE,ORG_GROUP_CODE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: idx_scr_scope_group 列定义冲突';
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE'
       AND index_name = 'idx_scr_ds_biz_line';
    IF v_columns IS NOT NULL AND v_columns <> 'BIZ_LINE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: idx_scr_ds_biz_line 列定义冲突';
    END IF;

    -- active_screen_code 让软删除记录返回 NULL，从而允许历史删除行复用编码，同时由唯一键
    -- 在并发创建/改名时给出最终串行化裁决。
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'active_screen_code') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'active_screen_code'
           AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES'
           AND UPPER(extra) LIKE '%STORED GENERATED%'
           AND UPPER(generation_expression) LIKE '%SCREEN_CODE%'
           AND UPPER(generation_expression) LIKE '%DELETED%';
        IF v_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: active_screen_code 生成列定义异常';
        END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND index_name = 'uk_rpt_screen_active_code';
    IF v_count > 0 THEN
        SELECT MIN(non_unique) INTO v_index_non_unique FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND index_name = 'uk_rpt_screen_active_code';
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
           AND index_name = 'uk_rpt_screen_active_code';
        IF v_count <> 1 OR v_index_non_unique <> 0 OR COALESCE(v_columns, '') <> 'ACTIVE_SCREEN_CODE' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: uk_rpt_screen_active_code 唯一键异常';
        END IF;
    END IF;

    -- 已存在白名单表必须在首个业务表 DDL 前完整校验；不存在才允许在后续创建。
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE') THEN
        SELECT COUNT(*) INTO v_count FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
           AND ((column_name = 'id' AND data_type = 'bigint' AND is_nullable = 'NO'
                 AND UPPER(extra) LIKE '%AUTO_INCREMENT%')
             OR (column_name = 'screen_id' AND data_type = 'bigint' AND is_nullable = 'NO')
             OR (column_name = 'role_code' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO')
             OR (column_name = 'status' AND data_type = 'varchar' AND character_maximum_length = 10
                 AND is_nullable = 'NO' AND column_default = 'ACTIVE')
             OR (column_name = 'created_by' AND data_type = 'varchar' AND character_maximum_length = 50
                 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'created_time' AND data_type = 'datetime' AND is_nullable = 'NO'
                 AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
             OR (column_name = 'updated_by' AND data_type = 'varchar' AND character_maximum_length = 50
                 AND is_nullable = 'YES' AND column_default IS NULL)
             OR (column_name = 'updated_time' AND data_type = 'datetime' AND is_nullable = 'NO'
                 AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP'
                 AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%'));
        IF v_count <> 8 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_ACCESS_ROLE 列定义异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
           AND index_name = 'uk_rpt_screen_access_role';
        SELECT MIN(non_unique) INTO v_index_non_unique FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
           AND index_name = 'uk_rpt_screen_access_role';
        IF COALESCE(v_columns, '') <> 'SCREEN_ID,ROLE_CODE' OR v_index_non_unique <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_ACCESS_ROLE 唯一键异常';
        END IF;
        SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
          FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
           AND index_name = 'idx_rpt_screen_access_role_status';
        SELECT MIN(non_unique) INTO v_index_non_unique FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE'
           AND index_name = 'idx_rpt_screen_access_role_status';
        IF COALESCE(v_columns, '') <> 'SCREEN_ID,STATUS' OR v_index_non_unique <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_ACCESS_ROLE 状态索引异常';
        END IF;
        SELECT COUNT(*) INTO v_count FROM (
            SELECT screen_id, role_code FROM RPT_SCREEN_ACCESS_ROLE GROUP BY screen_id, role_code HAVING COUNT(*) > 1
        ) AS duplicated_access_role;
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RPT_SCREEN_ACCESS_ROLE 存在重复屏角色';
        END IF;
    END IF;

    -- PT_RESOURCE / PT_ROLE / PT_ROLE_RESOURCE 的写入列全部按当前 auth 数字角色方案精确核验。
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
       AND ((column_name = 'RESOURCE_ID' AND data_type = 'varchar' AND character_maximum_length = 20
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RESOURCE_URL' AND data_type = 'varchar' AND character_maximum_length = 256
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RESOURCE_METHOD' AND data_type = 'varchar' AND character_maximum_length = 10
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'MENU_NAME' AND data_type = 'varchar' AND character_maximum_length = 256
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'MENU_RANK_NO' AND data_type = 'int' AND is_nullable = 'YES'
             AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'ISMENU' AND data_type = 'int' AND is_nullable = 'YES'
             AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'MENU_ENDFLAG' AND data_type = 'varchar' AND character_maximum_length = 10
             AND is_nullable = 'YES' AND column_default = '0')
         OR (column_name = 'STATUS' AND data_type = 'int' AND is_nullable = 'YES'
             AND CAST(column_default AS CHAR) = '0')
         OR (column_name = 'SYS_CODE' AND data_type = 'varchar' AND character_maximum_length = 10
             AND is_nullable = 'YES' AND column_default = 'PLATFORM')
         OR (column_name = 'CREATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES'
             AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP')
         OR (column_name = 'UPDATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES'
             AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP'
             AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
         OR (column_name = 'REMARK' AND data_type = 'varchar' AND character_maximum_length = 100
             AND is_nullable = 'YES' AND column_default IS NULL));
    IF v_count <> 12 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_RESOURCE 目标列定义或长度异常';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
       AND ((column_name = 'ROLE_ID' AND data_type = 'varchar' AND character_maximum_length = 50
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'ROLE_CODE' AND data_type = 'varchar' AND character_maximum_length = 50
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RECORD_STATUS' AND data_type = 'int' AND is_nullable = 'YES'
             AND CAST(column_default AS CHAR) = '0'));
    IF v_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE 目标列定义异常';
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_RESOURCE'
       AND ((column_name = 'ID' AND data_type = 'varchar' AND character_maximum_length = 32
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'ROLE_ID' AND data_type = 'varchar' AND character_maximum_length = 50
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'RESOURCE_ID' AND data_type = 'varchar' AND character_maximum_length = 20
             AND is_nullable = 'NO' AND column_default IS NULL)
         OR (column_name = 'SYS_CODE' AND data_type = 'varchar' AND character_maximum_length = 10
             AND is_nullable = 'YES' AND column_default = 'PLATFORM')
         OR (column_name = 'CREATE_TIME' AND data_type = 'datetime' AND is_nullable = 'YES'
             AND UPPER(CAST(column_default AS CHAR)) = 'CURRENT_TIMESTAMP'));
    IF v_count <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE_RESOURCE 目标列定义或长度异常';
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE'
       AND index_name = 'PRIMARY' AND non_unique = 0;
    IF COALESCE(v_columns, '') <> 'RESOURCE_ID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_RESOURCE 主键必须为 RESOURCE_ID';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT RESOURCE_URL, RESOURCE_METHOD, SYS_CODE FROM PT_RESOURCE
         GROUP BY RESOURCE_URL, RESOURCE_METHOD, SYS_CODE HAVING COUNT(*) > 1
    ) AS duplicate_resource_identity;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_RESOURCE 存在重复 URL/METHOD/SYS_CODE';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT index_name FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'PT_RESOURCE' AND non_unique = 0
         GROUP BY index_name
        HAVING GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',')
               = 'RESOURCE_URL,RESOURCE_METHOD,SYS_CODE'
    ) AS resource_identity_unique_index;
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_RESOURCE 缺少 URL/METHOD/SYS_CODE 唯一键';
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE'
       AND index_name = 'PRIMARY' AND non_unique = 0;
    IF COALESCE(v_columns, '') <> 'ROLE_ID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE 主键必须为 ROLE_ID';
    END IF;
    -- PT_ROLE 在当前 auth 基线中不以 ROLE_CODE 唯一索引作为部署前提；report 只能对自己
    -- 要解析的业务身份做精确判重，避免强迫 auth 增加跨模块索引，也绝不“任选一条”角色。
    SELECT COUNT(*) INTO v_count FROM (
        SELECT ROLE_CODE FROM PT_ROLE
         WHERE ROLE_CODE = 'SYS_ADMIN'
         GROUP BY ROLE_CODE HAVING COUNT(*) <> 1
    ) AS ambiguous_required_role_code;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: SYS_ADMIN ROLE_CODE 不唯一';
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PT_ROLE_RESOURCE'
       AND index_name = 'PRIMARY' AND non_unique = 0;
    IF COALESCE(v_columns, '') <> 'ID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: PT_ROLE_RESOURCE 主键必须为 ID';
    END IF;

    -- 资源 ID 缩短后必须全部适配 varchar(20)；旧超长 ID 若已存在，先人工清理其授权再继续。
    SELECT COUNT(*) INTO v_count FROM PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_SCR_CFG_META_SAVE';
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: 检测到废弃超长 R_RPT_SCR_CFG_META_SAVE，先人工清理';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT 'R_RPT_SCR_AR_LIST' AS resource_id, '/api/screen/admin/screens/*/access-roles' AS resource_url,
               'GET' AS resource_method, '大屏-查看角色列表' AS menu_name, 'RPT' AS sys_code, '查询屏级查看角色白名单' AS remark
        UNION ALL SELECT 'R_RPT_SCR_AR_SAVE', '/api/screen/admin/screens/*/access-roles', 'PUT',
               '大屏-查看角色覆盖保存', 'RPT', '高危覆盖保存屏级查看角色白名单（默认精确授予SYS_ADMIN）'
        UNION ALL SELECT 'R_RPT_SCR_META_SAVE', '/api/screen/admin/screens/*/metadata', 'PUT',
               '大屏-元数据范围更新', 'RPT', '仅更新屏元数据与机构范围，不修改画布区块'
        UNION ALL SELECT 'R_RPT_SCR_DS_PROBE', '/api/screen/admin/datasources/*/probe-columns', 'POST',
               '大屏-数据源列探测', 'RPT', '高危设计器列探测（默认不自动授权）'
    ) AS expected_resource
    WHERE CHAR_LENGTH(resource_id) > 20 OR CHAR_LENGTH(resource_url) > 256
       OR CHAR_LENGTH(resource_method) > 10 OR CHAR_LENGTH(menu_name) > 256
       OR CHAR_LENGTH(sys_code) > 10 OR CHAR_LENGTH(remark) > 100;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: 待写 PT_RESOURCE 值超过目标列长度';
    END IF;
    SELECT COUNT(*) INTO v_count FROM PT_RESOURCE p JOIN (
        SELECT 'R_RPT_SCR_AR_LIST' AS resource_id, '/api/screen/admin/screens/*/access-roles' AS resource_url, 'GET' AS resource_method, 'RPT' AS sys_code
        UNION ALL SELECT 'R_RPT_SCR_AR_SAVE', '/api/screen/admin/screens/*/access-roles', 'PUT', 'RPT'
        UNION ALL SELECT 'R_RPT_SCR_META_SAVE', '/api/screen/admin/screens/*/metadata', 'PUT', 'RPT'
        UNION ALL SELECT 'R_RPT_SCR_DS_PROBE', '/api/screen/admin/datasources/*/probe-columns', 'POST', 'RPT'
    ) AS expected_resource ON p.RESOURCE_ID = expected_resource.resource_id COLLATE utf8mb4_general_ci
     WHERE NOT (p.RESOURCE_URL <=> expected_resource.resource_url COLLATE utf8mb4_general_ci
            AND p.RESOURCE_METHOD <=> expected_resource.resource_method COLLATE utf8mb4_general_ci
            AND p.SYS_CODE <=> expected_resource.sys_code COLLATE utf8mb4_general_ci);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RESOURCE_ID 已指向不同 URL/METHOD/SYS_CODE';
    END IF;
    SELECT COUNT(*) INTO v_count FROM PT_RESOURCE p JOIN (
        SELECT 'R_RPT_SCR_AR_LIST' AS resource_id, '/api/screen/admin/screens/*/access-roles' AS resource_url, 'GET' AS resource_method, 'RPT' AS sys_code
        UNION ALL SELECT 'R_RPT_SCR_AR_SAVE', '/api/screen/admin/screens/*/access-roles', 'PUT', 'RPT'
        UNION ALL SELECT 'R_RPT_SCR_META_SAVE', '/api/screen/admin/screens/*/metadata', 'PUT', 'RPT'
        UNION ALL SELECT 'R_RPT_SCR_DS_PROBE', '/api/screen/admin/datasources/*/probe-columns', 'POST', 'RPT'
    ) AS expected_resource ON p.RESOURCE_URL = expected_resource.resource_url COLLATE utf8mb4_general_ci
       AND p.RESOURCE_METHOD = expected_resource.resource_method COLLATE utf8mb4_general_ci
       AND p.SYS_CODE = expected_resource.sys_code COLLATE utf8mb4_general_ci
     WHERE p.RESOURCE_ID <> expected_resource.resource_id COLLATE utf8mb4_general_ci;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: URL/METHOD/SYS_CODE 已绑定不同 RESOURCE_ID';
    END IF;

    -- ROLE_CODE 是跨模块业务身份。此处不创建角色、不假设历史管理员别名，也不接受重复或非数字 SYS_ADMIN ID。
    SELECT COUNT(*) INTO v_count FROM PT_ROLE WHERE ROLE_CODE = 'SYS_ADMIN';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: SYS_ADMIN ROLE_CODE 必须唯一且已存在';
    END IF;
    SELECT COUNT(*) INTO v_count FROM PT_ROLE
     WHERE ROLE_CODE = 'SYS_ADMIN' AND CAST(ROLE_ID AS CHAR) NOT REGEXP '^[0-9]+$';
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: SYS_ADMIN.ROLE_ID 必须为当前数字 ID';
    END IF;

    -- 2. 通过上述全部预检后，才开始补本期扩展列/索引。软删除唯一键先建立，消除
    -- screenCode 创建/改名竞态；deleted=1 时生成 NULL，历史删除记录不妨碍复用编码。
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'active_screen_code') THEN
        ALTER TABLE RPT_SCREEN ADD COLUMN active_screen_code VARCHAR(64)
            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN screen_code ELSE NULL END) STORED
            COMMENT 'deleted=0 时的唯一 screen_code' AFTER screen_code;
    END IF;
    SELECT COUNT(*) INTO v_count FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND index_name = 'uk_rpt_screen_active_code';
    IF v_count = 0 THEN
        ALTER TABLE RPT_SCREEN ADD UNIQUE KEY uk_rpt_screen_active_code (active_screen_code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'biz_line') THEN
        ALTER TABLE RPT_SCREEN ADD COLUMN biz_line VARCHAR(20) NOT NULL DEFAULT 'COMMON'
            COMMENT '屏级业务条线：CORP/RETAIL/COMMON' AFTER view_level;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'org_scope_mode') THEN
        ALTER TABLE RPT_SCREEN ADD COLUMN org_scope_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY_CONTEXT'
            COMMENT '机构范围：LEGACY_CONTEXT/NAMED_GROUP' AFTER biz_line;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'org_group_code') THEN
        ALTER TABLE RPT_SCREEN ADD COLUMN org_group_code VARCHAR(64) NULL
            COMMENT '命名机构组编码（auth 逻辑引用）' AFTER org_scope_mode;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN' AND column_name = 'updated_by') THEN
        ALTER TABLE RPT_SCREEN ADD COLUMN updated_by VARCHAR(50) NULL COMMENT '最近配置更新人' AFTER created_by;
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN'
       AND index_name = 'idx_scr_scope_group';
    IF v_columns IS NOT NULL AND v_columns <> 'ORG_SCOPE_MODE,ORG_GROUP_CODE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: idx_scr_scope_group 列定义冲突';
    END IF;
    IF v_columns IS NULL THEN
        ALTER TABLE RPT_SCREEN ADD KEY idx_scr_scope_group (org_scope_mode, org_group_code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE' AND column_name = 'biz_line') THEN
        ALTER TABLE RPT_SCREEN_DATASOURCE ADD COLUMN biz_line VARCHAR(20) NOT NULL DEFAULT 'COMMON'
            COMMENT '数据归属条线：CORP/RETAIL/COMMON' AFTER source_kind;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE' AND column_name = 'updated_by') THEN
        ALTER TABLE RPT_SCREEN_DATASOURCE ADD COLUMN updated_by VARCHAR(50) NULL COMMENT '最近更新人' AFTER created_by;
    END IF;
    SELECT GROUP_CONCAT(UPPER(column_name) ORDER BY seq_in_index SEPARATOR ',') INTO v_columns
      FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_DATASOURCE'
       AND index_name = 'idx_scr_ds_biz_line';
    IF v_columns IS NOT NULL AND v_columns <> 'BIZ_LINE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: idx_scr_ds_biz_line 列定义冲突';
    END IF;
    IF v_columns IS NULL THEN
        ALTER TABLE RPT_SCREEN_DATASOURCE ADD KEY idx_scr_ds_biz_line (biz_line);
    END IF;

    -- 3. 白名单表：存在时已经在首个 DDL 前完整预检；此处只允许缺失时按固定定义创建。
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'RPT_SCREEN_ACCESS_ROLE') THEN
        CREATE TABLE RPT_SCREEN_ACCESS_ROLE (
            id BIGINT NOT NULL AUTO_INCREMENT, screen_id BIGINT NOT NULL, role_code VARCHAR(50) NOT NULL,
            status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE', created_by VARCHAR(50) NULL,
            created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by VARCHAR(50) NULL,
            updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id), UNIQUE KEY uk_rpt_screen_access_role (screen_id, role_code),
            KEY idx_rpt_screen_access_role_status (screen_id, status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏级查看角色白名单';
    END IF;

    -- 4. 存量兼容只针对历史空值；新建接口已强制显式字段。复跑时 WHERE 不命中，保持零 diff。
    UPDATE RPT_SCREEN SET biz_line = 'COMMON' WHERE biz_line IS NULL OR TRIM(biz_line) = '';
    UPDATE RPT_SCREEN SET org_scope_mode = 'LEGACY_CONTEXT'
     WHERE org_scope_mode IS NULL OR TRIM(org_scope_mode) = '';
    UPDATE RPT_SCREEN_DATASOURCE SET biz_line = 'COMMON' WHERE biz_line IS NULL OR TRIM(biz_line) = '';

    -- 5. 资源身份双向预检，再插入缺失资源。资源 ID 与 URL+METHOD+SYS_CODE 任一冲突均停止。
    DROP TEMPORARY TABLE IF EXISTS tmp_rpt_scope_resources_20260811;
    CREATE TEMPORARY TABLE tmp_rpt_scope_resources_20260811 (
        resource_id VARCHAR(20) NOT NULL, resource_url VARCHAR(256) NOT NULL,
        resource_method VARCHAR(10) NOT NULL, menu_name VARCHAR(256) NOT NULL,
        remark VARCHAR(100) NOT NULL, PRIMARY KEY (resource_id),
        UNIQUE KEY uk_tmp_rpt_scope_resource_identity (resource_url, resource_method)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
    INSERT INTO tmp_rpt_scope_resources_20260811 VALUES
      ('R_RPT_SCR_AR_LIST','/api/screen/admin/screens/*/access-roles','GET','大屏-查看角色列表','查询屏级查看角色白名单'),
      ('R_RPT_SCR_AR_SAVE','/api/screen/admin/screens/*/access-roles','PUT','大屏-查看角色覆盖保存','高危覆盖保存屏级查看角色白名单（默认精确授予SYS_ADMIN）'),
      ('R_RPT_SCR_META_SAVE','/api/screen/admin/screens/*/metadata','PUT','大屏-元数据范围更新','仅更新屏元数据与机构范围，不修改画布区块'),
      ('R_RPT_SCR_DS_PROBE','/api/screen/admin/datasources/*/probe-columns','POST','大屏-数据源列探测','高危设计器列探测（默认不自动授权）');

    SELECT COUNT(*) INTO v_count FROM PT_RESOURCE p JOIN tmp_rpt_scope_resources_20260811 e
      ON p.RESOURCE_ID = e.resource_id
     WHERE NOT (p.RESOURCE_URL <=> e.resource_url AND p.RESOURCE_METHOD <=> e.resource_method AND p.SYS_CODE <=> 'RPT');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: RESOURCE_ID 已指向不同 URL/METHOD/SYS_CODE';
    END IF;
    SELECT COUNT(*) INTO v_count FROM PT_RESOURCE p JOIN tmp_rpt_scope_resources_20260811 e
      ON p.RESOURCE_URL = e.resource_url AND p.RESOURCE_METHOD = e.resource_method AND p.SYS_CODE = 'RPT'
     WHERE p.RESOURCE_ID <> e.resource_id;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: URL/METHOD/SYS_CODE 已绑定不同 RESOURCE_ID';
    END IF;
    INSERT INTO PT_RESOURCE (RESOURCE_ID,RESOURCE_URL,RESOURCE_METHOD,MENU_NAME,MENU_RANK_NO,ISMENU,MENU_ENDFLAG,
                             STATUS,SYS_CODE,CREATE_TIME,UPDATE_TIME,REMARK)
    SELECT e.resource_id,e.resource_url,e.resource_method,e.menu_name,0,0,'0',0,'RPT',NOW(),NOW(),e.remark
      FROM tmp_rpt_scope_resources_20260811 e
     WHERE NOT EXISTS (SELECT 1 FROM PT_RESOURCE p WHERE p.RESOURCE_ID = e.resource_id);

    -- 6. 授权只从当前数字 ROLE_ID + ROLE_CODE 解析。禁止历史字符串角色 ID 进入新绑定。
    SELECT COUNT(*) INTO v_count FROM PT_RESOURCE
     WHERE RESOURCE_ID IN ('R_RPT_SCR_CFG_GET','R_RPT_SCR_CFG_SAVE');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: 缺少既有屏读取/配置资源';
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT ROLE_ID, RESOURCE_ID FROM PT_ROLE_RESOURCE
         WHERE RESOURCE_ID IN ('R_RPT_SCR_AR_LIST','R_RPT_SCR_AR_SAVE','R_RPT_SCR_META_SAVE','R_RPT_SCR_DS_PROBE')
         GROUP BY ROLE_ID, RESOURCE_ID HAVING COUNT(*) > 1
    ) AS duplicated_role_resource;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'REPORT preflight: 目标 PT_ROLE_RESOURCE 存在重复绑定';
    END IF;

    INSERT INTO PT_ROLE_RESOURCE (ID,ROLE_ID,RESOURCE_ID,SYS_CODE,CREATE_TIME)
    SELECT REPLACE(UUID(),'-',''), r.ROLE_ID, 'R_RPT_SCR_AR_LIST', 'PLATFORM', NOW()
      FROM PT_ROLE_RESOURCE r JOIN PT_ROLE role_def ON role_def.ROLE_ID = r.ROLE_ID
     WHERE r.RESOURCE_ID = 'R_RPT_SCR_CFG_GET' AND role_def.RECORD_STATUS = 0
       AND CAST(r.ROLE_ID AS CHAR) REGEXP '^[0-9]+$'
       AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE x
                        WHERE x.ROLE_ID = r.ROLE_ID AND x.RESOURCE_ID = 'R_RPT_SCR_AR_LIST');
    INSERT INTO PT_ROLE_RESOURCE (ID,ROLE_ID,RESOURCE_ID,SYS_CODE,CREATE_TIME)
    SELECT REPLACE(UUID(),'-',''), r.ROLE_ID, 'R_RPT_SCR_META_SAVE', 'PLATFORM', NOW()
      FROM PT_ROLE_RESOURCE r JOIN PT_ROLE role_def ON role_def.ROLE_ID = r.ROLE_ID
     WHERE r.RESOURCE_ID = 'R_RPT_SCR_CFG_SAVE' AND role_def.RECORD_STATUS = 0
       AND CAST(r.ROLE_ID AS CHAR) REGEXP '^[0-9]+$'
       AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE x
                        WHERE x.ROLE_ID = r.ROLE_ID AND x.RESOURCE_ID = 'R_RPT_SCR_META_SAVE');
    INSERT INTO PT_ROLE_RESOURCE (ID,ROLE_ID,RESOURCE_ID,SYS_CODE,CREATE_TIME)
    SELECT REPLACE(UUID(),'-',''), admin_role.ROLE_ID, e.resource_id, 'PLATFORM', NOW()
      FROM PT_ROLE admin_role CROSS JOIN tmp_rpt_scope_resources_20260811 e
     WHERE admin_role.ROLE_CODE = 'SYS_ADMIN' AND admin_role.RECORD_STATUS = 0
       AND CAST(admin_role.ROLE_ID AS CHAR) REGEXP '^[0-9]+$'
       -- AR_SAVE 仅由当前唯一 SYS_ADMIN 的 ROLE_CODE 精确授予，绝不从 R_RPT_SQL_EXEC 推导；
       -- DS_PROBE 仍须由权限负责人另行审批，不能随管理员资源自动扩大。
       AND e.resource_id <> 'R_RPT_SCR_DS_PROBE'
       AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE x
                        WHERE x.ROLE_ID = admin_role.ROLE_ID AND x.RESOURCE_ID = e.resource_id);

    DROP TEMPORARY TABLE IF EXISTS tmp_rpt_scope_resources_20260811;
END$$
DELIMITER ;

CALL sp_screen_scope_map_align_20260811();
DROP PROCEDURE IF EXISTS sp_screen_scope_map_align_20260811;

-- ---------------------------------------------------------------------------
-- 待审批、默认不执行：DS_PROBE 的授权受众必须由权限负责人显式确认。严禁把
-- R_RPT_SQL_EXEC 的现有受众自动复制到该资源；AR_SAVE 的默认授权已在上方仅按唯一
-- SYS_ADMIN ROLE_CODE 写入，不依赖 SQL 探查资源。若获批准，请在独立变更单中以当前
-- 数字 ROLE_ID + ROLE_CODE 选择性执行类似下方语句，并先完成隔离 yiti_test 双跑与授权 diff。
--
-- INSERT INTO PT_ROLE_RESOURCE (ID,ROLE_ID,RESOURCE_ID,SYS_CODE,CREATE_TIME)
-- SELECT REPLACE(UUID(),'-',''), role_def.ROLE_ID, 'R_RPT_SCR_DS_PROBE', 'PLATFORM', NOW()
--   FROM PT_ROLE role_def
--  WHERE role_def.ROLE_CODE IN ('<APPROVED_ROLE_CODE>')
--    AND role_def.RECORD_STATUS = 0
--    AND CAST(role_def.ROLE_ID AS CHAR) REGEXP '^[0-9]+$'
--    AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE existing_binding
--                    WHERE existing_binding.ROLE_ID = role_def.ROLE_ID
--                      AND existing_binding.RESOURCE_ID = 'R_RPT_SCR_DS_PROBE');
-- ---------------------------------------------------------------------------

-- 执行后只读核验；二次执行应不再新增列、索引、资源、角色资源或回填行。
SELECT 'RPT_SCREEN' AS table_name, COUNT(*) AS row_count FROM RPT_SCREEN;
SELECT 'RPT_SCREEN_DATASOURCE' AS table_name, COUNT(*) AS row_count FROM RPT_SCREEN_DATASOURCE;
SELECT 'RPT_SCREEN_ACCESS_ROLE' AS table_name, COUNT(*) AS row_count FROM RPT_SCREEN_ACCESS_ROLE;
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE FROM PT_RESOURCE
 WHERE RESOURCE_ID IN ('R_RPT_SCR_AR_LIST','R_RPT_SCR_AR_SAVE','R_RPT_SCR_META_SAVE','R_RPT_SCR_DS_PROBE');
SELECT role_def.ROLE_ID, role_def.ROLE_CODE, rr.RESOURCE_ID
  FROM PT_ROLE_RESOURCE rr JOIN PT_ROLE role_def ON role_def.ROLE_ID = rr.ROLE_ID
 WHERE rr.RESOURCE_ID IN ('R_RPT_SCR_AR_LIST','R_RPT_SCR_AR_SAVE','R_RPT_SCR_META_SAVE','R_RPT_SCR_DS_PROBE')
 ORDER BY rr.RESOURCE_ID, CAST(role_def.ROLE_ID AS UNSIGNED);
