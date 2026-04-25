-- =====================================================================
-- report-analytics-center M1 阶段 PT_RESOURCE 注册（Task M1.6.1）
-- Version: V1_0_1
-- Date: 2026-04-25
--
-- 注册 8 个 M1 REST 端点：
--   - A 章 动态查询 3 个：/query-dimensions, /dynamic-query, /dynamic-query/export
--   - B 章 查询方案 5 个：GET 列表 / GET 详情 / POST / PUT / DELETE
--
-- v1.0 note: 资源命名前缀 R_RPT_*，SYS_CODE='RPT'，对照 perf 模块 P_PERF_* 命名风格。
--   细粒度授权由 @BizAuth.action（READ/LIST/WRITE/DELETE/EXPORT）+ RESOURCE_ID 联合判定，
--   BizType 单档使用 common-security BizType.REPORT。
--
-- 依赖前置：本脚本运行环境必须先有 pt_resource / pt_role / pt_role_resource / pt_role_biz_scope 4 张表
-- （由 auth-permission-center 的基线 DDL 创建）。生产环境通常已就绪；测试环境 onepl_test_v103
-- 库已经从平台 DDL 整体导入。
--
-- 幂等设计：使用 INSERT ... ON DUPLICATE KEY UPDATE 让脚本可重复运行，避免 Flyway 失败留痕。
-- =====================================================================

-- 1. 注册 8 个 REST endpoint（idempotent：已存在则覆盖 REMARK）
INSERT INTO pt_resource (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('R_RPT_META_QD',     '/api/reports/query-dimensions',           'GET',    '维度+指标树',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.1'),
('R_RPT_DQ_EXEC',     '/api/reports/dynamic-query',              'POST',   '动态查询执行',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.2'),
('R_RPT_DQ_EXPORT',   '/api/reports/dynamic-query/export',       'POST',   '动态查询导出',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.3 占位'),
('R_RPT_SQ_LIST',     '/api/reports/saved-queries',              'GET',    '查询方案列表',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.4'),
('R_RPT_SQ_GET',      '/api/reports/saved-queries/*',            'GET',    '查询方案详情',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.4'),
('R_RPT_SQ_SAVE',     '/api/reports/saved-queries',              'POST',   '保存查询方案',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.5'),
('R_RPT_SQ_UPD',      '/api/reports/saved-queries/*',            'PUT',    '更新查询方案',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.5'),
('R_RPT_SQ_DEL',      '/api/reports/saved-queries/*',            'DELETE', '删除查询方案',         NULL, 0, 0, '0', NULL, 0, 'RPT', NOW(), 'seed', 'v1.0 M1.5')
ON DUPLICATE KEY UPDATE REMARK = VALUES(REMARK), UPDATE_TIME = NOW();

-- 2. 角色-资源绑定：R_ADMIN + R_BACK_TECH 默认获得全部 8 条
-- pt_role_resource 真实列：ID(PK) / ROLE_ID / RESOURCE_ID / SYS_CODE / CREATE_TIME（无 CREATE_USER）
-- pt_role_resource 没有 (ROLE_ID, RESOURCE_ID) 唯一键, 用 NOT EXISTS 兜底幂等
INSERT INTO pt_role_resource (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'RPT', NOW()
  FROM pt_role r
  CROSS JOIN (
        SELECT 'R_RPT_META_QD'    AS RESOURCE_ID UNION ALL
        SELECT 'R_RPT_DQ_EXEC'    UNION ALL
        SELECT 'R_RPT_DQ_EXPORT'  UNION ALL
        SELECT 'R_RPT_SQ_LIST'    UNION ALL
        SELECT 'R_RPT_SQ_GET'     UNION ALL
        SELECT 'R_RPT_SQ_SAVE'    UNION ALL
        SELECT 'R_RPT_SQ_UPD'     UNION ALL
        SELECT 'R_RPT_SQ_DEL'
  ) res
 WHERE r.ROLE_CODE IN ('R_ADMIN', 'R_BACK_TECH')
   AND NOT EXISTS (
       SELECT 1 FROM pt_role_resource prr
        WHERE prr.ROLE_ID = r.ROLE_ID AND prr.RESOURCE_ID = res.RESOURCE_ID
   );

-- 3. BizScope 兜底：R_ADMIN + R_BACK_TECH 拥有 REPORT 全部数据范围（DataScope ALL）
-- pt_role_biz_scope 真实列：ID(PK) / ROLE_ID / BIZ_TYPE / DATA_SCOPE / RECORD_STATUS / CREATE_TIME / CREATE_USER
-- 也没有唯一键，用 NOT EXISTS 兜底幂等
INSERT INTO pt_role_biz_scope (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, 'REPORT', 'ALL', 0, NOW(), 'seed'
  FROM pt_role r
 WHERE r.ROLE_CODE IN ('R_ADMIN', 'R_BACK_TECH')
   AND NOT EXISTS (
       SELECT 1 FROM pt_role_biz_scope prbs
        WHERE prbs.ROLE_ID = r.ROLE_ID AND prbs.BIZ_TYPE = 'REPORT'
   );
