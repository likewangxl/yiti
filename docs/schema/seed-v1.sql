-- ============================================================================
-- seed-v1.sql — 公共种子数据（DEV/TEST only）
-- 版本：V1.0
-- 描述：字典 / 角色 / 资源 / 角色-资源绑定 / 角色-BizType-数据范围 种子数据
-- 日期：2026-03-25
-- 执行前提：create-table.sql + docs/schema/v1-additions.sql 已执行
-- 幂等策略：INSERT IGNORE（依赖主键/唯一约束防重复）
-- ============================================================================

SET NAMES utf8mb4;

-- =========================================================
-- 1) sys_dict 字典种子数据
-- =========================================================

-- ---------------------------------------------------------
-- 1.1 YES_NO（通用是否）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_YN_1', 'YES_NO', 'YES', '是', '1', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_YN_0', 'YES_NO', 'NO',  '否', '0', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.2 INDUSTRY（行业类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_IND_IT',    'INDUSTRY', 'IT',    '信息技术',       'IT',    1,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_MFG',   'INDUSTRY', 'MFG',   '制造业',         'MFG',   2,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_FIN',   'INDUSTRY', 'FIN',   '金融业',         'FIN',   3,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_RE',    'INDUSTRY', 'RE',    '房地产业',       'RE',    4,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_EDU',   'INDUSTRY', 'EDU',   '教育',           'EDU',   5,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_MED',   'INDUSTRY', 'MED',   '医疗卫生',       'MED',   6,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_RETAIL','INDUSTRY', 'RETAIL','批发和零售业',   'RETAIL',7,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_TRANS', 'INDUSTRY', 'TRANS', '交通运输业',     'TRANS', 8,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_ENERGY','INDUSTRY', 'ENERGY','能源',           'ENERGY',9,  'ACTIVE', 'V1 seed', 'seed'),
('D_IND_AGRI',  'INDUSTRY', 'AGRI',  '农林牧渔业',     'AGRI',  10, 'ACTIVE', 'V1 seed', 'seed'),
('D_IND_OTHER', 'INDUSTRY', 'OTHER', '其他',           'OTHER', 99, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.3 GROUP_TYPE（集团类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_GRP_1', 'GROUP_TYPE', 'GROUP',  '集团客户',   'GROUP',  1, 'ACTIVE', 'V1 seed', 'seed'),
('D_GRP_2', 'GROUP_TYPE', 'SINGLE', '非集团客户', 'SINGLE', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.4 CUSTOMER_TYPE（客户类型，影响线索审批流程路由）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_CT_1', 'CUSTOMER_TYPE', 'CORP',   '对公客户', 'CORP',   1, 'ACTIVE', 'V1 seed', 'seed'),
('D_CT_2', 'CUSTOMER_TYPE', 'RETAIL', '零售客户', 'RETAIL', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.5 ENTERPRISE_TYPE（企业类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_ET_1', 'ENTERPRISE_TYPE', 'SOE',     '国企',     'SOE',     1, 'ACTIVE', 'V1 seed', 'seed'),
('D_ET_2', 'ENTERPRISE_TYPE', 'PRIVATE', '民营',     'PRIVATE', 2, 'ACTIVE', 'V1 seed', 'seed'),
('D_ET_3', 'ENTERPRISE_TYPE', 'FOREIGN', '外资',     'FOREIGN', 3, 'ACTIVE', 'V1 seed', 'seed'),
('D_ET_4', 'ENTERPRISE_TYPE', 'JV',      '合资',     'JV',      4, 'ACTIVE', 'V1 seed', 'seed'),
('D_ET_5', 'ENTERPRISE_TYPE', 'COLLECT', '集体企业', 'COLLECT', 5, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.6 BIZ_KIND（业务种类）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_BK_DEP',  'BIZ_KIND', 'DEPOSIT',       '存款',       'DEPOSIT',       1, 'ACTIVE', 'V1 seed', 'seed'),
('D_BK_LOAN', 'BIZ_KIND', 'LOAN',          '贷款',       'LOAN',          2, 'ACTIVE', 'V1 seed', 'seed'),
('D_BK_MID',  'BIZ_KIND', 'INTERMEDIATE',  '中间业务',   'INTERMEDIATE',  3, 'ACTIVE', 'V1 seed', 'seed'),
('D_BK_CD',   'BIZ_KIND', 'NCD',           '大额存单',   'NCD',           4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.7 METRIC_DIM / PERF_BASE_DIM（指标维度）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_MD_EMP',  'PERF_BASE_DIM', 'EMP',  '人员', 'EMP',  1, 'ACTIVE', 'V1 seed', 'seed'),
('D_MD_ORG',  'PERF_BASE_DIM', 'ORG',  '机构', 'ORG',  2, 'ACTIVE', 'V1 seed', 'seed'),
('D_MD_CUST', 'PERF_BASE_DIM', 'CUST', '客户', 'CUST', 3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.8 PERF_CALC_FREQ（计算频率）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_CF_DAY',     'PERF_CALC_FREQ', 'DAY',     '日',   'DAY',     1, 'ACTIVE', 'V1 seed', 'seed'),
('D_CF_MONTH',   'PERF_CALC_FREQ', 'MONTH',   '月',   'MONTH',   2, 'ACTIVE', 'V1 seed', 'seed'),
('D_CF_QUARTER', 'PERF_CALC_FREQ', 'QUARTER', '季',   'QUARTER', 3, 'ACTIVE', 'V1 seed', 'seed'),
('D_CF_YEAR',    'PERF_CALC_FREQ', 'YEAR',    '年',   'YEAR',    4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.9 PERF_CALC_MODE（计算模式）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_CM_AUTO',   'PERF_CALC_MODE', 'AUTO',   '自动计算', 'AUTO',   1, 'ACTIVE', 'V1 seed', 'seed'),
('D_CM_MANUAL', 'PERF_CALC_MODE', 'MANUAL', '手工导入', 'MANUAL', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.10 PERF_CALC_LOGIC_TYPE（计算逻辑类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_CLT_SQL',     'PERF_CALC_LOGIC_TYPE', 'SQL',     'SQL查询',   'SQL',     1, 'ACTIVE', 'V1 seed', 'seed'),
('D_CLT_PROC',    'PERF_CALC_LOGIC_TYPE', 'PROC',    '存储过程',  'PROC',    2, 'ACTIVE', 'V1 seed', 'seed'),
('D_CLT_EXPR',    'PERF_CALC_LOGIC_TYPE', 'EXPR',    '表达式',    'EXPR',    3, 'ACTIVE', 'V1 seed', 'seed'),
('D_CLT_SUMMARY', 'PERF_CALC_LOGIC_TYPE', 'SUMMARY', '汇总',      'SUMMARY', 4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.11 PERF_METRIC_LEVEL（指标级别）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_ML_1', 'PERF_METRIC_LEVEL', '1', '一级基础', '1', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_ML_2', 'PERF_METRIC_LEVEL', '2', '二级派生', '2', 2, 'ACTIVE', 'V1 seed', 'seed'),
('D_ML_3', 'PERF_METRIC_LEVEL', '3', '三级复合', '3', 3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.12 PERF_KPI_CYCLE（KPI周期）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_KC_M', 'PERF_KPI_CYCLE', 'MONTHLY',   '月度', 'MONTHLY',   1, 'ACTIVE', 'V1 seed', 'seed'),
('D_KC_Q', 'PERF_KPI_CYCLE', 'QUARTERLY', '季度', 'QUARTERLY', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.13 PERF_TARGET_DIM（目标维度）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_TD_EMP', 'PERF_TARGET_DIM', 'EMP', '人员', 'EMP', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_TD_ORG', 'PERF_TARGET_DIM', 'ORG', '机构', 'ORG', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.14 PERF_TARGET_CYCLE（目标周期）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_TC_Y', 'PERF_TARGET_CYCLE', 'YEAR',    '年度', 'YEAR',    1, 'ACTIVE', 'V1 seed', 'seed'),
('D_TC_Q', 'PERF_TARGET_CYCLE', 'QUARTER', '季度', 'QUARTER', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.15 PERF_IMPORT_TYPE（导入类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_PIT_IDX', 'PERF_IMPORT_TYPE', 'INDEX_RESULT', '指标结果', 'INDEX_RESULT', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_PIT_KPI', 'PERF_IMPORT_TYPE', 'KPI_RESULT',   'KPI结果',  'KPI_RESULT',   2, 'ACTIVE', 'V1 seed', 'seed'),
('D_PIT_TGT', 'PERF_IMPORT_TYPE', 'TARGET',       '目标',     'TARGET',       3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.16 PERF_TASK_TYPE（任务类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_PTT_TRIAL',  'PERF_TASK_TYPE', 'METRIC_TRIAL', '指标试运行', 'METRIC_TRIAL', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_PTT_RUN',    'PERF_TASK_TYPE', 'METRIC_RUN',   '指标执行',   'METRIC_RUN',   2, 'ACTIVE', 'V1 seed', 'seed'),
('D_PTT_KPI',    'PERF_TASK_TYPE', 'KPI_RUN',      'KPI执行',    'KPI_RUN',      3, 'ACTIVE', 'V1 seed', 'seed'),
('D_PTT_RECALC', 'PERF_TASK_TYPE', 'RECALC',       '历史重算',   'RECALC',       4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.17 PERF_ALLOC_DIM（分配维度）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_PAD_RULE',    'PERF_ALLOC_DIM', 'RULE',    '按规则分配', 'RULE',    1, 'ACTIVE', 'V1 seed', 'seed'),
('D_PAD_ACCOUNT', 'PERF_ALLOC_DIM', 'ACCOUNT', '按台账分配', 'ACCOUNT', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.18 PERF_SUMMARY_RULE（汇总规则）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_PSR_SUM', 'PERF_SUMMARY_RULE', 'SUM', '求和', 'SUM', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_PSR_AVG', 'PERF_SUMMARY_RULE', 'AVG', '平均', 'AVG', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.19 PROJECT_TYPE（项目类型，业务申请中心）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_PJT_NEW',    'PROJECT_TYPE', 'NEW',       '新增项目', 'NEW',       1, 'ACTIVE', 'V1 seed', 'seed'),
('D_PJT_RENEW',  'PROJECT_TYPE', 'RENEWAL',   '续贷项目', 'RENEWAL',   2, 'ACTIVE', 'V1 seed', 'seed'),
('D_PJT_ADJUST', 'PROJECT_TYPE', 'ADJUST',    '调整项目', 'ADJUST',    3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.20 BIZ_TYPE（业务类型，业务申请中心）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_BT_WORKING_CAP', 'BIZ_TYPE', 'WORKING_CAPITAL', '流动资金贷款',   'WORKING_CAPITAL', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_BT_FIXED',       'BIZ_TYPE', 'FIXED_ASSET',     '固定资产贷款',   'FIXED_ASSET',     2, 'ACTIVE', 'V1 seed', 'seed'),
('D_BT_TRADE',       'BIZ_TYPE', 'TRADE_FINANCE',   '贸易融资',       'TRADE_FINANCE',   3, 'ACTIVE', 'V1 seed', 'seed'),
('D_BT_GUARANTEE',   'BIZ_TYPE', 'GUARANTEE',       '保函',           'GUARANTEE',       4, 'ACTIVE', 'V1 seed', 'seed'),
('D_BT_ACCEPTANCE',  'BIZ_TYPE', 'ACCEPTANCE',       '承兑汇票',       'ACCEPTANCE',      5, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.21 GUARANTEE_TYPE（担保方式）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_GT_CREDIT',   'GUARANTEE_TYPE', 'CREDIT',      '信用',     'CREDIT',      1, 'ACTIVE', 'V1 seed', 'seed'),
('D_GT_MORTGAGE',  'GUARANTEE_TYPE', 'MORTGAGE',    '抵押',     'MORTGAGE',    2, 'ACTIVE', 'V1 seed', 'seed'),
('D_GT_PLEDGE',    'GUARANTEE_TYPE', 'PLEDGE',      '质押',     'PLEDGE',      3, 'ACTIVE', 'V1 seed', 'seed'),
('D_GT_GUARANTEE', 'GUARANTEE_TYPE', 'GUARANTEE',   '保证',     'GUARANTEE',   4, 'ACTIVE', 'V1 seed', 'seed'),
('D_GT_MIXED',     'GUARANTEE_TYPE', 'MIXED',       '组合担保', 'MIXED',       5, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.22 POSITION（岗位）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_POS_CM',   'POSITION', 'CUST_MGR',      '客户经理',     'CUST_MGR',      1,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_BH',   'POSITION', 'BRANCH_HEAD',   '支行负责人',   'BRANCH_HEAD',   2,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_CORP', 'POSITION', 'CORP_STAFF',    '公司部员工',   'CORP_STAFF',    3,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_RTL',  'POSITION', 'RETAIL_STAFF',  '零售部员工',   'RETAIL_STAFF',  4,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_FIN',  'POSITION', 'FINANCE_STAFF', '资财部员工',   'FINANCE_STAFF', 5,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_TECH', 'POSITION', 'TECH_STAFF',    '科技部员工',   'TECH_STAFF',    6,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_SEC',  'POSITION', 'SECRETARY',     '部门秘书',     'SECRETARY',     7,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_CR',   'POSITION', 'CREDIT_REVIEW', '授信审查岗',   'CREDIT_REVIEW', 8,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_CA',   'POSITION', 'CREDIT_APPROVE','授信批复岗',   'CREDIT_APPROVE',9,  'ACTIVE', 'V1 seed', 'seed'),
('D_POS_PRES', 'POSITION', 'PRESIDENT',     '行长',         'PRESIDENT',     10, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.23 NOTIFY_TYPE（通知类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_NT_SYS', 'NOTIFY_TYPE', 'SYSTEM',   '系统通知', 'SYSTEM',   1, 'ACTIVE', 'V1 seed', 'seed'),
('D_NT_WF',  'NOTIFY_TYPE', 'WORKFLOW', '流程通知', 'WORKFLOW', 2, 'ACTIVE', 'V1 seed', 'seed'),
('D_NT_BIZ', 'NOTIFY_TYPE', 'BUSINESS', '业务通知', 'BUSINESS', 3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.24 JOB_STATUS（任务状态）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_JS_ACT', 'JOB_STATUS', 'ACTIVE', '活跃', 'ACTIVE', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_JS_PAU', 'JOB_STATUS', 'PAUSED', '暂停', 'PAUSED', 2, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.25 JOB_RUN_STATUS（执行状态）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_JRS_RUN',  'JOB_RUN_STATUS', 'RUNNING', '运行中', 'RUNNING', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_JRS_OK',   'JOB_RUN_STATUS', 'SUCCESS', '成功',   'SUCCESS', 2, 'ACTIVE', 'V1 seed', 'seed'),
('D_JRS_FAIL', 'JOB_RUN_STATUS', 'FAILED',  '失败',   'FAILED',  3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.26 CONFIG_VALUE_TYPE（配置值类型）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_CVT_STR',  'CONFIG_VALUE_TYPE', 'STRING', '字符串', 'STRING', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_CVT_JSON', 'CONFIG_VALUE_TYPE', 'JSON',   'JSON',   'JSON',   2, 'ACTIVE', 'V1 seed', 'seed'),
('D_CVT_NUM',  'CONFIG_VALUE_TYPE', 'NUMBER', '数值',   'NUMBER', 3, 'ACTIVE', 'V1 seed', 'seed'),
('D_CVT_BOOL', 'CONFIG_VALUE_TYPE', 'BOOL',   '布尔',   'BOOL',   4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.27 LEAD_SOURCE（线索来源）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_LS_SELF',   'LEAD_SOURCE', 'SELF_FOUND', '自行挖掘', 'SELF_FOUND', 1, 'ACTIVE', 'V1 seed', 'seed'),
('D_LS_ASSIGN', 'LEAD_SOURCE', 'ASSIGNED',   '上级分配', 'ASSIGNED',   2, 'ACTIVE', 'V1 seed', 'seed'),
('D_LS_IMPORT', 'LEAD_SOURCE', 'IMPORTED',   '批量导入', 'IMPORTED',   3, 'ACTIVE', 'V1 seed', 'seed'),
('D_LS_REFER',  'LEAD_SOURCE', 'REFERRAL',   '转介绍',   'REFERRAL',   4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.28 RISK_LEVEL（风险等级，审批表单用）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_RL_LOW',  'RISK_LEVEL', 'LOW',    '低风险', 'LOW',    1, 'ACTIVE', 'V1 seed', 'seed'),
('D_RL_MID',  'RISK_LEVEL', 'MEDIUM', '中风险', 'MEDIUM', 2, 'ACTIVE', 'V1 seed', 'seed'),
('D_RL_HIGH', 'RISK_LEVEL', 'HIGH',   '高风险', 'HIGH',   3, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.29 PRODUCT_CATEGORY（产品分类）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_PC_TRADE', 'PRODUCT_CATEGORY', 'TRADE_BANK',  '交易银行',   'TRADE_BANK',  1, 'ACTIVE', 'V1 seed', 'seed'),
('D_PC_FM',    'PRODUCT_CATEGORY', 'FIN_MARKET',  '金融市场',   'FIN_MARKET',  2, 'ACTIVE', 'V1 seed', 'seed'),
('D_PC_CORP',  'PRODUCT_CATEGORY', 'CORP_BANK',   '公司银行',   'CORP_BANK',   3, 'ACTIVE', 'V1 seed', 'seed'),
('D_PC_RTL',   'PRODUCT_CATEGORY', 'RETAIL_BANK', '零售银行',   'RETAIL_BANK', 4, 'ACTIVE', 'V1 seed', 'seed');

-- ---------------------------------------------------------
-- 1.30 DOC_CATEGORY（文档分类）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by)
VALUES
('D_DC_POLICY',  'DOC_CATEGORY', 'POLICY',    '制度文件',   'POLICY',    1, 'ACTIVE', 'V1 seed', 'seed'),
('D_DC_GUIDE',   'DOC_CATEGORY', 'GUIDE',     '操作指引',   'GUIDE',     2, 'ACTIVE', 'V1 seed', 'seed'),
('D_DC_TEMPLATE','DOC_CATEGORY', 'TEMPLATE',  '模板表单',   'TEMPLATE',  3, 'ACTIVE', 'V1 seed', 'seed'),
('D_DC_TRAIN',   'DOC_CATEGORY', 'TRAINING',  '培训材料',   'TRAINING',  4, 'ACTIVE', 'V1 seed', 'seed');


-- =========================================================
-- 2) PT_ROLE 角色种子数据（V1 全部12个角色）
-- =========================================================

INSERT IGNORE INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('R_ADMIN',            'SYS_ADMIN',        '系统管理员',         0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 超级管理员，运维与权限管理'),
('R_RM',               'CUST_MANAGER',     '客户经理',           0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 经营机构一线营销人员'),
('R_BRANCH_MGR',       'BRANCH_HEAD',      '经营机构负责人',     0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 支行/二级分行负责人'),
('R_CORP_DEPT',        'CORP_DEPT',        '公司部人员',         0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 分行公司业务管理部门'),
('R_RETAIL_DEPT',      'RETAIL_DEPT',      '零售部人员',         0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 分行零售业务管理部门'),
('R_BACK_FINANCE',     'BACK_FINANCE',     '中后台员工(资财)',   0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 财务会计部等后台支持'),
('R_BACK_TECH',        'BACK_TECH',        '中后台员工(科技)',   0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 信息技术部'),
('R_SUPPORT_SEC',      'SUPPORT_SECRETARY','中场支持部门秘书',   0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 中场支持部门秘书岗'),
('R_SUPPORT_STAFF',    'SUPPORT_STAFF',    '中场支持部门人员',   0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 中台部门员工'),
('R_CREDIT_REVIEWER',  'CREDIT_REVIEWER',  '授信审查人员',       0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 授信审查岗'),
('R_CREDIT_APPROVER',  'CREDIT_APPROVER',  '授信批复人员',       0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 授信批复岗'),
('R_PRESIDENT',        'BRANCH_PRESIDENT', '分行行长',           0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed - 分行最高管理者');


-- =========================================================
-- 3) PT_RESOURCE 接口资源种子数据
-- =========================================================

-- ---------------------------------------------------------
-- 3.1 auth-permission-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_AUTH_CURRENT',       '/api/auth/current-user',                  'GET',  '当前用户信息',      1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ORG_TREE',           '/api/org/tree',                           'GET',  '组织树',            1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ORG_SUBTREE',        '/api/org/subtree',                        'GET',  '组织子树',          1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_LIST',          '/api/sys/roles',                          'GET',  '角色列表',          1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_CREATE',        '/api/sys/roles',                          'POST', '角色创建',          1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_UPDATE',        '/api/sys/roles/*',                        'PUT',  '角色更新',          1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_RES_LIST',      '/api/sys/roles/*/resources',              'GET',  '角色资源查询',      1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_RES_REPLACE',   '/api/sys/roles/*/resources/replace',      'POST', '角色资源覆盖绑定',  1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_SCOPE_LIST',    '/api/sys/roles/*/biz-scopes',             'GET',  '角色BizScope查询',  1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ROLE_SCOPE_REPLACE', '/api/sys/roles/*/biz-scopes/replace',     'POST', '角色范围覆盖配置',  1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.2 portal-content-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_PORTAL_TODOS',       '/api/portal/dashboard/todos',              'GET',    '工作台待办',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PORTAL_NOTIFY',      '/api/portal/dashboard/notifications',      'GET',    '工作台通知',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PORTAL_NOTIFY_READ', '/api/portal/dashboard/notifications/*/read','PUT',   '通知已读',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PORTAL_CARDS',       '/api/portal/dashboard/cards',              'GET',    '工作台卡片',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_NAV_LIST',           '/api/portal/navs',                         'GET',    '导航列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_NAV_CREATE',         '/api/portal/navs',                         'POST',   '导航新增',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_NAV_UPDATE',         '/api/portal/navs/*',                       'PUT',    '导航编辑',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_NAV_DELETE',         '/api/portal/navs/*',                       'DELETE', '导航删除',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SHORTCUT_LIST',      '/api/portal/shortcuts',                    'GET',    '快捷入口列表',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SHORTCUT_CREATE',    '/api/portal/shortcuts',                    'POST',   '快捷入口新增',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SHORTCUT_UPDATE',    '/api/portal/shortcuts/*',                  'PUT',    '快捷入口编辑',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SHORTCUT_DELETE',    '/api/portal/shortcuts/*',                  'DELETE', '快捷入口删除',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SHORTCUT_REPLACE',   '/api/portal/shortcuts/replace',            'POST',   '快捷入口覆盖替换', 1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ADDRBOOK_LIST',      '/api/addrbook/employees',                  'GET',    '通讯录查询',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ADDRBOOK_SEARCH',    '/api/addrbook/employees/search',           'GET',    '员工搜索',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_ADDRBOOK_UPDATE',    '/api/addrbook/employees/*',                'PUT',    '通讯录维护',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PRODUCT_LIST',       '/api/products',                            'GET',    '产品列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PRODUCT_DETAIL',     '/api/products/*',                          'GET',    '产品详情',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PRODUCT_CREATE',     '/api/products',                            'POST',   '产品新增',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PRODUCT_UPDATE',     '/api/products/*',                          'PUT',    '产品编辑',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PRODUCT_DELETE',     '/api/products/*',                          'DELETE', '产品删除',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PRODUCT_EXPORT',     '/api/products/export',                     'GET',    '产品导出',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_DOC_LIST',           '/api/docs',                                'GET',    '文档列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_DOC_DETAIL',         '/api/docs/*',                              'GET',    '文档详情',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_DOC_UPLOAD',         '/api/docs',                                'POST',   '文档上传',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_DOC_UPDATE',         '/api/docs/*',                              'PUT',    '文档更新',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_DOC_DELETE',         '/api/docs/*',                              'DELETE', '文档删除',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_DOC_DOWNLOAD',       '/api/docs/*/download',                     'GET',    '文档下载',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_FILE_UPLOAD',        '/api/files',                               'POST',   '文件上传',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_FILE_DOWNLOAD',      '/api/files/*/download',                    'GET',    '文件下载',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.3 customer-marketing-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_TAG_LIST',           '/api/tags',                        'GET',  '标签列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_CREATE',         '/api/tags',                        'POST', '新增标签',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_UPDATE',         '/api/tags/*',                      'PUT',  '编辑标签',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_DETAIL',         '/api/tags/*',                      'GET',  '标签详情',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_ENABLE',         '/api/tags/*/enable',               'POST', '标签启用',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_DISABLE',        '/api/tags/*/disable',              'POST', '标签禁用',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_CUST_REPLACE',   '/api/tags/*/customers/replace',    'POST', '标签覆盖替换客户', 1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_IMPORT_PREVIEW', '/api/tags/*/import/preview',       'POST', '标签导入预检',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_IMPORT_CONFIRM', '/api/tags/*/import/confirm',       'POST', '标签导入执行',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TAG_EXPORT',         '/api/tags/*/export',               'POST', '标签导出',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_LIST',          '/api/leads',                       'GET',  '线索列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_CREATE',        '/api/leads',                       'POST', '线索新增草稿',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_UPDATE',        '/api/leads/*',                     'PUT',  '线索编辑草稿',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_DETAIL',        '/api/leads/*',                     'GET',  '线索详情',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_SUBMIT',        '/api/leads/*/submit',              'POST', '线索提交审批',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_IMPORT',        '/api/leads/import',                'POST', '线索批量导入',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_EXPORT',        '/api/leads/export',                'POST', '线索导出',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LEAD_IMP_BATCH',     '/api/leads/import-batches/*',      'GET',  '导入批次详情',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUST_POOL',          '/api/customers/pool',              'GET',  '待认领客户池',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CLAIM_CREATE',       '/api/claims',                      'POST', '认领客户',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CLAIM_CANCEL',       '/api/claims/*/cancel',             'POST', '取消认领',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CLAIM_RESTART',      '/api/claims/*/restart-touch',      'POST', '重新发起触达',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUSTOMER_LIST',      '/api/customers',                   'GET',  '客户列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUSTOMER_DETAIL',    '/api/customers/*',                 'GET',  '客户详情',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUSTOMER_HISTORY',   '/api/customers/*/history',         'GET',  '客户历史只读',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUSTOMER_EDIT',      '/api/customers/*/edit',            'POST', '客户编辑',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUSTOMER_TRANSFER',  '/api/customers/*/transfer',        'POST', '客户转交维护人',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_CUSTOMER_DEL_APPLY', '/api/customers/*/delete-apply',    'POST', '客户删除申请',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_LIST',         '/api/touch-tasks',                 'GET',  '触达任务列表',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_DETAIL',       '/api/touch-tasks/*',               'GET',  '触达任务详情',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_LOG',          '/api/touch-tasks/*/logs',          'POST', '追加触达日志',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_SUCCESS',      '/api/touch-tasks/*/success',       'POST', '触达成功',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_CANCEL',       '/api/touch-tasks/*/cancel',        'POST', '触达取消',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_RPT_LIST',     '/api/touch-report/list',           'GET',  '触达报表查询',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_TOUCH_RPT_EXPORT',   '/api/touch-report/export',         'POST', '触达报表导出',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.4 business-application-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_LOAN_LIST',          '/api/loans',                              'GET',  '资产投放列表',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LOAN_DETAIL',        '/api/loans/*',                            'GET',  '资产投放详情',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LOAN_CREATE',        '/api/loans',                              'POST', '资产投放草稿新增',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LOAN_UPDATE',        '/api/loans/*',                            'PUT',  '资产投放草稿编辑',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LOAN_SUBMIT',        '/api/loans/*/submit',                     'POST', '资产投放提交',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_LOAN_EXPORT',        '/api/loans/export',                       'POST', '资产投放导出',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPPORT_LIST',       '/api/supports',                           'GET',  '中场支持列表',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPPORT_DETAIL',     '/api/supports/*',                         'GET',  '中场支持详情',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPPORT_CREATE',     '/api/supports',                           'POST', '中场支持草稿新增',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPPORT_UPDATE',     '/api/supports/*',                         'PUT',  '中场支持草稿编辑',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPPORT_SUBMIT',     '/api/supports/groups/*/submit',           'POST', '中场支持提交',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPPORT_EXPORT',     '/api/supports/export',                    'POST', '中场支持导出',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPDEPT_LIST',       '/api/support-dept/requests',              'GET',  '承接列表',           1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPDEPT_DETAIL',     '/api/support-dept/requests/*',            'GET',  '承接详情',           1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SUPDEPT_DISPATCH',   '/api/support-dept/requests/*/dispatch',   'POST', '承接派单',           1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.5 workflow-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_WF_TODO',        '/api/workflow/tasks/todo',                    'GET',  '工作流待办列表',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_DONE',        '/api/workflow/tasks/done',                    'GET',  '工作流已办列表',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_DETAIL',      '/api/workflow/tasks/*',                       'GET',  '工作流任务详情',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_CLAIM',       '/api/workflow/tasks/*/claim',                 'POST', '工作流任务Claim',  1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_APPROVE',     '/api/workflow/tasks/*/approve',               'POST', '工作流任务同意',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_REJECT',      '/api/workflow/tasks/*/reject',                'POST', '工作流任务驳回',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_TRANSFER',    '/api/workflow/tasks/*/transfer',              'POST', '工作流任务转交',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_HISTORY',     '/api/workflow/process/history',               'GET',  '流程历史',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_IS_PART',     '/api/workflow/participants/is-participant',   'GET',  '参与者判定',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_CFG_CAND',    '/api/workflow/config/node-candidates',        'POST', '流程节点候选配置', 1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_CFG_FORM',    '/api/workflow/config/node-form',              'POST', '流程节点表单配置', 1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_WF_CFG_SLA',     '/api/workflow/config/timeout-rule',           'POST', '流程超时规则配置', 1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.6 performance-engine-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_PERF_METRIC_LIST',   '/api/perf/metrics',                      'GET',  '指标列表',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_DETAIL', '/api/perf/metrics/*',                    'GET',  '指标详情',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_CREATE', '/api/perf/metrics',                      'POST', '指标新增',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_UPDATE', '/api/perf/metrics/*',                    'PUT',  '指标编辑',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_ENABLE', '/api/perf/metrics/*/enable',             'POST', '指标启用',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_DISABLE','/api/perf/metrics/*/disable',            'POST', '指标禁用',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_TRIAL',  '/api/perf/metrics/*/trial-run',          'POST', '指标试运行',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_METRIC_RUN',    '/api/perf/metrics/*/run-now',            'POST', '指标立即执行',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_LIST',      '/api/perf/kpi-schemes',                  'GET',  'KPI方案列表',    1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_DETAIL',    '/api/perf/kpi-schemes/*',                'GET',  'KPI方案详情',    1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_CREATE',    '/api/perf/kpi-schemes',                  'POST', 'KPI方案新增',    1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_UPDATE',    '/api/perf/kpi-schemes/*',                'PUT',  'KPI方案编辑',    1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_ITEMS',     '/api/perf/kpi-schemes/*/items/replace',  'POST', 'KPI项配置替换',  1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TGT_LIST',      '/api/perf/target-plans',                 'GET',  '目标方案列表',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TGT_DETAIL',    '/api/perf/target-plans/*',               'GET',  '目标方案详情',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TGT_CREATE',    '/api/perf/target-plans',                 'POST', '目标方案新增',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TGT_UPDATE',    '/api/perf/target-plans/*',               'PUT',  '目标方案编辑',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TGT_IMPORT',    '/api/perf/target-plans/*/import',        'POST', '目标导入',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_IDX_IMPORT',    '/api/perf/imports/index-result',         'POST', '指标结果导入',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_IMPORT',    '/api/perf/imports/kpi-result',           'POST', 'KPI结果导入',    1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_KPI_EXEC',      '/api/perf/kpi/execute',                  'POST', 'KPI执行',        1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_RECALC',        '/api/perf/recalc',                       'POST', '历史重算',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TASKS',         '/api/perf/run-tasks',                    'GET',  '执行任务列表',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_IMP_BATCH_LIST','/api/perf/import-batches',               'GET',  '导入批次列表',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_IMP_BATCH_DET', '/api/perf/import-batches/*',             'GET',  '导入批次详情',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_PERF_TASK_STATUS',   '/api/data-task/status',                  'POST', '任务状态查询',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.7 report-analytics-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_RPT_DYN_QUERY',    '/api/report/dynamic/query',                          'POST', '动态指标查询',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_DYN_EXPORT',   '/api/report/dynamic/export',                         'POST', '动态指标导出',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_DYN_SAVE',     '/api/report/dynamic/saved',                          'POST', '保存查询方案',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_DYN_LIST',     '/api/report/dynamic/saved',                          'GET',  '查询保存方案列表',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_FIX_DASH',     '/api/report/fixed/branch-president/dashboard',       'GET',  '行长仪表盘',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_FIX_DASH_EXP', '/api/report/fixed/branch-president/dashboard/export','POST', '行长仪表盘导出',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_SQL_EXEC',     '/api/report/sql-probe/execute',                      'POST', 'SQL探查执行',        1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_RPT_SQL_HIST',     '/api/report/sql-probe/history',                      'GET',  'SQL探查历史',        1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 3.8 system-governance-center 资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
('RES_SYS_DICT_LIST',     '/api/sys/dicts',                   'GET',  '字典查询',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_DICT_CREATE',   '/api/sys/dicts',                   'POST', '字典新增',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_DICT_UPDATE',   '/api/sys/dicts/*',                 'PUT',  '字典编辑',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CAL_MONTH',     '/api/sys/calendar/month',          'GET',  '日历月查询',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CAL_SET',       '/api/sys/calendar/set-day',        'POST', '日历设置',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CAL_IMPORT',    '/api/sys/calendar/batch-import',   'POST', '日历批量导入',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CAL_INIT',      '/api/sys/calendar/init-year',      'POST', '日历年初初始化',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CFG_LIST',      '/api/sys/configs',                 'GET',  '配置列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CFG_DETAIL',    '/api/sys/configs/*',               'GET',  '配置详情',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CFG_CREATE',    '/api/sys/configs',                 'POST', '配置新增',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CFG_UPDATE',    '/api/sys/configs/*',               'PUT',  '配置编辑',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CFG_ENABLE',    '/api/sys/configs/*/enable',        'POST', '配置启用',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_CFG_DISABLE',   '/api/sys/configs/*/disable',       'POST', '配置禁用',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_LIST',      '/api/sys/jobs',                    'GET',  '任务列表',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_CREATE',    '/api/sys/jobs',                    'POST', '任务新增',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_UPDATE',    '/api/sys/jobs/*',                  'PUT',  '任务编辑',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_PAUSE',     '/api/sys/jobs/*/pause',            'POST', '任务暂停',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_RESUME',    '/api/sys/jobs/*/resume',           'POST', '任务恢复',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_TRIGGER',   '/api/sys/jobs/*/trigger',          'POST', '任务手动触发',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_JOB_RUNS',      '/api/sys/jobs/*/runs',             'GET',  '任务执行日志',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_SLA_LIST',      '/api/sys/timeout-rules',           'GET',  '超时规则列表',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_SLA_CREATE',    '/api/sys/timeout-rules',           'POST', '超时规则新增',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_SLA_UPDATE',    '/api/sys/timeout-rules/*',         'PUT',  '超时规则编辑',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_NOTIFY_LIST',   '/api/notify/list',                 'GET',  '我的通知列表',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_NOTIFY_READ',   '/api/notify/*/read',               'POST', '通知标已读',       1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_NOTIFY_BATCH',  '/api/notify/batch-read',           'POST', '通知批量已读',     1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_FILE_UPLOAD',   '/api/sys/files/upload',            'POST', '文件上传(治理)',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_FILE_DOWNLOAD', '/api/sys/files/*/download',        'GET',  '文件下载(治理)',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_FILE_BY_BIZ',   '/api/sys/files/by-biz',            'GET',  '文件按业务查询',   1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_AUDIT_LIST',    '/api/sys/audits',                   'GET',  '审计查询',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed'),
('RES_SYS_AUDIT_EXPORT',  '/api/sys/audits/export',            'POST', '审计导出',         1, '1', 0, 'PLATFORM', NOW(), 'seed', NOW(), 'seed', 'V1 seed');


-- =========================================================
-- 4) PT_ROLE_RESOURCE 角色-资源绑定种子数据
--    按 project_ana.md 2.1.2 权限矩阵生成
-- =========================================================

-- ---------------------------------------------------------
-- 4.1 系统管理员（R_ADMIN）: 绑定所有资源
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('RR_ADMIN_', RESOURCE_ID), 'R_ADMIN', RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_RESOURCE WHERE SYS_CODE = 'PLATFORM';

-- ---------------------------------------------------------
-- 4.2 客户经理（R_RM）: 通用只读 + 客户营销写 + 业务申请写 + 工作流办理 + 个人报表
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
-- 公共：当前用户、组织树、通知
('RR_RM_AUTH',      'R_RM', 'RES_AUTH_CURRENT',       'PLATFORM', NOW()),
('RR_RM_ORG',       'R_RM', 'RES_ORG_TREE',           'PLATFORM', NOW()),
('RR_RM_NOTIFY_L',  'R_RM', 'RES_SYS_NOTIFY_LIST',    'PLATFORM', NOW()),
('RR_RM_NOTIFY_R',  'R_RM', 'RES_SYS_NOTIFY_READ',    'PLATFORM', NOW()),
('RR_RM_NOTIFY_B',  'R_RM', 'RES_SYS_NOTIFY_BATCH',   'PLATFORM', NOW()),
-- 门户：工作台待办/通知/卡片/快捷入口
('RR_RM_PTODO',     'R_RM', 'RES_PORTAL_TODOS',       'PLATFORM', NOW()),
('RR_RM_PNOTIFY',   'R_RM', 'RES_PORTAL_NOTIFY',      'PLATFORM', NOW()),
('RR_RM_PNREAD',    'R_RM', 'RES_PORTAL_NOTIFY_READ', 'PLATFORM', NOW()),
('RR_RM_PCARDS',    'R_RM', 'RES_PORTAL_CARDS',       'PLATFORM', NOW()),
('RR_RM_SCLIST',    'R_RM', 'RES_SHORTCUT_LIST',      'PLATFORM', NOW()),
('RR_RM_SCCREATE',  'R_RM', 'RES_SHORTCUT_CREATE',    'PLATFORM', NOW()),
('RR_RM_SCUPDATE',  'R_RM', 'RES_SHORTCUT_UPDATE',    'PLATFORM', NOW()),
('RR_RM_SCDELETE',  'R_RM', 'RES_SHORTCUT_DELETE',    'PLATFORM', NOW()),
('RR_RM_SCREPLACE', 'R_RM', 'RES_SHORTCUT_REPLACE',   'PLATFORM', NOW()),
-- 通讯录：读+维护本人
('RR_RM_ADDR_L',    'R_RM', 'RES_ADDRBOOK_LIST',      'PLATFORM', NOW()),
('RR_RM_ADDR_S',    'R_RM', 'RES_ADDRBOOK_SEARCH',    'PLATFORM', NOW()),
('RR_RM_ADDR_U',    'R_RM', 'RES_ADDRBOOK_UPDATE',    'PLATFORM', NOW()),
-- 产品/文档：只读
('RR_RM_PROD_L',    'R_RM', 'RES_PRODUCT_LIST',       'PLATFORM', NOW()),
('RR_RM_PROD_D',    'R_RM', 'RES_PRODUCT_DETAIL',     'PLATFORM', NOW()),
('RR_RM_DOC_L',     'R_RM', 'RES_DOC_LIST',           'PLATFORM', NOW()),
('RR_RM_DOC_D',     'R_RM', 'RES_DOC_DETAIL',         'PLATFORM', NOW()),
('RR_RM_DOC_DL',    'R_RM', 'RES_DOC_DOWNLOAD',       'PLATFORM', NOW()),
('RR_RM_NAV_L',     'R_RM', 'RES_NAV_LIST',           'PLATFORM', NOW()),
-- 标签：只读
('RR_RM_TAG_L',     'R_RM', 'RES_TAG_LIST',           'PLATFORM', NOW()),
('RR_RM_TAG_D',     'R_RM', 'RES_TAG_DETAIL',         'PLATFORM', NOW()),
-- 线索：CRUD + 导入
('RR_RM_LEAD_L',    'R_RM', 'RES_LEAD_LIST',          'PLATFORM', NOW()),
('RR_RM_LEAD_C',    'R_RM', 'RES_LEAD_CREATE',        'PLATFORM', NOW()),
('RR_RM_LEAD_U',    'R_RM', 'RES_LEAD_UPDATE',        'PLATFORM', NOW()),
('RR_RM_LEAD_D',    'R_RM', 'RES_LEAD_DETAIL',        'PLATFORM', NOW()),
('RR_RM_LEAD_S',    'R_RM', 'RES_LEAD_SUBMIT',        'PLATFORM', NOW()),
('RR_RM_LEAD_I',    'R_RM', 'RES_LEAD_IMPORT',        'PLATFORM', NOW()),
('RR_RM_LEAD_E',    'R_RM', 'RES_LEAD_EXPORT',        'PLATFORM', NOW()),
('RR_RM_LEAD_IB',   'R_RM', 'RES_LEAD_IMP_BATCH',     'PLATFORM', NOW()),
-- 客户池只读 + 认领
('RR_RM_POOL',      'R_RM', 'RES_CUST_POOL',          'PLATFORM', NOW()),
('RR_RM_CLAIM_C',   'R_RM', 'RES_CLAIM_CREATE',       'PLATFORM', NOW()),
('RR_RM_CLAIM_X',   'R_RM', 'RES_CLAIM_CANCEL',       'PLATFORM', NOW()),
('RR_RM_CLAIM_R',   'R_RM', 'RES_CLAIM_RESTART',      'PLATFORM', NOW()),
-- 触达任务
('RR_RM_TOUCH_L',   'R_RM', 'RES_TOUCH_LIST',         'PLATFORM', NOW()),
('RR_RM_TOUCH_D',   'R_RM', 'RES_TOUCH_DETAIL',       'PLATFORM', NOW()),
('RR_RM_TOUCH_LOG', 'R_RM', 'RES_TOUCH_LOG',          'PLATFORM', NOW()),
('RR_RM_TOUCH_S',   'R_RM', 'RES_TOUCH_SUCCESS',      'PLATFORM', NOW()),
('RR_RM_TOUCH_X',   'R_RM', 'RES_TOUCH_CANCEL',       'PLATFORM', NOW()),
-- 资产投放
('RR_RM_LOAN_L',    'R_RM', 'RES_LOAN_LIST',          'PLATFORM', NOW()),
('RR_RM_LOAN_D',    'R_RM', 'RES_LOAN_DETAIL',        'PLATFORM', NOW()),
('RR_RM_LOAN_C',    'R_RM', 'RES_LOAN_CREATE',        'PLATFORM', NOW()),
('RR_RM_LOAN_U',    'R_RM', 'RES_LOAN_UPDATE',        'PLATFORM', NOW()),
('RR_RM_LOAN_S',    'R_RM', 'RES_LOAN_SUBMIT',        'PLATFORM', NOW()),
('RR_RM_LOAN_E',    'R_RM', 'RES_LOAN_EXPORT',        'PLATFORM', NOW()),
-- 中场支持（发起方）
('RR_RM_SUP_L',     'R_RM', 'RES_SUPPORT_LIST',       'PLATFORM', NOW()),
('RR_RM_SUP_D',     'R_RM', 'RES_SUPPORT_DETAIL',     'PLATFORM', NOW()),
('RR_RM_SUP_C',     'R_RM', 'RES_SUPPORT_CREATE',     'PLATFORM', NOW()),
('RR_RM_SUP_U',     'R_RM', 'RES_SUPPORT_UPDATE',     'PLATFORM', NOW()),
('RR_RM_SUP_S',     'R_RM', 'RES_SUPPORT_SUBMIT',     'PLATFORM', NOW()),
('RR_RM_SUP_E',     'R_RM', 'RES_SUPPORT_EXPORT',     'PLATFORM', NOW()),
-- 工作流办理
('RR_RM_WF_TODO',   'R_RM', 'RES_WF_TODO',            'PLATFORM', NOW()),
('RR_RM_WF_DONE',   'R_RM', 'RES_WF_DONE',            'PLATFORM', NOW()),
('RR_RM_WF_DET',    'R_RM', 'RES_WF_DETAIL',          'PLATFORM', NOW()),
('RR_RM_WF_CLM',    'R_RM', 'RES_WF_CLAIM',           'PLATFORM', NOW()),
('RR_RM_WF_APV',    'R_RM', 'RES_WF_APPROVE',         'PLATFORM', NOW()),
('RR_RM_WF_REJ',    'R_RM', 'RES_WF_REJECT',          'PLATFORM', NOW()),
('RR_RM_WF_TRF',    'R_RM', 'RES_WF_TRANSFER',        'PLATFORM', NOW()),
('RR_RM_WF_HIS',    'R_RM', 'RES_WF_HISTORY',         'PLATFORM', NOW()),
('RR_RM_WF_PART',   'R_RM', 'RES_WF_IS_PART',         'PLATFORM', NOW()),
-- 报表：个人只读
('RR_RM_RPT_Q',     'R_RM', 'RES_RPT_DYN_QUERY',     'PLATFORM', NOW()),
('RR_RM_RPT_SL',    'R_RM', 'RES_RPT_DYN_LIST',      'PLATFORM', NOW()),
('RR_RM_RPT_SS',    'R_RM', 'RES_RPT_DYN_SAVE',      'PLATFORM', NOW()),
-- 文件
('RR_RM_FILE_U',    'R_RM', 'RES_FILE_UPLOAD',        'PLATFORM', NOW()),
('RR_RM_FILE_D',    'R_RM', 'RES_FILE_DOWNLOAD',      'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.3 经营机构负责人（R_BRANCH_MGR）: 审批+机构视角+报表
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_BM_AUTH',     'R_BRANCH_MGR', 'RES_AUTH_CURRENT',       'PLATFORM', NOW()),
('RR_BM_ORG',      'R_BRANCH_MGR', 'RES_ORG_TREE',           'PLATFORM', NOW()),
('RR_BM_ORG_SUB',  'R_BRANCH_MGR', 'RES_ORG_SUBTREE',        'PLATFORM', NOW()),
('RR_BM_NTF_L',    'R_BRANCH_MGR', 'RES_SYS_NOTIFY_LIST',    'PLATFORM', NOW()),
('RR_BM_NTF_R',    'R_BRANCH_MGR', 'RES_SYS_NOTIFY_READ',    'PLATFORM', NOW()),
('RR_BM_PTODO',    'R_BRANCH_MGR', 'RES_PORTAL_TODOS',       'PLATFORM', NOW()),
('RR_BM_PNOTIFY',  'R_BRANCH_MGR', 'RES_PORTAL_NOTIFY',      'PLATFORM', NOW()),
('RR_BM_PCARDS',   'R_BRANCH_MGR', 'RES_PORTAL_CARDS',       'PLATFORM', NOW()),
('RR_BM_LEAD_L',   'R_BRANCH_MGR', 'RES_LEAD_LIST',          'PLATFORM', NOW()),
('RR_BM_LEAD_D',   'R_BRANCH_MGR', 'RES_LEAD_DETAIL',        'PLATFORM', NOW()),
('RR_BM_CUST_L',   'R_BRANCH_MGR', 'RES_CUSTOMER_LIST',      'PLATFORM', NOW()),
('RR_BM_CUST_D',   'R_BRANCH_MGR', 'RES_CUSTOMER_DETAIL',    'PLATFORM', NOW()),
('RR_BM_CUST_H',   'R_BRANCH_MGR', 'RES_CUSTOMER_HISTORY',   'PLATFORM', NOW()),
('RR_BM_CUST_E',   'R_BRANCH_MGR', 'RES_CUSTOMER_EDIT',      'PLATFORM', NOW()),
('RR_BM_CUST_T',   'R_BRANCH_MGR', 'RES_CUSTOMER_TRANSFER',  'PLATFORM', NOW()),
('RR_BM_CUST_DEL', 'R_BRANCH_MGR', 'RES_CUSTOMER_DEL_APPLY', 'PLATFORM', NOW()),
('RR_BM_TRPT_L',   'R_BRANCH_MGR', 'RES_TOUCH_RPT_LIST',     'PLATFORM', NOW()),
('RR_BM_TRPT_E',   'R_BRANCH_MGR', 'RES_TOUCH_RPT_EXPORT',   'PLATFORM', NOW()),
('RR_BM_SUP_L',    'R_BRANCH_MGR', 'RES_SUPPORT_LIST',       'PLATFORM', NOW()),
('RR_BM_SUP_D',    'R_BRANCH_MGR', 'RES_SUPPORT_DETAIL',     'PLATFORM', NOW()),
('RR_BM_WF_TODO',  'R_BRANCH_MGR', 'RES_WF_TODO',            'PLATFORM', NOW()),
('RR_BM_WF_DONE',  'R_BRANCH_MGR', 'RES_WF_DONE',            'PLATFORM', NOW()),
('RR_BM_WF_DET',   'R_BRANCH_MGR', 'RES_WF_DETAIL',          'PLATFORM', NOW()),
('RR_BM_WF_CLM',   'R_BRANCH_MGR', 'RES_WF_CLAIM',           'PLATFORM', NOW()),
('RR_BM_WF_APV',   'R_BRANCH_MGR', 'RES_WF_APPROVE',         'PLATFORM', NOW()),
('RR_BM_WF_REJ',   'R_BRANCH_MGR', 'RES_WF_REJECT',          'PLATFORM', NOW()),
('RR_BM_WF_TRF',   'R_BRANCH_MGR', 'RES_WF_TRANSFER',        'PLATFORM', NOW()),
('RR_BM_WF_HIS',   'R_BRANCH_MGR', 'RES_WF_HISTORY',         'PLATFORM', NOW()),
('RR_BM_RPT_Q',    'R_BRANCH_MGR', 'RES_RPT_DYN_QUERY',      'PLATFORM', NOW()),
('RR_BM_RPT_E',    'R_BRANCH_MGR', 'RES_RPT_DYN_EXPORT',     'PLATFORM', NOW()),
('RR_BM_RPT_SL',   'R_BRANCH_MGR', 'RES_RPT_DYN_LIST',       'PLATFORM', NOW()),
('RR_BM_RPT_SS',   'R_BRANCH_MGR', 'RES_RPT_DYN_SAVE',       'PLATFORM', NOW()),
('RR_BM_NAV_L',    'R_BRANCH_MGR', 'RES_NAV_LIST',            'PLATFORM', NOW()),
('RR_BM_FILE_U',   'R_BRANCH_MGR', 'RES_FILE_UPLOAD',         'PLATFORM', NOW()),
('RR_BM_FILE_D',   'R_BRANCH_MGR', 'RES_FILE_DOWNLOAD',       'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.4 公司部人员（R_CORP_DEPT）: 标签管理+线索审批+客户+触达监控+资产投放审批+报表
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_CD_AUTH',      'R_CORP_DEPT', 'RES_AUTH_CURRENT',        'PLATFORM', NOW()),
('RR_CD_ORG',       'R_CORP_DEPT', 'RES_ORG_TREE',            'PLATFORM', NOW()),
('RR_CD_NTF_L',     'R_CORP_DEPT', 'RES_SYS_NOTIFY_LIST',     'PLATFORM', NOW()),
('RR_CD_NTF_R',     'R_CORP_DEPT', 'RES_SYS_NOTIFY_READ',     'PLATFORM', NOW()),
('RR_CD_PTODO',     'R_CORP_DEPT', 'RES_PORTAL_TODOS',        'PLATFORM', NOW()),
('RR_CD_PNOTIFY',   'R_CORP_DEPT', 'RES_PORTAL_NOTIFY',       'PLATFORM', NOW()),
('RR_CD_PCARDS',    'R_CORP_DEPT', 'RES_PORTAL_CARDS',        'PLATFORM', NOW()),
-- 标签全功能
('RR_CD_TAG_L',     'R_CORP_DEPT', 'RES_TAG_LIST',            'PLATFORM', NOW()),
('RR_CD_TAG_C',     'R_CORP_DEPT', 'RES_TAG_CREATE',          'PLATFORM', NOW()),
('RR_CD_TAG_U',     'R_CORP_DEPT', 'RES_TAG_UPDATE',          'PLATFORM', NOW()),
('RR_CD_TAG_D',     'R_CORP_DEPT', 'RES_TAG_DETAIL',          'PLATFORM', NOW()),
('RR_CD_TAG_EN',    'R_CORP_DEPT', 'RES_TAG_ENABLE',          'PLATFORM', NOW()),
('RR_CD_TAG_DIS',   'R_CORP_DEPT', 'RES_TAG_DISABLE',         'PLATFORM', NOW()),
('RR_CD_TAG_CR',    'R_CORP_DEPT', 'RES_TAG_CUST_REPLACE',    'PLATFORM', NOW()),
('RR_CD_TAG_IP',    'R_CORP_DEPT', 'RES_TAG_IMPORT_PREVIEW',  'PLATFORM', NOW()),
('RR_CD_TAG_IC',    'R_CORP_DEPT', 'RES_TAG_IMPORT_CONFIRM',  'PLATFORM', NOW()),
('RR_CD_TAG_EX',    'R_CORP_DEPT', 'RES_TAG_EXPORT',          'PLATFORM', NOW()),
-- 线索审批
('RR_CD_LEAD_L',    'R_CORP_DEPT', 'RES_LEAD_LIST',           'PLATFORM', NOW()),
('RR_CD_LEAD_D',    'R_CORP_DEPT', 'RES_LEAD_DETAIL',         'PLATFORM', NOW()),
('RR_CD_LEAD_E',    'R_CORP_DEPT', 'RES_LEAD_EXPORT',         'PLATFORM', NOW()),
-- 客户全功能
('RR_CD_CUST_L',    'R_CORP_DEPT', 'RES_CUSTOMER_LIST',       'PLATFORM', NOW()),
('RR_CD_CUST_D',    'R_CORP_DEPT', 'RES_CUSTOMER_DETAIL',     'PLATFORM', NOW()),
('RR_CD_CUST_H',    'R_CORP_DEPT', 'RES_CUSTOMER_HISTORY',    'PLATFORM', NOW()),
('RR_CD_CUST_E',    'R_CORP_DEPT', 'RES_CUSTOMER_EDIT',       'PLATFORM', NOW()),
('RR_CD_CUST_T',    'R_CORP_DEPT', 'RES_CUSTOMER_TRANSFER',   'PLATFORM', NOW()),
('RR_CD_CUST_DEL',  'R_CORP_DEPT', 'RES_CUSTOMER_DEL_APPLY',  'PLATFORM', NOW()),
-- 触达监控
('RR_CD_TRPT_L',    'R_CORP_DEPT', 'RES_TOUCH_RPT_LIST',      'PLATFORM', NOW()),
('RR_CD_TRPT_E',    'R_CORP_DEPT', 'RES_TOUCH_RPT_EXPORT',    'PLATFORM', NOW()),
-- 资产投放（审批）
('RR_CD_LOAN_L',    'R_CORP_DEPT', 'RES_LOAN_LIST',           'PLATFORM', NOW()),
('RR_CD_LOAN_D',    'R_CORP_DEPT', 'RES_LOAN_DETAIL',         'PLATFORM', NOW()),
-- 工作流
('RR_CD_WF_TODO',   'R_CORP_DEPT', 'RES_WF_TODO',             'PLATFORM', NOW()),
('RR_CD_WF_DONE',   'R_CORP_DEPT', 'RES_WF_DONE',             'PLATFORM', NOW()),
('RR_CD_WF_DET',    'R_CORP_DEPT', 'RES_WF_DETAIL',           'PLATFORM', NOW()),
('RR_CD_WF_CLM',    'R_CORP_DEPT', 'RES_WF_CLAIM',            'PLATFORM', NOW()),
('RR_CD_WF_APV',    'R_CORP_DEPT', 'RES_WF_APPROVE',          'PLATFORM', NOW()),
('RR_CD_WF_REJ',    'R_CORP_DEPT', 'RES_WF_REJECT',           'PLATFORM', NOW()),
('RR_CD_WF_TRF',    'R_CORP_DEPT', 'RES_WF_TRANSFER',         'PLATFORM', NOW()),
('RR_CD_WF_HIS',    'R_CORP_DEPT', 'RES_WF_HISTORY',          'PLATFORM', NOW()),
-- 报表
('RR_CD_RPT_Q',     'R_CORP_DEPT', 'RES_RPT_DYN_QUERY',      'PLATFORM', NOW()),
('RR_CD_RPT_E',     'R_CORP_DEPT', 'RES_RPT_DYN_EXPORT',     'PLATFORM', NOW()),
('RR_CD_RPT_SL',    'R_CORP_DEPT', 'RES_RPT_DYN_LIST',       'PLATFORM', NOW()),
('RR_CD_RPT_SS',    'R_CORP_DEPT', 'RES_RPT_DYN_SAVE',       'PLATFORM', NOW()),
('RR_CD_NAV_L',     'R_CORP_DEPT', 'RES_NAV_LIST',            'PLATFORM', NOW()),
('RR_CD_FILE_U',    'R_CORP_DEPT', 'RES_FILE_UPLOAD',         'PLATFORM', NOW()),
('RR_CD_FILE_D',    'R_CORP_DEPT', 'RES_FILE_DOWNLOAD',       'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.5 零售部人员（R_RETAIL_DEPT）: 与公司部基本一致
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('RR_RD_', SUBSTRING(ID, 7)), 'R_RETAIL_DEPT', RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE WHERE ROLE_ID = 'R_CORP_DEPT';

-- ---------------------------------------------------------
-- 4.6 中后台员工-资财（R_BACK_FINANCE）: 绩效全功能 + 报表
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_BF_AUTH',     'R_BACK_FINANCE', 'RES_AUTH_CURRENT',         'PLATFORM', NOW()),
('RR_BF_ORG',      'R_BACK_FINANCE', 'RES_ORG_TREE',             'PLATFORM', NOW()),
('RR_BF_NTF_L',    'R_BACK_FINANCE', 'RES_SYS_NOTIFY_LIST',      'PLATFORM', NOW()),
('RR_BF_NTF_R',    'R_BACK_FINANCE', 'RES_SYS_NOTIFY_READ',      'PLATFORM', NOW()),
('RR_BF_PTODO',    'R_BACK_FINANCE', 'RES_PORTAL_TODOS',         'PLATFORM', NOW()),
('RR_BF_PNOTIFY',  'R_BACK_FINANCE', 'RES_PORTAL_NOTIFY',        'PLATFORM', NOW()),
('RR_BF_PCARDS',   'R_BACK_FINANCE', 'RES_PORTAL_CARDS',         'PLATFORM', NOW()),
-- 绩效全功能
('RR_BF_PM_L',     'R_BACK_FINANCE', 'RES_PERF_METRIC_LIST',     'PLATFORM', NOW()),
('RR_BF_PM_D',     'R_BACK_FINANCE', 'RES_PERF_METRIC_DETAIL',   'PLATFORM', NOW()),
('RR_BF_PM_C',     'R_BACK_FINANCE', 'RES_PERF_METRIC_CREATE',   'PLATFORM', NOW()),
('RR_BF_PM_U',     'R_BACK_FINANCE', 'RES_PERF_METRIC_UPDATE',   'PLATFORM', NOW()),
('RR_BF_PM_EN',    'R_BACK_FINANCE', 'RES_PERF_METRIC_ENABLE',   'PLATFORM', NOW()),
('RR_BF_PM_DIS',   'R_BACK_FINANCE', 'RES_PERF_METRIC_DISABLE',  'PLATFORM', NOW()),
('RR_BF_PM_TR',    'R_BACK_FINANCE', 'RES_PERF_METRIC_TRIAL',    'PLATFORM', NOW()),
('RR_BF_PM_RUN',   'R_BACK_FINANCE', 'RES_PERF_METRIC_RUN',      'PLATFORM', NOW()),
('RR_BF_KPI_L',    'R_BACK_FINANCE', 'RES_PERF_KPI_LIST',        'PLATFORM', NOW()),
('RR_BF_KPI_D',    'R_BACK_FINANCE', 'RES_PERF_KPI_DETAIL',      'PLATFORM', NOW()),
('RR_BF_KPI_C',    'R_BACK_FINANCE', 'RES_PERF_KPI_CREATE',      'PLATFORM', NOW()),
('RR_BF_KPI_U',    'R_BACK_FINANCE', 'RES_PERF_KPI_UPDATE',      'PLATFORM', NOW()),
('RR_BF_KPI_IT',   'R_BACK_FINANCE', 'RES_PERF_KPI_ITEMS',       'PLATFORM', NOW()),
('RR_BF_TGT_L',    'R_BACK_FINANCE', 'RES_PERF_TGT_LIST',        'PLATFORM', NOW()),
('RR_BF_TGT_D',    'R_BACK_FINANCE', 'RES_PERF_TGT_DETAIL',      'PLATFORM', NOW()),
('RR_BF_TGT_C',    'R_BACK_FINANCE', 'RES_PERF_TGT_CREATE',      'PLATFORM', NOW()),
('RR_BF_TGT_U',    'R_BACK_FINANCE', 'RES_PERF_TGT_UPDATE',      'PLATFORM', NOW()),
('RR_BF_TGT_I',    'R_BACK_FINANCE', 'RES_PERF_TGT_IMPORT',      'PLATFORM', NOW()),
('RR_BF_IDX_I',    'R_BACK_FINANCE', 'RES_PERF_IDX_IMPORT',      'PLATFORM', NOW()),
('RR_BF_KPI_I',    'R_BACK_FINANCE', 'RES_PERF_KPI_IMPORT',      'PLATFORM', NOW()),
('RR_BF_KPI_EX',   'R_BACK_FINANCE', 'RES_PERF_KPI_EXEC',        'PLATFORM', NOW()),
('RR_BF_RECALC',   'R_BACK_FINANCE', 'RES_PERF_RECALC',          'PLATFORM', NOW()),
('RR_BF_TASKS',    'R_BACK_FINANCE', 'RES_PERF_TASKS',            'PLATFORM', NOW()),
('RR_BF_IB_L',     'R_BACK_FINANCE', 'RES_PERF_IMP_BATCH_LIST',  'PLATFORM', NOW()),
('RR_BF_IB_D',     'R_BACK_FINANCE', 'RES_PERF_IMP_BATCH_DET',   'PLATFORM', NOW()),
('RR_BF_TS',       'R_BACK_FINANCE', 'RES_PERF_TASK_STATUS',      'PLATFORM', NOW()),
-- 报表
('RR_BF_RPT_Q',    'R_BACK_FINANCE', 'RES_RPT_DYN_QUERY',        'PLATFORM', NOW()),
('RR_BF_RPT_E',    'R_BACK_FINANCE', 'RES_RPT_DYN_EXPORT',       'PLATFORM', NOW()),
('RR_BF_RPT_SL',   'R_BACK_FINANCE', 'RES_RPT_DYN_LIST',         'PLATFORM', NOW()),
('RR_BF_RPT_SS',   'R_BACK_FINANCE', 'RES_RPT_DYN_SAVE',         'PLATFORM', NOW()),
-- 工作流（参与审批）
('RR_BF_WF_TODO',  'R_BACK_FINANCE', 'RES_WF_TODO',               'PLATFORM', NOW()),
('RR_BF_WF_DONE',  'R_BACK_FINANCE', 'RES_WF_DONE',               'PLATFORM', NOW()),
('RR_BF_WF_DET',   'R_BACK_FINANCE', 'RES_WF_DETAIL',             'PLATFORM', NOW()),
('RR_BF_WF_CLM',   'R_BACK_FINANCE', 'RES_WF_CLAIM',              'PLATFORM', NOW()),
('RR_BF_WF_APV',   'R_BACK_FINANCE', 'RES_WF_APPROVE',            'PLATFORM', NOW()),
('RR_BF_WF_REJ',   'R_BACK_FINANCE', 'RES_WF_REJECT',             'PLATFORM', NOW()),
('RR_BF_WF_HIS',   'R_BACK_FINANCE', 'RES_WF_HISTORY',            'PLATFORM', NOW()),
('RR_BF_NAV_L',    'R_BACK_FINANCE', 'RES_NAV_LIST',               'PLATFORM', NOW()),
('RR_BF_FILE_U',   'R_BACK_FINANCE', 'RES_FILE_UPLOAD',            'PLATFORM', NOW()),
('RR_BF_FILE_D',   'R_BACK_FINANCE', 'RES_FILE_DOWNLOAD',          'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.7 中后台员工-科技（R_BACK_TECH）: 导航/文档/系统配置全功能 + SQL探查
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_BT_AUTH',     'R_BACK_TECH', 'RES_AUTH_CURRENT',        'PLATFORM', NOW()),
('RR_BT_ORG',      'R_BACK_TECH', 'RES_ORG_TREE',            'PLATFORM', NOW()),
('RR_BT_NTF_L',    'R_BACK_TECH', 'RES_SYS_NOTIFY_LIST',     'PLATFORM', NOW()),
('RR_BT_NTF_R',    'R_BACK_TECH', 'RES_SYS_NOTIFY_READ',     'PLATFORM', NOW()),
('RR_BT_PTODO',    'R_BACK_TECH', 'RES_PORTAL_TODOS',        'PLATFORM', NOW()),
('RR_BT_PNOTIFY',  'R_BACK_TECH', 'RES_PORTAL_NOTIFY',       'PLATFORM', NOW()),
('RR_BT_PCARDS',   'R_BACK_TECH', 'RES_PORTAL_CARDS',        'PLATFORM', NOW()),
-- 导航管理
('RR_BT_NAV_L',    'R_BACK_TECH', 'RES_NAV_LIST',            'PLATFORM', NOW()),
('RR_BT_NAV_C',    'R_BACK_TECH', 'RES_NAV_CREATE',          'PLATFORM', NOW()),
('RR_BT_NAV_U',    'R_BACK_TECH', 'RES_NAV_UPDATE',          'PLATFORM', NOW()),
('RR_BT_NAV_D',    'R_BACK_TECH', 'RES_NAV_DELETE',          'PLATFORM', NOW()),
-- 文档管理
('RR_BT_DOC_L',    'R_BACK_TECH', 'RES_DOC_LIST',            'PLATFORM', NOW()),
('RR_BT_DOC_D',    'R_BACK_TECH', 'RES_DOC_DETAIL',          'PLATFORM', NOW()),
('RR_BT_DOC_UL',   'R_BACK_TECH', 'RES_DOC_UPLOAD',          'PLATFORM', NOW()),
('RR_BT_DOC_UP',   'R_BACK_TECH', 'RES_DOC_UPDATE',          'PLATFORM', NOW()),
('RR_BT_DOC_DEL',  'R_BACK_TECH', 'RES_DOC_DELETE',          'PLATFORM', NOW()),
('RR_BT_DOC_DL',   'R_BACK_TECH', 'RES_DOC_DOWNLOAD',        'PLATFORM', NOW()),
-- 系统治理全功能
('RR_BT_DICT_L',   'R_BACK_TECH', 'RES_SYS_DICT_LIST',       'PLATFORM', NOW()),
('RR_BT_DICT_C',   'R_BACK_TECH', 'RES_SYS_DICT_CREATE',     'PLATFORM', NOW()),
('RR_BT_DICT_U',   'R_BACK_TECH', 'RES_SYS_DICT_UPDATE',     'PLATFORM', NOW()),
('RR_BT_CAL_M',    'R_BACK_TECH', 'RES_SYS_CAL_MONTH',       'PLATFORM', NOW()),
('RR_BT_CAL_S',    'R_BACK_TECH', 'RES_SYS_CAL_SET',         'PLATFORM', NOW()),
('RR_BT_CAL_I',    'R_BACK_TECH', 'RES_SYS_CAL_IMPORT',      'PLATFORM', NOW()),
('RR_BT_CAL_INIT', 'R_BACK_TECH', 'RES_SYS_CAL_INIT',        'PLATFORM', NOW()),
('RR_BT_CFG_L',    'R_BACK_TECH', 'RES_SYS_CFG_LIST',        'PLATFORM', NOW()),
('RR_BT_CFG_D',    'R_BACK_TECH', 'RES_SYS_CFG_DETAIL',      'PLATFORM', NOW()),
('RR_BT_CFG_C',    'R_BACK_TECH', 'RES_SYS_CFG_CREATE',      'PLATFORM', NOW()),
('RR_BT_CFG_U',    'R_BACK_TECH', 'RES_SYS_CFG_UPDATE',      'PLATFORM', NOW()),
('RR_BT_CFG_EN',   'R_BACK_TECH', 'RES_SYS_CFG_ENABLE',      'PLATFORM', NOW()),
('RR_BT_CFG_DIS',  'R_BACK_TECH', 'RES_SYS_CFG_DISABLE',     'PLATFORM', NOW()),
('RR_BT_JOB_L',    'R_BACK_TECH', 'RES_SYS_JOB_LIST',        'PLATFORM', NOW()),
('RR_BT_JOB_C',    'R_BACK_TECH', 'RES_SYS_JOB_CREATE',      'PLATFORM', NOW()),
('RR_BT_JOB_U',    'R_BACK_TECH', 'RES_SYS_JOB_UPDATE',      'PLATFORM', NOW()),
('RR_BT_JOB_P',    'R_BACK_TECH', 'RES_SYS_JOB_PAUSE',       'PLATFORM', NOW()),
('RR_BT_JOB_R',    'R_BACK_TECH', 'RES_SYS_JOB_RESUME',      'PLATFORM', NOW()),
('RR_BT_JOB_T',    'R_BACK_TECH', 'RES_SYS_JOB_TRIGGER',     'PLATFORM', NOW()),
('RR_BT_JOB_RUNS', 'R_BACK_TECH', 'RES_SYS_JOB_RUNS',        'PLATFORM', NOW()),
('RR_BT_SLA_L',    'R_BACK_TECH', 'RES_SYS_SLA_LIST',        'PLATFORM', NOW()),
('RR_BT_SLA_C',    'R_BACK_TECH', 'RES_SYS_SLA_CREATE',      'PLATFORM', NOW()),
('RR_BT_SLA_U',    'R_BACK_TECH', 'RES_SYS_SLA_UPDATE',      'PLATFORM', NOW()),
('RR_BT_AUD_L',    'R_BACK_TECH', 'RES_SYS_AUDIT_LIST',      'PLATFORM', NOW()),
('RR_BT_AUD_E',    'R_BACK_TECH', 'RES_SYS_AUDIT_EXPORT',    'PLATFORM', NOW()),
-- SQL探查
('RR_BT_SQL_X',    'R_BACK_TECH', 'RES_RPT_SQL_EXEC',        'PLATFORM', NOW()),
('RR_BT_SQL_H',    'R_BACK_TECH', 'RES_RPT_SQL_HIST',        'PLATFORM', NOW()),
-- 工作流配置
('RR_BT_WF_CC',    'R_BACK_TECH', 'RES_WF_CFG_CAND',         'PLATFORM', NOW()),
('RR_BT_WF_CF',    'R_BACK_TECH', 'RES_WF_CFG_FORM',         'PLATFORM', NOW()),
('RR_BT_WF_CS',    'R_BACK_TECH', 'RES_WF_CFG_SLA',          'PLATFORM', NOW()),
('RR_BT_FILE_U',   'R_BACK_TECH', 'RES_SYS_FILE_UPLOAD',     'PLATFORM', NOW()),
('RR_BT_FILE_D',   'R_BACK_TECH', 'RES_SYS_FILE_DOWNLOAD',   'PLATFORM', NOW()),
('RR_BT_FILE_B',   'R_BACK_TECH', 'RES_SYS_FILE_BY_BIZ',     'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.8 中场支持部门秘书（R_SUPPORT_SEC）: 承接列表+派单
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_SS_AUTH',     'R_SUPPORT_SEC', 'RES_AUTH_CURRENT',       'PLATFORM', NOW()),
('RR_SS_ORG',      'R_SUPPORT_SEC', 'RES_ORG_TREE',           'PLATFORM', NOW()),
('RR_SS_NTF_L',    'R_SUPPORT_SEC', 'RES_SYS_NOTIFY_LIST',    'PLATFORM', NOW()),
('RR_SS_NTF_R',    'R_SUPPORT_SEC', 'RES_SYS_NOTIFY_READ',    'PLATFORM', NOW()),
('RR_SS_PTODO',    'R_SUPPORT_SEC', 'RES_PORTAL_TODOS',       'PLATFORM', NOW()),
('RR_SS_PNOTIFY',  'R_SUPPORT_SEC', 'RES_PORTAL_NOTIFY',      'PLATFORM', NOW()),
('RR_SS_PCARDS',   'R_SUPPORT_SEC', 'RES_PORTAL_CARDS',       'PLATFORM', NOW()),
('RR_SS_SD_L',     'R_SUPPORT_SEC', 'RES_SUPDEPT_LIST',       'PLATFORM', NOW()),
('RR_SS_SD_D',     'R_SUPPORT_SEC', 'RES_SUPDEPT_DETAIL',     'PLATFORM', NOW()),
('RR_SS_SD_DISP',  'R_SUPPORT_SEC', 'RES_SUPDEPT_DISPATCH',   'PLATFORM', NOW()),
('RR_SS_PROD_L',   'R_SUPPORT_SEC', 'RES_PRODUCT_LIST',       'PLATFORM', NOW()),
('RR_SS_PROD_D',   'R_SUPPORT_SEC', 'RES_PRODUCT_DETAIL',     'PLATFORM', NOW()),
('RR_SS_PROD_C',   'R_SUPPORT_SEC', 'RES_PRODUCT_CREATE',     'PLATFORM', NOW()),
('RR_SS_PROD_U',   'R_SUPPORT_SEC', 'RES_PRODUCT_UPDATE',     'PLATFORM', NOW()),
('RR_SS_WF_TODO',  'R_SUPPORT_SEC', 'RES_WF_TODO',            'PLATFORM', NOW()),
('RR_SS_WF_DONE',  'R_SUPPORT_SEC', 'RES_WF_DONE',            'PLATFORM', NOW()),
('RR_SS_WF_DET',   'R_SUPPORT_SEC', 'RES_WF_DETAIL',          'PLATFORM', NOW()),
('RR_SS_WF_CLM',   'R_SUPPORT_SEC', 'RES_WF_CLAIM',           'PLATFORM', NOW()),
('RR_SS_WF_APV',   'R_SUPPORT_SEC', 'RES_WF_APPROVE',         'PLATFORM', NOW()),
('RR_SS_WF_REJ',   'R_SUPPORT_SEC', 'RES_WF_REJECT',          'PLATFORM', NOW()),
('RR_SS_WF_TRF',   'R_SUPPORT_SEC', 'RES_WF_TRANSFER',        'PLATFORM', NOW()),
('RR_SS_WF_HIS',   'R_SUPPORT_SEC', 'RES_WF_HISTORY',         'PLATFORM', NOW()),
('RR_SS_NAV_L',    'R_SUPPORT_SEC', 'RES_NAV_LIST',            'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.9 中场支持部门人员（R_SUPPORT_STAFF）: 承接列表+办理
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_ST_AUTH',     'R_SUPPORT_STAFF', 'RES_AUTH_CURRENT',       'PLATFORM', NOW()),
('RR_ST_ORG',      'R_SUPPORT_STAFF', 'RES_ORG_TREE',           'PLATFORM', NOW()),
('RR_ST_NTF_L',    'R_SUPPORT_STAFF', 'RES_SYS_NOTIFY_LIST',    'PLATFORM', NOW()),
('RR_ST_NTF_R',    'R_SUPPORT_STAFF', 'RES_SYS_NOTIFY_READ',    'PLATFORM', NOW()),
('RR_ST_PTODO',    'R_SUPPORT_STAFF', 'RES_PORTAL_TODOS',       'PLATFORM', NOW()),
('RR_ST_PNOTIFY',  'R_SUPPORT_STAFF', 'RES_PORTAL_NOTIFY',      'PLATFORM', NOW()),
('RR_ST_PCARDS',   'R_SUPPORT_STAFF', 'RES_PORTAL_CARDS',       'PLATFORM', NOW()),
('RR_ST_SD_L',     'R_SUPPORT_STAFF', 'RES_SUPDEPT_LIST',       'PLATFORM', NOW()),
('RR_ST_SD_D',     'R_SUPPORT_STAFF', 'RES_SUPDEPT_DETAIL',     'PLATFORM', NOW()),
('RR_ST_WF_TODO',  'R_SUPPORT_STAFF', 'RES_WF_TODO',            'PLATFORM', NOW()),
('RR_ST_WF_DONE',  'R_SUPPORT_STAFF', 'RES_WF_DONE',            'PLATFORM', NOW()),
('RR_ST_WF_DET',   'R_SUPPORT_STAFF', 'RES_WF_DETAIL',          'PLATFORM', NOW()),
('RR_ST_WF_CLM',   'R_SUPPORT_STAFF', 'RES_WF_CLAIM',           'PLATFORM', NOW()),
('RR_ST_WF_APV',   'R_SUPPORT_STAFF', 'RES_WF_APPROVE',         'PLATFORM', NOW()),
('RR_ST_WF_REJ',   'R_SUPPORT_STAFF', 'RES_WF_REJECT',          'PLATFORM', NOW()),
('RR_ST_WF_HIS',   'R_SUPPORT_STAFF', 'RES_WF_HISTORY',         'PLATFORM', NOW()),
('RR_ST_NAV_L',    'R_SUPPORT_STAFF', 'RES_NAV_LIST',            'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.10 授信审查人员（R_CREDIT_REVIEWER）: 资产投放审查
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_CR_AUTH',     'R_CREDIT_REVIEWER', 'RES_AUTH_CURRENT',       'PLATFORM', NOW()),
('RR_CR_ORG',      'R_CREDIT_REVIEWER', 'RES_ORG_TREE',           'PLATFORM', NOW()),
('RR_CR_NTF_L',    'R_CREDIT_REVIEWER', 'RES_SYS_NOTIFY_LIST',    'PLATFORM', NOW()),
('RR_CR_NTF_R',    'R_CREDIT_REVIEWER', 'RES_SYS_NOTIFY_READ',    'PLATFORM', NOW()),
('RR_CR_PTODO',    'R_CREDIT_REVIEWER', 'RES_PORTAL_TODOS',       'PLATFORM', NOW()),
('RR_CR_PNOTIFY',  'R_CREDIT_REVIEWER', 'RES_PORTAL_NOTIFY',      'PLATFORM', NOW()),
('RR_CR_PCARDS',   'R_CREDIT_REVIEWER', 'RES_PORTAL_CARDS',       'PLATFORM', NOW()),
('RR_CR_LOAN_L',   'R_CREDIT_REVIEWER', 'RES_LOAN_LIST',          'PLATFORM', NOW()),
('RR_CR_LOAN_D',   'R_CREDIT_REVIEWER', 'RES_LOAN_DETAIL',        'PLATFORM', NOW()),
('RR_CR_WF_TODO',  'R_CREDIT_REVIEWER', 'RES_WF_TODO',            'PLATFORM', NOW()),
('RR_CR_WF_DONE',  'R_CREDIT_REVIEWER', 'RES_WF_DONE',            'PLATFORM', NOW()),
('RR_CR_WF_DET',   'R_CREDIT_REVIEWER', 'RES_WF_DETAIL',          'PLATFORM', NOW()),
('RR_CR_WF_CLM',   'R_CREDIT_REVIEWER', 'RES_WF_CLAIM',           'PLATFORM', NOW()),
('RR_CR_WF_APV',   'R_CREDIT_REVIEWER', 'RES_WF_APPROVE',         'PLATFORM', NOW()),
('RR_CR_WF_REJ',   'R_CREDIT_REVIEWER', 'RES_WF_REJECT',          'PLATFORM', NOW()),
('RR_CR_WF_HIS',   'R_CREDIT_REVIEWER', 'RES_WF_HISTORY',         'PLATFORM', NOW()),
('RR_CR_NAV_L',    'R_CREDIT_REVIEWER', 'RES_NAV_LIST',            'PLATFORM', NOW());

-- ---------------------------------------------------------
-- 4.11 授信批复人员（R_CREDIT_APPROVER）: 资产投放批复
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('RR_CA_', SUBSTRING(ID, 7)), 'R_CREDIT_APPROVER', RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE WHERE ROLE_ID = 'R_CREDIT_REVIEWER';

-- ---------------------------------------------------------
-- 4.12 分行行长（R_PRESIDENT）: 报表只读 + 仪表盘
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
VALUES
('RR_PR_AUTH',     'R_PRESIDENT', 'RES_AUTH_CURRENT',       'PLATFORM', NOW()),
('RR_PR_ORG',      'R_PRESIDENT', 'RES_ORG_TREE',           'PLATFORM', NOW()),
('RR_PR_NTF_L',    'R_PRESIDENT', 'RES_SYS_NOTIFY_LIST',    'PLATFORM', NOW()),
('RR_PR_NTF_R',    'R_PRESIDENT', 'RES_SYS_NOTIFY_READ',    'PLATFORM', NOW()),
('RR_PR_PTODO',    'R_PRESIDENT', 'RES_PORTAL_TODOS',       'PLATFORM', NOW()),
('RR_PR_PNOTIFY',  'R_PRESIDENT', 'RES_PORTAL_NOTIFY',      'PLATFORM', NOW()),
('RR_PR_PCARDS',   'R_PRESIDENT', 'RES_PORTAL_CARDS',       'PLATFORM', NOW()),
('RR_PR_NAV_L',    'R_PRESIDENT', 'RES_NAV_LIST',           'PLATFORM', NOW()),
('RR_PR_RPT_Q',    'R_PRESIDENT', 'RES_RPT_DYN_QUERY',     'PLATFORM', NOW()),
('RR_PR_RPT_E',    'R_PRESIDENT', 'RES_RPT_DYN_EXPORT',    'PLATFORM', NOW()),
('RR_PR_RPT_SL',   'R_PRESIDENT', 'RES_RPT_DYN_LIST',      'PLATFORM', NOW()),
('RR_PR_RPT_SS',   'R_PRESIDENT', 'RES_RPT_DYN_SAVE',      'PLATFORM', NOW()),
('RR_PR_DASH',     'R_PRESIDENT', 'RES_RPT_FIX_DASH',      'PLATFORM', NOW()),
('RR_PR_DASH_E',   'R_PRESIDENT', 'RES_RPT_FIX_DASH_EXP',  'PLATFORM', NOW());


-- =========================================================
-- 5) PT_ROLE_BIZ_SCOPE 角色-BizType-数据范围种子数据
--    按 project_ana.md 2.1.2 权限矩阵生成
-- =========================================================

-- ---------------------------------------------------------
-- 5.1 系统管理员（R_ADMIN）: 所有 BizType = ALL
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_ADMIN_NAV',          'R_ADMIN', 'NAV',           'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_ADDRBOOK',     'R_ADMIN', 'ADDRBOOK',      'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_PRODUCT',      'R_ADMIN', 'PRODUCT',       'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_DOC',          'R_ADMIN', 'DOC',           'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_TAG',          'R_ADMIN', 'TAG',           'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_LEAD',         'R_ADMIN', 'LEAD',          'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_CUSTOMER',     'R_ADMIN', 'CUSTOMER',      'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_CUSTOMER_POOL','R_ADMIN', 'CUSTOMER_POOL', 'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_CLAIM',        'R_ADMIN', 'CLAIM',         'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_TOUCH_TASK',   'R_ADMIN', 'TOUCH_TASK',    'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_TOUCH_REPORT', 'R_ADMIN', 'TOUCH_REPORT',  'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_LOAN',         'R_ADMIN', 'LOAN',          'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_SUPPORT',      'R_ADMIN', 'SUPPORT',       'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_SUPPORT_DEPT', 'R_ADMIN', 'SUPPORT_DEPT',  'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_REPORT',       'R_ADMIN', 'REPORT',        'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_PERF_CONFIG',  'R_ADMIN', 'PERF_CONFIG',   'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_ADMIN_SYS_CONFIG',   'R_ADMIN', 'SYS_CONFIG',    'ALL', 0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.2 客户经理（R_RM）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_RM_ADDRBOOK',      'R_RM', 'ADDRBOOK',      'ALL',           0, NOW(), 'seed', 'V1 seed'),
('S_RM_PRODUCT',       'R_RM', 'PRODUCT',       'ALL',           0, NOW(), 'seed', 'V1 seed'),
('S_RM_DOC',           'R_RM', 'DOC',           'ALL',           0, NOW(), 'seed', 'V1 seed'),
('S_RM_TAG',           'R_RM', 'TAG',           'ALL',           0, NOW(), 'seed', 'V1 seed'),
('S_RM_LEAD',          'R_RM', 'LEAD',          'SELF_CREATED',  0, NOW(), 'seed', 'V1 seed'),
('S_RM_CUSTOMER_POOL', 'R_RM', 'CUSTOMER_POOL', 'ALL',           0, NOW(), 'seed', 'V1 seed'),
('S_RM_CLAIM',         'R_RM', 'CLAIM',         'ORG',           0, NOW(), 'seed', 'V1 seed'),
('S_RM_TOUCH_TASK',    'R_RM', 'TOUCH_TASK',    'SELF_ASSIGNED', 0, NOW(), 'seed', 'V1 seed'),
('S_RM_LOAN',          'R_RM', 'LOAN',          'SELF_CREATED',  0, NOW(), 'seed', 'V1 seed'),
('S_RM_SUPPORT',       'R_RM', 'SUPPORT',       'SELF_CREATED',  0, NOW(), 'seed', 'V1 seed'),
('S_RM_REPORT',        'R_RM', 'REPORT',        'SELF',          0, NOW(), 'seed', 'V1 seed'),
('S_RM_NAV',           'R_RM', 'NAV',           'ALL',           0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.3 经营机构负责人（R_BRANCH_MGR）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_BM_LEAD',         'R_BRANCH_MGR', 'LEAD',         'ORG_SUBTREE', 0, NOW(), 'seed', 'V1 seed'),
('S_BM_CUSTOMER',     'R_BRANCH_MGR', 'CUSTOMER',     'ORG_SUBTREE', 0, NOW(), 'seed', 'V1 seed'),
('S_BM_TOUCH_REPORT', 'R_BRANCH_MGR', 'TOUCH_REPORT', 'ORG_SUBTREE', 0, NOW(), 'seed', 'V1 seed'),
('S_BM_SUPPORT',      'R_BRANCH_MGR', 'SUPPORT',      'ORG_SUBTREE', 0, NOW(), 'seed', 'V1 seed'),
('S_BM_REPORT',       'R_BRANCH_MGR', 'REPORT',       'ORG_SUBTREE', 0, NOW(), 'seed', 'V1 seed'),
('S_BM_NAV',          'R_BRANCH_MGR', 'NAV',          'ALL',         0, NOW(), 'seed', 'V1 seed'),
('S_BM_ADDRBOOK',     'R_BRANCH_MGR', 'ADDRBOOK',     'ALL',         0, NOW(), 'seed', 'V1 seed'),
('S_BM_PRODUCT',      'R_BRANCH_MGR', 'PRODUCT',      'ALL',         0, NOW(), 'seed', 'V1 seed'),
('S_BM_DOC',          'R_BRANCH_MGR', 'DOC',          'ALL',         0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.4 公司部人员（R_CORP_DEPT）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_CD_TAG',          'R_CORP_DEPT', 'TAG',           'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_CD_LEAD',         'R_CORP_DEPT', 'LEAD',          'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_CD_CUSTOMER',     'R_CORP_DEPT', 'CUSTOMER',      'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_CD_TOUCH_REPORT', 'R_CORP_DEPT', 'TOUCH_REPORT',  'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_CD_LOAN',         'R_CORP_DEPT', 'LOAN',          'WORKFLOW_PARTICIPANT',   0, NOW(), 'seed', 'V1 seed'),
('S_CD_REPORT',       'R_CORP_DEPT', 'REPORT',        'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_CD_NAV',          'R_CORP_DEPT', 'NAV',           'ALL',                   0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.5 零售部人员（R_RETAIL_DEPT）: 与公司部相同
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_RD_TAG',          'R_RETAIL_DEPT', 'TAG',           'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_RD_LEAD',         'R_RETAIL_DEPT', 'LEAD',          'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_RD_CUSTOMER',     'R_RETAIL_DEPT', 'CUSTOMER',      'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_RD_TOUCH_REPORT', 'R_RETAIL_DEPT', 'TOUCH_REPORT',  'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_RD_LOAN',         'R_RETAIL_DEPT', 'LOAN',          'WORKFLOW_PARTICIPANT',   0, NOW(), 'seed', 'V1 seed'),
('S_RD_REPORT',       'R_RETAIL_DEPT', 'REPORT',        'ALL',                   0, NOW(), 'seed', 'V1 seed'),
('S_RD_NAV',          'R_RETAIL_DEPT', 'NAV',           'ALL',                   0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.6 中后台员工-资财（R_BACK_FINANCE）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_BF_PERF_CONFIG', 'R_BACK_FINANCE', 'PERF_CONFIG', 'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_BF_REPORT',      'R_BACK_FINANCE', 'REPORT',      'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_BF_NAV',         'R_BACK_FINANCE', 'NAV',         'ALL', 0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.7 中后台员工-科技（R_BACK_TECH）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_BT_NAV',        'R_BACK_TECH', 'NAV',        'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_BT_DOC',        'R_BACK_TECH', 'DOC',        'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_BT_SYS_CONFIG', 'R_BACK_TECH', 'SYS_CONFIG', 'ALL', 0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.8 中场支持部门秘书（R_SUPPORT_SEC）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_SS_SUPPORT_DEPT', 'R_SUPPORT_SEC', 'SUPPORT_DEPT', 'ORG',  0, NOW(), 'seed', 'V1 seed'),
('S_SS_PRODUCT',      'R_SUPPORT_SEC', 'PRODUCT',      'ALL',  0, NOW(), 'seed', 'V1 seed'),
('S_SS_NAV',          'R_SUPPORT_SEC', 'NAV',          'ALL',  0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.9 中场支持部门人员（R_SUPPORT_STAFF）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_SF_SUPPORT_DEPT', 'R_SUPPORT_STAFF', 'SUPPORT_DEPT', 'SELF_ASSIGNED', 0, NOW(), 'seed', 'V1 seed'),
('S_SF_NAV',          'R_SUPPORT_STAFF', 'NAV',          'ALL',           0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.10 授信审查人员（R_CREDIT_REVIEWER）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_CRV_LOAN', 'R_CREDIT_REVIEWER', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, NOW(), 'seed', 'V1 seed'),
('S_CRV_NAV',  'R_CREDIT_REVIEWER', 'NAV',  'ALL',                 0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.11 授信批复人员（R_CREDIT_APPROVER）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_CAP_LOAN', 'R_CREDIT_APPROVER', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, NOW(), 'seed', 'V1 seed'),
('S_CAP_NAV',  'R_CREDIT_APPROVER', 'NAV',  'ALL',                 0, NOW(), 'seed', 'V1 seed');

-- ---------------------------------------------------------
-- 5.12 分行行长（R_PRESIDENT）
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
('S_PR_REPORT', 'R_PRESIDENT', 'REPORT', 'ALL', 0, NOW(), 'seed', 'V1 seed'),
('S_PR_NAV',    'R_PRESIDENT', 'NAV',    'ALL', 0, NOW(), 'seed', 'V1 seed');


-- =========================================================
-- 6) 其他模块初始数据
-- =========================================================

-- ---------------------------------------------------------
-- 6.1 sys_control 初始维度（绩效引擎）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_control (id, scope_dim, latest_data_date, current_version, is_valid)
VALUES
('SC_INIT_EMP',  'EMP',  '1970-01-01', NULL, 0),
('SC_INIT_ORG',  'ORG',  '1970-01-01', NULL, 0),
('SC_INIT_CUST', 'CUST', '1970-01-01', NULL, 0);

-- ---------------------------------------------------------
-- 6.2 sys_config_kv 推荐配置（系统治理中心）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_config_kv (id, config_key, config_value, value_type, status, remark, created_by)
VALUES
('CFG_SQL_PROBE_MAX_LIMIT',       'SQL_PROBE_MAX_LIMIT',       '2000',                        'NUMBER', 'ACTIVE', 'SQL探查LIMIT上限',    'seed'),
('CFG_SQL_PROBE_MAX_CONCURRENCY', 'SQL_PROBE_MAX_CONCURRENCY', '5',                           'NUMBER', 'ACTIVE', 'SQL探查并发上限',     'seed'),
('CFG_SQL_PROBE_WHITELIST_JSON',  'SQL_PROBE_WHITELIST_JSON',  '{"schemas":[],"tables":[]}',  'JSON',   'ACTIVE', 'SQL探查白名单',       'seed'),
('CFG_AUDIT_EXPORT_MAX_DAYS',     'AUDIT_EXPORT_MAX_DAYS',     '31',                          'NUMBER', 'ACTIVE', '审计导出最大天数',    'seed'),
('CFG_AUDIT_EXPORT_MAX_ROWS',     'AUDIT_EXPORT_MAX_ROWS',     '200000',                      'NUMBER', 'ACTIVE', '审计导出最大行数',    'seed');

-- ---------------------------------------------------------
-- 6.3 网址导航初始数据
-- ---------------------------------------------------------
INSERT IGNORE INTO portal_nav (id, nav_name, nav_url, nav_icon, nav_category, sort_order, status, created_by)
VALUES
('NAV001', 'CCRM系统',      'https://ccrm.bank.com',   'icon-ccrm',   '总行系统', 1, 'ACTIVE', 'SYSTEM'),
('NAV002', 'PCRM系统',      'https://pcrm.bank.com',   'icon-pcrm',   '总行系统', 2, 'ACTIVE', 'SYSTEM'),
('NAV003', '网银系统',      'https://ebank.bank.com',  'icon-ebank',  '电子渠道', 3, 'ACTIVE', 'SYSTEM'),
('NAV004', '信贷管理系统',  'https://credit.bank.com', 'icon-credit', '风险管理', 4, 'ACTIVE', 'SYSTEM'),
('NAV005', 'OA系统',        'https://oa.bank.com',     'icon-oa',     '办公系统', 5, 'ACTIVE', 'SYSTEM');

-- ---------------------------------------------------------
-- 6.4 产品资料初始数据（联调最小集）
-- ---------------------------------------------------------
INSERT IGNORE INTO product_info (id, product_code, product_name, product_category, description, status, created_by, deleted)
VALUES
('PROD001', 'TBK_DEPOSIT',     '交易银行-结构性存款', '交易银行', '结构性存款产品介绍',   'ACTIVE', 'SYSTEM', 0),
('PROD002', 'TBK_SUPPLY_CHAIN','交易银行-供应链金融', '交易银行', '供应链金融产品介绍',   'ACTIVE', 'SYSTEM', 0),
('PROD003', 'FM_BOND',         '金融市场-债券承销',   '金融市场', '债券承销服务介绍',     'ACTIVE', 'SYSTEM', 0),
('PROD004', 'CORP_LOAN',       '公司-流动资金贷款',   '公司银行', '流动资金贷款产品',     'ACTIVE', 'SYSTEM', 0);

-- ---------------------------------------------------------
-- 6.5 工作日历样例数据（2026年节假日示例）
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_calendar_day (day, is_workday, remark, created_by)
VALUES
('2026-01-01', 0, '元旦',     'seed'),
('2026-01-02', 0, '元旦假期', 'seed'),
('2026-01-03', 0, '元旦假期', 'seed'),
('2026-05-01', 0, '劳动节',   'seed'),
('2026-05-02', 0, '劳动节假期', 'seed'),
('2026-05-03', 0, '劳动节假期', 'seed'),
('2026-10-01', 0, '国庆节',   'seed'),
('2026-10-02', 0, '国庆节假期', 'seed'),
('2026-10-03', 0, '国庆节假期', 'seed'),
('2026-10-04', 0, '国庆节假期', 'seed'),
('2026-10-05', 0, '国庆节假期', 'seed'),
('2026-10-06', 0, '国庆节假期', 'seed'),
('2026-10-07', 0, '国庆节假期', 'seed');

-- ============================================================================
-- END OF seed-v1.sql
-- ============================================================================
