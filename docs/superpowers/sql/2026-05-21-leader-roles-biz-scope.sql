-- =========================================================
-- 2026-05-21 三个业务部门负责人角色补齐 PT_ROLE_BIZ_SCOPE
-- =========================================================
-- 背景：
--   2026-05-20-add-dept-leader-roles-and-fix-rolecodes.sql 新建了 R_CORP_LEAD /
--   R_FIN_LEAD / R_RETAIL_LEAD 三个负责人角色；
--   2026-05-20-grant-resources-and-create-leader-users.sql 给它们做了
--   PT_ROLE_RESOURCE 授权（≈300 资源）+ 测试账户 E60001/E60002/E60003。
--
--   但**漏配** PT_ROLE_BIZ_SCOPE。导致前端 fin_lead01 访问
--   /api/perf/alloc-adjust/*/approval-history 等被 @BizAuth(bizType=PERF_CONFIG)
--   保护的端点时，BizScopeService.checkScope() 抛
--     "用户角色未配置 PERF_CONFIG 的数据范围"
--   （AuthErrorCode.DATA_SCOPE_DENIED, AUTH-40303）。
--
-- 处置：
--   按"负责人 = 同部门经办人 BIZ_SCOPE 镜像"原则克隆：
--     R_FIN_LEAD    ← R_BACK_FINANCE 的 7 行（ADDRBOOK/CUSTOMER/NAV/ORG/PERF_CONFIG/REPORT 全 ALL，SYS_CONFIG=SELF）
--     R_CORP_LEAD   ← R_CORP_DEPT    的 11 行
--     R_RETAIL_LEAD ← R_RETAIL_DEPT  的 11 行
--
--   语义：负责人持有的数据范围至少不应窄于本部门经办；如未来业务方对负责人
--   要求扩大或差异化范围（例如 SYS_CONFIG 不应是 SELF），再单独追加变更。
--
-- 部署范围（双库执行）：
--   - onepl  生产/文档基线库
--   - yiti   本地 dev profile 实际连接库
--
-- 备份：
--   docs/superpowers/sql/backup/2026-05-21-pre-leader-bizscope-yiti.sql
--   docs/superpowers/sql/backup/2026-05-21-pre-leader-bizscope-onepl.sql
--
-- 幂等：
--   PT_ROLE_BIZ_SCOPE.ID 用 LEFT(MD5(role_id || '_' || biz_type), 32) 做稳定主键，
--   INSERT IGNORE 重跑命中 0 行。
-- =========================================================

INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE
    (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
SELECT
    LEFT(MD5(CONCAT(t.lead_role, '_', src.BIZ_TYPE)), 32) AS ID,
    t.lead_role  AS ROLE_ID,
    src.BIZ_TYPE,
    src.DATA_SCOPE,
    0            AS RECORD_STATUS,
    '2026-05-21 00:00:00' AS CREATE_TIME,
    'seed'       AS CREATE_USER,
    CONCAT('2026-05-21 镜像自 ', src.ROLE_ID) AS REMARK
FROM PT_ROLE_BIZ_SCOPE src
JOIN (
    SELECT 'R_FIN_LEAD'    AS lead_role, 'R_BACK_FINANCE' AS op_role
    UNION ALL SELECT 'R_CORP_LEAD',     'R_CORP_DEPT'
    UNION ALL SELECT 'R_RETAIL_LEAD',   'R_RETAIL_DEPT'
) t ON src.ROLE_ID = t.op_role
WHERE src.RECORD_STATUS = 0;

-- =========================================================
-- 验证
--   SELECT ROLE_ID, COUNT(*) FROM PT_ROLE_BIZ_SCOPE
--    WHERE ROLE_ID IN ('R_FIN_LEAD','R_CORP_LEAD','R_RETAIL_LEAD') GROUP BY ROLE_ID;
--   -- 期望：R_FIN_LEAD=7, R_CORP_LEAD=11, R_RETAIL_LEAD=11
--
--   SELECT ROLE_ID, BIZ_TYPE, DATA_SCOPE FROM PT_ROLE_BIZ_SCOPE
--    WHERE ROLE_ID='R_FIN_LEAD' AND BIZ_TYPE='PERF_CONFIG';
--   -- 期望 1 行 ALL
-- =========================================================
