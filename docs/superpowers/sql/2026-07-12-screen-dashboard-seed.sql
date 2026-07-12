-- 大屏种子配置（2026-07-12）：5 数据源 + 3 屏 + 区块 + 3 示例点位
-- 目标库 yiti 手工执行（onepl_test_bootstrap 不需要种子）。幂等：先删后插。
--
-- Step 3 前置查询真实值替换清单（2026-07-12 现场查得，PERF_METRIC_DEF 共 182 行，EMP/ORG 各 87 条 ACTIVE，
-- 并非空表，故未跳过任何数据源行）：
--   EMP_M1 = metric_code M_0002 / metric_name 一般性存款月均余额较上月-员工 / val_slot 51
--   EMP_M2 = metric_code M_0003 / metric_name 一般性存款季日均余额-员工   / val_slot 11
--   ORG_M1 = metric_code M_0265 / metric_name 一般性存款月均余额-机构     / val_slot 12
--   ORG_M2 = metric_code M_0266 / metric_name 一般性存款月均余额较上月-机构 / val_slot 51
--   ORG1 = org_code 105 / 延兴门西路支行（西安，与既有示例坐标 108.948024,34.263161 一致）
--   ORG2 = org_code 128 / 宝鸡分行     （宝鸡，坐标 107.237743,34.361979）
--   ORG3 = org_code 191 / 渭南分行     （渭南，坐标 109.502882,34.499381）
--   以上 3 个机构经核实（EXT_ORG_INFO JOIN ORG_INDEX_RESULT）均存在指标结果行，可正常联查；
--   现场 val_12（ORG_M1 槽位）在已导入数据中为 NULL、val_51 槽位为占位测试值 10000（非空但非真实业务值），
--   RANK_LIST/PIE_SHARE 区块渲染结构正确，数值待后续指标计算任务刷新即可，不影响脚本可执行与屏可渲染。

DELETE FROM RPT_SCREEN_BLOCK WHERE screen_id IN (9101,9102,9103);
DELETE FROM RPT_SCREEN WHERE id IN (9101,9102,9103);
DELETE FROM RPT_SCREEN_DATASOURCE WHERE id BETWEEN 9001 AND 9005;
DELETE FROM RPT_SCREEN_MAP_POINT WHERE org_code IN ('105','128','191');

-- 数据源
INSERT INTO RPT_SCREEN_DATASOURCE (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by) VALUES
(9001,'SCRDS_SEED01','员工核心指标(宽表)','TIMESERIES','WIDE_TABLE',
 '{"table":"EMP_INDEX_RESULT","subjectCol":"emp_id","subjectParam":"empId","metrics":[{"metricCode":"M_0002","metricName":"一般性存款月均余额较上月-员工","slot":51},{"metricCode":"M_0003","metricName":"一般性存款季日均余额-员工","slot":11}]}',
 '["LATEST","LAST_10D","LAST_1M","LAST_6M_EOM"]','ACTIVE','种子','SEED'),
(9002,'SCRDS_SEED02','机构核心指标(宽表)','TIMESERIES','WIDE_TABLE',
 '{"table":"ORG_INDEX_RESULT","subjectCol":"org_code","subjectParam":"orgCode","metrics":[{"metricCode":"M_0265","metricName":"一般性存款月均余额-机构","slot":12},{"metricCode":"M_0266","metricName":"一般性存款月均余额较上月-机构","slot":51}]}',
 '["LATEST","LAST_10D","LAST_1M","LAST_6M_EOM"]','ACTIVE','种子','SEED'),
(9003,'SCRDS_SEED03','个人KPI(月度)','TIMESERIES','KPI_RESULT',
 '{"cycleType":"MONTHLY"}','["LATEST","LAST_6M_EOM"]','ACTIVE','种子','SEED'),
(9004,'SCRDS_SEED04','全省机构排名(存款)','SINGLE','CUSTOM_SQL',
 '{"sql":"SELECT r.org_code, o.org_name, r.val_12 AS 指标值, RANK() OVER (ORDER BY r.val_12 DESC) AS 排名 FROM ORG_INDEX_RESULT r JOIN EXT_ORG_INFO o ON o.org_code = r.org_code WHERE r.data_date = (SELECT MAX(data_date) FROM ORG_INDEX_RESULT) ORDER BY r.val_12 DESC","dateCol":null}',
 NULL,'ACTIVE','种子','SEED'),
(9005,'SCRDS_SEED05','在途流程概览','SINGLE','CUSTOM_SQL',
 '{"sql":"SELECT ''在途任务'' AS 名称, COUNT(*) AS 数量 FROM ACT_RU_TASK","dateCol":null}',
 NULL,'ACTIVE','种子','SEED');

-- 屏
INSERT INTO RPT_SCREEN (id, screen_code, screen_name, view_level, status, created_by) VALUES
(9101,'SCR_PROVINCE','省分行经营总览','PROVINCE','ACTIVE','SEED'),
(9102,'SCR_BRANCH','支行经营详情','BRANCH','ACTIVE','SEED'),
(9103,'SCR_PERSON','个人业绩详情','PERSON','ACTIVE','SEED');

-- 省分行总览：LEFT 指标卡+趋势，RIGHT 排名（跳支行）+流程（MAIN=地图，不配区块）
INSERT INTO RPT_SCREEN_BLOCK (screen_id, region, row_no, col_no, width_pct, height_pct, component_type, bind_json, style_json, drill_json) VALUES
(9101,'LEFT',1,1,100,40,'METRIC_CARD',
 '{"dsId":9002,"period":"LATEST","items":[{"col":"一般性存款月均余额-机构","label":"一般性存款月均余额-机构"},{"col":"一般性存款月均余额较上月-机构","label":"一般性存款月均余额较上月-机构"}]}',
 '{"title":"全省核心指标","unit":"","decimals":2,"refreshSec":60}',
 '{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M","LAST_6M_EOM"]}'),
(9101,'LEFT',2,1,100,60,'LINE_TREND',
 '{"dsId":9002,"period":"LAST_1M","items":[{"col":"一般性存款月均余额-机构","label":"一般性存款月均余额-机构"}]}',
 '{"title":"一般性存款月均余额-机构趋势(近1月)","decimals":2,"refreshSec":300}', '{}'),
(9101,'RIGHT',1,1,100,60,'RANK_LIST',
 '{"dsId":9004,"period":"LATEST","nameCol":"org_name","valueCol":"指标值"}',
 '{"title":"支行排名","decimals":2,"refreshSec":300}',
 '{"jump":{"targetScreenCode":"SCR_BRANCH","params":{"orgCode":"$col:org_code"}}}'),
(9101,'RIGHT',2,1,100,40,'FLOW_STATUS',
 '{"dsId":9005,"period":"LATEST","nameCol":"名称","valueCol":"数量"}',
 '{"title":"流程概览","refreshSec":120}', '{}');

-- 支行详情：核心指标卡 + 趋势 + 排名列表（占位：人员列表一期用机构排名 SQL 同款思路，管理员可改）
INSERT INTO RPT_SCREEN_BLOCK (screen_id, region, row_no, col_no, width_pct, height_pct, component_type, bind_json, style_json, drill_json) VALUES
(9102,'MAIN',1,1,100,30,'METRIC_CARD',
 '{"dsId":9002,"period":"LATEST","items":[{"col":"一般性存款月均余额-机构","label":"一般性存款月均余额-机构"},{"col":"一般性存款月均余额较上月-机构","label":"一般性存款月均余额较上月-机构"}]}',
 '{"title":"本支行核心指标","decimals":2,"refreshSec":60}',
 '{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M","LAST_6M_EOM"]}'),
(9102,'MAIN',2,1,100,40,'LINE_TREND',
 '{"dsId":9002,"period":"LAST_6M_EOM","items":[{"col":"一般性存款月均余额-机构","label":"一般性存款月均余额-机构"},{"col":"一般性存款月均余额较上月-机构","label":"一般性存款月均余额较上月-机构"}]}',
 '{"title":"核心指标趋势(近6个月末)","decimals":2,"refreshSec":300}', '{}'),
(9102,'MAIN',3,1,100,30,'PIE_SHARE',
 '{"dsId":9004,"period":"LATEST","nameCol":"org_name","valueCol":"指标值"}',
 '{"title":"全省份额占比","refreshSec":300}', '{}');

-- 个人详情：KPI 卡 + KPI 趋势 + 个人指标卡
INSERT INTO RPT_SCREEN_BLOCK (screen_id, region, row_no, col_no, width_pct, height_pct, component_type, bind_json, style_json, drill_json) VALUES
(9103,'MAIN',1,1,50,30,'METRIC_CARD',
 '{"dsId":9003,"period":"LATEST","items":[{"col":"KPI总分","label":"KPI总分"}]}',
 '{"title":"最新KPI","decimals":2,"refreshSec":300}',
 '{"drillEnabled":true,"drillPeriods":["LAST_6M_EOM"]}'),
(9103,'MAIN',1,2,50,30,'METRIC_CARD',
 '{"dsId":9001,"period":"LATEST","items":[{"col":"一般性存款月均余额较上月-员工","label":"一般性存款月均余额较上月-员工"},{"col":"一般性存款季日均余额-员工","label":"一般性存款季日均余额-员工"}]}',
 '{"title":"个人核心指标","decimals":2,"refreshSec":300}',
 '{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M"]}'),
(9103,'MAIN',2,1,100,70,'LINE_TREND',
 '{"dsId":9003,"period":"LAST_6M_EOM","items":[{"col":"KPI总分","label":"KPI总分"}]}',
 '{"title":"KPI 历史趋势","decimals":2,"refreshSec":300}', '{}');

-- 示例点位（坐标：西安/宝鸡/渭南市中心附近，管理员后台可改；org_code 为 EXT_ORG_INFO 真实机构号）
INSERT INTO RPT_SCREEN_MAP_POINT (org_code, org_name, lng, lat, target_screen_code, status) VALUES
('105','延兴门西路支行',108.948024,34.263161,'SCR_BRANCH','ACTIVE'),
('128','宝鸡分行',107.237743,34.361979,'SCR_BRANCH','ACTIVE'),
('191','渭南分行',109.502882,34.499381,'SCR_BRANCH','ACTIVE');
