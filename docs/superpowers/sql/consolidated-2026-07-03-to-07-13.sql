-- =====================================================================
-- 合并脚本：docs/superpowers/sql 中 2026-07-03 ~ 2026-07-13 的迁移脚本汇总
-- 生成规则：
--   1) 已去除库名前缀（yiti. / onepl. → 无前缀，落当前连接库）
--   2) 按时间(文件日期)先后拼接
--   3) 前后冲突/被取代的语句按「最后一个为准」处理：
--      - stat-show-archive：SYS_JOB_CONF 的 yiti/onepl 两条重复 INSERT 去重为 1 条
--      - 大屏种子 dashboard-seed / dashboard-seed-province-fix / fix-kpi-cycletype 三个文件
--        的数据(屏 9101-9103 / 数据源 9001-9007 / block 1-10)已被 2026-07-13 canvas-seed
--        全量取代(canvas-seed 已含 province 修复与 9003 cycleType=YEARLY 修复)，故【略去】，
--        仅保留最终版 canvas-seed（其原作者注释亦明确要求跳过 dashboard-seed 系列）。
--   执行库：单库执行即可（原脚本设计为 yiti + onepl/onepl_test_bootstrap 双库分别执行）。
--   4) 补录（2026-07-14）：初版漏拼以下两块，现已按时间序/数字 ID 口径补齐：
--      - [07-09] 指标重算任务监控页（短线任务重算）：perf-run-task-add-trigger-type（PERF_RUN_TASK.trigger_type 列）
--        + perf-task-monitor-menu（M_PERF_TASK_MONITOR 菜单资源 + 角色绑定），已插到 07-03 与 07-11 之间。
--      - [07-13] 审批流监控 Task6 基座：RES_WF_MONITOR_LIST 资源行 + 秘书(231)/行长(2) 角色信息(PT_ROLE)
--        + 秘书=ORG/行长=ALL 数据范围(PT_ROLE_BIZ_SCOPE)，已插到 workflow-monitor-menu 段之前（数字 ID）。
-- =====================================================================


-- ########## [07-03] stat-show-archive-hist-ddl.sql —— 6 张旬度归档历史表 ##########
-- =====================================================================
-- 统计展示表旬度归档 —— 6 张历史表 DDL
-- 归档任务 STAT_SHOW_ARCHIVE 用；历史表与主表字段/索引/charset 完全一致（本 DDL 由主表
--   XAN_M98_CUST_STAT_SHOW3 / XAN_M98_EMP_STAT_SHOW3 逐字复制并重命名生成）。
--   _H2=每月1~10日、_H3=11~20日、_H1=21~月末（详见开发方案）。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。需在 yiti（dev）与 onepl（prod）双库执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `XAN_M98_CUST_STAT_SHOW3_H1` (
  `BRANCH_NO` varchar(300) DEFAULT NULL COMMENT '所属分行',
  `CUST_TYPE_CD` varchar(300) DEFAULT NULL COMMENT '客户类型代码',
  `CUST_VIEW_1_LEV` varchar(300) DEFAULT NULL COMMENT '一级客群',
  `CUST_VIEW_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '一级客群名称',
  `CUST_VIEW_2_LEV` varchar(300) DEFAULT NULL COMMENT '二级客群',
  `CUST_VIEW_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '二级客群名称',
  `CUST_VIEW_3_LEV` varchar(300) DEFAULT NULL COMMENT '三级客群',
  `CUST_VIEW_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '三级客群名称',
  `CUST_ID` varchar(300) DEFAULT NULL COMMENT '客户号',
  `CUST_NAME` varchar(300) DEFAULT NULL COMMENT '客户名称',
  `ACCT_ORG_ID` varchar(300) DEFAULT NULL COMMENT '账务机构',
  `ACCT_ORG_ID_NAME` varchar(300) CHARACTER SET gbk COLLATE gbk_chinese_ci DEFAULT NULL COMMENT '账务机构名称',
  `ACCT_FIRST_BRANCH_MAM` varchar(300) DEFAULT NULL COMMENT '账务分行',
  `ACCT_FIRST_BRANCH_MAM_NAME` varchar(300) DEFAULT NULL COMMENT '账务分行名称',
  `STAT_LEV3_ORG_ID` varchar(300) DEFAULT NULL COMMENT '三级统计机构号',
  `STAT_LEV3_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '三级统计机构名',
  `STAT_LEV4_ORG_ID` varchar(300) DEFAULT NULL COMMENT '四级统计机构号',
  `STAT_LEV4_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '四级统计机构名',
  `STAT_LEV5_ORG_ID` varchar(300) DEFAULT NULL COMMENT '五级统计机构号',
  `STAT_LEV5_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '五级统计机构名',
  `ALLOCATER_ID_MAM` varchar(300) DEFAULT NULL COMMENT '业绩分配者编号(管会)',
  `ALLOCATER_NAME_MAM` varchar(300) DEFAULT NULL COMMENT '业绩分配者名称(管会)',
  `PLATE_ID` varchar(300) DEFAULT NULL COMMENT '板块编号',
  `PLATE_NAME` varchar(300) DEFAULT NULL COMMENT '板块名称',
  `ONE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '1级编码_规模',
  `ONE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '1级名称_规模',
  `TWO_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '2级编码_规模',
  `TWO_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '2级名称_规模',
  `THREE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '3级编码_规模',
  `THREE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '3级名称_规模',
  `FOUR_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '4级编码_规模',
  `FOUR_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '4级名称_规模',
  `FIVE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '5级编码_规模',
  `FIVE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '5级名称_规模',
  `BIZ_CD` varchar(300) DEFAULT NULL COMMENT '规模业务代码',
  `BIZ_SEQ` varchar(300) DEFAULT NULL COMMENT '规模业务流水',
  `ID_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级编码_损益',
  `NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级名称_损益',
  `ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级编码_损益',
  `NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级名称_损益',
  `ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级编码_损益',
  `NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级名称_损益',
  `ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级编码_损益',
  `NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级名称_损益',
  `ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级编码_损益',
  `NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级名称_损益',
  `PL_BIZ_CD` varchar(300) DEFAULT NULL COMMENT '损益业务代码',
  `PL_BIZ_SEQ` varchar(300) DEFAULT NULL COMMENT '损益业务流水',
  `RPT_ID_1_LEV` varchar(300) DEFAULT NULL COMMENT '报表1级编码',
  `RPT_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '报表1级名称',
  `RPT_ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '报表2级编码',
  `RPT_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '报表2级名称',
  `RPT_ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '报表3级编码',
  `RPT_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '报表3级名称',
  `RPT_ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '报表4级编码',
  `RPT_NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '报表4级名称',
  `RPT_ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '报表5级编码',
  `RPT_NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '报表5级名称',
  `STATS_CYCLE` varchar(300) DEFAULT NULL COMMENT '统计周期',
  `ACCT_FLAG` varchar(300) DEFAULT NULL COMMENT '存款活分类',
  `ACCT_SEQ` varchar(300) DEFAULT NULL COMMENT '账户流水',
  `ASSET_FIVE_LEV_CAT_CD` varchar(300) DEFAULT NULL COMMENT '五级分类标识',
  `CURRENCY_CD` varchar(300) DEFAULT NULL COMMENT '规模币种',
  `CURR_BAL` varchar(300) DEFAULT NULL COMMENT '当前余额',
  `M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '月日均余额',
  `Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '季日均余额',
  `Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '年日均余额',
  `INT_BAL` varchar(300) DEFAULT NULL COMMENT '利息收支',
  `FTP_BAL` varchar(300) DEFAULT NULL COMMENT 'FTP收支',
  `FEE_BAL` varchar(300) DEFAULT NULL COMMENT '中间业务收入',
  `INVEST_VAL` varchar(300) DEFAULT NULL COMMENT '投资收益与公允价值变动',
  `SALES_AND_ADDTAX` varchar(300) DEFAULT NULL COMMENT '增值税及附加',
  `FTP_REVENUE` varchar(300) DEFAULT NULL COMMENT '营业净收入',
  `EXPECT_LOSS` varchar(300) DEFAULT NULL COMMENT '资产减值损失',
  `BUSINESS_EXPENSE` varchar(300) DEFAULT NULL COMMENT '营业费用',
  `SALES_OUT` varchar(300) DEFAULT NULL COMMENT '营业外收支',
  `PROFIT_BEFTAX` varchar(300) DEFAULT NULL COMMENT '税前利润',
  `INCOME_TAX` varchar(300) DEFAULT NULL COMMENT '所得税',
  `RWA_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA余额',
  `RWA_M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA月日均',
  `RWA_Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA季日均',
  `RWA_Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA年日均',
  `RISK_COST` varchar(300) DEFAULT NULL COMMENT '风险资本占用',
  `EVA_BAL` varchar(300) DEFAULT NULL COMMENT '经济增加值EVA',
  `FLAG` varchar(300) DEFAULT NULL COMMENT '标签位',
  `REMARK` varchar(300) DEFAULT NULL COMMENT '备注',
  `DATA_SRC` varchar(300) DEFAULT NULL COMMENT '数据来源',
  `STATIS_DT` varchar(300) CHARACTER SET gbk COLLATE gbk_chinese_ci DEFAULT NULL COMMENT '统计日期',
  KEY `idx_statis_dt` (`STATIS_DT`),
  KEY `idx_cust` (`CUST_ID`,`CUST_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=gbk ROW_FORMAT=DYNAMIC COMMENT='客户指标统计展示表';

CREATE TABLE IF NOT EXISTS `XAN_M98_CUST_STAT_SHOW3_H2` (
  `BRANCH_NO` varchar(300) DEFAULT NULL COMMENT '所属分行',
  `CUST_TYPE_CD` varchar(300) DEFAULT NULL COMMENT '客户类型代码',
  `CUST_VIEW_1_LEV` varchar(300) DEFAULT NULL COMMENT '一级客群',
  `CUST_VIEW_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '一级客群名称',
  `CUST_VIEW_2_LEV` varchar(300) DEFAULT NULL COMMENT '二级客群',
  `CUST_VIEW_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '二级客群名称',
  `CUST_VIEW_3_LEV` varchar(300) DEFAULT NULL COMMENT '三级客群',
  `CUST_VIEW_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '三级客群名称',
  `CUST_ID` varchar(300) DEFAULT NULL COMMENT '客户号',
  `CUST_NAME` varchar(300) DEFAULT NULL COMMENT '客户名称',
  `ACCT_ORG_ID` varchar(300) DEFAULT NULL COMMENT '账务机构',
  `ACCT_ORG_ID_NAME` varchar(300) CHARACTER SET gbk COLLATE gbk_chinese_ci DEFAULT NULL COMMENT '账务机构名称',
  `ACCT_FIRST_BRANCH_MAM` varchar(300) DEFAULT NULL COMMENT '账务分行',
  `ACCT_FIRST_BRANCH_MAM_NAME` varchar(300) DEFAULT NULL COMMENT '账务分行名称',
  `STAT_LEV3_ORG_ID` varchar(300) DEFAULT NULL COMMENT '三级统计机构号',
  `STAT_LEV3_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '三级统计机构名',
  `STAT_LEV4_ORG_ID` varchar(300) DEFAULT NULL COMMENT '四级统计机构号',
  `STAT_LEV4_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '四级统计机构名',
  `STAT_LEV5_ORG_ID` varchar(300) DEFAULT NULL COMMENT '五级统计机构号',
  `STAT_LEV5_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '五级统计机构名',
  `ALLOCATER_ID_MAM` varchar(300) DEFAULT NULL COMMENT '业绩分配者编号(管会)',
  `ALLOCATER_NAME_MAM` varchar(300) DEFAULT NULL COMMENT '业绩分配者名称(管会)',
  `PLATE_ID` varchar(300) DEFAULT NULL COMMENT '板块编号',
  `PLATE_NAME` varchar(300) DEFAULT NULL COMMENT '板块名称',
  `ONE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '1级编码_规模',
  `ONE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '1级名称_规模',
  `TWO_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '2级编码_规模',
  `TWO_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '2级名称_规模',
  `THREE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '3级编码_规模',
  `THREE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '3级名称_规模',
  `FOUR_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '4级编码_规模',
  `FOUR_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '4级名称_规模',
  `FIVE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '5级编码_规模',
  `FIVE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '5级名称_规模',
  `BIZ_CD` varchar(300) DEFAULT NULL COMMENT '规模业务代码',
  `BIZ_SEQ` varchar(300) DEFAULT NULL COMMENT '规模业务流水',
  `ID_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级编码_损益',
  `NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级名称_损益',
  `ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级编码_损益',
  `NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级名称_损益',
  `ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级编码_损益',
  `NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级名称_损益',
  `ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级编码_损益',
  `NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级名称_损益',
  `ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级编码_损益',
  `NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级名称_损益',
  `PL_BIZ_CD` varchar(300) DEFAULT NULL COMMENT '损益业务代码',
  `PL_BIZ_SEQ` varchar(300) DEFAULT NULL COMMENT '损益业务流水',
  `RPT_ID_1_LEV` varchar(300) DEFAULT NULL COMMENT '报表1级编码',
  `RPT_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '报表1级名称',
  `RPT_ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '报表2级编码',
  `RPT_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '报表2级名称',
  `RPT_ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '报表3级编码',
  `RPT_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '报表3级名称',
  `RPT_ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '报表4级编码',
  `RPT_NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '报表4级名称',
  `RPT_ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '报表5级编码',
  `RPT_NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '报表5级名称',
  `STATS_CYCLE` varchar(300) DEFAULT NULL COMMENT '统计周期',
  `ACCT_FLAG` varchar(300) DEFAULT NULL COMMENT '存款活分类',
  `ACCT_SEQ` varchar(300) DEFAULT NULL COMMENT '账户流水',
  `ASSET_FIVE_LEV_CAT_CD` varchar(300) DEFAULT NULL COMMENT '五级分类标识',
  `CURRENCY_CD` varchar(300) DEFAULT NULL COMMENT '规模币种',
  `CURR_BAL` varchar(300) DEFAULT NULL COMMENT '当前余额',
  `M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '月日均余额',
  `Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '季日均余额',
  `Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '年日均余额',
  `INT_BAL` varchar(300) DEFAULT NULL COMMENT '利息收支',
  `FTP_BAL` varchar(300) DEFAULT NULL COMMENT 'FTP收支',
  `FEE_BAL` varchar(300) DEFAULT NULL COMMENT '中间业务收入',
  `INVEST_VAL` varchar(300) DEFAULT NULL COMMENT '投资收益与公允价值变动',
  `SALES_AND_ADDTAX` varchar(300) DEFAULT NULL COMMENT '增值税及附加',
  `FTP_REVENUE` varchar(300) DEFAULT NULL COMMENT '营业净收入',
  `EXPECT_LOSS` varchar(300) DEFAULT NULL COMMENT '资产减值损失',
  `BUSINESS_EXPENSE` varchar(300) DEFAULT NULL COMMENT '营业费用',
  `SALES_OUT` varchar(300) DEFAULT NULL COMMENT '营业外收支',
  `PROFIT_BEFTAX` varchar(300) DEFAULT NULL COMMENT '税前利润',
  `INCOME_TAX` varchar(300) DEFAULT NULL COMMENT '所得税',
  `RWA_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA余额',
  `RWA_M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA月日均',
  `RWA_Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA季日均',
  `RWA_Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA年日均',
  `RISK_COST` varchar(300) DEFAULT NULL COMMENT '风险资本占用',
  `EVA_BAL` varchar(300) DEFAULT NULL COMMENT '经济增加值EVA',
  `FLAG` varchar(300) DEFAULT NULL COMMENT '标签位',
  `REMARK` varchar(300) DEFAULT NULL COMMENT '备注',
  `DATA_SRC` varchar(300) DEFAULT NULL COMMENT '数据来源',
  `STATIS_DT` varchar(300) CHARACTER SET gbk COLLATE gbk_chinese_ci DEFAULT NULL COMMENT '统计日期',
  KEY `idx_statis_dt` (`STATIS_DT`),
  KEY `idx_cust` (`CUST_ID`,`CUST_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=gbk ROW_FORMAT=DYNAMIC COMMENT='客户指标统计展示表';

CREATE TABLE IF NOT EXISTS `XAN_M98_CUST_STAT_SHOW3_H3` (
  `BRANCH_NO` varchar(300) DEFAULT NULL COMMENT '所属分行',
  `CUST_TYPE_CD` varchar(300) DEFAULT NULL COMMENT '客户类型代码',
  `CUST_VIEW_1_LEV` varchar(300) DEFAULT NULL COMMENT '一级客群',
  `CUST_VIEW_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '一级客群名称',
  `CUST_VIEW_2_LEV` varchar(300) DEFAULT NULL COMMENT '二级客群',
  `CUST_VIEW_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '二级客群名称',
  `CUST_VIEW_3_LEV` varchar(300) DEFAULT NULL COMMENT '三级客群',
  `CUST_VIEW_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '三级客群名称',
  `CUST_ID` varchar(300) DEFAULT NULL COMMENT '客户号',
  `CUST_NAME` varchar(300) DEFAULT NULL COMMENT '客户名称',
  `ACCT_ORG_ID` varchar(300) DEFAULT NULL COMMENT '账务机构',
  `ACCT_ORG_ID_NAME` varchar(300) CHARACTER SET gbk COLLATE gbk_chinese_ci DEFAULT NULL COMMENT '账务机构名称',
  `ACCT_FIRST_BRANCH_MAM` varchar(300) DEFAULT NULL COMMENT '账务分行',
  `ACCT_FIRST_BRANCH_MAM_NAME` varchar(300) DEFAULT NULL COMMENT '账务分行名称',
  `STAT_LEV3_ORG_ID` varchar(300) DEFAULT NULL COMMENT '三级统计机构号',
  `STAT_LEV3_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '三级统计机构名',
  `STAT_LEV4_ORG_ID` varchar(300) DEFAULT NULL COMMENT '四级统计机构号',
  `STAT_LEV4_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '四级统计机构名',
  `STAT_LEV5_ORG_ID` varchar(300) DEFAULT NULL COMMENT '五级统计机构号',
  `STAT_LEV5_ORG_NAME` varchar(300) DEFAULT NULL COMMENT '五级统计机构名',
  `ALLOCATER_ID_MAM` varchar(300) DEFAULT NULL COMMENT '业绩分配者编号(管会)',
  `ALLOCATER_NAME_MAM` varchar(300) DEFAULT NULL COMMENT '业绩分配者名称(管会)',
  `PLATE_ID` varchar(300) DEFAULT NULL COMMENT '板块编号',
  `PLATE_NAME` varchar(300) DEFAULT NULL COMMENT '板块名称',
  `ONE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '1级编码_规模',
  `ONE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '1级名称_规模',
  `TWO_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '2级编码_规模',
  `TWO_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '2级名称_规模',
  `THREE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '3级编码_规模',
  `THREE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '3级名称_规模',
  `FOUR_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '4级编码_规模',
  `FOUR_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '4级名称_规模',
  `FIVE_LEVL_CD` varchar(300) DEFAULT NULL COMMENT '5级编码_规模',
  `FIVE_LEVL_NAME` varchar(300) DEFAULT NULL COMMENT '5级名称_规模',
  `BIZ_CD` varchar(300) DEFAULT NULL COMMENT '规模业务代码',
  `BIZ_SEQ` varchar(300) DEFAULT NULL COMMENT '规模业务流水',
  `ID_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级编码_损益',
  `NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级名称_损益',
  `ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级编码_损益',
  `NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级名称_损益',
  `ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级编码_损益',
  `NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级名称_损益',
  `ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级编码_损益',
  `NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级名称_损益',
  `ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级编码_损益',
  `NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级名称_损益',
  `PL_BIZ_CD` varchar(300) DEFAULT NULL COMMENT '损益业务代码',
  `PL_BIZ_SEQ` varchar(300) DEFAULT NULL COMMENT '损益业务流水',
  `RPT_ID_1_LEV` varchar(300) DEFAULT NULL COMMENT '报表1级编码',
  `RPT_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '报表1级名称',
  `RPT_ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '报表2级编码',
  `RPT_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '报表2级名称',
  `RPT_ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '报表3级编码',
  `RPT_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '报表3级名称',
  `RPT_ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '报表4级编码',
  `RPT_NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '报表4级名称',
  `RPT_ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '报表5级编码',
  `RPT_NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '报表5级名称',
  `STATS_CYCLE` varchar(300) DEFAULT NULL COMMENT '统计周期',
  `ACCT_FLAG` varchar(300) DEFAULT NULL COMMENT '存款活分类',
  `ACCT_SEQ` varchar(300) DEFAULT NULL COMMENT '账户流水',
  `ASSET_FIVE_LEV_CAT_CD` varchar(300) DEFAULT NULL COMMENT '五级分类标识',
  `CURRENCY_CD` varchar(300) DEFAULT NULL COMMENT '规模币种',
  `CURR_BAL` varchar(300) DEFAULT NULL COMMENT '当前余额',
  `M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '月日均余额',
  `Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '季日均余额',
  `Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '年日均余额',
  `INT_BAL` varchar(300) DEFAULT NULL COMMENT '利息收支',
  `FTP_BAL` varchar(300) DEFAULT NULL COMMENT 'FTP收支',
  `FEE_BAL` varchar(300) DEFAULT NULL COMMENT '中间业务收入',
  `INVEST_VAL` varchar(300) DEFAULT NULL COMMENT '投资收益与公允价值变动',
  `SALES_AND_ADDTAX` varchar(300) DEFAULT NULL COMMENT '增值税及附加',
  `FTP_REVENUE` varchar(300) DEFAULT NULL COMMENT '营业净收入',
  `EXPECT_LOSS` varchar(300) DEFAULT NULL COMMENT '资产减值损失',
  `BUSINESS_EXPENSE` varchar(300) DEFAULT NULL COMMENT '营业费用',
  `SALES_OUT` varchar(300) DEFAULT NULL COMMENT '营业外收支',
  `PROFIT_BEFTAX` varchar(300) DEFAULT NULL COMMENT '税前利润',
  `INCOME_TAX` varchar(300) DEFAULT NULL COMMENT '所得税',
  `RWA_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA余额',
  `RWA_M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA月日均',
  `RWA_Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA季日均',
  `RWA_Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA年日均',
  `RISK_COST` varchar(300) DEFAULT NULL COMMENT '风险资本占用',
  `EVA_BAL` varchar(300) DEFAULT NULL COMMENT '经济增加值EVA',
  `FLAG` varchar(300) DEFAULT NULL COMMENT '标签位',
  `REMARK` varchar(300) DEFAULT NULL COMMENT '备注',
  `DATA_SRC` varchar(300) DEFAULT NULL COMMENT '数据来源',
  `STATIS_DT` varchar(300) CHARACTER SET gbk COLLATE gbk_chinese_ci DEFAULT NULL COMMENT '统计日期',
  KEY `idx_statis_dt` (`STATIS_DT`),
  KEY `idx_cust` (`CUST_ID`,`CUST_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=gbk ROW_FORMAT=DYNAMIC COMMENT='客户指标统计展示表';

CREATE TABLE IF NOT EXISTS `XAN_M98_EMP_STAT_SHOW3_H1` (
  `BRANCH_NO` varchar(300) DEFAULT NULL COMMENT '所属分行',
  `STAT_LEV3_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '三级统计机构号',
  `STAT_LEV3_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '三级统计机构名',
  `STAT_LEV4_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '四级统计机构号',
  `STAT_LEV4_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '四级统计机构名',
  `STAT_LEV5_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '五级统计机构号',
  `STAT_LEV5_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '五级统计机构名',
  `EMP_ID` varchar(300) DEFAULT NULL COMMENT '员工号',
  `EMP_NAME` varchar(300) DEFAULT NULL COMMENT '员工姓名',
  `EMP_STAT` varchar(300) DEFAULT NULL COMMENT '员工状态',
  `IND_TYPE` varchar(300) DEFAULT NULL COMMENT '指标类型',
  `IND_ID_1_LEV` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '1级编码',
  `IND_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级名称',
  `IND_ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级编码',
  `IND_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级名称',
  `IND_ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级编码',
  `IND_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级名称',
  `IND_ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级编码',
  `IND_NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级名称',
  `IND_ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级编码',
  `IND_NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级名称',
  `IND_ID_6_LEV` varchar(300) DEFAULT NULL COMMENT '6级编码',
  `IND_NAME_6_LEV` varchar(300) DEFAULT NULL COMMENT '6级名称',
  `SUB_PROJ_TYPE` varchar(300) DEFAULT NULL COMMENT '子项类型',
  `SUB_PROJ_CODE` varchar(300) DEFAULT NULL COMMENT '子项代码',
  `SUB_PROJ_NAME` varchar(300) DEFAULT NULL COMMENT '子项名称',
  `CURRENCY_TYPE` varchar(300) DEFAULT NULL COMMENT '币种类型',
  `CURRENCY_DESC` varchar(300) DEFAULT NULL COMMENT '币种描述',
  `CURR_BAL` varchar(300) DEFAULT NULL COMMENT '当前余额',
  `M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '月日均余额',
  `Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '季日均余额',
  `Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '年日均余额',
  `INT_BAL` varchar(300) DEFAULT NULL COMMENT '利息收支',
  `FTP_BAL` varchar(300) DEFAULT NULL COMMENT 'FTP收支',
  `FEE_BAL` varchar(300) DEFAULT NULL COMMENT '中间业务收入',
  `INVEST_VAL` varchar(300) DEFAULT NULL COMMENT '投资收益与公允价值变动',
  `SALES_AND_ADDTAX` varchar(300) DEFAULT NULL COMMENT '增值税及附加',
  `FTP_REVENUE` varchar(300) DEFAULT NULL COMMENT 'FTP收入',
  `EXPECT_LOSS` varchar(300) DEFAULT NULL COMMENT '预计损失',
  `BUSINESS_EXPENSE` varchar(300) DEFAULT NULL COMMENT '营业费用',
  `SALES_OUT` varchar(300) DEFAULT NULL COMMENT '营业外收支',
  `PROFIT_BEFTAX` varchar(300) DEFAULT NULL COMMENT '税前利润',
  `INCOME_TAX` varchar(300) DEFAULT NULL COMMENT '所得税',
  `RWA_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA余额',
  `RWA_M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA月日均',
  `RWA_Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA季日均',
  `RWA_Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA年日均',
  `RISK_COST` varchar(300) DEFAULT NULL COMMENT '风险资本占用',
  `EVA_BAL` varchar(300) DEFAULT NULL COMMENT '经济增加值EVA',
  `FLAG` varchar(300) DEFAULT NULL COMMENT '标志',
  `REMARK` varchar(300) DEFAULT NULL COMMENT '备注',
  `DATA_SRC` varchar(300) DEFAULT NULL COMMENT '数据来源',
  `HIVE_SYS_TIME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多租户系统日期',
  `STATIS_DT` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '统计日期',
  KEY `idx_BRANCH_NO` (`STATIS_DT`,`BRANCH_NO`),
  KEY `idx_EMP_ID` (`STATIS_DT`,`EMP_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='员工指标统计展示表';

CREATE TABLE IF NOT EXISTS `XAN_M98_EMP_STAT_SHOW3_H2` (
  `BRANCH_NO` varchar(300) DEFAULT NULL COMMENT '所属分行',
  `STAT_LEV3_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '三级统计机构号',
  `STAT_LEV3_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '三级统计机构名',
  `STAT_LEV4_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '四级统计机构号',
  `STAT_LEV4_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '四级统计机构名',
  `STAT_LEV5_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '五级统计机构号',
  `STAT_LEV5_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '五级统计机构名',
  `EMP_ID` varchar(300) DEFAULT NULL COMMENT '员工号',
  `EMP_NAME` varchar(300) DEFAULT NULL COMMENT '员工姓名',
  `EMP_STAT` varchar(300) DEFAULT NULL COMMENT '员工状态',
  `IND_TYPE` varchar(300) DEFAULT NULL COMMENT '指标类型',
  `IND_ID_1_LEV` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '1级编码',
  `IND_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级名称',
  `IND_ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级编码',
  `IND_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级名称',
  `IND_ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级编码',
  `IND_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级名称',
  `IND_ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级编码',
  `IND_NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级名称',
  `IND_ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级编码',
  `IND_NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级名称',
  `IND_ID_6_LEV` varchar(300) DEFAULT NULL COMMENT '6级编码',
  `IND_NAME_6_LEV` varchar(300) DEFAULT NULL COMMENT '6级名称',
  `SUB_PROJ_TYPE` varchar(300) DEFAULT NULL COMMENT '子项类型',
  `SUB_PROJ_CODE` varchar(300) DEFAULT NULL COMMENT '子项代码',
  `SUB_PROJ_NAME` varchar(300) DEFAULT NULL COMMENT '子项名称',
  `CURRENCY_TYPE` varchar(300) DEFAULT NULL COMMENT '币种类型',
  `CURRENCY_DESC` varchar(300) DEFAULT NULL COMMENT '币种描述',
  `CURR_BAL` varchar(300) DEFAULT NULL COMMENT '当前余额',
  `M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '月日均余额',
  `Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '季日均余额',
  `Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '年日均余额',
  `INT_BAL` varchar(300) DEFAULT NULL COMMENT '利息收支',
  `FTP_BAL` varchar(300) DEFAULT NULL COMMENT 'FTP收支',
  `FEE_BAL` varchar(300) DEFAULT NULL COMMENT '中间业务收入',
  `INVEST_VAL` varchar(300) DEFAULT NULL COMMENT '投资收益与公允价值变动',
  `SALES_AND_ADDTAX` varchar(300) DEFAULT NULL COMMENT '增值税及附加',
  `FTP_REVENUE` varchar(300) DEFAULT NULL COMMENT 'FTP收入',
  `EXPECT_LOSS` varchar(300) DEFAULT NULL COMMENT '预计损失',
  `BUSINESS_EXPENSE` varchar(300) DEFAULT NULL COMMENT '营业费用',
  `SALES_OUT` varchar(300) DEFAULT NULL COMMENT '营业外收支',
  `PROFIT_BEFTAX` varchar(300) DEFAULT NULL COMMENT '税前利润',
  `INCOME_TAX` varchar(300) DEFAULT NULL COMMENT '所得税',
  `RWA_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA余额',
  `RWA_M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA月日均',
  `RWA_Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA季日均',
  `RWA_Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA年日均',
  `RISK_COST` varchar(300) DEFAULT NULL COMMENT '风险资本占用',
  `EVA_BAL` varchar(300) DEFAULT NULL COMMENT '经济增加值EVA',
  `FLAG` varchar(300) DEFAULT NULL COMMENT '标志',
  `REMARK` varchar(300) DEFAULT NULL COMMENT '备注',
  `DATA_SRC` varchar(300) DEFAULT NULL COMMENT '数据来源',
  `HIVE_SYS_TIME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多租户系统日期',
  `STATIS_DT` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '统计日期',
  KEY `idx_BRANCH_NO` (`STATIS_DT`,`BRANCH_NO`),
  KEY `idx_EMP_ID` (`STATIS_DT`,`EMP_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='员工指标统计展示表';

CREATE TABLE IF NOT EXISTS `XAN_M98_EMP_STAT_SHOW3_H3` (
  `BRANCH_NO` varchar(300) DEFAULT NULL COMMENT '所属分行',
  `STAT_LEV3_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '三级统计机构号',
  `STAT_LEV3_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '三级统计机构名',
  `STAT_LEV4_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '四级统计机构号',
  `STAT_LEV4_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '四级统计机构名',
  `STAT_LEV5_ORG_ID` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '五级统计机构号',
  `STAT_LEV5_ORG_NAME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '五级统计机构名',
  `EMP_ID` varchar(300) DEFAULT NULL COMMENT '员工号',
  `EMP_NAME` varchar(300) DEFAULT NULL COMMENT '员工姓名',
  `EMP_STAT` varchar(300) DEFAULT NULL COMMENT '员工状态',
  `IND_TYPE` varchar(300) DEFAULT NULL COMMENT '指标类型',
  `IND_ID_1_LEV` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '1级编码',
  `IND_NAME_1_LEV` varchar(300) DEFAULT NULL COMMENT '1级名称',
  `IND_ID_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级编码',
  `IND_NAME_2_LEV` varchar(300) DEFAULT NULL COMMENT '2级名称',
  `IND_ID_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级编码',
  `IND_NAME_3_LEV` varchar(300) DEFAULT NULL COMMENT '3级名称',
  `IND_ID_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级编码',
  `IND_NAME_4_LEV` varchar(300) DEFAULT NULL COMMENT '4级名称',
  `IND_ID_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级编码',
  `IND_NAME_5_LEV` varchar(300) DEFAULT NULL COMMENT '5级名称',
  `IND_ID_6_LEV` varchar(300) DEFAULT NULL COMMENT '6级编码',
  `IND_NAME_6_LEV` varchar(300) DEFAULT NULL COMMENT '6级名称',
  `SUB_PROJ_TYPE` varchar(300) DEFAULT NULL COMMENT '子项类型',
  `SUB_PROJ_CODE` varchar(300) DEFAULT NULL COMMENT '子项代码',
  `SUB_PROJ_NAME` varchar(300) DEFAULT NULL COMMENT '子项名称',
  `CURRENCY_TYPE` varchar(300) DEFAULT NULL COMMENT '币种类型',
  `CURRENCY_DESC` varchar(300) DEFAULT NULL COMMENT '币种描述',
  `CURR_BAL` varchar(300) DEFAULT NULL COMMENT '当前余额',
  `M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '月日均余额',
  `Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '季日均余额',
  `Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT '年日均余额',
  `INT_BAL` varchar(300) DEFAULT NULL COMMENT '利息收支',
  `FTP_BAL` varchar(300) DEFAULT NULL COMMENT 'FTP收支',
  `FEE_BAL` varchar(300) DEFAULT NULL COMMENT '中间业务收入',
  `INVEST_VAL` varchar(300) DEFAULT NULL COMMENT '投资收益与公允价值变动',
  `SALES_AND_ADDTAX` varchar(300) DEFAULT NULL COMMENT '增值税及附加',
  `FTP_REVENUE` varchar(300) DEFAULT NULL COMMENT 'FTP收入',
  `EXPECT_LOSS` varchar(300) DEFAULT NULL COMMENT '预计损失',
  `BUSINESS_EXPENSE` varchar(300) DEFAULT NULL COMMENT '营业费用',
  `SALES_OUT` varchar(300) DEFAULT NULL COMMENT '营业外收支',
  `PROFIT_BEFTAX` varchar(300) DEFAULT NULL COMMENT '税前利润',
  `INCOME_TAX` varchar(300) DEFAULT NULL COMMENT '所得税',
  `RWA_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA余额',
  `RWA_M_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA月日均',
  `RWA_Q_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA季日均',
  `RWA_Y_AVG_BAL` varchar(300) DEFAULT NULL COMMENT 'RWA年日均',
  `RISK_COST` varchar(300) DEFAULT NULL COMMENT '风险资本占用',
  `EVA_BAL` varchar(300) DEFAULT NULL COMMENT '经济增加值EVA',
  `FLAG` varchar(300) DEFAULT NULL COMMENT '标志',
  `REMARK` varchar(300) DEFAULT NULL COMMENT '备注',
  `DATA_SRC` varchar(300) DEFAULT NULL COMMENT '数据来源',
  `HIVE_SYS_TIME` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多租户系统日期',
  `STATIS_DT` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '统计日期',
  KEY `idx_BRANCH_NO` (`STATIS_DT`,`BRANCH_NO`),
  KEY `idx_EMP_ID` (`STATIS_DT`,`EMP_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='员工指标统计展示表';

-- ########## [07-03] stat-show-archive.sql —— STAT_SHOW_ARCHIVE 归档任务（去重 yiti/onepl） ##########
-- ============================================================================
-- 统计展示表「日增量归档 + 分批清理 + 主表瘦身」(v2)：注册 Quartz 任务 STAT_SHOW_ARCHIVE
--
-- 主表：XAN_M98_CUST_STAT_SHOW3（客户）、XAN_M98_EMP_STAT_SHOW3（员工）
-- 历史表：主表名 + _H1/_H2/_H3（_H2=每月1~10日、_H3=11~20日、_H1=21~月末）
--   ★ 6 张历史表的显式建表 DDL 见同目录 2026-07-03-stat-show-archive-hist-ddl.sql（先在两库各执行该 DDL）。
--
-- 任务 STAT_SHOW_ARCHIVE：quartz_job_class=performance.job.quartz.StatShowArchiveQuartzJob
--   cron '0 30 6-18 * * ?' = 每天 6:30、7:30 … 18:30（共 13 次）循环触发。
--   每次：把昨天所属旬(旬首~昨天)逐日增量搬进历史表(count比对幂等)；1/11/21 号按天分批清上一代旧旬；
--         1 号按天分批把上月非月末从主表删掉(保留月末)。全程幂等、逐日提交、@DisallowConcurrentExecution。
--
-- yiti(dev) + onepl(prod) 双库；均幂等：建表见 -hist-ddl.sql + SYS_JOB_CONF INSERT IGNORE。
-- 生效：重启应用或调 reschedule；JobService.syncJobsOnStartup(overwriteExistingJobs=true) 覆盖 QRTZ_ 触发器。
-- ============================================================================

-- ---------- 前置：先在 yiti 与 onepl 两库各执行 2026-07-03-stat-show-archive-hist-ddl.sql 建 6 张历史表 ----------

-- ---------- yiti：注册任务 ----------
INSERT IGNORE INTO SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'STAT_SHOW_ARCHIVE', '统计展示表日增量归档', '0 30 6-18 * * ?',
   'com.bank.branch.platform.performance.job.quartz.StatShowArchiveQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天6:30~18:30循环：昨天旬块逐日增量搬入_H1/H2/H3(幂等)；1/11/21按天分批清上一代旧旬；1号按天分批瘦身主表留月末', NOW());


-- ---------- 校验（可选） ----------
-- SHOW TABLES FROM yiti  LIKE 'XAN_M98_%_STAT_SHOW3\_H_';
-- SELECT job_key, cron_expr, status, quartz_job_class FROM SYS_JOB_CONF  WHERE job_key='STAT_SHOW_ARCHIVE';
-- SELECT job_key, cron_expr, status, quartz_job_class FROM SYS_JOB_CONF WHERE job_key='STAT_SHOW_ARCHIVE';

-- ########## [07-09] perf-run-task-add-trigger-type.sql —— PERF_RUN_TASK 加 trigger_type 列（任务监控页） ##########
-- 2026-07-09 PERF_RUN_TASK 加 trigger_type（触发来源）列 + 复合索引，供任务监控页按重算过滤
-- 幂等：INFORMATION_SCHEMA 预检
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_RUN_TASK' AND COLUMN_NAME = 'trigger_type');
SET @sql := IF(@col = 0,
  'ALTER TABLE `PERF_RUN_TASK` ADD COLUMN `trigger_type` varchar(20) DEFAULT NULL COMMENT ''触发来源：RECALC/SCHEDULED/MANUAL'' AFTER `task_type`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_RUN_TASK' AND INDEX_NAME = 'idx_type_trigger');
SET @sql2 := IF(@idx = 0,
  'ALTER TABLE `PERF_RUN_TASK` ADD KEY `idx_type_trigger` (`task_type`, `trigger_type`)',
  'SELECT 1');
PREPARE s2 FROM @sql2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ########## [07-09] perf-task-monitor-menu.sql —— 指标重算任务监控 菜单+资源+角色绑定 ##########
-- =====================================================================
-- 指标重算任务监控 菜单 —— 挂在「绩效与考核」组(M_GROUP_PERF)下，考核计算之后（rank 7）
-- 前端路由 /perf/task-monitor（TaskMonitor.vue；列表走 GET /api/perf/run-tasks，
--   资源 P_PERF_RT_LIST 已注册）
-- 角色绑定：复用 P_PERF_RT_LIST（任务日志列表接口）的角色集
--   —— 凡可调 run-tasks 列表 API 的角色均可见本菜单
-- 幂等：先删同名行再插入，可重复执行。yiti（dev）与 onepl（prod）双库执行。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_PERF_TASK_MONITOR';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_PERF_TASK_MONITOR';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_PERF_TASK_MONITOR', '/perf/task-monitor', 'MENU', '任务监控', NULL, 7,
   '1', '1', 'M_GROUP_PERF', '0', 'YITI', NOW(), 'seed', '绩效与考核-指标重算任务监控');

-- 绑定到与 P_PERF_RT_LIST（run-tasks 列表接口）相同的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRMPTM_', ROLE_ID), ROLE_ID, 'M_PERF_TASK_MONITOR', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_RT_LIST';

-- ########## [07-11] eval-assign-item-add-group-dept.sql —— EVAL_ASSIGN_ITEM 加 GROUP_DEPT ##########
-- 评价任务待处理明细新增「分组部门」列（汇总优先键，空串回退 be_eval_dept）
-- 目标库：yiti + onepl_test_bootstrap，手工执行（项目已废弃 Flyway）
-- 幂等：仅当列不存在时 ADD
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EVAL_ASSIGN_ITEM'
      AND COLUMN_NAME = 'GROUP_DEPT'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE EVAL_ASSIGN_ITEM ADD COLUMN GROUP_DEPT VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）'' AFTER BE_EVAL_DEPT',
    'SELECT ''GROUP_DEPT already exists, skip'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ########## [07-11] eval-reward-item.sql —— 建表 EVAL_REWARD_ITEM ##########
-- 奖励分配明细表 EVAL_REWARD_ITEM（2026-07-11）
-- 批次复用 EVAL_ASSIGN_BATCH（task_type=REWARD）；本表仅存奖励分配明细。
-- 目标库：yiti + onepl_test_bootstrap 手工执行。EVAL_IMP_REWARD 字典项已存在，无需新增。
CREATE TABLE IF NOT EXISTS EVAL_REWARD_ITEM (
    item_id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_id              BIGINT       NOT NULL COMMENT '所属批次(EVAL_ASSIGN_BATCH)',
    assign_user_id        VARCHAR(64)  NOT NULL COMMENT '分配人USER_ID(归一化后)',
    be_assigned_user_id   VARCHAR(64)  NOT NULL COMMENT '被分配人工号(原样快照,不校验)',
    be_assigned_user_name VARCHAR(128)          COMMENT '被分配人姓名(快照)',
    dept_name             VARCHAR(128) NOT NULL DEFAULT '' COMMENT '部门名称(分组键)',
    original_value        DECIMAL(18,4)         COMMENT '原始值(展示)',
    cash_value            DECIMAL(18,4)         COMMENT '兑现值(展示)',
    assign_total          DECIMAL(18,4) NOT NULL COMMENT '分配合计(组内一致,分配目标池)',
    assign_value          DECIMAL(18,4)         COMMENT '分配值(提交时填入)',
    submitted             TINYINT      NOT NULL DEFAULT 0 COMMENT '0未提交/1已提交',
    submit_time           DATETIME              COMMENT '提交时间',
    create_time           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (item_id),
    KEY idx_reward_assigner (assign_user_id, batch_id, dept_name, submitted),
    KEY idx_reward_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励分配明细';

-- ########## [07-11] eval-reward-resources.sql —— 奖励分配 PT_RESOURCE 资源+角色 ##########
-- 奖励分配（REWARD）端点 PT_RESOURCE 资源注册 + 角色绑定（2026-07-11）
-- 9 个端点：管理端 6（/api/admin/eval/reward/*）+ 用户端 3（/api/eval/reward-tasks*）
-- RESOURCE_ID 续编 PERF_EVAL_39..47；角色绑定复用 PERF_EVAL_38（submit-batch）的全量角色集。
-- 幂等：先删后插。目标库 yiti + onepl_test_bootstrap 手工执行。

-- 1) 清理旧行（幂等重跑）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('PERF_EVAL_39','PERF_EVAL_40','PERF_EVAL_41','PERF_EVAL_42','PERF_EVAL_43',
   'PERF_EVAL_44','PERF_EVAL_45','PERF_EVAL_46','PERF_EVAL_47');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('PERF_EVAL_39','PERF_EVAL_40','PERF_EVAL_41','PERF_EVAL_42','PERF_EVAL_43',
   'PERF_EVAL_44','PERF_EVAL_45','PERF_EVAL_46','PERF_EVAL_47');

-- 2) 注册资源（ISMENU=0 非菜单 API 资源，STATUS=0 启用，SYS_CODE=PLATFORM）
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('PERF_EVAL_39', '/api/admin/eval/reward/import-template',      'GET',  '下载奖励分配导入模板',   0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_40', '/api/admin/eval/reward/import',               'POST', '导入奖励分配',           0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_41', '/api/admin/eval/reward/batches',              'GET',  '管理端-奖励分配批次列表', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_42', '/api/admin/eval/reward/batches/*',            'GET',  '管理端-奖励分配批次详情', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_43', '/api/admin/eval/reward/batches/*/publish',    'POST', '管理端-发布奖励分配批次', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_44', '/api/admin/eval/reward/batches/*/export',     'GET',  '管理端-导出奖励分配明细', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_45', '/api/eval/reward-tasks',                      'GET',  '我的奖励分配待处理汇总', 0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_46', '/api/eval/reward-tasks/items',                'GET',  '奖励分配明细',           0, 0, 0, 0, 'PLATFORM', NOW(), NOW()),
  ('PERF_EVAL_47', '/api/eval/reward-tasks/submit-batch',         'POST', '提交奖励分配',           0, 0, 0, 0, 'PLATFORM', NOW(), NOW());

-- 3) 角色绑定：为每个新资源复用 PERF_EVAL_38 的全量角色集（R_ADMIN/R_BACK_TECH + 全部业务角色）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('RWD_', SUBSTRING(t.RESOURCE_ID, 11), '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'PERF_EVAL_39' AS RESOURCE_ID UNION ALL SELECT 'PERF_EVAL_40' UNION ALL
  SELECT 'PERF_EVAL_41' UNION ALL SELECT 'PERF_EVAL_42' UNION ALL SELECT 'PERF_EVAL_43' UNION ALL
  SELECT 'PERF_EVAL_44' UNION ALL SELECT 'PERF_EVAL_45' UNION ALL SELECT 'PERF_EVAL_46' UNION ALL
  SELECT 'PERF_EVAL_47'
) t
WHERE r.RESOURCE_ID = 'PERF_EVAL_38';

-- ########## [07-11] eval-assign-batch-expire-job.sql —— 批次过期 Quartz 任务 ##########
-- 导入批次过期关闭 Quartz Job 注册（2026-07-11）
-- 每 10 分钟扫描 EVAL_ASSIGN_BATCH：status=0(进行中) 且 deadline 已过 → status=1(已结束)。
-- 覆盖评价任务导入(EVAL) 与 奖励分配(REWARD) 两类导入批次。
-- governance JobService.syncJobsOnStartup 启动时按本行注册 JobDetail+Trigger（需应用重启生效）。
-- 幂等：先删后插。目标库 yiti（运行库）执行。

DELETE FROM SYS_JOB_CONF WHERE job_key = 'EVAL_ASSIGN_BATCH_EXPIRE';

INSERT INTO SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_by, created_time)
VALUES
  (REPLACE(UUID(), '-', ''),
   'EVAL_ASSIGN_BATCH_EXPIRE',
   '导入批次过期关闭',
   '0 */10 * * * ?',
   'com.bank.branch.platform.performance.eval.job.EvalAssignBatchExpireJob',
   'FIRE_ONCE_NOW',
   'ACTIVE',
   1,
   '每10分钟扫描 EVAL_ASSIGN_BATCH 过期进行中批次(评价导入+奖励分配)置已结束',
   'admin',
   NOW());

-- ########## [07-12] screen-dashboard-ddl.sql —— 大屏 4 张基表 DDL ##########
-- 经营管理大屏 4 张配置表（2026-07-12）
-- 目标库：yiti + onepl_test_bootstrap 手工执行（root/djdev）
-- 幂等：DROP 后重建（首发无存量数据；后续结构变更须另写 ALTER 脚本，禁止重跑本脚本）

DROP TABLE IF EXISTS `RPT_SCREEN_DATASOURCE`;
CREATE TABLE `RPT_SCREEN_DATASOURCE` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ds_code`         VARCHAR(64)  NOT NULL COMMENT '数据源编码（应用层保证 deleted=0 内唯一）',
  `ds_name`         VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `ds_type`         VARCHAR(20)  NOT NULL COMMENT '能力标签：TIMESERIES 时序/SINGLE 单值',
  `source_kind`     VARCHAR(20)  NOT NULL COMMENT '来源：WIDE_TABLE/KPI_RESULT/CUSTOM_SQL',
  `config_json`     TEXT         NOT NULL COMMENT '类型化配置 JSON（三形态见 spec §5）',
  `time_param_json` VARCHAR(500) DEFAULT NULL COMMENT '允许的预设周期 JSON 数组，如 ["LATEST","LAST_10D"]',
  `status`          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by`      VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_ds_code` (`ds_code`),
  KEY `idx_scr_ds_type` (`ds_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏数据源定义';

DROP TABLE IF EXISTS `RPT_SCREEN`;
CREATE TABLE `RPT_SCREEN` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_code`  VARCHAR(64)  NOT NULL COMMENT '大屏编码（应用层保证 deleted=0 内唯一）',
  `screen_name`  VARCHAR(100) NOT NULL COMMENT '大屏名称',
  `view_level`   VARCHAR(20)  NOT NULL COMMENT '视角：PROVINCE/BRANCH/PERSON',
  `theme_json`   VARCHAR(1000) DEFAULT NULL COMMENT '主题变量覆盖 JSON（一期留空）',
  `status`       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_by`   VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_code` (`screen_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏定义';

DROP TABLE IF EXISTS `RPT_SCREEN_BLOCK`;
CREATE TABLE `RPT_SCREEN_BLOCK` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`      BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `region`         VARCHAR(10) NOT NULL COMMENT '区域：LEFT/MAIN/RIGHT',
  `row_no`         INT         NOT NULL DEFAULT 1 COMMENT '区域内行号（从 1 起）',
  `col_no`         INT         NOT NULL DEFAULT 1 COMMENT '行内列号（从 1 起）',
  `width_pct`      INT         NOT NULL DEFAULT 100 COMMENT '行内宽度百分比 1~100',
  `height_pct`     INT         NOT NULL DEFAULT 100 COMMENT '区域内行高百分比 1~100（同行取首块值）',
  `component_type` VARCHAR(20) NOT NULL COMMENT 'METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS',
  `bind_json`      TEXT        NOT NULL COMMENT '数据绑定 JSON',
  `style_json`     TEXT        DEFAULT NULL COMMENT '样式 JSON',
  `drill_json`     TEXT        DEFAULT NULL COMMENT '钻取/跳转 JSON',
  `created_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_block_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏区块（布局+组件+绑定+钻取）';

DROP TABLE IF EXISTS `RPT_SCREEN_MAP_POINT`;
CREATE TABLE `RPT_SCREEN_MAP_POINT` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_code`           VARCHAR(32)   NOT NULL COMMENT '支行机构号',
  `org_name`           VARCHAR(100)  NOT NULL COMMENT '支行名称',
  `lng`                DECIMAL(10,6) NOT NULL COMMENT '经度',
  `lat`                DECIMAL(10,6) NOT NULL COMMENT '纬度',
  `target_screen_code` VARCHAR(64)   DEFAULT 'SCR_BRANCH' COMMENT '点击跳转目标屏编码',
  `status`             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scr_map_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏地图支行点位';

-- ########## [07-12] screen-dashboard-resources.sql —— 大屏 PT_RESOURCE 资源+角色 ##########
-- 大屏端点 PT_RESOURCE 资源注册 + 角色绑定（2026-07-12）
-- 12 API 端点 + 1 条 GET /map-points 独立补行（ResourceMatcher 按 URL+METHOD 匹配，不能与 CFG_LIST 共用）+ 2 管理页菜单，共 15 条资源。
-- 幂等：先删后插。目标库 yiti + onepl_test_bootstrap 手工执行。
--
-- Step 1 核实结论（对照 PT_RESOURCE 真实结构，2026-07-12 现场 SHOW COLUMNS + 报表分析组样例核实）：
--   1) PT_RESOURCE 存在 PARENT_RESOURCE_ID（varchar(60)，无 FK 约束）父子列，菜单树靠该列挂接，非隐式排序。
--   2) "报表分析" 分组：RESOURCE_ID='M_GROUP_REPORT'，RESOURCE_URL='#group/report'，RESOURCE_METHOD='MENU'，
--      SYS_CODE='YITI'，PARENT_RESOURCE_ID=NULL，MENU_RANK_NO=4，ISMENU=1，MENU_ENDFLAG=0，STATUS=0。
--   3) 组下叶子菜单样例（M_REPORT_DASHBOARD/M_REPORT_SQL 等 7 条）：PARENT_RESOURCE_ID='M_GROUP_REPORT'，
--      RESOURCE_METHOD 统一为 'MENU'（不是 GET）；SYS_CODE 统一 'YITI'；MENU_ENDFLAG='1'；STATUS=0；
--      MENU_RANK_NO 当前占用到 6（M_REPORT_AMAS）。
--   => 本脚本两条菜单行按此结果对齐：RESOURCE_METHOD='MENU'、SYS_CODE='YITI'、PARENT_RESOURCE_ID='M_GROUP_REPORT'、
--      MENU_RANK_NO 续编 7/8（原任务模板里的 95/96、GET、PLATFORM、无父级均已按现场结构修正）。
--   4) API 资源 SYS_CODE：现场 R_RPT_DASH_PRES / R_RPT_SQL_EXEC 等同源 R_RPT_* 系列 43/52 条使用 SYS_CODE='RPT'
--      （PT_RESOURCE.SYS_CODE 代表模块归属标识，仅用于唯一索引 uk_pt_resource_url_method_sys 与后台展示分组过滤，
--      不参与 ResourceMatcher/RbacAuthorizer 运行时鉴权判定，已读代码确认），本脚本 13 条 API 资源随之改用 'RPT'
--      （原任务模板的 'PLATFORM' 未按现场约定，予以修正）。PT_ROLE_RESOURCE.SYS_CODE 维持 'PLATFORM'（该表全局默认值，
--      与 R_RPT_DASH_PRES/R_RPT_SQL_EXEC 现有绑定行一致）。
--
-- 角色策略：查看类(VIEW/DATA) 复制 R_RPT_DASH_PRES 的角色集；管理类(含两条菜单) 复制 R_RPT_SQL_EXEC 的
-- 角色集（R_BACK_TECH）并补 R_ADMIN 全量兜底。后续可在 系统管理→资源管理 界面调整。

-- 1) 清理旧行（幂等重跑；含 R_RPT_SCR_MAP_LIST，原任务模板 DELETE 清单漏列已在此补齐）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_DS_LIST','R_RPT_SCR_DS_SAVE','R_RPT_SCR_DS_UPD','R_RPT_SCR_DS_DEL','R_RPT_SCR_DS_TRY',
   'R_RPT_SCR_CFG_LIST','R_RPT_SCR_CFG_GET','R_RPT_SCR_CFG_SAVE','R_RPT_SCR_CFG_DEL','R_RPT_SCR_MAP_SAVE',
   'R_RPT_SCR_MAP_LIST','R_RPT_SCR_VIEW','R_RPT_SCR_DATA','M_RPT_SCR_DS','M_RPT_SCR_DSN');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_DS_LIST','R_RPT_SCR_DS_SAVE','R_RPT_SCR_DS_UPD','R_RPT_SCR_DS_DEL','R_RPT_SCR_DS_TRY',
   'R_RPT_SCR_CFG_LIST','R_RPT_SCR_CFG_GET','R_RPT_SCR_CFG_SAVE','R_RPT_SCR_CFG_DEL','R_RPT_SCR_MAP_SAVE',
   'R_RPT_SCR_MAP_LIST','R_RPT_SCR_VIEW','R_RPT_SCR_DATA','M_RPT_SCR_DS','M_RPT_SCR_DSN');

-- 2) API 资源（ISMENU=0，STATUS=0 启用；RESOURCE_ID ≤20 字符；SYS_CODE='RPT' 对齐 R_RPT_* 现场约定）
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_DS_LIST','/api/screen/admin/datasources',        'GET',   '大屏-数据源列表', 0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_DS_SAVE','/api/screen/admin/datasources',        'POST',  '大屏-数据源新建', 0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_DS_UPD', '/api/screen/admin/datasources/*',      'PUT',   '大屏-数据源更新', 0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_DS_DEL', '/api/screen/admin/datasources/*',      'DELETE','大屏-数据源删除', 0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_DS_TRY', '/api/screen/admin/datasources/try-run','POST',  '大屏-数据源试跑(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CFG_LIST','/api/screen/admin/screens',           'GET',   '大屏-屏列表',     0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CFG_GET','/api/screen/admin/screens/*',          'GET',   '大屏-屏详情',     0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CFG_SAVE','/api/screen/admin/screens',           'POST',  '大屏-屏保存',     0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CFG_DEL','/api/screen/admin/screens/*',          'DELETE','大屏-屏删除',     0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_MAP_SAVE','/api/screen/admin/map-points',        'PUT',   '大屏-点位保存',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_VIEW',   '/api/screen/view/*',                   'GET',   '大屏-整屏读取',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_DATA',   '/api/screen/data',                     'POST',  '大屏-统一取数',   0,0,0,0,'RPT',NOW(),NOW());

-- GET /api/screen/admin/map-points 与 GET /api/screen/admin/screens（R_RPT_SCR_CFG_LIST）URL 不同，
-- ResourceMatcher 按 URL+METHOD 精确匹配，须单独补一条资源（不能复用 CFG_LIST）：
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_MAP_LIST','/api/screen/admin/map-points','GET','大屏-点位列表',0,0,0,0,'RPT',NOW(),NOW());

-- 3) 菜单行（Step 1 现场核实结果对齐：RESOURCE_METHOD='MENU'、SYS_CODE='YITI'、
--    PARENT_RESOURCE_ID='M_GROUP_REPORT' 挂在"报表分析"分组下，MENU_RANK_NO 续编该组现有最大值 6 之后）
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('M_RPT_SCR_DS',  '/screen-admin/datasources', 'MENU', '大屏数据源', 7, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), NOW()),
  ('M_RPT_SCR_DSN', '/screen-admin/designer',    'MENU', '大屏设计器', 8, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), NOW());

-- 4) 角色绑定
-- 4a) 查看类（VIEW/DATA）复制 R_RPT_DASH_PRES 的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRV_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_VIEW' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_DATA'
) t
WHERE r.RESOURCE_ID = 'R_RPT_DASH_PRES';

-- 4b) 管理类（含两条菜单）复制 R_RPT_SQL_EXEC 的角色集（R_BACK_TECH）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRA_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_DS_LIST' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_DS_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_DS_UPD'  UNION ALL SELECT 'R_RPT_SCR_DS_DEL'  UNION ALL SELECT 'R_RPT_SCR_DS_TRY' UNION ALL
  SELECT 'R_RPT_SCR_CFG_LIST' UNION ALL SELECT 'R_RPT_SCR_CFG_GET' UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CFG_DEL' UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE' UNION ALL SELECT 'R_RPT_SCR_MAP_LIST' UNION ALL
  SELECT 'M_RPT_SCR_DS' UNION ALL SELECT 'M_RPT_SCR_DSN'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SQL_EXEC';

-- 4c) 全量兜底：R_ADMIN 补齐尚未持有的新资源（全部 15 条）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRB_', t.RESOURCE_ID, '_R_ADMIN'), 'R_ADMIN', t.RESOURCE_ID, 'PLATFORM', NOW()
FROM (
  SELECT 'R_RPT_SCR_DS_LIST' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_DS_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_DS_UPD'  UNION ALL SELECT 'R_RPT_SCR_DS_DEL'  UNION ALL SELECT 'R_RPT_SCR_DS_TRY' UNION ALL
  SELECT 'R_RPT_SCR_CFG_LIST' UNION ALL SELECT 'R_RPT_SCR_CFG_GET' UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CFG_DEL' UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE' UNION ALL SELECT 'R_RPT_SCR_MAP_LIST' UNION ALL
  SELECT 'R_RPT_SCR_VIEW' UNION ALL SELECT 'R_RPT_SCR_DATA' UNION ALL
  SELECT 'M_RPT_SCR_DS' UNION ALL SELECT 'M_RPT_SCR_DSN'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM PT_ROLE_RESOURCE x WHERE x.ROLE_ID = 'R_ADMIN' AND x.RESOURCE_ID = t.RESOURCE_ID
);

-- ########## [07-12] screen-dashboard-seed.sql / seed-province-fix.sql / fix-kpi-cycletype.sql ##########
-- 【已略去 — 数据被 2026-07-13 canvas-seed 全量取代（last-wins）】
-- ########## [07-12] screen-canvas-ddl.sql —— RPT_SCREEN 画布双态列 + RPT_SCREEN_PUBLISH_LOG ##########
-- 大屏画布设计器 V2 —— RPT_SCREEN 双态字段 + 发布归档新表(2026-07-12)
-- 目标库:yiti + onepl_test_bootstrap 手工执行(root/djdev)。
-- 【严禁重跑既有基线 2026-07-12-screen-dashboard-ddl.sql】本脚本为增量 ALTER + CREATE。
-- 幂等策略:新表 CREATE TABLE IF NOT EXISTS(标准 MySQL 语法);ALTER ADD COLUMN 不支持列级 IF NOT
-- EXISTS(该写法为 MariaDB 扩展,MySQL 8.0.33 实测报 ERROR 1064 语法错误),故本脚本 ALTER 部分**不幂等**,
-- 严禁对同一库重复执行(重跑会因列已存在报 Duplicate column name 失败)。
--
-- ===== 正向变更 =====
ALTER TABLE `RPT_SCREEN`
  ADD COLUMN `canvas_style_json`     LONGTEXT     DEFAULT NULL COMMENT '画布全局样式JSON(设计基准/背景/适配策略/主题覆盖,schemaVersion)',
  ADD COLUMN `canvas_draft_json`     LONGTEXT     DEFAULT NULL COMMENT '编辑态组件树JSON(草稿,编辑器唯一读写对象)',
  ADD COLUMN `canvas_published_json` LONGTEXT     DEFAULT NULL COMMENT '发布态渲染包JSON=组件树+图表绑定快照,线上/预览只读它',
  ADD COLUMN `canvas_version`        INT          NOT NULL DEFAULT 0 COMMENT '真乐观锁:保存 WHERE canvas_version=? 并自增,冲突RPT-43012',
  ADD COLUMN `publish_status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发布/1已发布/2已发布但有未发布修改',
  ADD COLUMN `published_at`          DATETIME     DEFAULT NULL COMMENT '最近一次发布时间',
  ADD COLUMN `published_by`          VARCHAR(32)  DEFAULT NULL COMMENT '最近一次发布人工号';

CREATE TABLE IF NOT EXISTS `RPT_SCREEN_PUBLISH_LOG` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`     BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `snapshot_json` LONGTEXT    NOT NULL COMMENT '发布时的渲染包(CANVAS_PUBLISHED_JSON 全量)',
  `published_by`  VARCHAR(32) DEFAULT NULL COMMENT '发布人工号',
  `published_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_pub_log_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏发布归档(按屏滚动保留最近10次)';

-- ===== 回滚脚本(如需撤销本次变更,手工执行以下语句;生产慎用,会丢发布归档与草稿) =====
-- ALTER TABLE `RPT_SCREEN`
--   DROP COLUMN `canvas_style_json`, DROP COLUMN `canvas_draft_json`,
--   DROP COLUMN `canvas_published_json`, DROP COLUMN `canvas_version`,
--   DROP COLUMN `publish_status`, DROP COLUMN `published_at`, DROP COLUMN `published_by`;
-- DROP TABLE IF EXISTS `RPT_SCREEN_PUBLISH_LOG`;

-- ########## [07-12] screen-canvas-resources.sql —— 画布端点 PT_RESOURCE 资源+角色 ##########
-- 大屏画布设计器 V2 端点 PT_RESOURCE 注册 + 角色绑定（2026-07-12）
-- 6 条 API 资源（load/save/publish/rollback/discard/publish-logs；view 端点复用既有 R_RPT_SCR_VIEW，不重注）。
-- 发布/回滚为高危：R_RPT_SCR_CV_PUB / R_RPT_SCR_CV_RB 独立资源，仅授管理角色 + R_ADMIN。
-- 幂等：先删后插。目标库 yiti + onepl_test_bootstrap 手工执行。

-- 1) 清理旧行（幂等重跑）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_CV_GET','R_RPT_SCR_CV_SAVE','R_RPT_SCR_CV_PUB','R_RPT_SCR_CV_RB',
   'R_RPT_SCR_CV_DISC','R_RPT_SCR_CV_LOG');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_CV_GET','R_RPT_SCR_CV_SAVE','R_RPT_SCR_CV_PUB','R_RPT_SCR_CV_RB',
   'R_RPT_SCR_CV_DISC','R_RPT_SCR_CV_LOG');

-- 2) API 资源（ISMENU=0，STATUS=0 启用，SYS_CODE='RPT' 对齐 R_RPT_* 现场约定）
-- 注：GET /api/screen/admin/canvas/*（单层 *）与 GET /api/screen/admin/canvas/*/publish-logs（* 后跟字面量段）
--     在 Spring AntPathMatcher 下按路径段数区分，互不误匹配，无需额外收紧。
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_CV_GET', '/api/screen/admin/canvas/*',              'GET',  '大屏-画布加载',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_SAVE','/api/screen/admin/canvas/save',           'POST', '大屏-画布保存',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_PUB', '/api/screen/admin/canvas/publish',        'POST', '大屏-画布发布(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_RB',  '/api/screen/admin/canvas/rollback',       'POST', '大屏-画布回滚(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_DISC','/api/screen/admin/canvas/discard',        'POST', '大屏-放弃草稿',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_LOG', '/api/screen/admin/canvas/*/publish-logs', 'GET',  '大屏-发布归档',   0,0,0,0,'RPT',NOW(),NOW());

-- 3) 角色绑定
-- 3a) 管理类（load/save/discard/log）复制 R_RPT_SCR_CFG_SAVE 的角色集（与既有屏管理同档）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCV_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_CV_GET' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CV_DISC' UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SCR_CFG_SAVE';

-- 3b) 高危类（publish/rollback）复制 R_RPT_SQL_EXEC 的角色集（R_BACK_TECH，与试跑同档高危）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCVH_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_CV_PUB' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_RB'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SQL_EXEC';

-- 3c) 全量兜底：R_ADMIN 补齐 6 条尚未持有的（防 AUTH-40304）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCVB_', t.RESOURCE_ID, '_R_ADMIN'), 'R_ADMIN', t.RESOURCE_ID, 'PLATFORM', NOW()
FROM (
  SELECT 'R_RPT_SCR_CV_GET' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CV_PUB' UNION ALL SELECT 'R_RPT_SCR_CV_RB' UNION ALL
  SELECT 'R_RPT_SCR_CV_DISC' UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM PT_ROLE_RESOURCE x WHERE x.ROLE_ID = 'R_ADMIN' AND x.RESOURCE_ID = t.RESOURCE_ID
);

-- ########## [07-13] screen-canvas-seed.sql —— 三屏画布最终种子（取代 dashboard-seed 系列） ##########
-- 大屏画布 V2 三屏重配种子(2026-07-13,从 yiti 库重配发布后导出)
-- 覆盖:RPT_SCREEN(SCR_PROVINCE/SCR_BRANCH/SCR_PERSON 画布字段:canvas_style_json/
--   canvas_draft_json/canvas_published_json/canvas_version/publish_status/published_at/published_by)
--   + RPT_SCREEN_BLOCK(三屏全部取数配置行)+ RPT_SCREEN_DATASOURCE(三屏引用的 7 条数据源全集)
--   + RPT_SCREEN_MAP_POINT(3 条,PROVINCE 屏地图点位)+ RPT_SCREEN_PUBLISH_LOG(三屏发布归档)。
-- 前提(fresh 库按顺序执行,勿跳步/勿乱序):
--   1) 2026-07-12-screen-dashboard-ddl.sql —— 建 4 张基表(RPT_SCREEN/RPT_SCREEN_BLOCK/
--      RPT_SCREEN_DATASOURCE/RPT_SCREEN_MAP_POINT)。该脚本含 DROP TABLE,仅用于从 0 建库,
--      【严禁在已有数据的库上重复执行】(会清空既有数据,具破坏性)。
--   2) 2026-07-12-screen-canvas-ddl.sql —— 画布双态增量列(RPT_SCREEN 增量 ALTER 字段)+
--      CREATE TABLE RPT_SCREEN_PUBLISH_LOG(发布归档表)。
--   3) 【跳过 2026-07-12-screen-dashboard-seed*.sql 系列(含 seed.sql 与 seed-province-fix.sql)
--      ——严禁在本种子之前执行】:它们对同一批 id(9101-9103 屏 / 9001-9007 数据源 / 1-10 号
--      block)做旧版行/块布局种子插入,与本种子的画布态数据是同一批主键的两个不同版本;若先
--      跑了 dashboard-seed 系列再跑本种子会主键冲突失败,本种子数据已完整取代其内容,无需
--      再执行 dashboard-seed 系列。
--   4) 本种子文件 —— 直接导入即可(承接步骤 1)/2)建好的空表,首次 fresh 部署无需 REPLACE)。
-- 重配方式:全程经画布管理端 API(POST /api/screen/admin/canvas/save → publish),未直接 UPDATE
--   RPT_SCREEN,已走服务端乐观锁校验/审计链路;三屏均实测 GET /api/screen/view/{code} 验证
--   state=published、组件数与 bindSnapshots 一致、PROVINCE mapPoints=3。详见
--   .superpowers/sdd/canvas-task-11-report.md。
-- 注意:RPT_SCREEN/RPT_SCREEN_BLOCK/RPT_SCREEN_DATASOURCE 均为 INSERT(非 REPLACE),按上述前提
--   链首次导入的 fresh 库(无同 id 行)可直接导入;若目标库已有同 id 行(如重复执行本脚本,或
--   误跑过 dashboard-seed 系列),需先手工清理对应行或将 INSERT 改 REPLACE INTO 再执行,避免
--   主键冲突失败。
-- MySQL dump 10.13  Distrib 8.0.33, for Linux (x86_64)
--
-- Host: localhost    Database: yiti
-- ------------------------------------------------------
-- Server version	8.0.33

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `RPT_SCREEN`
--
-- WHERE:  screen_code IN ('SCR_PROVINCE','SCR_BRANCH','SCR_PERSON')

LOCK TABLES `RPT_SCREEN` WRITE;
/*!40000 ALTER TABLE `RPT_SCREEN` DISABLE KEYS */;
INSERT INTO `RPT_SCREEN` (`id`, `screen_code`, `screen_name`, `view_level`, `theme_json`, `status`, `created_by`, `created_time`, `updated_time`, `deleted`, `canvas_style_json`, `canvas_draft_json`, `canvas_published_json`, `canvas_version`, `publish_status`, `published_at`, `published_by`) VALUES (9102,'SCR_BRANCH','支行经营详情','BRANCH',NULL,'ACTIVE','SEED','2026-07-12 15:45:09','2026-07-13 00:32:48',0,'{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null}','{\"schemaVersion\":1,\"components\":[{\"id\":\"w-9102a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":5,\"style\":{\"top\":96,\"left\":24,\"width\":1872,\"height\":264},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9002,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"本支行核心指标\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":60}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_10D\\\",\\\"LAST_1M\\\",\\\"LAST_6M_EOM\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9102a2\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":6,\"style\":{\"top\":384,\"left\":24,\"width\":1872,\"height\":384},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9002,\\\"period\\\":\\\"LAST_6M_EOM\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"核心指标趋势(近6个月末)\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9102a3\",\"component\":\"ChartWidget\",\"innerType\":\"PIE_SHARE\",\"blockId\":7,\"style\":{\"top\":792,\"left\":24,\"width\":1872,\"height\":264},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9004,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"org_name\\\",\\\"valueCol\\\":\\\"指标值\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"全省份额占比\\\",\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}]}','{\"schemaVersion\":1,\"canvasStyle\":{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null},\"components\":[{\"id\":\"w-9102a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":5,\"style\":{\"top\":96,\"left\":24,\"width\":1872,\"height\":264},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9002,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"本支行核心指标\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":60}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_10D\\\",\\\"LAST_1M\\\",\\\"LAST_6M_EOM\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9102a2\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":6,\"style\":{\"top\":384,\"left\":24,\"width\":1872,\"height\":384},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9002,\\\"period\\\":\\\"LAST_6M_EOM\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"核心指标趋势(近6个月末)\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9102a3\",\"component\":\"ChartWidget\",\"innerType\":\"PIE_SHARE\",\"blockId\":7,\"style\":{\"top\":792,\"left\":24,\"width\":1872,\"height\":264},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9004,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"org_name\\\",\\\"valueCol\\\":\\\"指标值\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"全省份额占比\\\",\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}],\"bindSnapshots\":{\"5\":{\"bind\":{\"dsId\":9002,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"本支行核心指标\",\"decimals\":2,\"refreshSec\":60},\"drill\":{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\",\"LAST_1M\",\"LAST_6M_EOM\"]}},\"6\":{\"bind\":{\"dsId\":9002,\"period\":\"LAST_6M_EOM\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]},\"componentType\":\"LINE_TREND\",\"styleCfg\":{\"title\":\"核心指标趋势(近6个月末)\",\"decimals\":2,\"refreshSec\":300},\"drill\":{}},\"7\":{\"bind\":{\"dsId\":9004,\"period\":\"LATEST\",\"nameCol\":\"org_name\",\"valueCol\":\"指标值\"},\"componentType\":\"PIE_SHARE\",\"styleCfg\":{\"title\":\"全省份额占比\",\"refreshSec\":300},\"drill\":{}}}}',1,1,'2026-07-13 00:32:48','admin'),(9103,'SCR_PERSON','个人业绩详情','PERSON',NULL,'ACTIVE','SEED','2026-07-12 15:45:09','2026-07-13 00:32:48',0,'{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null}','{\"schemaVersion\":1,\"components\":[{\"id\":\"w-9103a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":8,\"style\":{\"top\":96,\"left\":24,\"width\":912,\"height\":288},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9003,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"KPI总分\\\",\\\"label\\\":\\\"KPI总分\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"最新KPI\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_6M_EOM\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9103a2\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":9,\"style\":{\"top\":96,\"left\":984,\"width\":912,\"height\":288},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9001,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额较上月-员工\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-员工\\\"},{\\\"col\\\":\\\"一般性存款季日均余额-员工\\\",\\\"label\\\":\\\"一般性存款季日均余额-员工\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"个人核心指标\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_10D\\\",\\\"LAST_1M\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9103a3\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":10,\"style\":{\"top\":408,\"left\":24,\"width\":1872,\"height\":648},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9003,\\\"period\\\":\\\"LAST_6M_EOM\\\",\\\"items\\\":[{\\\"col\\\":\\\"KPI总分\\\",\\\"label\\\":\\\"KPI总分\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"KPI 历史趋势\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}]}','{\"schemaVersion\":1,\"canvasStyle\":{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null},\"components\":[{\"id\":\"w-9103a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":8,\"style\":{\"top\":96,\"left\":24,\"width\":912,\"height\":288},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9003,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"KPI总分\\\",\\\"label\\\":\\\"KPI总分\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"最新KPI\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_6M_EOM\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9103a2\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":9,\"style\":{\"top\":96,\"left\":984,\"width\":912,\"height\":288},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9001,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额较上月-员工\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-员工\\\"},{\\\"col\\\":\\\"一般性存款季日均余额-员工\\\",\\\"label\\\":\\\"一般性存款季日均余额-员工\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"个人核心指标\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_10D\\\",\\\"LAST_1M\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9103a3\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":10,\"style\":{\"top\":408,\"left\":24,\"width\":1872,\"height\":648},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9003,\\\"period\\\":\\\"LAST_6M_EOM\\\",\\\"items\\\":[{\\\"col\\\":\\\"KPI总分\\\",\\\"label\\\":\\\"KPI总分\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"KPI 历史趋势\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}],\"bindSnapshots\":{\"8\":{\"bind\":{\"dsId\":9003,\"period\":\"LATEST\",\"items\":[{\"col\":\"KPI总分\",\"label\":\"KPI总分\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"最新KPI\",\"decimals\":2,\"refreshSec\":300},\"drill\":{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_6M_EOM\"]}},\"9\":{\"bind\":{\"dsId\":9001,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额较上月-员工\",\"label\":\"一般性存款月均余额较上月-员工\"},{\"col\":\"一般性存款季日均余额-员工\",\"label\":\"一般性存款季日均余额-员工\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"个人核心指标\",\"decimals\":2,\"refreshSec\":300},\"drill\":{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\",\"LAST_1M\"]}},\"10\":{\"bind\":{\"dsId\":9003,\"period\":\"LAST_6M_EOM\",\"items\":[{\"col\":\"KPI总分\",\"label\":\"KPI总分\"}]},\"componentType\":\"LINE_TREND\",\"styleCfg\":{\"title\":\"KPI 历史趋势\",\"decimals\":2,\"refreshSec\":300},\"drill\":{}}}}',1,1,'2026-07-13 00:32:48','admin'),(9101,'SCR_PROVINCE','省分行经营总览','PROVINCE',NULL,'ACTIVE','SEED','2026-07-12 15:45:09','2026-07-13 00:46:38',0,'{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null}','{\"schemaVersion\":1,\"components\":[{\"id\":\"w-9101a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":1,\"style\":{\"top\":96,\"left\":24,\"width\":576,\"height\":336},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9006,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"全省核心指标\\\",\\\"unit\\\":\\\"\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":60}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a2\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":2,\"style\":{\"top\":456,\"left\":24,\"width\":576,\"height\":520},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9007,\\\"period\\\":\\\"LAST_1M\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"一般性存款月均余额-机构趋势(近1月)\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101m\",\"component\":\"MapCenter\",\"innerType\":null,\"blockId\":null,\"style\":{\"top\":96,\"left\":640,\"width\":640,\"height\":880},\"propValue\":{},\"bindJson\":null,\"styleJson\":null,\"drillJson\":null,\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a3\",\"component\":\"ChartWidget\",\"innerType\":\"RANK_LIST\",\"blockId\":3,\"style\":{\"top\":96,\"left\":1320,\"width\":576,\"height\":520},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9004,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"org_name\\\",\\\"valueCol\\\":\\\"指标值\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"支行排名\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"jump\\\":{\\\"targetScreenCode\\\":\\\"SCR_BRANCH\\\",\\\"params\\\":{\\\"orgCode\\\":\\\"$col:org_code\\\"}}}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a4\",\"component\":\"ChartWidget\",\"innerType\":\"FLOW_STATUS\",\"blockId\":4,\"style\":{\"top\":640,\"left\":1320,\"width\":576,\"height\":336},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9005,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"名称\\\",\\\"valueCol\\\":\\\"数量\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"流程概览\\\",\\\"refreshSec\\\":120}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}]}','{\"schemaVersion\":1,\"canvasStyle\":{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null},\"components\":[{\"id\":\"w-9101a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":1,\"style\":{\"top\":96,\"left\":24,\"width\":576,\"height\":336},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9006,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"全省核心指标\\\",\\\"unit\\\":\\\"\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":60}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a2\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":2,\"style\":{\"top\":456,\"left\":24,\"width\":576,\"height\":520},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9007,\\\"period\\\":\\\"LAST_1M\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"一般性存款月均余额-机构趋势(近1月)\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101m\",\"component\":\"MapCenter\",\"innerType\":null,\"blockId\":null,\"style\":{\"top\":96,\"left\":640,\"width\":640,\"height\":880},\"propValue\":{},\"bindJson\":null,\"styleJson\":null,\"drillJson\":null,\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a3\",\"component\":\"ChartWidget\",\"innerType\":\"RANK_LIST\",\"blockId\":3,\"style\":{\"top\":96,\"left\":1320,\"width\":576,\"height\":520},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9004,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"org_name\\\",\\\"valueCol\\\":\\\"指标值\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"支行排名\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"jump\\\":{\\\"targetScreenCode\\\":\\\"SCR_BRANCH\\\",\\\"params\\\":{\\\"orgCode\\\":\\\"$col:org_code\\\"}}}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a4\",\"component\":\"ChartWidget\",\"innerType\":\"FLOW_STATUS\",\"blockId\":4,\"style\":{\"top\":640,\"left\":1320,\"width\":576,\"height\":336},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9005,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"名称\\\",\\\"valueCol\\\":\\\"数量\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"流程概览\\\",\\\"refreshSec\\\":120}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}],\"bindSnapshots\":{\"1\":{\"bind\":{\"dsId\":9006,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"全省核心指标\",\"unit\":\"\",\"decimals\":2,\"refreshSec\":60},\"drill\":{}},\"2\":{\"bind\":{\"dsId\":9007,\"period\":\"LAST_1M\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"}]},\"componentType\":\"LINE_TREND\",\"styleCfg\":{\"title\":\"一般性存款月均余额-机构趋势(近1月)\",\"decimals\":2,\"refreshSec\":300},\"drill\":{}},\"3\":{\"bind\":{\"dsId\":9004,\"period\":\"LATEST\",\"nameCol\":\"org_name\",\"valueCol\":\"指标值\"},\"componentType\":\"RANK_LIST\",\"styleCfg\":{\"title\":\"支行排名\",\"decimals\":2,\"refreshSec\":300},\"drill\":{\"jump\":{\"targetScreenCode\":\"SCR_BRANCH\",\"params\":{\"orgCode\":\"$col:org_code\"}}}},\"4\":{\"bind\":{\"dsId\":9005,\"period\":\"LATEST\",\"nameCol\":\"名称\",\"valueCol\":\"数量\"},\"componentType\":\"FLOW_STATUS\",\"styleCfg\":{\"title\":\"流程概览\",\"refreshSec\":120},\"drill\":{}}}}',1,1,'2026-07-13 00:46:38','admin');
/*!40000 ALTER TABLE `RPT_SCREEN` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-13  0:47:08
-- MySQL dump 10.13  Distrib 8.0.33, for Linux (x86_64)
--
-- Host: localhost    Database: yiti
-- ------------------------------------------------------
-- Server version	8.0.33

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `RPT_SCREEN_BLOCK`
--
-- WHERE:  screen_id IN (9102,9103,9101)

LOCK TABLES `RPT_SCREEN_BLOCK` WRITE;
/*!40000 ALTER TABLE `RPT_SCREEN_BLOCK` DISABLE KEYS */;
INSERT INTO `RPT_SCREEN_BLOCK` (`id`, `screen_id`, `region`, `row_no`, `col_no`, `width_pct`, `height_pct`, `component_type`, `bind_json`, `style_json`, `drill_json`, `created_time`, `updated_time`) VALUES (1,9101,'LEFT',1,1,100,40,'METRIC_CARD','{\"dsId\":9006,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]}','{\"title\":\"全省核心指标\",\"unit\":\"\",\"decimals\":2,\"refreshSec\":60}','{}','2026-07-12 15:45:09','2026-07-12 16:19:08'),(2,9101,'LEFT',2,1,100,60,'LINE_TREND','{\"dsId\":9007,\"period\":\"LAST_1M\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"}]}','{\"title\":\"一般性存款月均余额-机构趋势(近1月)\",\"decimals\":2,\"refreshSec\":300}','{}','2026-07-12 15:45:09','2026-07-12 16:19:08'),(3,9101,'RIGHT',1,1,100,60,'RANK_LIST','{\"dsId\":9004,\"period\":\"LATEST\",\"nameCol\":\"org_name\",\"valueCol\":\"指标值\"}','{\"title\":\"支行排名\",\"decimals\":2,\"refreshSec\":300}','{\"jump\":{\"targetScreenCode\":\"SCR_BRANCH\",\"params\":{\"orgCode\":\"$col:org_code\"}}}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(4,9101,'RIGHT',2,1,100,40,'FLOW_STATUS','{\"dsId\":9005,\"period\":\"LATEST\",\"nameCol\":\"名称\",\"valueCol\":\"数量\"}','{\"title\":\"流程概览\",\"refreshSec\":120}','{}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(5,9102,'MAIN',1,1,100,30,'METRIC_CARD','{\"dsId\":9002,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]}','{\"title\":\"本支行核心指标\",\"decimals\":2,\"refreshSec\":60}','{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\",\"LAST_1M\",\"LAST_6M_EOM\"]}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(6,9102,'MAIN',2,1,100,40,'LINE_TREND','{\"dsId\":9002,\"period\":\"LAST_6M_EOM\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]}','{\"title\":\"核心指标趋势(近6个月末)\",\"decimals\":2,\"refreshSec\":300}','{}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(7,9102,'MAIN',3,1,100,30,'PIE_SHARE','{\"dsId\":9004,\"period\":\"LATEST\",\"nameCol\":\"org_name\",\"valueCol\":\"指标值\"}','{\"title\":\"全省份额占比\",\"refreshSec\":300}','{}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(8,9103,'MAIN',1,1,50,30,'METRIC_CARD','{\"dsId\":9003,\"period\":\"LATEST\",\"items\":[{\"col\":\"KPI总分\",\"label\":\"KPI总分\"}]}','{\"title\":\"最新KPI\",\"decimals\":2,\"refreshSec\":300}','{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_6M_EOM\"]}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(9,9103,'MAIN',1,2,50,30,'METRIC_CARD','{\"dsId\":9001,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额较上月-员工\",\"label\":\"一般性存款月均余额较上月-员工\"},{\"col\":\"一般性存款季日均余额-员工\",\"label\":\"一般性存款季日均余额-员工\"}]}','{\"title\":\"个人核心指标\",\"decimals\":2,\"refreshSec\":300}','{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\",\"LAST_1M\"]}','2026-07-12 15:45:09','2026-07-12 15:45:09'),(10,9103,'MAIN',2,1,100,70,'LINE_TREND','{\"dsId\":9003,\"period\":\"LAST_6M_EOM\",\"items\":[{\"col\":\"KPI总分\",\"label\":\"KPI总分\"}]}','{\"title\":\"KPI 历史趋势\",\"decimals\":2,\"refreshSec\":300}','{}','2026-07-12 15:45:09','2026-07-12 15:45:09');
/*!40000 ALTER TABLE `RPT_SCREEN_BLOCK` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-13  0:47:08
-- MySQL dump 10.13  Distrib 8.0.33, for Linux (x86_64)
--
-- Host: localhost    Database: yiti
-- ------------------------------------------------------
-- Server version	8.0.33

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `RPT_SCREEN_DATASOURCE`
--
-- WHERE:  id IN (9001,9002,9003,9004,9005,9006,9007)

LOCK TABLES `RPT_SCREEN_DATASOURCE` WRITE;
/*!40000 ALTER TABLE `RPT_SCREEN_DATASOURCE` DISABLE KEYS */;
INSERT INTO `RPT_SCREEN_DATASOURCE` (`id`, `ds_code`, `ds_name`, `ds_type`, `source_kind`, `config_json`, `time_param_json`, `status`, `remark`, `created_by`, `created_time`, `updated_time`, `deleted`) VALUES (9001,'SCRDS_SEED01','员工核心指标(宽表)','TIMESERIES','WIDE_TABLE','{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\",\"metrics\":[{\"metricCode\":\"M_0002\",\"metricName\":\"一般性存款月均余额较上月-员工\",\"slot\":51},{\"metricCode\":\"M_0003\",\"metricName\":\"一般性存款季日均余额-员工\",\"slot\":11}]}','[\"LATEST\",\"LAST_10D\",\"LAST_1M\",\"LAST_6M_EOM\"]','ACTIVE','种子','SEED','2026-07-12 15:45:09','2026-07-12 15:45:09',0),(9002,'SCRDS_SEED02','机构核心指标(宽表)','TIMESERIES','WIDE_TABLE','{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\",\"subjectParam\":\"orgCode\",\"metrics\":[{\"metricCode\":\"M_0265\",\"metricName\":\"一般性存款月均余额-机构\",\"slot\":12},{\"metricCode\":\"M_0266\",\"metricName\":\"一般性存款月均余额较上月-机构\",\"slot\":51}]}','[\"LATEST\",\"LAST_10D\",\"LAST_1M\",\"LAST_6M_EOM\"]','ACTIVE','种子','SEED','2026-07-12 15:45:09','2026-07-12 15:45:09',0),(9003,'SCRDS_SEED03','个人KPI(月度)','TIMESERIES','KPI_RESULT','{\"cycleType\":\"YEARLY\"}','[\"LATEST\",\"LAST_6M_EOM\"]','ACTIVE','种子','SEED','2026-07-12 15:45:09','2026-07-12 17:56:54',0),(9004,'SCRDS_SEED04','全省机构排名(存款)','SINGLE','CUSTOM_SQL','{\"sql\":\"SELECT r.org_code, o.org_name, r.val_12 AS 指标值, RANK() OVER (ORDER BY r.val_12 DESC) AS 排名 FROM ORG_INDEX_RESULT r JOIN EXT_ORG_INFO o ON o.org_code = r.org_code WHERE r.data_date = (SELECT MAX(data_date) FROM ORG_INDEX_RESULT) ORDER BY r.val_12 DESC\",\"dateCol\":null}',NULL,'ACTIVE','种子','SEED','2026-07-12 15:45:09','2026-07-12 15:45:09',0),(9005,'SCRDS_SEED05','在途流程概览','SINGLE','CUSTOM_SQL','{\"sql\":\"SELECT \'在途任务\' AS 名称, COUNT(*) AS 数量 FROM ACT_RU_TASK\",\"dateCol\":null}',NULL,'ACTIVE','种子','SEED','2026-07-12 15:45:09','2026-07-12 15:45:09',0),(9006,'SCRDS_SEED06','全省核心指标(聚合单值)','SINGLE','CUSTOM_SQL','{\"sql\":\"SELECT SUM(val_12) AS `一般性存款月均余额-机构`, SUM(val_51) AS `一般性存款月均余额较上月-机构` FROM ORG_INDEX_RESULT WHERE data_date = (SELECT MAX(data_date) FROM ORG_INDEX_RESULT)\",\"dateCol\":null}',NULL,'ACTIVE','终审 Important-1 修复：省屏全省聚合单值源','SEED','2026-07-12 16:19:23','2026-07-12 16:19:23',0),(9007,'SCRDS_SEED07','全省核心指标趋势(聚合时序)','TIMESERIES','CUSTOM_SQL','{\"sql\":\"SELECT data_date, SUM(val_12) AS `一般性存款月均余额-机构`, SUM(val_51) AS `一般性存款月均余额较上月-机构` FROM ORG_INDEX_RESULT WHERE data_date BETWEEN #{dateFrom} AND #{dateTo} GROUP BY data_date ORDER BY data_date\",\"dateCol\":\"data_date\"}','[\"LATEST\",\"LAST_10D\",\"LAST_1M\"]','ACTIVE','终审 Important-1 修复：省屏全省聚合时序源','SEED','2026-07-12 16:19:23','2026-07-12 16:19:23',0);
/*!40000 ALTER TABLE `RPT_SCREEN_DATASOURCE` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-13  0:47:08
-- MySQL dump 10.13  Distrib 8.0.33, for Linux (x86_64)
--
-- Host: localhost    Database: yiti
-- ------------------------------------------------------
-- Server version	8.0.33

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `RPT_SCREEN_MAP_POINT`
--

LOCK TABLES `RPT_SCREEN_MAP_POINT` WRITE;
/*!40000 ALTER TABLE `RPT_SCREEN_MAP_POINT` DISABLE KEYS */;
INSERT INTO `RPT_SCREEN_MAP_POINT` (`id`, `org_code`, `org_name`, `lng`, `lat`, `target_screen_code`, `status`, `created_time`, `updated_time`) VALUES (1,'105','延兴门西路支行',108.948024,34.263161,'SCR_BRANCH','ACTIVE','2026-07-12 15:45:09','2026-07-12 15:45:09'),(2,'128','宝鸡分行',107.237743,34.361979,'SCR_BRANCH','ACTIVE','2026-07-12 15:45:09','2026-07-12 15:45:09'),(3,'191','渭南分行',109.502882,34.499381,'SCR_BRANCH','ACTIVE','2026-07-12 15:45:09','2026-07-12 15:45:09');
/*!40000 ALTER TABLE `RPT_SCREEN_MAP_POINT` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-13  0:47:08
-- MySQL dump 10.13  Distrib 8.0.33, for Linux (x86_64)
--
-- Host: localhost    Database: yiti
-- ------------------------------------------------------
-- Server version	8.0.33

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `RPT_SCREEN_PUBLISH_LOG`
--
-- WHERE:  screen_id IN (9102,9103,9101)

LOCK TABLES `RPT_SCREEN_PUBLISH_LOG` WRITE;
/*!40000 ALTER TABLE `RPT_SCREEN_PUBLISH_LOG` DISABLE KEYS */;
INSERT INTO `RPT_SCREEN_PUBLISH_LOG` (`id`, `screen_id`, `snapshot_json`, `published_by`, `published_at`) VALUES (3,9101,'{\"schemaVersion\":1,\"canvasStyle\":{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null},\"components\":[{\"id\":\"w-9101a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":1,\"style\":{\"top\":96,\"left\":24,\"width\":576,\"height\":336},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9006,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"全省核心指标\\\",\\\"unit\\\":\\\"\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":60}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a2\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":2,\"style\":{\"top\":456,\"left\":24,\"width\":576,\"height\":520},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9007,\\\"period\\\":\\\"LAST_1M\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"一般性存款月均余额-机构趋势(近1月)\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101m\",\"component\":\"MapCenter\",\"innerType\":null,\"blockId\":null,\"style\":{\"top\":96,\"left\":640,\"width\":640,\"height\":880},\"propValue\":{},\"bindJson\":null,\"styleJson\":null,\"drillJson\":null,\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a3\",\"component\":\"ChartWidget\",\"innerType\":\"RANK_LIST\",\"blockId\":3,\"style\":{\"top\":96,\"left\":1320,\"width\":576,\"height\":520},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9004,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"org_name\\\",\\\"valueCol\\\":\\\"指标值\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"支行排名\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"jump\\\":{\\\"targetScreenCode\\\":\\\"SCR_BRANCH\\\",\\\"params\\\":{\\\"orgCode\\\":\\\"$col:org_code\\\"}}}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9101a4\",\"component\":\"ChartWidget\",\"innerType\":\"FLOW_STATUS\",\"blockId\":4,\"style\":{\"top\":640,\"left\":1320,\"width\":576,\"height\":336},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9005,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"名称\\\",\\\"valueCol\\\":\\\"数量\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"流程概览\\\",\\\"refreshSec\\\":120}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}],\"bindSnapshots\":{\"1\":{\"bind\":{\"dsId\":9006,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"全省核心指标\",\"unit\":\"\",\"decimals\":2,\"refreshSec\":60},\"drill\":{}},\"2\":{\"bind\":{\"dsId\":9007,\"period\":\"LAST_1M\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"}]},\"componentType\":\"LINE_TREND\",\"styleCfg\":{\"title\":\"一般性存款月均余额-机构趋势(近1月)\",\"decimals\":2,\"refreshSec\":300},\"drill\":{}},\"3\":{\"bind\":{\"dsId\":9004,\"period\":\"LATEST\",\"nameCol\":\"org_name\",\"valueCol\":\"指标值\"},\"componentType\":\"RANK_LIST\",\"styleCfg\":{\"title\":\"支行排名\",\"decimals\":2,\"refreshSec\":300},\"drill\":{\"jump\":{\"targetScreenCode\":\"SCR_BRANCH\",\"params\":{\"orgCode\":\"$col:org_code\"}}}},\"4\":{\"bind\":{\"dsId\":9005,\"period\":\"LATEST\",\"nameCol\":\"名称\",\"valueCol\":\"数量\"},\"componentType\":\"FLOW_STATUS\",\"styleCfg\":{\"title\":\"流程概览\",\"refreshSec\":120},\"drill\":{}}}}','admin','2026-07-13 00:46:39'),(1,9102,'{\"schemaVersion\":1,\"canvasStyle\":{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null},\"components\":[{\"id\":\"w-9102a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":5,\"style\":{\"top\":96,\"left\":24,\"width\":1872,\"height\":264},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9002,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"本支行核心指标\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":60}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_10D\\\",\\\"LAST_1M\\\",\\\"LAST_6M_EOM\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9102a2\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":6,\"style\":{\"top\":384,\"left\":24,\"width\":1872,\"height\":384},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9002,\\\"period\\\":\\\"LAST_6M_EOM\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额-机构\\\",\\\"label\\\":\\\"一般性存款月均余额-机构\\\"},{\\\"col\\\":\\\"一般性存款月均余额较上月-机构\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-机构\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"核心指标趋势(近6个月末)\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9102a3\",\"component\":\"ChartWidget\",\"innerType\":\"PIE_SHARE\",\"blockId\":7,\"style\":{\"top\":792,\"left\":24,\"width\":1872,\"height\":264},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9004,\\\"period\\\":\\\"LATEST\\\",\\\"nameCol\\\":\\\"org_name\\\",\\\"valueCol\\\":\\\"指标值\\\"}\",\"styleJson\":\"{\\\"title\\\":\\\"全省份额占比\\\",\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}],\"bindSnapshots\":{\"5\":{\"bind\":{\"dsId\":9002,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"本支行核心指标\",\"decimals\":2,\"refreshSec\":60},\"drill\":{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\",\"LAST_1M\",\"LAST_6M_EOM\"]}},\"6\":{\"bind\":{\"dsId\":9002,\"period\":\"LAST_6M_EOM\",\"items\":[{\"col\":\"一般性存款月均余额-机构\",\"label\":\"一般性存款月均余额-机构\"},{\"col\":\"一般性存款月均余额较上月-机构\",\"label\":\"一般性存款月均余额较上月-机构\"}]},\"componentType\":\"LINE_TREND\",\"styleCfg\":{\"title\":\"核心指标趋势(近6个月末)\",\"decimals\":2,\"refreshSec\":300},\"drill\":{}},\"7\":{\"bind\":{\"dsId\":9004,\"period\":\"LATEST\",\"nameCol\":\"org_name\",\"valueCol\":\"指标值\"},\"componentType\":\"PIE_SHARE\",\"styleCfg\":{\"title\":\"全省份额占比\",\"refreshSec\":300},\"drill\":{}}}}','admin','2026-07-13 00:32:49'),(2,9103,'{\"schemaVersion\":1,\"canvasStyle\":{\"schemaVersion\":1,\"designWidth\":1920,\"designHeight\":1080,\"background\":\"#03081c\",\"adaptor\":\"keepProportion\",\"themeOverride\":null},\"components\":[{\"id\":\"w-9103a1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":8,\"style\":{\"top\":96,\"left\":24,\"width\":912,\"height\":288},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9003,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"KPI总分\\\",\\\"label\\\":\\\"KPI总分\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"最新KPI\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_6M_EOM\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9103a2\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":9,\"style\":{\"top\":96,\"left\":984,\"width\":912,\"height\":288},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9001,\\\"period\\\":\\\"LATEST\\\",\\\"items\\\":[{\\\"col\\\":\\\"一般性存款月均余额较上月-员工\\\",\\\"label\\\":\\\"一般性存款月均余额较上月-员工\\\"},{\\\"col\\\":\\\"一般性存款季日均余额-员工\\\",\\\"label\\\":\\\"一般性存款季日均余额-员工\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"个人核心指标\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{\\\"drillEnabled\\\":true,\\\"drillPeriods\\\":[\\\"LAST_10D\\\",\\\"LAST_1M\\\"]}\",\"isLock\":false,\"isShow\":true},{\"id\":\"w-9103a3\",\"component\":\"ChartWidget\",\"innerType\":\"LINE_TREND\",\"blockId\":10,\"style\":{\"top\":408,\"left\":24,\"width\":1872,\"height\":648},\"propValue\":{},\"bindJson\":\"{\\\"dsId\\\":9003,\\\"period\\\":\\\"LAST_6M_EOM\\\",\\\"items\\\":[{\\\"col\\\":\\\"KPI总分\\\",\\\"label\\\":\\\"KPI总分\\\"}]}\",\"styleJson\":\"{\\\"title\\\":\\\"KPI 历史趋势\\\",\\\"decimals\\\":2,\\\"refreshSec\\\":300}\",\"drillJson\":\"{}\",\"isLock\":false,\"isShow\":true}],\"bindSnapshots\":{\"8\":{\"bind\":{\"dsId\":9003,\"period\":\"LATEST\",\"items\":[{\"col\":\"KPI总分\",\"label\":\"KPI总分\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"最新KPI\",\"decimals\":2,\"refreshSec\":300},\"drill\":{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_6M_EOM\"]}},\"9\":{\"bind\":{\"dsId\":9001,\"period\":\"LATEST\",\"items\":[{\"col\":\"一般性存款月均余额较上月-员工\",\"label\":\"一般性存款月均余额较上月-员工\"},{\"col\":\"一般性存款季日均余额-员工\",\"label\":\"一般性存款季日均余额-员工\"}]},\"componentType\":\"METRIC_CARD\",\"styleCfg\":{\"title\":\"个人核心指标\",\"decimals\":2,\"refreshSec\":300},\"drill\":{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\",\"LAST_1M\"]}},\"10\":{\"bind\":{\"dsId\":9003,\"period\":\"LAST_6M_EOM\",\"items\":[{\"col\":\"KPI总分\",\"label\":\"KPI总分\"}]},\"componentType\":\"LINE_TREND\",\"styleCfg\":{\"title\":\"KPI 历史趋势\",\"decimals\":2,\"refreshSec\":300},\"drill\":{}}}}','admin','2026-07-13 00:32:49');
/*!40000 ALTER TABLE `RPT_SCREEN_PUBLISH_LOG` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-13  0:47:08

-- ########## [07-13] workflow-monitor Task6 补录 —— RES_WF_MONITOR_LIST 资源行 + 秘书/行长角色 + 数据范围 ##########
-- =====================================================================
-- 审批流监控「基座」补录（workflow-monitor-transfer Task 6，76cbaacd）
-- 说明：本合并脚本原缺 Task 6 的三块基座数据 —— Task 6 内容仅落在 docs/schema/seed-v1.sql /
--   data.sql 的「语义 ID」基线里，从未沉淀为数字 ID 迁移脚本，导致下方 Task 7/12 只补了
--   角色-资源绑定(PT_ROLE_RESOURCE)，却少了：
--     1) 监控列表资源行 RES_WF_MONITOR_LIST（PT_RESOURCE，菜单/转交/RBAC 前置门禁都依赖它）
--     2) 秘书角色 / 行长角色信息（PT_ROLE：231=中场支持部门秘书、2=分行行长）
--     3) 秘书/行长的监控数据范围（PT_ROLE_BIZ_SCOPE：秘书=ORG 本机构、行长=ALL 全行）
--   此处按 LIVE DB(yiti) 数字 ID（231/2，见 2026-06-10-role-id-realign-to-intranet.sql）补齐，
--   与下方 Task 7/12 的数字 ID 口径一致（seed-v1/data.sql 的语义 ID 版本面向 fresh 基线，另存）。
-- 幂等：角色用 INSERT IGNORE（LIVE 已存在则跳过，绝不破坏既有角色/绑定）；资源行、数据范围先删同名再插入。
-- =====================================================================

-- 1) 秘书角色信息 / 行长角色信息（数字 ID；LIVE 已存在则 IGNORE，仅供 fresh 基线补建）
INSERT IGNORE INTO PT_ROLE
  (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
  ('231', 'SUPPORT_SE', '中场支持部门秘书', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', '中场支持部门秘书岗（审批流监控-本机构范围）'),
  ('2',   'BRANCH_PRE', '分行行长',         0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', '分行最高管理者（审批流监控-全行范围）');

-- 2) 审批流监控列表资源行 RES_WF_MONITOR_LIST（GET /api/workflow/monitor/processes）
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID = 'RES_WF_MONITOR_LIST';
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_WF_MONITOR_LIST', '/api/workflow/monitor/processes', 'GET', '审批流监控列表', NULL, 0,
   0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-monitor-2026-07-13', 'v1 workflow-monitor-transfer Task6');

-- 3) 分行行长与秘书角色的监控数据范围（PT_ROLE_BIZ_SCOPE：秘书 231=ORG 本机构、行长 2=ALL 全行）
DELETE FROM PT_ROLE_BIZ_SCOPE WHERE BIZ_TYPE = 'WORKFLOW_MONITOR' AND ROLE_ID IN ('231', '2');
INSERT INTO PT_ROLE_BIZ_SCOPE
  (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
  ('BSWFM231', '231', 'WORKFLOW_MONITOR', 'ORG', 0, NOW(), 'wf-monitor-2026-07-13', NOW(), 'wf-monitor-2026-07-13', '秘书岗-本机构监控'),
  ('BSWFM2',   '2',   'WORKFLOW_MONITOR', 'ALL', 0, NOW(), 'wf-monitor-2026-07-13', NOW(), 'wf-monitor-2026-07-13', '分行行长-全行监控');

-- ── 验证 ────────────────────────────────────────────────────
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD FROM PT_RESOURCE WHERE RESOURCE_ID = 'RES_WF_MONITOR_LIST';
SELECT ROLE_ID, ROLE_CODE, ROLE_CHNAME FROM PT_ROLE WHERE ROLE_ID IN ('231', '2');
SELECT ROLE_ID, BIZ_TYPE, DATA_SCOPE FROM PT_ROLE_BIZ_SCOPE WHERE BIZ_TYPE = 'WORKFLOW_MONITOR' AND ROLE_ID IN ('231', '2');

-- ########## [07-13] workflow-monitor-menu.sql —— 审批流监控菜单+资源 ##########
-- =====================================================================
-- 审批流监控 菜单 —— 挂在「系统设置」组(M_GROUP_SYSTEM)下，排在审批流程之后（rank 14）
-- 前端路由 /system/workflow-monitor（WorkflowMonitor.vue；列表走
--   GET /api/workflow/monitor/processes，资源 RES_WF_MONITOR_LIST 已由 Task 6 登记）
-- 角色绑定：秘书岗(中场支持部门秘书)/分行行长 —— LIVE DB(yiti) 当前 ROLE_ID 为
--   231 / 2（见 2026-06-10-role-id-realign-to-intranet.sql 的号段对齐，PT_ROLE
--   已从语义 ID R_SUPPORT_SEC/R_PRESIDENT 迁移为数字 ID，本脚本直接用数字 ID
--   落库；docs/schema/seed-v1.sql 的 PT_ROLE 仍是语义 ID，另在该文件追加对应
--   语义 ID 版本，两者不冲突，各自面向不同基线）
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_SYS_WF_MONITOR', '/system/workflow-monitor', 'GET', '审批流监控', NULL, 14,
   1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', '系统设置-审批流监控（workflow-monitor-transfer Task 7）');

-- 绑定角色：231=中场支持部门秘书（本机构范围），2=分行行长（全行范围）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
  ('RRMWFM_231', '231', 'M_SYS_WF_MONITOR', 'YITI', NOW()),
  ('RRMWFM_2',   '2',   'M_SYS_WF_MONITOR', 'YITI', NOW());

-- ── 验证 ────────────────────────────────────────────────────
SELECT RESOURCE_ID, RESOURCE_URL, MENU_NAME, ISMENU, PARENT_RESOURCE_ID, MENU_RANK_NO
FROM PT_RESOURCE WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';

SELECT ID, ROLE_ID, RESOURCE_ID FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_WF_MONITOR';

-- =====================================================================
-- 补漏：Task 6 的 76cbaacd 只登记了 RES_WF_MONITOR_LIST 资源行
-- （PT_RESOURCE）+ 秘书/行长数据范围（PT_ROLE_BIZ_SCOPE），漏了
-- RBAC 授权绑定（PT_ROLE_RESOURCE）——AuthorizationInterceptor Step 2
-- rbacAuthorizer.authorize() 是独立于 DataScopeContext 的前置门禁，
-- 未绑定时秘书/行长调用 GET /api/workflow/monitor/processes 一律 403
-- AUTH-40301，菜单能看见但列表打不开。这里一并补上（同 M_SYS_WF_MONITOR
-- 绑定的两个角色，Task 7 发现，随手修复）。
-- =====================================================================
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_MONITOR_LIST';
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
  ('RRWFML_231', '231', 'RES_WF_MONITOR_LIST', 'YITI', NOW()),
  ('RRWFML_2',   '2',   'RES_WF_MONITOR_LIST', 'YITI', NOW());

SELECT ID, ROLE_ID, RESOURCE_ID FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_MONITOR_LIST';

-- ########## [07-13] workflow-transfer-controller-resources.sql —— 转交端点 PT_RESOURCE ##########
-- =====================================================================
-- 任务转交待认领 六端点 PT_RESOURCE + 角色绑定（workflow-monitor-transfer Task 12）
-- TaskTransferController：
--   发起  POST /api/workflow/monitor/tasks/{taskId}/transfer  RES_WF_TRF_INIT
--   收件箱 GET  /api/workflow/transfers/inbox                  RES_WF_TRF_INBOX
--   认领  POST /api/workflow/transfers/{id}/accept             RES_WF_TRF_ACCEPT
--   拒绝  POST /api/workflow/transfers/{id}/decline            RES_WF_TRF_DECLINE
--   发件箱 GET  /api/workflow/transfers/outbox                  RES_WF_TRF_OUTBOX
--   撤回  POST /api/workflow/transfers/{id}/cancel             RES_WF_TRF_CANCEL
--
-- 角色绑定口径：
--   发起端点挂在监控域下，鉴权对齐 RES_WF_MONITOR_LIST/M_SYS_WF_MONITOR
--     （秘书岗=231，分行行长=2，见 2026-07-13-workflow-monitor-menu.sql）。
--   收件箱/认领/拒绝/发件箱/撤回 5 个端点的接收人可以是任意具备任务办理角色的人，不限
--     秘书岗/行长——绑定口径对齐 LIVE DB 当前 /api/workflow/tasks* 系列资源
--     （W_TASK_APPROVE 等）已绑定的角色集合（26 个角色：1,2,3,4,5,7,17,128,129,130,148,
--     149,208,229,230,231,232,233,234,235,236,237,238,239,240,241，经
--     `SELECT DISTINCT ROLE_ID FROM PT_ROLE_RESOURCE prr JOIN PT_RESOURCE pr ON ...
--      WHERE pr.RESOURCE_URL LIKE '/api/workflow/tasks%'` 实测确认），
--     任何当前能办理任务的角色都能看见/认领/拒绝转交。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 执行前已用 mysqldump 备份 PT_RESOURCE / PT_ROLE_RESOURCE 到
--   docs/superpowers/sql/backup/2026-07-13-yiti-pt-resource-before-transfer-controller.sql
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('RES_WF_TRF_INIT','RES_WF_TRF_INBOX','RES_WF_TRF_ACCEPT','RES_WF_TRF_DECLINE','RES_WF_TRF_OUTBOX','RES_WF_TRF_CANCEL');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('RES_WF_TRF_INIT','RES_WF_TRF_INBOX','RES_WF_TRF_ACCEPT','RES_WF_TRF_DECLINE','RES_WF_TRF_OUTBOX','RES_WF_TRF_CANCEL');

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_WF_TRF_INIT',    '/api/workflow/monitor/tasks/*/transfer', 'POST', '转交发起',   NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-13', 'v1 workflow-monitor-transfer Task12'),
  ('RES_WF_TRF_INBOX',   '/api/workflow/transfers/inbox',          'GET',  '转交收件箱', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-13', 'v1 workflow-monitor-transfer Task12'),
  ('RES_WF_TRF_ACCEPT',  '/api/workflow/transfers/*/accept',       'POST', '转交认领',   NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-13', 'v1 workflow-monitor-transfer Task12'),
  ('RES_WF_TRF_DECLINE', '/api/workflow/transfers/*/decline',      'POST', '转交拒绝',   NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-13', 'v1 workflow-monitor-transfer Task12'),
  ('RES_WF_TRF_OUTBOX',  '/api/workflow/transfers/outbox',         'GET',  '转交发件箱', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-13', 'v1 workflow-monitor-transfer Task12'),
  ('RES_WF_TRF_CANCEL',  '/api/workflow/transfers/*/cancel',       'POST', '转交撤回',   NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-13', 'v1 workflow-monitor-transfer Task12');

-- 发起端点：秘书岗(231)/分行行长(2)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('WFTRF_231_INIT','231','RES_WF_TRF_INIT','PLATFORM',NOW()),
  ('WFTRF_2_INIT','2','RES_WF_TRF_INIT','PLATFORM',NOW());

-- 收件箱/认领/拒绝/发件箱/撤回：26 个既有任务办理角色 x 5 端点 = 130 行
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('WFTRF_1_INB','1','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_1_ACC','1','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_1_DEC','1','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_1_OUT','1','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_1_CAN','1','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_2_INB','2','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_2_ACC','2','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_2_DEC','2','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_2_OUT','2','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_2_CAN','2','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_3_INB','3','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_3_ACC','3','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_3_DEC','3','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_3_OUT','3','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_3_CAN','3','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_4_INB','4','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_4_ACC','4','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_4_DEC','4','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_4_OUT','4','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_4_CAN','4','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_5_INB','5','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_5_ACC','5','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_5_DEC','5','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_5_OUT','5','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_5_CAN','5','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_7_INB','7','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_7_ACC','7','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_7_DEC','7','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_7_OUT','7','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_7_CAN','7','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_17_INB','17','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_17_ACC','17','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_17_DEC','17','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_17_OUT','17','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_17_CAN','17','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_128_INB','128','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_128_ACC','128','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_128_DEC','128','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_128_OUT','128','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_128_CAN','128','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_129_INB','129','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_129_ACC','129','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_129_DEC','129','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_129_OUT','129','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_129_CAN','129','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_130_INB','130','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_130_ACC','130','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_130_DEC','130','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_130_OUT','130','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_130_CAN','130','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_148_INB','148','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_148_ACC','148','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_148_DEC','148','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_148_OUT','148','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_148_CAN','148','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_149_INB','149','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_149_ACC','149','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_149_DEC','149','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_149_OUT','149','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_149_CAN','149','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_208_INB','208','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_208_ACC','208','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_208_DEC','208','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_208_OUT','208','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_208_CAN','208','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_229_INB','229','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_229_ACC','229','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_229_DEC','229','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_229_OUT','229','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_229_CAN','229','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_230_INB','230','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_230_ACC','230','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_230_DEC','230','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_230_OUT','230','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_230_CAN','230','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_231_INB','231','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_231_ACC','231','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_231_DEC','231','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_231_OUT','231','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_231_CAN','231','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_232_INB','232','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_232_ACC','232','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_232_DEC','232','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_232_OUT','232','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_232_CAN','232','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_233_INB','233','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_233_ACC','233','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_233_DEC','233','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_233_OUT','233','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_233_CAN','233','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_234_INB','234','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_234_ACC','234','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_234_DEC','234','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_234_OUT','234','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_234_CAN','234','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_235_INB','235','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_235_ACC','235','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_235_DEC','235','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_235_OUT','235','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_235_CAN','235','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_236_INB','236','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_236_ACC','236','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_236_DEC','236','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_236_OUT','236','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_236_CAN','236','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_237_INB','237','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_237_ACC','237','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_237_DEC','237','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_237_OUT','237','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_237_CAN','237','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_238_INB','238','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_238_ACC','238','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_238_DEC','238','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_238_OUT','238','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_238_CAN','238','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_239_INB','239','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_239_ACC','239','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_239_DEC','239','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_239_OUT','239','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_239_CAN','239','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_240_INB','240','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_240_ACC','240','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_240_DEC','240','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_240_OUT','240','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_240_CAN','240','RES_WF_TRF_CANCEL','PLATFORM',NOW()),
  ('WFTRF_241_INB','241','RES_WF_TRF_INBOX','PLATFORM',NOW()),
  ('WFTRF_241_ACC','241','RES_WF_TRF_ACCEPT','PLATFORM',NOW()),
  ('WFTRF_241_DEC','241','RES_WF_TRF_DECLINE','PLATFORM',NOW()),
  ('WFTRF_241_OUT','241','RES_WF_TRF_OUTBOX','PLATFORM',NOW()),
  ('WFTRF_241_CAN','241','RES_WF_TRF_CANCEL','PLATFORM',NOW());

-- ── 验证 ────────────────────────────────────────────────────
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_WF_TRF_%' ORDER BY RESOURCE_ID;
SELECT RESOURCE_ID, COUNT(*) AS role_count FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'RES_WF_TRF_%' GROUP BY RESOURCE_ID ORDER BY RESOURCE_ID;
