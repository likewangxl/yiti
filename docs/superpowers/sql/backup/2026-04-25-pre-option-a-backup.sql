mysqldump: [Warning] Using a password on the command line interface can be insecure.
-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: onepl
-- ------------------------------------------------------
-- Server version	8.0.45

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
-- Table structure for table `act_app_appdef`
--

DROP TABLE IF EXISTS `act_app_appdef`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_app_appdef` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `VERSION_` int NOT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_APP_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`),
  KEY `ACT_IDX_APP_DEF_DPLY` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_APP_DEF_DPLY` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `act_app_deployment` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_app_appdef`
--

LOCK TABLES `act_app_appdef` WRITE;
/*!40000 ALTER TABLE `act_app_appdef` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_app_appdef` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_app_databasechangelog`
--

DROP TABLE IF EXISTS `act_app_databasechangelog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_app_databasechangelog` (
  `ID` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `AUTHOR` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `FILENAME` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int NOT NULL,
  `EXECTYPE` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `MD5SUM` varchar(35) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `COMMENTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TAG` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LIQUIBASE` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CONTEXTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LABELS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_app_databasechangelog`
--

LOCK TABLES `act_app_databasechangelog` WRITE;
/*!40000 ALTER TABLE `act_app_databasechangelog` DISABLE KEYS */;
INSERT INTO `act_app_databasechangelog` VALUES ('1','flowable','org/flowable/app/db/liquibase/flowable-app-db-changelog.xml','2026-04-13 14:54:25',1,'EXECUTED','9:959783069c0c7ce80320a0617aa48969','createTable tableName=ACT_APP_DEPLOYMENT; createTable tableName=ACT_APP_DEPLOYMENT_RESOURCE; addForeignKeyConstraint baseTableName=ACT_APP_DEPLOYMENT_RESOURCE, constraintName=ACT_FK_APP_RSRC_DPL, referencedTableName=ACT_APP_DEPLOYMENT; createIndex...','',NULL,'4.24.0',NULL,NULL,'6063265617'),('2','flowable','org/flowable/app/db/liquibase/flowable-app-db-changelog.xml','2026-04-13 14:54:25',2,'EXECUTED','9:c75407b1c0e16adf2a6db585c05a94c7','modifyDataType columnName=DEPLOY_TIME_, tableName=ACT_APP_DEPLOYMENT','',NULL,'4.24.0',NULL,NULL,'6063265617'),('3','flowable','org/flowable/app/db/liquibase/flowable-app-db-changelog.xml','2026-04-13 14:54:25',3,'EXECUTED','9:c05b79a3b00e95136533085718361208','createIndex indexName=ACT_IDX_APP_DEF_UNIQ, tableName=ACT_APP_APPDEF','',NULL,'4.24.0',NULL,NULL,'6063265617');
/*!40000 ALTER TABLE `act_app_databasechangelog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_app_databasechangeloglock`
--

DROP TABLE IF EXISTS `act_app_databasechangeloglock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_app_databasechangeloglock` (
  `ID` int NOT NULL,
  `LOCKED` tinyint(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_app_databasechangeloglock`
--

LOCK TABLES `act_app_databasechangeloglock` WRITE;
/*!40000 ALTER TABLE `act_app_databasechangeloglock` DISABLE KEYS */;
INSERT INTO `act_app_databasechangeloglock` VALUES (1,0,NULL,NULL);
/*!40000 ALTER TABLE `act_app_databasechangeloglock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_app_deployment`
--

DROP TABLE IF EXISTS `act_app_deployment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_app_deployment` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_app_deployment`
--

LOCK TABLES `act_app_deployment` WRITE;
/*!40000 ALTER TABLE `act_app_deployment` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_app_deployment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_app_deployment_resource`
--

DROP TABLE IF EXISTS `act_app_deployment_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_app_deployment_resource` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_BYTES_` longblob,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_APP_RSRC_DPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_APP_RSRC_DPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `act_app_deployment` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_app_deployment_resource`
--

LOCK TABLES `act_app_deployment_resource` WRITE;
/*!40000 ALTER TABLE `act_app_deployment_resource` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_app_deployment_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_casedef`
--

DROP TABLE IF EXISTS `act_cmmn_casedef`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_casedef` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `VERSION_` int NOT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HAS_GRAPHICAL_NOTATION_` tinyint(1) DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  `DGRM_RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HAS_START_FORM_KEY_` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_CASE_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`),
  KEY `ACT_IDX_CASE_DEF_DPLY` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_CASE_DEF_DPLY` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `act_cmmn_deployment` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_casedef`
--

LOCK TABLES `act_cmmn_casedef` WRITE;
/*!40000 ALTER TABLE `act_cmmn_casedef` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_casedef` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_databasechangelog`
--

DROP TABLE IF EXISTS `act_cmmn_databasechangelog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_databasechangelog` (
  `ID` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `AUTHOR` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `FILENAME` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int NOT NULL,
  `EXECTYPE` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `MD5SUM` varchar(35) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `COMMENTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TAG` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LIQUIBASE` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CONTEXTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LABELS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_databasechangelog`
--

LOCK TABLES `act_cmmn_databasechangelog` WRITE;
/*!40000 ALTER TABLE `act_cmmn_databasechangelog` DISABLE KEYS */;
INSERT INTO `act_cmmn_databasechangelog` VALUES ('1','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:24',1,'EXECUTED','9:d0cc0aaadf0e4ef70c5b412cd05fadc4','createTable tableName=ACT_CMMN_DEPLOYMENT; createTable tableName=ACT_CMMN_DEPLOYMENT_RESOURCE; addForeignKeyConstraint baseTableName=ACT_CMMN_DEPLOYMENT_RESOURCE, constraintName=ACT_FK_CMMN_RSRC_DPL, referencedTableName=ACT_CMMN_DEPLOYMENT; create...','',NULL,'4.24.0',NULL,NULL,'6063263536'),('2','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:24',2,'EXECUTED','9:8095a5a8a222a100c2d0310cacbda5e7','addColumn tableName=ACT_CMMN_CASEDEF; addColumn tableName=ACT_CMMN_DEPLOYMENT_RESOURCE; addColumn tableName=ACT_CMMN_RU_CASE_INST; addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('3','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:24',3,'EXECUTED','9:f031b4f0ae67bc5a640736b379049b12','addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_RU_CASE_INST; createIndex indexName=ACT_IDX_PLAN_ITEM_STAGE_INST, tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableNam...','',NULL,'4.24.0',NULL,NULL,'6063263536'),('4','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:24',4,'EXECUTED','9:c484ecfb08719feccac2f80fc962dda9','createTable tableName=ACT_CMMN_HI_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_RU_MIL_INST; addColumn tableName=ACT_CMMN_HI_MIL_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('5','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:24',5,'EXECUTED','9:e6a67f8f0d16cd72117900442acfe6e0','modifyDataType columnName=DEPLOY_TIME_, tableName=ACT_CMMN_DEPLOYMENT; modifyDataType columnName=START_TIME_, tableName=ACT_CMMN_RU_CASE_INST; modifyDataType columnName=START_TIME_, tableName=ACT_CMMN_RU_PLAN_ITEM_INST; modifyDataType columnName=T...','',NULL,'4.24.0',NULL,NULL,'6063263536'),('6','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',6,'EXECUTED','9:7343ab247d959e5add9278b5386de833','createIndex indexName=ACT_IDX_CASE_DEF_UNIQ, tableName=ACT_CMMN_CASEDEF','',NULL,'4.24.0',NULL,NULL,'6063263536'),('7','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',7,'EXECUTED','9:d73200db684b6cdb748cc03570d5d2e9','renameColumn newColumnName=CREATE_TIME_, oldColumnName=START_TIME_, tableName=ACT_CMMN_RU_PLAN_ITEM_INST; renameColumn newColumnName=CREATE_TIME_, oldColumnName=CREATED_TIME_, tableName=ACT_CMMN_HI_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_RU_P...','',NULL,'4.24.0',NULL,NULL,'6063263536'),('8','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',8,'EXECUTED','9:eda5e43816221f2d8554bfcc90f1c37e','addColumn tableName=ACT_CMMN_HI_PLAN_ITEM_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('9','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',9,'EXECUTED','9:c34685611779075a73caf8c380f078ea','addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_HI_PLAN_ITEM_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('10','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',10,'EXECUTED','9:368e9472ad2348206205170d6c52d58e','addColumn tableName=ACT_CMMN_RU_CASE_INST; addColumn tableName=ACT_CMMN_RU_CASE_INST; createIndex indexName=ACT_IDX_CASE_INST_REF_ID_, tableName=ACT_CMMN_RU_CASE_INST; addColumn tableName=ACT_CMMN_HI_CASE_INST; addColumn tableName=ACT_CMMN_HI_CASE...','',NULL,'4.24.0',NULL,NULL,'6063263536'),('11','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',11,'EXECUTED','9:e54b50ceb2bcd5355ae4dfb56d9ff3ad','addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_HI_PLAN_ITEM_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('12','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',12,'EXECUTED','9:f53f262768d04e74529f43fcd93429b0','addColumn tableName=ACT_CMMN_RU_CASE_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('13','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',13,'EXECUTED','9:64e7eafbe97997094654e83caea99895','addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_HI_PLAN_ITEM_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('14','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',14,'EXECUTED','9:ab7d934abde497eac034701542e0a281','addColumn tableName=ACT_CMMN_RU_CASE_INST; addColumn tableName=ACT_CMMN_HI_CASE_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('16','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',15,'EXECUTED','9:03928d422e510959770e7a9daa5a993f','addColumn tableName=ACT_CMMN_RU_CASE_INST; addColumn tableName=ACT_CMMN_HI_CASE_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('17','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',16,'EXECUTED','9:f30304cf001d6eac78c793ea88cd5781','createIndex indexName=ACT_IDX_HI_CASE_INST_END, tableName=ACT_CMMN_HI_CASE_INST','',NULL,'4.24.0',NULL,NULL,'6063263536'),('18','flowable','org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml','2026-04-13 14:54:25',17,'EXECUTED','9:d782865087d6c0c3dc033ac20e783008','createIndex indexName=ACT_IDX_HI_PLAN_ITEM_INST_CASE, tableName=ACT_CMMN_HI_PLAN_ITEM_INST','',NULL,'4.24.0',NULL,NULL,'6063263536');
/*!40000 ALTER TABLE `act_cmmn_databasechangelog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_databasechangeloglock`
--

DROP TABLE IF EXISTS `act_cmmn_databasechangeloglock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_databasechangeloglock` (
  `ID` int NOT NULL,
  `LOCKED` tinyint(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_databasechangeloglock`
--

LOCK TABLES `act_cmmn_databasechangeloglock` WRITE;
/*!40000 ALTER TABLE `act_cmmn_databasechangeloglock` DISABLE KEYS */;
INSERT INTO `act_cmmn_databasechangeloglock` VALUES (1,0,NULL,NULL);
/*!40000 ALTER TABLE `act_cmmn_databasechangeloglock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_deployment`
--

DROP TABLE IF EXISTS `act_cmmn_deployment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_deployment` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL,
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_deployment`
--

LOCK TABLES `act_cmmn_deployment` WRITE;
/*!40000 ALTER TABLE `act_cmmn_deployment` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_deployment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_deployment_resource`
--

DROP TABLE IF EXISTS `act_cmmn_deployment_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_deployment_resource` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_BYTES_` longblob,
  `GENERATED_` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_CMMN_RSRC_DPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_CMMN_RSRC_DPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `act_cmmn_deployment` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_deployment_resource`
--

LOCK TABLES `act_cmmn_deployment_resource` WRITE;
/*!40000 ALTER TABLE `act_cmmn_deployment_resource` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_deployment_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_hi_case_inst`
--

DROP TABLE IF EXISTS `act_cmmn_hi_case_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_hi_case_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `BUSINESS_KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STATE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) DEFAULT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LAST_REACTIVATION_TIME_` datetime(3) DEFAULT NULL,
  `LAST_REACTIVATION_USER_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BUSINESS_STATUS_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_CASE_INST_END` (`END_TIME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_hi_case_inst`
--

LOCK TABLES `act_cmmn_hi_case_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_hi_case_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_hi_case_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_hi_mil_inst`
--

DROP TABLE IF EXISTS `act_cmmn_hi_mil_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_hi_mil_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `TIME_STAMP_` datetime(3) DEFAULT NULL,
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_hi_mil_inst`
--

LOCK TABLES `act_cmmn_hi_mil_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_hi_mil_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_hi_mil_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_hi_plan_item_inst`
--

DROP TABLE IF EXISTS `act_cmmn_hi_plan_item_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_hi_plan_item_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STATE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IS_STAGE_` tinyint(1) DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ITEM_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ITEM_DEFINITION_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `LAST_AVAILABLE_TIME_` datetime(3) DEFAULT NULL,
  `LAST_ENABLED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_DISABLED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_STARTED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_SUSPENDED_TIME_` datetime(3) DEFAULT NULL,
  `COMPLETED_TIME_` datetime(3) DEFAULT NULL,
  `OCCURRED_TIME_` datetime(3) DEFAULT NULL,
  `TERMINATED_TIME_` datetime(3) DEFAULT NULL,
  `EXIT_TIME_` datetime(3) DEFAULT NULL,
  `ENDED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL,
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  `ENTRY_CRITERION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXIT_CRITERION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SHOW_IN_OVERVIEW_` tinyint(1) DEFAULT NULL,
  `EXTRA_VALUE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DERIVED_CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LAST_UNAVAILABLE_TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_PLAN_ITEM_INST_CASE` (`CASE_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_hi_plan_item_inst`
--

LOCK TABLES `act_cmmn_hi_plan_item_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_hi_plan_item_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_hi_plan_item_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_ru_case_inst`
--

DROP TABLE IF EXISTS `act_cmmn_ru_case_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_ru_case_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `BUSINESS_KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STATE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) DEFAULT NULL,
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  `LOCK_TIME_` datetime(3) DEFAULT NULL,
  `IS_COMPLETEABLE_` tinyint(1) DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LAST_REACTIVATION_TIME_` datetime(3) DEFAULT NULL,
  `LAST_REACTIVATION_USER_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BUSINESS_STATUS_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_CASE_INST_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_CASE_INST_PARENT` (`PARENT_ID_`),
  KEY `ACT_IDX_CASE_INST_REF_ID_` (`REFERENCE_ID_`),
  CONSTRAINT `ACT_FK_CASE_INST_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `act_cmmn_casedef` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_ru_case_inst`
--

LOCK TABLES `act_cmmn_ru_case_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_ru_case_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_ru_case_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_ru_mil_inst`
--

DROP TABLE IF EXISTS `act_cmmn_ru_mil_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_ru_mil_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `TIME_STAMP_` datetime(3) DEFAULT NULL,
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_MIL_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_MIL_CASE_INST` (`CASE_INST_ID_`),
  CONSTRAINT `ACT_FK_MIL_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `act_cmmn_casedef` (`ID_`),
  CONSTRAINT `ACT_FK_MIL_CASE_INST` FOREIGN KEY (`CASE_INST_ID_`) REFERENCES `act_cmmn_ru_case_inst` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_ru_mil_inst`
--

LOCK TABLES `act_cmmn_ru_mil_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_ru_mil_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_ru_mil_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_ru_plan_item_inst`
--

DROP TABLE IF EXISTS `act_cmmn_ru_plan_item_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_ru_plan_item_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IS_STAGE_` tinyint(1) DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STATE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '',
  `ITEM_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ITEM_DEFINITION_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IS_COMPLETEABLE_` tinyint(1) DEFAULT NULL,
  `IS_COUNT_ENABLED_` tinyint(1) DEFAULT NULL,
  `VAR_COUNT_` int DEFAULT NULL,
  `SENTRY_PART_INST_COUNT_` int DEFAULT NULL,
  `LAST_AVAILABLE_TIME_` datetime(3) DEFAULT NULL,
  `LAST_ENABLED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_DISABLED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_STARTED_TIME_` datetime(3) DEFAULT NULL,
  `LAST_SUSPENDED_TIME_` datetime(3) DEFAULT NULL,
  `COMPLETED_TIME_` datetime(3) DEFAULT NULL,
  `OCCURRED_TIME_` datetime(3) DEFAULT NULL,
  `TERMINATED_TIME_` datetime(3) DEFAULT NULL,
  `EXIT_TIME_` datetime(3) DEFAULT NULL,
  `ENDED_TIME_` datetime(3) DEFAULT NULL,
  `ENTRY_CRITERION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXIT_CRITERION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXTRA_VALUE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DERIVED_CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LAST_UNAVAILABLE_TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_PLAN_ITEM_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_PLAN_ITEM_CASE_INST` (`CASE_INST_ID_`),
  KEY `ACT_IDX_PLAN_ITEM_STAGE_INST` (`STAGE_INST_ID_`),
  CONSTRAINT `ACT_FK_PLAN_ITEM_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `act_cmmn_casedef` (`ID_`),
  CONSTRAINT `ACT_FK_PLAN_ITEM_CASE_INST` FOREIGN KEY (`CASE_INST_ID_`) REFERENCES `act_cmmn_ru_case_inst` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_ru_plan_item_inst`
--

LOCK TABLES `act_cmmn_ru_plan_item_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_ru_plan_item_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_ru_plan_item_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_cmmn_ru_sentry_part_inst`
--

DROP TABLE IF EXISTS `act_cmmn_ru_sentry_part_inst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_cmmn_ru_sentry_part_inst` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int NOT NULL,
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PLAN_ITEM_INST_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ON_PART_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IF_PART_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TIME_STAMP_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_SENTRY_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_SENTRY_CASE_INST` (`CASE_INST_ID_`),
  KEY `ACT_IDX_SENTRY_PLAN_ITEM` (`PLAN_ITEM_INST_ID_`),
  CONSTRAINT `ACT_FK_SENTRY_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `act_cmmn_casedef` (`ID_`),
  CONSTRAINT `ACT_FK_SENTRY_CASE_INST` FOREIGN KEY (`CASE_INST_ID_`) REFERENCES `act_cmmn_ru_case_inst` (`ID_`),
  CONSTRAINT `ACT_FK_SENTRY_PLAN_ITEM` FOREIGN KEY (`PLAN_ITEM_INST_ID_`) REFERENCES `act_cmmn_ru_plan_item_inst` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_cmmn_ru_sentry_part_inst`
--

LOCK TABLES `act_cmmn_ru_sentry_part_inst` WRITE;
/*!40000 ALTER TABLE `act_cmmn_ru_sentry_part_inst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_cmmn_ru_sentry_part_inst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_dmn_databasechangelog`
--

DROP TABLE IF EXISTS `act_dmn_databasechangelog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_dmn_databasechangelog` (
  `ID` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `AUTHOR` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `FILENAME` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int NOT NULL,
  `EXECTYPE` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `MD5SUM` varchar(35) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `COMMENTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TAG` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LIQUIBASE` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CONTEXTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LABELS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_dmn_databasechangelog`
--

LOCK TABLES `act_dmn_databasechangelog` WRITE;
/*!40000 ALTER TABLE `act_dmn_databasechangelog` DISABLE KEYS */;
INSERT INTO `act_dmn_databasechangelog` VALUES ('1','activiti','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:22',1,'EXECUTED','9:5b36e70aee5a2e42f6e7a62ea5fa681b','createTable tableName=ACT_DMN_DEPLOYMENT; createTable tableName=ACT_DMN_DEPLOYMENT_RESOURCE; createTable tableName=ACT_DMN_DECISION_TABLE','',NULL,'4.24.0',NULL,NULL,'6063262932'),('2','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',2,'EXECUTED','9:fd13fa3f7af55d2b72f763fc261da30d','createTable tableName=ACT_DMN_HI_DECISION_EXECUTION','',NULL,'4.24.0',NULL,NULL,'6063262932'),('3','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',3,'EXECUTED','9:9f30e6a3557d4b4c713dbb2dcc141782','addColumn tableName=ACT_DMN_HI_DECISION_EXECUTION','',NULL,'4.24.0',NULL,NULL,'6063262932'),('4','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',4,'EXECUTED','9:41085fbde807dba96104ee75a2fcc4cc','dropColumn columnName=PARENT_DEPLOYMENT_ID_, tableName=ACT_DMN_DECISION_TABLE','',NULL,'4.24.0',NULL,NULL,'6063262932'),('5','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',5,'EXECUTED','9:169d906b6503ad6907b7e5cd0d70d004','modifyDataType columnName=DEPLOY_TIME_, tableName=ACT_DMN_DEPLOYMENT; modifyDataType columnName=START_TIME_, tableName=ACT_DMN_HI_DECISION_EXECUTION; modifyDataType columnName=END_TIME_, tableName=ACT_DMN_HI_DECISION_EXECUTION','',NULL,'4.24.0',NULL,NULL,'6063262932'),('6','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',6,'EXECUTED','9:f00f92f3ef1af3fc1604f0323630f9b1','createIndex indexName=ACT_IDX_DEC_TBL_UNIQ, tableName=ACT_DMN_DECISION_TABLE','',NULL,'4.24.0',NULL,NULL,'6063262932'),('7','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',7,'EXECUTED','9:d24d4c5f44083b4edf1231a7a682a2cd','dropIndex indexName=ACT_IDX_DEC_TBL_UNIQ, tableName=ACT_DMN_DECISION_TABLE; renameTable newTableName=ACT_DMN_DECISION, oldTableName=ACT_DMN_DECISION_TABLE; createIndex indexName=ACT_IDX_DMN_DEC_UNIQ, tableName=ACT_DMN_DECISION','',NULL,'4.24.0',NULL,NULL,'6063262932'),('8','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',8,'EXECUTED','9:3998ef0958b46fe9c19458183952d2a0','addColumn tableName=ACT_DMN_DECISION','',NULL,'4.24.0',NULL,NULL,'6063262932'),('9','flowable','org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml','2026-04-13 14:54:23',9,'EXECUTED','9:5c9dc65601456faa1aa12f8d3afe0e9e','createIndex indexName=ACT_IDX_DMN_INSTANCE_ID, tableName=ACT_DMN_HI_DECISION_EXECUTION','',NULL,'4.24.0',NULL,NULL,'6063262932');
/*!40000 ALTER TABLE `act_dmn_databasechangelog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_dmn_databasechangeloglock`
--

DROP TABLE IF EXISTS `act_dmn_databasechangeloglock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_dmn_databasechangeloglock` (
  `ID` int NOT NULL,
  `LOCKED` tinyint(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_dmn_databasechangeloglock`
--

LOCK TABLES `act_dmn_databasechangeloglock` WRITE;
/*!40000 ALTER TABLE `act_dmn_databasechangeloglock` DISABLE KEYS */;
INSERT INTO `act_dmn_databasechangeloglock` VALUES (1,0,NULL,NULL);
/*!40000 ALTER TABLE `act_dmn_databasechangeloglock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_dmn_decision`
--

DROP TABLE IF EXISTS `act_dmn_decision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_dmn_decision` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `VERSION_` int DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DECISION_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_DMN_DEC_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_dmn_decision`
--

LOCK TABLES `act_dmn_decision` WRITE;
/*!40000 ALTER TABLE `act_dmn_decision` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_dmn_decision` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_dmn_deployment`
--

DROP TABLE IF EXISTS `act_dmn_deployment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_dmn_deployment` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_dmn_deployment`
--

LOCK TABLES `act_dmn_deployment` WRITE;
/*!40000 ALTER TABLE `act_dmn_deployment` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_dmn_deployment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_dmn_deployment_resource`
--

DROP TABLE IF EXISTS `act_dmn_deployment_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_dmn_deployment_resource` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_BYTES_` longblob,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_dmn_deployment_resource`
--

LOCK TABLES `act_dmn_deployment_resource` WRITE;
/*!40000 ALTER TABLE `act_dmn_deployment_resource` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_dmn_deployment_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_dmn_hi_decision_execution`
--

DROP TABLE IF EXISTS `act_dmn_hi_decision_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_dmn_hi_decision_execution` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `DECISION_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) DEFAULT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `INSTANCE_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACTIVITY_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `FAILED_` tinyint(1) DEFAULT '0',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_JSON_` longtext COLLATE utf8mb4_general_ci,
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_DMN_INSTANCE_ID` (`INSTANCE_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_dmn_hi_decision_execution`
--

LOCK TABLES `act_dmn_hi_decision_execution` WRITE;
/*!40000 ALTER TABLE `act_dmn_hi_decision_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_dmn_hi_decision_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_evt_log`
--

DROP TABLE IF EXISTS `act_evt_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_evt_log` (
  `LOG_NR_` bigint NOT NULL AUTO_INCREMENT,
  `TYPE_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TIME_STAMP_` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DATA_` longblob,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL,
  `IS_PROCESSED_` tinyint DEFAULT '0',
  PRIMARY KEY (`LOG_NR_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_evt_log`
--

LOCK TABLES `act_evt_log` WRITE;
/*!40000 ALTER TABLE `act_evt_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_evt_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ge_bytearray`
--

DROP TABLE IF EXISTS `act_ge_bytearray`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ge_bytearray` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BYTES_` longblob,
  `GENERATED_` tinyint DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_BYTEARR_DEPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_BYTEARR_DEPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `act_re_deployment` (`id_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ge_bytearray`
--

LOCK TABLES `act_ge_bytearray` WRITE;
/*!40000 ALTER TABLE `act_ge_bytearray` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ge_bytearray` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ge_property`
--

DROP TABLE IF EXISTS `act_ge_property`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ge_property` (
  `NAME_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VALUE_` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REV_` int DEFAULT NULL,
  PRIMARY KEY (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ge_property`
--

LOCK TABLES `act_ge_property` WRITE;
/*!40000 ALTER TABLE `act_ge_property` DISABLE KEYS */;
INSERT INTO `act_ge_property` VALUES ('batch.schema.version','7.0.1.1',1),('cfg.execution-related-entities-count','true',1),('cfg.task-related-entities-count','true',1),('common.schema.version','7.0.1.1',1),('entitylink.schema.version','7.0.1.1',1),('eventsubscription.schema.version','7.0.1.1',1),('identitylink.schema.version','7.0.1.1',1),('job.schema.version','7.0.1.1',1),('next.dbid','1',1),('schema.history','create(7.0.1.1)',1),('schema.version','7.0.1.1',1),('task.schema.version','7.0.1.1',1),('variable.schema.version','7.0.1.1',1);
/*!40000 ALTER TABLE `act_ge_property` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_actinst`
--

DROP TABLE IF EXISTS `act_hi_actinst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_actinst` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ACT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CALL_PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACT_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `TRANSACTION_ORDER_` int DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_ACT_INST_START` (`START_TIME_`),
  KEY `ACT_IDX_HI_ACT_INST_END` (`END_TIME_`),
  KEY `ACT_IDX_HI_ACT_INST_PROCINST` (`PROC_INST_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_HI_ACT_INST_EXEC` (`EXECUTION_ID_`,`ACT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_actinst`
--

LOCK TABLES `act_hi_actinst` WRITE;
/*!40000 ALTER TABLE `act_hi_actinst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_actinst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_attachment`
--

DROP TABLE IF EXISTS `act_hi_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_attachment` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `URL_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CONTENT_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_attachment`
--

LOCK TABLES `act_hi_attachment` WRITE;
/*!40000 ALTER TABLE `act_hi_attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_comment`
--

DROP TABLE IF EXISTS `act_hi_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_comment` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TIME_` datetime(3) NOT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACTION_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `MESSAGE_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FULL_MSG_` longblob,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_comment`
--

LOCK TABLES `act_hi_comment` WRITE;
/*!40000 ALTER TABLE `act_hi_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_detail`
--

DROP TABLE IF EXISTS `act_hi_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_detail` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACT_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VAR_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REV_` int DEFAULT NULL,
  `TIME_` datetime(3) NOT NULL,
  `BYTEARRAY_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DOUBLE_` double DEFAULT NULL,
  `LONG_` bigint DEFAULT NULL,
  `TEXT_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TEXT2_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_DETAIL_PROC_INST` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_DETAIL_ACT_INST` (`ACT_INST_ID_`),
  KEY `ACT_IDX_HI_DETAIL_TIME` (`TIME_`),
  KEY `ACT_IDX_HI_DETAIL_NAME` (`NAME_`),
  KEY `ACT_IDX_HI_DETAIL_TASK_ID` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_detail`
--

LOCK TABLES `act_hi_detail` WRITE;
/*!40000 ALTER TABLE `act_hi_detail` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_entitylink`
--

DROP TABLE IF EXISTS `act_hi_entitylink`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_entitylink` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `LINK_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REF_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REF_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REF_SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ROOT_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ROOT_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HIERARCHY_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_ENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_REF_SCOPE` (`REF_SCOPE_ID_`,`REF_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_ROOT_SCOPE` (`ROOT_SCOPE_ID_`,`ROOT_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_entitylink`
--

LOCK TABLES `act_hi_entitylink` WRITE;
/*!40000 ALTER TABLE `act_hi_entitylink` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_entitylink` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_identitylink`
--

DROP TABLE IF EXISTS `act_hi_identitylink`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_identitylink` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `GROUP_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_USER` (`USER_ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_TASK` (`TASK_ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_identitylink`
--

LOCK TABLES `act_hi_identitylink` WRITE;
/*!40000 ALTER TABLE `act_hi_identitylink` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_identitylink` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_procinst`
--

DROP TABLE IF EXISTS `act_hi_procinst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_procinst` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `BUSINESS_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `START_USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `START_ACT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `END_ACT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUPER_PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CALLBACK_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CALLBACK_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BUSINESS_STATUS_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `PROC_INST_ID_` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_PRO_INST_END` (`END_TIME_`),
  KEY `ACT_IDX_HI_PRO_I_BUSKEY` (`BUSINESS_KEY_`),
  KEY `ACT_IDX_HI_PRO_SUPER_PROCINST` (`SUPER_PROCESS_INSTANCE_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_procinst`
--

LOCK TABLES `act_hi_procinst` WRITE;
/*!40000 ALTER TABLE `act_hi_procinst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_procinst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_taskinst`
--

DROP TABLE IF EXISTS `act_hi_taskinst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_taskinst` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_DEF_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `STATE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `IN_PROGRESS_TIME_` datetime(3) DEFAULT NULL,
  `IN_PROGRESS_STARTED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CLAIM_TIME_` datetime(3) DEFAULT NULL,
  `CLAIMED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUSPENDED_TIME_` datetime(3) DEFAULT NULL,
  `SUSPENDED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `COMPLETED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PRIORITY_` int DEFAULT NULL,
  `IN_PROGRESS_DUE_DATE_` datetime(3) DEFAULT NULL,
  `DUE_DATE_` datetime(3) DEFAULT NULL,
  `FORM_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_TASK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_INST_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_taskinst`
--

LOCK TABLES `act_hi_taskinst` WRITE;
/*!40000 ALTER TABLE `act_hi_taskinst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_taskinst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_tsk_log`
--

DROP TABLE IF EXISTS `act_hi_tsk_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_tsk_log` (
  `ID_` bigint NOT NULL AUTO_INCREMENT,
  `TYPE_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TIME_STAMP_` timestamp(3) NOT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DATA_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_tsk_log`
--

LOCK TABLES `act_hi_tsk_log` WRITE;
/*!40000 ALTER TABLE `act_hi_tsk_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_tsk_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_hi_varinst`
--

DROP TABLE IF EXISTS `act_hi_varinst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_varinst` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VAR_TYPE_` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BYTEARRAY_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DOUBLE_` double DEFAULT NULL,
  `LONG_` bigint DEFAULT NULL,
  `TEXT_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TEXT2_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `META_INFO_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_PROCVAR_NAME_TYPE` (`NAME_`,`VAR_TYPE_`),
  KEY `ACT_IDX_HI_VAR_SCOPE_ID_TYPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_VAR_SUB_ID_TYPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_PROCVAR_PROC_INST` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_PROCVAR_TASK_ID` (`TASK_ID_`),
  KEY `ACT_IDX_HI_PROCVAR_EXE` (`EXECUTION_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_varinst`
--

LOCK TABLES `act_hi_varinst` WRITE;
/*!40000 ALTER TABLE `act_hi_varinst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_hi_varinst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_bytearray`
--

DROP TABLE IF EXISTS `act_id_bytearray`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_bytearray` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `BYTES_` longblob,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_bytearray`
--

LOCK TABLES `act_id_bytearray` WRITE;
/*!40000 ALTER TABLE `act_id_bytearray` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_bytearray` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_group`
--

DROP TABLE IF EXISTS `act_id_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_group` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_group`
--

LOCK TABLES `act_id_group` WRITE;
/*!40000 ALTER TABLE `act_id_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_info`
--

DROP TABLE IF EXISTS `act_id_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_info` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `USER_ID_` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `VALUE_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `PASSWORD_` longblob,
  `PARENT_ID_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_info`
--

LOCK TABLES `act_id_info` WRITE;
/*!40000 ALTER TABLE `act_id_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_membership`
--

DROP TABLE IF EXISTS `act_id_membership`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_membership` (
  `USER_ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `GROUP_ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`USER_ID_`,`GROUP_ID_`),
  KEY `ACT_FK_MEMB_GROUP` (`GROUP_ID_`),
  CONSTRAINT `ACT_FK_MEMB_GROUP` FOREIGN KEY (`GROUP_ID_`) REFERENCES `act_id_group` (`ID_`),
  CONSTRAINT `ACT_FK_MEMB_USER` FOREIGN KEY (`USER_ID_`) REFERENCES `act_id_user` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_membership`
--

LOCK TABLES `act_id_membership` WRITE;
/*!40000 ALTER TABLE `act_id_membership` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_membership` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_priv`
--

DROP TABLE IF EXISTS `act_id_priv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_priv` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_PRIV_NAME` (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_priv`
--

LOCK TABLES `act_id_priv` WRITE;
/*!40000 ALTER TABLE `act_id_priv` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_priv` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_priv_mapping`
--

DROP TABLE IF EXISTS `act_id_priv_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_priv_mapping` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `PRIV_ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `USER_ID_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `GROUP_ID_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_PRIV_MAPPING` (`PRIV_ID_`),
  KEY `ACT_IDX_PRIV_USER` (`USER_ID_`),
  KEY `ACT_IDX_PRIV_GROUP` (`GROUP_ID_`),
  CONSTRAINT `ACT_FK_PRIV_MAPPING` FOREIGN KEY (`PRIV_ID_`) REFERENCES `act_id_priv` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_priv_mapping`
--

LOCK TABLES `act_id_priv_mapping` WRITE;
/*!40000 ALTER TABLE `act_id_priv_mapping` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_priv_mapping` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_property`
--

DROP TABLE IF EXISTS `act_id_property`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_property` (
  `NAME_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `VALUE_` varchar(300) COLLATE utf8mb3_bin DEFAULT NULL,
  `REV_` int DEFAULT NULL,
  PRIMARY KEY (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_property`
--

LOCK TABLES `act_id_property` WRITE;
/*!40000 ALTER TABLE `act_id_property` DISABLE KEYS */;
INSERT INTO `act_id_property` VALUES ('schema.version','7.0.1.1',1);
/*!40000 ALTER TABLE `act_id_property` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_token`
--

DROP TABLE IF EXISTS `act_id_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_token` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `TOKEN_VALUE_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `TOKEN_DATE_` timestamp(3) NULL DEFAULT NULL,
  `IP_ADDRESS_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `USER_AGENT_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `USER_ID_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `TOKEN_DATA_` varchar(2000) COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_token`
--

LOCK TABLES `act_id_token` WRITE;
/*!40000 ALTER TABLE `act_id_token` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_id_user`
--

DROP TABLE IF EXISTS `act_id_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_id_user` (
  `ID_` varchar(64) COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `FIRST_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `LAST_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `DISPLAY_NAME_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `EMAIL_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `PWD_` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
  `PICTURE_ID_` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_id_user`
--

LOCK TABLES `act_id_user` WRITE;
/*!40000 ALTER TABLE `act_id_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_id_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_procdef_info`
--

DROP TABLE IF EXISTS `act_procdef_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_procdef_info` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `INFO_JSON_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_INFO_PROCDEF` (`PROC_DEF_ID_`),
  KEY `ACT_IDX_INFO_PROCDEF` (`PROC_DEF_ID_`),
  KEY `ACT_FK_INFO_JSON_BA` (`INFO_JSON_ID_`),
  CONSTRAINT `ACT_FK_INFO_JSON_BA` FOREIGN KEY (`INFO_JSON_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_INFO_PROCDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`id_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_procdef_info`
--

LOCK TABLES `act_procdef_info` WRITE;
/*!40000 ALTER TABLE `act_procdef_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_procdef_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_re_deployment`
--

DROP TABLE IF EXISTS `act_re_deployment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_re_deployment` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `DEPLOY_TIME_` timestamp(3) NULL DEFAULT NULL,
  `DERIVED_FROM_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DERIVED_FROM_ROOT_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_DEPLOYMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ENGINE_VERSION_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_re_deployment`
--

LOCK TABLES `act_re_deployment` WRITE;
/*!40000 ALTER TABLE `act_re_deployment` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_re_deployment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_re_model`
--

DROP TABLE IF EXISTS `act_re_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_re_model` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LAST_UPDATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `VERSION_` int DEFAULT NULL,
  `META_INFO_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EDITOR_SOURCE_VALUE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EDITOR_SOURCE_EXTRA_VALUE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_MODEL_SOURCE` (`EDITOR_SOURCE_VALUE_ID_`),
  KEY `ACT_FK_MODEL_SOURCE_EXTRA` (`EDITOR_SOURCE_EXTRA_VALUE_ID_`),
  KEY `ACT_FK_MODEL_DEPLOYMENT` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_MODEL_DEPLOYMENT` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `act_re_deployment` (`ID_`),
  CONSTRAINT `ACT_FK_MODEL_SOURCE` FOREIGN KEY (`EDITOR_SOURCE_VALUE_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_MODEL_SOURCE_EXTRA` FOREIGN KEY (`EDITOR_SOURCE_EXTRA_VALUE_ID_`) REFERENCES `act_ge_bytearray` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_re_model`
--

LOCK TABLES `act_re_model` WRITE;
/*!40000 ALTER TABLE `act_re_model` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_re_model` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_re_procdef`
--

DROP TABLE IF EXISTS `act_re_procdef`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_re_procdef` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VERSION_` int NOT NULL,
  `DEPLOYMENT_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RESOURCE_NAME_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DGRM_RESOURCE_NAME_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HAS_START_FORM_KEY_` tinyint DEFAULT NULL,
  `HAS_GRAPHICAL_NOTATION_` tinyint DEFAULT NULL,
  `SUSPENSION_STATE_` int DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `ENGINE_VERSION_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DERIVED_FROM_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DERIVED_FROM_ROOT_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DERIVED_VERSION_` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_PROCDEF` (`KEY_`,`VERSION_`,`DERIVED_VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_re_procdef`
--

LOCK TABLES `act_re_procdef` WRITE;
/*!40000 ALTER TABLE `act_re_procdef` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_re_procdef` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_actinst`
--

DROP TABLE IF EXISTS `act_ru_actinst`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_actinst` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ACT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CALL_PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACT_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `TRANSACTION_ORDER_` int DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_RU_ACTI_START` (`START_TIME_`),
  KEY `ACT_IDX_RU_ACTI_END` (`END_TIME_`),
  KEY `ACT_IDX_RU_ACTI_PROC` (`PROC_INST_ID_`),
  KEY `ACT_IDX_RU_ACTI_PROC_ACT` (`PROC_INST_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_RU_ACTI_EXEC` (`EXECUTION_ID_`),
  KEY `ACT_IDX_RU_ACTI_EXEC_ACT` (`EXECUTION_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_RU_ACTI_TASK` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_actinst`
--

LOCK TABLES `act_ru_actinst` WRITE;
/*!40000 ALTER TABLE `act_ru_actinst` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_actinst` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_deadletter_job`
--

DROP TABLE IF EXISTS `act_ru_deadletter_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_deadletter_job` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_DJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_DJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_DJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_DEADLETTER_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_DEADLETTER_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`id_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `act_ru_execution` (`id_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_deadletter_job`
--

LOCK TABLES `act_ru_deadletter_job` WRITE;
/*!40000 ALTER TABLE `act_ru_deadletter_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_deadletter_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_entitylink`
--

DROP TABLE IF EXISTS `act_ru_entitylink`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_entitylink` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `LINK_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REF_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REF_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REF_SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ROOT_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ROOT_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HIERARCHY_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_ENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_REF_SCOPE` (`REF_SCOPE_ID_`,`REF_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_ROOT_SCOPE` (`ROOT_SCOPE_ID_`,`ROOT_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_entitylink`
--

LOCK TABLES `act_ru_entitylink` WRITE;
/*!40000 ALTER TABLE `act_ru_entitylink` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_entitylink` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_event_subscr`
--

DROP TABLE IF EXISTS `act_ru_event_subscr`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_event_subscr` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `EVENT_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `EVENT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACTIVITY_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CONFIGURATION_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATED_` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EVENT_SUBSCR_CONFIG_` (`CONFIGURATION_`),
  KEY `ACT_IDX_EVENT_SUBSCR_SCOPEREF_` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_EVENT_EXEC` (`EXECUTION_ID_`),
  CONSTRAINT `ACT_FK_EVENT_EXEC` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`id_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_event_subscr`
--

LOCK TABLES `act_ru_event_subscr` WRITE;
/*!40000 ALTER TABLE `act_ru_event_subscr` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_event_subscr` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_execution`
--

DROP TABLE IF EXISTS `act_ru_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_execution` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BUSINESS_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUPER_EXEC_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ROOT_PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `IS_ACTIVE_` tinyint DEFAULT NULL,
  `IS_CONCURRENT_` tinyint DEFAULT NULL,
  `IS_SCOPE_` tinyint DEFAULT NULL,
  `IS_EVENT_SCOPE_` tinyint DEFAULT NULL,
  `IS_MI_ROOT_` tinyint DEFAULT NULL,
  `SUSPENSION_STATE_` int DEFAULT NULL,
  `CACHED_ENT_STATE_` int DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `START_ACT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `START_TIME_` datetime(3) DEFAULT NULL,
  `START_USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `IS_COUNT_ENABLED_` tinyint DEFAULT NULL,
  `EVT_SUBSCR_COUNT_` int DEFAULT NULL,
  `TASK_COUNT_` int DEFAULT NULL,
  `JOB_COUNT_` int DEFAULT NULL,
  `TIMER_JOB_COUNT_` int DEFAULT NULL,
  `SUSP_JOB_COUNT_` int DEFAULT NULL,
  `DEADLETTER_JOB_COUNT_` int DEFAULT NULL,
  `EXTERNAL_WORKER_JOB_COUNT_` int DEFAULT NULL,
  `VAR_COUNT_` int DEFAULT NULL,
  `ID_LINK_COUNT_` int DEFAULT NULL,
  `CALLBACK_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CALLBACK_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BUSINESS_STATUS_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EXEC_BUSKEY` (`BUSINESS_KEY_`),
  KEY `ACT_IDC_EXEC_ROOT` (`ROOT_PROC_INST_ID_`),
  KEY `ACT_IDX_EXEC_REF_ID_` (`REFERENCE_ID_`),
  KEY `ACT_FK_EXE_PROCINST` (`PROC_INST_ID_`),
  KEY `ACT_FK_EXE_PARENT` (`PARENT_ID_`),
  KEY `ACT_FK_EXE_SUPER` (`SUPER_EXEC_`),
  KEY `ACT_FK_EXE_PROCDEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_EXE_PARENT` FOREIGN KEY (`PARENT_ID_`) REFERENCES `act_ru_execution` (`ID_`) ON DELETE CASCADE,
  CONSTRAINT `ACT_FK_EXE_PROCDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_EXE_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `act_ru_execution` (`ID_`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `ACT_FK_EXE_SUPER` FOREIGN KEY (`SUPER_EXEC_`) REFERENCES `act_ru_execution` (`ID_`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_execution`
--

LOCK TABLES `act_ru_execution` WRITE;
/*!40000 ALTER TABLE `act_ru_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_external_job`
--

DROP TABLE IF EXISTS `act_ru_external_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_external_job` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_EJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  CONSTRAINT `ACT_FK_EXTERNAL_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_EXTERNAL_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `act_ge_bytearray` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_external_job`
--

LOCK TABLES `act_ru_external_job` WRITE;
/*!40000 ALTER TABLE `act_ru_external_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_external_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_history_job`
--

DROP TABLE IF EXISTS `act_ru_history_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_history_job` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ADV_HANDLER_CFG_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_history_job`
--

LOCK TABLES `act_ru_history_job` WRITE;
/*!40000 ALTER TABLE `act_ru_history_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_history_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_identitylink`
--

DROP TABLE IF EXISTS `act_ru_identitylink`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_identitylink` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `GROUP_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_IDENT_LNK_USER` (`USER_ID_`),
  KEY `ACT_IDX_IDENT_LNK_GROUP` (`GROUP_ID_`),
  KEY `ACT_IDX_IDENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_IDENT_LNK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_IDENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_ATHRZ_PROCEDEF` (`PROC_DEF_ID_`),
  KEY `ACT_FK_TSKASS_TASK` (`TASK_ID_`),
  KEY `ACT_FK_IDL_PROCINST` (`PROC_INST_ID_`),
  CONSTRAINT `ACT_FK_ATHRZ_PROCEDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_IDL_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `act_ru_execution` (`ID_`),
  CONSTRAINT `ACT_FK_TSKASS_TASK` FOREIGN KEY (`TASK_ID_`) REFERENCES `act_ru_task` (`id_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_identitylink`
--

LOCK TABLES `act_ru_identitylink` WRITE;
/*!40000 ALTER TABLE `act_ru_identitylink` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_identitylink` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_job`
--

DROP TABLE IF EXISTS `act_ru_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_job` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_JOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_JOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_JOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `act_ru_execution` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_job`
--

LOCK TABLES `act_ru_job` WRITE;
/*!40000 ALTER TABLE `act_ru_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_suspended_job`
--

DROP TABLE IF EXISTS `act_ru_suspended_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_suspended_job` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_SJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_SJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_SJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_SUSPENDED_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_SUSPENDED_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `act_ru_execution` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_suspended_job`
--

LOCK TABLES `act_ru_suspended_job` WRITE;
/*!40000 ALTER TABLE `act_ru_suspended_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_suspended_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_task`
--

DROP TABLE IF EXISTS `act_ru_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_task` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `STATE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_DEF_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DELEGATION_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PRIORITY_` int DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `IN_PROGRESS_TIME_` datetime(3) DEFAULT NULL,
  `IN_PROGRESS_STARTED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CLAIM_TIME_` datetime(3) DEFAULT NULL,
  `CLAIMED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUSPENDED_TIME_` datetime(3) DEFAULT NULL,
  `SUSPENDED_BY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `IN_PROGRESS_DUE_DATE_` datetime(3) DEFAULT NULL,
  `DUE_DATE_` datetime(3) DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUSPENSION_STATE_` int DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `FORM_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `IS_COUNT_ENABLED_` tinyint DEFAULT NULL,
  `VAR_COUNT_` int DEFAULT NULL,
  `ID_LINK_COUNT_` int DEFAULT NULL,
  `SUB_TASK_COUNT_` int DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_TASK_CREATE` (`CREATE_TIME_`),
  KEY `ACT_IDX_TASK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TASK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TASK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_TASK_EXE` (`EXECUTION_ID_`),
  KEY `ACT_FK_TASK_PROCINST` (`PROC_INST_ID_`),
  KEY `ACT_FK_TASK_PROCDEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_TASK_EXE` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`ID_`),
  CONSTRAINT `ACT_FK_TASK_PROCDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_TASK_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `act_ru_execution` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_task`
--

LOCK TABLES `act_ru_task` WRITE;
/*!40000 ALTER TABLE `act_ru_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_timer_job`
--

DROP TABLE IF EXISTS `act_ru_timer_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_timer_job` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_TIMER_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_TIMER_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_TIMER_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_TIMER_JOB_DUEDATE` (`DUEDATE_`),
  KEY `ACT_IDX_TJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_TIMER_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_TIMER_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_TIMER_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `act_re_procdef` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `act_ru_execution` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_timer_job`
--

LOCK TABLES `act_ru_timer_job` WRITE;
/*!40000 ALTER TABLE `act_ru_timer_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_timer_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `act_ru_variable`
--

DROP TABLE IF EXISTS `act_ru_variable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_ru_variable` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BYTEARRAY_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DOUBLE_` double DEFAULT NULL,
  `LONG_` bigint DEFAULT NULL,
  `TEXT_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TEXT2_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `META_INFO_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_RU_VAR_SCOPE_ID_TYPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_RU_VAR_SUB_ID_TYPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_VAR_BYTEARRAY` (`BYTEARRAY_ID_`),
  KEY `ACT_IDX_VARIABLE_TASK_ID` (`TASK_ID_`),
  KEY `ACT_FK_VAR_EXE` (`EXECUTION_ID_`),
  KEY `ACT_FK_VAR_PROCINST` (`PROC_INST_ID_`),
  CONSTRAINT `ACT_FK_VAR_BYTEARRAY` FOREIGN KEY (`BYTEARRAY_ID_`) REFERENCES `act_ge_bytearray` (`ID_`),
  CONSTRAINT `ACT_FK_VAR_EXE` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `act_ru_execution` (`ID_`),
  CONSTRAINT `ACT_FK_VAR_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `act_ru_execution` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_ru_variable`
--

LOCK TABLES `act_ru_variable` WRITE;
/*!40000 ALTER TABLE `act_ru_variable` DISABLE KEYS */;
/*!40000 ALTER TABLE `act_ru_variable` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `addrbook_employee`
--

DROP TABLE IF EXISTS `addrbook_employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `addrbook_employee` (
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `emp_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工姓名',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `org_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属机构代码',
  `org_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属机构名称',
  `position` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '岗位',
  `self_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '自我描述',
  `responsible_product_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '负责产品ID列表(JSON数组)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-在职, RESIGNED-离职',
  `maintainer_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '维护人工号',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人 emp_id（同步时为 SYSTEM）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最后更新人 emp_id',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除, 1-已删除',
  PRIMARY KEY (`emp_id`),
  KEY `idx_org_code` (`org_code`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通讯录员工表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `addrbook_employee`
--

LOCK TABLES `addrbook_employee` WRITE;
/*!40000 ALTER TABLE `addrbook_employee` DISABLE KEYS */;
INSERT INTO `addrbook_employee` VALUES ('TEST_E001','员工 TEST_E001','13800000001',NULL,'ORG_SZ_001','深圳分行','客户经理',NULL,'[\"P001\",\"P002\"]','ACTIVE',NULL,NULL,'2026-04-25 15:22:17',NULL,'2026-04-25 15:22:17',0);
/*!40000 ALTER TABLE `addrbook_employee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '日志ID',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '链路追踪ID',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作人工号',
  `emp_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人姓名',
  `biz_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务类型',
  `biz_action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务动作',
  `resource_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源URL',
  `request_method` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求方法',
  `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求参数（脱敏）',
  `response_status` int DEFAULT NULL COMMENT '响应状态码',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户代理',
  `execution_time` int DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作原因（高危动作必填）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES ('0217835a9c194bcbbd3f60699877df19',NULL,'admin',NULL,'SQL_PROBE','EXECUTE_SQL',NULL,NULL,'SELECT COUNT(*) AS cnt FROM PT_RESOURCE LIMIT 1000',200,NULL,NULL,NULL,NULL,'count','2026-04-10 11:38:14'),('22892c8cff3a4c6a9cecd0efe8d67689',NULL,'admin',NULL,'SQL_PROBE','EXECUTE_SQL',NULL,NULL,'SELECT COUNT(*) AS cnt FROM PT_RESOURCE LIMIT 1000',200,NULL,NULL,NULL,NULL,'count','2026-04-10 12:20:13'),('4baf0386c31d4e1aa11785f00acd4235',NULL,'admin',NULL,'SQL_PROBE','EXECUTE_SQL',NULL,NULL,'SELECT 1 AS ok LIMIT 1000',200,NULL,NULL,NULL,NULL,'test','2026-04-10 12:20:11'),('c9274bde370449aa814a1a520eebabd3',NULL,'admin',NULL,'SQL_PROBE','EXECUTE_SQL',NULL,NULL,'SELECT 1 LIMIT 1000',200,NULL,NULL,NULL,NULL,'test','2026-04-10 11:38:11');
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_file_rel`
--

DROP TABLE IF EXISTS `biz_file_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_file_rel` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联ID',
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务类型(BizType或业务域)',
  `biz_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务ID(字符串)',
  `file_object_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件对象ID',
  `file_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用途：ATTACHMENT/PHOTO/...',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_file` (`biz_type`,`biz_id`,`file_object_id`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_file` (`file_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务-附件关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_file_rel`
--

LOCK TABLES `biz_file_rel` WRITE;
/*!40000 ALTER TABLE `biz_file_rel` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_file_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_process_map`
--

DROP TABLE IF EXISTS `biz_process_map`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_process_map` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '映射ID',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务键（格式：BIZ_TYPE:{id}）',
  `biz_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务类型',
  `biz_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务ID',
  `process_definition_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程定义KEY',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Flowable流程实例ID',
  `start_user` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '发起人工号',
  `current_assignee` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前处理人工号',
  `candidate_groups` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '候选组列表（JSON数组）',
  `process_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'RUNNING' COMMENT '流程状态：RUNNING-运行中, COMPLETED-已完成, CANCELLED-已取消',
  `title` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程标题',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_key` (`business_key`),
  UNIQUE KEY `uk_process_instance` (`process_instance_id`),
  KEY `idx_biz_type_id` (`biz_type`,`biz_id`),
  KEY `idx_start_user` (`start_user`),
  KEY `idx_current_assignee` (`current_assignee`),
  KEY `idx_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务流程映射表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_process_map`
--

LOCK TABLES `biz_process_map` WRITE;
/*!40000 ALTER TABLE `biz_process_map` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_process_map` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cust_alloc_relation`
--

DROP TABLE IF EXISTS `cust_alloc_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_alloc_relation` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `alloc_dim` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调整维度：RULE/ACCOUNT',
  `biz_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务种类',
  `account_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号(账号维度必填)',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `ratio` decimal(5,2) NOT NULL COMMENT '比例(0-100)',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `end_date` date DEFAULT NULL COMMENT '失效日期',
  `source_batch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源批次号(可选)',
  `source_process_date` date DEFAULT NULL COMMENT '来源业务日期(可选)',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
-- Table structure for table `cust_claim`
--

DROP TABLE IF EXISTS `cust_claim`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_claim` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '认领ID',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '认领机构代码',
  `claimed_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '认领人工号',
  `maintainer_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '维护人工号',
  `claim_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'CLAIMED' COMMENT '认领状态：CLAIMED-已认领, CANCELLED-已取消',
  `claim_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '认领时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '取消原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_org` (`cust_id`,`org_id`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_claimed_by` (`claimed_by`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_status` (`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户认领关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_claim`
--

LOCK TABLES `cust_claim` WRITE;
/*!40000 ALTER TABLE `cust_claim` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_claim` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cust_index_result`
--

DROP TABLE IF EXISTS `cust_index_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_index_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `data_date` date NOT NULL COMMENT '数据日期',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据版本',
  `cust_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `val_1` decimal(20,4) DEFAULT NULL COMMENT 'val_1',
  `val_2` decimal(20,4) DEFAULT NULL COMMENT 'val_2',
  `val_3` decimal(20,4) DEFAULT NULL COMMENT 'val_3',
  `val_4` decimal(20,4) DEFAULT NULL COMMENT 'val_4',
  `val_5` decimal(20,4) DEFAULT NULL COMMENT 'val_5',
  `val_6` decimal(20,4) DEFAULT NULL COMMENT 'val_6',
  `val_7` decimal(20,4) DEFAULT NULL COMMENT 'val_7',
  `val_8` decimal(20,4) DEFAULT NULL COMMENT 'val_8',
  `val_9` decimal(20,4) DEFAULT NULL COMMENT 'val_9',
  `val_10` decimal(20,4) DEFAULT NULL COMMENT 'val_10',
  `val_11` decimal(20,4) DEFAULT NULL COMMENT 'val_11',
  `val_12` decimal(20,4) DEFAULT NULL COMMENT 'val_12',
  `val_13` decimal(20,4) DEFAULT NULL COMMENT 'val_13',
  `val_14` decimal(20,4) DEFAULT NULL COMMENT 'val_14',
  `val_15` decimal(20,4) DEFAULT NULL COMMENT 'val_15',
  `val_16` decimal(20,4) DEFAULT NULL COMMENT 'val_16',
  `val_17` decimal(20,4) DEFAULT NULL COMMENT 'val_17',
  `val_18` decimal(20,4) DEFAULT NULL COMMENT 'val_18',
  `val_19` decimal(20,4) DEFAULT NULL COMMENT 'val_19',
  `val_20` decimal(20,4) DEFAULT NULL COMMENT 'val_20',
  `val_21` decimal(20,4) DEFAULT NULL COMMENT 'val_21',
  `val_22` decimal(20,4) DEFAULT NULL COMMENT 'val_22',
  `val_23` decimal(20,4) DEFAULT NULL COMMENT 'val_23',
  `val_24` decimal(20,4) DEFAULT NULL COMMENT 'val_24',
  `val_25` decimal(20,4) DEFAULT NULL COMMENT 'val_25',
  `val_26` decimal(20,4) DEFAULT NULL COMMENT 'val_26',
  `val_27` decimal(20,4) DEFAULT NULL COMMENT 'val_27',
  `val_28` decimal(20,4) DEFAULT NULL COMMENT 'val_28',
  `val_29` decimal(20,4) DEFAULT NULL COMMENT 'val_29',
  `val_30` decimal(20,4) DEFAULT NULL COMMENT 'val_30',
  `val_31` decimal(20,4) DEFAULT NULL COMMENT 'val_31',
  `val_32` decimal(20,4) DEFAULT NULL COMMENT 'val_32',
  `val_33` decimal(20,4) DEFAULT NULL COMMENT 'val_33',
  `val_34` decimal(20,4) DEFAULT NULL COMMENT 'val_34',
  `val_35` decimal(20,4) DEFAULT NULL COMMENT 'val_35',
  `val_36` decimal(20,4) DEFAULT NULL COMMENT 'val_36',
  `val_37` decimal(20,4) DEFAULT NULL COMMENT 'val_37',
  `val_38` decimal(20,4) DEFAULT NULL COMMENT 'val_38',
  `val_39` decimal(20,4) DEFAULT NULL COMMENT 'val_39',
  `val_40` decimal(20,4) DEFAULT NULL COMMENT 'val_40',
  `val_41` decimal(20,4) DEFAULT NULL COMMENT 'val_41',
  `val_42` decimal(20,4) DEFAULT NULL COMMENT 'val_42',
  `val_43` decimal(20,4) DEFAULT NULL COMMENT 'val_43',
  `val_44` decimal(20,4) DEFAULT NULL COMMENT 'val_44',
  `val_45` decimal(20,4) DEFAULT NULL COMMENT 'val_45',
  `val_46` decimal(20,4) DEFAULT NULL COMMENT 'val_46',
  `val_47` decimal(20,4) DEFAULT NULL COMMENT 'val_47',
  `val_48` decimal(20,4) DEFAULT NULL COMMENT 'val_48',
  `val_49` decimal(20,4) DEFAULT NULL COMMENT 'val_49',
  `val_50` decimal(20,4) DEFAULT NULL COMMENT 'val_50',
  `val_51` decimal(20,4) DEFAULT NULL COMMENT 'val_51',
  `val_52` decimal(20,4) DEFAULT NULL COMMENT 'val_52',
  `val_53` decimal(20,4) DEFAULT NULL COMMENT 'val_53',
  `val_54` decimal(20,4) DEFAULT NULL COMMENT 'val_54',
  `val_55` decimal(20,4) DEFAULT NULL COMMENT 'val_55',
  `val_56` decimal(20,4) DEFAULT NULL COMMENT 'val_56',
  `val_57` decimal(20,4) DEFAULT NULL COMMENT 'val_57',
  `val_58` decimal(20,4) DEFAULT NULL COMMENT 'val_58',
  `val_59` decimal(20,4) DEFAULT NULL COMMENT 'val_59',
  `val_60` decimal(20,4) DEFAULT NULL COMMENT 'val_60',
  `val_61` decimal(20,4) DEFAULT NULL COMMENT 'val_61',
  `val_62` decimal(20,4) DEFAULT NULL COMMENT 'val_62',
  `val_63` decimal(20,4) DEFAULT NULL COMMENT 'val_63',
  `val_64` decimal(20,4) DEFAULT NULL COMMENT 'val_64',
  `val_65` decimal(20,4) DEFAULT NULL COMMENT 'val_65',
  `val_66` decimal(20,4) DEFAULT NULL COMMENT 'val_66',
  `val_67` decimal(20,4) DEFAULT NULL COMMENT 'val_67',
  `val_68` decimal(20,4) DEFAULT NULL COMMENT 'val_68',
  `val_69` decimal(20,4) DEFAULT NULL COMMENT 'val_69',
  `val_70` decimal(20,4) DEFAULT NULL COMMENT 'val_70',
  `val_71` decimal(20,4) DEFAULT NULL COMMENT 'val_71',
  `val_72` decimal(20,4) DEFAULT NULL COMMENT 'val_72',
  `val_73` decimal(20,4) DEFAULT NULL COMMENT 'val_73',
  `val_74` decimal(20,4) DEFAULT NULL COMMENT 'val_74',
  `val_75` decimal(20,4) DEFAULT NULL COMMENT 'val_75',
  `val_76` decimal(20,4) DEFAULT NULL COMMENT 'val_76',
  `val_77` decimal(20,4) DEFAULT NULL COMMENT 'val_77',
  `val_78` decimal(20,4) DEFAULT NULL COMMENT 'val_78',
  `val_79` decimal(20,4) DEFAULT NULL COMMENT 'val_79',
  `val_80` decimal(20,4) DEFAULT NULL COMMENT 'val_80',
  `val_81` decimal(20,4) DEFAULT NULL COMMENT 'val_81',
  `val_82` decimal(20,4) DEFAULT NULL COMMENT 'val_82',
  `val_83` decimal(20,4) DEFAULT NULL COMMENT 'val_83',
  `val_84` decimal(20,4) DEFAULT NULL COMMENT 'val_84',
  `val_85` decimal(20,4) DEFAULT NULL COMMENT 'val_85',
  `val_86` decimal(20,4) DEFAULT NULL COMMENT 'val_86',
  `val_87` decimal(20,4) DEFAULT NULL COMMENT 'val_87',
  `val_88` decimal(20,4) DEFAULT NULL COMMENT 'val_88',
  `val_89` decimal(20,4) DEFAULT NULL COMMENT 'val_89',
  `val_90` decimal(20,4) DEFAULT NULL COMMENT 'val_90',
  `val_91` decimal(20,4) DEFAULT NULL COMMENT 'val_91',
  `val_92` decimal(20,4) DEFAULT NULL COMMENT 'val_92',
  `val_93` decimal(20,4) DEFAULT NULL COMMENT 'val_93',
  `val_94` decimal(20,4) DEFAULT NULL COMMENT 'val_94',
  `val_95` decimal(20,4) DEFAULT NULL COMMENT 'val_95',
  `val_96` decimal(20,4) DEFAULT NULL COMMENT 'val_96',
  `val_97` decimal(20,4) DEFAULT NULL COMMENT 'val_97',
  `val_98` decimal(20,4) DEFAULT NULL COMMENT 'val_98',
  `val_99` decimal(20,4) DEFAULT NULL COMMENT 'val_99',
  `val_100` decimal(20,4) DEFAULT NULL COMMENT 'val_100',
  `val_101` decimal(20,4) DEFAULT NULL COMMENT 'val_101',
  `val_102` decimal(20,4) DEFAULT NULL COMMENT 'val_102',
  `val_103` decimal(20,4) DEFAULT NULL COMMENT 'val_103',
  `val_104` decimal(20,4) DEFAULT NULL COMMENT 'val_104',
  `val_105` decimal(20,4) DEFAULT NULL COMMENT 'val_105',
  `val_106` decimal(20,4) DEFAULT NULL COMMENT 'val_106',
  `val_107` decimal(20,4) DEFAULT NULL COMMENT 'val_107',
  `val_108` decimal(20,4) DEFAULT NULL COMMENT 'val_108',
  `val_109` decimal(20,4) DEFAULT NULL COMMENT 'val_109',
  `val_110` decimal(20,4) DEFAULT NULL COMMENT 'val_110',
  `val_111` decimal(20,4) DEFAULT NULL COMMENT 'val_111',
  `val_112` decimal(20,4) DEFAULT NULL COMMENT 'val_112',
  `val_113` decimal(20,4) DEFAULT NULL COMMENT 'val_113',
  `val_114` decimal(20,4) DEFAULT NULL COMMENT 'val_114',
  `val_115` decimal(20,4) DEFAULT NULL COMMENT 'val_115',
  `val_116` decimal(20,4) DEFAULT NULL COMMENT 'val_116',
  `val_117` decimal(20,4) DEFAULT NULL COMMENT 'val_117',
  `val_118` decimal(20,4) DEFAULT NULL COMMENT 'val_118',
  `val_119` decimal(20,4) DEFAULT NULL COMMENT 'val_119',
  `val_120` decimal(20,4) DEFAULT NULL COMMENT 'val_120',
  `val_121` decimal(20,4) DEFAULT NULL COMMENT 'val_121',
  `val_122` decimal(20,4) DEFAULT NULL COMMENT 'val_122',
  `val_123` decimal(20,4) DEFAULT NULL COMMENT 'val_123',
  `val_124` decimal(20,4) DEFAULT NULL COMMENT 'val_124',
  `val_125` decimal(20,4) DEFAULT NULL COMMENT 'val_125',
  `val_126` decimal(20,4) DEFAULT NULL COMMENT 'val_126',
  `val_127` decimal(20,4) DEFAULT NULL COMMENT 'val_127',
  `val_128` decimal(20,4) DEFAULT NULL COMMENT 'val_128',
  `val_129` decimal(20,4) DEFAULT NULL COMMENT 'val_129',
  `val_130` decimal(20,4) DEFAULT NULL COMMENT 'val_130',
  `val_131` decimal(20,4) DEFAULT NULL COMMENT 'val_131',
  `val_132` decimal(20,4) DEFAULT NULL COMMENT 'val_132',
  `val_133` decimal(20,4) DEFAULT NULL COMMENT 'val_133',
  `val_134` decimal(20,4) DEFAULT NULL COMMENT 'val_134',
  `val_135` decimal(20,4) DEFAULT NULL COMMENT 'val_135',
  `val_136` decimal(20,4) DEFAULT NULL COMMENT 'val_136',
  `val_137` decimal(20,4) DEFAULT NULL COMMENT 'val_137',
  `val_138` decimal(20,4) DEFAULT NULL COMMENT 'val_138',
  `val_139` decimal(20,4) DEFAULT NULL COMMENT 'val_139',
  `val_140` decimal(20,4) DEFAULT NULL COMMENT 'val_140',
  `val_141` decimal(20,4) DEFAULT NULL COMMENT 'val_141',
  `val_142` decimal(20,4) DEFAULT NULL COMMENT 'val_142',
  `val_143` decimal(20,4) DEFAULT NULL COMMENT 'val_143',
  `val_144` decimal(20,4) DEFAULT NULL COMMENT 'val_144',
  `val_145` decimal(20,4) DEFAULT NULL COMMENT 'val_145',
  `val_146` decimal(20,4) DEFAULT NULL COMMENT 'val_146',
  `val_147` decimal(20,4) DEFAULT NULL COMMENT 'val_147',
  `val_148` decimal(20,4) DEFAULT NULL COMMENT 'val_148',
  `val_149` decimal(20,4) DEFAULT NULL COMMENT 'val_149',
  `val_150` decimal(20,4) DEFAULT NULL COMMENT 'val_150',
  `val_151` decimal(20,4) DEFAULT NULL COMMENT 'val_151',
  `val_152` decimal(20,4) DEFAULT NULL COMMENT 'val_152',
  `val_153` decimal(20,4) DEFAULT NULL COMMENT 'val_153',
  `val_154` decimal(20,4) DEFAULT NULL COMMENT 'val_154',
  `val_155` decimal(20,4) DEFAULT NULL COMMENT 'val_155',
  `val_156` decimal(20,4) DEFAULT NULL COMMENT 'val_156',
  `val_157` decimal(20,4) DEFAULT NULL COMMENT 'val_157',
  `val_158` decimal(20,4) DEFAULT NULL COMMENT 'val_158',
  `val_159` decimal(20,4) DEFAULT NULL COMMENT 'val_159',
  `val_160` decimal(20,4) DEFAULT NULL COMMENT 'val_160',
  `val_161` decimal(20,4) DEFAULT NULL COMMENT 'val_161',
  `val_162` decimal(20,4) DEFAULT NULL COMMENT 'val_162',
  `val_163` decimal(20,4) DEFAULT NULL COMMENT 'val_163',
  `val_164` decimal(20,4) DEFAULT NULL COMMENT 'val_164',
  `val_165` decimal(20,4) DEFAULT NULL COMMENT 'val_165',
  `val_166` decimal(20,4) DEFAULT NULL COMMENT 'val_166',
  `val_167` decimal(20,4) DEFAULT NULL COMMENT 'val_167',
  `val_168` decimal(20,4) DEFAULT NULL COMMENT 'val_168',
  `val_169` decimal(20,4) DEFAULT NULL COMMENT 'val_169',
  `val_170` decimal(20,4) DEFAULT NULL COMMENT 'val_170',
  `val_171` decimal(20,4) DEFAULT NULL COMMENT 'val_171',
  `val_172` decimal(20,4) DEFAULT NULL COMMENT 'val_172',
  `val_173` decimal(20,4) DEFAULT NULL COMMENT 'val_173',
  `val_174` decimal(20,4) DEFAULT NULL COMMENT 'val_174',
  `val_175` decimal(20,4) DEFAULT NULL COMMENT 'val_175',
  `val_176` decimal(20,4) DEFAULT NULL COMMENT 'val_176',
  `val_177` decimal(20,4) DEFAULT NULL COMMENT 'val_177',
  `val_178` decimal(20,4) DEFAULT NULL COMMENT 'val_178',
  `val_179` decimal(20,4) DEFAULT NULL COMMENT 'val_179',
  `val_180` decimal(20,4) DEFAULT NULL COMMENT 'val_180',
  `val_181` decimal(20,4) DEFAULT NULL COMMENT 'val_181',
  `val_182` decimal(20,4) DEFAULT NULL COMMENT 'val_182',
  `val_183` decimal(20,4) DEFAULT NULL COMMENT 'val_183',
  `val_184` decimal(20,4) DEFAULT NULL COMMENT 'val_184',
  `val_185` decimal(20,4) DEFAULT NULL COMMENT 'val_185',
  `val_186` decimal(20,4) DEFAULT NULL COMMENT 'val_186',
  `val_187` decimal(20,4) DEFAULT NULL COMMENT 'val_187',
  `val_188` decimal(20,4) DEFAULT NULL COMMENT 'val_188',
  `val_189` decimal(20,4) DEFAULT NULL COMMENT 'val_189',
  `val_190` decimal(20,4) DEFAULT NULL COMMENT 'val_190',
  `val_191` decimal(20,4) DEFAULT NULL COMMENT 'val_191',
  `val_192` decimal(20,4) DEFAULT NULL COMMENT 'val_192',
  `val_193` decimal(20,4) DEFAULT NULL COMMENT 'val_193',
  `val_194` decimal(20,4) DEFAULT NULL COMMENT 'val_194',
  `val_195` decimal(20,4) DEFAULT NULL COMMENT 'val_195',
  `val_196` decimal(20,4) DEFAULT NULL COMMENT 'val_196',
  `val_197` decimal(20,4) DEFAULT NULL COMMENT 'val_197',
  `val_198` decimal(20,4) DEFAULT NULL COMMENT 'val_198',
  `val_199` decimal(20,4) DEFAULT NULL COMMENT 'val_199',
  `val_200` decimal(20,4) DEFAULT NULL COMMENT 'val_200',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_date_ver` (`cust_id`,`data_date`,`version`),
  KEY `idx_date_ver` (`data_date`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=355 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户指标结果宽表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_index_result`
--

LOCK TABLES `cust_index_result` WRITE;
/*!40000 ALTER TABLE `cust_index_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_index_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cust_lead`
--

DROP TABLE IF EXISTS `cust_lead`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_lead` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '线索ID',
  `lead_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '线索编号',
  `lead_op` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'CREATE' COMMENT '线索操作：CREATE/UPDATE/DELETE',
  `source_cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联客户ID(UPDATE/DELETE时必填)',
  `prev_lead_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上一版本线索ID(UPDATE/DELETE时必填)',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号(从1开始)',
  `is_latest` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否最新版本(1-是,0-否)',
  `cust_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '统一社会信用代码(纳税人识别号)',
  `tag_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '标签ID列表(JSON数组)',
  `contact_person` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属行业',
  `group_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属集团类型(字典)',
  `customer_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户类型(字典)',
  `is_keystone` tinyint(1) DEFAULT NULL COMMENT '是否基石客户(1-是,0-否)',
  `enterprise_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '企业类型(字典)',
  `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened` tinyint(1) DEFAULT NULL COMMENT '是否开户(1-是,0-否)',
  `customer_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '客户说明',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额',
  `lead_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '线索来源',
  `lead_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'DRAFT' COMMENT '线索状态：DRAFT-草稿, SUBMITTED-已提交, IN_APPROVAL-审批中, APPROVED-已通过, REJECTED-已驳回',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构代码',
  `assigned_to` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分配用户',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人工号',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键（LEAD:{id}）',
  `import_batch_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '导入批次ID(批量导入)',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '备注',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_status` (`lead_status`),
  KEY `idx_import_batch` (`import_batch_id`),
  KEY `idx_cust_name` (`cust_name`),
  KEY `idx_unified_credit_code` (`unified_credit_code`),
  KEY `idx_source_cust` (`source_cust_id`),
  KEY `idx_lead_op_status` (`lead_op`,`lead_status`),
  KEY `idx_is_latest` (`is_latest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户线索表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_lead`
--

LOCK TABLES `cust_lead` WRITE;
/*!40000 ALTER TABLE `cust_lead` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_lead` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cust_master`
--

DROP TABLE IF EXISTS `cust_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_master` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `cust_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户编号',
  `cust_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '统一社会信用代码(纳税人识别号)',
  `contact_person` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属行业',
  `group_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属集团类型(字典)',
  `customer_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户类型(字典)',
  `is_keystone` tinyint(1) DEFAULT NULL COMMENT '是否基石客户(1-是,0-否)',
  `enterprise_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '企业类型(字典)',
  `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened` tinyint(1) DEFAULT NULL COMMENT '是否开户(1-是,0-否)',
  `customer_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '客户说明',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源机构代码（不承载可见性）',
  `lead_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源线索ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, INACTIVE-停用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除标记(0-否,1-是)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_no` (`cust_no`),
  UNIQUE KEY `uk_cust_name` (`cust_name`),
  KEY `idx_lead_id` (`lead_id`),
  KEY `idx_unified_credit_code` (`unified_credit_code`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户主档表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_master`
--

LOCK TABLES `cust_master` WRITE;
/*!40000 ALTER TABLE `cust_master` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_master` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cust_tag`
--

DROP TABLE IF EXISTS `cust_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_tag` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签ID',
  `tag_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签名称',
  `tag_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签编码',
  `tag_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标签分类',
  `tag_priority` int NOT NULL DEFAULT '0' COMMENT '标签优先级(数字越大越靠前)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标签描述',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-否, 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_code` (`tag_code`),
  UNIQUE KEY `uk_tag_name` (`tag_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户标签表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_tag`
--

LOCK TABLES `cust_tag` WRITE;
/*!40000 ALTER TABLE `cust_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cust_tag_rel`
--

DROP TABLE IF EXISTS `cust_tag_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cust_tag_rel` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联ID',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `tag_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签ID',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_tag` (`cust_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户-标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cust_tag_rel`
--

LOCK TABLES `cust_tag_rel` WRITE;
/*!40000 ALTER TABLE `cust_tag_rel` DISABLE KEYS */;
/*!40000 ALTER TABLE `cust_tag_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `doc_info`
--

DROP TABLE IF EXISTS `doc_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `doc_info` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文档ID',
  `doc_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文档标题',
  `doc_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文档分类',
  `file_object_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件对象ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `doc_info`
--

LOCK TABLES `doc_info` WRITE;
/*!40000 ALTER TABLE `doc_info` DISABLE KEYS */;
INSERT INTO `doc_info` VALUES ('15fdf8c56e1e4d56a04183d00409127d','TEST_产品手册B','PRODUCT_DOC','FILE_dc11488e','ACTIVE','tester','2026-04-25 15:22:18',NULL,NULL),('32bdb0c05cdf4b188931d4d69e35dac8','TEST_运营指南','OPERATION_DOC','FILE_db424bde','ACTIVE','tester','2026-04-25 15:22:18',NULL,NULL),('3daedcaaa97b4742bbb63e7385df6d26','TEST_产品手册A','PRODUCT_DOC','FILE_349f8a9d','ACTIVE','tester','2026-04-25 15:22:18',NULL,NULL),('f959f24c3e93413083b6bd804565ead7','TEST_已停用文档','PRODUCT_DOC','FILE_f3663253','DISABLED','tester','2026-04-25 15:22:18',NULL,NULL);
/*!40000 ALTER TABLE `doc_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `emp_index_result`
--

DROP TABLE IF EXISTS `emp_index_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `emp_index_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `data_date` date NOT NULL COMMENT '数据日期',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据版本',
  `emp_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `val_1` decimal(20,4) DEFAULT NULL COMMENT 'val_1',
  `val_2` decimal(20,4) DEFAULT NULL COMMENT 'val_2',
  `val_3` decimal(20,4) DEFAULT NULL COMMENT 'val_3',
  `val_4` decimal(20,4) DEFAULT NULL COMMENT 'val_4',
  `val_5` decimal(20,4) DEFAULT NULL COMMENT 'val_5',
  `val_6` decimal(20,4) DEFAULT NULL COMMENT 'val_6',
  `val_7` decimal(20,4) DEFAULT NULL COMMENT 'val_7',
  `val_8` decimal(20,4) DEFAULT NULL COMMENT 'val_8',
  `val_9` decimal(20,4) DEFAULT NULL COMMENT 'val_9',
  `val_10` decimal(20,4) DEFAULT NULL COMMENT 'val_10',
  `val_11` decimal(20,4) DEFAULT NULL COMMENT 'val_11',
  `val_12` decimal(20,4) DEFAULT NULL COMMENT 'val_12',
  `val_13` decimal(20,4) DEFAULT NULL COMMENT 'val_13',
  `val_14` decimal(20,4) DEFAULT NULL COMMENT 'val_14',
  `val_15` decimal(20,4) DEFAULT NULL COMMENT 'val_15',
  `val_16` decimal(20,4) DEFAULT NULL COMMENT 'val_16',
  `val_17` decimal(20,4) DEFAULT NULL COMMENT 'val_17',
  `val_18` decimal(20,4) DEFAULT NULL COMMENT 'val_18',
  `val_19` decimal(20,4) DEFAULT NULL COMMENT 'val_19',
  `val_20` decimal(20,4) DEFAULT NULL COMMENT 'val_20',
  `val_21` decimal(20,4) DEFAULT NULL COMMENT 'val_21',
  `val_22` decimal(20,4) DEFAULT NULL COMMENT 'val_22',
  `val_23` decimal(20,4) DEFAULT NULL COMMENT 'val_23',
  `val_24` decimal(20,4) DEFAULT NULL COMMENT 'val_24',
  `val_25` decimal(20,4) DEFAULT NULL COMMENT 'val_25',
  `val_26` decimal(20,4) DEFAULT NULL COMMENT 'val_26',
  `val_27` decimal(20,4) DEFAULT NULL COMMENT 'val_27',
  `val_28` decimal(20,4) DEFAULT NULL COMMENT 'val_28',
  `val_29` decimal(20,4) DEFAULT NULL COMMENT 'val_29',
  `val_30` decimal(20,4) DEFAULT NULL COMMENT 'val_30',
  `val_31` decimal(20,4) DEFAULT NULL COMMENT 'val_31',
  `val_32` decimal(20,4) DEFAULT NULL COMMENT 'val_32',
  `val_33` decimal(20,4) DEFAULT NULL COMMENT 'val_33',
  `val_34` decimal(20,4) DEFAULT NULL COMMENT 'val_34',
  `val_35` decimal(20,4) DEFAULT NULL COMMENT 'val_35',
  `val_36` decimal(20,4) DEFAULT NULL COMMENT 'val_36',
  `val_37` decimal(20,4) DEFAULT NULL COMMENT 'val_37',
  `val_38` decimal(20,4) DEFAULT NULL COMMENT 'val_38',
  `val_39` decimal(20,4) DEFAULT NULL COMMENT 'val_39',
  `val_40` decimal(20,4) DEFAULT NULL COMMENT 'val_40',
  `val_41` decimal(20,4) DEFAULT NULL COMMENT 'val_41',
  `val_42` decimal(20,4) DEFAULT NULL COMMENT 'val_42',
  `val_43` decimal(20,4) DEFAULT NULL COMMENT 'val_43',
  `val_44` decimal(20,4) DEFAULT NULL COMMENT 'val_44',
  `val_45` decimal(20,4) DEFAULT NULL COMMENT 'val_45',
  `val_46` decimal(20,4) DEFAULT NULL COMMENT 'val_46',
  `val_47` decimal(20,4) DEFAULT NULL COMMENT 'val_47',
  `val_48` decimal(20,4) DEFAULT NULL COMMENT 'val_48',
  `val_49` decimal(20,4) DEFAULT NULL COMMENT 'val_49',
  `val_50` decimal(20,4) DEFAULT NULL COMMENT 'val_50',
  `val_51` decimal(20,4) DEFAULT NULL COMMENT 'val_51',
  `val_52` decimal(20,4) DEFAULT NULL COMMENT 'val_52',
  `val_53` decimal(20,4) DEFAULT NULL COMMENT 'val_53',
  `val_54` decimal(20,4) DEFAULT NULL COMMENT 'val_54',
  `val_55` decimal(20,4) DEFAULT NULL COMMENT 'val_55',
  `val_56` decimal(20,4) DEFAULT NULL COMMENT 'val_56',
  `val_57` decimal(20,4) DEFAULT NULL COMMENT 'val_57',
  `val_58` decimal(20,4) DEFAULT NULL COMMENT 'val_58',
  `val_59` decimal(20,4) DEFAULT NULL COMMENT 'val_59',
  `val_60` decimal(20,4) DEFAULT NULL COMMENT 'val_60',
  `val_61` decimal(20,4) DEFAULT NULL COMMENT 'val_61',
  `val_62` decimal(20,4) DEFAULT NULL COMMENT 'val_62',
  `val_63` decimal(20,4) DEFAULT NULL COMMENT 'val_63',
  `val_64` decimal(20,4) DEFAULT NULL COMMENT 'val_64',
  `val_65` decimal(20,4) DEFAULT NULL COMMENT 'val_65',
  `val_66` decimal(20,4) DEFAULT NULL COMMENT 'val_66',
  `val_67` decimal(20,4) DEFAULT NULL COMMENT 'val_67',
  `val_68` decimal(20,4) DEFAULT NULL COMMENT 'val_68',
  `val_69` decimal(20,4) DEFAULT NULL COMMENT 'val_69',
  `val_70` decimal(20,4) DEFAULT NULL COMMENT 'val_70',
  `val_71` decimal(20,4) DEFAULT NULL COMMENT 'val_71',
  `val_72` decimal(20,4) DEFAULT NULL COMMENT 'val_72',
  `val_73` decimal(20,4) DEFAULT NULL COMMENT 'val_73',
  `val_74` decimal(20,4) DEFAULT NULL COMMENT 'val_74',
  `val_75` decimal(20,4) DEFAULT NULL COMMENT 'val_75',
  `val_76` decimal(20,4) DEFAULT NULL COMMENT 'val_76',
  `val_77` decimal(20,4) DEFAULT NULL COMMENT 'val_77',
  `val_78` decimal(20,4) DEFAULT NULL COMMENT 'val_78',
  `val_79` decimal(20,4) DEFAULT NULL COMMENT 'val_79',
  `val_80` decimal(20,4) DEFAULT NULL COMMENT 'val_80',
  `val_81` decimal(20,4) DEFAULT NULL COMMENT 'val_81',
  `val_82` decimal(20,4) DEFAULT NULL COMMENT 'val_82',
  `val_83` decimal(20,4) DEFAULT NULL COMMENT 'val_83',
  `val_84` decimal(20,4) DEFAULT NULL COMMENT 'val_84',
  `val_85` decimal(20,4) DEFAULT NULL COMMENT 'val_85',
  `val_86` decimal(20,4) DEFAULT NULL COMMENT 'val_86',
  `val_87` decimal(20,4) DEFAULT NULL COMMENT 'val_87',
  `val_88` decimal(20,4) DEFAULT NULL COMMENT 'val_88',
  `val_89` decimal(20,4) DEFAULT NULL COMMENT 'val_89',
  `val_90` decimal(20,4) DEFAULT NULL COMMENT 'val_90',
  `val_91` decimal(20,4) DEFAULT NULL COMMENT 'val_91',
  `val_92` decimal(20,4) DEFAULT NULL COMMENT 'val_92',
  `val_93` decimal(20,4) DEFAULT NULL COMMENT 'val_93',
  `val_94` decimal(20,4) DEFAULT NULL COMMENT 'val_94',
  `val_95` decimal(20,4) DEFAULT NULL COMMENT 'val_95',
  `val_96` decimal(20,4) DEFAULT NULL COMMENT 'val_96',
  `val_97` decimal(20,4) DEFAULT NULL COMMENT 'val_97',
  `val_98` decimal(20,4) DEFAULT NULL COMMENT 'val_98',
  `val_99` decimal(20,4) DEFAULT NULL COMMENT 'val_99',
  `val_100` decimal(20,4) DEFAULT NULL COMMENT 'val_100',
  `val_101` decimal(20,4) DEFAULT NULL COMMENT 'val_101',
  `val_102` decimal(20,4) DEFAULT NULL COMMENT 'val_102',
  `val_103` decimal(20,4) DEFAULT NULL COMMENT 'val_103',
  `val_104` decimal(20,4) DEFAULT NULL COMMENT 'val_104',
  `val_105` decimal(20,4) DEFAULT NULL COMMENT 'val_105',
  `val_106` decimal(20,4) DEFAULT NULL COMMENT 'val_106',
  `val_107` decimal(20,4) DEFAULT NULL COMMENT 'val_107',
  `val_108` decimal(20,4) DEFAULT NULL COMMENT 'val_108',
  `val_109` decimal(20,4) DEFAULT NULL COMMENT 'val_109',
  `val_110` decimal(20,4) DEFAULT NULL COMMENT 'val_110',
  `val_111` decimal(20,4) DEFAULT NULL COMMENT 'val_111',
  `val_112` decimal(20,4) DEFAULT NULL COMMENT 'val_112',
  `val_113` decimal(20,4) DEFAULT NULL COMMENT 'val_113',
  `val_114` decimal(20,4) DEFAULT NULL COMMENT 'val_114',
  `val_115` decimal(20,4) DEFAULT NULL COMMENT 'val_115',
  `val_116` decimal(20,4) DEFAULT NULL COMMENT 'val_116',
  `val_117` decimal(20,4) DEFAULT NULL COMMENT 'val_117',
  `val_118` decimal(20,4) DEFAULT NULL COMMENT 'val_118',
  `val_119` decimal(20,4) DEFAULT NULL COMMENT 'val_119',
  `val_120` decimal(20,4) DEFAULT NULL COMMENT 'val_120',
  `val_121` decimal(20,4) DEFAULT NULL COMMENT 'val_121',
  `val_122` decimal(20,4) DEFAULT NULL COMMENT 'val_122',
  `val_123` decimal(20,4) DEFAULT NULL COMMENT 'val_123',
  `val_124` decimal(20,4) DEFAULT NULL COMMENT 'val_124',
  `val_125` decimal(20,4) DEFAULT NULL COMMENT 'val_125',
  `val_126` decimal(20,4) DEFAULT NULL COMMENT 'val_126',
  `val_127` decimal(20,4) DEFAULT NULL COMMENT 'val_127',
  `val_128` decimal(20,4) DEFAULT NULL COMMENT 'val_128',
  `val_129` decimal(20,4) DEFAULT NULL COMMENT 'val_129',
  `val_130` decimal(20,4) DEFAULT NULL COMMENT 'val_130',
  `val_131` decimal(20,4) DEFAULT NULL COMMENT 'val_131',
  `val_132` decimal(20,4) DEFAULT NULL COMMENT 'val_132',
  `val_133` decimal(20,4) DEFAULT NULL COMMENT 'val_133',
  `val_134` decimal(20,4) DEFAULT NULL COMMENT 'val_134',
  `val_135` decimal(20,4) DEFAULT NULL COMMENT 'val_135',
  `val_136` decimal(20,4) DEFAULT NULL COMMENT 'val_136',
  `val_137` decimal(20,4) DEFAULT NULL COMMENT 'val_137',
  `val_138` decimal(20,4) DEFAULT NULL COMMENT 'val_138',
  `val_139` decimal(20,4) DEFAULT NULL COMMENT 'val_139',
  `val_140` decimal(20,4) DEFAULT NULL COMMENT 'val_140',
  `val_141` decimal(20,4) DEFAULT NULL COMMENT 'val_141',
  `val_142` decimal(20,4) DEFAULT NULL COMMENT 'val_142',
  `val_143` decimal(20,4) DEFAULT NULL COMMENT 'val_143',
  `val_144` decimal(20,4) DEFAULT NULL COMMENT 'val_144',
  `val_145` decimal(20,4) DEFAULT NULL COMMENT 'val_145',
  `val_146` decimal(20,4) DEFAULT NULL COMMENT 'val_146',
  `val_147` decimal(20,4) DEFAULT NULL COMMENT 'val_147',
  `val_148` decimal(20,4) DEFAULT NULL COMMENT 'val_148',
  `val_149` decimal(20,4) DEFAULT NULL COMMENT 'val_149',
  `val_150` decimal(20,4) DEFAULT NULL COMMENT 'val_150',
  `val_151` decimal(20,4) DEFAULT NULL COMMENT 'val_151',
  `val_152` decimal(20,4) DEFAULT NULL COMMENT 'val_152',
  `val_153` decimal(20,4) DEFAULT NULL COMMENT 'val_153',
  `val_154` decimal(20,4) DEFAULT NULL COMMENT 'val_154',
  `val_155` decimal(20,4) DEFAULT NULL COMMENT 'val_155',
  `val_156` decimal(20,4) DEFAULT NULL COMMENT 'val_156',
  `val_157` decimal(20,4) DEFAULT NULL COMMENT 'val_157',
  `val_158` decimal(20,4) DEFAULT NULL COMMENT 'val_158',
  `val_159` decimal(20,4) DEFAULT NULL COMMENT 'val_159',
  `val_160` decimal(20,4) DEFAULT NULL COMMENT 'val_160',
  `val_161` decimal(20,4) DEFAULT NULL COMMENT 'val_161',
  `val_162` decimal(20,4) DEFAULT NULL COMMENT 'val_162',
  `val_163` decimal(20,4) DEFAULT NULL COMMENT 'val_163',
  `val_164` decimal(20,4) DEFAULT NULL COMMENT 'val_164',
  `val_165` decimal(20,4) DEFAULT NULL COMMENT 'val_165',
  `val_166` decimal(20,4) DEFAULT NULL COMMENT 'val_166',
  `val_167` decimal(20,4) DEFAULT NULL COMMENT 'val_167',
  `val_168` decimal(20,4) DEFAULT NULL COMMENT 'val_168',
  `val_169` decimal(20,4) DEFAULT NULL COMMENT 'val_169',
  `val_170` decimal(20,4) DEFAULT NULL COMMENT 'val_170',
  `val_171` decimal(20,4) DEFAULT NULL COMMENT 'val_171',
  `val_172` decimal(20,4) DEFAULT NULL COMMENT 'val_172',
  `val_173` decimal(20,4) DEFAULT NULL COMMENT 'val_173',
  `val_174` decimal(20,4) DEFAULT NULL COMMENT 'val_174',
  `val_175` decimal(20,4) DEFAULT NULL COMMENT 'val_175',
  `val_176` decimal(20,4) DEFAULT NULL COMMENT 'val_176',
  `val_177` decimal(20,4) DEFAULT NULL COMMENT 'val_177',
  `val_178` decimal(20,4) DEFAULT NULL COMMENT 'val_178',
  `val_179` decimal(20,4) DEFAULT NULL COMMENT 'val_179',
  `val_180` decimal(20,4) DEFAULT NULL COMMENT 'val_180',
  `val_181` decimal(20,4) DEFAULT NULL COMMENT 'val_181',
  `val_182` decimal(20,4) DEFAULT NULL COMMENT 'val_182',
  `val_183` decimal(20,4) DEFAULT NULL COMMENT 'val_183',
  `val_184` decimal(20,4) DEFAULT NULL COMMENT 'val_184',
  `val_185` decimal(20,4) DEFAULT NULL COMMENT 'val_185',
  `val_186` decimal(20,4) DEFAULT NULL COMMENT 'val_186',
  `val_187` decimal(20,4) DEFAULT NULL COMMENT 'val_187',
  `val_188` decimal(20,4) DEFAULT NULL COMMENT 'val_188',
  `val_189` decimal(20,4) DEFAULT NULL COMMENT 'val_189',
  `val_190` decimal(20,4) DEFAULT NULL COMMENT 'val_190',
  `val_191` decimal(20,4) DEFAULT NULL COMMENT 'val_191',
  `val_192` decimal(20,4) DEFAULT NULL COMMENT 'val_192',
  `val_193` decimal(20,4) DEFAULT NULL COMMENT 'val_193',
  `val_194` decimal(20,4) DEFAULT NULL COMMENT 'val_194',
  `val_195` decimal(20,4) DEFAULT NULL COMMENT 'val_195',
  `val_196` decimal(20,4) DEFAULT NULL COMMENT 'val_196',
  `val_197` decimal(20,4) DEFAULT NULL COMMENT 'val_197',
  `val_198` decimal(20,4) DEFAULT NULL COMMENT 'val_198',
  `val_199` decimal(20,4) DEFAULT NULL COMMENT 'val_199',
  `val_200` decimal(20,4) DEFAULT NULL COMMENT 'val_200',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_date_ver` (`emp_id`,`data_date`,`version`),
  KEY `idx_date_ver` (`data_date`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=568 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工指标结果宽表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `emp_index_result`
--

LOCK TABLES `emp_index_result` WRITE;
/*!40000 ALTER TABLE `emp_index_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `emp_index_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ext_org_info`
--

DROP TABLE IF EXISTS `ext_org_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ext_org_info` (
  `ID` int NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  `ORG_CODE` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '机构编号',
  `ORG_NAME` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '机构名称',
  `ORG_LEVEL` int DEFAULT NULL COMMENT '机构等级 1 总行 2 分行 3 支行',
  `P_ID` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上级机构编码',
  `ORGAN_STATE` int DEFAULT '0' COMMENT '状态 0 启用 1 删除',
  `ADM_DIVISION_CODE` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行政区划代码',
  `ADM_DIVISION_NAME` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行政区划名称',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_ext_org_info_org_code` (`ORG_CODE`),
  KEY `idx_p_id` (`P_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机构表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ext_org_info`
--

LOCK TABLES `ext_org_info` WRITE;
/*!40000 ALTER TABLE `ext_org_info` DISABLE KEYS */;
INSERT INTO `ext_org_info` VALUES (1,'HQ','总行',1,NULL,0,NULL,NULL,'2026-04-07 15:49:38',NULL),(2,'BJ','北京分行',2,'HQ',0,NULL,NULL,'2026-04-07 15:49:38',NULL),(3,'SH','上海分行',2,'HQ',0,NULL,NULL,'2026-04-07 15:49:38',NULL),(4,'BJ_CY','北京分行朝阳支行',3,'BJ',0,NULL,NULL,'2026-04-07 15:49:38',NULL),(5,'SH_PD','上海分行浦东支行',3,'SH',0,NULL,NULL,'2026-04-07 15:49:38',NULL);
/*!40000 ALTER TABLE `ext_org_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ext_user_org`
--

DROP TABLE IF EXISTS `ext_user_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ext_user_org` (
  `USER_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `ORG_CODE` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '机构编码',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ORG_CODE`),
  KEY `idx_org_code` (`ORG_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户机构关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ext_user_org`
--

LOCK TABLES `ext_user_org` WRITE;
/*!40000 ALTER TABLE `ext_user_org` DISABLE KEYS */;
INSERT INTO `ext_user_org` VALUES ('admin','HQ','2026-04-07 15:49:38'),('E10002','SH_PD','2026-04-10 11:17:49'),('E20001','BJ_CY','2026-04-10 11:17:49'),('E30001','HQ','2026-04-10 11:17:49'),('E30002','HQ','2026-04-10 11:17:49'),('E40001','HQ','2026-04-10 11:17:49'),('E40002','HQ','2026-04-10 11:17:49'),('E50001','HQ','2026-04-10 11:17:49'),('E50002','HQ','2026-04-10 11:17:49'),('E60001','HQ','2026-04-10 11:17:49'),('E60002','HQ','2026-04-10 11:17:49'),('user001','BJ_CY','2026-04-07 15:49:38'),('user002','SH_PD','2026-04-07 15:49:38');
/*!40000 ALTER TABLE `ext_user_org` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_object`
--

DROP TABLE IF EXISTS `file_object`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_object` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件对象ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件类型',
  `storage_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储路径（对象存储）',
  `bucket_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '存储桶名称',
  `md5_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MD5哈希值',
  `uploaded_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上传人',
  `uploaded_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_uploaded_by` (`uploaded_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件对象表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_object`
--

LOCK TABLES `file_object` WRITE;
/*!40000 ALTER TABLE `file_object` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_object` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_channel_definition`
--

DROP TABLE IF EXISTS `flw_channel_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_channel_definition` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `VERSION_` int DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IMPLEMENTATION_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_CHANNEL_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_channel_definition`
--

LOCK TABLES `flw_channel_definition` WRITE;
/*!40000 ALTER TABLE `flw_channel_definition` DISABLE KEYS */;
/*!40000 ALTER TABLE `flw_channel_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_ev_databasechangelog`
--

DROP TABLE IF EXISTS `flw_ev_databasechangelog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_ev_databasechangelog` (
  `ID` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `AUTHOR` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `FILENAME` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int NOT NULL,
  `EXECTYPE` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `MD5SUM` varchar(35) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `COMMENTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TAG` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LIQUIBASE` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CONTEXTS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LABELS` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_ev_databasechangelog`
--

LOCK TABLES `flw_ev_databasechangelog` WRITE;
/*!40000 ALTER TABLE `flw_ev_databasechangelog` DISABLE KEYS */;
INSERT INTO `flw_ev_databasechangelog` VALUES ('1','flowable','org/flowable/eventregistry/db/liquibase/flowable-eventregistry-db-changelog.xml','2026-04-13 14:54:22',1,'EXECUTED','9:63268f536c469325acef35970312551b','createTable tableName=FLW_EVENT_DEPLOYMENT; createTable tableName=FLW_EVENT_RESOURCE; createTable tableName=FLW_EVENT_DEFINITION; createIndex indexName=ACT_IDX_EVENT_DEF_UNIQ, tableName=FLW_EVENT_DEFINITION; createTable tableName=FLW_CHANNEL_DEFIN...','',NULL,'4.24.0',NULL,NULL,'6063262255'),('2','flowable','org/flowable/eventregistry/db/liquibase/flowable-eventregistry-db-changelog.xml','2026-04-13 14:54:22',2,'EXECUTED','9:dcb58b7dfd6dbda66939123a96985536','addColumn tableName=FLW_CHANNEL_DEFINITION; addColumn tableName=FLW_CHANNEL_DEFINITION','',NULL,'4.24.0',NULL,NULL,'6063262255'),('3','flowable','org/flowable/eventregistry/db/liquibase/flowable-eventregistry-db-changelog.xml','2026-04-13 14:54:22',3,'EXECUTED','9:d0c05678d57af23ad93699991e3bf4f6','customChange','',NULL,'4.24.0',NULL,NULL,'6063262255');
/*!40000 ALTER TABLE `flw_ev_databasechangelog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_ev_databasechangeloglock`
--

DROP TABLE IF EXISTS `flw_ev_databasechangeloglock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_ev_databasechangeloglock` (
  `ID` int NOT NULL,
  `LOCKED` tinyint(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_ev_databasechangeloglock`
--

LOCK TABLES `flw_ev_databasechangeloglock` WRITE;
/*!40000 ALTER TABLE `flw_ev_databasechangeloglock` DISABLE KEYS */;
INSERT INTO `flw_ev_databasechangeloglock` VALUES (1,0,NULL,NULL);
/*!40000 ALTER TABLE `flw_ev_databasechangeloglock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_event_definition`
--

DROP TABLE IF EXISTS `flw_event_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_event_definition` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `VERSION_` int DEFAULT NULL,
  `KEY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_EVENT_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_event_definition`
--

LOCK TABLES `flw_event_definition` WRITE;
/*!40000 ALTER TABLE `flw_event_definition` DISABLE KEYS */;
/*!40000 ALTER TABLE `flw_event_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_event_deployment`
--

DROP TABLE IF EXISTS `flw_event_deployment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_event_deployment` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL,
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_event_deployment`
--

LOCK TABLES `flw_event_deployment` WRITE;
/*!40000 ALTER TABLE `flw_event_deployment` DISABLE KEYS */;
/*!40000 ALTER TABLE `flw_event_deployment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_event_resource`
--

DROP TABLE IF EXISTS `flw_event_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_event_resource` (
  `ID_` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_BYTES_` longblob,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_event_resource`
--

LOCK TABLES `flw_event_resource` WRITE;
/*!40000 ALTER TABLE `flw_event_resource` DISABLE KEYS */;
/*!40000 ALTER TABLE `flw_event_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_ru_batch`
--

DROP TABLE IF EXISTS `flw_ru_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_ru_batch` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `TYPE_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SEARCH_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SEARCH_KEY2_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` datetime(3) NOT NULL,
  `COMPLETE_TIME_` datetime(3) DEFAULT NULL,
  `STATUS_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `BATCH_DOC_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_ru_batch`
--

LOCK TABLES `flw_ru_batch` WRITE;
/*!40000 ALTER TABLE `flw_ru_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `flw_ru_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flw_ru_batch_part`
--

DROP TABLE IF EXISTS `flw_ru_batch_part`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flw_ru_batch_part` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `REV_` int DEFAULT NULL,
  `BATCH_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TYPE_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SCOPE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCOPE_TYPE_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SEARCH_KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SEARCH_KEY2_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIME_` datetime(3) NOT NULL,
  `COMPLETE_TIME_` datetime(3) DEFAULT NULL,
  `STATUS_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `RESULT_DOC_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `FLW_IDX_BATCH_PART` (`BATCH_ID_`),
  CONSTRAINT `FLW_FK_BATCH_PART_PARENT` FOREIGN KEY (`BATCH_ID_`) REFERENCES `flw_ru_batch` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flw_ru_batch_part`
--

LOCK TABLES `flw_ru_batch_part` WRITE;
/*!40000 ALTER TABLE `flw_ru_batch_part` DISABLE KEYS */;
/*!40000 ALTER TABLE `flw_ru_batch_part` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `kpi_result`
--

DROP TABLE IF EXISTS `kpi_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kpi_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `cycle_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期类型：MONTHLY/QUARTERLY',
  `cycle_date` date NOT NULL COMMENT '周期日期(如月末)',
  `as_of_date` date NOT NULL COMMENT '计算基准日(每日一算区分键)',
  `data_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标数据版本',
  `kpi_total_score` decimal(10,4) NOT NULL COMMENT 'KPI总分',
  `detail_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '明细(JSON，可选)',
  `calculated_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_cycle_asof` (`emp_id`,`cycle_type`,`cycle_date`,`as_of_date`),
  KEY `idx_as_of_date` (`as_of_date`)
) ENGINE=InnoDB AUTO_INCREMENT=301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='KPI结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `kpi_result`
--

LOCK TABLES `kpi_result` WRITE;
/*!40000 ALTER TABLE `kpi_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `kpi_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lead_import_batch`
--

DROP TABLE IF EXISTS `lead_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_import_batch` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次ID',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次号(展示用)',
  `source_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源文件名',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件MD5',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/PENDING_APPROVAL/APPROVED/REJECTED',
  `total_row_count` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `error_row_count` int NOT NULL DEFAULT '0' COMMENT '错误行数',
  `error_summary` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误摘要',
  `error_file_object_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误明细文件ID(可选)',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键(LEAD:IMP_{id})',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索导入批次表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lead_import_batch`
--

LOCK TABLES `lead_import_batch` WRITE;
/*!40000 ALTER TABLE `lead_import_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `lead_import_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `loan_apply`
--

DROP TABLE IF EXISTS `loan_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `loan_apply` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `apply_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请编号',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `source_touch_task_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源触达任务ID',
  `project_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '项目类型(字典)',
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务类型(字典)',
  `guarantee_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '担保方式(字典)',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '敞口金额',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键(LOAN:{id})',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除标记(0-否,1-是)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资产投放申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `loan_apply`
--

LOCK TABLES `loan_apply` WRITE;
/*!40000 ALTER TABLE `loan_apply` DISABLE KEYS */;
/*!40000 ALTER TABLE `loan_apply` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `org_index_result`
--

DROP TABLE IF EXISTS `org_index_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_index_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `data_date` date NOT NULL COMMENT '数据日期',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据版本',
  `org_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '机构编码',
  `val_1` decimal(20,4) DEFAULT NULL COMMENT 'val_1',
  `val_2` decimal(20,4) DEFAULT NULL COMMENT 'val_2',
  `val_3` decimal(20,4) DEFAULT NULL COMMENT 'val_3',
  `val_4` decimal(20,4) DEFAULT NULL COMMENT 'val_4',
  `val_5` decimal(20,4) DEFAULT NULL COMMENT 'val_5',
  `val_6` decimal(20,4) DEFAULT NULL COMMENT 'val_6',
  `val_7` decimal(20,4) DEFAULT NULL COMMENT 'val_7',
  `val_8` decimal(20,4) DEFAULT NULL COMMENT 'val_8',
  `val_9` decimal(20,4) DEFAULT NULL COMMENT 'val_9',
  `val_10` decimal(20,4) DEFAULT NULL COMMENT 'val_10',
  `val_11` decimal(20,4) DEFAULT NULL COMMENT 'val_11',
  `val_12` decimal(20,4) DEFAULT NULL COMMENT 'val_12',
  `val_13` decimal(20,4) DEFAULT NULL COMMENT 'val_13',
  `val_14` decimal(20,4) DEFAULT NULL COMMENT 'val_14',
  `val_15` decimal(20,4) DEFAULT NULL COMMENT 'val_15',
  `val_16` decimal(20,4) DEFAULT NULL COMMENT 'val_16',
  `val_17` decimal(20,4) DEFAULT NULL COMMENT 'val_17',
  `val_18` decimal(20,4) DEFAULT NULL COMMENT 'val_18',
  `val_19` decimal(20,4) DEFAULT NULL COMMENT 'val_19',
  `val_20` decimal(20,4) DEFAULT NULL COMMENT 'val_20',
  `val_21` decimal(20,4) DEFAULT NULL COMMENT 'val_21',
  `val_22` decimal(20,4) DEFAULT NULL COMMENT 'val_22',
  `val_23` decimal(20,4) DEFAULT NULL COMMENT 'val_23',
  `val_24` decimal(20,4) DEFAULT NULL COMMENT 'val_24',
  `val_25` decimal(20,4) DEFAULT NULL COMMENT 'val_25',
  `val_26` decimal(20,4) DEFAULT NULL COMMENT 'val_26',
  `val_27` decimal(20,4) DEFAULT NULL COMMENT 'val_27',
  `val_28` decimal(20,4) DEFAULT NULL COMMENT 'val_28',
  `val_29` decimal(20,4) DEFAULT NULL COMMENT 'val_29',
  `val_30` decimal(20,4) DEFAULT NULL COMMENT 'val_30',
  `val_31` decimal(20,4) DEFAULT NULL COMMENT 'val_31',
  `val_32` decimal(20,4) DEFAULT NULL COMMENT 'val_32',
  `val_33` decimal(20,4) DEFAULT NULL COMMENT 'val_33',
  `val_34` decimal(20,4) DEFAULT NULL COMMENT 'val_34',
  `val_35` decimal(20,4) DEFAULT NULL COMMENT 'val_35',
  `val_36` decimal(20,4) DEFAULT NULL COMMENT 'val_36',
  `val_37` decimal(20,4) DEFAULT NULL COMMENT 'val_37',
  `val_38` decimal(20,4) DEFAULT NULL COMMENT 'val_38',
  `val_39` decimal(20,4) DEFAULT NULL COMMENT 'val_39',
  `val_40` decimal(20,4) DEFAULT NULL COMMENT 'val_40',
  `val_41` decimal(20,4) DEFAULT NULL COMMENT 'val_41',
  `val_42` decimal(20,4) DEFAULT NULL COMMENT 'val_42',
  `val_43` decimal(20,4) DEFAULT NULL COMMENT 'val_43',
  `val_44` decimal(20,4) DEFAULT NULL COMMENT 'val_44',
  `val_45` decimal(20,4) DEFAULT NULL COMMENT 'val_45',
  `val_46` decimal(20,4) DEFAULT NULL COMMENT 'val_46',
  `val_47` decimal(20,4) DEFAULT NULL COMMENT 'val_47',
  `val_48` decimal(20,4) DEFAULT NULL COMMENT 'val_48',
  `val_49` decimal(20,4) DEFAULT NULL COMMENT 'val_49',
  `val_50` decimal(20,4) DEFAULT NULL COMMENT 'val_50',
  `val_51` decimal(20,4) DEFAULT NULL COMMENT 'val_51',
  `val_52` decimal(20,4) DEFAULT NULL COMMENT 'val_52',
  `val_53` decimal(20,4) DEFAULT NULL COMMENT 'val_53',
  `val_54` decimal(20,4) DEFAULT NULL COMMENT 'val_54',
  `val_55` decimal(20,4) DEFAULT NULL COMMENT 'val_55',
  `val_56` decimal(20,4) DEFAULT NULL COMMENT 'val_56',
  `val_57` decimal(20,4) DEFAULT NULL COMMENT 'val_57',
  `val_58` decimal(20,4) DEFAULT NULL COMMENT 'val_58',
  `val_59` decimal(20,4) DEFAULT NULL COMMENT 'val_59',
  `val_60` decimal(20,4) DEFAULT NULL COMMENT 'val_60',
  `val_61` decimal(20,4) DEFAULT NULL COMMENT 'val_61',
  `val_62` decimal(20,4) DEFAULT NULL COMMENT 'val_62',
  `val_63` decimal(20,4) DEFAULT NULL COMMENT 'val_63',
  `val_64` decimal(20,4) DEFAULT NULL COMMENT 'val_64',
  `val_65` decimal(20,4) DEFAULT NULL COMMENT 'val_65',
  `val_66` decimal(20,4) DEFAULT NULL COMMENT 'val_66',
  `val_67` decimal(20,4) DEFAULT NULL COMMENT 'val_67',
  `val_68` decimal(20,4) DEFAULT NULL COMMENT 'val_68',
  `val_69` decimal(20,4) DEFAULT NULL COMMENT 'val_69',
  `val_70` decimal(20,4) DEFAULT NULL COMMENT 'val_70',
  `val_71` decimal(20,4) DEFAULT NULL COMMENT 'val_71',
  `val_72` decimal(20,4) DEFAULT NULL COMMENT 'val_72',
  `val_73` decimal(20,4) DEFAULT NULL COMMENT 'val_73',
  `val_74` decimal(20,4) DEFAULT NULL COMMENT 'val_74',
  `val_75` decimal(20,4) DEFAULT NULL COMMENT 'val_75',
  `val_76` decimal(20,4) DEFAULT NULL COMMENT 'val_76',
  `val_77` decimal(20,4) DEFAULT NULL COMMENT 'val_77',
  `val_78` decimal(20,4) DEFAULT NULL COMMENT 'val_78',
  `val_79` decimal(20,4) DEFAULT NULL COMMENT 'val_79',
  `val_80` decimal(20,4) DEFAULT NULL COMMENT 'val_80',
  `val_81` decimal(20,4) DEFAULT NULL COMMENT 'val_81',
  `val_82` decimal(20,4) DEFAULT NULL COMMENT 'val_82',
  `val_83` decimal(20,4) DEFAULT NULL COMMENT 'val_83',
  `val_84` decimal(20,4) DEFAULT NULL COMMENT 'val_84',
  `val_85` decimal(20,4) DEFAULT NULL COMMENT 'val_85',
  `val_86` decimal(20,4) DEFAULT NULL COMMENT 'val_86',
  `val_87` decimal(20,4) DEFAULT NULL COMMENT 'val_87',
  `val_88` decimal(20,4) DEFAULT NULL COMMENT 'val_88',
  `val_89` decimal(20,4) DEFAULT NULL COMMENT 'val_89',
  `val_90` decimal(20,4) DEFAULT NULL COMMENT 'val_90',
  `val_91` decimal(20,4) DEFAULT NULL COMMENT 'val_91',
  `val_92` decimal(20,4) DEFAULT NULL COMMENT 'val_92',
  `val_93` decimal(20,4) DEFAULT NULL COMMENT 'val_93',
  `val_94` decimal(20,4) DEFAULT NULL COMMENT 'val_94',
  `val_95` decimal(20,4) DEFAULT NULL COMMENT 'val_95',
  `val_96` decimal(20,4) DEFAULT NULL COMMENT 'val_96',
  `val_97` decimal(20,4) DEFAULT NULL COMMENT 'val_97',
  `val_98` decimal(20,4) DEFAULT NULL COMMENT 'val_98',
  `val_99` decimal(20,4) DEFAULT NULL COMMENT 'val_99',
  `val_100` decimal(20,4) DEFAULT NULL COMMENT 'val_100',
  `val_101` decimal(20,4) DEFAULT NULL COMMENT 'val_101',
  `val_102` decimal(20,4) DEFAULT NULL COMMENT 'val_102',
  `val_103` decimal(20,4) DEFAULT NULL COMMENT 'val_103',
  `val_104` decimal(20,4) DEFAULT NULL COMMENT 'val_104',
  `val_105` decimal(20,4) DEFAULT NULL COMMENT 'val_105',
  `val_106` decimal(20,4) DEFAULT NULL COMMENT 'val_106',
  `val_107` decimal(20,4) DEFAULT NULL COMMENT 'val_107',
  `val_108` decimal(20,4) DEFAULT NULL COMMENT 'val_108',
  `val_109` decimal(20,4) DEFAULT NULL COMMENT 'val_109',
  `val_110` decimal(20,4) DEFAULT NULL COMMENT 'val_110',
  `val_111` decimal(20,4) DEFAULT NULL COMMENT 'val_111',
  `val_112` decimal(20,4) DEFAULT NULL COMMENT 'val_112',
  `val_113` decimal(20,4) DEFAULT NULL COMMENT 'val_113',
  `val_114` decimal(20,4) DEFAULT NULL COMMENT 'val_114',
  `val_115` decimal(20,4) DEFAULT NULL COMMENT 'val_115',
  `val_116` decimal(20,4) DEFAULT NULL COMMENT 'val_116',
  `val_117` decimal(20,4) DEFAULT NULL COMMENT 'val_117',
  `val_118` decimal(20,4) DEFAULT NULL COMMENT 'val_118',
  `val_119` decimal(20,4) DEFAULT NULL COMMENT 'val_119',
  `val_120` decimal(20,4) DEFAULT NULL COMMENT 'val_120',
  `val_121` decimal(20,4) DEFAULT NULL COMMENT 'val_121',
  `val_122` decimal(20,4) DEFAULT NULL COMMENT 'val_122',
  `val_123` decimal(20,4) DEFAULT NULL COMMENT 'val_123',
  `val_124` decimal(20,4) DEFAULT NULL COMMENT 'val_124',
  `val_125` decimal(20,4) DEFAULT NULL COMMENT 'val_125',
  `val_126` decimal(20,4) DEFAULT NULL COMMENT 'val_126',
  `val_127` decimal(20,4) DEFAULT NULL COMMENT 'val_127',
  `val_128` decimal(20,4) DEFAULT NULL COMMENT 'val_128',
  `val_129` decimal(20,4) DEFAULT NULL COMMENT 'val_129',
  `val_130` decimal(20,4) DEFAULT NULL COMMENT 'val_130',
  `val_131` decimal(20,4) DEFAULT NULL COMMENT 'val_131',
  `val_132` decimal(20,4) DEFAULT NULL COMMENT 'val_132',
  `val_133` decimal(20,4) DEFAULT NULL COMMENT 'val_133',
  `val_134` decimal(20,4) DEFAULT NULL COMMENT 'val_134',
  `val_135` decimal(20,4) DEFAULT NULL COMMENT 'val_135',
  `val_136` decimal(20,4) DEFAULT NULL COMMENT 'val_136',
  `val_137` decimal(20,4) DEFAULT NULL COMMENT 'val_137',
  `val_138` decimal(20,4) DEFAULT NULL COMMENT 'val_138',
  `val_139` decimal(20,4) DEFAULT NULL COMMENT 'val_139',
  `val_140` decimal(20,4) DEFAULT NULL COMMENT 'val_140',
  `val_141` decimal(20,4) DEFAULT NULL COMMENT 'val_141',
  `val_142` decimal(20,4) DEFAULT NULL COMMENT 'val_142',
  `val_143` decimal(20,4) DEFAULT NULL COMMENT 'val_143',
  `val_144` decimal(20,4) DEFAULT NULL COMMENT 'val_144',
  `val_145` decimal(20,4) DEFAULT NULL COMMENT 'val_145',
  `val_146` decimal(20,4) DEFAULT NULL COMMENT 'val_146',
  `val_147` decimal(20,4) DEFAULT NULL COMMENT 'val_147',
  `val_148` decimal(20,4) DEFAULT NULL COMMENT 'val_148',
  `val_149` decimal(20,4) DEFAULT NULL COMMENT 'val_149',
  `val_150` decimal(20,4) DEFAULT NULL COMMENT 'val_150',
  `val_151` decimal(20,4) DEFAULT NULL COMMENT 'val_151',
  `val_152` decimal(20,4) DEFAULT NULL COMMENT 'val_152',
  `val_153` decimal(20,4) DEFAULT NULL COMMENT 'val_153',
  `val_154` decimal(20,4) DEFAULT NULL COMMENT 'val_154',
  `val_155` decimal(20,4) DEFAULT NULL COMMENT 'val_155',
  `val_156` decimal(20,4) DEFAULT NULL COMMENT 'val_156',
  `val_157` decimal(20,4) DEFAULT NULL COMMENT 'val_157',
  `val_158` decimal(20,4) DEFAULT NULL COMMENT 'val_158',
  `val_159` decimal(20,4) DEFAULT NULL COMMENT 'val_159',
  `val_160` decimal(20,4) DEFAULT NULL COMMENT 'val_160',
  `val_161` decimal(20,4) DEFAULT NULL COMMENT 'val_161',
  `val_162` decimal(20,4) DEFAULT NULL COMMENT 'val_162',
  `val_163` decimal(20,4) DEFAULT NULL COMMENT 'val_163',
  `val_164` decimal(20,4) DEFAULT NULL COMMENT 'val_164',
  `val_165` decimal(20,4) DEFAULT NULL COMMENT 'val_165',
  `val_166` decimal(20,4) DEFAULT NULL COMMENT 'val_166',
  `val_167` decimal(20,4) DEFAULT NULL COMMENT 'val_167',
  `val_168` decimal(20,4) DEFAULT NULL COMMENT 'val_168',
  `val_169` decimal(20,4) DEFAULT NULL COMMENT 'val_169',
  `val_170` decimal(20,4) DEFAULT NULL COMMENT 'val_170',
  `val_171` decimal(20,4) DEFAULT NULL COMMENT 'val_171',
  `val_172` decimal(20,4) DEFAULT NULL COMMENT 'val_172',
  `val_173` decimal(20,4) DEFAULT NULL COMMENT 'val_173',
  `val_174` decimal(20,4) DEFAULT NULL COMMENT 'val_174',
  `val_175` decimal(20,4) DEFAULT NULL COMMENT 'val_175',
  `val_176` decimal(20,4) DEFAULT NULL COMMENT 'val_176',
  `val_177` decimal(20,4) DEFAULT NULL COMMENT 'val_177',
  `val_178` decimal(20,4) DEFAULT NULL COMMENT 'val_178',
  `val_179` decimal(20,4) DEFAULT NULL COMMENT 'val_179',
  `val_180` decimal(20,4) DEFAULT NULL COMMENT 'val_180',
  `val_181` decimal(20,4) DEFAULT NULL COMMENT 'val_181',
  `val_182` decimal(20,4) DEFAULT NULL COMMENT 'val_182',
  `val_183` decimal(20,4) DEFAULT NULL COMMENT 'val_183',
  `val_184` decimal(20,4) DEFAULT NULL COMMENT 'val_184',
  `val_185` decimal(20,4) DEFAULT NULL COMMENT 'val_185',
  `val_186` decimal(20,4) DEFAULT NULL COMMENT 'val_186',
  `val_187` decimal(20,4) DEFAULT NULL COMMENT 'val_187',
  `val_188` decimal(20,4) DEFAULT NULL COMMENT 'val_188',
  `val_189` decimal(20,4) DEFAULT NULL COMMENT 'val_189',
  `val_190` decimal(20,4) DEFAULT NULL COMMENT 'val_190',
  `val_191` decimal(20,4) DEFAULT NULL COMMENT 'val_191',
  `val_192` decimal(20,4) DEFAULT NULL COMMENT 'val_192',
  `val_193` decimal(20,4) DEFAULT NULL COMMENT 'val_193',
  `val_194` decimal(20,4) DEFAULT NULL COMMENT 'val_194',
  `val_195` decimal(20,4) DEFAULT NULL COMMENT 'val_195',
  `val_196` decimal(20,4) DEFAULT NULL COMMENT 'val_196',
  `val_197` decimal(20,4) DEFAULT NULL COMMENT 'val_197',
  `val_198` decimal(20,4) DEFAULT NULL COMMENT 'val_198',
  `val_199` decimal(20,4) DEFAULT NULL COMMENT 'val_199',
  `val_200` decimal(20,4) DEFAULT NULL COMMENT 'val_200',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_date_ver` (`org_code`,`data_date`,`version`),
  KEY `idx_date_ver` (`data_date`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=355 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机构指标结果宽表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `org_index_result`
--

LOCK TABLES `org_index_result` WRITE;
/*!40000 ALTER TABLE `org_index_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `org_index_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_alloc_adjust_apply`
--

DROP TABLE IF EXISTS `perf_alloc_adjust_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_alloc_adjust_apply` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `apply_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请编号',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `alloc_dim` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：RULE/ACCOUNT',
  `biz_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务种类',
  `account_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号(可选)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
INSERT INTO `perf_alloc_adjust_apply` VALUES ('TEST_Q26_01b6c3c440','AA_Q26_TEST_Q26_01b6c3c440','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_01b6c3c440','PI_Q26_TEST_Q26_01b6c3c440','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 16:45:30','q26_user','2026-04-24 16:45:30'),('TEST_Q26_03e5ab6c24','AA_Q26_TEST_Q26_03e5ab6c24','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_03e5ab6c24','PI_Q26_TEST_Q26_03e5ab6c24','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 09:33:58','q26_user','2026-04-24 09:33:58'),('TEST_Q26_07ce3fcc8a','AA_Q26_TEST_Q26_07ce3fcc8a','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_07ce3fcc8a','PI_Q26_TEST_Q26_07ce3fcc8a','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 18:39:19','q26_user','2026-04-24 18:39:18'),('TEST_Q26_0f9fe6b3c8','AA_Q26_TEST_Q26_0f9fe6b3c8','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_0f9fe6b3c8','PI_Q26_TEST_Q26_0f9fe6b3c8','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:45:21','q26_user','2026-04-24 13:45:20'),('TEST_Q26_13d5e30367','AA_Q26_TEST_Q26_13d5e30367','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_13d5e30367','PI_Q26_TEST_Q26_13d5e30367','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 16:36:20','q26_user','2026-04-24 16:36:20'),('TEST_Q26_16b3a3266f','AA_Q26_TEST_Q26_16b3a3266f','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_16b3a3266f','PI_Q26_TEST_Q26_16b3a3266f','ORG_Q26','Q2.6 IT','q26_user','2026-04-23 22:11:19','q26_user','2026-04-23 22:11:18'),('TEST_Q26_19339e5b22','AA_Q26_TEST_Q26_19339e5b22','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_19339e5b22','PI_Q26_TEST_Q26_19339e5b22','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:10:25','q26_user','2026-04-24 13:10:24'),('TEST_Q26_19383805a7','AA_Q26_TEST_Q26_19383805a7','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_19383805a7','PI_Q26_TEST_Q26_19383805a7','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 20:42:10','q26_user','2026-04-24 20:42:09'),('TEST_Q26_22ca5df1c8','AA_Q26_TEST_Q26_22ca5df1c8','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_22ca5df1c8','PI_Q26_TEST_Q26_22ca5df1c8','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:43:11','q26_user','2026-04-24 14:43:10'),('TEST_Q26_2ab0cd7c4f','AA_Q26_TEST_Q26_2ab0cd7c4f','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_2ab0cd7c4f','PI_Q26_TEST_Q26_2ab0cd7c4f','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 17:20:27','q26_user','2026-04-24 17:20:26'),('TEST_Q26_2c5f4bc0e9','AA_Q26_TEST_Q26_2c5f4bc0e9','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_2c5f4bc0e9','PI_Q26_TEST_Q26_2c5f4bc0e9','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:41:23','q26_user','2026-04-24 14:41:23'),('TEST_Q26_33e996da9d','AA_Q26_TEST_Q26_33e996da9d','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_33e996da9d','PI_Q26_TEST_Q26_33e996da9d','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:10:08','q26_user','2026-04-24 12:10:08'),('TEST_Q26_4fa028b5bb','AA_Q26_TEST_Q26_4fa028b5bb','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_4fa028b5bb','PI_Q26_TEST_Q26_4fa028b5bb','ORG_Q26','Q2.6 IT','q26_user','2026-04-25 12:32:26','q26_user','2026-04-25 12:32:25'),('TEST_Q26_524a5d85c7','AA_Q26_TEST_Q26_524a5d85c7','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_524a5d85c7','PI_Q26_TEST_Q26_524a5d85c7','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 11:34:40','q26_user','2026-04-24 11:34:39'),('TEST_Q26_5c1a28b77f','AA_Q26_TEST_Q26_5c1a28b77f','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_5c1a28b77f','PI_Q26_TEST_Q26_5c1a28b77f','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:15:08','q26_user','2026-04-24 12:15:07'),('TEST_Q26_5f1a08b01d','AA_Q26_TEST_Q26_5f1a08b01d','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_5f1a08b01d','PI_Q26_TEST_Q26_5f1a08b01d','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 17:15:49','q26_user','2026-04-24 17:15:49'),('TEST_Q26_661f05bf02','AA_Q26_TEST_Q26_661f05bf02','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_661f05bf02','PI_Q26_TEST_Q26_661f05bf02','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 09:12:35','q26_user','2026-04-24 09:12:35'),('TEST_Q26_66d37945a8','AA_Q26_TEST_Q26_66d37945a8','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_66d37945a8','PI_Q26_TEST_Q26_66d37945a8','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 21:08:13','q26_user','2026-04-24 21:08:12'),('TEST_Q26_7708382437','AA_Q26_TEST_Q26_7708382437','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_7708382437','PI_Q26_TEST_Q26_7708382437','ORG_Q26','Q2.6 IT','q26_user','2026-04-25 12:27:50','q26_user','2026-04-25 12:27:50'),('TEST_Q26_7a6f5f4f53','AA_Q26_TEST_Q26_7a6f5f4f53','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_7a6f5f4f53','PI_Q26_TEST_Q26_7a6f5f4f53','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:43:37','q26_user','2026-04-24 12:43:37'),('TEST_Q26_7c94fa44fc','AA_Q26_TEST_Q26_7c94fa44fc','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_7c94fa44fc','PI_Q26_TEST_Q26_7c94fa44fc','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:19:16','q26_user','2026-04-24 12:19:16'),('TEST_Q26_7cc925d3c8','AA_Q26_TEST_Q26_7cc925d3c8','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_7cc925d3c8','PI_Q26_TEST_Q26_7cc925d3c8','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:16:46','q26_user','2026-04-24 12:16:45'),('TEST_Q26_807ebe8ec2','AA_Q26_TEST_Q26_807ebe8ec2','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_807ebe8ec2','PI_Q26_TEST_Q26_807ebe8ec2','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:11:59','q26_user','2026-04-24 12:11:58'),('TEST_Q26_812381e699','AA_Q26_TEST_Q26_812381e699','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_812381e699','PI_Q26_TEST_Q26_812381e699','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 09:18:02','q26_user','2026-04-24 09:18:02'),('TEST_Q26_852f979f58','AA_Q26_TEST_Q26_852f979f58','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_852f979f58','PI_Q26_TEST_Q26_852f979f58','ORG_Q26','Q2.6 IT','q26_user','2026-04-23 22:58:10','q26_user','2026-04-23 22:58:10'),('TEST_Q26_8a92b01ade','AA_Q26_TEST_Q26_8a92b01ade','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_8a92b01ade','PI_Q26_TEST_Q26_8a92b01ade','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 09:24:55','q26_user','2026-04-24 09:24:55'),('TEST_Q26_8aa73ae082','AA_Q26_TEST_Q26_8aa73ae082','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_8aa73ae082','PI_Q26_TEST_Q26_8aa73ae082','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 11:45:50','q26_user','2026-04-24 11:45:50'),('TEST_Q26_9347d6b1ac','AA_Q26_TEST_Q26_9347d6b1ac','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_9347d6b1ac','PI_Q26_TEST_Q26_9347d6b1ac','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:06:14','q26_user','2026-04-24 13:06:13'),('TEST_Q26_a04c7e92c3','AA_Q26_TEST_Q26_a04c7e92c3','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_a04c7e92c3','PI_Q26_TEST_Q26_a04c7e92c3','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:06:31','q26_user','2026-04-24 14:06:31'),('TEST_Q26_a2d2c61387','AA_Q26_TEST_Q26_a2d2c61387','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_a2d2c61387','PI_Q26_TEST_Q26_a2d2c61387','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:51:35','q26_user','2026-04-24 14:51:34'),('TEST_Q26_a5849b2ebf','AA_Q26_TEST_Q26_a5849b2ebf','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_a5849b2ebf','PI_Q26_TEST_Q26_a5849b2ebf','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:09:45','q26_user','2026-04-24 14:09:44'),('TEST_Q26_a9322b8ea8','AA_Q26_TEST_Q26_a9322b8ea8','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_a9322b8ea8','PI_Q26_TEST_Q26_a9322b8ea8','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 21:03:06','q26_user','2026-04-24 21:03:05'),('TEST_Q26_aab1a1621a','AA_Q26_TEST_Q26_aab1a1621a','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_aab1a1621a','PI_Q26_TEST_Q26_aab1a1621a','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:07:58','q26_user','2026-04-24 14:07:57'),('TEST_Q26_aad963e5ef','AA_Q26_TEST_Q26_aad963e5ef','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_aad963e5ef','PI_Q26_TEST_Q26_aad963e5ef','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 20:43:08','q26_user','2026-04-24 20:43:08'),('TEST_Q26_b010eff863','AA_Q26_TEST_Q26_b010eff863','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_b010eff863','PI_Q26_TEST_Q26_b010eff863','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:13:34','q26_user','2026-04-24 12:13:33'),('TEST_Q26_b08765b854','AA_Q26_TEST_Q26_b08765b854','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_b08765b854','PI_Q26_TEST_Q26_b08765b854','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 11:27:26','q26_user','2026-04-24 11:27:26'),('TEST_Q26_b3356484c0','AA_Q26_TEST_Q26_b3356484c0','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_b3356484c0','PI_Q26_TEST_Q26_b3356484c0','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:08:20','q26_user','2026-04-24 13:08:20'),('TEST_Q26_b3870aa969','AA_Q26_TEST_Q26_b3870aa969','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_b3870aa969','PI_Q26_TEST_Q26_b3870aa969','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:12:31','q26_user','2026-04-24 13:12:31'),('TEST_Q26_b831bd9334','AA_Q26_TEST_Q26_b831bd9334','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_b831bd9334','PI_Q26_TEST_Q26_b831bd9334','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 11:39:02','q26_user','2026-04-24 11:39:02'),('TEST_Q26_b9a74e172f','AA_Q26_TEST_Q26_b9a74e172f','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_b9a74e172f','PI_Q26_TEST_Q26_b9a74e172f','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 18:45:56','q26_user','2026-04-24 18:45:56'),('TEST_Q26_c4bb938f59','AA_Q26_TEST_Q26_c4bb938f59','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_c4bb938f59','PI_Q26_TEST_Q26_c4bb938f59','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:05:08','q26_user','2026-04-24 14:05:08'),('TEST_Q26_c56b993e07','AA_Q26_TEST_Q26_c56b993e07','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_c56b993e07','PI_Q26_TEST_Q26_c56b993e07','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:57:02','q26_user','2026-04-24 13:57:01'),('TEST_Q26_c9de5988a1','AA_Q26_TEST_Q26_c9de5988a1','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_c9de5988a1','PI_Q26_TEST_Q26_c9de5988a1','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 17:18:58','q26_user','2026-04-24 17:18:58'),('TEST_Q26_ce94ccabe2','AA_Q26_TEST_Q26_ce94ccabe2','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_ce94ccabe2','PI_Q26_TEST_Q26_ce94ccabe2','ORG_Q26','Q2.6 IT','q26_user','2026-04-25 10:37:04','q26_user','2026-04-25 10:37:03'),('TEST_Q26_cfb82e2f12','AA_Q26_TEST_Q26_cfb82e2f12','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_cfb82e2f12','PI_Q26_TEST_Q26_cfb82e2f12','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 13:48:40','q26_user','2026-04-24 13:48:39'),('TEST_Q26_d3b58a29d3','AA_Q26_TEST_Q26_d3b58a29d3','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_d3b58a29d3','PI_Q26_TEST_Q26_d3b58a29d3','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 08:56:32','q26_user','2026-04-24 08:56:31'),('TEST_Q26_d6d3961713','AA_Q26_TEST_Q26_d6d3961713','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_d6d3961713','PI_Q26_TEST_Q26_d6d3961713','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:28:28','q26_user','2026-04-24 14:28:28'),('TEST_Q26_e2d9e9d249','AA_Q26_TEST_Q26_e2d9e9d249','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_e2d9e9d249','PI_Q26_TEST_Q26_e2d9e9d249','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:45:41','q26_user','2026-04-24 12:45:40'),('TEST_Q26_e767678066','AA_Q26_TEST_Q26_e767678066','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_e767678066','PI_Q26_TEST_Q26_e767678066','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 11:47:23','q26_user','2026-04-24 11:47:23'),('TEST_Q26_ef09984a8f','AA_Q26_TEST_Q26_ef09984a8f','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_ef09984a8f','PI_Q26_TEST_Q26_ef09984a8f','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 12:07:44','q26_user','2026-04-24 12:07:43'),('TEST_Q26_f0cd42c68c','AA_Q26_TEST_Q26_f0cd42c68c','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_f0cd42c68c','PI_Q26_TEST_Q26_f0cd42c68c','ORG_Q26','Q2.6 IT','q26_user','2026-04-24 14:12:02','q26_user','2026-04-24 14:12:01'),('TEST_Q26_R_02e86e43b2','AA_Q26_R_TEST_Q26_R_02e86e43b2','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_02e86e43b2','PI_Q26_R_TEST_Q26_R_02e86e43b2','ORG_Q26',NULL,'q26_user','2026-04-24 11:27:26','q26_user','2026-04-24 11:27:26'),('TEST_Q26_R_041ef7b8bc','AA_Q26_R_TEST_Q26_R_041ef7b8bc','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_041ef7b8bc','PI_Q26_R_TEST_Q26_R_041ef7b8bc','ORG_Q26',NULL,'q26_user','2026-04-24 13:45:21','q26_user','2026-04-24 13:45:20'),('TEST_Q26_R_081ec83b07','AA_Q26_R_TEST_Q26_R_081ec83b07','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_081ec83b07','PI_Q26_R_TEST_Q26_R_081ec83b07','ORG_Q26',NULL,'q26_user','2026-04-24 14:43:11','q26_user','2026-04-24 14:43:10'),('TEST_Q26_R_1a545ec5fc','AA_Q26_R_TEST_Q26_R_1a545ec5fc','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_1a545ec5fc','PI_Q26_R_TEST_Q26_R_1a545ec5fc','ORG_Q26',NULL,'q26_user','2026-04-24 16:36:20','q26_user','2026-04-24 16:36:20'),('TEST_Q26_R_1c49a5da0d','AA_Q26_R_TEST_Q26_R_1c49a5da0d','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_1c49a5da0d','PI_Q26_R_TEST_Q26_R_1c49a5da0d','ORG_Q26',NULL,'q26_user','2026-04-24 13:10:25','q26_user','2026-04-24 13:10:24'),('TEST_Q26_R_1e62c37091','AA_Q26_R_TEST_Q26_R_1e62c37091','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_1e62c37091','PI_Q26_R_TEST_Q26_R_1e62c37091','ORG_Q26',NULL,'q26_user','2026-04-24 14:41:23','q26_user','2026-04-24 14:41:23'),('TEST_Q26_R_22e5e4cb7c','AA_Q26_R_TEST_Q26_R_22e5e4cb7c','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_22e5e4cb7c','PI_Q26_R_TEST_Q26_R_22e5e4cb7c','ORG_Q26',NULL,'q26_user','2026-04-24 13:48:39','q26_user','2026-04-24 13:48:39'),('TEST_Q26_R_29ef0b50a1','AA_Q26_R_TEST_Q26_R_29ef0b50a1','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_29ef0b50a1','PI_Q26_R_TEST_Q26_R_29ef0b50a1','ORG_Q26',NULL,'q26_user','2026-04-24 08:56:32','q26_user','2026-04-24 08:56:31'),('TEST_Q26_R_2c5ce25142','AA_Q26_R_TEST_Q26_R_2c5ce25142','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_2c5ce25142','PI_Q26_R_TEST_Q26_R_2c5ce25142','ORG_Q26',NULL,'q26_user','2026-04-24 12:19:16','q26_user','2026-04-24 12:19:16'),('TEST_Q26_R_2d96647e52','AA_Q26_R_TEST_Q26_R_2d96647e52','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_2d96647e52','PI_Q26_R_TEST_Q26_R_2d96647e52','ORG_Q26',NULL,'q26_user','2026-04-24 12:13:34','q26_user','2026-04-24 12:13:33'),('TEST_Q26_R_354acd3943','AA_Q26_R_TEST_Q26_R_354acd3943','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_354acd3943','PI_Q26_R_TEST_Q26_R_354acd3943','ORG_Q26',NULL,'q26_user','2026-04-24 14:51:35','q26_user','2026-04-24 14:51:34'),('TEST_Q26_R_40aba5d984','AA_Q26_R_TEST_Q26_R_40aba5d984','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_40aba5d984','PI_Q26_R_TEST_Q26_R_40aba5d984','ORG_Q26',NULL,'q26_user','2026-04-23 22:11:18','q26_user','2026-04-23 22:11:18'),('TEST_Q26_R_4311e9e952','AA_Q26_R_TEST_Q26_R_4311e9e952','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_4311e9e952','PI_Q26_R_TEST_Q26_R_4311e9e952','ORG_Q26',NULL,'q26_user','2026-04-24 12:45:41','q26_user','2026-04-24 12:45:40'),('TEST_Q26_R_4714ea07cb','AA_Q26_R_TEST_Q26_R_4714ea07cb','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_4714ea07cb','PI_Q26_R_TEST_Q26_R_4714ea07cb','ORG_Q26',NULL,'q26_user','2026-04-24 11:47:23','q26_user','2026-04-24 11:47:23'),('TEST_Q26_R_486264e5bc','AA_Q26_R_TEST_Q26_R_486264e5bc','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_486264e5bc','PI_Q26_R_TEST_Q26_R_486264e5bc','ORG_Q26',NULL,'q26_user','2026-04-25 12:32:26','q26_user','2026-04-25 12:32:25'),('TEST_Q26_R_496c184827','AA_Q26_R_TEST_Q26_R_496c184827','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_496c184827','PI_Q26_R_TEST_Q26_R_496c184827','ORG_Q26',NULL,'q26_user','2026-04-24 13:12:31','q26_user','2026-04-24 13:12:31'),('TEST_Q26_R_51654ff498','AA_Q26_R_TEST_Q26_R_51654ff498','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_51654ff498','PI_Q26_R_TEST_Q26_R_51654ff498','ORG_Q26',NULL,'q26_user','2026-04-24 17:15:49','q26_user','2026-04-24 17:15:49'),('TEST_Q26_R_5203b95677','AA_Q26_R_TEST_Q26_R_5203b95677','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_5203b95677','PI_Q26_R_TEST_Q26_R_5203b95677','ORG_Q26',NULL,'q26_user','2026-04-24 11:39:02','q26_user','2026-04-24 11:39:02'),('TEST_Q26_R_560ab684e5','AA_Q26_R_TEST_Q26_R_560ab684e5','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_560ab684e5','PI_Q26_R_TEST_Q26_R_560ab684e5','ORG_Q26',NULL,'q26_user','2026-04-24 09:33:58','q26_user','2026-04-24 09:33:58'),('TEST_Q26_R_5c386fd49d','AA_Q26_R_TEST_Q26_R_5c386fd49d','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_5c386fd49d','PI_Q26_R_TEST_Q26_R_5c386fd49d','ORG_Q26',NULL,'q26_user','2026-04-24 18:39:19','q26_user','2026-04-24 18:39:18'),('TEST_Q26_R_695c83ff76','AA_Q26_R_TEST_Q26_R_695c83ff76','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_695c83ff76','PI_Q26_R_TEST_Q26_R_695c83ff76','ORG_Q26',NULL,'q26_user','2026-04-24 13:08:20','q26_user','2026-04-24 13:08:20'),('TEST_Q26_R_6db26e0fff','AA_Q26_R_TEST_Q26_R_6db26e0fff','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_6db26e0fff','PI_Q26_R_TEST_Q26_R_6db26e0fff','ORG_Q26',NULL,'q26_user','2026-04-24 12:16:46','q26_user','2026-04-24 12:16:45'),('TEST_Q26_R_76919b3679','AA_Q26_R_TEST_Q26_R_76919b3679','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_76919b3679','PI_Q26_R_TEST_Q26_R_76919b3679','ORG_Q26',NULL,'q26_user','2026-04-24 09:12:35','q26_user','2026-04-24 09:12:35'),('TEST_Q26_R_76ca8fa92c','AA_Q26_R_TEST_Q26_R_76ca8fa92c','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_76ca8fa92c','PI_Q26_R_TEST_Q26_R_76ca8fa92c','ORG_Q26',NULL,'q26_user','2026-04-24 14:05:08','q26_user','2026-04-24 14:05:08'),('TEST_Q26_R_76e2396c41','AA_Q26_R_TEST_Q26_R_76e2396c41','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_76e2396c41','PI_Q26_R_TEST_Q26_R_76e2396c41','ORG_Q26',NULL,'q26_user','2026-04-24 12:07:44','q26_user','2026-04-24 12:07:43'),('TEST_Q26_R_7737e16fcd','AA_Q26_R_TEST_Q26_R_7737e16fcd','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_7737e16fcd','PI_Q26_R_TEST_Q26_R_7737e16fcd','ORG_Q26',NULL,'q26_user','2026-04-24 21:08:13','q26_user','2026-04-24 21:08:12'),('TEST_Q26_R_78c2a97e2c','AA_Q26_R_TEST_Q26_R_78c2a97e2c','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_78c2a97e2c','PI_Q26_R_TEST_Q26_R_78c2a97e2c','ORG_Q26',NULL,'q26_user','2026-04-24 17:18:58','q26_user','2026-04-24 17:18:58'),('TEST_Q26_R_8356cd4d13','AA_Q26_R_TEST_Q26_R_8356cd4d13','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_8356cd4d13','PI_Q26_R_TEST_Q26_R_8356cd4d13','ORG_Q26',NULL,'q26_user','2026-04-24 12:15:08','q26_user','2026-04-24 12:15:07'),('TEST_Q26_R_863f087f05','AA_Q26_R_TEST_Q26_R_863f087f05','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_863f087f05','PI_Q26_R_TEST_Q26_R_863f087f05','ORG_Q26',NULL,'q26_user','2026-04-25 12:27:50','q26_user','2026-04-25 12:27:50'),('TEST_Q26_R_8728da5790','AA_Q26_R_TEST_Q26_R_8728da5790','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_8728da5790','PI_Q26_R_TEST_Q26_R_8728da5790','ORG_Q26',NULL,'q26_user','2026-04-24 12:43:37','q26_user','2026-04-24 12:43:37'),('TEST_Q26_R_a0c9a15f57','AA_Q26_R_TEST_Q26_R_a0c9a15f57','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_a0c9a15f57','PI_Q26_R_TEST_Q26_R_a0c9a15f57','ORG_Q26',NULL,'q26_user','2026-04-24 12:10:08','q26_user','2026-04-24 12:10:08'),('TEST_Q26_R_b31045ec14','AA_Q26_R_TEST_Q26_R_b31045ec14','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_b31045ec14','PI_Q26_R_TEST_Q26_R_b31045ec14','ORG_Q26',NULL,'q26_user','2026-04-24 21:03:06','q26_user','2026-04-24 21:03:05'),('TEST_Q26_R_b3968ac328','AA_Q26_R_TEST_Q26_R_b3968ac328','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_b3968ac328','PI_Q26_R_TEST_Q26_R_b3968ac328','ORG_Q26',NULL,'q26_user','2026-04-24 20:42:10','q26_user','2026-04-24 20:42:09'),('TEST_Q26_R_b8ccdbec3b','AA_Q26_R_TEST_Q26_R_b8ccdbec3b','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_b8ccdbec3b','PI_Q26_R_TEST_Q26_R_b8ccdbec3b','ORG_Q26',NULL,'q26_user','2026-04-24 14:09:45','q26_user','2026-04-24 14:09:44'),('TEST_Q26_R_c15dccfd83','AA_Q26_R_TEST_Q26_R_c15dccfd83','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_c15dccfd83','PI_Q26_R_TEST_Q26_R_c15dccfd83','ORG_Q26',NULL,'q26_user','2026-04-24 14:07:58','q26_user','2026-04-24 14:07:57'),('TEST_Q26_R_c31081d457','AA_Q26_R_TEST_Q26_R_c31081d457','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_c31081d457','PI_Q26_R_TEST_Q26_R_c31081d457','ORG_Q26',NULL,'q26_user','2026-04-24 11:34:40','q26_user','2026-04-24 11:34:39'),('TEST_Q26_R_c3992d4930','AA_Q26_R_TEST_Q26_R_c3992d4930','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_c3992d4930','PI_Q26_R_TEST_Q26_R_c3992d4930','ORG_Q26',NULL,'q26_user','2026-04-24 17:20:27','q26_user','2026-04-24 17:20:26'),('TEST_Q26_R_c569a630ad','AA_Q26_R_TEST_Q26_R_c569a630ad','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_c569a630ad','PI_Q26_R_TEST_Q26_R_c569a630ad','ORG_Q26',NULL,'q26_user','2026-04-24 09:18:02','q26_user','2026-04-24 09:18:02'),('TEST_Q26_R_c856b0eff1','AA_Q26_R_TEST_Q26_R_c856b0eff1','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_c856b0eff1','PI_Q26_R_TEST_Q26_R_c856b0eff1','ORG_Q26',NULL,'q26_user','2026-04-25 10:37:04','q26_user','2026-04-25 10:37:03'),('TEST_Q26_R_caf634e4dd','AA_Q26_R_TEST_Q26_R_caf634e4dd','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_caf634e4dd','PI_Q26_R_TEST_Q26_R_caf634e4dd','ORG_Q26',NULL,'q26_user','2026-04-24 12:11:59','q26_user','2026-04-24 12:11:58'),('TEST_Q26_R_ceecf2db6e','AA_Q26_R_TEST_Q26_R_ceecf2db6e','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_ceecf2db6e','PI_Q26_R_TEST_Q26_R_ceecf2db6e','ORG_Q26',NULL,'q26_user','2026-04-24 13:06:14','q26_user','2026-04-24 13:06:13'),('TEST_Q26_R_cf99d7216d','AA_Q26_R_TEST_Q26_R_cf99d7216d','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_cf99d7216d','PI_Q26_R_TEST_Q26_R_cf99d7216d','ORG_Q26',NULL,'q26_user','2026-04-24 14:28:28','q26_user','2026-04-24 14:28:27'),('TEST_Q26_R_d424173c91','AA_Q26_R_TEST_Q26_R_d424173c91','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_d424173c91','PI_Q26_R_TEST_Q26_R_d424173c91','ORG_Q26',NULL,'q26_user','2026-04-24 16:45:30','q26_user','2026-04-24 16:45:30'),('TEST_Q26_R_d65dd68d5e','AA_Q26_R_TEST_Q26_R_d65dd68d5e','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_d65dd68d5e','PI_Q26_R_TEST_Q26_R_d65dd68d5e','ORG_Q26',NULL,'q26_user','2026-04-24 13:57:02','q26_user','2026-04-24 13:57:01'),('TEST_Q26_R_dd46353420','AA_Q26_R_TEST_Q26_R_dd46353420','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_dd46353420','PI_Q26_R_TEST_Q26_R_dd46353420','ORG_Q26',NULL,'q26_user','2026-04-24 11:45:50','q26_user','2026-04-24 11:45:49'),('TEST_Q26_R_df72e27d31','AA_Q26_R_TEST_Q26_R_df72e27d31','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_df72e27d31','PI_Q26_R_TEST_Q26_R_df72e27d31','ORG_Q26',NULL,'q26_user','2026-04-23 22:58:10','q26_user','2026-04-23 22:58:10'),('TEST_Q26_R_e05674833b','AA_Q26_R_TEST_Q26_R_e05674833b','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_e05674833b','PI_Q26_R_TEST_Q26_R_e05674833b','ORG_Q26',NULL,'q26_user','2026-04-24 14:12:02','q26_user','2026-04-24 14:12:01'),('TEST_Q26_R_e0cc977f36','AA_Q26_R_TEST_Q26_R_e0cc977f36','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_e0cc977f36','PI_Q26_R_TEST_Q26_R_e0cc977f36','ORG_Q26',NULL,'q26_user','2026-04-24 14:06:31','q26_user','2026-04-24 14:06:31'),('TEST_Q26_R_e1fe24d80c','AA_Q26_R_TEST_Q26_R_e1fe24d80c','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_e1fe24d80c','PI_Q26_R_TEST_Q26_R_e1fe24d80c','ORG_Q26',NULL,'q26_user','2026-04-24 20:43:08','q26_user','2026-04-24 20:43:08'),('TEST_Q26_R_e8aa66193f','AA_Q26_R_TEST_Q26_R_e8aa66193f','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_e8aa66193f','PI_Q26_R_TEST_Q26_R_e8aa66193f','ORG_Q26',NULL,'q26_user','2026-04-24 09:24:55','q26_user','2026-04-24 09:24:55'),('TEST_Q26_R_fcfaa58ee8','AA_Q26_R_TEST_Q26_R_fcfaa58ee8','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_fcfaa58ee8','PI_Q26_R_TEST_Q26_R_fcfaa58ee8','ORG_Q26',NULL,'q26_user','2026-04-24 18:45:56','q26_user','2026-04-24 18:45:56');
/*!40000 ALTER TABLE `perf_alloc_adjust_apply` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_alloc_adjust_item`
--

DROP TABLE IF EXISTS `perf_alloc_adjust_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_alloc_adjust_item` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项ID',
  `apply_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
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
-- Table structure for table `perf_export_task`
--

DROP TABLE IF EXISTS `perf_export_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_export_task` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出任务ID',
  `export_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：KPI/METRIC/ALLOC/DETAIL',
  `params_json` text COLLATE utf8mb4_general_ci COMMENT '导出参数 JSON',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `file_key` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO object key',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `row_count` int DEFAULT NULL COMMENT '导出行数',
  `expire_at` datetime DEFAULT NULL COMMENT '文件过期时间',
  `operator_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作人员工号',
  `error_msg` text COLLATE utf8mb4_general_ci COMMENT '失败原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_export_type` (`export_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效异步导出任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perf_export_task`
--

LOCK TABLES `perf_export_task` WRITE;
/*!40000 ALTER TABLE `perf_export_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `perf_export_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_import_batch`
--

DROP TABLE IF EXISTS `perf_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_import_batch` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次ID',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次号',
  `import_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导入类型：INDEX_RESULT/KPI_RESULT/TARGET',
  `dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `as_of_date` date DEFAULT NULL COMMENT 'KPI导入基准日(可空)',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件名',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件MD5',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/SUCCESS/FAILED',
  `total_rows` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `success_rows` int NOT NULL DEFAULT '0' COMMENT '成功行数',
  `error_rows` int NOT NULL DEFAULT '0' COMMENT '失败行数',
  `error_file_object_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误明细文件ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
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
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项ID',
  `scheme_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案ID',
  `metric_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码(人员维度)',
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
/*!40000 ALTER TABLE `perf_kpi_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_kpi_scheme`
--

DROP TABLE IF EXISTS `perf_kpi_scheme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_kpi_scheme` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案ID',
  `scheme_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案编码(唯一)',
  `scheme_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `cycle_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期：MONTHLY/QUARTERLY',
  `open_detail` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否向员工开放明细',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
/*!40000 ALTER TABLE `perf_kpi_scheme` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_metric_def`
--

DROP TABLE IF EXISTS `perf_metric_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_metric_def` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标ID',
  `metric_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码(唯一)',
  `metric_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标名称',
  `metric_name_en` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '英文名',
  `metric_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '指标说明',
  `base_dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '基础维度：EMP/ORG/CUST',
  `metric_level` int NOT NULL COMMENT '指标层级：1/2/3',
  `calc_freq` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '计算频率：DAY/MONTH/QUARTER/YEAR',
  `calc_mode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '计算方式：AUTO/MANUAL',
  `calc_logic_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '逻辑类型：SQL/PROC/EXPR/SUMMARY',
  `sql_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '一级指标SQL/存储过程文本',
  `expr_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '二/三级指标表达式',
  `summary_rule` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '机构汇总规则：SUM/AVG等',
  `ref_metric_codes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '引用指标列表(JSON数组)',
  `val_slot` int DEFAULT NULL COMMENT '宽表槽位(1..200)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `unit` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '单位：元/万元/%',
  `decimal_places` tinyint DEFAULT '2' COMMENT '小数位数',
  `deleted` tinyint DEFAULT '0' COMMENT '0=存在 1=删除',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '指标详细描述',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_code` (`metric_code`),
  UNIQUE KEY `uk_base_dim_slot_alive` ((if((`deleted` = 0),concat(`base_dim`,_utf8mb4'#',`val_slot`),NULL))),
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
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '引用ID',
  `metric_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '引用者(上层指标)',
  `ref_metric_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '被引用(下层指标)',
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
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `task_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC',
  `task_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关键键(如metric_code)',
  `data_date` date DEFAULT NULL COMMENT '数据日期',
  `data_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据版本',
  `params_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '参数(JSON)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `started_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '发起人',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `error_msg` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `result_preview_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '结果预览(JSON)',
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
/*!40000 ALTER TABLE `perf_run_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_target_adjust_apply`
--

DROP TABLE IF EXISTS `perf_target_adjust_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_target_adjust_apply` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `plan_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `subject_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型：EMP/ORG',
  `subject_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID',
  `cycle_key` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期键',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
INSERT INTO `perf_target_adjust_apply` VALUES ('TEST_Q33_0cacfabc49','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_0cacfabc49','PI_Q33_TEST_Q33_0cacfabc49','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:08:22','q33_user','2026-04-24 13:08:21'),('TEST_Q33_16e191421e','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_16e191421e','PI_Q33_TEST_Q33_16e191421e','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 11:34:41','q33_user','2026-04-24 11:34:40'),('TEST_Q33_1ba1267ca1','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_1ba1267ca1','PI_Q33_TEST_Q33_1ba1267ca1','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-23 22:58:14','q33_user','2026-04-23 22:58:13'),('TEST_Q33_1d3e7f2185','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_1d3e7f2185','PI_Q33_TEST_Q33_1d3e7f2185','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:15:09','q33_user','2026-04-24 12:15:09'),('TEST_Q33_25d6346e63','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_25d6346e63','PI_Q33_TEST_Q33_25d6346e63','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:43:12','q33_user','2026-04-24 14:43:11'),('TEST_Q33_2e7dfcff9f','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_2e7dfcff9f','PI_Q33_TEST_Q33_2e7dfcff9f','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:57:03','q33_user','2026-04-24 13:57:02'),('TEST_Q33_3417f27d2e','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_3417f27d2e','PI_Q33_TEST_Q33_3417f27d2e','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 11:45:51','q33_user','2026-04-24 11:45:51'),('TEST_Q33_37dfe79f09','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_37dfe79f09','PI_Q33_TEST_Q33_37dfe79f09','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 09:25:02','q33_user','2026-04-24 09:25:01'),('TEST_Q33_3b1f5b9189','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_3b1f5b9189','PI_Q33_TEST_Q33_3b1f5b9189','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 20:42:11','q33_user','2026-04-24 20:42:10'),('TEST_Q33_3c3b1981bf','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_3c3b1981bf','PI_Q33_TEST_Q33_3c3b1981bf','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:06:32','q33_user','2026-04-24 14:06:32'),('TEST_Q33_48e2159440','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_48e2159440','PI_Q33_TEST_Q33_48e2159440','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 17:15:50','q33_user','2026-04-24 17:15:50'),('TEST_Q33_49a88b2315','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_49a88b2315','PI_Q33_TEST_Q33_49a88b2315','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 11:39:04','q33_user','2026-04-24 11:39:03'),('TEST_Q33_4b5b9b2b9d','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_4b5b9b2b9d','PI_Q33_TEST_Q33_4b5b9b2b9d','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-25 12:27:51','q33_user','2026-04-25 12:27:51'),('TEST_Q33_56a20c7ec6','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_56a20c7ec6','PI_Q33_TEST_Q33_56a20c7ec6','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 17:20:28','q33_user','2026-04-24 17:20:28'),('TEST_Q33_604db3b7b8','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_604db3b7b8','PI_Q33_TEST_Q33_604db3b7b8','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 16:45:31','q33_user','2026-04-24 16:45:31'),('TEST_Q33_7420b65d0f','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_7420b65d0f','PI_Q33_TEST_Q33_7420b65d0f','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 08:56:36','q33_user','2026-04-24 08:56:36'),('TEST_Q33_75bae3eb4d','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_75bae3eb4d','PI_Q33_TEST_Q33_75bae3eb4d','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:10:26','q33_user','2026-04-24 13:10:26'),('TEST_Q33_78c2dfbb7b','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_78c2dfbb7b','PI_Q33_TEST_Q33_78c2dfbb7b','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:09:46','q33_user','2026-04-24 14:09:46'),('TEST_Q33_7dc4197b4c','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_7dc4197b4c','PI_Q33_TEST_Q33_7dc4197b4c','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-23 22:41:05','q33_user','2026-04-23 22:41:04'),('TEST_Q33_7e1e6bca07','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_7e1e6bca07','PI_Q33_TEST_Q33_7e1e6bca07','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 11:47:25','q33_user','2026-04-24 11:47:24'),('TEST_Q33_7ecd42b455','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_7ecd42b455','PI_Q33_TEST_Q33_7ecd42b455','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 17:18:59','q33_user','2026-04-24 17:18:59'),('TEST_Q33_7fd015537a','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_7fd015537a','PI_Q33_TEST_Q33_7fd015537a','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 21:08:14','q33_user','2026-04-24 21:08:13'),('TEST_Q33_7fd20b700f','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_7fd20b700f','PI_Q33_TEST_Q33_7fd20b700f','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:10:10','q33_user','2026-04-24 12:10:09'),('TEST_Q33_852086adb5','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_852086adb5','PI_Q33_TEST_Q33_852086adb5','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:45:42','q33_user','2026-04-24 12:45:41'),('TEST_Q33_91dd68397e','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_91dd68397e','PI_Q33_TEST_Q33_91dd68397e','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-25 10:37:05','q33_user','2026-04-25 10:37:04'),('TEST_Q33_98cbedf2a9','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_98cbedf2a9','PI_Q33_TEST_Q33_98cbedf2a9','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:48:41','q33_user','2026-04-24 13:48:40'),('TEST_Q33_9cad9ec46c','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_9cad9ec46c','PI_Q33_TEST_Q33_9cad9ec46c','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:45:22','q33_user','2026-04-24 13:45:22'),('TEST_Q33_9fb39fc786','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_9fb39fc786','PI_Q33_TEST_Q33_9fb39fc786','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:19:18','q33_user','2026-04-24 12:19:17'),('TEST_Q33_a4b7714bae','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_a4b7714bae','PI_Q33_TEST_Q33_a4b7714bae','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 16:36:21','q33_user','2026-04-24 16:36:21'),('TEST_Q33_abbafee307','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_abbafee307','PI_Q33_TEST_Q33_abbafee307','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:12:03','q33_user','2026-04-24 14:12:02'),('TEST_Q33_af6be5488f','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_af6be5488f','PI_Q33_TEST_Q33_af6be5488f','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:05:09','q33_user','2026-04-24 14:05:09'),('TEST_Q33_b012371e88','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_b012371e88','PI_Q33_TEST_Q33_b012371e88','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 09:18:08','q33_user','2026-04-24 09:18:08'),('TEST_Q33_b912c2efb0','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_b912c2efb0','PI_Q33_TEST_Q33_b912c2efb0','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 18:39:20','q33_user','2026-04-24 18:39:19'),('TEST_Q33_d32855be71','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_d32855be71','PI_Q33_TEST_Q33_d32855be71','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:28:29','q33_user','2026-04-24 14:28:29'),('TEST_Q33_d5e809188c','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_d5e809188c','PI_Q33_TEST_Q33_d5e809188c','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:43:39','q33_user','2026-04-24 12:43:38'),('TEST_Q33_d84b9fc16b','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_d84b9fc16b','PI_Q33_TEST_Q33_d84b9fc16b','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:13:35','q33_user','2026-04-24 12:13:34'),('TEST_Q33_da0c28f656','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_da0c28f656','PI_Q33_TEST_Q33_da0c28f656','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:16:47','q33_user','2026-04-24 12:16:46'),('TEST_Q33_de9b715c8b','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_de9b715c8b','PI_Q33_TEST_Q33_de9b715c8b','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:07:59','q33_user','2026-04-24 14:07:58'),('TEST_Q33_e08a84880c','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_e08a84880c','PI_Q33_TEST_Q33_e08a84880c','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 20:43:09','q33_user','2026-04-24 20:43:09'),('TEST_Q33_e119262516','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_e119262516','PI_Q33_TEST_Q33_e119262516','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:07:45','q33_user','2026-04-24 12:07:44'),('TEST_Q33_e706dfd040','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_e706dfd040','PI_Q33_TEST_Q33_e706dfd040','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-25 12:32:27','q33_user','2026-04-25 12:32:26'),('TEST_Q33_e7f8964bcc','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_e7f8964bcc','PI_Q33_TEST_Q33_e7f8964bcc','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 11:27:28','q33_user','2026-04-24 11:27:27'),('TEST_Q33_e839ece0bb','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_e839ece0bb','PI_Q33_TEST_Q33_e839ece0bb','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:12:33','q33_user','2026-04-24 13:12:32'),('TEST_Q33_ebdc6c1461','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_ebdc6c1461','PI_Q33_TEST_Q33_ebdc6c1461','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:41:24','q33_user','2026-04-24 14:41:24'),('TEST_Q33_ed1b4d3b3a','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_ed1b4d3b3a','PI_Q33_TEST_Q33_ed1b4d3b3a','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 13:06:15','q33_user','2026-04-24 13:06:15'),('TEST_Q33_eeb40de329','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_eeb40de329','PI_Q33_TEST_Q33_eeb40de329','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 12:12:00','q33_user','2026-04-24 12:11:59'),('TEST_Q33_f44ff92d72','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_f44ff92d72','PI_Q33_TEST_Q33_f44ff92d72','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 18:45:57','q33_user','2026-04-24 18:45:57'),('TEST_Q33_f4bf3ec02b','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_f4bf3ec02b','PI_Q33_TEST_Q33_f4bf3ec02b','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 14:51:36','q33_user','2026-04-24 14:51:35'),('TEST_Q33_f5b37ff2b7','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_f5b37ff2b7','PI_Q33_TEST_Q33_f5b37ff2b7','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 09:12:42','q33_user','2026-04-24 09:12:41'),('TEST_Q33_f70c4c703a','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_f70c4c703a','PI_Q33_TEST_Q33_f70c4c703a','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 21:03:07','q33_user','2026-04-24 21:03:06'),('TEST_Q33_fff6fd4d5f','TEST_Q33_PLAN_X','EMP','EMP_Q33_001','2026Q1','APPROVED','TARGET_ADJUST:TEST_Q33_fff6fd4d5f','PI_Q33_TEST_Q33_fff6fd4d5f','ORG_Q33','{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.3 IT\"}','q33_user','2026-04-24 09:34:04','q33_user','2026-04-24 09:34:03'),('TEST_Q33_R_050d934066','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_050d934066','PI_Q33_R_TEST_Q33_R_050d934066','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-23 22:58:14','q33_user','2026-04-23 22:58:13'),('TEST_Q33_R_06d75cd507','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_06d75cd507','PI_Q33_R_TEST_Q33_R_06d75cd507','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 20:43:09','q33_user','2026-04-24 20:43:09'),('TEST_Q33_R_0d495d4832','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_0d495d4832','PI_Q33_R_TEST_Q33_R_0d495d4832','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:10:10','q33_user','2026-04-24 12:10:09'),('TEST_Q33_R_101131cdec','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_101131cdec','PI_Q33_R_TEST_Q33_R_101131cdec','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:19:18','q33_user','2026-04-24 12:19:17'),('TEST_Q33_R_13081e6513','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_13081e6513','PI_Q33_R_TEST_Q33_R_13081e6513','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 11:39:04','q33_user','2026-04-24 11:39:03'),('TEST_Q33_R_14e927cf0e','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_14e927cf0e','PI_Q33_R_TEST_Q33_R_14e927cf0e','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:12:03','q33_user','2026-04-24 14:12:02'),('TEST_Q33_R_196b5c4a00','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_196b5c4a00','PI_Q33_R_TEST_Q33_R_196b5c4a00','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 17:15:50','q33_user','2026-04-24 17:15:50'),('TEST_Q33_R_1a9c83afd9','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_1a9c83afd9','PI_Q33_R_TEST_Q33_R_1a9c83afd9','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:07:59','q33_user','2026-04-24 14:07:58'),('TEST_Q33_R_1aeef7036d','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_1aeef7036d','PI_Q33_R_TEST_Q33_R_1aeef7036d','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 16:45:31','q33_user','2026-04-24 16:45:31'),('TEST_Q33_R_1b038bcf19','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_1b038bcf19','PI_Q33_R_TEST_Q33_R_1b038bcf19','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:43:12','q33_user','2026-04-24 14:43:11'),('TEST_Q33_R_20af021d14','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_20af021d14','PI_Q33_R_TEST_Q33_R_20af021d14','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 09:34:04','q33_user','2026-04-24 09:34:03'),('TEST_Q33_R_2584f5878c','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_2584f5878c','PI_Q33_R_TEST_Q33_R_2584f5878c','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:57:03','q33_user','2026-04-24 13:57:02'),('TEST_Q33_R_2fb2081c12','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_2fb2081c12','PI_Q33_R_TEST_Q33_R_2fb2081c12','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:15:09','q33_user','2026-04-24 12:15:09'),('TEST_Q33_R_31240327e1','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_31240327e1','PI_Q33_R_TEST_Q33_R_31240327e1','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 11:45:51','q33_user','2026-04-24 11:45:51'),('TEST_Q33_R_350b242c3a','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_350b242c3a','PI_Q33_R_TEST_Q33_R_350b242c3a','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:41:24','q33_user','2026-04-24 14:41:24'),('TEST_Q33_R_384b7665fd','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_384b7665fd','PI_Q33_R_TEST_Q33_R_384b7665fd','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:09:46','q33_user','2026-04-24 14:09:46'),('TEST_Q33_R_3d8deaa878','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_3d8deaa878','PI_Q33_R_TEST_Q33_R_3d8deaa878','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 17:20:28','q33_user','2026-04-24 17:20:28'),('TEST_Q33_R_436879d6d4','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_436879d6d4','PI_Q33_R_TEST_Q33_R_436879d6d4','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:51:36','q33_user','2026-04-24 14:51:35'),('TEST_Q33_R_50bf94e69a','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_50bf94e69a','PI_Q33_R_TEST_Q33_R_50bf94e69a','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 18:39:20','q33_user','2026-04-24 18:39:19'),('TEST_Q33_R_53443b1e3a','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_53443b1e3a','PI_Q33_R_TEST_Q33_R_53443b1e3a','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-25 10:37:05','q33_user','2026-04-25 10:37:04'),('TEST_Q33_R_55b3426e47','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_55b3426e47','PI_Q33_R_TEST_Q33_R_55b3426e47','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 21:08:14','q33_user','2026-04-24 21:08:13'),('TEST_Q33_R_59428fc73b','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_59428fc73b','PI_Q33_R_TEST_Q33_R_59428fc73b','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:45:42','q33_user','2026-04-24 12:45:41'),('TEST_Q33_R_5ce468c7bb','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_5ce468c7bb','PI_Q33_R_TEST_Q33_R_5ce468c7bb','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:48:41','q33_user','2026-04-24 13:48:40'),('TEST_Q33_R_616db5fe7e','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_616db5fe7e','PI_Q33_R_TEST_Q33_R_616db5fe7e','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 21:03:07','q33_user','2026-04-24 21:03:06'),('TEST_Q33_R_656a6ccbef','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_656a6ccbef','PI_Q33_R_TEST_Q33_R_656a6ccbef','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 17:18:59','q33_user','2026-04-24 17:18:59'),('TEST_Q33_R_6a52c7d509','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_6a52c7d509','PI_Q33_R_TEST_Q33_R_6a52c7d509','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 09:18:08','q33_user','2026-04-24 09:18:08'),('TEST_Q33_R_70c17bee06','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_70c17bee06','PI_Q33_R_TEST_Q33_R_70c17bee06','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-23 22:41:04','q33_user','2026-04-23 22:41:04'),('TEST_Q33_R_70cb91da69','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_70cb91da69','PI_Q33_R_TEST_Q33_R_70cb91da69','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:43:39','q33_user','2026-04-24 12:43:38'),('TEST_Q33_R_72675dc209','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_72675dc209','PI_Q33_R_TEST_Q33_R_72675dc209','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 18:45:57','q33_user','2026-04-24 18:45:57'),('TEST_Q33_R_762adb5768','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_762adb5768','PI_Q33_R_TEST_Q33_R_762adb5768','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-25 12:27:51','q33_user','2026-04-25 12:27:51'),('TEST_Q33_R_78b3baa0dd','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_78b3baa0dd','PI_Q33_R_TEST_Q33_R_78b3baa0dd','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:13:35','q33_user','2026-04-24 12:13:34'),('TEST_Q33_R_7a523f8df1','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_7a523f8df1','PI_Q33_R_TEST_Q33_R_7a523f8df1','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 20:42:11','q33_user','2026-04-24 20:42:10'),('TEST_Q33_R_7a70699dac','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_7a70699dac','PI_Q33_R_TEST_Q33_R_7a70699dac','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-25 12:32:27','q33_user','2026-04-25 12:32:26'),('TEST_Q33_R_7cb3cb95f7','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_7cb3cb95f7','PI_Q33_R_TEST_Q33_R_7cb3cb95f7','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:12:33','q33_user','2026-04-24 13:12:32'),('TEST_Q33_R_7f552b53f9','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_7f552b53f9','PI_Q33_R_TEST_Q33_R_7f552b53f9','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 09:12:42','q33_user','2026-04-24 09:12:41'),('TEST_Q33_R_821f9d8a18','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_821f9d8a18','PI_Q33_R_TEST_Q33_R_821f9d8a18','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 08:56:36','q33_user','2026-04-24 08:56:36'),('TEST_Q33_R_8256375b9d','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_8256375b9d','PI_Q33_R_TEST_Q33_R_8256375b9d','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:06:15','q33_user','2026-04-24 13:06:15'),('TEST_Q33_R_96bc78b09b','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_96bc78b09b','PI_Q33_R_TEST_Q33_R_96bc78b09b','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:16:47','q33_user','2026-04-24 12:16:46'),('TEST_Q33_R_9b1563744e','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_9b1563744e','PI_Q33_R_TEST_Q33_R_9b1563744e','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:28:29','q33_user','2026-04-24 14:28:29'),('TEST_Q33_R_a134c499e3','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_a134c499e3','PI_Q33_R_TEST_Q33_R_a134c499e3','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:08:22','q33_user','2026-04-24 13:08:21'),('TEST_Q33_R_a3819f43ea','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_a3819f43ea','PI_Q33_R_TEST_Q33_R_a3819f43ea','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 11:47:25','q33_user','2026-04-24 11:47:24'),('TEST_Q33_R_ab62e0fdf2','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_ab62e0fdf2','PI_Q33_R_TEST_Q33_R_ab62e0fdf2','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:07:45','q33_user','2026-04-24 12:07:44'),('TEST_Q33_R_b039febf09','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_b039febf09','PI_Q33_R_TEST_Q33_R_b039febf09','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 12:12:00','q33_user','2026-04-24 12:11:59'),('TEST_Q33_R_b3f4d55cc7','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_b3f4d55cc7','PI_Q33_R_TEST_Q33_R_b3f4d55cc7','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 16:36:21','q33_user','2026-04-24 16:36:21'),('TEST_Q33_R_b4c60fb67e','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_b4c60fb67e','PI_Q33_R_TEST_Q33_R_b4c60fb67e','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:10:26','q33_user','2026-04-24 13:10:26'),('TEST_Q33_R_c72bcbc72d','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_c72bcbc72d','PI_Q33_R_TEST_Q33_R_c72bcbc72d','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:06:32','q33_user','2026-04-24 14:06:32'),('TEST_Q33_R_c9e053ce52','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_c9e053ce52','PI_Q33_R_TEST_Q33_R_c9e053ce52','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 09:25:02','q33_user','2026-04-24 09:25:01'),('TEST_Q33_R_d3bdc45cbe','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_d3bdc45cbe','PI_Q33_R_TEST_Q33_R_d3bdc45cbe','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 11:34:41','q33_user','2026-04-24 11:34:40'),('TEST_Q33_R_de3cdea3d2','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_de3cdea3d2','PI_Q33_R_TEST_Q33_R_de3cdea3d2','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 14:05:09','q33_user','2026-04-24 14:05:09'),('TEST_Q33_R_eac6fa69f3','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_eac6fa69f3','PI_Q33_R_TEST_Q33_R_eac6fa69f3','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 13:45:22','q33_user','2026-04-24 13:45:22'),('TEST_Q33_R_f15a5267b1','TEST_Q33_PLAN_R','ORG','ORG_Q33_101','2026Q2','REJECTED','TARGET_ADJUST:TEST_Q33_R_f15a5267b1','PI_Q33_R_TEST_Q33_R_f15a5267b1','ORG_Q33','{\"adjustments\":[],\"reason\":\"REJECT IT\"}','q33_user','2026-04-24 11:27:28','q33_user','2026-04-24 11:27:27');
/*!40000 ALTER TABLE `perf_target_adjust_apply` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perf_target_plan`
--

DROP TABLE IF EXISTS `perf_target_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_target_plan` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `plan_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案编码(唯一)',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `kpi_scheme_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联KPI方案ID',
  `target_dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标维度：EMP/ORG',
  `target_cycle` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标周期：YEAR/QUARTER',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `owner_emp_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'V1.4 S2.1 owner_emp_id',
  `owner_org_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'V1.4 S2.1 owner_org_code',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_status` (`status`),
  KEY `idx_owner_emp` (`owner_emp_id`),
  KEY `idx_owner_org` (`owner_org_code`)
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
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标值ID',
  `plan_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `subject_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型：EMP/ORG',
  `subject_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID(emp_id/org_code)',
  `cycle_key` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期键：2026或2026Q1',
  `metric_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码',
  `target_value` decimal(20,4) NOT NULL COMMENT '目标值',
  `base_value` decimal(20,4) DEFAULT NULL COMMENT '基础值(可空，默认为0)',
  `owner_emp_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'V1.4 S2.1 owner_emp_id',
  `owner_org_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'V1.4 S2.1 owner_org_code',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_subject_cycle_metric` (`plan_id`,`subject_type`,`subject_id`,`cycle_key`,`metric_code`),
  KEY `idx_metric_code` (`metric_code`),
  KEY `idx_subject` (`subject_type`,`subject_id`,`cycle_key`),
  KEY `idx_owner_emp` (`owner_emp_id`),
  KEY `idx_owner_org` (`owner_org_code`)
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
-- Table structure for table `portal_nav`
--

DROP TABLE IF EXISTS `portal_nav`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `portal_nav` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导航ID',
  `nav_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导航名称',
  `nav_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导航URL',
  `nav_icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `nav_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分类',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网址导航表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `portal_nav`
--

LOCK TABLES `portal_nav` WRITE;
/*!40000 ALTER TABLE `portal_nav` DISABLE KEYS */;
INSERT INTO `portal_nav` VALUES ('47ed474cd8e44a509b28820eb59ace40','TEST_排序C','https://example.com/排序c','icon-default','HQ_SYSTEM',10,'ACTIVE','tester','2026-04-25 15:22:18','batch_updater','2026-04-25 15:22:18'),('557b2ab684c44cb2a69d41067e815e70','TEST_排序B','https://example.com/排序b','icon-default','HQ_SYSTEM',20,'ACTIVE','tester','2026-04-25 15:22:18','batch_updater','2026-04-25 15:22:18'),('c02ce5e1a7624314aa61591a90711040','TEST_排序A','https://example.com/排序a','icon-default','HQ_SYSTEM',30,'ACTIVE','tester','2026-04-25 15:22:18','batch_updater','2026-04-25 15:22:18'),('NAV001','CCRM系统','https://ccrm.bank.com','icon-ccrm','总行系统',1,'ACTIVE','SYSTEM','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('NAV002','PCRM系统','https://pcrm.bank.com','icon-pcrm','总行系统',2,'ACTIVE','SYSTEM','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('NAV003','网银系统','https://ebank.bank.com','icon-ebank','电子渠道',3,'ACTIVE','SYSTEM','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('NAV004','信贷管理系统','https://credit.bank.com','icon-credit','风险管理',4,'ACTIVE','SYSTEM','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('NAV005','OA系统','https://oa.bank.com','icon-oa','办公系统',5,'ACTIVE','SYSTEM','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31');
/*!40000 ALTER TABLE `portal_nav` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `portal_shortcut`
--

DROP TABLE IF EXISTS `portal_shortcut`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `portal_shortcut` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '快捷入口ID',
  `shortcut_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '快捷入口名称',
  `shortcut_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '跳转URL',
  `shortcut_icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `shortcut_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型：SYSTEM-系统, CUSTOM-自定义',
  `target_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标类型：INTERNAL-内部, EXTERNAL-外部',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属用户工号（自定义快捷入口）',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_type` (`shortcut_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作台快捷入口表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `portal_shortcut`
--

LOCK TABLES `portal_shortcut` WRITE;
/*!40000 ALTER TABLE `portal_shortcut` DISABLE KEYS */;
INSERT INTO `portal_shortcut` VALUES ('207a9f942d95407085dd94428a1b86db','TEST_CUSTOM_E1','https://example.com/TEST_CUSTOM_E1',NULL,'CUSTOM','INTERNAL','E10001',0,'ACTIVE','tester',NULL,NULL,NULL),('2594479d6b1f45538c892bf60771d820','TEST_SYS_01','https://example.com/TEST_SYS_01',NULL,'SYSTEM','INTERNAL',NULL,0,'ACTIVE','tester',NULL,NULL,NULL),('b8f12b6ffac44ce58a24034a688fc355','TEST_CUSTOM_E2','https://example.com/TEST_CUSTOM_E2',NULL,'CUSTOM','INTERNAL','E10002',0,'ACTIVE','tester',NULL,NULL,NULL);
/*!40000 ALTER TABLE `portal_shortcut` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_info`
--

DROP TABLE IF EXISTS `product_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_info` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品ID',
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品代码',
  `product_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品名称',
  `product_category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品类别',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '产品描述',
  `support_for_support_request` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否支持中场支持',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属组织(维护组织)',
  `product_dept_org_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产品部门ORG_CODE',
  `file_object_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主附件文件ID',
  `responsible_emp_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '负责人列表(JSON数组,反向关联通讯录)',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品状态',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_product_code` (`product_code`),
  KEY `idx_category` (`product_category`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_info`
--

LOCK TABLES `product_info` WRITE;
/*!40000 ALTER TABLE `product_info` DISABLE KEYS */;
INSERT INTO `product_info` VALUES ('36361030564149f3933893e68e723c4d','SCOPE_ORG_2','产品 SCOPE_ORG_2','CAT_DEPOSIT',NULL,0,NULL,'ORG_SZ_002',NULL,NULL,'ACTIVE','tester',NULL,'2026-04-25 15:22:19',NULL,0),('9851faf974384106ab63daad451045c0','SCOPE_ORG_3','产品 SCOPE_ORG_3','CAT_DEPOSIT',NULL,0,NULL,'ORG_BJ_001',NULL,NULL,'ACTIVE','tester',NULL,'2026-04-25 15:22:19',NULL,0),('aaf56f8efaa94cd79e2f00985f5e2bc5','SCOPE_ORG_1','产品 SCOPE_ORG_1','CAT_DEPOSIT',NULL,0,NULL,'ORG_SZ_001',NULL,NULL,'ACTIVE','tester',NULL,'2026-04-25 15:22:19',NULL,0),('PROD001','TBK_DEPOSIT','交易银行-结构性存款','交易银行','结构性存款产品介绍',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-03 22:46:31','2026-04-03 22:46:31',0),('PROD002','TBK_SUPPLY_CHAIN','交易银行-供应链金融','交易银行','供应链金融产品介绍',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-03 22:46:31','2026-04-03 22:46:31',0),('PROD003','FM_BOND','金融市场-债券承销','金融市场','债券承销服务介绍',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-03 22:46:31','2026-04-03 22:46:31',0),('PROD004','CORP_LOAN','公司-流动资金贷款','公司银行','流动资金贷款产品',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-03 22:46:31','2026-04-03 22:46:31',0);
/*!40000 ALTER TABLE `product_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_resource`
--

DROP TABLE IF EXISTS `pt_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_resource` (
  `RESOURCE_ID` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源ID',
  `RESOURCE_URL` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源URL（支持Ant通配符）',
  `RESOURCE_METHOD` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '请求方法：GET/POST/PUT/DELETE，支持 *',
  `MENU_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `MENU_ICON_URL` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标路径',
  `MENU_RANK_NO` int DEFAULT '0' COMMENT '菜单排序',
  `ISMENU` int DEFAULT '0' COMMENT '是否菜单 0 是 1 不是',
  `MENU_ENDFLAG` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '表单结束标志，是否叶子节点菜单 1 是 0 不是',
  `PARENT_RESOURCE_ID` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上级资源ID',
  `STATUS` int DEFAULT '0' COMMENT '状态 0启用 1 不启用',
  `SYS_CODE` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`RESOURCE_ID`),
  UNIQUE KEY `uk_pt_resource_url_method_sys` (`RESOURCE_URL`,`RESOURCE_METHOD`,`SYS_CODE`),
  KEY `idx_pt_resource_status` (`STATUS`,`SYS_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_resource`
--

LOCK TABLES `pt_resource` WRITE;
/*!40000 ALTER TABLE `pt_resource` DISABLE KEYS */;
INSERT INTO `pt_resource` VALUES ('A_BZ_DELETE','/api/admin/biz-scopes/*','DELETE','删除业务范围',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_BZ_LIST','/api/admin/biz-scopes','GET','业务范围列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_BZ_MATRIX','/api/admin/biz-scopes/matrix','GET','业务范围矩阵',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_BZ_SAVE','/api/admin/biz-scopes','POST','保存业务范围',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_CHECK_PERM','/api/auth/check-permission','POST','权限校验',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_CURR_USER','/api/auth/current-user','GET','当前用户信息',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_LOGIN','/api/auth/login','POST','用户登录',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_LOGOUT','/api/auth/logout','POST','用户登出',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ORG_SUBTREE','/api/orgs/subtree','GET','当前机构子树',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ORG_TREE','/api/orgs/tree','GET','组织机构树',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ORG_USERS','/api/orgs/*/users','GET','机构下用户',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_PERMS','/api/auth/permissions','GET','当前用户权限集',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_CREATE','/api/admin/resources','POST','创建资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_DELETE','/api/admin/resources/*','DELETE','删除资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_TREE','/api/admin/resources/tree','GET','资源树',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_UPDATE','/api/admin/resources/*','PUT','更新资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_CREATE','/api/admin/roles/','POST','创建角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_DELETE','/api/admin/roles/*','DELETE','删除角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_LIST','/api/admin/roles/','GET','角色列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_UPDATE','/api/admin/roles/*','PUT','更新角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_USERS','/api/admin/roles/*/users','GET','角色下用户列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RR_BIND','/api/admin/roles/*/resources','POST','增量绑定角色资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RR_LIST','/api/admin/roles/*/resources','GET','角色资源列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RR_REPLACE','/api/admin/roles/*/resources','PUT','全量替换角色资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_UR_BIND','/api/admin/users/*/roles','POST','绑定用户角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_UR_DEL','/api/admin/users/*/roles/*','DELETE','解绑用户角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_UR_LIST','/api/admin/users/*/roles','GET','用户角色列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_AUDIT_DETAIL','/api/admin/sys/audit-logs/*','GET','审计日志详情',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_AUDIT_EXPORT','/api/admin/sys/audit-logs/export','POST','导出审计日志',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_AUDIT_LIST','/api/admin/sys/audit-logs','GET','审计日志列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_GET','/api/admin/sys/calendar','GET','查询工作日',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_IMPORT','/api/admin/sys/calendar/import','POST','导入节假日',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_INIT','/api/admin/sys/calendar/init','POST','初始化年份',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_PUBLIC','/api/sys/calendar','GET','公共日历查询',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_SET','/api/admin/sys/calendar/*','PUT','设置工作日',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CFG_LIST','/api/admin/sys/configs','GET','配置列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CFG_UPDATE','/api/admin/sys/configs/*','PUT','更新配置',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_CREATE','/api/admin/sys/dicts','POST','创建字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_DELETE','/api/admin/sys/dicts/*','DELETE','删除字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_ITEMS','/api/sys/dicts/*/items','GET','字典项列表(公共)',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_LIST','/api/sys/dicts','GET','字典类型列表(公共)',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_STATUS','/api/admin/sys/dicts/*/status','PUT','启禁字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_UPDATE','/api/admin/sys/dicts/*','PUT','更新字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_DELETE','/api/files/*','DELETE','删除文件',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_DOWNLOAD','/api/files/*/download','GET','下载文件',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_LIST','/api/files','GET','业务文件列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_UPLOAD','/api/files/upload','POST','上传文件',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_LIST','/api/admin/sys/jobs','GET','任务列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_LOGS','/api/admin/sys/jobs/*/logs','GET','任务执行日志',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_PAUSE','/api/admin/sys/jobs/*/pause','PUT','暂停任务',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_RESUME','/api/admin/sys/jobs/*/resume','PUT','恢复任务',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_TRIGGER','/api/admin/sys/jobs/*/trigger','POST','手动触发任务',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_COUNT','/api/notifications/unread-count','GET','未读通知数',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_DETAIL','/api/notifications/*','GET','通知详情',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_LIST','/api/notifications','GET','通知列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_READ','/api/notifications/*/read','PUT','标记已读',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_READ_ALL','/api/notifications/read-all','PUT','全部已读',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_SQL_EXEC','/api/admin/sql-probe/execute','POST','SQL 执行探查',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_SQL_HIST','/api/admin/sql-probe/history','GET','SQL 执行历史',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('P_PERF_ALLOC_CUR','/api/perf/alloc-relations','GET','当前分配关系',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_ALLOC_HIS','/api/perf/alloc-relations/history','GET','历史分配关系',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_ALLOC_SUM','/api/perf/alloc-relations/summary','GET','分配关系汇总',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_EXPT_DTL','/api/perf/export/detail','POST','KPI 明细导出',NULL,0,0,'0',NULL,0,'PERF','2026-04-24 08:05:37','seed','2026-04-24 08:05:37',NULL,'V1.2 Q6.4 高危'),('P_PERF_EXPT_MTR','/api/perf/export/metric','POST','指标宽表导出',NULL,0,0,'0',NULL,0,'PERF','2026-04-24 08:05:37','seed','2026-04-24 08:05:37',NULL,'V1.2 Q6.4'),('P_PERF_EXPT_TASK','/api/perf/export/task/*','GET','导出任务状态',NULL,0,0,'0',NULL,0,'PERF','2026-04-24 08:05:37','seed','2026-04-24 08:05:37',NULL,'V1.2 Q6.4'),('P_PERF_KPI_ADD','/api/perf/kpi-schemes','POST','新增KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_DEL','/api/perf/kpi-schemes/*','DELETE','删除KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_GET','/api/perf/kpi-schemes/*','GET','KPI方案详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_IADD','/api/perf/kpi-schemes/*/items','POST','添加指标项',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_IDEL','/api/perf/kpi-schemes/*/items/*','DELETE','删除指标项',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_IUPD','/api/perf/kpi-schemes/*/items/*','PUT','编辑指标项',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_LIST','/api/perf/kpi-schemes','GET','KPI方案列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_PUB','/api/perf/kpi-schemes/*/publish','POST','发布KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_UPD','/api/perf/kpi-schemes/*','PUT','编辑KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_ADD','/api/perf/metrics','POST','新增指标',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_DEL','/api/perf/metrics/*','DELETE','删除指标',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_GET','/api/perf/metrics/*','GET','指标详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_LIST','/api/perf/metrics','GET','指标列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_RBY','/api/perf/metrics/*/ref-by','GET','查谁引用了我',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_REFS','/api/perf/metrics/*/refs','GET','查指标上游依赖',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_SLOT','/api/perf/metrics/val-slots','GET','槽位占用查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_SREL','/api/perf/metrics/*/slot/release','POST','强制释放槽位',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_STAT','/api/perf/metrics/*/status','PUT','指标状态流转',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_UPD','/api/perf/metrics/*','PUT','编辑指标',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_RT_GET','/api/perf/run-tasks/*','GET','任务日志详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_RT_LIST','/api/perf/run-tasks','GET','任务日志列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_GET','/api/perf/sys-control','GET','版本查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_HIS','/api/perf/sys-control/history','GET','版本历史',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_INIT','/api/perf/sys-control/init','POST','版本初始化',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_SW','/api/perf/sys-control/switch-version','POST','版本切换',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_ADD','/api/perf/target-plans','POST','新增目标方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_GET','/api/perf/target-plans/*','GET','目标方案详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_LIST','/api/perf/target-plans','GET','目标方案列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_UPD','/api/perf/target-plans/*','PUT','编辑目标方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_V_ADD','/api/perf/target-values','POST','目标值upsert',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_V_BAT','/api/perf/target-values/batch','POST','目标值批量',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_V_LIST','/api/perf/target-values','GET','目标值查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('W_NC_CREATE','/api/admin/workflow/node-candidates','POST','新增候选人配置',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NC_GET','/api/admin/workflow/node-candidates/item/*','GET','候选人配置详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NC_LIST','/api/admin/workflow/node-candidates','GET','候选人配置列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NC_UPDATE','/api/admin/workflow/node-candidates/*','PUT','更新候选人配置',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_CREATE','/api/admin/workflow/node-forms','POST','新增节点表单',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_GET','/api/admin/workflow/node-forms/item/*','GET','节点表单详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_LIST','/api/admin/workflow/node-forms','GET','节点表单列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_UPDATE','/api/admin/workflow/node-forms/*','PUT','更新节点表单',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_DEFS','/api/admin/workflow/process-definitions','GET','流程定义列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_DETAIL','/api/workflow/processes/*','GET','流程实例详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_DIAGRAM','/api/workflow/processes/*/diagram','GET','流程进度图',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_HISTORY','/api/workflow/processes/*/history','GET','流程历史',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_MAP','/api/workflow/process-map','GET','流程映射查询',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_NODES','/api/workflow/processes/*/nodes','GET','流程节点结构',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_APPROVE','/api/workflow/tasks/*/approve','POST','审批通过',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_CLAIM','/api/workflow/tasks/*/claim','POST','签收任务',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_DETAIL','/api/workflow/tasks/*','GET','任务详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_DONE','/api/workflow/tasks/done','GET','已办任务列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_REJECT','/api/workflow/tasks/*/reject','POST','驳回任务',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_TODO','/api/workflow/tasks','GET','待办任务列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_TRANSFER','/api/workflow/tasks/*/transfer','POST','转交任务',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_CREATE','/api/admin/workflow/timeout-rules','POST','新增超时规则',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_GET','/api/admin/workflow/timeout-rules/item/*','GET','超时规则详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_LIST','/api/admin/workflow/timeout-rules','GET','超时规则列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_UPDATE','/api/admin/workflow/timeout-rules/*','PUT','更新超时规则',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10');
/*!40000 ALTER TABLE `pt_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_role`
--

DROP TABLE IF EXISTS `pt_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_role` (
  `ROLE_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `ROLE_CODE` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  `ROLE_CHNAME` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色中文名',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `SYS_CODE` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_role`
--

LOCK TABLES `pt_role` WRITE;
/*!40000 ALTER TABLE `pt_role` DISABLE KEYS */;
INSERT INTO `pt_role` VALUES ('R_77EBD269','R_TESTZ','retest-v2',1,'PLATFORM','2026-04-10 12:20:00',NULL,'2026-04-10 12:20:01',NULL,'upd'),('R_ADMIN','SYS_ADMIN','系统管理员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 超级管理员，运维与权限管理'),('R_BACK_FINANCE','BACK_FINAN','中后台员工(资财)',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 财务会计部等后台支持'),('R_BACK_TECH','BACK_TECH','中后台员工(科技)',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 信息技术部'),('R_BRANCH_MGR','BRANCH_HEA','经营机构负责人',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 支行/二级分行负责人'),('R_CORP_DEPT','CORP_DEPT','公司部人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 分行公司业务管理部门'),('R_CREDIT_APPROVER','CREDIT_APP','授信批复人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 授信批复岗'),('R_CREDIT_REVIEWER','CREDIT_REV','授信审查人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 授信审查岗'),('R_PRESIDENT','BRANCH_PRE','分行行长',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 分行最高管理者'),('R_RETAIL_DEPT','RETAIL_DEP','零售部人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 分行零售业务管理部门'),('R_RM','CUST_MANAG','客户经理',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 经营机构一线营销人员'),('R_SUPPORT_SEC','SUPPORT_SE','中场支持部门秘书',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 中场支持部门秘书岗'),('R_SUPPORT_STAFF','SUPPORT_ST','中场支持部门人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 中台部门员工');
/*!40000 ALTER TABLE `pt_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_role_biz_scope`
--

DROP TABLE IF EXISTS `pt_role_biz_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_role_biz_scope` (
  `ID` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `BIZ_TYPE` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务类型：NAV/PRODUCT/LEAD/CUSTOMER等',
  `DATA_SCOPE` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据范围：SELF_CREATED/SELF/SELF_ASSIGNED/ORG/ORG_SUBTREE/ALL/WORKFLOW_PARTICIPANT',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_pt_role_biz_scope_role_biz` (`ROLE_ID`,`BIZ_TYPE`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_biz_type` (`BIZ_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色业务范围表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_role_biz_scope`
--

LOCK TABLES `pt_role_biz_scope` WRITE;
/*!40000 ALTER TABLE `pt_role_biz_scope` DISABLE KEYS */;
INSERT INTO `pt_role_biz_scope` VALUES ('b6d4ea5b396211f1a12bc84d4421b4d8','R_BACK_TECH','PERF_CONFIG','ALL',0,'2026-04-16 15:05:56','seed','2026-04-16 15:05:56',NULL,'perf v1.0 - full access'),('d79564de349011f191754c496c37265b','R_BACK_FINANCE','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795651b349011f191754c496c37265b','R_BRANCH_MGR','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956528349011f191754c496c37265b','R_CORP_DEPT','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956536349011f191754c496c37265b','R_CREDIT_APPROVER','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956542349011f191754c496c37265b','R_CREDIT_REVIEWER','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795654e349011f191754c496c37265b','R_PRESIDENT','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795655b349011f191754c496c37265b','R_RETAIL_DEPT','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956566349011f191754c496c37265b','R_RM','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956574349011f191754c496c37265b','R_SUPPORT_SEC','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795657f349011f191754c496c37265b','R_SUPPORT_STAFF','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('da68abe1348b11f191754c496c37265b','R_ADMIN','ORG','ALL',0,'2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1'),('S_ADMIN_ADDRBOOK','R_ADMIN','ADDRBOOK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_CLAIM','R_ADMIN','CLAIM','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_CUSTOMER','R_ADMIN','CUSTOMER','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_CUSTOMER_POOL','R_ADMIN','CUSTOMER_POOL','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_DOC','R_ADMIN','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_LEAD','R_ADMIN','LEAD','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_LOAN','R_ADMIN','LOAN','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_NAV','R_ADMIN','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_PERF_CONFIG','R_ADMIN','PERF_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-16 15:05:56','seed','perf v1.0 - full access'),('S_ADMIN_PRODUCT','R_ADMIN','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_REPORT','R_ADMIN','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_SUPPORT','R_ADMIN','SUPPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_SUPPORT_DEPT','R_ADMIN','SUPPORT_DEPT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_SYS_CONFIG','R_ADMIN','SYS_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_TAG','R_ADMIN','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_TOUCH_REPORT','R_ADMIN','TOUCH_REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_TOUCH_TASK','R_ADMIN','TOUCH_TASK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BF_NAV','R_BACK_FINANCE','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BF_PERF_CONFIG','R_BACK_FINANCE','PERF_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BF_REPORT','R_BACK_FINANCE','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_ADDRBOOK','R_BRANCH_MGR','ADDRBOOK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_BM_CUSTOMER','R_BRANCH_MGR','CUSTOMER','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_DOC','R_BRANCH_MGR','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_BM_LEAD','R_BRANCH_MGR','LEAD','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_NAV','R_BRANCH_MGR','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_PRODUCT','R_BRANCH_MGR','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_BM_REPORT','R_BRANCH_MGR','REPORT','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_SUPPORT','R_BRANCH_MGR','SUPPORT','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_TOUCH_REPORT','R_BRANCH_MGR','TOUCH_REPORT','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BRANCH_PRE_TAG','R_PRESIDENT','TAG','ALL',0,NULL,NULL,NULL,NULL,NULL),('S_BT_DOC','R_BACK_TECH','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BT_NAV','R_BACK_TECH','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BT_SYS_CONFIG','R_BACK_TECH','SYS_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CAP_LOAN','R_CREDIT_APPROVER','LOAN','WORKFLOW_PARTICIPANT',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CAP_NAV','R_CREDIT_APPROVER','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_CUSTOMER','R_CORP_DEPT','CUSTOMER','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_LEAD','R_CORP_DEPT','LEAD','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_LOAN','R_CORP_DEPT','LOAN','WORKFLOW_PARTICIPANT',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_NAV','R_CORP_DEPT','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_REPORT','R_CORP_DEPT','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_TAG','R_CORP_DEPT','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_TOUCH_REPORT','R_CORP_DEPT','TOUCH_REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CRV_LOAN','R_CREDIT_REVIEWER','LOAN','WORKFLOW_PARTICIPANT',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CRV_NAV','R_CREDIT_REVIEWER','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_PR_NAV','R_PRESIDENT','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_PR_REPORT','R_PRESIDENT','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_CUSTOMER','R_RETAIL_DEPT','CUSTOMER','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_LEAD','R_RETAIL_DEPT','LEAD','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_LOAN','R_RETAIL_DEPT','LOAN','WORKFLOW_PARTICIPANT',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_NAV','R_RETAIL_DEPT','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_REPORT','R_RETAIL_DEPT','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_TAG','R_RETAIL_DEPT','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_TOUCH_REPORT','R_RETAIL_DEPT','TOUCH_REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_ADDRBOOK','R_RM','ADDRBOOK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_CLAIM','R_RM','CLAIM','ORG',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_CUSTOMER_POOL','R_RM','CUSTOMER_POOL','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_DOC','R_RM','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_LEAD','R_RM','LEAD','SELF_CREATED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_LOAN','R_RM','LOAN','SELF_CREATED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_NAV','R_RM','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_PRODUCT','R_RM','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_REPORT','R_RM','REPORT','SELF',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_SUPPORT','R_RM','SUPPORT','SELF_CREATED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_TAG','R_RM','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_TOUCH_TASK','R_RM','TOUCH_TASK','SELF_ASSIGNED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SF_NAV','R_SUPPORT_STAFF','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SF_SUPPORT_DEPT','R_SUPPORT_STAFF','SUPPORT_DEPT','SELF_ASSIGNED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SS_NAV','R_SUPPORT_SEC','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SS_PRODUCT','R_SUPPORT_SEC','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_SS_SUPPORT_DEPT','R_SUPPORT_SEC','SUPPORT_DEPT','ORG',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1');
/*!40000 ALTER TABLE `pt_role_biz_scope` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_role_resource`
--

DROP TABLE IF EXISTS `pt_role_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_role_resource` (
  `ID` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `RESOURCE_ID` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源ID',
  `SYS_CODE` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_resource_id` (`RESOURCE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色资源关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_role_resource`
--

LOCK TABLES `pt_role_resource` WRITE;
/*!40000 ALTER TABLE `pt_role_resource` DISABLE KEYS */;
INSERT INTO `pt_role_resource` VALUES ('da66408c348b11f191754c496c37265b','R_ADMIN','A_BZ_DELETE','AUTH','2026-04-10 11:17:49'),('da6647a9348b11f191754c496c37265b','R_ADMIN','A_BZ_LIST','AUTH','2026-04-10 11:17:49'),('da664882348b11f191754c496c37265b','R_ADMIN','A_BZ_MATRIX','AUTH','2026-04-10 11:17:49'),('da66491a348b11f191754c496c37265b','R_ADMIN','A_BZ_SAVE','AUTH','2026-04-10 11:17:49'),('da6649a7348b11f191754c496c37265b','R_ADMIN','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da664a37348b11f191754c496c37265b','R_ADMIN','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da664cd1348b11f191754c496c37265b','R_ADMIN','A_LOGIN','AUTH','2026-04-10 11:17:49'),('da664dbf348b11f191754c496c37265b','R_ADMIN','A_LOGOUT','AUTH','2026-04-10 11:17:49'),('da664e5a348b11f191754c496c37265b','R_ADMIN','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da664edc348b11f191754c496c37265b','R_ADMIN','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da664f56348b11f191754c496c37265b','R_ADMIN','A_ORG_USERS','AUTH','2026-04-10 11:17:49'),('da664fd2348b11f191754c496c37265b','R_ADMIN','A_PERMS','AUTH','2026-04-10 11:17:49'),('da665042348b11f191754c496c37265b','R_ADMIN','A_RES_CREATE','AUTH','2026-04-10 11:17:49'),('da6650b7348b11f191754c496c37265b','R_ADMIN','A_RES_DELETE','AUTH','2026-04-10 11:17:49'),('da66513b348b11f191754c496c37265b','R_ADMIN','A_RES_TREE','AUTH','2026-04-10 11:17:49'),('da6651ba348b11f191754c496c37265b','R_ADMIN','A_RES_UPDATE','AUTH','2026-04-10 11:17:49'),('da66522d348b11f191754c496c37265b','R_ADMIN','A_ROLE_CREATE','AUTH','2026-04-10 11:17:49'),('da6652aa348b11f191754c496c37265b','R_ADMIN','A_ROLE_DELETE','AUTH','2026-04-10 11:17:49'),('da66531f348b11f191754c496c37265b','R_ADMIN','A_ROLE_LIST','AUTH','2026-04-10 11:17:49'),('da665390348b11f191754c496c37265b','R_ADMIN','A_ROLE_UPDATE','AUTH','2026-04-10 11:17:49'),('da665402348b11f191754c496c37265b','R_ADMIN','A_ROLE_USERS','AUTH','2026-04-10 11:17:49'),('da665477348b11f191754c496c37265b','R_ADMIN','A_RR_BIND','AUTH','2026-04-10 11:17:49'),('da6654e9348b11f191754c496c37265b','R_ADMIN','A_RR_LIST','AUTH','2026-04-10 11:17:49'),('da66555a348b11f191754c496c37265b','R_ADMIN','A_RR_REPLACE','AUTH','2026-04-10 11:17:49'),('da6655cc348b11f191754c496c37265b','R_ADMIN','A_UR_BIND','AUTH','2026-04-10 11:17:49'),('da66563d348b11f191754c496c37265b','R_ADMIN','A_UR_DEL','AUTH','2026-04-10 11:17:49'),('da6656af348b11f191754c496c37265b','R_ADMIN','A_UR_LIST','AUTH','2026-04-10 11:17:49'),('da66571f348b11f191754c496c37265b','R_ADMIN','G_AUDIT_DETAIL','GOV','2026-04-10 11:17:49'),('da665794348b11f191754c496c37265b','R_ADMIN','G_AUDIT_EXPORT','GOV','2026-04-10 11:17:49'),('da66580a348b11f191754c496c37265b','R_ADMIN','G_AUDIT_LIST','GOV','2026-04-10 11:17:49'),('da665881348b11f191754c496c37265b','R_ADMIN','G_CAL_GET','GOV','2026-04-10 11:17:49'),('da6658f3348b11f191754c496c37265b','R_ADMIN','G_CAL_IMPORT','GOV','2026-04-10 11:17:49'),('da665963348b11f191754c496c37265b','R_ADMIN','G_CAL_INIT','GOV','2026-04-10 11:17:49'),('da6659e1348b11f191754c496c37265b','R_ADMIN','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da665a53348b11f191754c496c37265b','R_ADMIN','G_CAL_SET','GOV','2026-04-10 11:17:49'),('da665ac6348b11f191754c496c37265b','R_ADMIN','G_CFG_LIST','GOV','2026-04-10 11:17:49'),('da665b3a348b11f191754c496c37265b','R_ADMIN','G_CFG_UPDATE','GOV','2026-04-10 11:17:49'),('da665bae348b11f191754c496c37265b','R_ADMIN','G_DICT_CREATE','GOV','2026-04-10 11:17:49'),('da665c20348b11f191754c496c37265b','R_ADMIN','G_DICT_DELETE','GOV','2026-04-10 11:17:49'),('da665c94348b11f191754c496c37265b','R_ADMIN','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da665d06348b11f191754c496c37265b','R_ADMIN','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da665d7b348b11f191754c496c37265b','R_ADMIN','G_DICT_STATUS','GOV','2026-04-10 11:17:49'),('da665deb348b11f191754c496c37265b','R_ADMIN','G_DICT_UPDATE','GOV','2026-04-10 11:17:49'),('da665e5d348b11f191754c496c37265b','R_ADMIN','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da665ed0348b11f191754c496c37265b','R_ADMIN','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da665f49348b11f191754c496c37265b','R_ADMIN','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da665fb7348b11f191754c496c37265b','R_ADMIN','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da666029348b11f191754c496c37265b','R_ADMIN','G_JOB_LIST','GOV','2026-04-10 11:17:49'),('da666098348b11f191754c496c37265b','R_ADMIN','G_JOB_LOGS','GOV','2026-04-10 11:17:49'),('da66614a348b11f191754c496c37265b','R_ADMIN','G_JOB_PAUSE','GOV','2026-04-10 11:17:49'),('da6661c0348b11f191754c496c37265b','R_ADMIN','G_JOB_RESUME','GOV','2026-04-10 11:17:49'),('da666235348b11f191754c496c37265b','R_ADMIN','G_JOB_TRIGGER','GOV','2026-04-10 11:17:49'),('da6662ae348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da666321348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da666393348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da666406348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da66647c348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da6664f0348b11f191754c496c37265b','R_ADMIN','G_SQL_EXEC','GOV','2026-04-10 11:17:49'),('da666564348b11f191754c496c37265b','R_ADMIN','G_SQL_HIST','GOV','2026-04-10 11:17:49'),('da667bec348b11f191754c496c37265b','R_ADMIN','W_NC_CREATE','WF','2026-04-10 11:17:49'),('da667d1d348b11f191754c496c37265b','R_ADMIN','W_NC_GET','WF','2026-04-10 11:17:49'),('da667e25348b11f191754c496c37265b','R_ADMIN','W_NC_LIST','WF','2026-04-10 11:17:49'),('da667f1d348b11f191754c496c37265b','R_ADMIN','W_NC_UPDATE','WF','2026-04-10 11:17:49'),('da66800c348b11f191754c496c37265b','R_ADMIN','W_NF_CREATE','WF','2026-04-10 11:17:49'),('da6680fa348b11f191754c496c37265b','R_ADMIN','W_NF_GET','WF','2026-04-10 11:17:49'),('da668201348b11f191754c496c37265b','R_ADMIN','W_NF_LIST','WF','2026-04-10 11:17:49'),('da6682ee348b11f191754c496c37265b','R_ADMIN','W_NF_UPDATE','WF','2026-04-10 11:17:49'),('da6683d5348b11f191754c496c37265b','R_ADMIN','W_PROC_DEFS','WF','2026-04-10 11:17:49'),('da6684b9348b11f191754c496c37265b','R_ADMIN','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6685d0348b11f191754c496c37265b','R_ADMIN','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6686bb348b11f191754c496c37265b','R_ADMIN','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6689a4348b11f191754c496c37265b','R_ADMIN','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da668aa0348b11f191754c496c37265b','R_ADMIN','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da668b96348b11f191754c496c37265b','R_ADMIN','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da668c88348b11f191754c496c37265b','R_ADMIN','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da668d7b348b11f191754c496c37265b','R_ADMIN','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da668e63348b11f191754c496c37265b','R_ADMIN','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da66900a348b11f191754c496c37265b','R_ADMIN','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da66910d348b11f191754c496c37265b','R_ADMIN','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6691fe348b11f191754c496c37265b','R_ADMIN','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6692d7348b11f191754c496c37265b','R_ADMIN','W_TR_CREATE','WF','2026-04-10 11:17:49'),('da6693b7348b11f191754c496c37265b','R_ADMIN','W_TR_GET','WF','2026-04-10 11:17:49'),('da66948b348b11f191754c496c37265b','R_ADMIN','W_TR_LIST','WF','2026-04-10 11:17:49'),('da669562348b11f191754c496c37265b','R_ADMIN','W_TR_UPDATE','WF','2026-04-10 11:17:49'),('da66a7f6348b11f191754c496c37265b','R_BACK_TECH','A_BZ_DELETE','AUTH','2026-04-10 11:17:49'),('da66a98a348b11f191754c496c37265b','R_BACK_TECH','A_BZ_LIST','AUTH','2026-04-10 11:17:49'),('da66aa31348b11f191754c496c37265b','R_BACK_TECH','A_BZ_MATRIX','AUTH','2026-04-10 11:17:49'),('da66aac9348b11f191754c496c37265b','R_BACK_TECH','A_BZ_SAVE','AUTH','2026-04-10 11:17:49'),('da66ab45348b11f191754c496c37265b','R_BACK_TECH','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da66abbd348b11f191754c496c37265b','R_BACK_TECH','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da66ac2e348b11f191754c496c37265b','R_BACK_TECH','A_LOGIN','AUTH','2026-04-10 11:17:49'),('da66ac9e348b11f191754c496c37265b','R_BACK_TECH','A_LOGOUT','AUTH','2026-04-10 11:17:49'),('da66ad21348b11f191754c496c37265b','R_BACK_TECH','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da66c7b1348b11f191754c496c37265b','R_BACK_TECH','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da66c87c348b11f191754c496c37265b','R_BACK_TECH','A_ORG_USERS','AUTH','2026-04-10 11:17:49'),('da66c903348b11f191754c496c37265b','R_BACK_TECH','A_PERMS','AUTH','2026-04-10 11:17:49'),('da66c97a348b11f191754c496c37265b','R_BACK_TECH','A_RES_CREATE','AUTH','2026-04-10 11:17:49'),('da66c9f2348b11f191754c496c37265b','R_BACK_TECH','A_RES_DELETE','AUTH','2026-04-10 11:17:49'),('da66ca63348b11f191754c496c37265b','R_BACK_TECH','A_RES_TREE','AUTH','2026-04-10 11:17:49'),('da66cad4348b11f191754c496c37265b','R_BACK_TECH','A_RES_UPDATE','AUTH','2026-04-10 11:17:49'),('da66cb4e348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_CREATE','AUTH','2026-04-10 11:17:49'),('da66cbbd348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_DELETE','AUTH','2026-04-10 11:17:49'),('da66cc30348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_LIST','AUTH','2026-04-10 11:17:49'),('da66cca1348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_UPDATE','AUTH','2026-04-10 11:17:49'),('da66cd0e348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_USERS','AUTH','2026-04-10 11:17:49'),('da66cd97348b11f191754c496c37265b','R_BACK_TECH','A_RR_BIND','AUTH','2026-04-10 11:17:49'),('da66ce09348b11f191754c496c37265b','R_BACK_TECH','A_RR_LIST','AUTH','2026-04-10 11:17:49'),('da66ce7e348b11f191754c496c37265b','R_BACK_TECH','A_RR_REPLACE','AUTH','2026-04-10 11:17:49'),('da66ceeb348b11f191754c496c37265b','R_BACK_TECH','A_UR_BIND','AUTH','2026-04-10 11:17:49'),('da66cf57348b11f191754c496c37265b','R_BACK_TECH','A_UR_DEL','AUTH','2026-04-10 11:17:49'),('da66cfc2348b11f191754c496c37265b','R_BACK_TECH','A_UR_LIST','AUTH','2026-04-10 11:17:49'),('da66d035348b11f191754c496c37265b','R_BACK_TECH','G_AUDIT_DETAIL','GOV','2026-04-10 11:17:49'),('da66d0a9348b11f191754c496c37265b','R_BACK_TECH','G_AUDIT_EXPORT','GOV','2026-04-10 11:17:49'),('da66d1c9348b11f191754c496c37265b','R_BACK_TECH','G_AUDIT_LIST','GOV','2026-04-10 11:17:49'),('da66d240348b11f191754c496c37265b','R_BACK_TECH','G_CAL_GET','GOV','2026-04-10 11:17:49'),('da66d2c3348b11f191754c496c37265b','R_BACK_TECH','G_CAL_IMPORT','GOV','2026-04-10 11:17:49'),('da66d337348b11f191754c496c37265b','R_BACK_TECH','G_CAL_INIT','GOV','2026-04-10 11:17:49'),('da66d3a9348b11f191754c496c37265b','R_BACK_TECH','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da66d419348b11f191754c496c37265b','R_BACK_TECH','G_CAL_SET','GOV','2026-04-10 11:17:49'),('da66d48a348b11f191754c496c37265b','R_BACK_TECH','G_CFG_LIST','GOV','2026-04-10 11:17:49'),('da66d4f6348b11f191754c496c37265b','R_BACK_TECH','G_CFG_UPDATE','GOV','2026-04-10 11:17:49'),('da66d564348b11f191754c496c37265b','R_BACK_TECH','G_DICT_CREATE','GOV','2026-04-10 11:17:49'),('da66d5d2348b11f191754c496c37265b','R_BACK_TECH','G_DICT_DELETE','GOV','2026-04-10 11:17:49'),('da66d64b348b11f191754c496c37265b','R_BACK_TECH','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da66d6ba348b11f191754c496c37265b','R_BACK_TECH','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da66d728348b11f191754c496c37265b','R_BACK_TECH','G_DICT_STATUS','GOV','2026-04-10 11:17:49'),('da66d798348b11f191754c496c37265b','R_BACK_TECH','G_DICT_UPDATE','GOV','2026-04-10 11:17:49'),('da66d80b348b11f191754c496c37265b','R_BACK_TECH','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da66d878348b11f191754c496c37265b','R_BACK_TECH','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da66d8eb348b11f191754c496c37265b','R_BACK_TECH','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da66d962348b11f191754c496c37265b','R_BACK_TECH','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da66d9d5348b11f191754c496c37265b','R_BACK_TECH','G_JOB_LIST','GOV','2026-04-10 11:17:49'),('da66da44348b11f191754c496c37265b','R_BACK_TECH','G_JOB_LOGS','GOV','2026-04-10 11:17:49'),('da66ec72348b11f191754c496c37265b','R_BACK_TECH','G_JOB_PAUSE','GOV','2026-04-10 11:17:49'),('da66ed28348b11f191754c496c37265b','R_BACK_TECH','G_JOB_RESUME','GOV','2026-04-10 11:17:49'),('da66ed9b348b11f191754c496c37265b','R_BACK_TECH','G_JOB_TRIGGER','GOV','2026-04-10 11:17:49'),('da66ee05348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da66ee78348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da66eee3348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da66ef52348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da66efb8348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da66f021348b11f191754c496c37265b','R_BACK_TECH','G_SQL_EXEC','GOV','2026-04-10 11:17:49'),('da66f091348b11f191754c496c37265b','R_BACK_TECH','G_SQL_HIST','GOV','2026-04-10 11:17:49'),('da66fc3f348b11f191754c496c37265b','R_BACK_TECH','W_NC_CREATE','WF','2026-04-10 11:17:49'),('da66fcc9348b11f191754c496c37265b','R_BACK_TECH','W_NC_GET','WF','2026-04-10 11:17:49'),('da66fd4c348b11f191754c496c37265b','R_BACK_TECH','W_NC_LIST','WF','2026-04-10 11:17:49'),('da66fdd2348b11f191754c496c37265b','R_BACK_TECH','W_NC_UPDATE','WF','2026-04-10 11:17:49'),('da66fe42348b11f191754c496c37265b','R_BACK_TECH','W_NF_CREATE','WF','2026-04-10 11:17:49'),('da66feb0348b11f191754c496c37265b','R_BACK_TECH','W_NF_GET','WF','2026-04-10 11:17:49'),('da66ff1f348b11f191754c496c37265b','R_BACK_TECH','W_NF_LIST','WF','2026-04-10 11:17:49'),('da66ff8d348b11f191754c496c37265b','R_BACK_TECH','W_NF_UPDATE','WF','2026-04-10 11:17:49'),('da66fffa348b11f191754c496c37265b','R_BACK_TECH','W_PROC_DEFS','WF','2026-04-10 11:17:49'),('da670fc2348b11f191754c496c37265b','R_BACK_TECH','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da67106f348b11f191754c496c37265b','R_BACK_TECH','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6710e6348b11f191754c496c37265b','R_BACK_TECH','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da67115e348b11f191754c496c37265b','R_BACK_TECH','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6711d2348b11f191754c496c37265b','R_BACK_TECH','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da671240348b11f191754c496c37265b','R_BACK_TECH','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da6712b1348b11f191754c496c37265b','R_BACK_TECH','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da671322348b11f191754c496c37265b','R_BACK_TECH','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da671392348b11f191754c496c37265b','R_BACK_TECH','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da671400348b11f191754c496c37265b','R_BACK_TECH','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da67146a348b11f191754c496c37265b','R_BACK_TECH','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6714dc348b11f191754c496c37265b','R_BACK_TECH','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da671548348b11f191754c496c37265b','R_BACK_TECH','W_TR_CREATE','WF','2026-04-10 11:17:49'),('da672995348b11f191754c496c37265b','R_BACK_TECH','W_TR_GET','WF','2026-04-10 11:17:49'),('da672a92348b11f191754c496c37265b','R_BACK_TECH','W_TR_LIST','WF','2026-04-10 11:17:49'),('da672b05348b11f191754c496c37265b','R_BACK_TECH','W_TR_UPDATE','WF','2026-04-10 11:17:49'),('da674945348b11f191754c496c37265b','R_SUPPORT_STAFF','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674a92348b11f191754c496c37265b','R_SUPPORT_SEC','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674b10348b11f191754c496c37265b','R_RM','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674b7e348b11f191754c496c37265b','R_RETAIL_DEPT','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674be2348b11f191754c496c37265b','R_PRESIDENT','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674c4b348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674cab348b11f191754c496c37265b','R_CREDIT_APPROVER','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674d07348b11f191754c496c37265b','R_CORP_DEPT','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674d63348b11f191754c496c37265b','R_BRANCH_MGR','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674df5348b11f191754c496c37265b','R_BACK_FINANCE','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da676905348b11f191754c496c37265b','R_SUPPORT_STAFF','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da6769aa348b11f191754c496c37265b','R_SUPPORT_SEC','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677844348b11f191754c496c37265b','R_RM','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da6778e8348b11f191754c496c37265b','R_RETAIL_DEPT','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677957348b11f191754c496c37265b','R_PRESIDENT','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da6779cf348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677aad348b11f191754c496c37265b','R_CREDIT_APPROVER','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677b1a348b11f191754c496c37265b','R_CORP_DEPT','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677b83348b11f191754c496c37265b','R_BRANCH_MGR','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677beb348b11f191754c496c37265b','R_BACK_FINANCE','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677cc8348b11f191754c496c37265b','R_SUPPORT_STAFF','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da677d3f348b11f191754c496c37265b','R_SUPPORT_SEC','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da677eae348b11f191754c496c37265b','R_RM','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da677f91348b11f191754c496c37265b','R_RETAIL_DEPT','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da678000348b11f191754c496c37265b','R_PRESIDENT','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6780f5348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6781cf348b11f191754c496c37265b','R_CREDIT_APPROVER','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da67823d348b11f191754c496c37265b','R_CORP_DEPT','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6782a3348b11f191754c496c37265b','R_BRANCH_MGR','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da678306348b11f191754c496c37265b','R_BACK_FINANCE','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6783c9348b11f191754c496c37265b','R_SUPPORT_STAFF','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678445348b11f191754c496c37265b','R_SUPPORT_SEC','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da67853b348b11f191754c496c37265b','R_RM','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da6785aa348b11f191754c496c37265b','R_RETAIL_DEPT','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678618348b11f191754c496c37265b','R_PRESIDENT','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678685348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da6786e7348b11f191754c496c37265b','R_CREDIT_APPROVER','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678747348b11f191754c496c37265b','R_CORP_DEPT','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da6787a8348b11f191754c496c37265b','R_BRANCH_MGR','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678807348b11f191754c496c37265b','R_BACK_FINANCE','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678898348b11f191754c496c37265b','R_SUPPORT_STAFF','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678900348b11f191754c496c37265b','R_SUPPORT_SEC','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678963348b11f191754c496c37265b','R_RM','A_PERMS','AUTH','2026-04-10 11:17:49'),('da6789c3348b11f191754c496c37265b','R_RETAIL_DEPT','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678a23348b11f191754c496c37265b','R_PRESIDENT','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678a91348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678af5348b11f191754c496c37265b','R_CREDIT_APPROVER','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678b56348b11f191754c496c37265b','R_CORP_DEPT','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678bb8348b11f191754c496c37265b','R_BRANCH_MGR','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678c19348b11f191754c496c37265b','R_BACK_FINANCE','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678dd9348b11f191754c496c37265b','R_SUPPORT_STAFF','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da678e47348b11f191754c496c37265b','R_SUPPORT_SEC','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da679014348b11f191754c496c37265b','R_RM','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da6790ac348b11f191754c496c37265b','R_RETAIL_DEPT','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da679119348b11f191754c496c37265b','R_PRESIDENT','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da6791a7348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da67920f348b11f191754c496c37265b','R_CREDIT_APPROVER','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da67926b348b11f191754c496c37265b','R_CORP_DEPT','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da6792c5348b11f191754c496c37265b','R_BRANCH_MGR','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da679323348b11f191754c496c37265b','R_BACK_FINANCE','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da67943a348b11f191754c496c37265b','R_SUPPORT_STAFF','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6794a4348b11f191754c496c37265b','R_SUPPORT_SEC','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da679503348b11f191754c496c37265b','R_RM','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da67955c348b11f191754c496c37265b','R_RETAIL_DEPT','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6795b5348b11f191754c496c37265b','R_PRESIDENT','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da67960f348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da679668348b11f191754c496c37265b','R_CREDIT_APPROVER','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6796c5348b11f191754c496c37265b','R_CORP_DEPT','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da67971f348b11f191754c496c37265b','R_BRANCH_MGR','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da679777348b11f191754c496c37265b','R_BACK_FINANCE','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6797e1348b11f191754c496c37265b','R_SUPPORT_STAFF','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679841348b11f191754c496c37265b','R_SUPPORT_SEC','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da67989b348b11f191754c496c37265b','R_RM','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679d0f348b11f191754c496c37265b','R_RETAIL_DEPT','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679e00348b11f191754c496c37265b','R_PRESIDENT','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679e6f348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679ed6348b11f191754c496c37265b','R_CREDIT_APPROVER','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679f38348b11f191754c496c37265b','R_CORP_DEPT','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679f9b348b11f191754c496c37265b','R_BRANCH_MGR','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679ffc348b11f191754c496c37265b','R_BACK_FINANCE','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da67a0c2348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a274348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a33f348b11f191754c496c37265b','R_RM','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a3a6348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a405348b11f191754c496c37265b','R_PRESIDENT','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a466348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a4c4348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a522348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a57f348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a5f1348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a69c348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a701348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a75c348b11f191754c496c37265b','R_RM','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a7b7348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a813348b11f191754c496c37265b','R_PRESIDENT','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a86c348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a8c8348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a92f348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a989348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67abd4348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67ad89348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67ae34348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67aeb7348b11f191754c496c37265b','R_RM','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67af41348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67afad348b11f191754c496c37265b','R_PRESIDENT','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b009348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b064348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b0bf348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b117348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b170348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b1e9348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b24e348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b2ab348b11f191754c496c37265b','R_RM','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b305348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b35f348b11f191754c496c37265b','R_PRESIDENT','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b3b9348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b412348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b475348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b517348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b57b348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b63a348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b69d348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b6f9348b11f191754c496c37265b','R_RM','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b754348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b7af348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b80b348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b867348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b8d2348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b931348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b997348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67ba04348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67ba65348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bac4348b11f191754c496c37265b','R_RM','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bb20348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bb79348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bbd2348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67ce5f348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67cf1d348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67cf8c348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67cff8348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67d090348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e542348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e5de348b11f191754c496c37265b','R_RM','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e641348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e698348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e7b7348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e81b348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e877348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e8cf348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e924348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e9b1348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ea0c348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ea63348b11f191754c496c37265b','R_RM','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67eabe348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67eb16348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67eb6b348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ebc1348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ec16348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ec6e348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ecc4348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ed2d348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ed94348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67edec348b11f191754c496c37265b','R_RM','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ee43348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ee9c348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67eef2348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ef48348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ef9e348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67eff3348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67f04b348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ff1c348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6801d5348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680241348b11f191754c496c37265b','R_RM','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da68030d348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680367348b11f191754c496c37265b','R_PRESIDENT','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6803c7348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680423348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680479348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6804cf348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da68052c348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680599348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6805f7348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da681f17348b11f191754c496c37265b','R_RM','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da681fbd348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da682020348b11f191754c496c37265b','R_PRESIDENT','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da68207e348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6820dd348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da682137348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da68218f348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6821e9348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da682284348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6822f6348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da682351348b11f191754c496c37265b','R_RM','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6823a8348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6823fc348b11f191754c496c37265b','R_PRESIDENT','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da682456348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6824ab348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6824ff348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da682559348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6825b0348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da68261a348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682677348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6826e6348b11f191754c496c37265b','R_RM','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da68273f348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6827ab348b11f191754c496c37265b','R_PRESIDENT','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682804348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da68285b348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6828f0348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682947348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6829a4348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682a13348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682a71348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682ace348b11f191754c496c37265b','R_RM','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682b28348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682b7d348b11f191754c496c37265b','R_PRESIDENT','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682bd4348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682c29348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682c80348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682cd7348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da683b92348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da683c6e348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683cd7348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683d31348b11f191754c496c37265b','R_RM','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683d8c348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683de4348b11f191754c496c37265b','R_PRESIDENT','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683e3d348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683e94348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683eed348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683f48348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683fa4348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da68400b348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684066348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6840bc348b11f191754c496c37265b','R_RM','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da68414b348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6841a4348b11f191754c496c37265b','R_PRESIDENT','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6841fd348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684252348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6842a9348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684300348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684357348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6843be348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684419348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684470348b11f191754c496c37265b','R_RM','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da6844c5348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da68451b348b11f191754c496c37265b','R_PRESIDENT','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684572348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da6845c7348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da68461e348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684676348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da6846d0348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684738348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da68479b348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6847f3348b11f191754c496c37265b','R_RM','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da68484a348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6848a1348b11f191754c496c37265b','R_PRESIDENT','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6848f9348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da68494f348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6849a6348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6849fb348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da684a53348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da684ac1348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684b1e348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684b7f348b11f191754c496c37265b','R_RM','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684bd8348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684c2f348b11f191754c496c37265b','R_PRESIDENT','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684c8a348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684ce0348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684d3a348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684d91348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684dea348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684e5b348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684eb8348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684f0f348b11f191754c496c37265b','R_RM','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684f64348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684fb9348b11f191754c496c37265b','R_PRESIDENT','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da68500e348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da685063348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6850bf348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da685117348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da68516b348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6851d9348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685235348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da68528c348b11f191754c496c37265b','R_RM','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6852e1348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685339348b11f191754c496c37265b','R_PRESIDENT','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685391348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6853e6348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da68543a348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685490348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6854e7348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('R_ADMIN_P_PERF_ALLOC_CUR','R_ADMIN','P_PERF_ALLOC_CUR','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_ALLOC_HIS','R_ADMIN','P_PERF_ALLOC_HIS','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_ALLOC_SUM','R_ADMIN','P_PERF_ALLOC_SUM','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_ADD','R_ADMIN','P_PERF_KPI_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_DEL','R_ADMIN','P_PERF_KPI_DEL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_GET','R_ADMIN','P_PERF_KPI_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_IADD','R_ADMIN','P_PERF_KPI_IADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_IDEL','R_ADMIN','P_PERF_KPI_IDEL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_IUPD','R_ADMIN','P_PERF_KPI_IUPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_LIST','R_ADMIN','P_PERF_KPI_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_PUB','R_ADMIN','P_PERF_KPI_PUB','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_UPD','R_ADMIN','P_PERF_KPI_UPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_ADD','R_ADMIN','P_PERF_METRIC_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_DEL','R_ADMIN','P_PERF_METRIC_DEL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_GET','R_ADMIN','P_PERF_METRIC_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_LIST','R_ADMIN','P_PERF_METRIC_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_RBY','R_ADMIN','P_PERF_METRIC_RBY','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_REFS','R_ADMIN','P_PERF_METRIC_REFS','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_SLOT','R_ADMIN','P_PERF_METRIC_SLOT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_SREL','R_ADMIN','P_PERF_METRIC_SREL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_STAT','R_ADMIN','P_PERF_METRIC_STAT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_UPD','R_ADMIN','P_PERF_METRIC_UPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_RT_GET','R_ADMIN','P_PERF_RT_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_RT_LIST','R_ADMIN','P_PERF_RT_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_GET','R_ADMIN','P_PERF_SC_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_HIS','R_ADMIN','P_PERF_SC_HIS','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_INIT','R_ADMIN','P_PERF_SC_INIT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_SW','R_ADMIN','P_PERF_SC_SW','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_ADD','R_ADMIN','P_PERF_TGT_P_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_GET','R_ADMIN','P_PERF_TGT_P_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_LIST','R_ADMIN','P_PERF_TGT_P_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_UPD','R_ADMIN','P_PERF_TGT_P_UPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_V_ADD','R_ADMIN','P_PERF_TGT_V_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_V_BAT','R_ADMIN','P_PERF_TGT_V_BAT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_V_LIST','R_ADMIN','P_PERF_TGT_V_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_ALLOC_CUR','R_BACK_TECH','P_PERF_ALLOC_CUR','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_ALLOC_HIS','R_BACK_TECH','P_PERF_ALLOC_HIS','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_ALLOC_SUM','R_BACK_TECH','P_PERF_ALLOC_SUM','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_ADD','R_BACK_TECH','P_PERF_KPI_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_DEL','R_BACK_TECH','P_PERF_KPI_DEL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_GET','R_BACK_TECH','P_PERF_KPI_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_IADD','R_BACK_TECH','P_PERF_KPI_IADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_IDEL','R_BACK_TECH','P_PERF_KPI_IDEL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_IUPD','R_BACK_TECH','P_PERF_KPI_IUPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_LIST','R_BACK_TECH','P_PERF_KPI_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_PUB','R_BACK_TECH','P_PERF_KPI_PUB','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_UPD','R_BACK_TECH','P_PERF_KPI_UPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_ADD','R_BACK_TECH','P_PERF_METRIC_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_DEL','R_BACK_TECH','P_PERF_METRIC_DEL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_GET','R_BACK_TECH','P_PERF_METRIC_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_LIST','R_BACK_TECH','P_PERF_METRIC_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_RBY','R_BACK_TECH','P_PERF_METRIC_RBY','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_REFS','R_BACK_TECH','P_PERF_METRIC_REFS','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_SLOT','R_BACK_TECH','P_PERF_METRIC_SLOT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_SREL','R_BACK_TECH','P_PERF_METRIC_SREL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_STAT','R_BACK_TECH','P_PERF_METRIC_STAT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_UPD','R_BACK_TECH','P_PERF_METRIC_UPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_RT_GET','R_BACK_TECH','P_PERF_RT_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_RT_LIST','R_BACK_TECH','P_PERF_RT_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_GET','R_BACK_TECH','P_PERF_SC_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_HIS','R_BACK_TECH','P_PERF_SC_HIS','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_INIT','R_BACK_TECH','P_PERF_SC_INIT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_SW','R_BACK_TECH','P_PERF_SC_SW','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_ADD','R_BACK_TECH','P_PERF_TGT_P_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_GET','R_BACK_TECH','P_PERF_TGT_P_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_LIST','R_BACK_TECH','P_PERF_TGT_P_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_UPD','R_BACK_TECH','P_PERF_TGT_P_UPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_V_ADD','R_BACK_TECH','P_PERF_TGT_V_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_V_BAT','R_BACK_TECH','P_PERF_TGT_V_BAT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_V_LIST','R_BACK_TECH','P_PERF_TGT_V_LIST','PERF','2026-04-16 15:05:56');
/*!40000 ALTER TABLE `pt_role_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_user`
--

DROP TABLE IF EXISTS `pt_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_user` (
  `USER_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID（工号）',
  `USERNAME` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户姓名',
  `USERCHNNAME` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户中文姓名',
  `PWD` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码（加密）',
  `EMAIL` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `ISEXPIRED` int DEFAULT '0' COMMENT '1 过期 0 未过期',
  `ISLOCKED` int DEFAULT '0' COMMENT '1 被锁 0 未被锁',
  `PASS_WRONG_COUNT` int DEFAULT '0' COMMENT '密码错误次数',
  `ISENABLED` int DEFAULT '1' COMMENT '0 启用 1 未启用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_AUTHOR` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建者',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_AUTHOR` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新者',
  `REMARK` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `PWD_UPDATE_TIME` datetime DEFAULT NULL COMMENT '密码更新时间',
  PRIMARY KEY (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='人员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_user`
--

LOCK TABLES `pt_user` WRITE;
/*!40000 ALTER TABLE `pt_user` DISABLE KEYS */;
INSERT INTO `pt_user` VALUES ('admin','admin','系统管理员','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m','admin@test.com',0,0,0,0,'2026-04-07 15:49:20',NULL,'2026-04-10 11:25:23',NULL,NULL,NULL),('E10002','rm_li','李四(客户经理)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E20001','branch_wang','王五(支行行长)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E30001','corp_zhao','赵六(公司部)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E30002','retail_sun','孙七(零售部)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E40001','finance_zhou','周八(资财)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E40002','tech_wu','吴九(科技)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E50001','sec_zheng','郑十(中场秘书)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E50002','staff_qian','钱十一(中场人员)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E60001','reviewer_chen','陈十二(授信审查)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E60002','approver_he','何十三(授信批复)','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('user001','user001','张三','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m','zhangsan@test.com',0,0,0,0,'2026-04-07 15:49:20',NULL,'2026-04-10 11:25:23',NULL,NULL,NULL),('user002','user002','李四','$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m','lisi@test.com',0,0,0,0,'2026-04-07 15:49:20',NULL,'2026-04-10 11:25:23',NULL,NULL,NULL);
/*!40000 ALTER TABLE `pt_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_user_role`
--

DROP TABLE IF EXISTS `pt_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_user_role` (
  `USER_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `ROLE_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `DEFAULT_ASSIGN` int DEFAULT '0' COMMENT '默认分配',
  `INHERIT_ASSIGN` int DEFAULT '0' COMMENT '用户组角色继承',
  `GROUP_ASSING` int DEFAULT '0' COMMENT '角色组分配',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ROLE_ID`),
  KEY `idx_role_id` (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_user_role`
--

LOCK TABLES `pt_user_role` WRITE;
/*!40000 ALTER TABLE `pt_user_role` DISABLE KEYS */;
INSERT INTO `pt_user_role` VALUES ('admin','R_ADMIN',1,0,0,'2026-04-07 19:20:37'),('E10002','R_RM',0,0,0,'2026-04-10 11:17:49'),('E20001','R_BRANCH_MGR',0,0,0,'2026-04-10 11:17:49'),('E30001','R_CORP_DEPT',0,0,0,'2026-04-10 11:17:49'),('E30002','R_RETAIL_DEPT',0,0,0,'2026-04-10 11:17:49'),('E40001','R_BACK_FINANCE',0,0,0,'2026-04-10 11:17:49'),('E40002','R_BACK_TECH',0,0,0,'2026-04-10 11:17:49'),('E50001','R_SUPPORT_SEC',0,0,0,'2026-04-10 11:17:49'),('E50002','R_SUPPORT_STAFF',0,0,0,'2026-04-10 11:17:49'),('E60001','R_CREDIT_REVIEWER',0,0,0,'2026-04-10 11:17:49'),('E60002','R_CREDIT_APPROVER',0,0,0,'2026-04-10 11:17:49'),('user001','R_RM',1,0,0,'2026-04-07 19:20:37'),('user002','R_PRESIDENT',1,0,0,'2026-04-07 19:20:37');
/*!40000 ALTER TABLE `pt_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_blob_triggers`
--

DROP TABLE IF EXISTS `qrtz_blob_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_blob_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(200) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  `BLOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz Blob 触发器（本项目暂不用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_blob_triggers`
--

LOCK TABLES `qrtz_blob_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_blob_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_blob_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_calendars`
--

DROP TABLE IF EXISTS `qrtz_calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_calendars` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `CALENDAR_NAME` varchar(200) NOT NULL,
  `CALENDAR` blob NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`CALENDAR_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 业务日历';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_calendars`
--

LOCK TABLES `qrtz_calendars` WRITE;
/*!40000 ALTER TABLE `qrtz_calendars` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_calendars` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_cron_triggers`
--

DROP TABLE IF EXISTS `qrtz_cron_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_cron_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(200) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  `CRON_EXPRESSION` varchar(120) NOT NULL,
  `TIME_ZONE_ID` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz Cron 触发器扩展';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_cron_triggers`
--

LOCK TABLES `qrtz_cron_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_cron_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_cron_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_fired_triggers`
--

DROP TABLE IF EXISTS `qrtz_fired_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_fired_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `ENTRY_ID` varchar(95) NOT NULL,
  `TRIGGER_NAME` varchar(200) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  `INSTANCE_NAME` varchar(200) NOT NULL,
  `FIRED_TIME` bigint NOT NULL,
  `SCHED_TIME` bigint NOT NULL,
  `PRIORITY` int NOT NULL,
  `STATE` varchar(16) NOT NULL,
  `JOB_NAME` varchar(200) DEFAULT NULL,
  `JOB_GROUP` varchar(200) DEFAULT NULL,
  `IS_NONCONCURRENT` varchar(1) DEFAULT NULL,
  `REQUESTS_RECOVERY` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`ENTRY_ID`),
  KEY `IDX_QRTZ_FT_TRIG_INST_NAME` (`SCHED_NAME`,`INSTANCE_NAME`),
  KEY `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY` (`SCHED_NAME`,`INSTANCE_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_FT_J_G` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_T_G` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_TG` (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 已触发触发器（执行中状态）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_fired_triggers`
--

LOCK TABLES `qrtz_fired_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_fired_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_fired_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_job_details`
--

DROP TABLE IF EXISTS `qrtz_job_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_job_details` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `JOB_NAME` varchar(200) NOT NULL,
  `JOB_GROUP` varchar(200) NOT NULL,
  `DESCRIPTION` varchar(250) DEFAULT NULL,
  `JOB_CLASS_NAME` varchar(250) NOT NULL,
  `IS_DURABLE` varchar(1) NOT NULL,
  `IS_NONCONCURRENT` varchar(1) NOT NULL,
  `IS_UPDATE_DATA` varchar(1) NOT NULL,
  `REQUESTS_RECOVERY` varchar(1) NOT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_J_REQ_RECOVERY` (`SCHED_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_J_GRP` (`SCHED_NAME`,`JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz Job 元信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_job_details`
--

LOCK TABLES `qrtz_job_details` WRITE;
/*!40000 ALTER TABLE `qrtz_job_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_job_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_locks`
--

DROP TABLE IF EXISTS `qrtz_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_locks` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `LOCK_NAME` varchar(40) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`LOCK_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 集群锁（TRIGGER_ACCESS / STATE_ACCESS）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_locks`
--

LOCK TABLES `qrtz_locks` WRITE;
/*!40000 ALTER TABLE `qrtz_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 暂停触发器组';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_paused_trigger_grps`
--

LOCK TABLES `qrtz_paused_trigger_grps` WRITE;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_scheduler_state`
--

DROP TABLE IF EXISTS `qrtz_scheduler_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_scheduler_state` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `INSTANCE_NAME` varchar(200) NOT NULL,
  `LAST_CHECKIN_TIME` bigint NOT NULL,
  `CHECKIN_INTERVAL` bigint NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 调度器实例心跳';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_scheduler_state`
--

LOCK TABLES `qrtz_scheduler_state` WRITE;
/*!40000 ALTER TABLE `qrtz_scheduler_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_scheduler_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simple_triggers`
--

DROP TABLE IF EXISTS `qrtz_simple_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simple_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(200) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  `REPEAT_COUNT` bigint NOT NULL,
  `REPEAT_INTERVAL` bigint NOT NULL,
  `TIMES_TRIGGERED` bigint NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 简单触发器（本项目暂不用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simple_triggers`
--

LOCK TABLES `qrtz_simple_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simple_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simple_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simprop_triggers`
--

DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simprop_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(200) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  `STR_PROP_1` varchar(512) DEFAULT NULL,
  `STR_PROP_2` varchar(512) DEFAULT NULL,
  `STR_PROP_3` varchar(512) DEFAULT NULL,
  `INT_PROP_1` int DEFAULT NULL,
  `INT_PROP_2` int DEFAULT NULL,
  `LONG_PROP_1` bigint DEFAULT NULL,
  `LONG_PROP_2` bigint DEFAULT NULL,
  `DEC_PROP_1` decimal(13,4) DEFAULT NULL,
  `DEC_PROP_2` decimal(13,4) DEFAULT NULL,
  `BOOL_PROP_1` varchar(1) DEFAULT NULL,
  `BOOL_PROP_2` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz 自定义属性触发器（本项目暂不用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simprop_triggers`
--

LOCK TABLES `qrtz_simprop_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_triggers`
--

DROP TABLE IF EXISTS `qrtz_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(200) NOT NULL,
  `TRIGGER_GROUP` varchar(200) NOT NULL,
  `JOB_NAME` varchar(200) NOT NULL,
  `JOB_GROUP` varchar(200) NOT NULL,
  `DESCRIPTION` varchar(250) DEFAULT NULL,
  `NEXT_FIRE_TIME` bigint DEFAULT NULL,
  `PREV_FIRE_TIME` bigint DEFAULT NULL,
  `PRIORITY` int DEFAULT NULL,
  `TRIGGER_STATE` varchar(16) NOT NULL,
  `TRIGGER_TYPE` varchar(8) NOT NULL,
  `START_TIME` bigint NOT NULL,
  `END_TIME` bigint DEFAULT NULL,
  `CALENDAR_NAME` varchar(200) DEFAULT NULL,
  `MISFIRE_INSTR` smallint DEFAULT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_J` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_C` (`SCHED_NAME`,`CALENDAR_NAME`),
  KEY `IDX_QRTZ_T_G` (`SCHED_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_STATE` (`SCHED_NAME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_STATE` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_G_STATE` (`SCHED_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NEXT_FIRE_TIME` (`SCHED_NAME`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST` (`SCHED_NAME`,`TRIGGER_STATE`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) REFERENCES `qrtz_job_details` (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Quartz Trigger 主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_triggers`
--

LOCK TABLES `qrtz_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `report_saved_query`
--

DROP TABLE IF EXISTS `report_saved_query`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_saved_query` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案ID',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码列表(JSON数组)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `report_saved_query`
--

LOCK TABLES `report_saved_query` WRITE;
/*!40000 ALTER TABLE `report_saved_query` DISABLE KEYS */;
/*!40000 ALTER TABLE `report_saved_query` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `support_request`
--

DROP TABLE IF EXISTS `support_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `support_request` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID',
  `request_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请编号',
  `submit_group_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '同批提交分组ID(可选)',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `source_touch_task_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源触达任务ID',
  `product_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产品ID',
  `support_dept_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '承接部门ORG_CODE',
  `other_demand` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '其他需求',
  `dispatch_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '派单人(部门秘书)',
  `dispatch_time` datetime DEFAULT NULL COMMENT '派单时间',
  `assigned_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '承接办理人',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键(SUPPORT:{id})',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除标记(0-否,1-是)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_support_dept` (`support_dept_id`),
  KEY `idx_assigned_emp` (`assigned_emp_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='中场支持申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `support_request`
--

LOCK TABLES `support_request` WRITE;
/*!40000 ALTER TABLE `support_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `support_request` ENABLE KEYS */;
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
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作日历(按天)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_calendar_day`
--

LOCK TABLES `sys_calendar_day` WRITE;
/*!40000 ALTER TABLE `sys_calendar_day` DISABLE KEYS */;
INSERT INTO `sys_calendar_day` VALUES ('2026-01-01',0,'元旦','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-01-02',0,'元旦假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-01-03',0,'元旦假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-01-04',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-10',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-11',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-17',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-18',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-24',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-25',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-01-31',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-01',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-07',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-08',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-14',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-15',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-21',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-22',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-02-28',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-01',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-07',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-08',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-14',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-15',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-21',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-22',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-28',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-29',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-03-31',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-01',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-04',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-05',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-11',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-12',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-18',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-19',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-25',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-26',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-04-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-01',0,'labor-day','seed','2026-04-03 22:46:31','admin','2026-04-10 12:20:09'),('2026-05-02',0,'劳动节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-05-03',0,'劳动节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-05-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-09',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-10',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-16',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-17',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-23',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-24',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-30',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-05-31',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-01',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-06',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-07',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-13',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-14',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-20',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-21',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-27',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-28',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-06-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-01',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-04',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-05',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-11',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-12',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-18',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-19',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-25',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-26',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-07-31',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-01',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-02',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-08',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-09',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-15',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-16',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-22',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-23',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-29',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-30',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-08-31',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-01',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-05',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-06',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-12',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-13',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-19',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-20',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-26',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-27',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-09-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-01',0,'国庆节','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-02',0,'国庆节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-03',0,'国庆节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-04',0,'国庆节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-05',0,'国庆节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-06',0,'国庆节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-07',0,'国庆节假期','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('2026-10-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-10',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-11',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-17',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-18',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-24',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-25',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-10-31',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-01',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-05',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-06',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-07',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-08',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-12',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-13',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-14',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-15',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-19',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-20',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-21',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-22',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-26',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-27',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-28',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-29',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-11-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-01',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-02',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-03',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-04',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-05',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-06',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-07',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-08',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-09',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-10',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-11',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-12',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-13',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-14',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-15',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-16',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-17',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-18',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-19',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-20',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-21',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-22',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-23',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-24',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-25',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-26',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-27',0,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-28',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-29',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-30',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08'),('2026-12-31',1,NULL,'admin','2026-04-10 11:38:08','admin','2026-04-10 11:38:08');
/*!40000 ALTER TABLE `sys_calendar_day` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config_kv`
--

DROP TABLE IF EXISTS `sys_config_kv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config_kv` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置ID',
  `config_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置键(唯一)',
  `config_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '配置值',
  `value_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/JSON/NUMBER/BOOL',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
INSERT INTO `sys_config_kv` VALUES ('CFG_AUDIT_EXPORT_MAX_DAYS','AUDIT_EXPORT_MAX_DAYS','31','NUMBER','ACTIVE','审计导出最大天数','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('CFG_AUDIT_EXPORT_MAX_ROWS','AUDIT_EXPORT_MAX_ROWS','200000','NUMBER','ACTIVE','审计导出最大行数','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('CFG_SQL_PROBE_MAX_CONCURRENCY','SQL_PROBE_MAX_CONCURRENCY','5','NUMBER','ACTIVE','SQL探查并发上限','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('CFG_SQL_PROBE_MAX_LIMIT','SQL_PROBE_MAX_LIMIT','2000','NUMBER','ACTIVE','SQL探查LIMIT上限','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31'),('CFG_SQL_PROBE_WHITELIST_JSON','SQL_PROBE_WHITELIST_JSON','{\"schemas\":[],\"tables\":[]}','JSON','ACTIVE','SQL探查白名单','seed','2026-04-03 22:46:31',NULL,'2026-04-03 22:46:31');
/*!40000 ALTER TABLE `sys_config_kv` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_control`
--

DROP TABLE IF EXISTS `sys_control`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_control` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '控制ID',
  `scope_dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `latest_data_date` date NOT NULL COMMENT '最新数据日期',
  `current_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前有效版本',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '切版备注',
  `is_valid` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最后更新人',
  `publish_source` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布来源：MANUAL/AUTO/ROLLBACK',
  `publish_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布人',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_dim_date_version` (`scope_dim`,`latest_data_date`,`current_version`),
  KEY `idx_scope_valid` (`scope_dim`,`is_valid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据版本控制表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_control`
--

LOCK TABLES `sys_control` WRITE;
/*!40000 ALTER TABLE `sys_control` DISABLE KEYS */;
INSERT INTO `sys_control` VALUES ('SC_INIT_CUST','CUST','1970-01-01',NULL,NULL,0,'2026-04-03 22:46:31','2026-04-03 22:46:31',NULL,NULL,NULL,NULL),('SC_INIT_EMP','EMP','1970-01-01',NULL,NULL,0,'2026-04-03 22:46:31','2026-04-03 22:46:31',NULL,NULL,NULL,NULL),('SC_INIT_ORG','ORG','1970-01-01',NULL,NULL,0,'2026-04-03 22:46:31','2026-04-03 22:46:31',NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `sys_control` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict`
--

DROP TABLE IF EXISTS `sys_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典ID',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典类型',
  `dict_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典编码',
  `dict_label` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典标签',
  `dict_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典值',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
INSERT INTO `sys_dict` VALUES ('D_BK_CD','BIZ_KIND','NCD','大额存单','NCD',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BK_DEP','BIZ_KIND','DEPOSIT','存款','DEPOSIT',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BK_LOAN','BIZ_KIND','LOAN','贷款','LOAN',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BK_MID','BIZ_KIND','INTERMEDIATE','中间业务','INTERMEDIATE',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BT_ACCEPTANCE','BIZ_TYPE','ACCEPTANCE','承兑汇票','ACCEPTANCE',5,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BT_FIXED','BIZ_TYPE','FIXED_ASSET','固定资产贷款','FIXED_ASSET',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BT_GUARANTEE','BIZ_TYPE','GUARANTEE','保函','GUARANTEE',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BT_TRADE','BIZ_TYPE','TRADE_FINANCE','贸易融资','TRADE_FINANCE',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_BT_WORKING_CAP','BIZ_TYPE','WORKING_CAPITAL','流动资金贷款','WORKING_CAPITAL',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CF_DAY','PERF_CALC_FREQ','DAY','日','DAY',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CF_MONTH','PERF_CALC_FREQ','MONTH','月','MONTH',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CF_QUARTER','PERF_CALC_FREQ','QUARTER','季','QUARTER',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CF_YEAR','PERF_CALC_FREQ','YEAR','年','YEAR',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CLT_EXPR','PERF_CALC_LOGIC_TYPE','EXPR','表达式','EXPR',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CLT_PROC','PERF_CALC_LOGIC_TYPE','PROC','存储过程','PROC',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CLT_SQL','PERF_CALC_LOGIC_TYPE','SQL','SQL查询','SQL',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CLT_SUMMARY','PERF_CALC_LOGIC_TYPE','SUMMARY','汇总','SUMMARY',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CM_AUTO','PERF_CALC_MODE','AUTO','自动计算','AUTO',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CM_MANUAL','PERF_CALC_MODE','MANUAL','手工导入','MANUAL',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CT_1','CUSTOMER_TYPE','CORP','对公客户','CORP',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CT_2','CUSTOMER_TYPE','RETAIL','零售客户','RETAIL',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_CVT_BOOL','CONFIG_VALUE_TYPE','BOOL','布尔','BOOL',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_CVT_JSON','CONFIG_VALUE_TYPE','JSON','JSON','JSON',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_CVT_NUM','CONFIG_VALUE_TYPE','NUMBER','数值','NUMBER',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_CVT_STR','CONFIG_VALUE_TYPE','STRING','字符串','STRING',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_DC_GUIDE','DOC_CATEGORY','GUIDE','操作指引','GUIDE',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_DC_POLICY','DOC_CATEGORY','POLICY','制度文件','POLICY',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_DC_TEMPLATE','DOC_CATEGORY','TEMPLATE','模板表单','TEMPLATE',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_DC_TRAIN','DOC_CATEGORY','TRAINING','培训材料','TRAINING',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_EF5975FD','TEST_TYPE','X','updated','v2',2,'DISABLED','upd','admin','2026-04-10 12:20:08','admin','2026-04-10 12:20:09'),('D_ET_1','ENTERPRISE_TYPE','SOE','国企','SOE',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ET_2','ENTERPRISE_TYPE','PRIVATE','民营','PRIVATE',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ET_3','ENTERPRISE_TYPE','FOREIGN','外资','FOREIGN',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ET_4','ENTERPRISE_TYPE','JV','合资','JV',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ET_5','ENTERPRISE_TYPE','COLLECT','集体企业','COLLECT',5,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GRP_1','GROUP_TYPE','GROUP','集团客户','GROUP',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GRP_2','GROUP_TYPE','SINGLE','非集团客户','SINGLE',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GT_CREDIT','GUARANTEE_TYPE','CREDIT','信用','CREDIT',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GT_GUARANTEE','GUARANTEE_TYPE','GUARANTEE','保证','GUARANTEE',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GT_MIXED','GUARANTEE_TYPE','MIXED','组合担保','MIXED',5,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GT_MORTGAGE','GUARANTEE_TYPE','MORTGAGE','抵押','MORTGAGE',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_GT_PLEDGE','GUARANTEE_TYPE','PLEDGE','质押','PLEDGE',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_AGRI','INDUSTRY','AGRI','农林牧渔业','AGRI',10,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_EDU','INDUSTRY','EDU','教育','EDU',5,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_ENERGY','INDUSTRY','ENERGY','能源','ENERGY',9,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_FIN','INDUSTRY','FIN','金融业','FIN',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_IT','INDUSTRY','IT','信息技术','IT',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_MED','INDUSTRY','MED','医疗卫生','MED',6,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_MFG','INDUSTRY','MFG','制造业','MFG',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_OTHER','INDUSTRY','OTHER','其他','OTHER',99,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_RE','INDUSTRY','RE','房地产业','RE',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_RETAIL','INDUSTRY','RETAIL','批发和零售业','RETAIL',7,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_IND_TRANS','INDUSTRY','TRANS','交通运输业','TRANS',8,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_JRS_FAIL','JOB_RUN_STATUS','FAILED','失败','FAILED',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_JRS_OK','JOB_RUN_STATUS','SUCCESS','成功','SUCCESS',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_JRS_RUN','JOB_RUN_STATUS','RUNNING','运行中','RUNNING',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_JS_ACT','JOB_STATUS','ACTIVE','活跃','ACTIVE',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_JS_PAU','JOB_STATUS','PAUSED','暂停','PAUSED',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_KC_M','PERF_KPI_CYCLE','MONTHLY','月度','MONTHLY',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_KC_Q','PERF_KPI_CYCLE','QUARTERLY','季度','QUARTERLY',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_LS_ASSIGN','LEAD_SOURCE','ASSIGNED','上级分配','ASSIGNED',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_LS_IMPORT','LEAD_SOURCE','IMPORTED','批量导入','IMPORTED',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_LS_REFER','LEAD_SOURCE','REFERRAL','转介绍','REFERRAL',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_LS_SELF','LEAD_SOURCE','SELF_FOUND','自行挖掘','SELF_FOUND',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_MD_CUST','PERF_BASE_DIM','CUST','客户','CUST',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_MD_EMP','PERF_BASE_DIM','EMP','人员','EMP',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_MD_ORG','PERF_BASE_DIM','ORG','机构','ORG',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ML_1','PERF_METRIC_LEVEL','1','一级基础','1',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ML_2','PERF_METRIC_LEVEL','2','二级派生','2',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_ML_3','PERF_METRIC_LEVEL','3','三级复合','3',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_NT_BIZ','NOTIFY_TYPE','BUSINESS','业务通知','BUSINESS',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_NT_SYS','NOTIFY_TYPE','SYSTEM','系统通知','SYSTEM',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_NT_WF','NOTIFY_TYPE','WORKFLOW','流程通知','WORKFLOW',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PAD_ACCOUNT','PERF_ALLOC_DIM','ACCOUNT','按台账分配','ACCOUNT',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PAD_RULE','PERF_ALLOC_DIM','RULE','按规则分配','RULE',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PC_CORP','PRODUCT_CATEGORY','CORP_BANK','公司银行','CORP_BANK',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_PC_FM','PRODUCT_CATEGORY','FIN_MARKET','金融市场','FIN_MARKET',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_PC_RTL','PRODUCT_CATEGORY','RETAIL_BANK','零售银行','RETAIL_BANK',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_PC_TRADE','PRODUCT_CATEGORY','TRADE_BANK','交易银行','TRADE_BANK',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_PIT_IDX','PERF_IMPORT_TYPE','INDEX_RESULT','指标结果','INDEX_RESULT',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PIT_KPI','PERF_IMPORT_TYPE','KPI_RESULT','KPI结果','KPI_RESULT',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PIT_TGT','PERF_IMPORT_TYPE','TARGET','目标','TARGET',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PJT_ADJUST','PROJECT_TYPE','ADJUST','调整项目','ADJUST',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PJT_NEW','PROJECT_TYPE','NEW','新增项目','NEW',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PJT_RENEW','PROJECT_TYPE','RENEWAL','续贷项目','RENEWAL',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_BH','POSITION','BRANCH_HEAD','支行负责人','BRANCH_HEAD',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_CA','POSITION','CREDIT_APPROVE','授信批复岗','CREDIT_APPROVE',9,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_CM','POSITION','CUST_MGR','客户经理','CUST_MGR',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_CORP','POSITION','CORP_STAFF','公司部员工','CORP_STAFF',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_CR','POSITION','CREDIT_REVIEW','授信审查岗','CREDIT_REVIEW',8,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_FIN','POSITION','FINANCE_STAFF','资财部员工','FINANCE_STAFF',5,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_PRES','POSITION','PRESIDENT','行长','PRESIDENT',10,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_RTL','POSITION','RETAIL_STAFF','零售部员工','RETAIL_STAFF',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_SEC','POSITION','SECRETARY','部门秘书','SECRETARY',7,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_POS_TECH','POSITION','TECH_STAFF','科技部员工','TECH_STAFF',6,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PSR_AVG','PERF_SUMMARY_RULE','AVG','平均','AVG',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PSR_SUM','PERF_SUMMARY_RULE','SUM','求和','SUM',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PTT_KPI','PERF_TASK_TYPE','KPI_RUN','KPI执行','KPI_RUN',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PTT_RECALC','PERF_TASK_TYPE','RECALC','历史重算','RECALC',4,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PTT_RUN','PERF_TASK_TYPE','METRIC_RUN','指标执行','METRIC_RUN',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_PTT_TRIAL','PERF_TASK_TYPE','METRIC_TRIAL','指标试运行','METRIC_TRIAL',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_RL_HIGH','RISK_LEVEL','HIGH','高风险','HIGH',3,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_RL_LOW','RISK_LEVEL','LOW','低风险','LOW',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_RL_MID','RISK_LEVEL','MEDIUM','中风险','MEDIUM',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:46',NULL,'2026-04-03 22:43:46'),('D_TC_Q','PERF_TARGET_CYCLE','QUARTER','季度','QUARTER',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_TC_Y','PERF_TARGET_CYCLE','YEAR','年度','YEAR',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_TD_EMP','PERF_TARGET_DIM','EMP','人员','EMP',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_TD_ORG','PERF_TARGET_DIM','ORG','机构','ORG',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_YN_0','YES_NO','NO','否','0',2,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('D_YN_1','YES_NO','YES','是','1',1,'ACTIVE','V1 seed','seed','2026-04-03 22:43:45',NULL,'2026-04-03 22:43:45'),('PERF_DICT_001','PERF_BASE_DIM','PERF_BASE_DIM','指标维度','EMP/ORG/CUST',1,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_002','PERF_METRIC_LEVEL','PERF_METRIC_LEVEL','指标级次','1/2/3',2,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_003','PERF_METRIC_CALC_LOGIC','PERF_METRIC_CALC_LOGIC','计算逻辑类型','SQL/PROC/EXPR/SUMMARY',3,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_004','PERF_CALC_FREQ','PERF_CALC_FREQ','计算频率','DAY/MONTH/QUARTER/YEAR',4,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_005','PERF_CYCLE_TYPE','PERF_CYCLE_TYPE','考核周期类型','MONTHLY/QUARTERLY/YEARLY',5,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_006','PERF_TASK_TYPE','PERF_TASK_TYPE','任务类型','METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC/DATA_IMPORT',6,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_007','PERF_TASK_STATUS','PERF_TASK_STATUS','任务状态','PENDING/RUNNING/SUCCESS/FAILED/PARTIAL/CANCELLED',7,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_008','PERF_METRIC_STATUS','PERF_METRIC_STATUS','指标状态','DRAFT/PUBLISHED/DISABLED/ACTIVE',8,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_009','PERF_APPLY_STATUS','PERF_APPLY_STATUS','申请状态','(V1.2 use)',9,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PERF_DICT_010','PERF_ALLOC_DIM','PERF_ALLOC_DIM','分配维度','RULE/ACCOUNT',10,'ACTIVE','perf v1.0','seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38');
/*!40000 ALTER TABLE `sys_dict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_item`
--

DROP TABLE IF EXISTS `sys_dict_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项ID',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典类型（关联 sys_dict.dict_type）',
  `item_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项编码',
  `item_label` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项标签',
  `item_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项值',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
INSERT INTO `sys_dict_item` VALUES ('PI_AD_01','PERF_ALLOC_DIM','RULE','规则维度','RULE',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_AD_02','PERF_ALLOC_DIM','ACCOUNT','账号维度','ACCOUNT',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_AS_01','PERF_APPLY_STATUS','DRAFT','草稿','DRAFT',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_AS_02','PERF_APPLY_STATUS','IN_APPROVAL','审批中','IN_APPROVAL',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_AS_03','PERF_APPLY_STATUS','APPROVED','审批通过','APPROVED',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_AS_04','PERF_APPLY_STATUS','REJECTED','已驳回','REJECTED',4,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_AS_05','PERF_APPLY_STATUS','CANCELLED','已撤回','CANCELLED',5,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_BD_01','PERF_BASE_DIM','EMP','员工','EMP',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_BD_02','PERF_BASE_DIM','ORG','机构','ORG',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_BD_03','PERF_BASE_DIM','CUST','客户','CUST',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CF_01','PERF_CALC_FREQ','DAY','日','DAY',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CF_02','PERF_CALC_FREQ','MONTH','月','MONTH',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CF_03','PERF_CALC_FREQ','QUARTER','季','QUARTER',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CF_04','PERF_CALC_FREQ','YEAR','年','YEAR',4,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CL_01','PERF_METRIC_CALC_LOGIC','SQL','SQL查询','SQL',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CL_02','PERF_METRIC_CALC_LOGIC','PROC','存储过程','PROC',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CL_03','PERF_METRIC_CALC_LOGIC','EXPR','表达式','EXPR',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CL_04','PERF_METRIC_CALC_LOGIC','SUMMARY','汇总规则','SUMMARY',4,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CT_01','PERF_CYCLE_TYPE','MONTHLY','月度','MONTHLY',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CT_02','PERF_CYCLE_TYPE','QUARTERLY','季度','QUARTERLY',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_CT_03','PERF_CYCLE_TYPE','YEARLY','年度','YEARLY',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_ML_01','PERF_METRIC_LEVEL','1','一级','1',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_ML_02','PERF_METRIC_LEVEL','2','二级','2',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_ML_03','PERF_METRIC_LEVEL','3','三级','3',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_MS_01','PERF_METRIC_STATUS','DRAFT','草稿','DRAFT',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_MS_02','PERF_METRIC_STATUS','PUBLISHED','已发布','PUBLISHED',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_MS_03','PERF_METRIC_STATUS','DISABLED','已停用','DISABLED',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_MS_04','PERF_METRIC_STATUS','ACTIVE','激活中','ACTIVE',4,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TS_01','PERF_TASK_STATUS','PENDING','待执行','PENDING',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TS_02','PERF_TASK_STATUS','RUNNING','执行中','RUNNING',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TS_03','PERF_TASK_STATUS','SUCCESS','成功','SUCCESS',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TS_04','PERF_TASK_STATUS','FAILED','失败','FAILED',4,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TS_05','PERF_TASK_STATUS','PARTIAL','部分成功','PARTIAL',5,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TS_06','PERF_TASK_STATUS','CANCELLED','已取消','CANCELLED',6,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TT_01','PERF_TASK_TYPE','METRIC_TRIAL','指标试运行','METRIC_TRIAL',1,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TT_02','PERF_TASK_TYPE','METRIC_RUN','指标计算','METRIC_RUN',2,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TT_03','PERF_TASK_TYPE','KPI_RUN','KPI计算','KPI_RUN',3,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TT_04','PERF_TASK_TYPE','RECALC','历史回算','RECALC',4,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38'),('PI_TT_05','PERF_TASK_TYPE','DATA_IMPORT','数据导入','DATA_IMPORT',5,'ACTIVE',NULL,'seed','2026-04-16 15:07:38',NULL,'2026-04-16 15:07:38');
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_conf`
--

DROP TABLE IF EXISTS `sys_job_conf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_conf` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `job_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务KEY(唯一)',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `cron_expr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron表达式',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/PAUSED',
  `allow_manual_trigger` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许手动触发',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次执行时间',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次执行时间(可选)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `quartz_job_class` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
  `misfire_policy` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略：FIRE_ONCE_NOW/DO_NOTHING/IGNORE_MISFIRE_POLICY（V1.6 新增）',
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
INSERT INTO `sys_job_conf` VALUES ('JOB_DAILY_KPI_CALC','DAILY_KPI_CALC','日常 KPI 计算','0 30 1 * * ?','ACTIVE',1,NULL,NULL,'V1.6 quartz 整合引入；KPI 计算（T-1）错过补跑一次','SYSTEM','2026-04-25 15:39:07','SYSTEM','2026-04-25 15:39:07','com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob','FIRE_ONCE_NOW'),('JOB_PERF_RUN_TASK_CLEANUP','PERF_RUN_TASK_CLEANUP','绩效执行任务清理','0 30 3 * * ?','ACTIVE',1,NULL,NULL,'V1.6 quartz 整合引入；删除 SUCCESS + end_time<now-90d 任务，错过即跳过','SYSTEM','2026-04-25 15:39:07','SYSTEM','2026-04-25 15:39:07','com.bank.branch.platform.performance.job.quartz.PerfRunTaskCleanupQuartzJob','DO_NOTHING'),('JOB_SYS_CONTROL_CLEANUP','SYS_CONTROL_CLEANUP','系统控制历史清理','0 0 3 * * ?','ACTIVE',1,NULL,NULL,'V1.6 quartz 整合引入；按 scope_dim 分组保留最新 12 条历史，错过即跳过','SYSTEM','2026-04-25 15:39:07','SYSTEM','2026-04-25 15:39:07','com.bank.branch.platform.performance.job.quartz.SysControlCleanupQuartzJob','DO_NOTHING');
/*!40000 ALTER TABLE `sys_job_conf` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_run_log`
--

DROP TABLE IF EXISTS `sys_job_run_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_run_log` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行日志ID',
  `job_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `trigger_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型：SCHEDULED/MANUAL',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原因(手动触发必填)',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `error_msg` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '触发人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `scheduled_fire_time` datetime(3) DEFAULT NULL COMMENT 'Quartz 计划触发时间（V1.6 新增，用于 misfire 排查）',
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

--
-- Table structure for table `touch_log`
--

DROP TABLE IF EXISTS `touch_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `touch_log` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '日志ID',
  `touch_task_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触达任务ID',
  `log_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间(业务时间)',
  `client_uuid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端幂等UUID(移动端重试去重)',
  `log_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '日志内容',
  `photo_urls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '照片URL列表（JSON数组）',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属机构代码',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人工号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_client_uuid` (`touch_task_id`,`client_uuid`),
  KEY `idx_task_id` (`touch_task_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_task_log_time` (`touch_task_id`,`log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触达日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `touch_log`
--

LOCK TABLES `touch_log` WRITE;
/*!40000 ALTER TABLE `touch_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `touch_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `touch_task`
--

DROP TABLE IF EXISTS `touch_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `touch_task` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `task_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务编号',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID',
  `org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构代码',
  `assignee_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行人工号',
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务类型：FIRST_TOUCH-首次触达, FOLLOW_UP-跟进',
  `task_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待办, SUCCESS-成功, CANCELLED-取消',
  `plan_finish_time` datetime DEFAULT NULL COMMENT '计划完成时间(SLA)',
  `warning_time` datetime DEFAULT NULL COMMENT '预警时间(SLA)',
  `sla_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SLA状态：GREEN/YELLOW/RED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键（TOUCH:{id}）',
  `success_time` datetime DEFAULT NULL COMMENT '成功时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_assignee` (`assignee_emp_id`),
  KEY `idx_status` (`task_status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_assignee_status` (`assignee_emp_id`,`task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触达任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `touch_task`
--

LOCK TABLES `touch_task` WRITE;
/*!40000 ALTER TABLE `touch_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `touch_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_notification`
--

DROP TABLE IF EXISTS `user_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_notification` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知ID',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '接收人工号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '通知内容',
  `notify_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通知类型：SYSTEM-系统, WORKFLOW-流程, BUSINESS-业务',
  `biz_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务ID',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '跳转链接',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读：1-已读, 0-未读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_read` (`emp_id`,`is_read`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_notification`
--

LOCK TABLES `user_notification` WRITE;
/*!40000 ALTER TABLE `user_notification` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wf_node_candidate_conf`
--

DROP TABLE IF EXISTS `wf_node_candidate_conf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_node_candidate_conf` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置ID',
  `process_definition_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点KEY',
  `candidate_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '候选类型：ROLE-角色, ORG-机构, USER-指定用户',
  `candidate_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '候选值（JSON）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pd_node_type` (`process_definition_key`,`node_key`,`candidate_type`),
  KEY `idx_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点候选人配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_node_candidate_conf`
--

LOCK TABLES `wf_node_candidate_conf` WRITE;
/*!40000 ALTER TABLE `wf_node_candidate_conf` DISABLE KEYS */;
INSERT INTO `wf_node_candidate_conf` VALUES ('baae61bb40b948929cf62154cef4d4e7','test-proc','approval','ROLE','R_ADMIN,R_BRANCH_MGR','2026-04-10 12:20:19','2026-04-10 12:20:19'),('WNC_ALLOC_BIZ','alloc_adjust_approve_v1','biz_dept_review','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_ALLOC_BIZ_LDR','alloc_adjust_approve_v1','biz_dept_leader_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_ALLOC_BM','alloc_adjust_approve_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_ALLOC_FIN','alloc_adjust_approve_v1','finance_review','ROLE','[\"BACK_FINANCE\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_ALLOC_FIN_LDR','alloc_adjust_approve_v1','finance_leader_approve','ROLE','[\"BACK_FINANCE\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_ALLOC_ORIG','alloc_adjust_approve_v1','original_owner_approve','ROLE','[\"CUST_MANAGER\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LEAD_DEL_V1_HQ_ROLE','lead_delete_approve_v1','hq_delete_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LEAD_IMP_V1_HQ_ROLE','lead_import_approve_v1','hq_batch_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LEAD_V1_BM_ROLE','lead_approve_v1','branch_manager_approve','ROLE','[\"BRANCH_HEAD\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LEAD_V1_HQ_ROLE','lead_approve_v1','hq_review','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LOAN_V1_BM','loan_approve_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LOAN_V1_CA','loan_approve_v1','credit_approval','ROLE','[\"CREDIT_APPROVER\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LOAN_V1_CK','loan_approve_v1','credit_check','ROLE','[\"CREDIT_REVIEWER\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_LOAN_V1_CORP','loan_approve_v1','corp_review','ROLE','[\"CORP_DEPT\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch','ROLE','[\"SUPPORT_SECRETARY\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle','ROLE','[\"SUPPORT_STAFF\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle','ROLE','[\"SUPPORT_STAFF\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_TGT_ADJ_FL','target_adjust_approve_v1','finance_leader_approve','ROLE','[\"BACK_FINANCE\"]','2026-04-03 22:43:46','2026-04-03 22:46:34'),('WNC_TOUCH_V1_RM_ROLE','touch_process_v1','touch_execute','ROLE','[\"CUST_MANAGER\"]','2026-04-03 22:43:46','2026-04-03 22:46:34');
/*!40000 ALTER TABLE `wf_node_candidate_conf` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wf_node_form_conf`
--

DROP TABLE IF EXISTS `wf_node_form_conf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_node_form_conf` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置ID',
  `process_definition_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点KEY',
  `form_fields` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '表单字段配置（JSON）',
  `editable_fields` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '可编辑字段列表（JSON数组）',
  `required_fields` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '必填字段列表（JSON数组）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点表单配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_node_form_conf`
--

LOCK TABLES `wf_node_form_conf` WRITE;
/*!40000 ALTER TABLE `wf_node_form_conf` DISABLE KEYS */;
INSERT INTO `wf_node_form_conf` VALUES ('56dc9ad0416845a09ae6221cf611a9b7','test-proc','approval','[\"f1\"]','[\"f1\"]','[]','2026-04-10 12:20:20','2026-04-10 12:20:20'),('WFF_ALLOC_BIZ','alloc_adjust_approve_v1','biz_dept_review','[{\"key\":\"bizDeptOpinion\",\"label\":\"业务部门审核意见\",\"type\":\"TEXTAREA\"}]','[\"bizDeptOpinion\"]','[\"bizDeptOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_ALLOC_BIZ_LDR','alloc_adjust_approve_v1','biz_dept_leader_approve','[{\"key\":\"bizLeaderOpinion\",\"label\":\"业务部门负责人意见\",\"type\":\"TEXTAREA\"}]','[\"bizLeaderOpinion\"]','[\"bizLeaderOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_ALLOC_BM','alloc_adjust_approve_v1','branch_approve','[{\"key\":\"branchAllocOpinion\",\"label\":\"机构审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchAllocOpinion\"]','[\"branchAllocOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_ALLOC_FIN','alloc_adjust_approve_v1','finance_review','[{\"key\":\"financeOpinion\",\"label\":\"资财部审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"recalcRequired\",\"label\":\"是否需要历史重算\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"}]','[\"financeOpinion\",\"recalcRequired\"]','[\"financeOpinion\",\"recalcRequired\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_ALLOC_FIN_LDR','alloc_adjust_approve_v1','finance_leader_approve','[{\"key\":\"finLeaderOpinion\",\"label\":\"资财部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"finLeaderOpinion\"]','[\"finLeaderOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_ALLOC_ORIG','alloc_adjust_approve_v1','original_owner_approve','[{\"key\":\"ownerConfirm\",\"label\":\"原管户人确认意见\",\"type\":\"TEXTAREA\"}]','[\"ownerConfirm\"]','[\"ownerConfirm\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LEAD_DEL_V1_HQ','lead_delete_approve_v1','hq_delete_approve','[{\"key\":\"deleteOpinion\",\"label\":\"删除审批意见\",\"type\":\"TEXTAREA\"}]','[\"deleteOpinion\"]','[\"deleteOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LEAD_IMP_V1_HQ','lead_import_approve_v1','hq_batch_approve','[{\"key\":\"batchOpinion\",\"label\":\"批次审批意见\",\"type\":\"TEXTAREA\"}]','[\"batchOpinion\"]','[\"batchOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LEAD_V1_BM','lead_approve_v1','branch_manager_approve','[{\"key\":\"bmOpinion\",\"label\":\"机构负责人意见\",\"type\":\"TEXTAREA\"}]','[\"bmOpinion\"]','[\"bmOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:34'),('WFF_LEAD_V1_HQ','lead_approve_v1','hq_review','[{\"key\":\"hqConclusion\",\"label\":\"总部审核结论\",\"type\":\"TEXTAREA\"},{\"key\":\"riskLevel\",\"label\":\"风险等级\",\"type\":\"SELECT\",\"dictType\":\"RISK_LEVEL\"}]','[\"hqConclusion\",\"riskLevel\"]','[\"hqConclusion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LOAN_V1_BM','loan_approve_v1','branch_approve','[{\"key\":\"branchOpinion\",\"label\":\"机构审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchOpinion\"]','[\"branchOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LOAN_V1_CA','loan_approve_v1','credit_approval','[{\"key\":\"approvalOpinion\",\"label\":\"批复意见\",\"type\":\"TEXTAREA\"},{\"key\":\"approvedAmount\",\"label\":\"批复金额(万元)\",\"type\":\"NUMBER\"},{\"key\":\"approvedTerm\",\"label\":\"批复期限(月)\",\"type\":\"NUMBER\"}]','[\"approvalOpinion\",\"approvedAmount\",\"approvedTerm\"]','[\"approvalOpinion\",\"approvedAmount\",\"approvedTerm\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LOAN_V1_CK','loan_approve_v1','credit_check','[{\"key\":\"creditCheckOpinion\",\"label\":\"授信审查意见\",\"type\":\"TEXTAREA\"},{\"key\":\"creditCheckResult\",\"label\":\"审查结论\",\"type\":\"SELECT\",\"options\":[{\"label\":\"通过\",\"value\":\"PASS\"},{\"label\":\"补充材料\",\"value\":\"SUPPLEMENT\"},{\"label\":\"拒绝\",\"value\":\"REJECT\"}]}]','[\"creditCheckOpinion\",\"creditCheckResult\"]','[\"creditCheckOpinion\",\"creditCheckResult\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_LOAN_V1_CORP','loan_approve_v1','corp_review','[{\"key\":\"corpOpinion\",\"label\":\"公司部审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"needCreditCommittee\",\"label\":\"是否需要上会\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"},{\"key\":\"creditCommitteeConclusion\",\"label\":\"上会结论\",\"type\":\"TEXTAREA\"}]','[\"corpOpinion\",\"needCreditCommittee\",\"creditCommitteeConclusion\"]','[\"corpOpinion\",\"needCreditCommittee\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch','[{\"key\":\"assignedEmpId\",\"label\":\"指定支持人员\",\"type\":\"USER_SELECT\"},{\"key\":\"dispatchRemark\",\"label\":\"派单备注\",\"type\":\"TEXTAREA\"}]','[\"assignedEmpId\",\"dispatchRemark\"]','[\"assignedEmpId\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle','[{\"key\":\"handleResult\",\"label\":\"办理结果\",\"type\":\"TEXTAREA\"},{\"key\":\"visitPhotoUrls\",\"label\":\"拜访照片\",\"type\":\"FILE_LIST\"}]','[\"handleResult\",\"visitPhotoUrls\"]','[\"handleResult\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle','[{\"key\":\"handleResult\",\"label\":\"办理结果\",\"type\":\"TEXTAREA\"},{\"key\":\"visitPhotoUrls\",\"label\":\"拜访照片\",\"type\":\"FILE_LIST\"}]','[\"handleResult\",\"visitPhotoUrls\"]','[\"handleResult\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_TGT_ADJ_FL','target_adjust_approve_v1','finance_leader_approve','[{\"key\":\"adjustOpinion\",\"label\":\"修正审批意见\",\"type\":\"TEXTAREA\"}]','[\"adjustOpinion\"]','[\"adjustOpinion\"]','2026-04-03 22:43:47','2026-04-03 22:46:35'),('WFF_TOUCH_V1_EXEC','touch_process_v1','touch_execute','[]','[]','[]','2026-04-03 22:43:47','2026-04-03 22:46:35');
/*!40000 ALTER TABLE `wf_node_form_conf` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wf_timeout_rule`
--

DROP TABLE IF EXISTS `wf_timeout_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_timeout_rule` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则ID',
  `process_definition_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程定义KEY',
  `node_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点KEY',
  `timeout_hours` int NOT NULL COMMENT '超时小时数',
  `warning_hours` int DEFAULT NULL COMMENT '预警小时数',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_definition_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程超时规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_timeout_rule`
--

LOCK TABLES `wf_timeout_rule` WRITE;
/*!40000 ALTER TABLE `wf_timeout_rule` DISABLE KEYS */;
INSERT INTO `wf_timeout_rule` VALUES ('c7d69ada34e44e73bee8b29417977ff7','test-proc','approval',24,12,'2026-04-10 12:20:18','2026-04-10 12:20:19'),('WTR_ALLOC_BIZ','alloc_adjust_approve_v1','biz_dept_review',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_ALLOC_BIZ_LDR','alloc_adjust_approve_v1','biz_dept_leader_approve',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_ALLOC_BM','alloc_adjust_approve_v1','branch_approve',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_ALLOC_FIN','alloc_adjust_approve_v1','finance_review',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_ALLOC_FIN_LDR','alloc_adjust_approve_v1','finance_leader_approve',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_ALLOC_ORIG','alloc_adjust_approve_v1','original_owner_approve',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_LEAD_DEL_V1_HQ','lead_delete_approve_v1','hq_delete_approve',48,24,'2026-04-03 22:43:46','2026-04-03 22:46:34'),('WTR_LEAD_IMP_V1_HQ','lead_import_approve_v1','hq_batch_approve',72,24,'2026-04-03 22:43:46','2026-04-03 22:46:34'),('WTR_LEAD_V1_BM','lead_approve_v1','branch_manager_approve',48,24,'2026-04-03 22:43:46','2026-04-03 22:46:34'),('WTR_LEAD_V1_HQ','lead_approve_v1','hq_review',96,48,'2026-04-03 22:43:46','2026-04-03 22:46:34'),('WTR_LOAN_V1_BM','loan_approve_v1','branch_approve',48,24,'2026-04-03 22:43:46','2026-04-03 22:46:34'),('WTR_LOAN_V1_CA','loan_approve_v1','credit_approval',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_LOAN_V1_CK','loan_approve_v1','credit_check',72,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_LOAN_V1_CORP','loan_approve_v1','corp_review',72,24,'2026-04-03 22:43:46','2026-04-03 22:46:34'),('WTR_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch',24,8,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle',72,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle',72,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_TGT_ADJ_FL','target_adjust_approve_v1','finance_leader_approve',48,24,'2026-04-03 22:43:47','2026-04-03 22:46:34'),('WTR_TOUCH_V1_EXEC','touch_process_v1','touch_execute',48,24,'2026-04-03 22:43:46','2026-04-03 22:46:34');
/*!40000 ALTER TABLE `wf_timeout_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'onepl'
--

--
-- Dumping routines for database 'onepl'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-25 15:55:23
