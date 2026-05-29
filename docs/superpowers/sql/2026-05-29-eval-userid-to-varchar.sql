-- ============================================================
-- eval 模块工号列 BIGINT → VARCHAR(50) 治理
-- 日期: 2026-05-29
-- 背景: 工号本质字符型（PT_USER.USER_ID varchar(50)），eval 4 处误用 BIGINT。
-- 现网 EVAL_USER_TAG 仅少量行且为数字字符串，转换无损。
-- 注意: 仅改 USER_ID/CREATE_BY 列；TAG_ID/TASK_ID/RULE_ID/GROUP_ID/SCORE_ID/TARGET_ID 等主键/外键保持 BIGINT。
-- ============================================================

ALTER TABLE EVAL_USER_TAG    MODIFY COLUMN USER_ID         VARCHAR(50) NOT NULL COMMENT '人员工号，关联 PT_USER.USER_ID';
ALTER TABLE EVAL_TASK_TARGET MODIFY COLUMN BE_EVAL_USER_ID VARCHAR(50) NOT NULL COMMENT '被评价人工号';
ALTER TABLE EVAL_SCORE       MODIFY COLUMN EVAL_USER_ID    VARCHAR(50) NOT NULL COMMENT '评价人工号';
ALTER TABLE EVAL_TASK        MODIFY COLUMN CREATE_BY        VARCHAR(50) DEFAULT NULL COMMENT '创建人工号';
