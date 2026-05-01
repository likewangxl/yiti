-- =====================================================================
-- performance-engine-center V1.2 Phase Q0.2 基线脚本：调整申请表校准占位
-- Version: V1_2_0
-- Date: 2026-04-23
-- Task: Q0.2
--
-- 目的：
--   1) 登记 V1.2 基线版本号（flyway_schema_history 1.2.0），标识 V1.2 迁移链就位；
--   2) 使用 CREATE TABLE IF NOT EXISTS 兼容既有环境：
--      - 生产环境 onepl 通过 docs/schema/ddl-performance.sql 已建 3 张表；
--      - 测试库 onepl_test_bootstrap 通过 V1_0_0 基线脚本已建 3 张表；
--      - 全新环境首次建库时本脚本负责创建 3 张表（与生产 DDL 完全一致）。
--
-- 涵盖表：
--   1. perf_alloc_adjust_apply  —— 分配关系调整申请主单（V1.2 Q2 对公/零售分配调整）
--   2. perf_alloc_adjust_item   —— 分配关系调整明细（批量员工+比例）
--   3. perf_target_adjust_apply —— 目标修正申请（V1.2 Q3 目标值调整审批）
--
-- 字段规范：
--   - 主键 id varchar(32)（V1.0 整改决策：与 ddl-performance.sql 完全对齐）
--   - 审计四元 created_time / updated_time / created_by / updated_by
--   - business_key / process_instance_id 与 workflow-center 桥接
--   - status DRAFT/IN_APPROVAL/APPROVED/REJECTED（V1.2 Q2/Q3 实现时使用）
--
-- 说明：
--   - 本脚本与 V1_0_0 基线脚本的 CREATE TABLE 语义完全一致（同一份 DDL 来源），
--     属于显式版本化占位，避免 V1.2 迭代开始后无版本号可对齐。
--   - 后续 V1_2_1/V1_2_2 脚本将在此基础上叠加（事件表、导出任务表等）。
-- =====================================================================

-- -------------------------------------------
-- 15. 分配关系调整申请
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `perf_alloc_adjust_apply` (
  `id` varchar(32) NOT NULL COMMENT '申请ID',
  `apply_no` varchar(100) DEFAULT NULL COMMENT '申请编号',
  `cust_id` varchar(32) NOT NULL COMMENT '客户ID',
  `alloc_dim` varchar(16) NOT NULL COMMENT '维度：RULE/ACCOUNT',
  `biz_kind` varchar(32) DEFAULT NULL COMMENT '业务种类',
  `account_no` varchar(64) DEFAULT NULL COMMENT '账号(可选)',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构',
  `remark` text COMMENT '备注',
  `created_by` varchar(32) NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分配关系调整申请';

-- -------------------------------------------
-- 16. 分配关系调整明细
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `perf_alloc_adjust_item` (
  `id` varchar(32) NOT NULL COMMENT '项ID',
  `apply_id` varchar(32) NOT NULL COMMENT '申请ID',
  `emp_id` varchar(32) NOT NULL COMMENT '员工工号',
  `ratio` decimal(5,2) NOT NULL COMMENT '比例(0-100)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_emp` (`apply_id`, `emp_id`),
  KEY `idx_apply_id` (`apply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分配关系调整明细';

-- -------------------------------------------
-- 17. 目标修正申请
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `perf_target_adjust_apply` (
  `id` varchar(32) NOT NULL COMMENT '申请ID',
  `plan_id` varchar(32) NOT NULL COMMENT '目标方案ID',
  `subject_type` varchar(20) NOT NULL COMMENT '对象类型：EMP/ORG',
  `subject_id` varchar(50) NOT NULL COMMENT '对象ID',
  `cycle_key` varchar(20) NOT NULL COMMENT '周期键',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED',
  `business_key` varchar(100) DEFAULT NULL COMMENT '流程业务键',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id` varchar(50) NOT NULL COMMENT '归属机构',
  `remark` text COMMENT '备注',
  `created_by` varchar(32) NOT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标修正申请';
