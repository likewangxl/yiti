-- =====================================================================
-- 指标库数据清理脚本：指标定义（主表）+ 指标引用关系（副表）
-- ⚠️ 不可逆，执行前务必 mysqldump 备份！
-- ⚠️ 清空指标库会让依赖指标编号的 KPI方案项 / 指标结果 / KPI得分 失去关联，
--    建议先执行结果与方案清理脚本，再清指标库。
-- 用法：mysql -uroot -p <库名> < 本文件
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ===== 1) 指标库（主表：指标定义 + 副表：指标引用/口径关系）=====
TRUNCATE TABLE PERF_METRIC_REF;
TRUNCATE TABLE PERF_METRIC_DEF;

-- ===== 2) 指标计算执行记录（与指标库配套的运行日志/任务，按需保留可注释掉）=====
TRUNCATE TABLE PERF_METRIC_CALC_LOG;
TRUNCATE TABLE PERF_METRIC_CALC_TASK;

SET FOREIGN_KEY_CHECKS = 1;
-- ===== 指标库数据清理结束 =====
