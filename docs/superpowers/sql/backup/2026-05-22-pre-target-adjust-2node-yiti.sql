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
-- Dumping data for table `WF_NODE_CANDIDATE_CONF`
--

LOCK TABLES `WF_NODE_CANDIDATE_CONF` WRITE;
/*!40000 ALTER TABLE `WF_NODE_CANDIDATE_CONF` DISABLE KEYS */;
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ACORP_BIZ','perf_alloc_adjust_corp_v1','biz_dept_review','ROLE','[\"CORP_DEPT\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ACORP_BIZ_LDR','perf_alloc_adjust_corp_v1','biz_dept_leader_approve','ROLE','[\"CORP_DEPT_LEADER\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ACORP_BM','perf_alloc_adjust_corp_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ACORP_FIN','perf_alloc_adjust_corp_v1','finance_review','ROLE','[\"BACK_FINANCE\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ACORP_FIN_LDR','perf_alloc_adjust_corp_v1','finance_leader_approve','ROLE','[\"FINANCE_LEADER\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ACORP_ORIG','perf_alloc_adjust_corp_v1','original_owner_approve','ROLE','[\"R_RM\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ARTL_BIZ','perf_alloc_adjust_retail_v1','biz_dept_review','ROLE','[\"RETAIL_DEPT\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ARTL_BIZ_LDR','perf_alloc_adjust_retail_v1','biz_dept_leader_approve','ROLE','[\"RETAIL_DEPT_LEADER\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ARTL_BM','perf_alloc_adjust_retail_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ARTL_FIN','perf_alloc_adjust_retail_v1','finance_review','ROLE','[\"BACK_FINANCE\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ARTL_FIN_LDR','perf_alloc_adjust_retail_v1','finance_leader_approve','ROLE','[\"FINANCE_LEADER\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_ARTL_ORIG','perf_alloc_adjust_retail_v1','original_owner_approve','ROLE','[\"R_RM\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LEAD_DEL_V1_HQ_ROLE','lead_delete_approve_v1','hq_delete_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LEAD_IMP_V1_HQ_ROLE','lead_import_approve_v1','hq_batch_approve','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LEAD_V1_BM_ROLE','lead_approve_v1','branch_manager_approve','ROLE','[\"BRANCH_HEAD\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LEAD_V1_HQ_ROLE','lead_approve_v1','hq_review','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LOAN_V1_BM','loan_approve_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LOAN_V1_CA','loan_approve_v1','credit_approval','ROLE','[\"CREDIT_APPROVER\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LOAN_V1_CK','loan_approve_v1','credit_check','ROLE','[\"CREDIT_REVIEWER\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_LOAN_V1_CORP','loan_approve_v1','corp_review','ROLE','[\"CORP_DEPT\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_PERF_DEMO_PAC_BMR','perf_alloc_adjust_corp_v1','branch_mgr_review','USER','[\"E20001\"]','2026-05-20 01:12:52','2026-05-20 01:40:32');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_PERF_DEMO_PAC_HMR','perf_alloc_adjust_corp_v1','hq_mgr_review','USER','[\"user002\"]','2026-05-20 01:12:52','2026-05-20 01:40:32');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_PERF_DEMO_PAR_BMR','perf_alloc_adjust_retail_v1','branch_mgr_review','USER','[\"E20001\"]','2026-05-20 01:12:52','2026-05-20 01:40:32');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_PERF_DEMO_PAR_HMR','perf_alloc_adjust_retail_v1','hq_mgr_review','USER','[\"user002\"]','2026-05-20 01:12:52','2026-05-20 01:40:32');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_PERF_DEMO_PTA_BMR','perf_target_adjust_v1','branch_mgr_review','USER','[\"admin\"]','2026-05-20 01:12:52','2026-05-20 01:12:52');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_PERF_DEMO_PTA_HMR','perf_target_adjust_v1','hq_mgr_review','USER','[\"admin\"]','2026-05-20 01:12:52','2026-05-20 01:12:52');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch','ROLE','[\"SUPPORT_SECRETARY\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle','ROLE','[\"SUPPORT_STAFF\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle','ROLE','[\"SUPPORT_STAFF\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TGT_BIZ','perf_target_adjust_v1','biz_dept_review','ROLE','[\"CORP_DEPT\",\"RETAIL_DEPT\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TGT_BIZ_LDR','perf_target_adjust_v1','biz_dept_leader_approve','ROLE','[\"CORP_DEPT_LEADER\",\"RETAIL_DEPT_LEADER\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TGT_BM','perf_target_adjust_v1','branch_approve','ROLE','[\"BRANCH_HEAD\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TGT_FIN','perf_target_adjust_v1','finance_review','ROLE','[\"BACK_FINANCE\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TGT_FIN_LDR','perf_target_adjust_v1','finance_leader_approve','ROLE','[\"FINANCE_LEADER\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TGT_ORIG','perf_target_adjust_v1','original_owner_approve','ROLE','[\"R_RM\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_CANDIDATE_CONF` VALUES ('WNC_TOUCH_V1_RM_ROLE','touch_process_v1','touch_execute','ROLE','[\"R_RM\"]','2026-05-06 21:01:02','2026-05-21 00:23:43');
/*!40000 ALTER TABLE `WF_NODE_CANDIDATE_CONF` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `WF_TIMEOUT_RULE`
--

LOCK TABLES `WF_TIMEOUT_RULE` WRITE;
/*!40000 ALTER TABLE `WF_TIMEOUT_RULE` DISABLE KEYS */;
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ACORP_BIZ','perf_alloc_adjust_corp_v1','biz_dept_review',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ACORP_BIZ_LDR','perf_alloc_adjust_corp_v1','biz_dept_leader_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ACORP_BM','perf_alloc_adjust_corp_v1','branch_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ACORP_FIN','perf_alloc_adjust_corp_v1','finance_review',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ACORP_FIN_LDR','perf_alloc_adjust_corp_v1','finance_leader_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ACORP_ORIG','perf_alloc_adjust_corp_v1','original_owner_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ARTL_BIZ','perf_alloc_adjust_retail_v1','biz_dept_review',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ARTL_BIZ_LDR','perf_alloc_adjust_retail_v1','biz_dept_leader_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ARTL_BM','perf_alloc_adjust_retail_v1','branch_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ARTL_FIN','perf_alloc_adjust_retail_v1','finance_review',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ARTL_FIN_LDR','perf_alloc_adjust_retail_v1','finance_leader_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_ARTL_ORIG','perf_alloc_adjust_retail_v1','original_owner_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LEAD_DEL_V1_HQ','lead_delete_approve_v1','hq_delete_approve',48,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LEAD_IMP_V1_HQ','lead_import_approve_v1','hq_batch_approve',72,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LEAD_V1_BM','lead_approve_v1','branch_manager_approve',48,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LEAD_V1_HQ','lead_approve_v1','hq_review',96,48,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LOAN_V1_BM','loan_approve_v1','branch_approve',48,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LOAN_V1_CA','loan_approve_v1','credit_approval',48,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LOAN_V1_CK','loan_approve_v1','credit_check',72,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_LOAN_V1_CORP','loan_approve_v1','corp_review',72,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch',24,8,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle',72,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle',72,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TGT_BIZ','perf_target_adjust_v1','biz_dept_review',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TGT_BIZ_LDR','perf_target_adjust_v1','biz_dept_leader_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TGT_BM','perf_target_adjust_v1','branch_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TGT_FIN','perf_target_adjust_v1','finance_review',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TGT_FIN_LDR','perf_target_adjust_v1','finance_leader_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TGT_ORIG','perf_target_adjust_v1','original_owner_approve',48,24,'2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_TIMEOUT_RULE` VALUES ('WTR_TOUCH_V1_EXEC','touch_process_v1','touch_execute',48,24,'2026-05-06 21:01:02','2026-05-06 21:01:02');
/*!40000 ALTER TABLE `WF_TIMEOUT_RULE` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `WF_NODE_FORM_CONF`
--

LOCK TABLES `WF_NODE_FORM_CONF` WRITE;
/*!40000 ALTER TABLE `WF_NODE_FORM_CONF` DISABLE KEYS */;
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ACORP_BIZ','perf_alloc_adjust_corp_v1','biz_dept_review','[{\"key\":\"bizDeptOpinion\",\"label\":\"公司部经办审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"needsOriginalOwnerApprove\",\"label\":\"是否需要原业绩所属人审批\",\"type\":\"CHECKBOX\"}]','[\"bizDeptOpinion\",\"needsOriginalOwnerApprove\"]','[\"bizDeptOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ACORP_BIZ_LDR','perf_alloc_adjust_corp_v1','biz_dept_leader_approve','[{\"key\":\"bizLeaderOpinion\",\"label\":\"公司部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"bizLeaderOpinion\"]','[\"bizLeaderOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ACORP_BM','perf_alloc_adjust_corp_v1','branch_approve','[{\"key\":\"branchAllocOpinion\",\"label\":\"机构负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchAllocOpinion\"]','[\"branchAllocOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ACORP_FIN','perf_alloc_adjust_corp_v1','finance_review','[{\"key\":\"financeOpinion\",\"label\":\"资财部经办审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"recalcRequired\",\"label\":\"是否需要历史重算\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"}]','[\"financeOpinion\",\"recalcRequired\"]','[\"financeOpinion\",\"recalcRequired\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ACORP_FIN_LDR','perf_alloc_adjust_corp_v1','finance_leader_approve','[{\"key\":\"finLeaderOpinion\",\"label\":\"资财部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"finLeaderOpinion\"]','[\"finLeaderOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ACORP_ORIG','perf_alloc_adjust_corp_v1','original_owner_approve','[{\"key\":\"ownerConfirm\",\"label\":\"原业绩所属人确认意见\",\"type\":\"TEXTAREA\"}]','[\"ownerConfirm\"]','[\"ownerConfirm\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ARTL_BIZ','perf_alloc_adjust_retail_v1','biz_dept_review','[{\"key\":\"bizDeptOpinion\",\"label\":\"零售部经办审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"needsOriginalOwnerApprove\",\"label\":\"是否需要原业绩所属人审批\",\"type\":\"CHECKBOX\"}]','[\"bizDeptOpinion\",\"needsOriginalOwnerApprove\"]','[\"bizDeptOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ARTL_BIZ_LDR','perf_alloc_adjust_retail_v1','biz_dept_leader_approve','[{\"key\":\"bizLeaderOpinion\",\"label\":\"零售部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"bizLeaderOpinion\"]','[\"bizLeaderOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ARTL_BM','perf_alloc_adjust_retail_v1','branch_approve','[{\"key\":\"branchAllocOpinion\",\"label\":\"机构负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchAllocOpinion\"]','[\"branchAllocOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ARTL_FIN','perf_alloc_adjust_retail_v1','finance_review','[{\"key\":\"financeOpinion\",\"label\":\"资财部经办审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"recalcRequired\",\"label\":\"是否需要历史重算\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"}]','[\"financeOpinion\",\"recalcRequired\"]','[\"financeOpinion\",\"recalcRequired\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ARTL_FIN_LDR','perf_alloc_adjust_retail_v1','finance_leader_approve','[{\"key\":\"finLeaderOpinion\",\"label\":\"资财部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"finLeaderOpinion\"]','[\"finLeaderOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_ARTL_ORIG','perf_alloc_adjust_retail_v1','original_owner_approve','[{\"key\":\"ownerConfirm\",\"label\":\"原业绩所属人确认意见\",\"type\":\"TEXTAREA\"}]','[\"ownerConfirm\"]','[\"ownerConfirm\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LEAD_DEL_V1_HQ','lead_delete_approve_v1','hq_delete_approve','[{\"key\":\"deleteOpinion\",\"label\":\"删除审批意见\",\"type\":\"TEXTAREA\"}]','[\"deleteOpinion\"]','[\"deleteOpinion\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LEAD_IMP_V1_HQ','lead_import_approve_v1','hq_batch_approve','[{\"key\":\"batchOpinion\",\"label\":\"批次审批意见\",\"type\":\"TEXTAREA\"}]','[\"batchOpinion\"]','[\"batchOpinion\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LEAD_V1_BM','lead_approve_v1','branch_manager_approve','[{\"key\":\"bmOpinion\",\"label\":\"机构负责人意见\",\"type\":\"TEXTAREA\"}]','[\"bmOpinion\"]','[\"bmOpinion\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LEAD_V1_HQ','lead_approve_v1','hq_review','[{\"key\":\"hqConclusion\",\"label\":\"总部审核结论\",\"type\":\"TEXTAREA\"},{\"key\":\"riskLevel\",\"label\":\"风险等级\",\"type\":\"SELECT\",\"dictType\":\"RISK_LEVEL\"}]','[\"hqConclusion\",\"riskLevel\"]','[\"hqConclusion\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LOAN_V1_BM','loan_approve_v1','branch_approve','[{\"key\":\"branchOpinion\",\"label\":\"机构审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchOpinion\"]','[\"branchOpinion\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LOAN_V1_CA','loan_approve_v1','credit_approval','[{\"key\":\"approvalOpinion\",\"label\":\"批复意见\",\"type\":\"TEXTAREA\"},{\"key\":\"approvedAmount\",\"label\":\"批复金额(万元)\",\"type\":\"NUMBER\"},{\"key\":\"approvedTerm\",\"label\":\"批复期限(月)\",\"type\":\"NUMBER\"}]','[\"approvalOpinion\",\"approvedAmount\",\"approvedTerm\"]','[\"approvalOpinion\",\"approvedAmount\",\"approvedTerm\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LOAN_V1_CK','loan_approve_v1','credit_check','[{\"key\":\"creditCheckOpinion\",\"label\":\"授信审查意见\",\"type\":\"TEXTAREA\"},{\"key\":\"creditCheckResult\",\"label\":\"审查结论\",\"type\":\"SELECT\",\"options\":[{\"label\":\"通过\",\"value\":\"PASS\"},{\"label\":\"补充材料\",\"value\":\"SUPPLEMENT\"},{\"label\":\"拒绝\",\"value\":\"REJECT\"}]}]','[\"creditCheckOpinion\",\"creditCheckResult\"]','[\"creditCheckOpinion\",\"creditCheckResult\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_LOAN_V1_CORP','loan_approve_v1','corp_review','[{\"key\":\"corpOpinion\",\"label\":\"公司部审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"needCreditCommittee\",\"label\":\"是否需要上会\",\"type\":\"RADIO\",\"dictType\":\"YES_NO\"},{\"key\":\"creditCommitteeConclusion\",\"label\":\"上会结论\",\"type\":\"TEXTAREA\"}]','[\"corpOpinion\",\"needCreditCommittee\",\"creditCommitteeConclusion\"]','[\"corpOpinion\",\"needCreditCommittee\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_SUP_COMPLEX_SEC','support_complex_v1','dept_secretary_dispatch','[{\"key\":\"assignedEmpId\",\"label\":\"指定支持人员\",\"type\":\"USER_SELECT\"},{\"key\":\"dispatchRemark\",\"label\":\"派单备注\",\"type\":\"TEXTAREA\"}]','[\"assignedEmpId\",\"dispatchRemark\"]','[\"assignedEmpId\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_SUP_COMPLEX_STAFF','support_complex_v1','support_staff_handle','[{\"key\":\"handleResult\",\"label\":\"办理结果\",\"type\":\"TEXTAREA\"},{\"key\":\"visitPhotoUrls\",\"label\":\"拜访照片\",\"type\":\"FILE_LIST\"}]','[\"handleResult\",\"visitPhotoUrls\"]','[\"handleResult\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_SUP_SIMPLE_OWNER','support_simple_v1','product_owner_handle','[{\"key\":\"handleResult\",\"label\":\"办理结果\",\"type\":\"TEXTAREA\"},{\"key\":\"visitPhotoUrls\",\"label\":\"拜访照片\",\"type\":\"FILE_LIST\"}]','[\"handleResult\",\"visitPhotoUrls\"]','[\"handleResult\"]','2026-05-06 21:01:02','2026-05-06 21:01:02');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TGT_BIZ','perf_target_adjust_v1','biz_dept_review','[{\"key\":\"bizDeptOpinion\",\"label\":\"业务部门经办审核意见\",\"type\":\"TEXTAREA\"},{\"key\":\"needsOriginalOwnerApprove\",\"label\":\"是否需要原业绩所属人审批\",\"type\":\"CHECKBOX\"}]','[\"bizDeptOpinion\",\"needsOriginalOwnerApprove\"]','[\"bizDeptOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TGT_BIZ_LDR','perf_target_adjust_v1','biz_dept_leader_approve','[{\"key\":\"bizLeaderOpinion\",\"label\":\"业务部门负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"bizLeaderOpinion\"]','[\"bizLeaderOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TGT_BM','perf_target_adjust_v1','branch_approve','[{\"key\":\"branchAllocOpinion\",\"label\":\"机构负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"branchAllocOpinion\"]','[\"branchAllocOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TGT_FIN','perf_target_adjust_v1','finance_review','[{\"key\":\"financeOpinion\",\"label\":\"资财部经办审核意见\",\"type\":\"TEXTAREA\"}]','[\"financeOpinion\"]','[\"financeOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TGT_FIN_LDR','perf_target_adjust_v1','finance_leader_approve','[{\"key\":\"finLeaderOpinion\",\"label\":\"资财部负责人审批意见\",\"type\":\"TEXTAREA\"}]','[\"finLeaderOpinion\"]','[\"finLeaderOpinion\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TGT_ORIG','perf_target_adjust_v1','original_owner_approve','[{\"key\":\"ownerConfirm\",\"label\":\"原业绩所属人确认意见\",\"type\":\"TEXTAREA\"}]','[\"ownerConfirm\"]','[\"ownerConfirm\"]','2026-05-21 00:22:38','2026-05-21 00:22:38');
INSERT INTO `WF_NODE_FORM_CONF` VALUES ('WFF_TOUCH_V1_EXEC','touch_process_v1','touch_execute','[]','[]','[]','2026-05-06 21:01:02','2026-05-06 21:01:02');
/*!40000 ALTER TABLE `WF_NODE_FORM_CONF` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-22  1:08:17
