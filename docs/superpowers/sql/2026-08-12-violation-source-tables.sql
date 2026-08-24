-- 根据 t_acct_*.jpg 与 t_credit_*.jpg 截图还原的两张源表。
-- 目标库：yiti；可重复执行，不覆盖已存在的同名表。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_accountability_for_violations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '违规问责主键id',
  `accountability_for_violations_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `work_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工号',
  `institution_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构名称',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门名称',
  `post_at_the_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规事实发生时职务、岗位',
  `accountability_positions` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被问责时岗位、职务',
  `gender` varchar(5) DEFAULT NULL COMMENT '性别',
  `role_classification` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色分类',
  `type_of_person` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人员类型',
  `document_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件类型',
  `id_number` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件号码',
  `highest_education` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最高学历',
  `the_political_landscape` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '政治面貌',
  `is_dimission` varchar(5) DEFAULT NULL COMMENT '是否离职',
  `time_of_departure` datetime DEFAULT NULL COMMENT '离职时间',
  `Institutional_hierarchy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构层级',
  `attribution_of_violations` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规事实所属条线',
  `business_area` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务领域',
  `type_of_responsibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任类型',
  `primary_and_secondary_responsibility` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主、次要责任',
  `circumstances_of_the_violation` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规情节',
  `rank_at_the_time_of_the_violation` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规事实发生时职级',
  `accountability_ranks` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被问责时职级',
  `violation_characteristics` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规特性',
  `areas_of_violation` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规领域',
  `other_areas` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '其他领域',
  `facts_of_the_violation` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '违规事实',
  `party_attitude` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当事人态度',
  `processing_basis` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理依据',
  `accountability_documents_and_numbers` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问责文件及文号',
  `penalty_time` datetime DEFAULT NULL COMMENT '处罚时间',
  `penalty_period` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处罚期限',
  `penalty_release_time` datetime DEFAULT NULL COMMENT '处罚解除时间',
  `whether_to_submit_supervision` varchar(5) DEFAULT NULL COMMENT '是否报送监管',
  `submission_time` datetime DEFAULT NULL COMMENT '报送时间',
  `general_handling` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '一般处理',
  `disciplinary_action` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '纪律处分',
  `economic_treatment` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '经济处理方式',
  `withholding_amount` decimal(20,4) DEFAULT NULL COMMENT '扣发金额',
  `withholding_instructions` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '扣发说明',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `in_use` smallint DEFAULT '1' COMMENT '是否可用',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员违规问责信息';

CREATE TABLE IF NOT EXISTS `t_credit_violation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `accountability_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问责代码',
  `client_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户名称',
  `iou_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '借据号',
  `Insurance_principal` decimal(20,4) DEFAULT NULL COMMENT '出险本金(万元)',
  `estimated_loss` decimal(20,4) DEFAULT NULL COMMENT '预估损失额/核销金额/批量转让损失(万元)',
  `is_responsibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '认定结果有无责任',
  `is_audit_by_head_office` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否属于总行审核',
  `is_microfinance_credit_business` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否属于普惠金融信贷业务',
  `responsible_person_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任认定对象（姓名）',
  `employee_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '员工工号',
  `position` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职务(岗位)',
  `institution_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构名称',
  `Institutional_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构层级',
  `affiliated_institution` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辖属机构',
  `responsibility_determination` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任认定',
  `coefficient_of_responsibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任系数',
  `responsibility_ratio` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任占比',
  `is_economic_deduction` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '是否免于经济扣发',
  `amount_withheld` decimal(15,4) DEFAULT NULL COMMENT '扣发金额（元）',
  `recycle_and_return` decimal(15,4) DEFAULT NULL COMMENT '回收返还（元）',
  `is_false_buckle` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否虚扣',
  `other_processing` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '其他处理',
  `general_handling` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '一般处理',
  `disciplinary_action` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '纪律处分',
  `processing_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规问责-经济处理-处理类型',
  `amount_withheld_2` decimal(15,4) DEFAULT NULL COMMENT '违规问责-经济处理-扣发金额（元）',
  `withholding_instructions` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规问责-经济处理-扣发说明',
  `name_and_number_of_accountability_document` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '违规问责-问责文件名称及文号',
  `responsibility_determination_time` datetime DEFAULT NULL COMMENT '责任认定时间',
  `whether_to_leave` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否离职(0:是，1:否)',
  `is_report_to_the_banking_regulatory_commission` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否报送银监机构（0:是，1:否）',
  `submission_time` datetime DEFAULT NULL COMMENT '报送时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `in_use` smallint DEFAULT '1' COMMENT '是否启用',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机构信贷风险责任认定及追究处理信息统计汇总表(含首次/返还/核销/批量转让问责）';

-- 既有表增量对齐：该字段页面下拉值为中文“是/否”，不能保留 ascii 字符集。
ALTER TABLE `t_credit_violation`
  MODIFY COLUMN `is_economic_deduction` varchar(50)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '是否免于经济扣发';

-- 浦爱云盾菜单：顶层“浦爱云盾”下包含两张违规台账。
-- M_GROUP_VIOLATION 为既有稳定资源 ID，仅调整展示名称，避免破坏已有角色授权关系。
INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
  MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('M_GROUP_VIOLATION', '#group/yundun', 'MENU', '浦爱云盾', 8, 1, '0', NULL, 0, 'YD', 'system', '浦爱云盾菜单组'),
 ('M_YD_ACCOUNTABILITY', '/yundun/accountability-violations', 'MENU', '人员违规信息', 1, 1, '1', 'M_GROUP_VIOLATION', 0, 'YD', 'system', '人员违规问责信息'),
 ('M_YD_CREDIT', '/yundun/credit-violations', 'MENU', '信贷风险信息', 2, 1, '1', 'M_GROUP_VIOLATION', 0, 'YD', 'system', '信贷风险责任认定信息');

-- 既有环境增量对齐：INSERT IGNORE 不会更新已存在资源，因此显式改名并重挂子菜单。
UPDATE PT_RESOURCE
SET RESOURCE_URL = '#group/yundun',
    MENU_NAME = '浦爱云盾',
    REMARK = '浦爱云盾菜单组'
WHERE RESOURCE_ID = 'M_GROUP_VIOLATION';

UPDATE PT_RESOURCE
SET PARENT_RESOURCE_ID = 'M_GROUP_VIOLATION'
WHERE RESOURCE_ID IN ('M_YD_ACCOUNTABILITY', 'M_YD_CREDIT');

-- 接口资源全部挂到对应叶子菜单，权限配置选中页面时自动联动接口权限。
INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
  MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER)
VALUES
 ('P_YD_ACCT_LIST', '/api/yundun/accountability-violations', 'GET', '人员违规-列表', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_ACCT_GET', '/api/yundun/accountability-violations/{id:[0-9]+}', 'GET', '人员违规-详情', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_ACCT_EXPORT', '/api/yundun/accountability-violations/export', 'GET', '人员违规-导出', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_ACCT_ADD', '/api/yundun/accountability-violations', 'POST', '人员违规-新增', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_ACCT_UPDATE', '/api/yundun/accountability-violations/{id:[0-9]+}', 'PUT', '人员违规-编辑', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_ACCT_DELETE', '/api/yundun/accountability-violations/batch-delete', 'POST', '人员违规-删除', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_ACCT_IMPORT', '/api/yundun/accountability-violations/import', 'POST', '人员违规-导入', 0, 0, '0', 'M_YD_ACCOUNTABILITY', 0, 'YD', 'system'),
 ('P_YD_CREDIT_LIST', '/api/yundun/credit-violations', 'GET', '信贷风险-列表', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system'),
 ('P_YD_CREDIT_GET', '/api/yundun/credit-violations/{id:[0-9]+}', 'GET', '信贷风险-详情', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system'),
 ('P_YD_CREDIT_EXPORT', '/api/yundun/credit-violations/export', 'GET', '信贷风险-导出', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system'),
 ('P_YD_CREDIT_ADD', '/api/yundun/credit-violations', 'POST', '信贷风险-新增', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system'),
 ('P_YD_CREDIT_UPDATE', '/api/yundun/credit-violations/{id:[0-9]+}', 'PUT', '信贷风险-编辑', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system'),
 ('P_YD_CREDIT_DELETE', '/api/yundun/credit-violations/batch-delete', 'POST', '信贷风险-删除', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system'),
 ('P_YD_CREDIT_IMPORT', '/api/yundun/credit-violations/import', 'POST', '信贷风险-导入', 0, 0, '0', 'M_YD_CREDIT', 0, 'YD', 'system');

-- 系统管理员默认展示菜单并拥有其接口；其他角色由“权限配置”页面按需授权。
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT SUBSTRING(MD5(CONCAT(r.ROLE_ID, '|', x.RESOURCE_ID)), 1, 16),
       r.ROLE_ID, x.RESOURCE_ID, 'YD', NOW()
FROM PT_ROLE r
CROSS JOIN (
  SELECT 'M_GROUP_VIOLATION' RESOURCE_ID UNION ALL
  SELECT 'M_YD_ACCOUNTABILITY' UNION ALL SELECT 'M_YD_CREDIT' UNION ALL
  SELECT 'P_YD_ACCT_LIST' UNION ALL SELECT 'P_YD_ACCT_GET' UNION ALL SELECT 'P_YD_ACCT_EXPORT' UNION ALL
  SELECT 'P_YD_ACCT_ADD' UNION ALL SELECT 'P_YD_ACCT_UPDATE' UNION ALL
  SELECT 'P_YD_ACCT_DELETE' UNION ALL SELECT 'P_YD_ACCT_IMPORT' UNION ALL
  SELECT 'P_YD_CREDIT_LIST' UNION ALL SELECT 'P_YD_CREDIT_GET' UNION ALL SELECT 'P_YD_CREDIT_EXPORT' UNION ALL
  SELECT 'P_YD_CREDIT_ADD' UNION ALL SELECT 'P_YD_CREDIT_UPDATE' UNION ALL
  SELECT 'P_YD_CREDIT_DELETE' UNION ALL SELECT 'P_YD_CREDIT_IMPORT'
) x
WHERE r.ROLE_CODE = 'SYS_ADMIN';

INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE
 (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, REMARK)
SELECT SUBSTRING(MD5(CONCAT(r.ROLE_ID, '|VIOLATION')), 1, 16),
       r.ROLE_ID, 'VIOLATION', 'ALL', 0, 'system', '系统管理员违规管理全量范围'
FROM PT_ROLE r
WHERE r.ROLE_CODE = 'SYS_ADMIN';
