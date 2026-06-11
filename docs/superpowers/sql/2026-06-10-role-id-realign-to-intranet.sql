-- ============================================================================
-- 角色 ROLE_ID 对齐内网 sys_role（图片）+ 未匹配角色编号化
-- 当前 role_id 为 R_xxx 字符串；本脚本改为数字字符串（列仍 varchar，不改类型）。
-- 跨 4 表级联：PT_ROLE / PT_USER_ROLE / PT_ROLE_RESOURCE / PT_ROLE_BIZ_SCOPE。
-- 老 id 全是 R_ 开头、新 id 全为数字，二者无交集，单次 UPDATE 安全（无需两阶段）。
--
-- ⚠️ 执行前确认；执行后需清 SPRING_SESSION 强制重新登录（见末尾）。
-- ⚠️ 另需同步改的代码见配套清单（DashboardServiceImpl / KpiSchemeController /
--    RoleService.createRole / 前端 Targets/TargetValues/KpiRules）。
-- ============================================================================

-- 0) 备份（CREATE ... AS SELECT 会自动提交；保留以便回滚）
DROP TABLE IF EXISTS PT_ROLE_BAK_20260610;
DROP TABLE IF EXISTS PT_USER_ROLE_BAK_20260610;
DROP TABLE IF EXISTS PT_ROLE_RESOURCE_BAK_20260610;
DROP TABLE IF EXISTS PT_ROLE_BIZ_SCOPE_BAK_20260610;
CREATE TABLE PT_ROLE_BAK_20260610          AS SELECT * FROM PT_ROLE;
CREATE TABLE PT_USER_ROLE_BAK_20260610     AS SELECT * FROM PT_USER_ROLE;
CREATE TABLE PT_ROLE_RESOURCE_BAK_20260610 AS SELECT * FROM PT_ROLE_RESOURCE;
CREATE TABLE PT_ROLE_BIZ_SCOPE_BAK_20260610 AS SELECT * FROM PT_ROLE_BIZ_SCOPE;

-- 1) 映射临时表（old_id → new_id）
DROP TEMPORARY TABLE IF EXISTS role_id_map;
CREATE TEMPORARY TABLE role_id_map (old_id VARCHAR(50) PRIMARY KEY, new_id VARCHAR(50) NOT NULL);
INSERT INTO role_id_map (old_id, new_id) VALUES
-- ===== 图片里有的（按中文名对上，24 个）=====
('R_ADMIN','1'),            -- 系统管理员(图:管理员)
('R_PRESIDENT','2'),        -- 分行行长(图:分行领导)
('R_BRANCH_EMP','3'),       -- 分行员工
('R_10552DF9','4'),         -- 支行领导
('R_SUB_EMP','5'),          -- 支行员工
('R_MAIL_GROUP','6'),       -- 邮件组
('R_DATA_REPORT','7'),      -- 数据上报
('R_POS_FIN','11'),         -- 头寸资财
('R_POS_BOND','12'),        -- 头寸发债
('R_OP_PERM','16'),         -- 操作权限
('R_RETAIL_LEAD','17'),     -- 零售部负责人
('R_PRICING_FIN','18'),     -- 定价资财
('R_PROXY_TEST','108'),     -- 代发测试
('R_CORP_LEAD','128'),      -- 公司部负责人
('R_FIN_LEAD','129'),       -- 资财部负责人
('R_CC4B61D4','130'),       -- 公司主管行领导
('R_RETAIL_VP','131'),      -- 零售主管行领导
('R_CORP_PERF_REV','148'),  -- 公司业绩预审
('R_RTL_PERF_REV','149'),   -- 零售业绩预审
('R_FIN_PRE_REV','168'),    -- 资财预审
('R_RTL_LOAN_MGR','169'),   -- 零售信贷负责人
('R_RTL_PRICE_REV','188'),  -- 零售定价预审
('R_SCREEN_VIEW','208'),    -- 大屏查询
('R_SCREEN_ADMIN','228'),   -- 大屏管理员
-- ===== 图片里没有的（编号 229 起，11 个）=====
('R_BACK_TECH','229'),      -- 中后台员工(科技)
('R_SUPPORT_STAFF','230'),  -- 中场支持部门人员
('R_SUPPORT_SEC','231'),    -- 中场支持部门秘书
('R_CORP_DEPT','232'),      -- 公司部人员
('R_RM','233'),             -- 客户经理
('R_CREDIT_REVIEWER','234'),-- 授信审查人员
('R_CREDIT_APPROVER','235'),-- 授信批复人员
('R_BRANCH_MGR','236'),     -- 经营机构负责人
('R_129EE164','237'),       -- 自由报表操作人员
('R_BACK_FINANCE','238'),   -- 资财部经办人
('R_RETAIL_DEPT','239');    -- 零售部人员

-- 安全校验：映射应覆盖全部 35 个角色，且新 id 无重复
-- SELECT (SELECT COUNT(*) FROM PT_ROLE) AS roles, (SELECT COUNT(*) FROM role_id_map) AS mapped;
-- SELECT new_id, COUNT(*) FROM role_id_map GROUP BY new_id HAVING COUNT(*)>1;  -- 应为空
-- SELECT ROLE_ID FROM PT_ROLE WHERE ROLE_ID NOT IN (SELECT old_id FROM role_id_map);  -- 应为空

-- 2) 级联更新 4 张表
UPDATE PT_ROLE            r JOIN role_id_map m ON r.ROLE_ID = m.old_id SET r.ROLE_ID = m.new_id;
UPDATE PT_USER_ROLE       r JOIN role_id_map m ON r.ROLE_ID = m.old_id SET r.ROLE_ID = m.new_id;
UPDATE PT_ROLE_RESOURCE   r JOIN role_id_map m ON r.ROLE_ID = m.old_id SET r.ROLE_ID = m.new_id;
UPDATE PT_ROLE_BIZ_SCOPE  r JOIN role_id_map m ON r.ROLE_ID = m.old_id SET r.ROLE_ID = m.new_id;

DROP TEMPORARY TABLE IF EXISTS role_id_map;

-- 3) 校验（执行后人工看一眼）
-- SELECT ROLE_ID, ROLE_CODE, ROLE_CHNAME FROM PT_ROLE ORDER BY CAST(ROLE_ID AS UNSIGNED);
-- SELECT COUNT(*) FROM PT_USER_ROLE      WHERE ROLE_ID REGEXP '^R_';  -- 应为 0
-- SELECT COUNT(*) FROM PT_ROLE_RESOURCE  WHERE ROLE_ID REGEXP '^R_';  -- 应为 0
-- SELECT COUNT(*) FROM PT_ROLE_BIZ_SCOPE WHERE ROLE_ID REGEXP '^R_';  -- 应为 0

-- 4) 清会话（迁移后所有人需重新登录，否则旧 role_id 缓存导致权限错乱）
-- DELETE FROM SPRING_SESSION_ATTRIBUTES;
-- DELETE FROM SPRING_SESSION;
