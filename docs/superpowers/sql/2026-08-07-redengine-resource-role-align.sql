-- ============================================================================
-- 红色引擎资源与角色授权对齐脚本
--
-- 数据口径：
--   1. red-engine-center 当前 23 个 REST 端点，由 17 个 P_RE_* API 资源覆盖；
--   2. 平台统一入口 M_RE_ENGINE 作为 1 个菜单资源；
--   3. 合计 18 个资源、51 条角色资源关联；
--   4. 党建角色权限矩阵以 red-engine-center/CLAUDE.md 和 yiti 当前库为准；
--   5. SYS_ADMIN 通过 ROLE_CODE 动态定位，不依赖各环境不同的 ROLE_ID。
--
-- 特性：
--   - 可重复执行；资源和党建角色会对齐到当前定义；
--   - 角色资源关联按 ROLE_ID + RESOURCE_ID 做逻辑去重；
--   - 不删除目标库已有的额外自定义授权；
--   - 不包含字典、党组织、用户、演示业务数据。
--
-- 执行前请按项目运维规范备份目标库。
-- ============================================================================

SET NAMES utf8mb4;
START TRANSACTION;


-- ============================================================================
-- 1) PT_RESOURCE：1 个菜单资源 + 17 个 API 资源
--
-- P_RE_ORG_GET / P_RE_SUBMIT_GET 的 MENU_RANK_NO 必须为 10：
-- ResourceMatcher 按 MENU_RANK_NO ASC、RESOURCE_ID ASC 取首个 AntPath 匹配，
-- 该排序确保 /tree、/my 等字面量资源优先于同方法的 /* 通配符资源。
-- ============================================================================
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_USER, UPDATE_USER, REMARK)
VALUES
    ('M_RE_ENGINE',      '/redengine/dashboard',                  'MENU',   '红色引擎',                         NULL, 7,  1, '1', NULL, 0, 'RE', 'redengine-menu-align', 'redengine-resource-sync', '平台统一菜单入口，页面保持红色引擎原风格'),
    ('P_RE_ORG_TREE',    '/api/re/orgs/tree',                     'GET',    '红色引擎-党组织树',                NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_ORG_GET',     '/api/re/orgs/*',                        'GET',    '红色引擎-党组织详情',              NULL, 10, 0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_ORG_ADD',     '/api/re/orgs',                          'POST',   '红色引擎-新增党组织',              NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_ORG_UPD',     '/api/re/orgs/*',                        'PUT',    '红色引擎-修改党组织',              NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_ORG_DEL',     '/api/re/orgs/*',                        'DELETE', '红色引擎-删除党组织(高危)',        NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_MAP_LIST',    '/api/re/user-party-maps',               'GET',    '红色引擎-用户党组织映射列表',      NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_MAP_BIND',    '/api/re/user-party-maps',               'POST',   '红色引擎-绑定用户党组织(高危)',    NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_SUBMIT_ADD',  '/api/re/submits',                       'POST',   '红色引擎-新建上报',                NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_SUBMIT_MY',   '/api/re/submits/my',                    'GET',    '红色引擎-我的上报',                NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_SUBMIT_GET',  '/api/re/submits/*',                     'GET',    '红色引擎-上报详情',                NULL, 10, 0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_REVIEW_Q',    '/api/re/reviews/**',                    'GET',    '红色引擎-审核待审队列',            NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6 / Task9 复用 preview'),
    ('P_RE_REVIEW_APPR', '/api/re/reviews/*/approve',             'POST',   '红色引擎-审核通过+评分',            NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_REVIEW_REJ',  '/api/re/reviews/*/reject',              'POST',   '红色引擎-审核驳回',                NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_CKPT_VIEW',   '/api/re/cockpit/**',                    'GET',    '红色引擎-驾驶舱只读',              NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_CKPT_EXEC',   '/api/re/cockpit/overdue/execute',       'POST',   '红色引擎-执行逾期扣分(高危)',      NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_CKPT_ANNUAL', '/api/re/cockpit/archive/generate/*',    'POST',   '红色引擎-生成年度归档(高危)',      NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6'),
    ('P_RE_EXPORT',      '/api/re/export/*',                      'GET',    '红色引擎-数据导出',                NULL, 0,  0, '0', NULL, 0, 'RE', 'redengine-merge',      'redengine-resource-sync', 'spec 2026-07-18 §6') AS new
ON DUPLICATE KEY UPDATE
    RESOURCE_URL       = new.RESOURCE_URL,
    RESOURCE_METHOD    = new.RESOURCE_METHOD,
    MENU_NAME          = new.MENU_NAME,
    MENU_ICON_URL      = new.MENU_ICON_URL,
    MENU_RANK_NO       = new.MENU_RANK_NO,
    ISMENU             = new.ISMENU,
    MENU_ENDFLAG       = new.MENU_ENDFLAG,
    PARENT_RESOURCE_ID = new.PARENT_RESOURCE_ID,
    STATUS             = new.STATUS,
    SYS_CODE           = new.SYS_CODE,
    UPDATE_USER        = new.UPDATE_USER,
    REMARK             = new.REMARK;


-- ============================================================================
-- 2) PT_ROLE：资源关联所需的 4 个党建角色
-- SYS_ADMIN 是平台基础角色，不在本脚本中创建，关联时按 ROLE_CODE 动态定位。
-- ============================================================================
INSERT INTO PT_ROLE
    (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_USER, UPDATE_USER)
VALUES
    ('RE_ROLE_1', 'R_RE_ORGREV', '党建组织审核员', 0, 'RE', 'redengine-merge', 'redengine-resource-sync'),
    ('RE_ROLE_2', 'R_RE_BRREV',  '党建支部审核员', 0, 'RE', 'redengine-merge', 'redengine-resource-sync'),
    ('RE_ROLE_3', 'R_RE_SECR',   '党建支部书记',   0, 'RE', 'redengine-merge', 'redengine-resource-sync'),
    ('RE_ROLE_4', 'R_RE_REPORT', '党建报送员',     0, 'RE', 'redengine-merge', 'redengine-resource-sync') AS new
ON DUPLICATE KEY UPDATE
    ROLE_CODE     = new.ROLE_CODE,
    ROLE_CHNAME   = new.ROLE_CHNAME,
    RECORD_STATUS = new.RECORD_STATUS,
    SYS_CODE      = new.SYS_CODE,
    UPDATE_USER   = new.UPDATE_USER;


-- ============================================================================
-- 3) PT_ROLE_RESOURCE：51 条当前授权关系
-- ID 使用 MD5(ROLE_ID#RESOURCE_ID)；NOT EXISTS 同时兼容目标库中非 MD5 主键的既有关系。
-- ============================================================================

-- 3a) 全部 4 个党建角色：菜单入口、党组织树、党组织详情、上报详情（4 × 4 = 16）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'RE'
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'M_RE_ENGINE' AS RESOURCE_ID
    UNION ALL SELECT 'P_RE_ORG_TREE'
    UNION ALL SELECT 'P_RE_ORG_GET'
    UNION ALL SELECT 'P_RE_SUBMIT_GET'
) x
WHERE r.ROLE_CODE IN ('R_RE_ORGREV', 'R_RE_BRREV', 'R_RE_SECR', 'R_RE_REPORT')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = x.RESOURCE_ID
  );

-- 3b) 报送员 + 支部书记：新建上报（2）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#P_RE_SUBMIT_ADD')), r.ROLE_ID, 'P_RE_SUBMIT_ADD', 'RE'
FROM PT_ROLE r
WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = 'P_RE_SUBMIT_ADD'
  );

-- 3c) 报送员 + 支部书记 + 支部审核员：我的上报（3）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#P_RE_SUBMIT_MY')), r.ROLE_ID, 'P_RE_SUBMIT_MY', 'RE'
FROM PT_ROLE r
WHERE r.ROLE_CODE IN ('R_RE_REPORT', 'R_RE_SECR', 'R_RE_BRREV')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = 'P_RE_SUBMIT_MY'
  );

-- 3d) 支部审核员 + 组织审核员：待审/预览、审核通过、审核驳回（2 × 3 = 6）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'RE'
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'P_RE_REVIEW_Q' AS RESOURCE_ID
    UNION ALL SELECT 'P_RE_REVIEW_APPR'
    UNION ALL SELECT 'P_RE_REVIEW_REJ'
) x
WHERE r.ROLE_CODE IN ('R_RE_BRREV', 'R_RE_ORGREV')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = x.RESOURCE_ID
  );

-- 3e) 支部书记 + 组织审核员：驾驶舱只读、数据导出（2 × 2 = 4）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'RE'
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'P_RE_CKPT_VIEW' AS RESOURCE_ID
    UNION ALL SELECT 'P_RE_EXPORT'
) x
WHERE r.ROLE_CODE IN ('R_RE_SECR', 'R_RE_ORGREV')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = x.RESOURCE_ID
  );

-- 3f) 仅组织审核员：执行逾期扣分、生成年度归档（2）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.RESOURCE_ID)), r.ROLE_ID, x.RESOURCE_ID, 'RE'
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'P_RE_CKPT_EXEC' AS RESOURCE_ID
    UNION ALL SELECT 'P_RE_CKPT_ANNUAL'
) x
WHERE r.ROLE_CODE = 'R_RE_ORGREV'
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = x.RESOURCE_ID
  );

-- 3g) 平台系统管理员：全部 18 个红色引擎资源（18）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', p.RESOURCE_ID)), r.ROLE_ID, p.RESOURCE_ID, 'RE'
FROM PT_ROLE r
CROSS JOIN PT_RESOURCE p
WHERE r.ROLE_CODE = 'SYS_ADMIN'
  AND p.SYS_CODE = 'RE'
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = p.RESOURCE_ID
  );


-- ============================================================================
-- 4) PT_ROLE_BIZ_SCOPE：@BizAuth(RED_ENGINE) 所需配套数据范围
-- 党组织维度的数据隔离由红色引擎模块通过 RE_USER_PARTY_MAP 自行实现。
-- ============================================================================
INSERT INTO PT_ROLE_BIZ_SCOPE
    (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER)
SELECT MD5(CONCAT(r.ROLE_ID, '#RED_ENGINE')), r.ROLE_ID, 'RED_ENGINE', 'ALL', 0,
       'redengine-merge', 'redengine-resource-sync'
FROM PT_ROLE r
WHERE r.ROLE_CODE IN ('R_RE_ORGREV', 'R_RE_BRREV', 'R_RE_SECR', 'R_RE_REPORT', 'SYS_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_BIZ_SCOPE bs
      WHERE bs.ROLE_ID = r.ROLE_ID AND bs.BIZ_TYPE = 'RED_ENGINE'
  );

UPDATE PT_ROLE_BIZ_SCOPE bs
JOIN PT_ROLE r ON r.ROLE_ID = bs.ROLE_ID
SET bs.DATA_SCOPE = 'ALL',
    bs.RECORD_STATUS = 0,
    bs.UPDATE_USER = 'redengine-resource-sync'
WHERE bs.BIZ_TYPE = 'RED_ENGINE'
  AND r.ROLE_CODE IN ('R_RE_ORGREV', 'R_RE_BRREV', 'R_RE_SECR', 'R_RE_REPORT', 'SYS_ADMIN');


-- ============================================================================
-- 5) 执行后核验
-- 当前基线期望：18 个资源、51 条关联；各角色资源数分别为 11/8/8/6/18。
-- 若目标库保留了额外自定义授权，关联数可能大于该基线值。
-- ============================================================================
SELECT COUNT(*) AS RED_RESOURCE_COUNT
FROM PT_RESOURCE
WHERE SYS_CODE = 'RE';

SELECT r.ROLE_CODE, r.ROLE_CHNAME, COUNT(rr.RESOURCE_ID) AS RED_RESOURCE_COUNT
FROM PT_ROLE r
LEFT JOIN PT_ROLE_RESOURCE rr
       ON rr.ROLE_ID = r.ROLE_ID
      AND rr.RESOURCE_ID IN (SELECT RESOURCE_ID FROM PT_RESOURCE WHERE SYS_CODE = 'RE')
WHERE r.ROLE_CODE IN ('R_RE_ORGREV', 'R_RE_BRREV', 'R_RE_SECR', 'R_RE_REPORT', 'SYS_ADMIN')
GROUP BY r.ROLE_ID, r.ROLE_CODE, r.ROLE_CHNAME
ORDER BY r.ROLE_CODE;

SELECT COUNT(*) AS RED_ROLE_RESOURCE_COUNT
FROM PT_ROLE_RESOURCE rr
JOIN PT_RESOURCE p ON p.RESOURCE_ID = rr.RESOURCE_ID
WHERE p.SYS_CODE = 'RE';

COMMIT;
