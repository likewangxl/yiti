-- =====================================================================
-- 结果数据清理脚本：员工/机构/客户维度 指标结果表 + KPI 得分表
-- 含主表 + 副表（计算日志）。⚠️ 不可逆，执行前务必 mysqldump 备份！
-- 仅清空"计算/导入产出的结果数据"，不动指标库定义、KPI方案、目标方案等配置。
-- 用法：mysql -uroot -p <库名> < 本文件
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ===== 1) 三维度指标结果宽表（员工 / 机构 / 客户）=====
TRUNCATE TABLE EMP_INDEX_RESULT;
TRUNCATE TABLE ORG_INDEX_RESULT;
TRUNCATE TABLE CUST_INDEX_RESULT;

-- ===== 2) KPI 得分（主表 PERF_KPI_SCORE + 旧得分表 KPI_RESULT + 计算日志副表）=====
TRUNCATE TABLE PERF_KPI_SCORE;
TRUNCATE TABLE KPI_RESULT;
TRUNCATE TABLE PERF_KPI_CALC_LOG;

SET FOREIGN_KEY_CHECKS = 1;
-- ===== 结果数据清理结束 =====
