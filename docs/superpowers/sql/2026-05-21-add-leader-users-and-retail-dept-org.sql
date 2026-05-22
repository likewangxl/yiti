-- =========================================================
-- 2026-05-21 业务部门负责人节点用户补齐 + 西安分行-零售部机构新建
-- =========================================================
-- 背景：
--   2026-05-20 已创建 3 个负责人角色（R_CORP_LEAD / R_FIN_LEAD / R_RETAIL_LEAD），
--   2026-05-20 已创建 E60003 fin_lead01 资财部负责人（绑 02974000 西安分行-资金财务部）。
--
--   但 alloc_adjust_corp / retail / target_adjust 三个流程的 biz_dept_leader_approve
--   节点候选角色（CORP_DEPT_LEADER / RETAIL_DEPT_LEADER）此时还无任何用户，
--   导致流程进入此节点必然挂住。本次补齐：
--     - E60004 corp_leader01 / 陈八(公司部负责人)  → R_CORP_LEAD / 02975000 西安分行-公司部
--     - E60005 retail_leader01 / 周九(零售部负责人) → R_RETAIL_LEAD / 02976000 西安分行-零售部
--
--   2026-05-21 同步新建 "西安分行-零售部" (02976000) 机构与现有 "西安分行-公司部"
--   (02975000) / "西安分行-资金财务部" (02974000) 对称，使三位负责人按业务归属机构。
--
-- 部署范围：仅 yiti（含完整西安分行机构树）；onepl 基线无西安分行树，不跑
-- 备份：mysqldump 备份 PT_USER / PT_USER_ROLE / EXT_USER_ORG / EXT_ORG_INFO / PT_ROLE_RESOURCE
-- 幂等：所有 INSERT 用 INSERT IGNORE / UPDATE 带 WHERE 限定
-- =========================================================

-- A) 新增"西安分行-零售部"机构（仿照 02974000 资金财务部 / 02975000 公司部）
INSERT IGNORE INTO EXT_ORG_INFO
    (ID, ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID, ORGAN_STATE, ADM_DIVISION_CODE, ADM_DIVISION_NAME, CREATE_TIME, CREATE_USER)
VALUES
    (48, '02976000', '西安分行-零售部', 3, '02900001', 0, NULL, NULL, '2026-05-21 00:00:00', 'seed');

-- B) 2 个负责人测试账户（密码 123456，BCrypt hash 同 2026-05-20 测试账户族）
INSERT IGNORE INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, ISENABLED, ISLOCKED, PASS_WRONG_COUNT, CREATE_AUTHOR, REMARK)
VALUES
('E60004', 'corp_leader01',   '陈八(公司部负责人)',
    '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',
    0, 0, 0, 'seed', 'test-user 2026-05-21'),
('E60005', 'retail_leader01', '周九(零售部负责人)',
    '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',
    0, 0, 0, 'seed', 'test-user 2026-05-21');

-- C) 用户-角色绑定
INSERT IGNORE INTO PT_USER_ROLE (USER_ID, ROLE_ID) VALUES
('E60004', 'R_CORP_LEAD'),
('E60005', 'R_RETAIL_LEAD');

-- D) 用户-机构绑定（业务对称：负责人跟本部门同机构）
INSERT IGNORE INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
('E60004', '02975000'),  -- 西安分行-公司部
('E60005', '02976000');  -- 西安分行-零售部

-- E) 给 2 个负责人角色绑资源（基于 R_ADMIN 全集 - 39 个系统设置 API）
--    与 2026-05-20 grant-resources-and-create-leader-users.sql 同口径
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT LEFT(MD5(CONCAT(t.role_id,'_',rr.RESOURCE_ID)),32),
       t.role_id, rr.RESOURCE_ID, rr.SYS_CODE, '2026-05-21 00:00:00'
FROM PT_ROLE_RESOURCE rr
CROSS JOIN (SELECT 'R_CORP_LEAD' AS role_id UNION ALL SELECT 'R_RETAIL_LEAD') t
WHERE rr.ROLE_ID = 'R_ADMIN'
  AND rr.RESOURCE_ID NOT IN (
    'A_BZ_DELETE','A_BZ_LIST','A_BZ_MATRIX','A_BZ_SAVE',
    'A_RES_CREATE','A_RES_DELETE','A_RES_UPDATE',
    'A_ROLE_CREATE','A_ROLE_DELETE','A_ROLE_LIST','A_ROLE_UPDATE','A_ROLE_USERS',
    'A_RR_BIND','A_RR_LIST','A_RR_REPLACE',
    'A_UR_BIND','A_UR_DEL','A_UR_LIST',
    'G_AUDIT_DETAIL','G_AUDIT_EXPORT','G_AUDIT_LIST',
    'G_CAL_IMPORT','G_CAL_INIT','G_CAL_PUBLIC','G_CAL_SET',
    'G_CFG_LIST','G_CFG_UPDATE',
    'G_DICT_CREATE','G_DICT_DELETE','G_DICT_STATUS','G_DICT_UPDATE',
    'G_FILE_DELETE',
    'G_JOB_LIST','G_JOB_LOGS','G_JOB_PAUSE','G_JOB_RESUME','G_JOB_TRIGGER',
    'G_SQL_EXEC','G_SQL_HIST'
  );

-- =========================================================
-- 验证
--   SELECT u.USER_ID,u.USERNAME,u.USERCHNNAME,r.ROLE_CODE,uo.ORG_CODE,o.ORG_NAME,
--          (SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE ROLE_ID=ur.ROLE_ID) AS res_cnt
--     FROM PT_USER u
--     JOIN PT_USER_ROLE ur ON u.USER_ID=ur.USER_ID
--     JOIN PT_ROLE r ON ur.ROLE_ID=r.ROLE_ID
--     LEFT JOIN EXT_USER_ORG uo ON u.USER_ID=uo.USER_ID
--     LEFT JOIN EXT_ORG_INFO o ON uo.ORG_CODE=o.ORG_CODE
--    WHERE u.USER_ID IN ('E60004','E60005');
--   -- 期望：E60004 → CORP_DEPT_LEADER / 02975000 西安分行-公司部 / res_cnt ~ 306
--   --       E60005 → RETAIL_DEPT_LEADER / 02976000 西安分行-零售部 / res_cnt ~ 306
-- =========================================================
