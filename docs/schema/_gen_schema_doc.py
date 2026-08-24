# -*- coding: utf-8 -*-
"""
yiti 数据表结构与关联图生成器
============================

输入: 项目内嵌结构化元数据(从 docs/schema/ddl-*.sql 摘取, 跳过 Quartz)
输出: 同目录下三件产物
  - yiti-schema.xlsx       : 索引 sheet + 每模块一个 sheet
  - yiti-er-diagram.md     : 跨模块总览 + 每模块 Mermaid erDiagram
  - yiti-er-diagram.dot    : Graphviz 源文件 (dot -Tpng/-Tsvg 渲染)

执行:
  python3 yiti/docs/schema/_gen_schema_doc.py
"""

import os
from collections import OrderedDict
from typing import List, Tuple

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter


# ─────────────────────────────────────────────────────────────────────────────
# 1. 模块定义 (顺序即依赖低 → 高: auth → governance → workflow → portal →
#    customer → bizapp → performance → report)
# ─────────────────────────────────────────────────────────────────────────────
MODULES = OrderedDict([
    ("auth",        {"zh": "认证授权中心",   "code": "auth-permission-center",
                     "desc": "用户、角色、资源、机构与 RBAC 关联;数据范围(BizScope)的核心域"}),
    ("governance",  {"zh": "系统治理中心",   "code": "system-governance-center",
                     "desc": "字典、配置、工作日历、任务调度、审计、通知、文件、附件等横切支撑"}),
    ("workflow",    {"zh": "工作流中心",     "code": "workflow-center",
                     "desc": "Flowable 嵌入流程引擎之上的业务流程映射、节点候选人/表单/超时配置"}),
    ("portal",      {"zh": "门户内容中心",   "code": "portal-content-center",
                     "desc": "网址导航、工作台快捷入口、通讯录、产品、文档"}),
    ("customer",    {"zh": "客户营销中心",   "code": "customer-marketing-center",
                     "desc": "客户主档、标签、线索、导入批次、认领、触达任务/日志"}),
    ("bizapp",      {"zh": "业务申请中心",   "code": "business-application-center",
                     "desc": "资产投放申请(LOAN) + 中场支持申请(SUPPORT,多产品拆单+双视图)"}),
    ("performance", {"zh": "绩效引擎中心",   "code": "performance-engine-center",
                     "desc": "数据版本、指标定义/引用、KPI 方案/项、目标方案/值、宽表 200 槽位、KPI 结果、客户业绩分配/调整、目标修正"}),
    ("report",      {"zh": "报表分析中心",   "code": "report-analytics-center",
                     "desc": "动态查询保存方案、SQL 探查历史、快照(预留)、报表异步导出"}),
])


# ─────────────────────────────────────────────────────────────────────────────
# 2. 表结构元数据
# 每张表用 4 元组: (TABLE_NAME, comment_zh, pk_cols, columns, indexes)
#   columns: [(name, type, nullable, default, comment)]
#   indexes: [(name, type, cols, comment)]  type in {UK, IDX}
# ─────────────────────────────────────────────────────────────────────────────

# 通用审计列(不重复书写)
def _audit_cols(deleted: bool = True, by_field_pair: Tuple[str, str] = ("created_by", "updated_by")):
    cb, ub = by_field_pair
    base = [
        (cb,           "varchar(32)",  True,  None,                 "创建人"),
        ("created_time","datetime",    True,  "CURRENT_TIMESTAMP",  "创建时间"),
        (ub,           "varchar(32)",  True,  None,                 "更新人"),
        ("updated_time","datetime",    True,  "CURRENT_TIMESTAMP",  "更新时间(ON UPDATE CURRENT_TIMESTAMP)"),
    ]
    if deleted:
        base.append(("deleted",         "tinyint(1)",  False, "0",                  "逻辑删除: 0=未删, 1=已删"))
    return base


# Wide-table 200 槽位列生成器
def _val_slots(n: int = 200):
    return [(f"val_{i}", "decimal(20,4)", True, None, f"指标槽位 #{i} (映射 PERF_METRIC_DEF.val_slot)") for i in range(1, n + 1)]


# ─── auth ───────────────────────────────────────────────────────────────────
TABLES_AUTH = [
    ("PT_USER", "人员表(用户)", ["USER_ID"], [
        ("USER_ID",          "varchar(50)",  False, None, "用户ID(工号)"),
        ("USERNAME",         "varchar(200)", False, None, "用户姓名"),
        ("USERCHNNAME",      "varchar(200)", False, None, "用户中文姓名"),
        ("PWD",              "varchar(64)",  True,  None, "密码(BCrypt 加密)"),
        ("EMAIL",            "varchar(100)", True,  None, "邮箱"),
        ("ISEXPIRED",        "int",          True,  "0",  "1 过期 0 未过期"),
        ("ISLOCKED",         "int",          True,  "0",  "1 被锁 0 未被锁"),
        ("PASS_WRONG_COUNT", "int",          True,  "0",  "密码错误次数"),
        ("ISENABLED",        "int",          True,  "1",  "0 启用 1 未启用"),
        ("CREATE_TIME",      "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("CREATE_AUTHOR",    "varchar(50)",  True,  None, "创建者"),
        ("UPDATE_TIME",      "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("UPDATE_AUTHOR",    "varchar(50)",  True,  None, "更新者"),
        ("REMARK",           "varchar(100)", True,  None, "备注"),
        ("PWD_UPDATE_TIME",  "datetime",     True,  None, "密码更新时间"),
    ], []),

    ("PT_ROLE", "角色表", ["ROLE_ID"], [
        ("ROLE_ID",      "varchar(50)",  False, None,        "角色ID"),
        ("ROLE_CODE",    "varchar(50)",  False, None,        "角色编码"),
        ("ROLE_CHNAME",  "varchar(100)", False, None,        "角色中文名"),
        ("RECORD_STATUS","int",          True,  "0",         "0 可用 1 不可用"),
        ("SYS_CODE",     "varchar(10)",  True,  "PLATFORM",  "系统编号"),
        ("CREATE_TIME",  "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("CREATE_USER",  "varchar(50)",  True,  None,        "创建人"),
        ("UPDATE_TIME",  "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("UPDATE_USER",  "varchar(50)",  True,  None,        "更新人"),
        ("REMARK",       "varchar(100)", True,  None,        "备注"),
    ], []),

    ("PT_RESOURCE", "资源表(菜单/接口)", ["RESOURCE_ID"], [
        ("RESOURCE_ID",        "varchar(20)",  False, None,        "资源ID"),
        ("RESOURCE_URL",       "varchar(256)", False, None,        "资源URL(支持 Ant 通配符)"),
        ("RESOURCE_METHOD",    "varchar(10)",  False, None,        "请求方法 GET/POST/PUT/DELETE/* "),
        ("MENU_NAME",          "varchar(256)", False, None,        "菜单名称"),
        ("MENU_ICON_URL",      "varchar(256)", True,  None,        "图标路径"),
        ("MENU_RANK_NO",       "int",          True,  "0",         "菜单排序"),
        ("ISMENU",             "int",          True,  "0",         "是否菜单 0 是 1 不是"),
        ("MENU_ENDFLAG",       "varchar(10)",  True,  "0",         "是否叶子节点 1 是 0 否"),
        ("PARENT_RESOURCE_ID", "varchar(60)",  True,  None,        "上级资源ID(自关联)"),
        ("STATUS",             "int",          True,  "0",         "0 启用 1 不启用"),
        ("SYS_CODE",           "varchar(10)",  True,  "PLATFORM",  "系统编号"),
        ("CREATE_TIME",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("CREATE_USER",        "varchar(50)",  True,  None,        "创建人"),
        ("UPDATE_TIME",        "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("UPDATE_USER",        "varchar(50)",  True,  None,        "更新人"),
        ("REMARK",             "varchar(100)", True,  None,        "备注"),
    ], [
        ("uk_pt_resource_url_method_sys", "UK",  ["RESOURCE_URL","RESOURCE_METHOD","SYS_CODE"], "URL+方法+系统 唯一"),
        ("idx_pt_resource_status",        "IDX", ["STATUS","SYS_CODE"], "按状态扫描"),
    ]),

    ("PT_USER_ROLE", "用户角色关联表", ["USER_ID","ROLE_ID"], [
        ("USER_ID",         "varchar(50)", False, None, "用户ID"),
        ("ROLE_ID",         "varchar(50)", False, None, "角色ID"),
        ("DEFAULT_ASSIGN",  "int",         True,  "0",  "默认分配"),
        ("INHERIT_ASSIGN",  "int",         True,  "0",  "用户组角色继承"),
        ("GROUP_ASSING",    "int",         True,  "0",  "角色组分配"),
        ("CREATE_TIME",     "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [("idx_role_id","IDX",["ROLE_ID"],"按角色反查用户")]),

    ("PT_ROLE_RESOURCE", "角色资源关联表", ["ID"], [
        ("ID",          "varchar(32)", False, None,        "主键ID"),
        ("ROLE_ID",     "varchar(50)", False, None,        "角色ID"),
        ("RESOURCE_ID", "varchar(20)", False, None,        "资源ID"),
        ("SYS_CODE",    "varchar(10)", True,  "PLATFORM",  "系统编号"),
        ("CREATE_TIME", "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("idx_role_id",     "IDX", ["ROLE_ID"],     "按角色查授权"),
        ("idx_resource_id", "IDX", ["RESOURCE_ID"], "按资源反查角色"),
    ]),

    ("PT_ROLE_BIZ_SCOPE", "角色业务范围表(数据范围核心)", ["ID"], [
        ("ID",            "varchar(32)", False, None, "主键ID"),
        ("ROLE_ID",       "varchar(50)", False, None, "角色ID"),
        ("BIZ_TYPE",      "varchar(50)", False, None, "业务类型 NAV/PRODUCT/LEAD/CUSTOMER 等"),
        ("DATA_SCOPE",    "varchar(50)", False, None, "数据范围 SELF/SELF_CREATED/SELF_ASSIGNED/ORG/ORG_SUBTREE/ALL/WORKFLOW_PARTICIPANT"),
        ("RECORD_STATUS", "int",         True,  "0",  "0 可用 1 不可用"),
        ("CREATE_TIME",   "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("CREATE_USER",   "varchar(50)", True,  None, "创建人"),
        ("UPDATE_TIME",   "datetime",    True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("UPDATE_USER",   "varchar(50)", True,  None, "更新人"),
        ("REMARK",        "varchar(100)",True,  None, "备注"),
    ], [
        ("uk_pt_role_biz_scope_role_biz", "UK", ["ROLE_ID","BIZ_TYPE"], "角色×业务类型 唯一"),
        ("idx_role_id",                   "IDX",["ROLE_ID"], "按角色查"),
        ("idx_biz_type",                  "IDX",["BIZ_TYPE"],"按业务类型查"),
    ]),

    ("EXT_ORG_INFO", "机构表", ["ID"], [
        ("ID",                "int",          False, "AUTO_INCREMENT", "机构主键"),
        ("ORG_CODE",          "varchar(20)",  False, None, "机构编号(业务键)"),
        ("ORG_NAME",          "varchar(200)", False, None, "机构名称"),
        ("ORG_LEVEL",         "int",          True,  None, "机构等级 1 总行 2 分行 3 支行"),
        ("P_ID",              "varchar(20)",  True,  None, "上级机构编码(自关联,树形)"),
        ("ORGAN_STATE",       "int",          True,  "0",  "状态 0 启用 1 删除"),
        ("ADM_DIVISION_CODE", "varchar(20)",  True,  None, "行政区划代码"),
        ("ADM_DIVISION_NAME", "varchar(255)", True,  None, "行政区划名称"),
        ("CREATE_TIME",       "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("CREATE_USER",       "varchar(50)",  True,  None, "创建人"),
    ], [
        ("uk_ext_org_info_org_code","UK",["ORG_CODE"],"机构编码唯一"),
        ("idx_p_id",                "IDX",["P_ID"],   "按父节点查子树"),
    ]),

    ("EXT_USER_ORG", "用户机构关联表", ["USER_ID","ORG_CODE"], [
        ("USER_ID",     "varchar(50)", False, None, "用户ID"),
        ("ORG_CODE",    "varchar(20)", False, None, "机构编码"),
        ("CREATE_TIME", "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [("idx_org_code","IDX",["ORG_CODE"],"按机构反查用户")]),
]


# ─── governance ─────────────────────────────────────────────────────────────
TABLES_GOVERNANCE = [
    ("SYS_DICT", "字典表(类型+项合并存储)", ["id"], [
        ("id",          "varchar(32)",  False, None, "字典ID"),
        ("dict_type",   "varchar(100)", False, None, "字典类型"),
        ("dict_code",   "varchar(100)", False, None, "字典编码"),
        ("dict_label",  "varchar(200)", False, None, "字典标签"),
        ("dict_value",  "varchar(500)", False, None, "字典值"),
        ("sort_order",  "int(11)",      True,  "0",  "排序号"),
        ("status",      "varchar(20)",  True,  "ACTIVE", "ACTIVE/DISABLED"),
        ("remark",      "varchar(500)", True,  None, "备注"),
        *_audit_cols(deleted=False),
    ], [
        ("uk_dict_type_code", "UK", ["dict_type","dict_code"], "类型+编码唯一"),
        ("idx_dict_type",     "IDX",["dict_type"], "按类型查"),
    ]),

    ("SYS_DICT_ITEM", "字典项表(预留拆分用)", ["id"], [
        ("id",          "varchar(32)",  False, None, "字典项ID"),
        ("dict_type",   "varchar(100)", False, None, "字典类型(关联 sys_dict.dict_type)"),
        ("item_code",   "varchar(100)", False, None, "字典项编码"),
        ("item_label",  "varchar(200)", False, None, "字典项标签"),
        ("item_value",  "varchar(500)", False, None, "字典项值"),
        ("sort_order",  "int(11)",      True,  "0",  "排序号"),
        ("status",      "varchar(20)",  True,  "ACTIVE", "ACTIVE/DISABLED"),
        ("remark",      "varchar(500)", True,  None, "备注"),
        *_audit_cols(deleted=False),
    ], [
        ("uk_dict_type_item_code", "UK", ["dict_type","item_code"], "类型+项编码唯一"),
        ("idx_dict_type",          "IDX",["dict_type"], "按类型查"),
    ]),

    ("SYS_CALENDAR_DAY", "工作日历(按天)", ["day"], [
        ("day",        "date",        False, None, "日期"),
        ("is_workday", "tinyint(1)",  False, "1",  "1=工作日 0=休息日"),
        ("remark",     "varchar(500)",True,  None, "备注"),
        *_audit_cols(deleted=False),
    ], []),

    ("SYS_JOB_CONF", "任务调度配置(Quartz 包装)", ["id"], [
        ("id",                  "varchar(32)",  False, None,        "任务ID"),
        ("job_key",             "varchar(100)", False, None,        "任务KEY(唯一)"),
        ("job_name",            "varchar(200)", False, None,        "任务名称"),
        ("cron_expr",           "varchar(100)", False, None,        "Cron 表达式"),
        ("quartz_job_class",    "varchar(255)", False, "''",        "Quartz 包装 Job 类全限定名(V1.6 新增)"),
        ("misfire_policy",      "varchar(32)",  False, "FIRE_ONCE_NOW", "misfire 策略(V1.6 新增)"),
        ("status",              "varchar(20)",  False, "ACTIVE",    "ACTIVE/PAUSED"),
        ("allow_manual_trigger","tinyint(1)",   False, "1",         "是否允许手动触发"),
        ("last_run_time",       "datetime",     True,  None,        "上次执行时间"),
        ("next_run_time",       "datetime",     True,  None,        "下次执行时间"),
        ("remark",              "varchar(500)", True,  None,        "备注"),
        *_audit_cols(deleted=False),
    ], [
        ("uk_job_key", "UK", ["job_key"], "任务KEY唯一"),
        ("idx_status","IDX",["status"],   "按状态扫描"),
    ]),

    ("SYS_JOB_RUN_LOG", "任务执行日志", ["id"], [
        ("id",                  "varchar(32)",  False, None,        "执行日志ID"),
        ("job_id",              "varchar(32)",  False, None,        "任务ID(逻辑外键→sys_job_conf.id)"),
        ("trigger_type",        "varchar(20)",  False, None,        "SCHEDULED/MANUAL"),
        ("reason",              "varchar(500)", True,  None,        "原因(手动触发必填)"),
        ("start_time",          "datetime",     True,  "CURRENT_TIMESTAMP", "开始时间"),
        ("end_time",            "datetime",     True,  None,        "结束时间"),
        ("scheduled_fire_time", "datetime(3)",  True,  None,        "Quartz 计划触发时间(V1.6 新增)"),
        ("status",              "varchar(20)",  False, "RUNNING",   "RUNNING/SUCCESS/FAILED"),
        ("error_msg",           "longtext",     True,  None,        "错误信息"),
        ("created_by",          "varchar(32)",  True,  None,        "触发人"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("idx_job_id",       "IDX", ["job_id"],       "按任务查"),
        ("idx_created_time", "IDX", ["created_time"], "按时间扫描"),
    ]),

    ("SYS_CONFIG_KV", "系统配置 KV", ["id"], [
        ("id",          "varchar(32)",  False, None,      "配置ID"),
        ("config_key",  "varchar(200)", False, None,      "配置键(唯一)"),
        ("config_value","longtext",     True,  None,      "配置值"),
        ("value_type",  "varchar(20)",  False, "STRING",  "STRING/JSON/NUMBER/BOOL"),
        ("status",      "varchar(20)",  False, "ACTIVE",  "ACTIVE/DISABLED"),
        ("remark",      "varchar(500)", True,  None,      "备注"),
        *_audit_cols(deleted=False),
    ], [
        ("uk_config_key", "UK", ["config_key"], "配置键唯一"),
        ("idx_status",    "IDX",["status"],     "按状态扫描"),
    ]),

    ("USER_NOTIFICATION", "用户通知表", ["id"], [
        ("id",           "varchar(32)",  False, None, "通知ID"),
        ("emp_id",       "varchar(32)",  False, None, "接收人工号(逻辑外键→addrbook_employee.emp_id)"),
        ("title",        "varchar(200)", False, None, "通知标题"),
        ("content",      "text",         True,  None, "通知内容"),
        ("notify_type",  "varchar(50)",  True,  None, "SYSTEM/WORKFLOW/BUSINESS"),
        ("biz_type",     "varchar(50)",  True,  None, "业务类型"),
        ("biz_id",       "varchar(100)", True,  None, "业务ID"),
        ("link_url",     "varchar(500)", True,  None, "跳转链接"),
        ("is_read",      "tinyint(1)",   True,  "0",  "0 未读 1 已读"),
        ("read_time",    "datetime",     True,  None, "阅读时间"),
        ("created_time", "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("idx_emp_id_read",  "IDX", ["emp_id","is_read"], "按人查未读"),
        ("idx_created_time", "IDX", ["created_time"],     "按时间扫描"),
    ]),

    ("FILE_OBJECT", "文件对象表(对象存储元数据)", ["id"], [
        ("id",            "varchar(32)",  False, None, "文件对象ID"),
        ("file_name",     "varchar(255)", False, None, "文件名"),
        ("file_size",     "bigint(20)",   True,  None, "文件大小(字节)"),
        ("file_type",     "varchar(100)", True,  None, "文件类型(MIME)"),
        ("storage_path",  "varchar(500)", False, None, "存储路径(MinIO Object Key)"),
        ("bucket_name",   "varchar(100)", True,  None, "存储桶"),
        ("md5_hash",      "varchar(64)",  True,  None, "MD5"),
        ("uploaded_by",   "varchar(32)",  True,  None, "上传人"),
        ("uploaded_time", "datetime",     True,  "CURRENT_TIMESTAMP", "上传时间"),
    ], [("idx_uploaded_by","IDX",["uploaded_by"],"按上传人查")]),

    ("BIZ_FILE_REL", "业务-附件关联表", ["id"], [
        ("id",             "varchar(32)",  False, None, "关联ID"),
        ("biz_type",       "varchar(32)",  False, None, "业务类型(LOAN/SUPPORT/TOUCH 等)"),
        ("biz_id",         "varchar(100)", False, None, "业务ID(字符串)"),
        ("file_object_id", "varchar(32)",  False, None, "文件对象ID(逻辑外键→file_object.id)"),
        ("file_role",      "varchar(32)",  True,  None, "用途 ATTACHMENT/PHOTO/..."),
        ("created_by",     "varchar(32)",  True,  None, "创建人"),
        ("created_time",   "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_biz_file","UK", ["biz_type","biz_id","file_object_id"], "防重复关联"),
        ("idx_biz",    "IDX",["biz_type","biz_id"], "按业务查附件"),
        ("idx_file",   "IDX",["file_object_id"],    "按文件反查业务"),
    ]),

    ("AUDIT_LOG", "审计日志表(高危操作 + 链路追踪)", ["id"], [
        ("id",              "varchar(32)",  False, None, "日志ID"),
        ("trace_id",        "varchar(64)",  True,  None, "链路追踪ID"),
        ("emp_id",          "varchar(32)",  False, None, "操作人工号"),
        ("emp_name",        "varchar(100)", True,  None, "操作人姓名"),
        ("biz_type",        "varchar(50)",  True,  None, "业务类型"),
        ("biz_action",      "varchar(50)",  True,  None, "业务动作"),
        ("resource_url",    "varchar(500)", True,  None, "资源URL"),
        ("request_method",  "varchar(20)",  True,  None, "请求方法"),
        ("request_params",  "text",         True,  None, "请求参数(脱敏)"),
        ("response_status", "int(11)",      True,  None, "响应状态码"),
        ("error_msg",       "text",         True,  None, "错误信息"),
        ("ip_address",      "varchar(50)",  True,  None, "IP地址"),
        ("user_agent",      "varchar(500)", True,  None, "User-Agent"),
        ("execution_time",  "int(11)",      True,  None, "执行耗时(ms)"),
        ("reason",          "varchar(500)", True,  None, "操作原因(高危必填)"),
        ("created_time",    "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("idx_emp_id",       "IDX", ["emp_id"],       "按人查"),
        ("idx_biz_type",     "IDX", ["biz_type"],     "按业务类型查"),
        ("idx_created_time", "IDX", ["created_time"], "按时间扫描"),
        ("idx_trace_id",     "IDX", ["trace_id"],     "按 traceId 串接全链"),
    ]),
]


# ─── workflow ───────────────────────────────────────────────────────────────
TABLES_WORKFLOW = [
    ("BIZ_PROCESS_MAP", "业务流程映射表(business_key↔Flowable 实例)", ["id"], [
        ("id",                     "varchar(32)",  False, None, "映射ID"),
        ("business_key",           "varchar(100)", False, None, "业务键(BIZ_TYPE:{id})"),
        ("biz_type",               "varchar(50)",  False, None, "业务类型"),
        ("biz_id",                 "varchar(100)", False, None, "业务ID"),
        ("process_definition_key", "varchar(100)", False, None, "流程定义KEY"),
        ("process_instance_id",    "varchar(64)",  False, None, "Flowable 流程实例ID"),
        ("start_user",             "varchar(32)",  False, None, "发起人工号"),
        ("current_assignee",       "varchar(32)",  True,  None, "当前处理人工号"),
        ("candidate_groups",       "text",         True,  None, "候选组列表(JSON)"),
        ("process_status",         "varchar(50)",  True,  "RUNNING", "RUNNING/COMPLETED/CANCELLED"),
        ("title",                  "varchar(200)", True,  None, "流程标题"),
        ("start_time",             "datetime",     True,  "CURRENT_TIMESTAMP", "发起时间"),
        ("end_time",               "datetime",     True,  None, "结束时间"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_business_key",     "UK", ["business_key"],     "business_key 唯一"),
        ("uk_process_instance", "UK", ["process_instance_id"], "流程实例ID 唯一"),
        ("idx_biz_type_id",     "IDX",["biz_type","biz_id"], "按业务定位"),
        ("idx_start_user",      "IDX",["start_user"],       "按发起人查"),
        ("idx_current_assignee","IDX",["current_assignee"], "按当前处理人查"),
        ("idx_status",          "IDX",["process_status"],   "按状态查"),
    ]),

    ("WF_NODE_CANDIDATE_CONF", "流程节点候选人配置表", ["id"], [
        ("id",                     "varchar(32)",  False, None, "配置ID"),
        ("process_definition_key", "varchar(100)", False, None, "流程定义KEY"),
        ("node_key",               "varchar(100)", False, None, "节点KEY"),
        ("candidate_type",         "varchar(50)",  False, None, "ROLE/ORG/USER"),
        ("candidate_value",        "text",         True,  None, "候选值(JSON)"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_pd_node_type",   "UK", ["process_definition_key","node_key","candidate_type"], "PD+节点+类型 唯一"),
        ("idx_process_node",  "IDX",["process_definition_key","node_key"], "按节点查候选"),
    ]),

    ("WF_NODE_FORM_CONF", "流程节点表单配置表", ["id"], [
        ("id",                     "varchar(32)",  False, None, "配置ID"),
        ("process_definition_key", "varchar(100)", False, None, "流程定义KEY"),
        ("node_key",               "varchar(100)", False, None, "节点KEY"),
        ("form_fields",            "text",         True,  None, "表单字段配置(JSON)"),
        ("editable_fields",        "text",         True,  None, "可编辑字段(JSON)"),
        ("required_fields",        "text",         True,  None, "必填字段(JSON)"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [("uk_process_node","UK",["process_definition_key","node_key"],"PD+节点 唯一")]),

    ("WF_TIMEOUT_RULE", "流程超时规则表", ["id"], [
        ("id",                     "varchar(32)",  False, None, "规则ID"),
        ("process_definition_key", "varchar(100)", False, None, "流程定义KEY"),
        ("node_key",               "varchar(100)", False, None, "节点KEY"),
        ("timeout_hours",          "int(11)",      False, None, "超时小时数"),
        ("warning_hours",          "int(11)",      True,  None, "预警小时数"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [("uk_process_node","UK",["process_definition_key","node_key"],"PD+节点 唯一")]),
]


# ─── portal ─────────────────────────────────────────────────────────────────
TABLES_PORTAL = [
    ("PORTAL_NAV", "网址导航表", ["id"], [
        ("id",          "varchar(32)",  False, None, "导航ID"),
        ("nav_name",    "varchar(100)", False, None, "导航名称"),
        ("nav_url",     "varchar(500)", False, None, "导航URL"),
        ("nav_icon",    "varchar(100)", True,  None, "图标"),
        ("nav_category","varchar(50)",  True,  None, "分类"),
        ("sort_order",  "int(11)",      True,  "0",  "排序号"),
        ("status",      "varchar(20)",  True,  "ACTIVE", "ACTIVE/DISABLED"),
        *_audit_cols(deleted=False),
    ], []),

    ("PORTAL_SHORTCUT", "工作台快捷入口表", ["id"], [
        ("id",            "varchar(32)",  False, None, "快捷入口ID"),
        ("shortcut_name", "varchar(100)", False, None, "快捷入口名称"),
        ("shortcut_url",  "varchar(500)", False, None, "跳转URL"),
        ("shortcut_icon", "varchar(100)", True,  None, "图标"),
        ("shortcut_type", "varchar(50)",  True,  None, "SYSTEM/CUSTOM"),
        ("target_type",   "varchar(50)",  True,  None, "INTERNAL/EXTERNAL"),
        ("emp_id",        "varchar(32)",  True,  None, "所属用户工号(自定义快捷入口)"),
        ("sort_order",    "int(11)",      True,  "0",  "排序号"),
        ("status",        "varchar(20)",  True,  "ACTIVE","ACTIVE/DISABLED"),
        *_audit_cols(deleted=False),
    ], [
        ("idx_emp_id","IDX",["emp_id"],"按用户查自定义快捷"),
        ("idx_type",  "IDX",["shortcut_type"],"按类型查"),
    ]),

    ("ADDRBOOK_EMPLOYEE", "通讯录员工表(全行人员主数据)", ["emp_id"], [
        ("emp_id",                 "varchar(32)",  False, None, "员工工号(主键)"),
        ("emp_name",               "varchar(100)", False, None, "员工姓名"),
        ("mobile",                 "varchar(20)",  True,  None, "手机号"),
        ("email",                  "varchar(100)", True,  None, "邮箱"),
        ("org_code",               "varchar(50)",  True,  None, "所属机构代码(逻辑外键→ext_org_info.org_code)"),
        ("org_name",               "varchar(200)", True,  None, "所属机构名称(冗余,反规范化)"),
        ("position",               "varchar(100)", True,  None, "岗位"),
        ("self_desc",              "text",         True,  None, "自我描述"),
        ("responsible_product_ids","text",         True,  None, "负责产品ID列表(JSON 数组)"),
        ("status",                 "varchar(20)",  True,  "ACTIVE", "ACTIVE 在职 / RESIGNED 离职"),
        ("maintainer_emp_id",      "varchar(32)",  True,  None, "维护人工号(自关联)"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("deleted",                "int(11)",      True,  "0",  "0 未删 1 已删"),
    ], [
        ("idx_org_code",  "IDX",["org_code"],          "按机构查"),
        ("idx_maintainer","IDX",["maintainer_emp_id"], "按维护人查"),
        ("idx_deleted",   "IDX",["deleted"],           "过滤已删"),
    ]),

    ("PRODUCT_INFO", "产品信息表", ["id"], [
        ("id",                          "varchar(64)",  False, None, "产品ID"),
        ("product_code",                "varchar(64)",  False, None, "产品代码"),
        ("product_name",                "varchar(255)", False, None, "产品名称"),
        ("product_category",            "varchar(64)",  False, None, "产品类别"),
        ("description",                 "text",         True,  None, "产品描述"),
        ("support_for_support_request", "tinyint(1)",   False, "0",  "是否支持中场支持"),
        ("owner_org_id",                "varchar(50)",  True,  None, "归属组织(维护组织)"),
        ("product_dept_org_code",       "varchar(50)",  True,  None, "产品部门 ORG_CODE(逻辑外键→ext_org_info.org_code)"),
        ("file_object_id",              "varchar(32)",  True,  None, "主附件文件ID(逻辑外键→file_object.id)"),
        ("responsible_emp_ids",         "text",         True,  None, "负责人列表(JSON,反向关联通讯录)"),
        ("status",                      "varchar(32)",  False, None, "产品状态"),
        *_audit_cols(deleted=True),
    ], [
        ("idx_product_code","IDX",["product_code"],     "按产品代码查"),
        ("idx_category",    "IDX",["product_category"], "按类别查"),
        ("idx_status",      "IDX",["status"],           "按状态查"),
        ("idx_deleted",     "IDX",["deleted"],          "过滤已删"),
    ]),

    ("DOC_INFO", "文档信息表", ["id"], [
        ("id",            "varchar(32)",  False, None, "文档ID"),
        ("doc_title",     "varchar(200)", False, None, "文档标题"),
        ("doc_category",  "varchar(50)",  True,  None, "文档分类"),
        ("file_object_id","varchar(32)",  True,  None, "文件对象ID(逻辑外键→file_object.id)"),
        ("status",        "varchar(20)",  True,  "ACTIVE","ACTIVE/DISABLED"),
        *_audit_cols(deleted=False),
    ], []),
]


# ─── customer ───────────────────────────────────────────────────────────────
TABLES_CUSTOMER = [
    ("CUST_TAG", "客户标签表", ["id"], [
        ("id",            "varchar(32)",  False, None, "标签ID"),
        ("tag_name",      "varchar(100)", False, None, "标签名称"),
        ("tag_category",  "varchar(50)",  True,  None, "标签分类"),
        ("tag_priority",  "int(11)",      False, "0",  "优先级(数字越大越靠前)"),
        ("description",   "varchar(500)", True,  None, "标签描述"),
        ("status",        "varchar(20)",  True,  "ACTIVE","ACTIVE/DISABLED"),
        *_audit_cols(deleted=True),
    ], [
        ("uk_tag_name", "UK", ["tag_name"], "标签名称唯一"),
        ("idx_status",  "IDX",["status"],   "按状态查"),
    ]),

    ("CUST_TAG_REL", "客户-标签关联表", ["id"], [
        ("id",           "varchar(32)", False, None, "关联ID"),
        ("cust_id",      "varchar(32)", False, None, "客户ID(逻辑外键→cust_master.id)"),
        ("tag_id",       "varchar(32)", False, None, "标签ID(逻辑外键→cust_tag.id)"),
        ("created_by",   "varchar(32)", True,  None, "创建人"),
        ("created_time", "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_cust_tag", "UK", ["cust_id","tag_id"], "客户×标签 唯一"),
        ("idx_tag_id",  "IDX",["tag_id"], "按标签反查客户"),
    ]),

    ("CUST_LEAD", "客户线索表(支持版本化 CRUD)", ["id"], [
        ("id",                     "varchar(32)",  False, None, "线索ID"),
        ("lead_no",                "varchar(100)", False, None, "线索编号(展示)"),
        ("lead_op",                "varchar(20)",  False, "CREATE", "CREATE/UPDATE/DELETE"),
        ("source_cust_id",         "varchar(32)",  True,  None, "关联客户ID(UPDATE/DELETE 必填)"),
        ("prev_lead_id",           "varchar(32)",  True,  None, "上一版本线索ID(自关联)"),
        ("version_no",             "int(11)",      False, "1",  "版本号(从1开始)"),
        ("is_latest",              "tinyint(1)",   False, "1",  "1 最新 0 历史"),
        ("cust_name",              "varchar(200)", False, None, "客户名称"),
        ("unified_credit_code",    "varchar(50)",  True,  None, "统一社会信用代码"),
        ("tag_ids",                "text",         True,  None, "标签ID列表(JSON)"),
        ("contact_person",         "varchar(100)", True,  None, "联系人"),
        ("contact_mobile",         "varchar(20)",  True,  None, "联系电话"),
        ("industry",               "varchar(100)", True,  None, "所属行业"),
        ("group_type",             "varchar(50)",  True,  None, "集团类型(字典)"),
        ("customer_type",          "varchar(50)",  True,  None, "客户类型(字典)"),
        ("is_keystone",            "tinyint(1)",   True,  None, "是否基石客户"),
        ("enterprise_type",        "varchar(50)",  True,  None, "企业类型(字典)"),
        ("group_name",             "varchar(200)", True,  None, "集团名称"),
        ("is_account_opened",      "tinyint(1)",   True,  None, "是否开户"),
        ("customer_desc",          "text",         True,  None, "客户说明"),
        ("credit_amount",          "decimal(20,4)",True,  None, "授信金额"),
        ("credit_exposure_amount", "decimal(20,4)",True,  None, "授信敞口金额"),
        ("lead_source",            "varchar(50)",  True,  None, "线索来源"),
        ("lead_status",            "varchar(50)",  True,  "DRAFT", "DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED"),
        ("owner_org_id",           "varchar(50)",  False, None, "归属机构代码"),
        ("assigned_to",            "varchar(50)",  True,  None, "分配用户"),
        ("created_by",             "varchar(32)",  False, None, "创建人工号"),
        ("business_key",           "varchar(100)", True,  None, "流程业务键(LEAD:{id})"),
        ("import_batch_id",        "varchar(32)",  True,  None, "导入批次ID(逻辑外键→lead_import_batch.id)"),
        ("process_instance_id",    "varchar(64)",  True,  None, "Flowable 流程实例ID"),
        ("remark",                 "text",         True,  None, "备注"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",             "varchar(32)",  True,  None, "更新人"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("deleted",                "tinyint(4)",   True,  "0",  "删除标记"),
    ], [
        ("uk_lead_no",            "UK", ["lead_no"], "线索编号唯一"),
        ("idx_owner_org",         "IDX",["owner_org_id"], "按机构查"),
        ("idx_created_by",        "IDX",["created_by"],   "按创建人查"),
        ("idx_business_key",      "IDX",["business_key"], "按流程键查"),
        ("idx_status",            "IDX",["lead_status"],  "按状态查"),
        ("idx_import_batch",      "IDX",["import_batch_id"], "按批次查"),
        ("idx_cust_name",         "IDX",["cust_name"],    "按客户名查"),
        ("idx_unified_credit_code","IDX",["unified_credit_code"], "按统一社会信用代码查"),
        ("idx_source_cust",       "IDX",["source_cust_id"], "按关联客户查"),
        ("idx_lead_op_status",    "IDX",["lead_op","lead_status"], "按操作类型+状态查"),
        ("idx_is_latest",         "IDX",["is_latest"],    "过滤最新版本"),
    ]),

    ("LEAD_IMPORT_BATCH", "线索导入批次表", ["id"], [
        ("id",                  "varchar(32)",  False, None, "批次ID"),
        ("batch_no",            "varchar(64)",  False, None, "批次号(展示)"),
        ("source_file_name",    "varchar(255)", True,  None, "源文件名"),
        ("file_md5",            "varchar(64)",  True,  None, "文件MD5"),
        ("status",              "varchar(20)",  False, "CREATED","CREATED/PENDING_APPROVAL/APPROVED/REJECTED"),
        ("total_row_count",     "int(11)",      False, "0",  "总行数"),
        ("error_row_count",     "int(11)",      False, "0",  "错误行数"),
        ("error_summary",       "varchar(512)", True,  None, "错误摘要"),
        ("error_file_object_id","varchar(32)",  True,  None, "错误明细文件ID(逻辑外键→file_object.id)"),
        ("business_key",        "varchar(100)", True,  None, "流程业务键(LEAD:IMP_{id})"),
        ("process_instance_id", "varchar(64)",  True,  None, "流程实例ID"),
        ("owner_org_id",        "varchar(50)",  False, None, "归属机构"),
        ("created_by",          "varchar(32)",  False, None, "创建人"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",          "varchar(32)",  True,  None, "更新人"),
        ("updated_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_batch_no",      "UK", ["batch_no"],     "批次号唯一"),
        ("idx_owner_org",    "IDX",["owner_org_id"], "按机构查"),
        ("idx_status",       "IDX",["status"],       "按状态查"),
        ("idx_created_time", "IDX",["created_time"], "按时间扫描"),
    ]),

    ("CUST_MASTER", "客户主档表(去重后的客户实体)", ["id"], [
        ("id",                     "varchar(32)",  False, None, "客户ID"),
        ("cust_no",                "varchar(100)", False, None, "客户编号"),
        ("cust_name",              "varchar(200)", False, None, "客户名称"),
        ("unified_credit_code",    "varchar(50)",  True,  None, "统一社会信用代码"),
        ("contact_person",         "varchar(100)", True,  None, "联系人"),
        ("contact_mobile",         "varchar(20)",  True,  None, "联系电话"),
        ("industry",               "varchar(100)", True,  None, "所属行业"),
        ("group_type",             "varchar(50)",  True,  None, "集团类型"),
        ("customer_type",          "varchar(50)",  True,  None, "客户类型"),
        ("is_keystone",            "tinyint(1)",   True,  None, "是否基石客户"),
        ("enterprise_type",        "varchar(50)",  True,  None, "企业类型"),
        ("group_name",             "varchar(200)", True,  None, "集团名称"),
        ("is_account_opened",      "tinyint(1)",   True,  None, "是否开户"),
        ("customer_desc",          "text",         True,  None, "客户说明"),
        ("credit_amount",          "decimal(20,4)",True,  None, "授信金额"),
        ("credit_exposure_amount", "decimal(20,4)",True,  None, "授信敞口"),
        ("owner_org_id",           "varchar(50)",  True,  None, "来源机构(不承载可见性)"),
        ("lead_id",                "varchar(32)",  True,  None, "来源线索ID(逻辑外键→cust_lead.id)"),
        ("status",                 "varchar(20)",  True,  "ACTIVE","ACTIVE/INACTIVE"),
        ("deleted",                "tinyint(1)",   False, "0",  "0 未删 1 已删"),
        ("created_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",           "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_cust_no",            "UK", ["cust_no"],   "客户编号唯一"),
        ("uk_cust_name",          "UK", ["cust_name"], "客户名称唯一"),
        ("idx_lead_id",           "IDX",["lead_id"],   "按线索查"),
        ("idx_unified_credit_code","IDX",["unified_credit_code"], "按统一社会信用代码查"),
        ("idx_deleted",           "IDX",["deleted"],   "过滤已删"),
    ]),

    ("CUST_CLAIM", "客户认领关系表(机构维度)", ["id"], [
        ("id",                "varchar(32)", False, None, "认领ID"),
        ("cust_id",           "varchar(32)", False, None, "客户ID"),
        ("org_id",            "varchar(50)", False, None, "认领机构代码"),
        ("claimed_by",        "varchar(32)", False, None, "认领人工号"),
        ("maintainer_emp_id", "varchar(32)", True,  None, "维护人工号"),
        ("claim_status",      "varchar(50)", True,  "CLAIMED", "CLAIMED/CANCELLED"),
        ("claim_time",        "datetime",    True,  "CURRENT_TIMESTAMP", "认领时间"),
        ("cancel_time",       "datetime",    True,  None, "取消时间"),
        ("cancel_reason",     "varchar(500)",True,  None, "取消原因"),
        ("created_time",      "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",      "datetime",    True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_cust_org",   "UK", ["cust_id","org_id"], "客户×机构 唯一"),
        ("idx_cust_id",   "IDX",["cust_id"],   "按客户查"),
        ("idx_org_id",    "IDX",["org_id"],    "按机构查"),
        ("idx_claimed_by","IDX",["claimed_by"], "按认领人查"),
        ("idx_maintainer","IDX",["maintainer_emp_id"], "按维护人查"),
        ("idx_status",    "IDX",["claim_status"], "按认领状态查"),
    ]),

    ("TOUCH_TASK", "触达任务表(SLA 跟踪)", ["id"], [
        ("id",               "varchar(32)",  False, None, "任务ID"),
        ("task_no",          "varchar(100)", False, None, "任务编号"),
        ("cust_id",          "varchar(32)",  False, None, "客户ID"),
        ("org_id",           "varchar(50)",  False, None, "归属机构"),
        ("assignee_emp_id",  "varchar(32)",  False, None, "执行人工号"),
        ("task_type",        "varchar(50)",  True,  None, "FIRST_TOUCH/FOLLOW_UP"),
        ("task_status",      "varchar(50)",  True,  "PENDING", "PENDING/IN_PROGRESS/SUCCESS/CANCELLED"),
        ("plan_finish_time", "datetime",     True,  None, "计划完成时间(SLA)"),
        ("warning_time",     "datetime",     True,  None, "预警时间(SLA)"),
        ("sla_status",       "varchar(20)",  True,  None, "GREEN/YELLOW/RED"),
        ("sla_warning",      "tinyint(1)",   False, "0",  "SLA 预警标记"),
        ("business_key",     "varchar(100)", True,  None, "流程业务键(TOUCH:{id})"),
        ("success_time",     "datetime",     True,  None, "成功时间"),
        ("cancel_time",      "datetime",     True,  None, "取消时间"),
        ("created_time",     "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",     "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_task_no",            "UK", ["task_no"], "任务编号唯一"),
        ("idx_cust_id",           "IDX",["cust_id"], "按客户查"),
        ("idx_org_id",            "IDX",["org_id"],  "按机构查"),
        ("idx_assignee",          "IDX",["assignee_emp_id"], "按执行人查"),
        ("idx_status",            "IDX",["task_status"], "按状态查"),
        ("idx_business_key",      "IDX",["business_key"], "按流程键查"),
        ("idx_assignee_status",   "IDX",["assignee_emp_id","task_status"], "按执行人+状态联合查"),
    ]),

    ("TOUCH_LOG", "触达日志表(移动端上报+幂等)", ["id"], [
        ("id",            "varchar(32)",  False, None, "日志ID"),
        ("touch_task_id", "varchar(32)",  False, None, "触达任务ID(逻辑外键→touch_task.id)"),
        ("log_time",      "datetime",     True,  "CURRENT_TIMESTAMP", "业务时间"),
        ("client_uuid",   "varchar(64)",  True,  None, "客户端幂等UUID"),
        ("log_content",   "text",         True,  None, "日志内容"),
        ("photo_urls",    "text",         True,  None, "照片URL列表(JSON)"),
        ("owner_org_id",  "varchar(50)",  True,  None, "归属机构"),
        ("created_by",    "varchar(32)",  True,  None, "创建人"),
        ("created_time",  "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_task_client_uuid", "UK", ["touch_task_id","client_uuid"], "任务+UUID 防重复上报"),
        ("idx_task_id",         "IDX",["touch_task_id"], "按任务查"),
        ("idx_created_by",      "IDX",["created_by"],    "按创建人查"),
        ("idx_task_log_time",   "IDX",["touch_task_id","log_time"], "按任务+时间排序"),
    ]),
]


# ─── bizapp ─────────────────────────────────────────────────────────────────
TABLES_BIZAPP = [
    ("LOAN_APPLY", "资产投放申请表", ["id"], [
        ("id",                    "varchar(32)",  False, None, "申请ID(UUID)"),
        ("apply_no",              "varchar(100)", True,  None, "申请编号(LA+yyyyMMdd+6位)"),
        ("cust_id",               "varchar(32)",  False, None, "客户ID(逻辑外键→cust_master.id)"),
        ("source_touch_task_id",  "varchar(32)",  True,  None, "来源触达任务ID(逻辑外键→touch_task.id)"),
        ("project_type",          "varchar(32)",  True,  None, "项目类型(字典 PROJECT_TYPE)"),
        ("biz_type",              "varchar(32)",  True,  None, "业务类型(字典 BIZ_TYPE)"),
        ("guarantee_type",        "varchar(32)",  True,  None, "担保方式(字典 GUARANTEE_TYPE)"),
        ("credit_amount",         "decimal(20,4)",True,  None, "授信金额"),
        ("credit_exposure_amount","decimal(20,4)",True,  None, "敞口金额"),
        ("status",                "varchar(20)",  False, "DRAFT", "DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED"),
        ("business_key",          "varchar(100)", True,  None, "流程业务键(LOAN:{id})"),
        ("process_instance_id",   "varchar(64)",  True,  None, "流程实例ID"),
        ("owner_org_id",          "varchar(50)",  False, None, "归属机构(用于数据范围)"),
        *_audit_cols(deleted=True),
    ], [
        ("uk_apply_no",     "UK", ["apply_no"],     "申请编号唯一"),
        ("idx_cust_id",     "IDX",["cust_id"],      "按客户查"),
        ("idx_owner_org",   "IDX",["owner_org_id"], "按机构查"),
        ("idx_created_by",  "IDX",["created_by"],   "按创建人查"),
        ("idx_status",      "IDX",["status"],       "按状态查"),
        ("idx_business_key","IDX",["business_key"], "按流程键查"),
        ("idx_created_time","IDX",["created_time"], "按时间扫描"),
        ("idx_process_inst","IDX",["process_instance_id"], "按流程实例查"),
    ]),

    ("SUPPORT_REQUEST", "中场支持申请表(多产品拆单+双视图)", ["id"], [
        ("id",                   "varchar(32)",  False, None, "申请ID(UUID)"),
        ("request_no",           "varchar(100)", True,  None, "申请编号(SR+yyyyMMdd+6位)"),
        ("submit_group_id",      "varchar(64)",  True,  None, "同批提交分组ID(多产品拆单同组共享)"),
        ("cust_id",              "varchar(32)",  False, None, "客户ID(逻辑外键→cust_master.id)"),
        ("source_touch_task_id", "varchar(32)",  True,  None, "来源触达任务ID(逻辑外键→touch_task.id)"),
        ("product_id",           "varchar(64)",  True,  None, "产品ID(逻辑外键→product_info.id)"),
        ("support_dept_id",      "varchar(50)",  True,  None, "承接部门ORG_CODE(逻辑外键→ext_org_info.org_code)"),
        ("other_demand",         "text",         True,  None, "其他需求/补充说明"),
        ("dispatch_emp_id",      "varchar(32)",  True,  None, "派单人(秘书,场景B)"),
        ("dispatch_time",        "datetime",     True,  None, "派单时间"),
        ("assigned_emp_id",      "varchar(32)",  True,  None, "承接办理人(场景A=产品负责人,场景B=秘书派单)"),
        ("status",               "varchar(20)",  False, "DRAFT", "DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED"),
        ("business_key",         "varchar(100)", True,  None, "流程业务键(SUPPORT:{id})"),
        ("process_instance_id",  "varchar(64)",  True,  None, "流程实例ID"),
        ("owner_org_id",         "varchar(50)",  False, None, "归属机构(发起侧 ORG_CODE)"),
        *_audit_cols(deleted=True),
    ], [
        ("uk_request_no",   "UK", ["request_no"],     "申请编号唯一"),
        ("idx_cust_id",     "IDX",["cust_id"],        "按客户查"),
        ("idx_support_dept","IDX",["support_dept_id"],"按承接部门查"),
        ("idx_assigned_emp","IDX",["assigned_emp_id"],"按办理人查"),
        ("idx_created_by",  "IDX",["created_by"],     "按创建人查"),
        ("idx_status",      "IDX",["status"],         "按状态查"),
        ("idx_business_key","IDX",["business_key"],   "按流程键查"),
        ("idx_submit_group","IDX",["submit_group_id"],"按拆单分组查"),
        ("idx_owner_org",   "IDX",["owner_org_id"],   "按机构查"),
        ("idx_created_time","IDX",["created_time"],   "按时间扫描"),
        ("idx_product",     "IDX",["product_id"],     "按产品查"),
    ]),
]


# ─── performance ────────────────────────────────────────────────────────────
TABLES_PERFORMANCE = [
    ("SYS_CONTROL", "数据版本控制表(EMP/ORG/CUST 三维度)", ["id"], [
        ("id",               "varchar(32)", False, None, "控制ID"),
        ("scope_dim",        "varchar(20)", False, None, "EMP/ORG/CUST"),
        ("latest_data_date", "date",        False, None, "最新数据日期"),
        ("current_version",  "varchar(32)", True,  None, "当前有效版本"),
        ("is_valid",         "tinyint(1)",  False, "1",  "是否有效"),
        ("created_time",     "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",     "datetime",    True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_scope_dim_date","UK", ["scope_dim","latest_data_date"], "维度+日期 唯一"),
        ("idx_scope_valid",  "IDX",["scope_dim","is_valid"], "按维度+有效性查"),
    ]),

    ("PERF_METRIC_DEF", "指标定义表(metricCode→val_slot 路由)", ["id"], [
        ("id",              "varchar(32)",  False, None, "指标ID"),
        ("metric_code",     "varchar(64)",  False, None, "指标编码(唯一)"),
        ("metric_name",     "varchar(200)", False, None, "指标名称"),
        ("metric_name_en",  "varchar(200)", True,  None, "英文名"),
        ("metric_desc",     "text",         True,  None, "指标说明"),
        ("base_dim",        "varchar(20)",  False, None, "基础维度 EMP/ORG/CUST"),
        ("metric_level",    "int(11)",      False, None, "指标层级 1/2/3"),
        ("calc_freq",       "varchar(20)",  False, None, "DAY/MONTH/QUARTER/YEAR"),
        ("calc_mode",       "varchar(20)",  False, None, "AUTO/MANUAL"),
        ("calc_logic_type", "varchar(50)",  True,  None, "SQL/PROC/EXPR/SUMMARY"),
        ("sql_text",        "longtext",     True,  None, "一级指标 SQL/存储过程"),
        ("expr_text",       "text",         True,  None, "二/三级指标表达式"),
        ("summary_rule",    "varchar(20)",  True,  None, "机构汇总规则 SUM/AVG"),
        ("ref_metric_codes","text",         True,  None, "引用指标列表(JSON)"),
        ("val_slot",        "int(11)",      True,  None, "宽表槽位 1..200(同维度+slot 唯一)"),
        ("unit",            "varchar(16)",  True,  None, "单位 元/万元/%"),
        ("decimal_places",  "tinyint",      True,  "2",  "小数位数"),
        ("deleted",         "tinyint",      True,  "0",  "0 存在 1 删除"),
        ("description",     "varchar(500)", True,  None, "详细描述"),
        ("status",          "varchar(20)",  False, "ACTIVE", "ACTIVE/DISABLED"),
        ("created_by",      "varchar(32)",  True,  None, "创建人"),
        ("created_time",    "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",      "varchar(32)",  True,  None, "更新人"),
        ("updated_time",    "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
        ("cron_expr",       "varchar(120)", True,  None, "V1.7 自定义 cron"),
        ("subject_sql",     "longtext",     True,  None, "V1.7 EXPR/GROOVY 主体集合 SQL"),
        ("last_run_time",   "datetime",     True,  None, "V1.7 最近一次自动调度执行时间"),
    ], [
        ("uk_metric_code",          "UK", ["metric_code"], "指标编码唯一"),
        ("uk_base_dim_slot_alive",  "UK", ["base_dim","val_slot"], "未删除时同维度槽位唯一(函数索引)"),
        ("idx_dim_level",           "IDX",["base_dim","metric_level"], "按维度+层级查"),
        ("idx_status",              "IDX",["status"], "按状态查"),
        ("idx_val_slot",            "IDX",["val_slot"], "按槽位查"),
        ("idx_metric_def_schedulable","IDX",["status","calc_mode","deleted"], "V1.7 启动同步+HealthCheck 扫描"),
    ]),

    ("PERF_METRIC_REF", "指标引用关系(支持二/三级指标递归)", ["id"], [
        ("id",              "varchar(32)", False, None, "引用ID"),
        ("metric_code",     "varchar(64)", False, None, "上层指标"),
        ("ref_metric_code", "varchar(64)", False, None, "被引用(下层指标)"),
        ("created_time",    "datetime",    True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_metric_ref",  "UK", ["metric_code","ref_metric_code"], "防重"),
        ("idx_ref_metric", "IDX",["ref_metric_code"], "按下层反查上层"),
    ]),

    ("PERF_KPI_SCHEME", "KPI 方案", ["id"], [
        ("id",          "varchar(32)",  False, None, "方案ID"),
        ("scheme_code", "varchar(64)",  False, None, "方案编码(唯一)"),
        ("scheme_name", "varchar(200)", False, None, "方案名称"),
        ("cycle_type",  "varchar(20)",  False, None, "MONTHLY/QUARTERLY"),
        ("open_detail", "tinyint(1)",   False, "0",  "是否向员工开放明细"),
        ("status",      "varchar(20)",  False, "ACTIVE", "ACTIVE/DISABLED"),
        ("created_by",  "varchar(32)",  True,  None, "创建人"),
        ("created_time","datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",  "varchar(32)",  True,  None, "更新人"),
        ("updated_time","datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_scheme_code","UK", ["scheme_code"], "方案编码唯一"),
        ("idx_status",    "IDX",["status"],      "按状态查"),
    ]),

    ("PERF_KPI_ITEM", "KPI 方案项(权重/上下限)", ["id"], [
        ("id",           "varchar(32)",  False, None, "项ID"),
        ("scheme_id",    "varchar(32)",  False, None, "方案ID(逻辑外键→perf_kpi_scheme.id)"),
        ("metric_code",  "varchar(64)",  False, None, "指标编码(人员维度)"),
        ("weight",       "decimal(10,4)",False, None, "权重"),
        ("multiplier",   "decimal(10,4)",False, "1",  "加倍系数"),
        ("min_score",    "decimal(10,4)",False, "0",  "最低分"),
        ("max_score",    "decimal(10,4)",False, "999999", "最高分"),
        ("created_time", "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_scheme_metric","UK", ["scheme_id","metric_code"], "方案×指标 唯一"),
        ("idx_scheme_id",   "IDX",["scheme_id"], "按方案查"),
    ]),

    ("PERF_TARGET_PLAN", "目标方案", ["id"], [
        ("id",             "varchar(32)",  False, None, "目标方案ID"),
        ("plan_code",      "varchar(64)",  False, None, "方案编码(唯一)"),
        ("plan_name",      "varchar(200)", False, None, "方案名称"),
        ("kpi_scheme_id",  "varchar(32)",  False, None, "关联KPI方案ID(逻辑外键→perf_kpi_scheme.id)"),
        ("target_dim",     "varchar(20)",  False, None, "目标维度 EMP/ORG"),
        ("target_cycle",   "varchar(20)",  False, None, "目标周期 YEAR/QUARTER"),
        ("effective_date", "date",         False, None, "生效日期"),
        ("status",         "varchar(20)",  False, "ACTIVE", "ACTIVE/DISABLED"),
        ("owner_emp_id",   "varchar(32)",  True,  None, "归属员工(SELF/SELF_ASSIGNED)"),
        ("owner_org_code", "varchar(50)",  True,  None, "归属机构(ORG/ORG_SUBTREE)"),
        ("created_by",     "varchar(32)",  True,  None, "创建人"),
        ("created_time",   "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",     "varchar(32)",  True,  None, "更新人"),
        ("updated_time",   "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_plan_code",  "UK", ["plan_code"], "方案编码唯一"),
        ("idx_status",    "IDX",["status"],    "按状态查"),
        ("idx_owner_emp", "IDX",["owner_emp_id"], "数据范围 SELF"),
        ("idx_owner_org", "IDX",["owner_org_code"], "数据范围 ORG"),
    ]),

    ("PERF_TARGET_VALUE", "目标值/基础值(对象级)", ["id"], [
        ("id",             "varchar(32)",  False, None, "目标值ID"),
        ("plan_id",        "varchar(32)",  False, None, "目标方案ID(逻辑外键→perf_target_plan.id)"),
        ("subject_type",   "varchar(20)",  False, None, "EMP/ORG"),
        ("subject_id",     "varchar(50)",  False, None, "对象ID(emp_id/org_code)"),
        ("cycle_key",      "varchar(20)",  False, None, "周期键 2026 / 2026Q1"),
        ("metric_code",    "varchar(64)",  False, None, "指标编码"),
        ("target_value",   "decimal(20,4)",False, None, "目标值"),
        ("base_value",     "decimal(20,4)",True,  None, "基础值(可空)"),
        ("owner_emp_id",   "varchar(32)",  True,  None, "归属员工"),
        ("owner_org_code", "varchar(50)",  True,  None, "归属机构"),
        ("created_by",     "varchar(32)",  True,  None, "创建人"),
        ("created_time",   "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",     "varchar(32)",  True,  None, "更新人"),
        ("updated_time",   "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_plan_subject_cycle_metric", "UK", ["plan_id","subject_type","subject_id","cycle_key","metric_code"], "唯一约束防重"),
        ("idx_metric_code",              "IDX",["metric_code"], "按指标查"),
        ("idx_subject",                  "IDX",["subject_type","subject_id","cycle_key"], "按对象+周期查"),
        ("idx_owner_emp",                "IDX",["owner_emp_id"], "数据范围 SELF"),
        ("idx_owner_org",                "IDX",["owner_org_code"], "数据范围 ORG"),
    ]),

    ("PERF_IMPORT_BATCH", "绩效导入批次", ["id"], [
        ("id",                  "varchar(32)",  False, None, "批次ID"),
        ("batch_no",            "varchar(64)",  False, None, "批次号"),
        ("import_type",         "varchar(20)",  False, None, "INDEX_RESULT/KPI_RESULT/TARGET"),
        ("dim",                 "varchar(20)",  False, None, "EMP/ORG/CUST"),
        ("as_of_date",          "date",         True,  None, "KPI 导入基准日"),
        ("file_name",           "varchar(255)", True,  None, "文件名"),
        ("file_md5",            "varchar(64)",  True,  None, "文件MD5"),
        ("status",              "varchar(20)",  False, "CREATED", "CREATED/SUCCESS/FAILED"),
        ("total_rows",          "int(11)",      False, "0",  "总行数"),
        ("success_rows",        "int(11)",      False, "0",  "成功行数"),
        ("error_rows",          "int(11)",      False, "0",  "失败行数"),
        ("error_file_object_id","varchar(32)",  True,  None, "错误明细文件ID(逻辑外键→file_object.id)"),
        ("remark",              "varchar(500)", True,  None, "备注"),
        ("created_by",          "varchar(32)",  False, None, "创建人"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_batch_no",      "UK", ["batch_no"],     "批次号唯一"),
        ("idx_status",       "IDX",["status"],       "按状态查"),
        ("idx_created_by",   "IDX",["created_by"],   "按创建人查"),
        ("idx_created_time", "IDX",["created_time"], "按时间扫描"),
    ]),

    ("PERF_RUN_TASK", "绩效任务执行日志", ["id"], [
        ("id",                  "varchar(32)",  False, None, "任务ID"),
        ("task_type",           "varchar(30)",  False, None, "METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC"),
        ("task_key",            "varchar(100)", True,  None, "关键键(如 metric_code)"),
        ("data_date",           "date",         True,  None, "数据日期"),
        ("data_version",        "varchar(32)",  True,  None, "数据版本"),
        ("params_json",         "longtext",     True,  None, "参数 JSON"),
        ("status",              "varchar(20)",  False, "RUNNING", "RUNNING/SUCCESS/FAILED"),
        ("started_by",          "varchar(32)",  False, None, "发起人"),
        ("start_time",          "datetime",     True,  "CURRENT_TIMESTAMP", "开始时间"),
        ("end_time",            "datetime",     True,  None, "结束时间"),
        ("error_msg",           "longtext",     True,  None, "错误信息"),
        ("result_preview_json", "longtext",     True,  None, "结果预览 JSON"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("idx_task_type",    "IDX", ["task_type"],    "按任务类型查"),
        ("idx_status",       "IDX", ["status"],       "按状态查"),
        ("idx_started_by",   "IDX", ["started_by"],   "按发起人查"),
        ("idx_created_time", "IDX", ["created_time"], "按时间扫描"),
    ]),

    ("EMP_INDEX_RESULT", "员工指标结果宽表(val_1..val_200)", ["id"], [
        ("id",        "bigint(20)",  False, "AUTO_INCREMENT", "主键"),
        ("data_date", "date",        False, None, "数据日期"),
        ("version",   "varchar(32)", False, None, "数据版本(关联 sys_control.current_version)"),
        ("emp_id",    "varchar(50)", False, None, "员工工号(逻辑外键→addrbook_employee.emp_id)"),
        *_val_slots(200),
        ("created_time","datetime",  True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_subject_date_ver", "UK", ["emp_id","data_date","version"], "员工×日期×版本 唯一"),
        ("idx_date_ver",        "IDX",["data_date","version"], "按日期+版本扫描"),
    ]),

    ("ORG_INDEX_RESULT", "机构指标结果宽表(val_1..val_200)", ["id"], [
        ("id",        "bigint(20)",  False, "AUTO_INCREMENT", "主键"),
        ("data_date", "date",        False, None, "数据日期"),
        ("version",   "varchar(32)", False, None, "数据版本"),
        ("org_code",  "varchar(50)", False, None, "机构编码(逻辑外键→ext_org_info.org_code)"),
        *_val_slots(200),
        ("created_time","datetime",  True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_subject_date_ver", "UK", ["org_code","data_date","version"], "机构×日期×版本 唯一"),
        ("idx_date_ver",        "IDX",["data_date","version"], "按日期+版本扫描"),
    ]),

    ("CUST_INDEX_RESULT", "客户指标结果宽表(val_1..val_200)", ["id"], [
        ("id",        "bigint(20)",  False, "AUTO_INCREMENT", "主键"),
        ("data_date", "date",        False, None, "数据日期"),
        ("version",   "varchar(32)", False, None, "数据版本"),
        ("cust_id",   "varchar(50)", False, None, "客户ID(逻辑外键→cust_master.id)"),
        *_val_slots(200),
        ("created_time","datetime",  True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_subject_date_ver", "UK", ["cust_id","data_date","version"], "客户×日期×版本 唯一"),
        ("idx_date_ver",        "IDX",["data_date","version"], "按日期+版本扫描"),
    ]),

    ("KPI_RESULT", "KPI 结果表", ["id"], [
        ("id",               "bigint(20)",   False, "AUTO_INCREMENT", "主键"),
        ("emp_id",           "varchar(32)",  False, None, "员工工号"),
        ("cycle_type",       "varchar(20)",  False, None, "MONTHLY/QUARTERLY"),
        ("cycle_date",       "date",         False, None, "周期日期(月末)"),
        ("as_of_date",       "date",         False, None, "计算基准日(每日一算区分键)"),
        ("data_version",     "varchar(32)",  False, None, "指标数据版本"),
        ("kpi_total_score",  "decimal(10,4)",False, None, "KPI 总分"),
        ("detail_json",      "longtext",     True,  None, "明细 JSON"),
        ("calculated_time",  "datetime",     True,  "CURRENT_TIMESTAMP", "计算时间"),
    ], [
        ("uk_emp_cycle_asof","UK", ["emp_id","cycle_type","cycle_date","as_of_date"], "唯一约束"),
        ("idx_as_of_date",   "IDX",["as_of_date"], "按基准日查"),
    ]),

    ("CUST_ALLOC_RELATION", "客户业绩分配关系", ["id"], [
        ("id",                  "varchar(32)",  False, None, "主键ID"),
        ("cust_id",             "varchar(32)",  False, None, "客户ID(逻辑外键→cust_master.id)"),
        ("alloc_dim",           "varchar(16)",  False, None, "RULE/ACCOUNT"),
        ("biz_kind",            "varchar(32)",  True,  None, "业务种类"),
        ("account_no",          "varchar(64)",  True,  None, "账号(账号维度必填)"),
        ("emp_id",              "varchar(32)",  False, None, "员工工号"),
        ("ratio",               "decimal(5,2)", False, None, "比例(0-100)"),
        ("effective_date",      "date",         False, None, "生效日期"),
        ("end_date",            "date",         True,  None, "失效日期"),
        ("source_batch_id",     "varchar(64)",  True,  None, "来源批次号"),
        ("source_process_date", "date",         True,  None, "来源业务日期"),
        ("created_by",          "varchar(32)",  False, None, "创建人"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",          "varchar(32)",  True,  None, "更新人"),
        ("updated_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("idx_cust_id",        "IDX",["cust_id"],        "按客户查"),
        ("idx_emp_id",         "IDX",["emp_id"],         "按员工查"),
        ("idx_effective_date", "IDX",["effective_date"], "按生效日扫描"),
    ]),

    ("PERF_ALLOC_ADJUST_APPLY", "分配关系调整申请(走流程)", ["id"], [
        ("id",                  "varchar(32)",  False, None, "申请ID"),
        ("apply_no",            "varchar(100)", True,  None, "申请编号"),
        ("cust_id",             "varchar(32)",  False, None, "客户ID"),
        ("alloc_dim",           "varchar(16)",  False, None, "RULE/ACCOUNT"),
        ("biz_kind",            "varchar(32)",  True,  None, "业务种类"),
        ("account_no",          "varchar(64)",  True,  None, "账号"),
        ("status",              "varchar(20)",  False, "DRAFT", "DRAFT/IN_APPROVAL/APPROVED/REJECTED"),
        ("business_key",        "varchar(100)", True,  None, "流程业务键"),
        ("process_instance_id", "varchar(64)",  True,  None, "流程实例ID"),
        ("owner_org_id",        "varchar(50)",  False, None, "归属机构"),
        ("remark",              "text",         True,  None, "备注"),
        ("created_by",          "varchar(32)",  False, None, "创建人"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",          "varchar(32)",  True,  None, "更新人"),
        ("updated_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("uk_apply_no",    "UK", ["apply_no"],   "申请编号唯一"),
        ("idx_cust_id",    "IDX",["cust_id"],    "按客户查"),
        ("idx_status",     "IDX",["status"],     "按状态查"),
        ("idx_created_by", "IDX",["created_by"], "按创建人查"),
    ]),

    ("PERF_ALLOC_ADJUST_ITEM", "分配关系调整明细", ["id"], [
        ("id",           "varchar(32)",  False, None, "项ID"),
        ("apply_id",     "varchar(32)",  False, None, "申请ID(逻辑外键→perf_alloc_adjust_apply.id)"),
        ("emp_id",       "varchar(32)",  False, None, "员工工号"),
        ("ratio",        "decimal(5,2)", False, None, "比例(0-100)"),
        ("created_time", "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("uk_apply_emp", "UK", ["apply_id","emp_id"], "申请×员工 唯一"),
        ("idx_apply_id", "IDX",["apply_id"], "按申请查"),
    ]),

    ("PERF_TARGET_ADJUST_APPLY", "目标修正申请(走流程)", ["id"], [
        ("id",                  "varchar(32)",  False, None, "申请ID"),
        ("plan_id",             "varchar(32)",  False, None, "目标方案ID(逻辑外键→perf_target_plan.id)"),
        ("subject_type",        "varchar(20)",  False, None, "EMP/ORG"),
        ("subject_id",          "varchar(50)",  False, None, "对象ID"),
        ("cycle_key",           "varchar(20)",  False, None, "周期键"),
        ("status",              "varchar(20)",  False, "DRAFT", "DRAFT/IN_APPROVAL/APPROVED/REJECTED"),
        ("business_key",        "varchar(100)", True,  None, "流程业务键"),
        ("process_instance_id", "varchar(64)",  True,  None, "流程实例ID"),
        ("owner_org_id",        "varchar(50)",  False, None, "归属机构"),
        ("remark",              "text",         True,  None, "备注"),
        ("created_by",          "varchar(32)",  False, None, "创建人"),
        ("created_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_by",          "varchar(32)",  True,  None, "更新人"),
        ("updated_time",        "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("idx_plan_id",    "IDX",["plan_id"],    "按方案查"),
        ("idx_status",     "IDX",["status"],     "按状态查"),
        ("idx_created_by", "IDX",["created_by"], "按创建人查"),
    ]),
]


# ─── report ─────────────────────────────────────────────────────────────────
TABLES_REPORT = [
    ("RPT_SAVED_QUERY", "动态查询保存方案(每用户≤10)", ["id"], [
        ("id",           "varchar(32)",  False, None, "方案ID(UUID)"),
        ("emp_id",       "varchar(32)",  False, None, "员工工号"),
        ("name",         "varchar(200)", False, None, "方案名称"),
        ("dim",          "varchar(20)",  False, None, "EMP/ORG/CUST"),
        ("subject_ids",  "text",         False, None, "对象ID列表(JSON)"),
        ("metric_codes", "text",         False, None, "指标编码列表(JSON)"),
        ("version",      "int(11)",      True,  "0",  "乐观锁版本号"),
        ("created_time", "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time", "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("idx_emp_id_time","IDX",["emp_id","created_time"], "按用户最新优先"),
        ("idx_emp_id_name","IDX",["emp_id","name"],         "按用户+名称查"),
    ]),

    ("SQL_PROBE_HISTORY", "SQL 探查历史(保留 90 天)", ["id"], [
        ("id",                "varchar(32)",  False, None, "历史ID(UUID)"),
        ("emp_id",            "varchar(32)",  False, None, "执行人工号"),
        ("sql_text",          "text",         False, None, "SQL 语句"),
        ("remark",            "varchar(500)", True,  None, "备注(reason)"),
        ("row_count",         "int(11)",      True,  None, "影响行数"),
        ("execution_time_ms", "int(11)",      True,  None, "执行耗时(ms)"),
        ("status",            "varchar(20)",  True,  None, "RUNNING/SUCCESS/FAILED/TIMEOUT"),
        ("error_msg",         "text",         True,  None, "错误信息"),
        ("created_time",      "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
    ], [
        ("idx_emp_id",       "IDX", ["emp_id"],       "按人查"),
        ("idx_created_time", "IDX", ["created_time"], "按时间扫描"),
        ("idx_emp_time",     "IDX", ["emp_id","created_time"], "按人+时间查"),
        ("idx_status",       "IDX", ["status"],       "按状态查"),
    ]),

    ("RPT_SNAPSHOT_TASK", "快照任务配置(V1 预留, 未启用)", ["id"], [
        ("id",            "varchar(32)",  False, None, "任务ID"),
        ("task_name",     "varchar(200)", False, None, "任务名称"),
        ("snapshot_type", "varchar(50)",  False, None, "DAILY/MONTHLY"),
        ("cron_expr",     "varchar(100)", False, None, "Cron 表达式"),
        ("status",        "varchar(20)",  True,  "ACTIVE", "ACTIVE/DISABLED"),
        ("last_run_time", "datetime",     True,  None, "最近执行时间"),
        ("next_run_time", "datetime",     True,  None, "下次执行时间"),
        ("created_time",  "datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time",  "datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("idx_next_run_time","IDX",["next_run_time"], "按下次执行时间查"),
        ("idx_status",       "IDX",["status"],        "按状态查"),
    ]),

    ("RPT_EXPORT_TASK", "报表异步导出任务", ["id"], [
        ("id",          "varchar(32)",  False, None, "导出任务ID"),
        ("export_type", "varchar(32)",  False, None, "DYNAMIC_QUERY/TOUCH_SUMMARY/PERF_SUMMARY/CUSTPOOL_SUMMARY"),
        ("params_json", "text",         True,  None, "导出参数 JSON"),
        ("status",      "varchar(20)",  False, "PENDING", "PENDING/RUNNING/SUCCESS/FAILED/CANCELLED"),
        ("file_key",    "varchar(200)", True,  None, "governance.file_object.id"),
        ("file_size",   "bigint",       True,  None, "文件大小(字节)"),
        ("row_count",   "int",          True,  None, "导出行数"),
        ("expire_at",   "datetime",     True,  None, "文件过期时间"),
        ("operator_id", "varchar(32)",  False, None, "操作人员工号"),
        ("error_msg",   "text",         True,  None, "失败原因"),
        ("created_time","datetime",     True,  "CURRENT_TIMESTAMP", "创建时间"),
        ("updated_time","datetime",     True,  "CURRENT_TIMESTAMP", "更新时间"),
    ], [
        ("idx_operator",    "IDX", ["operator_id"], "按操作人查"),
        ("idx_status",      "IDX", ["status"],      "按状态查"),
        ("idx_export_type", "IDX", ["export_type"], "按导出类型查"),
    ]),
]


# ─── 汇总 ───────────────────────────────────────────────────────────────────
ALL_TABLES = OrderedDict([
    ("auth",        TABLES_AUTH),
    ("governance",  TABLES_GOVERNANCE),
    ("workflow",    TABLES_WORKFLOW),
    ("portal",      TABLES_PORTAL),
    ("customer",    TABLES_CUSTOMER),
    ("bizapp",      TABLES_BIZAPP),
    ("performance", TABLES_PERFORMANCE),
    ("report",      TABLES_REPORT),
])


# ─────────────────────────────────────────────────────────────────────────────
# 3. 业务关联(逻辑外键): (from_tab, from_col, to_tab, to_col, kind, note)
#    kind ∈ {N1=多对一, 11=一对一, 1N=一对多, NN=多对多, SELF=自关联, LOG=逻辑/弱关联}
# ─────────────────────────────────────────────────────────────────────────────
RELATIONSHIPS: List[Tuple[str, str, str, str, str, str]] = [
    # auth
    ("PT_USER_ROLE",      "USER_ID",     "PT_USER",       "USER_ID",     "N1",   "用户分配的角色"),
    ("PT_USER_ROLE",      "ROLE_ID",     "PT_ROLE",       "ROLE_ID",     "N1",   "角色拥有的用户"),
    ("PT_ROLE_RESOURCE",  "ROLE_ID",     "PT_ROLE",       "ROLE_ID",     "N1",   "角色绑定资源"),
    ("PT_ROLE_RESOURCE",  "RESOURCE_ID", "PT_RESOURCE",   "RESOURCE_ID", "N1",   "资源被角色引用"),
    ("PT_RESOURCE",       "PARENT_RESOURCE_ID","PT_RESOURCE","RESOURCE_ID","SELF","菜单/资源树"),
    ("PT_ROLE_BIZ_SCOPE", "ROLE_ID",     "PT_ROLE",       "ROLE_ID",     "N1",   "角色×BizType→DataScope"),
    ("EXT_USER_ORG",      "USER_ID",     "PT_USER",       "USER_ID",     "N1",   "用户归属机构(可多机构)"),
    ("EXT_USER_ORG",      "ORG_CODE",    "EXT_ORG_INFO",  "ORG_CODE",    "N1",   "机构含有的用户"),
    ("EXT_ORG_INFO",      "P_ID",        "EXT_ORG_INFO",  "ORG_CODE",    "SELF", "机构树(总行/分行/支行)"),

    # auth ↔ portal: 通讯录的 emp_id 与 PT_USER.USER_ID 是同一标识空间(工号)
    ("ADDRBOOK_EMPLOYEE", "emp_id",            "PT_USER",       "USER_ID",   "11",  "通讯录与登录用户共用工号"),
    ("ADDRBOOK_EMPLOYEE", "org_code",          "EXT_ORG_INFO",  "ORG_CODE",  "N1",  "员工所属机构"),
    ("ADDRBOOK_EMPLOYEE", "maintainer_emp_id", "ADDRBOOK_EMPLOYEE","emp_id", "SELF","员工维护人(通讯录维护)"),

    # portal ↔ governance
    ("PORTAL_SHORTCUT", "emp_id",         "ADDRBOOK_EMPLOYEE","emp_id", "N1", "用户自定义快捷入口"),
    ("PRODUCT_INFO",    "file_object_id", "FILE_OBJECT",      "id",     "N1", "产品主附件"),
    ("PRODUCT_INFO",    "product_dept_org_code","EXT_ORG_INFO","ORG_CODE","N1","产品部门"),
    ("DOC_INFO",        "file_object_id", "FILE_OBJECT",      "id",     "N1", "文档对应文件对象"),

    # governance 内部
    ("BIZ_FILE_REL",     "file_object_id", "FILE_OBJECT",  "id", "N1", "业务实体的附件"),
    ("SYS_JOB_RUN_LOG",  "job_id",         "SYS_JOB_CONF", "id", "N1", "调度任务的执行历史"),
    ("USER_NOTIFICATION","emp_id",         "ADDRBOOK_EMPLOYEE","emp_id","N1","通知接收人"),
    ("AUDIT_LOG",        "emp_id",         "PT_USER",      "USER_ID","N1","审计日志的操作人"),

    # workflow: BIZ_PROCESS_MAP 是流程映射枢纽,被多业务表通过 business_key 反向引用
    ("BIZ_PROCESS_MAP", "start_user",       "ADDRBOOK_EMPLOYEE","emp_id", "N1",  "流程发起人"),
    ("BIZ_PROCESS_MAP", "current_assignee", "ADDRBOOK_EMPLOYEE","emp_id", "N1",  "流程当前处理人"),

    # customer 内部
    ("CUST_TAG_REL",  "cust_id",         "CUST_MASTER", "id", "N1", "客户的标签"),
    ("CUST_TAG_REL",  "tag_id",          "CUST_TAG",    "id", "N1", "标签被引用"),
    ("CUST_LEAD",     "source_cust_id",  "CUST_MASTER", "id", "N1", "线索关联的客户(UPDATE/DELETE)"),
    ("CUST_LEAD",     "prev_lead_id",    "CUST_LEAD",   "id", "SELF","线索版本链"),
    ("CUST_LEAD",     "import_batch_id", "LEAD_IMPORT_BATCH","id","N1","线索归属批次"),
    ("CUST_LEAD",     "owner_org_id",    "EXT_ORG_INFO","ORG_CODE","N1","线索归属机构"),
    ("CUST_MASTER",   "lead_id",         "CUST_LEAD",   "id", "11", "客户来源线索(转客)"),
    ("CUST_CLAIM",    "cust_id",         "CUST_MASTER", "id", "N1", "客户被认领"),
    ("CUST_CLAIM",    "org_id",          "EXT_ORG_INFO","ORG_CODE","N1","认领机构"),
    ("CUST_CLAIM",    "claimed_by",      "ADDRBOOK_EMPLOYEE","emp_id","N1","认领人"),
    ("CUST_CLAIM",    "maintainer_emp_id","ADDRBOOK_EMPLOYEE","emp_id","N1","维护人"),
    ("TOUCH_TASK",    "cust_id",         "CUST_MASTER", "id", "N1", "客户触达任务"),
    ("TOUCH_TASK",    "org_id",          "EXT_ORG_INFO","ORG_CODE","N1","归属机构"),
    ("TOUCH_TASK",    "assignee_emp_id", "ADDRBOOK_EMPLOYEE","emp_id","N1","执行人"),
    ("TOUCH_LOG",     "touch_task_id",   "TOUCH_TASK",  "id", "N1", "任务的日志"),

    # bizapp ↔ customer/portal/auth
    ("LOAN_APPLY",      "cust_id",              "CUST_MASTER",      "id",       "N1", "资产投放针对客户"),
    ("LOAN_APPLY",      "source_touch_task_id", "TOUCH_TASK",       "id",       "N1", "源自触达任务"),
    ("LOAN_APPLY",      "owner_org_id",         "EXT_ORG_INFO",     "ORG_CODE", "N1", "归属机构"),
    ("LOAN_APPLY",      "created_by",           "ADDRBOOK_EMPLOYEE","emp_id",   "N1", "申请人"),
    ("SUPPORT_REQUEST", "cust_id",              "CUST_MASTER",      "id",       "N1", "支持申请针对客户"),
    ("SUPPORT_REQUEST", "source_touch_task_id", "TOUCH_TASK",       "id",       "N1", "源自触达任务"),
    ("SUPPORT_REQUEST", "product_id",           "PRODUCT_INFO",     "id",       "N1", "申请的产品"),
    ("SUPPORT_REQUEST", "support_dept_id",      "EXT_ORG_INFO",     "ORG_CODE", "N1", "承接部门"),
    ("SUPPORT_REQUEST", "owner_org_id",         "EXT_ORG_INFO",     "ORG_CODE", "N1", "发起机构"),
    ("SUPPORT_REQUEST", "assigned_emp_id",      "ADDRBOOK_EMPLOYEE","emp_id",   "N1", "承接人"),
    ("SUPPORT_REQUEST", "dispatch_emp_id",      "ADDRBOOK_EMPLOYEE","emp_id",   "N1", "派单人"),

    # business_key → BIZ_PROCESS_MAP (逻辑/弱关联,通过 business_key 列匹配)
    ("LOAN_APPLY",              "business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),
    ("SUPPORT_REQUEST",         "business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),
    ("CUST_LEAD",               "business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),
    ("LEAD_IMPORT_BATCH",       "business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),
    ("TOUCH_TASK",              "business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),
    ("PERF_ALLOC_ADJUST_APPLY", "business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),
    ("PERF_TARGET_ADJUST_APPLY","business_key", "BIZ_PROCESS_MAP", "business_key", "LOG", "流程映射"),

    # performance 内部
    ("PERF_METRIC_REF",     "metric_code",     "PERF_METRIC_DEF",  "metric_code", "N1", "上层指标"),
    ("PERF_METRIC_REF",     "ref_metric_code", "PERF_METRIC_DEF",  "metric_code", "N1", "下层指标(被引用)"),
    ("PERF_KPI_ITEM",       "scheme_id",       "PERF_KPI_SCHEME",  "id",          "N1", "KPI 方案的项"),
    ("PERF_KPI_ITEM",       "metric_code",     "PERF_METRIC_DEF",  "metric_code", "N1", "KPI 项的指标"),
    ("PERF_TARGET_PLAN",    "kpi_scheme_id",   "PERF_KPI_SCHEME",  "id",          "N1", "目标方案绑定 KPI"),
    ("PERF_TARGET_VALUE",   "plan_id",         "PERF_TARGET_PLAN", "id",          "N1", "目标方案下的值"),
    ("PERF_TARGET_VALUE",   "metric_code",     "PERF_METRIC_DEF",  "metric_code", "N1", "目标针对的指标"),
    ("PERF_IMPORT_BATCH",   "error_file_object_id","FILE_OBJECT",  "id",          "N1", "错误明细文件"),

    # performance 宽表 ↔ 主体
    ("EMP_INDEX_RESULT", "emp_id",  "ADDRBOOK_EMPLOYEE","emp_id",  "N1", "员工指标值"),
    ("EMP_INDEX_RESULT", "version", "SYS_CONTROL",      "current_version","LOG","数据版本对齐"),
    ("ORG_INDEX_RESULT", "org_code","EXT_ORG_INFO",     "ORG_CODE","N1", "机构指标值"),
    ("ORG_INDEX_RESULT", "version", "SYS_CONTROL",      "current_version","LOG","数据版本对齐"),
    ("CUST_INDEX_RESULT","cust_id", "CUST_MASTER",      "id",      "N1", "客户指标值"),
    ("CUST_INDEX_RESULT","version", "SYS_CONTROL",      "current_version","LOG","数据版本对齐"),

    ("KPI_RESULT",       "emp_id",       "ADDRBOOK_EMPLOYEE","emp_id",          "N1",  "员工 KPI 结果"),
    ("KPI_RESULT",       "data_version", "SYS_CONTROL",      "current_version", "LOG", "数据版本对齐"),

    ("CUST_ALLOC_RELATION",      "cust_id",  "CUST_MASTER",        "id",     "N1", "客户分配关系的客户"),
    ("CUST_ALLOC_RELATION",      "emp_id",   "ADDRBOOK_EMPLOYEE",  "emp_id", "N1", "客户分配关系的员工"),
    ("PERF_ALLOC_ADJUST_APPLY",  "cust_id",  "CUST_MASTER",        "id",     "N1", "调整针对客户"),
    ("PERF_ALLOC_ADJUST_APPLY",  "owner_org_id","EXT_ORG_INFO",    "ORG_CODE","N1","归属机构"),
    ("PERF_ALLOC_ADJUST_ITEM",   "apply_id", "PERF_ALLOC_ADJUST_APPLY","id", "N1", "申请的明细行"),
    ("PERF_ALLOC_ADJUST_ITEM",   "emp_id",   "ADDRBOOK_EMPLOYEE",  "emp_id", "N1", "明细行的员工"),
    ("PERF_TARGET_ADJUST_APPLY", "plan_id",  "PERF_TARGET_PLAN",   "id",     "N1", "目标方案"),
    ("PERF_TARGET_ADJUST_APPLY", "owner_org_id","EXT_ORG_INFO",    "ORG_CODE","N1","归属机构"),

    # report
    ("RPT_SAVED_QUERY",   "emp_id",      "ADDRBOOK_EMPLOYEE","emp_id", "N1", "用户保存的查询方案"),
    ("SQL_PROBE_HISTORY", "emp_id",      "ADDRBOOK_EMPLOYEE","emp_id", "N1", "执行人"),
    ("RPT_EXPORT_TASK",   "operator_id", "ADDRBOOK_EMPLOYEE","emp_id", "N1", "导出任务发起人"),
    ("RPT_EXPORT_TASK",   "file_key",    "FILE_OBJECT",      "id",     "N1", "导出文件"),
]


# ─────────────────────────────────────────────────────────────────────────────
# 4. Excel 生成
# ─────────────────────────────────────────────────────────────────────────────
HEADER_FILL = PatternFill(start_color="1F4E78", end_color="1F4E78", fill_type="solid")
HEADER_FONT = Font(name="Microsoft YaHei", size=11, bold=True, color="FFFFFF")
PK_FILL     = PatternFill(start_color="FFF2CC", end_color="FFF2CC", fill_type="solid")
MOD_FILL    = PatternFill(start_color="DDEBF7", end_color="DDEBF7", fill_type="solid")
BORDER      = Border(left=Side(style="thin", color="BFBFBF"),
                     right=Side(style="thin", color="BFBFBF"),
                     top=Side(style="thin", color="BFBFBF"),
                     bottom=Side(style="thin", color="BFBFBF"))
CELL_FONT   = Font(name="Microsoft YaHei", size=10)


def _apply_header(ws, row, headers, widths):
    for col_idx, (h, w) in enumerate(zip(headers, widths), start=1):
        c = ws.cell(row=row, column=col_idx, value=h)
        c.fill = HEADER_FILL
        c.font = HEADER_FONT
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = BORDER
        ws.column_dimensions[get_column_letter(col_idx)].width = w


def build_xlsx(out_path: str):
    wb = Workbook()
    # ── 索引 sheet ────────────────────────────────────────────────────────
    ws = wb.active
    ws.title = "索引"
    ws.sheet_view.showGridLines = False
    ws.cell(row=1, column=1, value="yiti(分行业务平台) 数据表结构索引").font = Font(
        name="Microsoft YaHei", size=14, bold=True, color="1F4E78")
    ws.cell(row=2, column=1, value="数据库 MySQL 8.0  /  字符集 utf8mb4  /  引擎 InnoDB  /  跳过 Quartz 11 张调度表  /  来源: docs/schema/ddl-*.sql").font = Font(
        name="Microsoft YaHei", size=10, italic=True, color="595959")

    headers = ["#", "模块", "Maven 模块名", "表名", "中文释义", "主键", "字段数", "索引数", "业务说明"]
    widths  = [5,  14,    28,             32,    40,        24,    8,       8,     50]
    _apply_header(ws, 4, headers, widths)

    row = 5
    seq = 0
    for mod_key, tabs in ALL_TABLES.items():
        meta = MODULES[mod_key]
        # 模块分组横条
        ws.cell(row=row, column=1, value=f"▌ {meta['zh']} ({meta['code']})  —  {meta['desc']}").font = Font(
            name="Microsoft YaHei", size=11, bold=True, color="1F4E78")
        ws.cell(row=row, column=1).fill = MOD_FILL
        ws.merge_cells(start_row=row, start_column=1, end_row=row, end_column=len(headers))
        ws.row_dimensions[row].height = 22
        row += 1

        for (tname, comment, pk, cols, idxs) in tabs:
            seq += 1
            vals = [seq, meta["zh"], meta["code"], tname, comment, ",".join(pk), len(cols), len(idxs), ""]
            for ci, v in enumerate(vals, start=1):
                c = ws.cell(row=row, column=ci, value=v)
                c.font = CELL_FONT
                c.border = BORDER
                c.alignment = Alignment(horizontal="left" if ci in (3,4,5,6,9) else "center",
                                        vertical="center", wrap_text=True)
            row += 1

    ws.freeze_panes = "A5"

    # ── 每模块 sheet ──────────────────────────────────────────────────────
    for mod_key, tabs in ALL_TABLES.items():
        meta = MODULES[mod_key]
        title = f"{mod_key}-{meta['zh']}"[:31]  # Excel sheet 名 ≤31
        ws = wb.create_sheet(title)
        ws.sheet_view.showGridLines = False

        ws.cell(row=1, column=1, value=f"{meta['zh']} · {meta['code']}").font = Font(
            name="Microsoft YaHei", size=14, bold=True, color="1F4E78")
        ws.cell(row=2, column=1, value=meta["desc"]).font = Font(
            name="Microsoft YaHei", size=10, italic=True, color="595959")

        headers = ["序", "字段名", "类型", "可空", "默认", "PK", "注释/业务含义"]
        widths  = [5,   28,      22,    6,     20,     5,   60]
        cur = 4

        for (tname, comment, pk, cols, idxs) in tabs:
            # 表头条
            ws.cell(row=cur, column=1,
                    value=f"■ {tname}    —    {comment}    (PK: {','.join(pk)} ; 字段 {len(cols)} 个 ; 索引 {len(idxs)} 个)"
                    ).font = Font(name="Microsoft YaHei", size=11, bold=True, color="FFFFFF")
            ws.cell(row=cur, column=1).fill = HEADER_FILL
            ws.merge_cells(start_row=cur, start_column=1, end_row=cur, end_column=len(headers))
            ws.row_dimensions[cur].height = 22
            cur += 1

            _apply_header(ws, cur, headers, widths)
            cur += 1

            for i, (cname, ctype, nullable, default, ccomment) in enumerate(cols, start=1):
                is_pk = cname in pk
                row_vals = [i, cname, ctype, "Y" if nullable else "N",
                            "" if default is None else default,
                            "★" if is_pk else "", ccomment]
                for ci, v in enumerate(row_vals, start=1):
                    c = ws.cell(row=cur, column=ci, value=v)
                    c.font = CELL_FONT
                    c.border = BORDER
                    c.alignment = Alignment(horizontal="left" if ci in (2,3,5,7) else "center",
                                            vertical="center", wrap_text=True)
                    if is_pk:
                        c.fill = PK_FILL
                cur += 1

            if idxs:
                ws.cell(row=cur, column=1, value="索引").font = Font(name="Microsoft YaHei", size=10, bold=True)
                ws.cell(row=cur, column=1).fill = MOD_FILL
                cur += 1
                _apply_header(ws, cur, ["序","索引名","类型","字段","用途"], [5,30,8,40,50])
                cur += 1
                for j, (iname, itype, icols, icomment) in enumerate(idxs, start=1):
                    rv = [j, iname, itype, ",".join(icols), icomment]
                    for ci, v in enumerate(rv, start=1):
                        c = ws.cell(row=cur, column=ci, value=v)
                        c.font = CELL_FONT
                        c.border = BORDER
                        c.alignment = Alignment(horizontal="left" if ci in (2,4,5) else "center",
                                                vertical="center", wrap_text=True)
                    cur += 1

            cur += 1  # 空行分隔

    wb.save(out_path)


# ─────────────────────────────────────────────────────────────────────────────
# 5. Mermaid 关联图
# ─────────────────────────────────────────────────────────────────────────────
MERMAID_KIND_MAP = {
    "N1":   "}o--||",   # 多对一(子表→主表), Mermaid: many-zeroOrMore --to-- exactlyOne
    "11":   "||--||",
    "1N":   "||--o{",
    "NN":   "}o--o{",
    "SELF": "}o--||",
    "LOG":  "}o..||",   # 逻辑关联用虚线
}


def _mermaid_table_block(table_name, comment, pk, cols, max_cols=12):
    """生成 mermaid erDiagram 中的实体块(精简到关键列, 完整结构在 Excel)."""
    important = []
    pk_set = set(pk)
    # 先放主键
    for c in cols:
        if c[0] in pk_set:
            important.append(c)
    # 然后放出现在关系或常用业务列里的
    for c in cols:
        if c in important:
            continue
        nm = c[0].lower()
        if nm in ("emp_id","cust_id","org_id","org_code","tag_id","role_id","user_id",
                  "metric_code","scheme_id","plan_id","apply_id","job_id","file_object_id",
                  "process_instance_id","business_key","status","data_date","version",
                  "owner_org_id","owner_org_code","owner_emp_id","subject_id",
                  "current_version","data_version","scope_dim","val_slot","base_dim"):
            important.append(c)
        if len(important) >= max_cols:
            break
    lines = []
    lines.append(f"  {table_name} {{")
    for (cname, ctype, nullable, default, ccomment) in important:
        # 类型简化
        t = ctype.split("(")[0].lower()
        marks = []
        if cname in pk_set:
            marks.append("PK")
        # 极简注释 - 截断
        cm = ccomment.replace("\"","").replace(":","").replace(",","").replace("|"," ").replace("/"," ").replace("→"," ")
        if len(cm) > 28:
            cm = cm[:28] + "…"
        line = f"    {t} {cname}"
        if marks:
            line += " " + " ".join(marks)
        if cm:
            line += f" \"{cm}\""
        lines.append(line)
    lines.append("  }")
    return "\n".join(lines)


def _mermaid_full_section(title, table_filter):
    """渲染一个 mermaid erDiagram, table_filter 为 set(table_names)."""
    out = []
    out.append(f"### {title}")
    out.append("")
    out.append("```mermaid")
    out.append("erDiagram")
    # 实体
    for mod_key, tabs in ALL_TABLES.items():
        for (tname, comment, pk, cols, idxs) in tabs:
            if tname in table_filter:
                out.append(_mermaid_table_block(tname, comment, pk, cols))
    # 关系(只保留两端都在 filter 里且非自关联混乱)
    for (ft, fc, tt, tc, kind, note) in RELATIONSHIPS:
        if ft in table_filter and tt in table_filter:
            arrow = MERMAID_KIND_MAP.get(kind, "}o--||")
            label = note.replace("\"", "")[:24]
            out.append(f"  {ft} {arrow} {tt} : \"{fc}→{tc} {label}\"")
    out.append("```")
    out.append("")
    return "\n".join(out)


def build_mermaid(out_path: str):
    out = []
    out.append("# yiti 数据表关联图(Mermaid)")
    out.append("")
    out.append("> 来源: 项目 `docs/schema/ddl-*.sql`(跳过 Quartz)。每个箭头后面的标签格式为 `源列→目标列  业务说明`。"
               "`}o..||` 虚线表示通过约定列(如 `business_key` 或 `version`)做的逻辑关联,无 FK 约束。")
    out.append("")

    # 跨模块总览(只挑节点最稠密的关键表,避免 mermaid 渲染爆掉)
    overview_tables = {
        # 主体维度
        "PT_USER","EXT_ORG_INFO","ADDRBOOK_EMPLOYEE","CUST_MASTER","PRODUCT_INFO",
        # 工作流枢纽
        "BIZ_PROCESS_MAP",
        # 业务核心域
        "CUST_LEAD","CUST_CLAIM","TOUCH_TASK","TOUCH_LOG",
        "LOAN_APPLY","SUPPORT_REQUEST",
        # 绩效核心
        "SYS_CONTROL","PERF_METRIC_DEF","PERF_KPI_SCHEME","PERF_KPI_ITEM",
        "PERF_TARGET_PLAN","PERF_TARGET_VALUE",
        "EMP_INDEX_RESULT","ORG_INDEX_RESULT","CUST_INDEX_RESULT","KPI_RESULT",
        "CUST_ALLOC_RELATION",
        # 横切
        "FILE_OBJECT","BIZ_FILE_REL",
        # 报表
        "RPT_EXPORT_TASK","RPT_SAVED_QUERY",
    }
    out.append(_mermaid_full_section("一、跨模块业务总览(精简实体)", overview_tables))

    # 每模块完整关系图
    out.append("---")
    out.append("")
    out.append("## 二、按模块的完整关联图")
    out.append("")
    for mod_key, tabs in ALL_TABLES.items():
        meta = MODULES[mod_key]
        names = {t[0] for t in tabs}
        # 把跟该模块表有直接边的"邻居表"也拉进来,但仅作为接口节点显示
        neighbors = set()
        for (ft, fc, tt, tc, kind, note) in RELATIONSHIPS:
            if ft in names and tt not in names:
                neighbors.add(tt)
            if tt in names and ft not in names:
                neighbors.add(ft)
        title = f"{meta['zh']}({mod_key}) — 含跨模块邻居表 {len(neighbors)} 张"
        section_tables = names | neighbors
        out.append(_mermaid_full_section(title, section_tables))

    # 数据范围: 表-业务关键字段图(说明性)
    out.append("---")
    out.append("")
    out.append("## 三、跨模块统一约定(说明性)")
    out.append("")
    out.append("- **business_key** = `{BIZ_TYPE}:{id}` 由所有走 Flowable 的业务表持有,"
               "通过 `BIZ_PROCESS_MAP.business_key` 反向关联。BizType 取值含 `LOAN/SUPPORT/LEAD/TOUCH/PERF_ALLOC_ADJUST/PERF_TARGET_ADJUST` 等。")
    out.append("- **owner_org_id / owner_org_code** = 数据范围(DataScope)过滤列,逻辑关联到 `EXT_ORG_INFO.ORG_CODE`,由 `PT_ROLE_BIZ_SCOPE` 配合解析。")
    out.append("- **emp_id** 在所有业务表里就是 `PT_USER.USER_ID`(工号),也即 `ADDRBOOK_EMPLOYEE.emp_id`,这是同一标识空间。")
    out.append("- **version / data_version** 在三张宽表与 `KPI_RESULT` 上,通过 `SYS_CONTROL.current_version` 维度级映射决定可见数据(逻辑外键,无物理约束)。")
    out.append("- **val_1..val_200** 在 `EMP/ORG/CUST_INDEX_RESULT` 上是 200 槽位列,由 `PERF_METRIC_DEF.val_slot`(同 `base_dim` 下未删除时唯一)路由到具体指标。")
    out.append("")

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))


# ─────────────────────────────────────────────────────────────────────────────
# 6. Graphviz dot 关联图(全图,适合后续 dot -Tsvg 渲染)
# ─────────────────────────────────────────────────────────────────────────────
DOT_MODULE_COLOR = {
    "auth":        "#9bc2e6",
    "governance":  "#ffc000",
    "workflow":    "#c6e0b4",
    "portal":      "#bdd7ee",
    "customer":    "#f4b084",
    "bizapp":      "#ffd966",
    "performance": "#a9d08e",
    "report":      "#b4a7d6",
}


def _dot_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")


def build_dot(out_path: str):
    lines = []
    lines.append("// yiti(分行业务平台) 数据表关联图 — Graphviz dot")
    lines.append("// 渲染:")
    lines.append("//   dot -Tsvg yiti-er-diagram.dot -o yiti-er.svg")
    lines.append("//   dot -Tpng -Gdpi=180 yiti-er-diagram.dot -o yiti-er.png")
    lines.append("digraph yiti {")
    lines.append("  graph [rankdir=LR, splines=spline, overlap=false, fontname=\"Microsoft YaHei\", concentrate=true, nodesep=0.4, ranksep=0.9];")
    lines.append("  node  [shape=plaintext, fontname=\"Microsoft YaHei\", fontsize=11];")
    lines.append("  edge  [fontname=\"Microsoft YaHei\", fontsize=9, color=\"#555555\"];")
    lines.append("")

    # 按模块 cluster 分组
    for mod_key, tabs in ALL_TABLES.items():
        meta = MODULES[mod_key]
        color = DOT_MODULE_COLOR[mod_key]
        lines.append(f"  subgraph cluster_{mod_key} {{")
        lines.append(f"    label=<<B>{meta['zh']}</B><BR/><FONT POINT-SIZE=\"9\">{meta['code']}</FONT>>;")
        lines.append(f"    style=\"filled,rounded\"; fillcolor=\"{color}33\"; color=\"{color}\"; penwidth=2;")
        for (tname, comment, pk, cols, idxs) in tabs:
            # 标签为 HTML-like 表格
            pk_set = set(pk)
            label_rows = [
                f"<TR><TD BGCOLOR=\"{color}\" COLSPAN=\"2\"><B>{tname}</B><BR/>"
                f"<FONT POINT-SIZE=\"8\">{_dot_escape(comment)}</FONT></TD></TR>"
            ]
            # 仅展示主键 + 关键列(避免 200 槽位淹掉图)
            shown = []
            for c in cols:
                if c[0] in pk_set:
                    shown.append(c)
            for c in cols:
                if c in shown:
                    continue
                nm = c[0].lower()
                if nm in ("emp_id","cust_id","org_id","org_code","tag_id","role_id","user_id",
                          "metric_code","scheme_id","plan_id","apply_id","job_id","file_object_id",
                          "process_instance_id","business_key","status","data_date","version",
                          "owner_org_id","owner_org_code","owner_emp_id","subject_id",
                          "current_version","data_version","scope_dim","val_slot","base_dim",
                          "lead_id","import_batch_id","touch_task_id","prev_lead_id",
                          "source_cust_id","source_touch_task_id","p_id","parent_resource_id",
                          "ref_metric_code","kpi_scheme_id","operator_id","file_key",
                          "support_dept_id","product_id","assigned_emp_id","dispatch_emp_id",
                          "claimed_by","maintainer_emp_id","assignee_emp_id"):
                    shown.append(c)
                if len(shown) >= 14:
                    break
            for (cname, ctype, _, _, ccm) in shown:
                pk_mark = "🔑 " if cname in pk_set else ""
                t = ctype.split("(")[0]
                # 大括号在 Graphviz HTML 标签里仅是文本,但截断可能切断 `{id}` 这种成对字符;
                # 直接去掉,避免渲染/计数歧义。
                ccm_clean = ccm.replace("{", "").replace("}", "")
                cm_short = (ccm_clean[:18] + "…") if len(ccm_clean) > 18 else ccm_clean
                label_rows.append(
                    f"<TR><TD ALIGN=\"LEFT\" PORT=\"{cname}\">{pk_mark}{cname}</TD>"
                    f"<TD ALIGN=\"LEFT\"><FONT COLOR=\"#666666\">{t}</FONT> "
                    f"<FONT COLOR=\"#999999\" POINT-SIZE=\"8\">{_dot_escape(cm_short)}</FONT></TD></TR>"
                )
            if len(cols) > len(shown):
                label_rows.append(
                    f"<TR><TD COLSPAN=\"2\" ALIGN=\"CENTER\"><FONT POINT-SIZE=\"8\" COLOR=\"#888888\">…还有 {len(cols)-len(shown)} 列见 Excel…</FONT></TD></TR>"
                )
            label = "<<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"3\">" + "".join(label_rows) + "</TABLE>>"
            lines.append(f"    {tname} [label={label}];")
        lines.append("  }")
        lines.append("")

    # 边(关系)
    for (ft, fc, tt, tc, kind, note) in RELATIONSHIPS:
        style = "solid"
        arrowhead = "normal"
        color = "#555555"
        if kind == "LOG":
            style = "dashed"
            color = "#888888"
        elif kind == "SELF":
            color = "#aa6633"
        elif kind == "11":
            color = "#1f78b4"
        label = _dot_escape(f"{fc}→{tc}\\n{note}")
        # 用 PORT 提升对齐(若列名匹配则连到对应 row, 否则用整体)
        # 简化: 不强制使用 port,让 dot 自由排版
        lines.append(f"  {ft} -> {tt} [label=\"{label}\", style={style}, color=\"{color}\", arrowhead={arrowhead}, fontcolor=\"#666666\"];")

    lines.append("}")

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


# ─────────────────────────────────────────────────────────────────────────────
# 7. main
# ─────────────────────────────────────────────────────────────────────────────
def main():
    here = os.path.dirname(os.path.abspath(__file__))
    xlsx_path = os.path.join(here, "yiti-schema.xlsx")
    md_path   = os.path.join(here, "yiti-er-diagram.md")
    dot_path  = os.path.join(here, "yiti-er-diagram.dot")

    n_tables = sum(len(t) for t in ALL_TABLES.values())
    n_cols   = sum(len(c) for tabs in ALL_TABLES.values() for (_, _, _, c, _) in tabs)
    n_rels   = len(RELATIONSHIPS)
    print(f"模块数: {len(ALL_TABLES)} ; 表数: {n_tables} ; 总字段: {n_cols} ; 关联: {n_rels}")

    build_xlsx(xlsx_path)
    print(f"  xlsx → {xlsx_path}")

    build_mermaid(md_path)
    print(f"  md   → {md_path}")

    build_dot(dot_path)
    print(f"  dot  → {dot_path}")


if __name__ == "__main__":
    main()
