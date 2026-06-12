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
-- Dumping data for table `PERF_ALLOC_ADJUST_ITEM`
--
-- WHERE:  item_kind='ORIGIN'

LOCK TABLES `PERF_ALLOC_ADJUST_ITEM` WRITE;
/*!40000 ALTER TABLE `PERF_ALLOC_ADJUST_ITEM` DISABLE KEYS */;
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('0d5ea40d2bab4a6cb38df6117855a2c9','5c9f3d1952084db9a6aacef7a3427e9a','ORIGIN','1234567890','rm_zhang','rm_zhang','张客户经理','174300','榆林分行神木支行',50.00,NULL,'2026-06-04 10:57:36');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('1e04882d1e554f919f5d6593764b21fa','fe78c1cdbbb94185adf4a2d207c56400','ORIGIN',NULL,'rm_li','rm_li','李四3(客户经理)','174300','榆林分行神木支行',20.00,NULL,'2026-06-08 10:42:06');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('2a40c417505a4779a253356e280af94c','32ad742feac848b086e846ea2bff040d','ORIGIN',NULL,'zhyg','zhyg','中一个','4401C2','宝鸡分行大润发社区支行',80.00,NULL,'2026-06-08 10:44:49');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('3695fe0cc6bf4f9a8ff4eeb421b5af67','e2aafb2ef5884f628fc32971479ded66','ORIGIN',NULL,'rm_li','rm_li','李四3(客户经理)','132','榆林分行神木支行',100.00,NULL,'2026-06-03 10:22:02');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('5474bd88d8684ed3b537835b38315d46','8cf7314cdb454b3b969da7167626d60c','ORIGIN',NULL,'zhyg','zhyg','中一个','4401C2','宝鸡分行大润发社区支行',100.00,NULL,'2026-06-08 09:35:52');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('6578cc763fbc40669bdbed97c4a6be36','247ed1a65d264620a55a388c7cbbbe84','ORIGIN',NULL,'rm_li','rm_li','李四3(客户经理)','133','榆林分行榆阳支行',90.00,NULL,'2026-06-02 15:56:59');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('730829e89988477494afc485ec7868a3','2208560d0b8f494e9a7da0cdec07b128','ORIGIN','123456789','rm_zhang','rm_zhang','张客户经理','174000','榆林分行(全辖)',100.00,NULL,'2026-06-03 18:33:10');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('7f6d333ffac44d7cbf0ca6c1ca3cfa84','32ad742feac848b086e846ea2bff040d','ORIGIN',NULL,'rm_li','rm_li','李四3(客户经理)','174200','榆林分行府谷支行',20.00,NULL,'2026-06-08 10:44:49');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('802c6eaa188543d3ad0a14f4d0bdf7d3','42c7fa8dba5e42ca97ac81a6da7999c6','ORIGIN',NULL,'zhyg','zhyg','中一个','4401C4','宝鸡分行大庆路小微支行',100.00,NULL,'2026-06-08 09:39:53');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('80b0ad3b9d1f405fa0716983e98fb91f','b4c02aa4bd8e47deb83004658f074f0c','ORIGIN',NULL,'rm_li','rm_li','李四3(客户经理)','1743C1','榆林分行神木大街光明路小微支行',20.00,NULL,'2026-06-08 10:15:21');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('84f4988cedd748159b077a1cd552b720','a8408530fcaa46749b17faae48aa6241','ORIGIN','1','12037073','12037073','孟晓娟','7200','西安分行',30.00,NULL,'2026-06-04 17:36:32');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('93f4566a7dad47ceb7edcd510c7ce249','fe78c1cdbbb94185adf4a2d207c56400','ORIGIN',NULL,'zhyg','zhyg','中一个','宝鸡',NULL,80.00,NULL,'2026-06-08 10:42:06');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('96f6cf0abd5048399af00b1bb6df15fd','2cec19104575450c9f69f761bd2128dc','ORIGIN',NULL,'zhyg','zhyg','中一个','4401C5','宝鸡分行清姜东二路社区支行',20.00,NULL,'2026-06-08 09:53:41');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('973f534a06e54aafb839dff89267514b','5c9f3d1952084db9a6aacef7a3427e9a','ORIGIN','1234567890','rm_li','rm_li','李四3(客户经理)','174300','榆林分行神木支行',50.00,NULL,'2026-06-04 10:57:36');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('d1d9e2647d5c491193bfb54db560acff','d2af3e6133b84dd28354fd74ab10801a','ORIGIN',NULL,'12037073','12037073','孟晓娟','720100','营业部',10.00,NULL,'2026-06-11 16:30:05');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('f39173fff9664bd0a5d07c07d94e90cb','2cec19104575450c9f69f761bd2128dc','ORIGIN',NULL,'rm_li','rm_li','李四3(客户经理)','174300','榆林分行神木支行',80.00,NULL,'2026-06-08 09:53:41');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('f3f23ae0d83f48bba43b31f8111761f0','b4c02aa4bd8e47deb83004658f074f0c','ORIGIN',NULL,'zhyg','zhyg','中一个','4401C2','宝鸡分行大润发社区支行',80.00,NULL,'2026-06-08 10:15:21');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('f73d357674714214a8b0b6275d3f4b53','b005adf434b94dd7807b6d75db80091f','ORIGIN',NULL,'rm_zhang','rm_zhang','张客户经理','174400','榆林分行榆阳支行',100.00,NULL,'2026-06-04 18:18:46');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('fa2966f8d1a6407bb0e6736ec057d751','21f560e1e6b9404eb4e73d2b0ee87932','ORIGIN',NULL,'12038011','12038011','刘鑫','720100','营业部',20.00,NULL,'2026-06-11 16:54:07');
INSERT INTO `PERF_ALLOC_ADJUST_ITEM` (`id`, `apply_id`, `item_kind`, `acct_no`, `emp_id`, `username`, `emp_chn_name`, `org_code`, `org_name`, `ratio`, `remark`, `created_time`) VALUES ('fa39ba7190e344518de539295ae04035','507b2f10a2e642e8b805f8b6d264602f','ORIGIN',NULL,'rm_zhang','rm_zhang','张客户经理','2121C1','渭南分行营业部水园小微支行',100.00,NULL,'2026-06-05 16:04:29');
/*!40000 ALTER TABLE `PERF_ALLOC_ADJUST_ITEM` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-12 11:47:20
