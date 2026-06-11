-- =============================================================================
-- 分配调整「保存草稿 / 草稿提交审批」资源登记 + 角色授权（2026-06-11）
--
-- 背景：新增端点 POST /api/perf/alloc-adjust/save-draft（保存草稿）与
--       POST /api/perf/alloc-adjust/{id}/submit（草稿提交审批）未登记 PT_RESOURCE，
--       fail-close 鉴权下会 403。本脚本登记 2 条资源并授权给 6 个角色：
--       分行员工 / 分行行长 / 支行员工 / 支行领导 / 客户经理 / 经营机构负责人。
--
-- 形态克隆自 P_PERF_ALLOC_AD_CRE（/create）：资源 SYS_CODE=PERF、PARENT=M_PERF_ADJUST、
-- 绑定 SYS_CODE=PLATFORM。子段通配符与 /*/withdraw 一致用 '*'。
--
-- 幂等：资源按 RESOURCE_ID NOT EXISTS、绑定按 (ROLE_ID,RESOURCE_ID) NOT EXISTS 防重。
-- 跨库可移植：角色按 ROLE_CHNAME 关联（yiti / onepl 角色 ID 不一致，名称一致）。
-- 适用库：yiti（开发）+ onepl（生产）双跑。
-- =============================================================================

-- 1) 资源登记：保存草稿
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
   CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
SELECT 'P_PERF_ALLOC_AD_DFT', '/api/perf/alloc-adjust/save-draft', 'POST', '保存分配调整草稿', NULL, 0,
       0, 0, 'M_PERF_ADJUST', 0, 'PERF',
       NOW(), 'add-save-draft-2026-06-11', NOW(), 'add-save-draft-2026-06-11', '保存为草稿端点'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_ALLOC_AD_DFT');

-- 2) 资源登记：草稿提交审批（子段通配 '*' 对应 {id}）
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
   CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
SELECT 'P_PERF_ALLOC_AD_SUB', '/api/perf/alloc-adjust/*/submit', 'POST', '草稿提交分配调整审批', NULL, 0,
       0, 0, 'M_PERF_ADJUST', 0, 'PERF',
       NOW(), 'add-save-draft-2026-06-11', NOW(), 'add-save-draft-2026-06-11', '草稿提交审批端点'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_ALLOC_AD_SUB');

-- 2.5) 补建缺失角色（仅 onepl 缺「分行员工/支行员工/支行领导」；yiti 已有则 NOT EXISTS 跳过）
--      命名风格对齐 onepl：ROLE_ID=R_<NAME>，ROLE_CODE=<CODE>，RECORD_STATUS=0，SYS_CODE=PLATFORM。
--      新建角色无用户绑定，仅为补齐资源授权矩阵；如需用户挂该角色另行处理。
INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
SELECT 'R_BRANCH_EMP', 'BRANCH_EMP', '分行员工', 0, 'PLATFORM', NOW(), 'add-save-draft-2026-06-11', NOW(), 'add-save-draft-2026-06-11', '补建：分配调整草稿授权'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE WHERE ROLE_CHNAME = '分行员工');

INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
SELECT 'R_SUB_BRANCH_EMP', 'SUB_BRANCH_EMP', '支行员工', 0, 'PLATFORM', NOW(), 'add-save-draft-2026-06-11', NOW(), 'add-save-draft-2026-06-11', '补建：分配调整草稿授权'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE WHERE ROLE_CHNAME = '支行员工');

INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
SELECT 'R_SUB_BRANCH_LEAD', 'SUB_BRANCH_LEAD', '支行领导', 0, 'PLATFORM', NOW(), 'add-save-draft-2026-06-11', NOW(), 'add-save-draft-2026-06-11', '补建：分配调整草稿授权'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PT_ROLE WHERE ROLE_CHNAME = '支行领导');

-- 3) 角色授权：6 角色 × 2 资源（按角色中文名关联，幂等防重）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT UPPER(REPLACE(UUID(), '-', '')), r.ROLE_ID, res.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE r
CROSS JOIN (
    SELECT 'P_PERF_ALLOC_AD_DFT' AS RESOURCE_ID
    UNION ALL
    SELECT 'P_PERF_ALLOC_AD_SUB'
) res
WHERE r.ROLE_CHNAME IN ('分行员工', '分行行长', '支行员工', '支行领导', '客户经理', '经营机构负责人')
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = res.RESOURCE_ID
  );
