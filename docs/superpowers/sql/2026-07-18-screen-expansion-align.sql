-- ============================================================================
-- 大屏扩充（2026-07-17 设计 spec §3/§7）落地对齐脚本
-- 文件：docs/superpowers/sql/2026-07-18-screen-expansion-align.sql
-- 设计：docs/superpowers/specs/2026-07-17-screen-designer-expansion-design.md
--
-- 内容：
--   1) PT_RESOURCE 新增 R_RPT_SCR_KPI_SCH（GET /api/screen/admin/kpi-schemes，KPI 方案下拉）
--      + PT_ROLE_RESOURCE 绑定（复制 R_RPT_SCR_DS_LIST 的角色集，含 SYS_ADMIN ROLE_ID=1）
--   2) PT_ROLE_BIZ_SCOPE 核查补缺：大屏取数/查看角色（ROLE_ID 1/2/3）确保存在 REPORT 行，
--      缺失才补 DATA_SCOPE='ALL'；已有行一律不覆盖（避免误改其他角色既有行级授权）
--   3) RPT_SCREEN_DATASOURCE 新种子 4 条（id 9008~9011，ds_code SCRDS_SEED08~11）：
--      9008 个人KPI细项快照(KPI_DETAIL/SNAPSHOT/EMP/KPI003，含 fieldMeta：完成率 unit '%')
--      9009 个人KPI细项趋势(KPI_DETAIL/TREND，3 个真实指标 M_0004/M_0005/M_0006)
--      9010 全省存款聚合单值(WIDE_TABLE + aggregation groupBy NONE SUM + scopeMode GLOBAL)
--      9011 机构存款排名(WIDE_TABLE + aggregation groupBy SUBJECT SUM + filters IN 10 支行，
--           替代原 CUSTOM_SQL 排名（SCRDS_SEED04）的引导式聚合示范)
--
-- 前置：
--   * 目标库 yiti；执行前已 mysqldump 备份 8 张相关表 →
--     docs/superpowers/sql/backup/2026-07-18-screen-expansion-pre-backup.sql
--   * 4 条数据源 config_json 已经 POST /api/screen/admin/datasources/try-run 实测通过
--     （2026-07-18，KPI 快照/趋势带 contextParams.empId=11051776，聚合/排名无主体参数）
--   * 幂等：全部写法可重复执行；不重跑任何既有 DDL 基线脚本
--
-- 已知数据现状（非脚本问题，验收时注意）：
--   * PERF_KPI_SCORE KPI003/EMP 全部 target_value=0 → 完成率列按设计算 NULL（前端显示"—"）、
--     缺口为负数（前端渲染"已超额"）；得分全 0（T+1 快照数据现状）
--   * ORG_INDEX_RESULT V1 版 val_12(M_0265 月均余额) 2026-06-16 后断档全 NULL，
--     val_51(M_0266 较上月净增) 持续有值至 2026-07-16 → 排名/聚合以 M_0266 为第一指标
-- ============================================================================

USE yiti;

-- ----------------------------------------------------------------------------
-- 1) PT_RESOURCE：R_RPT_SCR_KPI_SCH（字段风格对齐既有 R_RPT_SCR_* 行：
--    ISMENU=0 / MENU_ENDFLAG='0' / STATUS=0 / SYS_CODE='RPT' / 无父节点）
-- ----------------------------------------------------------------------------
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, REMARK)
VALUES
    ('R_RPT_SCR_KPI_SCH', '/api/screen/admin/kpi-schemes', 'GET', '大屏-KPI方案下拉', NULL,
     0, 0, '0', NULL, 0, 'RPT',
     'align-20260718', 'spec 2026-07-17 §3.1/§7 KPI_DETAIL 配置下拉')
ON DUPLICATE KEY UPDATE
    RESOURCE_URL    = VALUES(RESOURCE_URL),
    RESOURCE_METHOD = VALUES(RESOURCE_METHOD),
    MENU_NAME       = VALUES(MENU_NAME),
    STATUS          = 0,
    SYS_CODE        = 'RPT';

-- 角色绑定：动态复制 R_RPT_SCR_DS_LIST 的现有角色集（当前含 ROLE_ID 1/3/129/208/237/238，
-- 其中 ROLE_ID=1 即 SYS_ADMIN——历史脚本中 'R_ADMIN' 字面量对应的真实管理员角色）。
-- 幂等：已绑定的 (ROLE_ID, RESOURCE_ID) 不重复插入。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(rr.ROLE_ID, '#R_RPT_SCR_KPI_SCH')), rr.ROLE_ID, 'R_RPT_SCR_KPI_SCH', rr.SYS_CODE
FROM PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'R_RPT_SCR_DS_LIST'
  AND NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE x
                  WHERE x.ROLE_ID = rr.ROLE_ID AND x.RESOURCE_ID = 'R_RPT_SCR_KPI_SCH');

-- SYS_ADMIN（ROLE_ID=1）兜底行：即便未来 R_RPT_SCR_DS_LIST 绑定被清空，管理员也不丢该资源
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT('1', '#R_RPT_SCR_KPI_SCH')), '1', 'R_RPT_SCR_KPI_SCH', 'PLATFORM'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE_RESOURCE x
                  WHERE x.ROLE_ID = '1' AND x.RESOURCE_ID = 'R_RPT_SCR_KPI_SCH');

-- ----------------------------------------------------------------------------
-- 2) PT_ROLE_BIZ_SCOPE 核查补缺（BizType=REPORT，fail-close：无行即拒绝 RPT-43013）
--    2026-07-18 核查现状（SELECT * FROM PT_ROLE_BIZ_SCOPE WHERE BIZ_TYPE='REPORT'）：
--      ROLE_ID=1 (SYS_ADMIN)   → S_ADMIN_REPORT        DATA_SCOPE='ALL'  已存在 ✓
--      ROLE_ID=2 (BRANCH_PRE)  → S_PR_REPORT           DATA_SCOPE='ALL'  已存在 ✓
--      ROLE_ID=3 (BRANCH_EMP)  → RBS_R_BRANCH_EMP_REPORT DATA_SCOPE='ALL' 已存在 ✓
--    （1/2/3 即 R_RPT_SCR_DATA / R_RPT_SCR_VIEW 绑定的全部大屏取数/查看角色）
--    下面三条为防新库/回灌缺行的幂等补缺；本库执行为 no-op。
--    注意：仅"缺行才补"，绝不 UPDATE 既有行（129/208/237/238 等配置后台角色未绑
--    R_RPT_SCR_DATA，保持其现有 SELF/ALL 行级授权不动，防权限放大）。
-- ----------------------------------------------------------------------------
INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, REMARK)
SELECT 'RBS_1_REPORT_ALL', '1', 'REPORT', 'ALL', 0, 'align-20260718', '大屏取数兜底(SYS_ADMIN)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE_BIZ_SCOPE x WHERE x.ROLE_ID = '1' AND x.BIZ_TYPE = 'REPORT');

INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, REMARK)
SELECT 'RBS_2_REPORT_ALL', '2', 'REPORT', 'ALL', 0, 'align-20260718', '大屏取数兜底(BRANCH_PRE)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE_BIZ_SCOPE x WHERE x.ROLE_ID = '2' AND x.BIZ_TYPE = 'REPORT');

INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, REMARK)
SELECT 'RBS_3_REPORT_ALL', '3', 'REPORT', 'ALL', 0, 'align-20260718', '大屏取数兜底(BRANCH_EMP)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE_BIZ_SCOPE x WHERE x.ROLE_ID = '3' AND x.BIZ_TYPE = 'REPORT');

-- ----------------------------------------------------------------------------
-- 3) RPT_SCREEN_DATASOURCE 新种子 9008~9011（config 已 try-run 实测；幂等 upsert by 主键 id）
-- ----------------------------------------------------------------------------

-- 9008 个人KPI细项快照：ds_type=SINGLE（SNAPSHOT 强制），fieldMeta 示范（完成率 unit '%'）
INSERT INTO RPT_SCREEN_DATASOURCE
    (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by, deleted)
VALUES
    (9008, 'SCRDS_SEED08', '个人KPI细项快照(机构负责人方案)', 'SINGLE', 'KPI_DETAIL',
     '{"schemaVersion":2,"scopeMode":"SUBJECT","schemeCode":"KPI003","subjectType":"EMP","mode":"SNAPSHOT","fieldMeta":[{"col":"细项名称","role":"DIM"},{"col":"目标值","role":"METRIC","decimals":2},{"col":"实际值","role":"METRIC","decimals":2},{"col":"权重","role":"METRIC","decimals":2},{"col":"得分","role":"METRIC","decimals":2},{"col":"完成率","role":"METRIC","unit":"%","decimals":1},{"col":"缺口","role":"METRIC","decimals":2}]}',
     '["LATEST"]', 'ACTIVE', '大屏扩充种子：KPI_DETAIL SNAPSHOT（spec §3.1，T+1 快照）', 'SEED', 0)
ON DUPLICATE KEY UPDATE
    ds_code = VALUES(ds_code), ds_name = VALUES(ds_name), ds_type = VALUES(ds_type),
    source_kind = VALUES(source_kind), config_json = VALUES(config_json),
    time_param_json = VALUES(time_param_json), status = VALUES(status), remark = VALUES(remark);

-- 9009 个人KPI细项趋势：ds_type=TIMESERIES（TREND 强制），metricName 为 PERF_METRIC_DEF 真实名快照
INSERT INTO RPT_SCREEN_DATASOURCE
    (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by, deleted)
VALUES
    (9009, 'SCRDS_SEED09', '个人KPI细项趋势(机构负责人方案)', 'TIMESERIES', 'KPI_DETAIL',
     '{"schemaVersion":2,"scopeMode":"SUBJECT","schemeCode":"KPI003","subjectType":"EMP","mode":"TREND","valueCol":"score","metrics":[{"metricCode":"M_0004","metricName":"一般性存款季日均余额较上季-员工"},{"metricCode":"M_0005","metricName":"一般性存款年日均余额-员工"},{"metricCode":"M_0006","metricName":"一般性存款年日均余额较上年-员工"}],"fieldMeta":[{"col":"data_date","role":"DIM"},{"col":"一般性存款季日均余额较上季-员工","role":"METRIC","decimals":2},{"col":"一般性存款年日均余额-员工","role":"METRIC","decimals":2},{"col":"一般性存款年日均余额较上年-员工","role":"METRIC","decimals":2}]}',
     '["LATEST","LAST_10D","LAST_1M","LAST_6M_EOM"]', 'ACTIVE',
     '大屏扩充种子：KPI_DETAIL TREND（data_date × 细项得分透视）', 'SEED', 0)
ON DUPLICATE KEY UPDATE
    ds_code = VALUES(ds_code), ds_name = VALUES(ds_name), ds_type = VALUES(ds_type),
    source_kind = VALUES(source_kind), config_json = VALUES(config_json),
    time_param_json = VALUES(time_param_json), status = VALUES(status), remark = VALUES(remark);

-- 9010 全省存款聚合(单值)：aggregation groupBy=NONE + SUM → ds_type 强制 SINGLE；
--      scopeMode=GLOBAL（无主体参数，DATA_SCOPE 仅 ALL/省级 ORG_SUBTREE 放行）
INSERT INTO RPT_SCREEN_DATASOURCE
    (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by, deleted)
VALUES
    (9010, 'SCRDS_SEED10', '全省存款聚合(引导式单值)', 'SINGLE', 'WIDE_TABLE',
     '{"schemaVersion":2,"scopeMode":"GLOBAL","table":"ORG_INDEX_RESULT","subjectCol":"org_code","subjectParam":"orgCode","metrics":[{"metricCode":"M_0266","metricName":"一般性存款月均余额较上月-机构","slot":51},{"metricCode":"M_0265","metricName":"一般性存款月均余额-机构","slot":12}],"aggregation":{"groupBy":"NONE","agg":"SUM"},"fieldMeta":[{"col":"一般性存款月均余额较上月-机构","alias":"全省存款较上月净增","role":"METRIC","unit":"万元","decimals":2},{"col":"一般性存款月均余额-机构","alias":"全省存款月均余额","role":"METRIC","unit":"万元","decimals":2}]}',
     NULL, 'ACTIVE', '大屏扩充种子：WIDE_TABLE 聚合 NONE/SUM（spec §3.3，替代 CUSTOM_SQL 全省单值的引导式示范）', 'SEED', 0)
ON DUPLICATE KEY UPDATE
    ds_code = VALUES(ds_code), ds_name = VALUES(ds_name), ds_type = VALUES(ds_type),
    source_kind = VALUES(source_kind), config_json = VALUES(config_json),
    time_param_json = VALUES(time_param_json), status = VALUES(status), remark = VALUES(remark);

-- 9011 机构存款排名：aggregation groupBy=SUBJECT + SUM → ds_type 强制 SINGLE；
--      filters IN 圈定 10 家支行（105~114），第一聚合列（较上月净增）倒序即排名；
--      引导式聚合替代原 CUSTOM_SQL 排名（SCRDS_SEED04）的示范
INSERT INTO RPT_SCREEN_DATASOURCE
    (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by, deleted)
VALUES
    (9011, 'SCRDS_SEED11', '机构存款排名(引导式聚合)', 'SINGLE', 'WIDE_TABLE',
     '{"schemaVersion":2,"scopeMode":"GLOBAL","table":"ORG_INDEX_RESULT","subjectCol":"org_code","subjectParam":"orgCode","metrics":[{"metricCode":"M_0266","metricName":"一般性存款月均余额较上月-机构","slot":51},{"metricCode":"M_0265","metricName":"一般性存款月均余额-机构","slot":12}],"aggregation":{"groupBy":"SUBJECT","agg":"SUM","filters":[{"col":"org_code","op":"IN","value":"105,106,107,108,109,110,111,112,113,114"}]},"fieldMeta":[{"col":"org_code","alias":"机构","role":"DIM"},{"col":"一般性存款月均余额较上月-机构","alias":"存款较上月净增","role":"METRIC","unit":"万元","decimals":2},{"col":"一般性存款月均余额-机构","alias":"存款月均余额","role":"METRIC","unit":"万元","decimals":2}]}',
     NULL, 'ACTIVE', '大屏扩充种子：WIDE_TABLE 聚合 SUBJECT/SUM + filters IN（spec §3.3，替代 CUSTOM_SQL 排名 SCRDS_SEED04 的引导式示范）', 'SEED', 0)
ON DUPLICATE KEY UPDATE
    ds_code = VALUES(ds_code), ds_name = VALUES(ds_name), ds_type = VALUES(ds_type),
    source_kind = VALUES(source_kind), config_json = VALUES(config_json),
    time_param_json = VALUES(time_param_json), status = VALUES(status), remark = VALUES(remark);

-- ----------------------------------------------------------------------------
-- 执行后自查
-- ----------------------------------------------------------------------------
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE
FROM PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_SCR_KPI_SCH';

SELECT rr.ROLE_ID, r.ROLE_CODE
FROM PT_ROLE_RESOURCE rr JOIN PT_ROLE r ON r.ROLE_ID = rr.ROLE_ID
WHERE rr.RESOURCE_ID = 'R_RPT_SCR_KPI_SCH' ORDER BY rr.ROLE_ID;

SELECT ROLE_ID, BIZ_TYPE, DATA_SCOPE FROM PT_ROLE_BIZ_SCOPE
WHERE BIZ_TYPE = 'REPORT' AND ROLE_ID IN ('1','2','3');

SELECT id, ds_code, ds_name, ds_type, source_kind, status
FROM RPT_SCREEN_DATASOURCE WHERE id BETWEEN 9008 AND 9011 ORDER BY id;
