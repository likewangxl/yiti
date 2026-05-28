-- MySQL dump 10.13  Distrib 8.0.45, for Linux (x86_64)
--
-- Host: localhost    Database: onepl
-- ------------------------------------------------------
-- Server version	8.0.45-0ubuntu0.24.04.1

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
-- Current Database: `onepl`
--

/*!40000 DROP DATABASE IF EXISTS `onepl`*/;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `onepl` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `onepl`;

--
-- Table structure for table `ACT_EVT_LOG`
--

DROP TABLE IF EXISTS `ACT_EVT_LOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_EVT_LOG` (
  `LOG_NR_` bigint NOT NULL AUTO_INCREMENT,
  `TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TIME_STAMP_` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DATA_` longblob,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL,
  `IS_PROCESSED_` tinyint DEFAULT '0',
  PRIMARY KEY (`LOG_NR_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_EVT_LOG`
--

LOCK TABLES `ACT_EVT_LOG` WRITE;
/*!40000 ALTER TABLE `ACT_EVT_LOG` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_EVT_LOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_GE_BYTEARRAY`
--

DROP TABLE IF EXISTS `ACT_GE_BYTEARRAY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_GE_BYTEARRAY` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BYTES_` longblob,
  `GENERATED_` tinyint DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_BYTEARR_DEPL` (`DEPLOYMENT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_GE_BYTEARRAY`
--

LOCK TABLES `ACT_GE_BYTEARRAY` WRITE;
/*!40000 ALTER TABLE `ACT_GE_BYTEARRAY` DISABLE KEYS */;
INSERT INTO `ACT_GE_BYTEARRAY` VALUES ('43d2831d-42d6-11f1-a167-ea952063aef8',1,'perf_target_adjust_v1.bpmn20.xml','43d2831c-42d6-11f1-a167-ea952063aef8',_binary '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n  V1.2 Phase Q0.3 目标修正审批流程占位 BPMN.\n\n  流程：Start → 支行主管审批(branch_mgr_review) → 分行主管审批(hq_mgr_review) → End\n\n  说明：\n  - processDefinitionKey=perf_target_adjust_v1；V1.2 Q3 目标修正申请启动此流程.\n  - 流程结束后 V1.2 Q3 的 TargetAdjustCompletedListener 订阅 ProcessCompletedEvent，\n    审批通过则更新 perf_target_value 表对应的 (planId, subject, metricCode) 目标值，\n    并发布 TargetAdjustmentApprovedEvent（可能触发历史回算）.\n  - 初期最简两级审批；V1.2 Q3 实现时根据目标金额阈值可能拆分为单级/二级/三级审批.\n-->\n<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n             xmlns:flowable=\"http://flowable.org/bpmn\"\n             targetNamespace=\"http://www.bank.com/branch/platform/performance\">\n\n    <process id=\"perf_target_adjust_v1\" name=\"目标修正审批\" isExecutable=\"true\">\n        <extensionElements>\n            <flowable:executionListener event=\"end\" delegateExpression=\"${processCompletedListener}\"/>\n        </extensionElements>\n\n        <startEvent id=\"start\" name=\"开始\"/>\n\n        <userTask id=\"branch_mgr_review\" name=\"支行主管审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <userTask id=\"hq_mgr_review\" name=\"分行主管审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <endEvent id=\"end\" name=\"结束\"/>\n\n        <sequenceFlow id=\"flow_start_branch\" sourceRef=\"start\" targetRef=\"branch_mgr_review\"/>\n        <sequenceFlow id=\"flow_branch_hq\" sourceRef=\"branch_mgr_review\" targetRef=\"hq_mgr_review\"/>\n        <sequenceFlow id=\"flow_hq_end\" sourceRef=\"hq_mgr_review\" targetRef=\"end\"/>\n    </process>\n</definitions>\n',0),('43d2831e-42d6-11f1-a167-ea952063aef8',1,'perf_alloc_adjust_corp_v1.bpmn20.xml','43d2831c-42d6-11f1-a167-ea952063aef8',_binary '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n  V1.2 Phase Q0.3 对公分配调整审批流程占位 BPMN.\n\n  流程：Start → 支行主管审批(branch_mgr_review) → 分行主管审批(hq_mgr_review) → End\n\n  说明：\n  - processDefinitionKey=perf_alloc_adjust_corp_v1；performance-engine-center V1.2 Q2 启动此流程.\n  - 任务候选人通过 ${taskAssignmentListener}（来自 workflow-center）解析，\n    候选人配置存储在 wf_node_candidate_conf 表（V1.2 Q2 阶段通过 workflow-seed-v1.sql 补入）.\n  - 流程结束时 ${processCompletedListener} 发布 ProcessCompletedEvent，\n    V1.2 Q2 的 AllocAdjustCompletedListener 订阅后落地分配变更.\n  - V1.2 Q2 实现分配关系调整审批时才会填充完整的条件分支 / 驳回路径 / 网关.\n-->\n<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n             xmlns:flowable=\"http://flowable.org/bpmn\"\n             targetNamespace=\"http://www.bank.com/branch/platform/performance\">\n\n    <process id=\"perf_alloc_adjust_corp_v1\" name=\"对公分配关系调整审批\" isExecutable=\"true\">\n        <extensionElements>\n            <flowable:executionListener event=\"end\" delegateExpression=\"${processCompletedListener}\"/>\n        </extensionElements>\n\n        <startEvent id=\"start\" name=\"开始\"/>\n\n        <userTask id=\"branch_mgr_review\" name=\"支行主管审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <userTask id=\"hq_mgr_review\" name=\"分行主管审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <endEvent id=\"end\" name=\"结束\"/>\n\n        <sequenceFlow id=\"flow_start_branch\" sourceRef=\"start\" targetRef=\"branch_mgr_review\"/>\n        <sequenceFlow id=\"flow_branch_hq\" sourceRef=\"branch_mgr_review\" targetRef=\"hq_mgr_review\"/>\n        <sequenceFlow id=\"flow_hq_end\" sourceRef=\"hq_mgr_review\" targetRef=\"end\"/>\n    </process>\n</definitions>\n',0),('43d2831f-42d6-11f1-a167-ea952063aef8',1,'lead_approve_v1.bpmn20.xml','43d2831c-42d6-11f1-a167-ea952063aef8',_binary '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n    线索审批流程定义（lead_approve_v1）\n    Phase 2 (b) E2E IT 用，简化为 2 节点闭环：\n        startEvent → branch_manager_approve userTask → exclusiveGateway → endEvent (approved/rejected)\n    候选人通过 wf_node_candidate_conf（process_definition_key=lead_approve_v1, node_key=branch_manager_approve）注入。\n    审批通过条件：${approved}；驳回条件：${!approved}（与 loan_approve_v1 一致）。\n-->\n<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n             xmlns:flowable=\"http://flowable.org/bpmn\"\n             targetNamespace=\"http://www.bank.com/branch/platform/workflow\">\n\n    <process id=\"lead_approve_v1\" name=\"线索审批\" isExecutable=\"true\">\n        <extensionElements>\n            <flowable:executionListener event=\"end\" delegateExpression=\"${processCompletedListener}\"/>\n        </extensionElements>\n\n        <startEvent id=\"start\" name=\"开始\"/>\n\n        <userTask id=\"branch_manager_approve\" name=\"分行经理审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <exclusiveGateway id=\"branch_manager_decision\" name=\"分行经理决策\"/>\n\n        <endEvent id=\"approved_end\" name=\"通过结束\"/>\n        <endEvent id=\"rejected_end\" name=\"驳回结束\"/>\n\n        <sequenceFlow id=\"flow_start_branch\" sourceRef=\"start\" targetRef=\"branch_manager_approve\"/>\n        <sequenceFlow id=\"flow_branch_gateway\" sourceRef=\"branch_manager_approve\" targetRef=\"branch_manager_decision\"/>\n        <sequenceFlow id=\"flow_branch_pass\" sourceRef=\"branch_manager_decision\" targetRef=\"approved_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved}]]></conditionExpression>\n        </sequenceFlow>\n        <sequenceFlow id=\"flow_branch_reject\" sourceRef=\"branch_manager_decision\" targetRef=\"rejected_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${!approved}]]></conditionExpression>\n        </sequenceFlow>\n    </process>\n</definitions>\n',0),('43d28320-42d6-11f1-a167-ea952063aef8',1,'perf_alloc_adjust_retail_v1.bpmn20.xml','43d2831c-42d6-11f1-a167-ea952063aef8',_binary '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n  V1.2 Phase Q0.3 零售分配调整审批流程占位 BPMN.\n\n  流程：Start → 支行主管审批(branch_mgr_review) → 分行主管审批(hq_mgr_review) → End\n\n  说明：\n  - processDefinitionKey=perf_alloc_adjust_retail_v1；V1.2 Q2 零售分配调整走此流程.\n  - 对公（corp）与零售（retail）分拆独立 BPMN 是因为：\n      1. 两类候选组不同（对公：CORP_MGR / 零售：RETAIL_MGR）；\n      2. 审批时效 SLA 阈值可能不同；\n      3. 未来扩展（如零售增加客户经理确认环节）时彼此独立演进.\n  - 初期流程结构与对公相同，V1.2 Q2 实现时按需差异化.\n-->\n<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n             xmlns:flowable=\"http://flowable.org/bpmn\"\n             targetNamespace=\"http://www.bank.com/branch/platform/performance\">\n\n    <process id=\"perf_alloc_adjust_retail_v1\" name=\"零售分配关系调整审批\" isExecutable=\"true\">\n        <extensionElements>\n            <flowable:executionListener event=\"end\" delegateExpression=\"${processCompletedListener}\"/>\n        </extensionElements>\n\n        <startEvent id=\"start\" name=\"开始\"/>\n\n        <userTask id=\"branch_mgr_review\" name=\"支行主管审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <userTask id=\"hq_mgr_review\" name=\"分行主管审批\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n\n        <endEvent id=\"end\" name=\"结束\"/>\n\n        <sequenceFlow id=\"flow_start_branch\" sourceRef=\"start\" targetRef=\"branch_mgr_review\"/>\n        <sequenceFlow id=\"flow_branch_hq\" sourceRef=\"branch_mgr_review\" targetRef=\"hq_mgr_review\"/>\n        <sequenceFlow id=\"flow_hq_end\" sourceRef=\"hq_mgr_review\" targetRef=\"end\"/>\n    </process>\n</definitions>\n',0),('43d28321-42d6-11f1-a167-ea952063aef8',1,'loan_approve_v1.bpmn20.xml','43d2831c-42d6-11f1-a167-ea952063aef8',_binary '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n             xmlns:flowable=\"http://flowable.org/bpmn\"\n             targetNamespace=\"http://www.bank.com/branch/platform/workflow\">\n\n    <process id=\"loan_approve_v1\" name=\"资产投放审批\" isExecutable=\"true\">\n        <extensionElements>\n            <flowable:executionListener event=\"end\" delegateExpression=\"${processCompletedListener}\"/>\n        </extensionElements>\n\n        <startEvent id=\"start\" name=\"开始\"/>\n\n        <userTask id=\"branch_approve\" name=\"经营机构负责人审核\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n        <exclusiveGateway id=\"branch_decision\" name=\"经营机构负责人决策\"/>\n\n        <userTask id=\"corp_review\" name=\"公司部审核\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n        <exclusiveGateway id=\"corp_decision\" name=\"公司部决策\"/>\n\n        <userTask id=\"credit_check\" name=\"授信审查\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n        <exclusiveGateway id=\"credit_check_decision\" name=\"授信审查决策\"/>\n\n        <userTask id=\"credit_approval\" name=\"授信批复\">\n            <extensionElements>\n                <flowable:taskListener event=\"create\" delegateExpression=\"${taskAssignmentListener}\"/>\n            </extensionElements>\n        </userTask>\n        <exclusiveGateway id=\"credit_approval_decision\" name=\"授信批复决策\"/>\n\n        <endEvent id=\"approved_end\" name=\"通过结束\"/>\n        <endEvent id=\"rejected_end\" name=\"驳回结束\"/>\n\n        <sequenceFlow id=\"flow_start_branch\" sourceRef=\"start\" targetRef=\"branch_approve\"/>\n        <sequenceFlow id=\"flow_branch_gateway\" sourceRef=\"branch_approve\" targetRef=\"branch_decision\"/>\n        <sequenceFlow id=\"flow_branch_pass\" sourceRef=\"branch_decision\" targetRef=\"corp_review\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved}]]></conditionExpression>\n        </sequenceFlow>\n        <sequenceFlow id=\"flow_branch_reject\" sourceRef=\"branch_decision\" targetRef=\"rejected_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${!approved}]]></conditionExpression>\n        </sequenceFlow>\n\n        <sequenceFlow id=\"flow_corp_gateway\" sourceRef=\"corp_review\" targetRef=\"corp_decision\"/>\n        <sequenceFlow id=\"flow_corp_pass\" sourceRef=\"corp_decision\" targetRef=\"credit_check\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved}]]></conditionExpression>\n        </sequenceFlow>\n        <sequenceFlow id=\"flow_corp_reject\" sourceRef=\"corp_decision\" targetRef=\"rejected_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${!approved}]]></conditionExpression>\n        </sequenceFlow>\n\n        <sequenceFlow id=\"flow_credit_check_gateway\" sourceRef=\"credit_check\" targetRef=\"credit_check_decision\"/>\n        <sequenceFlow id=\"flow_credit_check_pass\" sourceRef=\"credit_check_decision\" targetRef=\"credit_approval\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved}]]></conditionExpression>\n        </sequenceFlow>\n        <sequenceFlow id=\"flow_credit_check_reject\" sourceRef=\"credit_check_decision\" targetRef=\"rejected_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${!approved}]]></conditionExpression>\n        </sequenceFlow>\n\n        <sequenceFlow id=\"flow_credit_approval_gateway\" sourceRef=\"credit_approval\" targetRef=\"credit_approval_decision\"/>\n        <sequenceFlow id=\"flow_credit_approval_pass\" sourceRef=\"credit_approval_decision\" targetRef=\"approved_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved}]]></conditionExpression>\n        </sequenceFlow>\n        <sequenceFlow id=\"flow_credit_approval_reject\" sourceRef=\"credit_approval_decision\" targetRef=\"rejected_end\">\n            <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${!approved}]]></conditionExpression>\n        </sequenceFlow>\n    </process>\n</definitions>\n',0);
/*!40000 ALTER TABLE `ACT_GE_BYTEARRAY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_GE_PROPERTY`
--

DROP TABLE IF EXISTS `ACT_GE_PROPERTY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_GE_PROPERTY` (
  `NAME_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `VALUE_` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REV_` int DEFAULT NULL,
  PRIMARY KEY (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_GE_PROPERTY`
--

LOCK TABLES `ACT_GE_PROPERTY` WRITE;
/*!40000 ALTER TABLE `ACT_GE_PROPERTY` DISABLE KEYS */;
INSERT INTO `ACT_GE_PROPERTY` VALUES ('batch.schema.version','7.0.1.1',1),('cfg.execution-related-entities-count','true',1),('cfg.task-related-entities-count','true',1),('common.schema.version','7.0.1.1',1),('entitylink.schema.version','7.0.1.1',1),('eventsubscription.schema.version','7.0.1.1',1),('identitylink.schema.version','7.0.1.1',1),('job.schema.version','7.0.1.1',1),('next.dbid','1',1),('schema.history','create(7.0.1.1)',1),('schema.version','7.0.1.1',1),('task.schema.version','7.0.1.1',1),('variable.schema.version','7.0.1.1',1);
/*!40000 ALTER TABLE `ACT_GE_PROPERTY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_ACTINST`
--

DROP TABLE IF EXISTS `ACT_HI_ACTINST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_ACTINST` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `ACT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALL_PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACT_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `TRANSACTION_ORDER_` int DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_ACT_INST_START` (`START_TIME_`),
  KEY `ACT_IDX_HI_ACT_INST_END` (`END_TIME_`),
  KEY `ACT_IDX_HI_ACT_INST_PROCINST` (`PROC_INST_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_HI_ACT_INST_EXEC` (`EXECUTION_ID_`,`ACT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_ACTINST`
--

LOCK TABLES `ACT_HI_ACTINST` WRITE;
/*!40000 ALTER TABLE `ACT_HI_ACTINST` DISABLE KEYS */;
INSERT INTO `ACT_HI_ACTINST` VALUES ('3c82adde-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.244',1,5,NULL,''),('3c83983f-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:32:23.245','2026-04-29 22:32:23.245',2,0,NULL,''),('3c839840-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8','branch_mgr_review','3c851ee1-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:32:23.245',NULL,3,NULL,NULL,''),('530ca349-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.054',1,1,NULL,''),('530cca5a-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:33:01.054','2026-04-29 22:33:01.054',2,0,NULL,''),('530cca5b-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8','branch_mgr_review','530cca5c-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:33:01.054',NULL,3,NULL,NULL,''),('6a7ade34-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:33:40.363','2026-04-29 22:33:40.363',1,0,NULL,''),('6a7ade35-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:33:40.363','2026-04-29 22:33:40.363',2,0,NULL,''),('6a7ade36-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8','branch_mgr_review','6a7ade37-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:33:40.363',NULL,3,NULL,NULL,''),('6dc6d4ef-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894',1,0,NULL,''),('6dc6d4f0-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894',2,0,NULL,''),('6dc6d4f1-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8','branch_mgr_review','6dc6d4f2-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:33:45.894',NULL,3,NULL,NULL,''),('7c81475a-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:34:10.604','2026-04-29 22:34:10.604',1,0,NULL,''),('7c81475b-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:34:10.604','2026-04-29 22:34:10.604',2,0,NULL,''),('7c81475c-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8','branch_mgr_review','7c81475d-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:34:10.604',NULL,3,NULL,NULL,'');
/*!40000 ALTER TABLE `ACT_HI_ACTINST` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_ATTACHMENT`
--

DROP TABLE IF EXISTS `ACT_HI_ATTACHMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_ATTACHMENT` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `URL_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CONTENT_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_ATTACHMENT`
--

LOCK TABLES `ACT_HI_ATTACHMENT` WRITE;
/*!40000 ALTER TABLE `ACT_HI_ATTACHMENT` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_HI_ATTACHMENT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_COMMENT`
--

DROP TABLE IF EXISTS `ACT_HI_COMMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_COMMENT` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TIME_` datetime(3) NOT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACTION_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `MESSAGE_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `FULL_MSG_` longblob,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_COMMENT`
--

LOCK TABLES `ACT_HI_COMMENT` WRITE;
/*!40000 ALTER TABLE `ACT_HI_COMMENT` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_HI_COMMENT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_DETAIL`
--

DROP TABLE IF EXISTS `ACT_HI_DETAIL`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_DETAIL` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACT_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `VAR_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REV_` int DEFAULT NULL,
  `TIME_` datetime(3) NOT NULL,
  `BYTEARRAY_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DOUBLE_` double DEFAULT NULL,
  `LONG_` bigint DEFAULT NULL,
  `TEXT_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TEXT2_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_DETAIL_PROC_INST` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_DETAIL_ACT_INST` (`ACT_INST_ID_`),
  KEY `ACT_IDX_HI_DETAIL_TIME` (`TIME_`),
  KEY `ACT_IDX_HI_DETAIL_NAME` (`NAME_`),
  KEY `ACT_IDX_HI_DETAIL_TASK_ID` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_DETAIL`
--

LOCK TABLES `ACT_HI_DETAIL` WRITE;
/*!40000 ALTER TABLE `ACT_HI_DETAIL` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_HI_DETAIL` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_ENTITYLINK`
--

DROP TABLE IF EXISTS `ACT_HI_ENTITYLINK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_ENTITYLINK` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `LINK_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REF_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REF_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REF_SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ROOT_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ROOT_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HIERARCHY_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_ENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_REF_SCOPE` (`REF_SCOPE_ID_`,`REF_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_ROOT_SCOPE` (`ROOT_SCOPE_ID_`,`ROOT_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_ENTITYLINK`
--

LOCK TABLES `ACT_HI_ENTITYLINK` WRITE;
/*!40000 ALTER TABLE `ACT_HI_ENTITYLINK` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_HI_ENTITYLINK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_IDENTITYLINK`
--

DROP TABLE IF EXISTS `ACT_HI_IDENTITYLINK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_IDENTITYLINK` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `GROUP_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_USER` (`USER_ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_TASK` (`TASK_ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_IDENTITYLINK`
--

LOCK TABLES `ACT_HI_IDENTITYLINK` WRITE;
/*!40000 ALTER TABLE `ACT_HI_IDENTITYLINK` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_HI_IDENTITYLINK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_PROCINST`
--

DROP TABLE IF EXISTS `ACT_HI_PROCINST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_PROCINST` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `BUSINESS_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `START_USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_ACT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `END_ACT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUPER_PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BUSINESS_STATUS_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `PROC_INST_ID_` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_PRO_INST_END` (`END_TIME_`),
  KEY `ACT_IDX_HI_PRO_I_BUSKEY` (`BUSINESS_KEY_`),
  KEY `ACT_IDX_HI_PRO_SUPER_PROCINST` (`SUPER_PROCESS_INSTANCE_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_PROCINST`
--

LOCK TABLES `ACT_HI_PROCINST` WRITE;
/*!40000 ALTER TABLE `ACT_HI_PROCINST` DISABLE KEYS */;
INSERT INTO `ACT_HI_PROCINST` VALUES ('3c8286c7-43d8-11f1-953f-ea952063aef8',1,'3c8286c7-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:3d84ba504ec944b09c31cd8a1bd0f923','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','2026-04-29 22:32:23.238',NULL,NULL,NULL,'start',NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,NULL,NULL,NULL),('530ca342-43d8-11f1-953f-ea952063aef8',1,'530ca342-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:c45910ecbfd049dda8eb825aece3a460','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','2026-04-29 22:33:01.053',NULL,NULL,NULL,'start',NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,NULL,NULL,NULL),('6a7ab71d-43d8-11f1-953f-ea952063aef8',1,'6a7ab71d-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:e89ba10c2e534064a1b6f0b28eabc36c','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','2026-04-29 22:33:40.362',NULL,NULL,NULL,'start',NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,NULL,NULL,NULL),('6dc6add8-43d8-11f1-953f-ea952063aef8',1,'6dc6add8-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:60b1c0575fa2408ebfc8f07a769cc6ca','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','2026-04-29 22:33:45.893',NULL,NULL,NULL,'start',NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,NULL,NULL,NULL),('7c812043-43d8-11f1-953f-ea952063aef8',1,'7c812043-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:e80eed6ac527417389eef8841b527d24','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','2026-04-29 22:34:10.603',NULL,NULL,NULL,'start',NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `ACT_HI_PROCINST` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_TASKINST`
--

DROP TABLE IF EXISTS `ACT_HI_TASKINST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_TASKINST` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_DEF_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STATE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `IN_PROGRESS_TIME_` datetime(3) DEFAULT NULL,
  `IN_PROGRESS_STARTED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CLAIM_TIME_` datetime(3) DEFAULT NULL,
  `CLAIMED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUSPENDED_TIME_` datetime(3) DEFAULT NULL,
  `SUSPENDED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `COMPLETED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PRIORITY_` int DEFAULT NULL,
  `IN_PROGRESS_DUE_DATE_` datetime(3) DEFAULT NULL,
  `DUE_DATE_` datetime(3) DEFAULT NULL,
  `FORM_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_TASK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_INST_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_TASKINST`
--

LOCK TABLES `ACT_HI_TASKINST` WRITE;
/*!40000 ALTER TABLE `ACT_HI_TASKINST` DISABLE KEYS */;
INSERT INTO `ACT_HI_TASKINST` VALUES ('3c851ee1-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'branch_mgr_review','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,NULL,NULL,'2026-04-29 22:32:23.245',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,50,NULL,NULL,NULL,NULL,'','2026-04-29 22:32:23.255'),('530cca5c-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'branch_mgr_review','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,NULL,NULL,'2026-04-29 22:33:01.054',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,50,NULL,NULL,NULL,NULL,'','2026-04-29 22:33:01.054'),('6a7ade37-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'branch_mgr_review','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,NULL,NULL,'2026-04-29 22:33:40.363',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,50,NULL,NULL,NULL,NULL,'','2026-04-29 22:33:40.363'),('6dc6d4f2-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'branch_mgr_review','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,NULL,NULL,'2026-04-29 22:33:45.894',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,50,NULL,NULL,NULL,NULL,'','2026-04-29 22:33:45.894'),('7c81475d-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'branch_mgr_review','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,NULL,NULL,'2026-04-29 22:34:10.604',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,50,NULL,NULL,NULL,NULL,'','2026-04-29 22:34:10.604');
/*!40000 ALTER TABLE `ACT_HI_TASKINST` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_TSK_LOG`
--

DROP TABLE IF EXISTS `ACT_HI_TSK_LOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_TSK_LOG` (
  `ID_` bigint NOT NULL AUTO_INCREMENT,
  `TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `TIME_STAMP_` timestamp(3) NOT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DATA_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_TSK_LOG`
--

LOCK TABLES `ACT_HI_TSK_LOG` WRITE;
/*!40000 ALTER TABLE `ACT_HI_TSK_LOG` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_HI_TSK_LOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_HI_VARINST`
--

DROP TABLE IF EXISTS `ACT_HI_VARINST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_VARINST` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `VAR_TYPE_` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BYTEARRAY_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DOUBLE_` double DEFAULT NULL,
  `LONG_` bigint DEFAULT NULL,
  `TEXT_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TEXT2_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `META_INFO_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_PROCVAR_NAME_TYPE` (`NAME_`,`VAR_TYPE_`),
  KEY `ACT_IDX_HI_VAR_SCOPE_ID_TYPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_VAR_SUB_ID_TYPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_PROCVAR_PROC_INST` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_PROCVAR_TASK_ID` (`TASK_ID_`),
  KEY `ACT_IDX_HI_PROCVAR_EXE` (`EXECUTION_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_HI_VARINST`
--

LOCK TABLES `ACT_HI_VARINST` WRITE;
/*!40000 ALTER TABLE `ACT_HI_VARINST` DISABLE KEYS */;
INSERT INTO `ACT_HI_VARINST` VALUES ('3c8286c8-43d8-11f1-953f-ea952063aef8',0,'3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,'applyId','string',NULL,NULL,NULL,NULL,NULL,NULL,'3d84ba504ec944b09c31cd8a1bd0f923',NULL,NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.239'),('3c82add9-43d8-11f1-953f-ea952063aef8',0,'3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,'planId','string',NULL,NULL,NULL,NULL,NULL,NULL,'1485fe155bb54215851902a43289ddbc',NULL,NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.239'),('3c82adda-43d8-11f1-953f-ea952063aef8',0,'3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,'subjectType','string',NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.239'),('3c82addb-43d8-11f1-953f-ea952063aef8',0,'3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,'subjectId','string',NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.239'),('3c82addc-43d8-11f1-953f-ea952063aef8',0,'3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,'cycleKey','string',NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.239'),('530ca343-43d8-11f1-953f-ea952063aef8',0,'530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,'applyId','string',NULL,NULL,NULL,NULL,NULL,NULL,'c45910ecbfd049dda8eb825aece3a460',NULL,NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.053'),('530ca344-43d8-11f1-953f-ea952063aef8',0,'530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,'planId','string',NULL,NULL,NULL,NULL,NULL,NULL,'ea2ffb678bb0410898621c4da509b57c',NULL,NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.053'),('530ca345-43d8-11f1-953f-ea952063aef8',0,'530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,'subjectType','string',NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.053'),('530ca346-43d8-11f1-953f-ea952063aef8',0,'530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,'subjectId','string',NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.053'),('530ca347-43d8-11f1-953f-ea952063aef8',0,'530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,'cycleKey','string',NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.053'),('6a7ab71e-43d8-11f1-953f-ea952063aef8',0,'6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,'applyId','string',NULL,NULL,NULL,NULL,NULL,NULL,'e89ba10c2e534064a1b6f0b28eabc36c',NULL,NULL,'2026-04-29 22:33:40.362','2026-04-29 22:33:40.362'),('6a7ab71f-43d8-11f1-953f-ea952063aef8',0,'6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,'planId','string',NULL,NULL,NULL,NULL,NULL,NULL,'f8971db0c2c64fca887321b0cd15455f',NULL,NULL,'2026-04-29 22:33:40.362','2026-04-29 22:33:40.362'),('6a7ab720-43d8-11f1-953f-ea952063aef8',0,'6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,'subjectType','string',NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL,'2026-04-29 22:33:40.362','2026-04-29 22:33:40.362'),('6a7ab721-43d8-11f1-953f-ea952063aef8',0,'6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,'subjectId','string',NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-04-29 22:33:40.362','2026-04-29 22:33:40.362'),('6a7ade32-43d8-11f1-953f-ea952063aef8',0,'6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,'cycleKey','string',NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL,'2026-04-29 22:33:40.363','2026-04-29 22:33:40.363'),('6dc6d4e9-43d8-11f1-953f-ea952063aef8',0,'6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,'applyId','string',NULL,NULL,NULL,NULL,NULL,NULL,'60b1c0575fa2408ebfc8f07a769cc6ca',NULL,NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894'),('6dc6d4ea-43d8-11f1-953f-ea952063aef8',0,'6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,'planId','string',NULL,NULL,NULL,NULL,NULL,NULL,'6ec77945a8e342028c420645a9dba947',NULL,NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894'),('6dc6d4eb-43d8-11f1-953f-ea952063aef8',0,'6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,'subjectType','string',NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894'),('6dc6d4ec-43d8-11f1-953f-ea952063aef8',0,'6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,'subjectId','string',NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894'),('6dc6d4ed-43d8-11f1-953f-ea952063aef8',0,'6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,'cycleKey','string',NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894'),('7c812044-43d8-11f1-953f-ea952063aef8',0,'7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,'applyId','string',NULL,NULL,NULL,NULL,NULL,NULL,'e80eed6ac527417389eef8841b527d24',NULL,NULL,'2026-04-29 22:34:10.603','2026-04-29 22:34:10.603'),('7c812045-43d8-11f1-953f-ea952063aef8',0,'7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,'planId','string',NULL,NULL,NULL,NULL,NULL,NULL,'9bfab7fbfd474abb93f1731e8d12f88d',NULL,NULL,'2026-04-29 22:34:10.603','2026-04-29 22:34:10.603'),('7c812046-43d8-11f1-953f-ea952063aef8',0,'7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,'subjectType','string',NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL,'2026-04-29 22:34:10.603','2026-04-29 22:34:10.603'),('7c812047-43d8-11f1-953f-ea952063aef8',0,'7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,'subjectId','string',NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-04-29 22:34:10.603','2026-04-29 22:34:10.603'),('7c812048-43d8-11f1-953f-ea952063aef8',0,'7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,'cycleKey','string',NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL,'2026-04-29 22:34:10.603','2026-04-29 22:34:10.603');
/*!40000 ALTER TABLE `ACT_HI_VARINST` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_ID_GROUP`
--

DROP TABLE IF EXISTS `ACT_ID_GROUP`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_GROUP` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_ID_GROUP`
--

LOCK TABLES `ACT_ID_GROUP` WRITE;
/*!40000 ALTER TABLE `ACT_ID_GROUP` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_ID_GROUP` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_ID_INFO`
--

DROP TABLE IF EXISTS `ACT_ID_INFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_INFO` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `USER_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `VALUE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PASSWORD_` longblob,
  `PARENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_ID_INFO`
--

LOCK TABLES `ACT_ID_INFO` WRITE;
/*!40000 ALTER TABLE `ACT_ID_INFO` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_ID_INFO` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_ID_MEMBERSHIP`
--

DROP TABLE IF EXISTS `ACT_ID_MEMBERSHIP`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_MEMBERSHIP` (
  `USER_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `GROUP_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`USER_ID_`,`GROUP_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_ID_MEMBERSHIP`
--

LOCK TABLES `ACT_ID_MEMBERSHIP` WRITE;
/*!40000 ALTER TABLE `ACT_ID_MEMBERSHIP` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_ID_MEMBERSHIP` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_ID_USER`
--

DROP TABLE IF EXISTS `ACT_ID_USER`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_USER` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `FIRST_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LAST_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DISPLAY_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EMAIL_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PWD_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PICTURE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_ID_USER`
--

LOCK TABLES `ACT_ID_USER` WRITE;
/*!40000 ALTER TABLE `ACT_ID_USER` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_ID_USER` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_PROCDEF_INFO`
--

DROP TABLE IF EXISTS `ACT_PROCDEF_INFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_PROCDEF_INFO` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `INFO_JSON_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_INFO_PROCDEF` (`PROC_DEF_ID_`),
  KEY `ACT_IDX_INFO_PROCDEF` (`PROC_DEF_ID_`),
  KEY `ACT_FK_INFO_JSON_BA` (`INFO_JSON_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_PROCDEF_INFO`
--

LOCK TABLES `ACT_PROCDEF_INFO` WRITE;
/*!40000 ALTER TABLE `ACT_PROCDEF_INFO` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_PROCDEF_INFO` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RE_DEPLOYMENT`
--

DROP TABLE IF EXISTS `ACT_RE_DEPLOYMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RE_DEPLOYMENT` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  `DEPLOY_TIME_` timestamp(3) NULL DEFAULT NULL,
  `DERIVED_FROM_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DERIVED_FROM_ROOT_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_DEPLOYMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ENGINE_VERSION_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RE_DEPLOYMENT`
--

LOCK TABLES `ACT_RE_DEPLOYMENT` WRITE;
/*!40000 ALTER TABLE `ACT_RE_DEPLOYMENT` DISABLE KEYS */;
INSERT INTO `ACT_RE_DEPLOYMENT` VALUES ('43d2831c-42d6-11f1-a167-ea952063aef8','SpringBootAutoDeployment',NULL,NULL,'','2026-04-28 22:45:45.299',NULL,NULL,'43d2831c-42d6-11f1-a167-ea952063aef8',NULL);
/*!40000 ALTER TABLE `ACT_RE_DEPLOYMENT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RE_MODEL`
--

DROP TABLE IF EXISTS `ACT_RE_MODEL`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RE_MODEL` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LAST_UPDATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `VERSION_` int DEFAULT NULL,
  `META_INFO_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DEPLOYMENT_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EDITOR_SOURCE_VALUE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EDITOR_SOURCE_EXTRA_VALUE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_MODEL_SOURCE` (`EDITOR_SOURCE_VALUE_ID_`),
  KEY `ACT_FK_MODEL_SOURCE_EXTRA` (`EDITOR_SOURCE_EXTRA_VALUE_ID_`),
  KEY `ACT_FK_MODEL_DEPLOYMENT` (`DEPLOYMENT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RE_MODEL`
--

LOCK TABLES `ACT_RE_MODEL` WRITE;
/*!40000 ALTER TABLE `ACT_RE_MODEL` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RE_MODEL` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RE_PROCDEF`
--

DROP TABLE IF EXISTS `ACT_RE_PROCDEF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RE_PROCDEF` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `VERSION_` int NOT NULL,
  `DEPLOYMENT_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESOURCE_NAME_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DGRM_RESOURCE_NAME_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HAS_START_FORM_KEY_` tinyint DEFAULT NULL,
  `HAS_GRAPHICAL_NOTATION_` tinyint DEFAULT NULL,
  `SUSPENSION_STATE_` int DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  `ENGINE_VERSION_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DERIVED_FROM_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DERIVED_FROM_ROOT_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DERIVED_VERSION_` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_PROCDEF` (`KEY_`,`VERSION_`,`DERIVED_VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RE_PROCDEF`
--

LOCK TABLES `ACT_RE_PROCDEF` WRITE;
/*!40000 ALTER TABLE `ACT_RE_PROCDEF` DISABLE KEYS */;
INSERT INTO `ACT_RE_PROCDEF` VALUES ('440be2a5-42d6-11f1-a167-ea952063aef8',1,'http://www.bank.com/branch/platform/performance','零售分配关系调整审批','perf_alloc_adjust_retail_v1',1,'43d2831c-42d6-11f1-a167-ea952063aef8','perf_alloc_adjust_retail_v1.bpmn20.xml',NULL,NULL,0,0,1,'',NULL,NULL,NULL,0),('lead_approve_v1:1:440be2a4-42d6-11f1-a167-ea952063aef8',1,'http://www.bank.com/branch/platform/workflow','线索审批','lead_approve_v1',1,'43d2831c-42d6-11f1-a167-ea952063aef8','lead_approve_v1.bpmn20.xml',NULL,NULL,0,0,1,'',NULL,NULL,NULL,0),('loan_approve_v1:1:440be2a6-42d6-11f1-a167-ea952063aef8',1,'http://www.bank.com/branch/platform/workflow','资产投放审批','loan_approve_v1',1,'43d2831c-42d6-11f1-a167-ea952063aef8','loan_approve_v1.bpmn20.xml',NULL,NULL,0,0,1,'',NULL,NULL,NULL,0),('perf_alloc_adjust_corp_v1:1:440be2a3-42d6-11f1-a167-ea952063aef8',1,'http://www.bank.com/branch/platform/performance','对公分配关系调整审批','perf_alloc_adjust_corp_v1',1,'43d2831c-42d6-11f1-a167-ea952063aef8','perf_alloc_adjust_corp_v1.bpmn20.xml',NULL,NULL,0,0,1,'',NULL,NULL,NULL,0),('perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',1,'http://www.bank.com/branch/platform/performance','目标修正审批','perf_target_adjust_v1',1,'43d2831c-42d6-11f1-a167-ea952063aef8','perf_target_adjust_v1.bpmn20.xml',NULL,NULL,0,0,1,'',NULL,NULL,NULL,0);
/*!40000 ALTER TABLE `ACT_RE_PROCDEF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_ACTINST`
--

DROP TABLE IF EXISTS `ACT_RU_ACTINST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_ACTINST` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT '1',
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `ACT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALL_PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACT_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) NOT NULL,
  `END_TIME_` datetime(3) DEFAULT NULL,
  `DURATION_` bigint DEFAULT NULL,
  `TRANSACTION_ORDER_` int DEFAULT NULL,
  `DELETE_REASON_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_RU_ACTI_START` (`START_TIME_`),
  KEY `ACT_IDX_RU_ACTI_END` (`END_TIME_`),
  KEY `ACT_IDX_RU_ACTI_PROC` (`PROC_INST_ID_`),
  KEY `ACT_IDX_RU_ACTI_PROC_ACT` (`PROC_INST_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_RU_ACTI_EXEC` (`EXECUTION_ID_`),
  KEY `ACT_IDX_RU_ACTI_EXEC_ACT` (`EXECUTION_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_RU_ACTI_TASK` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_ACTINST`
--

LOCK TABLES `ACT_RU_ACTINST` WRITE;
/*!40000 ALTER TABLE `ACT_RU_ACTINST` DISABLE KEYS */;
INSERT INTO `ACT_RU_ACTINST` VALUES ('3c82adde-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:32:23.239','2026-04-29 22:32:23.244',5,1,NULL,''),('3c83983f-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:32:23.245','2026-04-29 22:32:23.245',0,2,NULL,''),('3c839840-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','3c82addd-43d8-11f1-953f-ea952063aef8','branch_mgr_review','3c851ee1-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:32:23.245',NULL,NULL,3,NULL,''),('530ca349-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:33:01.053','2026-04-29 22:33:01.054',1,1,NULL,''),('530cca5a-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:33:01.054','2026-04-29 22:33:01.054',0,2,NULL,''),('530cca5b-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','530ca348-43d8-11f1-953f-ea952063aef8','branch_mgr_review','530cca5c-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:33:01.054',NULL,NULL,3,NULL,''),('6a7ade34-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:33:40.363','2026-04-29 22:33:40.363',0,1,NULL,''),('6a7ade35-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:33:40.363','2026-04-29 22:33:40.363',0,2,NULL,''),('6a7ade36-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ade33-43d8-11f1-953f-ea952063aef8','branch_mgr_review','6a7ade37-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:33:40.363',NULL,NULL,3,NULL,''),('6dc6d4ef-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894',0,1,NULL,''),('6dc6d4f0-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:33:45.894','2026-04-29 22:33:45.894',0,2,NULL,''),('6dc6d4f1-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6d4ee-43d8-11f1-953f-ea952063aef8','branch_mgr_review','6dc6d4f2-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:33:45.894',NULL,NULL,3,NULL,''),('7c81475a-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8','start',NULL,NULL,'开始','startEvent',NULL,'2026-04-29 22:34:10.604','2026-04-29 22:34:10.604',0,1,NULL,''),('7c81475b-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8','flow_start_branch',NULL,NULL,NULL,'sequenceFlow',NULL,'2026-04-29 22:34:10.604','2026-04-29 22:34:10.604',0,2,NULL,''),('7c81475c-43d8-11f1-953f-ea952063aef8',1,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','7c814759-43d8-11f1-953f-ea952063aef8','branch_mgr_review','7c81475d-43d8-11f1-953f-ea952063aef8',NULL,'支行主管审批','userTask',NULL,'2026-04-29 22:34:10.604',NULL,NULL,3,NULL,'');
/*!40000 ALTER TABLE `ACT_RU_ACTINST` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_DEADLETTER_JOB`
--

DROP TABLE IF EXISTS `ACT_RU_DEADLETTER_JOB`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_DEADLETTER_JOB` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_DJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_DJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_DJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_DEADLETTER_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_DEADLETTER_JOB_PROC_DEF` (`PROC_DEF_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_DEADLETTER_JOB`
--

LOCK TABLES `ACT_RU_DEADLETTER_JOB` WRITE;
/*!40000 ALTER TABLE `ACT_RU_DEADLETTER_JOB` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_DEADLETTER_JOB` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_ENTITYLINK`
--

DROP TABLE IF EXISTS `ACT_RU_ENTITYLINK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_ENTITYLINK` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CREATE_TIME_` datetime(3) DEFAULT NULL,
  `LINK_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REF_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REF_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REF_SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ROOT_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ROOT_SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HIERARCHY_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_ENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_REF_SCOPE` (`REF_SCOPE_ID_`,`REF_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_ROOT_SCOPE` (`ROOT_SCOPE_ID_`,`ROOT_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_ENTITYLINK`
--

LOCK TABLES `ACT_RU_ENTITYLINK` WRITE;
/*!40000 ALTER TABLE `ACT_RU_ENTITYLINK` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_ENTITYLINK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_EVENT_SUBSCR`
--

DROP TABLE IF EXISTS `ACT_RU_EVENT_SUBSCR`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_EVENT_SUBSCR` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `EVENT_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `EVENT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACTIVITY_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CONFIGURATION_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATED_` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EVENT_SUBSCR_CONFIG_` (`CONFIGURATION_`),
  KEY `ACT_IDX_EVENT_SUBSCR_SCOPEREF_` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_EVENT_EXEC` (`EXECUTION_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_EVENT_SUBSCR`
--

LOCK TABLES `ACT_RU_EVENT_SUBSCR` WRITE;
/*!40000 ALTER TABLE `ACT_RU_EVENT_SUBSCR` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_EVENT_SUBSCR` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_EXECUTION`
--

DROP TABLE IF EXISTS `ACT_RU_EXECUTION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_EXECUTION` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BUSINESS_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUPER_EXEC_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ROOT_PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ACT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IS_ACTIVE_` tinyint DEFAULT NULL,
  `IS_CONCURRENT_` tinyint DEFAULT NULL,
  `IS_SCOPE_` tinyint DEFAULT NULL,
  `IS_EVENT_SCOPE_` tinyint DEFAULT NULL,
  `IS_MI_ROOT_` tinyint DEFAULT NULL,
  `SUSPENSION_STATE_` int DEFAULT NULL,
  `CACHED_ENT_STATE_` int DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_ACT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `START_TIME_` datetime(3) DEFAULT NULL,
  `START_USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
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
  `CALLBACK_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CALLBACK_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REFERENCE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BUSINESS_STATUS_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EXEC_BUSKEY` (`BUSINESS_KEY_`),
  KEY `ACT_IDC_EXEC_ROOT` (`ROOT_PROC_INST_ID_`),
  KEY `ACT_IDX_EXEC_REF_ID_` (`REFERENCE_ID_`),
  KEY `ACT_FK_EXE_PROCINST` (`PROC_INST_ID_`),
  KEY `ACT_FK_EXE_PARENT` (`PARENT_ID_`),
  KEY `ACT_FK_EXE_SUPER` (`SUPER_EXEC_`),
  KEY `ACT_FK_EXE_PROCDEF` (`PROC_DEF_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_EXECUTION`
--

LOCK TABLES `ACT_RU_EXECUTION` WRITE;
/*!40000 ALTER TABLE `ACT_RU_EXECUTION` DISABLE KEYS */;
INSERT INTO `ACT_RU_EXECUTION` VALUES ('3c8286c7-43d8-11f1-953f-ea952063aef8',1,'3c8286c7-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:3d84ba504ec944b09c31cd8a1bd0f923',NULL,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,1,0,1,0,0,1,NULL,'',NULL,'start','2026-04-29 22:32:23.238',NULL,NULL,NULL,1,0,0,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('3c82addd-43d8-11f1-953f-ea952063aef8',1,'3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,'3c8286c7-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'3c8286c7-43d8-11f1-953f-ea952063aef8','branch_mgr_review',1,0,0,0,0,1,NULL,'',NULL,NULL,'2026-04-29 22:32:23.239',NULL,NULL,NULL,1,0,1,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('530ca342-43d8-11f1-953f-ea952063aef8',1,'530ca342-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:c45910ecbfd049dda8eb825aece3a460',NULL,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'530ca342-43d8-11f1-953f-ea952063aef8',NULL,1,0,1,0,0,1,NULL,'',NULL,'start','2026-04-29 22:33:01.053',NULL,NULL,NULL,1,0,0,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('530ca348-43d8-11f1-953f-ea952063aef8',1,'530ca342-43d8-11f1-953f-ea952063aef8',NULL,'530ca342-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'530ca342-43d8-11f1-953f-ea952063aef8','branch_mgr_review',1,0,0,0,0,1,NULL,'',NULL,NULL,'2026-04-29 22:33:01.053',NULL,NULL,NULL,1,0,1,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('6a7ab71d-43d8-11f1-953f-ea952063aef8',1,'6a7ab71d-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:e89ba10c2e534064a1b6f0b28eabc36c',NULL,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,1,0,1,0,0,1,NULL,'',NULL,'start','2026-04-29 22:33:40.362',NULL,NULL,NULL,1,0,0,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('6a7ade33-43d8-11f1-953f-ea952063aef8',1,'6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,'6a7ab71d-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'6a7ab71d-43d8-11f1-953f-ea952063aef8','branch_mgr_review',1,0,0,0,0,1,NULL,'',NULL,NULL,'2026-04-29 22:33:40.363',NULL,NULL,NULL,1,0,1,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('6dc6add8-43d8-11f1-953f-ea952063aef8',1,'6dc6add8-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:60b1c0575fa2408ebfc8f07a769cc6ca',NULL,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,1,0,1,0,0,1,NULL,'',NULL,'start','2026-04-29 22:33:45.893',NULL,NULL,NULL,1,0,0,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('6dc6d4ee-43d8-11f1-953f-ea952063aef8',1,'6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,'6dc6add8-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'6dc6add8-43d8-11f1-953f-ea952063aef8','branch_mgr_review',1,0,0,0,0,1,NULL,'',NULL,NULL,'2026-04-29 22:33:45.894',NULL,NULL,NULL,1,0,1,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('7c812043-43d8-11f1-953f-ea952063aef8',1,'7c812043-43d8-11f1-953f-ea952063aef8','TARGET_ADJUST:e80eed6ac527417389eef8841b527d24',NULL,'perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'7c812043-43d8-11f1-953f-ea952063aef8',NULL,1,0,1,0,0,1,NULL,'',NULL,'start','2026-04-29 22:34:10.603',NULL,NULL,NULL,1,0,0,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),('7c814759-43d8-11f1-953f-ea952063aef8',1,'7c812043-43d8-11f1-953f-ea952063aef8',NULL,'7c812043-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,'7c812043-43d8-11f1-953f-ea952063aef8','branch_mgr_review',1,0,0,0,0,1,NULL,'',NULL,NULL,'2026-04-29 22:34:10.604',NULL,NULL,NULL,1,0,1,0,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `ACT_RU_EXECUTION` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_EXTERNAL_JOB`
--

DROP TABLE IF EXISTS `ACT_RU_EXTERNAL_JOB`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_EXTERNAL_JOB` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_EJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_EXTERNAL_JOB`
--

LOCK TABLES `ACT_RU_EXTERNAL_JOB` WRITE;
/*!40000 ALTER TABLE `ACT_RU_EXTERNAL_JOB` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_EXTERNAL_JOB` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_HISTORY_JOB`
--

DROP TABLE IF EXISTS `ACT_RU_HISTORY_JOB`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_HISTORY_JOB` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ADV_HANDLER_CFG_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_HISTORY_JOB`
--

LOCK TABLES `ACT_RU_HISTORY_JOB` WRITE;
/*!40000 ALTER TABLE `ACT_RU_HISTORY_JOB` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_HISTORY_JOB` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_IDENTITYLINK`
--

DROP TABLE IF EXISTS `ACT_RU_IDENTITYLINK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_IDENTITYLINK` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `GROUP_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_IDENT_LNK_USER` (`USER_ID_`),
  KEY `ACT_IDX_IDENT_LNK_GROUP` (`GROUP_ID_`),
  KEY `ACT_IDX_IDENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_IDENT_LNK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_IDENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_ATHRZ_PROCEDEF` (`PROC_DEF_ID_`),
  KEY `ACT_FK_TSKASS_TASK` (`TASK_ID_`),
  KEY `ACT_FK_IDL_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_IDENTITYLINK`
--

LOCK TABLES `ACT_RU_IDENTITYLINK` WRITE;
/*!40000 ALTER TABLE `ACT_RU_IDENTITYLINK` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_IDENTITYLINK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_JOB`
--

DROP TABLE IF EXISTS `ACT_RU_JOB`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_JOB` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_JOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_JOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_JOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_JOB_PROC_DEF` (`PROC_DEF_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_JOB`
--

LOCK TABLES `ACT_RU_JOB` WRITE;
/*!40000 ALTER TABLE `ACT_RU_JOB` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_JOB` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_SUSPENDED_JOB`
--

DROP TABLE IF EXISTS `ACT_RU_SUSPENDED_JOB`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_SUSPENDED_JOB` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_SJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_SJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_SJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_SUSPENDED_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_SUSPENDED_JOB_PROC_DEF` (`PROC_DEF_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_SUSPENDED_JOB`
--

LOCK TABLES `ACT_RU_SUSPENDED_JOB` WRITE;
/*!40000 ALTER TABLE `ACT_RU_SUSPENDED_JOB` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_SUSPENDED_JOB` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_TASK`
--

DROP TABLE IF EXISTS `ACT_RU_TASK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_TASK` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROPAGATED_STAGE_INST_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STATE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PARENT_TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DESCRIPTION_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_DEF_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ASSIGNEE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DELEGATION_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PRIORITY_` int DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `IN_PROGRESS_TIME_` datetime(3) DEFAULT NULL,
  `IN_PROGRESS_STARTED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CLAIM_TIME_` datetime(3) DEFAULT NULL,
  `CLAIMED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUSPENDED_TIME_` datetime(3) DEFAULT NULL,
  `SUSPENDED_BY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IN_PROGRESS_DUE_DATE_` datetime(3) DEFAULT NULL,
  `DUE_DATE_` datetime(3) DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUSPENSION_STATE_` int DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  `FORM_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
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
  KEY `ACT_FK_TASK_PROCDEF` (`PROC_DEF_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_TASK`
--

LOCK TABLES `ACT_RU_TASK` WRITE;
/*!40000 ALTER TABLE `ACT_RU_TASK` DISABLE KEYS */;
INSERT INTO `ACT_RU_TASK` VALUES ('3c851ee1-43d8-11f1-953f-ea952063aef8',1,'3c82addd-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,'branch_mgr_review',NULL,NULL,NULL,50,'2026-04-30 05:32:23.245',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'',NULL,1,0,0,0),('530cca5c-43d8-11f1-953f-ea952063aef8',1,'530ca348-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,'branch_mgr_review',NULL,NULL,NULL,50,'2026-04-30 05:33:01.054',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'',NULL,1,0,0,0),('6a7ade37-43d8-11f1-953f-ea952063aef8',1,'6a7ade33-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,'branch_mgr_review',NULL,NULL,NULL,50,'2026-04-30 05:33:40.363',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'',NULL,1,0,0,0),('6dc6d4f2-43d8-11f1-953f-ea952063aef8',1,'6dc6d4ee-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,'branch_mgr_review',NULL,NULL,NULL,50,'2026-04-30 05:33:45.894',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'',NULL,1,0,0,0),('7c81475d-43d8-11f1-953f-ea952063aef8',1,'7c814759-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8','perf_target_adjust_v1:1:440b4662-42d6-11f1-a167-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,'created','支行主管审批',NULL,NULL,'branch_mgr_review',NULL,NULL,NULL,50,'2026-04-30 05:34:10.604',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'',NULL,1,0,0,0);
/*!40000 ALTER TABLE `ACT_RU_TASK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_TIMER_JOB`
--

DROP TABLE IF EXISTS `ACT_RU_TIMER_JOB`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_TIMER_JOB` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL,
  `LOCK_OWNER_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROCESS_INSTANCE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_DEF_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ELEMENT_NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_DEFINITION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CORRELATION_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RETRIES_` int DEFAULT NULL,
  `EXCEPTION_STACK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `EXCEPTION_MSG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL,
  `REPEAT_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `HANDLER_CFG_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CUSTOM_VALUES_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
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
  KEY `ACT_FK_TIMER_JOB_PROC_DEF` (`PROC_DEF_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_TIMER_JOB`
--

LOCK TABLES `ACT_RU_TIMER_JOB` WRITE;
/*!40000 ALTER TABLE `ACT_RU_TIMER_JOB` DISABLE KEYS */;
/*!40000 ALTER TABLE `ACT_RU_TIMER_JOB` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ACT_RU_VARIABLE`
--

DROP TABLE IF EXISTS `ACT_RU_VARIABLE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_VARIABLE` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `EXECUTION_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BYTEARRAY_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `DOUBLE_` double DEFAULT NULL,
  `LONG_` bigint DEFAULT NULL,
  `TEXT_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TEXT2_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `META_INFO_` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_RU_VAR_SCOPE_ID_TYPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_RU_VAR_SUB_ID_TYPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_VAR_BYTEARRAY` (`BYTEARRAY_ID_`),
  KEY `ACT_IDX_VARIABLE_TASK_ID` (`TASK_ID_`),
  KEY `ACT_FK_VAR_EXE` (`EXECUTION_ID_`),
  KEY `ACT_FK_VAR_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ACT_RU_VARIABLE`
--

LOCK TABLES `ACT_RU_VARIABLE` WRITE;
/*!40000 ALTER TABLE `ACT_RU_VARIABLE` DISABLE KEYS */;
INSERT INTO `ACT_RU_VARIABLE` VALUES ('3c8286c8-43d8-11f1-953f-ea952063aef8',1,'string','applyId','3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'3d84ba504ec944b09c31cd8a1bd0f923',NULL,NULL),('3c82add9-43d8-11f1-953f-ea952063aef8',1,'string','planId','3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1485fe155bb54215851902a43289ddbc',NULL,NULL),('3c82adda-43d8-11f1-953f-ea952063aef8',1,'string','subjectType','3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL),('3c82addb-43d8-11f1-953f-ea952063aef8',1,'string','subjectId','3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL),('3c82addc-43d8-11f1-953f-ea952063aef8',1,'string','cycleKey','3c8286c7-43d8-11f1-953f-ea952063aef8','3c8286c7-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL),('530ca343-43d8-11f1-953f-ea952063aef8',1,'string','applyId','530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'c45910ecbfd049dda8eb825aece3a460',NULL,NULL),('530ca344-43d8-11f1-953f-ea952063aef8',1,'string','planId','530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'ea2ffb678bb0410898621c4da509b57c',NULL,NULL),('530ca345-43d8-11f1-953f-ea952063aef8',1,'string','subjectType','530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL),('530ca346-43d8-11f1-953f-ea952063aef8',1,'string','subjectId','530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL),('530ca347-43d8-11f1-953f-ea952063aef8',1,'string','cycleKey','530ca342-43d8-11f1-953f-ea952063aef8','530ca342-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL),('6a7ab71e-43d8-11f1-953f-ea952063aef8',1,'string','applyId','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'e89ba10c2e534064a1b6f0b28eabc36c',NULL,NULL),('6a7ab71f-43d8-11f1-953f-ea952063aef8',1,'string','planId','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'f8971db0c2c64fca887321b0cd15455f',NULL,NULL),('6a7ab720-43d8-11f1-953f-ea952063aef8',1,'string','subjectType','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL),('6a7ab721-43d8-11f1-953f-ea952063aef8',1,'string','subjectId','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL),('6a7ade32-43d8-11f1-953f-ea952063aef8',1,'string','cycleKey','6a7ab71d-43d8-11f1-953f-ea952063aef8','6a7ab71d-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL),('6dc6d4e9-43d8-11f1-953f-ea952063aef8',1,'string','applyId','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'60b1c0575fa2408ebfc8f07a769cc6ca',NULL,NULL),('6dc6d4ea-43d8-11f1-953f-ea952063aef8',1,'string','planId','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'6ec77945a8e342028c420645a9dba947',NULL,NULL),('6dc6d4eb-43d8-11f1-953f-ea952063aef8',1,'string','subjectType','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL),('6dc6d4ec-43d8-11f1-953f-ea952063aef8',1,'string','subjectId','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL),('6dc6d4ed-43d8-11f1-953f-ea952063aef8',1,'string','cycleKey','6dc6add8-43d8-11f1-953f-ea952063aef8','6dc6add8-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL),('7c812044-43d8-11f1-953f-ea952063aef8',1,'string','applyId','7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'e80eed6ac527417389eef8841b527d24',NULL,NULL),('7c812045-43d8-11f1-953f-ea952063aef8',1,'string','planId','7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'9bfab7fbfd474abb93f1731e8d12f88d',NULL,NULL),('7c812046-43d8-11f1-953f-ea952063aef8',1,'string','subjectType','7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'EMP',NULL,NULL),('7c812047-43d8-11f1-953f-ea952063aef8',1,'string','subjectId','7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL),('7c812048-43d8-11f1-953f-ea952063aef8',1,'string','cycleKey','7c812043-43d8-11f1-953f-ea952063aef8','7c812043-43d8-11f1-953f-ea952063aef8',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'202604',NULL,NULL);
/*!40000 ALTER TABLE `ACT_RU_VARIABLE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ADDRBOOK_EMPLOYEE`
--

DROP TABLE IF EXISTS `ADDRBOOK_EMPLOYEE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ADDRBOOK_EMPLOYEE` (
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
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '删除标记：0-未删除, 1-已删除',
  PRIMARY KEY (`emp_id`),
  KEY `idx_org_code` (`org_code`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通讯录员工表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ADDRBOOK_EMPLOYEE`
--

LOCK TABLES `ADDRBOOK_EMPLOYEE` WRITE;
/*!40000 ALTER TABLE `ADDRBOOK_EMPLOYEE` DISABLE KEYS */;
INSERT INTO `ADDRBOOK_EMPLOYEE` VALUES ('TEST_E001','员工 TEST_E001','13800000001',NULL,'ORG_SZ_001','深圳分行','客户经理',NULL,'[\"P001\",\"P002\"]','ACTIVE',NULL,'2026-04-29 08:04:04','2026-04-29 08:04:04',0);
/*!40000 ALTER TABLE `ADDRBOOK_EMPLOYEE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `AUDIT_LOG`
--

DROP TABLE IF EXISTS `AUDIT_LOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `AUDIT_LOG` (
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
-- Dumping data for table `AUDIT_LOG`
--

LOCK TABLES `AUDIT_LOG` WRITE;
/*!40000 ALTER TABLE `AUDIT_LOG` DISABLE KEYS */;
INSERT INTO `AUDIT_LOG` VALUES ('006c680e530e42a1bb613fb758371eb4',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:34'),('018c29d424bd49ccadd75ff42fc993b1',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_WITHDRAW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:23'),('040f8b2d334944149de61f60e4efd92c',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('04a1dc54522e4acbbe32a0599d6a539e',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:07'),('064b8c48a87349c4a4009fa0cb9c78bf',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:41'),('07aa9b308ca344dfa132f352d96cbc0f',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:19'),('0dd37ed00bdc4330853b699b3a4acd5d',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:17'),('0ee4db34cac149228f3dd3c9b1ed69f9',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:17'),('116925c482094a67bf36824f53bee4b1',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:09'),('136ebce430be403c98ed708e0461c4c6',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:43'),('15a2a8794c47431597247c27c0c0c007',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:09'),('1648b509f8dd4032826da1e0c480c20b',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('167a45497d2a41ca9024d3f5c98685e2',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:40'),('17053e408d444ecabbd643beb0b35465',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:37'),('17f483da23e749f6a06f2f240c2f5d8a',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('1a4b0c28569148919209462a11dd371c',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:24'),('1ded4ddfbf144dc3a0645da7c3043ea2',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:11'),('1f5150f7e8d1417b8ab4ed0daf996253',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('1fbbd9d9d05b4740a01af9c4cb845c05',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('1fdd8297b732475685a7ad0f6a20d22c',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('201f1f98a6204d41b79f615eb9b24cd6',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('2157261b4b3b43ac9263be837b42a8a9',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:57'),('242dab3c435543ab8054cf754ebb8d5a',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:37'),('2462b2ba29a445a98e920fdd65ae9477',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:39'),('251e3c037cef47f4840fd1a94a0f22c9',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('267cc38499084abbaa95de6074ee43bc',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:07'),('26e72d01308841a3982e277848d51d05',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('2758f3cdab7342e482c61c28ff92e181',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:58'),('29263dd1758648caa41ced4fb5d3358f',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:37'),('293dd39b01b7492587e3b2380788c36c',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:02'),('2a009d26b3c24d8098b4616aca2de155',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:01'),('2cab35bb9b024bc19d8afa912f8fb5be',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:58'),('2cdbd05b756f4157bd0540fda2ea4d2e',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:15'),('2dc786fc7ce24e99af5835bc6c83efc0',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:40'),('2de860b2cc844d609a9aab8d4369811d',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('2ecd1c11bba24f918f05504787aa22b3',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:18'),('2f94ca7e22db4e1790ab3d7ce74ecbef',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('328bbbcb4e574b2a8909d40af56aea5d',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:31'),('34d8907ffe4b425c9518a2369fe69bfe',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:37'),('362d366989cb4a32aad0220eff57b918',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('36b2398898514b9aabe7bf5684bfdfbd',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:19'),('3729228f9ded477ca131560726dba928',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:39'),('3b6796a0a76c4aa8aa8ad00bcb3d0ed9',NULL,'admin',NULL,'TARGET_PLAN','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('3ca13a5418e8415c8fa37826c0b3c7af',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_WITHDRAW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:11'),('3d320396e522471a87dfa6e5da5bd8af',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:42'),('3d9b847d7a6f47518f8dfcf71d79efab',NULL,'admin',NULL,'TARGET_PLAN','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:17'),('422b00b778dd423c8fcb73cf8ea38a34',NULL,'admin',NULL,'PERF_RUN_TASK','PERF_RECALC',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:39'),('449f3059d02e4edd951f4f4868a39b5f',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:36'),('44c3e925617546c4add650c19d6cad01',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:14'),('456cce3dc682443c98b1885e1f950902',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:32'),('45a54d17e7d44cf3af3102b6edc477fe',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('46877e0fdd0149269cefff40318df75a',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:23'),('468ff99a8ecb4ad59d35c711692a3567',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('4f4cec43f2b34e3483ba82747727bf7e',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:07'),('53ddb823a2144e7792e3d9bc5509c954',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('541358757a144836ac585d438784d0eb',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:42'),('55a444e926e34389bbe3a6536f4efc94',NULL,'admin',NULL,'TARGET_PLAN','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:09'),('55df1f7fb6014058900c5d409d3433b4',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('57c2098fec494fcd990d20e4e78d315b',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('57efb0fd7c6948ba83145016f1f5d68a',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:20'),('59f755074ef14685b9e795d96d7fdf36',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:37'),('5a28697829e54c9b9d283f044f61843d',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('5aca5aa4571d4c878c0c53c0613d7d21',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('5b87baa99832467cb25cbd75e1ddb3d8',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:36'),('5cb9f884255d4e4e8617871b56c5f310',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:32'),('5cd70e8aa4fd462a8d157e7be84b0152',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_WITHDRAW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:41'),('5e42c1ba3ba04b82b82230ed02cfad99',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:37'),('60b4535168d441e79b3872de5aa3c015',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:15'),('637b7e9de6cc4842aa179038fac48374',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:43'),('63e5092319ac4850a60e17dfeb399dd8',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:10'),('643ac9d366ef4cc9b46703382a05c003',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:02'),('65e63400bbe04b239bb57f8eeb650965',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:19'),('67a86ab7a2954c49be6a3224bfa02df3',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:38'),('6836fdfa261f471da7fd61fba5de7bdf',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:57'),('6b1b526ed6b4418284f5210bc7435942',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('6fa35e70591f435983b4f4d0dcfd4fef',NULL,'admin',NULL,'TARGET_PLAN','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('6fd69001adc5409abb9934f6e34ca6f5',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('707b3d11dfa64403a5c5b2862ad5062d',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('70bd61f634984771881391047e49711c',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:46'),('712cd3abdc6f4ec38543df915d1083ed',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:57'),('7258cd27b60d4bb2ac0c077225cd30d0',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:35'),('744d806fce024708a13796f608e2ba61',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:37'),('7952a760bb9b42d9a57dba15b6e67209',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:09'),('7c7276b8f1994943a0bd99f0c0ac489c',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:24'),('7ea02c57bd6a48ddadaf2ac5ec33de93',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('7ec37f02a7164ea09c77dd4deb252aa9',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('7f1ba0ed302549b2821b6c3eb0a01a7c',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:22'),('7f368f48f9bc4b4b85fb974ecbf8b5ae',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:19'),('82a1c940727041b298ee3a3d59859fa5',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:37'),('83286ea268d845908b14bfd173ec40b9',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:20'),('85278e8224874688afac07245ce63372',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:57'),('85d9712989384fe6ba49b68eaeb21133',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:37'),('86885ac4b5af4f55bfea1cf6d2274812',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:07'),('870fac3577c84f7381672fb0e44d7962',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:47'),('888621f641a746f48ab464aa1f93ceaf',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('8aadc7b1d2204db897c57ebf28795023',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:14'),('8af8dd2951c347ff8e3319f415b91ab5',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:19'),('8bf02967a0034952853348577cc40a2a',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:40'),('8da5139a11e54f698abf35d6ba05a5cd',NULL,'admin',NULL,'METRIC_DEF','SLOT_RELEASE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:43'),('8fe26bcd249c45a7aa5ff4770bc981f0',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_WITHDRAW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:01'),('90bd2145634a4aeb8b980179c3460cb3',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('94ac07727edf4375a5095a1c18b90586',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('96bd3230742d444db6104110cefe4531',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('9a5ba978185641c6a7f41cde0c141e87',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:21:40'),('9af50e87c20b46549b7bcfda3f42f48a',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_WITHDRAW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:46'),('a22f6c998d4a49e9a847f6371480bd8b',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:15'),('a4ee1483162444f099ac1c0c499481a3',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('a7d1dc38fbbb44ee906d5378b9c06c5c',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('ad0f7687546f474cbceffd6e7677787a',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:36'),('afdcd90e5dbf4e12acf6fe023d55ef89',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('affa9856688c484fa8007bbfe0789395',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('b01ada39e2344ee591ab2471229a2014',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('b24d66433c2c4b10b4ef80bc1d84fbcb',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:43'),('b3309daa49da413391687db7870e118b',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:47'),('b463c171c59649efb7ec02b0e303fa65',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:22'),('b54a3a07e13840e5b68f83c9ec06a443',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:42'),('b7f225e6baf6474e9ecbf271ce2162d0',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:57'),('b9296b82b1774bbaafecc741f679e6ee',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:07'),('bc76c19b145448bba5d58d90612177fc',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('bf1f0541bb824b83937a234180946286',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('bfe6d181c49945988c8fc8aeaa70fb24',NULL,'admin',NULL,'TARGET_PLAN','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('c0a76ddc5118458ba0afc9697fcc0687',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:58'),('c34ead20e1e142ffb40a742837e7e013',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:31'),('c6ba3fefc6d14ee183265fef175d884d',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:43'),('c6f3781fdbb548f48d5a99273448bc44',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:11'),('ccb87ede64134a268f682e7ef4ecf418',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:36'),('cd5d4ffffc1c487c8fd4e1bbe780935b',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:11'),('ce160bc52da541cebe5913b29931b0db',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('cf0b140103cc46dca64e1cc35887c7e3',NULL,'admin',NULL,'TARGET_VALUE','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:44'),('cf77db5df0c24e4aa53417bef33b1386',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:45'),('d355c8447b4e4e7dbf38b2ac0d65116f',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:24'),('d3620972c14847dbab38127301ab0fa3',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:16'),('d4912da4651046748c1cde13ea3cf72e',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:41'),('d5de5096af04437a823b35faea8b31a6',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:19'),('d7751ead8c5b4f599913ff58afe701df',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:47'),('d8f9817df5be4172829cbe2174ba90a6',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:25:31'),('d9d6d297946f42bd8c133fa9d4623508',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:41'),('dfe195b84c344f2f96e668b44d5d1671',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:36'),('e297b68a27ab49f3acffc8fe8d572ae3',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:38'),('e39e9397e49a42e3a28eb17e351a855a',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:00'),('e3c0f6094ddb43999e57d6d9dba220ed',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:22'),('e5266c99dcc1419e9562e8a70fce4249',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:19'),('e59ef7be7baa40f3ba9e7fc235065a3d',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('e5fed3f7c0b7413ab352424ed7504fdf',NULL,'admin',NULL,'KPI_SCHEME','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('e886150cd8164344a998bcfa43641dbf',NULL,'admin',NULL,'TARGET_PLAN','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:21'),('e8ad406b369f4d9b969c6408452dca98',NULL,'admin',NULL,'PERF_TARGET_ADJUST','TARGET_ADJUST_CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:11'),('ec0f1ae0664c4267bab608d80f12cc33',NULL,'admin',NULL,'KPI_SCHEME','DELETE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:31:19'),('f01b66abaa1a4c76a81616e83cbb81ef',NULL,'admin',NULL,'METRIC_DEF','STATUS_CHANGE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:02'),('f6b99bc1c4cf4ab98e2cd4ba3980dc74',NULL,'admin',NULL,'TARGET_PLAN','UPDATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:32:59'),('f8d5efd772e0420fa79446abbaad6701',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:42'),('f97e586a9cec4b709e9f65222aa138e4',NULL,'admin',NULL,'KPI_SCHEME','CREATE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:34:08'),('fcca331e0772442396baf871130f0f55',NULL,'admin',NULL,'SYS_CONTROL','INIT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:37'),('fe1faa3065c94501827366b4541cb37b',NULL,'admin',NULL,'KPI_SCHEME','PUBLISH',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-29 07:33:42');
/*!40000 ALTER TABLE `AUDIT_LOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `BIZ_FILE_REL`
--

DROP TABLE IF EXISTS `BIZ_FILE_REL`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `BIZ_FILE_REL` (
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
-- Dumping data for table `BIZ_FILE_REL`
--

LOCK TABLES `BIZ_FILE_REL` WRITE;
/*!40000 ALTER TABLE `BIZ_FILE_REL` DISABLE KEYS */;
/*!40000 ALTER TABLE `BIZ_FILE_REL` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `BIZ_PROCESS_MAP`
--

DROP TABLE IF EXISTS `BIZ_PROCESS_MAP`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `BIZ_PROCESS_MAP` (
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
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程标题',
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
-- Dumping data for table `BIZ_PROCESS_MAP`
--

LOCK TABLES `BIZ_PROCESS_MAP` WRITE;
/*!40000 ALTER TABLE `BIZ_PROCESS_MAP` DISABLE KEYS */;
INSERT INTO `BIZ_PROCESS_MAP` VALUES ('6fb39a223e0a461abba518c4f6492eb3','TARGET_ADJUST:e80eed6ac527417389eef8841b527d24','TARGET_ADJUST','e80eed6ac527417389eef8841b527d24','perf_target_adjust_v1','7c812043-43d8-11f1-953f-ea952063aef8','admin',NULL,NULL,'RUNNING','目标修正-EMP-admin-TA202604294310E62D','2026-04-29 07:34:11',NULL,'2026-04-29 07:34:11','2026-04-29 07:34:11'),('92201e6604834037a5b94599786cc116','TARGET_ADJUST:3d84ba504ec944b09c31cd8a1bd0f923','TARGET_ADJUST','3d84ba504ec944b09c31cd8a1bd0f923','perf_target_adjust_v1','3c8286c7-43d8-11f1-953f-ea952063aef8','admin',NULL,NULL,'RUNNING','目标修正-EMP-admin-TA202604298ABDB86E','2026-04-29 07:32:23',NULL,'2026-04-29 07:32:23','2026-04-29 07:32:23'),('d8712e7157564eacb4c98f12e3a1960f','TARGET_ADJUST:e89ba10c2e534064a1b6f0b28eabc36c','TARGET_ADJUST','e89ba10c2e534064a1b6f0b28eabc36c','perf_target_adjust_v1','6a7ab71d-43d8-11f1-953f-ea952063aef8','admin',NULL,NULL,'RUNNING','目标修正-EMP-admin-TA202604296FCE3B0B','2026-04-29 07:33:40',NULL,'2026-04-29 07:33:40','2026-04-29 07:33:40'),('ebadaa19d33f443bbad8e86e3c8d3f49','TARGET_ADJUST:c45910ecbfd049dda8eb825aece3a460','TARGET_ADJUST','c45910ecbfd049dda8eb825aece3a460','perf_target_adjust_v1','530ca342-43d8-11f1-953f-ea952063aef8','admin',NULL,NULL,'RUNNING','目标修正-EMP-admin-TA202604297FD4D345','2026-04-29 07:33:01',NULL,'2026-04-29 07:33:01','2026-04-29 07:33:01'),('ecfa679b11a84ec89a943ce7c9e7d818','TARGET_ADJUST:60b1c0575fa2408ebfc8f07a769cc6ca','TARGET_ADJUST','60b1c0575fa2408ebfc8f07a769cc6ca','perf_target_adjust_v1','6dc6add8-43d8-11f1-953f-ea952063aef8','admin',NULL,NULL,'RUNNING','目标修正-EMP-admin-TA20260429624C4430','2026-04-29 07:33:46',NULL,'2026-04-29 07:33:46','2026-04-29 07:33:46');
/*!40000 ALTER TABLE `BIZ_PROCESS_MAP` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_ALLOC_RELATION`
--

DROP TABLE IF EXISTS `CUST_ALLOC_RELATION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_ALLOC_RELATION` (
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
-- Dumping data for table `CUST_ALLOC_RELATION`
--

LOCK TABLES `CUST_ALLOC_RELATION` WRITE;
/*!40000 ALTER TABLE `CUST_ALLOC_RELATION` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_ALLOC_RELATION` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_CLAIM`
--

DROP TABLE IF EXISTS `CUST_CLAIM`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_CLAIM` (
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
-- Dumping data for table `CUST_CLAIM`
--

LOCK TABLES `CUST_CLAIM` WRITE;
/*!40000 ALTER TABLE `CUST_CLAIM` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_CLAIM` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_INDEX_RESULT`
--

DROP TABLE IF EXISTS `CUST_INDEX_RESULT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_INDEX_RESULT` (
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户指标结果宽表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `CUST_INDEX_RESULT`
--

LOCK TABLES `CUST_INDEX_RESULT` WRITE;
/*!40000 ALTER TABLE `CUST_INDEX_RESULT` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_INDEX_RESULT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_LEAD`
--

DROP TABLE IF EXISTS `CUST_LEAD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_LEAD` (
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
-- Dumping data for table `CUST_LEAD`
--

LOCK TABLES `CUST_LEAD` WRITE;
/*!40000 ALTER TABLE `CUST_LEAD` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_LEAD` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_MASTER`
--

DROP TABLE IF EXISTS `CUST_MASTER`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_MASTER` (
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
-- Dumping data for table `CUST_MASTER`
--

LOCK TABLES `CUST_MASTER` WRITE;
/*!40000 ALTER TABLE `CUST_MASTER` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_MASTER` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_TAG`
--

DROP TABLE IF EXISTS `CUST_TAG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_TAG` (
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
-- Dumping data for table `CUST_TAG`
--

LOCK TABLES `CUST_TAG` WRITE;
/*!40000 ALTER TABLE `CUST_TAG` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_TAG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `CUST_TAG_REL`
--

DROP TABLE IF EXISTS `CUST_TAG_REL`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CUST_TAG_REL` (
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
-- Dumping data for table `CUST_TAG_REL`
--

LOCK TABLES `CUST_TAG_REL` WRITE;
/*!40000 ALTER TABLE `CUST_TAG_REL` DISABLE KEYS */;
/*!40000 ALTER TABLE `CUST_TAG_REL` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `DOC_INFO`
--

DROP TABLE IF EXISTS `DOC_INFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `DOC_INFO` (
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
-- Dumping data for table `DOC_INFO`
--

LOCK TABLES `DOC_INFO` WRITE;
/*!40000 ALTER TABLE `DOC_INFO` DISABLE KEYS */;
INSERT INTO `DOC_INFO` VALUES ('28263061988e4266ae1f9ee6af83f881','TEST_产品手册B','PRODUCT_DOC','FILE_b826794a','ACTIVE','tester','2026-04-29 08:04:04',NULL,NULL),('6a73867763d04c7cbfbf01a716a6960e','TEST_运营指南','OPERATION_DOC','FILE_31873663','ACTIVE','tester','2026-04-29 08:04:04',NULL,NULL),('6d428161d2ac4773825bc87fde619dba','TEST_产品手册A','PRODUCT_DOC','FILE_cf81b23f','ACTIVE','tester','2026-04-29 08:04:04',NULL,NULL),('6fc30b0c1db843c68b52969a2b34c186','TEST_已停用文档','PRODUCT_DOC','FILE_d2f26199','DISABLED','tester','2026-04-29 08:04:04',NULL,NULL);
/*!40000 ALTER TABLE `DOC_INFO` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `EMP_INDEX_RESULT`
--

DROP TABLE IF EXISTS `EMP_INDEX_RESULT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `EMP_INDEX_RESULT` (
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工指标结果宽表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `EMP_INDEX_RESULT`
--

LOCK TABLES `EMP_INDEX_RESULT` WRITE;
/*!40000 ALTER TABLE `EMP_INDEX_RESULT` DISABLE KEYS */;
/*!40000 ALTER TABLE `EMP_INDEX_RESULT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `EXT_ORG_INFO`
--

DROP TABLE IF EXISTS `EXT_ORG_INFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `EXT_ORG_INFO` (
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
-- Dumping data for table `EXT_ORG_INFO`
--

LOCK TABLES `EXT_ORG_INFO` WRITE;
/*!40000 ALTER TABLE `EXT_ORG_INFO` DISABLE KEYS */;
INSERT INTO `EXT_ORG_INFO` VALUES (1,'HQ','总行',1,NULL,0,NULL,NULL,'2026-04-07 15:49:38',NULL),(2,'BJ','北京分行',2,'HQ',0,NULL,NULL,'2026-04-07 15:49:38',NULL),(3,'SH','上海分行',2,'HQ',0,NULL,NULL,'2026-04-07 15:49:38',NULL),(4,'BJ_CY','北京分行朝阳支行',3,'BJ',0,NULL,NULL,'2026-04-07 15:49:38',NULL),(5,'SH_PD','上海分行浦东支行',3,'SH',0,NULL,NULL,'2026-04-07 15:49:38',NULL);
/*!40000 ALTER TABLE `EXT_ORG_INFO` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `EXT_USER_ORG`
--

DROP TABLE IF EXISTS `EXT_USER_ORG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `EXT_USER_ORG` (
  `USER_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `ORG_CODE` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '机构编码',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ORG_CODE`),
  KEY `idx_org_code` (`ORG_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户机构关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `EXT_USER_ORG`
--

LOCK TABLES `EXT_USER_ORG` WRITE;
/*!40000 ALTER TABLE `EXT_USER_ORG` DISABLE KEYS */;
INSERT INTO `EXT_USER_ORG` VALUES ('admin','HQ','2026-04-07 15:49:38'),('E10001','BJ_CY','2026-04-25 16:01:33'),('E10002','SH_PD','2026-04-10 11:17:49'),('E20001','BJ_CY','2026-04-10 11:17:49'),('E30001','HQ','2026-04-10 11:17:49'),('E30002','HQ','2026-04-10 11:17:49'),('E40001','HQ','2026-04-10 11:17:49'),('E40002','HQ','2026-04-10 11:17:49'),('E50001','HQ','2026-04-10 11:17:49'),('E50002','HQ','2026-04-10 11:17:49'),('E60001','HQ','2026-04-10 11:17:49'),('E60002','HQ','2026-04-10 11:17:49'),('E90001','HQ','2026-04-25 16:01:33'),('user001','BJ_CY','2026-04-07 15:49:38'),('user002','SH_PD','2026-04-07 15:49:38');
/*!40000 ALTER TABLE `EXT_USER_ORG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `FILE_OBJECT`
--

DROP TABLE IF EXISTS `FILE_OBJECT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FILE_OBJECT` (
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
-- Dumping data for table `FILE_OBJECT`
--

LOCK TABLES `FILE_OBJECT` WRITE;
/*!40000 ALTER TABLE `FILE_OBJECT` DISABLE KEYS */;
/*!40000 ALTER TABLE `FILE_OBJECT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `FLW_RU_BATCH`
--

DROP TABLE IF EXISTS `FLW_RU_BATCH`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_RU_BATCH` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `SEARCH_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SEARCH_KEY2_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) NOT NULL,
  `COMPLETE_TIME_` datetime(3) DEFAULT NULL,
  `STATUS_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BATCH_DOC_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `FLW_RU_BATCH`
--

LOCK TABLES `FLW_RU_BATCH` WRITE;
/*!40000 ALTER TABLE `FLW_RU_BATCH` DISABLE KEYS */;
/*!40000 ALTER TABLE `FLW_RU_BATCH` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `FLW_RU_BATCH_PART`
--

DROP TABLE IF EXISTS `FLW_RU_BATCH_PART`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_RU_BATCH_PART` (
  `ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `REV_` int DEFAULT NULL,
  `BATCH_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `SCOPE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SUB_SCOPE_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SCOPE_TYPE_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SEARCH_KEY_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `SEARCH_KEY2_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `CREATE_TIME_` datetime(3) NOT NULL,
  `COMPLETE_TIME_` datetime(3) DEFAULT NULL,
  `STATUS_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `RESULT_DOC_ID_` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '',
  PRIMARY KEY (`ID_`),
  KEY `FLW_IDX_BATCH_PART` (`BATCH_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `FLW_RU_BATCH_PART`
--

LOCK TABLES `FLW_RU_BATCH_PART` WRITE;
/*!40000 ALTER TABLE `FLW_RU_BATCH_PART` DISABLE KEYS */;
/*!40000 ALTER TABLE `FLW_RU_BATCH_PART` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `KPI_RESULT`
--

DROP TABLE IF EXISTS `KPI_RESULT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `KPI_RESULT` (
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='KPI结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `KPI_RESULT`
--

LOCK TABLES `KPI_RESULT` WRITE;
/*!40000 ALTER TABLE `KPI_RESULT` DISABLE KEYS */;
/*!40000 ALTER TABLE `KPI_RESULT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `LEAD_IMPORT_BATCH`
--

DROP TABLE IF EXISTS `LEAD_IMPORT_BATCH`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `LEAD_IMPORT_BATCH` (
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
-- Dumping data for table `LEAD_IMPORT_BATCH`
--

LOCK TABLES `LEAD_IMPORT_BATCH` WRITE;
/*!40000 ALTER TABLE `LEAD_IMPORT_BATCH` DISABLE KEYS */;
/*!40000 ALTER TABLE `LEAD_IMPORT_BATCH` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `LOAN_APPLY`
--

DROP TABLE IF EXISTS `LOAN_APPLY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `LOAN_APPLY` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID(UUID)',
  `apply_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请编号(LA+yyyyMMdd+6位序号)',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID,逻辑外键→cust_master.id',
  `source_touch_task_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源触达任务ID,逻辑外键→touch_task.id',
  `project_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '项目类型(字典PROJECT_TYPE)',
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务类型(字典BIZ_TYPE)',
  `guarantee_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '担保方式(字典GUARANTEE_TYPE)',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额(元,保留4位小数)',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '敞口金额(元,保留4位小数)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键,固定格式LOAN:{id}',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID,对应ACT_RU_EXECUTION/ACT_HI_PROCINST',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构(ORG_CODE),用于数据范围过滤',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人工号(PT_USER.emp_id)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人工号',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_process_inst` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资产投放申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `LOAN_APPLY`
--

LOCK TABLES `LOAN_APPLY` WRITE;
/*!40000 ALTER TABLE `LOAN_APPLY` DISABLE KEYS */;
/*!40000 ALTER TABLE `LOAN_APPLY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ORG_INDEX_RESULT`
--

DROP TABLE IF EXISTS `ORG_INDEX_RESULT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ORG_INDEX_RESULT` (
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机构指标结果宽表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ORG_INDEX_RESULT`
--

LOCK TABLES `ORG_INDEX_RESULT` WRITE;
/*!40000 ALTER TABLE `ORG_INDEX_RESULT` DISABLE KEYS */;
/*!40000 ALTER TABLE `ORG_INDEX_RESULT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_ALLOC_ADJUST_APPLY`
--

DROP TABLE IF EXISTS `PERF_ALLOC_ADJUST_APPLY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_ALLOC_ADJUST_APPLY` (
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
-- Dumping data for table `PERF_ALLOC_ADJUST_APPLY`
--

LOCK TABLES `PERF_ALLOC_ADJUST_APPLY` WRITE;
/*!40000 ALTER TABLE `PERF_ALLOC_ADJUST_APPLY` DISABLE KEYS */;
INSERT INTO `PERF_ALLOC_ADJUST_APPLY` VALUES ('TEST_Q26_0cd0c68277','AA_Q26_TEST_Q26_0cd0c68277','TEST_Q26_CUST_X','RULE','CORP_LOAN',NULL,'APPROVED','ALLOC_ADJUST:TEST_Q26_0cd0c68277','PI_Q26_TEST_Q26_0cd0c68277','ORG_Q26','Q2.6 IT','q26_user','2026-04-29 08:04:59','q26_user','2026-04-29 08:04:59'),('TEST_Q26_R_d224160075','AA_Q26_R_TEST_Q26_R_d224160075','TEST_Q26_CUST_R','RULE','RETAIL_CARD',NULL,'REJECTED','ALLOC_ADJUST:TEST_Q26_R_d224160075','PI_Q26_R_TEST_Q26_R_d224160075','ORG_Q26',NULL,'q26_user','2026-04-29 08:04:59','q26_user','2026-04-29 08:04:59');
/*!40000 ALTER TABLE `PERF_ALLOC_ADJUST_APPLY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_ALLOC_ADJUST_ITEM`
--

DROP TABLE IF EXISTS `PERF_ALLOC_ADJUST_ITEM`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_ALLOC_ADJUST_ITEM` (
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
-- Dumping data for table `PERF_ALLOC_ADJUST_ITEM`
--

LOCK TABLES `PERF_ALLOC_ADJUST_ITEM` WRITE;
/*!40000 ALTER TABLE `PERF_ALLOC_ADJUST_ITEM` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_ALLOC_ADJUST_ITEM` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_EXPORT_TASK`
--

DROP TABLE IF EXISTS `PERF_EXPORT_TASK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_EXPORT_TASK` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出任务ID',
  `export_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：KPI/METRIC/ALLOC/DETAIL',
  `params_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '导出参数 JSON',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `file_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO object key',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `row_count` int DEFAULT NULL COMMENT '导出行数',
  `expire_at` datetime DEFAULT NULL COMMENT '文件过期时间',
  `operator_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作人员工号',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '失败原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_export_type` (`export_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效异步导出任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PERF_EXPORT_TASK`
--

LOCK TABLES `PERF_EXPORT_TASK` WRITE;
/*!40000 ALTER TABLE `PERF_EXPORT_TASK` DISABLE KEYS */;
INSERT INTO `PERF_EXPORT_TASK` VALUES ('0faf74caeed54eaf9c5ae6cde9c6f512','METRIC','{\"metricCodes\":[\"M_TST_20260429_073218_L1\"],\"orgCodes\":[\"HQ\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:32:22','2026-04-29 07:32:22'),('146f72d950c84ec69d50798be2376d1a','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:32:22','2026-04-29 07:32:22'),('19c13004880842818d537d87683c0a7f','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:00','2026-04-29 07:33:00'),('233f019bf7c14290818dbaa662d9e7ae','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:34:10','2026-04-29 07:34:10'),('2c27c6b4961f448b96f47c7417925998','METRIC','{\"metricCodes\":[\"M_TST_20260429_073256_L1\"],\"orgCodes\":[\"HQ\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:00','2026-04-29 07:33:00'),('2d004499b0a3495a84648fb6d19fdd31','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:34:10','2026-04-29 07:34:10'),('335336717354470bbd353d9075ec4dbf','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:25:34','2026-04-29 07:25:34'),('3eadb245a7dd4ead815d4579daba92e5','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:00','2026-04-29 07:33:00'),('5be1b694d864409a94e6aa9906bd2482','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:40','2026-04-29 07:33:40'),('6a93cd7aac214e799e03ffb3e7f50636','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:32:23','2026-04-29 07:32:23'),('6fdd8141d6a8497eba62941b08b8c871','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:00','2026-04-29 07:33:00'),('747af4017b874a24a83119dc4c4c2bda','METRIC','{\"metricCodes\":[\"M_TST_20260429_073405_L1\"],\"orgCodes\":[\"HQ\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:34:10','2026-04-29 07:34:10'),('79608f66cca34f07b7928ea57d70ffb3','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:40','2026-04-29 07:33:40'),('92c9b45546fb4706b7f268a5fab863be','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:45','2026-04-29 07:33:45'),('94d0f8830d424701ab351a3bdb2e5297','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:31:18','2026-04-29 07:31:18'),('96f0a83aba0b45f1bba4771a27491995','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:40','2026-04-29 07:33:40'),('9801ad716cea43ac946ebbb89e248a51','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:31:18','2026-04-29 07:31:18'),('98586140c3b24d8fa51a8f79915fb944','METRIC','{\"metricCodes\":[\"M_TST_20260429_073341_L1\"],\"orgCodes\":[\"HQ\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:45','2026-04-29 07:33:45'),('9eb76efe0e4143aa858b11a801b74e6a','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:31:18','2026-04-29 07:31:18'),('a25dd54081134040826a2fe4b55d452d','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:25:34','2026-04-29 07:25:34'),('c5e917527b9b48cb805997cfce5d1dc9','METRIC','{\"metricCodes\":[\"M_TST_20260429_072530_L1\"],\"orgCodes\":[\"\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:25:34','2026-04-29 07:25:34'),('c882a5c1573648fe9b3610f92f9c7f70','METRIC','{\"metricCodes\":[\"M_TST_20260429_073335_L1\"],\"orgCodes\":[\"HQ\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:39','2026-04-29 07:33:39'),('d56205ac2af246fbaed2ea26fd31b377','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:32:22','2026-04-29 07:32:22'),('ddf3d36f90aa4526a1cc15f41b9f985e','DETAIL','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:45','2026-04-29 07:33:45'),('e294fd56853f465385b7bde39e01ebc9','METRIC','{\"metricCodes\":[\"M_TST_20260429_073114_L1\"],\"orgCodes\":[\"\"],\"dataDate\":\"2026-04-29\",\"baseDim\":\"EMP\",\"version\":\"V1\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:31:18','2026-04-29 07:31:18'),('e7e0bb4668e74b328ec9a3cb8ede924e','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:25:34','2026-04-29 07:25:34'),('f05af97de6ba430cabf600b73d05dd1f','KPI','{\"cycleType\":\"MONTHLY\",\"dataVersion\":\"V1\",\"asOfDate\":\"2026-04-29\",\"cycleDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:34:10','2026-04-29 07:34:10'),('f24c76694fc24a35bdd528acef38dd99','ALLOC','{\"bizKind\":\"DEPOSIT\",\"effectiveDate\":\"2026-04-29\"}','FAILED',NULL,NULL,NULL,NULL,'admin','导出文件生成失败: Failed to connect to localhost/127.0.0.1:9000','2026-04-29 07:33:45','2026-04-29 07:33:45');
/*!40000 ALTER TABLE `PERF_EXPORT_TASK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_IMPORT_BATCH`
--

DROP TABLE IF EXISTS `PERF_IMPORT_BATCH`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_IMPORT_BATCH` (
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
-- Dumping data for table `PERF_IMPORT_BATCH`
--

LOCK TABLES `PERF_IMPORT_BATCH` WRITE;
/*!40000 ALTER TABLE `PERF_IMPORT_BATCH` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_IMPORT_BATCH` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_KPI_ITEM`
--

DROP TABLE IF EXISTS `PERF_KPI_ITEM`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_KPI_ITEM` (
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
-- Dumping data for table `PERF_KPI_ITEM`
--

LOCK TABLES `PERF_KPI_ITEM` WRITE;
/*!40000 ALTER TABLE `PERF_KPI_ITEM` DISABLE KEYS */;
INSERT INTO `PERF_KPI_ITEM` VALUES ('ca1d4fc82c74498b973ae89c4a5d16bf','669032e367684e8aa7d9ac23a55ec875','M_TST_20260429_073405_L2',60.0000,1.0000,0.0000,999999.0000,'2026-04-29 07:34:07'),('dd014663c87d4b218474aa931d19285a','669032e367684e8aa7d9ac23a55ec875','M_TST_20260429_073405_L1',40.0000,1.0000,0.0000,999999.0000,'2026-04-29 07:34:07'),('SEED_KI_DEP','SEED_KS_EMP_2026','M_EMP_DEP_AVG_BAL',60.0000,1.0000,0.0000,100.0000,'2026-04-29 07:24:49'),('SEED_KI_FEE','SEED_KS_EMP_2026','M_EMP_FEE_INCOME',40.0000,1.0000,0.0000,100.0000,'2026-04-29 07:24:49');
/*!40000 ALTER TABLE `PERF_KPI_ITEM` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_KPI_SCHEME`
--

DROP TABLE IF EXISTS `PERF_KPI_SCHEME`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_KPI_SCHEME` (
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
-- Dumping data for table `PERF_KPI_SCHEME`
--

LOCK TABLES `PERF_KPI_SCHEME` WRITE;
/*!40000 ALTER TABLE `PERF_KPI_SCHEME` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_KPI_SCHEME` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_METRIC_DEF`
--

DROP TABLE IF EXISTS `PERF_METRIC_DEF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_METRIC_DEF` (
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
  `unit` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '单位：元/万元/%',
  `decimal_places` tinyint DEFAULT '2' COMMENT '小数位数',
  `deleted` tinyint DEFAULT '0' COMMENT '0=存在 1=删除',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '指标详细描述（补充 metric_desc）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_code` (`metric_code`),
  UNIQUE KEY `uk_base_dim_slot_alive` ((if((`deleted` = 0),concat(`base_dim`,_utf8mb4'#',`val_slot`),NULL))),
  KEY `idx_dim_level` (`base_dim`,`metric_level`),
  KEY `idx_status` (`status`),
  KEY `idx_val_slot` (`val_slot`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='指标定义表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PERF_METRIC_DEF`
--

LOCK TABLES `PERF_METRIC_DEF` WRITE;
/*!40000 ALTER TABLE `PERF_METRIC_DEF` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_METRIC_DEF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_METRIC_REF`
--

DROP TABLE IF EXISTS `PERF_METRIC_REF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_METRIC_REF` (
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
-- Dumping data for table `PERF_METRIC_REF`
--

LOCK TABLES `PERF_METRIC_REF` WRITE;
/*!40000 ALTER TABLE `PERF_METRIC_REF` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_METRIC_REF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_RUN_TASK`
--

DROP TABLE IF EXISTS `PERF_RUN_TASK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_RUN_TASK` (
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
-- Dumping data for table `PERF_RUN_TASK`
--

LOCK TABLES `PERF_RUN_TASK` WRITE;
/*!40000 ALTER TABLE `PERF_RUN_TASK` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_RUN_TASK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_TARGET_ADJUST_APPLY`
--

DROP TABLE IF EXISTS `PERF_TARGET_ADJUST_APPLY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_TARGET_ADJUST_APPLY` (
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
-- Dumping data for table `PERF_TARGET_ADJUST_APPLY`
--

LOCK TABLES `PERF_TARGET_ADJUST_APPLY` WRITE;
/*!40000 ALTER TABLE `PERF_TARGET_ADJUST_APPLY` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_TARGET_ADJUST_APPLY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_TARGET_PLAN`
--

DROP TABLE IF EXISTS `PERF_TARGET_PLAN`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_TARGET_PLAN` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `plan_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案编码(唯一)',
  `plan_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `kpi_scheme_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联KPI方案ID',
  `target_dim` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标维度：EMP/ORG',
  `target_cycle` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标周期：YEAR/QUARTER',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `owner_emp_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属员工（SELF/SELF_ASSIGNED scope 列）',
  `owner_org_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属机构（ORG/ORG_SUBTREE scope 列）',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_status` (`status`),
  KEY `idx_owner_emp` (`owner_emp_id`),
  KEY `idx_owner_org` (`owner_org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PERF_TARGET_PLAN`
--

LOCK TABLES `PERF_TARGET_PLAN` WRITE;
/*!40000 ALTER TABLE `PERF_TARGET_PLAN` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_TARGET_PLAN` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PERF_TARGET_VALUE`
--

DROP TABLE IF EXISTS `PERF_TARGET_VALUE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PERF_TARGET_VALUE` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标值ID',
  `plan_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标方案ID',
  `subject_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型：EMP/ORG',
  `subject_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID(emp_id/org_code)',
  `cycle_key` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '周期键：2026或2026Q1',
  `metric_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码',
  `target_value` decimal(20,4) NOT NULL COMMENT '目标值',
  `base_value` decimal(20,4) DEFAULT NULL COMMENT '基础值(可空，默认为0)',
  `owner_emp_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属员工（SELF/SELF_ASSIGNED scope 列）',
  `owner_org_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属机构（ORG/ORG_SUBTREE scope 列）',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
-- Dumping data for table `PERF_TARGET_VALUE`
--

LOCK TABLES `PERF_TARGET_VALUE` WRITE;
/*!40000 ALTER TABLE `PERF_TARGET_VALUE` DISABLE KEYS */;
/*!40000 ALTER TABLE `PERF_TARGET_VALUE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PORTAL_NAV`
--

DROP TABLE IF EXISTS `PORTAL_NAV`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PORTAL_NAV` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导航ID',
  `nav_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导航名称',
  `nav_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导航URL',
  `nav_icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `nav_category` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分类',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网址导航表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PORTAL_NAV`
--

LOCK TABLES `PORTAL_NAV` WRITE;
/*!40000 ALTER TABLE `PORTAL_NAV` DISABLE KEYS */;
INSERT INTO `PORTAL_NAV` VALUES ('NAV001','CCRM系统','https://ccrm.bank.com','icon-ccrm','总行系统',1,'ACTIVE','SYSTEM','2026-04-30 09:39:10',NULL,'2026-04-30 09:39:10'),('NAV002','PCRM系统','https://pcrm.bank.com','icon-pcrm','总行系统',2,'ACTIVE','SYSTEM','2026-04-30 09:39:10',NULL,'2026-04-30 09:39:10'),('NAV003','网银系统','https://ebank.bank.com','icon-ebank','电子渠道',3,'ACTIVE','SYSTEM','2026-04-30 09:39:10',NULL,'2026-04-30 09:39:10'),('NAV004','信贷管理系统','https://credit.bank.com','icon-credit','风险管理',4,'ACTIVE','SYSTEM','2026-04-30 09:39:10',NULL,'2026-04-30 09:39:10'),('NAV005','OA系统','https://oa.bank.com','icon-oa','办公系统',5,'ACTIVE','SYSTEM','2026-04-30 09:39:10',NULL,'2026-04-30 09:39:10');
/*!40000 ALTER TABLE `PORTAL_NAV` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PORTAL_SHORTCUT`
--

DROP TABLE IF EXISTS `PORTAL_SHORTCUT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PORTAL_SHORTCUT` (
  `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '快捷入口ID',
  `shortcut_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '快捷入口名称',
  `shortcut_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '跳转URL',
  `shortcut_icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `shortcut_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型：SYSTEM-系统, CUSTOM-自定义',
  `target_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标类型：INTERNAL-内部, EXTERNAL-外部',
  `emp_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属用户工号（自定义快捷入口）',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_type` (`shortcut_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作台快捷入口表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PORTAL_SHORTCUT`
--

LOCK TABLES `PORTAL_SHORTCUT` WRITE;
/*!40000 ALTER TABLE `PORTAL_SHORTCUT` DISABLE KEYS */;
/*!40000 ALTER TABLE `PORTAL_SHORTCUT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PRODUCT_INFO`
--

DROP TABLE IF EXISTS `PRODUCT_INFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PRODUCT_INFO` (
  `id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品ID',
  `product_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品代码',
  `product_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品名称',
  `product_category` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品类别',
  `description` text COLLATE utf8mb4_general_ci COMMENT '产品描述',
  `support_for_support_request` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否支持中场支持',
  `owner_org_id` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属组织(维护组织)',
  `product_dept_org_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产品部门ORG_CODE',
  `file_object_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主附件文件ID',
  `responsible_emp_ids` text COLLATE utf8mb4_general_ci COMMENT '负责人列表(JSON数组,反向关联通讯录)',
  `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品状态',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
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
-- Dumping data for table `PRODUCT_INFO`
--

LOCK TABLES `PRODUCT_INFO` WRITE;
/*!40000 ALTER TABLE `PRODUCT_INFO` DISABLE KEYS */;
INSERT INTO `PRODUCT_INFO` VALUES ('PROD001','TBK_DEPOSIT','交易银行-结构性存款','交易银行','结构性存款产品介绍',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-30 09:39:10','2026-04-30 09:39:10',0),('PROD002','TBK_SUPPLY_CHAIN','交易银行-供应链金融','交易银行','供应链金融产品介绍',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-30 09:39:10','2026-04-30 09:39:10',0),('PROD003','FM_BOND','金融市场-债券承销','金融市场','债券承销服务介绍',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-30 09:39:10','2026-04-30 09:39:10',0),('PROD004','CORP_LOAN','公司-流动资金贷款','公司银行','流动资金贷款产品',0,NULL,NULL,NULL,NULL,'ACTIVE','SYSTEM',NULL,'2026-04-30 09:39:10','2026-04-30 09:39:10',0);
/*!40000 ALTER TABLE `PRODUCT_INFO` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PT_RESOURCE`
--

DROP TABLE IF EXISTS `PT_RESOURCE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_RESOURCE` (
  `RESOURCE_ID` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源ID',
  `RESOURCE_URL` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源URL（支持Ant通配符）',
  `RESOURCE_METHOD` varchar(10) COLLATE utf8mb4_general_ci NOT NULL COMMENT '请求方法：GET/POST/PUT/DELETE，支持 *',
  `MENU_NAME` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `MENU_ICON_URL` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标路径',
  `MENU_RANK_NO` int DEFAULT '0' COMMENT '菜单排序',
  `ISMENU` int DEFAULT '0' COMMENT '是否菜单 0 是 1 不是',
  `MENU_ENDFLAG` varchar(10) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '表单结束标志，是否叶子节点菜单 1 是 0 不是',
  `PARENT_RESOURCE_ID` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上级资源ID',
  `STATUS` int DEFAULT '0' COMMENT '状态 0启用 1 不启用',
  `SYS_CODE` varchar(10) COLLATE utf8mb4_general_ci DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`RESOURCE_ID`),
  UNIQUE KEY `uk_pt_resource_url_method_sys` (`RESOURCE_URL`,`RESOURCE_METHOD`,`SYS_CODE`),
  KEY `idx_pt_resource_status` (`STATUS`,`SYS_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PT_RESOURCE`
--

LOCK TABLES `PT_RESOURCE` WRITE;
/*!40000 ALTER TABLE `PT_RESOURCE` DISABLE KEYS */;
INSERT INTO `PT_RESOURCE` VALUES ('A_BZ_DELETE','/api/admin/biz-scopes/*','DELETE','删除业务范围',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_BZ_LIST','/api/admin/biz-scopes','GET','业务范围列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_BZ_MATRIX','/api/admin/biz-scopes/matrix','GET','业务范围矩阵',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_BZ_SAVE','/api/admin/biz-scopes','POST','保存业务范围',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_CHECK_PERM','/api/auth/check-permission','POST','权限校验',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_CURR_USER','/api/auth/current-user','GET','当前用户信息',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_LOGIN','/api/auth/login','POST','用户登录',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_LOGOUT','/api/auth/logout','POST','用户登出',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ORG_SUBTREE','/api/orgs/subtree','GET','当前机构子树',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ORG_TREE','/api/orgs/tree','GET','组织机构树',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ORG_USERS','/api/orgs/*/users','GET','机构下用户',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_PERMS','/api/auth/permissions','GET','当前用户权限集',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_CREATE','/api/admin/resources','POST','创建资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_DELETE','/api/admin/resources/*','DELETE','删除资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_TREE','/api/admin/resources/tree','GET','资源树',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RES_UPDATE','/api/admin/resources/*','PUT','更新资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_CREATE','/api/admin/roles/','POST','创建角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_DELETE','/api/admin/roles/*','DELETE','删除角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_LIST','/api/admin/roles/','GET','角色列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_UPDATE','/api/admin/roles/*','PUT','更新角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_ROLE_USERS','/api/admin/roles/*/users','GET','角色下用户列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RR_BIND','/api/admin/roles/*/resources','POST','增量绑定角色资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RR_LIST','/api/admin/roles/*/resources','GET','角色资源列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_RR_REPLACE','/api/admin/roles/*/resources','PUT','全量替换角色资源',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_UR_BIND','/api/admin/users/*/roles','POST','绑定用户角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_UR_DEL','/api/admin/users/*/roles/*','DELETE','解绑用户角色',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('A_UR_LIST','/api/admin/users/*/roles','GET','用户角色列表',NULL,0,0,'0',NULL,0,'AUTH','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('B_LOAN_CANCEL','/api/loans/*/cancel','POST','撤回资产投放',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','撤回资产投放申请'),('B_LOAN_CREATE','/api/loans','POST','创建资产投放',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','创建资产投放草稿'),('B_LOAN_DELETE','/api/loans/*','DELETE','删除资产投放',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','删除资产投放草稿'),('B_LOAN_EXPORT','/api/loans/export','GET','导出资产投放',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','导出资产投放申请(高危)'),('B_LOAN_FORM','/api/loans/*/node-form/*','GET','节点表单配置',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','获取节点表单配置'),('B_LOAN_LIST','/api/loans','GET','资产投放列表',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','资产投放分页列表'),('B_LOAN_READ','/api/loans/*','GET','资产投放详情',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','资产投放申请详情'),('B_LOAN_SUBMIT','/api/loans/*/submit','POST','提交资产投放审批',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','提交资产投放审批'),('B_LOAN_UPDATE','/api/loans/*','PUT','更新资产投放',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','更新资产投放草稿'),('B_SUPD_DISP','/api/support-dept/requests/*/dispatch','POST','秘书派单',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','秘书派单'),('B_SUPD_DONE','/api/support-dept/requests/*/complete','POST','办理完成',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','支持人员办理完成'),('B_SUPD_LIST','/api/support-dept/requests','GET','承接侧列表',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','承接侧申请列表'),('B_SUPD_XFER','/api/support-dept/requests/*/transfer','POST','秘书转交',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','秘书转交(高危)'),('B_SUP_CANCEL','/api/support-requests/*/cancel','POST','撤回中场支持',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','撤回中场支持申请'),('B_SUP_CREATE','/api/support-requests','POST','创建中场支持',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','创建中场支持申请'),('B_SUP_DELETE','/api/support-requests/*','DELETE','删除中场支持',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','删除中场支持草稿'),('B_SUP_EXPORT','/api/support-requests/export','GET','导出中场支持',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','导出中场支持申请(高危)'),('B_SUP_LIST','/api/support-requests','GET','中场支持列表',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','中场支持发起侧列表'),('B_SUP_PROD','/api/support-requests/available-products','GET','可用产品列表',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','中场支持可用产品'),('B_SUP_READ','/api/support-requests/*','GET','中场支持详情',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','中场支持申请详情'),('B_SUP_SUBMIT','/api/support-requests/*/submit','POST','提交中场支持',NULL,0,0,'0',NULL,0,'BRANCH','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','提交中场支持审批'),('C_ADM_TT_ASSIGN','/api/admin/touch-tasks/batch-assign','POST','批量分配触达任务',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_ADM_TT_EXPORT','/api/admin/touch-tasks/export','GET','管理后台触达任务导出',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_ADM_TT_LIST','/api/admin/touch-tasks','GET','管理后台触达任务列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_ADM_TT_SUMMARY','/api/admin/touch-tasks/summary','GET','管理机构触达汇总',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-30 09:57:04','seed','2026-04-30 09:57:04',NULL,'P1a 2026-04-28'),('C_CLAIM_CANCEL','/api/claims/*/cancel','POST','取消认领',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CLAIM_CREATE','/api/claims','POST','认领客户',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CLAIM_MINE','/api/claims/mine','GET','我的认领列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CLAIM_RETOUCH','/api/claims/*/re-touch','POST','重新发起触达',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-30 09:57:04','seed','2026-04-30 09:57:04',NULL,'P1a 2026-04-28'),('C_CUST_DEL_APPLY','/api/customers/*/delete-apply','POST','客户删除申请',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_DETAIL','/api/customers/*','GET','客户主档详情',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_EXPORT','/api/customers/export','GET','客户列表导出',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_HIST_XORG','/api/customers/*/history','GET','客户跨机构历史查询',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_LIST','/api/customers','GET','客户主档列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_TAG_ADD','/api/customers/*/tags','POST','客户追加打标',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_TAG_DEL','/api/customers/*/tags/*','DELETE','客户取消单个标签',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_CUST_TRANSFER','/api/customers/*/claims/*/transfer','POST','转交维护人',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_BATCHES','/api/leads/batches','GET','导入批次列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_CREATE','/api/leads','POST','新建线索',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_DELETE','/api/leads/*','DELETE','删除线索',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_DEL_VER','/api/leads/delete-version','POST','创建删除版本',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_DETAIL','/api/leads/*','GET','线索详情',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_EDIT_VER','/api/leads/edit-version','POST','创建修改版本',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_IMP_BATCH','/api/leads/import/batches/*','GET','导入批次详情',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-30 09:57:04','seed','2026-04-30 09:57:04',NULL,'P1a 2026-04-28'),('C_LEAD_IMP_EXEC','/api/leads/import/execute','POST','执行导入',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_IMP_PRE','/api/leads/import/preview','POST','导入预览',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_LIST','/api/leads','GET','线索列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_SUBMIT','/api/leads/*/submit','POST','提交审批',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_UPDATE','/api/leads/*','PUT','编辑线索',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_LEAD_VERSIONS','/api/leads/*/versions','GET','查询线索版本链',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_POOL_LIST','/api/customer-pool','GET','客户池列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_CREATE','/api/tags','POST','新增标签',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_CUST_EXPORT','/api/tags/*/customers/export','GET','标签客户导出',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_CUST_IMP','/api/tags/*/customers/import','POST','标签客户导入',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_CUST_LIST','/api/tags/*/customers','GET','标签客户列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_ENABLED','/api/tags/enabled','GET','启用标签列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_LIST','/api/tags','GET','标签列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_STATUS','/api/tags/*/status','PUT','标签启停',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TAG_UPDATE','/api/tags/*','PUT','编辑标签',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TR_EXPORT','/api/touch-reports/export','GET','触达报告导出',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TR_LIST','/api/touch-reports','GET','触达报告列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TR_STAT','/api/touch-reports/statistics','GET','触达统计',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TT_CANCEL','/api/touch-tasks/*/cancel','POST','取消触达任务',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TT_DETAIL','/api/touch-tasks/*','GET','触达任务详情',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TT_LIST','/api/touch-tasks','GET','触达任务列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TT_LOG_ADD','/api/touch-tasks/*/logs','POST','新增触达日志',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TT_LOG_LIST','/api/touch-tasks/*/logs','GET','触达日志列表',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('C_TT_SUCCESS','/api/touch-tasks/*/success','POST','标记触达任务成功',NULL,0,0,'0',NULL,0,'CUSTOMER','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('G_AUDIT_DETAIL','/api/admin/sys/audit-logs/*','GET','审计日志详情',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_AUDIT_EXPORT','/api/admin/sys/audit-logs/export','POST','导出审计日志',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_AUDIT_LIST','/api/admin/sys/audit-logs','GET','审计日志列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_GET','/api/admin/sys/calendar','GET','查询工作日',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_IMPORT','/api/admin/sys/calendar/import','POST','导入节假日',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_INIT','/api/admin/sys/calendar/init','POST','初始化年份',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_PUBLIC','/api/sys/calendar','GET','公共日历查询',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CAL_SET','/api/admin/sys/calendar/*','PUT','设置工作日',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CFG_LIST','/api/admin/sys/configs','GET','配置列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_CFG_UPDATE','/api/admin/sys/configs/*','PUT','更新配置',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_CREATE','/api/admin/sys/dicts','POST','创建字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_DELETE','/api/admin/sys/dicts/*','DELETE','删除字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_ITEMS','/api/sys/dicts/*/items','GET','字典项列表(公共)',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_LIST','/api/sys/dicts','GET','字典类型列表(公共)',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_STATUS','/api/admin/sys/dicts/*/status','PUT','启禁字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_DICT_UPDATE','/api/admin/sys/dicts/*','PUT','更新字典项',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_DELETE','/api/files/*','DELETE','删除文件',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_DOWNLOAD','/api/files/*/download','GET','下载文件',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_LIST','/api/files','GET','业务文件列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_FILE_UPLOAD','/api/files/upload','POST','上传文件',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_LIST','/api/admin/sys/jobs','GET','任务列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_LOGS','/api/admin/sys/jobs/*/logs','GET','任务执行日志',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_PAUSE','/api/admin/sys/jobs/*/pause','PUT','暂停任务',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_RESUME','/api/admin/sys/jobs/*/resume','PUT','恢复任务',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_JOB_TRIGGER','/api/admin/sys/jobs/*/trigger','POST','手动触发任务',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_COUNT','/api/notifications/unread-count','GET','未读通知数',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_DETAIL','/api/notifications/*','GET','通知详情',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_LIST','/api/notifications','GET','通知列表',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_READ','/api/notifications/*/read','PUT','标记已读',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_NOTIFY_READ_ALL','/api/notifications/read-all','PUT','全部已读',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_SQL_EXEC','/api/admin/sql-probe/execute','POST','SQL 执行探查',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('G_SQL_HIST','/api/admin/sql-probe/history','GET','SQL 执行历史',NULL,0,0,'0',NULL,0,'GOV','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('P_PERF_ALLOC_AD_CRE','/api/perf/alloc-adjust/create','POST','创建分配调整申请',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_ALLOC_AD_GET','/api/perf/alloc-adjust/*','GET','分配调整申请详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_ALLOC_AD_LST','/api/perf/alloc-adjust/list','GET','分配调整申请列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_ALLOC_AD_WD','/api/perf/alloc-adjust/*/withdraw','POST','撤回分配调整申请',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_ALLOC_CUR','/api/perf/alloc-relations','GET','当前分配关系',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_ALLOC_HIS','/api/perf/alloc-relations/history','GET','历史分配关系',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_ALLOC_SUM','/api/perf/alloc-relations/summary','GET','分配关系汇总',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_DATA_TASK_ST','/api/data-task/status','POST','任务状态查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_EXPT_DTL','/api/perf/export/detail','POST','KPI 明细导出',NULL,0,0,'0',NULL,0,'PERF','2026-04-24 08:05:37','seed','2026-04-24 08:05:37',NULL,'V1.2 Q6.4 高危'),('P_PERF_EXPT_MTR','/api/perf/export/metric','POST','指标宽表导出',NULL,0,0,'0',NULL,0,'PERF','2026-04-24 08:05:37','seed','2026-04-24 08:05:37',NULL,'V1.2 Q6.4'),('P_PERF_EXPT_TASK','/api/perf/export/task/*','GET','导出任务状态',NULL,0,0,'0',NULL,0,'PERF','2026-04-24 08:05:37','seed','2026-04-24 08:05:37',NULL,'V1.2 Q6.4'),('P_PERF_EXP_ALLOC','/api/perf/export/alloc','POST','导出绩效分配',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_EXP_KPI','/api/perf/export/kpi','POST','导出 KPI 结果',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_IMP_BTC_DEL','/api/perf/import/batches/*','DELETE','删除导入批次',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_IMP_BTC_ERR','/api/perf/import/batches/*/errors','GET','导入批次错误清单',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_IMP_BTC_GET','/api/perf/import/batches/*','GET','导入批次详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_IMP_BTC_RTY','/api/perf/import/batches/*/retry','POST','重试导入批次',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_IMP_UPLOAD','/api/perf/import/upload','POST','导入文件上传',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_KPI_ADD','/api/perf/kpi-schemes','POST','新增KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_DEL','/api/perf/kpi-schemes/*','DELETE','删除KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_GET','/api/perf/kpi-schemes/*','GET','KPI方案详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_IADD','/api/perf/kpi-schemes/*/items','POST','添加指标项',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_IDEL','/api/perf/kpi-schemes/*/items/*','DELETE','删除指标项',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_IUPD','/api/perf/kpi-schemes/*/items/*','PUT','编辑指标项',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_LIST','/api/perf/kpi-schemes','GET','KPI方案列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_PUB','/api/perf/kpi-schemes/*/publish','POST','发布KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_KPI_UPD','/api/perf/kpi-schemes/*','PUT','编辑KPI方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_ADD','/api/perf/metrics','POST','新增指标',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_DEL','/api/perf/metrics/*','DELETE','删除指标',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_GET','/api/perf/metrics/*','GET','指标详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_LIST','/api/perf/metrics','GET','指标列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_RBY','/api/perf/metrics/*/ref-by','GET','查谁引用了我',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_REFS','/api/perf/metrics/*/refs','GET','查指标上游依赖',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_SLOT','/api/perf/metrics/val-slots','GET','槽位占用查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_SREL','/api/perf/metrics/*/slot/release','POST','强制释放槽位',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_STAT','/api/perf/metrics/*/status','PUT','指标状态流转',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_METRIC_UPD','/api/perf/metrics/*','PUT','编辑指标',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_MTR_EXEC','/api/perf/metrics/*/execute','POST','指标立即执行',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_MTR_TRIAL','/api/perf/metrics/*/trial-run','POST','指标试运行',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_RECALC','/api/perf/recalc','POST','历史重算',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_RT_GET','/api/perf/run-tasks/*','GET','任务日志详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_RT_LIST','/api/perf/run-tasks','GET','任务日志列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_GET','/api/perf/sys-control','GET','版本查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_HIS','/api/perf/sys-control/history','GET','版本历史',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_INIT','/api/perf/sys-control/init','POST','版本初始化',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SC_SW','/api/perf/sys-control/switch-version','POST','版本切换',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_SYS_RB','/api/perf/sys-control/rollback','POST','系统控制回滚',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_TGT_AD_CRE','/api/perf/target-adjust/create','POST','创建目标调整申请',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_TGT_AD_GET','/api/perf/target-adjust/*','GET','目标调整申请详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_TGT_AD_LST','/api/perf/target-adjust/list','GET','目标调整申请列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_TGT_AD_WD','/api/perf/target-adjust/*/withdraw','POST','撤回目标调整申请',NULL,0,0,'0',NULL,0,'PERF','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('P_PERF_TGT_P_ADD','/api/perf/target-plans','POST','新增目标方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_GET','/api/perf/target-plans/*','GET','目标方案详情',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_LIST','/api/perf/target-plans','GET','目标方案列表',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_P_UPD','/api/perf/target-plans/*','PUT','编辑目标方案',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_V_ADD','/api/perf/target-values','POST','目标值upsert',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_V_BAT','/api/perf/target-values/batch','POST','目标值批量',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('P_PERF_TGT_V_LIST','/api/perf/target-values','GET','目标值查询',NULL,0,0,'0',NULL,0,'PERF','2026-04-16 15:05:22','seed','2026-04-16 15:05:56','seed','v1.0'),('RES_CUSTOMER_EDIT','/api/customers/*/edit','POST','编辑客户',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_CLAIM_CANCE','/api/claims/*/cancel','POST','取消认领',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_CLAIM_LST','/api/my-claims','GET','已认领客户',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_CLAIM_RETOU','/api/claims/*/re-touch','POST','重新触达',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_CUST_DELETE','/api/customers/*/delete-apply','POST','删除申请',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_CUST_DETAIL','/api/customers/*','GET','客户详情',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_CUST_EXPORT','/api/customers/export','GET','导出客户',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_CUST_HIST','/api/customers/*/history','GET','跨机构历史',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_CUST_TRANS','/api/customers/*/transfer','POST','转交',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_CUST_UPDATE','/api/customers/*','PUT','编辑客户',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_LD_IMP_BATC','/api/leads/import/batches','GET','批次列表',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_LD_IMP_BTCH','/api/leads/import/batches/*','GET','批次详情',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_LD_IMP_PRE','/api/leads/import/preview','POST','导入预览',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_LEAD_DELETE','/api/leads/*','DELETE','删除线索',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_LEAD_DETAIL','/api/leads/*','GET','线索详情',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_LEAD_EDIT','/api/leads/*/edit','POST','编辑已通过线索',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_LEAD_SUBMIT','/api/leads/*/submit','POST','提交审批',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_LEAD_UPDATE','/api/leads/*','PUT','编辑线索',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_POOL_CLAIM','/api/customer-pool/*/claim','POST','认领客户',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_POOL_LIST','/api/customer-pool','GET','客户池列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TAG_ENA','/api/tags/enabled','GET','启用标签列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TAG_EXPORT','/api/tags/*/customers/export','GET','标签客户导出',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TAG_IMPORT','/api/tags/*/customers/import','POST','标签客户导入',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TAG_READ','/api/tags/*','GET','标签详情',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_TAG_STATUS','/api/tags/*/status','PUT','启用/禁用标签',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TAG_UPDATE','/api/tags/*','PUT','编辑标签',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TRPT_EXP','/api/touch-reports/export','GET','触达导出',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TRPT_LST','/api/touch-reports','GET','触达明细',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TRPT_SUM','/api/touch-reports/summary','GET','触达汇总',NULL,0,0,'0',NULL,1,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 17:23:17','align-2026-04-25',' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]'),('RES_CUST_TSK_CANCEL','/api/touch-tasks/*/cancel','POST','触达取消',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TSK_LOGS','/api/touch-tasks/*/logs','POST','触达日志',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TSK_READ','/api/touch-tasks/*','GET','触达任务详情',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_CUST_TSK_SUCCESS','/api/touch-tasks/*/success','POST','触达成功',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PORTAL_WORKSPACE','/api/portal/workspace','GET','工作台聚合',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 15:59:45','align-2026-04-25','2026-04-25 15:59:45','align-2026-04-25','V1 portal slice'),('RES_PRODUCT_CREATE','/api/products','POST','产品新增',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('RES_PRODUCT_DELETE','/api/products/*','DELETE','产品删除',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('RES_PRODUCT_DETAIL','/api/products/*','GET','产品详情',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('RES_PRODUCT_LIST','/api/products','GET','产品列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('RES_PRODUCT_UPDATE','/api/products/*','PUT','产品编辑',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('RES_PROD_SUP_AVL','/api/products/support-available','GET','中场支持产品查询',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33','align-2026-04-25','2026-04-25 16:01:33','align-2026-04-25','V1 portal slice'),('RES_PTL_ADDR_LIST','/api/employees','GET','通讯录列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_ADDR_READ','/api/employees/*','GET','员工详情',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_ADDR_SRCH','/api/employees/search','GET','员工搜索',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_ADDR_UPDATE','/api/employees/*','PUT','编辑员工',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_DOC_CREATE','/api/admin/documents','POST','上传文档',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_DOC_DEL','/api/admin/documents/*','DELETE','删除文档',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_DOC_DOWNLOAD','/api/documents/*/download','GET','文档下载',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_DOC_LIST','/api/documents','GET','文档列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_DOC_UPDATE','/api/admin/documents/*','PUT','编辑文档',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_NAV_CREATE','/api/admin/nav','POST','新增导航',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_NAV_DELETE','/api/admin/nav/*','DELETE','删除导航',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_NAV_LIST','/api/nav','GET','导航列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_NAV_SORT','/api/admin/nav/sort','PUT','批量排序',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_NAV_UPDATE','/api/admin/nav/*','PUT','编辑导航',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_PTL_PRD_EXPRT','/api/products/export','GET','导出产品',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_SHORTCUT_LIST','/api/portal/shortcuts','GET','快捷入口列表',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 17:23:17','align-2026-04-25','2026-04-25 17:23:17','align-2026-04-25','V1.1 PT 资源对账补齐'),('RES_SHORTCUT_PUT','/api/portal/shortcuts','PUT','快捷入口全量替换',NULL,0,0,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33','align-2026-04-25','2026-04-25 16:01:33','align-2026-04-25','V1 portal slice'),('RES_WF_APPROVE','/api/workflow/tasks/*/approve','POST','工作流任务审批通过',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_CANCEL','/api/workflow/processes/*/cancel','POST','工作流流程撤回',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_CLAIM','/api/workflow/tasks/*/claim','POST','工作流任务签收',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_DETAIL','/api/workflow/tasks/*','GET','工作流任务详情',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_DONE','/api/workflow/tasks/done','GET','工作流已办列表',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_REJECT','/api/workflow/tasks/*/reject','POST','工作流任务驳回',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_SUBMIT','/api/workflow/processes/submit','POST','工作流流程提交',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_TODO','/api/workflow/tasks','GET','工作流待办列表',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('RES_WF_TRANSFER','/api/workflow/tasks/*/transfer','POST','工作流任务转交',NULL,0,1,'0',NULL,0,'PLATFORM','2026-04-25 16:01:33',NULL,'2026-04-25 16:01:33',NULL,NULL),('R_RPT_DASH_EMP','/api/reports/dashboard/emp/*','GET','员工仪表盘',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M2.3 C.3'),('R_RPT_DASH_ORG','/api/reports/dashboard/org/*','GET','机构仪表盘',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M2.3 C.2'),('R_RPT_DASH_PRES','/api/reports/dashboard/president','GET','行长仪表盘',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M2.2 C.1'),('R_RPT_DQ_EXEC','/api/reports/dynamic-query','POST','动态查询执行',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.2'),('R_RPT_DQ_EXPORT','/api/reports/dynamic-query/export','POST','动态查询导出',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.3 占位'),('R_RPT_EXP_CANCEL','/api/reports/export-tasks/*','DELETE','导出任务取消',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M5.3 E.2'),('R_RPT_EXP_DOWNLOAD','/api/reports/export-tasks/*/download','GET','导出任务下载',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M5.3 E.3'),('R_RPT_EXP_STATUS','/api/reports/export-tasks/*','GET','导出任务状态查询',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M5.3 E.1'),('R_RPT_META_QD','/api/reports/query-dimensions','GET','维度+指标树',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.1'),('R_RPT_SQL_EXEC','/api/reports/sql-probe/execute','POST','SQL 探查执行',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M4.2 D.1'),('R_RPT_SQL_EXP','/api/reports/sql-probe/export','POST','SQL 探查结果导出',NULL,0,0,'0',NULL,1,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M6.1 占位（V1.1+ 启用）'),('R_RPT_SQL_HIST','/api/reports/sql-probe/history','GET','SQL 探查历史列表',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M4.3 D.2'),('R_RPT_SQL_HIST_DTL','/api/reports/sql-probe/history/*','GET','SQL 探查历史详情',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M4.3 D.3'),('R_RPT_SQL_WL','/api/reports/sql-probe/schema-whitelist','GET','SQL 探查白名单展示',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M4.3 D.4'),('R_RPT_SQ_DEL','/api/reports/saved-queries/*','DELETE','删除查询方案',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.5'),('R_RPT_SQ_GET','/api/reports/saved-queries/*','GET','查询方案详情',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.4'),('R_RPT_SQ_LIST','/api/reports/saved-queries','GET','查询方案列表',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.4'),('R_RPT_SQ_SAVE','/api/reports/saved-queries','POST','保存查询方案',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.5'),('R_RPT_SQ_UPD','/api/reports/saved-queries/*','PUT','更新查询方案',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M1.5'),('R_RPT_SUM_CUST_EXP','/api/reports/customer-pool-summary/export','POST','客户池统计导出',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M3.3 C.4'),('R_RPT_SUM_CUST_VW','/api/reports/customer-pool-summary','GET','客户池统计查看',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M3.3 C.4'),('R_RPT_SUM_PERF_EXP','/api/reports/perf-summary/export','POST','绩效汇总导出',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M3.2 C.3'),('R_RPT_SUM_PERF_VW','/api/reports/perf-summary','GET','绩效汇总查看',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M3.2 C.3'),('R_RPT_SUM_TOUCH_EXP','/api/reports/touch-task-summary/export','POST','触达汇总导出',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M3.1 C.2'),('R_RPT_SUM_TOUCH_VW','/api/reports/touch-task-summary','GET','触达汇总查看',NULL,0,0,'0',NULL,0,'RPT','2026-04-25 16:01:33','seed','2026-04-25 16:01:33',NULL,'v1.0 M3.1 C.2'),('W_NC_CREATE','/api/admin/workflow/node-candidates','POST','新增候选人配置',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NC_GET','/api/admin/workflow/node-candidates/item/*','GET','候选人配置详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NC_LIST','/api/admin/workflow/node-candidates','GET','候选人配置列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NC_UPDATE','/api/admin/workflow/node-candidates/*','PUT','更新候选人配置',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_CREATE','/api/admin/workflow/node-forms','POST','新增节点表单',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_GET','/api/admin/workflow/node-forms/item/*','GET','节点表单详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_LIST','/api/admin/workflow/node-forms','GET','节点表单列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_NF_UPDATE','/api/admin/workflow/node-forms/*','PUT','更新节点表单',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_CANCEL','/api/workflow/processes/*/cancel','POST','取消流程',NULL,0,0,'0',NULL,0,'WF','2026-04-30 09:57:04','seed','2026-04-30 09:57:04',NULL,'ProcessCommandController'),('W_PROC_DEFS','/api/admin/workflow/process-definitions','GET','流程定义列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_DETAIL','/api/workflow/processes/*','GET','流程实例详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_DIAGRAM','/api/workflow/processes/*/diagram','GET','流程进度图',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_HISTORY','/api/workflow/processes/*/history','GET','流程历史',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_MAP','/api/workflow/process-map','GET','流程映射查询',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_NODES','/api/workflow/processes/*/nodes','GET','流程节点结构',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_PROC_SUBMIT','/api/workflow/processes/submit','POST','发起流程',NULL,0,0,'0',NULL,0,'WF','2026-04-30 09:57:04','seed','2026-04-30 09:57:04',NULL,'ProcessCommandController'),('W_TASK_APPROVE','/api/workflow/tasks/*/approve','POST','审批通过',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_CLAIM','/api/workflow/tasks/*/claim','POST','签收任务',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_DETAIL','/api/workflow/tasks/*','GET','任务详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_DONE','/api/workflow/tasks/done','GET','已办任务列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_REJECT','/api/workflow/tasks/*/reject','POST','驳回任务',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_TODO','/api/workflow/tasks','GET','待办任务列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TASK_TRANSFER','/api/workflow/tasks/*/transfer','POST','转交任务',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_CREATE','/api/admin/workflow/timeout-rules','POST','新增超时规则',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_GET','/api/admin/workflow/timeout-rules/item/*','GET','超时规则详情',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_LIST','/api/admin/workflow/timeout-rules','GET','超时规则列表',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10'),('W_TR_UPDATE','/api/admin/workflow/timeout-rules/*','PUT','更新超时规则',NULL,0,0,'0',NULL,0,'WF','2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1 aligned 2026-04-10');
/*!40000 ALTER TABLE `PT_RESOURCE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PT_ROLE`
--

DROP TABLE IF EXISTS `PT_ROLE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_ROLE` (
  `ROLE_ID` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `ROLE_CODE` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  `ROLE_CHNAME` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色中文名',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `SYS_CODE` varchar(10) COLLATE utf8mb4_general_ci DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PT_ROLE`
--

LOCK TABLES `PT_ROLE` WRITE;
/*!40000 ALTER TABLE `PT_ROLE` DISABLE KEYS */;
INSERT INTO `PT_ROLE` VALUES ('R_ADMIN','SYS_ADMIN','系统管理员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 超级管理员，运维与权限管理'),('R_BACK_FINANCE','BACK_FINAN','中后台员工(资财)',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 财务会计部等后台支持'),('R_BACK_TECH','BACK_TECH','中后台员工(科技)',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 信息技术部'),('R_BRANCH_MGR','BRANCH_HEA','经营机构负责人',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_CORP_DEPT','CORP_DEPT','公司部人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_CREDIT_APPROVER','CREDIT_APP','授信批复人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_CREDIT_REVIEWER','CREDIT_REV','授信审查人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_PRESIDENT','BRANCH_PRE','分行行长',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 分行最高管理者'),('R_RETAIL_DEPT','RETAIL_DEP','零售部人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 分行零售业务管理部门'),('R_RM','R_RM','客户经理',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_SUPPORT_SEC','SUPPORT_SE','中场支持部门秘书',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 中场支持部门秘书岗'),('R_SUPPORT_STAFF','SUPPORT_ST','中场支持部门人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 中台部门员工');
/*!40000 ALTER TABLE `PT_ROLE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PT_ROLE_BIZ_SCOPE`
--

DROP TABLE IF EXISTS `PT_ROLE_BIZ_SCOPE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_ROLE_BIZ_SCOPE` (
  `ID` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `BIZ_TYPE` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务类型：NAV/PRODUCT/LEAD/CUSTOMER等',
  `DATA_SCOPE` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据范围：SELF_CREATED/SELF/SELF_ASSIGNED/ORG/ORG_SUBTREE/ALL/WORKFLOW_PARTICIPANT',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_pt_role_biz_scope_role_biz` (`ROLE_ID`,`BIZ_TYPE`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_biz_type` (`BIZ_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色业务范围表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PT_ROLE_BIZ_SCOPE`
--

LOCK TABLES `PT_ROLE_BIZ_SCOPE` WRITE;
/*!40000 ALTER TABLE `PT_ROLE_BIZ_SCOPE` DISABLE KEYS */;
INSERT INTO `PT_ROLE_BIZ_SCOPE` VALUES ('b6d4ea5b396211f1a12bc84d4421b4d8','R_BACK_TECH','PERF_CONFIG','ALL',0,'2026-04-16 15:05:56','seed','2026-04-16 15:05:56',NULL,'perf v1.0 - full access'),('d79564de349011f191754c496c37265b','R_BACK_FINANCE','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795651b349011f191754c496c37265b','R_BRANCH_MGR','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956528349011f191754c496c37265b','R_CORP_DEPT','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956536349011f191754c496c37265b','R_CREDIT_APPROVER','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956542349011f191754c496c37265b','R_CREDIT_REVIEWER','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795654e349011f191754c496c37265b','R_PRESIDENT','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795655b349011f191754c496c37265b','R_RETAIL_DEPT','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956566349011f191754c496c37265b','R_RM','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d7956574349011f191754c496c37265b','R_SUPPORT_SEC','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('d795657f349011f191754c496c37265b','R_SUPPORT_STAFF','SYS_CONFIG','SELF',0,'2026-04-10 11:53:32','seed','2026-04-10 11:53:32',NULL,'auto-fill for current-user/check-permission endpoints'),('da68abe1348b11f191754c496c37265b','R_ADMIN','ORG','ALL',0,'2026-04-10 11:17:49','seed','2026-04-10 11:17:49',NULL,'v1'),('FER_SCOPE_BM_LOAN','R_BRANCH_MGR','LOAN','ORG',0,'2026-04-25 16:01:33','flowable-real-env','2026-04-25 16:01:33','flowable-real-env','flowable real env scope'),('S_ADMIN_ADDRBOOK','R_ADMIN','ADDRBOOK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_CLAIM','R_ADMIN','CLAIM','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_CUSTOMER','R_ADMIN','CUSTOMER','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_CUSTOMER_POOL','R_ADMIN','CUSTOMER_POOL','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_DOC','R_ADMIN','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_LEAD','R_ADMIN','LEAD','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_LOAN','R_ADMIN','LOAN','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_NAV','R_ADMIN','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_PERF_CONFIG','R_ADMIN','PERF_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-16 15:05:56','seed','perf v1.0 - full access'),('S_ADMIN_PRODUCT','R_ADMIN','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_REPORT','R_ADMIN','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_SUPPORT','R_ADMIN','SUPPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_SUPPORT_DEPT','R_ADMIN','SUPPORT_DEPT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_SYS_CONFIG','R_ADMIN','SYS_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_TAG','R_ADMIN','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_TOUCH_REPORT','R_ADMIN','TOUCH_REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_ADMIN_TOUCH_TASK','R_ADMIN','TOUCH_TASK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BF_NAV','R_BACK_FINANCE','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BF_PERF_CONFIG','R_BACK_FINANCE','PERF_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BF_REPORT','R_BACK_FINANCE','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_ADDRBOOK','R_BRANCH_MGR','ADDRBOOK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_BM_CUSTOMER','R_BRANCH_MGR','CUSTOMER','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_DOC','R_BRANCH_MGR','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_BM_LEAD','R_BRANCH_MGR','LEAD','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_NAV','R_BRANCH_MGR','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_PRODUCT','R_BRANCH_MGR','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_BM_REPORT','R_BRANCH_MGR','REPORT','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_SUPPORT','R_BRANCH_MGR','SUPPORT','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BM_TOUCH_REPORT','R_BRANCH_MGR','TOUCH_REPORT','ORG_SUBTREE',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BRANCH_PRE_TAG','R_PRESIDENT','TAG','ALL',0,NULL,NULL,NULL,NULL,NULL),('S_BT_DOC','R_BACK_TECH','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BT_NAV','R_BACK_TECH','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_BT_SYS_CONFIG','R_BACK_TECH','SYS_CONFIG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CAP_LOAN','R_CREDIT_APPROVER','LOAN','ALL',0,'2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env scope'),('S_CAP_NAV','R_CREDIT_APPROVER','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_CUSTOMER','R_CORP_DEPT','CUSTOMER','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_LEAD','R_CORP_DEPT','LEAD','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_LOAN','R_CORP_DEPT','LOAN','ALL',0,'2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env scope'),('S_CD_NAV','R_CORP_DEPT','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_REPORT','R_CORP_DEPT','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_TAG','R_CORP_DEPT','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CD_TOUCH_REPORT','R_CORP_DEPT','TOUCH_REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_CRV_LOAN','R_CREDIT_REVIEWER','LOAN','ALL',0,'2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env scope'),('S_CRV_NAV','R_CREDIT_REVIEWER','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_PR_NAV','R_PRESIDENT','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_PR_REPORT','R_PRESIDENT','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_CUSTOMER','R_RETAIL_DEPT','CUSTOMER','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_LEAD','R_RETAIL_DEPT','LEAD','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_LOAN','R_RETAIL_DEPT','LOAN','WORKFLOW_PARTICIPANT',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_NAV','R_RETAIL_DEPT','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_REPORT','R_RETAIL_DEPT','REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_TAG','R_RETAIL_DEPT','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RD_TOUCH_REPORT','R_RETAIL_DEPT','TOUCH_REPORT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_ADDRBOOK','R_RM','ADDRBOOK','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_CLAIM','R_RM','CLAIM','ORG',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_CUSTOMER_POOL','R_RM','CUSTOMER_POOL','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_DOC','R_RM','DOC','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_LEAD','R_RM','LEAD','SELF_CREATED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_LOAN','R_RM','LOAN','SELF_CREATED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_NAV','R_RM','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_PRODUCT','R_RM','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_REPORT','R_RM','REPORT','SELF',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_SUPPORT','R_RM','SUPPORT','SELF_CREATED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_TAG','R_RM','TAG','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_RM_TOUCH_TASK','R_RM','TOUCH_TASK','SELF_ASSIGNED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SF_NAV','R_SUPPORT_STAFF','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SF_SUPPORT_DEPT','R_SUPPORT_STAFF','SUPPORT_DEPT','SELF_ASSIGNED',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SS_NAV','R_SUPPORT_SEC','NAV','ALL',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1'),('S_SS_PRODUCT','R_SUPPORT_SEC','PRODUCT','ALL',0,'2026-04-03 22:43:46','seed','2026-04-03 22:43:46',NULL,'V1 seed'),('S_SS_SUPPORT_DEPT','R_SUPPORT_SEC','SUPPORT_DEPT','ORG',0,'2026-04-03 22:43:46','seed','2026-04-10 11:17:49','seed','v1');
/*!40000 ALTER TABLE `PT_ROLE_BIZ_SCOPE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PT_ROLE_RESOURCE`
--

DROP TABLE IF EXISTS `PT_ROLE_RESOURCE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_ROLE_RESOURCE` (
  `ID` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `RESOURCE_ID` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源ID',
  `SYS_CODE` varchar(10) COLLATE utf8mb4_general_ci DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_resource_id` (`RESOURCE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色资源关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PT_ROLE_RESOURCE`
--

LOCK TABLES `PT_ROLE_RESOURCE` WRITE;
/*!40000 ALTER TABLE `PT_ROLE_RESOURCE` DISABLE KEYS */;
INSERT INTO `PT_ROLE_RESOURCE` VALUES ('da66408c348b11f191754c496c37265b','R_ADMIN','A_BZ_DELETE','AUTH','2026-04-10 11:17:49'),('da6647a9348b11f191754c496c37265b','R_ADMIN','A_BZ_LIST','AUTH','2026-04-10 11:17:49'),('da664882348b11f191754c496c37265b','R_ADMIN','A_BZ_MATRIX','AUTH','2026-04-10 11:17:49'),('da66491a348b11f191754c496c37265b','R_ADMIN','A_BZ_SAVE','AUTH','2026-04-10 11:17:49'),('da6649a7348b11f191754c496c37265b','R_ADMIN','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da664a37348b11f191754c496c37265b','R_ADMIN','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da664cd1348b11f191754c496c37265b','R_ADMIN','A_LOGIN','AUTH','2026-04-10 11:17:49'),('da664dbf348b11f191754c496c37265b','R_ADMIN','A_LOGOUT','AUTH','2026-04-10 11:17:49'),('da664e5a348b11f191754c496c37265b','R_ADMIN','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da664edc348b11f191754c496c37265b','R_ADMIN','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da664f56348b11f191754c496c37265b','R_ADMIN','A_ORG_USERS','AUTH','2026-04-10 11:17:49'),('da664fd2348b11f191754c496c37265b','R_ADMIN','A_PERMS','AUTH','2026-04-10 11:17:49'),('da665042348b11f191754c496c37265b','R_ADMIN','A_RES_CREATE','AUTH','2026-04-10 11:17:49'),('da6650b7348b11f191754c496c37265b','R_ADMIN','A_RES_DELETE','AUTH','2026-04-10 11:17:49'),('da66513b348b11f191754c496c37265b','R_ADMIN','A_RES_TREE','AUTH','2026-04-10 11:17:49'),('da6651ba348b11f191754c496c37265b','R_ADMIN','A_RES_UPDATE','AUTH','2026-04-10 11:17:49'),('da66522d348b11f191754c496c37265b','R_ADMIN','A_ROLE_CREATE','AUTH','2026-04-10 11:17:49'),('da6652aa348b11f191754c496c37265b','R_ADMIN','A_ROLE_DELETE','AUTH','2026-04-10 11:17:49'),('da66531f348b11f191754c496c37265b','R_ADMIN','A_ROLE_LIST','AUTH','2026-04-10 11:17:49'),('da665390348b11f191754c496c37265b','R_ADMIN','A_ROLE_UPDATE','AUTH','2026-04-10 11:17:49'),('da665402348b11f191754c496c37265b','R_ADMIN','A_ROLE_USERS','AUTH','2026-04-10 11:17:49'),('da665477348b11f191754c496c37265b','R_ADMIN','A_RR_BIND','AUTH','2026-04-10 11:17:49'),('da6654e9348b11f191754c496c37265b','R_ADMIN','A_RR_LIST','AUTH','2026-04-10 11:17:49'),('da66555a348b11f191754c496c37265b','R_ADMIN','A_RR_REPLACE','AUTH','2026-04-10 11:17:49'),('da6655cc348b11f191754c496c37265b','R_ADMIN','A_UR_BIND','AUTH','2026-04-10 11:17:49'),('da66563d348b11f191754c496c37265b','R_ADMIN','A_UR_DEL','AUTH','2026-04-10 11:17:49'),('da6656af348b11f191754c496c37265b','R_ADMIN','A_UR_LIST','AUTH','2026-04-10 11:17:49'),('da66571f348b11f191754c496c37265b','R_ADMIN','G_AUDIT_DETAIL','GOV','2026-04-10 11:17:49'),('da665794348b11f191754c496c37265b','R_ADMIN','G_AUDIT_EXPORT','GOV','2026-04-10 11:17:49'),('da66580a348b11f191754c496c37265b','R_ADMIN','G_AUDIT_LIST','GOV','2026-04-10 11:17:49'),('da665881348b11f191754c496c37265b','R_ADMIN','G_CAL_GET','GOV','2026-04-10 11:17:49'),('da6658f3348b11f191754c496c37265b','R_ADMIN','G_CAL_IMPORT','GOV','2026-04-10 11:17:49'),('da665963348b11f191754c496c37265b','R_ADMIN','G_CAL_INIT','GOV','2026-04-10 11:17:49'),('da6659e1348b11f191754c496c37265b','R_ADMIN','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da665a53348b11f191754c496c37265b','R_ADMIN','G_CAL_SET','GOV','2026-04-10 11:17:49'),('da665ac6348b11f191754c496c37265b','R_ADMIN','G_CFG_LIST','GOV','2026-04-10 11:17:49'),('da665b3a348b11f191754c496c37265b','R_ADMIN','G_CFG_UPDATE','GOV','2026-04-10 11:17:49'),('da665bae348b11f191754c496c37265b','R_ADMIN','G_DICT_CREATE','GOV','2026-04-10 11:17:49'),('da665c20348b11f191754c496c37265b','R_ADMIN','G_DICT_DELETE','GOV','2026-04-10 11:17:49'),('da665c94348b11f191754c496c37265b','R_ADMIN','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da665d06348b11f191754c496c37265b','R_ADMIN','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da665d7b348b11f191754c496c37265b','R_ADMIN','G_DICT_STATUS','GOV','2026-04-10 11:17:49'),('da665deb348b11f191754c496c37265b','R_ADMIN','G_DICT_UPDATE','GOV','2026-04-10 11:17:49'),('da665e5d348b11f191754c496c37265b','R_ADMIN','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da665ed0348b11f191754c496c37265b','R_ADMIN','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da665f49348b11f191754c496c37265b','R_ADMIN','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da665fb7348b11f191754c496c37265b','R_ADMIN','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da666029348b11f191754c496c37265b','R_ADMIN','G_JOB_LIST','GOV','2026-04-10 11:17:49'),('da666098348b11f191754c496c37265b','R_ADMIN','G_JOB_LOGS','GOV','2026-04-10 11:17:49'),('da66614a348b11f191754c496c37265b','R_ADMIN','G_JOB_PAUSE','GOV','2026-04-10 11:17:49'),('da6661c0348b11f191754c496c37265b','R_ADMIN','G_JOB_RESUME','GOV','2026-04-10 11:17:49'),('da666235348b11f191754c496c37265b','R_ADMIN','G_JOB_TRIGGER','GOV','2026-04-10 11:17:49'),('da6662ae348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da666321348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da666393348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da666406348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da66647c348b11f191754c496c37265b','R_ADMIN','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da6664f0348b11f191754c496c37265b','R_ADMIN','G_SQL_EXEC','GOV','2026-04-10 11:17:49'),('da666564348b11f191754c496c37265b','R_ADMIN','G_SQL_HIST','GOV','2026-04-10 11:17:49'),('da667bec348b11f191754c496c37265b','R_ADMIN','W_NC_CREATE','WF','2026-04-10 11:17:49'),('da667d1d348b11f191754c496c37265b','R_ADMIN','W_NC_GET','WF','2026-04-10 11:17:49'),('da667e25348b11f191754c496c37265b','R_ADMIN','W_NC_LIST','WF','2026-04-10 11:17:49'),('da667f1d348b11f191754c496c37265b','R_ADMIN','W_NC_UPDATE','WF','2026-04-10 11:17:49'),('da66800c348b11f191754c496c37265b','R_ADMIN','W_NF_CREATE','WF','2026-04-10 11:17:49'),('da6680fa348b11f191754c496c37265b','R_ADMIN','W_NF_GET','WF','2026-04-10 11:17:49'),('da668201348b11f191754c496c37265b','R_ADMIN','W_NF_LIST','WF','2026-04-10 11:17:49'),('da6682ee348b11f191754c496c37265b','R_ADMIN','W_NF_UPDATE','WF','2026-04-10 11:17:49'),('da6683d5348b11f191754c496c37265b','R_ADMIN','W_PROC_DEFS','WF','2026-04-10 11:17:49'),('da6684b9348b11f191754c496c37265b','R_ADMIN','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6685d0348b11f191754c496c37265b','R_ADMIN','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6686bb348b11f191754c496c37265b','R_ADMIN','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6689a4348b11f191754c496c37265b','R_ADMIN','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da668aa0348b11f191754c496c37265b','R_ADMIN','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da668b96348b11f191754c496c37265b','R_ADMIN','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da668c88348b11f191754c496c37265b','R_ADMIN','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da668d7b348b11f191754c496c37265b','R_ADMIN','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da668e63348b11f191754c496c37265b','R_ADMIN','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da66900a348b11f191754c496c37265b','R_ADMIN','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da66910d348b11f191754c496c37265b','R_ADMIN','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6691fe348b11f191754c496c37265b','R_ADMIN','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6692d7348b11f191754c496c37265b','R_ADMIN','W_TR_CREATE','WF','2026-04-10 11:17:49'),('da6693b7348b11f191754c496c37265b','R_ADMIN','W_TR_GET','WF','2026-04-10 11:17:49'),('da66948b348b11f191754c496c37265b','R_ADMIN','W_TR_LIST','WF','2026-04-10 11:17:49'),('da669562348b11f191754c496c37265b','R_ADMIN','W_TR_UPDATE','WF','2026-04-10 11:17:49'),('da66a7f6348b11f191754c496c37265b','R_BACK_TECH','A_BZ_DELETE','AUTH','2026-04-10 11:17:49'),('da66a98a348b11f191754c496c37265b','R_BACK_TECH','A_BZ_LIST','AUTH','2026-04-10 11:17:49'),('da66aa31348b11f191754c496c37265b','R_BACK_TECH','A_BZ_MATRIX','AUTH','2026-04-10 11:17:49'),('da66aac9348b11f191754c496c37265b','R_BACK_TECH','A_BZ_SAVE','AUTH','2026-04-10 11:17:49'),('da66ab45348b11f191754c496c37265b','R_BACK_TECH','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da66abbd348b11f191754c496c37265b','R_BACK_TECH','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da66ac2e348b11f191754c496c37265b','R_BACK_TECH','A_LOGIN','AUTH','2026-04-10 11:17:49'),('da66ac9e348b11f191754c496c37265b','R_BACK_TECH','A_LOGOUT','AUTH','2026-04-10 11:17:49'),('da66ad21348b11f191754c496c37265b','R_BACK_TECH','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da66c7b1348b11f191754c496c37265b','R_BACK_TECH','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da66c87c348b11f191754c496c37265b','R_BACK_TECH','A_ORG_USERS','AUTH','2026-04-10 11:17:49'),('da66c903348b11f191754c496c37265b','R_BACK_TECH','A_PERMS','AUTH','2026-04-10 11:17:49'),('da66c97a348b11f191754c496c37265b','R_BACK_TECH','A_RES_CREATE','AUTH','2026-04-10 11:17:49'),('da66c9f2348b11f191754c496c37265b','R_BACK_TECH','A_RES_DELETE','AUTH','2026-04-10 11:17:49'),('da66ca63348b11f191754c496c37265b','R_BACK_TECH','A_RES_TREE','AUTH','2026-04-10 11:17:49'),('da66cad4348b11f191754c496c37265b','R_BACK_TECH','A_RES_UPDATE','AUTH','2026-04-10 11:17:49'),('da66cb4e348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_CREATE','AUTH','2026-04-10 11:17:49'),('da66cbbd348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_DELETE','AUTH','2026-04-10 11:17:49'),('da66cc30348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_LIST','AUTH','2026-04-10 11:17:49'),('da66cca1348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_UPDATE','AUTH','2026-04-10 11:17:49'),('da66cd0e348b11f191754c496c37265b','R_BACK_TECH','A_ROLE_USERS','AUTH','2026-04-10 11:17:49'),('da66cd97348b11f191754c496c37265b','R_BACK_TECH','A_RR_BIND','AUTH','2026-04-10 11:17:49'),('da66ce09348b11f191754c496c37265b','R_BACK_TECH','A_RR_LIST','AUTH','2026-04-10 11:17:49'),('da66ce7e348b11f191754c496c37265b','R_BACK_TECH','A_RR_REPLACE','AUTH','2026-04-10 11:17:49'),('da66ceeb348b11f191754c496c37265b','R_BACK_TECH','A_UR_BIND','AUTH','2026-04-10 11:17:49'),('da66cf57348b11f191754c496c37265b','R_BACK_TECH','A_UR_DEL','AUTH','2026-04-10 11:17:49'),('da66cfc2348b11f191754c496c37265b','R_BACK_TECH','A_UR_LIST','AUTH','2026-04-10 11:17:49'),('da66d035348b11f191754c496c37265b','R_BACK_TECH','G_AUDIT_DETAIL','GOV','2026-04-10 11:17:49'),('da66d0a9348b11f191754c496c37265b','R_BACK_TECH','G_AUDIT_EXPORT','GOV','2026-04-10 11:17:49'),('da66d1c9348b11f191754c496c37265b','R_BACK_TECH','G_AUDIT_LIST','GOV','2026-04-10 11:17:49'),('da66d240348b11f191754c496c37265b','R_BACK_TECH','G_CAL_GET','GOV','2026-04-10 11:17:49'),('da66d2c3348b11f191754c496c37265b','R_BACK_TECH','G_CAL_IMPORT','GOV','2026-04-10 11:17:49'),('da66d337348b11f191754c496c37265b','R_BACK_TECH','G_CAL_INIT','GOV','2026-04-10 11:17:49'),('da66d3a9348b11f191754c496c37265b','R_BACK_TECH','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da66d419348b11f191754c496c37265b','R_BACK_TECH','G_CAL_SET','GOV','2026-04-10 11:17:49'),('da66d48a348b11f191754c496c37265b','R_BACK_TECH','G_CFG_LIST','GOV','2026-04-10 11:17:49'),('da66d4f6348b11f191754c496c37265b','R_BACK_TECH','G_CFG_UPDATE','GOV','2026-04-10 11:17:49'),('da66d564348b11f191754c496c37265b','R_BACK_TECH','G_DICT_CREATE','GOV','2026-04-10 11:17:49'),('da66d5d2348b11f191754c496c37265b','R_BACK_TECH','G_DICT_DELETE','GOV','2026-04-10 11:17:49'),('da66d64b348b11f191754c496c37265b','R_BACK_TECH','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da66d6ba348b11f191754c496c37265b','R_BACK_TECH','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da66d728348b11f191754c496c37265b','R_BACK_TECH','G_DICT_STATUS','GOV','2026-04-10 11:17:49'),('da66d798348b11f191754c496c37265b','R_BACK_TECH','G_DICT_UPDATE','GOV','2026-04-10 11:17:49'),('da66d80b348b11f191754c496c37265b','R_BACK_TECH','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da66d878348b11f191754c496c37265b','R_BACK_TECH','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da66d8eb348b11f191754c496c37265b','R_BACK_TECH','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da66d962348b11f191754c496c37265b','R_BACK_TECH','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da66d9d5348b11f191754c496c37265b','R_BACK_TECH','G_JOB_LIST','GOV','2026-04-10 11:17:49'),('da66da44348b11f191754c496c37265b','R_BACK_TECH','G_JOB_LOGS','GOV','2026-04-10 11:17:49'),('da66ec72348b11f191754c496c37265b','R_BACK_TECH','G_JOB_PAUSE','GOV','2026-04-10 11:17:49'),('da66ed28348b11f191754c496c37265b','R_BACK_TECH','G_JOB_RESUME','GOV','2026-04-10 11:17:49'),('da66ed9b348b11f191754c496c37265b','R_BACK_TECH','G_JOB_TRIGGER','GOV','2026-04-10 11:17:49'),('da66ee05348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da66ee78348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da66eee3348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da66ef52348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da66efb8348b11f191754c496c37265b','R_BACK_TECH','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da66f021348b11f191754c496c37265b','R_BACK_TECH','G_SQL_EXEC','GOV','2026-04-10 11:17:49'),('da66f091348b11f191754c496c37265b','R_BACK_TECH','G_SQL_HIST','GOV','2026-04-10 11:17:49'),('da66fc3f348b11f191754c496c37265b','R_BACK_TECH','W_NC_CREATE','WF','2026-04-10 11:17:49'),('da66fcc9348b11f191754c496c37265b','R_BACK_TECH','W_NC_GET','WF','2026-04-10 11:17:49'),('da66fd4c348b11f191754c496c37265b','R_BACK_TECH','W_NC_LIST','WF','2026-04-10 11:17:49'),('da66fdd2348b11f191754c496c37265b','R_BACK_TECH','W_NC_UPDATE','WF','2026-04-10 11:17:49'),('da66fe42348b11f191754c496c37265b','R_BACK_TECH','W_NF_CREATE','WF','2026-04-10 11:17:49'),('da66feb0348b11f191754c496c37265b','R_BACK_TECH','W_NF_GET','WF','2026-04-10 11:17:49'),('da66ff1f348b11f191754c496c37265b','R_BACK_TECH','W_NF_LIST','WF','2026-04-10 11:17:49'),('da66ff8d348b11f191754c496c37265b','R_BACK_TECH','W_NF_UPDATE','WF','2026-04-10 11:17:49'),('da66fffa348b11f191754c496c37265b','R_BACK_TECH','W_PROC_DEFS','WF','2026-04-10 11:17:49'),('da670fc2348b11f191754c496c37265b','R_BACK_TECH','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da67106f348b11f191754c496c37265b','R_BACK_TECH','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6710e6348b11f191754c496c37265b','R_BACK_TECH','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da67115e348b11f191754c496c37265b','R_BACK_TECH','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6711d2348b11f191754c496c37265b','R_BACK_TECH','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da671240348b11f191754c496c37265b','R_BACK_TECH','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da6712b1348b11f191754c496c37265b','R_BACK_TECH','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da671322348b11f191754c496c37265b','R_BACK_TECH','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da671392348b11f191754c496c37265b','R_BACK_TECH','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da671400348b11f191754c496c37265b','R_BACK_TECH','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da67146a348b11f191754c496c37265b','R_BACK_TECH','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6714dc348b11f191754c496c37265b','R_BACK_TECH','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da671548348b11f191754c496c37265b','R_BACK_TECH','W_TR_CREATE','WF','2026-04-10 11:17:49'),('da672995348b11f191754c496c37265b','R_BACK_TECH','W_TR_GET','WF','2026-04-10 11:17:49'),('da672a92348b11f191754c496c37265b','R_BACK_TECH','W_TR_LIST','WF','2026-04-10 11:17:49'),('da672b05348b11f191754c496c37265b','R_BACK_TECH','W_TR_UPDATE','WF','2026-04-10 11:17:49'),('da674945348b11f191754c496c37265b','R_SUPPORT_STAFF','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674a92348b11f191754c496c37265b','R_SUPPORT_SEC','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674b10348b11f191754c496c37265b','R_RM','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674b7e348b11f191754c496c37265b','R_RETAIL_DEPT','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674be2348b11f191754c496c37265b','R_PRESIDENT','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674c4b348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674cab348b11f191754c496c37265b','R_CREDIT_APPROVER','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674d07348b11f191754c496c37265b','R_CORP_DEPT','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674d63348b11f191754c496c37265b','R_BRANCH_MGR','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da674df5348b11f191754c496c37265b','R_BACK_FINANCE','A_CHECK_PERM','AUTH','2026-04-10 11:17:49'),('da676905348b11f191754c496c37265b','R_SUPPORT_STAFF','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da6769aa348b11f191754c496c37265b','R_SUPPORT_SEC','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677844348b11f191754c496c37265b','R_RM','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da6778e8348b11f191754c496c37265b','R_RETAIL_DEPT','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677957348b11f191754c496c37265b','R_PRESIDENT','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da6779cf348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677aad348b11f191754c496c37265b','R_CREDIT_APPROVER','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677b1a348b11f191754c496c37265b','R_CORP_DEPT','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677b83348b11f191754c496c37265b','R_BRANCH_MGR','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677beb348b11f191754c496c37265b','R_BACK_FINANCE','A_CURR_USER','AUTH','2026-04-10 11:17:49'),('da677cc8348b11f191754c496c37265b','R_SUPPORT_STAFF','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da677d3f348b11f191754c496c37265b','R_SUPPORT_SEC','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da677eae348b11f191754c496c37265b','R_RM','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da677f91348b11f191754c496c37265b','R_RETAIL_DEPT','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da678000348b11f191754c496c37265b','R_PRESIDENT','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6780f5348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6781cf348b11f191754c496c37265b','R_CREDIT_APPROVER','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da67823d348b11f191754c496c37265b','R_CORP_DEPT','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6782a3348b11f191754c496c37265b','R_BRANCH_MGR','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da678306348b11f191754c496c37265b','R_BACK_FINANCE','A_ORG_SUBTREE','AUTH','2026-04-10 11:17:49'),('da6783c9348b11f191754c496c37265b','R_SUPPORT_STAFF','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678445348b11f191754c496c37265b','R_SUPPORT_SEC','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da67853b348b11f191754c496c37265b','R_RM','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da6785aa348b11f191754c496c37265b','R_RETAIL_DEPT','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678618348b11f191754c496c37265b','R_PRESIDENT','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678685348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da6786e7348b11f191754c496c37265b','R_CREDIT_APPROVER','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678747348b11f191754c496c37265b','R_CORP_DEPT','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da6787a8348b11f191754c496c37265b','R_BRANCH_MGR','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678807348b11f191754c496c37265b','R_BACK_FINANCE','A_ORG_TREE','AUTH','2026-04-10 11:17:49'),('da678898348b11f191754c496c37265b','R_SUPPORT_STAFF','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678900348b11f191754c496c37265b','R_SUPPORT_SEC','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678963348b11f191754c496c37265b','R_RM','A_PERMS','AUTH','2026-04-10 11:17:49'),('da6789c3348b11f191754c496c37265b','R_RETAIL_DEPT','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678a23348b11f191754c496c37265b','R_PRESIDENT','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678a91348b11f191754c496c37265b','R_CREDIT_REVIEWER','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678af5348b11f191754c496c37265b','R_CREDIT_APPROVER','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678b56348b11f191754c496c37265b','R_CORP_DEPT','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678bb8348b11f191754c496c37265b','R_BRANCH_MGR','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678c19348b11f191754c496c37265b','R_BACK_FINANCE','A_PERMS','AUTH','2026-04-10 11:17:49'),('da678dd9348b11f191754c496c37265b','R_SUPPORT_STAFF','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da678e47348b11f191754c496c37265b','R_SUPPORT_SEC','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da679014348b11f191754c496c37265b','R_RM','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da6790ac348b11f191754c496c37265b','R_RETAIL_DEPT','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da679119348b11f191754c496c37265b','R_PRESIDENT','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da6791a7348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da67920f348b11f191754c496c37265b','R_CREDIT_APPROVER','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da67926b348b11f191754c496c37265b','R_CORP_DEPT','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da6792c5348b11f191754c496c37265b','R_BRANCH_MGR','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da679323348b11f191754c496c37265b','R_BACK_FINANCE','G_CAL_PUBLIC','GOV','2026-04-10 11:17:49'),('da67943a348b11f191754c496c37265b','R_SUPPORT_STAFF','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6794a4348b11f191754c496c37265b','R_SUPPORT_SEC','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da679503348b11f191754c496c37265b','R_RM','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da67955c348b11f191754c496c37265b','R_RETAIL_DEPT','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6795b5348b11f191754c496c37265b','R_PRESIDENT','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da67960f348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da679668348b11f191754c496c37265b','R_CREDIT_APPROVER','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6796c5348b11f191754c496c37265b','R_CORP_DEPT','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da67971f348b11f191754c496c37265b','R_BRANCH_MGR','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da679777348b11f191754c496c37265b','R_BACK_FINANCE','G_DICT_ITEMS','GOV','2026-04-10 11:17:49'),('da6797e1348b11f191754c496c37265b','R_SUPPORT_STAFF','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679841348b11f191754c496c37265b','R_SUPPORT_SEC','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da67989b348b11f191754c496c37265b','R_RM','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679d0f348b11f191754c496c37265b','R_RETAIL_DEPT','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679e00348b11f191754c496c37265b','R_PRESIDENT','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679e6f348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679ed6348b11f191754c496c37265b','R_CREDIT_APPROVER','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679f38348b11f191754c496c37265b','R_CORP_DEPT','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679f9b348b11f191754c496c37265b','R_BRANCH_MGR','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da679ffc348b11f191754c496c37265b','R_BACK_FINANCE','G_DICT_LIST','GOV','2026-04-10 11:17:49'),('da67a0c2348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a274348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a33f348b11f191754c496c37265b','R_RM','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a3a6348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a405348b11f191754c496c37265b','R_PRESIDENT','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a466348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a4c4348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a522348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a57f348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a5f1348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_DELETE','GOV','2026-04-10 11:17:49'),('da67a69c348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a701348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a75c348b11f191754c496c37265b','R_RM','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a7b7348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a813348b11f191754c496c37265b','R_PRESIDENT','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a86c348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a8c8348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a92f348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67a989348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67abd4348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_DOWNLOAD','GOV','2026-04-10 11:17:49'),('da67ad89348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67ae34348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67aeb7348b11f191754c496c37265b','R_RM','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67af41348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67afad348b11f191754c496c37265b','R_PRESIDENT','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b009348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b064348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b0bf348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b117348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b170348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_LIST','GOV','2026-04-10 11:17:49'),('da67b1e9348b11f191754c496c37265b','R_SUPPORT_STAFF','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b24e348b11f191754c496c37265b','R_SUPPORT_SEC','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b2ab348b11f191754c496c37265b','R_RM','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b305348b11f191754c496c37265b','R_RETAIL_DEPT','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b35f348b11f191754c496c37265b','R_PRESIDENT','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b3b9348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b412348b11f191754c496c37265b','R_CREDIT_APPROVER','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b475348b11f191754c496c37265b','R_CORP_DEPT','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b517348b11f191754c496c37265b','R_BRANCH_MGR','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b57b348b11f191754c496c37265b','R_BACK_FINANCE','G_FILE_UPLOAD','GOV','2026-04-10 11:17:49'),('da67b63a348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b69d348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b6f9348b11f191754c496c37265b','R_RM','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b754348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b7af348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b80b348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b867348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b8d2348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b931348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67b997348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_COUNT','GOV','2026-04-10 11:17:49'),('da67ba04348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67ba65348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bac4348b11f191754c496c37265b','R_RM','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bb20348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bb79348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67bbd2348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67ce5f348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67cf1d348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67cf8c348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67cff8348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_DETAIL','GOV','2026-04-10 11:17:49'),('da67d090348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e542348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e5de348b11f191754c496c37265b','R_RM','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e641348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e698348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e7b7348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e81b348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e877348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e8cf348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e924348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_LIST','GOV','2026-04-10 11:17:49'),('da67e9b1348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ea0c348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ea63348b11f191754c496c37265b','R_RM','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67eabe348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67eb16348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67eb6b348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ebc1348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ec16348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ec6e348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ecc4348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_READ','GOV','2026-04-10 11:17:49'),('da67ed2d348b11f191754c496c37265b','R_SUPPORT_STAFF','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ed94348b11f191754c496c37265b','R_SUPPORT_SEC','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67edec348b11f191754c496c37265b','R_RM','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ee43348b11f191754c496c37265b','R_RETAIL_DEPT','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ee9c348b11f191754c496c37265b','R_PRESIDENT','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67eef2348b11f191754c496c37265b','R_CREDIT_REVIEWER','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ef48348b11f191754c496c37265b','R_CREDIT_APPROVER','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ef9e348b11f191754c496c37265b','R_CORP_DEPT','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67eff3348b11f191754c496c37265b','R_BRANCH_MGR','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67f04b348b11f191754c496c37265b','R_BACK_FINANCE','G_NOTIFY_READ_ALL','GOV','2026-04-10 11:17:49'),('da67ff1c348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6801d5348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680241348b11f191754c496c37265b','R_RM','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da68030d348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680367348b11f191754c496c37265b','R_PRESIDENT','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6803c7348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680423348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680479348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da6804cf348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da68052c348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_DETAIL','WF','2026-04-10 11:17:49'),('da680599348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6805f7348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da681f17348b11f191754c496c37265b','R_RM','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da681fbd348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da682020348b11f191754c496c37265b','R_PRESIDENT','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da68207e348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6820dd348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da682137348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da68218f348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da6821e9348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_DIAGRAM','WF','2026-04-10 11:17:49'),('da682284348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6822f6348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da682351348b11f191754c496c37265b','R_RM','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6823a8348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6823fc348b11f191754c496c37265b','R_PRESIDENT','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da682456348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6824ab348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6824ff348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da682559348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da6825b0348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_HISTORY','WF','2026-04-10 11:17:49'),('da68261a348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682677348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6826e6348b11f191754c496c37265b','R_RM','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da68273f348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6827ab348b11f191754c496c37265b','R_PRESIDENT','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682804348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da68285b348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6828f0348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682947348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da6829a4348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_MAP','WF','2026-04-10 11:17:49'),('da682a13348b11f191754c496c37265b','R_SUPPORT_STAFF','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682a71348b11f191754c496c37265b','R_SUPPORT_SEC','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682ace348b11f191754c496c37265b','R_RM','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682b28348b11f191754c496c37265b','R_RETAIL_DEPT','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682b7d348b11f191754c496c37265b','R_PRESIDENT','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682bd4348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682c29348b11f191754c496c37265b','R_CREDIT_APPROVER','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682c80348b11f191754c496c37265b','R_CORP_DEPT','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da682cd7348b11f191754c496c37265b','R_BRANCH_MGR','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da683b92348b11f191754c496c37265b','R_BACK_FINANCE','W_PROC_NODES','WF','2026-04-10 11:17:49'),('da683c6e348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683cd7348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683d31348b11f191754c496c37265b','R_RM','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683d8c348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683de4348b11f191754c496c37265b','R_PRESIDENT','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683e3d348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683e94348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683eed348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683f48348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da683fa4348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_APPROVE','WF','2026-04-10 11:17:49'),('da68400b348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684066348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6840bc348b11f191754c496c37265b','R_RM','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da68414b348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6841a4348b11f191754c496c37265b','R_PRESIDENT','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6841fd348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684252348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6842a9348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684300348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da684357348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_CLAIM','WF','2026-04-10 11:17:49'),('da6843be348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684419348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684470348b11f191754c496c37265b','R_RM','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da6844c5348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da68451b348b11f191754c496c37265b','R_PRESIDENT','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684572348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da6845c7348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da68461e348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684676348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da6846d0348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_DETAIL','WF','2026-04-10 11:17:49'),('da684738348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da68479b348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6847f3348b11f191754c496c37265b','R_RM','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da68484a348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6848a1348b11f191754c496c37265b','R_PRESIDENT','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6848f9348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da68494f348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6849a6348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da6849fb348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da684a53348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_DONE','WF','2026-04-10 11:17:49'),('da684ac1348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684b1e348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684b7f348b11f191754c496c37265b','R_RM','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684bd8348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684c2f348b11f191754c496c37265b','R_PRESIDENT','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684c8a348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684ce0348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684d3a348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684d91348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684dea348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_REJECT','WF','2026-04-10 11:17:49'),('da684e5b348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684eb8348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684f0f348b11f191754c496c37265b','R_RM','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684f64348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da684fb9348b11f191754c496c37265b','R_PRESIDENT','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da68500e348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da685063348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6850bf348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da685117348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da68516b348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_TODO','WF','2026-04-10 11:17:49'),('da6851d9348b11f191754c496c37265b','R_SUPPORT_STAFF','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685235348b11f191754c496c37265b','R_SUPPORT_SEC','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da68528c348b11f191754c496c37265b','R_RM','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6852e1348b11f191754c496c37265b','R_RETAIL_DEPT','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685339348b11f191754c496c37265b','R_PRESIDENT','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685391348b11f191754c496c37265b','R_CREDIT_REVIEWER','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6853e6348b11f191754c496c37265b','R_CREDIT_APPROVER','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da68543a348b11f191754c496c37265b','R_CORP_DEPT','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da685490348b11f191754c496c37265b','R_BRANCH_MGR','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('da6854e7348b11f191754c496c37265b','R_BACK_FINANCE','W_TASK_TRANSFER','WF','2026-04-10 11:17:49'),('FER_WF_APPROVE_BM','R_BRANCH_MGR','RES_WF_APPROVE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_APPROVE_CA','R_CREDIT_APPROVER','RES_WF_APPROVE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_APPROVE_CD','R_CORP_DEPT','RES_WF_APPROVE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_APPROVE_CR','R_CREDIT_REVIEWER','RES_WF_APPROVE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_APPROVE_RM','R_RM','RES_WF_APPROVE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_CLAIM_BM','R_BRANCH_MGR','RES_WF_CLAIM','PLATFORM','2026-04-25 16:01:33'),('FER_WF_CLAIM_CA','R_CREDIT_APPROVER','RES_WF_CLAIM','PLATFORM','2026-04-25 16:01:33'),('FER_WF_CLAIM_CD','R_CORP_DEPT','RES_WF_CLAIM','PLATFORM','2026-04-25 16:01:33'),('FER_WF_CLAIM_CR','R_CREDIT_REVIEWER','RES_WF_CLAIM','PLATFORM','2026-04-25 16:01:33'),('FER_WF_CLAIM_RM','R_RM','RES_WF_CLAIM','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DETAIL_BM','R_BRANCH_MGR','RES_WF_DETAIL','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DETAIL_CA','R_CREDIT_APPROVER','RES_WF_DETAIL','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DETAIL_CD','R_CORP_DEPT','RES_WF_DETAIL','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DETAIL_CR','R_CREDIT_REVIEWER','RES_WF_DETAIL','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DETAIL_RM','R_RM','RES_WF_DETAIL','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DONE_BM','R_BRANCH_MGR','RES_WF_DONE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DONE_CA','R_CREDIT_APPROVER','RES_WF_DONE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DONE_CD','R_CORP_DEPT','RES_WF_DONE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DONE_CR','R_CREDIT_REVIEWER','RES_WF_DONE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_DONE_RM','R_RM','RES_WF_DONE','PLATFORM','2026-04-25 16:01:33'),('FER_WF_REJECT_BM','R_BRANCH_MGR','RES_WF_REJECT','PLATFORM','2026-04-25 16:01:33'),('FER_WF_REJECT_CA','R_CREDIT_APPROVER','RES_WF_REJECT','PLATFORM','2026-04-25 16:01:33'),('FER_WF_REJECT_CD','R_CORP_DEPT','RES_WF_REJECT','PLATFORM','2026-04-25 16:01:33'),('FER_WF_REJECT_CR','R_CREDIT_REVIEWER','RES_WF_REJECT','PLATFORM','2026-04-25 16:01:33'),('FER_WF_REJECT_RM','R_RM','RES_WF_REJECT','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TODO_BM','R_BRANCH_MGR','RES_WF_TODO','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TODO_CA','R_CREDIT_APPROVER','RES_WF_TODO','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TODO_CD','R_CORP_DEPT','RES_WF_TODO','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TODO_CR','R_CREDIT_REVIEWER','RES_WF_TODO','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TODO_RM','R_RM','RES_WF_TODO','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TRANSFER_BM','R_BRANCH_MGR','RES_WF_TRANSFER','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TRANSFER_CA','R_CREDIT_APPROVER','RES_WF_TRANSFER','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TRANSFER_CD','R_CORP_DEPT','RES_WF_TRANSFER','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TRANSFER_CR','R_CREDIT_REVIEWER','RES_WF_TRANSFER','PLATFORM','2026-04-25 16:01:33'),('FER_WF_TRANSFER_RM','R_RM','RES_WF_TRANSFER','PLATFORM','2026-04-25 16:01:33'),('RR_RM_WF_CANCEL','R_RM','RES_WF_CANCEL','PLATFORM','2026-04-25 16:01:33'),('RR_RM_WF_SUBMIT','R_RM','RES_WF_SUBMIT','PLATFORM','2026-04-25 16:01:33'),('R_ADMIN_P_PERF_ALLOC_CUR','R_ADMIN','P_PERF_ALLOC_CUR','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_ALLOC_HIS','R_ADMIN','P_PERF_ALLOC_HIS','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_ALLOC_SUM','R_ADMIN','P_PERF_ALLOC_SUM','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_ADD','R_ADMIN','P_PERF_KPI_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_DEL','R_ADMIN','P_PERF_KPI_DEL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_GET','R_ADMIN','P_PERF_KPI_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_IADD','R_ADMIN','P_PERF_KPI_IADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_IDEL','R_ADMIN','P_PERF_KPI_IDEL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_IUPD','R_ADMIN','P_PERF_KPI_IUPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_LIST','R_ADMIN','P_PERF_KPI_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_PUB','R_ADMIN','P_PERF_KPI_PUB','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_KPI_UPD','R_ADMIN','P_PERF_KPI_UPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_ADD','R_ADMIN','P_PERF_METRIC_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_DEL','R_ADMIN','P_PERF_METRIC_DEL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_GET','R_ADMIN','P_PERF_METRIC_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_LIST','R_ADMIN','P_PERF_METRIC_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_RBY','R_ADMIN','P_PERF_METRIC_RBY','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_REFS','R_ADMIN','P_PERF_METRIC_REFS','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_SLOT','R_ADMIN','P_PERF_METRIC_SLOT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_SREL','R_ADMIN','P_PERF_METRIC_SREL','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_STAT','R_ADMIN','P_PERF_METRIC_STAT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_METRIC_UPD','R_ADMIN','P_PERF_METRIC_UPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_RT_GET','R_ADMIN','P_PERF_RT_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_RT_LIST','R_ADMIN','P_PERF_RT_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_GET','R_ADMIN','P_PERF_SC_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_HIS','R_ADMIN','P_PERF_SC_HIS','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_INIT','R_ADMIN','P_PERF_SC_INIT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_SC_SW','R_ADMIN','P_PERF_SC_SW','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_ADD','R_ADMIN','P_PERF_TGT_P_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_GET','R_ADMIN','P_PERF_TGT_P_GET','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_LIST','R_ADMIN','P_PERF_TGT_P_LIST','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_P_UPD','R_ADMIN','P_PERF_TGT_P_UPD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_V_ADD','R_ADMIN','P_PERF_TGT_V_ADD','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_V_BAT','R_ADMIN','P_PERF_TGT_V_BAT','PERF','2026-04-16 15:05:56'),('R_ADMIN_P_PERF_TGT_V_LIST','R_ADMIN','P_PERF_TGT_V_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_ALLOC_CUR','R_BACK_TECH','P_PERF_ALLOC_CUR','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_ALLOC_HIS','R_BACK_TECH','P_PERF_ALLOC_HIS','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_ALLOC_SUM','R_BACK_TECH','P_PERF_ALLOC_SUM','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_ADD','R_BACK_TECH','P_PERF_KPI_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_DEL','R_BACK_TECH','P_PERF_KPI_DEL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_GET','R_BACK_TECH','P_PERF_KPI_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_IADD','R_BACK_TECH','P_PERF_KPI_IADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_IDEL','R_BACK_TECH','P_PERF_KPI_IDEL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_IUPD','R_BACK_TECH','P_PERF_KPI_IUPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_LIST','R_BACK_TECH','P_PERF_KPI_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_PUB','R_BACK_TECH','P_PERF_KPI_PUB','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_KPI_UPD','R_BACK_TECH','P_PERF_KPI_UPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_ADD','R_BACK_TECH','P_PERF_METRIC_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_DEL','R_BACK_TECH','P_PERF_METRIC_DEL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_GET','R_BACK_TECH','P_PERF_METRIC_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_LIST','R_BACK_TECH','P_PERF_METRIC_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_RBY','R_BACK_TECH','P_PERF_METRIC_RBY','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_REFS','R_BACK_TECH','P_PERF_METRIC_REFS','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_SLOT','R_BACK_TECH','P_PERF_METRIC_SLOT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_SREL','R_BACK_TECH','P_PERF_METRIC_SREL','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_STAT','R_BACK_TECH','P_PERF_METRIC_STAT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_METRIC_UPD','R_BACK_TECH','P_PERF_METRIC_UPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_RT_GET','R_BACK_TECH','P_PERF_RT_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_RT_LIST','R_BACK_TECH','P_PERF_RT_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_GET','R_BACK_TECH','P_PERF_SC_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_HIS','R_BACK_TECH','P_PERF_SC_HIS','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_INIT','R_BACK_TECH','P_PERF_SC_INIT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_SC_SW','R_BACK_TECH','P_PERF_SC_SW','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_ADD','R_BACK_TECH','P_PERF_TGT_P_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_GET','R_BACK_TECH','P_PERF_TGT_P_GET','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_LIST','R_BACK_TECH','P_PERF_TGT_P_LIST','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_P_UPD','R_BACK_TECH','P_PERF_TGT_P_UPD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_V_ADD','R_BACK_TECH','P_PERF_TGT_V_ADD','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_V_BAT','R_BACK_TECH','P_PERF_TGT_V_BAT','PERF','2026-04-16 15:05:56'),('R_BACK_TECH_P_PERF_TGT_V_LIST','R_BACK_TECH','P_PERF_TGT_V_LIST','PERF','2026-04-16 15:05:56');
/*!40000 ALTER TABLE `PT_ROLE_RESOURCE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PT_USER`
--

DROP TABLE IF EXISTS `PT_USER`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_USER` (
  `USER_ID` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID（工号）',
  `USERNAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户姓名',
  `USERCHNNAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户中文姓名',
  `PWD` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码（加密）',
  `EMAIL` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `ISEXPIRED` int DEFAULT '0' COMMENT '1 过期 0 未过期',
  `ISLOCKED` int DEFAULT '0' COMMENT '1 被锁 0 未被锁',
  `PASS_WRONG_COUNT` int DEFAULT '0' COMMENT '密码错误次数',
  `ISENABLED` int DEFAULT '1' COMMENT '0 启用 1 未启用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_AUTHOR` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建者',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_AUTHOR` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新者',
  `REMARK` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `PWD_UPDATE_TIME` datetime DEFAULT NULL COMMENT '密码更新时间',
  PRIMARY KEY (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='人员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PT_USER`
--

LOCK TABLES `PT_USER` WRITE;
/*!40000 ALTER TABLE `PT_USER` DISABLE KEYS */;
INSERT INTO `PT_USER` VALUES ('admin','admin','系统管理员','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','admin@test.com',0,0,0,0,'2026-04-07 15:49:20',NULL,'2026-04-10 11:25:23',NULL,NULL,NULL),('E10001','rm_zhang','张客户经理','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','rm_zhang@test.com',0,0,0,0,'2026-04-25 16:01:33','flowable-real-env','2026-04-25 16:01:33','flowable-real-env','flowable real env test user',NULL),('E10002','rm_li','李四(客户经理)','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E20001','branch_wang','王分行负责人','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','branch_wang@test.com',0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env test user',NULL),('E30001','corp_zhao','赵公司部审核','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','corp_zhao@test.com',0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env test user',NULL),('E30002','retail_sun','孙七(零售部)','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E40001','finance_zhou','周八(资财)','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E40002','tech_wu','吴九(科技)','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E50001','sec_zheng','郑十(中场秘书)','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E50002','staff_qian','钱十一(中场人员)','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu',NULL,0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-10 11:25:23',NULL,'test-user',NULL),('E60001','reviewer_chen','陈授信审查','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','reviewer_chen@test.com',0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env test user',NULL),('E60002','approver_he','何授信批复','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','approver_he@test.com',0,0,0,0,'2026-04-10 11:17:49','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env test user',NULL),('E90001','no_workflow_user','无工作流权限用户','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','no_workflow@test.com',0,0,0,0,'2026-04-25 16:01:33','flowable-real-env','2026-04-25 16:01:33','flowable-real-env','flowable real env test user',NULL),('user001','user001','张三','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','zhangsan@test.com',0,0,0,0,'2026-04-07 15:49:20',NULL,'2026-04-10 11:25:23',NULL,NULL,NULL),('user002','user002','李四','$2a$10$h9vw/3qZn51yz1SHPDsN7.09DjyRtpdoUnPEB04LTJ16GgmnbtDKu','lisi@test.com',0,0,0,0,'2026-04-07 15:49:20',NULL,'2026-04-10 11:25:23',NULL,NULL,NULL);
/*!40000 ALTER TABLE `PT_USER` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `PT_USER_ROLE`
--

DROP TABLE IF EXISTS `PT_USER_ROLE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_USER_ROLE` (
  `USER_ID` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `ROLE_ID` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `DEFAULT_ASSIGN` int DEFAULT '0' COMMENT '默认分配',
  `INHERIT_ASSIGN` int DEFAULT '0' COMMENT '用户组角色继承',
  `GROUP_ASSING` int DEFAULT '0' COMMENT '角色组分配',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ROLE_ID`),
  KEY `idx_role_id` (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `PT_USER_ROLE`
--

LOCK TABLES `PT_USER_ROLE` WRITE;
/*!40000 ALTER TABLE `PT_USER_ROLE` DISABLE KEYS */;
INSERT INTO `PT_USER_ROLE` VALUES ('admin','R_ADMIN',1,0,0,'2026-04-07 19:20:37'),('E10001','R_RM',1,0,0,'2026-04-25 16:01:33'),('E10002','R_RM',0,0,0,'2026-04-10 11:17:49'),('E20001','R_BRANCH_MGR',1,0,0,'2026-04-10 11:17:49'),('E30001','R_CORP_DEPT',1,0,0,'2026-04-10 11:17:49'),('E30002','R_RETAIL_DEPT',0,0,0,'2026-04-10 11:17:49'),('E40001','R_BACK_FINANCE',0,0,0,'2026-04-10 11:17:49'),('E40002','R_BACK_TECH',0,0,0,'2026-04-10 11:17:49'),('E50001','R_SUPPORT_SEC',0,0,0,'2026-04-10 11:17:49'),('E50002','R_SUPPORT_STAFF',0,0,0,'2026-04-10 11:17:49'),('E60001','R_CREDIT_REVIEWER',1,0,0,'2026-04-10 11:17:49'),('E60002','R_CREDIT_APPROVER',1,0,0,'2026-04-10 11:17:49'),('user001','R_RM',1,0,0,'2026-04-07 19:20:37'),('user002','R_PRESIDENT',1,0,0,'2026-04-07 19:20:37');
/*!40000 ALTER TABLE `PT_USER_ROLE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_BLOB_TRIGGERS`
--

DROP TABLE IF EXISTS `QRTZ_BLOB_TRIGGERS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_BLOB_TRIGGERS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `BLOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz Blob 触发器（本项目暂不用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_BLOB_TRIGGERS`
--

LOCK TABLES `QRTZ_BLOB_TRIGGERS` WRITE;
/*!40000 ALTER TABLE `QRTZ_BLOB_TRIGGERS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_BLOB_TRIGGERS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_CALENDARS`
--

DROP TABLE IF EXISTS `QRTZ_CALENDARS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_CALENDARS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `CALENDAR_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `CALENDAR` blob NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`CALENDAR_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 业务日历';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_CALENDARS`
--

LOCK TABLES `QRTZ_CALENDARS` WRITE;
/*!40000 ALTER TABLE `QRTZ_CALENDARS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_CALENDARS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_CRON_TRIGGERS`
--

DROP TABLE IF EXISTS `QRTZ_CRON_TRIGGERS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_CRON_TRIGGERS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `CRON_EXPRESSION` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TIME_ZONE_ID` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz Cron 触发器扩展';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_CRON_TRIGGERS`
--

LOCK TABLES `QRTZ_CRON_TRIGGERS` WRITE;
/*!40000 ALTER TABLE `QRTZ_CRON_TRIGGERS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_CRON_TRIGGERS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_FIRED_TRIGGERS`
--

DROP TABLE IF EXISTS `QRTZ_FIRED_TRIGGERS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_FIRED_TRIGGERS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `ENTRY_ID` varchar(95) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `INSTANCE_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `FIRED_TIME` bigint NOT NULL,
  `SCHED_TIME` bigint NOT NULL,
  `PRIORITY` int NOT NULL,
  `STATE` varchar(16) COLLATE utf8mb4_general_ci NOT NULL,
  `JOB_NAME` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `JOB_GROUP` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `IS_NONCONCURRENT` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `REQUESTS_RECOVERY` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`ENTRY_ID`),
  KEY `IDX_QRTZ_FT_TRIG_INST_NAME` (`SCHED_NAME`,`INSTANCE_NAME`),
  KEY `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY` (`SCHED_NAME`,`INSTANCE_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_FT_J_G` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_T_G` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_TG` (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 已触发触发器（执行中状态）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_FIRED_TRIGGERS`
--

LOCK TABLES `QRTZ_FIRED_TRIGGERS` WRITE;
/*!40000 ALTER TABLE `QRTZ_FIRED_TRIGGERS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_FIRED_TRIGGERS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_JOB_DETAILS`
--

DROP TABLE IF EXISTS `QRTZ_JOB_DETAILS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_JOB_DETAILS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `JOB_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `JOB_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `DESCRIPTION` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `JOB_CLASS_NAME` varchar(250) COLLATE utf8mb4_general_ci NOT NULL,
  `IS_DURABLE` varchar(1) COLLATE utf8mb4_general_ci NOT NULL,
  `IS_NONCONCURRENT` varchar(1) COLLATE utf8mb4_general_ci NOT NULL,
  `IS_UPDATE_DATA` varchar(1) COLLATE utf8mb4_general_ci NOT NULL,
  `REQUESTS_RECOVERY` varchar(1) COLLATE utf8mb4_general_ci NOT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_J_REQ_RECOVERY` (`SCHED_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_J_GRP` (`SCHED_NAME`,`JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz Job 元信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_JOB_DETAILS`
--

LOCK TABLES `QRTZ_JOB_DETAILS` WRITE;
/*!40000 ALTER TABLE `QRTZ_JOB_DETAILS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_JOB_DETAILS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_LOCKS`
--

DROP TABLE IF EXISTS `QRTZ_LOCKS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_LOCKS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `LOCK_NAME` varchar(40) COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`LOCK_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 集群锁（TRIGGER_ACCESS / STATE_ACCESS）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_LOCKS`
--

LOCK TABLES `QRTZ_LOCKS` WRITE;
/*!40000 ALTER TABLE `QRTZ_LOCKS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_LOCKS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_PAUSED_TRIGGER_GRPS`
--

DROP TABLE IF EXISTS `QRTZ_PAUSED_TRIGGER_GRPS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_PAUSED_TRIGGER_GRPS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 暂停触发器组';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_PAUSED_TRIGGER_GRPS`
--

LOCK TABLES `QRTZ_PAUSED_TRIGGER_GRPS` WRITE;
/*!40000 ALTER TABLE `QRTZ_PAUSED_TRIGGER_GRPS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_PAUSED_TRIGGER_GRPS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_SCHEDULER_STATE`
--

DROP TABLE IF EXISTS `QRTZ_SCHEDULER_STATE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_SCHEDULER_STATE` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `INSTANCE_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `LAST_CHECKIN_TIME` bigint NOT NULL,
  `CHECKIN_INTERVAL` bigint NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 调度器实例心跳';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_SCHEDULER_STATE`
--

LOCK TABLES `QRTZ_SCHEDULER_STATE` WRITE;
/*!40000 ALTER TABLE `QRTZ_SCHEDULER_STATE` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_SCHEDULER_STATE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_SIMPLE_TRIGGERS`
--

DROP TABLE IF EXISTS `QRTZ_SIMPLE_TRIGGERS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_SIMPLE_TRIGGERS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `REPEAT_COUNT` bigint NOT NULL,
  `REPEAT_INTERVAL` bigint NOT NULL,
  `TIMES_TRIGGERED` bigint NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 简单触发器（本项目暂不用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_SIMPLE_TRIGGERS`
--

LOCK TABLES `QRTZ_SIMPLE_TRIGGERS` WRITE;
/*!40000 ALTER TABLE `QRTZ_SIMPLE_TRIGGERS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_SIMPLE_TRIGGERS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_SIMPROP_TRIGGERS`
--

DROP TABLE IF EXISTS `QRTZ_SIMPROP_TRIGGERS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_SIMPROP_TRIGGERS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `STR_PROP_1` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STR_PROP_2` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `STR_PROP_3` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `INT_PROP_1` int DEFAULT NULL,
  `INT_PROP_2` int DEFAULT NULL,
  `LONG_PROP_1` bigint DEFAULT NULL,
  `LONG_PROP_2` bigint DEFAULT NULL,
  `DEC_PROP_1` decimal(13,4) DEFAULT NULL,
  `DEC_PROP_2` decimal(13,4) DEFAULT NULL,
  `BOOL_PROP_1` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `BOOL_PROP_2` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz 自定义属性触发器（本项目暂不用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_SIMPROP_TRIGGERS`
--

LOCK TABLES `QRTZ_SIMPROP_TRIGGERS` WRITE;
/*!40000 ALTER TABLE `QRTZ_SIMPROP_TRIGGERS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_SIMPROP_TRIGGERS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `QRTZ_TRIGGERS`
--

DROP TABLE IF EXISTS `QRTZ_TRIGGERS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `QRTZ_TRIGGERS` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `JOB_NAME` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `JOB_GROUP` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `DESCRIPTION` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `NEXT_FIRE_TIME` bigint DEFAULT NULL,
  `PREV_FIRE_TIME` bigint DEFAULT NULL,
  `PRIORITY` int DEFAULT NULL,
  `TRIGGER_STATE` varchar(16) COLLATE utf8mb4_general_ci NOT NULL,
  `TRIGGER_TYPE` varchar(8) COLLATE utf8mb4_general_ci NOT NULL,
  `START_TIME` bigint NOT NULL,
  `END_TIME` bigint DEFAULT NULL,
  `CALENDAR_NAME` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
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
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_GROUP`,`TRIGGER_STATE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quartz Trigger 主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `QRTZ_TRIGGERS`
--

LOCK TABLES `QRTZ_TRIGGERS` WRITE;
/*!40000 ALTER TABLE `QRTZ_TRIGGERS` DISABLE KEYS */;
/*!40000 ALTER TABLE `QRTZ_TRIGGERS` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `RPT_EXPORT_TASK`
--

DROP TABLE IF EXISTS `RPT_EXPORT_TASK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `RPT_EXPORT_TASK` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出任务ID',
  `export_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：DYNAMIC_QUERY/FIXED_REPORT/SQL_PROBE 等',
  `params_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '导出参数 JSON',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `file_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'governance.file_object 主键 (FileObjectDTO.id)',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `row_count` int DEFAULT NULL COMMENT '导出行数',
  `expire_at` datetime DEFAULT NULL COMMENT '文件过期时间',
  `operator_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作人员工号',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '失败原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_export_type` (`export_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表异步导出任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `RPT_EXPORT_TASK`
--

LOCK TABLES `RPT_EXPORT_TASK` WRITE;
/*!40000 ALTER TABLE `RPT_EXPORT_TASK` DISABLE KEYS */;
/*!40000 ALTER TABLE `RPT_EXPORT_TASK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `RPT_SAVED_QUERY`
--

DROP TABLE IF EXISTS `RPT_SAVED_QUERY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `RPT_SAVED_QUERY` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案ID（UUID）',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工工号',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指标编码列表(JSON数组)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`,`created_time`),
  KEY `idx_emp_id_name` (`emp_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `RPT_SAVED_QUERY`
--

LOCK TABLES `RPT_SAVED_QUERY` WRITE;
/*!40000 ALTER TABLE `RPT_SAVED_QUERY` DISABLE KEYS */;
/*!40000 ALTER TABLE `RPT_SAVED_QUERY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `RPT_SNAPSHOT_TASK`
--

DROP TABLE IF EXISTS `RPT_SNAPSHOT_TASK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `RPT_SNAPSHOT_TASK` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `task_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `snapshot_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '快照类型（DAILY/MONTHLY，V2扩展）',
  `cron_expr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron表达式',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
  `last_run_time` datetime DEFAULT NULL COMMENT '最近执行时间',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次执行时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_next_run_time` (`next_run_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='快照任务配置（V1预留）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `RPT_SNAPSHOT_TASK`
--

LOCK TABLES `RPT_SNAPSHOT_TASK` WRITE;
/*!40000 ALTER TABLE `RPT_SNAPSHOT_TASK` DISABLE KEYS */;
/*!40000 ALTER TABLE `RPT_SNAPSHOT_TASK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SQL_PROBE_HISTORY`
--

DROP TABLE IF EXISTS `SQL_PROBE_HISTORY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SQL_PROBE_HISTORY` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '历史ID（UUID）',
  `emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行人工号',
  `sql_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SQL语句',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注(reason)',
  `row_count` int DEFAULT NULL COMMENT '影响行数',
  `execution_time_ms` int DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED/TIMEOUT',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_emp_time` (`emp_id`,`created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SQL探查历史';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SQL_PROBE_HISTORY`
--

LOCK TABLES `SQL_PROBE_HISTORY` WRITE;
/*!40000 ALTER TABLE `SQL_PROBE_HISTORY` DISABLE KEYS */;
/*!40000 ALTER TABLE `SQL_PROBE_HISTORY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SUPPORT_REQUEST`
--

DROP TABLE IF EXISTS `SUPPORT_REQUEST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SUPPORT_REQUEST` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请ID(UUID)',
  `request_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请编号(SR+yyyyMMdd+6位序号)',
  `submit_group_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '同批提交分组ID(多产品拆单时同组共享)',
  `cust_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户ID,逻辑外键→cust_master.id',
  `source_touch_task_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源触达任务ID,逻辑外键→touch_task.id',
  `product_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产品ID,逻辑外键→product_info.id',
  `support_dept_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '承接部门ORG_CODE,逻辑外键→EXT_ORG_INFO.org_code',
  `other_demand` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '其他需求/补充说明',
  `dispatch_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '派单人工号(部门秘书,仅场景B)',
  `dispatch_time` datetime DEFAULT NULL COMMENT '派单时间',
  `assigned_emp_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '承接办理人工号(场景A=产品负责人,场景B=秘书派单)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED',
  `business_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程业务键,固定格式SUPPORT:{id}',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '归属机构(发起侧ORG_CODE)',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人工号(发起人)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人工号',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_support_dept` (`support_dept_id`),
  KEY `idx_assigned_emp` (`assigned_emp_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_submit_group` (`submit_group_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='中场支持申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SUPPORT_REQUEST`
--

LOCK TABLES `SUPPORT_REQUEST` WRITE;
/*!40000 ALTER TABLE `SUPPORT_REQUEST` DISABLE KEYS */;
/*!40000 ALTER TABLE `SUPPORT_REQUEST` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_CALENDAR_DAY`
--

DROP TABLE IF EXISTS `SYS_CALENDAR_DAY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_CALENDAR_DAY` (
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
-- Dumping data for table `SYS_CALENDAR_DAY`
--

LOCK TABLES `SYS_CALENDAR_DAY` WRITE;
/*!40000 ALTER TABLE `SYS_CALENDAR_DAY` DISABLE KEYS */;
INSERT INTO `SYS_CALENDAR_DAY` VALUES ('2026-01-01',0,'元旦','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-01-02',0,'元旦假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-01-03',0,'元旦假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-05-01',0,'劳动节','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-05-02',0,'劳动节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-05-03',0,'劳动节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-01',0,'国庆节','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-02',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-03',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-04',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-05',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-06',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('2026-10-07',0,'国庆节假期','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52');
/*!40000 ALTER TABLE `SYS_CALENDAR_DAY` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_CONFIG_KV`
--

DROP TABLE IF EXISTS `SYS_CONFIG_KV`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_CONFIG_KV` (
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
-- Dumping data for table `SYS_CONFIG_KV`
--

LOCK TABLES `SYS_CONFIG_KV` WRITE;
/*!40000 ALTER TABLE `SYS_CONFIG_KV` DISABLE KEYS */;
INSERT INTO `SYS_CONFIG_KV` VALUES ('CFG_AUDIT_EXPORT_MAX_DAYS','AUDIT_EXPORT_MAX_DAYS','31','NUMBER','ACTIVE','审计导出最大天数','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_AUDIT_EXPORT_MAX_ROWS','AUDIT_EXPORT_MAX_ROWS','200000','NUMBER','ACTIVE','审计导出最大行数','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_SQL_PROBE_MAX_CONCURRENCY','SQL_PROBE_MAX_CONCURRENCY','5','NUMBER','ACTIVE','SQL探查并发上限','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_SQL_PROBE_MAX_LIMIT','SQL_PROBE_MAX_LIMIT','2000','NUMBER','ACTIVE','SQL探查LIMIT上限','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52'),('CFG_SQL_PROBE_WHITELIST_JSON','SQL_PROBE_WHITELIST_JSON','{\"schemas\":[],\"tables\":[]}','JSON','ACTIVE','SQL探查白名单','seed','2026-04-28 00:30:52',NULL,'2026-04-28 00:30:52');
/*!40000 ALTER TABLE `SYS_CONFIG_KV` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_CONTROL`
--

DROP TABLE IF EXISTS `SYS_CONTROL`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_CONTROL` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '控制ID',
  `scope_dim` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `latest_data_date` date NOT NULL COMMENT '最新数据日期',
  `current_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前有效版本',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '切版备注',
  `is_valid` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最后更新人',
  `publish_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布来源：MANUAL/AUTO/ROLLBACK',
  `publish_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布人',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_dim_date_version` (`scope_dim`,`latest_data_date`,`current_version`),
  KEY `idx_scope_valid` (`scope_dim`,`is_valid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据版本控制表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SYS_CONTROL`
--

LOCK TABLES `SYS_CONTROL` WRITE;
/*!40000 ALTER TABLE `SYS_CONTROL` DISABLE KEYS */;
INSERT INTO `SYS_CONTROL` VALUES ('SC_INIT_CUST','CUST','1970-01-01',NULL,NULL,0,'2026-04-28 00:30:52','2026-04-28 00:30:52',NULL,NULL,NULL,NULL),('SC_INIT_EMP','EMP','1970-01-01',NULL,NULL,0,'2026-04-28 00:30:52','2026-04-28 00:30:52',NULL,NULL,NULL,NULL),('SC_INIT_ORG','ORG','1970-01-01',NULL,NULL,0,'2026-04-28 00:30:52','2026-04-28 00:30:52',NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `SYS_CONTROL` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_DICT`
--

DROP TABLE IF EXISTS `SYS_DICT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_DICT` (
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
-- Dumping data for table `SYS_DICT`
--

LOCK TABLES `SYS_DICT` WRITE;
/*!40000 ALTER TABLE `SYS_DICT` DISABLE KEYS */;
INSERT INTO `SYS_DICT` VALUES ('D_BK_CD','BIZ_KIND','NCD','大额存单','NCD',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BK_DEP','BIZ_KIND','DEPOSIT','存款','DEPOSIT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BK_LOAN','BIZ_KIND','LOAN','贷款','LOAN',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BK_MID','BIZ_KIND','INTERMEDIATE','中间业务','INTERMEDIATE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_BT_ACCEPTANCE','BIZ_TYPE','ACCEPTANCE','承兑汇票','ACCEPTANCE',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_FIXED','BIZ_TYPE','FIXED_ASSET','固定资产贷款','FIXED_ASSET',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_GUARANTEE','BIZ_TYPE','GUARANTEE','保函','GUARANTEE',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_TRADE','BIZ_TYPE','TRADE_FINANCE','贸易融资','TRADE_FINANCE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_BT_WORKING_CAP','BIZ_TYPE','WORKING_CAPITAL','流动资金贷款','WORKING_CAPITAL',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CF_DAY','PERF_CALC_FREQ','DAY','日','DAY',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CF_MONTH','PERF_CALC_FREQ','MONTH','月','MONTH',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CF_QUARTER','PERF_CALC_FREQ','QUARTER','季','QUARTER',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CF_YEAR','PERF_CALC_FREQ','YEAR','年','YEAR',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_EXPR','PERF_CALC_LOGIC_TYPE','EXPR','表达式','EXPR',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_PROC','PERF_CALC_LOGIC_TYPE','PROC','存储过程','PROC',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_SQL','PERF_CALC_LOGIC_TYPE','SQL','SQL查询','SQL',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CLT_SUMMARY','PERF_CALC_LOGIC_TYPE','SUMMARY','汇总','SUMMARY',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CM_AUTO','PERF_CALC_MODE','AUTO','自动计算','AUTO',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CM_MANUAL','PERF_CALC_MODE','MANUAL','手工导入','MANUAL',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CT_1','CUSTOMER_TYPE','CORP','对公客户','CORP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CT_2','CUSTOMER_TYPE','RETAIL','零售客户','RETAIL',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_CVT_BOOL','CONFIG_VALUE_TYPE','BOOL','布尔','BOOL',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CVT_JSON','CONFIG_VALUE_TYPE','JSON','JSON','JSON',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CVT_NUM','CONFIG_VALUE_TYPE','NUMBER','数值','NUMBER',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_CVT_STR','CONFIG_VALUE_TYPE','STRING','字符串','STRING',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_GUIDE','DOC_CATEGORY','GUIDE','操作指引','GUIDE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_POLICY','DOC_CATEGORY','POLICY','制度文件','POLICY',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_TEMPLATE','DOC_CATEGORY','TEMPLATE','模板表单','TEMPLATE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_DC_TRAIN','DOC_CATEGORY','TRAINING','培训材料','TRAINING',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_ET_1','ENTERPRISE_TYPE','SOE','国企','SOE',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_2','ENTERPRISE_TYPE','PRIVATE','民营','PRIVATE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_3','ENTERPRISE_TYPE','FOREIGN','外资','FOREIGN',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_4','ENTERPRISE_TYPE','JV','合资','JV',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ET_5','ENTERPRISE_TYPE','COLLECT','集体企业','COLLECT',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_GRP_1','GROUP_TYPE','GROUP','集团客户','GROUP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_GRP_2','GROUP_TYPE','SINGLE','非集团客户','SINGLE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_GT_CREDIT','GUARANTEE_TYPE','CREDIT','信用','CREDIT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_GUARANTEE','GUARANTEE_TYPE','GUARANTEE','保证','GUARANTEE',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_MIXED','GUARANTEE_TYPE','MIXED','组合担保','MIXED',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_MORTGAGE','GUARANTEE_TYPE','MORTGAGE','抵押','MORTGAGE',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_GT_PLEDGE','GUARANTEE_TYPE','PLEDGE','质押','PLEDGE',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_IND_AGRI','INDUSTRY','AGRI','农林牧渔业','AGRI',10,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_EDU','INDUSTRY','EDU','教育','EDU',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_ENERGY','INDUSTRY','ENERGY','能源','ENERGY',9,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_FIN','INDUSTRY','FIN','金融业','FIN',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_IT','INDUSTRY','IT','信息技术','IT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_MED','INDUSTRY','MED','医疗卫生','MED',6,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_MFG','INDUSTRY','MFG','制造业','MFG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_OTHER','INDUSTRY','OTHER','其他','OTHER',99,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_RE','INDUSTRY','RE','房地产业','RE',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_RETAIL','INDUSTRY','RETAIL','批发和零售业','RETAIL',7,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_IND_TRANS','INDUSTRY','TRANS','交通运输业','TRANS',8,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_JRS_FAIL','JOB_RUN_STATUS','FAILED','失败','FAILED',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JRS_OK','JOB_RUN_STATUS','SUCCESS','成功','SUCCESS',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JRS_RUN','JOB_RUN_STATUS','RUNNING','运行中','RUNNING',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JS_ACT','JOB_STATUS','ACTIVE','活跃','ACTIVE',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_JS_PAU','JOB_STATUS','PAUSED','暂停','PAUSED',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_KC_M','PERF_KPI_CYCLE','MONTHLY','月度','MONTHLY',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_KC_Q','PERF_KPI_CYCLE','QUARTERLY','季度','QUARTERLY',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_LS_ASSIGN','LEAD_SOURCE','ASSIGNED','上级分配','ASSIGNED',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_LS_IMPORT','LEAD_SOURCE','IMPORTED','批量导入','IMPORTED',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_LS_REFER','LEAD_SOURCE','REFERRAL','转介绍','REFERRAL',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_LS_SELF','LEAD_SOURCE','SELF_FOUND','自行挖掘','SELF_FOUND',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_MD_CUST','PERF_BASE_DIM','CUST','客户','CUST',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_MD_EMP','PERF_BASE_DIM','EMP','人员','EMP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_MD_ORG','PERF_BASE_DIM','ORG','机构','ORG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ML_1','PERF_METRIC_LEVEL','1','一级基础','1',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ML_2','PERF_METRIC_LEVEL','2','二级派生','2',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_ML_3','PERF_METRIC_LEVEL','3','三级复合','3',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_NT_BIZ','NOTIFY_TYPE','BUSINESS','业务通知','BUSINESS',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_NT_SYS','NOTIFY_TYPE','SYSTEM','系统通知','SYSTEM',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_NT_WF','NOTIFY_TYPE','WORKFLOW','流程通知','WORKFLOW',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PAD_ACCOUNT','PERF_ALLOC_DIM','ACCOUNT','按台账分配','ACCOUNT',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PAD_RULE','PERF_ALLOC_DIM','RULE','按规则分配','RULE',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PC_CORP','PRODUCT_CATEGORY','CORP_BANK','公司银行','CORP_BANK',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PC_FM','PRODUCT_CATEGORY','FIN_MARKET','金融市场','FIN_MARKET',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PC_RTL','PRODUCT_CATEGORY','RETAIL_BANK','零售银行','RETAIL_BANK',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PC_TRADE','PRODUCT_CATEGORY','TRADE_BANK','交易银行','TRADE_BANK',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PIT_IDX','PERF_IMPORT_TYPE','INDEX_RESULT','指标结果','INDEX_RESULT',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PIT_KPI','PERF_IMPORT_TYPE','KPI_RESULT','KPI结果','KPI_RESULT',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PIT_TGT','PERF_IMPORT_TYPE','TARGET','目标','TARGET',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PJT_ADJUST','PROJECT_TYPE','ADJUST','调整项目','ADJUST',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PJT_NEW','PROJECT_TYPE','NEW','新增项目','NEW',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PJT_RENEW','PROJECT_TYPE','RENEWAL','续贷项目','RENEWAL',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_BH','POSITION','BRANCH_HEAD','支行负责人','BRANCH_HEAD',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CA','POSITION','CREDIT_APPROVE','授信批复岗','CREDIT_APPROVE',9,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CM','POSITION','CUST_MGR','客户经理','CUST_MGR',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CORP','POSITION','CORP_STAFF','公司部员工','CORP_STAFF',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_CR','POSITION','CREDIT_REVIEW','授信审查岗','CREDIT_REVIEW',8,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_FIN','POSITION','FINANCE_STAFF','资财部员工','FINANCE_STAFF',5,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_PRES','POSITION','PRESIDENT','行长','PRESIDENT',10,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_RTL','POSITION','RETAIL_STAFF','零售部员工','RETAIL_STAFF',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_SEC','POSITION','SECRETARY','部门秘书','SECRETARY',7,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_POS_TECH','POSITION','TECH_STAFF','科技部员工','TECH_STAFF',6,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PSR_AVG','PERF_SUMMARY_RULE','AVG','平均','AVG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PSR_SUM','PERF_SUMMARY_RULE','SUM','求和','SUM',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_PTT_KPI','PERF_TASK_TYPE','KPI_RUN','KPI执行','KPI_RUN',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PTT_RECALC','PERF_TASK_TYPE','RECALC','历史重算','RECALC',4,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PTT_RUN','PERF_TASK_TYPE','METRIC_RUN','指标执行','METRIC_RUN',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_PTT_TRIAL','PERF_TASK_TYPE','METRIC_TRIAL','指标试运行','METRIC_TRIAL',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_RL_HIGH','RISK_LEVEL','HIGH','高风险','HIGH',3,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_RL_LOW','RISK_LEVEL','LOW','低风险','LOW',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_RL_MID','RISK_LEVEL','MEDIUM','中风险','MEDIUM',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:56',NULL,'2026-04-28 00:29:56'),('D_TC_Q','PERF_TARGET_CYCLE','QUARTER','季度','QUARTER',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_TC_Y','PERF_TARGET_CYCLE','YEAR','年度','YEAR',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_TD_EMP','PERF_TARGET_DIM','EMP','人员','EMP',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_TD_ORG','PERF_TARGET_DIM','ORG','机构','ORG',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_YN_0','YES_NO','NO','否','0',2,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55'),('D_YN_1','YES_NO','YES','是','1',1,'ACTIVE','V1 seed','seed','2026-04-28 00:29:55',NULL,'2026-04-28 00:29:55');
/*!40000 ALTER TABLE `SYS_DICT` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_DICT_ITEM`
--

DROP TABLE IF EXISTS `SYS_DICT_ITEM`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_DICT_ITEM` (
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
-- Dumping data for table `SYS_DICT_ITEM`
--

LOCK TABLES `SYS_DICT_ITEM` WRITE;
/*!40000 ALTER TABLE `SYS_DICT_ITEM` DISABLE KEYS */;
/*!40000 ALTER TABLE `SYS_DICT_ITEM` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_JOB_CONF`
--

DROP TABLE IF EXISTS `SYS_JOB_CONF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_JOB_CONF` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `job_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务KEY(唯一)',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `cron_expr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron表达式',
  `quartz_job_class` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
  `misfire_policy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略（V1.6 新增）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/PAUSED',
  `allow_manual_trigger` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许手动触发',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次执行时间',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次执行时间(可选)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_key` (`job_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务调度配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SYS_JOB_CONF`
--

LOCK TABLES `SYS_JOB_CONF` WRITE;
/*!40000 ALTER TABLE `SYS_JOB_CONF` DISABLE KEYS */;
/*!40000 ALTER TABLE `SYS_JOB_CONF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SYS_JOB_RUN_LOG`
--

DROP TABLE IF EXISTS `SYS_JOB_RUN_LOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SYS_JOB_RUN_LOG` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行日志ID',
  `job_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
  `trigger_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型：SCHEDULED/MANUAL',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原因(手动触发必填)',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `scheduled_fire_time` datetime(3) DEFAULT NULL COMMENT 'Quartz 计划触发时间（V1.6 新增）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `error_msg` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `created_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '触发人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务执行日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SYS_JOB_RUN_LOG`
--

LOCK TABLES `SYS_JOB_RUN_LOG` WRITE;
/*!40000 ALTER TABLE `SYS_JOB_RUN_LOG` DISABLE KEYS */;
/*!40000 ALTER TABLE `SYS_JOB_RUN_LOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `TOUCH_LOG`
--

DROP TABLE IF EXISTS `TOUCH_LOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `TOUCH_LOG` (
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
-- Dumping data for table `TOUCH_LOG`
--

LOCK TABLES `TOUCH_LOG` WRITE;
/*!40000 ALTER TABLE `TOUCH_LOG` DISABLE KEYS */;
/*!40000 ALTER TABLE `TOUCH_LOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `TOUCH_TASK`
--

DROP TABLE IF EXISTS `TOUCH_TASK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `TOUCH_TASK` (
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
  `sla_warning` tinyint(1) DEFAULT '0' COMMENT 'SLA 预警标记 (slaStatus 为 YELLOW 或 RED 时为 1)',
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
-- Dumping data for table `TOUCH_TASK`
--

LOCK TABLES `TOUCH_TASK` WRITE;
/*!40000 ALTER TABLE `TOUCH_TASK` DISABLE KEYS */;
/*!40000 ALTER TABLE `TOUCH_TASK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `USER_NOTIFICATION`
--

DROP TABLE IF EXISTS `USER_NOTIFICATION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `USER_NOTIFICATION` (
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
-- Dumping data for table `USER_NOTIFICATION`
--

LOCK TABLES `USER_NOTIFICATION` WRITE;
/*!40000 ALTER TABLE `USER_NOTIFICATION` DISABLE KEYS */;
/*!40000 ALTER TABLE `USER_NOTIFICATION` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `WF_NODE_CANDIDATE_CONF`
--

DROP TABLE IF EXISTS `WF_NODE_CANDIDATE_CONF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WF_NODE_CANDIDATE_CONF` (
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
-- Dumping data for table `WF_NODE_CANDIDATE_CONF`
--

LOCK TABLES `WF_NODE_CANDIDATE_CONF` WRITE;
/*!40000 ALTER TABLE `WF_NODE_CANDIDATE_CONF` DISABLE KEYS */;
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ALLOC_BIZ','alloc_adjust_approve_v1','biz_dept_review','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_ALLOC_BIZ_LDR','alloc_adjust_approve_v1','biz_dept_leader_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_ALLOC_BM','alloc_adjust_approve_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_ALLOC_FIN','alloc_adjust_approve_v1','finance_review','ROLE','[\"BACK_FINANCE\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_ALLOC_FIN_LDR','alloc_adjust_approve_v1','finance_leader_approve','ROLE','[\"BACK_FINANCE\"]','2026-04-28 00:30:54','2026-04-28 00:32:50'),('WNC_ALLOC_ORIG','alloc_adjust_approve_v1','original_owner_approve','ROLE','[\"CUST_MANAGER\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LEAD_DEL_V1_HQ_ROLE','lead_delete_approve_v1','hq_delete_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LEAD_IMP_V1_HQ_ROLE','lead_import_approve_v1','hq_batch_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LEAD_V1_BM_ROLE','lead_approve_v1','branch_manager_approve','ROLE','[\"BRANCH_HEAD\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LEAD_V1_HQ_ROLE','lead_approve_v1','hq_review','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LOAN_V1_BM','loan_approve_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LOAN_V1_CA','loan_approve_v1','credit_approval','ROLE','[\"CREDIT_APPROVER\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LOAN_V1_CK','loan_approve_v1','credit_check','ROLE','[\"CREDIT_REVIEWER\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_LOAN_V1_CORP','loan_approve_v1','corp_review','ROLE','[\"CORP_DEPT\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch','ROLE','[\"SUPPORT_SECRETARY\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle','ROLE','[\"SUPPORT_STAFF\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle','ROLE','[\"SUPPORT_STAFF\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_TGT_ADJ_FL','target_adjust_approve_v1','finance_leader_approve','ROLE','[\"BACK_FINANCE\"]','2026-04-28 00:30:53','2026-04-28 00:32:50'),('WNC_TOUCH_V1_RM_ROLE','touch_process_v1','touch_execute','ROLE','[\"CUST_MANAGER\"]','2026-04-28 00:30:53','2026-04-28 00:32:50');
/*!40000 ALTER TABLE `WF_NODE_CANDIDATE_CONF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `WF_NODE_FORM_CONF`
--

DROP TABLE IF EXISTS `WF_NODE_FORM_CONF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WF_NODE_FORM_CONF` (
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
-- Dumping data for table `WF_NODE_FORM_CONF`
--

LOCK TABLES `WF_NODE_FORM_CONF` WRITE;
/*!40000 ALTER TABLE `WF_NODE_FORM_CONF` DISABLE KEYS */;
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ALLOC_BIZ','alloc_adjust_approve_v1','biz_dept_review','[{\"key\":\"bizDeptOpinion\",\"label\":\"业务部门审核意见\",\"type\":\"TEXTAREA\"}]','[\"bizDeptOpinion\"]','[\"bizDeptOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_ALLOC_BIZ_LDR','alloc_adjust_approve_v1','biz_dept_leader_approve','[{\"key\":\"bizLeaderOpinion\",\"label\":\"业务部门负责人意见\",\"type\":\"TEXTAREA\"}]','[\"bizLeaderOpinion\"]','[\"bizLeaderOpinion\"]','2026-04-28 00:30:56','2026-04-28 00:32:52'),('WFF_ALLOC_BM','alloc_adjust_approve_v1','branch_approve','[{\"key\":\"branchAllocOpinion\",\"label\":\"机构审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchAllocOpinion\"]','[\"branchAllocOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_ALLOC_FIN','alloc_adjust_approve_v1','finance_review','[{\"key\":\"financeOpinion\",\"label\":\"资财部审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"recalcRequired\",\"label\":\"是否需要历史重算\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"}]','[\"financeOpinion\",\"recalcRequired\"]','[\"financeOpinion\",\"recalcRequired\"]','2026-04-28 00:30:56','2026-04-28 00:32:52'),('WFF_ALLOC_FIN_LDR','alloc_adjust_approve_v1','finance_leader_approve','[{\"key\":\"finLeaderOpinion\",\"label\":\"资财部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"finLeaderOpinion\"]','[\"finLeaderOpinion\"]','2026-04-28 00:30:56','2026-04-28 00:32:52'),('WFF_ALLOC_ORIG','alloc_adjust_approve_v1','original_owner_approve','[{\"key\":\"ownerConfirm\",\"label\":\"原管户人确认意见\",\"type\":\"TEXTAREA\"}]','[\"ownerConfirm\"]','[\"ownerConfirm\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LEAD_DEL_V1_HQ','lead_delete_approve_v1','hq_delete_approve','[{\"key\":\"deleteOpinion\",\"label\":\"删除审批意见\",\"type\":\"TEXTAREA\"}]','[\"deleteOpinion\"]','[\"deleteOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LEAD_IMP_V1_HQ','lead_import_approve_v1','hq_batch_approve','[{\"key\":\"batchOpinion\",\"label\":\"批次审批意见\",\"type\":\"TEXTAREA\"}]','[\"batchOpinion\"]','[\"batchOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LEAD_V1_BM','lead_approve_v1','branch_manager_approve','[{\"key\":\"bmOpinion\",\"label\":\"机构负责人意见\",\"type\":\"TEXTAREA\"}]','[\"bmOpinion\"]','[\"bmOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LEAD_V1_HQ','lead_approve_v1','hq_review','[{\"key\":\"hqConclusion\",\"label\":\"总部审核结论\",\"type\":\"TEXTAREA\"},{\"key\":\"riskLevel\",\"label\":\"风险等级\",\"type\":\"SELECT\",\"dictType\":\"RISK_LEVEL\"}]','[\"hqConclusion\",\"riskLevel\"]','[\"hqConclusion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LOAN_V1_BM','loan_approve_v1','branch_approve','[{\"key\":\"branchOpinion\",\"label\":\"机构审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchOpinion\"]','[\"branchOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LOAN_V1_CA','loan_approve_v1','credit_approval','[{\"key\":\"approvalOpinion\",\"label\":\"批复意见\",\"type\":\"TEXTAREA\"},{\"key\":\"approvedAmount\",\"label\":\"批复金额(万元)\",\"type\":\"NUMBER\"},{\"key\":\"approvedTerm\",\"label\":\"批复期限(月)\",\"type\":\"NUMBER\"}]','[\"approvalOpinion\",\"approvedAmount\",\"approvedTerm\"]','[\"approvalOpinion\",\"approvedAmount\",\"approvedTerm\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LOAN_V1_CK','loan_approve_v1','credit_check','[{\"key\":\"creditCheckOpinion\",\"label\":\"授信审查意见\",\"type\":\"TEXTAREA\"},{\"key\":\"creditCheckResult\",\"label\":\"审查结论\",\"type\":\"SELECT\",\"options\":[{\"label\":\"通过\",\"value\":\"PASS\"},{\"label\":\"补充材料\",\"value\":\"SUPPLEMENT\"},{\"label\":\"拒绝\",\"value\":\"REJECT\"}]}]','[\"creditCheckOpinion\",\"creditCheckResult\"]','[\"creditCheckOpinion\",\"creditCheckResult\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_LOAN_V1_CORP','loan_approve_v1','corp_review','[{\"key\":\"corpOpinion\",\"label\":\"公司部审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"needCreditCommittee\",\"label\":\"是否需要上会\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"},{\"key\":\"creditCommitteeConclusion\",\"label\":\"上会结论\",\"type\":\"TEXTAREA\"}]','[\"corpOpinion\",\"needCreditCommittee\",\"creditCommitteeConclusion\"]','[\"corpOpinion\",\"needCreditCommittee\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch','[{\"key\":\"assignedEmpId\",\"label\":\"指定支持人员\",\"type\":\"USER_SELECT\"},{\"key\":\"dispatchRemark\",\"label\":\"派单备注\",\"type\":\"TEXTAREA\"}]','[\"assignedEmpId\",\"dispatchRemark\"]','[\"assignedEmpId\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle','[{\"key\":\"handleResult\",\"label\":\"办理结果\",\"type\":\"TEXTAREA\"},{\"key\":\"visitPhotoUrls\",\"label\":\"拜访照片\",\"type\":\"FILE_LIST\"}]','[\"handleResult\",\"visitPhotoUrls\"]','[\"handleResult\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle','[{\"key\":\"handleResult\",\"label\":\"办理结果\",\"type\":\"TEXTAREA\"},{\"key\":\"visitPhotoUrls\",\"label\":\"拜访照片\",\"type\":\"FILE_LIST\"}]','[\"handleResult\",\"visitPhotoUrls\"]','[\"handleResult\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_TGT_ADJ_FL','target_adjust_approve_v1','finance_leader_approve','[{\"key\":\"adjustOpinion\",\"label\":\"修正审批意见\",\"type\":\"TEXTAREA\"}]','[\"adjustOpinion\"]','[\"adjustOpinion\"]','2026-04-28 00:30:55','2026-04-28 00:32:52'),('WFF_TOUCH_V1_EXEC','touch_process_v1','touch_execute','[]','[]','[]','2026-04-28 00:30:55','2026-04-28 00:32:52');
/*!40000 ALTER TABLE `WF_NODE_FORM_CONF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `WF_TIMEOUT_RULE`
--

DROP TABLE IF EXISTS `WF_TIMEOUT_RULE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WF_TIMEOUT_RULE` (
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
-- Dumping data for table `WF_TIMEOUT_RULE`
--

LOCK TABLES `WF_TIMEOUT_RULE` WRITE;
/*!40000 ALTER TABLE `WF_TIMEOUT_RULE` DISABLE KEYS */;
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ALLOC_BIZ','alloc_adjust_approve_v1','biz_dept_review',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_ALLOC_BIZ_LDR','alloc_adjust_approve_v1','biz_dept_leader_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_ALLOC_BM','alloc_adjust_approve_v1','branch_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_ALLOC_FIN','alloc_adjust_approve_v1','finance_review',48,24,'2026-04-28 00:30:55','2026-04-28 00:32:51'),('WTR_ALLOC_FIN_LDR','alloc_adjust_approve_v1','finance_leader_approve',48,24,'2026-04-28 00:30:55','2026-04-28 00:32:51'),('WTR_ALLOC_ORIG','alloc_adjust_approve_v1','original_owner_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LEAD_DEL_V1_HQ','lead_delete_approve_v1','hq_delete_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LEAD_IMP_V1_HQ','lead_import_approve_v1','hq_batch_approve',72,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LEAD_V1_BM','lead_approve_v1','branch_manager_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LEAD_V1_HQ','lead_approve_v1','hq_review',96,48,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LOAN_V1_BM','loan_approve_v1','branch_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LOAN_V1_CA','loan_approve_v1','credit_approval',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LOAN_V1_CK','loan_approve_v1','credit_check',72,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_LOAN_V1_CORP','loan_approve_v1','corp_review',72,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch',24,8,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle',72,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle',72,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_TGT_ADJ_FL','target_adjust_approve_v1','finance_leader_approve',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51'),('WTR_TOUCH_V1_EXEC','touch_process_v1','touch_execute',48,24,'2026-04-28 00:30:54','2026-04-28 00:32:51');
/*!40000 ALTER TABLE `WF_TIMEOUT_RULE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'onepl'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
-- V1.7 workflow submit/cancel 角色-资源绑定补录
INSERT IGNORE INTO `PT_ROLE_RESOURCE` (`ID`, `ROLE_ID`, `RESOURCE_ID`, `SYS_CODE`, `CREATE_TIME`) VALUES ('rr_rm_w_proc_submit_v17', 'R_RM', 'W_PROC_SUBMIT', 'WF', '2026-04-30 00:00:00');
INSERT IGNORE INTO `PT_ROLE_RESOURCE` (`ID`, `ROLE_ID`, `RESOURCE_ID`, `SYS_CODE`, `CREATE_TIME`) VALUES ('rr_rm_w_proc_cancel_v17', 'R_RM', 'W_PROC_CANCEL', 'WF', '2026-04-30 00:00:00');


/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-30 10:11:00
-- ====================================================================
-- 菜单分配 v1 种子数据：PT_RESOURCE 补 25 条菜单记录 + 修正历史脏数据
--
-- 背景：项目至今 PT_RESOURCE 303 条全是 API 接口/操作记录，
-- 菜单维度（IS_MENU=1 + parent_resource_id 树）从未建立。
-- 本脚本补齐菜单层级，使"角色 → 分配菜单"对话框有真实数据可勾。
--
-- 字段顺序：RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
--           MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID,
--           STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK
--
-- 命名规约：菜单 ID 一律 M_ 开头，与现有 A_/P_/R_/S_ 等接口资源前缀隔离。
-- RESOURCE_METHOD 用约定值 'MENU'（仅占位，不参与 URL 拦截）。
-- 表结构未变更（零 ALTER），完全沿用现有列。
-- ====================================================================

-- ──────────────────────────────────────────────────────────────────
-- Step 0：修正历史脏数据
-- 项目早期种子里 9 条接口型记录被误标为 ISMENU=1（如"工作流任务审批通过"），
-- 本步骤把它们改回 ISMENU=0，避免菜单树查询里出现伪菜单。
-- ──────────────────────────────────────────────────────────────────
UPDATE PT_RESOURCE
   SET ISMENU = 0
 WHERE ISMENU = 1
   AND RESOURCE_METHOD <> 'MENU';

-- ──────────────────────────────────────────────────────────────────
-- Step 0.5：清掉历史菜单种子让脚本幂等（重跑安全）
-- 仅删 M_ 前缀的菜单记录，不影响其他 303 条接口资源。
-- 同时清掉 PT_ROLE_RESOURCE 里跟菜单的绑定（避免外键约束 + 脏数据）。
-- ──────────────────────────────────────────────────────────────────
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'M_%';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID LIKE 'M_%';

-- ──────────────────────────────────────────────────────────────────
-- Step 1：根级菜单（1 条）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_ROOT_WORKSPACE', '/workspace', 'MENU', '工作台', '🏠', 0, 1, '1', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- Step 2：分组节点（3 条，parent=NULL，endflag=0 表示非叶）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
-- 分组节点 URL 必须唯一（uk_pt_resource_url_method_sys 约束），且不是真路由（# 前缀避免被前端 sidebar 误匹配）
('M_GROUP_PERF',   '#group/perf',   'MENU', '绩效与考核', '📈',  1, 1, '0', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1 - 分组'),
('M_GROUP_REPORT', '#group/report', 'MENU', '报表分析',   '📊',  2, 1, '0', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1 - 分组'),
('M_GROUP_SYSTEM', '#group/system', 'MENU', '系统设置',   '⚙️', 3, 1, '0', NULL, 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1 - 分组');

-- ──────────────────────────────────────────────────────────────────
-- Step 3：绩效与考核（6 个叶子菜单）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_PERF_METRICS',   '/perf/metrics',   'MENU', '指标库',   NULL, 1, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_KPI_RULES', '/perf/kpi-rules', 'MENU', 'KPI 规则', NULL, 2, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_TARGETS',   '/perf/targets',   'MENU', '目标管理', NULL, 3, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_IMPORT',    '/perf/import',    'MENU', '数据导入', NULL, 4, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_ADJUST',    '/perf/adjust',    'MENU', '业绩调整', NULL, 5, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_PERF_COMPUTE',   '/perf/compute',   'MENU', '考核计算', NULL, 6, 1, '1', 'M_GROUP_PERF', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- Step 4：报表分析（4 个叶子菜单）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_REPORT_DYNAMIC',   '/report/dynamic',   'MENU', '动态指标查询', NULL, 1, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_REPORT_DASHBOARD', '/report/dashboard', 'MENU', '行长仪表盘',   NULL, 2, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_REPORT_PRESETS',   '/report/presets',   'MENU', '预置报表',     NULL, 3, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_REPORT_SQL',       '/report/sql',       'MENU', 'SQL 探查',     NULL, 4, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- Step 5：系统设置（11 个叶子菜单）
-- ──────────────────────────────────────────────────────────────────
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK) VALUES
('M_SYS_USERS',         '/system/users',         'MENU', '用户管理',  NULL,  1, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_ROLES',         '/system/roles',         'MENU', '角色管理',  NULL,  2, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_RESOURCES',     '/system/resources',     'MENU', '资源/菜单', NULL,  3, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_PERMISSION',    '/system/permission',    'MENU', '权限配置',  NULL,  4, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_DICT',          '/system/dict',          'MENU', '字典管理',  NULL,  5, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_CALENDAR',      '/system/calendar',      'MENU', '工作日历',  NULL,  6, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_JOBS',          '/system/jobs',          'MENU', '任务调度',  NULL,  7, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_AUDIT',         '/system/audit',         'MENU', '审计日志',  NULL,  8, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_NOTIFICATIONS', '/system/notifications', 'MENU', '通知消息',  NULL,  9, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_CONFIG',        '/system/config',        'MENU', '系统配置',  NULL, 10, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1'),
('M_SYS_FILES',         '/system/files',         'MENU', '文件管理',  NULL, 11, 1, '1', 'M_GROUP_SYSTEM', 0, 'YITI', NOW(), 'seed', NOW(), NULL, '菜单分配 v1');

-- ──────────────────────────────────────────────────────────────────
-- 验证：菜单总数应为 25（含 1 根级 + 3 分组 + 21 叶子）
--   SELECT COUNT(*) FROM PT_RESOURCE WHERE ISMENU = 1;
-- 验证树结构：
--   SELECT MENU_RANK_NO, RESOURCE_ID, MENU_NAME, MENU_ENDFLAG, PARENT_RESOURCE_ID
--     FROM PT_RESOURCE WHERE ISMENU = 1 ORDER BY PARENT_RESOURCE_ID, MENU_RANK_NO;
-- ──────────────────────────────────────────────────────────────────

