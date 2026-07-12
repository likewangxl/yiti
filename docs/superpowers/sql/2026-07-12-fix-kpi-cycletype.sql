-- ================================================================
-- 大屏数据源 9003 KPI 周期类型修正（2026-07-12，FIX-2）
-- ----------------------------------------------------------------
-- 背景：RPT_SCREEN_DATASOURCE id=9003（个人KPI）的 config_json.cycleType 原为 MONTHLY，
--   但 KPI_RESULT 库中 4353 行 cycle_type 全部为 YEARLY（MONTHLY 计数 = 0），
--   二者零交集，导致个人屏 SCR_PERSON 的 KPI 卡/趋势区块即使 empId 正确也永远返回空 rows。
--   后端引擎 ScreenQueryEngine.KPI_CYCLE_TYPES 已在 FIX-2 放开 YEARLY，本脚本把数据源配置对齐库真实口径。
--
-- 目标库：yiti（开发/生产库）。onepl_test_bootstrap 测试库无此大屏种子，无需执行。
-- 执行方式：mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-07-12-fix-kpi-cycletype.sql
--   执行后见文末 SELECT，config_json 应为 {"cycleType":"YEARLY"}。
-- 幂等：WHERE 精确匹配 id=9003 且当前值为 MONTHLY，可安全重复执行（第二次起匹配 0 行不再改动）。
-- 仅 UPDATE，无 DDL、无 DROP/DELETE/INSERT；不涉及 Flyway。
--
-- 回滚（如需还原为 MONTHLY）：
--   UPDATE RPT_SCREEN_DATASOURCE SET config_json = '{"cycleType":"MONTHLY"}'
--    WHERE id = 9003 AND config_json = '{"cycleType":"YEARLY"}';
-- ================================================================

UPDATE RPT_SCREEN_DATASOURCE
   SET config_json = '{"cycleType":"YEARLY"}'
 WHERE id = 9003
   AND source_kind = 'KPI_RESULT'
   AND config_json = '{"cycleType":"MONTHLY"}';

-- 验证：期望 config_json = {"cycleType":"YEARLY"}
SELECT id, ds_name, source_kind, config_json
  FROM RPT_SCREEN_DATASOURCE
 WHERE id = 9003;
