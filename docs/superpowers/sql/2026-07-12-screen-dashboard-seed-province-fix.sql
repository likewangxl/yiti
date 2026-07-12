-- 大屏种子修复（2026-07-12）：省级总览屏 LEFT 区改绑全省聚合数据源
-- 背景（最终全分支审查 Important-1）：种子里 9101（SCR_PROVINCE）LEFT 区 METRIC_CARD 与
-- LINE_TREND 都绑 dsId=9002（ORG_INDEX_RESULT 宽表，subjectParam=orgCode），但省屏是顶层入口
-- 无 orgCode 上下文 → ScreenQueryEngine.ctxParam 抛 RPT-43008。且语义上省屏该展示全省聚合而非
-- 单机构。本脚本仅新增 2 条数据源（9006/9007）+ 改绑 9101 的 2 个 LEFT 区块，不改动
-- 2026-07-12-screen-dashboard-seed.sql（已归档，禁止改动）。
--
-- 现状核查（yiti 库 SELECT config_json FROM RPT_SCREEN_DATASOURCE WHERE id=9002）：
--   S1/N1 = val_slot 12 / metricName 一般性存款月均余额-机构
--   S2/N2 = val_slot 51 / metricName 一般性存款月均余额较上月-机构
-- 目标库 yiti 手工执行。幂等：先删后插 / 先删后改。

DELETE FROM RPT_SCREEN_DATASOURCE WHERE id IN (9006,9007);

-- 9006：单值省级卡片源（SINGLE/CUSTOM_SQL，无占位参数，MAX(data_date) 取最新一日全省聚合，
-- 与 9004 排名源同款“无 dateCol 占位”写法，不依赖“今天”是否已导入数据）
INSERT INTO RPT_SCREEN_DATASOURCE (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by) VALUES
(9006,'SCRDS_SEED06','全省核心指标(聚合单值)','SINGLE','CUSTOM_SQL',
 '{"sql":"SELECT SUM(val_12) AS `一般性存款月均余额-机构`, SUM(val_51) AS `一般性存款月均余额较上月-机构` FROM ORG_INDEX_RESULT WHERE data_date = (SELECT MAX(data_date) FROM ORG_INDEX_RESULT)","dateCol":null}',
 NULL,'ACTIVE','终审 Important-1 修复：省屏全省聚合单值源','SEED');

-- 9007：时序省级趋势源（TIMESERIES/CUSTOM_SQL，dateCol=data_date，按日聚合全省）；
-- time_param_json 只声明 CUSTOM_SQL 查询体实际支持语义的周期——LAST_6M_EOM 的“仅月末时点”
-- 过滤只有 buildWideTableQuery 会追加，buildCustomQuery 不会，故不声明 LAST_6M_EOM 避免误用
INSERT INTO RPT_SCREEN_DATASOURCE (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by) VALUES
(9007,'SCRDS_SEED07','全省核心指标趋势(聚合时序)','TIMESERIES','CUSTOM_SQL',
 '{"sql":"SELECT data_date, SUM(val_12) AS `一般性存款月均余额-机构`, SUM(val_51) AS `一般性存款月均余额较上月-机构` FROM ORG_INDEX_RESULT WHERE data_date BETWEEN #{dateFrom} AND #{dateTo} GROUP BY data_date ORDER BY data_date","dateCol":"data_date"}',
 '["LATEST","LAST_10D","LAST_1M"]','ACTIVE','终审 Important-1 修复：省屏全省聚合时序源','SEED');

-- 9101 LEFT METRIC_CARD 改绑 9006（单值源不可钻取，drill_json 清空——下方趋势块已覆盖趋势需求）
UPDATE RPT_SCREEN_BLOCK
SET bind_json = '{"dsId":9006,"period":"LATEST","items":[{"col":"一般性存款月均余额-机构","label":"一般性存款月均余额-机构"},{"col":"一般性存款月均余额较上月-机构","label":"一般性存款月均余额较上月-机构"}]}',
    drill_json = '{}'
WHERE screen_id = 9101 AND region = 'LEFT' AND component_type = 'METRIC_CARD';

-- 9101 LEFT LINE_TREND 改绑 9007（period 保留 LAST_1M）
UPDATE RPT_SCREEN_BLOCK
SET bind_json = '{"dsId":9007,"period":"LAST_1M","items":[{"col":"一般性存款月均余额-机构","label":"一般性存款月均余额-机构"}]}'
WHERE screen_id = 9101 AND region = 'LEFT' AND component_type = 'LINE_TREND';
