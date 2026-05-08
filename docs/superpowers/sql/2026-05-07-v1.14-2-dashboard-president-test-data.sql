-- ============================================================
-- V1.14 # 2 分行行长仪表盘 5 项 KPI 卡测试数据
-- ============================================================
-- 目标库: yiti（application.yml 当前连接库；非生产 onepl）
-- 目的: 让 GET /api/reports/dashboard/president?orgCode=0000&dataDate=2026-04-22
--       返回 5 项 KPI 卡真实值，前端 Dashboard.vue 直接渲染
--
-- 执行: mysql -u root -pdjdev -h localhost yiti < docs/superpowers/sql/2026-05-07-v1.14-2-dashboard-president-test-data.sql
--
-- 备份: 执行前请先 mysqldump 备份到 docs/superpowers/sql/backup/2026-05-07-pre-v1.14-2-test-data.sql
--       影响表: SYS_CONTROL / PERF_METRIC_DEF / EXT_ORG_INFO / ORG_INDEX_RESULT / CUST_MASTER / CUST_INDEX_RESULT
--
-- 幂等: 全部 INSERT 用 ON DUPLICATE KEY UPDATE，可重复执行
--
-- ----------------------------------------------------------------
-- 执行前 yiti 库现状（2026-05-07 探查）：
--   - SYS_CONTROL: 3 行 (EMP/ORG/CUST), latest_data_date='2026-04-22', version='V1'
--   - PERF_METRIC_DEF: ORG slot 1/2/3/4 已占（M0005/M0006/M2002/M0009）
--                      CUST slot 1/2 已占（M3001/M3002）
--                      → 本脚本注册 8 个新 metric_code 用 ORG slot 5-10 + CUST slot 3-4
--   - ORG_INDEX_RESULT: 2 行 (BJ_CY/SH_PD)，缺 0000/HQ/BJ/SH
--   - EXT_ORG_INFO: 5 行 (HQ/BJ/SH/BJ_CY/SH_PD)，缺 0000（前端默认 orgCode）
--   - CUST_MASTER: 8 行（不阻塞，但 Top 客户排序可能不全）
-- ----------------------------------------------------------------

-- ================================================================
-- Section 1: PERF_METRIC_DEF — V1.14 # 2 注册 8 个新 metric_code
-- ================================================================
-- 5 项 KPI 卡 (ORG slot 5-9) + 1 项排行榜 (ORG slot 10) + 2 项客户贡献 (CUST slot 3-4)
INSERT INTO PERF_METRIC_DEF
    (id, metric_code, metric_name, base_dim, metric_level, calc_freq, calc_mode,
     val_slot, unit, decimal_places, deleted, status, created_by, created_time)
VALUES
('M_DEP_BAL_ORG',              'DEP_BAL_ORG',              '机构存款日均余额',     'ORG',  1, 'DAY',   'AUTO',  5, '亿元', 2, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_LOAN_BAL_ORG',             'LOAN_BAL_ORG',             '机构贷款余额',         'ORG',  1, 'DAY',   'AUTO',  6, '亿元', 2, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_NPL_RATIO_ORG',            'NPL_RATIO_ORG',            '机构不良贷款率',       'ORG',  1, 'DAY',   'AUTO',  7, '%',    2, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_FEE_INCOME_ORG_MONTH',     'FEE_INCOME_ORG_MONTH',     '机构本月中间业务收入', 'ORG',  1, 'MONTH', 'AUTO',  8, '万元', 2, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_NEW_VALID_CUST_ORG_MONTH', 'NEW_VALID_CUST_ORG_MONTH', '机构本月新增有效客户', 'ORG',  1, 'MONTH', 'AUTO',  9, '',     0, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_KPI_TOTAL_SCORE_ORG',      'KPI_TOTAL_SCORE_ORG',      '机构KPI总分',          'ORG',  1, 'MONTH', 'AUTO', 10, '分',   1, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_AUM_TOTAL_CUST',           'AUM_TOTAL_CUST',           '客户AUM',              'CUST', 1, 'DAY',   'AUTO',  3, '万元', 2, 0, 'ACTIVE', 'seed-v1.14-2', NOW()),
('M_PROFIT_CONTRIB_CUST',      'PROFIT_CONTRIB_CUST',      '客户利润贡献',         'CUST', 1, 'DAY',   'AUTO',  4, '万元', 2, 0, 'ACTIVE', 'seed-v1.14-2', NOW())
ON DUPLICATE KEY UPDATE
    metric_name = VALUES(metric_name),
    val_slot    = VALUES(val_slot),
    unit        = VALUES(unit),
    status      = 'ACTIVE',
    deleted     = 0,
    updated_by  = 'seed-v1.14-2',
    updated_time = NOW();

-- ================================================================
-- Section 2: EXT_ORG_INFO — 补 0000（前端 mock 默认 orgCode）
-- ================================================================
INSERT INTO EXT_ORG_INFO
    (ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID, ORGAN_STATE,
     ADM_DIVISION_CODE, ADM_DIVISION_NAME, CREATE_TIME, CREATE_USER)
VALUES
('0000', '总行(默认机构)', 1, NULL, 0, '110000', '北京', NOW(), 'seed-v1.14-2')
ON DUPLICATE KEY UPDATE
    ORG_NAME    = VALUES(ORG_NAME),
    ORG_LEVEL   = VALUES(ORG_LEVEL),
    ORGAN_STATE = 0;

-- ================================================================
-- Section 3: ORG_INDEX_RESULT — 5 项 KPI 卡当日值
-- ================================================================
-- slot 映射: val_5=DEP_BAL_ORG / val_6=LOAN_BAL_ORG / val_7=NPL_RATIO_ORG
--           val_8=FEE_INCOME_ORG_MONTH / val_9=NEW_VALID_CUST_ORG_MONTH / val_10=KPI_TOTAL_SCORE_ORG

-- 3.1 当日值 (data_date='2026-04-22'，与前端 mock 默认日期一致)
INSERT INTO ORG_INDEX_RESULT
    (org_code, data_date, version, val_5, val_6, val_7, val_8, val_9, val_10)
VALUES
('0000', '2026-04-22', 'V1', 418.6000, 312.4000, 1.4200, 2840.0000, 428.0000, 88.5000),
('HQ',   '2026-04-22', 'V1', 418.6000, 312.4000, 1.4200, 2840.0000, 428.0000, 88.5000),
('BJ',   '2026-04-22', 'V1', 200.0000, 150.0000, 1.5000, 1200.0000, 200.0000, 86.0000),
('SH',   '2026-04-22', 'V1', 218.6000, 162.4000, 1.3500, 1640.0000, 228.0000, 90.0000)
ON DUPLICATE KEY UPDATE
    val_5  = VALUES(val_5),  val_6  = VALUES(val_6),  val_7  = VALUES(val_7),
    val_8  = VALUES(val_8),  val_9  = VALUES(val_9),  val_10 = VALUES(val_10);

-- 3.2 月初日值 (V1.14 # 2 buildStats 环比基准, 2026-04-22.withDayOfMonth(1) = 2026-04-01)
--    trend 计算: (418.6 - 405.4) / 405.4 = +3.26% → "↑ 较月初 +3.3%" type=up
INSERT INTO ORG_INDEX_RESULT
    (org_code, data_date, version, val_5, val_6, val_7, val_8, val_9)
VALUES
('0000', '2026-04-01', 'V1', 405.4000, 306.0000, 1.5000, 2528.0000, 363.0000),
('HQ',   '2026-04-01', 'V1', 405.4000, 306.0000, 1.5000, 2528.0000, 363.0000)
ON DUPLICATE KEY UPDATE
    val_5 = VALUES(val_5), val_6 = VALUES(val_6), val_7 = VALUES(val_7),
    val_8 = VALUES(val_8), val_9 = VALUES(val_9);

-- 3.3 12 月趋势数据 (buildTrend 用 DEP_BAL_ORG=val_5, LOAN_BAL_ORG=val_6 12 个月末值)
INSERT INTO ORG_INDEX_RESULT
    (org_code, data_date, version, val_5, val_6)
VALUES
('0000', '2025-05-31', 'V1', 350.0000, 270.0000),
('0000', '2025-06-30', 'V1', 360.0000, 275.0000),
('0000', '2025-07-31', 'V1', 370.0000, 280.0000),
('0000', '2025-08-31', 'V1', 380.0000, 285.0000),
('0000', '2025-09-30', 'V1', 388.0000, 290.0000),
('0000', '2025-10-31', 'V1', 393.0000, 295.0000),
('0000', '2025-11-30', 'V1', 398.0000, 298.0000),
('0000', '2025-12-31', 'V1', 402.0000, 300.0000),
('0000', '2026-01-31', 'V1', 405.0000, 303.0000),
('0000', '2026-02-28', 'V1', 410.0000, 306.0000),
('0000', '2026-03-31', 'V1', 414.0000, 309.0000)
-- 4 月最新值已在 3.1 填了
ON DUPLICATE KEY UPDATE
    val_5 = VALUES(val_5), val_6 = VALUES(val_6);

-- 3.4 兜底：今天 + 本月月初（防止前端切换到默认 today 后没数据）
INSERT INTO ORG_INDEX_RESULT
    (org_code, data_date, version, val_5, val_6, val_7, val_8, val_9, val_10)
VALUES
('0000', '2026-05-07', 'V1', 422.0000, 315.0000, 1.4000, 2900.0000, 450.0000, 89.0000),
('HQ',   '2026-05-07', 'V1', 422.0000, 315.0000, 1.4000, 2900.0000, 450.0000, 89.0000),
('0000', '2026-05-01', 'V1', 418.0000, 312.0000, 1.4500, 2850.0000, 430.0000, NULL),
('HQ',   '2026-05-01', 'V1', 418.0000, 312.0000, 1.4500, 2850.0000, 430.0000, NULL)
ON DUPLICATE KEY UPDATE
    val_5 = VALUES(val_5), val_6 = VALUES(val_6), val_7 = VALUES(val_7),
    val_8 = VALUES(val_8), val_9 = VALUES(val_9), val_10 = VALUES(val_10);

-- ================================================================
-- Section 4: CUST_MASTER — 10 个测试客户（Top 10 客户贡献用）
-- ================================================================
-- cust_no / cust_name 都是 UK，全部加 'TEST_RPT_' 前缀防生产数据冲突
INSERT INTO CUST_MASTER
    (id, cust_no, cust_name, status, deleted, created_time)
VALUES
('TEST_RPT_C001', 'TEST_RPT_C001', 'TEST_RPT_中国石化集团',     'ACTIVE', 0, NOW()),
('TEST_RPT_C002', 'TEST_RPT_C002', 'TEST_RPT_中国移动通信',     'ACTIVE', 0, NOW()),
('TEST_RPT_C003', 'TEST_RPT_C003', 'TEST_RPT_国家电网',         'ACTIVE', 0, NOW()),
('TEST_RPT_C004', 'TEST_RPT_C004', 'TEST_RPT_中国工商银行',     'ACTIVE', 0, NOW()),
('TEST_RPT_C005', 'TEST_RPT_C005', 'TEST_RPT_中国建设银行',     'ACTIVE', 0, NOW()),
('TEST_RPT_C006', 'TEST_RPT_C006', 'TEST_RPT_中国农业银行',     'ACTIVE', 0, NOW()),
('TEST_RPT_C007', 'TEST_RPT_C007', 'TEST_RPT_中国银行',         'ACTIVE', 0, NOW()),
('TEST_RPT_C008', 'TEST_RPT_C008', 'TEST_RPT_招商银行',         'ACTIVE', 0, NOW()),
('TEST_RPT_C009', 'TEST_RPT_C009', 'TEST_RPT_中国平安保险',     'ACTIVE', 0, NOW()),
('TEST_RPT_C010', 'TEST_RPT_C010', 'TEST_RPT_中国人寿保险',     'ACTIVE', 0, NOW())
ON DUPLICATE KEY UPDATE
    cust_name    = VALUES(cust_name),
    status       = 'ACTIVE',
    deleted      = 0,
    updated_time = NOW();

-- ================================================================
-- Section 5: CUST_INDEX_RESULT — 10 个客户的 AUM/利润贡献
-- ================================================================
-- slot 映射: val_3=AUM_TOTAL_CUST / val_4=PROFIT_CONTRIB_CUST
INSERT INTO CUST_INDEX_RESULT
    (cust_id, data_date, version, val_3, val_4)
VALUES
('TEST_RPT_C001', '2026-04-22', 'V1', 8000.0000, 98.5000),
('TEST_RPT_C002', '2026-04-22', 'V1', 7500.0000, 92.0000),
('TEST_RPT_C003', '2026-04-22', 'V1', 7000.0000, 86.5000),
('TEST_RPT_C004', '2026-04-22', 'V1', 6500.0000, 80.0000),
('TEST_RPT_C005', '2026-04-22', 'V1', 6000.0000, 75.0000),
('TEST_RPT_C006', '2026-04-22', 'V1', 5500.0000, 70.0000),
('TEST_RPT_C007', '2026-04-22', 'V1', 5000.0000, 65.0000),
('TEST_RPT_C008', '2026-04-22', 'V1', 4500.0000, 60.0000),
('TEST_RPT_C009', '2026-04-22', 'V1', 4000.0000, 55.0000),
('TEST_RPT_C010', '2026-04-22', 'V1', 3500.0000, 50.0000),
-- 兜底：今天日期同步填一份
('TEST_RPT_C001', '2026-05-07', 'V1', 8200.0000, 99.0000),
('TEST_RPT_C002', '2026-05-07', 'V1', 7700.0000, 93.0000),
('TEST_RPT_C003', '2026-05-07', 'V1', 7200.0000, 87.0000),
('TEST_RPT_C004', '2026-05-07', 'V1', 6700.0000, 81.0000),
('TEST_RPT_C005', '2026-05-07', 'V1', 6200.0000, 76.0000),
('TEST_RPT_C006', '2026-05-07', 'V1', 5700.0000, 71.0000),
('TEST_RPT_C007', '2026-05-07', 'V1', 5200.0000, 66.0000),
('TEST_RPT_C008', '2026-05-07', 'V1', 4700.0000, 61.0000),
('TEST_RPT_C009', '2026-05-07', 'V1', 4200.0000, 56.0000),
('TEST_RPT_C010', '2026-05-07', 'V1', 3700.0000, 51.0000)
ON DUPLICATE KEY UPDATE
    val_3 = VALUES(val_3),
    val_4 = VALUES(val_4);

-- ================================================================
-- 完成 — 验证查询
-- ================================================================
SELECT '========== V1.14 # 2 测试数据加载完成 ==========' AS message;
SELECT 'PERF_METRIC_DEF V1.14 # 2 新增' AS section,
       metric_code, base_dim, val_slot, status
  FROM PERF_METRIC_DEF
 WHERE created_by = 'seed-v1.14-2'
 ORDER BY base_dim, val_slot;

SELECT 'ORG_INDEX_RESULT 0000 当日 + 月初值' AS section,
       org_code, data_date, version, val_5, val_6, val_7, val_8, val_9, val_10
  FROM ORG_INDEX_RESULT
 WHERE org_code = '0000'
   AND data_date IN ('2026-04-22','2026-04-01','2026-05-07','2026-05-01')
 ORDER BY data_date DESC;

SELECT 'CUST_INDEX_RESULT TEST_RPT_*' AS section, COUNT(*) AS cust_index_rows
  FROM CUST_INDEX_RESULT
 WHERE cust_id LIKE 'TEST_RPT_%';
