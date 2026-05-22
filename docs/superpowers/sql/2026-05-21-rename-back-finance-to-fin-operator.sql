-- =========================================================
-- 2026-05-21 PT_ROLE.R_BACK_FINANCE 重命名为"资财部经办人"
-- =========================================================
-- 背景：
--   2026-05-20 已新增 R_CORP_LEAD / R_FIN_LEAD / R_RETAIL_LEAD 三个"负责人"角色，
--   alloc_adjust_approve_v1 流程 finance_review 节点的候选仍是
--   PT_ROLE.ROLE_CODE = 'BACK_FINANCE'（即 R_BACK_FINANCE），它实际承担的就是
--   "资财部经办人"职责，与 R_FIN_LEAD（资财部负责人）配对完成两级审批。
--
--   但 R_BACK_FINANCE.ROLE_CHNAME 历史命名是"中后台员工(资财)"，与公司部/零售部
--   "经办人 + 负责人"对称命名不一致（R_CORP_DEPT=公司部人员 / R_CORP_LEAD=公司部
--   负责人 / R_RETAIL_DEPT=零售部人员 / R_RETAIL_LEAD=零售部负责人）。
--
-- 处置：
--   仅修改 R_BACK_FINANCE.ROLE_CHNAME 为"资财部经办人"，保留 ROLE_ID
--   (R_BACK_FINANCE) 与 ROLE_CODE (BACK_FINANCE) 不动 —— 它们被以下表外键引用：
--     - WF_NODE_CANDIDATE_CONF.candidate_value（WNC_ALLOC_FIN 节点候选 BACK_FINANCE）
--     - PT_USER_ROLE.ROLE_ID（R_BACK_FINANCE 已绑测试用户 E40001）
--     - PT_ROLE_RESOURCE.ROLE_ID（R_BACK_FINANCE 持有大量授权）
--   仅改中文名零风险，不影响登录/鉴权/流程候选解析。
--
-- 部署范围（双库执行）：
--   - onepl  生产/文档基线库
--   - yiti   本地 dev profile 实际连接库
--
-- 备份：
--   docs/superpowers/sql/backup/2026-05-21-pre-rename-back-finance-yiti.sql
--   docs/superpowers/sql/backup/2026-05-21-pre-rename-back-finance-onepl.sql
--
-- 幂等：WHERE 精确限定 ROLE_ID + 旧 ROLE_CHNAME，重跑命中 0 行。
-- 注：PT_ROLE.REMARK 列上限 100 字符，2026-05-20 已 CONCAT 至 84 字符，
--    本次不再 CONCAT 改为覆写一行精简摘要避免 1406 Data too long。
-- =========================================================

UPDATE PT_ROLE
SET ROLE_CHNAME = '资财部经办人',
    UPDATE_TIME = '2026-05-21 00:00:00',
    UPDATE_USER = 'seed',
    REMARK = '资财部经办人(原 中后台员工(资财))；CODE=BACK_FINANCE，配对 R_FIN_LEAD 两级审批'
WHERE ROLE_ID = 'R_BACK_FINANCE'
  AND ROLE_CHNAME = '中后台员工(资财)';

-- =========================================================
-- 验证
--   SELECT ROLE_ID, ROLE_CODE, ROLE_CHNAME FROM PT_ROLE
--   WHERE ROLE_ID IN ('R_BACK_FINANCE','R_FIN_LEAD') ORDER BY ROLE_ID;
--   -- 期望:
--   --   R_BACK_FINANCE  BACK_FINANCE     资财部经办人
--   --   R_FIN_LEAD      FINANCE_LEADER   资财部负责人
-- =========================================================
