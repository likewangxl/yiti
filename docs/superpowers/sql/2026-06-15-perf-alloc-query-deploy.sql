-- ============================================================================
-- 业绩分配查询（报表分析中心，只读）—— 上线部署脚本（菜单 + 权限资源 + 角色授权）
-- 日期：2026-06-15
--
-- 【本脚本作用】把该功能上线所需的 PT_RESOURCE / PT_ROLE_RESOURCE 一次性整理齐全，
--   合并并取代以下 3 个零散脚本（保留历史归档，勿再单独执行）：
--     - 2026-06-15-amas-approval-history-resources.sql   （AMAS 历史业绩调整 API 资源）
--     - 2026-06-15-amas-approval-history-menu.sql         （业绩分配查询 菜单项）
--     - 2026-06-15-alloc-adjust-history-resources.sql     （业绩调整 API 资源）
--
-- 【页面构成】前端「报表分析 → 业绩分配查询」页（菜单 M_REPORT_AMAS，路由 /report/amas-approvals），
--   含两个 Tab：
--     · 业绩调整      ← PERF_ALLOC_ADJUST_APPLY（平台自有调整申请，走 Flowable 审批）
--     · 历史业绩调整  ← AMAS_PERF_ADJUST_APPROVAL（外部 AMAS 遗留审批历史）
--
-- 【后端端点 → 鉴权资源】（均 @BizAuth(bizType=REPORT)，只读，无 @AuditLog）
--   GET /api/reports/alloc-adjust-applies        AllocAdjustHistoryController.list    REPORT/LIST  → R_RPT_ALC_LIST
--   GET /api/reports/alloc-adjust-applies/{id}   AllocAdjustHistoryController.detail  REPORT/READ  → R_RPT_ALC_DET
--   GET /api/reports/amas-approvals              AmasApprovalHistoryController.list   REPORT/LIST  → R_RPT_AMAS_LIST
--   GET /api/reports/amas-approvals/{perfAdjustNo} AmasApprovalHistoryController.detail REPORT/READ → R_RPT_AMAS_DET
--
-- 【约定 / 幂等】
--   · 全脚本幂等（INSERT ... WHERE NOT EXISTS / UPDATE 对齐），可重复执行。
--   · RESOURCE_ID 长度 ≤ 20；详情路径变量统一用 AntPath '*'（由 ResourceMatcher 匹配）。
--   · STATUS=0 表示启用。
--   · API 鉴权资源 SYS_CODE='RPT'、ISMENU=0；菜单项 SYS_CODE='YITI'、ISMENU=1。
--   · 双库部署：API 资源 yiti + onepl 均建；菜单项仅 yiti（onepl 无 M_GROUP_REPORT 菜单体系）。
--   · 角色授权沿用本模块既有做法——复制「自由报表」受众（API 复制 R_RPT_FREE_DATA、
--     菜单复制 M_REPORT_FREE）。该做法与 DB 的角色主键形态无关（yiti 数字 ROLE_ID /
--     onepl 字符串 ROLE_ID 均适用）。⚠ 若目标库不存在 R_RPT_FREE_DATA / M_REPORT_FREE
--     （如全新 onepl），复制源为空 → 不会产生授权，需按 PART D 提示手工授予目标角色。
-- ============================================================================


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║ PART A —— API 鉴权资源（PT_RESOURCE，SYS_CODE='RPT'，ISMENU=0）  yiti + onepl ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- ---------- yiti ----------
INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                              MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_LIST', '/api/reports/alloc-adjust-applies', 'GET', '业绩分配查询-业绩调整列表',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/业绩调整 列表'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_LIST');

INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                              MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_DET', '/api/reports/alloc-adjust-applies/*', 'GET', '业绩分配查询-业绩调整详情',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/业绩调整 详情'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_DET');

INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                              MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_LIST', '/api/reports/amas-approvals', 'GET', '业绩分配查询-历史业绩调整列表',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/历史业绩调整 列表'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST');

INSERT INTO yiti.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                              MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_DET', '/api/reports/amas-approvals/*', 'GET', '业绩分配查询-历史业绩调整详情',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/历史业绩调整 详情'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_DET');

-- 名称对齐（把早期零散脚本里 “业绩分配审批历史-* / 业绩调整-*” 统一为最终页面口径）
UPDATE yiti.PT_RESOURCE SET MENU_NAME='业绩分配查询-业绩调整列表'     WHERE RESOURCE_ID='R_RPT_ALC_LIST';
UPDATE yiti.PT_RESOURCE SET MENU_NAME='业绩分配查询-业绩调整详情'     WHERE RESOURCE_ID='R_RPT_ALC_DET';
UPDATE yiti.PT_RESOURCE SET MENU_NAME='业绩分配查询-历史业绩调整列表' WHERE RESOURCE_ID='R_RPT_AMAS_LIST';
UPDATE yiti.PT_RESOURCE SET MENU_NAME='业绩分配查询-历史业绩调整详情' WHERE RESOURCE_ID='R_RPT_AMAS_DET';

-- ---------- onepl ----------
INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                               MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_LIST', '/api/reports/alloc-adjust-applies', 'GET', '业绩分配查询-业绩调整列表',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/业绩调整 列表'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_LIST');

INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                               MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_ALC_DET', '/api/reports/alloc-adjust-applies/*', 'GET', '业绩分配查询-业绩调整详情',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/业绩调整 详情'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_ALC_DET');

INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                               MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_LIST', '/api/reports/amas-approvals', 'GET', '业绩分配查询-历史业绩调整列表',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/历史业绩调整 列表'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_LIST');

INSERT INTO onepl.PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME,
                               MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'R_RPT_AMAS_DET', '/api/reports/amas-approvals/*', 'GET', '业绩分配查询-历史业绩调整详情',
       0, 0, 0, 0, 'RPT', 'seed', '业绩分配查询/历史业绩调整 详情'
WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_RESOURCE WHERE RESOURCE_ID = 'R_RPT_AMAS_DET');

UPDATE onepl.PT_RESOURCE SET MENU_NAME='业绩分配查询-业绩调整列表'     WHERE RESOURCE_ID='R_RPT_ALC_LIST';
UPDATE onepl.PT_RESOURCE SET MENU_NAME='业绩分配查询-业绩调整详情'     WHERE RESOURCE_ID='R_RPT_ALC_DET';
UPDATE onepl.PT_RESOURCE SET MENU_NAME='业绩分配查询-历史业绩调整列表' WHERE RESOURCE_ID='R_RPT_AMAS_LIST';
UPDATE onepl.PT_RESOURCE SET MENU_NAME='业绩分配查询-历史业绩调整详情' WHERE RESOURCE_ID='R_RPT_AMAS_DET';


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║ PART B —— 菜单项（PT_RESOURCE，SYS_CODE='YITI'，ISMENU=1）  仅 yiti          ║
-- ║ 侧边栏由 GET /api/auth/my-menus 渲染；菜单项是独立于 API 资源的一类资源行。   ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

INSERT INTO yiti.PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
     MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT 'M_REPORT_AMAS', '/report/amas-approvals', 'MENU', '业绩分配查询', 6, 1,
       '1', 'M_GROUP_REPORT', 0, 'YITI', 'seed', '业绩分配查询菜单（报表分析组下）'
WHERE NOT EXISTS (SELECT 1 FROM yiti.PT_RESOURCE WHERE RESOURCE_ID = 'M_REPORT_AMAS');

-- 名称对齐（早期建为“业绩分配审批历史”，现统一为“业绩分配查询”）
UPDATE yiti.PT_RESOURCE SET MENU_NAME='业绩分配查询' WHERE RESOURCE_ID='M_REPORT_AMAS';


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║ PART C —— 角色授权（PT_ROLE_RESOURCE）                                       ║
-- ║ API 资源复制 R_RPT_FREE_DATA 受众；菜单项复制 M_REPORT_FREE 受众。           ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- ---------- yiti：4 个 API 资源，复制自由报表查询(R_RPT_FREE_DATA)受众 ----------
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, x.rid, 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
JOIN (SELECT 'R_RPT_ALC_LIST' rid UNION ALL SELECT 'R_RPT_ALC_DET'
      UNION ALL SELECT 'R_RPT_AMAS_LIST' UNION ALL SELECT 'R_RPT_AMAS_DET') x
WHERE rr.RESOURCE_ID = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID = rr.ROLE_ID AND rr2.RESOURCE_ID = x.rid);

-- ---------- yiti：菜单项，复制自由报表菜单(M_REPORT_FREE)受众 ----------
INSERT INTO yiti.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, 'M_REPORT_AMAS', 'PLATFORM'
FROM yiti.PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'M_REPORT_FREE'
  AND NOT EXISTS (SELECT 1 FROM yiti.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID = rr.ROLE_ID AND rr2.RESOURCE_ID = 'M_REPORT_AMAS');

-- ---------- onepl：4 个 API 资源，复制自由报表查询(R_RPT_FREE_DATA)受众 ----------
-- ⚠ 若 onepl 无 R_RPT_FREE_DATA 授权行，则下面复制为空（0 授权），需按 PART D 手工授予。
INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, x.rid, 'PLATFORM'
FROM onepl.PT_ROLE_RESOURCE rr
JOIN (SELECT 'R_RPT_ALC_LIST' rid UNION ALL SELECT 'R_RPT_ALC_DET'
      UNION ALL SELECT 'R_RPT_AMAS_LIST' UNION ALL SELECT 'R_RPT_AMAS_DET') x
WHERE rr.RESOURCE_ID = 'R_RPT_FREE_DATA'
  AND NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr2
                  WHERE rr2.ROLE_ID = rr.ROLE_ID AND rr2.RESOURCE_ID = x.rid);


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║ PART D —— 上线后验证 & 兜底授权（按需手工执行）                              ║
-- ╚══════════════════════════════════════════════════════════════════════════╝
-- 1) 资源是否齐全（应各 1 行；yiti 含 M_REPORT_AMAS 菜单）：
--    SELECT RESOURCE_ID, MENU_NAME, RESOURCE_URL, ISMENU, SYS_CODE FROM yiti.PT_RESOURCE
--      WHERE RESOURCE_ID IN ('R_RPT_ALC_LIST','R_RPT_ALC_DET','R_RPT_AMAS_LIST','R_RPT_AMAS_DET','M_REPORT_AMAS');
--    SELECT RESOURCE_ID, MENU_NAME, RESOURCE_URL FROM onepl.PT_RESOURCE
--      WHERE RESOURCE_ID IN ('R_RPT_ALC_LIST','R_RPT_ALC_DET','R_RPT_AMAS_LIST','R_RPT_AMAS_DET');
--
-- 2) 授权行数（每个资源应 > 0；菜单 M_REPORT_AMAS 决定侧边栏可见性）：
--    SELECT RESOURCE_ID, COUNT(*) FROM yiti.PT_ROLE_RESOURCE
--      WHERE RESOURCE_ID IN ('R_RPT_ALC_LIST','R_RPT_ALC_DET','R_RPT_AMAS_LIST','R_RPT_AMAS_DET','M_REPORT_AMAS')
--      GROUP BY RESOURCE_ID;
--
-- 3) 兜底：目标库无 R_RPT_FREE_DATA 受众时，按需把 4 个 API 资源授予指定角色 ROLE_ID。
--    以 onepl 授予 R_ADMIN / R_BACK_TECH 为例（onepl 的 PT_ROLE_RESOURCE.ROLE_ID 存角色编码）：
--    INSERT INTO onepl.PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
--    SELECT REPLACE(UUID(),'-',''), r.ROLE_ID, x.rid, 'PLATFORM'
--    FROM (SELECT 'R_ADMIN' ROLE_ID UNION ALL SELECT 'R_BACK_TECH') r
--    JOIN (SELECT 'R_RPT_ALC_LIST' rid UNION ALL SELECT 'R_RPT_ALC_DET'
--          UNION ALL SELECT 'R_RPT_AMAS_LIST' UNION ALL SELECT 'R_RPT_AMAS_DET') x
--    WHERE NOT EXISTS (SELECT 1 FROM onepl.PT_ROLE_RESOURCE rr
--                      WHERE rr.ROLE_ID=r.ROLE_ID AND rr.RESOURCE_ID=x.rid);
--    （注意：yiti 的 PT_ROLE_RESOURCE.ROLE_ID 为数字主键，手工兜底时改用对应数字 ROLE_ID。）
-- ============================================================================
