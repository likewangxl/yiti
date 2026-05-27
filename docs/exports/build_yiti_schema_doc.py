#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基于 yiti 库 information_schema + 项目 DDL 归属，生成：
1. yiti_schema.xlsx       — 目录 + 每模块 sheet
2. yiti_er.puml            — PlantUML ER 关系图（按模块分组）
3. yiti_relations.tsv      — 物理 FK + 逻辑推断关系合并清单

业务模块归属来源：docs/schema/ddl-*.sql（项目权威）
逻辑关系推断：基于命名约定（USER_ID→PT_USER、ORG_ID→EXT_ORG_INFO、...）
"""

import csv
import os
from collections import defaultdict, OrderedDict

META_DIR = "/tmp/yiti_meta"
OUT_DIR = "/home/djdev/lf/yiti/docs/exports"

# ---------- 模块归属（来源：docs/schema/ddl-*.sql 实际声明） ----------
MODULE_DDL_MAP = {
    "auth-permission-center": [
        "PT_RESOURCE", "PT_ROLE", "PT_ROLE_BIZ_SCOPE", "PT_ROLE_RESOURCE",
        "PT_USER", "PT_USER_ROLE", "EXT_ORG_INFO", "EXT_USER_ORG",
    ],
    "system-governance-center": [
        "AUDIT_LOG", "BIZ_FILE_REL", "FILE_OBJECT", "SYS_CALENDAR_DAY",
        "SYS_CONFIG_KV", "SYS_DICT", "SYS_DICT_ITEM", "SYS_JOB_CONF",
        "SYS_JOB_RUN_LOG", "USER_NOTIFICATION",
    ],
    "workflow-center": [
        "BIZ_PROCESS_MAP", "WF_NODE_CANDIDATE_CONF", "WF_NODE_FORM_CONF",
        "WF_TIMEOUT_RULE",
    ],
    "customer-marketing-center": [
        "CUST_CLAIM", "CUST_LEAD", "CUST_MASTER", "CUST_TAG", "CUST_TAG_REL",
        "LEAD_IMPORT_BATCH", "TOUCH_LOG", "TOUCH_TASK",
    ],
    "business-application-center": [
        "LOAN_APPLY", "SUPPORT_REQUEST",
    ],
    "portal-content-center": [
        "ADDRBOOK_EMPLOYEE", "DOC_INFO", "PORTAL_NAV", "PORTAL_SHORTCUT",
        "PRODUCT_INFO",
    ],
    "performance-engine-center": [
        "CUST_ALLOC_RELATION", "CUST_INDEX_RESULT", "EMP_INDEX_RESULT",
        "KPI_RESULT", "ORG_INDEX_RESULT", "PERF_ALLOC_ADJUST_APPLY",
        "PERF_ALLOC_ADJUST_ITEM", "PERF_IMPORT_BATCH", "PERF_KPI_ITEM",
        "PERF_KPI_SCHEME", "PERF_METRIC_DEF", "PERF_METRIC_REF",
        "PERF_RUN_TASK", "PERF_TARGET_ADJUST_APPLY", "PERF_TARGET_PLAN",
        "PERF_TARGET_VALUE", "SYS_CONTROL",
    ],
    "report-analytics-center": [
        "RPT_EXPORT_TASK", "RPT_SAVED_QUERY", "RPT_SNAPSHOT_TASK",
        "SQL_PROBE_HISTORY",
    ],
}

# 模块顺序 + 中文名 + 颜色（PlantUML cluster 用）
MODULE_INFO = OrderedDict([
    ("auth-permission-center",     ("01-认证授权中心", "#E3F2FD")),
    ("system-governance-center",   ("02-系统治理中心", "#FFF3E0")),
    ("workflow-center",            ("03-工作流中心",   "#F3E5F5")),
    ("portal-content-center",      ("04-门户与内容",   "#E8F5E9")),
    ("customer-marketing-center",  ("05-客户营销",     "#FFEBEE")),
    ("business-application-center",("06-业务申请",     "#FBE9E7")),
    ("performance-engine-center",  ("07-绩效计算",     "#E0F7FA")),
    ("report-analytics-center",    ("08-报表分析",     "#F1F8E9")),
    ("flowable-engine",            ("09-Flowable引擎(只读)", "#ECEFF1")),
    ("quartz-scheduler",           ("10-Quartz调度(只读)",  "#ECEFF1")),
])


def classify_table(t: str) -> str:
    for mod, tables in MODULE_DDL_MAP.items():
        if t in tables:
            return mod
    if t.startswith("ACT_") or t.startswith("FLW_"):
        return "flowable-engine"
    if t.startswith("QRTZ_"):
        return "quartz-scheduler"
    return "uncategorized"


# ---------- 加载元数据 ----------
def load_tsv(path):
    with open(path, encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        headers = next(reader)
        return headers, [row for row in reader if row]


_, tables_rows = load_tsv(os.path.join(META_DIR, "tables.tsv"))
_, columns_rows = load_tsv(os.path.join(META_DIR, "columns.tsv"))
_, fks_rows = load_tsv(os.path.join(META_DIR, "fks.tsv"))

# 表 -> {comment, rows, engine, collation, module}
tables = OrderedDict()
for row in tables_rows:
    tname, comment, trows, engine, collation = (row + [""] * 5)[:5]
    tables[tname] = {
        "comment": comment, "rows": trows, "engine": engine,
        "collation": collation, "module": classify_table(tname),
        "columns": [],
    }

# 表 -> 列列表
for row in columns_rows:
    if len(row) < 9:
        row = row + [""] * (9 - len(row))
    tname, cname, pos, ctype, nullable, cdef, ckey, extra, ccomment = row[:9]
    if tname in tables:
        tables[tname]["columns"].append({
            "pos": pos, "name": cname, "type": ctype, "nullable": nullable,
            "default": cdef, "key": ckey, "extra": extra, "comment": ccomment,
        })

# 物理外键
physical_fks = []
for row in fks_rows:
    if len(row) < 5:
        continue
    src, src_col, ref_t, ref_col, name = row[:5]
    physical_fks.append({"src": src, "src_col": src_col, "ref": ref_t,
                         "ref_col": ref_col, "name": name, "kind": "physical"})

# ---------- 业务关系推断（命名约定） ----------
# 列名 -> (目标表, 目标列, 备注)；命中即视为逻辑外键
LOGICAL_RULES = [
    # ----- 认证 / 用户 / 组织 -----
    ("user_id",        "PT_USER",          "id",      "用户引用"),
    ("emp_id",         "PT_USER",          "id",      "员工引用"),
    ("created_by",     "PT_USER",          "id",      "创建人"),
    ("updated_by",     "PT_USER",          "id",      "更新人"),
    ("assignee_id",    "PT_USER",          "id",      "负责人"),
    ("assignee",       "PT_USER",          "username","Flowable assignee"),
    ("owner_id",       "PT_USER",          "id",      "所属人"),
    ("manager_id",     "PT_USER",          "id",      "客户经理"),
    ("operator_id",    "PT_USER",          "id",      "操作人"),
    ("applicant_id",   "PT_USER",          "id",      "申请人"),
    ("submitter_id",   "PT_USER",          "id",      "提交人"),
    ("create_user",    "PT_USER",          "id",      "创建人(旧)"),
    ("update_user",    "PT_USER",          "id",      "更新人(旧)"),
    ("role_id",        "PT_ROLE",          "id",      "角色引用"),
    ("resource_id",    "PT_RESOURCE",      "id",      "资源引用"),
    ("parent_id",      "PT_RESOURCE",      "id",      "父资源(语境敏感)"),
    ("org_code",       "EXT_ORG_INFO",     "org_code","机构引用"),
    ("org_id",         "EXT_ORG_INFO",     "id",      "机构引用"),
    ("owner_org_id",   "EXT_ORG_INFO",     "id",      "所属机构"),
    ("dept_id",        "EXT_ORG_INFO",     "id",      "部门引用"),
    # ----- 工作流 -----
    ("business_key",          "BIZ_PROCESS_MAP", "business_key", "流程业务键"),
    ("biz_key",               "BIZ_PROCESS_MAP", "business_key", "流程业务键"),
    ("process_instance_id",   "ACT_RU_EXECUTION","ID_",          "Flowable 流程实例"),
    ("process_definition_key","ACT_RE_PROCDEF",  "KEY_",         "Flowable 流程定义"),
    ("proc_def_key",          "WF_NODE_FORM_CONF","proc_def_key","流程定义键"),
    ("node_key",              "WF_NODE_FORM_CONF","node_key",    "节点键"),
    # ----- 字典 / 配置 -----
    ("dict_code",      "SYS_DICT",         "dict_code", "字典编码"),
    ("config_key",     "SYS_CONFIG_KV",    "config_key","配置键"),
    # ----- 文件 -----
    ("file_id",        "FILE_OBJECT",      "id",      "文件引用"),
    ("biz_id",         "BIZ_FILE_REL",     "biz_id",  "业务文件挂载"),
    # ----- 客户营销 -----
    ("lead_id",        "CUST_LEAD",        "id",      "线索引用"),
    ("customer_id",    "CUST_MASTER",      "id",      "客户引用"),
    ("cust_id",        "CUST_MASTER",      "id",      "客户引用"),
    ("master_id",      "CUST_MASTER",      "id",      "主客户引用"),
    ("tag_id",         "CUST_TAG",         "id",      "标签引用"),
    ("batch_id",       "LEAD_IMPORT_BATCH","id",      "批次引用"),
    ("import_batch_id","LEAD_IMPORT_BATCH","id",      "导入批次"),
    ("task_id",        "TOUCH_TASK",       "id",      "触达任务"),
    ("touch_task_id",  "TOUCH_TASK",       "id",      "触达任务"),
    # ----- 绩效 -----
    ("metric_id",      "PERF_METRIC_DEF",  "id",      "指标定义"),
    ("metric_code",    "PERF_METRIC_DEF",  "metric_code","指标编码"),
    ("kpi_scheme_id",  "PERF_KPI_SCHEME",  "id",      "KPI 方案"),
    ("scheme_id",      "PERF_KPI_SCHEME",  "id",      "KPI 方案"),
    ("kpi_item_id",    "PERF_KPI_ITEM",    "id",      "KPI 项"),
    ("run_task_id",    "PERF_RUN_TASK",    "id",      "调度任务"),
    ("target_plan_id", "PERF_TARGET_PLAN", "id",      "目标方案"),
    ("plan_id",        "PERF_TARGET_PLAN", "id",      "目标方案"),
    ("apply_id",       "PERF_ALLOC_ADJUST_APPLY", "id", "调整申请"),
    ("alloc_apply_id", "PERF_ALLOC_ADJUST_APPLY", "id", "调整申请"),
    ("perf_import_batch_id", "PERF_IMPORT_BATCH", "id", "绩效导入批次"),
    # ----- 业务申请 -----
    ("loan_apply_id",  "LOAN_APPLY",       "id",      "贷款申请"),
    ("product_id",     "PRODUCT_INFO",     "id",      "产品引用"),
    ("support_request_id", "SUPPORT_REQUEST", "id",   "支持申请"),
    # ----- 调度 -----
    ("job_key",        "SYS_JOB_CONF",     "job_key", "调度配置"),
    ("job_conf_id",    "SYS_JOB_CONF",     "id",      "调度配置"),
    # ----- 报表 -----
    ("query_id",       "RPT_SAVED_QUERY",  "id",      "保存查询"),
    ("export_task_id", "RPT_EXPORT_TASK",  "id",      "导出任务"),
    ("snapshot_task_id","RPT_SNAPSHOT_TASK","id",     "快照任务"),
]

# 部分列只有特定上下文才引用，例：CUST_ALLOC_RELATION.MANAGER_ID→PT_USER 才有意义
# 简化：按规则全字段扫描，但同模块自引用过滤
def infer_logical_fks():
    inferred = []
    seen = set()
    physical_keys = {(f["src"].upper(), f["src_col"].upper(), f["ref"].upper())
                     for f in physical_fks}
    # 规则索引化为大小写不敏感
    rule_index = {}  # rule_col_lower -> (ref_t, ref_c, note)
    for rc, rt, rcol, note in LOGICAL_RULES:
        rule_index.setdefault(rc.lower(), (rt, rcol, note))

    for tname, info in tables.items():
        if tname.startswith("ACT_") or tname.startswith("QRTZ_") or tname.startswith("FLW_"):
            continue  # 引擎表不参与业务推断
        for col in info["columns"]:
            cname = col["name"]
            hit = rule_index.get(cname.lower())
            if not hit:
                continue
            ref_t, ref_c, note = hit
            if ref_t not in tables or ref_t == tname:
                continue
            if (tname.upper(), cname.upper(), ref_t.upper()) in physical_keys:
                continue
            key = (tname, cname, ref_t)
            if key in seen:
                continue
            seen.add(key)
            inferred.append({
                "src": tname, "src_col": cname, "ref": ref_t,
                "ref_col": ref_c, "name": f"logical:{note}",
                "kind": "logical",
            })
    return inferred


inferred_fks = infer_logical_fks()
all_fks = physical_fks + inferred_fks

# 写出关系清单
with open(os.path.join(OUT_DIR, "yiti_relations.tsv"), "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="\t")
    w.writerow(["KIND", "SRC_TABLE", "SRC_MODULE", "SRC_COLUMN",
                "REF_TABLE", "REF_MODULE", "REF_COLUMN", "CONSTRAINT_NAME"])
    for r in all_fks:
        src_mod = tables.get(r["src"], {}).get("module", "?")
        ref_mod = tables.get(r["ref"], {}).get("module", "?")
        w.writerow([r["kind"], r["src"], src_mod, r["src_col"],
                    r["ref"], ref_mod, r["ref_col"], r["name"]])

# ---------- 生成 Excel ----------
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, PatternFill, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.table import Table, TableStyleInfo

wb = Workbook()

HDR_FILL = PatternFill("solid", fgColor="305496")
HDR_FONT = Font(name="Microsoft YaHei", size=11, bold=True, color="FFFFFF")
BODY_FONT = Font(name="Microsoft YaHei", size=10)
TBL_HDR_FILL = PatternFill("solid", fgColor="D9E1F2")
TBL_HDR_FONT = Font(name="Microsoft YaHei", size=11, bold=True, color="1F4E78")
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)
LEFT = Alignment(horizontal="left", vertical="center", wrap_text=True)


def style_header(ws, row, col_widths):
    for c, w in enumerate(col_widths, 1):
        cell = ws.cell(row=row, column=c)
        cell.fill = HDR_FILL
        cell.font = HDR_FONT
        cell.alignment = CENTER
        cell.border = BORDER
        ws.column_dimensions[get_column_letter(c)].width = w


# ---------- Sheet1: 目录 ----------
ws = wb.active
ws.title = "目录"
ws.append(["序号", "模块", "中文名", "表名", "表注释", "字段数", "数据行数(估)", "存储引擎"])
style_header(ws, 1, [6, 32, 22, 32, 60, 10, 14, 12])

idx = 1
for mod, (cn_name, _) in MODULE_INFO.items():
    mod_tables = sorted([t for t, info in tables.items() if info["module"] == mod])
    for t in mod_tables:
        info = tables[t]
        ws.append([idx, mod, cn_name, t, info["comment"],
                   len(info["columns"]), info["rows"], info["engine"]])
        idx += 1

# 加表格样式
last_row = ws.max_row
ref = f"A1:H{last_row}"
tbl = Table(displayName="TOC", ref=ref)
tbl.tableStyleInfo = TableStyleInfo(name="TableStyleMedium2", showRowStripes=True)
ws.add_table(tbl)
ws.freeze_panes = "A2"
for r in range(2, last_row + 1):
    for c in range(1, 9):
        cell = ws.cell(row=r, column=c)
        cell.font = BODY_FONT
        cell.alignment = LEFT if c in (3, 4, 5) else CENTER
        cell.border = BORDER

# ---------- 每模块一个 sheet ----------
COL_HEADERS = ["#", "字段名", "数据类型", "可空", "默认值", "键", "自增/扩展", "字段注释"]
COL_WIDTHS  = [4, 26, 22, 6, 18, 8, 14, 50]


def add_module_sheet(mod_key, cn_name):
    ws = wb.create_sheet(title=cn_name[:31])
    mod_tables = sorted([t for t, info in tables.items() if info["module"] == mod_key])
    if not mod_tables:
        ws.append([f"模块 {cn_name} 在 yiti 库中无表"])
        return

    # 顶部标题
    ws.append([f"{cn_name}  ({mod_key}) — 共 {len(mod_tables)} 张表"])
    ws.merge_cells(start_row=1, end_row=1, start_column=1, end_column=8)
    title_cell = ws.cell(row=1, column=1)
    title_cell.font = Font(name="Microsoft YaHei", size=14, bold=True, color="1F4E78")
    title_cell.alignment = CENTER
    title_cell.fill = PatternFill("solid", fgColor="DDEBF7")
    ws.row_dimensions[1].height = 26

    for c, w in enumerate(COL_WIDTHS, 1):
        ws.column_dimensions[get_column_letter(c)].width = w

    cur = 3
    for t in mod_tables:
        info = tables[t]
        # 表标题块
        ws.cell(row=cur, column=1, value=f"▶ {t}").font = Font(
            name="Microsoft YaHei", size=12, bold=True, color="FFFFFF")
        ws.cell(row=cur, column=1).fill = PatternFill("solid", fgColor="305496")
        ws.cell(row=cur, column=1).alignment = LEFT
        ws.merge_cells(start_row=cur, end_row=cur, start_column=1, end_column=2)

        cmt = info["comment"] or "(无注释)"
        meta = f"注释：{cmt}    字段数：{len(info['columns'])}    引擎：{info['engine']}    估行数：{info['rows']}"
        ws.cell(row=cur, column=3, value=meta).font = Font(
            name="Microsoft YaHei", size=10, color="FFFFFF")
        ws.cell(row=cur, column=3).fill = PatternFill("solid", fgColor="305496")
        ws.cell(row=cur, column=3).alignment = LEFT
        ws.merge_cells(start_row=cur, end_row=cur, start_column=3, end_column=8)
        ws.row_dimensions[cur].height = 22
        cur += 1

        # 列表头
        for c, h in enumerate(COL_HEADERS, 1):
            cell = ws.cell(row=cur, column=c, value=h)
            cell.font = TBL_HDR_FONT
            cell.fill = TBL_HDR_FILL
            cell.alignment = CENTER
            cell.border = BORDER
        cur += 1

        # 列内容
        for col in info["columns"]:
            row = [col["pos"], col["name"], col["type"], col["nullable"],
                   col["default"], col["key"], col["extra"], col["comment"]]
            for c, v in enumerate(row, 1):
                cell = ws.cell(row=cur, column=c, value=v)
                cell.font = BODY_FONT
                cell.border = BORDER
                cell.alignment = LEFT if c in (2, 3, 5, 8) else CENTER
                if col["key"] == "PRI":
                    cell.fill = PatternFill("solid", fgColor="FFF2CC")
            cur += 1

        cur += 1  # 空行

    ws.freeze_panes = "A3"


for mod_key, (cn_name, _) in MODULE_INFO.items():
    add_module_sheet(mod_key, cn_name)

# ---------- 关系总览 sheet ----------
ws = wb.create_sheet(title="11-关系总览")
ws.append(["类型", "源表", "源模块", "源列", "→", "目标表", "目标模块", "目标列", "约束/备注"])
style_header(ws, 1, [10, 28, 26, 22, 4, 28, 26, 18, 36])
for r in all_fks:
    src_mod = tables.get(r["src"], {}).get("module", "?")
    ref_mod = tables.get(r["ref"], {}).get("module", "?")
    kind_cn = "物理外键" if r["kind"] == "physical" else "逻辑关联"
    ws.append([kind_cn, r["src"], MODULE_INFO.get(src_mod, (src_mod,))[0],
               r["src_col"], "→", r["ref"], MODULE_INFO.get(ref_mod, (ref_mod,))[0],
               r["ref_col"], r["name"]])

# 表格样式
last_row = ws.max_row
ref = f"A1:I{last_row}"
tbl = Table(displayName="REL", ref=ref)
tbl.tableStyleInfo = TableStyleInfo(name="TableStyleMedium2", showRowStripes=True)
ws.add_table(tbl)
ws.freeze_panes = "A2"
for r in range(2, last_row + 1):
    for c in range(1, 10):
        cell = ws.cell(row=r, column=c)
        cell.font = BODY_FONT
        cell.alignment = LEFT if c in (2, 3, 6, 7, 9) else CENTER
        cell.border = BORDER
        if ws.cell(row=r, column=1).value == "物理外键":
            cell.fill = PatternFill("solid", fgColor="FFF2CC")

xlsx_path = os.path.join(OUT_DIR, "yiti_schema.xlsx")
wb.save(xlsx_path)
print(f"[OK] Excel: {xlsx_path}")

# ---------- PlantUML ER 图（业务核心） ----------
def write_puml_business():
    """业务核心图：8 个业务模块 + 关键流程键关系，剔除 ACT/QRTZ 引擎表。"""
    out = os.path.join(OUT_DIR, "yiti_er_business.puml")
    business_mods = [m for m in MODULE_INFO
                     if m not in ("flowable-engine", "quartz-scheduler")]
    business_tables = {t for t, info in tables.items()
                       if info["module"] in business_mods}

    lines = ["@startuml yiti_er_business",
             "!define TABLE(x) class x << (T,#FFAAAA) >>",
             "skinparam dpi 110",
             "skinparam classFontName Microsoft YaHei",
             "skinparam classFontSize 12",
             "skinparam classBackgroundColor #FFFFFF",
             "skinparam classBorderColor #1F4E78",
             "skinparam packageStyle rectangle",
             "skinparam ranksep 60",
             "skinparam nodesep 30",
             "left to right direction",
             "title yiti 业务核心数据模型 — 9 模块 + 跨模块逻辑关系\\n(物理外键 + 命名约定推断)",
             ""]

    for mod in business_mods:
        cn, color = MODULE_INFO[mod]
        ts = sorted([t for t in business_tables if tables[t]["module"] == mod])
        if not ts:
            continue
        lines.append(f'package "{cn}" as {mod.replace("-", "_")} {color} {{')
        for t in ts:
            info = tables[t]
            cmt = (info["comment"] or "").replace('"', "'").replace("\n", " ")[:30]
            pks = [c["name"] for c in info["columns"] if c["key"] == "PRI"]
            pk_str = ", ".join(pks) if pks else "—"
            lines.append(f'  entity "{t}" as {t} {{')
            lines.append(f'    .. {cmt} ..')
            lines.append(f'    PK: {pk_str}')
            # 列出关键引用列（命中 LOGICAL_RULES 的）
            rule_cols_lower = {r[0].lower() for r in LOGICAL_RULES}
            ref_cols = [c["name"] for c in info["columns"]
                        if c["name"].lower() in rule_cols_lower]
            for rc in ref_cols[:6]:
                lines.append(f'    + {rc}')
            if len(ref_cols) > 6:
                lines.append(f'    + ... (+{len(ref_cols) - 6})')
            lines.append('  }')
        lines.append("}")
        lines.append("")

    # 关系：跨模块或同模块的逻辑/物理 FK（仅业务表）
    seen = set()
    for r in all_fks:
        if r["src"] not in business_tables or r["ref"] not in business_tables:
            continue
        key = (r["src"], r["ref"], r["src_col"])
        if key in seen:
            continue
        seen.add(key)
        arrow = "}--" if r["kind"] == "physical" else "}.."
        label = r["src_col"]
        lines.append(f'{r["src"]} {arrow} {r["ref"]} : {label}')

    lines.append("@enduml")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"[OK] PlantUML: {out}")
    return out


def write_puml_overview():
    """模块级总览：每模块一个 box，仅画跨模块连线。"""
    out = os.path.join(OUT_DIR, "yiti_er_overview.puml")
    cross = defaultdict(int)
    for r in all_fks:
        s = tables.get(r["src"], {}).get("module", "?")
        t = tables.get(r["ref"], {}).get("module", "?")
        if s == t or s == "?" or t == "?":
            continue
        cross[(s, t)] += 1

    lines = ["@startuml yiti_er_overview",
             "skinparam dpi 110",
             "skinparam packageStyle rectangle",
             "skinparam classFontName Microsoft YaHei",
             "left to right direction",
             "title yiti 模块依赖总览 — 跨模块关系强度（条数=逻辑+物理）",
             ""]
    for mod, (cn, color) in MODULE_INFO.items():
        n = sum(1 for t in tables if tables[t]["module"] == mod)
        lines.append(f'package "{cn}\\n({n} 张表)" as {mod.replace("-", "_")} {color} {{}}')
    lines.append("")
    for (s, t), n in sorted(cross.items(), key=lambda x: -x[1]):
        a = s.replace("-", "_")
        b = t.replace("-", "_")
        lines.append(f'{a} --> {b} : {n}')
    lines.append("@enduml")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"[OK] PlantUML: {out}")
    return out


# 通用审计列（每张表都会引用 PT_USER）：在“非 auth 模块”图里隐藏，避免一锅粥
NOISE_AUDIT_COLS = {"created_by", "updated_by", "create_user", "update_user"}


def write_puml_per_module():
    """每业务模块一张内部 ER 图 + 跨模块引用作为 ghost 节点。"""
    business_mods = [m for m in MODULE_INFO
                     if m not in ("flowable-engine", "quartz-scheduler")]
    paths = []
    for mod in business_mods:
        cn, color = MODULE_INFO[mod]
        ts = sorted([t for t in tables if tables[t]["module"] == mod])
        if not ts:
            continue
        out = os.path.join(OUT_DIR, f"yiti_er_{mod}.puml")
        lines = [f"@startuml yiti_er_{mod}",
                 "skinparam dpi 110",
                 "skinparam classFontName Microsoft YaHei",
                 "skinparam classFontSize 12",
                 "skinparam packageStyle rectangle",
                 "skinparam linetype ortho",
                 f"title {cn} — 内部表与跨模块引用 (虚线=逻辑/实线=物理)",
                 ""]
        # 主模块包
        lines.append(f'package "{cn}" {color} {{')
        for t in ts:
            info = tables[t]
            cmt = (info["comment"] or "").replace('"', "'").replace("\n", " ")[:30]
            pks = [c["name"] for c in info["columns"] if c["key"] == "PRI"]
            pk_str = ", ".join(pks) if pks else "—"
            lines.append(f'  entity "{t}" as {t} {{')
            lines.append(f'    .. {cmt} ..')
            lines.append(f'    PK: {pk_str}')
            rule_cols_lower = {r[0].lower() for r in LOGICAL_RULES}
            ref_cols = [c["name"] for c in info["columns"]
                        if c["name"].lower() in rule_cols_lower]
            for rc in ref_cols[:8]:
                lines.append(f'    + {rc}')
            lines.append('  }')
        lines.append("}")
        lines.append("")
        # 涉及的外部表（其他模块）— 在非 auth 模块里跳过仅由 created_by/updated_by 引发的关系
        is_auth_module = (mod == "auth-permission-center")
        external = set()
        for r in all_fks:
            if not is_auth_module and r["src_col"].lower() in NOISE_AUDIT_COLS:
                continue
            if r["src"] in ts and r["ref"] not in ts and r["ref"] in tables:
                external.add(r["ref"])
            if r["ref"] in ts and r["src"] not in ts and r["src"] in tables:
                external.add(r["src"])
        if external:
            lines.append('package "外部模块（引用占位）" #FAFAFA {')
            for et in sorted(external):
                ext_mod = tables[et]["module"]
                ext_cn = MODULE_INFO.get(ext_mod, (ext_mod,))[0]
                cmt = (tables[et]["comment"] or "").replace('"', "'")[:24]
                lines.append(f'  entity "{et}" as {et} <<{ext_cn}>> {{')
                lines.append(f'    .. {cmt} ..')
                lines.append('  }')
            lines.append('}')
            lines.append("")
        # 关系
        seen = set()
        for r in all_fks:
            if r["src"] not in ts and r["ref"] not in ts:
                continue
            if not is_auth_module and r["src_col"].lower() in NOISE_AUDIT_COLS:
                continue
            if r["ref"] not in ts and r["ref"] not in external:
                continue
            key = (r["src"], r["src_col"], r["ref"])
            if key in seen:
                continue
            seen.add(key)
            arrow = "}--" if r["kind"] == "physical" else "}.."
            lines.append(f'{r["src"]} {arrow} {r["ref"]} : {r["src_col"]}')
        lines.append("@enduml")
        with open(out, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        paths.append(out)
    print(f"[OK] {len(paths)} per-module .puml files generated")
    return paths


business_puml = write_puml_business()
overview_puml = write_puml_overview()
per_module_pumls = write_puml_per_module()

print("\n汇总：")
print(f"  表数：{len(tables)}")
print(f"  字段数：{sum(len(t['columns']) for t in tables.values())}")
print(f"  物理外键：{len(physical_fks)}")
print(f"  逻辑关联（命名推断）：{len(inferred_fks)}")
for mod, (cn, _) in MODULE_INFO.items():
    n = sum(1 for t in tables if tables[t]["module"] == mod)
    print(f"  {cn}: {n} 张")
unc = sum(1 for t in tables if tables[t]["module"] == "uncategorized")
if unc:
    print(f"  未归类: {unc} 张")
    for t in tables:
        if tables[t]["module"] == "uncategorized":
            print(f"    - {t}")
