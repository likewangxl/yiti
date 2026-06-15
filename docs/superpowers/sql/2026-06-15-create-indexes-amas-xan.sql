-- =====================================================================
-- 为 AMAS_* / XAN_M98_*_SHOW3 五张无索引表补建索引
-- 目标库：yiti(dev) + onepl(prod)（按部署惯例双跑；测试库可选）
-- 用法：mysql -uroot -p <库名> < 本文件
--
-- ⚠️ 注意事项
-- 1) MySQL 8.0 的 CREATE INDEX 不支持 IF NOT EXISTS —— 本脚本非幂等，
--    重复执行会因索引已存在报 1061。重跑前请先 DROP 或确认未建过。
-- 2) XAN_M98_*_SHOW3 两表列全部是 varchar(300)（gbk/utf8mb3 混杂），
--    故对其一律用「前缀索引」避免超长 / 索引臃肿（日期值实际 ~10 字符，
--    客户号/工号 ~几十字符，前缀已足够选择性）。
-- 3) XAN 两表数据量可能较大，已加 ALGORITHM=INPLACE, LOCK=NONE 走在线 DDL，
--    生产建库高峰期外执行。
-- 4) 仅 XAN_M98_CUST_STAT_SHOW3 有应用内实证查询（CustMasterMapper
--    .syncNewCustomersFromStat：WHERE STATIS_DT=? GROUP BY CUST_ID,CUST_NAME）；
--    其余 4 表索引按列注释的逻辑主键/外键语义设计（外部 AMAS / Hive 抽数消费）。
-- =====================================================================


-- =====================================================================
-- 1) AMAS_APPR_RECORD（审批记录；本应用未直接读写，外部 AMAS 系统使用）
--    访问模式：① 按源数据编号取审批链并按序号排序 ② 按审批人+状态查待办
-- =====================================================================
-- 审批链：REGION_DT_ID(源数据编号) + APPR_SEQ(序号) 排序
CREATE INDEX idx_amas_appr_record_region
    ON AMAS_APPR_RECORD (REGION_DT_ID, APPR_SEQ);
-- 待我审批：审批人工号 + 审批状态
CREATE INDEX idx_amas_appr_record_user_status
    ON AMAS_APPR_RECORD (APPR_USERNAME, APPR_STATUS);
-- 可选：编号唯一定位（若确认 RECORD_ID 业务唯一，可改成 UNIQUE）
CREATE INDEX idx_amas_appr_record_id
    ON AMAS_APPR_RECORD (RECORD_ID);


-- =====================================================================
-- 2) AMAS_PERFORMANCE_ALLOCATION（业绩调整分配信息；一个调整对多分配人）
--    访问模式：按业绩调整编号取全部分配明细（可叠加是否原分配过滤）
-- =====================================================================
-- 核心：按调整编号取分配明细 + IS_ORIGINAL 过滤原/现分配
CREATE INDEX idx_amas_perf_alloc_no
    ON AMAS_PERFORMANCE_ALLOCATION (PERF_ADJUST_NO, IS_ORIGINAL);
-- 可选：按分配人工号反查其参与的分配
CREATE INDEX idx_amas_perf_alloc_username
    ON AMAS_PERFORMANCE_ALLOCATION (USERNAME);


-- =====================================================================
-- 3) AMAS_PERF_ADJUST_APPROVAL（业绩调整审批表；PERF_ADJUST_NO 逻辑主键）
--    访问模式：① 按编号定位 ② 我的申请 ③ 待我审批 ④ 按客户号查
-- =====================================================================
-- 编号定位（若确认业务唯一，可改成 UNIQUE INDEX）
CREATE INDEX idx_amas_perf_appr_no
    ON AMAS_PERF_ADJUST_APPROVAL (PERF_ADJUST_NO);
-- 我的申请：申请人工号 + 审批状态
CREATE INDEX idx_amas_perf_appr_apply
    ON AMAS_PERF_ADJUST_APPROVAL (APPLY_USERNAME, APPR_STATUS);
-- 待我审批：当前审批人账号 + 审批状态
CREATE INDEX idx_amas_perf_appr_curr
    ON AMAS_PERF_ADJUST_APPROVAL (CURR_APPR_USERNAME, APPR_STATUS);
-- 按客户号查该客户的业绩调整审批
CREATE INDEX idx_amas_perf_appr_cust
    ON AMAS_PERF_ADJUST_APPROVAL (CUST_ID);


-- =====================================================================
-- 4) XAN_M98_CUST_STAT_SHOW3（客户指标统计展示；全列 varchar(300)，前缀索引）
--    实证查询：WHERE STATIS_DT=? AND CUST_ID<>'' GROUP BY CUST_ID,CUST_NAME
-- =====================================================================
-- 同步主查询：统计日期 + 客户号（覆盖 WHERE 过滤与 GROUP BY 收敛）
CREATE INDEX idx_xan_cust_stat_dt_cust
    ON XAN_M98_CUST_STAT_SHOW3 (STATIS_DT(20), CUST_ID(50))
    ALGORITHM=INPLACE, LOCK=NONE;
-- 可选：按统计日期 + 业绩分配者(管会) 的口径消费
CREATE INDEX idx_xan_cust_stat_dt_alloc
    ON XAN_M98_CUST_STAT_SHOW3 (STATIS_DT(20), ALLOCATER_ID_MAM(50))
    ALGORITHM=INPLACE, LOCK=NONE;


-- =====================================================================
-- 5) XAN_M98_EMP_STAT_SHOW3（员工指标统计展示；全列 varchar(300)，前缀索引）
--    访问模式（与 CUST 表同构）：按统计日期 + 员工号取指标；按机构/指标口径汇总
-- =====================================================================
-- 主查询：统计日期 + 员工号
CREATE INDEX idx_xan_emp_stat_dt_emp
    ON XAN_M98_EMP_STAT_SHOW3 (STATIS_DT(20), EMP_ID(50))
    ALGORITHM=INPLACE, LOCK=NONE;
-- 可选：按统计日期 + 五级统计机构号 做机构口径汇总
CREATE INDEX idx_xan_emp_stat_dt_org
    ON XAN_M98_EMP_STAT_SHOW3 (STATIS_DT(20), STAT_LEV5_ORG_ID(40))
    ALGORITHM=INPLACE, LOCK=NONE;

-- ===== 索引创建结束 =====
