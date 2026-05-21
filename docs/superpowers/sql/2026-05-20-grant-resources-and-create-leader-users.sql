-- =========================================================
-- 2026-05-20 3 个业务部门负责人角色：授权 + 创建测试账户
-- =========================================================
-- 前置：已执行 2026-05-20-add-dept-leader-roles-and-fix-rolecodes.sql
--       （PT_ROLE 中 R_CORP_LEAD / R_FIN_LEAD / R_RETAIL_LEAD 三行已存在）
--
-- 范围：
--   A) PT_ROLE_RESOURCE 授权
--      给 3 个负责人角色绑定"R_ADMIN 全部资源 - 系统设置管理 API"。
--      系统设置管理 API 共 39 条（精确清单见排除子句），覆盖前端 system/*
--      路由组下 8 个"管理类页面"对应的写操作：用户/角色/资源/权限/字典/日历/
--      任务调度/审计日志/系统配置 的 CREATE/DELETE/UPDATE/STATUS/IMPORT/INIT
--      /SET/EXPORT/PAUSE/RESUME/TRIGGER/SQL_EXEC/SQL_HIST 等。
--      保留所有业务模块资源（C_/B_/W_/P_PERF_/R_RPT_/RES_*）+ 通用查询资源
--      （A_LOGIN/LOGOUT/CURR_USER/PERMS/ORG_*/RES_TREE/G_DICT_LIST/G_DICT_ITEMS
--      /G_NOTIFY_*/G_FILE_LIST/UPLOAD/DOWNLOAD/G_CAL_GET）。
--
--   B) PT_USER 新建 3 个测试账户
--      USER_ID = E60001 / E60002 / E60003
--      密码统一 123456，BCrypt hash $2b$10$16t1Spyl... 同其它 2026-04-10 测试账户
--
--   C) PT_USER_ROLE 绑定（一人一角色）
--      E60001 -> R_CORP_LEAD   (公司部负责人)
--      E60002 -> R_RETAIL_LEAD (零售部负责人)
--      E60003 -> R_FIN_LEAD    (资财部负责人)
--
--   D) EXT_USER_ORG 绑定到总行 HQ（沿用 corp_zhao / retail_sun / finance_zhou 的机构）
--
-- 部署范围：onepl + yiti 双库
-- 备份：mysqldump 备份 PT_ROLE_RESOURCE / PT_USER / PT_USER_ROLE / EXT_USER_ORG
--       到 docs/superpowers/sql/backup/2026-05-20-pre-leader-grant-and-users.sql
-- 幂等性：所有 INSERT 都用 INSERT IGNORE；PT_ROLE_RESOURCE.ID 用
--         MD5(role_id||'_'||resource_id) 32 位前缀作为稳定主键（重跑不冲突）
-- =========================================================

-- ---------------------------------------------------------
-- A) 3 个新角色批量授权（基于 R_ADMIN 全集 - 系统设置管理 39 条）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (`ID`, `ROLE_ID`, `RESOURCE_ID`, `SYS_CODE`, `CREATE_TIME`)
SELECT
    LEFT(MD5(CONCAT(target.role_id, '_', rr.RESOURCE_ID)), 32) AS ID,
    target.role_id AS ROLE_ID,
    rr.RESOURCE_ID,
    rr.SYS_CODE,
    '2026-05-20 00:00:00' AS CREATE_TIME
FROM PT_ROLE_RESOURCE rr
CROSS JOIN (
    SELECT 'R_CORP_LEAD' AS role_id
    UNION ALL SELECT 'R_FIN_LEAD'
    UNION ALL SELECT 'R_RETAIL_LEAD'
) target
WHERE rr.ROLE_ID = 'R_ADMIN'
  AND rr.RESOURCE_ID NOT IN (
    -- 用户管理 / 角色管理 / 资源管理 / 权限配置（保留 A_RES_TREE 用于侧栏菜单加载）
    'A_BZ_DELETE','A_BZ_LIST','A_BZ_MATRIX','A_BZ_SAVE',
    'A_RES_CREATE','A_RES_DELETE','A_RES_UPDATE',
    'A_ROLE_CREATE','A_ROLE_DELETE','A_ROLE_LIST','A_ROLE_UPDATE','A_ROLE_USERS',
    'A_RR_BIND','A_RR_LIST','A_RR_REPLACE',
    'A_UR_BIND','A_UR_DEL','A_UR_LIST',
    -- 审计日志（业务负责人不查全行审计）
    'G_AUDIT_DETAIL','G_AUDIT_EXPORT','G_AUDIT_LIST',
    -- 工作日历管理（保留 G_CAL_GET 用于业务侧查询节假日）
    'G_CAL_IMPORT','G_CAL_INIT','G_CAL_PUBLIC','G_CAL_SET',
    -- 系统配置
    'G_CFG_LIST','G_CFG_UPDATE',
    -- 字典管理（保留 G_DICT_LIST / G_DICT_ITEMS 用于业务下拉框）
    'G_DICT_CREATE','G_DICT_DELETE','G_DICT_STATUS','G_DICT_UPDATE',
    -- 文件管理删除（保留 G_FILE_LIST / G_FILE_UPLOAD / G_FILE_DOWNLOAD 用于业务上传下载）
    'G_FILE_DELETE',
    -- 任务调度
    'G_JOB_LIST','G_JOB_LOGS','G_JOB_PAUSE','G_JOB_RESUME','G_JOB_TRIGGER',
    -- SQL 探查
    'G_SQL_EXEC','G_SQL_HIST'
  );

-- ---------------------------------------------------------
-- B) 3 个测试账户（密码 123456）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, ISENABLED, ISLOCKED, PASS_WRONG_COUNT, CREATE_AUTHOR, REMARK)
VALUES
('E60001', 'corp_lead01',   '陈八(公司部负责人)',
    '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',
    0, 0, 0, 'seed', 'test-user 2026-05-20'),
('E60002', 'retail_lead01', '周九(零售部负责人)',
    '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',
    0, 0, 0, 'seed', 'test-user 2026-05-20'),
('E60003', 'fin_lead01',    '吴十(资财部负责人)',
    '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',
    0, 0, 0, 'seed', 'test-user 2026-05-20');

-- ---------------------------------------------------------
-- C) 用户-角色绑定
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_USER_ROLE (USER_ID, ROLE_ID)
VALUES
('E60001', 'R_CORP_LEAD'),
('E60002', 'R_RETAIL_LEAD'),
('E60003', 'R_FIN_LEAD');

-- ---------------------------------------------------------
-- D) 用户-机构绑定（均在总行 HQ，与 corp_zhao/retail_sun/finance_zhou 同级）
-- ---------------------------------------------------------
INSERT IGNORE INTO EXT_USER_ORG (USER_ID, ORG_CODE)
VALUES
('E60001', 'HQ'),
('E60002', 'HQ'),
('E60003', 'HQ');

-- =========================================================
-- 验证 SQL（执行后核对）
-- =========================================================
-- 1) 3 个角色各拿到多少资源（期望大致 119 - 39 = 80 条上下，浮动取决于 R_ADMIN 实际行数）
--    SELECT ROLE_ID, COUNT(*) FROM PT_ROLE_RESOURCE
--    WHERE ROLE_ID IN ('R_CORP_LEAD','R_FIN_LEAD','R_RETAIL_LEAD') GROUP BY ROLE_ID;
--
-- 2) 系统设置管理 API 都被排除（期望 0 行）
--    SELECT ROLE_ID, RESOURCE_ID FROM PT_ROLE_RESOURCE
--    WHERE ROLE_ID IN ('R_CORP_LEAD','R_FIN_LEAD','R_RETAIL_LEAD')
--      AND RESOURCE_ID IN ('A_ROLE_CREATE','G_DICT_CREATE','G_JOB_TRIGGER','G_AUDIT_LIST');
--
-- 3) 3 个测试账户都已建好
--    SELECT USER_ID, USERNAME, USERCHNNAME FROM PT_USER WHERE USER_ID IN ('E60001','E60002','E60003');
--
-- 4) 用户-角色 / 用户-机构 绑定都在
--    SELECT * FROM PT_USER_ROLE WHERE USER_ID IN ('E60001','E60002','E60003');
--    SELECT * FROM EXT_USER_ORG WHERE USER_ID IN ('E60001','E60002','E60003');
--
-- 5) 登录测试（任一账户）
--    用户名: corp_lead01 / retail_lead01 / fin_lead01
--    密码:   123456
-- =========================================================
