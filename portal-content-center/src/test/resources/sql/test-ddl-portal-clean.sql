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
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人 emp_id（同步时为 SYSTEM）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '最后更新人 emp_id',
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
