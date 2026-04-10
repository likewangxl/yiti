-- --------------------------------------------------------
-- 主机:                           127.0.0.1
-- 服务器版本:                        8.0.45 - MySQL Community Server - GPL
-- 服务器操作系统:                      Win64
-- HeidiSQL 版本:                  12.16.0.7229
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- 正在导出表  onepl.act_evt_log 的数据：~0 rows (大约)
DELETE FROM `act_evt_log`;

-- 正在导出表  onepl.act_ge_bytearray 的数据：~0 rows (大约)
DELETE FROM `act_ge_bytearray`;

-- 正在导出表  onepl.act_ge_property 的数据：~13 rows (大约)
DELETE FROM `act_ge_property`;
INSERT INTO `act_ge_property` (`NAME_`, `VALUE_`, `REV_`) VALUES
	('batch.schema.version', '7.0.1.1', 1),
	('cfg.execution-related-entities-count', 'true', 1),
	('cfg.task-related-entities-count', 'true', 1),
	('common.schema.version', '7.0.1.1', 1),
	('entitylink.schema.version', '7.0.1.1', 1),
	('eventsubscription.schema.version', '7.0.1.1', 1),
	('identitylink.schema.version', '7.0.1.1', 1),
	('job.schema.version', '7.0.1.1', 1),
	('next.dbid', '1', 1),
	('schema.history', 'create(7.0.1.1)', 1),
	('schema.version', '7.0.1.1', 1),
	('task.schema.version', '7.0.1.1', 1),
	('variable.schema.version', '7.0.1.1', 1);

-- 正在导出表  onepl.act_hi_actinst 的数据：~0 rows (大约)
DELETE FROM `act_hi_actinst`;

-- 正在导出表  onepl.act_hi_attachment 的数据：~0 rows (大约)
DELETE FROM `act_hi_attachment`;

-- 正在导出表  onepl.act_hi_comment 的数据：~0 rows (大约)
DELETE FROM `act_hi_comment`;

-- 正在导出表  onepl.act_hi_detail 的数据：~0 rows (大约)
DELETE FROM `act_hi_detail`;

-- 正在导出表  onepl.act_hi_entitylink 的数据：~0 rows (大约)
DELETE FROM `act_hi_entitylink`;

-- 正在导出表  onepl.act_hi_identitylink 的数据：~0 rows (大约)
DELETE FROM `act_hi_identitylink`;

-- 正在导出表  onepl.act_hi_procinst 的数据：~0 rows (大约)
DELETE FROM `act_hi_procinst`;

-- 正在导出表  onepl.act_hi_taskinst 的数据：~0 rows (大约)
DELETE FROM `act_hi_taskinst`;

-- 正在导出表  onepl.act_hi_tsk_log 的数据：~0 rows (大约)
DELETE FROM `act_hi_tsk_log`;

-- 正在导出表  onepl.act_hi_varinst 的数据：~0 rows (大约)
DELETE FROM `act_hi_varinst`;

-- 正在导出表  onepl.act_procdef_info 的数据：~0 rows (大约)
DELETE FROM `act_procdef_info`;

-- 正在导出表  onepl.act_re_deployment 的数据：~0 rows (大约)
DELETE FROM `act_re_deployment`;

-- 正在导出表  onepl.act_re_model 的数据：~0 rows (大约)
DELETE FROM `act_re_model`;

-- 正在导出表  onepl.act_re_procdef 的数据：~0 rows (大约)
DELETE FROM `act_re_procdef`;

-- 正在导出表  onepl.act_ru_actinst 的数据：~0 rows (大约)
DELETE FROM `act_ru_actinst`;

-- 正在导出表  onepl.act_ru_deadletter_job 的数据：~0 rows (大约)
DELETE FROM `act_ru_deadletter_job`;

-- 正在导出表  onepl.act_ru_entitylink 的数据：~0 rows (大约)
DELETE FROM `act_ru_entitylink`;

-- 正在导出表  onepl.act_ru_event_subscr 的数据：~0 rows (大约)
DELETE FROM `act_ru_event_subscr`;

-- 正在导出表  onepl.act_ru_execution 的数据：~0 rows (大约)
DELETE FROM `act_ru_execution`;

-- 正在导出表  onepl.act_ru_external_job 的数据：~0 rows (大约)
DELETE FROM `act_ru_external_job`;

-- 正在导出表  onepl.act_ru_history_job 的数据：~0 rows (大约)
DELETE FROM `act_ru_history_job`;

-- 正在导出表  onepl.act_ru_identitylink 的数据：~0 rows (大约)
DELETE FROM `act_ru_identitylink`;

-- 正在导出表  onepl.act_ru_job 的数据：~0 rows (大约)
DELETE FROM `act_ru_job`;

-- 正在导出表  onepl.act_ru_suspended_job 的数据：~0 rows (大约)
DELETE FROM `act_ru_suspended_job`;

-- 正在导出表  onepl.act_ru_task 的数据：~0 rows (大约)
DELETE FROM `act_ru_task`;

-- 正在导出表  onepl.act_ru_timer_job 的数据：~0 rows (大约)
DELETE FROM `act_ru_timer_job`;

-- 正在导出表  onepl.act_ru_variable 的数据：~0 rows (大约)
DELETE FROM `act_ru_variable`;

-- 正在导出表  onepl.addrbook_employee 的数据：~0 rows (大约)
DELETE FROM `addrbook_employee`;

-- 正在导出表  onepl.audit_log 的数据：~0 rows (大约)
DELETE FROM `audit_log`;
INSERT INTO `audit_log` (`id`, `trace_id`, `emp_id`, `emp_name`, `biz_type`, `biz_action`, `resource_url`, `request_method`, `request_params`, `response_status`, `error_msg`, `ip_address`, `user_agent`, `execution_time`, `reason`, `created_time`) VALUES
	('0217835a9c194bcbbd3f60699877df19', NULL, 'admin', NULL, 'SQL_PROBE', 'EXECUTE_SQL', NULL, NULL, 'SELECT COUNT(*) AS cnt FROM PT_RESOURCE LIMIT 1000', 200, NULL, NULL, NULL, NULL, 'count', '2026-04-10 11:38:14'),
	('22892c8cff3a4c6a9cecd0efe8d67689', NULL, 'admin', NULL, 'SQL_PROBE', 'EXECUTE_SQL', NULL, NULL, 'SELECT COUNT(*) AS cnt FROM PT_RESOURCE LIMIT 1000', 200, NULL, NULL, NULL, NULL, 'count', '2026-04-10 12:20:13'),
	('4baf0386c31d4e1aa11785f00acd4235', NULL, 'admin', NULL, 'SQL_PROBE', 'EXECUTE_SQL', NULL, NULL, 'SELECT 1 AS ok LIMIT 1000', 200, NULL, NULL, NULL, NULL, 'test', '2026-04-10 12:20:11'),
	('c9274bde370449aa814a1a520eebabd3', NULL, 'admin', NULL, 'SQL_PROBE', 'EXECUTE_SQL', NULL, NULL, 'SELECT 1 LIMIT 1000', 200, NULL, NULL, NULL, NULL, 'test', '2026-04-10 11:38:11');

-- 正在导出表  onepl.biz_file_rel 的数据：~0 rows (大约)
DELETE FROM `biz_file_rel`;

-- 正在导出表  onepl.biz_process_map 的数据：~0 rows (大约)
DELETE FROM `biz_process_map`;

-- 正在导出表  onepl.cust_alloc_relation 的数据：~0 rows (大约)
DELETE FROM `cust_alloc_relation`;

-- 正在导出表  onepl.cust_claim 的数据：~0 rows (大约)
DELETE FROM `cust_claim`;

-- 正在导出表  onepl.cust_index_result 的数据：~0 rows (大约)
DELETE FROM `cust_index_result`;

-- 正在导出表  onepl.cust_lead 的数据：~0 rows (大约)
DELETE FROM `cust_lead`;

-- 正在导出表  onepl.cust_master 的数据：~0 rows (大约)
DELETE FROM `cust_master`;

-- 正在导出表  onepl.cust_tag 的数据：~0 rows (大约)
DELETE FROM `cust_tag`;

-- 正在导出表  onepl.cust_tag_rel 的数据：~0 rows (大约)
DELETE FROM `cust_tag_rel`;

-- 正在导出表  onepl.doc_info 的数据：~0 rows (大约)
DELETE FROM `doc_info`;

-- 正在导出表  onepl.emp_index_result 的数据：~0 rows (大约)
DELETE FROM `emp_index_result`;

-- 正在导出表  onepl.ext_org_info 的数据：~5 rows (大约)
DELETE FROM `ext_org_info`;
INSERT INTO `ext_org_info` (`ID`, `ORG_CODE`, `ORG_NAME`, `ORG_LEVEL`, `P_ID`, `ORGAN_STATE`, `ADM_DIVISION_CODE`, `ADM_DIVISION_NAME`, `CREATE_TIME`, `CREATE_USER`) VALUES
	(1, 'HQ', '总行', 1, NULL, 0, NULL, NULL, '2026-04-07 15:49:38', NULL),
	(2, 'BJ', '北京分行', 2, 'HQ', 0, NULL, NULL, '2026-04-07 15:49:38', NULL),
	(3, 'SH', '上海分行', 2, 'HQ', 0, NULL, NULL, '2026-04-07 15:49:38', NULL),
	(4, 'BJ_CY', '北京分行朝阳支行', 3, 'BJ', 0, NULL, NULL, '2026-04-07 15:49:38', NULL),
	(5, 'SH_PD', '上海分行浦东支行', 3, 'SH', 0, NULL, NULL, '2026-04-07 15:49:38', NULL);

-- 正在导出表  onepl.ext_user_org 的数据：~3 rows (大约)
DELETE FROM `ext_user_org`;
INSERT INTO `ext_user_org` (`USER_ID`, `ORG_CODE`, `CREATE_TIME`) VALUES
	('admin', 'HQ', '2026-04-07 15:49:38'),
	('E10002', 'SH_PD', '2026-04-10 11:17:49'),
	('E20001', 'BJ_CY', '2026-04-10 11:17:49'),
	('E30001', 'HQ', '2026-04-10 11:17:49'),
	('E30002', 'HQ', '2026-04-10 11:17:49'),
	('E40001', 'HQ', '2026-04-10 11:17:49'),
	('E40002', 'HQ', '2026-04-10 11:17:49'),
	('E50001', 'HQ', '2026-04-10 11:17:49'),
	('E50002', 'HQ', '2026-04-10 11:17:49'),
	('E60001', 'HQ', '2026-04-10 11:17:49'),
	('E60002', 'HQ', '2026-04-10 11:17:49'),
	('user001', 'BJ_CY', '2026-04-07 15:49:38'),
	('user002', 'SH_PD', '2026-04-07 15:49:38');

-- 正在导出表  onepl.file_object 的数据：~0 rows (大约)
DELETE FROM `file_object`;

-- 正在导出表  onepl.flw_ru_batch 的数据：~0 rows (大约)
DELETE FROM `flw_ru_batch`;

-- 正在导出表  onepl.flw_ru_batch_part 的数据：~0 rows (大约)
DELETE FROM `flw_ru_batch_part`;

-- 正在导出表  onepl.kpi_result 的数据：~0 rows (大约)
DELETE FROM `kpi_result`;

-- 正在导出表  onepl.lead_import_batch 的数据：~0 rows (大约)
DELETE FROM `lead_import_batch`;

-- 正在导出表  onepl.loan_apply 的数据：~0 rows (大约)
DELETE FROM `loan_apply`;

-- 正在导出表  onepl.org_index_result 的数据：~0 rows (大约)
DELETE FROM `org_index_result`;

-- 正在导出表  onepl.perf_alloc_adjust_apply 的数据：~0 rows (大约)
DELETE FROM `perf_alloc_adjust_apply`;

-- 正在导出表  onepl.perf_alloc_adjust_item 的数据：~0 rows (大约)
DELETE FROM `perf_alloc_adjust_item`;

-- 正在导出表  onepl.perf_import_batch 的数据：~0 rows (大约)
DELETE FROM `perf_import_batch`;

-- 正在导出表  onepl.perf_kpi_item 的数据：~0 rows (大约)
DELETE FROM `perf_kpi_item`;

-- 正在导出表  onepl.perf_kpi_scheme 的数据：~0 rows (大约)
DELETE FROM `perf_kpi_scheme`;

-- 正在导出表  onepl.perf_metric_def 的数据：~0 rows (大约)
DELETE FROM `perf_metric_def`;

-- 正在导出表  onepl.perf_metric_ref 的数据：~0 rows (大约)
DELETE FROM `perf_metric_ref`;

-- 正在导出表  onepl.perf_run_task 的数据：~0 rows (大约)
DELETE FROM `perf_run_task`;

-- 正在导出表  onepl.perf_target_adjust_apply 的数据：~0 rows (大约)
DELETE FROM `perf_target_adjust_apply`;

-- 正在导出表  onepl.perf_target_plan 的数据：~0 rows (大约)
DELETE FROM `perf_target_plan`;

-- 正在导出表  onepl.perf_target_value 的数据：~0 rows (大约)
DELETE FROM `perf_target_value`;

-- 正在导出表  onepl.portal_nav 的数据：~5 rows (大约)
DELETE FROM `portal_nav`;
INSERT INTO `portal_nav` (`id`, `nav_name`, `nav_url`, `nav_icon`, `nav_category`, `sort_order`, `status`, `created_by`, `created_time`, `updated_by`, `updated_time`) VALUES
	('NAV001', 'CCRM系统', 'https://ccrm.bank.com', 'icon-ccrm', '总行系统', 1, 'ACTIVE', 'SYSTEM', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('NAV002', 'PCRM系统', 'https://pcrm.bank.com', 'icon-pcrm', '总行系统', 2, 'ACTIVE', 'SYSTEM', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('NAV003', '网银系统', 'https://ebank.bank.com', 'icon-ebank', '电子渠道', 3, 'ACTIVE', 'SYSTEM', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('NAV004', '信贷管理系统', 'https://credit.bank.com', 'icon-credit', '风险管理', 4, 'ACTIVE', 'SYSTEM', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('NAV005', 'OA系统', 'https://oa.bank.com', 'icon-oa', '办公系统', 5, 'ACTIVE', 'SYSTEM', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31');

-- 正在导出表  onepl.portal_shortcut 的数据：~0 rows (大约)
DELETE FROM `portal_shortcut`;

-- 正在导出表  onepl.product_info 的数据：~4 rows (大约)
DELETE FROM `product_info`;
INSERT INTO `product_info` (`id`, `product_code`, `product_name`, `product_category`, `description`, `support_for_support_request`, `owner_org_id`, `product_dept_org_code`, `file_object_id`, `responsible_emp_ids`, `status`, `created_by`, `updated_by`, `created_time`, `updated_time`, `deleted`) VALUES
	('PROD001', 'TBK_DEPOSIT', '交易银行-结构性存款', '交易银行', '结构性存款产品介绍', 0, NULL, NULL, NULL, NULL, 'ACTIVE', 'SYSTEM', NULL, '2026-04-03 22:46:31', '2026-04-03 22:46:31', 0),
	('PROD002', 'TBK_SUPPLY_CHAIN', '交易银行-供应链金融', '交易银行', '供应链金融产品介绍', 0, NULL, NULL, NULL, NULL, 'ACTIVE', 'SYSTEM', NULL, '2026-04-03 22:46:31', '2026-04-03 22:46:31', 0),
	('PROD003', 'FM_BOND', '金融市场-债券承销', '金融市场', '债券承销服务介绍', 0, NULL, NULL, NULL, NULL, 'ACTIVE', 'SYSTEM', NULL, '2026-04-03 22:46:31', '2026-04-03 22:46:31', 0),
	('PROD004', 'CORP_LOAN', '公司-流动资金贷款', '公司银行', '流动资金贷款产品', 0, NULL, NULL, NULL, NULL, 'ACTIVE', 'SYSTEM', NULL, '2026-04-03 22:46:31', '2026-04-03 22:46:31', 0);

-- 正在导出表  onepl.pt_resource 的数据：~84 rows (大约)
DELETE FROM `pt_resource`;
INSERT INTO `pt_resource` (`RESOURCE_ID`, `RESOURCE_URL`, `RESOURCE_METHOD`, `MENU_NAME`, `MENU_ICON_URL`, `MENU_RANK_NO`, `ISMENU`, `MENU_ENDFLAG`, `PARENT_RESOURCE_ID`, `STATUS`, `SYS_CODE`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`) VALUES
	('A_BZ_DELETE', '/api/admin/biz-scopes/*', 'DELETE', '删除业务范围', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_BZ_LIST', '/api/admin/biz-scopes', 'GET', '业务范围列表', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_BZ_MATRIX', '/api/admin/biz-scopes/matrix', 'GET', '业务范围矩阵', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_BZ_SAVE', '/api/admin/biz-scopes', 'POST', '保存业务范围', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_CHECK_PERM', '/api/auth/check-permission', 'POST', '权限校验', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_CURR_USER', '/api/auth/current-user', 'GET', '当前用户信息', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_LOGIN', '/api/auth/login', 'POST', '用户登录', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_LOGOUT', '/api/auth/logout', 'POST', '用户登出', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ORG_SUBTREE', '/api/orgs/subtree', 'GET', '当前机构子树', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ORG_TREE', '/api/orgs/tree', 'GET', '组织机构树', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ORG_USERS', '/api/orgs/*/users', 'GET', '机构下用户', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_PERMS', '/api/auth/permissions', 'GET', '当前用户权限集', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RES_CREATE', '/api/admin/resources', 'POST', '创建资源', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RES_DELETE', '/api/admin/resources/*', 'DELETE', '删除资源', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RES_TREE', '/api/admin/resources/tree', 'GET', '资源树', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RES_UPDATE', '/api/admin/resources/*', 'PUT', '更新资源', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ROLE_CREATE', '/api/admin/roles/', 'POST', '创建角色', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ROLE_DELETE', '/api/admin/roles/*', 'DELETE', '删除角色', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ROLE_LIST', '/api/admin/roles/', 'GET', '角色列表', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ROLE_UPDATE', '/api/admin/roles/*', 'PUT', '更新角色', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_ROLE_USERS', '/api/admin/roles/*/users', 'GET', '角色下用户列表', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RR_BIND', '/api/admin/roles/*/resources', 'POST', '增量绑定角色资源', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RR_LIST', '/api/admin/roles/*/resources', 'GET', '角色资源列表', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_RR_REPLACE', '/api/admin/roles/*/resources', 'PUT', '全量替换角色资源', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_UR_BIND', '/api/admin/users/*/roles', 'POST', '绑定用户角色', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_UR_DEL', '/api/admin/users/*/roles/*', 'DELETE', '解绑用户角色', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('A_UR_LIST', '/api/admin/users/*/roles', 'GET', '用户角色列表', NULL, 0, 0, '0', NULL, 0, 'AUTH', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_AUDIT_DETAIL', '/api/admin/sys/audit-logs/*', 'GET', '审计日志详情', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_AUDIT_EXPORT', '/api/admin/sys/audit-logs/export', 'POST', '导出审计日志', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_AUDIT_LIST', '/api/admin/sys/audit-logs', 'GET', '审计日志列表', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CAL_GET', '/api/admin/sys/calendar', 'GET', '查询工作日', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CAL_IMPORT', '/api/admin/sys/calendar/import', 'POST', '导入节假日', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CAL_INIT', '/api/admin/sys/calendar/init', 'POST', '初始化年份', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CAL_PUBLIC', '/api/sys/calendar', 'GET', '公共日历查询', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CAL_SET', '/api/admin/sys/calendar/*', 'PUT', '设置工作日', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CFG_LIST', '/api/admin/sys/configs', 'GET', '配置列表', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_CFG_UPDATE', '/api/admin/sys/configs/*', 'PUT', '更新配置', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_DICT_CREATE', '/api/admin/sys/dicts', 'POST', '创建字典项', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_DICT_DELETE', '/api/admin/sys/dicts/*', 'DELETE', '删除字典项', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_DICT_ITEMS', '/api/sys/dicts/*/items', 'GET', '字典项列表(公共)', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_DICT_LIST', '/api/sys/dicts', 'GET', '字典类型列表(公共)', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_DICT_STATUS', '/api/admin/sys/dicts/*/status', 'PUT', '启禁字典项', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_DICT_UPDATE', '/api/admin/sys/dicts/*', 'PUT', '更新字典项', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_FILE_DELETE', '/api/files/*', 'DELETE', '删除文件', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_FILE_DOWNLOAD', '/api/files/*/download', 'GET', '下载文件', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_FILE_LIST', '/api/files', 'GET', '业务文件列表', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_FILE_UPLOAD', '/api/files/upload', 'POST', '上传文件', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_JOB_LIST', '/api/admin/sys/jobs', 'GET', '任务列表', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_JOB_LOGS', '/api/admin/sys/jobs/*/logs', 'GET', '任务执行日志', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_JOB_PAUSE', '/api/admin/sys/jobs/*/pause', 'PUT', '暂停任务', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_JOB_RESUME', '/api/admin/sys/jobs/*/resume', 'PUT', '恢复任务', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_JOB_TRIGGER', '/api/admin/sys/jobs/*/trigger', 'POST', '手动触发任务', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_NOTIFY_COUNT', '/api/notifications/unread-count', 'GET', '未读通知数', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_NOTIFY_DETAIL', '/api/notifications/*', 'GET', '通知详情', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_NOTIFY_LIST', '/api/notifications', 'GET', '通知列表', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_NOTIFY_READ', '/api/notifications/*/read', 'PUT', '标记已读', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_NOTIFY_READ_ALL', '/api/notifications/read-all', 'PUT', '全部已读', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_SQL_EXEC', '/api/admin/sql-probe/execute', 'POST', 'SQL 执行探查', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('G_SQL_HIST', '/api/admin/sql-probe/history', 'GET', 'SQL 执行历史', NULL, 0, 0, '0', NULL, 0, 'GOV', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NC_CREATE', '/api/admin/workflow/node-candidates', 'POST', '新增候选人配置', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NC_GET', '/api/admin/workflow/node-candidates/item/*', 'GET', '候选人配置详情', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NC_LIST', '/api/admin/workflow/node-candidates', 'GET', '候选人配置列表', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NC_UPDATE', '/api/admin/workflow/node-candidates/*', 'PUT', '更新候选人配置', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NF_CREATE', '/api/admin/workflow/node-forms', 'POST', '新增节点表单', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NF_GET', '/api/admin/workflow/node-forms/item/*', 'GET', '节点表单详情', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NF_LIST', '/api/admin/workflow/node-forms', 'GET', '节点表单列表', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_NF_UPDATE', '/api/admin/workflow/node-forms/*', 'PUT', '更新节点表单', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_PROC_DEFS', '/api/admin/workflow/process-definitions', 'GET', '流程定义列表', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_PROC_DETAIL', '/api/workflow/processes/*', 'GET', '流程实例详情', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_PROC_DIAGRAM', '/api/workflow/processes/*/diagram', 'GET', '流程进度图', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_PROC_HISTORY', '/api/workflow/processes/*/history', 'GET', '流程历史', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_PROC_MAP', '/api/workflow/process-map', 'GET', '流程映射查询', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_PROC_NODES', '/api/workflow/processes/*/nodes', 'GET', '流程节点结构', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_APPROVE', '/api/workflow/tasks/*/approve', 'POST', '审批通过', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_CLAIM', '/api/workflow/tasks/*/claim', 'POST', '签收任务', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_DETAIL', '/api/workflow/tasks/*', 'GET', '任务详情', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_DONE', '/api/workflow/tasks/done', 'GET', '已办任务列表', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_REJECT', '/api/workflow/tasks/*/reject', 'POST', '驳回任务', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_TODO', '/api/workflow/tasks', 'GET', '待办任务列表', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TASK_TRANSFER', '/api/workflow/tasks/*/transfer', 'POST', '转交任务', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TR_CREATE', '/api/admin/workflow/timeout-rules', 'POST', '新增超时规则', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TR_GET', '/api/admin/workflow/timeout-rules/item/*', 'GET', '超时规则详情', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TR_LIST', '/api/admin/workflow/timeout-rules', 'GET', '超时规则列表', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10'),
	('W_TR_UPDATE', '/api/admin/workflow/timeout-rules/*', 'PUT', '更新超时规则', NULL, 0, 0, '0', NULL, 0, 'WF', '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1 aligned 2026-04-10');

-- 正在导出表  onepl.pt_role 的数据：~13 rows (大约)
DELETE FROM `pt_role`;
INSERT INTO `pt_role` (`ROLE_ID`, `ROLE_CODE`, `ROLE_CHNAME`, `RECORD_STATUS`, `SYS_CODE`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`) VALUES
	('R_77EBD269', 'R_TESTZ', 'retest-v2', 1, 'PLATFORM', '2026-04-10 12:20:00', NULL, '2026-04-10 12:20:01', NULL, 'upd'),
	('R_ADMIN', 'SYS_ADMIN', '系统管理员', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 超级管理员，运维与权限管理'),
	('R_BACK_FINANCE', 'BACK_FINAN', '中后台员工(资财)', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 财务会计部等后台支持'),
	('R_BACK_TECH', 'BACK_TECH', '中后台员工(科技)', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 信息技术部'),
	('R_BRANCH_MGR', 'BRANCH_HEA', '经营机构负责人', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 支行/二级分行负责人'),
	('R_CORP_DEPT', 'CORP_DEPT', '公司部人员', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 分行公司业务管理部门'),
	('R_CREDIT_APPROVER', 'CREDIT_APP', '授信批复人员', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 授信批复岗'),
	('R_CREDIT_REVIEWER', 'CREDIT_REV', '授信审查人员', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 授信审查岗'),
	('R_PRESIDENT', 'BRANCH_PRE', '分行行长', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 分行最高管理者'),
	('R_RETAIL_DEPT', 'RETAIL_DEP', '零售部人员', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 分行零售业务管理部门'),
	('R_RM', 'CUST_MANAG', '客户经理', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 经营机构一线营销人员'),
	('R_SUPPORT_SEC', 'SUPPORT_SE', '中场支持部门秘书', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 中场支持部门秘书岗'),
	('R_SUPPORT_STAFF', 'SUPPORT_ST', '中场支持部门人员', 0, 'PLATFORM', '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', 'seed', 'V1 seed - 中台部门员工');

-- 正在导出表  onepl.pt_role_biz_scope 的数据：~69 rows (大约)
DELETE FROM `pt_role_biz_scope`;
INSERT INTO `pt_role_biz_scope` (`ID`, `ROLE_ID`, `BIZ_TYPE`, `DATA_SCOPE`, `RECORD_STATUS`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`) VALUES
	('d79564de349011f191754c496c37265b', 'R_BACK_FINANCE', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d795651b349011f191754c496c37265b', 'R_BRANCH_MGR', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d7956528349011f191754c496c37265b', 'R_CORP_DEPT', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d7956536349011f191754c496c37265b', 'R_CREDIT_APPROVER', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d7956542349011f191754c496c37265b', 'R_CREDIT_REVIEWER', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d795654e349011f191754c496c37265b', 'R_PRESIDENT', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d795655b349011f191754c496c37265b', 'R_RETAIL_DEPT', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d7956566349011f191754c496c37265b', 'R_RM', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d7956574349011f191754c496c37265b', 'R_SUPPORT_SEC', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('d795657f349011f191754c496c37265b', 'R_SUPPORT_STAFF', 'SYS_CONFIG', 'SELF', 0, '2026-04-10 11:53:32', 'seed', '2026-04-10 11:53:32', NULL, 'auto-fill for current-user/check-permission endpoints'),
	('da68abe1348b11f191754c496c37265b', 'R_ADMIN', 'ORG', 'ALL', 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:17:49', NULL, 'v1'),
	('S_ADMIN_ADDRBOOK', 'R_ADMIN', 'ADDRBOOK', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_CLAIM', 'R_ADMIN', 'CLAIM', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_CUSTOMER', 'R_ADMIN', 'CUSTOMER', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_CUSTOMER_POOL', 'R_ADMIN', 'CUSTOMER_POOL', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_DOC', 'R_ADMIN', 'DOC', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_LEAD', 'R_ADMIN', 'LEAD', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_LOAN', 'R_ADMIN', 'LOAN', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_NAV', 'R_ADMIN', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_PERF_CONFIG', 'R_ADMIN', 'PERF_CONFIG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_PRODUCT', 'R_ADMIN', 'PRODUCT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_REPORT', 'R_ADMIN', 'REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_SUPPORT', 'R_ADMIN', 'SUPPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_SUPPORT_DEPT', 'R_ADMIN', 'SUPPORT_DEPT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_SYS_CONFIG', 'R_ADMIN', 'SYS_CONFIG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_TAG', 'R_ADMIN', 'TAG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_TOUCH_REPORT', 'R_ADMIN', 'TOUCH_REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_ADMIN_TOUCH_TASK', 'R_ADMIN', 'TOUCH_TASK', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BF_NAV', 'R_BACK_FINANCE', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BF_PERF_CONFIG', 'R_BACK_FINANCE', 'PERF_CONFIG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BF_REPORT', 'R_BACK_FINANCE', 'REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BM_ADDRBOOK', 'R_BRANCH_MGR', 'ADDRBOOK', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', NULL, 'V1 seed'),
	('S_BM_CUSTOMER', 'R_BRANCH_MGR', 'CUSTOMER', 'ORG_SUBTREE', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BM_DOC', 'R_BRANCH_MGR', 'DOC', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', NULL, 'V1 seed'),
	('S_BM_LEAD', 'R_BRANCH_MGR', 'LEAD', 'ORG_SUBTREE', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BM_NAV', 'R_BRANCH_MGR', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BM_PRODUCT', 'R_BRANCH_MGR', 'PRODUCT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', NULL, 'V1 seed'),
	('S_BM_REPORT', 'R_BRANCH_MGR', 'REPORT', 'ORG_SUBTREE', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BM_SUPPORT', 'R_BRANCH_MGR', 'SUPPORT', 'ORG_SUBTREE', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BM_TOUCH_REPORT', 'R_BRANCH_MGR', 'TOUCH_REPORT', 'ORG_SUBTREE', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BRANCH_PRE_TAG', 'R_PRESIDENT', 'TAG', 'ALL', 0, NULL, NULL, NULL, NULL, NULL),
	('S_BT_DOC', 'R_BACK_TECH', 'DOC', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BT_NAV', 'R_BACK_TECH', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_BT_SYS_CONFIG', 'R_BACK_TECH', 'SYS_CONFIG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CAP_LOAN', 'R_CREDIT_APPROVER', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CAP_NAV', 'R_CREDIT_APPROVER', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_CUSTOMER', 'R_CORP_DEPT', 'CUSTOMER', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_LEAD', 'R_CORP_DEPT', 'LEAD', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_LOAN', 'R_CORP_DEPT', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_NAV', 'R_CORP_DEPT', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_REPORT', 'R_CORP_DEPT', 'REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_TAG', 'R_CORP_DEPT', 'TAG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CD_TOUCH_REPORT', 'R_CORP_DEPT', 'TOUCH_REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CRV_LOAN', 'R_CREDIT_REVIEWER', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_CRV_NAV', 'R_CREDIT_REVIEWER', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_PR_NAV', 'R_PRESIDENT', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_PR_REPORT', 'R_PRESIDENT', 'REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_CUSTOMER', 'R_RETAIL_DEPT', 'CUSTOMER', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_LEAD', 'R_RETAIL_DEPT', 'LEAD', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_LOAN', 'R_RETAIL_DEPT', 'LOAN', 'WORKFLOW_PARTICIPANT', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_NAV', 'R_RETAIL_DEPT', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_REPORT', 'R_RETAIL_DEPT', 'REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_TAG', 'R_RETAIL_DEPT', 'TAG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RD_TOUCH_REPORT', 'R_RETAIL_DEPT', 'TOUCH_REPORT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_ADDRBOOK', 'R_RM', 'ADDRBOOK', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_CLAIM', 'R_RM', 'CLAIM', 'ORG', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_CUSTOMER_POOL', 'R_RM', 'CUSTOMER_POOL', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_DOC', 'R_RM', 'DOC', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_LEAD', 'R_RM', 'LEAD', 'SELF_CREATED', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_LOAN', 'R_RM', 'LOAN', 'SELF_CREATED', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_NAV', 'R_RM', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_PRODUCT', 'R_RM', 'PRODUCT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_REPORT', 'R_RM', 'REPORT', 'SELF', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_SUPPORT', 'R_RM', 'SUPPORT', 'SELF_CREATED', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_TAG', 'R_RM', 'TAG', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_RM_TOUCH_TASK', 'R_RM', 'TOUCH_TASK', 'SELF_ASSIGNED', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_SF_NAV', 'R_SUPPORT_STAFF', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_SF_SUPPORT_DEPT', 'R_SUPPORT_STAFF', 'SUPPORT_DEPT', 'SELF_ASSIGNED', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_SS_NAV', 'R_SUPPORT_SEC', 'NAV', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1'),
	('S_SS_PRODUCT', 'R_SUPPORT_SEC', 'PRODUCT', 'ALL', 0, '2026-04-03 22:43:46', 'seed', '2026-04-03 22:43:46', NULL, 'V1 seed'),
	('S_SS_SUPPORT_DEPT', 'R_SUPPORT_SEC', 'SUPPORT_DEPT', 'ORG', 0, '2026-04-03 22:43:46', 'seed', '2026-04-10 11:17:49', 'seed', 'v1');

-- 正在导出表  onepl.pt_role_resource 的数据：~556 rows (大约)
DELETE FROM `pt_role_resource`;
INSERT INTO `pt_role_resource` (`ID`, `ROLE_ID`, `RESOURCE_ID`, `SYS_CODE`, `CREATE_TIME`) VALUES
	('da66408c348b11f191754c496c37265b', 'R_ADMIN', 'A_BZ_DELETE', 'AUTH', '2026-04-10 11:17:49'),
	('da6647a9348b11f191754c496c37265b', 'R_ADMIN', 'A_BZ_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da664882348b11f191754c496c37265b', 'R_ADMIN', 'A_BZ_MATRIX', 'AUTH', '2026-04-10 11:17:49'),
	('da66491a348b11f191754c496c37265b', 'R_ADMIN', 'A_BZ_SAVE', 'AUTH', '2026-04-10 11:17:49'),
	('da6649a7348b11f191754c496c37265b', 'R_ADMIN', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da664a37348b11f191754c496c37265b', 'R_ADMIN', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da664cd1348b11f191754c496c37265b', 'R_ADMIN', 'A_LOGIN', 'AUTH', '2026-04-10 11:17:49'),
	('da664dbf348b11f191754c496c37265b', 'R_ADMIN', 'A_LOGOUT', 'AUTH', '2026-04-10 11:17:49'),
	('da664e5a348b11f191754c496c37265b', 'R_ADMIN', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da664edc348b11f191754c496c37265b', 'R_ADMIN', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da664f56348b11f191754c496c37265b', 'R_ADMIN', 'A_ORG_USERS', 'AUTH', '2026-04-10 11:17:49'),
	('da664fd2348b11f191754c496c37265b', 'R_ADMIN', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da665042348b11f191754c496c37265b', 'R_ADMIN', 'A_RES_CREATE', 'AUTH', '2026-04-10 11:17:49'),
	('da6650b7348b11f191754c496c37265b', 'R_ADMIN', 'A_RES_DELETE', 'AUTH', '2026-04-10 11:17:49'),
	('da66513b348b11f191754c496c37265b', 'R_ADMIN', 'A_RES_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6651ba348b11f191754c496c37265b', 'R_ADMIN', 'A_RES_UPDATE', 'AUTH', '2026-04-10 11:17:49'),
	('da66522d348b11f191754c496c37265b', 'R_ADMIN', 'A_ROLE_CREATE', 'AUTH', '2026-04-10 11:17:49'),
	('da6652aa348b11f191754c496c37265b', 'R_ADMIN', 'A_ROLE_DELETE', 'AUTH', '2026-04-10 11:17:49'),
	('da66531f348b11f191754c496c37265b', 'R_ADMIN', 'A_ROLE_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da665390348b11f191754c496c37265b', 'R_ADMIN', 'A_ROLE_UPDATE', 'AUTH', '2026-04-10 11:17:49'),
	('da665402348b11f191754c496c37265b', 'R_ADMIN', 'A_ROLE_USERS', 'AUTH', '2026-04-10 11:17:49'),
	('da665477348b11f191754c496c37265b', 'R_ADMIN', 'A_RR_BIND', 'AUTH', '2026-04-10 11:17:49'),
	('da6654e9348b11f191754c496c37265b', 'R_ADMIN', 'A_RR_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da66555a348b11f191754c496c37265b', 'R_ADMIN', 'A_RR_REPLACE', 'AUTH', '2026-04-10 11:17:49'),
	('da6655cc348b11f191754c496c37265b', 'R_ADMIN', 'A_UR_BIND', 'AUTH', '2026-04-10 11:17:49'),
	('da66563d348b11f191754c496c37265b', 'R_ADMIN', 'A_UR_DEL', 'AUTH', '2026-04-10 11:17:49'),
	('da6656af348b11f191754c496c37265b', 'R_ADMIN', 'A_UR_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da66571f348b11f191754c496c37265b', 'R_ADMIN', 'G_AUDIT_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da665794348b11f191754c496c37265b', 'R_ADMIN', 'G_AUDIT_EXPORT', 'GOV', '2026-04-10 11:17:49'),
	('da66580a348b11f191754c496c37265b', 'R_ADMIN', 'G_AUDIT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da665881348b11f191754c496c37265b', 'R_ADMIN', 'G_CAL_GET', 'GOV', '2026-04-10 11:17:49'),
	('da6658f3348b11f191754c496c37265b', 'R_ADMIN', 'G_CAL_IMPORT', 'GOV', '2026-04-10 11:17:49'),
	('da665963348b11f191754c496c37265b', 'R_ADMIN', 'G_CAL_INIT', 'GOV', '2026-04-10 11:17:49'),
	('da6659e1348b11f191754c496c37265b', 'R_ADMIN', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da665a53348b11f191754c496c37265b', 'R_ADMIN', 'G_CAL_SET', 'GOV', '2026-04-10 11:17:49'),
	('da665ac6348b11f191754c496c37265b', 'R_ADMIN', 'G_CFG_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da665b3a348b11f191754c496c37265b', 'R_ADMIN', 'G_CFG_UPDATE', 'GOV', '2026-04-10 11:17:49'),
	('da665bae348b11f191754c496c37265b', 'R_ADMIN', 'G_DICT_CREATE', 'GOV', '2026-04-10 11:17:49'),
	('da665c20348b11f191754c496c37265b', 'R_ADMIN', 'G_DICT_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da665c94348b11f191754c496c37265b', 'R_ADMIN', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da665d06348b11f191754c496c37265b', 'R_ADMIN', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da665d7b348b11f191754c496c37265b', 'R_ADMIN', 'G_DICT_STATUS', 'GOV', '2026-04-10 11:17:49'),
	('da665deb348b11f191754c496c37265b', 'R_ADMIN', 'G_DICT_UPDATE', 'GOV', '2026-04-10 11:17:49'),
	('da665e5d348b11f191754c496c37265b', 'R_ADMIN', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da665ed0348b11f191754c496c37265b', 'R_ADMIN', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da665f49348b11f191754c496c37265b', 'R_ADMIN', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da665fb7348b11f191754c496c37265b', 'R_ADMIN', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da666029348b11f191754c496c37265b', 'R_ADMIN', 'G_JOB_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da666098348b11f191754c496c37265b', 'R_ADMIN', 'G_JOB_LOGS', 'GOV', '2026-04-10 11:17:49'),
	('da66614a348b11f191754c496c37265b', 'R_ADMIN', 'G_JOB_PAUSE', 'GOV', '2026-04-10 11:17:49'),
	('da6661c0348b11f191754c496c37265b', 'R_ADMIN', 'G_JOB_RESUME', 'GOV', '2026-04-10 11:17:49'),
	('da666235348b11f191754c496c37265b', 'R_ADMIN', 'G_JOB_TRIGGER', 'GOV', '2026-04-10 11:17:49'),
	('da6662ae348b11f191754c496c37265b', 'R_ADMIN', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da666321348b11f191754c496c37265b', 'R_ADMIN', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da666393348b11f191754c496c37265b', 'R_ADMIN', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da666406348b11f191754c496c37265b', 'R_ADMIN', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da66647c348b11f191754c496c37265b', 'R_ADMIN', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da6664f0348b11f191754c496c37265b', 'R_ADMIN', 'G_SQL_EXEC', 'GOV', '2026-04-10 11:17:49'),
	('da666564348b11f191754c496c37265b', 'R_ADMIN', 'G_SQL_HIST', 'GOV', '2026-04-10 11:17:49'),
	('da667bec348b11f191754c496c37265b', 'R_ADMIN', 'W_NC_CREATE', 'WF', '2026-04-10 11:17:49'),
	('da667d1d348b11f191754c496c37265b', 'R_ADMIN', 'W_NC_GET', 'WF', '2026-04-10 11:17:49'),
	('da667e25348b11f191754c496c37265b', 'R_ADMIN', 'W_NC_LIST', 'WF', '2026-04-10 11:17:49'),
	('da667f1d348b11f191754c496c37265b', 'R_ADMIN', 'W_NC_UPDATE', 'WF', '2026-04-10 11:17:49'),
	('da66800c348b11f191754c496c37265b', 'R_ADMIN', 'W_NF_CREATE', 'WF', '2026-04-10 11:17:49'),
	('da6680fa348b11f191754c496c37265b', 'R_ADMIN', 'W_NF_GET', 'WF', '2026-04-10 11:17:49'),
	('da668201348b11f191754c496c37265b', 'R_ADMIN', 'W_NF_LIST', 'WF', '2026-04-10 11:17:49'),
	('da6682ee348b11f191754c496c37265b', 'R_ADMIN', 'W_NF_UPDATE', 'WF', '2026-04-10 11:17:49'),
	('da6683d5348b11f191754c496c37265b', 'R_ADMIN', 'W_PROC_DEFS', 'WF', '2026-04-10 11:17:49'),
	('da6684b9348b11f191754c496c37265b', 'R_ADMIN', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6685d0348b11f191754c496c37265b', 'R_ADMIN', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da6686bb348b11f191754c496c37265b', 'R_ADMIN', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6689a4348b11f191754c496c37265b', 'R_ADMIN', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da668aa0348b11f191754c496c37265b', 'R_ADMIN', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da668b96348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da668c88348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da668d7b348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da668e63348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da66900a348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da66910d348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da6691fe348b11f191754c496c37265b', 'R_ADMIN', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da6692d7348b11f191754c496c37265b', 'R_ADMIN', 'W_TR_CREATE', 'WF', '2026-04-10 11:17:49'),
	('da6693b7348b11f191754c496c37265b', 'R_ADMIN', 'W_TR_GET', 'WF', '2026-04-10 11:17:49'),
	('da66948b348b11f191754c496c37265b', 'R_ADMIN', 'W_TR_LIST', 'WF', '2026-04-10 11:17:49'),
	('da669562348b11f191754c496c37265b', 'R_ADMIN', 'W_TR_UPDATE', 'WF', '2026-04-10 11:17:49'),
	('da66a7f6348b11f191754c496c37265b', 'R_BACK_TECH', 'A_BZ_DELETE', 'AUTH', '2026-04-10 11:17:49'),
	('da66a98a348b11f191754c496c37265b', 'R_BACK_TECH', 'A_BZ_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da66aa31348b11f191754c496c37265b', 'R_BACK_TECH', 'A_BZ_MATRIX', 'AUTH', '2026-04-10 11:17:49'),
	('da66aac9348b11f191754c496c37265b', 'R_BACK_TECH', 'A_BZ_SAVE', 'AUTH', '2026-04-10 11:17:49'),
	('da66ab45348b11f191754c496c37265b', 'R_BACK_TECH', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da66abbd348b11f191754c496c37265b', 'R_BACK_TECH', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da66ac2e348b11f191754c496c37265b', 'R_BACK_TECH', 'A_LOGIN', 'AUTH', '2026-04-10 11:17:49'),
	('da66ac9e348b11f191754c496c37265b', 'R_BACK_TECH', 'A_LOGOUT', 'AUTH', '2026-04-10 11:17:49'),
	('da66ad21348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da66c7b1348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da66c87c348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ORG_USERS', 'AUTH', '2026-04-10 11:17:49'),
	('da66c903348b11f191754c496c37265b', 'R_BACK_TECH', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da66c97a348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RES_CREATE', 'AUTH', '2026-04-10 11:17:49'),
	('da66c9f2348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RES_DELETE', 'AUTH', '2026-04-10 11:17:49'),
	('da66ca63348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RES_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da66cad4348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RES_UPDATE', 'AUTH', '2026-04-10 11:17:49'),
	('da66cb4e348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ROLE_CREATE', 'AUTH', '2026-04-10 11:17:49'),
	('da66cbbd348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ROLE_DELETE', 'AUTH', '2026-04-10 11:17:49'),
	('da66cc30348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ROLE_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da66cca1348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ROLE_UPDATE', 'AUTH', '2026-04-10 11:17:49'),
	('da66cd0e348b11f191754c496c37265b', 'R_BACK_TECH', 'A_ROLE_USERS', 'AUTH', '2026-04-10 11:17:49'),
	('da66cd97348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RR_BIND', 'AUTH', '2026-04-10 11:17:49'),
	('da66ce09348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RR_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da66ce7e348b11f191754c496c37265b', 'R_BACK_TECH', 'A_RR_REPLACE', 'AUTH', '2026-04-10 11:17:49'),
	('da66ceeb348b11f191754c496c37265b', 'R_BACK_TECH', 'A_UR_BIND', 'AUTH', '2026-04-10 11:17:49'),
	('da66cf57348b11f191754c496c37265b', 'R_BACK_TECH', 'A_UR_DEL', 'AUTH', '2026-04-10 11:17:49'),
	('da66cfc2348b11f191754c496c37265b', 'R_BACK_TECH', 'A_UR_LIST', 'AUTH', '2026-04-10 11:17:49'),
	('da66d035348b11f191754c496c37265b', 'R_BACK_TECH', 'G_AUDIT_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da66d0a9348b11f191754c496c37265b', 'R_BACK_TECH', 'G_AUDIT_EXPORT', 'GOV', '2026-04-10 11:17:49'),
	('da66d1c9348b11f191754c496c37265b', 'R_BACK_TECH', 'G_AUDIT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da66d240348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CAL_GET', 'GOV', '2026-04-10 11:17:49'),
	('da66d2c3348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CAL_IMPORT', 'GOV', '2026-04-10 11:17:49'),
	('da66d337348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CAL_INIT', 'GOV', '2026-04-10 11:17:49'),
	('da66d3a9348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da66d419348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CAL_SET', 'GOV', '2026-04-10 11:17:49'),
	('da66d48a348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CFG_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da66d4f6348b11f191754c496c37265b', 'R_BACK_TECH', 'G_CFG_UPDATE', 'GOV', '2026-04-10 11:17:49'),
	('da66d564348b11f191754c496c37265b', 'R_BACK_TECH', 'G_DICT_CREATE', 'GOV', '2026-04-10 11:17:49'),
	('da66d5d2348b11f191754c496c37265b', 'R_BACK_TECH', 'G_DICT_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da66d64b348b11f191754c496c37265b', 'R_BACK_TECH', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da66d6ba348b11f191754c496c37265b', 'R_BACK_TECH', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da66d728348b11f191754c496c37265b', 'R_BACK_TECH', 'G_DICT_STATUS', 'GOV', '2026-04-10 11:17:49'),
	('da66d798348b11f191754c496c37265b', 'R_BACK_TECH', 'G_DICT_UPDATE', 'GOV', '2026-04-10 11:17:49'),
	('da66d80b348b11f191754c496c37265b', 'R_BACK_TECH', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da66d878348b11f191754c496c37265b', 'R_BACK_TECH', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da66d8eb348b11f191754c496c37265b', 'R_BACK_TECH', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da66d962348b11f191754c496c37265b', 'R_BACK_TECH', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da66d9d5348b11f191754c496c37265b', 'R_BACK_TECH', 'G_JOB_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da66da44348b11f191754c496c37265b', 'R_BACK_TECH', 'G_JOB_LOGS', 'GOV', '2026-04-10 11:17:49'),
	('da66ec72348b11f191754c496c37265b', 'R_BACK_TECH', 'G_JOB_PAUSE', 'GOV', '2026-04-10 11:17:49'),
	('da66ed28348b11f191754c496c37265b', 'R_BACK_TECH', 'G_JOB_RESUME', 'GOV', '2026-04-10 11:17:49'),
	('da66ed9b348b11f191754c496c37265b', 'R_BACK_TECH', 'G_JOB_TRIGGER', 'GOV', '2026-04-10 11:17:49'),
	('da66ee05348b11f191754c496c37265b', 'R_BACK_TECH', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da66ee78348b11f191754c496c37265b', 'R_BACK_TECH', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da66eee3348b11f191754c496c37265b', 'R_BACK_TECH', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da66ef52348b11f191754c496c37265b', 'R_BACK_TECH', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da66efb8348b11f191754c496c37265b', 'R_BACK_TECH', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da66f021348b11f191754c496c37265b', 'R_BACK_TECH', 'G_SQL_EXEC', 'GOV', '2026-04-10 11:17:49'),
	('da66f091348b11f191754c496c37265b', 'R_BACK_TECH', 'G_SQL_HIST', 'GOV', '2026-04-10 11:17:49'),
	('da66fc3f348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NC_CREATE', 'WF', '2026-04-10 11:17:49'),
	('da66fcc9348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NC_GET', 'WF', '2026-04-10 11:17:49'),
	('da66fd4c348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NC_LIST', 'WF', '2026-04-10 11:17:49'),
	('da66fdd2348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NC_UPDATE', 'WF', '2026-04-10 11:17:49'),
	('da66fe42348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NF_CREATE', 'WF', '2026-04-10 11:17:49'),
	('da66feb0348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NF_GET', 'WF', '2026-04-10 11:17:49'),
	('da66ff1f348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NF_LIST', 'WF', '2026-04-10 11:17:49'),
	('da66ff8d348b11f191754c496c37265b', 'R_BACK_TECH', 'W_NF_UPDATE', 'WF', '2026-04-10 11:17:49'),
	('da66fffa348b11f191754c496c37265b', 'R_BACK_TECH', 'W_PROC_DEFS', 'WF', '2026-04-10 11:17:49'),
	('da670fc2348b11f191754c496c37265b', 'R_BACK_TECH', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da67106f348b11f191754c496c37265b', 'R_BACK_TECH', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da6710e6348b11f191754c496c37265b', 'R_BACK_TECH', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da67115e348b11f191754c496c37265b', 'R_BACK_TECH', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da6711d2348b11f191754c496c37265b', 'R_BACK_TECH', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da671240348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da6712b1348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da671322348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da671392348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da671400348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da67146a348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da6714dc348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da671548348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TR_CREATE', 'WF', '2026-04-10 11:17:49'),
	('da672995348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TR_GET', 'WF', '2026-04-10 11:17:49'),
	('da672a92348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TR_LIST', 'WF', '2026-04-10 11:17:49'),
	('da672b05348b11f191754c496c37265b', 'R_BACK_TECH', 'W_TR_UPDATE', 'WF', '2026-04-10 11:17:49'),
	('da674945348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674a92348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674b10348b11f191754c496c37265b', 'R_RM', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674b7e348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674be2348b11f191754c496c37265b', 'R_PRESIDENT', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674c4b348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674cab348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674d07348b11f191754c496c37265b', 'R_CORP_DEPT', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674d63348b11f191754c496c37265b', 'R_BRANCH_MGR', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da674df5348b11f191754c496c37265b', 'R_BACK_FINANCE', 'A_CHECK_PERM', 'AUTH', '2026-04-10 11:17:49'),
	('da676905348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da6769aa348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677844348b11f191754c496c37265b', 'R_RM', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da6778e8348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677957348b11f191754c496c37265b', 'R_PRESIDENT', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da6779cf348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677aad348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677b1a348b11f191754c496c37265b', 'R_CORP_DEPT', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677b83348b11f191754c496c37265b', 'R_BRANCH_MGR', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677beb348b11f191754c496c37265b', 'R_BACK_FINANCE', 'A_CURR_USER', 'AUTH', '2026-04-10 11:17:49'),
	('da677cc8348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da677d3f348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da677eae348b11f191754c496c37265b', 'R_RM', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da677f91348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678000348b11f191754c496c37265b', 'R_PRESIDENT', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6780f5348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6781cf348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da67823d348b11f191754c496c37265b', 'R_CORP_DEPT', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6782a3348b11f191754c496c37265b', 'R_BRANCH_MGR', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678306348b11f191754c496c37265b', 'R_BACK_FINANCE', 'A_ORG_SUBTREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6783c9348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678445348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da67853b348b11f191754c496c37265b', 'R_RM', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6785aa348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678618348b11f191754c496c37265b', 'R_PRESIDENT', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678685348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6786e7348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678747348b11f191754c496c37265b', 'R_CORP_DEPT', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da6787a8348b11f191754c496c37265b', 'R_BRANCH_MGR', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678807348b11f191754c496c37265b', 'R_BACK_FINANCE', 'A_ORG_TREE', 'AUTH', '2026-04-10 11:17:49'),
	('da678898348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678900348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678963348b11f191754c496c37265b', 'R_RM', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da6789c3348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678a23348b11f191754c496c37265b', 'R_PRESIDENT', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678a91348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678af5348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678b56348b11f191754c496c37265b', 'R_CORP_DEPT', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678bb8348b11f191754c496c37265b', 'R_BRANCH_MGR', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678c19348b11f191754c496c37265b', 'R_BACK_FINANCE', 'A_PERMS', 'AUTH', '2026-04-10 11:17:49'),
	('da678dd9348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da678e47348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da679014348b11f191754c496c37265b', 'R_RM', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da6790ac348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da679119348b11f191754c496c37265b', 'R_PRESIDENT', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da6791a7348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da67920f348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da67926b348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da6792c5348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da679323348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_CAL_PUBLIC', 'GOV', '2026-04-10 11:17:49'),
	('da67943a348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da6794a4348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da679503348b11f191754c496c37265b', 'R_RM', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da67955c348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da6795b5348b11f191754c496c37265b', 'R_PRESIDENT', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da67960f348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da679668348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da6796c5348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da67971f348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da679777348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_DICT_ITEMS', 'GOV', '2026-04-10 11:17:49'),
	('da6797e1348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679841348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67989b348b11f191754c496c37265b', 'R_RM', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679d0f348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679e00348b11f191754c496c37265b', 'R_PRESIDENT', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679e6f348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679ed6348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679f38348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679f9b348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da679ffc348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_DICT_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67a0c2348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a274348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a33f348b11f191754c496c37265b', 'R_RM', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a3a6348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a405348b11f191754c496c37265b', 'R_PRESIDENT', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a466348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a4c4348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a522348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a57f348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a5f1348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_FILE_DELETE', 'GOV', '2026-04-10 11:17:49'),
	('da67a69c348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a701348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a75c348b11f191754c496c37265b', 'R_RM', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a7b7348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a813348b11f191754c496c37265b', 'R_PRESIDENT', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a86c348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a8c8348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a92f348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67a989348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67abd4348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_FILE_DOWNLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67ad89348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67ae34348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67aeb7348b11f191754c496c37265b', 'R_RM', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67af41348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67afad348b11f191754c496c37265b', 'R_PRESIDENT', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67b009348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67b064348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67b0bf348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67b117348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67b170348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_FILE_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67b1e9348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b24e348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b2ab348b11f191754c496c37265b', 'R_RM', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b305348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b35f348b11f191754c496c37265b', 'R_PRESIDENT', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b3b9348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b412348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b475348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b517348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b57b348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_FILE_UPLOAD', 'GOV', '2026-04-10 11:17:49'),
	('da67b63a348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b69d348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b6f9348b11f191754c496c37265b', 'R_RM', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b754348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b7af348b11f191754c496c37265b', 'R_PRESIDENT', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b80b348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b867348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b8d2348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b931348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67b997348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_NOTIFY_COUNT', 'GOV', '2026-04-10 11:17:49'),
	('da67ba04348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67ba65348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67bac4348b11f191754c496c37265b', 'R_RM', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67bb20348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67bb79348b11f191754c496c37265b', 'R_PRESIDENT', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67bbd2348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67ce5f348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67cf1d348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67cf8c348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67cff8348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_NOTIFY_DETAIL', 'GOV', '2026-04-10 11:17:49'),
	('da67d090348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e542348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e5de348b11f191754c496c37265b', 'R_RM', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e641348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e698348b11f191754c496c37265b', 'R_PRESIDENT', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e7b7348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e81b348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e877348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e8cf348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e924348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_NOTIFY_LIST', 'GOV', '2026-04-10 11:17:49'),
	('da67e9b1348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ea0c348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ea63348b11f191754c496c37265b', 'R_RM', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67eabe348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67eb16348b11f191754c496c37265b', 'R_PRESIDENT', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67eb6b348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ebc1348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ec16348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ec6e348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ecc4348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_NOTIFY_READ', 'GOV', '2026-04-10 11:17:49'),
	('da67ed2d348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67ed94348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67edec348b11f191754c496c37265b', 'R_RM', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67ee43348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67ee9c348b11f191754c496c37265b', 'R_PRESIDENT', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67eef2348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67ef48348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67ef9e348b11f191754c496c37265b', 'R_CORP_DEPT', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67eff3348b11f191754c496c37265b', 'R_BRANCH_MGR', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67f04b348b11f191754c496c37265b', 'R_BACK_FINANCE', 'G_NOTIFY_READ_ALL', 'GOV', '2026-04-10 11:17:49'),
	('da67ff1c348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6801d5348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da680241348b11f191754c496c37265b', 'R_RM', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da68030d348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da680367348b11f191754c496c37265b', 'R_PRESIDENT', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6803c7348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da680423348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da680479348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6804cf348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da68052c348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_PROC_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da680599348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da6805f7348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da681f17348b11f191754c496c37265b', 'R_RM', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da681fbd348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da682020348b11f191754c496c37265b', 'R_PRESIDENT', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da68207e348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da6820dd348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da682137348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da68218f348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da6821e9348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_PROC_DIAGRAM', 'WF', '2026-04-10 11:17:49'),
	('da682284348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6822f6348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da682351348b11f191754c496c37265b', 'R_RM', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6823a8348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6823fc348b11f191754c496c37265b', 'R_PRESIDENT', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da682456348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6824ab348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6824ff348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da682559348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da6825b0348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_PROC_HISTORY', 'WF', '2026-04-10 11:17:49'),
	('da68261a348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da682677348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da6826e6348b11f191754c496c37265b', 'R_RM', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da68273f348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da6827ab348b11f191754c496c37265b', 'R_PRESIDENT', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da682804348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da68285b348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da6828f0348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da682947348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da6829a4348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_PROC_MAP', 'WF', '2026-04-10 11:17:49'),
	('da682a13348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682a71348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682ace348b11f191754c496c37265b', 'R_RM', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682b28348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682b7d348b11f191754c496c37265b', 'R_PRESIDENT', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682bd4348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682c29348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682c80348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da682cd7348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da683b92348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_PROC_NODES', 'WF', '2026-04-10 11:17:49'),
	('da683c6e348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683cd7348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683d31348b11f191754c496c37265b', 'R_RM', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683d8c348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683de4348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683e3d348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683e94348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683eed348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683f48348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da683fa4348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_APPROVE', 'WF', '2026-04-10 11:17:49'),
	('da68400b348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da684066348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da6840bc348b11f191754c496c37265b', 'R_RM', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da68414b348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da6841a4348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da6841fd348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da684252348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da6842a9348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da684300348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da684357348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_CLAIM', 'WF', '2026-04-10 11:17:49'),
	('da6843be348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da684419348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da684470348b11f191754c496c37265b', 'R_RM', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6844c5348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da68451b348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da684572348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6845c7348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da68461e348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da684676348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da6846d0348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_DETAIL', 'WF', '2026-04-10 11:17:49'),
	('da684738348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da68479b348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da6847f3348b11f191754c496c37265b', 'R_RM', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da68484a348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da6848a1348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da6848f9348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da68494f348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da6849a6348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da6849fb348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da684a53348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_DONE', 'WF', '2026-04-10 11:17:49'),
	('da684ac1348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684b1e348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684b7f348b11f191754c496c37265b', 'R_RM', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684bd8348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684c2f348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684c8a348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684ce0348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684d3a348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684d91348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684dea348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_REJECT', 'WF', '2026-04-10 11:17:49'),
	('da684e5b348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da684eb8348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da684f0f348b11f191754c496c37265b', 'R_RM', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da684f64348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da684fb9348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da68500e348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da685063348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da6850bf348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da685117348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da68516b348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_TODO', 'WF', '2026-04-10 11:17:49'),
	('da6851d9348b11f191754c496c37265b', 'R_SUPPORT_STAFF', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da685235348b11f191754c496c37265b', 'R_SUPPORT_SEC', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da68528c348b11f191754c496c37265b', 'R_RM', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da6852e1348b11f191754c496c37265b', 'R_RETAIL_DEPT', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da685339348b11f191754c496c37265b', 'R_PRESIDENT', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da685391348b11f191754c496c37265b', 'R_CREDIT_REVIEWER', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da6853e6348b11f191754c496c37265b', 'R_CREDIT_APPROVER', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da68543a348b11f191754c496c37265b', 'R_CORP_DEPT', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da685490348b11f191754c496c37265b', 'R_BRANCH_MGR', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49'),
	('da6854e7348b11f191754c496c37265b', 'R_BACK_FINANCE', 'W_TASK_TRANSFER', 'WF', '2026-04-10 11:17:49');

-- 正在导出表  onepl.pt_user 的数据：~3 rows (大约)
DELETE FROM `pt_user`;
INSERT INTO `pt_user` (`USER_ID`, `USERNAME`, `USERCHNNAME`, `PWD`, `EMAIL`, `ISEXPIRED`, `ISLOCKED`, `PASS_WRONG_COUNT`, `ISENABLED`, `CREATE_TIME`, `CREATE_AUTHOR`, `UPDATE_TIME`, `UPDATE_AUTHOR`, `REMARK`, `PWD_UPDATE_TIME`) VALUES
	('admin', 'admin', '系统管理员', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', 'admin@test.com', 0, 0, 0, 0, '2026-04-07 15:49:20', NULL, '2026-04-10 11:25:23', NULL, NULL, NULL),
	('E10002', 'rm_li', '李四(客户经理)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E20001', 'branch_wang', '王五(支行行长)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E30001', 'corp_zhao', '赵六(公司部)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E30002', 'retail_sun', '孙七(零售部)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E40001', 'finance_zhou', '周八(资财)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E40002', 'tech_wu', '吴九(科技)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E50001', 'sec_zheng', '郑十(中场秘书)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E50002', 'staff_qian', '钱十一(中场人员)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E60001', 'reviewer_chen', '陈十二(授信审查)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('E60002', 'approver_he', '何十三(授信批复)', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', NULL, 0, 0, 0, 0, '2026-04-10 11:17:49', 'seed', '2026-04-10 11:25:23', NULL, 'test-user', NULL),
	('user001', 'user001', '张三', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', 'zhangsan@test.com', 0, 0, 0, 0, '2026-04-07 15:49:20', NULL, '2026-04-10 11:25:23', NULL, NULL, NULL),
	('user002', 'user002', '李四', '$2b$10$16t1SpylVaWF2rgXoPsCVO1dmylMXQUAcVFkjSwiFwPg3xjsOmH7m', 'lisi@test.com', 0, 0, 0, 0, '2026-04-07 15:49:20', NULL, '2026-04-10 11:25:23', NULL, NULL, NULL);

-- 正在导出表  onepl.pt_user_role 的数据：~3 rows (大约)
DELETE FROM `pt_user_role`;
INSERT INTO `pt_user_role` (`USER_ID`, `ROLE_ID`, `DEFAULT_ASSIGN`, `INHERIT_ASSIGN`, `GROUP_ASSING`, `CREATE_TIME`) VALUES
	('admin', 'R_ADMIN', 1, 0, 0, '2026-04-07 19:20:37'),
	('E10002', 'R_RM', 0, 0, 0, '2026-04-10 11:17:49'),
	('E20001', 'R_BRANCH_MGR', 0, 0, 0, '2026-04-10 11:17:49'),
	('E30001', 'R_CORP_DEPT', 0, 0, 0, '2026-04-10 11:17:49'),
	('E30002', 'R_RETAIL_DEPT', 0, 0, 0, '2026-04-10 11:17:49'),
	('E40001', 'R_BACK_FINANCE', 0, 0, 0, '2026-04-10 11:17:49'),
	('E40002', 'R_BACK_TECH', 0, 0, 0, '2026-04-10 11:17:49'),
	('E50001', 'R_SUPPORT_SEC', 0, 0, 0, '2026-04-10 11:17:49'),
	('E50002', 'R_SUPPORT_STAFF', 0, 0, 0, '2026-04-10 11:17:49'),
	('E60001', 'R_CREDIT_REVIEWER', 0, 0, 0, '2026-04-10 11:17:49'),
	('E60002', 'R_CREDIT_APPROVER', 0, 0, 0, '2026-04-10 11:17:49'),
	('user001', 'R_RM', 1, 0, 0, '2026-04-07 19:20:37'),
	('user002', 'R_PRESIDENT', 1, 0, 0, '2026-04-07 19:20:37');

-- 正在导出表  onepl.report_saved_query 的数据：~0 rows (大约)
DELETE FROM `report_saved_query`;

-- 正在导出表  onepl.support_request 的数据：~0 rows (大约)
DELETE FROM `support_request`;

-- 正在导出表  onepl.sys_calendar_day 的数据：~13 rows (大约)
DELETE FROM `sys_calendar_day`;
INSERT INTO `sys_calendar_day` (`day`, `is_workday`, `remark`, `created_by`, `created_time`, `updated_by`, `updated_time`) VALUES
	('2026-01-01', 0, '元旦', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-01-02', 0, '元旦假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-01-03', 0, '元旦假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-01-04', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-10', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-11', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-17', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-18', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-24', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-25', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-01-31', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-01', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-07', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-08', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-14', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-15', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-21', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-22', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-02-28', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-01', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-07', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-08', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-14', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-15', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-21', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-22', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-28', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-29', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-03-31', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-01', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-04', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-05', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-11', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-12', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-18', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-19', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-25', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-26', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-04-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-01', 0, 'labor-day', 'seed', '2026-04-03 22:46:31', 'admin', '2026-04-10 12:20:09'),
	('2026-05-02', 0, '劳动节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-05-03', 0, '劳动节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-05-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-09', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-10', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-16', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-17', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-23', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-24', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-30', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-05-31', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-01', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-06', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-07', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-13', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-14', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-20', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-21', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-27', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-28', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-06-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-01', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-04', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-05', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-11', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-12', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-18', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-19', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-25', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-26', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-07-31', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-01', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-02', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-08', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-09', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-15', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-16', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-22', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-23', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-29', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-30', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-08-31', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-01', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-05', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-06', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-12', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-13', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-19', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-20', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-26', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-27', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-09-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-01', 0, '国庆节', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-02', 0, '国庆节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-03', 0, '国庆节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-04', 0, '国庆节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-05', 0, '国庆节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-06', 0, '国庆节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-07', 0, '国庆节假期', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('2026-10-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-10', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-11', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-17', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-18', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-24', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-25', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-10-31', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-01', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-05', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-06', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-07', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-08', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-12', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-13', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-14', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-15', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-19', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-20', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-21', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-22', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-26', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-27', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-28', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-29', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-11-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-01', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-02', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-03', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-04', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-05', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-06', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-07', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-08', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-09', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-10', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-11', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-12', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-13', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-14', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-15', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-16', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-17', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-18', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-19', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-20', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-21', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-22', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-23', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-24', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-25', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-26', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-27', 0, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-28', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-29', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-30', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08'),
	('2026-12-31', 1, NULL, 'admin', '2026-04-10 11:38:08', 'admin', '2026-04-10 11:38:08');

-- 正在导出表  onepl.sys_config_kv 的数据：~5 rows (大约)
DELETE FROM `sys_config_kv`;
INSERT INTO `sys_config_kv` (`id`, `config_key`, `config_value`, `value_type`, `status`, `remark`, `created_by`, `created_time`, `updated_by`, `updated_time`) VALUES
	('CFG_AUDIT_EXPORT_MAX_DAYS', 'AUDIT_EXPORT_MAX_DAYS', '31', 'NUMBER', 'ACTIVE', '审计导出最大天数', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('CFG_AUDIT_EXPORT_MAX_ROWS', 'AUDIT_EXPORT_MAX_ROWS', '200000', 'NUMBER', 'ACTIVE', '审计导出最大行数', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('CFG_SQL_PROBE_MAX_CONCURRENCY', 'SQL_PROBE_MAX_CONCURRENCY', '5', 'NUMBER', 'ACTIVE', 'SQL探查并发上限', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('CFG_SQL_PROBE_MAX_LIMIT', 'SQL_PROBE_MAX_LIMIT', '2000', 'NUMBER', 'ACTIVE', 'SQL探查LIMIT上限', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31'),
	('CFG_SQL_PROBE_WHITELIST_JSON', 'SQL_PROBE_WHITELIST_JSON', '{"schemas":[],"tables":[]}', 'JSON', 'ACTIVE', 'SQL探查白名单', 'seed', '2026-04-03 22:46:31', NULL, '2026-04-03 22:46:31');

-- 正在导出表  onepl.sys_control 的数据：~3 rows (大约)
DELETE FROM `sys_control`;
INSERT INTO `sys_control` (`id`, `scope_dim`, `latest_data_date`, `current_version`, `is_valid`, `created_time`, `updated_time`) VALUES
	('SC_INIT_CUST', 'CUST', '1970-01-01', NULL, 0, '2026-04-03 22:46:31', '2026-04-03 22:46:31'),
	('SC_INIT_EMP', 'EMP', '1970-01-01', NULL, 0, '2026-04-03 22:46:31', '2026-04-03 22:46:31'),
	('SC_INIT_ORG', 'ORG', '1970-01-01', NULL, 0, '2026-04-03 22:46:31', '2026-04-03 22:46:31');

-- 正在导出表  onepl.sys_dict 的数据：~109 rows (大约)
DELETE FROM `sys_dict`;
INSERT INTO `sys_dict` (`id`, `dict_type`, `dict_code`, `dict_label`, `dict_value`, `sort_order`, `status`, `remark`, `created_by`, `created_time`, `updated_by`, `updated_time`) VALUES
	('D_BK_CD', 'BIZ_KIND', 'NCD', '大额存单', 'NCD', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BK_DEP', 'BIZ_KIND', 'DEPOSIT', '存款', 'DEPOSIT', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BK_LOAN', 'BIZ_KIND', 'LOAN', '贷款', 'LOAN', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BK_MID', 'BIZ_KIND', 'INTERMEDIATE', '中间业务', 'INTERMEDIATE', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BT_ACCEPTANCE', 'BIZ_TYPE', 'ACCEPTANCE', '承兑汇票', 'ACCEPTANCE', 5, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BT_FIXED', 'BIZ_TYPE', 'FIXED_ASSET', '固定资产贷款', 'FIXED_ASSET', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BT_GUARANTEE', 'BIZ_TYPE', 'GUARANTEE', '保函', 'GUARANTEE', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BT_TRADE', 'BIZ_TYPE', 'TRADE_FINANCE', '贸易融资', 'TRADE_FINANCE', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_BT_WORKING_CAP', 'BIZ_TYPE', 'WORKING_CAPITAL', '流动资金贷款', 'WORKING_CAPITAL', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CF_DAY', 'PERF_CALC_FREQ', 'DAY', '日', 'DAY', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CF_MONTH', 'PERF_CALC_FREQ', 'MONTH', '月', 'MONTH', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CF_QUARTER', 'PERF_CALC_FREQ', 'QUARTER', '季', 'QUARTER', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CF_YEAR', 'PERF_CALC_FREQ', 'YEAR', '年', 'YEAR', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CLT_EXPR', 'PERF_CALC_LOGIC_TYPE', 'EXPR', '表达式', 'EXPR', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CLT_PROC', 'PERF_CALC_LOGIC_TYPE', 'PROC', '存储过程', 'PROC', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CLT_SQL', 'PERF_CALC_LOGIC_TYPE', 'SQL', 'SQL查询', 'SQL', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CLT_SUMMARY', 'PERF_CALC_LOGIC_TYPE', 'SUMMARY', '汇总', 'SUMMARY', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CM_AUTO', 'PERF_CALC_MODE', 'AUTO', '自动计算', 'AUTO', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CM_MANUAL', 'PERF_CALC_MODE', 'MANUAL', '手工导入', 'MANUAL', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CT_1', 'CUSTOMER_TYPE', 'CORP', '对公客户', 'CORP', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CT_2', 'CUSTOMER_TYPE', 'RETAIL', '零售客户', 'RETAIL', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_CVT_BOOL', 'CONFIG_VALUE_TYPE', 'BOOL', '布尔', 'BOOL', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_CVT_JSON', 'CONFIG_VALUE_TYPE', 'JSON', 'JSON', 'JSON', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_CVT_NUM', 'CONFIG_VALUE_TYPE', 'NUMBER', '数值', 'NUMBER', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_CVT_STR', 'CONFIG_VALUE_TYPE', 'STRING', '字符串', 'STRING', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_DC_GUIDE', 'DOC_CATEGORY', 'GUIDE', '操作指引', 'GUIDE', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_DC_POLICY', 'DOC_CATEGORY', 'POLICY', '制度文件', 'POLICY', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_DC_TEMPLATE', 'DOC_CATEGORY', 'TEMPLATE', '模板表单', 'TEMPLATE', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_DC_TRAIN', 'DOC_CATEGORY', 'TRAINING', '培训材料', 'TRAINING', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_EF5975FD', 'TEST_TYPE', 'X', 'updated', 'v2', 2, 'DISABLED', 'upd', 'admin', '2026-04-10 12:20:08', 'admin', '2026-04-10 12:20:09'),
	('D_ET_1', 'ENTERPRISE_TYPE', 'SOE', '国企', 'SOE', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ET_2', 'ENTERPRISE_TYPE', 'PRIVATE', '民营', 'PRIVATE', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ET_3', 'ENTERPRISE_TYPE', 'FOREIGN', '外资', 'FOREIGN', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ET_4', 'ENTERPRISE_TYPE', 'JV', '合资', 'JV', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ET_5', 'ENTERPRISE_TYPE', 'COLLECT', '集体企业', 'COLLECT', 5, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GRP_1', 'GROUP_TYPE', 'GROUP', '集团客户', 'GROUP', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GRP_2', 'GROUP_TYPE', 'SINGLE', '非集团客户', 'SINGLE', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GT_CREDIT', 'GUARANTEE_TYPE', 'CREDIT', '信用', 'CREDIT', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GT_GUARANTEE', 'GUARANTEE_TYPE', 'GUARANTEE', '保证', 'GUARANTEE', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GT_MIXED', 'GUARANTEE_TYPE', 'MIXED', '组合担保', 'MIXED', 5, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GT_MORTGAGE', 'GUARANTEE_TYPE', 'MORTGAGE', '抵押', 'MORTGAGE', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_GT_PLEDGE', 'GUARANTEE_TYPE', 'PLEDGE', '质押', 'PLEDGE', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_AGRI', 'INDUSTRY', 'AGRI', '农林牧渔业', 'AGRI', 10, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_EDU', 'INDUSTRY', 'EDU', '教育', 'EDU', 5, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_ENERGY', 'INDUSTRY', 'ENERGY', '能源', 'ENERGY', 9, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_FIN', 'INDUSTRY', 'FIN', '金融业', 'FIN', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_IT', 'INDUSTRY', 'IT', '信息技术', 'IT', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_MED', 'INDUSTRY', 'MED', '医疗卫生', 'MED', 6, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_MFG', 'INDUSTRY', 'MFG', '制造业', 'MFG', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_OTHER', 'INDUSTRY', 'OTHER', '其他', 'OTHER', 99, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_RE', 'INDUSTRY', 'RE', '房地产业', 'RE', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_RETAIL', 'INDUSTRY', 'RETAIL', '批发和零售业', 'RETAIL', 7, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_IND_TRANS', 'INDUSTRY', 'TRANS', '交通运输业', 'TRANS', 8, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_JRS_FAIL', 'JOB_RUN_STATUS', 'FAILED', '失败', 'FAILED', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_JRS_OK', 'JOB_RUN_STATUS', 'SUCCESS', '成功', 'SUCCESS', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_JRS_RUN', 'JOB_RUN_STATUS', 'RUNNING', '运行中', 'RUNNING', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_JS_ACT', 'JOB_STATUS', 'ACTIVE', '活跃', 'ACTIVE', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_JS_PAU', 'JOB_STATUS', 'PAUSED', '暂停', 'PAUSED', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_KC_M', 'PERF_KPI_CYCLE', 'MONTHLY', '月度', 'MONTHLY', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_KC_Q', 'PERF_KPI_CYCLE', 'QUARTERLY', '季度', 'QUARTERLY', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_LS_ASSIGN', 'LEAD_SOURCE', 'ASSIGNED', '上级分配', 'ASSIGNED', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_LS_IMPORT', 'LEAD_SOURCE', 'IMPORTED', '批量导入', 'IMPORTED', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_LS_REFER', 'LEAD_SOURCE', 'REFERRAL', '转介绍', 'REFERRAL', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_LS_SELF', 'LEAD_SOURCE', 'SELF_FOUND', '自行挖掘', 'SELF_FOUND', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_MD_CUST', 'PERF_BASE_DIM', 'CUST', '客户', 'CUST', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_MD_EMP', 'PERF_BASE_DIM', 'EMP', '人员', 'EMP', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_MD_ORG', 'PERF_BASE_DIM', 'ORG', '机构', 'ORG', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ML_1', 'PERF_METRIC_LEVEL', '1', '一级基础', '1', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ML_2', 'PERF_METRIC_LEVEL', '2', '二级派生', '2', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_ML_3', 'PERF_METRIC_LEVEL', '3', '三级复合', '3', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_NT_BIZ', 'NOTIFY_TYPE', 'BUSINESS', '业务通知', 'BUSINESS', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_NT_SYS', 'NOTIFY_TYPE', 'SYSTEM', '系统通知', 'SYSTEM', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_NT_WF', 'NOTIFY_TYPE', 'WORKFLOW', '流程通知', 'WORKFLOW', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PAD_ACCOUNT', 'PERF_ALLOC_DIM', 'ACCOUNT', '按台账分配', 'ACCOUNT', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PAD_RULE', 'PERF_ALLOC_DIM', 'RULE', '按规则分配', 'RULE', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PC_CORP', 'PRODUCT_CATEGORY', 'CORP_BANK', '公司银行', 'CORP_BANK', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_PC_FM', 'PRODUCT_CATEGORY', 'FIN_MARKET', '金融市场', 'FIN_MARKET', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_PC_RTL', 'PRODUCT_CATEGORY', 'RETAIL_BANK', '零售银行', 'RETAIL_BANK', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_PC_TRADE', 'PRODUCT_CATEGORY', 'TRADE_BANK', '交易银行', 'TRADE_BANK', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_PIT_IDX', 'PERF_IMPORT_TYPE', 'INDEX_RESULT', '指标结果', 'INDEX_RESULT', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PIT_KPI', 'PERF_IMPORT_TYPE', 'KPI_RESULT', 'KPI结果', 'KPI_RESULT', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PIT_TGT', 'PERF_IMPORT_TYPE', 'TARGET', '目标', 'TARGET', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PJT_ADJUST', 'PROJECT_TYPE', 'ADJUST', '调整项目', 'ADJUST', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PJT_NEW', 'PROJECT_TYPE', 'NEW', '新增项目', 'NEW', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PJT_RENEW', 'PROJECT_TYPE', 'RENEWAL', '续贷项目', 'RENEWAL', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_BH', 'POSITION', 'BRANCH_HEAD', '支行负责人', 'BRANCH_HEAD', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_CA', 'POSITION', 'CREDIT_APPROVE', '授信批复岗', 'CREDIT_APPROVE', 9, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_CM', 'POSITION', 'CUST_MGR', '客户经理', 'CUST_MGR', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_CORP', 'POSITION', 'CORP_STAFF', '公司部员工', 'CORP_STAFF', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_CR', 'POSITION', 'CREDIT_REVIEW', '授信审查岗', 'CREDIT_REVIEW', 8, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_FIN', 'POSITION', 'FINANCE_STAFF', '资财部员工', 'FINANCE_STAFF', 5, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_PRES', 'POSITION', 'PRESIDENT', '行长', 'PRESIDENT', 10, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_RTL', 'POSITION', 'RETAIL_STAFF', '零售部员工', 'RETAIL_STAFF', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_SEC', 'POSITION', 'SECRETARY', '部门秘书', 'SECRETARY', 7, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_POS_TECH', 'POSITION', 'TECH_STAFF', '科技部员工', 'TECH_STAFF', 6, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PSR_AVG', 'PERF_SUMMARY_RULE', 'AVG', '平均', 'AVG', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PSR_SUM', 'PERF_SUMMARY_RULE', 'SUM', '求和', 'SUM', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PTT_KPI', 'PERF_TASK_TYPE', 'KPI_RUN', 'KPI执行', 'KPI_RUN', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PTT_RECALC', 'PERF_TASK_TYPE', 'RECALC', '历史重算', 'RECALC', 4, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PTT_RUN', 'PERF_TASK_TYPE', 'METRIC_RUN', '指标执行', 'METRIC_RUN', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_PTT_TRIAL', 'PERF_TASK_TYPE', 'METRIC_TRIAL', '指标试运行', 'METRIC_TRIAL', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_RL_HIGH', 'RISK_LEVEL', 'HIGH', '高风险', 'HIGH', 3, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_RL_LOW', 'RISK_LEVEL', 'LOW', '低风险', 'LOW', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_RL_MID', 'RISK_LEVEL', 'MEDIUM', '中风险', 'MEDIUM', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:46', NULL, '2026-04-03 22:43:46'),
	('D_TC_Q', 'PERF_TARGET_CYCLE', 'QUARTER', '季度', 'QUARTER', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_TC_Y', 'PERF_TARGET_CYCLE', 'YEAR', '年度', 'YEAR', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_TD_EMP', 'PERF_TARGET_DIM', 'EMP', '人员', 'EMP', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_TD_ORG', 'PERF_TARGET_DIM', 'ORG', '机构', 'ORG', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_YN_0', 'YES_NO', 'NO', '否', '0', 2, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45'),
	('D_YN_1', 'YES_NO', 'YES', '是', '1', 1, 'ACTIVE', 'V1 seed', 'seed', '2026-04-03 22:43:45', NULL, '2026-04-03 22:43:45');

-- 正在导出表  onepl.sys_dict_item 的数据：~0 rows (大约)
DELETE FROM `sys_dict_item`;

-- 正在导出表  onepl.sys_job_conf 的数据：~0 rows (大约)
DELETE FROM `sys_job_conf`;

-- 正在导出表  onepl.sys_job_run_log 的数据：~0 rows (大约)
DELETE FROM `sys_job_run_log`;

-- 正在导出表  onepl.touch_log 的数据：~0 rows (大约)
DELETE FROM `touch_log`;

-- 正在导出表  onepl.touch_task 的数据：~0 rows (大约)
DELETE FROM `touch_task`;

-- 正在导出表  onepl.user_notification 的数据：~0 rows (大约)
DELETE FROM `user_notification`;

-- 正在导出表  onepl.wf_node_candidate_conf 的数据：~19 rows (大约)
DELETE FROM `wf_node_candidate_conf`;
INSERT INTO `wf_node_candidate_conf` (`id`, `process_definition_key`, `node_key`, `candidate_type`, `candidate_value`, `created_time`, `updated_time`) VALUES
	('baae61bb40b948929cf62154cef4d4e7', 'test-proc', 'approval', 'ROLE', 'R_ADMIN,R_BRANCH_MGR', '2026-04-10 12:20:19', '2026-04-10 12:20:19'),
	('WNC_ALLOC_BIZ', 'alloc_adjust_approve_v1', 'biz_dept_review', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_ALLOC_BIZ_LDR', 'alloc_adjust_approve_v1', 'biz_dept_leader_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_ALLOC_BM', 'alloc_adjust_approve_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_ALLOC_FIN', 'alloc_adjust_approve_v1', 'finance_review', 'ROLE', '["BACK_FINANCE"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_ALLOC_FIN_LDR', 'alloc_adjust_approve_v1', 'finance_leader_approve', 'ROLE', '["BACK_FINANCE"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_ALLOC_ORIG', 'alloc_adjust_approve_v1', 'original_owner_approve', 'ROLE', '["CUST_MANAGER"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LEAD_DEL_V1_HQ_ROLE', 'lead_delete_approve_v1', 'hq_delete_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LEAD_IMP_V1_HQ_ROLE', 'lead_import_approve_v1', 'hq_batch_approve', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LEAD_V1_BM_ROLE', 'lead_approve_v1', 'branch_manager_approve', 'ROLE', '["BRANCH_HEAD"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LEAD_V1_HQ_ROLE', 'lead_approve_v1', 'hq_review', 'ROLE', '["CORP_DEPT","RETAIL_DEPT"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', 'ROLE', '["BRANCH_HEAD"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', 'ROLE', '["CREDIT_APPROVER"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', 'ROLE', '["CREDIT_REVIEWER"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', 'ROLE', '["CORP_DEPT"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', 'ROLE', '["SUPPORT_SECRETARY"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', 'ROLE', '["SUPPORT_STAFF"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', 'ROLE', '["SUPPORT_STAFF"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_TGT_ADJ_FL', 'target_adjust_approve_v1', 'finance_leader_approve', 'ROLE', '["BACK_FINANCE"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WNC_TOUCH_V1_RM_ROLE', 'touch_process_v1', 'touch_execute', 'ROLE', '["CUST_MANAGER"]', '2026-04-03 22:43:46', '2026-04-03 22:46:34');

-- 正在导出表  onepl.wf_node_form_conf 的数据：~19 rows (大约)
DELETE FROM `wf_node_form_conf`;
INSERT INTO `wf_node_form_conf` (`id`, `process_definition_key`, `node_key`, `form_fields`, `editable_fields`, `required_fields`, `created_time`, `updated_time`) VALUES
	('56dc9ad0416845a09ae6221cf611a9b7', 'test-proc', 'approval', '["f1"]', '["f1"]', '[]', '2026-04-10 12:20:20', '2026-04-10 12:20:20'),
	('WFF_ALLOC_BIZ', 'alloc_adjust_approve_v1', 'biz_dept_review', '[{"key":"bizDeptOpinion","label":"业务部门审核意见","type":"TEXTAREA"}]', '["bizDeptOpinion"]', '["bizDeptOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_ALLOC_BIZ_LDR', 'alloc_adjust_approve_v1', 'biz_dept_leader_approve', '[{"key":"bizLeaderOpinion","label":"业务部门负责人意见","type":"TEXTAREA"}]', '["bizLeaderOpinion"]', '["bizLeaderOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_ALLOC_BM', 'alloc_adjust_approve_v1', 'branch_approve', '[{"key":"branchAllocOpinion","label":"机构审批意见","type":"TEXTAREA"}]', '["branchAllocOpinion"]', '["branchAllocOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_ALLOC_FIN', 'alloc_adjust_approve_v1', 'finance_review', '[{"key":"financeOpinion","label":"资财部审核意见","type":"TEXTAREA"},{"key":"recalcRequired","label":"是否需要历史重算","type":"RADIO","dictType":"YES_NO"}]', '["financeOpinion","recalcRequired"]', '["financeOpinion","recalcRequired"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_ALLOC_FIN_LDR', 'alloc_adjust_approve_v1', 'finance_leader_approve', '[{"key":"finLeaderOpinion","label":"资财部负责人审批意见","type":"TEXTAREA"}]', '["finLeaderOpinion"]', '["finLeaderOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_ALLOC_ORIG', 'alloc_adjust_approve_v1', 'original_owner_approve', '[{"key":"ownerConfirm","label":"原管户人确认意见","type":"TEXTAREA"}]', '["ownerConfirm"]', '["ownerConfirm"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LEAD_DEL_V1_HQ', 'lead_delete_approve_v1', 'hq_delete_approve', '[{"key":"deleteOpinion","label":"删除审批意见","type":"TEXTAREA"}]', '["deleteOpinion"]', '["deleteOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LEAD_IMP_V1_HQ', 'lead_import_approve_v1', 'hq_batch_approve', '[{"key":"batchOpinion","label":"批次审批意见","type":"TEXTAREA"}]', '["batchOpinion"]', '["batchOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LEAD_V1_BM', 'lead_approve_v1', 'branch_manager_approve', '[{"key":"bmOpinion","label":"机构负责人意见","type":"TEXTAREA"}]', '["bmOpinion"]', '["bmOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WFF_LEAD_V1_HQ', 'lead_approve_v1', 'hq_review', '[{"key":"hqConclusion","label":"总部审核结论","type":"TEXTAREA"},{"key":"riskLevel","label":"风险等级","type":"SELECT","dictType":"RISK_LEVEL"}]', '["hqConclusion","riskLevel"]', '["hqConclusion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', '[{"key":"branchOpinion","label":"机构审批意见","type":"TEXTAREA"}]', '["branchOpinion"]', '["branchOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', '[{"key":"approvalOpinion","label":"批复意见","type":"TEXTAREA"},{"key":"approvedAmount","label":"批复金额(万元)","type":"NUMBER"},{"key":"approvedTerm","label":"批复期限(月)","type":"NUMBER"}]', '["approvalOpinion","approvedAmount","approvedTerm"]', '["approvalOpinion","approvedAmount","approvedTerm"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', '[{"key":"creditCheckOpinion","label":"授信审查意见","type":"TEXTAREA"},{"key":"creditCheckResult","label":"审查结论","type":"SELECT","options":[{"label":"通过","value":"PASS"},{"label":"补充材料","value":"SUPPLEMENT"},{"label":"拒绝","value":"REJECT"}]}]', '["creditCheckOpinion","creditCheckResult"]', '["creditCheckOpinion","creditCheckResult"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', '[{"key":"corpOpinion","label":"公司部审核意见","type":"TEXTAREA"},{"key":"needCreditCommittee","label":"是否需要上会","type":"RADIO","dictType":"YES_NO"},{"key":"creditCommitteeConclusion","label":"上会结论","type":"TEXTAREA"}]', '["corpOpinion","needCreditCommittee","creditCommitteeConclusion"]', '["corpOpinion","needCreditCommittee"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', '[{"key":"assignedEmpId","label":"指定支持人员","type":"USER_SELECT"},{"key":"dispatchRemark","label":"派单备注","type":"TEXTAREA"}]', '["assignedEmpId","dispatchRemark"]', '["assignedEmpId"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', '[{"key":"handleResult","label":"办理结果","type":"TEXTAREA"},{"key":"visitPhotoUrls","label":"拜访照片","type":"FILE_LIST"}]', '["handleResult","visitPhotoUrls"]', '["handleResult"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', '[{"key":"handleResult","label":"办理结果","type":"TEXTAREA"},{"key":"visitPhotoUrls","label":"拜访照片","type":"FILE_LIST"}]', '["handleResult","visitPhotoUrls"]', '["handleResult"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_TGT_ADJ_FL', 'target_adjust_approve_v1', 'finance_leader_approve', '[{"key":"adjustOpinion","label":"修正审批意见","type":"TEXTAREA"}]', '["adjustOpinion"]', '["adjustOpinion"]', '2026-04-03 22:43:47', '2026-04-03 22:46:35'),
	('WFF_TOUCH_V1_EXEC', 'touch_process_v1', 'touch_execute', '[]', '[]', '[]', '2026-04-03 22:43:47', '2026-04-03 22:46:35');

-- 正在导出表  onepl.wf_timeout_rule 的数据：~19 rows (大约)
DELETE FROM `wf_timeout_rule`;
INSERT INTO `wf_timeout_rule` (`id`, `process_definition_key`, `node_key`, `timeout_hours`, `warning_hours`, `created_time`, `updated_time`) VALUES
	('c7d69ada34e44e73bee8b29417977ff7', 'test-proc', 'approval', 24, 12, '2026-04-10 12:20:18', '2026-04-10 12:20:19'),
	('WTR_ALLOC_BIZ', 'alloc_adjust_approve_v1', 'biz_dept_review', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_ALLOC_BIZ_LDR', 'alloc_adjust_approve_v1', 'biz_dept_leader_approve', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_ALLOC_BM', 'alloc_adjust_approve_v1', 'branch_approve', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_ALLOC_FIN', 'alloc_adjust_approve_v1', 'finance_review', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_ALLOC_FIN_LDR', 'alloc_adjust_approve_v1', 'finance_leader_approve', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_ALLOC_ORIG', 'alloc_adjust_approve_v1', 'original_owner_approve', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_LEAD_DEL_V1_HQ', 'lead_delete_approve_v1', 'hq_delete_approve', 48, 24, '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WTR_LEAD_IMP_V1_HQ', 'lead_import_approve_v1', 'hq_batch_approve', 72, 24, '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WTR_LEAD_V1_BM', 'lead_approve_v1', 'branch_manager_approve', 48, 24, '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WTR_LEAD_V1_HQ', 'lead_approve_v1', 'hq_review', 96, 48, '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WTR_LOAN_V1_BM', 'loan_approve_v1', 'branch_approve', 48, 24, '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WTR_LOAN_V1_CA', 'loan_approve_v1', 'credit_approval', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_LOAN_V1_CK', 'loan_approve_v1', 'credit_check', 72, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_LOAN_V1_CORP', 'loan_approve_v1', 'corp_review', 72, 24, '2026-04-03 22:43:46', '2026-04-03 22:46:34'),
	('WTR_SUP_COMPLEX_SEC', 'support_complex_v1', 'dept_secretary_dispatch', 24, 8, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_SUP_COMPLEX_STAFF', 'support_complex_v1', 'support_staff_handle', 72, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_SUP_SIMPLE_OWNER', 'support_simple_v1', 'product_owner_handle', 72, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_TGT_ADJ_FL', 'target_adjust_approve_v1', 'finance_leader_approve', 48, 24, '2026-04-03 22:43:47', '2026-04-03 22:46:34'),
	('WTR_TOUCH_V1_EXEC', 'touch_process_v1', 'touch_execute', 48, 24, '2026-04-03 22:43:46', '2026-04-03 22:46:34');

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
