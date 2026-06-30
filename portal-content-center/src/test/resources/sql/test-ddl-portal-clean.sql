-- ============================================
-- 模块：门户内容中心 (portal-content-center)
-- 描述：网址导航、工作台快捷入口、通讯录、产品信息、文档信息
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 网址导航表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `portal_nav` (
  `id` varchar(32) NOT NULL COMMENT '导航ID',
  `nav_name` varchar(100) NOT NULL COMMENT '导航名称',
  `nav_url` varchar(500) NOT NULL COMMENT '导航URL',
  `nav_icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `nav_category` varchar(50) DEFAULT NULL COMMENT '分类',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网址导航表';

-- -------------------------------------------
-- 2. 工作台快捷入口表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `portal_shortcut` (
  `id` varchar(32) NOT NULL COMMENT '快捷入口ID',
  `shortcut_name` varchar(100) NOT NULL COMMENT '快捷入口名称',
  `shortcut_url` varchar(500) NOT NULL COMMENT '跳转URL',
  `shortcut_icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `shortcut_type` varchar(50) DEFAULT NULL COMMENT '类型：SYSTEM-系统, CUSTOM-自定义',
  `target_type` varchar(50) DEFAULT NULL COMMENT '目标类型：INTERNAL-内部, EXTERNAL-外部',
  `emp_id` varchar(32) DEFAULT NULL COMMENT '所属用户工号（自定义快捷入口）',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_type` (`shortcut_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作台快捷入口表';

-- -------------------------------------------
-- 3. 通讯录员工表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `addrbook_employee` (
  `emp_id` varchar(32) NOT NULL COMMENT '员工工号',
  `emp_name` varchar(100) NOT NULL COMMENT '员工姓名',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `org_code` varchar(50) DEFAULT NULL COMMENT '所属机构代码',
  `org_name` varchar(200) DEFAULT NULL COMMENT '所属机构名称',
  `position` varchar(100) DEFAULT NULL COMMENT '岗位',
  `self_desc` text COMMENT '自我描述',
  `responsible_product_ids` text COMMENT '负责产品ID列表(JSON数组)',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-在职, RESIGNED-离职',
  `maintainer_emp_id` varchar(32) DEFAULT NULL COMMENT '维护人工号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int(11) DEFAULT '0' COMMENT '删除标记：0-未删除, 1-已删除',
  PRIMARY KEY (`emp_id`),
  KEY `idx_org_code` (`org_code`),
  KEY `idx_maintainer` (`maintainer_emp_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通讯录员工表';

-- -------------------------------------------
-- 4. 产品信息表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `product_info` (
  `id` varchar(64) NOT NULL COMMENT '产品ID',
  `product_code` varchar(64) NOT NULL COMMENT '产品代码',
  `product_name` varchar(255) NOT NULL COMMENT '产品名称',
  `product_category` varchar(64) NOT NULL COMMENT '产品类别',
  `description` text COMMENT '产品描述',
  `support_for_support_request` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否支持中场支持',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '归属组织(维护组织)',
  `product_dept_org_code` varchar(50) DEFAULT NULL COMMENT '产品部门ORG_CODE',
  `file_object_id` varchar(32) DEFAULT NULL COMMENT '主附件文件ID',
  `responsible_emp_ids` text COMMENT '负责人列表(JSON数组,反向关联通讯录)',
  `status` varchar(32) NOT NULL COMMENT '产品状态',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_product_code` (`product_code`),
  KEY `idx_category` (`product_category`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品信息表';

-- -------------------------------------------
-- 5. 文档信息表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `doc_info` (
  `id` varchar(32) NOT NULL COMMENT '文档ID',
  `doc_title` varchar(200) NOT NULL COMMENT '文档标题',
  `doc_category` varchar(50) DEFAULT NULL COMMENT '文档分类',
  `file_object_id` varchar(32) DEFAULT NULL COMMENT '文件对象ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用, DISABLED-禁用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档信息表';

-- -------------------------------------------
-- 6. 担保信息同步相关表（GUARANTEE_INFO_SYNC 定时任务专用，源自 prod DDL）
--    字符集刻意沿用 prod：源表 clms/ccms 为 utf8mb3，目标 zh_guarantee_info 为 utf8mb4，
--    以在集成测试中真实复现 client_name(utf8mb4) ↔ customername(utf8mb3) 的跨字符集 JOIN。
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `clms_ed_credit_info` (
  `id` int NOT NULL AUTO_INCREMENT,
  `creditno` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '额度编号',
  `credittype` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '额度类型',
  `execnominalsum` decimal(24,6) DEFAULT NULL COMMENT '当前额度金额',
  `usablenominalsum` decimal(24,6) DEFAULT NULL COMMENT '可用额度金额',
  `suboccupynominalsum` decimal(24,6) DEFAULT NULL COMMENT '额度占用金额',
  `startdate` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '起始日',
  `expiredate` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '到期日',
  `customerid` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '客户编号',
  `customername` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '客户名称',
  `inputuserid` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '登记人',
  `hive_sys_time` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新时间',
  `pt_dt` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `zh_guarantee_info` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `client_num` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户号',
  `client_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户名称',
  `basic_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户基础id',
  `amount_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '额度类型',
  `notional_amount` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '名义金额',
  `occupy_notional_amount` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '已占用名义金额',
  `occupy_exposure_amount` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '已占用敞口金额',
  `expired` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '到期日',
  `start` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '起始日',
  `last_expire` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '授信到期日',
  `organ` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '经办机构',
  `operator` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '经办人',
  `usableexposuresum` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '可用敞口金额',
  `usablenominalsum` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '可用名义金额',
  `create_user` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '1' COMMENT '类型',
  `update_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `zh_guarantee_info_name` (`client_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='担保信息';

CREATE TABLE IF NOT EXISTS `ccms_business_contract` (
  `id` int NOT NULL AUTO_INCREMENT,
  `customerid` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '客户号',
  `customername` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '客户名称',
  `exposurebalance` decimal(24,6) DEFAULT NULL COMMENT '已占用金额(敞口)',
  `businesssum2` decimal(24,6) DEFAULT NULL COMMENT '名义金额',
  `usableexposuresum` decimal(24,6) DEFAULT NULL COMMENT '可用敞口金额',
  `usablenominalsum` decimal(24,6) DEFAULT NULL COMMENT '可用名义金额',
  `putoutdate` varchar(15) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '额度生效日期',
  `maturity` varchar(15) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '额度到期日',
  `operateuserid` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '经办人',
  `sjsj` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `index_business_customerid` (`customerid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
