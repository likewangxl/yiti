mysqldump: [Warning] Using a password on the command line interface can be insecure.
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
-- Table structure for table `cust_alloc_relation`
--

DROP TABLE IF EXISTS `cust_alloc_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_alloc_relation` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `cust_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `alloc_dim` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调整维度：RULE/ACCOUNT',
  `biz_kind` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务种类',
  `account_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号(账号维度必填)',
  `emp_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `ratio` decimal(5,2) NOT NULL COMMENT '比例(0-100)',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `end_date` date DEFAULT NULL COMMENT '失效日期',
  `source_batch_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源批次号(可选)',
  `source_process_date` date DEFAULT NULL COMMENT '来源业务日期(可选)',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_effective_date` (`effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户业绩分配关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_alloc_relation`
--

LOCK TABLES `cust_alloc_relation` WRITE;
/*!40000 ALTER TABLE `cust_alloc_relation` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_alloc_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_alloc_adjust_apply`
--

DROP TABLE IF EXISTS `perf_alloc_adjust_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_alloc_adjust_apply` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `apply_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请编号',
  `cust_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `alloc_dim` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：RULE/ACCOUNT',
  `biz_kind` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务种类',
  `account_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号(可选)',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED',
  `business_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键',
  `process_instance_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `remark` text COLLATE utf8mb4_general_ci COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分配关系调整申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_alloc_adjust_apply`
--

LOCK TABLES `perf_alloc_adjust_apply` WRITE;
/*!40000 ALTER TABLE `perf_alloc_adjust_apply` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_alloc_adjust_apply` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_alloc_adjust_item`
--

DROP TABLE IF EXISTS `perf_alloc_adjust_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_alloc_adjust_item` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '项ID',
  `apply_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `emp_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `ratio` decimal(5,2) NOT NULL COMMENT '比例(0-100)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_emp` (`apply_id`,`emp_id`),
  KEY `idx_apply_id` (`apply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分配关系调整明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_alloc_adjust_item`
--

LOCK TABLES `perf_alloc_adjust_item` WRITE;
/*!40000 ALTER TABLE `perf_alloc_adjust_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_alloc_adjust_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_import_batch`
--

DROP TABLE IF EXISTS `perf_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_import_batch` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次ID',
  `batch_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次号',
  `import_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导入类型：INDEX_RESULT/KPI_RESULT/TARGET',
  `dim` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `as_of_date` date DEFAULT NULL COMMENT 'KPI导入基准日(可空)',
  `file_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件名',
  `file_md5` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件MD5',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/SUCCESS/FAILED',
  `total_rows` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `success_rows` int NOT NULL DEFAULT '0' COMMENT '成功行数',
  `error_rows` int NOT NULL DEFAULT '0' COMMENT '失败行数',
  `error_file_object_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误明细文件ID',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效导入批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_import_batch`
--

LOCK TABLES `perf_import_batch` WRITE;
/*!40000 ALTER TABLE `perf_import_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_import_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_kpi_item`
--

DROP TABLE IF EXISTS `perf_kpi_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_kpi_item` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '项ID',
  `scheme_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案ID',
  `metric_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码(人员维度)',
  `weight` decimal(10,4) NOT NULL COMMENT '权重',
  `multiplier` decimal(10,4) NOT NULL DEFAULT '1.0000' COMMENT '加倍系数',
  `min_score` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '最低分',
  `max_score` decimal(10,4) NOT NULL DEFAULT '999999.0000' COMMENT '最高分',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheme_metric` (`scheme_id`,`metric_code`),
  KEY `idx_scheme_id` (`scheme_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='KPI方案项';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_kpi_item`
--

LOCK TABLES `perf_kpi_item` WRITE;
/*!40000 ALTER TABLE `perf_kpi_item` DISABLE KEYS */;
INSERT INTO `perf_kpi_item` VALUES ('26677d4f0a0445bba86d69a2a90d3152','e3cf2630d68b44d6952e7e79ebf52b2f','M_TST_20260429_072135_L2',60.0000,1.0000,0.0000,999999.0000,'2026-04-29 07:21:35'),('86f40fda75364224a04908c3a5089751','e3cf2630d68b44d6952e7e79ebf52b2f','M_TST_20260429_072135_L1',40.0000,1.0000,0.0000,999999.0000,'2026-04-29 07:21:35');
/*!40000 ALTER TABLE `perf_kpi_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_kpi_scheme`
--

DROP TABLE IF EXISTS `perf_kpi_scheme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_kpi_scheme` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案ID',
  `scheme_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案编码(唯一)',
  `scheme_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `cycle_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期：MONTHLY/QUARTERLY',
  `open_detail` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否向员工开放明细',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheme_code` (`scheme_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='KPI方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_kpi_scheme`
--

LOCK TABLES `perf_kpi_scheme` WRITE;
/*!40000 ALTER TABLE `perf_kpi_scheme` DISABLE KEYS */;
INSERT INTO `perf_kpi_scheme` VALUES ('0906a608d2024562a3ba5f1235ad08cb','KPI_TST__2','副本-rename-','QUARTERLY',1,'DISABLED','admin','2026-04-29 07:21:37','admin','2026-04-29 07:21:39'),('e3cf2630d68b44d6952e7e79ebf52b2f','KPI_TST_20260429_072135','测试KPI方案-20260429_072135','MONTHLY',1,'DISABLED','admin','2026-04-29 07:21:35','admin','2026-04-29 07:21:39');
/*!40000 ALTER TABLE `perf_kpi_scheme` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_metric_def`
--

DROP TABLE IF EXISTS `perf_metric_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_metric_def` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标ID',
  `metric_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码(唯一)',
  `metric_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标名称',
  `metric_name_en` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '英文名',
  `metric_desc` text COLLATE utf8mb4_general_ci COMMENT '指标说明',
  `base_dim` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '基础维度：EMP/ORG/CUST',
  `metric_level` int NOT NULL COMMENT '指标层级：1/2/3',
  `calc_freq` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '计算频率：DAY/MONTH/QUARTER/YEAR',
  `calc_mode` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '计算方式：AUTO/MANUAL',
  `calc_logic_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '逻辑类型：SQL/PROC/EXPR/SUMMARY',
  `sql_text` longtext COLLATE utf8mb4_general_ci COMMENT '一级指标SQL/存储过程文本',
  `expr_text` text COLLATE utf8mb4_general_ci COMMENT '二/三级指标表达式',
  `summary_rule` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '机构汇总规则：SUM/AVG等',
  `ref_metric_codes` text COLLATE utf8mb4_general_ci COMMENT '引用指标列表(JSON数组)',
  `val_slot` int DEFAULT NULL COMMENT '宽表槽位(1..200)',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_code` (`metric_code`),
  KEY `idx_dim_level` (`base_dim`,`metric_level`),
  KEY `idx_status` (`status`),
  KEY `idx_val_slot` (`val_slot`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='指标定义表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_metric_def`
--

LOCK TABLES `perf_metric_def` WRITE;
/*!40000 ALTER TABLE `perf_metric_def` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_metric_def` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_metric_ref`
--

DROP TABLE IF EXISTS `perf_metric_ref`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_metric_ref` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '引用ID',
  `metric_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '引用者(上层指标)',
  `ref_metric_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '被引用(下层指标)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_ref` (`metric_code`,`ref_metric_code`),
  KEY `idx_ref_metric` (`ref_metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='指标引用关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_metric_ref`
--

LOCK TABLES `perf_metric_ref` WRITE;
/*!40000 ALTER TABLE `perf_metric_ref` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_metric_ref` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_run_task`
--

DROP TABLE IF EXISTS `perf_run_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_run_task` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `task_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC',
  `task_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关键键(如metric_code)',
  `data_date` date DEFAULT NULL COMMENT '数据日期',
  `data_version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据版本',
  `params_json` longtext COLLATE utf8mb4_general_ci COMMENT '参数(JSON)',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `started_by` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '发起人',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `error_msg` longtext COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `result_preview_json` longtext COLLATE utf8mb4_general_ci COMMENT '结果预览(JSON)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_type` (`task_type`),
  KEY `idx_status` (`status`),
  KEY `idx_started_by` (`started_by`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效任务执行日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_run_task`
--

LOCK TABLES `perf_run_task` WRITE;
/*!40000 ALTER TABLE `perf_run_task` DISABLE KEYS */;
INSERT INTO `perf_run_task` VALUES ('4c0898a30e8f422cb740d7af2c66e91e','RECALC','RECALC_2026-04-29_2026-04-29','2026-04-29','V1','{\"startDate\":\"2026-04-29\",\"endDate\":\"2026-04-29\",\"metricCount\":1,\"dateCount\":1,\"cycleType\":\"MONTHLY\",\"reason\":\"curl test recalc\"}','FAILED','admin','2026-04-29 07:21:39','2026-04-29 07:21:39','success=0, failed=1; details: M_TST_20260429_072135_L1@2026-04-29: [UNEXPECTED] \n### Error querying database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column \'unit\' in \'field list\'\n### The error may exist in URL [jar:file:/home/djdev/.m2/repository/com/bank/branch/platfo; ','[]','2026-04-29 07:21:39');
/*!40000 ALTER TABLE `perf_run_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_target_adjust_apply`
--

DROP TABLE IF EXISTS `perf_target_adjust_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_target_adjust_apply` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `plan_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `subject_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型：EMP/ORG',
  `subject_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID',
  `cycle_key` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期键',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED',
  `business_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键',
  `process_instance_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `remark` text COLLATE utf8mb4_general_ci COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标修正申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_target_adjust_apply`
--

LOCK TABLES `perf_target_adjust_apply` WRITE;
/*!40000 ALTER TABLE `perf_target_adjust_apply` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_target_adjust_apply` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_target_plan`
--

DROP TABLE IF EXISTS `perf_target_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_target_plan` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `plan_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案编码(唯一)',
  `plan_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `kpi_scheme_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联KPI方案ID',
  `target_dim` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标维度：EMP/ORG',
  `target_cycle` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标周期：YEAR/QUARTER',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_target_plan`
--

LOCK TABLES `perf_target_plan` WRITE;
/*!40000 ALTER TABLE `perf_target_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_target_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_target_value`
--

DROP TABLE IF EXISTS `perf_target_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_target_value` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标值ID',
  `plan_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `subject_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型：EMP/ORG',
  `subject_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID(emp_id/org_code)',
  `cycle_key` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期键：2026或2026Q1',
  `metric_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码',
  `target_value` decimal(20,4) NOT NULL COMMENT '目标值',
  `base_value` decimal(20,4) DEFAULT NULL COMMENT '基础值(可空，默认为0)',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_subject_cycle_metric` (`plan_id`,`subject_type`,`subject_id`,`cycle_key`,`metric_code`),
  KEY `idx_metric_code` (`metric_code`),
  KEY `idx_subject` (`subject_type`,`subject_id`,`cycle_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标值/基础值';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_target_value`
--

LOCK TABLES `perf_target_value` WRITE;
/*!40000 ALTER TABLE `perf_target_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_target_value` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_calendar_day`
--

DROP TABLE IF EXISTS `sys_calendar_day`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_calendar_day` (
  `day` date NOT NULL COMMENT '日期',
  `is_workday` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否工作日：1-工作日,0-休息日',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作日历(按天)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_calendar_day`
--

LOCK TABLES `sys_calendar_day` WRITE;
/*!40000 ALTER TABLE `sys_calendar_day` DISABLE KEYS */;
INSERT INTO `sys_calendar_day` VALUES ('2026-01-01',0,'元旦','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-01-02',0,'元旦假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-01-03',0,'元旦假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-05-01',0,'劳动节','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-05-02',0,'劳动节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-05-03',0,'劳动节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-01',0,'国庆节','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-02',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-03',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-04',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-05',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-06',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-07',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52');
/*!40000 ALTER TABLE `sys_calendar_day` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config_kv`
--

DROP TABLE IF EXISTS `sys_config_kv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config_kv` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置ID',
  `config_key` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置键(唯一)',
  `config_value` longtext COLLATE utf8mb4_general_ci COMMENT '配置值',
  `value_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/JSON/NUMBER/BOOL',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置KV';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config_kv`
--

LOCK TABLES `sys_config_kv` WRITE;
/*!40000 ALTER TABLE `sys_config_kv` DISABLE KEYS */;
INSERT INTO `sys_config_kv` VALUES ('CFG_AUDIT_EXPORT_MAX_DAYS','AUDIT_EXPORT_MAX_DAYS','31','NUMBER','ACTIVE','审计导出最大天数','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_AUDIT_EXPORT_MAX_ROWS','AUDIT_EXPORT_MAX_ROWS','200000','NUMBER','ACTIVE','审计导出最大行数','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_SQL_PROBE_MAX_CONCURRENCY','SQL_PROBE_MAX_CONCURRENCY','5','NUMBER','ACTIVE','SQL探查并发上限','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_SQL_PROBE_MAX_LIMIT','SQL_PROBE_MAX_LIMIT','2000','NUMBER','ACTIVE','SQL探查LIMIT上限','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_SQL_PROBE_WHITELIST_JSON','SQL_PROBE_WHITELIST_JSON','{\"schemas\":[],\"tables\":[]}','JSON','ACTIVE','SQL探查白名单','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52');
/*!40000 ALTER TABLE `sys_config_kv` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_control`
--

DROP TABLE IF EXISTS `sys_control`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_control` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '控制ID',
  `scope_dim` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `latest_data_date` date NOT NULL COMMENT '最新数据日期',
  `current_version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前有效版本',
  `is_valid` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_dim_date` (`scope_dim`,`latest_data_date`),
  KEY `idx_scope_valid` (`scope_dim`,`is_valid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据版本控制表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_control`
--

LOCK TABLES `sys_control` WRITE;
/*!40000 ALTER TABLE `sys_control` DISABLE KEYS */;
INSERT INTO `sys_control` VALUES ('SC_INIT_CUST','CUST','1970-01-01',NULL,0,'2026-04-28 00:30:52','2026-04-28 00:30:52'),('SC_INIT_EMP','EMP','1970-01-01',NULL,0,'2026-04-28 00:30:52','2026-04-28 00:30:52'),('SC_INIT_ORG','ORG','1970-01-01',NULL,0,'2026-04-28 00:30:52','2026-04-28 00:30:52');
/*!40000 ALTER TABLE `sys_control` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict`
--

DROP TABLE IF EXISTS `sys_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典ID',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典类型',
  `dict_code` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典编码',
  `dict_label` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典标签',
  `dict_value` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典值',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`,`dict_code`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict`
--

LOCK TABLES `sys_dict` WRITE;
/*!40000 ALTER TABLE `sys_dict` DISABLE KEYS */;
INSERT INTO `sys_dict` VALUES ('D_BK_CD','BIZ_KIND','NCD','大额存单','NCD',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BK_DEP','BIZ_KIND','DEPOSIT','存款','DEPOSIT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BK_LOAN','BIZ_KIND','LOAN','贷款','LOAN',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BK_MID','BIZ_KIND','INTERMEDIATE','中间业务','INTERMEDIATE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BT_ACCEPTANCE','BIZ_TYPE','ACCEPTANCE','承兑汇票','ACCEPTANCE',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_FIXED','BIZ_TYPE','FIXED_ASSET','固定资产贷款','FIXED_ASSET',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_GUARANTEE','BIZ_TYPE','GUARANTEE','保函','GUARANTEE',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_TRADE','BIZ_TYPE','TRADE_FINANCE','贸易融资','TRADE_FINANCE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_WORKING_CAP','BIZ_TYPE','WORKING_CAPITAL','流动资金贷款','WORKING_CAPITAL',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CF_DAY','PERF_CALC_FREQ','DAY','日','DAY',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CF_MONTH','PERF_CALC_FREQ','MONTH','月','MONTH',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CF_QUARTER','PERF_CALC_FREQ','QUARTER','季','QUARTER',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CF_YEAR','PERF_CALC_FREQ','YEAR','年','YEAR',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_EXPR','PERF_CALC_LOGIC_TYPE','EXPR','表达式','EXPR',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_PROC','PERF_CALC_LOGIC_TYPE','PROC','存储过程','PROC',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_SQL','PERF_CALC_LOGIC_TYPE','SQL','SQL查询','SQL',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_SUMMARY','PERF_CALC_LOGIC_TYPE','SUMMARY','汇总','SUMMARY',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CM_AUTO','PERF_CALC_MODE','AUTO','自动计算','AUTO',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CM_MANUAL','PERF_CALC_MODE','MANUAL','手工导入','MANUAL',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CT_1','CUSTOMER_TYPE','CORP','对公客户','CORP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CT_2','CUSTOMER_TYPE','RETAIL','零售客户','RETAIL',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CVT_BOOL','CONFIG_VALUE_TYPE','BOOL','布尔','BOOL',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CVT_JSON','CONFIG_VALUE_TYPE','JSON','JSON','JSON',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CVT_NUM','CONFIG_VALUE_TYPE','NUMBER','数值','NUMBER',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CVT_STR','CONFIG_VALUE_TYPE','STRING','字符串','STRING',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_GUIDE','DOC_CATEGORY','GUIDE','操作指引','GUIDE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_POLICY','DOC_CATEGORY','POLICY','制度文件','POLICY',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_TEMPLATE','DOC_CATEGORY','TEMPLATE','模板表单','TEMPLATE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_TRAIN','DOC_CATEGORY','TRAINING','培训材料','TRAINING',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_ET_1','ENTERPRISE_TYPE','SOE','国企','SOE',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_2','ENTERPRISE_TYPE','PRIVATE','民营','PRIVATE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_3','ENTERPRISE_TYPE','FOREIGN','外资','FOREIGN',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_4','ENTERPRISE_TYPE','JV','合资','JV',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_5','ENTERPRISE_TYPE','COLLECT','集体企业','COLLECT',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_GRP_1','GROUP_TYPE','GROUP','集团客户','GROUP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_GRP_2','GROUP_TYPE','SINGLE','非集团客户','SINGLE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_GT_CREDIT','GUARANTEE_TYPE','CREDIT','信用','CREDIT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_GUARANTEE','GUARANTEE_TYPE','GUARANTEE','保证','GUARANTEE',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_MIXED','GUARANTEE_TYPE','MIXED','组合担保','MIXED',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_MORTGAGE','GUARANTEE_TYPE','MORTGAGE','抵押','MORTGAGE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_PLEDGE','GUARANTEE_TYPE','PLEDGE','质押','PLEDGE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_IND_AGRI','INDUSTRY','AGRI','农林牧渔业','AGRI',10,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_EDU','INDUSTRY','EDU','教育','EDU',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_ENERGY','INDUSTRY','ENERGY','能源','ENERGY',9,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_FIN','INDUSTRY','FIN','金融业','FIN',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_IT','INDUSTRY','IT','信息技术','IT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_MED','INDUSTRY','MED','医疗卫生','MED',6,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_MFG','INDUSTRY','MFG','制造业','MFG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_OTHER','INDUSTRY','OTHER','其他','OTHER',99,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_RE','INDUSTRY','RE','房地产业','RE',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_RETAIL','INDUSTRY','RETAIL','批发和零售业','RETAIL',7,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_TRANS','INDUSTRY','TRANS','交通运输业','TRANS',8,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_JRS_FAIL','JOB_RUN_STATUS','FAILED','失败','FAILED',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JRS_OK','JOB_RUN_STATUS','SUCCESS','成功','SUCCESS',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JRS_RUN','JOB_RUN_STATUS','RUNNING','运行中','RUNNING',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JS_ACT','JOB_STATUS','ACTIVE','活跃','ACTIVE',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JS_PAU','JOB_STATUS','PAUSED','暂停','PAUSED',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_KC_M','PERF_KPI_CYCLE','MONTHLY','月度','MONTHLY',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_KC_Q','PERF_KPI_CYCLE','QUARTERLY','季度','QUARTERLY',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_LS_ASSIGN','LEAD_SOURCE','ASSIGNED','上级分配','ASSIGNED',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_LS_IMPORT','LEAD_SOURCE','IMPORTED','批量导入','IMPORTED',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_LS_REFER','LEAD_SOURCE','REFERRAL','转介绍','REFERRAL',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_LS_SELF','LEAD_SOURCE','SELF_FOUND','自行挖掘','SELF_FOUND',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_MD_CUST','PERF_BASE_DIM','CUST','客户','CUST',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_MD_EMP','PERF_BASE_DIM','EMP','人员','EMP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_MD_ORG','PERF_BASE_DIM','ORG','机构','ORG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ML_1','PERF_METRIC_LEVEL','1','一级基础','1',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ML_2','PERF_METRIC_LEVEL','2','二级派生','2',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ML_3','PERF_METRIC_LEVEL','3','三级复合','3',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_NT_BIZ','NOTIFY_TYPE','BUSINESS','业务通知','BUSINESS',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_NT_SYS','NOTIFY_TYPE','SYSTEM','系统通知','SYSTEM',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_NT_WF','NOTIFY_TYPE','WORKFLOW','流程通知','WORKFLOW',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PAD_ACCOUNT','PERF_ALLOC_DIM','ACCOUNT','按台账分配','ACCOUNT',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PAD_RULE','PERF_ALLOC_DIM','RULE','按规则分配','RULE',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PC_CORP','PRODUCT_CATEGORY','CORP_BANK','公司银行','CORP_BANK',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PC_FM','PRODUCT_CATEGORY','FIN_MARKET','金融市场','FIN_MARKET',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PC_RTL','PRODUCT_CATEGORY','RETAIL_BANK','零售银行','RETAIL_BANK',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PC_TRADE','PRODUCT_CATEGORY','TRADE_BANK','交易银行','TRADE_BANK',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PIT_IDX','PERF_IMPORT_TYPE','INDEX_RESULT','指标结果','INDEX_RESULT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PIT_KPI','PERF_IMPORT_TYPE','KPI_RESULT','KPI结果','KPI_RESULT',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PIT_TGT','PERF_IMPORT_TYPE','TARGET','目标','TARGET',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PJT_ADJUST','PROJECT_TYPE','ADJUST','调整项目','ADJUST',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PJT_NEW','PROJECT_TYPE','NEW','新增项目','NEW',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PJT_RENEW','PROJECT_TYPE','RENEWAL','续贷项目','RENEWAL',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_BH','POSITION','BRANCH_HEAD','支行负责人','BRANCH_HEAD',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CA','POSITION','CREDIT_APPROVE','授信批复岗','CREDIT_APPROVE',9,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CM','POSITION','CUST_MGR','客户经理','CUST_MGR',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CORP','POSITION','CORP_STAFF','公司部员工','CORP_STAFF',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CR','POSITION','CREDIT_REVIEW','授信审查岗','CREDIT_REVIEW',8,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_FIN','POSITION','FINANCE_STAFF','资财部员工','FINANCE_STAFF',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_PRES','POSITION','PRESIDENT','行长','PRESIDENT',10,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_RTL','POSITION','RETAIL_STAFF','零售部员工','RETAIL_STAFF',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_SEC','POSITION','SECRETARY','部门秘书','SECRETARY',7,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_TECH','POSITION','TECH_STAFF','科技部员工','TECH_STAFF',6,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PSR_AVG','PERF_SUMMARY_RULE','AVG','平均','AVG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PSR_SUM','PERF_SUMMARY_RULE','SUM','求和','SUM',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PTT_KPI','PERF_TASK_TYPE','KPI_RUN','KPI执行','KPI_RUN',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PTT_RECALC','PERF_TASK_TYPE','RECALC','历史重算','RECALC',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PTT_RUN','PERF_TASK_TYPE','METRIC_RUN','指标执行','METRIC_RUN',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PTT_TRIAL','PERF_TASK_TYPE','METRIC_TRIAL','指标试运行','METRIC_TRIAL',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_RL_HIGH','RISK_LEVEL','HIGH','高风险','HIGH',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_RL_LOW','RISK_LEVEL','LOW','低风险','LOW',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_RL_MID','RISK_LEVEL','MEDIUM','中风险','MEDIUM',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_TC_Q','PERF_TARGET_CYCLE','QUARTER','季度','QUARTER',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_TC_Y','PERF_TARGET_CYCLE','YEAR','年度','YEAR',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_TD_EMP','PERF_TARGET_DIM','EMP','人员','EMP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_TD_ORG','PERF_TARGET_DIM','ORG','机构','ORG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_YN_0','YES_NO','NO','否','0',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_YN_1','YES_NO','YES','是','1',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55');
/*!40000 ALTER TABLE `sys_dict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_item`
--

DROP TABLE IF EXISTS `sys_dict_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项ID',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典类型（关联 sys_dict.dict_type）',
  `item_code` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项编码',
  `item_label` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项标签',
  `item_value` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项值',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_item_code` (`dict_type`,`item_code`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_item`
--

LOCK TABLES `sys_dict_item` WRITE;
/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_conf`
--

DROP TABLE IF EXISTS `sys_job_conf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_conf` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `job_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务KEY(唯一)',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `cron_expr` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron表达式',
  `quartz_job_class` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
  `misfire_policy` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略（V1.6 新增）',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/PAUSED',
  `allow_manual_trigger` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许手动触发',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次执行时间',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次执行时间(可选)',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_key` (`job_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务调度配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_conf`
--

LOCK TABLES `sys_job_conf` WRITE;
/*!40000 ALTER TABLE `sys_job_conf` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_conf` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_run_log`
--

DROP TABLE IF EXISTS `sys_job_run_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_run_log` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行日志ID',
  `job_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `trigger_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型：SCHEDULED/MANUAL',
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原因(手动触发必填)',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `scheduled_fire_time` datetime(3) DEFAULT NULL COMMENT 'Quartz 计划触发时间（V1.6 新增）',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `error_msg` longtext COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '触发人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务执行日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_run_log`
--

LOCK TABLES `sys_job_run_log` WRITE;
/*!40000 ALTER TABLE `sys_job_run_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_run_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-29  7:24:16
