-- 大屏画布 V2 三屏重配种子(2026-07-13,从 yiti 库重配发布后导出)
-- 覆盖:RPT_SCREEN(SCR_PROVINCE/SCR_BRANCH/SCR_PERSON 画布字段:canvas_style_json/
--   canvas_draft_json/canvas_published_json/canvas_version/publish_status/published_at/published_by)
--   + RPT_SCREEN_BLOCK(三屏全部取数配置行)+ RPT_SCREEN_DATASOURCE(三屏引用的 7 条数据源全集)
--   + RPT_SCREEN_MAP_POINT(3 条,PROVINCE 屏地图点位)+ RPT_SCREEN_PUBLISH_LOG(三屏发布归档)。
-- 前提:先执行 2026-07-12-screen-canvas-ddl.sql 建结构(RPT_SCREEN 增量 ALTER 字段 +
--   CREATE TABLE RPT_SCREEN_PUBLISH_LOG);【严禁执行 2026-07-12-screen-dashboard-ddl.sql】
--   (那是更早的 V1 大屏基线,含 DROP TABLE,与本脚本无关且具破坏性)。
-- 重配方式:全程经画布管理端 API(POST /api/screen/admin/canvas/save → publish),未直接 UPDATE
--   RPT_SCREEN,已走服务端乐观锁校验/审计链路;三屏均实测 GET /api/screen/view/{code} 验证
--   state=published、组件数与 bindSnapshots 一致、PROVINCE mapPoints=3。详见
--   .superpowers/sdd/canvas-task-11-report.md。
-- 注意:RPT_SCREEN/RPT_SCREEN_BLOCK/RPT_SCREEN_DATASOURCE 均为 INSERT(非 REPLACE),fresh 库
--   (刚跑完 DDL 基线,无同 id 行)可直接导入;若目标库已有同 id 行(如重复执行本脚本),需先手工
--   清理对应行或将 INSERT 改 REPLACE INTO 再执行,避免主键冲突失败。
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
