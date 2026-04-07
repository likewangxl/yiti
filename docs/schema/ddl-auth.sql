-- ============================================
-- 模块：权限认证中心 (auth-permission-center)
-- 描述：用户、角色、资源、机构及关联关系表
-- 版本：V1
-- 创建日期：2026-03-25
-- ============================================

SET NAMES utf8mb4;

-- -------------------------------------------
-- 1. 用户表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `PT_USER` (
  `USER_ID` varchar(50) NOT NULL COMMENT '用户ID（工号）',
  `USERNAME` varchar(200) NOT NULL COMMENT '用户姓名',
  `USERCHNNAME` varchar(200) NOT NULL COMMENT '用户中文姓名',
  `PWD` varchar(64) DEFAULT NULL COMMENT '密码（加密）',
  `EMAIL` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `ISEXPIRED` int DEFAULT '0' COMMENT '1 过期 0 未过期',
  `ISLOCKED` int DEFAULT '0' COMMENT '1 被锁 0 未被锁',
  `PASS_WRONG_COUNT` int DEFAULT '0' COMMENT '密码错误次数',
  `ISENABLED` int DEFAULT '1' COMMENT '0 启用 1 未启用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_AUTHOR` varchar(50) DEFAULT NULL COMMENT '创建者',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_AUTHOR` varchar(50) DEFAULT NULL COMMENT '更新者',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  `PWD_UPDATE_TIME` datetime DEFAULT NULL COMMENT '密码更新时间',
  PRIMARY KEY (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='人员表';

-- -------------------------------------------
-- 2. 角色表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `PT_ROLE` (
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `ROLE_CODE` varchar(50) NOT NULL COMMENT '角色编码',
  `ROLE_CHNAME` varchar(100) NOT NULL COMMENT '角色中文名',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `SYS_CODE` varchar(10) DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

-- -------------------------------------------
-- 3. 资源表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `PT_RESOURCE` (
  `RESOURCE_ID` varchar(20) NOT NULL COMMENT '资源ID',
  `RESOURCE_URL` varchar(256) NOT NULL COMMENT '资源URL（支持Ant通配符）',
  `RESOURCE_METHOD` varchar(10) NOT NULL COMMENT '请求方法：GET/POST/PUT/DELETE，支持 *',
  `MENU_NAME` varchar(256) NOT NULL COMMENT '菜单名称',
  `MENU_ICON_URL` varchar(256) DEFAULT NULL COMMENT '图标路径',
  `MENU_RANK_NO` int DEFAULT '0' COMMENT '菜单排序',
  `ISMENU` int DEFAULT '0' COMMENT '是否菜单 0 是 1 不是',
  `MENU_ENDFLAG` varchar(10) DEFAULT '0' COMMENT '表单结束标志，是否叶子节点菜单 1 是 0 不是',
  `PARENT_RESOURCE_ID` varchar(60) DEFAULT NULL COMMENT '上级资源ID',
  `STATUS` int DEFAULT '0' COMMENT '状态 0启用 1 不启用',
  `SYS_CODE` varchar(10) DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`RESOURCE_ID`),
  UNIQUE KEY `uk_pt_resource_url_method_sys` (`RESOURCE_URL`,`RESOURCE_METHOD`,`SYS_CODE`),
  KEY `idx_pt_resource_status` (`STATUS`,`SYS_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源表';

-- -------------------------------------------
-- 4. 用户角色关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `PT_USER_ROLE` (
  `USER_ID` varchar(50) NOT NULL COMMENT '用户ID',
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `DEFAULT_ASSIGN` int DEFAULT '0' COMMENT '默认分配',
  `INHERIT_ASSIGN` int DEFAULT '0' COMMENT '用户组角色继承',
  `GROUP_ASSING` int DEFAULT '0' COMMENT '角色组分配',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ROLE_ID`),
  KEY `idx_role_id` (`ROLE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

-- -------------------------------------------
-- 5. 角色资源关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `PT_ROLE_RESOURCE` (
  `ID` varchar(32) NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `RESOURCE_ID` varchar(20) NOT NULL COMMENT '资源ID',
  `SYS_CODE` varchar(10) DEFAULT 'PLATFORM' COMMENT '系统编号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_resource_id` (`RESOURCE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色资源关联表';

-- -------------------------------------------
-- 6. 角色业务范围表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `PT_ROLE_BIZ_SCOPE` (
  `ID` varchar(32) NOT NULL COMMENT '主键ID',
  `ROLE_ID` varchar(50) NOT NULL COMMENT '角色ID',
  `BIZ_TYPE` varchar(50) NOT NULL COMMENT '业务类型：NAV/PRODUCT/LEAD/CUSTOMER等',
  `DATA_SCOPE` varchar(50) NOT NULL COMMENT '数据范围：SELF_CREATED/SELF/SELF_ASSIGNED/ORG/ORG_SUBTREE/ALL/WORKFLOW_PARTICIPANT',
  `RECORD_STATUS` int DEFAULT '0' COMMENT '是否可用 0 可用 1 不可用',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `UPDATE_USER` varchar(50) DEFAULT NULL COMMENT '更新人',
  `REMARK` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_pt_role_biz_scope_role_biz` (`ROLE_ID`,`BIZ_TYPE`),
  KEY `idx_role_id` (`ROLE_ID`),
  KEY `idx_biz_type` (`BIZ_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色业务范围表';

-- -------------------------------------------
-- 7. 机构表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `EXT_ORG_INFO` (
  `ID` int NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  `ORG_CODE` varchar(20) NOT NULL COMMENT '机构编号',
  `ORG_NAME` varchar(200) NOT NULL COMMENT '机构名称',
  `ORG_LEVEL` int DEFAULT NULL COMMENT '机构等级 1 总行 2 分行 3 支行',
  `P_ID` varchar(20) DEFAULT NULL COMMENT '上级机构编码',
  `ORGAN_STATE` int DEFAULT '0' COMMENT '状态 0 启用 1 删除',
  `ADM_DIVISION_CODE` varchar(20) DEFAULT NULL COMMENT '行政区划代码',
  `ADM_DIVISION_NAME` varchar(255) DEFAULT NULL COMMENT '行政区划名称',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATE_USER` varchar(50) DEFAULT NULL COMMENT '创建人',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_ext_org_info_org_code` (`ORG_CODE`),
  KEY `idx_p_id` (`P_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机构表';

-- -------------------------------------------
-- 8. 用户机构关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `EXT_USER_ORG` (
  `USER_ID` varchar(50) NOT NULL COMMENT '用户ID',
  `ORG_CODE` varchar(20) NOT NULL COMMENT '机构编码',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`USER_ID`,`ORG_CODE`),
  KEY `idx_org_code` (`ORG_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户机构关联表';
