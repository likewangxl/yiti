mysqldump: [Warning] Using a password on the command line interface can be insecure.

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
DROP TABLE IF EXISTS `PT_ROLE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PT_ROLE` (
  `ROLE_ID` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色ID',
  `ROLE_CODE` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
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

LOCK TABLES `PT_ROLE` WRITE;
/*!40000 ALTER TABLE `PT_ROLE` DISABLE KEYS */;
INSERT INTO `PT_ROLE` VALUES ('R_ADMIN','SYS_ADMIN','系统管理员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 超级管理员，运维与权限管理'),('R_BACK_FINANCE','BACK_FINANCE','中后台员工(资财)',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-05-20 00:00:00','seed','V1 seed - 财务会计部等后台支持（2026-05-20 ROLE_CODE BACK_FINAN→BACK_FINANCE 对齐 candidateValue）'),('R_BACK_TECH','BACK_TECH','中后台员工(科技)',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 信息技术部'),('R_BRANCH_MGR','BRANCH_HEAD','经营机构负责人',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-05-20 00:00:00','seed','flowable real env role（2026-05-20 ROLE_CODE BRANCH_HEA→BRANCH_HEAD 对齐 candidateValue）'),('R_CORP_DEPT','CORP_DEPT','公司部人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_CORP_LEAD','CORP_DEPT_LEADER','公司部负责人',0,'PLATFORM','2026-05-20 00:00:00','seed','2026-05-20 00:00:00','seed','V1 seed - 公司部负责人，用于 alloc_adjust_approve_v1 biz_dept_leader_approve 节点'),('R_CREDIT_APPROVER','CREDIT_APP','授信批复人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_CREDIT_REVIEWER','CREDIT_REV','授信审查人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_FIN_LEAD','FINANCE_LEADER','资财部负责人',0,'PLATFORM','2026-05-20 00:00:00','seed','2026-05-20 00:00:00','seed','V1 seed - 资财部负责人，用于 perf_alloc_adjust/target_adjust finance_leader_approve 节点'),('R_PRESIDENT','BRANCH_PRE','分行行长',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 分行最高管理者'),('R_RETAIL_DEPT','RETAIL_DEPT','零售部人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-05-20 00:00:00','seed','V1 seed - 分行零售业务管理部门（2026-05-20 ROLE_CODE RETAIL_DEP→RETAIL_DEPT 对齐 candidateValue）'),('R_RETAIL_LEAD','RETAIL_DEPT_LEADER','零售部负责人',0,'PLATFORM','2026-05-20 00:00:00','seed','2026-05-20 00:00:00','seed','V1 seed - 零售部负责人，用于 alloc_adjust_approve_v1 biz_dept_leader_approve 节点'),('R_RM','R_RM','客户经理',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-25 16:01:33','flowable-real-env','flowable real env role'),('R_SUPPORT_SEC','SUPPORT_SE','中场支持部门秘书',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 中场支持部门秘书岗'),('R_SUPPORT_STAFF','SUPPORT_ST','中场支持部门人员',0,'PLATFORM','2026-04-03 22:43:46','seed','2026-04-03 22:43:46','seed','V1 seed - 中台部门员工');
/*!40000 ALTER TABLE `PT_ROLE` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

