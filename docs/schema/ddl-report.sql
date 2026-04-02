-- ============================================
-- 模块：报表分析中心 (report-analytics-center)
-- 描述：动态查询保存方案等报表配置表
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 动态查询保存方案（每用户最多保存10条）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `report_saved_query` (
  `id` varchar(32) NOT NULL COMMENT '方案ID',
  `emp_id` varchar(32) NOT NULL COMMENT '员工工号',
  `name` varchar(200) NOT NULL COMMENT '方案名称',
  `dim` varchar(20) NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids` text NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes` text NOT NULL COMMENT '指标编码列表(JSON数组)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';
