# 原始 stdout/stderr 摘录

以下为本轮真实客户端输出。仅保留实例身份、结构、资源编码与汇总；没有导出业务明细、配置全文、人员或凭据。

## 01. 写前最小只读盘点（exit 0）

```text
database_name	server_uuid	hostname	port
yiti_test	d3a209c4-42bd-11f1-bb1f-000c299f5629	ubuntu	3306
schema_table_count
192
TABLE_NAME	column_count
PT_ORG_GROUP	11
PT_ORG_GROUP_MEMBER	9
PT_ORG_PROFILE	16
PT_ROLE_ORG_GROUP	9
RPT_SCREEN	22
RPT_SCREEN_ACCESS_ROLE	8
RPT_SCREEN_DATASOURCE	15
metric	value
PT_ORG_GROUP_seed_targets	0
RPT_SCREEN_seed_targets	0
RPT_SCREEN_ACCESS_ROLE_seed_targets	0
PT_RESOURCE_align_targets	4
PT_ROLE_RESOURCE_align_targets	9
```

## 05. auth 幂等复跑（exit 0）

```text
database_name	server_uuid	hostname	port
yiti_test	d3a209c4-42bd-11f1-bb1f-000c299f5629	ubuntu	3306
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
auth_preflight_result
AUTH deploy zero-DDL preflight passed
auth_preflight_guard
AUTH deploy zero-DDL preflight passed
Note (Code 1305): PROCEDURE yiti_test.sp_auth_org_profile_group_20260811 does not exist
ROLE_ID	ROLE_CODE	ROLE_CHNAME
242	R_SCREEN_CORP_VIEWER	对公大屏查看
243	R_SCREEN_RETAIL_VIEWER	零售大屏查看
RESOURCE_ID	RESOURCE_URL	RESOURCE_METHOD	SYS_CODE
A_ORG_GRP_CREATE	/api/admin/org-groups	POST	PLATFORM
A_ORG_GRP_EDIT	/api/admin/org-groups/*	PUT	PLATFORM
A_ORG_GRP_LIST	/api/admin/org-groups	GET	PLATFORM
A_ORG_GRP_MEM	/api/admin/org-groups/*/members	PUT	PLATFORM
A_ORG_GRP_ROLE	/api/admin/org-groups/*/roles	PUT	PLATFORM
A_ORG_PROF_EDIT	/api/admin/org-profiles/*	PUT	PLATFORM
A_ORG_PROF_LIST	/api/admin/org-profiles	GET	PLATFORM
table_name	row_count
PT_ORG_PROFILE	0
PT_ORG_GROUP	2
PT_ORG_GROUP_MEMBER	0
PT_ROLE_ORG_GROUP	0
```

## 06. align 幂等复跑（exit 0）

```text
database_name	server_uuid	hostname	port
yiti_test	d3a209c4-42bd-11f1-bb1f-000c299f5629	ubuntu	3306
TABLE_NAME	TABLE_TYPE
PT_RESOURCE	BASE TABLE
PT_ROLE	BASE TABLE
PT_ROLE_RESOURCE	BASE TABLE
RPT_SCREEN	BASE TABLE
RPT_SCREEN_ACCESS_ROLE	BASE TABLE
RPT_SCREEN_DATASOURCE	BASE TABLE
TABLE_NAME	COLUMN_NAME	ORDINAL_POSITION	DATA_TYPE	CHARACTER_MAXIMUM_LENGTH	IS_NULLABLE	COLUMN_DEFAULT	EXTRA	GENERATION_EXPRESSION
PT_RESOURCE	RESOURCE_ID	1	varchar	20	NO	NULL		
PT_RESOURCE	RESOURCE_URL	2	varchar	256	NO	NULL		
PT_RESOURCE	RESOURCE_METHOD	3	varchar	10	NO	NULL		
PT_RESOURCE	MENU_NAME	4	varchar	256	NO	NULL		
PT_RESOURCE	MENU_ICON_URL	5	varchar	256	YES	NULL		
PT_RESOURCE	MENU_RANK_NO	6	int	NULL	YES	0		
PT_RESOURCE	ISMENU	7	int	NULL	YES	0		
PT_RESOURCE	MENU_ENDFLAG	8	varchar	10	YES	0		
PT_RESOURCE	PARENT_RESOURCE_ID	9	varchar	60	YES	NULL		
PT_RESOURCE	STATUS	10	int	NULL	YES	0		
PT_RESOURCE	SYS_CODE	11	varchar	10	YES	PLATFORM		
PT_RESOURCE	CREATE_TIME	12	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
PT_RESOURCE	CREATE_USER	13	varchar	50	YES	NULL		
PT_RESOURCE	UPDATE_TIME	14	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
PT_RESOURCE	UPDATE_USER	15	varchar	50	YES	NULL		
PT_RESOURCE	REMARK	16	varchar	100	YES	NULL		
PT_ROLE	ROLE_ID	1	varchar	50	NO	NULL		
PT_ROLE	ROLE_CODE	2	varchar	50	NO	NULL		
PT_ROLE	ROLE_CHNAME	3	varchar	100	NO	NULL		
PT_ROLE	RECORD_STATUS	4	int	NULL	YES	0		
PT_ROLE	SYS_CODE	5	varchar	10	YES	PLATFORM		
PT_ROLE	CREATE_TIME	6	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
PT_ROLE	CREATE_USER	7	varchar	50	YES	NULL		
PT_ROLE	UPDATE_TIME	8	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
PT_ROLE	UPDATE_USER	9	varchar	50	YES	NULL		
PT_ROLE	REMARK	10	varchar	100	YES	NULL		
PT_ROLE_RESOURCE	ID	1	varchar	32	NO	NULL		
PT_ROLE_RESOURCE	ROLE_ID	2	varchar	50	NO	NULL		
PT_ROLE_RESOURCE	RESOURCE_ID	3	varchar	20	NO	NULL		
PT_ROLE_RESOURCE	SYS_CODE	4	varchar	10	YES	PLATFORM		
PT_ROLE_RESOURCE	CREATE_TIME	5	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN	screen_code	2	varchar	64	NO	NULL		
RPT_SCREEN	active_screen_code	3	varchar	64	YES	NULL	STORED GENERATED	(case when (`deleted` = 0) then `screen_code` else NULL end)
RPT_SCREEN	screen_name	4	varchar	100	NO	NULL		
RPT_SCREEN	view_level	5	varchar	20	NO	NULL		
RPT_SCREEN	biz_line	6	varchar	20	NO	COMMON		
RPT_SCREEN	org_scope_mode	7	varchar	20	NO	LEGACY_CONTEXT		
RPT_SCREEN	org_group_code	8	varchar	64	YES	NULL		
RPT_SCREEN	theme_json	9	varchar	1000	YES	NULL		
RPT_SCREEN	status	10	varchar	10	NO	ACTIVE		
RPT_SCREEN	created_by	11	varchar	32	YES	NULL		
RPT_SCREEN	updated_by	12	varchar	50	YES	NULL		
RPT_SCREEN	created_time	13	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN	updated_time	14	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
RPT_SCREEN	deleted	15	tinyint	NULL	NO	0		
RPT_SCREEN	canvas_style_json	16	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_draft_json	17	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_published_json	18	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_version	19	int	NULL	NO	0		
RPT_SCREEN	publish_status	20	tinyint	NULL	NO	0		
RPT_SCREEN	published_at	21	datetime	NULL	YES	NULL		
RPT_SCREEN	published_by	22	varchar	32	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN_ACCESS_ROLE	screen_id	2	bigint	NULL	NO	NULL		
RPT_SCREEN_ACCESS_ROLE	role_code	3	varchar	50	NO	NULL		
RPT_SCREEN_ACCESS_ROLE	status	4	varchar	10	NO	ACTIVE		
RPT_SCREEN_ACCESS_ROLE	created_by	5	varchar	50	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	created_time	6	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN_ACCESS_ROLE	updated_by	7	varchar	50	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	updated_time	8	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
RPT_SCREEN_DATASOURCE	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN_DATASOURCE	ds_code	2	varchar	64	NO	NULL		
RPT_SCREEN_DATASOURCE	ds_name	3	varchar	100	NO	NULL		
RPT_SCREEN_DATASOURCE	ds_type	4	varchar	20	NO	NULL		
RPT_SCREEN_DATASOURCE	source_kind	5	varchar	20	NO	NULL		
RPT_SCREEN_DATASOURCE	biz_line	6	varchar	20	NO	COMMON		
RPT_SCREEN_DATASOURCE	config_json	7	text	65535	NO	NULL		
RPT_SCREEN_DATASOURCE	time_param_json	8	varchar	500	YES	NULL		
RPT_SCREEN_DATASOURCE	status	9	varchar	10	NO	ACTIVE		
RPT_SCREEN_DATASOURCE	remark	10	varchar	500	YES	NULL		
RPT_SCREEN_DATASOURCE	created_by	11	varchar	32	YES	NULL		
RPT_SCREEN_DATASOURCE	updated_by	12	varchar	50	YES	NULL		
RPT_SCREEN_DATASOURCE	created_time	13	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN_DATASOURCE	updated_time	14	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
RPT_SCREEN_DATASOURCE	deleted	15	tinyint	NULL	NO	0		
TABLE_NAME	INDEX_NAME	NON_UNIQUE	SEQ_IN_INDEX	COLUMN_NAME
PT_RESOURCE	idx_pt_resource_status	1	1	STATUS
PT_RESOURCE	idx_pt_resource_status	1	2	SYS_CODE
PT_RESOURCE	PRIMARY	0	1	RESOURCE_ID
PT_RESOURCE	uk_pt_resource_url_method_sys	0	1	RESOURCE_URL
PT_RESOURCE	uk_pt_resource_url_method_sys	0	2	RESOURCE_METHOD
PT_RESOURCE	uk_pt_resource_url_method_sys	0	3	SYS_CODE
PT_ROLE	PRIMARY	0	1	ROLE_ID
PT_ROLE	UK_PT_ROLE_ROLE_CODE	0	1	ROLE_CODE
PT_ROLE_RESOURCE	idx_resource_id	1	1	RESOURCE_ID
PT_ROLE_RESOURCE	idx_role_id	1	1	ROLE_ID
PT_ROLE_RESOURCE	PRIMARY	0	1	ID
RPT_SCREEN	idx_scr_code	1	1	screen_code
RPT_SCREEN	idx_scr_scope_group	1	1	org_scope_mode
RPT_SCREEN	idx_scr_scope_group	1	2	org_group_code
RPT_SCREEN	PRIMARY	0	1	id
RPT_SCREEN	uk_rpt_screen_active_code	0	1	active_screen_code
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	1	screen_id
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	2	status
RPT_SCREEN_ACCESS_ROLE	PRIMARY	0	1	id
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	1	screen_id
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	2	role_code
RPT_SCREEN_DATASOURCE	idx_scr_ds_biz_line	1	1	biz_line
RPT_SCREEN_DATASOURCE	idx_scr_ds_code	1	1	ds_code
RPT_SCREEN_DATASOURCE	idx_scr_ds_type	1	1	ds_type
RPT_SCREEN_DATASOURCE	PRIMARY	0	1	id
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
preflight_result
REPORT align zero-DDL preflight passed
preflight_guard
REPORT align zero-DDL preflight passed
Note (Code 1305): PROCEDURE yiti_test.sp_screen_scope_map_align_20260811 does not exist
table_name	row_count
RPT_SCREEN	5
table_name	row_count
RPT_SCREEN_DATASOURCE	11
table_name	row_count
RPT_SCREEN_ACCESS_ROLE	2
RESOURCE_ID	RESOURCE_URL	RESOURCE_METHOD	SYS_CODE
R_RPT_SCR_AR_LIST	/api/screen/admin/screens/*/access-roles	GET	RPT
R_RPT_SCR_AR_SAVE	/api/screen/admin/screens/*/access-roles	PUT	RPT
R_RPT_SCR_DS_PROBE	/api/screen/admin/datasources/*/probe-columns	POST	RPT
R_RPT_SCR_META_SAVE	/api/screen/admin/screens/*/metadata	PUT	RPT
ROLE_ID	ROLE_CODE	RESOURCE_ID
1	SYS_ADMIN	R_RPT_SCR_AR_LIST
129	FINANCE_LEADER	R_RPT_SCR_AR_LIST
237	R_2FAB45A1	R_RPT_SCR_AR_LIST
238	BACK_FINANCE	R_RPT_SCR_AR_LIST
1	SYS_ADMIN	R_RPT_SCR_AR_SAVE
1	SYS_ADMIN	R_RPT_SCR_META_SAVE
129	FINANCE_LEADER	R_RPT_SCR_META_SAVE
237	R_2FAB45A1	R_RPT_SCR_META_SAVE
238	BACK_FINANCE	R_RPT_SCR_META_SAVE
```

## 07. seed 幂等复跑（exit 0）

```text
database_name	server_uuid	hostname	port
yiti_test	d3a209c4-42bd-11f1-bb1f-000c299f5629	ubuntu	3306
TABLE_NAME	TABLE_TYPE
PT_ORG_GROUP	BASE TABLE
PT_ROLE	BASE TABLE
RPT_SCREEN	BASE TABLE
RPT_SCREEN_ACCESS_ROLE	BASE TABLE
TABLE_NAME	COLUMN_NAME	ORDINAL_POSITION	DATA_TYPE	CHARACTER_MAXIMUM_LENGTH	IS_NULLABLE	COLUMN_DEFAULT	EXTRA	GENERATION_EXPRESSION
PT_ORG_GROUP	ID	1	bigint	NULL	NO	NULL	auto_increment	
PT_ORG_GROUP	GROUP_CODE	2	varchar	64	NO	NULL		
PT_ORG_GROUP	GROUP_NAME	3	varchar	100	NO	NULL		
PT_ORG_GROUP	GROUP_PURPOSE	4	varchar	30	NO	NULL		
PT_ORG_GROUP	STATUS	5	varchar	10	NO	ACTIVE		
PT_ORG_GROUP	VERSION	6	int	NULL	NO	0		
PT_ORG_GROUP	CREATED_BY	7	varchar	50	YES	NULL		
PT_ORG_GROUP	CREATED_TIME	8	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
PT_ORG_GROUP	UPDATED_BY	9	varchar	50	YES	NULL		
PT_ORG_GROUP	UPDATED_TIME	10	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
PT_ORG_GROUP	REMARK	11	varchar	500	YES	NULL		
PT_ROLE	ROLE_ID	1	varchar	50	NO	NULL		
PT_ROLE	ROLE_CODE	2	varchar	50	NO	NULL		
PT_ROLE	ROLE_CHNAME	3	varchar	100	NO	NULL		
PT_ROLE	RECORD_STATUS	4	int	NULL	YES	0		
PT_ROLE	SYS_CODE	5	varchar	10	YES	PLATFORM		
PT_ROLE	CREATE_TIME	6	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
PT_ROLE	CREATE_USER	7	varchar	50	YES	NULL		
PT_ROLE	UPDATE_TIME	8	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
PT_ROLE	UPDATE_USER	9	varchar	50	YES	NULL		
PT_ROLE	REMARK	10	varchar	100	YES	NULL		
RPT_SCREEN	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN	screen_code	2	varchar	64	NO	NULL		
RPT_SCREEN	active_screen_code	3	varchar	64	YES	NULL	STORED GENERATED	(case when (`deleted` = 0) then `screen_code` else NULL end)
RPT_SCREEN	screen_name	4	varchar	100	NO	NULL		
RPT_SCREEN	view_level	5	varchar	20	NO	NULL		
RPT_SCREEN	biz_line	6	varchar	20	NO	COMMON		
RPT_SCREEN	org_scope_mode	7	varchar	20	NO	LEGACY_CONTEXT		
RPT_SCREEN	org_group_code	8	varchar	64	YES	NULL		
RPT_SCREEN	theme_json	9	varchar	1000	YES	NULL		
RPT_SCREEN	status	10	varchar	10	NO	ACTIVE		
RPT_SCREEN	created_by	11	varchar	32	YES	NULL		
RPT_SCREEN	updated_by	12	varchar	50	YES	NULL		
RPT_SCREEN	created_time	13	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN	updated_time	14	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
RPT_SCREEN	deleted	15	tinyint	NULL	NO	0		
RPT_SCREEN	canvas_style_json	16	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_draft_json	17	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_published_json	18	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_version	19	int	NULL	NO	0		
RPT_SCREEN	publish_status	20	tinyint	NULL	NO	0		
RPT_SCREEN	published_at	21	datetime	NULL	YES	NULL		
RPT_SCREEN	published_by	22	varchar	32	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN_ACCESS_ROLE	screen_id	2	bigint	NULL	NO	NULL		
RPT_SCREEN_ACCESS_ROLE	role_code	3	varchar	50	NO	NULL		
RPT_SCREEN_ACCESS_ROLE	status	4	varchar	10	NO	ACTIVE		
RPT_SCREEN_ACCESS_ROLE	created_by	5	varchar	50	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	created_time	6	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN_ACCESS_ROLE	updated_by	7	varchar	50	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	updated_time	8	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
TABLE_NAME	INDEX_NAME	NON_UNIQUE	SEQ_IN_INDEX	COLUMN_NAME
PT_ORG_GROUP	IDX_PT_ORG_GROUP_STATUS	1	1	STATUS
PT_ORG_GROUP	PRIMARY	0	1	ID
PT_ORG_GROUP	UK_PT_ORG_GROUP_CODE	0	1	GROUP_CODE
PT_ROLE	PRIMARY	0	1	ROLE_ID
PT_ROLE	UK_PT_ROLE_ROLE_CODE	0	1	ROLE_CODE
RPT_SCREEN	idx_scr_code	1	1	screen_code
RPT_SCREEN	idx_scr_scope_group	1	1	org_scope_mode
RPT_SCREEN	idx_scr_scope_group	1	2	org_group_code
RPT_SCREEN	PRIMARY	0	1	id
RPT_SCREEN	uk_rpt_screen_active_code	0	1	active_screen_code
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	1	screen_id
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	2	status
RPT_SCREEN_ACCESS_ROLE	PRIMARY	0	1	id
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	1	screen_id
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	2	role_code
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
preflight_result
REPORT seed zero-DDL preflight passed
preflight_guard
REPORT seed zero-DDL preflight passed
Note (Code 1305): PROCEDURE yiti_test.sp_screen_scope_map_seed_20260811 does not exist
screen_code	biz_line	org_scope_mode	org_group_code	publish_status	canvas_version
SCR_CORP_OVERVIEW	CORP	NAMED_GROUP	ORG_GRP_CORP_DEPARTMENTS	0	0
SCR_RETAIL_OVERVIEW	RETAIL	NAMED_GROUP	ORG_GRP_PRIMARY_OPERATING_UNITS	0	0
screen_id	role_code	status	ROLE_ID
9104	R_SCREEN_CORP_VIEWER	ACTIVE	242
9105	R_SCREEN_RETAIL_VIEWER	ACTIVE	243
```

## 08. 最终只读验收（exit 0）

```text
database_name	server_uuid	hostname	port
yiti_test	d3a209c4-42bd-11f1-bb1f-000c299f5629	ubuntu	3306
table_name	actual_column_count	expected_column_count
PT_ORG_PROFILE	16	16
PT_ORG_GROUP	11	11
PT_ORG_GROUP_MEMBER	9	9
PT_ROLE_ORG_GROUP	9	9
RPT_SCREEN	22	22
RPT_SCREEN_DATASOURCE	15	15
RPT_SCREEN_ACCESS_ROLE	8	8
TABLE_NAME	INDEX_NAME	non_unique	index_columns
PT_ORG_GROUP	IDX_PT_ORG_GROUP_STATUS	1	STATUS
PT_ORG_GROUP	PRIMARY	0	ID
PT_ORG_GROUP	UK_PT_ORG_GROUP_CODE	0	GROUP_CODE
PT_ORG_GROUP_MEMBER	IDX_PT_ORG_GROUP_MEMBER_GROUP	1	GROUP_CODE,STATUS
PT_ORG_GROUP_MEMBER	IDX_PT_ORG_GROUP_MEMBER_ORG	1	ORG_CODE
PT_ORG_GROUP_MEMBER	PRIMARY	0	ID
PT_ORG_GROUP_MEMBER	UK_PT_ORG_GROUP_MEMBER	0	GROUP_CODE,ORG_CODE
PT_ORG_PROFILE	IDX_PT_ORG_PROFILE_CITY	1	CITY_CODE
PT_ORG_PROFILE	IDX_PT_ORG_PROFILE_OWNER	1	OWNER_OPERATING_ORG_CODE
PT_ORG_PROFILE	IDX_PT_ORG_PROFILE_STATUS_LEVEL	1	STATUS,OPERATING_LEVEL
PT_ORG_PROFILE	PRIMARY	0	ORG_CODE
PT_ROLE_ORG_GROUP	IDX_PT_ROLE_ORG_GROUP_GROUP	1	GROUP_CODE,STATUS
PT_ROLE_ORG_GROUP	IDX_PT_ROLE_ORG_GROUP_ROLE	1	ROLE_ID,STATUS
PT_ROLE_ORG_GROUP	PRIMARY	0	ID
PT_ROLE_ORG_GROUP	UK_PT_ROLE_ORG_GROUP	0	ROLE_ID,GROUP_CODE
RPT_SCREEN	idx_scr_scope_group	1	ORG_SCOPE_MODE,ORG_GROUP_CODE
RPT_SCREEN	uk_rpt_screen_active_code	0	ACTIVE_SCREEN_CODE
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	SCREEN_ID,STATUS
RPT_SCREEN_ACCESS_ROLE	PRIMARY	0	ID
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	SCREEN_ID,ROLE_CODE
RPT_SCREEN_DATASOURCE	idx_scr_ds_biz_line	1	BIZ_LINE
metric	value
rpt_resource_target_count	4
rpt_resource_exact_identity_matches	4
rpt_role_resource_target_count	9
rpt_role_resource_ar_list	4
rpt_role_resource_ar_save_sys_admin	1
rpt_role_resource_meta_save	4
rpt_role_resource_ds_probe_default	0
rpt_role_resource_duplicate_pairs	0
metric	value
org_group_seed_targets	2
org_group_seed_identity_mismatch	0
org_group_member_seed_target_rows	0
role_org_group_seed_target_rows	0
screen_seed_targets	2
screen_seed_scope_mismatch	0
screen_seed_valid_draft_json	2
screen_retail_xian_composite_map	1
screen_retail_satellite_nodes_four	1
screen_seed_unpublished_drafts	2
screen_access_role_expected_pairs	2
screen_access_role_unexpected_pairs	0
screen_access_role_duplicate_pairs	0
datasource_total_rows	11
datasource_non_common_or_blank_biz_line	0
metric	value
duplicate_active_target_screen_code	0
duplicate_target_group_code	0
duplicate_target_role_code	0
temporary_procedure_residuals	0
metric	before_seed	after_rerun	delta
org_group_seed_targets	0	2	2
screen_seed_targets	0	2	2
screen_access_role_seed_targets	0	2	2
rpt_resource_align_targets	4	4	0
rpt_role_resource_align_targets	9	9	0
```

## 02. 不兼容客户端选项（exit 2，未建立数据库会话）

```text
mysql: [ERROR] unknown option '--abort-source-on-error'.
```

## 03. seed 首次执行（exit 0）

```text
database_name	server_uuid	hostname	port
yiti_test	d3a209c4-42bd-11f1-bb1f-000c299f5629	ubuntu	3306
TABLE_NAME	TABLE_TYPE
PT_ORG_GROUP	BASE TABLE
PT_ROLE	BASE TABLE
RPT_SCREEN	BASE TABLE
RPT_SCREEN_ACCESS_ROLE	BASE TABLE
TABLE_NAME	COLUMN_NAME	ORDINAL_POSITION	DATA_TYPE	CHARACTER_MAXIMUM_LENGTH	IS_NULLABLE	COLUMN_DEFAULT	EXTRA	GENERATION_EXPRESSION
PT_ORG_GROUP	ID	1	bigint	NULL	NO	NULL	auto_increment	
PT_ORG_GROUP	GROUP_CODE	2	varchar	64	NO	NULL		
PT_ORG_GROUP	GROUP_NAME	3	varchar	100	NO	NULL		
PT_ORG_GROUP	GROUP_PURPOSE	4	varchar	30	NO	NULL		
PT_ORG_GROUP	STATUS	5	varchar	10	NO	ACTIVE		
PT_ORG_GROUP	VERSION	6	int	NULL	NO	0		
PT_ORG_GROUP	CREATED_BY	7	varchar	50	YES	NULL		
PT_ORG_GROUP	CREATED_TIME	8	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
PT_ORG_GROUP	UPDATED_BY	9	varchar	50	YES	NULL		
PT_ORG_GROUP	UPDATED_TIME	10	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
PT_ORG_GROUP	REMARK	11	varchar	500	YES	NULL		
PT_ROLE	ROLE_ID	1	varchar	50	NO	NULL		
PT_ROLE	ROLE_CODE	2	varchar	50	NO	NULL		
PT_ROLE	ROLE_CHNAME	3	varchar	100	NO	NULL		
PT_ROLE	RECORD_STATUS	4	int	NULL	YES	0		
PT_ROLE	SYS_CODE	5	varchar	10	YES	PLATFORM		
PT_ROLE	CREATE_TIME	6	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
PT_ROLE	CREATE_USER	7	varchar	50	YES	NULL		
PT_ROLE	UPDATE_TIME	8	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
PT_ROLE	UPDATE_USER	9	varchar	50	YES	NULL		
PT_ROLE	REMARK	10	varchar	100	YES	NULL		
RPT_SCREEN	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN	screen_code	2	varchar	64	NO	NULL		
RPT_SCREEN	active_screen_code	3	varchar	64	YES	NULL	STORED GENERATED	(case when (`deleted` = 0) then `screen_code` else NULL end)
RPT_SCREEN	screen_name	4	varchar	100	NO	NULL		
RPT_SCREEN	view_level	5	varchar	20	NO	NULL		
RPT_SCREEN	biz_line	6	varchar	20	NO	COMMON		
RPT_SCREEN	org_scope_mode	7	varchar	20	NO	LEGACY_CONTEXT		
RPT_SCREEN	org_group_code	8	varchar	64	YES	NULL		
RPT_SCREEN	theme_json	9	varchar	1000	YES	NULL		
RPT_SCREEN	status	10	varchar	10	NO	ACTIVE		
RPT_SCREEN	created_by	11	varchar	32	YES	NULL		
RPT_SCREEN	updated_by	12	varchar	50	YES	NULL		
RPT_SCREEN	created_time	13	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN	updated_time	14	datetime	NULL	YES	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
RPT_SCREEN	deleted	15	tinyint	NULL	NO	0		
RPT_SCREEN	canvas_style_json	16	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_draft_json	17	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_published_json	18	longtext	4294967295	YES	NULL		
RPT_SCREEN	canvas_version	19	int	NULL	NO	0		
RPT_SCREEN	publish_status	20	tinyint	NULL	NO	0		
RPT_SCREEN	published_at	21	datetime	NULL	YES	NULL		
RPT_SCREEN	published_by	22	varchar	32	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	id	1	bigint	NULL	NO	NULL	auto_increment	
RPT_SCREEN_ACCESS_ROLE	screen_id	2	bigint	NULL	NO	NULL		
RPT_SCREEN_ACCESS_ROLE	role_code	3	varchar	50	NO	NULL		
RPT_SCREEN_ACCESS_ROLE	status	4	varchar	10	NO	ACTIVE		
RPT_SCREEN_ACCESS_ROLE	created_by	5	varchar	50	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	created_time	6	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED	
RPT_SCREEN_ACCESS_ROLE	updated_by	7	varchar	50	YES	NULL		
RPT_SCREEN_ACCESS_ROLE	updated_time	8	datetime	NULL	NO	CURRENT_TIMESTAMP	DEFAULT_GENERATED on update CURRENT_TIMESTAMP	
TABLE_NAME	INDEX_NAME	NON_UNIQUE	SEQ_IN_INDEX	COLUMN_NAME
PT_ORG_GROUP	IDX_PT_ORG_GROUP_STATUS	1	1	STATUS
PT_ORG_GROUP	PRIMARY	0	1	ID
PT_ORG_GROUP	UK_PT_ORG_GROUP_CODE	0	1	GROUP_CODE
PT_ROLE	PRIMARY	0	1	ROLE_ID
PT_ROLE	UK_PT_ROLE_ROLE_CODE	0	1	ROLE_CODE
RPT_SCREEN	idx_scr_code	1	1	screen_code
RPT_SCREEN	idx_scr_scope_group	1	1	org_scope_mode
RPT_SCREEN	idx_scr_scope_group	1	2	org_group_code
RPT_SCREEN	PRIMARY	0	1	id
RPT_SCREEN	uk_rpt_screen_active_code	0	1	active_screen_code
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	1	screen_id
RPT_SCREEN_ACCESS_ROLE	idx_rpt_screen_access_role_status	1	2	status
RPT_SCREEN_ACCESS_ROLE	PRIMARY	0	1	id
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	1	screen_id
RPT_SCREEN_ACCESS_ROLE	uk_rpt_screen_access_role	0	2	role_code
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
Warning (Code 1287): 'BINARY expr' is deprecated and will be removed in a future release. Please use CAST instead
preflight_result
REPORT seed zero-DDL preflight passed
preflight_guard
REPORT seed zero-DDL preflight passed
Note (Code 1305): PROCEDURE yiti_test.sp_screen_scope_map_seed_20260811 does not exist
screen_code	biz_line	org_scope_mode	org_group_code	publish_status	canvas_version
SCR_CORP_OVERVIEW	CORP	NAMED_GROUP	ORG_GRP_CORP_DEPARTMENTS	0	0
SCR_RETAIL_OVERVIEW	RETAIL	NAMED_GROUP	ORG_GRP_PRIMARY_OPERATING_UNITS	0	0
screen_id	role_code	status	ROLE_ID
9104	R_SCREEN_CORP_VIEWER	ACTIVE	242
9105	R_SCREEN_RETAIL_VIEWER	ACTIVE	243
```

## 04. seed 后只读聚合快照（exit 0）

```text
metric	value
PT_ORG_GROUP_seed_targets	2
PT_ORG_GROUP_seed_identity_mismatch	0
RPT_SCREEN_seed_targets	2
RPT_SCREEN_seed_scope_mismatch	0
RPT_SCREEN_draft_json_valid	2
RPT_SCREEN_xian_composite_map	1
RPT_SCREEN_unpublished_draft	2
RPT_SCREEN_ACCESS_ROLE_seed_targets	2
RPT_SCREEN_ACCESS_ROLE_seed_mismatch	0
PT_ORG_GROUP_MEMBER_target_rows	0
PT_ROLE_ORG_GROUP_target_rows	0
PT_RESOURCE_align_targets	4
PT_ROLE_RESOURCE_align_targets	9
```
