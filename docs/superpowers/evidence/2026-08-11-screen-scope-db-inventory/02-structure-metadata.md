# 结构元数据原始结果（脱敏）

本文件对应成功的显式 `READ ONLY` 事务中的 `information_schema.tables`、`columns`、`statistics`、`key_column_usage`、`triggers`、`views` 和 `routines` 查询。仅含 schema 元数据，不含任何行级业务数据。

## 表存在性

```text
AUDIT_LOG               BASE TABLE  InnoDB  utf8mb4_general_ci
PT_RESOURCE             BASE TABLE  InnoDB  utf8mb4_general_ci
PT_ROLE                 BASE TABLE  InnoDB  utf8mb4_general_ci
PT_ROLE_RESOURCE        BASE TABLE  InnoDB  utf8mb4_general_ci
RPT_SCREEN              BASE TABLE  InnoDB  utf8mb4_general_ci
RPT_SCREEN_DATASOURCE   BASE TABLE  InnoDB  utf8mb4_general_ci
```

以下目标表未返回表记录：`PT_ORG_PROFILE`、`PT_ORG_GROUP`、`PT_ORG_GROUP_MEMBER`、`PT_ROLE_ORG_GROUP`、`RPT_SCREEN_ACCESS_ROLE`。

## 列定义

记号：`—` 为 `information_schema.columns.column_default IS NULL`；`DEFAULT_GENERATED` 表示数据库自动生成当前时间默认值；未特别说明的生成表达式均为空。

### `PT_ROLE`

| 列 | 类型/长度 | 可空 | 默认值 | extra / generation |
|---|---|---|---|---|
| `ROLE_ID` | varchar(50) | NO | — | — |
| `ROLE_CODE` | varchar(50) | NO | — | — |
| `ROLE_CHNAME` | varchar(100) | NO | — | — |
| `RECORD_STATUS` | int (p=10,s=0) | YES | `0` | — |
| `SYS_CODE` | varchar(10) | YES | `PLATFORM` | — |
| `CREATE_TIME` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED` |
| `CREATE_USER` | varchar(50) | YES | — | — |
| `UPDATE_TIME` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED on update CURRENT_TIMESTAMP` |
| `UPDATE_USER` | varchar(50) | YES | — | — |
| `REMARK` | varchar(100) | YES | — | — |

### `PT_RESOURCE`

| 列 | 类型/长度 | 可空 | 默认值 | extra / generation |
|---|---|---|---|---|
| `RESOURCE_ID` | varchar(20) | NO | — | — |
| `RESOURCE_URL` | varchar(256) | NO | — | — |
| `RESOURCE_METHOD` | varchar(10) | NO | — | — |
| `MENU_NAME` | varchar(256) | NO | — | — |
| `MENU_ICON_URL` | varchar(256) | YES | — | — |
| `MENU_RANK_NO` | int (p=10,s=0) | YES | `0` | — |
| `ISMENU` | int (p=10,s=0) | YES | `0` | — |
| `MENU_ENDFLAG` | varchar(10) | YES | `0` | — |
| `PARENT_RESOURCE_ID` | varchar(60) | YES | — | — |
| `STATUS` | int (p=10,s=0) | YES | `0` | — |
| `SYS_CODE` | varchar(10) | YES | `PLATFORM` | — |
| `CREATE_TIME` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED` |
| `CREATE_USER` | varchar(50) | YES | — | — |
| `UPDATE_TIME` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED on update CURRENT_TIMESTAMP` |
| `UPDATE_USER` | varchar(50) | YES | — | — |
| `REMARK` | varchar(100) | YES | — | — |

### `PT_ROLE_RESOURCE`

| 列 | 类型/长度 | 可空 | 默认值 | extra / generation |
|---|---|---|---|---|
| `ID` | varchar(32) | NO | — | — |
| `ROLE_ID` | varchar(50) | NO | — | — |
| `RESOURCE_ID` | varchar(20) | NO | — | — |
| `SYS_CODE` | varchar(10) | YES | `PLATFORM` | — |
| `CREATE_TIME` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED` |

### `AUDIT_LOG`

| 列 | 类型/长度 | 可空 | 默认值 | extra / generation |
|---|---|---|---|---|
| `id` | varchar(32) | NO | — | — |
| `trace_id` | varchar(64) | YES | — | — |
| `emp_id` | varchar(32) | NO | — | — |
| `emp_name` | varchar(100) | YES | — | — |
| `biz_type` | varchar(50) | YES | — | — |
| `biz_action` | varchar(50) | YES | — | — |
| `resource_url` | varchar(500) | YES | — | — |
| `request_method` | varchar(20) | YES | — | — |
| `request_params` | text (max=65535) | YES | — | — |
| `response_status` | int (p=10,s=0) | YES | — | — |
| `error_msg` | text (max=65535) | YES | — | — |
| `ip_address` | varchar(50) | YES | — | — |
| `user_agent` | varchar(500) | YES | — | — |
| `execution_time` | int (p=10,s=0) | YES | — | — |
| `reason` | varchar(500) | YES | — | — |
| `created_time` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED` |

本期预期的 `target_type`、`target_id`、`before_snapshot`、`after_snapshot`、`added_items`、`removed_items` 均未出现。

### `RPT_SCREEN`

| 列 | 类型/长度 | 可空 | 默认值 | extra / generation |
|---|---|---|---|---|
| `id` | bigint (p=19,s=0) | NO | — | `auto_increment` |
| `screen_code` | varchar(64) | NO | — | — |
| `screen_name` | varchar(100) | NO | — | — |
| `view_level` | varchar(20) | NO | — | — |
| `theme_json` | varchar(1000) | YES | — | — |
| `status` | varchar(10) | NO | `ACTIVE` | — |
| `created_by` | varchar(32) | YES | — | — |
| `created_time` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED` |
| `updated_time` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED on update CURRENT_TIMESTAMP` |
| `deleted` | tinyint (p=3,s=0) | NO | `0` | — |
| `canvas_style_json` | longtext (max=4294967295) | YES | — | — |
| `canvas_draft_json` | longtext (max=4294967295) | YES | — | — |
| `canvas_published_json` | longtext (max=4294967295) | YES | — | — |
| `canvas_version` | int (p=10,s=0) | NO | `0` | — |
| `publish_status` | tinyint (p=3,s=0) | NO | `0` | — |
| `published_at` | datetime | YES | — | — |
| `published_by` | varchar(32) | YES | — | — |

本期对齐字段 `biz_line`、`org_scope_mode`、`org_group_code`、`updated_by`、`active_screen_code` 均未出现，故也不存在生成列表达式。

### `RPT_SCREEN_DATASOURCE`

| 列 | 类型/长度 | 可空 | 默认值 | extra / generation |
|---|---|---|---|---|
| `id` | bigint (p=19,s=0) | NO | — | `auto_increment` |
| `ds_code` | varchar(64) | NO | — | — |
| `ds_name` | varchar(100) | NO | — | — |
| `ds_type` | varchar(20) | NO | — | — |
| `source_kind` | varchar(20) | NO | — | — |
| `config_json` | text (max=65535) | NO | — | — |
| `time_param_json` | varchar(500) | YES | — | — |
| `status` | varchar(10) | NO | `ACTIVE` | — |
| `remark` | varchar(500) | YES | — | — |
| `created_by` | varchar(32) | YES | — | — |
| `created_time` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED` |
| `updated_time` | datetime | YES | `CURRENT_TIMESTAMP` | `DEFAULT_GENERATED on update CURRENT_TIMESTAMP` |
| `deleted` | tinyint (p=3,s=0) | NO | `0` | — |

本期对齐字段 `biz_line`、`updated_by` 均未出现。

## 主键、唯一键与索引

```text
AUDIT_LOG              PRIMARY                              unique  ID
AUDIT_LOG              idx_biz_type                         nonunique  biz_type
AUDIT_LOG              idx_created_time                     nonunique  created_time
AUDIT_LOG              idx_emp_id                           nonunique  emp_id
AUDIT_LOG              idx_trace_id                         nonunique  trace_id
PT_RESOURCE            PRIMARY                              unique  RESOURCE_ID
PT_RESOURCE            uk_pt_resource_url_method_sys        unique  RESOURCE_URL,RESOURCE_METHOD,SYS_CODE
PT_RESOURCE            idx_pt_resource_status               nonunique  STATUS,SYS_CODE
PT_ROLE                PRIMARY                              unique  ROLE_ID
PT_ROLE_RESOURCE       PRIMARY                              unique  ID
PT_ROLE_RESOURCE       idx_resource_id                      nonunique  RESOURCE_ID
PT_ROLE_RESOURCE       idx_role_id                          nonunique  ROLE_ID
RPT_SCREEN             PRIMARY                              unique  id
RPT_SCREEN             idx_scr_code                         nonunique  screen_code
RPT_SCREEN_DATASOURCE  PRIMARY                              unique  id
RPT_SCREEN_DATASOURCE  idx_scr_ds_code                      nonunique  ds_code
RPT_SCREEN_DATASOURCE  idx_scr_ds_type                      nonunique  ds_type
```

尚不存在的本期索引：`UK_PT_ROLE_ROLE_CODE`、`IDX_AUDIT_LOG_TARGET`、`uk_rpt_screen_active_code`、`idx_scr_scope_group`、`idx_scr_ds_biz_line`，以及 `RPT_SCREEN_ACCESS_ROLE` 的主键/唯一键/状态索引。

## 外键、触发器、视图、过程冲突

下列查询均返回 0 行：

- 目标表的外键（`information_schema.key_column_usage.referenced_table_name IS NOT NULL`）；
- 目标表触发器；
- 目标同名视图；
- `sp_auth_org_profile_group_20260811`、`sp_screen_scope_map_align_20260811`、`sp_screen_scope_map_seed_20260811` 同名例程。

因此当前没有这些对象与三份脚本拟创建/调用的对象发生命名冲突。
