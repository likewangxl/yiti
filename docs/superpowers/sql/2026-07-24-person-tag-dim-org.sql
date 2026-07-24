-- ============================================================================
-- 业务标签（原「人员标签」）：PERSON_TAG_REL 增加维度支持（员工 / 机构）
-- 日期：2026-07-24
-- 背景：KPI 方案「标签范围」原只能圈员工；现将成员扩展为「员工(EMP)/机构(ORG)」两维，
--       机构成员按业务编号 dept_no（EXT_ORG_INFO.DEPT_NO 口径）录入。一个标签可混装两维成员。
-- 兼容：存量行 DIM_TYPE 默认 'EMP'、USERNAME 保持不变、ORG_DEPT_NO 为 NULL，无需数据回填。
-- ============================================================================

-- 1) 新增维度列 + 机构编号列；USERNAME 改可空（ORG 行无工号）
ALTER TABLE PERSON_TAG_REL
    ADD COLUMN DIM_TYPE VARCHAR(8) NOT NULL DEFAULT 'EMP'
        COMMENT '成员维度：EMP=员工 / ORG=机构' AFTER TAG_ID,
    ADD COLUMN ORG_DEPT_NO VARCHAR(60) NULL
        COMMENT '机构业务编号（EXT_ORG_INFO.DEPT_NO），DIM_TYPE=ORG 时有值' AFTER USERNAME,
    MODIFY COLUMN USERNAME VARCHAR(200) NULL
        COMMENT '员工工号（PT_USER.USERNAME），DIM_TYPE=EMP 时有值';

-- 2) 机构成员唯一键：同一标签下机构编号唯一。
--    EMP 行 ORG_DEPT_NO 为 NULL，MySQL 唯一索引允许多个 NULL，故与员工行互不冲突。
--    （既有 UK_PTR_TAG_USER(TAG_ID, USERNAME) 同理：ORG 行 USERNAME 为 NULL，不冲突，保留不动。）
ALTER TABLE PERSON_TAG_REL
    ADD UNIQUE KEY UK_PTR_TAG_ORG (TAG_ID, ORG_DEPT_NO);

-- 3) 机构编号普通索引（按机构反查成员用）
ALTER TABLE PERSON_TAG_REL
    ADD KEY IDX_PTR_ORG_DEPT_NO (ORG_DEPT_NO);
